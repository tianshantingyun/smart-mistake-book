package com.tingyun.smartmistakebook.feature.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.export.MistakePdfBatchEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfDeliveryIntents
import com.tingyun.smartmistakebook.core.export.MistakePdfDocumentSaver
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.MistakePdfSaveResult
import com.tingyun.smartmistakebook.core.export.PreparedMistakePdfToken
import com.tingyun.smartmistakebook.core.export.PreparedPdfPrintAdapter
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 批量导出的题数上限（与导出核心同一口径；错题本行为"当前筛选"收集候选）。 */
const val MAX_LIBRARY_BATCH_EXPORT_QUESTIONS = MistakePdfBatchEligibility.MAX_QUESTIONS

/**
 * 「导出成果」入口（阶段 4A 批 4 · L7，A 形态的落点）。
 *
 * 列表读 [MistakeExportRecord]（文件名 / 时间 / 状态）：进行中如实说"正在整理"，失败照抄
 * worker 写下的人类原因；成功行提供 保存 / 分享 / 打印——**复用既有交付链**
 * （`MistakePdfDeliveryIntents` / `MistakePdfDocumentSaver` / `PreparedPdfPrintAdapter`），
 * 不重写。产物在自己的缓存里被清理后，这里如实说"文件已清理，请重新导出"，不静默失败。
 *
 * 通知点进来时（Android 13+ 授权场景）与未授权时的应用内退化，都落在这一屏。
 */
@Composable
fun MistakeExportHubRoute(
    records: List<MistakeExportRecord>,
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exporter = remember(context) { MistakePdfExporter(context) }
    val scope = rememberCoroutineScope()
    var pendingSaveToken by rememberSaveable { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<ExportHubMessage?>(null) }

    fun reopen(record: MistakeExportRecord): PreparedMistakePdfToken? =
        record.preparedPdfValueOrNull()?.let(PreparedMistakePdfToken::fromPersistedValue)

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = PreparedMistakePdfToken.fromPersistedValue(pendingSaveToken)
        pendingSaveToken = null
        val destination = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || destination == null) {
            actionInProgress = false
            return@rememberLauncherForActivityResult
        }
        if (token == null) {
            actionMessage = ExportHubMessage("保存所需的文件状态已失效，请重新打开后再保存。", true)
            actionInProgress = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saveResult = withContext(Dispatchers.IO) {
                exporter.reopenVerified(token)?.let { prepared ->
                    MistakePdfDocumentSaver.save(context, prepared, destination)
                }
            }
            actionMessage = saveResult?.toActionMessage()
                ?: ExportHubMessage("文件已清理，无法保存。请重新导出这一份。", true)
            actionInProgress = false
        }
    }

    RootPageColumn(
        modifier = modifier.testTag("mistake_export_hub"),
    ) {
        MistakeExportHubHeader(onBack)
        Spacer(Modifier.height(8.dp))
        notice?.let { text ->
            ExportHubNotice(message = text, onDismiss = onDismissNotice)
            Spacer(Modifier.height(10.dp))
        }
        if (records.isEmpty()) {
            Text(
                text = "还没有导出成果。在错题详情或错题本列表发起导出，整理完成后就会出现在这里。",
                modifier = Modifier.testTag("mistake_export_hub_empty"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            records.forEach { record ->
                ExportRecordRow(
                    record = record,
                    actionInProgress = actionInProgress,
                    onSave = {
                        val token = reopen(record)
                        if (token == null) {
                            actionMessage = ExportHubMessage(
                                "文件已清理，无法保存。请重新导出这一份。",
                                true,
                            )
                        } else {
                            actionMessage = null
                            actionInProgress = true
                            // 先把"文件还在不在"核清楚再打开保存位置：让用户挑完位置才说
                            // "文件已清理"是把一次必然失败的操作塞给他。
                            scope.launch {
                                val available = withContext(Dispatchers.IO) {
                                    exporter.reopenVerified(token) != null
                                }
                                if (!available) {
                                    actionInProgress = false
                                    actionMessage = ExportHubMessage(
                                        "文件已清理，无法保存。请重新导出这一份。",
                                        true,
                                    )
                                    return@launch
                                }
                                pendingSaveToken = token.toPersistedValue()
                                runCatching {
                                    saveLauncher.launch(
                                        MistakePdfDeliveryIntents.createDocument(
                                            record.displayName.orEmpty(),
                                        ),
                                    )
                                }.onFailure {
                                    pendingSaveToken = null
                                    actionInProgress = false
                                    actionMessage = ExportHubMessage(
                                        "暂时无法打开保存位置，请稍后再试。",
                                        true,
                                    )
                                }
                            }
                        }
                    },
                    onShare = {
                        actionMessage = null
                        actionInProgress = true
                        scope.launch {
                            actionMessage = runCatching {
                                val prepared = withContext(Dispatchers.IO) {
                                    reopen(record)?.let(exporter::reopenVerified)
                                } ?: error("Prepared export is unavailable")
                                val shareIntent = withContext(Dispatchers.IO) {
                                    MistakePdfDeliveryIntents.share(
                                        context = context,
                                        prepared = prepared,
                                        displayName = record.displayName.orEmpty(),
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "分享这份 A4 导出"),
                                )
                                ExportHubMessage("已打开分享方式。", false)
                            }.getOrElse {
                                ExportHubMessage("文件已清理，无法分享。请重新导出这一份。", true)
                            }
                            actionInProgress = false
                        }
                    },
                    onPrint = {
                        actionMessage = null
                        actionInProgress = true
                        scope.launch {
                            actionMessage = runCatching {
                                val prepared = withContext(Dispatchers.IO) {
                                    reopen(record)?.let(exporter::reopenVerified)
                                } ?: error("Prepared export is unavailable")
                                val printManager = context.getSystemService(Context.PRINT_SERVICE)
                                    as? PrintManager
                                    ?: error("Print service unavailable")
                                printManager.print(
                                    record.displayName.orEmpty().removeSuffix(".pdf"),
                                    PreparedPdfPrintAdapter(
                                        prepared = prepared,
                                        displayName = record.displayName.orEmpty(),
                                    ),
                                    PrintAttributes.Builder()
                                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                        .build(),
                                )
                                ExportHubMessage("已打开打印设置。", false)
                            }.getOrElse {
                                ExportHubMessage("文件已清理，无法打印。请重新导出这一份。", true)
                            }
                            actionInProgress = false
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            actionMessage?.let { message ->
                Text(
                    text = message.text,
                    modifier = Modifier.testTag("export_hub_action_message"),
                    color = if (message.isError) ErrorWarm else JadeActive,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MistakeExportHubHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("mistake_export_hub_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = Ink,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = "导出成果",
            color = Ink,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun ExportHubNotice(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mistake_export_hub_notice"),
        color = JadeSoft.copy(alpha = 0.38f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("mistake_export_hub_notice_dismiss"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "知道了",
                    tint = InkSecondary,
                )
            }
        }
    }
}

@Composable
private fun ExportRecordRow(
    record: MistakeExportRecord,
    actionInProgress: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_hub_record_${record.exportId}"),
        color = JadeSoft.copy(alpha = 0.24f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = record.displayName ?: "正在整理 A4 版式…",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatExportTime(record.createdAtEpochMillis)} · ${record.statusLabel()}",
                modifier = Modifier.testTag("export_hub_record_status_${record.exportId}"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (record.status == MistakeExportStatus.FAILED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = record.failureMessage.orEmpty(),
                    modifier = Modifier.testTag("export_hub_failure_${record.exportId}"),
                    color = ErrorWarm,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (record.status == MistakeExportStatus.SUCCEEDED) {
                Spacer(Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "保存 PDF",
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_hub_save_${record.exportId}"),
                    icon = Icons.Outlined.SaveAlt,
                    enabled = !actionInProgress,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlineActionChip(
                        text = "分享",
                        onClick = onShare,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_hub_share_${record.exportId}"),
                        icon = Icons.Outlined.Share,
                        enabled = !actionInProgress,
                    )
                    OutlineActionChip(
                        text = "打印",
                        onClick = onPrint,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_hub_print_${record.exportId}"),
                        icon = Icons.Outlined.Print,
                        enabled = !actionInProgress,
                    )
                }
            }
        }
    }
}

private fun MistakeExportRecord.statusLabel(): String = when (status) {
    MistakeExportStatus.RUNNING -> "正在整理"
    MistakeExportStatus.SUCCEEDED -> "已完成 · 共 ${pageCount ?: 0} 页"
    MistakeExportStatus.FAILED -> "未完成"
}

internal fun formatExportTime(epochMillis: Long): String = EXPORT_TIME_FORMATTER
    .format(Instant.ofEpochMilli(epochMillis))

private val EXPORT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm", Locale.CHINA)
    .withZone(ZoneId.systemDefault())

private fun MistakePdfSaveResult.toActionMessage(): ExportHubMessage = when (this) {
    MistakePdfSaveResult.SAVED -> ExportHubMessage("PDF 已保存，并已重新核对完整。", false)
    MistakePdfSaveResult.INVALID_PREPARED_FILE ->
        ExportHubMessage("文件状态已经变化，请重新导出后再保存。", true)
    MistakePdfSaveResult.DESTINATION_UNAVAILABLE ->
        ExportHubMessage("没有完成保存，请换一个位置后再试。", true)
    MistakePdfSaveResult.DESTINATION_INTEGRITY_MISMATCH ->
        ExportHubMessage("保存后的文件没有通过核对，请换一个位置后再试。", true)
}

private data class ExportHubMessage(
    val text: String,
    val isError: Boolean,
)
