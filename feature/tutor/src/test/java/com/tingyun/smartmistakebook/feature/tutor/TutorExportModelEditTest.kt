package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.exportProposalPayload
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
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 6B-3 ViewModel 接线：fake 模型端口 → 白名单应用 → 草稿/说明/在途状态。
 *
 * 钉住四条：合法命令落到 `paperEdit`；非法/越界**整批不落**且说明保持原样；端口失败不吞草稿；
 * 等待期间学生手动改过时，模型结果不覆盖他的改动。没有真实 Provider——端口就是 fake。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorExportModelEditTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aFakeModelReplyAppliesWhitelistedCommandsToThePaperCopy() = runTest(dispatcher.scheduler) {
        val layout = MistakePdfLayout(includeNote = true)
        val paper = twoQuestionPaper(layout)
        val assistant = RecordingAssistant(
            reply = {
                """{"commands":[{"type":"remove_question","entryId":"entry-b"}]}"""
            },
        )
        val viewModel = viewModel(paper, assistant)
        openEditSheet(this, viewModel, layout)

        viewModel.onExportEditInstructionChange("删掉第二题")
        viewModel.onExportEditWithModel()
        advanceUntilIdle()

        val sheet = requireNotNull(viewModel.uiState.value.exportSheet)
        assertEquals(listOf("entry-a"), sheet.paperEdit?.questions?.map { it.entryId })
        assertEquals("", sheet.paperEditInstruction)
        assertFalse(sheet.paperEditBusy)
        assertFalse(sheet.paperEditMessageIsError)
        assertTrue(sheet.paperEditMessage.orEmpty().contains("已按白名单命令"))
        assertEquals(1, assistant.calls)
        // 确认导出读的就是这份编辑副本（toDraft 重算指纹并标记 edited）。
        assertTrue(requireNotNull(sheet.paperEdit).toDraft().edited)
        // 原卷面没有被改：模型改的是副本。
        assertEquals(2, paper.questions.size)
    }

    @Test
    fun anIllegalReplyKeepsTheDraftAndExplainsWhyNothingWasApplied() =
        runTest(dispatcher.scheduler) {
            val layout = MistakePdfLayout(includeNote = true)
            val paper = twoQuestionPaper(layout)
            val assistant = RecordingAssistant(
                reply = {
                    """{"commands":[{"type":"remove_question","entryId":"entry-missing"}]}"""
                },
            )
            val viewModel = viewModel(paper, assistant)
            openEditSheet(this, viewModel, layout)
            val before = requireNotNull(viewModel.uiState.value.exportSheet?.paperEdit)

            viewModel.onExportEditInstructionChange("删掉那道不存在的题")
            viewModel.onExportEditWithModel()
            advanceUntilIdle()

            val sheet = requireNotNull(viewModel.uiState.value.exportSheet)
            assertSame(before, sheet.paperEdit)
            assertFalse(sheet.paperEditBusy)
            assertTrue(sheet.paperEditMessageIsError)
            assertTrue(sheet.paperEditMessage.orEmpty().contains("没有应用"))
            assertTrue(sheet.paperEditMessage.orEmpty().contains("保持原样"))
        }

    @Test
    fun aFailingModelPortDoesNotSwallowTheDraft() = runTest(dispatcher.scheduler) {
        val layout = MistakePdfLayout(includeNote = true)
        val paper = twoQuestionPaper(layout)
        val assistant = RecordingAssistant(reply = { throw IOException("no provider") })
        val viewModel = viewModel(paper, assistant)
        openEditSheet(this, viewModel, layout)
        val before = requireNotNull(viewModel.uiState.value.exportSheet?.paperEdit)

        viewModel.onExportEditInstructionChange("随便改一下")
        viewModel.onExportEditWithModel()
        advanceUntilIdle()

        val sheet = requireNotNull(viewModel.uiState.value.exportSheet)
        assertSame(before, sheet.paperEdit)
        assertTrue(sheet.paperEditMessageIsError)
        assertTrue(sheet.paperEditMessage.orEmpty().contains("这次没有改成"))
    }

    @Test
    fun aManualEditWhileWaitingWinsOverTheLateModelResult() = runTest(dispatcher.scheduler) {
        val layout = MistakePdfLayout(includeNote = true)
        val paper = twoQuestionPaper(layout)
        val reply = CompletableDeferred<String>()
        val assistant = RecordingAssistant(reply = { reply.await() })
        val viewModel = viewModel(paper, assistant)
        openEditSheet(this, viewModel, layout)

        viewModel.onExportEditInstructionChange("把第一题标题改了")
        viewModel.onExportEditWithModel()
        runCurrent()
        assertTrue(viewModel.uiState.value.exportSheet?.paperEditBusy == true)

        // 等待期间学生自己改了标题：这份新草稿不能被迟到结果盖回去。
        viewModel.onExportEditTitle("entry-a", "手动改的标题")
        reply.complete(
            """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"模型标题"}]}""",
        )
        advanceUntilIdle()

        val sheet = requireNotNull(viewModel.uiState.value.exportSheet)
        assertEquals(
            "手动改的标题",
            sheet.paperEdit?.questions?.first { it.entryId == "entry-a" }?.title,
        )
        assertFalse(sheet.paperEditBusy)
        assertTrue(sheet.paperEditMessageIsError)
        assertTrue(sheet.paperEditMessage.orEmpty().contains("被改过"))
    }

    @Test
    fun aMissingPortIsNotAdvertisedAndNeverTouchesTheDraft() = runTest(dispatcher.scheduler) {
        val layout = MistakePdfLayout(includeNote = true)
        val paper = twoQuestionPaper(layout)
        val viewModel = viewModel(paper, assistant = null)
        openEditSheet(this, viewModel, layout)
        val before = requireNotNull(viewModel.uiState.value.exportSheet?.paperEdit)

        viewModel.onExportEditInstructionChange("删掉第二题")
        viewModel.onExportEditWithModel()
        advanceUntilIdle()

        val sheet = requireNotNull(viewModel.uiState.value.exportSheet)
        assertSame(before, sheet.paperEdit)
        assertTrue(sheet.paperEditMessageIsError)
        assertTrue(sheet.paperEditMessage.orEmpty().contains("暂时不能交给模型"))
    }

    // ---- fixtures ----

    @Test
    fun candidateChangesCannotRaceLocalPaperPreparation() = runTest(dispatcher.scheduler) {
        val layout = MistakePdfLayout()
        val paper = twoQuestionPaper(layout)
        val ready = CompletableDeferred<MistakePdfPaperEdit>()
        val viewModel = viewModel(paper, null, readPaper = { _, _ -> ready.await() })
        openSelectionSheet(this, viewModel, layout)
        viewModel.onExportCandidateToggle("entry-a", true)
        val selectedBefore = requireNotNull(viewModel.uiState.value.exportSheet).selectedEntryIds
        viewModel.onExportEditRequest(listOf("entry-a"))
        runCurrent()
        assertTrue(viewModel.uiState.value.exportSheet?.editLoading == true)
        viewModel.onExportCandidateToggle("entry-b", true)
        viewModel.onExportCandidateToggle("entry-a", false)
        assertEquals(selectedBefore, viewModel.uiState.value.exportSheet?.selectedEntryIds)
        ready.complete(paper)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.exportSheet?.editLoading == true)
        assertSame(paper, viewModel.uiState.value.exportSheet?.paperEdit)
        viewModel.onExportCandidateToggle("entry-b", true)
        assertTrue("entry-b" in requireNotNull(viewModel.uiState.value.exportSheet).selectedEntryIds)
    }

    private suspend fun openEditSheet(
        scope: TestScope,
        viewModel: TutorConversationViewModel,
        layout: MistakePdfLayout,
    ) {
        openSelectionSheet(scope, viewModel, layout)
        viewModel.onExportEditRequest(listOf("entry-a", "entry-b"))
        scope.advanceUntilIdle()
        requireNotNull(viewModel.uiState.value.exportSheet?.paperEdit)
    }

    private suspend fun openSelectionSheet(
        scope: TestScope,
        viewModel: TutorConversationViewModel,
        layout: MistakePdfLayout,
    ) {
        val request = AgentPendingRequest(
            requestId = "agent-req:op-1:START_EXPORT:0000000000000000",
            conversationArea = TutorConversationAreas.AGENT,
            conversationId = "tutor-conv:test",
            logicalOperationId = "op-1",
            messageId = "assistant-op-1",
            kind = AgentPendingRequestKind.START_EXPORT,
            payloadJson = exportProposalPayload(layout),
            status = AgentPendingRequestStatus.PENDING,
            createdAtEpochMillis = 1,
        )
        pendingRequests.seed(listOf(request))
        viewModel.onDecidePendingRequest(request, AgentPendingRequestDecision.ACCEPT)
        scope.advanceUntilIdle()
    }

    private val pendingRequests = FakeAgentPendingRequestRepository()

    private fun viewModel(
        paper: MistakePdfPaperEdit,
        assistant: TutorPaperEditAssistant?,
        readPaper: suspend (List<String>, MistakePdfLayout) -> MistakePdfPaperEdit? = { _, _ -> paper },
    ) = TutorConversationViewModel(
        savedStateHandle = SavedStateHandle(),
        conversations = FakeConversationRepository(),
        modelTasks = FailingLobbyModelTasks(),
        pendingRequests = pendingRequests,
        readExportPaperEdit = readPaper,
        paperEditAssistant = assistant,
    )

    private fun twoQuestionPaper(layout: MistakePdfLayout): MistakePdfPaperEdit =
        MistakePdfPaperEdit.fromInputs(
            inputs = listOf(
                inputOf(readyState("entry-a", "题 A", "原始文字AAA"), layout),
                inputOf(readyState("entry-b", "题 B", "原始文字BBB"), layout),
            ),
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

    private class RecordingAssistant(
        private val reply: suspend () -> String,
    ) : TutorPaperEditAssistant {
        var calls: Int = 0
            private set

        override suspend fun commandsFor(
            paper: MistakePdfPaperEdit,
            instruction: String,
        ): String {
            calls += 1
            return reply()
        }
    }
}
