package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-82 收口：GENERATE_FIGURE 工具 schema 的 `program.maxLength`（JSON Schema 语义按
 * Unicode 码点计）与本地 UTF-8 字节门（[FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES] =
 * 65536，`TutorToolLoop.kt` 解析门与 `FigureGenerationPolicy.kt` 策略门，不动）的对齐。
 *
 * 消灭的具体失败：旧广告面把字节上限常量（65536）直接塞进按**码点**计数的 `maxLength`，
 * 非 ASCII 下"schema 合法"的串最坏可达字节门的 4×——模型按 schema 写满预算，调用在本地门
 * 被 fail-closed 拒掉，白烧一轮工具调用。广告面改 ⌊65536/4⌋ = 16384 后两条方向恒成立：
 * - schema 合法（≤16384 码点）⇒ UTF-8 ≤ 4×16384 = 65536 字节 ⇒ 必过本地字节门（含边界）；
 * - 本地字节门拒绝（> 65536 字节）⇒ 码点 > 16384 ⇒ schema 同样不合法（不再过度广告）。
 *
 * 负例取 **16385**（而非恰 16384）个 4 字节字符：16384 × 4 = 65536 字节恰在门的上界，
 * 门按 `≤`/`!(>)` 计含边界——16384 个 4 字节字符是**正例**（见 [theWorstCaseFourByteProgramAtTheAdvertisedBoundPassesBothGates]），
 * "16384 码点合法却被字节门拒"在新广告面下按定义不存在（这正是本修复消灭的失败）。
 */
class ProgramSchemaMaxLengthAlignmentTest {

    /** JSON Schema 的码点上限 = 字节门 ÷ 最坏 4 字节密度（与 OpenAiModelProtocol 同一派生）。 */
    private val schemaMaxLength = FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES / 4

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
        toolDeclarations = listOf(TutorToolName.GENERATE_FIGURE),
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

    private fun figureArguments(program: String): JsonObject = buildJsonObject {
        put("terms", buildJsonArray { })
        put("rationale", "图形是这道题的理解关键")
        put("kind", "REDRAW_PROBLEM")
        put("description", "重绘题面去手写")
        put("method", "JS")
        put("program", program)
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

    private fun figureParameters(body: String): JsonObject {
        val root = Json.parseToJsonElement(body).jsonObject
        return root["tools"]!!.jsonArray
            .map { it.jsonObject }
            .single {
                it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content == "GENERATE_FIGURE"
            }
            .let { it["function"]!!.jsonObject["parameters"]!!.jsonObject }
    }

    @Test
    fun programSchemaFacetIsTheByteSafeCodePointBound() {
        // 广告面必须等于 ⌊字节门/4⌋：码点语义下的字节安全上界。
        val body = OpenAiModelProtocol.requestBody(
            modelId = "test-model",
            input = respondInput(),
            images = emptyList(),
            enableNativeTools = true,
            figureMethods = setOf(FigureGenerationMethod.IMAGE_MODEL, FigureGenerationMethod.JS),
        )
        val properties = figureParameters(body)["properties"]!!.jsonObject
        val programMaxLength = properties["program"]!!.jsonObject["maxLength"]!!.jsonPrimitive.int

        assertEquals(schemaMaxLength, programMaxLength)
        assertTrue(
            "码点广告面 × 最坏 4 字节密度必须不越过本地字节门",
            programMaxLength * 4 <= FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES,
        )
        // 本批裁定只动 program：imagePrompt 的 4096 码点面原样（对 4096 字节门仍有最坏 4×
        // 过度广告，残余按台账登记），不得被顺手改掉。
        assertEquals(
            FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES,
            properties["imagePrompt"]!!.jsonObject["maxLength"]!!.jsonPrimitive.int,
        )
    }

    @Test
    fun aFullAsciiProgramAtTheAdvertisedBoundPassesSchemaAndTheLocalByteGate() {
        // 正例：16384 个 ASCII 字符 = 16384 码点（= 广告面，schema 合法）
        // = 16384 UTF-8 字节（远低于 65536 字节门）——schema 合法且本地门放行。
        val program = "a".repeat(schemaMaxLength)

        assertEquals(schemaMaxLength, program.length)
        assertEquals(schemaMaxLength, figureUsageUtf8Bytes(program))

        val call = routeACall(figureArguments(program = program))
        assertEquals(program, call.figureProgram)
    }

    @Test
    fun theWorstCaseFourByteProgramAtTheAdvertisedBoundPassesBothGates() {
        // 边界例（对齐的钉子）：16384 个 4 字节字符（U+1D54F）= 16384 码点（= 广告面，
        // schema 合法）= 65536 UTF-8 字节，恰在字节门上界（门按含边界计）——
        // "schema 合法 ⇒ 本地通过"在最坏非 ASCII 密度下仍成立。
        val program = "𝕏".repeat(schemaMaxLength)

        assertEquals(schemaMaxLength, program.codePointCount(0, program.length))
        assertEquals(FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES, figureUsageUtf8Bytes(program))

        val call = routeACall(figureArguments(program = program))
        assertEquals(program, call.figureProgram)
    }

    @Test
    fun aProgramPastTheAdvertisedBoundIsRejectedFailClosedWithTheByteCount() {
        // 负例：16385 个 4 字节字符 = 16385 码点（> 广告面 16384——旧的 65536 码点广告面
        // 曾把它当合法）= 65540 UTF-8 字节（> 65536 字节门）。fail-closed 方向正确：
        // 本地门按字节拒绝且错误信息带字节计数，schema 也不再广告这个串。
        val program = "𝕏".repeat(schemaMaxLength + 1)

        assertEquals(schemaMaxLength + 1, program.codePointCount(0, program.length))
        assertTrue(
            "负例必须真实越过本地字节门",
            figureUsageUtf8Bytes(program) > FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES,
        )

        val rejected = runCatching { routeACall(figureArguments(program = program)) }
        val message = requireNotNull(rejected.exceptionOrNull()?.message)
        assertTrue(
            "拒绝信息必须按 UTF-8 字节计数：$message",
            message.contains("${FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES} UTF-8 bytes"),
        )
    }
}
