package com.tingyun.smartmistakebook.visualjs

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepository
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualProgramInput
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualSnapshotInput
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameInputs
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameSnapshot
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import com.tingyun.smartmistakebook.core.database.TutorVisualTurnCommitRecord
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
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
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * **V-01 全链仪器化用例·真隔离引擎版（批次 37 计划任务 4 / 裁定 1）**：tool proposal →
 * coordinator（注入引擎）→ **真 [VisualJsClientEngine]（`:visual_js` 隔离进程）** →
 * **服务进程内真实 `AndroidVisualContentMeasurer` 度量** → compiler（DrawOps）→
 * 保存/真实重开恢复。全程 **0 Provider 派发**，四个独立断言位钉住。
 *
 * 与 core/data 侧 `core.data.visual.V01VisualChainInstrumentedTest`（进程内
 * `QuickJsVisualExecutionEngine` 降级变体）的分工（批次 37 起生效）：
 * - **本套件**承担"真隔离引擎"语义底线（V-01 判据）：真 bind、真隔离 UID、真 QuickJS、
 *   服务进程内真实度量（`VisualContentMeasuring.kt:53`，warm-up 构造，测试侧不可也不必
 *   替换）；
 * - **core/data 降级变体**承担屏栅格/PDF/restore 策略腿——这些断言依赖 core:data internal
 *   符号（`VisualDrawOpsPainter`/`VisualDrawOpsPdfPainter`/`VisualSnapshotRestore`/
 *   `REPLAY_TEXT_PAINT_FLAGS`，均为 core:data 编译单元 internal），app androidTest 跨模块
 *   不可达（R1 `docs/research/2026-10-31-v01-isolated-wiring.md` §2.3-4 对此的可达性判断
 *   已被本批实读证伪，如实登记）。两侧 KDoc 互为指路。
 *
 * **夹具缺陷①的根治落点（度量器单次使用 → 断言按键取，R1 §3.2-B）**：本套件**没有测试侧
 * 度量器**——真实度量发生在 `:visual_js` 服务进程内，测试拿不到也不该拿到度量器实例。
 * 断言的键改为**引擎回传的 `executed.contentIds`**（服务度量器按
 * `"c" + index + "-" + digest.take(8)` 生成的确定性事实，`VisualContentMeasuring.kt:69`，
 * digest = payload+环境指纹的内容摘要），对"整链渲染几次"不敏感——core/data 变体里
 * "夹具度量器被两次执行复用致 `singleOrNull()==null`"这一具体失败形状在本套件结构性不存在。
 *
 * **"度量真实发生过"的断言职责转移（如实登记，R1 §3.2-B-3）**：用例 A 的协调器产物只携带
 * `FigureArtifact.Program`（不携带渲染结果），拿不到 contentIds；真实度量的设备级证明由
 * 链腿用例承担：服务度量若缺失/被降桩，`execute` 如实 Rejected（度量不可用即拒绝，不伪造
 * metrics），`contentIds` 不会以服务形状出现、编译 DrawOps 也不会引用它——链腿断言即红。
 * 墨迹级证明（真实字形落纸）仍由 core/data 变体的 painter 腿承担。
 *
 * **零派发见证四断言位**（与 core/data 变体同一定义，任一为真即可单独证伪"JS 占了远程派发"）：
 * ① 审计 operation 行 `reserved_remote_dispatches=0 AND used_remote_dispatches=0`；
 * ② 审计 attempt 行 `method='JS' AND reservation_id IS NULL`；
 * ③ 账本零触碰：预置父操作行 `model_task_operation.dispatch_count` 写前写后不变——
 *    本套件把 `seedParentOperation` 提进链腿共用骨架（夹具缺陷②修复，R1 §3.4），
 *    填掉 core/data 变体"见证③在 C/D 腿无观测对象"的盲区；
 * ④ 产物恒 [FigureArtifact.Program]，绝无 [FigureArtifact.ImageAsset]；叠加
 *    `networkRequestsAllowed=false` 与只抛异常的配置门替身（[UnreachableConfigurationStore]）。
 *
 * **装配原则（沿用隔离先例 `VisualJsIsolationInstrumentedTest`，批次 24/36 设备绿）**：
 * 每测试方法一个引擎（close 是终态闩，一个实例不能跨测试复用）；`connectForTest()` 显式
 * 预热（冷启动 4.6–7.5 s 从被测结果里隔离，后续 execute 复用 CONNECTED link）；`@After`
 * 先 close 引擎（unbind → 服务 onDestroy → killSelf，回收隔离进程）再关库。注入缝是
 * `FigureGenerationCoordinationFactory.create` 既有第 4/5 参（默认 null），生产装配仍三参
 * ——负控用例把"生产 null → UNAVAILABLE"钉在同一套件里。
 *
 * **本轨纪律**：不运行 Gradle/设备命令（门禁由脚本串行执行）；以下判据是设备轨要执行的
 * 断言，不是已通过的结果（先红后绿登记法）。方法名无空格（D8 SimpleName 约束）。
 */
@RunWith(AndroidJUnit4::class)
class V01VisualChainInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()
    private val databases = CopyOnWriteArrayList<StudyDatabasePort>()
    private val databaseNames = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        // 先引擎：close = terminate + unbind + 完成等待点（终态闩）；unbind 后无绑定 →
        // 服务 onDestroy → killSelf，隔离进程回收（引擎 :691-694 注释承诺的路径）。
        engines.forEach { runCatching { it.close() } }
        engines.clear()
        // 后库：先关引擎再关库，避免在飞审计写（best-effort）落在已关闭连接上产生噪音。
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
    }

    // ------------------------------------------------------------ V-01 用例 A：生成腿 + 0 派发四断言位

    @Test
    fun aJsProposalThroughTheCoordinatorRendersWithZeroRemoteDispatchWitnesses() = runBlocking {
        withStore { store ->
            seedParentOperation(store, PARENT_REQUEST_ID)
            val engine = preconnectedEngine()
            // 轻量防退化闸（非隔离判据，隔离判据本体由 VisualJsIsolationInstrumentedTest 承担）：
            // 引擎若被降级成进程内 binder，本套件其余链断言仍可能绿——这里钉住"真隔离进程"
            // 这一结构事实，让退化在这一行先红。
            val workerUid = requireNotNull(engine.workerUidForTest()) {
                "a preconnected engine must have a worker link"
            }
            assertTrue(
                "the visual_js worker must run in the isolated process (uid $workerUid " +
                    "must not equal the host uid ${Process.myUid()})",
                workerUid != Process.myUid(),
            )
            val coordinator = FigureGenerationCoordinationFactory.create(
                database = store,
                configurationStore = UnreachableConfigurationStore(),
                networkRequestsAllowed = false,
                visualEngine = engine,
                visualFrameInputs = VisualFrameInputs { _, _ -> frozenFrame() },
            )

            val generated = coordinator.generate(chainRequest(), jsProposal())

            // 链结论：GENERATED + Program 产物（不是 ImageAsset）。协调器内部经真引擎走完
            // bind/execute/编译全链，任何一段失败都会在这里以 Rejected/FAILED 形状显形。
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
                    "（DefaultFigureGenerationCoordinator.visualProgramId）",
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

            // "度量真实发生过"的断言职责已转移到链腿用例（转移理由见文件 KDoc）：本用例
            // 手里只有 Program 产物，拿不到 contentIds；设备级证明由
            // theFrozenDrawCommandsCommitAndSurviveARealReopenRenderedByTheIsolatedEngine
            // 的 contentIds 形状/label 接线断言承担。
        }
    }

    // ------------------------------------------------------------ 负控：生产装配（不注入引擎）恒 UNAVAILABLE

    /**
     * 生产装配钉子：**生产装配只传三参 → JS 恒 UNAVAILABLE**。若有人把生产装配点接上引擎
     * 而能力快照/广告面未同步，或把"未接通"改成假成功，本负控即红。
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

    // ------------------------------------------------------------ V-01 链腿：真引擎重渲染 + 保存/真实重开

    /**
     * 链腿（core/data 变体用例 C/D 的公共骨架在 app 侧的可达形态）：生成 → **同一 program
     * 在同一真引擎上第二次执行**（同 digest 非熔断）直取 Rendered → 一次事务提交 assistant
     * 正文 + program + LIVE 快照 + link（D-02 契约）→ 真实关闭 + 同名重开 → 读侧行回读。
     *
     * 屏栅格逐位相等 / PDF 完整性 / restore 策略腿依赖 core:data internal painter/restore
     * 符号，不可达（分工见文件 KDoc），留在 core/data 降级变体；本腿钉的是公共面上的
     * 不变量：DrawOps 文本与 contentIds 逐字节穿越"提交→真实重开"，恢复语义的输入不变形。
     */
    @Test
    fun theFrozenDrawCommandsCommitAndSurviveARealReopenRenderedByTheIsolatedEngine() = runBlocking {
        val chain = runChainToCommittedReopen()

        // 夹具缺陷①的根治落点：键 = 引擎回传 contentIds（服务度量器确定性事实），不是
        // 测试夹具的累积记录（见文件 KDoc）。
        assertTrue(
            "the compiled draw ops must wire the label to the real measured content id",
            chain.drawCommandsJson.contains(chain.labelContentId),
        )

        // 重启后行回读：DrawOps 文本逐字节穿越"提交→真实重开"（恢复语义的唯一输入不变形）。
        assertEquals(
            "the draw commands must survive a real reopen byte-identically",
            chain.drawCommandsJson,
            chain.reopenedSnapshot.drawCommandsJson,
        )
        assertEquals(
            "the measured content ids must survive a real reopen",
            chain.executed.contentIds,
            parseContentIds(chain.reopenedSnapshot.contentIdsJson),
        )
        assertEquals(
            "the scene schema version must survive a real reopen",
            chain.executed.sceneSchemaVersion,
            chain.reopenedSnapshot.sceneSchemaVersion,
        )
        assertEquals(
            "the draw schema version must survive a real reopen",
            chain.executed.drawSchemaVersion,
            chain.reopenedSnapshot.renderSchemaVersion,
        )
        // 宿主冻结帧的回读：viewport/theme/timeMs/seed/fontScale 与提交输入逐位一致。
        assertEquals(VIEWPORT.widthDu, chain.reopenedSnapshot.viewportWidthDu)
        assertEquals(VIEWPORT.heightDu, chain.reopenedSnapshot.viewportHeightDu)
        assertEquals(FRAME_THEME, chain.reopenedSnapshot.theme)
        assertEquals((fontScaleOf() * 1_000f).roundToInt(), chain.reopenedSnapshot.fontScaleMilli)
        // 版本溯源列按提交输入回读（renderer 版本镜像 core:data internal 常量
        // VISUAL_RENDERER_IMPLEMENTATION_VERSION 的生产值；跨模块 internal 不可引用）。
        assertEquals(RENDERER_VERSION, chain.reopenedSnapshot.rendererVersion)
        assertEquals(FONT_MANIFEST_VERSION, chain.reopenedSnapshot.fontManifestVersion)
        // link 三元一致：消息 → 当前快照 → program。
        assertEquals(chain.committed.snapshot.snapshotId, chain.reopenedLink.currentSnapshotId)
        assertEquals(chain.committed.snapshot.programId, chain.reopenedLink.programId)
        assertEquals(TutorVisualStatus.LIVE, chain.reopenedSnapshot.status)
    }

    // ------------------------------------------------------------ 链的复用骨架（链腿专用）

    /**
     * 一次完整链路的中间产物（真隔离引擎版）：proposal → coordinator（注入真引擎）→
     * coordinator.generate（引擎第一次执行）→ 测试直取第二次执行 → commit → close →
     * 同名重开 → 读行。见证③的预置与复核在本骨架内完成（夹具缺陷②修复）。
     */
    private suspend fun runChainToCommittedReopen(): ChainArtifacts {
        val databaseName = "v01-chain-${System.nanoTime()}.db"
        databaseNames.add(databaseName)
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        databases.add(store)
        // 见证③补缺口：链腿也预置父操作行，账本零触碰在整条链路窗口内可观测，
        // 不只在用例 A（夹具缺陷②修复，R1 §3.4；消灭"账本被触碰只在用例 A 被观测"的盲区）。
        seedParentOperation(store, PARENT_REQUEST_ID)
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
        val engine = preconnectedEngine()
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

        // coordinator 的 Program 产物不携带 drawOps（generateLocalJs 只回 sourceJs）；
        // 重放腿对同一 program（同 digest，非熔断态）再执行一次直取 Rendered——programId
        // 用产物携带值，不在测试里复算私有公式。成功执行不置熔断（熔断只发生在
        // 宿主阶段超时/硬期限/worker 死亡路径），第二次同源执行照常渲染。
        val rendered = engine.execute(
            VisualExecutionRequest(
                programId = program.programId,
                sourceJs = program.sourceJs,
                sceneRevision = 1, // 协调器 VISUAL_SCENE_REVISION=1（首次执行恒为 1）。
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

        // 夹具缺陷①的根治落点（文件 KDoc"断言按键取"）：键来自引擎回传事实。
        val labelContentId = requirePresent(
            executed.contentIds.singleOrNull(),
            "the prepare batch must measure exactly one content entry",
        )
        assertTrue(
            "the content id must be the service measurer's deterministic shape " +
                "(c<index>-<digest8>, digest over payload+environment), got $labelContentId",
            SERVICE_CONTENT_ID_PATTERN.matches(labelContentId),
        )

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
                rendererVersion = RENDERER_VERSION,
                fontManifestVersion = FONT_MANIFEST_VERSION,
                questionVerificationStatus = "NOT_CHECKED",
            ),
            ownerRequestId = OWNER_REQUEST_ID,
        )
        assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)

        // 重启构造：真实关闭 + 同名重开（仓内惯例）→ 读侧既有口
        // （TutorVisualPort.readTutorMessageVisual/readTutorVisualSnapshot）。
        store.close()
        databases.remove(store)
        val reopened = StudyDatabaseFactory.open(context, databaseName)
        databases.add(reopened)
        val reopenedLink = requirePresent(
            reopened.readTutorMessageVisual(MESSAGE_ID, VISUAL_ORDINAL),
            "the visual link must survive a real reopen",
        )
        val reopenedSnapshot = requirePresent(
            reopened.readTutorVisualSnapshot(reopenedLink.currentSnapshotId),
            "the snapshot row must survive a real reopen",
        )
        assertEquals(TutorVisualStatus.LIVE, reopenedSnapshot.status)

        // 断言位 ③（链腿收尾）：整条链（生成 → 重渲染 → 提交 → 重开）之后账本仍是零触碰。
        assertEquals(
            "the JS path must never touch the durable dispatch ledger across the whole chain",
            0,
            readParentDispatchCount(PARENT_REQUEST_ID),
        )

        return ChainArtifacts(
            executed = executed,
            drawCommandsJson = drawCommandsJson,
            labelContentId = labelContentId,
            committed = committed,
            reopenedLink = reopenedLink,
            reopenedSnapshot = reopenedSnapshot,
        )
    }

    private inner class ChainArtifacts(
        val executed: VisualExecutionResult.Rendered,
        val drawCommandsJson: String,
        /** 引擎回传的唯一 contentId（服务进程内真实度量的确定性键，夹具缺陷①的修复落点）。 */
        val labelContentId: String,
        val committed: TutorVisualTurnCommitRecord,
        val reopenedLink: com.tingyun.smartmistakebook.core.database.TutorMessageVisualRecord,
        val reopenedSnapshot: com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord,
    )

    // ------------------------------------------------------------ 夹具

    /**
     * 每测试方法一个引擎 + 显式预热（隔离先例装配，文件 KDoc）：
     * close 是终态闩（`closed.compareAndSet`），一个实例不能跨测试复用——跨测试共享会把
     * fusedDigests/重建预算/连接计数状态泄漏进下一条用例，破坏断言位①②④的独立性。
     * 预热把冷启动 attach（设备实测 4.6–7.5 s）从被测结果里隔离出来：失败归因只剩链本身。
     */
    private fun isolatedEngine(): VisualJsClientEngine =
        VisualJsClientEngine(context).also { engines.add(it) }

    private suspend fun preconnectedEngine(): VisualJsClientEngine =
        isolatedEngine().also { engine ->
            assertTrue("initial connect failed", engine.connectForTest())
        }

    /**
     * 配置门 tripwire：JS 链不读凭证（凭证门在 `networkRequestsAllowed=false` 时先短路）；
     * 任何让 JS 路径触达配置/凭证的改动都会在这里炸出 UnsupportedOperationException，
     * 而不是静默通过（断言位 ④ 的组成）。
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
     * `reserve` 若被误调进 JS 路径就会真的递增 `dispatch_count`，断言位 ③ 即红。
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

    private fun fontScaleOf(): Float =
        context.resources.configuration.fontScale.let { if (it <= 0f) 1f else it }

    /** `TutorVisualSnapshotInput.contentIds` 的字典序 JSON 数组回读（与仓储编码同形）。 */
    private fun parseContentIds(json: String): Set<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }

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

        /**
         * 服务度量器 contentId 的确定性形状：`"c" + index + "-" + digest.take(8)`
         * （`VisualContentMeasuring.kt:69`；digest 为 `figureSha256Hex` 的小写 hex，:617）。
         * 本程序的 prepare 恰回 1 条 text → index 恒 0。断言按键取的键形校验（夹具缺陷①）。
         */
        val SERVICE_CONTENT_ID_PATTERN = Regex("c0-[0-9a-f]{8}")

        /** 生产 VERIFIED_FONT_MANIFESTS 为空集；测试提交的已登记清单版本（与 core/data 变体同值）。 */
        const val FONT_MANIFEST_VERSION = "font-manifest-v1"

        /**
         * 渲染器实现版本的测试侧镜像（与 core:data internal 常量
         * `VISUAL_RENDERER_IMPLEMENTATION_VERSION` 的生产值一致，`VisualRendererVersion.kt:20`；
         * 跨模块 internal 不可引用，故此处镜像字面量——core/data 侧设备套件仍钉真实常量）。
         */
        const val RENDERER_VERSION = "visual-renderer-v1"
    }
}
