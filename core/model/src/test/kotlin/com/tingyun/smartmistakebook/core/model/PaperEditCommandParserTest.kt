package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6B-3 白名单命令的**形状门**：模型只能输出这七种命令，多一个字段、换一种类型、写一段代码
 * 都整条拒绝。这里钉的是解析层；"id 在不在当前卷面"由 feature:tutor 的 applier 钉。
 */
class PaperEditCommandParserTest {

    @Test
    fun everyWhitelistedCommandParsesWithItsDeclaredFields() {
        val result = PaperEditCommandParser.parse(
            """
            {"commands":[
              {"type":"remove_question","entryId":"entry-b"},
              {"type":"restore_question","entryId":"entry-b"},
              {"type":"move_question","entryId":"entry-a","offset":-1},
              {"type":"set_question_title","entryId":"entry-a","title":"短一点的标题"},
              {"type":"set_block_text","entryId":"entry-a","blockId":"stem","text":"改过的题干"},
              {"type":"set_choice_text","entryId":"entry-a","blockId":"choice-1","choiceId":"a","text":"甲"},
              {"type":"set_note","entryId":"entry-a","note":"卷面备注"}
            ]}
            """.trimIndent(),
        )

        val parsed = result as PaperEditParseResult.Parsed
        assertEquals(
            listOf(
                PaperEditCommand.TYPE_REMOVE_QUESTION,
                PaperEditCommand.TYPE_RESTORE_QUESTION,
                PaperEditCommand.TYPE_MOVE_QUESTION,
                PaperEditCommand.TYPE_SET_QUESTION_TITLE,
                PaperEditCommand.TYPE_SET_BLOCK_TEXT,
                PaperEditCommand.TYPE_SET_CHOICE_TEXT,
                PaperEditCommand.TYPE_SET_NOTE,
            ),
            parsed.commands.map { it.type },
        )
        assertEquals(
            PaperEditCommand.MoveQuestion("entry-a", -1),
            parsed.commands[2],
        )
        assertEquals(
            PaperEditCommand.SetChoiceText("entry-a", "choice-1", "a", "甲"),
            parsed.commands[5],
        )
        assertEquals(PaperEditCommand.TYPES, parsed.commands.map { it.type }.toSet())
    }

    @Test
    fun anUnknownCommandTypeIsRejectedInsteadOfIgnored() {
        val result = PaperEditCommandParser.parse(
            """{"commands":[{"type":"replace_image","entryId":"entry-a","localUri":"file:///x.png"}]}""",
        )

        val rejected = result as PaperEditParseResult.Rejected
        assertTrue(rejected.reason.contains("replace_image"))
        assertTrue(rejected.reason.contains("白名单"))
    }

    @Test
    fun anExtraFieldSuchAsAnImageUriRejectsTheWholeCommand() {
        val result = PaperEditCommandParser.parse(
            """
            {"commands":[
              {"type":"set_block_text","entryId":"entry-a","blockId":"stem","text":"x",
               "localUri":"file:///private/clean.png","reason":"把图换掉"}
            ]}
            """.trimIndent(),
        )

        val rejected = result as PaperEditParseResult.Rejected
        assertTrue(rejected.reason.contains("白名单外"))
        assertTrue(rejected.reason.contains("localUri"))
        assertTrue(rejected.reason.contains("reason"))
    }

    @Test
    fun codeFencesAndFreeTextAreNotMasqueradedIntoCommands() {
        listOf(
            "```json\n{\"commands\":[]}\n```",
            "好的，我来改卷面：{\"commands\":[]}",
            "{\"commands\":[",
        ).forEach { raw ->
            val rejected = PaperEditCommandParser.parse(raw) as PaperEditParseResult.Rejected
            assertTrue(rejected.reason.contains("不是合法 JSON"))
        }
    }

    @Test
    fun theRootShapeIsExactlyTheCommandsObject() {
        (PaperEditCommandParser.parse("[]") as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("JSON 对象"))
        }
        (PaperEditCommandParser.parse("""{"commands":{},"note":"x"}""")
            as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("只能包含 commands"))
        }
        (PaperEditCommandParser.parse("""{"commands":{}}""")
            as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("必须是数组"))
        }
    }

    @Test
    fun wrongFieldTypesAreRejected() {
        listOf(
            """{"commands":[{"type":"move_question","entryId":"entry-a","offset":"1"}]}""",
            """{"commands":[{"type":"move_question","entryId":"entry-a","offset":1.5}]}""",
            """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":3}]}""",
            """{"commands":[{"type":"set_note","entryId":"entry-a","note":false}]}""",
            """{"commands":[{"type":"remove_question","entryId":""}]}""",
        ).forEach { raw ->
            val rejected = PaperEditCommandParser.parse(raw) as PaperEditParseResult.Rejected
            assertTrue("expected a rejection for $raw, got ${rejected.reason}", rejected.reason.isNotBlank())
        }
    }

    @Test
    fun zeroAndHugeMoveOffsetsAreRejected() {
        (PaperEditCommandParser.parse(
            """{"commands":[{"type":"move_question","entryId":"entry-a","offset":0}]}""",
        ) as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("不能为 0"))
        }
        (PaperEditCommandParser.parse(
            """{"commands":[{"type":"move_question","entryId":"entry-a","offset":${
                PaperEditCommandParser.MAX_MOVE_OFFSET + 1
            }}]}""",
        ) as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("越界"))
        }
    }

    @Test
    fun blankRequiredTextIsRejectedButBlankBlockTextAndNoteAreLeftToTheApplier() {
        (PaperEditCommandParser.parse(
            """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"  "}]}""",
        ) as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("title"))
        }
        (PaperEditCommandParser.parse(
            """{"commands":[{"type":"set_choice_text","entryId":"entry-a","blockId":"c","choiceId":"a","text":""}]}""",
        ) as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("text"))
        }
        // 选择题题干允许为空（与导出准入口径一致），所以解析层放行；由 applier 按块类型判断。
        val parsed = PaperEditCommandParser.parse(
            """{"commands":[{"type":"set_block_text","entryId":"entry-a","blockId":"c","text":""}]}""",
        ) as PaperEditParseResult.Parsed
        assertEquals(PaperEditCommand.SetBlockText("entry-a", "c", ""), parsed.commands.single())
        val clearedNote = PaperEditCommandParser.parse(
            """{"commands":[{"type":"set_note","entryId":"entry-a","note":""}]}""",
        ) as PaperEditParseResult.Parsed
        assertEquals(PaperEditCommand.SetNote("entry-a", ""), clearedNote.commands.single())
    }

    @Test
    fun emptyCommandsAreParsedSoTheCallerCanExplainThereWasNothingToDo() {
        val parsed = PaperEditCommandParser.parse("""{"commands":[]}""")
            as PaperEditParseResult.Parsed
        assertTrue(parsed.commands.isEmpty())
    }

    @Test
    fun payloadSizeAndCommandCountAreBounded() {
        val tooMany = (1..(PaperEditCommandParser.MAX_COMMANDS + 1)).joinToString(",") {
            """{"type":"remove_question","entryId":"entry-a"}"""
        }
        (PaperEditCommandParser.parse("""{"commands":[$tooMany]}""")
            as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("最多"))
        }
        val oversized = """{"commands":[{"type":"set_note","entryId":"entry-a","note":"${
            "x".repeat(PaperEditCommandParser.MAX_PAYLOAD_CHARS)
        }"}]}"""
        (PaperEditCommandParser.parse(oversized) as PaperEditParseResult.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("太长"))
        }
    }
}
