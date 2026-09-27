package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import kotlinx.coroutines.flow.first

/**
 * 讲题区一轮模型输出的**耐久文本**落在 `tutor_message` 上（K1a：唯一对话文本权威）。
 *
 * 写侧与读侧的约定只有三条：
 *
 * 1. `model_task.output_snapshot` 仍然是**原始输出账本**（曝光判定、重放、指纹、审计读它），
 *    但它不再是渲染源——屏幕上与提示词里的正文、思考块、学生气泡都从消息行来；
 * 2. 消息 id 由**逻辑操作**派生（`tutor-message-assistant:<requestId>`），所以同一次派发
 *    重放多少次都只有一行（DAO 的插入冲突按 id 命中即返回既有行）；
 * 3. 旧安装的行没有消息行（升级前讲题区从不写助手行），渲染侧保留一条**只读回落**到账本的
 *    路径，让旧会话照常可读；新写入一律只写消息行。
 */

/** 助手消息 id：同一逻辑操作只落一行（幂等靠它，不靠调用方自觉）。 */
internal fun tutorAssistantMessageId(requestId: String): String =
    "tutor-message-assistant:$requestId"

/** 学生消息 id：与 [com.tingyun.smartmistakebook.feature.tutor.TutorRespondCommands] 同一派生式。 */
internal fun tutorStudentMessageId(requestId: String): String = "tutor-message:$requestId"

/**
 * 会话行按需创建（K1b）：**发出第一条消息才建行**。
 *
 * 两个入口（讲题区的 Plan / Respond）都会走到这里，所以行的创建不依赖"某个入口记得先建"。
 * 幂等：先读一次，已存在就原样复用（同锚点幂等、异锚点冲突由 DAO 判）。
 */
internal suspend fun ensureTutorConversation(
    conversations: TutorConversationRepository,
    sessionId: String,
    questionDocumentId: String,
    revisionNumber: Int,
    title: String?,
    occurredAtEpochMillis: Long,
) {
    val conversationId = TutorConversationIds.captured(sessionId)
    if (conversations.observeConversation(conversationId).first() != null) return
    conversations.createConversation(
        CreateTutorConversationCommand(
            conversationId = conversationId,
            anchorKind = TutorConversationAnchorKind.EPHEMERAL_DRAFT,
            anchorId = sessionId,
            // 会话锚 = 本轮题面 + 题面修订号（与迁移和 `tutor_message.bound_problem_*` 同一口径）。
            anchorRevisionId = "$questionDocumentId:$revisionNumber",
            title = title,
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
 */
internal suspend fun recordTutorAssistantTurn(
    conversations: TutorConversationRepository?,
    sessionId: String,
    questionDocumentId: String,
    revisionNumber: Int,
    questionTitle: String?,
    requestId: String,
    replyToMessageId: String?,
    bodyMarkdown: String,
    thinkingMarkdown: String?,
    occurredAtEpochMillis: Long,
    completedAtEpochMillis: Long,
) {
    val repository = conversations ?: return
    val body = bodyMarkdown.takeIf(String::isNotBlank) ?: return
    runCatching {
        ensureTutorConversation(
            conversations = repository,
            sessionId = sessionId,
            questionDocumentId = questionDocumentId,
            revisionNumber = revisionNumber,
            title = questionTitle,
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
}

/** 该逻辑操作的学生行；没有时为 null。 */
internal fun List<TutorMessage>.studentTurnOf(requestId: String): TutorMessage? = firstOrNull {
    message -> message.role == TutorMessageRole.STUDENT && message.logicalOperationId == requestId
}

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
