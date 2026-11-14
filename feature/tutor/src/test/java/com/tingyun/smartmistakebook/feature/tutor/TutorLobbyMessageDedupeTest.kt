package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TutorLobbyMessageDedupeTest {

    private fun student(ordinal: Int, id: String = "s$ordinal") = TutorMessage(
        messageId = id,
        conversationId = "c1",
        ordinal = ordinal,
        role = TutorMessageRole.STUDENT,
        bodyMarkdown = "消息 $ordinal",
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = "op-$ordinal",
        replyToMessageId = null,
        createdAtEpochMillis = ordinal.toLong(),
        completedAtEpochMillis = ordinal.toLong(),
        errorCode = null,
    )

    private fun assistant(ordinal: Int, replyTo: String, body: String) = TutorMessage(
        messageId = "a-$ordinal",
        conversationId = "c1",
        ordinal = ordinal,
        role = TutorMessageRole.ASSISTANT,
        bodyMarkdown = body,
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = "op-$replyTo",
        replyToMessageId = replyTo,
        createdAtEpochMillis = ordinal.toLong(),
        completedAtEpochMillis = ordinal.toLong(),
        errorCode = null,
    )

    @Test
    fun regenerateKeepsOnlyTheLatestReplyForASingleStudentMessage() {
        val messages = listOf(
            student(1),
            assistant(2, "s1", "旧回复"),
            assistant(3, "s1", "新回复"),
        )
        assertEquals(listOf("s1", "a-3"), latestTutorLobbyMessages(messages).map { it.messageId })
    }

    @Test
    fun differentStudentMessagesKeepTheirOwnReplies() {
        val messages = listOf(
            student(1),
            assistant(2, "s1", "回复一"),
            student(3, "s3"),
            assistant(4, "s3", "回复二"),
        )
        assertEquals(
            listOf("s1", "a-2", "s3", "a-4"),
            latestTutorLobbyMessages(messages).map { it.messageId },
        )
    }
}
