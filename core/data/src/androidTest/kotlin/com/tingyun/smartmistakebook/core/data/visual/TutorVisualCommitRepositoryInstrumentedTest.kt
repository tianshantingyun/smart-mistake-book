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
    fun stageThenCommitStillDemotesThePreviousLatestCheckpoint() = runBlocking {
        // 批次 31 消灭的具体失败：S3（stage）→ S4（commit）是常规路径，但改前
        // `planCheckpoint` 看到"同 snapshotId 的行已存在"就直接返回空 demote/evict，
        // 于是 demote/evict 在真实写入上一次都不发生（两个 LATEST 并存）。
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val program = programInput()

            val first = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-stage-1"),
                program = program,
                snapshot = snapshotInput(drawTag = "a"),
                ownerRequestId = "owner-stage-1",
            )
            assertEquals(
                "首张快照是 INITIAL 基线",
                "INITIAL",
                first.snapshot.checkpointKind,
            )

            val second = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-stage-2"),
                program = program,
                snapshot = snapshotInput(drawTag = "b"),
                ownerRequestId = "owner-stage-2",
            )
            assertEquals("第二张是 LATEST", "LATEST", second.snapshot.checkpointKind)

            // 先落 STAGING（尚未被消息引用），再走提交提升：demote 必须在这里发生。
            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(drawTag = "c"),
                ownerRequestId = "owner-stage-3",
            )
            assertEquals("STAGING 行", "STAGING", store.readTutorVisualSnapshot(staged.snapshotId)?.status)
            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-stage-3"),
                program = program,
                snapshot = snapshotInput(drawTag = "c"),
                ownerRequestId = "owner-stage-3",
            )

            assertEquals("新快照提升为 LIVE", TutorVisualStatus.LIVE, committed.snapshot.status)
            assertEquals("新快照接手 LATEST 标记", "LATEST", committed.snapshot.checkpointKind)
            assertEquals(
                "旧 LATEST 必须被降级为可回收中间态（stage→commit 路径也要接线）",
                null,
                store.readTutorVisualSnapshot(second.snapshot.snapshotId)?.checkpointKind,
            )
            assertEquals(
                "INITIAL 基线永不被降级",
                "INITIAL",
                store.readTutorVisualSnapshot(first.snapshot.snapshotId)?.checkpointKind,
            )
        }
    }

    @Test
    fun commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand() = runBlocking {
        // 复核第 1 轮：既有仓库用例的旧快照都是 LIVE（策略对 LIVE 一律不淘汰），因此
        // `planCheckpoint` 在设备面从未产出过非空 evict 列表。这里先 stage 一张不提交的
        // STAGING（kind=LATEST），再提交另一张：两者无引用关系 ⇒ evict 候选非空，必须
        // 沿 plan→command 传到事务内并被删除（不是只证 demote 腿）。
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val program = programInput()

            val first = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-evict-1"),
                program = program,
                snapshot = snapshotInput(drawTag = "a"),
                ownerRequestId = "owner-evict-1",
            )
            assertEquals("INITIAL", first.snapshot.checkpointKind)

            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(drawTag = "b"),
                ownerRequestId = "owner-evict-2",
            )
            assertEquals("STAGING", store.readTutorVisualSnapshot(staged.snapshotId)?.status)

            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-evict-3"),
                program = program,
                snapshot = snapshotInput(drawTag = "c"),
                ownerRequestId = "owner-evict-3",
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertNull(
                "无引用的 STAGING 中间态必须被 plan→command 淘汰",
                store.readTutorVisualSnapshot(staged.snapshotId),
            )
            assertEquals(
                "INITIAL 基线仍在",
                TutorVisualStatus.LIVE,
                store.readTutorVisualSnapshot(first.snapshot.snapshotId)?.status,
            )
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

    private fun snapshotInput(drawTag: String = "a") = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = 1,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = 1,
        drawCommandsJson = """{"ops":["$drawTag"]}""",
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
