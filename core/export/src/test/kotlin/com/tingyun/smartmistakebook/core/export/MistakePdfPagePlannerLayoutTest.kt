package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.MistakePdfLayoutFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版式参数化与练习卷模板（B2）的 JVM 用例：模板差异、作答空白区行数/高度、答案/解析
 * 分区、备注、双栏缩图、块顺序与字号/边距。
 *
 * 这些规则改前硬编码在渲染器里、只能在仪器化里看结果；现在分页结构在 JVM 上直接断言，
 * PDF 字节层面的"三模板互异"由 `MistakePdfLayoutTemplatesInstrumentedTest` 钉住。
 */
class MistakePdfPagePlannerLayoutTest {

    private val defaultMeasure = PdfTextMeasure { text, start, end, style ->
        (end - start) * style.textSize
    }

    @Test
    fun `the three templates plan distinct structures for the same content`() {
        val blocks = listOf(
            MistakePdfBlock.Paragraph(id = "stem", text = "求函数的单调区间。"),
            choiceGroup(id = "choices", prompt = "下列哪个正确？"),
            symbolTable(id = "figure"),
        )

        val compact = plan(blocks, MistakePdfLayout.DEFAULT)
        val practice = plan(
            blocks,
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET),
        )
        val withAnswers = plan(
            blocks,
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_WITH_ANSWERS),
        )

        val compactSignature = signature(compact)
        val practiceSignature = signature(practice)
        val withAnswersSignature = signature(withAnswers)
        assertFalse(compactSignature == practiceSignature)
        assertFalse(compactSignature == withAnswersSignature)
        assertFalse(practiceSignature == withAnswersSignature)
        assertTrue(practice.flattened().any { it is PdfPlannedItem.AnswerBlank })
        assertTrue(
            withAnswers.flattened().any { it is PdfPlannedItem.Line && it.text == "答案" },
        )
    }

    @Test
    fun `practice sheet blank rows follow the ruling for paragraphs and choice groups`() {
        val longParagraph = MistakePdfBlock.Paragraph(id = "p-long", text = "字".repeat(100))
        val shortParagraph = MistakePdfBlock.Paragraph(id = "p-short", text = "字".repeat(10))
        val wideChoices = MistakePdfBlock.ChoiceGroup(
            id = "c-wide",
            prompt = "选一个",
            choices = (1..8).map { index -> MistakePdfChoice("c$index", "选项$index", false, true) },
            enabled = true,
        )
        val narrowChoices = choiceGroup(id = "c-narrow")
        val blocks = listOf(longParagraph, shortParagraph, wideChoices, narrowChoices)

        val plan = plan(
            blocks,
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET),
        )
        val blanks = plan.flattened().filterIsInstance<PdfPlannedItem.AnswerBlank>()

        // 段落：max(3, 字数/25) 行；100 字 → 4 行，10 字 → 3 行。
        // 选择题：min(选项数, 6) 行；8 个选项 → 6 行，2 个选项 → 2 行。
        assertEquals(listOf(4, 3, 6, 2), blanks.map { it.rows })
        blanks.forEach { blank ->
            assertEquals(
                "空白区高 = 行数 × BODY 行高（fontScale=2 时 18）",
                blank.rows * PdfLineStyle.BODY.lineHeight,
                blank.height,
                0.001f,
            )
        }
    }

    @Test
    fun `a practice blank stays on the same page and column as the last block line`() {
        val blocks = (1..25).map { index ->
            MistakePdfBlock.Paragraph(id = "pad$index", text = "第${index}段")
        } + MistakePdfBlock.Paragraph(id = "answer-space", text = "留白")

        listOf(
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET),
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET, columnCount = 2),
        ).forEach { layout ->
            val plan = plan(blocks, layout)
            val page = plan.pages.firstOrNull { page ->
                page.any { it is PdfPlannedItem.Line && it.text == "留白" }
            }
            assertTrue("段落“留白”必须被计划进某一页", page != null)
            val lineIndex = page!!.indexOfFirst { it is PdfPlannedItem.Line && it.text == "留白" }
            val line = page[lineIndex] as PdfPlannedItem.Line
            assertTrue("块尾行不能是页尾（那说明 keepWithNext 失效）", lineIndex < page.size - 1)
            val blank = page[lineIndex + 1]
            assertTrue("空白框必须紧跟块尾行", blank is PdfPlannedItem.AnswerBlank)
            assertTrue("空白框不得与它跟随的块拆到两页（复用 keepWithNext）", line.keepWithNext)
            assertEquals("空白框必须与块尾行同页同栏（${layout.columnCount} 栏）", line.column, blank.column)
        }
    }

    @Test
    fun `an over long paragraph is capped instead of failing the whole export`() {
        val longText = "字".repeat(1_200)
        val layouts = listOf(
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET),
            MistakePdfLayout(
                templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                marginPt = 72,
                fontScale = 3,
            ),
        )

        layouts.forEach { layout ->
            val plan = plan(listOf(MistakePdfBlock.Paragraph("long", longText)), layout)
            val typography = PdfTypography.forFontScale(layout.fontScale)
            val geometry = PdfPageGeometry.of(layout, typography)
            val lineHeight = typography.lineHeight(PdfLineStyle.BODY)
            val blank = plan.flattened().filterIsInstance<PdfPlannedItem.AnswerBlank>().single()

            assertTrue("空白框永不超过内容高", blank.height <= geometry.contentHeightPt)
            assertTrue(
                "块尾行必须能和空白框同栏（否则 keepWithNext 会拆页）",
                blank.height + lineHeight <= geometry.contentHeightPt,
            )
            assertTrue(
                "1200 字（${longText.length / 25} 行请求）必须被按栏封顶，实际 ${blank.rows} 行",
                blank.rows < longText.length / 25,
            )
            plan.flattened().forEach { item ->
                assertTrue("任何单项都不得超过内容高", item.height <= geometry.contentHeightPt)
            }
        }
    }

    @Test
    fun `answers form a separate section after every question block`() {
        val blocks = listOf(
            MistakePdfBlock.Paragraph(id = "stem", text = "求函数的单调区间。"),
            choiceGroup(id = "choices", prompt = "下列哪个正确？"),
            MistakePdfBlock.Paragraph(id = "tail", text = "写出你的理由。"),
        )

        val items = plan(
            blocks,
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET, includeAnswer = true),
        ).flattened()

        val headingIndex = items.indexOfFirst { it is PdfPlannedItem.Line && it.text == "答案" }
        assertTrue("答案区必须存在", headingIndex > 0)
        val tailIndex = items.indexOfFirst { it is PdfPlannedItem.Line && it.text == "写出你的理由。" }
        assertTrue("题目区在答案区之前", tailIndex in 0 until headingIndex)
        assertTrue(
            "答案解析独立成区：答案区之后不再出现题目块",
            items.drop(headingIndex).none { item ->
                item is PdfPlannedItem.Line && item.text == "求函数的单调区间。"
            },
        )
        assertTrue(
            "答案内容 = selectedChoiceId 对应的选项文本",
            items.any { it is PdfPlannedItem.Line && it.text == "1. B. 先减后增" },
        )
    }

    @Test
    fun `the with_answers template turns the answer section on without the flag`() {
        val blocks = listOf(choiceGroup(id = "choices", prompt = "下列哪个正确？"))

        val compact = plan(blocks, MistakePdfLayout.DEFAULT).flattened()
        val withAnswers = plan(
            blocks,
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_WITH_ANSWERS),
        ).flattened()

        assertFalse(compact.any { it is PdfPlannedItem.Line && it.text == "答案" })
        assertTrue(withAnswers.any { it is PdfPlannedItem.Line && it.text == "答案" })
        assertTrue(withAnswers.any { it is PdfPlannedItem.Line && it.text == "1. B. 先减后增" })
    }

    @Test
    fun `no selected choice means no empty answer section`() {
        val block = MistakePdfBlock.ChoiceGroup(
            id = "choices",
            prompt = "下列哪个正确？",
            choices = listOf(
                MistakePdfChoice("a", "单调递增", false, true),
                MistakePdfChoice("b", "先减后增", false, true),
            ),
            enabled = true,
        )

        val items = plan(
            listOf(block),
            MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_WITH_ANSWERS),
        ).flattened()

        assertFalse(
            "没有任何已选答案时不渲染空答案标题",
            items.any { it is PdfPlannedItem.Line && it.text == "答案" },
        )
    }

    @Test
    fun `an unavailable solution section is skipped fail closed instead of faked`() {
        val blocks = listOf(MistakePdfBlock.Paragraph(id = "stem", text = "求函数的单调区间。"))
        val skippedPlan = plan(blocks, MistakePdfLayout(includeSolution = true))
        val plainPlan = plan(blocks, MistakePdfLayout())

        assertEquals(
            listOf(
                PdfLayoutSkip(
                    feature = MistakePdfLayoutFeature.SOLUTION_SECTION,
                    reason = MistakePdfLayoutSkipReason.UNSUPPORTED_LAYOUT_FEATURE,
                ),
            ),
            skippedPlan.skipped,
        )
        assertTrue("跳过不是拒绝：题目区照常生成", skippedPlan.pages.isNotEmpty())
        assertEquals(
            "没有解析数据源时不留占位脏字，结构与不请求时逐项一致",
            signature(plainPlan),
            signature(skippedPlan),
        )
        assertFalse(
            skippedPlan.flattened().any { item ->
                item is PdfPlannedItem.Line && item.text.contains("解析")
            },
        )
    }

    @Test
    fun `the learner note renders only when requested and present`() {
        val blocks = listOf(MistakePdfBlock.Paragraph(id = "stem", text = "求函数的单调区间。"))

        val withNote = plan(
            blocks,
            MistakePdfLayout(includeNote = true),
            userNote = "先看导数的符号。",
        ).flattened()
        val withoutNote = plan(blocks, MistakePdfLayout.DEFAULT, userNote = "先看导数的符号。")
            .flattened()
        val requestedButEmpty = plan(blocks, MistakePdfLayout(includeNote = true)).flattened()

        assertTrue(withNote.any { it is PdfPlannedItem.Line && it.text == "我的备注" })
        assertTrue(withNote.any { it is PdfPlannedItem.Line && it.text == "先看导数的符号。" })
        assertFalse(withoutNote.any { it is PdfPlannedItem.Line && it.text == "我的备注" })
        assertFalse(requestedButEmpty.any { it is PdfPlannedItem.Line && it.text == "我的备注" })
    }

    @Test
    fun `two columns shrink an over wide image to the half column instead of failing`() {
        val imageSize = PdfImageSize(widthPx = 998, heightPx = 500)
        val figure = symbolTable(id = "figure")
        val single = plan(
            listOf(figure),
            MistakePdfLayout.DEFAULT,
            cleanImageSize = imageSize,
            cleanImageLocalUri = "file:///clean.jpg",
        )
        val twoColumnLayout = MistakePdfLayout(columnCount = 2, imageScale = 1.0f)
        assertTrue("该组合必须合法（选择缩到半栏，不是拒绝）", twoColumnLayout.validate().isEmpty())
        val twoColumn = plan(
            listOf(figure),
            twoColumnLayout,
            cleanImageSize = imageSize,
            cleanImageLocalUri = "file:///clean.jpg",
        )

        val singleImage = single.flattened().filterIsInstance<PdfPlannedItem.Image>().single()
        val twoColumnImage = twoColumn.flattened().filterIsInstance<PdfPlannedItem.Image>().single()
        val expectedColumnWidth = (499f - MistakePdfPagePlanner.COLUMN_GAP) / 2f
        assertEquals(499f, singleImage.width, 0.001f)
        assertEquals(250f, singleImage.height, 0.001f)
        assertEquals(expectedColumnWidth, twoColumnImage.width, 0.001f)
        assertEquals(500f * expectedColumnWidth / 998f, twoColumnImage.height, 0.001f)
        assertTrue("双栏图必须小于单栏图", twoColumnImage.height < singleImage.height)

        val singleFigure = single.flattened().filterIsInstance<PdfPlannedItem.Figure>().single()
        val twoColumnFigure = twoColumn.flattened().filterIsInstance<PdfPlannedItem.Figure>().single()
        assertEquals(PdfFigureLayout.height(figure), singleFigure.height, 0.001f)
        assertEquals(
            PdfFigureLayout.height(figure) * expectedColumnWidth / 499f,
            twoColumnFigure.height,
            0.001f,
        )
    }

    @Test
    fun `two columns fill the first column before the second`() {
        val blocks = (1..80).map { index ->
            MistakePdfBlock.Paragraph(id = "p$index", text = "第${index}段")
        }

        val pages = plan(blocks, MistakePdfLayout(columnCount = 2)).pages

        assertTrue(pages.size > 1)
        val firstPage = pages.first()
        assertEquals(
            "第一页先填满第 0 栏再进第 1 栏",
            listOf(0, 1),
            firstPage.map { it.column }.distinct(),
        )
        assertTrue(firstPage.all { it.column in 0..1 })
        assertEquals(0, firstPage.first().column)
    }

    @Test
    fun `font scale and margins inject into the planned geometry`() {
        val layout = MistakePdfLayout(marginPt = 72, fontScale = 3)
        val measure = measureFor(layout)
        val blocks = (1..60).map { index ->
            MistakePdfBlock.Paragraph(id = "p$index", text = "第${index}段")
        }

        val plan = MistakePdfPagePlanner.plan(
            input(blocks = blocks, layout = layout),
            measure,
        )
        val geometry = PdfPageGeometry.of(layout, PdfTypography.forFontScale(layout.fontScale))

        assertEquals(27f, PdfTypography.forFontScale(3).lineHeight(PdfLineStyle.BODY), 0.001f)
        // 页脚高 = FOOTER 行高(12×1.5=18) + 10 余量；内容高 = 842 − 2×72 − 28。
        assertEquals(842f - 144f - 28f, geometry.contentHeightPt, 0.001f)
        plan.pages.forEach { page ->
            val used = page.sumOf { it.height.toDouble() }.toFloat()
            assertTrue("每栏不得超过注入后的内容高：$used", used <= geometry.contentHeightPt + 0.001f)
        }
        assertTrue(
            "字号放大后行高必须随之缩放",
            plan.flattened().filterIsInstance<PdfPlannedItem.Line>()
                .any { it.style == PdfLineStyle.BODY && it.height == 27f },
        )
    }

    @Test
    fun `block order reorders kinds without dropping content`() {
        val blocks = listOf(
            MistakePdfBlock.Paragraph(id = "p1", text = "第一段"),
            symbolTable(id = "figure"),
            MistakePdfBlock.Paragraph(id = "p2", text = "第二段"),
        )

        val items = plan(
            blocks,
            MistakePdfLayout(blockOrder = listOf("figure", "paragraph")),
        ).flattened()

        val figureIndex = items.indexOfFirst { it is PdfPlannedItem.Figure }
        val firstParagraphIndex = items.indexOfFirst { it is PdfPlannedItem.Line && it.text == "第一段" }
        val secondParagraphIndex = items.indexOfFirst { it is PdfPlannedItem.Line && it.text == "第二段" }
        assertTrue(figureIndex in 0 until firstParagraphIndex)
        assertTrue(firstParagraphIndex < secondParagraphIndex)
    }

    // ---- fixtures ----

    private fun choiceGroup(id: String, prompt: String = "下列哪个正确？") = MistakePdfBlock.ChoiceGroup(
        id = id,
        prompt = prompt,
        choices = listOf(
            MistakePdfChoice("a", "单调递增", false, true),
            MistakePdfChoice("b", "先减后增", true, true),
        ),
        enabled = true,
    )

    private fun symbolTable(id: String) = MistakePdfBlock.Figure(
        id = id,
        title = "数表",
        alternativeText = "两行数表",
        schema = FigureSchema.SymbolTable(
            headers = listOf("x", "y"),
            rows = listOf(listOf("0", "0"), listOf("1", "1")),
        ),
    )

    private fun measureFor(layout: MistakePdfLayout): PdfTextMeasure {
        val typography = PdfTypography.forFontScale(layout.fontScale)
        return PdfTextMeasure { text, start, end, style ->
            (end - start) * typography.textSize(style)
        }
    }

    private fun plan(
        blocks: List<MistakePdfBlock>,
        layout: MistakePdfLayout,
        userNote: String? = null,
        cleanImageSize: PdfImageSize? = null,
        cleanImageLocalUri: String? = null,
    ): PdfPlan = MistakePdfPagePlanner.plan(
        input(
            blocks = blocks,
            layout = layout,
            userNote = userNote,
            cleanImageLocalUri = cleanImageLocalUri,
        ),
        measureFor(layout),
        cleanImageSize,
    )

    private fun signature(plan: PdfPlan): String = plan.pages.joinToString(separator = "\n") { page ->
        page.joinToString(separator = "|") { item ->
            when (item) {
                is PdfPlannedItem.Line -> "line:${item.style}:${item.height}:${item.text}"
                is PdfPlannedItem.Formula -> "formula:${item.latex}:${item.height}"
                is PdfPlannedItem.Figure -> "figure:${item.block.id}:${item.height}:${item.width}"
                is PdfPlannedItem.Image -> "image:${item.height}:${item.width}"
                is PdfPlannedItem.AnswerBlank -> "blank:${item.rows}:${item.height}"
            }
        }
    }

    private fun input(
        blocks: List<MistakePdfBlock>,
        layout: MistakePdfLayout,
        userNote: String? = null,
        cleanImageLocalUri: String? = null,
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry-layout-test",
        problemId = "problem-layout-test",
        problemRevisionId = "revision-layout-test",
        revisionNumber = 1,
        title = "测试题目",
        subject = "MATH",
        subtitle = "MATH · 第 1 版",
        documentTitle = null,
        blocks = blocks,
        questionDocumentSha256 = "0".repeat(64),
        inputSha256 = "1".repeat(64),
        maxPages = MistakePdfExportLimits.MAX_BATCH_PAGES,
        maxRenderedLines = MistakePdfExportLimits.MAX_BATCH_RENDERED_LINES,
        maxPdfBytes = MistakePdfExportLimits.MAX_BATCH_PDF_BYTES,
        cleanImageLocalUri = cleanImageLocalUri,
        layout = layout,
        userNote = userNote,
    )
}
