package com.tingyun.smartmistakebook.core.export

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 一个 OPC 部件：zip 内路径 + 字节。 */
internal data class DocxPart(val name: String, val bytes: ByteArray)

/** 一张已按交付尺寸校验过的图片部件（渲染器与部件写出器之间的形状）。 */
internal data class DocxImagePart(
    val partName: String,
    val relationshipId: String,
    val bytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val fileExtension: String,
    val contentType: String,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "A Word image part needs a positive pixel size" }
    }
}

/** 段落里的一段文本（粗体/字号为仅有的两个直接格式；不引入 styles.xml）。 */
internal data class DocxRun(
    val text: String,
    val bold: Boolean = false,
    /** 半磅（Word `w:sz` 单位），1pt = 2；null = 用默认字号。 */
    val halfPointSize: Int? = null,
)

/**
 * 手写 OOXML 的最小部件写出器（6B-1）。
 *
 * 它消灭的失败：仓库里没有 Word 生成器，而引第三方库（Apache POI / docx4j）会带进一个
 * 重量级依赖与它的 CVE/体积；这里只需要"把内容写成一个 Word 能打开的包"，所以按 OPC 最小集
 * 自己写：部件字节由渲染器给定，本对象只负责 XML 转义、段落/图片元素拼装与**确定性 zip**。
 *
 * **确定性**：条目顺序 = 调用顺序，时间戳固定为 DOS 下限（1980-01-01），压缩级别固定——
 * 同一输入在同一运行环境两次渲染字节一致（JVM 金样用例钉住；与 PDF 渲染器的确定性同口径）。
 *
 * **只写最小部件集**：`[Content_Types].xml` + `_rels/.rels` + `word/document.xml`
 * （有图片时再加 `word/_rels/document.xml.rels` 与 `word/media/` 下的图片部件）。
 * 不写 styles.xml / settings.xml——直接格式（`w:b` / `w:sz`）足够，缺省样式由 Word 自己补。
 */
internal object MinimalDocxWriter {
    const val CONTENT_TYPES_PART = "[Content_Types].xml"
    const val PACKAGE_RELS_PART = "_rels/.rels"
    const val DOCUMENT_PART = "word/document.xml"
    const val DOCUMENT_RELS_PART = "word/_rels/document.xml.rels"

    private const val FIXED_ENTRY_TIME_MILLIS = 0L
    private const val PACKAGE_RELS_NS =
        "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val OFFICE_DOCUMENT_RELATIONSHIP =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    private const val IMAGE_RELATIONSHIP =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
    private const val CONTENT_TYPES_NS =
        "http://schemas.openxmlformats.org/package/2006/content-types"

    fun utf8(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

    /** 写一个 OPC 包；条目顺序固定、时间戳固定（见 KDoc 的确定性口径）。 */
    fun write(parts: List<DocxPart>, output: File) {
        require(parts.isNotEmpty()) { "A Word package needs at least one part" }
        require(parts.map(DocxPart::name).distinct().size == parts.size) {
            "Duplicate Word package part name"
        }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            parts.forEach { part ->
                zip.putNextEntry(
                    ZipEntry(part.name).apply { time = FIXED_ENTRY_TIME_MILLIS },
                )
                zip.write(part.bytes)
                zip.closeEntry()
            }
        }
    }

    /** `[Content_Types].xml`：Default 必须在 Override 之前（OPC 架构是 sequence）。 */
    fun contentTypesPart(imageExtensions: Set<String>): DocxPart {
        val defaults = buildString {
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            imageExtensions.sorted().forEach { extension ->
                append("<Default Extension=\"").append(extension).append("\" ContentType=\"")
                append(IMAGE_CONTENT_TYPES.getValue(extension))
                append("\"/>")
            }
        }
        return DocxPart(
            name = CONTENT_TYPES_PART,
            bytes = utf8(
                XML_DECLARATION +
                    "<Types xmlns=\"$CONTENT_TYPES_NS\">" +
                    defaults +
                    "<Override PartName=\"/${DOCUMENT_PART}\" ContentType=\"" +
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml" +
                    "\"/></Types>",
            ),
        )
    }

    /**
     * 包级关系：唯一入口 `word/document.xml`。
     *
     * Target 是**相对包根**（`_rels/.rels` 的源就是包本身），必须写完整的
     * `word/document.xml`；写成 `document.xml` 会指向一个不存在的部件，Word 打开时直接
     * 报"文件已损坏"。
     */
    fun packageRelsPart(): DocxPart = DocxPart(
        name = PACKAGE_RELS_PART,
        bytes = utf8(
            XML_DECLARATION +
                "<Relationships xmlns=\"$PACKAGE_RELS_NS\">" +
                "<Relationship Id=\"rId1\" Type=\"$OFFICE_DOCUMENT_RELATIONSHIP\" " +
                "Target=\"$DOCUMENT_PART\"/>" +
                "</Relationships>",
        ),
    )

    /** 文档级关系：图片部件（没有图片时不写这个部件）。 */
    fun documentRelsPart(images: List<DocxImagePart>): DocxPart {
        require(images.isNotEmpty()) { "Document relationships are only written for embedded images" }
        return DocxPart(
            name = DOCUMENT_RELS_PART,
            bytes = utf8(
                XML_DECLARATION +
                    "<Relationships xmlns=\"$PACKAGE_RELS_NS\">" +
                    images.joinToString(separator = "") { image ->
                        "<Relationship Id=\"${image.relationshipId}\" Type=\"$IMAGE_RELATIONSHIP\" " +
                            "Target=\"${image.partName.removePrefix("word/")}\"/>"
                    } +
                    "</Relationships>",
            ),
        )
    }

    /** 一个段落（空 [runs] = 空行）。 */
    fun paragraph(runs: List<DocxRun>): String =
        "<w:p>" + runs.joinToString(separator = "", transform = ::runXml) + "</w:p>"

    fun paragraph(text: String, bold: Boolean = false, halfPointSize: Int? = null): String =
        paragraph(listOf(DocxRun(text = text, bold = bold, halfPointSize = halfPointSize)))

    /** 内联图片段落（DrawingML 最小 inline 形状；尺寸按像素等比换算成 EMU）。 */
    fun inlineImageParagraph(
        image: DocxImagePart,
        drawingId: Int,
        extentCx: Long,
        extentCy: Long,
    ): String {
        require(drawingId > 0) { "A drawing id must be positive" }
        require(extentCx > 0 && extentCy > 0) { "A drawing extent must be positive" }
        val name = image.partName.substringAfterLast('/')
        return buildString {
            append("<w:p><w:r><w:drawing>")
            append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
            append("<wp:extent cx=\"").append(extentCx).append("\" cy=\"").append(extentCy).append("\"/>")
            append("<wp:docPr id=\"").append(drawingId).append("\" name=\"")
                .append(escapeXmlText(name)).append("\"/>")
            append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"").append(drawingId).append("\" name=\"")
                .append(escapeXmlText(name)).append("\"/><pic:cNvPicPr/></pic:nvPicPr>")
            append("<pic:blipFill><a:blip r:embed=\"").append(image.relationshipId)
                .append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
            append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(extentCx)
                .append("\" cy=\"").append(extentCy).append("\"/></a:xfrm>")
            append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
            append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
        }
    }

    /**
     * XML 1.0 文本转义：`& < > " '` 五个实体 + 剔除 XML 1.0 非法控制字符。
     *
     * 不剔除的控制字符（0x00–0x08 / 0x0B / 0x0C / 0x0E–0x1F / 0xFFFE / 0xFFFF）会让
     * Word 直接拒绝整份文件，而题面文本来自 OCR/模型，出现控制字符并不罕见。
     * 换行在 [runXml] 里转成 `w:br`，不落进 `w:t`。
     */
    fun escapeXmlText(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when {
                character == '&' -> append("&amp;")
                character == '<' -> append("&lt;")
                character == '>' -> append("&gt;")
                character == '"' -> append("&quot;")
                character == '\'' -> append("&apos;")
                isXml10Invalid(character) -> Unit
                else -> append(character)
            }
        }
    }

    private fun runXml(run: DocxRun): String {
        val properties = buildString {
            if (run.bold) append("<w:b/>")
            run.halfPointSize?.let { size ->
                append("<w:sz w:val=\"").append(size).append("\"/>")
                append("<w:szCs w:val=\"").append(size).append("\"/>")
            }
        }
        // CRLF/CR 先归一成 LF 再切：否则 CR 会留在 w:t 里被 XML 解析规范化为 LF，
        // 行内换行不成立（Word 侧渲染异常）——对抗性复核 finding，2026-10-18。
        val normalized = run.text.replace("\r\n", "\n").replace('\r', '\n')
        val text = normalized.split('\n').joinToString(separator = "<w:br/>") { line ->
            "<w:t xml:space=\"preserve\">${escapeXmlText(line)}</w:t>"
        }
        return if (properties.isEmpty()) {
            "<w:r>$text</w:r>"
        } else {
            "<w:r><w:rPr>$properties</w:rPr>$text</w:r>"
        }
    }

    private fun isXml10Invalid(character: Char): Boolean {
        val code = character.code
        return code <= 0x08 ||
            code == 0x0B ||
            code == 0x0C ||
            code in 0x0E..0x1F ||
            code == 0xFFFE ||
            code == 0xFFFF
    }

    const val XML_DECLARATION: String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    val IMAGE_CONTENT_TYPES: Map<String, String> = mapOf(
        "png" to "image/png",
        "jpeg" to "image/jpeg",
    )
}
