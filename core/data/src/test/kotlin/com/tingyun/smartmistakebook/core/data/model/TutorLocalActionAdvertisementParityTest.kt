package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **两套广告同源**（D-K2e 本地动作白名单 / 规格 §3.1："协议广告两套并存在实施中收敛"）。
 *
 * 判别格是**集合与形状的逐项对拍**，而不是"两处都提到了动作"：
 *
 * - Route A（原生 function schema，`OpenAiModelProtocol.localActionSchemas`）里出现的动作名、
 *   每个动作的参数字段名与必填集合；
 * - Route B（提示词块，`OpenAiModelTaskAdapters.localActionPromptBlock`）里列出的动作名与
 *   参数（或"不接受任何参数"）；
 * - 两边都必须**恰好**等于 `core:model` 的 [TutorLocalAction] 声明——多一个、少一个、
 *   名称不同、参数不同，这条用例都红。
 *
 * 为什么值得一条专门的用例：工具面的文案在这里漂过三次（`TutorToolDescriptions` 的注释记着
 * 那次事故），而本地动作**同时**出现在两套广告里——漂一次的代价是"Route A 允许模型传参、
 * Route B 说不能传"，症状只在某种 provider 上出现。
 */
class TutorLocalActionAdvertisementParityTest {
    @Test
    fun routeAAdvertisesExactlyTheDeclaredActionsAndParameterShapes() {
        val schema = localActionNativeSchemas()

        val advertised = schema.associate { function ->
            val name = function.getValue("name").jsonPrimitive.content
            val parameters = function.getValue("parameters").jsonObject
            val properties = parameters.getValue("properties").jsonObject
            val required = parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content }
            name to (properties.keys to required.toSet())
        }

        val declared = TutorLocalAction.entries.associate { action ->
            action.actionId to (
                action.parameters.map { parameter -> parameter.parameterName }.toSet() to
                    action.parameters.filter { parameter -> parameter.required }
                        .map { parameter -> parameter.parameterName }
                        .toSet()
                )
        }

        assertEquals(declared.keys, advertised.keys)
        declared.forEach { (actionId, shape) ->
            val advertisedShape = requireNotNull(advertised[actionId])
            assertEquals("Route A 的 $actionId 参数集合与声明不一致", shape.first, advertisedShape.first)
            assertEquals("Route A 的 $actionId 必填集合与声明不一致", shape.second, advertisedShape.second)
        }
    }

    @Test
    fun routeARejectsEveryUndeclaredParameterStructurally() {
        localActionNativeSchemas().forEach { function ->
            val parameters = function.getValue("parameters").jsonObject
            assertEquals(
                "${function.getValue("name").jsonPrimitive.content} 必须拒绝未声明的参数",
                JsonPrimitive(false),
                parameters.getValue("additionalProperties"),
            )
        }
    }

    @Test
    fun routeBAdvertisesExactlyTheDeclaredActionsAndParameterShapes() {
        val prompt = localActionPrompt()

        TutorLocalAction.entries.forEach { action ->
            assertTrue(
                "Route B 的提示词里没有 ${action.actionId}",
                prompt.contains(action.actionId),
            )
            if (action.parameters.isEmpty()) {
                assertTrue(
                    "${action.actionId} 的参数形状是空集，提示词必须说不接受参数",
                    prompt.contains("${action.actionId}：${action.purposeDescription}（不接受任何参数）"),
                )
            } else {
                action.parameters.forEach { parameter ->
                    assertTrue(
                        "${action.actionId} 的参数 ${parameter.parameterName} 没进提示词",
                        prompt.contains(parameter.parameterName),
                    )
                }
            }
        }

        // 反向：广告里不得出现名单外的动作名（"ACTION_" 前缀不属于任何工具名，用它做探针）。
        val unknown = Regex("ACTION_[A-Z_]+").findAll(prompt).map { it.value }.toSet()
        assertEquals(emptySet<String>(), unknown)
    }

    @Test
    fun theActionAdvertisementsStayOffRoundsWithoutAConfirmationFace() {
        // 广告只给大厅轮：确认卡（本地动作的执行面）目前长在智能体栏这一条交互面上，
        // 讲题/计划轮声明了就是"广告一个没人接的东西"。
        val planTools = toolsOf(
            TutorPlanInput(
                sessionId = "tutor-session-1",
                draftRevisionNumber = 1,
                subject = "MATH",
                questionDocument = QuestionDocument(
                    id = "question-1",
                    blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
                ),
                toolDeclarations = TutorToolName.entries.toList(),
                relevantLearningEvidence = emptyList(),
                projectionIsCurrent = true,
            ),
        )
        val actionNames = TutorLocalAction.entries.map(TutorLocalAction::actionId).toSet()

        assertEquals(emptySet<String>(), planTools.map { it.first }.intersect(actionNames))
        assertEquals(
            "大厅轮必须同时广告全部四个本地动作",
            actionNames,
            actionNamesOfLobbyRound(),
        )
    }

    private fun actionNamesOfLobbyRound(): Set<String> =
        localActionNativeSchemas().map { function ->
            function.getValue("name").jsonPrimitive.content
        }.toSet()

    /** 一个任务输入在 Route A 里声明了哪些函数（工具 + 本地动作）。 */
    private fun toolsOf(
        input: com.tingyun.smartmistakebook.core.model.ModelTaskInput,
    ): List<Pair<String, JsonObject>> {
        val body = parseObject(
            OpenAiModelProtocol.requestBody(
                modelId = "model-1",
                input = input,
                images = emptyList(),
                stream = false,
                enableNativeTools = true,
            ),
        )
        val tools = body["tools"] as? JsonArray ?: return emptyList()
        return tools.map { element ->
            val function = element.jsonObject.getValue("function").jsonObject
            function.getValue("name").jsonPrimitive.content to function
        }
    }

    /**
     * 大厅轮在 Route A 里广告的本地动作函数 schema：把请求体里 `tools` 数组按**动作名**过滤
     * （同一数组里还混着五个工具的函数，它们由工具面单独对拍）。
     */
    private fun localActionNativeSchemas(): List<JsonObject> {
        val actionNames = TutorLocalAction.entries.map(TutorLocalAction::actionId).toSet()
        return toolsOf(
            TutorLobbyInput(
                conversationId = "conversation-1",
                messageOrdinal = 1,
                studentMessage = "帮我看看这道题",
            ),
        ).filter { (name, _) -> name in actionNames }.map { (_, function) -> function }
    }

    /** Route B 侧的广告：大厅轮提示词里的本地动作块。 */
    private fun localActionPrompt(): String =
        OpenAiModelTaskAdapters.prompt(
            TutorLobbyInput(
                conversationId = "conversation-1",
                messageOrdinal = 1,
                studentMessage = "帮我看看这道题",
            ),
        )

}
