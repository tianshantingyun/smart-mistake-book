package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.tutorTurnFigureAssetIds
import kotlinx.coroutines.flow.collect
import com.tingyun.smartmistakebook.core.model.stripUndisclosedSourceLabels
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import kotlinx.coroutines.flow.first
/**
 * 讲题区一轮模型输出的**耐久文本**落在 `tutor_message` 上（K1a：唯一对话文本权威）。
 *
 * 写侧与读侧的约定只有两条：
 *
 * 1. `model_task.output_snapshot` 仍然是**原始输出账本**（曝光判定、重放、指纹、审计读它），
 *    但它不再是渲染源——屏幕上与提示词里的正文、思考块、学生气泡都从消息行来，**没有回落**；
 * 2. 消息 id 由**逻辑操作**派生（`tutor-message-assistant:<requestId>`），所以同一次派发
 *    重放多少次都只有一行（DAO 的插入冲突按 id 命中即返回既有行）。
 */

/** 助手消息 id：同一逻辑操作只落一行（幂等靠它，不靠调用方自觉）。 */
internal fun tutorAssistantMessageId(requestId: String): String =
    "tutor-message-assistant:$requestId"

/** 学生消息 id：与 [com.tingyun.smartmistakebook.feature.tutor.TutorRespondCommands] 同一派生式。 */
internal fun tutorStudentMessageId(requestId: String): String = "tutor-message:$requestId"

/**
 * 会话行按需创建（K1b）：**发出第一条消息才建行**。
 *
 * A1：**会话身份全部由调用方显式给**——会话 id、会话区、锚、锚修订都是参数，这个函数不再
 * 从 `sessionId` 自己派生会话 id。此前那个派生式是"进入即静默接管上一次会话"的机关：只要
 * 题面一样，任何人进来都会算出同一个会话 id，于是"看一眼有没有、有就接着用"必然命中上次
 * 那条会话。现在进入方各自持有显式 id（错题讲题页每次进入新开一个），命中只可能命中
 * **自己点名的那条会话**。
 *
 * 已存在时原样复用（不改写它的锚——锚是创建时的既成事实，题面换修订不该把这一轮的写入顶掉，
 * 也不该悄悄把会话改成别的锚）。同 id 异锚的创建冲突由仓库判，这条先读是为了让正常的
 * "同一会话继续写"不落在冲突路径上。
 *
 * 标题不在这里写（B7）：会话标题 = 首条消息截取 + 时间。
 */
internal suspend fun ensureTutorConversation(
    conversations: TutorConversationRepository,
    conversationId: String,
    area: String,
    anchorId: String,
    anchorRevisionId: String,
    occurredAtEpochMillis: Long,
) {
    if (conversations.observeConversation(conversationId).first() != null) return
    conversations.createConversation(
        CreateTutorConversationCommand(
            conversationId = conversationId,
            area = area,
            anchorKind = TutorConversationAnchorKind.EPHEMERAL_DRAFT,
            anchorId = anchorId,
            anchorRevisionId = anchorRevisionId,
            title = null,
            createdAtEpochMillis = occurredAtEpochMillis,
        ),
    )
}

/**
 * 把一轮**已成功**的模型文本落进助手消息行。
 *
 * 调用点只有两个（Plan、Respond 的成功分支），都在 `runCatching` 里：簿记失败不阻断对话
 * （与写侧的学生回合同一条纪律），但留日志——文本悄悄丢掉的后果是这一段对话在重载后消失。
 *
 * 失败/取消的轮次**不落行**：助手行是"这一轮说了什么"的耐久事实，没有正文的失败轮只有账本
 * 上的状态（界面据此渲染失败卡）。
 *
 * [toolTraceJson]（B1）与正文一起写：痕迹是"这一轮查过什么"的另一半事实，分两次写就会出现
 * "有正文没痕迹"的中间态。null = 这一轮没有发起工具调用（空载体不落列）。
 *
 * [disclosedMaterialTitles]（S2 来源标签）：本轮**真的披露给模型的**课本材料标题。
 * 正文里的来源标签按它核对——没披露的标签（模型自己编的标题）在这里就被剥掉，
 * 落库与渲染看到的都是剥过的那一份（一份文本只留一个副本）。
 */
internal suspend fun recordTutorAssistantTurn(
    conversations: TutorConversationRepository?,
    sessionId: String,
    questionDocumentId: String,
    revisionNumber: Int,
    requestId: String,
    replyToMessageId: String?,
    bodyMarkdown: String,
    thinkingMarkdown: String?,
    occurredAtEpochMillis: Long,
    completedAtEpochMillis: Long,
    toolTraceJson: String? = null,
    disclosedMaterialTitles: List<String> = emptyList(),
) {
    val repository = conversations ?: return
    val rawBody = bodyMarkdown.takeIf(String::isNotBlank) ?: return
    // S2：来源标签按本轮披露的材料核对，未披露的剥掉（语义是交叉参照，不是答案出处）。
    val body = stripUndisclosedSourceLabels(rawBody, disclosedMaterialTitles)
    runCatching {
        ensureTutorConversation(
            conversations = repository,
            conversationId = TutorConversationIds.captured(sessionId),
            area = TutorConversationAreas.AGENT,
            anchorId = sessionId,
            // 会话锚 = 本轮题面 + 题面修订号（与迁移和 `tutor_message.bound_problem_*` 同一口径）。
            anchorRevisionId = "$questionDocumentId:$revisionNumber",
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        repository.appendAssistantMessage(
            AppendTutorAssistantMessageCommand(
                conversationId = TutorConversationIds.captured(sessionId),
                messageId = tutorAssistantMessageId(requestId),
                // 序号由会话计数器分配（K1c 单数轴）：正文是在回复到手后才写的，那时会话里
                // 可能已经有别的写入（大厅与讲题写同一条会话），调用方按快照号推算就会撞号。
                ordinal = null,
                replyToMessageId = replyToMessageId,
                bodyMarkdown = body,
                thinkingMarkdown = thinkingMarkdown,
                toolTraceJson = toolTraceJson,
                // A2：本轮生成的配图与正文、痕迹**同一次写入**建立持久引用——界面靠它重建配图
                // （不再依赖内存 URI），孤儿回收靠它不删这张已付费的图。
                sourceImageAssetIds = tutorTurnFigureAssetIds(toolTraceJson),
                logicalOperationId = requestId,
                status = TutorMessageStatus.SUCCEEDED,
                createdAtEpochMillis = occurredAtEpochMillis,
                completedAtEpochMillis = completedAtEpochMillis,
            ),
        )
    }.onFailure { failure ->
        android.util.Log.w("TutorTurn", "Assistant turn was not persisted to tutor_message", failure)
    }
}

/** 该逻辑操作的助手行；没有（旧行、或写入失败）时为 null。 */
internal fun List<TutorMessage>.assistantTurnOf(requestId: String): TutorMessage? = firstOrNull {
    message -> message.role == TutorMessageRole.ASSISTANT && message.logicalOperationId == requestId
}/** 该逻辑操作的学生行；没有时为 null。 */
internal fun List<TutorMessage>.studentTurnOf(requestId: String): TutorMessage? = firstOrNull {
    message -> message.role == TutorMessageRole.STUDENT && message.logicalOperationId == requestId
}

/**
 * 把一条**已经持久化**的请求跑完，并在它成功时把这一轮的正文落进消息行（K1a）。
 *
 * 消灭的失败：两条恢复路径（本地残留任务的自动续上、以及学生点「继续回复」）此前都只写
 * `modelTasks.execute(request).collect { }`——请求跑完了、账本上也有终态，但**没有写入器**。
 * 切到"正文只从消息行渲染"之后，续上的那一轮在屏幕上是一条**空回复**：模型答了、库里没有、
 * 界面什么也不显示。恢复路径和派发路径共用同一个写入器（[recordTutorAssistantTurn]），
 * 所以"这一轮说了什么"只有一个去处。
 *
 * 恢复 = 把**同一条**请求跑完（请求标识、授权、指纹都不变）；它不是新派发——[TutorPlanCommands.executeTurn]
 * 与 [TutorRespondCommands.collect] 会重新签发一次派发（新请求、新的授权信封），那是另一件事。
 */
internal suspend fun resumePersistedTutorTurn(
    modelTasks: ModelTaskRepository,
    conversations: TutorConversationRepository?,
    request: ModelTaskRequest,
) {
    var recorded = false
    modelTasks.execute(request).collect { snapshot ->
        if (recorded || snapshot.status != ModelTaskStatus.SUCCEEDED) return@collect
        when (val input = request.input) {
            is TutorPlanInput -> {
                val output = snapshot.output as? TutorPlanOutput ?: return@collect
                recorded = true
                recordTutorAssistantTurn(
                    conversations = conversations,
                    sessionId = input.sessionId,
                    questionDocumentId = input.questionDocument.id,
                    revisionNumber = input.draftRevisionNumber,
                    requestId = request.requestId,
                    replyToMessageId = null,
                    bodyMarkdown = output.plan.openingMarkdown,
                    thinkingMarkdown = output.plan.thinkingMarkdown,
                    occurredAtEpochMillis = request.occurredAtEpochMillis,
                    completedAtEpochMillis = snapshot.updatedAtEpochMillis,
                    toolTraceJson = toolTraceJsonFor(modelTasks, request.requestId),
                )
            }

            is TutorRespondInput -> {
                val output = snapshot.output as? TutorRespondOutput ?: return@collect
                recorded = true
                recordTutorAssistantTurn(
                    conversations = conversations,
                    sessionId = input.sessionId,
                    questionDocumentId = input.questionDocument.id,
                    revisionNumber = input.draftRevisionNumber,
                    requestId = request.requestId,
                    // 助手行回复的是这一轮的学生行（同一次逻辑操作）。
                    replyToMessageId = tutorStudentMessageId(request.requestId),
                    bodyMarkdown = output.messageMarkdown,
                    thinkingMarkdown = output.thinkingMarkdown,
                    occurredAtEpochMillis = request.occurredAtEpochMillis,
                    completedAtEpochMillis = snapshot.updatedAtEpochMillis,
                    toolTraceJson = toolTraceJsonFor(modelTasks, request.requestId),
                )
            }

            else -> return@collect
        }
    }
}

/**
 * 这一轮的工具痕迹（B1）：写助手行的那一刻从模型任务仓库读**一次当前值**。
 *
 * 为什么是"读一次当前值"而不是订阅：工具轮全部发生在终态之前，正文到手这一刻痕迹已经完整；
 * 订阅反而会有"正文写完、痕迹后到"的竞态（那正是分两次写的失败）。
 * 读失败、或这一轮根本没有发起工具调用 → null，消息行按"没有痕迹"落（旧行同一条语义）。
 */
internal suspend fun toolTraceJsonFor(
    modelTasks: ModelTaskRepository,
    requestId: String,
): String? = runCatching { modelTasks.observeToolTrace(requestId).first() }.getOrNull()

/** 会话标题的截断长度（K1c：首条消息截取，读时算，不调模型生成）。 */
internal const val TUTOR_CONVERSATION_TITLE_MAX_CHARS = 24

/**
 * 首条消息 → 会话标题（K1c）。
 *
 * 只取首行并去掉标题/列表/加粗等写作记号：一整段 markdown 直接当标题会撑满两行，
 * 而历史列表要的是"一眼认出这是哪次对话"。空正文返回 null（调用方回落到入口标签）。
 */
internal fun tutorConversationTitleOf(firstMessageBody: String?): String? {
    val line = firstMessageBody
        ?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull(String::isNotBlank)
        ?: return null
    val cleaned = line
        .trimStart('#', '-', '*', '>', ' ')
        .replace("**", "")
        .replace("`", "")
        .trim()
    if (cleaned.isBlank()) return null
    return if (cleaned.length <= TUTOR_CONVERSATION_TITLE_MAX_CHARS) {
        cleaned
    } else {
        cleaned.take(TUTOR_CONVERSATION_TITLE_MAX_CHARS) + "…"
    }
}
