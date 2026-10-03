package com.tingyun.smartmistakebook.core.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureAxis
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B2 的字节层证据：同一份内容用三个模板各生成一份真实 PDF，三份互不相同且都通过完整性核对。
 * 计划层差异（JVM）见 `MistakePdfPagePlannerLayoutTest`；这里补的是"确实画进了 PDF"。
 */
@RunWith(AndroidJUnit4::class)
class MistakePdfLayoutTemplatesInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun threeTemplatesProduceDistinctVerifiedPdfs() {
        val compact = prepare(MistakePdfLayout.DEFAULT)
        // 前置：先证明同内容同版式两次独立渲染是确定性的；否则"三模板互异"可能只是
        // 每次渲染都不同（非确定性）造成的空转通过。
        val compactAgain = prepare(MistakePdfLayout.DEFAULT)
        assertEquals(
            "同内容同版式的两次独立渲染必须字节一致（确定性前置）",
            compact.sha256,
            compactAgain.sha256,
        )
        val practice = prepare(
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET),
        )
        val withAnswers = prepare(
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_WITH_ANSWERS),
        )

        listOf(compact, practice, withAnswers).forEach { prepared ->
            assertTrue(prepared.verifyIntegrity())
            assertTrue(prepared.pageCount >= 1)
        }
        assertEquals(
            "三模板必须生成三份不同字节（否则模板参数没有真正生效）",
            3,
            setOf(compact.sha256, practice.sha256, withAnswers.sha256).size,
        )
    }

    @Test
    fun aPracticeSheetWithAnOverLongParagraphStillExports() {
        val prepared = prepare(
            layout = MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET),
            blocks = listOf(ContentBlock.Paragraph("long", "字".repeat(1_200))),
        )

        assertTrue("超长段落的作答空白框必须按栏封顶，而不是让整份导出失败", prepared.verifyIntegrity())
        assertTrue(prepared.pageCount >= 1)
    }

    @Test
    fun aParameterizedPracticeSheetRendersWithoutFailingItsBudgets() {
        val prepared = prepare(
            MistakePdfLayout(
                templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                marginPt = 72,
                fontScale = 3,
                columnCount = 2,
                imageScale = 0.8f,
                includeAnswer = true,
                includeNote = true,
            ),
            userNote = "先看导数的符号。",
        )

        assertTrue(prepared.verifyIntegrity())
        assertTrue(prepared.pageCount >= 1)
    }

    private fun prepare(
        layout: MistakePdfLayout,
        userNote: String? = null,
        blocks: List<ContentBlock> = defaultBlocks(),
    ): PreparedMistakePdf {
        val unique = UUID.randomUUID().toString()
        val state = MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-$unique",
                    problemId = "problem-$unique",
                    problemRevisionId = "revision-$unique",
                    revisionNumber = 1,
                    title = "模板字节用例",
                    subject = "数学",
                ),
                fallbackMarkdown = "fallback must never be used",
                source = MistakeSourceSet.Missing,
                userNote = userNote,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$unique",
                    title = "模板字节用例",
                    blocks = blocks,
                ),
                blockEvidence = blocks.map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-$unique",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            ),
        )
        val input = (
            MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible
            ).input
        return MistakePdfExporter(context).prepare(input)
    }

    private fun defaultBlocks(): List<ContentBlock> = listOf(
        ContentBlock.Paragraph("stem", "已知函数满足 \$f(x)=x^2-2x\$，求单调区间。"),
        ContentBlock.Figure(
            id = "figure",
            title = "函数图像",
            alternativeText = "抛物线",
            schema = FigureSchema.Cartesian(
                xAxis = FigureAxis(-2.0, 2.0, "x"),
                yAxis = FigureAxis(-2.0, 2.0, "y"),
            ),
        ),
        ContentBlock.ChoiceGroup(
            id = "choice",
            promptMarkdown = "函数在哪个区间递增？",
            choices = listOf(
                StructuredChoice("a", "负无穷到 1"),
                StructuredChoice("b", "1 到正无穷"),
            ),
            selectedChoiceId = "b",
        ),
    )
}
