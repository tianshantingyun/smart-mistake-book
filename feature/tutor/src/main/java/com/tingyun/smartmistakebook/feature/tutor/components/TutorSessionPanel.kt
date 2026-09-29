package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorRoundQuestionBindingPolicy
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.TutorRoundQuestionRetriever
import com.tingyun.smartmistakebook.core.domain.previousBoundRoundQuestion
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.toContiguousTutorHistory
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.tingyun.smartmistakebook.core.model.requiresModelSettings
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.MAX_TUTOR_MESSAGE_IMAGES
import com.tingyun.smartmistakebook.feature.tutor.MessageAttachmentDialog
import com.tingyun.smartmistakebook.feature.tutor.TutorMistakePickerDialog
import com.tingyun.smartmistakebook.feature.tutor.PendingMessageImage
import com.tingyun.smartmistakebook.feature.tutor.PendingMessageImagesRow
import com.tingyun.smartmistakebook.feature.tutor.tutorRespondImageIntakeError
import com.tingyun.smartmistakebook.feature.tutor.tutorRespondImageUnsupportedError
import java.io.File
import java.util.UUID
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch



@Composable
internal fun TutorModelPanel(
    question: TutorQuestionContext,
    profile: StudyProfileOverview,
    modelTasks: ModelTaskRepository,
    interactions: TutorInteractionRepository,
    /**
     * 学生会话的对话仓库：学生每一轮文字都要落进 `tutor_message`，供写侧门控
     * 逐字核对模型引文（见 `TutorRespondCommands.recordStudentTurnIfNeeded`）。
     * null 时该界面不落库，门控按空语料 fail-closed。
     */
    conversations: TutorConversationRepository? = null,
    catalogEntries: List<StudyCatalogEntry> = emptyList(),
    /**
     * 本轮候选菜单的本地文本检索源。null 时只组上一轮绑定的题这一条来源——
     * 没有检索就等于菜单里只有身份确定的题，本轮多半判成无题轮，不会误绑。
     */
    roundQuestionRetriever: TutorRoundQuestionRetriever? = null,
    /** 加号菜单"从错题库选择"选中后的题面读取器；null 时该菜单项不出现。 */
    attachedQuestionReader: TutorAttachedQuestionReader? = null,
    onLongTermWritesBlocked: () -> Unit = {},
    attachedImageResolver: (suspend (AttachedImage) -> String?)? = null,
    /**
     * 学生消息附图的读取器：既用于把选中的图片登记成规范资产，也用于在气泡里回显。
     * null 时会话页不提供附图入口（例如没有可用的资产库）。
     */
    imageIntake: LobbyMessageImageIntake? = null,
    onOpenModelSettings: () -> Unit,
    conversationEnabled: Boolean = true,
    /**
     * 这条交互面的差异（C1）：标题栏、题面卡、壳自己的任务按钮、是否自动开首轮、出口回调。
     * 由入口给（拍照会话 / 错题讲题），面板自己不再持有"这一页长什么样"。
     */
    surface: TutorSurfaceConfig,
    /** 确认卡的落库端口（A4）：null = 这个入口不接确认卡（无库界面与测试替身）。 */
    pendingRequests: AgentPendingRequestRepository? = null,
    /** 确认卡三条执行路径的落点；装配处注入（见 [TutorLocalActionLandings]）。 */
    localActionLandings: TutorLocalActionLandings = TutorLocalActionLandings(),
    clock: () -> Long = System::currentTimeMillis,
    modifier: Modifier = Modifier,
) {
    if (!conversationEnabled) {
        // 会话已结束（拍照入口的「结束且不保存」）：输入框**不消失**——只是如实说没有可以发
        // 过去的对象（A5）。它不假装能输入（只读），因为这里确实没有对象可发。
        TutorConversationScreen(
            config = surface,
            composer = {
                TutorSurfaceComposer(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    block = TutorComposerAvailability(
                        providerReady = true,
                        conversationEnded = true,
                    ).block(),
                    placeholder = "这次讲题已经结束",
                    reasonTestTag = "tutor_chat_ended_reason",
                )
            },
            autoScrollVersion = "${question.sessionId}:${question.revisionNumber}:ended",
            modifier = modifier,
        ) {
        }
        return
    }
    // 对话状态只有一个持有者（C2）：草稿、待发附件、相机目标、附件菜单、选题弹层、本轮附件题、
    // 结构化交互的在途与错误、发送状态机、首发失败文案全在 [TutorConversationViewModel] 里
    // （跨进程保留走 SavedStateHandle）。这一层只渲染 + 把意图转成调用，**不再自持对话状态**
    // ——从前那 19 个裸 `remember` 就是"两个讲题入口各持一份、进程死亡即丢"的来源。
    val viewModel: TutorConversationViewModel = viewModel(
        key = "tutor-conversation-${question.sessionId}",
        factory = TutorSurfaceConversationViewModelFactory(
            conversations = conversations ?: TutorConversationWithoutLibrary,
            modelTasks = modelTasks,
            imageIntake = imageIntake,
            pendingRequests = pendingRequests,
            landings = localActionLandings,
            surface = surface,
            // 会话键 = 这条交互面的会话行 id（K1b：行按需求创建，键先定下来）。
            surfaceConversationId = TutorConversationIds.captured(question.sessionId),
            attachedQuestionReader = attachedQuestionReader,
            catalogEntries = catalogEntries,
        ),
    )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val sessionContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 每次进入 / 每次回到前台都重读一次模型能力（外部事实，不在库里）。
    LaunchedEffect(question.sessionId) { viewModel.refreshProvider() }
    DisposableEffect(lifecycleOwner, question.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshProvider()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 「计划任务流已经发过一帧」的信号（A1，判据见 `tutorAutoStartsFirstTurn`）：
    // `collectAsState(initial = emptyList())` 会把"还没有首帧"与"确实没有任务"压成同一个值，
    // 于是 provider 早于任务流首帧到达时自动开轮会把首轮派发两遍。用 `null` 初值把这两件事
    // 分开：首帧一到（哪怕那一帧是空列表）就置真。
    val planTasksFrame by remember(question.sessionId) {
        modelTasks.observeBySubject(
            TutorConversationIds.captured(question.sessionId),
            ModelTaskKind.TUTOR_PLAN,
        )
    }.collectAsState(initial = null)
    val tasksObserved = planTasksFrame != null
    val persistedTasks: List<ModelTaskSnapshot> = planTasksFrame.orEmpty()
    // 消息流（K1a 正文 / 思考块 / 学生气泡的唯一渲染源）从 ViewModel 读：它订阅的就是这条
    // 交互面的会话键，界面不再自己再订一份（同一个事实不留两份订阅）。
    // 任务快照仍然订阅：状态、失败卡、重试入口、受门控的结构化载荷（选择题 / 完整讲解 /
    // 另一种方法）与在途实时流都靠它，但它不再是文本源。
    val persistedRespondTasks by remember(question.sessionId) {
        modelTasks.observeBySubject(TutorConversationIds.captured(question.sessionId), ModelTaskKind.TUTOR_RESPOND)
    }.collectAsState(initial = emptyList())
    val longTermWritesBlocked = persistedRespondTasks.blocksTutorLongTermWrites()
    LaunchedEffect(longTermWritesBlocked) {
        if (longTermWritesBlocked) onLongTermWritesBlocked()
    }
    val persistedResponses by remember(question.sessionId, interactions) {
        interactions.observe(question.sessionId)
    }.collectAsState(initial = emptyList())
    val sessionImageEnabled = imageIntake != null
    val currentProvider = state.provider
    val conversationProjection = remember(
        question.sessionId,
        question.revisionNumber,
        question.questionDocument.document.id,
        persistedTasks,
        persistedRespondTasks,
        persistedResponses,
        state.messages,
    ) {
        buildTutorConversationProjection(
            question = question,
            planTasks = persistedTasks,
            respondTasks = persistedRespondTasks,
            responses = persistedResponses,
            messages = state.messages,
        )
    }
    val tutorTasks = conversationProjection.planTasks
    val tutorResponses = conversationProjection.responses
    val tutorRespondTasks = conversationProjection.respondTasks
    val timeline = conversationProjection.timeline
    // Deliberately process-only: an answer is not durably unlocked until its exact bottom is visible.
    var planSolutionPreviewKeys by remember(
        question.sessionId,
        question.questionDocument.document.id,
        question.revisionNumber,
    ) { mutableStateOf(emptySet<PlanSolutionPreviewKey>()) }
    val solutionExposureTracker = rememberTutorSolutionExposureTracker(
        question = question,
        timeline = timeline,
        responses = tutorResponses,
        previewKeys = planSolutionPreviewKeys,
        longTermWritesBlocked = longTermWritesBlocked,
        interactions = interactions,
        clock = clock,
    )
    val answerExposureKeys = solutionExposureTracker.answerExposureKeys
    val currentCycle = conversationProjection.currentCycle
    val currentCycleTasks = conversationProjection.currentCyclePlanTasks
    val currentCycleResponses = conversationProjection.currentCycleResponses
    val observedTask = conversationProjection.observedPlanTask
    val executablePlanProvider = currentProvider?.takeIf { candidate ->
        candidate.executionLocation != ModelExecutionLocation.UNAVAILABLE &&
            candidate.supports(ModelTaskKind.TUTOR_PLAN)
    }

    val planCommands = remember(question.sessionId) {
        TutorPlanCommands(
            scope = scope,
            sink = TutorPlanSink(
                // Must read the backing state, not the composition-scoped
                // `executablePlanProvider` val: this lambda is captured once
                // by remember and would otherwise see the provider as it was
                // during the FIRST composition (null), silently killing every
                // auto-started turn (KD-1, docs/known-defects.md). 状态现在在
                // ViewModel 里，读它当前那一份（同一个理由，同一个读法）。
                provider = {
                    viewModel.uiState.value.provider?.takeIf { candidate ->
                        candidate.executionLocation != ModelExecutionLocation.UNAVAILABLE &&
                            candidate.supports(ModelTaskKind.TUTOR_PLAN)
                    }
                },
                question = { question },
                profile = { profile },
                clock = clock,
                planTasks = { tutorTasks },
                modelTasks = modelTasks,
                // 讲题轮的正文写进会话消息行（K1a）；会话行按需创建（K1b），所以这里给的是
                // 会话仓库本身而不是某一条已经建好的会话。
                conversations = conversations,
            ),
        )
    }

    fun executeTurn(
        cycleOrdinal: Int,
        priorConversationMemory: TutorConversationMemory?,
        priorCycleStudentMessages: List<String>,
        priorTurns: List<TutorTurnHistoryEntry>,
    ) {
        planCommands.executeTurn(
            cycleOrdinal = cycleOrdinal,
            priorConversationMemory = priorConversationMemory,
            priorCycleStudentMessages = priorCycleStudentMessages,
            priorTurns = priorTurns,
        )
    }


    val recoverableLocalPlanTask = observedTask?.takeIf { task ->
        currentProvider?.executionLocation == ModelExecutionLocation.LOCAL_NO_EGRESS &&
            task.request.egressManifest == null &&
            task.status.isTutorExecutionPending()
    }
    LaunchedEffect(
        recoverableLocalPlanTask?.request?.requestId,
        recoverableLocalPlanTask?.stateVersion,
    ) {
        recoverableLocalPlanTask?.let { task ->
            // 恢复 = 把这条**已经持久化**的请求跑完；正文由写入器落进消息行（K1a），
            // 否则续上的这一轮在屏幕上是一条空回复（见 `resumePersistedTutorTurn`）。
            resumePersistedTutorTurn(
                modelTasks = modelTasks,
                conversations = conversations,
                request = task.request,
            )
        }
    }
    // 闸门与在途判定读的是 backing state（不是组合期快照）：这几个值在下面的早退分支里也要用。
    val respondSupported = currentProvider?.let { candidate ->
        candidate.executionLocation != ModelExecutionLocation.UNAVAILABLE &&
            candidate.supports(ModelTaskKind.TUTOR_RESPOND)
    } == true
    val respondAgentAuthorized =
        tutorAgentChatEnabled(currentProvider, ModelTaskKind.TUTOR_RESPOND)
    val latestRespondTasks = conversationProjection.latestRespondTasks
    /**
     * 本会话最近一次回应轮里仍在途的那一条：正常派发、失败重试、恢复未完成任务三条路径落在
     * 同一个请求标识上（A3 的实时流按"当前在途任务"订阅，不按派发路径订阅）。
     */
    val recoverableRespondTask = latestRespondTasks.lastOrNull { task ->
        currentProvider?.let(task::matchesTutorProvider) == true &&
            task.status.isTutorExecutionPending()
    }

    LaunchedEffect(
        executablePlanProvider?.providerId,
        observedTask,
        tasksObserved,
    ) {
        if (
            tutorAutoStartsFirstTurn(
                // 进入即自动开首轮：讲题入口有题可讲（`autoStartFirstTurn`）；智能体栏没有这一句，
                // 等学生先说一句。会话已结束时也绝不自动再开一轮。
                autoStartFirstTurn = surface.autoStartFirstTurn,
                conversationEnabled = conversationEnabled,
                // 任务流还没发过首帧时 `observedTask == null` 不代表"没有任务"，见判据的注释。
                tasksObserved = tasksObserved,
                hasObservedTask = observedTask != null,
                provider = executablePlanProvider,
            )
        ) {
            executeTurn(1, null, emptyList(), emptyList())
        }
    }

    // A3：本会话当前**在途的那一轮**（计划与回应一视同仁）。订阅键 = 会话 id + 在途请求 id，
    // 所以"计划阶段没有流、只有回应轮有"这件事从结构上不可能再发生。
    val activeLiveTurn = activeTutorLiveTurn(
        conversationKey = TutorConversationIds.captured(question.sessionId),
        tasks = persistedTasks + persistedRespondTasks,
    )
    val liveTurn = rememberTutorLiveTurn(modelTasks, activeLiveTurn)
    val liveStatusText = (observedTask ?: recoverableRespondTask)?.tutorLiveStatusText()
    /**
     * 生成中的「停止」（A2）：讲题侧此前没有这个出口——学生想让一段正在生成的回复停下来，
     * 唯一的做法是离开页面。停的是**界面此刻正在渲染的那一轮**（在途实时流的那一条键），
     * 不是从台账反推出来的（重试之后反推会指向旧那一轮）。
     */
    val liveStopAction: (() -> Unit)? = activeLiveTurn?.let { active ->
        {
            viewModel.onStopTutoringTurn(
                requestId = active.requestId,
                conversationId = TutorConversationIds.captured(question.sessionId),
                // 停止补的那条助手行回复的是这一轮的学生行：消息 id 由请求 id 派生
                // （与写入侧 `TutorRespondCommands.recordStudentTurnIfNeeded` 同一派生式）。
                replyToMessageId = tutorStudentMessageId(active.requestId),
            )
        }
    }

    /**
     * 还没有讲解时**唯一的出口**：把这一页的首轮讲题跑起来。
     *
     * 拍照入口进入即自动开首轮（`autoStartFirstTurn`），错题讲题入口不自动开——但"不自动"
     * 不等于"没法开"：那一页的首轮由学生**显式**触发（这道题是他自己挑的，页面不替他发问），
     * 触发点就是输入区里这个出口。两条入口同一个动作、同一条派发路径，不新增第二条开轮方式。
     */
    fun startFirstTutorTurn() {
        executeTurn(1, null, emptyList(), emptyList())
    }

    if (observedTask == null) {
        // 讲解还没有出结果（模型未就绪 / 正在准备 / 准备失败）：输入框**不消失**（A5），
        // 在途的实时文本照常渲染（A3）——讲的是什么、进展到哪一步，学生都看得见。
        // 输入区用**同一个** [TutorSessionInputs]（与出结果那一帧同源）：这一帧此前自己拼了
        // 一个精简的输入框，于是加号（附图 / 「从错题库选择」）、附件区与出口回调在这里全部
        // 缺失——`theLibraryPickerEntryReachesTheSessionPanelOnTheSavedMistakePage` 钉的正是
        // 这件事（错题讲题页不自动开轮，所以它长期停在这一帧上）。
        TutorConversationScreen(
            config = surface,
            composer = {
                TutorSessionInputs(
                    state = state,
                    viewModel = viewModel,
                    surface = surface,
                    composerBlock = TutorComposerAvailability(
                        providerReady = respondAgentAuthorized,
                        providerLoadFailed = state.providerLoadFailed,
                        // 这一轮还没有可讲的讲解：未出结果 / 已经失败两种，各自有自己的说法。
                        planReady = false,
                        planFailed = executablePlanProvider != null,
                    ).block(),
                    chatSending = state.sending,
                    sessionImageEnabled = sessionImageEnabled,
                    attachedQuestionReader = attachedQuestionReader,
                    catalogEntries = catalogEntries,
                    // 发送在这一帧被上面的 block 挡住（还没有可回复的那一轮）：真正的出口是
                    // 「重试 / 重新生成这一轮」那一颗，它走的就是 [startFirstTutorTurn]。
                    onSendMessage = {},
                    onRetryTurn = ::startFirstTutorTurn,
                    onOpenModelSettings = onOpenModelSettings,
                )
            },
            attachments = {
                TutorSessionAttachmentArea(
                    state = state,
                    viewModel = viewModel,
                    surface = surface,
                    chatSending = state.sending,
                )
            },
            liveTurn = liveTurn.takeIf { activeLiveTurn != null },
            liveStatusText = liveStatusText,
            liveTurnStopAction = liveStopAction,
            liveTurnStopTestTag = "tutor_session_live_stop",
            autoScrollVersion = listOf(
                question.sessionId,
                question.revisionNumber,
                currentProvider?.providerConfigurationVersion,
                state.providerLoadFailed,
            ),
            modifier = modifier,
        ) {
            item("tutor_model_entry") {
                when {
                    currentProvider == null -> TutorModelStatusCard(
                        title = if (state.providerLoadFailed) "暂时没准备好" else "正在准备这道题",
                        detail = if (state.providerLoadFailed) {
                            "题目已经保存，检查设置后可以继续。"
                        } else {
                            "请稍候。"
                        },
                        actionLabel = if (state.providerLoadFailed) "检查设置" else null,
                        onAction = onOpenModelSettings,
                    )

                    executablePlanProvider == null -> TutorModelStatusCard(
                        title = "需要先连接大模型",
                        detail = "题目已经保存，配置完成后可以从这里继续。",
                        actionLabel = "去设置",
                        onAction = onOpenModelSettings,
                    )

                    else -> TutorModelStatusCard(
                        title = "正在准备这道题",
                        detail = "正在整理讲解，请稍候。",
                    )
                }
            }
        }
        return
    }

    val currentInput = observedTask.request.input as TutorPlanInput
    val currentResponse = conversationProjection.responsesByTurn[
        TutorTurnKey(currentInput.cycleOrdinal, currentInput.turnOrdinal)
    ]
    val currentHistory = currentCycleResponses.toContiguousTutorHistory()
    val nextTurnExists = currentCycleTasks.any { task ->
        (task.request.input as? TutorPlanInput)?.turnOrdinal == currentHistory.size + 1
    }
    val currentPlanOutput = observedTask.output as? TutorPlanOutput
    val chatSending = state.sending || latestRespondTasks.any { task ->
        currentProvider?.let(task::matchesTutorProvider) == true &&
            task.status.isTutorExecutionPending()
    }
    /**
     * 本轮候选菜单（派发前组好）：学生本轮显式添加的题 + 上一轮绑定的题 + 本地文本检索前 N 条。
     *
     * 前两条是身份（学生的动作 / 已校验的绑定），第三条是检索（只进菜单、不构成绑定）。
     * 检索是 suspend 的，所以在草稿变化时预先算好放进状态：`execute` 是点击即发的非 suspend
     * 路径，按下时现算会引入一次可见等待——或者更糟，按钮先亮后发。
     */
    val previousBoundQuestion = remember(persistedRespondTasks, question.sessionId) {
        previousBoundRoundQuestion(persistedRespondTasks)
    }
    var retrievedCandidates by remember(question.sessionId) {
        mutableStateOf(emptyList<RelatedProblemCandidate>())
    }
    LaunchedEffect(
        state.draft,
        question.sessionId,
        question.revisionNumber,
        catalogEntries,
        roundQuestionRetriever,
    ) {
        val retriever = roundQuestionRetriever
        if (retriever == null || state.draft.isBlank()) {
            retrievedCandidates = emptyList()
            return@LaunchedEffect
        }
        retrievedCandidates = runCatching {
            retriever.retrieve(
                catalog = catalogEntries,
                studentMessage = state.draft,
                excluded = listOfNotNull(
                    previousBoundQuestion,
                    state.pendingAttachedQuestion?.toCandidate(),
                ),
                limit = TutorRoundQuestionBindingPolicy.MAX_CANDIDATES,
            )
        }.getOrDefault(emptyList())
    }
    val boundQuestionCandidates = remember(
        previousBoundQuestion,
        retrievedCandidates,
        state.pendingAttachedQuestion,
    ) {
        TutorRoundQuestionBindingPolicy.assembleCandidates(
            explicitlyAdded = listOfNotNull(state.pendingAttachedQuestion?.toCandidate()),
            previouslyBound = listOfNotNull(previousBoundQuestion),
            retrieved = retrievedCandidates,
        )
    }

    val respondCommands = remember(question.sessionId) {
        TutorRespondCommands(
            scope = scope,
            sink = TutorRespondSink(
                currentProvider = { currentProvider },
                question = { question },
                profile = { profile },
                clock = clock,
                chatSubmitPending = { state.sending },
                setChatSubmitPending = viewModel::onSendPending,
                tutorSendState = { state.sendState },
                setTutorSendState = viewModel::onTutorSendState,
                setChatStartError = viewModel::onSendError,
                setLocallyStartedRespondRequestId = viewModel::onLocallyStartedRequestId,
                setChatDraft = viewModel::onDraftChange,
                currentPlanOutput = { currentPlanOutput },
                currentResponse = { currentResponse },
                currentInput = { currentInput },
                observedTask = { observedTask },
                tutorRespondTasks = { tutorRespondTasks },
                // 与上面一行是两个口径：上面是"这道题聊过什么"，这里是"这个会话走到第几轮"。
                // 轮次号必须按会话分配，否则同一会话换题会撞 model_task/tutor_message 的唯一槽。
                sessionRespondTasks = { persistedRespondTasks },
                answerExposureKeys = { answerExposureKeys },
                chatSending = { chatSending },
                modelTasks = modelTasks,
                conversations = conversations,
                // 提示词历史从消息流装配（K1a）。读的是 backing state 而不是组合期的快照：
                // 这个 lambda 由 remember 捕获一次，组合期的列表会永远停在第一帧。
                sessionMessages = { state.messages },
                // 回合收尾（A4）：这一轮成功之后，模型申请过的本地动作挂成一张落库的卡。
                // 拍照入口把 captureSessionId 交进这条链（见 TutorConversationWiring），
                // 那张卡执行的就是"把这次拍照的草稿存进错题本"。
                onTurnRecorded = { request, _ ->
                    viewModel.onTutoringTurnSucceeded(
                        requestId = request.requestId,
                        conversationId = TutorConversationIds.captured(question.sessionId),
                        // 学生这条消息附带的图片（规范资产 id）——存题的第二条落点靠它。
                        attachedImageAssetIds = (request.input as? TutorRespondInput)
                            ?.studentImageAssetRefs
                            .orEmpty(),
                    )
                },
            ),
        )
    }

    fun collectTutorRespondRequest(
        request: ModelTaskRequest,
        clearDraftOnPersist: Boolean,
        allowExternalEnvelopeForLocalRecovery: Boolean = false,
        isRetry: Boolean = false,
    ) {
        respondCommands.collect(
            request = request,
            clearDraftOnPersist = clearDraftOnPersist,
            allowExternalEnvelopeForLocalRecovery = allowExternalEnvelopeForLocalRecovery,
            isRetry = isRetry,
        )
    }

    fun executeTutorResponse(
        message: String,
        requestedMove: TutorMoveType? = null,
        clearDraftOnPersist: Boolean = false,
        studentImageAssetIds: List<String> = emptyList(),
    ) {
        // 显式添加的题在派发那一刻带出（之后清空，与图片同一生命周期）。
        val attached = state.pendingAttachedQuestion
        val dispatched = respondCommands.execute(
            message = message,
            requestedMove = requestedMove,
            clearDraftOnPersist = clearDraftOnPersist,
            studentImageAssetIds = studentImageAssetIds,
            boundQuestionCandidates = boundQuestionCandidates,
            // 本轮请求侧已知的题锚：显式添加优先，其次上一轮绑定延续。写工具门控在模型没有
            // 复述题锚时回退到它：原生 tool_calls 路由的整轮信封无处放声明，复述只能落在
            // 每次调用的 arguments 里，而复述不是必然的。
            knownRoundQuestion = attached?.toCandidate() ?: previousBoundQuestion,
            attachedQuestion = attached,
        )
        // 附加题随本次派发带出后清空（与图片同一生命周期）——**只有真的派出去了才清**：
        // 被拒的一轮没带走任何东西，清了就是把学生刚挑的题静默丢掉（他只能重新去挑一遍）。
        if (dispatched) viewModel.onAttachedQuestionDispatched()
    }

    /**
     * 发一条学生会话消息，带上已选好的附图。
     *
     * 附图在**发送时**才登记成规范资产（选中时只是本地 uri），这与大厅同口径：登记要读图、
     * 解码、算哈希，不该在选图那一刻阻塞界面；而一旦登记失败就如实说图片读不出来，
     * 不把消息发出去——否则模型会收到一条没有图的"看图"请求。
     */
    fun submitTutorResponse(message: String) {
        val selected = state.pendingImages
        val intake = imageIntake
        if (selected.isEmpty() || intake == null) {
            executeTutorResponse(message, clearDraftOnPersist = true)
            return
        }
        if (state.provider?.supportsImageInput != true) {
            viewModel.onSendError(tutorRespondImageUnsupportedError())
            return
        }
        scope.launch {
            val assetIds = runCatching {
                selected
                    .map { pending ->
                        intake.registerImage(pending.localUri, System.currentTimeMillis())
                    }
                    // 资产库按内容寻址：同一张照片被选两次会得到同一个 assetId，而请求契约
                    // 要求互不重复，所以按 assetId 去重而不是让重复选择顶掉整条消息。
                    .distinctBy { image -> image.assetId }
                    .map { image -> image.assetId }
            }.getOrElse { failure ->
                android.util.Log.w("TutorSession", "Attached image could not be registered", failure)
                viewModel.onSendError(tutorRespondImageIntakeError())
                return@launch
            }
            viewModel.onPendingImagesDispatched()
            executeTutorResponse(
                message = message,
                clearDraftOnPersist = true,
                studentImageAssetIds = assetIds,
            )
        }
    }

    fun retryTutorResponse(task: ModelTaskSnapshot) {
        respondCommands.retry(task)
    }


    LaunchedEffect(
        recoverableRespondTask?.request?.requestId,
        respondAgentAuthorized,
    ) {
        if (respondAgentAuthorized) {
            recoverableRespondTask
                ?.takeUnless { it.request.requestId == state.locallyStartedRequestId }
                ?.let { task ->
                    // 同上：恢复路径也要把这一轮的正文落进消息行（K1a 之后这是唯一的渲染源）。
                    resumePersistedTutorTurn(
                        modelTasks = modelTasks,
                        conversations = conversations,
                        request = task.request,
                    )
                }
        }
    }

    fun revealCurrentSolution(afterPreviewed: () -> Unit = {}) {
        if (state.interactionBusy) return
        if (currentResponse?.solutionRevealed != true) {
            val previewKey = observedTask.toPlanSolutionPreviewKey() ?: return
            planSolutionPreviewKeys = planSolutionPreviewKeys + previewKey
        }
        viewModel.onInteractionError(null)
        afterPreviewed()
    }
    LaunchedEffect(
        question.sessionId,
        currentCycle,
        currentHistory,
        currentInput.priorConversationMemory,
        currentInput.priorCycleStudentMessages,
        nextTurnExists,
        executablePlanProvider?.providerConfigurationVersion,
    ) {
        if (
            currentHistory.isNotEmpty() &&
            currentHistory.size < TutorPlanInput.MAX_TURNS &&
            !nextTurnExists
        ) {
            executeTurn(
                currentInput.cycleOrdinal,
                currentInput.priorConversationMemory,
                currentInput.priorCycleStudentMessages,
                currentHistory,
            )
        }
    }
    fun retryCurrentPlan() {
        if (executablePlanProvider == null) {
            onOpenModelSettings()
        } else {
            executeTurn(
                currentInput.cycleOrdinal,
                currentInput.priorConversationMemory,
                currentInput.priorCycleStudentMessages,
                currentInput.priorTurns,
            )
        }
    }

    val interactionCommands = remember(question.sessionId) {
        TutorInteractionCommands(
            scope = scope,
            sink = TutorInteractionSink(
                currentPlanOutput = { currentPlanOutput },
                currentInput = { currentInput },
                question = { question },
                clock = clock,
                interactionBusy = { state.interactionBusy },
                setInteractionBusy = viewModel::onInteractionBusy,
                setInteractionError = viewModel::onInteractionError,
                hasExecutableProvider = { executablePlanProvider != null },
                openModelSettings = onOpenModelSettings,
                currentCycleResponses = { currentCycleResponses },
                tutorResponses = { tutorResponses },
                tutorRespondTasks = { tutorRespondTasks },
                answerExposureKeys = { answerExposureKeys },
                currentCycle = { currentCycle },
                executeTurn = { cycle, memory, messages, turns ->
                    executeTurn(cycle, memory, messages, turns)
                },
                interactions = interactions,
            ),
        )
    }

    fun submitCurrentChoice(choiceId: String) {
        interactionCommands.submitChoice(choiceId)
    }

    fun continueCurrentTurn(requestedMove: TutorMoveType) {
        interactionCommands.continueTurn(requestedMove)
    }

    fun restartCurrentCycle() {
        interactionCommands.restartCycle()
    }

    val conversationListState = rememberLazyListState()
    fun solutionBottomModifier(stableId: String): Modifier = Modifier
        .testTag("tutor_solution_bottom_$stableId")
        .onGloballyPositioned { coordinates ->
            solutionExposureTracker.updateSolutionBottomBounds(
                stableId = stableId,
                bounds = coordinates.boundsInWindow(clipBounds = false),
            )
        }
    val tailId = timeline.lastOrNull()?.stableId
    val autoScrollVersion = timeline.map { timelineItem ->
        when (timelineItem) {
            is TutorConversationTimelineItem.Plan -> listOf(
                timelineItem.stableId,
                timelineItem.task.stateVersion,
                timelineItem.task.status,
            )
            is TutorConversationTimelineItem.ChoiceFeedback -> listOf(
                timelineItem.stableId,
                timelineItem.response.updatedAtEpochMillis,
                timelineItem.response.requestedMove,
                timelineItem.response.solutionRevealed,
            )
            is TutorConversationTimelineItem.Reply -> listOf(
                timelineItem.stableId,
                timelineItem.task.stateVersion,
                timelineItem.task.status,
            )
        }
    }
    // A5：输入区不再有条件——讲解没出结果、讲解失败、模型没配好、会话已结束这四种情况各自
    // 有自己的原因与出口，而输入框永远在。发不出去时「一句人话 + 可用动作」写在输入框上方。
    val composerBlock = TutorComposerAvailability(
        providerReady = respondSupported && respondAgentAuthorized,
        providerLoadFailed = state.providerLoadFailed,
        // 讲解出结果 = 这一轮有可以接着讲的东西；没出结果 / 已经失败各有各的说法。
        planReady = currentPlanOutput != null,
        planFailed = observedTask.status in TERMINAL_FAILURE_STATUSES,
    ).block()
    TutorConversationScreen(
        config = surface,
        composer = {
            TutorSessionInputs(
                state = state,
                viewModel = viewModel,
                surface = surface,
                composerBlock = composerBlock,
                chatSending = chatSending,
                sessionImageEnabled = sessionImageEnabled,
                attachedQuestionReader = attachedQuestionReader,
                catalogEntries = catalogEntries,
                // 发这条消息：登记附图（选中时只是本地 uri）→ 派发这一轮。
                onSendMessage = { submitTutorResponse(state.draft) },
                onRetryTurn = ::retryCurrentPlan,
                onOpenModelSettings = onOpenModelSettings,
            )
        },
        attachments = {
            TutorSessionAttachmentArea(
                state = state,
                viewModel = viewModel,
                surface = surface,
                chatSending = chatSending,
            )
        },
        autoScrollVersion = listOf(
            autoScrollVersion,
            respondAgentAuthorized,
            state.error,
            state.pendingRequestCards,
            state.pendingRequestDetail,
            liveTurn,
        ),
        forceFollowToken = state.locallyStartedRequestId,
        blockAutoFollowToken = solutionExposureTracker.blockAutoFollowToken,
        modifier = modifier,
        listState = conversationListState,
        listViewportModifier = Modifier.onGloballyPositioned { coordinates ->
            solutionExposureTracker.updateViewportBounds(
                coordinates.boundsInWindow(clipBounds = false),
            )
        },
        // 同一套在途区（思考卡 / 逐 token 回答 / 工具进度），与智能体栏共用一条实时流（A3）。
        liveTurn = liveTurn.takeIf { activeLiveTurn != null },
        liveStatusText = liveStatusText,
        // 「停止」（A2）与智能体栏同一个出口形状：非 null 就在这一轮底下露出来。
        liveTurnStopAction = liveStopAction,
        liveTurnStopTestTag = "tutor_session_live_stop",
    ) {
        items(timeline, key = TutorConversationTimelineItem::stableId) { timelineItem ->
            val isTail = timelineItem.stableId == tailId
            when (timelineItem) {
                is TutorConversationTimelineItem.Plan -> {
                    val taskInput = timelineItem.task.request.input as TutorPlanInput
                    val response = conversationProjection.responsesByTurn[
                        TutorTurnKey(taskInput.cycleOrdinal, taskInput.turnOrdinal)
                    ]
                    val isCurrentTurn = taskInput.cycleOrdinal == currentInput.cycleOrdinal &&
                        taskInput.turnOrdinal == currentInput.turnOrdinal
                    val executionMatches = currentProvider?.let(
                        timelineItem.task::matchesTutorProvider,
                    ) == true
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TutorTaskContent(
                        task = timelineItem.task,
                        response = response,
                        // 正文从消息行来（K1a）；旧行没有消息行时才回落账本。
                        openingMarkdown = timelineItem.bodyMarkdown,
                        // B1：这一轮查阅了什么的痕迹（同一行消息上，一起读、一起渲染）。
                        toolTraceJson = timelineItem.message?.toolTraceJson,
                        solutionRevealPreviewed = timelineItem.task.toPlanSolutionPreviewKey()
                            ?.let { it in planSolutionPreviewKeys } == true,
                        awaitingContinuation = false,
                        interactionEnabled = isTail && isCurrentTurn,
                        executionMatchesCurrentProvider = executionMatches,
                        splitChoiceFeedback = true,
                        interactionBusy = state.interactionBusy,
                        interactionError = state.interactionError.takeIf { isTail && isCurrentTurn },
                        onRetry = ::retryCurrentPlan,
                        onSubmitChoice = ::submitCurrentChoice,
                        onRequestHint = if (
                            respondSupported && respondAgentAuthorized && !chatSending
                        ) {
                            {
                                executeTutorResponse(
                                    message = "我不确定，请给我一点提示",
                                )
                            }
                        } else {
                            null
                        },
                        onContinue = ::continueCurrentTurn,
                        onRevealSolution = { revealCurrentSolution() },
                        onRestartCycle = ::restartCurrentCycle,
                        onOpenModelSettings = onOpenModelSettings,
                        solutionBottomModifier = solutionBottomModifier(timelineItem.stableId),
                    )
                    }
                }

                is TutorConversationTimelineItem.ChoiceFeedback -> {
                    val response = timelineItem.response
                    val output = timelineItem.planTask?.output as? TutorPlanOutput
                    val isCurrentTurn = response.cycleOrdinal == currentInput.cycleOrdinal &&
                        response.turnOrdinal == currentInput.turnOrdinal
                    if (output != null) {
                        TutorChoiceFeedbackContent(
                            output = output,
                            response = response,
                            solutionRevealPreviewed = timelineItem.planTask
                                .toPlanSolutionPreviewKey()
                                ?.let { it in planSolutionPreviewKeys } == true,
                            interactionEnabled = isTail && isCurrentTurn,
                            interactionBusy = state.interactionBusy,
                            interactionError = state.interactionError.takeIf { isTail && isCurrentTurn },
                            onContinue = ::continueCurrentTurn,
                            onRevealSolution = { revealCurrentSolution() },
                            onRestartCycle = ::restartCurrentCycle,
                            solutionBottomModifier = solutionBottomModifier(timelineItem.stableId),
                        )
                    } else {
                        TutorStoredChoiceFeedback(response)
                    }
                }

                is TutorConversationTimelineItem.Reply -> {
                    val executionMatches = currentProvider?.let(
                        timelineItem.task::matchesTutorProvider,
                    ) == true
                    val taskAllowsInteraction = timelineItem.task.status ==
                        ModelTaskStatus.SUCCEEDED || timelineItem.task.canRetryTutorResponse()
                    val opensLocalSettings =
                        timelineItem.task.failure?.code?.requiresModelSettings() == true
                    val recoveryEnabled = isTail && executionMatches &&
                        !chatSending && !state.interactionBusy &&
                        (opensLocalSettings || respondAgentAuthorized)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 这一轮讲的是哪一道（多题会话里必备）：只在本地确实知道这一轮的题时出现
                    // （附加题 / 模型声明核过的绑定），会话题自己的轮次不挂，避免噪声。
                    timelineItem.questionTitle?.let { roundQuestionTitle ->
                        Text(
                            text = "本题：$roundQuestionTitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSecondary,
                            maxLines = 1,
                            modifier = Modifier.testTag("tutor_reply_question_badge"),
                        )
                    }
                    TutorChatExchange(
                        task = timelineItem.task,
                        // 气泡与正文的唯一来源是消息行（K1a）；旧行由时间线项回落到账本/派发原文。
                        studentBodyMarkdown = timelineItem.studentBodyMarkdown,
                        assistantBodyMarkdown = timelineItem.bodyMarkdown,
                        assistantThinkingMarkdown = timelineItem.thinkingMarkdown,
                        // B1：这一轮查阅了什么的痕迹（同一行消息上）。
                        assistantToolTraceJson = timelineItem.message?.toolTraceJson,
                        awaitingContinuation = !respondAgentAuthorized &&
                            timelineItem.task.status.isTutorExecutionPending(),
                        interactionEnabled = isTail && taskAllowsInteraction &&
                            executionMatches && respondAgentAuthorized &&
                            !chatSending && !state.interactionBusy,
                        recoveryEnabled = recoveryEnabled,
                        executionMatchesCurrentProvider = executionMatches,
                        onRetry = { retryTutorResponse(timelineItem.task) },
                        onOpenModelSettings = onOpenModelSettings,
                        attachedImageResolver = attachedImageResolver,
                        studentImageIntake = imageIntake,
                        onMove = { move ->
                            executeTutorResponse(
                                message = move.label,
                                requestedMove = move.type,
                            )
                        },
                        onRevealSolution = { move ->
                            revealCurrentSolution {
                                executeTutorResponse(
                                    message = move.label,
                                    requestedMove = TutorMoveType.REVEAL_SOLUTION,
                                )
                            }
                        },
                        assistantBottomModifier = solutionBottomModifier(timelineItem.stableId),
                    )
                    }
                }
            }
        }
    }
}
