package com.tingyun.smartmistakebook.visualjs

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * 批次 38 M-03 负向矩阵 N1/N2（R2 `docs/research/2026-11-01-measure-artifact-and-budget.md`
 * §4.3）：度量环境字号/字重门在**边界与 NaN** 上的 fail-closed 判据。
 *
 * 12..48 sp 与 fontScale > 0 的门**已落**（`VisualMeasureEnvironment.init` require，
 * 批次 27 T2）；既有 `VisualContentMeasuringTest` 覆盖了 8f/64f/0f 等粗界，本文件补齐
 * R2 §4.3 点名的精确边界（11.9/48.1/NaN/-0.0）与**恰达界收下**（12f/48f 合法）——
 * 回归不放宽，界内新拒绝或界外放行都属门被改动，立即红。
 */
class VisualMeasureEnvironmentBudgetTest {

    private fun environment(
        density: Float = 2f,
        fontScale: Float = 1f,
        sizeSp: Float = 16f,
    ): VisualMeasureEnvironment = VisualMeasureEnvironment(
        density = density,
        fontScale = fontScale,
        typefaceId = VisualMeasureEnvironment.SYSTEM_DEFAULT_TYPEFACE_ID,
        localeTag = "zh-CN",
        sizeSp = sizeSp,
    )

    private fun assertRejected(block: () -> Any?) {
        try {
            block()
            fail("expected the environment gate to reject this reading")
        } catch (expected: IllegalArgumentException) {
            // 预期路径（A1 fail-closed：环境读数不可信构造即拒）。
        }
    }

    // ------------------------------------------------ N1：sizeSp 边界与 NaN

    @Test
    fun `sizeSp just below the floor and just above the ceiling is rejected`() {
        assertRejected { environment(sizeSp = 11.9f) } // < MIN_SIZE_SP(12)
        assertRejected { environment(sizeSp = 48.1f) } // > MAX_SIZE_SP(48)
    }

    @Test
    fun `sizeSp NaN is rejected by the range gate`() {
        // NaN in 12f..48f 恒 false：区间 require 天然拒绝 NaN（不另行加 NaN 特判）。
        assertRejected { environment(sizeSp = Float.NaN) }
    }

    @Test
    fun `sizeSp at the exact boundaries is accepted`() {
        assertEquals(12f, environment(sizeSp = 12f).sizeSp, 0f)
        assertEquals(48f, environment(sizeSp = 48f).sizeSp, 0f)
    }

    // ------------------------------------------------ N2：fontScale 边界与 NaN

    @Test
    fun `fontScale NaN and negative zero are rejected`() {
        assertRejected { environment(fontScale = Float.NaN) }
        // -0.0 > 0f 为 false：零/负（含负零）一致拒绝——fontScale 放大字号是唯一合法语义。
        assertRejected { environment(fontScale = -0.0f) }
        assertRejected { environment(fontScale = -1f) }
    }

    @Test
    fun `density NaN is rejected`() {
        assertRejected { environment(density = Float.NaN) }
    }
}
