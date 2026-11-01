package com.tingyun.smartmistakebook.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
) {
    var instruction by rememberSaveable { mutableStateOf("") }
    var targets by rememberSaveable { mutableStateOf(state.blocks.filter { it.kind != RevisionEditBlockKind.UNKNOWN }.take(32).map { it.id }.toList()) }
    var sources by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editTitle by rememberSaveable { mutableStateOf(false) }
    var showAfter by rememberSaveable { mutableStateOf(true) }
    val status = state.agentJob?.status
    val busy = status in setOf(MistakeEditJobStatus.QUEUED, MistakeEditJobStatus.REQUESTING, MistakeEditJobStatus.GENERATING_FIGURES)
    val ready = status == MistakeEditJobStatus.READY && state.agentPreview != null
    WorkspaceSheet("智能体修改", onDismiss, subtitle = "先预览再采用，保存后才会更新题目。", actions = {
        if (ready) ClaudeButton(onAdopt, Modifier.fillMaxWidth()) { Text("采用修改") }
        else if (busy) ClaudeSecondaryButton(onCancel, Modifier.fillMaxWidth()) { Text("停止修改") }
        else ClaudeButton({ onRequest(targets.toSet(), instruction, sources.toSet(), editTitle) }, Modifier.fillMaxWidth(),
            enabled = instruction.isNotBlank() && (targets.isNotEmpty() || editTitle) && !state.saving) { Text("生成修改建议") }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = SmartColors.LearningAccent)
            Text("修正文字、公式或配图", style = MaterialTheme.typography.titleSmall, color = Ink)
        }
        if (!busy && !ready) {
            Text("修改范围 · 已选 ${targets.size}/32 项", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
            AgentEditCheckRow("标题", editTitle) { editTitle = !editTitle }
            state.blocks.filter { it.kind != RevisionEditBlockKind.UNKNOWN }.forEachIndexed { index, block ->
                AgentEditCheckRow("${index + 1}. ${block.text.take(55).ifBlank { "配图或公式" }}", block.id in targets, enabled = block.id in targets || targets.size < 32) {
                    targets = if (block.id in targets) targets - block.id else targets + block.id
                }
            }
            val sourceAssets = (state.source as? MistakeSourceSet.Present)?.assets.orEmpty()
                .filter { it.location is MistakeSourceLocation.Available }.distinctBy { it.sourceAssetId }
            if (sourceAssets.isNotEmpty()) {
                Text("参考图片 · 修改配图时请选择对应源图", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                sourceAssets.forEachIndexed { index, asset ->
                    AgentEditCheckRow("图片 ${index + 1}", asset.sourceAssetId in sources, enabled = asset.sourceAssetId in sources || sources.size < 8) {
                        sources = if (asset.sourceAssetId in sources) sources - asset.sourceAssetId else sources + asset.sourceAssetId
                    }
                }
            }
            OutlinedTextField(instruction, { instruction = it.take(4_000) }, Modifier.fillMaxWidth(),
                label = { Text("希望怎样修改") }, placeholder = { Text("例如：修正公式下标，并把配图中的力标注清楚") }, minLines = 3, maxLines = 6)
        }
        state.agentJob?.let { job ->
            Surface(color = SmartColors.LearningSurface, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().animateContentSize()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SmartColors.LearningAccent)
                    Text(job.message ?: "正在准备修改建议", style = MaterialTheme.typography.bodyMedium, color = Ink)
                    if (status in setOf(MistakeEditJobStatus.FAILED, MistakeEditJobStatus.UNAVAILABLE, MistakeEditJobStatus.NEED_SOURCE, MistakeEditJobStatus.CANCELLED)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClaudeTextButton(onRetry) { Text("重试") }
                            onOpenSettings?.let { open -> ClaudeTextButton(open) { Text("模型设置") } }
                        }
                    }
                }
            }
        }
        state.message?.let { Text(it, color = InkSecondary, style = MaterialTheme.typography.bodySmall) }
        if (ready) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showAfter, { showAfter = false }, { Text("修改前") })
                FilterChip(showAfter, { showAfter = true }, { Text("修改后") })
            }
            AnimatedContent(showAfter, label = "agent-edit-comparison") { after ->
                val document = if (after) state.agentPreview else state.agentBefore
                document?.let {
                    StructuredContentRenderer(it.document, choicesEnabled = false, storedFigures = it.storedFigureReferences,
                        figureUri = { ref -> state.agentJob?.figureUris?.get(ref.assetId) ?: state.adoptedFigureUris[ref.assetId] })
                }
            }
        }
    }
}

@Composable
private fun AgentEditCheckRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}
