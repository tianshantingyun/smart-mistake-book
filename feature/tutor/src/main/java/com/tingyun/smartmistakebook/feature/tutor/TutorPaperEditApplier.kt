package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperQuestion
import com.tingyun.smartmistakebook.core.model.PaperEditCommand
import com.tingyun.smartmistakebook.core.model.PaperEditCommandParser
import com.tingyun.smartmistakebook.core.model.PaperEditParseResult

/**
 * 一次自然语言改卷的结果：要么整批应用成功，要么整批拒绝且**草稿原样不动**。
 *
 * 不做"应用一半"：模型给了一串命令时，学生看到的是模型对这一整句要求的理解；只落一半会让
 * 卷面停在一个谁都没要求过的中间态。因此 applier 逐条在**演进中的副本**上校验，任何一条失败
 * 都回到调用方手里那份原草稿。
 */
internal sealed interface TutorPaperEditApplication {
    data class Applied(
        /** 应用后的卷面副本（新实例；来源条目与来源题面指纹都没有被写）。 */
        val edit: MistakePdfPaperEdit,
        val appliedCommandCount: Int,
        /** 应用后重算的导出输入指纹（`MistakePdfPaperEdit.toDraft().input.inputSha256`）。 */
        val paperInputSha256: String,
        /** 文字改动导致"原图需复核"的题（仍保留原图，不自动重画）。 */
        val figureReviewEntryIds: Set<String>,
    ) : TutorPaperEditApplication

    data class Rejected(val reason: String) : TutorPaperEditApplication
}

/**
 * 白名单命令 → `MistakePdfPaperEdit` 的唯一落地处（6B-3）。
 *
 * 它只调用 6B-2 已有的编辑方法（删/加回/调序/改标题/改文字/改选项/改备注），不碰
 * `MistakePdfExportInput` 以外的任何东西，更不触碰错题条目：
 * - 命令里没有图片字段；落到 Figure/Image 块的文字命令会被拒绝（"保留原图"）；
 * - `restore_question` 只能加回来源里已删除的题，模型不能从别处点题；
 * - 全部应用后调用 `toDraft()`：它本来就会重算 `inputSha256`（产物缓存键与记录身份），
 *   同时 fail-closed 地挡住空必填文字。指纹进 [TutorPaperEditApplication.Applied]。
 */
internal object TutorPaperEditApplier {
    fun apply(paper: MistakePdfPaperEdit, rawCommandsJson: String): TutorPaperEditApplication {
        val commands = when (val parsed = PaperEditCommandParser.parse(rawCommandsJson)) {
            is PaperEditParseResult.Rejected -> return TutorPaperEditApplication.Rejected(parsed.reason)
            is PaperEditParseResult.Parsed -> parsed.commands
        }
        if (commands.isEmpty()) {
            return TutorPaperEditApplication.Rejected("模型没有给出可执行的修改。")
        }
        var current = paper
        commands.forEachIndexed { index, command ->
            when (val step = applyOne(current, command)) {
                is Step.Ok -> current = step.edit
                is Step.Bad -> return TutorPaperEditApplication.Rejected(
                    "第 ${index + 1} 条命令：${step.reason}",
                )
            }
        }
        val draft = runCatching { current.toDraft() }.getOrElse {
            // 入口已按块类型挡住空必填文字；走到这里说明还有未覆盖的形状，fail-closed。
            return TutorPaperEditApplication.Rejected(
                "改完的卷面还不能导出（有必填文字为空），这次修改没有应用。",
            )
        }
        val figureReviewEntryIds = current.questions
            .map { question -> question.entryId }
            .filterTo(linkedSetOf()) { entryId -> current.figureReviewRequired(entryId) }
        return TutorPaperEditApplication.Applied(
            edit = current,
            appliedCommandCount = commands.size,
            paperInputSha256 = draft.input.inputSha256,
            figureReviewEntryIds = figureReviewEntryIds,
        )
    }

    private fun applyOne(paper: MistakePdfPaperEdit, command: PaperEditCommand): Step =
        when (command) {
            is PaperEditCommand.RemoveQuestion -> {
                requireQuestion(paper, command.entryId) {
                    if (!paper.canRemoveQuestion) {
                        Step.Bad("卷面至少保留一道题，不能删到空。")
                    } else {
                        Step.Ok(paper.withQuestionRemoved(command.entryId))
                    }
                }
            }

            is PaperEditCommand.RestoreQuestion -> {
                if (paper.questions.any { question -> question.entryId == command.entryId }) {
                    Step.Bad("这道题已经在卷面里，不能重复加回。")
                } else if (paper.removedQuestions.none { it.entryId == command.entryId }) {
                    Step.Bad("这道题不在本次卷面的来源里，不能凭空加入。")
                } else {
                    Step.Ok(paper.withQuestionRestored(command.entryId))
                }
            }

            is PaperEditCommand.MoveQuestion -> requireQuestion(paper, command.entryId) { question ->
                val index = paper.questions.indexOfFirst { it.entryId == question.entryId }
                val target = index + command.offset
                if (command.offset == 0) {
                    Step.Bad("调序偏移不能为 0。")
                } else if (target !in paper.questions.indices) {
                    Step.Bad("调序越界：这道题不能移出当前卷面。")
                } else {
                    Step.Ok(paper.withQuestionMoved(command.entryId, command.offset))
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
                    ?: return@requireQuestion Step.Bad("这道题里没有 blockId=${command.blockId} 的文字块。")
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
                if (block !is MistakePdfBlock.ChoiceGroup) {
                    Step.Bad("blockId=${command.blockId} 不是选择题块。")
                } else if (block.choices.none { choice -> choice.id == command.choiceId }) {
                    Step.Bad("这个选择题里没有 choiceId=${command.choiceId} 的选项。")
                } else if (command.text.isBlank()) {
                    Step.Bad("选项文字不能改成空。")
                } else {
                    Step.Ok(
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
                    Step.Bad("当前版式不含备注区；先打开「含备注」再改，否则改了也不会出现在卷面上。")
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

    private sealed interface Step {
        data class Ok(val edit: MistakePdfPaperEdit) : Step
        data class Bad(val reason: String) : Step
    }
}
