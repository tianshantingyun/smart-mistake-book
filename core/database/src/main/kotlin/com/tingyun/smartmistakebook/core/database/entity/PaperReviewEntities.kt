package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.*

@Entity(tableName = "paper_review", foreignKeys = [ForeignKey(entity = ReviewSessionEntity::class,
    parentColumns = ["review_session_id"], childColumns = ["session_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("session_id")])
data class PaperReviewEntity(
    @PrimaryKey @ColumnInfo(name = "paper_id") val paperId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val fingerprint: String,
    @ColumnInfo(name = "draft_snapshot") val draftSnapshot: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "export_id") val exportId: String?,
)

@Entity(tableName = "paper_review_question", primaryKeys = ["paper_id", "ordinal"],
    foreignKeys = [ForeignKey(entity = PaperReviewEntity::class, parentColumns = ["paper_id"], childColumns = ["paper_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ErrorBookEntryEntity::class, parentColumns = ["entry_id"], childColumns = ["entry_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProblemRevisionEntity::class, parentColumns = ["revision_id"], childColumns = ["revision_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PracticeUnitEntity::class, parentColumns = ["practice_unit_id"], childColumns = ["practice_unit_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("entry_id"), Index("revision_id"), Index("practice_unit_id")])
data class PaperReviewQuestionEntity(
    @ColumnInfo(name = "paper_id") val paperId: String, val ordinal: Int,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "practice_unit_id") val practiceUnitId: String,
    @ColumnInfo(name = "problem_id") val problemId: String,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    val title: String, val markdown: String,
    val result: String?, val corrected: Boolean, @ColumnInfo(name = "original_visible") val originalVisible: Boolean,
    @ColumnInfo(name = "answered_at") val answeredAt: Long?, val note: String, val applied: Boolean,
)

@Entity(tableName = "paper_review_photo", foreignKeys = [
    ForeignKey(entity = PaperReviewEntity::class, parentColumns = ["paper_id"], childColumns = ["paper_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CanonicalSourceAssetEntity::class, parentColumns = ["source_asset_id"], childColumns = ["source_asset_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("paper_id"), Index("source_asset_id"), Index(value = ["paper_id", "source_asset_id"], unique = true)])
data class PaperReviewPhotoEntity(
    @PrimaryKey @ColumnInfo(name = "photo_id") val photoId: String,
    @ColumnInfo(name = "paper_id") val paperId: String,
    @ColumnInfo(name = "source_asset_id") val sourceAssetId: String,
    val corrected: Boolean, @ColumnInfo(name = "question_ordinal") val questionOrdinal: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
