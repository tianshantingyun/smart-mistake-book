package com.tingyun.smartmistakebook.feature.tutor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tingyun.smartmistakebook.core.domain.MAX_TUTOR_MESSAGE_IMAGES
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import java.io.File
import java.util.UUID

/**
 * 讲题侧的**输入区**（A5）与它的三个弹层（加号菜单 / 拍照 / 选题）。
 *
 * 为什么单独一处：这一块只认两样东西——当前状态（[TutorConversationUiState]）与一串意图；
 * 状态本身在 [TutorConversationViewModel] 里。抽出来之后，面板那一层只剩"把各部分接起来"
 * 与时间线渲染，文件回到规模门以内；四个入口（智能体栏 + 两个讲题入口 + 会话结束帧）仍然
 * 共用同一个输入区组件 [TutorSurfaceComposer]，没有第二套。
 *
 * 消灭的具体失败：输入区此前在讲题侧是**可选参数**——模型未就绪、讲解没出结果等状态各自
 * 渲染一个不带输入框的帧（A5）。现在它与状态同源：能不能发、为什么不能发都由 `block` 说。
 */
@Composable
internal fun TutorSessionInputs(
    state: TutorConversationUiState,
    viewModel: TutorConversationViewModel,
    surface: TutorSurfaceConfig,
    /** 不能发送时的原因与出口；null = 现在可以发。 */
    composerBlock: TutorComposerBlock?,
    chatSending: Boolean,
    /** 这个入口有没有附图能力（没有就不出现加号菜单）。 */
    sessionImageEnabled: Boolean,
    /** 加号菜单「从错题库选择」的题面读取器；null = 这个入口不提供该菜单项。 */
    attachedQuestionReader: TutorAttachedQuestionReader?,
    catalogEntries: List<StudyCatalogEntry>,
    /** 发这条消息（登记附图 + 派发这一轮）。 */
    onSendMessage: () -> Unit,
    /** 讲解没有准备好时重新生成这一轮。 */
    onRetryTurn: () -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    val sessionContext = LocalContext.current
    // 拍照 / 相册的返回一律交给 ViewModel 收：目标 uri 与待发附图都在它那里（B3 跨进程保留）。
    val sessionCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        viewModel.onCameraResult(saved)
    }
    val sessionGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_TUTOR_MESSAGE_IMAGES),
    ) { selectedUris ->
        viewModel.onGalleryResult(selectedUris.map { uri -> uri.toString() })
    }

    fun launchSessionCamera() {
        val directory = File(sessionContext.cacheDir, "captured_images").apply {
            if (!isDirectory) mkdirs()
        }
        val file = File(directory, "session-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            sessionContext,
            "${sessionContext.packageName}.capture.fileprovider",
            file,
        )
        // 目标 uri 先交给 ViewModel 记着（跨进程保留）：拍完的照片要回填到发起拍照时约定的位置。
        viewModel.onCameraLaunching(uri.toString())
        sessionCameraLauncher.launch(uri)
    }

    TutorSurfaceComposer(
        value = state.draft,
        sending = chatSending,
        block = composerBlock,
        onValueChange = {
            viewModel.onDraftChange(it)
            viewModel.onSendError(null)
        },
        onSend = onSendMessage,
        onAction = { action ->
            when (action.kind) {
                // 「重试」：讲解没有准备好时重新生成这一轮（与大堂的"原样再发"共用一个出口
                // 形状，文案只有一套）。
                TutorComposerActionKind.RETRY -> onRetryTurn()
                TutorComposerActionKind.OPEN_SETTINGS -> onOpenModelSettings()
                // 讲题侧这一轮停在半路时，出口就是既有的"重新生成这一轮"（同一个动作，
                // 不新增按钮）：这条路不产生"残留任务"那种挡住发送的原因，所以这个按钮
                // 在讲题侧不会出现。
                TutorComposerActionKind.RESUME -> onRetryTurn()
            }
        },
        actionTestTags = { kind ->
            when (kind) {
                TutorComposerActionKind.RETRY -> "tutor_chat_composer_retry"
                TutorComposerActionKind.OPEN_SETTINGS -> "tutor_chat_composer_settings"
                TutorComposerActionKind.RESUME -> "tutor_chat_composer_resume"
            }
        },
        placeholder = surface.composerPlaceholder,
        reasonTestTag = "tutor_chat_block_reason",
        onOpenAttachMenu = if (sessionImageEnabled || attachedQuestionReader != null) {
            viewModel::onOpenAttachMenu
        } else {
            null
        },
        // 待发附图与智能体栏同一个位置（输入框正上方那一排）。
        attachmentPreview = if (state.pendingImages.isNotEmpty()) {
            {
                PendingMessageImagesRow(
                    images = state.pendingImages,
                    onRemove = viewModel::onRemovePendingImage,
                    testTagPrefix = "session",
                )
            }
        } else {
            null
        },
        attachmentCount = state.pendingImages.size,
    )
    if (state.attachMenuOpen) {
        MessageAttachmentDialog(
            onDismiss = viewModel::onDismissAttachMenu,
            onLaunchCamera = {
                viewModel.onDismissAttachMenu()
                launchSessionCamera()
            },
            onLaunchGallery = {
                viewModel.onDismissAttachMenu()
                sessionGalleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            testTagPrefix = "session",
            onPickFromLibrary = if (attachedQuestionReader != null) {
                viewModel::onOpenMistakePicker
            } else {
                null
            },
        )
    }
    if (state.mistakePickerOpen) {
        TutorMistakePickerDialog(
            entries = catalogEntries,
            // 读盘在 ViewModel 里（它的状态它自己填）：读失败如实说，成功才成为本轮附件题。
            onPick = viewModel::onPickAttachedQuestion,
            onDismiss = viewModel::onDismissMistakePicker,
            testTagPrefix = "session",
        )
    }
}

/**
 * 讲题侧的**附件区**（本轮附件题 + 读题失败 + 发送路径上的错误 + 确认卡）：都在输入框正上方。
 *
 * `attachReadFailed` 必须留在这个槽里：读失败时既没有图也没有附加题，只按前两者开门会让
 * "读不到这道题的题面"这句话永远渲染不出来——学生点了「从错题库选择」，界面上什么也没发生
 * （本条件由 `CapturedTutorSessionInstrumentedTest#aFailedLibraryReadTellsTheStudentInsteadOfAttachingNothing` 钉住）。
 *
 * 确认卡（A4）也挂在同一片区域：模型申请了本地动作时它出现在输入区上方，学生点了才执行
 * （拍照入口那张卡执行的就是"把这次拍照的草稿存进错题本"）。
 */
@Composable
internal fun TutorSessionAttachmentArea(
    state: TutorConversationUiState,
    viewModel: TutorConversationViewModel,
    surface: TutorSurfaceConfig,
    /** 这一轮正在派发：卡与移除按钮此时不再接受点击（避免与派发抢同一条消息）。 */
    chatSending: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.pendingRequestCards.forEach { request ->
            TutorPendingRequestCard(
                request = request,
                enabled = !chatSending,
                onDecide = { decision -> viewModel.onDecidePendingRequest(request, decision) },
            )
        }
        state.pendingRequestDetail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier.testTag("session_pending_detail"),
            )
        }
        state.pendingAttachedQuestion?.let { attached ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "本题：${attached.title}",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("session_attached_question"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 出口回调（`TutorSurfaceConfig.onOpenAttachedQuestionDetail`）：本轮附件的
                    // 那道题可以点开看。没有这个回调的入口不出现这个按钮（不留点了没反应的入口）。
                    surface.onOpenAttachedQuestionDetail?.let { openDetail ->
                        Text(
                            text = "打开",
                            style = MaterialTheme.typography.bodySmall,
                            color = JadeActive,
                            modifier = Modifier
                                .clickable(onClick = openDetail)
                                .padding(horizontal = 6.dp)
                                .testTag("session_attached_question_open"),
                        )
                    }
                    // B2：这是**按钮**，不是"能点的文字"。此前它是一段 `Text` + `clickable`：
                    // 读屏读到的是文字（听不出可以点、听不出点下去会发生什么），触摸目标也
                    // 只有文字本身那么大。视觉保持克制——与「停止」「重试」「复制」同一种
                    // 描边小按钮（`OutlineActionChip`）。
                    OutlineActionChip(
                        text = "移除",
                        onClick = viewModel::onClearAttachedQuestion,
                        contentDescription = "移除本题附件",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .testTag("session_attached_question_remove"),
                    )
                }
            }
        }
        state.attachReadFailed?.let { failure ->
            Text(
                text = failure,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorWarm,
                modifier = Modifier.testTag("session_attach_failed"),
            )
        }
        // 发送路径上的错误与智能体栏同一个位置（输入框上方），文案也只有一套。
        state.error?.let { message -> TutorSurfaceStartError(message.message) }
    }
}
