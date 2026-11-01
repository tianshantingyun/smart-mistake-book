package com.tingyun.smartmistakebook.core.data.model.wire

import com.tingyun.smartmistakebook.core.data.model.IncompleteModelOutputException
import com.tingyun.smartmistakebook.core.data.model.OpenAiModelTaskAdapters
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import com.tingyun.smartmistakebook.core.model.TutorVisualContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-11（视觉契约 §7.1 / 准入清单 :199）：Route B 三协议的 **sourceJs 保真负向套件**。
 *
 * 三协议（Anthropic Messages / Gemini generateContent / OpenAI Responses，三者
 * `supportsNativeTools = false` 恒走 Route B）× 7 向量（原始换行 / 反斜杠 / 中文 / emoji /
 * 空 content / 截断 / 取消）= **21 例**；"21 向量全绿才可声称 Provider 已支持"——
 * 不得只测 OpenAI 的两个 parser（契约 §7.1 原文）。
 *
 * 载体与断言口径：
 * - **sourceJs 在 Route B 的真实载体是模型输出信封里的工具请求**（`toolRequests[].program`，
 *   唯一解析点 `toTutorToolCall`）——前 4 类文本向量经 `parseCompletion` 与
 *   `reconstructedBody`（SSE 增量重建）两个方向都必须 **UTF-8 逐字节相等**；
 * - **空 content**：`"program": ""` 被 strict schema 本地兜底拒绝（`TutorToolCall` 构造层
 *   "program must not be blank"），绝不成为可执行程序；流里的**空 delta 帧**则不得破坏
 *   重建的逐字节保真（契约"各自 normalize/reconstructedBody"两面各钉一半）；
 * - **截断**：终帧声明（stop_reason=max_tokens / finishReason=MAX_TOKENS /
 *   status=incomplete）必须原样带进重建体并以可重试的 INCOMPLETE_OUTPUT 显式拒绝；
 * - **取消**：流被切断（无终帧）时，半段 JSON 由 strict 解析层整条拒绝——半段 program
 *   绝不进入执行。
 *
 * **请求半边（批次 38 接线，A-11 载体落点）**：`TutorVisualContext → prompt 渲染`已落
 * `OpenAiModelTaskAdapters`（Plan/Respond 两半的 toolLoopPromptSuffix；null 时零字节追加，
 * prompt 与接线前逐字节相同）。请求侧逐字节断言按**同一张向量表**补：三协议 `requestBody`
 * 出线后从 prompt 文本里按 `===TUTOR_VISUAL_PROGRAM_BEGIN===/END` 信标取区域，与向量
 * program 做 UTF-8 逐字节比较；Respond 半边、有界字节声明与 null 不落块各有专项。
 */
class ModelWireProtocolSourceJsFidelityTest {

    // ---- 7 向量（清单 :199 行义口径）----

    /** 前 4 类：信封必须逐字节保真的文本形状（第 5 类"空 content"单测，见各协议空载体例）。 */
    private val textVectors: List<Pair<String, String>> = listOf(
        "原始换行" to
            "const visual = {\n  prepare() {\n    return [\"axis\"];\n  },\n  render() {},\n};\n",
        "反斜杠" to
            "const p = \"a\\\\b\";\nconst re = /x\\\\y/g;\nconst win = \"C:\\\\temp\";\n",
        "中文" to
            "const 标题 = \"抛物线 y=x² 与判别式\"; // 二次函数图像：开口方向与顶点",
        "emoji" to
            "const label = \"\uD83D\uDCCA 函数图像 \uD83D\uDE00\uD83C\uDFAF\"; // 成对代理",
    )

    private val cancellationVector = textVectors.first()

    // ---- Anthropic Messages ----

    @Test
    fun anthropicProgramCarrierSurvivesTheTextVectorsByteForByte() {
        textVectors.forEach { (label, program) ->
            val output = AnthropicMessagesProtocol.parseCompletion(
                body = anthropicEnvelope(toolRequestsText(program)),
                input = planInput(),
                modelVersion = "test-model",
            ) as TutorToolRequestsOutput

            assertEquals("[$label] method=JS", FigureGenerationMethod.JS, output.calls.single().figureMethod)
            assertUtf8BytesEqual(
                "[$label] sourceJs",
                program,
                requireNotNull(output.calls.single().figureProgram),
            )
        }
    }

    @Test
    fun anthropicEmptyProgramContentIsRefusedByTheStrictSchemaAndNeverBecomesAProgram() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            AnthropicMessagesProtocol.parseCompletion(
                body = anthropicEnvelope(toolRequestsText("")),
                input = planInput(),
                modelVersion = "test-model",
            )
        }

        assertTrue(
            "空 program 必须由构造层拒绝：${thrown.message}",
            thrown.message.orEmpty().contains("program must not be blank"),
        )
    }

    @Test
    fun anthropicEmptyContentDeltasDoNotCorruptTheReconstructedProgram() {
        val program = textVectors.first().second
        val text = toolRequestsText(program)
        val rawSse = listOf("", text.take(text.length / 2), "", text.drop(text.length / 2), "")
            .joinToString(separator = "") { chunk -> anthropicDeltaFrame(chunk) }

        val output = AnthropicMessagesProtocol.parseCompletion(
            AnthropicMessagesProtocol.reconstructedBody(rawSse),
            planInput(),
            "test-model",
        ) as TutorToolRequestsOutput

        assertUtf8BytesEqual("empty-delta", program, requireNotNull(output.calls.single().figureProgram))
    }

    @Test
    fun anthropicTruncatedProgramStreamIsExplicitlyIncompleteAndNeverParses() {
        val text = toolRequestsText(textVectors.first().second)
        val rawSse = anthropicDeltaFrame(cutInsideProgram(text)) +
            "event: message_delta\n" +
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}\n\n"

        val reconstructed = AnthropicMessagesProtocol.reconstructedBody(rawSse)

        assertTrue(
            "截断声明必须原样进重建体：$reconstructed",
            reconstructed.contains("\"stop_reason\":\"max_tokens\""),
        )
        val thrown = assertThrows(IncompleteModelOutputException::class.java) {
            AnthropicMessagesProtocol.parseCompletion(reconstructed, planInput(), "test-model")
        }
        assertTrue(thrown.message.orEmpty().contains("max_tokens"))
    }

    @Test
    fun anthropicCutStreamWithoutATerminalNeverParsesIntoAnExecutableProgram() {
        val text = toolRequestsText(cancellationVector.second)
        val reconstructed = AnthropicMessagesProtocol.reconstructedBody(
            anthropicDeltaFrame(cutInsideProgram(text)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AnthropicMessagesProtocol.parseCompletion(reconstructed, planInput(), "test-model")
        }
    }

    // ---- Gemini generateContent ----

    @Test
    fun geminiProgramCarrierSurvivesTheTextVectorsByteForByte() {
        textVectors.forEach { (label, program) ->
            val output = GeminiGenerateContentProtocol.parseCompletion(
                body = geminiEnvelope(toolRequestsText(program)),
                input = planInput(),
                modelVersion = "test-model",
            ) as TutorToolRequestsOutput

            assertEquals("[$label] method=JS", FigureGenerationMethod.JS, output.calls.single().figureMethod)
            assertUtf8BytesEqual(
                "[$label] sourceJs",
                program,
                requireNotNull(output.calls.single().figureProgram),
            )
        }
    }

    @Test
    fun geminiEmptyProgramContentIsRefusedByTheStrictSchemaAndNeverBecomesAProgram() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            GeminiGenerateContentProtocol.parseCompletion(
                body = geminiEnvelope(toolRequestsText("")),
                input = planInput(),
                modelVersion = "test-model",
            )
        }

        assertTrue(
            "空 program 必须由构造层拒绝：${thrown.message}",
            thrown.message.orEmpty().contains("program must not be blank"),
        )
    }

    @Test
    fun geminiEmptyContentDeltasDoNotCorruptTheReconstructedProgram() {
        val program = textVectors.first().second
        val text = toolRequestsText(program)
        val rawSse = listOf("", text.take(text.length / 2), "", text.drop(text.length / 2), "")
            .joinToString(separator = "") { chunk -> geminiDeltaFrame(chunk) }

        val output = GeminiGenerateContentProtocol.parseCompletion(
            GeminiGenerateContentProtocol.reconstructedBody(rawSse),
            planInput(),
            "test-model",
        ) as TutorToolRequestsOutput

        assertUtf8BytesEqual("empty-delta", program, requireNotNull(output.calls.single().figureProgram))
    }

    @Test
    fun geminiTruncatedProgramStreamIsExplicitlyIncompleteAndNeverParses() {
        val text = toolRequestsText(textVectors.first().second)
        val rawSse = geminiDeltaFrame(cutInsideProgram(text)) +
            "data: {\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}\n\n"

        val reconstructed = GeminiGenerateContentProtocol.reconstructedBody(rawSse)

        assertTrue(
            "截断声明必须原样进重建体：$reconstructed",
            reconstructed.contains("\"finishReason\":\"MAX_TOKENS\""),
        )
        val thrown = assertThrows(IncompleteModelOutputException::class.java) {
            GeminiGenerateContentProtocol.parseCompletion(reconstructed, planInput(), "test-model")
        }
        assertTrue(thrown.message.orEmpty().contains("MAX_TOKENS"))
    }

    @Test
    fun geminiCutStreamWithoutATerminalNeverParsesIntoAnExecutableProgram() {
        val text = toolRequestsText(cancellationVector.second)
        val reconstructed = GeminiGenerateContentProtocol.reconstructedBody(
            geminiDeltaFrame(cutInsideProgram(text)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            GeminiGenerateContentProtocol.parseCompletion(reconstructed, planInput(), "test-model")
        }
    }

    // ---- OpenAI Responses ----

    @Test
    fun responsesProgramCarrierSurvivesTheTextVectorsByteForByte() {
        textVectors.forEach { (label, program) ->
            val output = OpenAiResponsesProtocol.parseCompletion(
                body = responsesEnvelope(toolRequestsText(program)),
                input = planInput(),
                modelVersion = "test-model",
            ) as TutorToolRequestsOutput

            assertEquals("[$label] method=JS", FigureGenerationMethod.JS, output.calls.single().figureMethod)
            assertUtf8BytesEqual(
                "[$label] sourceJs",
                program,
                requireNotNull(output.calls.single().figureProgram),
            )
        }
    }

    @Test
    fun responsesEmptyProgramContentIsRefusedByTheStrictSchemaAndNeverBecomesAProgram() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesProtocol.parseCompletion(
                body = responsesEnvelope(toolRequestsText("")),
                input = planInput(),
                modelVersion = "test-model",
            )
        }

        assertTrue(
            "空 program 必须由构造层拒绝：${thrown.message}",
            thrown.message.orEmpty().contains("program must not be blank"),
        )
    }

    @Test
    fun responsesEmptyContentDeltasDoNotCorruptTheReconstructedProgram() {
        val program = textVectors.first().second
        val text = toolRequestsText(program)
        val rawSse = listOf("", text.take(text.length / 2), "", text.drop(text.length / 2), "")
            .joinToString(separator = "") { chunk -> responsesDeltaFrame(chunk) }

        val output = OpenAiResponsesProtocol.parseCompletion(
            OpenAiResponsesProtocol.reconstructedBody(rawSse),
            planInput(),
            "test-model",
        ) as TutorToolRequestsOutput

        assertUtf8BytesEqual("empty-delta", program, requireNotNull(output.calls.single().figureProgram))
    }

    @Test
    fun responsesTruncatedProgramStreamIsExplicitlyIncompleteAndNeverParses() {
        val text = toolRequestsText(textVectors.first().second)
        val rawSse = responsesDeltaFrame(cutInsideProgram(text)) +
            "data: {\"type\":\"response.incomplete\"," +
            "\"response\":{\"status\":\"incomplete\"," +
            "\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}\n\n"

        val reconstructed = OpenAiResponsesProtocol.reconstructedBody(rawSse)

        assertTrue(
            "截断声明必须原样进重建体：$reconstructed",
            reconstructed.contains("\"status\":\"incomplete\""),
        )
        val thrown = assertThrows(IncompleteModelOutputException::class.java) {
            OpenAiResponsesProtocol.parseCompletion(reconstructed, planInput(), "test-model")
        }
        assertTrue(thrown.message.orEmpty().contains("incomplete"))
    }

    @Test
    fun responsesCutStreamWithoutATerminalNeverParsesIntoAnExecutableProgram() {
        val text = toolRequestsText(cancellationVector.second)
        val reconstructed = OpenAiResponsesProtocol.reconstructedBody(
            responsesDeltaFrame(cutInsideProgram(text)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesProtocol.parseCompletion(reconstructed, planInput(), "test-model")
        }
    }

    // ---- 请求半边（A-11 载体批次 38 接线后的逐字节断言，同一张向量表）----

    @Test
    fun anthropicRequestSideCarrierSurvivesTheTextVectorsByteForByte() {
        requestSideVectors(::anthropicRequestBody)
    }

    @Test
    fun geminiRequestSideCarrierSurvivesTheTextVectorsByteForByte() {
        requestSideVectors(::geminiRequestBody)
    }

    @Test
    fun responsesRequestSideCarrierSurvivesTheTextVectorsByteForByte() {
        requestSideVectors(::responsesRequestBody)
    }

    /** Respond 半边走同一渲染点（toolLoopPromptSuffix 的 Respond 调用位），逐字节同口径。 */
    @Test
    fun theRespondHalfRendersTheSameCarrierByteForByte() {
        textVectors.forEach { (label, program) ->
            val body = anthropicRequestBody(respondInput(visualContext(program)))

            assertUtf8BytesEqual(
                "[$label] request-side (respond) sourceJs",
                program,
                carriedSourceJs(body),
            )
        }
    }

    @Test
    fun aNullVisualContextLandsNoCarrierBlockOnTheWire() {
        // 新可空字段为 null 时不出现新块（prompt 面的"新键缺席"口径）：
        // 现行生产全部 null（A-07 零构造），出线请求必须与接线前逐字节同形。
        val body = anthropicRequestBody(planInput())

        assertFalse(body.contains(OpenAiModelTaskAdapters.VISUAL_CONTEXT_HEADER))
        assertFalse(body.contains(OpenAiModelTaskAdapters.VISUAL_SOURCE_BEGIN))
        assertFalse(body.contains(OpenAiModelTaskAdapters.VISUAL_STATE_BEGIN))
    }

    @Test
    fun theCarrierRendersStateDiagnosticsCapabilityVersionAndBoundedByteCounts() {
        val program = "const visual = {};"
        val state = "{\"step\":1}"
        val context = TutorVisualContext(
            capabilityVersion = FigureGenerationPolicyVersion.VALUE,
            sourceJs = program,
            stateJson = state,
            diagnostics = "上一份程序在 measure 阶段被截断",
        )
        val prompt = carriedPrompt(anthropicRequestBody(planInput(context)))

        assertTrue(
            "能力版本必须原样渲染：$prompt",
            prompt.contains("capabilityVersion：${FigureGenerationPolicyVersion.VALUE}"),
        )
        assertUtf8BytesEqual("state region", state, carriedRegion(prompt, STATE_BEGIN, STATE_END))
        assertTrue(
            "state 有界字节声明必须与实际一致：$prompt",
            prompt.contains("共 ${state.toByteArray(Charsets.UTF_8).size} UTF-8 字节"),
        )
        assertTrue(
            "截断诊断必须原样渲染：$prompt",
            prompt.contains("上一份程序在 measure 阶段被截断"),
        )
    }

    /** 三协议共用的请求半边向量循环：出线请求里 program 区域 UTF-8 逐字节相等。 */
    private fun requestSideVectors(requestBody: (TutorPlanInput) -> String) {
        textVectors.forEach { (label, program) ->
            val body = requestBody(planInput(visualContext(program)))
            val prompt = carriedPrompt(body)

            assertUtf8BytesEqual("[$label] request-side sourceJs", program, carriedSourceJs(body))
            assertTrue(
                "[$label] 有界字节声明必须与实际一致",
                prompt.contains("共 ${program.toByteArray(Charsets.UTF_8).size} UTF-8 字节"),
            )
        }
    }

    private fun visualContext(program: String): TutorVisualContext =
        TutorVisualContext(
            capabilityVersion = FigureGenerationPolicyVersion.VALUE,
            sourceJs = program,
        )

    private fun anthropicRequestBody(input: ModelTaskInput): String =
        AnthropicMessagesProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            stream = false,
            enableNativeTools = false,
        )

    private fun geminiRequestBody(input: ModelTaskInput): String =
        GeminiGenerateContentProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            stream = false,
            enableNativeTools = false,
        )

    private fun responsesRequestBody(input: ModelTaskInput): String =
        OpenAiResponsesProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            stream = false,
            enableNativeTools = false,
        )

    /** Respond 半边夹具：修复回合形状（带 visualContext 的 Respond 输入）。 */
    private fun respondInput(context: TutorVisualContext? = null) = TutorRespondInput(
        sessionId = "session-sourcejs-respond",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-respond-fidelity",
            blocks = listOf(ContentBlock.Paragraph("stem", "画出抛物线的图像")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        responseOrdinal = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        studentMessage = "上一张图挂了，帮我修一下",
        priorMessages = emptyList(),
        requestedMove = null,
        visualContext = context,
    )

    /** 递归收集请求体 JSON 里的全部字符串值，取携带视觉上下文信标的那份 prompt 文本。 */
    private fun carriedPrompt(requestBody: String): String =
        jsonStringValues(Json.parseToJsonElement(requestBody))
            .single { it.contains(OpenAiModelTaskAdapters.VISUAL_SOURCE_BEGIN) }

    private fun carriedSourceJs(requestBody: String): String =
        carriedRegion(carriedPrompt(requestBody), SOURCE_BEGIN, SOURCE_END)

    private fun carriedRegion(prompt: String, begin: String, end: String): String {
        val beginIndex = prompt.indexOf(begin)
        check(beginIndex >= 0) { "carrier begin marker lost" }
        val start = beginIndex + begin.length + 1
        val stop = prompt.indexOf("\n$end", start)
        check(stop >= 0) { "carrier end marker lost" }
        return prompt.substring(start, stop)
    }

    private fun jsonStringValues(element: kotlinx.serialization.json.JsonElement): List<String> =
        when (element) {
            is kotlinx.serialization.json.JsonPrimitive ->
                if (element.isString) listOf(element.content) else emptyList()
            is kotlinx.serialization.json.JsonObject -> element.values.flatMap { jsonStringValues(it) }
            is kotlinx.serialization.json.JsonArray -> element.flatMap { jsonStringValues(it) }
        }

    // ---- 夹具 ----

    /** Route B 工具轮输出信封：一条 method=JS 的 GENERATE_FIGURE 请求，program=载荷。 */
    private fun toolRequestsText(program: String): String = buildJsonObject {
        put(
            "intentDecision",
            buildJsonObject {
                put("intent", "CURRENT_QUESTION_HELP")
                put("confidence", 0.9)
                put("explicitActionRequest", false)
                put("memoryPreference", "UNCHANGED")
                put("requestedLocalCapability", "NONE")
                put("lookupTerms", buildJsonArray { })
            },
        )
        put(
            "toolRequests",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("tool", "GENERATE_FIGURE")
                        put("rationale", "讲题需要过程图")
                        put("kind", "GENERATE_PROCESS")
                        put("description", "画一张抛物线示意图")
                        put("method", "JS")
                        put("program", program)
                    },
                )
            },
        )
    }.toString()

    /** 在 `program` 字符串字面量**中间**切断（模拟取消打断源码传输）——半段 JSON 必然非法。 */
    private fun cutInsideProgram(text: String): String {
        val anchor = "\"program\":\""
        val anchorStart = checkNotNull(text.indexOf(anchor)) { "fixture lost its program anchor" }
        return text.take(anchorStart + anchor.length + textVectors.first().second.length / 2)
    }

    private fun anthropicEnvelope(text: String): String = Json.encodeToString(
        buildJsonObject {
            put(
                "content",
                buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) },
            )
        },
    )

    private fun anthropicDeltaFrame(chunk: String): String =
        "event: content_block_delta\n" +
            "data: " + Json.encodeToString(
                buildJsonObject {
                    put("type", "content_block_delta")
                    put(
                        "delta",
                        buildJsonObject { put("type", "text_delta"); put("text", chunk) },
                    )
                },
            ) + "\n\n"

    private fun geminiEnvelope(text: String, finishReason: String? = null): String = Json.encodeToString(
        buildJsonObject {
            put(
                "candidates",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            finishReason?.let { put("finishReason", it) }
                            put(
                                "content",
                                buildJsonObject {
                                    put(
                                        "parts",
                                        buildJsonArray {
                                            add(buildJsonObject { put("text", text) })
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

    private fun geminiDeltaFrame(chunk: String): String =
        "data: " + Json.encodeToString(
            buildJsonObject {
                put(
                    "candidates",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "content",
                                    buildJsonObject {
                                        put(
                                            "parts",
                                            buildJsonArray {
                                                add(buildJsonObject { put("text", chunk) })
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        ) + "\n\n"

    private fun responsesEnvelope(text: String): String = Json.encodeToString(
        buildJsonObject {
            put(
                "output",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "message")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "output_text")
                                            put("text", text)
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

    private fun responsesDeltaFrame(chunk: String): String =
        "data: " + Json.encodeToString(
            buildJsonObject {
                put("type", "response.output_text.delta")
                put("delta", chunk)
            },
        ) + "\n\n"

    private fun planInput(context: TutorVisualContext? = null) = TutorPlanInput(
        sessionId = "session-sourcejs-fidelity",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-fidelity",
            blocks = listOf(ContentBlock.Paragraph("stem", "画出抛物线的图像")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        toolDeclarations = listOf(TutorToolName.GENERATE_FIGURE),
        visualContext = context,
    )

    /** 信标别名（与 [OpenAiModelTaskAdapters] 的载体常量同一来源）。 */
    private val SOURCE_BEGIN = OpenAiModelTaskAdapters.VISUAL_SOURCE_BEGIN
    private val SOURCE_END = OpenAiModelTaskAdapters.VISUAL_SOURCE_END
    private val STATE_BEGIN = OpenAiModelTaskAdapters.VISUAL_STATE_BEGIN
    private val STATE_END = OpenAiModelTaskAdapters.VISUAL_STATE_END

    /** 清单口径：UTF-8 逐字节相等（不是 equals 的字面复述——字节级断言防规范化漂移）。 */
    private fun assertUtf8BytesEqual(label: String, expected: String, actual: String) {
        assertTrue(
            "$label must be byte-identical over UTF-8",
            expected.toByteArray(Charsets.UTF_8).contentEquals(actual.toByteArray(Charsets.UTF_8)),
        )
        assertEquals(label, expected, actual)
    }
}
