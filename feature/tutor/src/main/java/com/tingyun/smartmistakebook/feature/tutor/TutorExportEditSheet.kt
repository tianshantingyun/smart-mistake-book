package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import com.tingyun.smartmistakebook.core.ui.WorkspaceSheet
import com.tingyun.smartmistakebook.core.ui.WorkspaceNotice
import com.tingyun.smartmistakebook.core.ui.WorkspaceNoticeTone
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.ContentSurface
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton


import com.tingyun.smartmistakebook.core.ui.ErrorText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.export.MistakePaperDraft
import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperQuestion
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton

/**
 * 卷面编辑态（6B-2）：选题之后、真正入队之前的那一步。
 *
 * 学生在这里对**卷面模型**做增删题 / 调序 / 改文字；确认时 [MistakePdfPaperEdit.toDraft]
 * 产出已定稿草稿，交给既有后台导出管线重渲染（PDF 与 docx 复用 6B-1 渲染器）。界面不改
 * 错题条目，也不提供"重画图"——图保留原图，题干改动后如实提示复核。
 */
@Composable
internal fun TutorExportEditSheet(
    edit: MistakePdfPaperEdit,
    format: MistakeExportFormat,
    onMove: (entryId: String, offset: Int) -> Unit,
    onRemove: (entryId: String) -> Unit,
    onRestore: (entryId: String) -> Unit,
    onTitleChange: (entryId: String, title: String) -> Unit,
    onBlockTextChange: (entryId: String, blockId: String, text: String) -> Unit,
    onChoiceTextChange: (entryId: String, blockId: String, choiceId: String, text: String) -> Unit,
    onNoteChange: (entryId: String, note: String) -> Unit,
    /** 6B-3：端口接上时才渲染自然语言改卷；null 端口 = 不显示。 */
    modelEditAvailable: Boolean,
    instruction: String,
    modelEditBusy: Boolean,
    modelEditMessage: String?,
    modelEditMessageIsError: Boolean,
    onInstructionChange: (String) -> Unit,
    onModelEdit: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (MistakePaperDraft) -> Unit,
    onDismiss: () -> Unit,
    includeDetailedAnswers: Boolean = false,
    onDetailedAnswersChange: ((Boolean) -> Unit)? = null,
    onPrepareDetailedAnswers: ((MistakePaperDraft) -> Unit)? = null,
) {
    WorkspaceSheet(
        title = "编辑卷面",
        subtitle = "共 ${edit.questions.size} 题 · 版式：${exportLayoutSummary(edit.layout)}",
        subtitleModifier = Modifier.testTag("tutor_export_edit_summary"),
        icon = Icons.Outlined.EditNote,
        onDismiss = { if (!modelEditBusy) onDismiss() },
        modifier = Modifier.testTag("tutor_export_edit_sheet"),
        actions = {
            PrimaryActionButton(
                text = if (includeDetailedAnswers) "准备答案并预览（${edit.questions.size} 题）" else "确认导出（${edit.questions.size} 题）",
                onClick = { if (includeDetailedAnswers && onPrepareDetailedAnswers != null) onPrepareDetailedAnswers(edit.toDraft()) else onConfirm(edit.toDraft()) },
                // 模型改卷在途时不允许抢跑：否则会导出改到一半之前的副本。
                enabled = !edit.hasBlankText && !modelEditBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tutor_export_edit_confirm"),
            )
            OutlineActionChip(
                text = "返回选题",
                enabled = !modelEditBusy,
                onClick = onBack,
                modifier = Modifier.testTag("tutor_export_edit_back"),
            )
            ClaudeTextButton(
                enabled = !modelEditBusy,
                onClick = onDismiss,
                modifier = Modifier.testTag("tutor_export_edit_dismiss"),
            ) { Text("先不用") }
        },
    ) {
        WorkspaceNotice(
            message = "仅修改导出卷面，错题本保持不变。" +
                exportFormatNotice(format)?.let { "\n$it" }.orEmpty(),
            modifier = Modifier.testTag("tutor_export_edit_notice"),
            tone = WorkspaceNoticeTone.INFO,
        )

        // 6B-3：自然语言改卷（只在这条入口接上模型端口时出现）。
        if (modelEditAvailable) {
            WorkspacePanel(containerColor = SmartColors.AgentSurface) {
                Text(
                    text = "用一句话改卷面",
                    modifier = Modifier.testTag("tutor_export_edit_ai_section"),
                    color = SmartColors.AgentAccent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = instruction,
                    onValueChange = onInstructionChange,
                    label = { Text("例如：删掉第二题，把第一题标题改短一点") },
                    enabled = !modelEditBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutor_export_edit_ai_instruction"),
                )
                Text(
                    text = "AI 仅能移出题目、加回已移出的题、调整顺序和" +
                        "修改文字。修改备注需开启「含备注」。不能改图，也不影响错题本。",
                    modifier = Modifier.testTag("tutor_export_edit_ai_hint"),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                PrimaryActionButton(
                    text = if (modelEditBusy) "正在改卷面…" else "让 AI 改卷面",
                    onClick = onModelEdit,
                    enabled = instruction.isNotBlank() && !modelEditBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutor_export_edit_ai_apply"),
                )
            }
        }
        modelEditMessage?.let { message ->
            WorkspaceNotice(
                message = message,
                modifier = Modifier.testTag("tutor_export_edit_ai_message"),
                tone = if (modelEditMessageIsError) WorkspaceNoticeTone.ERROR else WorkspaceNoticeTone.INFO,
            )
        }

        if (edit.omittedCount > 0) {
            WorkspaceNotice(
                message = "另有 ${edit.omittedCount} 道暂时不能导出，已略过。",
                modifier = Modifier.testTag("tutor_export_edit_omitted"),
                tone = WorkspaceNoticeTone.WARNING,
            )
        }

        edit.questions.forEachIndexed { index, question ->
            PaperQuestionEditor(
                index = index,
                question = question,
                canMoveUp = index > 0,
                canMoveDown = index < edit.questions.lastIndex,
                canRemove = edit.canRemoveQuestion,
                figureReviewRequired = edit.figureReviewRequired(question.entryId),
                onMove = { offset -> onMove(question.entryId, offset) },
                onRemove = { onRemove(question.entryId) },
                onTitleChange = { title ->
                    onTitleChange(question.entryId, title)
                },
                onBlockTextChange = { blockId, text ->
                    onBlockTextChange(question.entryId, blockId, text)
                },
                onChoiceTextChange = { blockId, choiceId, text ->
                    onChoiceTextChange(
                        question.entryId,
                        blockId,
                        choiceId,
                        text,
                    )
                },
                includeNote = edit.layout.includeNote,
                onNoteChange = { note -> onNoteChange(question.entryId, note) },
            )
        }

        if (edit.removedQuestions.isNotEmpty()) {
            Text(
                text = "已移出卷面的题（可加回）",
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            edit.removedQuestions.forEach { removed ->
                OutlineActionChip(
                    text = "加回：${removed.title}",
                    onClick = { onRestore(removed.entryId) },
                    modifier = Modifier.testTag(
                        "tutor_export_edit_restore_${removed.entryId}",
                    ),
                )
            }
        }

        if (edit.hasBlankText) {
            WorkspaceNotice(
                message = "有必填文字被清空，填上后才能导出。",
                modifier = Modifier.testTag("tutor_export_edit_blank_warning"),
                tone = WorkspaceNoticeTone.ERROR,
            )
        }

        if (onDetailedAnswersChange != null && onPrepareDetailedAnswers != null) TutorDetailedAnswersChoice(includeDetailedAnswers, onDetailedAnswersChange)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PaperQuestionEditor(
    index: Int,
    question: MistakePdfPaperQuestion,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    figureReviewRequired: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBlockTextChange: (blockId: String, text: String) -> Unit,
    onChoiceTextChange: (blockId: String, choiceId: String, text: String) -> Unit,
    includeNote: Boolean,
    onNoteChange: (String) -> Unit,
) {
    WorkspacePanel(
        containerColor = ContentSurface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tutor_export_edit_question_${question.entryId}"),
    ) {
        Text(
            text = "第 ${index + 1} 题",
            modifier = Modifier.fillMaxWidth(),
            color = SmartColors.JadeDark,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlineActionChip(
                text = "上移",
                onClick = { onMove(-1) },
                enabled = canMoveUp,
                modifier = Modifier.testTag("tutor_export_edit_up_${question.entryId}"),
            )
            OutlineActionChip(
                text = "下移",
                onClick = { onMove(1) },
                enabled = canMoveDown,
                modifier = Modifier.testTag("tutor_export_edit_down_${question.entryId}"),
            )
            OutlineActionChip(
                text = "移出",
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.testTag("tutor_export_edit_remove_${question.entryId}"),
            )
        }
        OutlinedTextField(
            value = question.title,
            onValueChange = onTitleChange,
            label = { Text("题目标题") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tutor_export_edit_title_${question.entryId}"),
        )
        question.blocks.forEach { block ->
            when (block) {
                is MistakePdfBlock.Paragraph -> PaperTextField(
                    value = block.text,
                    label = "题干/段落",
                    tag = "tutor_export_edit_block_${question.entryId}_${block.id}",
                    onValueChange = { text -> onBlockTextChange(block.id, text) },
                )
                is MistakePdfBlock.SectionHeading -> PaperTextField(
                    value = block.text,
                    label = "小标题",
                    tag = "tutor_export_edit_block_${question.entryId}_${block.id}",
                    onValueChange = { text -> onBlockTextChange(block.id, text) },
                )
                is MistakePdfBlock.Formula -> PaperTextField(
                    value = block.latex,
                    label = "公式（LaTeX）",
                    tag = "tutor_export_edit_block_${question.entryId}_${block.id}",
                    onValueChange = { text -> onBlockTextChange(block.id, text) },
                )
                is MistakePdfBlock.ChoiceGroup -> {
                    PaperTextField(
                        value = block.prompt,
                        label = "选择题题干",
                        tag = "tutor_export_edit_block_${question.entryId}_${block.id}",
                        onValueChange = { text -> onBlockTextChange(block.id, text) },
                    )
                    block.choices.forEachIndexed { choiceIndex, choice ->
                        PaperTextField(
                            value = choice.text,
                            label = "选项 ${choiceLabel(choiceIndex)}",
                            tag = "tutor_export_edit_choice_${question.entryId}_" +
                                "${block.id}_${choice.id}",
                            onValueChange = { text ->
                                onChoiceTextChange(block.id, choice.id, text)
                            },
                        )
                    }
                }
                is MistakePdfBlock.Figure -> Text(
                    text = "图形：${block.title ?: "未命名"}（保留原图）",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                is MistakePdfBlock.Image -> Text(
                    text = "图片：${block.title}（保留原图）",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                is MistakePdfBlock.Note -> Text(
                    text = "备注：${block.text}",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (figureReviewRequired) {
            Text(
                text = "文字已改：原图可能不再匹配，本版保留原图，不会自动重画。",
                modifier = Modifier.testTag(
                    "tutor_export_edit_figure_review_${question.entryId}",
                ),
                color = ErrorText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // 备注只在"含备注"版式里进卷面；改的是这份草稿，不写回错题条目。
        if (includeNote) {
            PaperTextField(
                value = question.note.orEmpty(),
                label = "卷面备注（不改错题本）",
                tag = "tutor_export_edit_note_${question.entryId}",
                onValueChange = onNoteChange,
            )
        }
    }
}

@Composable
private fun PaperTextField(
    value: String,
    label: String,
    tag: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

/** 与渲染器同一套选项标签（A. B. …；超过 26 个用序号）。 */
private fun choiceLabel(index: Int): String =
    if (index < 26) "${('A'.code + index).toChar()}." else "${index + 1}."
