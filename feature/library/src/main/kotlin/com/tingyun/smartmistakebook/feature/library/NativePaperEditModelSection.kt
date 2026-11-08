package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspaceNotice
import com.tingyun.smartmistakebook.core.ui.WorkspaceNoticeTone
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

/**
 * 6B-3 自然语言改卷的**卷面页入口**（批次 50）。
 *
 * 它消灭的具体失败：卷面页此前只有逐块手改，学生要改"把第 2 题删掉、第 1 题标题短一点"
 * 得自己点很多次；而直接连模型又会让模型输出变成文档（图片地址/原题/原答案都可能被旁路）。
 *
 * 交互只有一条链：写一句要求 → 生成**预览**（[NativePaperEditPreview]）→ 核对逐条差异 →
 * 采用到卷面 / 取消。失败、取消、命令非法、命令不产生实际变化时只给一句如实说明，
 * 卷面与输入框里的要求都保留；这里不会摆出任何"模型已改好"的假回执。
 */
@Composable
internal fun NativePaperEditModelSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    instruction: String,
    onInstructionChange: (String) -> Unit,
    portAvailable: Boolean,
    consentGranted: Boolean,
    busy: Boolean,
    preview: NativePaperEditPreview.Ready?,
    previewStale: Boolean,
    message: String?,
    messageIsError: Boolean,
    onGenerate: () -> Unit,
    onAdopt: () -> Unit,
    onCancel: () -> Unit,
    onOpenModelSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlineActionChip(
            text = if (expanded) "收起 AI 改卷" else "AI 改卷面（一句话）",
            onClick = onToggle,
            modifier = Modifier.testTag("native_paper_edit_ai_toggle"),
        )
        if (!expanded) return@Column

        Spacer(Modifier.height(6.dp))
        WorkspacePanel(modifier = Modifier.testTag("native_paper_edit_ai_section")) {
            Text(
                text = "用一句话改卷",
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = instruction,
                onValueChange = onInstructionChange,
                label = { Text("例如：删掉第二题，把第一题标题改短一点") },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("native_paper_edit_ai_instruction"),
            )
            Text(
                text = "模型只能移出/加回题、调整顺序、改标题/文字/选项/卷面备注；" +
                    "不能改图，也不影响错题本。先看预览，再决定是否采用。",
                modifier = Modifier.testTag("native_paper_edit_ai_hint"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!consentGranted) {
                WorkspaceNotice(
                    message = "还没有启用模型：先在设置里配置模型，才能让 AI 改卷面。" +
                        "已经写下的要求会保留。",
                    modifier = Modifier.testTag("native_paper_edit_ai_consent"),
                    tone = WorkspaceNoticeTone.WARNING,
                    title = "尚未启用模型",
                )
                onOpenModelSettings?.let { open ->
                    OutlineActionChip(
                        text = "打开模型设置",
                        onClick = open,
                        enabled = !busy,
                        modifier = Modifier.testTag("native_paper_edit_ai_settings"),
                    )
                }
            }
            PrimaryActionButton(
                text = if (busy) "正在等模型给出修改…" else "生成修改预览",
                onClick = onGenerate,
                enabled = portAvailable && consentGranted && instruction.isNotBlank() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("native_paper_edit_ai_generate"),
            )
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("native_paper_edit_ai_busy"),
                    color = SmartColors.LearningAccent,
                )
                Text(
                    text = "模型正在按你的要求修改卷面副本；失败或取消时卷面保持原样。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            message?.let { text ->
                WorkspaceNotice(
                    message = text,
                    modifier = Modifier.testTag("native_paper_edit_ai_message"),
                    tone = if (messageIsError) WorkspaceNoticeTone.ERROR else WorkspaceNoticeTone.INFO,
                )
            }
            preview?.let { ready ->
                PreviewBlock(ready, previewStale, onAdopt, onCancel)
            }
        }
    }
}

@Composable
private fun PreviewBlock(
    preview: NativePaperEditPreview.Ready,
    stale: Boolean,
    onAdopt: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = "修改预览 · ${preview.commandCount} 条命令 · ${preview.diffs.size} 处变化",
        modifier = Modifier.testTag("native_paper_edit_ai_preview"),
        color = Ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
    )
    preview.diffs.forEachIndexed { index, diff ->
        PreviewRow(index, diff)
    }
    if (preview.figureReviewEntryIds.isNotEmpty()) {
        WorkspaceNotice(
            message = "有 ${preview.figureReviewEntryIds.size} 道题的文字改过，原图需复核，不会自动重画。",
            modifier = Modifier.testTag("native_paper_edit_ai_figure_review"),
            tone = WorkspaceNoticeTone.WARNING,
        )
    }
    if (stale) {
        WorkspaceNotice(
            message = "卷面在预览后被手动改过，这次预览不能采用；请重新生成。",
            modifier = Modifier.testTag("native_paper_edit_ai_stale"),
            tone = WorkspaceNoticeTone.WARNING,
        )
    }
    PrimaryActionButton(
        text = "采用到卷面",
        onClick = onAdopt,
        enabled = !stale,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("native_paper_edit_ai_adopt"),
    )
    OutlineActionChip(
        text = "取消",
        onClick = onCancel,
        modifier = Modifier.testTag("native_paper_edit_ai_cancel"),
    )
}

@Composable
private fun PreviewRow(index: Int, diff: NativePaperEditDiff) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("native_paper_edit_ai_diff_$index"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${diff.questionLabel} · ${diff.change}",
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
        )
        val before = diff.before
        val after = diff.after
        if (before != null) {
            Text(
                text = "改前：${clipForPreview(before)}",
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (after != null) {
            Text(
                text = if (before == null) "加回：${clipForPreview(after)}" else "改后：${clipForPreview(after)}",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 预览只用于核对，长文字在卡片里截断；完整内容仍在下面的逐块编辑器里。 */
private fun clipForPreview(text: String): String {
    val oneLine = text.replace('\n', ' ').trim()
    return if (oneLine.length <= 120) oneLine else oneLine.take(120) + "…"
}
