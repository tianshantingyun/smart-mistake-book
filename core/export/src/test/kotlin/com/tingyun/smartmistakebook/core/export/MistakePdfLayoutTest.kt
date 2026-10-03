package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版式参数模型（B1）的 JVM 用例：合法性一处校验、身份一处序列化、指纹含全字段。
 *
 * 为什么这些必须在 JVM 上钉住：布局是缓存键的一部分——两个不同版式若指纹相同，
 * 学生会拿到上一版式生成的 PDF 却以为换了版式；非法参数若不在渲染前拒掉，会一路带进
 * 分页器（例如 fontScale=0 → 行高为 0，正文会堆叠成一页看不见的零高行）。
 */
class MistakePdfLayoutTest {

    @Test
    fun `default layout serializes to a stable canonical form`() {
        val expected = "templateId=compact;marginPt=48;fontScale=2;columnCount=1;" +
            "imageScaleBits=1065353216;blockOrder=;includeAnswer=false;includeSolution=false;" +
            "includeNote=false"

        assertEquals(expected, MistakePdfLayout.DEFAULT.canonicalForm())
        assertEquals(expected, MistakePdfLayout().canonicalForm())
        assertTrue(MistakePdfLayout.DEFAULT.validate().isEmpty())
    }

    @Test
    fun `out of range and unknown values are rejected by validate`() {
        assertEquals(
            setOf(MistakePdfLayoutViolation.MARGIN_OUT_OF_RANGE),
            MistakePdfLayout(marginPt = 100).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.MARGIN_OUT_OF_RANGE),
            MistakePdfLayout(marginPt = 23).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.FONT_SCALE_OUT_OF_RANGE),
            MistakePdfLayout(fontScale = 0).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.FONT_SCALE_OUT_OF_RANGE),
            MistakePdfLayout(fontScale = 4).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.COLUMN_COUNT_UNSUPPORTED),
            MistakePdfLayout(columnCount = 3).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.IMAGE_SCALE_OUT_OF_RANGE),
            MistakePdfLayout(imageScale = 0.49f).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.IMAGE_SCALE_OUT_OF_RANGE),
            MistakePdfLayout(imageScale = Float.NaN).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.UNKNOWN_TEMPLATE),
            MistakePdfLayout(templateId = "poster").validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.UNKNOWN_BLOCK_ORDER_ENTRY),
            MistakePdfLayout(blockOrder = listOf("figure", "banana")).validate(),
        )
        assertEquals(
            setOf(MistakePdfLayoutViolation.DUPLICATE_BLOCK_ORDER_ENTRY),
            MistakePdfLayout(blockOrder = listOf("figure", "figure")).validate(),
        )
    }

    @Test
    fun `every documented template and boundary value is accepted`() {
        MistakePdfLayout.TEMPLATE_IDS.forEach { template ->
            assertTrue(
                "模板 $template 必须是合法布局",
                MistakePdfLayout(templateId = template).validate().isEmpty(),
            )
        }
        assertTrue(MistakePdfLayout(marginPt = 24).validate().isEmpty())
        assertTrue(MistakePdfLayout(marginPt = 72).validate().isEmpty())
        assertTrue(MistakePdfLayout(fontScale = 1).validate().isEmpty())
        assertTrue(MistakePdfLayout(fontScale = 3).validate().isEmpty())
        assertTrue(MistakePdfLayout(columnCount = 2).validate().isEmpty())
        assertTrue(MistakePdfLayout(imageScale = 0.5f).validate().isEmpty())
        assertTrue(MistakePdfLayout(imageScale = 1.0f).validate().isEmpty())
        assertTrue(
            MistakePdfLayout(blockOrder = MistakePdfBlockKind.entries.map { it.token })
                .validate()
                .isEmpty(),
        )
    }

    @Test
    fun `the export input refuses an invalid layout before rendering`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MistakePdfExportInput(
                errorBookEntryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = "revision-1",
                revisionNumber = 1,
                title = "二次函数",
                subject = "数学",
                subtitle = "数学 · 第 1 版",
                documentTitle = null,
                blocks = listOf(MistakePdfBlock.Paragraph(id = "p1", text = "题目")),
                questionDocumentSha256 = "0".repeat(64),
                inputSha256 = "1".repeat(64),
                layout = MistakePdfLayout(marginPt = 100),
            )
        }

        assertTrue(failure.message.orEmpty().contains("MARGIN_OUT_OF_RANGE"))
    }

    @Test
    fun `same content with a different layout gets a different export fingerprint`() {
        val defaultLayout = MistakePdfLayout.DEFAULT
        val practiceLayout = MistakePdfLayout(
            templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
            columnCount = 2,
        )
        val noteLayout = defaultLayout.copy(includeNote = true)

        val defaultInput = eligibleInput(defaultLayout)
        val practiceInput = eligibleInput(practiceLayout)
        val noteInput = eligibleInput(noteLayout)

        assertNotEquals(defaultInput.inputSha256, practiceInput.inputSha256)
        assertNotEquals(defaultInput.inputSha256, noteInput.inputSha256)
        assertNotEquals(practiceInput.inputSha256, noteInput.inputSha256)
        // 同内容同版式必须稳定复现（否则预渲染缓存永远命不中）。
        assertEquals(defaultInput.inputSha256, eligibleInput(defaultLayout).inputSha256)
    }

    /**
     * P2-1：imageScale 用无损位表示进指纹。改前 `%.4f` 量化会让两个合法值（差 <5e-5）
     * 指纹相同——"换了参数却没换缓存键"，学生拿到旧版式的文件。
     */
    @Test
    fun `every distinct float image scale changes the fingerprint`() {
        val lower = MistakePdfLayout(imageScale = 0.50001f)
        val higher = MistakePdfLayout(imageScale = 0.50002f)

        assertTrue(lower.validate().isEmpty())
        assertTrue(higher.validate().isEmpty())
        assertNotEquals(lower.canonicalForm(), higher.canonicalForm())
        assertNotEquals(
            eligibleInput(lower).inputSha256,
            eligibleInput(higher).inputSha256,
        )
    }

    @Test
    fun `eligibility rejects an invalid layout fail closed`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MistakePdfEligibility.check(readyState(), MistakePdfLayout(columnCount = 7))
        }

        assertTrue(failure.message.orEmpty().contains("COLUMN_COUNT_UNSUPPORTED"))
    }

    @Test
    fun `the batch fingerprint includes the batch layout`() {
        val states = listOf(readyState())
        val singleColumn = MistakePdfBatchEligibility.check(states) as
            MistakePdfBatchEligibilityResult.Eligible
        val twoColumn = MistakePdfBatchEligibility.check(
            states,
            MistakePdfLayout(columnCount = 2),
        ) as MistakePdfBatchEligibilityResult.Eligible

        assertNotEquals(singleColumn.input.inputSha256, twoColumn.input.inputSha256)
        assertEquals(2, twoColumn.input.layout.columnCount)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            MistakePdfBatchEligibility.check(states, MistakePdfLayout(fontScale = 9))
        }
        assertTrue(failure.message.orEmpty().contains("FONT_SCALE_OUT_OF_RANGE"))
    }

    // ---- fixtures ----

    private fun eligibleInput(layout: MistakePdfLayout): MistakePdfExportInput =
        (
            MistakePdfEligibility.check(readyState(), layout)
                as MistakePdfEligibilityResult.Eligible
            ).input

    private fun readyState(): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem", "已知函数满足 \$f(x)=x^2-2x\$。")
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-layout",
                    problemId = "problem-layout",
                    problemRevisionId = "revision-layout",
                    revisionNumber = 2,
                    title = "二次函数",
                    subject = "数学",
                ),
                fallbackMarkdown = "绝不能导出的 fallback",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-layout",
                    title = "二次函数",
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-layout",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }
}
