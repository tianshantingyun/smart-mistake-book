package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock

/**
 * What kind of semantic change a saved revision introduces.
 *
 * The categories are intentionally coarser than the block model: the learner needs to know
 * whether the stem, the choices, the figure meaning, or the answer basis moved. Pure
 * presentation state (a selected choice, a disabled option) is not a semantic change.
 */
enum class MistakeRevisionChangeKind {
    TITLE,
    STEM,
    CHOICE,
    FIGURE,
    ANSWER,
}

/**
 * One before/after row shown before and after saving an edit. [before] is null for an added
 * block; [after] is null for a removed one.
 */
data class MistakeRevisionChange(
    val kind: MistakeRevisionChangeKind,
    val label: String,
    val before: String?,
    val after: String?,
) {
    init {
        require(label.isNotBlank()) { "Revision change label must not be blank" }
    }
}

/**
 * Compares the committed document with the edited one. The comparison ignores interaction-only
 * fields (`selectedChoiceId`, `enabled`) so toggling an option in the editor never fabricates a
 * semantic revision.
 */
object MistakeRevisionDiff {
    fun between(
        previousTitle: String,
        previousDocument: CapturedQuestionDocument,
        nextTitle: String,
        nextDocument: CapturedQuestionDocument,
        previousHadAnswerSpec: Boolean,
    ): List<MistakeRevisionChange> = buildList {
        if (previousTitle != nextTitle || previousDocument.document.title != nextDocument.document.title) {
            add(
                MistakeRevisionChange(
                    kind = MistakeRevisionChangeKind.TITLE,
                    label = "标题",
                    before = previousTitle,
                    after = nextTitle,
                ),
            )
        }
        val previousBlocks = previousDocument.document.blocks.associateBy(ContentBlock::id)
        val nextBlocks = nextDocument.document.blocks.associateBy(ContentBlock::id)
        val orderedIds = buildList {
            previousDocument.document.blocks.forEach { add(it.id) }
            nextDocument.document.blocks.forEach { block -> if (block.id !in previousBlocks) add(block.id) }
        }
        orderedIds.forEach { blockId ->
            val before = previousBlocks[blockId]
            val after = nextBlocks[blockId]
            when {
                before == null && after != null -> add(
                    MistakeRevisionChange(
                        kind = after.changeKind(),
                        label = after.changeLabel(),
                        before = null,
                        after = after.describe(),
                    ),
                )
                before != null && after == null -> add(
                    MistakeRevisionChange(
                        kind = before.changeKind(),
                        label = before.changeLabel(),
                        before = before.describe(),
                        after = null,
                    ),
                )
                before != null && after != null && before.hasSemanticDifference(after) -> add(
                    MistakeRevisionChange(
                        kind = after.changeKind(),
                        label = after.changeLabel(),
                        before = before.describe(),
                        after = after.describe(),
                    ),
                )
            }
        }
        // 只有“确实要生成新 revision”时才提示旧答案不再沿用：空差异保存会走 Unchanged，
        // 原 revision（含 answerSpec）不会被替换，提前显示 ANSWER 行反而制造假变更。
        if (previousHadAnswerSpec && isNotEmpty()) {
            add(
                MistakeRevisionChange(
                    kind = MistakeRevisionChangeKind.ANSWER,
                    label = "答案与解析",
                    before = "旧版答案与解析仍保留在历史版本",
                    after = "新版未沿用旧答案，需要重新核验后再判分",
                ),
            )
        }
    }

    private fun ContentBlock.changeKind(): MistakeRevisionChangeKind = when (this) {
        is ContentBlock.Paragraph, is ContentBlock.Formula, is ContentBlock.Unknown ->
            MistakeRevisionChangeKind.STEM
        is ContentBlock.ChoiceGroup -> MistakeRevisionChangeKind.CHOICE
        is ContentBlock.Figure -> MistakeRevisionChangeKind.FIGURE
    }

    private fun ContentBlock.changeLabel(): String = when (this) {
        is ContentBlock.Paragraph -> "题干"
        is ContentBlock.Formula -> "公式"
        is ContentBlock.ChoiceGroup -> "选项"
        is ContentBlock.Figure -> "图形"
        is ContentBlock.Unknown -> "未识别内容"
    }

    private fun ContentBlock.hasSemanticDifference(other: ContentBlock): Boolean {
        if (this::class != other::class) return true
        return when (this) {
            is ContentBlock.Paragraph -> other is ContentBlock.Paragraph && markdown != other.markdown
            is ContentBlock.Formula -> other is ContentBlock.Formula &&
                (latex != other.latex ||
                    alternativeText != other.alternativeText ||
                    display != other.display)
            is ContentBlock.ChoiceGroup -> other is ContentBlock.ChoiceGroup &&
                (promptMarkdown != other.promptMarkdown || choices != other.choices)
            is ContentBlock.Figure -> other is ContentBlock.Figure &&
                (title != other.title ||
                    alternativeText != other.alternativeText ||
                    schema != other.schema)
            is ContentBlock.Unknown -> other is ContentBlock.Unknown &&
                (type != other.type || fallbackText != other.fallbackText)
        }
    }

    private fun ContentBlock.describe(): String = when (this) {
        is ContentBlock.Paragraph -> markdown
        is ContentBlock.Formula -> buildString {
            append(latex)
            if (alternativeText.isNotBlank()) append("（").append(alternativeText).append("）")
        }
        is ContentBlock.ChoiceGroup -> buildString {
            if (promptMarkdown.isNotBlank()) append(promptMarkdown).append('\n')
            choices.forEachIndexed { index, choice ->
                if (index > 0) append('\n')
                append(('A'.code + index).toChar()).append(". ").append(choice.markdown)
            }
        }
        is ContentBlock.Figure -> listOfNotNull(title, alternativeText)
            .filter(String::isNotBlank)
            .joinToString(" · ")
        is ContentBlock.Unknown -> fallbackText
    }
}
