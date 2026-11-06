package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MistakeEditTasksTest {
    private val document = CapturedQuestionDocument(document = QuestionDocument("question", blocks = listOf(ContentBlock.Paragraph("stem", "原题"))), blockEvidence = listOf(evidence("stem")))
    private fun basis() = EditBasis("entry", "revision", CapturedQuestionDocumentFingerprint.of(document))

    @Test fun operationIdentityIncludesMeaningfulInstructionSourceAndExactDraft() {
        val first = editOperationKey(basis(), "stem", "b".repeat(64), "标出顶点")
        assertEquals(first, editOperationKey(basis(), "stem", "b".repeat(64), "  标出顶点\r\n"))
        assertNotEquals(first, editOperationKey(basis(), "stem", "b".repeat(64), "标出对称轴"))
        assertNotEquals(first, editOperationKey(basis(), "stem", "c".repeat(64), "标出顶点"))
        assertNotEquals(first, editOperationKey(basis().copy(draftSha256 = "d".repeat(64)), "stem", "b".repeat(64), "标出顶点"))
        assertNotEquals(editOperationKey(basis().copy(entryId = "a|b", revisionId = "c"), "stem", null, "修改"),
            editOperationKey(basis().copy(entryId = "a", revisionId = "b|c"), "stem", null, "修改"))
    }

    @Test fun invalidBasisInstructionAndTargetAreRejectedBeforeDispatch() {
        assertTrue(runCatching { EditBasis("entry", "revision", "bad") }.isFailure)
        assertTrue(runCatching { editOperationKey(basis(), "stem", null, "x".repeat(4001)) }.isFailure)
        assertTrue(runCatching { MistakeEditInput(basis(), document, setOf("new-block"), "修改") }.isFailure)
        assertTrue(runCatching { MistakeEditInput(basis().copy(draftSha256 = "a".repeat(64)), document, setOf("stem"), "修改") }.isFailure)
    }

    @Test fun adoptedFigureFieldIsOmittedWhenEmptyAndRoundTripsWhenPresent() {
        val json = Json { encodeDefaults = true }
        val old = json.encodeToString(CapturedQuestionDocument.serializer(), document)
        assertFalse(old.contains("storedFigureReferences"))
        val figure = StoredFigureReference("stem", "asset-1", "a".repeat(64), VisualRole.CLEAN_IMAGE)
        val updated = document.copy(storedFigureReferences = listOf(figure))
        assertEquals(updated, json.decodeFromString(CapturedQuestionDocument.serializer(), json.encodeToString(CapturedQuestionDocument.serializer(), updated)))
        assertNotEquals(CapturedQuestionDocumentFingerprint.of(document), CapturedQuestionDocumentFingerprint.of(updated))
        assertEquals(document, json.decodeFromString(CapturedQuestionDocument.serializer(), old))
    }

    @Test fun newTaskRequiresActualGlobalConsentAndImageCapabilityWhenItCarriesImages() {
        val input = MistakeEditInput(basis(), document, setOf("stem"), "修改")
        assertEquals(ModelTaskKind.MISTAKE_EDIT, input.kind)
        assertTrue(input.isAgentConsentEligible)
        assertFalse(input.requestsImageBytes)
        assertTrue(input.copy(sourceImages = listOf(ModelEgressAssetGrant("image", "a".repeat(64), 20, 10, 10))).requestsImageBytes)
    }
    @Test fun capabilityAndConsentFailuresRemainDistinct() {
        val input = MistakeEditInput(basis(), document, setOf("stem"), "修改")
        val request = ModelTaskRequest(requestId = "edit", input = input, occurredAtEpochMillis = 1, agentConsentGranted = true)
        val provider = ProviderCapabilitySnapshot("provider", "模型", "model", emptySet(), true, true, false)
        val missing = runCatching { ModelEgressPolicy.authorize(request, provider) }.exceptionOrNull() as ModelEgressAuthorizationException
        assertEquals(ModelFailureCode.PROVIDER_CAPABILITY_MISSING, missing.failureCode)
        val supported = provider.copy(supportedTasks = setOf(ModelTaskKind.MISTAKE_EDIT))
        val consent = runCatching { ModelEgressPolicy.authorize(request.copy(agentConsentGranted = false), supported) }.exceptionOrNull() as ModelEgressAuthorizationException
        assertEquals(ModelFailureCode.EGRESS_AUTHORIZATION_REQUIRED, consent.failureCode)
        assertEquals(ModelExecutionPermit.ProviderConsented, ModelEgressPolicy.authorize(request, supported).permit)
    }

    @Test fun typedTaskRoundTripsAndDurableCompletionRejectsReboundOrUnselectedChanges() {
        val input = MistakeEditInput(basis(), document, setOf("stem"), "修改")
        val request = ModelTaskRequest(requestId = "edit-roundtrip", input = input, occurredAtEpochMillis = 1, agentConsentGranted = true)
        assertEquals(request, ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(request)))
        val output = MistakeEditOutput(input.basis, MistakeEditSuggestion("修正文字", listOf(MistakeEditChange.ReplaceBlock(ContentBlock.Paragraph("stem", "新题面")))), "model")
        assertEquals(output, ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(output)))
        assertTrue(ModelTaskCompletionValidator.validate(request, output).isEmpty())
        assertFalse(ModelTaskCompletionValidator.validate(request, output.copy(basis = output.basis.copy(revisionId = "other"))).isEmpty())
        assertFalse(ModelTaskCompletionValidator.validate(request, output.copy(suggestion = output.suggestion.copy(changes = listOf(MistakeEditChange.ReplaceBlock(ContentBlock.Paragraph("new-block", "新题")))))).isEmpty())
    }

    @Test fun ordinaryMathComparisonsAreAllowedWhileExecutableMarkupIsRejected() {
        requireEditText("已知 a<b 且 c>d，求可行范围。", 1_000)
        assertTrue(runCatching { requireEditText("<script>alert(1)</script>", 1_000) }.isFailure)
        assertTrue(runCatching { requireEditText("ftp://example.com/private", 1_000) }.isFailure)
        assertTrue(runCatching { requireEditText("![图](https://example.com/image.png)", 1_000) }.isFailure)
    }

    @Test fun typedCompletionCannotChangeChoiceInteractionFlags() {
        val choices = ContentBlock.ChoiceGroup("choices", "选择答案", listOf(StructuredChoice("a", "A"), StructuredChoice("b", "B")))
        val captured = CapturedQuestionDocument(document = QuestionDocument("question", blocks = listOf(choices)), blockEvidence = listOf(evidence("choices")))
        val input = MistakeEditInput(EditBasis("entry", "revision", CapturedQuestionDocumentFingerprint.of(captured)), captured, setOf("choices"), "澄清选项")
        val invalid = MistakeEditSuggestion("修改选项", listOf(MistakeEditChange.ReplaceBlock(choices.copy(choices = choices.choices.map { it.copy(enabled = false) }))))
        assertTrue(runCatching { invalid.requireMatches(input) }.isFailure)
    }

    private fun evidence(id: String) = QuestionBlockEvidence(
        blockId = id, sourceAssetId = "", sourceRegion = null, writingLayer = WritingLayer.UNKNOWN,
        provenance = QuestionBlockProvenance.USER_TRANSCRIPTION, confidence = null,
        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED, producerVersion = null,
    )
}
