@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.ui.ErrorText
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.tingyun.smartmistakebook.core.ui.ClaudeFilterChip as FilterChip
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.CaptureSourcePage
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.ClaudeButton
import com.tingyun.smartmistakebook.core.ui.ClaudeCard
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

@Composable
internal fun WorkspaceSaveErrorCard(
    message: String,
    saving: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ErrorText.copy(alpha = .25f), RoundedCornerShape(20.dp))
            .testTag("capture_workspace_save_error"),
    ) {
        Text(message, color = ErrorText, style = MaterialTheme.typography.bodySmall)
        OutlineActionChip(
            text = if (saving) "正在重试…" else "重新保存",
            onClick = onRetry,
            enabled = !saving,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("capture_workspace_save_retry"),
        )
    }
}

@Composable
internal fun ReplacementStatusCard(
    isReplacing: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onKeepCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capture_replacement_status"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isReplacing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = JadeActive,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = ErrorWarm,
                )
            }
            Text(
                text = if (isReplacing) "正在安全替换题图" else error.orEmpty(),
                modifier = Modifier.padding(start = 8.dp),
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!isReplacing && error != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineActionChip(
                    text = "重试替换",
                    onClick = onRetry,
                    modifier = Modifier.testTag("capture_replacement_retry"),
                )
                OutlineActionChip(
                    text = "保留原题",
                    onClick = onKeepCurrent,
                    modifier = Modifier.testTag("capture_replacement_keep"),
                )
            }
        }
    }
}

@Composable
internal fun CaptureTopBar(title: String, onBack: () -> Unit) {
    com.tingyun.smartmistakebook.core.ui.ClaudePageHeader(title, onBack = onBack,
        backModifier = Modifier.testTag("capture_back_button"))
}

@Composable
internal fun CaptureSourcePageBar(
    pages: List<CaptureSourcePage>,
    selectedPageIndex: Int,
    enabled: Boolean,
    onSelectPage: (Int) -> Unit,
    onAddByCamera: () -> Unit,
    onAddFromPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "本题原图 · ${pages.size} 页",
            color = Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                FilterChip(
                    selected = page.pageIndex == selectedPageIndex,
                    onClick = { onSelectPage(page.pageIndex) },
                    label = { Text("第 ${page.pageIndex + 1} 页") },
                    modifier = Modifier.testTag("capture_source_page_${page.pageIndex}"),
                )
            }
            IconButton(
                onClick = onAddByCamera,
                enabled = enabled,
                modifier = Modifier.testTag("capture_add_page_camera"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "拍照补充本题下一页",
                    tint = JadeActive,
                )
            }
            IconButton(
                onClick = onAddFromPhotos,
                enabled = enabled,
                modifier = Modifier.testTag("capture_add_page_photos"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "从照片补充本题下一页",
                    tint = JadeActive,
                )
            }
        }
        Text(
            text = "跨页题按顺序补拍；每一页原图都会保留。",
            modifier = Modifier.padding(top = 4.dp),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 录入方式选择（L6）：拍照 / 相册 / 整卷/PDF。
 *
 * 消灭的失败：改前错题本栏是两个并列入口（"拍照或上传错题"与"批量导入试卷照片"），看起来
 * 是两件事、两套叫法，实际都是同一件事——录入（用户裁定原话："它不管是批量目录还是单个体
 * 目录都一样，都是录入"）。分流只在这里做一次：拍照/相册用于单道题（可补拍多页），
 * 整卷/PDF 进入既有的整卷管线。
 *
 * 方式名如实取"整卷/PDF"而不是"文件与目录"：全仓没有目录选择器（无 `OpenDocumentTree`），
 * 既有整卷管线只接受多张照片（`PickMultipleVisualMedia`）与单份 PDF（`OpenDocument`）——
 * 用"目录"命名会承诺一个点了没有的能力（2026-10-03 独立复核 P1）。
 *
 * @param onOpenFiles 整卷/PDF 的落点；null = 这个来源不提供这种方式（能力所限的原因写在
 *   调用点，不允许放一个点了没反应的入口）。
 */
@Composable
internal fun CaptureEntryModeChooser(
    onTakePicture: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenFiles: (() -> Unit)? = null,
    /** A8：手动建题入口。null = 这个来源不提供（讲题来源需要原题照片）。 */
    onStartManual: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capture_mode_chooser"),
    ) {
        CaptureModeOption(
            text = "拍照",
            supportingText = "拍摄一道完整题目，可继续补拍下一页",
            onClick = onTakePicture,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("capture_take_picture_button"),
            icon = Icons.Outlined.PhotoCamera,
            primary = true,
            contentDescription = "使用系统相机拍摄题目并整理",
        )
        CaptureModeOption(
            text = "相册",
            supportingText = "从已保存的照片中选择题图",
            onClick = onPickPhoto,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .testTag("capture_pick_photo_button"),
            icon = Icons.Outlined.PhotoLibrary,
            iconSurface = SmartColors.LearningSurface,
            iconTint = SmartColors.LearningAccent,
            contentDescription = "使用系统照片选择器选择题目图片并整理",
        )
        if (onOpenFiles != null) {
            CaptureModeOption(
                text = "整卷/PDF",
                supportingText = "导入整卷照片或一份 PDF，按页整理",
                onClick = onOpenFiles,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .testTag("capture_mode_files"),
                icon = Icons.Outlined.FolderOpen,
                iconSurface = SmartColors.ReadingSurface,
                iconTint = SmartColors.ReadingAccent,
                contentDescription = "选择整卷的多张照片或一份 PDF 并逐页整理",
            )
        }
        if (onStartManual != null) {
            CaptureModeOption(
                text = "手动建题",
                supportingText = "直接输入题面，确认后存入错题本",
                onClick = onStartManual,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .testTag("capture_mode_manual"),
                icon = Icons.Outlined.EditNote,
                contentDescription = "没有照片也能建题：直接输入题面并存入错题本",
            )
        }
    }
}

@Composable
private fun CaptureModeOption(
    text: String,
    supportingText: String,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    iconSurface: Color = JadeSoft,
    iconTint: Color = JadeActive,
) {
    val foreground = if (primary) SmartColors.OnJade else Ink
    @Composable fun Content() {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = if (primary) SmartColors.OnJade.copy(alpha = .12f) else iconSurface) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = if (primary) foreground else iconTint)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text, color = foreground, style = MaterialTheme.typography.titleMedium)
                Text(supportingText, color = if (primary) SmartColors.OnJade else InkSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = foreground)
        }
    }
    val target = modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }
    if (primary) {
        ClaudeButton(onClick, target, shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SmartColors.Jade, contentColor = SmartColors.OnJade),
            contentPadding = PaddingValues(16.dp)) { Content() }
    } else {
        ClaudeCard(onClick, target, shape = RoundedCornerShape(20.dp), bordered = false, elevation = 1.dp) {
            Box(Modifier.padding(16.dp)) { Content() }
        }
    }
}

@Composable
internal fun CaptureModelSetupBlock(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** 已保存过配置（只差能力测试）时给出下一步的准确指引。 */
    modelConfigured: Boolean = false,
) {
    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capture_model_setup_block"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (modelConfigured) {
                "模型配置已保存。测试通过后，可将题图交给模型整理。"
            } else {
                "配置好模型后，拍照题图才会交给模型整理"
            },
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlineActionChip(
            text = if (modelConfigured) "测试模型" else "去设置",
            onClick = onOpenSettings,
            modifier = Modifier.testTag("capture_model_setup_settings"),
        )
    }
}

@Composable
internal fun CaptureGuidance(modifier: Modifier = Modifier) {
    Text(
        text = "尽量把题干、选项和配图拍完整。",
        modifier = modifier.fillMaxWidth(),
        color = InkSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun AwaitingCorrectionCard(
    onRetake: () -> Unit,
    onPickAnother: () -> Unit,
    onStartCorrection: () -> Unit,
    isImporting: Boolean,
    modifier: Modifier = Modifier,
) {
    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ErrorText.copy(alpha = .25f), RoundedCornerShape(20.dp))
            .testTag("capture_pending_correction"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = ErrorWarm,
            )
            Text(
                text = if (isImporting) "正在保存题图" else "题图待重试",
                modifier = Modifier.padding(start = 10.dp),
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = if (isImporting) {
                "图片已收到，正在自动保存到本机；保存完成后会继续整理题面。"
            } else {
                "这张图还没有安全保存，已暂停后续处理。请重试，或重新拍摄。"
            },
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlineActionChip(
                text = "重新拍摄",
                onClick = onRetake,
                modifier = Modifier
                    .testTag("capture_retake_button"),
                icon = Icons.Outlined.PhotoCamera,
                enabled = !isImporting,
            )
            OutlineActionChip(
                text = "另选照片",
                onClick = onPickAnother,
                modifier = Modifier
                    .testTag("capture_pick_another_button"),
                icon = Icons.Outlined.PhotoLibrary,
                enabled = !isImporting,
            )
        }
        PrimaryActionButton(
            text = if (isImporting) "正在安全保存…" else "重试保存并继续",
            onClick = onStartCorrection,
            enabled = !isImporting,
            icon = Icons.Outlined.Save,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .testTag("capture_start_correction_button"),
        )
    }
}

@Composable
internal fun CaptureCommittedCard(
    reusedExistingProblem: Boolean,
    onView: () -> Unit,
    onCaptureAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capture_committed"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = JadeActive,
            )
            Text(
                text = "已经存入错题本",
                modifier = Modifier.padding(start = 8.dp),
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = "原图和整理后的题面都已保存。现在可以查看这道题，或继续录入下一道。",
            modifier = Modifier.padding(top = 10.dp),
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        // B5：新建入库还是复用了既有同一题，照提交回执如实说，不再把两者混成一句"已入库"。
        Text(
            text = captureCommittedReceiptLine(reusedExistingProblem),
            modifier = Modifier
                .padding(top = 10.dp)
                .testTag("capture_committed_receipt"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryActionButton(
            text = "查看这道题",
            onClick = onView,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .testTag("capture_view_saved_item"),
        )
        OutlineActionChip(
            text = "再录一道",
            onClick = onCaptureAnother,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("capture_another_item"),
        )
    }
}

@Composable
internal fun CaptureError(message: String, modifier: Modifier = Modifier) {
    WorkspacePanel(modifier = modifier.fillMaxWidth()
        .border(1.dp, ErrorText.copy(alpha = .25f), RoundedCornerShape(20.dp))
        .testTag("capture_error")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = ErrorWarm,
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 8.dp),
                color = ErrorText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun CaptureResumeStateCard(
    state: CaptureResumeLoadState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = state == CaptureResumeLoadState.LOADING ||
        state == CaptureResumeLoadState.REDIRECTING
    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isBusy) Outline else ErrorText.copy(alpha = .25f),
                shape = RoundedCornerShape(20.dp),
            )
            .testTag("capture_resume_${state.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = JadeActive,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = ErrorWarm,
                )
            }
            Text(
                text = when (state) {
                    CaptureResumeLoadState.LOADING -> "正在恢复题目…"
                    CaptureResumeLoadState.REDIRECTING -> "正在打开讲题会话…"
                    CaptureResumeLoadState.MISSING -> "这道题已处理或不存在"
                    CaptureResumeLoadState.SOURCE_UNAVAILABLE -> "原图暂时无法打开"
                    CaptureResumeLoadState.NOT_REQUESTED,
                    CaptureResumeLoadState.READY,
                    -> "正在恢复题目…"
                },
                modifier = Modifier.padding(start = 10.dp),
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = when (state) {
                CaptureResumeLoadState.LOADING ->
                    "正在读取本机保存的原图、题面和上次处理状态。"
                CaptureResumeLoadState.REDIRECTING ->
                    "这道题已经准备好讲解，将回到原来的临时会话。"
                CaptureResumeLoadState.MISSING ->
                    "这道题可能已经处理过或不存在，返回后可以继续之前的步骤。"
                CaptureResumeLoadState.SOURCE_UNAVAILABLE ->
                    "原图暂时无法打开，请返回后重新拍摄。"
                CaptureResumeLoadState.NOT_REQUESTED,
                CaptureResumeLoadState.READY,
                -> "正在读取本机保存的题目。"
            },
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!isBusy) {
            OutlineActionChip(
                text = "返回",
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_resume_back_to_inbox"),
            )
        }
    }
}
