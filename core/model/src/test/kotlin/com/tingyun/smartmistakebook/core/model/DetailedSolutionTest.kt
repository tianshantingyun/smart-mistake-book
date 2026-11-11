package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.*
import org.junit.Test

class DetailedSolutionTest {
    @Test fun legacyDefaultsKeepTheOriginalSerializedShape() {
        val encoded = DetailedSolutionCodec.encodeDocument(document)
        assertFalse(encoded.contains("teachingFigureOmission"))
        assertFalse(encoded.contains("imageModelUnavailableAtPreparation"))
        val input = DetailedSolutionInput(FrozenExportQuestion(1, "entry", basis, question))
        val encodedInput = DetailedSolutionCodec.json.encodeToString(DetailedSolutionInput.serializer(), input)
        assertFalse(encodedInput.contains("availableFigureMethods"))
        assertFalse(encodedInput.contains("existingGenericAnswer"))
    }
    private val question = QuestionDocument("q", "算术", listOf(ContentBlock.Paragraph("stem", "1+1=?")))
    private val basis = SolutionBasis("rev", solutionQuestionSha256(question, emptyList()))
    private val document = DetailedSolutionDocument(basis, "2", QuestionDocument("steps", blocks = listOf(ContentBlock.Paragraph("step-1", "1+1=2"))),
        origin = SolutionOrigin.MODEL, verification = SolutionVerification.UNVERIFIED)

    @Test fun exactCompleteSolutionReusesButChangedQuestionDoesNot() {
        assertTrue(canReuseSolution(document, basis))
        assertFalse(canReuseSolution(document, basis.copy(questionSha256 = "a".repeat(64))))
        assertFalse(canReuseSolution(document, basis, revoked = true))
        assertEquals(document, DetailedSolutionCodec.decodeDocument(DetailedSolutionCodec.encodeDocument(document)))
    }

    @Test fun missingStepsRemoteImagesAndUnpreparedFiguresAreRejected() {
        assertTrue(runCatching { document.copy(steps = QuestionDocument("empty", blocks = emptyList())) }.isFailure)
        assertTrue(runCatching { document.copy(finalAnswerMarkdown = "![answer](https://example.com/answer.png)") }.isFailure)
        assertFalse(canReuseSolution(document.copy(requiredFigureBlockIds = setOf("step-1")), basis))
    }

    @Test fun reorderingThePaperAndRefreshingCapabilitiesKeepsOneDispatchBudget() {
        val first = DetailedSolutionInput(FrozenExportQuestion(1, "entry", basis, question), "capability-v1")
        val reordered = first.copy(question = first.question.copy(ordinal = 7), figureGenerationPromptBody = "capability-v2")
        assertEquals(ModelTaskLogicalOperationFingerprint.of(first), ModelTaskLogicalOperationFingerprint.of(reordered))
        assertEquals(solutionQuestionSha256(question, emptyList()), solutionQuestionSha256(question.copy(id = "renamed", blocks = listOf(ContentBlock.Paragraph("renamed-stem", "1+1=?"))), emptyList()))
    }
    @Test fun historicTextRemainsReadableButCannotCompleteIllustratedExport() {
        val historic = DetailedSolutionCodec.decodeDocument(DetailedSolutionCodec.encodeDocument(document))
        assertTrue(canReuseSolution(historic, basis))
        assertFalse(canReuseSolution(historic, basis, requireTeachingFigure = true))
        val omitted = historic.copy(teachingFigureOmission = TeachingFigureOmission("target", "complex structure", "image model unavailable"), imageModelUnavailableAtPreparation = true)
        assertTrue(canReuseSolution(omitted, basis, requireTeachingFigure = true))
        assertTrue(runCatching { omitted.copy(imageModelUnavailableAtPreparation = false) }.isFailure)
        val input = DetailedSolutionInput(FrozenExportQuestion(1, "entry", basis, question), existingGenericAnswer = historic)
        assertEquals(ModelTaskLogicalOperationFingerprint.of(input), ModelTaskLogicalOperationFingerprint.of(input.copy(existingGenericAnswer = null)))
    }
    @Test fun enrichmentRequiresExactExistingTextAndFrozenCapabilityFacts() {
        val input = DetailedSolutionInput(FrozenExportQuestion(1, "entry", basis, question), availableFigureMethods = emptySet(), existingGenericAnswer = document)
        assertTrue(canEnrichGenericSolution(document, input))
        assertFalse(canEnrichGenericSolution(document, input.copy(existingGenericAnswer = null)))
        assertFalse(canEnrichGenericSolution(document, input.copy(availableFigureMethods = null)))
        assertFalse(canEnrichGenericSolution(document.copy(finalAnswerMarkdown = "changed"), input))
        val complete = document.copy(teachingFigureOmission = TeachingFigureOmission("target", "complex structure", "unavailable"), imageModelUnavailableAtPreparation = true)
        assertFalse(canEnrichGenericSolution(complete, input.copy(existingGenericAnswer = complete)))
    }
}
