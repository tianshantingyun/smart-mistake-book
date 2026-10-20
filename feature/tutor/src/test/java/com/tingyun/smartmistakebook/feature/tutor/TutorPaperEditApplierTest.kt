package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfChoice
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibilityResult
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperQuestion
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.PaperEditCommand
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.WritingLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6B-3 命令落地：合法命令只改**卷面副本**，非法/越界整批拒绝且草稿不动。
 *
 * 判别格：来源 `MistakeDetailState` / 来源题面指纹 / 原卷面的题与文字在应用前后必须一模一样；
 * 应用后 `toDraft()` 重算的 `inputSha256` 才是导出身份。
 */
class TutorPaperEditApplierTest {

    @Test
    fun validCommandsApplyToTheCopyAndRecomputeTheExportFingerprint() {
        val layout = MistakePdfLayout(includeNote = true)
        val paper = twoQuestionPaper(layout)
        val sourceFingerprint = paper.toDraft().input.inputSha256
        val sourceQuestions = paper.questions

        val application = TutorPaperEditApplier.apply(
            paper,
            """
            {"commands":[
              {"type":"set_block_text","entryId":"entry-a","blockId":"stem-entry-a","text":"改过的文字"},
              {"type":"move_question","entryId":"entry-b","offset":-1},
              {"type":"set_question_title","entryId":"entry-b","title":"短标题"},
              {"type":"set_note","entryId":"entry-a","note":"卷面里加的备注"}
            ]}
            """.trimIndent(),
        )

        val applied = application as TutorPaperEditApplication.Applied
        assertEquals(4, applied.appliedCommandCount)
        assertEquals(listOf("entry-b", "entry-a"), applied.edit.questions.map { it.entryId })
        assertEquals("短标题", applied.edit.questions.first().title)
        assertEquals(
            "改过的文字",
            applied.edit.questions.last().blocks.filterIsInstance<MistakePdfBlock.Paragraph>().single().text,
        )
        assertEquals("卷面里加的备注", applied.edit.questions.last().note)
        assertTrue(applied.edit.isEdited)

        // 导出身份：应用后重算的指纹必须就是 toDraft() 的指纹，且与来源快照不同。
        assertNotEquals(sourceFingerprint, applied.paperInputSha256)
        val draft = applied.edit.toDraft()
        assertTrue(draft.edited)
        assertEquals(applied.paperInputSha256, draft.input.inputSha256)
        assertEquals(sourceFingerprint, draft.sourceInputSha256)
        assertTrue(
            draft.input.blocks.any { block ->
                block is MistakePdfBlock.Paragraph && block.text == "改过的文字"
            },
        )

        // 原卷面（以及它背后的来源条目）没有被写：题序、标题、文字、备注全部保持原值。
        assertEquals(sourceQuestions, paper.questions)
        assertEquals("题 A", paper.questions.first().title)
        assertEquals("这题的备注", paper.questions.first().note)
        assertEquals(
            "原始文字AAA",
            paper.questions.first().blocks.filterIsInstance<MistakePdfBlock.Paragraph>().single().text,
        )
        assertEquals(sourceFingerprint, paper.toDraft().input.inputSha256)
        // 来源备注仍在原卷面快照里（作为 Note 块），没有被"改备注"写回条目。
        assertTrue(
            paper.toDraft().input.blocks.any { block ->
                block is MistakePdfBlock.Note && block.text == "这题的备注"
            },
        )
    }

    @Test
    fun editingTheCopyNeverWritesBackToTheQuestionDocumentOrItsAnswer() {
        val layout = MistakePdfLayout.DEFAULT
        val state = readyChoiceState("entry-a", "选择题")
        val sourceInput = inputOf(state, layout)
        val paper = MistakePdfPaperEdit.fromInputs(listOf(sourceInput), layout)

        val applied = TutorPaperEditApplier.apply(
            paper,
            """
            {"commands":[
              {"type":"set_block_text","entryId":"entry-a","blockId":"stem-entry-a","text":"改过的题干"},
              {"type":"set_choice_text","entryId":"entry-a","blockId":"choice-1","choiceId":"b","text":"乙改"}
            ]}
            """.trimIndent(),
        ) as TutorPaperEditApplication.Applied

        assertEquals("改过的题干",
            applied.edit.questions.single().blocks
                .filterIsInstance<MistakePdfBlock.Paragraph>().single().text)
        assertEquals("乙改",
            applied.edit.questions.single().blocks
                .filterIsInstance<MistakePdfBlock.ChoiceGroup>().single()
                .choices.first { it.id == "b" }.text)

        // 原题面文档没有被写：重新判定仍得到同一份来源输入（含原来的选项文字与选中的答案）。
        val reRead = inputOf(state, layout)
        assertEquals(sourceInput.inputSha256, reRead.inputSha256)
        assertEquals(sourceInput.blocks, reRead.blocks)
        val sourceChoice = state.questionDocument.document.blocks
            .filterIsInstance<ContentBlock.ChoiceGroup>()
            .single()
        assertEquals("乙", sourceChoice.choices.first { it.id == "b" }.markdown)
        assertEquals("a", sourceChoice.selectedChoiceId)
    }

    @Test
    fun removingAndRestoringAQuestionReturnsToTheExactSourceSnapshot() {
        val paper = twoQuestionPaper()
        val sourceFingerprint = paper.toDraft().input.inputSha256

        val applied = TutorPaperEditApplier.apply(
            paper,
            """
            {"commands":[
              {"type":"remove_question","entryId":"entry-b"},
              {"type":"restore_question","entryId":"entry-b"}
            ]}
            """.trimIndent(),
        ) as TutorPaperEditApplication.Applied

        assertFalse(applied.edit.isEdited)
        assertEquals(paper.questions, applied.edit.questions)
        assertEquals(sourceFingerprint, applied.paperInputSha256)
        assertEquals(sourceFingerprint, applied.edit.toDraft().input.inputSha256)
    }

    @Test
    fun oneInvalidCommandRejectsTheWholeBatchAndLeavesTheDraftUntouched() {
        val paper = twoQuestionPaper()
        val before = paper.toDraft().input.inputSha256

        val application = TutorPaperEditApplier.apply(
            paper,
            """
            {"commands":[
              {"type":"remove_question","entryId":"entry-b"},
              {"type":"set_block_text","entryId":"entry-missing","blockId":"stem","text":"x"}
            ]}
            """.trimIndent(),
        )

        val rejected = application as TutorPaperEditApplication.Rejected
        assertTrue(rejected.reason.contains("第 2 条"))
        assertTrue(rejected.reason.contains("entry-missing"))
        // 第一条合法命令也没有落：卷面重新导出仍是来源指纹。
        assertEquals(before, paper.toDraft().input.inputSha256)
        assertEquals(2, paper.questions.size)
    }

    @Test
    fun restoreCanOnlyBringBackQuestionsThatBelongToTheSourcePaper() {
        val paper = twoQuestionPaper()

        (TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"restore_question","entryId":"entry-elsewhere"}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("不能凭空加入"))
        }
        // 已经在卷面里的题也不能重复加。
        (TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"restore_question","entryId":"entry-a"}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("已经在卷面里"))
        }
    }

    @Test
    fun outOfBoundsMovesAndRemovingTheLastQuestionAreRejected() {
        val paper = twoQuestionPaper()

        (TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"move_question","entryId":"entry-a","offset":-1}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("越界"))
        }
        (TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"move_question","entryId":"entry-b","offset":5}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("越界"))
        }

        val single = singleQuestionPaper()
        (TutorPaperEditApplier.apply(
            single,
            """{"commands":[{"type":"remove_question","entryId":"entry-a"}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("至少保留一道题"))
        }
        assertEquals(1, single.questions.size)
    }

    @Test
    fun figureAndImageBlocksAreReadOnlyEvenWhenTheirIdsAreKnown() {
        val withFigureAndImage = singleQuestionPaper().withQuestionAdded(
            MistakePdfPaperQuestion(
                entryId = "entry-figure",
                title = "图题",
                subtitle = "MATH",
                blocks = listOf(
                    MistakePdfBlock.Figure(
                        id = "figure-1",
                        title = "受力分析图",
                        alternativeText = "斜面受力",
                        schema = FigureSchema.SymbolTable(
                            headers = listOf("项"),
                            rows = listOf(listOf("重力")),
                        ),
                    ),
                    MistakePdfBlock.Image(
                        id = "image-1",
                        localUri = "file:///private/clean.png",
                        title = "干净题面",
                    ),
                ),
            ),
        )

        listOf("figure-1", "image-1").forEach { blockId ->
            val rejected = TutorPaperEditApplier.apply(
                withFigureAndImage,
                """{"commands":[{"type":"set_block_text","entryId":"entry-figure","blockId":"$blockId","text":"x"}]}""",
            ) as TutorPaperEditApplication.Rejected
            assertTrue(rejected.reason.contains("图块"))
        }
        // 任何图片引用/额外字段都在解析层整条拒绝，不会"忽略那个字段"。
        val withUri = TutorPaperEditApplier.apply(
            withFigureAndImage,
            """
            {"commands":[{"type":"set_block_text","entryId":"entry-a","blockId":"stem-entry-a",
              "text":"改字","localUri":"file:///private/other.png"}]}
            """.trimIndent(),
        ) as TutorPaperEditApplication.Rejected
        assertTrue(withUri.reason.contains("白名单外"))
        assertTrue(withUri.reason.contains("localUri"))
    }

    @Test
    fun choiceTextNeedsAnExistingChoiceAndNonBlankText() {
        val paper = singleQuestionPaper().withQuestionAdded(
            MistakePdfPaperQuestion(
                entryId = "entry-choice",
                title = "选择题",
                subtitle = "MATH",
                blocks = listOf(
                    MistakePdfBlock.ChoiceGroup(
                        id = "choice-1",
                        prompt = "选择正确的一项",
                        choices = listOf(
                            MistakePdfChoice("a", "甲", selected = true, enabled = true),
                            MistakePdfChoice("b", "乙", selected = false, enabled = true),
                        ),
                        enabled = true,
                    ),
                ),
            ),
        )

        val applied = TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_choice_text","entryId":"entry-choice","blockId":"choice-1","choiceId":"b","text":"乙改"}]}""",
        ) as TutorPaperEditApplication.Applied
        val choice = applied.edit.questions
            .first { it.entryId == "entry-choice" }
            .blocks
            .filterIsInstance<MistakePdfBlock.ChoiceGroup>()
            .single()
            .choices
            .first { it.id == "b" }
        assertEquals("乙改", choice.text)

        listOf(
            """{"commands":[{"type":"set_choice_text","entryId":"entry-choice","blockId":"choice-1","choiceId":"missing","text":"x"}]}""",
            """{"commands":[{"type":"set_choice_text","entryId":"entry-choice","blockId":"stem-entry-a","choiceId":"a","text":"x"}]}""",
        ).forEach { raw ->
            assertTrue(TutorPaperEditApplier.apply(paper, raw) is TutorPaperEditApplication.Rejected)
        }
    }

    @Test
    fun notesRequireTheIncludeNoteLayoutAndBlankClearsThePaperNote() {
        val noNoteLayout = TutorPaperEditApplier.apply(
            twoQuestionPaper(),
            """{"commands":[{"type":"set_note","entryId":"entry-a","note":"x"}]}""",
        ) as TutorPaperEditApplication.Rejected
        assertTrue(noNoteLayout.reason.contains("备注区"))

        val layout = MistakePdfLayout(includeNote = true)
        val paper = twoQuestionPaper(layout)
        val added = TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_note","entryId":"entry-a","note":"新备注"}]}""",
        ) as TutorPaperEditApplication.Applied
        assertEquals("新备注", added.edit.questions.first { it.entryId == "entry-a" }.note)
        assertTrue(
            added.edit.toDraft().input.blocks.any { block ->
                block is MistakePdfBlock.Note && block.text == "新备注"
            },
        )

        val cleared = TutorPaperEditApplier.apply(
            added.edit,
            """{"commands":[{"type":"set_note","entryId":"entry-a","note":""}]}""",
        ) as TutorPaperEditApplication.Applied
        assertEquals(null, cleared.edit.questions.first { it.entryId == "entry-a" }.note)
    }

    @Test
    fun blankRequiredTextIsRejectedBeforeItCouldBlockExport() {
        val paper = twoQuestionPaper()

        (TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_block_text","entryId":"entry-a","blockId":"stem-entry-a","text":"  "}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("不能改成空"))
        }
        (TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":""}]}""",
        ) as TutorPaperEditApplication.Rejected).let { rejected ->
            assertTrue(rejected.reason.contains("title"))
        }
    }

    @Test
    fun aNoOpCommandIsNotAnEditAndKeepsTheSourceFingerprint() {
        val paper = twoQuestionPaper()
        val sourceFingerprint = paper.toDraft().input.inputSha256

        val applied = TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"题 A"}]}""",
        ) as TutorPaperEditApplication.Applied

        assertFalse(applied.edit.isEdited)
        assertEquals(sourceFingerprint, applied.paperInputSha256)
        assertEquals(sourceFingerprint, applied.edit.toDraft().sourceInputSha256)
    }

    @Test
    fun emptyCommandsAreRejectedWithAReadableExplanation() {
        val rejected = TutorPaperEditApplier.apply(
            twoQuestionPaper(),
            """{"commands":[]}""",
        ) as TutorPaperEditApplication.Rejected
        assertTrue(rejected.reason.contains("没有给出可执行的修改"))
    }

    @Test
    fun thePromptNamesOnlyWhitelistedShapesAndNeverLeaksImageLocations() {
        val withImage = singleQuestionPaper().withQuestionAdded(
            MistakePdfPaperQuestion(
                entryId = "entry-image",
                title = "带图题",
                subtitle = "MATH",
                blocks = listOf(
                    MistakePdfBlock.Image(
                        id = "image-1",
                        localUri = "file:///private/secret-clean.png",
                        title = "干净题面",
                    ),
                ),
                cleanImageLocalUri = "file:///private/another-secret.png",
                cleanImageSha256 = "a".repeat(64),
                cleanImageByteSize = 1_024L,
            ),
        )

        val prompt = TutorPaperEditPrompt.build(withImage, "把图换掉，删掉最后一题")

        PaperEditCommand.TYPES.forEach { type -> assertTrue("prompt misses $type", prompt.contains(type)) }
        assertTrue(prompt.contains("只输出 JSON"))
        assertFalse(prompt.contains("file://"))
        assertFalse(prompt.contains("localUri"))
        assertFalse(prompt.contains("cleanImage"))
        assertFalse(prompt.contains("another-secret"))
    }

    // ---- fixtures ----

    private fun twoQuestionPaper(layout: MistakePdfLayout = MistakePdfLayout.DEFAULT) =
        MistakePdfPaperEdit.fromInputs(
            inputs = listOf(
                inputOf(readyState("entry-a", "题 A", "原始文字AAA"), layout),
                inputOf(readyState("entry-b", "题 B", "原始文字BBB"), layout),
            ),
            layout = layout,
        )

    private fun singleQuestionPaper(layout: MistakePdfLayout = MistakePdfLayout.DEFAULT) =
        MistakePdfPaperEdit.fromInputs(
            inputs = listOf(inputOf(readyState("entry-a", "题 A", "原始文字AAA"), layout)),
            layout = layout,
        )

    private fun inputOf(
        state: MistakeDetailState.Ready,
        layout: MistakePdfLayout,
    ) = (MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible).input

    private fun readyState(
        entryId: String,
        title: String,
        paragraphText: String,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", paragraphText)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = identity(entryId, title),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
                userNote = "这题的备注",
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$entryId",
                    title = title,
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-$entryId",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun readyChoiceState(
        entryId: String,
        title: String,
    ): MistakeDetailState.Ready {
        val paragraph = ContentBlock.Paragraph("stem-$entryId", "原始文字AAA")
        val choiceGroup = ContentBlock.ChoiceGroup(
            id = "choice-1",
            promptMarkdown = "选择正确的一项",
            choices = listOf(
                StructuredChoice(id = "a", markdown = "甲"),
                StructuredChoice(id = "b", markdown = "乙"),
            ),
            selectedChoiceId = "a",
            enabled = true,
        )
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = identity(entryId, title),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
                userNote = "这题的备注",
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$entryId",
                    title = title,
                    blocks = listOf(paragraph, choiceGroup),
                ),
                blockEvidence = listOf(paragraph, choiceGroup).map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-$entryId",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            ),
        )
    }

    private fun identity(entryId: String, title: String) = MistakeDetailIdentity(
        errorBookEntryId = entryId,
        problemId = "problem-$entryId",
        problemRevisionId = "revision-$entryId",
        revisionNumber = 3,
        title = title,
        subject = "MATH",
    )
}
