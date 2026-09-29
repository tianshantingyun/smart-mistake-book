package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-Q9 的两半：**模式的默认出口**与**卡点升档的纯策略**。
 *
 * 判别格是"哪些事实会改变起步档"：正常模式恒为无阶梯；引导模式从 L1 起步；连续失败与耗时异常
 * 各升一档、**最多到 L3**（L4 只能由学生索要）；封顶参数压在下面。升档只活在引导模式内——
 * 这条边界是本文件里最要紧的一条。
 */
class TutorScaffoldPolicyTest {
    @Test
    fun theNormalModeHasNoLadderAtAll() {
        // 有问即答、不检测卡点（D-Q9 第 3 条）：即便本地攒了一堆卡点证据也不升档。
        assertNull(tutorScaffoldDirective(TutorInteractionMode.NORMAL))
        assertNull(
            tutorScaffoldDirective(
                mode = TutorInteractionMode.NORMAL,
                evidence = TutorScaffoldEvidence(
                    consecutiveFailedRounds = 5,
                    lastRoundDurationMillis = 10 * LONG_ROUND_MILLIS,
                ),
            ),
        )
    }

    @Test
    fun theGuidedModeStartsAtTheSelfTryStep() {
        assertEquals(
            TutorScaffoldLevel.L1,
            tutorScaffoldDirective(
                mode = TutorInteractionMode.GUIDED,
                evidence = TutorScaffoldEvidence.NONE,
            ),
        )
    }

    @Test
    fun consecutiveFailuresEscalateOneStepEach() {
        val oneFailure = tutorScaffoldDirective(
            mode = TutorInteractionMode.GUIDED,
            evidence = TutorScaffoldEvidence(consecutiveFailedRounds = FAILED_ROUNDS_PER_STEP),
        )
        val twoFailures = tutorScaffoldDirective(
            mode = TutorInteractionMode.GUIDED,
            evidence = TutorScaffoldEvidence(consecutiveFailedRounds = FAILED_ROUNDS_PER_STEP * 2),
        )

        assertEquals(TutorScaffoldLevel.L2, oneFailure)
        assertEquals(TutorScaffoldLevel.L3, twoFailures)
    }

    @Test
    fun aLongRoundEscalatesOneStep() {
        assertEquals(
            TutorScaffoldLevel.L2,
            tutorScaffoldDirective(
                mode = TutorInteractionMode.GUIDED,
                evidence = TutorScaffoldEvidence(lastRoundDurationMillis = LONG_ROUND_MILLIS),
            ),
        )
        // 差一毫秒不算：门是"达到"而不是"接近"。
        assertEquals(
            TutorScaffoldLevel.L1,
            tutorScaffoldDirective(
                mode = TutorInteractionMode.GUIDED,
                evidence = TutorScaffoldEvidence(lastRoundDurationMillis = LONG_ROUND_MILLIS - 1),
            ),
        )
    }

    @Test
    fun theAutomaticLadderNeverReachesTheFullSolution() {
        // L4 = 完整解法，只能由学生明确索要（提示词里明说）。本地自动升到顶等于把"自己试"
        // 这一档整体取消——这条底线就是这个测试守的东西。
        val worstCase = tutorScaffoldDirective(
            mode = TutorInteractionMode.GUIDED,
            evidence = TutorScaffoldEvidence(
                consecutiveFailedRounds = 100,
                lastRoundDurationMillis = 100 * LONG_ROUND_MILLIS,
            ),
        )

        assertEquals(TutorScaffoldLevel.L3, worstCase)
    }

    @Test
    fun aCeilingCompressesTheEscalatedStep() {
        // 复习栏"作答前封顶 L2"（阶段 5）用的就是这条参数：升档照样发生，但不越过封顶。
        assertEquals(
            TutorScaffoldLevel.L2,
            tutorScaffoldDirective(
                mode = TutorInteractionMode.GUIDED,
                evidence = TutorScaffoldEvidence(consecutiveFailedRounds = 3),
                ceiling = TutorScaffoldLevel.L2,
            ),
        )
        assertEquals(
            TutorScaffoldLevel.L1,
            tutorScaffoldDirective(
                mode = TutorInteractionMode.GUIDED,
                evidence = TutorScaffoldEvidence.NONE,
                ceiling = TutorScaffoldLevel.L1,
            ),
        )
    }

    @Test
    fun theLadderOrderIsTheOneThePromptTeaches() {
        // 顺序错了（比如把 L3 当成"给答案"）模型与本地就会互相打架：刻度本身的顺序在这里钉住。
        assertEquals(
            listOf(
                TutorScaffoldLevel.L0,
                TutorScaffoldLevel.L1,
                TutorScaffoldLevel.L2,
                TutorScaffoldLevel.L3,
                TutorScaffoldLevel.L4,
            ),
            TutorScaffoldLevel.entries.toList(),
        )
        assertTrue(TutorScaffoldLevel.L2.guidance.contains("不给解法"))
        assertTrue(TutorScaffoldLevel.L4.guidance.contains("完整"))
        assertEquals(TutorScaffoldLevel.L4, TutorScaffoldLevel.L3.escalate())
        assertEquals(TutorScaffoldLevel.L4, TutorScaffoldLevel.L4.escalate())
        assertEquals(TutorScaffoldLevel.L0, TutorScaffoldLevel.L0.cappedAt(TutorScaffoldLevel.L2))
    }

    @Test
    fun thePromptBlockCarriesTheLadderOnlyInTheGuidedMode() {
        val normal = tutorScaffoldPromptBlock(TutorInteractionMode.NORMAL, level = null)
        val guided = tutorScaffoldPromptBlock(TutorInteractionMode.GUIDED, level = TutorScaffoldLevel.L2)

        assertTrue(normal.contains("不设提示阶梯"))
        assertFalse(normal.contains(TutorScaffoldLevel.L2.name))
        TutorScaffoldLevel.entries.forEach { level ->
            assertTrue(
                "引导模式的提示词必须逐个列出刻度：缺 ${level.name}",
                guided.contains(level.name),
            )
        }
        assertTrue(guided.contains("本轮起步档：${TutorScaffoldLevel.L2.name}"))
        // 升过档的那一句只在真的升过档时出现（依据就是档位本身，不靠额外一个布尔位）。
        assertTrue(guided.contains("多给一步"))
        assertFalse(
            tutorScaffoldPromptBlock(TutorInteractionMode.GUIDED, TutorScaffoldLevel.L1)
                .contains("多给一步"),
        )
        assertTrue(
            tutorScaffoldPromptBlock(TutorInteractionMode.GUIDED, level = null)
                .contains("学生明确索要答案时"),
        )
    }

    @Test
    fun theModeSwitchesAreStudentFacingWords() {
        // 界面不出现内部词：模式名、脚手架、L0–L4 一律不上屏（学生语言纪律）。
        val labels = TutorInteractionMode.entries.flatMap { mode ->
            listOf(mode.displayLabel, mode.switchActionLabel, mode.displayDescription)
        }

        labels.forEach { label ->
            assertTrue(label.isNotBlank())
            assertFalse(label.contains("模式"))
            assertFalse(label.contains("脚手架"))
            assertFalse(label.contains(TutorScaffoldLevel.L0.name))
        }
        assertEquals(
            setOf("直接回答", "带着我一步步来"),
            TutorInteractionMode.entries.map(TutorInteractionMode::displayLabel).toSet(),
        )
    }
}
