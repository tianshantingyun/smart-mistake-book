package com.tingyun.smartmistakebook.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P8（批次 2 规格 §1.4 锚 1）：档位切点与区间文案的 JVM 用例。
 *
 * 切点值钉死（变异即红）；标签在**恰好等于切点**处换档；区间文案的取整与"宽区间不报数"。
 */
class MasteryBandsTest {

    @Test
    fun `cut points are the documented ones`() {
        // 改数值应当是有意的：0.85 = 裁决 13 修订保留的展示带边界；0.4 = 现状值。
        assertEquals(0.85, MASTERY_STRONG_THRESHOLD, 0.0)
        assertEquals(0.4, MASTERY_FAIR_THRESHOLD, 0.0)
    }

    @Test
    fun `band labels switch exactly at the cut points`() {
        assertEquals("较稳", masteryBandLabel(MASTERY_STRONG_THRESHOLD))
        assertEquals("一般", masteryBandLabel(MASTERY_STRONG_THRESHOLD - 1e-6))
        assertEquals("一般", masteryBandLabel(MASTERY_FAIR_THRESHOLD))
        assertEquals("薄弱", masteryBandLabel(MASTERY_FAIR_THRESHOLD - 1e-6))

        // 保持率档共用同一对切点（有意的设计：两个量都在 [0,1]、语义都是"有多牢"）。
        assertEquals("记忆较稳", retentionBandLabel(MASTERY_STRONG_THRESHOLD))
        assertEquals("记忆减弱", retentionBandLabel(MASTERY_STRONG_THRESHOLD - 1e-6))
        assertEquals("记忆减弱", retentionBandLabel(MASTERY_FAIR_THRESHOLD))
        assertEquals("记忆模糊", retentionBandLabel(MASTERY_FAIR_THRESHOLD - 1e-6))
    }

    @Test
    fun `interval label rounds to tenths and refuses to report wide spans`() {
        // 无证据（映射侧传 null）。
        assertEquals("证据还少", masteryIntervalLabel(0.2, null))
        // 宽区间（>= 0.5）= 证据少：报数会被读成确信。
        assertEquals("证据还少", masteryIntervalLabel(0.1, 0.6))
        // 规格 §1.3 的两个示例。
        assertEquals("约 6–10 成", masteryIntervalLabel(0.6058, 0.9854))
        assertEquals("约 2–7 成", masteryIntervalLabel(0.21, 0.62))
        // lo == hi 的单词形（含浮点容差：0.7*10 在 IEEE 下是 6.999…）。
        assertEquals("约 7 成", masteryIntervalLabel(0.7, 0.7))
    }
}
