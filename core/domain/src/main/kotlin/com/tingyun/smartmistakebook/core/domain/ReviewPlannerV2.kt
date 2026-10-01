package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.ReviewDifficultyBand
import com.tingyun.smartmistakebook.core.model.ReviewPlan
import com.tingyun.smartmistakebook.core.model.ReviewQueueItem
import com.tingyun.smartmistakebook.core.model.ReviewReason
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Review Planner V2 with enhanced scheduling algorithms:
 *
 * - Dynamic diversity penalties based on real-time queue composition
 * - Personal duration model based on historical response times
 * - Uncertainty as independent review value
 * - Knapsack/beam search/local swap optimization
 * - Fatigue and subject stacking constraints
 * - Hard sequencing constraints (audit §7.2): the same item family never
 *   appears in consecutive positions and the same subject never runs longer
 *   than [MAX_SAME_SUBJECT_RUN] consecutive positions; both are validated at
 *   sequence construction, so a violating candidate can never enter a plan
 * - Plan explanation for each selected item
 */
class ReviewPlannerV2(
    private val forgettingCurve: ForgettingCurve = ForgettingCurve(),
    private val durationModel: LogDurationModel = LogDurationModel(),
) {
    fun plan(request: ReviewPlanningRequest): ReviewPlan {
        require(request.learnerSnapshot.freshness == LearnerSnapshotFreshness.CURRENT) {
            "Review plans require a current learner snapshot"
        }
        require(request.learnerSnapshot.projectionStatus == ProjectionStatus.CURRENT) {
            "Review plans require a complete, conflict-free projection"
        }

        val now = request.planningAtEpochMillis
        // Apply the personalized duration model: each candidate's static
        // estimated duration is replaced by the learner-bucket prediction.
        val scored = request.candidates.mapNotNull { candidate ->
            scoreCandidate(candidate, request.learnerSnapshot, now, request.knowledgePrerequisites)
                ?.withModeledDuration(durationModel, request.learnerSnapshot.learnerId)
        }

        // Confusable partners (spec §6/C3): two candidates form a pair when
        // their KCs share a prerequisite and their mastery differs by less
        // than 0.2, so the interleaving pair can be scheduled together.
        val confusablePartners = computeConfusablePartners(scored, request)
        val scoredWithPairs = scored.map { scoredCandidate ->
            val partners = confusablePartners[scoredCandidate.candidate.practiceUnitId].orEmpty()
            if (partners.isEmpty()) {
                scoredCandidate
            } else {
                scoredCandidate.copy(reasons = scoredCandidate.reasons + ReviewReason.CONFUSABLE_PAIR)
            }
        }

        // S12/S13：备选池的池级事实（due 数量 / KC 频次）整个计划只算一次，
        // beam 与 localSwap 用的都是同一个池（scoredWithPairs 本身），
        // `antiOscillationPenalty` 的备选判定由此从 O(N) 每候选降到 O(|候选 KC|)。
        val alternativePool = AlternativePool.of(scoredWithPairs)

        // Phase 1: Initial selection with beam search
        val selected = beamSearchSelection(
            candidates = scoredWithPairs,
            timeBudgetSeconds = request.timeBudgetSeconds,
            confusablePartners = confusablePartners,
            alternativePool = alternativePool,
        )

        // Phase 2: Local swap optimization to improve diversity
        val optimized = localSwapOptimization(
            selected,
            scoredWithPairs,
            request.timeBudgetSeconds,
            confusablePartners,
            alternativePool,
        )
        check(satisfiesHardConstraints(optimized)) {
            "Review plan violated the hard sequencing constraints (audit §7.2)"
        }

        val planFingerprint = canonicalPlanFingerprint(request, optimized)
        val queue = optimized.mapIndexed { index, scoredCandidate ->
            val candidate = scoredCandidate.candidate
            ReviewQueueItem(
                queueItemId = "queue-$planFingerprint-$index",
                practiceUnitId = candidate.practiceUnitId,
                knowledgeNodeIds = candidate.knowledgeNodeIds,
                itemFamilyId = candidate.itemFamilyId,
                sourceBundleId = candidate.sourceBundleId,
                reasons = scoredCandidate.reasons,
                priorityScore = scoredCandidate.score,
                difficultyBand = scoredCandidate.difficultyBand,
                estimatedDurationSeconds = candidate.estimatedDurationSeconds,
                scheduledOrder = index,
                dueAtEpochMillis = request.learnerSnapshot
                    .problemMemoryStates[candidate.practiceUnitId]
                    ?.nextReviewAtEpochMillis,
            )
        }

        return ReviewPlan(
            planId = "plan-$planFingerprint",
            planFingerprint = planFingerprint,
            learnerId = request.learnerSnapshot.learnerId,
            localDayEpochDay = request.localDayEpochDay,
            timeZoneId = request.timeZoneId,
            generatedAtEpochMillis = now,
            plannerVersion = VERSION,
            projectionCheckpoint = request.learnerSnapshot.checkpoint,
            timeBudgetSeconds = request.timeBudgetSeconds,
            queueItems = queue,
        )
    }

    /**
     * Beam search selection that considers multiple candidate sequences
     * to find better combinations than greedy selection.
     *
     * All candidates that fit in the remaining budget are scored with the
     * incremental utility (base score minus dynamic diversity penalties),
     * then the beam is pruned to the top [beamWidth] states.
     */
    private fun beamSearchSelection(
        candidates: List<ScoredCandidate>,
        timeBudgetSeconds: Int,
        confusablePartners: Map<String, Set<String>> = emptyMap(),
        beamWidth: Int = 3,
        alternativePool: AlternativePool = AlternativePool.of(candidates),
    ): List<ScoredCandidate> {
        if (candidates.isEmpty()) return emptyList()

        // For small candidate sets, use simple greedy
        if (candidates.size <= beamWidth) {
            return greedySelection(candidates, timeBudgetSeconds, confusablePartners)
        }

        // S12：步数按时间预算收敛。每步至多追加一题、每题至少 minDuration 秒，
        // 所以第 s 步（0 基）能推进仅当 (s+1)·minDuration ≤ budget；预算装不下
        // 第 s+1 个最小题时，所有状态的剩余预算都 < minDuration，`fitting` 必空、
        // 原实现的 `!advanced` 回合必然退出 —— 这个上界只裁剪那些**注定不推进**的
        // 回合，与不设界时的输出逐位一致。
        val minDuration = candidates.minOf { it.candidate.estimatedDurationSeconds }.coerceAtLeast(1)
        val maxSteps = minOf(MAX_BEAM_STEPS, timeBudgetSeconds / minDuration)

        // Initialize beam with empty selections
        val initial = BeamState(
            selected = emptyList(),
            usedFamilies = emptyMap(),
            usedSources = emptyMap(),
            remainingSeconds = timeBudgetSeconds,
            totalScore = 0.0,
        )
        var beam = listOf(initial)
        // Track the best state seen so far. Beam pruning and the hard
        // sequencing constraints can drop every expandable state in a step;
        // the search must still return the best partial plan instead of
        // collapsing to an empty queue.
        var best = initial

        for (step in 0 until maxSteps) {
            val survivors = BeamSurvivors(beamWidth)
            var advanced = false

            for (state in beam) {
                // S12：已选 id 集合**每个状态每步只建一次**。原来的 getter 在
                // `fitting` 过滤器里对每个候选都重建一遍 `selected.map{}.toSet()`
                // （O(K·N) 每步）；状态每步只有 ≤beamWidth 个，逐状态建一次即 O(K)。
                val usedPracticeUnits = state.selected.mapTo(HashSet()) { it.candidate.practiceUnitId }
                val context = SelectionContext(state.selected)

                // Score EVERY fitting candidate with incremental utility
                // (do not pre-prune by static score alone, or valuable
                // diverse candidates would never enter the beam). Candidates
                // that violate the hard sequencing constraints are rejected
                // here, at sequence construction, so they can never enter a
                // plan.
                val fitting = candidates.filter {
                    it.candidate.estimatedDurationSeconds <= state.remainingSeconds &&
                        it.candidate.practiceUnitId !in usedPracticeUnits &&
                        canAppend(state.selected, it)
                }

                if (fitting.isEmpty()) {
                    // 该状态无可追加候选：原样携带到下一轮（可能成为 best）。
                    survivors.offerState(state)
                    continue
                }

                for (candidate in fitting) {
                    val familyPenalty = computeDynamicFamilyPenalty(candidate, state.usedFamilies)
                    val sourcePenalty = computeDynamicSourcePenalty(candidate, state.usedSources)
                    val adjustedScore = candidate.score - familyPenalty - sourcePenalty +
                        confusableBonus(candidate, state.selected, confusablePartners) -
                        antiOscillationPenalty(candidate, context, alternativePool)
                    if (adjustedScore <= 0) continue

                    advanced = true
                    // S12：子状态**延迟物化** —— 原实现对每个 fitting 候选都立刻建
                    // BeamState（含 selected 列表与两张频次 map 的整份拷贝），而 beam
                    // 每步只留 beamWidth 个：5000 个子状态里 4997 个是白建的。
                    // 这里只记 (总分, 父状态, 候选)，修剪后再物化 top-beamWidth 个。
                    survivors.offer(
                        candidate = candidate.copy(score = adjustedScore),
                        parent = state,
                        totalScore = state.totalScore + adjustedScore,
                    )
                }
            }

            // Keep top beamWidth states by total score（与原来
            // `sortedByDescending(totalScore).take(beamWidth)` 同一条稳定规则）。
            beam = survivors.materialize()
            if (beam.isEmpty()) break
            val stepBest = beam.first()
            if (stepBest.totalScore > best.totalScore) best = stepBest

            // Stop only when no beam state could add another candidate. The
            // previous `beam.size == 1` shortcut abandoned the search while a
            // single surviving state could still fill the remaining budget.
            if (!advanced) break
        }

        return best.selected
    }

    /**
     * Simple greedy selection as fallback.
     */
    private fun greedySelection(
        candidates: List<ScoredCandidate>,
        timeBudgetSeconds: Int,
        confusablePartners: Map<String, Set<String>> = emptyMap(),
    ): List<ScoredCandidate> {
        val remaining = candidates.toMutableList()
        val selected = mutableListOf<ScoredCandidate>()
        var usedFamilies = emptyMap<String, Int>()
        var usedSources = emptyMap<String, Int>()
        var remainingSeconds = timeBudgetSeconds

        while (remaining.isNotEmpty() && remainingSeconds > 0) {
            val fitting = remaining.filter {
                it.candidate.estimatedDurationSeconds <= remainingSeconds &&
                    canAppend(selected, it)
            }
            if (fitting.isEmpty()) break

            // 备选池 = 本轮的 fitting 集（与原实现传给 antiOscillationPenalty 的
            // 列表同一个）；n ≤ beamWidth 才有走到这里，池级预计算可忽略不计。
            val fittingPool = AlternativePool.of(fitting)
            val context = SelectionContext(selected)
            val adjustedCandidates = fitting.map { scored ->
                val familyPenalty = computeDynamicFamilyPenalty(scored, usedFamilies)
                val sourcePenalty = computeDynamicSourcePenalty(scored, usedSources)
                val adjustedScore = scored.score - familyPenalty - sourcePenalty +
                    confusableBonus(scored, selected, confusablePartners) -
                    antiOscillationPenalty(scored, context, fittingPool)
                scored.copy(score = adjustedScore.coerceAtLeast(0.0))
            }

            val chosen = adjustedCandidates.maxByOrNull(ScoredCandidate::score) ?: break
            if (chosen.score <= 0) break

            selected += chosen
            remaining.removeAll { it.candidate.practiceUnitId == chosen.candidate.practiceUnitId }
            remainingSeconds -= chosen.candidate.estimatedDurationSeconds
            usedFamilies = usedFamilies.increment(chosen.candidate.itemFamilyId)
            chosen.candidate.sourceBundleId?.let { usedSources = usedSources.increment(it) }
        }

        return selected
    }

    /**
     * Local swap optimization: try swapping items in the selected list
     * with unselected items to improve total utility (not just diversity).
     *
     * `unselected` is recomputed after every accepted swap so that already
     * selected practice units can never be offered again as swap targets.
     *
     * The swap criterion is *marginal*, mirroring the incremental criterion
     * the beam/greedy construction uses: an incoming item is evaluated
     * against the selection with the outgoing item removed, exactly as if it
     * were being appended in the outgoing item's place. Both candidates face
     * the same context, so the comparison is strict — a low-value item cannot
     * displace a high-value one merely because it adds a diversity dimension
     * (that was the old absolute total-utility bug: the diversity term was a
     * whole-selection constant, so swapping two mutually exclusive candidates
     * changed almost nothing and the higher *static* score always won).
     */
    private fun localSwapOptimization(
        selected: List<ScoredCandidate>,
        allCandidates: List<ScoredCandidate>,
        timeBudgetSeconds: Int,
        confusablePartners: Map<String, Set<String>> = emptyMap(),
        alternativePool: AlternativePool = AlternativePool.of(allCandidates),
    ): List<ScoredCandidate> {
        if (selected.size < 2) return selected

        var currentSelection = selected.toMutableList()

        // S13：增量维护"选中集"的派生量。原实现每个换位位置重建一次
        // `unselected`（O(N) 过滤）与整份 `sumOf`（每个候选对 O(K)），这里是
        // 一次 O(K) 初始化 + 每次接受换位 O(1) 更新：
        // - usedPracticeUnits：已选 id 集合（遍历 allCandidates 时原地跳过已选）；
        // - totalSeconds：当前选中总时长（newTime 由它 O(1) 算出，与原来
        //   `sumOf { duration } - timeDelta + incoming.duration` 的整数结果完全相同）。
        val usedPracticeUnits = selected.mapTo(HashSet()) { it.candidate.practiceUnitId }
        var totalSeconds = selected.sumOf { it.candidate.estimatedDurationSeconds }

        // Try swapping each selected item with each unselected item
        for (i in currentSelection.indices) {
            val currentItem = currentSelection[i]
            val timeDelta = currentItem.candidate.estimatedDurationSeconds
            // The context every candidate is scored against: the selection
            // minus the position under swap. Both the outgoing and the
            // incoming item face the same context, which makes their marginal
            // scores directly comparable.
            val rest = currentSelection.filterIndexed { index, _ -> index != i }
            val restContext = SelectionContext(rest)
            val restUsedFamilies = rest.groupingBy { it.candidate.itemFamilyId }.eachCount()
            val restUsedSources = rest.mapNotNull { it.candidate.sourceBundleId }
                .groupingBy { it }.eachCount()
            for (unselectedItem in allCandidates) {
                if (unselectedItem.candidate.practiceUnitId in usedPracticeUnits) continue
                val newTime = totalSeconds - timeDelta +
                    unselectedItem.candidate.estimatedDurationSeconds
                if (newTime > timeBudgetSeconds) continue

                val newSelection = currentSelection.toMutableList()
                newSelection[i] = unselectedItem
                // A swap must never break the hard sequencing constraints.
                if (!satisfiesHardConstraints(newSelection)) continue

                // Marginal comparison against the SAME context for both items:
                // the outgoing item is re-scored as if it were being appended
                // to `rest`, and the incoming item likewise. Only a net gain
                // (score gain minus any context penalty the incoming item
                // introduces) is accepted.
                val outgoingValue = marginalValue(
                    candidate = currentItem,
                    restContext = restContext,
                    restUsedFamilies = restUsedFamilies,
                    restUsedSources = restUsedSources,
                    alternativePool = alternativePool,
                    confusablePartners = confusablePartners,
                )
                val incomingValue = marginalValue(
                    candidate = unselectedItem,
                    restContext = restContext,
                    restUsedFamilies = restUsedFamilies,
                    restUsedSources = restUsedSources,
                    alternativePool = alternativePool,
                    confusablePartners = confusablePartners,
                )

                if (incomingValue > outgoingValue) {
                    currentSelection = newSelection
                    // 换位生效：增量更新已选集合与总时长（其余派生量按位置现算）。
                    usedPracticeUnits.remove(currentItem.candidate.practiceUnitId)
                    usedPracticeUnits.add(unselectedItem.candidate.practiceUnitId)
                    totalSeconds += unselectedItem.candidate.estimatedDurationSeconds - timeDelta
                    break
                }
            }
        }

        return currentSelection
    }

    /**
     * Marginal value of appending [candidate] to a fixed [context] (the
     * selection without the position under swap): static score minus the
     * dynamic context penalties the item would introduce, plus the confusable
     * partner bonus it would earn. This is the same incremental criterion the
     * greedy/beam construction applies, evaluated over the *remaining* items
     * of the selection instead of the already-selected prefix.
     */
    private fun marginalValue(
        candidate: ScoredCandidate,
        restContext: SelectionContext,
        restUsedFamilies: Map<String, Int>,
        restUsedSources: Map<String, Int>,
        alternativePool: AlternativePool,
        confusablePartners: Map<String, Set<String>>,
    ): Double {
        // 与 greedy/beam 完全同构的边际判据：复用同一组动态罚 helper，
        // 上下文换成"rest"（集合去掉换出位）的频次 map。复用保证两边
        // 的 family/source 罚带同一 coerceAtMost 上限，不会一处封顶一处不封。
        val familyPenalty = computeDynamicFamilyPenalty(candidate, restUsedFamilies)
        val sourcePenalty = computeDynamicSourcePenalty(candidate, restUsedSources)
        val confusable = confusableBonus(candidate, restContext.items, confusablePartners)
        val antiOscillation = antiOscillationPenalty(candidate, restContext, alternativePool)
        return candidate.score - familyPenalty - sourcePenalty + confusable - antiOscillation
    }

    /**
     * Hard sequencing constraints (audit §7.2), enforced where the sequence
     * is constructed so a violating candidate can never be selected:
     *
     * - the same item family must never occupy two consecutive positions;
     * - the same subject must never run longer than [MAX_SAME_SUBJECT_RUN]
     *   consecutive positions (candidates without a subject are exempt).
     *
     * Returns true when [candidate] may be appended to [selected].
     */
    private fun canAppend(selected: List<ScoredCandidate>, candidate: ScoredCandidate): Boolean {
        val last = selected.lastOrNull() ?: return true
        if (candidate.candidate.itemFamilyId == last.candidate.itemFamilyId) return false
        val subjectId = candidate.candidate.subjectId ?: return true
        if (subjectId != last.candidate.subjectId) return true
        val trailingRun = selected.asReversed()
            .takeWhile { it.candidate.subjectId == subjectId }
            .count()
        return trailingRun < MAX_SAME_SUBJECT_RUN
    }

    /** True when the whole [selection] satisfies the hard sequencing constraints. */
    private fun satisfiesHardConstraints(selection: List<ScoredCandidate>): Boolean {
        for (index in selection.indices) {
            if (!canAppend(selection.subList(0, index), selection[index])) return false
        }
        return true
    }

    /**
     * Compute dynamic family penalty based on current queue composition.
     * Penalty grows with each additional item from the same family.
     */
    private fun computeDynamicFamilyPenalty(
        candidate: ScoredCandidate,
        usedFamilies: Map<String, Int>,
    ): Double {
        val familyCount = usedFamilies[candidate.candidate.itemFamilyId] ?: 0
        return (familyCount * FAMILY_PENALTY_WEIGHT)
            .coerceAtMost(MAX_DIVERSITY_PENALTY)
    }

    /**
     * Compute dynamic source penalty based on current queue composition.
     */
    private fun computeDynamicSourcePenalty(
        candidate: ScoredCandidate,
        usedSources: Map<String, Int>,
    ): Double {
        val sourceCount = candidate.candidate.sourceBundleId
            ?.let { usedSources[it] }
            ?: 0
        return (sourceCount * SOURCE_PENALTY_WEIGHT)
            .coerceAtMost(MAX_DIVERSITY_PENALTY)
    }

    private fun scoreCandidate(
        candidate: ReviewCandidate,
        snapshot: LearnerSnapshot,
        now: Long,
        knowledgePrerequisites: Map<String, Set<String>> = emptyMap(),
    ): ScoredCandidate? {
        // Spec 2.16: leeched cards are paused from regular scheduling until a
        // cross-day success clears the Again streak. A hard exclusion makes that
        // recovery unreachable — the streak only clears by reviewing the card
        // again — so the pause is a heavy ranking penalty instead: the card
        // ranks far below every healthy candidate and only surfaces when the
        // pool is otherwise thin. Its difficulty stays frozen (LearningProjector).
        val leechRankFactor = if (candidate.leech) LEECH_RANK_FACTOR else 1.0
        val memory = snapshot.problemMemoryStates[candidate.practiceUnitId]
        val masteryStates = candidate.knowledgeNodeIds.mapNotNull(snapshot.knowledgeMasteryStates::get)
        val missingKnowledgeCount = candidate.knowledgeNodeIds.size - masteryStates.size
        val reasons = linkedSetOf<ReviewReason>()

        // Spec 2.9 prerequisite gate —— KF-08（2026-10-01，fix-plan）：前置未就绪 ⇒ **硬过滤**。
        // 此前是 `PREREQ_GAP_WEIGHT × gap` 的减项（降权但不排除），被降权的题仍会进入会话：
        // 学生还没掌握前置，反复做后继只是无效重复。先修恢复（gap 回 0）后该候选自动回到池里；
        // 没有前置图的调用 gap 恒 0，不产生任何过滤（"不知道前置" ≠ "缺前置"）。
        val prereqGap = prerequisiteGap(
            candidate = candidate,
            snapshot = snapshot,
            knowledgePrerequisites = knowledgePrerequisites,
        )
        if (prereqGap > 0.0) return null

        // Spec 2.10 graduation: a graduated card reaching its (long) due date
        // enters the queue as maintenance, explained as such.
        if (
            memory != null &&
            memory.isGraduationEligible &&
            memory.nextReviewAtEpochMillis <= now
        ) {
            reasons += ReviewReason.GRADUATED_MAINTENANCE
        }

        // Due risk
        val dueRisk = when {
            memory == null -> {
                reasons += ReviewReason.NEWLY_ADDED
                0.2
            }
            memory.nextReviewAtEpochMillis <= now -> {
                reasons += ReviewReason.DUE_RECALL_RISK
                val estimate = forgettingCurve.estimateAt(memory, now)
                if (estimate.clockAnomaly == ClockAnomaly.TIME_ROLLBACK) {
                    reasons += ReviewReason.CLOCK_ANOMALY
                }
                val retentionRisk = if (estimate.clockAnomaly == ClockAnomaly.TIME_ROLLBACK) {
                    1.0
                } else {
                    1.0 - estimate.probability
                }
                val overdueDays = (now - memory.nextReviewAtEpochMillis)
                    .coerceAtLeast(0)
                    .toDouble() / DAY_MILLIS
                retentionRisk + (overdueDays / 30.0).coerceAtMost(1.0)
            }
            else -> {
                val estimate = forgettingCurve.estimateAt(memory, now)
                if (estimate.clockAnomaly == ClockAnomaly.TIME_ROLLBACK) {
                    reasons += ReviewReason.CLOCK_ANOMALY
                    1.0
                } else {
                    1.0 - estimate.probability
                }
            }
        }

        // Missing knowledge
        if (missingKnowledgeCount > 0 || candidate.knowledgeNodeIds.isEmpty()) {
            reasons += ReviewReason.MISSING_KNOWLEDGE_EVIDENCE
            reasons += ReviewReason.CALIBRATION_CHECK
        }

        // Mastery risk —— 裁决 28（读侧语义闭合）：状态判断由
        // `ClearlyMasteredForSkipPolicy.effectiveStatus` 现算，CONFLICTED/UNKNOWN/STALE 三个
        // 高风险分支与展示面、V1 跳过判据读同一个出口；此前的 45 天窗（lastEvidenceAt 口径）
        // 并入该出口，chat "评论钟"把知识点从复习压力里抹掉的洞随之关闭。
        // KF-16 压制：先修未恢复的节点按有效稳定度判"已掌握"（此处压制意味着不再走低风险路径，
        // 而不是让它变成"未知"）。
        // S15：observations 单遍扫描 —— 校准支持判定与平滑分共用同一个
        // `MasterySmoothing.evaluate` 结果（原来 `.none { supported }`、风险分支的平滑分、
        // 最弱掌握度的再一遍平滑分共三遍）；最弱掌握度也在同一个状态循环里随取随比。
        val masteryRisks = ArrayList<Double>(masteryStates.size)
        var weakestSmoothedMastery: Double? = null
        for (state in masteryStates) {
            val evaluation = MasterySmoothing.evaluate(state, now)
            if (weakestSmoothedMastery == null || evaluation.score < weakestSmoothedMastery) {
                weakestSmoothedMastery = evaluation.score
            }
            val prerequisiteStabilities = knowledgePrerequisites[state.knowledgeNodeId]
                .orEmpty()
                .map { prerequisiteId ->
                    snapshot.knowledgeMasteryStates[prerequisiteId]?.memoryStabilityDays
                }
            val resolved = ClearlyMasteredForSkipPolicy.effectiveStatus(
                state = state,
                atEpochMillis = now,
                decay = forgettingCurve.decay,
                prerequisiteStabilityDays = prerequisiteStabilities,
            )
            masteryRisks += when {
                resolved == com.tingyun.smartmistakebook.core.model.MasteryStatus.CONFLICTED -> {
                    reasons += ReviewReason.CONFLICTED_KNOWLEDGE
                    reasons += ReviewReason.CALIBRATION_CHECK
                    1.0
                }
                resolved == com.tingyun.smartmistakebook.core.model.MasteryStatus.UNKNOWN -> {
                    reasons += ReviewReason.CALIBRATION_CHECK
                    1.0
                }
                resolved == com.tingyun.smartmistakebook.core.model.MasteryStatus.STALE -> {
                    reasons += ReviewReason.STALE_KNOWLEDGE
                    reasons += ReviewReason.CALIBRATION_CHECK
                    1.0
                }
                !evaluation.hasSupportedObservation -> {
                    reasons += ReviewReason.CALIBRATION_CHECK
                    1.0
                }
                // Spec 2.18: weakness input is the 7-day half-life smoothed
                // mastery, damping single-day swings.
                else -> 1.0 - evaluation.score
            }
        }

        val weakness = (masteryRisks + List(
            maxOf(missingKnowledgeCount, if (candidate.knowledgeNodeIds.isEmpty()) 1 else 0),
        ) { 1.0 }).maxOrNull() ?: 1.0
        if (weakness >= WEAKNESS_THRESHOLD) reasons += ReviewReason.WEAK_KNOWLEDGE

        // Lapse risk
        val lapseScore = memory?.let { memoryState ->
            memoryState.lastLapseAtEpochMillis
                ?.takeIf { now - it in 0..RECENT_LAPSE_WINDOW_MILLIS }
                ?.let { (memoryState.lapseCount / 3.0).coerceAtMost(1.0) }
        } ?: 0.0
        if (lapseScore > 0.0) reasons += ReviewReason.RECENT_LAPSE

        // Repeat mistake
        if (candidate.repeatMistakePriority > 0.0) reasons += ReviewReason.REPEATED_MISTAKE

        // Avoidance (spec 6 / D'Mello 2013): repeated switch-aways with poor
        // grades mark a card the learner finds aversive; nudge it toward
        // re-teaching instead of plain rescheduling.
        if (candidate.avoidance) reasons += ReviewReason.AVOIDANCE_SIGNAL

        // Spec 5 KC->question propagation: continuous pressure shared with V1.
        val kcDropPressure = kcMasteryDropPressure(masteryStates)
        if (kcDropPressure > 0.0) reasons += ReviewReason.KC_MASTERY_DROP

        // Exam priority
        if (candidate.examPriority > 0.0) reasons += ReviewReason.EXAM_PRIORITY

        // Waiting fairness
        val waitingScore = candidate.eligibleSinceEpochMillis
            ?.let { eligibleSince ->
                val waitingDays = (now - eligibleSince).coerceAtLeast(0).toDouble() / DAY_MILLIS
                ((waitingDays - WAITING_GRACE_DAYS) / WAITING_BONUS_RAMP_DAYS)
                    .coerceIn(0.0, 1.0)
            }
            ?: 0.0
        if (waitingScore > 0.0) reasons += ReviewReason.LONG_WAITING

        // Early review check. Spec 2.17 defines the exam queue as the cards with
        // R below the exam target, so an exam may only pull forward a card that
        // has actually decayed; the FSRS stability gain is e^{w10(1−R)}−1, which
        // is near zero at R≈1 (研究 2026-09-09 §5; Rohrer & Taylor 2005). The
        // other early reasons stay unconditional: CLOCK_ANOMALY/CALIBRATION_CHECK
        // are integrity checks, and REPEATED_MISTAKE / KC_MASTERY_DROP carry new
        // negative evidence about the card or its knowledge node that the card's
        // own retrievability cannot express (spec §5 KC→question propagation is
        // a user rule).
        val earlyReviewAllowed = reasons.any { reason ->
            reason == ReviewReason.CLOCK_ANOMALY ||
                reason == ReviewReason.CALIBRATION_CHECK ||
                reason == ReviewReason.REPEATED_MISTAKE ||
                reason == ReviewReason.KC_MASTERY_DROP
        } || (
            ReviewReason.EXAM_PRIORITY in reasons &&
                (memory?.let { memoryState ->
                    forgettingCurve.estimateAt(memoryState, now)
                        .takeIf { it.clockAnomaly != ClockAnomaly.TIME_ROLLBACK }
                        ?.probability
                } ?: 0.0) < EARLY_REVIEW_MAX_RETRIEVABILITY
            )
        if (
            memory != null &&
            memory.nextReviewAtEpochMillis > now &&
            !earlyReviewAllowed
        ) {
            return null
        }
        if (reasons.isEmpty()) return null

        // Compute final score
        val score = (
            DUE_WEIGHT * dueRisk +
                WEAKNESS_WEIGHT * weakness +
                LAPSE_WEIGHT * lapseScore +
                REPEAT_MISTAKE_WEIGHT * candidate.repeatMistakePriority +
                AVOIDANCE_WEIGHT * (if (candidate.avoidance) 1.0 else 0.0) +
                KC_DROP_WEIGHT * kcDropPressure +
                EXAM_WEIGHT * candidate.examPriority +
                WAITING_WEIGHT * waitingScore
            ).coerceAtLeast(0.0) * leechRankFactor

        return ScoredCandidate(
            candidate = candidate,
            score = score,
            reasons = reasons,
            difficultyBand = difficultyBand(candidate.difficulty),
            weakestKnowledgeMastery = weakestSmoothedMastery,
        )
    }

    /**
     * Weakest-prerequisite gap (spec 2.9). The rule itself lives in
     * [KnowledgeReadiness] because the session surface needs the same answer to
     * decide which remediation to offer; only the numeric projection is used here.
     */
    private fun prerequisiteGap(
        candidate: ReviewCandidate,
        snapshot: LearnerSnapshot,
        knowledgePrerequisites: Map<String, Set<String>>,
    ): Double = KnowledgeReadiness.gapOf(
        knowledgeNodeIds = candidate.knowledgeNodeIds,
        prerequisitesByNode = knowledgePrerequisites,
        masteryScoreOf = { knowledgeNodeId ->
            snapshot.knowledgeMasteryStates[knowledgeNodeId]?.conservativeMasteryScore
        },
    )

    /**
     * Confusable partner map: shared prerequisite and mastery gap below 0.2.
     *
     * S14：从 O(N²) 全成对改成"共享先修倒排配只"。原实现内层的判定是
     * `rightKcs.any { it in leftPrereqs } || leftKcs.any { l -> prereqs(l) ∩ rightKcs ≠ ∅ }`
     * —— 两个子句**恒等**（都在问"左题某个 KC 的先修 r 同时是右题的 KC"），
     * 第二个子句是重复推导；这里收成一条判定并倒排：建 `KC r → KC 含 r 的题`
     * 索引，对每个左题的每个 (KC l, 先修 r) 组合直接枚举"含 r 的右题"，
     * 只有真正可能成对的 (左, 右) 才进入掌握差判定。
     * 配对判定本身（含左/右方向与 i<j 的遍历口径）逐字保持，输出集合不变。
     */
    private fun computeConfusablePartners(
        scored: List<ScoredCandidate>,
        request: ReviewPlanningRequest,
    ): Map<String, Set<String>> {
        if (scored.size < 2) return emptyMap()
        val snapshot = request.learnerSnapshot
        val masteryByIndex = scored.map { scoredCandidate ->
            scoredCandidate.candidate.knowledgeNodeIds
                .mapNotNull { snapshot.knowledgeMasteryStates[it]?.conservativeMasteryScore }
                .minOrNull()
        }
        // KC r → 所有 KC 集合含 r 的题下标（即：这些题可做"右题"）。
        val candidateIndicesByKc = HashMap<String, MutableList<Int>>()
        scored.forEachIndexed { index, scoredCandidate ->
            for (knowledgeNodeId in scoredCandidate.candidate.knowledgeNodeIds) {
                candidateIndicesByKc.getOrPut(knowledgeNodeId, ::mutableListOf).add(index)
            }
        }
        val partners = mutableMapOf<String, MutableSet<String>>()
        scored.forEachIndexed { leftIndex, left ->
            val leftKcs = left.candidate.knowledgeNodeIds
            if (leftKcs.isEmpty()) return@forEachIndexed
            val leftMastery = masteryByIndex[leftIndex]
            for (knowledgeNodeId in leftKcs) {
                for (prerequisite in request.knowledgePrerequisites[knowledgeNodeId].orEmpty()) {
                    // rightIndex > leftIndex：与原 `scored.drop(index + 1)` 同一遍历口径。
                    for (rightIndex in candidateIndicesByKc[prerequisite].orEmpty()) {
                        if (rightIndex <= leftIndex) continue
                        val right = scored[rightIndex]
                        if (left.candidate.itemFamilyId == right.candidate.itemFamilyId) continue
                        val rightMastery = masteryByIndex[rightIndex]
                        if (
                            leftMastery != null && rightMastery != null &&
                            kotlin.math.abs(leftMastery - rightMastery) < CONFUSABLE_MASTERY_GAP
                        ) {
                            partners.getOrPut(left.candidate.practiceUnitId) { mutableSetOf() }
                                .add(right.candidate.practiceUnitId)
                            partners.getOrPut(right.candidate.practiceUnitId) { mutableSetOf() }
                                .add(left.candidate.practiceUnitId)
                        }
                    }
                }
            }
        }
        return partners
    }

    private fun confusableBonus(
        candidate: ScoredCandidate,
        selected: List<ScoredCandidate>,
        confusablePartners: Map<String, Set<String>>,
    ): Double {
        val partners = confusablePartners[candidate.candidate.practiceUnitId] ?: return 0.0
        val pairedSelected = selected.any { it.candidate.practiceUnitId in partners }
        return if (pairedSelected) CONFUSABLE_BONUS else 0.0
    }

    /**
     * Anti-oscillation damping (spec 2.18): at most two questions touching
     * the same KC per session, and the weakest-only reason may not exceed a
     * quarter of the session without due-risk support.
     *
     * S12/S13：备选池判定不再逐候选扫全池 —— `availableAlternatives` 的两条
     * `any{}` 由 [AlternativePool] 的池级事实给出精确答案（池只建一次）；
     * "弱点项在已选里占多少"由 [SelectionContext] 预计算，不再逐候选重扫 selected。
     */
    private fun antiOscillationPenalty(
        candidate: ScoredCandidate,
        context: SelectionContext,
        alternativePool: AlternativePool,
    ): Double {
        val candidateKcs = candidate.candidate.knowledgeNodeIds
        if (candidateKcs.isEmpty()) return 0.0
        val selected = context.items
        val sameKcCount = selected.count { selectedCandidate ->
            selectedCandidate.candidate.knowledgeNodeIds.any(candidateKcs::contains)
        }
        // The quota only steers the session when the backlog actually offers
        // candidates that touch other KCs; a single-KC backlog must still
        // fill the session.
        val diverseAlternatives = alternativePool.hasDisjointAlternative(candidate)
        // Blocked-first phase (研究 2026-09-09 §2): a KC the student has not
        // learned yet benefits from 2-3 consecutive items before interleaving
        // helps (low prior knowledge is an "undesirable difficulty" moderator),
        // so the quota only binds once the KC is at least ready-to-learn.
        val weakestMastery = candidate.weakestKnowledgeMastery
        val blockedFirst = weakestMastery != null &&
            weakestMastery < KnowledgeReadiness.READY_THRESHOLD
        if (
            sameKcCount >= MAX_PER_KNOWLEDGE_NODE_PER_SESSION &&
            diverseAlternatives &&
            !blockedFirst
        ) {
            return SAME_KC_EXHAUSTION_PENALTY
        }
        val candidateIsWeaknessOnly = ReviewReason.WEAK_KNOWLEDGE in candidate.reasons &&
            ReviewReason.DUE_RECALL_RISK !in candidate.reasons
        val dueAlternatives = alternativePool.hasDueAlternative(candidate)
        if (candidateIsWeaknessOnly && selected.isNotEmpty() && dueAlternatives) {
            val share = context.weaknessOnlyCount.toDouble() / (selected.size + 1)
            if (share > MAX_WEAKNESS_ONLY_SHARE) return WEAKNESS_SHARE_PENALTY
        }
        return 0.0
    }

    private fun difficultyBand(difficulty: Double): ReviewDifficultyBand = when {
        difficulty < EASY_DIFFICULTY_CEILING -> ReviewDifficultyBand.EASY
        difficulty < MEDIUM_DIFFICULTY_CEILING -> ReviewDifficultyBand.MEDIUM
        else -> ReviewDifficultyBand.HARD
    }

    private fun canonicalPlanFingerprint(
        request: ReviewPlanningRequest,
        selected: List<ScoredCandidate>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        fun field(name: String, value: Any?) {
            append(name)
            append(value?.toString() ?: "<null>")
        }
        field("schemaVersion", PLAN_FINGERPRINT_SCHEMA_VERSION)
        field("learnerId", request.learnerSnapshot.learnerId)
        field("localDayEpochDay", request.localDayEpochDay)
        field("timeZoneId", request.timeZoneId)
        field("timeBudgetSeconds", request.timeBudgetSeconds)
        field("planningAtEpochMillis", request.planningAtEpochMillis)
        field("plannerVersion", VERSION)
        field("projectorVersion", request.learnerSnapshot.checkpoint.projectorVersion)
        field("checkpointLastSequence", request.learnerSnapshot.checkpoint.lastSequence)
        field("checkpointProjectedAt", request.learnerSnapshot.checkpoint.projectedAtEpochMillis)
        field("correctionWatermark", request.learnerSnapshot.correctionWatermarkEpochMillis)
        field("queueSize", selected.size)
        selected.forEachIndexed { index, scored ->
            val candidate = scored.candidate
            field("queue[$index].practiceUnitId", candidate.practiceUnitId)
            field("queue[$index].knowledgeNodeCount", candidate.knowledgeNodeIds.size)
            candidate.knowledgeNodeIds.sorted().forEachIndexed { knowledgeIndex, knowledgeNodeId ->
                field("queue[$index].knowledgeNode[$knowledgeIndex]", knowledgeNodeId)
            }
            field("queue[$index].itemFamilyId", candidate.itemFamilyId)
            field("queue[$index].sourceBundleId", candidate.sourceBundleId)
            field("queue[$index].subjectId", candidate.subjectId)
            field("queue[$index].itemType", candidate.itemType)
            field("queue[$index].candidateDifficulty", java.lang.Double.toHexString(candidate.difficulty))
            field("queue[$index].candidateExamPriority", java.lang.Double.toHexString(candidate.examPriority))
            field(
                "queue[$index].candidateRepeatMistakePriority",
                java.lang.Double.toHexString(candidate.repeatMistakePriority),
            )
            field("queue[$index].eligibleSinceEpochMillis", candidate.eligibleSinceEpochMillis)
            field("queue[$index].reasonCount", scored.reasons.size)
            scored.reasons.map(ReviewReason::name).sorted().forEachIndexed { reasonIndex, reason ->
                field("queue[$index].reason[$reasonIndex]", reason)
            }
            field("queue[$index].priorityScore", java.lang.Double.toHexString(scored.score))
            field("queue[$index].difficultyBand", scored.difficultyBand)
            field("queue[$index].estimatedDurationSeconds", candidate.estimatedDurationSeconds)
            field("queue[$index].scheduledOrder", index)
            field(
                "queue[$index].dueAtEpochMillis",
                request.learnerSnapshot.problemMemoryStates[candidate.practiceUnitId]?.nextReviewAtEpochMillis,
            )
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class ScoredCandidate(
        val candidate: ReviewCandidate,
        val score: Double,
        val reasons: Set<ReviewReason>,
        val difficultyBand: ReviewDifficultyBand,
        /**
         * Smoothed mastery of the candidate's weakest bound knowledge node, or
         * null when no bound node has a mastery state. The same-KC interleaving
         * quota is waived below [KnowledgeReadiness.READY_THRESHOLD] so a
         * not-yet-learned KC can be blocked first (研究 2026-09-09 §2).
         */
        val weakestKnowledgeMastery: Double? = null,
    ) {
        /**
         * Replaces the candidate's static duration estimate with the
         * learner-bucket personalized prediction from [durationModel].
         *
         * Uses the candidate's real subject/item-type dimensions instead of
         * impersonating a subject with a knowledge-node id, so the duration
         * model buckets on `student × subject × itemType × difficulty`
         * (audit §7.3) instead of degrading to fewer dimensions.
         */
        fun withModeledDuration(durationModel: LogDurationModel, learnerId: String): ScoredCandidate {
            val predicted = durationModel.expectedSeconds(
                learnerId = learnerId,
                subjectId = candidate.subjectId,
                itemType = candidate.itemType,
                difficulty = candidate.difficulty,
            )
            val modeled = if (predicted > 0) {
                candidate.copy(
                    estimatedDurationSeconds = predicted.toInt().coerceAtLeast(1),
                )
            } else {
                candidate
            }
            return copy(candidate = modeled)
        }
    }

    private data class BeamState(
        val selected: List<ScoredCandidate>,
        val usedFamilies: Map<String, Int>,
        val usedSources: Map<String, Int>,
        val remainingSeconds: Int,
        val totalScore: Double,
    )

    /**
     * 一个"已选序列"的判据上下文（S12/S13）：把**与候选无关**的派生量预先算好，
     * 不再对每个候选重扫一遍 `selected`。语义与逐候选现算完全相同（纯函数）。
     */
    private class SelectionContext(val items: List<ScoredCandidate>) {
        /** `WEAK_KNOWLEDGE in reasons && DUE_RECALL_RISK !in reasons` 的选中项数。 */
        val weaknessOnlyCount: Int = items.count { scoredCandidate ->
            ReviewReason.WEAK_KNOWLEDGE in scoredCandidate.reasons &&
                ReviewReason.DUE_RECALL_RISK !in scoredCandidate.reasons
        }
    }

    /**
     * 固定备选池的池级事实（S12/S13）。`antiOscillationPenalty` 的两条备选判定
     * 原来对每个候选各扫一遍全池（beam 步的 O(N²) 残余项），这里把池级统计
     * 一次算完，逐候选判定只做 O(|候选 KC|) 的**精确**运算：
     *
     * - due 备选：池里 due 项计数（排除候选自身）—— 精确；
     * - 相异备选：`∃ x≠c: KCs(x) ∩ KCs(c) = ∅`。用 KC 频次做两条可判定短路
     *   （某 KC 覆盖全池 ⇒ 无；频次和 < 池大小 ⇒ 有），无法判定时才退回精确扫描。
     *
     * 调用前提：被判定/被扫描的候选都取自本池（三处调用点都成立：beam 的全池、
     * greedy 的 fitting 集、localSwap 的 allCandidates）。
     */
    private class AlternativePool(
        private val members: List<ScoredCandidate>,
        private val dueCount: Int,
        private val kcFrequency: Map<String, Int>,
    ) {
        /** `availableAlternatives.any { DUE_RECALL_RISK in it.reasons && it.id != candidate.id }`。 */
        fun hasDueAlternative(candidate: ScoredCandidate): Boolean =
            dueCount > if (ReviewReason.DUE_RECALL_RISK in candidate.reasons) 1 else 0

        /** `availableAlternatives.any { it.id != candidate.id && it.KCs.none(candidateKcs::contains) }`。 */
        fun hasDisjointAlternative(candidate: ScoredCandidate): Boolean {
            val candidateKcs = candidate.candidate.knowledgeNodeIds
            if (candidateKcs.isEmpty()) {
                // 空 KC 与任何成员都不相交；只要池里还有别的成员即可。
                return members.size > 1
            }
            var frequencySum = 0
            var frequencyMax = 0
            for (kc in candidateKcs) {
                val frequency = kcFrequency[kc] ?: 0
                frequencySum += frequency
                if (frequency > frequencyMax) frequencyMax = frequency
            }
            // 某 KC 出现在全池每个成员里 ⇒ 每个成员都与候选相交 ⇒ 无相异备选。
            if (frequencyMax == members.size) return false
            // 频次和的并集上界都小于池大小 ⇒ 至少一个成员与候选的 KC 全不相交；
            // 且该成员必不是候选自身（候选 KC 非空 ⇒ 与自己相交）。
            if (frequencySum < members.size) return true
            return members.any { member ->
                member.candidate.practiceUnitId != candidate.candidate.practiceUnitId &&
                    member.candidate.knowledgeNodeIds.none(candidateKcs::contains)
            }
        }

        companion object {
            fun of(members: List<ScoredCandidate>): AlternativePool {
                val frequencies = HashMap<String, Int>()
                var due = 0
                for (member in members) {
                    for (kc in member.candidate.knowledgeNodeIds) {
                        frequencies[kc] = (frequencies[kc] ?: 0) + 1
                    }
                    if (ReviewReason.DUE_RECALL_RISK in member.reasons) due += 1
                }
                return AlternativePool(members, due, frequencies)
            }
        }
    }

    /**
     * beam 一步的"存活子状态"选择器（S12）：按 `totalScore` 降序、同分保持
     * 生成顺序（与原 `sortedByDescending(totalScore).take(beamWidth)` 的稳定
     * 排序同一规则），只对 top-[width] 物化 [BeamState]。
     */
    private class BeamSurvivors(private val width: Int) {
        private class Ref(
            val totalScore: Double,
            val parent: BeamState,
            /** null = 原状态自身被携带（该状态已无 fitting 候选）。 */
            val candidate: ScoredCandidate?,
        )

        private val refs = ArrayList<Ref>(width + 1)

        fun offerState(state: BeamState) {
            offer(Ref(state.totalScore, state, null))
        }

        fun offer(candidate: ScoredCandidate, parent: BeamState, totalScore: Double) {
            offer(Ref(totalScore, parent, candidate))
        }

        private fun offer(ref: Ref) {
            var index = 0
            while (index < refs.size && refs[index].totalScore >= ref.totalScore) index++
            refs.add(index, ref)
            if (refs.size > width) refs.removeAt(refs.size - 1)
        }

        fun materialize(): List<BeamState> = refs.map { ref ->
            val candidate = ref.candidate
            if (candidate == null) {
                ref.parent
            } else {
                BeamState(
                    selected = ref.parent.selected + candidate,
                    usedFamilies = ref.parent.usedFamilies.increment(candidate.candidate.itemFamilyId),
                    usedSources = candidate.candidate.sourceBundleId
                        ?.let { ref.parent.usedSources.increment(it) }
                        ?: ref.parent.usedSources,
                    remainingSeconds = ref.parent.remainingSeconds -
                        candidate.candidate.estimatedDurationSeconds,
                    totalScore = ref.totalScore,
                )
            }
        }
    }

    companion object {
        /**
         * v3 → v4（裁决 28 读侧语义闭合，2026-10-01）：掌握风险分支的状态判断改由
         * `ClearlyMasteredForSkipPolicy.effectiveStatus` 现算（45 天窗并入出口，KF-16 先修
         * 压制接线）——风险值与理由集变化 → 计划输出变化，旧计划重排
         * （指纹 canonical-v8 → v9 同步）。
         */
        const val VERSION = "review-planner-v4"
        // W0-4（审计 Q5）：与 V1 共用的权重值**单源**在 `AlgorithmConstants.ReviewScoring`。
        // 名字保留（调用点可读性），值不再在本文件写第二遍——此前两张表各写一份同值，
        // 改一处另一处照旧跑，正是"权重表已分叉"那条证据。仅 V2 使用的常数留在本处（无分叉面）。
        private const val EASY_DIFFICULTY_CEILING = AlgorithmConstants.ReviewScoring.EASY_DIFFICULTY_CEILING
        private const val MEDIUM_DIFFICULTY_CEILING = AlgorithmConstants.ReviewScoring.MEDIUM_DIFFICULTY_CEILING
        private const val WEAKNESS_THRESHOLD = AlgorithmConstants.ReviewScoring.WEAKNESS_THRESHOLD
        private const val CONFUSABLE_MASTERY_GAP = 0.2
        private const val CONFUSABLE_BONUS = 1.5
        private const val MAX_PER_KNOWLEDGE_NODE_PER_SESSION = 2
        private const val SAME_KC_EXHAUSTION_PENALTY = 10.0
        /**
         * Spec 2.16 leech pause: a leeched card ranks far below every healthy
         * candidate but stays schedulable, because a cross-day success is the
         * only path that clears the Again streak.
         */
        private const val LEECH_RANK_FACTOR = 0.15
        /**
         * An exam may pull a card forward only below this predicted recall
         * (spec 2.17 defines the exam queue as `R < r*_exam`; 研究 2026-09-09 §5:
         * the FSRS stability gain is e^{w10(1−R)}−1).
         */
        private const val EARLY_REVIEW_MAX_RETRIEVABILITY =
            AlgorithmConstants.ReviewScoring.EARLY_REVIEW_MAX_RETRIEVABILITY
        private const val MAX_WEAKNESS_ONLY_SHARE = 0.25
        private const val WEAKNESS_SHARE_PENALTY = 3.0
        private const val DUE_WEIGHT = AlgorithmConstants.ReviewScoring.DUE_WEIGHT
        private const val WEAKNESS_WEIGHT = AlgorithmConstants.ReviewScoring.WEAKNESS_WEIGHT
        private const val LAPSE_WEIGHT = AlgorithmConstants.ReviewScoring.LAPSE_WEIGHT
        private const val REPEAT_MISTAKE_WEIGHT = AlgorithmConstants.ReviewScoring.REPEAT_MISTAKE_WEIGHT
        private const val AVOIDANCE_WEIGHT = AlgorithmConstants.ReviewScoring.AVOIDANCE_WEIGHT
        private const val EXAM_WEIGHT = AlgorithmConstants.ReviewScoring.EXAM_WEIGHT
        private const val WAITING_WEIGHT = AlgorithmConstants.ReviewScoring.WAITING_WEIGHT
        private const val FAMILY_PENALTY_WEIGHT = AlgorithmConstants.ReviewScoring.FAMILY_PENALTY_WEIGHT
        private const val SOURCE_PENALTY_WEIGHT = AlgorithmConstants.ReviewScoring.SOURCE_PENALTY_WEIGHT
        private const val MAX_DIVERSITY_PENALTY = AlgorithmConstants.ReviewScoring.MAX_DIVERSITY_PENALTY
        /** Hard constraint (audit §7.2): same-subject consecutive run limit. */
        private const val MAX_SAME_SUBJECT_RUN = 3
        private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS.toDouble()
        private const val RECENT_LAPSE_WINDOW_MILLIS = AlgorithmConstants.ReviewScoring.RECENT_LAPSE_WINDOW_MILLIS
        private const val WAITING_GRACE_DAYS = AlgorithmConstants.ReviewScoring.WAITING_GRACE_DAYS
        private const val WAITING_BONUS_RAMP_DAYS = AlgorithmConstants.ReviewScoring.WAITING_BONUS_RAMP_DAYS
        private const val PLAN_FINGERPRINT_SCHEMA_VERSION = "review-plan-canonical-v9"
        private const val MAX_BEAM_STEPS = 20
    }
}

/** Returns a copy of the frequency map with [key] incremented by one. */
private fun Map<String, Int>.increment(key: String): Map<String, Int> {
    return this + (key to (this[key] ?: 0) + 1)
}
