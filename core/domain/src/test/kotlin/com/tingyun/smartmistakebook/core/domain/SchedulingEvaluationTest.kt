package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingEvaluationHarnessTest {

    @Test
    fun `replay prefers the collected calendar day delta over a wall clock floor`() {
        // Two reviews 30 wall-clock minutes apart but on consecutive learner-local days: the
        // collected delta_t_days=1 must drive the long-run branch, not the same-day short-term one.
        val history = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, deltaTDays = null), // first, no prior
            sample("unit-1", DAY * 0 + 30 * 60_000L, FsrsRating.GOOD, deltaTDays = 1.0),
        )

        val endState = SchedulingReplay.predict(history)

        // A 30-minute gap under a wall-clock floor would be same-day (no end-state pair); the
        // collected calendar-day delta of 1.0 must yield the long-run end-state pair.
        assertNotNull(endState)
        assertEquals(history[1].reviewedAtEpochMillis, endState!!.target.reviewedAtEpochMillis)
    }

    @Test
    fun `replay emits only the sequence end pair for the last long range review`() {
        // W2-5①：末态口径——每卡一个监督点，来自序列里最后一次长程复习。
        val history = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD),
            sample("unit-1", DAY * 2, FsrsRating.GOOD),
            sample("unit-1", DAY * 6, FsrsRating.AGAIN),
            sample("unit-1", DAY * 9, FsrsRating.GOOD),
        )

        val endState = SchedulingReplay.predict(history)

        assertNotNull(endState)
        assertEquals("监督点是末次长程复习", history[3].reviewedAtEpochMillis, endState!!.target.reviewedAtEpochMillis)
        assertFalse("末次复习答对 → correct", !endState.correct)
        assertTrue(endState.probability in 0.0..1.0)
    }

    @Test
    fun `replay returns null when no long range review exists`() {
        val history = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, deltaTDays = null),
            sample("unit-1", DAY * 0 + 60_000L, FsrsRating.GOOD, deltaTDays = 0.0),
        )

        assertNull("全部同日复习没有长程监督点", SchedulingReplay.predict(history))
    }

    @Test
    fun `optimizer bounds match py-fsrs parameter validation ranges`() {
        // py-fsrs UPPER_BOUNDS_PARAMETERS: initial stability rows span to 100 days;
        // w12 (difficulty decay) = 0.25, w13 = 0.9, w15 (hard penalty) = 1.0,
        // w16 (easy bonus) = 6.0, w19 (short-term exponent) = 0.8, w20 (decay) = 0.8.
        assertEquals(100.0, FsrsParameterOptimizer.UPPER_BOUNDS[0], 1e-9)
        assertEquals(100.0, FsrsParameterOptimizer.UPPER_BOUNDS[3], 1e-9)
        assertEquals(4.0, FsrsParameterOptimizer.UPPER_BOUNDS[5], 1e-9)
        assertEquals(0.75, FsrsParameterOptimizer.UPPER_BOUNDS[7], 1e-9)
        assertEquals(0.25, FsrsParameterOptimizer.UPPER_BOUNDS[12], 1e-9)
        assertEquals(0.9, FsrsParameterOptimizer.UPPER_BOUNDS[13], 1e-9)
        assertEquals(1.0, FsrsParameterOptimizer.UPPER_BOUNDS[15], 1e-9)
        assertEquals(6.0, FsrsParameterOptimizer.UPPER_BOUNDS[16], 1e-9)
        assertEquals(0.8, FsrsParameterOptimizer.UPPER_BOUNDS[19], 1e-9)
        assertEquals(0.8, FsrsParameterOptimizer.UPPER_BOUNDS[20], 1e-9)
        // Lower bound w4 (difficulty intercept) is 1.0; w16 (easy bonus) is 1.0; w20 is 0.1.
        assertEquals(1.0, FsrsParameterOptimizer.LOWER_BOUNDS[4], 1e-9)
        assertEquals(1.0, FsrsParameterOptimizer.LOWER_BOUNDS[16], 1e-9)
        assertEquals(0.1, FsrsParameterOptimizer.LOWER_BOUNDS[20], 1e-9)
    }

    @Test
    fun `harness reports finite losses for both models`() {
        val samples = syntheticHistory()

        val report = SchedulingEvaluationHarness.evaluate(samples)

        assertTrue(report.fsrs.isValidModel)
        assertTrue(report.baseline.isValidModel)
        assertTrue(report.fsrs.sampleCount > 0)
        assertTrue(report.fsrs.logLoss > 0.0)
        assertTrue(report.baseline.logLoss > 0.0)
    }

    @Test
    fun `optimizer keeps defaults below the data floor`() {
        val samples = (0 until 3).flatMap { card ->
            listOf(
                sample("unit-$card", DAY * 0, FsrsRating.GOOD),
                sample("unit-$card", DAY * 3, FsrsRating.GOOD),
            )
        }

        val result = FsrsParameterOptimizer.optimize(samples)

        assertEquals(FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA, result.mode)
        assertEquals(
            FsrsScheduleMath.DEFAULT_PARAMETERS.toList(),
            result.parameters.toList(),
        )
    }

    @Test
    fun `optimizer refuses to fit below the 400 predictable sample hard gate`() {
        // W2-2/KF-05：官方 400 硬门取代 8/64 双门槛。100 卡 × 6 行 = 500 可预测样本
        // 远超门槛；但门下的窄带模式（INITIAL_STABILITY_ONLY）已废止——不足 400 就是
        // INSUFFICIENT_DATA，不存在"少拟合几个参数"的中间档。
        val samples = syntheticHistory(cardCount = 100)

        val result = FsrsParameterOptimizer.optimize(samples, iterations = 4)

        assertEquals(FsrsParameterOptimizer.Mode.FULL_FIT, result.mode)
        assertTrue(result.sampleCount >= 400)
        assertTrue(result.trainLogLoss.isFinite())
    }

    @Test
    fun `optimizer full fit improves or preserves the default log loss on learnable data`() {
        val samples = biasedHistory(correctStabilityGrowth = true, cardCount = 100)

        val defaultLoss = SchedulingReplay.bceLogLoss(
            samples.groupBy(ReviewSample::practiceUnitId).values
                .filter { it.size >= 2 }
                .mapNotNull { SchedulingReplay.predict(it) }
                .map { it.probability to it.correct },
        )
        val result = FsrsParameterOptimizer.optimize(samples, iterations = 12)

        assertEquals(FsrsParameterOptimizer.Mode.FULL_FIT, result.mode)
        assertTrue(
            "optimized ${result.trainLogLoss} should not exceed default $defaultLoss",
            result.trainLogLoss <= defaultLoss + 0.05,
        )
    }

    @Test
    fun `w15 w16 unlock contract matches spec section 2_11b`() {
        // Spec §2.11b: unlock hard/easy penalty coefficients only at >=5000 samples
        // and only when validation loss improves by more than 2%.
        assertEquals(5_000, FsrsParameterOptimizer.UNLOCK_W15_W16_MIN_SAMPLES)
        assertEquals(0.02, FsrsParameterOptimizer.UNLOCK_W15_W16_GAIN_MARGIN, 1e-9)
        // 研究 2026-09-09 §8: the HARD bucket must itself be populated.
        assertEquals(100, FsrsParameterOptimizer.MIN_HARD_SAMPLES_FOR_W15)
    }

    /**
     * W2-1/KF-01 的回归钉：扰动 w20 必须改变同一批数据上的 log loss——w20 一旦有了梯度，
     * 拟合才可能触到 decay；若有人把 decay 接线改回隐式默认（loss 对 w20 恒定），这条会红。
     */
    @Test
    fun `loss responds to w20 perturbations on a fixed dataset`() {
        val samples = syntheticHistory(cardCount = 100)

        val atDefault = FsrsParameterOptimizer.validationLogLoss(
            samples,
            FsrsScheduleMath.DEFAULT_PARAMETERS,
        )!!
        val perturbedParameters = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also {
            it[20] = FsrsScheduleMath.DEFAULT_PARAMETERS[20] + 0.08
        }
        val perturbed = FsrsParameterOptimizer.validationLogLoss(samples, perturbedParameters)!!

        assertTrue(
            "w20 +0.08 必须移动 loss：default=$atDefault perturbed=$perturbed",
            atDefault != perturbed,
        )
    }

    @Test
    fun `easy bonus is pinned neutral and never fitted`() {
        // 研究 2026-09-09 §7: schedulingRatingFor never returns EASY, so w16 can
        // neither apply at runtime nor be identified from the review log.
        assertEquals(1.0, FsrsScheduleMath.DEFAULT_PARAMETERS[16], 1e-12)
        assertEquals(
            1.0,
            FsrsScheduleMath.nextRecallStability(
                difficulty = 5.0,
                stability = 10.0,
                retrievability = 0.9,
                rating = FsrsRating.EASY,
            ) / FsrsScheduleMath.nextRecallStability(
                difficulty = 5.0,
                stability = 10.0,
                retrievability = 0.9,
                rating = FsrsRating.GOOD,
            ),
            1e-9,
        )
        val samples = syntheticHistory(cardCount = 100)
        val result = FsrsParameterOptimizer.optimize(samples, iterations = 6)
        assertTrue(16 !in result.optimizedParameterIndices)
        // W2-3/KF-12：w3 与 w16 并列剔除——三档评级下 EASY 结构性消失，w3 同为不可辨识参数。
        assertTrue(3 !in result.optimizedParameterIndices)
        // 被剔除的维度必须停留在默认值上（拟合不许碰它们）。
        assertEquals(FsrsScheduleMath.DEFAULT_PARAMETERS[3], result.parameters[3], 1e-12)
        assertEquals(FsrsScheduleMath.DEFAULT_PARAMETERS[16], result.parameters[16], 1e-12)
    }

    @Test
    fun `optimizer below the w15 w16 floor never fits those coefficients`() {
        val samples = syntheticHistory(cardCount = 100)  // well under 5000

        val result = FsrsParameterOptimizer.optimize(samples, iterations = 6)

        assertEquals(FsrsParameterOptimizer.Mode.FULL_FIT, result.mode)
        // w15 (hard penalty) and w16 (easy bonus) must stay out of the fitted set below the floor.
        assertTrue(15 !in result.optimizedParameterIndices)
        assertTrue(16 !in result.optimizedParameterIndices)
    }

    @Test
    fun `model judged rows never feed the optimizer or the evaluation`() {
        // 讲题判定是新的评分来源：校准达标前只允许进 calibrateSources 单列一源，
        // 不许进 FSRS 参数拟合（口径见 docs/research/model-judged-verdict-pricing.md §4(iii)8）。
        val judgedOnly = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            sample("unit-1", DAY * 2, FsrsRating.GOOD, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            sample("unit-1", DAY * 4, FsrsRating.AGAIN, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
        )

        assertEquals(0, FsrsParameterOptimizer.predictableSampleCount(judgedOnly))

        // 拟合样本被清空后，优化器必须停在数据不足档，而不是拿讲题判定行去拟合。
        val optimizeResult = FsrsParameterOptimizer.optimize(judgedOnly)
        assertEquals(FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA, optimizeResult.mode)
        assertEquals(0, optimizeResult.sampleCount)

        // 评估同理：过滤后没有可评估样本，直接拒绝而不是静默用讲题判定行。
        assertThrows(IllegalArgumentException::class.java) {
            SchedulingEvaluationHarness.evaluate(judgedOnly)
        }
    }

    @Test
    fun `model judged positives still surface in source calibration`() {
        val samples = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, sourceKind = ReviewSample.ATTEMPT_KIND),
            sample("unit-1", DAY * 2, FsrsRating.GOOD, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            sample("unit-1", DAY * 5, FsrsRating.AGAIN, sourceKind = ReviewSample.ATTEMPT_KIND),
        )

        val calibration = SchedulingEvaluationHarness.calibrateSources(samples)

        val judged = calibration.single { it.sourceKind == ReviewSample.MODEL_JUDGED_KIND }
        assertEquals(1, judged.positiveReportCount)
        assertEquals(1, judged.nextAttemptCount)
        assertEquals(0.0, judged.realizedRecallRate, 0.0)
    }

    private fun syntheticHistory(cardCount: Int = 12): List<ReviewSample> = (0 until cardCount).flatMap { card ->
        var at = DAY * card
        var rating = if (card % 3 == 0) FsrsRating.AGAIN else FsrsRating.GOOD
        buildList {
            add(sample("unit-$card", at, rating))
            for (step in 1..5) {
                at += DAY * (2 + step)
                rating = if (step == 3) FsrsRating.AGAIN else FsrsRating.GOOD
                add(sample("unit-$card", at, rating))
            }
        }
    }

    private fun biasedHistory(correctStabilityGrowth: Boolean, cardCount: Int): List<ReviewSample> {
        require(correctStabilityGrowth)
        return (0 until cardCount).flatMap { card ->
            var at = DAY * card
            buildList {
                add(sample("unit-$card", at, FsrsRating.GOOD))
                var interval = 3
                for (step in 1..7) {
                    at += DAY * interval
                    add(sample("unit-$card", at, FsrsRating.GOOD))
                    interval = (interval * 1.6).toInt().coerceAtLeast(2)
                }
            }
        }
    }

    @Test
    fun `source calibration pairs subjective positives with the next real attempt`() {
        val samples = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, sourceKind = "ATTEMPT"),
            sample("unit-1", DAY * 2, FsrsRating.GOOD, sourceKind = "SELF_REPORT"),
            sample("unit-1", DAY * 4, FsrsRating.AGAIN, sourceKind = "ATTEMPT"),
            sample("unit-2", DAY * 0, FsrsRating.GOOD, sourceKind = "ATTEMPT"),
            sample("unit-2", DAY * 2, FsrsRating.EASY, sourceKind = "SELF_REPORT"),
            sample("unit-2", DAY * 5, FsrsRating.GOOD, sourceKind = "ATTEMPT"),
        )

        val calibration = SchedulingEvaluationHarness.calibrateSources(samples)

        assertEquals(1, calibration.size)
        val selfReport = calibration.single()
        assertEquals("SELF_REPORT", selfReport.sourceKind)
        assertEquals(2, selfReport.positiveReportCount)
        assertEquals(2, selfReport.nextAttemptCount)
        // One of the two follow-up attempts failed, the other succeeded.
        assertEquals(0.5, selfReport.realizedRecallRate, 1e-9)
        assertFalse(selfReport.hasSufficientPairs)
        assertFalse(selfReport.suggestsDowngrade)
    }

    @Test
    fun `optimizer reports a finite validation loss under the hold out protocol`() {
        val samples = syntheticHistory(cardCount = 100)

        val result = FsrsParameterOptimizer.optimize(samples, iterations = 6)

        assertTrue(result.validationLogLoss.isFinite())
        assertTrue(result.trainLogLoss.isFinite())
    }

    @Test
    fun `persisted Learning state overrides the elapsed heuristic in replay`() {
        // W2-4/KF-23：state 是"同日重复 vs 长程复习"的权威判据。口径变更前的历史行可能
        // 墙钟跨日却仍属同一学习日（04:00 日界），按 elapsed 会多发一个长程对。
        val persisted = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, state = ReviewSample.STATE_NEW),
            sample("unit-1", DAY * 3, FsrsRating.GOOD, state = ReviewSample.STATE_LEARNING),
        )
        assertNull("state=Learning → 不发长程对（哪怕墙钟跨了 3 天）", SchedulingReplay.predict(persisted))

        val legacyRows = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD),
            sample("unit-1", DAY * 3, FsrsRating.GOOD),
        )
        assertNotNull("旧行（state=null）沿用 elapsed 启发式", SchedulingReplay.predict(legacyRows))
    }

    @Test
    fun `persisted Review state makes a short gap eligible for a long range pair`() {
        // 反向对照：墙钟只隔 1 小时，但落库状态是 Review（跨学习日发生在 04:00 前后）
        // → 仍然产生长程监督对。
        val history = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, state = ReviewSample.STATE_NEW),
            sample("unit-1", DAY * 0 + 3_600_000, FsrsRating.GOOD, state = ReviewSample.STATE_REVIEW),
        )
        assertNotNull(SchedulingReplay.predict(history))
    }

    @Test
    fun `legacy baseline skips same day repeats so both models score the same prediction pairs`() {
        // A history whose every long-run prediction is preceded by a same-day
        // repeat. The legacy baseline must skip the same-day review (it
        // carries no long-run retention signal) instead of emitting a
        // near-certainty pair, so the FSRS and baseline log-losses are
        // computed over identical prediction sets (spec §2.20 parity).
        val history = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, deltaTDays = null),
            sample("unit-1", DAY * 0 + 30 * 60_000L, FsrsRating.GOOD, deltaTDays = 0.0),
            sample("unit-1", DAY * 3, FsrsRating.GOOD, deltaTDays = 3.0),
        )

        val fsrsEndState = SchedulingReplay.predict(history)
        val legacyEndState = SchedulingEvaluationHarness.legacyPredictionsForTest(history)

        assertEquals(
            "same-day repeats must not create legacy prediction pairs",
            fsrsEndState == null,
            legacyEndState == null,
        )
    }

    @Test
    fun `optimizer thresholds count predictable samples not raw rows`() {
        // W2-2/KF-05 的 400 门按**可预测样本**计，不按行数：36 卡 × 4 行 = 144 行里有
        // 大量同日重复，可预测样本只有 36——若按行数判门，144 行会被误当成可拟合，
        // 拿 36 个数据点去解 15 个自由参数。
        val samples = (0 until 36).flatMap { card ->
            listOf(
                sample("unit-$card", DAY * card, FsrsRating.GOOD, deltaTDays = null),
                sample("unit-$card", DAY * card + 1_000L, FsrsRating.GOOD, deltaTDays = 0.0),
                sample("unit-$card", DAY * card + 2_000L, FsrsRating.GOOD, deltaTDays = 0.0),
                sample("unit-$card", DAY * (card + 3), FsrsRating.GOOD, deltaTDays = 3.0),
            )
        }

        val result = FsrsParameterOptimizer.optimize(samples, iterations = 4)

        assertEquals(FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA, result.mode)
        assertEquals(36, result.sampleCount)
        assertTrue(20 in result.parameters.indices)
        assertEquals(
            FsrsScheduleMath.DEFAULT_PARAMETERS.toList(),
            result.parameters.toList(),
        )
    }

    @Test
    fun `optimizer crosses the gate exactly at 400 predictable samples`() {
        // W2-2/KF-05 的边界钉：399 不拟合、400 拟合（每卡 2 行=1 个可预测样本）。
        fun gateFixture(cardCount: Int) = (0 until cardCount).flatMap { card ->
            listOf(
                sample("unit-$card", DAY * card, FsrsRating.GOOD, deltaTDays = null),
                sample("unit-$card", DAY * (card + 2), FsrsRating.GOOD, deltaTDays = 2.0),
            )
        }

        val below = FsrsParameterOptimizer.optimize(gateFixture(399), iterations = 4)
        val at = FsrsParameterOptimizer.optimize(gateFixture(400), iterations = 4)

        assertEquals(FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA, below.mode)
        assertEquals(FsrsParameterOptimizer.Mode.FULL_FIT, at.mode)
        assertEquals(399, below.sampleCount)
        assertEquals(400, at.sampleCount)
    }

    @Test
    fun `optimizer reports insufficient data when no cross day prediction exists`() {
        // Every review is on the same calendar day as its predecessor: there
        // is no long-run prediction to fit, so the optimizer must report
        // insufficient data instead of fitting noise (raw rows would exceed
        // the 8-sample floor here).
        val samples = (0 until 10).flatMap { card ->
            listOf(
                sample("unit-$card", DAY * card, FsrsRating.GOOD, deltaTDays = null),
                sample("unit-$card", DAY * card + 60_000L, FsrsRating.AGAIN, deltaTDays = 0.0),
            )
        }

        val result = FsrsParameterOptimizer.optimize(samples, iterations = 4)

        assertEquals(FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA, result.mode)
        assertEquals(
            FsrsScheduleMath.DEFAULT_PARAMETERS.toList(),
            result.parameters.toList(),
        )
    }

    /**
     * W1-4/KF-03 + 台账裁决 18：拟合样本口径——真实作答与**本地核对**保留，
     * 模型判词与**看答案**排除。口径只此一处（`fittableReviewSamples`）。
     */
    @Test
    fun `fittable samples keep real attempts and local checks but drop model verdicts and reveals`() {
        val samples = listOf(
            sample("unit-1", DAY * 0, FsrsRating.GOOD, sourceKind = ReviewSample.ATTEMPT_KIND),
            sample("unit-1", DAY * 1, FsrsRating.GOOD, sourceKind = ReviewSample.LOCAL_CHECKED_KIND),
            sample("unit-1", DAY * 2, FsrsRating.HARD, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            sample("unit-1", DAY * 3, FsrsRating.AGAIN, sourceKind = ReviewSample.REVEAL_KIND),
        )

        val fittable = fittableReviewSamples(samples)

        assertEquals(
            listOf(ReviewSample.ATTEMPT_KIND, ReviewSample.LOCAL_CHECKED_KIND),
            fittable.map(ReviewSample::sourceKind),
        )
    }

    private fun sample(
        unitId: String,
        at: Long,
        rating: FsrsRating,
        sourceKind: String = ReviewSample.ATTEMPT_KIND,
        deltaTDays: Double? = null,
        state: Int? = null,
    ) = ReviewSample(
        practiceUnitId = unitId,
        reviewedAtEpochMillis = at,
        rating = rating,
        sourceKind = sourceKind,
        deltaTDays = deltaTDays,
        state = state,
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}

class TimeOfDaySignalsTest {

    @Test
    fun `peak bucket midpoint maps to minutes after midnight`() {
        val split = TimeBucketSplit()
        assertEquals(8 * 60, split.midpointMinute(TimeBucket.MORNING))
        assertEquals(16 * 60, split.midpointMinute(TimeBucket.AFTERNOON))
        // Night spans across midnight: 23:00..05:00 midpoint is 02:00.
        assertEquals(2 * 60, split.midpointMinute(TimeBucket.NIGHT))
    }


    @Test
    fun `default bucket split maps a learner day`() {
        assertEquals(TimeBucket.MORNING, TimeBucketSplit().bucketFor(7))
        assertEquals(TimeBucket.NOON, TimeBucketSplit().bucketFor(12))
        assertEquals(TimeBucket.AFTERNOON, TimeBucketSplit().bucketFor(15))
        assertEquals(TimeBucket.EVENING, TimeBucketSplit().bucketFor(20))
        assertEquals(TimeBucket.NIGHT, TimeBucketSplit().bucketFor(1))
    }

    @Test
    fun `cold start multipliers stay neutral`() {
        val observations = List(20) { index ->
            TimeOfDayObservation(TimeBucket.MORNING, isCorrect = index % 2 == 0, durationMs = 5_000)
        }

        val profile = TimeOfDayCalibrator.profile(observations)

        assertEquals(1.0, profile.multiplierFor(TimeBucket.MORNING), 0.0)
        assertNull(profile.rtBaseline)
    }

    @Test
    fun `strong bucket accuracy raises its shrunken multiplier`() {
        val observations = buildList {
            repeat(40) { add(TimeOfDayObservation(TimeBucket.MORNING, isCorrect = true, durationMs = 10_000)) }
            repeat(40) { add(TimeOfDayObservation(TimeBucket.EVENING, isCorrect = false, durationMs = 10_000)) }
        }

        val profile = TimeOfDayCalibrator.profile(observations)

        assertTrue(profile.multiplierFor(TimeBucket.MORNING) > 1.0)
        assertTrue(profile.multiplierFor(TimeBucket.EVENING) < 1.0)
        assertTrue(profile.multiplierFor(TimeBucket.MORNING) <= TimeOfDayCalibrator.MAX_MULTIPLIER)
        assertNotNull(profile.rtBaseline)
    }

    @Test
    fun `suspected guesses lose weight only below the personal lower tail`() {
        val observations = List(60) { index ->
            TimeOfDayObservation(
                TimeBucket.MORNING,
                isCorrect = true,
                durationMs = 20_000L + index * 500,
            )
        }
        val profile = TimeOfDayCalibrator.profile(observations)

        val fluent = TimeOfDayCalibrator.correctedWeight(1.0, true, 20_000, profile)
        val slow = TimeOfDayCalibrator.correctedWeight(1.0, true, 60_000, profile)

        assertEquals(1.0 * TimeOfDayCalibrator.GUESS_WEIGHT_FACTOR, fluent, 1e-9)
        assertEquals(1.0, slow, 1e-9)
    }
}
