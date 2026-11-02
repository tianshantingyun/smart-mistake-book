package com.tingyun.smartmistakebook.core.data.study

/**
 * 休假/积压后的缺勤再平衡策略（批次 40 WS3 item 20）。
 *
 * **消灭的失败**：全仓目前没有 `rebalance`（0 命中）；休假回来只有"逾期越久排越前"
 * 的排序，没有补账配额，积压会无声增长。本策略每天用**剩余积压 ÷ 剩余学习日**
 * 重算（漏做自动进入下一天，不需要额外持久状态），并给出两个诚实读数：
 * 需要多少天清账（[RebalancePlan.daysNeeded]）与有多少题还不够安全提前窗
 * （[RebalancePlan.ineligibleCount]）。
 *
 * 上限纪律：成长上限 1.5 作用在**当日预算**上（20 → 30 min），不是"预算不动、
 * 硬塞 1.5 倍张数"——后者会让 F8 的 20 分钟承诺当场失效。超过上限不抬预算，
 * 改为延长 horizon（`D_need = ceil(b / (g − 1))`）。
 *
 * 默认关闭：调用方不启用时本策略不产生任何输出，既有计划逐位不变。
 */
object MissedDayRebalancePolicy {

    /**
     * @param itemCap 当日剩余名额上限（null = 只受 `baseBudgetSeconds` 折算容量约束）。
     *   **消灭的具体失败**：考试通道已占用当日预算时，再平衡若仍按整份预算取题，
     *   两条通道会把同一天排成 1.5× 以上；调用方必须传入考试取题后剩下的名额。
     * @param excludedPracticeUnitIds 已被其他通道排进今天的题（考试通道）。
     *   **消灭的具体失败**：先取题后再在合并处求交集——重复题白占一个名额，合并计数
     *   只剩"交集"这个数字，真正可用的名额却被浪费（R1 §2.3 C9-①）。去重必须发生在
     *   取题**之前**：本题在积压里不再参与 0.85 窗筛选、不再占 `itemCap`。
     */
    fun plan(
        backlog: List<ReviewCapacityFacts>,
        baseBudgetSeconds: Int,
        p50DurationSeconds: Int,
        remainingStudyDays: Int,
        growthCap: Double = ReviewCapacityPolicy.MAX_DAILY_GROWTH,
        itemCap: Int? = null,
        excludedPracticeUnitIds: Set<String> = emptySet(),
    ): RebalancePlan {
        require(baseBudgetSeconds > 0) { "Time budget must be positive" }
        require(p50DurationSeconds > 0) { "Median duration must be positive" }
        require(remainingStudyDays >= 0) { "Remaining study days must not be negative" }
        require(growthCap.isFinite() && growthCap > 1.0) {
            "Daily growth cap must be greater than one"
        }
        require(itemCap == null || itemCap >= 0) { "Item cap must not be negative" }
        require(backlog.map(ReviewCapacityFacts::practiceUnitId).distinct().size == backlog.size) {
            "Backlog practice units must be unique"
        }
        val candidates = backlog.filterNot { it.practiceUnitId in excludedPracticeUnitIds }
        if (candidates.isEmpty()) {
            return RebalancePlan(
                dailyTake = 0,
                dailyBudgetSeconds = baseBudgetSeconds,
                daysNeeded = 0,
                growthMultiplier = 1.0,
                picked = emptyList(),
                eligibleCount = 0,
                ineligibleCount = 0,
            )
        }
        val normalCapacity = ReviewCapacityPolicy.capacityPerDay(
            budgetSeconds = baseBudgetSeconds,
            p50DurationSeconds = p50DurationSeconds,
        )
        val backlogDays = candidates.size.toDouble() / normalCapacity
        val required = ReviewCapacityPolicy.requiredGrowthMultiplier(
            backlogDays = backlogDays,
            remainingStudyDays = remainingStudyDays,
        )
        val allowed = ReviewCapacityPolicy.boundedGrowthMultiplier(
            backlogDays = backlogDays,
            remainingStudyDays = remainingStudyDays,
            growthCap = growthCap,
        )
        val effectiveBudgetSeconds = ReviewCapacityPolicy.grownBudgetSeconds(
            baseBudgetSeconds = baseBudgetSeconds,
            multiplier = allowed,
        )
        val perDay = ReviewCapacityPolicy.capacityPerDay(
            budgetSeconds = effectiveBudgetSeconds,
            p50DurationSeconds = p50DurationSeconds,
        )
        val daysNeeded = if (required <= growthCap) {
            remainingStudyDays
        } else {
            ReviewCapacityPolicy.daysToClear(backlogDays, growthCap)
        }
        // 只拉"已用掉 ≥85% 间隔"的题：刚复习过的强题不提前，避免无谓的稳定性损失
        // （FSRS 的稳定性增益随 R 降低而增大；提前做会把增益白白花掉）。
        val eligible = candidates
            .filter { it.elapsedFraction >= ReviewCapacityPolicy.REBALANCE_MIN_ELAPSED_FRACTION }
            .sortedWith(
                compareByDescending<ReviewCapacityFacts> { it.elapsedFraction }
                    .thenByDescending { it.lapseCount }
                    .thenBy { it.nextReviewAtEpochMillis }
                    .thenBy { it.practiceUnitId },
            )
        val picked = eligible.take(itemCap?.let { minOf(perDay, it) } ?: perDay)
        return RebalancePlan(
            dailyTake = picked.size,
            dailyBudgetSeconds = effectiveBudgetSeconds,
            daysNeeded = daysNeeded,
            growthMultiplier = allowed,
            picked = picked,
            eligibleCount = eligible.size,
            ineligibleCount = candidates.size - eligible.size,
        )
    }
}

/**
 * 缺勤再平衡的只读结果。
 *
 * [dailyTake] 是**实际**取出的题数（≤ 单日容量）；[ineligibleCount] 是积压中
 * 还没进入 0.85 安全提前窗的题数——两者都不静默丢弃，供诚实预览使用。
 */
data class RebalancePlan(
    val dailyTake: Int,
    val dailyBudgetSeconds: Int,
    val daysNeeded: Int,
    val growthMultiplier: Double,
    val picked: List<ReviewCapacityFacts>,
    val eligibleCount: Int,
    val ineligibleCount: Int,
) {
    init {
        require(dailyTake >= 0) { "Daily take must not be negative" }
        require(dailyBudgetSeconds > 0) { "Daily budget must be positive" }
        require(daysNeeded >= 0) { "Days needed must not be negative" }
        require(growthMultiplier.isFinite() && growthMultiplier >= 1.0) {
            "Growth multiplier must be finite and at least one"
        }
        require(eligibleCount >= 0) { "Eligible count must not be negative" }
        require(ineligibleCount >= 0) { "Ineligible count must not be negative" }
        require(picked.size <= eligibleCount) { "Picked items must come from the eligible set" }
        require(dailyTake == picked.size) { "Daily take must equal the picked item count" }
        require(picked.map(ReviewCapacityFacts::practiceUnitId).distinct().size == picked.size) {
            "Picked practice units must be unique"
        }
    }
}
