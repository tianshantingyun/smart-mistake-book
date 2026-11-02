package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
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

    /**
     * 批次 41 / WS6 item 39：候选 / 被拒 / ACTIVE 唯一 / 撤销后 active 为空。
     *
     * 与上一条用例的分工：上一条证明"能写的三种迁移"，本条只钉读侧可见性与活键释放——
     * 候选不是现行标签；被拒不半升、不留 binding；撤销后读侧与全表都没有 ACTIVE，
     * 且活键被释放，后续标签能正常落账。
     */
    @Test
    fun candidateRejectedAndRetractedStatesLeaveNoActiveLabelBehind() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StudyDatabaseFactory.openInMemory(context).use { store ->
            seedContentChain(store)
            val scope = ErrorCauseScope(
                learnerId = "learner-1",
                practiceUnitId = UNIT_ID,
                problemRevisionId = REVISION_ID,
            )
            val dao = store.database.errorCauseDao()

            // 候选可以独立存在，但读侧不得把它当现行标签
            val candidate = observation(scope, "candidate-1", ErrorCause.CONCEPTUAL)
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(candidate))
            assertNull("候选不得被读成现行标签", store.readActiveBinding(scope))
            assertEquals(0, dao.activeBindingTotal())
            assertEquals(
                ErrorCauseObservationStatus.PROPOSED.name,
                dao.findObservation(candidate.observationId)?.status,
            )

            val booking = binding(scope, "binding-accepted", candidate.observationId)
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.acceptObservationAndActivateBinding(
                    booking,
                    5_000,
                    ErrorCauseDecisionSource.USER,
                ),
            )
            assertEquals(booking, store.readActiveBinding(scope))
            assertEquals(1, dao.activeBindingTotal())

            // 被拒：同 scope 第二条候选撞 active_key 唯一性；binding 不留行、observation 不半升
            val rejected = observation(scope, "candidate-2", ErrorCause.MISIDENTIFICATION)
            assertEquals(ErrorCauseAppendResult.INSERTED, store.appendObservation(rejected))
            assertEquals(
                "同一 scope 不能有第二条 ACTIVE（active_key 唯一）",
                ErrorCauseWriteResult.CONFLICT,
                store.acceptObservationAndActivateBinding(
                    binding(
                        scope,
                        "binding-rejected",
                        rejected.observationId,
                        cause = ErrorCause.MISIDENTIFICATION,
                    ),
                    6_000,
                    ErrorCauseDecisionSource.USER,
                ),
            )
            assertEquals("被拒的 binding 不得留下行", 0, dao.bindingCount("binding-rejected"))
            assertEquals(
                "被拒的 observation 必须留在 PROPOSED",
                ErrorCauseObservationStatus.PROPOSED.name,
                dao.findObservation(rejected.observationId)?.status,
            )
            assertEquals(1, dao.activeBindingTotal())
            assertEquals("binding-accepted", store.readActiveBinding(scope)?.bindingId)

            // 撤销：读侧为空、全表无 ACTIVE、活键腾空，撤回行保留
            val retraction = observation(
                scope = scope,
                observationId = "retraction-active",
                cause = ErrorCause.CONCEPTUAL,
                status = ErrorCauseObservationStatus.RETRACTED,
                supersedes = candidate.observationId,
            )
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.retractActiveBinding(retraction, "binding-accepted", 7_000),
            )
            assertNull("撤销后 readActiveBinding 必须为空", store.readActiveBinding(scope))
            assertEquals("撤销后不得再有 ACTIVE", 0, dao.activeBindingTotal())
            val voided = dao.findBinding("binding-accepted")!!
            assertEquals(ErrorCauseBindingState.VOIDED.name, voided.state)
            assertNull("撤销必须腾出 active_key", voided.activeKey)
            assertEquals(7_000L, voided.endedAtEpochMillis)
            assertNotNull("撤回 observation 必须保留（不删除）", dao.findObservation("retraction-active"))
            assertEquals(
                "撤销不得连带删除/升级被拒候选",
                ErrorCauseObservationStatus.PROPOSED.name,
                dao.findObservation(rejected.observationId)?.status,
            )

            // 活键确实被释放：同一被拒候选现在可以落账，且仍只有一条 ACTIVE
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.acceptObservationAndActivateBinding(
                    binding(
                        scope,
                        "binding-after-retract",
                        rejected.observationId,
                        cause = ErrorCause.MISIDENTIFICATION,
                    ),
                    8_000,
                    ErrorCauseDecisionSource.USER,
                ),
            )
            assertEquals(1, dao.activeBindingTotal())
            assertEquals("binding-after-retract", store.readActiveBinding(scope)?.bindingId)
        }
    }

    /**
     * 批次 41 / WS6 item 40：撤销不留半行。
     *
     * 上一条用例的 rollback 段证明的是改判路径；本用例专钉**撤回**路径：让事务第二步
     * （binding ACTIVE→VOIDED）在真 SQLite 上失败，断言第一步（插入 RETRACTED observation）
     * 一起回滚，binding 仍是 ACTIVE 且活键仍在。失败用测试侧触发器注入，不改生产代码。
     *
     * 为什么必须真库：JVM 假端口没有事务，"先写后失败"的回滚只能由 Room + SQLite 证明。
     */
    @Test
    fun aFailingVoidRollsBackTheRetractionRowAndKeepsTheBindingActive() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StudyDatabaseFactory.openInMemory(context).use { store ->
            seedContentChain(store)
            val scope = ErrorCauseScope(
                learnerId = "learner-1",
                practiceUnitId = UNIT_ID,
                problemRevisionId = REVISION_ID,
            )
            val dao = store.database.errorCauseDao()
            val candidate = observation(scope, "candidate-rollback", ErrorCause.CONCEPTUAL)
            store.appendObservation(candidate)
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.acceptObservationAndActivateBinding(
                    binding(scope, "binding-rollback", candidate.observationId),
                    5_000,
                    ErrorCauseDecisionSource.USER,
                ),
            )

            // 注入：只拦 ACTIVE→VOIDED 这一步；第一步的 RETRACTED 行已经写入事务。
            store.database.useWriterConnection { connection ->
                connection.executeSQL(
                    """
                    CREATE TRIGGER fail_error_cause_void
                    BEFORE UPDATE OF state ON error_cause_binding
                    WHEN NEW.state = 'VOIDED'
                    BEGIN
                        SELECT RAISE(ABORT, 'injected void failure');
                    END
                    """.trimIndent(),
                )
            }

            val retraction = observation(
                scope = scope,
                observationId = "retraction-rollback",
                cause = ErrorCause.CONCEPTUAL,
                status = ErrorCauseObservationStatus.RETRACTED,
                supersedes = candidate.observationId,
            )
            val failure = runCatching {
                store.retractActiveBinding(retraction, "binding-rollback", 9_000)
            }
            assertTrue("被注入的第二步失败必须抛出，不能静默 APPLIED", failure.isFailure)

            assertNull(
                "撤回 observation 必须随事务回滚，不能留下半行",
                dao.findObservation("retraction-rollback"),
            )
            val activeAfterRollback = store.readActiveBinding(scope)
            assertNotNull("binding 必须回滚为 ACTIVE", activeAfterRollback)
            assertEquals("binding-rollback", activeAfterRollback!!.bindingId)
            assertEquals(1, dao.activeBindingTotal())
            val row = dao.findBinding("binding-rollback")!!
            assertEquals(ErrorCauseBindingState.ACTIVE.name, row.state)
            assertNotNull("活键必须回滚", row.activeKey)
            assertNull("结束时间必须回滚", row.endedAtEpochMillis)

            // 移除注入后同一撤回必须成功：失败只来自注入，不是数据/编码问题。
            store.database.useWriterConnection { connection ->
                connection.executeSQL("DROP TRIGGER fail_error_cause_void")
            }
            assertEquals(
                ErrorCauseWriteResult.APPLIED,
                store.retractActiveBinding(retraction, "binding-rollback", 9_000),
            )
            assertNull(store.readActiveBinding(scope))
            assertEquals(0, dao.activeBindingTotal())
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
