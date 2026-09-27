package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 待确认请求（插眼 5，`docs/research/2026-09-25-mastery-mechanism-review.md` §5）：
 * **一次需要学生点头的本地行为**——确认卡挂起/打断工具调用时的持久化、恢复、留痕。
 *
 * 消灭的具体失败：把会话锁死。既有先例 `TutorLobbyRoute.kt:163-164`（"残留 true 会永久锁死
 * 输入框"）——确认卡挂起的回合必须有**落库的身份**，进程死亡后能重建同一张卡、裁决后能留痕，
 * 不得悬死。行是唯一权威：内存里的相位可以丢，行不能丢。
 *
 * ## 落库语义（本阶段定下来的部分）
 *
 * - **幂等**：`request_id` 是主键。同一个请求（同一回合 + 同一 kind + 同一 payload）重复落库
 *   是 no-op，**同一请求只挂一次**；同 id 不同 payload → 冲突（不覆盖已挂的那张卡）。
 * - **状态机**：`PENDING` → 终态（[AgentPendingRequestStatus.ACCEPTED] /
 *   [AgentPendingRequestStatus.DECLINED] / [AgentPendingRequestStatus.IGNORED]），终态不可回头；
 *   重复裁决**同一结果**幂等，不同结果冲突（第一个裁决为准，不静默改写历史）。
 * - **不自动失效**：没有 TTL、没有"过期自动拒绝"。学生没点，行就一直是 PENDING。
 * - **留痕**：终态带 `resolved_at` 与 `resolution_note`（默认收起，给"被拒理由"用）。
 * - **回喂**（D-K2e：本地动作 ≈ 工具，结果进下一轮上下文）：终态行经
 *   [AgentPendingRequestRepository.readResolvedRequests] 读回，装配进下一轮提示词是阶段 2 的事
 *   （那时动的是模型输入类型，按 `bf8be888` 纪律 strip 空载体 + 升指纹）。
 */

/**
 * 待确认请求的 kind 白名单 = 规格 §3.3 ask 档的五个对象（写工具 + 四个本地动作）。
 *
 * 为什么 kind 收在落库层而不是直接用工具名：确认卡的**执行路径**按 kind 分派
 * （打开某题 / 存题 / 导出 / 复习计划 / 工具写），而 ask 档里既有工具（`NOTEBOOK_WRITE`）又有
 * 本地动作（其余四个）——工具与动作共用同一张 persisted 卡与同一个状态机，kind 就是这五个的
 * 并集。名单外的 kind 不入库（列上不建 CHECK 约束，由落库口把关——SQLite 加约束要重建表）。
 */
enum class AgentPendingRequestKind {
    /** 工具拼写：模型在工具面里请求存题。 */
    NOTEBOOK_WRITE,

    /** 动作拼写：模型在本地动作通道里请求存题（同一件事，见 [agentPendingRequestKind]）。 */
    SAVE_TO_NOTEBOOK,

    OPEN_PROBLEM,
    START_EXPORT,
    ADD_TO_REVIEW_PLAN,
}

/** 待确认请求的状态。PENDING 是唯一非终态。 */
enum class AgentPendingRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    IGNORED,
    ;

    val isTerminal: Boolean get() = this != PENDING
}

/** 学生的裁决。PENDING 不是裁决结果——没有"裁决为待定"这件事。 */
enum class AgentPendingRequestDecision {
    ACCEPT,
    DECLINE,
    IGNORE,
    ;

    val terminalStatus: AgentPendingRequestStatus
        get() = when (this) {
            ACCEPT -> AgentPendingRequestStatus.ACCEPTED
            DECLINE -> AgentPendingRequestStatus.DECLINED
            IGNORE -> AgentPendingRequestStatus.IGNORED
        }
}

/**
 * 一条待确认请求。
 *
 * - [conversationArea] 是**物化快照**（与曝光账本的 `anchor_source` 同一手法）：悬浮球待确认层
 *   按会话区读，不必 join 会话行；会话区在创建后不再变，抄一份不会漂。
 * - [logicalOperationId] / [messageId] 是**被挂起的那一回合的身份**：进程死亡后重建同一张卡、
 *   裁决后继续原请求，都靠它们把行与回合对上（不靠"当前会话"这类会漂的推法）。
 */
data class AgentPendingRequest(
    val requestId: String,
    val conversationArea: String,
    val conversationId: String,
    val logicalOperationId: String,
    val messageId: String,
    val kind: AgentPendingRequestKind,
    val payloadJson: String,
    val status: AgentPendingRequestStatus,
    val createdAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long? = null,
    val resolutionNote: String? = null,
) {
    init {
        require(requestId.isNotBlank()) { "A pending request needs an id" }
        require(conversationArea.isNotBlank()) { "A pending request needs a conversation area" }
        require(conversationId.isNotBlank()) { "A pending request needs a conversation" }
        require(logicalOperationId.isNotBlank()) { "A pending request needs a logical operation id" }
        require(messageId.isNotBlank()) { "A pending request needs a message id" }
        requireAgentPendingRequestPayload(kind = kind, payloadJson = payloadJson)
        require(createdAtEpochMillis >= 0L) { "A pending request creation time must not be negative" }
        require(
            status.isTerminal == (resolvedAtEpochMillis != null),
        ) { "Only a resolved pending request carries a resolution time" }
        require(resolutionNote == null || resolutionNote.isNotBlank()) {
            "A pending request note must be null or non-blank"
        }
        require(status != AgentPendingRequestStatus.PENDING || resolutionNote == null) {
            "An unsettled pending request cannot carry a resolution note"
        }
    }
}

/**
 * 挂起前的形状：还没有状态，因为落库那一刻它只可能是 PENDING——让调用方交出终态等于让它
 * 自己造历史。
 */
data class AgentPendingRequestDraft(
    val conversationArea: String,
    val conversationId: String,
    val logicalOperationId: String,
    val messageId: String,
    val kind: AgentPendingRequestKind,
    val payloadJson: String,
) {
    init {
        require(conversationArea.isNotBlank()) { "A pending request needs a conversation area" }
        require(conversationId.isNotBlank()) { "A pending request needs a conversation" }
        require(logicalOperationId.isNotBlank()) { "A pending request needs a logical operation id" }
        require(messageId.isNotBlank()) { "A pending request needs a message id" }
        requireAgentPendingRequestPayload(kind = kind, payloadJson = payloadJson)
    }

    /**
     * 这一挂的幂等键：同一回合 + 同一 kind + 同一 payload = 同一个请求。重放（同一条模型输出
     * 被再次处理、进程重启后重放）落回同一行，不会挂出第二张卡。
     *
     * 摘要用 FNV-1a 64 位而不是 `String.hashCode`：前者是定死算法的纯函数，落库的键不会随
     * 语言/版本换算法而变——幂等键必须是可长期复算的。
     */
    fun requestId(): String = agentPendingRequestId(
        logicalOperationId = logicalOperationId,
        kind = kind,
        payloadJson = payloadJson,
    )
}

/** 幂等键的形状：`agent-req:<回合>:<kind>:<payload 摘要>`。 */
fun agentPendingRequestId(
    logicalOperationId: String,
    kind: AgentPendingRequestKind,
    payloadJson: String,
): String {
    require(logicalOperationId.isNotBlank()) { "A pending request needs a logical operation id" }
    return "agent-req:$logicalOperationId:${kind.name}:${fnv1a64Hex(payloadJson)}"
}

/**
 * 裁决的纯状态机：当前状态 + 裁决 → 终态。
 *
 * 返回 null = **冲突**（已有另一个终态）：第一个裁决为准，调用方据此报冲突而不是改写历史。
 * 返回与当前相同的终态 = **幂等**（双击、两处 UI 先后点同一个决定）。
 */
fun agentPendingRequestResolution(
    current: AgentPendingRequestStatus,
    decision: AgentPendingRequestDecision,
): AgentPendingRequestStatus? {
    if (current == AgentPendingRequestStatus.PENDING) return decision.terminalStatus
    return current.takeIf { it == decision.terminalStatus }
}

/**
 * 裁决结果（回喂模型的那一行，D-K2e）。
 *
 * 本阶段只有"接口 + 落库语义"：把它拼进下一轮提示词属于阶段 2（模型输入类型一改就要
 * strip 空载体 + 升指纹，见 `bf8be888` 纪律）。[resolutionNote] 同时是学生看到的"被拒理由"
 * ——同一份文本，不写两遍。
 */
data class AgentPendingRequestOutcome(
    val requestId: String,
    val kind: AgentPendingRequestKind,
    val status: AgentPendingRequestStatus,
    val resolutionNote: String?,
) {
    init {
        require(requestId.isNotBlank()) { "A pending request outcome needs a request id" }
        require(status.isTerminal) { "Only a settled pending request has an outcome" }
    }
}

/** payload 上限：确认卡的参数是**固定形状**的一小把 id/枚举，不是模型正文的容器。 */
const val MAX_AGENT_PENDING_REQUEST_PAYLOAD_CHARS = 2_000

/**
 * payload 的公共校验（形状无关）：必须是**参数对象**（JSON object），有大小上限、不含控制字符。
 *
 * 为什么在这一层把关：payload 会进数据库、会在卡上渲染、会回喂模型。让模型正文流进来 =
 * 卡片变成第二个会话流，且是绕过一切结果预算的通道（工具结果有 4k/轮的上限，卡上文本没有）。
 * 每种 kind 的**固定字段形状**随本地动作通道（阶段 2）落地时在这里补齐——那时它才真的有形状。
 */
fun requireAgentPendingRequestPayload(kind: AgentPendingRequestKind, payloadJson: String) {
    require(payloadJson.length <= MAX_AGENT_PENDING_REQUEST_PAYLOAD_CHARS) {
        "$kind payload exceeds $MAX_AGENT_PENDING_REQUEST_PAYLOAD_CHARS chars"
    }
    require(payloadJson.startsWith("{") && payloadJson.endsWith("}")) {
        "$kind payload must be a JSON object of fixed parameters"
    }
    require(payloadJson.isNotBlank() && payloadJson.none(Char::isISOControl)) {
        "$kind payload must not carry control characters"
    }
}

/**
 * 待确认请求的持久化端口（core:data 用 Room 实现，界面层只读得到 [Flow]）。
 *
 * 默认实现**不落库**（返回 null / 空流）：测试替身与无库界面不存在这张表。默认返回 null
 * 而不是"假装成功"，是为了让"没落成"与"落成了"在调用方看来不一样——**没落成时调用方不得
 * 把回合推进 AWAITING_CONSENT**（`TutorConsentRequests` 就是这么用的）。一张挂起的卡没有行，
 * 进程死亡后就是悬死，那正是插眼 5 要消灭的失败。
 */
interface AgentPendingRequestRepository {
    /**
     * 落一行 PENDING。返回落成的行；本实现不落库时返回 null。
     *
     * 幂等：同一 `request_id`（同一回合 + 同一 kind + 同一 payload）重复调用返回已存在的行；
     * 同 id 不同 payload → 抛 `IllegalStateException`（不覆盖已挂的卡）。
     */
    suspend fun createRequest(draft: AgentPendingRequestDraft, createdAtEpochMillis: Long): AgentPendingRequest? =
        null

    /**
     * 裁决：PENDING → 终态（幂等、留痕）。返回终态行；本实现不落库时返回 null。
     *
     * 重复裁决同一结果 → 返回已有终态；不同结果、或行不存在 → 抛 `IllegalStateException`。
     */
    suspend fun resolveRequest(
        requestId: String,
        decision: AgentPendingRequestDecision,
        resolutionNote: String?,
        resolvedAtEpochMillis: Long,
    ): AgentPendingRequest? = null

    /** 观察未裁决的卡（[conversationArea] 为 null = 全栏，悬浮球待确认层用）。 */
    fun observePendingRequests(conversationArea: String? = null): Flow<List<AgentPendingRequest>> =
        flowOf(emptyList())

    /** 读未裁决的卡（进程死亡后重建同一张卡的输入）。 */
    suspend fun readPendingRequests(conversationArea: String? = null): List<AgentPendingRequest> =
        emptyList()

    /**
     * 回喂通道（预留）：读已裁决的行，最近的在前。
     *
     * 本阶段**没有"已回喂"标记**：裁决即继续，回喂发生在同一条回合的下一次派发里，而那个
     * 消费者（阶段 2 的提示词装配）还没落地。加一个无人读的 `fed_back_at` 列就是凭空多一份
     * 状态——它随消费者一起来。
     */
    suspend fun readResolvedRequests(
        conversationArea: String,
        limit: Int = MAX_RESOLVED_PENDING_REQUESTS,
    ): List<AgentPendingRequest> = emptyList()

    companion object {
        /** 一次回喂读回的上限：卡是给学生看的一条小字，不是可翻页的历史。 */
        const val MAX_RESOLVED_PENDING_REQUESTS = 20
    }
}

/** FNV-1a 64 位摘要（小写十六进制）：定死算法的纯函数，用作幂等键的 payload 部分。 */
internal fun fnv1a64Hex(value: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= 0x100000001b3L // FNV prime
    }
    return hash.toULong().toString(radix = 16).padStart(length = 16, padChar = '0')
}
