package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState

/**
 * KD-68 诚实预览的**数据侧**（批次 40 WS3 item 22）：把学习者投影里的记忆状态
 * 换成 [ReviewCapacityFacts]，再交给纯策略层算出"考试缺口/再平衡/O 张排不进"的读数。
 *
 * **消灭的失败**：`unschedulableCount` 及其同族的超容事实以前只存在于策略函数和测试里，
 * 生产读面没有任何对象能把它们一起算出来；UI 只能继续用粗略算术承诺覆盖。
 * 本读取器是数据侧唯一出口：同一个 `ForgettingCurve`、同一个 decay、同一份考试日历，
 * 算出的 R 与排程侧同源；不写计划、不改引入集合。
 *
 * 非热边界：结果目前是 `core/data` 的只读模型；挂到 `StudyExperienceSnapshot`
 *（`core/domain` 热区）与 `feature/review` 消费仍待非热窗口，登记为阻塞，不在本批伪造。
 */
internal class ReviewCapacityPreviewReader(
    private val forgettingCurve: ForgettingCurve,
) {

    /**
     * 为每道有记忆状态的题生成容量事实。
     *
     * [examEpochDayBySubject] 只应包含窗口内的最近考试日；没有考试的科目 →
     * `retrievabilityAtExam = null`。[examAtEpochMillisBySubject] 是调用方按**学习时区**
     * 算出的考试日结束时刻（生产路径由 `StudyReviewPlannerService` 传入）；省略时退回
     * UTC 当日结束（保守近似，仅供纯函数用例）。
     */
    fun facts(
        memoryStates: Map<String, ProblemMemoryState>,
        subjectByPracticeUnit: Map<String, String>,
        nowEpochMillis: Long,
        examEpochDayBySubject: Map<String, Long>,
        examAtEpochMillisBySubject: Map<String, Long>? = null,
    ): List<ReviewCapacityFacts> {
        require(nowEpochMillis >= 0) { "Planning time must not be negative" }
        examEpochDayBySubject.values.forEach { examEpochDay ->
            require(examEpochDay >= 0) { "Exam epoch day must not be negative" }
        }
        examAtEpochMillisBySubject?.values?.forEach { at ->
            require(at >= 0) { "Exam evaluation time must not be negative" }
        }
        return memoryStates.values
            .sortedBy(ProblemMemoryState::practiceUnitId)
            .map { state ->
                val subject = subjectByPracticeUnit[state.practiceUnitId].orEmpty()
                val examEpochDay = examEpochDayBySubject[subject]
                val examAtEpochMillis = examAtEpochMillisBySubject?.get(subject)
                    ?: examEpochDay?.let { epochDay ->
                        epochDay * DAY_MILLIS + (DAY_MILLIS - 1)
                    }
                val elapsedMillis = (nowEpochMillis - state.lastReviewedAtEpochMillis)
                    .coerceAtLeast(0L)
                val intervalMillis = (state.nextReviewAtEpochMillis - state.lastReviewedAtEpochMillis)
                    .coerceAtLeast(0L)
                ReviewCapacityFacts(
                    practiceUnitId = state.practiceUnitId,
                    subjectId = subject,
                    difficulty = state.difficulty,
                    stabilityDays = state.stabilityDays,
                    retrievabilityNow = forgettingCurve.retentionAt(state, nowEpochMillis),
                    retrievabilityAtExam = examAtEpochMillis?.let { at ->
                        forgettingCurve.retentionAt(state, at)
                    },
                    elapsedFraction = if (intervalMillis == 0L) {
                        0.0
                    } else {
                        elapsedMillis.toDouble() / intervalMillis
                    },
                    nextReviewAtEpochMillis = state.nextReviewAtEpochMillis,
                    lapseCount = state.lapseCount,
                )
            }
    }

    /**
     * 数据侧诚实预览：考试缺口题数、超容未满足数、再平衡可拉/不可拉数、逐科名额与总量，
     * 以及考试通道与再平衡通道的重复计数。
     *
     * [baseBudgetSeconds] 传**今天实际可用的**当日预算：考试爬坡已生效时就是爬坡后的值；
     * [growthCeilingBudgetSeconds] 只在预算已被爬坡抬高时传同一个值，声明"今天不再乘 1.5"。
     * 两个参数而不是让门面自己再乘一次：否则门面会在已爬坡的预算上再乘 1.5，总量可到基准的
     * 2.25×（R1 C9-②）。生产调用 `StudyReviewPlannerService.reviewCapacityPreview` 正是
     * 按这个口径传（`baseBudgetSeconds = effectiveBudgetSeconds`，爬坡时 ceiling 同值）。
     * 窗口外/无考试且再平衡关闭 → [ReviewCapacityPreview.EMPTY]。
     */
    fun preview(
        memoryStates: Map<String, ProblemMemoryState>,
        subjectByPracticeUnit: Map<String, String>,
        nowEpochMillis: Long,
        todayEpochDay: Long,
        examEpochDayBySubject: Map<String, Long>,
        examAtEpochMillisBySubject: Map<String, Long>? = null,
        baseRetention: Double = ReviewCapacityPolicy.BASE_TARGET_RETENTION,
        baseBudgetSeconds: Int,
        p50DurationSeconds: Int,
        rebalanceEnabled: Boolean = false,
        remainingStudyDays: Int = 0,
        growthCeilingBudgetSeconds: Int? = null,
    ): ReviewCapacityPreview {
        require(todayEpochDay >= 0) { "Today epoch day must not be negative" }
        require(baseRetention in 0.7..ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION) {
            "Base retention must be in 0.7..${ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION}"
        }
        require(baseBudgetSeconds > 0) { "Time budget must be positive" }
        require(p50DurationSeconds > 0) { "Median duration must be positive" }
        val inWindowExams = examEpochDayBySubject.filter { (subject, examDay) ->
            subject.isNotBlank() &&
                (examDay - todayEpochDay) in 1..ReviewCapacityPolicy.EXAM_WINDOW_DAYS
        }
        if (inWindowExams.isEmpty() && !rebalanceEnabled) return ReviewCapacityPreview.EMPTY
        val facts = facts(
            memoryStates = memoryStates,
            subjectByPracticeUnit = subjectByPracticeUnit,
            nowEpochMillis = nowEpochMillis,
            examEpochDayBySubject = examEpochDayBySubject,
            examAtEpochMillisBySubject = examAtEpochMillisBySubject,
        )
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = todayEpochDay,
            examEpochDayBySubject = examEpochDayBySubject,
            baseRetention = baseRetention,
            budgetSeconds = baseBudgetSeconds,
            p50DurationSeconds = p50DurationSeconds,
            rebalanceEnabled = rebalanceEnabled,
            remainingStudyDays = remainingStudyDays,
            growthCeilingBudgetSeconds = growthCeilingBudgetSeconds,
        )
        return ReviewCapacityPreview(
            examSubjectsInWindow = inWindowExams.size,
            examDeficitCount = plan.examDeficitCount,
            examUnmetCount = plan.examUnmetCount,
            rebalanceBacklogCount = if (plan.rebalance == null) 0 else facts.size,
            rebalanceIneligibleCount = plan.rebalance?.ineligibleCount ?: 0,
            deduplicatedCount = plan.deduplicatedCount,
            subjects = plan.subjectReadouts,
            totalGranted = plan.examPicked.size,
            dailyCapacity = plan.dailyCapacity,
            maxDailyCapacity = plan.maxDailyCapacity,
        )
    }

    private companion object {
        val DAY_MILLIS: Long = AlgorithmConstants.DAY_MILLIS
    }
}

/**
 * KD-68 同族的只读预览事实。所有计数都是"数据侧真相"，不是 UI 文案承诺：
 * - [examUnmetCount]：窗口内缺口题超出单日容量的部分（今天做不完）；
 * - [rebalanceIneligibleCount]：积压里还没进入 0.85 安全提前窗、本轮不拉的题；
 * - [intakeUnschedulableCount]：沿用既有 `intakeUnschedulableCount` 口径
 *   （时长超过整日预算、每日引入永远装不下的新题）；
 * - [deduplicatedCount]：考试与再平衡同时命中的题数（去重前置后恒为 0，保留字段作回归哨兵）；
 * - [subjects] / [totalGranted]：逐科名额与考试通道总量；[dailyCapacity] /
 *   [maxDailyCapacity] 是两个可比的上限（基础日容量 / 1.5× 成长上限）。
 */
data class ReviewCapacityPreview(
    val examSubjectsInWindow: Int = 0,
    val examDeficitCount: Int = 0,
    val examUnmetCount: Int = 0,
    val rebalanceBacklogCount: Int = 0,
    val rebalanceIneligibleCount: Int = 0,
    val deduplicatedCount: Int = 0,
    val intakeUnschedulableCount: Int = 0,
    val subjects: List<SubjectCapacity> = emptyList(),
    val totalGranted: Int = 0,
    val dailyCapacity: Int = 0,
    val maxDailyCapacity: Int = 0,
) {
    init {
        require(examSubjectsInWindow >= 0) { "Exam subject count must not be negative" }
        require(examDeficitCount >= 0) { "Exam deficit count must not be negative" }
        require(examUnmetCount >= 0) { "Exam unmet count must not be negative" }
        require(examUnmetCount <= examDeficitCount) {
            "Unmet exam deficit must be a subset of the deficit count"
        }
        require(rebalanceBacklogCount >= 0) { "Rebalance backlog count must not be negative" }
        require(rebalanceIneligibleCount >= 0) { "Rebalance ineligible count must not be negative" }
        require(rebalanceIneligibleCount <= rebalanceBacklogCount) {
            "Ineligible backlog must be a subset of the backlog count"
        }
        require(deduplicatedCount >= 0) { "Deduplicated count must not be negative" }
        require(intakeUnschedulableCount >= 0) { "Intake unschedulable count must not be negative" }
        require(dailyCapacity >= 0) { "Daily capacity must not be negative" }
        require(maxDailyCapacity >= dailyCapacity) {
            "Max daily capacity must not be below the base daily capacity"
        }
        require(totalGranted == subjects.sumOf(SubjectCapacity::granted)) {
            "Total granted must equal the sum of the per-subject grants"
        }
        require(totalGranted <= maxDailyCapacity) {
            "Preview total must not exceed the growth ceiling"
        }
        // 有考试取题时，考试通道只允许占基础日容量；再平衡另用剩余名额（≤ 上限）。
        require(subjects.isEmpty() || totalGranted <= dailyCapacity) {
            "Exam-channel preview total must fit the shared daily capacity"
        }
    }

    companion object {
        val EMPTY = ReviewCapacityPreview()
    }
}
