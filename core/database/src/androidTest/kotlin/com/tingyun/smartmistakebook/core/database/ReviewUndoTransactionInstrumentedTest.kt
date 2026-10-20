package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.AttemptSubmittedResponse
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-05 attempt 级 undo 的**数据库原子边界**（隔离内存库，不碰用户学习库）。
 *
 * 钉住：
 * 1. VOID 更正 + 会话回退 + review_log 有效样本标记在同一事务提交；
 * 2. 同一撤销请求重放不再回退（双击/杀进程重试幂等）；
 * 3. 撤销只回退**最后一次**推进，不能撤销更早的题；
 * 4. 撤销后重评能按新的 stateVersion 推进（version 与 ordinal 解耦）；
 * 5. review_log 保留审计行但 `scheduling_eligible=0`，且"前条"判定跳过它；
 * 6. 增量投影在更正处停下要求全量重放；全量重放把该 attempt 的学习效果完全排除。
 */
@RunWith(AndroidJUnit4::class)
class ReviewUndoTransactionInstrumentedTest {
    private lateinit var store: RoomStudyDatabase

    @Before
    fun setUp() = runBlocking {
        store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext<Context>())
        store.seedStudyFacts(seedBundle())
        store.saveAssessmentEvidenceSnapshot(evidenceSnapshot())
        // 第二题需要自己的快照（快照的 practiceUnitId 必须与队列项一致，否则 review 写入
        // 会被 cross-practice-unit 闸拒绝）。
        store.saveAssessmentEvidenceSnapshot(
            evidenceSnapshot(
                practiceUnitId = SECOND_UNIT_ID,
                snapshotId = SECOND_SNAPSHOT_ID,
                assessmentItemId = "assessment-item-2",
                bindingId = "binding-2",
                suffix = "2",
            ),
        )
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun voidRewindsSessionMarksReviewLogIneligibleAndSurvivesReplay() = runBlocking {
        val (plan, session) = saveStartedReview()
        val firstItem = plan.queue.sortedBy { it.ordinal }.first()
        val attempt = recordAttempt(
            suffix = "first",
            presentationId = "presentation-first",
            occurredAt = OCCURRED_AT + 30_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = 0,
            queueItem = firstItem,
            evidence = selfReportedRecall(),
            outcome = ProblemMemoryOutcome.ASSISTED_RECALL,
        )
        recordSelfReportReviewLog(attempt.attempt.attempt.attemptId)

        val undoCommand = undoCommand(
            sessionId = session.reviewSessionId,
            expectedStateVersion = 1,
            attempt = attempt,
            undoneAtEpochMillis = OCCURRED_AT + 60_000,
        )
        val undo = store.undoReviewAttempt(undoCommand)

        assertTrue(undo.created)
        assertEquals("会话回退一格", 0, undo.session.currentOrdinal)
        assertEquals("版本继续递增（undo 不倒退版本）", 2L, undo.session.stateVersion)
        assertEquals(StudyDbValue.ReviewStatus.IN_PROGRESS, undo.session.status)
        assertNull(undo.session.completedAtEpochMillis)
        assertTrue(undo.correction.isVoid)

        val ledger = store.loadLearningLedger(LEARNER).validPrefix.map { it.event }
        assertEquals(2, ledger.size)
        assertTrue(ledger[0] is com.tingyun.smartmistakebook.core.model.Attempt)
        assertTrue(ledger[1] is AttemptCorrection)
        assertEquals(
            "VOID 不改写/删除原 attempt 行",
            attempt.attempt.attempt.attemptId,
            (ledger[0] as com.tingyun.smartmistakebook.core.model.Attempt).attemptId,
        )

        val reviewRows = store.readReviewLogSamples(LEARNER, 100)
        assertEquals(1, reviewRows.size)
        assertFalse("撤销行保留审计但退出有效证据集合", reviewRows.single().schedulingEligible)
        assertNull(
            "撤销后「前条」判定不得读到被撤销的 attempt",
            store.findLastReviewLogRow(LEARNER, UNIT_ID),
        )

        val repaired = store.observeReviewPlanForSession(session.reviewSessionId).first()
        assertEquals(undo.session, repaired?.activeSession)

        // 同一撤销请求重放：不回退第二次（请求载荷逐位相同才允许幂等回执）。
        val replay = store.undoReviewAttempt(undoCommand)
        assertFalse("幂等：同一 request 的 correction id 已存在", replay.created)
        assertEquals(0, replay.session.currentOrdinal)
        assertEquals(2L, replay.session.stateVersion)

        // 撤销 → 重评：用新的版本号推进，队列项按会话 ordinal 定位。
        val reanswer = recordAttempt(
            suffix = "reanswer",
            presentationId = "presentation-first",
            occurredAt = OCCURRED_AT + 70_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = undo.session.stateVersion,
            queueItem = firstItem,
            evidence = LearningEvidence(
                direction = LearningEvidenceDirection.POSITIVE,
                weight = 0.7,
                reason = LearningEvidenceReason.SELF_REPORTED_RECALL,
            ),
            outcome = ProblemMemoryOutcome.ASSISTED_RECALL,
        )
        assertEquals(1, reanswer.advance.session.currentOrdinal)
        assertEquals(3L, reanswer.advance.session.stateVersion)
        assertTrue(reanswer.advance.created)
    }

    /**
     * PRD-05 曝光独立性：撤销只排除**评分**，不移除已发生的答案曝光；撤销后用同一
     * presentation 重评，DB 的权威规范化必须继续把它判成"看过答案后的作答"（NONE，
     * 非独立），不能借撤销恢复成独立未辅助作答。
     */
    @Test
    fun reanswerAfterUndoKeepsThePreRevealExposureAndIsNotIndependent() = runBlocking {
        val (plan, session) = saveStartedReview()
        val firstItem = plan.queue.sortedBy { it.ordinal }.first()
        val first = recordAttempt(
            suffix = "first",
            presentationId = "presentation-first",
            occurredAt = OCCURRED_AT + 30_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = 0,
            queueItem = firstItem,
            evidence = LearningEvidence(
                direction = LearningEvidenceDirection.POSITIVE,
                weight = 1.0,
                reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
            ),
            outcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        )
        // 作答后主动看答案：曝光事实在撤销后必须继续保留。
        val reveal = store.recordAnswerReveal(
            AnswerRevealWriteCommand(
                learnerId = LEARNER,
                assessmentEventId = "reveal-event-first",
                presentationId = "presentation-first",
                assessmentSnapshotId = SNAPSHOT_ID,
                contentMarkdown = "完整答案与推导",
                occurredAtEpochMillis = OCCURRED_AT + 40_000,
                studyDay = studyDay(OCCURRED_AT + 40_000),
            ),
        )
        assertTrue(reveal.created)

        val undo = store.undoReviewAttempt(
            undoCommand(
                sessionId = session.reviewSessionId,
                expectedStateVersion = 1,
                attempt = first,
                undoneAtEpochMillis = OCCURRED_AT + 50_000,
            ),
        )

        val reanswer = recordAttempt(
            suffix = "reanswer",
            presentationId = "presentation-first",
            occurredAt = OCCURRED_AT + 60_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = undo.session.stateVersion,
            queueItem = firstItem,
            evidence = LearningEvidence(
                direction = LearningEvidenceDirection.POSITIVE,
                weight = 1.0,
                reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
            ),
            outcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        )
        val persisted = requireNotNull(store.readAttemptP0(reanswer.attempt.attempt.attemptId))
        assertEquals("重评保留原 presentation 的序号因果", 2, persisted.attempt.responseOrdinal)
        assertEquals(
            "撤销评分不撤销已发生的曝光",
            LearningEvidenceReason.ANSWER_REVEALED,
            persisted.attempt.evidence.reason,
        )
        assertEquals(LearningEvidenceDirection.NONE, persisted.attempt.evidence.direction)
        assertFalse(persisted.attempt.evidence.isIndependent)

        // 撤销/重评都不删除历史：曝光 outcome 与 VOID 更正都留在账本里。
        val ledger = store.loadLearningLedger(LEARNER).validPrefix.map { it.event }
        assertEquals(1, ledger.count { it is AnswerRevealOutcome })
        assertEquals(1, ledger.count { it is AttemptCorrection })
    }

    @Test
    fun undoRejectsAnAttemptThatIsNotTheLastAdvance() = runBlocking {
        val (plan, session) = saveStartedReview()
        val items = plan.queue.sortedBy { it.ordinal }
        val first = recordAttempt(
            suffix = "first",
            presentationId = "presentation-first",
            occurredAt = OCCURRED_AT + 30_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = 0,
            queueItem = items[0],
            evidence = selfReportedRecall(),
            outcome = ProblemMemoryOutcome.ASSISTED_RECALL,
        )
        val second = recordAttempt(
            suffix = "second",
            presentationId = "presentation-second",
            occurredAt = OCCURRED_AT + 40_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = 1,
            queueItem = items[1],
            evidence = selfReportedRecall(),
            outcome = ProblemMemoryOutcome.ASSISTED_RECALL,
            snapshotId = SECOND_SNAPSHOT_ID,
        )
        assertEquals(2, second.advance.session.currentOrdinal)

        val failure = runCatching {
            store.undoReviewAttempt(
                undoCommand(
                    sessionId = session.reviewSessionId,
                    expectedStateVersion = 2,
                    attempt = first,
                    undoneAtEpochMillis = OCCURRED_AT + 60_000,
                ),
            )
        }

        assertTrue(
            "只能撤销最后一次推进；更早的 attempt 没有指向当前版本的 receipt",
            failure.exceptionOrNull() is ImmutablePayloadConflictException,
        )
        val current = store.observeReviewPlanForSession(session.reviewSessionId).first()
        assertEquals(2, current?.latestSession?.currentOrdinal)
        assertEquals(2, store.loadLearningLedger(LEARNER).validPrefix.size)
    }

    @Test
    fun incrementalProjectionStopsAndFullReplayExcludesTheVoidedAttempt() = runBlocking {
        val (plan, session) = saveStartedReview()
        val attempt = recordAttempt(
            suffix = "first",
            presentationId = "presentation-first",
            occurredAt = OCCURRED_AT + 30_000,
            sessionId = session.reviewSessionId,
            expectedStateVersion = 0,
            queueItem = plan.queue.sortedBy { it.ordinal }.first(),
            evidence = selfReportedRecall(),
            outcome = ProblemMemoryOutcome.ASSISTED_RECALL,
        )
        store.undoReviewAttempt(
            undoCommand(
                sessionId = session.reviewSessionId,
                expectedStateVersion = 1,
                attempt = attempt,
                undoneAtEpochMillis = OCCURRED_AT + 60_000,
            ),
        )

        val batch = store.loadProjectionBatch(PROJECTION, LEARNER, 100)
        assertEquals(
            "增量遇到更正必须停下并触发全量重放",
            ProjectionBatchStopReason.FULL_REPLAY_REQUIRED,
            batch.stopReason,
        )
        assertEquals(
            "更正之前的 attempt 已读入，但不得跨过更正继续增量",
            1,
            batch.events.size,
        )

        val ledger = store.loadLearningLedger(LEARNER).validPrefix.map { it.event }
        val replay = LearningProjector().replay(LEARNER, ledger)
        assertEquals(setOf(attempt.attempt.attempt.attemptId), replay.voidedAttemptIds)
        assertTrue(
            "全量重放：被撤销的 attempt 不建卡",
            replay.snapshot.problemMemoryStates.isEmpty(),
        )
        assertTrue(
            "全量重放：被撤销的 attempt 不产生掌握度",
            replay.snapshot.knowledgeMasteryStates.isEmpty(),
        )
        assertTrue(attempt.attempt.attempt.attemptId in replay.appliedAttemptIds)
    }

    // ---- fixtures ----

    private suspend fun saveStartedReview(): Pair<ReviewPlanBundle, ReviewSessionRecord> {
        val plan = reviewBundle()
        store.saveReviewPlan(plan)
        val session = ReviewSessionRecord(
            reviewSessionId = SESSION_ID,
            reviewPlanId = plan.plan.reviewPlanId,
            status = StudyDbValue.ReviewStatus.IN_PROGRESS,
            startedAtEpochMillis = OCCURRED_AT,
            lastActiveAtEpochMillis = OCCURRED_AT,
            completedAtEpochMillis = null,
            currentOrdinal = 0,
            timeBudgetSeconds = plan.plan.timeBudgetSeconds,
            projectionCheckpoint = plan.plan.projectionCheckpoint,
        )
        store.saveReviewSession(session)
        return plan to session
    }

    private suspend fun recordAttempt(
        suffix: String,
        presentationId: String,
        occurredAt: Long,
        sessionId: String,
        expectedStateVersion: Long,
        queueItem: ReviewQueueItemRecord,
        evidence: LearningEvidence,
        outcome: ProblemMemoryOutcome,
        snapshotId: String = SNAPSHOT_ID,
    ): ReviewAttemptWriteResult {
        return store.recordReviewAttempt(
            ReviewAttemptWriteCommand(
                attempt = AttemptWriteCommand(
                    learnerId = LEARNER,
                    submissionId = "submission-$suffix",
                    attemptId = "attempt-$suffix",
                    presentationId = presentationId,
                    assessmentSnapshotId = snapshotId,
                    submittedResponse = AttemptSubmittedResponse.Choice(
                        choiceId = "choice-a",
                        choiceMarkdown = "自评：对",
                        submittedAtEpochMillis = occurredAt,
                    ),
                    evidence = evidence,
                    problemMemoryOutcome = outcome,
                    occurredAtEpochMillis = occurredAt,
                    durationSeconds = 30,
                    studyDay = studyDay(occurredAt),
                    hintCount = 0,
                    revealedBeforeAnswer = false,
                ),
                sessionId = sessionId,
                expectedStateVersion = expectedStateVersion,
                reviewQueueItemId = queueItem.reviewQueueItemId,
                practiceUnitId = queueItem.practiceUnitId,
            ),
        )
    }

    private suspend fun recordSelfReportReviewLog(attemptId: String) {
        store.recordReviewLogEntries(
            listOf(
                ReviewLogEntry(
                    learnerId = LEARNER,
                    practiceUnitId = UNIT_ID,
                    rating = 3,
                    deltaTDays = 0.0,
                    durationMs = 30_000,
                    reviewedAtEpochMillis = OCCURRED_AT + 30_000,
                    sourceKind = "SELF_REPORT",
                    sourceId = attemptId,
                    evidenceWeight = 0.8,
                    timeBucket = "MORNING",
                    state = 0,
                    recordedAtEpochMillis = OCCURRED_AT + 30_001,
                ),
            ),
        )
    }

    private fun undoCommand(
        sessionId: String,
        expectedStateVersion: Long,
        attempt: ReviewAttemptWriteResult,
        undoneAtEpochMillis: Long,
    ): ReviewAttemptUndoCommand {
        val correction = AttemptCorrection.voidOf(
            correctionId = "correction-void-${attempt.attempt.attempt.attemptId}",
            attemptId = attempt.attempt.attempt.attemptId,
            reasonMarkdown = "学生撤销了本次复习自评",
            occurredAtEpochMillis = undoneAtEpochMillis,
            eventSequence = 1,
        )
        return ReviewAttemptUndoCommand(
            learnerId = LEARNER,
            sessionId = sessionId,
            expectedStateVersion = expectedStateVersion,
            practiceUnitId = UNIT_ID,
            attemptId = attempt.attempt.attempt.attemptId,
            undoneAtEpochMillis = undoneAtEpochMillis,
            correction = AttemptCorrectionRecord(
                learnerId = LEARNER,
                submissionId = attempt.attempt.submissionId,
                correctionId = correction.correctionId,
                attemptId = correction.attemptId,
                replacementEvidence = correction.replacementEvidence,
                replacementMemoryOutcome = correction.replacementMemoryOutcome,
                reasonMarkdown = correction.reasonMarkdown,
                occurredAtEpochMillis = correction.occurredAtEpochMillis,
            ),
        )
    }

    private fun reviewBundle(): ReviewPlanBundle {
        // 契约要求 reviewPlanId == "plan-$planFingerprint"。
        val planId = "plan-review-undo-plan"
        return ReviewPlanBundle(
            plan = ReviewPlanRecord(
                reviewPlanId = planId,
                learnerId = LEARNER,
                localDate = java.time.LocalDate.ofEpochDay(LOCAL_DAY).toString(),
                localDayEpochDay = LOCAL_DAY,
                timeZoneId = ZONE,
                timeBudgetSeconds = 900,
                planningAtEpochMillis = OCCURRED_AT,
                status = StudyDbValue.ReviewStatus.PLANNED,
                plannerVersion = "planner-v2",
                projectionCheckpoint = 0,
                planFingerprint = "review-undo-plan",
                planRevision = 1,
                createdAtEpochMillis = OCCURRED_AT,
            ),
            queue = listOf(
                queueItem(planId, ordinal = 0, practiceUnitId = UNIT_ID),
                queueItem(planId, ordinal = 1, practiceUnitId = SECOND_UNIT_ID),
            ),
            activeSession = null,
            isCurrent = false,
        )
    }

    private fun queueItem(planId: String, ordinal: Int, practiceUnitId: String) = ReviewQueueItemRecord(
        reviewQueueItemId = "queue-$ordinal",
        reviewPlanId = planId,
        practiceUnitId = practiceUnitId,
        knowledgeNodeIds = setOf(KNOWLEDGE_ID),
        itemFamilyId = "family-1",
        sourceBundleId = null,
        reasons = setOf("DUE_RECALL_RISK"),
        ordinal = ordinal,
        priorityScore = 0.75,
        difficultyBand = "MEDIUM",
        dueAtEpochMillis = null,
        estimatedSeconds = 300,
        reasonSnapshot = "到期",
    )

    private fun evidenceSnapshot(
        practiceUnitId: String = UNIT_ID,
        snapshotId: String = SNAPSHOT_ID,
        assessmentItemId: String = "assessment-item-1",
        bindingId: String = BINDING_ID,
        suffix: String = "1",
    ) = AssessmentEvidenceSnapshot(
        snapshotId = snapshotId,
        assessmentItemId = assessmentItemId,
        practiceUnitId = practiceUnitId,
        problemRevisionId = REVISION_ID,
        answerSpecId = "answer-spec-$suffix",
        itemFamilyId = "family-$suffix",
        sourceBundleId = null,
        taxonomyVersion = "taxonomy-v1",
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            CalibrationSupport.SUPPORTED,
            "calibration-source",
            "calibration-v1",
            0,
            OCCURRED_AT,
        ),
        attributions = listOf(
            KnowledgeEvidenceAttribution(
                bindingId = bindingId,
                knowledgeNodeId = KNOWLEDGE_ID,
                weight = 1.0,
                basisRevisionId = REVISION_ID,
                taxonomyVersion = "taxonomy-v1",
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        ),
        capturedAtEpochMillis = OCCURRED_AT - 1_000,
    )

    private fun selfReportedRecall() = LearningEvidence(
        direction = LearningEvidenceDirection.POSITIVE,
        weight = 0.8,
        reason = LearningEvidenceReason.SELF_REPORTED_RECALL,
    )

    private fun seedBundle() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(PROBLEM_ID, "problem-fingerprint", "MATH", OCCURRED_AT - 10_000),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = REVISION_ID,
                problemId = PROBLEM_ID,
                revisionNumber = 1,
                title = "函数单调性",
                problemMarkdown = "求函数的单调区间。",
                answerSpecId = "answer-spec-1",
                answerSpecSnapshot = "增区间与减区间",
                answerVerificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
                sourceType = "IMPORT",
                sourceReference = null,
                contentFingerprint = "revision-fingerprint",
                createdAtEpochMillis = OCCURRED_AT - 9_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = UNIT_ID,
                problemId = PROBLEM_ID,
                problemRevisionId = REVISION_ID,
                unitKey = "whole",
                unitKind = "WHOLE",
                title = "函数单调性",
                promptMarkdown = "求单调区间。",
                estimatedSeconds = 120,
                createdAtEpochMillis = OCCURRED_AT - 8_000,
            ),
            PracticeUnitSeedRecord(
                practiceUnitId = SECOND_UNIT_ID,
                problemId = PROBLEM_ID,
                problemRevisionId = REVISION_ID,
                unitKey = "whole-2",
                unitKind = "WHOLE",
                title = "函数单调性（第二题）",
                promptMarkdown = "求另一个单调区间。",
                estimatedSeconds = 120,
                createdAtEpochMillis = OCCURRED_AT - 8_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-1",
                practiceUnitId = UNIT_ID,
                problemId = PROBLEM_ID,
                currentRevisionId = REVISION_ID,
                sourceKey = "source-1",
                acceptedAtEpochMillis = OCCURRED_AT - 7_000,
                updatedAtEpochMillis = OCCURRED_AT - 7_000,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = KNOWLEDGE_ID,
                stableCode = "math.function.monotonicity",
                subject = "MATH",
                displayName = "函数单调性",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "taxonomy-v1",
                createdAtEpochMillis = OCCURRED_AT - 6_000,
            ),
        ),
        knowledgeBindings = listOf(
            KnowledgeBindingSeedRecord(
                bindingId = BINDING_ID,
                practiceUnitId = UNIT_ID,
                knowledgeNodeId = KNOWLEDGE_ID,
                basisRevisionId = REVISION_ID,
                strength = 1.0,
                sourceType = "VERIFIED",
                taxonomyVersion = "taxonomy-v1",
                acceptedAtEpochMillis = OCCURRED_AT - 5_000,
            ),
            KnowledgeBindingSeedRecord(
                bindingId = "binding-2",
                practiceUnitId = SECOND_UNIT_ID,
                knowledgeNodeId = KNOWLEDGE_ID,
                basisRevisionId = REVISION_ID,
                strength = 1.0,
                sourceType = "VERIFIED",
                taxonomyVersion = "taxonomy-v1",
                acceptedAtEpochMillis = OCCURRED_AT - 5_000,
            ),
        ),
    )

    private fun studyDay(atEpochMillis: Long): StudyDayContext {
        val zoned = Instant.ofEpochMilli(atEpochMillis).atZone(ZoneId.of(ZONE))
        return StudyDayContext(
            epochDay = zoned.toLocalDate().toEpochDay(),
            timeZoneId = ZONE,
            utcOffsetMinutes = zoned.offset.totalSeconds / 60,
        )
    }

    private companion object {
        const val LEARNER = "learner-1"
        const val PROJECTION = "study-experience-v1"
        const val PROBLEM_ID = "problem-1"
        const val REVISION_ID = "revision-1"
        const val UNIT_ID = "unit-1"
        const val SECOND_UNIT_ID = "unit-2"
        const val KNOWLEDGE_ID = "knowledge-1"
        const val BINDING_ID = "binding-1"
        const val SNAPSHOT_ID = "evidence-snapshot-1"
        const val SECOND_SNAPSHOT_ID = "evidence-snapshot-2"
        const val SESSION_ID = "session-1"
        const val ZONE = "Asia/Shanghai"
        const val OCCURRED_AT = 1_728_000_000_000L
        const val LOCAL_DAY = 20_000L
    }
}
