package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MistakeEditWorkflowTest {
    private fun input(prompt: String? = null): MistakeEditInput {
        val blocks = listOf(ContentBlock.Paragraph("stem", "原题条件"), ContentBlock.Paragraph("diagram", "配图"))
        val document = CapturedQuestionDocument(document = QuestionDocument("doc", "函数题", blocks), blockEvidence = blocks.map {
            QuestionBlockEvidence(it.id, "", null, WritingLayer.UNKNOWN, QuestionBlockProvenance.USER_TRANSCRIPTION, null, QuestionBlockReviewStatus.USER_CONFIRMED, null)
        })
        return MistakeEditInput(EditBasis("entry", "revision", CapturedQuestionDocumentFingerprint.of(document)), document, linkedSetOf("stem", "diagram"), "修正条件并更新配图", figureGenerationPromptBody = prompt)
    }

    @Test fun runtimeCapabilityRefreshAndTargetOrderDoNotResetTheLogicalDispatchBudget() {
        val original = input("image capability unavailable")
        val refreshed = original.copy(targetBlockIds = linkedSetOf("diagram", "stem"), figureGenerationPromptBody = "image capability available")
        assertEquals(mistakeEditJobId(original), mistakeEditJobId(refreshed))
        assertEquals(ModelTaskLogicalOperationFingerprint.of(original), ModelTaskLogicalOperationFingerprint.of(refreshed))
        assertNotEquals(mistakeEditJobId(original), mistakeEditJobId(original.copy(instruction = "只修改文字")))
    }

    @Test fun adoptingTextAndFigureIsAtomicAndKeepsTheInputDraftUntouched() {
        val input = input()
        val output = MistakeEditOutput(input.basis, MistakeEditSuggestion("修正条件与配图", listOf(
            MistakeEditChange.ReplaceBlock(ContentBlock.Paragraph("stem", "更正后的条件")),
            MistakeEditChange.ReplaceFigure("diagram", FigureGenerationProposal(decision = FigureGenerationDecision.GENERATE,
                purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION, method = FigureGenerationMethod.IMAGE_MODEL,
                fidelity = FigureFidelity.SEMANTIC, imageOperation = FigureImageOperation.GENERATE, imagePrompt = "更新图形")),
        )), "tested-model")
        val figure = StoredFigureReference("diagram", "generated-asset", "a".repeat(64), VisualRole.CLEAN_IMAGE)
        assertTrue(runCatching { applyPreparedMistakeEdit(input, output, emptyList()) }.isFailure)
        val applied = applyPreparedMistakeEdit(input, output, listOf(figure))
        assertEquals("更正后的条件", (applied.document.blocks.first() as ContentBlock.Paragraph).markdown)
        assertEquals(listOf(figure), applied.storedFigureReferences)
        assertEquals("原题条件", (input.draft.document.blocks.first() as ContentBlock.Paragraph).markdown)
        assertTrue(input.draft.storedFigureReferences.isEmpty())
        assertTrue(CapturedQuestionDocumentValidator.validateForCommit(applied).isEmpty())
        assertEquals("tested-model", applied.blockEvidence.first().producerVersion)
    }

    @Test fun reboundOutputAndNonEditResultsAreNeverAdoptable() {
        val input = input()
        val output = MistakeEditOutput(input.basis, MistakeEditSuggestion("无需修改", emptyList(), decision = MistakeEditDecision.NO_CHANGE), "model")
        assertTrue(runCatching { applyPreparedMistakeEdit(input, output, emptyList()) }.isFailure)
        val positive = output.copy(basis = input.basis.copy(draftSha256 = "b".repeat(64)), suggestion = MistakeEditSuggestion("修改文字", listOf(MistakeEditChange.ReplaceBlock(ContentBlock.Paragraph("stem", "新条件")))))
        assertTrue(runCatching { applyPreparedMistakeEdit(input, positive, emptyList()) }.isFailure)
    }
}
