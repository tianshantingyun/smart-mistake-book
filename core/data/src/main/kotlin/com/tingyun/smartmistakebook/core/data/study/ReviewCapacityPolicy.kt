package com.tingyun.smartmistakebook.core.data.study

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * 5B 复习节奏的纯策略面（批次 40 WS3 item 18）：把 FSRS 已有的
 * difficulty / stability / retrievability 三个状态量当作**输入事实**，只做容量
 * 与配额的确定性算术，不重写、不拟合、不训练第二套遗忘曲线。
 *
 * **消灭的失败**：现有调度只有"新引入追赶"和"+50% 考试预算"两条容量通道；
 * 到期复习与考试保留率缺口没有配额、没有可兑现性读数，超预算时静默截断，
 * 学生看不到"今天欠了多少"。本对象把这三件事收成可单测的纯函数：
 * 每日配额（[dailyQuota]）、考试目标保留率（[examTargetRetention]）、
 * 考试缺口（[examDeficit]）、周末/集中日重排（[allocateByDayWeights]）、
 * 休假追赶上限（[daysToClear] / [boundedGrowthMultiplier]）。
 *
 * 数值来源：主会话批次 40 item 17 裁定（`docs/research/2026-11-02-ws3-review-budget-adjudication.md`）。
 * 所有常量都是**起步值**，与 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 的
 * `PENDING_CALIBRATION` 同级；外部先例只提供方向（Anki 的 90% 默认保留率、
 * Deadline2 的 ceil 配额、FSRS Helper 的 0.15 相对偏离阈值），不提供本仓的具体档位。
 *
 * 副作用边界：无时钟、无 IO、无随机源；同一输入逐位同输出。
 */
object ReviewCapacityPolicy {

    // ------------------------------------------------------------------
    // F8：每日复习时间预算（docs/research/2026-11-02-ws3-review-budget-adjudication.md §2）
    // ------------------------------------------------------------------

    /** 默认 20 min：保留现网行为的最低风险值，也是 `RoomBackedStudyExperienceRepository` 的构造默认。 */
    const val DEFAULT_DAILY_BUDGET_MINUTES: Int = 20

    /** 下限 10 min：中档估时下仍有 3 题容量（到期 + 新引入各至少 1 题）。 */
    const val MIN_DAILY_BUDGET_MINUTES: Int = 10

    /** 上限 60 min：中档估时 20 题；再高会把"今日欠账"伪装成"今天能做完"。 */
    const val MAX_DAILY_BUDGET_MINUTES: Int = 60

    /** 档位步进 5 min（10/15/20/.../60）。 */
    const val BUDGET_STEP_MINUTES: Int = 5

    // ------------------------------------------------------------------
    // M5 / 考试余量：目标可提取度
    // ------------------------------------------------------------------

    /** FSRS 目标保留率默认 0.90（Anki 手册默认；py-fsrs `desired_retention=0.9`）。 */
    const val BASE_TARGET_RETENTION: Double = 0.90

    /** 考前目标保留率上限 0.97（本仓 `MemoryUpdateModel` 的 0.7..0.97 窄口径）。 */
    const val MAX_EXAM_TARGET_RETENTION: Double = 0.97

    /** 考试坡道窗口 14 天（与 `StudyReviewPlannerService.EXAM_RAMP_DAYS` 同窗）。 */
    const val EXAM_WINDOW_DAYS: Int = 14

    // ------------------------------------------------------------------
    // F9：休假/积压追赶
    // ------------------------------------------------------------------

    /**
     * 每日预算相对正常值的最大放大倍数，作用在**预算**上而不是固定预算下的张数。
     * 1.5 与 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5`（+50%）同值：同一个
     * "每天最多多干 50%"的承诺不应在考试与休假两条路上给出两个数。
     */
    const val MAX_DAILY_GROWTH: Double = 1.5

    /**
     * 只拉"已用掉 ≥85% 间隔"的题（FSRS Helper 的 0.15 相对偏离阈值反推；
     * 上游 Advance 0.13 / Postpone 0.15 不一致，本仓取更保守的 0.15）。
     */
    const val REBALANCE_MIN_ELAPSED_FRACTION: Double = 0.85

    // ------------------------------------------------------------------
    // F18：周末/集中清空模式（按日重排，不改总量）
    // ------------------------------------------------------------------

    /** 休息日：该日不分配配额。 */
    const val DAY_WEIGHT_REST: Int = 0

    /** 平日权重。 */
    const val DAY_WEIGHT_WEEKDAY: Int = 1

    /** 周末/集中日权重：默认 2×（20 min 预算下 3× 必超）。 */
    const val DAY_WEIGHT_WEEKEND: Int = 2

    /** 分钟档位 → 秒；只接受裁定区间内的整档值，构造期 fail-closed。 */
    fun secondsForBudgetMinutes(minutes: Int): Int {
        require(minutes in MIN_DAILY_BUDGET_MINUTES..MAX_DAILY_BUDGET_MINUTES) {
            "Review budget minutes must be in $MIN_DAILY_BUDGET_MINUTES..$MAX_DAILY_BUDGET_MINUTES"
        }
        require((minutes - MIN_DAILY_BUDGET_MINUTES) % BUDGET_STEP_MINUTES == 0) {
            "Review budget minutes must step by $BUDGET_STEP_MINUTES"
        }
        return minutes * 60
    }

    /**
     * 每日应做量：`ceil(remaining / studyDaysLeft)`，夹在 `[0, capPerDay]`。
     *
     * - `remaining == 0 → 0`（无债不排）。
     * - `studyDaysLeft <= 0 → capPerDay`（当天尽力，不除零；考试当天/已过不产生配额）。
     * - 单调性：`remaining` ↑ 不减；`studyDaysLeft` ↑ 不增。
     */
    fun dailyQuota(remaining: Int, studyDaysLeft: Int, capPerDay: Int): Int {
        require(remaining >= 0) { "Remaining items must not be negative" }
        require(capPerDay >= 0) { "Daily capacity must not be negative" }
        if (remaining == 0 || capPerDay == 0) return 0
        if (studyDaysLeft <= 0) return capPerDay
        val exact = remaining / studyDaysLeft + if (remaining % studyDaysLeft == 0) 0 else 1
        return min(exact, capPerDay)
    }

    /**
     * spec §2.17 的考前目标保留率：
     * `r*_exam(d) = min(0.97, r* + (0.97 − r*)·(1 − d/14))`，`d ∈ [0, 14]`。
     *
     * - `d >= 14` 或考试已过 → 逐位返回 `baseRetention`（窗口外零影响）。
     * - `d == 0`（考试当天）→ 0.97；配额层另有"当天不排"的门（[ExamQuotaPlanner]）。
     * - 恒 `∈ [baseRetention, 0.97]`。
     */
    fun examTargetRetention(baseRetention: Double, daysUntilExam: Int): Double {
        require(baseRetention in 0.7..MAX_EXAM_TARGET_RETENTION) {
            "Base retention must be in 0.7..$MAX_EXAM_TARGET_RETENTION"
        }
        if (daysUntilExam !in 0..EXAM_WINDOW_DAYS) return baseRetention
        if (daysUntilExam == 0) return MAX_EXAM_TARGET_RETENTION
        val progress = 1.0 - daysUntilExam.toDouble() / EXAM_WINDOW_DAYS
        return (baseRetention + (MAX_EXAM_TARGET_RETENTION - baseRetention) * progress)
            .coerceIn(baseRetention, MAX_EXAM_TARGET_RETENTION)
    }

    /** 考试缺口 `max(0, r*_exam − R(examDay))`，恒 `∈ [0, 1]`。 */
    fun examDeficit(retrievabilityAtExam: Double, targetRetentionAtExam: Double): Double {
        require(retrievabilityAtExam.isFinite() && retrievabilityAtExam in 0.0..1.0) {
            "Exam-day retrievability must be finite and in 0..1"
        }
        require(targetRetentionAtExam.isFinite() && targetRetentionAtExam in 0.0..1.0) {
            "Target retention must be finite and in 0..1"
        }
        return (targetRetentionAtExam - retrievabilityAtExam).coerceIn(0.0, 1.0)
    }

    /**
     * 单日容量（张）：`budgetSeconds / p50DurationSeconds`，下限 1。
     * 预算或时长非正时构造期抛错（对齐 `NewIntroductionPolicy` 的 fail-closed）。
     */
    fun capacityPerDay(budgetSeconds: Int, p50DurationSeconds: Int): Int {
        require(budgetSeconds > 0) { "Time budget must be positive" }
        require(p50DurationSeconds > 0) { "Median duration must be positive" }
        return (budgetSeconds / p50DurationSeconds).coerceAtLeast(1)
    }

    /**
     * F18：按日权重把 `remaining` 重排到未来若干天，**总量守恒**。
     *
     * 用最大余数法：先取 floor，再把剩余名额按小数余数降序（同分按日序升序）
     * 逐日 +1，因此 `sum(result) == remaining` 恒成立。
     *
     * fail-closed：权重全 0（没有任何可用学习日）→ 抛 `IllegalArgumentException`，
     * 不静默丢题；权重为 0 的休息日恒得 0。
     */
    fun allocateByDayWeights(remaining: Int, dayWeights: List<Int>): List<Int> {
        require(remaining >= 0) { "Remaining items must not be negative" }
        require(dayWeights.isNotEmpty()) { "Day weights must not be empty" }
        require(dayWeights.all { it >= 0 }) { "Day weights must not be negative" }
        val totalWeight = dayWeights.sum()
        require(totalWeight > 0) { "At least one study day is required" }
        if (remaining == 0) return List(dayWeights.size) { 0 }

        val assigned = IntArray(dayWeights.size)
        val remainders = ArrayList<Remainder>(dayWeights.size)
        var assignedTotal = 0
        dayWeights.forEachIndexed { index, weight ->
            val exact = remaining.toDouble() * weight / totalWeight
            val floorValue = floor(exact).toInt()
            assigned[index] = floorValue
            assignedTotal += floorValue
            remainders += Remainder(index = index, fraction = exact - floorValue)
        }
        var leftover = remaining - assignedTotal
        remainders
            .sortedWith(
                compareByDescending<Remainder> { it.fraction }
                    .thenBy { it.index },
            )
            .forEach { remainder ->
                if (leftover > 0) {
                    assigned[remainder.index] += 1
                    leftover -= 1
                }
            }
        return assigned.toList()
    }

    /**
     * F9：给定积压折合的"正常日"数 `b` 与成长上限 `g`，清账所需天数
     * `ceil(b / (g − 1))`。`b == 0 → 0`；`g <= 1` 抛错（不允许"越追越多"）。
     */
    fun daysToClear(backlogDays: Double, maxDailyGrowth: Double): Int {
        require(backlogDays.isFinite() && backlogDays >= 0.0) {
            "Backlog days must be finite and non-negative"
        }
        require(maxDailyGrowth.isFinite() && maxDailyGrowth > 1.0) {
            "Daily growth cap must be greater than one"
        }
        if (backlogDays == 0.0) return 0
        return ceil(backlogDays / (maxDailyGrowth - 1.0)).toInt()
    }

    /**
     * F9：追赶所需的预算倍率 `1 + b / D`；无剩余学习日时返回 `+∞`
     * （由 [boundedGrowthMultiplier] 夹到 `g`，当天尽力）。
     */
    fun requiredGrowthMultiplier(backlogDays: Double, remainingStudyDays: Int): Double {
        require(backlogDays.isFinite() && backlogDays >= 0.0) {
            "Backlog days must be finite and non-negative"
        }
        require(remainingStudyDays >= 0) { "Remaining study days must not be negative" }
        if (remainingStudyDays == 0) return Double.POSITIVE_INFINITY
        return 1.0 + backlogDays / remainingStudyDays
    }

    /** F9：实际允许的预算倍率 = `min(required, growthCap)`；`D == 0` 时取 `growthCap`。 */
    fun boundedGrowthMultiplier(
        backlogDays: Double,
        remainingStudyDays: Int,
        growthCap: Double = MAX_DAILY_GROWTH,
    ): Double {
        require(growthCap.isFinite() && growthCap > 1.0) {
            "Daily growth cap must be greater than one"
        }
        val required = requiredGrowthMultiplier(backlogDays, remainingStudyDays)
        return min(required, growthCap)
    }

    /** 把预算乘子作用到**预算**上（20 min × 1.5 = 30 min），向上取整到秒。 */
    fun grownBudgetSeconds(baseBudgetSeconds: Int, multiplier: Double): Int {
        require(baseBudgetSeconds > 0) { "Time budget must be positive" }
        require(multiplier.isFinite() && multiplier >= 1.0) {
            "Budget multiplier must be finite and at least one"
        }
        return ceil(baseBudgetSeconds * multiplier).toInt()
    }

    private data class Remainder(val index: Int, val fraction: Double)
}

/**
 * 容量策略的输入事实（item 18 的"三参数简化"）。
 *
 * FSRS 的三个状态量原样携带、**不在本层重算**：
 * - [difficulty]：FSRS D（1..10）；
 * - [stabilityDays]：FSRS S；
 * - [retrievabilityNow]：FSRS R(t_now)，由调用方用 `ForgettingCurve` 现算。
 *
 * 另外携带排程所需的观测事实：[retrievabilityAtExam]（无考试日 = null）、
 * [elapsedFraction]（已用掉的原间隔比例，F9 的 0.85 门）、[nextReviewAtEpochMillis]、
 * [lapseCount]。本类只做校验，不做曲线运算。
 */
data class ReviewCapacityFacts(
    val practiceUnitId: String,
    val subjectId: String = "",
    val difficulty: Double,
    val stabilityDays: Double,
    val retrievabilityNow: Double,
    val retrievabilityAtExam: Double?,
    val elapsedFraction: Double,
    val nextReviewAtEpochMillis: Long,
    val lapseCount: Int = 0,
) {
    init {
        require(practiceUnitId.isNotBlank()) { "Practice unit id must not be blank" }
        require(difficulty.isFinite() && difficulty in 1.0..10.0) {
            "Difficulty must be between one and ten"
        }
        require(stabilityDays.isFinite() && stabilityDays > 0.0) { "Stability must be positive" }
        require(retrievabilityNow.isFinite() && retrievabilityNow in 0.0..1.0) {
            "Current retrievability must be finite and in 0..1"
        }
        require(
            retrievabilityAtExam == null ||
                (retrievabilityAtExam.isFinite() && retrievabilityAtExam in 0.0..1.0),
        ) { "Exam-day retrievability must be finite and in 0..1 when provided" }
        require(elapsedFraction.isFinite() && elapsedFraction >= 0.0) {
            "Elapsed fraction must be finite and non-negative"
        }
        require(nextReviewAtEpochMillis >= 0) { "Next review time must not be negative" }
        require(lapseCount >= 0) { "Lapse count must not be negative" }
    }
}
