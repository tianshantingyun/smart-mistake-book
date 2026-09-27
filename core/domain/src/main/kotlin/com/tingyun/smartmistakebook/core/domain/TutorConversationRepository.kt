package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.flow.Flow

enum class TutorConversationAnchorKind {
    TEXT_ONLY,
    EPHEMERAL_DRAFT,
    PROBLEM_REVISION,
}

enum class TutorConversationStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED,
}

enum class TutorMessageRole {
    STUDENT,
    ASSISTANT,
    LOCAL_EVENT,
}

enum class TutorMessageStatus {
    PERSISTED,
    WAITING,
    STREAMING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class TutorConversation(
    val conversationId: String,
    val anchorKind: TutorConversationAnchorKind,
    val anchorId: String?,
    val anchorRevisionId: String?,
    val status: TutorConversationStatus,
    val title: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastTurnOrdinal: Int,
    val studentDraft: String? = null,
    /** 真实消息行数（列表展示用；讲题会话的序号有空洞，不能当条数）。 */
    val messageCount: Int = 0,
    /**
     * 首条**有正文**的消息（K1c 会话标题的读侧输入）。
     *
     * 标题不再由写侧生成：历史列表按"首条消息截取"读时算，所以这里只需要原文，
     * 截断与兜底留在界面层（同一份原文可以被不同入口以不同长度使用）。
     */
    val firstMessageBodyMarkdown: String? = null,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(createdAtEpochMillis >= 0L) { "Tutor conversation creation time must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "Tutor conversation update time cannot precede creation"
        }
        require(lastTurnOrdinal >= 0) { "Tutor conversation turn ordinal must not be negative" }
        when (anchorKind) {
            TutorConversationAnchorKind.TEXT_ONLY -> {
                require(anchorId == null && anchorRevisionId == null) {
                    "A text-only tutor conversation cannot carry an anchor"
                }
            }
            TutorConversationAnchorKind.EPHEMERAL_DRAFT,
            TutorConversationAnchorKind.PROBLEM_REVISION,
            -> {
                require(anchorId != null && anchorId.isNotBlank()) {
                    "Anchored tutor conversations require an anchor id"
                }
                require(anchorRevisionId != null && anchorRevisionId.isNotBlank()) {
                    "Anchored tutor conversations require an anchor revision id"
                }
            }
        }
    }
}

data class TutorMessage(
    val messageId: String,
    val conversationId: String,
    val ordinal: Int,
    val role: TutorMessageRole,
    val bodyMarkdown: String,
    /** 模型给出的思考轨迹（折叠展示、不回喂模型）；旧消息为 null。 */
    val thinkingMarkdown: String? = null,
    val status: TutorMessageStatus,
    val logicalOperationId: String?,
    val replyToMessageId: String?,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val errorCode: String?,
    /** 学生消息附图的规范资产 id（按选择顺序）；空表示纯文字消息。 */
    val sourceImageAssetIds: List<String> = emptyList(),
    /**
     * 本轮绑定的题（本地校验通过后的声明）。两列同时为 null = **无题轮**——包括迁移前写下的
     * 旧行：它们的题归属当年无从判定，读回保持 null，不回填、不猜测。
     */
    val boundProblemId: String? = null,
    val boundProblemRevisionId: String? = null,
) {
    init {
        require(messageId.isNotBlank()) { "Tutor message id must not be blank" }
        require(conversationId.isNotBlank()) { "Tutor message conversation id must not be blank" }
        require(ordinal > 0) { "Tutor message ordinal must be positive" }
        require(bodyMarkdown.isNotBlank()) { "Tutor message body must not be blank" }
        require(createdAtEpochMillis >= 0L) { "Tutor message creation time must not be negative" }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= createdAtEpochMillis) {
            "Tutor message completion time cannot precede creation"
        }
        require(
            status != TutorMessageStatus.SUCCEEDED ||
                role != TutorMessageRole.STUDENT ||
                completedAtEpochMillis != null,
        ) { "A completed student message requires a completion time" }
        require(
            status == TutorMessageStatus.PERSISTED ||
                status == TutorMessageStatus.WAITING ||
                status == TutorMessageStatus.STREAMING ||
                logicalOperationId != null,
        ) { "Only in-flight assistant messages may omit a logical operation id" }
        require((boundProblemId == null) == (boundProblemRevisionId == null)) {
            "A bound round question needs both its problem id and its revision id"
        }
    }
}

data class TutorConversationSnapshot(
    val conversation: TutorConversation,
    val messages: List<TutorMessage>,
)

data class CreateTutorConversationCommand(
    val conversationId: String,
    val anchorKind: TutorConversationAnchorKind,
    val anchorId: String?,
    val anchorRevisionId: String?,
    val title: String?,
    val createdAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(createdAtEpochMillis >= 0L) { "Tutor conversation creation time must not be negative" }
        require(title == null || title.isNotBlank()) {
            "Tutor conversation title must be null or non-blank"
        }
        when (anchorKind) {
            TutorConversationAnchorKind.TEXT_ONLY -> {
                require(anchorId == null && anchorRevisionId == null) {
                    "A text-only tutor conversation cannot carry an anchor"
                }
            }
            TutorConversationAnchorKind.EPHEMERAL_DRAFT,
            TutorConversationAnchorKind.PROBLEM_REVISION,
            -> {
                require(anchorId != null && anchorId.isNotBlank()) {
                    "Anchored tutor conversations require an anchor id"
                }
                require(anchorRevisionId != null && anchorRevisionId.isNotBlank()) {
                    "Anchored tutor conversations require an anchor revision id"
                }
            }
        }
    }
}

data class AppendTutorStudentMessageCommand(
    val conversationId: String,
    val messageId: String,
    /**
     * 会话级单调 ordinal（K1c）。`null` = 由会话计数器分配下一位（会话行 `last_turn_ordinal + 1`），
     * 调用方不需要自己维护数轴；显式给值仍然支持（大厅按自己观察到的会话快照号发请求，
     * 请求里的轮次号必须与消息号同源）。
     */
    val ordinal: Int? = null,
    val bodyMarkdown: String,
    val logicalOperationId: String,
    val createdAtEpochMillis: Long,
    /** 附图的规范资产 id（按选择顺序，最多 9 张）；空表示纯文字消息。 */
    val sourceImageAssetIds: List<String> = emptyList(),
    /**
     * 本轮绑定的题（本地两条校验通过后的声明）；两列同时为空表示**无题轮**。
     *
     * 挂在学生消息行上：一轮的锚是学生这一轮说的话。助手回复可能失败/重试/取消，挂在回复行上
     * 会让绑定随重试而漂。
     */
    val boundProblemId: String? = null,
    val boundProblemRevisionId: String? = null,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(messageId.isNotBlank()) { "Tutor message id must not be blank" }
        require(ordinal == null || ordinal > 0) { "Tutor message ordinal must be positive" }
        require(bodyMarkdown.isNotBlank()) { "Tutor message body must not be blank" }
        require(logicalOperationId.isNotBlank()) { "Tutor logical operation id must not be blank" }
        require(createdAtEpochMillis >= 0L) { "Tutor message creation time must not be negative" }
        require(sourceImageAssetIds.size <= MAX_TUTOR_MESSAGE_IMAGES) {
            "Tutor student message carries too many images"
        }
        require(sourceImageAssetIds.distinct().size == sourceImageAssetIds.size) {
            "Tutor student message image ids must be unique"
        }
        require((boundProblemId == null) == (boundProblemRevisionId == null)) {
            "A bound round question needs both its problem id and its revision id"
        }
        require(boundProblemId == null || boundProblemId.isNotBlank()) {
            "A bound round question problem id must not be blank"
        }
        require(boundProblemRevisionId == null || boundProblemRevisionId.isNotBlank()) {
            "A bound round question revision id must not be blank"
        }
    }
}

/** 学生消息附图上限（与 Lobby 契约一致）。 */
const val MAX_TUTOR_MESSAGE_IMAGES = 9

/**
 * 把本轮绑定的题补写到学生消息行。返回 false 表示该界面/实现不做这件事——调用方不得把
 * false 当成"绑定成功"：绑定是否成立由 [TutorRoundQuestionBindingPolicy] 判定，这一步只负责
 * 把它落下去。
 */
data class BindStudentMessageQuestionCommand(
    val messageId: String,
    val boundProblemId: String,
    val boundProblemRevisionId: String,
) {
    init {
        require(messageId.isNotBlank()) { "Tutor message id must not be blank" }
        require(boundProblemId.isNotBlank()) { "A bound round question problem id must not be blank" }
        require(boundProblemRevisionId.isNotBlank()) {
            "A bound round question revision id must not be blank"
        }
    }
}

data class AppendTutorAssistantMessageCommand(
    val conversationId: String,
    val messageId: String,
    /**
     * 会话级单调 ordinal（K1c 单数轴）。`null` = 由会话计数器分配下一位，与
     * [AppendTutorStudentMessageCommand.ordinal] 同一个分配点；助手正文在模型回复到手后才写，
     * 那时会话里可能已经有别的写入，调用方按自己观察到的快照号推算就会撞号。
     */
    val ordinal: Int? = null,
    val replyToMessageId: String?,
    val bodyMarkdown: String,
    /** 模型给出的思考轨迹；随消息一起展示（折叠），失败/中断的回复不落。 */
    val thinkingMarkdown: String? = null,
    val logicalOperationId: String?,
    val status: TutorMessageStatus = TutorMessageStatus.SUCCEEDED,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val errorCode: String? = null,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(messageId.isNotBlank()) { "Tutor message id must not be blank" }
        require(ordinal == null || ordinal > 0) { "Tutor message ordinal must be positive" }
        require(bodyMarkdown.isNotBlank()) { "Tutor message body must not be blank" }
        require(
            logicalOperationId == null || logicalOperationId.isNotBlank(),
        ) { "Tutor logical operation id must be null or non-blank" }
        require(replyToMessageId == null || replyToMessageId.isNotBlank()) {
            "Tutor reply-to id must be null or non-blank"
        }
        require(createdAtEpochMillis >= 0L) { "Tutor message creation time must not be negative" }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= createdAtEpochMillis) {
            "Tutor message completion time cannot precede creation"
        }
        require(
            status in setOf(
                TutorMessageStatus.STREAMING,
                TutorMessageStatus.SUCCEEDED,
                TutorMessageStatus.FAILED,
                TutorMessageStatus.CANCELLED,
            ),
        ) { "Tutor assistant messages use a terminal or streaming status" }
        require(status != TutorMessageStatus.SUCCEEDED || completedAtEpochMillis != null) {
            "A succeeded assistant message requires a completion time"
        }
    }
}

data class UpdateTutorMessageStatusCommand(
    val messageId: String,
    val expectedStatus: TutorMessageStatus,
    val nextStatus: TutorMessageStatus,
    val bodyMarkdown: String?,
    val completedAtEpochMillis: Long?,
    val errorCode: String?,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(messageId.isNotBlank()) { "Tutor message id must not be blank" }
        require(updatedAtEpochMillis >= 0L) { "Tutor message update time must not be negative" }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= updatedAtEpochMillis) {
            "Tutor message completion time cannot precede its update"
        }
    }
}

data class PauseTutorConversationCommand(
    val conversationId: String,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(occurredAtEpochMillis >= 0L) { "Tutor conversation pause time must not be negative" }
    }
}

data class ArchiveTutorConversationCommand(
    val conversationId: String,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(occurredAtEpochMillis >= 0L) {
            "Tutor conversation archive time must not be negative"
        }
    }
}

data class DeleteTutorConversationCommand(
    val conversationId: String,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(occurredAtEpochMillis >= 0L) {
            "Tutor conversation deletion time must not be negative"
        }
    }
}

data class SaveTutorConversationDraftCommand(
    val conversationId: String,
    val draft: String,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(draft.isNotBlank()) { "Tutor conversation draft must not be blank" }
        require(occurredAtEpochMillis >= 0L) {
            "Tutor conversation draft save time must not be negative"
        }
    }
}

data class ClearTutorConversationDraftCommand(
    val conversationId: String,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(occurredAtEpochMillis >= 0L) {
            "Tutor conversation draft clear time must not be negative"
        }
    }
}

interface TutorConversationRepository {
    fun observeRecent(limit: Int = 20): Flow<List<TutorConversation>>

    fun observeConversation(conversationId: String): Flow<TutorConversationSnapshot?>

    suspend fun createConversation(command: CreateTutorConversationCommand): TutorConversation

    suspend fun appendStudentMessage(command: AppendTutorStudentMessageCommand): TutorMessage

    /**
     * 把本轮绑定的题补写到学生消息行（拿到模型回复之后）。
     *
     * 默认不落：不是所有实现都提供会话行（测试替身、无库的界面）。默认返回 false 而不是 true，
     * 是为了让"没落成"与"落成了"在调用方看来不一样——绑定落不下去时不得被当成已绑定。
     */
    suspend fun bindStudentMessageQuestion(command: BindStudentMessageQuestionCommand): Boolean = false

    suspend fun appendAssistantMessage(command: AppendTutorAssistantMessageCommand): TutorMessage

    suspend fun updateMessageStatus(command: UpdateTutorMessageStatusCommand): TutorMessage

    suspend fun pauseConversation(command: PauseTutorConversationCommand): TutorConversation

    suspend fun archiveConversation(command: ArchiveTutorConversationCommand): TutorConversation

    suspend fun deleteConversation(command: DeleteTutorConversationCommand)

    suspend fun saveDraft(command: SaveTutorConversationDraftCommand)

    suspend fun clearDraft(command: ClearTutorConversationDraftCommand)
}
