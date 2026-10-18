package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MistakePdfBlockKind
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import java.io.File
import kotlin.math.roundToInt

/**
 * 把一份 [MistakePdfExportInput] 写成一个最小 OOXML 的 Word 文档（6B-1）。
 *
 * **单一文档模型**：渲染器只消费既有的 [MistakePdfExportInput]/[MistakePdfBlock] 块序列
 * （与 PDF 侧同源，见 `MistakePdfEligibility`），不新造题面模型、不改 `MistakePdfPagePlanner`。
 *
 * **Word 侧的如实降级**（与 PDF 的差异都记录在这里，供批记录/后续批量核对）：
 * - 公式：PDF 画 MathBox；Word 写 LaTeX 文本（display=false 时按规划器的口径包 `$…$`），
 *   非空 alternativeText 追加"读作：…"一行——内容不丢，字形不做矢量排版；
 * - 图形（[MistakePdfBlock.Figure]）：PDF 画矢量图形；Word 写标题 + 无障碍文字说明，
 *   不嵌图片、不留占位脏字；
 * - 作答空白区（练习卷的 AnswerBlank）：Word 侧不含（细线框是画布产物，Word 没有对应项）；
 * - 页脚：PDF 每页画"第 n / m 页"；Word 包不写页眉页脚部件（需要时在 Word 里插入页码），
 *   正文内容不受影响；
 * - 答案区/备注区：与 PDF 计划器同一判据、同一文案结构（文本内容一致）；
 * - 图片：`cleanImageLocalUri` 与批量 [MistakePdfBlock.Image] 都尝试按 PNG/JPEG 嵌入；
 *   读不出时省略该图、保留其余内容（PDF 计划器在拿不到尺寸时同样不排入这张图；
 *   PDF 渲染器在这种输入上整份失败，差异见批记录）；
 * - 页数/分页：Word 自己排版，渲染器不设页数上限，只受字节预算约束（由 exporter 核）。
 *
 * **本批边界（6B-1）**：只做"把既有题面模型交付成 Word 文件"。应用内编辑 Word 与自然语言
 * 改卷属于 6B-2/3，本渲染器不提供也不假装提供；学生拿到的是一份可打印/可再编辑的普通
 * `.docx`，后续编辑在 Word/WPS 里完成。
 *
 * **确定性**：段落顺序、部件顺序、时间戳都固定（见 [MinimalDocxWriter]），同一输入两次
 * 渲染字节一致；JVM 金样用例 `DocxMistakeRendererTest` 用 zip 读回 + XML 解析钉住部件与文本。
 */
internal object DocxMistakeRenderer {
    private const val PAGE_WIDTH_TWIPS = 11_906L
    private const val PAGE_HEIGHT_TWIPS = 16_838L

    /** 1 twip = 1/1440 inch，1 EMU = 1/914400 inch。 */
    private const val EMU_PER_TWIP = 635L
    private const val TWIPS_PER_POINT = 20L

    /** 双栏间距 18pt（与 `MistakePdfPagePlanner.COLUMN_GAP` 同口径）。 */
    private const val COLUMN_GAP_TWIPS = 18L * TWIPS_PER_POINT

    /** 字号基准与 `PdfLineStyle` 一致（pt）：标题 20 / 小标题 15 / 正文 12 / 元信息 10 / 公式 13。 */
    private const val TITLE_POINT = 20f
    private const val HEADING_POINT = 15f
    private const val BODY_POINT = 12f
    private const val META_POINT = 10f
    private const val FORMULA_POINT = 13f

    private const val MEDIA_DIRECTORY = "word/media"

    private const val DOCUMENT_NAMESPACES =
        "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""

    fun render(input: MistakePdfExportInput, output: File) {
        val blocks = orderedBlocks(input.blocks, input.layout)
        val images = embeddedImages(input, blocks)
        val documentXml = documentXml(input, blocks, images)
        val parts = buildList {
            add(MinimalDocxWriter.contentTypesPart(images.map { it.part.fileExtension }.toSet()))
            add(MinimalDocxWriter.packageRelsPart())
            if (images.isNotEmpty()) {
                add(MinimalDocxWriter.documentRelsPart(images.map(EmbeddedImage::part)))
            }
            add(DocxPart(MinimalDocxWriter.DOCUMENT_PART, MinimalDocxWriter.utf8(documentXml)))
            images.forEach { image -> add(DocxPart(image.part.partName, image.part.bytes)) }
        }
        MinimalDocxWriter.write(parts, output)
    }

    // ---- 图片 ----

    private data class EmbeddedImage(val uri: String, val part: DocxImagePart)

    /**
     * 需要嵌入的图，按**在文档里出现的顺序**（干净题面在前，块按排序后的顺序），
     * 同一 uri 只嵌一份。读不出的 uri 直接不进列表（降级：省略图，不失败）。
     */
    private fun embeddedImages(
        input: MistakePdfExportInput,
        blocks: List<MistakePdfBlock>,
    ): List<EmbeddedImage> {
        val uris = buildList {
            input.cleanImageLocalUri?.takeIf(String::isNotBlank)?.let(::add)
            blocks.filterIsInstance<MistakePdfBlock.Image>().forEach { block ->
                block.localUri.takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
        var index = 0
        return uris.mapNotNull { uri ->
            val bytes = DocxImageSource.read(uri) ?: return@mapNotNull null
            index += 1
            EmbeddedImage(
                uri = uri,
                part = DocxImagePart(
                    partName = "$MEDIA_DIRECTORY/image$index.${bytes.fileExtension}",
                    relationshipId = "rId$index",
                    bytes = bytes.bytes,
                    widthPx = bytes.widthPx,
                    heightPx = bytes.heightPx,
                    fileExtension = bytes.fileExtension,
                    contentType = bytes.contentType,
                ),
            )
        }
    }

    /**
     * 交付尺寸：栏宽 × imageScale（与 PDF 的视觉宽同口径），高度按像素等比。
     *
     * 竖长图按**内容区高度**封顶后等比缩宽：PDF 计划器同样按内容高封顶（`imageWidth`），
     * 否则一张超长截图会超出 Word 页面、被裁掉一截。
     */
    private fun imageExtent(
        layout: MistakePdfLayout,
        image: DocxImagePart,
    ): Pair<Long, Long> {
        val marginTwips = layout.marginPt.toLong() * TWIPS_PER_POINT
        val contentTwips = PAGE_WIDTH_TWIPS - marginTwips * 2
        val columnTwips = if (layout.columnCount == 2) {
            (contentTwips - COLUMN_GAP_TWIPS) / 2
        } else {
            contentTwips
        }
        val naturalWidthEmu =
            (columnTwips * layout.imageScale).roundToInt().toLong() * EMU_PER_TWIP
        val naturalHeightEmu =
            (naturalWidthEmu * image.heightPx / image.widthPx).coerceAtLeast(1L)
        val maxHeightEmu = (PAGE_HEIGHT_TWIPS - marginTwips * 2) * EMU_PER_TWIP
        return if (naturalHeightEmu <= maxHeightEmu) {
            naturalWidthEmu to naturalHeightEmu
        } else {
            // 等比缩宽到内容区高度；至少保留 1 EMU，避免除零/零宽。
            ((naturalWidthEmu * maxHeightEmu) / naturalHeightEmu).coerceAtLeast(1L) to
                maxHeightEmu
        }
    }

    // ---- 文档正文 ----

    private fun documentXml(
        input: MistakePdfExportInput,
        blocks: List<MistakePdfBlock>,
        images: List<EmbeddedImage>,
    ): String {
        val typography = DocxTypography(input.layout.fontScale)
        val imagesByUri = images.associateBy(EmbeddedImage::uri)
        var drawingId = 0
        val body = StringBuilder()

        body.append(textParagraph(input.title, DocxTextStyle.TITLE, typography))
        body.append(textParagraph(input.subtitle, DocxTextStyle.META, typography))
        input.documentTitle
            ?.takeIf { it.isNotBlank() && it != input.title }
            ?.let { title ->
                body.append(
                    textParagraph("题面标题：$title", DocxTextStyle.META, typography),
                )
            }

        input.cleanImageLocalUri
            ?.takeIf(String::isNotBlank)
            ?.let(imagesByUri::get)
            ?.let { embedded ->
                body.append(textParagraph("干净题面", DocxTextStyle.HEADING, typography))
                drawingId += 1
                val (cx, cy) = imageExtent(input.layout, embedded.part)
                body.append(
                    MinimalDocxWriter.inlineImageParagraph(embedded.part, drawingId, cx, cy),
                )
            }

        blocks.forEach { block ->
            when (block) {
                is MistakePdfBlock.Paragraph ->
                    body.append(textParagraph(block.text, DocxTextStyle.BODY, typography))
                is MistakePdfBlock.SectionHeading ->
                    body.append(textParagraph(block.text, DocxTextStyle.HEADING, typography))
                is MistakePdfBlock.Formula -> {
                    if (block.latex.isNotBlank()) {
                        val formula = if (block.display) block.latex else "\$${block.latex}\$"
                        body.append(textParagraph(formula, DocxTextStyle.FORMULA, typography))
                    }
                    if (block.alternativeText.isNotBlank()) {
                        body.append(
                            textParagraph(
                                "读作：${block.alternativeText}",
                                DocxTextStyle.META,
                                typography,
                            ),
                        )
                    }
                }
                is MistakePdfBlock.ChoiceGroup -> {
                    if (block.prompt.isNotBlank()) {
                        body.append(textParagraph(block.prompt, DocxTextStyle.BODY, typography))
                    }
                    block.choices.forEachIndexed { index, choice ->
                        body.append(
                            textParagraph(
                                choiceLabel(index) + choice.text,
                                DocxTextStyle.CHOICE,
                                typography,
                            ),
                        )
                    }
                }
                is MistakePdfBlock.Note -> if (input.layout.includeNote) {
                    body.append(textParagraph("我的备注", DocxTextStyle.HEADING, typography))
                    body.append(textParagraph(block.text, DocxTextStyle.BODY, typography))
                }
                is MistakePdfBlock.Image -> {
                    if (block.title.isNotBlank()) {
                        body.append(textParagraph(block.title, DocxTextStyle.HEADING, typography))
                    }
                    imagesByUri[block.localUri]?.let { embedded ->
                        drawingId += 1
                        val (cx, cy) = imageExtent(input.layout, embedded.part)
                        body.append(
                            MinimalDocxWriter.inlineImageParagraph(embedded.part, drawingId, cx, cy),
                        )
                    }
                }
                is MistakePdfBlock.Figure -> {
                    // PDF 画矢量图形；Word 写标题 + 无障碍文字说明（见类 KDoc 的降级口径）。
                    block.title?.takeIf(String::isNotBlank)?.let { title ->
                        body.append(textParagraph(title, DocxTextStyle.HEADING, typography))
                    }
                    if (block.alternativeText.isNotBlank()) {
                        body.append(textParagraph(block.alternativeText, DocxTextStyle.BODY, typography))
                    }
                }
            }
        }

        answerSection(input.blocks, input.layout)?.let { lines ->
            body.append(textParagraph("答案", DocxTextStyle.HEADING, typography))
            lines.forEach { line -> body.append(textParagraph(line, DocxTextStyle.BODY, typography)) }
        }
        noteSection(input)?.let { note ->
            body.append(textParagraph("我的备注", DocxTextStyle.HEADING, typography))
            body.append(textParagraph(note, DocxTextStyle.BODY, typography))
        }

        body.append(sectionProperties(input.layout))
        return MinimalDocxWriter.XML_DECLARATION +
            "<w:document $DOCUMENT_NAMESPACES><w:body>" +
            body +
            "</w:body></w:document>"
    }

    /**
     * 答案区：与 `MistakePdfPagePlanner.addAnswerSection`（:311-340）同一判据与同一文案结构。
     * 未请求时返回 null（不排标题，也不留占位）。
     */
    private fun answerSection(
        blocks: List<MistakePdfBlock>,
        layout: MistakePdfLayout,
    ): List<String>? {
        val requested = layout.includeAnswer ||
            layout.templateId == MistakePdfLayout.TEMPLATE_WITH_ANSWERS
        if (!requested) return null
        val groups = if (blocks.any { it is MistakePdfBlock.SectionHeading }) {
            questionGroups(blocks).map { group -> group.filterIsInstance<MistakePdfBlock.ChoiceGroup>() }
        } else {
            blocks.filterIsInstance<MistakePdfBlock.ChoiceGroup>()
                .map { group -> listOf(group) }
                .ifEmpty { listOf(emptyList()) }
        }
        return groups.mapIndexed { index, choices ->
            val selected = choices.mapNotNull { group ->
                val selectedIndex = group.choices.indexOfFirst { it.selected }
                if (selectedIndex < 0) {
                    null
                } else {
                    choiceLabel(selectedIndex) + group.choices[selectedIndex].text
                }
            }
            val missing = selected.isEmpty() || selected.size < choices.size
            "${index + 1}. " + (if (selected.isEmpty()) {
                "暂无可导出的答案"
            } else {
                selected.joinToString(separator = "；") +
                    if (missing) "；部分小题暂无可导出的答案" else ""
            })
        }
    }

    /** 备注区（`error_book_entry.user_note`）：只在请求且确有内容时渲染（与计划器同口径）。 */
    private fun noteSection(input: MistakePdfExportInput): String? {
        if (!input.layout.includeNote) return null
        return input.userNote?.takeIf(String::isNotBlank)
    }

    private fun sectionProperties(layout: MistakePdfLayout): String {
        val margin = layout.marginPt.toLong() * TWIPS_PER_POINT
        val columns = if (layout.columnCount == 2) {
            "<w:cols w:num=\"2\" w:space=\"${COLUMN_GAP_TWIPS}\"/>"
        } else {
            ""
        }
        return "<w:sectPr>" +
            "<w:pgSz w:w=\"$PAGE_WIDTH_TWIPS\" w:h=\"$PAGE_HEIGHT_TWIPS\"/>" +
            "<w:pgMar w:top=\"$margin\" w:right=\"$margin\" w:bottom=\"$margin\" w:left=\"$margin\"" +
            " w:header=\"851\" w:footer=\"992\" w:gutter=\"0\"/>" +
            columns +
            "</w:sectPr>"
    }

    private fun textParagraph(
        text: String,
        style: DocxTextStyle,
        typography: DocxTypography,
    ): String = MinimalDocxWriter.paragraph(
        listOf(
            DocxRun(
                text = text,
                bold = style.bold,
                halfPointSize = style.halfPointSize(typography),
            ),
        ),
    )

    // ---- 顺序与标签：与 PDF 计划器同口径（planner 侧函数是私有实现，见类 KDoc） ----

    /**
     * 块顺序：空 [MistakePdfLayout.blockOrder] 保持原顺序；非空按白名单类型稳定排序，
     * 未列出的类型按原顺序排在最后（与 `MistakePdfPagePlanner.orderedBlocks` :247-269 同口径）。
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
        return questionGroups(blocks).flatMap { group ->
            val fixed = group.filter { it is MistakePdfBlock.SectionHeading }
            val notes = group.filterIsInstance<MistakePdfBlock.Note>()
            val ordered = group
                .filter { it !is MistakePdfBlock.SectionHeading && it !is MistakePdfBlock.Note }
                .withIndex()
                .sortedWith(
                    compareBy(
                        { priority[it.value.docxBlockKind] ?: Int.MAX_VALUE },
                        { it.index },
                    ),
                )
                .map { it.value }
            fixed + ordered + notes
        }
    }

    private fun questionGroups(blocks: List<MistakePdfBlock>): List<List<MistakePdfBlock>> {
        val groups = mutableListOf<MutableList<MistakePdfBlock>>()
        blocks.forEach { block ->
            if (groups.isEmpty() || block is MistakePdfBlock.SectionHeading) {
                // 显式 add：`groups += mutableListOf()` 在 Kotlin 里可能解析到
                // List.plus（返回新 List）而不是 MutableCollection.plusAssign，
                // 对 val 赋值直接编译失败。
                groups.add(mutableListOf<MistakePdfBlock>())
            }
            groups.last().add(block)
        }
        return groups
    }

    /** 与 `MistakePdfPagePlanner.choiceLabel`（:362-363）逐字一致。 */
    private fun choiceLabel(index: Int): String =
        if (index < 26) "${('A'.code + index).toChar()}. " else "${index + 1}. "

    private enum class DocxTextStyle(val basePoint: Float, val bold: Boolean) {
        TITLE(TITLE_POINT, true),
        HEADING(HEADING_POINT, true),
        BODY(BODY_POINT, false),
        CHOICE(BODY_POINT, false),
        META(META_POINT, false),
        FORMULA(FORMULA_POINT, false),
        ;

        fun halfPointSize(typography: DocxTypography): Int =
            typography.halfPoints(basePoint)
    }

    /** 字号档位换算（与 `PdfTypography` 同一因子：fontScale 2 = ×1.0）。 */
    private class DocxTypography(fontScale: Int) {
        private val factor = fontScale / 2f

        fun halfPoints(basePoint: Float): Int =
            (basePoint * factor * HALF_POINTS_PER_POINT).roundToInt()
    }

    private const val HALF_POINTS_PER_POINT = 2f
}

private val MistakePdfBlock.docxBlockKind: MistakePdfBlockKind
    get() = when (this) {
        is MistakePdfBlock.Image -> MistakePdfBlockKind.FIGURE
        is MistakePdfBlock.Note -> MistakePdfBlockKind.PARAGRAPH
        is MistakePdfBlock.Paragraph -> MistakePdfBlockKind.PARAGRAPH
        is MistakePdfBlock.SectionHeading -> MistakePdfBlockKind.SECTION_HEADING
        is MistakePdfBlock.Formula -> MistakePdfBlockKind.FORMULA
        is MistakePdfBlock.ChoiceGroup -> MistakePdfBlockKind.CHOICE_GROUP
        is MistakePdfBlock.Figure -> MistakePdfBlockKind.FIGURE
    }
