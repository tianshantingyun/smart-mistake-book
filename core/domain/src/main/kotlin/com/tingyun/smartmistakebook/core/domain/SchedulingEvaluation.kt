package com.tingyun.smartmistakebook.core.domain

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shared scheduling-evaluation data model (spec mastery-scheduling §2.20/B0):
 * raw review evidence replayed under two competing models - the FSRS-6 power
 * law and the legacy exponential baseline - scored by binary cross-entropy
 * log-loss. The collection path (review_log) stays decoupled from
 * scheduling; this harness is the only consumer that replays it.
 */
data class ReviewSample(
    val practiceUnitId: String,
    val reviewedAtEpochMillis: Long,
    val rating: FsrsRating,
    val durationMs: Long = 0,
    /** ATTEMPT / LOCAL_CHECKED / MODEL_JUDGED / REVEAL / SELF_REPORT / VISUAL — source calibration key (spec §2.5). */
    val sourceKind: String = ATTEMPT_KIND,
    /** Planner reason snapshot carried onto the attempt (spec §6 calibration). */
    val plannedReason: String? = null,
    /**
     * Calendar-day delta from the prior review, captured at collection time from the learner-local
     * study day. Null when unknown (legacy samples); the replay then falls back to a wall-clock
     * floor over [reviewedAtEpochMillis].
     */
    val deltaTDays: Double? = null,
) {
    val isCorrect: Boolean get() = rating != FsrsRating.AGAIN

    /**
     * Elapsed calendar days from [lastReviewedAt] to this sample: the
     * collected learner-local delta when present, otherwise a wall-clock day
     * floor. Shared by every replay/optimizer path so the delta_t semantics
     * stay identical across models (spec §2.20 parity).
     */
    fun elapsedDaysSince(lastReviewedAt: Long): Double = deltaTDays ?: (
        (reviewedAtEpochMillis - lastReviewedAt).toDouble() / DAY_MILLIS
        ).coerceAtLeast(0.0)

    companion object {
        const val ATTEMPT_KIND = "ATTEMPT"

        /**
         * 讲题判定通道（模型判词）的 source kind。与 ATTEMPT 分开落库，且**不参与**
         * FSRS 参数拟合（[fittableReviewSamples] 显式排除）——开放作答的判词有 κ≈0.70 的
         * 判定噪声（台账裁决 18），Anki 官方口径下混进拟合会把所有间隔系统性拉偏。
         * 校准走 [SchedulingEvaluationHarness.calibrateSources] 单独一档；κ 达标后由
         * Wave X 用真实校准数据另裁是否解除。
         */
        const val MODEL_JUDGED_KIND = "MODEL_JUDGED"

        /**
         * 讲题判定通道（**本地核对**）的 source kind（台账裁决 18 = A，随 Wave 1 落地）：
         * 检查题有标准答案、判定确定性≈0 噪声，与模型判词同用 MODEL_JUDGED 会把干净数据
         * 一起扔掉。**参与拟合**（与 ATTEMPT 同列）；权重仍是保守的 0.5，定价校准另议。
         */
        const val LOCAL_CHECKED_KIND = "LOCAL_CHECKED"

        /**
         * 答案揭示（看答案）的 source kind（W1-4/KF-03；台账裁决 1 = B）。揭示行 rating=AGAIN
         * 但**不是回忆尝试**（无可用 delta_t、无作答耗时），必须与真实作答分开，
         * 且**不参与拟合**——否则拟合集里混进一批"半真"的 AGAIN 行。
         */
        const val REVEAL_KIND = "REVEAL"

        /** W0-4：日长单源在 `AlgorithmConstants.DAY_MILLIS`（本文件要 Double，故在此别名一次）。 */
        private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()
    }
}

/**
 * Per-source calibration (spec §2.5): for every subjective positive report
 * (self-report/rating graded Hard or better), find the next real attempt on
 * the same card and compare its realized recall. A source whose realized
 * recall sits well below the real-attempt baseline is over-claiming and the
 * static mapping (subjective Easy cap) should be revisited - by human
 * decision, never auto-rewritten.
 */
data class SourceCalibration(
    val sourceKind: String,
    val positiveReportCount: Int,
    val nextAttemptCount: Int,
    val realizedRecallRate: Double,
    val attemptBaselineRecallRate: Double,
) {
    /** Minimum paired outcomes before any calibration suggestion is valid. */
    val hasSufficientPairs: Boolean get() = nextAttemptCount >= MIN_PAIRED_OUTCOMES

    /**
     * Suggested static adjustment: downgrade when the source's realized
     * recall underperforms the attempt baseline by more than the margin.
     */
    val suggestsDowngrade: Boolean
        get() = hasSufficientPairs && realizedRecallRate <= attemptBaselineRecallRate - DOWNGRADE_MARGIN

    companion object {
        const val MIN_PAIRED_OUTCOMES = 30
        const val DOWNGRADE_MARGIN = 0.15
    }
}

data class ModelEvaluation(
    val modelName: String,
    val logLoss: Double,
    val sampleCount: Int,
) {
    val isValidModel: Boolean get() = logLoss.isFinite()
}

data class SchedulingEvaluationReport(
    val fsrs: ModelEvaluation,
    val baseline: ModelEvaluation,
    val evaluationSampleCount: Int,
) {
    /**
     * Spec §2.20 go/no-go gate: FSRS-6 must beat the exponential baseline on
     * the same replayed evidence before it may stay enabled.
     */
    val fsrsBeatsBaseline: Boolean
        get() = fsrs.isValidModel && baseline.isValidModel &&
            evaluationSampleCount >= MIN_EVALUATION_SAMPLES &&
            fsrs.logLoss < baseline.logLoss

    companion object {
        const val MIN_EVALUATION_SAMPLES = 200
    }
}

/** Replay one card's ordered samples and emit the sequence-end prediction pair. */
object SchedulingReplay {

    /**
     * W2-5①（fsrs-optimizer 对齐，附录 B）：每卡序列只输出**一对**——序列里最后一次长程复习
     * 的（预测 R, 实际结果）。官方 BPTT 的 loss 只取每序列末态（`outputs[seq_lens−1]`），
     * 长程对之外的同日重复只推进状态、不再各发一对。此前"每个长程复习各发一对"会让 loss
     * 被同卡强相关的历史对灌满，官方口径下每卡的监督只有一个末态点。
     *
     * 偏离官方的一处（有意，见台账 Wave 2 记录）：官方末态取 `outputs[seq_lens−1]` 的
     * **后序列状态**；我们取**该复习发生前的状态**（`R(gap, S_before)`）——无泄漏、与逐复习
     * 的概率语义一致，且同日尾行不参与末态对（它们本无长程信号，spec §2.15）。
     */
    data class EndStatePrediction(
        val probability: Double,
        val correct: Boolean,
        /** 末次长程复习本身：时间切分（训练/验证桶）按它的时间戳。 */
        val target: ReviewSample,
    )

    fun predict(
        history: List<ReviewSample>,
        parameters: DoubleArray = FsrsScheduleMath.DEFAULT_PARAMETERS,
    ): EndStatePrediction? {
        require(history.isNotEmpty()) { "Replay requires a non-empty review history" }
        // W2-1/KF-01：decay 显式来自本次重放/拟合的参数集，不再隐式吃默认 w20。
        val decay = -parameters[20]
        val ordered = history.sortedBy(ReviewSample::reviewedAtEpochMillis)
        var stability = 0.0
        var difficulty = 0.0
        var hasState = false
        var lastReviewedAt = 0L
        var endState: EndStatePrediction? = null
        for (sample in ordered) {
            if (!hasState) {
                stability = FsrsScheduleMath.initialStability(sample.rating, parameters)
                difficulty = FsrsScheduleMath.clampDifficulty(
                    FsrsScheduleMath.initialDifficulty(sample.rating, parameters),
                )
                hasState = true
                lastReviewedAt = sample.reviewedAtEpochMillis
                continue
            }
            val elapsedDays = sample.elapsedDaysSince(lastReviewedAt)
            if (elapsedDays < 1.0) {
                // Same-day repeats carry no long-run prediction (spec §2.15).
                stability = FsrsScheduleMath.shortTermStability(stability, sample.rating, parameters)
                difficulty = FsrsScheduleMath.nextDifficulty(difficulty, sample.rating, parameters)
                lastReviewedAt = sample.reviewedAtEpochMillis
                continue
            }
            val retrievability = FsrsScheduleMath.retention(elapsedDays, stability, decay)
            endState = EndStatePrediction(retrievability, sample.isCorrect, sample)
            stability = if (sample.rating == FsrsRating.AGAIN) {
                FsrsScheduleMath.nextForgetStability(difficulty, stability, retrievability, parameters)
            } else {
                FsrsScheduleMath.nextRecallStability(
                    difficulty,
                    stability,
                    retrievability,
                    sample.rating,
                    parameters,
                )
            }
            difficulty = FsrsScheduleMath.nextDifficulty(difficulty, sample.rating, parameters)
            lastReviewedAt = sample.reviewedAtEpochMillis
        }
        return endState
    }

    fun bceLogLoss(predictions: List<Pair<Double, Boolean>>): Double {
        if (predictions.isEmpty()) return Double.NaN
        var total = 0.0
        for ((probability, correct) in predictions) {
            val clamped = min(max(probability, 1e-6), 1.0 - 1e-6)
            total += if (correct) -ln(clamped) else -ln(1.0 - clamped)
        }
        return total / predictions.size
    }

}

/** Same-file single copy: both the harness and the optimizer cut at the same index pick. */
private fun quantile(sorted: List<Long>, fraction: Double): Long {
    if (sorted.isEmpty()) return 0
    val index = (fraction * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
    return sorted[index]
}

/**
 * 可参与评估/拟合的复习样本：排除讲题判定行，直到 `calibrateSources` 的配对结果证明
 * 它可以进（见 [ReviewSample.MODEL_JUDGED_KIND] 的说明）。调用方的"有没有数据"判断
 * 必须用同一口径，否则只做过讲题判定复习的学习者会在报告路径上被空集绊倒。
 */
/**
 * FSRS 参数拟合的样本口径（W1-4/KF-03 + 台账裁决 18）：
 * - 排除 [ReviewSample.MODEL_JUDGED_KIND]——开放作答的模型判词带 κ≈0.70 判定噪声，混进拟合
 *   会系统性拉偏所有间隔（Anki 官方口径：评分即拟合信号）；
 * - 排除 [ReviewSample.REVEAL_KIND]——看答案行 rating 是 AGAIN 但不是一次回忆尝试；
 * - 保留 [ReviewSample.ATTEMPT_KIND] 与 [ReviewSample.LOCAL_CHECKED_KIND]——真实作答与
 *   有标准答案的本地核对都是干净的拟合信号（裁决 18：本地核对解除排除）。
 *
 * 每一个"有没有数据/可不可拟合"的判断都必须走这一条（不再各自手写口径）。
 */
fun fittableReviewSamples(samples: List<ReviewSample>): List<ReviewSample> =
    samples.filterNot {
        it.sourceKind == ReviewSample.MODEL_JUDGED_KIND || it.sourceKind == ReviewSample.REVEAL_KIND
    }

/**
 * Backtest harness (spec §2.20): replays the real review ledger under FSRS-6
 * and under the audited exponential baseline with a chronological
 * time-series split, and reports both log-losses plus the go/no-go gate.
 */
object SchedulingEvaluationHarness {

    /**
     * Source calibration table (spec §2.5/A2): realized recall of the next
     * real attempt after each subjective positive report, per source kind,
 * against the real-attempt baseline.
     */
    fun calibrateSources(samples: List<ReviewSample>): List<SourceCalibration> {
        val subjectiveKinds = samples.map(ReviewSample::sourceKind)
            .filterNot { it == ReviewSample.ATTEMPT_KIND }
            .toSortedSet()
        if (subjectiveKinds.isEmpty()) return emptyList()
        val perCard = samples.groupBy(ReviewSample::practiceUnitId)
            .mapValues { (_, history) -> history.sortedBy(ReviewSample::reviewedAtEpochMillis) }
        val attemptBaseline = perCard.values
            .flatMap { history -> history.filter { it.sourceKind == ReviewSample.ATTEMPT_KIND } }
        val attemptBaselineRate = rate(attemptBaseline)
        return subjectiveKinds.map { sourceKind ->
            var positiveReports = 0
            var paired = 0
            var correctNext = 0
            perCard.values.forEach { history ->
                history.forEachIndexed { index, sample ->
                    if (sample.sourceKind != sourceKind || sample.rating < FsrsRating.HARD) {
                        return@forEachIndexed
                    }
                    positiveReports += 1
                    val nextAttempt = history.drop(index + 1)
                        .firstOrNull { it.sourceKind == ReviewSample.ATTEMPT_KIND } ?: return@forEachIndexed
                    paired += 1
                    if (nextAttempt.isCorrect) correctNext += 1
                }
            }
            SourceCalibration(
                sourceKind = sourceKind,
                positiveReportCount = positiveReports,
                nextAttemptCount = paired,
                realizedRecallRate = if (paired == 0) Double.NaN else correctNext.toDouble() / paired,
                attemptBaselineRecallRate = attemptBaselineRate,
            )
        }
    }

    private fun rate(samples: List<ReviewSample>): Double =
        if (samples.isEmpty()) Double.NaN else samples.count(ReviewSample::isCorrect).toDouble() / samples.size

    /**
     * Per-planned-reason realized recall (spec §6 weight recalibration):
     * groups collected samples by the planner reason that scheduled them and
     * reports each group's recall against the overall baseline. Advisory
     * only - weight constants change by human decision at the >=200-sample
     * threshold, never automatically.
     */
    fun calibratePlannedReasons(samples: List<ReviewSample>): List<PlannedReasonCalibration> {
        val withReason = samples.mapNotNull { sample ->
            sample.plannedReason?.let { reason -> sample to reason }
        }
        if (withReason.isEmpty()) return emptyList()
        val overall = rate(withReason.map { it.first })
        return withReason.groupBy { it.second }
            .map { (reason, rows) ->
                PlannedReasonCalibration(
                    plannedReason = reason,
                    sampleCount = rows.size,
                    realizedRecallRate = rate(rows.map { it.first }),
                    overallRecallRate = overall,
                )
            }
            .sortedByDescending(PlannedReasonCalibration::sampleCount)
    }

    fun evaluate(
        samples: List<ReviewSample>,
        parameters: DoubleArray = FsrsScheduleMath.DEFAULT_PARAMETERS,
        trainFraction: Double = 0.7,
    ): SchedulingEvaluationReport {
        val fittable = fittableReviewSamples(samples)
        require(fittable.isNotEmpty()) { "Evaluation requires review samples" }
        require(trainFraction in 0.1..0.9) { "Train fraction must be within 0.1..0.9" }
        val perCard = fittable.groupBy(ReviewSample::practiceUnitId)
            .map { (_, history) -> history.sortedBy(ReviewSample::reviewedAtEpochMillis) }
            .filter { it.size >= 2 }
        require(perCard.isNotEmpty()) { "Evaluation requires at least one card with two reviews" }

        val cutoff = quantile(
            fittable.map(ReviewSample::reviewedAtEpochMillis).sorted(),
            trainFraction,
        )
        val fsrsTrain = mutableListOf<Pair<Double, Boolean>>()
        val fsrsTest = mutableListOf<Pair<Double, Boolean>>()
        val legacyTrain = mutableListOf<Pair<Double, Boolean>>()
        val legacyTest = mutableListOf<Pair<Double, Boolean>>()
        perCard.forEach { history ->
            // W2-5①：两个模型都只出序列末态一对，按末次长程复习的时间戳分桶——
            // 训练/验证协议对两模型逐位同构。
            SchedulingReplay.predict(history, parameters)?.let { pair ->
                (if (pair.target.reviewedAtEpochMillis <= cutoff) fsrsTrain else fsrsTest) +=
                    pair.probability to pair.correct
            }
            legacy(history)?.let { pair ->
                (if (pair.target.reviewedAtEpochMillis <= cutoff) legacyTrain else legacyTest) +=
                    pair.probability to pair.correct
            }
        }
        val evaluationSamples = fsrsTest.size
        return SchedulingEvaluationReport(
            fsrs = ModelEvaluation(
                "fsrs6",
                if (fsrsTest.isEmpty()) SchedulingReplay.bceLogLoss(fsrsTrain) else SchedulingReplay.bceLogLoss(fsrsTest),
                if (fsrsTest.isEmpty()) fsrsTrain.size else evaluationSamples,
            ),
            baseline = ModelEvaluation(
                "legacy-exponential",
                if (legacyTest.isEmpty()) SchedulingReplay.bceLogLoss(legacyTrain) else SchedulingReplay.bceLogLoss(legacyTest),
                if (legacyTest.isEmpty()) legacyTrain.size else evaluationSamples,
            ),
            evaluationSampleCount = evaluationSamples,
        )
    }

    /**
     * The audited pre-FSRS model: exponential retention with the ad-hoc
     * stability multipliers, fed ratings mapped back to outcomes.
     *
     * The replay mirrors [SchedulingReplay.predict] so both models score the
     * exact same end-state prediction pair (spec §2.20 parity): the first sample only
     * seeds state, and a same-day repeat (elapsed < 1 day) carries no
     * long-run retention signal — it advances state without emitting a
     * prediction pair.
     */
    internal fun legacyPredictionsForTest(history: List<ReviewSample>): SchedulingReplay.EndStatePrediction? =
        legacy(history)

    private fun legacy(history: List<ReviewSample>): SchedulingReplay.EndStatePrediction? {
        val ordered = history.sortedBy(ReviewSample::reviewedAtEpochMillis)
        var stability = 0.5
        var hasState = false
        var lastReviewedAt = 0L
        var endState: SchedulingReplay.EndStatePrediction? = null
        for (sample in ordered) {
            if (!hasState) {
                hasState = true
                lastReviewedAt = sample.reviewedAtEpochMillis
                continue
            }
            val elapsedDays = sample.elapsedDaysSince(lastReviewedAt)
            if (elapsedDays < 1.0) {
                // Same-day repeats carry no long-run prediction (spec §2.15);
                // advance state exactly like the FSRS replay does so the two
                // models evaluate identical prediction sets.
                stability = if (sample.isCorrect) {
                    stability * 2.6 + 0.25
                } else {
                    (stability * 0.45).coerceAtLeast(0.25)
                }
                lastReviewedAt = sample.reviewedAtEpochMillis
                continue
            }
            val probability = Math.pow(0.9, elapsedDays / stability)
            endState = SchedulingReplay.EndStatePrediction(probability, sample.isCorrect, sample)
            stability = if (sample.isCorrect) {
                stability * 2.6 + 0.25
            } else {
                (stability * 0.45).coerceAtLeast(0.25)
            }
            lastReviewedAt = sample.reviewedAtEpochMillis
        }
        return endState
    }

}

/**
 * Local FSRS-6 parameter optimizer (spec §2.11/B6): bounded Adam with
 * central-difference gradients over the replay log-loss, honoring the
 * official hard gate (fewer than the 400 predictable-sample floor: keep
 * defaults; KF-05/台账裁决). Wave 2 aligns the objective with fsrs-optimizer
 * (附录 B): sequence-end loss, L2 prior penalty, no early stopping.
 */
object FsrsParameterOptimizer {

    /**
     * Per-index bounds matching py-fsrs `LOWER_BOUNDS_PARAMETERS` / `UPPER_BOUNDS_PARAMETERS`
     * (fsrs/scheduler.py) and fsrs-rs `parameter_clipper.rs`. The initial-stability rows (w0..w3)
     * span to INITIAL_STABILITY_MAX = 100 days, not 10.
     */
    internal val LOWER_BOUNDS = doubleArrayOf(
        0.001, 0.001, 0.001, 0.001, 1.0, 0.001, 0.001, 0.001, 0.0, 0.0,
        0.001, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1,
    )
    internal val UPPER_BOUNDS = doubleArrayOf(
        100.0, 100.0, 100.0, 100.0, 10.0, 4.0, 4.0, 0.75, 4.5, 0.8,
        3.5, 5.0, 0.25, 0.9, 4.0, 1.0, 6.0, 2.0, 2.0, 0.8, 0.8,
    )

    data class Result(
        val parameters: DoubleArray,
        val mode: Mode,
        val trainLogLoss: Double,
        val validationLogLoss: Double,
        val sampleCount: Int,
        val optimizedParameterIndices: List<Int>,
    )

    enum class Mode { INSUFFICIENT_DATA, FULL_FIT }

    fun optimize(
        samples: List<ReviewSample>,
        iterations: Int = DEFAULT_ITERATIONS,
    ): Result {
        // 讲题判定行是新的评分来源，校准达标前不进拟合器（口径见
        // ReviewSample.MODEL_JUDGED_KIND；W1-4 起看答案行 REVEAL 与之同列，
        // 本地核对 LOCAL_CHECKED 则保留——完整口径只在 fittableReviewSamples 一处）：
        // 拟合参数会被一种偏离标尺的评分整体拉偏。
        // 过滤后为空不是调用错误——只做过讲题判定复习的学习者就该拿到"数据不足"，
        // 而不是异常；下面 predictableSampleCount=0 会走 INSUFFICIENT_DATA 分支。
        val fittingSamples = fittableReviewSamples(samples)
        require(samples.isNotEmpty()) { "Optimization requires review samples" }
        // W2-2/KF-05：官方 400 硬门（原 8/64 双门槛废止）。门槛按可预测（长程）样本计——
        // 首样与同日重复不产生预测对，行数会虚高；口径与 predictableSampleCount 一致。
        val predictableSampleCount = predictableSampleCount(fittingSamples)
        if (predictableSampleCount < MIN_SAMPLES_FOR_FITTING) {
            return Result(
                FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf(),
                Mode.INSUFFICIENT_DATA,
                Double.NaN,
                Double.NaN,
                predictableSampleCount,
                emptyList(),
            )
        }
        // W2-3/KF-12：w3 与 w16 并列剔除——三档评级（裁决）下 EASY 结构性消失，w3 的训练集
        // 为空，拟合它是不可辨识的自由参数；w16 此前已钉 1.0（研究 2026-09-09 §7/§8）。
        val baseIndices = (0..2).toList() + (4..14).toList() + listOf(20)

        // Chronological hold-out (srs-benchmark protocol): each card's FULL
        // history is replayed once and the prediction points are bucketed by
        // timestamp, so validation reviews benefit from the train-segment
        // memory instead of restarting from a cold card. Optimization
        // targets the validation tail so a winning parameter set generalizes
        // forward instead of memorizing the past.
        val cutoff = quantile(
            fittingSamples.map(ReviewSample::reviewedAtEpochMillis).sorted(),
            TRAIN_FRACTION,
        )
        val cards = fittingSamples.groupBy(ReviewSample::practiceUnitId)
            .values
            .filter { it.size >= 2 }
            .map { history -> history.sortedBy(ReviewSample::reviewedAtEpochMillis) }

        var (bestParams, bestValidationObjective) = fit(cards, cutoff, baseIndices, iterations)
        var fittedIndices = baseIndices

        // Spec §2.11b: unlock the hard-penalty coefficient (w15) only when the sample
        // volume reaches the unlock floor, enough HARD reviews exist to identify it,
        // AND doing so improves validation loss by more than the gain margin — a guard
        // against overfitting an extra parameter on insufficient data.
        //
        // w16 (easy bonus) is never fitted: the evidence mapping never grades a review
        // EASY, so its training set is empty and any fitted value is unidentifiable
        // (研究 2026-09-09 §7/§8). It stays pinned at 1.0.
        if (
            predictableSampleCount >= UNLOCK_W15_W16_MIN_SAMPLES &&
            fittingSamples.count { it.rating == FsrsRating.HARD } >= MIN_HARD_SAMPLES_FOR_W15
        ) {
            val extendedIndices = (baseIndices + 15).distinct()
            val (extendedParams, extendedValidationObjective) = fit(cards, cutoff, extendedIndices, iterations)
            val relativeGain = (bestValidationObjective - extendedValidationObjective) / bestValidationObjective
            if (bestValidationObjective.isFinite() && relativeGain > UNLOCK_W15_W16_GAIN_MARGIN) {
                bestParams = extendedParams
                fittedIndices = extendedIndices
            }
        }

        // 上报口径 = 纯 BCE（采纳门/UI 的"log loss"语义）；含 L2 的 objective 只在拟合内部用。
        return Result(
            bestParams,
            Mode.FULL_FIT,
            bceFor(cards, cutoff, bestParams, wantValidation = false),
            bceFor(cards, cutoff, bestParams, wantValidation = true),
            predictableSampleCount,
            fittedIndices,
        )
    }

    /**
     * W2-2 采纳门的比较口径：对**任意**参数集在同一时间切分协议（fittable → 每卡全历史重放 →
     * 80% 时间分位切桶）下的验证桶 log loss。候选参数与存量参数都必须走这同一个函数，
     * "不劣于才写入"的比较才同构；无验证对（分位之上没有可预测样本）返回 null。
     */
    fun validationLogLoss(samples: List<ReviewSample>, parameters: DoubleArray): Double? {
        val fittingSamples = fittableReviewSamples(samples)
        val cards = fittingSamples.groupBy(ReviewSample::practiceUnitId)
            .values
            .filter { it.size >= 2 }
            .map { history -> history.sortedBy(ReviewSample::reviewedAtEpochMillis) }
        if (cards.isEmpty()) return null
        val cutoff = quantile(
            fittingSamples.map(ReviewSample::reviewedAtEpochMillis).sorted(),
            TRAIN_FRACTION,
        )
        val pairs = endStatePairs(cards, cutoff, parameters, wantValidation = true)
        if (pairs.isEmpty()) return null
        return SchedulingReplay.bceLogLoss(pairs)
    }

    /**
     * Number of review samples the replay can emit a prediction pair for: the
     * first review of each card only seeds state, and same-day repeats carry
     * no long-run retention signal. A sample qualifies when it has a prior
     * review at least one calendar day earlier.
     */
    internal fun predictableSampleCount(samples: List<ReviewSample>): Int {
        // 与 evaluate/optimize 同一口径：讲题判定行不算"可预测样本"，避免它们
        // 抬高解锁拟合的样本量门槛。
        val fittable = fittableReviewSamples(samples)
        var count = 0
        fittable.groupBy(ReviewSample::practiceUnitId)
            .values
            .forEach { history ->
                val ordered = history.sortedBy(ReviewSample::reviewedAtEpochMillis)
                if (ordered.size < 2) return@forEach
                var lastReviewedAt = ordered.first().reviewedAtEpochMillis
                for (sample in ordered.drop(1)) {
                    val elapsedDays = sample.elapsedDaysSince(lastReviewedAt)
                    if (elapsedDays >= 1.0) count += 1
                    lastReviewedAt = sample.reviewedAtEpochMillis
                }
            }
        return count
    }

    /**
     * Runs one bounded-Adam fit over [fittedIndices] and returns the best parameters plus their
     * validation objective. The default parameters seed every fit so each stage is independent.
     *
     * W2-5③（附录 B）：**无早停**——固定跑满 [iterations]（原"连续 5 步无改善即断"删除），
     * best 仍按验证 objective 保留（官方 best_w 按最低 eval loss）。
     */
    private fun fit(
        cards: List<List<ReviewSample>>,
        cutoff: Long,
        fittedIndices: List<Int>,
        iterations: Int,
    ): Pair<DoubleArray, Double> {
        var parameters = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf()
        val firstMoment = DoubleArray(FsrsScheduleMath.PARAMETER_COUNT)
        val secondMoment = DoubleArray(FsrsScheduleMath.PARAMETER_COUNT)
        var bestLoss = objectiveFor(cards, cutoff, parameters, wantValidation = true)
        var best = parameters.copyOf()
        for (iteration in 0 until iterations) {
            val gradient = DoubleArray(FsrsScheduleMath.PARAMETER_COUNT)
            for (index in fittedIndices) {
                val upper = parameters.copyOf().also { it[index] = it[index] + EPSILON }
                val lower = parameters.copyOf().also { it[index] = it[index] - EPSILON }
                gradient[index] = (objectiveFor(cards, cutoff, upper, wantValidation = false) -
                    objectiveFor(cards, cutoff, lower, wantValidation = false)) / (2 * EPSILON)
            }
            for (index in fittedIndices) {
                firstMoment[index] = BETA1 * firstMoment[index] + (1 - BETA1) * gradient[index]
                secondMoment[index] = BETA2 * secondMoment[index] + (1 - BETA2) * gradient[index] * gradient[index]
                val firstCorrection = firstMoment[index] / (1 - Math.pow(BETA1, (iteration + 1).toDouble()))
                val secondCorrection = secondMoment[index] / (1 - Math.pow(BETA2, (iteration + 1).toDouble()))
                parameters[index] -= LEARNING_RATE * firstCorrection / (sqrt(secondCorrection) + 1e-8)
                parameters[index] = parameters[index].coerceIn(LOWER_BOUNDS[index], UPPER_BOUNDS[index])
            }
            val currentLoss = objectiveFor(cards, cutoff, parameters, wantValidation = true)
            if (currentLoss < bestLoss - 1e-9) {
                bestLoss = currentLoss
                best = parameters.copyOf()
            }
        }
        return best to bestLoss
    }

    /**
     * W2-5②/附录 B 的目标函数：桶内 BCE 均值 + L2 先验罚项。
     * 罚项 `γ·Σ((w−w_init)²/σ²)`，逐字对齐 fsrs-optimizer v6.5.0
     * `src/fsrs_optimizer/fsrs_optimizer.py`（γ 默认 1：:446/:1424；σ=DEFAULT_PARAMS_STDDEV_TENSOR：
     * :79-100；训练罚项 ：514-523）。官方训练目标是 sum-BCE + γ·罚项（minibatch 按比例摊进
     * epoch）；全批等价形式 = mean BCE + γ·罚项/N——与官方 eval 形式
     * （`BCE.mean() + penalty·γ/train_set_size`，:579-584）逐项同构，故 N 取桶内对数。
     * 数值梯度按全目标求差分，罚项随之自动进入梯度。
     */
    /** 纯 BCE（不含 L2）——[Result] 的上报口径与 W2-2 采纳门的比较口径。 */
    private fun bceFor(
        cards: List<List<ReviewSample>>,
        cutoff: Long,
        parameters: DoubleArray,
        wantValidation: Boolean,
    ): Double {
        val pairs = endStatePairs(cards, cutoff, parameters, wantValidation)
        return if (pairs.isEmpty()) 10.0 else SchedulingReplay.bceLogLoss(pairs)
    }

    private fun objectiveFor(
        cards: List<List<ReviewSample>>,
        cutoff: Long,
        parameters: DoubleArray,
        wantValidation: Boolean,
    ): Double {
        val pairs = endStatePairs(cards, cutoff, parameters, wantValidation)
        val bce = SchedulingReplay.bceLogLoss(pairs)
        if (pairs.isEmpty() || bce.isNaN()) return 10.0
        var penalty = 0.0
        for (index in parameters.indices) {
            val delta = parameters[index] - FsrsScheduleMath.DEFAULT_PARAMETERS[index]
            penalty += (delta / PARAMETER_SIGMA[index]) * (delta / PARAMETER_SIGMA[index])
        }
        return bce + L2_GAMMA * penalty / pairs.size
    }

    /**
     * One end-state pair per card (W2-5①), bucketed by the predicted review's
     * timestamp — the chronological hold-out protocol: each card's FULL
     * history is replayed once (so validation keeps train-segment memory) and
     * the pair belongs to the bucket of the review it predicted.
     */
    private fun endStatePairs(
        cards: List<List<ReviewSample>>,
        cutoff: Long,
        parameters: DoubleArray,
        wantValidation: Boolean,
    ): List<Pair<Double, Boolean>> =
        cards.mapNotNull { history ->
            SchedulingReplay.predict(history, parameters)
                ?.takeIf { (it.target.reviewedAtEpochMillis > cutoff) == wantValidation }
        }
            .map { it.probability to it.correct }

    /** W2-2/KF-05：官方 400 硬门（Anki 口径），取代原 8/64 双门槛。 */
    const val MIN_SAMPLES_FOR_FITTING = 400
    /** Spec §2.11b: unlock w15/w16 only at this sample volume. */
    const val UNLOCK_W15_W16_MIN_SAMPLES = 5_000
    /**
     * …and only when the HARD bucket itself is populated enough to identify the
     * coefficient (研究 2026-09-09 §8: a user who never rates Hard leaves w15
     * just as unidentifiable as w16 always is).
     */
    const val MIN_HARD_SAMPLES_FOR_W15 = 100
    /** Spec §2.11b: unlock w15/w16 only when validation loss improves by more than this fraction. */
    const val UNLOCK_W15_W16_GAIN_MARGIN = 0.02
    const val DEFAULT_ITERATIONS = 24
    const val TRAIN_FRACTION = 0.8
    /**
     * W2-5② L2 先验罚项的 γ（裁决 14：对齐官方默认）。取证：fsrs-optimizer
     * **v6.5.0**（2026-02-02 release）`src/fsrs_optimizer/fsrs_optimizer.py`，
     * `gamma: float = 1`（:446 Trainer.__init__）/ `gamma: float = 1.0`（:1424）。
     */
    const val L2_GAMMA = 1.0
    /**
     * W2-5② 罚项的分母 σ：逐参数先验标准差，逐字对齐 fsrs-optimizer v6.5.0
     * `DEFAULT_PARAMS_STDDEV_TENSOR`（`fsrs_optimizer.py:79-100`）。
     */
    val PARAMETER_SIGMA = doubleArrayOf(
        6.43, 9.66, 17.58, 27.85, 0.57, 0.28, 0.6, 0.12, 0.39, 0.18,
        0.33, 0.3, 0.09, 0.16, 0.57, 0.25, 1.03, 0.31, 0.32, 0.14, 0.27,
    )
    private const val EPSILON = 1e-4
    private const val LEARNING_RATE = 2e-3
    private const val BETA1 = 0.9
    private const val BETA2 = 0.999
}

/**
 * Per-planned-reason realized recall (spec §6 weight recalibration):
 * advisory analysis; weight constants change by human decision at the
 * >=200-sample threshold, never automatically.
 */
data class PlannedReasonCalibration(
    val plannedReason: String,
    val sampleCount: Int,
    val realizedRecallRate: Double,
    val overallRecallRate: Double,
) {
    /** Spec §6: recalibration analysis activates at two hundred samples. */
    val hasSufficientSamples: Boolean get() = sampleCount >= MIN_SAMPLES

    companion object {
        const val MIN_SAMPLES = 200
    }
}
