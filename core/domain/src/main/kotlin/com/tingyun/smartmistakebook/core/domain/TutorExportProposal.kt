package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * `START_EXPORT` 确认卡 payload 的**唯一形状**（4B B3-1）：
 *
 * ```json
 * { "templateId": "practice_sheet", "layout": { ...MistakePdfLayout 全字段... } }
 * ```
 *
 * 它消灭的失败：改前这个 kind 的 payload 是空形状——模型对版式零输入，"两栏""字大点"这类
 * 学生要求无处落地；而一旦放开参数又没有形状，模型就能把任意排版标记塞进来。现在模型只能
 * 提**模板名 + [MistakePdfLayout] 的白名单字段**（见 `TutorLocalAction.parameters`），
 * 本地在这里收成固定形状：键恰好这两个、`templateId` 必须是模板枚举、`layout` 必须是
 * **完整**的布局对象且过 `validate()`、两处 templateId 必须一致。任何一处不符 → 落库口拒。
 *
 * **题 id 不在形状里**：候选题目来自本轮本地留痕（`NOTEBOOK_READ` 的
 * `TutorToolTraceEntry.problemEntryIds`），由学生在导出 sheet 里勾选——不新增"模型可写题 id"
 * 的通道，也就没有编造面。
 */
internal const val KEY_EXPORT_TEMPLATE_ID = "templateId"
internal const val KEY_EXPORT_LAYOUT = "layout"

internal val EXPORT_PROPOSAL_PAYLOAD_KEYS = setOf(KEY_EXPORT_TEMPLATE_ID, KEY_EXPORT_LAYOUT)

/** `layout` 对象的字段集（必须**恰好**是这个集合：缺字段、多字段都拒）。 */
private val EXPORT_LAYOUT_FIELD_NAMES = setOf(
    "templateId",
    "marginPt",
    "fontScale",
    "columnCount",
    "blockOrder",
    "imageScale",
    "includeAnswer",
    "includeSolution",
    "includeNote",
)

private val exportPayloadJson = Json { encodeDefaults = true }

/**
 * 模型提议的版式 → 确认卡 payload（本地唯一写入点）。
 *
 * 缺省字段在进 payload 前已被填成 [MistakePdfLayout] 的默认值：卡上展示的、学生改的、
 * worker 渲染的必须是**同一份**完整布局，而不是"一部分默认值散在三个地方各算一次"。
 */
fun exportProposalPayload(layout: MistakePdfLayout): String {
    require(layout.validate().isEmpty()) { "Invalid export layout: ${layout.validate()}" }
    val root = buildJsonObject {
        put(KEY_EXPORT_TEMPLATE_ID, JsonPrimitive(layout.templateId))
        put(
            KEY_EXPORT_LAYOUT,
            buildJsonObject {
                put("templateId", JsonPrimitive(layout.templateId))
                put("marginPt", JsonPrimitive(layout.marginPt))
                put("fontScale", JsonPrimitive(layout.fontScale))
                put("columnCount", JsonPrimitive(layout.columnCount))
                put(
                    "blockOrder",
                    buildJsonArray {
                        layout.blockOrder.forEach { token -> add(JsonPrimitive(token)) }
                    },
                )
                put("imageScale", JsonPrimitive(layout.imageScale))
                put("includeAnswer", JsonPrimitive(layout.includeAnswer))
                put("includeSolution", JsonPrimitive(layout.includeSolution))
                put("includeNote", JsonPrimitive(layout.includeNote))
            },
        )
    }
    return exportPayloadJson.encodeToString(JsonObject.serializer(), root)
}

/**
 * payload → 版式提议；任何一处不符返回 null（**不抛**：确认卡渲染与执行都要能对坏行降级，
 * 而不是让整个界面崩掉）。
 */
fun tutorLocalActionExportProposal(payloadJson: String): MistakePdfLayout? {
    val root = runCatching {
        exportPayloadJson.parseToJsonElement(payloadJson) as? JsonObject
    }.getOrNull() ?: return null
    if (root.keys != EXPORT_PROPOSAL_PAYLOAD_KEYS) return null
    val templateId = root.stringValueOrNull(KEY_EXPORT_TEMPLATE_ID) ?: return null
    if (templateId !in MistakePdfLayout.TEMPLATE_IDS) return null
    val layout = (root[KEY_EXPORT_LAYOUT] as? JsonObject)
        ?.let(::mistakePdfLayoutFromJsonObject)
        ?: return null
    return layout.takeIf { it.templateId == templateId }
}

/**
 * 这是不是**旧版本**写下的 START_EXPORT payload（空对象 `{}`）。
 *
 * `d2317f5d` 的写入形状：admission 用空 key 集构造，落库的是 `{}`。升级后这类行按读回宽容
 * 通过结构校验，但 [tutorLocalActionExportProposal] 读不出提议——交互面据此走"来自旧版本的
 * 导出请求"的降级文案，并在学生确认后用 [MistakePdfLayout.DEFAULT] 打开 sheet（不死路、不崩）。
 */
fun agentPendingRequestExportPayloadIsLegacy(payloadJson: String): Boolean {
    val root = runCatching {
        exportPayloadJson.parseToJsonElement(payloadJson) as? JsonObject
    }.getOrNull() ?: return false
    return root.isEmpty()
}

/**
 * 模型给的**扁平**参数（`TutorLocalAction.START_EXPORT.parameters`）→ 完整布局。
 *
 * 每条规则都 fail-closed：缺 `templateId`、枚举外模板、越界/非数字的数值、非布尔值、
 * 重复/未知的块顺序 → null（调用方据此**不挂卡**）。缺省字段用布局默认值。
 */
internal fun exportLayoutFromActionParameters(
    parameters: Map<String, String>,
): MistakePdfLayout? {
    val templateId = parameters["templateId"] ?: return null
    if (templateId !in MistakePdfLayout.TEMPLATE_IDS) return null
    val layout = MistakePdfLayout(
        templateId = templateId,
        marginPt = parameters["marginPt"]
            ?.let { value -> value.toIntOrNull() ?: return null }
            ?: MistakePdfLayout.DEFAULT_MARGIN_PT,
        fontScale = parameters["fontScale"]
            ?.let { value -> value.toIntOrNull() ?: return null }
            ?: MistakePdfLayout.DEFAULT_FONT_SCALE,
        columnCount = parameters["columnCount"]
            ?.let { value -> value.toIntOrNull() ?: return null }
            ?: MistakePdfLayout.DEFAULT_COLUMN_COUNT,
        blockOrder = parameters["blockOrder"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: emptyList(),
        imageScale = parameters["imageScale"]
            ?.let { value -> value.toFloatOrNull() ?: return null }
            ?: MistakePdfLayout.DEFAULT_IMAGE_SCALE,
        includeAnswer = parameters["includeAnswer"]
            ?.let { value -> value.toBooleanStrictOrNull() ?: return null }
            ?: false,
        includeSolution = parameters["includeSolution"]
            ?.let { value -> value.toBooleanStrictOrNull() ?: return null }
            ?: false,
        includeNote = parameters["includeNote"]
            ?.let { value -> value.toBooleanStrictOrNull() ?: return null }
            ?: false,
    )
    return layout.takeIf { it.validate().isEmpty() }
}

/**
 * `layout` JSON 对象 → 布局；键必须**恰好**是 [EXPORT_LAYOUT_FIELD_NAMES]、类型必须逐项相符、
 * 值必须过 `validate()`。任何一处不符返回 null（这是"缺字段拒"的落点）。
 */
internal fun mistakePdfLayoutFromJsonObject(json: JsonObject): MistakePdfLayout? {
    if (json.keys != EXPORT_LAYOUT_FIELD_NAMES) return null
    val layout = MistakePdfLayout(
        templateId = json.stringValueOrNull("templateId") ?: return null,
        marginPt = json.intValueOrNull("marginPt") ?: return null,
        fontScale = json.intValueOrNull("fontScale") ?: return null,
        columnCount = json.intValueOrNull("columnCount") ?: return null,
        blockOrder = json.arrayOfStringsOrNull("blockOrder") ?: return null,
        imageScale = json.floatValueOrNull("imageScale") ?: return null,
        includeAnswer = json.booleanValueOrNull("includeAnswer") ?: return null,
        includeSolution = json.booleanValueOrNull("includeSolution") ?: return null,
        includeNote = json.booleanValueOrNull("includeNote") ?: return null,
    )
    return layout.takeIf { it.validate().isEmpty() }
}

/** 严格取值：类型不符 / 缺失一律 null（不借用 kotlinx 的宽松数字解析）。 */
private fun JsonObject.stringValueOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.intValueOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.intOrNull

private fun JsonObject.floatValueOrNull(key: String): Float? =
    (this[key] as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.floatOrNull

private fun JsonObject.booleanValueOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.booleanOrNull

private fun JsonObject.arrayOfStringsOrNull(key: String): List<String>? {
    val array = this[key] as? JsonArray ?: return null
    return array.map { element ->
        (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull?.takeIf(String::isNotBlank)
            ?: return null
    }
}
