package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import com.tingyun.smartmistakebook.core.model.TutorDifficultyTier
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 5b 容量 UI 的数据侧接线（KD-68 同族）：
 *
 * - 快照必须带出与 `StudyReviewPlannerService.reviewCapacityPreview` **同一份**容量事实
 *   （考试缺口、逐科 granted/unmet、今日容量、再平衡状态），不允许 UI 自己重算；
 * - 窗口内没有考试且积压追赶未规划时，预览本身就是 `ReviewCapacityPreview.EMPTY`，
 *   快照必须如实给 `null`（没有读数），而不是把全零当"今天容量为 0 题"。
 */
class StudySnapshotCapacityPreviewTest {

    @Test
    fun `snapshot carries the planner capacity preview without re-deriving it`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        database.addMistake(mistake("unit-1"))
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = scope,
            clock = CLOCK,
            studyZoneId = ZONE,
            schedulingSettingsStore = FixedExamSettingsStore(
                listOf(
                    ExamCalendarEntry(
                        entryId = "exam-math",
                        subject = "MATH",
                        examEpochDay = LOCAL_DAY + 7,
                        title = "数学",
                    ),
                ),
            ),
        )
        try {
            repository.initialize()
            val snapshotCapacity = requireNotNull(
                repository.snapshot.value.review.capacity,
            ) { "窗口内有考试时，快照必须带出容量预览，而不是让 UI 自己重算" }
            val plannerPreview = repository.reviewCapacityPreview()

            assertEquals(plannerPreview.examSubjectsInWindow, snapshotCapacity.examSubjectsInWindow)
            assertEquals(plannerPreview.examDeficitCount, snapshotCapacity.examDeficitCount)
            assertEquals(plannerPreview.examUnmetCount, snapshotCapacity.examUnmetCount)
            assertEquals(plannerPreview.totalGranted, snapshotCapacity.totalGranted)
            assertEquals(plannerPreview.dailyCapacity, snapshotCapacity.dailyCapacity)
            assertEquals(plannerPreview.maxDailyCapacity, snapshotCapacity.maxDailyCapacity)
            assertEquals(
                plannerPreview.subjects.map { listOf(it.subjectId, it.granted, it.unmet, it.feasibleBeforeExam) },
                snapshotCapacity.subjects.map { listOf(it.subjectId, it.granted, it.unmet, it.feasibleBeforeExam) },
            )
            assertEquals(
                plannerPreview.rebalanceState == RebalancePlanningState.PLANNED,
                snapshotCapacity.rebalancePlanned,
            )
            assertEquals(
                plannerPreview.deduplicatedCount,
                snapshotCapacity.deduplicatedCount,
            )
            assertTrue("窗口内有考试时今日容量必须是一个真实上限", snapshotCapacity.dailyCapacity > 0)
            assertTrue(
                "考试科目出现在逐科读数里",
                snapshotCapacity.subjects.any { it.subjectId == "MATH" },
            )
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun `no exam in the window leaves the capacity preview honestly absent`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        database.addMistake(mistake("unit-hard"))
        database.teachingAdvisories += advisory("unit-hard", TutorDifficultyTier.HARD)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = scope,
            clock = CLOCK,
            studyZoneId = ZONE,
            reviewTimeBudgetSeconds = 120,
        )
        try {
            repository.initialize()
            val review = repository.snapshot.value.review
            assertNull(
                "没有考试且积压追赶未规划 → 没有容量读数；不得用全零冒充读数",
                review.capacity,
            )
            assertEquals(
                "既有 intakeUnschedulableCount 派生不受容量接线影响",
                1,
                review.intakeUnschedulableCount,
            )
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun `mapper mirrors every field and never invents facts for the empty preview`() {
        assertNull(
            "EMPTY 预览不得变成一份全零读数",
            ReviewCapacityPreview.EMPTY.toStudyReviewCapacity(),
        )

        val preview = ReviewCapacityPreview(
            examSubjectsInWindow = 1,
            examDeficitCount = 7,
            examUnmetCount = 2,
            rebalanceBacklogCount = 4,
            rebalanceIneligibleCount = 1,
            rebalanceState = RebalancePlanningState.PLANNED,
            rebalanceGranted = 2,
            deduplicatedCount = 0,
            intakeUnschedulableCount = 3,
            subjects = listOf(
                SubjectCapacity(
                    subjectId = "MATH",
                    daysUntilExam = 7,
                    demand = 5,
                    granted = 3,
                    unmet = 2,
                    feasibleBeforeExam = false,
                ),
            ),
            totalGranted = 5,
            dailyCapacity = 6,
            maxDailyCapacity = 9,
        )

        val mapped = requireNotNull(preview.toStudyReviewCapacity())
        assertEquals(1, mapped.examSubjectsInWindow)
        assertEquals(7, mapped.examDeficitCount)
        assertEquals(2, mapped.examUnmetCount)
        assertTrue(mapped.rebalancePlanned)
        assertEquals(4, mapped.rebalanceBacklogCount)
        assertEquals(1, mapped.rebalanceIneligibleCount)
        assertEquals(2, mapped.rebalanceGranted)
        assertEquals(5, mapped.totalGranted)
        assertEquals(6, mapped.dailyCapacity)
        assertEquals(9, mapped.maxDailyCapacity)
        assertEquals(1, mapped.subjects.size)
        assertEquals("MATH", mapped.subjects.single().subjectId)
        assertEquals(7, mapped.subjects.single().daysUntilExam)
        assertEquals(5, mapped.subjects.single().demand)
        assertEquals(3, mapped.subjects.single().granted)
        assertEquals(2, mapped.subjects.single().unmet)
        assertFalse(mapped.subjects.single().feasibleBeforeExam)
    }

    private fun mistake(unitId: String) = MistakeRecord(
        entryId = "entry:$unitId",
        problemId = "problem:$unitId",
        problemRevisionId = "revision:$unitId",
        practiceUnitId = unitId,
        sourceKey = null,
        subject = "MATH",
        title = "题 $unitId",
        problemMarkdown = "markdown",
        status = "ACTIVE",
        createdAtEpochMillis = 1_000,
        nextReviewAtEpochMillis = null,
        retrievability = null,
        knowledgeNodeIds = setOf("kc:$unitId"),
    )

    private fun advisory(unitId: String, tier: TutorDifficultyTier) = TeachingAdvisoryRecord(
        advisoryId = "advisory:$unitId",
        learnerId = LEARNER_ID,
        practiceUnitId = unitId,
        knowledgeNodeId = "kc:$unitId",
        advisoryKind = TeachingAdvisoryRecord.KIND_DIFFICULTY_TIER,
        payloadMarkdown = tier.name,
        confidence = 1.0,
        sourceId = "source:$unitId",
        createdAtEpochMillis = 1_000,
    )

    private class FixedExamSettingsStore(exams: List<ExamCalendarEntry>) : SchedulingSettingsStore {
        private val current = MutableStateFlow(exams)
        override val options: Flow<SchedulingOptions> = flowOf(SchedulingOptions())
        override suspend fun setOptions(options: SchedulingOptions) = Unit
        override val exams: Flow<List<ExamCalendarEntry>> = current
        override suspend fun addExam(entry: ExamCalendarEntry) {
            current.value = current.value + entry
        }

        override suspend fun removeExam(entryId: String) {
            current.value = current.value.filterNot { it.entryId == entryId }
        }

        override val optimizedParameters: Flow<DoubleArray?> = flowOf(null)
        override suspend fun setOptimizedParameters(parameters: DoubleArray?) = Unit
    }

    private companion object {
        const val LEARNER_ID = "learner:capacity-snapshot"
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZONE)
        val LOCAL_DAY: Long = java.time.LocalDate.of(2026, 1, 2).toEpochDay()
    }
}
