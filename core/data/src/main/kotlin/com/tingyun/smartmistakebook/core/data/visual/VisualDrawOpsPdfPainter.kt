package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.pdf.PdfDocument
import java.io.IOException
import java.io.OutputStream

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
 * 3. **I/O 归类**：`writeTo` 的写侧异常折进 [PAINTER_PDF_IO]，调用方才能按码分类。
 *    注意平台行为：API 34 的 native `SkJavaOutputStream::write` 会把 OutputStream 的
 *    Java 异常 `ExceptionDescribe/ExceptionClear` 后返回 false，异常**不会**逃回 Java
 *    （KD-79）。故本类用 [PdfWriteFailureTrackingStream] 在异常离开 Java 前记录，
 *    `writeTo` 返回后复验——否则写盘失败静默截断 PDF 并报成功（fail-open）。
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
        val pdf = PdfDocument()
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
            // OutputStream 抛出的 Java 异常后 `ExceptionDescribe()` + `ExceptionClear()`
            // 并返回 false（android-14.0.0_r1 CreateJavaOutputStreamAdaptor.cpp），Java
            // 侧**看不到**异常——`catch (IOException)` 在此不可达，写盘失败会静默截断
            // PDF 并报成功（fail-open，批次 29 设备证据 R2 / KD-79）。包装层在异常离开
            // Java 时先记录再重抛；writeTo 正常返回后再按记录发 PAINTER_PDF_IO。
            val sink = PdfWriteFailureTrackingStream(out)
            try {
                pdf.writeTo(sink)
            } catch (io: IOException) {
                // 保留原 catch 作双重兜底：若未来平台版本改为让异常逃回 Java，仍按码归类。
                throw paintingFailure(PAINTER_PDF_IO, "pdf write failed: ${io.describe()}")
            }
            sink.firstFailure?.let { failure ->
                throw paintingFailure(PAINTER_PDF_IO, "pdf write failed: ${failure.describe()}")
            }
        } finally {
            try {
                pdf.close()
            } catch (_: RuntimeException) {
                // close() 的收口异常（如页未 finish）不得掩盖上方已上抛的原始有码异常。
            }
        }
    }
}

/**
 * 记录**在异常离开 Java 之前**的写侧失败（KD-79 的核心 seam）。
 *
 * `PdfDocument.writeTo` 的 native 写适配器会 `ExceptionDescribe()` + `ExceptionClear()`
 * 掉 OutputStream 抛出的 Java 异常并返回 false（android-14.0.0_r1
 * `CreateJavaOutputStreamAdaptor.cpp` `SkJavaOutputStream::write`），因此调用方拿不到
 * 异常、也拿不到返回值（`writeTo` 是 void）。本包装在每个写入口先记录首个失败再**重抛**
 * ——重抛保证 native 仍按"这次写失败"处理，记录保证 `writeTo` 返回后能据此 fail-closed。
 *
 * 只记录、不吞：`close()`/`flush()` 也原样转发，行为与直接传 [delegate] 一致。
 */
private class PdfWriteFailureTrackingStream(
    private val delegate: OutputStream,
) : OutputStream() {

    /** 首个从 Java 侧逃出的写异常；无失败为 null。 */
    var firstFailure: Exception? = null
        private set

    private fun <T> recordOnFailure(block: () -> T): T =
        try {
            block()
        } catch (failure: Exception) {
            if (firstFailure == null) firstFailure = failure
            throw failure
        }

    override fun write(b: Int) = recordOnFailure { delegate.write(b) }

    override fun write(b: ByteArray, off: Int, len: Int) = recordOnFailure { delegate.write(b, off, len) }

    override fun flush() = recordOnFailure { delegate.flush() }

    override fun close() = recordOnFailure { delegate.close() }
}

private fun Exception.describe(): String = message ?: javaClass.simpleName
