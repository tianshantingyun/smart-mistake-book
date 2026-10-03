package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MathBox
import com.tingyun.smartmistakebook.core.model.MathMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * A4 版式的**纯排版核心**（阶段 4A 批 4：从 `DeterministicMistakePdfRenderer` 抽出）。
 *
 * 为什么抽出来：改前分页/折行/预算判定与 `android.graphics.Paint`、`Bitmap` 缠在一起，
 * 只能在仪器化里跑——"行预算多算一行""分页边界少算一段"这类错误要真机才能发现。这个单元
 * **不碰 android.graphics**：文字宽度经 [PdfTextMeasure] 注入（生产用 Paint，JVM 用例用
 * 确定性假测量），干净题面图只接收像素尺寸。JVM 用例见 `MistakePdfPagePlannerTest`。
 *
 * 行为与抽取前逐位一致（页宽/边距/行高/分页规则都是原常量原算法），`MistakePdfExporter`
 * 的仪器化用例（分页硬上限、批量边界、真实 PDF）继续钉住渲染结果。
 */
internal object MistakePdfPagePlanner {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val LEFT = 48f
    const val RIGHT = 48f
    const val TOP = 48f
    const val BOTTOM = 48f
    const val FOOTER_HEIGHT = 22f
    const val FORMULA_VERTICAL_PADDING = 3f

    /**
     * Builds the page plan for one export input.
     *
     * Throws [MistakePdfExportException] with [MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED]
     * when the rendered-line budget or the page budget would be exceeded — the fail-closed
     * behaviour of the renderer is part of the plan, not of the drawing loop.
     */
    fun plan(
        input: MistakePdfExportInput,
        measure: PdfTextMeasure,
        cleanImageSize: PdfImageSize? = null,
    ): List<List<PdfPlannedItem>> {
        val rawItems = buildRawItems(input, measure, cleanImageSize)
        if (rawItems.size > input.maxRenderedLines) {
            throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED)
        }

        val contentHeight = PAGE_HEIGHT - TOP - BOTTOM - FOOTER_HEIGHT
        val pages = mutableListOf<MutableList<PdfPlannedItem>>()
        var current = mutableListOf<PdfPlannedItem>()
        var usedHeight = 0f
        rawItems.forEachIndexed { index, item ->
            if (item.height > contentHeight) {
                throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED)
            }
            val nextHeight = if (item.keepWithNext) {
                rawItems.getOrNull(index + 1)?.height ?: 0f
            } else {
                0f
            }
            if (
                current.isNotEmpty() &&
                usedHeight + item.height + nextHeight > contentHeight
            ) {
                pages += current
                current = mutableListOf()
                usedHeight = 0f
            }
            current += item
            usedHeight += item.height
        }
        if (current.isNotEmpty()) pages += current
        if (pages.size > input.maxPages) {
            throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED)
        }
        return pages
    }

    private fun buildRawItems(
        input: MistakePdfExportInput,
        measure: PdfTextMeasure,
        cleanImageSize: PdfImageSize?,
    ): List<PdfPlannedItem> = buildList {
        addWrapped(input.title, PdfLineStyle.TITLE, measure)
        addWrapped(input.subtitle, PdfLineStyle.META, measure)
        input.documentTitle
            ?.takeIf { it.isNotBlank() && it != input.title }
            ?.let { addWrapped("题面标题：$it", PdfLineStyle.META, measure) }
        add(PdfPlannedItem.Line("", PdfLineStyle.SPACER))
        if (cleanImageSize != null) {
            add(PdfPlannedItem.Line("干净题面", PdfLineStyle.SECTION_HEADING))
            add(PdfPlannedItem.Image(cleanImageSize.layoutHeightPx()))
            add(PdfPlannedItem.Line("", PdfLineStyle.SPACER))
        }
        input.blocks.forEach { block ->
            when (block) {
                is MistakePdfBlock.Paragraph -> addWrapped(block.text, PdfLineStyle.BODY, measure)
                is MistakePdfBlock.SectionHeading ->
                    addWrapped(
                        block.text,
                        PdfLineStyle.SECTION_HEADING,
                        measure,
                        keepLastWithNext = true,
                    )
                is MistakePdfBlock.Formula -> {
                    if (block.latex.isNotBlank()) {
                        addFormula(block.latex, block.display, measure)
                    }
                    if (block.alternativeText.isNotBlank()) {
                        addWrapped("读作：${block.alternativeText}", PdfLineStyle.META, measure)
                    }
                }
                is MistakePdfBlock.ChoiceGroup -> {
                    if (block.prompt.isNotBlank()) addWrapped(block.prompt, PdfLineStyle.BODY, measure)
                    block.choices.forEachIndexed { index, choice ->
                        val label = if (index < 26) {
                            "${('A'.code + index).toChar()}. "
                        } else {
                            "${index + 1}. "
                        }
                        val states = buildList {
                            if (choice.selected) add("已选")
                            if (!choice.enabled || !block.enabled) add("不可选")
                        }.joinToString(separator = "、", prefix = " [", postfix = "]")
                            .takeIf { it != " []" }
                            .orEmpty()
                        addWrapped(label + choice.text + states, PdfLineStyle.CHOICE, measure)
                    }
                }
                is MistakePdfBlock.Figure -> add(
                    PdfPlannedItem.Figure(
                        block = block,
                        height = PdfFigureLayout.height(block),
                    ),
                )
            }
            if (block !is MistakePdfBlock.SectionHeading) {
                add(PdfPlannedItem.Line("", PdfLineStyle.SPACER))
            }
        }
    }

    private fun MutableList<PdfPlannedItem>.addWrapped(
        value: String,
        style: PdfLineStyle,
        measure: PdfTextMeasure,
        keepLastWithNext: Boolean = false,
    ) {
        val firstAddedIndex = size
        val maxWidth = PAGE_WIDTH - LEFT - RIGHT
        value.split('\n').forEach { sourceLine ->
            if (sourceLine.isEmpty()) {
                add(PdfPlannedItem.Line("", style))
                return@forEach
            }
            var start = 0
            while (start < sourceLine.length) {
                var end = start
                var lastFittingEnd = start
                while (end < sourceLine.length) {
                    val codePoint = sourceLine.codePointAt(end)
                    end += Character.charCount(codePoint)
                    if (measure.measure(sourceLine, start, end, style) <= maxWidth) {
                        lastFittingEnd = end
                    } else {
                        break
                    }
                }
                if (lastFittingEnd == start) {
                    lastFittingEnd = min(sourceLine.length, start + 1)
                }
                add(PdfPlannedItem.Line(sourceLine.substring(start, lastFittingEnd), style))
                start = lastFittingEnd
            }
        }
        if (keepLastWithNext && size > firstAddedIndex) {
            val lastLine = this[lastIndex] as PdfPlannedItem.Line
            this[lastIndex] = lastLine.copy(keepWithNext = true)
        }
    }

    /**
     * Adds a laid-out formula as a [PdfPlannedItem.Formula]. The latex is parsed once at plan
     * time (via MathBox) so its height participates in pagination; when parsing or budget
     * fails the formula falls back to a monospace line, preserving the pre-MathBox exporter
     * behaviour.
     */
    private fun MutableList<PdfPlannedItem>.addFormula(
        latex: String,
        display: Boolean,
        measure: PdfTextMeasure,
    ) {
        val fontSizePx = PdfLineStyle.FORMULA.textSize
        val metrics = MathMetrics.of(fontSizePx)
        val box = CanvasMathBoxRenderer.buildMathBox(latex, metrics)
        if (box == null) {
            val formula = if (display) latex else "\$$latex\$"
            addWrapped(formula, PdfLineStyle.FORMULA, measure)
            return
        }
        val lineHeight = PdfLineStyle.BODY.lineHeight
        val stripHeight = maxOf(box.height + FORMULA_VERTICAL_PADDING * 2f, lineHeight)
        add(PdfPlannedItem.Formula(latex = latex, box = box, fontSizePx = fontSizePx, height = stripHeight))
    }
}

/** 生产实现（Paint）/ JVM 用例的确定性假测量都要满足的折行宽度读口。 */
internal fun interface PdfTextMeasure {
    fun measure(text: String, start: Int, end: Int, style: PdfLineStyle): Float
}

/**
 * 一行文本的版式口径。抽成纯枚举（不带 Typeface）：typeface 属于绘制实现，
 * 计划器只关心字号与行高。
 */
internal enum class PdfLineStyle(
    val textSize: Float,
    val lineHeight: Float,
) {
    TITLE(20f, 28f),
    SECTION_HEADING(15f, 22f),
    META(10f, 16f),
    BODY(12f, 18f),
    FORMULA(13f, 20f),
    CHOICE(12f, 18f),
    SPACER(12f, 8f),
    FOOTER(9f, 12f),
}

/** 干净题面图的像素尺寸：计划器只用它算占高，不持有 Bitmap。 */
internal data class PdfImageSize(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "A clean image must have a positive pixel size" }
    }

    fun layoutHeightPx(): Float =
        heightPx * (MistakePdfPagePlanner.PAGE_WIDTH - MistakePdfPagePlanner.LEFT -
            MistakePdfPagePlanner.RIGHT) / widthPx.coerceAtLeast(1)
}

/** 计划好的一项。Android 侧只负责按类型绘制，不再参与分页决策。 */
internal sealed interface PdfPlannedItem {
    val height: Float

    val keepWithNext: Boolean
        get() = false

    data class Line(
        val text: String,
        val style: PdfLineStyle,
        override val keepWithNext: Boolean = false,
    ) : PdfPlannedItem {
        override val height: Float = style.lineHeight
    }

    data class Figure(
        val block: MistakePdfBlock.Figure,
        override val height: Float,
    ) : PdfPlannedItem

    data class Formula(
        val latex: String,
        val box: MathBox,
        val fontSizePx: Float,
        override val height: Float,
    ) : PdfPlannedItem

    /** 干净题面图：只带占高；位图留在渲染器里。 */
    data class Image(
        override val height: Float,
    ) : PdfPlannedItem
}
