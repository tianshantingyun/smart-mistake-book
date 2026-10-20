package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit

/**
 * 6B-3 的模型端口：给定**当前卷面副本**和一句学生要求，返回模型的白名单命令 JSON。
 *
 * 它是一个显式端口而不是在 ViewModel 里直接发模型任务，理由有三条：
 * 1. **只有一份文档事实**：传给模型的是 `MistakePdfPaperEdit`（本地选题/排版后的副本），
 *    不是错题条目的可写句柄；模型没有写回 `error_book_entry` 的通道；
 * 2. **白名单是解析器的职责**：端口返回**原始字符串**，[TutorPaperEditApplier] 用
 *    `PaperEditCommandParser` 严格解析；端口实现不得自己"理解"或执行命令；
 * 3. **无真实 Provider 也能验收**：装配处可以注入 fake（测试）或 null（本入口不接模型）；
 *    为 null 时界面不显示"让 AI 改卷面"，不广告没接上的能力（与 PRD-06 同一纪律）。
 *
 * 生产适配器的提示词必须用 [TutorPaperEditPrompt.build]：那里是白名单、禁代码、禁图片地址
 * 的唯一声明点。模型返回代码块、解释文字或截图 URL 都会在解析层整条拒绝，草稿保持原样。
 *
 * **本批边界**：T5 不拥有新的 `ModelTaskKind` 与协议适配文件，因此这里只落端口与提示词；
 * 未注入端口时编辑态不显示「让 AI 改卷面」（不广告未接线能力）。真实 Provider 的调用链
 * 与质量属于装配/后续批次，本批用 fake 覆盖验收。
 */
internal fun interface TutorPaperEditAssistant {
    suspend fun commandsFor(paper: MistakePdfPaperEdit, instruction: String): String
}

/**
 * 自然语言改卷的提示词（6B-3）。
 *
 * 它只描述**卷面副本**：题序、`entryId`、文字块 id/类型/文字、选择题选项 id/文字；不包含
 * 题面图片 URI、图片摘要、资产 id、错题条目备注以外的任何本地路径。模型因此没有可引用的
 * 图片地址——"图引用仅限既有/角色安全"不是靠模型自觉，而是提示词里根本没有这个字段。
 */
object TutorPaperEditPrompt {
    /** 学生一句话要求的长度上限（与输入框同一上限）。 */
    const val MAX_INSTRUCTION_CHARS = 2_000

    /** 组装后提示词的总长度上限；超出时按文本安全截断。 */
    const val MAX_PROMPT_CHARS = 12_000

    private const val MAX_FIELD_CHARS = 400

    fun build(paper: MistakePdfPaperEdit, instruction: String): String {
        val body = buildString {
            appendLine("你在修改一份练习卷的**副本**。只输出一个 JSON 对象：")
            appendLine("""{"commands":[ ... ]}""")
            appendLine()
            appendLine("允许的命令（只能从下面这些 type 里选，不能自造）：")
            appendLine("""- remove_question：{"type":"remove_question","entryId":"..."} 删除一道题（至少留一道）""")
            appendLine("""- restore_question：{"type":"restore_question","entryId":"..."} 把已删除的来源题加回（唯一的"增"）""")
            appendLine("""- move_question：{"type":"move_question","entryId":"...","offset":-1} 调序（负=上移，正=下移，不能为 0）""")
            appendLine("""- set_question_title：{"type":"set_question_title","entryId":"...","title":"..."} 改卷面标题""")
            appendLine("""- set_block_text：{"type":"set_block_text","entryId":"...","blockId":"...","text":"..."} 改段落/小标题/公式/选择题题干""")
            appendLine("""- set_choice_text：{"type":"set_choice_text","entryId":"...","blockId":"...","choiceId":"...","text":"..."} 改选项文字""")
            appendLine("""- set_note：{"type":"set_note","entryId":"...","note":"..."} 改卷面备注（空字符串=清空）""")
            if (!paper.layout.includeNote) {
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
            // 先放学生要求：总长度截断只会裁掉卷面尾部，不会把"要改什么"裁没。
            appendLine("学生要求：")
            appendLine(clip(instruction, MAX_INSTRUCTION_CHARS))
            appendLine()
            appendLine("卷面（按当前顺序）：")
            paper.questions.forEachIndexed { index, question ->
                appendLine("第 ${index + 1} 题 entryId=${question.entryId} 标题=${clip(question.title)}")
                question.blocks.forEach { block ->
                    appendLine("  " + describeBlock(block))
                }
                if (paper.layout.includeNote) {
                    question.note?.let { note -> appendLine("  备注=${clip(note)}") }
                }
                if (question.cleanImageLocalUri != null) {
                    // 只说明"有题面图且只读"，绝不把 URI/摘要交给模型。
                    appendLine("  题面图：已内联在卷面上（只读，不能替换或引用）")
                }
            }
            if (paper.removedQuestions.isNotEmpty()) {
                appendLine()
                appendLine("已从卷面删除、可以用 restore_question 加回的来源题：")
                paper.removedQuestions.forEach { removed ->
                    appendLine("  entryId=${removed.entryId} 标题=${clip(removed.title)}")
                }
            }
        }
        return body.take(MAX_PROMPT_CHARS)
    }

    private fun describeBlock(block: MistakePdfBlock): String = when (block) {
        is MistakePdfBlock.Paragraph ->
            "block id=${block.id} kind=paragraph text=${clip(block.text)}"
        is MistakePdfBlock.SectionHeading ->
            "block id=${block.id} kind=section_heading text=${clip(block.text)}"
        is MistakePdfBlock.Formula ->
            "block id=${block.id} kind=formula latex=${clip(block.latex)} " +
                "alternativeText=${clip(block.alternativeText)}"
        is MistakePdfBlock.ChoiceGroup -> buildString {
            append("block id=${block.id} kind=choice_group prompt=${clip(block.prompt)} choices=")
            append(
                block.choices.joinToString("；") { choice ->
                    "${choice.id}=${clip(choice.text)}"
                },
            )
        }
        is MistakePdfBlock.Figure ->
            "block id=${block.id} kind=figure（只读：不能改图或引用新图）"
        is MistakePdfBlock.Image ->
            "block id=${block.id} kind=image（只读：不能替换或引用新图）"
        is MistakePdfBlock.Note ->
            "block id=${block.id} kind=note（只读：改备注请用 set_note）"
    }

    private fun clip(text: String, maxChars: Int = MAX_FIELD_CHARS): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "…"
}
