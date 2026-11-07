package com.tingyun.smartmistakebook.core.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 49 / P1-6：离线模拟学生（[SimulatedLearner]）的门。
 *
 * 这四条用例各自钉住一个**具体的失败**，而不是"跑一遍看看"：
 * 1. 同 seed 逐位复现——消灭"模拟含未受控随机源（HashMap 遍历、共享 RNG 流），
 *    同一 seed 两次跑出不同指标"；换 seed 必须换结果，否则摘要是常量、复现性无意义。
 * 2. 预算守恒——消灭"排程器选超预算 / 模拟层把没作答的题算成工作量"；`f=0` 时
 *    逐日预算必须等于基础预算（G6 窗口外逐位不变的那条不变量在模拟层的第一道护栏）。
 * 3. 留出 seed 不复用——消灭"dev 与 holdout 用同一批世界（或换标签不换世界），
 *    一次性裁决被反复偷看"。
 * 4. 指标非空——消灭"模拟什么都没产出、但四个用例因为全是空集而全绿"。
 *
 * 运行：`./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.simulation.*"`
 */
class SimulatedLearnerTest {

    @Test
    fun `same seed reproduces every metric bit for bit`() {
        val config = SimulationConfig(seed = DEV_SEEDS.first())

        val first = SimulatedLearner(config).run()
        val second = SimulatedLearner(config).run()

        assertEquals("同一 seed 的指标必须逐位相同", first.metrics, second.metrics)
        assertEquals(
            "同一 seed 的规范化摘要必须逐位相同（P1-8 报告的 sha256 载体）",
            first.metrics.digest(),
            second.metrics.digest(),
        )
        assertEquals("同一 seed 的 review_log 必须逐行相同", first.reviewLog, second.reviewLog)
        assertTrue("模拟必须真的写出 review_log 行", first.reviewLog.isNotEmpty())

        val otherSeed = SimulatedLearner(config.copy(seed = DEV_SEEDS[1])).run()
        assertNotEquals(
            "换 seed 必须换世界（否则摘要对 seed 不敏感，复现性只是常量）",
            first.metrics.digest(),
            otherSeed.metrics.digest(),
        )
    }

    @Test
    fun `every simulated day stays inside its review budget`() {
        val run = SimulatedLearner(SimulationConfig(seed = DEV_SEEDS[2])).run()
        val metrics = run.metrics

        assertEquals(
            "horizon 内每天都要有一条工作量记录",
            metrics.horizonDays,
            metrics.dailyWorkload.size,
        )
        metrics.dailyWorkload.forEach { day ->
            assertTrue(
                "第 ${day.day} 天计划 ${day.plannedSeconds}s 超过预算 ${day.budgetSeconds}s",
                day.plannedSeconds <= day.budgetSeconds,
            )
            assertEquals(
                "第 ${day.day} 天入选题数与作答时长必须一一对应（模拟不丢题）",
                day.plannedSeconds,
                day.answeredSeconds,
            )
            assertEquals(
                "f=0 时每日预算必须等于基础预算（窗口外逐位不变的第一护栏）",
                metrics.dailyBudgetSeconds,
                day.budgetSeconds,
            )
            assertTrue("日题数不得为负", day.plannedItemCount >= 0)
        }
        assertEquals("不得出现超发日", 0, metrics.overrunDays)
        assertTrue(
            "总工作量 ${metrics.totalAnsweredSeconds}s 不得超过总预算 ${metrics.totalBudgetSeconds}s",
            metrics.totalAnsweredSeconds <= metrics.totalBudgetSeconds,
        )
        assertTrue("模拟必须真的排过题", metrics.answeredItemCount > 0)
    }

    @Test
    fun `holdout seeds are disjoint from the dev seeds and name distinct worlds`() {
        assertEquals("dev 必须冻结为 10 个 world seed", 10, DEV_SEEDS.size)
        assertEquals("holdout 必须冻结为 10 个 world seed", 10, HOLDOUT_SEEDS.size)

        val shared = DEV_SEEDS.toSet().intersect(HOLDOUT_SEEDS.toSet())
        assertTrue("留出 seed 与训练 seed 不得复用，实际交集=$shared", shared.isEmpty())

        val fingerprints = (DEV_SEEDS + HOLDOUT_SEEDS).map { seed ->
            SimulatedLearner(SimulationConfig(seed = seed)).worldFingerprint()
        }
        assertEquals("20 个 seed 必须给出 20 个互不相同的世界", fingerprints.size, fingerprints.toSet().size)
    }

    @Test
    fun `a full run populates every required metric`() {
        val run = SimulatedLearner(SimulationConfig(seed = DEV_SEEDS[3])).run()
        val metrics = run.metrics

        assertEquals("冻结的世界形状：30 个 KC", 30, metrics.knowledgeNodeCount)
        assertEquals("冻结的世界形状：300 道题", 300, metrics.questionCount)
        assertTrue(
            "必须有可评分的概率预测（新卡没有先验 R，不计入 Brier/ECE）",
            metrics.scoredPredictionCount > 0,
        )
        assertTrue("冷启动段必须真的引入过新卡", metrics.newCardAttemptCount > 0)

        val brier = requireNotNull(metrics.brierScore) { "Brier 不得为 null（无数据必须 BLOCKED，不是 0.0）" }
        val ece = requireNotNull(metrics.expectedCalibrationError) {
            "ECE 不得为 null（无数据必须 BLOCKED，不是 0.0）"
        }
        assertTrue("Brier 必须落在 0..1，实际 $brier", brier in 0.0..1.0)
        assertTrue("ECE 必须落在 0..1，实际 $ece", ece in 0.0..1.0)

        assertEquals("5 科必须有 5 条覆盖记录", 5, metrics.perSubjectCoverage.size)
        assertEquals("5 场考试必须有 5 条覆盖记录", 5, metrics.examSubjectCoverage.size)
        metrics.perSubjectCoverage.forEach { coverage ->
            assertTrue(
                "${coverage.subjectId} 的覆盖率 ${coverage.reviewedShare} 必须在 0..1",
                coverage.reviewedShare in 0.0..1.0,
            )
            assertTrue(
                "${coverage.subjectId} 在整个 horizon 内至少复习过一个 KC",
                coverage.reviewedKnowledgeNodeCount > 0,
            )
        }
        metrics.examSubjectCoverage.forEach { coverage ->
            assertTrue(
                "${coverage.subjectId} 考前窗口覆盖率 ${coverage.reviewedShare} 必须在 0..1",
                coverage.reviewedShare in 0.0..1.0,
            )
        }

        assertTrue("per-reason recall 必须有样本", metrics.perReasonRecall.isNotEmpty())
        assertEquals(
            "per-reason 分组的样本总数必须覆盖全部复习行",
            metrics.answeredItemCount,
            metrics.perReasonRecall.sumOf { it.sampleCount },
        )

        assertTrue(
            "掌握数 ${metrics.masteredKnowledgeNodeCount} 必须落在 0..${metrics.knowledgeNodeCount}",
            metrics.masteredKnowledgeNodeCount in 0..metrics.knowledgeNodeCount,
        )
        assertEquals("review_log 行数必须等于作答数", metrics.answeredItemCount, run.reviewLog.size)
    }

    @Test
    fun `the careless rate actually moves the simulated outcomes`() {
        val seed = DEV_SEEDS[4]
        val careful = SimulatedLearner(SimulationConfig(seed = seed, carelessRate = 0.0)).run()
        val careless = SimulatedLearner(SimulationConfig(seed = seed, carelessRate = 0.2)).run()

        fun recall(run: SimulationRun): Double =
            run.reviewLog.count { it.isCorrect }.toDouble() / run.reviewLog.size

        assertTrue(
            "slip=0 的实际召回 ${recall(careful)} 必须高于 slip=0.2 的 ${recall(careless)}" +
                "（否则 careless 旋钮没接线）",
            recall(careful) > recall(careless),
        )
        assertNotEquals(
            "两个世界错配臂必须产生不同的规范化摘要",
            careful.metrics.digest(),
            careless.metrics.digest(),
        )
    }

    @Test
    fun `a perturbed truth parameter set changes the simulated world`() {
        val seed = DEV_SEEDS[5]
        // R1 T9 / §5.1：真值 = 先验中心时，拟合"恢复"可能只是 L2 罚项把估计拉回先验。
        // P1-8 的扰动臂依赖这个旋钮真的进了投影器；收下不用就是死参数。
        val perturbedParameters = com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
            .DEFAULT_PARAMETERS.copyOf()
            .also { it[20] = it[20] * 1.3 }

        val centered = SimulatedLearner(SimulationConfig(seed = seed)).run()
        val perturbed = SimulatedLearner(
            SimulationConfig(seed = seed, truthParameters = perturbedParameters),
        ).run()

        assertNotEquals(
            "扰动真值必须改变世界（否则 truthParameters 没接线）",
            centered.metrics.digest(),
            perturbed.metrics.digest(),
        )
    }

    private companion object {
        /**
         * 三层集合中的 dev 层（10 个 world seed，可反复用于开发/调网格）。
         * 与 [HOLDOUT_SEEDS] 不同基，且由用例第三条机检不相交。
         */
        val DEV_SEEDS = listOf(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L)

        /**
         * 三层集合中的 holdout 层（10 个 world seed，一次性裁决；看后再改 = 新预注册）。
         * 取值故意换基（9xx）而不是 dev 的邻位，避免"相邻 seed 看起来不同、世界其实同构"。
         */
        val HOLDOUT_SEEDS = listOf(901L, 902L, 903L, 904L, 905L, 906L, 907L, 908L, 909L, 910L)
    }
}
