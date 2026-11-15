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
 * 每一种 kind 都有真实落点；`ADD_TO_REVIEW_PLAN` 自批次 A 起走应用动作执行器（旧卡不带
 * 动作参数，落 [APPLICATION_ACTION] 的"从当前对话重新发起"路径，不假装执行成功）。
 *
 * `START_EXPORT` 自 4B 批 4 起**已接线**：落到 [EXPORT_SHEET]——学生确认后打开导出 sheet
 * （版式表单预填模型提议值 + 本轮本地留痕的候选题勾选），确认后走后台导出管线。
 */
enum class TutorLocalActionTarget {
    /** Typed application action dispatched by the autonomous executor. */
    APPLICATION_ACTION,
    SAVE_CAPTURE_DRAFT,
    INTAKE_ATTACHED_IMAGES,
    LIBRARY_PROBLEM_DETAIL,

    /**
     * 打开导出 sheet（4B B3-3）：确认卡上学生点的"确认"不是"立刻导出"，而是"打开导出设置"
     * ——版式表单预填模型提议值、候选题来自本轮本地留痕，学生在 sheet 里改完勾完再点开始导出。
     */
    EXPORT_SHEET,
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

    // 选择导出（4B B3-3 已接线）：学生确认后打开导出 sheet，版式预填、候选题勾选都在那里。
    AgentPendingRequestKind.START_EXPORT -> TutorLocalActionTarget.EXPORT_SHEET

    // 纳入复习计划（批次 A 已接线）：走应用动作执行器（按条目 id 追加今日计划 revision）。
    // 旧卡不带动作参数，执行端无法从卡片重建命令——因此它落 APPLICATION_ACTION 的
    // "从当前对话重新发起"路径，不假装执行成功。
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN -> TutorLocalActionTarget.APPLICATION_ACTION
}

/**
 * 每种 kind 在 `agent_pending_request.payload_json` 里**允许出现的键**（固定字段形状）。
 *
 * 与 [TutorLocalActionContext] 的序列化键一一对应（`START_EXPORT` 是另一份形状，见
 * [EXPORT_PROPOSAL_PAYLOAD_KEYS]）；这里列出来是为了让落库口
 * （`requireAgentPendingRequestPayload`）能按 kind 逐字核对——**空形状也是形状**：
 * 除 `START_EXPORT` 外，模型不能往 payload 里写任何东西；`START_EXPORT` 自 4B B3-1 起
 * 收模型提议的**模板名 + 版式参数**，但**题 id 仍不在形状里**（题由学生在导出 sheet 里勾选）。
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

    // 选择导出：模板名 + 完整版式对象（模型只能提版式，题由学生在 sheet 里勾）。
    AgentPendingRequestKind.START_EXPORT -> EXPORT_PROPOSAL_PAYLOAD_KEYS
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
