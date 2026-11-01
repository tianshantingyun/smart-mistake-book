package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MistakeEditProtocolTest {
    private val draft = CapturedQuestionDocument(document = QuestionDocument("local-question", blocks = listOf(ContentBlock.Paragraph("local-block", "原题"), ContentBlock.Paragraph("other-block", "保留内容"))), blockEvidence = listOf(evidence("local-block"), evidence("other-block")))
    private val input = MistakeEditInput(EditBasis("local-entry", "local-revision", CapturedQuestionDocumentFingerprint.of(draft)), draft, setOf("local-block"), "更清楚地说明条件")
    private val json = Json
    private fun payload(text: String) = json.parseToJsonElement(text).jsonObject

    @Test fun parserMapsOnlyHostAllocatedAliasesAndBindsTheExactBasis() {
        val parsed = OpenAiMistakeEditProtocol.parse(payload("""{"summaryMarkdown":"补充条件","changes":[{"type":"paragraph","target":"block-1","markdown":"已澄清的条件"}]}"""), input, "tested-model")
        assertEquals(input.basis, parsed.basis)
        assertEquals(ContentBlock.Paragraph("local-block", "已澄清的条件"), (parsed.suggestion.changes.single() as MistakeEditChange.ReplaceBlock).block)
        val prompt = OpenAiMistakeEditProtocol.prompt(input)
        assertFalse(prompt.contains("local-entry")); assertFalse(prompt.contains("local-block")); assertFalse(prompt.contains("local-revision"))
        assertTrue(prompt.contains("block-1")); assertTrue(prompt.contains("保留内容"))
    }

    @Test fun unselectedAliasesNewIdsExtraFieldsAndArbitraryUrisAreRejected() {
        val base = """{"summaryMarkdown":"修改","changes":[{"type":"paragraph","target":"block-1","markdown":"修改后的题面"}]}"""
        for (invalid in listOf(
            base.replace("block-1", "block-2"), base.replace("block-1", "invented-block"),
            base.replace("\"summaryMarkdown\":", "\"problemId\":\"new-question\",\"summaryMarkdown\":"),
            base.replace("修改后的题面", "![图](file:///private/image.png)"),
            base.replace("\"markdown\":", "\"assetUri\":\"https://example.com/a.png\",\"markdown\":"),
        )) assertTrue(invalid, runCatching { OpenAiMistakeEditProtocol.parse(payload(invalid), input, "model") }.isFailure)
    }

    @Test fun figuresUseTheTypedSharedProposalInsteadOfEmbeddingSourceInText() {
        val parsed = OpenAiMistakeEditProtocol.parse(payload("""{"summaryMarkdown":"增加辅助图","changes":[{"type":"figure","target":"block-1","proposal":{"policyVersion":"figure-generation-policy-v1","decision":"GENERATE","purpose":"SOURCE_RECONSTRUCTION","method":"JS","fidelity":"SEMANTIC","program":"draw.line(0,0,1,1)","reason":"线段示意"}}]}"""), input, "model")
        assertEquals(FigureGenerationMethod.JS, (parsed.suggestion.changes.single() as MistakeEditChange.ReplaceFigure).proposal.method)
        assertTrue(runCatching { OpenAiMistakeEditProtocol.parse(payload("""{"summaryMarkdown":"修改","changes":[{"type":"paragraph","target":"block-1","markdown":"```js draw.line(0,0,1,1) ```"}]}"""), input, "model") }.isFailure)
    }

    @Test fun truncatedJsonOrAnEmptyPatchCannotBecomeASuccessfulSuggestion() {
        assertTrue(runCatching { OpenAiMistakeEditProtocol.parse(payload("{\"changes\":["), input, "model") }.isFailure)
        assertTrue(runCatching { OpenAiMistakeEditProtocol.parse(payload("""{"summaryMarkdown":"无需修改","changes":[]}"""), input, "model") }.isFailure)
    }
    @Test fun explicitNoChangeOrMissingSourceDoesNotPretendToApplyAnEmptyPatch() {
        for (decision in listOf("NO_CHANGE", "NEED_SOURCE", "UNAVAILABLE")) {
            val parsed = OpenAiMistakeEditProtocol.parse(payload("""{"decision":"$decision","summaryMarkdown":"需要核对原图后才能修改","changes":[]}"""), input, "model")
            assertTrue(parsed.suggestion.changes.isEmpty())
            assertEquals("需要核对原图后才能修改", parsed.suggestion.summaryMarkdown)
        }
        assertTrue(runCatching { OpenAiMistakeEditProtocol.parse(payload("""{"decision":"NO_CHANGE","summaryMarkdown":"无需修改","changes":[{"type":"paragraph","target":"block-1","markdown":"偷偷修改"}]}"""), input, "model") }.isFailure)
    }

    private fun evidence(id: String) = QuestionBlockEvidence(
        blockId = id, sourceAssetId = "", sourceRegion = null, writingLayer = WritingLayer.UNKNOWN,
        provenance = QuestionBlockProvenance.USER_TRANSCRIPTION, confidence = null,
        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED, producerVersion = null,
    )
}
