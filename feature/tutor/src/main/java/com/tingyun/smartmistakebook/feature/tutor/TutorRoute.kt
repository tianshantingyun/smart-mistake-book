package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.ui.Divider
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkMuted
import com.tingyun.smartmistakebook.core.ui.Jade
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.TutorReplyMarkdown

/**
 * 智能体栏的**公开入口**（一条交互面的外层壳）：App 层只认这一个名字，内部一律走
 * [TutorLobbyRoute] → [TutorConversationScreen]（C1）。
 *
 * ## 删掉的第三路径（阶段 2c 死重删除）
 *
 * 这里曾经还有一条"讲题页面"分支：`practiceUnitId + teachingArtifact` 判定 → `TutorScreen`
 * （选择题 / 提交 / 揭示 / 追问）→ `TutorViewModel`（保存与提交状态）。它在生产里**到不了**：
 * `RoomBackedStudyExperienceRepository` 恒把 `tutorPracticeUnitId` 置 null（只有 debug fixture
 * `M1CuratedFixtureSource` 给值），于是 `tutorArtifact` 恒为 null，判定永远走大厅分支；
 * 而错题复习的 artifact 渲染由 `ReviewSessionScreen` 自带的卡流负责，与这里无关。
 * 一条"只有 fixture 能进"的路径留在生产代码里，只会让下一个人以为它还在被用。
 */
@Composable
fun TutorRoute(
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
    onOpenMistakeTutor: (MistakeRevisionKey) -> Unit = {},
    /** 确认卡的落库端口（A4）：null = 这个入口不接确认卡。 */
    pendingRequests: AgentPendingRequestRepository? = null,
    /** 确认卡执行路径 ① 的落点（拍照草稿 → 错题本条目）；null = 这个入口没有拍照草稿。 */
    captureRepository: CaptureWorkflowRepository? = null,
    /** 确认卡执行路径 ③ 的落点（聊天附图 → 录入链路）；null = 不接这一条。 */
    attachedImageIntake: TutorAttachedImageIntake? = null,
    /** 确认卡执行路径 ② 的落点（打开错题本；有具体题时给它的 id）。 */
    onOpenLibraryProblem: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TutorLobbyRoute(
        onCapture = onCapture,
        onOpenCapabilitySettings = onOpenCapabilitySettings,
        onOpenMistakeNotebook = onOpenMistakeNotebook,
        onOpenProfile = onOpenProfile,
        onOpenHistory = onOpenHistory,
        conversations = conversations,
        modelTasks = modelTasks,
        catalogEntries = catalogEntries,
        imageIntake = imageIntake,
        initialConversationId = initialConversationId,
        onOpenMistakeTutor = onOpenMistakeTutor,
        pendingRequests = pendingRequests,
        localActionLandings = tutorLocalActionLandings(
            openNotebook = onOpenLibraryProblem ?: { _ -> onOpenMistakeNotebook() },
            captureRepository = captureRepository,
            attachedImageIntake = attachedImageIntake,
        ),
        modifier = modifier,
    )
}

/**
 * 智能体页面的标题栏。**全页面只有这一个**：底部「智能体」、拍照讲解、错题详情「讲解这道题」、
 * 复习判题 / 判题复核、历史重开都渲染它，学生从任何入口进来看到的都是同一个页面的同一个头。
 *
 * 消灭的失败：此前"讲题会话页"（拍照会话、错题讲题）各自带一条"← 讲题"头、大厅带"讲题 + 历史 +
 * 设置"头，同一次讲题的两种状态看起来像两个不同的页面（产品侧原话：「这不是同一个页面」）；
 * 而且会话页没有历史与能力设置入口，学生要回到大厅才能找到它们。
 */
@Composable
internal fun TutorPageHeader(
    onOpenCapabilitySettings: () -> Unit,
    onOpenHistory: (() -> Unit)? = null,
    /** 由别的页面推进来时（错题详情、历史、拍照流程）给一个返回；大厅这种标签根页传 null。 */
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tutor_page_back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = Ink,
                    )
                }
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text = "讲题",
                modifier = Modifier.weight(1f),
                color = Ink,
                style = MaterialTheme.typography.headlineLarge,
            )
            if (onOpenHistory != null) {
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag("tutor_history_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = "讲题历史",
                        tint = Ink,
                    )
                }
            }
            IconButton(
                onClick = onOpenCapabilitySettings,
                modifier = Modifier.testTag("tutor_capability_settings_button"),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = "讲题能力设置", tint = Ink)
            }
        }
        PaperDivider(Modifier.padding(top = 8.dp, bottom = 14.dp))
    }
}

/**
 * 讲题助手的回复气泡。正文走开放的富渲染路径（完整 Markdown + 数学引擎，行内公式转可读数学、
 * 独立公式交给数学渲染），安全仍由 [TutorReplyMarkdown] 内部的 fail-closed 判定兜底：
 * 一旦出现 HTML/链接/远程图片就自动退回受限文本，不需要每个调用点各自把关。
 */
@Composable
internal fun TutorPrompt(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(JadeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = "智能讲题助手",
                modifier = Modifier.size(21.dp),
                tint = JadeActive,
            )
        }
        TutorReplyMarkdown(
            markdown = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .border(1.dp, Divider, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun TutorComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onCapture: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入你的推导、困惑或新问题",
    enabled: Boolean = true,
    /**
     * 非 null 时左侧按钮改为“+”并调用它打开添加菜单（Lobby 附图：相机/相册二选一）；
     * null 时保持相机直跳拍题。
     */
    onOpenAttachMenu: (() -> Unit)? = null,
    /** 输入框上方的附件预览行（微信式，可选）。 */
    attachmentPreview: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        attachmentPreview?.invoke()
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 82.dp)
                .testTag("tutor_draft_input"),
            placeholder = { Text(placeholder) },
            enabled = enabled,
            minLines = 2,
            maxLines = 3,
            leadingIcon = {
                val opensAttachMenu = onOpenAttachMenu != null
                IconButton(
                    onClick = onOpenAttachMenu ?: onCapture,
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tutor_attach_button"),
                ) {
                    Icon(
                        imageVector = if (opensAttachMenu) {
                            Icons.Outlined.Add
                        } else {
                            Icons.Outlined.PhotoCamera
                        },
                        contentDescription = if (opensAttachMenu) {
                            "添加图片：拍照或从相册选择"
                        } else {
                            "拍题或从相册选择题目图片"
                        },
                        tint = Jade,
                    )
                }
            },
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tutor_send_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "提交输入",
                        tint = if (value.isBlank()) InkMuted else Jade,
                    )
                }
            },
            shape = RoundedCornerShape(8.dp),
        )
    }
}
