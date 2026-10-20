package com.tingyun.smartmistakebook.feature.library

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.export.MistakePaperDraft
import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperQuestion
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import kotlinx.coroutines.CancellationException

/**
 * 原生卷面编辑入口（6B-2 / T6）。
 *
 * 它消灭的失败：6B-2 的编辑态只在讲题页的导出 sheet 里，而那张 sheet 只能由模型的
 * `START_EXPORT` 卡打开——模型不可达时，学生**没有本地入口**可以增删题 / 调序 / 改文字。
 * 这个路由从原生详情与「导出成果」进入，只读本机题面，不派发任何模型任务。
 *
 * **只改文档副本**：编辑动作全部落在 [MistakePdfPaperEdit] 的卷面题序列上；确认时
 * [confirmNativePaperEdit] 用 [MistakePdfPaperEdit.toDraft] 产出一份草稿，交给既有的
 * `MistakeExportJobRequest.Edited` 管线重渲染。错题条目、题面 revision 与用户备注一概不写。
 *
 * **未编辑逐位不变**：没有改动时草稿输入就是正常批量导出（`MistakePdfBatchEligibility`）
 * 的同一份快照；编辑后才重算 `inputSha256`（缓存键），来源题面文档指纹保持不变。
 *
 * @param entryIds 来源题（详情 = 单个；批量 = 当前筛选的顺序），由装配处决定顺序。
 * @param loadPaper 只读题面装载口；真实现是 `Application.buildMistakePaperEdit`。返回 null
 *   （一道都不能导出）时页面如实说明，不摆一个空编辑器。
 * @param onExport 确认后的交付回调：草稿 + 学生在原生格式 sheet 里选的格式。装配处负责
 *   `startMistakeExportEdited` 与导航到「导出成果」。
 */
@Composable
fun MistakePaperEditRoute(
    entryIds: List<String>,
    loadPaper: suspend (List<String>) -> MistakePdfPaperEdit?,
    onBack: () -> Unit,
    onExport: (MistakePaperDraft, MistakeExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestLoadPaper by rememberUpdatedState(loadPaper)
    val latestOnExport by rememberUpdatedState(onExport)
    val requestedIds = remember(entryIds) {
        entryIds.map(String::trim).filter(String::isNotEmpty).distinct()
    }
    val stateKey = remember(requestedIds) { requestedIds.joinToString(separator = "\u0000") }
    var loadState by remember(stateKey) {
        mutableStateOf<NativePaperEditLoadState>(NativePaperEditLoadState.Loading)
    }
    var edit by remember(stateKey) { mutableStateOf<MistakePdfPaperEdit?>(null) }
    var formatSheetOpen by rememberSaveable(stateKey) { mutableStateOf(false) }
    var message by remember(stateKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(stateKey) {
        loadState = NativePaperEditLoadState.Loading
        edit = null
        message = null
        if (requestedIds.isEmpty()) {
            loadState = NativePaperEditLoadState.Unavailable
            return@LaunchedEffect
        }
        loadState = try {
            val loaded = latestLoadPaper(requestedIds)
            if (loaded == null) {
                NativePaperEditLoadState.Unavailable
            } else {
                edit = loaded
                NativePaperEditLoadState.Ready
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // 读取失败的界面原因与"没有可导出题面"不同，但都不该把异常细节丢给学生；
            // 这里统一按"暂时没有可编辑卷面"如实说明，并保留返回。
            NativePaperEditLoadState.Unavailable
        }
    }

    RootPageColumn(modifier = modifier.testTag("native_paper_edit_screen")) {
        NativePaperEditHeader(onBack)
        Spacer(Modifier.height(8.dp))
        val current = edit
        when {
            loadState == NativePaperEditLoadState.Loading -> Text(
                text = "正在读取题面…",
                modifier = Modifier.testTag("native_paper_edit_loading"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            current == null -> {
                Text(
                    text = "没有可编辑的卷面：所选题目暂时不能导出（缺少完整题面或原图）。",
                    modifier = Modifier.testTag("native_paper_edit_unavailable"),
                    color = ErrorWarm,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                OutlineActionChip(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.testTag("native_paper_edit_unavailable_back"),
                )
            }
            else -> {
                Text(
                    text = "共 ${current.questions.size} 题 · 只改这份卷面",
                    modifier = Modifier.testTag("native_paper_edit_summary"),
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "编辑不会写回错题本里的题目；确认后按所选格式重新生成文件。",
                    modifier = Modifier.testTag("native_paper_edit_notice"),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (current.omittedCount > 0) {
                    Text(
                        text = "另有 ${current.omittedCount} 道暂时不能导出，已略过。",
                        modifier = Modifier.testTag("native_paper_edit_omitted"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(4.dp))

                current.questions.forEachIndexed { index, question ->
                    NativePaperQuestionEditor(
                        index = index,
                        question = question,
                        canMoveUp = index > 0,
                        canMoveDown = index < current.questions.lastIndex,
                        canRemove = current.canRemoveQuestion,
                        figureReviewRequired = current.figureReviewRequired(question.entryId),
                        includeNote = current.layout.includeNote,
                        onMove = { offset -> edit = edit?.withQuestionMoved(question.entryId, offset) },
                        onRemove = { edit = edit?.withQuestionRemoved(question.entryId) },
                        onTitleChange = { title ->
                            edit = edit?.withQuestionTitle(question.entryId, title)
                        },
                        onBlockTextChange = { blockId, text ->
                            edit = edit?.withBlockText(question.entryId, blockId, text)
                        },
                        onChoiceTextChange = { blockId, choiceId, text ->
                            edit = edit?.withChoiceText(question.entryId, blockId, choiceId, text)
                        },
                        onNoteChange = { note -> edit = edit?.withNote(question.entryId, note) },
                    )
                }

                if (current.removedQuestions.isNotEmpty()) {
                    Text(
                        text = "已删除的题（可以加回）",
                        modifier = Modifier.testTag("native_paper_edit_removed"),
                        color = Ink,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    current.removedQuestions.forEach { removed ->
                        OutlineActionChip(
                            text = "加回：${removed.title}",
                            onClick = { edit = edit?.withQuestionRestored(removed.entryId) },
                            modifier = Modifier.testTag(
                                "native_paper_edit_restore_${removed.entryId}",
                            ),
                        )
                    }
                }

                if (current.hasBlankText) {
                    Text(
                        text = "有必填文字被清空，填上后才能导出。",
                        modifier = Modifier.testTag("native_paper_edit_blank_warning"),
                        color = ErrorWarm,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                message?.let { text ->
                    Text(
                        text = text,
                        modifier = Modifier.testTag("native_paper_edit_message"),
                        color = ErrorWarm,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(4.dp))
                PrimaryActionButton(
                    text = "选择格式并导出（${current.questions.size} 题）",
                    onClick = {
                        message = null
                        formatSheetOpen = true
                    },
                    enabled = !current.hasBlankText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("native_paper_edit_export"),
                )
                OutlineActionChip(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.testTag("native_paper_edit_back"),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    val editedPaper = edit
    if (formatSheetOpen && editedPaper != null) {
        // 原生格式入口（PRD-04）原样复用：默认 PDF、可选 Word、可取消；取消不入队。
        MistakeExportFormatSheet(
            subtitle = "重新生成编辑后的卷面（${editedPaper.questions.size} 题）",
            onDismiss = { formatSheetOpen = false },
            onConfirm = { format ->
                formatSheetOpen = false
                when (val result = confirmNativePaperEdit(editedPaper)) {
                    is NativePaperEditConfirm.Ready -> latestOnExport(result.draft, format)
                    NativePaperEditConfirm.BlankText ->
                        message = "有必填文字被清空，填上后才能导出。"
                }
            },
        )
    }
}

/** 原生编辑页装载态（只在页面内用；不依赖模型能力）。 */
private enum class NativePaperEditLoadState {
    Loading,
    Ready,
    Unavailable,
}

/** 确认口：空白必填文字不是"可以导出但内容为空"，而是拒绝并说明。 */
internal sealed interface NativePaperEditConfirm {
    data class Ready(val draft: MistakePaperDraft) : NativePaperEditConfirm

    data object BlankText : NativePaperEditConfirm
}

internal fun confirmNativePaperEdit(edit: MistakePdfPaperEdit): NativePaperEditConfirm =
    if (edit.hasBlankText) {
        NativePaperEditConfirm.BlankText
    } else {
        NativePaperEditConfirm.Ready(edit.toDraft())
    }

@Composable
private fun NativePaperEditHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("native_paper_edit_header_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = Ink,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = "编辑卷面",
            color = Ink,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun NativePaperQuestionEditor(
    index: Int,
    question: MistakePdfPaperQuestion,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    figureReviewRequired: Boolean,
    includeNote: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBlockTextChange: (blockId: String, text: String) -> Unit,
    onChoiceTextChange: (blockId: String, choiceId: String, text: String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("native_paper_edit_question_${question.entryId}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "第 ${index + 1} 题",
                modifier = Modifier.weight(1f),
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            OutlineActionChip(
                text = "上移",
                onClick = { onMove(-1) },
                enabled = canMoveUp,
                modifier = Modifier.testTag("native_paper_edit_up_${question.entryId}"),
            )
            OutlineActionChip(
                text = "下移",
                onClick = { onMove(1) },
                enabled = canMoveDown,
                modifier = Modifier.testTag("native_paper_edit_down_${question.entryId}"),
            )
            OutlineActionChip(
                text = "删除",
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.testTag("native_paper_edit_remove_${question.entryId}"),
            )
        }
        OutlinedTextField(
            value = question.title,
            onValueChange = onTitleChange,
            label = { Text("题目标题") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("native_paper_edit_title_${question.entryId}"),
        )
        question.blocks.forEach { block ->
            when (block) {
                is MistakePdfBlock.Paragraph -> NativePaperTextField(
                    value = block.text,
                    label = "题干/段落",
                    tag = "native_paper_edit_block_${question.entryId}_${block.id}",
                    onValueChange = { text -> onBlockTextChange(block.id, text) },
                )
                is MistakePdfBlock.SectionHeading -> NativePaperTextField(
                    value = block.text,
                    label = "小标题",
                    tag = "native_paper_edit_block_${question.entryId}_${block.id}",
                    onValueChange = { text -> onBlockTextChange(block.id, text) },
                )
                is MistakePdfBlock.Formula -> NativePaperTextField(
                    value = block.latex,
                    label = "公式（LaTeX）",
                    tag = "native_paper_edit_block_${question.entryId}_${block.id}",
                    onValueChange = { text -> onBlockTextChange(block.id, text) },
                )
                is MistakePdfBlock.ChoiceGroup -> {
                    NativePaperTextField(
                        value = block.prompt,
                        label = "选择题题干",
                        tag = "native_paper_edit_block_${question.entryId}_${block.id}",
                        onValueChange = { text -> onBlockTextChange(block.id, text) },
                    )
                    block.choices.forEachIndexed { choiceIndex, choice ->
                        NativePaperTextField(
                            value = choice.text,
                            label = "选项 ${nativeChoiceLabel(choiceIndex)}",
                            tag = "native_paper_edit_choice_${question.entryId}_" +
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("native_paper_edit_figure_review_${question.entryId}"),
                shape = RoundedCornerShape(8.dp),
                color = JadeSoft.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Outline),
            ) {
                Text(
                    text = "文字已改：原图可能不再匹配，本版保留原图，不会自动重画。",
                    modifier = Modifier.padding(10.dp),
                    color = ErrorWarm,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (includeNote) {
            NativePaperTextField(
                value = question.note.orEmpty(),
                label = "卷面备注（不改错题本）",
                tag = "native_paper_edit_note_${question.entryId}",
                onValueChange = onNoteChange,
            )
        }
    }
}

@Composable
private fun NativePaperTextField(
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
private fun nativeChoiceLabel(index: Int): String =
    if (index < 26) "${('A'.code + index).toChar()}." else "${index + 1}."
