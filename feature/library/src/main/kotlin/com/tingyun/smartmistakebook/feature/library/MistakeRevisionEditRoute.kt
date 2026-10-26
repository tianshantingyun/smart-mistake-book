package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.heightIn
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
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
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
import com.tingyun.smartmistakebook.core.ui.ReferenceEditorPage
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
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

    ReferenceEditorPage(
        modifier = modifier.testTag("mistake_revision_edit_screen"),
        imageUris = if (state.loading || state.unavailableReason != null) emptyList() else
            state.source.detailAssets().question.mapNotNull { (it.location as? MistakeSourceLocation.Available)?.localUri },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
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
            return@ReferenceEditorPage
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
            return@ReferenceEditorPage
        }

        if (state.hasInProgressOldConversation) {
            RevisionNotice(
                text = "这道题已有讲解。保存修改后，原讲解和草稿会留在旧版，" +
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
                        text = "这里只能修改图形的文字说明，不能重画图形。" +
                            "旧版图形与解析保留在旧版本，不会被新版覆盖。",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("mistake_revision_edit_figure_notice_$index"),
                    )
                }
                RevisionEditBlockKind.UNKNOWN -> Text(
                    text = "暂不支持编辑的内容会保留原样。",
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
                            "暂无修改。"
                        },
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.diff.forEach { row ->
                        com.tingyun.smartmistakebook.core.ui.ClaudeRevisionDiff(row.label, row.before, row.after)
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
            dangerConfirm = true,
            title = { Text("放弃未保存的修改？") },
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
