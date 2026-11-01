package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max

/**
 * [VisualDrawOpsPdfPainter] 的设备轨冒烟（批次 29 T3；R1
 * `docs/research/2026-10-28-pdf-replay-differences.md` §7.4 用例表）。
 *
 * 判据边界（R1 §5，不主张的明写）：
 * - **不主张 PDF 字节稳定**（字体子集 tag / Skia 内部实现 / 元数据不是我们的契约，R1 §5.6）；
 *   确定性只主张**栅格**一致（同文档两次独立构建 → `PdfRenderer` 回读逐位相等）。
 * - **不主张与屏逐像素相等**：写侧是 Skia、回读侧是 PDFium，两套栅格器（R1 §0-9）。本文件
 *   的形状判据用 bbox（±1 px）与内部色（±2/±3），文本用墨迹存在性（硬判 `ink > 0`，消灭
 *   Skia 静默丢字形）。
 * - 回读一律 `RENDER_MODE_FOR_PRINT`：display 模式会追加 `FPDF_LCD_TEXT` 子像素彩边，会把
 *   测量仪器自身的差异当成 PDF 缺陷（R2 `2026-10-28-screen-pdf-parity-gate.md` §0-3）。
 *
 * 坐标判据：夹具 viewBox 48×32 du；屏 `pxPerDu=2` → 96×64 px；PDF `ptPerDu=1` →
 * 48×32 pt，`PdfRenderer` `scale(2)` → 96×64 px。两侧同一像素网格（DPI 被消去）。
 */
@RunWith(AndroidJUnit4::class)
class VisualDrawOpsPdfPainterInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val viewport = VisualViewport(48, 32)
    private val createdFiles = ArrayList<File>()

    @After
    fun deleteTemporaryFiles() {
        createdFiles.forEach { it.delete() }
        createdFiles.clear()
    }

    // ------------------------------------------------------------ 夹具与助手

    private fun validatedDocument(
        json: String,
        atViewport: VisualViewport = viewport,
    ): VisualDrawOpsDocument {
        val validation = VisualDrawOpsValidator.validate(json, setOf("c1"), atViewport)
        if (validation !is VisualDrawOpsValidation.Valid) {
            val invalid = validation as VisualDrawOpsValidation.Invalid
            fail("fixture must validate: ${invalid.code}: ${invalid.message}")
        }
        return (validation as VisualDrawOpsValidation.Valid).document
    }

    /** 走生产入口写一份 PDF：每次新 painter，证明实例状态不跨写泄漏。 */
    private fun writePdf(
        document: VisualDrawOpsDocument,
        ptPerDu: Float,
        labelContent: (String) -> VisualLabelContent? = { null },
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        VisualDrawOpsPdfPainter().write(document, bytes, ptPerDu, labelContent)
        return bytes.toByteArray()
    }

    private fun temporaryPdfFile(bytes: ByteArray): File {
        val file = File.createTempFile("visual-pdf-", ".pdf", context.cacheDir)
        createdFiles.add(file)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    /** 页尺寸（点）——`PdfRenderer.Page.getWidth/getHeight` 与 `PdfDocument` 页单位同源。 */
    private fun pdfPageSize(bytes: ByteArray): Pair<Int, Int> {
        val file = temporaryPdfFile(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page -> return page.width to page.height }
            }
        }
    }

    /** PDF 页 → 像素阵（白底），`scale` 把页点映射到与屏相同的像素网格。 */
    private fun rasterizePdf(bytes: ByteArray, scale: Float, width: Int, height: Int): IntArray {
        val file = temporaryPdfFile(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        val transform = Matrix().apply { setScale(scale, scale) }
                        page.render(
                            bitmap,
                            null,
                            transform,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT,
                        )
                        return pixelsOf(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /** 屏 → 像素阵（同一份文档、同一份 label resolver）。 */
    private fun renderScreen(
        document: VisualDrawOpsDocument,
        pxPerDu: Float,
        labelContent: (String) -> VisualLabelContent? = { null },
    ): IntArray {
        val width = (document.viewBox.w * pxPerDu).toInt()
        val height = (document.viewBox.h * pxPerDu).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            VisualDrawOpsPainter().draw(document, Canvas(bitmap), pxPerDu, labelContent)
            return pixelsOf(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also { pixels ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    private fun pixelAt(pixels: IntArray, width: Int, x: Int, y: Int): Int = pixels[y * width + x]

    private fun channelDistance(first: Int, second: Int): Int = max(
        max(abs(Color.red(first) - Color.red(second)), abs(Color.green(first) - Color.green(second))),
        abs(Color.blue(first) - Color.blue(second)),
    )

    /** 墨迹计数（与白底的通道距离 > 8；把 PDFium 的近白 AA 边排除在外）。 */
    private fun inkCount(
        pixels: IntArray,
        width: Int,
        xRange: IntRange,
        yRange: IntRange,
        blank: Int,
    ): Int {
        var count = 0
        for (y in yRange) {
            for (x in xRange) {
                if (channelDistance(pixelAt(pixels, width, x, y), blank) > 8) count += 1
            }
        }
        return count
    }

    /** 墨迹包围盒 `[left, top, right, bottom]`；无墨返回 null。 */
    private fun inkBbox(pixels: IntArray, width: Int, height: Int, blank: Int): IntArray? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (channelDistance(pixelAt(pixels, width, x, y), blank) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    private fun assertChannelsClose(expected: Int, actual: Int, tolerance: Int, at: String) {
        val distance = channelDistance(expected, actual)
        assertTrue(
            "$at: expected≈0x${Integer.toHexString(expected)} got 0x${Integer.toHexString(actual)} (Δ$distance)",
            distance <= tolerance,
        )
    }

    private fun assertBboxClose(expected: IntArray?, actual: IntArray?, tolerance: Int, at: String) {
        assertNotNull("$at: screen raster must contain ink", expected)
        assertNotNull("$at: pdf raster must contain ink", actual)
        val screen = expected!!
        val pdf = actual!!
        for (index in 0..3) {
            assertTrue(
                "$at: bbox[$index] screen=${screen[index]} pdf=${pdf[index]}",
                abs(screen[index] - pdf[index]) <= tolerance,
            )
        }
    }

    private fun paintingFailureOf(block: () -> Unit): VisualDrawOpsPaintingException =
        try {
            block()
            throw AssertionError("expected VisualDrawOpsPaintingException")
        } catch (error: VisualDrawOpsPaintingException) {
            error
        }

    private fun textContent(
        payload: String,
        lines: List<VisualLabelLine>,
        heightDu: Float,
    ): VisualLabelContent.Text = VisualLabelContent.Text(
        payload = payload,
        sizeSp = 16f,
        fontScale = 1f,
        heightDu = heightDu,
        lines = lines,
    )

    /**
     * hand-built 文档（绕过校验器）：生成侧门（KD-78 ①②）收紧后，stroke-only / 奇数 dash
     * 会在 validator 处被拒；这两条仍是 painter 侧的**兜底**语义（计划 §1.3/§1.4），故用
     * 手建文档直接钉回放侧码族，不依赖生成门当前状态。
     */
    private fun handBuiltPathDocument(fill: String?, stroke: VisualStrokeSpec?): VisualDrawOpsDocument =
        VisualDrawOpsDocument(
            drawSchemaVersion = 1,
            viewBox = VisualDrawOpsViewBox(0.0, 0.0, 48.0, 32.0),
            background = null,
            ops = listOf(
                VisualDrawOp.DrawPath(
                    path = listOf(
                        VisualPathCommand.MoveTo(4.0, 4.0),
                        VisualPathCommand.LineTo(20.0, 4.0),
                        VisualPathCommand.LineTo(20.0, 12.0),
                        VisualPathCommand.LineTo(4.0, 12.0),
                        VisualPathCommand.Close,
                    ),
                    fill = fill,
                    stroke = stroke,
                    alpha = 1.0,
                ),
            ),
            hitRegions = emptyList(),
            stats = VisualDrawOpsStats(nodes = 0, pathCommands = 5, labels = 0, ops = 1),
        )

    // ------------------------------------------------------------ 页尺寸 / DPI 归一

    @Test
    fun pdfPageSizeUsesCeilOfViewBoxTimesPtPerDu() {
        val document = validatedDocument(DOC_FILL_RECT)
        // 48×32 du @1 pt/du → 48×32 pt（整数比例不额外加一）。
        assertEquals(48 to 32, pdfPageSize(writePdf(document, ptPerDu = 1f)))
        // @1.5 pt/du → 72×48 pt。
        assertEquals(72 to 48, pdfPageSize(writePdf(document, ptPerDu = 1.5f)))
        // 7×5 du @0.3 pt/du → 2.1×1.5 pt：ceil 才是 (3,2)（round/floor 分别给 (2,2)/(2,1)）。
        val fractional = validatedDocument(DOC_SMALL_BACKGROUND, VisualViewport(7, 5))
        assertEquals(3 to 2, pdfPageSize(writePdf(fractional, ptPerDu = 0.3f)))
    }

    @Test
    fun pdfFillMatchesTheScreenOnTheNormalizedPixelGrid() {
        // 同一文档：屏 pxPerDu=2（96×64）vs PDF ptPerDu=1 + PdfRenderer scale(2)（96×64）。
        // 矩形 du (4,2)-(22,14) → 设备 (8,4)-(44,28)（两侧同一网格）。
        val document = validatedDocument(DOC_FILL_RECT)
        val screen = renderScreen(document, pxPerDu = 2f)
        val pdf = rasterizePdf(writePdf(document, ptPerDu = 1f), scale = 2f, width = 96, height = 64)
        assertBboxClose(
            expected = inkBbox(screen, 96, 64, Color.WHITE),
            actual = inkBbox(pdf, 96, 64, Color.WHITE),
            tolerance = 1,
            at = "pdfFill",
        )
        // 关键区域着色：内部像素（远离 AA 边）必须同色（形状类内部色差 ≤2）。
        assertChannelsClose(0xFFFF0000.toInt(), pixelAt(screen, 96, 26, 16), 2, "screen fill")
        assertChannelsClose(pixelAt(screen, 96, 26, 16), pixelAt(pdf, 96, 26, 16), 2, "pdf fill")
        // 页原点在左上（R1 §9-8 的 origin probe）：矩形下方（y=40）不得有墨——若 y 翻转，
        // 墨会出现在下半页而本断言必红。
        assertEquals(0, inkCount(pdf, 96, 0..95, 36..63, Color.WHITE))
    }

    @Test
    fun pdfAndScreenShareTheSameGridAtAnyPageScale() {
        // DPI 归一：ptPerDu=1 + scale(2) 与 ptPerDu=2 + scale(1) 必须落到同一像素网格。
        // 内容坐标只差一个精确 ×2，故 bbox 与内部色都要一致（±1 px / ±2，R1 §6.3）；
        // 不做整阵逐位相等——那属于同参数确定性（下一个用例），跨页比例用容差更可归因。
        val document = validatedDocument(DOC_FILL_RECT)
        val base = rasterizePdf(writePdf(document, ptPerDu = 1f), scale = 2f, width = 96, height = 64)
        val doubled = rasterizePdf(writePdf(document, ptPerDu = 2f), scale = 1f, width = 96, height = 64)
        assertBboxClose(
            expected = inkBbox(base, 96, 64, Color.WHITE),
            actual = inkBbox(doubled, 96, 64, Color.WHITE),
            tolerance = 1,
            at = "pdfScale2",
        )
        assertChannelsClose(pixelAt(base, 96, 26, 16), pixelAt(doubled, 96, 26, 16), 2, "pdf scale probe")
    }

    @Test
    fun pdfBackgroundCoversTheRoundedUpPage() {
        // ceil 余条（7×5 du @0.3 → 3×2 pt）必须被背景填满：scale 10 → 30×20 px，四角同背景色。
        // 若只填 viewBox 实际面积（2.1×1.5 pt），右/下余条会露白，本断言红。
        val document = validatedDocument(DOC_SMALL_BACKGROUND, VisualViewport(7, 5))
        assertEquals(3 to 2, pdfPageSize(writePdf(document, ptPerDu = 0.3f)))
        val pixels = rasterizePdf(writePdf(document, ptPerDu = 0.3f), scale = 10f, width = 30, height = 20)
        // 四角 == 背景色（±2/255，R1 §6.3"背景"行）；若右/下余条露白，距离会是百级。
        val corners = listOf(0 to 0, 29 to 0, 0 to 19, 29 to 19)
        for ((x, y) in corners) {
            assertChannelsClose(0xFF123456.toInt(), pixelAt(pixels, 30, x, y), 2, "pdf background ($x,$y)")
        }
    }

    // ------------------------------------------------------------ 共享语义（矩阵 / clip / 文本 / alpha）

    @Test
    fun pdfNestedGroupClipCutsInRootSpace() {
        // 与屏用例同夹具（R1 §7.4 `pdfNestedGroupClipCutsInRootSpace`）：clip 烘焙为根空间
        // du (4,0)-(12,8)，绘制矩形局部 (0,0)-(8,8) 过 G2(+2) → 设备 (8,0)-(40,32)；
        // 交 = (8,0)-(24,16)。裁剪区外（x≥26）必须零墨——矩阵双乘会给出墨。
        val document = validatedDocument(DOC_NESTED_CLIP)
        val pdf = rasterizePdf(writePdf(document, ptPerDu = 1f), scale = 2f, width = 96, height = 64)
        assertTrue("clip 内应有墨", inkCount(pdf, 96, 10..22, 2..14, Color.WHITE) > 0)
        assertEquals(0, inkCount(pdf, 96, 26..95, 0..15, Color.WHITE))
    }

    @Test
    fun pdfGroupLabelLeavesInkAtTheProjectedRootSpacePosition() {
        // label(x=15,y=20) 在 concat(+5du) 生效下发射，坐标已是根空间投影 → 墨迹左缘设备 x=30。
        // 硬判 `ink > 0`（R1 §0-8：Skia 的 SkPDFStrike/SkPDFFont 失败会静默不出墨）。
        // 量具与回放 textPaint 同构：flags=REPLAY_TEXT_PAINT_FLAGS（0，平台构造默认是 1283）。
        val advancePx = Paint().apply {
            flags = REPLAY_TEXT_PAINT_FLAGS
            textSize = 16f * 2f
        }.measureText("H")
        assertTrue("font advance sanity", advancePx > 0f)
        val document = validatedDocument(DOC_LABEL_GROUP)
        val resolver: (String) -> VisualLabelContent? = { id ->
            if (id == "c1") textContent("H", listOf(VisualLabelLine(0, 1)), heightDu = 12f) else null
        }
        val screen = renderScreen(document, pxPerDu = 2f, labelContent = resolver)
        val pdf = rasterizePdf(writePdf(document, ptPerDu = 1f, labelContent = resolver), 2f, 96, 64)
        val rows = 6..50
        val leftEdgePx = 30
        assertTrue(
            "pdf label band must contain ink",
            inkCount(pdf, 96, leftEdgePx - 1..leftEdgePx + advancePx.toInt() + 2, rows, Color.WHITE) > 0,
        )
        assertEquals(0, inkCount(pdf, 96, 0..leftEdgePx - 4, rows, Color.WHITE))
        // 文本 bbox 只做带宽断言（±2 px）：两侧 AA/hinting 不同，字形像素不可比（R1 §5.1）。
        assertBboxClose(
            expected = inkBbox(screen, 96, 64, Color.WHITE),
            actual = inkBbox(pdf, 96, 64, Color.WHITE),
            tolerance = 2,
            at = "pdfLabel",
        )
    }

    @Test
    fun pdfHalfAlphaTracksTheScreenQuantizedAlpha() {
        // alpha=0.5 → floor(127.5)=127 的共享口径（T5 落码后公式含 colorA；本用例只比较
        // 屏/PDF 两侧同值，不复制公式——单一真值源在共享 painter）。内部色差 ≤3（R1 §6.3
        // "alpha 探针色"行）。
        val document = validatedDocument(DOC_ALPHA)
        val screen = renderScreen(document, pxPerDu = 2f)
        val pdf = rasterizePdf(writePdf(document, ptPerDu = 1f), 2f, 96, 64)
        val screenCenter = pixelAt(screen, 96, 24, 16)
        val pdfCenter = pixelAt(pdf, 96, 24, 16)
        assertChannelsClose(screenCenter, pdfCenter, 3, "pdf alpha center")
        // 量级正确：半透明红在白底上，绿/蓝必须显著高于 0 且显著低于 255（不透明红=0、
        // 未着色=255）。±6 覆盖两端栅格器的取整差。
        assertTrue("alpha must be applied, got g=${Color.green(pdfCenter)}", Color.green(pdfCenter) in 118..136)
        assertTrue("alpha must be applied, got b=${Color.blue(pdfCenter)}", Color.blue(pdfCenter) in 118..136)
    }

    @Test
    fun pdfDoesNotPaintHitRegions() {
        // 裁定 1（批次 29 §1.1）：hitRegions 可非空、painter 不绘制（原生点内判定由交互层
        // 消费）。夹具是空白页 + 覆盖大半页的命中区：谁把命中区当 op 画，白底会整块变色。
        // 两侧同判——共享语义在屏与 PDF 上一致。
        val document = validatedDocument(DOC_HIT_REGION_ONLY)
        val pdf = rasterizePdf(writePdf(document, ptPerDu = 1f), 2f, 96, 64)
        assertEquals(0, inkCount(pdf, 96, 0..95, 0..63, Color.WHITE))
        val screen = renderScreen(document, pxPerDu = 2f)
        assertEquals(0, inkCount(screen, 96, 0..95, 0..63, Color.WHITE))
    }

    // ------------------------------------------------------------ 确定性（栅格，不主张字节）

    @Test
    fun pdfRasterIsDeterministicAcrossTwoIndependentBuilds() {
        // 同文档两次独立构建（新 painter + 新 PdfDocument）→ 各自回读栅格逐位相等。
        // 不比较 PDF 字节：字体子集 tag/内部对象序不是我们的稳定契约（R1 §5.6）。
        val document = validatedDocument(DOC_NESTED_CLIP)
        val firstBytes = writePdf(document, ptPerDu = 1f)
        val secondBytes = writePdf(document, ptPerDu = 1f)
        val first = rasterizePdf(firstBytes, 2f, 96, 64)
        val second = rasterizePdf(secondBytes, 2f, 96, 64)
        assertTrue(Arrays.equals(first, second))
        // 同一份字节回读两遍也逐位相等（PdfRenderer 侧确定性）。
        val third = rasterizePdf(firstBytes, 2f, 96, 64)
        assertTrue(Arrays.equals(first, third))
    }

    // ------------------------------------------------------------ fail-closed 矩阵（与屏 painter 同码族）

    @Test
    fun pdfUnusablePtPerDuFailsClosed() {
        val document = validatedDocument(DOC_FILL_RECT)
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            paintingFailureOf { writePdf(document, ptPerDu = 0f) }.code,
        )
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            paintingFailureOf { writePdf(document, ptPerDu = Float.NaN) }.code,
        )
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            paintingFailureOf { writePdf(document, ptPerDu = Float.POSITIVE_INFINITY) }.code,
        )
    }

    @Test
    fun pdfOversizePageFailsClosed() {
        // 48×32 du @400 pt/du = 19200×12800 pt > 14400 兼容预算。
        val document = validatedDocument(DOC_FILL_RECT)
        val failure = paintingFailureOf { writePdf(document, ptPerDu = 400f) }
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failure.code)
    }

    @Test
    fun pdfWriteFailureIsCoded() {
        // 写盘失败必须以 PAINTER_PDF_IO 上抛，而不是裸 IOException 逃出有码族。
        val document = validatedDocument(DOC_BG_SET)
        val failure = paintingFailureOf {
            VisualDrawOpsPdfPainter().write(document, FailingOutputStream(), 1f, { null })
        }
        assertEquals(PAINTER_PDF_IO, failure.code)
    }

    @Test
    fun pdfWriteFailureMidStreamKeepsTheFirstFailureText() {
        // N-PDF-02：首块之后才开始抛。native 会继续尝试后续块（E-04 logcat 230 次），
        // 我们的记录必须停在**首个**失败，且失败前已接受的字节不回退（证明是"中途"而非首写）。
        val document = validatedDocument(DOC_FILL_RECT)
        val out = MidStreamFailingOutputStream(chunksBeforeFailure = 1)
        val failure = paintingFailureOf {
            VisualDrawOpsPdfPainter().write(document, out, 1f, { null })
        }
        assertEquals(PAINTER_PDF_IO, failure.code)
        assertTrue(
            "first failure must be the mid-stream IOException, got ${failure.message}",
            failure.message!!.contains(MID_STREAM_WRITE_FAILURE),
        )
        assertTrue("failure must happen after the first chunk, accepted=${out.acceptedBytes}", out.acceptedBytes > 0)
    }

    // ------------------------------------------------------------ 文件目标（D1 ∧ D2 ∧ D3）

    @Test
    fun pdfFileWriteRoundTripsThroughReadback() {
        // P-PDF-01：真文件目标 → 长度/尾标回读 + PdfRenderer 页数回读 + renameTo 发布。
        val document = validatedDocument(DOC_NESTED_CLIP)
        val target = File(context.cacheDir, "visual-pdf-file-${System.nanoTime()}.pdf")
        createdFiles.add(target)
        VisualDrawOpsPdfPainter().writeFile(document, target, 1f, { null })
        assertTrue("target must exist", target.isFile)
        assertTrue("target must be non-empty", target.length() > 0)
        val bytes = target.readBytes()
        assertTrue(
            "published file must end with the %%EOF trailer",
            String(bytes, StandardCharsets.ISO_8859_1).contains("%%EOF"),
        )
        // 回读页尺寸与流目标一致（同一份 PDF 字节的契约面）。
        assertEquals(48 to 32, pdfPageSize(bytes))
        val leftovers = context.cacheDir.listFiles { file ->
            file.name.startsWith("${target.name}.pending-")
        }.orEmpty()
        assertTrue("publish must not leave a pending file", leftovers.isEmpty())
    }

    @Test
    fun pdfReadOnlyTargetFailsClosedBeforeAnyWrite() {
        // N-PDF-06 的落地形态：目标目录 0555 → 临时件 open 失败（EACCES）先于 writeTo，
        // 必须折成有码 PAINTER_PDF_IO，而不是裸 FileNotFoundException 逃出族外。
        val directory = File(context.cacheDir, "visual-pdf-readonly-${System.nanoTime()}")
        assertTrue("read-only fixture dir must be created", directory.mkdirs())
        try {
            assertTrue(
                "cache dir must accept chmod 0555 for this fixture",
                directory.setWritable(false, false),
            )
            val document = validatedDocument(DOC_FILL_RECT)
            val target = File(directory, "out.pdf")
            val failure = paintingFailureOf {
                VisualDrawOpsPdfPainter().writeFile(document, target, 1f, { null })
            }
            assertEquals(PAINTER_PDF_IO, failure.code)
            assertTrue(
                "open failure must name the unwritable target, got ${failure.message}",
                failure.message!!.contains("not writable"),
            )
            assertTrue("no half-written target may be published", !target.exists())
        } finally {
            directory.setWritable(true, true)
            directory.deleteRecursively()
        }
    }

    @Test
    fun pdfReplayFailuresKeepTheScreenPainterCodeFamily() {
        // 例外面同码族（任务四）：共享 painter 的码原样穿过 PDF 包装，不被生命周期收口异常
        // （close() 的 IllegalStateException）掩盖。手建文档 = 生成门收紧后回放侧仍须兜底。
        val strokeOnly = handBuiltPathDocument(
            fill = null,
            stroke = VisualStrokeSpec(2.0, VisualStrokeCap.BUTT, VisualStrokeJoin.MITER, 4.0, null),
        )
        val strokeOnlyFailure = paintingFailureOf { writePdf(strokeOnly, ptPerDu = 1f) }
        assertEquals(PAINTER_STROKE_WITHOUT_FILL, strokeOnlyFailure.code)
        assertTrue(strokeOnlyFailure.message!!.contains("ops[0]"))

        val oddDash = handBuiltPathDocument(
            fill = "#ff0000",
            stroke = VisualStrokeSpec(2.0, VisualStrokeCap.BUTT, VisualStrokeJoin.MITER, 4.0, listOf(2.0, 4.0, 2.0)),
        )
        val oddDashFailure = paintingFailureOf { writePdf(oddDash, ptPerDu = 1f) }
        assertEquals(PAINTER_DASH_ODD, oddDashFailure.code)
        assertTrue(oddDashFailure.message!!.contains("ops[0]"))
    }

    // ------------------------------------------------------------ 夹具

    private class FailingOutputStream : OutputStream() {
        override fun write(b: Int) {
            throw IOException("simulated write failure")
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            throw IOException("simulated write failure")
        }
    }

    /**
     * 接受前 [chunksBeforeFailure] 块、其后每块都抛（N-PDF-02 夹具）。
     * `acceptedBytes` 用来证明失败发生在"中途"而不是首块（首块已被 native 顺利交出）。
     */
    private class MidStreamFailingOutputStream(
        private val chunksBeforeFailure: Int,
    ) : OutputStream() {

        var acceptedBytes: Long = 0L
            private set

        private var chunksAccepted = 0

        override fun write(b: Int) {
            throw IOException(MID_STREAM_WRITE_FAILURE)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (chunksAccepted >= chunksBeforeFailure) {
                throw IOException(MID_STREAM_WRITE_FAILURE)
            }
            chunksAccepted += 1
            acceptedBytes += len.toLong()
        }
    }

    private companion object {
        const val MID_STREAM_WRITE_FAILURE = "simulated mid-stream write failure"

        val DOC_FILL_RECT = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,2],["L",22,2],["L",22,14],["L",4,14],["Z"]],""" +
            """"fill":"#ff0000","stroke":null,"alpha":1}],"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        val DOC_ALPHA = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],""" +
            """"fill":"#ff0000","stroke":null,"alpha":0.5}],"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        val DOC_NESTED_CLIP = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"save"},{"op":"concat","m":[2,0,0,2,0,0]},{"op":"save"},""" +
            """{"op":"clip","path":[["M",4,0],["L",12,0],["L",12,8],["L",4,8],["Z"]],"rule":"nonzero"},""" +
            """{"op":"concat","m":[1,0,0,1,2,0]},""" +
            """{"op":"path","path":[["M",0,0],["L",8,0],["L",8,8],["L",0,8],["Z"]],"fill":"#00aa00","stroke":null,"alpha":1},""" +
            """{"op":"restore"},{"op":"restore"}],"hitRegions":[],"stats":{"nodes":2,"pathCommands":10,"labels":0,"ops":8}}"""

        val DOC_LABEL_GROUP = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"save"},{"op":"concat","m":[1,0,0,1,5,0]},""" +
            """{"op":"label","contentId":"c1","x":15,"y":20,"rotationDeg":0,"hAlign":"center","vAlign":"middle","color":"#26354a","alpha":1},""" +
            """{"op":"restore"}],"hitRegions":[],"stats":{"nodes":1,"pathCommands":0,"labels":1,"ops":4}}"""

        val DOC_BG_SET = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#123456","ops":[],"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

        /** 7×5 du：`ptPerDu=0.3` 时页 2.1×1.5 pt → ceil 后 3×2 pt（ceil 余条承载夹具）。 */
        val DOC_SMALL_BACKGROUND = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":7,"h":5},""" +
            """"background":"#123456","ops":[],"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

        /** 空白页 + 非空命中区（stats.pathCommands 把命中区路径计入复算，故为 5）。 */
        val DOC_HIT_REGION_ONLY = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[],"hitRegions":[{"id":"r1","path":[["M",0,0],["L",40,0],""" +
            """["L",40,28],["L",0,28],["Z"]],"rule":"nonzero"}],""" +
            """"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":0}}"""
    }
}
