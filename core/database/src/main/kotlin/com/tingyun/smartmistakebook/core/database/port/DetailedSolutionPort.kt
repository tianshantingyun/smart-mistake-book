package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.flow.Flow

data class PersistedAnswerManifest(
    val manifestId: String,
    val manifest: FrozenAnswerManifest,
    val stateVersion: Long = 0,
    val status: DetailedSolutionPreparationStatus = DetailedSolutionPreparationStatus.QUEUED,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
) {
    val manifestSha256: String get() = DetailedSolutionCodec.manifestSha256(manifest)
    init {
        require(manifestId == "answer-manifest:" + manifestSha256)
        require(stateVersion >= 0 && createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis)
    }
}

data class PersistedDetailedSolution(
    val solutionId: String,
    val request: ModelTaskRequest,
    val generation: Int = 0,
    val stateVersion: Long = 0,
    val status: DetailedSolutionPreparationStatus = DetailedSolutionPreparationStatus.QUEUED,
    val output: DetailedSolutionOutput? = null,
    val figures: List<StoredFigureReference> = emptyList(),
    val message: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
) {
    val input: DetailedSolutionInput get() = request.input as DetailedSolutionInput
    val basis: SolutionBasis get() = input.question.basis
    val document: DetailedSolutionDocument? get() = output?.document?.copy(figures = figures)
    init {
        require(request.input is DetailedSolutionInput && solutionId == detailedSolutionId(basis))
        require(generation >= 0 && stateVersion >= 0 && createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis)
        require(message == null || message.length <= 512)
        require(output == null || output.document.basis == basis)
        require(figures.size <= 8 && figures.map { it.blockId }.distinct().size == figures.size)
        require(figures.all { it.blockId in requireNotNull(output).document.requiredFigureBlockIds && it.role == VisualRole.TUTOR_EXPLANATION })
        if (status == DetailedSolutionPreparationStatus.READY) require(canReuseSolution(requireNotNull(document), basis, figuresIntact = true))
    }
}

interface DetailedSolutionPort {
    suspend fun freezeAnswerManifest(record: PersistedAnswerManifest): PersistedAnswerManifest = unavailable()
    suspend fun readAnswerManifest(manifestId: String): PersistedAnswerManifest? = unavailable()
    fun observeAnswerManifest(manifestId: String): Flow<PersistedAnswerManifest?> = unavailable()
    suspend fun updateAnswerManifest(manifestId: String, expectedVersion: Long, status: DetailedSolutionPreparationStatus, atEpochMillis: Long): Boolean = unavailable()
    suspend fun createDetailedSolution(record: PersistedDetailedSolution): PersistedDetailedSolution = unavailable()
    suspend fun readDetailedSolution(solutionId: String): PersistedDetailedSolution? = unavailable()
    fun observeDetailedSolutions(solutionIds: List<String>): Flow<List<PersistedDetailedSolution>> = unavailable()
    suspend fun updateDetailedSolution(record: PersistedDetailedSolution): Boolean = unavailable()
    suspend fun attachDetailedSolutionFigure(solutionId: String, expectedVersion: Long, blockId: String, attemptId: String, asset: CanonicalSourceAssetRecord, atEpochMillis: Long): PersistedDetailedSolution? = unavailable()
    suspend fun verifyDetailedSolutionFigures(solutionId: String): Boolean = unavailable()
}

private fun unavailable(): Nothing = throw UnsupportedOperationException("Detailed solution persistence is not installed")
