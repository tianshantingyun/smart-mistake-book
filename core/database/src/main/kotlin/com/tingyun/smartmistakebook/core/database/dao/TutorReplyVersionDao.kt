package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.TutorReplyVersionRecord
import com.tingyun.smartmistakebook.core.database.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
internal abstract class TutorReplyVersionDao {
    @Transaction
    @Query("""SELECT v.*, g.conversation_id, g.original_request_id, g.selected_version_id,
        EXISTS(SELECT 1 FROM tutor_message later
            WHERE later.conversation_id = g.conversation_id AND later.ordinal > root.ordinal
              AND later.role IN ('STUDENT', 'ASSISTANT')) AS locked
        FROM tutor_reply_version v JOIN tutor_reply_group g ON g.group_id = v.group_id
        JOIN tutor_message root ON root.message_id = g.group_id
        WHERE g.conversation_id = :conversationId ORDER BY root.ordinal, v.ordinal""")
    protected abstract fun observeRows(conversationId: String): Flow<List<TutorReplyVersionRow>>

    fun observe(conversationId: String): Flow<List<TutorReplyVersionRecord>> =
        observeRows(conversationId).map { rows -> rows.map { row -> row.toRecord() } }

    @Query("SELECT * FROM tutor_message WHERE message_id = :messageId")
    protected abstract suspend fun message(messageId: String): TutorMessageEntity?

    @Query("""SELECT COUNT(*) FROM tutor_message WHERE conversation_id = :conversationId
        AND ordinal > :ordinal AND role IN ('STUDENT', 'ASSISTANT')""")
    protected abstract suspend fun laterMessages(conversationId: String, ordinal: Int): Int

    @Query("""SELECT COUNT(*) FROM model_task WHERE subject_id = :conversationId AND request_id != :exceptRequestId
        AND task_kind IN ('TUTOR_PLAN','TUTOR_RESPOND','TUTOR_LOBBY')
        AND status IN ('WAITING_FOR_MODEL','QUEUED','RUNNING','STREAMING')""")
    protected abstract suspend fun pendingTasks(conversationId: String, exceptRequestId: String): Int

    @Query("SELECT * FROM tutor_reply_group WHERE group_id = :groupId")
    protected abstract suspend fun group(groupId: String): TutorReplyGroupEntity?

    @Query("SELECT * FROM tutor_reply_version WHERE version_id = :versionId")
    protected abstract suspend fun version(versionId: String): TutorReplyVersionEntity?

    @Query("SELECT COALESCE(MAX(ordinal), 0) FROM tutor_reply_version WHERE group_id = :groupId")
    protected abstract suspend fun lastVersionOrdinal(groupId: String): Int

    @Query("SELECT * FROM tutor_message_source_asset WHERE message_id = :messageId")
    protected abstract suspend fun sourceLinks(messageId: String): List<TutorMessageSourceAssetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertGroup(entity: TutorReplyGroupEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertVersion(entity: TutorReplyVersionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertLinks(entities: List<TutorMessageSourceAssetEntity>)

    @Query("UPDATE tutor_reply_group SET selected_version_id = :versionId WHERE group_id = :groupId")
    protected abstract suspend fun updateSelection(groupId: String, versionId: String)

    @Transaction
    open suspend fun publish(original: TutorReplyVersionRecord, replacement: TutorReplyVersionRecord,
        admittedLinks: List<TutorMessageSourceAssetEntity>): Boolean {
        require(original.groupId == replacement.groupId && original.conversationId == replacement.conversationId)
        require(replacement.versionId.isNotBlank() && replacement.bodyMarkdown.isNotBlank())
        require(replacement.requestJson != null && replacement.outputJson != null)
        val root = message(original.groupId) ?: return false
        if (root.conversationId != original.conversationId || root.role != "ASSISTANT" ||
            root.status != "SUCCEEDED" || laterMessages(root.conversationId, root.ordinal) != 0 ||
            pendingTasks(root.conversationId, replacement.requestId) != 0) return false
        val existingGroup = group(original.groupId)
        if (existingGroup == null) {
            require(original.bodyMarkdown == root.bodyMarkdown && original.ordinal == 1)
            insertGroup(TutorReplyGroupEntity(original.groupId, original.conversationId,
                original.requestId, original.versionId))
            insertVersion(original.toEntity())
            original.responseJson?.let {
                insertAnswer(TutorReplyVersionAnswerEntity(original.versionId, it, original.createdAtEpochMillis))
            }
        } else {
            require(existingGroup.conversationId == replacement.conversationId)
        }
        val stored = version(replacement.versionId)
        if (stored != null) {
            require(stored == replacement.toEntity()) { "Reply version replay changed its immutable payload" }
        } else {
            require(replacement.ordinal == lastVersionOrdinal(original.groupId) + 1)
            insertVersion(replacement.toEntity())
            val links = sourceLinks(root.messageId)
            val known = links.map { it.sourceAssetId }.toSet()
            val startOrdinal = (links.maxOfOrNull { it.ordinal } ?: -1) + 1
            val additions = admittedLinks.filter { it.sourceAssetId !in known }.distinctBy { it.sourceAssetId }
            require(additions.all { it.role != null && it.messageId == root.messageId })
            insertLinks(additions.mapIndexed { index, link -> link.copy(ordinal = startOrdinal + index) })
        }
        updateSelection(original.groupId, replacement.versionId)
        return true
    }

    @Transaction
    open suspend fun select(conversationId: String, groupId: String, versionId: String): Boolean {
        val root = message(groupId) ?: return false
        val selected = version(versionId) ?: return false
        if (root.conversationId != conversationId || selected.groupId != groupId ||
            laterMessages(conversationId, root.ordinal) != 0 || pendingTasks(conversationId, "") != 0) return false
        updateSelection(groupId, versionId)
        return true
    }

    @Query("SELECT * FROM tutor_reply_version_answer WHERE version_id = :versionId")
    protected abstract suspend fun answer(versionId: String): TutorReplyVersionAnswerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAnswer(entity: TutorReplyVersionAnswerEntity)

    @Query("""UPDATE tutor_reply_version_answer SET response_json = :responseJson,
        updated_at_epoch_millis = :updatedAt WHERE version_id = :versionId AND response_json = :expected""")
    protected abstract suspend fun updateAnswer(versionId: String, expected: String, responseJson: String, updatedAt: Long): Int

    @Transaction
    open suspend fun saveAnswer(versionId: String, expected: String?, responseJson: String, updatedAt: Long, allowLockedReveal: Boolean = false): Boolean {
        require(responseJson.length in 1..131072 && updatedAt >= 0)
        val selected = version(versionId) ?: return false
        val owner = group(selected.groupId) ?: return false
        val root = message(owner.groupId) ?: return false
        if (owner.selectedVersionId != versionId ||
            (!allowLockedReveal && laterMessages(owner.conversationId, root.ordinal) != 0) ||
            pendingTasks(owner.conversationId, "") != 0) return false
        val existing = answer(versionId)
        if (allowLockedReveal) {
            val next = org.json.JSONObject(responseJson)
            require(next.getBoolean("solutionRevealed"))
            if (existing == null) {
                require(listOf("diagnosticStemMarkdown", "selectedChoiceId", "selectedChoiceMarkdown",
                    "selectionWasCorrect", "feedbackMarkdown", "requestedMove").all(next::isNull))
            } else {
                val previous = org.json.JSONObject(existing.responseJson)
                require(next.keys().asSequence().toSet() == previous.keys().asSequence().toSet())
                require(previous.keys().asSequence().filter { it !in setOf("solutionRevealed", "updatedAtEpochMillis") }
                    .all { previous.get(it) == next.get(it) })
                require(next.getLong("updatedAtEpochMillis") >= previous.getLong("updatedAtEpochMillis"))
            }
        }
        if (existing?.responseJson == responseJson) return true
        if (existing == null) {
            if (expected != null) return false
            insertAnswer(TutorReplyVersionAnswerEntity(versionId, responseJson, updatedAt))
            return true
        }
        if (expected == null) return false
        return updateAnswer(versionId, expected, responseJson, updatedAt) == 1
    }
}

private fun TutorReplyVersionRecord.toEntity() = TutorReplyVersionEntity(versionId, groupId, requestId,
    ordinal, bodyMarkdown, thinkingMarkdown, toolTraceJson, sourceAssetIdsJson, requestJson, outputJson, providerJson, createdAtEpochMillis)

private fun TutorReplyVersionRow.toRecord() = TutorReplyVersionRecord(version.versionId, version.groupId,
    conversationId, version.requestId, version.ordinal, originalRequestId, version.bodyMarkdown,
    version.thinkingMarkdown, version.toolTraceJson, version.sourceAssetIdsJson, version.requestJson,
    version.outputJson, version.createdAtEpochMillis, selectedVersionId == version.versionId, locked,
    answers.singleOrNull()?.responseJson, version.providerJson)
