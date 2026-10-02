package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.tingyun.smartmistakebook.core.database.AttemptAdvanceProofRecord
import com.tingyun.smartmistakebook.core.database.AttemptCorrectionRecord
import com.tingyun.smartmistakebook.core.database.AnswerRevealWriteCommand
import com.tingyun.smartmistakebook.core.database.AttemptIdempotencyConflictException
import com.tingyun.smartmistakebook.core.database.AttemptPersistenceRecord
import com.tingyun.smartmistakebook.core.database.AttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.ConsumedLedgerEventReceipt
import com.tingyun.smartmistakebook.core.database.DatabaseContractValidator
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.LearningLedgerIntegrityException
import com.tingyun.smartmistakebook.core.database.PersistedAttemptP0
import com.tingyun.smartmistakebook.core.database.PersistedAnswerRevealP0
import com.tingyun.smartmistakebook.core.database.PersistedCorrectionP0
import com.tingyun.smartmistakebook.core.database.PersistedLearnerSnapshot
import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.PersistedIncrementalLearningEvent
import com.tingyun.smartmistakebook.core.database.ProjectionArchiveRecord
import com.tingyun.smartmistakebook.core.database.ProjectionBatch
import com.tingyun.smartmistakebook.core.database.ProjectionBatchStopReason
import com.tingyun.smartmistakebook.core.database.ProjectionCasConflictException
import com.tingyun.smartmistakebook.core.database.ProjectionCommit
import com.tingyun.smartmistakebook.core.database.ProjectionCommitMode
import com.tingyun.smartmistakebook.core.database.ProjectionOutboxRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.model.AppliedAttemptRecord
import com.tingyun.smartmistakebook.core.model.AppliedAnswerRevealRecord
import com.tingyun.smartmistakebook.core.model.AppliedCorrectionRecord
import com.tingyun.smartmistakebook.core.model.AppliedTutorAnswerExposureRecord
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.PresentationProjectionState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import com.tingyun.smartmistakebook.core.database.entity.AppliedAttemptRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedAnswerRevealRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedCorrectionRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedTutorAnswerExposureRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AnswerRevealOutcomeEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentAnswerRevealEventEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEventEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceAttributionEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentItemSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentPresentationEntity
import com.tingyun.smartmistakebook.core.database.entity.AttemptCorrectionEntity
import com.tingyun.smartmistakebook.core.database.entity.AttemptEventEntity
import com.tingyun.smartmistakebook.core.database.entity.AttemptSubmissionEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorAnswerExposureOutcomeEntity
import com.tingyun.smartmistakebook.core.database.entity.IndependentCorrectObservationEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerKnowledgeMasteryStateEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerProblemMemoryStateEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerProjectionSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.LearningSequenceEntity
import com.tingyun.smartmistakebook.core.database.entity.PracticeUnitKnowledgeBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionArchiveEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionOutboxEntity
import com.tingyun.smartmistakebook.core.database.entity.PresentationProjectionStateEntity
import kotlinx.coroutines.flow.Flow


@Dao
internal abstract class ProjectionTransactionDao {
    @Query(
        """
        SELECT * FROM learner_projection_snapshot
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findHeader(
        projectionName: String,
        learnerId: String,
    ): LearnerProjectionSnapshotEntity?

    @Query(
        """
        SELECT * FROM projection_outbox
        WHERE learner_id = :learnerId AND outbox_sequence > :afterSequence
        ORDER BY outbox_sequence ASC
        LIMIT :limit
        """,
    )
    protected abstract suspend fun findOutboxAfter(
        learnerId: String,
        afterSequence: Long,
        limit: Int,
    ): List<ProjectionOutboxEntity>

    @Query(
        """
        SELECT * FROM projection_outbox
        WHERE learner_id = :learnerId
          AND outbox_sequence > :afterSequence
          AND outbox_sequence <= :throughSequence
        ORDER BY outbox_sequence ASC
        """,
    )
    protected abstract suspend fun findOutboxRange(
        learnerId: String,
        afterSequence: Long,
        throughSequence: Long,
    ): List<ProjectionOutboxEntity>

    @Query("SELECT last_allocated_sequence FROM learning_sequence WHERE learner_id = :learnerId")
    protected abstract suspend fun lastAllocatedSequence(learnerId: String): Long?

    @Query("SELECT * FROM attempt_event WHERE attempt_id = :attemptId LIMIT 1")
    protected abstract suspend fun findAttempt(attemptId: String): AttemptEventEntity?

    @Query("SELECT * FROM attempt_correction WHERE correction_id = :correctionId LIMIT 1")
    protected abstract suspend fun findCorrection(correctionId: String): AttemptCorrectionEntity?

    @Query("SELECT * FROM answer_reveal_outcome WHERE outcome_id = :outcomeId LIMIT 1")
    protected abstract suspend fun findProjectionAnswerReveal(
        outcomeId: String,
    ): AnswerRevealOutcomeEntity?

    @Query("SELECT * FROM tutor_answer_exposure_outcome WHERE outcome_id = :outcomeId LIMIT 1")
    protected abstract suspend fun findProjectionTutorExposure(
        outcomeId: String,
    ): TutorAnswerExposureOutcomeEntity?

    @Query("SELECT * FROM learner_chat_evidence WHERE evidence_id = :evidenceId LIMIT 1")
    protected abstract suspend fun findChatEvidence(evidenceId: String): LearnerChatEvidenceEntity?

    @Query("SELECT * FROM assessment_evidence_snapshot WHERE snapshot_id = :snapshotId LIMIT 1")
    protected abstract suspend fun findEvidenceSnapshot(
        snapshotId: String,
    ): AssessmentEvidenceSnapshotEntity?

    @Query(
        """
        SELECT * FROM assessment_evidence_attribution
        WHERE snapshot_id = :snapshotId
        ORDER BY binding_id ASC
        """,
    )
    protected abstract suspend fun findAttributions(
        snapshotId: String,
    ): List<AssessmentEvidenceAttributionEntity>

    // S5（重放路径批量读）：下面这组 IN 批查询把账本逐行的 2-3 次取数压成每块每表一次。
    // 调用方（readLedgerChunk）保证列表非空且长度 ≤ LEDGER_READ_CHUNK_SIZE。

    @Query("SELECT * FROM attempt_event WHERE attempt_id IN (:attemptIds)")
    protected abstract suspend fun findAttemptsByIds(attemptIds: List<String>): List<AttemptEventEntity>

    @Query("SELECT * FROM attempt_correction WHERE correction_id IN (:correctionIds)")
    protected abstract suspend fun findCorrectionsByIds(
        correctionIds: List<String>,
    ): List<AttemptCorrectionEntity>

    @Query("SELECT * FROM answer_reveal_outcome WHERE outcome_id IN (:outcomeIds)")
    protected abstract suspend fun findAnswerRevealsByIds(
        outcomeIds: List<String>,
    ): List<AnswerRevealOutcomeEntity>

    @Query("SELECT * FROM tutor_answer_exposure_outcome WHERE outcome_id IN (:outcomeIds)")
    protected abstract suspend fun findTutorExposuresByIds(
        outcomeIds: List<String>,
    ): List<TutorAnswerExposureOutcomeEntity>

    @Query("SELECT * FROM learner_chat_evidence WHERE evidence_id IN (:evidenceIds)")
    protected abstract suspend fun findChatEvidencesByIds(
        evidenceIds: List<String>,
    ): List<LearnerChatEvidenceEntity>

    @Query("SELECT * FROM assessment_evidence_snapshot WHERE snapshot_id IN (:snapshotIds)")
    protected abstract suspend fun findEvidenceSnapshotsByIds(
        snapshotIds: List<String>,
    ): List<AssessmentEvidenceSnapshotEntity>

    @Query(
        """
        SELECT * FROM assessment_evidence_attribution
        WHERE snapshot_id IN (:snapshotIds)
        ORDER BY snapshot_id ASC, binding_id ASC
        """,
    )
    protected abstract suspend fun findAttributionsBySnapshotIds(
        snapshotIds: List<String>,
    ): List<AssessmentEvidenceAttributionEntity>

    @Query(
        """
        SELECT * FROM learner_problem_memory_state
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY practice_unit_id ASC
        """,
    )
    protected abstract suspend fun findMemoryStates(
        projectionName: String,
        learnerId: String,
    ): List<LearnerProblemMemoryStateEntity>

    @Query(
        """
        SELECT * FROM learner_knowledge_mastery_state
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY knowledge_node_id ASC
        """,
    )
    protected abstract suspend fun findMasteryStates(
        projectionName: String,
        learnerId: String,
    ): List<LearnerKnowledgeMasteryStateEntity>

    @Query(
        """
        SELECT * FROM independent_correct_observation
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY knowledge_node_id ASC, ordinal ASC
        """,
    )
    protected abstract suspend fun findObservations(
        projectionName: String,
        learnerId: String,
    ): List<IndependentCorrectObservationEntity>

    @Query(
        """
        SELECT * FROM applied_attempt_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY event_sequence ASC
        """,
    )
    protected abstract suspend fun findAppliedAttempts(
        projectionName: String,
        learnerId: String,
    ): List<AppliedAttemptRecordEntity>

    @Query(
        """
        SELECT * FROM applied_correction_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY event_sequence ASC
        """,
    )
    protected abstract suspend fun findAppliedCorrections(
        projectionName: String,
        learnerId: String,
    ): List<AppliedCorrectionRecordEntity>

    @Query(
        """
        SELECT * FROM applied_answer_reveal_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY event_sequence ASC
        """,
    )
    protected abstract suspend fun findAppliedAnswerReveals(
        projectionName: String,
        learnerId: String,
    ): List<AppliedAnswerRevealRecordEntity>

    @Query(
        """
        SELECT * FROM applied_tutor_answer_exposure_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY event_sequence ASC
        """,
    )
    protected abstract suspend fun findAppliedTutorAnswerExposures(
        projectionName: String,
        learnerId: String,
    ): List<AppliedTutorAnswerExposureRecordEntity>

    @Query(
        """
        SELECT * FROM presentation_projection_state
        WHERE projection_name = :projectionName AND learner_id = :learnerId
        ORDER BY presentation_id ASC
        """,
    )
    protected abstract suspend fun findPresentationProjectionStates(
        projectionName: String,
        learnerId: String,
    ): List<PresentationProjectionStateEntity>

    @Query(
        """
        SELECT * FROM presentation_projection_state
        WHERE projection_name = :projectionName
          AND learner_id = :learnerId
          AND presentation_id IN (:presentationIds)
        ORDER BY presentation_id ASC
        """,
    )
    protected abstract suspend fun findPresentationProjectionStatesForIds(
        projectionName: String,
        learnerId: String,
        presentationIds: List<String>,
    ): List<PresentationProjectionStateEntity>

    @Query(
        """
        SELECT * FROM presentation_projection_state
        WHERE projection_name = :projectionName
          AND learner_id = :learnerId
          AND presentation_id = :presentationId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findPresentationProjectionState(
        projectionName: String,
        learnerId: String,
        presentationId: String,
    ): PresentationProjectionStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertHeader(header: LearnerProjectionSnapshotEntity)

    @Query(
        """
        UPDATE learner_projection_snapshot
        SET state_version = :newStateVersion,
            checkpoint_sequence = :checkpointSequence,
            known_ledger_head_sequence = :knownLedgerHeadSequence,
            projector_version = :projectorVersion,
            projected_at_epoch_millis = :projectedAtEpochMillis,
            generated_at_epoch_millis = :generatedAtEpochMillis,
            correction_watermark_epoch_millis = :correctionWatermarkEpochMillis,
            freshness = :freshness,
            projection_status = :projectionStatus
        WHERE projection_name = :projectionName
          AND learner_id = :learnerId
          AND checkpoint_sequence = :expectedCheckpoint
          AND state_version = :expectedStateVersion
        """,
    )
    protected abstract suspend fun compareAndSetHeader(
        projectionName: String,
        learnerId: String,
        expectedCheckpoint: Long,
        expectedStateVersion: Long,
        newStateVersion: Long,
        checkpointSequence: Long,
        knownLedgerHeadSequence: Long,
        projectorVersion: String,
        projectedAtEpochMillis: Long,
        generatedAtEpochMillis: Long,
        correctionWatermarkEpochMillis: Long?,
        freshness: String,
        projectionStatus: String,
    ): Int

    // ---- S1（W4-2 投影热路径）：按主键差集删/改，取代"每批全删全插" ----
    //
    // 旧的 `deleteX(projectionName, learnerId)`（整表清空）已删除：提交路径现在只对
    // **消失的主键**发删除、只对**变更的行**发 upsert（`applyProjectionTables`）。

    @Query(
        """
        DELETE FROM learner_problem_memory_state
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND practice_unit_id IN (:practiceUnitIds)
        """,
    )
    protected abstract suspend fun deleteMemoryStatesByIds(
        projectionName: String,
        learnerId: String,
        practiceUnitIds: List<String>,
    )

    @Query(
        """
        DELETE FROM independent_correct_observation
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND knowledge_node_id = :knowledgeNodeId AND ordinal = :ordinal
        """,
    )
    protected abstract suspend fun deleteObservation(
        projectionName: String,
        learnerId: String,
        knowledgeNodeId: String,
        ordinal: Int,
    )

    @Query(
        """
        DELETE FROM learner_knowledge_mastery_state
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND knowledge_node_id IN (:knowledgeNodeIds)
        """,
    )
    protected abstract suspend fun deleteMasteryStatesByIds(
        projectionName: String,
        learnerId: String,
        knowledgeNodeIds: List<String>,
    )

    @Query(
        """
        DELETE FROM applied_attempt_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND attempt_id IN (:attemptIds)
        """,
    )
    protected abstract suspend fun deleteAppliedAttemptsByIds(
        projectionName: String,
        learnerId: String,
        attemptIds: List<String>,
    )

    @Query(
        """
        DELETE FROM applied_correction_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND correction_id IN (:correctionIds)
        """,
    )
    protected abstract suspend fun deleteAppliedCorrectionsByIds(
        projectionName: String,
        learnerId: String,
        correctionIds: List<String>,
    )

    @Query(
        """
        DELETE FROM applied_answer_reveal_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND outcome_id IN (:outcomeIds)
        """,
    )
    protected abstract suspend fun deleteAppliedAnswerRevealsByIds(
        projectionName: String,
        learnerId: String,
        outcomeIds: List<String>,
    )

    @Query(
        """
        DELETE FROM applied_tutor_answer_exposure_record
        WHERE projection_name = :projectionName AND learner_id = :learnerId
          AND outcome_id IN (:outcomeIds)
        """,
    )
    protected abstract suspend fun deleteAppliedTutorAnswerExposuresByIds(
        projectionName: String,
        learnerId: String,
        outcomeIds: List<String>,
    )

    /**
     * `@Upsert` 而非 REPLACE/全删全插：REPLACE 先删后插，而这几张表以 RESTRICT 引用不可变
     * 账本行（attempt/correction/reveal/exposure）与知识节点——删了会被外键直接拒绝；
     * **@Upsert 只写变更行**，未变行一字不动（S1 的核心收益）。
     */
    @Upsert
    protected abstract suspend fun upsertMemoryStates(states: List<LearnerProblemMemoryStateEntity>)

    @Upsert
    protected abstract suspend fun upsertMasteryStates(states: List<LearnerKnowledgeMasteryStateEntity>)

    /** 观察表增量 append：新序号的行走 upsert 插入，旧序号行原样保留（差集删除另发）。 */
    @Upsert
    protected abstract suspend fun upsertObservations(
        observations: List<IndependentCorrectObservationEntity>,
    )

    @Upsert
    protected abstract suspend fun upsertAppliedAttempts(records: List<AppliedAttemptRecordEntity>)

    @Upsert
    protected abstract suspend fun upsertAppliedCorrections(records: List<AppliedCorrectionRecordEntity>)

    @Upsert
    protected abstract suspend fun upsertAppliedAnswerReveals(
        records: List<AppliedAnswerRevealRecordEntity>,
    )

    @Upsert
    protected abstract suspend fun upsertAppliedTutorAnswerExposures(
        records: List<AppliedTutorAnswerExposureRecordEntity>,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPresentationProjectionState(
        state: PresentationProjectionStateEntity,
    )

    @Query(
        """
        UPDATE presentation_projection_state
        SET terminal_outcome_id = :terminalOutcomeId,
            terminal_outcome = :terminalOutcome,
            terminal_event_sequence = :terminalEventSequence,
            memory_projected = :memoryProjected,
            memory_projection_sequence = :memoryProjectionSequence,
            last_response_ordinal = :lastResponseOrdinal,
            state_version = :nextStateVersion
        WHERE projection_name = :projectionName
          AND learner_id = :learnerId
          AND presentation_id = :presentationId
          AND state_version = :expectedStateVersion
        """,
    )
    protected abstract suspend fun compareAndSetPresentationProjectionState(
        projectionName: String,
        learnerId: String,
        presentationId: String,
        expectedStateVersion: Long,
        terminalOutcomeId: String?,
        terminalOutcome: String?,
        terminalEventSequence: Long?,
        memoryProjected: Boolean,
        memoryProjectionSequence: Long?,
        lastResponseOrdinal: Int,
        nextStateVersion: Long,
    ): Int

    @Transaction
    open suspend fun loadProjectionBatch(
        projectionName: String,
        learnerId: String,
        limit: Int,
    ): ProjectionBatch {
        DatabaseContractValidator.validateProjectionRequest(projectionName, learnerId, limit)
        val checkpoint = findHeader(projectionName, learnerId)?.checkpointSequence ?: 0L
        val ledgerHead = lastAllocatedSequence(learnerId) ?: 0L
        val rows = findOutboxAfter(learnerId, checkpoint, limit + 1)
        val events = mutableListOf<PersistedIncrementalLearningEvent>()
        var expected = checkpoint + 1
        for (row in rows) {
            if (row.outboxSequence != expected) {
                return batchStop(
                    projectionName,
                    learnerId,
                    checkpoint,
                    ledgerHead,
                    events,
                    ProjectionBatchStopReason.GAP,
                    expected,
                    "Expected sequence $expected but found ${row.outboxSequence}",
                )
            }
            if (row.eventKind == EVENT_KIND_CORRECTION) {
                return batchStop(
                    projectionName,
                    learnerId,
                    checkpoint,
                    ledgerHead,
                    events,
                    ProjectionBatchStopReason.FULL_REPLAY_REQUIRED,
                    expected,
                    "Correction ${row.eventId} requires a full ledger replay",
                )
            }
            val persisted = when (row.eventKind) {
                EVENT_KIND_ATTEMPT -> readAttempt(row)?.let { attempt ->
                    PersistedIncrementalLearningEvent(
                        event = attempt.attempt,
                        canonicalFingerprint = attempt.canonicalFingerprint,
                        outbox = attempt.outbox,
                    )
                }
                EVENT_KIND_ANSWER_REVEAL -> readAnswerReveal(row)?.let { reveal ->
                    PersistedIncrementalLearningEvent(
                        event = reveal.outcome,
                        canonicalFingerprint = reveal.canonicalFingerprint,
                        outbox = reveal.outbox,
                    )
                }
                EVENT_KIND_TUTOR_ANSWER_EXPOSURE -> readTutorAnswerExposure(row)?.let { outcome ->
                    PersistedIncrementalLearningEvent(
                        event = outcome,
                        canonicalFingerprint = row.canonicalFingerprint,
                        outbox = row.toRecord(),
                    )
                }
                EVENT_KIND_CHAT_EVIDENCE -> readChatEvidence(row)?.let { outcome ->
                    PersistedIncrementalLearningEvent(
                        event = outcome,
                        canonicalFingerprint = row.canonicalFingerprint,
                        outbox = row.toRecord(),
                    )
                }
                else -> null
            } ?: return batchStop(
                projectionName,
                learnerId,
                checkpoint,
                ledgerHead,
                events,
                ProjectionBatchStopReason.CONFLICT,
                expected,
                "Ledger event ${row.eventId} is missing or differs from its canonical outbox",
            )
            if (events.size == limit) {
                return batchStop(
                    projectionName,
                    learnerId,
                    checkpoint,
                    ledgerHead,
                    events,
                    ProjectionBatchStopReason.LIMIT_REACHED,
                    expected,
                    null,
                )
            }
            events += persisted
            expected++
        }
        val consumedThrough = expected - 1
        return if (ledgerHead > consumedThrough) {
            batchStop(
                projectionName,
                learnerId,
                checkpoint,
                ledgerHead,
                events,
                ProjectionBatchStopReason.GAP,
                expected,
                "Sequence $expected was allocated but has no immutable ledger event",
            )
        } else {
            batchStop(
                projectionName,
                learnerId,
                checkpoint,
                ledgerHead,
                events,
                ProjectionBatchStopReason.END_OF_LEDGER,
                null,
                null,
            )
        }
    }

    /**
     * S5（重放路径批量读）：账本全量读不再是"一个事务里逐行 2-3 次查询"。
     *
     * - **批量**：每块把 outbox 行以及五类载荷表、证据快照、归因（1:N）按 `IN (:ids)` 各取一次，
     *   行与载荷在内存里按键拼接（等价于 outbox↔payload 的联表读），查询数从 O(行数) 降到
     *   O(块数)；
     * - **事务外分块**：块循环（[readLedgerChunk] 的调用）在事务之外，每块一个短事务——
     *   5 万行的账本不再把整本塞进一个长事务，内存峰值与锁持有时间都降到一块；
     * - **语义不变（含并发追写）**：完整性判定用的 `last_allocated_sequence` 与它要覆盖的那一块行
     *   读自**同一个事务快照**（[readLedgerChunk] 内读，见那里的注释）——旧实现是"整本行 + 分配头
     *   同一事务"，本实现是"最后一块行 + 分配头同一事务"，两者在并发写入者追加时的判定相同：
     *   要么追写行与前进的分配头一起可见（进前缀/继续读），要么都不可见（前缀停在旧头，交由提交侧
     *   的 STALE 路径处理），不会把"块读之后提交的追写"误报成"已分配但没有账本行"的假 GAP；
     * - 每行的身份三连（learner/sequence/指纹列）与载荷 SHA-256 校验与旧实现逐字相同，首个坏行的
     *   GAP/CONFLICT 判定原样保留。
     */
    open suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead {
        DatabaseContractValidator.validateLedgerRequest(learnerId)
        val prefix = mutableListOf<PersistedLearningLedgerEvent>()
        var expected = 1L
        var allocated = 0L
        while (true) {
            val chunk = readLedgerChunk(
                learnerId = learnerId,
                afterSequence = expected - 1,
                limit = LEDGER_READ_CHUNK_SIZE,
            )
            // 分配头取自与 chunk.rows 同一个事务快照：尾部判定不能用事务外的单读（那会把并发
            // 追写误报成 GAP，见本函数口径注释）。
            allocated = chunk.allocatedSequence
            for (resolved in chunk.rows) {
                val row = resolved.outbox
                if (row.outboxSequence != expected) {
                    return LearningLedgerRead(
                        learnerId = learnerId,
                        validPrefix = prefix,
                        status = LearningLedgerReadStatus.GAP,
                        blockedAtSequence = expected,
                        detail = "Expected sequence $expected but found ${row.outboxSequence}",
                    )
                }
                val event = resolved.event
                if (event == null) {
                    return LearningLedgerRead(
                        learnerId = learnerId,
                        validPrefix = prefix,
                        status = LearningLedgerReadStatus.CONFLICT,
                        blockedAtSequence = expected,
                        detail = "Ledger event ${row.eventId} is missing or conflicts with its outbox",
                    )
                }
                prefix += PersistedLearningLedgerEvent(event, row.canonicalFingerprint)
                expected++
            }
            if (chunk.rows.size < LEDGER_READ_CHUNK_SIZE) break
        }
        return if (allocated == expected - 1) {
            LearningLedgerRead(learnerId, prefix, LearningLedgerReadStatus.COMPLETE)
        } else {
            LearningLedgerRead(
                learnerId = learnerId,
                validPrefix = prefix,
                status = LearningLedgerReadStatus.GAP,
                blockedAtSequence = expected,
                detail = "Sequence $expected was allocated but has no immutable ledger event",
            )
        }
    }

    /**
     * 一个读块的短事务：取一块 outbox 行，并按表批量取回它们的载荷
     * （见 [loadLearningLedger] 的口径注释）。载荷身份/指纹校验与单行读共用同一组
     * [resolveXxx] 核心函数，判定逐位相同。
     *
     * **分配头与行必须同一快照**：`last_allocated_sequence` 在这里读、与 [findOutboxAfter] 同属一个
     * `@Transaction`，所以"分配头前进"与"该行已在本块可见"要么同时成立、要么同时不成立。
     * 第一版实现把尾部头读放在块循环之外单读（无事务），并发写入者（如 chat 证据写入，
     * 不与 study 仓库共享互斥）在"最后一块读之后、尾部头读之前"提交时，会得到
     * `allocated > expected - 1` 而该行其实存在——假 GAP 会把 `StudyProjectionDrainer` 打成
     * 完整性异常。同一快照内读消除该中间态：真洞（分配头覆盖的序列确实无行）仍会被同一判定捕获。
     */
    @Transaction
    protected open suspend fun readLedgerChunk(
        learnerId: String,
        afterSequence: Long,
        limit: Int,
    ): LedgerChunk {
        val rows = findOutboxAfter(learnerId, afterSequence, limit)
        val allocated = lastAllocatedSequence(learnerId) ?: 0L
        if (rows.isEmpty()) return LedgerChunk(rows = emptyList(), allocatedSequence = allocated)
        val attemptIds = rows.filter { it.eventKind == EVENT_KIND_ATTEMPT }.map { it.eventId }
        val correctionIds = rows.filter { it.eventKind == EVENT_KIND_CORRECTION }.map { it.eventId }
        val revealIds = rows.filter { it.eventKind == EVENT_KIND_ANSWER_REVEAL }.map { it.eventId }
        val exposureIds = rows.filter { it.eventKind == EVENT_KIND_TUTOR_ANSWER_EXPOSURE }.map { it.eventId }
        val chatIds = rows.filter { it.eventKind == EVENT_KIND_CHAT_EVIDENCE }.map { it.eventId }
        val attempts = if (attemptIds.isEmpty()) {
            emptyMap()
        } else {
            findAttemptsByIds(attemptIds).associateBy(AttemptEventEntity::attemptId)
        }
        val corrections = if (correctionIds.isEmpty()) {
            emptyMap()
        } else {
            findCorrectionsByIds(correctionIds).associateBy(AttemptCorrectionEntity::correctionId)
        }
        val reveals = if (revealIds.isEmpty()) {
            emptyMap()
        } else {
            findAnswerRevealsByIds(revealIds).associateBy(AnswerRevealOutcomeEntity::outcomeId)
        }
        val exposures = if (exposureIds.isEmpty()) {
            emptyMap()
        } else {
            findTutorExposuresByIds(exposureIds).associateBy(TutorAnswerExposureOutcomeEntity::outcomeId)
        }
        val chatEvidences = if (chatIds.isEmpty()) {
            emptyMap()
        } else {
            findChatEvidencesByIds(chatIds).associateBy(LearnerChatEvidenceEntity::evidence_id)
        }
        val snapshotIds = (
            attempts.values.map(AttemptEventEntity::assessmentSnapshotId) +
                reveals.values.map(AnswerRevealOutcomeEntity::assessmentSnapshotId)
            )
            .distinct()
        val snapshots = if (snapshotIds.isEmpty()) {
            emptyMap()
        } else {
            findEvidenceSnapshotsByIds(snapshotIds)
                .associateBy(AssessmentEvidenceSnapshotEntity::snapshotId)
        }
        val attributions = if (snapshotIds.isEmpty()) {
            emptyMap()
        } else {
            findAttributionsBySnapshotIds(snapshotIds)
                .groupBy(AssessmentEvidenceAttributionEntity::snapshotId)
        }
        val resolvedRows = rows.map { row ->
            val event = when (row.eventKind) {
                EVENT_KIND_ATTEMPT -> {
                    val payload = attempts[row.eventId]
                    resolveAttempt(
                        row = row,
                        entity = payload,
                        snapshot = payload?.let { snapshots[it.assessmentSnapshotId] },
                        attributions = payload?.let { attributions[it.assessmentSnapshotId] }.orEmpty(),
                    )
                }

                EVENT_KIND_CORRECTION ->
                    resolveCorrection(row, corrections[row.eventId])

                EVENT_KIND_ANSWER_REVEAL -> {
                    val payload = reveals[row.eventId]
                    resolveAnswerReveal(
                        row = row,
                        entity = payload,
                        snapshot = payload?.let { snapshots[it.assessmentSnapshotId] },
                        attributions = payload?.let { attributions[it.assessmentSnapshotId] }.orEmpty(),
                    )
                }

                EVENT_KIND_TUTOR_ANSWER_EXPOSURE ->
                    resolveTutorAnswerExposure(row, exposures[row.eventId])

                EVENT_KIND_CHAT_EVIDENCE -> resolveChatEvidence(row, chatEvidences[row.eventId])

                else -> null
            }
            LedgerChunkRow(outbox = row, event = event)
        }
        return LedgerChunk(rows = resolvedRows, allocatedSequence = allocated)
    }

    @Transaction
    open suspend fun readCurrentSnapshot(
        projectionName: String,
        learnerId: String,
    ): PersistedLearnerSnapshot? {
        DatabaseContractValidator.validateProjectionRequest(projectionName, learnerId, 1)
        val header = findHeader(projectionName, learnerId) ?: return null
        return header.toPersistedSnapshot(
            memoryStates = findMemoryStates(projectionName, learnerId),
            masteryStates = findMasteryStates(projectionName, learnerId),
            observations = findObservations(projectionName, learnerId),
            appliedAttempts = findAppliedAttempts(projectionName, learnerId),
            appliedCorrections = findAppliedCorrections(projectionName, learnerId),
            appliedAnswerReveals = findAppliedAnswerReveals(projectionName, learnerId),
            appliedTutorAnswerExposures = findAppliedTutorAnswerExposures(projectionName, learnerId),
        )
    }

    @Transaction
    open suspend fun commitProjection(commit: ProjectionCommit): PersistedLearnerSnapshot {
        DatabaseContractValidator.validateProjectionCommit(commit)
        val existing = findHeader(commit.projectionName, commit.learnerId)
        if (existing == null) {
            if (commit.expectedPreviousCheckpoint != 0L || commit.expectedPreviousStateVersion != 0L) {
                throw ProjectionCasConflictException("Projection snapshot does not exist at the expected state")
            }
        } else if (existing.checkpointSequence != commit.expectedPreviousCheckpoint ||
            existing.stateVersion != commit.expectedPreviousStateVersion
        ) {
            throw ProjectionCasConflictException("Projection checkpoint/state version CAS failed")
        }
        val actualLedgerHead = lastAllocatedSequence(commit.learnerId) ?: 0L
        if (commit.knownLedgerHeadSequence > actualLedgerHead ||
            (existing != null && commit.knownLedgerHeadSequence < existing.knownLedgerHeadSequence)
        ) {
            throw ProjectionCasConflictException("Known ledger head is stale or ahead of storage")
        }
        if (commit.snapshot.projectionStatus == ProjectionStatus.CURRENT &&
            commit.snapshot.checkpoint.lastSequence != actualLedgerHead
        ) {
            throw ProjectionCasConflictException("A CURRENT snapshot must include the current ledger head")
        }

        val targetCheckpoint = commit.snapshot.checkpoint.lastSequence
        val rows = findOutboxRange(
            learnerId = commit.learnerId,
            afterSequence = commit.expectedPreviousCheckpoint,
            throughSequence = targetCheckpoint,
        )
        val expectedRows = commit.consumedLedgerEvents
        if (rows.size != expectedRows.size) {
            throw ProjectionCasConflictException("Committed ledger receipts do not cover the pending prefix")
        }
        // S8（重放管道）：本笔提交里每一行只解析/重算一次指纹——回执校验刚验证过的行，
        // 下面的 applied 窗口校验不再重复取数 + SHA（同一事务内这些行不可能变）。
        val validated = ValidatedLedgerEvents(
            attemptIds = commit.snapshot.appliedAttemptRecords.keys,
            correctionIds = commit.snapshot.appliedCorrectionRecords.keys,
            revealIds = commit.snapshot.appliedAnswerRevealRecords.keys,
            exposureIds = commit.snapshot.appliedTutorAnswerExposureRecords.keys,
        )
        rows.zip(expectedRows).forEach { (row, receipt) ->
            if (row.eventKind != receipt.eventKind ||
                row.eventId != receipt.eventId ||
                row.outboxSequence != receipt.eventSequence ||
                row.canonicalFingerprint != receipt.canonicalFingerprint ||
                !row.hasValidCanonicalEvent()
            ) {
                throw ProjectionCasConflictException(
                    "Ledger receipt differs from sequence ${receipt.eventSequence}",
                )
            }
            validated.record(row)
        }
        if (commit.mode == ProjectionCommitMode.INCREMENTAL &&
            rows.any {
                it.eventKind != EVENT_KIND_ATTEMPT && it.eventKind != EVENT_KIND_ANSWER_REVEAL
                    && it.eventKind != EVENT_KIND_TUTOR_ANSWER_EXPOSURE
                    && it.eventKind != EVENT_KIND_CHAT_EVIDENCE
            }
        ) {
            throw ProjectionCasConflictException("Incremental commit cannot cross a correction")
        }
        verifyAppliedEventWindows(commit.snapshot, commit.learnerId, validated)

        val storedSnapshot = if (actualLedgerHead == commit.snapshot.knownLedgerHeadSequence) {
            commit.snapshot
        } else {
            commit.snapshot.copy(
                knownLedgerHeadSequence = actualLedgerHead,
                freshness = LearnerSnapshotFreshness.STALE,
            )
        }
        val newStateVersion = commit.expectedPreviousStateVersion + 1
        val header = storedSnapshot.toEntity(
            projectionName = commit.projectionName,
            stateVersion = newStateVersion,
            knownLedgerHeadSequence = actualLedgerHead,
        )
        if (existing == null) {
            insertHeader(header)
        } else if (compareAndSetHeader(
                projectionName = commit.projectionName,
                learnerId = commit.learnerId,
                expectedCheckpoint = commit.expectedPreviousCheckpoint,
                expectedStateVersion = commit.expectedPreviousStateVersion,
                newStateVersion = newStateVersion,
                checkpointSequence = header.checkpointSequence,
                knownLedgerHeadSequence = header.knownLedgerHeadSequence,
                projectorVersion = header.projectorVersion,
                projectedAtEpochMillis = header.projectedAtEpochMillis,
                generatedAtEpochMillis = header.generatedAtEpochMillis,
                correctionWatermarkEpochMillis = header.correctionWatermarkEpochMillis,
                freshness = header.freshness,
                projectionStatus = header.projectionStatus,
            ) != 1
        ) {
            throw ProjectionCasConflictException("Projection checkpoint/state version CAS failed")
        }

        applyPresentationAuthorityTransitions(
            projectionName = commit.projectionName,
            learnerId = commit.learnerId,
            rows = rows,
        )
        verifyCommittedPresentationStates(commit, rows)

        applyProjectionTables(
            projectionName = commit.projectionName,
            learnerId = commit.learnerId,
            snapshot = storedSnapshot,
        )

        return PersistedLearnerSnapshot(
            projectionName = commit.projectionName,
            stateVersion = newStateVersion,
            knownLedgerHeadSequence = actualLedgerHead,
            snapshot = storedSnapshot,
        )
    }

    /**
     * S1（W4-2 投影热路径）：把 [snapshot] 的 7 张投影表写进库——**变更行 upsert、消失行按
     * 主键差集删除**，不再每批全删全插。
     *
     * 不变量：提交后各表内容与旧实现（全删 + 全插同一份快照）逐位一致——差集比较用的是
     * 行内容的完整相等（data class equality），未变行不动、变更行重写、消失行删除，
     * 三种情况合起来恰好等于"清空重写"的结果，但写放大只与**变更量**成正比。
     *
     * 顺序约束来自外键：
     * 1. 掌握态先于观察表（`independent_correct_observation` CASCADE 引用掌握态）；
     * 2. 消失的掌握态先删（其观察行随 CASCADE 消失，随后差集删除是空操作）；
     * 3. 已应用记录四表相互独立，各自差集。
     * 崩溃安全不变：整个提交仍在一个 `@Transaction` 里，提交点即事务边界。
     */
    private suspend fun applyProjectionTables(
        projectionName: String,
        learnerId: String,
        snapshot: LearnerSnapshot,
    ) {
        reconcileRows(
            existing = findMemoryStates(projectionName, learnerId),
            desired = snapshot.toMemoryEntities(projectionName),
            keyOf = LearnerProblemMemoryStateEntity::practiceUnitId,
            upsert = ::upsertMemoryStates,
        ) { ids -> deleteMemoryStatesByIds(projectionName, learnerId, ids) }

        val (masteryStates, observations) = snapshot.toMasteryEntities(projectionName)
        reconcileRows(
            existing = findMasteryStates(projectionName, learnerId),
            desired = masteryStates,
            keyOf = LearnerKnowledgeMasteryStateEntity::knowledgeNodeId,
            upsert = ::upsertMasteryStates,
        ) { ids -> deleteMasteryStatesByIds(projectionName, learnerId, ids) }

        // 观察表：按 (knowledge_node_id, ordinal) 复合主键差集。增量下序号只增不改（append），
        // 变更/消失只可能来自全量重放（修正事件让某题从正答变负答等）。
        val existingObservations = findObservations(projectionName, learnerId)
        val existingObservationByKey = existingObservations.associateBy {
            it.knowledgeNodeId to it.ordinal
        }
        val changedObservations = observations.filter { observation ->
            existingObservationByKey[observation.knowledgeNodeId to observation.ordinal] != observation
        }
        val desiredObservationKeys = observations.mapTo(hashSetOf()) { it.knowledgeNodeId to it.ordinal }
        if (changedObservations.isNotEmpty()) upsertObservations(changedObservations)
        existingObservations
            .filter { (it.knowledgeNodeId to it.ordinal) !in desiredObservationKeys }
            .forEach { deleteObservation(projectionName, learnerId, it.knowledgeNodeId, it.ordinal) }

        reconcileRows(
            existing = findAppliedAttempts(projectionName, learnerId),
            desired = snapshot.toAppliedEntities(projectionName),
            keyOf = AppliedAttemptRecordEntity::attemptId,
            upsert = ::upsertAppliedAttempts,
        ) { ids -> deleteAppliedAttemptsByIds(projectionName, learnerId, ids) }
        reconcileRows(
            existing = findAppliedCorrections(projectionName, learnerId),
            desired = snapshot.toAppliedCorrectionEntities(projectionName),
            keyOf = AppliedCorrectionRecordEntity::correctionId,
            upsert = ::upsertAppliedCorrections,
        ) { ids -> deleteAppliedCorrectionsByIds(projectionName, learnerId, ids) }
        reconcileRows(
            existing = findAppliedAnswerReveals(projectionName, learnerId),
            desired = snapshot.toAppliedAnswerRevealEntities(projectionName),
            keyOf = AppliedAnswerRevealRecordEntity::outcomeId,
            upsert = ::upsertAppliedAnswerReveals,
        ) { ids -> deleteAppliedAnswerRevealsByIds(projectionName, learnerId, ids) }
        reconcileRows(
            existing = findAppliedTutorAnswerExposures(projectionName, learnerId),
            desired = snapshot.toAppliedTutorAnswerExposureEntities(projectionName),
            keyOf = AppliedTutorAnswerExposureRecordEntity::outcomeId,
            upsert = ::upsertAppliedTutorAnswerExposures,
        ) { ids -> deleteAppliedTutorAnswerExposuresByIds(projectionName, learnerId, ids) }
    }

    /**
     * 一张投影表的差集写：变更行 upsert、消失行按主键分批删除（`IN (:ids)` 受 SQLite 参数
     * 上限约束，复用 [SQLITE_PRESENTATION_ID_BATCH_SIZE] 的分块口径）。未变行一个字节都不写。
     */
    private suspend fun <T, K> reconcileRows(
        existing: List<T>,
        desired: List<T>,
        keyOf: (T) -> K,
        upsert: suspend (List<T>) -> Unit,
        delete: suspend (List<K>) -> Unit,
    ) {
        val existingByKey = existing.associateBy(keyOf)
        val changed = desired.filter { existingByKey[keyOf(it)] != it }
        if (changed.isNotEmpty()) upsert(changed)
        val desiredKeys = desired.mapTo(hashSetOf(), keyOf)
        val removed = existing
            .map(keyOf)
            .filterNot(desiredKeys::contains)
        removed.chunked(SQLITE_PRESENTATION_ID_BATCH_SIZE).forEach { delete(it) }
    }

    private suspend fun applyPresentationAuthorityTransitions(
        projectionName: String,
        learnerId: String,
        rows: List<ProjectionOutboxEntity>,
    ) {
        rows.forEach { row ->
            val previous = when (row.eventKind) {
                EVENT_KIND_ATTEMPT -> {
                    val attempt = findAttempt(row.eventId)
                        ?: throw ProjectionCasConflictException(
                            "Attempt ${row.eventId} disappeared during authority projection",
                        )
                    transitionPresentationAuthorityForAttempt(
                        projectionName = projectionName,
                        learnerId = learnerId,
                        attempt = attempt,
                    )
                }
                EVENT_KIND_ANSWER_REVEAL -> {
                    val reveal = findProjectionAnswerReveal(row.eventId)
                        ?: throw ProjectionCasConflictException(
                            "Answer reveal ${row.eventId} disappeared during authority projection",
                        )
                    transitionPresentationAuthorityForReveal(
                        projectionName = projectionName,
                        learnerId = learnerId,
                        reveal = reveal,
                    )
                }
                EVENT_KIND_CORRECTION -> null
                EVENT_KIND_TUTOR_ANSWER_EXPOSURE -> null
                else -> throw ProjectionCasConflictException("Unknown presentation authority event kind")
            }
            previous?.let { persistPresentationProjectionState(it) }
        }
    }

    private suspend fun transitionPresentationAuthorityForAttempt(
        projectionName: String,
        learnerId: String,
        attempt: AttemptEventEntity,
    ): PresentationAuthorityTransition {
        if (attempt.learnerId != learnerId) {
            throw ProjectionCasConflictException("Attempt learner differs from authority learner")
        }
        val current = findPresentationProjectionState(
            projectionName = projectionName,
            learnerId = learnerId,
            presentationId = attempt.presentationId,
        )
        val expectedOrdinal = (current?.lastResponseOrdinal ?: 0) + 1
        if (attempt.responseOrdinal != expectedOrdinal) {
            throw ProjectionCasConflictException(
                "Presentation ${attempt.presentationId} expected response ordinal $expectedOrdinal " +
                    "but found ${attempt.responseOrdinal}",
            )
        }
        current?.terminalEventSequence?.let { terminalSequence ->
            if (terminalSequence >= attempt.eventSequence) {
                throw ProjectionCasConflictException(
                    "Presentation terminal sequence must precede its later response",
                )
            }
        }
        val next = PresentationProjectionStateEntity(
            projectionName = projectionName,
            learnerId = learnerId,
            presentationId = attempt.presentationId,
            terminalOutcomeId = current?.terminalOutcomeId,
            terminalOutcome = current?.terminalOutcome,
            terminalEventSequence = current?.terminalEventSequence,
            memoryProjected = true,
            memoryProjectionSequence = if (current?.terminalEventSequence == null) {
                attempt.eventSequence
            } else {
                current.memoryProjectionSequence
            },
            lastResponseOrdinal = attempt.responseOrdinal,
            stateVersion = (current?.stateVersion ?: 0) + 1,
        )
        return PresentationAuthorityTransition(current, next)
    }

    private suspend fun transitionPresentationAuthorityForReveal(
        projectionName: String,
        learnerId: String,
        reveal: AnswerRevealOutcomeEntity,
    ): PresentationAuthorityTransition {
        if (reveal.learnerId != learnerId) {
            throw ProjectionCasConflictException("Answer-reveal learner differs from authority learner")
        }
        val current = findPresentationProjectionState(
            projectionName = projectionName,
            learnerId = learnerId,
            presentationId = reveal.presentationId,
        )
        if (current?.terminalEventSequence != null) {
            throw ProjectionCasConflictException(
                "Presentation ${reveal.presentationId} already has a projected terminal outcome",
            )
        }
        val next = PresentationProjectionStateEntity(
            projectionName = projectionName,
            learnerId = learnerId,
            presentationId = reveal.presentationId,
            terminalOutcomeId = reveal.outcomeId,
            terminalOutcome = ProblemMemoryOutcome.ANSWER_REVEALED.name,
            terminalEventSequence = reveal.eventSequence,
            memoryProjected = true,
            memoryProjectionSequence = current?.memoryProjectionSequence ?: reveal.eventSequence,
            lastResponseOrdinal = current?.lastResponseOrdinal ?: 0,
            stateVersion = (current?.stateVersion ?: 0) + 1,
        )
        return PresentationAuthorityTransition(current, next)
    }

    private suspend fun persistPresentationProjectionState(
        transition: PresentationAuthorityTransition,
    ) {
        val previous = transition.previous
        val next = transition.next
        check(next.memoryProjected == (next.memoryProjectionSequence != null)) {
            "Presentation memory authority is internally inconsistent"
        }
        check(
            listOf(next.terminalOutcomeId, next.terminalOutcome, next.terminalEventSequence)
                .all { it == null } ||
                listOf(next.terminalOutcomeId, next.terminalOutcome, next.terminalEventSequence)
                    .all { it != null },
        ) { "Presentation terminal authority is internally inconsistent" }
        if (previous == null) {
            insertPresentationProjectionState(next)
            return
        }
        if (compareAndSetPresentationProjectionState(
                projectionName = next.projectionName,
                learnerId = next.learnerId,
                presentationId = next.presentationId,
                expectedStateVersion = previous.stateVersion,
                terminalOutcomeId = next.terminalOutcomeId,
                terminalOutcome = next.terminalOutcome,
                terminalEventSequence = next.terminalEventSequence,
                memoryProjected = next.memoryProjected,
                memoryProjectionSequence = next.memoryProjectionSequence,
                lastResponseOrdinal = next.lastResponseOrdinal,
                nextStateVersion = next.stateVersion,
            ) != 1
        ) {
            throw ProjectionCasConflictException(
                "Presentation ${next.presentationId} authority state-version CAS failed",
            )
        }
    }

    private suspend fun verifyCommittedPresentationStates(
        commit: ProjectionCommit,
        rows: List<ProjectionOutboxEntity>,
    ) {
        val expected = when (commit.mode) {
            ProjectionCommitMode.INCREMENTAL -> {
                val presentationIds = rows.mapNotNullTo(linkedSetOf()) { row ->
                    when (row.eventKind) {
                        EVENT_KIND_ATTEMPT -> findAttempt(row.eventId)?.presentationId
                        EVENT_KIND_ANSWER_REVEAL ->
                            findProjectionAnswerReveal(row.eventId)?.presentationId
                        EVENT_KIND_CORRECTION -> null
                        EVENT_KIND_TUTOR_ANSWER_EXPOSURE -> null
                        EVENT_KIND_CHAT_EVIDENCE -> null
                        else -> null
                    }
                }
                if (presentationIds.isEmpty()) {
                    emptyMap()
                } else {
                    findPresentationProjectionStatesInBatches(
                        projectionName = commit.projectionName,
                        learnerId = commit.learnerId,
                        presentationIds = presentationIds.toList(),
                    ).associate { state ->
                        state.presentationId to state.toModel(commit.snapshot.checkpoint.lastSequence)
                    }
                }
            }

            ProjectionCommitMode.FULL_REPLAY -> findPresentationProjectionStates(
                projectionName = commit.projectionName,
                learnerId = commit.learnerId,
            ).associate { state ->
                state.presentationId to state.toModel(commit.snapshot.checkpoint.lastSequence)
            }
        }
        if (commit.presentationProjectionStates != expected) {
            throw ProjectionCasConflictException(
                "Projected presentation authority differs from the immutable ledger",
            )
        }
    }

    /**
     * S8：本笔提交中已经按账本行校验过的事件 id（按类型分开——同一串 id 可以是不同类型
     * 的两行，不能互相顶替）。
     *
     * 只收集 [verifyAppliedEventWindows] 会再次问到的 id（applied 窗口 ≤4096/类），
     * 内存与窗口同阶。校验语义不变：每个 id 第一次仍走完整的身份三连 + 载荷 SHA-256
     * 重算；这里只是让同一事务内的第二次问询复用第一次的结论。
     */
    private class ValidatedLedgerEvents(
        private val attemptIds: Set<String>,
        private val correctionIds: Set<String>,
        private val revealIds: Set<String>,
        private val exposureIds: Set<String>,
    ) {
        private val attempts = linkedSetOf<String>()
        private val corrections = linkedSetOf<String>()
        private val reveals = linkedSetOf<String>()
        private val exposures = linkedSetOf<String>()

        fun record(row: ProjectionOutboxEntity) {
            when (row.eventKind) {
                EVENT_KIND_ATTEMPT -> if (row.eventId in attemptIds) attempts += row.eventId
                EVENT_KIND_CORRECTION -> if (row.eventId in correctionIds) corrections += row.eventId
                EVENT_KIND_ANSWER_REVEAL -> if (row.eventId in revealIds) reveals += row.eventId
                EVENT_KIND_TUTOR_ANSWER_EXPOSURE ->
                    if (row.eventId in exposureIds) exposures += row.eventId
            }
        }

        fun attemptValidated(eventId: String): Boolean = eventId in attempts
        fun correctionValidated(eventId: String): Boolean = eventId in corrections
        fun revealValidated(eventId: String): Boolean = eventId in reveals
        fun exposureValidated(eventId: String): Boolean = eventId in exposures
    }

    private suspend fun verifyAppliedEventWindows(
        snapshot: LearnerSnapshot,
        learnerId: String,
        validated: ValidatedLedgerEvents,
    ) {
        snapshot.appliedAttemptRecords.values.forEach { applied ->
            val attempt = findAttempt(applied.attemptId)
                ?: throw ProjectionCasConflictException(
                    "Applied attempt ${applied.attemptId} is not in the immutable ledger",
                )
            if (attempt.learnerId != learnerId ||
                attempt.eventSequence != applied.eventSequence ||
                attempt.canonicalFingerprint != applied.canonicalFingerprint ||
                (!validated.attemptValidated(applied.attemptId) && readAttempt(attempt.toOutbox()) == null)
            ) {
                throw ProjectionCasConflictException(
                    "Applied attempt ${applied.attemptId} differs from the immutable ledger",
                )
            }
        }
        snapshot.appliedCorrectionRecords.values.forEach { applied ->
            val correction = findCorrection(applied.correctionId)
                ?: throw ProjectionCasConflictException(
                    "Applied correction ${applied.correctionId} is not in the immutable ledger",
                )
            if (correction.learnerId != learnerId ||
                correction.attemptId != applied.attemptId ||
                correction.eventSequence != applied.eventSequence ||
                correction.canonicalFingerprint != applied.canonicalFingerprint ||
                (
                    !validated.correctionValidated(applied.correctionId) &&
                        readCorrection(correction.toOutbox()) == null
                    )
            ) {
                throw ProjectionCasConflictException(
                    "Applied correction ${applied.correctionId} differs from the immutable ledger",
                )
            }
        }
        snapshot.appliedAnswerRevealRecords.values.forEach { applied ->
            val reveal = findProjectionAnswerReveal(applied.outcomeId)
                ?: throw ProjectionCasConflictException(
                    "Applied answer reveal ${applied.outcomeId} is not in the immutable ledger",
                )
            if (reveal.learnerId != learnerId ||
                reveal.presentationId != applied.presentationId ||
                reveal.eventSequence != applied.eventSequence ||
                reveal.canonicalFingerprint != applied.canonicalFingerprint ||
                (!validated.revealValidated(applied.outcomeId) && readAnswerReveal(reveal.toOutbox()) == null)
            ) {
                throw ProjectionCasConflictException(
                    "Applied answer reveal ${applied.outcomeId} differs from the immutable ledger",
                )
            }
        }
        snapshot.appliedTutorAnswerExposureRecords.values.forEach { applied ->
            val exposure = findProjectionTutorExposure(applied.outcomeId)
                ?: throw ProjectionCasConflictException(
                    "Applied tutor answer exposure ${applied.outcomeId} is not in the immutable ledger",
                )
            if (exposure.learnerId != learnerId ||
                exposure.exposureId != applied.exposureId ||
                exposure.eventSequence != applied.eventSequence ||
                exposure.canonicalFingerprint != applied.canonicalFingerprint ||
                (
                    !validated.exposureValidated(applied.outcomeId) &&
                        readTutorAnswerExposure(exposure.toOutbox()) == null
                    )
            ) {
                throw ProjectionCasConflictException(
                    "Applied tutor answer exposure ${applied.outcomeId} differs from the immutable ledger",
                )
            }
        }
    }

    private suspend fun ProjectionOutboxEntity.hasValidCanonicalEvent(): Boolean = when (eventKind) {
        EVENT_KIND_ATTEMPT -> readAttempt(this) != null
        EVENT_KIND_ANSWER_REVEAL -> readAnswerReveal(this) != null
        EVENT_KIND_TUTOR_ANSWER_EXPOSURE -> readTutorAnswerExposure(this) != null
        EVENT_KIND_CORRECTION -> readCorrection(this) != null
        EVENT_KIND_CHAT_EVIDENCE -> readChatEvidence(this) != null
        else -> false
    }

    /**
     * 账本行的身份三连：学习者、序列、指纹列必须与 outbox 行逐位一致——这是"载荷行就是这一行
     * 账本"的锚点。批量读（[readLedgerChunk]）与单行读共用，判定逐字相同。
     */
    private fun AttemptEventEntity.matchesOutbox(row: ProjectionOutboxEntity): Boolean =
        learnerId == row.learnerId &&
            eventSequence == row.outboxSequence &&
            canonicalFingerprint == row.canonicalFingerprint

    private fun AttemptCorrectionEntity.matchesOutbox(row: ProjectionOutboxEntity): Boolean =
        learnerId == row.learnerId &&
            eventSequence == row.outboxSequence &&
            canonicalFingerprint == row.canonicalFingerprint

    private fun AnswerRevealOutcomeEntity.matchesOutbox(row: ProjectionOutboxEntity): Boolean =
        learnerId == row.learnerId &&
            eventSequence == row.outboxSequence &&
            canonicalFingerprint == row.canonicalFingerprint

    private fun TutorAnswerExposureOutcomeEntity.matchesOutbox(row: ProjectionOutboxEntity): Boolean =
        learnerId == row.learnerId &&
            eventSequence == row.outboxSequence &&
            canonicalFingerprint == row.canonicalFingerprint

    /**
     * 账本行 → [Attempt]（不含取数）：身份三连 + 载荷反序列化 + 载荷 SHA-256 重算。
     * 任一环节不符返回 null（调用方按 CONFLICT 处理）。批量读与单行读共用本函数，
     * 保证 [readLedgerChunk] 与 [readAttempt] 判定逐位一致。
     */
    private fun resolveAttempt(
        row: ProjectionOutboxEntity,
        entity: AttemptEventEntity?,
        snapshot: AssessmentEvidenceSnapshotEntity?,
        attributions: List<AssessmentEvidenceAttributionEntity>,
    ): Attempt? {
        if (entity == null || !entity.matchesOutbox(row)) return null
        if (snapshot == null) return null
        val attempt = runCatching {
            entity.toModel(snapshot.toModel(attributions))
        }.getOrNull() ?: return null
        if (LearningLedgerFingerprint.attempt(attempt) != row.canonicalFingerprint) return null
        return attempt
    }

    private fun resolveCorrection(
        row: ProjectionOutboxEntity,
        entity: AttemptCorrectionEntity?,
    ): AttemptCorrection? {
        if (entity == null || !entity.matchesOutbox(row)) return null
        val correction = runCatching(entity::toModel).getOrNull() ?: return null
        if (LearningLedgerFingerprint.correction(correction) != row.canonicalFingerprint) return null
        return correction
    }

    private fun resolveAnswerReveal(
        row: ProjectionOutboxEntity,
        entity: AnswerRevealOutcomeEntity?,
        snapshot: AssessmentEvidenceSnapshotEntity?,
        attributions: List<AssessmentEvidenceAttributionEntity>,
    ): AnswerRevealOutcome? {
        if (entity == null || !entity.matchesOutbox(row)) return null
        if (snapshot == null) return null
        val outcome = runCatching {
            entity.toModel(snapshot.toModel(attributions))
        }.getOrNull() ?: return null
        if (LearningLedgerFingerprint.answerReveal(outcome) != row.canonicalFingerprint) return null
        return outcome
    }

    private fun resolveTutorAnswerExposure(
        row: ProjectionOutboxEntity,
        entity: TutorAnswerExposureOutcomeEntity?,
    ): TutorAnswerExposureOutcome? {
        if (entity == null || !entity.matchesOutbox(row)) return null
        val outcome = runCatching(entity::toModel).getOrNull() ?: return null
        if (LearningLedgerFingerprint.tutorAnswerExposure(outcome) != row.canonicalFingerprint) return null
        return outcome
    }

    /**
     * The evidence row carries the payload; the outbox row carries identity,
     * sequence and fingerprint. The model event's sequence is the outbox
     * sequence, so the recomputed fingerprint pins the pair together.
     */
    private fun resolveChatEvidence(
        row: ProjectionOutboxEntity,
        entity: LearnerChatEvidenceEntity?,
    ): ChatEvidenceSubmitted? {
        if (entity == null || entity.learner_id != row.learnerId) return null
        val outcome = runCatching { entity.toChatEvidenceModel(row.outboxSequence) }.getOrNull() ?: return null
        if (LearningLedgerFingerprint.chatEvidence(outcome) != row.canonicalFingerprint) return null
        return outcome
    }

    private suspend fun readAttempt(row: ProjectionOutboxEntity): PersistedAttemptP0? {
        val entity = findAttempt(row.eventId) ?: return null
        if (!entity.matchesOutbox(row)) return null
        val snapshot = findEvidenceSnapshot(entity.assessmentSnapshotId) ?: return null
        val attempt = resolveAttempt(
            row = row,
            entity = entity,
            snapshot = snapshot,
            attributions = findAttributions(snapshot.snapshotId),
        ) ?: return null
        return PersistedAttemptP0(
            learnerId = entity.learnerId,
            submissionId = entity.submissionId,
            attempt = attempt,
            canonicalFingerprint = row.canonicalFingerprint,
            outbox = row.toRecord(),
        )
    }

    private suspend fun readCorrection(row: ProjectionOutboxEntity): PersistedCorrectionP0? {
        val entity = findCorrection(row.eventId) ?: return null
        val correction = resolveCorrection(row, entity) ?: return null
        return PersistedCorrectionP0(
            learnerId = entity.learnerId,
            submissionId = entity.submissionId,
            correction = correction,
            canonicalFingerprint = row.canonicalFingerprint,
            outbox = row.toRecord(),
        )
    }

    private suspend fun readAnswerReveal(row: ProjectionOutboxEntity): PersistedAnswerRevealP0? {
        val entity = findProjectionAnswerReveal(row.eventId) ?: return null
        if (!entity.matchesOutbox(row)) return null
        val snapshot = findEvidenceSnapshot(entity.assessmentSnapshotId) ?: return null
        val outcome = resolveAnswerReveal(
            row = row,
            entity = entity,
            snapshot = snapshot,
            attributions = findAttributions(snapshot.snapshotId),
        ) ?: return null
        return PersistedAnswerRevealP0(
            learnerId = entity.learnerId,
            assessmentEventId = entity.assessmentEventId,
            outcome = outcome,
            canonicalFingerprint = row.canonicalFingerprint,
            outbox = row.toRecord(),
        )
    }

    private suspend fun readTutorAnswerExposure(
        row: ProjectionOutboxEntity,
    ): TutorAnswerExposureOutcome? = resolveTutorAnswerExposure(
        row = row,
        entity = findProjectionTutorExposure(row.eventId),
    )

    private suspend fun readChatEvidence(row: ProjectionOutboxEntity): ChatEvidenceSubmitted? =
        resolveChatEvidence(row, findChatEvidence(row.eventId))

    private suspend fun batchStop(
        projectionName: String,
        learnerId: String,
        checkpoint: Long,
        ledgerHead: Long,
        events: List<PersistedIncrementalLearningEvent>,
        reason: ProjectionBatchStopReason,
        blockedAt: Long?,
        detail: String?,
    ): ProjectionBatch {
        val presentationIds = events.mapNotNullTo(linkedSetOf()) { persisted ->
            when (val event = persisted.event) {
                is Attempt -> event.presentationId
                is AnswerRevealOutcome -> event.presentationId
                is TutorAnswerExposureOutcome, is ChatEvidenceSubmitted -> null
            }
        }
        val persistedStates = if (presentationIds.isEmpty()) {
            emptyMap()
        } else {
            findPresentationProjectionStatesInBatches(
                projectionName = projectionName,
                learnerId = learnerId,
                presentationIds = presentationIds.toList(),
            ).associateBy(PresentationProjectionStateEntity::presentationId)
        }
        val authoritativeStates = presentationIds.associateWith { presentationId ->
            persistedStates[presentationId]?.toModel(checkpoint)
                ?: PresentationProjectionState(
                    presentationId = presentationId,
                    asOfLedgerSequence = checkpoint,
                    memoryProjectionApplied = false,
                )
        }
        return ProjectionBatch(
            projectionName = projectionName,
            learnerId = learnerId,
            previousCheckpoint = checkpoint,
            ledgerHeadSequence = ledgerHead,
            events = events,
            authoritativePresentationStates = authoritativeStates,
            stopReason = reason,
            blockedAtSequence = blockedAt,
            detail = detail,
        )
    }

    private suspend fun findPresentationProjectionStatesInBatches(
        projectionName: String,
        learnerId: String,
        presentationIds: List<String>,
    ): List<PresentationProjectionStateEntity> = presentationIds
        .chunked(SQLITE_PRESENTATION_ID_BATCH_SIZE)
        .flatMap { batch ->
            findPresentationProjectionStatesForIds(
                projectionName = projectionName,
                learnerId = learnerId,
                presentationIds = batch,
            )
        }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertArchive(rows: List<ProjectionArchiveEntity>)

    @Query(
        """
        SELECT `sql` FROM sqlite_master
        WHERE type = 'table' AND name IN (:tableNames)
        ORDER BY name ASC
        """,
    )
    protected abstract suspend fun findTableDdl(tableNames: List<String>): List<String>

    /**
     * 归档一份投影（内核修复路线图 W0-1/Q2 ③）：把**即将被重放覆盖**的那份存储投影整份留下。
     *
     * 写入时机是 `StudyProjectionDrainer.commitFullReplay` 里、调用 `LearningProjector.replay`
     * **之前**——顺序是这条机制的全部价值：重放会原地覆盖 9 张投影表，归档晚一步就只剩新值。
     *
     * `schema_ddl` 在同一个事务里从 `sqlite_master` 现读，不由调用方传：手抄的 DDL 迟早与真表漂开，
     * 而回退流程要靠它判断"这份 JSON 能不能原样写回现在的表"。
     */
    @Transaction
    open suspend fun archiveProjectionSnapshot(record: ProjectionArchiveRecord) {
        DatabaseContractValidator.validateProjectionArchive(record)
        insertArchive(
            listOf(
                ProjectionArchiveEntity(
                    projectionName = record.projectionName,
                    learnerId = record.learnerId,
                    archivedAtEpochMillis = record.archivedAtEpochMillis,
                    snapshotJson = record.snapshotJson,
                    projectorVersion = record.projectorVersion,
                    schemaDdl = findTableDdl(PROJECTION_ARCHIVE_TABLES).joinToString("\n\n"),
                ),
            ),
        )
    }
}

/**
 * S5：账本批量读的一块。`event == null` 表示该行的载荷缺失或与 outbox 行冲突——
 * 与单行读返回 null 同一判定，由 [ProjectionTransactionDao.loadLearningLedger] 映射为
 * CONFLICT 状态。
 */
internal data class LedgerChunkRow(
    val outbox: ProjectionOutboxEntity,
    val event: LearningLedgerEvent?,
)

/**
 * S5：一个读块（一个短事务的产物）。[allocatedSequence] 与 [rows] 读自**同一事务快照**——
 * `loadLearningLedger` 的完整性判定必须用同一快照的头，否则并发追写会被误报成 GAP
 * （见 `ProjectionTransactionDao.readLedgerChunk` 的口径注释）。
 */
internal data class LedgerChunk(
    val rows: List<LedgerChunkRow>,
    val allocatedSequence: Long,
)

/**
 * 归档要连同 DDL 一起记下的表 = 一份 [LearnerSnapshot] 的全部载体
 * （与 `readCurrentSnapshot`/`commitProjection` 读写的 9 张表逐一对应：头部 1 张 + 状态 3 张 +
 * 已应用记录 4 张 + 呈现态 1 张）。回退工具要按这份清单判断"当时的表结构还兼不兼容"，
 * 所以清单短一张，回退时就多一处盲区。
 */
internal val PROJECTION_ARCHIVE_TABLES: List<String> = listOf(
    "applied_answer_reveal_record",
    "applied_attempt_record",
    "applied_correction_record",
    "applied_tutor_answer_exposure_record",
    "independent_correct_observation",
    "learner_knowledge_mastery_state",
    "learner_problem_memory_state",
    "learner_projection_snapshot",
    "presentation_projection_state",
)

