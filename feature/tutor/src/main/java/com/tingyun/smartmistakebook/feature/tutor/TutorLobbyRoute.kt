package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.export.MistakePaperDraft
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
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
    initialToolPrompt: String? = null,
    onBack: (() -> Unit)? = null,
    assistantText: (String, String?) -> String = { text, _ -> text },
    assistantAccessory: (@Composable (TutorMessage, String) -> Unit)? = null,
    attachedQuestionReader: TutorAttachedQuestionReader? = null,
    /**
     * 确认卡的落库端口（A4 / 插眼 5）。null = 这个入口不接确认卡：模型申请的本地动作仍然
     * 如实出现在正文里，只是没有可点的卡（与"静默丢弃"不同——丢弃是连话都不说）。
     */
    pendingRequests: AgentPendingRequestRepository? = null,
    /** 确认卡三条执行路径的落点（见 [tutorLocalActionLandings]）。 */
    localActionLandings: TutorLocalActionLandings = TutorLocalActionLandings(),
    /**
     * 导出 sheet 的"开始导出"落点（4B B3-3）：装配处（app）入队后台导出并导航到「导出成果」。
     * 默认什么都不做 = 这个入口不接导出（sheet 仍会打开，只是确认按钮没有落点——测试替身）。
     */
    onStartExport: (
        format: MistakeExportFormat,
        layout: MistakePdfLayout,
        entryIds: List<String>,
    ) -> Unit = { _, _, _ -> },
    /**
     * 6B-2：选题后读题面、建可编辑卷面。null = 这个入口不接编辑态（sheet 上如实说一句，
     * 仍可按原样导出）。读取是纯本地事实，不派发模型任务。
     */
    readExportPaperEdit:
        (suspend (entryIds: List<String>, layout: MistakePdfLayout) -> MistakePdfPaperEdit?)? =
        null,
    /**
     * 6B-3：自然语言改卷的模型端口（输入一句要求 + 当前卷面副本，返回白名单命令 JSON）。
     *
     * null = 这个入口不接模型改卷：编辑态不显示「让 AI 改卷面」，手动编辑与导出照旧。
     * 端口拿到的是卷面副本；真正的白名单解析/越界校验/落地在 `TutorPaperEditApplier`。
     */
    paperEditAssistant: (suspend (instruction: String, paper: MistakePdfPaperEdit) -> String)? =
        null,
    /** 6B-2：编辑态确认后的落点（装配处落盘草稿 + 入队后台重渲染）。 */
    onStartEditedExport: (MistakePaperDraft, MistakeExportFormat) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onPrepareDetailedAnswers: ((MistakeExportFormat, MistakePdfLayout, List<String>) -> Unit)? = null,
    onPrepareEditedDetailedAnswers: ((MistakePaperDraft, MistakeExportFormat) -> Unit)? = null,
) {
    // 这条交互面长在哪一栏（K1 的判别列）：会话行按它写，一张配置对象说了算——
    // ViewModel 建会话行时读它（下面的 factory），屏幕的配置也带它，两处不会漂。
    val surfaceArea = TutorConversationAreas.AGENT
    val paperEditCommandSource = paperEditAssistant
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
            readExportPaperEdit = readExportPaperEdit,
            attachedQuestionReader = attachedQuestionReader,
            paperEditAssistant = paperEditCommandSource?.let { source ->
                object : TutorPaperEditAssistant {
                    override suspend fun commandsFor(
                        paper: MistakePdfPaperEdit,
                        instruction: String,
                    ): String = source(instruction, paper)
                }
            },
        ),
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lobbyImageEnabled = imageIntake != null
    val hasLearningTools = LocalTutorLearningTools.current.isNotEmpty()
    val canPickQuestion = attachedQuestionReader != null

    // 每次进入这个页面都重读一次模型能力：学生可能刚从设置里换了模型/配了 Key，而能力是
    // 外部事实、不在库里。VM 只订阅它、不缓存它。
    LaunchedEffect(Unit) { viewModel.refreshProvider() }
    LaunchedEffect(viewModel, catalogEntries) { viewModel.restorePendingAttachedQuestion(catalogEntries) }
    var exitedLearningTool by rememberSaveable { mutableStateOf(false) }
    val initialToolDraft = remember(initialToolPrompt, exitedLearningTool) {
        if (exitedLearningTool) null else TutorLearningToolDraft.fromMessage(initialToolPrompt)
    }
    LaunchedEffect(viewModel, initialToolDraft) {
        initialToolDraft?.let { viewModel.seedLearningToolDraft(it.request) }
    }

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

    fun launchLobbyGallery() {
        lobbyGalleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // 进栏一律新对话（决策台账 D-Q6-4）、空会话不落库（K1b）：进入本页**不认领任何旧会话**，
    // 也不为"只是进来看一眼"建会话行——会话行由第一条消息自己保证。这些规则现在落在
    // [TutorConversationViewModel] 的初始状态里（默认空会话 id + 自己创建会话行）。

    val capabilities = remember(onCapture, canPickQuestion) {
        agentCapabilityDirectory(
            onCapture = onCapture,
            onPickMistake = viewModel::onOpenMistakePicker,
        ).filter { canPickQuestion || it.id != "pick-from-notebook" }
    }
    // 重新生成 = 覆盖：同一学生消息只展示最新助手回复（旧回复留库，不进界面）。
    val visibleMessages = remember(state.messages) { latestTutorLobbyMessages(state.messages) }
    val lastStudent = visibleMessages.lastOrNull { it.role == TutorMessageRole.STUDENT }
    val toolDraft = initialToolDraft ?: remember(lastStudent?.bodyMarkdown) {
        TutorLearningToolDraft.fromMessage(lastStudent?.bodyMarkdown)
    }
    val messageById = remember(visibleMessages) { visibleMessages.associateBy { it.messageId } }
    val readingQuestion = state.pendingAttachedQuestionEntryId != null && state.pendingAttachedQuestion == null
    val composerBlock = TutorComposerAvailability(
        providerReady = state.provider?.supports(ModelTaskKind.TUTOR_LOBBY) == true,
        providerLoadFailed = state.providerLoadFailed,
        // 残留任务是"发送被挡住"的第五种情况：给一句人话 + 「继续回复」出口，不许静默吞掉点击。
        stalledTurnReason = state.stalledTurnBlockReason,
    ).block() ?: if (readingQuestion) TutorComposerBlock("正在读取所选题目…") else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .testTag("tutor_lobby"),
    ) {
        TutorConversationScreen(
            config = TutorSurfaceConfig(
                area = surfaceArea,
                header = {
                    TutorPageHeader(
                        onOpenCapabilitySettings = onOpenCapabilitySettings,
                        onOpenHistory = onOpenHistory,
                        onNewConversation = {
                            if (viewModel.onStartNewConversation()) exitedLearningTool = true
                        },
                        onBack = onBack,
                    )
                },
                composerPlaceholder = "发消息",
                liveAnswerTestTag = "tutor_lobby_streaming_reply",
                // 空态 = 能力目录（A1）：进入即新会话，先告诉学生"它能干什么"。
                // 「试一句」（S5）点了就作为首条消息发出去，不是填进输入框。
                emptyState = {
                    if (initialToolDraft != null) {
                        Text(
                            text = initialToolDraft.label,
                            color = InkSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        TutorCapabilityDirectory(capabilities = capabilities, onExample = viewModel::onExampleMessage)
                    }
                },
            ),
            composer = {
                TutorSurfaceComposer(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    onSend = { viewModel.onSubmitDraft(toolDraft) },
                    sending = state.sending || state.resumingRequestId != null,
                    block = composerBlock,
                    onAction = { action ->
                        when (action.kind) {
                            TutorComposerActionKind.RETRY -> viewModel.onRetryFailedMessage()
                            TutorComposerActionKind.OPEN_SETTINGS -> onOpenCapabilitySettings()
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
                    placeholder = if (toolDraft != null) "补充你的需求" else "发消息",
                    onOpenAttachMenu = if (lobbyImageEnabled || hasLearningTools || canPickQuestion) {
                        viewModel::onOpenAttachMenu
                    } else {
                        null
                    },
                    attachmentPreview = if (state.pendingImages.isNotEmpty() || toolDraft != null ||
                        state.pendingAttachedQuestionEntryId != null
                    ) {
                        {
                            toolDraft?.let {
                                Text(
                                    "使用本机学习记录 · ${it.label}",
                                    color = InkSecondary,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (state.pendingImages.isNotEmpty()) PendingMessageImagesRow(
                                images = state.pendingImages, onRemove = viewModel::onRemovePendingImage, testTagPrefix = "lobby",
                            )
                            if (state.pendingAttachedQuestionEntryId != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = state.pendingAttachedQuestion?.title ?: "正在读取题目…",
                                        modifier = Modifier.weight(1f).testTag("lobby_attached_question"),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = InkSecondary,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = viewModel::onClearAttachedQuestion) { Text("移除题目") }
                                }
                            }
                        }
                    } else {
                        null
                    },
                    attachmentCount = state.pendingImages.size + if (state.pendingAttachedQuestion != null) 1 else 0,
                    tools = {
                        TutorInteractionModeSwitch(
                            mode = state.interactionMode,
                            onSwitch = viewModel::onSwitchInteractionMode,
                        )
                    },
                )
            },
            // 发送路径上的错误与输入框同一个常驻块：学生正在这里按，就在这里告诉他。
            attachments = if (state.error != null || state.attachReadFailed != null) {
                {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        state.attachReadFailed?.let { TutorSurfaceStartError(message = it) }
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
                            } else if (message.primaryAction?.actionType == ActionType.OPEN_SETTINGS) {
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
            // 同一条实时流（A3）。生成时输入栏右侧可随时停止。
            liveTurn = state.visibleLiveTurn?.let { turn ->
                turn.copy(answer = turn.answer?.let { assistantText(it, lastStudent?.bodyMarkdown) })
            },
            liveStatusText = state.stalledTask?.tutorLiveStatusText(),
            livePlaceholder = if (state.resumingRequestId != null) {
                TUTOR_LIVE_PLACEHOLDER
            } else {
                "正在发送…"
            },
            liveTurnStopAction = if (state.stalledTask != null) viewModel::onStopGenerating else null,
            emptyStateVisible = visibleMessages.isEmpty() && state.tasks.isEmpty(),
            autoScrollVersion = listOf(
                visibleMessages.size,
                visibleMessages.lastOrNull()?.messageId,
                visibleMessages.lastOrNull()?.status,
                state.tasks.lastOrNull()?.stateVersion,
                state.notebookLookupRequested,
                state.error,
                state.liveTurn,
            ),
            forceFollowToken = lastStudent?.messageId,
        ) {
            // 整条会话都列出来（B6）：长会话里前面的轮次不能被静默截掉——学生往上翻，
            // 讲过的每一轮都还在。
            items(
                items = visibleMessages,
                key = { it.messageId },
            ) { message ->
                val source = messageById[message.replyToMessageId]?.takeIf { it.role == TutorMessageRole.STUDENT }
                val visibleMessage = when (message.role) {
                    TutorMessageRole.ASSISTANT -> if (source != null) {
                        message.copy(bodyMarkdown = assistantText(message.bodyMarkdown, source.bodyMarkdown))
                    } else message
                    TutorMessageRole.STUDENT -> TutorLearningToolDraft.fromMessage(message.bodyMarkdown)
                        ?.let { message.copy(bodyMarkdown = it.request) } ?: message
                    else -> message
                }
                TutorSurfaceMessageItem(
                    message = visibleMessage,
                    imageIntake = imageIntake,
                    onOpenCapabilitySettings = onOpenCapabilitySettings,
                    resendTarget = message.resendTargetOrNull(visibleMessages),
                    onResend = viewModel::onResendMessage,
                    // 统一动作条（A2）：成功 / 已停止 / 失败三种状态共用同一个「重试」，目标都是
                    // 它回复的那条学生消息（= 重发同类请求，同一逻辑操作的下一次尝试）。
                    retryTarget = message.retryTargetOrNull(visibleMessages),
                    onRetry = viewModel::onResendMessage,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (source != null && message.role == TutorMessageRole.ASSISTANT &&
                    message.status == TutorMessageStatus.SUCCEEDED
                ) {
                    assistantAccessory?.invoke(message, source.bodyMarkdown)
                }
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
            // 确认卡（A4）：模型申请的本地动作落成一张**库里的行**，学生点了才执行。
            // 卡挂在会话流末尾（这一轮刚问完），裁决后行进终态、卡自己消失。
            state.pendingRequestCards.forEach { request ->
                item(key = "lobby-pending-${request.requestId}") {
                    TutorPendingRequestCard(
                        request = request,
                        modifier = Modifier.padding(top = 12.dp),
                        enabled = !state.hasActiveTask,
                        busy = request.requestId in state.decidingPendingRequestIds,
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
            onLaunchCamera = if (lobbyImageEnabled) ::launchLobbyCamera else null,
            onLaunchGallery = if (lobbyImageEnabled) ::launchLobbyGallery else null,
            testTagPrefix = "lobby",
            onPickFromLibrary = if (canPickQuestion) viewModel::onOpenMistakePicker else null,
        )
    }
    if (state.mistakePickerOpen) {
        TutorMistakePickerDialog(
            entries = catalogEntries,
            onPick = viewModel::onPickAttachedQuestion,
            onDismiss = viewModel::onDismissMistakePicker,
            testTagPrefix = "lobby",
        )
    }
    // 导出 sheet（B3-3）：START_EXPORT 卡被同意后打开；版式预填模型提议值、候选题来自本轮
    // 本地留痕，学生在其中改完勾完点「开始导出」——那时才真的入队后台导出。
    state.exportSheet?.let { sheet ->
        TutorExportSheet(
            onDetailedAnswersChange = if (onPrepareDetailedAnswers != null) viewModel::onExportDetailedAnswersChange else null,
            onPrepareDetailedAnswers = onPrepareDetailedAnswers?.let { prepare -> { format, layout, entryIds ->
                viewModel.onExportSheetDismiss(); prepare(format, layout, entryIds)
            } },
            onPrepareEditedDetailedAnswers = onPrepareEditedDetailedAnswers?.let { prepare -> { draft ->
                viewModel.onExportSheetDismiss(); prepare(draft, sheet.format)
            } },
            state = sheet,
            catalogEntries = catalogEntries,
            onLayoutChange = viewModel::onExportLayoutChange,
            onFormatChange = viewModel::onExportFormatChange,
            onToggleCandidate = viewModel::onExportCandidateToggle,
            onDismiss = viewModel::onExportSheetDismiss,
            onConfirm = { format, layout, entryIds ->
                viewModel.onExportSheetDismiss()
                onStartExport(format, layout, entryIds)
            },
            onEditRequest = viewModel::onExportEditRequest,
            onEditBack = viewModel::onExportEditBack,
            onEditMove = viewModel::onExportEditMove,
            onEditRemove = viewModel::onExportEditRemove,
            onEditRestore = viewModel::onExportEditRestore,
            onEditTitle = viewModel::onExportEditTitle,
            onEditBlockText = viewModel::onExportEditBlockText,
            onEditChoiceText = viewModel::onExportEditChoiceText,
            onEditNote = viewModel::onExportEditNote,
            // 6B-3：null 端口不渲染这一块（不广告未接线的能力）。
            modelEditAvailable = paperEditCommandSource != null,
            onEditInstructionChange = viewModel::onExportEditInstructionChange,
            onEditWithModel = viewModel::onExportEditWithModel,
            onConfirmEdited = { draft ->
                viewModel.onExportSheetDismiss()
                onStartEditedExport(draft, sheet.format)
            },
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
    ModelFailureCode.MODEL_NOT_CONFIGURED -> "请先在模型设置中完成配置，再发送。"
    ModelFailureCode.AUTHENTICATION_FAILED -> "API Key 可能已失效，检查后重新发送。"
    ModelFailureCode.PROVIDER_CAPABILITY_MISSING ->
        "当前模型不支持这项功能，请更换模型或重新测试。"
    ModelFailureCode.EGRESS_AUTHORIZATION_REQUIRED -> "请重新点击发送，确认本次发送内容。"
    ModelFailureCode.EGRESS_AUTHORIZATION_INVALID ->
        "本次发送确认已过期，请重新点击发送。"
    ModelFailureCode.NETWORK_UNAVAILABLE -> "网络暂时不可用，可以稍后再试。"
    ModelFailureCode.SERVICE_UNAVAILABLE -> "模型服务暂时不可用，可以稍后再试。"
    ModelFailureCode.TIMEOUT -> "模型回复超时，请重试。"
    ModelFailureCode.RATE_LIMITED -> "请求太频繁，稍等片刻再发。"
    ModelFailureCode.INVALID_RESPONSE -> "这次回复无法使用，请重试。"
    ModelFailureCode.INCOMPLETE_OUTPUT -> "回复未完成，请重试。"
    ModelFailureCode.PROVIDER_REJECTED_INPUT ->
        "模型服务拒绝了请求。可减少文字或缩小图片后重试。"
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
