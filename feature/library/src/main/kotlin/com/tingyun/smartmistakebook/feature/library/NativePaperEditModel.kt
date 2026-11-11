package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperQuestion
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditChoiceSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditCommand
import com.tingyun.smartmistakebook.core.model.PaperEditCommandParser
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditModelException
import com.tingyun.smartmistakebook.core.model.PaperEditModelPort
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import com.tingyun.smartmistakebook.core.model.PaperEditParseResult
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditRemovedQuestion
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/**
 * 一次自然语言改卷的**预览**结果（6B-3 / 批次 50）。
 *
 * 预览是"模型命令在副本上真实应用后"的产物，而不是模型原文：
 * - [Ready.edit] 是应用后的卷面副本（来源条目、题面指纹、图都未被写）；
 * - [Ready.diffs] 是逐条命令的实际变化（给学生核对，不把模型解释文字当结果）；
 * - 任一命令失败、命令为空、命令没有产生实际变化、改完不能导出、端口失败/取消，
 *   一律 [Rejected]，卷面保持原样，不落半批修改。
 */
internal sealed interface NativePaperEditPreview {
    data class Ready(
        /** 生成预览时的那份卷面；采用时必须仍是同一实例（CAS）。 */
        val baseline: MistakePdfPaperEdit,
        val edit: MistakePdfPaperEdit,
        val commandCount: Int,
        val diffs: List<NativePaperEditDiff>,
        /** 文字改动导致"原图需复核"的题（仍保留原图，不自动重画）。 */
        val figureReviewEntryIds: Set<String>,
    ) : NativePaperEditPreview

    data class Rejected(val reason: String) : NativePaperEditPreview
}

/** 一条命令在卷面上产生的可见变化；`before`/`after` 为 null 表示新增/删除。 */
internal data class NativePaperEditDiff(
    val questionLabel: String,
    val change: String,
    val before: String?,
    val after: String?,
)

/** 采用预览的结果：只有生成预览的同一份卷面才能采用（等待/预览期间手动改过就作废）。 */
internal sealed interface NativePaperEditAdoption {
    data class Adopted(val edit: MistakePdfPaperEdit) : NativePaperEditAdoption

    data class Stale(val reason: String) : NativePaperEditAdoption
}

/**
 * 6B-3 自然语言改卷的**宿主侧接缝**：卷面副本 → 有界 [PaperEditInput] → [PaperEditModelPort]
 * → 白名单命令 → 在副本上应用并产出差异清单。
 *
 * 它消灭的具体失败（为什么不是把 tutor 的 applier 直接搬过来）：
 * - 卷面页（详情页经「编辑卷面」进入的同一页）此前没有自然语言入口，学生只能逐块手改；
 * - 直接把模型输出当文档：模型若能自由改文档，图片地址、原题、原答案都会出现在模型可见面；
 *   这里只把有界文本快照交出去，回来仍是七条白名单命令；
 * - 失败当成"没有修改"：端口失败/取消/没落终态会被 `RepositoryPaperEditModelPort` 抛成
 *   [PaperEditModelException]；这里如实拒绝并保留卷面，不返回空命令冒充成功；
 * - 预览与采用之间卷面被手动改动：预览绑定生成时的实例，采用时做同一性校验（CAS）。
 *
 * 与 `feature:tutor` 的 `TutorPaperEditApplier` 是**同一套白名单 + 同一组卷面编辑方法**的两份
 * 实现：tutor 那份是模块内 `internal`，且不产出差异清单；本模块需要"给学生核对的变化"，
 * 不能为了复用把 tutor 的内部类型暴露成跨模块 API。两处都调用
 * `PaperEditCommandParser`，命令集由 `core:model` 单一持有；后续若要合并，应先让 applier
 * 变成 `core:export` 的公共 API，而不是在这里另设第二套白名单。
 */
internal object NativePaperEditModel {
    /** 与输入框同一上限（`core:model` 的单一源）。 */
    const val MAX_INSTRUCTION_CHARS: Int = PaperEditInput.MAX_INSTRUCTION_CHARS

    /**
     * 宿主侧卷面身份：由来源题 id 的有序集合派生，**不拿 entryId 冒充纸面 id**
     * （`docs/research/2026-11-02-model-task-layering.md` §10.6）。同一组题在同一顺序下稳定，
     * 因此重试同一逻辑操作不会因为随机 id 获得新预算；长度受 [PaperEditInput.MAX_ID_CHARS] 约束。
     */
    fun paperIdFor(entryIds: List<String>): String {
        val canonical = entryIds.map(String::trim).filter(String::isNotEmpty)
            .joinToString(separator = "\u001F")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "native-paper-edit:" + digest.take(32)
    }

    /**
     * 宿主侧卷面版本（CAS + 逻辑操作指纹的一部分）：用卷面草稿的 `inputSha256` 派生，
     * 同一内容稳定、任一字/结构/版式变化都会换版本。
     *
     * @throws IllegalArgumentException 卷面有必填文字被清空（`toDraft()` fail-closed）。
     */
    fun revisionFor(paper: MistakePdfPaperEdit): Long {
        require(!paper.hasBlankText) { "A paper with blank required text has no revision" }
        val sha = paper.toDraft().input.inputSha256
        return sha.take(15).toLong(16)
    }

    /**
     * 把卷面副本映射成模型可见的有界快照（表见
     * `docs/research/2026-11-02-model-task-layering.md` §10.6）：
     * 图/URI/摘要/本地路径结构上不可表达；备注只在含备注版式出现；公式只带 LaTeX。
     */
    fun buildInput(
        paper: MistakePdfPaperEdit,
        instruction: String,
        paperId: String,
        revision: Long,
    ): PaperEditInput {
        val trimmed = instruction.trim()
        require(trimmed.isNotEmpty()) { "请先写一句修改要求。" }
        require(trimmed.length <= MAX_INSTRUCTION_CHARS) {
            "修改要求最多 $MAX_INSTRUCTION_CHARS 字。"
        }
        val includeNote = paper.layout.includeNote
        return PaperEditInput(
            paperId = paperId,
            revision = revision,
            instruction = trimmed,
            questions = paper.questions.map { question ->
                PaperEditQuestionSnapshot(
                    entryId = question.entryId,
                    title = question.title,
                    blocks = question.blocks.map(::blockSnapshot),
                    // 不含备注区时结构上不放备注：提示词也不会广告 set_note。
                    note = if (includeNote) question.note else null,
                )
            },
            removedQuestions = paper.removedQuestions.map { removed ->
                PaperEditRemovedQuestion(entryId = removed.entryId, title = removed.title)
            },
            includeNote = includeNote,
        )
    }

    /**
     * 提交一次改卷并返回预览。端口为 null、输入不可表达、任务失败/取消/未落终态、命令非法、
     * 命令不产生实际变化、改完不可导出——全部 [NativePaperEditPreview.Rejected]，**卷面不动**。
     *
     * 这里不复制、也不回显模型/供应商的错误正文：学生看到的是本地可判定的事实
     * （失败 / 取消 / 没有给出可执行修改），不是伪造的"模型回执"。
     */
    suspend fun request(
        port: PaperEditModelPort?,
        paper: MistakePdfPaperEdit,
        instruction: String,
        paperId: String,
        revision: Long,
        agentConsentGranted: Boolean,
    ): NativePaperEditPreview {
        if (port == null) {
            return NativePaperEditPreview.Rejected(
                "这个入口暂时不能交给模型改卷面，卷面保持原样。",
            )
        }
        if (paper.hasBlankText) {
            return NativePaperEditPreview.Rejected(
                "卷面里有必填文字被清空，填上后才能让 AI 改卷面；卷面保持原样。",
            )
        }
        val trimmed = instruction.trim()
        if (trimmed.isEmpty()) {
            return NativePaperEditPreview.Rejected("先写一句修改要求。")
        }
        if (trimmed.length > MAX_INSTRUCTION_CHARS) {
            return NativePaperEditPreview.Rejected(
                "修改要求最多 $MAX_INSTRUCTION_CHARS 字，请缩短后再试；卷面保持原样。",
            )
        }
        val input = try {
            buildInput(paper, trimmed, paperId, revision)
        } catch (_: IllegalArgumentException) {
            return NativePaperEditPreview.Rejected(
                "这份卷面暂时不能交给模型（可能太长或含无法发送的字符）；卷面保持原样。",
            )
        }
        val output = try {
            port.commandsFor(input, agentConsentGranted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PaperEditModelException) {
            return NativePaperEditPreview.Rejected(modelFailureMessage(failure))
        } catch (_: Exception) {
            return NativePaperEditPreview.Rejected(
                "这次没有改成：提交改卷任务失败，卷面保持原样，可以再试一次。",
            )
        }
        return previewFor(paper, output)
    }

    /** 白名单命令 → 副本上的预览；整批成功才返回 [NativePaperEditPreview.Ready]。 */
    fun previewFor(
        paper: MistakePdfPaperEdit,
        output: PaperEditOutput,
    ): NativePaperEditPreview {
        val commands = when (val parsed = PaperEditCommandParser.parse(output.commandsJson)) {
            is PaperEditParseResult.Rejected -> return NativePaperEditPreview.Rejected(
                parsed.reason + " 卷面保持原样。",
            )
            is PaperEditParseResult.Parsed -> parsed.commands
        }
        if (commands.isEmpty()) {
            // 空命令是合法的"没有可执行修改"，但不是"已改好"；如实拒绝，不摆成功。
            return NativePaperEditPreview.Rejected(
                "模型没有给出可执行的修改，卷面保持原样。",
            )
        }
        var current = paper
        val diffs = ArrayList<NativePaperEditDiff>(commands.size)
        commands.forEachIndexed { index, command ->
            val before = current
            when (val step = applyOne(before, command)) {
                is Step.Bad -> return NativePaperEditPreview.Rejected(
                    "第 ${index + 1} 条命令：${step.reason} 卷面保持原样。",
                )
                is Step.Ok -> {
                    current = step.edit
                    diffOf(command, before, current)?.let(diffs::add)
                }
            }
        }
        if (diffs.isEmpty()) {
            return NativePaperEditPreview.Rejected(
                "模型返回的命令没有产生任何实际修改，卷面保持原样。",
            )
        }
        try {
            current.toDraft()
        } catch (_: IllegalArgumentException) {
            return NativePaperEditPreview.Rejected(
                "改完的卷面还不能导出（有必填文字为空），这次修改没有应用；卷面保持原样。",
            )
        }
        val figureReviewEntryIds = current.questions
            .map(MistakePdfPaperQuestion::entryId)
            .filterTo(linkedSetOf()) { entryId -> current.figureReviewRequired(entryId) }
        return NativePaperEditPreview.Ready(
            baseline = paper,
            edit = current,
            commandCount = commands.size,
            diffs = diffs,
            figureReviewEntryIds = figureReviewEntryIds,
        )
    }

    /** 采用预览：只接受生成预览的同一份卷面（引用同一性 = 无手动改动）。 */
    fun adopt(
        current: MistakePdfPaperEdit,
        preview: NativePaperEditPreview.Ready,
    ): NativePaperEditAdoption =
        if (current !== preview.baseline) {
            NativePaperEditAdoption.Stale(
                "卷面在生成预览后又被改过，这次预览不能采用；请重新生成。",
            )
        } else {
            NativePaperEditAdoption.Adopted(preview.edit)
        }

    private fun modelFailureMessage(failure: PaperEditModelException): String =
        when (failure.status) {
            ModelTaskStatus.CANCELLED -> "改卷已取消，卷面保持原样。"
            null -> "改卷任务没有产生任何结果，卷面保持原样，可以再试一次。"
            else -> if (failure.retryable) {
                "模型这次没有给出可用的修改（可以重试），卷面保持原样。"
            } else {
                "模型这次没有给出可用的修改，卷面保持原样。"
            }
        }

    private fun blockSnapshot(block: MistakePdfBlock): PaperEditBlockSnapshot = when (block) {
        is MistakePdfBlock.Paragraph ->
            PaperEditBlockSnapshot(block.id, PaperEditBlockKind.PARAGRAPH, text = block.text)
        is MistakePdfBlock.SectionHeading ->
            PaperEditBlockSnapshot(block.id, PaperEditBlockKind.SECTION_HEADING, text = block.text)
        is MistakePdfBlock.Formula ->
            PaperEditBlockSnapshot(block.id, PaperEditBlockKind.FORMULA, text = block.latex)
        is MistakePdfBlock.ChoiceGroup -> PaperEditBlockSnapshot(
            blockId = block.id,
            kind = PaperEditBlockKind.CHOICE_GROUP,
            text = block.prompt,
            choices = block.choices.map { choice ->
                PaperEditChoiceSnapshot(choiceId = choice.id, text = choice.text)
            },
        )
        // 图/备注只读：快照不带标题、URI、摘要、schema 或备注文字。
        is MistakePdfBlock.Figure -> PaperEditBlockSnapshot(block.id, PaperEditBlockKind.FIGURE)
        is MistakePdfBlock.Image -> PaperEditBlockSnapshot(block.id, PaperEditBlockKind.IMAGE)
        is MistakePdfBlock.Note -> PaperEditBlockSnapshot(block.id, PaperEditBlockKind.NOTE)
    }

    private fun applyOne(paper: MistakePdfPaperEdit, command: PaperEditCommand): Step =
        when (command) {
            is PaperEditCommand.RemoveQuestion -> requireQuestion(paper, command.entryId) {
                if (!paper.canRemoveQuestion) {
                    Step.Bad("卷面至少保留一道题，不能删到空。")
                } else {
                    Step.Ok(paper.withQuestionRemoved(command.entryId))
                }
            }

            is PaperEditCommand.RestoreQuestion -> {
                if (paper.questions.any { it.entryId == command.entryId }) {
                    Step.Bad("这道题已经在卷面里，不能重复加回。")
                } else if (paper.removedQuestions.none { it.entryId == command.entryId }) {
                    Step.Bad("这道题不在本次卷面的来源里，不能凭空加入。")
                } else {
                    Step.Ok(paper.withQuestionRestored(command.entryId))
                }
            }

            is PaperEditCommand.MoveQuestion -> requireQuestion(paper, command.entryId) {
                val index = paper.questions.indexOfFirst { it.entryId == command.entryId }
                val target = index + command.offset
                when {
                    command.offset == 0 -> Step.Bad("调序偏移不能为 0。")
                    target !in paper.questions.indices ->
                        Step.Bad("调序越界：这道题不能移出当前卷面。")
                    else -> Step.Ok(paper.withQuestionMoved(command.entryId, command.offset))
                }
            }

            is PaperEditCommand.SetQuestionTitle -> requireQuestion(paper, command.entryId) {
                if (command.title.isBlank()) {
                    Step.Bad("标题不能改成空。")
                } else {
                    Step.Ok(paper.withQuestionTitle(command.entryId, command.title))
                }
            }

            is PaperEditCommand.SetBlockText -> requireQuestion(paper, command.entryId) { question ->
                val block = question.blocks.firstOrNull { it.id == command.blockId }
                    ?: return@requireQuestion Step.Bad(
                        "这道题里没有 blockId=${command.blockId} 的文字块。",
                    )
                when (block) {
                    is MistakePdfBlock.Paragraph -> if (command.text.isBlank()) {
                        Step.Bad("段落不能改成空文字。")
                    } else {
                        Step.Ok(paper.withBlockText(command.entryId, block.id, command.text))
                    }

                    is MistakePdfBlock.SectionHeading -> if (command.text.isBlank()) {
                        Step.Bad("小标题不能改成空文字。")
                    } else {
                        Step.Ok(paper.withBlockText(command.entryId, block.id, command.text))
                    }

                    is MistakePdfBlock.Formula -> if (
                        command.text.isBlank() && block.alternativeText.isBlank()
                    ) {
                        Step.Bad("公式和替代文字不能同时为空。")
                    } else {
                        Step.Ok(paper.withBlockText(command.entryId, block.id, command.text))
                    }

                    // 选择题题干允许为空（与导出准入口径一致）；空题干不渲染，不是空块。
                    is MistakePdfBlock.ChoiceGroup ->
                        Step.Ok(paper.withBlockText(command.entryId, block.id, command.text))

                    is MistakePdfBlock.Figure,
                    is MistakePdfBlock.Image,
                    -> Step.Bad("图块不能在改卷里修改或替换，只能保留原图。")

                    is MistakePdfBlock.Note ->
                        Step.Bad("备注请用 set_note 命令改，不要当普通文字块改。")
                }
            }

            is PaperEditCommand.SetChoiceText -> requireQuestion(paper, command.entryId) { question ->
                val block = question.blocks.firstOrNull { it.id == command.blockId }
                when {
                    block !is MistakePdfBlock.ChoiceGroup ->
                        Step.Bad("blockId=${command.blockId} 不是选择题块。")
                    block.choices.none { choice -> choice.id == command.choiceId } ->
                        Step.Bad("这个选择题里没有 choiceId=${command.choiceId} 的选项。")
                    command.text.isBlank() -> Step.Bad("选项文字不能改成空。")
                    else -> Step.Ok(
                        paper.withChoiceText(
                            command.entryId,
                            command.blockId,
                            command.choiceId,
                            command.text,
                        ),
                    )
                }
            }

            is PaperEditCommand.SetNote -> requireQuestion(paper, command.entryId) {
                if (!paper.layout.includeNote) {
                    Step.Bad(
                        "当前版式不含备注区；先打开「含备注」再改，否则改了也不会出现在卷面上。",
                    )
                } else {
                    Step.Ok(
                        paper.withNote(
                            command.entryId,
                            command.note.takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }

    private inline fun requireQuestion(
        paper: MistakePdfPaperEdit,
        entryId: String,
        block: (MistakePdfPaperQuestion) -> Step,
    ): Step {
        val question = paper.questions.firstOrNull { it.entryId == entryId }
            ?: return Step.Bad("卷面上没有 entryId=$entryId 的题。")
        return block(question)
    }

    private fun diffOf(
        command: PaperEditCommand,
        before: MistakePdfPaperEdit,
        after: MistakePdfPaperEdit,
    ): NativePaperEditDiff? = when (command) {
        is PaperEditCommand.RemoveQuestion -> {
            val index = before.questions.indexOfFirst { it.entryId == command.entryId }
            if (index < 0 || after.questions.any { it.entryId == command.entryId }) {
                null
            } else {
                val title = before.questions[index].title
                NativePaperEditDiff("第 ${index + 1} 题「$title」", "移出题目", title, null)
            }
        }

        is PaperEditCommand.RestoreQuestion -> {
            val index = after.questions.indexOfFirst { it.entryId == command.entryId }
            if (index < 0 || before.questions.any { it.entryId == command.entryId }) {
                null
            } else {
                val title = after.questions[index].title
                NativePaperEditDiff("第 ${index + 1} 题「$title」", "加回题目", null, title)
            }
        }

        is PaperEditCommand.MoveQuestion -> {
            val from = before.questions.indexOfFirst { it.entryId == command.entryId }
            val to = after.questions.indexOfFirst { it.entryId == command.entryId }
            if (from < 0 || to < 0 || from == to) {
                null
            } else {
                NativePaperEditDiff(
                    questionLabel = "「${after.questions[to].title}」",
                    change = "调整顺序",
                    before = "第 ${from + 1} 题",
                    after = "第 ${to + 1} 题",
                )
            }
        }

        is PaperEditCommand.SetQuestionTitle -> {
            val old = before.questionOrNull(command.entryId)?.title
            val new = after.questionOrNull(command.entryId)?.title
            if (old == null || new == null || old == new) {
                null
            } else {
                NativePaperEditDiff("「$new」", "改标题", old, new)
            }
        }

        is PaperEditCommand.SetBlockText -> {
            val oldBlock = before.questionOrNull(command.entryId)
                ?.blocks?.firstOrNull { it.id == command.blockId }
            val newBlock = after.questionOrNull(command.entryId)
                ?.blocks?.firstOrNull { it.id == command.blockId }
            val old = editableText(oldBlock)
            val new = editableText(newBlock)
            if (old == null || new == null || old == new) {
                null
            } else {
                NativePaperEditDiff(
                    questionLabel = "「${after.questionOrNull(command.entryId)?.title.orEmpty()}」",
                    change = "改${blockLabel(newBlock)}",
                    before = old,
                    after = new,
                )
            }
        }

        is PaperEditCommand.SetChoiceText -> {
            val oldBlock = before.questionOrNull(command.entryId)
                ?.blocks?.firstOrNull { it.id == command.blockId } as? MistakePdfBlock.ChoiceGroup
            val newBlock = after.questionOrNull(command.entryId)
                ?.blocks?.firstOrNull { it.id == command.blockId } as? MistakePdfBlock.ChoiceGroup
            val oldIndex = oldBlock?.choices?.indexOfFirst { it.id == command.choiceId } ?: -1
            val newIndex = newBlock?.choices?.indexOfFirst { it.id == command.choiceId } ?: -1
            val old = if (oldIndex >= 0) oldBlock?.choices?.get(oldIndex)?.text else null
            val new = if (newIndex >= 0) newBlock?.choices?.get(newIndex)?.text else null
            if (old == null || new == null || old == new) {
                null
            } else {
                NativePaperEditDiff(
                    questionLabel = "「${after.questionOrNull(command.entryId)?.title.orEmpty()}」",
                    change = "改选项 ${choiceLabel(newIndex)}",
                    before = old,
                    after = new,
                )
            }
        }

        is PaperEditCommand.SetNote -> {
            val old = before.questionOrNull(command.entryId)?.note.orEmpty()
            val new = after.questionOrNull(command.entryId)?.note.orEmpty()
            if (old == new) {
                null
            } else {
                NativePaperEditDiff(
                    questionLabel = "「${after.questionOrNull(command.entryId)?.title.orEmpty()}」",
                    change = "改卷面备注",
                    before = old.ifEmpty { "（空）" },
                    after = new.ifEmpty { "（空）" },
                )
            }
        }
    }

    private fun MistakePdfPaperEdit.questionOrNull(entryId: String): MistakePdfPaperQuestion? =
        questions.firstOrNull { it.entryId == entryId }

    private fun editableText(block: MistakePdfBlock?): String? = when (block) {
        is MistakePdfBlock.Paragraph -> block.text
        is MistakePdfBlock.SectionHeading -> block.text
        is MistakePdfBlock.Formula -> block.latex
        is MistakePdfBlock.ChoiceGroup -> block.prompt
        is MistakePdfBlock.Figure,
        is MistakePdfBlock.Image,
        is MistakePdfBlock.Note,
        null,
        -> null
    }

    private fun blockLabel(block: MistakePdfBlock?): String = when (block) {
        is MistakePdfBlock.Paragraph -> "段落"
        is MistakePdfBlock.SectionHeading -> "小标题"
        is MistakePdfBlock.Formula -> "公式"
        is MistakePdfBlock.ChoiceGroup -> "选择题题干"
        is MistakePdfBlock.Figure,
        is MistakePdfBlock.Image,
        -> "图块"
        is MistakePdfBlock.Note -> "备注块"
        null -> "文字"
    }

    /** 与渲染器同一套选项标签（A. B. …；超过 26 个用序号）。 */
    private fun choiceLabel(index: Int): String =
        if (index in 0..25) "${('A'.code + index).toChar()}." else "${index + 1}."

    private sealed interface Step {
        data class Ok(val edit: MistakePdfPaperEdit) : Step
        data class Bad(val reason: String) : Step
    }
}
