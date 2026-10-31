package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.EditBasis
import com.tingyun.smartmistakebook.core.model.MistakeEditOutput
import com.tingyun.smartmistakebook.core.model.StoredFigureReference
import kotlinx.coroutines.flow.Flow

data class MistakeEditRequest(
    val key: MistakeRevisionKey,
    val draft: CapturedQuestionDocument,
    val targetBlockIds: Set<String>,
    val instruction: String,
    val sourceImageAssetIds: Set<String> = emptySet(),
    val editTitle: Boolean = false,
)

enum class MistakeEditJobStatus { QUEUED, REQUESTING, GENERATING_FIGURES, READY, NO_CHANGE, NEED_SOURCE, UNAVAILABLE, ADOPTED, FAILED, CANCELLED, SUPERSEDED }

data class MistakeEditJob(
    val operationId: String,
    val basis: EditBasis,
    val status: MistakeEditJobStatus,
    val output: MistakeEditOutput? = null,
    val figures: List<StoredFigureReference> = emptyList(),
    val message: String? = null,
)

/** Preparation is durable; adopting a suggestion changes only the edit draft, never a problem revision. */
interface MistakeEditRepository {
    suspend fun requestEdit(request: MistakeEditRequest, agentConsentGranted: Boolean): String
    fun observe(operationId: String): Flow<MistakeEditJob?>
    /** Returns only a validated SUGGEST_CHANGES job for the exact current basis. */
    suspend fun readySuggestion(operationId: String, currentBasis: EditBasis): MistakeEditJob?
    suspend fun cancel(operationId: String)
    suspend fun markAdopted(operationId: String, currentBasis: EditBasis): Boolean
}
