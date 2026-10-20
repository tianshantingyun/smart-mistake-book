package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b：`GENERATE_FIGURE` 的 method/imageOperation/imagePrompt 在**两条路由**
 * （Route A 原生 tool_calls / Route B json_object 信封）上逐字同义（T3）。
 *
 * 消灭的具体失败（KD-30 的同类）：原生 schema 广告了新字段、但 Route A 解析不读，
 * 或只有 Route B 读——某种 provider 上模型的选择被静默丢弃，而两侧单看都"能跑"。
 */
class TutorFigureMethodDualParseTest {

    private fun respondInput(declarations: List<TutorToolName> = listOf(TutorToolName.GENERATE_FIGURE)) =
        TutorRespondInput(
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
            toolDeclarations = declarations,
        )

    private fun intentDecision() = buildJsonObject {
        put(
            "intentDecision",
            buildJsonObject {
                put("intent", "CURRENT_QUESTION_HELP")
                put("confidence", 0.95)
                put("explicitActionRequest", false)
                put("memoryPreference", "UNCHANGED")
                put("requestedLocalCapability", "NONE")
                put("lookupTerms", buildJsonArray { })
            },
        )
    }

    /** Route B：json_object 信封里的 toolRequests[0]。 */
    private fun routeBCall(arguments: JsonObject): TutorToolCall {
        val payload = intentDecision().toMutableMap().apply {
            put(
                "toolRequests",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("tool", "GENERATE_FIGURE")
                            arguments.forEach { (key, value) -> put(key, value) }
                        },
                    )
                },
            )
        }
        val output = OpenAiModelTaskAdapters.parse(
            JsonObject(payload),
            respondInput(),
            "test-model-v1",
        )
        return (output as TutorToolRequestsOutput).calls.single()
    }

    /** Route A：原生 assistant.tool_calls（arguments 是 JSON 字符串）。 */
    private fun routeACall(arguments: JsonObject): TutorToolCall {
        val envelope = buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "message",
                                buildJsonObject {
                                    put("role", "assistant")
                                    put("content", kotlinx.serialization.json.JsonNull)
                                    put(
                                        "tool_calls",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("id", "call_fig")
                                                    put("type", "function")
                                                    put(
                                                        "function",
                                                        buildJsonObject {
                                                            put("name", "GENERATE_FIGURE")
                                                            put("arguments", arguments.toString())
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
        }
        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope.toString(),
            input = respondInput(),
            modelVersion = "test-model-v1",
        )
        return (output as TutorToolRequestsOutput).calls.single()
    }

    private fun figureArguments(
        method: String? = "IMAGE_MODEL",
        operation: String? = "EDIT",
        prompt: String? = "保留题干文字、标注和单位，只清除手写。",
        program: String? = null,
        kind: String = "REDRAW_PROBLEM",
        description: String = "重绘题面去手写",
    ): JsonObject = buildJsonObject {
        put("terms", buildJsonArray { })
        put("rationale", "图形是这道题的理解关键")
        put("kind", kind)
        put("description", description)
        method?.let { put("method", it) }
        operation?.let { put("imageOperation", it) }
        prompt?.let { put("imagePrompt", it) }
        program?.let { put("program", it) }
    }

    @Test
    fun imageModelEditParsesIdenticallyOnBothRoutes() {
        val arguments = figureArguments()
        val routeB = routeBCall(arguments)
        val routeA = routeACall(arguments)

        assertEquals(routeB, routeA)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, routeA.figureMethod)
        assertEquals(FigureImageOperation.EDIT, routeA.figureImageOperation)
        assertEquals("保留题干文字、标注和单位，只清除手写。", routeA.figureImagePrompt)
        assertNull(routeA.figureProgram)

        val proposal = requireNotNull(routeA.toFigureGenerationProposal())
        assertEquals(FigureGenerationDecision.GENERATE, proposal.decision)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, proposal.method)
        assertEquals(FigureImageOperation.EDIT, proposal.imageOperation)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, proposal.purpose)
    }

    @Test
    fun imageModelGenerateParsesIdenticallyOnBothRoutes() {
        val arguments = figureArguments(
            operation = "GENERATE",
            prompt = "画一条数轴，标出导数为正、负的区间。",
            kind = "GENERATE_PROCESS",
            description = "数轴标注导数符号区间",
        )
        val routeB = routeBCall(arguments)
        val routeA = routeACall(arguments)

        assertEquals(routeB, routeA)
        assertEquals(FigureImageOperation.GENERATE, routeA.figureImageOperation)
        assertEquals(
            FigureGenerationPurpose.EXPLANATION,
            requireNotNull(routeA.toFigureGenerationProposal()).purpose,
        )
    }

    @Test
    fun legacyKindAndDescriptionStillParseIdenticallyOnBothRoutes() {
        val arguments = figureArguments(method = null, operation = null, prompt = null)
        val routeB = routeBCall(arguments)
        val routeA = routeACall(arguments)

        assertEquals(routeB, routeA)
        assertNull(routeA.figureMethod)
        assertNull(routeA.figureImageOperation)
        assertNull(routeA.figureImagePrompt)
        assertEquals(TutorFigureKind.REDRAW_PROBLEM, routeA.figureKind)
    }

    @Test
    fun jsMethodIsRejectedOnBothRoutes() {
        // JS 运行库未过门：两条路由都必须拒绝，而不是"先收下再说"。
        val arguments = figureArguments(
            method = "JS",
            operation = "GENERATE",
            prompt = "draw",
            program = "const visual = {};",
        )

        val routeB = runCatching { routeBCall(arguments) }
        val routeA = runCatching { routeACall(arguments) }

        assertTrue("Route B 必须拒绝 JS：${routeB.exceptionOrNull()}", routeB.exceptionOrNull() is IllegalArgumentException)
        assertTrue("Route A 必须拒绝 JS：${routeA.exceptionOrNull()}", routeA.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun aProgramPayloadIsRejectedOnBothRoutes() {
        val arguments = figureArguments(
            method = "IMAGE_MODEL",
            operation = "EDIT",
            prompt = "保留原图",
            program = "const visual = {};",
        )

        assertTrue(
            runCatching { routeBCall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun anUnknownNativeArgumentIsRejectedJustLikeRouteB() {
        // 严格对拍：Route B 用 requireOnlyKeys 拒未知键；原生路由不得静默忽略。
        val arguments = JsonObject(figureArguments() + ("unexpected" to JsonPrimitive("x")))

        assertTrue(
            runCatching { routeBCall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun theNativeSchemaAdvertisesOnlyImageModelAndTheNewPayloadFields() {
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = respondInput(),
            images = emptyList(),
            enableNativeTools = true,
        )
        val figureBlock = functionBlock(body, "GENERATE_FIGURE")

        assertTrue("schema 必须声明 method", figureBlock.contains("\"method\""))
        assertTrue("method 只能广告 IMAGE_MODEL", figureBlock.contains("\"IMAGE_MODEL\""))
        assertFalse("JS 不得出现在工具声明里：$figureBlock", figureBlock.contains("\"JS\""))
        assertTrue("schema 必须声明 imageOperation", figureBlock.contains("\"imageOperation\""))
        assertTrue(figureBlock.contains("\"EDIT\""))
        assertTrue(figureBlock.contains("\"GENERATE\""))
        assertTrue("schema 必须声明 imagePrompt", figureBlock.contains("\"imagePrompt\""))
        assertTrue("imagePrompt must be bounded", figureBlock.contains("\"maxLength\""))
        // 旧字段保留（兼容过渡），但不出现别的工具的语义字段。
        assertTrue(figureBlock.contains("\"kind\""))
        assertTrue(figureBlock.contains("\"description\""))
        assertFalse(figureBlock.contains("\"direction\""))
    }

    private fun functionBlock(body: String, name: String): String {
        val rest = body.substringAfter("\"name\":\"$name\"", missingDelimiterValue = "")
        val next = rest.indexOf("\"name\":\"")
        return if (next >= 0) rest.substring(0, next) else rest
    }
}
