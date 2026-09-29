package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.AppendTutorStudentMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.SetTutorInteractionModeDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorConversationRecord
import com.tingyun.smartmistakebook.core.database.TutorMessageRecord
import com.tingyun.smartmistakebook.core.database.TutorTurnResponseRecord
import com.tingyun.smartmistakebook.core.database.isTutorRoundRow
import com.tingyun.smartmistakebook.core.database.toTurnRecordOrNull
import com.tingyun.smartmistakebook.core.database.UpdateTutorMessageStatusDatabaseCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.database.BindStudentMessageQuestionDatabaseCommand
import com.tingyun.smartmistakebook.core.domain.BindStudentMessageQuestionCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ArchiveTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.DeleteTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.PauseTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.SaveTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.SetTutorInteractionModeCommand
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.domain.UpdateTutorMessageStatusCommand
import com.tingyun.smartmistakebook.core.domain.defaultTutorInteractionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class RoomTutorConversationRepository(
    private val database: StudyDatabasePort,
) : TutorConversationRepository {
    /**
     * 某个会话区里最近的会话（按更新时间倒序）。**按区过滤在 SQL 里**（`conversation_area`），
     * 不是读回来再筛：分区后"最近 N 条"必须仍然是本区的最近 N 条，而不是被别的区的行挤掉的
     * 残缺列表。是否"只列有内容的会话"（K1b）仍由界面层决定（见 `TutorHistoryRoute`）。
     */
    override fun observeRecent(limit: Int, area: String): Flow<List<TutorConversation>> {
        require(limit > 0) { "Tutor conversation limit must be positive" }
        require(area.isNotBlank()) { "Tutor conversation area must not be blank" }
        return database.observeRecentTutorConversations(limit, area)
            .map { records -> records.map(TutorConversationRecord::toDomain) }
    }

    /**
     * 一条会话的**唯一读投影**：会话行 + 消息流 + 轮次事实（K1）。
     *
     * 轮次行与消息行同存 `tutor_message`（51→52 把 `tutor_turn_response` 并了进来），所以
     * 两条投影出自同一次读：`turns` 从消息行里那些带轮次列的行还原，`messages` 只保留真正的
     * 对话消息（轮次行是 `LOCAL_EVENT`、正文为空，既不是学生说的话也不是助手说的话——它此前
     * 会被当成消息映射，正文为空的 `require` 直接把整条读流打断）。
     */
    override fun observeConversation(
        conversationId: String,
    ): Flow<TutorConversationSnapshot?> {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        return combine(
            database.observeTutorConversation(conversationId),
            database.observeTutorMessages(conversationId),
        ) { conversation, records ->
            conversation?.let { row ->
                val conversationRecords = records.filterNot { record -> record.isTutorRoundRow }
                val assetIdsByMessage = if (conversationRecords.isEmpty()) {
                    emptyMap()
                } else {
                    database.readTutorMessageSourceAssets(
                        conversationRecords.map { record -> record.messageId },
                    ).groupBy(
                        keySelector = { link -> link.messageId },
                        valueTransform = { link -> link.sourceAssetId },
                    )
                }
                row.toConversationSnapshot(records, assetIdsByMessage)
            }
        }
    }

    override suspend fun createConversation(
        command: CreateTutorConversationCommand,
    ): TutorConversation = withContext(Dispatchers.IO) {
        database.createTutorConversation(command.toDatabase()).toDomain()
    }

    override suspend fun appendStudentMessage(
        command: AppendTutorStudentMessageCommand,
    ): TutorMessage = withContext(Dispatchers.IO) {
        database.appendTutorStudentMessage(command.toDatabase()).toDomain()
    }

    override suspend fun bindStudentMessageQuestion(
        command: BindStudentMessageQuestionCommand,
    ): Boolean = withContext(Dispatchers.IO) {
        database.bindTutorStudentMessageQuestion(
            BindStudentMessageQuestionDatabaseCommand(
                messageId = command.messageId,
                boundProblemId = command.boundProblemId,
                boundProblemRevisionId = command.boundProblemRevisionId,
            ),
        )
        true
    }

    override suspend fun appendAssistantMessage(
        command: AppendTutorAssistantMessageCommand,
    ): TutorMessage = withContext(Dispatchers.IO) {
        database.appendTutorAssistantMessage(command.toDatabase()).toDomain()
    }

    override suspend fun updateMessageStatus(
        command: UpdateTutorMessageStatusCommand,
    ): TutorMessage = withContext(Dispatchers.IO) {
        database.updateTutorMessageStatus(command.toDatabase()).toDomain()
    }

    override suspend fun setInteractionMode(
        command: SetTutorInteractionModeCommand,
    ): TutorConversation = withContext(Dispatchers.IO) {
        database.setTutorInteractionMode(
            SetTutorInteractionModeDatabaseCommand(
                conversationId = command.conversationId,
                interactionMode = command.interactionMode.name,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            ),
        ).toDomain()
    }

    override suspend fun pauseConversation(
        command: PauseTutorConversationCommand,
    ): TutorConversation = withContext(Dispatchers.IO) {
        database.pauseTutorConversation(
            conversationId = command.conversationId,
            updatedAtEpochMillis = command.occurredAtEpochMillis,
        ).toDomain()
    }

    override suspend fun archiveConversation(
        command: ArchiveTutorConversationCommand,
    ): TutorConversation = withContext(Dispatchers.IO) {
        database.archiveTutorConversation(
            conversationId = command.conversationId,
            updatedAtEpochMillis = command.occurredAtEpochMillis,
        ).toDomain()
    }

    override suspend fun deleteConversation(
        command: DeleteTutorConversationCommand,
    ) {
        withContext(Dispatchers.IO) {
            database.deleteTutorConversation(command.conversationId)
        }
    }

    override suspend fun saveDraft(
        command: SaveTutorConversationDraftCommand,
    ) {
        withContext(Dispatchers.IO) {
            database.saveTutorConversationDraft(
                conversationId = command.conversationId,
                draft = command.draft,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            )
        }
    }

    override suspend fun clearDraft(
        command: ClearTutorConversationDraftCommand,
    ) {
        withContext(Dispatchers.IO) {
            database.clearTutorConversationDraft(
                conversationId = command.conversationId,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            )
        }
    }
}

/**
 * 会话行 + 它的**全部**消息行 → 唯一读投影（K1）。
 *
 * 一条判别把两种行分开：**轮次行**（本地事件，正文为空）只进 [TutorConversationSnapshot.turns]，
 * 对话消息只进 [TutorConversationSnapshot.messages]。此前轮次行会被当成普通消息映射，而
 * `TutorMessage` 要求正文非空——一次选择提交之后，整条会话读流就地抛出（学生点一次选项，
 * 画面就崩）。抽成纯函数是为了把这条判别直接钉在用例里。
 */
internal fun TutorConversationRecord.toConversationSnapshot(
    records: List<TutorMessageRecord>,
    assetIdsByMessage: Map<String, List<String>> = emptyMap(),
): TutorConversationSnapshot = TutorConversationSnapshot(
    conversation = toDomain(),
    messages = records
        .filterNot { record -> record.isTutorRoundRow }
        .map { record -> record.toDomain(assetIdsByMessage[record.messageId].orEmpty()) },
    turns = toTurnRecords(records),
)

object TutorConversationRepositoryFactory {
    fun create(database: StudyDatabasePort): TutorConversationRepository =
        RoomTutorConversationRepository(database)
}

/**
 * 会话行 + 它的全部消息行 → 轮次事实（K1：轮次行与消息行同表）。
 *
 * 会话 id 从锚还原（`anchorId` 就是那条讲题会话的 id，见 `TutorConversationIds.captured`
 * 的两个写入方）；锚为空（纯文字会话）时没有轮次可言——那条会话里不可能有轮次行。
 */
private fun TutorConversationRecord.toTurnRecords(
    records: List<TutorMessageRecord>,
): List<TutorTurnResponse> {
    val sessionId = anchorId?.takeIf(String::isNotBlank) ?: return emptyList()
    return records
        .asSequence()
        .filter { record -> record.isTutorRoundRow }
        .mapNotNull { record -> record.toTurnRecordOrNull(sessionId) }
        .sortedWith(compareBy(TutorTurnResponseRecord::cycleOrdinal, TutorTurnResponseRecord::turnOrdinal))
        .map(TutorTurnResponseRecord::toDomain)
        .toList()
}

private fun CreateTutorConversationCommand.toDatabase() = CreateTutorConversationDatabaseCommand(
    conversationId = conversationId,
    conversationArea = area,
    // 会话区默认表只有一处出处：创建方给 null 就走它（智能体栏正常 / 复习栏引导）。
    interactionMode = (interactionMode ?: defaultTutorInteractionMode(area)).name,
    anchorKind = anchorKind.name,
    anchorId = anchorId,
    anchorRevisionId = anchorRevisionId,
    title = title,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun AppendTutorStudentMessageCommand.toDatabase() =
    AppendTutorStudentMessageDatabaseCommand(
        conversationId = conversationId,
        messageId = messageId,
        ordinal = ordinal,
        bodyMarkdown = bodyMarkdown,
        logicalOperationId = logicalOperationId,
        createdAtEpochMillis = createdAtEpochMillis,
        sourceImageAssetIds = sourceImageAssetIds,
        boundProblemId = boundProblemId,
        boundProblemRevisionId = boundProblemRevisionId,
    )

private fun AppendTutorAssistantMessageCommand.toDatabase() =
    AppendTutorAssistantMessageDatabaseCommand(
        conversationId = conversationId,
        messageId = messageId,
        ordinal = ordinal,
        replyToMessageId = replyToMessageId,
        bodyMarkdown = bodyMarkdown,
        thinkingMarkdown = thinkingMarkdown,
        toolTraceJson = toolTraceJson,
        logicalOperationId = logicalOperationId,
        status = status.name,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        errorCode = errorCode,
    )

private fun UpdateTutorMessageStatusCommand.toDatabase() =
    UpdateTutorMessageStatusDatabaseCommand(
        messageId = messageId,
        expectedStatus = expectedStatus.name,
        nextStatus = nextStatus.name,
        bodyMarkdown = bodyMarkdown,
        completedAtEpochMillis = completedAtEpochMillis,
        errorCode = errorCode,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun TutorConversationRecord.toDomain() = TutorConversation(
    conversationId = conversationId,
    area = conversationArea,
    // 未知字符串回落 NORMAL（集合开放：将来新增的模式由新版写、旧版读回时按正常答）。
    interactionMode = TutorInteractionMode.fromName(interactionMode),
    anchorKind = TutorConversationAnchorKind.valueOf(anchorKind),
    anchorId = anchorId,
    anchorRevisionId = anchorRevisionId,
    status = TutorConversationStatus.valueOf(status),
    title = title,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    lastTurnOrdinal = lastTurnOrdinal,
    studentDraft = studentDraft,
    messageCount = messageCount,
    firstMessageBodyMarkdown = firstMessageBodyMarkdown,
)

private fun TutorMessageRecord.toDomain(
    sourceImageAssetIds: List<String> = emptyList(),
) = TutorMessage(
    messageId = messageId,
    conversationId = conversationId,
    ordinal = ordinal,
    role = TutorMessageRole.valueOf(role),
    bodyMarkdown = bodyMarkdown,
    thinkingMarkdown = thinkingMarkdown,
    status = TutorMessageStatus.valueOf(status),
    logicalOperationId = logicalOperationId,
    replyToMessageId = replyToMessageId,
    createdAtEpochMillis = createdAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    errorCode = errorCode,
    sourceImageAssetIds = sourceImageAssetIds,
    boundProblemId = boundProblemId,
    boundProblemRevisionId = boundProblemRevisionId,
    toolTraceJson = toolTraceJson,
)
