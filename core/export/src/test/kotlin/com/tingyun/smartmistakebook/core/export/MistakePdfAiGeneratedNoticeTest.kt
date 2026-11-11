package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出 PDF 的**显式** AI 标识（《人工智能生成合成内容标识办法》第四条）：
 * 模型产出的文字在首屏有一行可见说明，与文件级隐式 `/AIGC`（第五条）并列，不是替代关系。
 *
 * 负向同样重要：纯用户内容不得出现这行说明（第十条禁止伪造标识）。
 */
class MistakePdfAiGeneratedNoticeTest {

    private val measure = PdfTextMeasure { text, start, end, style -> (end - start) * style.textSize }

    @Test
    fun `model text puts a visible AI notice on the first screen`() {
        val lines = planLines(hasModelParsedText = true)

        assertTrue(
            "含模型文本的导出必须在页面上可见地说明（不是只在 /AIGC 里）",
            lines.any { it.text == "本文件含 AI 生成内容" },
        )
        // 首屏：说明出现在正文段落之前，读者不翻页也能看到。
        val noticeIndex = lines.indexOfFirst { it.text == "本文件含 AI 生成内容" }
        val bodyIndex = lines.indexOfFirst { it.text == "题干文字" }
        assertTrue(noticeIndex in 0 until bodyIndex)
    }

    @Test
    fun `pure user content keeps the page free of the AI notice`() {
        val lines = planLines(hasModelParsedText = false)

        assertFalse(
            "纯用户内容出现 AI 说明就是伪标（《办法》第十条）",
            lines.any { it.text == "本文件含 AI 生成内容" },
        )
    }

    private fun planLines(hasModelParsedText: Boolean): List<PdfPlannedItem.Line> =
        MistakePdfPagePlanner.plan(
            input = MistakePdfExportInput(
                errorBookEntryId = "entry-notice",
                problemId = "problem-notice",
                problemRevisionId = "revision-notice",
                revisionNumber = 1,
                title = "函数单调区间",
                subject = "MATH",
                subtitle = "MATH · 第 1 版",
                documentTitle = null,
                blocks = listOf(MistakePdfBlock.Paragraph(id = "p1", text = "题干文字")),
                questionDocumentSha256 = "0".repeat(64),
                hasModelParsedText = hasModelParsedText,
                inputSha256 = "1".repeat(64),
                layout = MistakePdfLayout.DEFAULT,
            ),
            measure = measure,
        ).pages.flatten().filterIsInstance<PdfPlannedItem.Line>()
}
