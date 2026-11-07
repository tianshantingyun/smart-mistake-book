package com.tingyun.smartmistakebook.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.model.MistakeEditJobStatus
import com.tingyun.smartmistakebook.core.model.ReadableMathText
import com.tingyun.smartmistakebook.core.ui.*

@Composable
internal fun MistakeAgentEditSheet(
    state: MistakeRevisionEditUiState,
    onDismiss: () -> Unit,
    onRequest: (Set<String>, String, Set<String>, Boolean) -> Unit,
    onAdopt: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onInstructionChanged: (String) -> Unit = {},
) {
    val editableBlocks = state.blocks.filter { it.kind != RevisionEditBlockKind.UNKNOWN }
    var instruction by rememberSaveable { mutableStateOf(state.agentInstructionDraft) }
    var targets by rememberSaveable { mutableStateOf(editableBlocks.take(32).map { it.id }) }
    var sources by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editTitle by rememberSaveable { mutableStateOf(false) }
    var showAfter by rememberSaveable { mutableStateOf(true) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var viewerUri by remember { mutableStateOf<String?>(null) }
    val status = state.agentJob?.status
    val busy = status in setOf(MistakeEditJobStatus.QUEUED, MistakeEditJobStatus.REQUESTING, MistakeEditJobStatus.GENERATING_FIGURES)
    val ready = state.agentPreview != null
    val actionBusy = state.agentAction != null
    val retryable = status in setOf(MistakeEditJobStatus.FAILED, MistakeEditJobStatus.UNAVAILABLE,
        MistakeEditJobStatus.NEED_SOURCE, MistakeEditJobStatus.CANCELLED)
    val sourceAssets = (state.source as? MistakeSourceSet.Present)?.assets.orEmpty()
        .filter { it.location is MistakeSourceLocation.Available }.distinctBy { it.sourceAssetId }
    LaunchedEffect(state.agentJob?.operationId) { adjusting = false }
    viewerUri?.let { uri -> LocalImageViewer(uri, "参考图片", { viewerUri = null }) }
    WorkspaceSheet("智能体修改", onDismiss, icon = Icons.Outlined.AutoAwesome,
        subtitle = "采用后请在编辑页保存。", actions = {
        when {
            state.agentAdopting -> ClaudeButton({}, Modifier.fillMaxWidth(), enabled = false) { Text("正在采用到草稿…") }
            busy -> ClaudeSecondaryButton(onCancel, Modifier.fillMaxWidth(), enabled = !actionBusy) {
                Text(state.agentAction ?: "停止本次生成")
            }
            ready && !adjusting -> {
                ClaudeButton(onAdopt, Modifier.fillMaxWidth(), enabled = !actionBusy && !state.saving) { Text("采用到草稿") }
                ClaudeTextButton({ adjusting = true }, enabled = !actionBusy) { Text("返回调整要求") }
            }
            else -> {
                ClaudeButton({ onRequest(targets.toSet(), instruction, sources.toSet(), editTitle) }, Modifier.fillMaxWidth(),
                    enabled = !actionBusy && state.agentConfigured && instruction.isNotBlank() &&
                        (targets.isNotEmpty() || editTitle) && !state.saving) {
                    Text(state.agentAction ?: if (ready) "按新要求生成 · 替换此预览" else "按当前要求生成修改")
                }
                if (ready) ClaudeTextButton({ adjusting = false }, enabled = !actionBusy) { Text("返回已有预览") }
            }
        }
    }) {
        if (!state.agentConfigured) {
            WorkspaceNotice("请先在模型设置中启用智能体，再返回提交修改要求。已输入的要求会保留。", tone = WorkspaceNoticeTone.WARNING,
                title = "尚未启用智能体")
            onOpenSettings?.let { open -> ClaudeSecondaryButton(open, enabled = !actionBusy) { Text("打开模型设置") } }
        }
        if (!busy && (!ready || adjusting)) {
            OutlinedTextField(instruction, { instruction = it.take(4_000); onInstructionChanged(instruction) }, Modifier.fillMaxWidth(),
                enabled = !actionBusy, label = { Text("希望怎样修改") },
                placeholder = { Text("例如：把第（2）问表述改清楚，保留题意；或修正公式、配图标注") }, minLines = 3, maxLines = 6,
                supportingText = { Text("${instruction.length}/4000 · 修改要求越具体，越便于核对") })
            if (ready) WorkspaceNotice("新修改生成后将替换预览，之前仍可返回查看。")
            ClaudeSecondaryButton({ showTargets = !showTargets }, Modifier.fillMaxWidth(), enabled = !actionBusy) {
                Text("修改范围 · 已选 ${targets.size}/${editableBlocks.size} 处${if (editTitle) "及标题" else ""} · ${if (showTargets) "收起" else "展开"}")
            }
            ClaudeDisclosure(showTargets) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentEditCheckRow("标题", editTitle, enabled = !actionBusy) { editTitle = !editTitle }
                if (editableBlocks.size > 32) Text("每次最多修改 32 处。", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                editableBlocks.forEachIndexed { index, block ->
                    val preview = remember(block) {
                        when (block.kind) {
                            RevisionEditBlockKind.CHOICE -> "选项 · 共 ${block.choices.size} 项"
                            RevisionEditBlockKind.FORMULA -> "公式 · ${ReadableMathText.formula(block.text).take(55)}"
                            RevisionEditBlockKind.FIGURE -> "配图 · ${block.text.take(55).ifBlank { "题目配图" }}"
                            else -> "题干 · ${ReadableMathText.inlineMarkdown(block.text).take(55)}"
                        }
                    }
                    AgentEditCheckRow("${index + 1}. $preview", block.id in targets,
                        enabled = !actionBusy && (block.id in targets || targets.size < 32)) {
                        targets = if (block.id in targets) targets - block.id else targets + block.id
                    }
                }
            } }
            ClaudeSecondaryButton({ showSources = !showSources }, Modifier.fillMaxWidth(), enabled = !actionBusy) {
                val selection = if (sourceAssets.isEmpty()) "暂无图片" else "已选 ${sources.size}/${sourceAssets.size} 张"
                Text("参考图片 · $selection · ${if (showSources) "收起" else "展开"}")
            }
            ClaudeDisclosure(showSources) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请选择要修改的源图。", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                if (sourceAssets.size > 8) Text("每次最多参考 8 张图片。", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                if (sourceAssets.isEmpty()) WorkspaceNotice("暂无可用图片，可修改文字和公式。", tone = WorkspaceNoticeTone.WARNING)
                sourceAssets.forEachIndexed { index, asset ->
                    val label = "图片 ${index + 1} · " + when (asset.role) {
                        "QUESTION_SOURCE", "SOURCE_EVIDENCE" -> "原题图片"
                        "CLEAN_IMAGE" -> "整理后的题图"
                        "QUESTION_DIAGRAM" -> "题目配图"
                        "TUTOR_EXPLANATION" -> "讲解配图"
                        else -> "参考图片"
                    }
                    val uri = (asset.location as MistakeSourceLocation.Available).localUri
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AgentEditCheckRow(label, asset.sourceAssetId in sources,
                            enabled = !actionBusy && (asset.sourceAssetId in sources || sources.size < 8)) {
                            sources = if (asset.sourceAssetId in sources) sources - asset.sourceAssetId else sources + asset.sourceAssetId
                        }
                        BoundedLocalImage(uri, label, expanded = true, expandedMaxHeight = 180.dp, modifier = Modifier.fillMaxWidth())
                        ClaudeTextButton({ viewerUri = uri }) { Text("放大查看图片 ${index + 1}") }
                    }
                }
            } }
        }
        state.agentJob?.let { job ->
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SmartColors.LearningAccent)
                WorkspaceNotice("${job.message ?: "正在生成修改"}。关闭此面板仅离开查看，任务会继续；需要中止请点“停止本次生成”。")
            } else if (retryable || status == MistakeEditJobStatus.SUPERSEDED) {
                WorkspaceNotice(job.message ?: "本次修改未完成，请调整要求后重新生成，或重试上次请求。",
                    tone = if (status == MistakeEditJobStatus.CANCELLED) WorkspaceNoticeTone.WARNING else WorkspaceNoticeTone.ERROR)
            }
            if (busy || ready || retryable) {
                Text("上次已提交的要求", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                Text(state.agentSubmittedInstruction ?: "已恢复历史请求，重试会沿用当时保存的要求与范围。",
                    style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            if (retryable) {
                Text("重试沿用上次要求；上方输入框的改动请使用“按当前要求生成修改”。", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                ClaudeSecondaryButton(onRetry, enabled = !actionBusy && !state.saving && state.agentConfigured) { Text("重试上次请求") }
                onOpenSettings?.let { open -> ClaudeTextButton(open, enabled = !actionBusy) { Text("检查模型设置") } }
            }
        }
        state.agentError?.let { WorkspaceNotice(it, tone = WorkspaceNoticeTone.ERROR) }
        if (ready && !adjusting) {
            WorkspaceNotice("核对后采用到草稿，再保存题面。", tone = WorkspaceNoticeTone.SUCCESS, title = "修改预览已就绪")
            ClaudeSegmentedControl(
                segments = listOf(ClaudeSegment("before", "修改前"), ClaudeSegment("after", "修改后")),
                selectedId = if (showAfter) "after" else "before",
                onSelect = { showAfter = it == "after" },
            )
            @Composable fun Comparison(after: Boolean) {
                val document = if (after) state.agentPreview else state.agentBefore
                document?.let {
                    StructuredContentRenderer(it.document, choicesEnabled = false, storedFigures = it.storedFigureReferences,
                        figureUri = { ref ->
                            state.agentJob?.figureUris?.get(ref.assetId) ?: state.adoptedFigureUris[ref.assetId] ?:
                                ((state.source as? MistakeSourceSet.Present)?.assets?.singleOrNull { asset ->
                                    asset.sourceAssetId == ref.assetId && asset.contentSha256 == ref.contentSha256 && asset.role == ref.role.name
                                }?.location as? MistakeSourceLocation.Available)?.localUri
                        })
                }
            }
            MistakeSectionCard(if (showAfter) "修改后题面" else "原题面", headerColor = SmartColors.AgentSurface) {
                if (LocalClaudeMotionEnabled.current) AnimatedContent(
                    showAfter,
                    transitionSpec = {
                        fadeIn(tween(ClaudeMotion.RevealMillis)) togetherWith fadeOut(tween(ClaudeMotion.SnapMillis))
                    },
                    label = "agent-edit-comparison",
                ) { Comparison(it) }
                else Comparison(showAfter)
            }
        }
    }
}

@Composable
private fun AgentEditCheckRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(if (checked) SmartColors.JadeSoft else SmartColors.SubtleSurface)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}
