package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.RepointTutorMessageVisualCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **V-04 竞态面补缺（批次 38）**：只落既有设备套件**没有**的负向向量（实读核对后的缺口矩阵，
 * 已被钉住的向量不重复——引文见各用例 KDoc）：
 *
 * 1. **事务拒绝后同轮可重试**：`failedMessageWriteRollsBackProgramSnapshotAndLink`
 *    （`TutorVisualCommitInstrumentedTest`）钉了"失败全体回滚"，但没钉"回滚后同一轮能再提交成功、
 *    无半行残留"——预算拒绝路径（plan 层、任何写之前）与此处互补。
 * 2. **A 的在飞租约行扛过 B 的提交后，A 手里的 `(programId, snapshotId)` 仍可提交**：
 *    `freshForeignStagingLeaseIsNotEvictedByAnotherRequest`
 *    （`TutorVisualCommitRepositoryInstrumentedTest`）钉了"行不被删"，没钉后半边
 *    "A 随后提交仍成功"——`DEFAULT_VISUAL_STAGING_LEASE_MILLIS` KDoc
 *    （`TutorVisualCommitRepository.kt:29-36`）登记的失败的完整闭环。
 * 3. **迟到 repoint 指向未提升（STAGING）/悬空快照**：陈旧 revision 与跨 program 向量已由
 *    `stagingLeaseAndPointerCasUseChangedRowCounts`（端口级）与
 *    `messageLinkRejectsForeignKeyProgramAndStagingSnapshots`（DAO 级）钉住；
 *    CAS 的 `EXISTS` 子查询还要求目标 `status = LIVE`——这条"半成品/悬空目标命中 0"此前无人钉。
 *
 * **本轨纪律**：不运行 Gradle/设备命令（门禁由脚本串行执行）；判据是设备轨要执行的断言，
 * 不是已通过的结果。方法名无空格（D8 SimpleName 约束）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualCommitRaceInstrumentedTest {

    @Test
    fun aRejectedTurnLeavesNoHalfRowsAndTheSameTurnSucceedsOnRetry() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val program = programInput()
            val programId = programIdOf(program)

            // 基线：29 字节 LIVE 快照（INITIAL），占用 per-program 预算。
            val defaultRepository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            defaultRepository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-race-baseline"),
                program = program,
                snapshot = snapshotInput("a"),
                ownerRequestId = "owner-race-baseline",
            )

            // 同一轮在窄预算下被拒（58 > 40，两条 LIVE 行都不可淘汰 → plan 在任何写之前拒绝）。
            val tightRepository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
                perProgramBudgetBytes = 40L,
            )
            val retried = snapshotInput("b")
            val retriedSnapshotId = tightRepository.snapshotIdFor(programId, retried)
            val rejection = runCatching {
                tightRepository.commitAssistantTurnWithVisual(
                    message = assistantCommand("message-race-retry"),
                    program = program,
                    snapshot = retried,
                    ownerRequestId = "owner-race-retry",
                )
            }.exceptionOrNull()
            assertTrue(
                "窄预算下必须有界拒绝：$rejection",
                rejection is TutorVisualCommitRejectedException,
            )
            assertEquals(
                TutorVisualCommitRejection.PER_PROGRAM_BUDGET,
                (rejection as TutorVisualCommitRejectedException).reason,
            )
            assertNull("拒绝零新增：快照行不留", store.readTutorVisualSnapshot(retriedSnapshotId))
            assertNull("拒绝零新增：消息不留", store.readTutorMessageVisual("message-race-retry", 0))

            // 同一轮（同消息 id、同 program/snapshot）以合法预算重试 → 全部成立。
            val committed = defaultRepository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-race-retry"),
                program = program,
                snapshot = retried,
                ownerRequestId = "owner-race-retry",
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertEquals("message-race-retry", committed.message.messageId)
            assertEquals(1, committed.link.revision)
            assertNotNull(store.readTutorMessageVisual("message-race-retry", 0))
        }
    }

    @Test
    fun aFreshStagingLeaseSurvivesAnotherCommitAndTheOwnerCanStillCommit() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            var now = 9_000L
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { now },
                stagingLeaseMillis = LEASE_MILLIS,
            )
            val program = programInput()

            // B 的基线提交（INITIAL）。
            repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-race-b1"),
                program = program,
                snapshot = snapshotInput("a"),
                ownerRequestId = "owner-race-b1",
            )

            // A stage 自己的预备行（LATEST、租约新鲜）。
            val aSnapshot = snapshotInput("b")
            val staged = repository.stageSnapshot(
                program = program,
                snapshot = aSnapshot,
                ownerRequestId = "owner-race-a",
            )
            assertEquals(TutorVisualStatus.STAGING, store.readTutorVisualSnapshot(staged.snapshotId)?.status)

            // B 的第二次提交：plan 会把 A 的行列进淘汰候选（未固定、STAGING、LATEST），
            // 但租约 SQL 拒删（owner 不同且未过期）。
            repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-race-b2"),
                program = program,
                snapshot = snapshotInput("c"),
                ownerRequestId = "owner-race-b2",
            )
            assertEquals(
                "B 的提交不得删 A 的在飞预备行",
                TutorVisualStatus.STAGING,
                store.readTutorVisualSnapshot(staged.snapshotId)?.status,
            )

            // 后半边（本用例的新增分辨力）：A 手里的 (programId, snapshotId) 仍有效，
            // A 的提交正常成立——不被 B 的 GC 逼成"重建行或失败"。
            // 注意不断言"latest live"：promoted_at 取快照创建时间（staged 的 createdAt 早于
            // B2 的 c 行），该排序语义由 TutorVisualRebuildRestoreInstrumentedTest 用单调
            // 创建时间钉住，此处断言会不确定。
            now += 1_000
            val aCommit = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-race-a1"),
                program = program,
                snapshot = aSnapshot,
                ownerRequestId = "owner-race-a",
            )
            assertEquals(TutorVisualStatus.LIVE, aCommit.snapshot.status)
            assertEquals(
                "A 的快照 id 与 stage 时发放的同一个（没有偷偷重建行）",
                staged.snapshotId,
                aCommit.snapshot.snapshotId,
            )
            assertEquals("owner-race-a 提交后 link 成立", 1, aCommit.link.revision)
            assertEquals(
                "A 的预备行已被提升为 LIVE",
                TutorVisualStatus.LIVE,
                store.readTutorVisualSnapshot(staged.snapshotId)?.status,
            )
        }
    }

    @Test
    fun aLateCheckpointCannotRepointAtAnUnpromotedOrMissingSnapshot() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val program = programInput()

            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-race-late"),
                program = program,
                snapshot = snapshotInput("a"),
                ownerRequestId = "owner-race-late",
            )
            assertEquals(1, committed.link.revision)

            // 一张从未提升的 STAGING 快照（阶段半途被取消的"迟到结果"）。
            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput("b"),
                ownerRequestId = "owner-race-stale",
            )

            assertFalse(
                "未提升（STAGING）的快照不能接住消息指针",
                store.repointTutorMessageVisual(
                    RepointTutorMessageVisualCommand(
                        messageId = "message-race-late",
                        ordinal = 0,
                        expectedRevision = 1,
                        nextSnapshotId = staged.snapshotId,
                        nowEpochMillis = 9_001,
                    ),
                ),
            )
            assertFalse(
                "悬空快照 id 不能接住消息指针",
                store.repointTutorMessageVisual(
                    RepointTutorMessageVisualCommand(
                        messageId = "message-race-late",
                        ordinal = 0,
                        expectedRevision = 1,
                        nextSnapshotId = "snap-does-not-exist",
                        nowEpochMillis = 9_002,
                    ),
                ),
            )
            val link = requireNotNull(store.readTutorMessageVisual("message-race-late", 0))
            assertEquals("两次迟到者都没动指针", committed.link.currentSnapshotId, link.currentSnapshotId)
            assertEquals("两次迟到者都没递增 revision", 1, link.revision)
        }
    }

    // ------------------------------------------------------------ 夹具

    private suspend fun withDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch38-visual-race-${System.nanoTime()}.db"
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
        toolTraceJson = """[{"tool":"plot","status":"OK"}]""",
        sourceImageAssetIds = emptyList(),
        logicalOperationId = "operation-$messageId",
        status = "SUCCEEDED",
        createdAtEpochMillis = 1_000,
        completedAtEpochMillis = 1_000,
        errorCode = null,
    )

    private fun programInput() = TutorVisualProgramInput(
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceJs = "draw();",
        initialStateJson = "{}",
        basisFingerprint = "basis-batch38-race",
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
    )

    private fun programIdOf(program: TutorVisualProgramInput): String =
        TutorVisualIdentity.deriveProgramId(
            sourceJs = program.sourceJs,
            initialStateJson = program.initialStateJson,
            basisFingerprint = program.basisFingerprint,
            apiVersion = program.apiVersion,
            sdkVersion = program.sdkVersion,
            role = program.role,
        )

    /** 与既有套件同形：`drawTag` 不同 = 内容不同 = 另一张快照（仓库 budget 用例同款字节量）。 */
    private fun snapshotInput(drawTag: String) = TutorVisualSnapshotInput(
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
    )

    private companion object {
        const val CONVERSATION_ID = "conversation-batch38-race"
        const val LEARNER_ID = "learner-batch38"

        /** 租约窗口（用例注入，与既有套件同款常量）。 */
        const val LEASE_MILLIS = 60L * 60L * 1_000L
    }
}
