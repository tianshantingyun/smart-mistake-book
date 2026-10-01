package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 裁决 28（读侧语义闭合）的核心出口 [ClearlyMasteredForSkipPolicy.effectiveStatus]：
 * 把"写入瞬间的 status 快照"与"此刻的记忆卡事实"合成为读侧状态。
 *
 * 消灭的失败：已忘的知识点被展示成「强项」、被跳过判据放行——状态只在投影写入时更新，
 * 没有时间驱动的刷新；各消费点此前各自用 45 天窗补救。本套用例把出口的每条分支与边界
 * 钉死（含双钟、时钟回拨、KF-16 压制）。
 */
class MasteryEffectiveStatusTest {

    private val t0 = 1_000_000_000_000L
    private val day = 86_400_000L
    private val defaultDecay = MasteryDecisionPolicy.DEFAULT.decay

    private fun state(
        status: MasteryStatus = MasteryStatus.MASTERED,
        stabilityDays: Double? = 30.0,
        lastAttemptAt: Long? = t0,
        lastEvidenceAt: Long? = lastAttemptAt,
        evidenceMass: Double = 2.0,
    ) = KnowledgeMasteryState(
        knowledgeNodeId = "kc-a",
        masteryScore = 0.95,
        conservativeMasteryScore = 0.9,
        evidenceMass = evidenceMass,
        memoryStabilityDays = stabilityDays,
        memoryDifficulty = 6.0,
        lastAttemptAtEpochMillis = lastAttemptAt,
        lastAttemptStudyDayEpochDay = lastAttemptAt?.let { it / day },
        status = status,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = "test",
        checkpointSequence = 1,
        lastEvidenceAtEpochMillis = lastEvidenceAt,
    )

    private fun resolve(atEpochMillis: Long, state: KnowledgeMasteryState?, prerequisiteStabilityDays: List<Double?> = emptyList()) =
        ClearlyMasteredForSkipPolicy.effectiveStatus(
            state = state,
            atEpochMillis = atEpochMillis,
            decay = defaultDecay,
            prerequisiteStabilityDays = prerequisiteStabilityDays,
        )

    @Test
    fun `no state and empty evidence both resolve to unknown`() {
        assertEquals(MasteryStatus.UNKNOWN, resolve(t0, null))
        assertEquals(MasteryStatus.UNKNOWN, resolve(t0, state(evidenceMass = 0.0)))
    }

    @Test
    fun `a durable card with a recent attempt is mastered`() {
        assertEquals(MasteryStatus.MASTERED, resolve(t0, state()))
    }

    @Test
    fun `the durability bar is inclusive at 21 days`() {
        // 边界：S=21.0 恰好达标（ε 容差），20.9 掉出门外。
        assertEquals(MasteryStatus.MASTERED, resolve(t0, state(stabilityDays = 21.0)))
        assertEquals(MasteryStatus.LEARNING, resolve(t0, state(stabilityDays = 20.9)))
    }

    @Test
    fun `retention decay flips mastered to learning and then to stale`() {
        // S=30 的耐久卡：+20 天时 R≈0.925 仍达标；+40 天时 R≈0.879 跌破 0.9（凭记忆卡自动
        // 降档，不依赖任何新事件）；+60 天时证据锚点超过 45 天从窗外 → STALE。
        assertEquals(MasteryStatus.MASTERED, resolve(t0 + 20 * day, state()))
        assertEquals(MasteryStatus.LEARNING, resolve(t0 + 40 * day, state()))
        assertEquals(MasteryStatus.STALE, resolve(t0 + 60 * day, state()))
    }

    @Test
    fun `the chat clock alone cannot keep a node mastered`() {
        // 双钟：lastEvidenceAt（评论钟）刚刚，但 lastAttemptAt（记忆卡钟）已 60 天——E 判据
        // 吃记忆卡，判 STALE。旧跳过判据只看 lastEvidenceAt，会把这种点当作"新鲜已掌握"。
        assertEquals(
            MasteryStatus.STALE,
            resolve(t0, state(lastAttemptAt = t0 - 60 * day, lastEvidenceAt = t0)),
        )
    }

    @Test
    fun `a missing evidence anchor resolves to stale`() {
        assertEquals(
            MasteryStatus.STALE,
            resolve(t0, state(lastAttemptAt = null, lastEvidenceAt = null)),
        )
    }

    @Test
    fun `clock rollback keeps the node out of mastered`() {
        // 判定时刻早于最后作答（设备时钟回拨）→ 不信任，判 STALE（保守：回队列重新确认）。
        assertEquals(
            MasteryStatus.STALE,
            resolve(t0 - day, state(lastAttemptAt = t0)),
        )
    }

    @Test
    fun `conflicted stored status keeps precedence over a durable card`() {
        // 未恢复的 CONFLICTED 是写入侧事实（独立答错撤销了掌握，恢复要走证据链），读侧不改判——
        // 即使记忆卡耐久且新鲜。
        assertEquals(MasteryStatus.CONFLICTED, resolve(t0, state(status = MasteryStatus.CONFLICTED)))
    }

    @Test
    fun `prerequisite suppression vetoes mastery but unknown prerequisites do not`() {
        assertEquals(MasteryStatus.MASTERED, resolve(t0, state()))
        assertEquals(MasteryStatus.LEARNING, resolve(t0, state(), prerequisiteStabilityDays = listOf(5.0)))
        assertEquals(
            MasteryStatus.MASTERED,
            resolve(t0, state(), prerequisiteStabilityDays = listOf(null, 30.0)),
        )
    }
}
