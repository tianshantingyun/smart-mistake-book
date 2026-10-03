package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.FigureSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染核心（[MistakePdfPagePlanner]）的 JVM 用例：折行、分页、预算与算式降级都在这里钉住，
 * 不必等真机。改前这些规则与 `Paint`/`PdfDocument` 缠在一起，只能在仪器化里发现
 * "行预算多算一行 / 分页边界少算一段"。
 *
 * 测量用确定性假实现：宽度 ≈ 字符数 × 字号（`maxWidth = 595 - 48 - 48 = 499`），
 * 于是每行的字符容量可精确算出（BODY=41、TITLE=24、SECTION_HEADING=33）。
 */
class MistakePdfPagePlannerTest {

    private val measure = PdfTextMeasure { text, start, end, style ->
        (end - start) * style.textSize
    }

    @Test
    fun `a short document plans one page in reading order`() {
        val input = input(
            blocks = listOf(
                MistakePdfBlock.Paragraph(id = "p1", text = "求函数的单调区间。"),
            ),
        )

        val pages = MistakePdfPagePlanner.plan(input, measure).pages

        assertEquals(1, pages.size)
        val items = pages.single()
        assertEquals(PdfLineStyle.TITLE, (items[0] as PdfPlannedItem.Line).style)
        assertEquals("求函数的单调区间。", (items[3] as PdfPlannedItem.Line).text)
    }

    @Test
    fun `content paginates within the content height without losing an item`() {
        val blocks = (1..40).map { index ->
            MistakePdfBlock.Paragraph(id = "p$index", text = paragraphText(index))
        }
        val input = input(blocks = blocks)

        val pages = MistakePdfPagePlanner.plan(input, measure).pages

        assertTrue("40 段单行内容必须分成多页（夹具保证溢出）", pages.size > 1)
        pages.forEachIndexed { index, page ->
            val used = page.sumOf { it.height.toDouble() }.toFloat()
            assertTrue(
                "第 ${index + 1} 页超过内容高度：$used",
                used <= contentHeight(),
            )
        }
        // 分页只改变页边界，不改变顺序、不丢不重：3 + 40×2 = 83 项（标题、副标题、题头间隔 + 每段一行一间隔）。
        val flattened = pages.flatten().map { item ->
            when (item) {
                is PdfPlannedItem.Line -> "line:${item.text}:${item.style}"
                else -> "other:${item::class.simpleName}"
            }
        }
        assertEquals(3 + blocks.size * 2, flattened.size)
        assertTrue(flattened.first().startsWith("line:测试题目:${PdfLineStyle.TITLE}"))
        assertEquals(
            "每一段正文按原序都在",
            (1..40).map { "第${it}段" },
            flattened.mapNotNull { entry ->
                entry.takeIf { it.startsWith("line:第") && it.endsWith("段:${PdfLineStyle.BODY}") }
                    ?.removePrefix("line:")
                    ?.substringBefore(":")
            },
        )
    }

    @Test
    fun `rendered line budget fails closed`() {
        val input = input(
            blocks = listOf(
                MistakePdfBlock.Paragraph(id = "p1", text = paragraphText(1)),
                MistakePdfBlock.Paragraph(id = "p2", text = paragraphText(2)),
            ),
            maxRenderedLines = 5,
        )

        val failure = runCatching { MistakePdfPagePlanner.plan(input, measure) }.exceptionOrNull()

        assertEquals(
            MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED,
            (failure as? MistakePdfExportException)?.failure,
        )
    }

    @Test
    fun `page budget fails closed`() {
        val input = input(
            blocks = (1..28).map { index ->
                MistakePdfBlock.Paragraph(id = "p$index", text = paragraphText(index))
            },
            maxPages = 1,
        )

        val failure = runCatching { MistakePdfPagePlanner.plan(input, measure) }.exceptionOrNull()

        assertEquals(
            MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED,
            (failure as? MistakePdfExportException)?.failure,
        )
    }

    @Test
    fun `a section heading is kept with the first line after it`() {
        // 25 个单行段落后累计高度 = 28(标题) + 16(副标题) + 8(题头间隔) + 25*(18+8) = 702。
        // 还剩 724 - 702 = 22；标题行 22 + 下一段首行 18 = 40 > 22 → 标题必须整条挪到下一页
        // （若 keepWithNext 失效：702+22=724 恰好留下一个孤行标题，这个断言就会红）。
        val blocks = buildList {
            (1..25).forEach { index ->
                add(MistakePdfBlock.Paragraph(id = "pad$index", text = paragraphText(index)))
            }
            add(MistakePdfBlock.SectionHeading(id = "heading", text = "解析"))
            add(MistakePdfBlock.Paragraph(id = "after", text = paragraphText(99)))
        }

        val pages = MistakePdfPagePlanner.plan(input(blocks = blocks), measure).pages

        assertEquals(2, pages.size)
        assertFalse(
            "孤行标题必须被推到下一页",
            pages[0].any { it is PdfPlannedItem.Line && it.text == "解析" },
        )
        val secondPage = pages[1].mapNotNull { (it as? PdfPlannedItem.Line)?.text }
        assertEquals("解析", secondPage.first())
        assertTrue("标题必须和下一段首行同页", secondPage[1].startsWith("第99段"))
    }

    @Test
    fun `a valid formula becomes a laid out strip and an over budget one falls back to text`() {
        val overBudgetLatex = "x".repeat(2_100)
        val input = input(
            blocks = listOf(
                MistakePdfBlock.Formula(
                    id = "f1",
                    latex = "\\frac{1}{2}",
                    alternativeText = "二分之一",
                    display = true,
                ),
                MistakePdfBlock.Formula(
                    id = "f2",
                    latex = overBudgetLatex,
                    alternativeText = "超预算算式",
                    display = true,
                ),
            ),
        )

        val items = MistakePdfPagePlanner.plan(input, measure).flattened()

        val formula = items.filterIsInstance<PdfPlannedItem.Formula>().single()
        assertEquals("\\frac{1}{2}", formula.latex)
        assertTrue("算式要按 MathBox 的实高参与分页", formula.height > PdfLineStyle.BODY.lineHeight)
        assertEquals(
            "超预算算式降级为等宽文本行（原样，不静默丢）",
            overBudgetLatex,
            items.filterIsInstance<PdfPlannedItem.Line>()
                .filter { it.style == PdfLineStyle.FORMULA }
                .joinToString("") { it.text },
        )
        assertTrue(
            "读作备选文案仍在",
            items.any { it is PdfPlannedItem.Line && it.text == "读作：超预算算式" },
        )
    }

    @Test
    fun `figure height participates in pagination and a clean image only when sized`() {
        val figureBlock = MistakePdfBlock.Figure(
            id = "fig",
            title = "数表",
            alternativeText = "两行数表",
            schema = FigureSchema.SymbolTable(
                headers = listOf("x", "y"),
                rows = listOf(listOf("0", "0"), listOf("1", "1")),
            ),
        )

        val withImage = MistakePdfPagePlanner.plan(
            input(blocks = listOf(figureBlock), cleanImageLocalUri = "file:///clean.jpg"),
            measure,
            cleanImageSize = PdfImageSize(widthPx = 998, heightPx = 500),
        ).pages.single()
        val withoutImage = MistakePdfPagePlanner.plan(
            input(blocks = listOf(figureBlock)),
            measure,
        ).pages.single()

        val figure = withImage.filterIsInstance<PdfPlannedItem.Figure>().single()
        assertEquals(
            "表格高度 = 48 页眉页脚 + 18 × (行数 + 表头行)",
            PdfFigureLayout.height(figureBlock),
            figure.height,
            0.0f,
        )
        val image = withImage.filterIsInstance<PdfPlannedItem.Image>().single()
        assertEquals(500f * 499f / 998f, image.height, 0.001f)
        assertTrue(withoutImage.none { it is PdfPlannedItem.Image })
    }

    // ---- fixtures ----

    private fun contentHeight() = PdfPageGeometry.of(
        MistakePdfLayout.DEFAULT,
        PdfTypography.forFontScale(MistakePdfLayout.DEFAULT_FONT_SCALE),
    ).contentHeightPt

    /** 41 字内 = 一行（BODY 行容量），前缀 "第N段" 让段落可从文本反认。 */
    private fun paragraphText(index: Int): String = "第${index}段"

    private fun input(
        blocks: List<MistakePdfBlock>,
        maxPages: Int = MistakePdfExportLimits.MAX_BATCH_PAGES,
        maxRenderedLines: Int = MistakePdfExportLimits.MAX_BATCH_RENDERED_LINES,
        cleanImageLocalUri: String? = null,
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry-test",
        problemId = "problem-test",
        problemRevisionId = "revision-test",
        revisionNumber = 1,
        title = "测试题目",
        subject = "MATH",
        subtitle = "MATH · 第 1 版",
        documentTitle = null,
        blocks = blocks,
        questionDocumentSha256 = "0".repeat(64),
        inputSha256 = "1".repeat(64),
        maxPages = maxPages,
        maxRenderedLines = maxRenderedLines,
        maxPdfBytes = MistakePdfExportLimits.MAX_BATCH_PDF_BYTES,
        cleanImageLocalUri = cleanImageLocalUri,
    )
}
