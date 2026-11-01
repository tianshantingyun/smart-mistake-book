package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.*
import com.tingyun.smartmistakebook.core.database.entity.MistakeEditJobEntity
import com.tingyun.smartmistakebook.core.database.port.PersistedMistakeEditJob
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MistakeEditJobDao {
    @Query("UPDATE mistake_edit_job SET status = :status, output_snapshot = :output, figures_snapshot = :figures, message = :message, state_version = state_version + 1 WHERE operation_id = :id AND state_version = :version AND committed_revision_id IS NULL")
    suspend fun repairSnapshots(id: String, version: Long, status: String, output: String, figures: String, message: String): Int

    @Query("""SELECT c.source_asset_id AS assetId, c.content_sha256 AS contentSha256
        FROM figure_generation_attempt a JOIN figure_generation_operation o ON o.operation_id = a.operation_id
        JOIN canonical_source_asset c ON c.source_asset_id = a.output_asset_id
        WHERE o.operation_id = :operationId AND o.source_kind = 'PROBLEM_REVISION' AND o.source_id = :revisionId
        AND o.basis_fingerprint = :basis AND o.purpose = :purpose AND o.policy_version = :policy AND a.status = 'SUCCEEDED'
        ORDER BY a.attempt_ordinal DESC LIMIT 1""")
    suspend fun recoverFigure(operationId: String, revisionId: String, basis: String, purpose: String, policy: String): RecoveredEditFigure?
    @Query("UPDATE mistake_edit_job SET status = 'FAILED', output_snapshot = NULL, figures_snapshot = '[]', message = :message, state_version = state_version + 1 WHERE operation_id = :id AND state_version = :version AND committed_revision_id IS NULL")
    suspend fun quarantine(id: String, version: Long, message: String): Int
    @Query("SELECT * FROM mistake_edit_job WHERE entry_id = :entryId AND basis_revision_id = :revisionId AND draft_sha256 = :sha AND committed_revision_id IS NULL ORDER BY created_at_epoch_millis DESC, operation_id DESC LIMIT 1")
    suspend fun latest(entryId: String, revisionId: String, sha: String): MistakeEditJobEntity?
    @Query("SELECT question_document_snapshot FROM problem_revision WHERE revision_id = :revisionId")
    suspend fun revisionSnapshot(revisionId: String): String?

    @Query("INSERT INTO problem_revision_source_asset (problem_revision_id, source_asset_id, role, ordinal) VALUES (:revisionId, :assetId, :role, :ordinal)")
    suspend fun linkFigure(revisionId: String, assetId: String, role: String, ordinal: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MistakeEditJobEntity): Long

    @Update
    suspend fun update(entity: MistakeEditJobEntity): Int

    @Query("SELECT * FROM mistake_edit_job WHERE operation_id = :operationId")
    suspend fun read(operationId: String): MistakeEditJobEntity?

    @Query("SELECT * FROM mistake_edit_job WHERE operation_id = :operationId")
    fun observe(operationId: String): Flow<MistakeEditJobEntity?>

    @Query("SELECT * FROM mistake_edit_job WHERE status IN ('QUEUED','REQUESTING','GENERATING_FIGURES') AND (:after IS NULL OR operation_id > :after) ORDER BY operation_id LIMIT :limit")
    suspend fun pending(after: String?, limit: Int): List<MistakeEditJobEntity>

    @Query("SELECT COUNT(*) FROM error_book_entry WHERE entry_id = :entryId AND current_revision_id = :revisionId AND status = 'ACTIVE'")
    suspend fun currentBasis(entryId: String, revisionId: String): Int

    @Query("SELECT output_snapshot FROM model_task WHERE request_id = :requestId AND request_fingerprint = :fingerprint AND status = 'SUCCEEDED'")
    suspend fun completedOutput(requestId: String, fingerprint: String): String?

    @Query("""SELECT COUNT(*) FROM figure_generation_attempt a
        JOIN figure_generation_operation o ON o.operation_id = a.operation_id
        WHERE a.attempt_id = :attemptId AND o.operation_id = :operationId
        AND o.source_kind = 'PROBLEM_REVISION' AND o.source_id = :revisionId
        AND o.basis_fingerprint = :basis AND o.purpose = :purpose AND o.policy_version = :policy
        AND a.status = 'SUCCEEDED' AND (a.output_asset_id IS NULL OR a.output_asset_id = :assetId)""")
    suspend fun eligibleAttempt(attemptId: String, operationId: String, revisionId: String, basis: String, purpose: String, policy: String, assetId: String): Int

    @Query("""SELECT COUNT(*) FROM figure_generation_attempt a
        JOIN figure_generation_operation o ON o.operation_id = a.operation_id
        JOIN canonical_source_asset c ON c.source_asset_id = a.output_asset_id
        WHERE o.operation_id = :operationId AND o.source_kind = 'PROBLEM_REVISION'
        AND o.source_id = :revisionId AND o.basis_fingerprint = :basis
        AND o.purpose = :purpose AND o.policy_version = :policy AND a.status = 'SUCCEEDED'
        AND c.source_asset_id = :assetId AND c.content_sha256 = :sha""")
    suspend fun admittedFigure(operationId: String, revisionId: String, basis: String, purpose: String, policy: String, assetId: String, sha: String): Int

    @Query("UPDATE mistake_edit_job SET committed_revision_id = :revisionId, state_version = state_version + 1, updated_at_epoch_millis = :at WHERE operation_id = :operationId AND state_version = :version AND status = 'ADOPTED' AND committed_revision_id IS NULL")
    suspend fun consume(operationId: String, version: Long, revisionId: String, at: Long): Int
}

internal data class RecoveredEditFigure(val assetId: String, val contentSha256: String)

internal fun PersistedMistakeEditJob.toEntity(): MistakeEditJobEntity = MistakeEditJobEntity(
    operationId, input.basis.entryId, input.basis.revisionId, input.basis.draftSha256,
    ModelTaskLogicalOperationFingerprint.of(request), request.requestId, ModelTaskCodec.encodeRequest(request), ModelTaskFingerprint.of(request),
    generation, status.name, stateVersion, output?.let(ModelTaskCodec::encodeOutput), StoredFigureReferenceCodec.encode(figures),
    message, createdAtEpochMillis, updatedAtEpochMillis, committedRevisionId,
)

internal fun MistakeEditJobEntity.toRecord(): PersistedMistakeEditJob {
    val request = ModelTaskCodec.decodeRequest(requestSnapshot)
    require(request.requestId == requestId && ModelTaskFingerprint.of(request) == requestFingerprint)
    require(ModelTaskLogicalOperationFingerprint.of(request) == semanticFingerprint)
    val input = request.input as MistakeEditInput
    require(input.basis == EditBasis(entryId, basisRevisionId, draftSha256))
    return PersistedMistakeEditJob(operationId, request, generation, stateVersion, MistakeEditJobStatus.valueOf(status),
        outputSnapshot?.let { ModelTaskCodec.decodeOutput(it) as MistakeEditOutput }, StoredFigureReferenceCodec.decode(figuresSnapshot),
        message, createdAtEpochMillis, updatedAtEpochMillis, committedRevisionId)
}

internal suspend fun MistakeEditJobDao.requireOutputAuthority(job: PersistedMistakeEditJob) {
    job.output?.let { output ->
        require(completedOutput(job.request.requestId, ModelTaskFingerprint.of(job.request))?.let(ModelTaskCodec::decodeOutput) == output) {
            "Edit output does not match its completed model task"
        }
    }
}

internal suspend fun MistakeEditJobDao.requireFigureAuthority(job: PersistedMistakeEditJob) {
    requireOutputAuthority(job)
    job.figures.forEach { ref ->
        val output = requireNotNull(job.output)
        val change = output.suggestion.changes.filterIsInstance<MistakeEditChange.ReplaceFigure>().single { it.blockId == ref.blockId }
        require(admittedFigure(mistakeEditFigureOperationId(job.operationId, ref.blockId), job.input.basis.revisionId,
            mistakeEditFigureBasis(ModelTaskFingerprint.of(job.request), output, ref.blockId), requireNotNull(change.proposal.purpose).name,
            FigureGenerationPolicyVersion.VALUE, ref.assetId, ref.contentSha256) == 1) { "Figure lacks exact job provenance" }
    }
}
