package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ArchiveTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.DeleteTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.PauseTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.SaveTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.UpdateTutorMessageStatusCommand
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorToolFigureTrace
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 讲题轮的正文落 `tutor_message`（K1a/K1b）：只有三条约定，逐条钉住——
 * 消息 id 由逻辑操作派生（幂等的支点）、会话行按需创建（空会话不落库）、正文交给会话计数器排号。
 */
class TutorTurnMessagesTest {
    @Test
    fun aRecordedTurnCreatesTheConversationAndWritesOneRow() = runTest {
        val conversations = TutorConversationRows()

        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "先看临界点两侧的符号。",
            thinkingMarkdown = "先判断定义域。",
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
        )
        // 同一次逻辑操作再记一遍（重放/恢复）：只有一行。
        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "先看临界点两侧的符号。",
            thinkingMarkdown = "先判断定义域。",
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
        )

        val created = conversations.createCommands.single()
        assertEquals("tutor-conv:captured:$SESSION_ID", created.conversationId)
        assertEquals(TutorConversationAnchorKind.EPHEMERAL_DRAFT, created.anchorKind)
        assertEquals(SESSION_ID, created.anchorId)
        assertEquals("question-1:2", created.anchorRevisionId)
        // B7：写侧不再把题名当会话标题——标题 = 首条消息截取 + 时间，读时算。
        assertNull(created.title)
        assertEquals("agent", created.area.lowercase())

        val turn = conversations.assistantTurns.single()
        assertEquals("tutor-message-assistant:$REQUEST_ID", turn.messageId)
        assertEquals("先看临界点两侧的符号。", turn.bodyMarkdown)
        assertEquals("先判断定义域。", turn.thinkingMarkdown)
        assertEquals(REQUEST_ID, turn.logicalOperationId)
        // 序号由会话计数器分配（没给号），由 DAO 落库时给出——调用方自己不排号。
        assertNull(turn.requestedOrdinal)
        // B1：这一轮没有发起工具调用 → 痕迹为 null（空载体不落列）。
        assertNull(turn.toolTraceJson)
    }

    @Test
    fun aRecordedTurnCarriesItsToolTraceOnTheSameRow() = runTest {
        // B1：痕迹与正文**同一次写入**（分两次写就会出现"有正文没痕迹"的中间态），
        // 且它就是 core:model 编码出来的那一份（重开会话读回来的是同一个渲染函数）。
        val conversations = TutorConversationRows()
        val traceJson = requireNotNull(
            encodeTutorTurnToolTrace(
                TutorTurnToolTrace(
                    entries = listOf(
                        TutorToolTraceEntry(
                            tool = TutorToolName.NOTEBOOK_READ,
                            resultCount = 2,
                            ok = true,
                        ),
                    ),
                ),
            ),
        )

        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "错题本里有两道同类题。",
            thinkingMarkdown = null,
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
            toolTraceJson = traceJson,
        )

        assertEquals(traceJson, conversations.assistantTurns.single().toolTraceJson)
    }

    @Test
    fun aRecordedTurnCarriesItsGeneratedFigureReferencesOnTheSameRow() = runTest {
        // A2：本轮生成图的**持久引用**与正文、痕迹同一次写入——消息行在，引用就在；
        // 界面靠它重建配图，孤儿回收靠它不删这张已付费的图。
        val conversations = TutorConversationRows()
        val traceJson = requireNotNull(
            encodeTutorTurnToolTrace(
                TutorTurnToolTrace(
                    entries = listOf(
                        TutorToolTraceEntry(
                            tool = TutorToolName.GENERATE_FIGURE,
                            ok = true,
                            figure = TutorToolFigureTrace(
                                kind = TutorFigureKind.REDRAW_PROBLEM,
                                figureId = "figure-a",
                                model = "gpt-image-2",
                                generatedNow = true,
                            ),
                        ),
                        TutorToolTraceEntry(
                            tool = TutorToolName.GENERATE_FIGURE,
                            ok = true,
                            figure = TutorToolFigureTrace(
                                kind = TutorFigureKind.GENERATE_PROCESS,
                                figureId = "figure-b",
                            ),
                        ),
                    ),
                ),
            ),
        )

        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "图放在下面。",
            thinkingMarkdown = null,
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
            toolTraceJson = traceJson,
        )

        assertEquals(
            listOf("figure-a", "figure-b"),
            conversations.assistantTurns.single().sourceImageAssetIds,
        )
    }

    @Test
    fun aTurnWithoutFiguresWritesNoFigureReferences() = runTest {
        val conversations = TutorConversationRows()
        val traceJson = requireNotNull(
            encodeTutorTurnToolTrace(
                TutorTurnToolTrace(
                    entries = listOf(
                        TutorToolTraceEntry(
                            tool = TutorToolName.NOTEBOOK_READ,
                            resultCount = 2,
                            ok = true,
                        ),
                    ),
                ),
            ),
        )

        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "错题本里有两道同类题。",
            thinkingMarkdown = null,
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
            toolTraceJson = traceJson,
        )

        assertTrue(conversations.assistantTurns.single().sourceImageAssetIds.isEmpty())
    }

    /**
     * A1：命中即复用**只可能命中调用方点名的那条会话**——会话 id 由调用方显式给（不再由
     * 题面派生），所以"已经在了"就是"这条会话确实存在"，原样复用、不重写它的锚。
     */
    @Test
    fun anExistingConversationIsReusedInsteadOfBeingRecreated() = runTest {
        val conversations = TutorConversationRows()
        conversations.seed(
            TutorConversation(
                conversationId = "tutor-conv:captured:$SESSION_ID",
                anchorKind = TutorConversationAnchorKind.EPHEMERAL_DRAFT,
                anchorId = SESSION_ID,
                anchorRevisionId = "question-1:2",
                status = TutorConversationStatus.ACTIVE,
                title = "函数单调性",
                createdAtEpochMillis = 10,
                updatedAtEpochMillis = 10,
                lastTurnOrdinal = 0,
            ),
        )

        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "接着看第二段。",
            thinkingMarkdown = null,
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
        )

        assertTrue(conversations.createCommands.isEmpty())
        assertEquals("接着看第二段。", conversations.assistantTurns.single().bodyMarkdown)
    }

    @Test
    fun aBlankBodyIsNotWrittenAtAll() = runTest {
        val conversations = TutorConversationRows()

        recordTutorAssistantTurn(
            conversations = conversations,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "   ",
            thinkingMarkdown = null,
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
        )

        // 空正文不是"这一轮说了什么"：不写行，也不因此建会话（K1b 空会话不落库）。
        assertTrue(conversations.assistantTurns.isEmpty())
        assertTrue(conversations.createCommands.isEmpty())
    }

    @Test
    fun aNullConversationRepositoryIsANoOpForTestDoubles() = runTest {
        recordTutorAssistantTurn(
            conversations = null,
            sessionId = SESSION_ID,
            questionDocumentId = "question-1",
            revisionNumber = 2,
            requestId = REQUEST_ID,
            replyToMessageId = null,
            bodyMarkdown = "正文",
            thinkingMarkdown = null,
            occurredAtEpochMillis = 100,
            completedAtEpochMillis = 120,
        )
    }

    @Test
    fun theTitleIsTheFirstLineOfTheFirstMessageWithMarkdownNoiseStripped() {
        assertEquals("求函数的单调区间", tutorConversationTitleOf("# 求函数的单调区间\n\n后面还有正文"))
        assertEquals("先说结论", tutorConversationTitleOf("  **先说结论**  "))
        assertNull(tutorConversationTitleOf(""))
        assertNull(tutorConversationTitleOf("   \n  \n"))
        assertNull(tutorConversationTitleOf(null))
    }

    @Test
    fun aLongFirstMessageIsTruncatedWithAnEllipsis() {
        val title = requireNotNull(tutorConversationTitleOf("字".repeat(80)))

        assertEquals(TUTOR_CONVERSATION_TITLE_MAX_CHARS + 1, title.length)
        assertTrue(title.endsWith("…"))
    }

    private companion object {
        const val SESSION_ID = "session-1"
        const val REQUEST_ID = "tutor-respond:request-1"
    }
}

/**
 * 按 Room 的真实形状工作的会话替身：`appendAssistantMessage` 命中同一条消息 id 就返回既有行
 * （幂等），会话行按需创建。
 */
private class TutorConversationRows : TutorConversationRepository {
    private val conversations = MutableStateFlow<Map<String, TutorConversation>>(emptyMap())
    val createCommands = mutableListOf<CreateTutorConversationCommand>()
    val assistantTurns = mutableListOf<RecordedAssistantTurn>()

    suspend fun seed(conversation: TutorConversation) {
        conversations.update { rows -> rows + (conversation.conversationId to conversation) }
    }

    override fun observeRecent(limit: Int, area: String): Flow<List<TutorConversation>> =
        flowOf(emptyList())

    override fun observeConversation(
        conversationId: String,
    ): Flow<TutorConversationSnapshot?> = conversations.map { rows ->
        rows[conversationId]?.let { conversation ->
            TutorConversationSnapshot(conversation, emptyList())
        }
    }

    override suspend fun createConversation(
        command: CreateTutorConversationCommand,
    ): TutorConversation {
        createCommands += command
        conversations.value[command.conversationId]?.let { existing -> return existing }
        val created = TutorConversation(
            conversationId = command.conversationId,
            anchorKind = command.anchorKind,
            anchorId = command.anchorId,
            anchorRevisionId = command.anchorRevisionId,
            status = TutorConversationStatus.ACTIVE,
            title = command.title,
            createdAtEpochMillis = command.createdAtEpochMillis,
            updatedAtEpochMillis = command.createdAtEpochMillis,
            lastTurnOrdinal = 0,
        )
        conversations.update { rows -> rows + (created.conversationId to created) }
        return created
    }

    override suspend fun appendStudentMessage(
        command: AppendTutorStudentMessageCommand,
    ): TutorMessage = error("No student write expected")

    override suspend fun appendAssistantMessage(
        command: AppendTutorAssistantMessageCommand,
    ): TutorMessage {
        // 幂等：消息 id 命中既有行就是同一行（Room 的 INSERT OR IGNORE 语义）。
        assistantTurns.firstOrNull { row -> row.messageId == command.messageId }?.let { row ->
            return row.toMessage(command)
        }
        check(command.conversationId in conversations.value) {
            "FOREIGN KEY constraint failed (code 787)"
        }
        val existing = conversations.value.getValue(command.conversationId)
        val ordinal = command.ordinal ?: (existing.lastTurnOrdinal + 1)
        conversations.update { rows ->
            rows + (
                command.conversationId to existing.copy(
                    updatedAtEpochMillis = command.createdAtEpochMillis,
                    lastTurnOrdinal = ordinal,
                )
                )
        }
        return RecordedAssistantTurn(
            messageId = command.messageId,
            bodyMarkdown = command.bodyMarkdown,
            thinkingMarkdown = command.thinkingMarkdown,
            toolTraceJson = command.toolTraceJson,
            sourceImageAssetIds = command.sourceImageAssetIds,
            replyToMessageId = command.replyToMessageId,
            logicalOperationId = command.logicalOperationId,
            requestedOrdinal = command.ordinal,
        ).also(assistantTurns::add).toMessage(command)
    }

    override suspend fun updateMessageStatus(
        command: UpdateTutorMessageStatusCommand,
    ): TutorMessage = error("No message status write expected")

    override suspend fun pauseConversation(
        command: PauseTutorConversationCommand,
    ): TutorConversation = error("No pause expected")

    override suspend fun archiveConversation(
        command: ArchiveTutorConversationCommand,
    ): TutorConversation = error("No archive expected")

    override suspend fun deleteConversation(command: DeleteTutorConversationCommand) =
        error("No delete expected")

    override suspend fun saveDraft(command: SaveTutorConversationDraftCommand) =
        error("No draft write expected")

    override suspend fun clearDraft(command: ClearTutorConversationDraftCommand) =
        error("No draft clear expected")
}

/** 落到替身里的助手行：`requestedOrdinal` 是**调用方给的**号（null = 交给会话计数器）。 */
private data class RecordedAssistantTurn(
    val messageId: String,
    val bodyMarkdown: String,
    val thinkingMarkdown: String?,
    /** 这一轮的工具痕迹（B1）：与正文同一次写入。 */
    val toolTraceJson: String?,
    /** A2：本轮生成图的持久引用（与正文、痕迹同一次写入）。 */
    val sourceImageAssetIds: List<String>,
    val replyToMessageId: String?,
    val logicalOperationId: String?,
    val requestedOrdinal: Int?,
) {
    fun toMessage(command: AppendTutorAssistantMessageCommand) = TutorMessage(
        messageId = messageId,
        conversationId = command.conversationId,
        ordinal = 1,
        role = TutorMessageRole.ASSISTANT,
        bodyMarkdown = bodyMarkdown,
        thinkingMarkdown = thinkingMarkdown,
        toolTraceJson = toolTraceJson,
        sourceImageAssetIds = sourceImageAssetIds,
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = logicalOperationId,
        replyToMessageId = replyToMessageId,
        createdAtEpochMillis = command.createdAtEpochMillis,
        completedAtEpochMillis = requireNotNull(command.completedAtEpochMillis),
        errorCode = null,
    )
}
