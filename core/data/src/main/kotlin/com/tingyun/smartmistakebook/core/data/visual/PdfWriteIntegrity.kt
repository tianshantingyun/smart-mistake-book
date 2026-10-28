package com.tingyun.smartmistakebook.core.data.visual

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * PDF 写成功判定的**纯计算层**（批次 30 T5；R2 `docs/research/2026-10-29-pdf-write-safety.md`
 * §3.1/§3.2/§5）。
 *
 * **文件级结构不变量：本文件零 android 导入。** 与 [VisualDrawOpsPdfPainterPure] 同一条分层
 * 理由：`core:data` 的 JVM 单测没有 Robolectric，而"写侧失败被记录/尾标缺失/字节数不符"
 * 这些判定必须能在 JVM 门里钉死；`PdfDocument` 的 native 吞异常行为与真实文件回读归设备轨。
 *
 * ## 判据分级（R2 §5；D0 已被设备否证，不得单独使用）
 *
 * - **D1** [PdfWriteFailureTrackingStream.firstFailure]：写期任何从 Java 逃出的失败都记录
 *   （含 `Error`——native 的 `ExceptionCheck` 对任何 `Throwable` 都返回 true 后
 *   `ExceptionClear`，只 `catch (Exception)` 会漏；见 R2 §3.1 吞掉面②）。
 * - **D2** 接受字节 `> 0` 且尾窗（[PDF_TAIL_WINDOW_BYTES]）内出现 `%%EOF`（[pdfWriteFailureReason]）。
 * - **D3**（文件目标，见 [VisualDrawOpsPdfPainter.writeFile]）：`flush + fd.sync` 成功、
 *   文件长度 == 接受字节（[pdfFileFailureReason]）、重开 `PdfRenderer` 页数相符、`renameTo` 为 true。
 *
 * ## 已知不覆盖面（R2 §3.5，不得读成已覆盖）
 *
 * **委托流静默短写**（不抛、少收字节）在纯流目标上**不可观测**：跟踪流只能看到"自己交出去
 * 的字节是否被委托流正常返回"，看不到委托流实际落盘多少。它只在文件目标层由
 * [pdfFileFailureReason] 的"文件长度 == 接受字节"抓到。**中段静默丢块**（尾标完整、页数正确）
 * 三层判据都看不见，归批次 29 T4 的原型栅格门（非产品判据）。
 */

/** PDF 尾标（SkPDF `serialize_footer` 的 `"\n%%EOF"`；R2 §3.2 只主张"尾窗内出现"）。 */
internal const val PDF_TRAILER_MARKER = "%%EOF"

/**
 * 尾窗字节数。取 1024 的理由：远大于 `%%EOF`（5 B），同时能容纳 SkPDF 尾块（native 每块
 * 4096 B 的最后一截）——判据只主张"尾部整段丢失"可抓，不主张"精确结尾相等"（R2 §3.2 前提）。
 */
internal const val PDF_TAIL_WINDOW_BYTES = 1024

/** 写侧失败的可读文本（此前是 `VisualDrawOpsPdfPainter.kt` 的私有 `Exception.describe()`）。 */
internal fun Throwable.pdfWriteFailureText(): String = message ?: javaClass.simpleName

/**
 * 写侧遥测：接受字节数 + 滚动尾窗**纯数据**（不含异常语义，便于 JVM 断言）。
 *
 * 只记录**委托流正常返回后**的字节；委托流抛异常时该块不计入（首失败已由跟踪流记录）。
 * 时间/空间 O(1)：一个 `Long` + 一个 [PDF_TAIL_WINDOW_BYTES] 缓冲；每块至多一次窗口拷贝。
 */
internal class PdfWriteTelemetry {

    /** 委托流正常接受的字节总数。 */
    var acceptedBytes: Long = 0L
        private set

    private val tail = ByteArray(PDF_TAIL_WINDOW_BYTES)
    private var tailLength = 0
    private val single = ByteArray(1)

    fun record(bytes: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        acceptedBytes += len.toLong()
        if (len >= PDF_TAIL_WINDOW_BYTES) {
            System.arraycopy(
                bytes,
                off + len - PDF_TAIL_WINDOW_BYTES,
                tail,
                0,
                PDF_TAIL_WINDOW_BYTES,
            )
            tailLength = PDF_TAIL_WINDOW_BYTES
            return
        }
        val overflow = tailLength + len - PDF_TAIL_WINDOW_BYTES
        if (overflow > 0) {
            // 同阵列前移拷贝；System.arraycopy 对重叠区间按 memmove 语义处理。
            System.arraycopy(tail, overflow, tail, 0, tailLength - overflow)
            tailLength -= overflow
        }
        System.arraycopy(bytes, off, tail, tailLength, len)
        tailLength += len
    }

    fun recordSingle(byte: Int) {
        single[0] = byte.toByte()
        record(single, 0, 1)
    }

    /** 尾窗内是否出现 `%%EOF`（不主张精确结尾；见 [PDF_TRAILER_MARKER]）。 */
    fun tailContainsTrailer(): Boolean {
        if (tailLength < PDF_TRAILER_MARKER.length) return false
        return String(tail, 0, tailLength, StandardCharsets.ISO_8859_1)
            .contains(PDF_TRAILER_MARKER)
    }
}

/**
 * 记录**在异常离开 Java 之前**的写侧失败（KD-79 的核心 seam；批次 29 落码，批次 30 从
 * `VisualDrawOpsPdfPainter.kt` 迁入本纯层并补 D2 遥测）。
 *
 * `PdfDocument.writeTo` 的 native 写适配器会 `ExceptionDescribe()` + `ExceptionClear()`
 * 掉 OutputStream 抛出的 Java 失败并返回 false（android-14.0.0_r1
 * `CreateJavaOutputStreamAdaptor.cpp` `SkJavaOutputStream::write`），因此调用方拿不到异常、
 * 也拿不到返回值（`writeTo` 是 void）。本包装在每个写入口先记录首个失败再**重抛**——重抛
 * 保证 native 仍按"这次写失败"处理，记录保证 `writeTo` 返回后能据此 fail-closed。
 *
 * 捕获面是 [Throwable] 而不是 `Exception`：native 的 `ExceptionCheck` 对 `Error` 同样返回
 * true 并清除（R2 §3.1 吞掉面②），`catch (Exception)` 会让 `OutOfMemoryError` 型写失败
 * 继续静默成功（已知缺口 N-PDF-09 的修复；只记录首失败，后续失败不覆盖）。
 *
 * 只记录、不吞：`close()`/`flush()` 也原样转发，行为与直接传 [delegate] 一致。
 */
internal class PdfWriteFailureTrackingStream(
    private val delegate: OutputStream,
) : OutputStream() {

    /** 写侧遥测（D2 的数据面）；每次 `write` 成功返回后才累加。 */
    val telemetry = PdfWriteTelemetry()

    /** 首个从 Java 侧逃出的写失败（含 `Error`）；无失败为 null。 */
    var firstFailure: Throwable? = null
        private set

    private fun <T> recordOnFailure(block: () -> T): T =
        try {
            block()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure
            throw failure
        }

    override fun write(b: Int) {
        recordOnFailure { delegate.write(b) }
        telemetry.recordSingle(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        recordOnFailure { delegate.write(b, off, len) }
        telemetry.record(b, off, len)
    }

    override fun flush() = recordOnFailure { delegate.flush() }

    override fun close() = recordOnFailure { delegate.close() }
}

/**
 * 流目标的写成功判定（D1 ∧ D2）：返回失败描述，成功返回 null。
 *
 * 两级顺序固定：先看首失败（D1），再看字节数与尾标（D2）——首失败的原文比尾标缺失更能归因。
 * **它消灭的具体失败**：
 * 1. native 吞掉写期异常后 `writeTo` 正常返回、调用方把截断 PDF 当成功（KD-79 本体，D1）；
 * 2. native 在调用 Java `write` 之前失败（如 `SetByteArrayRegion` 异常）→ 一个字节都没交出来，
 *    首失败不触发、`writeTo` 依旧正常返回（D2 的 `acceptedBytes > 0`）；
 * 3. 生成侧未写出尾标（`%%EOF` 不在尾窗）却被当成功（D2 尾标）。
 *
 * **它不消灭**：委托流静默短写（见文件头"已知不覆盖面"）。
 */
internal fun pdfWriteFailureReason(sink: PdfWriteFailureTrackingStream): String? {
    sink.firstFailure?.let { failure ->
        return "pdf write failed: ${failure.pdfWriteFailureText()}"
    }
    if (sink.telemetry.acceptedBytes <= 0L) {
        return "pdf write produced no bytes"
    }
    if (!sink.telemetry.tailContainsTrailer()) {
        return "pdf write is missing the $PDF_TRAILER_MARKER trailer (payload was truncated)"
    }
    return null
}

/**
 * 文件目标的字节面判定（D3 的前两段）：长度必须与接受字节一致，且文件尾窗含 `%%EOF`。
 *
 * **它消灭的具体失败**：委托流（或平台写路径）静默少收字节、尾部截断、零字节文件——
 * 这些在流目标上不可观测（见文件头），但文件落盘后 `File.length()` 与回读尾窗是硬事实。
 * 调用方必须先 `flush()` + `fd.sync()` 再调用本函数，否则读到的是 page cache 里的页数而非
 * 持久化结果（`sync` 失败由调用方自己折成有码失败）。
 */
internal fun pdfFileFailureReason(file: File, acceptedBytes: Long): String? {
    if (acceptedBytes <= 0L) return "pdf write produced no bytes"
    if (!file.isFile) return "pdf target is missing after write: ${file.name}"
    val length = file.length()
    if (length != acceptedBytes) {
        return "pdf target length $length does not match the $acceptedBytes accepted bytes"
    }
    val tail = readFileTail(file)
        ?: return "pdf target cannot be read back: ${file.name}"
    if (!containsTrailer(tail)) {
        return "pdf target is missing the $PDF_TRAILER_MARKER trailer (payload was truncated)"
    }
    return null
}

/** 读文件最后 [PDF_TAIL_WINDOW_BYTES]（短文件读全量）；读失败返回 null。 */
private fun readFileTail(file: File): ByteArray? = try {
    RandomAccessFile(file, "r").use { reader ->
        val length = reader.length()
        val start = (length - PDF_TAIL_WINDOW_BYTES).coerceAtLeast(0L)
        val size = (length - start).toInt().coerceAtMost(PDF_TAIL_WINDOW_BYTES)
        val buffer = ByteArray(size)
        reader.seek(start)
        reader.readFully(buffer)
        buffer
    }
} catch (_: Exception) {
    null
}

private fun containsTrailer(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    return String(bytes, StandardCharsets.ISO_8859_1).contains(PDF_TRAILER_MARKER)
}
