package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.knowledge.KnowledgePrerequisiteReader
import com.tingyun.smartmistakebook.core.database.MAX_REVIEW_COMPLETION_HISTORY_DAYS
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.domain.StudyExperienceSnapshot
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeCoverageOverview
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Composes the one read model every feature tab consumes from the current
 * mistakes, the learner projection and the retained-or-replanned review plan.
 * Extracted from the study repository so the snapshot contract (catalog,
 * review overview, profile, knowledge coverage, tutor slots) is stated in one
 * place instead of inside a write-path class.
 */
internal class StudySnapshotBuilder(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val studyZoneId: ZoneId,
    private val forgettingCurve: ForgettingCurve,
    private val plannerService: StudyReviewPlannerService,
    /**
     * KF-16 读侧接线（裁决 28）：展示面现算"此刻是否已掌握"需要先修稳定度做下行压制；
     * 图按需解析（只查本次快照引用的 KC），与排程侧共用同一个解析器。
     */
    private val knowledgePrerequisites: KnowledgePrerequisiteReader,
    private val learnerSnapshot: suspend () -> LearnerSnapshot,
) {
    suspend fun build(
        mistakes: List<MistakeRecord>,
        pendingCorrectionCount: Int,
        knowledgeCoverage: StudyKnowledgeCoverageOverview,
    ): StudyExperienceSnapshot {
        val projection = learnerSnapshot()
        check(
            projection.freshness == LearnerSnapshotFreshness.CURRENT &&
                projection.projectionStatus == ProjectionStatus.CURRENT,
        ) { "Cannot publish a study snapshot from a stale or incomplete learning projection" }

        val planningContext = plannerService.planningContext(projection)
        // W0-2/Q4 读时校验：版本门只落在**非进行中**的那条读回上——进行中的会话必须与显示队列
        // 同一份计划（`recordReviewAttempt` 按 ordinal 认队列项，显示另一份计划会让作答被拒），
        // 拿版本去挡它会让学生卡在唯一活跃会话的不变量上。见
        // `StudyReviewPlannerService.isCurrentPlannerVersion` 的两道边界注释。
        // 版本不符 → 不保留 → 下面的 `createReviewPlan` 重排一份（指纹含算法版本，落成新行）。
        val activePlan = database.observeActiveReviewPlan(learnerId).first()
        val retainedPlan = activePlan ?: database.observeCurrentReviewPlan(
            learnerId = learnerId,
            localDayEpochDay = planningContext.localDate.toEpochDay(),
            timeZoneId = studyZoneId.id,
        ).first()?.takeIf { current ->
            plannerService.isCurrentPlannerVersion(current) &&
                (
                    current.activeSession != null ||
                        current.latestSession?.status == StudyDbValue.ReviewStatus.COMPLETED
                    )
        }
        val reviewBundle = retainedPlan ?: plannerService.createReviewPlan(
            mistakes = mistakes,
            learnerSnapshot = projection,
            planningContext = planningContext,
        ).also { database.saveReviewPlan(it) }
        val completedReviewDays = database.observeCompletedReviewLocalDays(
            learnerId = learnerId,
            limit = MAX_REVIEW_COMPLETION_HISTORY_DAYS,
        ).first()
        val orderedMistakes = mistakes.sortedWith(
            compareByDescending<MistakeRecord>(MistakeRecord::createdAtEpochMillis)
                .thenBy(MistakeRecord::entryId),
        )
        // Intake backlog (spec batch-intake §1): never-attempted questions
        // (no memory state) that are NOT in today's plan queue — they stay in
        // the backlog with no learning pressure until introduced.
        val plannedUnitIds = reviewBundle.queue.mapTo(hashSetOf()) { it.practiceUnitId }
        val intakeBacklog = orderedMistakes.filter { mistake ->
            projection.problemMemoryStates[mistake.practiceUnitId] == null &&
                mistake.practiceUnitId !in plannedUnitIds
        }
        val referencedKnowledgeNodeIds = buildSet {
            addAll(projection.knowledgeMasteryStates.keys)
            orderedMistakes.forEach { mistake -> addAll(mistake.knowledgeNodeIds) }
        }
        // 裁决 28（KF-16 读侧接线）：把先修图解析一次，供 catalog / profile 两处现算
        // "此刻是否已掌握（含先修压制）"。图里查不到 = 无已知先修（"未知 ≠ 缺失"）。
        val prerequisiteStabilityDaysByNode = knowledgePrerequisites
            .graphFor(referencedKnowledgeNodeIds)
            .prerequisitesByDependent
            .mapValues { (_, prerequisiteIds) ->
                prerequisiteIds.map { prerequisiteId ->
                    projection.knowledgeMasteryStates[prerequisiteId]?.memoryStabilityDays
                }
            }
        val resolvedKnowledgeContexts = plannerService.resolveKnowledgeContexts(referencedKnowledgeNodeIds)
        // D-M M1：fixture 的 knowledgeNames 字典退场；展示名只来自知识库真值（解析器）。
        val resolvedKnowledgeNames = resolvedKnowledgeContexts.mapValues {
            it.value.displayName
        }
        return StudyExperienceSnapshot(
            status = StudyDataStatus.READY,
            catalog = orderedMistakes.map { mistake ->
                mistake.toCatalogEntry(
                    learnerSnapshot = projection,
                    atEpochMillis = planningContext.planningAtEpochMillis,
                    resolvedKnowledgeNames = resolvedKnowledgeNames,
                    forgettingCurve = forgettingCurve,
                    prerequisiteStabilityDaysByNode = prerequisiteStabilityDaysByNode,
                )
            },
            pendingCorrectionCount = pendingCorrectionCount,
            review = reviewBundle.toOverview(
                completedReviewDays = completedReviewDays,
                currentLocalDay = planningContext.localDate.toEpochDay(),
                intakeBacklogCount = intakeBacklog.size,
                intakeMedianEstimateSeconds = intakeBacklog.medianEstimateSeconds(),
            ),
            profile = projection.toProfileOverview(
                resolvedKnowledgeContexts = resolvedKnowledgeContexts,
                fallbackKnowledgeNames = resolvedKnowledgeNames,
                atEpochMillis = planningContext.planningAtEpochMillis,
                decay = forgettingCurve.decay,
                prerequisiteStabilityDaysByNode = prerequisiteStabilityDaysByNode,
            ),
            knowledgeCoverage = knowledgeCoverage,
            // D-M M1：curated tutor fixture 退场，"导师示例已保存"不再有任何来源，恒 false
            //（字段保留以维持读模型契约；UI 侧无消费者）。
            tutorExampleSaved = false,
            // The tutor root has no current question until the student captures or selects one.
            tutorPracticeUnitId = null,
            tutorDecision = null,
        )
    }
}
