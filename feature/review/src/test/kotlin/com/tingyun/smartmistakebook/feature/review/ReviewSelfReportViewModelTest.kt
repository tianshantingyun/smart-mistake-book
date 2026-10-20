package com.tingyun.smartmistakebook.feature.review

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.ReviewSelfReportKey
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionProgress
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoRequest
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoResult
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewResult
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewSubmission
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-05 三键自评的**界面状态机合同**：稳定 request id + 持久回执。
 *
 * 消灭的失败（设计 §3.4 / 计划 T1-2）：
 * - 双击/连点重复提交同一个 attempt；
 * - 旋转/进程重建后丢失"正在提交"的身份，重试变成第二笔；
 * - undo 之后仍拿旧 stateVersion 提交，或复用旧 request id 导致重评被当成原 attempt 的回执；
 * - 撤销失败时把本地状态清掉，让学生以为已经撤销成功。
 */
class ReviewSelfReportViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun doubleTapSubmitsExactlyOnePersistentRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)
        val requests = mutableListOf<StudySelfReportedReviewSubmission>()

        try {
            viewModel.select(ReviewSelfReportKey.GOOD)
            repeat(2) {
                viewModel.requestSubmit(
                    presentationId = PRESENTATION_ID,
                    practiceUnitId = UNIT_ID,
                    fallbackSessionId = SESSION_ID,
                    fallbackStateVersion = 0,
                    selectedChoiceId = "A",
                    durationSeconds = 12,
                    occurredAtEpochMillis = 10_000,
                ) { request ->
                    requests += request
                    delay(100)
                    selfReportResult(attemptId = "attempt-1")
                }
            }
            advanceUntilIdle()

            assertEquals("双击只产生一个提交", 1, requests.size)
            assertEquals(SelfReportStatus.RECORDED, viewModel.status)
            assertEquals("attempt-1", viewModel.recorded?.attemptId)
            assertEquals(
                ReviewSelfReportKey.GOOD,
                handle.get<String>("review_self_report_selected")?.let(ReviewSelfReportKey::valueOf),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun interruptedSubmitRetriesWithTheSameRequestIdentity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)
        val requests = mutableListOf<StudySelfReportedReviewSubmission>()
        var attempt = 0

        try {
            viewModel.select(ReviewSelfReportKey.AGAIN)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 20,
                occurredAtEpochMillis = 10_000,
            ) { request ->
                requests += request
                attempt += 1
                if (attempt == 1) error("process killed before the receipt was read")
                selfReportResult(attemptId = "attempt-1")
            }
            advanceUntilIdle()
            assertEquals(SelfReportStatus.FAILED, viewModel.status)
            assertNull("失败不能假装已记录", viewModel.recorded)

            // 旋转/进程重建：同一个 SavedStateHandle 生成新的 ViewModel。
            val restored = ReviewSelfReportViewModel(handle)
            assertEquals(SelfReportStatus.FAILED, restored.status)
            restored.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 20,
                occurredAtEpochMillis = 99_000,
            ) { request ->
                requests += request
                selfReportResult(attemptId = "attempt-1")
            }
            advanceUntilIdle()

            assertEquals(2, requests.size)
            assertEquals(
                "重试必须复用第一次的 request id（数据库幂等键）",
                requests[0].requestId,
                requests[1].requestId,
            )
            assertEquals(
                "重试沿用首次提交的时间，不制造第二个练习日",
                requests[0].occurredAtEpochMillis,
                requests[1].occurredAtEpochMillis,
            )
            assertEquals(SelfReportStatus.RECORDED, restored.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun undoRewindsTheSessionAndReanswerUsesTheNewVersionAndIdentity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)
        val submissions = mutableListOf<StudySelfReportedReviewSubmission>()
        val undos = mutableListOf<StudyReviewUndoRequest>()

        try {
            viewModel.select(ReviewSelfReportKey.GOOD)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 10,
                occurredAtEpochMillis = 10_000,
            ) { request ->
                submissions += request
                selfReportResult(attemptId = "attempt-1")
            }
            advanceUntilIdle()
            assertEquals(
                "首次提交使用宿主传入的当前会话版本（新会话为 0）",
                0L,
                submissions.single().expectedStateVersion,
            )

            viewModel.requestUndo(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                occurredAtEpochMillis = 20_000,
            ) { request ->
                undos += request
                undoResult()
            }
            advanceUntilIdle()

            assertEquals(1, undos.size)
            assertNull("撤销成功后必须清掉已记录 attempt", viewModel.recorded)
            assertNull(viewModel.selectedKey)
            assertEquals(
                "会话回退：ordinal 0、版本 2（版本单调递增，不与 ordinal 绑定）",
                StudyReviewSessionProgress(
                    sessionId = SESSION_ID,
                    planId = PLAN_ID,
                    currentOrdinal = 0,
                    queueSize = 3,
                    stateVersion = 2,
                    status = StudyReviewSessionStatus.ACTIVE,
                ),
                viewModel.latestProgress,
            )
            assertEquals(2, viewModel.currentAttemptOrdinal())

            // 撤销后重评：同一 presentation、新的 request id、新的 stateVersion；
            // 不把"看过答案后的重评"恢复成独立作答。
            viewModel.acknowledgeUndo()
            viewModel.select(ReviewSelfReportKey.HARD)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 999,
                selectedChoiceId = null,
                durationSeconds = 5,
                occurredAtEpochMillis = 30_000,
            ) { request ->
                submissions += request
                selfReportResult(attemptId = "attempt-2", ordinal = 1, version = 3)
            }
            advanceUntilIdle()

            assertEquals(2, submissions.size)
            assertEquals(
                "重评使用撤销后的会话版本，而不是外部捕获的旧版本",
                2L,
                submissions[1].expectedStateVersion,
            )
            assertNotEquals(
                "重评是新的 attempt，request id 必须不同",
                submissions[0].requestId,
                submissions[1].requestId,
            )
            assertEquals(2, submissions[1].responseOrdinal)
            assertEquals(ReviewSelfReportKey.HARD, submissions[1].key)
            assertEquals("attempt-2", viewModel.recorded?.attemptId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun failedUndoKeepsTheRecordedAttemptAndRetriesTheSameRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)
        val undos = mutableListOf<StudyReviewUndoRequest>()
        var attempts = 0

        try {
            viewModel.select(ReviewSelfReportKey.GOOD)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 10,
                occurredAtEpochMillis = 10_000,
            ) { selfReportResult(attemptId = "attempt-1") }
            advanceUntilIdle()

            repeat(2) { round ->
                viewModel.requestUndo(
                    presentationId = PRESENTATION_ID,
                    practiceUnitId = UNIT_ID,
                    occurredAtEpochMillis = 20_000L + round,
                ) { request ->
                    undos += request
                    attempts += 1
                    if (attempts == 1) error("write interrupted")
                    undoResult()
                }
                advanceUntilIdle()
                if (round == 0) {
                    assertNotNull("第一次撤销失败后本地仍认为这次作答有效", viewModel.recorded)
                }
            }

            assertEquals(2, undos.size)
            assertEquals(
                "撤销重试复用同一个 request id（确定性 correction id）",
                undos[0].requestId,
                undos[1].requestId,
            )
            assertEquals(SelfReportUndoStatus.UNDONE, viewModel.undoStatus)
            assertNull(viewModel.recorded)
            assertTrue(attempts == 2)
            assertFalse(handle.contains("review_self_report_pending_undo_request"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun recordedJudgementAndUndoReceiptSurviveRotation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)

        try {
            viewModel.select(ReviewSelfReportKey.HARD)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 10,
                occurredAtEpochMillis = 10_000,
            ) { selfReportResult(attemptId = "attempt-1") }
            advanceUntilIdle()

            val rotated = ReviewSelfReportViewModel(handle)
            assertEquals(SelfReportStatus.RECORDED, rotated.status)
            assertEquals(
                "旋转后回执必须仍指向学生实际按下的那一档（Good/Hard 证据口径相同）",
                ReviewSelfReportKey.HARD,
                rotated.recorded?.key,
            )

            rotated.requestUndo(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                occurredAtEpochMillis = 20_000,
            ) { undoResult() }
            advanceUntilIdle()

            val rotatedAgain = ReviewSelfReportViewModel(handle)
            assertNull(rotatedAgain.recorded)
            assertNull(
                "撤销后旋转不能把上一轮按下的键恢复成'已选中'",
                rotatedAgain.selectedKey,
            )
            assertEquals(SelfReportUndoStatus.UNDONE, rotatedAgain.undoStatus)
            assertEquals(
                ReviewUndoReceipt(
                    correctionId = "correction-void-1",
                    created = true,
                    nextPracticeUnitId = UNIT_ID,
                ),
                rotatedAgain.undoReceipt,
            )
            assertEquals(
                "撤销后的会话版本也要跨旋转保留，供重评使用",
                2L,
                rotatedAgain.latestProgress?.stateVersion,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun failedSubmitDoesNotAllowSwitchingTheJudgementUntilRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)
        val requests = mutableListOf<StudySelfReportedReviewSubmission>()
        var attempts = 0

        try {
            viewModel.select(ReviewSelfReportKey.GOOD)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 10,
                occurredAtEpochMillis = 10_000,
            ) { request ->
                requests += request
                attempts += 1
                if (attempts == 1) error("response lost")
                selfReportResult(attemptId = "attempt-1")
            }
            advanceUntilIdle()
            assertEquals(SelfReportStatus.FAILED, viewModel.status)
            assertTrue(viewModel.hasPendingSubmit)

            viewModel.select(ReviewSelfReportKey.AGAIN)
            assertEquals(
                "提交中断后不能换键：写侧可能已经落了一笔 GOOD",
                ReviewSelfReportKey.GOOD,
                viewModel.selectedKey,
            )

            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 10,
                occurredAtEpochMillis = 99_000,
            ) { request ->
                requests += request
                selfReportResult(attemptId = "attempt-1")
            }
            advanceUntilIdle()

            assertEquals(2, requests.size)
            assertEquals(requests[0].requestId, requests[1].requestId)
            assertEquals(ReviewSelfReportKey.GOOD, requests[1].key)
            assertFalse(viewModel.hasPendingSubmit)
            assertEquals(SelfReportStatus.RECORDED, viewModel.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun replayedUndoReceiptClearsTheAttemptWithoutAskingToRewindAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val viewModel = ReviewSelfReportViewModel(handle)
        var undoRequests = 0

        try {
            viewModel.select(ReviewSelfReportKey.AGAIN)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = SESSION_ID,
                fallbackStateVersion = 0,
                selectedChoiceId = null,
                durationSeconds = 10,
                occurredAtEpochMillis = 10_000,
            ) {
                selfReportResult(
                    attemptId = "attempt-1",
                    evidenceReason = LearningEvidenceReason.SELF_REPORTED_STUCK,
                )
            }
            advanceUntilIdle()

            // 写侧已经回退但响应丢失：重试拿到 created=false 的既有回执。
            viewModel.requestUndo(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                occurredAtEpochMillis = 20_000,
            ) {
                undoRequests += 1
                undoResult(created = false)
            }
            advanceUntilIdle()

            assertEquals(1, undoRequests)
            assertNull(viewModel.recorded)
            assertEquals(false, viewModel.undoReceipt?.created)
            assertEquals(SelfReportUndoStatus.UNDONE, viewModel.undoStatus)

            // 已撤销后再次点撤销不会再发第二次请求。
            viewModel.requestUndo(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                occurredAtEpochMillis = 30_000,
            ) { error("must not submit another undo request") }
            advanceUntilIdle()
            assertEquals(1, undoRequests)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun firstSubmissionUsesTheHostSessionIdentity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ReviewSelfReportViewModel(SavedStateHandle())
        val requests = mutableListOf<StudySelfReportedReviewSubmission>()

        try {
            viewModel.select(ReviewSelfReportKey.GOOD)
            viewModel.requestSubmit(
                presentationId = PRESENTATION_ID,
                practiceUnitId = UNIT_ID,
                fallbackSessionId = "session-today",
                fallbackStateVersion = 7,
                selectedChoiceId = "A",
                durationSeconds = 3,
                occurredAtEpochMillis = 10_000,
            ) { request ->
                requests += request
                selfReportResult(attemptId = "attempt-1")
            }
            advanceUntilIdle()

            val request = requests.single()
            assertEquals("session-today", request.sessionId)
            assertEquals(7L, request.expectedStateVersion)
            assertEquals(1, request.responseOrdinal)
            assertEquals("A", request.selectedChoiceId)
            assertEquals(ReviewSelfReportKey.GOOD, request.key)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun interruptedUndoDoesNotStayInRecordingForever() {
        // 撤销进行中进程被杀：SavedStateHandle 里留下 RECORDING。若原样恢复，
        // 撤销按钮会永久禁用、提示永远是"正在撤销"，而这次作答其实仍然有效。
        val handle = SavedStateHandle(
            mapOf(
                "review_self_report_status" to SelfReportStatus.RECORDED.name,
                "review_self_report_undo_status" to SelfReportUndoStatus.RECORDING.name,
                "review_self_report_attempt_id" to "attempt-1",
                "review_self_report_attempt_reason" to "SELF_REPORTED_RECALL",
                "review_self_report_attempt_key" to "HARD",
                "review_self_report_result_session" to SESSION_ID,
                "review_self_report_result_plan" to PLAN_ID,
                "review_self_report_result_ordinal" to 1,
                "review_self_report_result_queue_size" to 3,
                "review_self_report_result_version" to 1L,
                "review_self_report_result_status" to StudyReviewSessionStatus.ACTIVE.name,
                "review_self_report_result_next" to "unit-next",
            ),
        )

        val restored = ReviewSelfReportViewModel(handle)

        assertEquals(SelfReportUndoStatus.FAILED, restored.undoStatus)
        assertNotNull("撤销没确认成功前，这次作答仍然有效", restored.recorded)
        assertEquals(ReviewSelfReportKey.HARD, restored.recorded?.key)
    }

    // ---- fixtures ----

    private fun selfReportResult(
        attemptId: String,
        ordinal: Int = 1,
        version: Long = 1,
        evidenceReason: LearningEvidenceReason = LearningEvidenceReason.SELF_REPORTED_RECALL,
    ) = StudySelfReportedReviewResult(
        attemptId = attemptId,
        created = true,
        evidenceReason = evidenceReason,
        objectiveChoiceCorrect = null,
        progress = StudyReviewSessionProgress(
            sessionId = SESSION_ID,
            planId = PLAN_ID,
            currentOrdinal = ordinal,
            queueSize = 3,
            stateVersion = version,
            status = StudyReviewSessionStatus.ACTIVE,
        ),
        nextPracticeUnitId = "unit-next",
    )

    private fun undoResult(created: Boolean = true) = StudyReviewUndoResult(
        correctionId = "correction-void-1",
        created = created,
        progress = StudyReviewSessionProgress(
            sessionId = SESSION_ID,
            planId = PLAN_ID,
            currentOrdinal = 0,
            queueSize = 3,
            stateVersion = 2,
            status = StudyReviewSessionStatus.ACTIVE,
        ),
        nextPracticeUnitId = UNIT_ID,
    )

    private companion object {
        const val PRESENTATION_ID = "presentation:review:unit-1"
        const val UNIT_ID = "unit-1"
        const val SESSION_ID = "session-1"
        const val PLAN_ID = "plan-1"
    }
}
