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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b / A-02 / A-03：`GENERATE_FIGURE` 的
 * method/program/imageOperation/imagePrompt 在**两条路由**
 * （Route A 原生 tool_calls / Route B json_object 信封）上逐字同义（T3）。
 *
 * 消灭的具体失败（KD-30 的同类）：原生 schema 广告了新字段、但 Route A 解析不读，
 * 或只有 Route B 读——某种 provider 上模型的选择被静默丢弃，而两侧单看都"能跑"。
 *
 * A-02/A-03 之后 JS 是**契约层**可表达的合法选择：两路必须解析出同形的
 * `TutorToolCall`（含独立字段 `program`）。负向（缺 program、夹带图像载荷、program 越界、
 * program 出现在别的工具上、未知键）一并保留，一条都不放宽。
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
    private fun routeBCall(
        arguments: JsonObject,
        tool: String = "GENERATE_FIGURE",
    ): TutorToolCall {
        val payload = intentDecision().toMutableMap().apply {
            put(
                "toolRequests",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("tool", tool)
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
    private fun routeACall(
        arguments: JsonObject,
        tool: String = "GENERATE_FIGURE",
    ): TutorToolCall {
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
                                                            put("name", tool)
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
    fun jsMethodParsesIdenticallyOnBothRoutesWithItsProgram() {
        // A-02（X-04 正向）：JS 在**两条路由**上都可表达，且 program 逐字节相同。
        // 夹带 \r\n、反斜杠、中文与 emoji，防止某一路做了转义/换行归一。
        val program = "renderScene({\r\n  title: '数轴\\\\标注',\r\n  label: \"导数区间 📐\"\r\n});"
        val arguments = figureArguments(
            method = "JS",
            operation = null,
            prompt = null,
            program = program,
        )

        val routeB = routeBCall(arguments)
        val routeA = routeACall(arguments)

        assertEquals("两路由必须解析出同一个 TutorToolCall", routeB, routeA)
        assertEquals(FigureGenerationMethod.JS, routeA.figureMethod)
        assertEquals(program, routeA.figureProgram)
        assertNull(routeA.figureImageOperation)
        assertNull(routeA.figureImagePrompt)

        val proposalB = requireNotNull(routeB.toFigureGenerationProposal())
        val proposalA = requireNotNull(routeA.toFigureGenerationProposal())
        assertEquals(proposalB, proposalA)
        assertEquals(FigureGenerationMethod.JS, proposalA.method)
        assertEquals(program, proposalA.program)
        // 两路结果同形：JS proposal 不带任何图像载荷。
        assertNull(proposalA.imageOperation)
        assertNull(proposalA.imagePrompt)

        // Route A 的 strict schema 会把**全部属性**都输出（不适用的一律 null）。
        // 这条证明"provider 按广告的 schema 产出的 JS arguments"在两条路由上都解析成同一个
        // 调用——研究复核里"手造 arguments 不能证明 native 路可执行"的缺口由它补上。
        val strictShape = JsonObject(
            arguments + ("imageOperation" to JsonNull) + ("imagePrompt" to JsonNull),
        )
        assertEquals(routeB, routeBCall(strictShape))
        assertEquals(routeA, routeACall(strictShape))
    }

    @Test
    fun anImageModelCallWithAnExplicitNullProgramParsesLikeTheLegacyShape() {
        // 旧请求（没有 program 字段）与新 schema 下的 `program: null` 必须逐字同义。
        val legacy = figureArguments()
        val withNullProgram = JsonObject(legacy + ("program" to JsonNull))

        assertEquals(routeBCall(legacy), routeBCall(withNullProgram))
        assertEquals(routeACall(legacy), routeACall(withNullProgram))
    }

    @Test
    fun jsWithoutAProgramIsRejectedOnBothRoutes() {
        // 保留的负向（X-04）：JS 的源码只能走独立字段 program，缺失即拒。
        val arguments = figureArguments(method = "JS", operation = null, prompt = null, program = null)

        assertTrue(
            runCatching { routeBCall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun javascriptInTheDescriptionIsNotAProgram() {
        // 负向：把源码塞进 description 不算 program——不猜文本、不二次提取。
        val arguments = figureArguments(
            method = "JS",
            operation = null,
            prompt = null,
            program = null,
            description = "renderScene({ kind: 'number-line' });",
        )

        assertTrue(
            runCatching { routeBCall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun jsWithAnImagePromptIsRejectedOnBothRoutes() {
        // 保留的负向（X-04）：两种方法的载荷互斥；JS 不得夹带图像提示词。
        val arguments = figureArguments(
            method = "JS",
            operation = null,
            prompt = "画一条数轴",
            program = "renderScene({});",
        )

        assertTrue(
            runCatching { routeBCall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun aProgramOverSixtyFourKibibytesIsRejectedOnBothRoutes() {
        // 预算按 UTF-8 字节（ASCII 1:1）：64 KiB + 1 B 必须拒。
        val overBudget = "x".repeat(
            com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES + 1,
        )
        val arguments = figureArguments(method = "JS", operation = null, prompt = null, program = overBudget)

        assertTrue(
            runCatching { routeBCall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun aProgramOutsideGenerateFigureIsRejectedOnBothRoutes() {
        // 负向：program 只属于 GENERATE_FIGURE，别的工具带它就是未知载荷。
        val arguments = buildJsonObject {
            put("terms", buildJsonArray { add(JsonPrimitive("单调性")) })
            put("rationale", "学生问到单调性")
            put("program", "renderScene({});")
        }

        assertTrue(
            runCatching { routeBCall(arguments, tool = "KNOWLEDGE_READ") }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { routeACall(arguments, tool = "KNOWLEDGE_READ") }
                .exceptionOrNull() is IllegalArgumentException,
        )
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
        // 生产默认（不传 figureMethods）：广告面与本批之前逐字节相同——零广告改动。
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
        assertFalse(
            "生产默认不得广告 JS 的 program 载体：$figureBlock",
            figureBlock.contains("\"program\""),
        )
        assertTrue("schema 必须声明 imageOperation", figureBlock.contains("\"imageOperation\""))
        assertTrue(figureBlock.contains("\"EDIT\""))
        assertTrue(figureBlock.contains("\"GENERATE\""))
        assertTrue("schema 必须声明 imagePrompt", figureBlock.contains("\"imagePrompt\""))
        assertTrue("imagePrompt must be bounded", figureBlock.contains("\"maxLength\""))
        // 旧字段保留（兼容过渡），但不出现别的工具的语义字段。
        assertTrue(figureBlock.contains("\"kind\""))
        assertTrue(figureBlock.contains("\"description\""))
        assertFalse(figureBlock.contains("\"direction\""))
        // strict 口径：required 恰好覆盖全部 properties。
        assertStrictRequiredCoversEveryProperty(figureParameters(body))
    }

    @Test
    fun theNativeSchemaCanAdvertiseJavascriptWithProgramAsAnIndependentField() {
        // A-03：能力集合显式含 JS 时，schema 必须**能**同时广告两方法，且 JS 的载荷是
        // 独立字段 `program`；图像字段改为可空——否则 strict schema 里 JS 调用结构性地
        // 产不出合法 arguments（这正是研究复核指出的 A-03 缺口）。
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = respondInput(),
            images = emptyList(),
            enableNativeTools = true,
            figureMethods = setOf(FigureGenerationMethod.IMAGE_MODEL, FigureGenerationMethod.JS),
        )
        val params = figureParameters(body)
        val properties = params["properties"]!!.jsonObject
        val figureBlock = functionBlock(body, "GENERATE_FIGURE")

        assertEquals(
            "method.enum 必须同时广告两方法",
            listOf("IMAGE_MODEL", "JS"),
            enumValues(properties["method"]!!.jsonObject),
        )
        assertTrue("program 必须是独立属性", "program" in properties)
        assertFalse(
            "program 不得被塞进 description 蒙混",
            params["properties"]!!.jsonObject["description"]!!
                .jsonObject["description"]!!
                .jsonPrimitive.content.contains("program"),
        )
        assertEquals(
            "JS 调用必须能表达\"不带图像操作\"",
            listOf("string", "null"),
            typeUnion(properties["imageOperation"]!!.jsonObject),
        )
        assertEquals(
            "JS 调用必须能表达\"不带图像提示词\"",
            listOf("string", "null"),
            typeUnion(properties["imagePrompt"]!!.jsonObject),
        )
        assertEquals(
            "program 同样可空（IMAGE_MODEL 调用不填）",
            listOf("string", "null"),
            typeUnion(properties["program"]!!.jsonObject),
        )
        assertTrue(figureBlock.contains("\"JS\""))
        assertEquals(JsonPrimitive(false), params["additionalProperties"])
        assertStrictRequiredCoversEveryProperty(params)
    }

    private fun figureParameters(body: String): JsonObject {
        val root = Json.parseToJsonElement(body).jsonObject
        return root["tools"]!!.jsonArray
            .map { it.jsonObject }
            .single {
                it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content == "GENERATE_FIGURE"
            }
            .let { it["function"]!!.jsonObject["parameters"]!!.jsonObject }
    }

    private fun enumValues(schema: JsonObject): List<String> =
        schema["enum"]!!.jsonArray.map { it.jsonPrimitive.content }

    private fun typeUnion(schema: JsonObject): List<String> =
        schema["type"]!!.jsonArray.map { it.jsonPrimitive.content }

    private fun assertStrictRequiredCoversEveryProperty(params: JsonObject) {
        val properties = params["properties"]!!.jsonObject.keys.sorted()
        val required = params["required"]!!.jsonArray.map { it.jsonPrimitive.content }.sorted()
        assertEquals(
            "strict schema 的 required 必须恰好覆盖 properties（additionalProperties=false）",
            properties,
            required,
        )
    }

    private fun functionBlock(body: String, name: String): String {
        val rest = body.substringAfter("\"name\":\"$name\"", missingDelimiterValue = "")
        val next = rest.indexOf("\"name\":\"")
        return if (next >= 0) rest.substring(0, next) else rest
    }
}
