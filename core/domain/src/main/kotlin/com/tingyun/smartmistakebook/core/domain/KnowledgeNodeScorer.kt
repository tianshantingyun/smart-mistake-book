package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ReviewDifficultyBand
import com.tingyun.smartmistakebook.core.model.ReviewReason

/**
 * 知识点打分的**共享打分核**（D-M M6，2026-10-02）。
 *
 * 退场背景：`ReviewPlanner`（V1）的 `scoreKnowledgeNode` 是知识点复习队列的**唯一**打分实现
 * （V2 没有该方法），而 V1 的错题 `plan()` 生产不可达（默认 V2 且无翻闸点）。M6 把知识点
 * 打分从 V1 文件里抽出来成为本类，队列改为消费它；V1 类与其计划版本串一并退场。
 *
 * 消灭的失败：知识点队列此前依赖一个**生产不可达**的排程器类里的方法——V1 一删，
 * 队列要么无处取分、要么被迫复制一份打分（两份同值表分叉的老病）。抽成独立打分核后，
 * 队列、V2 的风险分支与读侧跳过判据读的是同一组公式与同一个出口
 * （[ClearlyMasteredForSkipPolicy.effectiveStatus]）。
 *
 * 版本：本类不携带自己的版本串——知识点队列**不是**持久化投影/计划输出（会话内现排，
 * 不落库），因此抽取它不触发任何 bump（见 `docs/research/algorithm-version-ledger.md` §3.12）。
 */
class KnowledgeNodeScorer(
    private val forgettingCurve: ForgettingCurve = ForgettingCurve(),
) {

    /**
     * 单个知识点的复习打分（spec dual-review-entry §3.2）：知识点没有题目记忆，到期风险来自
     * "距上次证据多久"或承载题目的预测检索概率；掌握度风险与错题排程同源
     * （[masteryRiskFor]）。已掌握且新鲜、又没有提前/弱点理由的点返回 null（跳过）。
     */
    fun scoreKnowledgeNode(
        knowledgeNodeId: String,
        state: KnowledgeMasteryState?,
        now: Long,
        /**
         * Predicted recall probability of the items carrying this knowledge node
         * (min over the bound practice units whose FSRS memory is known), or null
         * when no carrying item has memory. When present it replaces the mastery
         * proxy as the due-risk input: the forgetting curve R(t,S) is the
         * principled time function (Cepeda et al. 2006/2008: interval meaning is
         * relative to the retention target), while an evidence EMA does not decay
         * with time inside the fresh window.
         */
        recallRisk: Double? = null,
        /**
         * KF-16（裁决 28 读侧接线）：该节点先修的记忆稳定度（未知 = null，忽略）。给定时
         * 跳过判据按**有效稳定度** = min(自身, 先修最小值) 计算——先修未恢复，后继不跳过。
         */
        prerequisiteStabilityDays: Collection<Double?> = emptyList(),
    ): ScoredKnowledgeNode? {
        require(recallRisk == null || recallRisk.isFinite() && recallRisk in 0.0..1.0) {
            "Knowledge-node recall risk must be between zero and one"
        }
        val reasons = linkedSetOf<ReviewReason>()
        val dueRisk = when {
            recallRisk != null -> {
                reasons += ReviewReason.DUE_RECALL_RISK
                if (
                    state?.lastEvidenceAtEpochMillis?.let {
                        now - it > ClearlyMasteredForSkipPolicy.MAX_EVIDENCE_AGE_MILLIS
                    } == true
                ) {
                    reasons += ReviewReason.STALE_KNOWLEDGE
                }
                1.0 - recallRisk
            }
            state == null -> {
                reasons += ReviewReason.NEWLY_ADDED
                0.2
            }
            else -> {
                val lastEvidenceAt = state.lastEvidenceAtEpochMillis
                if (lastEvidenceAt == null) {
                    reasons += ReviewReason.CALIBRATION_CHECK
                    1.0
                } else if (
                    now - lastEvidenceAt > ClearlyMasteredForSkipPolicy.MAX_EVIDENCE_AGE_MILLIS
                ) {
                    reasons += ReviewReason.DUE_RECALL_RISK
                    reasons += ReviewReason.STALE_KNOWLEDGE
                    // Stall risk grows with days since last evidence, capped at 1.
                    val daysSince = (now - lastEvidenceAt).coerceAtLeast(0).toDouble() / DAY_MILLIS
                    (daysSince / KNOWLEDGE_DUE_RAMP_DAYS).coerceAtMost(1.0)
                } else {
                    // Fresh evidence: due risk is the forget-curve retention loss.
                    reasons += ReviewReason.DUE_RECALL_RISK
                    1.0 - state.masteryScore
                }
            }
        }
        val masteryRisk = state
            ?.let {
                masteryRiskFor(
                    state = it,
                    now = now,
                    reasons = reasons,
                    prerequisiteStabilityDays = prerequisiteStabilityDays,
                )
            }
            ?: run {
                reasons += ReviewReason.MISSING_KNOWLEDGE_EVIDENCE
                reasons += ReviewReason.CALIBRATION_CHECK
                1.0
            }
        val weakness = maxOf(dueRisk, masteryRisk)
        if (weakness >= WEAKNESS_THRESHOLD) reasons += ReviewReason.WEAK_KNOWLEDGE

        // Skip a node that is **currently** clearly mastered (E 判据此刻成立，含 KF-16 压制)
        // and not otherwise early/weak. 裁决 28：此前是"存储态 MASTERED + 证据 ≤ 45 天"，两个
        // 判据不同源（双钟：chat 证据算新鲜、记忆卡已过期也照样跳过）；现在与展示面、风险
        // 分支共用 [ClearlyMasteredForSkipPolicy.effectiveStatus] 一个出口——已忘的点回到队列。
        val clearlyMastered = state != null && ClearlyMasteredForSkipPolicy.effectiveStatus(
            state = state,
            atEpochMillis = now,
            decay = forgettingCurve.decay,
            prerequisiteStabilityDays = prerequisiteStabilityDays,
        ) == MasteryStatus.MASTERED
        val hasEarlyReason = reasons.any { reason ->
            reason == ReviewReason.CONFLICTED_KNOWLEDGE ||
                reason == ReviewReason.STALE_KNOWLEDGE ||
                reason == ReviewReason.CALIBRATION_CHECK
        }
        if (clearlyMastered && !hasEarlyReason) return null
        if (reasons.isEmpty()) return null

        val score = (
            DUE_WEIGHT * dueRisk +
                WEAKNESS_WEIGHT * masteryRisk
            ).coerceAtLeast(0.0)
        return ScoredKnowledgeNode(
            knowledgeNodeId = knowledgeNodeId,
            score = score,
            reasons = reasons,
            difficultyBand = knowledgeDifficultyBand(masteryRisk),
        )
    }

    /**
     * Shared mastery-risk scoring for a single knowledge node (spec
     * dual-review-entry §3.2): CONFLICTED/STALE/UNKNOWN or unsupported evidence
     * is high risk; otherwise the weakness is the 7-day-half-life smoothed
     * mastery. Adds the matching reasons to [reasons]. The same rule feeds the
     * mistake planner's per-bound-node risk (V2 `scoreCandidate`).
     *
     * 裁决 28（读侧语义闭合，2026-10-01）：状态判断改由
     * [ClearlyMasteredForSkipPolicy.effectiveStatus] 现算——CONFLICTED/UNKNOWN/STALE 三个
     * 高风险分支与展示面、跳过策略读**同一个出口**；此前的 45 天窗（`lastEvidenceAt` 口径）
     * 并入该出口（锚点 = `lastAttemptAt ?: lastEvidenceAt`），chat 通道只改 comment 时钟
     * 就把知识点从复习压力里抹掉的"双钟"洞随之关闭。
     */
    private fun masteryRiskFor(
        state: KnowledgeMasteryState,
        now: Long,
        reasons: MutableSet<ReviewReason>,
        prerequisiteStabilityDays: Collection<Double?> = emptyList(),
    ): Double {
        val resolved = ClearlyMasteredForSkipPolicy.effectiveStatus(
            state = state,
            atEpochMillis = now,
            decay = forgettingCurve.decay,
            prerequisiteStabilityDays = prerequisiteStabilityDays,
        )
        if (resolved == MasteryStatus.CONFLICTED) {
            reasons += ReviewReason.CONFLICTED_KNOWLEDGE
            reasons += ReviewReason.CALIBRATION_CHECK
            return 1.0
        }
        if (resolved == MasteryStatus.UNKNOWN) {
            reasons += ReviewReason.CALIBRATION_CHECK
            return 1.0
        }
        if (resolved == MasteryStatus.STALE) {
            reasons += ReviewReason.STALE_KNOWLEDGE
            reasons += ReviewReason.CALIBRATION_CHECK
            return 1.0
        }
        val currentSupportedEvidenceMass = state.independentCorrectObservations
            .filter { it.calibrationSupportAt(now) == CalibrationSupport.SUPPORTED }
            .sumOf { it.evidenceWeight }
        if (currentSupportedEvidenceMass <= 0.0) {
            reasons += ReviewReason.CALIBRATION_CHECK
            return 1.0
        }
        // Spec 2.18: weakness input is the 7-day half-life smoothed
        // mastery, damping single-day swings.
        return 1.0 - MasterySmoothing.smoothedMasteryScore(state, now)
    }

    /**
     * 知识点复习的难度档（spec dual-review-entry §3.2 "难度循环"）：由掌握度风险推出——
     * 越薄弱/越无证据的点越难复习。与错题排程的难度档同构，供
     * [selectKnowledgeReviewQueue] 在分数平局时轮换难度档。
     */
    private fun knowledgeDifficultyBand(masteryRisk: Double): ReviewDifficultyBand = when {
        masteryRisk >= 1.0 -> ReviewDifficultyBand.HARD
        masteryRisk >= WEAKNESS_THRESHOLD -> ReviewDifficultyBand.MEDIUM
        else -> ReviewDifficultyBand.EASY
    }

    /** Result of [scoreKnowledgeNode]: a knowledge node + its composite score. */
    data class ScoredKnowledgeNode(
        val knowledgeNodeId: String,
        val score: Double,
        val reasons: Set<ReviewReason>,
        /** 掌握度风险推出的复习难度档（spec §3.2 难度循环），见 [knowledgeDifficultyBand]。 */
        val difficultyBand: ReviewDifficultyBand,
    )

    companion object {
        /** 会话内难度轮换顺序；知识点队列与错题排程共用（spec dual-review-entry §3.2）。 */
        internal val DIFFICULTY_CYCLE = listOf(
            ReviewDifficultyBand.MEDIUM,
            ReviewDifficultyBand.EASY,
            ReviewDifficultyBand.HARD,
        )
        // W0-4（审计 Q5）：权重表**单源**在 `AlgorithmConstants.ReviewScoring`。
        private const val WEAKNESS_THRESHOLD = AlgorithmConstants.ReviewScoring.WEAKNESS_THRESHOLD
        private const val DUE_WEIGHT = AlgorithmConstants.ReviewScoring.DUE_WEIGHT
        private const val WEAKNESS_WEIGHT = AlgorithmConstants.ReviewScoring.WEAKNESS_WEIGHT
        private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()

        /** Days of evidence age at which a knowledge node's due risk saturates. */
        private const val KNOWLEDGE_DUE_RAMP_DAYS = 30.0
    }
}
