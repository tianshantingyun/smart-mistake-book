package com.tingyun.smartmistakebook.core.data.visual

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisualDrawOpsPainterPure] 纯计算层的 JVM 门（批次 28 T2；R1 §3.1 分层与 §4 判据 2 的
 * "每条正例配一条错实现必红的负例"）。
 *
 * **不变量**：不触任何 android 符号（被测文件本身零 android 导入——这是 JVM 门无
 * Robolectric 也能成立的结构前提）；Canvas/Paint 应用层与 R1 §4 全量冒烟判据归设备轨
 * `VisualDrawOpsPainterInstrumentedTest`。
 *
 * 覆盖映射：
 * - 矩阵列序正例（切变工作例）+ 原序误装负例（R1 §3.3：两装法映射不同点，像素必不同）；
 * - Double→Float 窄化 fail-closed（PAINTER_BOUNDS）；
 * - 颜色严格词表正/负例（PAINTER_COLOR）；
 * - alpha floor 取整（含 0.5→127 的 round-必红钉）与越界拒绝（PAINTER_ALPHA）；
 * - 诊断预算口径（≤512，同 [com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome]）。
 */
class VisualDrawOpsPainterPureTest {

    private fun failureOf(block: () -> Unit): VisualDrawOpsPaintingException =
        try {
            block()
            throw AssertionError("expected VisualDrawOpsPaintingException")
        } catch (error: VisualDrawOpsPaintingException) {
            error
        }

    // ------------------------------------------------------------ 矩阵列序

    @Test
    fun `a shear matrix loads into android column order`() {
        // 切变工作例（R1 §3.3）：契约 [a,b,c,d,e,f]=[1,1,0,1,0,0]，y' = x + y。
        // 期望 9 值 = [MSCALE_X=1, MSKEW_X=0, MTRANS_X=0, MSKEW_Y=1, MSCALE_Y=1, MTRANS_Y=0,
        //              0, 0, 1]——即 [a, c, e, b, d, f] = [1, 0, 0, 1, 1, 0] 前六位。
        val values = VisualMatrix(a = 1.0, b = 1.0, c = 0.0, d = 1.0, e = 0.0, f = 0.0).toAndroidValues()
        // JUnit 4.13.2 的 float 断言只有带 delta 的重载（无 delta 版会编译失败）；delta=0 即逐位相等。
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f),
            values,
            0f,
        )
        // 工作例断点：契约映射 (2,1) → (2,3)。
        val x = values[0] * 2f + values[1] * 1f + values[2]
        val y = values[3] * 2f + values[4] * 1f + values[5]
        assertEquals(2f, x, 0f)
        assertEquals(3f, y, 0f)
    }

    @Test
    fun `a naive row major loading maps the same point differently`() {
        // 负例钉子（先红判据）：若按契约原序 [a,b,c,d,e,f] 误装 values[0..5]，同点 (2,1)
        // 映射为 (3,2)——与正例 (2,3) 不同，故两装法渲染出的像素阵必须不同（R1 §3.3）。
        val correct = VisualMatrix(a = 1.0, b = 1.0, c = 0.0, d = 1.0, e = 0.0, f = 0.0).toAndroidValues()
        val naive = floatArrayOf(1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        assertFalse(java.util.Arrays.equals(correct, naive))
        assertEquals(3f, naive[0] * 2f + naive[1] * 1f + naive[2], 0f)
        assertEquals(2f, naive[3] * 2f + naive[4] * 1f + naive[5], 0f)
    }

    @Test
    fun `a translation loads into the third column`() {
        // [a,b,c,d,e,f]=[1,0,0,1,10,20] → MTRANS_X=10、MTRANS_Y=20（第三列，非第六/七位）。
        val values = VisualMatrix(a = 1.0, b = 0.0, c = 0.0, d = 1.0, e = 10.0, f = 20.0).toAndroidValues()
        assertArrayEquals(
            floatArrayOf(1f, 0f, 10f, 0f, 1f, 20f, 0f, 0f, 1f),
            values,
            0f,
        )
    }

    @Test
    fun `the perspective row is constant and matrix entries narrow through the bounds gate`() {
        val values = VisualMatrix(a = 2.0, b = 3.0, c = 5.0, d = 7.0, e = 11.0, f = 13.0).toAndroidValues()
        assertEquals(0f, values[6], 0f)
        assertEquals(0f, values[7], 0f)
        assertEquals(1f, values[8], 0f)
        // 界内值安全窄化；越 Float 域即 PAINTER_BOUNDS（校验器 ±1e5 界内不可达，防未来松动）。
        assertEquals(100000f, drawOpsFloat(100000.0), 0f)
        val overflow = failureOf { drawOpsFloat(1e300) }
        assertEquals(PAINTER_BOUNDS, overflow.code)
        val notANumber = failureOf { drawOpsFloat(Double.NaN) }
        assertEquals(PAINTER_BOUNDS, notANumber.code)
    }

    // ------------------------------------------------------------ 颜色严格词表

    @Test
    fun `six and eight digit hex colors parse to argb`() {
        assertEquals(0xFFFF0000.toInt(), parseDrawOpsColor("#ff0000"))
        assertEquals(0xFFFF0000.toInt(), parseDrawOpsColor("#FF0000"))
        // 契约词表是 #RRGGBBAA（alpha 在末两位，校验器 DRAWOPS_COLOR 消息明文），不是 CSS 的
        // #AARRGGBB——"#80123456" = R=80 G=12 B=34 A=56 → ARGB 0x56801234。
        assertEquals(0x56801234, parseDrawOpsColor("#80123456"))
        assertEquals(0x00000000, parseDrawOpsColor("#00000000"))
    }

    @Test
    fun `lenient color words and shapes are rejected with a typed code`() {
        // android Color.parseColor 会放行的词表形态——DrawOps 契约外，必须拒。
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("red") }.code)
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("#fff") }.code)
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("#12345") }.code)
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("#1234567") }.code)
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("#123456789") }.code)
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("#12345g") }.code)
        assertEquals(PAINTER_COLOR, failureOf { parseDrawOpsColor("") }.code)
    }

    // ------------------------------------------------------------ alpha floor

    @Test
    fun `alpha quantizes by floor not rounding`() {
        assertEquals(0, drawOpsAlpha255(0.0))
        assertEquals(255, drawOpsAlpha255(1.0))
        // round 必红钉（R1 §4-判据 2）：floor(0.5×255)=127，round 会给 128。
        assertEquals(127, drawOpsAlpha255(0.5))
        assertEquals(254, drawOpsAlpha255(254.5 / 255.0))
    }

    @Test
    fun `an out of range alpha is rejected with a typed code`() {
        assertEquals(PAINTER_ALPHA, failureOf { drawOpsAlpha255(1.5) }.code)
        assertEquals(PAINTER_ALPHA, failureOf { drawOpsAlpha255(-0.001) }.code)
        assertEquals(PAINTER_ALPHA, failureOf { drawOpsAlpha255(Double.NaN) }.code)
    }

    // ------------------------------------------------------------ 诊断口径

    @Test
    fun `painting failures carry the code prefix and the diagnostics budget`() {
        val failure = failureOf { parseDrawOpsColor("red") }
        assertTrue(failure.message!!.startsWith("PAINTER_COLOR: "))
        // 消息截断到 FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS（引擎折叠口径同源）：
        // 总长 ≤ "CODE: " 前缀 + 预算。
        val budget = com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS
        assertEquals(512, budget)
        assertTrue(failure.message!!.length <= "PAINTER_COLOR: ".length + budget)
    }

    // ------------------------------------------------------------ label DTO（设备轨 fail-closed 夹具的前置面）

    @Test
    fun `label content dto carries explicit line ranges and replay sizing`() {
        val text = VisualLabelContent.Text(
            payload = "a\nb",
            sizeSp = 16f,
            fontScale = 1f,
            heightDu = 24f,
            lines = listOf(VisualLabelLine(0, 1), VisualLabelLine(2, 3)),
        )
        assertEquals(2, text.lines.size)
        // 行高反解不变量：heightDu = 段数 × 行高 → lineHeightDu = heightDu / 段数。
        assertEquals(12f, text.heightDu / text.lines.size, 0f)
        // math 变体可构造（设备轨 PAINTER_LABEL_MATH 负例的夹具面），本批只被拒绝。
        val math = VisualLabelContent.Math(
            box = com.tingyun.smartmistakebook.core.model.MathBox.Leaf(
                width = 10f,
                height = 12f,
                baseline = 9f,
                content = "x",
            ),
            sizeSp = 16f,
            fontScale = 1f,
        )
        assertTrue(math is VisualLabelContent.Math)
    }
}
