package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批次 44 F-055/F-057 回归：2h soak 点"复习安排"滑杆端点后进程 FATAL。
 *
 * 崩溃链（`docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-logcat.txt`）：
 * monkey 点滑杆 → `SliderState.dispatchRawDelta` → `SchedulingSettingsScreen.kt:74`
 * `options.copy(desiredRetention = value.toDouble())` →
 * `SchedulingOptions.<init>` 的 `require(desiredRetention in 0.7..0.97)` 抛
 * IllegalArgumentException: Desired retention must be within the supported 0.7..0.97 range。
 *
 * 成因不是滑杆越界，而是 Float→Double 的表示误差：`0.97f.toDouble()` =
 * 0.9700000286102295，`0.7f.toDouble()` = 0.6999999880790710，两端都在 band 外。
 * 这些用例钉住"滑杆读数 → 落库值"的映射本身，崩溃路径从此有可复跑的回归面。
 */
class SchedulingRetentionSliderValueTest {

    @Test
    fun sliderEndpointsLandExactlyOnTheSupportedBand() {
        assertEquals(0.7, retentionFromSlider(0.7f), 0.0)
        assertEquals(0.97, retentionFromSlider(0.97f), 0.0)
    }

    @Test
    fun sliderEndpointsProduceAConstructibleSchedulingOptions() {
        // 修复前这两行都以 IllegalArgumentException 结束（soak 崩溃点）。
        assertEquals(
            0.7,
            SchedulingOptions(desiredRetention = retentionFromSlider(0.7f)).desiredRetention,
            0.0,
        )
        assertEquals(
            0.97,
            SchedulingOptions(desiredRetention = retentionFromSlider(0.97f)).desiredRetention,
            0.0,
        )
    }

    @Test
    fun valuesOutsideTheSliderRangeAreClampedInsteadOfCrashing() {
        assertEquals(0.7, retentionFromSlider(-1f), 0.0)
        assertEquals(0.97, retentionFromSlider(1.5f), 0.0)
    }

    @Test
    fun inBandSliderValuesArePreserved() {
        // 0.9f 的 Double 表示是 0.8999999761581421，band 内即原样落库。
        assertEquals(0.9, retentionFromSlider(0.9f), 1e-7)
    }
}
