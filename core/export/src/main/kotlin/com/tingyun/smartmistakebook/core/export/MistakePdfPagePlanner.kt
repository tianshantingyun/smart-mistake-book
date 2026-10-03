package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MathBox
import com.tingyun.smartmistakebook.core.model.MathMetrics
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.MistakePdfBlockKind
import com.tingyun.smartmistakebook.core.model.MistakePdfLayoutFeature
import kotlin.math.max
import kotlin.math.min

private const val FORMULA_VERTICAL_PADDING = 3f
private const val FOOTER_RESERVE_BELOW_TEXT = 10f

/**
 * A4 版式的**纯排版核心**（阶段 4A 批 4 抽出；批 3 参数化）。
 *
 * 为什么抽出来：改前分页/折行/预算判定与 `android.graphics.Paint`、`Bitmap` 缠在一起，
 * 只能在仪器化里跑——"行预算多算一行""分页边界少算一段"这类错误要真机才能发现。这个单元
 * **不碰 android.graphics**：文字宽度经 [PdfTextMeasure] 注入（生产用 Paint，JVM 用例用
 * 确定性假测量），干净题面图只接收像素尺寸。JVM 用例见 `MistakePdfPagePlannerTest`。
 *
 * 批 3 起版式不再硬编码：页边距/字号缩放/栏数/图形缩放/模板全部来自
 * [MistakePdfExportInput.layout]（[MistakePdfLayout]）。默认版式（[MistakePdfLayout.DEFAULT]）
 * 下 geometry 与字号因子都是原常数（48pt 边距、48/12+10=22 页脚、fontScale 2→1.0 因子），
 * 输出与参数化前等价；`MistakePdfPlannerGoldenTest` 钉住这一等价的结构与摘要。
 *
 * `plan()` 的结果是 [PdfPlan]：页面×计划项 + 被 fail-closed 跳过的版式功能
 * （当前只有解析区——没有数据源，不留占位脏字）。
 */
internal object MistakePdfPagePlanner {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    /** 双栏之间的栏间距。单栏不使用。 */
    const val COLUMN_GAP = 18f

    /** 解答空白区的规则（练习卷模板）：选择题 1 行高/选项（≤6），段落 max(3, 字数/25) 行。 */
    private const val BLANK_MAX_CHOICE_ROWS = 6
    private const val BLANK_CHARS_PER_ROW = 25
    private const val BLANK_MIN_ROWS = 3

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
    ): PdfPlan {
        val layout = input.layout
        val typography = PdfTypography.forFontScale(layout.fontScale)
        val geometry = PdfPageGeometry.of(layout, typography)
        val draft = buildRawItems(input, measure, cleanImageSize, typography, geometry)
        if (draft.items.size > input.maxRenderedLines) {
            throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED)
        }

        val contentHeight = geometry.contentHeightPt
        val pages = mutableListOf<MutableList<PdfPlannedItem>>()
        var current = mutableListOf<PdfPlannedItem>()
        var column = 0
        var usedHeight = 0f
        draft.items.forEachIndexed { index, item ->
            if (item.height > contentHeight) {
                throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED)
            }
            val nextHeight = if (item.keepWithNext) {
                draft.items.getOrNull(index + 1)?.height ?: 0f
            } else {
                0f
            }
            if (
                current.isNotEmpty() &&
                usedHeight + item.height + nextHeight > contentHeight
            ) {
                if (column + 1 < geometry.columnCount) {
                    // 双栏：第一栏满了先换栏，整页满了才翻页。
                    column += 1
                } else {
                    pages += current
                    current = mutableListOf()
                    column = 0
                }
                usedHeight = 0f
            }
            current += item.withColumn(column)
            usedHeight += item.height
        }
        if (current.isNotEmpty()) pages += current
        if (pages.size > input.maxPages) {
            throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED)
        }
        return PdfPlan(pages = pages, skipped = draft.skipped)
    }

    private fun buildRawItems(
        input: MistakePdfExportInput,
        measure: PdfTextMeasure,
        cleanImageSize: PdfImageSize?,
        typography: PdfTypography,
        geometry: PdfPageGeometry,
    ): RawPlanDraft {
        val layout = input.layout
        val skipped = mutableListOf<PdfLayoutSkip>()
        val items = buildList {
            addWrapped(input.title, PdfLineStyle.TITLE, measure, typography, geometry.columnWidthPt)
            addWrapped(input.subtitle, PdfLineStyle.META, measure, typography, geometry.columnWidthPt)
            input.documentTitle
                ?.takeIf { it.isNotBlank() && it != input.title }
                ?.let {
                    addWrapped(
                        "题面标题：$it",
                        PdfLineStyle.META,
                        measure,
                        typography,
                        geometry.columnWidthPt,
                    )
                }
            add(spacer(typography))
            if (cleanImageSize != null) {
                add(line("干净题面", PdfLineStyle.SECTION_HEADING, typography))
                val width = geometry.visualWidthPt(layout)
                add(
                    PdfPlannedItem.Image(
                        height = cleanImageSize.layoutHeight(width),
                        width = width,
                    ),
                )
                add(spacer(typography))
            }
            orderedBlocks(input.blocks, layout).forEach { block ->
                when (block) {
                    is MistakePdfBlock.Paragraph ->
                        addWrapped(block.text, PdfLineStyle.BODY, measure, typography, geometry.columnWidthPt)
                    is MistakePdfBlock.SectionHeading ->
                        addWrapped(
                            block.text,
                            PdfLineStyle.SECTION_HEADING,
                            measure,
                            typography,
                            geometry.columnWidthPt,
                            keepLastWithNext = true,
                        )
                    is MistakePdfBlock.Formula -> {
                        if (block.latex.isNotBlank()) {
                            addFormula(block.latex, block.display, measure, typography, geometry)
                        }
                        if (block.alternativeText.isNotBlank()) {
                            addWrapped(
                                "读作：${block.alternativeText}",
                                PdfLineStyle.META,
                                measure,
                                typography,
                                geometry.columnWidthPt,
                            )
                        }
                    }
                    is MistakePdfBlock.ChoiceGroup -> {
                        if (block.prompt.isNotBlank()) {
                            addWrapped(block.prompt, PdfLineStyle.BODY, measure, typography, geometry.columnWidthPt)
                        }
                        block.choices.forEachIndexed { index, choice ->
                            val states = buildList {
                                if (choice.selected) add("已选")
                                if (!choice.enabled || !block.enabled) add("不可选")
                            }.joinToString(separator = "、", prefix = " [", postfix = "]")
                                .takeIf { it != " []" }
                                .orEmpty()
                            addWrapped(
                                choiceLabel(index) + choice.text + states,
                                PdfLineStyle.CHOICE,
                                measure,
                                typography,
                                geometry.columnWidthPt,
                            )
                        }
                    }
                    is MistakePdfBlock.Figure -> {
                        val width = geometry.visualWidthPt(layout)
                        add(
                            PdfPlannedItem.Figure(
                                block = block,
                                height = PdfFigureLayout.height(block) * geometry.visualHeightFactor(layout),
                                width = width,
                            ),
                        )
                    }
                }
                if (layout.templateId == MistakePdfLayout.TEMPLATE_PRACTICE_SHEET) {
                    addPracticeBlank(block, typography, geometry)
                }
                if (block !is MistakePdfBlock.SectionHeading) {
                    add(spacer(typography))
                }
            }
            addAnswerSection(input.blocks, layout, measure, typography, geometry)
            // 请求了但当前实现撑不住的功能：按布局自己的判据（unsupportedRequestedFeatures）
            // fail-closed 跳过。导出入口的"会跳过"提示与结果通知读的是同一份判据。
            layout.unsupportedRequestedFeatures().forEach { feature ->
                skipped += PdfLayoutSkip(
                    feature = feature,
                    reason = MistakePdfLayoutSkipReason.UNSUPPORTED_LAYOUT_FEATURE,
                )
            }
            addNoteSection(input, measure, typography, geometry)
        }
        return RawPlanDraft(items = items, skipped = skipped)
    }

    /**
     * 块顺序：空 [MistakePdfLayout.blockOrder] 时保持题面原顺序；非空时按白名单里的
     * 类型顺序稳定排序，**未列出的块类型按原顺序排在最后**（不丢内容，见 layout KDoc）。
     */
    private fun orderedBlocks(
        blocks: List<MistakePdfBlock>,
        layout: MistakePdfLayout,
    ): List<MistakePdfBlock> {
        if (layout.blockOrder.isEmpty()) return blocks
        val priority = layout.blockOrder
            .mapNotNull(MistakePdfBlockKind::fromToken)
            .withIndex()
            .associate { (index, kind) -> kind to index }
        return blocks.withIndex()
            .sortedWith(
                compareBy(
                    { priority[it.value.kind] ?: Int.MAX_VALUE },
                    { it.index },
                ),
            )
            .map { it.value }
    }

    /**
     * 练习卷模板的作答空白区：挂在块内容之后、标准间隔之前，且不与块尾孤行拆页。
     *
     * 空白框是**原子项**（greedy 分页时整框挪页/挪栏，这是"复用 keepWithNext"的口径），
     * 因此行数必须封顶：上限 = 内容高能放下的行数 − 1，保证"块尾行 + 空白框"始终能
     * 同页/同栏。改前超过一栏高的空白框会作为单项直接抛 `PAGE_LIMIT_EXCEEDED`，让整份
     * 导出失败（约 ≥1025 字段落即可触发）——超长段落现在按一栏封顶，题面照常导出。
     */
    private fun MutableList<PdfPlannedItem>.addPracticeBlank(
        block: MistakePdfBlock,
        typography: PdfTypography,
        geometry: PdfPageGeometry,
    ) {
        val requestedRows = when (block) {
            is MistakePdfBlock.ChoiceGroup -> min(block.choices.size, BLANK_MAX_CHOICE_ROWS)
            is MistakePdfBlock.Paragraph -> max(BLANK_MIN_ROWS, block.text.length / BLANK_CHARS_PER_ROW)
            else -> return
        }
        if (requestedRows <= 0) return
        val lineHeight = typography.lineHeight(PdfLineStyle.BODY)
        val maxRows = max(1, (geometry.contentHeightPt / lineHeight).toInt() - 1)
        val rows = min(requestedRows, maxRows)
        val last = lastOrNull()
        if (last is PdfPlannedItem.Line) {
            this[lastIndex] = last.copy(keepWithNext = true)
        }
        add(
            PdfPlannedItem.AnswerBlank(
                height = rows * lineHeight,
                rows = rows,
            ),
        )
    }

    /**
     * 答案独立成区（用户裁定 2026-10-01）：题目区之后统一排"答案"区，
     * 内容 = 每个 ChoiceGroup 的已选项文本（`selectedChoiceId` 在入口已解析为 `selected`）。
     *
     * 没有任何已选答案时不渲染该区（不留空标题占位）。
     */
    private fun MutableList<PdfPlannedItem>.addAnswerSection(
        blocks: List<MistakePdfBlock>,
        layout: MistakePdfLayout,
        measure: PdfTextMeasure,
        typography: PdfTypography,
        geometry: PdfPageGeometry,
    ) {
        val requested = layout.includeAnswer ||
            layout.templateId == MistakePdfLayout.TEMPLATE_WITH_ANSWERS
        if (!requested) return
        val choiceGroups = blocks.filterIsInstance<MistakePdfBlock.ChoiceGroup>()
        val answers = choiceGroups.mapIndexedNotNull { index, group ->
            val selectedIndex = group.choices.indexOfFirst { it.selected }
            if (selectedIndex < 0) return@mapIndexedNotNull null
            "${index + 1}. ${choiceLabel(selectedIndex)}${group.choices[selectedIndex].text}"
        }
        if (answers.isEmpty()) return
        add(line("答案", PdfLineStyle.SECTION_HEADING, typography, keepWithNext = true))
        answers.forEach { answer ->
            addWrapped(answer, PdfLineStyle.BODY, measure, typography, geometry.columnWidthPt)
        }
        add(spacer(typography))
    }

    /** 备注区（`error_book_entry.user_note`）：只在请求且确有内容时渲染。 */
    private fun MutableList<PdfPlannedItem>.addNoteSection(
        input: MistakePdfExportInput,
        measure: PdfTextMeasure,
        typography: PdfTypography,
        geometry: PdfPageGeometry,
    ) {
        if (!input.layout.includeNote) return
        val note = input.userNote?.takeIf { it.isNotBlank() } ?: return
        addWrapped(
            "我的备注",
            PdfLineStyle.SECTION_HEADING,
            measure,
            typography,
            geometry.columnWidthPt,
            keepLastWithNext = true,
        )
        addWrapped(note, PdfLineStyle.BODY, measure, typography, geometry.columnWidthPt)
    }

    private fun choiceLabel(index: Int): String =
        if (index < 26) "${('A'.code + index).toChar()}. " else "${index + 1}. "

    private fun MutableList<PdfPlannedItem>.addWrapped(
        value: String,
        style: PdfLineStyle,
        measure: PdfTextMeasure,
        typography: PdfTypography,
        maxWidth: Float,
        keepLastWithNext: Boolean = false,
    ) {
        val firstAddedIndex = size
        value.split('\n').forEach { sourceLine ->
            if (sourceLine.isEmpty()) {
                add(line("", style, typography))
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
                add(line(sourceLine.substring(start, lastFittingEnd), style, typography))
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
        typography: PdfTypography,
        geometry: PdfPageGeometry,
    ) {
        val fontSizePx = typography.textSize(PdfLineStyle.FORMULA)
        val metrics = MathMetrics.of(fontSizePx)
        val box = CanvasMathBoxRenderer.buildMathBox(latex, metrics)
        if (box == null) {
            val formula = if (display) latex else "\$$latex\$"
            addWrapped(formula, PdfLineStyle.FORMULA, measure, typography, geometry.columnWidthPt)
            return
        }
        val lineHeight = typography.lineHeight(PdfLineStyle.BODY)
        val stripHeight = maxOf(box.height + FORMULA_VERTICAL_PADDING * 2f, lineHeight)
        add(PdfPlannedItem.Formula(latex = latex, box = box, fontSizePx = fontSizePx, height = stripHeight))
    }

    private fun line(
        text: String,
        style: PdfLineStyle,
        typography: PdfTypography,
        keepWithNext: Boolean = false,
    ): PdfPlannedItem.Line = PdfPlannedItem.Line(
        text = text,
        style = style,
        height = typography.lineHeight(style),
        keepWithNext = keepWithNext,
    )

    private fun spacer(typography: PdfTypography): PdfPlannedItem.Line =
        line("", PdfLineStyle.SPACER, typography)
}

private data class RawPlanDraft(
    val items: List<PdfPlannedItem>,
    val skipped: List<PdfLayoutSkip>,
)

/**
 * 版式功能的 fail-closed 记录：请求了、但没有数据源/实现支撑，于是**跳过而不留占位脏字**。
 *
 * 可跳过功能的枚举在 `core:model` 的 [MistakePdfLayoutFeature]（与
 * `MistakePdfLayout.unsupportedRequestedFeatures()` 同一处定义）；这里只记录"跳过了哪一项、
 * 为什么"。
 */
internal enum class MistakePdfLayoutSkipReason {
    UNSUPPORTED_LAYOUT_FEATURE,
}

internal data class PdfLayoutSkip(
    val feature: MistakePdfLayoutFeature,
    val reason: MistakePdfLayoutSkipReason,
)

/**
 * 一次分页的完整产物：页面 + 被跳过的版式功能。
 *
 * **[skipped] 的消费者（4B B3-4）**：导出入口（sheet 的"解析区暂不可用，本次会跳过"）与
 * 结果通知（"已跳过"）读的是与这里同源的 `MistakePdfLayout.unsupportedRequestedFeatures()`；
 * 计划层用例按它断言 fail-closed（不留占位脏字）。
 */
internal data class PdfPlan(
    val pages: List<List<PdfPlannedItem>>,
    val skipped: List<PdfLayoutSkip> = emptyList(),
) {
    val pageCount: Int get() = pages.size

    fun flattened(): List<PdfPlannedItem> = pages.flatten()
}

/**
 * 字号缩放：fontScale 2 = 参数化前的固定字号表（因子 1.0），1 = 0.5×，3 = 1.5×。
 * 计划器与渲染器必须用**同一份**缩放（宽度测量与绘制基线不能各算一套）。
 */
internal class PdfTypography private constructor(fontScale: Int) {
    private val factor: Float = fontScale / 2f

    fun textSize(style: PdfLineStyle): Float = style.textSize * factor

    fun lineHeight(style: PdfLineStyle): Float = style.lineHeight * factor

    companion object {
        fun forFontScale(fontScale: Int): PdfTypography = PdfTypography(fontScale)
    }
}

/**
 * 一页的几何口径（由 layout 派生）：四边边距、栏宽、可用内容高。
 *
 * 默认版式下与参数化前的常数逐位一致：内容宽 595-96=499、页脚高 12+10=22、
 * 内容高 842-96-22=724。
 */
internal data class PdfPageGeometry(
    val marginPt: Float,
    val columnCount: Int,
    val contentWidthPt: Float,
    val columnWidthPt: Float,
    val contentHeightPt: Float,
) {
    /** 图形/干净题面图的实际绘制宽：栏宽 × imageScale（双栏即"缩到半栏"）。 */
    fun visualWidthPt(layout: MistakePdfLayout): Float = columnWidthPt * layout.imageScale

    /** 矢量图形（按设计宽占满内容宽）在窄栏里的等比缩小因子。 */
    fun visualHeightFactor(layout: MistakePdfLayout): Float =
        visualWidthPt(layout) / contentWidthPt

    companion object {
        fun of(layout: MistakePdfLayout, typography: PdfTypography): PdfPageGeometry {
            val margin = layout.marginPt.toFloat()
            val contentWidth = MistakePdfPagePlanner.PAGE_WIDTH - margin * 2f
            val columnWidth = if (layout.columnCount == 2) {
                (contentWidth - MistakePdfPagePlanner.COLUMN_GAP) / 2f
            } else {
                contentWidth
            }
            val footerHeight =
                typography.lineHeight(PdfLineStyle.FOOTER) + FOOTER_RESERVE_BELOW_TEXT
            return PdfPageGeometry(
                marginPt = margin,
                columnCount = layout.columnCount,
                contentWidthPt = contentWidth,
                columnWidthPt = columnWidth,
                contentHeightPt = MistakePdfPagePlanner.PAGE_HEIGHT - margin * 2f - footerHeight,
            )
        }
    }
}

/** 生产实现（Paint）/ JVM 用例的确定性假测量都要满足的折行宽度读口。 */
internal fun interface PdfTextMeasure {
    fun measure(text: String, start: Int, end: Int, style: PdfLineStyle): Float
}

/**
 * 一行文本的版式口径。抽成纯枚举（不带 Typeface）：typeface 属于绘制实现，
 * 计划器只关心字号与行高。这里的数值是 fontScale=2（默认）时的基数，
 * 其余档位经 [PdfTypography] 缩放。
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

    /** 按目标绘制宽等比换算占高。 */
    fun layoutHeight(widthPt: Float): Float =
        heightPx * widthPt / widthPx.coerceAtLeast(1)
}

/** 计划好的一项。Android 侧只负责按类型/栏位绘制，不再参与分页决策。 */
internal sealed interface PdfPlannedItem {
    val height: Float

    /** 双栏版式下的栏位（0/1）；单栏恒为 0。 */
    val column: Int

    val keepWithNext: Boolean
        get() = false

    data class Line(
        val text: String,
        val style: PdfLineStyle,
        override val height: Float,
        override val keepWithNext: Boolean = false,
        override val column: Int = 0,
    ) : PdfPlannedItem

    data class Figure(
        val block: MistakePdfBlock.Figure,
        override val height: Float,
        val width: Float,
        override val column: Int = 0,
    ) : PdfPlannedItem

    data class Formula(
        val latex: String,
        val box: MathBox,
        val fontSizePx: Float,
        override val height: Float,
        override val column: Int = 0,
    ) : PdfPlannedItem

    /** 干净题面图：只带占高与绘制宽；位图留在渲染器里。 */
    data class Image(
        override val height: Float,
        val width: Float,
        override val column: Int = 0,
    ) : PdfPlannedItem

    /** 练习卷作答空白区：细线框，[rows] 个书写行。 */
    data class AnswerBlank(
        override val height: Float,
        val rows: Int,
        override val column: Int = 0,
    ) : PdfPlannedItem
}

private val MistakePdfBlock.kind: MistakePdfBlockKind
    get() = when (this) {
        is MistakePdfBlock.Paragraph -> MistakePdfBlockKind.PARAGRAPH
        is MistakePdfBlock.SectionHeading -> MistakePdfBlockKind.SECTION_HEADING
        is MistakePdfBlock.Formula -> MistakePdfBlockKind.FORMULA
        is MistakePdfBlock.ChoiceGroup -> MistakePdfBlockKind.CHOICE_GROUP
        is MistakePdfBlock.Figure -> MistakePdfBlockKind.FIGURE
    }

private fun PdfPlannedItem.withColumn(column: Int): PdfPlannedItem = when (this) {
    is PdfPlannedItem.Line -> copy(column = column)
    is PdfPlannedItem.Figure -> copy(column = column)
    is PdfPlannedItem.Formula -> copy(column = column)
    is PdfPlannedItem.Image -> copy(column = column)
    is PdfPlannedItem.AnswerBlank -> copy(column = column)
}
