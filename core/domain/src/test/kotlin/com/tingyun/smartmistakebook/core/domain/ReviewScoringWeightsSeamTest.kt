package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ReviewPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 权重注入 seam 的机检 S3（`docs/research/2026-11-08-review-calibration-prereg.md` §5.1）。
 *
 * 消灭的具体失败（缺了这条用例会发生什么）：
 * - `ReviewPlannerV2(scoring = ReviewScoringWeights.DEFAULT)` 与不传权重**不是**同一条路径
 *   （某处仍引用旧 `private const val` / 某字段没接上）→ 标定实验的"空臂"不是产品现状，
 *   所有 Δ 都失去基线；
 * - `ReviewScoringWeights` 的默认值与 `AlgorithmConstants.ReviewScoring` 漂移 → 静悄悄地
 *   改了产品权重却没 bump 版本、没重生成金样；
 * - seam 是**死旋钮**（参数收下但没进打分式）→ 17 个权重臂全跑出同一个数，报告会写成
 *   "无可测差异"，而事实是"根本没接上"。
 *
 * 夹具范围说明：预注册 S3 写的是"在三组金样夹具上"。三组金样的夹具生成器是
 * [ReviewPlannerScaleBenchmarkTest] 的 `private` 函数；S1（三组金样逐位不变）已由该用例
 * 直接覆盖**隐式默认**路径。本文件用一份自建的、覆盖全部 10 个打分项的确定性夹具覆盖
 * **显式 DEFAULT** 路径与 seam 活性，避免把同一夹具复制成第二份权威。
 */
class ReviewScoringWeightsSeamTest {

    @Test
    fun `explicit DEFAULT scores bit for bit like the implicit default`() {
        val implicitPlan = ReviewPlannerV2().plan(request())
        val explicitPlan = ReviewPlannerV2(scoring = ReviewScoringWeights.DEFAULT).plan(request())

        assertEquals(
            "显式传 ReviewScoringWeights.DEFAULT 与不传权重的计划指纹必须逐位相同",
            implicitPlan.planFingerprint,
            explicitPlan.planFingerprint,
        )
        assertEquals(selection(implicitPlan), selection(explicitPlan))
    }

    @Test
    fun `default weights equal the product constants they replace`() {
        val weights = ReviewScoringWeights.DEFAULT
        assertEquals(AlgorithmConstants.ReviewScoring.DUE_WEIGHT, weights.dueWeight, 0.0)
        assertEquals(AlgorithmConstants.ReviewScoring.WEAKNESS_WEIGHT, weights.weaknessWeight, 0.0)
        assertEquals(AlgorithmConstants.ReviewScoring.LAPSE_WEIGHT, weights.lapseWeight, 0.0)
        assertEquals(
            AlgorithmConstants.ReviewScoring.REPEAT_MISTAKE_WEIGHT,
            weights.repeatMistakeWeight,
            0.0,
        )
        assertEquals(AlgorithmConstants.ReviewScoring.AVOIDANCE_WEIGHT, weights.avoidanceWeight, 0.0)
        assertEquals(AlgorithmConstants.ReviewScoring.EXAM_WEIGHT, weights.examWeight, 0.0)
        assertEquals(AlgorithmConstants.ReviewScoring.WAITING_WEIGHT, weights.waitingWeight, 0.0)
        assertEquals(
            AlgorithmConstants.ReviewScoring.FAMILY_PENALTY_WEIGHT,
            weights.familyPenaltyWeight,
            0.0,
        )
        assertEquals(
            AlgorithmConstants.ReviewScoring.SOURCE_PENALTY_WEIGHT,
            weights.sourcePenaltyWeight,
            0.0,
        )
        assertEquals(
            AlgorithmConstants.ReviewScoring.MAX_DIVERSITY_PENALTY,
            weights.maxDiversityPenalty,
            0.0,
        )
        assertEquals(2.5, weights.kcDropWeight, 0.0)
    }

    /**
     * seam 活性：每个坐标单独放大都必须让计划至少变一次。若某个坐标不改变任何输出，
     * 说明这个权重在本夹具里没有消费点（或根本没接线），标定实验不能把它当"无可测差异"。
     */
    @Test
    fun `every scoring coordinate is actually consumed by the planner`() {
        val baseline = ReviewPlannerV2().plan(request()).planFingerprint
        val probes = mapOf(
            "due" to ReviewScoringWeights.DEFAULT.copy(dueWeight = 0.0),
            "weakness" to ReviewScoringWeights.DEFAULT.copy(weaknessWeight = 0.0),
            "lapse" to ReviewScoringWeights.DEFAULT.copy(lapseWeight = 0.0),
            "repeatMistake" to ReviewScoringWeights.DEFAULT.copy(repeatMistakeWeight = 0.0),
            "avoidance" to ReviewScoringWeights.DEFAULT.copy(avoidanceWeight = 0.0),
            "kcDrop" to ReviewScoringWeights.DEFAULT.copy(kcDropWeight = 0.0),
            "exam" to ReviewScoringWeights.DEFAULT.copy(examWeight = 0.0),
            "waiting" to ReviewScoringWeights.DEFAULT.copy(waitingWeight = 0.0),
            "diversity" to ReviewScoringWeights.DEFAULT.copy(
                familyPenaltyWeight = 0.0,
                sourcePenaltyWeight = 0.0,
            ),
        )
        probes.forEach { (coordinate, weights) ->
            assertNotEquals(
                "权重坐标 $coordinate 置零后计划指纹没变：这个权重没有被消费（死旋钮）",
                baseline,
                ReviewPlannerV2(scoring = weights).plan(request()).planFingerprint,
            )
        }
    }

    private fun selection(plan: ReviewPlan): String =
        plan.queueItems.joinToString(";") { item ->
            val reasons = item.reasons.map { it.name }.sorted().joinToString(",")
            "${item.practiceUnitId}[$reasons]${java.lang.Double.toHexString(item.priorityScore)}"
        }

    private fun request(): ReviewPlanningRequest = ReviewPlanningRequest(
        learnerSnapshot = snapshot(),
        candidates = listOf(
            // 到期 + 近期遗忘 + 重复错题 + 回避：四条打分项同时在场。
            candidate("unit-leech", "family-0", "source-1", difficulty = 3.0, seconds = 60, subject = "s0")
                .copy(repeatMistakePriority = 0.6, avoidance = true),
            candidate("unit-due", "family-1", "source-1", difficulty = 5.0, seconds = 60, subject = "s0"),
            candidate("unit-exam", "family-2", "source-2", difficulty = 6.0, seconds = 60, subject = "s1")
                .copy(examPriority = 0.9),
            candidate("unit-waiting", "family-0", "source-2", difficulty = 7.0, seconds = 60, subject = "s1")
                .copy(eligibleSinceEpochMillis = NOW - 40L * DAY_MILLIS),
            candidate("unit-weak", "family-3", "source-3", difficulty = 8.0, seconds = 60, subject = "s0", kc = "kc-b"),
            candidate("unit-plain", "family-4", "source-4", difficulty = 9.0, seconds = 60, subject = "s0"),
            candidate("unit-plain-2", "family-5", "source-5", difficulty = 9.5, seconds = 60, subject = "s1"),
        ),
        localDayEpochDay = 400,
        timeZoneId = "Asia/Shanghai",
        // 60s × 5 支撑的前提下，60s 的题最多进 5 道：让"边际收益"排序真的决定谁被砍掉。
        timeBudgetSeconds = 300,
        planningAtEpochMillis = NOW,
    )

    private fun candidate(
        unitId: String,
        familyId: String,
        sourceId: String?,
        difficulty: Double,
        seconds: Int,
        subject: String,
        kc: String = "kc-a",
    ) = ReviewCandidate(
        practiceUnitId = unitId,
        knowledgeNodeIds = setOf(kc),
        itemFamilyId = familyId,
        sourceBundleId = sourceId,
        subjectId = subject,
        difficulty = difficulty,
        estimatedDurationSeconds = seconds,
    )

    private fun snapshot(): LearnerSnapshot {
        val masteryA = masteryState("kc-a", conservative = 0.2)
        val masteryB = masteryState("kc-b", conservative = 0.1)
        return LearnerSnapshot(
            learnerId = "learner-seam",
            problemMemoryStates = mapOf(
                // 逾期最久 + 近期遗忘：DUE / LAPSE 两条权重都有非零输入。
                "unit-due" to memoryState(
                    unitId = "unit-due",
                    stabilityDays = 2.0,
                    lastReviewedAt = NOW - 8L * DAY_MILLIS,
                    nextReviewAt = NOW - 5L * DAY_MILLIS,
                    lapseCount = 1,
                    lastLapseAt = NOW - 9L * DAY_MILLIS,
                ),
                // 连续两次遗忘 → leech（LEECH_RANK_FACTOR）与重复错题同时生效。
                "unit-leech" to memoryState(
                    unitId = "unit-leech",
                    stabilityDays = 1.0,
                    lastReviewedAt = NOW - 50L * DAY_MILLIS,
                    nextReviewAt = NOW - 45L * DAY_MILLIS,
                    lapseCount = 3,
                    lastLapseAt = NOW - 51L * DAY_MILLIS,
                ),
                "unit-exam-absent" to memoryState(
                    unitId = "unit-exam-absent",
                    stabilityDays = 4.0,
                    lastReviewedAt = NOW - 20L * DAY_MILLIS,
                ),
            ),
            knowledgeMasteryStates = mapOf("kc-a" to masteryA, "kc-b" to masteryB),
            checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, NOW),
            generatedAtEpochMillis = NOW,
        )
    }

    private fun masteryState(knowledgeNodeId: String, conservative: Double) = KnowledgeMasteryState(
        knowledgeNodeId = knowledgeNodeId,
        masteryScore = 0.4,
        conservativeMasteryScore = conservative,
        evidenceMass = 2.0,
        status = MasteryStatus.LEARNING,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        // KC 掉档压力的唯一入口：最近一条证据为负且保守分低于 0.6。
        lastEvidenceDirection = LearningEvidenceDirection.NEGATIVE.name,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 4,
    )

    private fun memoryState(
        unitId: String,
        stabilityDays: Double,
        lastReviewedAt: Long,
        nextReviewAt: Long = lastReviewedAt,
        lapseCount: Int = 0,
        lastLapseAt: Long? = null,
    ) = ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = stabilityDays,
        difficulty = 5.0,
        lastReviewedAtEpochMillis = lastReviewedAt,
        nextReviewAtEpochMillis = nextReviewAt,
        lapseCount = lapseCount,
        lastLapseAtEpochMillis = lastLapseAt,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 4,
        lastEvidenceDirection = LearningEvidenceDirection.NEGATIVE.name,
    )

    private companion object {
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS
        val NOW = 400 * DAY_MILLIS
    }
}
