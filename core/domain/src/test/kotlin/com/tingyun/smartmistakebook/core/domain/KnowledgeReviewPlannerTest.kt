package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EventTimeTrust
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ReviewReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识点复习排程（spec dual-review-entry §3.2）：`scoreKnowledgeNode` 对单个知识点
 * 打分的纯函数测试——到期风险（lastEvidenceAt 距今 + 遗忘曲线/平滑掌握度）+
 * 掌握度风险（CONFLICTED/STALE/UNKNOWN → 高分），与错题排程 `scoreCandidate`
 * 对"题绑定的知识点"的打分逻辑同构。
 */
class KnowledgeReviewPlannerTest {

    private val planner = ReviewPlanner()
    private val now = 1_000_000_000_000L

    /** An observation whose calibration is SUPPORTED at [now], so it counts as evidence. */
    private fun supportedObservation(atEpochMillis: Long = now) = IndependentCorrectObservation(
        itemFamilyId = "fam1",
        studyDayEpochDay = 20_000,
        occurredAtEpochMillis = atEpochMillis,
        evidenceWeight = 1.0,
        calibration = CalibrationSnapshot(
            support = CalibrationSupport.SUPPORTED,
            sourceId = "test-source",
            version = "test-v0",
            validFromEpochMillis = 0,
            validUntilEpochMillis = Long.MAX_VALUE,
        ),
        timeTrust = EventTimeTrust.TRUSTED,
    )

    private fun mastery(
        id: String,
        status: MasteryStatus,
        conservative: Double = 0.9,
        masteryScore: Double = 0.95,
        lastEvidenceAt: Long? = now,
        observations: List<IndependentCorrectObservation> = emptyList(),
        evidenceMass: Double = 0.0,
        memoryStabilityDays: Double? = null,
        lastAttemptAt: Long? = null,
    ): KnowledgeMasteryState = KnowledgeMasteryState(
        knowledgeNodeId = id,
        masteryScore = masteryScore,
        conservativeMasteryScore = conservative,
        evidenceMass = evidenceMass,
        independentCorrectObservations = observations,
        status = status,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = "test",
        checkpointSequence = 1,
        lastEvidenceAtEpochMillis = lastEvidenceAt,
        memoryStabilityDays = memoryStabilityDays,
        lastAttemptAtEpochMillis = lastAttemptAt,
        lastAttemptStudyDayEpochDay = lastAttemptAt?.let { it / 86_400_000L },
    )

    @Test
    fun conflictedKnowledgeNodeGetsHighScoreAndConflictReason() {
        val score = requireNotNull(planner.scoreKnowledgeNode(
            knowledgeNodeId = "kc1",
            state = mastery("kc1", MasteryStatus.CONFLICTED),
            now = now,
        ))
        assertTrue(score.score >= 1.0)
        assertTrue(score.reasons.contains(ReviewReason.CONFLICTED_KNOWLEDGE))
    }

    @Test
    fun unknownKnowledgeNodeGetsHighScore() {
        val score = requireNotNull(planner.scoreKnowledgeNode(
            knowledgeNodeId = "kc1",
            state = mastery("kc1", MasteryStatus.UNKNOWN),
            now = now,
        ))
        assertTrue(score.score >= 1.0)
        assertTrue(score.reasons.contains(ReviewReason.CALIBRATION_CHECK))
    }

    @Test
    fun staleKnowledgeNodeGetsHighScoreAndStaleReason() {
        val score = requireNotNull(planner.scoreKnowledgeNode(
            knowledgeNodeId = "kc1",
            state = mastery("kc1", MasteryStatus.STALE, lastEvidenceAt = now - 400L * 86_400_000L),
            now = now,
        ))
        assertTrue(score.score >= 1.0)
        assertTrue(score.reasons.contains(ReviewReason.STALE_KNOWLEDGE))
    }

    @Test
    fun masteredFreshKnowledgeNodeIsSkippedFromQueue() {
        // 裁决 28：跳过判据与 E 判据同源——"已掌握"由知识点记忆卡在此刻成立（稳定度 ≥ 21 天
        // ∧ 当前召回概率 ≥ 0.9），不再看存储态 status + 证据新鲜度。夹具给足耐久卡。
        assertNull(planner.scoreKnowledgeNode(
            knowledgeNodeId = "kc1",
            state = mastery(
                "kc1",
                MasteryStatus.MASTERED,
                conservative = 0.9,
                masteryScore = 0.95,
                lastEvidenceAt = now,
                observations = listOf(supportedObservation(now)),
                evidenceMass = 1.0,
                memoryStabilityDays = 30.0,
                lastAttemptAt = now,
            ),
            now = now,
        ))
    }

    @Test
    fun masteredButStaleEvidenceIsScoredForReview() {
        // 已掌握但证据过期（>45 天）→ 不该被 skip，应回到队列复习（含 STALE）。
        val staleAt = now - 100L * 86_400_000L
        val score = requireNotNull(planner.scoreKnowledgeNode(
            knowledgeNodeId = "kc1",
            state = mastery(
                "kc1",
                MasteryStatus.MASTERED,
                conservative = 0.9,
                masteryScore = 0.95,
                lastEvidenceAt = staleAt,
                observations = listOf(supportedObservation(staleAt)),
                evidenceMass = 1.0,
                memoryStabilityDays = 30.0,
                lastAttemptAt = staleAt,
            ),
            now = now,
        ))
        assertTrue(score.reasons.contains(ReviewReason.STALE_KNOWLEDGE))
    }

    @Test
    fun freshlyCommentedNodeStillReturnsForReviewWhenTheMemoryCardIsOverdue() {
        // 裁决 28 双钟回归：chat 通道只写 lastEvidenceAt（评论钟），E 判据吃 lastAttemptAt
        // （记忆卡钟）。旧跳过判据看 lastEvidenceAt——一次讲题对话就能让已忘的知识点从队列里
        // 消失；现在锚点是最后作答，本用例锁死该行为（刚评论过也照样回队列）。
        val score = requireNotNull(planner.scoreKnowledgeNode(
            knowledgeNodeId = "kc1",
            state = mastery(
                "kc1",
                MasteryStatus.MASTERED,
                lastEvidenceAt = now,
                observations = listOf(supportedObservation(now)),
                evidenceMass = 1.0,
                memoryStabilityDays = 30.0,
                lastAttemptAt = now - 60L * 86_400_000L,
            ),
            now = now,
        ))
        assertTrue(score.reasons.contains(ReviewReason.STALE_KNOWLEDGE))
    }

    @Test
    fun weakPrerequisiteKeepsADurableNodeInTheQueue() {
        // 裁决 28 / KF-16 读侧接线：先修未恢复 ⇒ 有效稳定度 = min(自身, 先修) 跌破耐久门，
        // 节点不得被"已掌握则跳过"放行；先修恢复（传入 ≥ 门槛的 S）后回到跳过态。
        val durable = mastery(
            "kc1",
            MasteryStatus.MASTERED,
            lastEvidenceAt = now,
            observations = listOf(supportedObservation(now)),
            evidenceMass = 1.0,
            memoryStabilityDays = 30.0,
            lastAttemptAt = now,
        )
        assertNull(planner.scoreKnowledgeNode("kc1", durable, now))
        val suppressed = requireNotNull(
            planner.scoreKnowledgeNode(
                knowledgeNodeId = "kc1",
                state = durable,
                now = now,
                prerequisiteStabilityDays = listOf(5.0),
            ),
        )
        assertTrue(suppressed.score > 0.0)
        assertNull(
            planner.scoreKnowledgeNode(
                knowledgeNodeId = "kc1",
                state = durable,
                now = now,
                prerequisiteStabilityDays = listOf(null, 30.0),
            ),
        )
    }
}
