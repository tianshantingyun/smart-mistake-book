package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.AttentionSignal
import com.tingyun.smartmistakebook.core.domain.FsrsEvidenceRatingMapper
import com.tingyun.smartmistakebook.core.domain.MasteryEvidencePolicy
import com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission
import com.tingyun.smartmistakebook.core.model.AssessmentAssistanceEvent
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.AssessmentSubmissionContext
import com.tingyun.smartmistakebook.core.model.AttemptSubmittedResponse
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.PersistedAssessmentAssistance
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.TutorAssistanceKind

/**
 * Builds the immutable write commands for every study submission path: curated
 * choices. Extracted from the study repository so the evidence-weighting rules
 * (attention and response-time discounts, hint/retry pricing) stay in one auditable place.
 */
internal class StudySubmissionPreparer(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val fixtureSource: StudyFixtureSource,
    private val reviewLogSink: ReviewLogSink,
    private val writeContext: StudyWriteContext,
) {

    suspend fun prepareChoiceSubmission(
        submission: StudyChoiceSubmission,
    ): PreparedChoiceSubmission {
        val artifact = writeContext.requireTeachingArtifact(submission.practiceUnitId)
        val assessmentItem = artifact.assessmentItems.singleOrNull()
            ?: error("Curated practice unit ${submission.practiceUnitId} must have one assessment")
        val evidenceSnapshot = requireNotNull(
            fixtureSource.evidenceSnapshotForAssessment(assessmentItem.id),
        ) { "No verified evidence snapshot for assessment ${assessmentItem.id}" }
        val evaluation = assessmentItem.evaluateChoice(submission.selectedChoiceId)
        val submittedResponse = AttemptSubmittedResponse.Choice(
            choiceId = evaluation.choice.id,
            choiceMarkdown = evaluation.choice.markdown,
            submittedAtEpochMillis = submission.occurredAtEpochMillis,
        )
        // W1-3/KF-02：提交前读该呈现上**已经发生**的答案揭示。揭示之后这道题的任何作答都不是
        // 独立回忆——MasteryEvidencePolicy 的 ANSWER_WAS_REVEALED / INCORRECT_AFTER_REVEAL 分支
        // 只有把揭示事实真的递进来才可达（此前本类从不传 persistedAssistance，
        // revealedBeforeAnswer 也硬编码 false，两个分支因此不可达，审计 KF-02 的原始证据）。
        //
        // 序号空间：揭示的 eventSequence 是账本全局序号（learning_sequence 分配，与本次作答的
        // event_sequence 同一计数器），responseSequence 必须落在它之后本次比较才成立。本次作答的
        // 真实序号由 DB 事务内分配（此刻还不存在），"揭示序号 + 1" 是它的下界：真实值 ≥ 下界。
        // 没有揭示时保持 responseOrdinal（此时协助集为空，该字段不参与任何先后比较）。
        val revealedBefore = database.findAnswerRevealForPresentation(
            learnerId = learnerId,
            presentationId = submission.presentationId,
        )
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = assessmentItem,
            context = AssessmentSubmissionContext(
                assessmentItemId = assessmentItem.id,
                selectedChoiceId = submission.selectedChoiceId,
                presentationId = submission.presentationId,
                responseSequence = revealedBefore
                    ?.let { it.eventSequence + 1 }
                    ?: submission.responseOrdinal.toLong(),
                responseOrdinal = submission.responseOrdinal,
                persistedAssistance = revealedBefore?.let { fact ->
                    listOf(
                        PersistedAssessmentAssistance(
                            event = AssessmentAssistanceEvent(
                                eventId = fact.outcomeId,
                                assessmentItemId = assessmentItem.id,
                                presentationId = submission.presentationId,
                                kind = TutorAssistanceKind.ANSWER_REVEAL,
                                contentMarkdown = artifact.explanationMarkdown,
                                occurredAtEpochMillis = fact.occurredAtEpochMillis,
                                eventSequence = fact.eventSequence,
                            ),
                            // 揭示在本次提交前已经持久化（能读到它本身就是证据），模型契约
                            // 要求 persistence ≥ occurrence，取提交时刻即真值。
                            persistedAtEpochMillis = submission.occurredAtEpochMillis,
                        ),
                    )
                } ?: emptyList(),
            ),
        )
        // Attention + response-time discount (spec 2.14): switches and
        // away-time fragment encoding (Craik et al. 1996) and a personally
        // abnormally fast answer is a suspected guess (Meyer 2010), so the
        // evidence weight shrinks and maps to a lower grade via the mapper.
        val attentionFactor = AttentionSignal.attentionFactor(
            submission.interruptionCount,
            submission.awayMillis,
        )
        val rtFactor = reviewLogSink.responseTimeDiscount(
            isCorrect = evaluation.isCorrect,
            durationMs = submission.durationSeconds * 1000L,
        )
        val discountedEvidence = decision.evidence.let { evidence ->
            val factor = (attentionFactor * rtFactor).coerceIn(0.0, 1.0)
            if (factor < 1.0 && evidence.direction != LearningEvidenceDirection.NONE) {
                evidence.copy(weight = (evidence.weight * factor).coerceIn(0.0, 1.0))
            } else {
                evidence
            }
        }
        return PreparedChoiceSubmission(
            evidenceSnapshot = evidenceSnapshot,
            command = AttemptWriteCommand(
                learnerId = learnerId,
                submissionId = writeContext.stableId("submission", submission.requestId),
                attemptId = writeContext.stableId("attempt", submission.requestId),
                presentationId = submission.presentationId,
                assessmentSnapshotId = evidenceSnapshot.snapshotId,
                submittedResponse = submittedResponse,
                evidence = discountedEvidence,
                problemMemoryOutcome = decision.problemMemoryOutcome,
                occurredAtEpochMillis = submission.occurredAtEpochMillis,
                durationSeconds = submission.durationSeconds,
                studyDay = writeContext.studyDayAt(submission.occurredAtEpochMillis),
                hintCount = submission.hintCount,
                // W1-3/KF-02：真值落库（此前硬编码 false）。该列当前无读者——它的价值是
                // 让"这次作答不是独立回忆"在 attempt_event 里可查，供后续波次的证据审计。
                revealedBeforeAnswer = revealedBefore != null,
            ),
            isCorrect = evaluation.isCorrect,
        )
    }


    internal data class PreparedChoiceSubmission(
        val evidenceSnapshot: AssessmentEvidenceSnapshot,
        val command: AttemptWriteCommand,
        val isCorrect: Boolean,
    )


}
