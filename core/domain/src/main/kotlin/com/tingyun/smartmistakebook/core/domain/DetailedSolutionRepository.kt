package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.flow.Flow

typealias DetailedSolutionPreparationStatus = com.tingyun.smartmistakebook.core.model.DetailedSolutionPreparationStatus

data class DetailedSolutionItemState(
    val ordinal: Int,
    val basis: SolutionBasis,
    val status: DetailedSolutionPreparationStatus,
    val document: DetailedSolutionDocument? = null,
    val message: String? = null,
    val figureUris: Map<String, String> = emptyMap(),
)

data class DetailedSolutionPreparation(
    val manifestId: String,
    val manifestSha256: String,
    val manifest: FrozenAnswerManifest,
    val status: DetailedSolutionPreparationStatus,
    val items: List<DetailedSolutionItemState>,
    val solutionsSha256: String? = null,
) {
    val complete: Boolean get() = status == DetailedSolutionPreparationStatus.READY && items.size == manifest.questions.size &&
        items.all { it.status == DetailedSolutionPreparationStatus.READY && it.document != null }
}

interface DetailedSolutionRepository {
    /** Only an explicit prepare action queues model work; observation and opening a sheet are read-only. */
    suspend fun freeze(manifest: FrozenAnswerManifest): String
    fun observe(manifestId: String): Flow<DetailedSolutionPreparation?>
    suspend fun read(manifestId: String): DetailedSolutionPreparation?
    suspend fun prepare(manifestId: String, agentConsentGranted: Boolean)
    suspend fun cancel(manifestId: String)
    suspend fun retry(manifestId: String, agentConsentGranted: Boolean)
    suspend fun invalidate(manifestId: String)
}
