package com.tingyun.smartmistakebook.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 确认卡的**执行路由**（A4，插眼 5）：一张待确认的卡落地到哪一个真实动作。
 *
 * 消灭的具体失败：确认卡此前要么只导航（错题入口的「确认加入错题本」），要么整张面板根本
 * 不渲染（大厅 → REQUEST_SAVE_CONFIRMATION 被静默丢弃）——学生点了什么也没发生，或者连点
 * 的机会都没有。路由把"这张卡要干什么"变成一个显式判定，执行方（feature 层）只按它分派。
 *
 * ## 三条路由与它们的真实落点
 *
 * | 目标 | 落点 | 是否真落库 |
 * |---|---|---|
 * | [SAVE_CAPTURE_DRAFT] | `SaveTutorDraftToLibraryUseCase`（既有）：拍照草稿 → 错题本条目 | 是（错题本条目） |
 * | [INTAKE_ATTACHED_IMAGES] | 录入链路：本文消息附带的图片 → 单页草稿 / 批量导入任务 | 是（草稿 / 导入任务） |
 * | [LIBRARY_PROBLEM_DETAIL] | 打开错题本（有具体题则到它的详情） | 是（真动作：导航） |
 *
 * 判据只来自**本轮上下文**（有没有拍照会话 / 有没有已入库的题 / 有没有附图），不看入口、不看栏
 * ——与工具面同一条纪律（ADR 0001：差异只来自栏上下文、本轮绑定、本地门）。
 *
 * 白名单里尚未接线的两件（`START_EXPORT` / `ADD_TO_REVIEW_PLAN`）落到 [TutorLocalActionTarget.NOT_WIRED_YET]：
 * **卡照挂**（"模型提出行为 → 请求学生同意"本来就是它设计里的形态），学生点了之后本地如实说
 * "这一步现在还不能自动做"，不留一个假装执行成功的假象。真接线属阶段 4B（导出排版）与阶段 3B
 * （复习计划调整）。
 */
enum class TutorLocalActionTarget {
    SAVE_CAPTURE_DRAFT,
    INTAKE_ATTACHED_IMAGES,
    LIBRARY_PROBLEM_DETAIL,

    /**
     * 尚未接通：本地把学生的裁决（同意 / 不同意）落成终态留痕，执行结果如实说"还不能自动做"。
     *
     * 它与 null 的区别是**有没有一张卡**：null = 连卡都不该出现（本地没有可执行目标，学生点了
     * 什么也不会发生——那正是"点了没反应"的老毛病），[NOT_WIRED_YET] = 卡该出现，只是执行那一步
     * 还没接上。
     */
    NOT_WIRED_YET,
}

/**
 * 一张确认卡的执行上下文：本轮**已经有**的那些目标。全部可空/可空列表——空载体是常态
 * （大厅无题轮的卡只能靠附图落地），所以它同时是 payload 的形状：固定字段、固定类型。
 */
data class TutorLocalActionContext(
    /** 本轮的拍照会话（讲题侧的 confirmed tutor session）；有它就存草稿。 */
    val captureSessionId: String? = null,
    /** 本轮已经入库的那道题。 */
    val libraryProblemId: String? = null,
    /** 本条学生消息附带的图片（规范资产 id，按选择顺序）。 */
    val attachedImageAssetIds: List<String> = emptyList(),
) {
    init {
        require(captureSessionId == null || captureSessionId.isNotBlank()) {
            "A capture session id must be null or non-blank"
        }
        require(libraryProblemId == null || libraryProblemId.isNotBlank()) {
            "A library problem id must be null or non-blank"
        }
        require(attachedImageAssetIds.all(String::isNotBlank)) {
            "Attached image asset ids must not be blank"
        }
    }

    /**
     * payload 里没有任何可执行目标：这样的卡不该挂出来（挂出来点了也无处落地，
     * 那正是"点了什么也没发生"的老毛病）。
     */
    val isEmpty: Boolean
        get() = captureSessionId == null &&
            libraryProblemId == null &&
            attachedImageAssetIds.isEmpty()
}

/**
 * 这张卡的执行目标；null = 白名单里尚未接线的动作（不挂卡、也不假装已执行）。
 */
fun tutorLocalActionTarget(
    kind: AgentPendingRequestKind,
    context: TutorLocalActionContext,
): TutorLocalActionTarget? = when (kind) {
    // 存题（工具拼写与动作拼写是同一件事）：按"最具体的目标"顺序取。
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
    -> when {
        context.captureSessionId != null -> TutorLocalActionTarget.SAVE_CAPTURE_DRAFT
        context.attachedImageAssetIds.isNotEmpty() -> TutorLocalActionTarget.INTAKE_ATTACHED_IMAGES
        else -> TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL
    }

    // 打开某题：有题就到那道题，没题就打开错题本（真动作，不是空操作）。
    AgentPendingRequestKind.OPEN_PROBLEM -> TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL

    // 尚未接通的两件：卡要挂（学生同意/拒绝都是真实决定），执行如实说"还不能自动做"。
    AgentPendingRequestKind.START_EXPORT,
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN,
    -> TutorLocalActionTarget.NOT_WIRED_YET
}

/**
 * 每种 kind 在 `agent_pending_request.payload_json` 里**允许出现的键**（固定字段形状）。
 *
 * 与 [TutorLocalActionContext] 的序列化键一一对应；这里列出来是为了让落库口
 * （`requireAgentPendingRequestPayload`）能按 kind 逐字核对——**空形状也是形状**：
 * `START_EXPORT` 的参数就是"没有参数"（导出选什么由学生在本地勾选，模型不该也不能指定）。
 */
internal fun agentPendingRequestPayloadKeys(kind: AgentPendingRequestKind): Set<String> = when (kind) {
    // 存题（两种拼写）：目标可以是拍照草稿、已入库的题或本条消息的附图。
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
    -> setOf(KEY_CAPTURE_SESSION_ID, KEY_LIBRARY_PROBLEM_ID, KEY_IMAGE_ASSET_IDS)

    // 打开某题 / 纳入复习计划：只关心它是哪道题。
    AgentPendingRequestKind.OPEN_PROBLEM,
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN,
    -> setOf(KEY_LIBRARY_PROBLEM_ID)

    // 选择导出：没有任何模型可填的参数。
    AgentPendingRequestKind.START_EXPORT -> emptySet()
}

/** 确认卡的固定 payload 键（写进 `agent_pending_request.payload_json`）。 */
internal const val KEY_CAPTURE_SESSION_ID = "captureSessionId"
internal const val KEY_LIBRARY_PROBLEM_ID = "libraryProblemId"
internal const val KEY_IMAGE_ASSET_IDS = "imageAssetIds"

private val pendingRequestPayloadJson = Json { encodeDefaults = true }

/**
 * 上下文的持久形状（A4）：确认卡在进程里可以丢，**行不能丢**——进程死亡后重建同一张卡、
 * 学生点了之后还能照原样执行，靠的就是这份 payload 把目标记下来。
 *
 * 空键不落（空载体抹平的同一条纪律）：没有的目标不写进 payload，读回时仍是 null。
 */
fun TutorLocalActionContext.toAgentPendingRequestPayload(): String =
    toAgentPendingRequestPayload(keys = AFFECTED_ALL_PAYLOAD_KEYS)

/**
 * 按 kind 的固定字段形状只序列化**它允许的键**（`requireAgentPendingRequestPayload` 的另一半：
 * 那边核对"没有多余的键"，这边保证"不会多写键"）。
 */
fun TutorLocalActionContext.toAgentPendingRequestPayload(keys: Set<String>): String {
    val payload = buildJsonObject {
        val sessionId = captureSessionId
        if (sessionId != null && KEY_CAPTURE_SESSION_ID in keys) {
            put(KEY_CAPTURE_SESSION_ID, JsonPrimitive(sessionId))
        }
        val problemId = libraryProblemId
        if (problemId != null && KEY_LIBRARY_PROBLEM_ID in keys) {
            put(KEY_LIBRARY_PROBLEM_ID, JsonPrimitive(problemId))
        }
        if (attachedImageAssetIds.isNotEmpty() && KEY_IMAGE_ASSET_IDS in keys) {
            val assetIds = JsonArray(
                attachedImageAssetIds.map { assetId -> JsonPrimitive(assetId) },
            )
            put(KEY_IMAGE_ASSET_IDS, assetIds)
        }
    }
    return pendingRequestPayloadJson.encodeToString(JsonObject.serializer(), payload)
}

private val AFFECTED_ALL_PAYLOAD_KEYS =
    setOf(KEY_CAPTURE_SESSION_ID, KEY_LIBRARY_PROBLEM_ID, KEY_IMAGE_ASSET_IDS)

/** payload → 上下文（重建那张卡时读）。缺键 / 类型不符的部分按"没有"处理，不让坏行崩掉重建。 */
fun tutorLocalActionContext(payloadJson: String): TutorLocalActionContext {
    val root = runCatching {
        pendingRequestPayloadJson.parseToJsonElement(payloadJson) as? JsonObject
    }.getOrNull() ?: return TutorLocalActionContext()
    return TutorLocalActionContext(
        captureSessionId = root.stringOrNull(KEY_CAPTURE_SESSION_ID),
        libraryProblemId = root.stringOrNull(KEY_LIBRARY_PROBLEM_ID),
        attachedImageAssetIds = (root[KEY_IMAGE_ASSET_IDS] as? JsonArray)
            .orEmpty()
            .mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
            .filter(String::isNotBlank),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)

/**
 * 裁决 + 执行结果 → 回喂模型的那一行（`TutorLobbyInput.localActionOutcomes`）。
 *
 * [detail] 写进待确认行的 `resolution_note`（同一份文本：学生看到的"结果 / 被拒理由"与模型
 * 读到的"本地做成了没有"是同一句话）。
 */
fun agentPendingRequestOutcomeRecord(
    request: AgentPendingRequest,
    detail: String?,
): TutorLocalActionOutcomeRecord = TutorLocalActionOutcomeRecord(
    kind = request.kind.name,
    decision = request.status.name,
    detail = detail,
)

/**
 * 回喂记录（core:domain 的形状）：字段与 `TutorLobbyLocalActionOutcome`（core:model 的序列化
 * 形状）一一对应，由 feature 层在装配请求时转换——domain 不反向依赖 model 的输入类型。
 */
data class TutorLocalActionOutcomeRecord(
    val kind: String,
    val decision: String,
    val detail: String?,
) {
    init {
        require(kind.isNotBlank()) { "A local action outcome record needs a kind" }
        require(decision.isNotBlank()) { "A local action outcome record needs a decision" }
        require(detail == null || detail.isNotBlank()) {
            "A local action outcome record detail must be null or non-blank"
        }
    }
}

/**
 * 已裁决的行 → 回喂记录（进程死亡后也能重建：这些行本来就在库里）。
 *
 * 只回喂**这几个 kind**——它们才是学生能看见的动作；行里其他内容不进提示词。
 */
fun List<AgentPendingRequest>.toLocalActionOutcomeRecords(limit: Int): List<TutorLocalActionOutcomeRecord> {
    require(limit > 0) { "A local action outcome limit must be positive" }
    return asSequence()
        .filter { request -> request.status.isTerminal }
        .take(limit)
        .map { request -> agentPendingRequestOutcomeRecord(request, request.resolutionNote) }
        .toList()
}
