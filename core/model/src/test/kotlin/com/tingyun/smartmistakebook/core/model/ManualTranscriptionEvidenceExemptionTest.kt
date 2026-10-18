package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A8 用户转写合法化：校验器的 provenance 分支（v67）**。
 *
 * 手动建题（用户自己敲题面）没有照片，也没有"这一块在照片里的位置"——空白
 * `source_asset_id` 与缺失区域是**诚实的形态**，不是缺陷。放行**只按 provenance**：
 * `USER_TRANSCRIPTION` 一个来源，其余（本地 OCR / 远程 OCR / 模型解析 / 导入结构、
 * 以及用户**改正** `USER_CORRECTION`）照旧必须指向真实源资产并有区域。
 *
 * 边界同样钉住：转写块若**给了**一个非法区域，仍然按非法拒（放行不是"跳过全部校验"）。
 */
class ManualTranscriptionEvidenceExemptionTest {

    @Test
    fun `user transcription without source asset or region commits and round trips`() {
        val document = manualDocument(provenance = QuestionBlockProvenance.USER_TRANSCRIPTION)

        assertTrue(
            "手敲的块没有源图/没有区域是合法形态：${CapturedQuestionDocumentValidator.validateForCommit(document)}",
            CapturedQuestionDocumentValidator.validateForCommit(document).isEmpty(),
        )
        assertTrue(
            "草稿校验同样放行（否则快照根本存不下来）",
            CapturedQuestionDocumentValidator.validateDraft(document).isEmpty(),
        )
        val decoded = CapturedQuestionDocumentCodec.decode(
            CapturedQuestionDocumentCodec.encode(document),
        )
        assertEquals(document, decoded)
    }

    @Test
    fun `local policy acceptance does not change the transcription exemption`() {
        val document = manualDocument(
            provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
            reviewStatus = QuestionBlockReviewStatus.LOCAL_POLICY_ACCEPTED,
        )

        assertTrue(CapturedQuestionDocumentValidator.validateForCommit(document).isEmpty())
    }

    @Test
    fun `every other provenance still requires a source asset and a region`() {
        val rejected = listOf(
            QuestionBlockProvenance.USER_CORRECTION,
            QuestionBlockProvenance.LOCAL_OCR,
            QuestionBlockProvenance.OPTIONAL_REMOTE_OCR,
            QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
            QuestionBlockProvenance.DETERMINISTIC_RULE,
            QuestionBlockProvenance.IMPORTED_STRUCTURE,
        )
        rejected.forEach { provenance ->
            val document = manualDocument(
                provenance = provenance,
                reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                producerVersion = "fixture-v1",
            )
            val draftCodes = CapturedQuestionDocumentValidator.validateDraft(document)
                .map(CapturedQuestionIssue::code)
                .toSet()
            val commitCodes = CapturedQuestionDocumentValidator.validateForCommit(document)
                .map(CapturedQuestionIssue::code)
                .toSet()
            assertTrue(
                "$provenance 没有源资产必须拒（放行只按 USER_TRANSCRIPTION）",
                CapturedQuestionIssueCode.BLANK_SOURCE_ASSET_ID in draftCodes,
            )
            assertTrue(
                "$provenance 没有区域必须拒",
                CapturedQuestionIssueCode.MISSING_SOURCE_REGION in commitCodes,
            )
        }
    }

    @Test
    fun `a user transcription block with a region is still region validated`() {
        val document = manualDocument(
            provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
        ).copy(
            blockEvidence = listOf(
                manualEvidence(
                    provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                    sourceRegion = NormalizedSourceRegion(0.9, 0.1, 0.2, 1.1),
                ),
            ),
        )

        val codes = CapturedQuestionDocumentValidator.validateForCommit(document)
            .map(CapturedQuestionIssue::code)
            .toSet()
        assertTrue(
            "转写块给了非法区域照样拒：放行不是跳过全部校验",
            CapturedQuestionIssueCode.INVALID_SOURCE_REGION in codes,
        )
        assertFalse(
            "给了区域就不该再报「缺区域」",
            CapturedQuestionIssueCode.MISSING_SOURCE_REGION in codes,
        )
    }

    @Test
    fun `an empty manual document is still rejected as an empty question`() {
        val document = manualDocument(
            provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
            blockMarkdown = "   ",
        )

        val codes = CapturedQuestionDocumentValidator.validateForCommit(document)
            .map(CapturedQuestionIssue::code)
            .toSet()
        assertTrue(
            "空白题面还是空题（转写放行不等于「可以没有内容」）",
            CapturedQuestionIssueCode.EMPTY_QUESTION in codes,
        )
    }

    private fun manualDocument(
        provenance: QuestionBlockProvenance,
        reviewStatus: QuestionBlockReviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
        producerVersion: String? = null,
        blockMarkdown: String = "求函数 \$f(x)=x^2\$ 的单调区间。",
    ) = CapturedQuestionDocument(
        document = QuestionDocument(
            id = "manual-document",
            title = "手敲的题",
            blocks = listOf(ContentBlock.Paragraph("stem", blockMarkdown)),
        ),
        blockEvidence = listOf(
            manualEvidence(
                provenance = provenance,
                reviewStatus = reviewStatus,
                producerVersion = producerVersion,
            ),
        ),
    )

    private fun manualEvidence(
        provenance: QuestionBlockProvenance,
        reviewStatus: QuestionBlockReviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
        producerVersion: String? = null,
        sourceRegion: NormalizedSourceRegion? = null,
    ) = QuestionBlockEvidence(
        blockId = "stem",
        sourceAssetId = "",
        sourceRegion = sourceRegion,
        writingLayer = WritingLayer.UNKNOWN,
        provenance = provenance,
        confidence = null,
        reviewStatus = reviewStatus,
        producerVersion = producerVersion,
    )
}
