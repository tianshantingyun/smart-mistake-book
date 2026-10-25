package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.VisualRole
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * T7（2026-10-25）：导出卷面上 AI 生成图的**用户可见显式标识**（R3 差距清单 G3，
 * 《人工智能生成合成内容标识办法》第四条第二款）。
 *
 * 钉住四件事：
 * 1. 题目区 CLEAN_IMAGE 图（含旧调用方 null）与解析区每张讲解图（TUTOR_EXPLANATION）
 *    图下各带一行固定图注，且图注紧跟自己的图；
 * 2. QUESTION_DIAGRAM（题目自带示意图，无生产写入点、无法证明出自模型）**不伪报**；
 * 3. 没有生成图的卷子一个图注都没有（转录/原文不标 AI）；
 * 4. DOCX 与 PDF 同判据；加图注后 role-aware 与 legacy 形态仍逐字节一致（不破坏
 *    `MistakeExportVisualRoleTest` 的旧输出等价口径）。
 */
class AiGeneratedImageCaptionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val measure = PdfTextMeasure { text, start, end, style ->
        (end - start) * style.textSize
    }

    // ---- PDF 计划 ----

    @Test
    fun `the clean question image carries an explicit ai caption right below it`() {
        val plan = MistakePdfPagePlanner.plan(
            input(questionImageRole = VisualRole.CLEAN_IMAGE.name),
            measure,
            PdfImageSize(widthPx = 200, heightPx = 100),
        )
        val items = plan.flattened()
        val imageIndex = items.indexOfFirst { it is PdfPlannedItem.Image }
        val captionLines = items.withIndex()
            .filter { it.value is PdfPlannedItem.Line && (it.value as PdfPlannedItem.Line).text == AI_GENERATED_IMAGE_CAPTION }
        assertEquals(1, captionLines.size)
        assertEquals("图注必须紧跟题目图", imageIndex + 1, captionLines.single().index)
    }

    @Test
    fun `a question diagram is not labeled as ai generated`() {
        val plan = MistakePdfPagePlanner.plan(
            input(questionImageRole = VisualRole.QUESTION_DIAGRAM.name),
            measure,
            PdfImageSize(widthPx = 200, heightPx = 100),
        )
        assertFalse(
            "题目自带示意图不能被伪报成 AI 生成",
            plan.flattened().filterIsInstance<PdfPlannedItem.Line>()
                .any { it.text == AI_GENERATED_IMAGE_CAPTION },
        )
    }

    @Test
    fun `every solution image is captioned and a paper without generated images has none`() {
        val withSolution = MistakePdfPagePlanner.plan(
            input(
                questionImageRole = null,
                solutionImages = listOf(
                    explanationImage("file:///explain-a.png"),
                    explanationImage("file:///explain-b.png"),
                ),
                layout = MistakePdfLayout(includeSolution = true),
            ),
            measure,
            cleanImageSize = null,
            cleanImageSizes = mapOf(
                "file:///explain-a.png" to PdfImageSize(widthPx = 100, heightPx = 50),
                "file:///explain-b.png" to PdfImageSize(widthPx = 100, heightPx = 50),
            ),
        )
        val captionIndexes = withSolution.flattened()
            .withIndex()
            .filter { it.value is PdfPlannedItem.Line && (it.value as PdfPlannedItem.Line).text == AI_GENERATED_IMAGE_CAPTION }
            .map { it.index }
        assertEquals("每张讲解图各带一行图注", 2, captionIndexes.size)
        val imageIndexes = withSolution.flattened()
            .withIndex()
            .filter { it.value is PdfPlannedItem.Image }
            .map { it.index }
        assertEquals(listOf(imageIndexes[0] + 1, imageIndexes[1] + 1), captionIndexes)

        val plain = MistakePdfPagePlanner.plan(input(questionImageRole = null), measure)
        assertFalse(
            "没有生成图的卷子不得出现 AI 图注（OCR 转录/原文不是生成内容）",
            plain.flattened().filterIsInstance<PdfPlannedItem.Line>()
                .any { it.text == AI_GENERATED_IMAGE_CAPTION },
        )
    }

    @Test
    fun `batch question-area image blocks are captioned by role`() {
        val plan = MistakePdfPagePlanner.plan(
            input(
                questionImageRole = null,
                blocks = listOf(
                    MistakePdfBlock.Image(
                        id = "block-a",
                        localUri = "file:///a.png",
                        title = "第 1 题 · 干净题面",
                        role = VisualRole.CLEAN_IMAGE.name,
                    ),
                    MistakePdfBlock.Image(
                        id = "block-b",
                        localUri = "file:///b.png",
                        title = "第 2 题 · 题图",
                        role = VisualRole.QUESTION_DIAGRAM.name,
                    ),
                ),
            ),
            measure,
            cleanImageSize = null,
            cleanImageSizes = mapOf(
                "file:///a.png" to PdfImageSize(widthPx = 100, heightPx = 50),
                "file:///b.png" to PdfImageSize(widthPx = 100, heightPx = 50),
            ),
        )
        val items = plan.flattened()
        val captionIndexes = items.withIndex()
            .filter { it.value is PdfPlannedItem.Line && (it.value as PdfPlannedItem.Line).text == AI_GENERATED_IMAGE_CAPTION }
            .map { it.index }
        assertEquals("只有 CLEAN_IMAGE 角色的卷面图块带图注", 1, captionIndexes.size)
        val firstImageIndex = items.indexOfFirst { it is PdfPlannedItem.Image }
        assertEquals(firstImageIndex + 1, captionIndexes.single())
    }

    // ---- DOCX ----

    @Test
    fun `docx captions the clean and solution images and not the question diagram`() {
        val clean = png(width = 4, height = 2)
        val explanation = png(width = 2, height = 2)
        val cleanFile = temporaryFolder.newFile("caption-clean.png").apply { writeBytes(clean) }
        val explanationFile = temporaryFolder.newFile("caption-explain.png").apply { writeBytes(explanation) }

        val labeled = renderDocx(
            input(
                questionImageRole = VisualRole.CLEAN_IMAGE.name,
                cleanImageUri = cleanFile.toURI().toString(),
                solutionImages = listOf(
                    explanationImage(explanationFile.toURI().toString()),
                ),
                layout = MistakePdfLayout(includeSolution = true),
            ),
            "captioned.docx",
        )
        assertEquals(2, documentTexts(labeled).count { it == AI_GENERATED_IMAGE_CAPTION })

        val diagramFile = temporaryFolder.newFile("caption-diagram.png").apply { writeBytes(clean) }
        val unlabeled = renderDocx(
            input(
                questionImageRole = VisualRole.QUESTION_DIAGRAM.name,
                cleanImageUri = diagramFile.toURI().toString(),
            ),
            "diagram.docx",
        )
        assertFalse(
            "题目示意图在 Word 里同样不得伪报",
            documentTexts(unlabeled).contains(AI_GENERATED_IMAGE_CAPTION),
        )
    }

    @Test
    fun `docx legacy shape stays byte identical to the role aware shape under captions`() {
        val clean = png(width = 4, height = 2)
        val cleanFile = temporaryFolder.newFile("identical-clean.png").apply { writeBytes(clean) }
        val roleAware = input(
            questionImageRole = VisualRole.CLEAN_IMAGE.name,
            cleanImageUri = cleanFile.toURI().toString(),
        )
        val legacyShape = withoutRole(roleAware)

        val roleAwareFile = renderDocx(roleAware, "role-aware-caption.docx")
        val legacyFile = renderDocx(legacyShape, "legacy-shape-caption.docx")

        assertArrayEquals(legacyFile.readBytes(), roleAwareFile.readBytes())
    }

    // ---- fixtures ----

    private fun explanationImage(uri: String): MistakePdfExportImage = MistakePdfExportImage(
        role = VisualRole.TUTOR_EXPLANATION.name,
        localUri = uri,
    )

    private fun input(
        questionImageRole: String?,
        cleanImageUri: String? = null,
        blocks: List<MistakePdfBlock> = listOf(MistakePdfBlock.Paragraph(id = "stem", text = "题干")),
        solutionImages: List<MistakePdfExportImage> = emptyList(),
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry-caption",
        problemId = "problem-caption",
        problemRevisionId = "revision-caption",
        revisionNumber = 1,
        title = "测试题",
        subject = "MATH",
        subtitle = "MATH · 第 1 版",
        documentTitle = null,
        blocks = blocks,
        questionDocumentSha256 = "0".repeat(64),
        inputSha256 = "1".repeat(64),
        cleanImageLocalUri = cleanImageUri,
        questionImageRole = questionImageRole,
        solutionImages = solutionImages,
        layout = layout,
    )

    /** 同一份输入去掉角色字段（模拟旧调用方；与 `MistakeExportVisualRoleTest` 同口径）。 */
    private fun withoutRole(input: MistakePdfExportInput): MistakePdfExportInput =
        MistakePdfExportInput(
            errorBookEntryId = input.errorBookEntryId,
            problemId = input.problemId,
            problemRevisionId = input.problemRevisionId,
            revisionNumber = input.revisionNumber,
            title = input.title,
            subject = input.subject,
            subtitle = input.subtitle,
            documentTitle = input.documentTitle,
            blocks = input.blocks,
            questionDocumentSha256 = input.questionDocumentSha256,
            inputSha256 = input.inputSha256,
            maxPages = input.maxPages,
            maxRenderedLines = input.maxRenderedLines,
            maxPdfBytes = input.maxPdfBytes,
            cleanImageLocalUri = input.cleanImageLocalUri,
            cleanImageSha256 = input.cleanImageSha256,
            cleanImageByteSize = input.cleanImageByteSize,
            layout = input.layout,
            userNote = input.userNote,
        )

    private var renderCounter = 0

    private fun renderDocx(input: MistakePdfExportInput, name: String): File {
        renderCounter += 1
        val file = File(temporaryFolder.root, "$renderCounter-$name")
        DocxMistakeRenderer.render(input, file)
        return file
    }

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

    private fun documentTexts(file: File): List<String> {
        val documentXml = readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(documentXml.toByteArray(Charsets.UTF_8)))
        val nodes = document.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
            "t",
        )
        return (0 until nodes.length).map { index -> nodes.item(index).textContent }
    }

    /** 结构完整的 PNG（签名 + IHDR + IEND）；与 `MistakeExportVisualRoleTest` 的最小夹具同构。 */
    private fun png(width: Int, height: Int): ByteArray {
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
}
