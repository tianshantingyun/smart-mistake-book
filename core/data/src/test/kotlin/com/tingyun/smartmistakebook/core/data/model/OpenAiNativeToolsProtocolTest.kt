package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorIntentDecision
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route A（OpenAI 原生 tools 协议）双轨适配层测试（spec model-intent-routing §3 wire）。
 *
 * 双轨 = 响应驱动：请求带原生 `tools` schema 时，provider 支持则回 `assistant.tool_calls`，
 * 不支持则忽略 tools 字段、照常在 json_object 信封里吐 JSON——两条路在 parseResponse 分轨。
 * Route A 只替换"模型如何表达工具申请"这一段 wire；授权/执行/回填/多轮由 repository
 * 既有工具环接管（TutorToolRequestsOutput 对两路同形）。能力门控默认关（enableNativeTools=false
 * = Route B 现状）；本测试验证协议层就绪后两路都正确。
 */
class OpenAiNativeToolsProtocolTest {

    private fun respondInput() = TutorRespondInput(
        sessionId = "session-1",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-1",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        responseOrdinal = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        studentMessage = "这一步怎么来的？",
        priorMessages = emptyList(),
        requestedMove = null,
        toolDeclarations = listOf(
            TutorToolName.KNOWLEDGE_READ,
            TutorToolName.NOTEBOOK_READ,
            TutorToolName.MASTERY_READ,
            TutorToolName.MASTERY_UPDATE,
        ),
    )

    private fun lobbyInput() = TutorLobbyInput(
        conversationId = "conv-1",
        messageOrdinal = 1,
        studentMessage = "帮我看看错题本里有没有二次函数",
        priorMessages = emptyList(),
        toolDeclarations = listOf(TutorToolName.NOTEBOOK_READ),
    )

    private fun toolCallEnvelope(nativeBody: String): String = """
        {"choices":[{"message":$nativeBody}]}
    """.trimIndent()

    @Test
    fun nativeToolsAdvertisedOnlyWhenEnabledAndDeclared() {
        // enableNativeTools=true + Respond 声明工具 → 请求含 tools schema
        val withTools = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = respondInput(),
            images = emptyList(),
            enableNativeTools = true,
        )
        assertTrue("应附原生 tools 声明", withTools.contains("\"tools\""))
        assertTrue("tools 应含 MASTERY_UPDATE 的 function schema", withTools.contains("\"name\":\"MASTERY_UPDATE\""))
        assertTrue("schema 应含 direction 枚举", withTools.contains("\"direction\""))
        assertTrue("schema 应 strict（additionalProperties:false）", withTools.contains("\"additionalProperties\":false"))

        // enableNativeTools=false → 现状（Route B），无 tools 字段
        val noTools = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = respondInput(),
            images = emptyList(),
        )
        assertFalse("默认不附原生 tools（Route B 现状）", noTools.contains("\"tools\""))
    }

    @Test
    fun masteryUpdateSchemaIsStrictWithMandatorySemanticFields() {
        // model 层 TutorToolCall 契约强制 MASTERY_UPDATE 必有 direction+understanding，
        // 故 strict schema（additionalProperties:false）的 required 必须覆盖全部 5 个字段，
        // 否则真实 strict provider 会判 schema 非法。
        val withTools = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = respondInput(),
            images = emptyList(),
            enableNativeTools = true,
        )
        // 该 body 含 4 个工具 schema，须切到 MASTERY_UPDATE 那个 function 块再取 required
        val masteryBlock = functionBlock(withTools, "MASTERY_UPDATE")
        val required = functionRequired(masteryBlock)

        assertTrue(
            "MASTERY_UPDATE strict schema 的 required 应含 direction+understanding+confidence（实得：$required）",
            required.contains("\"direction\"") &&
                required.contains("\"understanding\"") &&
                required.contains("\"confidence\"") &&
                required.contains("\"terms\"") &&
                required.contains("\"rationale\""),
        )
    }

    @Test
    fun notebookReadNativeDescriptionIncludesPagingAndEmptyTerms() {
        val body = OpenAiModelProtocol.requestBody("test-model", respondInput(), emptyList(), enableNativeTools = true)
        val notebook = functionBlock(body, "NOTEBOOK_READ")
        assertTrue(notebook.contains("terms 可空"))
        assertTrue(notebook.contains("offset"))
        assertTrue(notebook.contains("limit"))
    }

    @Test
    fun notebookWriteSchemaAndBothParsersAcceptEmptyTerms() {
        val input = respondInput().copy(toolDeclarations = listOf(TutorToolName.NOTEBOOK_WRITE))
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            enableNativeTools = true,
        )
        val schema = functionBlock(body, "NOTEBOOK_WRITE")
        assertTrue(schema.contains("\"minItems\":0"))
        assertTrue(schema.contains("可留空"))
        val native = toolCallEnvelope(
            """{"content":null,"tool_calls":[{"id":"save-1","type":"function",
                "function":{"name":"NOTEBOOK_WRITE","arguments":"{\"terms\":[],\"rationale\":\"保存本轮题目\"}"}}]}""",
        )
        val nativeOutput = OpenAiModelProtocol.parseResponse(native, input, "model") as TutorToolRequestsOutput
        assertTrue(nativeOutput.calls.single().terms.isEmpty())
        val envelope = toolCallEnvelope(
            kotlinx.serialization.json.buildJsonObject {
                put("content", kotlinx.serialization.json.JsonPrimitive("""{"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.9,"explicitActionRequest":true,"memoryPreference":"UNCHANGED","requestedLocalCapability":"NONE","lookupTerms":[]},"toolRequests":[{"tool":"NOTEBOOK_WRITE","terms":[],"rationale":"保存本轮题目"}]}"""))
            }.toString(),
        )
        val envelopeOutput = OpenAiModelProtocol.parseResponse(envelope, input, "model") as TutorToolRequestsOutput
        assertTrue(envelopeOutput.calls.single().terms.isEmpty())
    }

    @Test
    fun readToolSchemaStaysMinimalRequired() {
        // 仅声明读工具（NOTEBOOK_READ）时 strict required 不应被 T6 语义字段污染
        val input = respondInput().copy(
            toolDeclarations = listOf(TutorToolName.NOTEBOOK_READ),
        )
        val withTools = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            enableNativeTools = true,
        )
        val required = functionRequired(functionBlock(withTools, "NOTEBOOK_READ"))

        assertTrue(
            "读工具 strict required 应仅为 terms+rationale（实得：$required）",
            required.contains("\"terms\"") && required.contains("\"rationale\"") && !required.contains("direction"),
        )
    }

    @Test
    fun lobbyWithNoNativeToolsEnabledStaysRouteB() {
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = lobbyInput(),
            images = emptyList(),
        )
        assertFalse(body.contains("\"tools\""))
    }

    // ---- D-M M7：咨询工具的原生 schema 与解析 ----

    /** 请求体里某个 function 块的文本：从它的 name 起，到下一个函数的 name（或结尾）为止。 */
    private fun functionRequired(function: String): String =
        parseObject(function)["parameters"]!!.let { it as kotlinx.serialization.json.JsonObject }["required"].toString()

    private fun functionBlock(body: String, name: String): String {
        val tools = parseObject(body)["tools"] as kotlinx.serialization.json.JsonArray
        return tools.map { (it as kotlinx.serialization.json.JsonObject)["function"] as kotlinx.serialization.json.JsonObject }
            .single { (it["name"] as kotlinx.serialization.json.JsonPrimitive).content == name }.toString()
    }

    @Test
    fun advisoryWriteSchemaCarriesScopeKindEnumsAndThePayloadBound() {
        val input = respondInput().copy(
            toolDeclarations = listOf(
                TutorToolName.ADVISORY_READ,
                TutorToolName.ADVISORY_WRITE,
            ),
        )
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            enableNativeTools = true,
        )

        val writeBlock = functionBlock(body, "ADVISORY_WRITE")
        assertTrue("ADVISORY_WRITE schema 必须在请求体里", writeBlock.isNotEmpty())
        // 三 kind 限枚举（约束解码白名单）。
        assertTrue("kind 枚举必须进 schema：$writeBlock", writeBlock.contains("\"TEACHING_FOCUS\""))
        assertTrue(writeBlock.contains("\"MISCONCEPTION\""))
        assertTrue(writeBlock.contains("\"DIFFICULTY_TIER\""))
        // scope 三选一。
        assertTrue(writeBlock.contains("\"NODE\""))
        assertTrue(writeBlock.contains("\"PROBLEM\""))
        assertTrue(writeBlock.contains("\"SUBJECT\""))
        // payload 上限进 schema（本地仍兜底校验）。
        assertTrue(
            "payload 上限必须进 schema：$writeBlock",
            writeBlock.contains(
                "\"maxLength\":${com.tingyun.smartmistakebook.core.model.TutorToolCall.MAX_ADVISORY_PAYLOAD_CHARS}",
            ),
        )
        val required = functionRequired(writeBlock)

        assertTrue(required.contains("\"advisoryScope\""))
        assertTrue(required.contains("\"advisoryKind\""))
        assertTrue(required.contains("\"payloadMarkdown\""))

        val readBlock = functionBlock(body, "ADVISORY_READ")
        assertTrue("读侧必须有 scope 枚举：$readBlock", readBlock.contains("\"advisoryScope\""))
        assertFalse("读侧不得携带写字段", readBlock.contains("\"payloadMarkdown\""))
    }

    @Test
    fun nativeAdvisoryWriteCallParsesScopeKindAndPayload() {
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_adv","type":"function",
               "function":{"name":"ADVISORY_WRITE",
                 "arguments":"{\"terms\":[\"K1\"],\"rationale\":\"学生暴露了持续误区\",\"advisoryScope\":\"NODE\",\"advisoryKind\":\"MISCONCEPTION\",\"payloadMarkdown\":\"乘负数时忘记变号。\"}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        ) as TutorToolRequestsOutput

        val call = output.calls.single()
        assertEquals(TutorToolName.ADVISORY_WRITE, call.tool)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorAdvisoryScope.NODE, call.advisoryScope)
        assertEquals(
            com.tingyun.smartmistakebook.core.model.TutorAdvisoryKind.MISCONCEPTION,
            call.advisoryKind,
        )
        assertEquals("乘负数时忘记变号。", call.payloadMarkdown)
    }

    @Test
    fun nativeCallsWithTheSameToolAndDifferentArgumentsKeepTheirOrder() {
        // 同一工具可以服务不同查询，后续结果必须保持调用顺序。
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[
               {"id":"call_1","type":"function",
                "function":{"name":"NOTEBOOK_READ",
                  "arguments":"{\"terms\":[\"二次函数\"],\"rationale\":\"先查最近错题\"}"}},
               {"id":"call_2","type":"function",
                "function":{"name":"NOTEBOOK_READ",
                  "arguments":"{\"terms\":[\"单调性\"],\"rationale\":\"再查一次同类题\"}"}}]}
            """.trimIndent(),
        )

        val round = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        ) as TutorToolRequestsOutput

        assertEquals(2, round.calls.size)
        assertTrue(round.calls.all { it.tool == TutorToolName.NOTEBOOK_READ })
        assertEquals(listOf(listOf("二次函数"), listOf("单调性")), round.calls.map { it.terms })
    }

    @Test
    fun nativeToolCallsResponseParsesToToolRequestsOutput() {
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant",
             "content":"{\"intentDecision\":{\"intent\":\"CURRENT_QUESTION_HELP\",\"confidence\":0.9,\"explicitActionRequest\":false,\"memoryPreference\":\"UNCHANGED\",\"requestedLocalCapability\":\"NONE\"}}",
             "tool_calls":[{"id":"call_1","type":"function",
               "function":{"name":"MASTERY_UPDATE",
                 "arguments":"{\"terms\":[\"knowledge-node-1\"],\"rationale\":\"学生明确说理解了配方法。\",\"direction\":\"POSITIVE\",\"understanding\":\"CONFIDENT\",\"confidence\":0.85}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        )
        assertTrue("原生 tool_calls 应解析为工具申请轮", output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(
            com.tingyun.smartmistakebook.core.model.TutorMessageIntent.CURRENT_QUESTION_HELP,
            round.intentDecision.intent,
        )
        assertEquals(1, round.calls.size)
        val call = round.calls[0]
        assertEquals(TutorToolName.MASTERY_UPDATE, call.tool)
        assertEquals(listOf("knowledge-node-1"), call.terms)
        assertEquals("学生明确说理解了配方法。", call.rationale)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection.POSITIVE, call.direction)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier.CONFIDENT, call.understanding)
        assertEquals(0.85, call.confidence, 1e-9)
        // 逐次题锚也必须从 arguments 里读出来：原生 tool_calls 路由的标准形态 content=null，
        // 写工具的准入只能落在调用自己身上（复核意见一）。
        assertNull("没有题锚字段时就是没有声明", call.boundQuestion)
    }

    @Test
    fun nativeWriteCallCarriesItsQuestionAnchorFromArguments() {
        // 原生路由能表达写工具的题锚：arguments 里的 problemId / problemRevisionId /
        // anchorTerms 与 json_object 信封路由同名同义，两条路由共用同一套本地校验。
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_1","type":"function",
               "function":{"name":"MASTERY_UPDATE",
                 "arguments":"{\"terms\":[\"knowledge-node-1\"],\"rationale\":\"学生明确说理解了配方法。\",\"direction\":\"POSITIVE\",\"understanding\":\"CONFIDENT\",\"confidence\":0.85,\"problemId\":\"problem-peifang\",\"problemRevisionId\":\"revision-peifang\",\"anchorTerms\":[\"配方法\"]}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        ) as TutorToolRequestsOutput

        val anchor = output.calls.single().boundQuestion
        assertEquals("problem-peifang", anchor?.problemId)
        assertEquals("revision-peifang", anchor?.problemRevisionId)
        assertEquals(listOf("配方法"), anchor?.anchorTerms)
    }

    @Test
    fun readToolNativeCallParsesTermsOnly() {
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant",
             "content":"{\"intentDecision\":{\"intent\":\"MISTAKE_NOTEBOOK_LOOKUP\",\"confidence\":0.9,\"explicitActionRequest\":true,\"memoryPreference\":\"UNCHANGED\",\"requestedLocalCapability\":\"READ_MISTAKE_NOTEBOOK\",\"lookupTerms\":[\"二次函数\"]}}",
             "tool_calls":[{"id":"call_2","type":"function",
               "function":{"name":"NOTEBOOK_READ","arguments":"{\"terms\":[\"二次函数\"],\"rationale\":\"学生想找二次函数错题\"}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = lobbyInput(),
            modelVersion = "test-model-v1",
        )
        assertTrue(output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(
            com.tingyun.smartmistakebook.core.model.TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP,
            round.intentDecision.intent,
        )
        assertEquals(TutorToolName.NOTEBOOK_READ, round.calls.single().tool)
        assertEquals(listOf("二次函数"), round.calls.single().terms)
    }

    @Test
    fun nativeMasteryReadPreservesTheExtendedResultRequest() {
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = toolCallEnvelope(
                """
                {"role":"assistant","content":null,
                 "tool_calls":[{"id":"call_mastery","type":"function",
                   "function":{"name":"MASTERY_READ",
                     "arguments":"{\"terms\":[\"K1\"],\"rationale\":\"读取更完整的掌握情况\",\"extendedResult\":true}"}}]}
                """.trimIndent(),
            ),
            input = respondInput(),
            modelVersion = "test-model-v1",
        ) as TutorToolRequestsOutput

        assertTrue("原生路由必须保留模型申请的扩展结果预算", output.calls.single().extendedResult)
    }

    @Test
    fun contentNullNativeToolCallsDeriveLobbyIntentFromKind() {
        // 标准原生 tool_calls：content=null，无意图信封 → 从 dispatch kind 推导。
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_3","type":"function",
               "function":{"name":"NOTEBOOK_READ","arguments":"{\"terms\":[\"二次函数\"],\"rationale\":\"学生想找二次函数错题\"}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = lobbyInput(),
            modelVersion = "test-model-v1",
        )
        assertTrue(output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(
            "Lobby 无 content 意图时应推导 MISTAKE_NOTEBOOK_LOOKUP（NOTEBOOK_READ 授权依赖）",
            com.tingyun.smartmistakebook.core.model.TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP,
            round.intentDecision.intent,
        )
        assertEquals(TutorToolName.NOTEBOOK_READ, round.calls.single().tool)
    }

    @Test
    fun contentNullNativeToolCallsDeriveRespondIntentFromKind() {
        // Respond 无 content 意图时应推导 CURRENT_QUESTION_HELP（T6/T2/T3/T5 授权依赖）。
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_4","type":"function",
               "function":{"name":"MASTERY_UPDATE",
                 "arguments":"{\"terms\":[\"knowledge-node-1\"],\"rationale\":\"学生能独立完成同类题。\",\"direction\":\"POSITIVE\",\"understanding\":\"CONFIDENT\",\"confidence\":0.8}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        )
        assertTrue(output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(
            com.tingyun.smartmistakebook.core.model.TutorMessageIntent.CURRENT_QUESTION_HELP,
            round.intentDecision.intent,
        )
        assertEquals(TutorToolName.MASTERY_UPDATE, round.calls.single().tool)
    }

    // ---- 4B A1：生图工具的原生 schema 与解析 ----

    @Test
    fun theFullEightToolFaceIsAdvertisedAndTheFigureSchemaIsStrict() {
        val input = respondInput().copy(toolDeclarations = TutorToolName.entries.toList())
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = input,
            images = emptyList(),
            enableNativeTools = true,
        )

        // 8 枚工具面在原生路由上全部可达（一个都不能漏）。
        TutorToolName.entries.forEach { tool ->
            assertTrue(
                "tools 必须含 ${tool.name} 的 function schema",
                body.contains("\"name\":\"${tool.name}\""),
            )
        }

        val figureBlock = functionBlock(body, "GENERATE_FIGURE")
        assertTrue("GENERATE_FIGURE schema 必须在请求体里", figureBlock.isNotEmpty())
        // kind 二选一（约束解码白名单）。
        assertTrue("kind 枚举必须进 schema：$figureBlock", figureBlock.contains("\"REDRAW_PROBLEM\""))
        assertTrue(figureBlock.contains("\"GENERATE_PROCESS\""))
        // description 硬上限进 schema（本地仍兜底校验）。
        assertTrue(
            "description 上限必须进 schema：$figureBlock",
            figureBlock.contains(
                "\"maxLength\":${com.tingyun.smartmistakebook.core.model.TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS}",
            ),
        )
        val required = functionRequired(figureBlock)

        assertTrue(required.contains("\"kind\""))
        assertTrue(required.contains("\"description\""))
        assertTrue(required.contains("\"terms\""))
        assertTrue(required.contains("\"rationale\""))
        // 生图不得携带别的工具的语义字段。
        assertFalse(figureBlock.contains("\"direction\""))
        assertFalse(figureBlock.contains("\"advisoryKind\""))
    }

    @Test
    fun nativeGenerateFigureCallParsesKindAndDescription() {
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_fig","type":"function",
               "function":{"name":"GENERATE_FIGURE",
                 "arguments":"{\"terms\":[],\"rationale\":\"图形是这道题的理解关键\",\"kind\":\"REDRAW_PROBLEM\",\"description\":\"重绘题面去手写\"}"}}]}
            """.trimIndent(),
        )
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        ) as TutorToolRequestsOutput

        val call = output.calls.single()
        assertEquals(TutorToolName.GENERATE_FIGURE, call.tool)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorFigureKind.REDRAW_PROBLEM, call.figureKind)
        assertEquals("重绘题面去手写", call.figureDescription)
        assertTrue(call.terms.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nativeGenerateFigureCallRejectsAnUnknownKind() {
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_fig","type":"function",
               "function":{"name":"GENERATE_FIGURE",
                 "arguments":"{\"terms\":[],\"rationale\":\"图形关键\",\"kind\":\"PAINT_SOMETHING\",\"description\":\"画一张图\"}"}}]}
            """.trimIndent(),
        )
        OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nativeGenerateFigureCallRejectsAnOverlongDescription() {
        val description =
            "长".repeat(com.tingyun.smartmistakebook.core.model.TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS + 1)
        val envelope = toolCallEnvelope(
            """
            {"role":"assistant","content":null,
             "tool_calls":[{"id":"call_fig","type":"function",
               "function":{"name":"GENERATE_FIGURE",
                 "arguments":"{\"terms\":[],\"rationale\":\"图形关键\",\"kind\":\"GENERATE_PROCESS\",\"description\":\"$description\"}"}}]}
            """.trimIndent(),
        )
        OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        )
    }

    @Test
    fun contentResponseStillParsesAsRouteB() {
        // provider 忽略 tools、在 json_object 信封里回 content → 回落 Route B 正常解析
        val envelope = """
            {"choices":[{"message":{"role":"assistant",
              "content":"{\"intentDecision\":{\"intent\":\"CURRENT_QUESTION_HELP\",\"confidence\":0.9,\"explicitActionRequest\":false,\"memoryPreference\":\"UNCHANGED\",\"requestedLocalCapability\":\"NONE\",\"lookupTerms\":[]},\"messageMarkdown\":\"很好，我们看配方法。\",\"solutionRevealed\":false}"}}]}
        """.trimIndent()
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope,
            input = respondInput(),
            modelVersion = "test-model-v1",
        )
        assertTrue("无 tool_calls 时应回落 Route B 终答", output is TutorRespondOutput)
    }

    @Test
    fun verboseRationaleTermsAndConfidenceAreNormalizedInsteadOfFailingTheRound() {
        val payload = Json.parseToJsonElement(
            """
            {
              "rationale": "  ${"请查阅".repeat(120)}  ",
              "terms": ["  函数  ", "函数", "${"很长的检索词".repeat(20)}"],
              "confidence": 1.7
            }
            """.trimIndent(),
        ).jsonObject

        val call = payload.toTutorToolCall(TutorToolName.NOTEBOOK_READ)

        assertEquals(TutorToolCall.MAX_TOOL_RATIONALE_CHARS, call.rationale.length)
        assertFalse(call.rationale.startsWith(" "))
        assertEquals("函数", call.terms.first())
        assertEquals(2, call.terms.size)
        assertTrue(call.terms.last().length <= TutorIntentDecision.MAX_LOOKUP_TERM_CHARS)
        assertEquals(1.0, call.confidence, 0.0)
    }
}
