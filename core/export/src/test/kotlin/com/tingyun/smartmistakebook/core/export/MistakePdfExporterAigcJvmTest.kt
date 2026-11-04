package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcContentPolicy
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcMetadata
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS2 的 JVM 侧（计划 items 15–16 的可执行部分）。
 *
 * 覆盖：
 * 1. 打标判据与渲染器取图集合一致（AI 图/纯用户内容/讲解图块/无图陷阱）；
 * 2. 真实 `labelPdfInPlace`（生产路径调用的同一函数）在真实 PDF 字节上注入并可读回；
 * 3. fail-closed：注入器拒绝的输入抛 `GENERATED_PDF_INVALID`，且文件一个字节都不改。
 *
 * **不覆盖**：`MistakePdfExporter.prepare` 全链与 `PdfRenderer` 打开——`android.graphics.pdf`
 * 在 JVM 单测不可用（R1 §8 末），该层由 `MistakePdfExporterAigcInstrumentedTest` 在设备上覆盖。
 */
class MistakePdfExporterAigcJvmTest {

    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    // ---------------------------------------------------------------- 判据矩阵

    @Test
    fun `question clean image requires a label`() {
        assertTrue(
            input(
                cleanImageLocalUri = "file:///clean.png",
                questionImageRole = VisualRole.CLEAN_IMAGE.name,
            ).paintsAiGeneratedImage(),
        )
    }

    @Test
    fun `question diagram alone stays unlabeled`() {
        assertFalse(
            input(
                cleanImageLocalUri = "file:///diagram.png",
                questionImageRole = VisualRole.QUESTION_DIAGRAM.name,
            ).paintsAiGeneratedImage(),
        )
    }

    @Test
    fun `missing image stays unlabeled even though a null role means AI`() {
        assertFalse(
            "no image URI must win over isAiGeneratedExportImage(null) == true",
            input(questionImageRole = null).paintsAiGeneratedImage(),
        )
    }

    @Test
    fun `solution images count only when the solution section is requested`() {
        val solution = listOf(
            MistakePdfExportImage(
                role = VisualRole.TUTOR_EXPLANATION.name,
                localUri = "file:///solution.png",
            ),
        )
        assertTrue(
            input(
                solutionImages = solution,
                layout = MistakePdfLayout(includeSolution = true),
            ).paintsAiGeneratedImage(),
        )
        assertFalse(input(solutionImages = solution).paintsAiGeneratedImage())
    }

    @Test
    fun `drawable block images follow the renderer role filter`() {
        assertTrue(blockImage(role = null).paintsAiGeneratedImage())
        assertTrue(blockImage(role = VisualRole.CLEAN_IMAGE.name).paintsAiGeneratedImage())
        assertFalse(blockImage(role = VisualRole.QUESTION_DIAGRAM.name).paintsAiGeneratedImage())
        assertFalse(
            "TUTOR_EXPLANATION blocks are never drawn in the question area",
            blockImage(role = VisualRole.TUTOR_EXPLANATION.name).paintsAiGeneratedImage(),
        )
    }

    @Test
    fun `pure user text stays unlabeled`() {
        assertFalse(input().paintsAiGeneratedImage())
    }

    @Test
    fun `policy generation is consistent across sentinel fingerprint and policy`() {
        // 三方必须同时升版：改判据只升一处，旧的无标包就能在 TTL 内被复用（KD-75 reopen condition）。
        assertEquals(2, PdfAigcContentPolicy.GENERATION)
        assertEquals(".aigc-policy-gen-2", MistakePdfExporter.AIGC_POLICY_SENTINEL)
        assertEquals("mistake-export-v4-aigc-gen-2", exportFingerprintPolicyTag())
    }

    // ---------------------------------------------------------------- labelPdfInPlace

    @Test
    fun `labelPdfInPlace injects the document label and keeps the original prefix`() {
        val original = minimalPdf()
        val file = temporaryFile(original)
        val input = input(
            cleanImageLocalUri = "file:///clean.png",
            questionImageRole = VisualRole.CLEAN_IMAGE.name,
        )

        labelPdfInPlace(file, input)

        val labeled = file.readBytes()
        assertArrayEquals(
            "the incremental update must append and keep the rendered bytes as a prefix",
            original,
            labeled.copyOfRange(0, original.size),
        )
        val readBackValue = PdfAigcReader.readLatestAigc(labeled)
        assertNotNull(readBackValue)
        val readBack = readBackValue!!
        assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, readBack.label)
        assertEquals(input.inputSha256, readBack.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(labeled))
    }

    @Test
    fun `labelPdfInPlace leaves pure user content byte identical`() {
        val original = minimalPdf()
        val file = temporaryFile(original)

        labelPdfInPlace(file, input())

        assertArrayEquals(original, file.readBytes())
        assertEquals(null, PdfAigcReader.readLatestAigc(file.readBytes()))
    }

    /**
     * KD-75 C2 的正向：**没有任何图**、只有模型解析文本的输入也必须打标，
     * 且与 AI 图走同一个 `labelPdfInPlace` 注入点（同一份元数据、同一次增量更新）。
     */
    @Test
    fun `labelPdfInPlace labels model parsed text without any image`() {
        val original = minimalPdf()
        val file = temporaryFile(original)
        val input = input(hasModelParsedText = true)

        assertFalse("the fixture must not carry any AI image", input.paintsAiGeneratedImage())
        labelPdfInPlace(file, input)

        val labeled = file.readBytes()
        assertArrayEquals(
            "model text must use the same append-only update as AI images",
            original,
            labeled.copyOfRange(0, original.size),
        )
        val readBackValue = PdfAigcReader.readLatestAigc(labeled)
        assertNotNull("model parsed text alone must carry /AIGC", readBackValue)
        val readBack = readBackValue!!
        assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, readBack.label)
        assertEquals(input.inputSha256, readBack.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(labeled))
    }

    @Test
    fun `labelPdfInPlace fails closed without touching the file when injection is rejected`() {
        val original = "not a pdf".toByteArray(Charsets.US_ASCII)
        val file = temporaryFile(original)

        val failure = assertThrows(MistakePdfExportException::class.java) {
            labelPdfInPlace(
                file,
                input(
                    cleanImageLocalUri = "file:///clean.png",
                    questionImageRole = VisualRole.CLEAN_IMAGE.name,
                ),
            )
        }

        assertEquals(MistakePdfExportFailure.GENERATED_PDF_INVALID, failure.failure)
        assertArrayEquals("a rejected label must not leave a half-written file", original, file.readBytes())
    }

    // ---------------------------------------------------------------- 夹具

    private fun input(
        inputSha256: String = sha,
        blocks: List<MistakePdfBlock> = listOf(MistakePdfBlock.Paragraph("p", "纯用户内容")),
        cleanImageLocalUri: String? = null,
        questionImageRole: String? = null,
        solutionImages: List<MistakePdfExportImage> = emptyList(),
        hasModelParsedText: Boolean = false,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry",
        problemId = "problem",
        problemRevisionId = "revision",
        revisionNumber = 1,
        title = "title",
        subject = "数学",
        subtitle = "数学 · 第 1 版",
        documentTitle = null,
        blocks = blocks,
        questionDocumentSha256 = sha,
        hasModelParsedText = hasModelParsedText,
        inputSha256 = inputSha256,
        cleanImageLocalUri = cleanImageLocalUri,
        questionImageRole = questionImageRole,
        solutionImages = solutionImages,
        layout = layout,
    )

    private fun blockImage(role: String?): MistakePdfExportInput = input(
        blocks = listOf(
            MistakePdfBlock.Image(id = "image", localUri = "file:///block.png", title = "题图", role = role),
        ),
    )

    private fun temporaryFile(bytes: ByteArray): File =
        File.createTempFile("mistake-pdf-aigc-", ".pdf").apply {
            writeBytes(bytes)
            deleteOnExit()
        }

    /** 手写最小但结构完整的 PDF（1 页 + Info），只服务注入器解析，不涉及渲染。 */
    private fun minimalPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = IntArray(6)
        fun objectBody(number: Int, body: String) {
            offsets[number] = out.size()
            out.writeAscii("$number 0 obj\n$body\nendobj\n")
        }
        out.writeAscii("%PDF-1.4\n")
        objectBody(1, "<< /Type /Catalog /Pages 2 0 R >>")
        objectBody(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        objectBody(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>")
        objectBody(4, "<< /Length 0 >>\nstream\n\nendstream")
        objectBody(5, "<< /Title (User Content) /Producer (fixture) >>")
        val xrefOffset = out.size()
        out.writeAscii("xref\n0 6\n")
        out.writeAscii(String.format(Locale.ROOT, "%010d %05d f \n", 0, 65_535))
        for (number in 1..5) {
            out.writeAscii(String.format(Locale.ROOT, "%010d %05d n \n", offsets[number], 0))
        }
        out.writeAscii("trailer\n<< /Size 6 /Root 1 0 R /Info 5 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(text: String) {
        write(text.toByteArray(Charsets.US_ASCII))
    }
}
