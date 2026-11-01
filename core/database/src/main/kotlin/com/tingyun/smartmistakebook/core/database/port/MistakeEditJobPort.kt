package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.flow.Flow

data class PersistedMistakeEditJob(
    val operationId: String,
    val request: ModelTaskRequest,
    val generation: Int = 0,
    val stateVersion: Long = 0,
    val status: MistakeEditJobStatus = MistakeEditJobStatus.QUEUED,
    val output: MistakeEditOutput? = null,
    val figures: List<StoredFigureReference> = emptyList(),
    val message: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val committedRevisionId: String? = null,
) {
    val input: MistakeEditInput get() = request.input as MistakeEditInput
    init {
        require(request.input is MistakeEditInput && operationId == mistakeEditJobId(input))
        require(generation >= 0 && stateVersion >= 0 && createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis)
        require(message == null || message.length <= 512)
        require(figures.size <= 32 && figures.map { it.blockId }.distinct().size == figures.size)
        output?.let { require(it.basis == input.basis); it.suggestion.requireMatches(input) }
        require(figures.isEmpty() || output?.suggestion?.decision == MistakeEditDecision.SUGGEST_CHANGES)
        val changes = output?.suggestion?.changes.orEmpty().filterIsInstance<MistakeEditChange.ReplaceFigure>()
        figures.forEach { ref ->
            val change = changes.single { it.blockId == ref.blockId }
            require(change.proposal.decision == FigureGenerationDecision.GENERATE)
            require(ref.role == editFigureRole(requireNotNull(change.proposal.purpose)))
        }
        if (status in setOf(MistakeEditJobStatus.READY, MistakeEditJobStatus.ADOPTED)) {
            applyPreparedMistakeEdit(input, requireNotNull(output), figures)
        }
        val decision = when (status) {
            MistakeEditJobStatus.NO_CHANGE -> MistakeEditDecision.NO_CHANGE
            MistakeEditJobStatus.NEED_SOURCE -> MistakeEditDecision.NEED_SOURCE
            else -> null
        }
        require(decision == null || output?.suggestion?.decision == decision)
        require(committedRevisionId == null || status == MistakeEditJobStatus.ADOPTED)
    }

    fun requireSuccessor(next: PersistedMistakeEditJob) {
        require(next.operationId == operationId && next.createdAtEpochMillis == createdAtEpochMillis)
        require(next.stateVersion == stateVersion + 1 && next.updatedAtEpochMillis >= updatedAtEpochMillis)
        require(ModelTaskLogicalOperationFingerprint.of(next.request) == ModelTaskLogicalOperationFingerprint.of(request))
        require(committedRevisionId == null && next.committedRevisionId == null)
        if (next.generation == generation + 1) {
            require(status in setOf(MistakeEditJobStatus.FAILED, MistakeEditJobStatus.CANCELLED, MistakeEditJobStatus.UNAVAILABLE, MistakeEditJobStatus.NEED_SOURCE))
            require(next.status == MistakeEditJobStatus.QUEUED)
            if (output?.suggestion?.decision == MistakeEditDecision.SUGGEST_CHANGES) {
                require(next.request == request && next.output == output && next.figures == figures)
            } else require(next.output == null && next.figures.isEmpty())
            return
        }
        require(next.generation == generation && next.request == request)
        require(output == null || next.output == output)
        require(next.figures.containsAll(figures))
        val allowed = when (status) {
            MistakeEditJobStatus.QUEUED -> setOf(MistakeEditJobStatus.REQUESTING, MistakeEditJobStatus.GENERATING_FIGURES, MistakeEditJobStatus.UNAVAILABLE)
            MistakeEditJobStatus.REQUESTING -> setOf(MistakeEditJobStatus.GENERATING_FIGURES, MistakeEditJobStatus.READY, MistakeEditJobStatus.NO_CHANGE, MistakeEditJobStatus.NEED_SOURCE, MistakeEditJobStatus.UNAVAILABLE)
            MistakeEditJobStatus.GENERATING_FIGURES -> setOf(MistakeEditJobStatus.GENERATING_FIGURES, MistakeEditJobStatus.READY, MistakeEditJobStatus.UNAVAILABLE)
            MistakeEditJobStatus.READY -> setOf(MistakeEditJobStatus.ADOPTED)
            else -> emptySet()
        }
        require(next.status in allowed || (status !in setOf(MistakeEditJobStatus.CANCELLED, MistakeEditJobStatus.SUPERSEDED) && next.status in setOf(MistakeEditJobStatus.CANCELLED, MistakeEditJobStatus.SUPERSEDED, MistakeEditJobStatus.FAILED)))
    }
}

/** Every mutation uses a persisted state version; a late provider response cannot resurrect a job. */
interface MistakeEditJobPort {
    suspend fun latestMistakeEditJob(basis: EditBasis): PersistedMistakeEditJob? = unsupported()
    suspend fun createMistakeEditJob(job: PersistedMistakeEditJob): PersistedMistakeEditJob = unsupported()
    suspend fun readMistakeEditJob(operationId: String): PersistedMistakeEditJob? = unsupported()
    fun observeMistakeEditJob(operationId: String): Flow<PersistedMistakeEditJob?> = unsupported()
    suspend fun transitionMistakeEditJob(next: PersistedMistakeEditJob): Boolean = unsupported()
    suspend fun readPendingMistakeEditJobs(afterOperationId: String?, limit: Int = 32): List<PersistedMistakeEditJob> = unsupported()
    suspend fun attachMistakeEditFigure(operationId: String, expectedVersion: Long, blockId: String, attemptId: String, asset: CanonicalSourceAssetRecord, atEpochMillis: Long): PersistedMistakeEditJob? = unsupported()
    suspend fun isMistakeEditBasisCurrent(basis: EditBasis): Boolean = unsupported()
    suspend fun verifyMistakeEditFigures(operationId: String): Boolean = unsupported()
}

private fun unsupported(): Nothing = throw UnsupportedOperationException("Mistake edit persistence is not installed")
