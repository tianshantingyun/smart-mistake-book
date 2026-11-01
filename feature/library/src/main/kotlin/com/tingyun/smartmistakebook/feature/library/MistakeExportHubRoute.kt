package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.heightIn
import com.tingyun.smartmistakebook.core.ui.ErrorText
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
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
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
import com.tingyun.smartmistakebook.core.export.MistakeDocxDeliveryIntents
import com.tingyun.smartmistakebook.core.export.MistakeDocxDocumentSaver
import com.tingyun.smartmistakebook.core.export.MistakeDocxExporter
import com.tingyun.smartmistakebook.core.export.MistakeDocxSaveResult
import com.tingyun.smartmistakebook.core.export.MistakePdfBatchEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfDeliveryIntents
import com.tingyun.smartmistakebook.core.export.MistakePdfDocumentSaver
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.MistakePdfSaveResult
import com.tingyun.smartmistakebook.core.export.PreparedMistakePdfToken
import com.tingyun.smartmistakebook.core.export.PreparedPdfPrintAdapter
import com.tingyun.smartmistakebook.core.export.deliveryFormat
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 批量导出的题数上限（与导出核心同一口径；错题本行为"当前筛选"收集候选）。 */
const val MAX_LIBRARY_BATCH_EXPORT_QUESTIONS = MistakePdfBatchEligibility.MAX_QUESTIONS

/**
 * 「已导出的文件」入口（阶段 4A 批 4 · L7，A 形态的落点）。
 *
 * 列表读 [MistakeExportRecord]（文件名 / 时间 / 状态）：进行中如实说"正在整理"，失败照抄
 * worker 写下的人类原因；成功行提供 保存 / 分享 / 打印——**复用既有交付链**
 * （`MistakePdfDeliveryIntents` / `MistakePdfDocumentSaver` / `PreparedPdfPrintAdapter`），
 * 不重写。产物在自己的缓存里被清理后，这里如实说"文件已清理，请重新导出"，不静默失败。
 *
 * 6B-1：记录格式由 [deliveryFormat] 从 displayName 的扩展名判定（零 schema）。PDF 走既有
 * 三件套；Word 走同构的 docx 交付链（保存/分享），**不显示打印**（没有 Word 的打印适配器）。
 * 新增的 Word 记录与旧 PDF 记录在同一屏上按各自的格式动作，互不串用。
 *
 * 通知点进来时（Android 13+ 授权场景）与未授权时的应用内退化，都落在这一屏。
 *
 * 6B-2：本屏同时是原生「编辑卷面」入口之一。来源题 id 由装配处随导航带进来（导出记录里只有
 * 产物身份，没有题 id）；本页**不猜来源**，拿不到 id 时就不摆一个点了也没用的按钮。
 */
@Composable
fun MistakeExportHubRoute(
    records: List<MistakeExportRecord>,
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
    onBack: () -> Unit,
    /** 6B-2：上一次原生导出（详情单题 / 错题本当前筛选）的来源题 id，顺序由装配处给出。 */
    paperEditEntryIds: List<String> = emptyList(),
    /** 6B-2：非 null 且来源非空时显示「编辑卷面」入口；回调原样交出来源 id。 */
    onEditPaper: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pdfExporter = remember(context) { MistakePdfExporter(context) }
    val docxExporter = remember(context) { MistakeDocxExporter(context) }
    val scope = rememberCoroutineScope()
    var pendingSaveToken by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSaveFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<ExportHubMessage?>(null) }

    fun reopen(record: MistakeExportRecord): PreparedMistakePdfToken? =
        record.preparedPdfValueOrNull()?.let(PreparedMistakePdfToken::fromPersistedValue)

    /** 产物还在不在：按记录格式走对应导出器（两条链各自 fail-closed）。 */
    fun artifactAvailable(record: MistakeExportRecord): Boolean {
        val token = reopen(record) ?: return false
        return when (record.deliveryFormat()) {
            MistakeExportFormat.PDF -> pdfExporter.reopenVerified(token) != null
            MistakeExportFormat.DOCX -> docxExporter.reopenVerified(token) != null
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = PreparedMistakePdfToken.fromPersistedValue(pendingSaveToken)
        val format = MistakeExportFormat.fromToken(pendingSaveFormat)
        pendingSaveToken = null
        pendingSaveFormat = null
        val destination = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || destination == null) {
            actionInProgress = false
            return@rememberLauncherForActivityResult
        }
        if (token == null || format == null) {
            actionMessage = ExportHubMessage("文件状态已变化，请重新打开后保存。", true)
            actionInProgress = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saveResult = withContext(Dispatchers.IO) {
                when (format) {
                    MistakeExportFormat.PDF -> pdfExporter.reopenVerified(token)
                        ?.let { prepared ->
                            MistakePdfDocumentSaver.save(context, prepared, destination)
                                .toActionMessage()
                        }
                    MistakeExportFormat.DOCX -> docxExporter.reopenVerified(token)
                        ?.let { prepared ->
                            MistakeDocxDocumentSaver.save(context, prepared, destination)
                                .toActionMessage()
                        }
                }
            }
            actionMessage = saveResult
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
        // 6B-2 原生编辑入口：不依赖模型 START_EXPORT 卡；来源由装配处提供，这里不猜。
        val editPaper = onEditPaper
        // 入口标题按实际可编辑的题数报，空白/重复 id 不冒充题目。
        val editEntryIds = paperEditEntryIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (editPaper != null && editEntryIds.isNotEmpty()) {
            OutlineActionChip(
                text = "编辑卷面后再导出（${editEntryIds.size} 题）",
                onClick = { editPaper(editEntryIds) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_hub_edit_paper"),
            )
            Text(
                text = "仅修改这份卷面，不影响错题本。确认后会重新生成文件。",
                modifier = Modifier.testTag("export_hub_edit_paper_notice"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (records.isEmpty()) {
            Text(
                text = "暂无导出文件。可从错题详情或错题本列表导出，完成后在这里查看。",
                modifier = Modifier.testTag("mistake_export_hub_empty"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            records.forEach { record ->
                val format = record.deliveryFormat()
                ExportRecordRow(
                    record = record,
                    format = format,
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
                                    artifactAvailable(record)
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
                                pendingSaveFormat = format.token
                                runCatching {
                                    saveLauncher.launch(
                                        when (format) {
                                            MistakeExportFormat.PDF ->
                                                MistakePdfDeliveryIntents.createDocument(
                                                    record.displayName.orEmpty(),
                                                )
                                            MistakeExportFormat.DOCX ->
                                                MistakeDocxDeliveryIntents.createDocument(
                                                    record.displayName.orEmpty(),
                                                )
                                        },
                                    )
                                }.onFailure {
                                    pendingSaveToken = null
                                    pendingSaveFormat = null
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
                            // 产物缺失与"打开分享方式"失败是两件不同的事：前者文件真的没了，
                            // 后者文件还在、重试可能成功。改前一律说"文件已清理"，把后者也说错。
                            val preparedIntent = runCatching {
                                withContext(Dispatchers.IO) {
                                    when (format) {
                                        MistakeExportFormat.PDF -> reopen(record)
                                            ?.let(pdfExporter::reopenVerified)
                                            ?.let { prepared ->
                                                MistakePdfDeliveryIntents.share(
                                                    context = context,
                                                    prepared = prepared,
                                                    displayName = record.displayName.orEmpty(),
                                                )
                                            }
                                        MistakeExportFormat.DOCX -> reopen(record)
                                            ?.let(docxExporter::reopenVerified)
                                            ?.let { prepared ->
                                                MistakeDocxDeliveryIntents.share(
                                                    context = context,
                                                    prepared = prepared,
                                                    displayName = record.displayName.orEmpty(),
                                                )
                                            }
                                    }
                                }
                            }
                            val shareIntent = preparedIntent.getOrNull()
                            actionMessage = when {
                                shareIntent != null -> runCatching {
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "分享文件"),
                                    )
                                    ExportHubMessage("已打开分享方式。", false)
                                }.getOrElse {
                                    ExportHubMessage("暂时无法打开分享方式，请稍后再试。", true)
                                }
                                preparedIntent.isSuccess ->
                                    ExportHubMessage("文件已清理，无法分享。请重新导出这一份。", true)
                                else ->
                                    ExportHubMessage("暂时无法准备分享文件，请稍后再试。", true)
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
                                    reopen(record)?.let(pdfExporter::reopenVerified)
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
                    color = if (message.isError) ErrorText else JadeActive,
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
            .heightIn(min = 58.dp),
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
            text = "已导出的文件",
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
    format: MistakeExportFormat,
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
                text = record.displayName ?: "正在整理导出文件…",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatExportTime(record.createdAtEpochMillis)} · ${record.statusLabel(format)}",
                modifier = Modifier.testTag("export_hub_record_status_${record.exportId}"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (record.status == MistakeExportStatus.FAILED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = record.failureMessage.orEmpty(),
                    modifier = Modifier.testTag("export_hub_failure_${record.exportId}"),
                    color = ErrorText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (record.status == MistakeExportStatus.SUCCEEDED) {
                Spacer(Modifier.height(10.dp))
                PrimaryActionButton(
                    text = if (format == MistakeExportFormat.DOCX) "保存 Word" else "保存 PDF",
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
                    // Word 产物没有可用的打印适配器（PreparedPdfPrintAdapter 只认 PDF）：
                    // 不摆一个必然失败的按钮，学生在 Word 里打印。
                    if (format == MistakeExportFormat.PDF) {
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
}

private fun MistakeExportRecord.statusLabel(format: MistakeExportFormat): String = when (status) {
    MistakeExportStatus.RUNNING -> "正在整理"
    MistakeExportStatus.SUCCEEDED -> when (format) {
        // Word 没有"页"这一交付概念：记录行的 pageCount 是占位值，不展示（见批记录）。
        MistakeExportFormat.PDF -> "已完成 · 共 ${pageCount ?: 0} 页"
        MistakeExportFormat.DOCX -> "已完成 · Word 文档"
    }
    MistakeExportStatus.FAILED -> "未完成"
}

internal fun formatExportTime(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochMillis))

private fun MistakePdfSaveResult.toActionMessage(): ExportHubMessage = when (this) {
    MistakePdfSaveResult.SAVED -> ExportHubMessage("PDF 已保存并校验通过。", false)
    MistakePdfSaveResult.INVALID_PREPARED_FILE ->
        ExportHubMessage("文件状态已经变化，请重新导出后再保存。", true)
    MistakePdfSaveResult.DESTINATION_UNAVAILABLE ->
        ExportHubMessage("没有完成保存，请换一个位置后再试。", true)
    MistakePdfSaveResult.DESTINATION_INTEGRITY_MISMATCH ->
        ExportHubMessage("保存的文件校验未通过，请换个位置重试。", true)
}

private fun MistakeDocxSaveResult.toActionMessage(): ExportHubMessage = when (this) {
    MistakeDocxSaveResult.SAVED -> ExportHubMessage("Word 文件已保存并校验通过。", false)
    MistakeDocxSaveResult.INVALID_PREPARED_FILE ->
        ExportHubMessage("文件状态已经变化，请重新导出后再保存。", true)
    MistakeDocxSaveResult.DESTINATION_UNAVAILABLE ->
        ExportHubMessage("没有完成保存，请换一个位置后再试。", true)
    MistakeDocxSaveResult.DESTINATION_INTEGRITY_MISMATCH ->
        ExportHubMessage("保存的文件校验未通过，请换个位置重试。", true)
}

private data class ExportHubMessage(
    val text: String,
    val isError: Boolean,
)
