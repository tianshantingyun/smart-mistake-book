package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "detailed_solution_manifest", indices = [Index("paper_draft_id"), Index("status")])
internal data class DetailedSolutionManifestEntity(
    @PrimaryKey @ColumnInfo(name = "manifest_id") val manifestId: String,
    @ColumnInfo(name = "paper_draft_id") val paperDraftId: String,
    @ColumnInfo(name = "manifest_snapshot") val manifestSnapshot: String,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "input_sha256") val inputSha256: String,
    val status: String,
    @ColumnInfo(name = "state_version") val stateVersion: Long,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(tableName = "detailed_solution", foreignKeys = [
    ForeignKey(entity = ProblemRevisionEntity::class, parentColumns = ["revision_id"], childColumns = ["revision_id"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["revision_id", "question_sha256", "protocol_version"], unique = true), Index("status")])
internal data class DetailedSolutionEntity(
    @PrimaryKey @ColumnInfo(name = "solution_id") val solutionId: String,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "question_sha256") val questionSha256: String,
    @ColumnInfo(name = "protocol_version") val protocolVersion: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "request_snapshot") val requestSnapshot: String,
    @ColumnInfo(name = "request_fingerprint") val requestFingerprint: String,
    @ColumnInfo(name = "output_snapshot") val outputSnapshot: String?,
    @ColumnInfo(name = "document_snapshot") val documentSnapshot: String?,
    @ColumnInfo(name = "document_sha256") val documentSha256: String?,
    @ColumnInfo(name = "figures_snapshot") val figuresSnapshot: String,
    val origin: String?,
    val verification: String?,
    val generation: Int,
    val status: String,
    @ColumnInfo(name = "state_version") val stateVersion: Long,
    val message: String?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)
