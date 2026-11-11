package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorReplyVersion
import com.tingyun.smartmistakebook.core.domain.selectedTutorReplyVersions
import org.junit.Assert.assertEquals
import org.junit.Test

class TutorReplyVersionSelectionTest {
    @Test fun regenerationHasItsOwnDispatchBudgetWhileLegacyRetriesKeepTheirIdentity() {
        val original = com.tingyun.smartmistakebook.core.model.ModelTaskRequest(
            requestId = "original", input = com.tingyun.smartmistakebook.core.model.TutorLobbyInput("conversation", 1, "解释题目"),
            occurredAtEpochMillis = 1)
        val first = original.copy(requestId = "tutor-regen:first")
        val second = original.copy(requestId = "tutor-regen:second")
        fun fingerprint(request: com.tingyun.smartmistakebook.core.model.ModelTaskRequest) =
            com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint.of(request)
        org.junit.Assert.assertNotEquals(fingerprint(first), fingerprint(second))
        org.junit.Assert.assertNotEquals(fingerprint(first), fingerprint(original))
        assertEquals(fingerprint(first), fingerprint(first.copy(occurredAtEpochMillis = 2)))
        assertEquals(fingerprint(original), fingerprint(original.copy(requestId = "legacy-retry")))
    }

    @Test fun selectionReturnsOneCompleteVersionPerReply() {
        val first = TutorReplyVersion("v1", "reply-1", "conversation", "request-1", 1, selected = false)
        val second = TutorReplyVersion("v2", "reply-1", "conversation", "request-2", 2, selected = true)
        assertEquals(listOf(second), selectedTutorReplyVersions(listOf(first, second)))
    }

    @Test fun legacyAndInterruptedSelectionsHaveADeterministicFallback() {
        val first = TutorReplyVersion("v1", "reply-1", "conversation", "request-1", 1)
        val second = TutorReplyVersion("v2", "reply-1", "conversation", "request-2", 2)
        assertEquals(listOf(first), selectedTutorReplyVersions(listOf(second, first)))
        assertEquals(emptyList<TutorReplyVersion>(), selectedTutorReplyVersions(emptyList()))
    }

    @Test fun browsingLockedHistoryDoesNotChangeTheFollowUpContext() {
        val original = TutorReplyVersion("v1", "reply", "conversation", "request-1", 1,
            selected = false, locked = true, bodyMarkdown = "旧版正文")
        val chosen = TutorReplyVersion("v2", "reply", "conversation", "request-2", 2,
            selected = true, locked = true, bodyMarkdown = "已选正文", sourceImageAssetIds = listOf("figure-2"))
        val message = com.tingyun.smartmistakebook.core.domain.TutorMessage(
            messageId = "reply", conversationId = "conversation", ordinal = 1,
            role = com.tingyun.smartmistakebook.core.domain.TutorMessageRole.ASSISTANT,
            bodyMarkdown = chosen.bodyMarkdown,
            status = com.tingyun.smartmistakebook.core.domain.TutorMessageStatus.SUCCEEDED,
            logicalOperationId = chosen.requestId, replyToMessageId = null,
            createdAtEpochMillis = 0, completedAtEpochMillis = 1, errorCode = null)
        val state = TutorConversationUiState(messages = listOf(message), replyVersions = listOf(original, chosen),
            viewedReplyVersions = mapOf("reply" to "v1"))
        assertEquals("旧版正文", state.displayMessages().single().bodyMarkdown)
        assertEquals("已选正文", state.messages.single().bodyMarkdown)
        assertEquals("v2", selectedTutorReplyVersions(state.replyVersions).single().versionId)
    }

    @Test fun aPendingOldViewCannotOverrideAnEditableSelection() {
        val selected = TutorReplyVersion("v2", "reply", "conversation", "request-2", 2, selected = true)
        val first = TutorReplyVersion("v1", "reply", "conversation", "request-1", 1)
        val state = TutorConversationUiState(replyVersions = listOf(first, selected), viewedReplyVersions = mapOf("reply" to "v1"))
        assertEquals("v2", selectedTutorReplyVersions(state.displayReplyVersions()).single().versionId)
    }
}
