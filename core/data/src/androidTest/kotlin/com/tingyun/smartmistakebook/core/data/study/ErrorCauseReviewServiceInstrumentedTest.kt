package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseAppendResult
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseObservationRecord
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseIdentity
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批次 50 / 5c：详情页错因接缝的**真库**验收（Room + SQLite，生产 `RoomStudyDatabase` 实现）。
 *
 * 与 JVM `ErrorCauseReviewServiceTest` 的分工：那边用假端口证状态机顺序；这里证明真 SQL 上：
 * 1. 候选读口只回本练习单元的 `PROPOSED` 行（别的练习单元的候选不出现）；
 * 2. 用户确认 → `USER_CONFIRMED` 的 ACTIVE，下一次读取能看到；重复确认不写第二条；
 * 3. 撤销 → 读侧为空、活键释放（新的确认能正常落账）。
 * 4. 门默认关闭：`autoBookingClosed` 恒 true，候选只有经过"人"才会变成标签。
 *
 * 撤回行"不被删除"的库级断言在 `core:database` 的 `ErrorCauseLedgerInstrumentedTest`
 * （`dao.findObservation("retraction-1")` 非空）已有；本用例不重复造同一夹具，只证接缝可见性。
 */
@RunWith(AndroidJUnit4::class)
class ErrorCauseReviewServiceInstrumentedTest {

    @Test
    fun confirmThenUndoRoundTripsThroughTheRealLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "error-cause-review-5c-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        try {
            seedContentChain(context, store, databaseName)
            val scope = ErrorCauseScope(
                learnerId = LEARNER_ID,
                practiceUnitId = UNIT_ID,
                problemRevisionId = REVISION_ID,
            )
            val service = ErrorCauseReviewService(store)

            val candidate = observation(scope, ErrorCause.CONCEPTUAL, detail = null)
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(candidate))
            val otherUnit = observation(
                scope = scope.copy(practiceUnitId = OTHER_UNIT_ID),
                cause = ErrorCause.PROCEDURAL,
                detail = ErrorCauseDetail.PROC_STEP_ORDER,
                refId = "message-2",
            )
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(otherUnit))

            assertTrue("D-1c 未认证：模型自动落账必须关闭", service.autoBookingClosed)
            val firstRead = service.read(scope)
            assertNull("候选不是现行标签", firstRead.active)
            assertEquals("只回本练习单元的候选", 1, firstRead.candidates.size)
            assertEquals(candidate.observationId, firstRead.candidates.single().observationId)

            // 用户确认：真 SQL 上落 ACCEPTED + ACTIVE
            assertEquals(
                ErrorCauseReviewWriteResult.APPLIED,
                service.confirmCandidate(scope, candidate.observationId, 20_000),
            )
            val confirmed = service.read(scope)
            assertNotNull("确认后下一次读取必须看到现行标签", confirmed.active)
            assertEquals(scope.activeKey(), confirmed.active!!.activeKey)
            assertEquals(ErrorCauseAcceptanceSource.USER_CONFIRMED, confirmed.active.acceptanceSource)
            assertNull("用户确认不带 D-1c 门版本", confirmed.active.gateVersion)
            assertEquals(candidate.observationId, confirmed.active.sourceObservationId)
            assertEquals("确认后候选列表清空", 0, confirmed.candidates.size)
            assertEquals(
                "重放同一确认不得写出第二条 ACTIVE",
                ErrorCauseReviewWriteResult.CANDIDATE_NOT_FOUND,
                service.confirmCandidate(scope, candidate.observationId, 21_000),
            )

            // 撤销：active 为空，活键释放
            assertEquals(
                ErrorCauseReviewWriteResult.APPLIED,
                service.undoActiveTag(
                    scope = scope,
                    bindingId = confirmed.active.bindingId,
                    observedAtEpochMillis = 30_000,
                    decidedAtEpochMillis = 30_000,
                ),
            )
            assertNull("撤销后读侧必须为空", service.read(scope).active)

            // 活键确实释放：本单元另一条候选可以正常落账，仍只有一条 ACTIVE
            val second = observation(
                scope,
                ErrorCause.PROCEDURAL,
                ErrorCauseDetail.PROC_STEP_ORDER,
                refId = "message-3",
            )
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(second))
            assertEquals(
                ErrorCauseReviewWriteResult.APPLIED,
                service.confirmCandidate(scope, second.observationId, 32_000),
            )
            val afterSecond = service.read(scope)
            assertNotNull("撤销释放了活键，新的确认能落账", afterSecond.active)
            assertEquals(second.observationId, afterSecond.active!!.sourceObservationId)
            assertEquals(0, afterSecond.candidates.size)
            assertEquals(
                "另一练习单元的候选始终不进入本作用域",
                1,
                store.readProposedObservations(LEARNER_ID, OTHER_UNIT_ID).size,
            )
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun seedContentChain(
        context: Context,
        store: StudyDatabasePort,
        databaseName: String,
    ) {
        seedStudyFacts(
            context = context,
            database = store,
            databaseName = databaseName,
            bundle = StudySeedBundle(
                problems = listOf(
                    ProblemSeedRecord(
                        problemId = PROBLEM_ID,
                        canonicalFingerprint = "fp-error-cause-5c",
                        subject = "MATH",
                        createdAtEpochMillis = 0L,
                    ),
                ),
                revisions = listOf(
                    ProblemRevisionSeedRecord(
                        revisionId = REVISION_ID,
                        problemId = PROBLEM_ID,
                        revisionNumber = 1,
                        title = "5c 错因候选测试题",
                        problemMarkdown = "求证。",
                        answerSpecId = null,
                        answerSpecSnapshot = null,
                        answerVerificationStatus = "USER_ASSERTED",
                        sourceType = "CAPTURE",
                        sourceReference = null,
                        contentFingerprint = "fp-error-cause-5c-r1",
                        createdAtEpochMillis = 0L,
                    ),
                ),
                practiceUnits = listOf(
                    PracticeUnitSeedRecord(
                        practiceUnitId = UNIT_ID,
                        problemId = PROBLEM_ID,
                        problemRevisionId = REVISION_ID,
                        unitKey = UNIT_ID,
                        unitKind = "SINGLE",
                        title = "5c 错因候选测试单元",
                        promptMarkdown = "求证。",
                        estimatedSeconds = 60,
                        createdAtEpochMillis = 0L,
                    ),
                    PracticeUnitSeedRecord(
                        practiceUnitId = OTHER_UNIT_ID,
                        problemId = PROBLEM_ID,
                        problemRevisionId = REVISION_ID,
                        unitKey = OTHER_UNIT_ID,
                        unitKind = "SINGLE",
                        title = "5c 另一练习单元",
                        promptMarkdown = "求证。",
                        estimatedSeconds = 60,
                        createdAtEpochMillis = 0L,
                    ),
                ),
                errorBookEntries = emptyList(),
            ),
        )
    }

    /**
     * 候选行：`attempt_id` 留空。`error_cause_observation.attempt_id` 有到 `attempt_event` 的
     * RESTRICT 外键，本用例没有作答事件可挂（真库会直接 FK 拒绝）；候选本身允许与作答解耦。
     * 同一作用域内靠 cause/detail/证据引用区分身份（内容寻址 id）。
     */
    private fun observation(
        scope: ErrorCauseScope,
        cause: ErrorCause,
        detail: ErrorCauseDetail?,
        refId: String = "message-1",
    ): ErrorCauseObservationRecord = ErrorCauseObservationRecord(
        observationId = ErrorCauseIdentity.observationId(
            scope = scope,
            attemptId = null,
            cause = cause,
            detail = detail,
            channel = ErrorCauseChannel.TUTOR_TRACE,
            extractorVersion = ErrorCauseExtractor.LOCAL_EXTRACTOR_VERSION,
            refs = listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, refId)),
        ),
        scope = scope,
        attemptId = null,
        cause = cause,
        detail = detail,
        confidence = 0.85,
        channel = ErrorCauseChannel.TUTOR_TRACE,
        extractorVersion = ErrorCauseExtractor.LOCAL_EXTRACTOR_VERSION,
        taxonomyVersion = ErrorCause.TAXONOMY_VERSION,
        rationaleMarkdown = "本地理由",
        evidenceRefs = listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, refId)),
        observedAtEpochMillis = 1_000,
        status = ErrorCauseObservationStatus.PROPOSED,
    )

    private companion object {
        const val LEARNER_ID = "learner-1"
        const val PROBLEM_ID = "problem-error-cause-5c"
        const val REVISION_ID = "revision-error-cause-5c"
        const val UNIT_ID = "unit-error-cause-5c"
        const val OTHER_UNIT_ID = "unit-error-cause-5c-other"
    }
}
