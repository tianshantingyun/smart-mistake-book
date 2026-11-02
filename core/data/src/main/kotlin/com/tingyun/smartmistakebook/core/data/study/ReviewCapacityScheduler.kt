package com.tingyun.smartmistakebook.core.data.study

/**
 * 只读容量调度门面（批次 40 WS3 item 21）：把考试配额与缺勤再平衡合并成一份
 * 可核对的计划，并落实"同一道题的两种需求只计一次"的硬规则。
 *
 * **消灭的失败**：考试缺口与积压追赶各自算配额时，同一道题可能被两条通道各排一次，
 * 时间被重复计；或者两条通道都超容量却只报一个笼统数字。本门面：
 * - 考试通道按科目各自的最近考试日计算（不跨科串味）；
 * - 缺勤通道默认关闭，关闭时零输出；
 * - 交叉项只保留考试通道的 picked，重复数单独报成 [ReviewCapacityPlan.deduplicatedCount]；
 * - 未满足量（[ExamQuotaPlan.unmetCount]、[RebalancePlan.ineligibleCount]）原样上报，
 *   不静默降级。
 *
 * 优先级（R3 §4.3）：今日到期复习 > 考试缺口 > 积压追赶。到期复习的预算由既有
 * `ReviewPlannerV2` 先占；本门面只处理后两者，不新增权重、不改 V2 打分。
 */
object ReviewCapacityScheduler {

    fun plan(
        facts: List<ReviewCapacityFacts>,
        todayEpochDay: Long,
        examEpochDayBySubject: Map<String, Long>,
        baseRetention: Double = ReviewCapacityPolicy.BASE_TARGET_RETENTION,
        budgetSeconds: Int,
        p50DurationSeconds: Int,
        rebalanceEnabled: Boolean = false,
        remainingStudyDays: Int = 0,
        studyDayWeightsBySubject: Map<String, List<Int>> = emptyMap(),
    ): ReviewCapacityPlan {
        require(todayEpochDay >= 0) { "Today epoch day must not be negative" }
        require(baseRetention in 0.7..ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION) {
            "Base retention must be in 0.7..${ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION}"
        }
        require(budgetSeconds > 0) { "Time budget must be positive" }
        require(p50DurationSeconds > 0) { "Median duration must be positive" }
        require(remainingStudyDays >= 0) { "Remaining study days must not be negative" }

        val bySubject = facts.groupBy(ReviewCapacityFacts::subjectId)
        val exams = examEpochDayBySubject.entries
            .filter { (subject, examDay) ->
                val daysUntil = examDay - todayEpochDay
                subject.isNotBlank() && daysUntil in 1..ReviewCapacityPolicy.EXAM_WINDOW_DAYS
            }
            .sortedBy { it.key }
        // 当日容量只有一份：多科目考试通道与再平衡通道都从这份容量里取，绝不允许各拿整份。
        val dailyCapacity = ReviewCapacityPolicy.capacityPerDay(budgetSeconds, p50DurationSeconds)
        val maxDailyCapacity = ReviewCapacityPolicy.capacityPerDay(
            budgetSeconds = ReviewCapacityPolicy.grownBudgetSeconds(
                baseBudgetSeconds = budgetSeconds,
                multiplier = ReviewCapacityPolicy.MAX_DAILY_GROWTH,
            ),
            p50DurationSeconds = p50DurationSeconds,
        )
        val desiredQuotas = exams.map { (subject, examDay) ->
            ExamQuotaPlanner.plan(
                facts = bySubject[subject].orEmpty(),
                examEpochDay = examDay,
                todayEpochDay = todayEpochDay,
                baseRetention = baseRetention,
                budgetSeconds = budgetSeconds,
                p50DurationSeconds = p50DurationSeconds,
                studyDayWeights = studyDayWeightsBySubject[subject],
            ).quota
        }
        // 各科“按本科缺口/剩余天算出的应取数”之和超过单日容量时，按应取数比例把同一份
        // 容量分给各科（最大余数法，Σ = dailyCapacity）；不超时保持各自应取数。
        val quotaCaps = if (desiredQuotas.sum() > dailyCapacity) {
            ReviewCapacityPolicy.allocateByDayWeights(
                remaining = dailyCapacity,
                dayWeights = desiredQuotas,
            )
        } else {
            null
        }
        var deficitCount = 0
        var unmetCount = 0
        val examPicked = ArrayList<ReviewCapacityFacts>()
        exams.forEachIndexed { index, (subject, examDay) ->
            val plan = ExamQuotaPlanner.plan(
                facts = bySubject[subject].orEmpty(),
                examEpochDay = examDay,
                todayEpochDay = todayEpochDay,
                baseRetention = baseRetention,
                budgetSeconds = budgetSeconds,
                p50DurationSeconds = p50DurationSeconds,
                studyDayWeights = studyDayWeightsBySubject[subject],
                quotaCap = quotaCaps?.get(index),
            )
            deficitCount += plan.deficitCount
            unmetCount += plan.unmetCount
            examPicked += plan.picked
        }

        // 考试通道先占当日容量；再平衡只能用剩下的名额。考试在窗且已取题时不再额外叠加
        // F9 的 1.5×（当日预算已由考试爬坡扩容，叠加会变成 2.25×）；无考试取题时才允许
        // F9 的成长上限（maxDailyCapacity）。
        val remainingSlots = if (examPicked.isEmpty()) {
            maxDailyCapacity
        } else {
            (dailyCapacity - examPicked.size).coerceAtLeast(0)
        }
        val rebalance = if (rebalanceEnabled && remainingSlots > 0) {
            MissedDayRebalancePolicy.plan(
                backlog = facts,
                baseBudgetSeconds = if (examPicked.isEmpty()) {
                    budgetSeconds
                } else {
                    remainingSlots * p50DurationSeconds
                },
                p50DurationSeconds = p50DurationSeconds,
                remainingStudyDays = remainingStudyDays,
                itemCap = remainingSlots,
            )
        } else {
            null
        }
        val examPickedIds = examPicked.mapTo(hashSetOf(), ReviewCapacityFacts::practiceUnitId)
        val duplicated = rebalance?.picked.orEmpty().count { it.practiceUnitId in examPickedIds }
        return ReviewCapacityPlan(
            examDeficitCount = deficitCount,
            examUnmetCount = unmetCount,
            examPicked = examPicked,
            rebalance = rebalance,
            deduplicatedCount = duplicated,
            dailyCapacity = dailyCapacity,
            maxDailyCapacity = maxDailyCapacity,
        )
    }
}

/**
 * 合并后的只读容量计划。`examPicked` 与 `rebalance.picked` 可能含同一 practice unit；
 * [deduplicatedCount] 给出交集大小，消费方按"考试通道优先"只计一次时间。
 *
 * **容量口径（必须显式，不得再各自超配）**：
 * - [dailyCapacity]：`budgetSeconds` 折算的单日容量；多科目考试配额之和 ≤ 它。
 * - [maxDailyCapacity]：`budgetSeconds × 1.5` 折算的绝对上限（F9 成长上限）。
 * - [totalPickedCount]：两条通道去重后的实际题数。
 * - 无考试取题时 `totalPickedCount ≤ maxDailyCapacity`（F9 可成长）；
 *   有考试取题时 `totalPickedCount ≤ dailyCapacity`（再平衡只能用考试剩下的名额，
 *   不再叠加第二次 1.5×）。
 */
data class ReviewCapacityPlan(
    val examDeficitCount: Int,
    val examUnmetCount: Int,
    val examPicked: List<ReviewCapacityFacts>,
    val rebalance: RebalancePlan?,
    val deduplicatedCount: Int,
    val dailyCapacity: Int,
    val maxDailyCapacity: Int,
) {
    /** 两条通道按 `practiceUnitId` 去重后的实际题数（同一道题只计一次）。 */
    val totalPickedCount: Int
        get() {
            val ids = examPicked.mapTo(LinkedHashSet(), ReviewCapacityFacts::practiceUnitId)
            rebalance?.picked.orEmpty().forEach { ids += it.practiceUnitId }
            return ids.size
        }

    init {
        require(examDeficitCount >= 0) { "Exam deficit count must not be negative" }
        require(examUnmetCount >= 0) { "Exam unmet count must not be negative" }
        require(deduplicatedCount >= 0) { "Deduplicated count must not be negative" }
        require(dailyCapacity >= 0) { "Daily capacity must not be negative" }
        require(maxDailyCapacity >= dailyCapacity) {
            "Max daily capacity must not be below the base daily capacity"
        }
        require(examUnmetCount <= examDeficitCount) {
            "Unmet exam deficit must be a subset of the deficit count"
        }
        require(examPicked.map(ReviewCapacityFacts::practiceUnitId).distinct().size == examPicked.size) {
            "Exam-picked practice units must be unique across subjects"
        }
        require(examPicked.size <= dailyCapacity) {
            "Exam-picked items must fit in the shared daily capacity"
        }
        require(totalPickedCount <= maxDailyCapacity) {
            "Picked items must fit in the 1.5x daily ceiling"
        }
        require(examPicked.isEmpty() || totalPickedCount <= dailyCapacity) {
            "Exam and rebalance channels must share one daily capacity"
        }
        require(
            deduplicatedCount == rebalance?.picked.orEmpty()
                .count { it.practiceUnitId in examPicked.mapTo(hashSetOf(), ReviewCapacityFacts::practiceUnitId) },
        ) {
            "Deduplicated count must equal the channel intersection"
        }
    }
}
