package com.tingyun.smartmistakebook.visualjs

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.model.FigurePromptAssembler
import com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameInputs
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameSnapshot
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * **A-09 广告-执行一致性·注入式设备用例（批次 38 计划任务，清单 A-09 建议名）**：
 * 能力快照广告了 JS（含 JS）→ GENERATE_FIGURE 的 JS 调用**真的在本机执行成功**
 * （引擎 Rendered → coordinator GENERATED），而不是 UNAVAILABLE；全程 **0 远程派发**，
 * 四个独立断言位钉住（与 V-01 两变体同一定义，任一为真即可单独证伪"JS 占了远程派发"）：
 * ① 审计 operation 行 `reserved_remote_dispatches=0 AND used_remote_dispatches=0` 且 SUCCEEDED；
 * ② 审计 attempt 行 `method='JS' AND reservation_id IS NULL`（恰一行、指纹为 JS 形状）；
 * ③ 账本零触碰：预置父操作行 `model_task_operation.dispatch_count` 写前写后不变；
 * ④ 产物恒 [FigureArtifact.Program]，绝无 [FigureArtifact.ImageAsset]；叠加
 * `networkRequestsAllowed=false` 与只抛异常的配置门替身（[UnreachableConfigurationStore]）。
 *
 * **为什么是"注入式"（不接生产广告开关）**：生产广告面本批保持现状逐位不动——
 * `RoomModelTaskRepository.toolDeclarationsFor` 的 JS 双就绪位显式 `false/false`（A-05，
 * 接线归 L3 装配批）、`OpenAiModelProtocol.figureMethods` 默认只广告 IMAGE_MODEL、工具环
 * 轮次层/执行器层对 JS fail-closed（`RoomModelTaskToolRounds.kt:58`、
 * `RoomTutorToolRunner.kt:1105`）、app 装配三参建 coordinator（`SmartMistakeBookApplication.kt:364`，
 * 无引擎 → JS 恒 UNAVAILABLE）。本套件**不碰以上任何一处**：快照在测试装配里注入——
 * 请求的 `capabilities` 用"生产默认 `IMAGE_MODEL_ONLY` + JS"（JS 接线后生产会广告的形状，
 * 两个方法同时在广告面，远程路径处于 armed 状态），prompt 用
 * [FigurePromptAssembler.assemble] 按同一快照组装（广告正文随快照含 JS 契约段），
 * 引擎与帧输入经 [FigureGenerationCoordinationFactory.create] 既有第 4/5 参注入
 * （默认 null，生产装配一字不动——负控用例把"生产三参 → UNAVAILABLE"钉在同一套件里）。
 *
 * **与 V-01 app 套件（`V01VisualChainInstrumentedTest`）的分工**：V-01 用例 A 的请求快照
 * 只含 `{JS}`——"无远程方法可派发"的前提下证 0 派发。本套件的注入面是 **IMAGE_MODEL + JS
 * 同时在广告面**：这正是 A-09 的风险所在（A-02 轮次层门的注释原文：JS 调用若漏进图像
 * 生成器就是"一次真实的远程付费派发 + 一条 method=IMAGE_MODEL 的假痕迹"）——广告面里有
 * IMAGE_MODEL、账本已预置、预算允许派发时，JS 调用必须仍走本地并保持 0 远程派发。
 * 装配同型（同包、同工厂缝、同引擎预热/收尾顺序、同审计读法、同账本预置手法）。
 *
 * **"Rendered" 的设备级证据**：coordinator 的 GENERATED 只在引擎回传
 * [VisualExecutionResult.Rendered] 时产生（`DefaultFigureGenerationCoordinator.kt:372-387`，
 * Rejected 一律折 FAILED/UNAVAILABLE）；本套件在四断言位之后再对同一 program 直取一次引擎
 * 执行，钉住 [VisualExecutionResult.Rendered] 本体与服务度量器的确定性 contentId 形状
 * （`"c" + index + "-" + digest.take(8)`，`VisualContentMeasuring.kt:81-82`，本批实读；
 * V-01 KDoc 里的 `:69` 引用已被 M-01 的布局 artifact 改动漂移）——"成功"不是 coordinator
 * 状态的自说自话。直取执行不经 coordinator、不写审计（用例内复钉 attempt 恰一行）。
 *
 * **负控的如实登记（UNAVAILABLE 与 REJECTED 的归属）**：
 * - **生产装配负控 → UNAVAILABLE**：生产三参 create（不注入引擎）+ 广告了 JS 的请求 →
 *   `generateLocalJs` 无引擎折 UNAVAILABLE（`DefaultFigureGenerationCoordinator.kt:275-280`），
 *   与 V-01 两变体的负控同型（"生产装配只传三参 → JS 恒 UNAVAILABLE"的钉子在本套件自持）。
 * - **默认快照负控 → REJECTED（不是 UNAVAILABLE）**：能力快照保持生产默认
 *   [FigureGenerationCapabilities.IMAGE_MODEL_ONLY]（JS 不在广告面）时，JS proposal 在
 *   policy 门被拒（`FigureGenerationPolicy.kt:135-140`，METHOD_NOT_AVAILABLE，
 *   "本机未接通 JS 方法，不得假装可用"），coordinator 把 policy 拒绝映为
 *   [FigureGenerationStatus.REJECTED]（`DefaultFigureGenerationCoordinator.kt:176-184`）。
 *   即"默认 IMAGE_MODEL_ONLY"的 coordinator 级归宿是 REJECTED，UNAVAILABLE 属于上面那条
 *   生产装配腿——本套件两条负控各自断言其真实机制，不把 REJECTED 改写成 UNAVAILABLE。
 *   （生产链路上 JS 调用的实际挡点是工具环 fail-closed 门与 IMAGE 入口预检，属 core:data
 *   主源与本批热区，本套件不触碰、不模拟。）
 *
 * **装配原则（沿用隔离先例与 V-01 app 套件）**：每测试方法一个引擎（close 是终态闩，一个
 * 实例不能跨测试复用）；`connectForTest()` 显式预热，把冷启动 attach（设备实测 4.6–7.5 s）
 * 从被测结果里隔离；`@After` 先 close 引擎（unbind → 服务 onDestroy → killSelf 回收隔离
 * 进程）再关库、删库。测试库一律 `a09-ads-*.db` 独立命名，不触生产应用数据库
 * （无需 `DestructiveDeviceTestGuard`）。
 *
 * **本轨纪律**：不运行 Gradle/设备命令（门禁由脚本串行执行）；以下判据是设备轨要执行的
 * 断言，不是已通过的结果（先红后绿登记法）。方法名无空格（D8 SimpleName 约束）。
 * 本套件为**新增文件**，未修改任何生产源或既有测试。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsAdvertiseThenExecuteInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()
    private val databases = CopyOnWriteArrayList<StudyDatabasePort>()
    private val databaseNames = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        // 先引擎：close = terminate + unbind + 完成等待点（终态闩）；unbind 后无绑定 →
        // 服务 onDestroy → killSelf，隔离进程回收（与 V-01 app 套件同一收尾顺序）。
        engines.forEach { runCatching { it.close() } }
        engines.clear()
        // 后库：先关引擎再关库，避免在飞审计写（best-effort）落在已关闭连接上产生噪音。
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
    }

    // ---------------- 正例：注入含 JS 的能力快照 → 本机执行成功（Rendered）+ 0 远程派发四断言位

    @Test
    fun anAdvertisedJsSnapshotRendersThroughTheCoordinatorWithZeroRemoteDispatchWitnesses() =
        runBlocking {
            withStore { store ->
                seedParentOperation(store, PARENT_REQUEST_ID)
                val engine = preconnectedEngine()
                // 轻量防退化闸（同 V-01 用例 A）：引擎若被降级成进程内 binder，下面的
                // Rendered/0 派发断言仍可能绿——这里先钉住"真隔离进程"这一结构事实。
                val workerUid = requireNotNull(engine.workerUidForTest()) {
                    "a preconnected engine must have a worker link"
                }
                assertTrue(
                    "the visual_js worker must run in the isolated process (uid $workerUid " +
                        "must not equal the host uid ${android.os.Process.myUid()})",
                    workerUid != android.os.Process.myUid(),
                )
                val frame = frozenFrame()
                val coordinator = FigureGenerationCoordinationFactory.create(
                    database = store,
                    configurationStore = UnreachableConfigurationStore(),
                    networkRequestsAllowed = false,
                    visualEngine = engine,
                    visualFrameInputs = VisualFrameInputs { _, _ -> frame },
                )

                // 广告面注入：请求与判定上下文用同一份"生产默认 + JS"的快照组装。
                val request = advertisedRequest()

                val generated = coordinator.generate(request, jsProposal())

                // 广告侧证据：组装出的判定上下文真的把 JS 广告给了模型（结构位 + 正文两处），
                // 预算段声明"结构化程序在本机执行，不占远程派发"——下面的四断言位兑现它。
                val prompt = requirePresent(request.prompt, "the request must carry the assembled prompt")
                assertEquals(
                    "the injected snapshot must advertise both methods in the prompt face",
                    setOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL),
                    prompt.availableMethods.toSet(),
                )
                assertTrue(
                    "the prompt body must carry the JS selection instruction",
                    prompt.body.contains("选择 JS 就返回 method=JS 与独立 program 字段"),
                )
                assertTrue(
                    "the prompt body must declare that local programs do not consume remote dispatches",
                    prompt.body.contains("结构化程序在本机执行，不占远程派发"),
                )

                // 链结论：GENERATED + Program 产物。coordinator 只在引擎 Rendered 时给
                // GENERATED；任何一段失败都会以 REJECTED/FAILED/UNAVAILABLE 形状在这里显形。
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
                assertEquals(
                    "reserved_remote_dispatches",
                    0,
                    operation.int("reserved_remote_dispatches"),
                )
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
                assertNull(
                    "local attempt must not carry a reservation",
                    attempt.stringOrNull("reservation_id"),
                )
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

                // Rendered 的设备级证据：同一 program（同 digest，非熔断态）直取引擎再执行
                // 一次，钉住 Rendered 本体与服务度量器的确定性 contentId 形状。成功执行不置
                // 熔断（熔断只发生在宿主阶段超时/硬期限/worker 死亡路径），第二次同源执行照常
                // 渲染（V-01 链腿同一手法）。该执行不经 coordinator、不写审计——用例末尾复钉。
                val rendered = engine.execute(
                    VisualExecutionRequest(
                        programId = program.programId,
                        sourceJs = program.sourceJs,
                        sceneRevision = 1, // coordinator VISUAL_SCENE_REVISION=1（首次执行恒为 1）。
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
                val labelContentId = requirePresent(
                    executed.contentIds.singleOrNull(),
                    "the prepare batch must measure exactly one content entry",
                )
                assertTrue(
                    "the compiled draw ops must wire the label to the real measured content id",
                    drawCommandsJson.contains(labelContentId),
                )
                assertTrue(
                    "the content id must be the service measurer's deterministic shape " +
                        "(c<index>-<digest8>), got $labelContentId",
                    SERVICE_CONTENT_ID_PATTERN.matches(labelContentId),
                )

                // 直取执行不写审计：attempt 仍恰一行（Rendered 证据没有污染审计观察窗）。
                assertEquals(
                    "the engine-level re-execution must not append audit rows",
                    1,
                    readAllRows(
                        "SELECT attempt_id FROM figure_generation_attempt WHERE operation_id = ?",
                        OPERATION_ID,
                    ).size,
                )

                // 断言位 ③（窗口收尾）：整窗（生成 + 直取重渲染）之后账本仍是零触碰。
                // 广告面里有 IMAGE_MODEL、父操作已预置、预算允许派发——账本仍一分未动。
                assertEquals(
                    "the JS path must never touch the durable dispatch ledger even with " +
                        "IMAGE_MODEL advertised in the same snapshot",
                    0,
                    readParentDispatchCount(PARENT_REQUEST_ID),
                )
            }
        }

    // ---------------- 负控一：生产装配（不注入引擎）恒 UNAVAILABLE

    /**
     * 生产装配钉子（与 V-01 两变体负控同型，本套件自持）：**生产装配只传三参 → JS 恒
     * UNAVAILABLE**。若有人把生产装配点接上引擎而能力快照/广告面未同步，或把"未接通"改成
     * 假成功，本负控即红。请求侧同样用"广告了 JS"的快照——即"开关翻动而装配未跟上"的最坏
     * 形状下，未接通仍然如实回 UNAVAILABLE，不假成功。
     */
    @Test
    fun theProductionAssemblyWithoutAnInjectedEngineKeepsTheAdvertisedJsUnavailable() = runBlocking {
        withStore { store ->
            val coordinator = FigureGenerationCoordinationFactory.create(
                database = store,
                configurationStore = UnreachableConfigurationStore(),
                networkRequestsAllowed = false,
            )

            val outcome = coordinator.generate(advertisedRequest(), jsProposal())

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

    // ---------------- 负控二：生产默认快照 IMAGE_MODEL_ONLY → JS proposal 不执行

    /**
     * 默认快照钉子：能力快照保持**未改动的生产默认** [FigureGenerationCapabilities.IMAGE_MODEL_ONLY]
     * （JS 不在广告面）时，JS proposal 在 policy 门被拒——coordinator 映为
     * [FigureGenerationStatus.REJECTED]（如实登记：不是 UNAVAILABLE，机制归属见文件 KDoc）。
     * 引擎照常注入并预热：证明拒的是**快照门**而不是缺硬件——只要广告面没开 JS，哪怕执行
     * 能力齐备，JS proposal 也不得执行（"广告一个必然不可用的工具"与"未广告却执行"同罪）。
     * 不建 attempt、不碰账本、审计无行。
     */
    @Test
    fun theDefaultImageModelOnlySnapshotRefusesTheJsProposalAtPolicyWithoutExecuting() = runBlocking {
        withStore { store ->
            seedParentOperation(store, PARENT_REQUEST_ID)
            val engine = preconnectedEngine()
            val coordinator = FigureGenerationCoordinationFactory.create(
                database = store,
                configurationStore = UnreachableConfigurationStore(),
                networkRequestsAllowed = false,
                visualEngine = engine,
                visualFrameInputs = VisualFrameInputs { _, _ -> frozenFrame() },
            )

            val outcome = coordinator.generate(defaultSnapshotRequest(), jsProposal())

            assertEquals(
                "actual: status=${outcome.status} diagnostics=${outcome.diagnostics}",
                FigureGenerationStatus.REJECTED,
                outcome.status,
            )
            assertEquals(
                "the policy gate must name the un-advertised method as the refusal reason",
                "本机未接通 JS 方法，不得假装可用",
                outcome.diagnostics,
            )
            assertNull("a refused generation carries no artifact", outcome.artifact)
            assertNull("no attempt was started", outcome.attemptId)
            // REJECTED 发生在审计状态创建之前：无 operation 行、无 attempt 行。
            store.readDatabaseVersion()
            assertEquals(
                0,
                readAllRows(
                    "SELECT operation_id FROM figure_generation_operation WHERE operation_id = ?",
                    OPERATION_ID,
                ).size,
            )
            assertEquals(
                0,
                readAllRows(
                    "SELECT attempt_id FROM figure_generation_attempt WHERE operation_id = ?",
                    OPERATION_ID,
                ).size,
            )
            // 账本零触碰：被拒的调用不预留、不付费。
            assertEquals(
                "a refused JS proposal must never touch the durable dispatch ledger",
                0,
                readParentDispatchCount(PARENT_REQUEST_ID),
            )
        }
    }

    // ---------------- 夹具（与 V-01 app 套件同型，独立自持）

    /**
     * 每测试方法一个引擎 + 显式预热（隔离先例装配）：close 是终态闩，一个实例不能跨测试
     * 复用；预热把冷启动 attach 从被测结果里隔离出来，失败归因只剩链本身。
     */
    private fun isolatedEngine(): VisualJsClientEngine =
        VisualJsClientEngine(context).also { engines.add(it) }

    private suspend fun preconnectedEngine(): VisualJsClientEngine =
        isolatedEngine().also { engine ->
            assertTrue("initial connect failed", engine.connectForTest())
        }

    /**
     * 配置门 tripwire（断言位 ④ 的组成）：JS 链不读凭证（凭证门在 `networkRequestsAllowed=false`
     * 时先短路）；任何让 JS 路径触达配置/凭证的改动（含把 JS 调用误路由进图像后端）都会在
     * 这里炸出 UnsupportedOperationException，而不是静默通过。
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

    /** tool proposal 腿：经 [TutorToolCall] 的 JS 工具调用形状构造（V-01/A-02 同形）。 */
    private fun jsProposal(): FigureGenerationProposal =
        TutorToolCall(
            tool = TutorToolName.GENERATE_FIGURE,
            rationale = "为这道题生成一张讲解图",
            figureKind = TutorFigureKind.GENERATE_PROCESS,
            figureDescription = "一张带标签的圆与说明文字",
            figureMethod = FigureGenerationMethod.JS,
            figureProgram = PROGRAM_JS,
        ).toFigureGenerationProposal() ?: error("a GENERATE_FIGURE JS call must produce a proposal")

    /**
     * 注入的广告面：生产默认 [FigureGenerationCapabilities.IMAGE_MODEL_ONLY] 原样保留图像
     * 能力位，`availableMethods` 并入 JS——JS 接线后生产会广告的形状（IMAGE_MODEL 与 JS 同在
     * 广告面，远程路径处于 armed 状态）。构造走 `copy`，让"默认 + JS"的派生关系可见。
     */
    private val snapshotAdvertisingJs: FigureGenerationCapabilities =
        FigureGenerationCapabilities.IMAGE_MODEL_ONLY.copy(
            availableMethods =
            FigureGenerationCapabilities.IMAGE_MODEL_ONLY.availableMethods +
                FigureGenerationMethod.JS,
        )

    /** 广告了 JS 的请求：prompt 用同一份快照组装（判定上下文与 capabilities 同源）。 */
    private fun advertisedRequest(): FigureGenerationRequest =
        FigureGenerationRequest(
            operationId = OPERATION_ID,
            purpose = EXPLANATION_PURPOSE,
            target = FigureGenerationTarget(),
            capabilities = snapshotAdvertisingJs,
            budget = FigureGenerationBudget(),
            dispatchParent = FigureDispatchParent.ModelTask(
                requestId = PARENT_REQUEST_ID,
                reserveChatFinalAnswer = true,
            ),
            source = null,
            constraints = emptyList(),
            prompt = FigurePromptAssembler.assemble(
                EXPLANATION_PURPOSE,
                snapshotAdvertisingJs,
                FigureGenerationBudget(),
            ),
            sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
            sourceId = MESSAGE_ID,
            basisFingerprint = null, // JS 形状由 coordinator 按 program 自行推导（A-08）。
            explicitUserRetry = false,
        )

    /** 生产默认快照的请求：IMAGE_MODEL_ONLY 原样（JS 不在广告面），prompt 同源组装。 */
    private fun defaultSnapshotRequest(): FigureGenerationRequest {
        val snapshot = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
        return FigureGenerationRequest(
            operationId = OPERATION_ID,
            purpose = EXPLANATION_PURPOSE,
            target = FigureGenerationTarget(),
            capabilities = snapshot,
            budget = FigureGenerationBudget(),
            dispatchParent = FigureDispatchParent.ModelTask(
                requestId = PARENT_REQUEST_ID,
                reserveChatFinalAnswer = true,
            ),
            source = null,
            constraints = emptyList(),
            prompt = FigurePromptAssembler.assemble(
                EXPLANATION_PURPOSE,
                snapshot,
                FigureGenerationBudget(),
            ),
            sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
            sourceId = MESSAGE_ID,
            basisFingerprint = null,
            explicitUserRetry = false,
        )
    }

    /**
     * 预置账本父操作（断言位 ③ 的观测对象）：`model_task_operation` 行 + 引用它的
     * `model_task` 行（FK RESTRICT），沿用审计夹具的库文件直写手法（V-01 同型：
     * Room 建好 schema 后第二条连接写行）。`reserve` 若被误调进 JS 路径就会真的递增
     * `dispatch_count`，断言位 ③ 即红。
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
                    "task_id" to "task-a09-ads",
                    "request_id" to requestId,
                    "request_fingerprint" to "request-fingerprint-a09",
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

    /** 与审计夹具同一手法（第二条 SQLite 连接），只读查询。 */
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

    /** 仓内惯例的建库/清理（V-01 app 套件 withStore 同型；库文件独立命名，不触生产库）。 */
    private suspend fun withStore(block: suspend (StudyDatabasePort) -> Unit) {
        val databaseName = "a09-ads-${System.nanoTime()}.db"
        databaseNames.add(databaseName)
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        databases.add(store)
        block(store)
    }

    private fun <T> requirePresent(value: T?, message: String): T = value ?: error(message)

    private companion object {
        /** 讲解场景：一组（平移 +5,+5、opacity 0.5）内含一个圆路径与一个真实度量驱动的 label。 */
        val PROGRAM_JS = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '$LABEL_PAYLOAD' }]; },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-a09-ads', revision: input.revision,
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
        const val OPERATION_ID = "operation-a09-ads"
        const val PARENT_REQUEST_ID = "request-a09-ads-parent"
        const val PARENT_OPERATION_FINGERPRINT = "fingerprint-a09-ads-parent"
        const val SUBJECT_ID = "subject-a09-ads"
        const val TASK_KIND = "TUTOR"
        const val MESSAGE_ID = "message-a09-ads"
        val EXPLANATION_PURPOSE = FigureGenerationPurpose.EXPLANATION

        /**
         * 服务度量器 contentId 的确定性形状：`"c" + index + "-" + digest.take(8)`
         * （`VisualContentMeasuring.kt:81-82`，前缀与位数常量在同文件 `:114-115`）。
         * 本程序的 prepare 恰回 1 条 text → index 恒 0。
         */
        val SERVICE_CONTENT_ID_PATTERN = Regex("c0-[0-9a-f]{8}")
    }
}
