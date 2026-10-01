package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import kotlin.math.floor

fun interface EpochMillisClock {
    fun nowEpochMillis(): Long
}

object SystemEpochMillisClock : EpochMillisClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

enum class ClockAnomaly {
    NONE,
    TIME_ROLLBACK,
}

data class RetentionEstimate(
    val probability: Double,
    val clockAnomaly: ClockAnomaly,
)

/**
 * 遗忘曲线（spec mastery-scheduling §2.20）：FSRS-6 幂律 R(t,S)。
 *
 * KF-11 / 批次 3 收口（2026-10-01）：audited 指数基线与 `ForgettingCurveAlgorithm`
 * 两值枚举已删除——**FSRS 唯一化**（此前 V1 排程器 / HLR 审计用默认构造拿到的是
 * legacy 算法，与投影侧 FSRS 口径分叉）。
 *
 * W2-1/KF-01：FSRS 的 [decay] 显式化——默认等于默认参数集的 w20；学生投影与排程路径
 * 应传同一份个性化模型的 decay（`RoomBackedStudyExperienceRepository` 的
 * `activeFsrsDecay`），无参数上下文的构造保持默认并在台账登记为边界。
 */
class ForgettingCurve(
    private val clock: EpochMillisClock = SystemEpochMillisClock,
    private val stabilityRetention: Double = DEFAULT_STABILITY_RETENTION,
    private val decay: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
) {
    init {
        require(stabilityRetention in 0.0..1.0 && stabilityRetention != 0.0 && stabilityRetention != 1.0) {
            "Stability retention must be strictly between zero and one"
        }
    }

    fun retentionNow(state: ProblemMemoryState): Double = retentionAt(
        state = state,
        atEpochMillis = clock.nowEpochMillis(),
    )

    fun retentionAt(state: ProblemMemoryState, atEpochMillis: Long): Double {
        return estimateAt(state, atEpochMillis).probability
    }

    fun estimateAt(state: ProblemMemoryState, atEpochMillis: Long): RetentionEstimate {
        require(atEpochMillis >= 0) { "Evaluation time must not be negative" }
        val rollback = atEpochMillis < state.lastReviewedAtEpochMillis
        val elapsedMillis = if (rollback) 0 else atEpochMillis - state.lastReviewedAtEpochMillis
        // py-fsrs floors elapsed wall-clock time to whole days (the scheduling
        // delta_t itself uses learner-local calendar days).
        val elapsedDays = floor(elapsedMillis.toDouble() / DAY_MILLIS).coerceAtLeast(0.0)
        val probability = FsrsScheduleMath.retention(elapsedDays, state.stabilityDays, decay)
            .coerceIn(0.0, 1.0)
        return RetentionEstimate(
            probability = probability,
            clockAnomaly = if (rollback) ClockAnomaly.TIME_ROLLBACK else ClockAnomaly.NONE,
        )
    }

    fun reviewAtTargetRetention(
        reviewedAtEpochMillis: Long,
        stabilityDays: Double,
        targetRetention: Double = stabilityRetention,
    ): Long {
        require(reviewedAtEpochMillis >= 0) { "Review time must not be negative" }
        require(stabilityDays.isFinite() && stabilityDays > 0.0) {
            "Stability must be positive"
        }
        require(targetRetention in 0.0..1.0 && targetRetention != 0.0 && targetRetention != 1.0) {
            "Target retention must be strictly between zero and one"
        }
        val intervalDays = FsrsScheduleMath.intervalDays(stabilityDays, targetRetention, decay)
        val intervalMillis = intervalDays * DAY_MILLIS.toLong()
        return if (Long.MAX_VALUE - reviewedAtEpochMillis < intervalMillis) {
            Long.MAX_VALUE
        } else {
            reviewedAtEpochMillis + intervalMillis
        }
    }

    companion object {
        const val VERSION = "forgetting-curve-v3"
        const val DEFAULT_STABILITY_RETENTION = 0.9
        /** W0-4：日长单源在 `AlgorithmConstants.DAY_MILLIS`（本文件要 Double，故在此别名一次）。 */
        private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()
    }
}
