package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import kotlin.math.pow

/**
 * Weakness-input smoothing (spec mastery-scheduling §2.18): the planner reads
 * the conservative mastery score through a seven-day half-life EMA over the
 * knowledge node's independent-correct observations, so a single strong or
 * weak day cannot swing the review queue.
 *
 * The EMA is the decay-weighted share of the node's evidence whose calibration
 * support still stands. The numerator decays each supported observation by
 * 2^(−age/7d) while the denominator keeps the raw evidence weights, so age
 * cannot cancel out: stale evidence pulls the smoothed score down even when
 * every observation is still nominally supported. (Before 2026-09-09 both the
 * numerator and the denominator decayed, which made the ratio identically 1
 * for uniformly-supported nodes and silently halved every weakness signal —
 * see the audit note on `MasterySmoothing`.)
 */
object MasterySmoothing {
    const val HALF_LIFE_DAYS = 7.0
    const val POINT_ESTIMATE_WEIGHT = 0.5

    fun smoothedMasteryScore(state: KnowledgeMasteryState, atEpochMillis: Long): Double =
        evaluate(state, atEpochMillis).score

    /**
     * S15（W4-1 排程放大）：observations **单遍**扫描同时产出两个读侧值——
     * 平滑分与"是否存在仍受支持的观察"（校准支持判定）。两个值原本各扫一遍
     * （`ReviewPlannerV2` 里 `.none { supported }` 一遍、平滑分一遍，最弱掌握度
     * 又一遍），这里合并；公式、累加顺序与逐遍版本**逐位相同**。
     */
    fun evaluate(state: KnowledgeMasteryState, atEpochMillis: Long): Evaluation {
        val conservative = state.conservativeMasteryScore
        val observations = state.independentCorrectObservations
        if (observations.isEmpty()) {
            return Evaluation(score = conservative, hasSupportedObservation = false)
        }
        var totalWeight = 0.0
        var supportedDecayedWeight = 0.0
        var hasSupportedObservation = false
        for (observation in observations) {
            val ageDays = (atEpochMillis - observation.occurredAtEpochMillis)
                .coerceAtLeast(0)
                .toDouble() / DAY_MILLIS
            totalWeight += observation.evidenceWeight
            if (observation.calibrationSupportAt(atEpochMillis) == CalibrationSupport.SUPPORTED) {
                hasSupportedObservation = true
                supportedDecayedWeight +=
                    observation.evidenceWeight * 2.0.pow(-ageDays / HALF_LIFE_DAYS)
            }
        }
        if (totalWeight <= 0.0) {
            return Evaluation(score = conservative, hasSupportedObservation = hasSupportedObservation)
        }
        val ema = (supportedDecayedWeight / totalWeight).coerceIn(0.0, 1.0)
        return Evaluation(
            score = (POINT_ESTIMATE_WEIGHT * conservative + (1.0 - POINT_ESTIMATE_WEIGHT) * ema)
                .coerceIn(0.0, 1.0),
            hasSupportedObservation = hasSupportedObservation,
        )
    }

    /** [evaluate] 的成对返回值。 */
    data class Evaluation(
        val score: Double,
        val hasSupportedObservation: Boolean,
    )

    /** W0-4：日长单源在 `AlgorithmConstants.DAY_MILLIS`（本文件要 Double，故在此别名一次）。 */
    private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()
}
