package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionOutcomeRecord
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.Retryability
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorRequestedLocalCapability
import com.tingyun.smartmistakebook.core.model.appFailure
import com.tingyun.smartmistakebook.core.model.decodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.tutorScaffoldDirective
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 智能体栏这一轮的**派发**（新发送 / 失败重发 / 继续未完成 / 试一句）：三条路径此前各自实现过
 * 一遍，而两份实现已经漂移（正常发送路径从不读实时状态，只有"继续回复"看得到）。
 *
 * 为什么拆出来（而不是留在 [TutorConversationViewModel] 里）：它是那个类里最大的一块内聚逻辑
 * （发送 + 派发 + 回合状态），与"状态与输入"是两件事；拆开之后 ViewModel 只留状态的读写与
 * 订阅，这一块只通过回调读写那份状态——状态仍然只有一个持有者（C2），文件也回到规模门以内。
 *
 * @param currentState 读唯一那份状态（不另持副本）。
 * @param updateState 写唯一那份状态（跨进程保留也走它）。
 * @param reduceSend 发送状态机的一次归约（B5：没有界面自己的派遣预算）。
 */
internal class TutorLobbySendCommands(
    private val conversations: TutorConversationRepository,
    private val modelTasks: ModelTaskRepository,
    private val imageIntake: LobbyMessageImageIntake?,
    private val conversationArea: String,
    private val pendingRequestCoordinator: TutorPendingRequestCoordinator,
    private val roundStopCommands: TutorRoundStopCommands,
    private val scope: CoroutineScope,
    private val currentState: () -> TutorConversationUiState,
    private val updateState: ((TutorConversationUiState) -> TutorConversationUiState) -> Unit,
    private val reduceSend: (TutorSendAction) -> Unit,
) {

    /**
     * 「试一句」（S5）：空态能力目录里点了一句示例 → **它作为首条消息直接发出去**。
     *
     * 与"填进输入框等你再点发送"的区别就是要消灭的那次多余动作：空态里学生还不知道怎么开始，
     * 让他再点一次发送等于多一道门。草稿**不动**（那是他自己的东西）。
     */
    fun onExampleMessage(text: String) {
        val message = text.trim()
        if (message.isBlank()) return
        if (!turnCanBeStarted()) return
        startMessage(message = message, clearDraft = false)
    }

    fun onSubmitDraft() {
        val state = currentState()
        val message = state.draft.trim()
        if (message.isBlank() && state.pendingImages.isEmpty()) return
        if (!turnCanBeStarted()) return
        startMessage(message)
    }

    /**
     * 起新的一轮之前，先看这一轮收尾了没有。
     *
     * 返回 false = 现在不能起新的，**而原因已经在输入区上**（A5）：`hasActiveTask` 与
     * [TutorConversationUiState.stalledTurnBlockReason] 读的是同一个判据，所以输入区要么真的
     * 能发出去，要么正显示着"先点「继续回复」把它接上"；正在发送 / 正在续上时那两项本身
     * 就把发送键变成转圈（不是灰按钮），也没有静默。三条发送入口（新发送 / 失败重发 /
     * 原型重发 /「试一句」）共用这一个判据，不会有哪条路径忘了挡。
     *
     * 消灭的失败：残留态下发送键照常可点，按下去在 `hasActiveTask` 早退处被**静默吞掉**
     * ——学生既发不出去也不知道为什么（旧渲染条件还让「继续回复」出口同时不可达）。
     */
    private fun turnCanBeStarted(): Boolean = !currentState().hasActiveTask

    /**
     * 原样重发最近一次发送失败的那条消息（失败卡上的「重试」）。
     *
     * 重试沿用**同一个逻辑操作**：同一句原文、同一批附图、同一请求标识，因此不会追加第二条
     * 学生消息，也不会变成一次静默的空操作。
     */
    fun onRetryFailedMessage() {
        val message = currentState().lastFailedMessage ?: return
        startMessage(message)
    }

    /**
     * 「继续回复」：把停在半路的那一轮重新接上（列表里那个出口与输入区里的动作都走这里）。
     *
     * 消灭的失败：这个出口此前**不可达**——渲染条件与 [TutorConversationUiState.hasActiveTask]
     * 自相矛盾，学生既发不出新消息也恢复不了这一轮（见 [TutorConversationUiState.resumableStalledTask]）。
     */
    fun onResumeStalledTask() {
        val state = currentState()
        val task = state.stalledTask ?: return
        // 已经在动它了（正在发送 / 正在续上）：列表里的出口此刻本来就该被收起，走到这里说明
        // 渲染与点击之间状态刚变——重新渲染会把出口收掉，不是静默丢弃。
        if (state.sending || state.resumingRequestId != null) return
        val conversationId = state.conversationId
        if (conversationId.isBlank()) {
            // 认不到那一轮所在的会话行时接不上：如实说，而不是按了没反应。
            updateState { current -> current.copy(error = tutorSendRecoveryFailedError()) }
            return
        }
        updateState { current -> current.copy(resumingRequestId = task.request.requestId, error = null) }
        // 这一轮的学生消息早就落库了（这正是"继续"的前提）：把它重新认成当前回合，
        // 它迟到的终态才落得进状态；被取代的旧回合照旧被忽略。
        openRound(task.request.requestId, task.request.requestId)
        val replyTo = state.messages.lastOrNull { it.role == TutorMessageRole.STUDENT }
        val assistantOrdinal = nextTutorMessageOrdinals(
            state.conversation?.lastTurnOrdinal ?: 0,
        ).student
        scope.launch {
            try {
                dispatchTurn(
                    request = task.request,
                    conversationId = conversationId,
                    replyToMessageId = replyTo?.messageId,
                    assistantOrdinal = assistantOrdinal,
                    logicalOperationId = task.request.requestId,
                    messageId = task.request.requestId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { current -> current.copy(error = tutorSendRecoveryFailedError()) }
            } finally {
                updateState { current -> current.copy(resumingRequestId = null) }
            }
        }
    }

    /**
     * 发一条消息（新发送与失败重发共用）。
     *
     * 时间戳只取一次：同一个时刻既作"学生做出发送决定"的授权时刻，也作请求发生时刻——
     * `ModelEgressManifest.requireAuthorizes` 要求 `approvedAt` 不早于 `occurredAt`，两次读秒
     * 会让"是否落在同一毫秒"决定发送成败。
     */
    private fun startMessage(
        message: String,
        decidedAtEpochMillis: Long = System.currentTimeMillis(),
        /** false = 不动草稿（「试一句」用的是示例文案，不该把学生输入框里的东西清掉）。 */
        clearDraft: Boolean = true,
    ) {
        val state = currentState()
        val currentProvider = state.provider
        if (currentProvider == null && !state.providerLoadFailed) {
            // 首帧：模型能力还没读回来，别把它误报成"当前模型不能处理对话"。
            updateState { current ->
                current.copy(
                    error = appFailure(
                        code = AppFailureCode.TUTOR_PROVIDER_UNAVAILABLE,
                        title = "正在读取模型配置",
                        message = "模型配置还在读取中，稍等片刻再发送。",
                        dataPreserved = true,
                        retryability = Retryability.RETRYABLE,
                        primaryAction = ActionType.RETRY,
                    ),
                )
            }
            return
        }
        if (currentProvider == null || !currentProvider.supports(ModelTaskKind.TUTOR_LOBBY)) {
            updateState { current ->
                current.copy(
                    error = appFailure(
                        code = if (state.providerLoadFailed) {
                            AppFailureCode.PROVIDER_NOT_CONFIGURED
                        } else {
                            AppFailureCode.PROVIDER_CAPABILITY_MISMATCH
                        },
                        title = if (state.providerLoadFailed) {
                            "暂时读不到模型配置"
                        } else {
                            "当前模型还不能处理对话"
                        },
                        message = if (state.providerLoadFailed) {
                            "暂时读不到模型配置，请检查后再试。"
                        } else {
                            "当前模型还不能处理对话，请先完成模型配置和能力测试。"
                        },
                        dataPreserved = true,
                        primaryAction = ActionType.OPEN_SETTINGS,
                    ),
                )
            }
            return
        }
        if (!turnCanBeStarted()) return
        val sentImages = state.pendingImages
        if (sentImages.isNotEmpty() && currentProvider.supportsImageInput != true) {
            updateState { current ->
                current.copy(
                    error = appFailure(
                        code = AppFailureCode.PROVIDER_CAPABILITY_MISMATCH,
                        title = "当前模型不支持看图",
                        message = "去掉图片或更换支持图片的模型后再发送。",
                        dataPreserved = true,
                        primaryAction = ActionType.OPEN_SETTINGS,
                    ),
                )
            }
            return
        }
        val occurredAt = decidedAtEpochMillis
        val currentConversationId = state.conversationId
        updateState { current ->
            current.copy(
                sending = true,
                lastFailedMessage = message.ifBlank { current.lastFailedMessage },
            )
        }
        scope.launch {
            try {
                // 附图与拍照/讲题同口径：学生选择图片并点发送本身就是本次知情，不再弹确认卡。
                if (clearDraft) clearDraftLocallyAndDurably(occurredAt)
                updateState { current ->
                    current.copy(error = null, notebookLookupRequested = false)
                }
                // 资产库按内容寻址：同一张照片被选两次会拿到同一个 assetId，而请求契约要求
                // assetId 互不重复；这里按 assetId 去重（保留首次出现的位置）。
                val imageAssets = if (sentImages.isNotEmpty() && imageIntake != null) {
                    sentImages
                        .map { pending ->
                            imageIntake.registerImage(pending.localUri, System.currentTimeMillis())
                        }
                        .distinctBy { it.assetId }
                } else {
                    emptyList()
                }
                // 纯图消息给一句可读的兜底文本（消息体不能为空）。
                val effectiveMessage = message.ifBlank { TUTOR_CONVERSATION_IMAGE_ONLY_MESSAGE }
                // 活跃会话可能已被用户从历史页删除：不存在就开新会话，消息照常发出
                // ——而不是向外键冲突抛错、谎称已保留。
                val freshSnapshot = currentConversationId.takeIf { it.isNotBlank() }?.let { id ->
                    runCatching { conversations.observeConversation(id).first() }.getOrNull()
                }
                val conversationId: String
                val lastTurnOrdinal: Int
                val freshMessages: List<TutorMessage>
                if (freshSnapshot != null) {
                    conversationId = currentConversationId
                    lastTurnOrdinal = freshSnapshot.conversation.lastTurnOrdinal
                    freshMessages = freshSnapshot.messages
                } else {
                    val created = conversations.createConversation(
                        CreateTutorConversationCommand(
                            conversationId = "tutor-conv:${UUID.randomUUID()}",
                            // 会话区显式给（A1）：这个会话诞生于智能体栏，就不该出现在复习栏的历史里。
                            area = conversationArea,
                            // 模式：把学生此刻选的那一个带进新行（没选过就是会话区默认）。
                            interactionMode = state.interactionMode,
                            anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
                            anchorId = null,
                            anchorRevisionId = null,
                            // 标题不写（B7）：标题 = 首条消息截取 + 时间。
                            title = null,
                            createdAtEpochMillis = occurredAt,
                        ),
                    )
                    conversationId = created.conversationId
                    lastTurnOrdinal = created.lastTurnOrdinal
                    freshMessages = emptyList()
                    updateState { current -> current.copy(conversationId = conversationId) }
                }
                // 会话级单调 ordinal（K1c）：报号规则只有一处，学生与助手各占一位。
                val ordinals = nextTutorMessageOrdinals(lastTurnOrdinal)
                val studentOrdinal = ordinals.student
                val messageId = "tutor-message:${UUID.randomUUID()}"
                val logicalOperationId = "tutor-lobby-op:${UUID.randomUUID()}"
                val studentMessage = conversations.appendStudentMessage(
                    AppendTutorStudentMessageCommand(
                        conversationId = conversationId,
                        messageId = messageId,
                        ordinal = studentOrdinal,
                        bodyMarkdown = effectiveMessage,
                        logicalOperationId = logicalOperationId,
                        createdAtEpochMillis = occurredAt,
                        sourceImageAssetIds = imageAssets.map { it.assetId },
                    ),
                )
                openRound(logicalOperationId, messageId)
                updateState { current ->
                    current.copy(pendingImages = emptyList(), lastFailedMessage = null)
                }
                val context = com.tingyun.smartmistakebook.core.domain.TutorContextComposer.compose(
                    freshMessages,
                )
                val contextImages = freshMessages.toContextImages(imageIntake, studentOrdinal)
                // 回喂（A4 / D-K2e）：本地动作的裁决结果读**库里的行**（进程死亡后照样读得到），
                // 而不是内存里的一份副本——一份文本只有一个来源。
                val localActionOutcomes = pendingRequestCoordinator
                    .resolvedOutcomeRecords(conversationId)
                    .map(TutorLocalActionOutcomeRecord::toModelInputOutcome)
                val request = try {
                    buildTutorLobbyRequest(
                        provider = currentProvider,
                        conversationId = conversationId,
                        messageOrdinal = studentOrdinal,
                        studentMessage = effectiveMessage,
                        priorMessages = context.recent,
                        priorDigest = context.digest,
                        occurredAtEpochMillis = occurredAt,
                        approvedAtEpochMillis = decidedAtEpochMillis,
                        imageAssets = imageAssets,
                        contextImageAssets = contextImages,
                        localActionOutcomes = localActionOutcomes,
                        interactionMode = state.interactionMode,
                        // 引导模式下的起步档：本地纯策略算（卡点自动升档），正常模式恒为 null。
                        scaffoldLevel = tutorScaffoldDirective(
                            mode = state.interactionMode,
                            evidence = tutorLobbyStallEvidence(state.tasks),
                        ),
                    )
                } catch (_: IllegalArgumentException) {
                    // 契约违规不是网络问题：重试会逐字重放同一个非法请求，所以如实说是格式问题，
                    // 并且不给重试按钮（给了也必然再失败一次）。
                    failRound(
                        logicalOperationId = logicalOperationId,
                        messageId = messageId,
                        errorCode = AppFailureCode.VALIDATION_FAILED.name,
                        retryable = false,
                    )
                    updateState { current ->
                        current.copy(
                            error = appFailure(
                                code = AppFailureCode.VALIDATION_FAILED,
                                title = "这条消息暂时发不出去",
                                message = "这条消息的格式需要调整，改一下再发送。",
                                dataPreserved = true,
                            ),
                        )
                    }
                    return@launch
                }
                dispatchTurn(
                    request = request,
                    conversationId = conversationId,
                    replyToMessageId = studentMessage.messageId,
                    assistantOrdinal = ordinals.assistant,
                    logicalOperationId = logicalOperationId,
                    messageId = messageId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                android.util.Log.e("TutorConversation", "Failed to send message", e)
                updateState { current ->
                    current.copy(
                        error = appFailure(
                            code = AppFailureCode.NETWORK_UNAVAILABLE,
                            title = "这条消息已经保留",
                            message = "这条消息已经保留，但暂时没有发出去。",
                            dataPreserved = true,
                            retryability = Retryability.RETRYABLE,
                            primaryAction = ActionType.RETRY,
                        ),
                    )
                }
            } finally {
                updateState { current -> current.copy(sending = false) }
            }
        }
    }

    /** 失败卡上的「重新发送」：原样再发一遍最后一条失败的学生消息。 */
    fun onResendMessage(message: TutorMessage) {
        val state = currentState()
        if (!turnCanBeStarted()) return
        val currentProvider = state.provider ?: return
        if (!currentProvider.supports(ModelTaskKind.TUTOR_LOBBY)) return
        val conversationId = state.conversationId
        if (conversationId.isBlank() || message.conversationId != conversationId) return
        updateState { current ->
            current.copy(sending = true, error = null, notebookLookupRequested = false)
        }
        // 同一次用户动作取一次时钟：授权时刻与请求时刻必须同刻（见 startMessage 的说明）。
        val decidedAtEpochMillis = System.currentTimeMillis()
        scope.launch {
            try {
                val snapshot = runCatching {
                    conversations.observeConversation(conversationId).first()
                }.getOrNull() ?: return@launch
                // 只允许重发"最后一条学生消息"：界面按钮同样只在这时出现，这里再挡一次是因为
                // 渲染与点击之间学生可能已经发了新消息——那时重发就会把一个更晚的轮次，
                // 连同它的回复一起塞进这次请求的上下文里。
                if (snapshot.messages.any { candidate ->
                        candidate.role == TutorMessageRole.STUDENT &&
                            candidate.ordinal > message.ordinal
                    }
                ) {
                    return@launch
                }
                // 轮次号 = **这条学生消息自己的会话序号**（K1c 单数轴）：重发必须与原派发同号，
                // 否则会重建出第二个请求标识、把同一条消息派发两次。
                val messageOrdinal = message.ordinal
                val attempt = currentState().tasks.count { task ->
                    (task.request.input as? TutorLobbyInput)?.messageOrdinal == messageOrdinal
                }
                // 附图从资产库读回元数据：图被清理或被改动时按纯文字重发，
                // 而不是让整条消息发不出去。
                val imageAssets = message.sourceImageAssetIds
                    .mapNotNull { assetId -> imageIntake?.describeImage(assetId) }
                val context = com.tingyun.smartmistakebook.core.domain.TutorContextComposer.compose(
                    snapshot.messages,
                )
                val contextImages = snapshot.messages.toContextImages(
                    intake = imageIntake,
                    beforeOrdinal = message.ordinal,
                )
                val localActionOutcomes = pendingRequestCoordinator
                    .resolvedOutcomeRecords(conversationId)
                    .map(TutorLocalActionOutcomeRecord::toModelInputOutcome)
                val request = try {
                    buildTutorLobbyRequest(
                        provider = currentProvider,
                        conversationId = conversationId,
                        messageOrdinal = messageOrdinal,
                        studentMessage = message.bodyMarkdown,
                        priorMessages = context.recent,
                        priorDigest = context.digest,
                        occurredAtEpochMillis = decidedAtEpochMillis,
                        approvedAtEpochMillis = decidedAtEpochMillis,
                        attempt = attempt,
                        imageAssets = imageAssets,
                        contextImageAssets = contextImages,
                        localActionOutcomes = localActionOutcomes,
                        interactionMode = state.interactionMode,
                        // 引导模式下的起步档：本地纯策略算（卡点自动升档），正常模式恒为 null。
                        scaffoldLevel = tutorScaffoldDirective(
                            mode = state.interactionMode,
                            evidence = tutorLobbyStallEvidence(state.tasks),
                        ),
                    )
                } catch (_: IllegalArgumentException) {
                    updateState { current ->
                        current.copy(
                            error = appFailure(
                                code = AppFailureCode.VALIDATION_FAILED,
                                title = "这条消息暂时发不出去",
                                message = "这条消息的格式需要调整，改一下再发送。",
                                dataPreserved = true,
                            ),
                        )
                    }
                    return@launch
                }
                dispatchTurn(
                    request = request,
                    conversationId = conversationId,
                    replyToMessageId = message.messageId,
                    assistantOrdinal = snapshot.conversation.lastTurnOrdinal + 1,
                    logicalOperationId = message.logicalOperationId ?: request.requestId,
                    // 重发沿用这条学生消息自己的标识：同一轮的重放，不是新起一轮。
                    messageId = message.messageId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                android.util.Log.e("TutorConversation", "Failed to resend message", e)
                updateState { current ->
                    current.copy(
                        error = appFailure(
                            code = AppFailureCode.NETWORK_UNAVAILABLE,
                            title = "这条消息已经保留",
                            message = "这条消息已经保留，但暂时没有发出去。",
                            dataPreserved = true,
                            retryability = Retryability.RETRYABLE,
                            primaryAction = ActionType.RETRY,
                        ),
                    )
                }
            } finally {
                updateState { current -> current.copy(sending = false) }
            }
        }
    }

    /**
     * 派发一轮大厅对话并消费它的状态流：终态落一条助手消息。
     *
     * 新发送、失败重发、继续未完成任务三条入口共用这一处——它们此前各自实现过一次，而两份
     * 实现已经漂移（正常发送路径从不读实时状态，只有"继续回复"看得到）。实时文本由
     * [observeLiveTurn] 订阅同一条通道渲染。
     */
    private suspend fun dispatchTurn(
        request: ModelTaskRequest,
        conversationId: String,
        replyToMessageId: String?,
        assistantOrdinal: Int,
        logicalOperationId: String,
        messageId: String,
    ) {
        var terminalHandled = false
        roundStopCommands.beginRound(
            requestId = request.requestId,
            conversationId = conversationId,
            replyToMessageId = replyToMessageId,
            logicalOperationId = logicalOperationId,
        )
        try {
            modelTasks.execute(request).collect { task ->
                if (terminalHandled) return@collect
                val output = task.output as? TutorLobbyOutput
                when {
                    task.status == ModelTaskStatus.SUCCEEDED && output != null -> {
                        terminalHandled = true
                        // 模型申请"查错题本"时，界面必须给出可点的一步（此前这条申请被解析、
                        // 被校验、然后被丢掉）。
                        updateState { current ->
                            current.copy(
                                notebookLookupRequested = output.intentDecision.requestedLocalCapability ==
                                    TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
                            )
                        }
                        // B1：这一轮查阅了什么的痕迹（工具轮都在终态之前跑完，此刻读到的就是
                        // 完整的一份；没有工具调用就是 null）。消息行与挂卡判定共用这一份。
                        val traceJson = toolTraceJsonFor(modelTasks, request.requestId)
                        conversations.appendAssistantMessage(
                            AppendTutorAssistantMessageCommand(
                                conversationId = conversationId,
                                messageId = "tutor-message:${UUID.randomUUID()}",
                                ordinal = assistantOrdinal,
                                replyToMessageId = replyToMessageId,
                                bodyMarkdown = output.messageMarkdown,
                                thinkingMarkdown = output.thinkingMarkdown,
                                toolTraceJson = traceJson,
                                logicalOperationId = logicalOperationId,
                                status = TutorMessageStatus.SUCCEEDED,
                                createdAtEpochMillis = request.occurredAtEpochMillis,
                                completedAtEpochMillis = task.updatedAtEpochMillis,
                                errorCode = null,
                            ),
                        )
                        // 确认卡（A4 / D-K2e）：模型这一轮申请了本地动作（动作拼写或工具
                        // 拼写）时**先挂卡**，再收尾这一轮——挂起必须发生在 completeRound 之前
                        // （状态机不允许给已完成的那一轮挂一张没有主人的卡）。痕迹与消息行
                        // 同一次写入，所以这里 decode 的就是学生点开看到的那一份。
                        val lobbyInput = request.input as? TutorLobbyInput
                        val suspended = pendingRequestCoordinator.suspendRequestedActionsIfAny(
                            conversationId = conversationId,
                            requestedActions = lobbyInput?.requestedLocalActions.orEmpty() +
                                output.localActions,
                            attachedImageAssetIds = lobbyInput?.sourceImageAssetRefs
                                ?.map { ref -> ref.assetId }
                                .orEmpty(),
                            toolTrace = decodeTutorTurnToolTrace(traceJson),
                        )
                        if (!suspended) completeRound(logicalOperationId, messageId)
                    }

                task.status in TERMINAL_FAILURE_STATUSES -> {
                    terminalHandled = true
                        conversations.appendAssistantMessage(
                            AppendTutorAssistantMessageCommand(
                                conversationId = conversationId,
                                messageId = "tutor-message:${UUID.randomUUID()}",
                                ordinal = assistantOrdinal,
                                replyToMessageId = replyToMessageId,
                                bodyMarkdown = TUTOR_CONVERSATION_FAILED_REPLY_BODY,
                                logicalOperationId = logicalOperationId,
                                status = TutorMessageStatus.FAILED,
                                createdAtEpochMillis = request.occurredAtEpochMillis,
                                completedAtEpochMillis = task.updatedAtEpochMillis,
                                errorCode = task.failure?.code?.name,
                            ),
                        )
                        failRound(
                            logicalOperationId = logicalOperationId,
                            messageId = messageId,
                            errorCode = task.failure?.code?.name ?: "UNKNOWN",
                            retryable = task.status != ModelTaskStatus.PERMANENT_FAILURE,
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            // 学生按下停止时，取消就是从这条收集上抛出来的：这一轮由 [onStopGenerating] 收尾
            // （灰字「已停止」+ 可重试），这里静默退出，不再补一条"这条消息已经保留"的中断消息。
            if (!roundStopCommands.isStudentStopped(logicalOperationId)) throw cancelled
            return
        } finally {
            roundStopCommands.endRound(logicalOperationId)
        }
        if (roundStopCommands.isStudentStopped(logicalOperationId)) return
        if (!terminalHandled) {
            conversations.appendAssistantMessage(
                AppendTutorAssistantMessageCommand(
                    conversationId = conversationId,
                    messageId = "tutor-message:${UUID.randomUUID()}",
                    ordinal = assistantOrdinal,
                    replyToMessageId = replyToMessageId,
                    bodyMarkdown = TUTOR_CONVERSATION_INTERRUPTED_REPLY_BODY,
                    logicalOperationId = logicalOperationId,
                    status = TutorMessageStatus.FAILED,
                    createdAtEpochMillis = request.occurredAtEpochMillis,
                    completedAtEpochMillis = System.currentTimeMillis(),
                    errorCode = "UNKNOWN",
                ),
            )
            failRound(
                logicalOperationId = logicalOperationId,
                messageId = messageId,
                errorCode = "UNKNOWN",
                retryable = true,
            )
        }
    }

    // ---- 发送状态（B5） ----

    private fun openRound(logicalOperationId: String, messageId: String) {
        reduceSend(TutorSendAction.Reset)
        reduceSend(TutorSendAction.StudentMessagePersisted(logicalOperationId, messageId))
        reduceSend(TutorSendAction.ConsentGranted(logicalOperationId, messageId))
    }

    private fun completeRound(logicalOperationId: String, messageId: String) {
        reduceSend(TutorSendAction.DispatchSucceeded(logicalOperationId, messageId))
    }

    private fun failRound(
        logicalOperationId: String,
        messageId: String,
        errorCode: String,
        retryable: Boolean,
    ) {
        reduceSend(
            TutorSendAction.DispatchFailed(
                logicalOperationId = logicalOperationId,
                messageId = messageId,
                errorCode = errorCode,
                retryable = retryable,
            ),
        )
    }

    private fun reduceSend(action: TutorSendAction) {
        updateState { state ->
            state.copy(sendState = TutorTurnSendStateMachine.reduce(state.sendState, action))
        }
    }

    /** 直接落一个相位（挂起/裁决/重启用：那几处的相位不是"归约一条动作"就能表达的）。 */
    private fun applySendState(state: TutorSendState) {
        updateState { current -> current.copy(sendState = state) }
    }

    /**
     * 草稿**本地先清、再落库**（学生按下发送那一刻就该看到输入框空了，不等落库往返）。
     *
     * 读的是当前状态而不是组合期的值：这条路径由三条发送入口共用，任何一个入口拿着旧草稿都
     * 会把刚发出的话再写回输入框。
     */
    private fun clearDraftLocallyAndDurably(occurredAt: Long) {
        val conversationId = currentState().conversationId
        updateState { state -> state.copy(draft = "") }
        if (conversationId.isBlank()) return
        scope.launch {
            runCatching {
                conversations.clearDraft(
                    ClearTutorConversationDraftCommand(
                        conversationId = conversationId,
                        occurredAtEpochMillis = occurredAt,
                    ),
                )
            }
        }
    }
}
