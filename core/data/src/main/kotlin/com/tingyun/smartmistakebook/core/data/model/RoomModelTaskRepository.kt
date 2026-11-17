package com.tingyun.smartmistakebook.core.data.model

import org.commonmark.node.Paragraph
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.reviewTutorTeachingFigures
import com.tingyun.smartmistakebook.core.model.TutorMessageIntent
import com.tingyun.smartmistakebook.core.model.TutorTeachingFigureStatus
import com.tingyun.smartmistakebook.core.model.TutorTeachingFigureReview
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import com.tingyun.smartmistakebook.core.model.figureCapabilitiesSnapshot
import com.tingyun.smartmistakebook.core.model.withFigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.TutorAgentContext
import com.tingyun.smartmistakebook.core.model.agentContextSnapshot
import com.tingyun.smartmistakebook.core.model.withAgentContext

import com.tingyun.smartmistakebook.core.database.CreateModelTaskCommand
import com.tingyun.smartmistakebook.core.database.ReserveModelTaskRemoteDispatchCommand
import com.tingyun.smartmistakebook.core.model.TutorCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCode
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCodeRole
import com.tingyun.smartmistakebook.core.model.TutorTeachingReference
import com.tingyun.smartmistakebook.core.data.study.TutorKnowledgeCodeRegistry
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.isReady
import com.tingyun.smartmistakebook.core.database.TransitionModelTaskCommand
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.domain.masteryUpdateCodeWhitelist
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentDecision
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.CaptureParseInput
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ModelEgressAuthorizationException
import com.tingyun.smartmistakebook.core.model.ModelEgressPolicy
import com.tingyun.smartmistakebook.core.model.ModelExecutionPermit
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelLiveText
import com.tingyun.smartmistakebook.core.model.ModelTaskCompletionValidator
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.domain.TutorPermissionSubject
import com.tingyun.smartmistakebook.core.domain.TutorPermissionTier
import com.tingyun.smartmistakebook.core.domain.TutorRoundPermissionContext
import com.tingyun.smartmistakebook.core.domain.TUTOR_TOOL_DECLARATIONS
import com.tingyun.smartmistakebook.core.domain.tutorPermissionDecision
import com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import com.tingyun.smartmistakebook.core.model.TutorToolRoundResult
import com.tingyun.smartmistakebook.core.model.tutorToolTraceHeadline
import com.tingyun.smartmistakebook.core.model.tutorToolTraceRunningText
import com.tingyun.smartmistakebook.core.data.study.RoomTutorToolRunner
import com.tingyun.smartmistakebook.core.data.study.TutorToolExecution
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.disclosesQuestionCandidates
import com.tingyun.smartmistakebook.core.model.tutorToolAuthorization
import com.tingyun.smartmistakebook.core.model.tutorToolRoundResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class RoomModelTaskRepository internal constructor(
    private val database: StudyDatabasePort,
    private val gateway: ModelGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * 知识能力就绪位（D-Q3）：工具环的 KNOWLEDGE_READ 在内容没就位时回"还在准备"。
     * 必需参数（无默认）：落一个默认值就等于留一条"忘了接线 → 静默零命中"的后门。
     *
     * PRD-06：它同时是能力快照的"知识材料就绪"位——没就绪时 KNOWLEDGE_READ 连广告都不出现
     * （而不是先广告、再让模型拿到一句"还在准备"）。
     */
    private val knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability>,
    /**
     * A1 生图执行缝（4B）：生产由 app 经 [ModelTaskRepositoryFactory] 注入
     * `TutorFigureGeneratorFactory` 的产物。
     *
     * 默认 null 与上面那个必需参数不同：null 不是"静默零命中"——`GENERATE_FIGURE` 会回
     * **可见的**"本机没有可用的生图通道"错误结果（工具卡灰色小字也照实说），忘了接线当场
     * 看得见；测试直调不必被生图通道绑住。
     *
     * PRD-06：非 null 且本轮 provider 确认支持图片能力时，`GENERATE_FIGURE` 才进广告面；
     * 否则模型连这枚工具都看不到（不是先广告、再回一句"不可用"）。
     */
    private val figureGenerator: TutorFigureGenerator? = null,
    private val figureCapabilities: (suspend () -> com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities)? = null,
    private val localActionExecutor: com.tingyun.smartmistakebook.core.domain.TutorLocalActionExecutor? = null,
    private val visualEngine: com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine? = null,
    /**
     * JS 引擎的**装配态就绪位**（warm-up / 隔离服务握手完成）。
     *
     * 与 `visualEngine != null` 不是一回事：引擎对象在进程里可能已创建，但 `:visual_js`
     * 服务尚未 ready（KD-76 的冷启动窗口）。只按非 null 广告会让首个 RUN_JAVASCRIPT 必败；
     * null = 旧装配/测试没有就绪位，维持原有行为。
     */
    private val visualEngineReady: (() -> Boolean)? = null,
    private val figureInspector: com.tingyun.smartmistakebook.core.data.model.TutorFigureInspector? = null,
    private val javascriptStateStore: com.tingyun.smartmistakebook.core.domain.TutorJavascriptStateStore? = null,
    private val resultStore: com.tingyun.smartmistakebook.core.domain.TutorToolResultStore? = null,
    /**
     * 已审知识目录检索（KNOWLEDGE_CATALOG_READ）：生产由 app 注入
     * `knowledgeCatalogRepository.search(...)`；null = 未接线，工具回可见的不可用结果。
     */
    private val catalogSearch: (suspend (String) -> List<com.tingyun.smartmistakebook.core.model.CatalogHit>)? = null,
) : ModelTaskRepository {
    internal val toolRunner = RoomTutorToolRunner(
        database,
        knowledgeBaseAvailability,
        figureGenerator,
        visualEngine,
        localActionExecutor,
        figureInspector,
        javascriptStateStore,
        resultStore,
        catalogSearch,
    )

    /**
     * 会话级知识点代号注册表（单一代号通道，ADR 0001 / D5）：sessionId → 注册表，进程内
     * 内存。Plan/Respond 的 execute() 入口先对输入赋码（持久化形状即赋码形状），工具环的
     * MASTERY_UPDATE 白名单与 runner 的代号解析都读同一份。大厅没有科目上下文与预披露节点，
     * 不建注册表（其 MASTERY_UPDATE 白名单为空 → 任何代号结构性拒）。
     */
    private val knowledgeCodeRegistries = ConcurrentHashMap<String, TutorKnowledgeCodeRegistry>()

    /**
     * 生成中的逐 token 实时文本（界面通道，见 [ModelTaskLiveTextStore]）：它是"此刻屏幕上该
     * 显示什么"，不是事实来源，所以既不落库也不写审计行。
     */
    private val liveTexts = ModelTaskLiveTextStore()

    /**
     * 这一轮**查阅了什么**的痕迹（B1，见 [ModelTaskToolTraceStore]）：与实时文本的关键差别是
     * 用时——实时文本随终态清空，痕迹**不许**（消息行的写入方正是在终态那一刻读它）。
     */
    private val toolTraces = ModelTaskToolTraceStore()

    override suspend fun capabilities(): ProviderCapabilitySnapshot = gateway.capabilities().let { provider ->
        if (figureCapabilities == null) provider else provider.copy(figureGenerationCapabilities = figureCapabilities.invoke())
    }

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> =
        database.observeModelTask(requestId)

    override fun observeLiveText(requestId: String): Flow<ModelLiveText?> =
        liveTexts.observe(requestId)

    override fun observeToolTrace(requestId: String): Flow<String?> =
        combine(toolTraces.observe(requestId), database.observeModelTask(requestId)) { live, snapshot ->
            live ?: snapshot?.takeIf { it.status == ModelTaskStatus.SUCCEEDED }?.output?.deliveryTraceOrNull()
        }.distinctUntilChanged()

    override fun observeBySubject(
        subjectId: String,
        kind: ModelTaskKind,
    ): Flow<List<ModelTaskSnapshot>> = database.observeModelTasks(subjectId, kind)

    override fun observeRecentBySubject(
        subjectId: String,
        kind: ModelTaskKind,
        limit: Int,
    ): Flow<List<ModelTaskSnapshot>> = database.observeRecentModelTasks(subjectId, kind, limit)

    /**
     * 取消通道的登记处（A2）：**这一轮正在跑的 Job**。`flow` 的流体内取
     * `coroutineContext[Job]` 存进来，`cancel` 才找得到要停的那一条；
     * 流结束时按 Job 身份摘除（不是按 key 盲删——同一 requestId 的下一次执行不能被上一次的
     * finally 摘掉）。
     */
    private val activeExecutions = ConcurrentHashMap<String, Job>()

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = collectExecute(request)

    /**
     * 学生按下「停止」（A2）：取消这一轮的在途 Job，然后在 [NonCancellable] 里把任务写进既有
     * 的 `CANCELLED` 终态。
     *
     * 两个纪律：
     * - **先取消再落终态**（并等它真的退出）。反过来的话，被取消的协程可能在终态之后又写一帧，
     *   而取消本身也可能撞上任务自己的状态转换——那会让"停止"看起来没生效。
     * - **写终态复用既有 [transition]**（同一状态机、同一审计、同一终态清理实时文本），不另开
     *   写路径；取消不调用 `reserveModelTaskRemoteDispatch`，所以派发预算分毫不动。
     *
     * 冲突（取消与任务自己的转换撞车）时重读一行再试：取消的语义是"这一轮到此为止"，
     * 不能因为一次比较交换失败就静默地不生效。
     */
    override suspend fun cancel(requestId: String) {
        require(requestId.isNotBlank()) { "A model task cancellation needs a request id" }
        val job = activeExecutions[requestId]
        withContext(NonCancellable) {
            job?.cancel(StudentStoppedTheTurn())
            // 有界等待：取消是协作式的，真正的网络调用在取消时立刻收尾；上限只为"万一它卡住
            // 也不让停止按钮永远转圈"——终态照样会在下面落地。
            job?.let { withTimeoutOrNull(CANCEL_JOIN_TIMEOUT_MILLIS) { it.join() } }
        }
        landCancelledTerminal(requestId)
    }

    /** 把一行非终态任务写进 `CANCELLED`：重读 → 转换 → 撞车则重试。 */
    private suspend fun landCancelledTerminal(requestId: String) {
        repeat(CANCELLED_TRANSITION_ATTEMPTS) {
            val latest = database.readModelTask(requestId) ?: return
            if (latest.status.isTerminal) return
            try {
                transition(
                    current = latest,
                    nextStatus = ModelTaskStatus.CANCELLED,
                    stage = latest.stage,
                    userMessage = STOPPED_USER_MESSAGE,
                )
                return
            } catch (_: ConcurrentModelTaskTransition) {
                // 撞上任务自己的转换：重读当前行再来一次。
            }
        }
    }

    private fun collectExecute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
        // 单一代号通道（D5）：Plan/Respond 输入的预披露条目（派发方给的未赋码条目，或重试
        // 时存库行带回的已赋码条目）在此统一过会话注册表——首现顺序分配 K1..Kn、会话内
        // 稳定，教学参考的代号字段一并填上。赋码后的形状才是持久化与指纹的形状。
        val request = request.withSessionKnowledgeCodes()
        val requestFingerprint = ModelTaskFingerprint.of(request)
        val operationFingerprint = ModelTaskLogicalOperationFingerprint.of(request)
        val initial = database.createModelTask(
            CreateModelTaskCommand(
                taskId = stableTaskId(request.requestId),
                request = request,
                requestFingerprint = requestFingerprint,
                operationFingerprint = operationFingerprint,
                occurredAtEpochMillis = request.occurredAtEpochMillis,
            ),
        ).snapshot
        emit(initial)
        if (initial.status.isTerminal) return@flow
        var executionStart = initial
        var ownerCompletion = CompletableDeferred<Unit>()
        while (true) {
            val activeOwnerCompletion =
                processActiveOperations.putIfAbsent(operationFingerprint, ownerCompletion)
            if (activeOwnerCompletion == null) break

            activeOwnerCompletion.await()
            val latest = database.readModelTask(request.requestId) ?: return@flow
            emit(latest)
            if (latest.status.isTerminal || latest.status == ModelTaskStatus.RETRYABLE_FAILURE) {
                return@flow
            }
            executionStart = latest
            ownerCompletion = CompletableDeferred()
        }

        val executionJob = currentCoroutineContext()[Job]
        if (executionJob != null) activeExecutions[request.requestId] = executionJob
        var remoteDispatchWasReserved = false
        try {
            // 只有执行所有者可以清痕迹，等待者不能抹掉正在执行的工具结果。
            toolTraces.clear(request.requestId)
            var current = prepareForExecution(executionStart)
            emit(current)
            // 生图能力必须按**本机真实装配**折进本轮：`gateway.capabilities()` 只描述远程
            // 提供方，看不到本地 JS 引擎/宿主帧是否就绪。只有讲题三类输入消费这份快照；
            // 其余任务（采集/分类/导出等）保持原样，免得为一次凭据解析付无谓代价。
            val declaredProvider = if (
                request.input is TutorPlanInput ||
                request.input is TutorRespondInput ||
                request.input is TutorLobbyInput
            ) {
                capabilities()
            } else {
                gateway.capabilities()
            }
            capabilityFailure(request, declaredProvider)?.let { failure ->
                emit(
                    transition(
                        current = current,
                        nextStatus = ModelTaskStatus.PERMANENT_FAILURE,
                        stage = ModelTaskStage.PREPARING,
                        userMessage = failure.message,
                        failure = failure,
                    ),
                )
                return@flow
            }
            dependencyFailure(request, declaredProvider)?.let { failure ->
                emit(
                    transition(
                        current = current,
                        nextStatus = ModelTaskStatus.PERMANENT_FAILURE,
                        stage = ModelTaskStage.VALIDATING_OUTPUT,
                        userMessage = failure.message,
                        failure = failure,
                    ),
                )
                return@flow
            }
            // 工具环（spec model-intent-routing §3.1）：模型可在作答前申请本地只读查询。
            // 每轮派遣独立 reserve（预算记账，MAX_DISPATCHES=9 = 8 轮工具 + 1 轮终答，
            // 见 a4109e1 与 D-001 2026-09-09 增补）；本地工具执行不占派遣预算。
            // 工具轮配额用尽（MAX_TOOL_ROUNDS=5）后不再声明工具，模型必须直接作答。
            // PRD-06：派遣前先把"配置/依赖就绪"折进本轮的声明集——**第一条派遣**也必须
            // 只广告真的能执行的工具。工具环的收敛集只写回第二轮，不能拿它补第一轮。
            // 把真实生图能力折进输入本身：工具 schema（method.enum/program）、提示词方法段、
            // 以及轮次层/执行器的 JS 就绪门都从 `input.figureCapabilitiesSnapshot()` 读；
            // 只折进派遣副本，持久化 request 与指纹逐字不变。
            val capabilityInput = request.input.withFigureGenerationCapabilities(
                declaredProvider.figureGenerationCapabilities,
            ).let { input ->
                if (input !is com.tingyun.smartmistakebook.core.model.TutorLobbyInput &&
                    input !is com.tingyun.smartmistakebook.core.model.TutorPlanInput &&
                    input !is com.tingyun.smartmistakebook.core.model.TutorRespondInput
                ) {
                    return@let input
                }
                val availableActions = try {
                    localActionExecutor?.availableActions()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 有执行器却查不出可用集 = 装配故障：fail-closed，不能回退成"全部动作"。
                    if (localActionExecutor == null) null else emptySet()
                }
                if (availableActions == null) {
                    input
                } else {
                    input.withAgentContext(
                        (input.agentContextSnapshot() ?: TutorAgentContext())
                            .copy(availableLocalActions = availableActions),
                    )
                }
            }
            val declaredTools = toolDeclarationsFor(capabilityInput, declaredProvider)
            var roundRequest = request.copy(
                input = capabilityInput.withDeclaredTools(declaredTools),
            )
            var toolRoundsUsed = 0
            var toolRoundResults = emptyList<TutorToolRoundResult>()
            var answered = false
            while (!answered) {
            val execution = try {
                ModelEgressPolicy.authorize(
                    request = roundRequest,
                    provider = declaredProvider,
                    nowEpochMillis = clock(),
                )
            } catch (denied: ModelEgressAuthorizationException) {
                emit(
                    transition(
                        current = current,
                        nextStatus = ModelTaskStatus.PERMANENT_FAILURE,
                        stage = ModelTaskStage.PREPARING,
                        userMessage = denied.message,
                        failure = ModelTaskFailure(
                            code = denied.failureCode,
                            message = denied.message,
                            retryable = false,
                        ),
                    ),
                )
                return@flow
            }
            current = when (execution.permit) {
                is ModelExecutionPermit.External,
                ModelExecutionPermit.ProviderConsented,
                -> {
                    val reservation = database.reserveModelTaskRemoteDispatch(
                        ReserveModelTaskRemoteDispatchCommand(
                            taskId = current.taskId,
                            expectedStateVersion = current.stateVersion,
                            expectedStatus = current.status,
                            provider = declaredProvider,
                            occurredAtEpochMillis = maxOf(clock(), current.updatedAtEpochMillis),
                        ),
                    )
                    if (!reservation.applied) {
                        if (!reservation.budgetExhausted) throw ConcurrentModelTaskTransition()
                        emit(
                            transition(
                                current = reservation.snapshot,
                                nextStatus = ModelTaskStatus.PERMANENT_FAILURE,
                                stage = ModelTaskStage.PREPARING,
                                userMessage = DISPATCH_LIMIT_USER_MESSAGE,
                                attemptCount = reservation.logicalDispatchCount,
                                failure = exhaustedDispatchFailure(executionStart.failure),
                            ),
                        )
                        return@flow
                    }
                    remoteDispatchWasReserved = true
                    reservation.snapshot
                }
                ModelExecutionPermit.LocalOnly -> transition(
                    current = current,
                    nextStatus = ModelTaskStatus.RUNNING,
                    stage = ModelTaskStage.PREPARING,
                    userMessage = "正在准备",
                    provider = declaredProvider,
                )
            }
            emit(current)
            var eventCount = 0
            var terminalEventSeen = false
            var providerStarted = false
            var toolRound: TutorToolRequestsOutput? = null
            withTimeout(MODEL_TASK_CEILING_MILLIS) {
                gateway.execute(execution)
                    .onEach { event ->
                        // 逐 token 实时文本不计入事件预算：它不落库、不写审计行，正是为了让
                        // "每个增量都能到屏幕"成为可能；计数只约束真正要落盘的进度与终态。
                        if (event !is ModelGatewayEvent.LiveProgress) {
                            eventCount += 1
                            if (eventCount > MAX_GATEWAY_EVENTS) {
                                throw InvalidProviderProtocol("模型返回了过多状态事件")
                            }
                        } else {
                            // 实时文本不算状态变化：发布到内存通道后直接返回，不发快照也不落库。
                            // 发快照的代价很具体——一帧一次 UI 重组，几百帧就是几百次白重组，
                            // 而屏幕上要显示什么由 liveTexts 那条通道单独驱动。
                            if (!providerStarted) {
                                throw InvalidProviderProtocol("模型在开始任务前返回了内容")
                            }
                            liveTexts.publish(
                                current.request.requestId,
                                ModelLiveText(event.kind, event.text),
                            )
                            return@onEach
                        }
                        when (event) {
                            is ModelGatewayEvent.Started -> {
                                if (providerStarted) {
                                    throw InvalidProviderProtocol("模型返回了重复或乱序的开始事件")
                                }
                                providerStarted = true
                            }
                            is ModelGatewayEvent.Progress,
                            is ModelGatewayEvent.Completed,
                            -> if (!providerStarted) {
                                throw InvalidProviderProtocol("模型在开始任务前返回了内容")
                            }
                            is ModelGatewayEvent.LiveProgress,
                            is ModelGatewayEvent.Failed,
                            -> Unit
                        }
                        val requestedActions = (event as? ModelGatewayEvent.Completed)
                            ?.output?.let { it as? TutorLobbyOutput }?.localActions.orEmpty()
                        if (event is ModelGatewayEvent.Completed &&
                            (event.output is TutorToolRequestsOutput || requestedActions.isNotEmpty())) {
                            // 工具申请轮：不落终态，转回 QUEUED（RUNNING→QUEUED 合法，
                            // RUNNING→RUNNING 被 canTransitionTo 拒绝）后进入下一轮
                            toolRound = event.output as? TutorToolRequestsOutput
                                ?: (event.output as TutorLobbyOutput).let { output ->
                                    TutorToolRequestsOutput(
                                        intentDecision = output.intentDecision,
                                        calls = emptyList(),
                                        modelVersion = output.modelVersion,
                                        localActions = requestedActions,
                                    )
                                }
                            current = transition(
                                current = current,
                                nextStatus = ModelTaskStatus.QUEUED,
                                stage = ModelTaskStage.PREPARING,
                                userMessage = "正在查阅资料",
                                provider = declaredProvider,
                            )
                            emit(current)
                            terminalEventSeen = true
                            return@onEach
                        }
                        val eventToPersist = if (
                            event is ModelGatewayEvent.Completed &&
                            event.output is TutorLobbyOutput &&
                            roundRequest.input is TutorLobbyInput
                        ) {
                            event.copy(
                                output = (event.output as TutorLobbyOutput).copy(
                                    requestedLocalActions = (roundRequest.input as TutorLobbyInput)
                                        .requestedLocalActions,
                                ),
                            )
                        } else {
                            event
                        }
                        current = applyGatewayEvent(
                            current = current,
                            event = eventToPersist,
                            declaredProvider = declaredProvider,
                            enforceRemoteDispatchBudget = remoteDispatchWasReserved,
                        )
                        emit(current)
                        terminalEventSeen = event is ModelGatewayEvent.Completed ||
                            event is ModelGatewayEvent.Failed
                    }
                    .takeWhile { !terminalEventSeen }
                    .collect {}
            }
            if (!terminalEventSeen) {
                throw InvalidProviderProtocol("模型没有返回完成状态")
            }
            val requests = toolRound
            if (requests == null) {
                answered = true
            } else {
                toolRoundsUsed += 1
                if (toolRoundsUsed > TutorToolRoundResult.MAX_TOOL_ROUNDS) {
                    throw InvalidProviderProtocol("模型在工具配额用尽后仍未作答")
                }
                val authorization = tutorToolAuthorization(requests.intentDecision, declaredTools)
                // 工具调用要看得见：学生此前完全看不到"正在查阅错题本"这类过程（工具环只把
                // 结果塞进下一轮提示词，界面上一片安静），于是工具调用看起来"没有接进来"。
                // 生成中聚合成一个活单元（「正在查阅错题本、掌握情况…」），随流更新。
                liveTexts.publish(
                    request.requestId,
                    ModelLiveText(
                        kind = ModelLiveKind.TOOL,
                        text = tutorToolTraceRunningText(requests.calls.map(TutorToolCall::tool)),
                    ),
                )
                // 扩展结果预算每轮只放一次：一次读工具的结果最多 6k 字符，三个并发请求会在下一轮
                // prompt 里堆到 18k。模型仍可对每次查询表达"需要更大预算"（语义），但放大几次由本地
                // 定——与本项目"模型给语义、本地给数值"的划分一致。
                var extendedResultUsed = false
                // 单一代号通道（D5）：MASTERY_UPDATE 的 enum 白名单 = 本会话已披露代号集。
                // Route A 的 schema 约束解码是前哨，这里是 Route B（json_object 信封）与
                // 越界复述的背底：非法/编造/未披露代号结构性拒，不进执行器、不进门。
                // 白名单**取自注册表**（不是本轮输入的 knowledgeCodes 字段），附加题轮次由
                // [masteryUpdateCodeWhitelist] 判成空集：那一轮讲的是另一道题，代号表不属于它。
                val disclosedKnowledgeCodes = masteryUpdateCodeWhitelist(
                    input = roundRequest.input,
                    sessionDisclosedCodes = sessionKnowledgeCodeRegistry(
                        roundRequest.input,
                        knowledgeCodeRegistries,
                    )
                        ?.disclosedCodes()
                        .orEmpty(),
                )
                // 权限三档（规格 §3.3 / D-K2c-K2d）在**执行前**统一问一次：读工具 allow、
                // MASTERY_UPDATE auto+visible、NOTEBOOK_WRITE ask——分档与"ask 档那句诚实的
                // 回执"都在 TutorToolRoundPermissions 里（那段策略单独可测）。
                val permissionRounds = tutorToolRoundsByPermission(
                    calls = requests.calls,
                    allowedTools = authorization.allowedTools,
                )
                val executions = tutorToolRoundOutcomes(
                    calls = permissionRounds.executable,
                    authorizedTools = authorization.allowedTools,
                    disclosedKnowledgeCodes = disclosedKnowledgeCodes,
                    javascriptReady = figureGenerator != null &&
                        com.tingyun.smartmistakebook.core.model.FigureGenerationMethod.JS in
                        roundRequest.input.figureCapabilitiesSnapshot()?.availableMethods.orEmpty(),
                    runTool = { call, allowsExtendedResult ->
                        toolRunner.runTraced(
                            call,
                            tutorToolContext(
                                input = roundRequest.input,
                                requestId = roundRequest.requestId,
                                allowsExtendedResult = allowsExtendedResult,
                                sessionKnowledgeCodeRegistry = sessionKnowledgeCodeRegistry(
                                    roundRequest.input,
                                    knowledgeCodeRegistries,
                                ),
                            ),
                        )
                    },
                    consumeExtendedResult = { extendedResultUsed = true },
                ) + permissionRounds.awaitingConsent
                // Action-only rounds carry their receipts separately from tool results.
                if (executions.isNotEmpty()) {
                    toolRoundResults = toolRoundResults + tutorToolRoundResult(
                        roundOrdinal = toolRoundsUsed,
                        outcomes = executions.map(TutorToolExecution::outcome),
                        extendedResultUsed = extendedResultUsed,
                    )
                }
                // 痕迹（B1）：先并进这一轮的条目，再用**同一条渲染函数**说出这一轮的结果——
                // 生成中那行小字与重开会话后看到的那行小字因此不可能漂成两句话。
                toolTraces.append(request.requestId, executions)
                val actionOutcomes = executeLocalActions(roundRequest, requests.localActions, toolRoundsUsed)
                liveTexts.publish(
                    request.requestId,
                    ModelLiveText(
                        kind = ModelLiveKind.TOOL,
                        text = tutorActionProgressText(
                            toolTraces.current(request.requestId)?.let(::tutorToolTraceHeadline),
                            actionOutcomes,
                        ),
                    ),
                )
                // The capability snapshot applies the completed-round budget to both surfaces.
                val converged = toolDeclarationsFor(
                    roundRequest.input,
                    declaredProvider,
                ).toList()
                // KNOWLEDGE_READ 本轮追加披露的节点已进注册表：下一轮输入带**全会话**披露集
                // （只增不减），映射表才能在前缀区保持稳定、模型引用的 K6 在下一轮仍可见。
                val sessionKnowledgeCodes = sessionKnowledgeCodeRegistry(
                    roundRequest.input,
                    knowledgeCodeRegistries,
                )?.disclosed()
                val progressed = roundRequest.input.withToolRoundProgress(
                    newRounds = toolRoundResults,
                    convergedDeclarations = converged,
                    sessionKnowledgeCodes = sessionKnowledgeCodes,
                    // Retain requested actions for history. Current-round execution receipts
                    // below tell the next dispatch which side effects actually completed.
                    newLocalActions = requests.localActions.filter { request ->
                        request.action in
                            TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS
                    },
                    actionOutcomes = actionOutcomes,
                    toolRoundsUsed = toolRoundsUsed,
                )
                roundRequest = roundRequest.copy(
                    // 把这一轮工具的真实产物折进下一轮上下文：NOTEBOOK_READ 的条目 id、
                    // SELECT_QUESTION 选中的题、生图/读图的资产 id、JS 状态与动作回执。
                    // 这些只来自本地执行结果，模型编造的 id 不会进来。
                    input = progressed.withAgentFacts(executions, actionOutcomes, toolRoundsUsed),
                    egressManifest = roundRequest.egressManifest,
                )
            }
            }
        } catch (concurrent: ConcurrentModelTaskTransition) {
            database.readModelTask(request.requestId)?.let { latest -> emit(latest) }
        } catch (timeout: TimeoutCancellationException) {
            val latest = database.readModelTask(request.requestId) ?: throw timeout
            if (!latest.status.isTerminal && latest.status != ModelTaskStatus.RETRYABLE_FAILURE) {
                emit(
                    transitionRemoteFailure(
                        current = latest,
                        stage = latest.stage,
                        userMessage = "模型响应超时，任务已保留",
                        enforceRemoteDispatchBudget = remoteDispatchWasReserved,
                        failure = ModelTaskFailure(
                            code = ModelFailureCode.TIMEOUT,
                            message = "模型响应超时，可以稍后继续",
                            retryable = true,
                        ),
                    ),
                )
            }
        } catch (invalid: InvalidProviderProtocol) {
            // 归因（A4）：用户可见文案只有"这次内容无法使用"，从卡片上看不出判它的具体原因
            //（事件乱序 / 事件超限 / 输出格式不符 …）——把底层这一句写进日志；用户可见文案不动。
            android.util.Log.w(
                MODEL_TASK_LOG_TAG,
                "model task rejected as INVALID_RESPONSE: " +
                    "${invalid.javaClass.simpleName}: ${invalid.message}",
            )
            val latest = database.readModelTask(request.requestId) ?: throw invalid
            if (!latest.status.isTerminal) {
                emit(
                    transition(
                        current = latest,
                        nextStatus = ModelTaskStatus.PERMANENT_FAILURE,
                        stage = ModelTaskStage.VALIDATING_OUTPUT,
                        userMessage = "这次内容无法使用，请重试",
                        failure = ModelTaskFailure(
                            code = ModelFailureCode.INVALID_RESPONSE,
                            message = invalid.userMessage,
                            retryable = false,
                        ),
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            if (cancelled is StudentStoppedTheTurn) {
                // 学生停止必须先落终态再交出执行权，否则等待者会接管并重新派发。
                withContext(NonCancellable) { landCancelledTerminal(request.requestId) }
            }
            throw cancelled
        } catch (failure: Exception) {
            // 只记异常类，不记 message：message 可能带回模型输出/学生内容。
            android.util.Log.e(
                MODEL_TASK_LOG_TAG,
                "Model task failed unexpectedly: ${failure.javaClass.name}",
            )
            val latest = database.readModelTask(request.requestId) ?: throw failure
            if (!latest.status.isTerminal && latest.status != ModelTaskStatus.RETRYABLE_FAILURE) {
                emit(
                    transitionRemoteFailure(
                        current = latest,
                        stage = latest.stage,
                        userMessage = "这次处理已保存，可以稍后重试",
                        enforceRemoteDispatchBudget = remoteDispatchWasReserved,
                        failure = ModelTaskFailure(
                            code = ModelFailureCode.UNKNOWN,
                            message = "模型暂时没有完成这项任务",
                            retryable = true,
                        ),
                    ),
                )
            }
        } finally {
            if (executionJob != null) activeExecutions.remove(request.requestId, executionJob)
            processActiveOperations.remove(operationFingerprint, ownerCompletion)
            ownerCompletion.complete(Unit)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun prepareForExecution(initial: ModelTaskSnapshot): ModelTaskSnapshot =
        when (initial.status) {
            ModelTaskStatus.WAITING_FOR_MODEL,
            ModelTaskStatus.RETRYABLE_FAILURE,
            ModelTaskStatus.RUNNING,
            ModelTaskStatus.STREAMING,
            -> transition(
                current = initial,
                nextStatus = ModelTaskStatus.QUEUED,
                stage = ModelTaskStage.PREPARING,
                userMessage = if (initial.attemptCount == 0) {
                    "正在准备"
                } else {
                    "正在从上次位置继续"
                },
            )
            ModelTaskStatus.QUEUED -> initial
            ModelTaskStatus.SUCCEEDED,
            ModelTaskStatus.PERMANENT_FAILURE,
            ModelTaskStatus.CANCELLED,
            -> initial
        }

    private suspend fun applyGatewayEvent(
        current: ModelTaskSnapshot,
        event: ModelGatewayEvent,
        declaredProvider: ProviderCapabilitySnapshot,
        enforceRemoteDispatchBudget: Boolean,
    ): ModelTaskSnapshot = when (event) {
        is ModelGatewayEvent.LiveProgress -> {
            // 实时文本不改变任务状态：发布与"不发快照"都在 onEach 里完成，这里只保证状态机
            // 对这类事件是显式的无变化（走到这里说明它被当成了状态事件，状态本身仍不动）。
            current
        }
        is ModelGatewayEvent.Started -> {
            if (current.status != ModelTaskStatus.RUNNING) {
                throw InvalidProviderProtocol("模型返回了重复或乱序的开始事件")
            }
            if (!event.provider.sameRemoteIdentityAs(declaredProvider)) {
                throw InvalidProviderProtocol("模型身份或能力在任务开始后发生变化")
            }
            if (!event.provider.supports(current.request.input.kind)) {
                throw InvalidProviderProtocol("当前模型不支持这项任务")
            }
            if (
                current.request.input is CaptureAssessmentInput ||
                current.request.input is CaptureParseInput
            ) {
                if (!event.provider.supportsImageInput || !event.provider.supportsStructuredOutput) {
                    throw InvalidProviderProtocol("当前配置暂时无法处理这张题图")
                }
            }
            current
        }
        is ModelGatewayEvent.Progress -> {
            if (current.status !in setOf(ModelTaskStatus.RUNNING, ModelTaskStatus.STREAMING)) {
                throw InvalidProviderProtocol("模型在开始任务前返回了进度")
            }
            transition(
                current = current,
                nextStatus = ModelTaskStatus.STREAMING,
                stage = event.stage,
                userMessage = event.userMessage,
                provider = current.provider,
            )
        }
        is ModelGatewayEvent.Completed -> {
            if (current.status !in setOf(ModelTaskStatus.RUNNING, ModelTaskStatus.STREAMING)) {
                throw InvalidProviderProtocol("模型在开始任务前返回了完成内容")
            }
            val validationIssues = ModelTaskCompletionValidator.validate(
                request = current.request,
                output = event.output,
            )
            if (validationIssues.isNotEmpty()) {
                throw InvalidProviderProtocol("模型输出格式不符合当前题目任务")
            }
            val figureReview = reviewTeachingFigures(current.request, event.output)
            if (figureReview != null) toolTraces.completeFigures(current.request.requestId, figureReview)
            val deliveryTrace = toolTraces.current(current.request.requestId)?.let(
                ::encodeTutorTurnToolTrace)
            val completedOutput = when (val output = event.output) {
                is TutorPlanOutput -> output.copy(deliveryToolTraceJson = deliveryTrace)
                is TutorRespondOutput -> output.copy(deliveryToolTraceJson = deliveryTrace)
                is TutorLobbyOutput -> output.copy(deliveryToolTraceJson = deliveryTrace)
                else -> output
            }
            transition(
                current = current,
                nextStatus = ModelTaskStatus.SUCCEEDED,
                stage = ModelTaskStage.COMPLETE,
                userMessage = if (figureReview?.status == TutorTeachingFigureStatus.INCOMPLETE) {
                    "正文已保存，配图未完成"
                } else when (current.request.input) {
                    is com.tingyun.smartmistakebook.core.model.DetailedSolutionInput -> "详细答案已准备好，请预览后导出"
                    is com.tingyun.smartmistakebook.core.model.MistakeEditInput -> when ((event.output as? com.tingyun.smartmistakebook.core.model.MistakeEditOutput)?.suggestion?.decision) {
                        com.tingyun.smartmistakebook.core.model.MistakeEditDecision.NO_CHANGE -> "当前无需修改"
                        com.tingyun.smartmistakebook.core.model.MistakeEditDecision.NEED_SOURCE -> "需要补充原图后继续"
                        com.tingyun.smartmistakebook.core.model.MistakeEditDecision.UNAVAILABLE -> "这次修改暂时无法完成"
                        else -> "修改建议已准备好，请审阅后采用"
                    }
                    is CaptureAssessmentInput -> "图片检查已完成，可以继续转写题面"
                    is CaptureParseInput -> "题面已准备好"
                    is ImagePipelineClassifyInput -> "题面分类已完成"
                    is TutorPlanInput -> "讲解已准备好"
                    is TutorLobbyInput -> "回复已准备好"
                    is com.tingyun.smartmistakebook.core.model.TutorDebriefInput -> "讲题要点已整理"
                    is TutorRespondInput -> "回复已准备好"
                    is com.tingyun.smartmistakebook.core.model.KnowledgeQuizInput -> "复习题已准备好"
                    is ProblemOrganizationInput -> "分类和题目联系建议已生成，请确认后再保存"
                    is PaperEditInput -> "卷面修改建议已生成，请确认后再应用"
                },
                provider = current.provider,
                output = completedOutput,
            )
        }
        is ModelGatewayEvent.Failed -> {
            if (current.status !in setOf(
                    ModelTaskStatus.RUNNING,
                    ModelTaskStatus.STREAMING,
                )
            ) {
                throw InvalidProviderProtocol("模型返回了乱序的失败事件")
            }
            transitionRemoteFailure(
                current = current,
                stage = current.stage,
                userMessage = event.failure.message,
                provider = current.provider,
                enforceRemoteDispatchBudget = enforceRemoteDispatchBudget,
                failure = event.failure,
            )
        }
    }

    private fun reviewTeachingFigures(
        request: ModelTaskRequest,
        output: ModelTaskOutput,
    ): TutorTeachingFigureReview? {
        val capabilities = request.input.figureCapabilitiesSnapshot() ?: return null
        val content = when (output) {
            is TutorPlanOutput -> output.plan.openingMarkdown to output.teachingFigureOmission
            is TutorRespondOutput -> {
                if (output.intentDecision.intent !in TEACHING_INTENTS) return null
                output.messageMarkdown to output.teachingFigureOmission
            }
            is TutorLobbyOutput -> {
                if (output.intentDecision.intent !in TEACHING_INTENTS) return null
                output.messageMarkdown to output.teachingFigureOmission
            }
            else -> return null
        }
        return reviewTutorTeachingFigures(
            tutorTeachingFigureReferences(content.first), toolTraces.current(request.requestId), capabilities, content.second)
    }

    private suspend fun transitionRemoteFailure(
        current: ModelTaskSnapshot,
        stage: ModelTaskStage,
        userMessage: String,
        provider: ProviderCapabilitySnapshot? = current.provider,
        enforceRemoteDispatchBudget: Boolean,
        failure: ModelTaskFailure,
    ): ModelTaskSnapshot {
        val dispatchBudgetExhausted = failure.retryable && enforceRemoteDispatchBudget &&
            !ModelTaskRemoteDispatchPolicy.canSchedule(current.attemptCount)
        val mayRetry = failure.retryable && !dispatchBudgetExhausted
        return transition(
            current = current,
            nextStatus = if (mayRetry) {
                ModelTaskStatus.RETRYABLE_FAILURE
            } else {
                ModelTaskStatus.PERMANENT_FAILURE
            },
            stage = stage,
            userMessage = if (dispatchBudgetExhausted) {
                DISPATCH_LIMIT_USER_MESSAGE
            } else {
                userMessage
            },
            provider = provider,
            failure = if (mayRetry || !failure.retryable) {
                failure
            } else {
                failure.copy(retryable = false)
            },
        )
    }

    private suspend fun transition(
        current: ModelTaskSnapshot,
        nextStatus: ModelTaskStatus,
        stage: ModelTaskStage,
        userMessage: String,
        attemptCount: Int = current.attemptCount,
        provider: com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot? = null,
        output: ModelTaskOutput? = null,
        failure: ModelTaskFailure? = null,
    ): ModelTaskSnapshot = database.transitionModelTask(
        TransitionModelTaskCommand(
            taskId = current.taskId,
            expectedStateVersion = current.stateVersion,
            expectedStatus = current.status,
            nextStatus = nextStatus,
            stage = stage,
            userMessage = userMessage,
            attemptCount = attemptCount,
            provider = provider,
            output = output,
            failure = failure,
            occurredAtEpochMillis = maxOf(clock(), current.updatedAtEpochMillis),
        ),
    ).let { result ->
        if (!result.applied) throw ConcurrentModelTaskTransition()
        // 终态（含可重试失败）到了：内存里的实时文本已由落库正文接管，留着只会在下一次
        // 派发前闪出上一条的残留。
        if (nextStatus.isTerminal || nextStatus == ModelTaskStatus.RETRYABLE_FAILURE) {
            liveTexts.clear(current.request.requestId)
        }
        result.snapshot
    }

    private suspend fun dependencyFailure(
        request: ModelTaskRequest,
        declaredProvider: ProviderCapabilitySnapshot,
    ): ModelTaskFailure? {
        val input = request.input as? CaptureParseInput ?: return null
        val draft = database.readProblemDraft(input.draftId)
            ?: return invalidDependency("原题草稿已不存在，请重新录入")
        if (draft.currentRevision.revisionNumber != input.basisRevisionNumber) {
            return invalidDependency("题面已更新，请基于最新版本重新转写")
        }
        val draftSources = draft.sourceAssets.sortedBy { it.pageIndex }
        val requestedSources = input.sourceAssets.sortedBy { it.pageIndex }
        if (
            draftSources.size != requestedSources.size ||
            draftSources.zip(requestedSources).any { (draftPage, requestedPage) ->
                val source = draftPage.sourceAsset
                draftPage.pageIndex != requestedPage.pageIndex ||
                    source.sourceAssetId != requestedPage.assetId ||
                    source.contentSha256 != requestedPage.sha256 ||
                    source.width != requestedPage.width ||
                    source.height != requestedPage.height
            }
        ) {
            return invalidDependency("题图版本与转写任务不一致，请重新开始")
        }
        input.assessmentRequestIds.zip(requestedSources).forEach { (requestId, source) ->
            val draftSource = draftSources[source.pageIndex].sourceAsset
            val assessment = database.readModelTask(requestId)
                ?: return invalidDependency("请先完成每一页题图的范围检查")
            val assessmentInput = assessment.request.input as? CaptureAssessmentInput
                ?: return invalidDependency("题图检查任务类型不匹配")
            val assessmentOutput = assessment.output as? CaptureAssessmentOutput
                ?: return invalidDependency("仍有题图页面尚未检查完成")
            if (assessment.provider?.isDemo != declaredProvider.isDemo) {
                return invalidDependency("演示任务不能授权真实题图处理，请重新检查题图")
            }
            val decision = assessmentOutput.assessment.decision
            val pageAccepted = decision == CaptureAssessmentDecision.PASS ||
                (decision == CaptureAssessmentDecision.NEED_MORE_IMAGE &&
                    source.pageIndex < requestedSources.lastIndex)
            if (
                assessment.status != ModelTaskStatus.SUCCEEDED ||
                !pageAccepted ||
                assessmentInput.draftId != input.draftId ||
                assessmentInput.sourceAssetId != source.assetId ||
                assessmentInput.imageWidth != source.width ||
                assessmentInput.imageHeight != source.height ||
                assessment.request.occurredAtEpochMillis != draftSource.createdAtEpochMillis
            ) {
                return invalidDependency("题图检查未通过，暂不开始结构化转写")
            }
        }
        return null
    }

    private fun capabilityFailure(
        request: ModelTaskRequest,
        provider: ProviderCapabilitySnapshot,
    ): ModelTaskFailure? {
        if (provider.providerId == UNCONFIGURED_PROVIDER_ID) return null
        if (!provider.supports(request.input.kind)) {
            return ModelTaskFailure(
                code = ModelFailureCode.PROVIDER_CAPABILITY_MISSING,
                message = "当前模型不支持这项任务",
                retryable = false,
            )
        }
        if (
            (request.input is CaptureAssessmentInput || request.input is CaptureParseInput) &&
            (!provider.supportsImageInput || !provider.supportsStructuredOutput)
        ) {
            return ModelTaskFailure(
                code = ModelFailureCode.PROVIDER_CAPABILITY_MISSING,
                message = "当前配置暂时无法处理这张题图",
                retryable = false,
            )
        }
        val lobbyInput = request.input as? TutorLobbyInput
        if (
            lobbyInput != null &&
            lobbyInput.sourceImageAssetRefs.isNotEmpty() &&
            !provider.supportsImageInput
        ) {
            return ModelTaskFailure(
                code = ModelFailureCode.PROVIDER_CAPABILITY_MISSING,
                message = "当前模型不支持看图，请先更换支持图片的模型。",
                retryable = false,
            )
        }
        return null
    }

    private fun invalidDependency(message: String) = ModelTaskFailure(
        code = ModelFailureCode.PROVIDER_REJECTED_INPUT,
        message = message,
        retryable = false,
    )

    private fun exhaustedDispatchFailure(previous: ModelTaskFailure?): ModelTaskFailure =
        previous?.copy(retryable = false) ?: ModelTaskFailure(
            code = ModelFailureCode.UNKNOWN,
            message = "这次处理已达到重试次数上限，未再次连接模型",
            retryable = false,
        )

    private fun stableTaskId(requestId: String): String = "model-task-" +
        MessageDigest.getInstance("SHA-256")
            .digest(requestId.toByteArray(StandardCharsets.UTF_8))
            .take(16)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    /**
     * 这一轮**真的可以广告**的工具（`docs/tutor-surface-unification.md` §5.6；2026-09-21
     * 裁定 D7/D8 把 Plan 也纳入）：智能体页所有模型调用（Plan / Respond / 大厅）同一工具面，
     * **代码零入口分叉**；裁掉的只有本机事实（处理器是否实现、依赖是否就绪）。
     *
     * PRD-06：声明集不再是无条件的全量——图像后端/知识材料没就绪时对应工具不出现在
     * 模型面前。声明仍不等于放行：逐次裁决只剩两条，都取自模型自己这一轮的语义输出：
     * 意图授权矩阵（意图 × 置信度 × 声明集）与 MASTERY_UPDATE 的代号白名单（本会话已披露
     * 集合）。无题轮不再结构性拒写（D6）：写不写由模型语义判定，低置信/无引文写入由统一
     * 本地门挡。
     */
    internal fun toolDeclarationsFor(
        input: ModelTaskInput,
        declaredProvider: ProviderCapabilitySnapshot,
    ): Set<TutorToolName> {
        val methods = input.figureCapabilitiesSnapshot()?.availableMethods
        val jsReady = figureGenerator != null && com.tingyun.smartmistakebook.core.model.FigureGenerationMethod.JS in methods.orEmpty()
        // 空声明 = 入口没有限制（旧请求/首轮默认）：先补页面默认声明面，再按就绪位裁。
        // 空列表被当成“没有任何工具”会让首轮模型完全看不到工具，模型仍可能按旧提示词
        // 申请工具，工具环却把它当未声明丢弃（批次 16 设备用例暴露）。
        val effectiveInput = when (input) {
            is TutorLobbyInput -> if (input.toolDeclarations.isEmpty()) {
                input.copy(toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList())
            } else {
                input
            }
            is TutorPlanInput -> if (input.toolDeclarations.isEmpty()) {
                input.copy(toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList())
            } else {
                input
            }
            is TutorRespondInput -> if (input.toolDeclarations.isEmpty()) {
                input.copy(toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList())
            } else {
                input
            }
            else -> input
        }
        return TutorCapabilitySnapshot.forRound(effectiveInput)
            .withDependencies(
                imageBackendReady = figureGenerator != null && (methods?.contains(
                    com.tingyun.smartmistakebook.core.model.FigureGenerationMethod.IMAGE_MODEL)
                    ?: declaredProvider.supportsImageInput),
                knowledgeMaterialReady = knowledgeBaseAvailability.value.isReady,
                // A-05：JS 双就绪位（引擎 ready + 度量 ready）取自 coordinator 折进输入
                // 的真实能力集合——`jsReady` 同时要求执行器存在且本机 JS 方法可执行；
                // 装配级死亡（引擎 close/宿主帧丢失）会让下一轮的方法集合不再含 JS，
                // 这里随之为 false，JS 从广告面消失。
                jsEngineReady = jsReady,
                jsMeasurementReady = jsReady,
                // JS 工具只有在真实引擎已装配时才广告；READ_FIGURE 只有在像素检查器
                // 与"能看图"的提供方同时成立时才广告——否则下一轮无法把 grant 出网。
                javascriptToolReady = visualEngine != null &&
                    (visualEngineReady?.let { ready ->
                        runCatching { ready() }.getOrDefault(false)
                    } ?: true),
                figureReadReady = figureInspector != null && declaredProvider.supportsImageInput,
            )
            .tools
    }

    /**
     * 把能力快照折进本轮派遣的输入（只有声明集变化，其余字段逐字保留）。
     *
     * 不是"改写用户的请求"：持久化/指纹用的是入口 request；这里只裁**这一轮告诉模型什么**。
     * 工具环后续轮次的收敛集本来就写回输入（[withToolRoundProgress]），这里补的是第一轮。
     */
    private fun ModelTaskInput.withDeclaredTools(
        tools: Set<TutorToolName>,
    ): ModelTaskInput = when (this) {
        is TutorLobbyInput -> copy(toolDeclarations = tools.toList())
        is TutorPlanInput -> copy(toolDeclarations = tools.toList())
        is TutorRespondInput -> copy(toolDeclarations = tools.toList())
        else -> this
    }

    /**
     * 把已执行的工具轮结果、收敛声明集与会话代号披露集写回输入，供下一轮派遣携带（spec §3.4）。
     *
     * [sessionKnowledgeCodes] 为 null = 该输入种类没有代号通道（大厅）；非 null 时以注册表的
     * 全会话披露集**整体替换**输入的 knowledgeCodes（只增不减：工具发现的新节点也带着它们的
     * 代号进下一轮，模型引用的代号永远查得到映射表）。
     */
    private fun ModelTaskInput.withToolRoundProgress(
        newRounds: List<TutorToolRoundResult>,
        convergedDeclarations: List<TutorToolName>,
        sessionKnowledgeCodes: List<TutorKnowledgeCode>?,
        newLocalActions: List<TutorLocalActionRequest> = emptyList(),
        actionOutcomes: List<com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome> = emptyList(),
        toolRoundsUsed: Int,
    ): ModelTaskInput = when (this) {
        is TutorLobbyInput -> copy(
            toolRoundResults = newRounds,
            toolRoundsUsed = toolRoundsUsed,
            toolDeclarations = convergedDeclarations,
            localActionOutcomes = (localActionOutcomes + actionOutcomes)
                .takeLast(TutorLobbyInput.MAX_LOCAL_ACTION_OUTCOMES),
            // Keep bounded request history independently of actual execution receipts.
            requestedLocalActions = (requestedLocalActions + newLocalActions)
                .distinct()
                .take(TutorLobbyInput.MAX_REQUESTED_LOCAL_ACTIONS),
        )
        is TutorPlanInput -> copy(
            toolRoundResults = newRounds,
            toolDeclarations = convergedDeclarations,
            knowledgeCodes = sessionKnowledgeCodes ?: this.knowledgeCodes,
        )
        is TutorRespondInput -> copy(
            toolRoundResults = newRounds,
            toolDeclarations = convergedDeclarations,
            knowledgeCodes = sessionKnowledgeCodes ?: this.knowledgeCodes,
        )
        else -> this
    }

    /**
     * 把本轮**真实执行结果**折进 agent context：条目 id / 选中的题 / 资产 id / JS 状态 /
     * 动作回执。它只认执行器返回的事实，不解析模型文本；所有列表都有上界。
     */
    private fun ModelTaskInput.withAgentFacts(
        executions: List<TutorToolExecution>,
        actionOutcomes: List<com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome>,
        toolRoundsUsed: Int,
    ): ModelTaskInput {
        val base = agentContextSnapshot() ?: TutorAgentContext()
        val selected = executions.mapNotNull { it.selectedQuestion }.lastOrNull()
        val entries = (base.knownEntryIds + executions.flatMap { it.problemEntryIds } +
            listOfNotNull(selected?.entryId)).distinct().takeLast(180)
        val figures = (base.knownFigureIds + executions.mapNotNull { it.figure?.figureId } +
            executions.flatMap { it.figureIds } +
            executions.mapNotNull { it.feedbackImage?.assetId } +
            executions.flatMap { execution -> execution.feedbackImages.map { it.assetId } } +
            listOfNotNull(selected).flatMap { question -> question.sourceImages.map { it.assetId } })
            .distinct().takeLast(32)
        val feedback = (base.feedbackImages + executions.mapNotNull { it.feedbackImage } +
            executions.flatMap { it.feedbackImages })
            .distinctBy { it.assetId }.takeLast(4)
        val subjects = (base.subjects + listOfNotNull(base.subject, selected?.subject) +
            executions.flatMap { it.subjects }).distinct().takeLast(32)
        val state = executions.lastOrNull { it.javascriptState != null }?.javascriptState
            ?: base.javascriptStateJson
        val outcomes = (base.actionOutcomes + actionOutcomes).takeLast(12)
        val question = selected ?: base.selectedQuestion
        if (entries == base.knownEntryIds && figures == base.knownFigureIds &&
            feedback == base.feedbackImages && subjects == base.subjects &&
            state == base.javascriptStateJson && outcomes == base.actionOutcomes &&
            question == base.selectedQuestion && toolRoundsUsed == base.roundsUsed
        ) {
            return this
        }
        return withAgentContext(
            base.copy(
                subject = executions.mapNotNull { it.selectedSubject }.lastOrNull()
                    ?: selected?.subject ?: base.subject,
                subjects = subjects,
                selectedQuestion = question,
                knownEntryIds = entries,
                knownFigureIds = figures,
                feedbackImages = feedback,
                javascriptStateJson = state,
                actionOutcomes = outcomes,
                roundsUsed = toolRoundsUsed,
            ),
        )
    }

    private suspend fun executeLocalActions(
        request: ModelTaskRequest,
        actions: List<TutorLocalActionRequest>,
        roundOrdinal: Int,
    ): List<com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome> {
        val input = request.input
        val lobby = input as? TutorLobbyInput
        val sessionId = (input as? TutorPlanInput)?.sessionId ?: (input as? TutorRespondInput)?.sessionId
        val conversationId = lobby?.conversationId
            ?: sessionId?.let(TutorConversationIds::captured)
        val selectedCurrent = input.agentContextSnapshot()?.selectedQuestion?.entryId
        val attachedImageIds = input.attachedImageAssetIdsForActions()
        val candidates = toolTraces.current(request.requestId)?.entries.orEmpty()
            .filter { it.tool == TutorToolName.NOTEBOOK_READ }
            .flatMap { it.problemEntryIds }.distinct()
        val outcomes = (lobby?.localActionOutcomes ?: input.agentContextSnapshot()?.actionOutcomes)
            .orEmpty().toMutableList()
        return actions.map { action ->
            val savedIds = outcomes.filter { outcome ->
                outcome.ok == true && outcome.decision == "SUCCEEDED" &&
                    outcome.kind in setOf("SAVE_TO_NOTEBOOK", "OPEN_PROBLEM")
            }.mapNotNull { it.artifactId }
            val knownIds = (candidates + savedIds + listOfNotNull(selectedCurrent) +
                input.agentContextSnapshot()?.knownEntryIds.orEmpty()).toSet()
            val selectedProblem = action.parameters["entryId"]
            val selectedExports = action.parameters["entryIds"]?.split(',')?.map(String::trim)
                ?: candidates.ifEmpty { listOfNotNull(selectedCurrent) }
            val unknownTarget = selectedProblem != null && selectedProblem !in knownIds ||
                selectedExports.any { it.isBlank() || it !in knownIds }
            val executor = localActionExecutor
            val outcome = if (conversationId == null || executor == null || unknownTarget) {
                com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome(
                    kind = action.action.actionId,
                    decision = "FAILED",
                    detail = if (unknownTarget) "指定的题目不在本轮已检索或已保存的条目中，这次未执行。"
                        else "本机当前没有接通这项应用操作。",
                    callId = action.callId,
                    ok = false,
                )
            } else {
                executor.execute(com.tingyun.smartmistakebook.core.domain.TutorLocalActionCommand(
                    logicalOperationId = request.requestId,
                    conversationId = conversationId,
                    request = action,
                    context = com.tingyun.smartmistakebook.core.domain.TutorLocalActionContext(
                        captureSessionId = sessionId,
                        libraryProblemId = selectedProblem ?: selectedCurrent
                        ?: savedIds.lastOrNull() ?: candidates.singleOrNull(),
                        attachedImageAssetIds = attachedImageIds,
                    ),
                    exportEntryIds = selectedExports.distinct(),
                    roundOrdinal = roundOrdinal,
                    allowedBackupRefs = input.agentContextSnapshot()?.backupReferences.orEmpty()
                        .map { backup -> backup.ref },
                ))
            }
            outcomes += outcome
            outcome
        }
    }

    /**
     * 派生前对 Plan/Respond 输入做代号赋码（[TutorKnowledgeCodeRegistry.adopt]：已赋码条目
     * 按原码登记、未赋码条目按列表顺序首次分配），并把教学参考的代号字段填上（材料绑定多个
     * 节点时取其中已披露的第一个）。返回的才是持久化 / 指纹 / 提示词用的形状。
     *
     * 插眼 8（裁决 22 修订二）：会话代号表**显式含本科「未分类」兜底桶**——节点缺则经既有
     * `ensurePseudoKnowledgeNode` 幂等创建（MODEL_CANDIDATE，不进召回面），随后登记进注册表
     * 并追加到本轮 `knowledgeCodes`（模型侧才有桶代号可写）。桶在 adopt **之后**追加：
     * 已赋码的真实条目先按原码登记，桶拿下一个空闲号，历史行的代号编号不漂。
     * 桶的创建/登记失败不阻断本轮派遣（它是罕见兜底，不是主路径）——记录日志、跳过。
     */
    private suspend fun ModelTaskRequest.withSessionKnowledgeCodes(): ModelTaskRequest {
        val input = this.input
        // 大厅现在也能 SELECT_QUESTION 并在后续轮读知识点：给会话建一个空注册表，
        // KNOWLEDGE_READ 才能追加分配代号、MASTERY_UPDATE 的代号白名单也有同一份来源。
        if (input is TutorLobbyInput) {
            knowledgeCodeRegistries.getOrPut(input.conversationId) { TutorKnowledgeCodeRegistry() }
            return this
        }
        val sessionId = when (input) {
            is TutorPlanInput -> input.sessionId
            is TutorRespondInput -> input.sessionId
            else -> return this
        }
        val subject = when (input) {
            is TutorPlanInput -> input.subject
            is TutorRespondInput -> input.subject
            else -> null
        }
        val registry = knowledgeCodeRegistries.getOrPut(sessionId) { TutorKnowledgeCodeRegistry() }
        val bucketEntry = subject?.takeIf(String::isNotBlank)?.let { subjectName ->
            // fail-open 只对**失败**成立：协程取消必须原样穿透，否则"学生停止"会被当成
            // "桶没建好"继续往下走（取消语义在工具环里是一等公民）。
            try {
                database.ensurePseudoKnowledgeNode(subjectName, clock())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w(
                    "TutorKnowledgeContext",
                    "unclassified bucket node was not ensured for subject=$subjectName: $failure",
                )
                null
            }?.let { node ->
                TutorKnowledgeCode(
                    knowledgeNodeId = node.knowledgeNodeId,
                    displayName = node.displayName,
                    role = TutorKnowledgeCodeRole.UNCLASSIFIED_BUCKET,
                )
            }
        }
        return when (input) {
            is TutorPlanInput -> copy(
                input = input.copy(
                    knowledgeCodes = registry.adopt(input.knowledgeCodes)
                        .withUnclassifiedBucket(registry, bucketEntry),
                    reviewedTeachingReferences = input.reviewedTeachingReferences
                        .withSessionCodes(registry),
                ),
            )
            is TutorRespondInput -> copy(
                input = input.copy(
                    knowledgeCodes = registry.adopt(input.knowledgeCodes)
                        .withUnclassifiedBucket(registry, bucketEntry),
                    reviewedTeachingReferences = input.reviewedTeachingReferences
                        .withSessionCodes(registry),
                ),
            )
            else -> this
        }
    }

    /**
     * 追加本科「未分类」桶条目（插眼 8）。已在列表里（上一轮写回的形状）原样返回——
     * `adopt` 会按原码登记，不重复分配。桶条目在**末尾**，会话内稳定（编号只增不动）。
     */
    private fun List<TutorKnowledgeCode>.withUnclassifiedBucket(
        registry: TutorKnowledgeCodeRegistry,
        bucket: TutorKnowledgeCode?,
    ): List<TutorKnowledgeCode> {
        if (bucket == null) return this
        if (any { entry -> entry.knowledgeNodeId == bucket.knowledgeNodeId }) return this
        // 兜底桶不该成为派遣的失败源：会话代号空间耗尽等异常时记录日志、跳过桶
        //（模型这一轮没有桶代号可用，其余代号与工具面不受影响）。取消照常穿透。
        val code = try {
            registry.assign(bucket)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w(
                "TutorKnowledgeContext",
                "unclassified bucket code was not assigned: $failure",
            )
            return this
        }
        return this + bucket.copy(code = code)
    }

    private fun List<TutorTeachingReference>.withSessionCodes(
        registry: TutorKnowledgeCodeRegistry,
    ): List<TutorTeachingReference> = map { reference ->
        val code = reference.code ?: reference.knowledgeNodeIds
            .firstNotNullOfOrNull { nodeId -> registry.codeFor(nodeId) }
        if (code == null) reference else reference.copy(code = code)
    }

}

object ModelTaskRepositoryFactory {
    fun create(
        database: StudyDatabasePort,
        gateway: ModelGateway,
        knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability>,
        /**
         * A1（4B）：工具环 `GENERATE_FIGURE` 的生图通道；null = 本装配没有（测试/未配置），
         * 工具回可见的"不可用"结果。生产在 app 里装配（`TutorFigureGeneratorFactory`）。
         */
        figureGenerator: TutorFigureGenerator? = null,
        figureCapabilities: (suspend () -> com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities)? = null,
        localActionExecutor: com.tingyun.smartmistakebook.core.domain.TutorLocalActionExecutor? = null,
        visualEngine: com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine? = null,
        visualEngineReady: (() -> Boolean)? = null,
        figureInspector: com.tingyun.smartmistakebook.core.data.model.TutorFigureInspector? = null,
        javascriptStateStore: com.tingyun.smartmistakebook.core.domain.TutorJavascriptStateStore? = null,
        resultStore: com.tingyun.smartmistakebook.core.domain.TutorToolResultStore? = null,
        catalogSearch: (suspend (String) -> List<com.tingyun.smartmistakebook.core.model.CatalogHit>)? = null,
    ): ModelTaskRepository = RoomModelTaskRepository(
        database = database,
        gateway = gateway,
        knowledgeBaseAvailability = knowledgeBaseAvailability,
        figureGenerator = figureGenerator,
        figureCapabilities = figureCapabilities,
        localActionExecutor = localActionExecutor,
        visualEngine = visualEngine,
        visualEngineReady = visualEngineReady,
        figureInspector = figureInspector,
        javascriptStateStore = javascriptStateStore,
        resultStore = resultStore,
        catalogSearch = catalogSearch,
    )
}

/** 学生按下停止时用的取消原因：与"采集链被系统回收"这类取消区分得开（诊断与测试都读它）。 */
class StudentStoppedTheTurn : CancellationException("学生停止了这一轮生成")

private class ConcurrentModelTaskTransition : RuntimeException()

private class InvalidProviderProtocol(val userMessage: String) : RuntimeException(userMessage)

/**
 * 远程提供方身份是否一致。
 *
 * `figureGenerationCapabilities` 是本机 coordinator 折进快照的**本地能力位**（JS 引擎/
 * 宿主帧/图像后端），不属于远程模型身份；派遣前把它折进 `declaredProvider` 后，网关的
 * `Started` 事件仍只带远程快照。逐字段比较会把"本地能力已折入"误判成"任务开始后模型
 * 变了"，所以身份比较显式忽略这一列。
 */
internal fun ProviderCapabilitySnapshot.sameRemoteIdentityAs(
    other: ProviderCapabilitySnapshot,
): Boolean =
    copy(figureGenerationCapabilities = null) == other.copy(figureGenerationCapabilities = null)

/** 任务级归因日志的标签（与模型调用/网关日志同一标签，logcat 可按一个标签抓全链路）。 */
private const val MODEL_TASK_LOG_TAG = "SmartMistakeBook"

/**
 * **每一轮** `gateway.execute` 的防呆安全上限（600s）——不是"一条任务 600s"。
 *
 * 覆盖范围（注释口径与实现对齐）：`withTimeout` 只包住一圈 `gateway.execute`（工具环
 * `while (!answered)` 循环体内），而一条任务最多派遣
 * `ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES = 9` 轮（8 轮工具
 * `TutorToolRoundResult.MAX_TOOL_ROUNDS` + 1 轮终答），所以**一条任务**的本地占用上限是
 * 约 6 × 600s = 3_600s。这是上限的上限，不是任何一条任务的时长承诺。
 *
 * 它不是输出预算、也不是时长预算：不参与"这次调用该花多久"的任何规划，只兜住"派发方已经死掉
 * 却没人收尾"的任务，避免一条失控的流把 RUNNING 状态永远留在库里。任何"上游还在正常生成"的
 * 调用都不该被它打断——真正的停滞由传输层读超时（空闲看门狗）与各调用点自己的超时判定。
 *
 * 600s 与传输层的 `CALL_TIMEOUT_SECONDS = 600` 同口径（也与 Claude Code 的
 * `API_TIMEOUT_MS = 600s` 同数）：正常路径上传输层自己的绝对上限先到，这一条只是它缺席时的
 * 防呆；此前 120s 的紧预算会把"推理模型还在思考"的健康调用判成 TIMEOUT（见 KD-33）。
 */
private const val MODEL_TASK_CEILING_MILLIS = 600_000L
private const val MAX_GATEWAY_EVENTS = 64
private const val UNCONFIGURED_PROVIDER_ID = "unconfigured"
private const val DISPATCH_LIMIT_USER_MESSAGE = "这次处理未能完成，请重新开始"

private const val STOPPED_USER_MESSAGE = "已停止"

/** 取消时等待在途 Job 退出的上限：协作式取消正常立刻返回，上限只为不把"停止"卡住。 */
private const val CANCEL_JOIN_TIMEOUT_MILLIS = 5_000L

/** 取消落终态的重试次数：撞上任务自己的转换才有第二次机会。 */
private const val CANCELLED_TRANSITION_ATTEMPTS = 4

/** 痕迹形状解不开时的兜底小字（正常路径走不到：有执行就有条目）。 */
private const val TUTOR_TOOL_TRACE_DONE_FALLBACK = "已查阅"

private val processActiveOperations = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

private val TEACHING_INTENTS = setOf(
    TutorMessageIntent.CURRENT_QUESTION_HELP,
    TutorMessageIntent.AMBIGUOUS,
)

/** Mirrors the document renderer: a whole top-level paragraph, never a quoted/code example. */
internal fun tutorTeachingFigureReferences(markdown: String): List<String> {
    val document = Parser.builder()
        .includeSourceSpans(IncludeSourceSpans.BLOCKS).build().parse(markdown)
    val offsets = buildList {
        add(0)
        markdown.forEachIndexed { index, character -> if (character == '\n') add(index + 1) }
    }
    val references = mutableListOf<String>()
    var displayMathDelimiter: String? = null
    var node = document.firstChild
    val pattern = Regex("\\[\\[figure:([^\\s\\[\\]]{1,256})]]")
    while (node != null) {
        if (node is Paragraph && node.sourceSpans.isNotEmpty()) {
            val first = node.sourceSpans.first()
            val last = node.sourceSpans.last()
            val text = markdown.substring(offsets[first.lineIndex] + first.columnIndex,
                offsets[last.lineIndex] + last.columnIndex + last.length)
            val inMath = displayMathDelimiter != null
            displayMathDelimiter = com.tingyun.smartmistakebook.core.model.SafeInlineMarkdown
                .displayMathDelimiterAfter(text, displayMathDelimiter)
            if (!inMath) pattern.matchEntire(text.trim())?.let { references += it.groupValues[1] }
        }
        node = node.next
    }
    return references
}

internal fun tutorActionProgressText(
    toolHeadline: String?,
    actionOutcomes: List<com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome>,
): String = (listOfNotNull(toolHeadline) + actionOutcomes.map { outcome ->
    (outcome.detail ?: "应用操作状态已更新。").take(512)
}).joinToString("\n").take(TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS)
    .ifBlank { TUTOR_TOOL_TRACE_DONE_FALLBACK }

private fun ModelTaskOutput.deliveryTraceOrNull(): String? = when (this) {
    is TutorPlanOutput -> deliveryToolTraceJson
    is TutorRespondOutput -> deliveryToolTraceJson
    is TutorLobbyOutput -> deliveryToolTraceJson
    else -> null
}
