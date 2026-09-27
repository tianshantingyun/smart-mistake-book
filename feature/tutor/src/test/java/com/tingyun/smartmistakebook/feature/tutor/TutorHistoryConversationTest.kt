package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * K1b：空会话不落库——历史列表只列**有消息**的会话。
 *
 * 反证：把 `tutorHistoryConversations` 的过滤去掉（等于"最近会话全都列"），
 * `anEmptyConversationIsNotListed` 转红。
 */
class TutorHistoryConversationTest {

    @Test
    fun anEmptyConversationIsNotListed() {
        val empty = conversation("conversation-empty", messageCount = 0)
        val withMessages = conversation("conversation-with-messages", messageCount = 2)

        assertEquals(
            listOf("conversation-with-messages"),
            tutorHistoryConversations(listOf(empty, withMessages)).map { it.conversationId },
        )
    }

    @Test
    fun conversationsAreListedWholeAndInOrder() {
        val first = conversation("conversation-1", messageCount = 1)
        val second = conversation("conversation-2", messageCount = 5)

        assertEquals(
            listOf(first, second),
            tutorHistoryConversations(listOf(first, second)),
        )
    }

    private fun conversation(conversationId: String, messageCount: Int) = TutorConversation(
        conversationId = conversationId,
        anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
        anchorId = null,
        anchorRevisionId = null,
        status = TutorConversationStatus.ACTIVE,
        title = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
        lastTurnOrdinal = messageCount,
        messageCount = messageCount,
    )
}
