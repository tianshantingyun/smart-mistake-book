package com.tingyun.smartmistakebook.core.data.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisualDrawOpsPdfPainterPure] 页几何纯层的 JVM 门（批次 29 T3；R1
 * `docs/research/2026-10-28-pdf-replay-differences.md` §7.2/§7.3）。
 *
 * **不变量**：不触任何 android 符号（被测文件零 android 导入——JVM 无 Robolectric 也能成立
 * 的结构前提）；`PdfDocument` 生命周期、实际页尺寸与栅格对拍归设备轨
 * `VisualDrawOpsPdfPainterInstrumentedTest`。
 *
 * 覆盖映射：
 * - ceil 取整（每边独立）+ 区分 round/floor 必红的非整数负例；
 * - `ptPerDu` 非有限/非正 → [PAINTER_PDF_PAGE_BOUNDS]；
 * - 退化/非有限 viewBox → 同码；
 * - 14400 pt 兼容预算的两侧边界（=上限放行、+1 拒绝）；
 * - 诊断预算口径（码前缀 + ≤512，与 [com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome] 同源）。
 */
class VisualDrawOpsPdfPainterPureTest {

    private fun failureOf(block: () -> Unit): VisualDrawOpsPaintingException =
        try {
            block()
            throw AssertionError("expected VisualDrawOpsPaintingException")
        } catch (error: VisualDrawOpsPaintingException) {
            error
        }

    // ------------------------------------------------------------ ceil 取整

    @Test
    fun `page size ceils viewBox times ptPerDu on each side`() {
        // ceil→整页不留白（R1 §7.2）：48×32 du @1 pt/du = 48×32 pt。
        assertEquals(VisualPdfPageSize(48, 32), visualPdfPageSize(48.0, 32.0, 1f))
        // 非整数比例：1.5 是二进制精确值 → 72×48，无浮点噪声。
        assertEquals(VisualPdfPageSize(72, 48), visualPdfPageSize(48.0, 32.0, 1.5f))
    }

    @Test
    fun `a fractional page is ceiled not rounded or floored`() {
        // 7×5 du @0.3 pt/du ⇒ 2.1000…×1.5000… pt。ceil → (3,2)；
        // round 会给 (2,2)（宽错），floor 会给 (2,1)（宽高皆错）——三者互不相同，
        // 故本用例对"改成 round/floor"的实现必红（R1 §7.2 的 ceil 裁定）。
        assertEquals(VisualPdfPageSize(3, 2), visualPdfPageSize(7.0, 5.0, 0.3f))
        // 右/下边界落在整数上时 ceil 不额外加一（48×32 @2 = 96×64，不是 97×65）。
        assertEquals(VisualPdfPageSize(96, 64), visualPdfPageSize(48.0, 32.0, 2f))
    }

    // ------------------------------------------------------------ 页几何门

    @Test
    fun `an unusable ptPerDu is rejected with a typed code`() {
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(48.0, 32.0, 0f) }.code)
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(48.0, 32.0, -0.5f) }.code)
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(48.0, 32.0, Float.NaN) }.code)
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            failureOf { visualPdfPageSize(48.0, 32.0, Float.POSITIVE_INFINITY) }.code,
        )
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            failureOf { visualPdfPageSize(48.0, 32.0, Float.NEGATIVE_INFINITY) }.code,
        )
    }

    @Test
    fun `a degenerate or non finite viewBox is rejected with the same code`() {
        // ceil 后 0（PageInfo.Builder 会抛无码 IAE 的输入）。
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(0.0, 32.0, 1f) }.code)
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(-3.0, 32.0, 1f) }.code)
        // 非有限：ceil(NaN)=NaN、ceil(+Inf)=+Inf —— 先于 toInt() 拒（避免饱和成 Int.MAX 的假页）。
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(Double.NaN, 32.0, 1f) }.code)
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            failureOf { visualPdfPageSize(Double.POSITIVE_INFINITY, 32.0, 1f) }.code,
        )
    }

    @Test
    fun `the compatibility budget is inclusive at 14400 points`() {
        // 14400 是兼容预算（R1 §9-11：非规约条文）——边界闭区间，恰好 = 上限放行。
        assertEquals(
            VisualPdfPageSize(VISUAL_PDF_MAX_PAGE_POINTS, 1),
            visualPdfPageSize(VISUAL_PDF_MAX_PAGE_POINTS.toDouble(), 1.0, 1f),
        )
        // +1 即拒（巨型页让查看器拒开的 fail-closed 面）。
        assertEquals(
            PAINTER_PDF_PAGE_BOUNDS,
            failureOf {
                visualPdfPageSize((VISUAL_PDF_MAX_PAGE_POINTS + 1).toDouble(), 1.0, 1f)
            }.code,
        )
        // 48×32 du 在 ptPerDu=400 时 19200×12800——远超声明的栏宽，同样拒。
        assertEquals(PAINTER_PDF_PAGE_BOUNDS, failureOf { visualPdfPageSize(48.0, 32.0, 400f) }.code)
    }

    // ------------------------------------------------------------ 诊断口径

    @Test
    fun `pdf painting failures carry the code prefix and the diagnostics budget`() {
        val failure = failureOf { visualPdfPageSize(48.0, 32.0, 0f) }
        assertTrue(failure.message!!.startsWith("$PAINTER_PDF_PAGE_BOUNDS: "))
        val budget = com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS
        assertEquals(512, budget)
        assertTrue(failure.message!!.length <= "$PAINTER_PDF_PAGE_BOUNDS: ".length + budget)
    }
}
