package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout

/**
 * 卷面编辑（6B-2）：对**导出卷面模型**做增删题 / 调序 / 改文字。
 *
 * **边界（计划 §2 最后一条）**：这里只改渲染输入 `MistakePdfExportInput` 的块序列；错题条目、
 * 题面文档与 revision 一概不写。编辑后的卷面用 [MistakePaperDraft] 交给既有 JobRequest 管线：
 * PDF 沿用既有 `MistakePdfPagePlanner`（本批不修改），docx 沿用 6B-1 渲染器；编辑只改输入模型，
 * 不新造排版器。
 *
 * **题的单位**：一个 [MistakePdfPaperQuestion] 对应一次可导出判定产出的单题输入
 * （`MistakePdfEligibility` 的 `Eligible.input`）。卷面 = 这些单题输入的有序列表。这样
 * "删题 / 调序 / 加回"只动列表，不需要从扁平块序列里猜题边界（单题内部本来就可以有小标题）。
 *
 * **未编辑逐位不变**：`questions == sourceQuestions` 时 [toDraft] 直接返回
 * [buildBatchExportInput] 对来源输入算出的那份快照——与正常批量导出同一个函数、同一指纹。
 * 编辑后则重算 `inputSha256`（缓存键），但保留 `questionDocumentSha256`（来源题面文档指纹）
 * 与各题的 entryId，证明这只是一份卷面草稿，错题条目没有被改。
 *
 * **图不重画**：调序与纯排版编辑不触碰图；文字（含标题）改动后 [figureReviewRequired] 给出
 * "原图需复核"的标记，渲染仍复用原图（本批没有生成新图的能力，也不暗中调用）。
 */
class MistakePdfPaperEdit private constructor(
    val layout: MistakePdfLayout,
    private val sourceInputs: List<MistakePdfExportInput>,
    private val sourceQuestions: List<MistakePdfPaperQuestion>,
    val questions: List<MistakePdfPaperQuestion>,
    /** 选题时因"暂时不能导出"被略过的条数（如实展示，不静默吞掉）。 */
    val omittedCount: Int = 0,
) {
    init {
        require(questions.isNotEmpty()) { "A paper edit needs at least one question" }
        require(questions.map(MistakePdfPaperQuestion::entryId).distinct().size == questions.size) {
            "A paper edit must not repeat an entry"
        }
    }

    /** 与来源相比是否有任何结构/文字改动（改回原样会重新变回 false）。 */
    val isEdited: Boolean
        get() = questions != sourceQuestions

    /** 只剩一道题时不允许删（空卷面没有可渲染的输入）。 */
    val canRemoveQuestion: Boolean
        get() = questions.size > 1

    /**
     * 有必填文字被清空（标题/段落/小标题/选项/公式），此时不能确认导出。
     *
     * 编辑中的空文本允许存在（输入框可以边删边改）；[toDraft] 在确认口 fail-closed，
     * 界面据此禁用确认按钮并说明原因——比等到渲染时才发现空块更早、也更可行动。
     */
    val hasBlankText: Boolean
        get() = questions.any { question ->
            question.title.isBlank() || question.blocks.any { block ->
                when (block) {
                    is MistakePdfBlock.Paragraph -> block.text.isBlank()
                    is MistakePdfBlock.SectionHeading -> block.text.isBlank()
                    is MistakePdfBlock.Formula ->
                        block.latex.isBlank() && block.alternativeText.isBlank()
                    is MistakePdfBlock.ChoiceGroup ->
                        block.choices.any { choice -> choice.text.isBlank() }
                    is MistakePdfBlock.Figure,
                    is MistakePdfBlock.Image,
                    is MistakePdfBlock.Note,
                    -> false
                }
            }
        }

    /** 被删掉、仍可加回的题（来源顺序）。 */
    val removedQuestions: List<MistakePdfPaperQuestion>
        get() = sourceQuestions.filter { source ->
            questions.none { question -> question.entryId == source.entryId }
        }

    /**
     * 这道题的文字改过、但图仍是来源图：调用方应如实提示"原图需复核"。
     *
     * 比较标题与非图块：改标题也可能是改条件；调序不改变本题内容，因此不会触发。
     * 只改图块自己的标题/说明不算改动图义（图仍原样复用）。
     */
    fun figureReviewRequired(entryId: String): Boolean {
        val current = questions.firstOrNull { it.entryId == entryId } ?: return false
        if (current.blocks.none { it is MistakePdfBlock.Figure }) return false
        val source = sourceQuestions.firstOrNull { it.entryId == entryId } ?: return false
        if (current.title != source.title) return true
        return current.blocks.filterNot { it is MistakePdfBlock.Figure } !=
            source.blocks.filterNot { it is MistakePdfBlock.Figure }
    }

    /** 上移（-1）/下移（+1）；到边界或未知 id 时原样返回。 */
    fun withQuestionMoved(entryId: String, offset: Int): MistakePdfPaperEdit {
        val index = questions.indexOfFirst { it.entryId == entryId }
        if (index < 0 || offset == 0) return this
        val target = (index + offset).coerceIn(0, questions.lastIndex)
        if (target == index) return this
        val next = questions.toMutableList().apply { add(target, removeAt(index)) }
        return withQuestions(next)
    }

    /** 删题；只剩一道时原样返回（界面同时禁用按钮，这里是二道闸）。 */
    fun withQuestionRemoved(entryId: String): MistakePdfPaperEdit {
        if (!canRemoveQuestion) return this
        if (questions.none { it.entryId == entryId }) return this
        return withQuestions(questions.filterNot { it.entryId == entryId })
    }

    /**
     * 加回一道被删掉的来源题；已在卷面里或来源里没有时原样返回。
     *
     * 默认按**来源位置**插回（删掉再加回 = 原样撤销）；显式 [atIndex] 时按当前位置插入。
     */
    fun withQuestionRestored(
        entryId: String,
        atIndex: Int = Int.MAX_VALUE,
    ): MistakePdfPaperEdit {
        if (questions.any { it.entryId == entryId }) return this
        val sourceIndex = sourceQuestions.indexOfFirst { it.entryId == entryId }
        if (sourceIndex < 0) return this
        val target = if (atIndex == Int.MAX_VALUE) {
            sourceIndex.coerceIn(0, questions.size)
        } else {
            atIndex.coerceIn(0, questions.size)
        }
        val next = questions.toMutableList().apply {
            add(target, sourceQuestions[sourceIndex])
        }
        return withQuestions(next)
    }

    /**
     * 从外部加入一道题（下一批"从错题本再加一题"的接口；本批界面只用 [withQuestionRestored]）。
     *
     * 入参必须是已过 `MistakePdfEligibility` 的单题输入；这里只做卷面层的去重与插入。
     */
    fun withQuestionAdded(
        question: MistakePdfPaperQuestion,
        atIndex: Int = Int.MAX_VALUE,
    ): MistakePdfPaperEdit {
        if (questions.any { it.entryId == question.entryId }) return this
        val target = atIndex.coerceIn(0, questions.size)
        val next = questions.toMutableList().apply { add(target, question) }
        return withQuestions(next)
    }

    /** 改题目标题（卷面上的小标题；序号由装配时重排）。空标题允许在编辑中出现，确认时拒绝。 */
    fun withQuestionTitle(entryId: String, title: String): MistakePdfPaperEdit =
        withQuestion(entryId) { it.copy(title = title) }

    /**
     * 改一个文字块：段落 / 小标题 / 公式（LaTeX）/ 选择题题干。
     *
     * 未知 id、图形块、图片块原样返回（过期的界面行不能串改别的题）。空文字允许边删边改，
     * 由 [hasBlankText] / [toDraft] 在确认口挡住。
     */
    fun withBlockText(entryId: String, blockId: String, text: String): MistakePdfPaperEdit =
        withQuestion(entryId) { question ->
            val blocks = question.blocks.map { block ->
                when {
                    block.id != blockId -> block
                    block is MistakePdfBlock.Paragraph -> block.copy(text = text)
                    block is MistakePdfBlock.SectionHeading -> block.copy(text = text)
                    block is MistakePdfBlock.Formula -> block.copy(latex = text)
                    // 选择题题干允许为空（与准入口径一致：空题干不渲染，不算空块）。
                    block is MistakePdfBlock.ChoiceGroup -> block.copy(prompt = text)
                    else -> block
                }
            }
            question.copy(blocks = blocks)
        }

    /** 改一个选项的文字；空文字允许在编辑中出现，由 [hasBlankText] / [toDraft] 挡住。 */
    fun withChoiceText(
        entryId: String,
        blockId: String,
        choiceId: String,
        text: String,
    ): MistakePdfPaperEdit = withQuestion(entryId) { question ->
        val blocks = question.blocks.map { block ->
            if (block !is MistakePdfBlock.ChoiceGroup || block.id != blockId) {
                block
            } else {
                block.copy(
                    choices = block.choices.map { choice ->
                        if (choice.id == choiceId) choice.copy(text = text) else choice
                    },
                )
            }
        }
        question.copy(blocks = blocks)
    }

    /** 改卷面里的备注文字（只改草稿；绝不写回 `error_book_entry.user_note`）。 */
    fun withNote(entryId: String, note: String?): MistakePdfPaperEdit = withQuestion(entryId) {
        it.copy(note = note?.takeIf(String::isNotBlank))
    }

    /** 生成可入队的卷面草稿；未编辑时与正常批量导出逐位一致。 */
    fun toDraft(): MistakePaperDraft {
        require(!hasBlankText) { "A paper with blank required text cannot be exported" }
        val sourceInput = buildBatchExportInput(sourceInputs, layout)
        if (!isEdited) {
            return MistakePaperDraft(
                input = sourceInput,
                edited = false,
                questionCount = questions.size,
                sourceInputSha256 = sourceInput.inputSha256,
            )
        }
        val blocks = buildPaperBlocks(questions, layout)
        val fingerprint = paperContentFingerprint(
            sourceInputSha256 = sourceInput.inputSha256,
            layout = layout,
            questions = questions,
        )
        val editedInput = MistakePdfExportInput(
            errorBookEntryId = sourceInput.errorBookEntryId,
            problemId = sourceInput.problemId,
            problemRevisionId = fingerprint,
            revisionNumber = sourceInput.revisionNumber,
            title = sourceInput.title,
            subject = sourceInput.subject,
            // 题数会因删/加而变：卷面自己的副标题必须跟着当前题数，不能沿用来源快照的计数。
            subtitle = "按当前筛选整理 · 共 ${questions.size} 道",
            documentTitle = sourceInput.documentTitle,
            blocks = blocks,
            // 来源题面文档指纹保持不变：卷面编辑不产生新的题面 revision（5B 才做修订编辑）。
            questionDocumentSha256 = sourceInput.questionDocumentSha256,
            inputSha256 = fingerprint,
            maxPages = sourceInput.maxPages,
            maxRenderedLines = sourceInput.maxRenderedLines,
            maxPdfBytes = sourceInput.maxPdfBytes,
            cleanImageLocalUri = sourceInput.cleanImageLocalUri,
            // KD-55：卷面编辑只改块序列，题面图仍是来源记录那一份——完整性期望随图一起带过来，
            // 否则编辑后的 docx 会绕过渲染前复核。
            cleanImageSha256 = sourceInput.cleanImageSha256,
            cleanImageByteSize = sourceInput.cleanImageByteSize,
            layout = layout,
            userNote = sourceInput.userNote,
        )
        return MistakePaperDraft(
            input = editedInput,
            edited = true,
            questionCount = questions.size,
            sourceInputSha256 = sourceInput.inputSha256,
        )
    }

    private fun withQuestion(
        entryId: String,
        transform: (MistakePdfPaperQuestion) -> MistakePdfPaperQuestion,
    ): MistakePdfPaperEdit {
        val index = questions.indexOfFirst { it.entryId == entryId }
        if (index < 0) return this
        val next = questions.toMutableList().apply { set(index, transform(get(index))) }
        return withQuestions(next)
    }

    private fun withQuestions(next: List<MistakePdfPaperQuestion>): MistakePdfPaperEdit =
        MistakePdfPaperEdit(
            layout = layout,
            sourceInputs = sourceInputs,
            sourceQuestions = sourceQuestions,
            questions = next,
            omittedCount = omittedCount,
        )

    companion object {
        /**
         * 从已过判定的单题输入建卷面（顺序 = 输入顺序；重复 entryId 只保留第一份）。
         *
         * @throws IllegalArgumentException 输入为空、超上限或版式非法时 fail-closed。
         */
        fun fromInputs(
            inputs: List<MistakePdfExportInput>,
            layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
            omittedCount: Int = 0,
        ): MistakePdfPaperEdit {
            require(layout.validate().isEmpty()) { "Invalid paper layout: ${layout.validate()}" }
            val distinct = inputs.distinctBy(MistakePdfExportInput::errorBookEntryId)
            require(distinct.isNotEmpty()) { "A paper needs at least one eligible question" }
            require(distinct.all { input -> input.title.isNotBlank() }) {
                "Every paper question needs a title"
            }
            require(distinct.size <= MistakePdfBatchEligibility.MAX_QUESTIONS) {
                "A paper cannot hold more than ${MistakePdfBatchEligibility.MAX_QUESTIONS} questions"
            }
            require(omittedCount >= 0) { "Omitted count must not be negative" }
            val questions = distinct.map(::paperQuestionOf)
            return MistakePdfPaperEdit(
                layout = layout,
                sourceInputs = distinct,
                sourceQuestions = questions,
                questions = questions,
                omittedCount = omittedCount,
            )
        }

        /**
         * 选题后读取题面并建卷面：只读错题详情（纯本地），不派发任何模型任务。
         *
         * 不能导出的题被略过（计数进 [MistakePdfPaperEdit.omittedCount]）；一道都不剩时返回
         * null，由界面如实说明。上限按 [MistakePdfBatchEligibility.MAX_QUESTIONS] 截断。
         */
        suspend fun fromStates(
            entryIds: List<String>,
            layout: MistakePdfLayout,
            readByEntryId: suspend (String) -> MistakeDetailState,
        ): MistakePdfPaperEdit? {
            require(layout.validate().isEmpty()) { "Invalid paper layout: ${layout.validate()}" }
            val requested = entryIds
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            if (requested.isEmpty()) return null
            val ids = requested.take(MistakePdfBatchEligibility.MAX_QUESTIONS)
            val inputs = ids.mapNotNull { entryId ->
                (MistakePdfEligibility.check(readByEntryId(entryId), layout) as?
                    MistakePdfEligibilityResult.Eligible)?.input
            }
            if (inputs.isEmpty()) return null
            return fromInputs(
                inputs = inputs,
                layout = layout,
                // 超上限被截掉的也算"略过"，不静默消失。
                omittedCount = requested.size - inputs.size,
            )
        }
    }
}

/**
 * 卷面上的一道题（论文模型，不是错题条目的副本）。
 *
 * [entryId] 只是来源引用：编辑、渲染与导出都不写它所在的条目；[cleanImageLocalUri] 与
 * [note] 也只在卷面装配时被内联成块。
 */
data class MistakePdfPaperQuestion(
    val entryId: String,
    val title: String,
    val subtitle: String,
    val blocks: List<MistakePdfBlock>,
    val cleanImageLocalUri: String? = null,
    val note: String? = null,
    /** 干净题面图在资产记录里的完整性期望（KD-55），装配时随图内联进 Image 块。 */
    val cleanImageSha256: String? = null,
    val cleanImageByteSize: Long? = null,
) {
    init {
        require(entryId.isNotBlank()) { "A paper question needs an entry id" }
    }
}

/** 从一份已过判定的单题导出输入取卷面题（块列表按值复制，不共享可变状态）。 */
internal fun paperQuestionOf(input: MistakePdfExportInput): MistakePdfPaperQuestion =
    MistakePdfPaperQuestion(
        entryId = input.errorBookEntryId,
        title = input.title,
        subtitle = input.subtitle,
        blocks = input.blocks,
        cleanImageLocalUri = input.cleanImageLocalUri,
        note = input.userNote,
        cleanImageSha256 = input.cleanImageSha256,
        cleanImageByteSize = input.cleanImageByteSize,
    )

/**
 * 把卷面题序列装配成导出块序列。**批量导出与编辑草稿共用这一份**，两处不会排出不同的卷。
 *
 * 每题的编号与命名空间 id 由当前位置生成（调序后卷面序号随之更新）；图标题里的题号同理。
 */
internal fun buildPaperBlocks(
    questions: List<MistakePdfPaperQuestion>,
    layout: MistakePdfLayout,
): List<MistakePdfBlock> = questions.flatMapIndexed { index, question ->
    buildList {
        add(
            MistakePdfBlock.SectionHeading(
                id = "question-$index-heading",
                text = "${index + 1}. ${question.title}",
            ),
        )
        add(
            MistakePdfBlock.Paragraph(
                id = "question-$index-meta",
                text = question.subtitle,
            ),
        )
        question.blocks.forEach { block ->
            add(block.withNamespacedId("question-$index-${block.id}"))
        }
        question.cleanImageLocalUri?.let { uri ->
            add(
                MistakePdfBlock.Image(
                    id = "question-$index-image",
                    localUri = uri,
                    title = "第 ${index + 1} 题 · 干净题面",
                    // KD-55：把来源记录的 sha/长度带进批量块，渲染前用它复核文件字节。
                    expectedContentSha256 = question.cleanImageSha256,
                    expectedByteSize = question.cleanImageByteSize,
                ),
            )
        }
        if (layout.includeNote) {
            question.note?.let { note ->
                add(MistakePdfBlock.Note(id = "question-$index-note", text = note))
            }
        }
    }
}

/**
 * 编辑后卷面的内容指纹（进 `inputSha256`，也就是产物缓存键与记录里的输入身份）。
 *
 * 逐块哈希再用固定分隔符拼起来：文字里出现任何分隔符都不会造成两个不同卷面撞键。
 * 图 schema 用 `toString()` 的哈希参与——同一版本内确定性足够（缓存键不跨版本承诺）。
 */
internal fun paperContentFingerprint(
    sourceInputSha256: String,
    layout: MistakePdfLayout,
    questions: List<MistakePdfPaperQuestion>,
): String = exportFingerprint(
    "mistake-paper-edit-v1",
    sourceInputSha256,
    layout.canonicalForm(),
    questions.size.toString(),
    questions.joinToString(separator = "\n") { question ->
        listOf(
            exportFingerprint(
                "paper-question-v1",
                question.entryId,
                question.title,
                question.subtitle,
                question.cleanImageLocalUri.orEmpty(),
                question.cleanImageSha256.orEmpty(),
                question.cleanImageByteSize?.toString().orEmpty(),
                question.note.orEmpty(),
            ),
            question.blocks.joinToString(separator = ",") { block ->
                block.contentFingerprint()
            },
        ).joinToString(separator = "|")
    },
)

private fun MistakePdfBlock.contentFingerprint(): String = when (this) {
    is MistakePdfBlock.Paragraph -> exportFingerprint("paper-block-v1", "paragraph", id, text)
    is MistakePdfBlock.SectionHeading ->
        exportFingerprint("paper-block-v1", "heading", id, text)
    is MistakePdfBlock.Formula -> exportFingerprint(
        "paper-block-v1",
        "formula",
        id,
        latex,
        alternativeText,
        display.toString(),
    )
    is MistakePdfBlock.ChoiceGroup -> exportFingerprint(
        "paper-block-v1",
        "choice_group",
        id,
        prompt,
        enabled.toString(),
        *choices.flatMap { choice ->
            listOf(
                choice.id,
                choice.text,
                choice.selected.toString(),
                choice.enabled.toString(),
            )
        }.toTypedArray(),
    )
    is MistakePdfBlock.Figure -> exportFingerprint(
        "paper-block-v1",
        "figure",
        id,
        title.orEmpty(),
        alternativeText,
        schemaFingerprint(schema),
    )
    is MistakePdfBlock.Image -> exportFingerprint(
        "paper-block-v1",
        "image",
        id,
        localUri,
        title,
        expectedContentSha256.orEmpty(),
        expectedByteSize?.toString().orEmpty(),
    )
    is MistakePdfBlock.Note -> exportFingerprint("paper-block-v1", "note", id, text)
}

private fun schemaFingerprint(schema: FigureSchema): String = exportFingerprint(
    "paper-figure-schema-v1",
    schema::class.qualifiedName.orEmpty(),
    schema.toString(),
)
