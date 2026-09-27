package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.database.AgentPendingRequestRecord
import com.tingyun.smartmistakebook.core.database.CreateAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.ResolveAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.port.AgentPendingRequestPort
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDraft
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 待确认请求（插眼 5）落库实现：域记录 ↔ 行，语义（幂等、终态不可回头）全部由
 * `AgentPendingRequestDao` 在同一事务里保证，这里不重复判一遍——两处各判一次，就有两套
 * 可能打架的规则。
 *
 * 依赖的是**这一族方法的窄口**（[AgentPendingRequestPort]）而不是整个 `StudyDatabasePort`：
 * 它只读这一张表，测试也就不必造一整个数据库替身。
 *
 * 冲突（同 id 不同载荷、重复裁决不同结果、裁决不存在的行）**不吞**：
 * `ImmutablePayloadConflictException` 直接抛给调用方。吞掉它的后果是"学生的决定静默消失"，
 * 那比一次可见的失败糟糕得多。
 */
internal class RoomAgentPendingRequestRepository(
    private val database: AgentPendingRequestPort,
) : AgentPendingRequestRepository {
    override suspend fun createRequest(
        draft: AgentPendingRequestDraft,
        createdAtEpochMillis: Long,
    ): AgentPendingRequest? {
        require(createdAtEpochMillis >= 0L) { "A pending request creation time must not be negative" }
        return withContext(Dispatchers.IO) {
            database.createAgentPendingRequest(
                CreateAgentPendingRequestDatabaseCommand(
                    requestId = draft.requestId(),
                    conversationArea = draft.conversationArea,
                    conversationId = draft.conversationId,
                    logicalOperationId = draft.logicalOperationId,
                    messageId = draft.messageId,
                    kind = draft.kind.name,
                    payloadJson = draft.payloadJson,
                    createdAtEpochMillis = createdAtEpochMillis,
                ),
            )?.toDomain()
        }
    }

    override suspend fun resolveRequest(
        requestId: String,
        decision: AgentPendingRequestDecision,
        resolutionNote: String?,
        resolvedAtEpochMillis: Long,
    ): AgentPendingRequest? {
        require(requestId.isNotBlank()) { "A pending request id must not be blank" }
        require(resolvedAtEpochMillis >= 0L) { "A decision time must not be negative" }
        return withContext(Dispatchers.IO) {
            database.resolveAgentPendingRequest(
                ResolveAgentPendingRequestDatabaseCommand(
                    requestId = requestId,
                    status = decision.terminalStatus.name,
                    resolutionNote = resolutionNote,
                    resolvedAtEpochMillis = resolvedAtEpochMillis,
                ),
            )?.toDomain()
        }
    }

    override fun observePendingRequests(
        conversationArea: String?,
    ): Flow<List<AgentPendingRequest>> =
        database.observePendingAgentRequests(conversationArea)
            .map { records -> records.map(AgentPendingRequestRecord::toDomain) }

    override suspend fun readPendingRequests(
        conversationArea: String?,
    ): List<AgentPendingRequest> = withContext(Dispatchers.IO) {
        database.readPendingAgentRequests(conversationArea).map(AgentPendingRequestRecord::toDomain)
    }

    override suspend fun readResolvedRequests(
        conversationArea: String,
        limit: Int,
    ): List<AgentPendingRequest> {
        require(conversationArea.isNotBlank()) { "A pending request area must not be blank" }
        require(limit > 0) { "A pending request read limit must be positive" }
        return withContext(Dispatchers.IO) {
            database.readResolvedAgentPendingRequests(conversationArea = conversationArea, limit = limit)
                .map(AgentPendingRequestRecord::toDomain)
        }
    }
}

object AgentPendingRequestRepositoryFactory {
    fun create(database: StudyDatabasePort): AgentPendingRequestRepository =
        RoomAgentPendingRequestRepository(database)
}

internal fun AgentPendingRequestRecord.toDomain() = AgentPendingRequest(
    requestId = requestId,
    conversationArea = conversationArea,
    conversationId = conversationId,
    logicalOperationId = logicalOperationId,
    messageId = messageId,
    kind = AgentPendingRequestKind.valueOf(kind),
    payloadJson = payloadJson,
    status = AgentPendingRequestStatus.valueOf(status),
    createdAtEpochMillis = createdAtEpochMillis,
    resolvedAtEpochMillis = resolvedAtEpochMillis,
    resolutionNote = resolutionNote,
)
