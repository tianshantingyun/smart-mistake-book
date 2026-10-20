package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelTextBudgetExceededException
import java.nio.charset.StandardCharsets
import java.util.TreeMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal object OpenAiSse {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun eventDataBlocks(raw: String): List<String> {
        val blocks = ArrayList<String>()
        val current = StringBuilder()
        for (rawLine in raw.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty()) {
                flush(current, blocks)
                continue
            }
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trimStart()
            if (current.isNotEmpty()) current.append('\n')
            current.append(payload)
        }
        flush(current, blocks)
        return blocks.filter { it != "[DONE]" && it.isNotBlank() }
    }

    fun deltaContent(payload: String): String? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        val delta = first["delta"] as? JsonObject ?: return null
        return (delta["content"] as? JsonPrimitive)?.contentOrNull
    }

    /**
     * 这一帧声明的 `choices[0].finish_reason`（模型为什么停下来）：多数帧没有这个键，
     * 终帧上带 `stop` / `length` / `content_filter` / `tool_calls` 之一。
     */
    fun deltaFinishReason(payload: String): String? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        return (first["finish_reason"] as? JsonPrimitive)?.contentOrNull
    }

    /**
     * The chain-of-thought a reasoning model streams as its own delta. Providers spell the field
     * differently (`reasoning`, `reasoning_content`, `reasoning_text` — the same three spellings
     * opencode's client accepts); all mean the same student-visible thinking that the tutor shows
     * while the answer is still forming.
     */
    fun deltaReasoning(payload: String): String? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        val delta = first["delta"] as? JsonObject ?: return null
        return (delta["reasoning"] as? JsonPrimitive)?.contentOrNull
            ?: (delta["reasoning_content"] as? JsonPrimitive)?.contentOrNull
            ?: (delta["reasoning_text"] as? JsonPrimitive)?.contentOrNull
    }

    /**
     * 这一帧是否携带**原生工具调用增量**（`choices[0].delta.tool_calls` 非空）。
     *
     * 只回答"这一轮是工具轮吗"这一个问题（A6）：是工具轮，则同一轮里流出来的 `content` 是
     * 模型附带的叙述（"我先查一下错题本"），属于查阅单元，不属于回答正文。参数增量本身不进
     * 任何学生可见通道——工具调用的真实意图仍由终态解析产出。
     */
    fun deltaHasToolCalls(payload: String): Boolean {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return false
        val choices = root["choices"] as? JsonArray ?: return false
        val first = choices.firstOrNull() as? JsonObject ?: return false
        val delta = first["delta"] as? JsonObject ?: return false
        return (delta["tool_calls"] as? JsonArray)?.isNotEmpty() == true
    }

    /**
     * Incremental delta bodies in transport order. Each emission is the raw content that arrived in
     * one SSE frame; a frame never contributes the [DONE] terminator or a blank body. Malformed
     * frames are skipped so a stray byte cannot poison an otherwise well-formed stream.
     *
     * A [DONE] terminator frame ends the stream: any bytes that follow it (e.g. a trailing chunk
     * that raced with the terminal event) are ignored.
     */
    fun deltaChunks(raw: String): Sequence<String> = sequence {
        for (block in OpenAiSse.eventDataBlocksTerminated(raw)) {
            if (block == DONE_BLOCK) break
            val content = deltaContent(block)
            if (!content.isNullOrBlank()) {
                yield(content)
            }
        }
    }

    internal fun eventDataBlocksTerminated(raw: String): Sequence<String> = sequence {
        val current = StringBuilder()
        for (rawLine in raw.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty()) {
                val block = current.toString()
                if (current.isNotEmpty()) {
                    yield(block)
                    current.clear()
                }
                continue
            }
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trimStart()
            if (current.isNotEmpty()) current.append('\n')
            current.append(payload)
        }
        if (current.isNotEmpty()) yield(current.toString())
    }

    internal const val DONE_BLOCK = "[DONE]"

    /**
     * Rebuilds a non-streaming chat-completion envelope from the SSE frames, including the
     * `delta.tool_calls` increments (WG-04a).
     *
     * Until this aggregation existed the reconstructed message carried only `content`, so a
     * streaming tool round silently lost every native call and the tool loop never ran. The gate is
     * fail-closed:
     * - tool calls are merged by `index`, never by arrival order (parallel calls interleave);
     * - a call may only become executable after a terminal frame declared
     *   `finish_reason=tool_calls` (missing → [IncompleteModelOutputException]; `length` /
     *   `content_filter` → [IncompleteModelOutputException]; anything else → invalid);
     * - `id`/`name`/`type` take the first non-blank value, a later conflicting value is invalid;
     * - `arguments` are concatenated and parsed only once the stream ended; invalid JSON, an empty
     *   string, or more than one JSON value is invalid;
     * - a malformed data frame aborts the round instead of being skipped (a skipped fragment could
     *   make the remaining arguments look complete);
     * - the accumulated arguments are bounded while reading ([StreamedToolCallBudget]) and again at
     *   reconstruction: 512 KiB of decoded arguments in total, 64 KiB for a decoded `sourceJs`
     *   (the product budgets in `docs/current/visual-generation-contract.md` §4/§7.1);
     * - a non-tool text stream must end with an explicit terminal (`finish_reason` or `[DONE]`);
     *   a connection that stops mid-answer without either is [IncompleteModelOutputException],
     *   never a "complete" partial reply.
     */
    fun reconstructedChatCompletion(rawSse: String): String {
        val accumulator = ToolCallAccumulator()
        val content = StringBuilder()
        var finishReason: String? = null
        var terminalDeclared = false
        var sawDone = false
        for (block in eventDataBlocksTerminated(rawSse)) {
            if (block == DONE_BLOCK) {
                sawDone = true
                break
            }
            if (block.isBlank()) continue
            val choice = choiceOf(block) ?: continue
            val delta = choice["delta"]
            if (delta != null && delta !is JsonNull) {
                val deltaObject = delta as? JsonObject
                    ?: throw InvalidModelResponseException("streamed delta is not a JSON object")
                val toolCallDelta = deltaObject["tool_calls"]
                if (terminalDeclared && toolCallDelta != null && toolCallDelta !is JsonNull) {
                    val late = toolCallDelta as? JsonArray
                        ?: throw InvalidModelResponseException(
                            "tool_calls delta is not an array",
                        )
                    if (late.isNotEmpty()) {
                        throw InvalidModelResponseException(
                            "streamed tool call fragments arrived after the terminal frame",
                        )
                    }
                }
                (deltaObject["content"] as? JsonPrimitive)?.contentOrNull?.let { content.append(it) }
                accumulator.acceptDelta(toolCallDelta)
            }
            val reason = choice["finish_reason"].finishReasonOrNull()
            if (reason != null) {
                if (finishReason != null && finishReason != reason) {
                    throw InvalidModelResponseException(
                        "streamed reply declared conflicting finish_reason values",
                    )
                }
                finishReason = reason
                terminalDeclared = true
            }
        }

        if (accumulator.hasCalls()) {
            val toolCalls = accumulator.toWireCalls(finishReason)
            return reconstructedBody(content.toString(), finishReason, toolCalls)
        }
        if (finishReason == TOOL_CALL_FINISH_REASON) {
            throw InvalidModelResponseException(
                "streamed reply declared tool_calls without any tool call",
            )
        }
        if (content.isBlank()) {
            // 没有正文的流分两类：上游**说了**这次被截断（只有思考、答案被输出上限切掉，
            // 实测形态见 KD-33），和什么声明都没有的空白流。前者是"这次没写完"（可重试），
            // 后者维持既有的"这次答复没法用"（InvalidModelResponseException）。
            if (finishReason != null && finishReason in TRUNCATING_FINISH_REASONS) {
                throw IncompleteModelOutputException(
                    "streamed reply was truncated by the provider (finish_reason=$finishReason)",
                )
            }
            throw InvalidModelResponseException()
        }
        // 非工具文本流也必须有一个**明确终态**：provider 声明了 finish_reason，或至少
        // 送来 [DONE]。上游/网络在中间断开时两者都没有——把已收到的半段正文当作完整答复
        // 返回，重放会把它当成模型的最终回复（WG-04 验收要求截断有明确终态）。
        if (finishReason == null && !sawDone) {
            throw IncompleteModelOutputException(
                "streamed reply ended before the provider declared a terminal frame",
            )
        }
        return reconstructedBody(content.toString(), finishReason, toolCalls = null)
    }

    /**
     * Enforces the same tool-payload budget on a **non-streaming** chat-completion body. It only
     * bounds the payload; shape validation stays in [OpenAiModelProtocol.parseResponse]. This is the
     * counterpart of [StreamedToolCallBudget] for the `stream=false` fallback, so a provider cannot
     * bypass the cap just because the tool round was not streamed.
     */
    fun requireToolCallPayloadBudget(completionBody: String) {
        // 绝大多数响应不是工具轮：先做一次廉价的键存在性判断，避免为纯文本答复多解析一遍整包。
        if (!completionBody.contains("\"tool_calls\"")) return
        val root = runCatching { json.parseToJsonElement(completionBody) as? JsonObject }
            .getOrNull() ?: return
        val choices = root["choices"] as? JsonArray ?: return
        val message = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject ?: return
        val toolCalls = message["tool_calls"] as? JsonArray ?: return
        var totalBytes = 0
        toolCalls.forEachIndexed { index, element ->
            val function = (element as? JsonObject)?.get("function") as? JsonObject ?: return@forEachIndexed
            val arguments = function.optionalWireString("arguments") ?: return@forEachIndexed
            totalBytes += arguments.toByteArray(StandardCharsets.UTF_8).size
            if (totalBytes > MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES) {
                throw ModelTextBudgetExceededException(
                    "tool arguments exceed the ${MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES / 1024} KiB budget",
                )
            }
            val payload = runCatching {
                json.parseToJsonElement(arguments) as? JsonObject
            }.getOrNull() ?: return@forEachIndexed
            requireDecodedSourceBudget(payload, index)
        }
    }

    private fun reconstructedBody(
        content: String,
        finishReason: String?,
        toolCalls: JsonArray?,
    ): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "message",
                                buildJsonObject {
                                    if (toolCalls == null) {
                                        put("content", content)
                                    } else {
                                        // 标准原生工具调用的消息形状是 content=null；模型若同时说了
                                        // 一句叙述，仍原样保留在 content 里（解析层优先看 tool_calls）。
                                        if (content.isEmpty()) {
                                            put("content", JsonNull)
                                        } else {
                                            put("content", content)
                                        }
                                        put("tool_calls", toolCalls)
                                    }
                                },
                            )
                            // 终帧的 finish_reason 必须原样带进重建体：正文非空也可能被截断，
                            // 而终态解析（OpenAiModelProtocol.parseResponse）按这个键才分得出
                            // "模型写完了"与"模型没写完"，丢了它截断就又被归成内容不可用。
                            finishReason?.let { put("finish_reason", it) }
                        },
                    )
                },
            )
        },
    )

    private fun JsonElement?.finishReasonOrNull(): String? {
        if (this == null || this is JsonNull) return null
        val primitive = this as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw InvalidModelResponseException("streamed finish_reason is not a string")
        }
        return primitive.content
    }

    /**
     * Parses one data frame strictly. Unlike the live-text helpers above (which stay lenient so a
     * stray frame cannot break the visible answer), the terminal reconstruction must not guess: a
     * frame it cannot read could hide a tool-call fragment.
     */
    private fun choiceOf(payload: String): JsonObject? {
        val root = try {
            json.parseToJsonElement(payload)
        } catch (_: Exception) {
            throw InvalidModelResponseException("streamed SSE frame is not valid JSON")
        }
        if (root !is JsonObject) {
            throw InvalidModelResponseException("streamed SSE frame is not a JSON object")
        }
        val error = root["error"]
        if (error != null && error !is JsonNull) {
            throw InvalidModelResponseException("streamed reply carried an error frame")
        }
        val choices = root["choices"] ?: return null
        if (choices is JsonNull) return null
        val array = choices as? JsonArray
            ?: throw InvalidModelResponseException("streamed choices is not an array")
        if (array.isEmpty()) return null
        return array.firstOrNull() as? JsonObject
            ?: throw InvalidModelResponseException("streamed choice is not an object")
    }

    private fun JsonObject.optionalWireString(name: String): String? {
        val element = this[name] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw InvalidModelResponseException("tool call field '$name' is not a string")
        }
        return primitive.content
    }

    /**
     * Incremental aggregator used while the transport is still reading. It applies the same shape
     * and byte-budget rules as the final reconstruction so an over-budget or malformed stream is
     * cancelled mid-flight instead of being buffered first.
     */
    internal class StreamedToolCallBudget {
        private val accumulator = ToolCallAccumulator()

        fun observe(payload: String) {
            accumulator.acceptFrame(payload)
        }
    }

    private class PendingToolCall {
        var id: String? = null
        var type: String? = null
        var name: String? = null
        val arguments = StringBuilder()

        /** Merges one chunk and returns the number of UTF-8 bytes appended to `arguments`. */
        fun merge(chunk: JsonObject): Int {
            mergeIdentity(ID_FIELD, chunk.optionalWireString(ID_FIELD), ::assignId)
            mergeIdentity(TYPE_FIELD, chunk.optionalWireString(TYPE_FIELD), ::assignType)
            val function = chunk["function"]
            if (function == null || function is JsonNull) return 0
            val functionObject = function as? JsonObject
                ?: throw InvalidModelResponseException("tool call function is not an object")
            mergeIdentity(NAME_FIELD, functionObject.optionalWireString(NAME_FIELD), ::assignName)
            val fragment = functionObject.optionalWireString(ARGUMENTS_FIELD) ?: return 0
            arguments.append(fragment)
            return fragment.toByteArray(StandardCharsets.UTF_8).size
        }

        private fun mergeIdentity(field: String, value: String?, set: (String) -> Unit) {
            if (value.isNullOrEmpty()) return
            val current = when (field) {
                ID_FIELD -> id
                TYPE_FIELD -> type
                else -> name
            }
            when {
                current == null -> set(value)
                current != value -> throw InvalidModelResponseException(
                    "tool call $field changed within the same index",
                )
            }
        }

        private fun assignId(value: String) {
            id = value
        }

        private fun assignType(value: String) {
            if (value != FUNCTION_TYPE) {
                throw InvalidModelResponseException("tool call type '$value' is not supported")
            }
            type = value
        }

        private fun assignName(value: String) {
            name = value
        }

        fun toWire(index: Int): JsonObject {
            val resolvedId = id?.takeIf { it.isNotBlank() }
                ?: throw InvalidModelResponseException("tool call $index has no id")
            val resolvedType = type
                ?: throw InvalidModelResponseException("tool call $index has no type")
            val resolvedName = name?.takeIf { it.isNotBlank() }
                ?: throw InvalidModelResponseException("tool call $index has no name")
            val rawArguments = arguments.toString()
            if (rawArguments.isBlank()) {
                throw InvalidModelResponseException("tool call $index has empty arguments")
            }
            val payload = try {
                json.parseToJsonElement(rawArguments) as? JsonObject
                    ?: throw InvalidModelResponseException(
                        "tool call $index arguments are not a JSON object",
                    )
            } catch (failure: InvalidModelResponseException) {
                throw failure
            } catch (_: Exception) {
                throw InvalidModelResponseException("tool call $index arguments are not valid JSON")
            }
            requireDecodedSourceBudget(payload, index)
            return buildJsonObject {
                put("id", resolvedId)
                put("type", resolvedType)
                put(
                    "function",
                    buildJsonObject {
                        put("name", resolvedName)
                        put("arguments", rawArguments)
                    },
                )
            }
        }
    }

    private class ToolCallAccumulator {
        private val calls = TreeMap<Int, PendingToolCall>()
        private var argumentBytes = 0

        fun hasCalls(): Boolean = calls.isNotEmpty()

        fun acceptFrame(payload: String) {
            if (payload.isBlank() || payload == DONE_BLOCK) return
            val choice = choiceOf(payload) ?: return
            val delta = choice["delta"]
            if (delta == null || delta is JsonNull) return
            val deltaObject = delta as? JsonObject
                ?: throw InvalidModelResponseException("streamed delta is not a JSON object")
            acceptDelta(deltaObject["tool_calls"])
        }

        fun acceptDelta(element: JsonElement?) {
            if (element == null || element is JsonNull) return
            val array = element as? JsonArray
                ?: throw InvalidModelResponseException("tool_calls delta is not an array")
            for (item in array) {
                val chunk = item as? JsonObject
                    ?: throw InvalidModelResponseException("tool call chunk is not an object")
                val indexElement = chunk["index"]
                    ?: throw InvalidModelResponseException("tool call chunk has no index")
                if (indexElement is JsonNull) {
                    throw InvalidModelResponseException("tool call chunk has no index")
                }
                val index = (indexElement as? JsonPrimitive)
                    ?.takeIf { !it.isString }
                    ?.intOrNull
                    ?: throw InvalidModelResponseException("tool call index is not an integer")
                if (index !in 0 until MAX_STREAMED_TOOL_CALLS) {
                    throw InvalidModelResponseException(
                        "tool call index $index is outside the supported range",
                    )
                }
                argumentBytes += calls.getOrPut(index) { PendingToolCall() }.merge(chunk)
                if (argumentBytes > MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES) {
                    throw ModelTextBudgetExceededException(
                        "streamed tool arguments exceed the " +
                            "${MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES / 1024} KiB budget",
                    )
                }
            }
        }

        /**
         * Validates the terminal declaration and returns the calls ordered by `index`. Indices must
         * be contiguous from 0: a gap means a fragment was lost, and filling it would hand the tool
         * loop a call the provider never made.
         */
        fun toWireCalls(finishReason: String?): JsonArray {
            if (finishReason == null) {
                throw IncompleteModelOutputException(
                    "streamed tool call ended before the provider declared finish_reason",
                )
            }
            if (finishReason in TRUNCATING_FINISH_REASONS) {
                throw IncompleteModelOutputException(
                    "streamed tool call was truncated by the provider (finish_reason=$finishReason)",
                )
            }
            if (finishReason != TOOL_CALL_FINISH_REASON) {
                throw InvalidModelResponseException(
                    "tool calls cannot finish with finish_reason=$finishReason",
                )
            }
            val indices = calls.keys.toList()
            if (indices != (0 until indices.size).toList()) {
                throw InvalidModelResponseException(
                    "streamed tool call indices are not contiguous from 0",
                )
            }
            return buildJsonArray {
                indices.forEach { index -> add(calls.getValue(index).toWire(index)) }
            }
        }
    }

    private fun requireDecodedSourceBudget(payload: JsonObject, index: Int) {
        val source = payload["sourceJs"] ?: return
        if (source is JsonNull) return
        val primitive = source as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw InvalidModelResponseException("tool call $index sourceJs is not a string")
        }
        val bytes = primitive.content.toByteArray(StandardCharsets.UTF_8).size
        if (bytes > MAX_DECODED_TOOL_SOURCE_UTF8_BYTES) {
            throw ModelTextBudgetExceededException(
                "tool call $index sourceJs exceeds the " +
                    "${MAX_DECODED_TOOL_SOURCE_UTF8_BYTES / 1024} KiB decoded budget",
            )
        }
    }

    private fun flush(current: StringBuilder, blocks: MutableList<String>) {
        if (current.isEmpty()) return
        blocks.add(current.toString())
        current.clear()
    }

    private const val FUNCTION_TYPE = "function"
    private const val ID_FIELD = "id"
    private const val TYPE_FIELD = "type"
    private const val NAME_FIELD = "name"
    private const val ARGUMENTS_FIELD = "arguments"
}

/**
 * The wire-level terminal that makes a native tool call executable.
 * Other `finish_reason` values (even `stop`) never mean "the tool arguments are complete".
 */
internal const val TOOL_CALL_FINISH_REASON = "tool_calls"

/** 单轮全部工具参数（解码后 JSON 文本）的 UTF-8 预算：visual contract §4。 */
internal const val MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES = 512 * 1024

/** `sourceJs` 解码后的 UTF-8 预算：visual contract §4/§7.1（单程序源码 64 KiB）。 */
internal const val MAX_DECODED_TOOL_SOURCE_UTF8_BYTES = 64 * 1024

/**
 * OpenAI Node SDK finalization anchors 128 tool calls per stream; the same bound keeps the
 * index map and the reconstructed array finite.
 */
internal const val MAX_STREAMED_TOOL_CALLS = 128
