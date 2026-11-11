package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibilityResult
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-85：**自然语言改卷产生的模型文本**必须进入导出判据。
 *
 * 失败的形状：来源题面的 provenance 是纯用户（`USER_CORRECTION`），模型把题干改写后
 * `toDraft()` 仍只透传 `sourceInput.hasModelParsedText=false` → 导出 PDF/DOCX 不带
 * `/AIGC` / `AIGC` 属性。用户实际可用入口就是这条（`TutorConversationViewModel` →
 * `TutorPaperEditApplier.apply` → `TutorExportEditSheet`）。
 *
 * 两向都要钉住：模型写进来的文字要标；**手工编辑**（不经过模型 applier）不能标——后者是
 * 《办法》第十条的伪造标识。第三条钉住"只删题/调序"也不标：没有模型文字进入文件。
 */
class TutorPaperEditModelTextTest {

    @Test
    fun modelAppliedTextTurnsOnTheExportLabel() {
        val paper = singleQuestionPaper()
        assertFalse(
            "fixture 的来源必须是纯用户内容",
            paper.toDraft().input.hasModelParsedText,
        )

        val applied = TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_block_text","entryId":"entry-a","blockId":"stem-entry-a","text":"模型改写后的题干"}]}""",
        ) as TutorPaperEditApplication.Applied

        assertTrue(
            "模型改写的文字进入导出输入后必须判为含模型文本（KD-85）",
            applied.edit.toDraft().input.hasModelParsedText,
        )
        // 应用结果本身（以及提供给 UI 的指纹）仍然是那份编辑后的卷面。
        assertTrue(applied.edit.isEdited)
        assertTrue(applied.edit.questions.single().blocks.any { block ->
            block is com.tingyun.smartmistakebook.core.export.MistakePdfBlock.Paragraph &&
                block.text == "模型改写后的题干"
        })
    }

    @Test
    fun manualEditsStayUnlabelled() {
        val paper = singleQuestionPaper()

        val manual = paper.withBlockText("entry-a", "stem-entry-a", "学生自己改的题干")

        assertFalse(
            "手工编辑不是模型文本，不得伪标（《办法》第十条）",
            manual.toDraft().input.hasModelParsedText,
        )
    }

    @Test
    fun aStructuralOnlyModelEditDoesNotInventModelText() {
        val paper = twoQuestionPaper()

        val applied = TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"remove_question","entryId":"entry-b"}]}""",
        ) as TutorPaperEditApplication.Applied

        assertTrue(applied.edit.isEdited)
        assertFalse(
            "只删题没有模型文字进入文件；标成 AI 生成是伪造标识",
            applied.edit.toDraft().input.hasModelParsedText,
        )
    }

    @Test
    fun aNoOpModelCommandLeavesTheLabelOff() {
        val paper = singleQuestionPaper()

        val applied = TutorPaperEditApplier.apply(
            paper,
            """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"题 A"}]}""",
        ) as TutorPaperEditApplication.Applied

        assertFalse(applied.edit.isEdited)
        assertFalse(
            "没有实际写入任何模型文字，不能因为「经过模型」就加标",
            applied.edit.toDraft().input.hasModelParsedText,
        )
    }

    @Test
    fun aSourceThatAlreadyCarriesModelParsedTextStaysLabelledAfterAManualEdit() {
        val paper = modelParsedPaper()

        val manual = paper.withBlockText("entry-a", "stem-entry-a", "学生自己又改了一次")

        assertTrue(manual.toDraft().input.hasModelParsedText)
    }

    // ---- fixtures ----

    private fun twoQuestionPaper(): MistakePdfPaperEdit = MistakePdfPaperEdit.fromInputs(
        inputs = listOf(
            inputOf(readyState("entry-a", "题 A", "原始文字AAA")),
            inputOf(readyState("entry-b", "题 B", "原始文字BBB")),
        ),
        layout = MistakePdfLayout.DEFAULT,
    )

    private fun singleQuestionPaper(): MistakePdfPaperEdit = MistakePdfPaperEdit.fromInputs(
        inputs = listOf(inputOf(readyState("entry-a", "题 A", "原始文字AAA"))),
        layout = MistakePdfLayout.DEFAULT,
    )

    private fun modelParsedPaper(): MistakePdfPaperEdit =
        // `MODEL_DOCUMENT_PARSE` 的来源派生由 KD-75 的资格层用例覆盖；这里只验"来源位为真
        // 的卷面经过手工再编辑后不被洗回 false"。
        singleQuestionPaper().withModelTextApplied()

    private fun inputOf(state: MistakeDetailState.Ready) =
        (MistakePdfEligibility.check(state, MistakePdfLayout.DEFAULT)
            as MistakePdfEligibilityResult.Eligible).input

    private fun readyState(
        entryId: String,
        title: String,
        paragraphText: String,
        provenance: QuestionBlockProvenance = QuestionBlockProvenance.USER_CORRECTION,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", paragraphText)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = entryId,
                    problemId = "problem-$entryId",
                    problemRevisionId = "revision-$entryId",
                    revisionNumber = 3,
                    title = title,
                    subject = "MATH",
                ),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
                userNote = null,
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
                        provenance = provenance,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }
}
