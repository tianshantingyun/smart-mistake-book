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
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.UpdateTutorMessageStatusCommand
import com.tingyun.smartmistakebook.core.domain.defaultTutorInteractionMode
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PRD-08：草稿竞态的可控复现（JVM）。
 *
 * 四条判别格，对应用户可见的四种丢失：
 * 1. 进程/页面重建后，较旧的会话行副本不许覆盖 SavedStateHandle 里较新的恢复输入；
 * 2. 一次慢读在返回时，不许覆盖读期间学生已经打进输入框的新内容；
 * 3. 提交成功后的清稿落在新输入之后时，不许把新输入从持久副本里抹掉；
 * 4. 一次迟到的旧写（取消已在 SQL 之后）不许覆盖更晚的输入；
 * 5. 一次读失败不许被当成"读到了空草稿"，把屏幕上的输入清空。
 *
 * 另有串行口 CAS 的定向单测，以及两条如实性用例：写失败时输入仍在（重建后可读，不谎称已落盘）；
 * 真实重建不自动外发（不建行、不派发、不追加消息）。
 *
 * 这些用例在修复前分别失败；本轨不运行 Gradle，执行由门禁串行完成。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorConversationDraftRaceTest {
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
    fun aRestoredDraftIsNotOverwrittenByTheOlderConversationRow() = runTest(dispatcher.scheduler) {
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "旧稿"))
        }
        val handle = SavedStateHandle(
            mapOf(
                "tutorConversationId" to "tutor-conv:race",
                "tutorConversationDraft" to "新输入",
                "tutorConversationDraftVersion" to 3L,
            ),
        )

        val viewModel = viewModel(conversations = conversations, savedStateHandle = handle)
        advanceUntilIdle()

        // 较新的恢复输入是学生最后一次见到的内容；会话行里的是更早的持久副本。
        assertEquals("新输入", viewModel.uiState.value.draft)
        assertEquals("新输入", handle.get<String>("tutorConversationDraft"))
    }

    @Test
    fun aRowReadThatFinishesAfterNewTypingDoesNotOverwriteIt() = runTest(dispatcher.scheduler) {
        val readGate = CompletableDeferred<Unit>()
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "旧稿"))
            this.readGate = readGate
        }
        val viewModel = viewModel(
            conversations = conversations,
            initialConversationId = "tutor-conv:race",
        )

        // 会话订阅与草稿回读都已经开始、都卡在旧读上；此时学生打入新内容。
        runCurrent()
        assertTrue(conversations.readStarts >= 2)
        viewModel.onDraftChange("新输入")
        runCurrent()

        readGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("新输入", viewModel.uiState.value.draft)
    }

    @Test
    fun aFailedRowReadIsNotTreatedAsAnEmptyDraft() = runTest(dispatcher.scheduler) {
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "旧稿"))
            emitReads = false
        }
        val handle = SavedStateHandle(
            mapOf(
                "tutorConversationId" to "tutor-conv:race",
                "tutorConversationDraft" to "恢复的草稿",
            ),
        )

        val viewModel = viewModel(conversations = conversations, savedStateHandle = handle)
        advanceUntilIdle()

        // 读失败不是"读到了空草稿"：恢复输入留在原处，不因为一次失败被清空。
        assertEquals("恢复的草稿", viewModel.uiState.value.draft)
        assertEquals("恢复的草稿", handle.get<String>("tutorConversationDraft"))
    }

    @Test
    fun aSubmitClearThatLandsLateDoesNotEraseNewTyping() = runTest(dispatcher.scheduler) {
        val clearGate = CompletableDeferred<Unit>()
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "待发送"))
            this.clearGate = clearGate
        }
        val viewModel = viewModel(
            conversations = conversations,
            initialConversationId = "tutor-conv:race",
        )
        advanceUntilIdle()
        assertEquals("待发送", viewModel.uiState.value.draft)

        // 提交之后清稿的持久写卡在半路；学生在清稿真正落库之前又打了新内容。
        viewModel.onSubmitDraft()
        runCurrent()
        assertTrue(conversations.clearStarted)
        viewModel.onDraftChange("新输入")
        runCurrent()

        clearGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("新输入", viewModel.uiState.value.draft)
        assertEquals("新输入", conversations.currentDurableDraft())
    }

    @Test
    fun aStaleDraftWriteCannotOverwriteNewerTyping() = runTest(dispatcher.scheduler) {
        val saveGate = CompletableDeferred<Unit>()
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "初始"))
            firstSaveGate = saveGate
        }
        val viewModel = viewModel(
            conversations = conversations,
            initialConversationId = "tutor-conv:race",
        )
        advanceUntilIdle()

        // 第一次写卡在 SQL 里；学生继续输入，旧写随后才落库。
        viewModel.onDraftChange("A")
        runCurrent()
        viewModel.onDraftChange("AB")
        runCurrent()

        saveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("AB", viewModel.uiState.value.draft)
        assertEquals("AB", conversations.currentDurableDraft())
    }

    @Test
    fun aFailedDraftWriteStaysReadableAndComesBackAfterRecreation() = runTest(dispatcher.scheduler) {
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "旧稿"))
        }
        val handle = SavedStateHandle(mapOf("tutorConversationId" to "tutor-conv:race"))
        val first = viewModel(conversations = conversations, savedStateHandle = handle)
        advanceUntilIdle()
        assertEquals("旧稿", first.uiState.value.draft)

        conversations.failNextSave = true
        first.onDraftChange("强杀前的输入")
        advanceUntilIdle()

        // 持久写失败：不谎称已经落盘，输入本身仍然可读，且 SavedStateHandle 里有它。
        assertEquals("强杀前的输入", first.uiState.value.draft)
        assertEquals("旧稿", conversations.currentDurableDraft())
        assertEquals("强杀前的输入", handle.get<String>("tutorConversationDraft"))

        // 系统保存实例状态后的重建（不是强杀）：恢复输入不许被更旧的会话行副本盖掉。
        val recreated = viewModel(conversations = conversations, savedStateHandle = handle)
        advanceUntilIdle()

        assertEquals("强杀前的输入", recreated.uiState.value.draft)
        assertEquals("强杀前的输入", handle.get<String>("tutorConversationDraft"))
    }

    @Test
    fun recreationDoesNotSendOrCreateAnything() = runTest(dispatcher.scheduler) {
        val conversations = DraftRaceRepository().apply {
            seed(conversation("tutor-conv:race", studentDraft = "恢复的草稿"))
        }
        val modelTasks = RecordingLobbyModelTasks()
        val handle = SavedStateHandle(
            mapOf(
                "tutorConversationId" to "tutor-conv:race",
                "tutorConversationDraft" to "恢复的草稿",
                "tutorConversationDraftVersion" to 2L,
            ),
        )

        val viewModel = viewModel(
            conversations = conversations,
            modelTasks = modelTasks,
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        assertEquals("恢复的草稿", viewModel.uiState.value.draft)
        // 重建只读回自己的事实：不建会话、不追加学生消息、不发起模型派发。
        assertTrue(modelTasks.executedRequests.isEmpty())
        assertTrue(conversations.createCommands.isEmpty())
        assertTrue(conversations.appendStudentCommands.isEmpty())
        // 恢复不写库：持久副本仍是进入前的值，不被重建改写。
        assertEquals("恢复的草稿", conversations.currentDurableDraft())
    }

    @Test
    fun theSerialGateSkipsAStaleSaveAndAClearThatWouldWipeNewerText() = runTest(dispatcher.scheduler) {
        val conversations = DraftRaceRepository()
        var current = "新输入"
        val gate = TutorDraftWriteGate(conversations) { current }

        // 迟到的旧保存：要写的是更早的文本，当前输入已经不是它。
        gate.saveDraft(
            SaveTutorConversationDraftCommand(
                conversationId = "tutor-conv:race",
                draft = "旧输入",
                occurredAtEpochMillis = 1,
            ),
        )
        assertNull(conversations.currentDurableDraft())

        // 迟到的清稿：输入框里已经有清稿之后打的新字，不许清。
        gate.clearDraft(
            ClearTutorConversationDraftCommand(
                conversationId = "tutor-conv:race",
                occurredAtEpochMillis = 2,
            ),
        )
        assertNull(conversations.currentDurableDraft())

        // 当前输入的写照常落，并在迟到的写之后仍然有效。
        gate.saveDraft(
            SaveTutorConversationDraftCommand(
                conversationId = "tutor-conv:race",
                draft = "新输入",
                occurredAtEpochMillis = 3,
            ),
        )
        assertEquals("新输入", conversations.currentDurableDraft())
    }

    private fun viewModel(
        conversations: DraftRaceRepository,
        modelTasks: RecordingLobbyModelTasks = RecordingLobbyModelTasks(),
        initialConversationId: String? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = TutorConversationViewModel(
        savedStateHandle = savedStateHandle,
        conversations = conversations,
        modelTasks = modelTasks,
        imageIntake = null,
        initialConversationId = initialConversationId,
    ).also { viewModel ->
        // 与页面进入时同一步：读一次模型能力（唯一的活动闸门）。
        viewModel.refreshProvider()
    }

    private fun conversation(
        conversationId: String,
        studentDraft: String?,
    ) = TutorConversation(
        conversationId = conversationId,
        area = TutorConversationAreas.AGENT,
        interactionMode = defaultTutorInteractionMode(TutorConversationAreas.AGENT),
        anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
        anchorId = null,
        anchorRevisionId = null,
        status = TutorConversationStatus.ACTIVE,
        title = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastTurnOrdinal = 0,
        studentDraft = studentDraft,
    )
}

/**
 * 按 Room 的形状工作的内存会话仓库，并给草稿写/读加可控闸门：
 * - [readGate]：让一次旧读停在返回之前（可用于先输入、后返回的乱序）；
 * - [firstSaveGate]：让第一次草稿写停在 SQL 已完成、结果未生效的位置；
 * - [clearGate]：让提交清稿停在真正落库之前；
 * - [failNextSave]：下一次草稿写失败。
 */
internal class DraftRaceRepository : TutorConversationRepository {
    private val conversations = MutableStateFlow<Map<String, TutorConversation>>(emptyMap())
    private val messages = MutableStateFlow<Map<String, List<TutorMessage>>>(emptyMap())

    val createCommands = mutableListOf<CreateTutorConversationCommand>()
    val appendStudentCommands = mutableListOf<AppendTutorStudentMessageCommand>()
    val appendAssistantCommands = mutableListOf<AppendTutorAssistantMessageCommand>()

    var readGate: CompletableDeferred<Unit>? = null
    var readStarts = 0
    /** false = 每次读都直接结束（等价于一次读失败/读不到行），不发快照。 */
    var emitReads = true

    var clearGate: CompletableDeferred<Unit>? = null
    var clearStarted = false
    var firstSaveGate: CompletableDeferred<Unit>? = null
    var failNextSave = false

    private var durableDraft: String? = null

    fun seed(conversation: TutorConversation, messages: List<TutorMessage> = emptyList()) {
        conversations.update { rows -> rows + (conversation.conversationId to conversation) }
        this.messages.update { rows -> rows + (conversation.conversationId to messages) }
        durableDraft = conversation.studentDraft
    }

    fun currentDurableDraft(): String? = durableDraft

    override fun observeRecent(limit: Int, area: String): Flow<List<TutorConversation>> =
        conversations.map { rows ->
            rows.values.filter { conversation -> conversation.area == area }.take(limit)
        }

    override fun observeConversation(
        conversationId: String,
    ): Flow<TutorConversationSnapshot?> = flow {
        readStarts += 1
        readGate?.await()
        if (!emitReads) return@flow
        val conversation = conversations.value[conversationId]
        if (conversation != null) {
            emit(
                TutorConversationSnapshot(
                    conversation = conversation,
                    messages = messages.value[conversationId].orEmpty(),
                ),
            )
        } else {
            emit(null)
        }
    }

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
        appendStudentCommands += command
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
        appendAssistantCommands += command
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

    override suspend fun saveDraft(command: SaveTutorConversationDraftCommand) {
        val gate = firstSaveGate
        firstSaveGate = null
        // 模拟 SQL 已经在事务执行器里：取消协程不再能撤回这次写。
        withContext(NonCancellable) {
            gate?.await()
            if (failNextSave) {
                failNextSave = false
                throw IOException("draft write failed")
            }
            durableDraft = command.draft
        }
    }

    override suspend fun clearDraft(command: ClearTutorConversationDraftCommand) {
        clearStarted = true
        val gate = clearGate
        clearGate = null
        // 同上：清稿一旦已经进了事务执行器，取消也不会把它撤回。
        withContext(NonCancellable) {
            gate?.await()
            durableDraft = null
        }
    }

    private fun messages(conversationId: String): List<TutorMessage> =
        messages.value[conversationId].orEmpty()
}

/** 只记录派发的大厅模型替身：一轮以可重试失败收场，不碰网络。 */
internal class RecordingLobbyModelTasks : ModelTaskRepository {
    val executedRequests = mutableListOf<ModelTaskRequest>()

    override suspend fun capabilities(): ProviderCapabilitySnapshot = provider()

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> {
        executedRequests += request
        return flowOf(
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
                    code = ModelFailureCode.TIMEOUT,
                    message = "暂时没有完成",
                    retryable = true,
                ),
                createdAtEpochMillis = request.occurredAtEpochMillis,
                updatedAtEpochMillis = request.occurredAtEpochMillis + 1,
            ),
        )
    }

    private fun provider() = ProviderCapabilitySnapshot(
        providerId = "provider",
        providerDisplayName = "模型",
        modelId = "model",
        supportedTasks = buildSet { add(ModelTaskKind.TUTOR_LOBBY) },
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "configuration-v1",
    )
}
