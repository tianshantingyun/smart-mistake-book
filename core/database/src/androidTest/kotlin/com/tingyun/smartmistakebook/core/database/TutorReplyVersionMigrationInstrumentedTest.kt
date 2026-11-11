package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorReplyVersionMigrationInstrumentedTest {
    @Test fun version75PreservesMessagesAndAcceptsIndependentVersions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reply-versions-${System.nanoTime()}.db"
        createDatabaseFromExportedSchema(context, name, version = 75)
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { old ->
            old.execSQL("""INSERT INTO tutor_conversation(conversation_id, anchor_kind, status,
                created_at_epoch_millis, updated_at_epoch_millis, last_turn_ordinal)
                VALUES('version-conversation','TEXT_ONLY','ACTIVE',1000,1001,1)""")
            old.execSQL("""INSERT INTO tutor_message(message_id, conversation_id, ordinal, role, body_markdown,
                status, logical_operation_id, created_at_epoch_millis, completed_at_epoch_millis)
                VALUES('reply','version-conversation',1,'ASSISTANT','原始讲解','SUCCEEDED','request-original',1000,1001)""")
        }
        try {
            var store = StudyDatabaseFactory.open(context, name)
            assertEquals(76, store.readDatabaseVersion())
            assertEquals("原始讲解", store.observeTutorMessages("version-conversation").first().single().bodyMarkdown)
            val first = version("v1", 1, "原始讲解")
            val second = version("v2", 2, "新的讲解")
            assertTrue(store.publishTutorReplyVersion(first, second))
            assertTrue(store.publishTutorReplyVersion(first, second))
            assertEquals(2, store.observeTutorReplyVersions("version-conversation").first().size)
            assertTrue(store.saveTutorReplyVersionAnswer("v2", null, "{\"choice\":\"B\"}", 2000))
            assertTrue(store.selectTutorReplyVersion("version-conversation", "reply", "v1"))
            assertTrue(store.saveTutorReplyVersionAnswer("v1", null, "{\"choice\":\"A\"}", 2001))
            store.close()
            store = StudyDatabaseFactory.open(context, name)
            val restored = store.observeTutorReplyVersions("version-conversation").first()
            assertEquals("{\"choice\":\"A\"}", restored.single { it.versionId == "v1" }.responseJson)
            assertEquals("{\"choice\":\"B\"}", restored.single { it.versionId == "v2" }.responseJson)
            assertEquals("原始讲解", store.observeTutorMessages("version-conversation").first().single().bodyMarkdown)
            store.appendTutorStudentMessage(AppendTutorStudentMessageDatabaseCommand(
                conversationId = "version-conversation", messageId = "follow-up", ordinal = 2,
                bodyMarkdown = "接着解释", logicalOperationId = "follow-up", createdAtEpochMillis = 3000))
            assertTrue(store.observeTutorReplyVersions("version-conversation").first().all { it.locked })
            assertFalse(store.selectTutorReplyVersion("version-conversation", "reply", "v2"))
            assertFalse(store.saveTutorReplyVersionAnswer("v1", "{\"choice\":\"A\"}", "{}", 4000))
            assertFalse(store.publishTutorReplyVersion(first, version("v3", 3, "迟到的讲解")))
            store.deleteTutorConversation("version-conversation")
            assertTrue(store.observeTutorReplyVersions("version-conversation").first().isEmpty())
            store.close()
        } finally { context.deleteDatabase(name) }
    }

    @Test fun backendReadersSeeOnlyTheChosenVersionsOwnAnswer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StudyDatabaseFactory.openInMemory(context)
        try {
            val session = "versioned-session"
            val conversation = com.tingyun.smartmistakebook.core.model.TutorConversationIds.captured(session)
            store.recordTutorChoice(PersistTutorChoiceCommand(sessionId = session, questionDocumentId = "doc",
                revisionNumber = 1, cycleOrdinal = 1, turnOrdinal = 1, diagnosticStemMarkdown = "第一版题卡",
                selectedChoiceId = "A", selectedChoiceMarkdown = "原选项", selectionWasCorrect = true,
                feedbackMarkdown = "原反馈", choiceSubmittedAtEpochMillis = 1000))
            store.appendTutorAssistantMessage(AppendTutorAssistantMessageDatabaseCommand(
                conversationId = conversation, messageId = "reply", replyToMessageId = null,
                bodyMarkdown = "原始讲解", logicalOperationId = "request-original", status = "SUCCEEDED",
                createdAtEpochMillis = 1001, completedAtEpochMillis = 1002, errorCode = null))
            val planRequest = """{"input":{"type":"tutor_plan","sessionId":"versioned-session","draftRevisionNumber":1,
                "questionDocument":{"id":"doc"},"cycleOrdinal":1,"turnOrdinal":1}}"""
            fun answer(correct: Boolean) = """{"sessionId":"versioned-session","questionDocumentId":"doc",
                "revisionNumber":1,"cycleOrdinal":1,"turnOrdinal":1,"diagnosticStemMarkdown":"题卡",
                "selectedChoiceId":"A","selectedChoiceMarkdown":"选项","selectionWasCorrect":$correct,
                "feedbackMarkdown":"反馈","requestedMove":null,"solutionRevealed":false,
                "submittedAtEpochMillis":1000,"updatedAtEpochMillis":2000,"choiceSubmittedAtEpochMillis":1000}"""
            val first = version("v1", 1, "原始讲解").copy(conversationId = conversation,
                requestJson = planRequest, responseJson = answer(true))
            val second = version("v2", 2, "新版讲解").copy(conversationId = conversation, requestJson = planRequest)
            assertTrue(store.publishTutorReplyVersion(first, second))
            assertTrue(store.observeTutorTurnResponses(session).first().isEmpty())
            assertTrue(store.saveTutorReplyVersionAnswer("v2", null, answer(false), 2000))
            assertEquals(false, store.observeTutorTurnResponses(session).first().single().selectionWasCorrect)
            assertTrue(store.selectTutorReplyVersion(conversation, "reply", "v1"))
            assertEquals(true, store.observeTutorTurnResponses(session).first().single().selectionWasCorrect)
            store.appendTutorStudentMessage(AppendTutorStudentMessageDatabaseCommand(
                conversationId = conversation, messageId = "after-answer", ordinal = 3,
                bodyMarkdown = "给我完整解答", logicalOperationId = "follow-up", createdAtEpochMillis = 3000))
            val revealed = answer(true).replace("\"solutionRevealed\":false", "\"solutionRevealed\":true")
                .replace("\"updatedAtEpochMillis\":2000", "\"updatedAtEpochMillis\":3000")
            assertTrue(store.saveTutorReplyVersionAnswer("v1", answer(true), revealed, 3000, allowLockedReveal = true))
            val afterReveal = store.observeTutorTurnResponses(session).first().single()
            assertTrue(afterReveal.solutionRevealed)
            assertEquals(true, afterReveal.selectionWasCorrect)
            var rejected = false
            try {
                store.saveTutorReplyVersionAnswer("v1", revealed,
                    revealed.replace("\"selectedChoiceId\":\"A\"", "\"selectedChoiceId\":\"B\""),
                    3001, allowLockedReveal = true)
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        } finally { store.close() }
    }

    @Test fun regeneratedResponsesHaveIndependentOperationsWithoutConflictingWithTheOriginalSlot() = runBlocking {
        val store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext())
        try {
            val input = com.tingyun.smartmistakebook.core.model.TutorRespondInput(
                sessionId = "budget-session", draftRevisionNumber = 1, subject = "MATH",
                questionDocument = com.tingyun.smartmistakebook.core.model.QuestionDocument(id = "budget-doc",
                    blocks = listOf(com.tingyun.smartmistakebook.core.model.ContentBlock.Paragraph("stem", "求函数的单调区间。"))),
                relevantLearningEvidence = emptyList(), projectionIsCurrent = true, responseOrdinal = 1,
                studentMessage = "解释这道题")
            for (id in listOf("original", "tutor-regen:first", "tutor-regen:second")) {
                val request = com.tingyun.smartmistakebook.core.model.ModelTaskRequest(requestId = id, input = input,
                    occurredAtEpochMillis = 1000)
                val result = store.createModelTask(CreateModelTaskCommand(taskId = "task-$id", request = request,
                    requestFingerprint = com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint.of(request),
                    occurredAtEpochMillis = 1000))
                assertEquals(id, result.snapshot.request.requestId)
            }
        } finally { store.close() }
    }

    private fun version(id: String, ordinal: Int, body: String) = TutorReplyVersionRecord(
        versionId = id, groupId = "reply", conversationId = "version-conversation", requestId = if (id == "v1") "request-original" else "request-$id",
        ordinal = ordinal, originalRequestId = "request-original", bodyMarkdown = body, thinkingMarkdown = null,
        toolTraceJson = null, sourceAssetIdsJson = "[]", requestJson = "{}", outputJson = "{}",
        createdAtEpochMillis = 1000L + ordinal, selected = false, locked = false, responseJson = null)
}
