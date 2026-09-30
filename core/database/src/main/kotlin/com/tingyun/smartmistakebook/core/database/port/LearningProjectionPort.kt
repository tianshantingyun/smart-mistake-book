package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.AnswerRevealWriteCommand
import com.tingyun.smartmistakebook.core.database.AnswerRevealWriteResult
import com.tingyun.smartmistakebook.core.database.AssessmentEventSeedRecord
import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.AttemptAdvanceProofRecord
import com.tingyun.smartmistakebook.core.database.AttemptCorrectionRecord
import com.tingyun.smartmistakebook.core.database.AttemptCorrectionResult
import com.tingyun.smartmistakebook.core.database.AttemptPersistenceRecord
import com.tingyun.smartmistakebook.core.database.AttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.ReviewLogEntry
import com.tingyun.smartmistakebook.core.database.ReviewLogSampleRecord
import com.tingyun.smartmistakebook.core.database.AttemptWriteResult
import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.PersistedAnswerRevealFact
import com.tingyun.smartmistakebook.core.database.PersistedAnswerRevealP0
import com.tingyun.smartmistakebook.core.database.PersistedAttemptP0
import com.tingyun.smartmistakebook.core.database.PersistedCorrectionP0
import com.tingyun.smartmistakebook.core.database.PersistedLearnerSnapshot
import com.tingyun.smartmistakebook.core.database.ProjectionArchiveRecord
import com.tingyun.smartmistakebook.core.database.ProjectionBatch
import com.tingyun.smartmistakebook.core.database.ProjectionCommit
import com.tingyun.smartmistakebook.core.database.ReviewAttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.ReviewAttemptWriteResult
import com.tingyun.smartmistakebook.core.database.SeedResult
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot

/**
 * Port for fixture seeding and assessment snapshot/event writes.
 */
interface SeedAssessmentPort {
    suspend fun seedFixture(bundle: StudySeedBundle): SeedResult

    suspend fun saveAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord)

    suspend fun saveAssessmentEvidenceSnapshot(snapshot: AssessmentEvidenceSnapshot)

    suspend fun appendAssessmentEvent(event: AssessmentEventSeedRecord)
}

/**
 * Port for attempt, answer-reveal, and correction writes plus deprecated P0 inspection.
 */
interface AttemptWritePort {
    suspend fun recordAttempt(command: AttemptWriteCommand): AttemptWriteResult

    suspend fun recordReviewAttempt(
        command: ReviewAttemptWriteCommand,
    ): ReviewAttemptWriteResult

    suspend fun recordAnswerReveal(command: AnswerRevealWriteCommand): AnswerRevealWriteResult

    suspend fun reconcileAnswerRevealOutcomes(
        learnerId: String,
        limit: Int = 100,
    ): List<AnswerRevealWriteResult>

    /**
     * 读该呈现上**已经发生**的答案揭示（W1-3/KF-02）：提交前的证据定价分支要据此判断
     * "本次作答之前答案是否已被揭示"。没有揭示返回 null。
     *
     * 刻意与 [recordAnswerReveal] 同置一处：揭示的读与写共享同一语义（揭示让呈现终止，
     * 其后的每次作答都算"看过答案"）；轻量事实、不走 P0 校验链（那是审计读回的语义）。
     */
    suspend fun findAnswerRevealForPresentation(
        learnerId: String,
        presentationId: String,
    ): PersistedAnswerRevealFact?

    suspend fun appendAttemptCorrection(correction: AttemptCorrectionRecord): AttemptCorrectionResult

    /**
     * Raw collected review evidence (spec mastery-scheduling 3.1). Insert is
     * idempotent per (learner, source id); collection is decoupled from
     * scheduling and never blocks the ledger path on failure.
     */
    suspend fun recordReviewLogEntries(entries: List<ReviewLogEntry>)

    suspend fun readReviewLogSamples(learnerId: String, limit: Int): List<ReviewLogSampleRecord>

    /** Most recent review-log timestamp for one card and evidence kind. */
    suspend fun readLastReviewLogAt(
        learnerId: String,
        practiceUnitId: String,
        sourceKind: String,
    ): Long?

    suspend fun findAttemptPersistence(submissionId: String): AttemptPersistenceRecord?

    suspend fun findAttemptAdvanceProof(attemptId: String): AttemptAdvanceProofRecord? = null

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readAssessmentSnapshotP0(
        assessmentItemSnapshotId: String,
    ): AssessmentItemSnapshotSeedRecord?

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readAttemptP0(attemptId: String): PersistedAttemptP0?

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readCorrectionP0(correctionId: String): PersistedCorrectionP0?

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readAnswerRevealP0(outcomeId: String): PersistedAnswerRevealP0?
}

/**
 * Port for learning projection and ledger reads/writes.
 */
interface LearningProjectionPort {
    suspend fun loadProjectionBatch(
        projectionName: String,
        learnerId: String,
        limit: Int,
    ): ProjectionBatch

    suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead

    suspend fun readCurrentLearnerSnapshot(
        projectionName: String,
        learnerId: String,
    ): PersistedLearnerSnapshot?

    suspend fun commitProjection(commit: ProjectionCommit): PersistedLearnerSnapshot

    /**
     * 归档一份即将被重放覆盖的投影（内核修复路线图 W0-1 ③）。
     *
     * 调用点在 `StudyProjectionDrainer.commitFullReplay` 里、`LearningProjector.replay` **之前**：
     * 顺序本身就是机制——重放原地覆盖投影表，晚一步归档就只剩新值。
     */
    suspend fun archiveProjectionSnapshot(record: ProjectionArchiveRecord)
}