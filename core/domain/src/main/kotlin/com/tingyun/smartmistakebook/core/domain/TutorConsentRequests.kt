package com.tingyun.smartmistakebook.core.domain

/**
 * 确认卡接线（插眼 5，`docs/research/2026-09-25-mastery-mechanism-review.md` §5）。
 *
 * 把三件已经存在、彼此不认识的东西接起来：
 *
 * 1. **权限档**（[tutorPermissionTier]）：ask 档的对象才挂卡；
 * 2. **持久行**（[AgentPendingRequestRepository]）：挂起的卡落库，进程死亡后读得回来；
 * 3. **相位机**（[TutorTurnSendStateMachine]）：回合进 [TutorSendPhase.AWAITING_CONSENT]，
 *    裁决后继续。**这是 `AWAITING_CONSENT` 的真空生产者**——改前这个相位只有状态机与动作
 *    声明，没有任何东西会把它推出来（也没有任何东西会把它推出去）。
 *
 * ## 不悬死（插眼 5 的硬要求）
 *
 * - 挂起只在**行真的落了**之后才算成立：[suspend] 返回 null = 没挂成，调用方**不得**推进相位
 *   （相位在内存里，行不在库里；进程一死就悬死）。
 * - 裁决**一定落库**（终态 + 留痕）；回合还在等确认才推进相位，否则原样返回——
 *   卡不会因为回合结束而永远挂着。
 * - 同一回合的相位不会被过期结果改写：行的回合身份与当前状态不一致时不动状态机
 *   （`TutorTurnSendStateMachine` 的陈旧保护会给 PERMANENT_FAILURE，那是给"用错了回合"的，
 *   不是给"回合已经走过去了"的——后者是正常时序）。
 *
 * ## 相位命名的一处将就（写在这里，免得下一个人以为"同意"是执行条件）
 *
 * 裁决的三个终态在相位上是同一件事：**裁决已作出、回合可以继续**，而状态机里从
 * `AWAITING_CONSENT` 走出去的动作只有 [TutorSendAction.ConsentGranted]（没有独立的拒绝动作）。
 * 本文件因此用它表示"裁决后继续"——**同意与否由待确认行的终态承载，不由相位承载**：
 * 执行路径读行（[AgentPendingRequest.status]），不读相位。真正执行本地动作是阶段 2 的事
 * （拒绝时模型收到的也是"没执行"的结果，见 [AgentPendingRequestOutcome]）。
 */
class TutorConsentRequests(
    private val requests: AgentPendingRequestRepository,
) {
    /**
     * 一个 ask 档对象请求挂起本回合。
     *
     * 顺序是"先算相位、再落行"：相位算不出来（陈旧回合）就不该在库里留下一张没有主人的卡——
     * 那种行是悬死卡，正是本机制要消灭的东西。反过来，行没落成时返回 null，调用方保持原状态。
     *
     * @return 挂起后的行与相位；本实现不落库、或本回合已陈旧时返回 null。
     */
    suspend fun suspend(
        state: TutorSendState,
        command: SuspendTurnForConsentCommand,
    ): TutorConsentSuspension? {
        val decision = tutorPermissionDecision(
            subject = command.subject,
            context = command.context,
        )
        require(decision.requiresConsentCard) {
            "Only an admitted ask-tier subject suspends a turn for consent: " +
                "${command.subject} is ${decision.tier} (admitted=${decision.admitted})"
        }
        val kind = checkNotNull(agentPendingRequestKind(command.subject)) {
            "An ask-tier subject must have a pending request kind: ${command.subject}"
        }
        val logicalOperationId = requireNotNull(state.logicalOperationId) {
            "A turn without a logical operation id cannot hang a consent card"
        }
        val messageId = requireNotNull(state.messageId) {
            "A turn without a message id cannot hang a consent card"
        }
        val suspended = TutorTurnSendStateMachine.reduce(
            state,
            TutorSendAction.ConsentRequired(logicalOperationId, messageId),
        )
        if (suspended.phase != TutorSendPhase.AWAITING_CONSENT) return null
        val draft = AgentPendingRequestDraft(
            conversationArea = command.conversationArea,
            conversationId = command.conversationId,
            logicalOperationId = logicalOperationId,
            messageId = messageId,
            kind = kind,
            payloadJson = command.payloadJson,
        )
        val request = requests.createRequest(
            draft = draft,
            createdAtEpochMillis = command.occurredAtEpochMillis,
        ) ?: return null
        return TutorConsentSuspension(request = request, state = suspended)
    }

    /**
     * 学生裁决：终态落库 + 留痕，回合继续。
     *
     * [TutorConsentResolution.state] 只在**原回合仍在等这张卡**时前进（DISPATCHING）；否则原样
     * 返回——裁决照样落库（"点击后终态 + 留痕"），只是这个回合不需要被推进。
     *
     * @return 终态行、回合相位与回喂行；本实现不落库时返回 null。
     */
    suspend fun decide(
        state: TutorSendState,
        command: DecidePendingRequestCommand,
    ): TutorConsentResolution? {
        val request = requests.resolveRequest(
            requestId = command.requestId,
            decision = command.decision,
            resolutionNote = command.resolutionNote,
            resolvedAtEpochMillis = command.occurredAtEpochMillis,
        ) ?: return null
        val stillWaiting = state.phase == TutorSendPhase.AWAITING_CONSENT &&
            state.logicalOperationId == request.logicalOperationId &&
            state.messageId == request.messageId
        val resumed = if (stillWaiting) {
            TutorTurnSendStateMachine.reduce(
                state,
                TutorSendAction.ConsentGranted(request.logicalOperationId, request.messageId),
            )
        } else {
            state
        }
        return TutorConsentResolution(
            request = request,
            state = resumed,
            outcome = request.toOutcome(),
        )
    }

    /**
     * 进程死亡/离开后回到原会话区，重建**同一张卡**：行自己带着回合身份
     * （[AgentPendingRequest.logicalOperationId] / [AgentPendingRequest.messageId]），
     * 所以重建不需要别的记忆——这正是把这两列落库的理由。
     *
     * @return 重建出的挂起；行与当前状态对不上（回合已经走过去）时返回 null。
     */
    fun resumeAfterRestart(
        state: TutorSendState,
        request: AgentPendingRequest,
    ): TutorConsentSuspension? {
        if (state.logicalOperationId != null && state.logicalOperationId != request.logicalOperationId) {
            return null
        }
        if (state.messageId != null && state.messageId != request.messageId) return null
        if (request.status != AgentPendingRequestStatus.PENDING) return null
        val resumed = TutorTurnSendStateMachine.reduce(
            state,
            TutorSendAction.ResumeAfterRestart(request.logicalOperationId, request.messageId),
        )
        if (resumed.phase != TutorSendPhase.AWAITING_CONSENT) return null
        return TutorConsentSuspension(request = request, state = resumed)
    }
}

/** 挂起一个回合的输入：对象 + 本轮上下文 + 落库所需的身份与参数。 */
data class SuspendTurnForConsentCommand(
    val subject: TutorPermissionSubject,
    val conversationArea: String,
    val conversationId: String,
    /** 该 kind 的固定参数形状（JSON object）；见 [requireAgentPendingRequestPayload]。 */
    val payloadJson: String,
    val occurredAtEpochMillis: Long,
    val context: TutorRoundPermissionContext = TutorRoundPermissionContext(),
) {
    init {
        require(conversationArea.isNotBlank()) { "A consent turn needs a conversation area" }
        require(conversationId.isNotBlank()) { "A consent turn needs a conversation" }
        require(occurredAtEpochMillis >= 0L) { "A consent turn time must not be negative" }
    }
}

/** 裁决一个待确认请求的输入。理由可选：留痕给"被拒理由"用，不是第二个必填表单。 */
data class DecidePendingRequestCommand(
    val requestId: String,
    val decision: AgentPendingRequestDecision,
    val resolutionNote: String? = null,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(requestId.isNotBlank()) { "A pending request decision needs a request id" }
        require(occurredAtEpochMillis >= 0L) { "A decision time must not be negative" }
        require(resolutionNote == null || resolutionNote.isNotBlank()) {
            "A decision note must be null or non-blank"
        }
    }
}

/** 挂起成立：行 + 相位。行是权威，相位是这一刻的视图。 */
data class TutorConsentSuspension(
    val request: AgentPendingRequest,
    val state: TutorSendState,
)

/** 裁决成立：终态行 + 回合相位 + 回喂模型/卡上呈现的一行。 */
data class TutorConsentResolution(
    val request: AgentPendingRequest,
    val state: TutorSendState,
    val outcome: AgentPendingRequestOutcome,
)

internal fun AgentPendingRequest.toOutcome() = AgentPendingRequestOutcome(
    requestId = requestId,
    kind = kind,
    status = status,
    resolutionNote = resolutionNote,
)
