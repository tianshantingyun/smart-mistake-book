package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseAppendResult
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseBindingRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseObservationRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseWriteResult
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseBindingState
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDecisionSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
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
 * schema 73 错因账本的**真库事务验收**（批次 40 / WS2 item 16）。
 *
 * 与 JVM 假端口用例的分工：这里用 Room + SQLite 真驱动证明三件事——
 * 1. 同内容 observation 重放幂等（主键冲突不写第二行）；
 * 2. 同一 scope 的第二条 ACTIVE 被 `active_key` 唯一索引挡下；
 * 3. **事务回滚不留半行**：故意让 supersede 的新 binding 与旧 binding 同主键，插入失败后
 *    旧 binding 仍是 ACTIVE、新 observation 仍是 PROPOSED（两步都被回滚）。
 */
@RunWith(AndroidJUnit4::class)
class ErrorCauseLedgerInstrumentedTest {

    @Test
    fun appendAcceptRetractAndSupersedeKeepOneActiveRow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StudyDatabaseFactory.openInMemory(context).use { store ->
            seedContentChain(store)

            val scope = ErrorCauseScope(
                learnerId = "learner-1",
                practiceUnitId = UNIT_ID,
                problemRevisionId = REVISION_ID,
            )
            val firstObservation = observation(scope, "observation-1", ErrorCause.CONCEPTUAL)
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(firstObservation))
            assertEquals(
                "同内容重放必须幂等",
                ErrorCauseAppendResult.DUPLICATE,
                store.appendObservation(firstObservation),
            )

            val firstBinding = binding(scope, "binding-1", firstObservation.observationId)
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.acceptObservationAndActivateBinding(
                    firstBinding,
                    5_000,
                    ErrorCauseDecisionSource.USER,
                ),
            )
            assertEquals(firstBinding, store.readActiveBinding(scope))

            val secondObservation = observation(scope, "observation-2", ErrorCause.CONCEPTUAL)
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(secondObservation))
            assertEquals(
                "同一 scope 不能有第二条 ACTIVE（active_key 唯一）",
                ErrorCauseWriteResult.CONFLICT,
                store.acceptObservationAndActivateBinding(
                    binding(
                        scope,
                        "binding-2",
                        secondObservation.observationId,
                        cause = ErrorCause.CONCEPTUAL,
                    ),
                    6_000,
                    ErrorCauseDecisionSource.USER,
                ),
            )
            val dao = store.database.errorCauseDao()
            assertEquals(
                "被拒的 observation 必须留在 PROPOSED，不能半升为 ACCEPTED",
                ErrorCauseObservationStatus.PROPOSED.name,
                dao.findObservation(secondObservation.observationId)?.status,
            )
            assertEquals(1, dao.activeBindingTotal())

            // 回滚用例：新 binding 复用旧主键 → insert 失败 → 前两步（accept 新 observation、
            // supersede 旧 binding）必须一起回滚。
            val rollbackObservation = observation(scope, "observation-3", ErrorCause.MISIDENTIFICATION)
            store.appendObservation(rollbackObservation)
            val rollbackFailure = runCatching {
                store.supersedeActiveBinding(
                    newObservation = rollbackObservation,
                    newBinding = binding(
                        scope,
                        "binding-1",
                        rollbackObservation.observationId,
                        cause = ErrorCause.MISIDENTIFICATION,
                    ),
                    oldBindingId = "binding-1",
                    decidedAtEpochMillis = 7_000,
                    decisionSource = ErrorCauseDecisionSource.USER,
                )
            }
            assertTrue("主键冲突必须抛出，而不是静默成功", rollbackFailure.isFailure)
            val activeAfterRollback = store.readActiveBinding(scope)!!
            assertEquals("旧 binding 必须回滚为 ACTIVE", firstBinding, activeAfterRollback)
            assertEquals(
                "新 observation 必须回滚为 PROPOSED",
                ErrorCauseObservationStatus.PROPOSED.name,
                dao.findObservation(rollbackObservation.observationId)?.status,
            )

            val retraction = observation(
                scope = scope,
                observationId = "retraction-1",
                cause = ErrorCause.CONCEPTUAL,
                status = ErrorCauseObservationStatus.RETRACTED,
                supersedes = firstObservation.observationId,
            )
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.retractActiveBinding(retraction, "binding-1", 8_000),
            )
            assertNull("撤回后读侧必须看不到 ACTIVE", store.readActiveBinding(scope))
            val voided = dao.findBinding("binding-1")!!
            assertEquals(ErrorCauseBindingState.VOIDED.name, voided.state)
            assertNull(voided.activeKey)
            assertNotNull("撤回行必须保留（不删除）", dao.findObservation("retraction-1"))
            assertEquals(
                "撤回重放必须幂等",
                ErrorCauseWriteResult.APPLIED,
                store.retractActiveBinding(retraction, "binding-1", 8_000),
            )
        }
    }

    private suspend fun seedContentChain(store: StudyDatabasePort) {
        store.seedStudyFacts(
            StudySeedBundle(
                problems = listOf(
                    ProblemSeedRecord(
                        problemId = PROBLEM_ID,
                        canonicalFingerprint = "fp-error-cause",
                        subject = "MATH",
                        createdAtEpochMillis = 0L,
                    ),
                ),
                revisions = listOf(
                    ProblemRevisionSeedRecord(
                        revisionId = REVISION_ID,
                        problemId = PROBLEM_ID,
                        revisionNumber = 1,
                        title = "错因账本测试题",
                        problemMarkdown = "求证。",
                        answerSpecId = null,
                        answerSpecSnapshot = null,
                        answerVerificationStatus = "USER_ASSERTED",
                        sourceType = "CAPTURE",
                        sourceReference = null,
                        contentFingerprint = "fp-error-cause-r1",
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
                        title = "错因账本测试单元",
                        promptMarkdown = "求证。",
                        estimatedSeconds = 60,
                        createdAtEpochMillis = 0L,
                    ),
                ),
                errorBookEntries = emptyList(),
            ),
        )
    }

    private fun observation(
        scope: ErrorCauseScope,
        observationId: String,
        cause: ErrorCause,
        status: ErrorCauseObservationStatus = ErrorCauseObservationStatus.PROPOSED,
        supersedes: String? = null,
    ) = ErrorCauseObservationRecord(
        observationId = observationId,
        scope = scope,
        attemptId = null,
        cause = cause,
        detail = if (cause == ErrorCause.PROCEDURAL) ErrorCauseDetail.PROC_STEP_ORDER else null,
        confidence = 0.85,
        channel = ErrorCauseChannel.TUTOR_TRACE,
        extractorVersion = "error-cause-local-v1",
        taxonomyVersion = ErrorCause.TAXONOMY_VERSION,
        rationaleMarkdown = "本地理由",
        evidenceRefs = listOf(
            ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, "message-1"),
        ),
        observedAtEpochMillis = 1_000,
        status = status,
        decidedAtEpochMillis = if (status == ErrorCauseObservationStatus.PROPOSED) null else 2_000,
        decisionSource = if (status == ErrorCauseObservationStatus.PROPOSED) null else {
            ErrorCauseDecisionSource.USER
        },
        supersedesObservationId = supersedes,
    )

    private fun binding(
        scope: ErrorCauseScope,
        bindingId: String,
        sourceObservationId: String,
        cause: ErrorCause = ErrorCause.CONCEPTUAL,
    ) = ErrorCauseBindingRecord(
        bindingId = bindingId,
        scope = scope,
        cause = cause,
        detail = null,
        confidence = 0.85,
        acceptanceSource = ErrorCauseAcceptanceSource.USER_CONFIRMED,
        gateVersion = null,
        sourceObservationId = sourceObservationId,
        acceptedAtEpochMillis = 5_000,
        state = ErrorCauseBindingState.ACTIVE,
        activeKey = scope.activeKey(),
    )

    private companion object {
        const val PROBLEM_ID = "problem-error-cause"
        const val REVISION_ID = "revision-error-cause"
        const val UNIT_ID = "unit-error-cause"
    }
}
