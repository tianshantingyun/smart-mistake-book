package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.MasteryDecisionPolicy
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 裁决 28（读侧语义闭合）在展示面的验收锁定：
 * ①「曾经掌握、久不作答」的知识点不再出现在 strengths（存的是 MASTERED 快照，但记忆卡
 *   此刻不达标）；
 * ② chat 证据不能单独保住「强项」身份（双钟：判据吃最后作答，不看评论钟）；
 * ③ KF-16 先修压制在展示面生效——先修未恢复的后继掉出 strengths（读侧接线前
 *   `prerequisiteStabilityDays` 全仓无生产调用点）。
 */
class ReadSideMasteryClosureTest {

    private val t0 = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun durableState(
        id: String,
        stabilityDays: Double = 30.0,
        lastAttemptAtEpochMillis: Long = t0,
        lastEvidenceAtEpochMillis: Long? = lastAttemptAtEpochMillis,
    ) = KnowledgeMasteryState(
        knowledgeNodeId = id,
        masteryScore = 0.95,
        conservativeMasteryScore = 0.9,
        evidenceMass = 2.0,
        successWeight = 13.5,
        failureWeight = 0.5,
        memoryStabilityDays = stabilityDays,
        memoryDifficulty = 6.0,
        lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
        lastAttemptStudyDayEpochDay = lastAttemptAtEpochMillis / day,
        status = MasteryStatus.MASTERED,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = "test-projector",
        checkpointSequence = 0,
        lastEvidenceAtEpochMillis = lastEvidenceAtEpochMillis,
    )

    private fun profileOf(
        vararg states: KnowledgeMasteryState,
        prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>> = emptyMap(),
    ) = LearnerSnapshot.empty("learner-1", projectorVersion = "test-projector")
        .copy(knowledgeMasteryStates = states.associateBy(KnowledgeMasteryState::knowledgeNodeId))
        .toProfileOverview(
            resolvedKnowledgeContexts = emptyMap(),
            fallbackKnowledgeNames = emptyMap(),
            atEpochMillis = t0,
            decay = MasteryDecisionPolicy.DEFAULT.decay,
            prerequisiteStabilityDaysByNode = prerequisiteStabilityDaysByNode,
        )

    @Test
    fun `a forgotten but stored-mastered node drops out of strengths and into weaknesses`() {
        val profile = profileOf(
            durableState("kc-forgotten", stabilityDays = 40.0, lastAttemptAtEpochMillis = t0 - 60 * day),
        )

        assertTrue("已忘的点不得出现在强项，实际：${profile.strengths}", profile.strengths.isEmpty())
        assertEquals(listOf("kc-forgotten"), profile.weaknesses.map { it.knowledgeNodeId })
        assertEquals(MasteryStatus.STALE, profile.weaknesses.single().status)
        assertEquals(0, profile.newlyMasteredCount)
    }

    @Test
    fun `a currently durable node stays in strengths`() {
        val profile = profileOf(durableState("kc-strong", lastAttemptAtEpochMillis = t0 - day))

        assertEquals(listOf("kc-strong"), profile.strengths.map { it.knowledgeNodeId })
        assertTrue(profile.weaknesses.isEmpty())
        assertEquals(1, profile.newlyMasteredCount)
    }

    @Test
    fun `a chat-refreshed but card-overdue node is not a strength`() {
        // 双钟：评论钟刚刚（lastEvidence=t0），记忆卡最后作答在 60 天前——不算强项。
        val profile = profileOf(
            durableState(
                "kc-chat",
                lastAttemptAtEpochMillis = t0 - 60 * day,
                lastEvidenceAtEpochMillis = t0,
            ),
        )

        assertTrue(profile.strengths.isEmpty())
        assertEquals(listOf("kc-chat"), profile.weaknesses.map { it.knowledgeNodeId })
    }

    @Test
    fun `a dependent with an unrecovered prerequisite is suppressed out of strengths`() {
        val durable = durableState("kc-dependent", lastAttemptAtEpochMillis = t0 - day)

        // 对照：无压制时它在 strengths（卡耐久且新鲜）。
        val unsuppressed = profileOf(durable)
        assertEquals(listOf("kc-dependent"), unsuppressed.strengths.map { it.knowledgeNodeId })

        // KF-16：先修 S=5 未恢复 ⇒ 有效稳定度 min(30, 5) 跌破 21 天门，不入 strengths。
        val suppressed = profileOf(
            durable,
            prerequisiteStabilityDaysByNode = mapOf("kc-dependent" to listOf(5.0)),
        )
        assertTrue(suppressed.strengths.isEmpty())
        assertEquals(MasteryStatus.LEARNING, suppressed.weaknesses.single().status)

        // "未知 ≠ 缺失"：先修稳定度未知（null）不压制。
        val unknownPrerequisite = profileOf(
            durable,
            prerequisiteStabilityDaysByNode = mapOf("kc-dependent" to listOf(null, 30.0)),
        )
        assertEquals(listOf("kc-dependent"), unknownPrerequisite.strengths.map { it.knowledgeNodeId })
    }
}
