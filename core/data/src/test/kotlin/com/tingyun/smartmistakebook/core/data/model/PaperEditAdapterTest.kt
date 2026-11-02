package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelTextBudgetExceededException
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditCommand
import com.tingyun.smartmistakebook.core.model.PaperEditCommandParser
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import com.tingyun.smartmistakebook.core.model.PaperEditParseResult
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot
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
 * 6B-3 改卷的 wire 半边（批次 40 / WS4 项 26/27）。
 *
 * - 提示词的白名单与解析器共用 `PaperEditCommand.TYPES`；
 * - 网关解析先过同一把白名单解析器，形状错误 → INVALID_RESPONSE 的前置异常，
 *   超预算 → 可重试的文本预算异常（KD-38 同出口）；
 * - 上游 `finish_reason` 截断在适配器之前分流（既有协议行为，本用例证明新 kind 也走同一条）。
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
