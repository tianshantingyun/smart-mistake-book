package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.CreateModelTaskCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TransitionModelTaskCommand
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationLocalJsBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import com.tingyun.smartmistakebook.core.model.figureJsBasisFingerprint
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A-10 协调器级跨 method 合计向量（批次 39 计划任务 7）：**同一 operationId 先 JS 后 IMAGE
 * 各产出一份最终图后，第 3 份（IMAGE）在协调器入口被拒**——经由
 * [DefaultFigureGenerationCoordinator] 全路径 + **真 Room 账本/审计**（设备载体）。
 *
 * 与既有两层守卫的分层（批次 39 基线 §6/§10.3：扩例落协调器层，不重复门层）：
 * - 门层（JVM）：`FigureGenerationLocalBudgetBoundaryTest` 6 例——fake 账本/通道/引擎，
 *   钉 [FigureOperationLocalBudgets] 的计数语义（与 method 无关）；
 * - 账本层（设备）：`FigureDispatchBudgetBoundaryInstrumentedTest` 3 例——直调
 *   [RoomFigureDispatchLedger]，钉 `reserveModelTaskOperationDispatch` 的真实 SQL
 *   （6 上限 / 换 method 不重置 / 终答保留位）；
 * - **本套件（设备，协调器层）**：以上两层都没证明的形状 = JS 腿（本地执行、账本零触碰）
 *   与 IMAGE 腿（真实 SQL 预留）在**同一个 operationId** 上交汇时，协调器的
 *   `admitFinalFigure` 在真库上把第 3 份拒在"开后端 / 预留 / 通道 / 进引擎"之前，
 *   且拒绝不留**任何**持久痕迹（attempt 行不增、dispatch_count 不动、审计不写）。
 *   门本体（批次 36 在树）没有新门；本例灭掉的是"协调器装配下真库形状无人证明"这一覆盖缺口。
 *
 * 先红后绿登记（设备轨执行时核对）：若协调器的最终图门被移除/放宽（回归为第 3 份照样
 * 打开后端或进引擎），本例红在 `backendOpens`/`channelCalls`/`executeCount`/`dispatch_count`
 * 四个计数位之一（第 3 腿把某一计数从 1 推进到 2）；若拒绝原因文案漂移（不再是"已产出
 * N 份最终图（上限 2）"），红在诊断断言。任何"放宽断言来变绿"都不允许。
 *
 * 夹具沿用（复用批次 36/38 实测件，零新机制）：
 * - `withDatabase`/`seedModelTask`：`FigureDispatchBudgetBoundaryInstrumentedTest` 同型
 *   （真实任务行经生产迁移链路落 QUEUED，含账本观测用的父操作行）；
 * - SQL 观测：`V01VisualChainInstrumentedTest` 同型（只读第二条 SQLite 连接）；
 * - 真引擎：`QuickJsVisualExecutionEngine`（真编译器资产）+ `DeviceTextMeasurer`（V01 同型），
 *   JS 腿是**真实渲染**产出的最终图，不是引擎桩。
 */
@RunWith(AndroidJUnit4::class)
class FigureCrossMethodFinalFigureDeviceVectorInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val engines = java.util.concurrent.CopyOnWriteArrayList<QuickJsVisualExecutionEngine>()
    private val databaseNames = java.util.concurrent.CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        engines.forEach { runCatching { it.close() } }
        engines.clear()
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
    }

    @Test
    fun aThirdFinalFigureAcrossJsAndImageMethodsIsRejectedByTheCoordinatorOverTheRealLedger() =
        runBlocking {
            withDatabase { database ->
                val parent = seedModelTask(database, "a10-coord-cross")
                val measurer = DeviceTextMeasurer(densityOf(), fontScaleOf())
                val realEngine = QuickJsVisualExecutionEngine(measurer).also { engines.add(it) }
                val engine = CountingVisualEngine(realEngine)
                val channel = CountingImageChannel()
                var backendOpens = 0
                val coordinator = DefaultFigureGenerationCoordinator(
                    ledger = RoomFigureDispatchLedger(database, now = { ++clock; clock }),
                    openBackend = {
                        backendOpens += 1
                        FigureImageBackend(
                            configurationFingerprint = "config-a10-coord",
                            openChannel = { channel },
                            closeAction = {},
                        )
                    },
                    now = { ++clock; clock },
                    audit = RoomFigureGenerationAuditRecorder(database),
                    visualEngine = engine,
                    visualFrameInputs = { _, _ -> FRAME },
                )

                // 第 1 份：JS 腿真实渲染（本地执行，账本零触碰）。
                val jsOutcome = coordinator.generate(jsRequest(parent.requestId), jsProposal())
                assertEquals(
                    "actual: status=${jsOutcome.status} diagnostics=${jsOutcome.diagnostics}",
                    FigureGenerationStatus.GENERATED,
                    jsOutcome.status,
                )
                assertTrue(
                    "JS 产物必须是 Program，got ${jsOutcome.artifact?.let { it::class.simpleName }}",
                    jsOutcome.artifact is FigureArtifact.Program,
                )
                assertEquals("JS 腿真实进引擎一次", 1, engine.executeCount)
                assertEquals("JS 腿不开图像后端", 0, backendOpens)
                assertEquals("JS 腿不调通道", 0, channel.generateCalls)
                assertEquals("JS 腿零远程派发（真实 SQL 计数）", 0, readParentDispatchCount(database, parent.requestId))
                // JS 腿的持久孪生：operation 0/0 SUCCEEDED + attempt `reservation_id IS NULL`。
                readSingleOperation(database, OPERATION_ID).let { operation ->
                    assertEquals("SUCCEEDED", operation.string("status"))
                    assertEquals(0, operation.int("reserved_remote_dispatches"))
                    assertEquals(0, operation.int("used_remote_dispatches"))
                }
                val jsAttempt = readSingleAttempt(database, OPERATION_ID)
                assertEquals("JS", jsAttempt.string("method"))
                assertNull("本地 attempt 不携带预留", jsAttempt.stringOrNull("reservation_id"))
                assertEquals(
                    "input fingerprint 是 JS 形状（program 摘要参与，A-08）",
                    figureJsBasisFingerprint(
                        operationId = OPERATION_ID,
                        sourceSha256 = null,
                        program = PROGRAM_JS,
                    ),
                    jsAttempt.string("input_fingerprint"),
                )

                // 第 2 份：IMAGE 腿经真实账本预留后产出。
                val imageOutcome = coordinator.generate(imageRequest(parent.requestId), imageProposal())
                assertEquals(
                    "actual: status=${imageOutcome.status} diagnostics=${imageOutcome.diagnostics}",
                    FigureGenerationStatus.GENERATED,
                    imageOutcome.status,
                )
                assertTrue(
                    "IMAGE 产物必须是 ImageAsset，got ${imageOutcome.artifact?.let { it::class.simpleName }}",
                    imageOutcome.artifact is FigureArtifact.ImageAsset,
                )
                assertEquals("IMAGE 腿开后端一次", 1, backendOpens)
                assertEquals("IMAGE 腿调通道一次", 1, channel.generateCalls)
                assertEquals("IMAGE 腿占一次真实远程派发", 1, readParentDispatchCount(database, parent.requestId))
                // 同一 operationId 的持久孪生：仍是一行 operation（重放插入 IGNORE，
                // FigureGenerationDao.insertOperation），终态由 IMAGE 腿的 finish 写 1/1；
                // attempt 两行（JS 本地 + IMAGE 带预留）。
                readSingleOperation(database, OPERATION_ID).let { operation ->
                    assertEquals("SUCCEEDED", operation.string("status"))
                    assertEquals(1, operation.int("reserved_remote_dispatches"))
                    assertEquals(1, operation.int("used_remote_dispatches"))
                }
                val attempts = readAllAttempts(database, OPERATION_ID)
                assertEquals("同一操作两行 attempt（JS + IMAGE）", 2, attempts.size)
                val imageAttempt = attempts.single { it.string("method") == "IMAGE_MODEL" }
                assertTrue(
                    "IMAGE attempt 必须携带真实预留 id",
                    imageAttempt.stringOrNull("reservation_id")?.startsWith("figure-dispatch:") == true,
                )

                // 第 3 份：同 operationId 的 IMAGE 再生成——协调器最终图门拒绝。
                val third = coordinator.generate(imageRequest(parent.requestId), imageProposal())

                assertEquals(
                    "actual: status=${third.status} diagnostics=${third.diagnostics}",
                    FigureGenerationStatus.REJECTED,
                    third.status,
                )
                assertTrue(
                    "拒绝必须说明最终图上限：${third.diagnostics}",
                    third.diagnostics.orEmpty().contains("已产出 2 份最终图") &&
                        third.diagnostics.orEmpty()
                            .contains("${FigureGenerationLocalJsBudget.MAX_FINAL_FIGURES_PER_OPERATION}"),
                )
                assertNull("被拒的生成不建 attempt 行（无 attemptId 回传）", third.attemptId)
                // 四个计数位逐一钉死：拒绝发生在开后端 / 预留 / 通道 / 进引擎之前。
                assertEquals("被拒的生成不得进引擎", 1, engine.executeCount)
                assertEquals("被拒的生成不得开后端", 1, backendOpens)
                assertEquals("被拒的生成不得调通道", 1, channel.generateCalls)
                assertEquals("被拒的生成不得预留（真实 SQL 计数不动）", 1, readParentDispatchCount(database, parent.requestId))
                // 拒绝不留任何持久痕迹：attempt 仍 2 行、operation 仍 1 行且终态不变。
                assertEquals(2, readAllAttempts(database, OPERATION_ID).size)
                readSingleOperation(database, OPERATION_ID).let { operation ->
                    assertEquals("SUCCEEDED", operation.string("status"))
                    assertEquals(1, operation.int("reserved_remote_dispatches"))
                    assertEquals(1, operation.int("used_remote_dispatches"))
                }
                assertEquals(
                    "引擎收到的源码必须就是提案 program（未换源）",
                    listOf(PROGRAM_JS),
                    engine.executedSourceJs,
                )
            }
        }

    // ------------------------------------------------------------ 夹具（复用批次 36/38 实测件）

    private class SeededParent(
        val requestId: String,
        val taskId: String,
        val stateVersion: Long,
        val status: ModelTaskStatus,
    )

    /**
     * `FigureDispatchBudgetBoundaryInstrumentedTest.seedModelTask` 同型：真实模型任务行经
     * 生产迁移链路落 QUEUED（禁止绕过端口直改 DB——批次 37 修复形态）。
     */
    private suspend fun seedModelTask(database: StudyDatabasePort, tag: String): SeededParent {
        val request = ModelTaskRequest(
            requestId = "task-$tag",
            input = ImagePipelineClassifyInput(
                sourceAssetId = "asset-$tag",
                imageWidth = 1_080,
                imageHeight = 1_440,
            ),
            occurredAtEpochMillis = 1_000,
        )
        val created = database.createModelTask(
            CreateModelTaskCommand(
                taskId = "task-$tag",
                request = request,
                requestFingerprint = ModelTaskFingerprint.of(request),
                occurredAtEpochMillis = 1_000,
            ),
        )
        assertTrue("夹具任务行必须写成功", created.applied)
        val queued = database.transitionModelTask(
            TransitionModelTaskCommand(
                taskId = created.snapshot.taskId,
                expectedStateVersion = created.snapshot.stateVersion,
                expectedStatus = created.snapshot.status,
                nextStatus = ModelTaskStatus.QUEUED,
                stage = ModelTaskStage.PREPARING,
                userMessage = "正在准备",
                attemptCount = created.snapshot.attemptCount,
                occurredAtEpochMillis = ++clock,
            ),
        )
        assertTrue(
            "夹具任务必须经生产迁移进入 QUEUED：applied=${queued.applied}",
            queued.applied,
        )
        assertEquals(ModelTaskStatus.QUEUED, queued.snapshot.status)
        return SeededParent(
            requestId = request.requestId,
            taskId = created.snapshot.taskId,
            stateVersion = queued.snapshot.stateVersion,
            status = queued.snapshot.status,
        )
    }

    private fun jsRequest(parentRequestId: String): FigureGenerationRequest = FigureGenerationRequest(
        operationId = OPERATION_ID,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.JS),
            supportsParametricInteraction = true,
        ),
        budget = FigureGenerationBudget(),
        dispatchParent = FigureDispatchParent.ModelTask(parentRequestId, false),
        source = null,
    )

    private fun imageRequest(parentRequestId: String): FigureGenerationRequest = FigureGenerationRequest(
        operationId = OPERATION_ID,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(),
        dispatchParent = FigureDispatchParent.ModelTask(parentRequestId, false),
        source = null,
    )

    private fun jsProposal(): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        method = FigureGenerationMethod.JS,
        reason = "测试",
        program = PROGRAM_JS,
        fidelity = FigureFidelity.FAITHFUL,
    )

    private fun imageProposal(): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        method = FigureGenerationMethod.IMAGE_MODEL,
        reason = "测试",
        imageOperation = FigureImageOperation.GENERATE,
        imagePrompt = "画一张数轴示意图。",
        fidelity = FigureFidelity.FAITHFUL,
    )

    /** 计数包装：转发真引擎（真实渲染），钉"协调器把哪份源码交给了引擎"。 */
    private class CountingVisualEngine(
        private val delegate: QuickJsVisualExecutionEngine,
    ) : VisualExecutionEngine {
        var executeCount: Int = 0
            private set
        val executedSourceJs = mutableListOf<String>()

        override suspend fun execute(
            request: VisualExecutionRequest,
        ): VisualExecutionResult {
            executeCount += 1
            executedSourceJs.add(request.sourceJs)
            return delegate.execute(request)
        }

        override fun close() = delegate.close()
    }

    /** JVM 门层测试 `StubChannel` 同型：只计数，返回固定字节。 */
    private class CountingImageChannel : ImageGenerationChannel {
        var generateCalls: Int = 0
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            return ImageRedrawResult(
                imageBytes = byteArrayOf(1, 2, 3),
                mimeType = "image/png",
                modelVersion = "test-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult =
            throw UnsupportedOperationException("not used in this test")
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

        override suspend fun measure(
            batch: VisualContentMeasureBatch,
        ): VisualContentMeasureResult {
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
                        digest = figureSha256Hex(
                            entry.payload.toByteArray(StandardCharsets.UTF_8),
                        ),
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------ SQL 观测（V01 同型手法）

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

    private suspend fun readSingleAttempt(database: StudyDatabasePort, operationId: String): AuditRow {
        val rows = readAllAttempts(database, operationId)
        assertEquals("expected exactly one attempt row for $operationId", 1, rows.size)
        return rows.single()
    }

    private suspend fun readParentDispatchCount(database: StudyDatabasePort, requestId: String): Int {
        val rows = readRows(
            database,
            "model_task_operation operation JOIN model_task task " +
                "ON task.operation_fingerprint = operation.operation_fingerprint",
            "operation.dispatch_count AS dispatch_count",
            "task.request_id = ?",
            requestId,
        )
        assertEquals("expected exactly one parent dispatch row for $requestId", 1, rows.size)
        return rows.single().int("dispatch_count")
    }

    /** 只读第二条连接（V01 `withSqlite` 同型；显式传库名，WAL 与 Room 连接共存为既测惯例）。 */
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
            sqlite.rawQuery(
                "SELECT $columns FROM $table WHERE $where",
                args,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows.add(AuditRow(readRowValues(cursor)))
                }
            }
            return rows
        } finally {
            sqlite.close()
        }
    }

    private fun readRowValues(cursor: Cursor): Map<String, Any?> {
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
        return values
    }

    // ------------------------------------------------------------ 建库/清理（仓内惯例）

    private var clock: Long = 10_000L

    /** `FigureDispatchBudgetBoundaryInstrumentedTest.withDatabase` 同型：随机名、用后删。 */
    private suspend fun withDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val databaseName = "batch39-a10-cross-${System.nanoTime()}.db"
        databaseNames.add(databaseName)
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            block(store)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun densityOf(): Float = context.resources.displayMetrics.density

    private fun fontScaleOf(): Float =
        context.resources.configuration.fontScale.let { if (it <= 0f) 1f else it }

    private companion object {
        const val OPERATION_ID = "operation-a10-coord-cross"

        /** 讲解场景（V01 `PROGRAM_JS` 同构：一组 + 平移内含圆路径与真实度量驱动的 label）。 */
        val PROGRAM_JS = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '$LABEL_PAYLOAD' }]; },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-a10-coord-cross', revision: input.revision,
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

        val FRAME = VisualFrameSnapshot(
            viewport = VisualViewport(360, 240),
            theme = "light",
            seed = 42L,
            timeMs = 1_000L,
        )

        const val OPERATION_TABLE = "figure_generation_operation"
        const val ATTEMPT_TABLE = "figure_generation_attempt"
        const val OPERATION_COLUMNS =
            "status, reserved_remote_dispatches, used_remote_dispatches"
        const val ATTEMPT_COLUMNS = "attempt_id, method, status, reservation_id, input_fingerprint"
    }
}
