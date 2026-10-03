package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import kotlinx.coroutines.flow.Flow

/** 学生消息附图上限（与 Lobby 契约一致）。 */
const val MAX_TUTOR_MESSAGE_IMAGES = 9

/**
 * 会话区（D-Q1H/M10）：会话**按栏隔离**的判别列，集合开放。
 *
 * 智能体栏一个区、复习栏两个入口各一个区；会话只能在本区被列出（见
 * [TutorConversationRepository.observeRecent] 的 `area` 参数）。把"这一条会话属于哪个区"
 * 做成创建时的**显式参数**而不是从锚/题/入口名反推：反推就是 A1 那类"进入即静默接管旧会话"
 * 的温床——会话的身份必须由打开它的人明确给出。
 *
 * 与 [com.tingyun.smartmistakebook.core.domain.AgentPendingRequest.conversationArea] 同一口径
 * （那里是物化快照），都是字符串而非枚举：集合开放，未来新增区不需要改动已落库的行。
 */
object TutorConversationAreas {
    const val AGENT = "AGENT"
    const val REVIEW_MISTAKE = "REVIEW_MISTAKE"
    const val REVIEW_KNOWLEDGE = "REVIEW_KNOWLEDGE"
}

/**
 * 会话区 → 默认交互模式（D-Q9：智能体栏默认正常，**复习栏默认引导**）。
 *
 * 一张表、一处出处：创建会话时给 null 就走这里；阶段 5 的两个复习入口不需要再抄一份默认值，
 * 也不会出现"入口说引导、行里写着正常"的分叉。
 */
fun defaultTutorInteractionMode(area: String): TutorInteractionMode = when (area) {
    TutorConversationAreas.REVIEW_MISTAKE,
    TutorConversationAreas.REVIEW_KNOWLEDGE,
    -> TutorInteractionMode.GUIDED
    else -> TutorInteractionMode.NORMAL
}

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
    /**
     * 会话区（见 [TutorConversationAreas]）：这条会话属于哪一栏的会话区。默认 [TutorConversationAreas.AGENT]
     * 只为迁移前后的旧行读回服务（旧行按迁移口径回填 AGENT）；**新写入一律由创建方显式给出**。
     */
    val area: String = TutorConversationAreas.AGENT,
    /**
     * 交互模式（D-Q9）：这条会话按哪种交互形态答复。默认 [TutorInteractionMode.NORMAL]
     * 只为旧行读回服务（迁移前的会话只有正常模式）；新写入由创建方显式给出。
     */
    val interactionMode: TutorInteractionMode = TutorInteractionMode.NORMAL,
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
        require(area.isNotBlank()) { "Tutor conversation area must not be blank" }
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
    /**
     * 这一轮智能体查阅了什么的痕迹（B1）：本地形状的 JSON，只进界面、不进模型。
     * null = 这一轮没有发起工具调用，或它是迁移前写下的旧行（**无痕迹**，不回填、不猜测）。
     */
    val toolTraceJson: String? = null,
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

/**
 * 一条会话的**唯一读投影**：会话行 + 消息流 + 轮次事实（讲题区选择题/揭示/动作）。
 *
 * [turns] 与 [messages] 出自**同一张表**（K1 把 `tutor_turn_response` 并入 `tutor_message`
 * 的轮次行），所以在同一次订阅里一起给出：调用方读一次会话就拿到"说了什么"与"这一轮发生了
 * 什么"，不需要第二条订阅，也就不会再出现两份投影互相漂移。
 */
data class TutorConversationSnapshot(
    val conversation: TutorConversation,
    val messages: List<TutorMessage>,
    val turns: List<TutorTurnResponse> = emptyList(),
)

data class CreateTutorConversationCommand(
    val conversationId: String,
    /**
     * 会话区（见 [TutorConversationAreas]）：**创建方显式给出**，不从锚/题反推。
     * 默认值只为"智能体栏"这一条主路径服务（本阶段唯一的创建方是智能体栏与讲题区）；
     * 复习栏两个入口在阶段 5 显式给 `REVIEW_*`。
     */
    val area: String = TutorConversationAreas.AGENT,
    /**
     * 交互模式（D-Q9）：`null` = 由**会话区的默认**决定（[defaultTutorInteractionMode]：
     * 智能体栏正常、复习栏引导）。创建方给 null 而不是自己抄一份默认表——默认表只有一处出处，
     * 阶段 5 的两个复习入口因此不会与它分叉。
     */
    val interactionMode: TutorInteractionMode? = null,
    val anchorKind: TutorConversationAnchorKind,
    val anchorId: String?,
    val anchorRevisionId: String?,
    val title: String?,
    val createdAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(area.isNotBlank()) { "Tutor conversation area must not be blank" }
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
    /**
     * 这一轮的工具痕迹（B1）。与正文一起写：痕迹是"这一轮说过什么"的另一半事实（这一轮查过
     * 什么），分两次写就会出现"有正文没痕迹"的中间态；没有痕迹时给 null（空载体不落列）。
     */
    val toolTraceJson: String? = null,
    /**
     * A2（4B）：这一轮**成功生成/命中**的配图规范资产 id（按生成顺序，与痕迹里的 figureId
     * 同源）。与正文、痕迹同一次写入 `tutor_message_source_asset` 引用：这条引用是"图还在"
     * 的持久事实——界面靠它重建配图（不再依赖内存 URI），孤儿回收靠它不删图。
     *
     * 上限沿用 [MAX_TUTOR_MESSAGE_IMAGES]：工具面每轮每种工具至多一次、最多 5 轮，实际用量
     * 远低于该上限；超限是装配错误，拒写而不是默默截断（截断会让痕迹与引用对不上）。
     */
    val sourceImageAssetIds: List<String> = emptyList(),
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
        require(toolTraceJson == null || toolTraceJson.isNotBlank()) {
            "Tutor message tool trace must be null or non-blank"
        }
        require(sourceImageAssetIds.size <= MAX_TUTOR_MESSAGE_IMAGES) {
            "Tutor assistant message carries too many figures"
        }
        require(sourceImageAssetIds.all(String::isNotBlank)) {
            "Tutor assistant message figure ids must not be blank"
        }
        require(sourceImageAssetIds.distinct().size == sourceImageAssetIds.size) {
            "Tutor assistant message figure ids must be unique"
        }
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

/**
 * 切换交互模式（D-Q9）：学生在会话里自己选的那一下（可反复换）。
 */
data class SetTutorInteractionModeCommand(
    val conversationId: String,
    val interactionMode: TutorInteractionMode,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        require(occurredAtEpochMillis >= 0L) {
            "Tutor conversation mode switch time must not be negative"
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
    /**
     * 某个会话区里最近的会话（按更新时间倒序）。
     *
     * [area] **不给默认值**：历史列表列出的是哪一个区的会话，必须由调用方说出来（智能体栏传
     * [TutorConversationAreas.AGENT]，复习栏两个入口在阶段 5 各传自己的区）。默认值会让
     * "复习栏的历史混进智能体栏"这类错误编译期看不出来。
     */
    fun observeRecent(limit: Int = 20, area: String): Flow<List<TutorConversation>>

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

    /**
     * 切换交互模式（D-Q9）。默认不落库（返回 null）而不是假装成功：测试替身与无库界面不存在
     * 这条会话行，而"没落成"与"落成了"必须在调用方看来不一样——模式没落成时那一轮不得按新模式
     * 发出去（否则学生以为切了，实际按旧模式答）。
     */
    suspend fun setInteractionMode(command: SetTutorInteractionModeCommand): TutorConversation? = null

    suspend fun pauseConversation(command: PauseTutorConversationCommand): TutorConversation

    suspend fun archiveConversation(command: ArchiveTutorConversationCommand): TutorConversation

    suspend fun deleteConversation(command: DeleteTutorConversationCommand)

    suspend fun saveDraft(command: SaveTutorConversationDraftCommand)

    suspend fun clearDraft(command: ClearTutorConversationDraftCommand)
}
