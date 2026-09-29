package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 残留任务（tasks 里有非终态快照、又没有任何协程在动它）下的两条出口（A2/A5 的回归格）。
 *
 * 判别格有三个，缺一个就会重现当初那个"既发不出去也恢复不了"的僵局：
 * 1. **「继续回复」在场**：渲染条件与 `hasActiveTask` 不能同源（旧条件 `A && !(A||B)` 恒假）；
 * 2. **发送不静默**：挡住发送的那句话与挡它的判据同源，输入区上方就写着出口；
 * 3. **接上之后能正常发**：这一轮收到终态、残留消失，下一条消息照常发出去。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorStalledTurnResumeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 1. 出口可达：残留态、且没有东西在动它。 */
    @Test
    fun theResumeExitIsReachableWhenARoundIsLeftHalfWay() {
        val state = TutorConversationUiState(
            conversationId = CONVERSATION_ID,
            tasks = listOf(liveTask()),
        )

        // 回归的成因：旧渲染条件与 hasActiveTask 自相矛盾（A && !(A||B) 恒假），出口恒不出现。
        assertFalse(state.stalledTask != null && !state.hasActiveTask)
        // 现在的条件（A && !B）成立：出口在场，点击走到 onResumeStalledTask。
        assertTrue(state.resumeStalledTurnVisible)
        assertNotNull(state.resumableStalledTask)
        assertEquals(TUTOR_TURN_STALLED_BLOCK_REASON, state.stalledTurnBlockReason)
    }

    /** 正在发送 / 正在续上时不给「继续回复」这条假出口（那时发送键上是转圈，不是灰按钮）。 */
    @Test
    fun anInFlightRoundShowsProgressInsteadOfAFalseResumeExit() {
        val sending = TutorConversationUiState(
            conversationId = CONVERSATION_ID,
            tasks = listOf(liveTask()),
            sending = true,
        )
        val resuming = sending.copy(sending = false, resumingRequestId = "op-1")

        assertFalse(sending.resumeStalledTurnVisible)
        assertNull(sending.stalledTurnBlockReason)
        assertFalse(resuming.resumeStalledTurnVisible)
        assertNull(resuming.stalledTurnBlockReason)
    }

    /** 2. 发送不静默：挡住发送时给出的就是那句人话 + 「继续回复」出口。 */
    @Test
    fun aSendWhileARoundIsLeftHalfWaySaysWhyInsteadOfSwallowingTheTap() {
        val state = TutorConversationUiState(
            conversationId = CONVERSATION_ID,
            tasks = listOf(liveTask()),
        )

        val block = TutorComposerAvailability(
            providerReady = true,
            providerLoadFailed = false,
            stalledTurnReason = state.stalledTurnBlockReason,
        ).block()

        assertNotNull("挡住发送必须给出原因，不许静默吞掉点击", block)
        assertEquals(TUTOR_TURN_STALLED_BLOCK_REASON, block?.reason)
        assertEquals(TutorComposerActionKind.RESUME, block?.action?.kind)
        assertEquals(TUTOR_RESUME_TURN_LABEL, block?.action?.label)
        // 输入框本身照常可用（A5）：学生可以先打字，只是这条发不出去。
        assertEquals(false, block?.inputDisabled)
    }

    /** 模型/讲解的问题先说话：两者同时为真时，学生先要知道的是配置那件事。 */
    @Test
    fun theProviderProblemIsSaidFirstWhenBothAreTrue() {
        val block = TutorComposerAvailability(
            providerReady = false,
            stalledTurnReason = TUTOR_TURN_STALLED_BLOCK_REASON,
        ).block()

        assertEquals(TutorComposerActionKind.OPEN_SETTINGS, block?.action?.kind)
    }

    /** 3. 接上之后能正常发：残留任务收到终态 → 出口收起 → 下一条消息真的发出去。 */
    @Test
    fun resumingTheStalledRoundLetsTheNextMessageThrough() = runTest(dispatcher.scheduler) {
        val conversations = FakeConversationRepository()
        conversations.seed(
            conversation = conversation(),
            messages = listOf(studentMessage()),
        )
        val modelTasks = StallingLobbyModelTasks(liveTask())

        val viewModel = TutorConversationViewModel(
            savedStateHandle = SavedStateHandle(),
            conversations = conversations,
            modelTasks = modelTasks,
            imageIntake = null,
            // 残留任务只可能属于一条**已知**的会话：它正是从这条会话的台账里读回来的。
            initialConversationId = CONVERSATION_ID,
        )
        viewModel.refreshProvider()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.resumeStalledTurnVisible)

        // 残留态下按发送：不发，也不静默——原因在输入区那一块上（见上面的纯函数格）。
        viewModel.onDraftChange("第二问")
        viewModel.onSubmitDraft()
        advanceUntilIdle()
        assertTrue(modelTasks.executedRequestIds.isEmpty())
        assertEquals(1, conversations.messages(CONVERSATION_ID).size)

        // 「继续回复」：这一轮重新派发 → 收到终态。
        viewModel.onResumeStalledTask()
        advanceUntilIdle()

        assertEquals(listOf(STALLED_REQUEST_ID), modelTasks.executedRequestIds)
        assertFalse(viewModel.uiState.value.resumeStalledTurnVisible)
        assertFalse(viewModel.uiState.value.hasActiveTask)

        // 接上之后，下一条消息照常发出去（草稿还在，没被那两次点击吃掉）。
        assertEquals("第二问", viewModel.uiState.value.draft)
        viewModel.onSubmitDraft()
        advanceUntilIdle()

        assertEquals(
            listOf("第一问", "第二问"),
            conversations.messages(CONVERSATION_ID)
                .filter { message -> message.role == TutorMessageRole.STUDENT }
                .map { message -> message.bodyMarkdown },
        )
    }

    private fun conversation() = TutorConversation(
        conversationId = CONVERSATION_ID,
        area = TutorConversationAreas.AGENT,
        anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
        anchorId = null,
        anchorRevisionId = null,
        status = TutorConversationStatus.ACTIVE,
        title = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastTurnOrdinal = 1,
    )

    private fun studentMessage() = TutorMessage(
        messageId = "tutor-message:first",
        conversationId = CONVERSATION_ID,
        ordinal = 1,
        role = TutorMessageRole.STUDENT,
        bodyMarkdown = "第一问",
        status = TutorMessageStatus.PERSISTED,
        logicalOperationId = STALLED_REQUEST_ID,
        replyToMessageId = null,
        createdAtEpochMillis = 1,
        completedAtEpochMillis = 1,
        errorCode = null,
    )

    /** 停在非终态的那一条：进程死亡 / 离开页面留下的残留。 */
    private fun liveTask() = ModelTaskSnapshot(
        taskId = "task:$STALLED_REQUEST_ID",
        request = ModelTaskRequest(
            requestId = STALLED_REQUEST_ID,
            input = TutorLobbyInput(
                conversationId = CONVERSATION_ID,
                messageOrdinal = 1,
                studentMessage = "第一问",
            ),
            occurredAtEpochMillis = 1,
        ),
        requestFingerprint = ModelTaskFingerprint.of(
            ModelTaskRequest(
                requestId = STALLED_REQUEST_ID,
                input = TutorLobbyInput(
                    conversationId = CONVERSATION_ID,
                    messageOrdinal = 1,
                    studentMessage = "第一问",
                ),
                occurredAtEpochMillis = 1,
            ),
        ),
        status = ModelTaskStatus.STREAMING,
        stateVersion = 3,
        stage = ModelTaskStage.WAITING,
        userMessage = "正在回复…",
        attemptCount = 1,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 9,
    )

    private companion object {
        const val CONVERSATION_ID = "tutor-conv:stalled"
        const val STALLED_REQUEST_ID = "tutor-lobby-op:stalled"
    }
}

/**
 * 会真的走到终态的大厅替身：`execute` 把同一请求的快照换成终态，台账订阅跟着变——
 * 残留任务被接上之后就不该再是"停在半路"（否则"接上之后能发"这一格测的就不是真实语义）。
 */
private class StallingLobbyModelTasks(
    seed: ModelTaskSnapshot,
) : ModelTaskRepository {
    private val tasks = MutableStateFlow(listOf(seed))
    val executedRequestIds = mutableListOf<String>()

    override suspend fun capabilities(): ProviderCapabilitySnapshot = provider()

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = tasks.map { all ->
        all.firstOrNull { snapshot -> snapshot.request.requestId == requestId }
    }

    override fun observeBySubject(
        subjectId: String,
        kind: ModelTaskKind,
    ): Flow<List<ModelTaskSnapshot>> = tasks.map { all ->
        all.filter { snapshot -> snapshot.request.input is TutorLobbyInput }
    }

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> {
        executedRequestIds += request.requestId
        val terminal = tasks.value.first { it.request.requestId == request.requestId }.copy(
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.RETRYABLE_FAILURE,
            stage = ModelTaskStage.COMPLETE,
            output = null,
            failure = ModelTaskFailure(
                code = ModelFailureCode.TIMEOUT,
                message = "暂时没有完成",
                retryable = true,
            ),
            updatedAtEpochMillis = request.occurredAtEpochMillis + 1,
        )
        tasks.update { all ->
            all.map { snapshot ->
                if (snapshot.request.requestId == request.requestId) terminal else snapshot
            }
        }
        return flowOf(terminal)
    }

    private fun provider() = ProviderCapabilitySnapshot(
        providerId = "provider",
        providerDisplayName = "模型",
        modelId = "model",
        supportedTasks = buildSet { add(ModelTaskKind.TUTOR_LOBBY) },
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = true,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "configuration-v1",
    )
}
