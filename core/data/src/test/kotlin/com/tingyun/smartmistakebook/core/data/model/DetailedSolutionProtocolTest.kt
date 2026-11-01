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
}
