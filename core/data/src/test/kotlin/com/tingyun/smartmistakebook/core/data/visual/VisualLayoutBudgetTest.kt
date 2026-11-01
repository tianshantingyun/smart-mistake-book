package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 批次 38 M-03 排版预算的 JVM 判据（R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §4.3 负向矩阵的**纯函数**子集：
 * N4 累计像素门 + N6 的门函数半边）。
 *
 * **判据口径**：累计按 **du**（每盒 `ceil(widthDu) × ceil(heightDu)`）；字节门按
 * 真实 px × bytesPerPixel；两门独立判、恰达上限不拒（契约"≤ 4 MP / ≤ 16 MiB"）。
 * 设备半边（N5 满幅预览拒绝 / N6 真实 createBitmap 分配前拒绝 / N7 合计越界）随 D-04
 * 预览分配点落地（`NativeContentLayoutInstrumentedTest`，R2 §4.3 测试位），本批不写
 * ——分配点尚不存在，写了也是无的放矢。
 *
 * 先红后绿：常量与门在实现前落盘即红（`VisualLayoutBudget` 缺失时编译不过）；本环境
 * 不运行 Gradle，执行归门禁串行双门（结果登记 UNVERIFIED）。
 */
class VisualLayoutBudgetTest {

    // ------------------------------------------------ 常量（两门独立、防"发现等价"合并）

    @Test
    fun `the pixel budget constant is exactly 4 MP in du`() {
        assertEquals(4_194_304L, VisualLayoutBudget.MAX_TOTAL_LAYOUT_PIXELS_DU)
        assertEquals(2048L * 2048L, VisualLayoutBudget.MAX_TOTAL_LAYOUT_PIXELS_DU)
    }

    @Test
    fun `the bitmap byte budget constant is exactly 16 MiB`() {
        assertEquals(16_777_216L, VisualLayoutBudget.MAX_BITMAP_BYTES)
        assertEquals(16L * 1024L * 1024L, VisualLayoutBudget.MAX_BITMAP_BYTES)
    }

    @Test
    fun `the two budget constants stay separate declarations`() {
        // 数字自洽注记（R2 §4.2）：16 MiB ÷ 4 B 恰好等于 4 MP——**巧合级对齐**，不是同一条
        // 门。若有人"发现等价"把两常量合并/互相推导，本断言（独立声明的字面）最先红。
        assertEquals(
            VisualLayoutBudget.MAX_BITMAP_BYTES / VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL,
            VisualLayoutBudget.MAX_TOTAL_LAYOUT_PIXELS_DU,
        )
        assertEquals(4, VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL)
    }

    // ------------------------------------------------ N4：累计像素门（du 口径）

    private fun measured(widthDu: Float, heightDu: Float) = VisualMeasuredContent(
        contentId = "c-x",
        widthDu = widthDu,
        heightDu = heightDu,
        baselineDu = 0f,
        fontSizeSp = 16f,
        digest = "d",
    )

    @Test
    fun `empty and zero-area batches are admitted`() {
        assertNull(VisualLayoutBudget.transactionBudgetDiagnostic(emptyList()))
        assertNull(
            VisualLayoutBudget.transactionBudgetDiagnostic(listOf(measured(0f, 0f))),
        )
    }

    @Test
    fun `the exact 4 MP total is admitted and one pixel more is refused`() {
        // 恰达上限不拒（契约"≤"）：2048² du = 4,194,304。
        assertNull(
            VisualLayoutBudget.transactionBudgetDiagnostic(listOf(measured(2048f, 2048f))),
        )
        // 2048² + 1：1×1 的第二盒补上最后一像素 → 有界拒绝。
        val diagnostic = VisualLayoutBudget.transactionBudgetDiagnostic(
            listOf(measured(2048f, 2048f), measured(1f, 1f)),
        )
        assertNotNull(diagnostic)
        assertTrue(
            "the diagnostic must carry the LAYOUT_PIXEL_BUDGET code, got: $diagnostic",
            diagnostic!!.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET),
        )
        assertTrue(diagnostic.contains("4194304"))
    }

    @Test
    fun `a full-canvas layout box alone exceeds the transaction budget`() {
        // 参照数（R2 §4.2）：画布边长上限 4096 du → 满幅 = 16,777,216 du² = 16 MP = 4× 预算。
        // 满幅内容必拒——预览/整幅渲染必须缩采样（D-04 接线点的硬上界）。
        val diagnostic = VisualLayoutBudget.transactionBudgetDiagnostic(
            listOf(measured(4096f, 4096f)),
        )
        assertNotNull(diagnostic)
    }

    @Test
    fun `multiple wide text boxes sum across entries`() {
        // "累计"语义：单盒都在预算内、合计越界即拒（N4 的"多条宽文本"形态，JVM 纯函数面）。
        val threeQuarters = measured(3072f, 2048f) // 6,291,456 du²（1.5× 预算，单盒即越）
        assertNotNull(VisualLayoutBudget.transactionBudgetDiagnostic(listOf(threeQuarters)))
        // 单盒 1/2 预算 × 2 条 = 恰达；再补 1 像素 → 越。
        val half = measured(2048f, 1024f) // 2,097,152 du²
        assertNull(VisualLayoutBudget.transactionBudgetDiagnostic(listOf(half, half)))
        assertNotNull(
            VisualLayoutBudget.transactionBudgetDiagnostic(listOf(half, half, measured(1f, 1f))),
        )
    }

    @Test
    fun `fractional box edges are ceiled per box`() {
        // R2 §4.2 口径：ceil(widthDu) × ceil(heightDu)。1.2×1.2 → 2×2 = 4（不是 1.44）。
        assertEquals(4.0, VisualLayoutBudget.boxPixelsDu(1.2f, 1.2f), 0.0)
        // 两盒各 0.6×0.6：每盒 ceil 后 1 像素（不是合计 0.72 后取整）。
        assertNull(
            VisualLayoutBudget.transactionBudgetDiagnostic(
                listOf(measured(0.6f, 0.6f), measured(0.6f, 0.6f)),
            ),
        )
        assertEquals(2.0, VisualLayoutBudget.totalLayoutPixelsDu(listOf(measured(0.6f, 0.6f), measured(0.6f, 0.6f))), 0.0)
    }

    @Test
    fun `broken box dimensions are refused at the pure function`() {
        assertIllegalArgument { VisualLayoutBudget.boxPixelsDu(-1f, 1f) }
        assertIllegalArgument { VisualLayoutBudget.boxPixelsDu(1f, Float.NaN) }
        assertIllegalArgument { VisualLayoutBudget.boxPixelsDu(Float.POSITIVE_INFINITY, 1f) }
    }

    // ------------------------------------------------ N6（门函数半边）：单 bitmap 字节门

    @Test
    fun `the exact 16 MiB allocation is admitted and one byte more is refused`() {
        // 恰达：2048×2048×4 = 16,777,216 = 上限（"≤"语义）。
        VisualLayoutBudget.requireBitmapBytesWithinBudget(2048, 2048, VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL)
        // N6 参照数：2050×2050×4 = 16,810,000 > 16 MiB → 分配前拒绝，异常携带码。
        try {
            VisualLayoutBudget.requireBitmapBytesWithinBudget(2050, 2050, VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL)
            fail("a 16,810,000-byte allocation must be refused before creation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(
                "the refusal must carry the BITMAP_BYTE_BUDGET code, got: ${expected.message}",
                expected.message!!.startsWith(VisualLayoutBudget.BITMAP_BYTE_BUDGET),
            )
        }
    }

    @Test
    fun `the byte gate stays independent of the pixel budget`() {
        // RGB_565（2 B/px）下 2896×2896 = 8,386,816 px ≤ 4 MP 但 ×2 = 16,773,632 B ≤ 16 MiB——
        // 恰都在内；2897×2897×2 = 16,784,618 B > 16 MiB 而像素 8,392,609 > 4 MP 同越。
        // 真正两门分叉的形态是小字节大像素或反例——这里钉住字节门只看字节：
        // 4096×4096×1（1 B/px）= 16,777,216 B 恰达字节上限（像素 16 MP 远超像素门），
        // 字节门必须放行（它不管像素），像素门另行拒绝——两门独立判的执行面证据。
        VisualLayoutBudget.requireBitmapBytesWithinBudget(4096, 4096, 1)
    }

    @Test
    fun `broken allocation parameters are refused`() {
        assertIllegalArgument {
            VisualLayoutBudget.requireBitmapBytesWithinBudget(-1, 10, VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL)
        }
        assertIllegalArgument {
            VisualLayoutBudget.requireBitmapBytesWithinBudget(10, 10, 0)
        }
    }

    @Test
    fun `bitmap byte math does not overflow int multiplication`() {
        // Int 乘法会溢出的量级（>2^31）：Long 通道必须给出正确字节数。
        assertEquals(68_719_476_736L, VisualLayoutBudget.bitmapBytes(131_072, 131_072, 4))
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // 预期路径。
        }
    }
}
