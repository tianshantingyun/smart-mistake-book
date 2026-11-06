package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.*
import com.tingyun.smartmistakebook.core.database.entity.*
import kotlinx.coroutines.flow.Flow

data class PaperReviewSessionState(val sessionId: String, val planId: String, val ordinal: Int, val version: Long, val status: String)

interface PaperReviewStore {
    suspend fun sessionState(sessionId: String): PaperReviewSessionState?

    fun observePapers(): Flow<List<PaperReviewEntity>>
    fun observeQuestions(paperId: String): Flow<List<PaperReviewQuestionEntity>>
    fun observePhotos(paperId: String): Flow<List<PaperReviewPhotoEntity>>
    suspend fun paper(paperId: String): PaperReviewEntity?
    suspend fun questions(paperId: String): List<PaperReviewQuestionEntity>
    suspend fun photos(paperId: String): List<PaperReviewPhotoEntity>
    suspend fun create(paper: PaperReviewEntity, questions: List<PaperReviewQuestionEntity>)
    suspend fun setExport(paperId: String, exportId: String)
    suspend fun addPhoto(photo: PaperReviewPhotoEntity)
    suspend fun updateQuestion(question: PaperReviewQuestionEntity)
}

@Dao
abstract class PaperReviewDao : PaperReviewStore {
    @Query("SELECT review_session_id AS sessionId, review_plan_id AS planId, current_ordinal AS ordinal, state_version AS version, status FROM review_session WHERE review_session_id=:sessionId")
    abstract override suspend fun sessionState(sessionId: String): PaperReviewSessionState?

    @Query("SELECT * FROM paper_review ORDER BY created_at DESC") abstract override fun observePapers(): Flow<List<PaperReviewEntity>>
    @Query("SELECT * FROM paper_review_question WHERE paper_id=:paperId ORDER BY ordinal") abstract override fun observeQuestions(paperId: String): Flow<List<PaperReviewQuestionEntity>>
    @Query("SELECT * FROM paper_review_photo WHERE paper_id=:paperId ORDER BY created_at, photo_id") abstract override fun observePhotos(paperId: String): Flow<List<PaperReviewPhotoEntity>>
    @Query("SELECT * FROM paper_review WHERE paper_id=:paperId") abstract override suspend fun paper(paperId: String): PaperReviewEntity?
    @Query("SELECT * FROM paper_review_question WHERE paper_id=:paperId ORDER BY ordinal") abstract override suspend fun questions(paperId: String): List<PaperReviewQuestionEntity>
    @Query("SELECT * FROM paper_review_photo WHERE paper_id=:paperId ORDER BY created_at, photo_id") abstract override suspend fun photos(paperId: String): List<PaperReviewPhotoEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertPaper(paper: PaperReviewEntity): Long
    @Insert protected abstract suspend fun insertQuestions(questions: List<PaperReviewQuestionEntity>)
    @Transaction override suspend fun create(paper: PaperReviewEntity, questions: List<PaperReviewQuestionEntity>) {
        if (insertPaper(paper) != -1L) insertQuestions(questions)
        else check(this.paper(paper.paperId)?.fingerprint == paper.fingerprint) { "Paper identity conflict" }
    }
    @Query("UPDATE paper_review SET export_id=:exportId WHERE paper_id=:paperId") abstract override suspend fun setExport(paperId: String, exportId: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertPhoto(photo: PaperReviewPhotoEntity): Long
    @Query("SELECT * FROM paper_review_photo WHERE photo_id=:id") protected abstract suspend fun photo(id: String): PaperReviewPhotoEntity?
    @Update protected abstract suspend fun updatePhoto(photo: PaperReviewPhotoEntity)
    @Transaction override suspend fun addPhoto(photo: PaperReviewPhotoEntity) {
        if (insertPhoto(photo) != -1L) return
        val existing = requireNotNull(photo(photo.photoId))
        // One raw page can contain several questions. Re-association broadens its scope without duplicating bytes.
        updatePhoto(existing.copy(corrected = existing.corrected || photo.corrected,
            questionOrdinal = existing.questionOrdinal.takeIf { it == photo.questionOrdinal }))
    }
    @Update abstract override suspend fun updateQuestion(question: PaperReviewQuestionEntity)
}
