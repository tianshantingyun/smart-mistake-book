package com.tingyun.smartmistakebook.core.data.model.wire

import com.tingyun.smartmistakebook.core.model.KnowledgeQuizInput
import com.tingyun.smartmistakebook.core.model.KnowledgeQuizOutput
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anthropic Messages 协议（spec 2026-09-08-multi-protocol §3.3）：请求用顶层 `system` +
 * user 内容块（无 system role）、`x-api-key` + `anthropic-version` 认证、`max_tokens` 必填；
 * 响应取 `content[].text`；流式取 `content_block_delta` 的 `text_delta.text`。
 * 形状来源：官方 SDK `anthropics/anthropic-sdk-python`（见实现类注释）。
 */
class AnthropicMessagesProtocolTest {
    private val protocol = AnthropicMessagesProtocol
    private val input = KnowledgeQuizInput(
        knowledgeNodeId = "kc-derivative-monotonicity",
        subjectId = "MATH",
        materialTitle = "导数符号与单调性",
        materialContentMarkdown = "若 f'(x) > 0 在区间上恒成立，则 f 单调递增。",
        materialBoundaryMarkdown = "仅覆盖导数符号与单调性的关系，不含凹凸性。",
        lastMasteryScore = 0.42,
        lastEvidenceAtEpochMillis = 1_700_000_000_000L,
    )

    private fun bodyOf(images: List<com.tingyun.smartmistakebook.core.data.model.ApprovedImage> = emptyList()) =
        Json.parseToJsonElement(
            protocol.requestBody("claude-test", input, images, stream = false, enableNativeTools = false),
        ).jsonObject

    @Test
    fun endpointIsTheMessagesPath() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            protocol.endpoint("https://api.anthropic.com".toHttpUrl(), "claude-test", stream = false).toString(),
        )
    }

    @Test
    fun headersUseApiKeyAndAnthropicVersionInsteadOfBearer() {
        val headers = protocol.headers("sk-ant-test".toCharArray(), stream = false)
        assertEquals("sk-ant-test", headers.single { it.first == "x-api-key" }.second)
        assertEquals("2023-06-01", headers.single { it.first == "anthropic-version" }.second)
        assertEquals("application/json; charset=utf-8", headers.single { it.first == "Accept" }.second)
        assertTrue(headers.none { it.first == "Authorization" })
        assertEquals(
            "text/event-stream",
            protocol.headers("k".toCharArray(), stream = true).single { it.first == "Accept" }.second,
        )
    }

    @Test
    fun requestBodyUsesTopLevelSystemAndUserContentBlocks() {
        val body = bodyOf()

        assertEquals("claude-test", body["model"]?.jsonPrimitive?.content)
        assertTrue(body["max_tokens"]!!.jsonPrimitive.content.toInt() > 0)
        // 系统提示在顶层；messages 里不得出现 system role。
        assertTrue(body["system"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        val blocks = messages[0].jsonObject["content"]!!.jsonArray
        assertEquals("text", blocks[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(blocks[0].jsonObject["text"]!!.jsonPrimitive.content.contains("kc-derivative-monotonicity"))
        // Route B：不发 tools，也没有 json_object 信封。
        assertNull(body["tools"])
        assertNull(body["response_format"])
        assertNull(body["stream"])
    }

    @Test
    fun requestBodyAttachesImagesAsBase64SourceBlocks() {
        val image = com.tingyun.smartmistakebook.core.data.model.ApprovedImage(
            mimeType = "image/jpeg",
            bytes = byteArrayOf(0x01, 0x02, 0x03),
        )
        try {
            val blocks = bodyOf(listOf(image))["messages"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonArray
            assertEquals("image", blocks[1].jsonObject["type"]?.jsonPrimitive?.content)
            val source = blocks[1].jsonObject["source"]!!.jsonObject
            assertEquals("base64", source["type"]?.jsonPrimitive?.content)
            assertEquals("image/jpeg", source["media_type"]?.jsonPrimitive?.content)
            assertEquals(image.base64(), source["data"]?.jsonPrimitive?.content)
        } finally {
            image.close()
        }
    }

    @Test
    fun streamingFlagIsOnlySentWhenStreaming() {
        val streamed = Json.parseToJsonElement(
            protocol.requestBody("claude-test", input, emptyList(), stream = true, enableNativeTools = false),
        ).jsonObject
        assertEquals(true, streamed["stream"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun parseCompletionReadsTheTextBlocksAndParsesTheTaskPayload() {
        val output = protocol.parseCompletion(
            body = envelopeOf("""{"questionMarkdown":"若 f'(x)>0 恒成立，则 f 单调递增。","choices":[{"choiceId":"A","markdown":"递增"},{"choiceId":"B","markdown":"递减"}],"correctChoiceId":"A"}"""),
            input = input,
            modelVersion = "claude-test",
        ) as KnowledgeQuizOutput

        assertEquals("若 f'(x)>0 恒成立，则 f 单调递增。", output.questionMarkdown)
        assertEquals("A", output.correctChoiceId)
        assertEquals("claude-test", output.modelVersion)
    }

    @Test
    fun parseCompletionRejectsAReplyWithoutTextBlocks() {
        assertThrows(com.tingyun.smartmistakebook.core.data.model.InvalidModelResponseException::class.java) {
            protocol.parseCompletion(
                body = """{"content":[{"type":"tool_use","id":"x"}]}""",
                input = input,
                modelVersion = "claude-test",
            )
        }
    }

    @Test
    fun parseCompletionClassifiesAMaxTokensStopAsIncompleteOutput() {
        // stop_reason=max_tokens 是上游在说"这次被输出上限截断了"——与 Chat Completions 的
        // finish_reason=length 同口径：这是"这次没写完"（可重试），不是"内容不可用"
        // （重发会逐字复现）。正文非空也照判，因为判定看的是声明而不是正文。
        val thrown = assertThrows(
            com.tingyun.smartmistakebook.core.data.model.IncompleteModelOutputException::class.java,
        ) {
            protocol.parseCompletion(
                body = envelopeOf("""{"questionMarkdown":"题干（被切断""", stopReason = "max_tokens"),
                input = input,
                modelVersion = "claude-test",
            )
        }
        assertTrue("截断原因必须写进归因", thrown.message.orEmpty().contains("max_tokens"))

        // 反向：空文本但**没有**截断声明，仍是既有的 INVALID_RESPONSE（重发没有意义）。
        assertThrows(com.tingyun.smartmistakebook.core.data.model.InvalidModelResponseException::class.java) {
            protocol.parseCompletion(
                body = envelopeOf("", stopReason = "end_turn"),
                input = input,
                modelVersion = "claude-test",
            )
        }
    }

    @Test
    fun streamDeltaReadsOnlyTextDeltas() {
        assertEquals(
            "你好",
            protocol.streamDelta("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}"""),
        )
        assertNull(protocol.streamDelta("""{"type":"message_start","message":{}}"""))
        assertNull(
            protocol.streamDelta(
                """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{}"}}""",
            ),
        )
        assertNull(protocol.streamDelta("not-json"))
    }

    @Test
    fun reconstructedBodyRebuildsATextBlockFromTheSseStream() {
        val rawSse = buildString {
            append("event: message_start\ndata: {\"type\":\"message_start\"}\n\n")
            append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}\n\n")
            append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"世界\"}}\n\n")
            append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        }

        val reconstructed = Json.parseToJsonElement(protocol.reconstructedBody(rawSse)).jsonObject
        assertEquals("你好世界", reconstructed["content"]!!.jsonArray[0].jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun truncatedStreamCarriesItsStopReasonAndParsesAsIncompleteOutput() {
        // AUD-14：流式里"为什么停下来"只有 message_delta 的 `delta.stop_reason` 一处声明。
        // 重建体丢了它，被 max_tokens 截断的一轮会落回 INVALID_RESPONSE（不可重试、重发会
        // 逐字复现），而不是可重试的 INCOMPLETE_OUTPUT——与 Chat Completions 带
        // finish_reason 的重建体同一条链。
        val rawSse = buildString {
            append("event: content_block_delta\n")
            append("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"题干（被切断\"}}\n\n")
            append("event: message_delta\n")
            append("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":12}}\n\n")
            append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        }

        val reconstructed = protocol.reconstructedBody(rawSse)

        assertTrue(
            "重建体必须原样带上终帧的 stop_reason：$reconstructed",
            reconstructed.contains("\"stop_reason\":\"max_tokens\""),
        )
        val thrown = assertThrows(
            com.tingyun.smartmistakebook.core.data.model.IncompleteModelOutputException::class.java,
        ) {
            protocol.parseCompletion(reconstructed, input, "claude-test")
        }
        assertTrue("截断原因必须写进归因", thrown.message.orEmpty().contains("max_tokens"))
    }

    @Test
    fun reasoningOnlyTruncatedStreamIsIncompleteOutputBeforeBlankTextIsJudged() {
        // KD-33 形态：思考烧光输出预算、正文一字节没吐，终帧说 stop_reason=max_tokens。
        // 重建体自己就要走 INCOMPLETE_OUTPUT（可重试），而不是没有正文的 INVALID_RESPONSE。
        assertThrows(
            com.tingyun.smartmistakebook.core.data.model.IncompleteModelOutputException::class.java,
        ) {
            protocol.reconstructedBody(
                "event: message_delta\n" +
                    "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}\n\n",
            )
        }
    }

    @Test
    fun completedStreamKeepsItsStopReasonAndStillParses() {
        // 反向：正常收尾（end_turn）的重建体照带标记，解析仍是正常任务输出。
        val rawSse = buildString {
            append("event: content_block_delta\n")
            append(
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\"," +
                    "\"text\":\"{\\\"questionMarkdown\\\":\\\"题干\\\",\\\"choices\\\":" +
                    "[{\\\"choiceId\\\":\\\"A\\\",\\\"markdown\\\":\\\"甲\\\"}," +
                    "{\\\"choiceId\\\":\\\"B\\\",\\\"markdown\\\":\\\"乙\\\"}]," +
                    "\\\"correctChoiceId\\\":\\\"A\\\"}\"}}\n\n",
            )
            append("event: message_delta\n")
            append("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n")
        }

        val reconstructed = protocol.reconstructedBody(rawSse)

        assertTrue(reconstructed.contains("\"stop_reason\":\"end_turn\""))
        val output = protocol.parseCompletion(reconstructed, input, "claude-test") as KnowledgeQuizOutput
        assertEquals("A", output.correctChoiceId)
    }

    @Test
    fun probeBodiesUseAnthropicShapesAndTheSharedTokens() {
        val structured = Json.parseToJsonElement(
            protocol.probeRequestBody("claude-test", ModelProbeKind.STRUCTURED),
        ).jsonObject
        assertEquals(ModelProbeSpec.STRUCTURED_INSTRUCTION, structured["system"]?.jsonPrimitive?.content)
        assertNull(structured["response_format"])
        assertTrue(structured.toString().contains(ModelProbeSpec.STRUCTURED_TOKEN))

        val imageProbe = Json.parseToJsonElement(
            protocol.probeRequestBody("claude-test", ModelProbeKind.IMAGE),
        ).jsonObject
        val blocks = imageProbe["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("image", blocks[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(imageProbe.toString().contains(ModelProbeSpec.SYNTHETIC_IMAGE_BASE64))

        assertThrows(IllegalStateException::class.java) {
            protocol.probeRequestBody("claude-test", ModelProbeKind.TOOLS)
        }
    }

    @Test
    fun probeResponseTextReadsContentBlocks() {
        assertEquals("Q7M2", protocol.probeResponseText(envelopeOf("Q7M2")))
        assertNull(protocol.probeResponseText("{}"))
    }

    @Test
    fun realHttpRoundTripParsesTheReply() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        envelopeOf(
                            """{"questionMarkdown":"题干","choices":[{"choiceId":"A","markdown":"甲"},{"choiceId":"B","markdown":"乙"}],"correctChoiceId":"B"}""",
                        ),
                    ),
            )
            server.start()
            val requestBody = protocol.requestBody("claude-test", input, emptyList(), stream = false, enableNativeTools = false)
            val request = Request.Builder()
                .url(server.url("/v1/messages"))
                .header("x-api-key", "sk-ant-test")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            val responseBody = OkHttpClient().newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                response.body.string()
            }
            val recorded = server.takeRequest()
            assertTrue(recorded.body.readUtf8().contains("\"system\""))

            val output = protocol.parseCompletion(responseBody, input, "claude-test") as KnowledgeQuizOutput
            assertEquals("B", output.correctChoiceId)
        }
    }

    private fun envelopeOf(text: String, stopReason: String? = null): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            stopReason?.let { put("stop_reason", it) }
            put(
                "content",
                buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                },
            )
        },
    )
}
