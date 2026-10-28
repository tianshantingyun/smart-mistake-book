package com.tingyun.smartmistakebook.core.data.visual

import kotlin.math.ceil

/**
 * PDF 回放 painter 的**纯计算层**（批次 29 T3；R1
 * `docs/research/2026-10-28-pdf-replay-differences.md` §7.2 落码规格）。
 *
 * **文件级结构不变量：本文件零 android 导入。** 与 [VisualDrawOpsPainterPure] 同一条分层理由：
 * `core:data` 的 JVM 单测没有 Robolectric，而"页尺寸 = ceil(viewBox × ptPerDu)"的取整规则
 * 与 int 边界（`PdfDocument.PageInfo.Builder` 只收正整数，越界抛**无码**
 * `IllegalArgumentException`）必须能在 JVM 门里钉死，不能只靠设备轨。
 *
 * 本文件只承载 PDF 目标**独有的纯计算**（页几何）。DrawOps 回放语义不在这里复制——
 * 矩阵装配、颜色词表、alpha floor、dash 形状门全部复用 [VisualDrawOpsPainterPure] 的
 * 单一实现（复制第二份状态机 = 两份实现必然漂移，R1 §7.1）。
 */

/**
 * 页几何门：消灭"`ptPerDu` 非有限/非正或取整后页尺寸非法 → `PageInfo.Builder` 抛无码
 * `IllegalArgumentException`（page width must be positive）/ 巨型页让查看器拒开"。
 *
 * 后者是兼容预算而非规约条文（R1 §9-11 明示 `14400` 未取到条文出处）：自定上限，先折进
 * 有码族，将来取到条文再改值，码面不变。
 */
internal const val PAINTER_PDF_PAGE_BOUNDS = "PAINTER_PDF_PAGE_BOUNDS"

/**
 * I/O 门：消灭"`PdfDocument.writeTo` / 输出流写入失败以裸 `IOException` 逃出
 * [VisualDrawOpsPaintingException] 族 → 调用方无法按码分类（引擎侧诊断口径为
 * "CODE: message"）"。
 */
internal const val PAINTER_PDF_IO = "PAINTER_PDF_IO"

/**
 * 单页点数上限（宽与高各自约束）。**兼容预算，不是 PDF 规约事实**（R1 §9-11：本轮未取到
 * 规约条文）。取 14400 pt = 200 英寸，远超任何目标栏宽；超界视为目标参数错误而非可渲染输入。
 */
internal const val VISUAL_PDF_MAX_PAGE_POINTS = 14_400

/** 页尺寸（PostScript point；`PdfDocument.PageInfo` 只接受 int）。 */
internal data class VisualPdfPageSize(
    val widthPt: Int,
    val heightPt: Int,
)

/**
 * `viewBox`（du）× `ptPerDu` → 页尺寸（int pt），**取 ceil**。
 *
 * **它消灭的具体失败**：
 * 1. `round`/`floor` 把右/下 ≤1 pt 的内容裁进页外（背景/边框贴边时直接丢墨）；
 * 2. 取整后 0 或负值在 `PageInfo.Builder.create()` 处才炸成无码 IAE；
 * 3. 非有限输入（NaN/Inf）静默转成 0/Int.MAX 生成退化页。
 *
 * ceil 的副作用是右/下至多多出 1 pt 空白，而 `drawColor` 在记录画布上的 clip 就是整页
 * （R1 §1），余条被背景色填满，不会留白边（设备面 `pdfBackgroundCoversTheRoundedUpPage`）。
 *
 * 注：`viewBox.x/y` 不参与页尺寸——校验器要求 viewBox 严格回显 `{0,0,viewport}`，根变换
 * 从 (0,0) 起算；手建文档若带非零偏移，语义由回放侧 `setMatrix(root)` 决定，不在本函数猜。
 */
internal fun visualPdfPageSize(
    viewBoxW: Double,
    viewBoxH: Double,
    ptPerDu: Float,
): VisualPdfPageSize {
    if (!ptPerDu.isFinite() || ptPerDu <= 0f) {
        throw paintingFailure(
            PAINTER_PDF_PAGE_BOUNDS,
            "pdf ptPerDu must be finite and positive, got $ptPerDu",
        )
    }
    val width = ceil(viewBoxW * ptPerDu.toDouble())
    val height = ceil(viewBoxH * ptPerDu.toDouble())
    if (!width.isFinite() || !height.isFinite()) {
        throw paintingFailure(
            PAINTER_PDF_PAGE_BOUNDS,
            "pdf page size is not finite for viewBox ${viewBoxW}x$viewBoxH at ptPerDu $ptPerDu",
        )
    }
    val widthPt = width.toInt()
    val heightPt = height.toInt()
    if (widthPt < 1 || heightPt < 1 || widthPt > VISUAL_PDF_MAX_PAGE_POINTS ||
        heightPt > VISUAL_PDF_MAX_PAGE_POINTS
    ) {
        throw paintingFailure(
            PAINTER_PDF_PAGE_BOUNDS,
            "pdf page must be within 1..$VISUAL_PDF_MAX_PAGE_POINTS points per side, " +
                "got ${widthPt}x$heightPt for viewBox ${viewBoxW}x$viewBoxH at ptPerDu $ptPerDu",
        )
    }
    return VisualPdfPageSize(widthPt = widthPt, heightPt = heightPt)
}
