package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeRevisionDiffTest {
    @Test
    fun stemAndTitleChangesAreReportedWithBeforeAfter() {
        val previous = document(
            title = "原题",
            blocks = listOf(ContentBlock.Paragraph("stem", "原题干")),
        )
        val next = document(
            title = "更正后的题",
            blocks = listOf(ContentBlock.Paragraph("stem", "更正后的题干")),
        )

        val changes = MistakeRevisionDiff.between("原题", previous, "更正后的题", next, false)

        assertEquals(listOf("标题", "题干"), changes.map { it.label })
        assertEquals("原题", changes[0].before)
        assertEquals("更正后的题", changes[0].after)
        assertEquals("原题干", changes[1].before)
        assertEquals("更正后的题干", changes[1].after)
    }

    @Test
    fun choiceChangesAreSemanticButSelectionStateIsNot() {
        val previous = document(
            blocks = listOf(choiceGroup(selectedChoiceId = "a")),
        )
        val pureSelectionChange = document(
            blocks = listOf(choiceGroup(selectedChoiceId = "b")),
        )
        val wordingChange = document(
            blocks = listOf(
                choiceGroup(
                    selectedChoiceId = "b",
                    choices = listOf(
                        StructuredChoice("a", "选项 A 改了"),
                        StructuredChoice("b", "选项 B"),
                    ),
                ),
            ),
        )

        assertTrue(
            MistakeRevisionDiff.between("题", previous, "题", pureSelectionChange, false).isEmpty(),
        )
        val changes = MistakeRevisionDiff.between("题", previous, "题", wordingChange, false)
        assertEquals(listOf("选项"), changes.map { it.label })
        assertEquals(MistakeRevisionChangeKind.CHOICE, changes.single().kind)
    }

    @Test
    fun figureMeaningChangesAreFigureNotStem() {
        val previous = document(
            blocks = listOf(figure("旧图义")),
        )
        val next = document(
            blocks = listOf(figure("新图义")),
        )

        val changes = MistakeRevisionDiff.between("题", previous, "题", next, false)

        assertEquals(listOf(MistakeRevisionChangeKind.FIGURE), changes.map { it.kind })
    }

    @Test
    fun oldAnswerBasisProducesAnExplicitAnswerChange() {
        val previous = document(blocks = listOf(ContentBlock.Paragraph("stem", "题干")))
        val next = document(blocks = listOf(ContentBlock.Paragraph("stem", "新题干")))

        val changes = MistakeRevisionDiff.between("题", previous, "题", next, true)

        assertEquals(
            listOf(MistakeRevisionChangeKind.STEM, MistakeRevisionChangeKind.ANSWER),
            changes.map { it.kind },
        )
        assertEquals("旧版答案与解析仍保留在历史版本", changes.last().before)
    }

    @Test
    fun anUnchangedDocumentDoesNotFabricateAnAnswerChange() {
        val question = document(blocks = listOf(ContentBlock.Paragraph("stem", "题干")))

        assertTrue(
            "没有实际修改时保存走 Unchanged，旧答案不会被丢弃，不能提前报 ANSWER 差异",
            MistakeRevisionDiff.between("题", question, "题", question, true).isEmpty(),
        )
    }

    @Test
    fun removedBlocksAreReportedInsteadOfSilentlyDropped() {
        val previous = document(
            blocks = listOf(
                ContentBlock.Paragraph("stem", "题干"),
                ContentBlock.Paragraph("extra", "多余内容"),
            ),
        )
        val next = document(blocks = listOf(ContentBlock.Paragraph("stem", "题干")))

        val changes = MistakeRevisionDiff.between("题", previous, "题", next, false)

        assertEquals("题干", changes.single().label)
        assertEquals("多余内容", changes.single().before)
        assertEquals(null, changes.single().after)
    }

    private fun document(
        title: String? = "题",
        blocks: List<ContentBlock>,
    ) = CapturedQuestionDocument(
        document = QuestionDocument(id = "document-1", title = title, blocks = blocks),
        blockEvidence = emptyList(),
    )

    private fun choiceGroup(
        selectedChoiceId: String?,
        choices: List<StructuredChoice> = listOf(
            StructuredChoice("a", "选项 A"),
            StructuredChoice("b", "选项 B"),
        ),
    ) = ContentBlock.ChoiceGroup(
        id = "choices",
        promptMarkdown = "选择一项",
        choices = choices,
        selectedChoiceId = selectedChoiceId,
    )

    private fun figure(alternativeText: String) = ContentBlock.Figure(
        id = "figure",
        title = "图",
        alternativeText = alternativeText,
        schema = com.tingyun.smartmistakebook.core.model.FigureSchema.Unknown(
            type = "test",
            fallbackText = alternativeText,
        ),
    )
}
