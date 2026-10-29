package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualCheckpointSummary
import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import com.tingyun.smartmistakebook.core.database.port.TutorVisualPort
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
 * 本轨不运行 Gradle；设备侧按批次纪律只用 `assembleDebugAndroidTest` + `adb install -r -t` + `am instrument`
 * （**禁用 `connectedAndroidTest`**），定向执行用 `-e class …TutorVisualCommitRepositoryInstrumentedTest`。
 * 批次 31 E-09 实跑 **4 例**（`docs/research/evidence/2026-10-29-batch-31/step09-persistence-structure.md:10/:77`）；
 * 批次 32 E-08 已在设备上实跑 **8 例**：`OK (8 tests)`（`build/device-track-batch32/step07-visual-repo.log`），
 * 含 plan→command evict、租约保留、per-program/global 事务内重判。复核修复轮新增第 **9** 例
 * （`stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`，stage 路径同一份
 * 淘汰口径），该例**尚未上设备**——不得把 8 例绿读成 9 例绿。
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
        //
        // 批次 32 起淘汰 SQL 还校验 STAGING 租约：本次提交的 owner 与 staged 行不同，
        // 因此把时钟推过租约窗口，模拟"该请求已放弃/超时"，才应回收它。
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

            val first = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-evict-1"),
                program = program,
                snapshot = snapshotInput(drawTag = "a"),
                ownerRequestId = "owner-evict-1",
            )
            assertEquals("INITIAL", first.snapshot.checkpointKind)

            // 第二张 LIVE 快照（LATEST）。第三次提交会把它降为 NULL 中间态，但它仍是 LIVE，
            // 淘汰集里绝不能出现——这就是"LIVE 中间态保留"的分辨力。
            val second = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-evict-2"),
                program = program,
                snapshot = snapshotInput(drawTag = "b"),
                ownerRequestId = "owner-evict-2",
            )
            assertEquals("LATEST", second.snapshot.checkpointKind)

            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(drawTag = "c"),
                ownerRequestId = "owner-evict-3",
            )
            assertEquals("STAGING", store.readTutorVisualSnapshot(staged.snapshotId)?.status)

            now += LEASE_MILLIS + 1L
            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-evict-4"),
                program = program,
                snapshot = snapshotInput(drawTag = "d"),
                ownerRequestId = "owner-evict-4",
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertNull(
                "租约已过期、无人引用的 STAGING 中间态必须被 plan→command 淘汰",
                store.readTutorVisualSnapshot(staged.snapshotId),
            )
            assertEquals(
                "INITIAL 基线仍在且仍为 LIVE",
                TutorVisualStatus.LIVE,
                store.readTutorVisualSnapshot(first.snapshot.snapshotId)?.status,
            )
            assertEquals(
                "LIVE 中间态（被降级的旧 LATEST）不得当缓存删除",
                TutorVisualStatus.LIVE,
                store.readTutorVisualSnapshot(second.snapshot.snapshotId)?.status,
            )
            assertNull(
                "旧 LATEST 的标记被新 checkpoint 接手",
                store.readTutorVisualSnapshot(second.snapshot.snapshotId)?.checkpointKind,
            )
        }
    }

    @Test
    fun freshForeignStagingLeaseIsNotEvictedByAnotherRequest() = runBlocking {
        // 批次 32 消灭的具体失败：淘汰 SQL 只看 pin/kind/status 时，B 请求会把 A 请求**刚刚**
        // stage、租约尚未过期的 STAGING 行当缓存删掉——A 已经拿到的 (programId, snapshotId)
        // 短暂失效。这里 A 的 stage 与 B 的 commit 使用同一时刻，断言 A 的行仍为 STAGING。
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
                stagingLeaseMillis = LEASE_MILLIS,
            )
            val program = programInput()

            repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-lease-1"),
                program = program,
                snapshot = snapshotInput(drawTag = "a"),
                ownerRequestId = "owner-lease-1",
            )
            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(drawTag = "b"),
                ownerRequestId = "owner-lease-2",
            )
            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-lease-3"),
                program = program,
                snapshot = snapshotInput(drawTag = "c"),
                ownerRequestId = "owner-lease-3",
            )

            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertEquals(
                "另一个请求刚 stage、租约未过期的行不得被本次提交当缓存删掉",
                TutorVisualStatus.STAGING,
                store.readTutorVisualSnapshot(staged.snapshotId)?.status,
            )
        }
    }

    @Test
    fun perProgramBudgetIsReinforcedInsideTheTransactionAfterAStaleRead() = runBlocking {
        // 批次 32 消灭的具体失败（并发双过）：预算此前只在事务外判。两个提交各自读到"还没超"
        // 的旧总量、都通过判定、都写入，最终超限且没有门拦。这里用 [StaleReadVisualPort] 把
        // 事务外的那次读**确定性地**变成陈旧的（返回空/0），复现并发窗口：
        // 预检放行 → 事务内重读真实总量 → 必须拒绝并回滚消息/link/snapshot。
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val program = programInput()
            commitBaseline(store, program)

            val staleBudget = 40L // 基线 29 字节可过；加第二张 29 字节后 58 > 40
            val repository = TutorVisualCommitRepository(
                database = StaleReadVisualPort(store),
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
                perProgramBudgetBytes = staleBudget,
            )
            val incoming = snapshotInput(drawTag = "b")
            val incomingId = repository.snapshotIdFor(repository.programIdFor(program), incoming)

            val failure = runCatching {
                repository.commitAssistantTurnWithVisual(
                    message = assistantCommand(messageId = "message-budget-per-program"),
                    program = program,
                    snapshot = incoming,
                    ownerRequestId = "owner-budget-per-program",
                )
            }

            val rejection = failure.exceptionOrNull()
            assertTrue(
                "事务内重读超 per-program 预算必须是有界拒绝：$rejection",
                rejection is TutorVisualCommitRejectedException,
            )
            assertEquals(
                TutorVisualCommitRejection.PER_PROGRAM_BUDGET,
                (rejection as TutorVisualCommitRejectedException).reason,
            )
            assertNull(
                "事务回滚：新快照行不留",
                store.readTutorVisualSnapshot(incomingId),
            )
            assertNull(
                "事务回滚：消息/link 不留",
                store.readTutorMessageVisual("message-budget-per-program", 0),
            )
        }
    }

    @Test
    fun globalBudgetIsReinforcedInsideTheTransactionAfterAStaleRead() = runBlocking {
        // 与上例同构，只把收紧的轴换成全库 32 MiB（这里注入 40 字节）：证明事务内重判
        // 覆盖两条预算轴，而不只是 per-program。
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val program = programInput()
            commitBaseline(store, program)

            val staleBudget = 40L
            val repository = TutorVisualCommitRepository(
                database = StaleReadVisualPort(store),
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
                globalBudgetBytes = staleBudget,
            )
            val incoming = snapshotInput(drawTag = "b")
            val incomingId = repository.snapshotIdFor(repository.programIdFor(program), incoming)

            val failure = runCatching {
                repository.commitAssistantTurnWithVisual(
                    message = assistantCommand(messageId = "message-budget-global"),
                    program = program,
                    snapshot = incoming,
                    ownerRequestId = "owner-budget-global",
                )
            }

            val rejection = failure.exceptionOrNull()
            assertTrue(
                "事务内重读超全局预算必须是有界拒绝：$rejection",
                rejection is TutorVisualCommitRejectedException,
            )
            assertEquals(
                TutorVisualCommitRejection.GLOBAL_BUDGET,
                (rejection as TutorVisualCommitRejectedException).reason,
            )
            assertNull(store.readTutorVisualSnapshot(incomingId))
            assertNull(store.readTutorMessageVisual("message-budget-global", 0))
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

    @Test
    fun stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate() = runBlocking {
        // 批次 32 复核修复：plan 在事务外按"淘汰后净值"判预算，而 stage 的事务内重判读全量
        // 字节；stage 路径此前只写 incomingKind、把 plan 的淘汰集丢掉，于是"总量超、净值不超"
        // 时预检放行、事务拒绝（与契约 §8.1"超限只淘汰中间态，淘汰后仍超才不提交"方向相反）。
        // 本用例把三张 29 字节快照做到 87 字节，预算 70：基线 LIVE 29 + 本次 29 = 58 可过，
        // 但必须先在同一事务里淘汰那张过期、未固定的 STAGING 行（29 字节）。
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            var now = 9_000L
            val looseRepository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { now },
                stagingLeaseMillis = LEASE_MILLIS,
            )
            val program = programInput()
            val baseline = looseRepository.commitAssistantTurnWithVisual(
                message = assistantCommand(messageId = "message-stage-budget-1"),
                program = program,
                snapshot = snapshotInput(drawTag = "a"),
                ownerRequestId = "owner-stage-budget-1",
            )
            val abandoned = looseRepository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(drawTag = "b"),
                ownerRequestId = "owner-stage-budget-2",
            )
            assertEquals(
                "被放弃的预备行此刻仍是 STAGING",
                TutorVisualStatus.STAGING,
                store.readTutorVisualSnapshot(abandoned.snapshotId)?.status,
            )

            // 推进到租约过期之后，再换一个"总量会超、淘汰后不超"的窄预算仓储。
            now += LEASE_MILLIS + 1L
            val tightRepository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { now },
                perProgramBudgetBytes = 70L,
                stagingLeaseMillis = LEASE_MILLIS,
            )
            val staged = tightRepository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(drawTag = "c"),
                ownerRequestId = "owner-stage-budget-3",
            )

            assertEquals(
                "淘汰后净值 58 ≤ 70，本次 stage 必须成功而不是被事务内全量重判拒绝",
                TutorVisualStatus.STAGING,
                store.readTutorVisualSnapshot(staged.snapshotId)?.status,
            )
            assertNull(
                "过期、未固定、非 INITIAL 的 STAGING 行必须在同一事务内被回收",
                store.readTutorVisualSnapshot(abandoned.snapshotId),
            )
            assertEquals(
                "LIVE 基线（INITIAL）不受淘汰影响",
                TutorVisualStatus.LIVE,
                store.readTutorVisualSnapshot(baseline.snapshot.snapshotId)?.status,
            )
        }
    }

    // ---- 夹具 ----

    /**
     * 用**真实**仓储（默认预算）落一张 LIVE 基线快照，供预算用例构造"已占用字节"。
     *
     * 基线内容约 29 字节（`"{}"` + `{"scene":true}` + `{"ops":["a"]}`），小于用例注入的
     * 40 字节预算；第二张同尺寸快照进来后 58 > 40，事务内重判必须拒绝。
     */
    private suspend fun commitBaseline(store: StudyDatabasePort, program: TutorVisualProgramInput) {
        val repository = TutorVisualCommitRepository(
            database = store,
            freeSpaceBytes = { Long.MAX_VALUE },
            clock = { 9_000 },
        )
        repository.commitAssistantTurnWithVisual(
            message = assistantCommand(messageId = "message-budget-baseline"),
            program = program,
            snapshot = snapshotInput(drawTag = "a"),
            ownerRequestId = "owner-budget-baseline",
        )
    }

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

        /**
         * 租约窗口。用例显式注入它而不是依赖生产默认值：断言的是"过租约才淘汰、
         * 未过租约保留"这条行为，不是 60 分钟这个数字本身。
         */
        const val LEASE_MILLIS = 60L * 60L * 1_000L
    }

    /**
     * 模拟**事务外预算读的陈旧窗口**（批次 32 并发双过的确定性复现）。
     *
     * 真实竞态是"B 在事务外读到 A 提交前的总量，随后 A 提交"。本类把那次读固定为
     * 空列表 / 0 字节，其余方法全部委托给真库；于是仓储的计划层必然放行，唯一能拦住
     * 超额写入的就是写入事务内的重读重判。若把事务内重判删掉，本类支撑的两个用例会
     * 变成"超额写入成功"而红——这正是它要消灭的失败。
     */
    private class StaleReadVisualPort(
        private val delegate: StudyDatabasePort,
    ) : TutorVisualPort by delegate {
        override suspend fun readVisualCheckpointSummaries(
            programId: String,
        ): List<TutorVisualCheckpointSummary> = emptyList()

        override suspend fun readGlobalVisualCheckpointStoredBytes(): Long = 0L
    }
}
