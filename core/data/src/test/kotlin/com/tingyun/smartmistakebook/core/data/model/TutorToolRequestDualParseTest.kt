package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorMessageIntent
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorToolRequestDualParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun lobbyInput() = TutorLobbyInput(
        conversationId = "conv-1",
        messageOrdinal = 1,
        studentMessage = "帮我看看错题本里有没有二次函数",
        priorMessages = emptyList(),
    )

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
    )

    private fun toolRequestPayload() = json.parseToJsonElement("""
        {"intentDecision":{"intent":"MISTAKE_NOTEBOOK_LOOKUP","confidence":0.9,
          "explicitActionRequest":true,"memoryPreference":"UNCHANGED",
          "requestedLocalCapability":"READ_MISTAKE_NOTEBOOK","lookupTerms":["二次函数"]},
         "toolRequests":[{"tool":"NOTEBOOK_READ","terms":["二次函数"],"rationale":"学生想找二次函数错题"}]}
    """.trimIndent()).jsonObject

    private fun finalAnswerPayload() = json.parseToJsonElement("""
        {"intentDecision":{"intent":"MISTAKE_NOTEBOOK_LOOKUP","confidence":0.9,
          "explicitActionRequest":true,"memoryPreference":"UNCHANGED",
          "requestedLocalCapability":"READ_MISTAKE_NOTEBOOK","lookupTerms":["二次函数"]},
         "messageMarkdown":"错题本里有 2 道二次函数相关错题。"}
    """.trimIndent()).jsonObject

    private fun respondFinalAnswerPayload() = json.parseToJsonElement("""
        {"intentDecision":{"intent":"MISTAKE_NOTEBOOK_LOOKUP","confidence":0.9,
          "explicitActionRequest":true,"memoryPreference":"UNCHANGED",
          "requestedLocalCapability":"READ_MISTAKE_NOTEBOOK","lookupTerms":["二次函数"]},
         "messageMarkdown":"错题本里有 2 道二次函数相关错题。",
         "solutionRevealed":false}
    """.trimIndent()).jsonObject

    @Test
    fun toolRequestPayloadParsesToToolRequestsOutput() {
        val output = OpenAiModelTaskAdapters.parse(
            toolRequestPayload(), lobbyInput(), "test-model-v1",
        )
        assertTrue("应解析为工具申请轮", output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP, round.intentDecision.intent)
        assertEquals(1, round.calls.size)
        assertEquals(TutorToolName.NOTEBOOK_READ, round.calls[0].tool)
        assertEquals(listOf("二次函数"), round.calls[0].terms)
    }

    @Test
    fun finalAnswerPayloadParsesToLobbyOutput() {
        val output = OpenAiModelTaskAdapters.parse(
            finalAnswerPayload(), lobbyInput(), "test-model-v1",
        )
        assertTrue("无 toolRequests 应解析为终答", output is TutorLobbyOutput)
    }

    @Test
    fun respondToolRequestPayloadParsesToToolRequestsOutput() {
        val output = OpenAiModelTaskAdapters.parse(
            toolRequestPayload(), respondInput(), "test-model-v1",
        )
        assertTrue("含 toolRequests 的 Respond payload 应解析为工具申请轮", output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP, round.intentDecision.intent)
        assertEquals(1, round.calls.size)
        assertEquals(TutorToolName.NOTEBOOK_READ, round.calls[0].tool)
        assertEquals(listOf("二次函数"), round.calls[0].terms)
    }

    @Test
    fun respondFinalAnswerPayloadParsesToRespondOutput() {
        val output = OpenAiModelTaskAdapters.parse(
            respondFinalAnswerPayload(), respondInput(), "test-model-v1",
        )
        assertTrue("无 toolRequests 的 Respond payload 应解析为终答", output is TutorRespondOutput)
        val respond = output as TutorRespondOutput
        assertEquals("错题本里有 2 道二次函数相关错题。", respond.messageMarkdown)
        assertEquals("question-1", respond.questionDocumentId)
        assertEquals(false, respond.solutionRevealed)
    }

    @Test
    fun masteryUpdateToolRequestParsesSemanticFields() {
        val payload = json.parseToJsonElement("""
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.95,
              "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
              "requestedLocalCapability":"NONE","lookupTerms":[]},
             "toolRequests":[{"tool":"MASTERY_UPDATE","terms":["knowledge-node-1"],
               "rationale":"学生说现在理解了配方法，且刚独立做对一道同类题。",
               "direction":"POSITIVE","understanding":"MASTERED","difficultyTier":"MEDIUM","confidence":0.85}]}
        """.trimIndent()).jsonObject

        val output = OpenAiModelTaskAdapters.parse(payload, respondInput(), "test-model-v1")
        assertTrue(output is TutorToolRequestsOutput)
        val round = output as TutorToolRequestsOutput
        assertEquals(1, round.calls.size)
        val call = round.calls[0]
        assertEquals(TutorToolName.MASTERY_UPDATE, call.tool)
        assertEquals(listOf("knowledge-node-1"), call.terms)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection.POSITIVE, call.direction)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier.MASTERED, call.understanding)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorDifficultyTier.MEDIUM, call.difficultyTier)
        assertEquals(0.85, call.confidence, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun masteryUpdateWithoutDirectionIsRejectedByModelContract() {
        // MASTERY_UPDATE 缺 direction/understanding 触发 TutorToolCall.init require——
        // 模型协议违规，契约层 fail-fast（绝不让无方向证据进 gate）。
        val payload = json.parseToJsonElement("""
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.95,
              "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
              "requestedLocalCapability":"NONE","lookupTerms":[]},
             "toolRequests":[{"tool":"MASTERY_UPDATE","terms":["knowledge-node-1"],
               "rationale":"学生说现在理解了。"}]}
        """.trimIndent()).jsonObject

        OpenAiModelTaskAdapters.parse(payload, respondInput(), "test-model-v1")
    }

    @Test
    fun advisoryWriteToolRequestParsesScopeKindAndPayload() {
        // Route B（json_object 信封）与原生 tool_calls 同名同义：D-M M7 的三个咨询字段
        // 必须在这条路由上也解析得出来（否则 provider 忽略 tools 时写侧整条失效）。
        val payload = json.parseToJsonElement("""
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.9,
              "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
              "requestedLocalCapability":"NONE","lookupTerms":[]},
             "toolRequests":[{"tool":"ADVISORY_WRITE","terms":[],
               "rationale":"学生暴露了持续误区",
               "advisoryScope":"PROBLEM","advisoryKind":"MISCONCEPTION",
               "payloadMarkdown":"两边乘负数忘记变号。"}]}
        """.trimIndent()).jsonObject

        val output = OpenAiModelTaskAdapters.parse(payload, respondInput(), "test-model-v1")
        assertTrue(output is TutorToolRequestsOutput)
        val call = (output as TutorToolRequestsOutput).calls.single()
        assertEquals(TutorToolName.ADVISORY_WRITE, call.tool)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorAdvisoryScope.PROBLEM, call.advisoryScope)
        assertEquals(
            com.tingyun.smartmistakebook.core.model.TutorAdvisoryKind.MISCONCEPTION,
            call.advisoryKind,
        )
        assertEquals("两边乘负数忘记变号。", call.payloadMarkdown)
    }

    // ---- 4B A1：生图工具的两路由同名同义 ----

    private fun generateFigureRouteBPayload(kind: String, description: String) =
        json.parseToJsonElement(
            """
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.95,
              "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
              "requestedLocalCapability":"NONE","lookupTerms":[]},
             "toolRequests":[{"tool":"GENERATE_FIGURE","terms":[],
               "rationale":"图形是这道题的理解关键",
               "kind":"$kind","description":"$description"}]}
            """.trimIndent(),
        ).jsonObject

    @Test
    fun generateFigureParsesIdenticallyOnBothRoutes() {
        // KD-30：两路由对 kind/description 的声明必须同名同义。同一次生图申请在
        // Route B 信封与 Route A 原生 tool_calls 里必须解析成**同一个** TutorToolCall。
        val routeBCall = (
            OpenAiModelTaskAdapters.parse(
                generateFigureRouteBPayload("REDRAW_PROBLEM", "重绘题面去手写"),
                respondInput(),
                "test-model-v1",
            ) as TutorToolRequestsOutput
            ).calls.single()
        val routeAEnvelope = """
            {"choices":[{"message":{"role":"assistant","content":null,
             "tool_calls":[{"id":"call_fig","type":"function",
               "function":{"name":"GENERATE_FIGURE",
                 "arguments":"{\"terms\":[],\"rationale\":\"图形是这道题的理解关键\",\"kind\":\"REDRAW_PROBLEM\",\"description\":\"重绘题面去手写\"}"}}]}}]}
        """.trimIndent()
        val routeACall = (
            OpenAiModelProtocol.parseResponse(
                routeAEnvelope,
                respondInput(),
                "test-model-v1",
            ) as TutorToolRequestsOutput
            ).calls.single()

        assertEquals(TutorToolName.GENERATE_FIGURE, routeBCall.tool)
        assertEquals(com.tingyun.smartmistakebook.core.model.TutorFigureKind.REDRAW_PROBLEM, routeBCall.figureKind)
        assertEquals("重绘题面去手写", routeBCall.figureDescription)
        assertEquals("两路由必须解析出同一个 TutorToolCall", routeBCall, routeACall)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateFigureWithAnUnknownKindIsRejectedOnRouteB() {
        // 非法 kind 是协议错误（enumValue 拒 → 整条输出无效），不是"忽略一下"。
        OpenAiModelTaskAdapters.parse(
            generateFigureRouteBPayload("PAINT_SOMETHING", "画一张图"),
            respondInput(),
            "test-model-v1",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateFigureWithAnOverlongDescriptionIsRejectedOnRouteB() {
        OpenAiModelTaskAdapters.parse(
            generateFigureRouteBPayload(
                "GENERATE_PROCESS",
                "长".repeat(com.tingyun.smartmistakebook.core.model.TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS + 1),
            ),
            respondInput(),
            "test-model-v1",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateFigureWithoutAKindIsRejectedOnRouteB() {
        val payload = json.parseToJsonElement(
            """
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.95,
              "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
              "requestedLocalCapability":"NONE","lookupTerms":[]},
             "toolRequests":[{"tool":"GENERATE_FIGURE","terms":[],
               "rationale":"图形是这道题的理解关键","description":"画一张图"}]}
            """.trimIndent(),
        ).jsonObject
        OpenAiModelTaskAdapters.parse(payload, respondInput(), "test-model-v1")
    }

    @Test
    fun advisoryReadToolRequestMayOmitItsScopeForTheSubjectDefault() {
        val payload = json.parseToJsonElement("""
            {"intentDecision":{"intent":"LEARNING_PROGRESS_LOOKUP","confidence":0.9,
              "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
              "requestedLocalCapability":"NONE","lookupTerms":[]},
             "toolRequests":[{"tool":"ADVISORY_READ","terms":[],
               "rationale":"回顾本科目教学备注"}]}
        """.trimIndent()).jsonObject

        val output = OpenAiModelTaskAdapters.parse(payload, respondInput(), "test-model-v1")
        val call = (output as TutorToolRequestsOutput).calls.single()
        assertEquals(TutorToolName.ADVISORY_READ, call.tool)
        assertEquals(null, call.advisoryScope)
        assertTrue(call.terms.isEmpty())
    }
}
