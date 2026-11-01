package com.tingyun.smartmistakebook.core.data.visual

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X-03 strictOffline ①档**交互重布局**设备向量（批次 39 计划任务 9）：一张**真实持久化**
 * 的视觉程序（真 Room `tutor_visual_program` 行）在冷重开之后，经
 * [StrictOfflineVisualRelayout] ①档判定放行、走协调器 JS 路径完成**两帧交互重布局**
 * （拖动换视口 + 播放推进时间轴），全程**零远程派发、零新源码**。
 *
 * 契约口径（`visual-generation-contract.md` :149）："strictOffline 禁止外部派发和获取新模型
 * 源码，但允许已持久程序在 API/SDK/运行库兼容且本地执行未关闭时重新布局、拖动和播放。"
 *
 * 与既有守卫的分层：JVM `StrictOfflineVisualRelayoutTest` 钉判定梯子每一档（纯函数、无 IO）；
 * `FigureGenerationCoordinatorTest`（JVM）钉 JS 臂的 0 派发形状（fake 账本）；批次 38 V01
 * 设备链钉"生成腿 0 派发四断言位"。**都没有证明的形状 = 已持久程序行 → ①档判定 →
 * 交互重布局**这一冷恢复全链在真库上的持久形状（X-03 清单行登记缺口："交互重布局/拖动/播放
 * 设备向量"）。
 *
 * 本例钉住的失败（先红后绿登记，设备轨执行时核对）：
 * - 若重布局路径从"持久行原文"换成"新生成源码"，红在源码逐字节等同断言（①档判定的
 *   [PersistedVisualProgram] 类型边界 + 引擎实收源码 == 持久行原文的双重核对）；
 * - 若重布局触了账本/图像后端（"0 派发"被破坏），红在 `dispatch_count`、后端 tripwire、
 *   attempt 行 `reservation_id IS NULL` 三处之一；
 * - 若 ①档判定被跳过或降级（版本/熔断误判），红在判定结论断言。
 * 不许放宽断言变绿。
 *
 * 夹具沿用（复用批次 38 V01 设备链实测件，零新机制）：链提交（会话 + assistant 消息 +
 * program + LIVE 快照 + link，`TutorVisualCommitRepository.commitAssistantTurnWithVisual`）、
 * `seedParentOperation` 直写手法与 SQL 观测、`DeviceTextMeasurer`、真
 * `QuickJsVisualExecutionEngine`。离线 witnesses：重布局协调器的 `openBackend` 是 tripwire
 * （JS 路径结构上不触它；一旦触发即红）。
 */
@RunWith(AndroidJUnit4::class)
class StrictOfflineRelayoutDeviceVectorInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val engines = CopyOnWriteArrayList<QuickJsVisualExecutionEngine>()
    private val databaseNames = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        engines.forEach { runCatching { it.close() } }
        engines.clear()
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
    }

    @Test
    fun aPersistedProgramRelayoutsInteractivelyOfflineWithZeroRemoteDispatchWitnesses() = runBlocking {
        val databaseName = "batch39-x03-relayout-${System.nanoTime()}.db"
        databaseNames.add(databaseName)
        context.deleteDatabase(databaseName)
        // ---- 阶段 1：生成并持久化一张真实程序行（V01 链提交骨架）----
        val (programId, sourceJs) = commitPersistedProgram(databaseName)
        // ---- 阶段 2–5：冷重开 → ①档判定 → 交互重布局 → 0 派发持久 witnesses ----
        relayoutFromReopenedRow(databaseName, programId, sourceJs)
    }

    /** 阶段 1：生成并持久化一张真实程序行；返回持久行的 (programId, 源码原文)。 */
    private suspend fun commitPersistedProgram(databaseName: String): Pair<String, String> {
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            store.createTutorConversation(
                CreateTutorConversationDatabaseCommand(
                    conversationId = CONVERSATION_ID,
                    anchorKind = "TEXT_ONLY",
                    anchorId = null,
                    anchorRevisionId = null,
                    title = null,
                    createdAtEpochMillis = 1_000,
                ),
            )
            seedParentOperation(store)
            val measurer = DeviceTextMeasurer(densityOf(), fontScaleOf())
            val engine = QuickJsVisualExecutionEngine(measurer).also { engines.add(it) }
            val chainCoordinator = DefaultFigureGenerationCoordinator(
                ledger = RoomFigureDispatchLedger(store, now = { ++clock; clock }),
                openBackend = { error("the strictOffline chain must never open an image backend") },
                now = { ++clock; clock },
                audit = RoomFigureGenerationAuditRecorder(store),
                visualEngine = engine,
                visualFrameInputs = { _, _ -> CHAIN_FRAME },
            )
            val generated = chainCoordinator.generate(chainRequest(), jsProposal(PROGRAM_JS))
            assertEquals(
                "chain setup must generate: status=${generated.status} diagnostics=${generated.diagnostics}",
                FigureGenerationStatus.GENERATED,
                generated.status,
            )
            val program = generated.artifact as FigureArtifact.Program

            // coordinator 的 Program 产物不携带 drawOps；快照提交腿对同一 program 直取一次
            // Rendered（V01 同型）。
            val rendered = engine.execute(
                VisualExecutionRequest(
                    programId = program.programId,
                    sourceJs = program.sourceJs,
                    sceneRevision = 1, // 协调器 VISUAL_SCENE_REVISION=1，首次执行恒为 1。
                    stateJson = null,
                    viewport = CHAIN_FRAME.viewport,
                    timeMs = CHAIN_FRAME.timeMs,
                    seed = CHAIN_FRAME.seed,
                    theme = CHAIN_FRAME.theme,
                    explicitUserRetry = false,
                ),
            )
            val executed = rendered as VisualExecutionResult.Rendered
            val drawCommandsJson = requireNotNull(executed.drawCommandsJson) {
                "Rendered must carry draw ops"
            }

            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { 9_000 },
            )
            val committed = repository.commitAssistantTurnWithVisual(
                message = assistantCommand(),
                program = TutorVisualProgramInput(
                    learnerId = LEARNER_ID,
                    role = "QUESTION_DIAGRAM",
                    sourceJs = PROGRAM_JS,
                    initialStateJson = "{}",
                    basisFingerprint = "basis-x03-relayout",
                    apiVersion = 1,
                    sdkVersion = "visual-sdk-v1",
                ),
                snapshot = TutorVisualSnapshotInput(
                    stateJson = "{}",
                    sceneSchemaVersion = executed.sceneSchemaVersion,
                    sceneJson = executed.sceneJson,
                    renderSchemaVersion = executed.drawSchemaVersion,
                    drawCommandsJson = drawCommandsJson,
                    contentIds = executed.contentIds,
                    viewportWidthDu = CHAIN_FRAME.viewport.widthDu,
                    viewportHeightDu = CHAIN_FRAME.viewport.heightDu,
                    fontScaleMilli = (fontScaleOf() * 1_000f).roundToInt(),
                    theme = CHAIN_FRAME.theme,
                    timeMs = CHAIN_FRAME.timeMs,
                    seed = CHAIN_FRAME.seed,
                    runtimeVersion = "quickjs-kt-1.0.15",
                    compilerVersion = "visual-compiler-v1",
                    rendererVersion = VISUAL_RENDERER_IMPLEMENTATION_VERSION,
                    fontManifestVersion = FONT_MANIFEST_VERSION,
                    questionVerificationStatus = "NOT_CHECKED",
                ),
                ownerRequestId = OWNER_REQUEST_ID,
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            return program.programId to program.sourceJs
        } finally {
            store.close()
        }
    }

    /** 阶段 2–5：冷重开读持久行 → ①档判定 → 两帧交互重布局 → 0 派发持久 witnesses。 */
    private suspend fun relayoutFromReopenedRow(
        databaseName: String,
        chainProgramId: String,
        chainSourceJs: String,
    ) {
        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            val link = requireNotNull(reopened.readTutorMessageVisual(MESSAGE_ID, VISUAL_ORDINAL)) {
                "the visual link must survive a real reopen"
            }
            val snapshotRow = requireNotNull(reopened.readTutorVisualSnapshot(link.currentSnapshotId)) {
                "the snapshot row must survive a real reopen"
            }
            val programRow = requireNotNull(reopened.readTutorVisualProgram(snapshotRow.programId)) {
                "the persisted program row must survive a real reopen"
            }
            // 批次 39 复核修正（A 类夹具缺陷）：链身份与持久行身份是**两套派生空间**——
            // 协调器 Program 产物 id 是视觉链身份（visual:<operationId>:<sha16>），持久层
            // program 行 id 是内容六字段派生（visual-program-<64hex>）。原断言把两者划等号
            // 结构性不可能成立；X-03 语义要钉的是"持久行身份由内容派生、重开无新源码入口"。
            assertNotEquals(
                "链身份与持久行身份不得划等号（两套派生空间）",
                chainProgramId,
                programRow.programId,
            )
            assertEquals(
                "持久 program 行 id 必须由内容六字段派生（无新源码入口）",
                TutorVisualIdentity.deriveProgramId(
                    sourceJs = programRow.sourceJs,
                    initialStateJson = programRow.initialStateJson,
                    basisFingerprint = programRow.basisFingerprint,
                    apiVersion = programRow.apiVersion,
                    sdkVersion = programRow.sdkVersion,
                    role = programRow.role,
                ),
                programRow.programId,
            )
            // 类型边界：PersistedVisualProgram 只能由持久行字段构造（无"新源码"入口）。
            val persisted = PersistedVisualProgram(
                programId = programRow.programId,
                sourceJs = programRow.sourceJs,
                apiVersion = programRow.apiVersion,
                sdkVersion = programRow.sdkVersion,
            )
            assertEquals("程序行原文逐字节 = 生成时的 program", chainSourceJs, persisted.sourceJs)

            // ---- ①档判定（默认兼容集 = V1 词表）----
            val decision = StrictOfflineVisualRelayout.decide(
                program = persisted,
                localEngineAvailable = true,
                programFused = false,
            )
            assertTrue(
                "兼容且未熔断的持久程序必须①档放行，got $decision",
                decision is StrictOfflineVisualDecision.RelayoutAllowed,
            )
            val allowed = decision as StrictOfflineVisualDecision.RelayoutAllowed
            assertEquals("①档放行的 programId 是持久行 id", persisted.programId, allowed.programId)
            assertEquals("①档放行的源码是持久行原文", persisted.sourceJs, allowed.sourceJs)

            // ---- 两帧交互重布局（拖动换视口 + 播放推进时间轴），离线装配 ----
            val measurer = DeviceTextMeasurer(densityOf(), fontScaleOf())
            val engine = QuickJsVisualExecutionEngine(measurer).also { engines.add(it) }
            val counting = CountingVisualEngine(engine)
            var frameIndex = 0
            val relayoutCoordinator = DefaultFigureGenerationCoordinator(
                ledger = RoomFigureDispatchLedger(reopened, now = { ++clock; clock }),
                openBackend = { error("strictOffline relayout must not open an image backend") },
                now = { ++clock; clock },
                audit = RoomFigureGenerationAuditRecorder(reopened),
                visualEngine = counting,
                visualFrameInputs = { _, _ ->
                    INTERACTIVE_FRAMES[frameIndex++ % INTERACTIVE_FRAMES.size]
                },
            )
            val dragOutcome = relayoutCoordinator.generate(
                relayoutRequest(),
                jsProposal(allowed.sourceJs),
            )
            val playOutcome = relayoutCoordinator.generate(
                relayoutRequest(),
                jsProposal(allowed.sourceJs),
            )
            assertEquals(
                "拖动帧重布局必须 GENERATED：status=${dragOutcome.status} diagnostics=${dragOutcome.diagnostics}",
                FigureGenerationStatus.GENERATED,
                dragOutcome.status,
            )
            assertEquals(
                "播放帧重布局必须 GENERATED：status=${playOutcome.status} diagnostics=${playOutcome.diagnostics}",
                FigureGenerationStatus.GENERATED,
                playOutcome.status,
            )
            // 零新源码：两帧都只用持久行原文（判定的源码与引擎实收源码双重核对）。
            assertEquals(
                listOf(allowed.sourceJs, allowed.sourceJs),
                counting.executedSourceJs,
            )
            assertEquals(
                "重布局产物 Program 的源码是持久行原文",
                allowed.sourceJs,
                (dragOutcome.artifact as FigureArtifact.Program).sourceJs,
            )
            // 交互真的流进了引擎：两帧的视口/时间轴按注入次序逐一到达（拖动帧 300×200，
            // 播放帧 timeMs 61_000）。登记边界：本夹具 program 不消费 timeMs（V01 程序
            // 同构），播放帧的"场景随时间变"没有可断言面，如实以帧到达序列为证。
            assertEquals(
                "两帧必须按次序真实到达引擎（拖动换视口 + 播放推进时间轴）",
                listOf(
                    listOf(300, 200, 1_000L),
                    listOf(300, 200, 61_000L),
                ),
                counting.executedFrames,
            )
            // 重布局真的"重新布局"了：拖动帧场景（300×200）≠ 持久快照的场景（360×240）
            // ——不是把持久快照原样重放一遍。
            assertEquals("两帧都必须真实渲染", 2, counting.renderedScenes.size)
            assertNotEquals(
                "拖动帧场景必须 ≠ 持久快照场景（重布局不是快照重放）",
                snapshotRow.sceneJson,
                counting.renderedScenes[0],
            )
            assertNotEquals(
                "播放帧场景同样 ≠ 持久快照场景",
                snapshotRow.sceneJson,
                counting.renderedScenes[1],
            )

            // ---- 0 远程派发持久 witnesses（真库）----
            assertEquals(
                "重布局零远程派发（真实 SQL 计数写前后不变）",
                0,
                readParentDispatchCount(reopened, PARENT_REQUEST_ID),
            )
            readSingleOperation(reopened, RELAYOUT_OPERATION_ID).let { operation ->
                assertEquals("SUCCEEDED", operation.string("status"))
                assertEquals("reserved_remote_dispatches", 0, operation.int("reserved_remote_dispatches"))
                assertEquals("used_remote_dispatches", 0, operation.int("used_remote_dispatches"))
            }
            val attempts = readAllAttempts(reopened, RELAYOUT_OPERATION_ID)
            assertEquals("两帧交互各留一行本地 attempt", 2, attempts.size)
            attempts.forEach { attempt ->
                assertEquals("attempt method", "JS", attempt.string("method"))
                assertEquals("attempt status", "SUCCEEDED", attempt.string("status"))
                assertEquals(
                    "本地重布局 attempt 不携带预留（reservation_id IS NULL）",
                    null,
                    attempt.stringOrNull("reservation_id"),
                )
            }
        } finally {
            reopened.close()
        }
    }

    // ------------------------------------------------------------ 夹具（复用批次 38 V01 设备链实测件）

    /** 计数包装：转发真引擎，钉"协调器把哪份源码/哪一帧交给了引擎"与场景差异。 */
    private class CountingVisualEngine(
        private val delegate: QuickJsVisualExecutionEngine,
    ) : VisualExecutionEngine {
        var executeCount: Int = 0
            private set
        val executedSourceJs = mutableListOf<String>()
        val renderedScenes = mutableListOf<String>()

        /** 每次执行的 (viewport 宽, viewport 高, timeMs)，按到达次序。 */
        val executedFrames = mutableListOf<List<Any>>()

        override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
            executeCount += 1
            executedSourceJs.add(request.sourceJs)
            executedFrames.add(
                listOf(request.viewport.widthDu, request.viewport.heightDu, request.timeMs),
            )
            val result = delegate.execute(request)
            if (result is VisualExecutionResult.Rendered) {
                renderedScenes.add(result.sceneJson)
            }
            return result
        }

        override fun close() = delegate.close()
    }

    /** V01 设备夹具同型：真实系统字体度量（不是固定值桩）。 */
    private class DeviceTextMeasurer(
        private val density: Float,
        fontScale: Float,
        private val sizeSp: Float = 16f,
    ) : VisualContentMeasurer {
        private val paint = android.graphics.Paint().apply {
            flags = REPLAY_TEXT_PAINT_FLAGS
            textSize = sizeSp * density * fontScale
        }
        val payload = LABEL_PAYLOAD

        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
            if (batch.entries.isEmpty()) {
                return VisualContentMeasureResult(emptyList())
            }
            val metrics = paint.fontMetrics
            val lineHeightPx = metrics.descent - metrics.ascent
            return VisualContentMeasureResult(
                batch.entries.mapIndexed { index, entry ->
                    VisualMeasuredContent(
                        contentId = "c$index",
                        widthDu = paint.measureText(entry.payload) / density,
                        heightDu = lineHeightPx / density,
                        baselineDu = -metrics.ascent / density,
                        fontSizeSp = sizeSp,
                        digest = figureSha256Hex(entry.payload.toByteArray(StandardCharsets.UTF_8)),
                    )
                },
            )
        }
    }

    private fun chainRequest() = FigureGenerationRequest(
        operationId = CHAIN_OPERATION_ID,
        purpose = FigureGenerationPurpose.EXPLANATION,
        target = FigureGenerationTarget(),
        capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.JS),
        ),
        budget = FigureGenerationBudget(),
        dispatchParent = FigureDispatchParent.ModelTask(
            requestId = PARENT_REQUEST_ID,
            reserveChatFinalAnswer = true,
        ),
        source = null,
        constraints = emptyList(),
        sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
        sourceId = MESSAGE_ID,
        basisFingerprint = null,
        explicitUserRetry = false,
    )

    private fun relayoutRequest() = FigureGenerationRequest(
        operationId = RELAYOUT_OPERATION_ID,
        purpose = FigureGenerationPurpose.EXPLANATION,
        target = FigureGenerationTarget(),
        capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.JS),
        ),
        budget = FigureGenerationBudget(),
        dispatchParent = FigureDispatchParent.ModelTask(
            requestId = PARENT_REQUEST_ID,
            reserveChatFinalAnswer = false,
        ),
        source = null,
        constraints = emptyList(),
        sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
        sourceId = MESSAGE_ID,
        basisFingerprint = null,
        explicitUserRetry = false,
    )

    private fun jsProposal(program: String): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.EXPLANATION,
        method = FigureGenerationMethod.JS,
        reason = "测试",
        program = program,
        fidelity = FigureFidelity.FAITHFUL,
    )

    private fun assistantCommand() = AppendTutorAssistantMessageDatabaseCommand(
        conversationId = CONVERSATION_ID,
        messageId = MESSAGE_ID,
        ordinal = null,
        replyToMessageId = null,
        bodyMarkdown = "图在下面。",
        thinkingMarkdown = null,
        toolTraceJson = """[{"tool":"GENERATE_FIGURE","status":"OK"}]""",
        sourceImageAssetIds = emptyList(),
        logicalOperationId = "operation-$MESSAGE_ID",
        status = "SUCCEEDED",
        createdAtEpochMillis = 1_000,
        completedAtEpochMillis = 2_000,
        errorCode = null,
    )

    /** V01 同型：预置账本父操作（dispatch_count 观测对象；FK RESTRICT 的两行）。 */
    private suspend fun seedParentOperation(store: StudyDatabasePort) {
        store.readDatabaseVersion() // Room 惰性建库：先确保 schema（含两表）已落文件。
        withSqlite { sqlite ->
            sqlite.insertOrThrow(
                "model_task_operation",
                null,
                contentValuesOf(
                    "operation_fingerprint" to PARENT_OPERATION_FINGERPRINT,
                    "subject_id" to SUBJECT_ID,
                    "task_kind" to TASK_KIND,
                    "dispatch_count" to 0,
                    "created_at_epoch_millis" to 1L,
                    "updated_at_epoch_millis" to 1L,
                ),
            )
            sqlite.insertOrThrow(
                "model_task",
                null,
                contentValuesOf(
                    "task_id" to "task-x03-relayout",
                    "request_id" to PARENT_REQUEST_ID,
                    "request_fingerprint" to "request-fingerprint-x03",
                    "operation_fingerprint" to PARENT_OPERATION_FINGERPRINT,
                    "request_snapshot" to "{}",
                    "task_kind" to TASK_KIND,
                    "subject_id" to SUBJECT_ID,
                    "status" to "RUNNING",
                    "state_version" to 1L,
                    "stage" to "TOOL_ROUND",
                    "user_message" to "这题怎么解？",
                    "attempt_count" to 1,
                    "created_at_epoch_millis" to 1L,
                    "updated_at_epoch_millis" to 1L,
                ),
            )
        }
    }

    private suspend fun readParentDispatchCount(database: StudyDatabasePort, requestId: String): Int {
        val row = readSingleRow(
            database,
            "model_task_operation operation JOIN model_task task " +
                "ON task.operation_fingerprint = operation.operation_fingerprint",
            "operation.dispatch_count AS dispatch_count",
            "task.request_id = ?",
            requestId,
        )
        return row.int("dispatch_count")
    }

    private class AuditRow(private val values: Map<String, Any?>) {
        fun string(column: String): String =
            requireNotNull(values[column] as? String) { "column $column must be a non-null string" }

        fun stringOrNull(column: String): String? = values[column] as? String

        fun int(column: String): Int = (values[column] as Number).toInt()
    }

    private suspend fun readSingleOperation(database: StudyDatabasePort, operationId: String): AuditRow {
        val rows = readRows(database, OPERATION_TABLE, OPERATION_COLUMNS, "operation_id = ?", operationId)
        assertEquals("expected exactly one operation row for $operationId", 1, rows.size)
        return rows.single()
    }

    private suspend fun readAllAttempts(database: StudyDatabasePort, operationId: String): List<AuditRow> =
        readRows(database, ATTEMPT_TABLE, ATTEMPT_COLUMNS, "operation_id = ?", operationId)

    private suspend fun readSingleRow(
        database: StudyDatabasePort,
        table: String,
        columns: String,
        where: String,
        vararg args: String,
    ): AuditRow {
        val rows = readRows(database, table, columns, where, *args)
        assertEquals("expected exactly one row for: $table", 1, rows.size)
        return rows.single()
    }

    /** 只读第二条连接（V01 `withSqlite` 同型；显式传库名）。 */
    private suspend fun readRows(
        database: StudyDatabasePort,
        table: String,
        columns: String,
        where: String,
        vararg args: String,
    ): List<AuditRow> {
        database.readDatabaseVersion() // Room 惰性建库：先确保 schema 已落文件。
        val name = requireNotNull(databaseNames.lastOrNull()) { "no database opened yet" }
        val sqlite = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            val rows = mutableListOf<AuditRow>()
            sqlite.rawQuery("SELECT $columns FROM $table WHERE $where", args).use { cursor ->
                while (cursor.moveToNext()) {
                    val values = LinkedHashMap<String, Any?>()
                    for (index in 0 until cursor.columnCount) {
                        values[cursor.getColumnName(index)] = when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                            else -> cursor.getString(index)
                        }
                    }
                    rows.add(AuditRow(values))
                }
            }
            return rows
        } finally {
            sqlite.close()
        }
    }

    /** V01 同型：写侧第二条连接（seedParentOperation 用）。 */
    private inline fun <T> withSqlite(block: (SQLiteDatabase) -> T): T {
        val name = requireNotNull(databaseNames.lastOrNull()) { "no database opened yet" }
        val sqlite = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            sqlite.execSQL("PRAGMA foreign_keys = ON")
            sqlite.beginTransaction()
            try {
                val result = block(sqlite)
                sqlite.setTransactionSuccessful()
                return result
            } finally {
                sqlite.endTransaction()
            }
        } finally {
            sqlite.close()
        }
    }

    private fun contentValuesOf(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
        pairs.forEach { (key, value) ->
            when (value) {
                null -> putNull(key)
                is String -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                else -> throw IllegalArgumentException("unsupported fixture value for $key")
            }
        }
    }

    private fun densityOf(): Float = context.resources.displayMetrics.density

    private fun fontScaleOf(): Float =
        context.resources.configuration.fontScale.let { if (it <= 0f) 1f else it }

    private var clock: Long = 10_000L

    private companion object {
        /** 讲解场景（V01 `PROGRAM_JS` 同构：一组 + 平移内含圆路径与真实度量驱动的 label）。 */
        val PROGRAM_JS = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '$LABEL_PAYLOAD' }]; },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-x03-relayout', revision: input.revision,
                         viewBox: { x: 0, y: 0, width: input.viewport.widthDu,
                                    height: input.viewport.heightDu },
                         background: '#ffffff',
                         paths: [{ id: 'dot', commands: visualSdk.circle(50, 50, 40) }],
                         root: { type: 'group', matrix: [1, 0, 0, 1, 5, 5], opacity: 0.5,
                                 children: [
                                   { type: 'path', pathId: 'dot', fill: '#ff0000' },
                                   { type: 'label', contentId: metrics[0].contentId,
                                     anchor: { x: 100, y: 60 }, offset: { x: 2, y: 3 },
                                     hAlign: 'center', vAlign: 'middle', color: '#26354a' } ] },
                         controls: [], hitRegions: [] };
              },
            };
        """.trimIndent()

        const val LABEL_PAYLOAD = "标签 S=ab"

        /** 提交帧（V01 冻结帧）。 */
        val CHAIN_FRAME = VisualFrameSnapshot(
            viewport = VisualViewport(360, 240),
            theme = "light",
            seed = 42L,
            timeMs = 1_000L,
        )

        /**
         * 两帧交互：拖动帧把视口从提交时的 360×240 换到 300×200（程序把视口写进 viewBox
         * → 场景必不同于持久快照）；播放帧推进时间轴（timeMs 1_000 → 61_000）。登记边界：
         * 本夹具 program 不消费 timeMs（V01 程序同构），播放帧的场景差异没有可断言面，
         * 以帧到达序列为证（见 [CountingVisualEngine.executedFrames]）。
         */
        val INTERACTIVE_FRAMES = listOf(
            VisualFrameSnapshot(
                viewport = VisualViewport(300, 200),
                theme = "light",
                seed = 42L,
                timeMs = 1_000L,
            ),
            VisualFrameSnapshot(
                viewport = VisualViewport(300, 200),
                theme = "light",
                seed = 42L,
                timeMs = 61_000L,
            ),
        )

        const val CHAIN_OPERATION_ID = "operation-x03-relayout-chain"
        const val RELAYOUT_OPERATION_ID = "operation-x03-relayout-interactive"
        const val PARENT_REQUEST_ID = "request-x03-relayout-parent"
        const val PARENT_OPERATION_FINGERPRINT = "fingerprint-x03-relayout-parent"
        const val SUBJECT_ID = "subject-x03-relayout"
        const val TASK_KIND = "TUTOR"
        const val CONVERSATION_ID = "conversation-x03-relayout"
        const val MESSAGE_ID = "message-x03-relayout"
        const val OWNER_REQUEST_ID = "owner-x03-relayout"
        const val LEARNER_ID = "learner-x03-relayout"
        const val VISUAL_ORDINAL = 0

        /** 快照提交所需（V01 同值）：测试注入的已登记清单版本。 */
        const val FONT_MANIFEST_VERSION = "font-manifest-v1"

        const val OPERATION_TABLE = "figure_generation_operation"
        const val ATTEMPT_TABLE = "figure_generation_attempt"
        const val OPERATION_COLUMNS =
            "status, reserved_remote_dispatches, used_remote_dispatches"
        const val ATTEMPT_COLUMNS = "attempt_id, method, status, reservation_id"
    }
}
