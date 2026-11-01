package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.ui.ErrorText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.PendingCaptureItem
import com.tingyun.smartmistakebook.core.domain.PendingCaptureStage
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.ScreenStatePanel
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.claudeInteractiveRow
import com.tingyun.smartmistakebook.core.ui.studentSubjectLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 录入界面入口态的「待处理」列表（L4）。
 *
 * 裁定原文：「待处理草稿放录入界面，不放错题本栏」。改前这张列表只存在于错题本栏
 * （`PendingCaptureInboxRoute`，已随批量导入改造删除），录入界面入口态没有任何地方
 * 能看到"还没存入的题"；`BatchImportRoute` 的文案"已保存的题会继续留在待处理题目中"
 * 也因此没有落点。本列表消费 `CaptureWorkflowRepository.observePendingCaptures`，
 * 逐条如实展示 [PendingCaptureStage] 的 8 种状态，并提供两条出路：
 * 恢复（打开草稿/讲题会话）与废弃（标记 ABANDONED，从列表移除、不入错题本）。
 */

/** 打开一条待处理题的落点：讲题会话已就绪的题回到会话，其余回到草稿。 */
internal sealed interface PendingCaptureOpenTarget {
    data class Draft(val draftId: String) : PendingCaptureOpenTarget
    data class TutorSession(val sessionId: String) : PendingCaptureOpenTarget
}

internal fun pendingCaptureOpenTarget(item: PendingCaptureItem): PendingCaptureOpenTarget {
    val sessionId = item.tutorSessionId
    return if (item.stage == PendingCaptureStage.TUTOR_SESSION_READY && sessionId != null) {
        PendingCaptureOpenTarget.TutorSession(sessionId)
    } else {
        PendingCaptureOpenTarget.Draft(item.draftId)
    }
}

internal data class PendingCaptureSummary(
    val total: Int,
    val modelWorking: Int,
    val readyForStudent: Int,
    val needsAttention: Int,
) {
    init {
        require(total >= 0)
        require(modelWorking >= 0)
        require(readyForStudent >= 0)
        require(needsAttention >= 0)
        require(total == modelWorking + readyForStudent + needsAttention)
    }
}

/** 8 态各自归类：模型在处理 / 等学生继续 / 需要学生处理。 */
internal fun summarizePendingCaptures(items: List<PendingCaptureItem>): PendingCaptureSummary {
    var modelWorking = 0
    var readyForStudent = 0
    var needsAttention = 0
    items.forEach { item ->
        when (item.stage) {
            PendingCaptureStage.MODEL_WORKING -> modelWorking++
            PendingCaptureStage.SOURCE_UNAVAILABLE,
            PendingCaptureStage.RECAPTURE_REQUIRED,
            PendingCaptureStage.RETRY_OR_MANUAL,
            -> needsAttention++
            PendingCaptureStage.TUTOR_SESSION_READY,
            PendingCaptureStage.MANUAL_REVIEW_REQUIRED,
            PendingCaptureStage.READY_TO_REVIEW,
            PendingCaptureStage.READY_TO_CONTINUE,
            -> readyForStudent++
        }
    }
    return PendingCaptureSummary(
        total = items.size,
        modelWorking = modelWorking,
        readyForStudent = readyForStudent,
        needsAttention = needsAttention,
    )
}

internal fun pendingCaptureStageLabel(
    stage: PendingCaptureStage,
    /** A8：手动建题的草稿没有原图，不能声称"已保存原图"。 */
    hasSourceImage: Boolean = true,
): String = when (stage) {
    PendingCaptureStage.TUTOR_SESSION_READY -> "题目已保存，可以开始讲解"
    PendingCaptureStage.SOURCE_UNAVAILABLE -> "原图暂时打不开，请重新拍摄"
    PendingCaptureStage.RECAPTURE_REQUIRED -> "关键内容看不清，请重新拍摄"
    PendingCaptureStage.MODEL_WORKING -> "正在整理题目，可稍后再来"
    PendingCaptureStage.RETRY_OR_MANUAL -> "上次没有完成，点此继续"
    PendingCaptureStage.MANUAL_REVIEW_REQUIRED -> "题面待补全，继续编辑"
    PendingCaptureStage.READY_TO_REVIEW -> "题面已整理，可继续"
    PendingCaptureStage.READY_TO_CONTINUE -> if (hasSourceImage) {
        "已保存原图，点此继续"
    } else {
        "尚未存入错题本，继续处理"
    }
}

@Composable
internal fun CapturePendingCapturesSection(
    items: List<PendingCaptureItem>,
    message: String?,
    discardInProgress: Boolean,
    onOpenItem: (PendingCaptureItem) -> Unit,
    onDiscardItem: (PendingCaptureItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("capture_pending_section")) {
        SectionHeader(
            title = "待处理题目",
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "未完成录入的题保存在这里，存入错题本后自动移出。",
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (items.isEmpty()) {
            PendingCaptureEmpty()
        } else {
            val summary = remember(items) { summarizePendingCaptures(items) }
            PendingCaptureSummaryCard(
                summary = summary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(6.dp))
            items.forEach { item ->
                Spacer(Modifier.height(10.dp))
                PendingCaptureRow(
                    item = item,
                    discardEnabled = !discardInProgress,
                    onClick = { onOpenItem(item) },
                    onDiscard = { onDiscardItem(item) },
                )
            }
        }
        message?.let { text ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("capture_pending_message"),
            )
        }
    }
}

@Composable
private fun PendingCaptureSummaryCard(
    summary: PendingCaptureSummary,
    modifier: Modifier = Modifier,
) {
    val details = buildList {
        if (summary.modelWorking > 0) add("正在整理 ${summary.modelWorking} 道")
        if (summary.readyForStudent > 0) add("等你继续 ${summary.readyForStudent} 道")
        if (summary.needsAttention > 0) add("需要处理 ${summary.needsAttention} 道")
    }.joinToString(" · ")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capture_pending_summary"),
        shape = RoundedCornerShape(16.dp),
        color = JadeSoft,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "共 ${summary.total} 道待处理题，保存在本机",
                color = Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = details,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PendingCaptureRow(
    item: PendingCaptureItem,
    discardEnabled: Boolean,
    onClick: () -> Unit,
    onDiscard: () -> Unit,
) {
    val needsAttention = item.stage == PendingCaptureStage.SOURCE_UNAVAILABLE ||
        item.stage == PendingCaptureStage.RECAPTURE_REQUIRED ||
        item.stage == PendingCaptureStage.RETRY_OR_MANUAL
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capture_pending_${item.draftId}"),
        shape = RoundedCornerShape(20.dp),
        color = SmartColors.ContentSurface,
        shadowElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 68.dp)
                    .claudeInteractiveRow(onClick = onClick, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(14.dp),
                    color = if (needsAttention) ErrorWarm.copy(alpha = .10f) else JadeSoft) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                needsAttention -> Icons.Outlined.WarningAmber
                                item.stage == PendingCaptureStage.TUTOR_SESSION_READY ->
                                    Icons.AutoMirrored.Outlined.Chat
                                else -> Icons.Outlined.Schedule
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (needsAttention) ErrorWarm else JadeActive,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.title,
                        color = Ink,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pendingCaptureStageLabel(
                            item.stage,
                            hasSourceImage = item.hasSourceImage,
                        ),
                        color = if (needsAttention) ErrorText else InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            item.subject?.studentSubjectLabel(),
                            pendingCaptureOriginLabel(item.origin),
                            formatPendingTime(item.updatedAtEpochMillis),
                        ).joinToString(" · "),
                        color = InkSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (item.stage == PendingCaptureStage.TUTOR_SESSION_READY) {
                        "打开待讲题目"
                    } else {
                        "继续处理"
                    },
                    tint = InkSecondary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDiscard,
                    enabled = discardEnabled,
                    modifier = Modifier.testTag("capture_pending_discard_${item.draftId}"),
                ) {
                    Text("移出待处理", color = ErrorText)
                }
            }
        }
    }
}

@Composable
private fun PendingCaptureEmpty() {
    ScreenStatePanel(icon = Icons.Outlined.Schedule, title = "暂无待处理题目",
        supportingText = "未完成录入的题会显示在这里。",
        modifier = Modifier.padding(top = 12.dp).testTag("capture_pending_empty"))
}

@Composable
internal fun PendingCaptureDiscardDialog(
    item: PendingCaptureItem,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("移出待处理列表？") },
        text = {
            Text(
                text = "「${item.title}」将移出待处理列表，不会加入错题本。" +
                    "原图和记录仍保留在本机。",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !inProgress,
                modifier = Modifier.testTag("capture_pending_discard_confirm"),
            ) { Text("移出待处理", color = ErrorText) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !inProgress,
                modifier = Modifier.testTag("capture_pending_discard_cancel"),
            ) { Text("取消") }
        },
    )
}

private fun pendingCaptureOriginLabel(origin: CaptureEntryOrigin): String = when (origin) {
    CaptureEntryOrigin.TUTOR -> "讲题拍题"
    CaptureEntryOrigin.LIBRARY -> "错题本录入"
}

private fun formatPendingTime(epochMillis: Long): String = SimpleDateFormat(
    "M月d日 HH:mm",
    Locale.getDefault(),
).format(Date(epochMillis))
