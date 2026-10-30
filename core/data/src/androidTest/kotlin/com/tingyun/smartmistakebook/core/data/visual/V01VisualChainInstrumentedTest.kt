package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import com.tingyun.smartmistakebook.core.database.TutorVisualTurnCommitRecord
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.figureJsBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * **V-01 全链仪器化用例（批次 36 计划任务 5）**：tool proposal → coordinator（注入引擎）→
 * 真实 QuickJS 引擎（真编译器资产）→ 真实度量 → compiler（DrawOps）→ painter（屏）→
 * 保存/真实重开恢复 → painter（PDF）。全程 **0 Provider 派发**，以四个独立断言位钉住。
 *
 * 装配原则（R2 `docs/research/2026-10-30-v01-chain-seams.md` §1.3）：**不接生产装配点**。
 * `FigureGenerationCoordinationFactory.create` 的第 4/5 参（`visualEngine`/`visualFrameInputs`）
 * 是既有公共缝（`DefaultFigureGenerationCoordinator.kt:810-827`）；生产装配仍只传三参
 * （`SmartMistakeBookApplication.kt:363-369`）→ 生产 JS 恒 UNAVAILABLE，能力快照/广告面
 * 一字不动。负控用例
 * [theProductionAssemblyWithoutAnInjectedEngineKeepsJsUnavailableWithoutAuditRows]
 * 把"生产 null → UNAVAILABLE 且零审计行"钉在同一套件里，注入缝一旦失灵本套件即红。
 *
 * **0 Provider 派发的四个独立断言位**（任一为真即可单独证伪"JS 占了远程派发"）：
 * ① 审计 operation 行 `reserved_remote_dispatches=0 AND used_remote_dispatches=0`
 *    （JS 路径 `beginLocalAttempt` 保持 reserved=0，`DefaultFigureGenerationCoordinator.kt:602-624`）；
 * ② 审计 attempt 行 `method='JS' AND reservation_id IS NULL`
 *    （本地写径唯一形状，对账豁免依赖它，`:636-639` KDoc）；
 * ③ 账本零触碰：JS 路径不调 `ledger.reserve`（`:240-249` KDoc）→ 预置父操作行
 *    `model_task_operation.dispatch_count` 写前写后不变（见 [seedParentOperation]）；
 * ④ 产物恒 [FigureArtifact.Program]，绝无 [FigureArtifact.ImageAsset]
 *    （`generateLocalJs:327-339` 唯一成功形态）；叠加 `networkRequestsAllowed=false`
 *    （凭证门 `ImageCredentialGate.kt:21` 恒 null）与只抛异常的配置门替身（[UnreachableConfigurationStore]）。
 *
 * **降级登记（计划任务 5 原文允许的两处）**：
 * 1. **UI 卡片段**：`VisualSceneCard` 消费面零命中（grep 实证，R2 §1.1），屏渲染腿以
 *    [VisualDrawOpsPainter.drawRendered] 测试内最小渲染替代（Canvas/位图断言）——这是
 *    "测试内最小渲染"的登记形态，不声称 UI 卡片已消费本链（归 S-07）。
 * 2. **度量器**：生产真实度量器 `AndroidVisualContentMeasurer` 在 app 模块
 *    （`visualjs/VisualContentMeasuring.kt:53`），core:data 依赖方向不可达。本套件的
 *    [DeviceTextMeasurer] 在测试内做**真机真实文本度量**（真实系统字体 metrics：flags 钉
 *    [REPLAY_TEXT_PAINT_FLAGS]=0、textSize=sizeSp×density×fontScale、`measureText`/fontMetrics
 *    换算 du）——与回放侧 painter 的 textPaint 装配同构（KD-78 ④ 口径），不是固定值桩。
 *    与 app 侧实现的两处已知差异如实登记：无 API 33+ 文本感知 metrics（混排行高可能低估，
 *    R2 §1.5-1 边界 P1）；无环境摘要指纹（digest 只按 payload 字节，本套件不做缓存语义）。
 *
 * **本轨纪律**：不运行 Gradle/设备命令（门禁由脚本串行执行）；以下判据是设备轨要执行的
 * 断言，不是已通过的结果（同 `TutorVisualCommitRepositoryInstrumentedTest` 先红后绿登记法）。
 * 方法名无空格（D8 SimpleName 约束，批次 21 设备轨实证）。
 */
@RunWith(AndroidJUnit4::class)
class V01VisualChainInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val engines = CopyOnWriteArrayList<QuickJsVisualExecutionEngine>()
    private val databases = CopyOnWriteArrayList<StudyDatabasePort>()
    private val databaseNames = CopyOnWriteArrayList<String>()
    private val createdFiles = CopyOnWriteArrayList<File>()

    @After
    fun tearDown() {
        engines.forEach { runCatching { it.close() } }
        engines.clear()
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
        createdFiles.forEach { runCatching { it.delete() } }
        createdFiles.clear()
    }

    // ------------------------------------------------------------ V-01 用例 A：生成腿 + 0 派发四断言位

    @Test
    fun aJsProposalThroughTheCoordinatorRendersWithZeroRemoteDispatchWitnesses() = runBlocking {
        withStore { store ->
            seedParentOperation(store, PARENT_REQUEST_ID)
            val measurer = DeviceTextMeasurer(densityOf(), fontScaleOf())
            val engine = engine(measurer)
            val coordinator = FigureGenerationCoordinationFactory.create(
                database = store,
                configurationStore = UnreachableConfigurationStore(),
                networkRequestsAllowed = false,
                visualEngine = engine,
                visualFrameInputs = VisualFrameInputs { _, _ -> frozenFrame() },
            )

            val generated = coordinator.generate(chainRequest(), jsProposal())

            // 链结论：GENERATED + Program 产物（不是 ImageAsset）。
            assertEquals(
                "actual: status=${generated.status} diagnostics=${generated.diagnostics}",
                FigureGenerationStatus.GENERATED,
                generated.status,
            )
            assertEquals(FigureGenerationMethod.JS, generated.method)
            val artifact = requirePresent(generated.artifact, "a generated outcome must carry its artifact")
            // 断言位 ④：产物是 Program（源码即产物），绝无 ImageAsset。
            assertTrue(
                "a JS generation must carry a Program artifact, got ${artifact::class.simpleName}",
                artifact is FigureArtifact.Program,
            )
            val program = artifact as FigureArtifact.Program
            assertEquals(EXPLANATION_PURPOSE, program.purpose)
            assertEquals(PROGRAM_JS, program.sourceJs)
            assertEquals(
                "programId = visual:{operationId}:{sha256(program) 前 16 位}" +
                    "（DefaultFigureGenerationCoordinator.kt:772-775）",
                "visual:$OPERATION_ID:" +
                    figureSha256Hex(PROGRAM_JS.toByteArray(StandardCharsets.UTF_8)).take(16),
                program.programId,
            )

            // 断言位 ①：审计 operation 行 0/0（reserved=used=0）且 SUCCEEDED。
            val operation = readSingleRow(
                "SELECT status, reserved_remote_dispatches, used_remote_dispatches " +
                    "FROM figure_generation_operation WHERE operation_id = ?",
                OPERATION_ID,
            )
            assertEquals("operation status", "SUCCEEDED", operation.string("status"))
            assertEquals("reserved_remote_dispatches", 0, operation.int("reserved_remote_dispatches"))
            assertEquals("used_remote_dispatches", 0, operation.int("used_remote_dispatches"))

            // 断言位 ②：审计 attempt 行 method='JS' 且 reservation_id IS NULL（本地写径唯一形状）。
            val attempts = readAllRows(
                "SELECT attempt_id, method, status, reservation_id, input_fingerprint " +
                    "FROM figure_generation_attempt WHERE operation_id = ?",
                OPERATION_ID,
            )
            assertEquals("exactly one local attempt", 1, attempts.size)
            val attempt = attempts.single()
            assertEquals("attempt method", "JS", attempt.string("method"))
            assertEquals("attempt status", "SUCCEEDED", attempt.string("status"))
            assertNull("local attempt must not carry a reservation", attempt.stringOrNull("reservation_id"))
            requirePresent(
                generated.attemptId,
                "a recorded local attempt carries the outcome attempt id",
            )
            assertEquals(
                "attempt id must be the one the outcome carried back",
                generated.attemptId,
                attempt.string("attempt_id"),
            )
            assertEquals(
                "input fingerprint is the JS 形状（program 摘要参与，A-08）",
                figureJsBasisFingerprint(
                    operationId = OPERATION_ID,
                    sourceSha256 = null,
                    program = PROGRAM_JS,
                ),
                attempt.string("input_fingerprint"),
            )

            // 断言位 ③：账本零触碰——预置父操作行的 dispatch_count 写前写后不变。
            assertEquals(
                "the JS path must never touch the durable dispatch ledger",
                0,
                readParentDispatchCount(PARENT_REQUEST_ID),
            )

            // 度量真实发生过（不是桩的固定值）：真实字体 metrics 参与了 render。
            val measured = requirePresent(measurer.measured.singleOrNull(), "one label entry must be measured")
            assertTrue("real advance width must be positive", measured.widthDu > 0f)
            assertTrue("real line height must be positive", measured.heightDu > 0f)
        }
    }

    // ------------------------------------------------------------ 负控：生产装配（不注入引擎）恒 UNAVAILABLE

    /**
     * R2 §0 核心结论的钉子：**生产装配只传三参 → JS 恒 UNAVAILABLE**。若有人把生产装配点
     * 接上引擎而能力快照/广告面未同步，或把"未接通"改成假成功，本负控即红。
     */
    @Test
    fun theProductionAssemblyWithoutAnInjectedEngineKeepsJsUnavailableWithoutAuditRows() = runBlocking {
        withStore { store ->
            val coordinator = FigureGenerationCoordinationFactory.create(
                database = store,
                configurationStore = UnreachableConfigurationStore(),
                networkRequestsAllowed = false,
            )

            val outcome = coordinator.generate(chainRequest(), jsProposal())

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.UNAVAILABLE,
                outcome.status,
            )
            assertNull("an unavailable generation carries no artifact", outcome.artifact)
            assertNull("no attempt was started", outcome.attemptId)
            // 未接通不建 attempt、不碰账本，审计也没有 operation 行（与"未接通"逐位一致）。
            // 读前先强制建库（UNAVAILABLE 路径全程不触库，文件可能尚未生成）。
            store.readDatabaseVersion()
            assertEquals(
                0,
                readAllRows(
                    "SELECT operation_id FROM figure_generation_operation WHERE operation_id = ?",
                    OPERATION_ID,
                ).size,
            )
        }
    }

    // ------------------------------------------------------------ V-01 用例 B+C：屏渲染 + 保存/真实重开 + 恢复重放

    @Test
    fun theFrozenDrawCommandsReplayToTheSameNonBlankBitmapBeforeAndAfterARealReopen() = runBlocking {
        val chain = runChainToRestoredReplay()
        val density = densityOf()
        val labelResolver = chain.labelResolver()

        // 重启前屏栅格（测试内最小渲染——UI 卡片段降级登记见文件 KDoc）。
        val beforePixels = chain.rendered.renderToPixels(density, labelResolver)
        val width = (VIEWPORT.widthDu * density).roundToInt()
        val height = (VIEWPORT.heightDu * density).roundToInt()

        // 屏渲染腿断言：背景角点 + 路径墨 + 文本带墨（真实度量驱动投影）。
        assertEquals(0xFFFFFFFF.toInt(), beforePixels[0])
        assertTrue("the replay must contain path ink", chain.hasPathInk(beforePixels, width, height))
        assertTrue(
            "the label band must contain text ink (real metrics drove the projection)",
            chain.hasLabelInk(beforePixels, width, height, density),
        )

        // 恢复结果必须是 Replay（②档重放），不是 ReadOnly 降级。
        val replay = chain.restoreOutcome as VisualRestoreOutcome.Replay
        assertEquals(chain.committed.snapshot.snapshotId, replay.snapshotId)
        assertEquals(VIEWPORT.widthDu, replay.viewportWidthDu)
        assertEquals(VIEWPORT.heightDu, replay.viewportHeightDu)
        assertEquals(FRAME_THEME, replay.theme)
        // 恢复出的文档与重启前同一份 DrawOps 文本（同一解析器、同一不变量、同一指令序列）。
        assertEquals(chain.document, replay.document)

        // 重绘一致性：恢复文档重走 painter → 与重启前栅格逐位相等（同设备确定性）。
        val afterBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            VisualDrawOpsPainter().draw(replay.document, Canvas(afterBitmap), density, labelResolver)
            val afterPixels = IntArray(width * height).also {
                afterBitmap.getPixels(it, 0, width, 0, 0, width, height)
            }
            assertTrue(
                "the restored document must replay to the exact pre-restart raster",
                Arrays.equals(beforePixels, afterPixels),
            )
        } finally {
            afterBitmap.recycle()
        }
    }

    // ------------------------------------------------------------ V-01 用例 D：PDF 断言

    @Test
    fun theRestoredDocumentWritesAPdfPassingIntegrityAndRendererReadback() = runBlocking {
        val chain = runChainToRestoredReplay()
        val replay = chain.restoreOutcome as VisualRestoreOutcome.Replay
        val ptPerDu = 1f
        val target = File(context.cacheDir, "v01-chain-${System.nanoTime()}.pdf")
        createdFiles.add(target)

        VisualDrawOpsPdfPainter().writeFile(
            document = replay.document,
            target = target,
            ptPerDu = ptPerDu,
            labelContent = chain.labelResolver(),
        )

        // D1∧D2∧D3 由 writeFile 内部强制（任一不成立即 PAINTER_PDF_IO，含失败不发布）；
        // 这里再取直接文件级证据：非空 + 尾窗含 %%EOF。
        assertTrue("the published pdf must exist", target.exists())
        assertTrue("the published pdf must not be empty", target.length() > 0L)
        val bytes = target.readBytes()
        val tail = bytes.copyOfRange(maxOf(0, bytes.size - PDF_TAIL_WINDOW_BYTES), bytes.size)
        assertTrue(
            "the pdf tail window must contain the trailer marker",
            String(tail, StandardCharsets.US_ASCII).contains(PDF_TRAILER_MARKER),
        )

        // PdfRenderer 回读（仓内约定，R2 §1.3 用例 D）：单页 + 页尺寸 = du×ptPerDu +
        // 栅格非空白 + 文本带墨（硬判 ink > 0，消灭 Skia 静默丢字形）。
        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(
                        "page width must be viewBox.w × ptPerDu (ceil)",
                        ceil(VIEWPORT.widthDu * ptPerDu).toInt(),
                        page.width,
                    )
                    assertEquals(
                        "page height must be viewBox.h × ptPerDu (ceil)",
                        ceil(VIEWPORT.heightDu * ptPerDu).toInt(),
                        page.height,
                    )
                    val scale = 2f
                    val rasterWidth = (page.width * scale).roundToInt()
                    val rasterHeight = (page.height * scale).roundToInt()
                    val bitmap = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            Matrix().apply { setScale(scale, scale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT,
                        )
                        val pixels = IntArray(rasterWidth * rasterHeight).also {
                            bitmap.getPixels(it, 0, rasterWidth, 0, 0, rasterWidth, rasterHeight)
                        }
                        assertTrue(
                            "the pdf raster must not be blank",
                            pixels.any { it != Color.WHITE },
                        )
                        assertTrue(
                            "the pdf text must leave ink in the label band (no silent glyph drop)",
                            chain.hasLabelInk(pixels, rasterWidth, rasterHeight, ptPerDu * scale),
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ 链的复用骨架（用例 C/D 共用）

    /**
     * 一次完整链路的中间产物：proposal → coordinator（注入引擎）→ Rendered（引擎直取，
     * 供屏/PDF 重放）→ commit（真实仓储一次事务）→ close → 同名重开 → 读行 → 恢复判定。
     */
    private suspend fun runChainToRestoredReplay(): ChainArtifacts {
        val databaseName = "v01-chain-${System.nanoTime()}.db"
        databaseNames.add(databaseName)
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        databases.add(store)
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
        val measurer = DeviceTextMeasurer(densityOf(), fontScaleOf())
        val engine = engine(measurer)
        val frame = frozenFrame()
        val coordinator = FigureGenerationCoordinationFactory.create(
            database = store,
            configurationStore = UnreachableConfigurationStore(),
            networkRequestsAllowed = false,
            visualEngine = engine,
            visualFrameInputs = VisualFrameInputs { _, _ -> frame },
        )
        val generated = coordinator.generate(chainRequest(), jsProposal())
        assertEquals(
            "chain setup must generate: status=${generated.status} diagnostics=${generated.diagnostics}",
            FigureGenerationStatus.GENERATED,
            generated.status,
        )
        val program = requirePresent(generated.artifact, "the chain must produce a Program") as FigureArtifact.Program

        // coordinator 的 Program 产物不携带 drawOps（generateLocalJs:333-337 只回 sourceJs）；
        // 重放腿对同一 program（同 digest，非熔断态）再执行一次直取 Rendered——programId
        // 用产物携带值，不在测试里复算私有公式。
        val rendered = engine.execute(
            VisualExecutionRequest(
                programId = program.programId,
                sourceJs = program.sourceJs,
                sceneRevision = 1, // 协调器 VISUAL_SCENE_REVISION=1（:779，KDoc"首次执行恒为 1"）。
                stateJson = null,
                viewport = frame.viewport,
                timeMs = frame.timeMs,
                seed = frame.seed,
                theme = frame.theme,
                explicitUserRetry = false,
            ),
        )
        val executed = rendered as VisualExecutionResult.Rendered
        val drawCommandsJson = requirePresent(executed.drawCommandsJson, "Rendered must carry draw ops")

        // 保存：一次事务提交 assistant 正文 + program + LIVE 快照 + link（D-02 契约）。
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
                basisFingerprint = "basis-v01-chain",
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
                viewportWidthDu = frame.viewport.widthDu,
                viewportHeightDu = frame.viewport.heightDu,
                fontScaleMilli = (fontScaleOf() * 1_000f).roundToInt(),
                theme = frame.theme,
                timeMs = frame.timeMs,
                seed = frame.seed,
                runtimeVersion = "quickjs-kt-1.0.15",
                compilerVersion = "visual-compiler-v1",
                rendererVersion = VISUAL_RENDERER_IMPLEMENTATION_VERSION,
                fontManifestVersion = FONT_MANIFEST_VERSION,
                questionVerificationStatus = "NOT_CHECKED",
            ),
            ownerRequestId = OWNER_REQUEST_ID,
        )
        assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)

        // 重启构造：真实关闭 + 同名重开（仓内惯例，TutorVisualCommitRepositoryInstrumentedTest
        // withDatabase）→ 读侧既有口（TutorVisualPort.readTutorMessageVisual/readTutorVisualSnapshot）。
        store.close()
        databases.remove(store)
        val reopened = StudyDatabaseFactory.open(context, databaseName)
        databases.add(reopened)
        val link = requirePresent(
            reopened.readTutorMessageVisual(MESSAGE_ID, VISUAL_ORDINAL),
            "the visual link must survive a real reopen",
        )
        val snapshotRow = requirePresent(
            reopened.readTutorVisualSnapshot(link.currentSnapshotId),
            "the snapshot row must survive a real reopen",
        )
        assertEquals(TutorVisualStatus.LIVE, snapshotRow.status)

        // 行 → 恢复记录的测试内映射（R2 §1.2-3：VisualSnapshotRestore 无生产读链，
        // status→live、content_ids_json→Set、版本三列直取）。
        val record = VisualSnapshotRestoreRecord(
            snapshotId = snapshotRow.snapshotId,
            programId = snapshotRow.programId,
            drawCommandsJson = snapshotRow.drawCommandsJson,
            contentIds = parseContentIds(snapshotRow.contentIdsJson),
            sceneSchemaVersion = snapshotRow.sceneSchemaVersion,
            renderSchemaVersion = snapshotRow.renderSchemaVersion,
            rendererVersion = snapshotRow.rendererVersion,
            fontManifestVersion = snapshotRow.fontManifestVersion,
            viewportWidthDu = snapshotRow.viewportWidthDu,
            viewportHeightDu = snapshotRow.viewportHeightDu,
            fontScaleMilli = snapshotRow.fontScaleMilli,
            theme = snapshotRow.theme,
            previewAssetId = snapshotRow.previewAssetId,
            live = snapshotRow.status == TutorVisualStatus.LIVE,
        )
        // 生产 VERIFIED_FONT_MANIFESTS 为空集（VisualSnapshotRestore.kt:125）→ 默认策略恒只读；
        // 测试注入已登记清单验证解析/回放路径本身（VisualRestorePolicy KDoc 明示的测试用法）。
        val restoreOutcome = VisualSnapshotRestore.restore(
            record = record,
            previewVerified = false,
            policy = VisualRestorePolicy(verifiedFontManifests = setOf(FONT_MANIFEST_VERSION)),
        )
        assertTrue(
            "the restored snapshot must replay, got $restoreOutcome",
            restoreOutcome is VisualRestoreOutcome.Replay,
        )
        return ChainArtifacts(
            rendered = executed,
            document = validatedDocument(drawCommandsJson, executed.contentIds),
            committed = committed,
            restoreOutcome = restoreOutcome,
            measurer = measurer,
        )
    }

    private inner class ChainArtifacts(
        val rendered: VisualExecutionResult.Rendered,
        val document: VisualDrawOpsDocument,
        val committed: TutorVisualTurnCommitRecord,
        val restoreOutcome: VisualRestoreOutcome,
        val measurer: DeviceTextMeasurer,
    ) {
        fun labelResolver(): (String) -> VisualLabelContent? {
            val measured = requirePresent(measurer.measured.singleOrNull(), "one label entry must be measured")
            return { contentId ->
                if (contentId == measured.contentId) {
                    VisualLabelContent.Text(
                        payload = measurer.payload,
                        sizeSp = measured.fontSizeSp,
                        fontScale = fontScaleOf(),
                        heightDu = measured.heightDu,
                        lines = listOf(VisualLabelLine(0, measurer.payload.length)),
                    )
                } else {
                    null
                }
            }
        }

        fun hasPathInk(pixels: IntArray, width: Int, height: Int): Boolean {
            // 路径墨存在性：圆心 du (55,55)（circle(50,50,40) 过组平移 +5,+5）非背景色。
            val x = (55 * width.toFloat() / VIEWPORT.widthDu).roundToInt()
            val y = (55 * height.toFloat() / VIEWPORT.heightDu).roundToInt()
            require(x in 0 until width && y in 0 until height) { "probe ($x,$y) outside raster" }
            val center = pixels[y * width + x]
            return center != Color.WHITE && center != Color.TRANSPARENT
        }

        fun hasLabelInk(pixels: IntArray, width: Int, height: Int, pxPerDu: Float): Boolean {
            // label (x,y) 是编译器一次算定的最终左缘 + baseline（painter KDoc :204-205）；
            // 带宽用与回放同构的度量 Paint 量 advance，不重推编译器内部公式。
            val label = document.ops.filterIsInstance<VisualDrawOp.Label>().single()
            val leftPx = (label.x * pxPerDu).roundToInt()
            val baselinePx = (label.y * pxPerDu).roundToInt()
            val measurePaint = Paint().apply { flags = REPLAY_TEXT_PAINT_FLAGS }
            val measuredLabel = requirePresent(measurer.measured.singleOrNull(), "one label entry must be measured")
            measurePaint.textSize = measuredLabel.fontSizeSp * fontScaleOf() * pxPerDu
            val advancePx = measurePaint.measureText(measurer.payload).roundToInt()
            val lineHeightPx = (measuredLabel.heightDu * pxPerDu).roundToInt()
            var ink = 0
            for (y in (baselinePx - lineHeightPx)..(baselinePx + 4)) {
                if (y !in 0 until height) continue
                for (x in (leftPx - 2)..(leftPx + advancePx + 2)) {
                    if (x !in 0 until width) continue
                    if (pixels[y * width + x] != Color.WHITE) ink += 1
                }
            }
            return ink > 0
        }
    }

    // ------------------------------------------------------------ 夹具

    /**
     * 测试内真实文本度量（降级登记见文件 KDoc：生产度量器在 app 模块不可达）。
     * 与回放侧同构（KD-78 ④）：flags 钉 [REPLAY_TEXT_PAINT_FLAGS]=0、textSize =
     * sizeSp × density × fontScale px；advance/fontMetrics → du（/density）。
     */
    private class DeviceTextMeasurer(
        private val density: Float,
        fontScale: Float,
        private val sizeSp: Float = 16f,
    ) : VisualContentMeasurer {
        private val paint = Paint().apply {
            flags = REPLAY_TEXT_PAINT_FLAGS
            textSize = sizeSp * density * fontScale
        }
        val payload = LABEL_PAYLOAD
        val measured = CopyOnWriteArrayList<VisualMeasuredContent>()

        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
            if (batch.entries.isEmpty()) {
                return VisualContentMeasureResult(emptyList())
            }
            val metrics = paint.fontMetrics
            val lineHeightPx = metrics.descent - metrics.ascent
            return VisualContentMeasureResult(
                batch.entries.mapIndexed { index, entry ->
                    require(entry.kind == VisualContentKind.TEXT) {
                        "the chain fixture measures TEXT only, got ${entry.kind}"
                    }
                    val content = VisualMeasuredContent(
                        contentId = "c$index",
                        widthDu = paint.measureText(entry.payload) / density,
                        heightDu = lineHeightPx / density,
                        baselineDu = -metrics.ascent / density,
                        fontSizeSp = sizeSp,
                        digest = figureSha256Hex(entry.payload.toByteArray(StandardCharsets.UTF_8)),
                    )
                    measured.add(content)
                    content
                },
            )
        }
    }

    /**
     * 配置门 tripwire：JS 链不读凭证（凭证门在 `networkRequestsAllowed=false` 时先短路，
     * `ImageCredentialGate.kt:21-22`）；任何让 JS 路径触达配置/凭证的改动都会在这里炸出
     * UnsupportedOperationException，而不是静默通过。
     */
    private class UnreachableConfigurationStore : ModelConfigurationStore {
        override val configuration: Flow<ModelConfigurationSnapshot> = emptyFlow()

        override suspend fun save(
            update: ModelConfigurationUpdate,
            apiKey: ModelApiKey,
        ): ModelConfigurationMutationResult =
            throw UnsupportedOperationException("the JS chain must not read the configuration store")

        override suspend fun rotateApiKey(apiKey: ModelApiKey): ModelConfigurationMutationResult =
            throw UnsupportedOperationException("the JS chain must not read the configuration store")

        override suspend fun readCredential(): ModelCredentialReadResult =
            throw UnsupportedOperationException("the JS chain must not read the configuration store")

        override suspend fun clear(): ModelConfigurationMutationResult =
            throw UnsupportedOperationException("the JS chain must not read the configuration store")
    }

    private fun engine(measurer: VisualContentMeasurer): QuickJsVisualExecutionEngine =
        QuickJsVisualExecutionEngine(measurer).also { engines.add(it) }

    private fun frozenFrame() = VisualFrameSnapshot(
        viewport = VIEWPORT,
        theme = FRAME_THEME,
        seed = 42L,
        timeMs = 1_000L,
    )

    /** tool proposal 腿：经 [TutorToolCall] 的 JS 工具调用形状构造（A-02 同形）。 */
    private fun jsProposal(): FigureGenerationProposal =
        TutorToolCall(
            tool = TutorToolName.GENERATE_FIGURE,
            rationale = "为这道题生成一张讲解图",
            figureKind = TutorFigureKind.GENERATE_PROCESS,
            figureDescription = "一张带标签的圆与说明文字",
            figureMethod = FigureGenerationMethod.JS,
            figureProgram = PROGRAM_JS,
        ).toFigureGenerationProposal() ?: error("a GENERATE_FIGURE JS call must produce a proposal")

    private fun chainRequest() = FigureGenerationRequest(
        operationId = OPERATION_ID,
        purpose = EXPLANATION_PURPOSE,
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
        basisFingerprint = null, // 旧调用口径：coordinator 按 JS 形状自行推导（A-08）。
        explicitUserRetry = false,
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

    /**
     * 预置账本父操作（断言位 ③ 的观测对象）：`model_task_operation` 行 + 引用它的
     * `model_task` 行（FK RESTRICT），沿用审计夹具的库文件直写手法
     * （`FigureGenerationAuditFixtures.kt` 同型：Room 建好 schema 后第二条连接写行）。
     * `findByRequestId`（ModelTaskTransactionDao.kt:328）按 request_id 找任务行、再按
     * operation_fingerprint 找操作行——两行都在，`reserve` 若被误调进 JS 路径就会真的
     * 递增 `dispatch_count`，断言位 ③ 即红。
     */
    private suspend fun seedParentOperation(store: StudyDatabasePort, requestId: String) {
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
                    "task_id" to "task-v01-chain",
                    "request_id" to requestId,
                    "request_fingerprint" to "request-fingerprint-v01",
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

    private fun readParentDispatchCount(requestId: String): Int {
        val row = readSingleRow(
            "SELECT operation.dispatch_count AS dispatch_count " +
                "FROM model_task_operation operation " +
                "JOIN model_task task ON task.operation_fingerprint = operation.operation_fingerprint " +
                "WHERE task.request_id = ?",
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

    private fun readSingleRow(sql: String, vararg args: String): AuditRow {
        val rows = readAllRows(sql, *args)
        assertEquals("expected exactly one row for: $sql", 1, rows.size)
        return rows.single()
    }

    /** 与审计夹具同一手法（第二条 SQLite 连接），方向相反：只读查询。 */
    private fun readAllRows(sql: String, vararg args: String): List<AuditRow> =
        withSqlite { sqlite ->
            val rows = mutableListOf<AuditRow>()
            sqlite.rawQuery(sql, args).use { cursor ->
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
            rows
        }

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

    /** 仓内惯例的建库/清理（TutorVisualCommitRepositoryInstrumentedTest.withDatabase 同型）。 */
    private suspend fun withStore(block: suspend (StudyDatabasePort) -> Unit) {
        val databaseName = "v01-chain-audit-${System.nanoTime()}.db"
        databaseNames.add(databaseName)
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        databases.add(store)
        block(store)
    }

    private fun densityOf(): Float = context.resources.displayMetrics.density

    private fun fontScaleOf(): Float =
        context.resources.configuration.fontScale.let { if (it <= 0f) 1f else it }

    private fun validatedDocument(json: String, contentIds: Set<String>): VisualDrawOpsDocument {
        val validation = VisualDrawOpsValidator.validate(json, contentIds, VIEWPORT)
        if (validation !is VisualDrawOpsValidation.Valid) {
            val invalid = validation as VisualDrawOpsValidation.Invalid
            fail("compiled draw ops must validate: ${invalid.code}: ${invalid.message}")
        }
        return (validation as VisualDrawOpsValidation.Valid).document
    }

    private fun VisualExecutionResult.Rendered.renderToPixels(
        pxPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ): IntArray {
        val width = (VIEWPORT.widthDu * pxPerDu).roundToInt()
        val height = (VIEWPORT.heightDu * pxPerDu).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            VisualDrawOpsPainter().drawRendered(this, Canvas(bitmap), pxPerDu, labelContent)
            return IntArray(width * height).also {
                bitmap.getPixels(it, 0, width, 0, 0, width, height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun parseContentIds(json: String): Set<String> =
        (Json.parseToJsonElement(json) as JsonArray)
            .map { element -> (element as JsonPrimitive).content }
            .toSet()

    private fun <T> requirePresent(value: T?, message: String): T = value ?: error(message)

    private companion object {
        /** 讲解场景：一组（平移 +5,+5、opacity 0.5）内含一个圆路径与一个真实度量驱动的 label。 */
        val PROGRAM_JS = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '$LABEL_PAYLOAD' }]; },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-v01-chain', revision: input.revision,
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

        val VIEWPORT = VisualViewport(360, 240)
        const val FRAME_THEME = "light"
        const val LABEL_PAYLOAD = "标签 S=ab"
        const val OPERATION_ID = "operation-v01-chain"
        const val PARENT_REQUEST_ID = "request-v01-chain-parent"
        const val PARENT_OPERATION_FINGERPRINT = "fingerprint-v01-chain-parent"
        const val SUBJECT_ID = "subject-v01-chain"
        const val TASK_KIND = "TUTOR"
        const val CONVERSATION_ID = "conversation-v01-chain"
        const val MESSAGE_ID = "message-v01-chain"
        const val OWNER_REQUEST_ID = "owner-v01-chain"
        const val LEARNER_ID = "learner-v01-chain"
        const val VISUAL_ORDINAL = 0
        val EXPLANATION_PURPOSE = FigureGenerationPurpose.EXPLANATION

        /** 生产 VERIFIED_FONT_MANIFESTS 为空集；测试注入的已登记清单版本（策略 KDoc 明示用法）。 */
        const val FONT_MANIFEST_VERSION = "font-manifest-v1"
    }
}
