package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import com.tingyun.smartmistakebook.core.model.TutorDifficultyTier
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-68 批次 23 T3 的**下游接线**测试（复核发现 #9 补）：`NewIntroductionPolicyTest`
 * 已证明 policy 的 `unschedulableCount` 口径，但此前没有任何用例构造
 * `StudyDatabasePort`/`LearnerSnapshot` 去调 `StudyReviewPlannerService.intakeUnschedulableCount`
 * 或走 `StudySnapshotBuilder.build`——"快照上的数字与 planner 同口径"只是读码结论。
 *
 * 本用例用假库 + 真 `RoomBackedStudyExperienceRepository` 构造快照：
 * - 三个从未作答的新题：两道 EASY（模型档 90 s）、一道 HARD（300 s），日预算 120 s；
 * - FIFO 先引入一道 EASY；第二道 EASY 因切片用完而顺延（**可调度**，不是 unschedulable）；
 * - HARD 的真实时长 300 s > 整日预算 120 s，永远进不了每日计划 → 只有它计入
 *   `intakeUnschedulableCount`。
 */
class StudySnapshotIntakeUnschedulableTest {

    @Test
    fun `snapshot counts only backlog items that can never fit the daily budget`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), zone)
        val learnerId = "learner:local"

        database.addMistake(mistake("unit-easy-1", createdAtEpochMillis = 1_000))
        database.addMistake(mistake("unit-easy-2", createdAtEpochMillis = 2_000))
        database.addMistake(mistake("unit-hard", createdAtEpochMillis = 3_000))
        database.teachingAdvisories += advisory(learnerId, "unit-easy-1", TutorDifficultyTier.EASY)
        database.teachingAdvisories += advisory(learnerId, "unit-easy-2", TutorDifficultyTier.EASY)
        database.teachingAdvisories += advisory(learnerId, "unit-hard", TutorDifficultyTier.HARD)

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
            clock = clock,
            studyZoneId = zone,
            reviewTimeBudgetSeconds = 120,
        )
        try {
            repository.initialize()

            val review = repository.snapshot.value.review
            assertEquals(
                "FIFO 只应引入第一道 EASY；第二道 EASY 与 HARD 留在积压",
                listOf("unit-easy-1"),
                review.scheduledPracticeUnitIds,
            )
            assertEquals("积压 = 顺延的 EASY + 不可调度的 HARD", 2, review.intakeBacklogCount)
            assertEquals(
                "只有真实时长超过整日预算的 HARD 计入不可调度；顺延不是不可调度",
                1,
                review.intakeUnschedulableCount,
            )
            assertTrue(
                "快照不得报出超过积压的不可调度数",
                review.intakeUnschedulableCount <= review.intakeBacklogCount,
            )
            assertFalse(
                "被引入的 EASY 不得同时留在积压计数里",
                review.scheduledPracticeUnitIds.contains("unit-easy-2") ||
                    review.scheduledPracticeUnitIds.contains("unit-hard"),
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    private fun mistake(unitId: String, createdAtEpochMillis: Long) = MistakeRecord(
        entryId = "entry:$unitId",
        problemId = "problem:$unitId",
        problemRevisionId = "revision:$unitId",
        practiceUnitId = unitId,
        sourceKey = null,
        subject = "MATH",
        title = "题 $unitId",
        problemMarkdown = "markdown",
        status = "ACTIVE",
        createdAtEpochMillis = createdAtEpochMillis,
        nextReviewAtEpochMillis = null,
        retrievability = null,
        knowledgeNodeIds = setOf("kc:$unitId"),
    )

    private fun advisory(
        learnerId: String,
        unitId: String,
        tier: TutorDifficultyTier,
    ) = TeachingAdvisoryRecord(
        advisoryId = "advisory:$unitId",
        learnerId = learnerId,
        practiceUnitId = unitId,
        knowledgeNodeId = "kc:$unitId",
        advisoryKind = TeachingAdvisoryRecord.KIND_DIFFICULTY_TIER,
        payloadMarkdown = tier.name,
        confidence = 1.0,
        sourceId = "source:$unitId",
        createdAtEpochMillis = 1_000,
    )
}
