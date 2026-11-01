package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.*
import com.tingyun.smartmistakebook.core.database.StudyDatabase
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.entity.DetailedSolutionEntity
import com.tingyun.smartmistakebook.core.database.entity.DetailedSolutionManifestEntity
import com.tingyun.smartmistakebook.core.database.port.*
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
internal interface DetailedSolutionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertManifest(entity: DetailedSolutionManifestEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSolution(entity: DetailedSolutionEntity): Long
    @Update suspend fun updateSolution(entity: DetailedSolutionEntity): Int
    @Query("SELECT * FROM detailed_solution_manifest WHERE manifest_id = :id") suspend fun manifest(id: String): DetailedSolutionManifestEntity?
    @Query("SELECT * FROM detailed_solution_manifest WHERE manifest_id = :id") fun observeManifest(id: String): Flow<DetailedSolutionManifestEntity?>
    @Query("SELECT * FROM detailed_solution WHERE solution_id = :id") suspend fun solution(id: String): DetailedSolutionEntity?
    @Query("SELECT * FROM detailed_solution WHERE solution_id IN (:ids)") fun observeSolutions(ids: List<String>): Flow<List<DetailedSolutionEntity>>
    @Query("UPDATE detailed_solution_manifest SET status = :status, state_version = state_version + 1, updated_at_epoch_millis = :at WHERE manifest_id = :id AND state_version = :version")
    suspend fun updateManifest(id: String, version: Long, status: String, at: Long): Int
    @Query("SELECT COUNT(*) FROM problem_revision r JOIN error_book_entry e ON e.problem_id = r.problem_id WHERE r.revision_id = :revisionId AND e.entry_id = :entryId")
    suspend fun revisionBelongs(entryId: String, revisionId: String): Int
    @Query("""SELECT COUNT(*) FROM canonical_source_asset c JOIN problem_revision_source_asset l ON l.source_asset_id = c.source_asset_id
        WHERE l.problem_revision_id = :revisionId AND c.source_asset_id = :assetId AND c.content_sha256 = :sha
        AND c.byte_size = :bytes AND c.width = :width AND c.height = :height""")
    suspend fun sourceMatches(revisionId: String, assetId: String, sha: String, bytes: Long, width: Int, height: Int): Int
    @Query("SELECT COUNT(*) FROM problem_revision_source_asset WHERE problem_revision_id = :revisionId AND source_asset_id = :assetId AND role = :role")
    suspend fun figureRoleMatches(revisionId: String, assetId: String, role: String): Int
}

internal fun PersistedAnswerManifest.toEntity() = DetailedSolutionManifestEntity(manifestId, manifest.paperDraftId,
    DetailedSolutionCodec.encodeManifest(manifest), manifestSha256, manifest.inputSha256, status.name, stateVersion, createdAtEpochMillis, updatedAtEpochMillis)

internal fun DetailedSolutionManifestEntity.toRecord(): PersistedAnswerManifest {
    val manifest = DetailedSolutionCodec.decodeManifest(manifestSnapshot)
    require(manifest.paperDraftId == paperDraftId && manifest.inputSha256 == inputSha256 && DetailedSolutionCodec.manifestSha256(manifest) == manifestSha256)
    return PersistedAnswerManifest(manifestId, manifest, stateVersion, DetailedSolutionPreparationStatus.valueOf(status), createdAtEpochMillis, updatedAtEpochMillis)
}

internal fun PersistedDetailedSolution.toEntity() = DetailedSolutionEntity(solutionId, basis.revisionId, basis.questionSha256,
    DETAILED_SOLUTION_PROTOCOL_VERSION, request.requestId, ModelTaskCodec.encodeRequest(request), ModelTaskFingerprint.of(request),
    output?.let(ModelTaskCodec::encodeOutput), document?.let(DetailedSolutionCodec::encodeDocument), document?.let(DetailedSolutionCodec::documentSha256),
    StoredFigureReferenceCodec.encode(figures), document?.origin?.name, document?.verification?.name, generation, status.name, stateVersion, message, createdAtEpochMillis, updatedAtEpochMillis)

internal fun DetailedSolutionEntity.toRecord(): PersistedDetailedSolution {
    val request = ModelTaskCodec.decodeRequest(requestSnapshot)
    require(request.requestId == requestId && ModelTaskFingerprint.of(request) == requestFingerprint && protocolVersion == DETAILED_SOLUTION_PROTOCOL_VERSION)
    val record = PersistedDetailedSolution(solutionId, request, generation, stateVersion, DetailedSolutionPreparationStatus.valueOf(status),
        outputSnapshot?.let { ModelTaskCodec.decodeOutput(it) as DetailedSolutionOutput }, StoredFigureReferenceCodec.decode(figuresSnapshot), message, createdAtEpochMillis, updatedAtEpochMillis)
    require(record.basis == SolutionBasis(revisionId, questionSha256))
    require(record.document?.let(DetailedSolutionCodec::encodeDocument) == documentSnapshot && record.document?.let(DetailedSolutionCodec::documentSha256) == documentSha256)
    require(record.document?.origin?.name == origin && record.document?.verification?.name == verification)
    return record
}

/** Transaction ownership stays here; a UI/preparation worker never supplies unproven asset links. */
internal class RoomDetailedSolutionStore(
    private val database: StudyDatabase,
    private val registerAsset: suspend (CanonicalSourceAssetRecord) -> Unit,
) : DetailedSolutionPort {
    override suspend fun freezeAnswerManifest(record: PersistedAnswerManifest): PersistedAnswerManifest = database.withWriteTransaction {
        val dao = database.detailedSolutionDao()
        dao.manifest(record.manifestId)?.toRecord()?.let { existing ->
            require(existing.manifest == record.manifest)
            return@withWriteTransaction existing
        }
        require(record.stateVersion == 0L && record.status == DetailedSolutionPreparationStatus.QUEUED)
        record.manifest.questions.forEach { requireQuestionSources(it) }
        check(dao.insertManifest(record.toEntity()) != -1L)
        record
    }

    private suspend fun requireQuestionSources(question: FrozenExportQuestion) {
        val dao = database.detailedSolutionDao()
        require(dao.revisionBelongs(question.entryId, question.basis.revisionId) == 1) { "Frozen question revision is not part of this entry" }
        question.sourceImages.forEach { source -> require(dao.sourceMatches(question.basis.revisionId, source.assetId, source.sha256, source.byteSize, source.width, source.height) >= 1) { "Frozen source is not an admitted revision asset" } }
        require(question.figures.all { ref ->
            question.sourceImages.any { it.assetId == ref.assetId && it.sha256 == ref.contentSha256 } &&
                dao.figureRoleMatches(question.basis.revisionId, ref.assetId, ref.role.name) >= 1
        }) { "Frozen figure role does not match the revision asset" }
    }

    override suspend fun readAnswerManifest(manifestId: String) = database.detailedSolutionDao().manifest(manifestId)?.toRecord()
    override fun observeAnswerManifest(manifestId: String) = database.detailedSolutionDao().observeManifest(manifestId).map { it?.toRecord() }
    override suspend fun updateAnswerManifest(manifestId: String, expectedVersion: Long, status: DetailedSolutionPreparationStatus, atEpochMillis: Long): Boolean = database.withWriteTransaction {
        val current = readAnswerManifest(manifestId) ?: return@withWriteTransaction false
        if (current.stateVersion != expectedVersion || current.status == DetailedSolutionPreparationStatus.STALE) return@withWriteTransaction false
        require(atEpochMillis >= current.updatedAtEpochMillis)
        if (status == DetailedSolutionPreparationStatus.READY) current.manifest.questions.forEach { question ->
            val solution = requireNotNull(readDetailedSolution(detailedSolutionId(question.basis)))
            require(solution.status == DetailedSolutionPreparationStatus.READY)
            requireAuthority(solution)
        }
        database.detailedSolutionDao().updateManifest(manifestId, expectedVersion, status.name, atEpochMillis) == 1
    }

    override suspend fun createDetailedSolution(record: PersistedDetailedSolution): PersistedDetailedSolution = database.withWriteTransaction {
        database.detailedSolutionDao().solution(record.solutionId)?.toRecord()?.let { return@withWriteTransaction it }
        require(record.stateVersion == 0L && record.generation == 0 && record.status == DetailedSolutionPreparationStatus.QUEUED && record.output == null && record.figures.isEmpty())
        requireQuestionSources(record.input.question)
        check(database.detailedSolutionDao().insertSolution(record.toEntity()) != -1L)
        record
    }

    override suspend fun readDetailedSolution(solutionId: String) = database.detailedSolutionDao().solution(solutionId)?.toRecord()
    override fun observeDetailedSolutions(solutionIds: List<String>): Flow<List<PersistedDetailedSolution>> {
        require(solutionIds.size in 1..100)
        return database.detailedSolutionDao().observeSolutions(solutionIds).map { rows -> rows.map { it.toRecord() } }
    }

    override suspend fun updateDetailedSolution(record: PersistedDetailedSolution): Boolean = database.withWriteTransaction {
        val current = readDetailedSolution(record.solutionId) ?: return@withWriteTransaction false
        if (record.stateVersion != current.stateVersion + 1) return@withWriteTransaction false
        require(record.createdAtEpochMillis == current.createdAtEpochMillis && record.updatedAtEpochMillis >= current.updatedAtEpochMillis && record.basis == current.basis)
        if (record.generation == current.generation + 1) {
            require(record.figures.all { it in current.figures })
            require(current.status in setOf(DetailedSolutionPreparationStatus.FAILED, DetailedSolutionPreparationStatus.CANCELLED))
            require(record.status == DetailedSolutionPreparationStatus.QUEUED)
            require(ModelTaskLogicalOperationFingerprint.of(current.request) == ModelTaskLogicalOperationFingerprint.of(record.request))
            if (current.output != null) require(record.output == current.output && record.request == current.request) else require(record.output == null)
        } else {
            require(record.figures == current.figures)
            require(record.generation == current.generation && record.request == current.request)
            require(current.output == null || record.output == current.output)
            val allowed = when (current.status) {
                DetailedSolutionPreparationStatus.QUEUED -> setOf(DetailedSolutionPreparationStatus.RUNNING, DetailedSolutionPreparationStatus.CANCELLED, DetailedSolutionPreparationStatus.FAILED)
                DetailedSolutionPreparationStatus.RUNNING -> setOf(DetailedSolutionPreparationStatus.RUNNING, DetailedSolutionPreparationStatus.READY, DetailedSolutionPreparationStatus.CANCELLED, DetailedSolutionPreparationStatus.FAILED)
                DetailedSolutionPreparationStatus.READY -> setOf(DetailedSolutionPreparationStatus.STALE, DetailedSolutionPreparationStatus.FAILED)
                else -> emptySet()
            }
            require(record.status in allowed)
        }
        requireAuthority(record)
        check(database.detailedSolutionDao().updateSolution(record.toEntity()) == 1)
        true
    }

    private suspend fun requireAuthority(record: PersistedDetailedSolution) {
        val output = record.output ?: return
        val audit = database.mistakeEditJobDao()
        require(audit.completedOutput(record.request.requestId, ModelTaskFingerprint.of(record.request))?.let(ModelTaskCodec::decodeOutput) == output)
        record.figures.forEach { ref -> require(audit.admittedFigure(detailedSolutionFigureOperationId(record.solutionId, ref.blockId), record.basis.revisionId,
            detailedSolutionFigureBasis(record.request, output, ref.blockId), FigureGenerationPurpose.EXPLANATION.name,
            FigureGenerationPolicyVersion.VALUE, ref.assetId, ref.contentSha256) == 1) { "Answer figure lacks exact preparation provenance" } }
    }

    override suspend fun verifyDetailedSolutionFigures(solutionId: String): Boolean = database.withReadTransaction {
        val current = readDetailedSolution(solutionId) ?: return@withReadTransaction false
        requireAuthority(current)
        true
    }

    override suspend fun attachDetailedSolutionFigure(solutionId: String, expectedVersion: Long, blockId: String, attemptId: String, asset: CanonicalSourceAssetRecord, atEpochMillis: Long): PersistedDetailedSolution? = database.withWriteTransaction {
        val current = readDetailedSolution(solutionId) ?: return@withWriteTransaction null
        if (current.stateVersion != expectedVersion || current.status != DetailedSolutionPreparationStatus.RUNNING) return@withWriteTransaction null
        requireAuthority(current)
        val output = requireNotNull(current.output)
        require(blockId in output.document.requiredFigureBlockIds && current.figures.none { it.blockId == blockId })
        require(asset.sourceAssetId == mistakeEditFigureAssetId(attemptId))
        require(database.mistakeEditJobDao().eligibleAttempt(attemptId, detailedSolutionFigureOperationId(solutionId, blockId), current.basis.revisionId,
            detailedSolutionFigureBasis(current.request, output, blockId), FigureGenerationPurpose.EXPLANATION.name,
            FigureGenerationPolicyVersion.VALUE, asset.sourceAssetId) == 1)
        registerAsset(asset)
        check(database.figureGenerationDao().attachAttemptOutputAsset(attemptId, asset.sourceAssetId) == 1)
        val next = current.copy(stateVersion = current.stateVersion + 1, updatedAtEpochMillis = atEpochMillis,
            figures = current.figures + StoredFigureReference(blockId, asset.sourceAssetId, asset.contentSha256, VisualRole.TUTOR_EXPLANATION))
        requireAuthority(next)
        check(database.detailedSolutionDao().updateSolution(next.toEntity()) == 1)
        next
    }
}
