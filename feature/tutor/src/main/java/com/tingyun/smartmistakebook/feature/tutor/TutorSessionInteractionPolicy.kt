package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry

internal const val TUTOR_CHOICE_SAVE_ERROR = "这个选择暂时没有保存，请重试后再继续。"
internal const val TUTOR_MOVE_SAVE_ERROR = "下一种讲法没有启动，请再试一次。"

/**
 * 选择写入的有界等待（KD-36）。
 *
 * 选项在"行到达前"保持锁定是既有语义（重复提交由它挡住），但写入**永远不回来**时
 * 没有第二条出路：界面锁死、库里零持久化、连错误都看不见。超过这个上限就按失败处理——
 * 解锁、给错误、让学生重试；真正的慢写入（本地 Room）远在这个量级以内。
 */
internal const val TUTOR_CHOICE_SAVE_TIMEOUT_MILLIS = 15_000L

internal fun tutorChoiceSubmissionCanStart(
    hasPlanOutput: Boolean,
    hasDiagnosticItem: Boolean,
    hasEvaluation: Boolean,
    interactionBusy: Boolean,
): Boolean = hasPlanOutput && hasDiagnosticItem && hasEvaluation && !interactionBusy

internal fun tutorMoveCanStart(
    interactionBusy: Boolean,
    hasExecutableProvider: Boolean,
): Boolean = !interactionBusy && hasExecutableProvider

internal fun tutorRestartCanStart(
    hasExecutableProvider: Boolean,
    hasConversationMemory: Boolean,
): Boolean = hasExecutableProvider && hasConversationMemory

/**
 * 「这一行是不是**这一轮**的计划任务」的唯一判据：轮次号与本轮前情（会话记忆 / 上一轮学生
 * 消息 / 已讲轮次）全部相同 = 同一次派发的又一次尝试；换题、换前情、换轮次都不是。
 *
 * 消灭的具体失败（KD-35 的判据半边）：这条判据此前只写在命令里那个 `count { … }` lambda 内部，
 * 单测够不着它，于是"重试算第几次尝试"没有任何纯逻辑钉子——而它正是重试身份（requestId 尾缀
 * `attempt`）的来源、也是"重试会不会复用旧行、撞上同标识不同载荷"的分水岭。
 */
internal fun ModelTaskSnapshot.matchesTutorPlanTurn(
    cycleOrdinal: Int,
    priorConversationMemory: TutorConversationMemory?,
    priorCycleStudentMessages: List<String>,
    priorTurns: List<TutorTurnHistoryEntry>,
): Boolean {
    val input = request.input as? TutorPlanInput ?: return false
    return input.cycleOrdinal == cycleOrdinal &&
        input.priorConversationMemory == priorConversationMemory &&
        input.priorCycleStudentMessages == priorCycleStudentMessages &&
        input.priorTurns == priorTurns
}

/**
 * 这一轮**已经落过几行**计划任务 = 这次派发是这一轮的第几次尝试（0 = 首次）。
 *
 * 它直接进 [tutorPlanRequestId] 的尾缀（KD-35）：重试必须换到新标识——沿用旧标识而载荷
 * 不同（重试的时钟必然不同）会撞写入路径的"同标识不同载荷"冲突。计数只能按"新读到的"
 * 任务列表算：冻在第一帧（那时通常还没有任何任务）的列表会把每次重试都算成第 0 次。
 */
internal fun tutorPlanAttemptCount(
    planTasks: List<ModelTaskSnapshot>,
    cycleOrdinal: Int,
    priorConversationMemory: TutorConversationMemory?,
    priorCycleStudentMessages: List<String>,
    priorTurns: List<TutorTurnHistoryEntry>,
): Int = planTasks.count { task ->
    task.matchesTutorPlanTurn(
        cycleOrdinal = cycleOrdinal,
        priorConversationMemory = priorConversationMemory,
        priorCycleStudentMessages = priorCycleStudentMessages,
        priorTurns = priorTurns,
    )
}

/**
 * 进入即自动开首轮的判据（A1）。
 *
 * [tasksObserved] 是"这条会话的计划任务流**已经发过至少一帧**"的信号。`observedTask == null`
 * 有两个完全不同的含义——"确实还没有这一轮的任务"（可以开轮）与"还没读到"（**不许**开轮）。
 * 没有这个信号时，provider 早于任务流首帧到达的那一次组合会把后者读成前者：同一轮被派发
 * 两遍（首次派发的任务还在路上，第二次派发又发了一条同类的计划请求）。首帧一到（哪怕那一帧
 * 是空列表——那是"确实没有任务"这件真事实）这个判据才说得清"要不要开轮"。
 *
 * 为什么不能靠延时/重试盖：延时只是把窗口缩小，竞态仍在；缺的本来就是"任务流读到第几帧了"
 * 这一件事实，补上它比猜一个足够长的等待更小也更准。
 */
internal fun tutorAutoStartsFirstTurn(
    autoStartFirstTurn: Boolean,
    conversationEnabled: Boolean,
    tasksObserved: Boolean,
    hasObservedTask: Boolean,
    provider: ProviderCapabilitySnapshot?,
): Boolean = autoStartFirstTurn &&
    conversationEnabled &&
    tasksObserved &&
    !hasObservedTask &&
    tutorAgentChatEnabled(provider, ModelTaskKind.TUTOR_PLAN)

/**
 * The single live agent gate for a tutor send surface. One place decides whether a send
 * may reach the provider right now: a local provider always dispatches (it never egresses),
 * while an external provider dispatches when it supports the kind and, for image-bearing
 * visual kinds, accepts images. A null or UNAVAILABLE provider fails closed.
 *
 * The global "model agent" consent toggle used to sit here as an extra condition; it was
 * removed on 2026-09-13 — a configured provider is the single condition, and the only way
 * to have a build that never egresses is the `strictOffline` flavour (no INTERNET permission).
 */
internal fun tutorAgentChatEnabled(
    provider: ProviderCapabilitySnapshot?,
    kind: ModelTaskKind,
): Boolean {
    val candidate = provider ?: return false
    if (candidate.executionLocation == ModelExecutionLocation.UNAVAILABLE) return false
    if (!candidate.supports(kind)) return false
    if (candidate.executionLocation == ModelExecutionLocation.LOCAL_NO_EGRESS) return true
    return true
}

internal const val TUTOR_RESPOND_VALIDATION_TITLE = "消息格式需要调整"
internal const val TUTOR_RESPOND_VALIDATION_MESSAGE = "这条消息包含暂时无法发送的字符，请调整后再试。"
internal const val TUTOR_RESPOND_NETWORK_TITLE = "这条消息还没有发出"
internal const val TUTOR_RESPOND_NETWORK_MESSAGE = "这条消息还没有发出，请重试。"

/**
 * 纯图消息的兜底正文。消息体不能为空，而学生这次只发了图片；与大厅同一句话，
 * 学生在两处看到的是同一种措辞。
 */
internal const val TUTOR_RESPOND_IMAGE_ONLY_MESSAGE = "请帮我看看这些图片。"

internal fun tutorRespondProviderCanExecute(
    provider: ProviderCapabilitySnapshot?,
): Boolean = provider != null &&
    provider.executionLocation != ModelExecutionLocation.UNAVAILABLE &&
    provider.supports(ModelTaskKind.TUTOR_RESPOND)

internal fun tutorRespondCollectCanStart(
    provider: ProviderCapabilitySnapshot?,
    requestHasEgressManifest: Boolean,
    allowExternalEnvelopeForLocalRecovery: Boolean,
    chatSubmitPending: Boolean,
): Boolean {
    if (!tutorAgentChatEnabled(provider, ModelTaskKind.TUTOR_RESPOND)) return false
    // Respond-specific recovery: dispatch an external-enveloped persisted task under a
    // local provider only when explicitly allowed.
    if (provider!!.executionLocation == ModelExecutionLocation.LOCAL_NO_EGRESS &&
        requestHasEgressManifest &&
        !allowExternalEnvelopeForLocalRecovery
    ) {
        return false
    }
    return !chatSubmitPending
}

/**
 * 派发一轮学生消息时推进发送状态（B5）。
 *
 * 这里曾经有一道**界面自己的派遣预算**：`InProgressBudgetExhausted`（上一轮还没落地就不许再发）
 * 与 `DispatchLimitReached`（这个界面自数到第 3 次就不许再发）。两者都删了——预算只有一个主人，
 * 是内核账本（`ModelTasks.MAX_DISPATCHES`）；界面自数一套只会比内核先到顶，把学生拦在一句
 * "发送次数已到上限"前面，而他的消息其实还能发。
 *
 * 现在的语义只有两条：**新学生消息一律开新回合**（先 [TutorSendAction.Reset] 回到空闲，再落
 * 这一轮的标识），**重试沿用原标识**（同一次派发的重放不该变成第二次派发）。
 */
internal fun tutorRespondSendAdvance(
    sendState: TutorSendState,
    logicalOperationId: String,
    messageId: String,
    isRetry: Boolean,
): TutorSendState = if (isRetry) {
    TutorTurnSendStateMachine.reduce(
        sendState,
        TutorSendAction.ConsentGranted(logicalOperationId, messageId),
    )
} else {
    TutorTurnSendStateMachine.reduce(
        TutorTurnSendStateMachine.reduce(
            TutorTurnSendStateMachine.reduce(sendState, TutorSendAction.Reset),
            TutorSendAction.StudentMessagePersisted(logicalOperationId, messageId),
        ),
        TutorSendAction.ConsentGranted(logicalOperationId, messageId),
    )
}

internal fun tutorRespondExecuteCanStart(
    hasPlanOutput: Boolean,
    providerCanExecute: Boolean,
    messageBlank: Boolean,
    chatSending: Boolean,
): Boolean = hasPlanOutput && providerCanExecute && !messageBlank && !chatSending
