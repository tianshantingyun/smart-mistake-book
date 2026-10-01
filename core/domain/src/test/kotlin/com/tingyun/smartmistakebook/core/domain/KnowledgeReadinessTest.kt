package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * spec §2.9 的前置判定。这是排程侧（降权 + `PREREQ_GAP` 理由）与会话侧（注入补救材料）
 * 共用的唯一权威，因此这里锁定的既包括数值口径，也包括"哪一个是阻塞前置"的选择规则。
 */
class KnowledgeReadinessTest {

    private val prerequisites = mapOf(
        "kc-target" to setOf("kc-weak", "kc-ready"),
    )

    private val mastery = mapOf(
        "kc-weak" to 0.25,
        "kc-ready" to 0.9,
    )

    @Test
    fun `the gap is measured from the weakest prerequisite`() {
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = prerequisites,
            masteryScoreOf = mastery::get,
        )

        assertEquals("kc-weak", blocking?.prerequisiteKnowledgeNodeId)
        assertEquals(0.6 - 0.25, requireNotNull(blocking).gap, 1e-9)
    }

    @Test
    fun `a prerequisite exactly at the threshold is not blocking`() {
        // τ_ready 是"已具备"的下界（含）。把等号归到缺失一侧会让每一道刚好达标的题都被
        // 降权并弹补救卡，闸门从"信号"退化为"噪声"。
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = mapOf("kc-target" to setOf("kc-exact")),
            masteryScoreOf = { 0.6 },
        )

        assertNull(blocking)
    }

    @Test
    fun `a prerequisite with no mastery evidence is not treated as missing`() {
        // 未知 ≠ 不会。若把未知当缺失，任何新绑定的前置关系都会立刻把题判成"前置缺失"。
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = mapOf("kc-target" to setOf("kc-never-seen")),
            masteryScoreOf = { null },
        )

        assertNull(blocking)
    }

    @Test
    fun `an unknown prerequisite does not mask a known weak one`() {
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = mapOf("kc-target" to setOf("kc-never-seen", "kc-weak")),
            masteryScoreOf = { id -> if (id == "kc-weak") 0.2 else null },
        )

        assertEquals("kc-weak", blocking?.prerequisiteKnowledgeNodeId)
    }

    @Test
    fun `a node with no recorded prerequisites has no gap`() {
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("pseudo:MATH"),
            prerequisitesByNode = emptyMap(),
            masteryScoreOf = mastery::get,
        )

        assertNull(blocking)
    }

    @Test
    fun `equally weak prerequisites resolve deterministically by id`() {
        // 同为最弱时按 id 定序：否则会话可能在两个同样弱的前置之间反复改选，学员每次打开
        // 看到的补救材料都不一样。
        val tied = mapOf("kc-target" to setOf("kc-bbb", "kc-aaa"))
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = tied,
            masteryScoreOf = { 0.3 },
        )

        assertEquals("kc-aaa", blocking?.prerequisiteKnowledgeNodeId)

        // 与集合迭代顺序无关。
        val reversed = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = mapOf("kc-target" to setOf("kc-aaa", "kc-bbb")),
            masteryScoreOf = { 0.3 },
        )
        assertEquals("kc-aaa", reversed?.prerequisiteKnowledgeNodeId)
    }

    @Test
    fun `the weakest prerequisite is taken across every knowledge node of the question`() {
        // 一道题绑定多个 KC 时，任何一个 KC 的前置缺失都构成阻塞——每题先各取最弱前置、
        // 再取全局最弱，与"取最大 gap"同序。
        val blocking = KnowledgeReadiness.weakestBlockingPrerequisite(
            knowledgeNodeIds = setOf("kc-target", "kc-other"),
            prerequisitesByNode = mapOf(
                "kc-target" to setOf("kc-ready"),
                "kc-other" to setOf("kc-worst"),
            ),
            masteryScoreOf = { id -> if (id == "kc-worst") 0.1 else 0.9 },
        )

        assertEquals("kc-worst", blocking?.prerequisiteKnowledgeNodeId)
        assertEquals(0.5, requireNotNull(blocking).gap, 1e-9)
    }

    @Test
    fun `the numeric projection agrees with the blocking prerequisite`() {
        // 排程侧只用数值投影。两处必须由同一判定导出，否则"排程降权但不给补救"或反过来
        // 都会成为可能。
        val args = setOf("kc-target")
        val gap = KnowledgeReadiness.gapOf(
            knowledgeNodeIds = args,
            prerequisitesByNode = prerequisites,
            masteryScoreOf = mastery::get,
        )

        assertEquals(
            requireNotNull(
                KnowledgeReadiness.weakestBlockingPrerequisite(args, prerequisites, mastery::get),
            ).gap,
            gap,
            1e-9,
        )
        assertEquals(0.35, gap, 1e-9)
    }

    @Test
    fun `no blocking prerequisite projects to a zero gap`() {
        val gap = KnowledgeReadiness.gapOf(
            knowledgeNodeIds = setOf("kc-target"),
            prerequisitesByNode = emptyMap(),
            masteryScoreOf = mastery::get,
        )

        assertEquals(0.0, gap, 1e-9)
    }

    @Test
    fun `effective stability is suppressed by the weakest prerequisite`() {
        // KF-16（裁决 7，E 口径重述）：有效稳定度 = min(自身 S, 各先修 S 最小值)。
        // 台账锚点（原以"下界"表述，0.4/0.9）：先修弱 → 有效取先修；先修恢复 → 解除。
        assertEquals(
            0.4,
            requireNotNull(KnowledgeReadiness.effectiveStabilityDays(0.9, listOf(0.4))),
            1e-9,
        )
        assertEquals(
            0.4,
            requireNotNull(KnowledgeReadiness.effectiveStabilityDays(0.9, listOf(0.4, 30.0))),
            1e-9,
        )
        assertEquals(
            0.9,
            requireNotNull(KnowledgeReadiness.effectiveStabilityDays(0.9, listOf(30.0))),
            1e-9,
        )
    }

    @Test
    fun `unknown prerequisite stability is not treated as missing`() {
        // 与"未知 ≠ 不会"同一约定：没有记忆卡的前置不参与压制（否则新绑定会立刻压死后继）。
        assertEquals(
            0.9,
            requireNotNull(KnowledgeReadiness.effectiveStabilityDays(0.9, listOf(null, 30.0))),
            1e-9,
        )
        assertEquals(
            0.9,
            requireNotNull(KnowledgeReadiness.effectiveStabilityDays(0.9, emptyList())),
            1e-9,
        )
    }

    @Test
    fun `a dependent without its own card has no effective stability`() {
        assertNull(KnowledgeReadiness.effectiveStabilityDays(null, listOf(0.4)))
        assertNull(KnowledgeReadiness.effectiveStabilityDays(null, emptyList()))
    }
}
