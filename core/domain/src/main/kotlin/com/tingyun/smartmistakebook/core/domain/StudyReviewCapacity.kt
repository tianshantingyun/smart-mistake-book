package com.tingyun.smartmistakebook.core.domain

/**
 * 逐科容量读数（5b 容量 UI / KD-68 同族的读面事实）。
 *
 * 这是 `core/data` 里 `SubjectCapacity` 的 **core/domain 镜像**：`feature` 模块不能依赖
 * `core:data`（依赖边界），所以快照必须在 domain 层给出同一份事实，而不是让 UI 自己重算。
 *
 * - [demand]：本科按考试缺口与剩余学习日算出的应排数（未受共享日容量挤压前）；
 * - [granted]：实际分到的名额；
 * - [unmet]：`demand − granted`，被共享日容量挤掉的部分；
 * - [feasibleBeforeExam]：按今天这个名额考前是否做得完；false 是必须如实上报的读数，
 *   不能对用户承诺覆盖。
 */
data class StudySubjectCapacity(
    val subjectId: String,
    val daysUntilExam: Int,
    val demand: Int,
    val granted: Int,
    val unmet: Int,
    val feasibleBeforeExam: Boolean,
) {
    init {
        require(subjectId.isNotBlank()) { "Capacity subject id must not be blank" }
        require(daysUntilExam >= 1) { "Days until exam must be positive" }
        require(demand >= 0) { "Capacity demand must not be negative" }
        require(granted in 0..demand) { "Capacity grant must fit the demand" }
        require(unmet == demand - granted) { "Capacity unmet must equal demand minus grant" }
    }
}

/**
 * 复习容量预览的只读事实（5b 容量 UI）：今日容量、考试缺口、积压追赶状态、逐科名额。
 *
 * 由 `StudyReviewPlannerService.reviewCapacityPreview` 经 `StudySnapshotBuilder` 派生，
 * 与排程侧同一 `ForgettingCurve`/同一考试表/同一预算口径；UI 只渲染，不重算、不写计划。
 *
 * **null 与全零的区别（F12/KD-68 诚实原则）**：`StudyReviewOverview.capacity == null` 表示
 * "没有容量规划"（考试窗口内没有考试，且积压追赶未规划），读面必须显示如实空态；
 * 非 null 时 [dailyCapacity] 才是"今天能排几题"的真实上限。[rebalancePlanned] 为 false 时
 * [rebalanceBacklogCount] 的 0 **不表示**"没有积压"，只表示这条通道没有参与本轮规划。
 */
data class StudyReviewCapacity(
    val examSubjectsInWindow: Int = 0,
    val examDeficitCount: Int = 0,
    val examUnmetCount: Int = 0,
    val rebalancePlanned: Boolean = false,
    val rebalanceBacklogCount: Int = 0,
    val rebalanceIneligibleCount: Int = 0,
    val rebalanceGranted: Int = 0,
    val deduplicatedCount: Int = 0,
    val subjects: List<StudySubjectCapacity> = emptyList(),
    val totalGranted: Int = 0,
    val dailyCapacity: Int = 0,
    val maxDailyCapacity: Int = 0,
) {
    init {
        require(examSubjectsInWindow >= 0) { "Exam subject count must not be negative" }
        require(examDeficitCount >= 0) { "Exam deficit count must not be negative" }
        require(examUnmetCount in 0..examDeficitCount) {
            "Unmet exam deficit must be a subset of the deficit count"
        }
        require(rebalanceBacklogCount >= 0) { "Rebalance backlog count must not be negative" }
        require(rebalanceIneligibleCount in 0..rebalanceBacklogCount) {
            "Ineligible backlog must be a subset of the backlog count"
        }
        require(rebalanceGranted in 0..rebalanceBacklogCount) {
            "Rebalance grants must come from the reported backlog"
        }
        require(deduplicatedCount >= 0) { "Deduplicated count must not be negative" }
        require(dailyCapacity >= 0) { "Daily capacity must not be negative" }
        require(maxDailyCapacity >= dailyCapacity) {
            "Max daily capacity must not be below the base daily capacity"
        }
        require(totalGranted == subjects.sumOf(StudySubjectCapacity::granted) + rebalanceGranted) {
            "Total granted must equal exam grants plus the rebalance channel"
        }
        require(totalGranted <= maxDailyCapacity) {
            "Preview total must not exceed the growth ceiling"
        }
        // 未规划不是"积压 0 题"：没有规划就不允许同时报出积压或取题。
        require(rebalancePlanned || (rebalanceGranted == 0 && rebalanceBacklogCount == 0)) {
            "A not-planned rebalance channel must not report backlog or grants"
        }
        require(subjects.isEmpty() || subjects.sumOf(StudySubjectCapacity::granted) <= dailyCapacity) {
            "Exam-channel preview total must fit the shared daily capacity"
        }
    }
}
