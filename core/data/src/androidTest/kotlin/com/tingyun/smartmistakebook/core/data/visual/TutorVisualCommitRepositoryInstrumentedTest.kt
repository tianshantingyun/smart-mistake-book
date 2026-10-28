package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **D-02 仓储层（批次 30 / 任务 4）的存储满与身份验收**。先于实现写（红）：
 *
 * 1. **磁盘预检在写库之前**（设计 §3.3 S0）：可用空间 0 ⇒ 有界拒绝，四表**零新增行**，
 *    不许让 `SQLiteFullException` 穿透成"提交了一半"；
 * 2. **`visualId = programId`**：仓储派生的身份就是快照行上的 `program_id`，且与纯函数
 *    `TutorVisualIdentity` 的结果逐字相同（没有第二个身份来源）；
 * 3. **正文/trace/link 同一次提交**：成功路径上三者一起出现，存储满路径上三者一起不出现。
 *
 * 本轨不运行 Gradle；门禁脚本串行跑
 * `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=…TutorVisualCommitRepositoryInstrumentedTest`
 * （按批次纪律，设备侧用 `assembleDebugAndroidTest` + `adb install -r -t` + `am instrument`）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualCommitRepositoryInstrumentedTest {

    @Test
    fun diskFullRejectsBeforeAnyVisualRowIsWritten() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { 0L },
                clock = { 9_000 },
            )
            val program = programInput()
            val snapshot = snapshotInput()

            val programId = repository.programIdFor(program)
            val snapshotId = repository.snapshotIdFor(programId, snapshot)
            val failure = runCatching {
                repository.commitAssistantTurnWithVisual(
                    message = assistantCommand(messageId = MESSAGE_ID),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_ID,
                )
            }

            val rejection = failure.exceptionOrNull()
            assertTrue(
                "磁盘不足必须是有界拒绝而不是 SQLiteFullException 穿透：$rejection",
                rejection is TutorVisualCommitRejectedException,
            )
            assertEquals(
                TutorVisualCommitRejection.DISK_SPACE_INSUFFICIENT,
                (rejection as TutorVisualCommitRejectedException).reason,
            )
            assertEquals(
                "拒绝发生在 S0，不许留下 program 行",
                0,
                store.countTutorVisualProgramsForTest(programId),
            )
            assertNull("拒绝发生在 S0，不许留下 snapshot 行", store.readTutorVisualSnapshot(snapshotId))
        }
    }

    @Test
    fun commitMakesVisualIdAndProgramIdTheSameAndWritesBodyTraceAndLinkTogether() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val program = programInput()
            val snapshot = snapshotInput()

            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = MESSAGE_ID),
                program = program,
                snapshot = snapshot,
                ownerRequestId = OWNER_ID,
                uiStateJson = """{"draft":true}""",
                formDigest = "digest-1",
            )

            val expectedProgramId = TutorVisualIdentity.deriveProgramId(
                sourceJs = program.sourceJs,
                initialStateJson = program.initialStateJson,
                basisFingerprint = program.basisFingerprint,
                apiVersion = program.apiVersion,
                sdkVersion = program.sdkVersion,
                role = program.role,
            )
            assertEquals("对外 visualId 与 program_id 是同一个值", expectedProgramId, committed.snapshot.programId)
            assertEquals(repository.programIdFor(program), committed.snapshot.programId)
            assertEquals(committed.snapshot.snapshotId, committed.link.currentSnapshotId)
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertEquals("正文与图同一次提交", "图在下面。", committed.message.bodyMarkdown)
            assertEquals("trace 与正文同一次提交", TOOL_TRACE, committed.message.toolTraceJson)
            assertEquals("""{"draft":true}""", committed.link.uiStateJson)
            assertEquals("digest-1", committed.link.formDigest)
        }
    }

    @Test
    fun snapshotOverSourceBudgetIsRejectedBeforeAnyWrite() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val tooBig = programInput(sourceJs = "a".repeat(64 * 1024 + 1))
            val programId = repository.programIdFor(tooBig)

            val failure = runCatching {
                repository.commitAssistantTurnWithVisual(
                    message = assistantCommand(messageId = MESSAGE_ID),
                    program = tooBig,
                    snapshot = snapshotInput(),
                    ownerRequestId = OWNER_ID,
                )
            }

            assertTrue(
                "源码超 64 KiB 必须在写库前拒绝：${failure.exceptionOrNull()}",
                failure.exceptionOrNull() is IllegalArgumentException,
            )
            assertEquals(0, store.countTutorVisualProgramsForTest(programId))
            assertTrue(store.readTutorMessageVisual(MESSAGE_ID, 0) == null)
        }
    }

    // ---- 夹具 ----

    private suspend fun withDatabase(
        block: suspend (StudyDatabasePort) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch30-visual-repo-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            block(store)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun conversationCommand() = CreateTutorConversationDatabaseCommand(
        conversationId = CONVERSATION_ID,
        anchorKind = "TEXT_ONLY",
        anchorId = null,
        anchorRevisionId = null,
        title = null,
        createdAtEpochMillis = 1_000,
    )

    private fun assistantCommand(messageId: String) = AppendTutorAssistantMessageDatabaseCommand(
        conversationId = CONVERSATION_ID,
        messageId = messageId,
        ordinal = null,
        replyToMessageId = null,
        bodyMarkdown = "图在下面。",
        thinkingMarkdown = "先算再画。",
        toolTraceJson = TOOL_TRACE,
        sourceImageAssetIds = emptyList(),
        logicalOperationId = "operation-$messageId",
        status = "SUCCEEDED",
        createdAtEpochMillis = 1_000,
        completedAtEpochMillis = 1_000,
        errorCode = null,
    )

    private fun programInput(sourceJs: String = "draw();") = TutorVisualProgramInput(
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceJs = sourceJs,
        initialStateJson = "{}",
        basisFingerprint = "basis-batch30",
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
        basisRevisionId = null,
        requestSnapshot = null,
    )

    private fun snapshotInput() = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = 1,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = 1,
        drawCommandsJson = """{"ops":["a"]}""",
        contentIds = emptySet(),
        viewportWidthDu = 360,
        viewportHeightDu = 640,
        fontScaleMilli = 1_000,
        theme = "LIGHT",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = "visual-renderer-v1",
        fontManifestVersion = "font-manifest-v1",
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = null,
    )

    private companion object {
        const val CONVERSATION_ID = "conversation-batch30-repo"
        const val LEARNER_ID = "learner-batch30"
        const val MESSAGE_ID = "message-batch30-repo"
        const val OWNER_ID = "request-batch30-repo"
        const val TOOL_TRACE = """[{"tool":"plot","status":"OK"}]"""
    }
}
