package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.MAX_TUTOR_MESSAGE_IMAGES
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.recoverableByResending
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.Paper
import java.io.File
import java.util.UUID

/**
 * 智能体栏（无题轮）的**薄壳**：只做三件入口自己的事——建 [TutorConversationViewModel]、
 * 接拍照/相册的返回、把空态换成能力目录；其余全部由一个交互面组件
 * [TutorConversationScreen] 渲染（C1）。
 *
 * 状态全在 [TutorConversationViewModel] 里（C2）：这一层只剩渲染与"把意图转成调用"。
 * 从前散在这里的 14 个裸 `remember`、会话/任务/实时三条订阅的接线、以及发送路径的三份复制，
 * 都已经搬进那个 ViewModel；这里因此不再持有任何跨重建会丢的对话状态。
 */
@Composable
internal fun TutorLobbyRoute(
    onCapture: () -> Unit,
    onOpenCapabilitySettings: () -> Unit,
    onOpenMistakeNotebook: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit,
    conversations: TutorConversationRepository,
    modelTasks: ModelTaskRepository,
    catalogEntries: List<StudyCatalogEntry>,
    imageIntake: LobbyMessageImageIntake? = null,
    initialConversationId: String? = null,
    /**
     * 加号（或空态里的「从错题本选择」）在错题库里选中一道题之后，把这道题**作为本轮附件**
     * 交给讲题页面：不换页面，只是这一轮有了要讲的那道题。
     */
    onOpenMistakeTutor: (MistakeRevisionKey) -> Unit = {},
    /**
     * 确认卡的落库端口（A4 / 插眼 5）。null = 这个入口不接确认卡：模型申请的本地动作仍然
     * 如实出现在正文里，只是没有可点的卡（与"静默丢弃"不同——丢弃是连话都不说）。
     */
    pendingRequests: AgentPendingRequestRepository? = null,
    /** 确认卡三条执行路径的落点（见 [tutorLocalActionLandings]）。 */
    localActionLandings: TutorLocalActionLandings = TutorLocalActionLandings(),
    modifier: Modifier = Modifier,
) {
    // 这条交互面长在哪一栏（K1 的判别列）：会话行按它写，一张配置对象说了算——
    // ViewModel 建会话行时读它（下面的 factory），屏幕的配置也带它，两处不会漂。
    val surfaceArea = TutorConversationAreas.AGENT
    val viewModel: TutorConversationViewModel = viewModel(
        factory = TutorConversationViewModelFactory(
            conversations = conversations,
            modelTasks = modelTasks,
            imageIntake = imageIntake,
            // 显式恢复的唯一入口：历史列表点回来。默认 null = 本轮新开（A1）。
            initialConversationId = initialConversationId,
            area = surfaceArea,
            pendingRequests = pendingRequests,
            localActionLandings = localActionLandings,
        ),
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lobbyImageEnabled = imageIntake != null

    // 每次进入这个页面都重读一次模型能力：学生可能刚从设置里换了模型/配了 Key，而能力是
    // 外部事实、不在库里。VM 只订阅它、不缓存它。
    LaunchedEffect(Unit) { viewModel.refreshProvider() }

    val lobbyCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        viewModel.onCameraResult(saved)
    }
    val lobbyGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_TUTOR_MESSAGE_IMAGES),
    ) { selected ->
        viewModel.onGalleryResult(selected.map { it.toString() })
    }

    fun launchLobbyCamera() {
        val directory = File(context.cacheDir, "captured_images").apply {
            if (!isDirectory) mkdirs()
        }
        val file = File(directory, "lobby-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.capture.fileprovider",
            file,
        )
        // 目标 uri 先交给 VM 记着（跨进程保留）：拍完的照片要回填到发起拍照时约定的位置。
        viewModel.onCameraLaunching(uri.toString())
        lobbyCameraLauncher.launch(uri)
    }

    // 进栏一律新对话（决策台账 D-Q6-4）、空会话不落库（K1b）：进入本页**不认领任何旧会话**，
    // 也不为"只是进来看一眼"建会话行——会话行由第一条消息自己保证。这些规则现在落在
    // [TutorConversationViewModel] 的初始状态里（默认空会话 id + 自己创建会话行）。

    val capabilities = remember(onCapture, onOpenMistakeNotebook) {
        agentCapabilityDirectory(
            onCapture = onCapture,
            onPickMistake = viewModel::onOpenMistakePicker,
            onOpenMistakeNotebook = onOpenMistakeNotebook,
        )
    }
    val composerBlock = TutorComposerAvailability(
        providerReady = state.provider?.supports(ModelTaskKind.TUTOR_LOBBY) == true,
        providerLoadFailed = state.providerLoadFailed,
        // 残留任务是"发送被挡住"的第五种情况：给一句人话 + 「继续回复」出口，不许静默吞掉点击。
        stalledTurnReason = state.stalledTurnBlockReason,
    ).block()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .imePadding()
            .testTag("tutor_lobby"),
    ) {
        TutorConversationScreen(
            config = TutorSurfaceConfig(
                area = surfaceArea,
                header = {
                    TutorPageHeader(
                        onOpenCapabilitySettings = onOpenCapabilitySettings,
                        onOpenHistory = onOpenHistory,
                    )
                    // 交互模式（D-Q9）：这条会话现在按哪种形态答复，以及换过去的那一下。
                    TutorInteractionModeSwitch(
                        mode = state.interactionMode,
                        onSwitch = viewModel::onSwitchInteractionMode,
                    )
                },
                composerPlaceholder = "输入题目、困惑，或说你现在想做什么",
                liveAnswerTestTag = "tutor_lobby_streaming_reply",
                // 空态 = 能力目录（A1）：进入即新会话，先告诉学生"它能干什么"。
                // 「试一句」（S5）点了就作为首条消息发出去，不是填进输入框。
                emptyState = {
                    TutorCapabilityDirectory(
                        capabilities = capabilities,
                        onExample = viewModel::onExampleMessage,
                    )
                },
            ),
            composer = {
                TutorSurfaceComposer(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    onSend = viewModel::onSubmitDraft,
                    sending = state.sending || state.resumingRequestId != null,
                    block = composerBlock,
                    onAction = { action ->
                        when (action.kind) {
                            TutorComposerActionKind.RETRY -> viewModel.onRetryFailedMessage()
                            TutorComposerActionKind.OPEN_SETTINGS -> onOpenCapabilitySettings
                            TutorComposerActionKind.RESUME -> viewModel.onResumeStalledTask()
                        }
                    },
                    actionTestTags = { kind ->
                        when (kind) {
                            TutorComposerActionKind.RETRY -> "tutor_lobby_retry_send"
                            TutorComposerActionKind.OPEN_SETTINGS -> "tutor_lobby_open_model_settings"
                            TutorComposerActionKind.RESUME -> "tutor_lobby_resume_turn"
                        }
                    },
                    placeholder = "输入题目、困惑，或说你现在想做什么",
                    onOpenAttachMenu = if (lobbyImageEnabled) {
                        viewModel::onOpenAttachMenu
                    } else {
                        null
                    },
                    attachmentPreview = if (state.pendingImages.isNotEmpty()) {
                        {
                            PendingMessageImagesRow(
                                images = state.pendingImages,
                                onRemove = viewModel::onRemovePendingImage,
                                testTagPrefix = "lobby",
                            )
                        }
                    } else {
                        null
                    },
                    attachmentCount = state.pendingImages.size,
                )
            },
            // 发送路径上的错误与输入框同一个常驻块：学生正在这里按，就在这里告诉他。
            attachments = if (state.error != null) {
                {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        state.error?.let { message ->
                            TutorSurfaceStartError(
                                message = message.message,
                                modifier = Modifier.padding(bottom = 6.dp),
                                testTag = "tutor_lobby_send_error",
                            )
                            if (message.primaryAction?.actionType == ActionType.RETRY) {
                                OutlineActionChip(
                                    text = TUTOR_SURFACE_RETRY_LABEL,
                                    onClick = viewModel::onRetryFailedMessage,
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .testTag("tutor_lobby_retry_send"),
                                )
                            } else {
                                OutlineActionChip(
                                    text = TUTOR_SURFACE_OPEN_SETTINGS_LABEL,
                                    onClick = onOpenCapabilitySettings,
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .testTag("tutor_lobby_open_model_settings"),
                                )
                            }
                        }
                    }
                }
            } else {
                null
            },
            modifier = Modifier
                .weight(1f)
                .testTag("tutor_screen"),
            // 在途状态（思考卡 / 逐 token 回答 / 工具进度）由屏幕组件渲染，与讲题同一套、
            // 同一条实时流（A3）。「停止」也长在这一块里（A2）：这一轮还没有消息行可挂动作条。
            liveTurn = state.visibleLiveTurn,
            liveStatusText = state.stalledTask?.tutorLiveStatusText(),
            livePlaceholder = if (state.resumingRequestId != null) {
                TUTOR_LIVE_PLACEHOLDER
            } else {
                "正在发送…"
            },
            liveTurnStopAction = viewModel::onStopGenerating,
            emptyStateVisible = state.messages.isEmpty() && state.tasks.isEmpty(),
            autoScrollVersion = listOf(
                state.messages.size,
                state.messages.lastOrNull()?.messageId,
                state.messages.lastOrNull()?.status,
                state.tasks.lastOrNull()?.stateVersion,
                state.notebookLookupRequested,
                state.error,
                state.liveTurn,
            ),
        ) {
            // 整条会话都列出来（B6）：长会话里前面的轮次不能被静默截掉——学生往上翻，
            // 讲过的每一轮都还在。
            items(
                items = state.messages,
                key = { it.messageId },
            ) { message ->
                TutorSurfaceMessageItem(
                    message = message,
                    imageIntake = imageIntake,
                    onOpenCapabilitySettings = onOpenCapabilitySettings,
                    resendTarget = message.resendTargetOrNull(state.messages),
                    onResend = viewModel::onResendMessage,
                    // 统一动作条（A2）：成功 / 已停止 / 失败三种状态共用同一个「重试」，目标都是
                    // 它回复的那条学生消息（= 重发同类请求，同一逻辑操作的下一次尝试）。
                    retryTarget = message.retryTargetOrNull(state.messages),
                    onRetry = viewModel::onResendMessage,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (state.resumeStalledTurnVisible) {
                item(key = "lobby-stalled") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "上一条回复没有完成",
                            color = InkSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        OutlineActionChip(
                            text = TUTOR_RESUME_TURN_LABEL,
                            onClick = viewModel::onResumeStalledTask,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("tutor_lobby_resume"),
                        )
                    }
                }
            }
            if (state.notebookLookupRequested && !state.hasActiveTask) {
                item(key = "lobby-notebook-lookup") {
                    OutlineActionChip(
                        text = "打开错题本",
                        onClick = onOpenMistakeNotebook,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("tutor_lobby_open_notebook"),
                    )
                }
            }
            // 确认卡（A4）：模型申请的本地动作落成一张**库里的行**，学生点了才执行。
            // 卡挂在会话流末尾（这一轮刚问完），裁决后行进终态、卡自己消失。
            state.pendingRequestCards.forEach { request ->
                item(key = "lobby-pending-${request.requestId}") {
                    TutorPendingRequestCard(
                        request = request,
                        modifier = Modifier.padding(top = 12.dp),
                        enabled = !state.hasActiveTask,
                        onDecide = { decision -> viewModel.onDecidePendingRequest(request, decision) },
                    )
                }
            }
            state.pendingRequestDetail?.let { detail ->
                item(key = "lobby-pending-detail") {
                    Text(
                        text = detail,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("tutor_lobby_pending_detail"),
                        color = InkSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (state.attachMenuOpen) {
        MessageAttachmentDialog(
            onDismiss = viewModel::onDismissAttachMenu,
            onLaunchCamera = {
                viewModel.onDismissAttachMenu()
                launchLobbyCamera()
            },
            onLaunchGallery = {
                viewModel.onDismissAttachMenu()
                lobbyGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            testTagPrefix = "lobby",
            onPickFromLibrary = if (catalogEntries.isNotEmpty()) {
                viewModel::onOpenMistakePicker
            } else {
                null
            },
        )
    }
    if (state.mistakePickerOpen) {
        TutorMistakePickerDialog(
            entries = catalogEntries,
            onPick = { entry ->
                viewModel.onDismissMistakePicker()
                onOpenMistakeTutor(entry.toMistakeRevisionKey())
            },
            onDismiss = viewModel::onDismissMistakePicker,
            testTagPrefix = "lobby",
        )
    }
}

/**
 * Lobby 回复失败的补充说明（纯函数，便于单测）：把内部失败码翻译成学生能行动的一句话。
 *
 * 逐条穷举而不是 `else -> null` 兜底：真实发生过的那次失败（`EGRESS_AUTHORIZATION_INVALID`）
 * 此前正好落在兜底分支里，于是失败卡只有一句"没有准备好"，学生既不知道原因也看不到出口。
 * 穷举还让以后新增的失败码**必须**在这里表态，而不是静默退回通用文案。
 */
internal fun lobbyFailureReasonText(code: ModelFailureCode?): String? = when (code) {
    null -> null
    ModelFailureCode.MODEL_NOT_CONFIGURED -> "当前还没有可用的模型配置，去设置里配好再发。"
    ModelFailureCode.AUTHENTICATION_FAILED -> "API Key 可能已失效，检查后重新发送。"
    ModelFailureCode.PROVIDER_CAPABILITY_MISSING ->
        "当前模型不支持这项对话能力，换一个模型或重新做一次能力测试。"
    ModelFailureCode.EGRESS_AUTHORIZATION_REQUIRED -> "这次发送还没有得到授权，重新发送即可。"
    ModelFailureCode.EGRESS_AUTHORIZATION_INVALID ->
        "这次发送的授权已经失效（例如隔了很久才重试），重新发送会重新授权。"
    ModelFailureCode.NETWORK_UNAVAILABLE -> "网络暂时不可用，可以稍后再试。"
    ModelFailureCode.SERVICE_UNAVAILABLE -> "模型服务暂时不可用，可以稍后再试。"
    ModelFailureCode.TIMEOUT -> "模型响应超时，可以再发一次。"
    ModelFailureCode.RATE_LIMITED -> "请求太频繁，稍等片刻再发。"
    ModelFailureCode.INVALID_RESPONSE -> "模型这次返回的内容无法使用，可以再发一次。"
    ModelFailureCode.PROVIDER_REJECTED_INPUT ->
        "上游拒绝了这次请求；带图时通常是图太多或太大，去掉一张或换小一点的图再发。"
    ModelFailureCode.UNKNOWN -> null
}

/**
 * 这条失败的回复还能不能"原样再发一次"；能则返回要重发的那条学生消息。
 *
 * 只在三个条件同时成立时给按钮：失败码属于重发有意义的那些码、这条失败仍是会话最后一条
 * 消息（更早的失败重发会让对话顺序错乱）、以及能找到它回复的那条学生消息（重发要带上
 * 原消息原文与它的附图）。消灭的失败：失败后界面上没有任何出口，学生只能把话重打一遍
 * ——实测记录里就是这么发生的：失败之后手动补发了"3"和"第三题"两条新消息。
 */
internal fun TutorMessage.resendTargetOrNull(
    conversationMessages: List<TutorMessage>,
): TutorMessage? {
    if (status != TutorMessageStatus.FAILED) return null
    if (conversationMessages.lastOrNull()?.messageId != messageId) return null
    val code = errorCode?.let { raw ->
        runCatching { ModelFailureCode.valueOf(raw) }.getOrNull()
    }
    if (code?.recoverableByResending() != true) return null
    val repliedTo = replyToMessageId ?: return null
    return conversationMessages.firstOrNull { candidate ->
        candidate.messageId == repliedTo && candidate.role == TutorMessageRole.STUDENT
    }
}
