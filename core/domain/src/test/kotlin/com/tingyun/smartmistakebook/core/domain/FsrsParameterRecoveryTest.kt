package com.tingyun.smartmistakebook.core.domain

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2-1 的退出门基座（roadmap Wave 2 / KF-01）：**合成数据恢复**。
 *
 * 用已知参数（=默认参数集）前向模拟一张真实的复习账目（每张卡的评级按模型 R 值 Bernoulli
 * 抽样），再让优化器在只看 (间隔, 结果) 的条件下重新拟合。它消灭的失败是"拟合器跑得通
 * 但什么也没学到"——w20 接线之前 loss 对 decay 的梯度恒为零，任何此类回归都会让
 * w20 偏离真值、本测试当场红。
 *
 * 数据规模：800 卡 × 8 次复习（间隔 1→120 天，确定性）+ 固定种子标签（确定性）；
 * 800 个末态监督点对 15 个自由维度（w0..w2、w4..w14、w20）是充分统计量。
 */
class FsrsParameterRecoveryTest {

    private val trueDecay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]

    @Test
    fun `fitted w20 recovers the generating decay from synthetic review data`() {
        val samples = syntheticReviews(cardCount = 800, seed = 20261001L)

        // 灵敏度前提：w20 偏离真值必须移动同批数据上的 log loss（否则恢复无从谈起）。
        val perturbedParameters = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also {
            it[20] = FsrsScheduleMath.DEFAULT_PARAMETERS[20] + 0.10
        }
        val lossAtTruth = FsrsParameterOptimizer.validationLogLoss(samples, FsrsScheduleMath.DEFAULT_PARAMETERS)!!
        val lossAtPerturbed =
            FsrsParameterOptimizer.validationLogLoss(samples, perturbedParameters)!!
        assertNotEquals("loss 必须随 w20 变化", lossAtTruth, lossAtPerturbed, 1e-9)

        val result = FsrsParameterOptimizer.optimize(samples, iterations = 12)

        assertEquals(FsrsParameterOptimizer.Mode.FULL_FIT, result.mode)
        assertEquals(
            "拟合后的 decay 必须回到真值 ±0.08（ε 为本测试按数据规模调定的容差）",
            FsrsScheduleMath.DEFAULT_PARAMETERS[20],
            result.parameters[20],
            0.08,
        )
        // 采纳门同款比较：拟合参数在验证桶上的纯 BCE 不得劣于默认参数。
        assertTrue(
            "fitted ${result.validationLogLoss} should be ≈ default $lossAtTruth",
            result.validationLogLoss <= lossAtTruth + 0.02,
        )
    }

    /**
     * 用真值参数前向模拟一摞卡的复习历史。间隔固定（1..120 天递增），评级按当次
     * 预测 R 值 Bernoulli 抽样（GOOD/AGAIN 两档——产品的实际评级词汇）；行的时间戳
     * 随卡错开，让全局时间分位切分能把训练/验证桶混合到不同卡上。
     */
    private fun syntheticReviews(cardCount: Int, seed: Long): List<ReviewSample> {
        val rng = Random(seed)
        val intervals = longArrayOf(1, 3, 7, 14, 30, 60, 120)
        val decay = trueDecay
        val day = AlgorithmConstants.DAY_MILLIS
        return (0 until cardCount).flatMap { card ->
            var stability = FsrsScheduleMath.initialStability(FsrsRating.GOOD)
            var difficulty = FsrsScheduleMath.clampDifficulty(
                FsrsScheduleMath.initialDifficulty(FsrsRating.GOOD),
            )
            var at = 10_000L * day + card * day
            buildList {
                add(
                    ReviewSample(
                        practiceUnitId = "unit-$card",
                        reviewedAtEpochMillis = at,
                        rating = FsrsRating.GOOD,
                        deltaTDays = null,
                    ),
                )
                for (gap in intervals) {
                    at += gap * day
                    val retrievability = FsrsScheduleMath.retention(gap.toDouble(), stability, decay)
                    val correct = rng.nextDouble() < retrievability
                    val rating = if (correct) FsrsRating.GOOD else FsrsRating.AGAIN
                    add(
                        ReviewSample(
                            practiceUnitId = "unit-$card",
                            reviewedAtEpochMillis = at,
                            rating = rating,
                            deltaTDays = gap.toDouble(),
                        ),
                    )
                    stability = if (correct) {
                        FsrsScheduleMath.nextRecallStability(difficulty, stability, retrievability, FsrsRating.GOOD)
                    } else {
                        FsrsScheduleMath.nextForgetStability(difficulty, stability, retrievability)
                    }.let { FsrsScheduleMath.clampStability(it) }
                    difficulty = FsrsScheduleMath.nextDifficulty(difficulty, rating)
                }
            }
        }
    }
}
