package com.tingyun.smartmistakebook.visualjs

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-78 ④ 的**跨侧钉**（批次 29 T5）：度量 Paint 与回放 textPaint 必须共享同一 flags，
 * 且该值进入度量环境指纹。
 *
 * **它消灭的具体失败**：度量侧与回放侧各自演化 flags（AA/locale/subpixel），内容摘要不变、
 * 度量与回放静默失配（冻结布局的 advance/字形与重放不同）。三面在此一次比对：
 * 1. 真实度量 Paint 的 flags（[PaintVisualTextMeasureBackend.paintFlagsForTest]）；
 * 2. 回放 textPaint 的 flags（`core:data` painter 构造时显式 `flags = REPLAY_TEXT_PAINT_FLAGS`；
 *    该侧 JVM 测钉常量、设备测钉真实 textPaint 的 flags——跨模块 internal 不可见，本文件
 *    只钉平台默认**不是** 0 这一事实）；
 * 3. 环境指纹的 `paintFlags=` 段（JVM 测钉字面 `paintFlags=0`）。
 *
 * **平台 `Paint()` 默认 flags 是 1283**（AA|FILTER_BITMAP|DEV_KERN|EMBEDDED_BITMAP，
 * 批次 29 设备证据 R1）。两侧都必须**显式**置 0，不能假设"默认即 0"。
 *
 * 设备轨 UNVERIFIED 时本文件只作判据存在性，不当作已过。
 */
@RunWith(AndroidJUnit4::class)
class VisualMeasureReplayPaintFlagsInstrumentedTest {

    private fun environment(paintFlags: Int = VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS) =
        VisualMeasureEnvironment(
            density = 2f,
            fontScale = 1f,
            typefaceId = VisualMeasureEnvironment.SYSTEM_DEFAULT_TYPEFACE_ID,
            localeTag = "zh-CN",
            sizeSp = VisualMeasureEnvironment.CANONICAL_SIZE_SP,
            paintFlags = paintFlags,
        )

    @Test
    fun theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags() {
        val environment = environment()
        val backend = PaintVisualTextMeasureBackend.forEnvironment(environment)
        // 度量侧在 forEnvironment 内显式装配 DEFAULT_PAINT_FLAGS（0）；回放侧的
        // REPLAY_TEXT_PAINT_FLAGS 由 core:data 设备测钉死（跨模块 internal 不可见）。
        assertEquals(VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS, backend.paintFlagsForTest())
        assertEquals(0, environment.paintFlags)
        assertTrue(environment.fingerprint().endsWith("|paintFlags=${environment.paintFlags}"))
        // 平台默认不是 0（API 34 = 1283）：把"为什么必须显式置值"钉成可执行事实，
        // 防止有人把 DEFAULT_PAINT_FLAGS 改回"平台默认即 0"的错误假设（KD-78 ④ R1）。
        assertNotEquals(0, Paint().flags)
    }

    @Test
    fun aFingerprintThatDisagreesWithTheActualPaintFailsClosed() {
        // 环境声称 paintFlags=1 而实际度量 Paint 被显式装配为 0：装配即拒，
        // 不写"声称值"进摘要（校验比的是实际 Paint，不是 environment 的自述）。
        try {
            PaintVisualTextMeasureBackend.forEnvironment(environment(paintFlags = 1))
            fail("a fingerprint/paint flags mismatch must fail closed")
        } catch (expected: VisualMeasurementEnvironmentException) {
            assertTrue(expected.message!!.contains("paintFlags=1"))
        }
    }
}
