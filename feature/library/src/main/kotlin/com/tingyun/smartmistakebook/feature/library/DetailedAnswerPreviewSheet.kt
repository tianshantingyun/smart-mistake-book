package com.tingyun.smartmistakebook.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.DetailedSolutionPreparation
import com.tingyun.smartmistakebook.core.domain.MistakeExportGroup
import com.tingyun.smartmistakebook.core.model.*
import com.tingyun.smartmistakebook.core.ui.*

@Composable
fun DetailedAnswerPreviewSheet(
    group: MistakeExportGroup?,
    preparation: DetailedSolutionPreparation?,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onConfirm: (AnswerPreviewApproval) -> Unit,
    onSaveQuestions: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onChangeFormat: (MistakeExportFormat) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    var acknowledged by rememberSaveable(group?.manifestSha256, preparation?.solutionsSha256, group?.format?.token) { mutableStateOf(false) }
    val ready = preparation?.complete == true
    val stopped = preparation?.status in setOf(DetailedSolutionPreparationStatus.FAILED, DetailedSolutionPreparationStatus.CANCELLED)
    val completed = preparation?.items?.count { it.status == DetailedSolutionPreparationStatus.READY } ?: 0
    WorkspaceSheet("详细答案预览", onDismiss, icon = Icons.Outlined.MenuBook, subtitle = "题目和答案分别生成文件，可分别保存和分享。", actions = {
        if (ready && group != null) ClaudeButton({
            onConfirm(AnswerPreviewApproval(group.manifestSha256, requireNotNull(preparation.solutionsSha256), group.format))
        }, Modifier.fillMaxWidth(), enabled = acknowledged && !busy) { Text("确认并导出题目与答案") }
        else if (preparation?.status == DetailedSolutionPreparationStatus.STALE) ClaudeSecondaryButton(onDismiss, Modifier.fillMaxWidth()) { Text("返回重新准备") }
        else if (stopped) ClaudeButton(onRetry, Modifier.fillMaxWidth(), enabled = !busy) { Text("重试未完成的答案") }
        else ClaudeButton({}, Modifier.fillMaxWidth(), enabled = false) { Text("正在准备答案 $completed/${preparation?.items?.size ?: 0}") }
        ClaudeSecondaryButton(onSaveQuestions, Modifier.fillMaxWidth(), enabled = group != null && !busy) { Text("先导出题目") }
    }) {
        if (group == null || preparation == null) {
            if (message == null) {
                CircularProgressIndicator(color = SmartColors.LearningAccent)
                Text("正在读取本次卷面…", color = InkSecondary)
            }
        } else {
            ClaudeSegmentedControl(
                segments = MistakeExportFormat.entries.map { ClaudeSegment(it.token, it.studentLabel) },
                selectedId = group.format.token,
                onSelect = { token -> MistakeExportFormat.entries.firstOrNull { it.token == token }?.let(onChangeFormat) },
                enabled = !busy,
            )
            if (group.format == MistakeExportFormat.DOCX) Text(mistakeExportFormatDocxNotice, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            Surface(color = SmartColors.LearningSurface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ready) "${preparation.items.size} 题答案已准备好" else "已准备 $completed/${preparation.items.size} 题",
                        style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text("按本次组卷题号排列。", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    if (!ready && !stopped && preparation.status != DetailedSolutionPreparationStatus.STALE) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = SmartColors.LearningAccent)
                        ClaudeTextButton(onStop, enabled = !busy) { Text("停止准备") }
                    }
                }
            }
            if (stopped && onOpenSettings != null) ClaudeTextButton(onOpenSettings, enabled = !busy) { Text("模型设置") }
            preparation.items.forEach { item ->
                var expanded by rememberSaveable(group.manifestId, item.ordinal) { mutableStateOf(item.ordinal == 1) }
                val canExpand = item.document != null
                MistakeSectionCard(
                    title = "第 ${item.ordinal} 题",
                    headerColor = SmartColors.LearningSurface,
                ) {
                    Row(Modifier.fillMaxWidth().then(if (canExpand) Modifier.clickable(role = Role.Button) { expanded = !expanded } else Modifier)
                        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when (item.status) {
                                DetailedSolutionPreparationStatus.READY -> "已准备好"
                                DetailedSolutionPreparationStatus.QUEUED -> "等待准备"
                                DetailedSolutionPreparationStatus.RUNNING -> "正在准备"
                                DetailedSolutionPreparationStatus.CANCELLED -> "已停止"
                                DetailedSolutionPreparationStatus.FAILED -> item.message ?: "准备失败，可重试"
                                DetailedSolutionPreparationStatus.STALE -> "内容已失效，请重新准备"
                            }, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                        if (canExpand) Text(if (expanded) "收起" else "查看", color = SmartColors.LearningAccent, style = MaterialTheme.typography.labelLarge)
                    }
                    ClaudeDisclosure(canExpand && expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item.document?.let { document ->
                                document.teachingFigureOmission?.let { omission ->
                                    WorkspaceNotice(
                                        "${omission.reason}\n${omission.jsLimitation}",
                                        tone = WorkspaceNoticeTone.INFO,
                                    )
                                }
                                StructuredContentRenderer(document.steps, choicesEnabled = false, storedFigures = document.figures,
                                    figureUri = { item.figureUris[it.assetId] }, showStoredFigureCaptions = true)
                                Text("最终答案", style = MaterialTheme.typography.titleSmall, color = Ink)
                                com.tingyun.smartmistakebook.core.ui.TutorReplyMarkdown(document.finalAnswerMarkdown)
                            }
                        }
                    }
                }
            }
            if (ready) Row(Modifier.fillMaxWidth().toggleable(acknowledged, enabled = !busy, role = Role.Checkbox, onValueChange = { acknowledged = it }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(acknowledged, null, enabled = !busy)
                Text("我已查看这份答案，按当前格式导出", Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        message?.let { WorkspaceNotice(it, tone = WorkspaceNoticeTone.ERROR) }
    }
}
