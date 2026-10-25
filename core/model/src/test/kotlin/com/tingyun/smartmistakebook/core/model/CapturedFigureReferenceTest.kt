package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-02（4E）：转写期图形的**类型化引用**契约。
 *
 * 覆盖：
 * - 同型 proposal 的形状判据（单一来源：解析期与复用期共用）；
 * - 引用构造的绑定校验（源页/裁切范围/草稿修订/源图 sha）；
 * - 编解码回环与**指纹稳定性**（空引用不落新键——已落库文档的读回指纹必须不变，
 *   `RoomMistakeDetailRepository` / `RoomMistakeOrganizationRepository` 都按
 *   decode→重算→比对存库指纹判损坏，多一个空键就会把全部旧题判成 CorruptSnapshot）；
 * - 保存（提交门）不因引用而拒绝：保存后引用仍在。
 */
class CapturedFigureReferenceTest {

    /** 直接构造 proposal（不过引用构造层）——形状判据的负向用例必须在构造前就能断言。 */
    private fun proposal(
        decision: FigureGenerationDecision = FigureGenerationDecision.GENERATE,
        method: FigureGenerationMethod? = FigureGenerationMethod.IMAGE_MODEL,
        program: String? = null,
        imageOperation: FigureImageOperation? = FigureImageOperation.EDIT,
        imagePrompt: String? = "重绘该区域内的函数图像与坐标轴",
        policyVersion: String = FigureGenerationPolicyVersion.VALUE,
    ): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = policyVersion,
        decision = decision,
        purpose = FigureGenerationPurpose.SOURCE_TRANSCRIPTION,
        method = method,
        reason = "原图含函数图像，无法结构化重建",
        imageOperation = imageOperation,
        imagePrompt = imagePrompt,
        program = program,
    )

    private fun imageModelReference(
        proposal: FigureGenerationProposal = proposal(),
    ): CapturedFigureReference = CapturedFigureReference(
        proposal = proposal,
        sourceAssetId = "asset-1",
        sourcePageIndex = 0,
        sourceRegion = NormalizedSourceRegion(0.1, 0.1, 0.9, 0.9),
        sourceImageSha256 = "a".repeat(64),
        basisRevisionNumber = 2,
    )

    private fun jsReference(): CapturedFigureReference = imageModelReference(
        proposal(
            method = FigureGenerationMethod.JS,
            program = "renderScene({});",
            imageOperation = null,
            imagePrompt = null,
        ),
    )

    private fun document(
        figureReferences: List<CapturedFigureReference> = emptyList(),
        reviewStatus: QuestionBlockReviewStatus = QuestionBlockReviewStatus.LOCAL_POLICY_ACCEPTED,
    ): CapturedQuestionDocument = CapturedQuestionDocument(
        document = QuestionDocument(
            id = "document-1",
            title = "函数题",
            blocks = listOf(ContentBlock.Paragraph(id = "stem", markdown = "已知函数 f(x)。")),
        ),
        blockEvidence = listOf(
            QuestionBlockEvidence(
                blockId = "stem",
                sourceAssetId = "asset-1",
                sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                writingLayer = WritingLayer.PRINTED,
                provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
                reviewStatus = reviewStatus,
                producerVersion = "model-1",
            ),
        ),
        figureReferences = figureReferences,
    )

    @Test
    fun `image model proposal passes the shared shape check`() {
        assertNull(validateCapturedFigureProposalShape(proposal()))
    }

    @Test
    fun `js proposal is recordable but never advertised`() {
        // 转写选 JS 字段位：形状合法（仅记录不执行——执行门在能力校验，本构建 JS UNAVAILABLE）。
        assertNull(
            validateCapturedFigureProposalShape(
                proposal(
                    method = FigureGenerationMethod.JS,
                    program = "renderScene({});",
                    imageOperation = null,
                    imagePrompt = null,
                ),
            ),
        )
    }

    @Test
    fun `no figure decision carries no method or payload`() {
        assertNull(
            validateCapturedFigureProposalShape(
                proposal(
                    decision = FigureGenerationDecision.NO_FIGURE,
                    method = null,
                    imageOperation = null,
                    imagePrompt = null,
                ),
            ),
        )
    }

    @Test
    fun `policy version mismatch is rejected`() {
        assertEquals(
            CapturedFigureProposalShapeIssue.POLICY_VERSION_MISMATCH,
            validateCapturedFigureProposalShape(
                proposal(policyVersion = "figure-generation-policy-v0"),
            ),
        )
    }

    @Test
    fun `generate without method is rejected`() {
        assertEquals(
            CapturedFigureProposalShapeIssue.GENERATE_WITHOUT_METHOD,
            validateCapturedFigureProposalShape(
                proposal(method = null, imageOperation = null, imagePrompt = null),
            ),
        )
    }

    @Test
    fun `js payload mixing image fields is rejected`() {
        assertEquals(
            CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH,
            validateCapturedFigureProposalShape(
                proposal(
                    method = FigureGenerationMethod.JS,
                    program = "renderScene({});",
                    imagePrompt = "不应出现",
                ),
            ),
        )
    }

    @Test
    fun `image model without paired operation and prompt is rejected`() {
        assertEquals(
            CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH,
            validateCapturedFigureProposalShape(proposal(imagePrompt = null)),
        )
        assertEquals(
            CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH,
            validateCapturedFigureProposalShape(proposal(program = "renderScene({});")),
        )
    }

    @Test
    fun `non generating decision with method or payload is rejected`() {
        assertEquals(
            CapturedFigureProposalShapeIssue.DECISION_WITH_PAYLOAD,
            validateCapturedFigureProposalShape(proposal(decision = FigureGenerationDecision.NO_FIGURE)),
        )
    }

    @Test
    fun `oversized image prompt is rejected by utf8 budget`() {
        val oversized = "x".repeat(FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES + 1)
        assertEquals(
            CapturedFigureProposalShapeIssue.IMAGE_PROMPT_OVER_BUDGET,
            validateCapturedFigureProposalShape(proposal(imagePrompt = oversized)),
        )
    }

    @Test
    fun `reference constructor rejects invalid bindings`() {
        assertThrows<IllegalArgumentException> {
            imageModelReference().copy(sourceAssetId = "")
        }
        assertThrows<IllegalArgumentException> {
            imageModelReference().copy(sourceImageSha256 = "not-a-sha")
        }
        assertThrows<IllegalArgumentException> {
            imageModelReference().copy(basisRevisionNumber = 0)
        }
        assertThrows<IllegalArgumentException> {
            imageModelReference().copy(sourcePageIndex = -1)
        }
        assertThrows<IllegalArgumentException> {
            imageModelReference().copy(
                sourceRegion = NormalizedSourceRegion(0.5, 0.1, 0.4, 0.9),
            )
        }
    }

    @Test
    fun `reference constructor rejects an invalid proposal shape`() {
        assertThrows<IllegalArgumentException> {
            imageModelReference(
                proposal(method = null, imageOperation = null, imagePrompt = null),
            )
        }
    }

    @Test
    fun `document without references encodes without the new key`() {
        val encoded = CapturedQuestionDocumentCodec.encode(document())
        assertFalse(encoded.contains("figureReferences"))
        val decoded = CapturedQuestionDocumentCodec.decode(encoded)
        assertTrue(decoded.figureReferences.isEmpty())
        // 指纹稳定性：decode→重编码必须逐字相同（旧文档读回重算指纹的口径）。
        assertEquals(encoded, CapturedQuestionDocumentCodec.encode(decoded))
    }

    @Test
    fun `references survive the codec round trip and stay after commit validation`() {
        val withReferences = document(
            figureReferences = listOf(imageModelReference(), jsReference()),
        )
        val decoded = CapturedQuestionDocumentCodec.decode(
            CapturedQuestionDocumentCodec.encode(withReferences),
        )
        assertEquals(withReferences.figureReferences, decoded.figureReferences)
        assertTrue(CapturedQuestionDocumentValidator.validateDraft(decoded).isEmpty())
        assertTrue(CapturedQuestionDocumentValidator.validateForCommit(decoded).isEmpty())
    }

    @Test
    fun `too many references are flagged by draft validation`() {
        val oversized = document(
            figureReferences = List(CapturedFigureReference.MAX_FIGURE_REFERENCES + 1) {
                imageModelReference()
            },
        )
        val issues = CapturedQuestionDocumentValidator.validateDraft(oversized)
        assertTrue(
            issues.any { it.code == CapturedQuestionIssueCode.FIGURE_REFERENCE_BUDGET_EXCEEDED },
        )
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (expected: Throwable) {
            assertTrue(
                "expected ${T::class.simpleName} but got ${expected::class.simpleName}",
                expected is T,
            )
            return
        }
        throw AssertionError("expected ${T::class.simpleName} but nothing was thrown")
    }
}
