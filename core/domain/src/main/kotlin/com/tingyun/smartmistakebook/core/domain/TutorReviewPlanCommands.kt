package com.tingyun.smartmistakebook.core.domain

/**
 * `ADD_TO_REVIEW_PLAN` 的本地执行结果。
 *
 * 消灭的具体失败：这个动作以前只有确认卡的**历史解码**（`NOT_WIRED_YET`），模型被广告后
 * 执行只会得到一句"还没接通"——学生要的"把这道题排进今天的复习"从来没有真正发生。
 * 结果做成 sealed 类型是因为调用方必须对四种结局给出不同的话：真的排进去了、本来就在、
 * 计划已开始/完成（不能改）、预算装不下（不能假装排满还成功）。
 */
sealed interface AddToReviewPlanResult {
    /** 已经作为新的一项写入今天的计划（新 revision）。 */
    data class Added(
        val reviewPlanId: String,
        val ordinal: Int,
        val scheduledCount: Int,
    ) : AddToReviewPlanResult

    /** 这道题本来就在今天的计划里（重复请求幂等成功，不新建 revision）。 */
    data class AlreadyScheduled(
        val reviewPlanId: String,
    ) : AddToReviewPlanResult

    /** 没有写入，原因见 [failure]；[detail] 是可直接给学生看的一句话。 */
    data class NotAdded(
        val failure: AddToReviewPlanFailure,
        val detail: String,
    ) : AddToReviewPlanResult
}

enum class AddToReviewPlanFailure {
    /** 错题本里已经没有这个条目。 */
    ENTRY_NOT_FOUND,

    /** 今天还没有可编辑的计划（快照未就绪）。 */
    PLAN_NOT_READY,

    /** 今天的复习会话已经开始（队列 ordinal 已冻结，不能改写）。 */
    PLAN_STARTED,

    /** 今天的复习已经完成。 */
    PLAN_COMPLETED,

    /** 计划的每日预算装不下这一项。 */
    BUDGET_EXHAUSTED,

    /** 这道题既没有现役知识绑定，pseudo 绑定也没能落库。 */
    KNOWLEDGE_BINDING_MISSING,

    /** 仓储没有实现这条命令（旧替身/旧构建）。 */
    UNSUPPORTED,
}
