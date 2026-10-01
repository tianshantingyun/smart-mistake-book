package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState

/** Result of applying one evidence event to a card's memory state. */
data class MemoryUpdateResult(
    val stabilityDays: Double,
    val difficulty: Double,
    val nextReviewAtEpochMillis: Long,
    /**
     * True when the update is a same-day (short-term) or otherwise sub-day
     * refresh: the next review lands on the legacy ten-minute cadence instead
     * of the interval inverse.
     */
    val shortTermReview: Boolean,
)

/**
 * Pluggable card-memory update mathematics (spec mastery-scheduling §2.20
 * kill switch): the FSRS-6 model and the audited legacy exponential model can
 * be swapped without touching the projector or the ledger.
 */
interface MemoryUpdateModel {
    val algorithmId: String

    fun updateMemory(
        previous: ProblemMemoryState?,
        rating: FsrsRating,
        outcome: ProblemMemoryOutcome,
        weight: Double,
        occurredAtEpochMillis: Long,
        effectiveAttemptAtEpochMillis: Long,
        /**
         * Calendar-day delta between this review and the previous one (learner-local epoch day
         * difference), not a 24-hour wall-clock floor. FSRS uses it as delta_t; a cross-midnight
         * review under 24 hours apart is still a new day.
         */
        elapsedCalendarDays: Double,
    ): MemoryUpdateResult
}

/**
 * FSRS-6 updates (spec §2.2-2.4, §2.15): power-law retrievability, R-dependent
 * stability, mean-reverting difficulty on the 1..10 domain, same-day reviews
 * on the short-term branch, and whole-day interval scheduling.
 *
 * KF-11（2026-10-01）：audited 的 legacy 指数模型（`LegacyExponentialMemoryUpdateModel`，
 * 系数 D 级无据）与其 kill-switch（`useFsrsScheduling` 分支 + 设置开关 + DataStore key）
 * 已删除——**FSRS 唯一化**。本类是全仓唯一的记忆更新模型。
 */
class FsrsMemoryUpdateModel(
    private val parameters: DoubleArray = FsrsScheduleMath.DEFAULT_PARAMETERS,
    val desiredRetention: Double = DEFAULT_DESIRED_RETENTION,
) : MemoryUpdateModel {

    override val algorithmId: String = ALGORITHM_ID

    /**
     * W2-1/KF-01：本模型的遗忘曲线衰减 `-w20`。稳定性/难度更新的 retrievability 与间隔反函数
     * 都必须用它（个性化参数),不再隐式吃 `FsrsScheduleMath` 的默认值；投影器的毕业间隔
     * （`LearningProjector` 里 `memoryUpdateModel is FsrsMemoryUpdateModel` 分支）也从这里取。
     */
    val decay: Double = -parameters[20]

    init {
        FsrsScheduleMath.requireValid(parameters)
        require(desiredRetention in 0.7..0.97) {
            "Desired retention must be within the supported 0.7..0.97 range"
        }
    }

    override fun updateMemory(
        previous: ProblemMemoryState?,
        rating: FsrsRating,
        outcome: ProblemMemoryOutcome,
        weight: Double,
        occurredAtEpochMillis: Long,
        effectiveAttemptAtEpochMillis: Long,
        elapsedCalendarDays: Double,
    ): MemoryUpdateResult {
        val (stability, difficulty) = nextMemoryState(
            previousStabilityDays = previous?.stabilityDays,
            previousDifficulty = previous?.difficulty,
            rating = rating,
            elapsedCalendarDays = elapsedCalendarDays,
        )
        val intervalDays = FsrsScheduleMath.intervalDays(stability, desiredRetention, decay).coerceAtLeast(1)
        val nextReviewAt = addDays(effectiveAttemptAtEpochMillis, intervalDays.toLong())
        return MemoryUpdateResult(
            stabilityDays = stability,
            difficulty = difficulty,
            nextReviewAtEpochMillis = nextReviewAt,
            // Learning steps are disabled (spec §2.15): every interval is at
            // least one whole day even for same-day reviews.
            shortTermReview = false,
        )
    }

    /**
     * 记忆状态核心更新（W3-2/E 判据的"知识点记忆卡"与逐题共用同一套公式与分支）：
     * previous 为空 → 初始值（首条）；同日（elapsed < 1）→ 短程分支；跨日 AGAIN → 遗忘分支；
     * 跨日其余 → 长程成功分支；难度每次都更新（含同日）。
     */
    fun nextMemoryState(
        previousStabilityDays: Double?,
        previousDifficulty: Double?,
        rating: FsrsRating,
        elapsedCalendarDays: Double,
    ): Pair<Double, Double> {
        if (previousStabilityDays == null || previousDifficulty == null) {
            return FsrsScheduleMath.initialStability(rating, parameters) to
                FsrsScheduleMath.clampDifficulty(FsrsScheduleMath.initialDifficulty(rating, parameters))
        }
        val elapsedDays = elapsedCalendarDays.coerceAtLeast(0.0)
        val stability = if (elapsedDays < 1.0) {
            FsrsScheduleMath.shortTermStability(previousStabilityDays, rating, parameters)
        } else if (rating == FsrsRating.AGAIN) {
            FsrsScheduleMath.nextForgetStability(
                difficulty = previousDifficulty,
                stability = previousStabilityDays,
                retrievability = FsrsScheduleMath.retention(elapsedDays, previousStabilityDays, decay),
                parameters = parameters,
            )
        } else {
            FsrsScheduleMath.nextRecallStability(
                difficulty = previousDifficulty,
                stability = previousStabilityDays,
                retrievability = FsrsScheduleMath.retention(elapsedDays, previousStabilityDays, decay),
                rating = rating,
                parameters = parameters,
            )
        }
        // Difficulty updates on every review, including same-day ones.
        val difficulty = FsrsScheduleMath.nextDifficulty(previousDifficulty, rating, parameters)
        return stability to difficulty
    }

    private fun addDays(epochMillis: Long, days: Long): Long {
        val increment = days * DAY_MILLIS.toLong()
        return if (Long.MAX_VALUE - epochMillis < increment) Long.MAX_VALUE else epochMillis + increment
    }

    companion object {
        const val ALGORITHM_ID = "fsrs6"
        const val DEFAULT_DESIRED_RETENTION = 0.9
        /** W0-4：日长单源在 `AlgorithmConstants.DAY_MILLIS`（本文件要 Double，故在此别名一次）。 */
        private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()
    }
}
