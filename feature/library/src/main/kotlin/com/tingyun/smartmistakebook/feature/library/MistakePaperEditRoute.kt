package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.ui.ClaudePageHeader

import androidx.compose.foundation.layout.heightIn
import com.tingyun.smartmistakebook.core.ui.ErrorText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tingyun.smartmistakebook.core.model.PaperEditModelPort
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.ReferenceEditorPage
import com.tingyun.smartmistakebook.core.ui.InlineActionButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
 * @param modelPort 6B-3 自然语言改卷端口（批次 50）；null = 这个入口不接模型，页面不摆 AI 区。
 *   端口只收 `core:model` 类型，生产实现 `RepositoryPaperEditModelPort` 由 app 装配。
 * @param modelConsentGranted 应用层"配置了模型 = 同意"的实值；false 时入口只如实说明并给
 *   设置入口，不提交请求——不广告未启用的能力。
 * @param initialModelEdit 详情页「AI 改卷面」进入时展开 AI 区；卷面页自己的 chip 也可展开。
 * @param onOpenModelSettings 未启用模型时的设置入口；null 时只显示说明。
 */
@Composable
fun MistakePaperEditRoute(
    entryIds: List<String>,
    loadPaper: suspend (List<String>) -> MistakePdfPaperEdit?,
    onBack: () -> Unit,
    onExport: (MistakePaperDraft, MistakeExportFormat) -> Unit,
    modifier: Modifier = Modifier,
    onPrepareDetailedAnswers: ((MistakePaperDraft, MistakeExportFormat) -> Unit)? = null,
    modelPort: PaperEditModelPort? = null,
    modelConsentGranted: Boolean = false,
    initialModelEdit: Boolean = false,
    onOpenModelSettings: (() -> Unit)? = null,
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
    // 6B-3：AI 区是**一次性预览**状态，不跨进程保存（预览绑定具体卷面实例）。
    val modelScope = rememberCoroutineScope()
    var modelSectionOpen by rememberSaveable(stateKey) { mutableStateOf(initialModelEdit) }
    var modelInstruction by rememberSaveable(stateKey) { mutableStateOf("") }
    var modelBusy by remember(stateKey) { mutableStateOf(false) }
    var modelPreview by remember(stateKey) {
        mutableStateOf<NativePaperEditPreview.Ready?>(null)
    }
    var modelMessage by remember(stateKey) { mutableStateOf<String?>(null) }
    var modelMessageIsError by remember(stateKey) { mutableStateOf(false) }

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

    fun requestModelPreview(paper: MistakePdfPaperEdit) {
        val port = modelPort ?: return
        if (modelBusy) return
        if (!modelConsentGranted) {
            modelMessage = "还没有启用模型：先在设置里配置模型，才能让 AI 改卷面。"
            modelMessageIsError = true
            return
        }
        if (paper.hasBlankText) {
            modelMessage = "卷面里有必填文字被清空，填上后才能让 AI 改卷面。"
            modelMessageIsError = true
            return
        }
        val revision = try {
            NativePaperEditModel.revisionFor(paper)
        } catch (_: IllegalArgumentException) {
            modelMessage = "这份卷面暂时不能交给模型；卷面保持原样。"
            modelMessageIsError = true
            return
        }
        modelBusy = true
        modelPreview = null
        modelMessage = null
        modelMessageIsError = false
        // 提交时冻结这次要求：等待期间输入框里的后续编辑不应改变已经发出的任务。
        val instruction = modelInstruction
        modelScope.launch {
            val outcome = NativePaperEditModel.request(
                port = port,
                paper = paper,
                instruction = instruction,
                paperId = NativePaperEditModel.paperIdFor(requestedIds),
                revision = revision,
                agentConsentGranted = modelConsentGranted,
            )
            modelBusy = false
            when (outcome) {
                is NativePaperEditPreview.Ready -> {
                    if (edit !== paper) {
                        // 等待期间学生手动改过卷面：他的改动优先，这次结果丢弃。
                        modelMessage = "卷面在等待时被改过，这次结果没有采用（卷面保持你改后的样子）。"
                        modelMessageIsError = true
                    } else {
                        modelPreview = outcome
                        modelMessage = "模型给出了可执行的修改（${outcome.commandCount} 条命令，" +
                            "${outcome.diffs.size} 处变化），核对后采用或取消。"
                        modelMessageIsError = false
                    }
                }
                is NativePaperEditPreview.Rejected -> {
                    modelMessage = outcome.reason
                    modelMessageIsError = true
                }
            }
        }
    }

    fun adoptModelPreview(paper: MistakePdfPaperEdit) {
        val preview = modelPreview ?: return
        when (val result = NativePaperEditModel.adopt(paper, preview)) {
            is NativePaperEditAdoption.Adopted -> {
                edit = result.edit
                modelPreview = null
                modelMessage = "已采用这次修改（${preview.diffs.size} 处变化）；" +
                    "确认导出时按这份卷面生成。"
                modelMessageIsError = false
            }
            is NativePaperEditAdoption.Stale -> {
                modelPreview = null
                modelMessage = result.reason
                modelMessageIsError = true
            }
        }
    }

    fun cancelModelPreview() {
        modelPreview = null
        modelMessage = "已取消这次修改，卷面保持原样。"
        modelMessageIsError = false
    }

    val referenceImages = edit?.questions.orEmpty().flatMap { question ->
        listOfNotNull(question.cleanImageLocalUri) + question.blocks.filterIsInstance<MistakePdfBlock.Image>()
            .filter { it.role != VisualRole.TUTOR_EXPLANATION.name }.map { it.localUri }
    }.distinct()
    ReferenceEditorPage(modifier = modifier.testTag("native_paper_edit_screen"), imageUris = referenceImages) {
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
                    color = ErrorText,
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
                    text = "仅修改导出卷面，错题本保持不变。",
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
                if (modelPort != null) {
                    Spacer(Modifier.height(8.dp))
                    val preview = modelPreview
                    NativePaperEditModelSection(
                        expanded = modelSectionOpen,
                        onToggle = { modelSectionOpen = !modelSectionOpen },
                        instruction = modelInstruction,
                        onInstructionChange = { value ->
                            modelInstruction = value.take(NativePaperEditModel.MAX_INSTRUCTION_CHARS)
                            // 要求一变，旧预览就不再对应当前这句话：清掉，避免"按旧要求采用"。
                            if (modelPreview != null) {
                                modelPreview = null
                                modelMessage = "修改要求已改，请重新生成预览。"
                                modelMessageIsError = false
                            } else if (modelMessageIsError) {
                                // 旧的拒绝说明不再对应当前这句要求。
                                modelMessage = null
                                modelMessageIsError = false
                            }
                        },
                        portAvailable = true,
                        consentGranted = modelConsentGranted,
                        busy = modelBusy,
                        preview = preview,
                        previewStale = preview != null && current !== preview.baseline,
                        message = modelMessage,
                        messageIsError = modelMessageIsError,
                        onGenerate = { requestModelPreview(current) },
                        onAdopt = { adoptModelPreview(current) },
                        onCancel = ::cancelModelPreview,
                        onOpenModelSettings = onOpenModelSettings,
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
                        text = "已移出卷面的题（可加回）",
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
                        color = ErrorText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                message?.let { text ->
                    Text(
                        text = text,
                        modifier = Modifier.testTag("native_paper_edit_message"),
                        color = ErrorText,
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
            onPrepareDetailedAnswers = onPrepareDetailedAnswers?.let { prepare -> { format ->
                when (val result = confirmNativePaperEdit(editedPaper)) {
                    is NativePaperEditConfirm.Ready -> { formatSheetOpen = false; prepare(result.draft, format) }
                    NativePaperEditConfirm.BlankText -> message = "有必填文字被清空，填上后才能导出。"
                }
            } },
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
    ClaudePageHeader("编辑卷面", onBack = onBack,
        backModifier = Modifier.testTag("native_paper_edit_header_back"))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
    com.tingyun.smartmistakebook.core.ui.WorkspacePanel(
        modifier = Modifier.testTag("native_paper_edit_question_${question.entryId}"),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "第 ${index + 1} 题",
                modifier = Modifier.fillMaxWidth(),
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            InlineActionButton(
                text = "上移",
                onClick = { onMove(-1) },
                enabled = canMoveUp,
                modifier = Modifier.testTag("native_paper_edit_up_${question.entryId}"),
            )
            InlineActionButton(
                text = "下移",
                onClick = { onMove(1) },
                enabled = canMoveDown,
                modifier = Modifier.testTag("native_paper_edit_down_${question.entryId}"),
            )
            InlineActionButton(
                text = "移出",
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
                shape = RoundedCornerShape(12.dp),
                color = JadeSoft.copy(alpha = 0.4f),
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = "文字已改：原图可能不再匹配，本版保留原图，不会自动重画。",
                    modifier = Modifier.padding(10.dp),
                    color = ErrorText,
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
