package com.tingyun.smartmistakebook.core.data.visual

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * PDF 写成功判定纯层的 JVM 门（批次 30 T5；R2 `docs/research/2026-10-29-pdf-write-safety.md`
 * §3.1/§3.2/§5/§6）。
 *
 * **不变量**：不触任何 android 符号（被测文件 `PdfWriteIntegrity.kt` 零 android 导入）。
 * `PdfDocument.writeTo` 的 native 吞异常行为、真实 `PdfRenderer` 回读与 `fd.sync` 归设备轨
 * `VisualDrawOpsPdfPainterInstrumentedTest`；本文件只钉**判定语义**本身。
 *
 * 覆盖映射（R2 §6 的 ID）：
 * - N-PDF-02（流中途抛）：首失败记录 + 重抛 + 接受字节只计成功块；
 * - N-PDF-09（`Error` 型抛）：`Throwable` 捕获面（原 `catch (Exception)` 的已知缺口）；
 * - N-PDF-03（零字节成功）：`acceptedBytes > 0` 判据；
 * - N-PDF-04（尾部静默截断）：尾窗 `%%EOF` 判据 + 文件长度 == 接受字节；
 * - 短写：文件层"长度 != 接受字节"（纯流目标不可观测，见 `PdfWriteIntegrity.kt` 文件头）。
 *
 * **不覆盖**：中段静默丢块（R2 §3.5 已知残留，三条判据都看不见，不得在此造假绿用例）。
 */
class PdfWriteIntegrityTest {

    private val createdFiles = ArrayList<File>()

    @After
    fun cleanUp() {
        createdFiles.forEach { it.delete() }
        createdFiles.clear()
    }

    // ------------------------------------------------------------ D1：首失败记录与重抛

    @Test
    fun `the tracking stream records the first failure and rethrows every attempt`() {
        // 首块放行、其后每块抛不同异常：记录必须停在**第一个**（N-PDF-02 的"首失败"语义）。
        val first = IOException("first write failure")
        val later = IOException("later write failure")
        val delegate = ScriptedStream(listOf(null, first, later))
        val sink = PdfWriteFailureTrackingStream(delegate)

        sink.write(ByteArray(8), 0, 8)
        assertEquals(8L, sink.telemetry.acceptedBytes)
        assertNull(sink.firstFailure)

        val firstThrown = runCatching { sink.write(ByteArray(8), 0, 8) }.exceptionOrNull()
        assertSame(first, firstThrown)
        assertSame(first, sink.firstFailure)
        // 失败块不计入接受字节（只统计委托流正常返回的块）。
        assertEquals(8L, sink.telemetry.acceptedBytes)

        val secondThrown = runCatching { sink.write(ByteArray(8), 0, 8) }.exceptionOrNull()
        assertSame(later, secondThrown)
        // 后续失败不得覆盖首失败。
        assertSame(first, sink.firstFailure)
    }

    @Test
    fun `an Error typed write failure is recorded too`() {
        // N-PDF-09：native 的 ExceptionCheck 对 Error 同样 true 并 ExceptionClear，
        // 只 catch (Exception) 会让 OOM 型写失败静默成功。记录面必须是 Throwable。
        val oom = OutOfMemoryError("simulated oom in write")
        val sink = PdfWriteFailureTrackingStream(ScriptedStream(listOf(oom)))

        val thrown = runCatching { sink.write(ByteArray(4), 0, 4) }.exceptionOrNull()
        assertSame(oom, thrown)
        assertSame(oom, sink.firstFailure)
        val reason = pdfWriteFailureReason(sink)
        assertTrue("reason must carry the OOM message, got $reason", reason!!.contains("simulated oom"))
    }

    @Test
    fun `the first failure text falls back to the class name when there is no message`() {
        val sink = PdfWriteFailureTrackingStream(ScriptedStream(listOf(IOException())))
        runCatching { sink.write(ByteArray(1), 0, 1) }
        assertEquals("pdf write failed: IOException", pdfWriteFailureReason(sink))
    }

    @Test
    fun `a disk full style failure keeps its errno text`() {
        // 计划 §1.6 的"磁盘满模拟"（N-PDF-05）：真 ENOSPC 需要填满 /data（不可行，登记 UNVERIFIED），
        // 这里钉的是"IOException 的 errno 文本原样进入有码失败消息"，而不是调用方收到裸异常。
        val sink = PdfWriteFailureTrackingStream(
            ScriptedStream(listOf(IOException("ENOSPC (No space left on device)"))),
        )
        runCatching { sink.write(ByteArray(4), 0, 4) }
        assertEquals(
            "pdf write failed: ENOSPC (No space left on device)",
            pdfWriteFailureReason(sink),
        )
    }

    // ------------------------------------------------------------ D2：字节数与尾标

    @Test
    fun `a stream that produced no bytes fails closed`() {
        // 对应 native 在调用 Java write 之前就失败（一个字节都没交出来）而 writeTo 正常返回。
        val sink = PdfWriteFailureTrackingStream(ByteArrayOutputStream())
        assertEquals("pdf write produced no bytes", pdfWriteFailureReason(sink))
    }

    @Test
    fun `a payload without the trailer fails closed`() {
        // N-PDF-04：尾部静默截断 → %%EOF 没写出，但委托流不抛（这里用不抛的 ByteArrayOutputStream）。
        val sink = PdfWriteFailureTrackingStream(ByteArrayOutputStream())
        val payload = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".toByteArray(StandardCharsets.ISO_8859_1)
        sink.write(payload, 0, payload.size)
        val reason = pdfWriteFailureReason(sink)
        assertTrue("expected trailer complaint, got $reason", reason!!.contains(PDF_TRAILER_MARKER))
    }

    @Test
    fun `a payload ending with the trailer passes`() {
        val sink = PdfWriteFailureTrackingStream(ByteArrayOutputStream())
        val payload = "%PDF-1.7\nstartxref\n0\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
        sink.write(payload, 0, payload.size)
        assertNull(pdfWriteFailureReason(sink))
    }

    @Test
    fun `the trailer is matched across chunk boundaries`() {
        // native 以 4096 B 分块写；尾标可能被切成两块，尾窗必须按字节流拼接判断。
        val sink = PdfWriteFailureTrackingStream(ByteArrayOutputStream())
        val head = "%PDF-1.7\nstartxref\n0\n%%".toByteArray(StandardCharsets.ISO_8859_1)
        val rest = "EOF".toByteArray(StandardCharsets.ISO_8859_1)
        sink.write(head, 0, head.size)
        sink.write(rest, 0, rest.size)
        assertNull(pdfWriteFailureReason(sink))
    }

    @Test
    fun `a trailer that has scrolled out of the tail window fails closed`() {
        // 尾窗只保留最后 1024 B：先写尾标再写 2 KB 噪声 → 尾标不在窗内（尾部整段丢失的形态）。
        val sink = PdfWriteFailureTrackingStream(ByteArrayOutputStream())
        val early = "%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
        sink.write(early, 0, early.size)
        val noise = ByteArray(2048) { 'x'.code.toByte() }
        sink.write(noise, 0, noise.size)
        val reason = pdfWriteFailureReason(sink)
        assertTrue("expected trailer complaint, got $reason", reason!!.contains(PDF_TRAILER_MARKER))
    }

    @Test
    fun `a single byte trailer split into per byte writes is still matched`() {
        // write(int) 不是 native 的路径（R2 §2.4），但包装层必须同语义（惰性防御）。
        val sink = PdfWriteFailureTrackingStream(ByteArrayOutputStream())
        "%%EOF".forEach { sink.write(it.code) }
        assertEquals(5L, sink.telemetry.acceptedBytes)
        assertNull(pdfWriteFailureReason(sink))
    }

    // ------------------------------------------------------------ D3 字节面：文件长度 == 接受字节

    @Test
    fun `a complete file passes the byte level readback`() {
        val bytes = "%PDF-1.7\nstartxref\n0\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
        val file = temporaryFile(bytes)
        assertNull(pdfFileFailureReason(file, bytes.size.toLong()))
    }

    @Test
    fun `a file shorter than the accepted bytes fails closed`() {
        // 短写：委托流正常返回了 N 字节，落盘只有 M < N（纯流目标不可观测，文件层可观测）。
        val bytes = "%PDF-1.7\nstartxref\n0\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
        val file = temporaryFile(bytes.copyOf(bytes.size - 8))
        val reason = pdfFileFailureReason(file, bytes.size.toLong())
        assertTrue(
            "expected a length mismatch, got $reason",
            reason!!.contains("does not match the ${bytes.size} accepted bytes"),
        )
    }

    @Test
    fun `a file larger than the accepted bytes fails closed`() {
        val bytes = "%PDF-1.7\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
        val file = temporaryFile(bytes + ByteArray(3) { 'z'.code.toByte() })
        val reason = pdfFileFailureReason(file, bytes.size.toLong())
        assertTrue("expected a length mismatch, got $reason", reason!!.contains("does not match"))
    }

    @Test
    fun `a file without the trailer fails closed even when the length matches`() {
        val bytes = "%PDF-1.7\nstartxref\n0\n".toByteArray(StandardCharsets.ISO_8859_1)
        val file = temporaryFile(bytes)
        val reason = pdfFileFailureReason(file, bytes.size.toLong())
        assertTrue("expected trailer complaint, got $reason", reason!!.contains(PDF_TRAILER_MARKER))
    }

    @Test
    fun `a missing file fails closed`() {
        val file = temporaryFile("%PDF-1.7\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1))
        assertTrue(file.delete())
        val reason = pdfFileFailureReason(file, 13L)
        assertTrue("expected a missing-file complaint, got $reason", reason!!.contains("is missing"))
    }

    @Test
    fun `a zero accepted byte count fails closed`() {
        val file = temporaryFile("%PDF-1.7\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1))
        assertEquals("pdf write produced no bytes", pdfFileFailureReason(file, 0L))
    }

    // ------------------------------------------------------------ 夹具

    private fun temporaryFile(bytes: ByteArray): File {
        val file = File.createTempFile("pdf-write-integrity-", ".pdf")
        createdFiles.add(file)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    /** 按脚本逐块返回失败（null = 放行）的委托流；越界后重复最后一项。 */
    private class ScriptedStream(
        private val script: List<Throwable?>,
    ) : OutputStream() {

        private var calls = 0

        override fun write(b: Int) = next()

        override fun write(b: ByteArray, off: Int, len: Int) = next()

        override fun flush() = next()

        override fun close() = next()

        private fun next() {
            val failure = script[calls.coerceAtMost(script.lastIndex)]
            calls += 1
            failure?.let { throw it }
        }
    }
}
