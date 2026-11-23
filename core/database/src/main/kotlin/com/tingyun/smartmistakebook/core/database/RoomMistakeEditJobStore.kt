package com.tingyun.smartmistakebook.core.database

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.dao.requireFigureAuthority
import com.tingyun.smartmistakebook.core.database.dao.toEntity as toMistakeEditJobEntity
import com.tingyun.smartmistakebook.core.database.dao.toRecord as toMistakeEditJobRecord
import com.tingyun.smartmistakebook.core.database.port.MistakeEditJobPort
import com.tingyun.smartmistakebook.core.database.port.PersistedMistakeEditJob
import com.tingyun.smartmistakebook.core.model.EditBasis
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.MistakeEditChange
import com.tingyun.smartmistakebook.core.model.MistakeEditDecision
import com.tingyun.smartmistakebook.core.model.MistakeEditJobStatus
import com.tingyun.smartmistakebook.core.model.MistakeEditOutput
import com.tingyun.smartmistakebook.core.model.ModelTaskCodec
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.StoredFigureReference
import com.tingyun.smartmistakebook.core.model.StoredFigureReferenceCodec
import com.tingyun.smartmistakebook.core.model.editFigureRole
import com.tingyun.smartmistakebook.core.model.mistakeEditFigureAssetId
import com.tingyun.smartmistakebook.core.model.mistakeEditFigureBasis
import com.tingyun.smartmistakebook.core.model.mistakeEditFigureOperationId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomMistakeEditJobStore(
    private val database: StudyDatabase,
    private val upsertCanonicalSourceAssetRow: suspend (CanonicalSourceAssetRecord) -> Unit,
) : MistakeEditJobPort {
    override suspend fun latestMistakeEditJob(basis: EditBasis): PersistedMistakeEditJob? =
        database.mistakeEditJobDao().latest(basis.entryId, basis.revisionId, basis.draftSha256)
            ?.let { readMistakeEditJob(it.operationId) }

    override suspend fun createMistakeEditJob(job: PersistedMistakeEditJob): PersistedMistakeEditJob =
        database.withWriteTransaction {
            val dao = database.mistakeEditJobDao()
            dao.read(job.operationId)?.let { row ->
                val existing = requireNotNull(readMistakeEditJob(row.operationId)) {
                    "这条修改记录已损坏，请调整修改要求后重新提交"
                }
                require(
                    ModelTaskLogicalOperationFingerprint.of(existing.request) ==
                        ModelTaskLogicalOperationFingerprint.of(job.request),
                )
                return@withWriteTransaction existing
            }
            require(
                job.status == MistakeEditJobStatus.QUEUED && job.stateVersion == 0L &&
                    job.generation == 0 && job.output == null && job.figures.isEmpty() &&
                    job.committedRevisionId == null,
            )
            require(dao.currentBasis(job.input.basis.entryId, job.input.basis.revisionId) == 1) {
                "Edit basis is no longer current"
            }
            check(dao.insert(job.toMistakeEditJobEntity()) != -1L)
            job
        }

    override suspend fun readMistakeEditJob(operationId: String): PersistedMistakeEditJob? =
        database.withWriteTransaction {
            val dao = database.mistakeEditJobDao()
            val row = dao.read(operationId) ?: return@withWriteTransaction null
            try {
                row.toMistakeEditJobRecord()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                try {
                    val base = row.copy(
                        status = "FAILED",
                        outputSnapshot = null,
                        figuresSnapshot = "[]",
                    ).toMistakeEditJobRecord()
                    require(base.committedRevisionId == null)
                    val output = requireNotNull(
                        dao.completedOutput(
                            base.request.requestId,
                            ModelTaskFingerprint.of(base.request),
                        ),
                    ).let {
                        ModelTaskCodec.decodeOutput(it)
                            as MistakeEditOutput
                    }
                    require(output.basis == base.input.basis)
                    output.suggestion.requireMatches(base.input)
                    val changes = output.suggestion.changes
                        .filterIsInstance<MistakeEditChange.ReplaceFigure>()
                    val refs = changes.mapNotNull { change ->
                        val purpose = change.proposal.purpose ?: return@mapNotNull null
                        dao.recoverFigure(
                            mistakeEditFigureOperationId(base.operationId, change.blockId),
                            base.input.basis.revisionId,
                            mistakeEditFigureBasis(
                                ModelTaskFingerprint.of(base.request),
                                output,
                                change.blockId,
                            ),
                            purpose.name,
                            FigureGenerationPolicyVersion.VALUE,
                        )?.let {
                            StoredFigureReference(
                                change.blockId,
                                it.assetId,
                                it.contentSha256,
                                editFigureRole(purpose),
                            )
                        }
                    }
                    val status = when (output.suggestion.decision) {
                        MistakeEditDecision.NO_CHANGE ->
                            MistakeEditJobStatus.NO_CHANGE
                        MistakeEditDecision.NEED_SOURCE ->
                            MistakeEditJobStatus.NEED_SOURCE
                        MistakeEditDecision.UNAVAILABLE ->
                            MistakeEditJobStatus.UNAVAILABLE
                        MistakeEditDecision.SUGGEST_CHANGES ->
                            if (refs.size == changes.size) MistakeEditJobStatus.READY
                            else MistakeEditJobStatus.FAILED
                    }
                    val recovered = base.copy(
                        output = output,
                        figures = refs,
                        status = status,
                        stateVersion = base.stateVersion + 1,
                        message = "已从完成记录恢复修改建议，已生成的图片不会重复生成",
                    )
                    dao.requireFigureAuthority(recovered)
                    check(
                        dao.repairSnapshots(
                            operationId,
                            row.stateVersion,
                            status.name,
                            ModelTaskCodec.encodeOutput(output),
                            StoredFigureReferenceCodec.encode(refs),
                            requireNotNull(recovered.message),
                        ) == 1,
                    )
                    return@withWriteTransaction recovered
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An unverifiable row must not block recovery of other jobs.
                }
                dao.quarantine(operationId, row.stateVersion, "修改记录损坏，请重新提交修改要求")
                val failed = dao.read(operationId)
                runCatching { failed?.toMistakeEditJobRecord() }.getOrNull()
            }
        }

    override fun observeMistakeEditJob(operationId: String): Flow<PersistedMistakeEditJob?> =
        database.mistakeEditJobDao().observe(operationId).map { row ->
            row?.let { runCatching { it.toMistakeEditJobRecord() }.getOrNull() }
                ?: readMistakeEditJob(operationId)
        }

    override suspend fun transitionMistakeEditJob(next: PersistedMistakeEditJob): Boolean =
        database.withWriteTransaction {
            val dao = database.mistakeEditJobDao()
            val previous = dao.read(next.operationId)?.toMistakeEditJobRecord()
                ?: return@withWriteTransaction false
            if (previous.stateVersion + 1 != next.stateVersion) return@withWriteTransaction false
            previous.requireSuccessor(next)
            require(
                next.figures == previous.figures ||
                    next.generation == previous.generation + 1 &&
                    next.figures.all { it in previous.figures },
            ) { "Only audited asset attachment may add a figure" }
            dao.requireFigureAuthority(next)
            if (next.status in setOf(
                    MistakeEditJobStatus.REQUESTING,
                    MistakeEditJobStatus.GENERATING_FIGURES,
                    MistakeEditJobStatus.READY,
                    MistakeEditJobStatus.ADOPTED,
                )
            ) {
                if (dao.currentBasis(next.input.basis.entryId, next.input.basis.revisionId) != 1) {
                    return@withWriteTransaction false
                }
            }
            check(dao.update(next.toMistakeEditJobEntity()) == 1)
            true
        }

    override suspend fun readPendingMistakeEditJobs(
        afterOperationId: String?,
        limit: Int,
    ): List<PersistedMistakeEditJob> {
        require(limit in 1..64)
        val result = mutableListOf<PersistedMistakeEditJob>()
        var cursor = afterOperationId
        while (result.size < limit) {
            val remaining = limit - result.size
            val rows = database.mistakeEditJobDao().pending(cursor, remaining)
            if (rows.isEmpty()) break
            rows.forEach { row ->
                readMistakeEditJob(row.operationId)
                    ?.takeIf { it.status in setOf(
                        MistakeEditJobStatus.QUEUED,
                        MistakeEditJobStatus.REQUESTING,
                        MistakeEditJobStatus.GENERATING_FIGURES,
                    ) }
                    ?.let(result::add)
            }
            cursor = rows.last().operationId
            if (rows.size < remaining) break
        }
        return result
    }

    override suspend fun isMistakeEditBasisCurrent(basis: EditBasis): Boolean =
        database.mistakeEditJobDao().currentBasis(basis.entryId, basis.revisionId) == 1

    override suspend fun verifyMistakeEditFigures(operationId: String): Boolean =
        database.withReadTransaction {
            val dao = database.mistakeEditJobDao()
            val job = dao.read(operationId)?.toMistakeEditJobRecord()
                ?: return@withReadTransaction false
            dao.requireFigureAuthority(job)
            true
        }

    override suspend fun attachMistakeEditFigure(
        operationId: String,
        expectedVersion: Long,
        blockId: String,
        attemptId: String,
        asset: CanonicalSourceAssetRecord,
        atEpochMillis: Long,
    ): PersistedMistakeEditJob? = database.withWriteTransaction {
        val dao = database.mistakeEditJobDao()
        val job = dao.read(operationId)?.toMistakeEditJobRecord()
            ?: return@withWriteTransaction null
        if (job.stateVersion != expectedVersion ||
            job.status != MistakeEditJobStatus.GENERATING_FIGURES ||
            job.committedRevisionId != null
        ) {
            return@withWriteTransaction null
        }
        if (dao.currentBasis(job.input.basis.entryId, job.input.basis.revisionId) != 1) {
            return@withWriteTransaction null
        }
        dao.requireFigureAuthority(job)
        require(asset.sourceAssetId == mistakeEditFigureAssetId(attemptId)) {
            "Each generated attempt needs its own immutable asset identity"
        }
        val output = requireNotNull(job.output)
        val change = output.suggestion.changes
            .filterIsInstance<MistakeEditChange.ReplaceFigure>()
            .single { it.blockId == blockId }
        require(job.figures.none { it.blockId == blockId })
        val purpose = requireNotNull(change.proposal.purpose)
        require(
            dao.eligibleAttempt(
                attemptId,
                mistakeEditFigureOperationId(operationId, blockId),
                job.input.basis.revisionId,
                mistakeEditFigureBasis(ModelTaskFingerprint.of(job.request), output, blockId),
                purpose.name,
                FigureGenerationPolicyVersion.VALUE,
                asset.sourceAssetId,
            ) == 1,
        ) { "Generated attempt does not belong to this edit target" }
        upsertCanonicalSourceAssetRow(asset)
        check(database.figureGenerationDao().attachAttemptOutputAsset(attemptId, asset.sourceAssetId) == 1)
        val next = job.copy(
            stateVersion = job.stateVersion + 1,
            updatedAtEpochMillis = atEpochMillis,
            figures = job.figures + StoredFigureReference(
                blockId,
                asset.sourceAssetId,
                asset.contentSha256,
                editFigureRole(purpose),
            ),
        )
        job.requireSuccessor(next)
        dao.requireFigureAuthority(next)
        check(dao.update(next.toMistakeEditJobEntity()) == 1)
        next
    }
}
