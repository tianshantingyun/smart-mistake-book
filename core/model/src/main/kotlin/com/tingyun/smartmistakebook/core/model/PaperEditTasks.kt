package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 6B-3 自然语言改卷的**模型任务输入/输出**（批次 40 / WS4）。
 *
 * 为什么放在 `core:model`（WS4 裁定 23）：
 * - `ModelTaskInput` / `ModelTaskOutput` 是 `sealed interface`，子类型只能声明在同一模块；
 * - `core:model` 是叶子模块，只有 kotlinx-serialization，不含 Room/Android 依赖；
 * - `core:data -> core:model` 是单向依赖，wire 适配与提示词因此能读到这里的中立快照。
 *
 * 这里**不引用** `core:export` 的 `MistakePdfPaperEdit`：`core:model` 不能反向依赖，
 * `core:data` 也看不到 export。两边都能看见的只有 `feature:tutor` / `app`（热区），所以
 * `MistakePdfPaperEdit -> PaperEditInput` 的映射属于热区接缝，本批只出契约（见
 * `docs/research/2026-11-02-model-task-layering.md` §7 与 §10）。
 *
 * 消灭的具体失败：
 * - 把整份 `MistakePdfPaperEdit` 交给模型 → 会带出图片 URI / 资产摘要 / 本地路径；这里的
 *   快照只有题序、entryId、标题、文字块 id/类型/文字、选项 id/文字与已删来源题；
 * - 让模型自由改文档 → 输出不是命令而是文档；这里输出仍是**白名单命令 JSON**，由
 *   [PaperEditCommandParser] 做形状门，applier 只执行解析通过的命令。
 */
@Serializable
@SerialName("paper_edit_input")
data class PaperEditInput(
    /** 卷面（导出草稿）的身份；也是模型任务的 `subjectId`。 */
    val paperId: String,
    /** 宿主侧卷面版本，用于丢弃过期结果（CAS）；不参与模型可见文本。 */
    val revision: Long,
    /** 学生本轮的一句话要求（与输入框同一上限）。 */
    val instruction: String,
    val questions: List<PaperEditQuestionSnapshot>,
    /** 已从卷面删除、仍可用 `restore_question` 加回的来源题。 */
    val removedQuestions: List<PaperEditRemovedQuestion> = emptyList(),
    /** 当前版式是否含备注区；false 时提示词不广告 `set_note`，快照也不得带备注。 */
    val includeNote: Boolean = false,
) : ModelTaskInput {
    override val kind: ModelTaskKind
        get() = ModelTaskKind.PAPER_EDIT

    /**
     * 改卷不是会话内轮次，没有逐次披露清单的生产构造点；它走全局同意通道
     * （`isAgentConsentEligible = true`），与 TUTOR_PLAN/RESPOND 同一判据。
     */
    override val isAgentConsentEligible: Boolean
        get() = true

    /** 卷面快照是文本；本任务不携带任何图片字节。 */
    override val requestsImageBytes: Boolean
        get() = false

    override val subjectId: String
        get() = paperId

    init {
        paperId.requireSafeModelText("Paper edit id", MAX_ID_CHARS, allowLineBreaks = false)
        require(revision >= 0) { "Paper edit revision must not be negative" }
        instruction.requireSafeModelText(
            label = "Paper edit instruction",
            maxChars = MAX_INSTRUCTION_CHARS,
            allowLineBreaks = true,
        )
        require(questions.isNotEmpty()) { "A paper edit needs at least one question" }
        require(questions.map(PaperEditQuestionSnapshot::entryId).distinct().size == questions.size) {
            "Paper edit entry ids must be unique"
        }
        require(removedQuestions.map(PaperEditRemovedQuestion::entryId).distinct().size ==
            removedQuestions.size) {
            "Paper edit removed entry ids must be unique"
        }
        val presentEntryIds = questions.mapTo(hashSetOf(), PaperEditQuestionSnapshot::entryId)
        require(removedQuestions.none { it.entryId in presentEntryIds }) {
            "A removed paper question cannot still be on the paper"
        }
        require(includeNote || questions.all { it.note == null }) {
            "A paper without a note area cannot carry notes"
        }
        require(snapshotChars() <= MAX_SNAPSHOT_CHARS.toLong()) {
            "Paper edit snapshot exceeds $MAX_SNAPSHOT_CHARS characters"
        }
    }

    /** 用 Long 累加：列表长度本身不受单一字段预算约束，Int 累加在极端输入上可能溢出。 */
    private fun snapshotChars(): Long = questions.sumOf { question ->
        question.entryId.length.toLong() + question.title.length +
            (question.note?.length ?: 0) +
            question.blocks.sumOf { block ->
                block.blockId.length.toLong() + block.text.length +
                    block.choices.sumOf { choice ->
                        choice.choiceId.length.toLong() + choice.text.length
                    }
            }
    } + removedQuestions.sumOf { removed ->
        removed.entryId.length.toLong() + removed.title.length
    }

    companion object {
        /** 与 [PaperEditCommandParser.MAX_ID_CHARS] 同一口径（entryId / blockId / choiceId）。 */
        const val MAX_ID_CHARS: Int = PaperEditCommandParser.MAX_ID_CHARS
        const val MAX_TITLE_CHARS: Int = PaperEditCommandParser.MAX_TITLE_CHARS
        const val MAX_NOTE_CHARS: Int = PaperEditCommandParser.MAX_NOTE_CHARS
        const val MAX_TEXT_CHARS: Int = PaperEditCommandParser.MAX_TEXT_CHARS

        /**
         * 学生一句话要求的长度上限（与输入框同一上限）。
         *
         * 与 `feature:tutor` 的 `TutorPaperEditPrompt.MAX_INSTRUCTION_CHARS` 是同一个 2 000；
         * feature 是热区，本批不能改成引用这里，重复源已登记在 WS4 seam 文档，待热区释放后合并。
         */
        const val MAX_INSTRUCTION_CHARS: Int = 2_000

        /**
         * 快照可进入提示词的字符预算。
         *
         * 与 `feature:tutor` 的 `TutorPaperEditPrompt.MAX_PROMPT_CHARS` 同值：超过它时
         * 提示词尾部会被截断，模型可能看不到后面的题。请求构造期先拒，比在提示词里静默丢题更早、
         * 更可行动（学生能减少选题或拆成两次改卷）。
         */
        const val MAX_SNAPSHOT_CHARS: Int = 12_000
    }
}

/** 一块卷面文字块的中立快照；`kind` 决定模型能对它做什么（图/备注只读）。 */
@Serializable
data class PaperEditBlockSnapshot(
    val blockId: String,
    val kind: PaperEditBlockKind,
    /** 段落 / 小标题的文字，公式的 LaTeX，或选择题题干的 prompt。 */
    val text: String = "",
    /** 仅选择题组携带；其它块必须为空。 */
    val choices: List<PaperEditChoiceSnapshot> = emptyList(),
) {
    init {
        blockId.requireSafeModelText(
            "Paper edit block id",
            PaperEditInput.MAX_ID_CHARS,
            allowLineBreaks = false,
        )
        if (text.isNotEmpty()) {
            text.requireSafeModelText(
                label = "Paper edit block text",
                maxChars = PaperEditInput.MAX_TEXT_CHARS,
                allowLineBreaks = true,
            )
        }
        require(choices.map(PaperEditChoiceSnapshot::choiceId).distinct().size == choices.size) {
            "Paper edit choice ids must be unique within a block"
        }
        when (kind) {
            PaperEditBlockKind.FIGURE,
            PaperEditBlockKind.IMAGE,
            PaperEditBlockKind.NOTE,
            -> require(text.isEmpty() && choices.isEmpty()) {
                "A read-only paper block cannot carry editable text or choices"
            }
            PaperEditBlockKind.PARAGRAPH,
            PaperEditBlockKind.SECTION_HEADING,
            PaperEditBlockKind.FORMULA,
            -> require(choices.isEmpty()) {
                "Only a choice group may carry choices"
            }
            PaperEditBlockKind.CHOICE_GROUP -> Unit
        }
    }
}

/** 选择题组的一个选项。 */
@Serializable
data class PaperEditChoiceSnapshot(
    val choiceId: String,
    val text: String,
) {
    init {
        choiceId.requireSafeModelText(
            "Paper edit choice id",
            PaperEditInput.MAX_ID_CHARS,
            allowLineBreaks = false,
        )
        if (text.isNotEmpty()) {
            text.requireSafeModelText(
                label = "Paper edit choice text",
                maxChars = PaperEditInput.MAX_TEXT_CHARS,
                allowLineBreaks = true,
            )
        }
    }
}

/**
 * 卷面块类型。wire 名（`@SerialName`）与 [wireName] 是持久契约：它进请求 JSON、逻辑操作
 * 指纹与提示词；改名会让旧行/旧逻辑操作的哈希失配。
 */
@Serializable
enum class PaperEditBlockKind {
    @SerialName("paragraph")
    PARAGRAPH,

    @SerialName("section_heading")
    SECTION_HEADING,

    @SerialName("formula")
    FORMULA,

    @SerialName("choice_group")
    CHOICE_GROUP,

    @SerialName("figure")
    FIGURE,

    @SerialName("image")
    IMAGE,

    @SerialName("note")
    NOTE,
    ;

    /** 提示词与 wire 共用的稳定名字（与 `@SerialName` 逐字一致）。 */
    val wireName: String
        get() = when (this) {
            PARAGRAPH -> "paragraph"
            SECTION_HEADING -> "section_heading"
            FORMULA -> "formula"
            CHOICE_GROUP -> "choice_group"
            FIGURE -> "figure"
            IMAGE -> "image"
            NOTE -> "note"
        }

}

/** 卷面上的一道题（按当前顺序）。 */
@Serializable
data class PaperEditQuestionSnapshot(
    val entryId: String,
    val title: String,
    val blocks: List<PaperEditBlockSnapshot>,
    /** 卷面备注；null = 本题没有备注。仅版式含备注区时允许出现。 */
    val note: String? = null,
) {
    init {
        entryId.requireSafeModelText(
            "Paper edit entry id",
            PaperEditInput.MAX_ID_CHARS,
            allowLineBreaks = false,
        )
        title.requireSafeModelText(
            "Paper edit question title",
            PaperEditInput.MAX_TITLE_CHARS,
            allowLineBreaks = false,
        )
        require(blocks.map(PaperEditBlockSnapshot::blockId).distinct().size == blocks.size) {
            "Paper edit block ids must be unique within a question"
        }
        note?.let { value ->
            require(value.isNotBlank()) {
                "A paper question note must be null or non-blank"
            }
            value.requireSafeModelText(
                label = "Paper edit question note",
                maxChars = PaperEditInput.MAX_NOTE_CHARS,
                allowLineBreaks = true,
            )
        }
    }
}

/** 已从卷面删除、仍属于本次来源的题（`restore_question` 的候选）。 */
@Serializable
data class PaperEditRemovedQuestion(
    val entryId: String,
    val title: String,
) {
    init {
        entryId.requireSafeModelText(
            "Paper edit removed entry id",
            PaperEditInput.MAX_ID_CHARS,
            allowLineBreaks = false,
        )
        title.requireSafeModelText(
            "Paper edit removed title",
            PaperEditInput.MAX_TITLE_CHARS,
            allowLineBreaks = false,
        )
    }
}

/**
 * 模型输出：**已经过白名单形状门**的命令 JSON，而不是模型原文。
 *
 * 构造期就调用 [PaperEditCommandParser] 做形状校验：这样无论走网关解析、落库读回还是
 * 测试直构，"任务成功但 applier 解析失败"这条分叉都构造不出来。超出
 * [PaperEditCommandParser.MAX_PAYLOAD_CHARS] 抛 [ModelTextBudgetExceededException]，
 * 网关据此归到**可重试**的 `INCOMPLETE_OUTPUT`；形状错误抛 [IllegalArgumentException]，
 * 归**不可重试**的 `INVALID_RESPONSE`（与 KD-38 同一出口）。
 */
@Serializable
@SerialName("paper_edit_output")
data class PaperEditOutput(
    /** 经 [PaperEditCommandParser] 验证过的 `{"commands":[...]}` 原文（有界）。 */
    val commandsJson: String,
    val modelVersion: String,
) : ModelTaskOutput {
    init {
        if (commandsJson.length > PaperEditCommandParser.MAX_PAYLOAD_CHARS) {
            throw ModelTextBudgetExceededException(
                "Paper edit commands exceed the ${PaperEditCommandParser.MAX_PAYLOAD_CHARS}-character budget",
            )
        }
        when (val parsed = PaperEditCommandParser.parse(commandsJson)) {
            is PaperEditParseResult.Parsed -> Unit
            is PaperEditParseResult.Rejected ->
                throw IllegalArgumentException(parsed.reason)
        }
        modelVersion.requireSafeModelText(
            label = "Paper edit model version",
            maxChars = MAX_MODEL_VERSION_CHARS,
            allowLineBreaks = false,
        )
    }
}
