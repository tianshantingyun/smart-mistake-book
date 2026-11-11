package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class DetailedSolutionProtocolTest {
    private val question = QuestionDocument("q", blocks = listOf(ContentBlock.Paragraph("stem", "1+1=?")))
    private val input = DetailedSolutionInput(FrozenExportQuestion(1, "entry", SolutionBasis("rev", solutionQuestionSha256(question, emptyList())), question))
    private val wire = """{"finalAnswerMarkdown":"2","steps":[{"type":"paragraph","markdown":"1+1=2"}]}"""

    @Test fun hostOwnsBasisOriginAndVerification() {
        val result = OpenAiDetailedSolutionProtocol.parse(Json.parseToJsonElement(wire).jsonObject, input, "tested-model")
        assertEquals(input.question.basis, result.document.basis)
        assertEquals(SolutionOrigin.MODEL, result.document.origin)
        assertEquals(SolutionVerification.UNVERIFIED, result.document.verification)
        assertTrue(canReuseSolution(result.document, input.question.basis))
    }

    @Test fun extraAuthorityFieldsAndMissingFinalAnswerNeverBecomeReady() {
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(Json.parseToJsonElement(wire.dropLast(1) + """, "verification":"VERIFIED"}""").jsonObject, input, "model") }.isFailure)
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(Json.parseToJsonElement("""{"steps":[]}""").jsonObject, input, "model") }.isFailure)
    }
    @Test fun frozenCapabilitiesPreventSilentTextOnlyCompletion() {
        val current = input.copy(availableFigureMethods = setOf(FigureGenerationMethod.JS))
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(Json.parseToJsonElement(wire).jsonObject, current, "model") }.isFailure)
    }

    @Test fun omissionRequiresActualImageModelAbsence() {
        val payload = Json.parseToJsonElement(wire.dropLast(1) + """, "teachingFigureOmission":{"instructionalTarget":"structure","jsLimitation":"complex natural morphology","reason":"image model unavailable"}}""").jsonObject
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(payload, input, "model") }.isFailure)
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(payload, input.copy(availableFigureMethods = setOf(FigureGenerationMethod.IMAGE_MODEL)), "model") }.isFailure)
        val result = OpenAiDetailedSolutionProtocol.parse(payload, input.copy(availableFigureMethods = setOf(FigureGenerationMethod.JS)), "model")
        assertTrue(result.document.imageModelUnavailableAtPreparation)
        assertEquals("structure", result.document.teachingFigureOmission?.instructionalTarget)
    }
    @Test fun unavailableRuntimeCannotClaimComplexOmission() {
        val payload = Json.parseToJsonElement(wire.dropLast(1) + """, "teachingFigureOmission":{"instructionalTarget":"structure","jsLimitation":"complex natural morphology","reason":"image model unavailable"}}""").jsonObject
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(payload, input.copy(availableFigureMethods = emptySet()), "model") }.isFailure)
    }

    @Test fun promptExposesOptionalOmissionAndRetainsGenericEnrichmentContext() {
        val existing = OpenAiDetailedSolutionProtocol.parse(Json.parseToJsonElement(wire).jsonObject, input, "old-model").document
        val enriched = input.copy(existingGenericAnswer = existing, availableFigureMethods = emptySet())
        val prompt = OpenAiDetailedSolutionProtocol.prompt(enriched)
        assertTrue(prompt.contains("teachingFigureOmission"))
        assertTrue(prompt.contains(existing.finalAnswerMarkdown))
        assertTrue(prompt.contains("jsLimitation"))
        val changed = Json.parseToJsonElement(wire.replace("1+1=2", "changed") .dropLast(1) + """, "teachingFigureOmission":{"instructionalTarget":"structure","jsLimitation":"complex morphology","reason":"unavailable"}}""").jsonObject
        assertTrue(runCatching { OpenAiDetailedSolutionProtocol.parse(changed, enriched.copy(availableFigureMethods = setOf(FigureGenerationMethod.JS)), "new-model") }.isFailure)
    }
}
