package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 手写 OOXML 渲染器（6B-1）的 JVM 金样：zip 读回 + XML 解析。
 *
 * 钉住三件事：① 最小部件集齐备且结构能被 OOXML/Word 的命名空间规则解析；② 文本内容
 * **逐条**与块序列一致（顺序、选项标签、公式/图形/备注的降级口径）；③ 同一输入两次渲染
 * 字节一致（确定性交付）。
 */
class DocxMistakeRendererTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** 同一用例里可能渲染多份（带/不带答案区各一份），文件名不能重复。 */
    private var renderCounter = 0

    @Test
    fun `the package carries the minimal part set with the document override and entry relationship`() {
        val file = renderToFile(input(blocks = listOf(paragraph("题干"))))

        val parts = readZip(file)
        assertEquals(
            setOf(
                MinimalDocxWriter.CONTENT_TYPES_PART,
                MinimalDocxWriter.PACKAGE_RELS_PART,
                MinimalDocxWriter.DOCUMENT_PART,
            ),
            parts.keys,
        )

        val contentTypes = parts.getValue(MinimalDocxWriter.CONTENT_TYPES_PART).decodeToString()
        assertTrue(contentTypes.contains("Extension=\"rels\""))
        assertTrue(contentTypes.contains("Extension=\"xml\""))
        assertTrue(
            contentTypes.contains(
                "Override PartName=\"/word/document.xml\" ContentType=\"" +
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"",
            ),
        )
        // OPC 架构里 Default 必须在 Override 之前。
        assertTrue(contentTypes.indexOf("Extension=") < contentTypes.indexOf("<Override"))
        assertFalse("没有图片时不写 png 默认项", contentTypes.contains("Extension=\"png\""))

        val packageRels = parts.getValue(MinimalDocxWriter.PACKAGE_RELS_PART).decodeToString()
        assertTrue(packageRels.contains("Target=\"word/document.xml\""))
        assertTrue(packageRels.contains("relationships/officeDocument"))

        // document.xml 能被命名空间感知的解析器读出 w:document/w:body（结构合法）。
        val document = parseXml(parts.getValue(MinimalDocxWriter.DOCUMENT_PART))
        assertEquals("document", document.documentElement.localName)
        assertEquals(W_NS, document.documentElement.namespaceURI)
        val body = document.getElementsByTagNameNS(W_NS, "body").item(0)
        assertEquals(1, document.getElementsByTagNameNS(W_NS, "body").length)
        // WordprocessingML 要求 w:sectPr 是 w:body 的最后一个子元素。
        assertEquals("sectPr", body.lastChild.localName)
        assertEquals(W_NS, body.lastChild.namespaceURI)
    }

    @Test
    fun `the document text follows the block sequence item by item and escapes XML`() {
        val file = renderToFile(
            input(
                documentTitle = "题面",
                blocks = listOf(
                    paragraph("已知 f(x)=x² <单调> & \"区间\"。"),
                    MistakePdfBlock.SectionHeading("解析", "解析步骤"),
                    MistakePdfBlock.Formula(
                        id = "formula-1",
                        latex = "x^2",
                        alternativeText = "x 的平方",
                        display = false,
                    ),
                    MistakePdfBlock.ChoiceGroup(
                        id = "choice-1",
                        prompt = "选择正确的一项",
                        choices = listOf(
                            MistakePdfChoice("a", "甲", selected = true, enabled = true),
                            MistakePdfChoice("b", "乙", selected = false, enabled = true),
                        ),
                        enabled = true,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "函数单调区间",
                "MATH · 第 3 版",
                "题面标题：题面",
                "已知 f(x)=x² <单调> & \"区间\"。",
                "解析步骤",
                "\$x^2\$",
                "读作：x 的平方",
                "选择正确的一项",
                "A. 甲",
                "B. 乙",
            ),
            documentTexts(readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART)),
        )

        // 转义发生在字节层：解析回来的文本与原文本逐字一致（不丢实体、不双重转义）。
        val raw = readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()
        assertTrue(raw.contains("&lt;单调&gt;"))
        assertTrue(raw.contains("&amp;"))
        assertTrue(raw.contains("&quot;"))
    }

    /** XML 1.0 非法控制字符必须剔除（否则 Word 拒绝整份文件）。 */
    @Test
    fun `xml-control characters are stripped instead of breaking the package`() {
        val file = renderToFile(input(blocks = listOf(paragraph("可控\u0001字符"))))

        assertEquals(
            listOf("函数单调区间", "MATH · 第 3 版", "题面标题：题面", "可控字符"),
            documentTexts(readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART)),
        )
    }

    /**
     * CRLF/CR 必须先归一成 LF 再切行：CR 留在 `w:t` 里会被 XML 解析规范化为 LF，
     * 行内换行不成立（Word 侧渲染异常）——对抗性复核 finding，2026-10-18。
     */
    @Test
    fun `carriage returns become real line breaks instead of leaking into text runs`() {
        val file = renderToFile(input(blocks = listOf(paragraph("第一行\r\n第二行\r第三行"))))
        val documentXml = readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()

        assertFalse("原始 XML 里不许残留 CR", documentXml.contains('\r'))
        assertTrue(
            "三行之间要有真换行（w:br）：两处断点都要落对",
            documentXml.contains("第一行</w:t><w:br/><w:t xml:space=\"preserve\">第二行") &&
                documentXml.contains("第二行</w:t><w:br/><w:t xml:space=\"preserve\">第三行"),
        )
        assertEquals(
            listOf("函数单调区间", "MATH · 第 3 版", "题面标题：题面", "第一行", "第二行", "第三行"),
            documentTexts(readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART)),
        )
    }

    @Test
    fun `answers are laid out as their own section and notes only when requested`() {
        val blocks = listOf(
            paragraph("第一题题干"),
            choiceGroup(questionId = "q1", selectedIndex = 0),
        )

        val withExtras = renderToFile(
            input(
                blocks = blocks,
                userNote = "这题考单调性",
                layout = MistakePdfLayout(includeAnswer = true, includeNote = true),
            ),
        )
        assertEquals(
            listOf(
                "函数单调区间",
                "MATH · 第 3 版",
                "题面标题：题面",
                "第一题题干",
                "A. 甲",
                "B. 乙",
                "答案",
                "1. A. 甲",
                "我的备注",
                "这题考单调性",
            ),
            documentTexts(readZip(withExtras).getValue(MinimalDocxWriter.DOCUMENT_PART)),
        )

        // 不请求答案/备注：同一份块序列不出现答案区，也不出现备注（不静默带上）。
        val bare = renderToFile(input(blocks = blocks, userNote = "这题考单调性"))
        val bareTexts = documentTexts(readZip(bare).getValue(MinimalDocxWriter.DOCUMENT_PART))
        assertFalse(bareTexts.contains("答案"))
        assertFalse(bareTexts.contains("这题考单调性"))
    }

    @Test
    fun `a missing answer is stated per question instead of leaving a silent hole`() {
        val file = renderToFile(
            input(
                blocks = listOf(choiceGroup(questionId = "q1", selectedIndex = -1)),
                layout = MistakePdfLayout(includeAnswer = true),
            ),
        )

        val texts = documentTexts(readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART))
        assertTrue(texts.contains("答案"))
        assertTrue(texts.contains("1. 暂无可导出的答案"))
    }

    @Test
    fun `a clean image is embedded as a media part with an image relationship and its real aspect ratio`() {
        val png = pngBytes(width = 4, height = 2)
        val imageFile = temporaryFolder.newFile("clean.png").apply { writeBytes(png) }

        val file = renderToFile(
            input(
                blocks = listOf(paragraph("题干")),
                cleanImageLocalUri = imageFile.toURI().toString(),
            ),
        )

        val parts = readZip(file)
        assertTrue(parts.containsKey("word/media/image1.png"))
        assertArrayEquals(png, parts.getValue("word/media/image1.png"))

        val contentTypes = parts.getValue(MinimalDocxWriter.CONTENT_TYPES_PART).decodeToString()
        assertTrue(contentTypes.contains("Extension=\"png\" ContentType=\"image/png\""))

        val rels = parts.getValue(MinimalDocxWriter.DOCUMENT_RELS_PART).decodeToString()
        assertTrue(rels.contains("Id=\"rId1\""))
        assertTrue(rels.contains("Target=\"media/image1.png\""))

        val documentXml = parts.getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()
        assertTrue(documentXml.contains("r:embed=\"rId1\""))
        // 默认版式：48pt 边距 → 内容宽 9986 twip → 6 341 110 EMU；4:2 图等比得 3 170 555。
        assertTrue(documentXml.contains("<wp:extent cx=\"6341110\" cy=\"3170555\"/>"))
    }

    @Test
    fun `a very tall image is capped to the content height instead of running off the page`() {
        val imageFile = temporaryFolder.newFile("tall.png").apply {
            writeBytes(pngBytes(width = 100, height = 1000))
        }

        val file = renderToFile(
            input(
                blocks = listOf(paragraph("题干")),
                cleanImageLocalUri = imageFile.toURI().toString(),
            ),
        )

        // 默认版式内容高 = 16838 - 2×48pt×20 = 14918 twip = 9 472 930 EMU；
        // 原图 100×1000 等比会高 10 倍于宽，按内容高封顶后宽 = 9 472 930 / 10。
        val documentXml = readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()
        assertTrue(documentXml.contains("<wp:extent cx=\"947293\" cy=\"9472930\"/>"))
    }

    /**
     * KD-55 正常路径：干净题面的记录 sha/长度与实际文件一致时照常嵌入（复核不误伤）。
     */
    @Test
    fun `a clean image that matches its record is embedded`() {
        val png = pngBytes(width = 4, height = 2)
        val imageFile = temporaryFolder.newFile("recorded.png").apply { writeBytes(png) }

        val file = renderToFile(
            input(
                blocks = listOf(paragraph("题干")),
                cleanImageLocalUri = imageFile.toURI().toString(),
                cleanImageSha256 = sha256Hex(png),
                cleanImageByteSize = png.size.toLong(),
            ),
        )

        val parts = readZip(file)
        assertArrayEquals(png, parts.getValue("word/media/image1.png"))
    }

    /**
     * KD-55 失败路径：记录读完之后文件被换掉（TOCTOU）——渲染必须省略这张图，
     * 不能把未经复核的字节写进 Word 包；题面其余内容照常渲染。
     */
    @Test
    fun `a clean image that no longer matches its record is omitted instead of embedded`() {
        val recorded = pngBytes(width = 4, height = 2)
        val replacement = pngBytes(width = 8, height = 6)
        val imageFile = temporaryFolder.newFile("swapped.png").apply { writeBytes(replacement) }

        val file = renderToFile(
            input(
                blocks = listOf(paragraph("题干")),
                cleanImageLocalUri = imageFile.toURI().toString(),
                cleanImageSha256 = sha256Hex(recorded),
                cleanImageByteSize = recorded.size.toLong(),
            ),
        )

        val parts = readZip(file)
        assertFalse(parts.keys.any { it.startsWith("word/media/") })
        // 图被省略时连"干净题面"标题也不出现（与既有的读不出降级同一口径）。
        assertEquals(
            listOf("函数单调区间", "MATH · 第 3 版", "题面标题：题面", "题干"),
            documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART)),
        )
    }

    /** KD-55：批量 Image 块同样按自己的记录复核（长度相同、字节不同 → 不嵌）。 */
    @Test
    fun `a batch image block is verified against its record before embedding`() {
        val png = pngBytes(width = 2, height = 2)
        val other = pngBytes(width = 3, height = 2)
        val imageFile = temporaryFolder.newFile("batch-recorded.png").apply { writeBytes(png) }
        val uri = imageFile.toURI().toString()

        val matched = renderToFile(
            input(
                blocks = listOf(
                    MistakePdfBlock.Image(
                        id = "q1-image",
                        localUri = uri,
                        title = "第 1 题 · 干净题面",
                        expectedContentSha256 = sha256Hex(png),
                        expectedByteSize = png.size.toLong(),
                    ),
                ),
            ),
        )
        assertTrue(readZip(matched).containsKey("word/media/image1.png"))

        val mismatched = renderToFile(
            input(
                blocks = listOf(
                    MistakePdfBlock.Image(
                        id = "q1-image",
                        localUri = uri,
                        title = "第 1 题 · 干净题面",
                        expectedContentSha256 = sha256Hex(other),
                        expectedByteSize = png.size.toLong(),
                    ),
                ),
            ),
        )
        val mismatchedParts = readZip(mismatched)
        assertFalse(mismatchedParts.keys.any { it.startsWith("word/media/") })
        // 块标题仍然在（图省略，题面文字不丢）。
        assertTrue(
            documentTexts(mismatchedParts.getValue(MinimalDocxWriter.DOCUMENT_PART))
                .contains("第 1 题 · 干净题面"),
        )
    }

    @Test
    fun `a clean image that cannot be read is omitted while the rest of the document still renders`() {
        val file = renderToFile(
            input(
                blocks = listOf(paragraph("题干")),
                cleanImageLocalUri = File(temporaryFolder.root, "missing.png").toURI().toString(),
            ),
        )

        val parts = readZip(file)
        assertEquals(
            setOf(
                MinimalDocxWriter.CONTENT_TYPES_PART,
                MinimalDocxWriter.PACKAGE_RELS_PART,
                MinimalDocxWriter.DOCUMENT_PART,
            ),
            parts.keys,
        )
        assertEquals(
            listOf("函数单调区间", "MATH · 第 3 版", "题面标题：题面", "题干"),
            documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART)),
        )
    }

    @Test
    fun `a batch image block embeds its file or degrades to its title alone`() {
        val png = pngBytes(width = 2, height = 2)
        val imageFile = temporaryFolder.newFile("batch.png").apply { writeBytes(png) }
        val missingUri = File(temporaryFolder.root, "gone.png").toURI().toString()

        val embedded = renderToFile(
            input(
                blocks = listOf(
                    MistakePdfBlock.Image("q1-image", imageFile.toURI().toString(), "第 1 题 · 干净题面"),
                ),
            ),
        )
        val embeddedParts = readZip(embedded)
        assertTrue(embeddedParts.containsKey("word/media/image1.png"))
        assertTrue(
            documentTexts(embeddedParts.getValue(MinimalDocxWriter.DOCUMENT_PART))
                .contains("第 1 题 · 干净题面"),
        )

        val degraded = renderToFile(
            input(blocks = listOf(MistakePdfBlock.Image("q1-image", missingUri, "第 1 题 · 干净题面"))),
        )
        val degradedParts = readZip(degraded)
        assertFalse(degradedParts.keys.any { it.startsWith("word/media/") })
        assertTrue(
            documentTexts(degradedParts.getValue(MinimalDocxWriter.DOCUMENT_PART))
                .contains("第 1 题 · 干净题面"),
        )
    }

    @Test
    fun `a vector figure keeps its title and text alternative instead of vanishing`() {
        val file = renderToFile(
            input(
                blocks = listOf(
                    MistakePdfBlock.Figure(
                        id = "figure-1",
                        title = "受力分析图",
                        alternativeText = "斜面上一物体受重力与支持力",
                        schema = FigureSchema.SymbolTable(
                            headers = listOf("项"),
                            rows = listOf(listOf("重力")),
                        ),
                    ),
                ),
            ),
        )

        val texts = documentTexts(readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART))
        assertTrue(texts.contains("受力分析图"))
        assertTrue(texts.contains("斜面上一物体受重力与支持力"))
    }

    @Test
    fun `the same input renders byte for byte identical packages`() {
        val blocks = listOf(
            paragraph("题干"),
            choiceGroup(questionId = "q1", selectedIndex = 0),
            MistakePdfBlock.Formula("f", "x^2", "x 的平方", display = true),
        )

        val first = temporaryFolder.newFile("first.docx")
        val second = temporaryFolder.newFile("second.docx")
        DocxMistakeRenderer.render(input(blocks = blocks), first)
        DocxMistakeRenderer.render(input(blocks = blocks), second)

        assertArrayEquals(first.readBytes(), second.readBytes())
    }

    @Test
    fun `page size, margins and columns follow the export layout`() {
        val file = renderToFile(
            input(
                blocks = listOf(paragraph("题干")),
                layout = MistakePdfLayout(marginPt = 60, columnCount = 2),
            ),
        )

        val documentXml = readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()
        assertTrue(documentXml.contains("<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"))
        // 60pt = 1200 twip，四边同值。
        assertTrue(
            documentXml.contains(
                "<w:pgMar w:top=\"1200\" w:right=\"1200\" w:bottom=\"1200\" w:left=\"1200\"",
            ),
        )
        assertTrue(documentXml.contains("<w:cols w:num=\"2\""))
    }

    // ---- fixtures ----

    private fun renderToFile(input: MistakePdfExportInput): File {
        // 不预创建文件（renderer 自己会写）；同一用例多次调用时名字递增，
        // 避免 TemporaryFolder.newFile 在第二次调用时因同名文件已存在而失败。
        renderCounter += 1
        val file = File(temporaryFolder.root, "mistake-$renderCounter.docx")
        DocxMistakeRenderer.render(input, file)
        return file
    }

    private fun input(
        blocks: List<MistakePdfBlock>,
        title: String = "函数单调区间",
        documentTitle: String? = "题面",
        cleanImageLocalUri: String? = null,
        cleanImageSha256: String? = null,
        cleanImageByteSize: Long? = null,
        userNote: String? = null,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry-1",
        problemId = "problem-1",
        problemRevisionId = "revision-3",
        revisionNumber = 3,
        title = title,
        subject = "MATH",
        subtitle = "MATH · 第 3 版",
        documentTitle = documentTitle,
        blocks = blocks,
        questionDocumentSha256 = "a".repeat(64),
        inputSha256 = "b".repeat(64),
        cleanImageLocalUri = cleanImageLocalUri,
        cleanImageSha256 = cleanImageSha256,
        cleanImageByteSize = cleanImageByteSize,
        userNote = userNote,
        layout = layout,
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun paragraph(text: String) = MistakePdfBlock.Paragraph(id = "paragraph-1", text = text)

    private fun choiceGroup(questionId: String, selectedIndex: Int) = MistakePdfBlock.ChoiceGroup(
        id = questionId,
        prompt = "",
        choices = listOf(
            MistakePdfChoice("a", "甲", selected = selectedIndex == 0, enabled = true),
            MistakePdfChoice("b", "乙", selected = selectedIndex == 1, enabled = true),
        ),
        enabled = true,
    )

    private fun readZip(file: File): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
            }
        }
        return parts
    }

    private fun documentTexts(documentXml: ByteArray): List<String> {
        val document = parseXml(documentXml)
        val nodes = document.getElementsByTagNameNS(W_NS, "t")
        return (0 until nodes.length).map { index -> nodes.item(index).textContent }
    }

    /**
     * 解析本用例自产的字节；仍按"不解析不可信 XML"的口径关掉 DOCTYPE 与外部实体
     * （不启用外部实体、拒绝 DOCTYPE），避免这个解析口以后被复用到外部输入时成为缺口。
     * 个别 JVM 不认识的特性名按不支持处理（runCatching）——不影响本用例的断言。
     */
    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    /** 结构完整的 PNG（签名 + IHDR + IEND）；IHDR 给出真实宽高，渲染器只读头部。 */
    private fun pngBytes(width: Int, height: Int): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply {
                update(typeBytes)
                update(data)
            }.value
            return byteArrayOf(
                (data.size ushr 24).toByte(),
                (data.size ushr 16).toByte(),
                (data.size ushr 8).toByte(),
                data.size.toByte(),
            ) + typeBytes + data + byteArrayOf(
                (crc ushr 24).toByte(),
                (crc ushr 16).toByte(),
                (crc ushr 8).toByte(),
                crc.toByte(),
            )
        }

        val header = byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(),
            (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(),
            (height ushr 8).toByte(), height.toByte(),
            8, 6, 0, 0, 0,
        )
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", header) +
            chunk("IEND", ByteArray(0))
    }

    private companion object {
        const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }
}
