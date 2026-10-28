package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * DrawOps V1 → `PdfDocument` 页画布的**回放包装**（批次 29 T3；R1
 * `docs/research/2026-10-28-pdf-replay-differences.md` §7）。
 *
 * 它**不**实现第二套 op 遍历：`PdfDocument.Page.getCanvas()` 就是一块
 * `SkPictureRecorder` 记录画布（AOSP `libs/hwui/jni/pdf/PdfDocument.cpp:24,35,72,82,89-95`），
 * 与屏 `Bitmap` 画布走同一套 `Canvas` API，因此唯一的绘制入口是共享的
 * [VisualDrawOpsPainter.draw]——`ptPerDu` 只是替换 `pxPerDu`（契约
 * `visual-generation-contract.md:192`）。矩阵列序、clip/label 根空间舞步、背景首步、
 * fill→stroke 相位序、dash 形状门、颜色词表、alpha floor **全部由共享实现唯一决定**；
 * 本类只包 PDF 目标独有的三件事，各自对应一个已发生的失败：
 *
 * 1. **生命周期**：`startPage/finishPage` 必须成对，`close()`/`writeTo()` 都会
 *    `throwIfCurrentPageNotFinished()`；绘制中途抛异常时若页未 finish，`finally` 里的
 *    `close()` 会再抛 `IllegalStateException("Current page not finished!")`，把真实有码异常
 *    （如 `PAINTER_BALANCE`）盖掉。故 draw 失败路径先 finish（丢弃该页）再原样上抛。
 * 2. **页几何**：页尺寸是 int pt，`PageInfo.Builder` 对非正尺寸抛**无码**
 *    `IllegalArgumentException`；取整与边界规则折进 [PAINTER_PDF_PAGE_BOUNDS]
 *    （纯层 [visualPdfPageSize]）。
 * 3. **I/O 归类与写成功判定**：`writeTo` 的写侧失败折进 [PAINTER_PDF_IO]；且不能只信
 *    "`writeTo` 正常返回"——API 34 的 native `SkJavaOutputStream::write` 会把 OutputStream 的
 *    Java 失败 `ExceptionDescribe/ExceptionClear` 后返回 false，异常**不会**逃回 Java
 *    （KD-79）。故写侧用 [PdfWriteFailureTrackingStream]（纯层
 *    `PdfWriteIntegrity.kt`）记录首失败 + 字节数/尾标，`writeTo` 返回后按
 *    [pdfWriteFailureReason] 复验——否则写盘失败静默截断 PDF 并报成功（fail-open）。
 *
 * ## PDF 特有差异（R1 §2/§4/§5 清单；显式处置，不静默改语义）
 *
 * | 面 | 屏（Bitmap Canvas） | PDF（SkPDF 写侧 + 查看器栅格化） | 本类处置 |
 * |---|---|---|---|
 * | 坐标/单位 | du→px，`pxPerDu` | 页单位 pt；`ptPerDu`；页尺寸 int 且取 ceil | 只换比例，绘制代码零分支；余条由背景整页覆盖 |
 * | clip | `clipPath` = INTERSECT、non-AA | `W n`/`W\* n`，只支持求交与非逆填充 | DrawOps 词表只发 nonzero/evenodd 且只求交 → 语义被完整覆盖 |
 * | dash | `DashPathEffect` 本地空间压成轮廓 | SkPDF 把虚线**压平成填充几何**（不写 `d` 算符） | 绘制调用不变；段结构由同一 pattern 决定；亚像素轮廓差归允许差异（见 T4 原型门） |
 * | alpha | `Paint.setAlpha` 后 Skia 合成 | ExtGState `/ca`（`CA`）取 `Paint` 颜色浮点 alpha | 同一 `bindPathPaint`/`bindTextPaint` → 两端自动同值 |
 * | 文本 | 设备字体链 + Skia 栅格化（AA 关） | 子集嵌入或 CFF→Type3 轮廓；查看器栅格化 | 位置由共享实现一次算定；`ink > 0` 由设备轨硬判（Skia 静默丢字形面） |
 * | 抗锯齿 | 路径 AA 开、文本 AA 关 | 内容流无 AA 参数，查看器决定 | **不可比**；不主张像素相等（一致性门只做带宽/结构断言，且是原型门） |
 * | 背景 | `background=null` = 透明位图 | 页无 alpha 通道；`drawColor` 填满整页，null = 纸白 | 目标语义差，登记而非修补；判决夹具一律给不透明背景 |
 * | hitRegions | 不绘制（交互层消费） | 同左 | 不读 `document.hitRegions`（共享 painter 亦不读） |
 * | PDF 字节 | 不适用 | 字体子集 tag / Skia 内部实现不稳定 | 不主张字节稳定；确定性只主张**栅格**一致（设备轨用例） |
 *
 * **螺纹纪律**：与 [VisualDrawOpsPainter] 相同——内部持有该共享实例及其常驻缓冲，非线程安全；
 * 同一实例只在单线程使用，可跨多次 [write] 复用。`write()` 每次新建 `PdfDocument`。
 */
internal class VisualDrawOpsPdfPainter {

    private val shared = VisualDrawOpsPainter()

    /**
     * 把一份**已通过 [VisualDrawOpsValidator] 校验**的类型化文档写成单页 PDF。
     *
     * 页宽高由 [visualPdfPageSize] 从 `document.viewBox` × [ptPerDu] 取 ceil；内容矩形缺省
     * 即整页（`PdfDocument.java:369-375`），不平移。
     *
     * 写成功判据 = **D1 ∧ D2**（R2 §5）：`writeTo` 返回后要求跟踪流无首失败、接受字节 > 0、
     * 尾窗含 `%%EOF`；任一不成立即 [PAINTER_PDF_IO]。**流目标不主张 D3**——`OutputStream`
     * 不可 seek，无法回读页数/文件长度；需要完整判据的调用方用 [writeFile]。
     *
     * @param ptPerDu 根变换比例（点数/du）；非有限/非正或页尺寸越界即
     *   [PAINTER_PDF_PAGE_BOUNDS]（先于任何 PDF 对象创建）。
     * @param labelContent contentId → 内容解析器；返回 null = 绘制时内容不可达
     *   （[PAINTER_UNKNOWN_LABEL]，与屏侧同码）。
     * @throws VisualDrawOpsPaintingException 共享 painter 的全部码族 + [PAINTER_PDF_PAGE_BOUNDS]
     *   + [PAINTER_PDF_IO]。
     */
    fun write(
        document: VisualDrawOpsDocument,
        out: OutputStream,
        ptPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ) {
        // 先于 PdfDocument 创建做页几何门：坏比例不能留下一个半开的文档对象。
        val pageSize = visualPdfPageSize(document.viewBox.w, document.viewBox.h, ptPerDu)
        writeTracked(document, out, pageSize, ptPerDu, labelContent)
    }

    /**
     * 文件目标的完整写成功判据（R2 §5 的 **D1 ∧ D2 ∧ D3**）：同目录临时件 → 写 → `flush` +
     * `fd.sync` → 长度/尾标回读 → `PdfRenderer` 页数回读 → `renameTo` 发布。
     *
     * **它消灭的具体失败**（每一条都是流目标看不见的）：
     * 1. 写成功但只进 page cache（掉电后为空/残缺）→ `fd.sync()` 失败折 [PAINTER_PDF_IO]；
     * 2. 委托写路径静默少收字节 / 尾部截断（[pdfFileFailureReason] 的长度与尾标回读）；
     * 3. 结构性损坏（失 xref/trailer、页丢失）→ 重开 `PdfRenderer` 的页数回读；
     * 4. `renameTo` 返回 false 却被当成功 → 显式查返回值；
     * 5. 目标目录只读/不存在 → 临时件 open 失败折 [PAINTER_PDF_IO]（裸 `FileNotFoundException`
     *    会逃出有码族，调用方无法按码分类）。
     *
     * **落点理由（不复用 `core:export`）**：`MistakePdfExporter.prepare/validatedPageCount/publish`
     * 是同一序列的成熟实现，但 `validatedPageCount`/`publish`/`PreparedPdfStore` 均 private，
     * `core:data` 不依赖 `core:export`，而 `core:export` 是本批禁改热区——故按该序列在本包
     * 复制（R2 §3.6 + 研究复核高危⑥的保守分支），不新增依赖、不改热区。
     *
     * @param target 最终 PDF 路径；父目录必须存在且可写（同目录临时件 + `renameTo` 的语义）。
     * @throws VisualDrawOpsPaintingException 同 [write]；失败时保证不发布半成品（临时件删除，
     *   已存在的 `target` 不被本方法破坏）。
     */
    fun writeFile(
        document: VisualDrawOpsDocument,
        target: File,
        ptPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ) {
        // 同 [write]：页几何门先于任何文件创建。
        val pageSize = visualPdfPageSize(document.viewBox.w, document.viewBox.h, ptPerDu)
        val directory = target.absoluteFile.parentFile
            ?: throw paintingFailure(
                PAINTER_PDF_IO,
                "pdf target has no parent directory: ${target.name}",
            )
        val pending = File(directory, "${target.name}.pending-${UUID.randomUUID()}")
        var published = false
        try {
            val fileOutput = try {
                FileOutputStream(pending)
            } catch (failure: Exception) {
                // 只读目录 / 不存在目录 / EACCES：open 失败先于 writeTo，必须折进有码族。
                throw paintingFailure(
                    PAINTER_PDF_IO,
                    "pdf target is not writable: ${pending.name}: ${failure.pdfWriteFailureText()}",
                )
            }
            val sink = try {
                fileOutput.use { raw ->
                    val tracked = writeTracked(document, raw, pageSize, ptPerDu, labelContent)
                    try {
                        raw.flush()
                        raw.fd.sync()
                    } catch (failure: Exception) {
                        throw paintingFailure(
                            PAINTER_PDF_IO,
                            "pdf sync failed: ${failure.pdfWriteFailureText()}",
                        )
                    }
                    tracked
                }
            } catch (coded: VisualDrawOpsPaintingException) {
                throw coded
            } catch (failure: Exception) {
                // close() 等收口失败同样是写侧失败（不得静默当成功）。
                throw paintingFailure(
                    PAINTER_PDF_IO,
                    "pdf file write failed: ${failure.pdfWriteFailureText()}",
                )
            }
            pdfFileFailureReason(pending, sink.telemetry.acceptedBytes)?.let { reason ->
                throw paintingFailure(PAINTER_PDF_IO, reason)
            }
            val pageCount = readPdfPageCount(pending)
            if (pageCount != PDF_PAGE_COUNT) {
                throw paintingFailure(
                    PAINTER_PDF_IO,
                    "pdf readback expected $PDF_PAGE_COUNT page, got $pageCount",
                )
            }
            if (!pending.renameTo(target)) {
                throw paintingFailure(
                    PAINTER_PDF_IO,
                    "pdf publish failed: rename ${pending.name} -> ${target.name} returned false",
                )
            }
            published = true
        } finally {
            if (!published) {
                // 失败不得留下半成品（临时件与本方法新建的目标文件都不发布）。
                pending.delete()
            }
        }
    }

    /**
     * 共享的写路径：页几何 → `PdfDocument` 生命周期 → 跟踪流写入 → D1/D2 复验。
     * 返回跟踪流，供文件目标读取 [PdfWriteTelemetry.acceptedBytes]。
     */
    private fun writeTracked(
        document: VisualDrawOpsDocument,
        out: OutputStream,
        pageSize: VisualPdfPageSize,
        ptPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ): PdfWriteFailureTrackingStream {
        val pdf = PdfDocument()
        val sink = PdfWriteFailureTrackingStream(out)
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
            val page = pdf.startPage(pageInfo)
            try {
                // 唯一绘制入口：与屏侧完全同一状态机（本类没有自己的 when(op)）。
                shared.draw(document, page.canvas, ptPerDu, labelContent)
            } catch (failure: Throwable) {
                // 页未 finish 时 close()/writeTo() 会抛无码 IllegalStateException 并掩盖
                // 原始有码异常（R1 §7.1-1）；先 finish 丢弃该页，再原样上抛。
                try {
                    pdf.finishPage(page)
                } catch (_: RuntimeException) {
                    // 收口失败不得二次掩盖原始异常。
                }
                throw failure
            }
            pdf.finishPage(page)
            // PdfDocument.writeTo 在 API 34 由 native `SkJavaOutputStream::write` 捕获
            // OutputStream 抛出的 Java 失败后 `ExceptionDescribe()` + `ExceptionClear()`
            // 并返回 false（android-14.0.0_r1 CreateJavaOutputStreamAdaptor.cpp），Java
            // 侧**看不到**异常——`catch (IOException)` 在此不可达，写盘失败会静默截断
            // PDF 并报成功（fail-open，批次 29 设备证据 R2 / KD-79）。包装层在失败离开
            // Java 时先记录再重抛；writeTo 正常返回后再按记录与字节/尾标复验。
            try {
                pdf.writeTo(sink)
            } catch (io: IOException) {
                // 保留原 catch 作双重兜底：若未来平台版本改为让异常逃回 Java，仍按码归类。
                throw paintingFailure(PAINTER_PDF_IO, "pdf write failed: ${io.pdfWriteFailureText()}")
            }
            pdfWriteFailureReason(sink)?.let { reason ->
                throw paintingFailure(PAINTER_PDF_IO, reason)
            }
        } finally {
            try {
                pdf.close()
            } catch (_: RuntimeException) {
                // close() 的收口异常（如页未 finish）不得掩盖上方已上抛的原始有码异常。
            }
        }
        return sink
    }

    /**
     * D3 的页数回读：`PdfRenderer` 只吃可 seek 的 fd（`PdfRenderer.java:162-168`）。
     *
     * 必须 `catch (Exception)` 而不是只接 `IOException`：`PdfUtils.cpp:45-80`
     * `forwardPdfiumError` 对 `FPDF_ERR_UNKNOWN` 抛受检 `java.lang.Exception`，
     * `openPage` 失败抛 `IllegalStateException`（R2 §3.4）。
     */
    private fun readPdfPageCount(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
    } catch (failure: Exception) {
        throw paintingFailure(
            PAINTER_PDF_IO,
            "pdf readback failed: ${failure.pdfWriteFailureText()}",
        )
    }

    private companion object {
        /** 本 painter 恒写单页（`PdfDocument.PageInfo.Builder(..., 1)`），回读页数只认 1。 */
        const val PDF_PAGE_COUNT = 1
    }
}
