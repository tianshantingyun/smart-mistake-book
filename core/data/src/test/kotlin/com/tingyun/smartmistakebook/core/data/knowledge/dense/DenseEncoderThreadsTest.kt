package com.tingyun.smartmistakebook.core.data.knowledge.dense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端侧推理线程数的**解析逻辑**（纯函数，JVM 可钉）。
 *
 * 为什么要单测这条：线程数以前是硬编码常量、生产不传，于是"到底几线程"只存在于真机行为里
 * ——而模型推理的 `.so` 不在 JVM 里，解析写错了 JVM 侧没有任何门能发现（只有仪表化才跑得到，
 * 且仪表化只打印不断言）。本测试把 `resolveEncoderThreads` 的三条口径钉在 JVM 上：
 * 上限 4（判据坐标）、低核数跟随、异常核数报告不产生 0/负数。
 *
 * **不测 LiteRT 行为**（线程数是否真的加速推理）：那要真机 + XNNPACK，见
 * `DenseFirstUseCostInstrumentedTest` 与 `tools/dense_build/README.md` §9.4（宿主上这张图
 * 基本串行，1/2/4/8 四列差 ≤5% ⇒ 宿主不可解析）。
 */
class DenseEncoderThreadsTest {

    @Test
    fun capsAtFourOnManyCoreDevices() {
        // 判据坐标是 4 线程；核数再多也不超过上限（宿主 32 核 ⇒ 仍然 4）。
        assertEquals(4, resolveEncoderThreads(8))
        assertEquals(4, resolveEncoderThreads(16))
        assertEquals(4, resolveEncoderThreads(32))
        assertEquals(4, resolveEncoderThreads(Int.MAX_VALUE))
    }

    @Test
    fun followsCoreCountBelowTheCap() {
        // 低核设备按实际核数走（不是一律 4）——"按核数右尺寸"里的右尺寸就是这条。
        assertEquals(1, resolveEncoderThreads(1))
        assertEquals(2, resolveEncoderThreads(2))
        assertEquals(3, resolveEncoderThreads(3))
        assertEquals(4, resolveEncoderThreads(4))
    }

    @Test
    fun neverReturnsLessThanOneOnBrokenCoreReports() {
        // 0/负数是核数报告的异常值，不是"无限省线程"的理由：LiteRT 的 num_threads 要求 ≥1
        // （`ai_edge_litert` 的绑定对 <1 直接抛），交 0 下去就是把"不可用"变成"抛异常回退纯词面"。
        assertEquals(1, resolveEncoderThreads(0))
        assertEquals(1, resolveEncoderThreads(-1))
        assertEquals(1, resolveEncoderThreads(Int.MIN_VALUE))
    }

    @Test
    fun productionDefaultIsTheResolvedValueNotAHardcodedConstant() {
        // 钉住"生产默认就是解析结果"：以前 DEFAULT_THREADS 是写死的 2，改了核数也还是 2；
        // 有人把这一行改回常量而没留解析，本断言当场红（两端都在运行时算，与机器无关）。
        val expected = resolveEncoderThreads(Runtime.getRuntime().availableProcessors())
        assertEquals(expected, LiteRtDenseQueryEncoder.DEFAULT_THREADS)
        assertTrue(
            "解析结果必须在 [MIN_ENCODER_THREADS, MAX_ENCODER_THREADS] 内，实测 $expected",
            expected in MIN_ENCODER_THREADS..MAX_ENCODER_THREADS,
        )
    }
}
