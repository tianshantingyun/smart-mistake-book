package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

import com.tingyun.smartmistakebook.core.ui.ClaudePageHeader

import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingyun.smartmistakebook.core.domain.BatchImportJob
import com.tingyun.smartmistakebook.core.domain.BatchImportDraftState
import com.tingyun.smartmistakebook.core.domain.BatchImportPage
import com.tingyun.smartmistakebook.core.domain.BatchImportPageStatus
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.BatchImportStatus
import com.tingyun.smartmistakebook.core.domain.BatchOrganizationUnavailableException
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import com.tingyun.smartmistakebook.core.domain.CreatePdfImportRequest
import com.tingyun.smartmistakebook.core.domain.MAX_BATCH_IMPORT_PAGES
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageLazyColumn
import com.tingyun.smartmistakebook.core.ui.SmartColors
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 整卷录入（L6）：录入流内部的一步，不再是错题本栏与"拍照"并列的平级入口。
 *
 * 从录入屏的「整卷/PDF」方式进来，只做这一件事：把整卷的多张照片 / 一份 PDF 按页保存
 * 成草稿，逐页/逐题继续。路由与 [BatchImportRepository] 管线未动——收敛的只是入口与命名。
 */
@Composable
fun BatchImportRoute(
    repository: BatchImportRepository,
    onOpenDraft: (String) -> Unit,
    onSplitReady: (String) -> Unit = {},
    onOpenSavedEntry: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jobs by repository.observeBatchImports().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var isOrganizing by remember { mutableStateOf(false) }
    // KD-42：整卷录入在 job 落库之前被杀 → 列表里什么都没有。staging 目录里那批没人认领的页面
    // 是唯一剩下的证据，入口据此如实说一句「上次导入未完成」。
    //
    // 读一次就够：答案只会在「冒出新的任务」（重导成功、或老任务重新认领页面）或「会话被回收」
    // 时改变，所以按最新任务 id 刷新。读失败按「没有提示」处理——界面宁可少说一句，也不能靠猜
    // 说学生的上次导入被中断了。
    var interruptedImport by remember { mutableStateOf(false) }
    LaunchedEffect(jobs.firstOrNull()?.jobId) {
        interruptedImport = try {
            repository.hasInterruptedBatchImport()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
    fun launchAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "操作暂时没有完成，请稍后重试。"
            }
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_BATCH_IMPORT_PAGES),
    ) { selected ->
        if (selected.size < 2) {
            message = "请选择至少 2 张照片。单张图片请使用普通录入。"
            return@rememberLauncherForActivityResult
        }
        message = null
        scope.launch {
            try {
                repository.createBatchImport(
                    CreateBatchImportRequest(
                        requestId = UUID.randomUUID().toString(),
                        localUris = selected.map { it.toString() },
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "这组照片暂时没有保存下来，请重新选择。"
            }
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { selected ->
        selected ?: return@rememberLauncherForActivityResult
        message = null
        scope.launch {
            try {
                repository.createPdfImport(
                    CreatePdfImportRequest(
                        requestId = UUID.randomUUID().toString(),
                        localUri = selected.toString(),
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "这份 PDF 暂时没有导入，请确认文件可以正常打开。"
            }
        }
    }
    val latestJob = jobs.firstOrNull()
    BatchImportContent(
        job = latestJob,
        message = message,
        interruptedImport = interruptedImport,
        onChoosePhotos = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onChoosePdf = { pdfPicker.launch(arrayOf("application/pdf")) },
        onPause = { jobId -> launchAction { repository.pauseBatchImport(jobId) } },
        onResume = { jobId -> launchAction { repository.resumeBatchImport(jobId) } },
        onRetry = { jobId, page ->
            launchAction { repository.retryBatchImportPage(jobId, page) }
        },
        onSkip = { jobId, page ->
            launchAction { repository.skipBatchImportPage(jobId, page) }
        },
        onSplitReady = onSplitReady,
        isOrganizing = isOrganizing,
        onOrganize = { jobId ->
            scope.launch {
                isOrganizing = true
                try {
                    repository.organizeBatch(jobId)
                    message = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: BatchOrganizationUnavailableException) {
                    message = "请先在“我的”里配置模型服务，再整理相邻页面。"
                } catch (_: Exception) {
                    message = "分题尚未完成，所有页面已保留，可稍后继续。"
                } finally {
                    isOrganizing = false
                }
            }
        },
        onOpenDraft = onOpenDraft,
        onOpenSavedEntry = onOpenSavedEntry,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun BatchImportContent(
    job: BatchImportJob?,
    message: String?,
    // KD-42：存在未被任何任务认领的 staging 会话（上次导入在 job 落库之前被杀）。
    // 只有为真时才出现中断提示，读口见 BatchImportRepository.hasInterruptedBatchImport()。
    interruptedImport: Boolean = false,
    onChoosePhotos: () -> Unit,
    onChoosePdf: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String, Int) -> Unit,
    onSkip: (String, Int) -> Unit,
    onSplitReady: (String) -> Unit = {},
    isOrganizing: Boolean = false,
    onOrganize: (String) -> Unit = {},
    onOpenDraft: (String) -> Unit,
    onOpenSavedEntry: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RootPageLazyColumn(
        modifier = modifier.testTag("batch_import_root"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item("header") {
            BatchImportHeader(onBack)
            Spacer(Modifier.height(12.dp))
            WorkspacePanel {
                Column {
                    Text(
                        text = "选择多张照片或一份 PDF。逐页保存，单页失败不影响其他页。",
                        color = SmartColors.InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (interruptedImport) {
                        Spacer(Modifier.height(12.dp))
                        BatchImportInterruptedNotice()
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryActionButton(
                        text = when {
                            job == null -> "选择多张照片"
                            job.status == BatchImportStatus.COMPLETED && job.failedCount == 0 ->
                                "再选一组照片"
                            else -> "请先完成本次导入"
                        },
                        onClick = onChoosePhotos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("batch_import_choose"),
                        icon = Icons.Outlined.ImageSearch,
                        enabled = job == null ||
                            (job.status == BatchImportStatus.COMPLETED && job.failedCount == 0),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlineActionChip(
                        text = when {
                            job == null -> "导入整份 PDF"
                            job.status == BatchImportStatus.COMPLETED && job.failedCount == 0 ->
                                "再导入一份 PDF"
                            else -> "请先完成本次导入"
                        },
                        onClick = onChoosePdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("batch_import_choose_pdf"),
                        icon = Icons.Outlined.PictureAsPdf,
                        enabled = job == null ||
                            (job.status == BatchImportStatus.COMPLETED && job.failedCount == 0),
                    )
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = SmartColors.ErrorText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (job == null) {
            item("empty") { BatchImportEmpty() }
        } else {
            item("summary") {
                BatchImportSummary(
                    job = job,
                    onPause = { onPause(job.jobId) },
                    onResume = { onResume(job.jobId) },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (
                job.status == BatchImportStatus.COMPLETED &&
                job.failedCount == 0 &&
                job.remainingBoundaryCount > 0
            ) {
                item("organization") {
                    BatchOrganizationCard(
                        job = job,
                        isOrganizing = isOrganizing,
                        onOrganize = { onOrganize(job.jobId) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            job.splitReadyJobId?.let { splitJobId ->
                item("split_ready") {
                    BatchSplitReadyCard(
                        onOpen = { onSplitReady(splitJobId) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            job.pages.forEachIndexed { index, page ->
                item("page_${page.pageIndex}") {
                    BatchImportPageRow(
                        page = page,
                        continuesPrevious = index > 0 &&
                            job.pages[index - 1].draftId != null &&
                            job.pages[index - 1].draftId == page.draftId,
                        onRetry = { onRetry(job.jobId, page.pageIndex) },
                        onSkip = { onSkip(job.jobId, page.pageIndex) },
                        onOpenDraft = onOpenDraft,
                        onOpenSavedEntry = onOpenSavedEntry,
                    )
                }
            }
        }
        item("footer") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * KD-42 的中断提示：整卷录入在 job 落库之前被杀时，列表里既没有任务也没有失败页，学生看到的是
 * 「什么都没发生」；这条提示把那批仍在 staging 里的页面如实说出来，并指向旁边既有的「选择多张
 * 照片 / 导入整份 PDF」——重导即可。
 *
 * 归因边界：判据是 staging 里有没有**未被任何任务认领**的会话（见 [BatchImportRepository.hasInterruptedBatchImport] 与
 * `AndroidBatchImportSourceStaging.hasOrphanedSessions`），所以它不随 60 分钟宽限自行出现或消失：
 * 会话被新任务认领、或被启动对账（超宽限）回收之前，这句提示就一直成立。
 */
@Composable
private fun BatchImportInterruptedNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("batch_import_interrupted"),
        color = SmartColors.ErrorWarm.copy(alpha = 0.10f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            SmartColors.ErrorWarm.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "上次导入未完成",
                color = SmartColors.Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "上次选择的页面未保存成功，请重新选择照片或 PDF。",
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BatchOrganizationCard(
    job: BatchImportJob,
    isOrganizing: Boolean,
    onOrganize: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("batch_import_organization"),
        color = SmartColors.Paper,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isOrganizing) "正在拆分题目" else "自动拆分题目",
                color = SmartColors.Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    isOrganizing -> "正在按页面顺序整理，可以离开本页，原页面不会丢失。"
                    else ->
                        "自动识别跨页题目，并整理为单独的题。" +
                            "整理会直接交给已配置模型，不再逐次询问。"
                },
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                isOrganizing -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = SmartColors.Jade,
                    trackColor = SmartColors.Outline.copy(alpha = 0.6f),
                )
                else -> PrimaryActionButton(
                    text = "开始分题",
                    onClick = onOrganize,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
            }
        }
    }
}

@Composable
private fun BatchSplitReadyCard(
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("batch_import_split_ready"),
        color = SmartColors.Paper,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "有页面已自动分题",
                color = SmartColors.Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "分出的题目已裁好，去勾选要录入的题目，逐题确认存入错题本。",
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            PrimaryActionButton(
                text = "选择要录入的题",
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("batch_import_split_ready_open"),
            )
        }
    }
}

@Composable
private fun BatchImportHeader(onBack: () -> Unit) {
    ClaudePageHeader("整卷录入", onBack = onBack)
}

@Composable
private fun BatchImportSummary(
    job: BatchImportJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val completed = batchImportCompletedCount(job)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("batch_import_summary"),
        color = SmartColors.JadeSoft.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = batchImportTitle(job),
                        color = SmartColors.Ink,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = batchImportSummaryLine(job),
                        color = SmartColors.InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (job.status) {
                    BatchImportStatus.PROCESSING -> OutlineActionChip("暂停", onPause)
                    BatchImportStatus.PAUSED -> OutlineActionChip("继续", onResume)
                    BatchImportStatus.COMPLETED -> Unit
                }
            }
            LinearProgressIndicator(
                progress = { completed.toFloat() / job.pages.size },
                modifier = Modifier.fillMaxWidth(),
                color = SmartColors.Jade,
                trackColor = SmartColors.Outline.copy(alpha = 0.6f),
            )
            Text(
                text = "可以离开本页；已保存的题会留在录入页的「待处理题目」里，可从那里继续。",
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BatchImportPageRow(
    page: BatchImportPage,
    continuesPrevious: Boolean,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onOpenSavedEntry: (String) -> Unit,
) {
    val readyDraft = page.draftId.takeIf { page.draftState == BatchImportDraftState.EDITING }
    val savedEntry = page.savedEntryId.takeIf { page.draftState == BatchImportDraftState.COMMITTED }
    val clickable = readyDraft != null || savedEntry != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .clickable(enabled = clickable) {
                if (savedEntry != null) onOpenSavedEntry(savedEntry)
                else readyDraft?.let(onOpenDraft)
            }
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .testTag("batch_import_page_${page.pageIndex}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (page.status) {
                BatchImportPageStatus.READY -> Icons.Outlined.CheckCircle
                BatchImportPageStatus.FAILED -> Icons.Outlined.ErrorOutline
                BatchImportPageStatus.SKIPPED -> Icons.Outlined.CheckCircle
                else -> Icons.Outlined.HourglassTop
            },
            contentDescription = null,
            tint = if (page.status == BatchImportPageStatus.FAILED) {
                SmartColors.ErrorWarm
            } else {
                SmartColors.Jade
            },
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = if (continuesPrevious) {
                    "第 ${page.pageIndex + 1} 页 · 接上页"
                } else {
                    "第 ${page.pageIndex + 1} 页"
                },
                style = MaterialTheme.typography.titleSmall,
                color = SmartColors.Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (continuesPrevious && page.draftState == BatchImportDraftState.EDITING) {
                    "已和上一页放在同一道题里"
                } else {
                    batchImportPageLabel(page)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (page.status == BatchImportPageStatus.FAILED) {
                    SmartColors.ErrorText
                } else {
                    SmartColors.InkSecondary
                },
            )
        }
        when {
            page.status == BatchImportPageStatus.FAILED -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlineActionChip("跳过", onSkip)
                OutlineActionChip("重试", onRetry)
            }
            clickable -> Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "打开这道题",
                tint = SmartColors.InkSecondary,
            )
        }
    }
    PaperDivider(color = SmartColors.Outline.copy(alpha = 0.7f))
}

@Composable
private fun BatchImportEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("尚未录入整卷", style = MaterialTheme.typography.titleMedium)
        Text("可选择多张照片，也可直接选择 PDF。", color = SmartColors.InkSecondary)
    }
}

internal fun batchImportTitle(job: BatchImportJob): String = when (job.status) {
    BatchImportStatus.PROCESSING -> "正在保存 ${job.pages.size} 页"
    BatchImportStatus.PAUSED -> "已暂停，进度保留在本机"
    BatchImportStatus.COMPLETED -> if (job.failedCount == 0) {
        if (job.remainingBoundaryCount == 0) {
            "${job.questionCount} 道题已分好"
        } else {
            "${job.pages.size} 页已保存"
        }
    } else {
        "其余页面已保存"
    }
}

internal fun batchImportPageLabel(status: BatchImportPageStatus): String = when (status) {
    BatchImportPageStatus.QUEUED -> "准备中"
    BatchImportPageStatus.IMPORTING -> "正在保存"
    BatchImportPageStatus.READY -> "已保存，点此继续"
    BatchImportPageStatus.FAILED -> "这一页没有保存"
    BatchImportPageStatus.SKIPPED -> "已跳过"
}

internal fun batchImportPageLabel(page: BatchImportPage): String =
    if (page.status != BatchImportPageStatus.READY) {
        batchImportPageLabel(page.status)
    } else when (page.draftState) {
        BatchImportDraftState.EDITING -> "已保存，点此继续"
        BatchImportDraftState.COMMITTED -> if (page.savedEntryId != null) "已存入错题本，查看题目" else "已保存"
        BatchImportDraftState.REMOVED -> "已移除"
        BatchImportDraftState.UNAVAILABLE, null -> "这道题暂时无法打开"
    }

internal fun batchImportSummaryLine(job: BatchImportJob): String = buildList {
    add("已保存 ${job.readyCount} 页")
    if (job.skippedCount > 0) add("已跳过 ${job.skippedCount} 页")
    add("需重试 ${job.failedCount} 页")
    add("剩余 ${job.remainingCount} 页")
}.joinToString(" · ")

internal fun batchImportCompletedCount(job: BatchImportJob): Int =
    job.readyCount + job.failedCount + job.skippedCount
