package com.tingyun.smartmistakebook.core.data.model.wire

import com.tingyun.smartmistakebook.core.data.model.ApprovedImage
import com.tingyun.smartmistakebook.core.data.model.IncompleteModelOutputException
import com.tingyun.smartmistakebook.core.data.model.InvalidModelResponseException
import com.tingyun.smartmistakebook.core.data.model.JSON_MEDIA_TYPE
import com.tingyun.smartmistakebook.core.data.model.OpenAiModelProtocol
import com.tingyun.smartmistakebook.core.data.model.OpenAiModelTaskAdapters
import com.tingyun.smartmistakebook.core.data.model.OpenAiSse
import com.tingyun.smartmistakebook.core.data.model.SSE_ACCEPT
import com.tingyun.smartmistakebook.core.data.model.objectValue
import com.tingyun.smartmistakebook.core.data.model.optionalString
import com.tingyun.smartmistakebook.core.data.model.parseObject
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/**
 * Anthropic Messages 协议（spec 2026-09-08-multi-protocol §3.3）。
 *
 * 形状核对来源（官方 SDK `anthropics/anthropic-sdk-python`，2026-09-09）：
 * - 请求 `MessageCreateParams{max_tokens 必填, messages 必填, model 必填, system 顶层,
 *   temperature, stream}`（`types/message_create_params.py`）——messages 里**没有** system
 *   role，系统提示必须放顶层 `system`。
 * - 响应 `Message{content: [{type:"text", text}]}`（`types/message.py`）。
 * - 流式事件按 `type` 判别，文本增量在 `content_block_delta` 的
 *   `delta{type:"text_delta", text}`（`raw_message_stream_event.py`、
 *   `raw_content_block_delta_event.py`、`text_delta.py`）。
 * - 图片块 `{type:"image", source:{type:"base64", media_type, data}}`
 *   （`base64_image_source_param.py`）。
 *
 * 工具环按用户裁定走 Route B（`supportsNativeTools = false`，不发 tools）；结构化输出
 * 没有 json_object 信封，靠 prompt 约束——解析器已容忍 markdown 围栏。
 */
internal object AnthropicMessagesProtocol : ModelWireProtocol {
    override val protocol = ModelProviderProtocol.ANTHROPIC_MESSAGES
    override val supportsNativeTools = false

    override fun endpoint(baseUrl: HttpUrl, modelId: String, stream: Boolean): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegment("v1")
            .addPathSegment("messages")
            .build()

    override fun headers(apiKey: CharArray, stream: Boolean): List<Pair<String, String>> = listOf(
        "x-api-key" to String(apiKey),
        "anthropic-version" to ANTHROPIC_VERSION,
        "Accept" to if (stream) SSE_ACCEPT else JSON_MEDIA_TYPE.toString(),
    )

    override fun requestBody(
        modelId: String,
        input: ModelTaskInput,
        images: List<ApprovedImage>,
        stream: Boolean,
        enableNativeTools: Boolean,
    ): String = buildJsonObject {
        put("model", modelId)
        // Anthropic 必填；取一个对各代 Claude 都安全的输出上限。
        put("max_tokens", ANTHROPIC_MAX_TOKENS)
        put("temperature", ANTHROPIC_TEMPERATURE)
        put("system", OpenAiModelProtocol.SYSTEM_PROMPT)
        if (stream) put("stream", true)
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", OpenAiModelTaskAdapters.prompt(input))
                                    },
                                )
                                images.forEach { image ->
                                    add(
                                        buildJsonObject {
                                            put("type", "image")
                                            put(
                                                "source",
                                                buildJsonObject {
                                                    put("type", "base64")
                                                    put("media_type", image.mimeType)
                                                    put("data", image.base64())
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }.toString()

    override fun parseCompletion(
        body: String,
        input: ModelTaskInput,
        modelVersion: String,
    ): ModelTaskOutput {
        val envelope = parseObject(body)
        // 上游唯一一处"为什么停下来"的声明（同 Chat Completions 的 finish_reason）：
        // `stop_reason=max_tokens` = 达到 `max_tokens` 输出上限被截断（官方 SDK 的
        // `StopReason` 取值，含 "max_tokens"）。截断是"这次没写完"（可重试），必须先于
        // "文本为空"分流——推理模型把预算烧在思考上、正文一字节没吐时正是这个形态（KD-33），
        // 否则会被归成重发会逐字复现的 INVALID_RESPONSE。
        val stopReason = envelope.optionalString("stop_reason")
        if (stopReason == TRUNCATING_STOP_REASON) {
            throw IncompleteModelOutputException(
                "provider stopped before the answer was complete (stop_reason=$stopReason)",
            )
        }
        val text = textBlocksOf(envelope)
        if (text.isBlank()) {
            throw InvalidModelResponseException("Anthropic reply carried no text block")
        }
        return parseTaskOutputFromText(text, input, modelVersion)
    }

    override fun streamDelta(payload: String): String? = runCatching {
        val event = parseObject(payload)
        if (event["type"]?.jsonPrimitive?.contentOrNull != "content_block_delta") {
            return@runCatching null
        }
        val delta = event["delta"]?.objectValue() ?: return@runCatching null
        if (delta["type"]?.jsonPrimitive?.contentOrNull != "text_delta") {
            return@runCatching null
        }
        delta["text"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    override fun reconstructedBody(rawSse: String): String {
        val blocks = OpenAiSse.eventDataBlocksTerminated(rawSse).toList()
        val text = buildString {
            for (block in blocks) {
                if (block == OpenAiSse.DONE_BLOCK) break
                append(streamDelta(block) ?: continue)
            }
        }
        // 流式里"为什么停下来"的声明（KD-33 补记 / AUD-14）：Anthropic 在 `message_delta`
        // 事件的 `delta.stop_reason` 上给出终态原因（与整包响应的顶层 `stop_reason` 同义）。
        // 它必须原样带进重建体：正文非空也可能被 `max_tokens` 截断，终态解析
        // （[parseCompletion]）按这个键才分得出"模型写完了"与"模型没写完"——丢了它，
        // 截断又会落回 INVALID_RESPONSE（不可重试、重发会逐字复现）。
        val stopReason = blocks.firstNotNullOfOrNull(::streamStopReason)
        if (text.isBlank() && stopReason == TRUNCATING_STOP_REASON) {
            throw IncompleteModelOutputException(
                "streamed reply was truncated by the provider (stop_reason=$stopReason)",
            )
        }
        return buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                },
            )
            stopReason?.let { put("stop_reason", it) }
        }.toString()
    }

    override fun probeRequestBody(modelId: String, probe: ModelProbeKind): String = when (probe) {
        ModelProbeKind.STRUCTURED -> structuredProbeBody(modelId)
        ModelProbeKind.IMAGE -> imageProbeBody(modelId)
        // Route B：本协议不声明原生工具，探测不会请求它。
        ModelProbeKind.TOOLS -> error("Anthropic Messages protocol does not use native tools")
    }

    override fun probeResponseText(body: String): String? = runCatching {
        textBlocksOf(parseObject(body)).ifBlank { null }
    }.getOrNull()

    /**
     * 这一帧携带的"为什么停下来"声明：`message_delta` 事件的 `delta.stop_reason`
     * （官方 SSE 事件序列的终态事件之一；与整包响应顶层的 `stop_reason` 同义）。
     * 也接受顶层 `stop_reason`（个别兼容实现把整包形状放进单帧），两者都没有则 null。
     */
    private fun streamStopReason(payload: String): String? = runCatching {
        val event = parseObject(payload)
        event.optionalString("stop_reason")
            ?: event["delta"]?.objectValue()?.optionalString("stop_reason")
    }.getOrNull()

    /** 把响应 `content[]` 里的文本块按序拼接（忽略非文本块）。 */
    private fun textBlocksOf(envelope: JsonObject): String = envelope["content"]?.jsonArray
        .orEmpty()
        .mapNotNull { block ->
            val element = block as? JsonObject ?: return@mapNotNull null
            if (element["type"]?.jsonPrimitive?.contentOrNull != "text") return@mapNotNull null
            element["text"]?.jsonPrimitive?.contentOrNull
        }
        .joinToString("")

    private fun structuredProbeBody(modelId: String): String = buildJsonObject {
        put("model", modelId)
        put("max_tokens", ModelProbeSpec.PROBE_MAX_TOKENS)
        put("temperature", 0)
        put("system", ModelProbeSpec.STRUCTURED_INSTRUCTION)
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", ModelProbeSpec.STRUCTURED_USER)
                    },
                )
            },
        )
    }.toString()

    private fun imageProbeBody(modelId: String): String = buildJsonObject {
        put("model", modelId)
        put("max_tokens", ModelProbeSpec.PROBE_MAX_TOKENS)
        put("temperature", 0)
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "image")
                                        put(
                                            "source",
                                            buildJsonObject {
                                                put("type", "base64")
                                                put("media_type", "image/png")
                                                put("data", ModelProbeSpec.SYNTHETIC_IMAGE_BASE64)
                                            },
                                        )
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", ModelProbeSpec.IMAGE_USER)
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }.toString()

    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val ANTHROPIC_MAX_TOKENS = 8192
    private const val ANTHROPIC_TEMPERATURE = 0.1

    /**
     * 上游声明"这次生成被截断"的 `stop_reason`（官方 SDK `StopReason` 里表示达到 `max_tokens`
     * 输出上限的那一个）。与 Chat Completions 的 `length` 同一出口：可重试的
     * [IncompleteModelOutputException]，不是"内容不可用"。
     *
     * 边界：`StopReason` 里的 `model_context_window_exceeded`（上下文窗口被占满）**不**在此列
     * ——重发同一条请求会逐字复现，归成可重试是误导；`end_turn` / `stop_sequence` 等是正常收尾。
     */
    private const val TRUNCATING_STOP_REASON = "max_tokens"
}
