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
        growthCeilingBudgetSeconds: Int? = null,
    ): ReviewCapacityPlan {
        require(todayEpochDay >= 0) { "Today epoch day must not be negative" }
        require(baseRetention in 0.7..ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION) {
            "Base retention must be in 0.7..${ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION}"
        }
        require(budgetSeconds > 0) { "Time budget must be positive" }
        require(p50DurationSeconds > 0) { "Median duration must be positive" }
        require(remainingStudyDays >= 0) { "Remaining study days must not be negative" }
        require(growthCeilingBudgetSeconds == null || growthCeilingBudgetSeconds >= budgetSeconds) {
            "Growth ceiling budget must not be below the base budget"
        }
        require(facts.map(ReviewCapacityFacts::practiceUnitId).distinct().size == facts.size) {
            "Capacity facts must be unique per practice unit"
        }

        val bySubject = facts.groupBy(ReviewCapacityFacts::subjectId)
        val exams = examEpochDayBySubject.entries
            .filter { (subject, examDay) ->
                val daysUntil = examDay - todayEpochDay
                subject.isNotBlank() && daysUntil in 1..ReviewCapacityPolicy.EXAM_WINDOW_DAYS
            }
            .sortedBy { it.key }
        // 当日容量只有一份：多科目考试通道与再平衡通道都从这份容量里取，绝不允许各拿整份。
        val dailyCapacity = ReviewCapacityPolicy.capacityPerDay(budgetSeconds, p50DurationSeconds)
        // 绝对上限由**未爬坡**的基准预算 + 显式上限算：调用方传进来的 budgetSeconds 若已经是
        // 考试扩容后的值，不能再乘一次 1.5（会到 2.25×）。默认仍保持 `budget × 1.5` 的既有语义。
        val maxDailyCapacity = ReviewCapacityPolicy.capacityPerDay(
            budgetSeconds = growthCeilingBudgetSeconds ?: ReviewCapacityPolicy.grownBudgetSeconds(
                baseBudgetSeconds = budgetSeconds,
                multiplier = ReviewCapacityPolicy.MAX_DAILY_GROWTH,
            ),
            p50DurationSeconds = p50DurationSeconds,
        )
        val subjectIds = exams.map { it.key }
        // 第一遍只算"本科按缺口/剩余天应取多少"（demand），不带共享容量上限。
        val demands = exams.map { (subject, examDay) ->
            ExamQuotaPlanner.plan(
                facts = bySubject[subject].orEmpty(),
                examEpochDay = examDay,
                todayEpochDay = todayEpochDay,
                baseRetention = baseRetention,
                budgetSeconds = budgetSeconds,
                p50DurationSeconds = p50DurationSeconds,
                studyDayWeights = studyDayWeightsBySubject[subject],
            )
        }
        val desiredQuotas = demands.map { it.quota }
        // 各科应取数之和超过单日容量时，按"距考天数升序、缺口降序"把同一份容量分成逐科名额
        // （R1 §4.2）；不超时各科保持自己的应取数。分科配额而不是全局队列：排程与预览都
        // 需要"这一科今天分到几个"的读数，且 Anki 的根总量/子配额正是这一层语义。
        val quotaCaps = if (desiredQuotas.sum() > dailyCapacity) {
            ReviewCapacityPolicy.allocateSubjectCaps(
                subjectIds = subjectIds,
                demands = desiredQuotas,
                daysUntilExam = demands.map { it.daysUntilExam },
                deficits = demands.map { it.deficitCount },
                capacity = dailyCapacity,
            )
        } else {
            null
        }
        var deficitCount = 0
        var unmetCount = 0
        val examPicked = ArrayList<ReviewCapacityFacts>()
        val subjectReadouts = ArrayList<SubjectCapacity>(exams.size)
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
            val demand = desiredQuotas[index]
            subjectReadouts += SubjectCapacity(
                subjectId = subject,
                daysUntilExam = plan.daysUntilExam,
                demand = demand,
                granted = plan.picked.size,
                unmet = demand - plan.picked.size,
                feasibleBeforeExam = plan.deficitCount <= plan.quota * plan.studyDaysLeft,
            )
        }

        // 考试通道先占当日容量；再平衡只能用剩下的名额。考试在窗且已取题时不再额外叠加
        // F9 的 1.5×（当日预算已由考试爬坡扩容，叠加会变成 2.25×）；无考试取题时才允许
        // F9 的成长上限（maxDailyCapacity）。
        val remainingSlots = if (examPicked.isEmpty()) {
            maxDailyCapacity
        } else {
            (dailyCapacity - examPicked.size).coerceAtLeast(0)
        }
        // 去重前置：考试已取的题不再参与积压通道（R1 T5/T6）；合并处的交集因此恒为空，
        // 不会出现"先取后去重、重复题白占名额"。
        val examPickedIds = examPicked.mapTo(hashSetOf(), ReviewCapacityFacts::practiceUnitId)
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
                excludedPracticeUnitIds = examPickedIds,
            )
        } else {
            null
        }
        val duplicated = rebalance?.picked.orEmpty().count { it.practiceUnitId in examPickedIds }
        return ReviewCapacityPlan(
            examDeficitCount = deficitCount,
            examUnmetCount = unmetCount,
            examPicked = examPicked,
            rebalance = rebalance,
            deduplicatedCount = duplicated,
            dailyCapacity = dailyCapacity,
            maxDailyCapacity = maxDailyCapacity,
            subjectReadouts = subjectReadouts,
            starvedSubjects = subjectReadouts
                .filter { it.demand > 0 && it.granted == 0 }
                .map(SubjectCapacity::subjectId),
        )
    }
}

/**
 * 合并后的只读容量计划。去重发生在取题**之前**（[MissedDayRebalancePolicy.plan] 的
 * `excludedPracticeUnitIds`），所以 [deduplicatedCount] 恒为 0；保留该字段作为回归哨兵。
 *
 * **容量口径（必须显式，不得再各自超配）**：
 * - [dailyCapacity]：`budgetSeconds` 折算的单日容量；多科目考试配额之和 ≤ 它。
 * - [maxDailyCapacity]：绝对上限（F9 成长上限）。默认 `budgetSeconds × 1.5`；调用方已把
 *   爬坡后的预算作为 `budgetSeconds` 传入时，必须同时传 `growthCeilingBudgetSeconds`，
 *   否则会二次放大到 2.25×。
 * - [totalPickedCount]：两条通道的实际题数（无交集）。
 * - 无考试取题时 `totalPickedCount ≤ maxDailyCapacity`（F9 可成长）；
 *   有考试取题时 `totalPickedCount ≤ dailyCapacity`（再平衡只能用考试剩下的名额）。
 */
data class ReviewCapacityPlan(
    val examDeficitCount: Int,
    val examUnmetCount: Int,
    val examPicked: List<ReviewCapacityFacts>,
    val rebalance: RebalancePlan?,
    val deduplicatedCount: Int,
    val dailyCapacity: Int,
    val maxDailyCapacity: Int,
    /** 逐科读数（与 `examEpochDayBySubject` 的科序一致）：份额从哪里来、哪一科被挤。 */
    val subjectReadouts: List<SubjectCapacity> = emptyList(),
    /** 应取 > 0 却一个名额也没分到的科（名额已用尽），由调用方如实展示。 */
    val starvedSubjects: List<String> = emptyList(),
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
        require(deduplicatedCount == 0) {
            "Rebalance must exclude exam-picked units before picking; the intersection must be empty"
        }
        require(subjectReadouts.sumOf(SubjectCapacity::granted) == examPicked.size) {
            "Subject readouts must account for every exam-picked item"
        }
        require(subjectReadouts.all { it.granted <= it.demand && it.unmet == it.demand - it.granted }) {
            "Each subject grant must fit its demand and report the unmet remainder"
        }
        require(subjectReadouts.map(SubjectCapacity::subjectId).distinct().size == subjectReadouts.size) {
            "Subject readouts must be unique per subject"
        }
        require(
            starvedSubjects == subjectReadouts
                .filter { it.demand > 0 && it.granted == 0 }
                .map(SubjectCapacity::subjectId),
        ) {
            "Starved subjects must be exactly the subjects with demand but no grant"
        }
    }
}

/**
 * 一科今天分到的名额与诚实读数（WS1 item 3 的逐科面）。
 *
 * - [demand]：本科按缺口/剩余天算出的应取数（未受共享容量挤压前）；
 * - [granted]：实际分到的名额（= 本科今日 picked 数）；
 * - [unmet]：`demand − granted`，被共享容量挤掉的部分；
 * - [feasibleBeforeExam]：`deficit ≤ granted × 剩余学习日`——按今天这个名额，考前做得完吗。
 *   为 false 时该科无论怎么排都赶不上，必须作为诚实读数上报，不能承诺覆盖。
 */
data class SubjectCapacity(
    val subjectId: String,
    val daysUntilExam: Int,
    val demand: Int,
    val granted: Int,
    val unmet: Int,
    val feasibleBeforeExam: Boolean,
) {
    init {
        require(subjectId.isNotBlank()) { "Subject id must not be blank" }
        require(daysUntilExam in 1..ReviewCapacityPolicy.EXAM_WINDOW_DAYS) {
            "Days until exam must be inside the ramp window"
        }
        require(demand >= 0) { "Subject demand must not be negative" }
        require(granted >= 0) { "Subject grant must not be negative" }
        require(granted <= demand) { "Subject grant must not exceed its demand" }
        require(unmet == demand - granted) { "Unmet must equal demand minus grant" }
    }
}
