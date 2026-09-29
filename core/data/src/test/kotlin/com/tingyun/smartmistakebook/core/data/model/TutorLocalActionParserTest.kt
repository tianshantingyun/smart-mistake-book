package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 本地动作通道的**解析器**（D-K2e）：两条路由都得把动作请求读成同一个形状，且**只认白名单**。
 *
 * 判别格是"模型造的东西进不来"：未知动作 id、未声明的参数键、不存在的函数名——三条都是
 * 整条输出无效（不是"忽略掉多余信息"），因为"模型只能选不能造"这条纪律落在解析层。
 */
class TutorLocalActionParserTest {
    private fun lobbyInput(): TutorLobbyInput = TutorLobbyInput(
        conversationId = "conv-1",
        messageOrdinal = 1,
        studentMessage = "帮我看看这道题",
        toolDeclarations = TutorToolName.entries.toList(),
    )

    private fun parseRouteB(json: String): TutorLobbyOutput {
        val output = OpenAiModelTaskAdapters.parse(
            payload = parseObject(json),
            input = lobbyInput(),
            modelVersion = "model-1",
        )
        return output as TutorLobbyOutput
    }

    @Test
    fun routeBReadsTheDeclaredActionsOutOfTheEnvelope() {
        val output = parseRouteB(
            """
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.9,
            "explicitActionRequest":true,"memoryPreference":"UNCHANGED",
            "requestedLocalCapability":"NONE","lookupTerms":[]},
            "messageMarkdown":"我先帮你把这道题收进错题本，你点一下确认。",
            "localActions":[{"action":"SAVE_TO_NOTEBOOK"}]}
            """.trimIndent(),
        )

        assertEquals(
            listOf(TutorLocalAction.SAVE_TO_NOTEBOOK),
            output.localActions.map { request -> request.action },
        )
    }

    @Test
    fun routeBWithoutTheKeyRequestsNothing() {
        val output = parseRouteB(
            """
            {"intentDecision":{"intent":"CASUAL_CONVERSATION","confidence":0.9,
            "explicitActionRequest":false,"memoryPreference":"UNCHANGED",
            "requestedLocalCapability":"NONE","lookupTerms":[]},
            "messageMarkdown":"你好。"}
            """.trimIndent(),
        )

        assertTrue(output.localActions.isEmpty())
    }

    @Test
    fun routeBRejectsAnActionThatIsNotOnTheWhitelist() {
        assertInvalidOutput(
            """
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.9,
            "explicitActionRequest":true,"memoryPreference":"UNCHANGED",
            "requestedLocalCapability":"NONE","lookupTerms":[]},
            "messageMarkdown":"这就去做。","localActions":[{"action":"DELETE_EVERYTHING"}]}
            """.trimIndent(),
        )
    }

    @Test
    fun routeBRejectsAnUndeclaredParameter() {
        // 四个动作的形状都是空集：任何参数键都是模型"造"的，整条输出无效。
        assertInvalidOutput(
            """
            {"intentDecision":{"intent":"CURRENT_QUESTION_HELP","confidence":0.9,
            "explicitActionRequest":true,"memoryPreference":"UNCHANGED",
            "requestedLocalCapability":"NONE","lookupTerms":[]},
            "messageMarkdown":"这就去做。","localActions":[{"action":"OPEN_PROBLEM","problemId":"p1"}]}
            """.trimIndent(),
        )
    }

    @Test
    fun routeAReadsActionCallsApartFromToolCalls() {
        val output = parseRouteA(
            nativeBody = """
            {"content":null,"tool_calls":[
              {"id":"call-1","type":"function",
               "function":{"name":"OPEN_PROBLEM","arguments":"{}"}},
              {"id":"call-2","type":"function",
               "function":{"name":"KNOWLEDGE_READ","arguments":"{\"rationale\":\"学生问到单调性\",\"terms\":[\"单调性\"]}"}}
            ]}
            """.trimIndent(),
        ) as TutorToolRequestsOutput

        assertEquals(
            listOf(TutorLocalAction.OPEN_PROBLEM),
            output.localActions.map { request -> request.action },
        )
        assertEquals(listOf(TutorToolName.KNOWLEDGE_READ), output.calls.map { call -> call.tool })
    }

    @Test
    fun routeARejectsAFunctionThatIsNeitherAToolNorADeclaredAction() {
        val failure = runCatching {
            parseRouteA(
                nativeBody = """
                {"content":null,"tool_calls":[
                  {"id":"call-1","type":"function",
                   "function":{"name":"OPEN_THE_POD_BAY_DOORS","arguments":"{}"}}
                ]}
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertNotNull("不存在的函数名必须让整条输出无效", failure)
    }

    @Test
    fun routeARejectsAnUndeclaredActionParameter() {
        val failure = runCatching {
            parseRouteA(
                nativeBody = """
                {"content":null,"tool_calls":[
                  {"id":"call-1","type":"function",
                   "function":{"name":"START_EXPORT","arguments":"{\"problemIds\":[\"p1\"]}"}}
                ]}
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
    }

    private fun parseRouteA(nativeBody: String): com.tingyun.smartmistakebook.core.model.ModelTaskOutput =
        OpenAiModelProtocol.parseResponse(
            responseBody = """{"choices":[{"message":$nativeBody}]}""",
            input = lobbyInput(),
            modelVersion = "model-1",
        )

    private fun assertInvalidOutput(json: String) {
        try {
            parseRouteB(json)
            fail("形状不对的本地动作必须让整条输出无效")
        } catch (expected: InvalidModelResponseException) {
            // 期望：解析层结构性拒（不是"忽略掉多余信息"）。
        }
    }
}
