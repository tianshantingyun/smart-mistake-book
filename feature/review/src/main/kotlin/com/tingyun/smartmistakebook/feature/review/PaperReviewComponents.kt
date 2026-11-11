package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.*
import com.tingyun.smartmistakebook.core.model.ReadableMathText
import com.tingyun.smartmistakebook.core.ui.*
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField

@Composable
internal fun PaperQuestionMaterial(
    question: PaperReviewQuestion, repository: MistakeDetailRepository,
    onImage: (String, String) -> Unit,
) {
    var retry by remember(question.problemRevisionId) { mutableIntStateOf(0) }
    val material by produceState<MistakeDetailState>(MistakeDetailState.Loading, question.problemRevisionId, retry) {
        value = try { repository.readExact(MistakeRevisionKey(question.entryId, question.problemId, question.problemRevisionId)) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { MistakeDetailState.NotFound }
    }
    WorkspacePanel(containerColor = SmartColors.ContentSurface) {
        Text("原题 · 纸卷所用版本", style = MaterialTheme.typography.labelLarge, color = SmartColors.JadeDark)
        when (val state = material) {
            is MistakeDetailState.Ready -> StructuredContentRenderer(state.questionDocument.document, choicesEnabled = false, readOnlyChoices = true,
                storedFigures = state.questionDocument.storedFigureReferences, figureUri = { ref ->
                    (state.detail.source as? MistakeSourceSet.Present)?.assets
                        ?.singleOrNull { it.sourceAssetId == ref.assetId && it.contentSha256 == ref.contentSha256 }
                        ?.location?.let { (it as? MistakeSourceLocation.Available)?.localUri }
                })
            MistakeDetailState.Loading -> Text("正在读取完整题面…", color = SmartColors.InkSecondary)
            else -> {
                Text(ReadableMathText.inlineMarkdown(question.markdown, lineBreakReplacement = '\n'))
                if (state !is MistakeDetailState.Legacy) {
                    WorkspaceNotice("完整题面无法读取，请结合原卷文字核对。", tone = WorkspaceNoticeTone.WARNING)
                    InlineActionButton("重新读取题面", { retry++ })
                }
            }
        }
        val source = when (val state = material) {
            is MistakeDetailState.Ready -> state.detail.source
            is MistakeDetailState.Legacy -> state.detail.source
            else -> null
        }
        source?.questionImageAssets()?.forEachIndexed { index, asset ->
            val uri = (asset.location as? MistakeSourceLocation.Available)?.localUri
            if (uri != null) {
                PaperDivider()
                Text("原题图片 ${index + 1} · 点击放大", style = MaterialTheme.typography.labelMedium, color = SmartColors.InkSecondary)
                PaperImage(uri, "原题图片 ${index + 1}", onImage)
            }
        }
    }
}

@Composable
internal fun PaperImage(uri: String, label: String, onImage: (String, String) -> Unit) {
    BoundedLocalImage(uri, label, expanded = true, expandedMaxHeight = 380.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "放大$label") { onImage(uri, label) })
}

@Composable
internal fun PaperPhotoCard(photo: PaperReviewPhoto, index: Int, onImage: (String, String) -> Unit) {
    WorkspacePanel(containerColor = SmartColors.ContentSurface) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("作答照片 ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(if (photo.corrected) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                    null, Modifier.size(14.dp), tint = if (photo.corrected) SmartColors.JadeDark else SmartColors.InkSecondary)
                Text(if (photo.corrected) "已订正" else "未订正", color = SmartColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(if (photo.questionOrdinal == null) "整卷照片 · 本题可用" else "本题照片", color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        PaperImage(photo.localUri, "作答照片 ${index + 1}", onImage)
        Text("点击放大查看原始作答", color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PaperCheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (checked) SmartColors.JadeSoft else androidx.compose.ui.graphics.Color.Transparent,
        RoundedCornerShape(12.dp)).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaperResultForm(
    question: PaperReviewQuestion, draft: PaperReviewDraft, correctedPhoto: Boolean,
    hasPhotos: Boolean, busy: Boolean, validTime: Boolean,
    onEdit: ((PaperReviewDraft) -> PaperReviewDraft) -> Unit,
    onTime: () -> Unit, onNow: () -> Unit, onSave: () -> Unit,
    onUpload: () -> Unit, onAskAgent: () -> Unit,
) {
    val needsOriginal = (draft.corrected || correctedPhoto) && !draft.originalVisible && draft.result in listOf(ReviewSelfReportKey.GOOD, ReviewSelfReportKey.HARD)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WorkspacePanel(containerColor = SmartColors.ContentSurface) {
            Text(if (question.applied) "已保存的复习结果" else "核对原始作答", style = MaterialTheme.typography.titleMedium)
            if (question.applied) {
                WorkspaceNotice("${paperResultLabel(question.selectedResult)} · 已计入复习记录", tone = WorkspaceNoticeTone.SUCCESS)
                question.answeredAtEpochMillis?.let { Text("作答于 ${paperDateTime(it)}", color = SmartColors.InkSecondary) }
                if (question.corrected) Text("已订正${if (question.originalVisible) "，原始作答可辨认" else ""}", color = SmartColors.InkSecondary)
                if (question.note.isNotBlank()) Text(question.note)
            } else {
                Text("按订正前的表现选择，确认后计入复习。智能体建议仅供参考。", color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(null, ReviewSelfReportKey.GOOD, ReviewSelfReportKey.HARD, ReviewSelfReportKey.AGAIN).forEach { result ->
                        OutlineActionChip(paperResultLabel(result), { onEdit { it.copy(result = result) } }, selected = draft.result == result, enabled = !busy)
                    }
                }
                PaperCheckRow("本题已经订正", draft.corrected || correctedPhoto, !busy && !correctedPhoto) { value -> onEdit { it.copy(corrected = value) } }
                if (draft.corrected || correctedPhoto) PaperCheckRow("能分清原始作答与订正", draft.originalVisible, !busy) { value -> onEdit { it.copy(originalVisible = value) } }
                if (needsOriginal) WorkspaceNotice("仅有订正答案不能记为独立答对。请补充原始作答，或暂不确认。", tone = WorkspaceNoticeTone.WARNING)
            }
        }
        if (!question.applied) {
            WorkspacePanel(containerColor = SmartColors.ContentSurface) {
                Text("实际作答时间", style = MaterialTheme.typography.titleSmall)
                Text(draft.answeredAt?.let(::paperDateTime) ?: "请选择这道题完成作答的时间", color = SmartColors.InkSecondary)
                Text("首次选择时，同卷未设置时间的题目会共用此时间，之后可逐题调整。", color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlineActionChip("刚刚完成", onNow, enabled = !busy)
                    OutlineActionChip("选择时间", onTime, enabled = !busy)
                }
                if (draft.answeredAt != null && !validTime) WorkspaceNotice("作答时间应在纸卷生成之后、当前时间之前。", tone = WorkspaceNoticeTone.ERROR)
                OutlinedTextField(draft.note, { value -> onEdit { it.copy(note = value.take(4000)) } }, label = { Text("作答或订正说明（选填）") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (!hasPhotos) WorkspaceNotice("请先回传本题作答照片，再保存结果。", tone = WorkspaceNoticeTone.INFO)
            }
            WorkspacePanel(containerColor = SmartColors.SubtleSurface) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InlineActionButton("补充本题照片", onUpload, enabled = !busy)
                    InlineActionButton("请智能体检查", onAskAgent, enabled = !busy && hasPhotos)
                }
                PrimaryActionButton(if (busy) "正在处理…" else if (draft.result == null) "保存为待确认" else "保存结果并继续",
                    onSave, enabled = !busy && !needsOriginal && validTime && hasPhotos, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
internal fun paperResultLabel(result: ReviewSelfReportKey?) = when (result) {
    ReviewSelfReportKey.GOOD -> "独立答对"
    ReviewSelfReportKey.HARD -> "费力答对"
    ReviewSelfReportKey.AGAIN -> "答错或未答出"
    else -> "待确认"
}
internal fun paperDateTime(at: Long) = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(at))
internal fun paperStatus(paper: PaperReview): String = when {
    paper.questions.isNotEmpty() && paper.completedCount == paper.questions.size -> "已完成"
    paper.completedCount > 0 -> "部分已确认"
    paper.photos.isNotEmpty() -> "待核对"
    paper.exportStatus == MistakeExportStatus.FAILED -> "导出失败"
    paper.exportStatus == MistakeExportStatus.RUNNING -> "正在生成"
    else -> "待回传"
}
