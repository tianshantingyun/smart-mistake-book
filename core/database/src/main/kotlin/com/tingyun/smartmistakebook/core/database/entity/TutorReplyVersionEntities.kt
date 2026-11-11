package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "tutor_reply_group", foreignKeys = [
    ForeignKey(entity = TutorMessageEntity::class, parentColumns = ["message_id"],
        childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["conversation_id"])])
internal data class TutorReplyGroupEntity(
    @PrimaryKey @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "original_request_id") val originalRequestId: String,
    @ColumnInfo(name = "selected_version_id") val selectedVersionId: String,
)

@Entity(tableName = "tutor_reply_version", foreignKeys = [
    ForeignKey(entity = TutorReplyGroupEntity::class, parentColumns = ["group_id"],
        childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["group_id", "ordinal"], unique = true)])
internal data class TutorReplyVersionEntity(
    @PrimaryKey @ColumnInfo(name = "version_id") val versionId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    val ordinal: Int,
    @ColumnInfo(name = "body_markdown") val bodyMarkdown: String,
    @ColumnInfo(name = "thinking_markdown") val thinkingMarkdown: String?,
    @ColumnInfo(name = "tool_trace_json") val toolTraceJson: String?,
    @ColumnInfo(name = "source_asset_ids_json") val sourceAssetIdsJson: String,
    @ColumnInfo(name = "request_json") val requestJson: String?,
    @ColumnInfo(name = "output_json") val outputJson: String?,
    @ColumnInfo(name = "provider_json") val providerJson: String?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

@Entity(tableName = "tutor_reply_version_answer", foreignKeys = [
    ForeignKey(entity = TutorReplyVersionEntity::class, parentColumns = ["version_id"],
        childColumns = ["version_id"], onDelete = ForeignKey.CASCADE),
])
internal data class TutorReplyVersionAnswerEntity(
    @PrimaryKey @ColumnInfo(name = "version_id") val versionId: String,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

internal data class TutorReplyVersionRow(
    @Embedded val version: TutorReplyVersionEntity,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "original_request_id") val originalRequestId: String,
    @ColumnInfo(name = "selected_version_id") val selectedVersionId: String,
    val locked: Boolean,
    @Relation(parentColumns = ["version_id"], entityColumns = ["version_id"])
    val answers: List<TutorReplyVersionAnswerEntity>,
)
