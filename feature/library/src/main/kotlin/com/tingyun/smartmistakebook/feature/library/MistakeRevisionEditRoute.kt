package com.tingyun.smartmistakebook.feature.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SectionHeader

/**
 * Minimal editor for one saved question. It is deliberately a full page (not a dialog) so the
 * learner can compare long stems, but it is only reachable from the detail page — capture never
 * forces an edit step before saving a new mistake.
 */
@Composable
fun MistakeRevisionEditRoute(
    entryId: String,
    repository: MistakeDetailRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MistakeRevisionEditViewModel = viewModel(
        key = "mistake-revision-edit:$entryId",
        factory = MistakeRevisionEditViewModel.factory(entryId, repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.onEditorOpened() }

    fun leave() {
        if (state.dirty) confirmDiscard = true else onBack()
    }
    BackHandler { leave() }

    RootPageColumn(
        modifier = modifier.testTag("mistake_revision_edit_screen"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { leave() },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mistake_revision_edit_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回详情",
                    tint = Ink,
                )
            }
            Text(
                text = "修改这道题",
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        state.unavailableReason?.let { reason ->
            Text(
                text = reason,
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("mistake_revision_edit_unavailable"),
            )
            return@RootPageColumn
        }
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp)
                    .testTag("mistake_revision_edit_loading"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = JadeActive)
                Spacer(Modifier.height(10.dp))
                Text("正在读取当前题面…", color = InkSecondary)
            }
            return@RootPageColumn
        }

        if (state.hasInProgressOldConversation) {
            RevisionNotice(
                text = "这道题有一轮绑定当前版本的讲解。保存后，旧讲解与草稿会保留在旧版本里，" +
                    "不会带入新版本；新版讲解需要重新开始。",
                tag = "mistake_revision_edit_old_session_notice",
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChanged,
            label = { Text("标题") },
            singleLine = true,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mistake_revision_edit_title"),
        )

        state.blocks.forEachIndexed { index, block ->
            Spacer(Modifier.height(14.dp))
            when (block.kind) {
                RevisionEditBlockKind.STEM -> OutlinedTextField(
                    value = block.text,
                    onValueChange = { viewModel.onBlockTextChanged(block.id, it) },
                    label = { Text("题干") },
                    minLines = 3,
                    maxLines = 12,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_revision_edit_block_$index"),
                )
                RevisionEditBlockKind.FORMULA -> {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { viewModel.onBlockTextChanged(block.id, it) },
                        label = { Text("公式 LaTeX") },
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mistake_revision_edit_formula_$index"),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = block.secondaryText,
                        onValueChange = { viewModel.onBlockSecondaryChanged(block.id, it) },
                        label = { Text("公式的文字说明") },
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mistake_revision_edit_formula_alt_$index"),
                    )
                }
                RevisionEditBlockKind.CHOICE -> {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { viewModel.onBlockTextChanged(block.id, it) },
                        label = { Text("选项题干") },
                        minLines = 2,
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mistake_revision_edit_choices_prompt_$index"),
                    )
                    block.choices.forEachIndexed { choiceIndex, choice ->
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = choice.markdown,
                            onValueChange = {
                                viewModel.onChoiceChanged(block.id, choiceIndex, it)
                            },
                            label = { Text("选项 ${choiceLabel(choiceIndex)}") },
                            enabled = !state.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "mistake_revision_edit_choice_${block.id}_$choiceIndex",
                                ),
                        )
                    }
                }
                RevisionEditBlockKind.FIGURE -> {
                    OutlinedTextField(
                        value = block.secondaryText,
                        onValueChange = { viewModel.onBlockSecondaryChanged(block.id, it) },
                        label = { Text("图形标题") },
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mistake_revision_edit_figure_title_$index"),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { viewModel.onBlockTextChanged(block.id, it) },
                        label = { Text("图形的文字含义") },
                        minLines = 2,
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mistake_revision_edit_figure_alt_$index"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "图形本身不在这里重画。需要新图时走统一生图（本批未接）；" +
                            "旧版图形与解析保留在旧版本，不会被新版覆盖。",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("mistake_revision_edit_figure_notice_$index"),
                    )
                }
                RevisionEditBlockKind.UNKNOWN -> Text(
                    text = "未识别的内容块保持原样，不在这里修改。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("将保存的修改")
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mistake_revision_edit_diff"),
            shape = RoundedCornerShape(10.dp),
            color = JadeSoft.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, Outline),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.diff.isEmpty()) {
                    Text(
                        text = if (state.savedRevisionNumber != null) {
                            "已保存为第 ${state.savedRevisionNumber} 版。"
                        } else {
                            "还没有修改。"
                        },
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.diff.forEach { row ->
                        Text(
                            text = row.label,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "修改前：${row.before?.takeIf(String::isNotBlank) ?: "（无）"}",
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "修改后：${row.after?.takeIf(String::isNotBlank) ?: "（无）"}",
                            color = Ink,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        state.conflict?.let { conflict ->
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mistake_revision_edit_conflict"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = conflict,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::reload,
                        modifier = Modifier.testTag("mistake_revision_edit_reload"),
                    ) {
                        Text("重新载入当前版本")
                    }
                }
            }
        }

        state.message?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("mistake_revision_edit_message"),
            )
        }
        state.invalidationSummary?.let { summary ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("mistake_revision_edit_invalidated"),
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryActionButton(
            text = if (state.saving) "保存中…" else "保存修改",
            onClick = viewModel::save,
            enabled = !state.saving && state.dirty && state.conflict == null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mistake_revision_edit_save"),
            contentDescription = "保存为新的题面版本",
        )
        if (state.savedRevisionNumber != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mistake_revision_edit_done"),
            ) {
                Text("完成，返回详情")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃还没保存的修改？") },
            text = { Text("这道题的已保存版本不会受影响；只有这次尚未保存的修改会丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onBack()
                    },
                    modifier = Modifier.testTag("mistake_revision_edit_discard_confirm"),
                ) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun RevisionNotice(text: String, tag: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Outline),
    ) {
        Text(
            text = text,
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun choiceLabel(index: Int): String =
    if (index in 0..25) ('A'.code + index).toChar().toString() else "${index + 1}"
