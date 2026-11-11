package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校准可见页（批次 49 P2-12）的数据口径。
 *
 * 这一层要钉住的是"学生看到的数字到底是哪一个参数集、在哪一批样本上算的"：
 * - 参数来源必须是**可区分**的（本地拟合 / 默认 / 导入），否则备份恢复回来的参数会被
 *   贴上"最近本地拟合"的标签，学生据此判断"拟合是否在工作"就是错的；
 * - 验证 logLoss 必须用**同一时间切分**分别算当前参数与默认参数基线，否则"vs 基线"
 *   比较的是两批不同的验证对，数字不能相减；
 * - 21 维必须完整展示（拟合器写回 21 维，展示少一维 = 学生看不到实际生效的参数）；
 * - per-reason 表必须与拟合/评估同一口径（MODEL_JUDGED / REVEAL 不进）。
 */
class SchedulingCalibrationViewBuilderTest {

    @Test
    fun `nothing fitted yet reports the default parameters and an empty fit record`() {
        val samples = twoDayCard()

        val view = SchedulingCalibrationViewBuilder.build(
            samples = samples,
            storedParameters = null,
            lastFit = null,
        )

        assertEquals(SchedulingCalibrationView.ParameterSource.DEFAULT, view.parameterSource)
        assertEquals(FsrsScheduleMath.DEFAULT_PARAMETERS.toList(), view.parameters)
        assertNull(view.lastFit)
        assertEquals(1, view.predictableSampleCount)
        assertNotNull("默认参数也要能在同一批验证对上给出损失", view.currentValidationLogLoss)
        assertEquals(view.currentValidationLogLoss!!, view.baselineValidationLogLoss!!, 0.0)
    }

    @Test
    fun `a locally fitted set is attributed to the local fit with its own record`() {
        val samples = twoDayCard()
        val fitted = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also { it[0] = it[0] * 1.5 }
        val record = FsrsParameterFit(
            adoptedAtEpochMillis = 1_760_000_000_000L,
            sampleCount = 1_234,
            validationLogLoss = 0.312,
            baselineValidationLogLoss = 0.355,
        )

        val view = SchedulingCalibrationViewBuilder.build(samples, fitted, record)

        assertEquals(SchedulingCalibrationView.ParameterSource.LOCAL_FIT, view.parameterSource)
        assertEquals(fitted.toList(), view.parameters)
        assertEquals(record, view.lastFit)
    }

    @Test
    fun `parameters without a local fit record are reported as imported not as a local fit`() {
        // 备份恢复走 setOptimizedParameters(备份值)：参数变了，但本机从未拟合过。
        // 把它标成"本地拟合"会让"最近拟合时间"指向一个不存在的本地事件。
        val restored = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also { it[4] = it[4] + 0.25 }

        val view = SchedulingCalibrationViewBuilder.build(
            samples = twoDayCard(),
            storedParameters = restored,
            lastFit = null,
        )

        assertEquals(SchedulingCalibrationView.ParameterSource.IMPORTED, view.parameterSource)
        assertNull(view.lastFit)
    }

    @Test
    fun `current and baseline validation losses are computed on the same split`() {
        val samples = threeCardHistory()
        val fitted = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also {
            it[0] = it[0] * 2.0
            it[4] = it[4] + 0.5
        }

        val view = SchedulingCalibrationViewBuilder.build(samples, fitted, lastFit = null)

        assertEquals(
            "当前参数用当前参数算",
            FsrsParameterOptimizer.validationLogLoss(samples, fitted),
            view.currentValidationLogLoss,
        )
        assertEquals(
            "基线用默认参数、同一批验证对算",
            FsrsParameterOptimizer.validationLogLoss(samples, FsrsScheduleMath.DEFAULT_PARAMETERS),
            view.baselineValidationLogLoss,
        )
    }

    @Test
    fun `per reason rows share the fitting sample scope and sample floors come from single constants`() {
        val samples = listOf(
            sample("unit-1", 0, reason = "DUE_RECALL_RISK", correct = true),
            sample("unit-2", DAY, reason = "DUE_RECALL_RISK", correct = false, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            sample("unit-3", DAY * 2, reason = "WEAK_KNOWLEDGE", correct = false, sourceKind = ReviewSample.REVEAL_KIND),
            sample("unit-4", DAY * 4, reason = "WEAK_KNOWLEDGE", correct = true),
        )

        val view = SchedulingCalibrationViewBuilder.build(samples, null, null)

        assertEquals("MODEL_JUDGED / REVEAL 不进入按原因校准", 2, view.perReason.sumOf { it.sampleCount })
        assertEquals(
            listOf("DUE_RECALL_RISK", "WEAK_KNOWLEDGE"),
            view.perReason.map { it.plannedReason }.sorted(),
        )
    }

    @Test
    fun `exam ramp and planner version are read from their single source constants`() {
        val view = SchedulingCalibrationViewBuilder.build(twoDayCard(), null, null)

        assertEquals(FsrsScheduleMath.PARAMETER_COUNT, view.parameters.size)
        assertEquals(21, view.parameterCount)
        assertEquals(
            AlgorithmConstants.ReviewScoring.EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION,
            view.examCapacityRampMaxExtraFraction,
            0.0,
        )
        assertEquals(ReviewPlannerV2.VERSION, view.plannerVersion)
        assertEquals(FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING, view.fitSampleFloor)
    }

    @Test
    fun `an empty review log yields a view with no predictable samples and no losses`() {
        val view = SchedulingCalibrationViewBuilder.build(emptyList(), null, null)

        assertEquals(0, view.predictableSampleCount)
        assertNull(view.currentValidationLogLoss)
        assertNull(view.baselineValidationLogLoss)
        assertTrue(view.perReason.isEmpty())
    }

    private fun twoDayCard(): List<ReviewSample> = listOf(
        sample("unit-1", 0, reason = "DUE_RECALL_RISK", correct = true),
        sample("unit-1", DAY * 2, reason = "DUE_RECALL_RISK", correct = false),
    )

    /** Three cards, each with two long-range reviews → three validation/train pairs. */
    private fun threeCardHistory(): List<ReviewSample> = buildList {
        repeat(3) { card ->
            val base = DAY * 10 * (card + 1)
            add(sample("unit-$card", base, reason = "DUE_RECALL_RISK", correct = true))
            add(sample("unit-$card", base + DAY * 3 * (card + 1), reason = "WEAK_KNOWLEDGE", correct = card != 1))
            add(sample("unit-$card", base + DAY * 6 * (card + 1), reason = "WEAK_KNOWLEDGE", correct = true))
        }
    }

    private fun sample(
        unitId: String,
        at: Long,
        reason: String?,
        correct: Boolean,
        sourceKind: String = ReviewSample.ATTEMPT_KIND,
    ) = ReviewSample(
        practiceUnitId = unitId,
        reviewedAtEpochMillis = at,
        rating = if (correct) FsrsRating.GOOD else FsrsRating.AGAIN,
        sourceKind = sourceKind,
        plannedReason = reason,
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}
