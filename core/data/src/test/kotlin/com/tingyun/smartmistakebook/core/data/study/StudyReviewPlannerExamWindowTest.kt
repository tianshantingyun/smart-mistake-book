package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.knowledge.KnowledgePrerequisiteReader
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.ReviewPlanBundle
import com.tingyun.smartmistakebook.core.database.port.FullLibraryScanNodeRecord
import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G6 考前冲刺半边（批次 19 / T4）的服务层验证：扩容爬升 + 薄弱优先 + 科目/日期/范围变化。
 *
 * 对应新阶段 spec 5B 承接节（`docs/research/2026-10-11-new-stages-spec.md`:193）：
 * "考试日历入口已有，本阶段验科目/日期/范围变化后选择和预算是否变化"。
 *
 * 逐位不变的证明口径：同一二进制、同一夹具，只改考试表——无考试日与窗口外考试的
 * 计划**指纹**逐位一致（指纹含队列、理由、分数、预算与时间；见
 * `ReviewPlannerV2.canonicalPlanFingerprint`），且窗口外不触发全库扫描读。
 */
class StudyReviewPlannerExamWindowTest {

    // ------------------------------------------------------------------
    // G6 扩容爬升：纯函数表（线性形状与窗口复用既有考前坡道，插眼 1 已裁）
    // ------------------------------------------------------------------

    @Test
    fun `exam capacity budget ramps linearly inside the window and stays flat outside`() {
        val service = plannerService(CountingScanPort(), exams = emptyList(), budgetSeconds = 1200)

        // 无考试日 / 窗口外 / 窗口边缘（daysUntil=14 → ramp=0）：预算逐位不变。
        assertEquals(1200, service.examCapacityBudgetSeconds(emptyList(), LOCAL_DAY, 1200))
        assertEquals(1200, service.examCapacityBudgetSeconds(listOf(exam(daysUntil = 30)), LOCAL_DAY, 1200))
        assertEquals(1200, service.examCapacityBudgetSeconds(listOf(exam(daysUntil = 14)), LOCAL_DAY, 1200))
        // 考试已过恒为基础预算。
        assertEquals(1200, service.examCapacityBudgetSeconds(listOf(exam(daysUntil = -1)), LOCAL_DAY, 1200))
        // 窗口内线性放大：考试当天峰值（+50% 占位幅度）、按天线性回落（toInt 截断）。
        assertEquals(1800, service.examCapacityBudgetSeconds(listOf(exam(daysUntil = 0)), LOCAL_DAY, 1200))
        assertEquals(1500, service.examCapacityBudgetSeconds(listOf(exam(daysUntil = 7)), LOCAL_DAY, 1200))
        assertEquals(1242, service.examCapacityBudgetSeconds(listOf(exam(daysUntil = 13)), LOCAL_DAY, 1200))
        // 多科并行：取各科爬升的最大值（不叠加——预算是全天一口）。
        assertEquals(
            1671,
            service.examCapacityBudgetSeconds(
                listOf(exam(daysUntil = 3, subject = "MATH"), exam(daysUntil = 10, subject = "PHYSICS")),
                LOCAL_DAY,
                1200,
            ),
        )
    }

    // ------------------------------------------------------------------
    // 无考试日 / 窗口外：行为逐位不变（指纹级对照）
    // ------------------------------------------------------------------

    @Test
    fun `plan without exam is bit-identical to a plan with an out-of-window exam`() = runBlocking {
        val noExamPort = CountingScanPort()
        val farExamPort = CountingScanPort()
        val mistakes = dueMistakes("MATH", "unit-a", "unit-b")

        val noExam = plan(
            noExamPort,
            exams = emptyList(),
            budgetSeconds = 1200,
            mistakes = mistakes,
            memoriesAt = { at -> mapOf(dueMemoryAt(at, "unit-a"), dueMemoryAt(at, "unit-b")) },
        )
        val farExam = plan(
            farExamPort,
            // 科目同名 + 窗口外：证明守恒的是窗口门，不是科目错配。
            exams = listOf(exam(daysUntil = 30, subject = "MATH")),
            budgetSeconds = 1200,
            mistakes = mistakes,
            memoriesAt = { at -> mapOf(dueMemoryAt(at, "unit-a"), dueMemoryAt(at, "unit-b")) },
        )

        assertEquals(
            "窗口外考试不得改变计划任何一位",
            noExam.plan.planFingerprint,
            farExam.plan.planFingerprint,
        )
        assertEquals(1200, noExam.plan.timeBudgetSeconds)
        assertEquals(1200, farExam.plan.timeBudgetSeconds)
        assertEquals("无考试日不得触发全库扫描读", 0, noExamPort.fullLibraryScanReadCount)
        assertEquals("窗口外考试不得触发全库扫描读", 0, farExamPort.fullLibraryScanReadCount)
    }

    // ------------------------------------------------------------------
    // 窗口内：预算爬升改变每日实际选择
    // ------------------------------------------------------------------

    @Test
    fun `in-window exam grows the daily budget and the queue`() = runBlocking {
        val mistakes = dueMistakes("MATH", "unit-m1", "unit-m2", "unit-m3")

        val plain = plan(
            CountingScanPort(),
            exams = emptyList(),
            budgetSeconds = 120, // 冷启动时长模型恒 60s/题 → 恰好装 2 题。
            mistakes = mistakes,
            memoriesAt = { at ->
                mapOf(dueMemoryAt(at, "unit-m1"), dueMemoryAt(at, "unit-m2"), dueMemoryAt(at, "unit-m3"))
            },
        )
        val sprint = plan(
            CountingScanPort(),
            exams = listOf(exam(daysUntil = 0, subject = "MATH")),
            budgetSeconds = 120,
            mistakes = mistakes,
            memoriesAt = { at ->
                mapOf(dueMemoryAt(at, "unit-m1"), dueMemoryAt(at, "unit-m2"), dueMemoryAt(at, "unit-m3"))
            },
        )

        assertEquals(120, plain.plan.timeBudgetSeconds)
        assertEquals("考试当天预算 = 120 + 120×0.5×1.0 = 180", 180, sprint.plan.timeBudgetSeconds)
        assertEquals("基础预算装 2 题（60s×2）", 2, plain.queue.size)
        assertEquals("扩容后装 3 题（60s×3 ≤ 180）", 3, sprint.queue.size)
    }

    // ------------------------------------------------------------------
    // 科目有/无考试日：选择变化（正反向）
    // ------------------------------------------------------------------

    @Test
    fun `in-window exam flips the daily selection toward the exam subject`() = runBlocking {
        val mistakes = dueMistakes("PHYSICS", "unit-a") + dueMistakes("MATH", "unit-b")
        val memoriesAt: (Long) -> Map<String, ProblemMemoryState> = { at ->
            mapOf(dueMemoryAt(at, "unit-a"), dueMemoryAt(at, "unit-b"))
        }

        // 反向：无考试 → 两题同分，按 practiceUnitId 稳定次序取 unit-a。
        val plain = plan(
            CountingScanPort(),
            exams = emptyList(),
            budgetSeconds = 60, // 冷启动 60s/题 → 恰好一题。
            mistakes = mistakes,
            memoriesAt = memoriesAt,
        )
        assertEquals(listOf("unit-a"), plain.queue.map { it.practiceUnitId })

        // 正向：MATH 窗口内考试 → unit-b 带科目坡道加权胜出，理由含 EXAM_PRIORITY。
        val mathExam = plan(
            CountingScanPort(),
            exams = listOf(exam(daysUntil = 3, subject = "MATH")),
            budgetSeconds = 60,
            mistakes = mistakes,
            memoriesAt = memoriesAt,
        )
        assertEquals(listOf("unit-b"), mathExam.queue.map { it.practiceUnitId })
        assertTrue(
            "入选的考试科目题必须带 EXAM_PRIORITY 理由",
            "EXAM_PRIORITY" in mathExam.queue.single().reasons,
        )

        // 反向对照：换 PHYSICS 考试 → unit-a 胜出（科目维度确实在起作用）。
        val physicsExam = plan(
            CountingScanPort(),
            exams = listOf(exam(daysUntil = 3, subject = "PHYSICS")),
            budgetSeconds = 60,
            mistakes = mistakes,
            memoriesAt = memoriesAt,
        )
        assertEquals(listOf("unit-a"), physicsExam.queue.map { it.practiceUnitId })
    }

    // ------------------------------------------------------------------
    // 多科并行：预算取最大爬升、选择按各自科目坡道
    // ------------------------------------------------------------------

    @Test
    fun `parallel exams take the steepest ramp for budget and rank by subject ramp`() = runBlocking {
        val mistakes = dueMistakes("PHYSICS", "unit-phys") + dueMistakes("MATH", "unit-math")

        val both = plan(
            CountingScanPort(),
            exams = listOf(
                exam(daysUntil = 3, subject = "MATH"),
                exam(daysUntil = 10, subject = "PHYSICS"),
            ),
            budgetSeconds = 60,
            mistakes = mistakes,
            memoriesAt = { at -> mapOf(dueMemoryAt(at, "unit-phys"), dueMemoryAt(at, "unit-math")) },
        )

        // 预算 = 60 + 60×0.5×max(11/14, 4/14) = 60 + 23 = 83（恰好一题 60s）。
        assertEquals(83, both.plan.timeBudgetSeconds)
        // MATH 坡道（11/14）陡于 PHYSICS（4/14）→ 数学问胜出。
        assertEquals(listOf("unit-math"), both.queue.map { it.practiceUnitId })
    }

    // ------------------------------------------------------------------
    // 薄弱优先（3E 全库扫描分接入 EXAM 通道）：正反向 + 扫描读门
    // ------------------------------------------------------------------

    @Test
    fun `weak knowledge node pulls its bound fresh question ahead before the exam`() = runBlocking {
        val mistakes = listOf(
            // kc-weak 的在册到期题：90 天前复习、稳定度 1 天 → 扫描聚合 R≈0.50 → 到期压力≈0.50。
            mistake("unit-x", "MATH", KC_WEAK, T0),
            // 两道新题（无记忆状态 → 只能经 NewIntroductionPolicy 进入当日计划）：
            // fresh-b 更早入库（无考试时 FIFO 先引入它）。
            mistake("fresh-b", "MATH", KC_HEALTHY, T0),
            mistake("fresh-a", "MATH", KC_WEAK, T0 + HOUR),
        )

        val plainPort = CountingScanPort().apply { seedNodes() }
        val sprintPort = CountingScanPort().apply { seedNodes() }

        // 反向：无考试 → FIFO 引入更早的 fresh-b；不读扫描。
        val plain = plan(
            plainPort,
            exams = emptyList(),
            budgetSeconds = 300,
            mistakes = mistakes,
        ) { planningAt -> mapOf(dueMemoryAt(planningAt, "unit-x")) }
        val plainIds = plain.queue.map { it.practiceUnitId }
        assertTrue("无考试基线：FIFO 引入 fresh-b", "fresh-b" in plainIds)
        assertFalse("无考试基线：fresh-a 不进当日计划", "fresh-a" in plainIds)
        assertTrue("到期题 unit-x 不受影响", "unit-x" in plainIds)
        assertEquals("无考试日不得触发全库扫描读", 0, plainPort.fullLibraryScanReadCount)

        // 正向：窗口内考试（坡道 4/14≈0.286 < kc-weak 扫描到期压力≈0.50）→ 弱点绑定的新题先引入。
        val sprint = plan(
            sprintPort,
            exams = listOf(exam(daysUntil = 10, subject = "MATH")),
            budgetSeconds = 300,
            mistakes = mistakes,
        ) { planningAt -> mapOf(dueMemoryAt(planningAt, "unit-x")) }
        val sprintIds = sprint.queue.map { it.practiceUnitId }
        assertTrue("考前：弱点 KC 绑定的新题 fresh-a 先引入", "fresh-a" in sprintIds)
        assertFalse("考前：fresh-b 让位", "fresh-b" in sprintIds)
        assertTrue("unit-x" in sprintIds)
        assertEquals("窗口内考试才读全库扫描（恰一次）", 1, sprintPort.fullLibraryScanReadCount)
        assertTrue(
            "弱点绑定的新题经既有 EXAM 通道表达（EXAM_PRIORITY 理由在场）",
            "EXAM_PRIORITY" in sprint.queue.first { it.practiceUnitId == "fresh-a" }.reasons,
        )
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private companion object {
        const val LEARNER_ID = "learner:g6"
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZONE)
        const val DAY = AlgorithmConstants.DAY_MILLIS
        const val HOUR = 3_600_000L
        val T0 = Instant.parse("2025-12-01T08:00:00Z").toEpochMilli()

        /** 固定时钟下的学习日：2026-01-02（本地 16:00，04:00 日界之后）。 */
        val LOCAL_DAY: Long = LocalDate.of(2026, 1, 2).toEpochDay()

        const val KC_WEAK = "kc:g6:weak"
        const val KC_HEALTHY = "kc:g6:healthy"

        val DECAY: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]

        fun exam(daysUntil: Int, subject: String = "MATH") = ExamCalendarEntry(
            entryId = "exam-$subject-$daysUntil",
            subject = subject,
            examEpochDay = LOCAL_DAY + daysUntil,
            title = "g6-$subject",
        )
    }

    /** KF-32 同款开口：只加计数，不改行为——证明扫描读的门（无考试/窗口外为 0）。 */
    private class CountingScanPort : FakeStudyDatabasePort() {
        var fullLibraryScanReadCount: Int = 0
            private set

        override suspend fun readFullLibraryScanNodes(): List<FullLibraryScanNodeRecord> {
            fullLibraryScanReadCount += 1
            return super.readFullLibraryScanNodes()
        }
    }

    private class MutableExamSettingsStore(exams: List<ExamCalendarEntry>) : SchedulingSettingsStore {
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
        schedulingSettingsStore = MutableExamSettingsStore(exams),
        predictionAuditService = HLRPredictionAuditService(),
        predictionAuditSink = NoopPredictionAuditSink,
        knowledgePrerequisites = KnowledgePrerequisiteReader(database),
        learnerSnapshot = { snapshot(emptyMap()) },
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

    private fun mistake(
        unitId: String,
        subject: String,
        knowledgeNodeId: String,
        createdAt: Long,
    ) = MistakeRecord(
        entryId = "entry:$unitId",
        problemId = "problem:$unitId",
        problemRevisionId = "revision:$unitId",
        practiceUnitId = unitId,
        sourceKey = null,
        subject = subject,
        title = "题 $unitId",
        problemMarkdown = "markdown",
        status = "ACTIVE",
        createdAtEpochMillis = createdAt,
        nextReviewAtEpochMillis = null,
        retrievability = null,
        knowledgeNodeIds = setOf(knowledgeNodeId),
    )

    private fun dueMistakes(subject: String, vararg unitIds: String): List<MistakeRecord> =
        unitIds.map { unitId -> mistake(unitId, subject, "kc:$unitId", T0) }

    /** 到期且高度衰减的卡：90 天前复习、稳定度 1 天、逾期 80 天。 */
    private fun dueMemoryAt(
        planningAt: Long,
        unitId: String,
        lastReviewedDaysAgo: Long = 90,
        stabilityDays: Double = 1.0,
        overdueDays: Long = 80,
    ) = unitId to ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = stabilityDays,
        difficulty = 5.5,
        lastReviewedAtEpochMillis = planningAt - lastReviewedDaysAgo * DAY,
        nextReviewAtEpochMillis = planningAt - overdueDays * DAY,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 0,
    )

    /** CountingScanPort 上播种两个知识节点（全库扫描行 = 节点 ∩ 有在册绑定题）。 */
    private fun CountingScanPort.seedNodes() {
        listOf(KC_WEAK to "薄弱点", KC_HEALTHY to "健康点").forEach { (nodeId, name) ->
            knowledgeNodes += KnowledgeNodeSeedRecord(
                knowledgeNodeId = nodeId,
                stableCode = nodeId,
                subject = "MATH",
                displayName = name,
                parentKnowledgeNodeId = null,
                taxonomyVersion = "g6-test",
                createdAtEpochMillis = T0,
            )
        }
    }

    /**
     * 走真服务组装一份当日计划：先由固定时钟推出排程时刻，再据此构造记忆状态，
     * 最后 createReviewPlan（候选组装 → 新题引入 → V2 排程 → 落库形状）。
     * [memoriesAt] 收到排程时刻，保证"到期/未到期"与生产同一口径。
     */
    private suspend fun plan(
        database: FakeStudyDatabasePort,
        exams: List<ExamCalendarEntry>,
        budgetSeconds: Int,
        mistakes: List<MistakeRecord>,
        memoriesAt: (Long) -> Map<String, ProblemMemoryState> = { emptyMap() },
    ): ReviewPlanBundle {
        val service = plannerService(database, exams, budgetSeconds)
        val planningContext = service.planningContext(snapshot(emptyMap()))
        mistakes.forEach(database::addMistake)
        return service.createReviewPlan(
            mistakes = mistakes,
            learnerSnapshot = snapshot(memoriesAt(planningContext.planningAtEpochMillis)),
            planningContext = planningContext,
        )
    }
}
