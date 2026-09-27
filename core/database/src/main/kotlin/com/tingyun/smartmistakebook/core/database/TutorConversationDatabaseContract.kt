package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.TUTOR_CONVERSATION_AREA_AGENT

data class TutorConversationRecord(
    val conversationId: String,
    /** 会话区（K1）：会话按栏隔离的判别列。 */
    val conversationArea: String = TUTOR_CONVERSATION_AREA_AGENT,
    val anchorKind: String,
    val anchorId: String?,
    val anchorRevisionId: String?,
    val status: String,
    val title: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastTurnOrdinal: Int,
    val studentDraft: String?,
    /** 真实消息行数（列表展示用；last_turn_ordinal 是序号语义，讲题会话有空洞）。 */
    val messageCount: Int = 0,
    /**
     * 首条**有正文**的消息的正文（K1c 会话标题的读侧输入；本地事件行正文为空，天然被排除）。
     * 只由列表读侧填充——`observeRecent` 需要它算标题，单条会话的读侧不付这次查询。
     */
    val firstMessageBodyMarkdown: String? = null,
)

data class TutorMessageRecord(
    val messageId: String,
    val conversationId: String,
    val ordinal: Int,
    val role: String,
    val bodyMarkdown: String,
    val thinkingMarkdown: String? = null,
    /** 本轮绑定的题（学生消息行）；两列同时为空表示无题轮。 */
    val boundProblemId: String? = null,
    val boundProblemRevisionId: String? = null,
    val status: String,
    val logicalOperationId: String?,
    val replyToMessageId: String?,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val errorCode: String?,
)

data class CreateTutorConversationDatabaseCommand(
    val conversationId: String,
    /**
     * 会话区（K1）：AGENT / REVIEW_MISTAKE / REVIEW_KNOWLEDGE，集合开放。
     * 默认 AGENT；复习栏两个入口由创建方显式给出 REVIEW_*（阶段 5 接线）。
     */
    val conversationArea: String = TUTOR_CONVERSATION_AREA_AGENT,
    val anchorKind: String,
    val anchorId: String?,
    val anchorRevisionId: String?,
    val title: String?,
    val createdAtEpochMillis: Long,
)

data class AppendTutorStudentMessageDatabaseCommand(
    val conversationId: String,
    val messageId: String,
    /** `null` = 由会话计数器分配（会话行 `last_turn_ordinal + 1`）；见 K1c 单数轴。 */
    val ordinal: Int? = null,
    val bodyMarkdown: String,
    val logicalOperationId: String,
    val createdAtEpochMillis: Long,
    /** 本条消息附图的规范资产 id（按选择顺序）；空表示纯文字消息。 */
    val sourceImageAssetIds: List<String> = emptyList(),
    /**
     * 本轮绑定的题（本地校验通过后的声明）；两列同时为空表示无题轮。
     * 两列是一个整体：只给其一无法定位确定题面，因此由 [requireBoundQuestionPair] 统一校验。
     */
    val boundProblemId: String? = null,
    val boundProblemRevisionId: String? = null,
) {
    init {
        requireBoundQuestionPair(boundProblemId, boundProblemRevisionId)
    }
}

/**
 * 题引用的两列必须同进同出。只给 `problemId` 不给 `problemRevisionId` 的"半绑定"会把
 * "哪一道题"退化成"哪一族题"——题面改了就是另一道题，答案与学习证据都不可搬。
 */
internal fun requireBoundQuestionPair(problemId: String?, problemRevisionId: String?) {
    require((problemId == null) == (problemRevisionId == null)) {
        "A bound question reference needs both its problem id and its revision id"
    }
    require(problemId == null || problemId.isNotBlank()) {
        "A bound question problem id must not be blank"
    }
    require(problemRevisionId == null || problemRevisionId.isNotBlank()) {
        "A bound question revision id must not be blank"
    }
}

/** 学生消息附图的引用行（消息删除时级联删除）。 */
data class TutorMessageSourceAssetRecord(
    val messageId: String,
    val sourceAssetId: String,
    val ordinal: Int,
)

data class AppendTutorAssistantMessageDatabaseCommand(
    val conversationId: String,
    val messageId: String,
    /**
     * `null` = 由会话计数器分配（会话行 `last_turn_ordinal + 1`）；见 K1c 单数轴。
     *
     * 助手行与用户行同一条数轴：讲题区一轮的助手正文是在模型回复到手后才写的，那时会话里
     * 可能已经有别的写入（大厅与讲题真的会写进同一条会话），调用方自己按观察到的快照号
     * 推算就会撞号；号由会话计数器在写入的同一个事务里给出，才是唯一不会撞的分配方式。
     */
    val ordinal: Int? = null,
    val replyToMessageId: String?,
    val bodyMarkdown: String,
    val thinkingMarkdown: String? = null,
    val logicalOperationId: String?,
    val status: String,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val errorCode: String?,
)

/**
 * 把本轮绑定的题写到学生消息行上。
 *
 * 为什么是一次**后写**而不是随学生消息一起写：绑定 = 模型声明 + 本地两条校验，而两者都要等
 * 模型回复到手；学生消息在派发前就已经落库（写侧门控的引文语料依赖它）。所以顺序只能是
 * "先写学生消息、拿到回复后再补绑定"。
 */
data class BindStudentMessageQuestionDatabaseCommand(
    val messageId: String,
    val boundProblemId: String,
    val boundProblemRevisionId: String,
) {
    init {
        require(messageId.isNotBlank()) { "Tutor message id must not be blank" }
        requireBoundQuestionPair(boundProblemId, boundProblemRevisionId)
        require(boundProblemId.isNotBlank()) { "A binding must name a problem" }
    }
}

data class UpdateTutorMessageStatusDatabaseCommand(
    val messageId: String,
    val expectedStatus: String,
    val nextStatus: String,
    val bodyMarkdown: String?,
    val completedAtEpochMillis: Long?,
    val errorCode: String?,
    val updatedAtEpochMillis: Long,
)

data class SaveTutorConversationDraftDatabaseCommand(
    val conversationId: String,
    val draft: String,
    val updatedAtEpochMillis: Long,
)

data class ClearTutorConversationDraftDatabaseCommand(
    val conversationId: String,
    val updatedAtEpochMillis: Long,
)
