package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.model.wire.OpenAiChatCompletionsProtocol
import com.tingyun.smartmistakebook.core.model.ModelTextBudgetExceededException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-04a 的传输面：真实 `Call.awaitBoundedSseResponse` 读取路径上的工具增量聚合、
 * 超预算早停与跨 read 的 UTF-8 解码。
 */
class OpenAiSseToolCallStreamTest {

    @Test
    fun toolCallSseIsAggregatedIntoTheReconstructedMessage() = runBlocking {
        val body = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"查"),
            toolFrame(index = 0, arguments = "错题本\"}"),
            finishFrame("tool_calls"),
        )

        val response = SseDeliveringCall(body).awaitBoundedSseResponse(OpenAiChatCompletionsProtocol) {}

        assertEquals(200, response.statusCode)
        // 纯工具轮没有正文增量：实时回放通道保持 null，工具参数只走终态重建体。
        assertNull(response.streamChunks)
        val choice = ((Json.parseToJsonElement(response.body) as JsonObject)["choices"]!!.jsonArray)[0]
            .jsonObject
        assertEquals("tool_calls", choice["finish_reason"]!!.jsonPrimitive.content)
        val call = choice["message"]!!.jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("call_a", call["id"]!!.jsonPrimitive.content)
        assertEquals(
            "{\"rationale\":\"查错题本\"}",
            call["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun oversizedToolArgumentsAbortTheStreamInsteadOfAccumulatingThem() {
        // 超预算必须在读取/累积时中止本轮，不能等整段巨量参数进入重建体后才检查。
        val body = sse(
            toolFrame(
                index = 0,
                id = "call_a",
                name = "GENERATE_FIGURE",
                arguments = "{\"sourceJs\":\"" + "x".repeat(MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES) + "\"}",
            ),
            finishFrame("tool_calls"),
        )

        assertThrows(ModelTextBudgetExceededException::class.java) {
            runBlocking {
                SseDeliveringCall(body).awaitBoundedSseResponse(OpenAiChatCompletionsProtocol) {}
            }
        }
    }

    @Test
    fun aMalformedFrameAbortsTheStreamInsteadOfBeingSilentlySkipped() {
        val body = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":"),
            "data: not-json",
            toolFrame(index = 0, arguments = "\"a\"}"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            runBlocking {
                SseDeliveringCall(body).awaitBoundedSseResponse(OpenAiChatCompletionsProtocol) {}
            }
        }
    }

    @Test
    fun multiByteCharactersSplitAcrossReadsAreDecodedWithoutReplacement() {
        val text = "{\"rationale\":\"第一行\\n第二行 😀\"}"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val accumulator = Utf8SseAccumulator()
        var offset = 0
        var step = 1
        while (offset < bytes.size) {
            val end = (offset + step).coerceAtMost(bytes.size)
            accumulator.append(bytes.copyOfRange(offset, end), end - offset)
            offset = end
            step = (step % 4) + 1
        }

        assertEquals(text, accumulator.finish())
        assertTrue("U+FFFD 说明跨 read 的多字节字符被拆坏了", '�' !in accumulator.text)
    }

    private fun sse(vararg blocks: String): String = blocks.joinToString("\n\n") + "\n\n"

    private fun finishFrame(reason: String): String =
        "data: " + Json.encodeToString(
            buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("delta", buildJsonObject { })
                                put("finish_reason", reason)
                            },
                        )
                    },
                )
            },
        )

    private fun toolFrame(
        index: Int,
        id: String? = null,
        name: String? = null,
        arguments: String? = null,
    ): String {
        val chunk = buildJsonObject {
            put("index", index)
            id?.let { put("id", it) }
            put("type", "function")
            if (name != null || arguments != null) {
                put(
                    "function",
                    buildJsonObject {
                        name?.let { put("name", it) }
                        arguments?.let { put("arguments", it) }
                    },
                )
            }
        }
        return "data: " + Json.encodeToString(
            buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "delta",
                                    buildJsonObject {
                                        put("tool_calls", buildJsonArray { add(chunk) })
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    private class SseDeliveringCall(private val body: String) : Call {
        private val request = Request.Builder()
            .url("https://api.example.com/v1/chat/completions")
            .build()

        override fun request(): Request = request

        override fun execute(): Response = error("execute is unused")

        override fun enqueue(responseCallback: Callback) {
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("ok")
                .header("Content-Type", "text/event-stream")
                .body(body.toResponseBody())
                .build()
            responseCallback.onResponse(this, response)
        }

        override fun cancel() = Unit
        override fun isExecuted(): Boolean = true
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = this

        override fun addEventListener(eventListener: okhttp3.EventListener) = Unit
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }
}
