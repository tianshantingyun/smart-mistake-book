package com.tingyun.smartmistakebook.core.domain.simulation

import com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationHarness
import com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationReport
import com.tingyun.smartmistakebook.core.domain.fittableReviewSamples
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 49 / P1-10 上线门（模拟侧）：`evaluateSchedulingModels` 的同一判据
 * `SchedulingEvaluationReport.fsrsBeatsBaseline` 必须在模拟学习者的 review_log 上成立。
 *
 * 消灭的具体失败：计划 P1-10 要求「在模拟+真库断言 fsrsBeatsBaseline」，但批次 49
 * 报告把 A 线整体记为 BLOCKED 且模拟侧也没跑——上线门只有一句「未运行」，没有任何
 * 可复算的断言。本用例直接复用生产评估器（不是另写一份指标）对模拟 review_log 求值：
 * 样本数不足 200 或 FSRS logLoss 不低于基线时失败，而不是靠“模拟世界是 FSRS 生成的”
 * 这一先验默认通过。
 *
 * 真库侧仍是 **BLOCKED**：本机零真库样本，不伪造数字；出口见报告 §A。
 *
 * 运行：
 *   ./gradlew :core:domain:test --tests \
 *     "com.tingyun.smartmistakebook.core.domain.simulation.SchedulingEvaluationOnSimulationTest"
 */
class SchedulingEvaluationOnSimulationTest {

    @Test
    fun `fsrs beats the exponential baseline on the simulated review log`() {
        // 预注册冻结的 10 个 dev seed（ReviewCalibrationExperimentTest 的 DEV_SEEDS 是局部
        // 值，不能跨文件引用；这里用同一字面量）。单 world 只有 ~107 个可评估样本，
        // 低于评估器的 200 样本下限；按 world 前缀隔离 practiceUnitId 后合并，避免把
        // 不同世界的同一道题当成一条连续卡史。
        val samples = (101L..110L).flatMap { seed ->
            val run = SimulatedLearner(SimulationConfig(seed = seed)).run()
            fittableReviewSamples(run.reviewLog).map {
                it.copy(practiceUnitId = "seed$seed:${it.practiceUnitId}")
            }
        }

        assertTrue(
            "模拟 review_log 必须提供够 200 的可评估样本（否则 fsrsBeatsBaseline 的门无法成立）",
            samples.size >= SchedulingEvaluationReport.MIN_EVALUATION_SAMPLES,
        )

        val report = SchedulingEvaluationHarness.evaluate(samples)
        println(
            "[batch49-p1-10] samples=${samples.size} " +
                "fsrsLogLoss=${report.fsrs.logLoss} baselineLogLoss=${report.baseline.logLoss} " +
                "fsrsValid=${report.fsrs.isValidModel} baselineValid=${report.baseline.isValidModel} " +
                "sampleCount=${report.evaluationSampleCount} beats=${report.fsrsBeatsBaseline}",
        )

        assertTrue(
            "FSRS 必须在同一证据流上击败指数基线：fsrs=${report.fsrs.logLoss} " +
                "baseline=${report.baseline.logLoss} samples=${report.evaluationSampleCount}",
            report.fsrsBeatsBaseline,
        )
    }
}
