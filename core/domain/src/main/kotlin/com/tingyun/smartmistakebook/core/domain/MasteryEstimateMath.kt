package com.tingyun.smartmistakebook.core.domain

import kotlin.math.sqrt

/**
 * 掌握度估计数学（W3-1/W3-2，KF-09/10）：Jeffreys β-二项点估计 + Wilson 区间。
 *
 * **判据不在这里**——2026-09-30 裁定（台账「裁决 13 · 修订」）后 MASTERED = 知识点自己的记忆
 * 状态（稳定度 ≥ 21 天 ∧ 当前召回概率 ≥ 0.9，见 [ClearlyMasteredForSkipPolicy]）。本对象只服务
 * **展示层**：`masteryScore` = 点估计、`conservativeMasteryScore` = Wilson 下界、
 * 区间（上下界）宽度 = KF-20 的"透明度"。
 *
 * 公式与 fix-plan KF-10 逐字一致（z = 1.96）：
 * ```
 * n  = s + f
 * p̂  = (s + α) / (n + α + β)                                  # Jeffreys α=β=0.5
 * 下界 = ( p̂ + z²/(2n) − z·√( p̂(1−p̂)/n + z²/(4n²) ) ) / (1 + z²/n)   # n > 0
 * 上界 = ( p̂ + z²/(2n) + z·√( p̂(1−p̂)/n + z²/(4n²) ) ) / (1 + z²/n)   # 截断到 [0, 1]
 * n = 0 → 先验均值（0.5）
 * ```
 *
 * 手算对照表（python 转写公式，测试里钉住）：s=1,f=0 → p̂=0.75、下界=0.117906、上界=0.985366；
 * s=8,f=0 → p̂=0.944444、下界=0.605809；s=1,f=1 → p̂=0.5、下界=0.094529。
 */
object MasteryEstimateMath {
    /** Wilson 区间（下界, 上界），用于展示层的"区间=透明度"。 */
    data class Interval(val lower: Double, val upper: Double) {
        init {
            require(lower <= upper) { "Interval lower bound must not exceed its upper bound" }
            require(lower >= 0.0 && upper <= 1.0) { "Interval must lie within [0, 1]" }
        }
    }

    /** β-二项点估计 p̂ = (s + α) / (n + α + β)，Jeffreys 先验 (0.5, 0.5)。 */
    fun pointEstimate(successWeight: Double, failureWeight: Double): Double {
        requireWeights(successWeight, failureWeight)
        val alpha = AlgorithmConstants.Mastery.JEFFREYS_ALPHA
        val beta = AlgorithmConstants.Mastery.JEFFREYS_BETA
        return (successWeight + alpha) / (successWeight + failureWeight + alpha + beta)
    }

    /** Wilson 区间（KF-10 公式，p̂ 取 Jeffreys 点估计）；n = 0 → 先验均值（不做区间）。 */
    fun interval(successWeight: Double, failureWeight: Double): Interval {
        requireWeights(successWeight, failureWeight)
        val n = successWeight + failureWeight
        val priorMean =
            AlgorithmConstants.Mastery.JEFFREYS_ALPHA /
                (AlgorithmConstants.Mastery.JEFFREYS_ALPHA + AlgorithmConstants.Mastery.JEFFREYS_BETA)
        if (n <= 0.0) {
            return Interval(priorMean, priorMean)
        }
        val z = AlgorithmConstants.Mastery.WILSON_Z
        val zSquared = z * z
        val p = pointEstimate(successWeight, failureWeight)
        val denominator = 1.0 + zSquared / n
        val center = (p + zSquared / (2.0 * n)) / denominator
        val halfWidth =
            (z / denominator) * sqrt(p * (1.0 - p) / n + zSquared / (4.0 * n * n))
        return Interval(
            lower = (center - halfWidth).coerceIn(0.0, 1.0),
            upper = (center + halfWidth).coerceIn(0.0, 1.0),
        )
    }

    /** 保守掌握度 = Wilson 下界（`conservativeMasteryScore` 的唯一来源）。 */
    fun lowerBound(successWeight: Double, failureWeight: Double): Double =
        interval(successWeight, failureWeight).lower

    private fun requireWeights(successWeight: Double, failureWeight: Double) {
        require(successWeight.isFinite() && successWeight >= 0.0) {
            "Success weight must be finite and non-negative"
        }
        require(failureWeight.isFinite() && failureWeight >= 0.0) {
            "Failure weight must be finite and non-negative"
        }
    }
}
