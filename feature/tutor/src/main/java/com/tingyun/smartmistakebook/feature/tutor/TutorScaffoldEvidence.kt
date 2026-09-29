package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorScaffoldEvidence

/**
 * 这条会话的**卡点证据**（D-Q9 的本地一半）：只从本地可观察的事实算，语义判断留给模型。
 *
 * 两件可观察的事实：
 * - **连续失败轮数**：最近连续几轮没有拿到可用的回复（派发失败 / 被中断 / 取消）。它对应的
 *   真实场景是"学生问了好几次都没得到答复"——本地能看见的就是这条；
 * - **上一轮耗时**：最近一轮从派发到终态的时长（学生卡在一轮里很久，通常意味着他在自己试，
 *   或者这一轮对他确实难）。取**最长的那一轮**而不是最后一轮：最后一轮可能是刚才那次失败。
 *
 * 学生"还是不懂/还是不会"这种语义信号本地**不猜**（那正是被删掉的
 * `TutorIntentAuthorityPolicy` 的老路），由模型自己按提示词里的规则判断并直接给到 L4。
 */
internal fun tutorLobbyStallEvidence(tasks: List<ModelTaskSnapshot>): TutorScaffoldEvidence {
    val ordered = tasks.sortedBy { task -> task.request.occurredAtEpochMillis }
    val consecutiveFailedRounds = ordered.reversed()
        .takeWhile { task -> task.status in FAILURE_STATUSES }
        .size
    val longestRoundMillis = ordered
        .filter { task -> task.status == ModelTaskStatus.SUCCEEDED }
        .maxOfOrNull { task -> task.updatedAtEpochMillis - task.createdAtEpochMillis }
    return TutorScaffoldEvidence(
        consecutiveFailedRounds = consecutiveFailedRounds,
        lastRoundDurationMillis = longestRoundMillis,
    )
}

/**
 * "这一轮没有拿到可用回复"的状态集合。取消也算：学生按停止多半是因为这一轮没在给他想要的东西，
 * 而升一档的代价只是"这次多给一步"——比"再问一次还是老样子"便宜。
 */
private val FAILURE_STATUSES = setOf(
    ModelTaskStatus.RETRYABLE_FAILURE,
    ModelTaskStatus.PERMANENT_FAILURE,
    ModelTaskStatus.CANCELLED,
)
