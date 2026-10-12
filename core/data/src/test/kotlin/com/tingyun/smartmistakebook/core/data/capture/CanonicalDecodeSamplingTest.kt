package com.tingyun.smartmistakebook.core.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-32：规范资产解码的采样率（纯函数，JVM 面可测）。
 *
 * 消灭的失败：改前按整幅 ARGB_8888 解码，3072×4096 一张就是 50.3MB，批量导入 3 页叠加
 * 把 native 堆顶到 190.9MB（`sample_import_run.txt` 00:49:08 `Native Heap Alloc
 * 195,449 KB`，而同期 Dalvik Alloc 只有 13MB——约束不在 Java 堆）。
 *
 * 这里钉的是采样率的**边界**：超预算才降（按 2 的幂）、落到预算内就停、已足够小不放大
 * （放大既费内存又改分辨率）、短边 1 像素的退化图不降到 0。
 */
class CanonicalDecodeSamplingTest {

    private val budget = 2_048

    @Test
    fun `a 12_6MP photo decodes at half edge and a quarter of the pixels`() {
        val sample = boundedDecodeSampleSize(width = 3_072, height = 4_096, maxEdgePx = budget)
        assertEquals("3072×4096 → 1536×2048", 2, sample)
        assertEquals(1_536, 3_072 / sample)
        assertEquals(2_048, 4_096 / sample)
        assertTrue(
            "单份像素缓冲从 50.3MB 降到 12.6MB",
            1_536L * 2_048L * 4L < (3_072L * 4_096L * 4L) / 3,
        )
    }

    @Test
    fun `an image at or under the budget is never resampled`() {
        assertEquals("长边恰好等于预算", 1, boundedDecodeSampleSize(2_048, 1_536, budget))
        assertEquals("竖图恰好等于预算", 1, boundedDecodeSampleSize(1_536, 2_048, budget))
        assertEquals("预览级小图不放大", 1, boundedDecodeSampleSize(1_600, 1_200, budget))
        assertEquals("正方形小图不放大", 1, boundedDecodeSampleSize(64, 64, budget))
        assertEquals("1×1", 1, boundedDecodeSampleSize(1, 1, budget))
    }

    @Test
    fun `sampling is a power of two and every sampled edge stays inside the budget`() {
        listOf(
            3_072 to 4_096,
            4_000 to 800,
            8_000 to 6_000,
            8_192 to 1_953, // 尺寸门上沿（MAX_DIMENSION=8_192）
            1_953 to 8_192,
        ).forEach { (width, height) ->
            val sample = boundedDecodeSampleSize(width, height, budget)
            assertTrue(
                "$width×$height 的采样率必须是 2 的幂：$sample",
                sample > 0 && (sample and (sample - 1)) == 0,
            )
            assertTrue(
                "$width×$height 采样后长边必须在预算内：${maxOf(width, height) / sample}",
                maxOf(width, height) / sample <= budget,
            )
            assertTrue("$width×$height 不得放大", sample >= 1)
        }
    }

    @Test
    fun `a one-pixel-tall degenerated image is left alone instead of sampled to zero`() {
        assertEquals(
            "短边只有 1 像素时再加倍会让解码器拿到 0 尺寸",
            1,
            boundedDecodeSampleSize(width = 8_193, height = 1, maxEdgePx = budget),
        )
        assertEquals(1, boundedDecodeSampleSize(width = 1, height = 9_000, maxEdgePx = budget))
    }

    @Test
    fun `invalid budgets and bounds fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            boundedDecodeSampleSize(width = 0, height = 10, maxEdgePx = budget)
        }
        assertThrows(IllegalArgumentException::class.java) {
            boundedDecodeSampleSize(width = 10, height = 10, maxEdgePx = 0)
        }
    }
}
