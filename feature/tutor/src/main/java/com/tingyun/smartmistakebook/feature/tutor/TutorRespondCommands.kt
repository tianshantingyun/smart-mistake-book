package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorRoundQuestionBindingPolicy
import com.tingyun.smartmistakebook.core.domain.BindStudentMessageQuestionCommand
import com.tingyun.smartmistakebook.core.model.AttachedRoundQuestion
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.AppFailure
import com.tingyun.smartmistakebook.core.domain.TutorAnswerExposureKey
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.Retryability
import com.tingyun.smartmistakebook.core.model.appFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun tutorRespondInProgressError(): AppFailure = appFailure(
    code = AppFailureCode.DISPATCH_BUDGET_EXHAUSTED,
    title = TUTOR_RESPOND_IN_PROGRESS_TITLE,
    message = TUTOR_RESPOND_IN_PROGRESS_MESSAGE,
    dataPreserved = true,
    retryability = Retryability.RETRYABLE,
    primaryAction = ActionType.RETRY,
)

internal fun tutorRespondLimitError(): AppFailure = appFailure(
    code = AppFailureCode.DISPATCH_BUDGET_EXHAUSTED,
    title = TUTOR_RESPOND_LIMIT_TITLE,
    message = TUTOR_RESPOND_LIMIT_MESSAGE,
    dataPreserved = true,
    retryability = Retryability.RETRYABLE,
    primaryAction = ActionType.RETRY,
)

/**
 * **派发槽位号**分配：该会话全部 RESPOND 任务（不按题过滤）里最大的号 +1。
 *
 * 消灭的失败：此前序号按当前题派生（只在该题的任务里取 max），同一会话换了题就从 1 重新
 * 开始，而 `model_task` 的唯一槽是
 * `(subject_id, task_kind, tutor_response_ordinal)`，于是"会话里的第二道题"的第一轮
 * 会与第一道题的第一轮撞槽。
 *
 * **它不是消息序号**（K1c 之后两者分工明确）：`tutor_message.ordinal` 是会话级单调 ordinal，
 * 由会话行 `last_turn_ordinal` 统一分配（`TutorConversationDao.appendStudentMessage` 与
 * 轮次行 `TutorInteractionDao.ensureRoundRow`），不再由本函数的号算术派生
 * （旧的 `responseOrdinal * 2 - 1` 已废弃）。本函数的号只用来占一次派发槽位、
 * 并进入请求标识（重试同号 = 同一次派发）。
 *
 * [sessionId] 由本函数自己过滤，而不是只信调用方给的那份列表：传进来一份"全库任务"不会
 * 静默算出一个把别的会话的号也数进去的错号，而是照常只按本会话算。
 *
 * 空会话（首轮）返回 1。旧行照常读：本函数只影响新分配，历史行的号不变。
 */
internal fun nextTutorResponseOrdinal(
    sessionId: String,
    sessionRespondTasks: List<ModelTaskSnapshot>,
): Int {
    require(sessionId.isNotBlank()) { "Tutor response session id must not be blank" }
    val highest = sessionRespondTasks.maxOfOrNull { task ->
        val input = task.request.input as? TutorRespondInput
        if (input?.sessionId == sessionId) input.responseOrdinal else 0
    } ?: 0
    return highest + 1
}

internal fun tutorRespondValidationError(): AppFailure = appFailure(
    code = AppFailureCode.VALIDATION_FAILED,
    title = TUTOR_RESPOND_VALIDATION_TITLE,
    message = TUTOR_RESPOND_VALIDATION_MESSAGE,
    dataPreserved = true,
)

/**
 * 当前模型看不了图：去掉图片或换模型，不给重试——重试会带上同一张图，必然再失败一次。
 */
internal fun tutorRespondImageUnsupportedError(): AppFailure = appFailure(
    code = AppFailureCode.PROVIDER_CAPABILITY_MISMATCH,
    title = "当前模型不支持看图",
    message = "去掉图片或更换支持图片的模型后再发送。",
    dataPreserved = true,
    primaryAction = ActionType.OPEN_SETTINGS,
)

/** 图片没能进资产库：说清楚是图片的问题，而不是谎称网络故障。 */
internal fun tutorRespondImageIntakeError(): AppFailure = appFailure(
    code = AppFailureCode.VALIDATION_FAILED,
    title = "这张图片没有准备好",
    message = "图片暂时读不出来，换一张或去掉后再发送。",
    dataPreserved = true,
)

internal fun tutorRespondNetworkError(): AppFailure = appFailure(
    code = AppFailureCode.NETWORK_UNAVAILABLE,
    title = TUTOR_RESPOND_NETWORK_TITLE,
    message = TUTOR_RESPOND_NETWORK_MESSAGE,
    dataPreserved = true,
    retryability = Retryability.RETRYABLE,
    primaryAction = ActionType.RETRY,
)

internal class TutorRespondCommands(
    private val scope: CoroutineScope,
    private val sink: TutorRespondSink,
) {
    fun collect(
        request: ModelTaskRequest,
        clearDraftOnPersist: Boolean,
        allowExternalEnvelopeForLocalRecovery: Boolean = false,
        isRetry: Boolean = false,
    ) {
        val provider = sink.currentProvider() ?: return
        if (
            !tutorRespondCollectCanStart(
                provider = provider,
                requestHasEgressManifest = request.egressManifest != null,
                allowExternalEnvelopeForLocalRecovery = allowExternalEnvelopeForLocalRecovery,
                chatSubmitPending = sink.chatSubmitPending(),
            )
        ) {
            return
        }
        val logicalOperationId = request.requestId
        val messageId = request.requestId
        when (
            val advance = tutorRespondSendAdvance(
                sendState = sink.tutorSendState(),
                logicalOperationId = logicalOperationId,
                messageId = messageId,
                isRetry = isRetry,
            )
        ) {
            TutorRespondSendAdvance.InProgressBudgetExhausted -> {
                sink.setChatStartError(tutorRespondInProgressError())
                return
            }
            TutorRespondSendAdvance.DispatchLimitReached -> {
                sink.setChatStartError(tutorRespondLimitError())
                return
            }
            is TutorRespondSendAdvance.Ready -> sink.setTutorSendState(advance.nextState)
        }
        sink.setChatSubmitPending(true)
        sink.setLocallyStartedRespondRequestId(request.requestId)
        sink.setChatStartError(null)
        scope.launch {
            var persisted = false
            try {
                recordStudentTurnIfNeeded(request)
                sink.modelTasks.execute(request).collect { snapshot ->
                    if (!persisted) {
                        persisted = true
                        if (clearDraftOnPersist) sink.setChatDraft("")
                    }
                    when (snapshot.status) {
                        ModelTaskStatus.SUCCEEDED -> {
                            bindRoundQuestionIfNeeded(request, snapshot)
                            recordAssistantTurnIfNeeded(request, snapshot)
                            sink.setTutorSendState(
                                TutorTurnSendStateMachine.reduce(
                                    sink.tutorSendState(),
                                    TutorSendAction.DispatchSucceeded(logicalOperationId, messageId),
                                ),
                            )
                        }
                        ModelTaskStatus.RETRYABLE_FAILURE -> sink.setTutorSendState(
                            TutorTurnSendStateMachine.reduce(
                                sink.tutorSendState(),
                                TutorSendAction.DispatchFailed(
                                    logicalOperationId,
                                    messageId,
                                    errorCode = snapshot.failure?.code?.name ?: "UNKNOWN",
                                    retryable = true,
                                ),
                            ),
                        )
                        ModelTaskStatus.PERMANENT_FAILURE,
                        ModelTaskStatus.CANCELLED,
                        -> sink.setTutorSendState(
                            TutorTurnSendStateMachine.reduce(
                                sink.tutorSendState(),
                                TutorSendAction.DispatchFailed(
                                    logicalOperationId,
                                    messageId,
                                    errorCode = snapshot.failure?.code?.name ?: "UNKNOWN",
                                    retryable = false,
                                ),
                            ),
                        )
                        else -> Unit
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sink.setChatStartError(tutorRespondNetworkError())
            } finally {
                sink.setChatSubmitPending(false)
            }
        }
    }

    /**
     * 派发一轮学生消息。返回 **true 表示这一轮真的派出去了**（请求已交给 [collect]）。
     *
     * 前置不满足、或构造期校验不过时返回 false，同时如实报错（[TutorRespondSink.setChatStartError]）。
     * 调用方据此决定"随消息带出的附件"要不要清空：被拒的一轮没带走任何东西，清了就是把学生
     * 刚附加的题静默丢掉（见 `CapturedTutorSessionInstrumentedTest#aRejectedSendKeepsTheAttachedQuestion…`）。
     */
    fun execute(
        message: String,
        requestedMove: TutorMoveType? = null,
        clearDraftOnPersist: Boolean = false,
        studentImageAssetIds: List<String> = emptyList(),
        /** 本轮候选菜单（界面在派发前组好：显式添加 + 上一轮绑定 + 本地检索）。 */
        boundQuestionCandidates: List<RelatedProblemCandidate> = emptyList(),
        /**
         * 本轮请求侧已知的题锚（同上两个来源里的**那一道**，不是候选集）。写工具门控在
         * "模型没有复述题锚"时回退到它；为空即真的无题轮，写工具照旧被拒。
         */
        knownRoundQuestion: RelatedProblemCandidate? = null,
        /** 学生本轮显式添加的题（加号里的"从错题库选择"）；本轮按它讲。 */
        attachedQuestion: AttachedRoundQuestion? = null,
    ): Boolean {
        // 纯图消息给一句可读的兜底文本：消息体不能为空，且落库、指纹与派发必须用同一份文本，
        // 否则"学生看到的"和"模型读到的"会不是同一条消息。
        val effectiveMessage = message.ifBlank { TUTOR_RESPOND_IMAGE_ONLY_MESSAGE }
        if (
            !tutorRespondExecuteCanStart(
                hasPlanOutput = sink.currentPlanOutput() != null,
                providerCanExecute = tutorRespondProviderCanExecute(sink.currentProvider()),
                messageBlank = effectiveMessage.isBlank(),
                chatSending = sink.chatSending(),
            )
        ) {
            return false
        }
        val visiblePlan = sink.currentPlanOutput() ?: return false
        val provider = sink.currentProvider() ?: return false
        val question = sink.question()
        val respondTasks = sink.tutorRespondTasks()
        val sessionRespondTasks = sink.sessionRespondTasks()
        // 轮次号由**会话层**统一分配：取该会话全部 RESPOND 任务的最大号 +1，而不是只取
        // "当前这道题"的。按题分配会在同一会话跨题时从 1 重新开始，而 `model_task` 的唯一槽
        // 是 `(subject_id, task_kind, tutor_response_ordinal)`、`subject_id` 就是会话 id——
        // 于是第二道题的第一轮会与第一道题的第一轮撞槽（`ModelTaskTransactionDao.create`
        // 的槽位校验），落库直接抛冲突；`tutor_message` 的 `(conversation_id, ordinal)` 同理
        // （ordinal = responseOrdinal*2-1）。
        val responseOrdinal = nextTutorResponseOrdinal(question.sessionId, sessionRespondTasks)
        // 原样保留能装下的轮次，装不下的压成摘要：长会话里模型不再"忘记"前面讲过什么。
        // 文本从**消息流**取（K1a 唯一对话文本权威），装配仍走同一条 TutorContextComposer；
        // 只有"完整答案学生还没真的看到"这一道闸门读账本（曝光事实本来就在账本上）。
        val context = tutorSessionContext(
            messages = sink.sessionMessages(),
            respondTasks = respondTasks,
            answerExposureKeys = sink.answerExposureKeys(),
        )
        val currentInput = sink.currentInput()
        val visibleContext = visibleTutorContextMarkdown(
            visiblePlan,
            sink.currentResponse(),
            answerWasExposed = sink.observedTask().toPlanAnswerExposureKey() in sink.answerExposureKeys(),
        )
        // 重试计数与会话同口径：同一个会话轮次号下的多次尝试都算同一次的重试。
        val attempt = sessionRespondTasks.count { task ->
            (task.request.input as? TutorRespondInput)?.responseOrdinal == responseOrdinal
        }
        val requestId = tutorRespondRequestId(
            question = question,
            provider = provider,
            responseOrdinal = responseOrdinal,
            cycleOrdinal = currentInput.cycleOrdinal,
            turnOrdinal = currentInput.turnOrdinal,
            studentMessage = effectiveMessage,
            visibleTutorContextMarkdown = visibleContext,
            priorMessages = context.recent,
            priorDigest = context.digest,
            requestedMove = requestedMove,
            studentImageAssetIds = studentImageAssetIds,
            attachedQuestion = attachedQuestion,
            attempt = attempt,
        )
        val occurredAt = maxOf(
            sink.clock(),
            sessionRespondTasks.maxOfOrNull { it.createdAtEpochMillis + 1 } ?: 0L,
        )
        val request = try {
            buildTutorRespondRequest(
                question = question,
                profile = sink.profile(),
                provider = provider,
                requestId = requestId,
                occurredAtEpochMillis = occurredAt,
                responseOrdinal = responseOrdinal,
                cycleOrdinal = currentInput.cycleOrdinal,
                turnOrdinal = currentInput.turnOrdinal,
                studentMessage = effectiveMessage,
                visibleTutorContextMarkdown = visibleContext,
                priorMessages = context.recent,
                priorDigest = context.digest,
                requestedMove = requestedMove,
                studentImageAssetIds = studentImageAssetIds,
                boundQuestionCandidates = boundQuestionCandidates,
                knownRoundQuestion = knownRoundQuestion,
                attachedQuestion = attachedQuestion,
            )
        } catch (_: IllegalArgumentException) {
            sink.setChatStartError(tutorRespondValidationError())
            return false
        }
        collect(
            request = request,
            clearDraftOnPersist = clearDraftOnPersist,
        )
        return true
    }

    /**
     * 把这一轮的**助手正文与思考块**落进 `tutor_message`（K1a：消息行是唯一对话文本权威）。
     *
     * 消灭的失败：切换之前，助手正文只活在 `model_task.output_snapshot` 里，而学生气泡与
     * 讲题区正文都从快照渲染——同一条会话文本有两个去处（消息行、快照），重试/重放/删除之后
     * 两边会不一致；历史列表的标题、会话导出的文本也读不到这一轮的正文。落库后：
     * 渲染、标题、提示词历史读的是同一行。
     *
     * 幂等由消息 id（= 请求 id 派生）保证：同一次派发重放多少次都只有一行。失败/取消的轮次
     * 不落行——那一轮没有正文，只有账本上的状态（界面据此渲染失败卡）。
     */
    private suspend fun recordAssistantTurnIfNeeded(
        request: ModelTaskRequest,
        snapshot: ModelTaskSnapshot,
    ) {
        val conversations = sink.conversations ?: return
        val input = request.input as? TutorRespondInput ?: return
        val output = snapshot.output as? TutorRespondOutput ?: return
        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = input.sessionId,
            questionDocumentId = input.questionDocument.id,
            revisionNumber = input.draftRevisionNumber,
            questionTitle = input.questionDocument.title,
            requestId = request.requestId,
            // 助手行回复的是这一轮学生消息（同一次逻辑操作的学生行）。
            replyToMessageId = tutorStudentMessageId(request.requestId),
            bodyMarkdown = output.messageMarkdown,
            thinkingMarkdown = output.thinkingMarkdown,
            occurredAtEpochMillis = request.occurredAtEpochMillis,
            completedAtEpochMillis = snapshot.updatedAtEpochMillis,
        )
    }

    /**
     * 本轮绑定的题落到消息层。
     *
     * 时机：只能在**模型回复到手之后**——绑定 = 模型声明 + 本地两条校验，两者都要等回复。
     * 学生消息行在派发前就已落库（写侧门控的引文语料靠它），所以这里是一次后写。
     *
     * 契约：
     * - 校验不过（`resolvedRoundQuestion` 为 null）→ 什么都不写，本轮就是无题轮；
     * - 校验过了 → 写 `problemId` + `problemRevisionId` 两列到学生消息行；
     * - 落库失败不阻断对话（与 [recordStudentTurnIfNeeded] 同一条纪律：面板不该因内部记账
     *   报错而崩），但**留日志**——绑定悄悄丢掉的后果是下一轮的"上一轮绑定"候选凭空消失。
     */
    private suspend fun bindRoundQuestionIfNeeded(
        request: ModelTaskRequest,
        snapshot: ModelTaskSnapshot,
    ) {
        val conversations = sink.conversations ?: return
        val input = request.input as? TutorRespondInput ?: return
        val output = snapshot.output as? TutorRespondOutput ?: return
        // 解析层已经把模型声明核过一遍（`output.boundQuestion` 就是核过的结果）。这里**再核
        // 一遍**：落库的是耐久记录，而"解析层是唯一写入口"是对**生产**路径成立的假设——测试替身
        // 与将来任何直接构造输出的地方都不受它约束。一次幂等的重核换来的是"写进消息层的绑定
        // 一定通过两条本地校验"，代价只是重复一次纯函数调用。
        val binding = TutorRoundQuestionBindingPolicy.resolve(
            candidates = input.boundQuestionCandidates,
            declaration = output.boundQuestion,
            studentMessage = input.studentMessage,
            // 学生显式附加了题的一轮：附加题被钉住，落库的绑定因此不可能指向菜单里的别的题
            // （与解析层同一把闸门——这里是耐久记录前的第二次裁决）。
            pinnedQuestion = input.attachedQuestion?.toCandidate(),
        ) ?: return
        runCatching {
            conversations.bindStudentMessageQuestion(
                BindStudentMessageQuestionCommand(
                    messageId = "tutor-message:${request.requestId}",
                    boundProblemId = binding.problemId,
                    boundProblemRevisionId = binding.problemRevisionId,
                ),
            )
        }.onFailure { failure ->
            android.util.Log.w(
                "TutorRespond",
                "Round question binding was not persisted",
                failure,
            )
        }
    }

    /**
     * 把学生这一轮的文字落进 `tutor_message` 的 STUDENT 行。
     *
     * 消灭的失败：讲题会话里学生打的字此前只存在模型任务快照里，而写侧门控核对"模型有没有
     * 真引用学生的话"时读的正是 STUDENT 行 —— 语料恒为空，于是提示词承诺的"引文会被本地
     * 逐条比对"对纯文字（开放式）作答形同虚设，模型判 MASTERED 也机械不可达。
     * 落库后，锚核对才对**所有**模型交互生效，不再只管选择题。
     *
     * 会话行**按需创建**（[ensureStudentConversation]）：错题讲题页此前没有任何一方建过这
     * 一行——`TutorSessionViewModel` 只为拍照会话建——而 `tutor_message.conversation_id`
     * 上有外键。于是那条路径上每一次 append 都抛（外键 787）并被下面的 `runCatching` 吞掉：
     * 学生打了字、模型也回了，`tutor_message` 里一行 STUDENT 都没有，写侧门控的引文核对
     * 永远是空语料。会话由这条写入自己保证，就不再依赖"某个入口记得先建"。
     *
     * 簿记失败不阻断发信：面板不该因内部记账报错而发不出去；写侧门控对空语料是
     * fail-closed（该次正向写不进去），学生仍能正常对话。失败留一条日志，不再无声无息。
     */
    private suspend fun recordStudentTurnIfNeeded(request: ModelTaskRequest) {
        val conversations = sink.conversations ?: return
        val input = request.input as? TutorRespondInput ?: return
        val message = input.studentMessage.takeIf(String::isNotBlank) ?: return
        runCatching {
            ensureStudentConversation(conversations, input, request.occurredAtEpochMillis)
            conversations.appendStudentMessage(
                AppendTutorStudentMessageCommand(
                    conversationId = TutorConversationIds.captured(input.sessionId),
                    messageId = "tutor-message:${request.requestId}",
                    // 会话级单调 ordinal（K1c）：由会话计数器分配（`null`），不再由派发槽位号
                    // 算术派生（旧的 `responseOrdinal * 2 - 1`）。序号只影响排序与唯一键，
                    // 幂等由 message_id（= 请求 id）保证——重放同一条请求读回的是同一行。
                    bodyMarkdown = message,
                    logicalOperationId = request.requestId,
                    createdAtEpochMillis = request.occurredAtEpochMillis,
                    sourceImageAssetIds = input.studentImageAssetRefs,
                ),
            )
        }.onFailure { failure ->
            android.util.Log.w(
                "TutorRespond",
                "Student turn was not persisted to tutor_message",
                failure,
            )
        }
    }

    /**
     * 会话行不存在时按本轮题面派生锚点建一行；已存在时原样复用——同锚点幂等、异锚点冲突，
     * 所以只有"确认没有"的时候才建，不能无条件建。
     */
    private suspend fun ensureStudentConversation(
        conversations: TutorConversationRepository,
        input: TutorRespondInput,
        occurredAtEpochMillis: Long,
    ) {
        ensureTutorConversation(
            conversations = conversations,
            sessionId = input.sessionId,
            // 与会话页同一口径：锚点 = 本轮题面 + 题面修订号。
            questionDocumentId = input.questionDocument.id,
            revisionNumber = input.draftRevisionNumber,
            title = input.questionDocument.title,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
    }

    fun retry(task: ModelTaskSnapshot) {
        if (!task.canRetryTutorResponse()) return
        val provider = sink.currentProvider()?.takeIf(::tutorRespondProviderCanExecute) ?: return
        val request = when (provider.executionLocation) {
            // 全局同意下，持久化的任务可直接重新 collect；gate 在 collect() 内再查一次。
            ModelExecutionLocation.EXTERNAL_PROVIDER -> task.request
            ModelExecutionLocation.LOCAL_NO_EGRESS -> if (
                task.request.egressManifest == null && task.matchesTutorProvider(provider)
            ) {
                task.request
            } else {
                return
            }
            ModelExecutionLocation.UNAVAILABLE -> return
        }
        if (sink.chatSubmitPending()) return
        collect(
            request = request,
            clearDraftOnPersist = false,
            isRetry = true,
        )
    }
}

internal class TutorRespondSink(
    val currentProvider: () -> ProviderCapabilitySnapshot?,
    val question: () -> TutorQuestionContext,
    val profile: () -> StudyProfileOverview,
    val clock: () -> Long,
    val chatSubmitPending: () -> Boolean,
    val setChatSubmitPending: (Boolean) -> Unit,
    val tutorSendState: () -> TutorSendState,
    val setTutorSendState: (TutorSendState) -> Unit,
    val setChatStartError: (AppFailure?) -> Unit,
    val setLocallyStartedRespondRequestId: (String?) -> Unit,
    val setChatDraft: (String) -> Unit,
    val currentPlanOutput: () -> TutorPlanOutput?,
    val currentResponse: () -> TutorTurnResponse?,
    val currentInput: () -> TutorPlanInput,
    val observedTask: () -> ModelTaskSnapshot,
    /** 当前**这道题**的 RESPOND 任务：只用于拼上下文（历史、摘要、重试计数）。 */
    val tutorRespondTasks: () -> List<ModelTaskSnapshot>,
    /**
     * 当前**会话**的 RESPOND 任务（不按题过滤）：轮次号由它统一分配，所以跨题也必须单调。
     * 与 [tutorRespondTasks] 是两个口径，不能互换——一个是"这道题聊过什么"，一个是"这个
     * 会话走到第几轮"。
     */
    val sessionRespondTasks: () -> List<ModelTaskSnapshot>,
    val answerExposureKeys: () -> Set<TutorAnswerExposureKey>,
    val chatSending: () -> Boolean,
    val modelTasks: ModelTaskRepository,
    /**
     * 学生文字落库用的会话仓库。null 表示该界面不提供（例如测试替身）——
     * 此时不落库，写侧门控按空语料 fail-closed，不会误放行任何正向判定。
     */
    val conversations: TutorConversationRepository? = null,
    /**
     * 这条会话的消息流（K1a 唯一文本权威）：提示词历史从它装配。
     *
     * 与 [tutorRespondTasks] 是两个不同的东西，不能互换：快照是**派发台账**（状态、重试、
     * 结构化载荷），消息流是**对话事实**。读的时候要从 backing state 取（组合期快照会停在
     * 第一帧）。
     */
    val sessionMessages: () -> List<com.tingyun.smartmistakebook.core.domain.TutorMessage> = {
        emptyList()
    },
)
