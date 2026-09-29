package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDraft
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.domain.ConfirmedMistakeOrganization
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationOptions
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationPreparation
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationConfirmation
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationSelection
import com.tingyun.smartmistakebook.core.domain.RecordTutorChoiceCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorMoveCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorSolutionExposureCommand
import com.tingyun.smartmistakebook.core.domain.RevealTutorSolutionCommand
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.TutorConversationReference
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionContext
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND
import com.tingyun.smartmistakebook.core.model.TutorIntentDecision
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.TutorMemoryPreference
import com.tingyun.smartmistakebook.core.model.TutorMessageIntent
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRequestedLocalCapability
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D1 / D2 回归：错题讲题页（`SavedMistakeTutorRoute`）的内部接线。
 *
 * 三处症状都是"这条路自己没把东西传下去"，所以断言必须落在**路由自己**身上——渲染
 * `SavedMistakeTutorRoute`，由它内部装配，而不是直接渲染下层组件：
 * - D1 模型要求的配图在会话页没有 `attachedImageResolver`：`TutorChatExchange` 里
 *   `attachedImageResolver?.let { ... }` 整段被跳过，图既不解析也不渲染（只有拍照会话
 *   传了它）。
 * - D2 意图确认按钮：`TutorSessionPanel` 的 `onRequestSave` / `onRequestEnd` 默认 `{}`，
 *   而本路由从未传过——学生点「确认加入错题本」「确认结束且不保存」毫无反应。这两个按钮与
 *   它们那两个参数在 A4 一并删除（模型的本地动作改走**持久确认卡**），
 *   `confirmingTheSaveOfferRunsARealLocalActionOnTheSavedMistakePage` 钉的就是替代它的那条路。
 * - A4 确认卡：`pendingRequests` 此前没有接到这条路由上，模型在这一页申请的本地动作
 *   （"把这一轮的题存进错题本"）没有出口。现在与拍照入口、智能体栏走同一条通道：
 *   模型提出请求（工具拼写 `NOTEBOOK_WRITE` 在 ask 档、工具环没有执行它 → 痕迹里的
 *   `awaiting_consent`）→ 挂一张落库的卡 → 学生裁决 → 执行这一页的真实出口（打开错题本）。
 *
 * A1 之后本页**每次进入新开一个会话**（`mistake-tutor:<uuid>`），所以这些用例不再拿
 * "存库前那次讲题"的会话 id 去恢复快照：替身按**页面此刻真正在用的会话**（订阅键里读回来）
 * 造计划快照，学生的消息由**真实派发**驱动一轮（`execute` 是那条路），断言这一轮长在
 * 本页自己的会话上、旧会话从不被认领。
 */
@RunWith(AndroidJUnit4::class)
class SavedMistakeTutorRouteInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modelRequestedFigureIsResolvedAndRenderedOnTheSavedMistakePage() {
        val resolvedImageIds = mutableListOf<String>()
        val fixture = SavedMistakeReplyFixture(attachedImages = listOf(FIGURE))
        val modelTasks = SavedMistakeModelTasks(fixture)
        setSavedMistakeScreen(
            modelTasks = modelTasks,
            attachedImageResolver = { image ->
                resolvedImageIds += image.imageId
                FIGURE_LOCAL_URI
            },
        )

        composeRule.onNodeWithTag("saved_mistake_tutor_screen").assertExists()
        val studentMessage = "这道题的过程图能画出来吗"
        sendStudentMessage(studentMessage, modelTasks)
        composeRule.onNodeWithTag("tutor_conversation_list")
            .performScrollToNode(hasTestTag("tutor_chat_assistant_1"))
        // 这一轮真的是**这一页自己的会话**上派发出去的那一轮（不是存库前那次讲题）。
        val input = modelTasks.respondRequests.single().input as TutorRespondInput
        assertEquals(studentMessage, input.studentMessage)
        assertTrue(
            "这一轮的会话必须是本页进入时新开的那个",
            input.sessionId.startsWith(ENTRY_SESSION_PREFIX),
        )
        // 模型要的配图真的被解析、真的画出来了（而不是整段被跳过）。
        composeRule.onNodeWithText(FIGURE_COLLAPSED_TITLE).assertExists()
        assertEquals(listOf(FIGURE.imageId), resolvedImageIds)
    }

    /**
     * 模型请求"把这一轮的题存进错题本"（工具拼写 `NOTEBOOK_WRITE` 落在 ask 档 → 那一轮的
     * 痕迹是 `awaiting_consent`）时，这一页必须给得出**一个真实出口**：
     *
     * 渲染：卡出现在输入区上方，两个按钮都是可点的（不是一张空壳）；
     * 裁决：学生点"加入错题本"之前，真实落点一次也没被调用过（本地动作的执行条件是**学生点了**，
     *   不是"模型提了"）；
     * 执行：点了之后这一页的真实出口（打开错题本）真的被调用，落库的行落成 ACCEPTED 并留下
     *   回喂给模型的那一句话（同一句话也是学生看到的那一句）。
     *
     * 这条用例的强度来自"整条链都在场"：路由自己装配（`pendingRequests` + 本页真实落点），
     * 学生的消息由真实派发驱动，卡由生产代码挂出（不是测试直接构造 context）。改前这一页
     * 没有这条通道——模型的申请在界面流里消失（本用例会因为"卡不存在"直接红）。
     */
    @Test
    fun confirmingTheSaveOfferRunsARealLocalActionOnTheSavedMistakePage() {
        var openedNotebook = 0
        // 落点收到的**目标**（A4 执行路径 ②）：卡里带的那道题要一路走到出口，不只是躺在行里。
        val openedProblemIds = mutableListOf<String?>()
        val pendingRequests = FakeAgentPendingRequests()
        val fixture = SavedMistakeReplyFixture(
            studentMessage = "帮我把这道题保存进错题本",
            intentDecision = TutorIntentDecision(
                intent = TutorMessageIntent.CURRENT_QUESTION_HELP,
                confidence = 0.95,
                explicitActionRequest = true,
                memoryPreference = TutorMemoryPreference.UNCHANGED,
                requestedLocalCapability =
                TutorRequestedLocalCapability.OFFER_SAVE_CURRENT_QUESTION,
            ),
        )
        val modelTasks = SavedMistakeModelTasks(
            fixture,
            toolTraceJson = notebookWriteAwaitingConsentTraceJson(),
        )
        setSavedMistakeScreen(
            modelTasks = modelTasks,
            pendingRequests = pendingRequests,
            onOpenMistakeNotebook = { problemId ->
                openedNotebook += 1
                openedProblemIds += problemId
            },
        )

        sendStudentMessage(fixture.studentMessage, modelTasks)
        awaitUsableConfirmCard()

        // 卡真的挂出来了：行已经落库、状态是 PENDING（模型这一轮提出的请求没有被静默丢掉）。
        val pending = pendingRequests.rows.single()
        assertEquals(AgentPendingRequestKind.NOTEBOOK_WRITE, pending.kind)
        assertEquals(AgentPendingRequestStatus.PENDING, pending.status)
        assertEquals(1, pendingRequests.rows.size)
        // 卡上的目标 = 这一页锚着的那道**已在错题本里**的题（错题本条目 id）。它在行里，
        // 所以进程死亡后重建的是同一张卡、同一个目标。
        assertEquals(KEY.entryId, tutorLocalActionContext(pending.payloadJson).libraryProblemId)
        // 学生还没点：真实落点一次也没被调用过。
        composeRule.runOnIdle { assertEquals(0, openedNotebook) }

        composeRule.onNodeWithTag("tutor_pending_request_accept").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { pendingRequests.resolvedRows().isNotEmpty() }
        composeRule.waitForIdle()

        // 执行：这一页的真实出口被调用了一次（学生点的那一下才执行），
        // 而且拿到的是**这道题**的错题本条目 id——不是"只打开列表"。
        composeRule.runOnIdle { assertEquals(1, openedNotebook) }
        composeRule.runOnIdle { assertEquals(listOf<String?>(KEY.entryId), openedProblemIds) }
        // 裁决落终态 + 留痕；回喂的那一句与学生看到的那一句是同一句。
        val resolved = pendingRequests.resolvedRows().single()
        assertEquals(AgentPendingRequestStatus.ACCEPTED, resolved.status)
        assertEquals("已经在错题本里打开了。", resolved.resolutionNote)
        composeRule.onNodeWithText("已经在错题本里打开了。").assertExists()
        // 卡不再挂着（终态行留在库里当留痕，不再占界面）。
        composeRule.onNodeWithTag("tutor_pending_request_accept").assertDoesNotExist()
        // A1：这一轮长在本页进入时新开的会话上，旧会话一次也没被问过。
        assertRoundBelongsToTheEntrySession(modelTasks)
    }

    /**
     * 模型提出"先到这里"（`OFFER_END_WITHOUT_SAVE`）时，这一页**不伪造一张卡**：A4 的本地动作
     * 白名单里没有"结束"这件事，这一页结束讲题的动作是学生自己的出口（返回/离开），
     * 题与对话都已经保存。挂一张"要不要结束"的卡就是给学生一个点了无处落地的按钮。
     *
     * A1 那两条断言（这一轮属于本次进入、旧会话不被认领）继续在这里钉着——它们是这一轮本身
     * 的合同，与确认卡无关。
     */
    @Test
    fun theEndOfferRoundBelongsToThisEntryAndHangsNoCard() {
        val pendingRequests = FakeAgentPendingRequests()
        val fixture = SavedMistakeReplyFixture(
            studentMessage = "先到这里，结束吧",
            intentDecision = TutorIntentDecision(
                intent = TutorMessageIntent.END_OR_PAUSE,
                confidence = 0.95,
                explicitActionRequest = true,
                memoryPreference = TutorMemoryPreference.UNCHANGED,
                requestedLocalCapability = TutorRequestedLocalCapability.OFFER_END_WITHOUT_SAVE,
            ),
        )
        val modelTasks = SavedMistakeModelTasks(fixture)
        setSavedMistakeScreen(modelTasks = modelTasks, pendingRequests = pendingRequests)

        sendStudentMessage(fixture.studentMessage, modelTasks)

        composeRule.onNodeWithTag("tutor_conversation_list")
            .performScrollToNode(hasTestTag("tutor_chat_assistant_1"))
        composeRule.onNodeWithTag("tutor_chat_assistant_1").assertExists()
        composeRule.onNodeWithTag("tutor_pending_request_accept").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(pendingRequests.rows.isEmpty()) }
        assertRoundBelongsToTheEntrySession(modelTasks)
    }

    /**
     * 学生自己打出这一轮（`tutor_chat_composer` + `tutor_chat_send`）：页面**不自动开轮**
     * （本页的合同是"入库后不自动讲题"），所以它的每一轮都得由学生显式发起。
     */
    private fun sendStudentMessage(message: String, modelTasks: SavedMistakeModelTasks) {
        // 讲解就绪 + 模型可用时输入区没有阻断（`tutor_chat_block_reason` 只在那时不存在）：
        // 这一页不自动开轮，所以"能发"这件事本身就是"讲解已经在了"的信号。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tutor_chat_block_reason")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithTag("tutor_chat_composer").performTextInput(message)
        composeRule.onNodeWithTag("tutor_chat_send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            modelTasks.respondRequests.isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /** 这一轮长在本页进入时新开的会话上；存库前那次讲题的会话**一次也没有被问过**。 */
    private fun assertRoundBelongsToTheEntrySession(modelTasks: SavedMistakeModelTasks) {
        val input = modelTasks.respondRequests.single().input as TutorRespondInput
        assertTrue(
            "这一轮的会话必须是本页进入时新开的那个：${input.sessionId}",
            input.sessionId.startsWith(ENTRY_SESSION_PREFIX),
        )
        // 页面自始至终没有向替身要过存库前那条会话（要了就等于"进入即接管旧会话"）。
        assertTrue(
            "旧会话不许被认领：${modelTasks.requestedSubjects}",
            modelTasks.requestedSubjects.none { subject -> subject.endsWith(PRE_SAVE_SESSION_ID) },
        )
        // 只派发了一次（页面自己不会开机：本页不自动开首轮）。
        assertEquals(1, modelTasks.respondRequests.size)
    }

    /**
     * 加号菜单里的「从错题库选择」必须穿过路由的两层 composable 到会话面板。
     *
     * 同 D1/D2 一条纪律：路由拿得到读取器、却没往下传，入口就静默消失——真机上表现为
     * "学生说好的选题功能在错题讲题页不见了"。所以这里渲染**路由自己**，并且让附图入口
     * 保持关闭（本页不传 `imageIntake`），于是加号按钮的存在只可能来自读取器这一条原因。
     */
    @Test
    fun theLibraryPickerEntryReachesTheSessionPanelOnTheSavedMistakePage() {
        val readerState = mutableStateOf<TutorAttachedQuestionReader?>(null)
        composeRule.setContent {
            SmartMistakeBookTheme {
                SavedMistakeTutorRoute(
                    key = KEY,
                    repository = savedMistakeRepository(),
                    organizationRepository = confirmedOrganizationRepository(),
                    teachingReferenceRepository = TutorTeachingReferenceRepository { _, _ ->
                        emptyList()
                    },
                    modelTasks = SavedMistakeModelTasks(SavedMistakeReplyFixture()),
                    interactions = inertInteractions(),
                    profile = StudyProfileOverview(),
                    // 读取器在组合里读状态，下面的 runOnIdle 才能把它接上（接线缺失的
                    // 表现正是"入口从来不存在"，所以第一条断言就是 assertDoesNotExist）。
                    attachedQuestionReader = readerState.value,
                    onOpenModelSettings = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("tutor_chat_attach").assertDoesNotExist()

        composeRule.runOnIdle {
            readerState.value = TutorAttachedQuestionReader { null }
        }
        composeRule.onNodeWithTag("tutor_chat_attach").assertExists()
        composeRule.onNodeWithTag("tutor_chat_attach").performClick()
        composeRule.onNodeWithTag("session_attach_library").assertExists()
    }

    /**
     * **还没有讲解的那一帧**也必须给得出加号与一个真的出口。
     *
     * 这一帧此前自己拼了一个精简输入框（`TutorSurfaceComposer` 直调，不传
     * `onOpenAttachMenu`、不接动作、不带附件区），于是错题讲题页——它不自动开轮，因此长期
     * 停在这一帧上——的加号（附图 / 「从错题库选择」）与「重试」出口全都静默消失，只剩一个
     * 点了没反应的按钮。这条用例同时钉住两件事：入口在，且那颗「重试」真的会派发首轮。
     */
    @Test
    fun theFrameWithoutAPlanStillOffersTheAttachEntryAndARealStartAction() {
        var startRequests = 0
        val modelTasksWithoutAnyPlan = object : ModelTaskRepository {
            override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

            override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

            override fun observeBySubject(
                subjectId: String,
                kind: ModelTaskKind,
            ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

            override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
                startRequests += 1
            }
        }
        setSavedMistakeScreen(
            modelTasks = modelTasksWithoutAnyPlan,
            attachedQuestionReader = TutorAttachedQuestionReader { null },
        )

        composeRule.onNodeWithTag("tutor_chat_attach").assertExists()
        composeRule.onNodeWithTag("tutor_chat_composer_retry").performClick()
        composeRule.runOnIdle { assertEquals(1, startRequests) }
    }

    /**
     * 等一张**可用的**确认卡：卡已经渲染出来、两个按钮都不是禁用态（还在派发中的那一帧卡是
     * 灰的——`TutorSessionAttachmentArea` 用 `enabled = !chatSending` 挡住点击，那是对的，
     * 学生不该在一轮还在跑时裁决）。
     */
    private fun awaitUsableConfirmCard() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("tutor_pending_request_accept").assertIsEnabled()
                composeRule.onNodeWithTag("tutor_pending_request_decline").assertIsEnabled()
            }.isSuccess
        }
    }

    private fun setSavedMistakeScreen(
        modelTasks: ModelTaskRepository,
        attachedImageResolver: (suspend (AttachedImage) -> String?)? = null,
        attachedQuestionReader: TutorAttachedQuestionReader? = null,
        pendingRequests: AgentPendingRequestRepository? = null,
        onOpenMistakeNotebook: (problemId: String?) -> Unit = {},
    ) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                SavedMistakeTutorRoute(
                    key = KEY,
                    repository = savedMistakeRepository(),
                    organizationRepository = confirmedOrganizationRepository(),
                    teachingReferenceRepository = TutorTeachingReferenceRepository { _, _ ->
                        emptyList()
                    },
                    modelTasks = modelTasks,
                    interactions = inertInteractions(),
                    profile = StudyProfileOverview(),
                    attachedImageResolver = attachedImageResolver,
                    attachedQuestionReader = attachedQuestionReader,
                    // 确认卡（A4）的落库端口：与 app 层同一个仓库（`agent_pending_request`），
                    // 这里是它的内存替身。
                    pendingRequests = pendingRequests,
                    onOpenMistakeNotebook = onOpenMistakeNotebook,
                    onOpenModelSettings = {},
                    onBack = {},
                )
            }
        }
    }

    /** 错题讲题页的题面：会话 id 与保存前那次讲题一致（会话页同一来源）。 */
    private fun savedMistakeRepository() = object : MistakeDetailRepository {
        override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> =
            flowOf(readyState())

        override fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState> =
            flowOf(readyState())

        override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState = readyState()

        override fun observeRevisionHistory(
            errorBookEntryId: String,
        ): Flow<List<MistakeRevisionSummary>> = flowOf(emptyList())
    }

    private fun readyState() = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = KEY.entryId,
                problemId = KEY.problemId,
                problemRevisionId = KEY.problemRevisionId,
                revisionNumber = REVISION,
                title = TITLE,
                subject = SUBJECT,
            ),
            fallbackMarkdown = "备用题面",
            source = MistakeSourceSet.Missing,
            tutorConversation = TutorConversationReference(
                // 存库前那次讲题的会话（A1 之前页面会认领它，现在只作为"上一次"的既成事实）。
                sessionId = PRE_SAVE_SESSION_ID,
                questionRevisionNumber = REVISION,
            ),
        ),
        questionDocument = questionDocument(),
    )

    private fun confirmedOrganizationRepository() = object : MistakeOrganizationRepository {
        override suspend fun prepare(
            key: MistakeRevisionKey,
            profile: StudyProfileOverview,
            provider: ProviderCapabilitySnapshot,
            attempt: Int,
            occurredAtEpochMillis: Long,
            approvedAtEpochMillis: Long,
        ): MistakeOrganizationPreparation = error("No organization round expected")

        override fun observeConfirmed(
            key: MistakeRevisionKey,
        ): Flow<ConfirmedMistakeOrganization> = flowOf(ConfirmedMistakeOrganization())

        override suspend fun applySuccessfulOrganization(
            requestId: String,
        ): ProblemOrganizationConfirmation = error("No organization write expected")

        override suspend fun confirm(
            requestId: String,
            selection: ProblemOrganizationSelection,
            acceptedAtEpochMillis: Long,
        ): ProblemOrganizationConfirmation = error("No organization write expected")

        override suspend fun correctConfirmedOrganization(
            key: MistakeRevisionKey,
            selection: ProblemOrganizationSelection,
            correctedAtEpochMillis: Long,
        ): ProblemOrganizationConfirmation = error("No organization write expected")

        override fun observeOrganizationOptions(
            key: MistakeRevisionKey,
        ): Flow<MistakeOrganizationOptions> = flowOf(
            MistakeOrganizationOptions(
                subject = SUBJECT,
                chapters = emptyList(),
                knowledgeNodes = emptyList(),
            ),
        )
    }

    private fun inertInteractions() = object : TutorInteractionRepository {
        override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = flowOf(emptyList())

        override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse =
            error("No choice write expected")

        override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse =
            error("No move write expected")

        override suspend fun revealSolution(command: RevealTutorSolutionCommand): TutorTurnResponse =
            error("No reveal expected")

        override suspend fun recordSolutionExposure(command: RecordTutorSolutionExposureCommand) =
            error("No exposure write expected")
    }

    /**
     * 错题讲题页的模型替身：**按页面真实路径**给这一页自己的会话造快照。
     *
     * 会话 id 由页面生成（A1：每次进入新开 `mistake-tutor:<uuid>`），所以替身不能预先写死一个
     * id：它从订阅键（`TutorConversationIds.captured(sessionId)`）里把这一页这一刻的会话读回来，
     * 只为**那个**会话造计划与回应快照。存库前那次讲题的会话 id（`tutor-session-before-save`）
     * 因此永远不会被认领——那正是 A1 要钉的事实。
     *
     * 计划快照是预置的（这一页不自动开轮：它来自学生上一次显式发起的首轮，同一次进入的会话 id
     * 跨重建保留）；回应轮由学生的发送**真实派发**（[execute]），所以这一页的轮次仍然只有一条
     * 来源：学生的动作。
     */
    private class SavedMistakeModelTasks(
        private val fixture: SavedMistakeReplyFixture,
        /**
         * 这一轮的工具痕迹（B1）。模型申请"存进错题本"的那一轮在痕迹里留下 `awaiting_consent`
         * （`NOTEBOOK_WRITE` 是 ask 档、工具环没有执行它），确认卡就是从这里挂出来的——
         * 替身必须按生产同一条读取路径（`observeToolTrace`）给出它，而不是让测试自己构造卡。
         */
        private val toolTraceJson: String? = null,
    ) : ModelTaskRepository {
        /** 页面订阅过的会话键（按 `TutorConversationIds.captured` 的形状），供"旧会话没被问过"断言。 */
        val requestedSubjects = mutableListOf<String>()
        val respondRequests = mutableListOf<ModelTaskRequest>()
        // 每个会话一份**热**状态流（与真实仓库同一形状）：学生派发之后的终态要能被订阅方看到，
        // 冷 `flowOf` 只会把创建那一刻的列表发一遍，后面的轮次永远不出现。
        private val plans = mutableMapOf<String, MutableStateFlow<List<ModelTaskSnapshot>>>()
        private val replies = mutableMapOf<String, MutableStateFlow<List<ModelTaskSnapshot>>>()

        private fun sessionOf(subjectId: String): String =
            subjectId.removePrefix(TutorConversationIds.CAPTURED_PREFIX)

        fun debugState(): String {
            val dispatched = respondRequests.map { request: ModelTaskRequest ->
                request.input as TutorRespondInput
            }
            return "requests=${respondRequests.size}" +
                " sessions=${dispatched.map(TutorRespondInput::sessionId)}" +
                " ordinals=${dispatched.map(TutorRespondInput::responseOrdinal)}" +
                " replies=${replies.mapValues { entry -> entry.value.value.map { task -> task.status } }}" +
                " requested=${requestedSubjects}"
        }

        private fun planFlow(sessionId: String): MutableStateFlow<List<ModelTaskSnapshot>> =
            plans.getOrPut(sessionId) { MutableStateFlow(listOf(planSnapshot(sessionId))) }

        private fun replyFlow(sessionId: String): MutableStateFlow<List<ModelTaskSnapshot>> =
            replies.getOrPut(sessionId) { MutableStateFlow(emptyList()) }

        override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(
            (plans.values + replies.values)
                .flatMap { flow -> flow.value }
                .firstOrNull { it.request.requestId == requestId },
        )

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> {
            requestedSubjects += subjectId
            val sessionId = sessionOf(subjectId)
            return when (kind) {
                ModelTaskKind.TUTOR_PLAN -> planFlow(sessionId)
                ModelTaskKind.TUTOR_RESPOND -> replyFlow(sessionId)
                else -> flowOf(emptyList())
            }
        }

        /**
         * 这一轮的工具痕迹：模型申请的"存进错题本"（`NOTEBOOK_WRITE` 落在 ask 档、工具环没有
         * 执行它）留下的就是它——确认卡的唯一来源。
         */
        override fun observeToolTrace(requestId: String): Flow<String?> = flowOf(toolTraceJson)

        /** 学生按发送那一刻真实派发的那一轮（页面自己不派发）。 */
        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            val input = request.input as? TutorRespondInput
                ?: error("The saved-mistake page only dispatches student replies")
            respondRequests += request
            // 输出必须与**本轮请求**逐位一致（`ModelTaskCompletionValidator`）：替身照同一条契约
            // 造，才不会把"模型回了"这件事做成了一个凭空的快照。
            val succeeded = ModelTaskSnapshot(
                taskId = "task:${request.requestId}",
                request = request,
                requestFingerprint = ModelTaskFingerprint.of(request),
                status = ModelTaskStatus.SUCCEEDED,
                stateVersion = 3,
                stage = ModelTaskStage.COMPLETE,
                userMessage = "回复已准备好",
                attemptCount = 1,
                provider = provider,
                output = fixture.output.copy(
                    sessionId = input.sessionId,
                    draftRevisionNumber = input.draftRevisionNumber,
                    questionDocumentId = input.questionDocument.id,
                    responseOrdinal = input.responseOrdinal,
                    cycleOrdinal = input.cycleOrdinal,
                    turnOrdinal = input.turnOrdinal,
                ),
                createdAtEpochMillis = request.occurredAtEpochMillis,
                updatedAtEpochMillis = request.occurredAtEpochMillis + 1,
            )
            val running = succeeded.copy(
                status = ModelTaskStatus.RUNNING,
                stateVersion = 2,
                stage = ModelTaskStage.PREPARING,
                userMessage = "正在回复",
                output = null,
            )
            replyFlow(input.sessionId).value = listOf(running)
            emit(running)
            replyFlow(input.sessionId).value = listOf(succeeded)
            emit(succeeded)
        }
    }

    private companion object {
        /** 本页进入时新开的会话（A1）：`entrySessionId` 的前缀，用来把"这一页自己的会话"与
         * "存库前那次讲题"分开。 */
        const val ENTRY_SESSION_PREFIX = "mistake-tutor:"
        const val PRE_SAVE_SESSION_ID = "tutor-session-before-save"
        const val REVISION = 2
        const val SUBJECT = "MATH"
        const val TITLE = "求函数的单调区间"
        const val FIGURE_LOCAL_URI = "file:///tmp/saved-mistake-figure.png"
        const val FIGURE_DESCRIPTION = "数轴上的符号变化"
        // 折叠标题 = "过程图 · " + description.take(18)。
        const val FIGURE_COLLAPSED_TITLE = "过程图 · 数轴上的符号变化"

        val KEY = MistakeRevisionKey(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-1",
        )

        val FIGURE = AttachedImage(
            imageId = "figure-1",
            kind = AttachedImageKind.GENERATE_PROCESS,
            description = FIGURE_DESCRIPTION,
            accessibilityText = "解题过程图",
        )

        val provider = ProviderCapabilitySnapshot(
            providerId = "configured-provider",
            providerDisplayName = "已配置模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
        )

        fun questionDocument() = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-1",
                title = TITLE,
                blocks = listOf(
                    ContentBlock.Paragraph(id = "stem", markdown = "已知 f(x)=x³-3x，求单调区间。"),
                ),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = "asset-1",
                    writingLayer = WritingLayer.PRINTED,
                    provenance = QuestionBlockProvenance.USER_CORRECTION,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                ),
            ),
        )

        fun questionContext(sessionId: String) = TutorQuestionContext(
            sessionId = sessionId,
            revisionNumber = REVISION,
            subject = SUBJECT,
            title = TITLE,
            questionDocument = questionDocument(),
        )

        /** 本页这一刻的会话（`entrySessionId`）上的首轮讲解：不自动开，只作为"已经在讲这道题"的现状。 */
        fun planSnapshot(sessionId: String): ModelTaskSnapshot {
            val request = buildTutorPlanRequest(
                question = questionContext(sessionId),
                profile = StudyProfileOverview(),
                provider = provider,
                requestId = "tutor-plan:$sessionId",
                occurredAtEpochMillis = 100,
            )
            return ModelTaskSnapshot(
                taskId = "task:${request.requestId}",
                request = request,
                requestFingerprint = ModelTaskFingerprint.of(request),
                status = ModelTaskStatus.SUCCEEDED,
                stateVersion = 1,
                stage = ModelTaskStage.COMPLETE,
                userMessage = "讲解已准备好",
                attemptCount = 1,
                provider = provider,
                output = TutorPlanOutput(
                    sessionId = sessionId,
                    draftRevisionNumber = REVISION,
                    questionDocumentId = "document-1",
                    plan = TutorTurnPlan(
                        openingMarkdown = "先判断导数的正负变化。",
                        solutionMarkdown = "完整主解法内容",
                        alternateMethodMarkdown = "符号表替代解法内容",
                        difficultyReasonMarkdown = "用于区分符号对应和变号遗漏。",
                        targetedEvidenceLabels = emptyList(),
                        inferredKnowledgeLabels = listOf("导数"),
                    ),
                    modelVersion = "model-v1",
                ),
                createdAtEpochMillis = 100,
                updatedAtEpochMillis = 100,
            )
        }

    }
}

/**
 * 那一轮的工具痕迹（B1）：模型在工具面里申请"存进错题本"，`NOTEBOOK_WRITE` 落在 ask 档 ——
 * 工具环**不执行它**，只在痕迹里留下这条 `awaiting_consent`。交互面据此挂出确认卡
 * （`TutorPendingRequestCoordinator` 读的就是同一条痕迹）。
 */
private fun notebookWriteAwaitingConsentTraceJson(): String = requireNotNull(
    encodeTutorTurnToolTrace(
        TutorTurnToolTrace(
            entries = listOf(
                TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = false,
                    errorKind = TOOL_AWAITING_CONSENT_ERROR_KIND,
                ),
            ),
        ),
    ),
)

/**
 * `agent_pending_request` 的内存替身（语义与 `RoomAgentPendingRequestRepository` 同口径）：
 * 幂等按 `request_id`、终态不可回头、留痕与终态一次写入。界面层读到的就是这一份行。
 */
private class FakeAgentPendingRequests : AgentPendingRequestRepository {
    private val state = MutableStateFlow<List<AgentPendingRequest>>(emptyList())

    /** 库里的全部行：卡是否真的挂出来了、裁决是否真的落成终态，都由它断言。 */
    val rows: List<AgentPendingRequest> get() = state.value

    /** 已裁决的行（终态 + 留痕）。 */
    fun resolvedRows(): List<AgentPendingRequest> = rows.filter { row -> row.status.isTerminal }

    override suspend fun createRequest(
        draft: AgentPendingRequestDraft,
        createdAtEpochMillis: Long,
    ): AgentPendingRequest? {
        val requestId = draft.requestId()
        state.value.firstOrNull { row -> row.requestId == requestId }?.let { existing ->
            return existing
        }
        val created = AgentPendingRequest(
            requestId = requestId,
            conversationArea = draft.conversationArea,
            conversationId = draft.conversationId,
            logicalOperationId = draft.logicalOperationId,
            messageId = draft.messageId,
            kind = draft.kind,
            payloadJson = draft.payloadJson,
            status = AgentPendingRequestStatus.PENDING,
            createdAtEpochMillis = createdAtEpochMillis,
        )
        state.value = state.value + created
        return created
    }

    override suspend fun resolveRequest(
        requestId: String,
        decision: AgentPendingRequestDecision,
        resolutionNote: String?,
        resolvedAtEpochMillis: Long,
    ): AgentPendingRequest? {
        val existing = state.value.firstOrNull { row -> row.requestId == requestId }
            ?: return null
        check(existing.status == AgentPendingRequestStatus.PENDING) { "already resolved" }
        val resolved = existing.copy(
            status = decision.terminalStatus,
            resolvedAtEpochMillis = resolvedAtEpochMillis,
            resolutionNote = resolutionNote,
        )
        state.value = state.value.map { row ->
            if (row.requestId == requestId) resolved else row
        }
        return resolved
    }

    override fun observePendingRequests(conversationArea: String?): Flow<List<AgentPendingRequest>> =
        state.map { all ->
            all.filter { row ->
                row.status == AgentPendingRequestStatus.PENDING &&
                    (conversationArea == null || row.conversationArea == conversationArea)
            }
        }

    override suspend fun readPendingRequests(conversationArea: String?): List<AgentPendingRequest> =
        observePendingRequests(conversationArea).first()

    override suspend fun readResolvedRequests(
        conversationArea: String,
        limit: Int,
    ): List<AgentPendingRequest> = rows
        .filter { row -> row.conversationArea == conversationArea && row.status.isTerminal }
        .sortedByDescending { row -> row.resolvedAtEpochMillis }
        .take(limit)
}

/** 一轮模型回复：正文 + 学生原话 + 意图判定 + 模型要求的配图。 */
private class SavedMistakeReplyFixture(
    val studentMessage: String = "这题我看懂了",
    intentDecision: TutorIntentDecision = TutorIntentDecision.ambiguousDefault(),
    attachedImages: List<AttachedImage> = emptyList(),
) {
    /** 会话 id 一律由**派发那一刻的真实请求**决定（`execute` 里 copy 进去）：这里给一个占位值，
     * 任何"预先写死一个会话"的读法都会在断言里露出来。 */
    val output = TutorRespondOutput(
        sessionId = "session-resolved-at-dispatch",
        draftRevisionNumber = 2,
        questionDocumentId = "document-1",
        responseOrdinal = 1,
        messageMarkdown = "先看临界点两侧的符号。",
        intentDecision = intentDecision,
        attachedImages = attachedImages,
        modelVersion = "model-v1",
    )
}
