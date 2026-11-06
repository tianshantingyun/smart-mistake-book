package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.*
import org.junit.Test

class DetailedSolutionTest {
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
}
