package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.domain.DecidePendingRequestCommand
import com.tingyun.smartmistakebook.core.domain.SuspendTurnForConsentCommand
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntakeResult
import com.tingyun.smartmistakebook.core.domain.TutorConsentRequests
import com.tingyun.smartmistakebook.core.domain.TutorConsentSuspension
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionAdmission
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionContext
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionOutcomeRecord
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionTarget
import com.tingyun.smartmistakebook.core.domain.TutorPermissionSubject
import com.tingyun.smartmistakebook.core.domain.TutorRoundPermissionContext
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.allowedLocalActions
import com.tingyun.smartmistakebook.core.domain.awaitingConsentPendingRequestKinds
import com.tingyun.smartmistakebook.core.domain.toLocalActionOutcomeRecords
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionAdmission
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionContext
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionTarget
import com.tingyun.smartmistakebook.core.domain.tutorPermissionSubject
import com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace

/**
 * 确认卡的三条执行路径落在哪里（A4）：**装配处注入**，界面层只负责路由与呈现。
 *
 * 三条路径各自都有一个真实落点，且都是既有的：
 * - ① `saveCaptureDraft`：拍照草稿 → 错题本条目（`SaveTutorDraftToLibraryUseCase`）；
 * - ② `openLibraryProblem`：打开错题本（有具体题就到它的详情）——真动作是导航；
 * - ③ `intakeAttachedImages`：聊天里附的图片 → 录入链路（草稿 / 批量导入任务）。
 *
 * 默认实现"没接上"（返回 null / 不做动作）：接不上时裁决照样落终态，回喂的那句话如实说
 * "没能执行"——**不伪造一次成功**。
 *
 * public 的理由：装配处（app 模块）要把它交给 `TutorRoute`；路由规则本身仍在 core:domain，
 * 这里只是一组可注入的落点，不泄漏机制。
 */
data class TutorLocalActionLandings(
    val saveCaptureDraft: suspend (sessionId: String) -> String? = { null },
    val openLibraryProblem: (problemId: String?) -> Unit = {},
    val intakeAttachedImages: suspend (
        assetIds: List<String>,
        occurredAtEpochMillis: Long,
    ) -> TutorAttachedImageIntakeResult? = { _, _ -> null },
)

/**
 * 确认卡的落库、裁决与执行（A4 / 插眼 5 / D-K2e 本地动作通道）。
 *
 * 消灭的具体失败：模型申请的本地动作此前**没有执行者**——`AWAITING_CONSENT` 相位没有生产者
 * 也没有消费者，动作在界面上被静默丢弃（大厅）或只导航（错题入口），学生点了什么也没发生；
 * 进程死亡后更没有任何恢复路径。这里把四件事接起来：
 *
 * 1. **准入即挂行**（[suspendFor]：core:domain 的 [tutorLocalActionAdmission] 判定形状 /
 *    权限档 / 本轮目标，判定过了才落行）；行没落成就不推进相位（不悬死的前提）；
 * 2. **裁决落终态 + 执行真动作**：裁决先落库（终态 + 留痕），再按 [tutorLocalActionTarget]
 *    分派到真实路径；执行结果写回那一行的留痕（同一句话：学生看到的结果 / 模型读到的
 *    "本地做成了没有"）；
 * 3. **五 kind 各有落点**：存题（两种拼写）/ 打开某题走既有的三条真路径；导出与复习计划
 *    调整的流程尚未接线，落 [TutorLocalActionTarget.NOT_WIRED_YET]——**卡照挂、执行如实说
 *    "还不能自动做"**，不假装成功；
 * 4. **回喂**：`AgentPendingRequestRepository.readResolvedRequests` 读回已裁决的行——进程死亡
 *    之后照样读得到（行在库里，不在内存里）。
 */
internal class TutorPendingRequestCommands(
    private val conversationArea: String,
    private val requests: AgentPendingRequestRepository,
    private val landings: TutorLocalActionLandings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val consent = TutorConsentRequests(requests)

    /**
     * 把一条**已准入**的动作挂成一张落库的卡（行先落库，相位才推进）。
     *
     * 权限档是第二次问：`TutorConsentRequests.suspend` 自己也要判一次"这个对象是不是 ask 档
     * 且本轮放行"。两次判定的输入必须同源（否则一边放行一边拒绝，卡就会凭空消失），所以这里
     * 把**准入时的那个对象**原样交给它，上下文也只放行这一个对象。
     */
    suspend fun suspendFor(
        state: TutorSendState,
        conversationId: String,
        admission: TutorLocalActionAdmission,
        occurredAtEpochMillis: Long,
    ): TutorConsentSuspension? = consent.suspend(
        state = state,
        command = SuspendTurnForConsentCommand(
            subject = admission.decision.subject,
            conversationArea = conversationArea,
            conversationId = conversationId,
            payloadJson = admission.payloadJson,
            occurredAtEpochMillis = occurredAtEpochMillis,
            context = admission.permissionContext,
        ),
    )

    /**
     * 学生点卡：执行真实路径 → 裁决落终态（终态与留痕**一次写入**，不与"先裁决再补留痕"抢同一行）。
     *
     * 顺序说明：先执行、再落行。理由有两条：① `resolveRequest` 只接受一次裁决，留痕必须与终态
     * 同写，所以结果必须先知道；② 三条落点各自幂等（③ 的请求标识由资产 id 确定性派生、① 靠
     * `isSaved` 守卫、② 是导航），所以偶发的中断重放不会做出第二份东西。
     *
     * @return 裁决后的回合相位与回喂行；行不存在 / 落库未接上时返回 null。
     */
    suspend fun decide(
        state: TutorSendState,
        requestId: String,
        decision: AgentPendingRequestDecision,
    ): TutorPendingDecision? {
        val pending = requests.readPendingRequests(conversationArea)
            .firstOrNull { request -> request.requestId == requestId }
            ?: return null
        val detail = when (decision) {
            AgentPendingRequestDecision.ACCEPT -> execute(pending)
            AgentPendingRequestDecision.DECLINE -> declineDetail(pending.kind)
            AgentPendingRequestDecision.IGNORE -> "学生把这张卡先搁下了。"
        }
        val resolution = consent.decide(
            state = state,
            command = DecidePendingRequestCommand(
                requestId = requestId,
                decision = decision,
                resolutionNote = detail,
                occurredAtEpochMillis = clock(),
            ),
        ) ?: return null
        return TutorPendingDecision(state = resolution.state, detail = detail)
    }

    /** 进程死亡/离开后重建同一张卡：行自己带着回合身份，相位照它回到等确认。 */
    fun resumeAfterRestart(state: TutorSendState, pending: AgentPendingRequest): TutorSendState? =
        consent.resumeAfterRestart(state, pending)?.state

    /** 回喂通道：已裁决的行（最近的在前）→ 下一轮输入里的那一小段。 */
    suspend fun resolvedOutcomeRecords(conversationId: String): List<TutorLocalActionOutcomeRecord> {
        val resolved = runCatching {
            requests.readResolvedRequests(
                conversationArea = conversationArea,
                limit = AgentPendingRequestRepository.MAX_RESOLVED_PENDING_REQUESTS,
            )
        }.getOrNull().orEmpty()
        return resolved
            .filter { request -> request.conversationId == conversationId }
            .toLocalActionOutcomeRecords(MAX_FEEDBACK_OUTCOMES)
    }

    private suspend fun execute(request: AgentPendingRequest): String {
        val context = tutorLocalActionContext(request.payloadJson)
        return when (tutorLocalActionTarget(request.kind, context)) {
            TutorLocalActionTarget.SAVE_CAPTURE_DRAFT -> {
                val sessionId = context.captureSessionId
                    ?: return "没能执行：这一轮没有可保存的拍照草稿。"
                landings.saveCaptureDraft(sessionId)
                    ?: return "没能执行：这一轮的草稿已经不在本机了。"
            }

            TutorLocalActionTarget.INTAKE_ATTACHED_IMAGES -> {
                val result = runCatching {
                    landings.intakeAttachedImages(
                        context.attachedImageAssetIds,
                        clock(),
                    )
                }.getOrNull()
                when {
                    result == null -> "没能执行：录入暂时没有接上。"
                    result.landed -> result.detail
                    else -> "没能执行：${result.detail}"
                }
            }

            TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL -> {
                landings.openLibraryProblem(context.libraryProblemId)
                "已经在错题本里打开了。"
            }

            // 尚未接通的流程（导出排版属阶段 4B、复习计划调整属阶段 3B）：**如实说**，
            // 不留"也许已经做了"的模糊。学生的裁决本身已经落成终态留痕（这就是本次的产出）。
            TutorLocalActionTarget.NOT_WIRED_YET -> notWiredYetDetail(request.kind)

            null -> "这一步现在还不能自动做。"
        }
    }

    private companion object {
        const val MAX_FEEDBACK_OUTCOMES = 8
    }
}

/**
 * 尚未接通的流程给学生的原话（**逐条穷举**，不用 else 兜底）：一句"还不能自动做"必须说清
 * 哪件事没做，否则学生只能猜"是不是悄悄做了"。
 */
internal fun notWiredYetDetail(kind: AgentPendingRequestKind): String = when (kind) {
    AgentPendingRequestKind.START_EXPORT ->
        "这一步还不能自动做：导出流程还没接通，这次没有生成文件。"
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN ->
        "这一步还不能自动做：复习计划的调整还没接通，这次没有改动计划。"
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
    AgentPendingRequestKind.OPEN_PROBLEM,
    -> "这一步现在还不能自动做。"
}

/** 学生选择"先不"时给模型/留痕的那一句（逐 kind，不写"先不保存"去回答"要不要打开"）。 */
internal fun declineDetail(kind: AgentPendingRequestKind): String = when (kind) {
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
    -> "学生选择先不保存。"
    AgentPendingRequestKind.OPEN_PROBLEM -> "学生选择先不打开。"
    AgentPendingRequestKind.START_EXPORT -> "学生选择先不导出。"
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN -> "学生选择先不加进复习计划。"
}

/**
 * 一次裁决的结果：回合相位 + 给学生看的那句话（也是写进那一行留痕的同一句话）。
 *
 * 回喂的那一小段**不在这里**：它从库里的已裁决行读回（`resolvedOutcomeRecords`），
 * 进程死亡后照样读得到——同一份事实不留两个副本。
 */
internal data class TutorPendingDecision(
    val state: TutorSendState,
    val detail: String,
)

/**
 * 本地动作通道的**交互面接线**（A4 / D-K2e）：挂起 / 观察 / 重建 / 裁决四条流程。
 *
 * 拆出来的理由只有一个：交互面的 ViewModel 是 C2 定的"状态唯一持有者"，它已经装着发送、附件、
 * 实时流三件事；这几条流程只通过回调读写那份状态，不另持一份（换个人持有就变成第二个真相）。
 */
internal class TutorPendingRequestCoordinator(
    private val conversationArea: String,
    private val requests: AgentPendingRequestRepository?,
    private val commands: TutorPendingRequestCommands?,
    private val currentState: () -> TutorConversationUiState,
    private val updateState: ((TutorConversationUiState) -> TutorConversationUiState) -> Unit,
    private val applySendState: (TutorSendState) -> Unit,
    /** 这条会话锚着的拍照会话 id：有它，卡执行的是"把草稿存进错题本"（执行路径 ①）。 */
    private val anchoredCaptureSessionId: String?,
    /**
     * 这条会话锚着的那道**已在错题本里**的题（执行路径 ②）：错题讲题页给它。有它，模型申请
     * "存 / 打开这一轮这道题"才有一个本地目标（执行 = 打开错题本）；没有它这类申请挂不出卡。
     */
    private val anchoredLibraryProblemId: String? = null,
) {
    /**
     * 这一轮模型申请了什么本地动作（**两种拼写都算**）？有就挂一张**落库**的卡。
     *
     * 两种拼写：
     * - **动作拼写**：`output.localActions`（Route B 的 json_object 信封）与
     *   `input.requestedLocalActions`（原生 tool_calls 路由把动作并进了下一轮输入）；
     * - **工具拼写**：`NOTEBOOK_WRITE` 被授权矩阵放进 ask 档、工具环没有执行它——
     *   那一轮留下的信号是 [toolTrace] 里的 `awaiting_consent`。
     *
     * 两个入口各给各的事实（大厅给输入/输出里的动作与附图；讲题侧给那一轮痕迹带出的工具拼写），
     * 但挂的是同一张卡、走同一个执行器：差异只来自本轮上下文，不来自栏。
     *
     * 隐藏的判据只有一条：**本地有没有可执行目标**（拍照会话 / 附图 / 题）。没有就不挂卡——
     * 挂出来点了也无处落地，那正是"点了什么也没发生"的老毛病（core:domain 的准入函数负责判）。
     *
     * @return 是否挂起了这一回合（挂起成功时回合停在 AWAITING_CONSENT，收尾交给学生点卡）。
     */
    suspend fun suspendRequestedActionsIfAny(
        conversationId: String,
        requestedActions: List<TutorLocalActionRequest>,
        /** 本条学生消息附带的图片（规范资产 id）：存题的第二条落点靠它。 */
        attachedImageAssetIds: List<String>,
        toolTrace: TutorTurnToolTrace? = null,
    ): Boolean {
        val commands = commands ?: return false
        val roundContext = TutorLocalActionContext(
            captureSessionId = anchoredCaptureSessionId,
            // 这道题已经在错题本里（错题讲题页）：它既是"存这一轮"的落点（本地只有这一件
            // 可存的东西时打开它），也是"打开这一轮这道题"的目标。
            libraryProblemId = anchoredLibraryProblemId,
            attachedImageAssetIds = attachedImageAssetIds,
        )
        val requested = requestedActions.distinct()
        // 两处放行各有来源，都不在这里重算语义：
        // - **动作拼写**的放行 = 本地动作通道对 ID + 参数形状的核对（`allowedLocalActions`）；
        // - **工具拼写**的放行 = 痕迹里那条 `awaiting_consent` 本身就是**已准入**的证据：
        //   工具环是先过授权门、认出它是 ask 档才没有执行它，执行器把这件事记成了这条痕迹。
        //   所以放行集合必须把那些工具收进来——留成空集时 `admitted=false`，这张按对象入口
        //   挂的卡**恒挂不出来**（"讲题侧申请写入错题本 → 确认卡"那条路此前就是这样死的）。
        val permissionContext = TutorRoundPermissionContext(
            allowedTools = awaitingConsentTools(toolTrace),
            allowedActions = allowedLocalActions(requested),
        )
        val admission = requested
            .mapNotNull { request ->
                tutorLocalActionAdmission(
                    request = request,
                    context = roundContext,
                    permissionContext = permissionContext,
                )
            }
            .firstOrNull()
            ?: awaitingConsentPendingRequestKinds(toolTrace)
                .firstNotNullOfOrNull { kind ->
                    tutorLocalActionAdmission(
                        subject = tutorPermissionSubject(kind),
                        context = roundContext,
                        permissionContext = permissionContext,
                    )
                }
            ?: return false
        val suspension = commands.suspendFor(
            state = currentState().sendState,
            conversationId = conversationId,
            admission = admission,
            occurredAtEpochMillis = System.currentTimeMillis(),
        ) ?: return false
        updateState { state ->
            state.copy(
                pendingRequestCards = (state.pendingRequestCards + suspension.request)
                    .distinctBy { card -> card.requestId },
                pendingRequestDetail = null,
            )
        }
        // 相位由挂起走出来（AWAITING_CONSENT）：回合停在"等学生点卡"上，裁决后继续。
        applySendState(suspension.state)
        return true
    }

    /**
     * 这条会话未裁决的卡（行是权威：进程死亡后重建的是同一张卡）。
     *
     * 只看**本会话**的卡：栏里别的会话挂着的卡不该出现在这条会话流里（悬浮球那一层统一收，
     * 属于阶段 6）。
     */
    suspend fun observeCards() {
        val requests = requests ?: return
        requests.observePendingRequests(conversationArea).collect { all ->
            val conversationId = currentState().conversationId
            val mine = all.filter { request ->
                request.isPending() && request.belongsToConversation(conversationId)
            }
            updateState { state -> state.copy(pendingRequestCards = mine) }
        }
    }

    /**
     * 进程死亡/切栏回来后重建同一张卡（插眼 5 的硬要求：确认卡不得悬死）。
     *
     * 卡本身由 [observeCards] 从库里读出来渲染；这里补的是**回合相位**。
     */
    suspend fun resumeAfterRestart() {
        val commands = commands ?: return
        val conversationId = currentState().conversationId
        if (conversationId.isBlank()) return
        val pending = runCatching {
            requests?.readPendingRequests(conversationArea)
        }.getOrNull().orEmpty()
            .lastOrNull { request -> request.belongsToConversation(conversationId) }
            ?: return
        val resumed = commands.resumeAfterRestart(currentState().sendState, pending) ?: return
        applySendState(resumed)
    }

    /** 学生点卡：裁决落终态 → 执行真实路径 → 结果进回喂（下一轮输入带上）。 */
    suspend fun decide(
        request: AgentPendingRequest,
        decision: AgentPendingRequestDecision,
    ): Boolean {
        val commands = commands ?: return false
        val result = runCatching {
            commands.decide(
                state = currentState().sendState,
                requestId = request.requestId,
                decision = decision,
            )
        }.getOrNull() ?: return false
        applySendState(result.state)
        updateState { state -> state.copy(pendingRequestDetail = result.detail) }
        return true
    }

    /** 回喂（A4 / D-K2e）：已裁决的行读回下一轮要带的那一小段。 */
    suspend fun resolvedOutcomeRecords(conversationId: String): List<TutorLocalActionOutcomeRecord> =
        commands?.resolvedOutcomeRecords(conversationId).orEmpty()
}

/**
 * 工具痕迹里"在等学生确认"的那些工具（本地放行集合的来源）。
 *
 * 与 `core:domain` 的 [awaitingConsentPendingRequestKinds] 同一份依据（`awaiting_consent`
 * 那条痕迹），这里要的是**工具**本身而不是 kind——准入函数按对象判放行。
 */
internal fun awaitingConsentTools(trace: TutorTurnToolTrace?): Set<TutorToolName> =
    trace?.entries.orEmpty()
        .filter { entry -> entry.errorKind == TOOL_AWAITING_CONSENT_ERROR_KIND }
        .map { entry -> entry.tool }
        .toSet()

/** 未裁决的卡是不是属于这条会话（界面只渲染本会话的卡）。 */
internal fun AgentPendingRequest.belongsToConversation(conversationId: String): Boolean =
    conversationId.isNotBlank() && this.conversationId == conversationId

/** 只渲染未裁决的卡（终态的行留在库里当留痕，不再占界面）。 */
internal fun AgentPendingRequest.isPending(): Boolean = status == AgentPendingRequestStatus.PENDING

/** 回喂记录 → 模型输入形状（core:domain → core:model 的唯一转换点）。 */
internal fun TutorLocalActionOutcomeRecord.toModelInputOutcome() = TutorLobbyLocalActionOutcome(
    kind = kind,
    decision = decision,
    detail = detail,
)
