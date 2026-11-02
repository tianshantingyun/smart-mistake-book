package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelTaskCodec
import com.tingyun.smartmistakebook.core.model.ModelTaskCompletionValidator
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTextBudgetExceededException
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditChoiceSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditCommand
import com.tingyun.smartmistakebook.core.model.PaperEditCommandParser
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import com.tingyun.smartmistakebook.core.model.PaperEditParseResult
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditRemovedQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6B-3 改卷的 wire 半边（批次 40 / WS4 项 26/27）。
 *
 * - 提示词的白名单与解析器共用 `PaperEditCommand.TYPES`；
 * - 网关解析先过同一把白名单解析器，形状错误 → INVALID_RESPONSE 的前置异常，
 *   超预算 → 可重试的文本预算异常（KD-38 同出口）；
 * - 上游 `finish_reason` 截断在适配器之前分流（既有协议行为，本用例证明新 kind 也走同一条）。
 *
 * 批次 41 / WS7 项 43 追加两例：真实 Route B 请求体 → 响应 → 持久化编解码 → applier 口径解析的
 * 完整往返（含全部七类块 / 选项 / 备注 / 已删来源题），以及 schema 18 行不得携带改卷输入的
 * **读回侧**负向（构造期守卫之外的第二道）。
 */
class PaperEditAdapterTest {

    @Test
    fun promptListsEveryWhitelistedCommandFromTheParser() {
        val prompt = OpenAiModelTaskAdapters.prompt(input(includeNote = true))

        PaperEditCommand.TYPES.forEach { type ->
            assertTrue("提示词缺少白名单命令 $type：$prompt", prompt.contains(type))
        }
        assertTrue(prompt.contains("学生要求"))
        assertTrue(prompt.contains("把第 1 题标题改短"))
        assertTrue(prompt.contains("entry-a"))
        assertTrue(prompt.contains("stem-entry-a"))
        // 卷面快照没有图片字段：提示词里也不得有可被模型模仿的图片地址/本地路径。
        assertFalse(prompt.contains("localUri"))
        assertFalse(prompt.contains("data:image"))
        assertFalse(prompt.contains("file://"))
    }

    @Test
    fun promptOmitsSetNoteWhenTheLayoutHasNoNoteArea() {
        val prompt = OpenAiModelTaskAdapters.prompt(input(includeNote = false))

        // 不广告命令模板（提示词仍可提"不要输出 set_note"作为收窄说明）。
        assertFalse(prompt.contains("{\"type\":\"" + PaperEditCommand.TYPE_SET_NOTE + "\""))
        assertTrue(prompt.contains("当前版式不含备注区"))
    }

    @Test
    fun parseReturnsTheValidatedCommandJson() {
        val payload = buildJsonObject {
            put(
                "commands",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", PaperEditCommand.TYPE_SET_QUESTION_TITLE)
                            put("entryId", "entry-a")
                            put("title", "短一点的标题")
                        },
                    )
                },
            )
        }

        val output = OpenAiModelTaskAdapters.parse(payload, input(), "fake/paper-v1") as PaperEditOutput
        val parsed = PaperEditCommandParser.parse(output.commandsJson) as PaperEditParseResult.Parsed

        assertEquals(listOf(PaperEditCommand.TYPE_SET_QUESTION_TITLE), parsed.commands.map { it.type })
        assertEquals("fake/paper-v1", output.modelVersion)
    }

    @Test
    fun parseRejectsUnknownRootKeysAndUnknownCommandTypes() {
        val unknownRootKey = buildJsonObject {
            put("commands", buildJsonArray {})
            put("explanation", "顺便解释一下")
        }
        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiModelTaskAdapters.parse(unknownRootKey, input(), "fake/paper-v1")
        }

        val unknownType = buildJsonObject {
            put(
                "commands",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "replace_image")
                            put("entryId", "entry-a")
                        },
                    )
                },
            )
        }
        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiModelTaskAdapters.parse(unknownType, input(), "fake/paper-v1")
        }
    }

    @Test
    fun anOverBudgetResponseUsesTheRetryableTextBudgetException() {
        val hugeText = "x".repeat(PaperEditCommandParser.MAX_PAYLOAD_CHARS + 1)
        val payload = buildJsonObject {
            put(
                "commands",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", PaperEditCommand.TYPE_SET_BLOCK_TEXT)
                            put("entryId", "entry-a")
                            put("blockId", "stem-entry-a")
                            put("text", hugeText)
                        },
                    )
                },
            )
        }

        assertThrows(ModelTextBudgetExceededException::class.java) {
            OpenAiModelTaskAdapters.parse(payload, input(), "fake/paper-v1")
        }
    }

    @Test
    fun protocolParsesPaperEditResponsesAndKeepsTheFailureSplit() {
        val valid = OpenAiModelProtocol.parseResponse(
            responseBody = envelope(paperEditPayload()),
            input = input(),
            modelVersion = "fake/paper-v1",
        )
        assertTrue(valid is PaperEditOutput)

        // 形状错误：网关把它归到不可重试的 INVALID_RESPONSE。
        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiModelProtocol.parseResponse(
                responseBody = envelope(
                    """{"commands":[{"type":"replace_image","entryId":"entry-a"}]}""",
                ),
                input = input(),
                modelVersion = "fake/paper-v1",
            )
        }

        // 截断：网关在适配器之前分流到可重试的 INCOMPLETE_OUTPUT。
        assertThrows(IncompleteModelOutputException::class.java) {
            OpenAiModelProtocol.parseResponse(
                responseBody = envelope(paperEditPayload(), finishReason = "length"),
                input = input(),
                modelVersion = "fake/paper-v1",
            )
        }
    }

    @Test
    fun theWireRequestCarriesTheWholeBoundedSnapshotAndTheResponseRoundTripsBack() {
        // 批次 41 / WS7 项 43：往返要按**真实 wire 形状**走一遍，而不是只断言提示词里出现过几个词。
        // 具体失败：若 Route B 信封/提示词漏掉某一类块（如选项、已删来源题、备注），模型会对着
        // 残缺卷面输出命令，而 applier 按完整卷面执行——两边对不上，学生看到改错位置。
        val input = richInput()

        val body = OpenAiModelProtocol.requestBody(
            modelId = "fake/paper-v1",
            input = input,
            images = emptyList(),
        )
        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("fake/paper-v1", root["model"]!!.jsonPrimitive.contentOrNull)
        val sentPrompt = root["messages"]!!.jsonArray[1]
            .jsonObject["content"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.contentOrNull
        val prompt = requireNotNull(sentPrompt)

        assertEquals("发给模型的必须是同一份核心提示词", PaperEditPrompt.build(input), prompt)
        listOf(
            "学生要求：",
            "把第 1 题标题改短",
            "第 1 题 entryId=entry-a 标题=二次函数综合题",
            "block id=stem-entry-a kind=paragraph text=求函数的单调区间",
            "block id=heading-entry-a kind=section_heading text=第一部分",
            "block id=formula-entry-a kind=formula latex=x^{2}+1",
            "block id=choices-entry-a kind=choice_group prompt=下列哪个是二次函数 choices=a=y=x^2；b=y=x",
            "block id=figure-entry-a kind=figure（只读：不能改图或引用新图）",
            "block id=image-entry-a kind=image（只读：不能改图或引用新图）",
            "block id=note-entry-a kind=note（只读：改备注请用 set_note）",
            "备注=这题再练一次",
            "entryId=entry-b 标题=已删的题",
        ).forEach { fragment ->
            assertTrue("请求缺少卷面片段「$fragment」：$prompt", prompt.contains(fragment))
        }
        // 快照字段的**缺席**同样是契约：图/备注块只读、图片地址与本地路径不进模型。
        assertFalse(prompt.contains("localUri"))
        assertFalse(prompt.contains("data:image"))
        assertFalse(prompt.contains("file://"))
        assertFalse(prompt.contains("expectedContentSha256"))

        val output = OpenAiModelProtocol.parseResponse(
            responseBody = envelope(richPaperEditPayload()),
            input = input,
            modelVersion = "fake/paper-v1",
        ) as PaperEditOutput

        // 响应半边：同一份命令 PDF 先过白名单，再经持久化编解码读回，最后按 applier 的口径解析。
        val persisted = ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(output))
        assertEquals(output, persisted)
        val persistedOutput = persisted as PaperEditOutput
        val parsed = PaperEditCommandParser.parse(persistedOutput.commandsJson)
        assertTrue("$parsed", parsed is PaperEditParseResult.Parsed)
        assertEquals(
            listOf(
                PaperEditCommand.SetQuestionTitle("entry-a", "短一点的标题"),
                PaperEditCommand.SetChoiceText("entry-a", "choices-entry-a", "a", "y=2x^2"),
                PaperEditCommand.RestoreQuestion("entry-b"),
            ),
            (parsed as PaperEditParseResult.Parsed).commands,
        )
        assertTrue(
            ModelTaskCompletionValidator.validate(
                ModelTaskRequest(
                    requestId = "paper-edit:round-trip",
                    input = input,
                    occurredAtEpochMillis = 1_000,
                ),
                persisted,
            ).isEmpty(),
        )
    }

    @Test
    fun aSchemaNineteenRequestCannotBeDecodedUnderTheLegacySchema() {
        // 批次 41 / WS7 项 43 第二半：schema 19 的守卫不仅在**构造期**，也要在**持久化读回**处生效。
        // 具体失败：旧 schema 的行若能把 paper_edit_input 带过去，读回时不会有任何一处说"这一行
        // 自相矛盾"——指纹复原会按 schema 18 的键集解释它，账本/备份里就多了一类无法归因的行。
        val request = ModelTaskRequest(
            requestId = "paper-edit:schema-nineteen",
            input = richInput(),
            occurredAtEpochMillis = 1_000,
        )
        val encoded = ModelTaskCodec.encodeRequest(request)

        assertTrue("当前 schema 必须是 19：$encoded", encoded.contains("\"schemaVersion\":19"))
        assertTrue(encoded.contains("\"type\":\"paper_edit_input\""))
        val decoded = ModelTaskCodec.decodeRequest(encoded)
        assertEquals(request, decoded)
        assertEquals("读回再编码必须逐字节一致（请求指纹按字节复原）", encoded, ModelTaskCodec.encodeRequest(decoded))

        val smuggled = encoded.replace("\"schemaVersion\":19", "\"schemaVersion\":18")
        val rejected = runCatching { ModelTaskCodec.decodeRequest(smuggled) }
        assertTrue(
            "schema 18 的行不得携带改卷输入：${rejected.exceptionOrNull()}",
            rejected.exceptionOrNull() is IllegalArgumentException,
        )
    }

    private fun richInput() = PaperEditInput(
        paperId = "paper-1",
        revision = 3,
        instruction = "把第 1 题标题改短",
        questions = listOf(
            PaperEditQuestionSnapshot(
                entryId = "entry-a",
                title = "二次函数综合题",
                blocks = listOf(
                    PaperEditBlockSnapshot(
                        blockId = "stem-entry-a",
                        kind = PaperEditBlockKind.PARAGRAPH,
                        text = "求函数的单调区间",
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "heading-entry-a",
                        kind = PaperEditBlockKind.SECTION_HEADING,
                        text = "第一部分",
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "formula-entry-a",
                        kind = PaperEditBlockKind.FORMULA,
                        text = "x^{2}+1",
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "choices-entry-a",
                        kind = PaperEditBlockKind.CHOICE_GROUP,
                        text = "下列哪个是二次函数",
                        choices = listOf(
                            PaperEditChoiceSnapshot("a", "y=x^2"),
                            PaperEditChoiceSnapshot("b", "y=x"),
                        ),
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "figure-entry-a",
                        kind = PaperEditBlockKind.FIGURE,
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "image-entry-a",
                        kind = PaperEditBlockKind.IMAGE,
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "note-entry-a",
                        kind = PaperEditBlockKind.NOTE,
                    ),
                ),
                note = "这题再练一次",
            ),
        ),
        removedQuestions = listOf(
            PaperEditRemovedQuestion(entryId = "entry-b", title = "已删的题"),
        ),
        includeNote = true,
    )

    private fun richPaperEditPayload(): String = Json.encodeToString(
        buildJsonObject {
            put(
                "commands",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", PaperEditCommand.TYPE_SET_QUESTION_TITLE)
                            put("entryId", "entry-a")
                            put("title", "短一点的标题")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("type", PaperEditCommand.TYPE_SET_CHOICE_TEXT)
                            put("entryId", "entry-a")
                            put("blockId", "choices-entry-a")
                            put("choiceId", "a")
                            put("text", "y=2x^2")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("type", PaperEditCommand.TYPE_RESTORE_QUESTION)
                            put("entryId", "entry-b")
                        },
                    )
                },
            )
        },
    )

    private fun paperEditPayload(): String = Json.encodeToString(
        buildJsonObject {
            put(
                "commands",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", PaperEditCommand.TYPE_SET_QUESTION_TITLE)
                            put("entryId", "entry-a")
                            put("title", "短一点的标题")
                        },
                    )
                },
            )
        },
    )

    private fun envelope(content: String, finishReason: String? = null): String = Json.encodeToString(
        buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("message", buildJsonObject { put("content", content) })
                            finishReason?.let { put("finish_reason", it) }
                        },
                    )
                },
            )
        },
    )

    private fun input(includeNote: Boolean = false) = PaperEditInput(
        paperId = "paper-1",
        revision = 3,
        instruction = "把第 1 题标题改短",
        questions = listOf(
            PaperEditQuestionSnapshot(
                entryId = "entry-a",
                title = "二次函数综合题",
                blocks = listOf(
                    PaperEditBlockSnapshot(
                        blockId = "stem-entry-a",
                        kind = PaperEditBlockKind.PARAGRAPH,
                        text = "求函数的单调区间",
                    ),
                ),
            ),
        ),
        includeNote = includeNote,
    )
}
