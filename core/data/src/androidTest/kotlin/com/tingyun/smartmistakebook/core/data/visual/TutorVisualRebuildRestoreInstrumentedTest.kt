package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **V-03 的三组缺口用例（批次 38）**：进程重建续最近 checkpoint、strictOffline 只重放（0 派发
 * 观测）、恢复门走**真实行 → 端口读侧 → 映射**的端到端。与既有覆盖的分工（实读核对，不重复）：
 *
 * - `VisualSnapshotRestoreTest`（12 例 JVM）钉恢复判定本身，输入是手工 record；
 * - `V01VisualChainInstrumentedTest` 钉"生成腿 0 派发 + 同一链内恢复重放"，其恢复记录是
 *   **测试内手拼**（`runChainToRestoredReplay` 自注"测试内映射"）；
 * - 本套件补的是：**生产读侧**（`TutorVisualPort.readLatestLiveTutorVisualSnapshot` +
 *   `VisualSnapshotRestoreReadSide`）驱动的重建续读与恢复门，且恢复腿独立于生成腿
 *   （本文件不构造任何引擎/协调器，这就是 strictOffline 只重放腿的装配形态）。
 *
 * **本轨纪律**：不运行 Gradle/设备命令（门禁由脚本串行执行）；判据是设备轨要执行的断言，
 * 不是已通过的结果（`TutorVisualCommitRepositoryInstrumentedTest` 的先红后绿登记法）。
 * 方法名无空格（D8 SimpleName 约束）。
 *
 * **strictOffline 0 派发的观测口径（如实登记其证明范围）**：本套件装配里**不存在**任何
 * 引擎/网络对象，恢复腿走完（含 painter 真栅格）后，派发账面三表
 * （`figure_generation_operation` / `figure_generation_attempt` / `model_task_operation`）
 * 仍为 0 行——它证明"恢复腿没向任何派发面记账"；"恢复结构上不可能出网"由
 * `VisualSnapshotRestore` / `VisualSnapshotRestoreReadSide` 的 grep 门与反射断言承担
 * （类型层无网络/引擎符号）。两者合起来才是 strictOffline 只重放的完整证据。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualRebuildRestoreInstrumentedTest {

    @Test
    fun processRebuildContinuesFromTheLatestLiveCheckpoint() = runBlocking {
        withReopenableDatabase { db ->
            db.store.createTutorConversation(conversationCommand())
            var now = 9_000L
            val repository = TutorVisualCommitRepository(
                database = db.store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { now },
            )
            val program = programInput()
            val programId = repository.programIdFor(program)

            // 同 program 两个 checkpoint：INITIAL → LATEST（promoted_at 随时钟递增，"最近"确定性）。
            val first = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-rebuild-1", createdAt = now),
                program = program,
                snapshot = snapshotInput(fill = "#111111"),
                ownerRequestId = "owner-rebuild-1",
            )
            assertEquals("INITIAL", first.snapshot.checkpointKind)
            now += 1_000
            val second = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-rebuild-2", createdAt = now),
                program = program,
                snapshot = snapshotInput(fill = "#222222"),
                ownerRequestId = "owner-rebuild-2",
            )
            assertEquals("LATEST", second.snapshot.checkpointKind)

            // 一张从未提交的 STAGING 行：恢复读侧绝不能把它当"最近"。
            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(fill = "#333333"),
                ownerRequestId = "owner-rebuild-3",
            )
            assertEquals(
                TutorVisualStatus.STAGING,
                db.store.readTutorVisualSnapshot(staged.snapshotId)?.status,
            )

            // 真实进程重建：关闭 + 同名重开（仓内惯例，V-01 链同型）。
            val reopened = db.reopen()

            // 新读缝：重建后按 program 续读最近 checkpoint —— 必须是第二个 LIVE 快照，
            // 不是 INITIAL、也绝不是 STAGING 行。
            val latest = requireNotNull(
                reopened.readLatestLiveTutorVisualSnapshot(programId),
            ) { "the rebuilt process must still see the latest live checkpoint" }
            assertEquals("重建续读的是最新 LIVE 快照", second.snapshot.snapshotId, latest.snapshotId)
            assertEquals(TutorVisualStatus.LIVE, latest.status)

            // 生产读侧映射 → 恢复判定：Replay 且 snapshotId = 新 LATEST 行。
            val outcome = VisualSnapshotRestore.restore(
                record = VisualSnapshotRestoreReadSide.restoreRecordOf(latest),
                previewVerified = false,
                policy = VisualRestorePolicy(verifiedFontManifests = setOf(FONT_MANIFEST_VERSION)),
            )
            assertTrue("the latest checkpoint must replay, got $outcome", outcome is VisualRestoreOutcome.Replay)
            assertEquals(second.snapshot.snapshotId, (outcome as VisualRestoreOutcome.Replay).snapshotId)

            // STAGING 行未被删（对账没跑过），但恢复读侧的"最近 LIVE"出口绝不流出行外。
            assertEquals(
                TutorVisualStatus.STAGING,
                reopened.readTutorVisualSnapshot(staged.snapshotId)?.status,
            )
            assertNotNull(reopened.readTutorVisualSnapshot(first.snapshot.snapshotId))
        }
    }

    @Test
    fun theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings() = runBlocking {
        // 装配即证据：本用例不构造任何引擎/协调器/网络对象——恢复腿只有
        // 仓储写侧 + 重建重开 + 端口读侧 + 映射 + restore + painter。
        withReopenableDatabase { db ->
            db.store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = db.store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val program = programInput()
            val programId = repository.programIdFor(program)
            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-offline-1"),
                program = program,
                snapshot = snapshotInput(fill = "#112233"),
                ownerRequestId = "owner-offline-1",
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)

            val reopened = db.reopen()
            val latest = requireNotNull(reopened.readLatestLiveTutorVisualSnapshot(programId))
            val outcome = VisualSnapshotRestore.restore(
                record = VisualSnapshotRestoreReadSide.restoreRecordOf(latest),
                previewVerified = false,
                policy = VisualRestorePolicy(verifiedFontManifests = setOf(FONT_MANIFEST_VERSION)),
            )
            val replay = outcome as VisualRestoreOutcome.Replay

            // 恢复出的文档真的被画出来（不是静默空转）：先铺白底，断言非白像素=路径墨
            //（新建 Bitmap 初始像素是透明黑，不铺底则"非白"恒真、断言无分辨力）。
            val density = densityOf()
            val width = (REPLAY_VIEW_WIDTH * density).toInt()
            val height = (REPLAY_VIEW_HEIGHT * density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(WHITE)
                VisualDrawOpsPainter().draw(replay.document, Canvas(bitmap), density) { null }
                val pixels = IntArray(width * height).also {
                    bitmap.getPixels(it, 0, width, 0, 0, width, height)
                }
                assertTrue(
                    "the restored document must leave ink (restore is not a silent no-op)",
                    pixels.any { it != WHITE },
                )
            } finally {
                bitmap.recycle()
            }

            // 0 派发观测：恢复腿走完（含绘制），三个派发账面仍为 0 行。
            // 证明范围见文件 KDoc：这里钉"没向任何派发面记账"；类型层不可能出网由 grep 门承担。
            assertEquals(0L, rawCount(db.databaseName, "figure_generation_operation"))
            assertEquals(0L, rawCount(db.databaseName, "figure_generation_attempt"))
            assertEquals(0L, rawCount(db.databaseName, "model_task_operation"))
        }
    }

    @Test
    fun everyRestoreGateSurvivesRealRowsThroughThePortReadSide() = runBlocking {
        withReopenableDatabase { db ->
            db.store.createTutorConversation(conversationCommand())
            val repository = TutorVisualCommitRepository(
                database = db.store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val program = programInput()
            val programId = repository.programIdFor(program)
            val verifiedFonts = VisualRestorePolicy(verifiedFontManifests = setOf(FONT_MANIFEST_VERSION))

            // 正常 LIVE 行：全部门通过 → Replay（同 program 的第一张 checkpoint）。
            val positive = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-gates-positive"),
                program = program,
                snapshot = snapshotInput(fill = "#101010"),
                ownerRequestId = "owner-gates-positive",
            )
            // 每个只读门各造一行（走公开写路径：写侧允许 scene/render schema ≥1、renderer 任意非空）。
            val sceneUnknown = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-gates-scene"),
                program = program,
                snapshot = snapshotInput(fill = "#202020", sceneSchemaVersion = 2),
                ownerRequestId = "owner-gates-scene",
            )
            val renderUnknown = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-gates-render"),
                program = program,
                snapshot = snapshotInput(fill = "#303030", renderSchemaVersion = 2),
                ownerRequestId = "owner-gates-render",
            )
            val rendererMismatch = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-gates-renderer"),
                program = program,
                snapshot = snapshotInput(fill = "#404040", rendererVersion = "visual-renderer-v0"),
                ownerRequestId = "owner-gates-renderer",
            )
            val fontUnverified = repository.commitAssistantTurnWithVisual(
                message = assistantCommand("message-gates-font"),
                program = program,
                snapshot = snapshotInput(fill = "#505050"),
                ownerRequestId = "owner-gates-font",
            )
            // 从未提升的 STAGING 行：恢复门 1。
            val staged = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput(fill = "#606060"),
                ownerRequestId = "owner-gates-staging",
            )

            val reopened = db.reopen()

            suspend fun restoreByPortRead(snapshotId: String, policy: VisualRestorePolicy): VisualRestoreOutcome {
                val row = requireNotNull(reopened.readTutorVisualSnapshot(snapshotId))
                return VisualSnapshotRestore.restore(
                    record = VisualSnapshotRestoreReadSide.restoreRecordOf(row),
                    previewVerified = false,
                    policy = policy,
                )
            }

            assertTrue(
                "the healthy row replays",
                restoreByPortRead(positive.snapshot.snapshotId, verifiedFonts)
                    is VisualRestoreOutcome.Replay,
            )
            assertEquals(
                VisualRestoreFallbackReason.UNKNOWN_SCENE_SCHEMA,
                (restoreByPortRead(sceneUnknown.snapshot.snapshotId, verifiedFonts)
                    as VisualRestoreOutcome.ReadOnly).reason,
            )
            assertEquals(
                VisualRestoreFallbackReason.UNKNOWN_RENDER_SCHEMA,
                (restoreByPortRead(renderUnknown.snapshot.snapshotId, verifiedFonts)
                    as VisualRestoreOutcome.ReadOnly).reason,
            )
            assertEquals(
                VisualRestoreFallbackReason.RENDERER_VERSION_MISMATCH,
                (restoreByPortRead(rendererMismatch.snapshot.snapshotId, verifiedFonts)
                    as VisualRestoreOutcome.ReadOnly).reason,
            )
            // 默认策略无可验证字体清单：同一行只能走只读（像素一致性不承诺）。
            assertEquals(
                VisualRestoreFallbackReason.FONT_MANIFEST_UNVERIFIED,
                (restoreByPortRead(fontUnverified.snapshot.snapshotId, VisualRestorePolicy.CURRENT)
                    as VisualRestoreOutcome.ReadOnly).reason,
            )
            assertEquals(
                "the staged row must never replay",
                VisualRestoreFallbackReason.SNAPSHOT_NOT_LIVE,
                (restoreByPortRead(staged.snapshotId, verifiedFonts)
                    as VisualRestoreOutcome.ReadOnly).reason,
            )
            assertTrue(
                "the recovery read never surfaces the staged row",
                reopened.readLatestLiveTutorVisualSnapshot(programId)!!.snapshotId != staged.snapshotId,
            )
        }
    }

    // ------------------------------------------------------------ 夹具

    /** 建库 + 追踪"当前打开"的库：[ReopenableDatabase.reopen] 真实关闭再同名重开。 */
    private class ReopenableDatabase(
        val store: StudyDatabasePort,
        val databaseName: String,
        private val context: Context,
    ) {
        private var current: StudyDatabasePort = store

        suspend fun reopen(): StudyDatabasePort {
            current.close()
            current = StudyDatabaseFactory.open(context, databaseName)
            return current
        }

        fun closeCurrent() {
            runCatching { current.close() }
        }
    }

    private suspend fun withReopenableDatabase(
        block: suspend (ReopenableDatabase) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch38-visual-rebuild-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val db = ReopenableDatabase(
            store = StudyDatabaseFactory.open(context, databaseName),
            databaseName = databaseName,
            context = context,
        )
        try {
            block(db)
        } finally {
            db.closeCurrent()
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

    private fun assistantCommand(
        messageId: String,
        createdAt: Long = 1_000,
    ) = AppendTutorAssistantMessageDatabaseCommand(
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
        createdAtEpochMillis = createdAt,
        completedAtEpochMillis = createdAt,
        errorCode = null,
    )

    private fun programInput() = TutorVisualProgramInput(
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceJs = "draw();",
        initialStateJson = "{}",
        basisFingerprint = "basis-batch38-rebuild",
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
    )

    /**
     * 重放腿用**合法 DrawOps 文档**（解析器同一套生成门会复算 stats 并校验 `#RRGGBB` 色）——
     * 文档形状与 `VisualSnapshotRestoreTest` 的 pathDocument 同源；`fill` 不同 = 内容不同 =
     * 另一张快照（内容参与 snapshotId 派生）。
     */
    private fun snapshotInput(
        fill: String,
        sceneSchemaVersion: Int = 1,
        renderSchemaVersion: Int = 1,
        rendererVersion: String = VISUAL_RENDERER_IMPLEMENTATION_VERSION,
    ) = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = sceneSchemaVersion,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = renderSchemaVersion,
        drawCommandsJson = pathDocument(fill),
        contentIds = emptySet(),
        viewportWidthDu = REPLAY_VIEW_WIDTH,
        viewportHeightDu = REPLAY_VIEW_HEIGHT,
        fontScaleMilli = 1_000,
        theme = "LIGHT",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = rendererVersion,
        fontManifestVersion = FONT_MANIFEST_VERSION,
        questionVerificationStatus = "NOT_CHECKED",
    )

    /** viewBox 与快照视口同尺寸（painter 按 viewBox 投影——保持一致）。 */
    private fun pathDocument(fill: String): String =
        """{"drawSchemaVersion":1,"viewBox":{"x":0.0,"y":0.0,"w":$REPLAY_VIEW_WIDTH.0,""" +
            """"h":$REPLAY_VIEW_HEIGHT.0},"background":null,"ops":[{"op":"path",""" +
            """"path":[["M",0.0,0.0],["L",10.0,10.0]],"fill":"$fill","stroke":null,"alpha":1.0}],""" +
            """"hitRegions":[],"stats":{"nodes":1,"pathCommands":2,"labels":0,"ops":1}}"""

    private fun densityOf(): Float =
        ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private fun rawCount(databaseName: String, table: String): Long {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sqlite = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            val statement = sqlite.rawQuery("SELECT COUNT(*) FROM `$table`", emptyArray())
            try {
                statement.moveToFirst()
                return statement.getLong(0)
            } finally {
                statement.close()
            }
        } finally {
            sqlite.close()
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conversation-batch38-rebuild"
        const val LEARNER_ID = "learner-batch38"

        /** 重放腿视口（与 pathDocument 的 viewBox 同值）。 */
        const val REPLAY_VIEW_WIDTH = 360
        const val REPLAY_VIEW_HEIGHT = 240
        const val WHITE = 0xFFFFFFFF.toInt()

        /** 生产 VERIFIED_FONT_MANIFESTS 为空集；测试注入的已登记清单版本（策略 KDoc 明示用法）。 */
        const val FONT_MANIFEST_VERSION = "font-manifest-v1"
    }
}
