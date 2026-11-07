package com.tingyun.smartmistakebook.core.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模拟器的两条**合同**（预注册 §8 机检 6 / 7）。它们各自消灭一个具体失败：
 *
 * - [golden world digest is pinned]：模拟里混入未受控随机源（共享 RNG 流、哈希序、并行收集），
 *   同 seed 悄悄漂移而无人察觉。摘要吃 `Double.toHexString`，末位漂移也会变红。
 * - [shorter horizons are a prefix of the longer run]：`horizonDays` 泄漏进**当日决策**
 *   （例如校准窗口按 horizon 截断、`validUntilEpochMillis` 按 horizon 收口），
 *   于是第 30/60/90 天的读数不可加、§6 的保持率与误判率全部作废。
 *
 * 数值口径：预注册 §2.2 冻结的 golden 层 = `seed=5001, horizonDays=60,
 * questionsPerKnowledgeNode=4`（120 题）。摘要 hex 由首次执行得到后**钉住**；
 * 任何一次模拟器行为变化都必须显式重生成这个常量，而不是让它悄悄绿着。
 */
class SimulationDeterminismContractTest {

    @Test
    fun `golden world digest is pinned`() {
        val digest = SimulatedLearner(
            SimulationConfig(
                seed = GOLDEN_SEED,
                horizonDays = GOLDEN_HORIZON_DAYS,
                questionsPerKnowledgeNode = GOLDEN_QUESTIONS_PER_KC,
                // 默认考日 108..120 在 60 天 horizon 下越界（`SimulationConfig.init` 硬要求）；
                // golden 层只钉"世界 + 作答 + 指标"的可复现性，不含考试窗口，故留空。
                examDayBySubject = emptyMap(),
            ),
        ).run().metrics.digest()

        assertEquals(
            "golden 世界（seed=5001/60 天/120 题）的规范化摘要变了：模拟器出现未受控随机源或行为变更",
            GOLDEN_DIGEST,
            digest,
        )
    }

    @Test
    fun `shorter horizons are a prefix of the longer run`() {
        val full = SimulatedLearner(
            SimulationConfig(
                seed = GOLDEN_SEED,
                horizonDays = 120,
                questionsPerKnowledgeNode = GOLDEN_QUESTIONS_PER_KC,
            ),
        ).run()
        listOf(30, 60, 90).forEach { horizon ->
            val short = SimulatedLearner(
                SimulationConfig(
                    seed = GOLDEN_SEED,
                    horizonDays = horizon,
                    questionsPerKnowledgeNode = GOLDEN_QUESTIONS_PER_KC,
                    // 默认考日（108..120）在短 horizon 下越界；考窗最早第 95 天才开始，
                    // 因此 30/60/90 天的决策与"有考试记录"逐位相同。
                    examDayBySubject = emptyMap(),
                ),
            ).run()
            assertTrue(
                "horizon=$horizon 的 review_log 行数超过 120 天 run：horizon 影响了作答本身",
                short.reviewLog.size <= full.reviewLog.size,
            )
            assertEquals(
                "horizon=$horizon 的 review_log 不是 120 天 run 的前缀：horizonDays 泄漏进当日决策",
                short.reviewLog,
                full.reviewLog.take(short.reviewLog.size),
            )
            assertEquals(
                "horizon=$horizon 的 dailyWorkload 不是 120 天 run 的前缀",
                short.metrics.dailyWorkload,
                full.metrics.dailyWorkload.take(horizon),
            )
        }
    }

    private companion object {
        const val GOLDEN_SEED = 5001L
        const val GOLDEN_HORIZON_DAYS = 60
        const val GOLDEN_QUESTIONS_PER_KC = 4

        /** 首次执行（2026-11-08，本机 JDK）得到的 `SimulationMetrics.digest()`。 */
        const val GOLDEN_DIGEST = "55fe92cc6a36aa274dd5da030a3a1e8d98b9364d68445ca7fc1469f88a0aacfe"
    }
}
