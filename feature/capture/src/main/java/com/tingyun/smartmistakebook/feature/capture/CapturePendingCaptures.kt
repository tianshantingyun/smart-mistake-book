package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tingyun.smartmistakebook.core.ui.InkMuted
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.SectionHeader
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

internal fun pendingCaptureStageLabel(stage: PendingCaptureStage): String = when (stage) {
    PendingCaptureStage.TUTOR_SESSION_READY -> "题目已保存，可以开始讲解"
    PendingCaptureStage.SOURCE_UNAVAILABLE -> "原图暂时打不开，请重新拍摄"
    PendingCaptureStage.RECAPTURE_REQUIRED -> "关键内容看不清，请重新拍摄"
    PendingCaptureStage.MODEL_WORKING -> "正在整理题目，可稍后再来"
    PendingCaptureStage.RETRY_OR_MANUAL -> "上次没有完成，点此继续"
    PendingCaptureStage.MANUAL_REVIEW_REQUIRED -> "题面还没整理完整，点此继续"
    PendingCaptureStage.READY_TO_REVIEW -> "题面已整理，可继续"
    PendingCaptureStage.READY_TO_CONTINUE -> "已保存原图，点此继续"
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
            text = "还没存入错题本的题会先留在这里；存入后自动移出。",
            color = InkMuted,
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
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${summary.total} 道临时题记录保留在本机",
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
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 68.dp)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        needsAttention -> Icons.Outlined.WarningAmber
                        item.stage == PendingCaptureStage.TUTOR_SESSION_READY ->
                            Icons.AutoMirrored.Outlined.Chat
                        else -> Icons.Outlined.Schedule
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (needsAttention) ErrorWarm else JadeActive,
                )
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pendingCaptureStageLabel(item.stage),
                        color = if (needsAttention) ErrorWarm else InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            item.subject,
                            pendingCaptureOriginLabel(item.origin),
                            formatPendingTime(item.updatedAtEpochMillis),
                        ).joinToString(" · "),
                        color = InkMuted,
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
                    Text("不再保留", color = ErrorWarm)
                }
            }
        }
    }
}

@Composable
private fun PendingCaptureEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp)
            .testTag("capture_pending_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("没有待处理题目", color = Ink, style = MaterialTheme.typography.titleMedium)
        Text("新拍的题会先安全保存在本机，再出现在这里。", color = InkSecondary)
    }
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
        title = { Text("不再保留这道题？") },
        text = {
            Text(
                text = "「${item.title}」会从待处理列表移除，也不会存入错题本。" +
                    "本机原图和记录会按原样保留到后续清理。",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !inProgress,
                modifier = Modifier.testTag("capture_pending_discard_confirm"),
            ) { Text("不再保留", color = ErrorWarm) }
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
