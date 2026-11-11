package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AnswerRevealWriteCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealRequest
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealResult
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason

/**
 * Answer-reveal writes for a saved question: the reveal outcome row plus the
 * zero-weight review-log entry that keeps the reveal visible to the memory
 * model without pretending to be recall evidence. Extracted from the study
 * repository so the reveal contract stays readable next to its write.
 */
internal class StudyAnswerRevealService(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val practiceUnitFacts: StudyPracticeUnitFacts,
    private val reviewLogSink: ReviewLogSink,
    private val writeContext: StudyWriteContext,
    private val learnerSnapshot: suspend () -> LearnerSnapshot,
) {
    suspend fun reveal(request: StudyAnswerRevealRequest): StudyAnswerRevealResult {
        // D-M M1：揭示所需元数据与提交同源（practice unit + 当前绑定 + 题目字段派生），
        // fixture 目录门已删除。
        val facts = practiceUnitFacts.submissionFacts(request.practiceUnitId)
        val evidenceSnapshot = facts.evidenceSnapshot

        database.saveAssessmentEvidenceSnapshot(evidenceSnapshot)
        val priorMemory = learnerSnapshot().problemMemoryStates
            ?.get(request.practiceUnitId)
        val writeResult = database.recordAnswerReveal(
            AnswerRevealWriteCommand(
                learnerId = learnerId,
                assessmentEventId = writeContext.stableId("answer-reveal", request.requestId),
                presentationId = request.presentationId,
                assessmentSnapshotId = evidenceSnapshot.snapshotId,
                contentMarkdown = facts.explanationMarkdown,
                occurredAtEpochMillis = request.occurredAtEpochMillis,
                studyDay = writeContext.studyDayAt(request.occurredAtEpochMillis),
            ),
        )
        if (writeResult.created) {
            reviewLogSink.record(
                practiceUnitId = request.practiceUnitId,
                evidence = LearningEvidence(
                    direction = LearningEvidenceDirection.NONE,
                    weight = 0.0,
                    reason = LearningEvidenceReason.ANSWER_REVEALED,
                ),
                occurredAtEpochMillis = request.occurredAtEpochMillis,
                durationSeconds = 0,
                studyDay = writeContext.studyDayAt(request.occurredAtEpochMillis),
                // W1-4/KF-03：揭示行必须与真实作答分开落 `source_kind`，否则它在拟合集里
                // 冒充一次 AGAIN 的真实作答（rating 由 ANSWER_REVEALED 映射成 AGAIN，见
                // `FsrsEvidenceRatingMapper`）；`SchedulingEvaluation.fittableReviewSamples`
                // 按本档排除。
                sourceKind = ReviewLogSink.SOURCE_KIND_REVEAL,
                sourceId = writeResult.outcome.outcomeId,
                priorMemory = priorMemory,
            )
        }
        return StudyAnswerRevealResult(
            outcomeId = writeResult.outcome.outcomeId,
            created = writeResult.created,
            explanationMarkdown = facts.explanationMarkdown,
        )
    }
}
