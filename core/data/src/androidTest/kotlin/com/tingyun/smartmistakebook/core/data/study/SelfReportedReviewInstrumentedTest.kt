package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.ReviewSelfReportKey
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoRequest
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewSubmission
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-05：三键自评的**真 Room + 生产写路径**（不是 FakeStudyDatabasePort）。
 *
 * 消灭的失败：自评此前只有 JVM 假库覆盖，真实路径（保存 evidence snapshot →
 * `recordReviewAttempt` 同事务推进会话 → `review_log` 落档）与 undo（VOID 更正 +
 * 会话指针回退）在设备上从未跑过。D1 设备轨阻塞时，这是该通道唯一可执行的真库证据。
 *
 * 覆盖：GOOD 原档 0.8 / 调度档 Good；同 request id 重放不写第二笔；undo 回退且保留原
 * attempt；undo 后用新 request id 重评能再次推进（不把撤销当成"从未发生"）。
 */
@RunWith(AndroidJUnit4::class)
class SelfReportedReviewInstrumentedTest {

    @Test
    fun selfReportWritesThroughRoomAdvancesAndUndoesWithAReattempt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "self-reported-review-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )

        try {
            seedStudyFacts(context, database, databaseName, selfReportSeed())
            repository.initialize()
            val started = requireNotNull(
                repository.startOrResumeReviewSession("self-report-start", START_AT),
            )
            val practiceUnitId = requireNotNull(
                repository.snapshot.value.review.scheduledPracticeUnitIds.singleOrNull(),
            )
            assertEquals("captured-unit", practiceUnitId)
            val presentationId =
                "presentation:captured-review:captured-revision:captured-unit:session:" +
                    started.sessionId

            val submission = StudySelfReportedReviewSubmission(
                requestId = "self-report-room-1",
                sessionId = started.sessionId,
                expectedStateVersion = started.stateVersion,
                presentationId = presentationId,
                practiceUnitId = practiceUnitId,
                key = ReviewSelfReportKey.GOOD,
                responseOrdinal = 1,
                durationSeconds = 20,
                occurredAtEpochMillis = START_AT + 1_000,
            )

            val result = repository.submitReviewSelfReport(submission)
            assertTrue("首次自评必须真正落库", result.created)
            assertEquals(LearningEvidenceReason.SELF_REPORTED_RECALL, result.evidenceReason)
            assertEquals(started.currentOrdinal + 1, result.progress.currentOrdinal)
            assertEquals(StudyReviewSessionStatus.COMPLETED, result.progress.status)

            val selfReportSamples = database.readReviewLogSamples(learnerId, limit = 10)
                .filter { it.sourceKind == ReviewLogSink.SOURCE_KIND_SELF_REPORT }
            assertEquals(1, selfReportSamples.size)
            assertEquals("Good 原档 0.8 逐位落库", 0.8, selfReportSamples.single().evidenceWeight, 1e-9)
            assertEquals("调度档 = Good", 3, selfReportSamples.single().rating)
            assertNotNull(
                "attempt 行必须经真库事务写出",
                database.readAttemptP0(result.attemptId),
            )

            // 双击/进程中断后的重试：同一 request id 只生效一次。
            val replay = repository.submitReviewSelfReport(submission)
            assertFalse("重放不得创建第二次 attempt", replay.created)
            assertEquals(result.attemptId, replay.attemptId)
            assertEquals(
                "重放不得再写一条 review_log",
                1,
                database.readReviewLogSamples(learnerId, limit = 10)
                    .count { it.sourceKind == ReviewLogSink.SOURCE_KIND_SELF_REPORT },
            )

            // undo：VOID 更正 + 会话指针回退；原 attempt 保留（不删除）。
            val undo = repository.undoReviewSelfReport(
                StudyReviewUndoRequest(
                    requestId = "self-report-room-undo-1",
                    sessionId = started.sessionId,
                    expectedStateVersion = result.progress.stateVersion,
                    presentationId = presentationId,
                    practiceUnitId = practiceUnitId,
                    attemptId = result.attemptId,
                    occurredAtEpochMillis = START_AT + 2_000,
                ),
            )
            assertTrue(undo.created)
            assertEquals(started.currentOrdinal, undo.progress.currentOrdinal)
            assertEquals(StudyReviewSessionStatus.ACTIVE, undo.progress.status)
            assertEquals(practiceUnitId, undo.nextPracticeUnitId)
            assertNotNull(
                "撤销不删除原 attempt（只追加 VOID 更正）",
                database.readAttemptP0(result.attemptId),
            )

            // undo → 重评：新 request id 必须能再次推进，而不是被"撤销"吞掉。
            val reattempt = repository.submitReviewSelfReport(
                submission.copy(
                    requestId = "self-report-room-2",
                    expectedStateVersion = undo.progress.stateVersion,
                    responseOrdinal = 2,
                    occurredAtEpochMillis = START_AT + 3_000,
                ),
            )
            assertTrue(reattempt.created)
            assertNotEquals(result.attemptId, reattempt.attemptId)
            assertEquals(started.currentOrdinal + 1, reattempt.progress.currentOrdinal)
            assertEquals(StudyReviewSessionStatus.COMPLETED, reattempt.progress.status)
        } finally {
            repository.close()
            applicationScope.cancel()
            context.deleteDatabase(databaseName)
        }
    }

    private fun selfReportSeed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "captured-problem",
                canonicalFingerprint = "fp-captured",
                subject = "MATH",
                createdAtEpochMillis = 0L,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "captured-revision",
                problemId = "captured-problem",
                revisionNumber = 1,
                title = "拍摄保存的题",
                problemMarkdown = "解方程。",
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "USER_ASSERTED",
                sourceType = "CAPTURE",
                sourceReference = null,
                contentFingerprint = "fp-captured-r1",
                createdAtEpochMillis = 0L,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "captured-unit",
                problemId = "captured-problem",
                problemRevisionId = "captured-revision",
                unitKey = "captured-unit",
                unitKind = "SINGLE",
                title = "拍摄保存的题",
                promptMarkdown = "解方程。",
                estimatedSeconds = 60,
                createdAtEpochMillis = 0L,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "captured-entry",
                practiceUnitId = "captured-unit",
                problemId = "captured-problem",
                currentRevisionId = "captured-revision",
                sourceKey = "capture:prod-photo",
                status = "ACTIVE",
                acceptedAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            ),
        ),
    )

    private companion object {
        const val learnerId = "learner:local"

        /** 2026-01-10 10:00 +08：远离纪元（04:00 学习日界下纪元附近的时刻日序为负）。 */
        val START_AT = java.time.Instant.parse("2026-01-10T02:00:00Z").toEpochMilli()
    }
}
