package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.database.TutorConversationRecord
import com.tingyun.smartmistakebook.core.database.TutorMessageRecord
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话唯一读投影（K1）：**一条订阅**里同时给出"说了什么"（messages）与"这一轮发生了什么"
 * （turns），且轮次行不会被当成对话消息。
 *
 * 判别格是**轮次行**（`LOCAL_EVENT`、正文为空、只带轮次列）：它在 `turns` 里、不在 `messages` 里。
 * 修复前的形状（轮次行也进 messages）会让映射对空正文抛 `require`——学生一提交选择题，
 * 整条会话读流就地崩掉。
 */
class TutorConversationSnapshotTest {
    @Test
    fun roundRowsBecomeTurnFactsAndStayOutOfTheMessageStream() {
        val snapshot = conversationRecord().toConversationSnapshot(
            records = listOf(
                studentMessageRecord(ordinal = 1, body = "这题怎么做"),
                roundRow(cycleOrdinal = 1, turnOrdinal = 1, selectedChoiceId = "choice-2"),
                assistantMessageRecord(ordinal = 3, body = "先看单调性"),
            ),
        )

        assertEquals(
            listOf("这题怎么做", "先看单调性"),
            snapshot.messages.map { message -> message.bodyMarkdown },
        )
        assertTrue(
            snapshot.messages.none { message -> message.role == TutorMessageRole.LOCAL_EVENT },
        )

        val turn = snapshot.turns.single()
        assertEquals("session-1", turn.sessionId)
        assertEquals("question-1", turn.questionDocumentId)
        assertEquals(2, turn.revisionNumber)
        assertEquals(1, turn.cycleOrdinal)
        assertEquals(1, turn.turnOrdinal)
        assertEquals("choice-2", turn.selectedChoiceId)
        assertEquals(true, turn.selectionWasCorrect)
        assertEquals("这个判断是对的。", turn.feedbackMarkdown)
    }

    @Test
    fun turnFactsComeOutInRoundOrder() {
        val snapshot = conversationRecord().toConversationSnapshot(
            records = listOf(
                roundRow(cycleOrdinal = 2, turnOrdinal = 1, selectedChoiceId = "choice-3"),
                roundRow(cycleOrdinal = 1, turnOrdinal = 2, selectedChoiceId = "choice-1"),
                // 揭示不是 requestedMove（域模型明确禁止），它由 solutionRevealed 记。
                roundRow(cycleOrdinal = 1, turnOrdinal = 1, solutionRevealed = true),
            ),
        )

        assertEquals(
            listOf(1 to 1, 1 to 2, 2 to 1),
            snapshot.turns.map { turn -> turn.cycleOrdinal to turn.turnOrdinal },
        )
    }

    @Test
    fun aConversationWithoutRoundRowsHasNoTurnFacts() {
        val snapshot = conversationRecord().toConversationSnapshot(
            records = listOf(studentMessageRecord(ordinal = 1, body = "只聊了这一句")),
        )

        assertTrue(snapshot.turns.isEmpty())
        assertEquals(1, snapshot.messages.size)
    }

    private fun conversationRecord() = TutorConversationRecord(
        conversationId = "tutor-conv:captured:session-1",
        conversationArea = TutorConversationAreas.AGENT,
        anchorKind = TutorConversationAnchorKind.EPHEMERAL_DRAFT.name,
        anchorId = "session-1",
        anchorRevisionId = "question-1:2",
        status = TutorConversationStatus.ACTIVE.name,
        title = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
        lastTurnOrdinal = 3,
        studentDraft = null,
    )

    private fun studentMessageRecord(
        ordinal: Int,
        body: String,
        messageId: String = "tutor-message:$ordinal",
    ) = TutorMessageRecord(
        messageId = messageId,
        conversationId = "tutor-conv:captured:session-1",
        ordinal = ordinal,
        role = TutorMessageRole.STUDENT.name,
        bodyMarkdown = body,
        status = "PERSISTED",
        logicalOperationId = "op-$ordinal",
        replyToMessageId = null,
        createdAtEpochMillis = ordinal.toLong(),
        completedAtEpochMillis = ordinal.toLong(),
        errorCode = null,
    )

    private fun assistantMessageRecord(ordinal: Int, body: String) = TutorMessageRecord(
        messageId = "tutor-message-assistant:$ordinal",
        conversationId = "tutor-conv:captured:session-1",
        ordinal = ordinal,
        role = TutorMessageRole.ASSISTANT.name,
        bodyMarkdown = body,
        status = "SUCCEEDED",
        logicalOperationId = "op-$ordinal",
        replyToMessageId = "tutor-message:${ordinal - 2}",
        createdAtEpochMillis = ordinal.toLong(),
        completedAtEpochMillis = ordinal.toLong(),
        errorCode = null,
    )

    /** 轮次行：`LOCAL_EVENT` + 空正文，只带轮次列（真实形状）。 */
    private fun roundRow(
        cycleOrdinal: Int,
        turnOrdinal: Int,
        selectedChoiceId: String? = null,
        solutionRevealed: Boolean = false,
    ) = TutorMessageRecord(
        messageId = "tutor-round:session-1:$cycleOrdinal:$turnOrdinal",
        conversationId = "tutor-conv:captured:session-1",
        ordinal = 2,
        role = TutorMessageRole.LOCAL_EVENT.name,
        bodyMarkdown = "",
        roundCycleOrdinal = cycleOrdinal,
        roundTurnOrdinal = turnOrdinal,
        roundQuestionDocumentId = "question-1",
        roundRevisionNumber = 2,
        // 选择题五件要么全有、要么全无（域模型的不变式）：没选选项的轮次不带这些列。
        choiceStemMarkdown = selectedChoiceId?.let { "关键一步是什么？" },
        choiceSelectedId = selectedChoiceId,
        choiceSelectedMarkdown = selectedChoiceId?.let { "先判断符号" },
        choiceWasCorrect = selectedChoiceId?.let { true },
        choiceFeedbackMarkdown = selectedChoiceId?.let { "这个判断是对的。" },
        solutionRevealed = solutionRevealed,
        requestedMove = null,
        status = "PERSISTED",
        logicalOperationId = null,
        replyToMessageId = null,
        createdAtEpochMillis = 2,
        completedAtEpochMillis = 2,
        errorCode = null,
    )
}
