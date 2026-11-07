package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.knowledge.KnowledgePrerequisiteReader
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.HLRPredictionAuditService
import com.tingyun.smartmistakebook.core.domain.KnowledgeNodeScorer
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.LogDurationModel
import com.tingyun.smartmistakebook.core.domain.PredictionAuditSink
import com.tingyun.smartmistakebook.core.domain.RecallPredictionAudit
import com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
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
import org.junit.Test

/**
 * KD-68 数据侧预览的生产入口（批次 40 WS3 item 22）：同一服务、同一考试表、
 * 同一时长模型，把考试缺口与不可调度新题一次算清。
 */
class StudyReviewPlannerServiceCapacityPreviewTest {

    @Test
    fun `service preview reports exam deficits from the real planning context`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val service = plannerService(
            database = database,
            exams = listOf(
                ExamCalendarEntry(
                    entryId = "exam-math",
                    subject = "MATH",
                    examEpochDay = LOCAL_DAY + 7,
                    title = "数学",
                ),
            ),
            budgetSeconds = 1200,
        )
        val context = service.planningContext(snapshot(emptyMap()))
        val mistakes = (1..8).map { index -> mistake("unit-$index") }
        val memories = mistakes.associate { mistake ->
            mistake.practiceUnitId to memory(
                unitId = mistake.practiceUnitId,
                planningAt = context.planningAtEpochMillis,
            )
        }

        val preview = service.reviewCapacityPreview(
            mistakes = mistakes,
            learnerSnapshot = snapshot(memories),
            planningContext = context,
        )

        assertEquals(1, preview.examSubjectsInWindow)
        assertEquals(8, preview.examDeficitCount)
        assertEquals("考前 7 天预算扩到 1500s（容量 8/日），ceil(8/7)=2 → 未满足 6", 6, preview.examUnmetCount)
        assertEquals(0, preview.rebalanceBacklogCount)
        assertEquals(0, preview.intakeUnschedulableCount)
    }

    @Test
    fun `service preview keeps the existing intake unschedulable count`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.addMistake(mistake("unit-hard"))
        database.teachingAdvisories += TeachingAdvisoryRecord(
            advisoryId = "advisory:unit-hard",
            learnerId = LEARNER_ID,
            practiceUnitId = "unit-hard",
            knowledgeNodeId = "kc:unit-hard",
            advisoryKind = TeachingAdvisoryRecord.KIND_DIFFICULTY_TIER,
            payloadMarkdown = TutorDifficultyTier.HARD.name,
            confidence = 1.0,
            sourceId = "source:unit-hard",
            createdAtEpochMillis = 1_000,
        )
        val service = plannerService(database = database, exams = emptyList(), budgetSeconds = 120)
        val context = service.planningContext(snapshot(emptyMap()))

        val preview = service.reviewCapacityPreview(
            mistakes = listOf(mistake("unit-hard")),
            learnerSnapshot = snapshot(emptyMap()),
            planningContext = context,
        )

        assertEquals(0, preview.examSubjectsInWindow)
        assertEquals(
            "300s 的真实时长超过 120s 整日预算 → 每日引入永远装不下",
            1,
            preview.intakeUnschedulableCount,
        )
    }

    @Test
    fun `service preview is empty for no exam and disabled rebalance`() = runBlocking {
        val service = plannerService(
            database = FakeStudyDatabasePort(),
            exams = emptyList(),
            budgetSeconds = 1200,
        )
        val context = service.planningContext(snapshot(emptyMap()))
        val result = service.reviewCapacityPreview(
            mistakes = listOf(mistake("unit-a")),
            learnerSnapshot = snapshot(
                mapOf("unit-a" to memory("unit-a", context.planningAtEpochMillis)),
            ),
            planningContext = context,
        )
        assertEquals(ReviewCapacityPreview.EMPTY, result)
    }

    @Test
    fun `repository exposes the preview data source`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.addMistake(mistake("unit-hard"))
        database.teachingAdvisories += TeachingAdvisoryRecord(
            advisoryId = "advisory:unit-hard",
            learnerId = LEARNER_ID,
            practiceUnitId = "unit-hard",
            knowledgeNodeId = "kc:unit-hard",
            advisoryKind = TeachingAdvisoryRecord.KIND_DIFFICULTY_TIER,
            payloadMarkdown = TutorDifficultyTier.HARD.name,
            confidence = 1.0,
            sourceId = "source:unit-hard",
            createdAtEpochMillis = 1_000,
        )
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
            clock = CLOCK,
            studyZoneId = ZONE,
            reviewTimeBudgetSeconds = 120,
        )
        try {
            repository.initialize()
            val preview = repository.reviewCapacityPreview()
            assertEquals(
                "生产入口必须把既有 intakeUnschedulableCount 一起带出",
                1,
                preview.intakeUnschedulableCount,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    // ------------------------------------------------------------------
    // 夹具（与 StudyReviewPlannerExamWindowTest 同口径）
    // ------------------------------------------------------------------

    private fun plannerService(
        database: FakeStudyDatabasePort,
        exams: List<ExamCalendarEntry>,
        budgetSeconds: Int,
    ): StudyReviewPlannerService = StudyReviewPlannerService(
        database = database,
        learnerId = LEARNER_ID,
        studyZoneId = ZONE,
        clock = CLOCK,
        reviewTimeBudgetSeconds = budgetSeconds,
        planningDecay = DECAY,
        reviewPlannerV2 = ReviewPlannerV2(ForgettingCurve()),
        knowledgeNodeScorer = KnowledgeNodeScorer(),
        durationModel = LogDurationModel(),
        reviewLogSink = ReviewLogSink(database, LEARNER_ID, CLOCK, ZONE),
        schedulingSettingsStore = FixedExamSettingsStore(exams),
        predictionAuditService = HLRPredictionAuditService(),
        predictionAuditSink = NoopPredictionAuditSink,
        knowledgePrerequisites = KnowledgePrerequisiteReader(database),
        learnerSnapshot = { snapshot(emptyMap()) },
        // 预算边界用例（120s）低于 SchedulingOptions 的 5 分钟下限，显式按构造参数解析。
        budgetResolution = BudgetResolution.CONSTRUCTOR_ONLY,
    )

    private fun snapshot(memories: Map<String, ProblemMemoryState>) = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = memories,
        checkpoint = ProjectionCheckpoint(
            lastSequence = 0,
            projectorVersion = LearningProjector.VERSION,
            projectedAtEpochMillis = 0,
        ),
        generatedAtEpochMillis = CLOCK.millis(),
    )

    private fun memory(unitId: String, planningAt: Long) = ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = 10.0,
        difficulty = 5.5,
        lastReviewedAtEpochMillis = planningAt - 10 * DAY,
        nextReviewAtEpochMillis = planningAt,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 0,
    )

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

    private object NoopPredictionAuditSink : PredictionAuditSink {
        override suspend fun record(prediction: RecallPredictionAudit) = Unit

        override suspend fun resolveOutcome(
            practiceUnitId: String,
            wasIndependentCorrect: Boolean,
            observedAtEpochMillis: Long,
            responseLatencyMs: Long?,
            hintCount: Int,
        ): Int = 0
    }

    private companion object {
        const val LEARNER_ID = "learner:capacity-preview"
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZONE)
        const val DAY = AlgorithmConstants.DAY_MILLIS
        val LOCAL_DAY = java.time.LocalDate.of(2026, 1, 2).toEpochDay()
        val DECAY: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]
    }
}
