package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout

enum class MistakePdfBatchIneligibility {
    EMPTY,
    TOO_MANY_QUESTIONS,
    NO_READY_QUESTION,
}

sealed interface MistakePdfBatchEligibilityResult {
    data class Eligible(
        val input: MistakePdfExportInput,
        val includedCount: Int,
        val omittedCount: Int,
    ) : MistakePdfBatchEligibilityResult

    data class Ineligible(
        val reason: MistakePdfBatchIneligibility,
    ) : MistakePdfBatchEligibilityResult
}

/**
 * Builds one immutable, ordered print snapshot from the current library result.
 *
 * Unavailable entries are omitted automatically. The user is never asked to repair model output
 * or manually re-enter a question just to print the rest of the list.
 */
object MistakePdfBatchEligibility {
    const val MAX_QUESTIONS = 100

    /**
     * @param layout 版式参数（B1/B2）：整批同一份；非法参数 fail-closed，且参与
     *   指纹（换版式必须换缓存键）。
     */
    fun check(
        states: List<MistakeDetailState>,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfBatchEligibilityResult {
        val violations = layout.validate()
        require(violations.isEmpty()) { "Invalid export layout: $violations" }
        if (states.isEmpty()) {
            return MistakePdfBatchEligibilityResult.Ineligible(
                MistakePdfBatchIneligibility.EMPTY,
            )
        }
        if (states.size > MAX_QUESTIONS) {
            return MistakePdfBatchEligibilityResult.Ineligible(
                MistakePdfBatchIneligibility.TOO_MANY_QUESTIONS,
            )
        }

        val eligibleInputs = states.mapNotNull { state ->
            (MistakePdfEligibility.check(state, layout) as? MistakePdfEligibilityResult.Eligible)
                ?.input
        }
        if (eligibleInputs.isEmpty()) {
            return MistakePdfBatchEligibilityResult.Ineligible(
                MistakePdfBatchIneligibility.NO_READY_QUESTION,
            )
        }

        val includedCount = eligibleInputs.size
        return MistakePdfBatchEligibilityResult.Eligible(
            input = buildBatchExportInput(eligibleInputs, layout),
            includedCount = includedCount,
            omittedCount = states.size - includedCount,
        )
    }
}

/**
 * 把有序的单题输入装配成一份批量导出快照（4A 既有口径，6B-2 卷面编辑共用）。
 *
 * 块装配与编号在 [buildPaperBlocks]；指纹仍是 `mistake-batch-v2 + 题数 + 版式 + 各题输入指纹`，
 * 因此未编辑的卷面草稿走这里得到的快照与正常批量导出**逐位一致**。
 */
internal fun buildBatchExportInput(
    inputs: List<MistakePdfExportInput>,
    layout: MistakePdfLayout,
): MistakePdfExportInput {
    require(inputs.isNotEmpty()) { "A batch export needs at least one input" }
    val questions = inputs.map(::paperQuestionOf)
    val blocks = buildPaperBlocks(questions, layout)
    val sourceFingerprints = inputs.map(MistakePdfExportInput::inputSha256)
    val combinedFingerprint = exportFingerprint(
        "mistake-batch-v2",
        inputs.size.toString(),
        layout.canonicalForm(),
        *sourceFingerprints.toTypedArray(),
    )
    return MistakePdfExportInput(
        errorBookEntryId = "batch",
        problemId = "batch",
        problemRevisionId = combinedFingerprint,
        revisionNumber = 1,
        title = "错题练习",
        subject = "当前筛选",
        subtitle = "按当前筛选整理 · 共 ${inputs.size} 道",
        documentTitle = null,
        blocks = blocks,
        questionDocumentSha256 = combinedFingerprint,
        inputSha256 = combinedFingerprint,
        maxPages = MistakePdfExportLimits.MAX_BATCH_PAGES,
        maxRenderedLines = MistakePdfExportLimits.MAX_BATCH_RENDERED_LINES,
        maxPdfBytes = MistakePdfExportLimits.MAX_BATCH_PDF_BYTES,
        layout = layout,
    )
}

internal fun MistakePdfBlock.withNamespacedId(namespacedId: String): MistakePdfBlock = when (this) {
    is MistakePdfBlock.Image -> copy(id = namespacedId)
    is MistakePdfBlock.Note -> copy(id = namespacedId)
    is MistakePdfBlock.Paragraph -> copy(id = namespacedId)
    is MistakePdfBlock.SectionHeading -> copy(id = namespacedId)
    is MistakePdfBlock.Formula -> copy(id = namespacedId)
    is MistakePdfBlock.ChoiceGroup -> copy(id = namespacedId)
    is MistakePdfBlock.Figure -> copy(id = namespacedId)
}
