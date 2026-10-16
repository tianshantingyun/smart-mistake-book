package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState

/**
 * 知识点复习队列的一个候选：一个知识点 + 其当前掌握态（供 [KnowledgeNodeScorer.scoreKnowledgeNode]
 * 打分）+ 该点复习的预估耗时（供时间预算约束）。
 *
 * [subjectId] 与 [materialGroupId] 是会话交错维度（spec §3.2 "多样性"）：同科目的点连续出现
 * 会让跨科交错失效；同讲解材料的点连续出题会得到雷同题目。[materialGroupId] 为 null 表示
 * 该点没有可归组的讲解材料（此时只按科目交错）。
 */
data class KnowledgeReviewCandidate(
    val knowledgeNodeId: String,
    val subjectId: String,
    val materialGroupId: String?,
    val state: KnowledgeMasteryState?,
    val estimatedDurationSeconds: Int,
    /**
     * 该知识点绑定题目的预测检索概率（取绑定题中已知 FSRS 记忆的最小 R），null = 无可用记忆。
     * 供 [KnowledgeNodeScorer.scoreKnowledgeNode] 以遗忘曲线而非掌握度 EMA 估计到期风险。
     */
    val recallRisk: Double? = null,
) {
    init {
        require(knowledgeNodeId.isNotBlank()) { "Knowledge review candidate id must not be blank" }
        require(subjectId.isNotBlank()) { "Knowledge review candidate subject must not be blank" }
        require(materialGroupId == null || materialGroupId.isNotBlank()) {
            "Knowledge review material group must not be blank when provided"
        }
        require(estimatedDurationSeconds > 0) { "Knowledge review estimate must be positive" }
        require(recallRisk == null || recallRisk.isFinite() && recallRisk in 0.0..1.0) {
            "Knowledge review candidate recall risk must be between zero and one"
        }
    }
}

/**
 * One planned knowledge-review session for today (spec dual-review-entry §3.2):
 * the ordered queue of knowledge nodes to quiz the student on, derived from
 * today's mistake-review plan scope. Each entry carries only the node identity
 * plus the mastery facts the session needs to build a KNOWLEDGE_QUIZ request;
 * the teaching material itself is resolved at dispatch time via the canonical
 * [TutorTeachingReferenceRepository] so this plan stays a light, durable index.
 */
data class KnowledgeReviewSessionPlan(
    /** Ordered queue; empty exactly when today's scope has no risky node to review. */
    val queue: List<KnowledgeReviewQueueEntry> = emptyList(),
) {
    init {
        require(queue.map(KnowledgeReviewQueueEntry::knowledgeNodeId).distinct().size == queue.size) {
            "Knowledge review plan must not repeat a knowledge node"
        }
    }

    val isEmpty: Boolean
        get() = queue.isEmpty()
}

/** One planned knowledge-review queue entry (spec dual-review-entry §3.2/§3.3). */
data class KnowledgeReviewQueueEntry(
    val knowledgeNodeId: String,
    /** Subject the node belongs to; resolves the teaching material for the quiz input. */
    val subject: String,
    /** Stable, learner-facing node name for the session header. */
    val displayName: String,
    val masteryScore: Double?,
    val lastEvidenceAtEpochMillis: Long?,
) {
    init {
        require(knowledgeNodeId.isNotBlank()) { "Knowledge review entry id must not be blank" }
        require(subject.isNotBlank()) { "Knowledge review entry subject must not be blank" }
        require(displayName.isNotBlank()) { "Knowledge review entry name must not be blank" }
        require(masteryScore == null || masteryScore.isFinite() && masteryScore in 0.0..1.0) {
            "Knowledge review entry mastery must be between zero and one"
        }
        require(lastEvidenceAtEpochMillis == null || lastEvidenceAtEpochMillis >= 0) {
            "Knowledge review entry evidence time must not be negative"
        }
    }
}

/**
 * 知识点 → 其绑定题目的预测检索概率（取该点所有已知 FSRS 记忆中的**最小** R）。
 *
 * 用途：知识点自身没有记忆痕迹，其"是否快要忘"只能由承载它的题目来回答。取最小值是保守
 * 选择——只要有一道绑定题濒临遗忘，该知识点就值得复习。没有已知记忆的点不出现在结果里，
 * 由 [KnowledgeNodeScorer.scoreKnowledgeNode] 回退到掌握度估计。
 *
 * 依据：Cepeda et al. 2006/2008（间隔的意义相对于目标保持间隔）与 FSRS R(t,S) 曲线本身；
 * 掌握度 EMA 在"新鲜"窗口内不随时间衰减，不适合作为到期风险的唯一输入。
 *
 * 消灭的失败：知识点到期风险此前用 `1 − masteryScore` 与"45 天悬崖"近似，排序既不随时间
 * 连续变化，也与绑定题的真实遗忘状态脱节。
 */
fun knowledgeRecallRiskByNode(
    boundPracticeUnitIdsByNode: Map<String, List<String>>,
    memoryStates: Map<String, ProblemMemoryState>,
    nowEpochMillis: Long,
    decay: Double,
): Map<String, Double> = boundPracticeUnitIdsByNode.mapNotNull { (nodeId, unitIds) ->
    unitIds.asSequence()
        .mapNotNull { unitId -> memoryStates[unitId] }
        .filter { it.stabilityDays > 0.0 && it.lastReviewedAtEpochMillis > 0 }
        .map { memory ->
            val elapsedDays = (nowEpochMillis - memory.lastReviewedAtEpochMillis)
                .coerceAtLeast(0).toDouble() / DAY_MILLIS
            FsrsScheduleMath.retention(
                elapsedDays = elapsedDays,
                stabilityDays = memory.stabilityDays,
                // W2-1/KF-01 + 批次 3（规划侧个性化）：decay 由调用方显式传入——
                // `StudyReviewPlannerService.planningDecay` 即投影同一份 `activeFsrsDecay`，
                // 风险排序与排程/投影同口径（此前的"默认 w20 边界"就此关闭）。
                decay = decay,
            )
        }
        .minOrNull()
        ?.let { nodeId to it }
}.toMap()

/** W0-4：日长单源在 `AlgorithmConstants.DAY_MILLIS`（本文件要 Double，故在此别名一次）。 */
private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()

/**
 * 生成今天知识点复习队列（spec dual-review-entry §3.2）：对候选知识点用与错题排程同构的
 * 打分（[KnowledgeNodeScorer.scoreKnowledgeNode]）打分，再按**同一套**机制取队——时间预算 +
 * 多样性（同讲解材料/同科目软降权）+ 难度档轮换（[KnowledgeNodeScorer.DIFFICULTY_CYCLE]
 * 平局时轮换）。已掌握且新鲜的知识点被 scoreKnowledgeNode 跳过（不进队列）。纯函数，不依赖 DB/UI。
 *
 * **两个范围档**（3E 全库薄弱点扫描，2026-10-16 裁定「第二档 = 每日 10 个知识点」）：
 * - [candidates] 第一档 = 今日错题复习队列的题绑定知识点（`extractReviewKnowledgeScope`）；
 * - [fullLibraryScanCandidates] 第二档 = 全库「有绑定题的活跃知识点 ∩ 材料可出题」**减去**今日队列
 *   后的候选（范围条件由 [fullLibraryScanCandidate] 落地，减法由调用方做）。
 *
 * 第二档**先**按分取前 [fullLibraryScanDailyCap] 个（F17 配额；平局按节点 id 定序，纯函数确定），
 * 再与第一档**并集**（同一节点两档都有时以第一档为准），最后是同一套时间预算（F8 预算）+ 多样性
 * 取队。因此最终队列里第二档的条目只少不多：名额封的是"进并集的增量"，不封今日队列那一档
 * （"全库扫描不受今日队列限制"，反过来扫描也不该把今日队列挤掉）。
 *
 * 与错题排程的一处刻意差异：多样性只做**软降权**、不做"同族已用即停"的硬约束。知识点常共享
 * 一份讲解材料，硬约束会在共用材料时直接截断队列（学生永远只复习到第一个点）。
 *
 * 消灭的失败：知识点复习缺少"今天复习哪些点、按什么顺序"的确定队列，导致 UI 无从排程，
 * 或连续出同一份材料/同一科目的题，跨科交错与题目多样性失效；以及范围锁死今日错题并集时，
 * 已经掌握不住的知识点只要今天错题没碰到就永远捞不回来（3E 的原始缺口）。
 */
fun selectKnowledgeReviewQueue(
    scorer: KnowledgeNodeScorer,
    candidates: List<KnowledgeReviewCandidate>,
    now: Long,
    timeBudgetSeconds: Int,
    /**
     * KF-16（裁决 28 读侧接线）：节点 → 其先修的记忆稳定度（取不到 = null，忽略；无条目 =
     * 无已知先修）。先修未恢复的节点按有效稳定度压制、不被跳过——延续"未知 ≠ 缺失"约定。
     */
    prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>> = emptyMap(),
    /** 3E 第二档候选（全库扫描）。默认空 = 单档行为逐位不变（既有调用方与用例不受影响）。 */
    fullLibraryScanCandidates: List<KnowledgeReviewCandidate> = emptyList(),
    /** 第二档每日名额上限（F17 配额；取值出处见 [FULL_LIBRARY_SCAN_DAILY_CAP]）。 */
    fullLibraryScanDailyCap: Int = FULL_LIBRARY_SCAN_DAILY_CAP,
): List<KnowledgeNodeScorer.ScoredKnowledgeNode> {
    require(timeBudgetSeconds >= 0) { "Knowledge review time budget must not be negative" }
    require(fullLibraryScanDailyCap >= 0) { "Full-library scan daily cap must not be negative" }
    val scannedPicks = fullLibraryScanCandidates
        .mapNotNull { candidate ->
            scoredCandidateOf(scorer, candidate, now, prerequisiteStabilityDaysByNode)
        }
        .sortedWith(
            compareByDescending<Pair<KnowledgeNodeScorer.ScoredKnowledgeNode, KnowledgeReviewCandidate>> {
                it.first.score
            }.thenBy { it.first.knowledgeNodeId },
        )
        .take(fullLibraryScanDailyCap)
        .map { (_, candidate) -> candidate }
    // 并集：同 id 以第一档（今日队列）那一条为准，且不出现重复节点
    // （KnowledgeReviewSessionPlan 拒绝重复 id）。
    val pool = (candidates + scannedPicks)
        .distinctBy(KnowledgeReviewCandidate::knowledgeNodeId)
        .mapNotNull { candidate ->
            scoredCandidateOf(scorer, candidate, now, prerequisiteStabilityDaysByNode)
        }
        .toMutableList()
    var remainingSeconds = timeBudgetSeconds
    val materialRepeats = mutableMapOf<String, Int>()
    val subjectRepeats = mutableMapOf<String, Int>()
    val selected = mutableListOf<KnowledgeNodeScorer.ScoredKnowledgeNode>()
    var preferredBandIndex = 0
    while (pool.isNotEmpty()) {
        val fitting = pool.filter { (_, candidate) ->
            candidate.estimatedDurationSeconds <= remainingSeconds
        }
        if (fitting.isEmpty()) break
        val desiredBand = KnowledgeNodeScorer.DIFFICULTY_CYCLE[
            preferredBandIndex % KnowledgeNodeScorer.DIFFICULTY_CYCLE.size,
        ]
        val chosen = fitting
            .sortedWith(
                compareByDescending<Pair<KnowledgeNodeScorer.ScoredKnowledgeNode, KnowledgeReviewCandidate>> {
                    it.first.score - diversityPenalty(it.second, materialRepeats, subjectRepeats)
                }
                    .thenBy { if (it.first.difficultyBand == desiredBand) 0 else 1 }
                    .thenBy { it.first.knowledgeNodeId },
            )
            .first()
        selected += chosen.first
        pool.removeAll { (scored, _) ->
            scored.knowledgeNodeId == chosen.first.knowledgeNodeId
        }
        remainingSeconds -= chosen.second.estimatedDurationSeconds
        chosen.second.materialGroupId?.let { materialRepeats.merge(it, 1, Int::plus) }
        subjectRepeats.merge(chosen.second.subjectId, 1, Int::plus)
        preferredBandIndex++
    }
    return selected
}

/** 单个候选的打分（两档共用同一出口，避免"两个档各写一遍打分参数"分叉）。 */
private fun scoredCandidateOf(
    scorer: KnowledgeNodeScorer,
    candidate: KnowledgeReviewCandidate,
    now: Long,
    prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>>,
): Pair<KnowledgeNodeScorer.ScoredKnowledgeNode, KnowledgeReviewCandidate>? =
    scorer.scoreKnowledgeNode(
        knowledgeNodeId = candidate.knowledgeNodeId,
        state = candidate.state,
        now = now,
        recallRisk = candidate.recallRisk,
        prerequisiteStabilityDays =
            prerequisiteStabilityDaysByNode[candidate.knowledgeNodeId].orEmpty(),
    )?.let { scored -> scored to candidate }

/** 同讲解材料/同科目重复出现的软降权；权重与错题排程的 family/source 降权同量级。 */
private fun diversityPenalty(
    candidate: KnowledgeReviewCandidate,
    materialRepeats: Map<String, Int>,
    subjectRepeats: Map<String, Int>,
): Double {
    val materialPenalty = (candidate.materialGroupId?.let { materialRepeats[it] } ?: 0) *
        MATERIAL_REPEAT_PENALTY
    val subjectPenalty = (subjectRepeats[candidate.subjectId] ?: 0) * SUBJECT_REPEAT_PENALTY
    return (materialPenalty + subjectPenalty).coerceAtMost(MAX_DIVERSITY_PENALTY)
}

/**
 * 同讲解材料/同科目重复出现的软降权；权重与错题排程的 family/source 降权同量级——
 * **单源**在 `AlgorithmConstants.ReviewScoring`（W0-4 审计 Q5：这三条此前是本文件写下的
 * 第三份同值字面量；值一个没改，只塌掉副本）。名字按本文件语义保留，映射按"同一降权量级"
 * 而非字面名称。
 */
private const val MATERIAL_REPEAT_PENALTY = AlgorithmConstants.ReviewScoring.FAMILY_PENALTY_WEIGHT
private const val SUBJECT_REPEAT_PENALTY = AlgorithmConstants.ReviewScoring.SOURCE_PENALTY_WEIGHT
private const val MAX_DIVERSITY_PENALTY = AlgorithmConstants.ReviewScoring.MAX_DIVERSITY_PENALTY
