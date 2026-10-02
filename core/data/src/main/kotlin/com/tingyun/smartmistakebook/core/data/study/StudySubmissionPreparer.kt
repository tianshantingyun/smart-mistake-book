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
    private val practiceUnitFacts: StudyPracticeUnitFacts,
    private val reviewLogSink: ReviewLogSink,
    private val writeContext: StudyWriteContext,
) {

    suspend fun prepareChoiceSubmission(
        submission: StudyChoiceSubmission,
    ): PreparedChoiceSubmission {
        // D-M M1：目录从库内事实派生（practice unit + 当前绑定 + 题目字段），
        // fixture 目录门（`outside the verified M1 catalog`）已删除。
        val facts = practiceUnitFacts.submissionFacts(submission.practiceUnitId)
        val assessmentItem = facts.assessmentItem
        val evidenceSnapshot = facts.evidenceSnapshot
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
        // 已知边界（2026-10-01 审查登记）：判定以 **outcome 表的存在性**为准——legacy 桥接
        // （`importLegacyAnswerRevealAssessmentEvent`/`reconcileAnswerRevealOutcomes`，当前均无
        // 生产调用方）物化前的 "terminal 但无 outcome 行" 会两头分叉；读取与写入之间的竞态窗口
        // 同理。两者中账本证据都由 DB 权威规范化兜住，只有 revealedBeforeAnswer 审计列可能欠账。
        //
        // 序号空间：揭示的 eventSequence 是账本全局序号（learning_sequence 分配，与本次作答的
        // event_sequence 同一计数器），responseSequence 必须落在它之后本次比较才成立。本次作答的
        // 真实序号由 DB 事务内分配（此刻还不存在），"揭示序号 + 1" 是它的下界：真实值 ≥ 下界。
        // 没有揭示时保持 responseOrdinal（此时协助集为空，该字段不参与任何先后比较）。
        val revealedBefore = database.findAnswerRevealForPresentation(
            learnerId = learnerId,
            presentationId = submission.presentationId,
        )
        // D-M M2：hintCount > 0 转成 HINT 形态的协助条目。此前 hint 只进 prediction 审计，
        // `AssessmentSubmissionContext.hintWasUsed` 由 `persistedAssistance` 派生，而提交路径
        // 只填 ANSWER_REVEAL —— INCORRECT_AFTER_HINT / CORRECT_AFTER_HINT 两档结构性不可达
        // （三档"答对"里只有独立答对是真的）。hint 的采集（UI）归阶段 5，本处只做非 UI 接线。
        //
        // 形态取**一条** HINT 事件（hintCount 是次数，不是逐条内容）：消费者只有
        // `hintWasUsed` 这一个布尔派生，逐条展开不会多消灭任何一个失败；次数本身仍原样落
        // `attempt_event.hint_count` 与 prediction 审计，不丢信息。hint 内容没有随提交持久化
        // （UI 只给计数），故 contentMarkdown 只写事实标记，不编造提示文本。
        //
        // 序号：hint 未落库、没有账本序号。合成序号锚在**揭示之后一位**（无揭示则从 1 起），
        // 使 (a) 与揭示序号不重（AssessmentSubmissionContext 要求序号互异）、(b) 严格小于
        // 本次的 responseSequence 水位。水位相应上移到"最大协助序号 + 1"；揭示存在时它不小于
        // 既有的"揭示序号 + 1"下界（真实作答序号 ≥ 揭示序号 + 1 = 下界，见上），因此不引入
        // 更强的错误断言。评估优先级不变：answerWasRevealed 分支在前，揭示与 hint 同时存在时
        // 仍由揭示定价（"揭示之后又看到提示"不改变判定）。
        val hintedAssistanceSequence = if (submission.hintCount > 0) {
            (revealedBefore?.eventSequence ?: 0L) + 1L
        } else {
            null
        }
        val assistance = buildList {
            revealedBefore?.let { fact ->
                add(
                    PersistedAssessmentAssistance(
                        event = AssessmentAssistanceEvent(
                            eventId = fact.outcomeId,
                            assessmentItemId = assessmentItem.id,
                            presentationId = submission.presentationId,
                            kind = TutorAssistanceKind.ANSWER_REVEAL,
                            contentMarkdown = facts.explanationMarkdown,
                            occurredAtEpochMillis = fact.occurredAtEpochMillis,
                            eventSequence = fact.eventSequence,
                        ),
                        // 揭示在本次提交前已经持久化（能读到它本身就是证据），模型契约
                        // 要求 persistence ≥ occurrence，取提交时刻即真值。
                        persistedAtEpochMillis = submission.occurredAtEpochMillis,
                    ),
                )
            }
            hintedAssistanceSequence?.let { hintSequence ->
                add(
                    PersistedAssessmentAssistance(
                        event = AssessmentAssistanceEvent(
                            // 确定性 id（同一条提交命令重放得到同一个值）：hint 事实的会话内
                            // 身份来自"哪次提交"本身，用与 submission/attempt 同一套
                            // stableId 规则派生，不引入随机源。
                            eventId = writeContext.stableId("assistance-hint", submission.requestId),
                            assessmentItemId = assessmentItem.id,
                            presentationId = submission.presentationId,
                            kind = TutorAssistanceKind.HINT,
                            contentMarkdown = "本呈现作答前使用过提示（hintCount=${submission.hintCount}）",
                            // 提示发生的真实时刻没有随提交采集（UI 只有计数），取提交时刻为
                            // 上界事实，不伪装成更早的精确时间。
                            occurredAtEpochMillis = submission.occurredAtEpochMillis,
                            eventSequence = hintSequence,
                        ),
                        persistedAtEpochMillis = submission.occurredAtEpochMillis,
                    ),
                )
            }
        }
        val responseSequence = hintedAssistanceSequence?.plus(1L)
            ?: revealedBefore?.let { it.eventSequence + 1 }
            ?: submission.responseOrdinal.toLong()
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = assessmentItem,
            context = AssessmentSubmissionContext(
                assessmentItemId = assessmentItem.id,
                selectedChoiceId = submission.selectedChoiceId,
                presentationId = submission.presentationId,
                responseSequence = responseSequence,
                responseOrdinal = submission.responseOrdinal,
                persistedAssistance = assistance,
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
