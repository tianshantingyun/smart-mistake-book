package com.tingyun.smartmistakebook.core.data.model.wire

import com.tingyun.smartmistakebook.core.data.model.ApprovedImage
import com.tingyun.smartmistakebook.core.data.model.OpenAiModelTaskAdapters
import com.tingyun.smartmistakebook.core.data.model.parseObject
import com.tingyun.smartmistakebook.core.data.model.unwrapJsonFence
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import okhttp3.HttpUrl

/**
 * 一个协议族要提供的**全部协议相关行为**。任务 prompt 与 JSON 解析不在此层
 * （见 OpenAiModelTaskAdapters）：协议实现只负责"信封"。
 */
internal interface ModelWireProtocol {
    val protocol: ModelProviderProtocol
    /** 是否支持原生 tools 往返（仅 OpenAI 兼容为 true；新协议走 Route B）。 */
    val supportsNativeTools: Boolean

    fun endpoint(baseUrl: HttpUrl, modelId: String, stream: Boolean): HttpUrl

    fun headers(apiKey: CharArray, stream: Boolean): List<Pair<String, String>>

    fun requestBody(
        modelId: String,
        input: ModelTaskInput,
        images: List<ApprovedImage>,
        stream: Boolean,
        enableNativeTools: Boolean,
    ): String

    fun parseCompletion(body: String, input: ModelTaskInput, modelVersion: String): ModelTaskOutput

    /** SSE 单帧的文本增量；非文本帧返回 null。 */
    fun streamDelta(payload: String): String?

    /**
     * 这一轮是否走 **Route A（原生 tools）**——即 `content` 增量可能是逐字长出来的正文。
     *
     * 消灭的失败（A6）：实时文本此前不看协议路由，把 Route B 的 `content` 增量（json_object /
     * prompt 信封 `{"intentDecision":…,"messageMarkdown":…}`）当正文流出去，学生看到一段 JSON。
     * 默认 false：非 OpenAI 协议族恒走 Route B（见 [supportsNativeTools]）。
     */
    fun usesNativeToolRoute(input: ModelTaskInput, enableNativeTools: Boolean): Boolean = false

    /**
     * SSE 单帧里是否出现**原生工具调用增量**（`delta.tool_calls`）：用来在流式期间认出"这一轮
     * 是工具轮"。工具轮的 `content` 是模型附带的叙述（"我先查一下错题本"），它属于查阅单元，
     * 不属于回答正文。协议族不区分则默认 false。
     */
    fun streamToolCallDelta(payload: String): Boolean = false

    /**
     * SSE 单帧里的思考链增量（推理模型把思考和答案分开发流）。协议族若不区分，返回 null，
     * 界面就只显示答案本身。
     */
    fun streamReasoningDelta(payload: String): String? = null

    /** 把整段 SSE 重建成一次完整响应体（供终态解析）。 */
    fun reconstructedBody(rawSse: String): String

    /**
     * 能力探测的请求体（spec §3.4：请求按协议构造、判定断言共用 [ModelProbeSpec] 的令牌）。
     * 探测是"最小请求"，与任务请求无关，所以不在这里走 [requestBody]。
     */
    fun probeRequestBody(modelId: String, probe: ModelProbeKind): String

    /** 能力探测：从探测响应体里取出模型回复的文本（协议信封差异的唯一去处）。 */
    fun probeResponseText(body: String): String?
}

/**
 * 按配置协议取实现。`when` 穷尽列出全部协议：新增枚举常量若没配实现会**编译失败**，
 * 不会退化成运行期才发现的静默降级。
 */
internal fun protocolFor(protocol: ModelProviderProtocol): ModelWireProtocol = when (protocol) {
    ModelProviderProtocol.OPENAI_CHAT_COMPLETIONS -> OpenAiChatCompletionsProtocol
    ModelProviderProtocol.OPENAI_RESPONSES -> OpenAiResponsesProtocol
    ModelProviderProtocol.ANTHROPIC_MESSAGES -> AnthropicMessagesProtocol
    ModelProviderProtocol.GEMINI_GENERATE_CONTENT -> GeminiGenerateContentProtocol
}

/**
 * 把模型的文本回复解析成任务输出（spec §3.1）：模型可能返回裸 JSON 或带 markdown 围栏，
 * 两者都经 [unwrapJsonFence] 归一，再交给协议无关的任务解析器。
 */
internal fun parseTaskOutputFromText(
    text: String,
    input: ModelTaskInput,
    modelVersion: String,
): ModelTaskOutput = OpenAiModelTaskAdapters.parse(
    parseObject(text.unwrapJsonFence()),
    input,
    modelVersion,
)
