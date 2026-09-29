package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorAnswerExposureKey
import com.tingyun.smartmistakebook.core.domain.TutorContextComposer
import com.tingyun.smartmistakebook.core.domain.TutorContextWindow
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorChatHistoryEntry
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorSuggestedMove
import com.tingyun.smartmistakebook.core.model.canExposeSolutionFor
import com.tingyun.smartmistakebook.core.model.requiresModelSettings
import com.tingyun.smartmistakebook.core.model.requiresRoundQuestionBinding
import com.tingyun.smartmistakebook.core.ui.AttachedImagesSection
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip

private data class TutorRespondExchangeKey(
    val sessionId: String,
    val revisionNumber: Int,
    val questionDocumentId: String,
    val responseOrdinal: Int,
    val studentMessage: String,
    val visibleTutorContextMarkdown: String?,
    val priorMessages: List<TutorChatHistoryEntry>,
    val requestedMove: TutorMoveType?,
)

private fun TutorRespondInput.exchangeKey() = TutorRespondExchangeKey(
    sessionId = sessionId,
    revisionNumber = draftRevisionNumber,
    questionDocumentId = questionDocument.id,
    responseOrdinal = responseOrdinal,
    studentMessage = studentMessage,
    visibleTutorContextMarkdown = visibleTutorContextMarkdown,
    priorMessages = priorMessages,
    requestedMove = requestedMove,
)

internal fun latestTutorRespondTasks(tasks: List<ModelTaskSnapshot>): List<ModelTaskSnapshot> = tasks
    .filter { it.request.input is TutorRespondInput }
    .groupBy { (it.request.input as TutorRespondInput).exchangeKey() }
    .values
    .map { attempts ->
        attempts.maxWith(
            compareBy<ModelTaskSnapshot>(ModelTaskSnapshot::createdAtEpochMillis)
                .thenBy(ModelTaskSnapshot::updatedAtEpochMillis)
                .thenBy { it.request.requestId },
        )
    }
    .sortedWith(
        compareBy<ModelTaskSnapshot> {
            (it.request.input as TutorRespondInput).responseOrdinal
        }.thenBy(ModelTaskSnapshot::createdAtEpochMillis)
            .thenBy { it.request.requestId },
    )

internal fun ModelTaskSnapshot.canRetryTutorResponse(): Boolean =
    request.input is TutorRespondInput && status == ModelTaskStatus.RETRYABLE_FAILURE

private fun ModelTaskSnapshot.requiresTutorModelSettings(): Boolean {
    val code = failure?.code ?: return false
    return code.requiresModelSettings()
}

/**
 * 讲题会话的**提示词历史**（K1a）：文本只从消息行取，装配走同一条 [TutorContextComposer]。
 *
 * 消灭的失败：此前这里是**第二份**文本源——从模型任务快照重新拼一遍学生消息与助手正文
 * （`input.studentMessage` / `output.messageMarkdown`），与 `tutor_message` 各存一份；
 * 重试、重放、删除之后两边会不一致，而模型读的是快照那一份、学生看的是消息那一份。
 * 现在消息行是唯一权威，未裁剪的整轮由装配器压成摘要（不是先裁再摘要）。
 *
 * 唯一保留的账本读法是**答案门**（见 [hidingUnexposedTutorAnswers]）：曝光事实本来就在
 * 账本上，它决定"学生有没有真的看到完整答案"。
 */
internal fun tutorSessionContext(
    messages: List<TutorMessage>,
    respondTasks: List<ModelTaskSnapshot>,
    answerExposureKeys: Set<TutorAnswerExposureKey>,
): TutorContextWindow = TutorContextComposer.compose(
    messages.hidingUnexposedTutorAnswers(respondTasks, answerExposureKeys),
)

/**
 * 把"揭示了完整答案、而学生还没真的看到"的助手正文换成占位。
 *
 * 门控（与升级前同一条件，逐条列出）：
 * 1. 本地判定这一轮**确实揭示了答案**（`output.solutionRevealed`）；
 * 2. 这一轮的答案**可以被展示**（`canExposeSolutionFor`，含"本轮有没有绑定题"这一维）；
 * 3. 学生**真的看到了**：曝光账本里有这一轮的键——或者这一轮讲的是学生自己附加的题
 *    （附加轮的暴露刻意不落账，见 [TutorSolutionExposureTarget.recordsExposure]）。
 *
 * 三条都过才保留原文；否则换成 [HIDDEN_TUTOR_ANSWER_CONTEXT]——否则模型会以为学生读过了
 * 那段它其实没看到的完整解答。
 */
internal fun List<TutorMessage>.hidingUnexposedTutorAnswers(
    respondTasks: List<ModelTaskSnapshot>,
    answerExposureKeys: Set<TutorAnswerExposureKey>,
): List<TutorMessage> {
    if (isEmpty() || respondTasks.isEmpty()) return this
    val tasksByRequestId = respondTasks.associateBy { task -> task.request.requestId }
    return map { message ->
        if (message.role != TutorMessageRole.ASSISTANT) return@map message
        val task = message.logicalOperationId?.let(tasksByRequestId::get) ?: return@map message
        val input = task.request.input as? TutorRespondInput ?: return@map message
        val output = task.output as? TutorRespondOutput ?: return@map message
        if (!output.solutionRevealed) return@map message
        val answerWasExposed = output.canExposeSolutionFor(
            input,
            requiresRoundQuestionBinding = task.request.requiresRoundQuestionBinding,
        ) && (
            input.attachedQuestion != null ||
                task.toRespondAnswerExposureKey() in answerExposureKeys
            )
        if (answerWasExposed) message else message.copy(bodyMarkdown = HIDDEN_TUTOR_ANSWER_CONTEXT)
    }
}

private const val HIDDEN_TUTOR_ANSWER_CONTEXT =
    "上一条讲解包含完整答案，但学生还没有完整看到；不要假设学生已经读过答案。"
private const val UNAUTHORIZED_TUTOR_ANSWER_MESSAGE =
    "我先不直接展开完整答案。你可以继续问当前步骤，或者点“看完整讲解”。"
private val LOCAL_REVEAL_SOLUTION_MOVE = TutorSuggestedMove(
    id = "local-reveal-solution",
    label = "看完整讲解",
    type = TutorMoveType.REVEAL_SOLUTION,
)

/**
 * Recovers the most recent contiguous suffix of exact student messages from persisted requests.
 * Model output and task success are intentionally irrelevant: an omitted or failed reply cannot
 * erase what the student actually said.
 */
internal fun priorCycleStudentMessages(tasks: List<ModelTaskSnapshot>): List<String> {
    val latestByOrdinal = latestTutorRespondTasks(tasks)
        .groupBy { task -> (task.request.input as TutorRespondInput).responseOrdinal }
        .values
        .map { sameOrdinal ->
            sameOrdinal.maxWith(
                compareBy<ModelTaskSnapshot>(ModelTaskSnapshot::createdAtEpochMillis)
                    .thenBy(ModelTaskSnapshot::updatedAtEpochMillis)
                    .thenBy { task -> task.request.requestId },
            )
        }
        .sortedBy { task -> (task.request.input as TutorRespondInput).responseOrdinal }
    var expectedOrdinal = (latestByOrdinal.lastOrNull()?.request?.input as? TutorRespondInput)
        ?.responseOrdinal
        ?: return emptyList()
    var retainedChars = 0
    return buildList {
        for (task in latestByOrdinal.asReversed()) {
            val input = task.request.input as TutorRespondInput
            if (input.responseOrdinal != expectedOrdinal) break
            val message = input.studentMessage
            if (
                size >= TutorPlanInput.MAX_PRIOR_CYCLE_STUDENT_MESSAGES ||
                retainedChars + message.length >
                TutorPlanInput.MAX_PRIOR_CYCLE_STUDENT_MESSAGE_CHARS
            ) {
                break
            }
            add(message)
            retainedChars += message.length
            expectedOrdinal -= 1
        }
    }.asReversed()
}

/**
 * 一轮讲题的渲染：学生气泡 + 助手回复，两件都走交互面那一套共用件（[TutorSurfaceStudentBubble] /
 * [TutorSurfaceAssistantReply]）。
 *
 * 消灭的失败：这一处此前自己画了两套气泡（另一种圆角、另一种底色、另一套失败卡），与智能体栏
 * 的同一条回复长得不一样；重试按钮的文案在这里叫"重试"、在智能体栏叫"重新发送"。现在视觉与
 * 文案只有一套，本函数只剩"把这一轮的状态翻译成那套件要的形状"。
 */
@Composable
internal fun TutorChatExchange(
    task: ModelTaskSnapshot,
    /**
     * 学生气泡正文（K1a：消息行是唯一文本权威）。null = 这一轮的学生行没有落库，
     * 气泡整段不渲染——**不回落派发请求里的原文**，否则同一句话会有第二个来源。
     */
    studentBodyMarkdown: String?,
    /** 助手正文；null = 这一轮没有消息行（同样不回落账本）。 */
    assistantBodyMarkdown: String?,
    /** 思考块正文；旧行回落到账本。 */
    assistantThinkingMarkdown: String?,
    /**
     * 这一轮的工具痕迹（B1，来自助手消息行）：加粗灰色小字内联、点开可看详情（含被拒理由）。
     * null = 这一轮没有发起工具调用（或旧行没有痕迹）。
     */
    assistantToolTraceJson: String? = null,
    awaitingContinuation: Boolean = false,
    interactionEnabled: Boolean,
    recoveryEnabled: Boolean,
    executionMatchesCurrentProvider: Boolean,
    onRetry: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onMove: (TutorSuggestedMove) -> Unit,
    onRevealSolution: (TutorSuggestedMove) -> Unit,
    attachedImageResolver: (suspend (AttachedImage) -> String?)? = null,
    /** 学生消息附图的规范资产读取器；为 null 时不渲染气泡里的图片。 */
    studentImageIntake: LobbyMessageImageIntake? = null,
    assistantBottomModifier: Modifier = Modifier,
    /**
     * 助手动作条（A2：复制 / 已停止）的标签；null = 按轮次号给讲题侧那一个
     * （`tutor_chat_message_actions_<轮次号>`）。
     */
    replyActionTestTag: String? = null,
    modifier: Modifier = Modifier,
) {
    val input = task.request.input as TutorRespondInput
    val output = task.output as? TutorRespondOutput
    val replyTestTag = "tutor_chat_assistant_${input.responseOrdinal}"
    val actionTestTag = replyActionTestTag
        ?: "tutor_chat_message_actions_${input.responseOrdinal}"
    val succeededOutput = if (task.status == ModelTaskStatus.SUCCEEDED) output else null
    val answerMustStayHidden = succeededOutput != null && succeededOutput.solutionRevealed &&
        !succeededOutput.canExposeSolutionFor(
            input,
            requiresRoundQuestionBinding = task.request.requiresRoundQuestionBinding,
        )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        studentBodyMarkdown?.let { studentMessage ->
            TutorSurfaceStudentBubble(
                body = studentMessage,
                imageAssetIds = input.studentImageAssetRefs,
                imageIntake = studentImageIntake,
                testTag = "tutor_chat_user_${input.responseOrdinal}",
            )
        }
        when {
            task.status == ModelTaskStatus.SUCCEEDED && succeededOutput == null -> {
                TutorSurfaceAssistantReply(
                    bodyMarkdown = null,
                    testTag = replyTestTag,
                    failure = TutorSurfaceFailure(detail = TUTOR_REPLY_INCOMPLETE_DETAIL),
                )
            }

            answerMustStayHidden -> {
                TutorSurfaceAssistantReply(
                    bodyMarkdown = UNAUTHORIZED_TUTOR_ANSWER_MESSAGE,
                    testTag = replyTestTag,
                    extras = {
                        if (interactionEnabled) {
                            OutlineActionChip(
                                text = LOCAL_REVEAL_SOLUTION_MOVE.label,
                                onClick = {
                                    onRevealSolution(LOCAL_REVEAL_SOLUTION_MOVE)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tutor_chat_local_reveal_solution"),
                            )
                        }
                    },
                )
            }

            succeededOutput != null -> {
                val ready = succeededOutput
                TutorSurfaceAssistantReply(
                    bodyMarkdown = assistantBodyMarkdown,
                    thinkingMarkdown = assistantThinkingMarkdown,
                    // B1：这一轮查阅了什么的痕迹，跟着这一轮的回复一起出现（不混进思考卡）。
                    toolTraceJson = assistantToolTraceJson,
                    testTag = replyTestTag,
                    // 答案曝光的定位点：只有真揭示了完整答案的那一轮才有它
                    // （"精确底部进视口"才落账，见 TutorSolutionExposureTracker）。
                    bottomAnchor = assistantBottomModifier.takeIf { ready.solutionRevealed },
                    extras = {
                        attachedImageResolver?.let { resolver ->
                            ready.attachedImages
                                .takeIf { it.isNotEmpty() }
                                ?.let { images ->
                                    AttachedImagesSection(
                                        images = images,
                                        resolve = resolver,
                                    )
                                }
                        }
                        if (interactionEnabled) {
                            ready.suggestedMoves.forEach { move ->
                                OutlineActionChip(
                                    text = move.label,
                                    onClick = {
                                        if (move.type == TutorMoveType.REVEAL_SOLUTION) {
                                            onRevealSolution(move)
                                        } else {
                                            onMove(move)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("tutor_chat_move_${move.id}"),
                                )
                            }
                        }
                        // 动作条（A2）：讲题入口与智能体栏同一条——「复制」复制的是消息行里的
                        // **原文 markdown**（渲染后的文本会丢格式）。重试沿用既有的重发口径
                        // （只有可重试的那一轮才有），所以这一条不会长出一个点了没反应的按钮。
                        TutorReplyActionBar(
                            shape = tutorReplyActionShape(
                                bodyMarkdown = assistantBodyMarkdown,
                                retryable = interactionEnabled && task.canRetryTutorResponse(),
                            ),
                            onRetry = onRetry,
                            testTagPrefix = actionTestTag,
                        )
                    },
                )
            }

            // 学生按了停止（A2）：一行灰字「已停止」，**不是**失败卡的红字——这一轮不是模型
            // 失败，也不是学生做错了什么。这一轮没有答出来的正文，所以没有「复制」可给。
            task.status == ModelTaskStatus.CANCELLED -> TutorSurfaceAssistantReply(
                bodyMarkdown = null,
                testTag = replyTestTag,
                extras = {
                    TutorReplyActionBar(
                        shape = tutorReplyActionShape(
                            bodyMarkdown = null,
                            stopped = true,
                        ),
                        testTagPrefix = actionTestTag,
                    )
                },
            )

            task.status == ModelTaskStatus.RETRYABLE_FAILURE ||
                task.status == ModelTaskStatus.PERMANENT_FAILURE ||
                task.status == ModelTaskStatus.CANCELLED -> {
                val settingsRequired = task.failure?.code?.requiresModelSettings() == true
                val actionLabel = when {
                    recoveryEnabled && executionMatchesCurrentProvider && settingsRequired ->
                        TUTOR_SURFACE_OPEN_SETTINGS_LABEL
                    interactionEnabled && task.canRetryTutorResponse() -> TUTOR_SURFACE_RETRY_LABEL
                    else -> null
                }
                TutorSurfaceAssistantReply(
                    bodyMarkdown = null,
                    testTag = replyTestTag,
                    failure = TutorSurfaceFailure(
                        detail = when {
                            !executionMatchesCurrentProvider -> "旧配置中的回复没有完成。"
                            settingsRequired -> "模型设置需要更新，题目已经保存。"
                            else -> TUTOR_REPLY_INCOMPLETE_DETAIL
                        },
                        primaryActionLabel = actionLabel,
                        primaryActionTestTag = if (settingsRequired) {
                            "tutor_chat_model_settings"
                        } else {
                            "tutor_chat_retry"
                        },
                        onPrimaryAction = if (settingsRequired) {
                            onOpenModelSettings
                        } else {
                            onRetry
                        },
                    ),
                )
            }

            else -> {
                val progress = when {
                    executionMatchesCurrentProvider && awaitingContinuation -> TutorSurfaceProgress(
                        text = "回复已暂停，点下方“继续对话”后接着完成。",
                        textTestTag = "tutor_chat_reply_paused",
                        spinning = false,
                    )
                    // 逐 token 的思考链与回答正文由屏幕组件的在途区统一渲染（同一条实时流，
                    // 与智能体栏同一套）：这里只留一个"还在生成"的紧凑指示。
                    executionMatchesCurrentProvider -> TutorSurfaceProgress(
                        text = TUTOR_LIVE_PLACEHOLDER,
                        spinnerTestTag = "tutor_chat_reply_progress",
                    )

                    else -> TutorSurfaceProgress(
                        text = "旧配置中的回复未完成",
                        textTestTag = "tutor_chat_legacy_incomplete",
                        spinning = false,
                    )
                }
                TutorSurfaceAssistantReply(
                    bodyMarkdown = null,
                    testTag = replyTestTag,
                    progress = progress,
                )
            }
        }
    }
}

/**
 * 失败卡的正文：内容不在快照里（输出缺失）或模型没给出可用的回复时，学生看到的就是这一句。
 */
private const val TUTOR_REPLY_INCOMPLETE_DETAIL = "这次回复没有完成。"
