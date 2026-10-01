package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * W4-3/S8：全量重放经 `StudyProjectionDrainer` 的读（loadLearningLedger）→ 算（replay）→
 * 提交三段后，投影输出与指纹值必须与"投影器自行重算指纹"的旧口径**逐位一致**。
 *
 * 两条断言各钉一半：
 * 1. 等价——drainer 排空后的快照等于 `LearningProjector().replay(ledger)`（重算路径）的快照；
 * 2. 转发——读边界给的指纹值原样落进 applied 记录（真 DAO 的提交侧
 *    `verifyAppliedEventWindows` 仍会与账本行比对，所以"信任"不是"免检"；本 fake 只验证转发）。
 */
class ReplayFingerprintDrainerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test
    fun `drainer full replay matches projector recomputation bit for bit`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val events = mixedLedger()
        database.projectionLedger = events.map {
            PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
        }
        database.publishDisplacedProjection(previousVersionSnapshot())
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
        )

        val drained = requireNotNull(drainer.drain())

        assertEquals(
            "重放管道（含指纹复用）的输出必须与投影器自行重算逐位相同",
            LearningProjector().replay(LEARNER_ID, events).snapshot,
            drained.snapshot,
        )
        drained.snapshot.appliedAttemptRecords.values.forEach { record ->
            assertEquals(
                LearningLedgerFingerprint.attempt(
                    events.single { it.ledgerEventId == record.attemptId } as Attempt,
                ),
                record.canonicalFingerprint,
            )
        }
    }

    @Test
    fun `drainer forwards the read-boundary fingerprint instead of recomputing`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val attempt = attempt(sequence = 1)
        val readBoundaryValue = "read-boundary-fingerprint"
        database.projectionLedger = listOf(PersistedLearningLedgerEvent(attempt, readBoundaryValue))
        database.publishDisplacedProjection(previousVersionSnapshot())
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
        )

        val drained = requireNotNull(drainer.drain())

        val record = drained.snapshot.appliedAttemptRecords.getValue(attempt.attemptId)
        assertEquals(readBoundaryValue, record.canonicalFingerprint)
        assertNotEquals(
            "重算会得到另一个值——这条断言证明 drainer 确实在转发读边界的值",
            LearningLedgerFingerprint.attempt(attempt),
            record.canonicalFingerprint,
        )
    }

    private fun previousVersionSnapshot() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = mapOf(
            "unit-previous-projector-version" to ProblemMemoryState(
                practiceUnitId = "unit-previous-projector-version",
                stabilityDays = 2.0,
                difficulty = 9.0,
                lastReviewedAtEpochMillis = 10L * 86_400_000L,
                nextReviewAtEpochMillis = 12L * 86_400_000L,
                lastAttemptId = "attempt:previous-version",
                projectorVersion = PREVIOUS_VERSION,
                checkpointSequence = 1,
            ),
        ),
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = PREVIOUS_VERSION,
            projectedAtEpochMillis = 10L * 86_400_000L,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = 10L * 86_400_000L,
    )

    /** 五类事件各至少一条、序列 1..N 连续的全量重放账本。 */
    private fun mixedLedger(): List<LearningLedgerEvent> {
        val correctedAttempt = attempt(sequence = 5)
        return listOf(
            attempt(sequence = 1),
            answerReveal(sequence = 2),
            chatEvidence(sequence = 3),
            tutorExposure(sequence = 4),
            correctedAttempt,
            correction(sequence = 6, attempt = correctedAttempt),
        )
    }

    private fun attempt(sequence: Long): Attempt = Attempt(
        attemptId = "attempt-$sequence",
        presentationId = "presentation-$sequence",
        responseOrdinal = 1,
        assessmentSnapshot = snapshot("attempt-$sequence", "unit-${sequence % 4}"),
        evidence = LearningEvidence(
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
        ),
        problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        occurredAtEpochMillis = occurredAt(sequence),
        durationSeconds = 45,
        studyDay = studyDay(sequence),
        eventSequence = sequence,
    )

    private fun answerReveal(sequence: Long): AnswerRevealOutcome = AnswerRevealOutcome(
        outcomeId = "reveal-$sequence",
        presentationId = "presentation-reveal-$sequence",
        assessmentSnapshot = snapshot("reveal-$sequence", "unit-${sequence % 4}"),
        occurredAtEpochMillis = occurredAt(sequence),
        studyDay = studyDay(sequence),
        eventSequence = sequence,
    )

    private fun chatEvidence(sequence: Long): ChatEvidenceSubmitted = ChatEvidenceSubmitted(
        evidenceId = "chat-$sequence",
        conversationId = "conversation-replay",
        knowledgeNodeId = "kc-${sequence % 3}",
        direction = LearningEvidenceDirection.POSITIVE,
        weight = ChatEvidenceSubmitted.POSITIVE_WEIGHT,
        reasonMarkdown = "模型判断。",
        confidence = 0.9,
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun tutorExposure(sequence: Long): TutorAnswerExposureOutcome = TutorAnswerExposureOutcome(
        outcomeId = "exposure-$sequence",
        exposureId = "exposure-fact-$sequence",
        sessionId = "session-$sequence",
        questionDocumentId = "question-document-$sequence",
        questionRevisionNumber = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        problemRevisionId = "revision-1",
        practiceUnitId = "unit-${sequence % 4}",
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun correction(sequence: Long, attempt: Attempt): AttemptCorrection = AttemptCorrection(
        correctionId = "correction-$sequence",
        attemptId = attempt.attemptId,
        replacementEvidence = LearningEvidence(
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_INCORRECT,
        ),
        replacementMemoryOutcome = ProblemMemoryOutcome.RETRIEVAL_FAILURE,
        reasonMarkdown = "修正为独立答错。",
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun snapshot(id: String, practiceUnitId: String): AssessmentEvidenceSnapshot =
        AssessmentEvidenceSnapshot(
            snapshotId = "snapshot-$id",
            assessmentItemId = "assessment-$id",
            practiceUnitId = practiceUnitId,
            problemRevisionId = "revision-1",
            answerSpecId = "answer-1",
            itemFamilyId = "family-$id",
            sourceBundleId = "bundle-replay",
            taxonomyVersion = "taxonomy-v1",
            verification = AssessmentSnapshotVerification.VERIFIED,
            calibration = CalibrationSnapshot(
                support = CalibrationSupport.SUPPORTED,
                sourceId = "calibration-source",
                version = "calibration-v1",
                validFromEpochMillis = 0,
                validUntilEpochMillis = Long.MAX_VALUE,
            ),
            attributions = listOf(
                KnowledgeEvidenceAttribution(
                    bindingId = "binding-$id",
                    knowledgeNodeId = "kc-${id.length % 3}",
                    weight = 1.0,
                    basisRevisionId = "revision-1",
                    taxonomyVersion = "taxonomy-v1",
                    role = EvidenceAttributionRole.PRIMARY,
                    certainty = EvidenceAttributionCertainty.DIRECT,
                ),
            ),
            capturedAtEpochMillis = 0,
        )

    private fun occurredAt(sequence: Long): Long = BASE_EPOCH_MILLIS + sequence * 60_000L

    private fun studyDay(sequence: Long): StudyDayContext = StudyDayContext(
        epochDay = 20_000L + sequence / 1_440L,
        timeZoneId = "Asia/Shanghai",
        utcOffsetMinutes = 480,
    )

    private companion object {
        const val LEARNER_ID = "learner:replay-drainer"
        const val PREVIOUS_VERSION = "learning-core-v6(projector-v6,evidence-v4)"
        const val BASE_EPOCH_MILLIS = 1_767_225_600_000L
    }
}
