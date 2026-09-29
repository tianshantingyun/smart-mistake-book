package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ArchiveTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.DeleteTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.PauseTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.SaveTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.SetTutorInteractionModeCommand
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.TutorSendPhase
import com.tingyun.smartmistakebook.core.domain.UpdateTutorMessageStatusCommand
import com.tingyun.smartmistakebook.core.domain.defaultTutorInteractionMode
import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 对话状态收敛（C2/A1/B3/B5）在 ViewModel 这一层的契约。
 *
 * 四条判别格：**进入不接管**（默认没有会话 id，也不建行）、**显式 id 才恢复**、
 * **第一条消息才建行且会话区/标题显式给**、**草稿与附件跨进程保留**。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorConversationViewModelTest {
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
    fun aFreshEntryDoesNotAdoptAnyExistingConversation() = runTest(dispatcher.scheduler) {
        val conversations = FakeConversationRepository()
        conversations.seed(
            conversation = conversation(
                conversationId = "tutor-conv:earlier",
                area = TutorConversationAreas.AGENT,
            ),
            messages = listOf(studentMessage("tutor-message:earlier", "tutor-conv:earlier", 1)),
        )

        val viewModel = viewModel(conversations = conversations)
        advanceUntilIdle()

        // A1：进入就是一次新的会话——不认领旧会话、不为"看一眼"建行（K1b）。
        assertEquals("", viewModel.uiState.value.conversationId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(conversations.createCommands.isEmpty())
    }

    @Test
    fun anExplicitConversationIdRestoresThatConversation() = runTest(dispatcher.scheduler) {
        val conversations = FakeConversationRepository()
        conversations.seed(
            conversation = conversation(
                conversationId = "tutor-conv:history-1",
                area = TutorConversationAreas.AGENT,
            ),
            messages = listOf(studentMessage("tutor-message:h1", "tutor-conv:history-1", 1)),
        )

        // 唯一一条恢复路径：历史列表点回来时给的**显式 id**。
        val viewModel = viewModel(
            conversations = conversations,
            initialConversationId = "tutor-conv:history-1",
        )
        advanceUntilIdle()

        assertEquals("tutor-conv:history-1", viewModel.uiState.value.conversationId)
        assertEquals(listOf("tutor-message:h1"), viewModel.uiState.value.messages.map { it.messageId })
        assertTrue(conversations.createCommands.isEmpty())
    }

    @Test
    fun theFirstMessageCreatesTheConversationWithTheAgentAreaAndNoTitle() =
        runTest(dispatcher.scheduler) {
            val conversations = FakeConversationRepository()
            val viewModel = viewModel(conversations = conversations)
            advanceUntilIdle()

            viewModel.onDraftChange("这道题怎么下手")
            viewModel.onSubmitDraft()
            advanceUntilIdle()

            val created = conversations.createCommands.single()
            assertEquals("命中会话区：本阶段智能体栏一个区", TutorConversationAreas.AGENT, created.area)
            // B7：写侧不再把题名当会话标题（这里的题名本来就没有——文字会话）；标题读时算。
            assertNull(created.title)
            assertEquals(TutorConversationAnchorKind.TEXT_ONLY, created.anchorKind)

            val conversationId = viewModel.uiState.value.conversationId
            assertEquals(created.conversationId, conversationId)
            assertEquals(
                listOf("这道题怎么下手"),
                conversations.messages(conversationId)
                    .filter { message -> message.role == TutorMessageRole.STUDENT }
                    .map { message -> message.bodyMarkdown },
            )
            // 状态机把这一轮推进到"已派发"（B5：界面不再有派遣预算这道闸门）；
            // 替身固定以可重试失败收场，所以终态是 RETRYABLE_FAILURE。
            assertEquals(
                TutorSendPhase.RETRYABLE_FAILURE,
                viewModel.uiState.value.sendState.phase,
            )
        }

    @Test
    fun aFailedTurnStillLetsTheNextMessageThroughInTheSameConversation() =
        runTest(dispatcher.scheduler) {
            val conversations = FakeConversationRepository()
            val viewModel = viewModel(conversations = conversations)
            advanceUntilIdle()

            viewModel.onDraftChange("第一问")
            viewModel.onSubmitDraft()
            advanceUntilIdle()

            // 第一次派发以可重试失败收场（替身固定返回 RETRYABLE_FAILURE）。
            assertEquals(
                TutorSendPhase.RETRYABLE_FAILURE,
                viewModel.uiState.value.sendState.phase,
            )

            viewModel.onDraftChange("第二问")
            viewModel.onSubmitDraft()
            advanceUntilIdle()

            // 失败之后接着说下一句：同一会话里多了一条学生消息，没有第二条会话行。
            assertEquals(1, conversations.createCommands.size)
            assertEquals(
                listOf("第一问", "第二问"),
                conversations.messages(viewModel.uiState.value.conversationId)
                    .filter { message -> message.role == TutorMessageRole.STUDENT }
                    .map { message -> message.bodyMarkdown },
            )
            assertFalse(viewModel.uiState.value.hasActiveTask)
        }

    /**
     * 大厅任务台账只留每个轮次**最近一次**派发（重试/重放会在同一轮次号下攒多个快照），
     * 且**不做长度截断**（B6：长会话里前面的轮次不能被静默丢掉）。
     */
    @Test
    fun lobbyTaskLedgerKeepsTheNewestAttemptPerRoundWithoutTruncating() {
        val tasks = (1..40).map { ordinal ->
            lobbyTask(requestId = "op-$ordinal", messageOrdinal = ordinal, updatedAt = 10L)
        } + lobbyTask(requestId = "op-3-retry", messageOrdinal = 3, updatedAt = 99L)

        val ledger = tutorLobbyConversationTasks(tasks)

        assertEquals(40, ledger.size)
        assertEquals(
            "op-3-retry",
            (ledger.first { task -> (task.request.input as TutorLobbyInput).messageOrdinal == 3 })
                .request.requestId,
        )
        assertEquals(
            (1..40).toList(),
            ledger.map { task -> (task.request.input as TutorLobbyInput).messageOrdinal },
        )
    }

    private fun lobbyTask(
        requestId: String,
        messageOrdinal: Int,
        updatedAt: Long,
    ): ModelTaskSnapshot {
        val request = ModelTaskRequest(
            requestId = requestId,
            input = TutorLobbyInput(
                conversationId = "tutor-conv:test",
                messageOrdinal = messageOrdinal,
                studentMessage = "第 $messageOrdinal 问",
            ),
            occurredAtEpochMillis = updatedAt,
        )
        return ModelTaskSnapshot(
            taskId = "task:$requestId",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.RETRYABLE_FAILURE,
            stateVersion = 1,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "这次没有完成",
            attemptCount = 1,
            failure = ModelTaskFailure(
                code = com.tingyun.smartmistakebook.core.model.ModelFailureCode.TIMEOUT,
                message = "暂时没有完成",
                retryable = true,
            ),
            createdAtEpochMillis = updatedAt,
            updatedAtEpochMillis = updatedAt,
        )
    }

    @Test
    fun composerDraftAndAttachmentsGoThroughSavedState() = runTest(dispatcher.scheduler) {
        val conversations = FakeConversationRepository()
        val restoredHandle = SavedStateHandle(
            mapOf(
                "tutorConversationDraft" to "还没发出去的半句话",
                "tutorConversationPendingImages" to arrayListOf("file:///pick-1.jpg"),
                "tutorConversationPendingCameraUri" to "file:///camera-target.jpg",
                "tutorConversationAttachMenuOpen" to true,
                "tutorConversationMistakePickerOpen" to true,
                "tutorConversationAttachReadFailed" to "这道题的题面现在读不出来，换一道试试。",
            ),
        )

        val viewModel = viewModel(
            conversations = conversations,
            savedStateHandle = restoredHandle,
        )
        advanceUntilIdle()

        // B3：进程死亡/重建之后，学生选好的图、拍照目标、草稿与两个开关都还在。
        val restored = viewModel.uiState.value
        assertEquals("还没发出去的半句话", restored.draft)
        assertEquals(listOf("file:///pick-1.jpg"), restored.pendingImages.map { it.localUri })
        assertEquals("file:///camera-target.jpg", restored.pendingCameraImageUri)
        assertTrue(restored.attachMenuOpen)
        assertTrue(restored.mistakePickerOpen)
        assertEquals("这道题的题面现在读不出来，换一道试试。", restored.attachReadFailed)

        // 反向：状态一变就写回 SavedStateHandle（只有一个写入点）。
        viewModel.onDraftChange("改了一句")
        viewModel.onRemovePendingImage(0)
        assertEquals("改了一句", restoredHandle.get<String>("tutorConversationDraft"))
        assertEquals(0, restoredHandle.get<ArrayList<String>>("tutorConversationPendingImages")?.size)
    }

    @Test
    fun theAgentAreaStartsInTheNormalModeAndSwitchingPersistsIt() = runTest(dispatcher.scheduler) {
        val conversations = FakeConversationRepository()
        val viewModel = viewModel(conversations = conversations)
        advanceUntilIdle()

        // 智能体栏默认正常模式（D-Q9；复习栏默认引导留给阶段 5）。
        assertEquals(TutorInteractionMode.NORMAL, viewModel.uiState.value.interactionMode)

        viewModel.onSwitchInteractionMode()
        advanceUntilIdle()

        assertEquals(TutorInteractionMode.GUIDED, viewModel.uiState.value.interactionMode)
        // 空会话不落库（K1b）：还没发第一条消息时，模式只活在界面里，随建行一起落。
        assertTrue(conversations.modeCommands.isEmpty())
    }

    @Test
    fun theModeSwitchLandsOnTheConversationRowOnceItExists() = runTest(dispatcher.scheduler) {
        val conversations = FakeConversationRepository()
        conversations.seed(
            conversation = conversation(
                conversationId = "tutor-conv:history-2",
                area = TutorConversationAreas.AGENT,
            ),
            messages = listOf(studentMessage("tutor-message:h2", "tutor-conv:history-2", 1)),
        )
        val viewModel = viewModel(
            conversations = conversations,
            initialConversationId = "tutor-conv:history-2",
        )
        advanceUntilIdle()

        viewModel.onSwitchInteractionMode()
        advanceUntilIdle()

        assertEquals(
            listOf(
                SetTutorInteractionModeCommand(
                    conversationId = "tutor-conv:history-2",
                    interactionMode = TutorInteractionMode.GUIDED,
                    occurredAtEpochMillis = conversations.modeCommands.single().occurredAtEpochMillis,
                ),
            ),
            conversations.modeCommands,
        )
        assertEquals(TutorInteractionMode.GUIDED, viewModel.uiState.value.interactionMode)
    }

    private fun viewModel(
        conversations: FakeConversationRepository,
        initialConversationId: String? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = TutorConversationViewModel(
        savedStateHandle = savedStateHandle,
        conversations = conversations,
        modelTasks = FailingLobbyModelTasks(),
        imageIntake = null,
        initialConversationId = initialConversationId,
    ).also { viewModel ->
        // 页面进入时会读一次模型能力（唯一的活动闸门）；测试里显式重放同一步。
        viewModel.refreshProvider()
    }

    private fun conversation(
        conversationId: String,
        area: String,
    ) = TutorConversation(
        conversationId = conversationId,
        area = area,
        anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
        anchorId = null,
        anchorRevisionId = null,
        status = TutorConversationStatus.ACTIVE,
        title = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastTurnOrdinal = 0,
    )

    private fun studentMessage(
        messageId: String,
        conversationId: String,
        ordinal: Int,
        body: String = "问一句",
    ) = TutorMessage(
        messageId = messageId,
        conversationId = conversationId,
        ordinal = ordinal,
        role = TutorMessageRole.STUDENT,
        bodyMarkdown = body,
        status = TutorMessageStatus.PERSISTED,
        logicalOperationId = "op-$messageId",
        replyToMessageId = null,
        createdAtEpochMillis = ordinal.toLong(),
        completedAtEpochMillis = ordinal.toLong(),
        errorCode = null,
    )
}

/** 内存版会话仓库：按 Room 的形状工作（同 id 创建幂等、序号由会话计数器分配）。 */
internal class FakeConversationRepository : TutorConversationRepository {
    private val conversations = MutableStateFlow<Map<String, TutorConversation>>(emptyMap())
    private val messages = MutableStateFlow<Map<String, List<TutorMessage>>>(emptyMap())
    val createCommands = mutableListOf<CreateTutorConversationCommand>()
    val modeCommands = mutableListOf<SetTutorInteractionModeCommand>()

    override suspend fun setInteractionMode(
        command: SetTutorInteractionModeCommand,
    ): TutorConversation? {
        modeCommands += command
        val existing = conversations.value[command.conversationId] ?: return null
        val updated = existing.copy(
            interactionMode = command.interactionMode,
            updatedAtEpochMillis = maxOf(existing.updatedAtEpochMillis, command.occurredAtEpochMillis),
        )
        conversations.update { rows -> rows + (updated.conversationId to updated) }
        return updated
    }

    fun seed(conversation: TutorConversation, messages: List<TutorMessage>) {
        conversations.update { rows -> rows + (conversation.conversationId to conversation) }
        this.messages.update { rows -> rows + (conversation.conversationId to messages) }
    }

    fun messages(conversationId: String): List<TutorMessage> =
        messages.value[conversationId].orEmpty()

    override fun observeRecent(limit: Int, area: String): Flow<List<TutorConversation>> =
        conversations.map { rows ->
            rows.values
                .filter { conversation -> conversation.area == area }
                .sortedByDescending(TutorConversation::updatedAtEpochMillis)
                .take(limit)
        }

    override fun observeConversation(
        conversationId: String,
    ): Flow<TutorConversationSnapshot?> = MutableStateFlow(
        conversations.value[conversationId]?.let { conversation ->
            TutorConversationSnapshot(
                conversation = conversation,
                messages = messages.value[conversationId].orEmpty(),
            )
        },
    )

    override suspend fun createConversation(
        command: CreateTutorConversationCommand,
    ): TutorConversation {
        createCommands += command
        conversations.value[command.conversationId]?.let { existing -> return existing }
        val created = TutorConversation(
            conversationId = command.conversationId,
            area = command.area,
            interactionMode = command.interactionMode
                ?: defaultTutorInteractionMode(command.area),
            anchorKind = command.anchorKind,
            anchorId = command.anchorId,
            anchorRevisionId = command.anchorRevisionId,
            status = TutorConversationStatus.ACTIVE,
            title = command.title,
            createdAtEpochMillis = command.createdAtEpochMillis,
            updatedAtEpochMillis = command.createdAtEpochMillis,
            lastTurnOrdinal = 0,
        )
        conversations.update { rows -> rows + (created.conversationId to created) }
        return created
    }

    override suspend fun appendStudentMessage(
        command: AppendTutorStudentMessageCommand,
    ): TutorMessage {
        val ordinal = command.ordinal ?: (messages(command.conversationId).size + 1)
        val message = TutorMessage(
            messageId = command.messageId,
            conversationId = command.conversationId,
            ordinal = ordinal,
            role = TutorMessageRole.STUDENT,
            bodyMarkdown = command.bodyMarkdown,
            status = TutorMessageStatus.PERSISTED,
            logicalOperationId = command.logicalOperationId,
            replyToMessageId = null,
            createdAtEpochMillis = command.createdAtEpochMillis,
            completedAtEpochMillis = command.createdAtEpochMillis,
            errorCode = null,
        )
        messages.update { rows ->
            rows + (command.conversationId to (rows[command.conversationId].orEmpty() + message))
        }
        return message
    }

    override suspend fun appendAssistantMessage(
        command: AppendTutorAssistantMessageCommand,
    ): TutorMessage {
        val ordinal = command.ordinal ?: (messages(command.conversationId).size + 1)
        val message = TutorMessage(
            messageId = command.messageId,
            conversationId = command.conversationId,
            ordinal = ordinal,
            role = TutorMessageRole.ASSISTANT,
            bodyMarkdown = command.bodyMarkdown,
            thinkingMarkdown = command.thinkingMarkdown,
            status = command.status,
            logicalOperationId = command.logicalOperationId,
            replyToMessageId = command.replyToMessageId,
            createdAtEpochMillis = command.createdAtEpochMillis,
            completedAtEpochMillis = command.completedAtEpochMillis,
            errorCode = command.errorCode,
        )
        messages.update { rows ->
            rows + (command.conversationId to (rows[command.conversationId].orEmpty() + message))
        }
        return message
    }

    override suspend fun updateMessageStatus(
        command: UpdateTutorMessageStatusCommand,
    ): TutorMessage = error("No status write expected")

    override suspend fun pauseConversation(
        command: PauseTutorConversationCommand,
    ): TutorConversation = error("No pause expected")

    override suspend fun archiveConversation(
        command: ArchiveTutorConversationCommand,
    ): TutorConversation = error("No archive expected")

    override suspend fun deleteConversation(command: DeleteTutorConversationCommand) =
        error("No delete expected")

    override suspend fun saveDraft(command: SaveTutorConversationDraftCommand) = Unit

    override suspend fun clearDraft(command: ClearTutorConversationDraftCommand) = Unit
}

/**
 * 一个支持大厅对话、但每轮都以**可重试失败**收场的模型替身：不碰网络，用来钉住"失败之后
 * 学生接着说下一句"这条路径（B5 的核心失败：旧轮次的结果不许改写新轮次）。
 */
internal class FailingLobbyModelTasks : ModelTaskRepository {
    override suspend fun capabilities(): ProviderCapabilitySnapshot = provider()

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flowOf(
        ModelTaskSnapshot(
            taskId = "task:${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.RETRYABLE_FAILURE,
            stateVersion = 1,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "这次没有完成",
            attemptCount = 1,
            provider = provider(),
            output = null,
            failure = ModelTaskFailure(
                code = com.tingyun.smartmistakebook.core.model.ModelFailureCode.TIMEOUT,
                message = "暂时没有完成",
                retryable = true,
            ),
            createdAtEpochMillis = request.occurredAtEpochMillis,
            updatedAtEpochMillis = request.occurredAtEpochMillis + 1,
        ),
    )

    private fun provider() = ProviderCapabilitySnapshot(
        providerId = "provider",
        providerDisplayName = "模型",
        modelId = "model",
        supportedTasks = buildSet {
            add(ModelTaskKind.TUTOR_LOBBY)
        },
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "configuration-v1",
    )
}
