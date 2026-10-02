package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection

/**
 * 排程输入契约（`ReviewCandidate` / `ReviewPlanningRequest`）与 KC 传导压力。
 *
 * D-M M6（2026-10-02）：本文件曾承载 V1 `ReviewPlanner` 类——其 `plan()` 生产不可达
 * （默认 V2 且无翻闸点），知识点打分方法已抽成 [KnowledgeNodeScorer]。V1 类、其计划
 * 版本串与回滚开关一并退场，仅保留 V2 排程与知识点队列共用的这份输入契约。
 */

/**
 * Spec 5 KC-to-question propagation: when a bound knowledge node's latest
 * evidence was negative and mastery sits below the drop threshold, every
 * question bound to that node gains continuous pressure (bigger drop ->
 * bigger weight -> more likely to make the budget cut) plus an early-entry
 * reason. Not a mechanical gate.
 */
internal const val KC_DROP_MASTERY_THRESHOLD = 0.6
internal const val KC_DROP_WEIGHT = 2.5

internal fun kcMasteryDropPressure(
    masteryStates: List<com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState>,
): Double = masteryStates
    .filter { it.lastEvidenceDirection == LearningEvidenceDirection.NEGATIVE.name }
    .maxOfOrNull { state ->
        (KC_DROP_MASTERY_THRESHOLD - state.conservativeMasteryScore) / KC_DROP_MASTERY_THRESHOLD
    }?.coerceIn(0.0, 1.0) ?: 0.0

data class ReviewCandidate(
    val practiceUnitId: String,
    val knowledgeNodeIds: Set<String>,
    val itemFamilyId: String,
    val sourceBundleId: String?,
    val difficulty: Double,
    val estimatedDurationSeconds: Int,
    val examPriority: Double = 0.0,
    val repeatMistakePriority: Double = 0.0,
    val eligibleSinceEpochMillis: Long? = null,
    val recentFamilyCount: Int = 0,
    val recentSourceCount: Int = 0,
    /** Subject the mistake belongs to; drives the V2 "same-subject run" hard constraint. */
    val subjectId: String? = null,
    /** Item-type dimension for the personalized duration model; null until the data layer exposes it. */
    val itemType: String? = null,
    /** Leech state (spec §2.16): paused from regular scheduling until re-taught. */
    val leech: Boolean = false,
    /**
     * Avoidance signal (spec §6 / D'Mello 2013): the learner repeatedly
     * switched away from this card and graded it poorly - a difficulty or
     * aversion marker routing it toward re-teaching.
     */
    val avoidance: Boolean = false,
) {
    init {
        require(practiceUnitId.isNotBlank()) { "Practice unit id must not be blank" }
        require(knowledgeNodeIds.none(String::isBlank)) { "Knowledge-node ids must not be blank" }
        require(itemFamilyId.isNotBlank()) { "Item family id must not be blank" }
        require(sourceBundleId == null || sourceBundleId.isNotBlank()) {
            "Source bundle id must not be blank when provided"
        }
        require(subjectId == null || subjectId.isNotBlank()) {
            "Subject id must not be blank when provided"
        }
        require(itemType == null || itemType.isNotBlank()) {
            "Item type must not be blank when provided"
        }
        require(difficulty.isFinite() && difficulty in 1.0..10.0) {
            "Difficulty must be between one and ten"
        }
        require(estimatedDurationSeconds > 0) { "Estimated duration must be positive" }
        require(examPriority.isFinite() && examPriority in 0.0..1.0) {
            "Exam priority must be between zero and one"
        }
        require(repeatMistakePriority.isFinite() && repeatMistakePriority in 0.0..1.0) {
            "Repeat-mistake priority must be between zero and one"
        }
        require(eligibleSinceEpochMillis == null || eligibleSinceEpochMillis >= 0) {
            "Review eligibility time must not be negative"
        }
        require(recentFamilyCount >= 0) { "Recent family count must not be negative" }
        require(recentSourceCount >= 0) { "Recent source count must not be negative" }
    }
}

data class ReviewPlanningRequest(
    val learnerSnapshot: LearnerSnapshot,
    val candidates: List<ReviewCandidate>,
    val localDayEpochDay: Long,
    val timeZoneId: String,
    val timeBudgetSeconds: Int,
    val planningAtEpochMillis: Long,
    /**
     * KC prerequisite graph (spec §6, linkage L4): knowledge node id to its
     * prerequisite knowledge node ids, sourced from the PREREQUISITE_OF
     * relation table. Missing entries mean "no known prerequisites".
     */
    val knowledgePrerequisites: Map<String, Set<String>> = emptyMap(),
) {
    init {
        require(timeZoneId.isNotBlank()) { "Review planning time-zone id must not be blank" }
        require(timeBudgetSeconds >= 0) { "Time budget must not be negative" }
        require(planningAtEpochMillis >= 0) { "Review planning time must not be negative" }
        require(
            planningAtEpochMillis >= learnerSnapshot.decisionWatermarkEpochMillis,
        ) { "Review planning time must not precede the projected snapshot or correction watermark" }
        require(candidates.map(ReviewCandidate::practiceUnitId).distinct().size == candidates.size) {
            "A planning request must not repeat a practice unit"
        }
    }
}
