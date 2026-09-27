package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.AgentPendingRequestRecord
import com.tingyun.smartmistakebook.core.database.CreateAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.ResolveAgentPendingRequestDatabaseCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 待确认请求（插眼 5）的持久化端口。
 *
 * 默认**不落库**（null / 空流）：测试替身与无库界面没有这张表。默认值刻意选"做不到"而不是
 * "假装做到了"——调用方（`TutorConsentRequests`）据 null 判定"没挂成"，从而**不把回合推进
 * AWAITING_CONSENT**。一张挂起却没有行的卡，进程一死就悬死，那正是插眼 5 要消灭的失败。
 */
interface AgentPendingRequestPort {
    /** 落一行 PENDING。幂等：同一 `request_id` 同载荷重放返回已存在的行；不同载荷报冲突。 */
    suspend fun createAgentPendingRequest(
        command: CreateAgentPendingRequestDatabaseCommand,
    ): AgentPendingRequestRecord? = null

    /** 裁决到终态（幂等、留痕）。重复裁决同一结果返回已有终态；不同结果报冲突。 */
    suspend fun resolveAgentPendingRequest(
        command: ResolveAgentPendingRequestDatabaseCommand,
    ): AgentPendingRequestRecord? = null

    /** 观察未裁决的卡（null = 全栏；悬浮球待确认层用）。 */
    fun observePendingAgentRequests(
        conversationArea: String? = null,
    ): Flow<List<AgentPendingRequestRecord>> = flowOf(emptyList())

    /** 读未裁决的卡（进程死亡后重建同一张卡的输入）。 */
    suspend fun readPendingAgentRequests(
        conversationArea: String? = null,
    ): List<AgentPendingRequestRecord> = emptyList()

    /** 回喂通道（预留）：读已裁决的行，最近的在前。 */
    suspend fun readResolvedAgentPendingRequests(
        conversationArea: String,
        limit: Int,
    ): List<AgentPendingRequestRecord> = emptyList()
}
