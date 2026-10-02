package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeGroundingSummaryRecord
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.ReviewPlanBundle
import com.tingyun.smartmistakebook.core.database.ReviewSessionRecord
import com.tingyun.smartmistakebook.core.database.ReviewedKnowledgeCoverageRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.ClearlyMasteredForSkipPolicy
import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
import com.tingyun.smartmistakebook.core.domain.MasteryEstimateMath
import com.tingyun.smartmistakebook.core.domain.ReviewCompletionStreak
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeCoverageGap
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeCoverageOverview
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSubjectCoverage
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyQuestionMemory
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionProgress
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind

/**
 * Pure projections from database records onto the read models consumed by the
 * four feature tabs. Extracted from the repository so the composition root
 * keeps only orchestration; every dependency a mapper needs is an explicit
 * parameter, which is what makes these functions testable on their own.
 */
internal fun MistakeRecord.toCatalogEntry(
    learnerSnapshot: LearnerSnapshot,
    atEpochMillis: Long,
    resolvedKnowledgeNames: Map<String, String>,
    forgettingCurve: ForgettingCurve,
    /** KF-16 / 裁决 28：节点 → 其先修的记忆稳定度（无条目 = 无已知先修）。 */
    prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>> = emptyMap(),
): StudyCatalogEntry {
    val memory = learnerSnapshot.problemMemoryStates[practiceUnitId]
    val knowledgeNodeIds = this.knowledgeNodeIds
    val knowledgeStates = knowledgeNodeIds.mapNotNull(
        learnerSnapshot.knowledgeMasteryStates::get,
    )
    return StudyCatalogEntry(
        entryId = entryId,
        problemId = problemId,
        problemRevisionId = problemRevisionId,
        practiceUnitId = practiceUnitId,
        subject = subject,
        title = title,
        problemMarkdown = problemMarkdown,
        sourceKey = sourceKey,
        // D-M M1：curated fixture 目录退场后不再有"本地示例"标记；字段保留以维持读模型契约。
        isCuratedExample = false,
        chapterLabels = chapterLabels,
        knowledgeLabels = knowledgeLabels.ifEmpty {
            knowledgeNodeIds.map { knowledgeNodeId ->
                resolvedKnowledgeNames[knowledgeNodeId] ?: knowledgeNodeId
            }
        },
        masteryStatus = knowledgeStates.conservativeMasteryStatus(
            atEpochMillis = atEpochMillis,
            decay = forgettingCurve.decay,
            prerequisiteStabilityDaysByNode = prerequisiteStabilityDaysByNode,
        ),
        nextReviewAtEpochMillis = memory?.nextReviewAtEpochMillis ?: nextReviewAtEpochMillis,
        retrievability = memory?.let { forgettingCurve.retentionAt(it, atEpochMillis) }
            ?: retrievability,
        questionMemory = memory?.let { state ->
            StudyQuestionMemory(
                independentRecallCount = state.independentCorrectCount,
                assistedRecallCount = state.assistedCorrectCount,
                retrievalFailureCount = state.lapseCount,
                answerRevealCount = state.answerRevealCount,
                lastReviewedAtEpochMillis = state.lastReviewedAtEpochMillis,
                nextReviewAtEpochMillis = state.nextReviewAtEpochMillis,
                retrievabilityAtSnapshot = forgettingCurve.retentionAt(state, atEpochMillis),
                projectionIsCurrent = learnerSnapshot.freshness == LearnerSnapshotFreshness.CURRENT &&
                    learnerSnapshot.projectionStatus == ProjectionStatus.CURRENT,
            )
        },
    )
}

/**
 * 裁决 28（读侧语义闭合，2026-10-01）：列表级"保守状态"由**读时现算**的
 * [ClearlyMasteredForSkipPolicy.effectiveStatus] 聚合，不再读写入瞬间的冻结 `status`
 * （先把每个 state 换成此刻的有效状态，再按同一优先级聚合：CONFLICTED > STALE > LEARNING
 * > 全 MASTERED > UNKNOWN）。
 */
internal fun List<KnowledgeMasteryState>.conservativeMasteryStatus(
    atEpochMillis: Long,
    decay: Double,
    prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>> = emptyMap(),
): MasteryStatus = when {
    isEmpty() -> MasteryStatus.UNKNOWN
    any { it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) == MasteryStatus.CONFLICTED } ->
        MasteryStatus.CONFLICTED
    any { it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) == MasteryStatus.STALE } ->
        MasteryStatus.STALE
    any { it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) == MasteryStatus.LEARNING } ->
        MasteryStatus.LEARNING
    all { it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) == MasteryStatus.MASTERED } ->
        MasteryStatus.MASTERED
    else -> MasteryStatus.UNKNOWN
}

/** 读侧单点状态（[conservativeMasteryStatus] 与 profile 过滤共用同一出口）。 */
private fun KnowledgeMasteryState.resolvedStatus(
    atEpochMillis: Long,
    decay: Double,
    prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>>,
): MasteryStatus = ClearlyMasteredForSkipPolicy.effectiveStatus(
    state = this,
    atEpochMillis = atEpochMillis,
    decay = decay,
    prerequisiteStabilityDays = prerequisiteStabilityDaysByNode[knowledgeNodeId].orEmpty(),
)

internal fun LearnerSnapshot.toProfileOverview(
    resolvedKnowledgeContexts: Map<String, ResolvedKnowledgeContext>,
    fallbackKnowledgeNames: Map<String, String>,
    /**
     * 裁决 28：读时重算三件套——评估时刻（与排程同钟：`planningContext.planningAtEpochMillis`）、
     * 同源 FSRS decay（`forgettingCurve.decay`）、先修稳定度表（KF-16 压制；无条目 = 无已知先修）。
     * 不给默认值：漏传 = 编译失败，不允许再有"忘传就把已忘的点算成强项"的静默路径。
     */
    atEpochMillis: Long,
    decay: Double,
    prerequisiteStabilityDaysByNode: Map<String, Collection<Double?>>,
): StudyProfileOverview {
    val weaknesses = knowledgeMasteryStates.values
        .filter { it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) != MasteryStatus.MASTERED }
        .sortedWith(
            compareBy<KnowledgeMasteryState> { it.conservativeMasteryScore }
                .thenBy { it.knowledgeNodeId },
        )
        .map { state ->
            val context = resolvedKnowledgeContexts[state.knowledgeNodeId]
            // P8（规格 §1-C）：保守分与区间上界由**同一次** interval(s, f) 产出——
            // 下界写回 conservativeMasteryScore（排序与档位在用它），读面重算、不落库。
            val interval = MasteryEstimateMath.interval(state.successWeight, state.failureWeight)
            StudyKnowledgeSummary(
                knowledgeNodeId = state.knowledgeNodeId,
                displayName = context?.displayName
                    ?: fallbackKnowledgeNames[state.knowledgeNodeId]
                    ?: state.knowledgeNodeId,
                status = state.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode),
                conservativeMasteryScore = interval.lower,
                masteryIntervalUpper = if (state.successWeight + state.failureWeight > 0.0) {
                    interval.upper
                } else {
                    null
                },
                memoryStabilityDays = state.memoryStabilityDays,
                evidenceMass = state.evidenceMass,
                independentCorrectObservationCount =
                    state.independentCorrectObservations.size,
                lastEvidenceAtEpochMillis = state.lastEvidenceAtEpochMillis,
                lastIndependentErrorAtEpochMillis =
                    state.lastIndependentErrorAtEpochMillis,
                subject = context?.subject ?: SubjectKind.GENERAL,
                topicPath = context?.topicPath.orEmpty(),
            )
        }
    val strengths = knowledgeMasteryStates.values
        .filter { it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) == MasteryStatus.MASTERED }
        .sortedWith(
            compareByDescending<KnowledgeMasteryState> { it.conservativeMasteryScore }
                .thenBy { it.knowledgeNodeId },
        )
        .map { state ->
            val context = resolvedKnowledgeContexts[state.knowledgeNodeId]
            // P8（规格 §1-C）：与 weaknesses 同一口径（同一次 interval(s, f)，下界回写）。
            val interval = MasteryEstimateMath.interval(state.successWeight, state.failureWeight)
            StudyKnowledgeSummary(
                knowledgeNodeId = state.knowledgeNodeId,
                displayName = context?.displayName
                    ?: fallbackKnowledgeNames[state.knowledgeNodeId]
                    ?: state.knowledgeNodeId,
                status = state.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode),
                conservativeMasteryScore = interval.lower,
                masteryIntervalUpper = if (state.successWeight + state.failureWeight > 0.0) {
                    interval.upper
                } else {
                    null
                },
                memoryStabilityDays = state.memoryStabilityDays,
                evidenceMass = state.evidenceMass,
                independentCorrectObservationCount =
                    state.independentCorrectObservations.size,
                lastEvidenceAtEpochMillis = state.lastEvidenceAtEpochMillis,
                lastIndependentErrorAtEpochMillis =
                    state.lastIndependentErrorAtEpochMillis,
                subject = context?.subject ?: SubjectKind.GENERAL,
                topicPath = context?.topicPath.orEmpty(),
            )
        }
    return StudyProfileOverview(
        hasLearningEvidence = appliedAttemptRecords.isNotEmpty(),
        recordedAttemptCount = appliedAttemptRecords.size,
        newlyMasteredCount = knowledgeMasteryStates.values.count {
            it.resolvedStatus(atEpochMillis, decay, prerequisiteStabilityDaysByNode) == MasteryStatus.MASTERED
        },
        weaknesses = weaknesses,
        strengths = strengths,
        projectionIsCurrent = freshness == LearnerSnapshotFreshness.CURRENT &&
            projectionStatus == ProjectionStatus.CURRENT,
    )
}

internal fun ReviewPlanBundle.toOverview(
    completedReviewDays: List<Long>,
    currentLocalDay: Long,
    intakeBacklogCount: Int,
    intakeMedianEstimateSeconds: Int,
): StudyReviewOverview {
    val visibleSession = activeSession ?: latestSession
    val effectiveCompletedDays = if (
        latestSession?.status == StudyDbValue.ReviewStatus.COMPLETED
    ) {
        completedReviewDays + plan.localDayEpochDay
    } else {
        completedReviewDays
    }
    val totalEstimatedSeconds = queue.fold(0L) { total, item ->
        Math.addExact(total, item.estimatedSeconds.toLong())
    }
    check(totalEstimatedSeconds in 0..Int.MAX_VALUE.toLong()) {
        "Review plan duration exceeds the supported UI range"
    }
    return StudyReviewOverview(
        planId = plan.reviewPlanId,
        scheduledCount = queue.size,
        estimatedSeconds = totalEstimatedSeconds.toInt(),
        reasons = queue.flatMapTo(linkedSetOf()) { item ->
            item.reasons.map { com.tingyun.smartmistakebook.core.model.ReviewReason.valueOf(it) }
        },
        scheduledPracticeUnitIds = queue.sortedBy { it.ordinal }.map { it.practiceUnitId },
        activeSessionId = activeSession?.reviewSessionId,
        currentOrdinal = visibleSession?.currentOrdinal ?: 0,
        sessionStateVersion = visibleSession?.stateVersion,
        completedToday = currentLocalDay in effectiveCompletedDays,
        completionStreakDays = ReviewCompletionStreak.count(
            completedLocalDays = effectiveCompletedDays,
            currentLocalDay = currentLocalDay,
        ),
        intakeBacklogCount = intakeBacklogCount,
        intakeMedianEstimateSeconds = intakeMedianEstimateSeconds,
    )
}

internal fun ReviewSessionRecord.toProgress(queueSize: Int) = StudyReviewSessionProgress(
    sessionId = reviewSessionId,
    planId = reviewPlanId,
    currentOrdinal = currentOrdinal,
    queueSize = queueSize,
    stateVersion = stateVersion,
    status = when (status) {
        StudyDbValue.ReviewStatus.IN_PROGRESS -> StudyReviewSessionStatus.ACTIVE
        StudyDbValue.ReviewStatus.COMPLETED -> StudyReviewSessionStatus.COMPLETED
        else -> error("Review session $reviewSessionId has unsupported status $status")
    },
)

internal fun List<ReviewedKnowledgeCoverageRecord>.toKnowledgeCoverageOverview(
    groundingSummaries: List<KnowledgeGroundingSummaryRecord>,
): StudyKnowledgeCoverageOverview {
    val reviewedSubjects = map { summary ->
        StudyKnowledgeSubjectCoverage(
            subject = SubjectKind.valueOf(summary.subject),
            topicCount = summary.topicCount,
            atomicKnowledgeCount = summary.atomicKnowledgeCount,
            reviewedSourceCount = summary.reviewedSourceCount,
            latestReviewedAtEpochMillis = summary.latestReviewedAtEpochMillis,
        )
    }.sortedBy { coverage -> coverage.subject.ordinal }
    val pendingGaps = groundingSummaries.map { summary ->
        StudyKnowledgeCoverageGap(
            groundingKey = summary.groundingKey,
            subject = SubjectKind.valueOf(summary.subject),
            expectedParentKnowledgeDisplayName = summary.expectedParentKnowledgeDisplayName,
            query = summary.query,
            relatedQuestionCount = summary.relatedQuestionCount,
            firstObservedAtEpochMillis = summary.firstObservedAtEpochMillis,
            lastObservedAtEpochMillis = summary.lastObservedAtEpochMillis,
        )
    }
    return StudyKnowledgeCoverageOverview(
        reviewedSubjects = reviewedSubjects,
        pendingGaps = pendingGaps,
    )
}

/**
 * Median of the intake backlog's estimated seconds (spec batch-intake §6 P3):
 * the robust typical-item estimate for the coverage preview — a median is not
 * skewed by a few oversized outliers. Empty list → 0.
 */
internal fun List<MistakeRecord>.medianEstimateSeconds(): Int {
    if (isEmpty()) return 0
    val sorted = map(MistakeRecord::estimatedSeconds).sorted()
    return sorted[sorted.size / 2]
}

/** Resolved display context of one knowledge node, built from the knowledge table. */
internal data class ResolvedKnowledgeContext(
    val displayName: String,
    val subject: SubjectKind,
    val topicPath: List<String>,
)
