package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "mistake_edit_job",
    foreignKeys = [
        ForeignKey(entity = ErrorBookEntryEntity::class, parentColumns = ["entry_id"], childColumns = ["entry_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProblemRevisionEntity::class, parentColumns = ["revision_id"], childColumns = ["basis_revision_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProblemRevisionEntity::class, parentColumns = ["revision_id"], childColumns = ["committed_revision_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("entry_id"), Index("basis_revision_id"), Index("committed_revision_id"), Index(value = ["status", "operation_id"])],
)
internal data class MistakeEditJobEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "basis_revision_id") val basisRevisionId: String,
    @ColumnInfo(name = "draft_sha256") val draftSha256: String,
    @ColumnInfo(name = "semantic_fingerprint") val semanticFingerprint: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "request_snapshot") val requestSnapshot: String,
    @ColumnInfo(name = "request_fingerprint") val requestFingerprint: String,
    val generation: Int,
    val status: String,
    @ColumnInfo(name = "state_version") val stateVersion: Long,
    @ColumnInfo(name = "output_snapshot") val outputSnapshot: String?,
    @ColumnInfo(name = "figures_snapshot") val figuresSnapshot: String,
    val message: String?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "committed_revision_id") val committedRevisionId: String?,
)
