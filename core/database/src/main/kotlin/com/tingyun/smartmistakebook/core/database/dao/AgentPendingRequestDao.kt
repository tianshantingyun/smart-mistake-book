package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.AGENT_PENDING_REQUEST_STATUS_PENDING
import com.tingyun.smartmistakebook.core.database.AGENT_PENDING_REQUEST_STATUS_TERMINAL
import com.tingyun.smartmistakebook.core.database.AgentPendingRequestRecord
import com.tingyun.smartmistakebook.core.database.CreateAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.ResolveAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.entity.AgentPendingRequestEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 待确认请求（插眼 5）的读写口。两条不变量在这里、不在调用方：
 *
 * 1. **同一请求只挂一次**：`request_id` 是主键，`INSERT IGNORE` + 载荷比对——同 id 同载荷是
 *    重放（幂等成功），同 id 不同载荷是冲突（不覆盖已挂的那张卡，也不静默丢弃新的那张）。
 * 2. **PENDING → 终态，不可回头**：裁决走 `WHERE status = 'PENDING'` 的比较交换；已有终态时
 *    同一结果幂等返回、不同结果报冲突（第一个裁决为准）。
 *
 * 为什么这些不放在调用方：调用方有三个（会话层、悬浮球待确认层、恢复路径），谁都可以重放
 * （模型输出重放、进程重启重放、双击）。不变量放在唯一写口上，重放的幂等就是**原子**的，
 * 不依赖调用方"记得先查一下"。
 */
@Dao
internal abstract class AgentPendingRequestDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRequest(entity: AgentPendingRequestEntity): Long

    @Query("SELECT * FROM agent_pending_request WHERE request_id = :requestId LIMIT 1")
    protected abstract suspend fun findRequest(requestId: String): AgentPendingRequestEntity?

    /**
     * 未裁决的卡。[conversationArea] 为 null = 全栏（悬浮球待确认层读所有区）。
     * 条件写成 `:area IS NULL OR ...` 而不是拼 SQL：外部输入一律参数绑定。
     */
    @Query(
        """
        SELECT * FROM agent_pending_request
        WHERE status = :status
          AND (:conversationArea IS NULL OR conversation_area = :conversationArea)
        ORDER BY created_at_epoch_millis DESC, request_id DESC
        """,
    )
    protected abstract fun observeRequests(
        conversationArea: String?,
        status: String,
    ): Flow<List<AgentPendingRequestEntity>>

    @Query(
        """
        SELECT * FROM agent_pending_request
        WHERE status = :status
          AND (:conversationArea IS NULL OR conversation_area = :conversationArea)
        ORDER BY created_at_epoch_millis DESC, request_id DESC
        """,
    )
    protected abstract suspend fun readRequests(
        conversationArea: String?,
        status: String,
    ): List<AgentPendingRequestEntity>

    /** 回喂读数：已裁决的行，最近的在前。 */
    @Query(
        """
        SELECT * FROM agent_pending_request
        WHERE conversation_area = :conversationArea
          AND status IN (:terminalStatuses)
        ORDER BY resolved_at_epoch_millis DESC, request_id DESC
        LIMIT :limit
        """,
    )
    protected abstract suspend fun readResolvedRequests(
        conversationArea: String,
        terminalStatuses: List<String>,
        limit: Int,
    ): List<AgentPendingRequestEntity>

    @Query(
        """
        UPDATE agent_pending_request
        SET status = :status,
            resolved_at_epoch_millis = :resolvedAtEpochMillis,
            resolution_note = :resolutionNote
        WHERE request_id = :requestId
          AND status = :expectedStatus
        """,
    )
    protected abstract suspend fun updateStatus(
        requestId: String,
        expectedStatus: String,
        status: String,
        resolvedAtEpochMillis: Long,
        resolutionNote: String?,
    ): Int

    fun observePending(conversationArea: String?): Flow<List<AgentPendingRequestRecord>> {
        require(conversationArea == null || conversationArea.isNotBlank()) {
            "A pending request area must be null or non-blank"
        }
        return observeRequests(
            conversationArea = conversationArea,
            status = AGENT_PENDING_REQUEST_STATUS_PENDING,
        ).map { rows -> rows.map(AgentPendingRequestEntity::toRecord) }
    }

    open suspend fun readPending(conversationArea: String?): List<AgentPendingRequestRecord> {
        require(conversationArea == null || conversationArea.isNotBlank()) {
            "A pending request area must be null or non-blank"
        }
        return readRequests(
            conversationArea = conversationArea,
            status = AGENT_PENDING_REQUEST_STATUS_PENDING,
        ).map(AgentPendingRequestEntity::toRecord)
    }

    open suspend fun readResolved(
        conversationArea: String,
        limit: Int,
    ): List<AgentPendingRequestRecord> {
        require(conversationArea.isNotBlank()) { "A pending request area must not be blank" }
        require(limit > 0) { "A pending request read limit must be positive" }
        return readResolvedRequests(
            conversationArea = conversationArea,
            terminalStatuses = AGENT_PENDING_REQUEST_STATUS_TERMINAL.toList(),
            limit = limit,
        ).map(AgentPendingRequestEntity::toRecord)
    }

    @Transaction
    open suspend fun createRequest(
        command: CreateAgentPendingRequestDatabaseCommand,
    ): AgentPendingRequestRecord {
        require(command.requestId.isNotBlank()) { "A pending request id must not be blank" }
        require(command.conversationArea.isNotBlank()) {
            "A pending request area must not be blank"
        }
        require(command.conversationId.isNotBlank()) { "A pending request needs a conversation" }
        require(command.logicalOperationId.isNotBlank()) {
            "A pending request needs a logical operation id"
        }
        require(command.messageId.isNotBlank()) { "A pending request needs a message id" }
        require(command.kind.isNotBlank()) { "A pending request needs a kind" }
        require(command.payloadJson.isNotBlank()) { "A pending request needs a payload" }
        require(command.createdAtEpochMillis >= 0L) {
            "A pending request creation time must not be negative"
        }
        val entity = command.toEntity()
        if (insertRequest(entity) != -1L) return entity.toRecord()
        val existing = checkNotNull(findRequest(command.requestId)) {
            "Agent pending request insert was not readable"
        }
        // 重放 = 同一回合、同一 kind、同一参数、同一张卡。时间不比：重放发生在之后，
        // 比时间会把幂等变成冲突（这正是重放要走的那条路）。
        if (
            existing.conversationArea != entity.conversationArea ||
            existing.conversationId != entity.conversationId ||
            existing.logicalOperationId != entity.logicalOperationId ||
            existing.messageId != entity.messageId ||
            existing.kind != entity.kind ||
            existing.payloadJson != entity.payloadJson
        ) {
            throw ImmutablePayloadConflictException("agent_pending_request", command.requestId)
        }
        return existing.toRecord()
    }

    @Transaction
    open suspend fun resolveRequest(
        command: ResolveAgentPendingRequestDatabaseCommand,
    ): AgentPendingRequestRecord {
        require(command.requestId.isNotBlank()) { "A pending request id must not be blank" }
        require(command.status in AGENT_PENDING_REQUEST_STATUS_TERMINAL) {
            "A pending request can only be settled into a terminal status: ${command.status}"
        }
        require(command.resolvedAtEpochMillis >= 0L) {
            "A pending request resolution time must not be negative"
        }
        require(command.resolutionNote == null || command.resolutionNote.isNotBlank()) {
            "A pending request note must be null or non-blank"
        }
        val updated = updateStatus(
            requestId = command.requestId,
            expectedStatus = AGENT_PENDING_REQUEST_STATUS_PENDING,
            status = command.status,
            resolvedAtEpochMillis = command.resolvedAtEpochMillis,
            resolutionNote = command.resolutionNote,
        )
        val existing = findRequest(command.requestId)
            ?: throw ImmutablePayloadConflictException("agent_pending_request", command.requestId)
        if (updated != 1) {
            // 已有终态：同一结果 = 双击/两处 UI 先后点了同一个决定（幂等）；
            // 不同结果 = 有人想改写历史，第一个裁决为准。
            if (existing.status != command.status) {
                throw ImmutablePayloadConflictException(
                    "agent_pending_request_status",
                    command.requestId,
                )
            }
            return existing.toRecord()
        }
        return existing.toRecord()
    }
}

internal fun AgentPendingRequestEntity.toRecord() = AgentPendingRequestRecord(
    requestId = requestId,
    conversationArea = conversationArea,
    conversationId = conversationId,
    logicalOperationId = logicalOperationId,
    messageId = messageId,
    kind = kind,
    payloadJson = payloadJson,
    status = status,
    createdAtEpochMillis = createdAtEpochMillis,
    resolvedAtEpochMillis = resolvedAtEpochMillis,
    resolutionNote = resolutionNote,
)

private fun CreateAgentPendingRequestDatabaseCommand.toEntity() = AgentPendingRequestEntity(
    requestId = requestId,
    conversationArea = conversationArea,
    conversationId = conversationId,
    logicalOperationId = logicalOperationId,
    messageId = messageId,
    kind = kind,
    payloadJson = payloadJson,
    status = AGENT_PENDING_REQUEST_STATUS_PENDING,
    createdAtEpochMillis = createdAtEpochMillis,
    resolvedAtEpochMillis = null,
    resolutionNote = null,
)
