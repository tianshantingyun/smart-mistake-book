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

    /**
     * B7：标题 = **首条消息截取** + 时间。写侧不再把题名塞进 `title`，所以首条消息优先于
     * `title` 列；`title` 只作为旧行的兜底（升级前写下的行带着题名），两者都没有才回落入口标签。
     */
    @Test
    fun theTitleComesFromTheFirstMessageNotFromTheStoredQuestionTitle() {
        val withBoth = conversation(
            conversationId = "conversation-1",
            messageCount = 2,
            title = "函数单调性",
            firstMessageBodyMarkdown = "# 求函数的单调区间",
        )
        val legacyRowOnly = conversation(
            conversationId = "conversation-2",
            messageCount = 1,
            title = "函数单调性",
        )
        val neither = conversation(conversationId = "conversation-3", messageCount = 1)

        assertEquals("求函数的单调区间", tutorConversationTitle(withBoth))
        assertEquals("函数单调性", tutorConversationTitle(legacyRowOnly))
        assertEquals("文字讲题", tutorConversationTitle(neither))
    }

    private fun conversation(
        conversationId: String,
        messageCount: Int,
        title: String? = null,
        firstMessageBodyMarkdown: String? = null,
    ) = TutorConversation(
        conversationId = conversationId,
        anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
        anchorId = null,
        anchorRevisionId = null,
        status = TutorConversationStatus.ACTIVE,
        title = title,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
        lastTurnOrdinal = messageCount,
        messageCount = messageCount,
        firstMessageBodyMarkdown = firstMessageBodyMarkdown,
    )
}
