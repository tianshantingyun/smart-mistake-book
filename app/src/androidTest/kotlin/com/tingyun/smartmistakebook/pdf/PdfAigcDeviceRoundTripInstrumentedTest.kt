package com.tingyun.smartmistakebook.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcContentPolicy
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcErrorCode
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcInjectionException
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcInjector
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcMetadata
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcReader
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-75 的设备往返（L1，计划 §2 WS2 11–15；研究 R9
 * `docs/research/2026-11-03-instrumentation-pdf-verification.md`）。
 *
 * 证明范围：平台 PDFium 接受 `/AIGC` 增量段（页数/页尺寸/渲染不变）、自洽读回、二次注入幂等、
 * 畸形输入 fail-closed。**不是独立读者**：`PdfAigcReader` 与注入器共用解析器；独立读者（pypdf）
 * 读 `filesDir/pdf-aigc-device/` 目录下的 `.pdf` 文件，不在本类断言。也不依赖真实导出链（`core/export` 是热区，
 * 接缝另出 patch 草案）。
 *
 * 单独筛选（flavor 不同，测试包名不同）：
 * ```
 * adb shell am instrument -w -r \
 *   -e class com.tingyun.smartmistakebook.pdf.PdfAigcDeviceRoundTripInstrumentedTest#classicXrefTablePdfSurvivesInjectionAndOpensInPlatformRenderer \
 *   com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 * strictOffline 用 `com.tingyun.smartmistakebook.offline.test`。畸形四例按方法分次
 * `am instrument`（每次新进程；R9 §6.4）：若 PDFium 对某类输入原生崩溃，只损失该类，且必须
 * 记“未判定”，不得改写成 fail-closed 通过。
 *
 * 流型夹具按 R9 §7.2 注在设备侧做最小镜像，不复制 JVM 夹具文件、不加二进制资产：避免双源
 * 漂移，也避开压缩 asset 不能直接交给 `PdfRenderer` 的坑（先落文件再打开）。
 *
 * 证据：**被测应用**的 `filesDir/pdf-aigc-device/run-<stamp>/`（`run-as <app-pkg> cat
 * files/pdf-aigc-device/run-<stamp>/...`；`<stamp>` 取 logcat 的 `evidence=` 行）。用
 * `targetContext` 而不是 `instrumentation.context`：instrumentation 进程跑在被测应用 uid 下，
 * 测试包自己的 filesDir 属另一 uid，设备上写入会 EACCES。每次
 * instrumentation 进程用新目录，避免上一轮残留被当成当前证据。
 * 设备绿必须同时绑定源快照 `head` 与测试/被测 APK 的 sha256（计划 §4；禁止
 * `:app:connectedLocalFirstDebugAndroidTest`），本类只产出 PDF 与日志，不自行判定“通过”。
 * Skia（`PdfDocument`）产物的 Info/xref 形态在 R9 §10.2 仍是 UNVERIFIED，本类用 logcat 记录
 * 实际分支（`sourceXref` / `infoKeys`），避免“注入成功”只覆盖一种形态。
 */
@RunWith(AndroidJUnit4::class)
class PdfAigcDeviceRoundTripInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val injector = PdfAigcInjector()
    private val metadata = PdfAigcMetadata.document(PRODUCE_ID)

    private val evidenceDirectory: File by lazy {
        val directory = File(
            // instrumentation 进程运行在**被测应用**的 uid 下；测试包自己的 filesDir（uid 不同）
            // 在设备上不可写（实测 EACCES → 仅 4 例会因证据目录失败而红，断言从未执行）。
            File(instrumentation.targetContext.filesDir, "pdf-aigc-device"),
            "run-$RUN_STAMP",
        )
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
            "cannot create evidence directory $directory"
        }
        directory
    }

    // ---------------------------------------------------------------- a) 真实 PdfDocument 产物

    @Test
    fun pdfDocumentChineseTwoPagePdfSurvivesInjectionAndOpensInPlatformRenderer() {
        val source = pdfDocumentBytes()
        Log.i(
            TAG,
            "case=pdfdocument sourceBytes=${source.size} " +
                "sourceXref=${describeXrefShape(source)} " +
                "infoKeys=${PdfAigcReader.readInfoStrings(source).keys}",
        )

        // 先证明 source 本身在设备上可打开：失败时区分“夹具坏”与“注入段被拒”（R9 §8 #14）。
        assertOpensWithExpectedGeometry(
            writeEvidence("pdfdocument-chinese-source", source),
            expectedPages = 2,
            expectedWidth = 595,
            expectedHeight = 842,
        )

        val labeled = injector.inject(source, metadata)
        assertArrayEquals(
            "the incremental update must append and keep the original bytes as a prefix",
            source,
            labeled.copyOfRange(0, source.size),
        )

        val evidence = writeEvidence("pdfdocument-chinese-labeled", labeled)
        assertOpensWithExpectedGeometry(evidence, expectedPages = 2, expectedWidth = 595, expectedHeight = 842)

        val readBack = PdfAigcReader.readLatestAigc(evidence.readBytes())
        assertNotNull("the /AIGC Info value must be readable from the published file", readBack)
        val aigc = readBack!!
        assertEquals(PRODUCE_ID, aigc.produceId)
        assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, aigc.label)
        assertTrue("ContentProducer must survive read-back", aigc.contentProducer.isNotBlank())
    }

    // ---------------------------------------------------------------- b) 表型 / 流型 xref

    @Test
    fun classicXrefTablePdfSurvivesInjectionAndOpensInPlatformRenderer() {
        val source = classicXrefTablePdf()
        assertOpensWithExpectedGeometry(writeEvidence("classic-table-source", source), expectedPages = 2)
        val labeled = injector.inject(source, metadata)

        assertArrayEquals(source, labeled.copyOfRange(0, source.size))
        val evidence = writeEvidence("classic-table-labeled", labeled)
        assertOpensWithExpectedGeometry(evidence, expectedPages = 2)
        val published = evidence.readBytes()
        assertEquals(PRODUCE_ID, requireNotNull(PdfAigcReader.readLatestAigc(published)).produceId)
        assertEquals(ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(published)["Title"])
        assertEquals(ORIGINAL_AUTHOR, PdfAigcReader.readInfoStrings(published)["Author"])
    }

    @Test
    fun xrefStreamPdfSurvivesInjectionAndOpensInPlatformRenderer() {
        val source = xrefStreamPdf()
        assertOpensWithExpectedGeometry(writeEvidence("xref-stream-source", source), expectedPages = 2)
        val labeled = injector.inject(source, metadata)

        assertArrayEquals(source, labeled.copyOfRange(0, source.size))
        val evidence = writeEvidence("xref-stream-labeled", labeled)
        assertOpensWithExpectedGeometry(evidence, expectedPages = 2)
        val published = evidence.readBytes()
        assertEquals(PRODUCE_ID, requireNotNull(PdfAigcReader.readLatestAigc(published)).produceId)
        assertEquals(ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(published)["Title"])
    }

    // ---------------------------------------------------------------- c) 二次注入幂等

    @Test
    fun secondInjectionWithSameMetadataKeepsOneInfoAndBytePrefix() {
        val source = classicXrefTablePdf()
        val first = injector.inject(source, metadata)
        val second = injector.inject(first, metadata)

        assertArrayEquals("re-labelling with the same metadata must be byte-idempotent", first, second)
        assertArrayEquals("the original bytes must stay a prefix", source, second.copyOfRange(0, source.size))
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(second))

        val firstReadBack = requireNotNull(PdfAigcReader.readLatestAigc(first))
        val secondReadBack = requireNotNull(PdfAigcReader.readLatestAigc(second))
        assertEquals(
            "a second injection must reuse the same Info object, not add another",
            firstReadBack.infoObjectNumber,
            secondReadBack.infoObjectNumber,
        )
        assertEquals(PRODUCE_ID, secondReadBack.produceId)
        assertEquals(ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(second)["Title"])
        writeEvidence("classic-table-twice", second)
    }

    // ---------------------------------------------------------------- d) 畸形四类 fail-closed

    @Test
    fun malformedHeaderIsRejectedWithTypedErrorAndNoOutput() {
        assertRejectedWithoutOutput(PdfAigcErrorCode.NOT_PDF_HEADER, "not-a-pdf".toByteArray(Charsets.US_ASCII))
    }

    @Test
    fun missingStartXrefIsRejectedWithTypedErrorAndNoOutput() {
        assertRejectedWithoutOutput(
            PdfAigcErrorCode.STARTXREF_MISSING,
            replaceAscii(classicXrefTablePdf(), "startxref", "startxrefX"),
        )
    }

    @Test
    fun malformedXrefTableIsRejectedWithTypedErrorAndNoOutput() {
        assertRejectedWithoutOutput(
            PdfAigcErrorCode.XREF_TABLE_MALFORMED,
            classicXrefTablePdf(corruptEntryObject = 7),
        )
    }

    @Test
    fun encryptedPdfIsRejectedWithTypedErrorAndNoOutput() {
        assertRejectedWithoutOutput(
            PdfAigcErrorCode.ENCRYPTED,
            classicXrefTablePdf(trailerExtra = " /Encrypt 9 0 R"),
        )
    }

    // ---------------------------------------------------------------- e) 内容政策

    @Test
    fun modelParsedTextForcesAigcLabelling() {
        val provenanceNames = listOf(
            QuestionBlockProvenance.USER_TRANSCRIPTION.name,
            QuestionBlockProvenance.MODEL_DOCUMENT_PARSE.name,
        )
        assertTrue(PdfAigcContentPolicy.hasModelParsedText(provenanceNames))
        val shouldLabel = PdfAigcContentPolicy.shouldLabel(
            hasAiGeneratedImage = false,
            hasModelParsedText = PdfAigcContentPolicy.hasModelParsedText(provenanceNames),
        )
        assertTrue("model-parsed text alone must force /AIGC", shouldLabel)

        val source = classicXrefTablePdf()
        val labeled = if (shouldLabel) injector.inject(source, metadata) else source
        val readBack = PdfAigcReader.readLatestAigc(labeled)
        assertNotNull(readBack)
        assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, readBack!!.label)
    }

    @Test
    fun userOnlyContentStaysUnlabelled() {
        val provenanceNames = listOf(
            QuestionBlockProvenance.USER_TRANSCRIPTION.name,
            QuestionBlockProvenance.USER_CORRECTION.name,
        )
        assertFalse(PdfAigcContentPolicy.hasModelParsedText(provenanceNames))
        assertFalse(PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage = false, hasModelParsedText = false))
        assertNull(
            "a pure user document must not carry /AIGC",
            PdfAigcReader.readLatestAigc(classicXrefTablePdf()),
        )
    }

    // ---------------------------------------------------------------- 断言工具

    /**
     * 打开注入后的字节并断言页数/页尺寸/非白像素。页尺寸用整型点值（R9 §0.6、§7.4）；
     * 非白像素只断“存在墨迹”，不断精确像素值（PDFium 抗锯齿随模块版本漂移）。
     */
    private fun assertOpensWithExpectedGeometry(
        file: File,
        expectedPages: Int,
        expectedWidth: Int? = null,
        expectedHeight: Int? = null,
    ) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(
                    "the incremental /AIGC update must not change the page count",
                    expectedPages,
                    renderer.pageCount,
                )
                for (index in 0 until expectedPages) {
                    renderer.openPage(index).use { page ->
                        if (expectedWidth != null) {
                            assertEquals("page $index width (points)", expectedWidth, page.width)
                        }
                        if (expectedHeight != null) {
                            assertEquals("page $index height (points)", expectedHeight, page.height)
                        }
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(page.width * page.height)
                            bitmap.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
                            assertTrue(
                                "page $index must render non-blank ink after injection",
                                pixels.any { it != Color.WHITE },
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    /**
     * fail-closed 断言：注入必须抛 typed error；只有注入成功才会写目标文件，因此“文件不存在”
     * 同时证明没有发布半成品。不绑定异常 message（R9 §7.4）。
     */
    private fun assertRejectedWithoutOutput(expected: PdfAigcErrorCode, source: ByteArray) {
        val target = File(evidenceDirectory, "malformed-${System.nanoTime()}.pdf")
        val failure = assertThrows(PdfAigcInjectionException::class.java) {
            val labeled = injector.inject(source, metadata)
            target.writeBytes(labeled)
        }
        assertEquals(expected, failure.code)
        assertFalse("a rejected input must not publish a file", target.exists())
    }

    private fun writeEvidence(name: String, bytes: ByteArray): File {
        val file = File(evidenceDirectory, "$name.pdf")
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.flush()
        }
        Log.i(TAG, "evidence=${file.absolutePath}")
        return file
    }

    // ---------------------------------------------------------------- 夹具（代码内生成，不加二进制资产）

    /** 真 `PdfDocument` 产物：2 页 A4，含中文文本（R9 §3.4 的生成骨架）。 */
    private fun pdfDocumentBytes(): ByteArray {
        val document = PdfDocument()
        var failure: Throwable? = null
        try {
            return ByteArrayOutputStream().use { output ->
                repeat(2) { index ->
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(595, 842, index + 1).create(),
                    )
                    try {
                        page.canvas.drawColor(Color.WHITE)
                        // 黑块保证“非白”判据不依赖 CJK 字体回退；中文文本仍按需求画进页面。
                        page.canvas.drawRect(
                            40f,
                            40f,
                            120f,
                            60f,
                            Paint().apply { color = Color.BLACK },
                        )
                        page.canvas.drawText(
                            "错题本 中文测试 AIGC-${index + 1}",
                            48f,
                            96f,
                            Paint().apply {
                                color = Color.BLACK
                                textSize = 28f
                                isAntiAlias = true
                            },
                        )
                    } finally {
                        document.finishPage(page)
                    }
                }
                document.writeTo(output)
                output.toByteArray()
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            // R9 §3.1：writeTo 失败时再 close 会掩盖原始异常，先记 failure 再决定是否吞 close 错。
            if (failure == null) {
                document.close()
            } else {
                runCatching { document.close() }
            }
        }
    }

    /**
     * 经典 xref 表 + Info（2 页，内容流画黑方块，保证渲染有墨）。
     *
     * 消灭的具体失败：Skia 产物的 xref 形态在设备首跑前未知（R9 §3.3），若只依赖它，表型
     * 分支可能一次都没被设备覆盖。这里给出确定性的表型输入。
     */
    private fun classicXrefTablePdf(
        corruptEntryObject: Int? = null,
        trailerExtra: String = "",
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeAscii("%PDF-1.4\n")
        val offsets = IntArray(8)
        fun objectBody(number: Int, body: String) {
            offsets[number] = out.size()
            out.writeAscii("$number 0 obj\n")
            out.writeAscii(body)
            out.writeAscii("\nendobj\n")
        }

        val pageContent = "0 0 0 rg 10 10 50 50 re f"
        objectBody(1, "<< /Type /Catalog /Pages 2 0 R >>")
        objectBody(2, "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>")
        objectBody(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << >> /Contents 4 0 R >>")
        objectBody(4, "<< /Length ${pageContent.length} >>\nstream\n$pageContent\nendstream")
        objectBody(5, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << >> /Contents 6 0 R >>")
        objectBody(6, "<< /Length ${pageContent.length} >>\nstream\n$pageContent\nendstream")
        objectBody(7, "<< /Title ($ORIGINAL_TITLE) /Author ($ORIGINAL_AUTHOR) /Producer (fixture) >>")

        val xrefOffset = out.size()
        out.writeAscii("xref\n0 8\n")
        out.writeAscii(xrefEntry(0, 65_535, 'f', corrupt = false))
        for (number in 1..7) {
            out.writeAscii(
                xrefEntry(offsets[number], 0, 'n', corrupt = number == corruptEntryObject),
            )
        }
        out.writeAscii(
            "trailer\n<< /Size 8 /Root 1 0 R /Info 7 0 R " +
                "/ID [<$HEX_ID> <$HEX_ID>]",
        )
        if (trailerExtra.isNotEmpty()) out.writeAscii(trailerExtra)
        out.writeAscii(" >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    /**
     * PDF 1.5 xref 流（`/W [1 4 2]`、`/Index [0 9]`），镜像 JVM 夹具的最小设备版。
     *
     * 消灭的具体失败：设备侧没有流型输入构造器，`previousIsStream` 分支永远无法在设备上
     * 被证明（R9 §7.2 注）。按最小镜像写在代码里，不复制 JVM 夹具文件、不加二进制资产。
     */
    private fun xrefStreamPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeAscii("%PDF-1.5\n")
        val offsets = IntArray(9)
        fun objectBody(number: Int, body: String) {
            offsets[number] = out.size()
            out.writeAscii("$number 0 obj\n")
            out.writeAscii(body)
            out.writeAscii("\nendobj\n")
        }

        val pageContent = "0 0 0 rg 10 10 50 50 re f"
        objectBody(1, "<< /Type /Catalog /Pages 2 0 R >>")
        objectBody(2, "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>")
        objectBody(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << >> /Contents 4 0 R >>")
        objectBody(4, "<< /Length ${pageContent.length} >>\nstream\n$pageContent\nendstream")
        objectBody(5, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << >> /Contents 6 0 R >>")
        objectBody(6, "<< /Length ${pageContent.length} >>\nstream\n$pageContent\nendstream")
        objectBody(7, "<< /Title ($ORIGINAL_TITLE) /Author ($ORIGINAL_AUTHOR) /Producer (fixture) >>")

        val xrefStreamOffset = out.size()
        val rowWidth = 7
        val rows = ByteArray(9 * rowWidth)
        fun row(index: Int, type: Int, field2: Long, field3: Long) {
            val base = index * rowWidth
            rows[base] = type.toByte()
            rows[base + 1] = ((field2 ushr 24) and 0xFF).toByte()
            rows[base + 2] = ((field2 ushr 16) and 0xFF).toByte()
            rows[base + 3] = ((field2 ushr 8) and 0xFF).toByte()
            rows[base + 4] = (field2 and 0xFF).toByte()
            rows[base + 5] = ((field3 ushr 8) and 0xFF).toByte()
            rows[base + 6] = (field3 and 0xFF).toByte()
        }
        row(0, 0, 0L, 65_535L)
        for (number in 1..7) row(number, 1, offsets[number].toLong(), 0L)
        row(8, 1, xrefStreamOffset.toLong(), 0L)

        out.writeAscii(
            "8 0 obj\n<< /Type /XRef /Size 9 /Index [0 9] /W [1 4 2] " +
                "/Length ${rows.size} /Root 1 0 R /Info 7 0 R >>\nstream\n",
        )
        out.write(rows)
        out.writeAscii("\nendstream\nendobj\nstartxref\n$xrefStreamOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun xrefEntry(offset: Int, generation: Int, type: Char, corrupt: Boolean): String {
        val digits = String.format(Locale.ROOT, "%010d", offset)
        val rendered = if (corrupt) "X" + digits.substring(1) else digits
        return String.format(Locale.ROOT, "%s %05d %c \n", rendered, generation, type)
    }

    private fun replaceAscii(bytes: ByteArray, old: String, new: String): ByteArray {
        val text = bytes.toString(Charsets.ISO_8859_1)
        check(text.contains(old)) { "fixture does not contain '$old'" }
        return text.replace(old, new).toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * 诊断用：报告 Skia 产物走的是经典表还是 xref 流（R9 §3.3/§10.2 #1）。只记日志，不断言——
     * Skia 的实际形态在设备首跑前是 UNVERIFIED，断言它会变成猜测。
     */
    private fun describeXrefShape(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.ISO_8859_1)
        val keyword = text.lastIndexOf("startxref")
        if (keyword < 0) return "no-startxref"
        var position = keyword + "startxref".length
        while (position < text.length && text[position].isWhitespace()) position++
        val digitsStart = position
        while (position < text.length && text[position].isDigit()) position++
        val offset = text.substring(digitsStart, position).toIntOrNull() ?: return "startxref-not-integer"
        if (offset !in bytes.indices) return "startxref-out-of-range"
        return if (text.startsWith("xref", offset)) "classic-table" else "xref-stream-or-object-header"
    }

    private fun ByteArrayOutputStream.writeAscii(text: String) {
        write(text.toByteArray(Charsets.US_ASCII))
    }

    private companion object {
        const val TAG = "PdfAigcDeviceRoundTrip"
        const val PRODUCE_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val ORIGINAL_TITLE = "Original Title"
        const val ORIGINAL_AUTHOR = "Original Author"
        const val HEX_ID = "00112233445566778899aabbccddeeff"

        /** 同一 instrumentation 进程的所有用例共享；新进程 = 新证据目录。 */
        val RUN_STAMP = "${System.currentTimeMillis()}-${Process.myPid()}"
    }
}
