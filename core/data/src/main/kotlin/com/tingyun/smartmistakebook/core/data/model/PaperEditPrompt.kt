package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditCommand
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot

/**
 * 6B-3 自然语言改卷的**wire 提示词**（批次 40 / WS4 / 计划项 26）。
 *
 * 单一白名单来源：命令清单从 [PaperEditCommand.TYPES] 生成，初始化时逐项对拍；提示词里
 * 不可能出现解析器不认识的 type（反之亦然）。这是本文件存在的理由——`feature:tutor` 里已有
 * 一份 `TutorPaperEditPrompt`，两份提示词各自声明白名单必然会漂移（模型按 A 写、解析器按 B 拒）。
 * feature 是热区，本批不动它；重复源与"把 feature 端口改为转调这里"的欠账登记在
 * `docs/research/2026-11-02-model-task-layering.md` §10。
 *
 * 只描述**卷面副本**：题序、entryId、标题、文字块 id/类型/文字、选项 id/文字、已删来源题；
 * 不含图片 URI、资产 id、摘要或任何本地路径。输入里也没有这些字段，所以"模型引用图片"在
 * 结构上不可表达。
 */
internal object PaperEditPrompt {
    /** 与 [PaperEditInput.MAX_SNAPSHOT_CHARS] 同一预算：超出时裁掉卷面尾部。 */
    const val MAX_PROMPT_CHARS: Int = PaperEditInput.MAX_SNAPSHOT_CHARS

    private data class CommandDoc(
        val type: String,
        val template: String,
        val description: String,
    )

    /**
     * 七条命令的说明。顺序与 [PaperEditCommand.TYPES] 的声明顺序一致（linkedSetOf）。
     * 模板里的键集必须与 `PaperEditCommandParser` 的 `expectedKeys` 一致——多一个字段
     * 解析器整条拒绝，模板就是现场最直接的误导来源。
     */
    private val commandDocs: List<CommandDoc> = listOf(
        CommandDoc(
            PaperEditCommand.TYPE_REMOVE_QUESTION,
            """{"type":"remove_question","entryId":"..."}""",
            "删除一道题（至少留一道）",
        ),
        CommandDoc(
            PaperEditCommand.TYPE_RESTORE_QUESTION,
            """{"type":"restore_question","entryId":"..."}""",
            "把已删除的来源题加回（唯一的“增”）",
        ),
        CommandDoc(
            PaperEditCommand.TYPE_MOVE_QUESTION,
            """{"type":"move_question","entryId":"...","offset":-1}""",
            "调序（负=上移，正=下移，不能为 0）",
        ),
        CommandDoc(
            PaperEditCommand.TYPE_SET_QUESTION_TITLE,
            """{"type":"set_question_title","entryId":"...","title":"..."}""",
            "改卷面标题",
        ),
        CommandDoc(
            PaperEditCommand.TYPE_SET_BLOCK_TEXT,
            """{"type":"set_block_text","entryId":"...","blockId":"...","text":"..."}""",
            "改段落 / 小标题 / 公式 / 选择题题干",
        ),
        CommandDoc(
            PaperEditCommand.TYPE_SET_CHOICE_TEXT,
            """{"type":"set_choice_text","entryId":"...","blockId":"...","choiceId":"...","text":"..."}""",
            "改选项文字",
        ),
        CommandDoc(
            PaperEditCommand.TYPE_SET_NOTE,
            """{"type":"set_note","entryId":"...","note":"..."}""",
            "改卷面备注（空字符串=清空）",
        ),
    )

    init {
        // 漂移即启动失败：新增/删除命令必须同时更新提示词，不能靠"看起来还在"。
        check(commandDocs.map(CommandDoc::type).toSet() == PaperEditCommand.TYPES) {
            "Paper edit prompt command list drifted from PaperEditCommand.TYPES"
        }
    }

    fun build(input: PaperEditInput): String {
        val body = buildString {
            appendLine("你在修改一份练习卷的**副本**。只输出一个 JSON 对象：")
            appendLine("""{"commands":[ ... ]}""")
            appendLine()
            appendLine("允许的命令（只能从下面这些 type 里选，不能自造）：")
            commandDocs.forEach { doc ->
                // 没有备注区的版式不广告 set_note 模板（仍保留下面一句"不要输出"，收窄而不是留空）。
                if (doc.type == PaperEditCommand.TYPE_SET_NOTE && !input.includeNote) return@forEach
                appendLine("- ${doc.type}：${doc.template} ${doc.description}")
            }
            if (!input.includeNote) {
                appendLine("（当前版式不含备注区：不要输出 set_note。）")
            }
            appendLine()
            appendLine("硬规则：")
            appendLine("1. 只能使用下面卷面里出现过的 entryId / blockId / choiceId；不得编造。")
            appendLine("2. 不能新增卷面之外的题；restore_question 只能加回卷面已删除的来源题。")
            appendLine("3. 不能改图：不得输出图片地址、URI、base64、SVG、HTML、代码或 Markdown 代码块。")
            appendLine("4. 只改这份卷面副本，不能修改原题、原答案或错题本。")
            appendLine("5. 同一条命令只带上面列出的字段；多一个字段整条会被拒绝。")
            appendLine("6. 做不到或不需要改时输出 {\"commands\":[]}。")
            appendLine("7. 只输出 JSON，不要解释、不要前后缀、不要代码块。")
            appendLine("8. 只为学生要求改的内容输出命令；不要顺手改无关的题或文字。")
            appendLine()
            // 学生要求放在卷面前面：总长度截断只会裁掉卷面尾部，不会把"要改什么"裁没。
            appendLine("学生要求：")
            appendLine(input.instruction)
            appendLine()
            appendLine("卷面（按当前顺序）：")
            input.questions.forEachIndexed { index, question ->
                appendQuestion(index, question, includeNote = input.includeNote)
            }
            if (input.removedQuestions.isNotEmpty()) {
                appendLine()
                appendLine("已从卷面删除、可以用 restore_question 加回的来源题：")
                input.removedQuestions.forEach { removed ->
                    appendLine("  entryId=${removed.entryId} 标题=${removed.title}")
                }
            }
        }
        return body.take(MAX_PROMPT_CHARS)
    }

    private fun StringBuilder.appendQuestion(
        index: Int,
        question: PaperEditQuestionSnapshot,
        includeNote: Boolean,
    ) {
        appendLine("第 ${index + 1} 题 entryId=${question.entryId} 标题=${question.title}")
        question.blocks.forEach { block ->
            appendLine("  " + describeBlock(block, includeNote))
        }
        if (includeNote) {
            question.note?.let { note -> appendLine("  备注=$note") }
        }
    }

    private fun describeBlock(
        block: PaperEditBlockSnapshot,
        includeNote: Boolean,
    ): String = when (block.kind) {
        PaperEditBlockKind.FIGURE, PaperEditBlockKind.IMAGE ->
            "block id=${block.blockId} kind=${block.kind.wireName}（只读：不能改图或引用新图）"
        PaperEditBlockKind.NOTE ->
            if (includeNote) {
                "block id=${block.blockId} kind=note（只读：改备注请用 set_note）"
            } else {
                "block id=${block.blockId} kind=note（只读：当前版式不含备注区）"
            }
        PaperEditBlockKind.FORMULA ->
            "block id=${block.blockId} kind=formula latex=${block.text}"
        PaperEditBlockKind.CHOICE_GROUP -> buildString {
            append("block id=${block.blockId} kind=choice_group prompt=${block.text} choices=")
            append(
                block.choices.joinToString("；") { choice ->
                    "${choice.choiceId}=${choice.text}"
                },
            )
        }
        PaperEditBlockKind.PARAGRAPH, PaperEditBlockKind.SECTION_HEADING ->
            "block id=${block.blockId} kind=${block.kind.wireName} text=${block.text}"
    }
}
