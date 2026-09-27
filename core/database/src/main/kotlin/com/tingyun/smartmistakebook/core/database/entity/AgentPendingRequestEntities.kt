package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 待确认请求行（插眼 5）：**一次需要学生点头的本地行为**的持久身份。
 *
 * 为什么它是表而不是内存状态：确认卡挂起的是**一回合**，而进程会死、学生会离开页面。
 * 内存里的相位一丢，卡就没了；行还在，就能在**原会话区**（或悬浮球待确认层）重建同一张卡。
 * 既有先例 `TutorLobbyRoute.kt:163-164`（"残留 true 会永久锁死输入框"）说的就是没有落点
 * 的挂起状态有多难收场。
 *
 * 列的口径：
 * - `request_id`：**幂等键**（`agent-req:<回合>:<kind>:<payload 摘要>`，见 core:domain 的
 *   `agentPendingRequestId`）。同一请求只挂一次——重放落回同一行，`INSERT IGNORE` 天然消化。
 * - `conversation_area` / `conversation_id`：卡回哪儿。会话区是**物化快照**（会话区创建后不再
 *   变，抄一份不会漂），悬浮球待确认层按它读，不必 join 会话行。
 * - `logical_operation_id` / `message_id`：**被挂起的那一回合的身份**。重建同一张卡、裁决后
 *   继续原请求，都靠它把行与回合对上——不靠"当前会话"这类会漂的推法。
 * - `kind`：ask 档白名单（写工具 + 四个本地动作），执行路径按它分派。
 * - `payload_json`：该 kind 的**固定参数形状**（JSON object，有上限），不是模型正文的容器。
 * - `status`：`PENDING` → `ACCEPTED` / `DECLINED` / `IGNORED`，**终态不可回头**；没有 TTL，
 *   学生没点的卡不会自己失效。
 * - `resolved_at_epoch_millis` / `resolution_note`：留痕（终态那一刻 + 理由）。两列与终态同进
 *   同出：未裁决的行两列都是 NULL。
 */
@Entity(
    tableName = "agent_pending_request",
    foreignKeys = [
        ForeignKey(
            entity = TutorConversationEntity::class,
            parentColumns = ["conversation_id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["status", "created_at_epoch_millis"]),
        Index(value = ["conversation_id"]),
        Index(value = ["logical_operation_id"]),
    ],
)
internal data class AgentPendingRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "request_id")
    val requestId: String,
    @ColumnInfo(name = "conversation_area")
    val conversationArea: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "logical_operation_id")
    val logicalOperationId: String,
    @ColumnInfo(name = "message_id")
    val messageId: String,
    val kind: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "resolved_at_epoch_millis")
    val resolvedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "resolution_note")
    val resolutionNote: String? = null,
)
