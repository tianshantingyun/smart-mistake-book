package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun parseObject(value: String): JsonObject = try {
    Json.parseToJsonElement(value).jsonObject
} catch (_: Exception) {
    throw InvalidModelResponseException("model response is not a JSON object")
}

internal fun JsonElement?.extractTextContent(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonArray -> mapNotNull { element ->
        element.objectValue().takeIf { it.optionalString("type") == "text" }?.optionalString("text")
    }.joinToString("").ifBlank { null }
    else -> null
}

internal fun String.unwrapJsonFence(): String {
    val trimmed = trim()
    if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) return trimmed
    return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}

internal fun JsonObject.array(name: String): JsonArray =
    this[name] as? JsonArray ?: JsonArray(emptyList())

internal fun JsonObject.optionalArray(name: String): JsonArray {
    val value = this[name] ?: return JsonArray(emptyList())
    return value as? JsonArray ?: throw InvalidModelResponseException()
}

internal fun JsonObject.requireOnlyKeys(allowedKeys: Set<String>) {
    if (keys.any { it !in allowedKeys }) throw InvalidModelResponseException()
}

internal fun JsonObject.optionalObject(name: String): JsonObject? {
    val value = this[name] ?: return null
    if (value is JsonPrimitive && value.contentOrNull == null) return null
    return value as? JsonObject ?: throw InvalidModelResponseException()
}

internal fun JsonObject.objectValue(name: String): JsonObject = this[name].objectValue()
internal fun JsonElement?.objectValue(): JsonObject =
    this as? JsonObject ?: throw InvalidModelResponseException()

internal fun JsonObject.requiredString(name: String): String = optionalString(name)
    ?.takeIf(String::isNotBlank) ?: throw InvalidModelResponseException()

internal fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

internal fun JsonObject.optionalInt(name: String): Int? =
    (this[name] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.optionalDouble(name: String): Double? =
    (this[name] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..1.0 }

/**
 * An absent key yields null; a present key that is not a JSON boolean throws,
 * mirroring [requiredBoolean] so a quoted `"true"` is never accepted as a flag.
 */
internal fun JsonObject.optionalBoolean(name: String): Boolean? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return when (primitive.content.takeUnless { primitive.isString }) {
        "true" -> true
        "false" -> false
        else -> throw InvalidModelResponseException()
    }
}

internal fun JsonObject.optionalFiniteDouble(name: String): Double? {
    val value = this[name] ?: return null
    if (value is JsonPrimitive && value.contentOrNull == null) return null
    return (value as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite)
        ?: throw InvalidModelResponseException()
}

internal fun JsonObject.requiredFiniteDouble(name: String): Double =
    (this[name] as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite)
        ?: throw InvalidModelResponseException()

internal fun JsonElement.requiredPrimitiveString(): String =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        ?: throw InvalidModelResponseException()

internal fun JsonObject.requiredBoolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.let { primitive ->
        when (primitive.content.takeUnless { primitive.isString }) {
            "true" -> true
            "false" -> false
            else -> null
        }
    } ?: throw InvalidModelResponseException()

internal fun JsonObject.requiredRegion(): NormalizedSourceRegion =
    (this["region"] as? JsonObject)?.toRegion() ?: throw InvalidModelResponseException()

internal fun JsonObject.optionalRegion(): NormalizedSourceRegion? =
    (this["region"] as? JsonObject)?.toRegion()

internal fun JsonObject.toRegion(): NormalizedSourceRegion {
    requireOnlyKeys(NORMALIZED_REGION_WIRE_KEYS)
    fun coordinate(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull
        ?: throw InvalidModelResponseException()
    return NormalizedSourceRegion(
        left = coordinate("left"),
        top = coordinate("top"),
        right = coordinate("right"),
        bottom = coordinate("bottom"),
    ).also { region ->
        if (!region.isValidRegion()) throw InvalidModelResponseException()
    }
}

internal val CAPTURE_ASSESSMENT_WIRE_KEYS =
    setOf("decision", "issues", "suggestedActions", "questionRegions", "followingPageRelations")
internal val CAPTURE_ASSESSMENT_ISSUE_WIRE_KEYS =
    setOf("code", "severity", "region", "message")
internal val NORMALIZED_REGION_WIRE_KEYS = setOf("left", "top", "right", "bottom")

internal fun NormalizedSourceRegion.isValidRegion(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        left in 0.0..1.0 && top in 0.0..1.0 && right in 0.0..1.0 && bottom in 0.0..1.0 &&
        left < right && top < bottom

internal fun NormalizedSourceRegion.contains(other: NormalizedSourceRegion): Boolean =
    left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

internal inline fun <reified T : Enum<T>> enumValue(value: String): T =
    enumValues<T>().firstOrNull { it.name == value } ?: throw InvalidModelResponseException()

internal class InvalidModelResponseException(message: String = "invalid model response") :
    IllegalArgumentException(message)

/**
 * 上游**明确声明**这次生成没有走完（`finish_reason = length / content_filter`）。它不是
 * [InvalidModelResponseException] 的子类，因为两者是两件事、两个出口：
 * - 这里是"这一次没写完"——答复本身可能完全合法，只是被输出上限或内容策略切断了；
 * - 那里是"这次答复形状不对"——同一条请求重发多半会逐字复现。
 *
 * 分开的直接收益是重发判决不再误伤：截断必须可重试，而形状错误重发没有意义。
 */
internal class IncompleteModelOutputException(
    message: String = "model output stopped before the answer was complete",
) : RuntimeException(message)

/**
 * 上游声明"这次生成被截断"的 `finish_reason`（OpenAI 契约：`length` = 达到输出上限，
 * `content_filter` = 被内容策略中途中止）。
 *
 * 两处读同一份判定——非流式解析（[OpenAiModelProtocol.parseResponse]）与流式重建
 *（[OpenAiSse.reconstructedChatCompletion]）——所以集合只有这一个来源：各写一份必然漂移，
 * 表现就是"同一条截断，流式可重试、非流式不可重试"。
 */
internal val TRUNCATING_FINISH_REASONS = setOf("length", "content_filter")
