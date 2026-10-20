package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakePdfBatchEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfBatchEligibilityResult
import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6B-2 原生编辑入口（T6）的确认口。
 *
 * 钉住三件事：
 * 1. **未编辑逐位不变**：原生编辑页不碰任何东西直接导出时，草稿输入与正常批量导出
 *    （`MistakePdfBatchEligibility.check`）是同一份快照（块序列、副标题、指纹全同）。
 * 2. **编辑后重算指纹**：改一个字就换 `inputSha256`，但来源题面文档指纹不变。
 * 3. **不反写原题**：确认只读卷面模型与来源快照；`MistakeDetailState` 的值不变。
 *
 * 只验证纯本地模型，不派发模型任务；设备链另见仪器化用例。
 */
class NativePaperEditEntryTest {

    @Test
    fun `an unedited native paper export is the same snapshot as the normal batch export`() =
        runBlocking {
            val states = listOf(
                readyState("entry-a", "题 A", "原始文字AAA"),
                readyState("entry-b", "题 B", "原始文字BBB"),
            )
            val direct = MistakePdfBatchEligibility.check(states, MistakePdfLayout.DEFAULT)
                as MistakePdfBatchEligibilityResult.Eligible
            val edit = paperEdit(states, listOf("entry-a", "entry-b"))

            val draft = (confirmNativePaperEdit(edit) as NativePaperEditConfirm.Ready).draft

            assertFalse(draft.edited)
            assertEquals(direct.input.inputSha256, draft.input.inputSha256)
            assertEquals(direct.input.inputSha256, draft.sourceInputSha256)
            assertEquals(direct.input.blocks, draft.input.blocks)
            assertEquals(direct.input.subtitle, draft.input.subtitle)
            assertEquals(direct.input.problemRevisionId, draft.input.problemRevisionId)
            assertEquals(direct.input.questionDocumentSha256, draft.input.questionDocumentSha256)
        }

    @Test
    fun `editing a paragraph recomputes the fingerprint while the source document identity stays`() =
        runBlocking {
            val states = listOf(
                readyState("entry-a", "题 A", "原始文字AAA"),
                readyState("entry-b", "题 B", "原始文字BBB"),
            )
            val direct = MistakePdfBatchEligibility.check(states, MistakePdfLayout.DEFAULT)
                as MistakePdfBatchEligibilityResult.Eligible
            val edit = paperEdit(states, listOf("entry-a", "entry-b"))

            val draft = (
                confirmNativePaperEdit(
                    edit.withBlockText("entry-a", "stem-entry-a", "改过的文字"),
                ) as NativePaperEditConfirm.Ready
                ).draft

            assertTrue(draft.edited)
            assertNotEquals(direct.input.inputSha256, draft.input.inputSha256)
            // 草稿仍记得编辑前那份批量快照的指纹（来源身份）。
            assertEquals(direct.input.inputSha256, draft.sourceInputSha256)
            // 来源题面文档指纹保留：卷面编辑不产生新的题面 revision。
            assertEquals(direct.input.questionDocumentSha256, draft.input.questionDocumentSha256)
            assertTrue(
                draft.input.blocks.any { block ->
                    block is MistakePdfBlock.Paragraph && block.text == "改过的文字"
                },
            )
            // 只改文档副本：来源状态仍是原文。
            assertEquals("原始文字AAA", stateText(states.first(), "entry-a"))
        }

    @Test
    fun `restoring the original text removes the edit and returns to the source snapshot`() =
        runBlocking {
            val states = listOf(readyState("entry-a", "题 A", "原始文字AAA"))
            val direct = MistakePdfBatchEligibility.check(states, MistakePdfLayout.DEFAULT)
                as MistakePdfBatchEligibilityResult.Eligible
            val edit = paperEdit(states, listOf("entry-a"))

            val restored = edit
                .withBlockText("entry-a", "stem-entry-a", "改一下")
                .withBlockText("entry-a", "stem-entry-a", "原始文字AAA")
            val draft = (confirmNativePaperEdit(restored) as NativePaperEditConfirm.Ready).draft

            assertFalse(draft.edited)
            assertEquals(direct.input.inputSha256, draft.input.inputSha256)
            assertEquals(direct.input.blocks, draft.input.blocks)
        }

    @Test
    fun `a blank required text fails closed instead of exporting a half document`() = runBlocking {
        val edit = paperEdit(
            listOf(readyState("entry-a", "题 A", "原始文字AAA")),
            listOf("entry-a"),
        )

        val result = confirmNativePaperEdit(edit.withQuestionTitle("entry-a", "  "))

        assertEquals(NativePaperEditConfirm.BlankText, result)
        assertEquals(true, edit.withQuestionTitle("entry-a", "  ").hasBlankText)
    }

    @Test
    fun `removing the only question keeps the paper renderable`() = runBlocking {
        val edit = paperEdit(
            listOf(readyState("entry-a", "题 A", "原始文字AAA")),
            listOf("entry-a"),
        )

        val afterRemove = edit.withQuestionRemoved("entry-a")

        assertFalse(edit.canRemoveQuestion)
        assertEquals(1, afterRemove.questions.size)
    }

    private suspend fun paperEdit(
        states: List<MistakeDetailState>,
        entryIds: List<String>,
    ): MistakePdfPaperEdit {
        val byId = states.associateBy { state ->
            (state as MistakeDetailState.Ready).detail.identity.errorBookEntryId
        }
        return checkNotNull(
            MistakePdfPaperEdit.fromStates(entryIds, MistakePdfLayout.DEFAULT) { entryId ->
                byId.getValue(entryId)
            },
        )
    }

    private fun stateText(state: MistakeDetailState, entryId: String): String {
        val ready = state as MistakeDetailState.Ready
        val block = ready.questionDocument.document.blocks.single() as ContentBlock.Paragraph
        assertEquals(entryId, ready.detail.identity.errorBookEntryId)
        return block.markdown
    }

    private fun readyState(
        entryId: String,
        title: String,
        paragraphText: String,
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
}
