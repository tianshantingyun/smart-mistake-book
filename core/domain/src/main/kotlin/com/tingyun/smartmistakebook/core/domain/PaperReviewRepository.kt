package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.flow.Flow

/** A frozen paper belongs to one persisted review queue, even when uploaded on a later day. */
data class PaperReview(
    val paperId: String, val sessionId: String, val planId: String, val fingerprint: String,
    val draftSnapshot: String, val createdAtEpochMillis: Long, val exportId: String?,
    val questions: List<PaperReviewQuestion> = emptyList(), val photos: List<PaperReviewPhoto> = emptyList(),
    val exportStatus: MistakeExportStatus? = null, val exportFailure: String? = null,
) {
    val completedCount: Int get() = questions.count { it.applied }
}
data class PaperReviewQuestion(
    val ordinal: Int, val entryId: String, val practiceUnitId: String, val problemId: String,
    val problemRevisionId: String, val title: String, val markdown: String,
    val selectedResult: ReviewSelfReportKey? = null, val corrected: Boolean = false,
    val originalVisible: Boolean = false, val answeredAtEpochMillis: Long? = null,
    val note: String = "", val applied: Boolean = false,
)
data class PaperReviewPhoto(val photoId: String, val localUri: String, val corrected: Boolean, val questionOrdinal: Int?, val sourceAssetId: String = "")
data class PaperReviewConfirmation(
    val paperId: String, val ordinal: Int, val originalResult: ReviewSelfReportKey?,
    val corrected: Boolean, val originalVisible: Boolean, val answeredAtEpochMillis: Long, val note: String,
)
fun paperReviewEvidenceKey(corrected: Boolean, originalResult: ReviewSelfReportKey?, originalVisible: Boolean): ReviewSelfReportKey? =
    originalResult?.takeUnless { corrected && !originalVisible && it != ReviewSelfReportKey.AGAIN }
fun validPaperAnsweredAt(exportedAt: Long, answeredAt: Long, uploadedAt: Long): Boolean =
    exportedAt >= 0 && answeredAt in exportedAt..uploadedAt

fun paperReviewPhotosForQuestion(photos: List<PaperReviewPhoto>, ordinal: Int): List<PaperReviewPhoto> =
    photos.filter { (it.questionOrdinal == null || it.questionOrdinal == ordinal) &&
        it.localUri.isNotBlank() && it.sourceAssetId.isNotBlank() }.distinctBy { it.photoId }

/** Every page sent to the agent is an explicit student selection; excess pages are never silently dropped. */
fun paperReviewAgentPhotos(photos: List<PaperReviewPhoto>, ordinal: Int, selectedPhotoIds: Set<String>): List<PaperReviewPhoto> {
    require(selectedPhotoIds.isNotEmpty()) { "请先选择要检查的作答照片" }
    require(selectedPhotoIds.size <= MAX_TUTOR_MESSAGE_IMAGES) { "一次最多检查 $MAX_TUTOR_MESSAGE_IMAGES 张照片，请分批选择" }
    val available = paperReviewPhotosForQuestion(photos, ordinal)
    require(selectedPhotoIds.all { id -> available.any { it.photoId == id } }) { "部分照片不属于本题或已无法读取，请重新选择" }
    return available.filter { it.photoId in selectedPhotoIds }
}

interface PaperReviewRepository {
    fun observe(): Flow<List<PaperReview>>
    fun observe(paperId: String): Flow<PaperReview?>
    suspend fun create(review: PaperReview): String
    suspend fun export(paperId: String): String
    suspend fun addPhotos(paperId: String, localUris: List<String>, corrected: Boolean, questionOrdinal: Int? = null)
    suspend fun confirm(command: PaperReviewConfirmation)
    suspend fun applyConfirmed(paperId: String): String
    suspend fun draftSnapshot(paperId: String): String
}
