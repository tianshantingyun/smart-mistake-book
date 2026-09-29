package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ConfirmedTutorSession
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorConversationReference
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelLiveText
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 一条交互面（C1）+ 致命档 A1/A3/A5 在界面这一层的判据。
 *
 * 三条入口（智能体栏 / 拍照讲题 / 错题讲题）渲染**同一个组件** [TutorConversationScreen]，
 * 所以"它们是不是同一条交互面"可以按同一个标签断言；空态是能力目录；计划阶段的实时文本走
 * 同一条通道；输入框在五种情况下都在。
 */
@RunWith(AndroidJUnit4::class)
class TutorConversationScreenInstrumentedTest : CapturedTutorSessionTestBase() {

    // ---- C1：三入口同一条交互面 ----

    @Test
    fun theCaptureEntryRendersTheSameConversationSurface() {
        val session = session()
        composeRule.setContent {
            MaterialTheme {
                Column {
                    ReadyCapturedSession(
                        session = session,
                        clock = { 10_000L },
                        saveInProgress = false,
                        saveError = null,
                        onSave = {},
                        modelTasks = ChatModelTaskRepository(session),
                        interactions = emptyInteractions(),
                        profile = StudyProfileOverview(),
                        onOpenModelSettings = {},
                    )
                }
            }
        }

        // 入口自己的标签（`captured_tutor_session_screen`）不是交互面自带的：它由入口把它拼进
        // 传给交互面的 `modifier`（`CapturedTutorSessionRoute` 的 `modifier.testTag(...)`），
        // 而这里渲染的是取数缝 `ReadyCapturedSession`，没有那个标签。标签本身由路由级用例覆盖。
        composeRule.onNodeWithTag(TUTOR_SURFACE_TAG).assertExists()
        composeRule.onNodeWithTag("tutor_conversation_list").assertExists()
    }

    @Test
    fun theSavedMistakeEntryRendersTheSameConversationSurface() {
        val session = session()
        composeRule.setContent {
            MaterialTheme {
                Column {
                    SavedMistakeTutorContent(
                        state = savedMistakeReadyState(session),
                        modelTasks = ChatModelTaskRepository(session),
                        interactions = emptyInteractions(),
                        profile = StudyProfileOverview(),
                        learningMemory = null,
                        onOpenModelSettings = {},
                        clock = { 10_000L },
                    )
                }
            }
        }

        // 同上：`saved_mistake_tutor_screen` 由 `SavedMistakeTutorRoute` 拼进 modifier
        // （路由级用例断言它）；这里渲染的是 `SavedMistakeTutorContent`。
        composeRule.onNodeWithTag(TUTOR_SURFACE_TAG).assertExists()
    }

    @Test
    fun theAgentEntryRendersTheSameSurfaceWithTheCapabilityDirectoryAsItsEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                TutorLobbyRoute(
                    onCapture = {},
                    onOpenCapabilitySettings = {},
                    onOpenMistakeNotebook = {},
                    onOpenProfile = {},
                    onOpenHistory = {},
                    conversations = emptyConversations(),
                    modelTasks = unavailableModelTasks(),
                    catalogEntries = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag(TUTOR_SURFACE_TAG).assertExists()
        composeRule.onNodeWithTag("tutor_screen").assertExists()
        // A1：进入即新会话，空态是"它能干什么"（能力目录），不是上一次的对话。
        composeRule.onNodeWithTag("tutor_empty_state").assertExists()
        composeRule.onNodeWithTag(TUTOR_CAPABILITY_DIRECTORY_TAG).assertExists()
        composeRule.onNodeWithTag("tutor_capability_explain").assertExists()
        composeRule.onNodeWithTag("tutor_capability_capture").assertExists()
    }

    // ---- A3：计划阶段也有流 ----

    @Test
    fun thePlanPhaseStreamsItsTextThroughTheSameLiveChannel() {
        val session = session()
        val streamed = "先看导数的符号，再判断单调区间。"
        setCapturedEntry(
            session = session,
            modelTasks = planPhaseModelTasks(
                session = session,
                planStatus = ModelTaskStatus.RUNNING,
                liveAnswer = streamed,
            ),
        )

        // 计划阶段（还没有讲解输出）也必须看得到逐 token 的正文——这正是 A3 指的那条缺陷：
        // 订阅此前只挂回应轮，计划阶段全程没有流。
        composeRule.onNodeWithText(streamed, substring = true).assertExists()
    }

    // ---- A5：输入框在五种情况下都在 ----

    @Test
    fun theComposerStaysVisibleWhileTheModelIsNotReady() {
        setCapturedEntry(session(), unavailableModelTasks())
        assertComposerVisibleWithReason()
    }

    @Test
    fun theComposerStaysVisibleWhileThePlanIsStillBeingPrepared() {
        val session = session()
        setCapturedEntry(
            session,
            planPhaseModelTasks(session, planStatus = ModelTaskStatus.RUNNING),
        )
        assertComposerVisibleWithReason()
    }

    @Test
    fun theComposerStaysVisibleWhenThePlanFailed() {
        val session = session()
        setCapturedEntry(
            session,
            planPhaseModelTasks(session, planStatus = ModelTaskStatus.RETRYABLE_FAILURE),
        )
        assertComposerVisibleWithReason()
        // 计划失败时输入区里就有一个能用的动作，文案是那个统一后的「重试」。
        // 按文案断言要限定节点：同一个动作有两个入口（列表里的失败卡 `tutor_plan_retry`
        // 与输入区上方的 `tutor_chat_composer_retry`），全树按文案找会撞到两个节点。
        composeRule.onNodeWithTag("tutor_chat_composer_retry").assertExists()
        composeRule.onNodeWithTag("tutor_chat_composer_retry")
            .assertTextContains(TUTOR_SURFACE_RETRY_LABEL)
    }

    @Test
    fun theComposerStaysVisibleWhenTheConversationEnded() {
        val ended = session().copy(isEndedWithoutSave = true)
        setCapturedEntry(ended, unavailableModelTasks())
        composeRule.onNodeWithTag("tutor_chat_composer").assertExists()
        composeRule.onNodeWithTag("tutor_chat_ended_reason").assertExists()
    }

    @Test
    fun theComposerIsReadyWithoutAReasonOnceTheTeachingHasArrived() {
        val session = session()
        setCapturedEntry(
            session,
            planPhaseModelTasks(session, planStatus = ModelTaskStatus.SUCCEEDED),
        )
        composeRule.onNodeWithTag("tutor_chat_composer").assertIsDisplayed()
        composeRule.onNodeWithTag("tutor_chat_block_reason").assertDoesNotExist()
    }

    private fun assertComposerVisibleWithReason() {
        composeRule.onNodeWithTag("tutor_chat_composer").assertExists()
        composeRule.onNodeWithTag("tutor_chat_block_reason").assertExists()
    }

    private fun setCapturedEntry(session: ConfirmedTutorSession, modelTasks: ModelTaskRepository) {
        composeRule.setContent {
            MaterialTheme {
                ReadyCapturedSession(
                    session = session,
                    clock = { 10_000L },
                    saveInProgress = false,
                    saveError = null,
                    onSave = {},
                    modelTasks = modelTasks,
                    interactions = emptyInteractions(),
                    profile = StudyProfileOverview(),
                    onOpenModelSettings = {},
                )
            }
        }
    }

    private fun savedMistakeReadyState(session: ConfirmedTutorSession) = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = "entry-surface",
                problemId = "problem-surface",
                problemRevisionId = "problem-revision-surface",
                revisionNumber = session.draftRevisionNumber,
                title = session.title,
                subject = session.subject,
            ),
            fallbackMarkdown = "已知题面",
            source = MistakeSourceSet.Missing,
            tutorConversation = TutorConversationReference(
                sessionId = session.sessionId,
                questionRevisionNumber = session.draftRevisionNumber,
            ),
        ),
        questionDocument = session.questionDocument,
    )

    /**
     * 讲题入口的模型替身：一条计划任务停在给定状态，可选地把逐 token 正文发到实时通道上。
     * 计划阶段正是 A3 此前完全没有流的那一段。
     */
    private inner class PlanPhaseModelTasks(
        session: ConfirmedTutorSession,
        private val planStatus: ModelTaskStatus,
        private val liveAnswer: String? = null,
    ) : ModelTaskRepository {
        private val provider = ProviderCapabilitySnapshot(
            providerId = "configured-provider",
            providerDisplayName = "已配置模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
        )
        private val planRequest = buildTutorPlanRequest(
            session = session,
            profile = StudyProfileOverview(),
            provider = provider,
            requestId = PLAN_REQUEST_ID,
            occurredAtEpochMillis = 100,
        )
        private val task = ModelTaskSnapshot(
            taskId = "task-surface-plan",
            request = planRequest,
            requestFingerprint = ModelTaskFingerprint.of(planRequest),
            status = planStatus,
            stateVersion = 1,
            stage = ModelTaskStage.PREPARING,
            userMessage = "正在准备这道题的讲解",
            attemptCount = 1,
            provider = provider,
            // 成功态必须带输出（`ModelTaskSnapshot` 的构造期不变量）。
            output = tutorOutput().takeIf { planStatus == ModelTaskStatus.SUCCEEDED },
            failure = if (planStatus == ModelTaskStatus.RETRYABLE_FAILURE) {
                ModelTaskFailure(
                    code = ModelFailureCode.TIMEOUT,
                    message = "这次讲解没有完成",
                    retryable = true,
                )
            } else {
                null
            },
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )

        override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(
            task.takeIf { it.request.requestId == requestId },
        )

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = when (kind) {
            ModelTaskKind.TUTOR_PLAN -> flowOf(listOf(task))
            else -> flowOf(emptyList())
        }

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flowOf(task)

        override fun observeLiveText(requestId: String): Flow<ModelLiveText?> = flow {
            if (liveAnswer != null && requestId == PLAN_REQUEST_ID) {
                emit(ModelLiveText(ModelLiveKind.ANSWER, liveAnswer))
            }
        }
    }

    private fun planPhaseModelTasks(
        session: ConfirmedTutorSession,
        planStatus: ModelTaskStatus,
        liveAnswer: String? = null,
    ): ModelTaskRepository = PlanPhaseModelTasks(session, planStatus, liveAnswer)

    private companion object {
        const val PLAN_REQUEST_ID = "surface-plan"
    }
}
