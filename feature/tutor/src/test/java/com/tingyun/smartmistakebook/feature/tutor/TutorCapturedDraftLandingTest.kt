package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AppendCaptureDraftPageRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSplitResult
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSummary
import com.tingyun.smartmistakebook.core.domain.CaptureDraftWorkspaceSnapshot
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.ConfirmedTutorSession
import com.tingyun.smartmistakebook.core.domain.ConsumeCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveRequest
import com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveResult
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.PendingCaptureItem
import com.tingyun.smartmistakebook.core.domain.ReplaceCaptureDraftRequest
import com.tingyun.smartmistakebook.core.domain.ResumableCaptureDraft
import com.tingyun.smartmistakebook.core.domain.SaveCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.domain.SaveTutorSessionRequest
import com.tingyun.smartmistakebook.core.domain.SplitCaptureDraftRequest
import com.tingyun.smartmistakebook.core.domain.SplitRegionDraftsRequest
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionContext
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A4 执行路径 ①（"确认加入错题本" → 拍照草稿**真落库**）与 A2 的「停止」在**生产接线**上的回归格。
 *
 * 判别格是"整条链都在场"：交互面配置里的拍照会话 id（`TutorSurfaceConfig.captureSessionId`）
 * → [tutorSurfaceConversationViewModel]（与两个讲题入口同一个装配函数）→ 协调器 → 命令 →
 * 真实落点（`saveCaptureDraft` 真的调用 `SaveTutorDraftToLibraryUseCase`）。
 *
 * 这条链此前是不可达的：`anchoredCaptureSessionId` 没有任何生产调用方传值，`SAVE_CAPTURE_DRAFT`
 * 只有直接**手搓** `TutorLocalActionContext(captureSessionId = …)` 的单元测试才触发——那种用例
 * 证明的是"函数能跑"，不是"学生点的那一下能落到库里"。这里的 context 全部由生产代码自己组。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorCapturedDraftLandingTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 「确认加入错题本」：卡真挂出来，点了之后草稿真的进了错题本。 */
    @Test
    fun aCapturedLessonTurnThatAsksToWriteTheNotebookReallySavesTheDraft() =
        runTest(dispatcher.scheduler) {
            val capture = RecordingCaptureRepository(CAPTURE_SESSION_ID)
            val requests = FakeAgentPendingRequestRepository()
            val conversations = FakeConversationRepository()
            val viewModel = capturedSurfaceViewModel(
                conversations = conversations,
                pendingRequests = requests,
                captureRepository = capture,
            )
            advanceUntilIdle()

            turnSucceeded(viewModel)
            advanceUntilIdle()

            val card = viewModel.uiState.value.pendingRequestCards.single()
            assertEquals(AgentPendingRequestKind.NOTEBOOK_WRITE, card.kind)
            // 卡上的目标就是**本次拍照的会话**（payload 里带着它，进程死亡后重建也是同一张）。
            assertEquals(
                CAPTURE_SESSION_ID,
                tutorLocalActionContext(card.payloadJson).captureSessionId,
            )
            assertTrue(capture.savedSessions.isEmpty())

            viewModel.onDecidePendingRequest(card, AgentPendingRequestDecision.ACCEPT)
            advanceUntilIdle()

            assertEquals("已经加入错题本。", viewModel.uiState.value.pendingRequestDetail)
            assertEquals(listOf(CAPTURE_SESSION_ID), capture.savedSessions)
        }

    /** 没有这个锚就没有卡：目标的来源必须是**本轮本地事实**，不是"系统觉得能存"。 */
    @Test
    fun theSameTurnWithoutACapturedSessionAnchorHangsNoCard() = runTest(dispatcher.scheduler) {
        val capture = RecordingCaptureRepository(CAPTURE_SESSION_ID)
        val requests = FakeAgentPendingRequestRepository()
        val viewModel = capturedSurfaceViewModel(
            conversations = FakeConversationRepository(),
            pendingRequests = requests,
            captureRepository = capture,
            captureSessionId = null,
        )
        advanceUntilIdle()

        turnSucceeded(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingRequestCards.isEmpty())
        assertTrue(requests.readPendingRequests(TutorConversationAreas.AGENT).isEmpty())
    }

    /**
     * A2 的「停止」在讲题侧也真的落成事实：这一轮被取消，会话流里补一条「已停止」的助手行
     * （回复它回复的那条学生消息）——界面上的出口与这条路径是同一个方法。
     */
    @Test
    fun stoppingALessonTurnCancelsItAndLeavesAStoppedAssistantRow() =
        runTest(dispatcher.scheduler) {
            val conversations = FakeConversationRepository()
            val modelTasks = TraceOnlyModelTasks(traceJson = null)
            val viewModel = capturedSurfaceViewModel(
                conversations = conversations,
                pendingRequests = null,
                captureRepository = null,
                modelTasks = modelTasks,
            )
            advanceUntilIdle()

            viewModel.onStopTutoringTurn(
                requestId = RESPOND_REQUEST_ID,
                conversationId = CAPTURED_CONVERSATION_ID,
                replyToMessageId = "tutor-message:$RESPOND_REQUEST_ID",
            )
            advanceUntilIdle()

            assertEquals(listOf(RESPOND_REQUEST_ID), modelTasks.cancelledRequestIds)
            val stopped = conversations.messages(CAPTURED_CONVERSATION_ID).single()
            assertEquals(TUTOR_CONVERSATION_STOPPED_REPLY_BODY, stopped.bodyMarkdown)
            assertEquals(TutorMessageStatus.CANCELLED, stopped.status)
            assertEquals(RESPOND_REQUEST_ID, stopped.logicalOperationId)
            assertEquals("tutor-message:$RESPOND_REQUEST_ID", stopped.replyToMessageId)
        }

    /** 走生产接线：交互面配置 → ViewModel（与路由同一个函数）。 */
    private fun capturedSurfaceViewModel(
        conversations: FakeConversationRepository,
        pendingRequests: AgentPendingRequestRepository?,
        captureRepository: CaptureWorkflowRepository?,
        captureSessionId: String? = CAPTURE_SESSION_ID,
        modelTasks: ModelTaskRepository = TraceOnlyModelTasks(traceJson = notebookWriteTraceJson()),
    ) = tutorSurfaceConversationViewModel(
        savedStateHandle = SavedStateHandle(),
        conversations = conversations,
        modelTasks = modelTasks,
        imageIntake = null,
        pendingRequests = pendingRequests,
        // 与拍照入口同一个装配函数产出的落点（真实草稿保存）。
        landings = tutorLocalActionLandings(
            openNotebook = {},
            captureRepository = captureRepository,
        ),
        surface = TutorSurfaceConfig(
            header = {},
            captureSessionId = captureSessionId,
        ),
        surfaceConversationId = CAPTURED_CONVERSATION_ID,
    )

    /** 一轮讲题成功：模型这一轮申请了"写入错题本"，工具环没执行它（ask 档）。 */
    private suspend fun turnSucceeded(viewModel: TutorConversationViewModel) {
        // 先按真实派发推进发送状态（回合要有逻辑操作 + 消息身份，卡才挂得出来）。
        viewModel.onTutorSendState(
            tutorRespondSendAdvance(
                sendState = TutorSendState(),
                logicalOperationId = RESPOND_REQUEST_ID,
                messageId = "tutor-message:$RESPOND_REQUEST_ID",
                isRetry = false,
            ),
        )
        viewModel.onTutoringTurnSucceeded(
            requestId = RESPOND_REQUEST_ID,
            conversationId = CAPTURED_CONVERSATION_ID,
            attachedImageAssetIds = emptyList(),
        )
    }

    /** 那一轮的工具痕迹：`NOTEBOOK_WRITE` 在 ask 档、工具环没有执行它 → `awaiting_consent`。 */
    private fun notebookWriteTraceJson(): String = requireNotNull(
        encodeTutorTurnToolTrace(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.NOTEBOOK_WRITE,
                        ok = false,
                        errorKind = com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND,
                    ),
                ),
            ),
        ),
    )

    private companion object {
        const val CAPTURE_SESSION_ID = "capture-session-1"
        const val CAPTURED_CONVERSATION_ID = "tutor-conversation:captured:capture-session-1"
        const val RESPOND_REQUEST_ID = "tutor-respond-request-1"
    }
}

/** 只提供工具痕迹的模型替身：这一条链读的只有 `observeToolTrace`。 */
private class TraceOnlyModelTasks(
    private val traceJson: String?,
) : ModelTaskRepository {
    val cancelledRequestIds = mutableListOf<String>()

    override suspend fun capabilities(): ProviderCapabilitySnapshot = ProviderCapabilitySnapshot(
        providerId = "provider",
        providerDisplayName = "模型",
        modelId = "model",
        supportedTasks = emptySet(),
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = true,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "configuration-v1",
    )

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flowOf()

    override fun observeToolTrace(requestId: String): Flow<String?> = flowOf(traceJson)

    override suspend fun cancel(requestId: String) {
        cancelledRequestIds += requestId
    }
}

/** 会记账的拍照仓库：只实现这条链真正用到的那两个方法。 */
private class RecordingCaptureRepository(
    private val sessionId: String,
) : CaptureWorkflowRepository {
    val savedSessions = mutableListOf<String>()
    private var saved = false

    override fun observePendingCaptures(): Flow<List<PendingCaptureItem>> = flowOf(emptyList())

    override suspend fun readPendingCapture(draftId: String): ResumableCaptureDraft? = null

    override suspend fun abandonPendingCapture(
        draftId: String,
        expectedRevisionNumber: Int,
        abandonedAtEpochMillis: Long,
    ): Boolean = error("not used")

    override suspend fun readDraftWorkspace(draftId: String): CaptureDraftWorkspaceSnapshot? = null

    override suspend fun saveDraftWorkspace(
        request: SaveCaptureDraftWorkspaceRequest,
    ): CaptureDraftWorkspaceSnapshot = error("not used")

    override suspend fun consumeDraftWorkspace(
        request: ConsumeCaptureDraftWorkspaceRequest,
    ): Boolean = error("not used")

    override suspend fun importDraft(request: CaptureDraftImportRequest): CaptureDraftSummary =
        error("not used")

    override suspend fun appendDraftPage(request: AppendCaptureDraftPageRequest): CaptureDraftSummary =
        error("not used")

    override suspend fun replaceDraft(request: ReplaceCaptureDraftRequest): CaptureDraftSummary =
        error("not used")

    override suspend fun splitDraft(request: SplitCaptureDraftRequest): CaptureDraftSplitResult =
        error("not used")

    override suspend fun createSplitRegionDrafts(
        request: SplitRegionDraftsRequest,
    ): List<CaptureDraftSummary> = emptyList()

    override suspend fun confirmForTutoring(
        request: ConfirmCapturedProblemRequest,
    ): ConfirmedTutorSession = error("not used")

    override suspend fun readTutorSession(sessionId: String): ConfirmedTutorSession? =
        session().takeIf { it.sessionId == sessionId }

    override suspend fun saveTutorSession(
        request: SaveTutorSessionRequest,
    ): CapturedProblemCommitSummary {
        savedSessions += request.sessionId
        saved = true
        return CapturedProblemCommitSummary(
            draftId = "draft-1",
            problemId = "problem-1",
            problemRevisionId = "revision-1",
            practiceUnitId = "unit-1",
            errorBookEntryId = "entry-1",
            created = true,
        )
    }

    override suspend fun endTutorSessionWithoutSaving(
        request: EndTutorSessionWithoutSaveRequest,
    ): EndTutorSessionWithoutSaveResult = error("not used")

    override suspend fun confirmAndCommit(
        request: ConfirmCapturedProblemRequest,
    ): CapturedProblemCommitSummary = error("not used")

    override suspend fun attachCleanRedrawImage(
        problemRevisionId: String,
        cleanImageBytes: ByteArray,
        cleanImageMimeType: String,
    ): Boolean = error("not used")

    private fun session() = ConfirmedTutorSession(
        sessionId = sessionId,
        draftId = "draft-1",
        draftRevisionNumber = 1,
        subject = "MATH",
        title = "函数单调性",
        questionDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "question-1",
                blocks = listOf(ContentBlock.Paragraph("stem", "求函数定义域。")),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = "asset-1",
                    sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                    writingLayer = WritingLayer.PRINTED,
                    provenance = QuestionBlockProvenance.USER_CORRECTION,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                ),
            ),
        ),
        sourceImageUri = "file:///source.png",
        createdAtEpochMillis = 1_000,
        isSaved = saved,
        errorBookEntryId = if (saved) "entry-1" else null,
    )
}
