package com.tingyun.smartmistakebook.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionChangeKind
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionDiff
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionReceipt
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RevisionEditBlockKind {
    STEM,
    FORMULA,
    CHOICE,
    FIGURE,
    UNKNOWN,
}

data class RevisionEditChoiceDraft(
    val id: String,
    val markdown: String,
    val accessibilityLabel: String?,
)

data class RevisionEditBlockDraft(
    val id: String,
    val kind: RevisionEditBlockKind,
    val text: String,
    val secondaryText: String = "",
    val choices: List<RevisionEditChoiceDraft> = emptyList(),
    val readOnly: Boolean = false,
)

data class RevisionEditDiffRow(
    val label: String,
    val before: String?,
    val after: String?,
)

data class MistakeRevisionEditUiState(
    val loading: Boolean = true,
    val unavailableReason: String? = null,
    val entryId: String = "",
    val title: String = "",
    val blocks: List<RevisionEditBlockDraft> = emptyList(),
    val diff: List<RevisionEditDiffRow> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val conflict: String? = null,
    val message: String? = null,
    val savedRevisionNumber: Int? = null,
    val savedAtEpochMillis: Long? = null,
    val invalidationSummary: String? = null,
    /** True when a tutoring conversation is anchored to the revision being edited. */
    val hasInProgressOldConversation: Boolean = false,
    val source: MistakeSourceSet = MistakeSourceSet.Missing,
)

/**
 * Minimal editor for one saved question. It holds the draft locally, compares it with the exact
 * revision it was opened on, and never guesses the expected revision: the save command carries
 * the baseline revision id/number so a concurrent edit surfaces as a conflict.
 */
class MistakeRevisionEditViewModel(
    private val entryId: String,
    private val repository: MistakeDetailRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MistakeRevisionEditUiState(entryId = entryId))
    val state: StateFlow<MistakeRevisionEditUiState> = _state.asStateFlow()

    private var baselineDocument: CapturedQuestionDocument? = null
    private var baselineTitle: String = ""
    private var baselineRevisionId: String? = null
    private var baselineRevisionNumber: Int = 0
    /** 当前基线版本是否带机器可判题的答案；保存前差异据此显示“旧答案将不再沿用”。 */
    private var baselineAnswerSpecPresent: Boolean = false
    private var openedOnce = false

    init {
        loadBaseline()
    }

    /**
     * Called every time the editor becomes visible. The ViewModel is retained by the host's
     * ViewModelStore, so without this a second open would show the previous save/conflict card
     * instead of the current head.
     */
    fun onEditorOpened() {
        if (openedOnce) {
            loadBaseline()
        } else {
            openedOnce = true
        }
    }

    fun onTitleChanged(value: String) {
        _state.update { current -> current.copy(title = value.take(MAX_TITLE_CHARS), message = null) }
        recomputeDiff()
    }

    fun onBlockTextChanged(blockId: String, value: String) {
        updateBlock(blockId) { block -> block.copy(text = value.take(MAX_BLOCK_CHARS)) }
        _state.update { it.copy(message = null) }
    }

    fun onBlockSecondaryChanged(blockId: String, value: String) {
        updateBlock(blockId) { block -> block.copy(secondaryText = value.take(MAX_BLOCK_CHARS)) }
        _state.update { it.copy(message = null) }
    }

    fun onChoiceChanged(blockId: String, choiceIndex: Int, value: String) {
        updateBlock(blockId) { block ->
            if (choiceIndex !in block.choices.indices) return@updateBlock block
            block.copy(
                choices = block.choices.mapIndexed { index, choice ->
                    if (index == choiceIndex) {
                        choice.copy(markdown = value.take(MAX_BLOCK_CHARS))
                    } else {
                        choice
                    }
                },
            )
        }
        _state.update { it.copy(message = null) }
    }

    /**
     * Reloads the current head while keeping exactly what the learner typed. Used after a CAS
     * conflict: the old draft is not thrown away, and the next save targets the reloaded revision
     * only after the learner can see the new diff.
     */
    fun reload() {
        val draftBlocks = _state.value.blocks
        val draftTitle = _state.value.title
        viewModelScope.launch {
            _state.update { it.copy(loading = true, conflict = null, message = null) }
            val loaded = readReadyState()
            if (loaded == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        unavailableReason = "这道题已不存在，无法继续修改。",
                    )
                }
                return@launch
            }
            val detail = loaded.detail
            val loadedTitle = loaded.questionDocument.document.title
                ?.takeIf(String::isNotBlank) ?: detail.identity.title
            baselineDocument = loaded.questionDocument
            baselineTitle = loadedTitle
            baselineRevisionId = detail.identity.problemRevisionId
            baselineRevisionNumber = detail.identity.revisionNumber
            baselineAnswerSpecPresent = loaded.answerSpecPresent
            _state.update {
                it.copy(
                    loading = false,
                    entryId = detail.identity.errorBookEntryId,
                    title = draftTitle,
                    blocks = draftBlocks,
                    source = detail.source,
                    conflict = null,
                    message = "已重新载入第 ${detail.identity.revisionNumber} 版，请核对差异后再保存。",
                    savedRevisionNumber = null,
                    savedAtEpochMillis = null,
                    invalidationSummary = null,
                    hasInProgressOldConversation =
                        detail.tutorConversation?.questionRevisionNumber ==
                            detail.identity.revisionNumber,
                )
            }
            recomputeDiff()
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || current.unavailableReason != null) return
        val document = currentDocument() ?: run {
            _state.update { it.copy(message = "题面内容还不完整，暂时无法保存。") }
            return
        }
        val expectedRevisionId = baselineRevisionId ?: return
        if (baselineRevisionNumber <= 0) return
        _state.update { it.copy(saving = true, conflict = null, message = null) }
        viewModelScope.launch {
            val result = try {
                repository.reviseProblem(
                    ReviseMistakeProblemCommand(
                        commandId = UUID.randomUUID().toString(),
                        entryId = entryId,
                        expectedRevisionId = expectedRevisionId,
                        expectedRevisionNumber = baselineRevisionNumber,
                        questionDocument = document,
                        revisedAtEpochMillis = System.currentTimeMillis(),
                        title = current.title.trim().takeIf(String::isNotEmpty),
                    ),
                )
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        message = failure.message ?: "保存失败，请重试。",
                    )
                }
                return@launch
            }
            when (result) {
                is ReviseMistakeProblemResult.Saved -> {
                    val receipt = result.receipt
                    baselineDocument = document
                    baselineTitle = receipt.title
                    baselineRevisionId = receipt.problemRevisionId
                    baselineRevisionNumber = receipt.revisionNumber
                    // 回执指向的 revision 的真实答案状态：新 revision 恒为 false；
                    // 内容回退到旧 revision（created=false）时可能重新带回旧 answerSpec。
                    baselineAnswerSpecPresent = receipt.answerSpecPresent
                    _state.update {
                        it.copy(
                            saving = false,
                            title = receipt.title,
                            diff = emptyList(),
                            dirty = false,
                            message = if (result.created) {
                                "已保存第 ${receipt.revisionNumber} 版；旧版本与历史作答仍可查看。"
                            } else {
                                "内容与第 ${receipt.revisionNumber} 版相同，已切回该版本。"
                            },
                            savedRevisionNumber = receipt.revisionNumber,
                            savedAtEpochMillis = receipt.createdAtEpochMillis,
                            invalidationSummary = invalidationSummary(receipt),
                            hasInProgressOldConversation = false,
                        )
                    }
                }
                is ReviseMistakeProblemResult.Unchanged -> {
                    // 没有新 revision：回执指向当前 head，答案状态以回执为准。
                    baselineAnswerSpecPresent = result.receipt.answerSpecPresent
                    _state.update {
                        it.copy(
                            saving = false,
                            diff = emptyList(),
                            dirty = false,
                            message = "没有需要保存的修改。",
                            savedRevisionNumber = result.receipt.revisionNumber,
                            savedAtEpochMillis = result.receipt.createdAtEpochMillis,
                            invalidationSummary = null,
                        )
                    }
                }
                is ReviseMistakeProblemResult.Conflict -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            conflict = buildString {
                                append("题面已在另一处更新")
                                val number = result.currentRevisionNumber
                                if (number != null) append("为第 ").append(number).append(" 版")
                                append("。你的修改仍保留在这里；请先重新载入，核对差异后再保存。")
                            },
                        )
                    }
                }
                is ReviseMistakeProblemResult.DuplicateCommandConflict -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            message = "保存信息已变化，本次未保存。请重新保存。",
                        )
                    }
                }
                is ReviseMistakeProblemResult.InvalidDocument -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            message = "题面还不完整，暂时无法保存：${
                                result.issues.take(3).joinToString("、").ifBlank { "内容校验未通过" }
                            }",
                        )
                    }
                }
                ReviseMistakeProblemResult.NotFound -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            unavailableReason = "这道题已不存在，无法保存修改。",
                        )
                    }
                }
                ReviseMistakeProblemResult.NotSupported -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            message = "当前版本暂不支持修改题面，本次未保存。",
                        )
                    }
                }
            }
        }
    }

    private fun loadBaseline() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val loaded = readReadyState()
            if (loaded == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        unavailableReason = "这道题目前没有可编辑的结构化题面。",
                    )
                }
                return@launch
            }
            val detail = loaded.detail
            val document = loaded.questionDocument
            val initialTitle = document.document.title?.takeIf(String::isNotBlank)
                ?: detail.identity.title
            baselineDocument = document
            baselineTitle = initialTitle
            baselineRevisionId = detail.identity.problemRevisionId
            baselineRevisionNumber = detail.identity.revisionNumber
            baselineAnswerSpecPresent = loaded.answerSpecPresent
            _state.update {
                it.copy(
                    loading = false,
                    unavailableReason = null,
                    entryId = detail.identity.errorBookEntryId,
                    title = initialTitle,
                    blocks = document.document.blocks.map { block -> block.toDraft() },
                    source = detail.source,
                    message = null,
                    conflict = null,
                    savedRevisionNumber = null,
                    savedAtEpochMillis = null,
                    invalidationSummary = null,
                    hasInProgressOldConversation =
                        detail.tutorConversation?.questionRevisionNumber ==
                            detail.identity.revisionNumber,
                )
            }
            recomputeDiff()
        }
    }

    private suspend fun readReadyState(): MistakeDetailState.Ready? {
        val state = repository.observe(entryId).first { it != MistakeDetailState.Loading }
        return state as? MistakeDetailState.Ready
    }

    private fun updateBlock(
        blockId: String,
        transform: (RevisionEditBlockDraft) -> RevisionEditBlockDraft,
    ) {
        _state.update { current ->
            current.copy(
                blocks = current.blocks.map { block ->
                    if (block.id == blockId && !block.readOnly) transform(block) else block
                },
            )
        }
        recomputeDiff()
    }

    private fun recomputeDiff() {
        val current = _state.value
        val baseline = baselineDocument ?: return
        val edited = currentDocument() ?: return
        // The editor's title field is authoritative for the new revision. Normalize the baseline
        // document the same way so an unchanged title never looks like a change just because the
        // stored document had no title field.
        val normalizedBaseline = baseline.copy(
            document = baseline.document.copy(title = baselineTitle),
        )
        val changes = MistakeRevisionDiff.between(
            previousTitle = baselineTitle,
            previousDocument = normalizedBaseline,
            nextTitle = current.title.trim().ifEmpty { baselineTitle },
            nextDocument = edited,
            previousHadAnswerSpec = baselineAnswerSpecPresent,
        )
        _state.update {
            it.copy(
                diff = changes.map { change ->
                    RevisionEditDiffRow(
                        label = change.label,
                        before = change.before,
                        after = change.after,
                    )
                },
                dirty = changes.isNotEmpty(),
            )
        }
    }

    private fun currentDocument(): CapturedQuestionDocument? {
        val baseline = baselineDocument ?: return null
        val current = _state.value
        val originalById = baseline.document.blocks.associateBy(ContentBlock::id)
        val blocks = current.blocks.mapNotNull { draft ->
            val original = originalById[draft.id] ?: return@mapNotNull null
            when (original) {
                is ContentBlock.Paragraph ->
                    ContentBlock.Paragraph(id = original.id, markdown = draft.text)
                is ContentBlock.Formula -> ContentBlock.Formula(
                    id = original.id,
                    latex = draft.text,
                    alternativeText = draft.secondaryText,
                    display = original.display,
                )
                is ContentBlock.ChoiceGroup -> ContentBlock.ChoiceGroup(
                    id = original.id,
                    promptMarkdown = draft.text,
                    choices = original.choices.mapIndexed { index, choice ->
                        StructuredChoice(
                            id = choice.id,
                            markdown = draft.choices.getOrNull(index)?.markdown ?: choice.markdown,
                            accessibilityLabel = choice.accessibilityLabel,
                            enabled = choice.enabled,
                        )
                    },
                    selectedChoiceId = original.selectedChoiceId,
                    enabled = original.enabled,
                )
                is ContentBlock.Figure -> ContentBlock.Figure(
                    id = original.id,
                    title = draft.secondaryText.takeIf(String::isNotBlank),
                    alternativeText = draft.text,
                    schema = original.schema,
                )
                is ContentBlock.Unknown -> original
            }
        }
        if (blocks.isEmpty()) return null
        return baseline.copy(
            document = QuestionDocument(
                id = baseline.document.id,
                title = current.title.trim().takeIf(String::isNotEmpty),
                blocks = blocks,
            ),
        )
    }

    private fun invalidationSummary(receipt: MistakeRevisionReceipt): String? {
        val parts = buildList {
            if (receipt.cancelledModelTaskCount > 0) {
                add("已取消 ${receipt.cancelledModelTaskCount} 个旧版模型任务")
            }
            if (receipt.supersededCleanRedrawCount > 0) {
                add("已作废 ${receipt.supersededCleanRedrawCount} 个旧版重绘任务")
            }
            if (receipt.staleRelationCount > 0) {
                add("已把 ${receipt.staleRelationCount} 条旧版关系标为待重新整理")
            }
            if (MistakeRevisionChangeKind.ANSWER in receipt.changes.map { it.kind }) {
                add("旧版答案与解析未带入新版")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("；")
    }

    companion object {
        private const val MAX_TITLE_CHARS = 160
        private const val MAX_BLOCK_CHARS = 8_000

        fun factory(
            entryId: String,
            repository: MistakeDetailRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { MistakeRevisionEditViewModel(entryId, repository) }
        }
    }
}

private fun ContentBlock.toDraft(): RevisionEditBlockDraft = when (this) {
    is ContentBlock.Paragraph -> RevisionEditBlockDraft(
        id = id,
        kind = RevisionEditBlockKind.STEM,
        text = markdown,
    )
    is ContentBlock.Formula -> RevisionEditBlockDraft(
        id = id,
        kind = RevisionEditBlockKind.FORMULA,
        text = latex,
        secondaryText = alternativeText,
    )
    is ContentBlock.ChoiceGroup -> RevisionEditBlockDraft(
        id = id,
        kind = RevisionEditBlockKind.CHOICE,
        text = promptMarkdown,
        choices = choices.map { choice ->
            RevisionEditChoiceDraft(
                id = choice.id,
                markdown = choice.markdown,
                accessibilityLabel = choice.accessibilityLabel,
            )
        },
    )
    is ContentBlock.Figure -> RevisionEditBlockDraft(
        id = id,
        kind = RevisionEditBlockKind.FIGURE,
        text = alternativeText,
        secondaryText = title.orEmpty(),
    )
    is ContentBlock.Unknown -> RevisionEditBlockDraft(
        id = id,
        kind = RevisionEditBlockKind.UNKNOWN,
        text = fallbackText,
        readOnly = true,
    )
}
