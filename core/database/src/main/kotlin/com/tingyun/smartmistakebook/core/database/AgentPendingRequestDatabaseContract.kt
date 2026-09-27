package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.TUTOR_CONVERSATION_AREA_AGENT

/** 待确认请求行（插眼 5）的读侧记录。 */
data class AgentPendingRequestRecord(
    val requestId: String,
    val conversationArea: String,
    val conversationId: String,
    val logicalOperationId: String,
    val messageId: String,
    val kind: String,
    val payloadJson: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long?,
    val resolutionNote: String?,
)

/** 落一行 PENDING 待确认请求。状态不由调用方给：落库那一刻它只可能是 PENDING。 */
data class CreateAgentPendingRequestDatabaseCommand(
    val requestId: String,
    val conversationArea: String = TUTOR_CONVERSATION_AREA_AGENT,
    val conversationId: String,
    val logicalOperationId: String,
    val messageId: String,
    val kind: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
)

/**
 * 裁决一行待确认请求。`status` 只能是终态——`PENDING` 不是裁决结果，写进来的话
 * "裁决"这个词就没有意义了（DAO 会拒）。
 */
data class ResolveAgentPendingRequestDatabaseCommand(
    val requestId: String,
    val status: String,
    val resolutionNote: String?,
    val resolvedAtEpochMillis: Long,
)

/** 唯一非终态。存字符串而不是布尔：状态还要长（`IGNORED` 之外的"被其他机制收走"类终态）。 */
internal const val AGENT_PENDING_REQUEST_STATUS_PENDING = "PENDING"

/**
 * 终态集合。与 core:domain 的 `AgentPendingRequestStatus` 同名同义——**名字的权威在 domain**，
 * 这里只做 SQL 口径（CAS 的期望值比较、裁决输入的合法性）。
 */
internal val AGENT_PENDING_REQUEST_STATUS_TERMINAL = setOf("ACCEPTED", "DECLINED", "IGNORED")
