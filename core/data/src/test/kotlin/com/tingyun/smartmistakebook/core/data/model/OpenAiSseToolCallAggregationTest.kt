package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelTextBudgetExceededException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-04a：SSE 终态门。
 *
 * `reconstructedChatCompletion` 此前只拼接 `delta.content`，原生工具调用参数在流式回合里
 * 直接消失。这一组测试锁住三件事：按 index 聚合、终态声明必须是 `finish_reason=tool_calls`、
 * 任何截断/超预算/形状冲突都 fail-closed。
 */
class OpenAiSseToolCallAggregationTest {

    @Test
    fun aggregatesInterleavedToolCallsByIndexAndCarriesTheTerminalFinishReason() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"先查"),
            toolFrame(index = 1, id = "call_b", name = "MASTERY_UPDATE", arguments = "{\"rationale\":\"记下"),
            toolFrame(index = 0, arguments = "错题本\",\"terms\":[\"t\"]}"),
            toolFrame(index = 1, arguments = "这一条\"}"),
            finishFrame("tool_calls"),
        )

        val choice = firstChoice(OpenAiSse.reconstructedChatCompletion(raw))

        assertEquals("tool_calls", choice["finish_reason"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, choice["message"]!!.jsonObject["content"])
        val calls = choice["message"]!!.jsonObject["tool_calls"]!!.jsonArray
        assertEquals(2, calls.size)
        assertEquals("call_a", calls[0].jsonObject.string("id"))
        assertEquals("LOOKUP", calls[0].jsonObject.function().string("name"))
        assertEquals(
            "{\"rationale\":\"先查错题本\",\"terms\":[\"t\"]}",
            calls[0].jsonObject.function().string("arguments"),
        )
        assertEquals("call_b", calls[1].jsonObject.string("id"))
        assertEquals(
            "{\"rationale\":\"记下这一条\"}",
            calls[1].jsonObject.function().string("arguments"),
        )
    }

    @Test
    fun repeatsOfTheSameIdAndNameAreAcceptedAndArgumentsStillAppend() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a"),
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "\"}"),
            finishFrame("tool_calls"),
        )

        val call = firstCall(OpenAiSse.reconstructedChatCompletion(raw))
        assertEquals("{\"rationale\":\"a\"}", call.function().string("arguments"))
    }

    @Test
    fun argumentsWithNewlinesBackslashesCjkAndEmojiSurviveVerbatim() {
        val expected = "{\"rationale\":\"第一行\\n第二行\\\\路径 😀\"}"
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = expected.substring(0, 9)),
            toolFrame(index = 0, arguments = expected.substring(9)),
            finishFrame("tool_calls"),
        )

        val call = firstCall(OpenAiSse.reconstructedChatCompletion(raw))
        assertEquals(expected, call.function().string("arguments"))
    }

    @Test
    fun aToolOnlyRoundWithUsageChunksAndACleanEofIsAccepted() {
        // usage chunk（choices: []）与没有 [DONE]、只有干净 EOF 都是合法终态；终帧已经声明 tool_calls。
        val raw = listOf(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a\"}"),
            finishFrame("tool_calls"),
            "data: {\"choices\":[],\"usage\":{\"total_tokens\":10}}",
        ).joinToString("\n\n")

        val call = firstCall(OpenAiSse.reconstructedChatCompletion(raw))
        assertEquals("call_a", call.string("id"))
    }

    @Test
    fun conflictingIdsForTheSameIndexFailClosed() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP"),
            toolFrame(index = 0, id = "call_b"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun conflictingNamesForTheSameIndexFailClosed() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP"),
            toolFrame(index = 0, name = "MASTERY_UPDATE"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun aNonFunctionToolCallTypeFailsClosed() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", type = "text", name = "LOOKUP"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun aMissingToolCallIndexFailsClosed() {
        val raw = sse(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_a\"," +
                "\"type\":\"function\",\"function\":{\"name\":\"LOOKUP\"}}]}}]}",
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun indicesOutsideTheBoundedCallCountFailClosed() {
        val raw = sse(
            toolFrame(index = MAX_STREAMED_TOOL_CALLS, id = "call_a", name = "LOOKUP"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun nonContiguousIndicesFailClosed() {
        // index 1 先出现、index 0 从未出现：重建出的 tool_calls 会带空洞，拒绝而不是补位。
        val raw = sse(
            toolFrame(index = 1, id = "call_b", name = "LOOKUP", arguments = "{\"rationale\":\"b\"}"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun toolCallsWithoutATerminalFinishReasonAreIncompleteNotInvalid() {
        // 断连/取消/EOF：上游从未声明终态。这是"这一次没写完"（可重试），不是形状错误。
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a\"}"),
        )

        assertThrows(IncompleteModelOutputException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun aTruncatingFinishReasonWithToolCallsIsIncomplete() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a\""),
            finishFrame("length"),
        )

        assertThrows(IncompleteModelOutputException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun aStopFinishReasonWithToolCallsIsRejected() {
        // 规范里工具轮的终态是 tool_calls；stop + tool_calls 是 provider 偏差，不猜它已完成。
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a\"}"),
            finishFrame("stop"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun toolCallsDeclaredByFinishReasonButMissingFromTheStreamAreRejected() {
        val raw = sse(finishFrame("tool_calls"))

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun emptyArgumentsAreRejectedInsteadOfTreatedAsAnEmptyObject() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = ""),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun invalidArgumentJsonIsRejected() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a\""),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun multipleJsonValuesInArgumentsAreRejected() {
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"a\":1} {\"b\":2}"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun toolCallFragmentsAfterTheTerminalFrameAreRejected() {
        // 终帧之后仍有参数片段：说明宣告终态时参数并不完整，不能把它拼进可执行调用。
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":\"a\""),
            finishFrame("tool_calls"),
            toolFrame(index = 0, arguments = "}"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun aMalformedDataFrameInAToolRoundFailsClosed() {
        // 坏帧可能是丢掉的那一段工具参数；静默跳过会让剩余参数看起来仍然完整。
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"rationale\":"),
            "data: not-json",
            toolFrame(index = 0, arguments = "\"a\"}"),
            finishFrame("tool_calls"),
        )

        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun aDecodedSourceJsOver64KiBIsABudgetFailure() {
        val arguments = Json.encodeToString(
            buildJsonObject {
                put("rationale", "画图")
                put("sourceJs", "x".repeat(MAX_DECODED_TOOL_SOURCE_UTF8_BYTES + 1))
            },
        )
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "GENERATE_FIGURE", arguments = arguments),
            finishFrame("tool_calls"),
        )

        assertThrows(ModelTextBudgetExceededException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun totalStreamedToolArgumentsOver512KiBIsABudgetFailure() {
        val arguments = "{\"rationale\":\"" + "x".repeat(MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES) + "\"}"
        val raw = sse(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = arguments),
            finishFrame("tool_calls"),
        )

        assertThrows(ModelTextBudgetExceededException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun nonStreamingToolPayloadsOver512KiBAreAlsoRejected() {
        // stream=false 的降级路径不能成为绕过预算的后门。
        val arguments = "{\"rationale\":\"" + "x".repeat(MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES) + "\"}"
        val body = Json.encodeToString(
            buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "message",
                                    buildJsonObject {
                                        put("content", JsonNull)
                                        put(
                                            "tool_calls",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("id", "call_a")
                                                        put("type", "function")
                                                        put(
                                                            "function",
                                                            buildJsonObject {
                                                                put("name", "LOOKUP")
                                                                put("arguments", arguments)
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        assertThrows(ModelTextBudgetExceededException::class.java) {
            OpenAiSse.requireToolCallPayloadBudget(body)
        }
    }

    @Test
    fun anIncrementalBudgetObserverAbortsAsSoonAsTheCapIsCrossed() {
        val budget = OpenAiSse.StreamedToolCallBudget()
        // 生产路径传给 observe 的是 eventDataBlocksTerminated 剥掉 `data: ` 之后的帧体
        // （OpenAiModelTransport.readSse 的 onFrame）。这里必须按同一形状喂入；直接传
        // toolFrame 的完整 SSE 行会被 choiceOf 当成非法 JSON（不是聚合器的语义）。
        budget.observe(
            toolFrame(index = 0, id = "call_a", name = "LOOKUP", arguments = "{\"a\":1}")
                .removePrefix("data: "),
        )

        assertThrows(ModelTextBudgetExceededException::class.java) {
            budget.observe(
                toolFrame(
                    index = 0,
                    arguments = "\"" + "x".repeat(MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES) + "\"",
                ).removePrefix("data: "),
            )
        }
    }

    @Test
    fun aContentOnlyRoundKeepsItsExistingShape() {
        val raw = sse(
            contentFrame("{\"ok\":"),
            contentFrame("true}"),
            finishFrame("stop"),
        )

        val choice = firstChoice(OpenAiSse.reconstructedChatCompletion(raw))

        assertEquals("{\"ok\":true}", choice["message"]!!.jsonObject.string("content"))
        assertEquals("stop", choice["finish_reason"]!!.jsonPrimitive.content)
        assertTrue(choice["message"]!!.jsonObject["tool_calls"] == null)
    }

    private fun firstChoice(reconstructed: String): JsonObject {
        val root = Json.parseToJsonElement(reconstructed) as JsonObject
        return (root["choices"] as JsonArray)[0].jsonObject
    }

    private fun firstCall(reconstructed: String): JsonObject =
        firstChoice(reconstructed)["message"]!!.jsonObject["tool_calls"]!!.jsonArray[0].jsonObject

    private fun JsonObject.function(): JsonObject = this["function"]!!.jsonObject
    private fun JsonObject.string(name: String): String =
        this[name]!!.jsonPrimitive.content

    private fun sse(vararg blocks: String): String = blocks.joinToString("\n\n") + "\n\n"

    private fun contentFrame(content: String): String =
        "data: " + Json.encodeToString(
            buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("delta", buildJsonObject { put("content", content) })
                                put("finish_reason", JsonNull)
                            },
                        )
                    },
                )
            },
        )

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
        type: String? = "function",
        name: String? = null,
        arguments: String? = null,
    ): String {
        val chunk = buildJsonObject {
            put("index", index)
            id?.let { put("id", it) }
            type?.let { put("type", it) }
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
}
