package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.MAX_TUTOR_MESSAGE_IMAGES
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.SaveTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionContext
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionOutcomeRecord
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.SetTutorInteractionModeCommand
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.domain.defaultTutorInteractionMode
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.AppFailure
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.Retryability
import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.tutorScaffoldDirective
import com.tingyun.smartmistakebook.core.model.decodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorRequestedLocalCapability
import com.tingyun.smartmistakebook.core.model.appFailure
import com.tingyun.smartmistakebook.core.model.recoverableByResending
import com.tingyun.smartmistakebook.core.model.requiresModelSettings
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 智能体交互面的**对话状态唯一持有者**（C2）。
 *
 * 这个类里的东西此前散在组合里：14 个裸 `remember` 状态（其中附图与相机目标是跨重建就会
 * 丢的那种）、一条会话订阅、一条任务订阅、一条实时流订阅，以及"发一条消息"这条路径的三份
 * 复制（新发送 / 失败重发 / 继续未完成）。收在这里之后：
 *
 * - **进入即新开**（A1）：默认没有任何会话 id；只有显式给了 `initialConversationId`
 *   （历史列表点回来）才恢复。进入一个页面**永远不会**认领上次那条会话。
 * - **一条消息订阅**：会话行 + 消息流 + 轮次事实都从 [TutorConversationRepository.observeConversation]
 *   的同一个投影来（见 `TutorConversationSnapshot`），不再有第二条订阅去别处取对话事实。
 * - **会话区显式**（[TutorConversationAreas.AGENT]，本阶段恒为智能体栏）：会话属于哪一栏
 *   由创建方写死，不从题面/入口反推。
 * - **草稿与附件跨进程保留**（B3）：走 [SavedStateHandle]，发送成功才清。
 * - **发送状态**由 [TutorTurnSendStateMachine] 归约（B5）：没有界面自己的派遣预算，
 *   新学生消息一律开新回合，被取代回合的迟到结果一律忽略。
 *
 * 识别的边界：讲题区（拍照会话 / 错题讲题）的 18 个状态这一轮还没搬进来（下一个工作流做），
 * 所以这里现在服务的是大厅这一个接入点；两处共用同一套日历/状态语义，搬迁时不再改口径。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TutorConversationViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val conversations: TutorConversationRepository,
    private val modelTasks: ModelTaskRepository,
    private val imageIntake: LobbyMessageImageIntake? = null,
    /** 显式恢复用的会话 id（从历史列表点回来）；null/空白 = 本轮新开。 */
    initialConversationId: String? = null,
    /**
     * 会话区（K1 的判别列）：这条会话属于哪一栏由入口显式给（交互面的 `TutorSurfaceConfig.area`），
     * 不从题面/入口反推。本阶段三个入口都是智能体栏；复习栏两个入口在阶段 5 各给一个区。
     */
    val conversationArea: String = TutorConversationAreas.AGENT,
    /**
     * 确认卡的落库端口（A4 / 插眼 5）：null = 这个入口不接确认卡（测试替身与无库界面）。
     * 接上之后：模型申请的本地动作会挂出一张**落库**的卡，学生点了才执行，裁决回喂下一轮。
     */
    private val pendingRequests: AgentPendingRequestRepository? = null,
    /** 确认卡三条执行路径的落点；装配处注入（见 [TutorLocalActionLandings]）。 */
    private val localActionLandings: TutorLocalActionLandings = TutorLocalActionLandings(),
    /**
     * 这条会话锚着的**拍照会话 id**（讲题侧 EPHEMERAL_DRAFT 锚）：有它，确认卡的执行目标就是
     * "把这一轮的草稿存进错题本"（执行路径 ①）；没有它，卡落到附图 / 错题本（②③）。
     */
    private val anchoredCaptureSessionId: String? = null,
    /**
     * 这条会话锚着的**那一道已在错题本里的题**（A4 执行路径 ②）：错题讲题页给它（这一页讲的
     * 就是这一条错题）。有它，模型申请"存 / 打开这一轮这道题"时那张卡的目标就是它；没有它，
     * 这类申请在本页没有可执行目标、挂不出卡（本地不伪造一张点了无处落地的卡）。
     */
    private val anchoredLibraryProblemId: String? = null,
    /**
     * 加号菜单「从错题库选择」选中后的题面读取器：选题与恢复都在这里读（讲题侧）。
     * null = 这个入口不提供这项（菜单项也不出现）。
     */
    private val attachedQuestionReader: TutorAttachedQuestionReader? = null,
    /** 选题弹层的目录条目（与 [attachedQuestionReader] 配对：键 → 条目 → 题面）。 */
    private val catalogEntries: List<StudyCatalogEntry> = emptyList(),
) : ViewModel() {

    private val restored = savedStateHandle.restoredTutorConversationState()

    private val _uiState = MutableStateFlow(
        TutorConversationUiState(
            conversationId = restored.conversationId
                .takeIf(String::isNotBlank)
                ?: initialConversationId?.takeIf(String::isNotBlank).orEmpty(),
            draft = restored.draft,
            pendingImages = restored.pendingImageUris.map(::PendingMessageImage),
            pendingCameraImageUri = restored.pendingCameraImageUri,
            attachMenuOpen = restored.attachMenuOpen,
            mistakePickerOpen = restored.mistakePickerOpen,
            attachReadFailed = restored.attachReadFailed,
            lastFailedMessage = restored.lastFailedMessage,
            pendingAttachedQuestionEntryId = restored.pendingAttachedQuestionEntryId,
            // 空会话（还没落库）按**会话区默认**给模式：智能体栏正常、复习栏引导。
            interactionMode = defaultTutorInteractionMode(conversationArea),
        ),
    )
    val uiState: StateFlow<TutorConversationUiState> = _uiState.asStateFlow()

    private var draftPersistJob: Job? = null

    // 下面两个协作件必须在 `init` **之前**声明：`init` 里的 `viewModelScope.launch` 在
    // `Dispatchers.Main.immediate` 上会**同步**执行到它的第一个挂起点，而空会话那一帧
    // （`flowOf(null)` / `flowOf(emptyList())`）不挂起——同步路径上就会读到还没初始化的 null
    // （真机上直接 NPE，JVM 单测的测试调度器不同步执行所以照不出来）。改回来 = 重新引入该崩溃。

    // ---- 确认卡（A4 / 插眼 5） ----

    /**
     * 确认卡的四条流程（挂起 / 观察 / 重建 / 裁决）交给 [TutorPendingRequestCoordinator]：
     * 它只通过回调读写这里的状态，状态仍只有这一个持有者。
     */
    private val pendingRequestCoordinator = TutorPendingRequestCoordinator(
        conversationArea = conversationArea,
        requests = pendingRequests,
        commands = pendingRequests?.let { requests ->
            TutorPendingRequestCommands(
                conversationArea = conversationArea,
                requests = requests,
                landings = localActionLandings,
            )
        },
        currentState = { _uiState.value },
        updateState = ::updateState,
        applySendState = ::applySendState,
        anchoredCaptureSessionId = anchoredCaptureSessionId,
        anchoredLibraryProblemId = anchoredLibraryProblemId,
    )

    /** 在途一轮的实时文本订阅（A3）：两个可变字段跟着它走，见 `TutorLiveTurnSubscription`。 */
    private val liveTurnSubscription = TutorLiveTurnSubscription(
        scope = viewModelScope,
        modelTasks = modelTasks,
        onTurn = { turn -> updateState { state -> state.copy(liveTurn = turn) } },
    )

    init {
        viewModelScope.launch {
            _uiState.map { it.conversationId }
                .distinctUntilChanged()
                .flatMapLatest { conversationId ->
                    if (conversationId.isBlank()) {
                        flowOf(null)
                    } else {
                        conversations.observeConversation(conversationId)
                    }
                }
                .collect { snapshot ->
                    updateState { state ->
                        state.copy(
                            conversation = snapshot?.conversation,
                            messages = snapshot?.messages.orEmpty(),
                            // 模式的行是权威（切换落库之后以它为准）；没有行时按会话区默认。
                            interactionMode = snapshot?.conversation?.interactionMode
                                ?: defaultTutorInteractionMode(conversationArea),
                        )
                    }
                }
        }
        viewModelScope.launch {
            _uiState.map { it.conversationId }
                .distinctUntilChanged()
                .flatMapLatest { conversationId ->
                    if (conversationId.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        modelTasks.observeRecentBySubject(
                            conversationId,
                            ModelTaskKind.TUTOR_LOBBY,
                            MAX_PERSISTED_TASKS,
                        )
                    }
                }
                .collect { persisted ->
                    val tasks = tutorLobbyConversationTasks(persisted)
                    updateState { state -> state.copy(tasks = tasks) }
                    observeLiveTurn(_uiState.value.conversationId, tasks)
                }
        }
        viewModelScope.launch { reloadDraftFromConversationRow() }
        viewModelScope.launch { restorePendingAttachedQuestion() }
        viewModelScope.launch { pendingRequestCoordinator.observeCards() }
        viewModelScope.launch { pendingRequestCoordinator.resumeAfterRestart() }
    }

    /**
     * 「停止」这一条路（A2）交给 [TutorRoundStopCommands]：在途回合登记、取消、补「已停止」行。
     * 它同样只通过回调读写这里的状态。
     */
    private val roundStopCommands = TutorRoundStopCommands(
        modelTasks = modelTasks,
        conversations = conversations,
        currentState = { _uiState.value },
        updateState = ::updateState,
        onRoundStopped = {
            updateState { current -> current.copy(sending = false, resumingRequestId = null) }
            // 停止之后这一轮不再是"当前那一轮"：它迟到的结果一律忽略（B5）。
            reduceSend(TutorSendAction.Reset)
        },
        onStopFailed = {
            // 停不下来时如实说：这一轮的终态没落成，学生仍然可以重试。
            updateState { current -> current.copy(error = tutorSendRecoveryFailedError()) }
        },
    )

    /**
     * 发送这一条路（新发送 / 失败重发 / 继续未完成 / 试一句）交给 [TutorLobbySendCommands]：
     * 它只通过回调读写这里的状态，状态仍只有这一个持有者。
     */
    private val sendCommands = TutorLobbySendCommands(
        conversations = conversations,
        modelTasks = modelTasks,
        imageIntake = imageIntake,
        conversationArea = conversationArea,
        pendingRequestCoordinator = pendingRequestCoordinator,
        roundStopCommands = roundStopCommands,
        scope = viewModelScope,
        currentState = { _uiState.value },
        updateState = ::updateState,
        reduceSend = ::reduceSend,
    )

    /** 生成中的「停止」出口（A2）：取消这一轮，并把终态与那一行一起收好。 */
    fun onStopGenerating() {
        viewModelScope.launch { roundStopCommands.stop() }
    }

    /**
     * 讲题侧的「停止」（A2）：取消这一轮正在生成的派发，并在会话流里补一条灰字「已停止」。
     *
     * 与大堂共用同一条停止路径（[TutorRoundStopCommands]）：登记 → 取消 → 补行 → 放开输入区。
     * 讲题侧的在途轮次由调用方给出——它就是**界面此刻正在渲染的那一条**（[TutorLiveTurn] 的
     * 键）；界面层不该从任务台账反推"该停哪一条"（重试之后反推出来的会指向旧那一轮）。
     */
    fun onStopTutoringTurn(
        requestId: String,
        conversationId: String,
        replyToMessageId: String?,
    ) {
        roundStopCommands.beginRound(
            requestId = requestId,
            conversationId = conversationId,
            replyToMessageId = replyToMessageId,
            logicalOperationId = requestId,
        )
        viewModelScope.launch { roundStopCommands.stop() }
    }

    /**
     * 「试一句」（S5）：空态能力目录里点了一句示例 → **它作为首条消息直接发出去**。
     *
     * 与"填进输入框等你再点发送"的区别就是要消灭的那次多余动作：空态里学生还不知道怎么开始，
     * 让他再点一次发送等于多一道门。草稿**不动**（那是他自己的东西）。
     */
    fun onExampleMessage(text: String) = sendCommands.onExampleMessage(text)

    /**
     * 切换交互模式（D-Q9）：学生自己选的那一下。
     *
     * 立刻改本地状态（学生看得见自己切了），再落库；**空会话不落库**（K1b）——那时模式随第一条
     * 消息的建行一起落（[startMessage] 把当前模式交给创建命令）。落库失败不回滚界面：模式是本地
     * 事实，行是它的耐久副本，下一次写入会把它带上。
     */
    fun onSwitchInteractionMode() {
        val next = when (_uiState.value.interactionMode) {
            TutorInteractionMode.NORMAL -> TutorInteractionMode.GUIDED
            TutorInteractionMode.GUIDED -> TutorInteractionMode.NORMAL
        }
        updateState { state -> state.copy(interactionMode = next) }
        val conversationId = _uiState.value.conversationId
        if (conversationId.isBlank()) return
        val occurredAt = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                conversations.setInteractionMode(
                    SetTutorInteractionModeCommand(
                        conversationId = conversationId,
                        interactionMode = next,
                        occurredAtEpochMillis = occurredAt,
                    ),
                )
            }
        }
    }

    /** 学生点卡：裁决落终态 → 执行真实路径 → 结果进回喂。 */
    fun onDecidePendingRequest(
        request: AgentPendingRequest,
        decision: AgentPendingRequestDecision,
    ) {
        viewModelScope.launch { pendingRequestCoordinator.decide(request, decision) }
    }

    // ---- 输入与附件（B3：全部跨进程保留） ----

    fun onDraftChange(value: String) {
        val next = value.take(TutorLobbyInput.MAX_STUDENT_MESSAGE_CHARS)
        updateState { state -> state.copy(draft = next) }
        val conversationId = _uiState.value.conversationId
        if (conversationId.isBlank()) return
        draftPersistJob?.cancel()
        draftPersistJob = viewModelScope.launch {
            val occurredAt = System.currentTimeMillis()
            runCatching {
                if (next.isBlank()) {
                    conversations.clearDraft(
                        ClearTutorConversationDraftCommand(
                            conversationId = conversationId,
                            occurredAtEpochMillis = occurredAt,
                        ),
                    )
                } else {
                    conversations.saveDraft(
                        SaveTutorConversationDraftCommand(
                            conversationId = conversationId,
                            draft = next,
                            occurredAtEpochMillis = occurredAt,
                        ),
                    )
                }
            }
        }
    }

    fun onOpenAttachMenu() {
        updateState { state -> state.copy(attachMenuOpen = true) }
    }

    fun onDismissAttachMenu() {
        updateState { state -> state.copy(attachMenuOpen = false) }
    }

    /** 发起拍照：先记下这次要回填的目标 uri，拍完（或没拍成）再清。 */
    fun onCameraLaunching(uri: String) {
        updateState { state -> state.copy(pendingCameraImageUri = uri) }
    }

    fun onCameraResult(saved: Boolean) {
        val uri = _uiState.value.pendingCameraImageUri
        updateState { state -> state.copy(pendingCameraImageUri = null) }
        if (saved && uri != null) addPendingImages(listOf(uri))
    }

    fun onGalleryResult(uris: List<String>) {
        addPendingImages(uris.take(remainingImageSlots()))
    }

    /**
     * 待发附图**已经随着这一次派发带走**（讲题侧：登记成规范资产之后才清）。
     *
     * 只有真的派出去了才清：图片没进资产库时如实报错、附图留着，学生换一张或去掉再发
     * ——静默清掉就等于把他刚选的图丢了。
     */
    fun onPendingImagesDispatched() {
        updateState { state -> state.copy(pendingImages = emptyList()) }
    }

    fun onRemovePendingImage(index: Int) {
        updateState { state ->
            state.copy(pendingImages = state.pendingImages.filterIndexed { i, _ -> i != index })
        }
    }

    fun onOpenMistakePicker() {
        updateState { state -> state.copy(attachMenuOpen = false, mistakePickerOpen = true) }
    }

    fun onDismissMistakePicker() {
        updateState { state -> state.copy(mistakePickerOpen = false) }
    }

    /**
     * 读题面失败（讲题侧：加号 → 从错题库选择）：如实说读不出来，而不是把一条没有题面的
     * "添加"带进请求。
     */
    fun onAttachReadFailed(message: String?) {
        updateState { state -> state.copy(attachReadFailed = message) }
    }

    // ---- 讲题侧本轮附件与结构化交互（C2：状态只有一个持有者，界面不再自持） ----

    /**
     * 学生在选题弹层里选中了一道题（讲题侧的「从错题库选择」）：读回题面 → 成为本轮附件。
     *
     * 读盘在 ViewModel 里（而不是界面里）：它是一次 suspend 读，结果要落进状态，而状态只有这
     * 一个持有者。读不出来如实说（[TutorConversationUiState.attachReadFailed]），不把一条没有
     * 题面的"添加"带进请求。
     */
    fun onPickAttachedQuestion(entry: StudyCatalogEntry) {
        updateState { state -> state.copy(mistakePickerOpen = false, attachReadFailed = null) }
        val reader = attachedQuestionReader ?: return
        viewModelScope.launch {
            val read = runCatching { reader.read(entry) }.getOrNull()
            updateState { state ->
                if (read == null) {
                    state.copy(attachReadFailed = TUTOR_ATTACH_READ_FAILED_MESSAGE)
                } else {
                    state.copy(
                        pendingAttachedQuestion = read,
                        pendingAttachedQuestionEntryId = entry.entryId,
                    )
                }
            }
        }
    }

    /** 移除本轮附件题（题面本身不落库：本轮结束它就没有意义了）。 */
    fun onClearAttachedQuestion() {
        updateState { state ->
            state.copy(pendingAttachedQuestion = null, pendingAttachedQuestionEntryId = null)
        }
    }

    /**
     * 这一轮附件题**已经随着派发带走**：清掉（与附图同一生命周期）。
     *
     * 只有真的派出去了才清：被拒的一轮没带走任何东西，清了就是把学生刚挑的题静默丢掉。
     */
    fun onAttachedQuestionDispatched() {
        updateState { state ->
            state.copy(
                pendingAttachedQuestion = null,
                pendingAttachedQuestionEntryId = null,
                attachReadFailed = null,
            )
        }
    }

    fun onInteractionBusy(busy: Boolean) {
        updateState { state -> state.copy(interactionBusy = busy) }
    }

    fun onInteractionError(message: String?) {
        updateState { state -> state.copy(interactionError = message) }
    }

    /** 讲题侧这一轮的派发在途（与大厅的 `sending` 是同一件事：一条会话只有一个发送状态机）。 */
    fun onSendPending(pending: Boolean) {
        updateState { state -> state.copy(sending = pending) }
    }

    /** 发送路径上的错误（讲题侧的首发失败文案与大厅同一条字段、同一个位置）。 */
    fun onSendError(failure: AppFailure?) {
        updateState { state -> state.copy(error = failure) }
    }

    /** 讲题侧的发送状态机（B5）：与大厅共用同一份状态，一次派发只推进它。 */
    fun onTutorSendState(state: TutorSendState) {
        updateState { current -> current.copy(sendState = state) }
    }

    fun onLocallyStartedRequestId(requestId: String?) {
        updateState { state -> state.copy(locallyStartedRequestId = requestId) }
    }

    /**
     * 讲题侧一轮成功之后（A4）：模型在这一轮申请了本地动作就**挂一张卡**——与大厅同一条通道、
     * 同一张卡、同一个执行器。
     *
     * 工具拼写（`NOTEBOOK_WRITE` 被工具环放进 ask 档、工具环不执行它）在讲题侧留下的信号就是
     * 那一轮的工具痕迹（`awaiting_consent`），所以这里读的是那一条痕迹。拍照入口把
     * `captureSessionId` 带进上下文之后，这张卡执行的就是"把这次拍照的草稿存进错题本"
     * （执行路径 ①）——它此前在生产里不可达（见 `TutorConversationWiring`）。
     */
    fun onTutoringTurnSucceeded(
        requestId: String,
        conversationId: String,
        attachedImageAssetIds: List<String>,
    ) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            pendingRequestCoordinator.suspendRequestedActionsIfAny(
                conversationId = conversationId,
                requestedActions = emptyList(),
                attachedImageAssetIds = attachedImageAssetIds,
                toolTrace = decodeTutorTurnToolTrace(
                    toolTraceJsonFor(modelTasks, requestId),
                ),
            )
        }
    }

    /**
     * 进程死亡后把本轮附件题读回来（保存的是**身份**：题面是重对象，不落 SavedStateHandle）。
     *
     * 读不回来（题被删了 / 目录里没有它 / 读取器没接上）就不带附件——学生看到的是没有那一行
     * 附件，而不是一条没有题面的"添加"被悄悄带进请求。
     */
    private suspend fun restorePendingAttachedQuestion() {
        val entryId = _uiState.value.pendingAttachedQuestionEntryId ?: return
        if (_uiState.value.pendingAttachedQuestion != null) return
        val reader = attachedQuestionReader ?: return
        val entry = catalogEntries.firstOrNull { candidate -> candidate.entryId == entryId } ?: return
        val read = runCatching { reader.read(entry) }.getOrNull() ?: return
        updateState { state -> state.copy(pendingAttachedQuestion = read) }
    }

    private fun remainingImageSlots(): Int = MAX_TUTOR_MESSAGE_IMAGES - _uiState.value.pendingImages.size

    private fun addPendingImages(uris: List<String>) {
        if (uris.isEmpty()) return
        updateState { state ->
            state.copy(
                pendingImages = (state.pendingImages + uris.map(::PendingMessageImage))
                    .take(MAX_TUTOR_MESSAGE_IMAGES),
            )
        }
    }

    // ---- 模型能力（唯一的活动闸门） ----

    fun refreshProvider() {
        viewModelScope.launch {
            try {
                val provider = modelTasks.capabilities()
                updateState { state -> state.copy(provider = provider, providerLoadFailed = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { state -> state.copy(providerLoadFailed = true) }
            }
        }
    }

    // ---- 发送（新发送 / 失败重发 / 继续未完成） ----
    // 三条路径都在 [TutorLobbySendCommands] 里：它只通过回调读写这里的状态，状态仍只有这一个
    // 持有者（C2）。这里只留入口——薄的委托，不放逻辑，也就不再有第二份实现可以漂移。

    fun onSubmitDraft() = sendCommands.onSubmitDraft()

    fun onRetryFailedMessage() = sendCommands.onRetryFailedMessage()

    fun onResumeStalledTask() = sendCommands.onResumeStalledTask()

    fun onResendMessage(message: TutorMessage) = sendCommands.onResendMessage(message)


    private fun reduceSend(action: TutorSendAction) {
        updateState { state ->
            state.copy(sendState = TutorTurnSendStateMachine.reduce(state.sendState, action))
        }
    }

    /** 直接落一个相位（挂起/裁决/重启用：那几处的相位不是"归约一条动作"就能表达的）。 */
    private fun applySendState(state: TutorSendState) {
        updateState { current -> current.copy(sendState = state) }
    }

    // ---- 内部 ----

    private suspend fun reloadDraftFromConversationRow() {
        val conversationId = _uiState.value.conversationId
        if (conversationId.isBlank()) return
        val saved = runCatching {
            conversations.observeConversation(conversationId).first()
        }.getOrNull()?.conversation?.studentDraft.orEmpty()
        updateState { state -> state.copy(draft = saved) }
    }

    private fun observeLiveTurn(conversationKey: String, tasks: List<ModelTaskSnapshot>) {
        liveTurnSubscription.update(conversationKey, tasks)
    }

    private fun updateState(transform: (TutorConversationUiState) -> TutorConversationUiState) {
        _uiState.update(transform)
        // B3：跨进程保留的那八件在这里统一写进 SavedStateHandle——只有一个写入点
        // （键与恢复都在 `TutorConversationSavedState` 里，与这里同一组事实）。
        persistTutorConversationState(savedStateHandle, _uiState.value)
    }

}
