package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest

/**
 * 一条**模型动作请求**的本地准入结果：这件事本地认不认、认了的话挂什么参数的卡。
 *
 * 消灭的具体失败：动作请求此前没有准入这一环——模型的本地动作要么被界面静默丢弃（大厅），
 * 要么只按"本轮意图"推出来一张没有参数来源的卡。这里把三件事**按顺序**问一遍：
 *
 * 1. **形状**：请求里的参数键必须在 [TutorLocalAction.parameters] 里声明过（除
 *    `START_EXPORT` 的模板/版式参数外，任何参数都是无效请求——模型只能选不能造）；
 * 2. **权限档**：`TutorPermissionPolicy` 的档位与本轮放行（四个动作恒 ask，本轮放行由
 *    [TutorRoundPermissionContext.allowedActions] 给）；
 * 3. **目标**：本轮**本地**有没有可执行的东西（[tutorLocalActionTarget]）——没有目标的卡不该
 *    出现，那不是"待确认"，那是"点了没反应"。
 *
 * 三步全过才返回一张卡的种子；任何一步不过返回 null（**不挂卡**，也不假装执行）。
 * 三个判定的输入全部来自本轮事实，不看入口、不看栏（ADR 0001 内核部分）。
 */
data class TutorLocalActionAdmission(
    /** 动作拼写下是哪个动作；工具拼写（`NOTEBOOK_WRITE`）没有动作 id，为 null。 */
    val action: TutorLocalAction?,
    val kind: AgentPendingRequestKind,
    /** 该 kind 的固定字段形状的 payload（[agentPendingRequestPayloadKeys]）。 */
    val payloadJson: String,
    /** 本地解析出来的执行目标；null 不可能出现在准入结果里（构造时就拒了）。 */
    val target: TutorLocalActionTarget,
    /** 权限档裁决（ask + admitted），保留给调用方与留痕。 */
    val decision: TutorPermissionDecision,
    /**
     * 准入时用的那份上下文：挂卡时 `TutorConsentRequests.suspend` 会**再判一次**同一件事，
     * 两次判定的输入必须同源（一边放行一边拒绝，卡就会凭空消失）。
     */
    val permissionContext: TutorRoundPermissionContext,
) {
    val subject: TutorPermissionSubject get() = decision.subject
}

/**
 * 一条动作请求 → 准入结果；null = 不挂卡（形状不合 / 不放行 / 本地没有目标）。
 *
 * [context] 是本轮**本地事实**（拍照会话 / 已入库的题 / 本条消息的附图）；模型无法影响它，
 * 这正是"固定 ID + 固定参数形状，本地定义"的落点：模型选的是**哪件事**，目标是本地解析的
 * **哪一件东西**。
 */
fun tutorLocalActionAdmission(
    request: TutorLocalActionRequest,
    context: TutorLocalActionContext,
    permissionContext: TutorRoundPermissionContext = TutorRoundPermissionContext(),
): TutorLocalActionAdmission? {
    // ① 形状：参数键必须在声明里；必填项必须在。
    val declared = request.action.parameters
    val declaredNames = declared.map { parameter -> parameter.parameterName }.toSet()
    if (!request.parameters.keys.all { key -> key in declaredNames }) return null
    if (declared.any { parameter ->
            parameter.required && request.parameters[parameter.parameterName].isNullOrBlank()
        }
    ) {
        return null
    }
    return tutorLocalActionAdmission(
        subject = TutorPermissionSubject.LocalAction(request.action),
        context = context,
        permissionContext = permissionContext,
        actionParameters = request.parameters,
    )
}

/**
 * 同一个准入的三步判定，**按对象**给（两种拼写都从这里走）：工具拼写（`NOTEBOOK_WRITE` 被
 * 授权矩阵放进 ask 档、工具环没有执行它）与动作拼写（模型在本地动作通道里选了
 * `SAVE_TO_NOTEBOOK`）最后落在**同一张卡**上——这是 D-K2e 白名单表里"同一件事的两种拼写"
 * 在代码里的落点。
 */
fun tutorLocalActionAdmission(
    subject: TutorPermissionSubject,
    context: TutorLocalActionContext,
    permissionContext: TutorRoundPermissionContext = TutorRoundPermissionContext(),
    /**
     * 动作拼写带来的参数（工具拼写没有：`NOTEBOOK_WRITE` 的形状是空集）。
     *
     * `START_EXPORT` 的 payload 只能从这些参数来（模板名 + 版式参数，见
     * [exportLayoutFromActionParameters]）——没有参数就没有提议，**不挂卡**（不替模型挑一个
     * 默认模板塞进学生面前；模型按声明必填 templateId）。
     */
    actionParameters: Map<String, String> = emptyMap(),
): TutorLocalActionAdmission? {
    // 档位与本轮放行（四个动作恒 ask；没放行连卡都不出现）。
    val decision = tutorPermissionDecision(subject, permissionContext)
    if (!decision.requiresConsentCard) return null
    val kind = agentPendingRequestKind(subject) ?: return null
    // ③ 目标：本轮本地有什么可执行的。
    val payloadContext = actionPayloadContext(kind = kind, context = context)
    // 存题（两种拼写）在**本轮没有任何可保存的东西**时不挂卡：挂一张"点了只会打开错题本"的卡
    // 不是"待确认"，那是学生点了才发现答非所问（老毛病："点了什么也没发生"的同一类）。
    if (kind in SAVE_KINDS && payloadContext.isEmpty) return null
    val target = tutorLocalActionTarget(kind, payloadContext) ?: return null
    val payloadJson = when (kind) {
        AgentPendingRequestKind.START_EXPORT -> {
            val layout = exportLayoutFromActionParameters(actionParameters) ?: return null
            exportProposalPayload(layout)
        }
        else -> payloadContext.toAgentPendingRequestPayload(
            keys = agentPendingRequestPayloadKeys(kind),
        )
    }
    requireAgentPendingRequestPayload(kind = kind, payloadJson = payloadJson)
    val action = (subject as? TutorPermissionSubject.LocalAction)?.action
    return TutorLocalActionAdmission(
        action = action,
        kind = kind,
        payloadJson = payloadJson,
        target = target,
        decision = decision,
        permissionContext = permissionContext,
    )
}

/**
 * 动作 kind 需要的**那一部分**上下文：打开某题 / 纳入复习计划只关心"是哪道题"，
 * 存题关心全部三种目标，选择导出什么都不关心（题由学生在导出 sheet 里勾选，版式来自
 * [exportLayoutFromActionParameters] 的模型提议）。
 *
 * 裁掉的部分**不进 payload**：一张"打开某题"的卡带着拍照会话 id 落库，读回的人会以为
 * 它跟那次拍照有关——卡片的目标必须是它真正会用到的那几个字段。
 */
private fun actionPayloadContext(
    kind: AgentPendingRequestKind,
    context: TutorLocalActionContext,
): TutorLocalActionContext = when (kind) {
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
    -> context

    AgentPendingRequestKind.OPEN_PROBLEM,
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN,
    -> TutorLocalActionContext(libraryProblemId = context.libraryProblemId)

    AgentPendingRequestKind.START_EXPORT -> TutorLocalActionContext()
}

/** 存题的两张拼写：它们的卡必须真的有一件东西可存。 */
private val SAVE_KINDS = setOf(
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
)

/**
 * 本轮本地动作通道放行的动作集合（[TutorRoundPermissionContext.allowedActions] 的来源）。
 *
 * 一个动作"放行"= **它的请求形态过了本地形状核对**（键集与声明一致）。与工具面的口径对齐
 * （`tutorToolAuthorization` 的放行来自意图 × 置信度 × 声明集）：这里不做语义判断，语义是
 * 模型的事——本地只管"这条请求是不是按契约说的"。
 */
fun allowedLocalActions(requests: List<TutorLocalActionRequest>): Set<TutorLocalAction> =
    requests.filter { request ->
        val declaredNames = request.action.parameters
            .map { parameter -> parameter.parameterName }
            .toSet()
        request.parameters.keys.all { key -> key in declaredNames }
    }.map { request -> request.action }.toSet()

/**
 * 这一轮的工具痕迹里"在等学生确认"的调用 → 对应的待确认 kind。
 *
 * 为什么需要它：工具拼写（`NOTEBOOK_WRITE`）走的不是本地动作通道——它是工具环里的一次函数
 * 调用，而工具环里 ask 档的对象**不执行**（见 `RoomModelTaskRepository` 的权限分档），于是
 * 那一轮唯一留下的信号就是痕迹里这条 `awaiting_consent`。交互面据此挂出**同一张卡**
 * （[tutorLocalActionAdmission] 的按对象入口）。
 */
fun awaitingConsentPendingRequestKinds(
    trace: com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace?,
): Set<AgentPendingRequestKind> = trace?.entries.orEmpty()
    .filter { entry ->
        entry.errorKind == com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND
    }
    .mapNotNull { entry ->
        agentPendingRequestKind(TutorPermissionSubject.Tool(entry.tool))
    }
    .toSet()
