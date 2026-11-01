package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.database.CreateModelTaskCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TransitionModelTaskCommand
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchKind
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.VisualSourceBasis
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X-02 源证据门设备向量（批次 39 计划任务 8）：清单 :241 验收列五个名字
 * **缺源 / 错图 / SHA 变化 / 授权不足 / 预算不足**的设备半边 + 一个正控。
 *
 * 与 JVM 半边的分层（批次 38 落树：`VisualSourceBasisTest` 钉策略门 code、
 * `VisualSourceSeenDispatchObservationTest` 钉 seen 观测——两者都是 fake 账本/审计）：
 * 本套件经 [DefaultFigureGenerationCoordinator] 全路径 + **真 Room 账本/审计**，钉的是
 * JVM fake 证明不了的持久形状：
 * - 五个拒绝位全部落在真库零写入上（operation/attempt 两表 0 行、`dispatch_count` 不动、
 *   后端不开、通道不调）——"先于预留"从 fake 计数升格为**真实 SQL 行计数**；
 * - 预算不足走真实 `reserveModelTaskOperationDispatch` 的 BUDGET_EXHAUSTED 拒绝路径
 *   （真实计数 6 进诊断），不是 fake 的 Denied 替身；
 * - 正控钉"已发送"的持久孪生：attempt 行 `reservation_id` 非空 + `source_sha256` 等于
 *   实际出网字节摘要，且 `outcome.sourceSeen.sourceSeenDispatchId` 与该行 attempt_id 一一
 *   对应（契约 :39"实际发送后记录 sourceSeenDispatchId/字节摘要"的真库证据位）。
 *
 * 先红后绿登记（设备轨执行时核对）：
 * - 任一拒绝位若被放宽（如源证据门放行未授权派发），对应例红在 outcome 状态/诊断断言；
 * - "先于预留"若破坏（拒绝前建了行/预留了派发），红在行计数或 `dispatch_count` 断言；
 * - 正控若 seen 记录与持久 attempt 行脱钩（冒充已发送），红在 attempt 行
 *   `source_sha256`/`reservation_id` 或 seen 绑定断言。不许放宽断言变绿。
 *
 * 夹具沿用：`withDatabase`/`seedModelTask`（批次 36 `FigureDispatchBudgetBoundaryInstrumentedTest`
 * 同型）、SQL 观测（批次 38 `V01VisualChainInstrumentedTest` 同型）、budget 耗尽法（同上
 * 边界测试：直调生产账本 6 次真实预留）；basis/source 形状沿 JVM
 * `VisualSourceSeenDispatchObservationTest`（批次 38 实测件），账本/审计换真。
 */
@RunWith(AndroidJUnit4::class)
class VisualSourceEvidenceDeviceVectorInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val databaseNames = java.util.concurrent.CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
    }

    // ------------------------------------------------------------ 缺源

    @Test
    fun missingSourceEvidenceRejectsTheEditBeforeAnyDurableWrite() = runBlocking {
        withDatabase { database ->
            val fixture = deviceVectorFixture(database)
            val request = request(
                parentRequestId = fixture.parentRequestId,
                basis = authorizedBasis(),
                source = null, // 缺源：编辑必须带已授权源图字节，而请求一个都没带。
            )

            val outcome = fixture.coordinator.generate(request, editProposal(request))

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.REJECTED,
                outcome.status,
            )
            assertTrue(
                "缺源拒绝必须说明编辑需要已授权源图：${outcome.diagnostics}",
                outcome.diagnostics.orEmpty().contains("图像编辑必须带已授权的源图字节"),
            )
            assertNothingDurable(database, fixture)
        }
    }

    // ------------------------------------------------------------ 错图

    @Test
    fun mismatchedSourceAssetAgainstTheFrozenBasisIsRejectedBeforeAnyDurableWrite() = runBlocking {
        withDatabase { database ->
            val fixture = deviceVectorFixture(database)
            val request = request(
                parentRequestId = fixture.parentRequestId,
                basis = authorizedBasis(assetId = "asset-frozen-x02"),
                // 字节与 SHA 都对，但资产 id 与冻结基准的 canonical 引用不一致（错图）。
                source = FigureSourceEvidence(
                    assetId = "asset-other-x02",
                    contentSha256 = figureSha256Hex(SOURCE_BYTES),
                    mimeType = "image/png",
                    imageBytes = SOURCE_BYTES,
                ),
            )

            val outcome = fixture.coordinator.generate(request, editProposal(request))

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.REJECTED,
                outcome.status,
            )
            assertTrue(
                "错图拒绝必须说明 canonical 引用不一致：${outcome.diagnostics}",
                outcome.diagnostics.orEmpty().contains("与冻结基准的 canonical 引用"),
            )
            assertNothingDurable(database, fixture)
        }
    }

    // ------------------------------------------------------------ SHA 变化

    @Test
    fun changedSourceBytesAgainstTheFrozenShaAreRejectedBeforeAnyDurableWrite() = runBlocking {
        withDatabase { database ->
            val fixture = deviceVectorFixture(database)
            val request = request(
                parentRequestId = fixture.parentRequestId,
                basis = authorizedBasis(sha = figureSha256Hex("其它字节".toByteArray())),
                source = defaultSource(),
            )

            val outcome = fixture.coordinator.generate(request, editProposal(request))

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.REJECTED,
                outcome.status,
            )
            assertTrue(
                "SHA 变化拒绝必须说明字节与冻结基准不一致：${outcome.diagnostics}",
                outcome.diagnostics.orEmpty().contains("SHA 不一致"),
            )
            assertNothingDurable(database, fixture)
        }
    }

    // ------------------------------------------------------------ 授权不足

    @Test
    fun unauthorizedDisclosureBasisRejectsByteDispatchBeforeAnyDurableWrite() = runBlocking {
        withDatabase { database ->
            val fixture = deviceVectorFixture(database)
            // 引用与 SHA 成对且与字节一致，但 allowsImageDisclosure=false（授权不足）。
            val request = request(
                parentRequestId = fixture.parentRequestId,
                basis = VisualSourceBasis(
                    subjectId = SUBJECT_ID,
                    revision = 1,
                    canonicalAssetId = "asset-frozen-x02",
                    contentSha256 = figureSha256Hex(SOURCE_BYTES),
                ),
                source = defaultSource(),
            )

            val outcome = fixture.coordinator.generate(request, editProposal(request))

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.REJECTED,
                outcome.status,
            )
            assertTrue(
                "授权不足拒绝必须说明基准未授权披露原图：${outcome.diagnostics}",
                outcome.diagnostics.orEmpty().contains("未授权披露原图"),
            )
            assertNothingDurable(database, fixture)
        }
    }

    // ------------------------------------------------------------ 预算不足

    @Test
    fun exhaustedLedgerBudgetDeniesTheAuthorizedDispatchWithTheRealCount() = runBlocking {
        withDatabase { database ->
            val fixture = deviceVectorFixture(database)
            // 夹具半边：先经生产账本把该父操作的真实预算耗尽（6 次真实预留，与批次 38
            // FigureDispatchBudgetBoundaryInstrumentedTest 同法），再让协调器发起一次
            // 授权完整（引用/SHA/授权齐备）的派发——被真账本拒。
            repeat(ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES) { attempt ->
                val decision = fixture.ledger.reserve(fixtureDispatch(fixture.parentRequestId, attempt))
                assertTrue(
                    "夹具第 ${attempt + 1} 次真实预留必须授予",
                    decision is RemoteModelDispatchDecision.Granted,
                )
            }
            assertEquals(
                ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES,
                readParentDispatchCount(database, fixture.parentRequestId),
            )
            val request = request(
                parentRequestId = fixture.parentRequestId,
                basis = authorizedBasis(),
                source = defaultSource(),
            )

            val outcome = fixture.coordinator.generate(request, editProposal(request))

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.FAILED,
                outcome.status,
            )
            assertTrue(
                "预算不足拒绝必须带真实计数与未出网口径：${outcome.diagnostics}",
                outcome.diagnostics.orEmpty()
                    .contains("已派发 ${ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES}") &&
                    outcome.diagnostics.orEmpty().contains("未出网"),
            )
            assertNull("账本拒绝没有 attempt 行", outcome.attemptId)
            assertNull("没有字节出网就没有已发送证据", outcome.sourceSeen)
            assertEquals(
                "被拒的派发不得再动真实计数",
                ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES,
                readParentDispatchCount(database, fixture.parentRequestId),
            )
            // 持久孪生：operation 行落 FAILED 0/0（begin 过、attempt 未建），attempt 0 行。
            readSingleOperation(database, OPERATION_ID).let { operation ->
                assertEquals("FAILED", operation.string("status"))
                assertEquals(0, operation.int("reserved_remote_dispatches"))
                assertEquals(0, operation.int("used_remote_dispatches"))
            }
            assertEquals(0, readAllAttempts(database, OPERATION_ID).size)
        }
    }

    // ------------------------------------------------------------ 正控：授权完整的派发与"已发送"持久孪生

    @Test
    fun anAuthorizedMatchingDispatchLeavesTheSentSourceWitnessDurable() = runBlocking {
        withDatabase { database ->
            val fixture = deviceVectorFixture(database)
            val request = request(
                parentRequestId = fixture.parentRequestId,
                basis = authorizedBasis(),
                source = defaultSource(),
            )

            val outcome = fixture.coordinator.generate(request, editProposal(request))

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.GENERATED,
                outcome.status,
            )
            assertEquals(1, fixture.channel.generateCalls)
            val sentBytes = fixture.channel.lastSourceImageBytes
            assertNotNull("通道必须收到源图字节", sentBytes)
            assertEquals(
                "源图字节必须真的进了派发载荷（通道收到原字节）",
                SOURCE_BYTES.toList(),
                sentBytes!!.toList(),
            )
            val seen = outcome.sourceSeen
            assertNotNull("源图字节出网后 seen 记录必须存在", seen)
            assertEquals(
                "seen 记录指向真实的 attempt 行",
                outcome.attemptId,
                seen!!.sourceSeenDispatchId,
            )
            assertEquals(
                "seen 摘要 = 实际出网源图的 sha",
                figureSha256Hex(SOURCE_BYTES),
                seen.contentSha256,
            )
            assertEquals(1, readParentDispatchCount(database, fixture.parentRequestId))
            // 持久孪生：attempt 行 reservation_id 非空 + source_sha256 = 出网字节摘要。
            readSingleOperation(database, OPERATION_ID).let { operation ->
                assertEquals("SUCCEEDED", operation.string("status"))
                assertEquals(1, operation.int("reserved_remote_dispatches"))
                assertEquals(1, operation.int("used_remote_dispatches"))
            }
            val attempt = readSingleAttempt(database, OPERATION_ID)
            assertEquals("IMAGE_MODEL", attempt.string("method"))
            assertTrue(
                "已发送的 attempt 行必须携带真实预留 id",
                attempt.stringOrNull("reservation_id")?.startsWith("figure-dispatch:") == true,
            )
            assertEquals(
                "attempt 行 source_sha256 必须等于出网字节摘要（持久孪生）",
                figureSha256Hex(SOURCE_BYTES),
                attempt.string("source_sha256"),
            )
            assertEquals(
                "outcome attemptId 与持久行同 id",
                outcome.attemptId,
                attempt.string("attempt_id"),
            )
        }
    }

    // ------------------------------------------------------------ 夹具

    private class DeviceVectorFixture(
        val coordinator: DefaultFigureGenerationCoordinator,
        val ledger: RoomFigureDispatchLedger,
        val channel: RecordingImageChannel,
        val parentRequestId: String,
        val backendOpens: java.util.concurrent.atomic.AtomicInteger =
            java.util.concurrent.atomic.AtomicInteger(0),
    )

    /**
     * 协调器 + 真账本/真审计 + 计数通道；每个用例一条独立库。
     * 真账本与协调器共用同一实例与同一注入时钟（与批次 38 边界测试同型）。
     */
    private suspend fun deviceVectorFixture(database: StudyDatabasePort): DeviceVectorFixture {
        val parent = seedModelTask(database, tag = "x02")
        val ledger = RoomFigureDispatchLedger(database, now = { ++clock; clock })
        val channel = RecordingImageChannel()
        val backendOpens = java.util.concurrent.atomic.AtomicInteger(0)
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = {
                backendOpens.incrementAndGet()
                FigureImageBackend(
                    configurationFingerprint = "config-x02",
                    openChannel = { channel },
                    closeAction = {},
                )
            },
            now = { ++clock; clock },
            audit = RoomFigureGenerationAuditRecorder(database),
        )
        return DeviceVectorFixture(
            coordinator = coordinator,
            ledger = ledger,
            channel = channel,
            parentRequestId = parent.requestId,
            backendOpens = backendOpens,
        )
    }

    private fun fixtureDispatch(parentRequestId: String, attempt: Int): RemoteModelDispatchRequest =
        RemoteModelDispatchRequest(
            operationId = "figure-op:x02-fixture-$attempt",
            parent = FigureDispatchParent.ModelTask(parentRequestId, false),
            remoteKind = RemoteModelDispatchKind.IMAGE_GENERATION,
            method = FigureGenerationMethod.IMAGE_MODEL,
            purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
            policyVersion = FigureGenerationPolicyVersion.VALUE,
            sourceImageSha256 = null,
            configurationFingerprint = "config-x02",
            occurredAtEpochMillis = ++clock,
        )

    private class SeededParent(val requestId: String)

    /** `FigureDispatchBudgetBoundaryInstrumentedTest.seedModelTask` 同型（生产链路落 QUEUED）。 */
    private suspend fun seedModelTask(database: StudyDatabasePort, tag: String): SeededParent {
        val request = ModelTaskRequest(
            requestId = "task-$tag-vector",
            input = ImagePipelineClassifyInput(
                sourceAssetId = "asset-$tag-vector",
                imageWidth = 1_080,
                imageHeight = 1_440,
            ),
            occurredAtEpochMillis = 1_000,
        )
        val created = database.createModelTask(
            CreateModelTaskCommand(
                taskId = "task-$tag-vector",
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
        return SeededParent(requestId = request.requestId)
    }

    private fun request(
        parentRequestId: String,
        basis: VisualSourceBasis?,
        source: FigureSourceEvidence?,
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = OPERATION_ID,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(),
        dispatchParent = FigureDispatchParent.ModelTask(parentRequestId, false),
        source = source,
        visualSourceBasis = basis,
    )

    private fun editProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "基于已授权原图重建干净题面。",
            reason = "测试",
        )

    private fun authorizedBasis(
        assetId: String = "asset-frozen-x02",
        sha: String = figureSha256Hex(SOURCE_BYTES),
    ): VisualSourceBasis = VisualSourceBasis(
        subjectId = SUBJECT_ID,
        revision = 1,
        canonicalAssetId = assetId,
        contentSha256 = sha,
        allowsImageDisclosure = true,
    )

    private fun defaultSource(): FigureSourceEvidence = FigureSourceEvidence(
        assetId = "asset-frozen-x02",
        contentSha256 = figureSha256Hex(SOURCE_BYTES),
        mimeType = "image/png",
        imageBytes = SOURCE_BYTES,
    )

    /** 计数通道（JVM 观测测试 `RecordingChannel` 同型）：记录实际收到的源图字节。 */
    private class RecordingImageChannel : ImageGenerationChannel {
        var generateCalls: Int = 0
            private set
        var lastSourceImageBytes: ByteArray? = null
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            lastSourceImageBytes = request.sourceImageBytes
            return ImageRedrawResult(
                imageBytes = byteArrayOf(9, 8, 7),
                mimeType = "image/png",
                modelVersion = "test-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used in this test")
    }

    /** 五个拒绝位共用的"真库零写入"判据：两表 0 行、计数不动、通道 0 次。 */
    private suspend fun assertNothingDurable(
        database: StudyDatabasePort,
        fixture: DeviceVectorFixture,
    ) {
        assertEquals(
            "策略拒绝不建任何 operation 行",
            0,
            countRows(database, OPERATION_TABLE, "operation_id = ?", OPERATION_ID),
        )
        assertEquals(
            "策略拒绝不建任何 attempt 行",
            0,
            countRows(database, ATTEMPT_TABLE, "operation_id = ?", OPERATION_ID),
        )
        assertEquals(
            "策略拒绝不触碰真实派发计数",
            0,
            readParentDispatchCount(database, fixture.parentRequestId),
        )
        assertEquals("策略拒绝不调通道", 0, fixture.channel.generateCalls)
        assertEquals("策略拒绝不开图像后端", 0, fixture.backendOpens.get())
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

    private suspend fun countRows(
        database: StudyDatabasePort,
        table: String,
        where: String,
        vararg args: String,
    ): Int {
        database.readDatabaseVersion()
        val rows = readRows(database, table, "COUNT(*) AS row_count", where, *args)
        return rows.single().int("row_count")
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
            sqlite.rawQuery(
                "SELECT $columns FROM $table WHERE $where",
                args,
            ).use { cursor ->
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

    // ------------------------------------------------------------ 建库/清理（仓内惯例）

    private var clock: Long = 10_000L

    /** `FigureDispatchBudgetBoundaryInstrumentedTest.withDatabase` 同型：随机名、用后删。 */
    private suspend fun withDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val databaseName = "batch39-x02-evidence-${System.nanoTime()}.db"
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

    private companion object {
        const val OPERATION_ID = "operation-x02-evidence"
        const val SUBJECT_ID = "session-x02-evidence"

        val SOURCE_BYTES = "source-photo-x02".toByteArray()

        const val OPERATION_TABLE = "figure_generation_operation"
        const val ATTEMPT_TABLE = "figure_generation_attempt"
        const val OPERATION_COLUMNS =
            "status, reserved_remote_dispatches, used_remote_dispatches"
        const val ATTEMPT_COLUMNS = "attempt_id, method, status, reservation_id, source_sha256"
    }
}
