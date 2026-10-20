package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 6B-3 有界自然语言改卷的**唯一命令白名单**（PRD-10「6B-3 自然语言改卷」）。
 *
 * 模型不是直接改文档，也不是输出代码：它只能从这里的七种命令里选，命令只带**卷面上已有的**
 * `entryId` / `blockId` / `choiceId` 与文字。真正改卷的仍是 `MistakePdfPaperEdit` 的既有方法
 * （`feature:tutor` 的 applier 负责调用），因此：
 * - **不改原题/原答案**：命令里没有写回错题条目的通道，也没有 revision/answer 字段；
 * - **不自由生成代码**：命令是纯数据，本地不解释、不执行任何表达式；
 * - **图引用只限既有/角色安全**：命令里**没有**图片字段。`set_block_text` 落到图块会被 applier
 *   拒绝；任何 `localUri`/`image`/`schema`/`svg` 之类的额外字段在解析层就整条拒绝。
 *
 * 为什么把解析放在 `core:model`：它是纯 JVM 模块，已有 kotlinx.serialization JSON 依赖；
 * `core:export` 是只读复用（本批不修改），`feature:tutor` 没有直接声明 JSON 依赖。
 * 解析层只做**形状**校验（键集、类型、长度、范围）；"这个 id 在不在当前卷面"由 applier
 * 按当前文档校验——那需要 `MistakePdfPaperEdit`，不能放在这里。
 */
sealed interface PaperEditCommand {
    val type: String

    /** 删除卷面上的一道题（至少留一道）。 */
    data class RemoveQuestion(val entryId: String) : PaperEditCommand {
        override val type: String get() = TYPE_REMOVE_QUESTION
    }

    /**
     * 把一道**已从卷面删除、但仍属于本次来源**的题加回。
     *
     * 这是唯一的"增"：模型不能从别处点题，也不能编造 `entryId`——卷面上的题来自本地选题，
     * 模型没有新增题目的通道。
     */
    data class RestoreQuestion(val entryId: String) : PaperEditCommand {
        override val type: String get() = TYPE_RESTORE_QUESTION
    }

    /** 调序：`offset` 为负=上移，正=下移，0 与越界都拒。 */
    data class MoveQuestion(val entryId: String, val offset: Int) : PaperEditCommand {
        override val type: String get() = TYPE_MOVE_QUESTION
    }

    /** 改卷面上的题目标题（不写回题目本身）。 */
    data class SetQuestionTitle(val entryId: String, val title: String) : PaperEditCommand {
        override val type: String get() = TYPE_SET_QUESTION_TITLE
    }

    /** 改一个文字块：段落 / 小标题 / 公式（LaTeX）/ 选择题题干。 */
    data class SetBlockText(
        val entryId: String,
        val blockId: String,
        val text: String,
    ) : PaperEditCommand {
        override val type: String get() = TYPE_SET_BLOCK_TEXT
    }

    /** 改一个选项的文字。 */
    data class SetChoiceText(
        val entryId: String,
        val blockId: String,
        val choiceId: String,
        val text: String,
    ) : PaperEditCommand {
        override val type: String get() = TYPE_SET_CHOICE_TEXT
    }

    /** 改/清空卷面备注（只改草稿；绝不写回 `error_book_entry.user_note`）。 */
    data class SetNote(val entryId: String, val note: String) : PaperEditCommand {
        override val type: String get() = TYPE_SET_NOTE
    }

    companion object {
        const val TYPE_REMOVE_QUESTION = "remove_question"
        const val TYPE_RESTORE_QUESTION = "restore_question"
        const val TYPE_MOVE_QUESTION = "move_question"
        const val TYPE_SET_QUESTION_TITLE = "set_question_title"
        const val TYPE_SET_BLOCK_TEXT = "set_block_text"
        const val TYPE_SET_CHOICE_TEXT = "set_choice_text"
        const val TYPE_SET_NOTE = "set_note"

        /** 提示词与解析器共用的白名单（顺序稳定，便于两处对拍）。 */
        val TYPES: Set<String> = linkedSetOf(
            TYPE_REMOVE_QUESTION,
            TYPE_RESTORE_QUESTION,
            TYPE_MOVE_QUESTION,
            TYPE_SET_QUESTION_TITLE,
            TYPE_SET_BLOCK_TEXT,
            TYPE_SET_CHOICE_TEXT,
            TYPE_SET_NOTE,
        )
    }
}

/** 解析结果：要么得到一组命令，要么整条拒绝并给出一句学生能看懂的原因。 */
sealed interface PaperEditParseResult {
    data class Parsed(val commands: List<PaperEditCommand>) : PaperEditParseResult

    data class Rejected(val reason: String) : PaperEditParseResult
}

/**
 * 模型输出的严格解析器（白名单形状）。
 *
 * 形状是拒绝的依据：根对象键集必须**恰好**是 `commands`；每条命令的键集必须**恰好**是它
 * 声明的那几个；多一个字段（包括 `localUri`/`code`/`schema`）都不放行。这样"模型塞了一个
 * 图片地址"或"模型顺便写了段代码"不会变成一条被忽略的字段，而是整条修改被拒绝、草稿保持原样。
 *
 * 长度上限是 DoS/误输出边界，不是排版限制：超长 payload 在解析前就被拒绝，不尝试构造巨大的
 * 中间对象。空 `commands` 是合法的"没有可执行修改"，由调用方如实告诉学生。
 */
object PaperEditCommandParser {
    /** 原始 JSON 的字符上限（远大于一次正常改卷所需）。 */
    const val MAX_PAYLOAD_CHARS = 32_768

    /** 一次改卷最多接受的命令数。 */
    const val MAX_COMMANDS = 24

    /** `entryId` / `blockId` / `choiceId` 的长度上限。 */
    const val MAX_ID_CHARS = 160

    /** 标题、备注、段落/分值文字的字符上限。 */
    const val MAX_TITLE_CHARS = 400
    const val MAX_NOTE_CHARS = 2_000
    const val MAX_TEXT_CHARS = 8_000

    /** 单条调序命令的偏移绝对值上限（远大于卷面最大题数，只防离谱输入）。 */
    const val MAX_MOVE_OFFSET = 1_000

    private val rootKeys = setOf("commands")
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(raw: String): PaperEditParseResult {
        if (raw.isBlank()) {
            return PaperEditParseResult.Rejected("编辑命令是空的。")
        }
        if (raw.length > MAX_PAYLOAD_CHARS) {
            return PaperEditParseResult.Rejected(
                "编辑命令太长（最多 $MAX_PAYLOAD_CHARS 字）。",
            )
        }
        val root = try {
            json.parseToJsonElement(raw)
        } catch (_: Exception) {
            // 模型回了代码块、解释文字或截断的 JSON：整条拒绝，不做"从中挑一段"的猜测。
            return PaperEditParseResult.Rejected(
                "编辑命令不是合法 JSON；只接受 {\"commands\": [...]} 这一种形状。",
            )
        }
        if (root !is JsonObject) {
            return PaperEditParseResult.Rejected("编辑命令必须是 JSON 对象。")
        }
        if (root.keys != rootKeys) {
            return PaperEditParseResult.Rejected(
                "编辑命令只能包含 commands 字段" + keysHint(rootKeys, root.keys) + "。",
            )
        }
        val array = root["commands"] as? JsonArray
            ?: return PaperEditParseResult.Rejected("commands 必须是数组。")
        if (array.size > MAX_COMMANDS) {
            return PaperEditParseResult.Rejected(
                "一次最多 $MAX_COMMANDS 条编辑命令。",
            )
        }
        val commands = ArrayList<PaperEditCommand>(array.size)
        array.forEachIndexed { index, element ->
            val ordinal = index + 1
            when (val parsed = parseCommand(element, ordinal)) {
                is CommandParse.Ok -> commands += parsed.command
                is CommandParse.Bad -> return PaperEditParseResult.Rejected(parsed.reason)
            }
        }
        return PaperEditParseResult.Parsed(commands)
    }

    private fun parseCommand(element: JsonElement, ordinal: Int): CommandParse {
        val objectValue = element as? JsonObject
            ?: return CommandParse.Bad("第 $ordinal 条编辑命令必须是 JSON 对象。")
        val type = objectValue.stringValue("type")
            ?: return CommandParse.Bad("第 $ordinal 条编辑命令缺少字符串字段 type。")
        val expectedKeys = when (type) {
            PaperEditCommand.TYPE_REMOVE_QUESTION,
            PaperEditCommand.TYPE_RESTORE_QUESTION,
            -> setOf("type", "entryId")

            PaperEditCommand.TYPE_MOVE_QUESTION -> setOf("type", "entryId", "offset")
            PaperEditCommand.TYPE_SET_QUESTION_TITLE -> setOf("type", "entryId", "title")
            PaperEditCommand.TYPE_SET_BLOCK_TEXT -> setOf("type", "entryId", "blockId", "text")
            PaperEditCommand.TYPE_SET_CHOICE_TEXT ->
                setOf("type", "entryId", "blockId", "choiceId", "text")
            PaperEditCommand.TYPE_SET_NOTE -> setOf("type", "entryId", "note")
            else -> return CommandParse.Bad(
                "第 $ordinal 条编辑命令的类型「$type」不在白名单里。",
            )
        }
        if (objectValue.keys != expectedKeys) {
            return CommandParse.Bad(
                "第 $ordinal 条编辑命令的形状不对" +
                    keysHint(expectedKeys, objectValue.keys) + "。",
            )
        }
        return when (type) {
            PaperEditCommand.TYPE_REMOVE_QUESTION -> CommandParse.Ok(
                PaperEditCommand.RemoveQuestion(
                    entryId = objectValue.requiredId("entryId") ?: return missingId(
                        ordinal,
                        "entryId",
                    ),
                ),
            )

            PaperEditCommand.TYPE_RESTORE_QUESTION -> CommandParse.Ok(
                PaperEditCommand.RestoreQuestion(
                    entryId = objectValue.requiredId("entryId") ?: return missingId(
                        ordinal,
                        "entryId",
                    ),
                ),
            )

            PaperEditCommand.TYPE_MOVE_QUESTION -> {
                val entryId = objectValue.requiredId("entryId")
                    ?: return missingId(ordinal, "entryId")
                val offset = objectValue.integerValue("offset")
                    ?: return CommandParse.Bad(
                        "第 $ordinal 条编辑命令的 offset 必须是整数。",
                    )
                if (offset == 0) {
                    return CommandParse.Bad("第 $ordinal 条编辑命令的 offset 不能为 0。")
                }
                if (kotlin.math.abs(offset) > MAX_MOVE_OFFSET) {
                    return CommandParse.Bad(
                        "第 $ordinal 条编辑命令的调序偏移越界（最多 $MAX_MOVE_OFFSET）。",
                    )
                }
                CommandParse.Ok(PaperEditCommand.MoveQuestion(entryId, offset))
            }

            PaperEditCommand.TYPE_SET_QUESTION_TITLE -> {
                val entryId = objectValue.requiredId("entryId")
                    ?: return missingId(ordinal, "entryId")
                val title = objectValue.requiredText("title", MAX_TITLE_CHARS)
                    ?: return CommandParse.Bad(
                        "第 $ordinal 条编辑命令的 title 必须是非空、不超过 " +
                            "$MAX_TITLE_CHARS 字的字符串。",
                    )
                CommandParse.Ok(PaperEditCommand.SetQuestionTitle(entryId, title))
            }

            PaperEditCommand.TYPE_SET_BLOCK_TEXT -> {
                val entryId = objectValue.requiredId("entryId")
                    ?: return missingId(ordinal, "entryId")
                val blockId = objectValue.requiredId("blockId")
                    ?: return missingId(ordinal, "blockId")
                // 空文字在解析层放行（选择题题干允许为空），由 applier 按块类型决定是否拒绝。
                val text = objectValue.textValue("text", MAX_TEXT_CHARS)
                    ?: return CommandParse.Bad(
                        "第 $ordinal 条编辑命令的 text 必须是字符串（最多 $MAX_TEXT_CHARS 字）。",
                    )
                CommandParse.Ok(PaperEditCommand.SetBlockText(entryId, blockId, text))
            }

            PaperEditCommand.TYPE_SET_CHOICE_TEXT -> {
                val entryId = objectValue.requiredId("entryId")
                    ?: return missingId(ordinal, "entryId")
                val blockId = objectValue.requiredId("blockId")
                    ?: return missingId(ordinal, "blockId")
                val choiceId = objectValue.requiredId("choiceId")
                    ?: return missingId(ordinal, "choiceId")
                val text = objectValue.requiredText("text", MAX_TEXT_CHARS)
                    ?: return CommandParse.Bad(
                        "第 $ordinal 条编辑命令的 text 必须是非空、不超过 " +
                            "$MAX_TEXT_CHARS 字的字符串。",
                    )
                CommandParse.Ok(
                    PaperEditCommand.SetChoiceText(entryId, blockId, choiceId, text),
                )
            }

            PaperEditCommand.TYPE_SET_NOTE -> {
                val entryId = objectValue.requiredId("entryId")
                    ?: return missingId(ordinal, "entryId")
                // 空 note = 清空备注，属合法操作。
                val note = objectValue.textValue("note", MAX_NOTE_CHARS)
                    ?: return CommandParse.Bad(
                        "第 $ordinal 条编辑命令的 note 必须是字符串（最多 $MAX_NOTE_CHARS 字）。",
                    )
                CommandParse.Ok(PaperEditCommand.SetNote(entryId, note))
            }

            else -> CommandParse.Bad("第 $ordinal 条编辑命令的类型不在白名单里。")
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull

    private fun JsonObject.requiredId(key: String): String? =
        stringValue(key)?.takeIf { value ->
            value.isNotBlank() && value.length <= MAX_ID_CHARS
        }

    private fun JsonObject.requiredText(
        key: String,
        maxChars: Int,
    ): String? = stringValue(key)?.takeIf { value ->
        value.isNotBlank() && value.length <= maxChars
    }

    private fun JsonObject.textValue(key: String, maxChars: Int): String? =
        stringValue(key)?.takeIf { value -> value.length <= maxChars }

    private fun JsonObject.integerValue(key: String): Int? =
        (this[key] as? JsonPrimitive)
            ?.takeIf { primitive -> !primitive.isString }
            ?.intOrNull

    private fun missingId(ordinal: Int, key: String): CommandParse.Bad = CommandParse.Bad(
        "第 $ordinal 条编辑命令的 $key 必须是非空、不超过 $MAX_ID_CHARS 字的字符串。",
    )

    private fun keysHint(expected: Set<String>, actual: Set<String>): String {
        val missing = expected - actual
        val extra = actual - expected
        return buildString {
            if (missing.isNotEmpty()) {
                append("；缺少字段：")
                append(missing.sorted().joinToString("、"))
            }
            if (extra.isNotEmpty()) {
                append("；包含白名单外的字段：")
                append(extra.sorted().joinToString("、"))
            }
        }
    }

    private sealed interface CommandParse {
        data class Ok(val command: PaperEditCommand) : CommandParse
        data class Bad(val reason: String) : CommandParse
    }
}
