package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput

internal sealed interface TutorConversationTimelineItem {
    val occurredAtEpochMillis: Long
    val stableId: String

    data class Plan(
        val task: ModelTaskSnapshot,
        /**
         * 本轮正文的消息行（K1a 唯一文本权威）。null = 这一轮没有消息行（例如写入失败、
         * 或这一轮的正文本来就没有落库）；**不回落账本**——回落会让同一条文本有两个去处。
         */
        val message: TutorMessage? = null,
    ) : TutorConversationTimelineItem {
        private val input = task.request.input as TutorPlanInput

        override val occurredAtEpochMillis: Long = task.request.occurredAtEpochMillis
        override val stableId: String = "plan:${input.cycleOrdinal}:${input.turnOrdinal}:${task.request.requestId}"

        /**
         * 本轮讲解的正文，只从消息行来（K1a）。
         *
         * 完整讲解 / 另一种方法 / 选择题**刻意不从消息行读**：它们是受曝光门控的结构化载荷
         * （选择项要能点、完整讲解必须等揭示且学生真的看到），不是这一轮的"说过的话"。
         */
        val bodyMarkdown: String?
            get() = message?.bodyMarkdown

        val thinkingMarkdown: String?
            get() = message?.thinkingMarkdown
    }

    data class ChoiceFeedback(
        val response: TutorTurnResponse,
        val planTask: ModelTaskSnapshot?,
    ) : TutorConversationTimelineItem {
        override val occurredAtEpochMillis: Long = requireNotNull(
            response.choiceSubmittedAtEpochMillis,
        )

        /**
         * 键里带**题面身份**（B6 之后时间线按会话过滤）：同一会话里题面换过修订时，
         * 两个修订的 (cycle, turn) 可以相同，只用轮次号做键会让两条反馈撞同一个列表键。
         */
        override val stableId: String =
            "choice:${response.questionDocumentId}:${response.revisionNumber}:" +
                "${response.cycleOrdinal}:${response.turnOrdinal}"
    }

    data class Reply(
        val task: ModelTaskSnapshot,
        /** 这一轮助手正文的消息行（见 [Plan.message]）。 */
        val message: TutorMessage? = null,
        /** 这一轮学生气泡的消息行（唯一权威）。 */
        val studentMessage: TutorMessage? = null,
    ) : TutorConversationTimelineItem {
        private val input = task.request.input as TutorRespondInput

        override val occurredAtEpochMillis: Long = task.request.occurredAtEpochMillis
        override val stableId: String = "reply:${task.request.requestId}"

        /** 助手正文：只有消息行这一个来源（K1a）。 */
        val bodyMarkdown: String?
            get() = message?.bodyMarkdown

        val thinkingMarkdown: String?
            get() = message?.thinkingMarkdown

        /** 学生气泡正文：只有消息行这一个来源；这一轮的写入没有落库时为 null。 */
        val studentBodyMarkdown: String?
            get() = studentMessage?.bodyMarkdown

        /**
         * 这一轮讲的是哪一道题（标题）；会话题自己那一轮为 null。
         *
         * 多题会话里光看回复正文看不出"这轮在讲哪道"——上一轮讲 A、这一轮学生从错题库
         * 附加了 B，两段文字风格一样。badge 只在**本地确实知道这一轮的题**时显示，且优先
         * 信学生自己的动作（附加题）而不是模型的声明。
         */
        val questionTitle: String? = replyQuestionTitle(task)
    }
}

/**
 * 一条回复"讲的是哪一道题"的标题来源，按可靠性排序：
 * 1. 学生本轮显式附加的题（[TutorRespondInput.attachedQuestion]）——学生的动作就是锚，
 *    与提示词的 confirmedQuestion 同源；
 * 2. 模型声明且经本地校验的题（[TutorRespondOutput.boundQuestion]，解析层只在核过后才写：
 *    候选必须在派发前的菜单内、锚词必须逐字可核对）——从那一轮的菜单里取同题候选的标题；
 * 3. 都没有则 null：**真的无题轮**（或上一轮绑定延续而模型没有复述）不显示 badge。
 *
 * 第 3 条是刻意的：会话题自己的轮次（题面就摆在页面上方）再挂一行"本题：…"只是噪声。
 */
internal fun replyQuestionTitle(task: ModelTaskSnapshot): String? {
    val input = task.request.input as? TutorRespondInput ?: return null
    input.attachedQuestion?.let { attached -> return attached.title }
    val declaration = (task.output as? TutorRespondOutput)?.boundQuestion ?: return null
    // 同一道题的同一修订才算命中（另一个修订是另一道题，题面与答案都不可搬）——
    // 与 `TutorRoundQuestionBindingPolicy.resolve` 的候选匹配同一条口径。
    return input.boundQuestionCandidates.singleOrNull { candidate ->
        candidate.problemId == declaration.problemId &&
            candidate.problemRevisionId == declaration.problemRevisionId
    }?.title
}

internal data class TutorTurnKey(
    val cycleOrdinal: Int,
    val turnOrdinal: Int,
)

private fun TutorPlanInput.turnKey() = TutorTurnKey(cycleOrdinal, turnOrdinal)

private fun TutorTurnResponse.turnKey() = TutorTurnKey(cycleOrdinal, turnOrdinal)

/**
 * 这一条任务/轮次行属不属于**本会话**（B6）。
 *
 * 过滤只看会话：此前还要求题面文档与修订号逐一相等，于是"同一会话里题面被重新编辑/换了一次
 * 修订"就等于把此前所有轮次从屏幕上抹掉——学生接着问，前面的讲解整段消失（而它们还在库里）。
 * 会话是轮次的容器；同一会话里的每一轮各自带自己的题面修订（轮次行上就有），不需要用会话
 * 级的修订号去筛。
 */
private fun ModelTaskSnapshot.matches(question: TutorQuestionContext): Boolean = when (
    val input = request.input
) {
    is TutorPlanInput -> input.sessionId == question.sessionId

    is TutorRespondInput -> input.sessionId == question.sessionId

    else -> false
}

private fun TutorTurnResponse.matches(question: TutorQuestionContext): Boolean =
    sessionId == question.sessionId

internal fun latestTutorPlanTasks(
    question: TutorQuestionContext,
    tasks: List<ModelTaskSnapshot>,
): List<ModelTaskSnapshot> = latestExactTutorPlanTasks(
    tasks.filter { it.matches(question) && it.request.input is TutorPlanInput },
)

private fun latestExactTutorPlanTasks(
    tasks: List<ModelTaskSnapshot>,
): List<ModelTaskSnapshot> = tasks
    .groupBy { (it.request.input as TutorPlanInput).turnKey() }
    .values
    .map { attempts ->
        attempts.maxWith(
            compareBy<ModelTaskSnapshot>(ModelTaskSnapshot::createdAtEpochMillis)
                .thenBy { it.request.occurredAtEpochMillis }
                .thenBy { it.request.requestId }
                .thenBy(ModelTaskSnapshot::stateVersion),
        )
    }
    .sortedWith(
        compareBy<ModelTaskSnapshot> {
            (it.request.input as TutorPlanInput).cycleOrdinal
        }.thenBy { (it.request.input as TutorPlanInput).turnOrdinal }
            .thenBy { it.request.requestId },
    )

internal data class TutorConversationProjection(
    val planTasks: List<ModelTaskSnapshot>,
    val respondTasks: List<ModelTaskSnapshot>,
    val responses: List<TutorTurnResponse>,
    val latestPlanTasks: List<ModelTaskSnapshot>,
    val latestRespondTasks: List<ModelTaskSnapshot>,
    val timeline: List<TutorConversationTimelineItem>,
    val currentCycle: Int,
    val currentCyclePlanTasks: List<ModelTaskSnapshot>,
    val currentCycleResponses: List<TutorTurnResponse>,
    val responsesByTurn: Map<TutorTurnKey, TutorTurnResponse>,
    val observedPlanTask: ModelTaskSnapshot?,
)

internal fun buildTutorConversationProjection(
    question: TutorQuestionContext,
    planTasks: List<ModelTaskSnapshot>,
    respondTasks: List<ModelTaskSnapshot>,
    responses: List<TutorTurnResponse>,
    /**
     * 这条会话的消息流（K1a 唯一文本权威）。正文/思考/学生气泡从它取，任务快照只留
     * 状态与结构化载荷；旧行（没有消息行）由时间线项自己回落到账本。
     */
    messages: List<TutorMessage> = emptyList(),
): TutorConversationProjection {
    val exactPlanTasks = planTasks.filter { task ->
        task.matches(question) && task.request.input is TutorPlanInput
    }
    val exactRespondTasks = respondTasks.filter { task ->
        task.matches(question) && task.request.input is TutorRespondInput
    }
    val exactResponses = responses.filter { response -> response.matches(question) }
    val latestPlans = latestExactTutorPlanTasks(exactPlanTasks)
    val latestResponses = latestTutorRespondTasks(exactRespondTasks)
    val currentCycle = maxOf(
        exactPlanTasks.maxOfOrNull { task ->
            (task.request.input as TutorPlanInput).cycleOrdinal
        } ?: 1,
        exactResponses.maxOfOrNull(TutorTurnResponse::cycleOrdinal) ?: 1,
    )
    val currentCyclePlans = latestPlans.filter { task ->
        (task.request.input as TutorPlanInput).cycleOrdinal == currentCycle
    }
    val currentCycleResponses = exactResponses.filter { response ->
        response.cycleOrdinal == currentCycle
    }
    val observedTask = currentCyclePlans.maxWithOrNull(
        compareBy<ModelTaskSnapshot> {
            (it.request.input as TutorPlanInput).turnOrdinal
        }.thenBy(ModelTaskSnapshot::createdAtEpochMillis)
            .thenBy { it.request.requestId },
    )
    return TutorConversationProjection(
        planTasks = exactPlanTasks,
        respondTasks = exactRespondTasks,
        responses = exactResponses,
        latestPlanTasks = latestPlans,
        latestRespondTasks = latestResponses,
        timeline = buildExactTutorConversationTimeline(
            planTasks = latestPlans,
            respondTasks = latestResponses,
            responses = exactResponses,
            messages = messages,
        ),
        currentCycle = currentCycle,
        currentCyclePlanTasks = currentCyclePlans,
        currentCycleResponses = currentCycleResponses,
        responsesByTurn = exactResponses.associateBy(TutorTurnResponse::turnKey),
        observedPlanTask = observedTask,
    )
}

internal fun buildTutorConversationTimeline(
    question: TutorQuestionContext,
    planTasks: List<ModelTaskSnapshot>,
    respondTasks: List<ModelTaskSnapshot>,
    responses: List<TutorTurnResponse>,
    messages: List<TutorMessage> = emptyList(),
): List<TutorConversationTimelineItem> = buildTutorConversationProjection(
    question = question,
    planTasks = planTasks,
    respondTasks = respondTasks,
    responses = responses,
    messages = messages,
).timeline

private fun buildExactTutorConversationTimeline(
    planTasks: List<ModelTaskSnapshot>,
    respondTasks: List<ModelTaskSnapshot>,
    responses: List<TutorTurnResponse>,
    messages: List<TutorMessage>,
): List<TutorConversationTimelineItem> {
    val plansByTurn = planTasks.associateBy { task ->
        (task.request.input as TutorPlanInput).turnKey()
    }
    val items = buildList {
        planTasks.forEach { task ->
            add(
                TutorConversationTimelineItem.Plan(
                    task = task,
                    message = messages.assistantTurnOf(task.request.requestId),
                ),
            )
        }
        responses
            .asSequence()
            .filter(TutorTurnResponse::hasChoicePayload)
            .forEach { response ->
                add(
                    TutorConversationTimelineItem.ChoiceFeedback(
                        response = response,
                        planTask = plansByTurn[response.turnKey()],
                    ),
                )
            }
        respondTasks.forEach { task ->
            add(
                TutorConversationTimelineItem.Reply(
                    task = task,
                    message = messages.assistantTurnOf(task.request.requestId),
                    studentMessage = messages.studentTurnOf(task.request.requestId),
                ),
            )
        }
    }
    return items.sortedWith(
        compareBy<TutorConversationTimelineItem>(
            TutorConversationTimelineItem::occurredAtEpochMillis,
        ).thenBy { item ->
            when (item) {
                is TutorConversationTimelineItem.Plan -> 0
                is TutorConversationTimelineItem.ChoiceFeedback -> 1
                is TutorConversationTimelineItem.Reply -> 2
            }
        }.thenBy(TutorConversationTimelineItem::stableId),
    )
}
