package com.tingyun.smartmistakebook.core.data.visual

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureEntry
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * [VisualExecutionEngine] 的真实引擎实现（批次 21 T3，主会话裁定 2）：quickjs-kt-android
 * 1.0.15 发布 AAR（钉版，sha256 见 `gradle/libs.versions.toml`）上的契约 §3.2/§4 承担者。
 * API 面全部读自钉 commit `a081755bcdb18ee6afa7ffee25b8fa976f06d31f` 上游源码（QuickJs.kt、
 * QuickJs.jni.kt、Bindings.dsl.kt、castValueOr.jvm.kt），非训练记忆。
 *
 * **落位偏差（对照契约 §5，登记报告）**：契约写"原生执行编排由 app 的 AndroidVisualExecutionEngine
 * 实现……core:data 只通过该端口做来源/幂等/持久化/CAS，不直接引用 AIDL/原生排版"。本实现落在
 * core:data（app/src/main 是他线在飞热区，新增文件会混入他线收口），**没有**引用 AIDL 或任何
 * 原生排版类型——契约给 core:data 划的两条红线（AIDL/原生排版）仍然成立；偏差仅是"编排实现在
 * 哪个模块"。4E.2 把 JS 编排搬进 `:visual_js` 隔离进程 + core:visual-js 客户端时，本类整体迁移，
 * 端口不变。
 *
 * 事务编排（契约 §3.2 逐步）：
 * 1. prepare 上下文：新建 QuickJs 实例 → bootstrap（封闭 Date、注入种子 PRNG、冻结 Math）→
 *    求值用户源码 → `visual.prepare(input)` → JSON.stringify 取回批量内容度量请求 → close。
 * 2. 原生度量：经 [VisualContentMeasurer] 恰一批。
 * 3. render 上下文：**新建** QuickJs 实例重走 bootstrap + 同一源码 →
 *    `visual.render(input, metrics)` → JSON.stringify 取回场景 JSON → close。
 * 4. 输出校验：字节预算 + 引用冻结检查（扫描场景 JSON 的 `contentId` 值，拒绝本事务未冻结的
 *    引用）。完整 SceneV1 严格校验归 4E.2 共享 compiler，这里只做可观测的保守面。
 *
 * 每事务新上下文：prepare/render 各用全新实例（脚本全局不跨事务、也不跨阶段复用）——
 * 契约 §3.2"程序不能依赖跨调用全局变量"由结构保证，不靠脚本自觉。
 *
 * **终止层数（任务三：三层落两层；对 issue #240 只有协作层对普通循环有效）**：
 * - 协作层：`evaluationTimeoutMillis`（每个 root evaluate 各一次；QuickJS 原生 interrupt handler
 *   在 JS 执行中采样）+ 宿主协程取消（上游把 cancel 钩到同一原生中断）。到点抛
 *   [QuickJsInterruptedException] → 映射 [VisualExecutionRejection.PHASE_TIMEOUT]，不熔断。
 * - 宿主硬期限层：独立 watchdog 线程在服务硬期限（1500ms）到点后置 `hardExpired`，并把该事务
 *   活引擎实例的 `close()` 交给专用守护线程强弃（close 会先 requestInterrupt 再抢 jsMutex）。
 *   **这一层不保证调用方上界**：`withTimeout(hard + 500ms)` 包住的 `runPhase` 在
 *   `withContext(jsDispatcher)` 内执行同步 JNI；协程取消不能抢占该阻塞块，`withTimeout` 会等到
 *   块结束才恢复。issue #240 下 evaluate 与 close 都被 jsMutex 卡死，`execute()` **永不返回**，
 *   [HARD_DEADLINE_EXCEEDED] 分支与 `finally` 都执行不到；`inFlight` 不复位，后续 `execute()`
 *   只回 [VisualExecutionRejection.BUSY]，程序也不会进 fused 集合。**因此 T3 是部分/阻塞状态，
 *   不能宣称"hard+500ms 必返回"或"引擎本身继续可用"。**
 * - **未做（登记为 4E.2 前置）**：契约 §4 的第三层——`android:isolatedProcess="true"` 的
 *   `:visual_js` 进程、AIDL 控制通道与 `killProcess(myPid())` 硬终止。契约原文："独立 watchdog
 *   线程在每个 JS 阶段开始前启动；硬超时由服务自己 killProcess(myPid())"。进程级硬终止是唯一能
 *   终止任何循环的层；本实现的两层对"自续 job 队列"（上游 issue 240）只能限爆不能终止。
 *
 * **上游已知缺陷 issue #240 的接受策略**（https://github.com/dokar3/quickjs-kt/issues/240，
 * OPEN/零评论/无修复 PR @ 1.0.15）：一个 job 先 `Promise.resolve().then(f)` 排队下一个再自旋时，
 * `evaluate()` 永不返回（排水循环不查中断且全程持 jsMutex），`close()` 也随之卡死。本实现的
 * 爆炸半径控制：每事务专用单线程 dispatcher（实例随事务销毁）、强弃 close 放专用守护线程
 * （绝不占 watchdog 线程）。#240 的最坏后果 = 泄漏一条 daemon 线程 + 一份未释放原生 runtime
 * （accept-the-leak，R1 §2.3.4）+ **该引擎实例永久 BUSY**（`inFlight` 无法复位）。仪器化用例
 * （QuickJsVisualExecutionEngineInstrumentedTest）按 R1 §2.4 的观察形状（daemon 线程 + join
 * 超时，不直跑断言）覆盖该场景；观察到的"不返回"是已知缺陷，不是终止成功。
 *
 * 错误面（契约 §4"超限不截断成正确图，返回有界诊断"）：一切失败以
 * [VisualExecutionResult.Rejected] 返回，不抛库异常穿透端口。JS 异常分类按消息启发式：
 * "out of memory" → [MEMORY_LIMIT]（QuickJS 原生 OOM 消息）；"not defined" →
 * [SANDBOX_VIOLATION]（ReferenceError：沙箱无 IO/网络/主机对象）；其余 → [INVALID_PROGRAM]。
 * 程序故意抛同文案错误可被误分类（仍是有界拒绝，无截断面），登记为已知边界。
 *
 * `close()` 幂等；关闭后 `execute()` 立即回 [ENGINE_CLOSED]。close 不等待引擎锁（可能被
 * #240 卡住的强弃走专用守护线程），可在任意线程调用。
 */
class QuickJsVisualExecutionEngine(
    private val measurer: VisualContentMeasurer,
    /** quickjs-kt 的 job dispatcher（只承载异步绑定 job；本引擎不定义 async 绑定，近乎闲置）。 */
    private val jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 单 JS 阶段协作期限（契约 §4：100ms；只计 JS 执行时间）。 */
    private val cooperativeTimeoutMillis: Long =
        VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS,
    /** 服务硬期限（契约 §4：1500ms；本实现的 watchdog 强弃层）。 */
    private val hardDeadlineMillis: Long = VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS,
) : VisualExecutionEngine {

    private val closed = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val fusedProgramDigests: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())
    private val currentTransaction = AtomicReference<Transaction?>(null)

    private val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "quickjs-visual-watchdog").apply { isDaemon = true }
    }

    override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
        if (closed.get()) {
            return rejected(VisualExecutionRejection.ENGINE_CLOSED, "engine is closed")
        }
        val sourceBytes = figureUsageUtf8Bytes(request.sourceJs)
        if (sourceBytes > VisualExecutionBudget.MAX_SOURCE_JS_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "sourceJs is $sourceBytes bytes, over ${VisualExecutionBudget.MAX_SOURCE_JS_BYTES}",
            )
        }
        val stateBytes = request.stateJson?.let { figureUsageUtf8Bytes(it) } ?: 0
        if (stateBytes > VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "state is $stateBytes bytes, over ${VisualExecutionBudget.MAX_STATE_JSON_BYTES}",
            )
        }
        val digest = figureSha256Hex(request.sourceJs.toByteArray(StandardCharsets.UTF_8))
        if (!request.explicitUserRetry && digest in fusedProgramDigests) {
            return rejected(
                VisualExecutionRejection.PROGRAM_FUSED,
                "program digest ${digest.take(12)} is fused until explicit user retry",
            )
        }
        if (closed.get()) {
            return rejected(VisualExecutionRejection.ENGINE_CLOSED, "engine is closed")
        }
        if (!inFlight.compareAndSet(false, true)) {
            return rejected(
                VisualExecutionRejection.BUSY,
                "another visual transaction is already in flight",
            )
        }
        val transaction = Transaction(request, digest)
        currentTransaction.set(transaction)
        val hardTimer = watchdog.schedule(
            { transaction.onHardDeadline() },
            hardDeadlineMillis,
            TimeUnit.MILLISECONDS,
        )
        try {
            // 这个 withTimeout 只对可协作的挂起有效：issue #240 的同步 JNI 阻塞不可抢占，
            // 此时本调用永不返回，下面的 catch/finally 都不执行（见类 KDoc）。真正的调用方
            // 上界必须由调用方自己的线程/进程边界承担（R1 §2.4 观察形状）。
            val result = withTimeout(hardDeadlineMillis + HOST_AWAIT_GRACE_MILLIS) {
                runTransaction(transaction)
            }
            if (result is VisualExecutionResult.Rejected &&
                result.reason == VisualExecutionRejection.HARD_DEADLINE_EXCEEDED
            ) {
                fusedProgramDigests.add(digest)
            }
            return result
        } catch (timeout: TimeoutCancellationException) {
            // 可协作挂起的硬上界到点（#240 的同步 JNI 阻塞到不了这里，见类 KDoc）——强弃、
            // 熔断、放弃该事务的执行线程（daemon，随进程回收）。
            transaction.forceAbandon()
            fusedProgramDigests.add(digest)
            return rejected(
                VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                "transaction did not finish within the host hard deadline " +
                    "(${hardDeadlineMillis} ms + ${HOST_AWAIT_GRACE_MILLIS} ms grace); " +
                    "the program is fused and its worker abandoned",
            )
        } catch (cancel: CancellationException) {
            transaction.forceAbandon()
            throw cancel
        } finally {
            hardTimer.cancel(false)
            currentTransaction.compareAndSet(transaction, null)
            inFlight.set(false)
        }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        currentTransaction.get()?.forceAbandon()
        watchdog.shutdownNow()
    }

    // ---------------------------------------------------------------- 事务

    private suspend fun runTransaction(transaction: Transaction): VisualExecutionResult {
        val request = transaction.request
        // 每事务专用单线程 dispatcher：JS 同步执行发生在 evaluate() 调用者线程（上游
        // QuickJs.jni.kt evalAndAwait），这里把两个阶段都钉到本事务自己的线程上。issue #240
        // 卡死时泄漏的正是这条 daemon 线程，下一事务拿全新线程，引擎不连坐。
        val jsExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "quickjs-visual-js").apply { isDaemon = true }
        }
        val jsDispatcher = jsExecutor.asCoroutineDispatcher()
        try {
            if (transaction.hardExpired) {
                return rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "transaction expired before it started",
                )
            }
            val inputJson = try {
                buildFrameInput(request)
            } catch (error: SerializationException) {
                // stateJson 声明为 StateV1 JSON；非 JSON 无法内嵌为 FrameInput 字面量。
                // 归 INVALID_PROGRAM（生产流里 state 来自程序 initialState 或上一轮结果，
                // 修复走回喂模型诊断），登记为映射判断。
                return rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "frame input is not executable: state is not valid JSON",
                )
            }
            val preparedJson = runPhase(
                transaction,
                jsDispatcher,
                PHASE_PREPARE,
                inputJson,
            )
            val entries = parseContentRequests(preparedJson)
            val batch = VisualContentMeasureBatch(
                programId = request.programId,
                sceneRevision = request.sceneRevision,
                entries = entries,
            )
            val measured = try {
                measurer.measure(batch)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Throwable) {
                return rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "content measurement failed: ${failure.javaClass.simpleName}",
                )
            }
            val contentIds = measured.entries.map { it.contentId }.toSet()
            val sceneJson = runPhase(
                transaction,
                jsDispatcher,
                PHASE_RENDER,
                inputJson,
                metricsJsonText = metricsJson(measured.entries),
            )
            if (transaction.hardExpired) {
                // 渲染完成但硬期限在收尾窗口到点：结果作废（契约 §4"线程中已开始的同步工作
                // 可能晚于期限结束，只能废弃结果"），不截断成"正确图"。
                return rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "transaction result discarded: the hard deadline fired during the render phase",
                )
            }
            return validateScene(sceneJson, contentIds)
        } catch (outcome: PhaseRejection) {
            return outcome.outcome
        } catch (budget: IllegalArgumentException) {
            // 度量批次/条目构造器的预算强制（128 条 / 6000 字符）与空 payload 等：
            // 程序侧越界，有界暴露（与 fake 同型映射）。
            return rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "program violated a transaction budget: ${budget.message ?: "unknown"}",
            )
        } finally {
            jsDispatcher.close()
        }
    }

    /**
     * 一个 JS 阶段（prepare 或 render）：全新 QuickJs 实例 → 沙箱配置 → bootstrap → 用户源码 →
     * 调用点。返回该阶段 `JSON.stringify` 的结果字符串。
     */
    private suspend fun runPhase(
        transaction: Transaction,
        jsDispatcher: CoroutineDispatcher,
        phaseName: String,
        inputJson: String,
        metricsJsonText: String? = null,
    ): String? {
        return try {
            withContext(jsDispatcher) {
                if (transaction.hardExpired) {
                    throw PhaseRejection(
                        rejected(
                            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                            "phase $phaseName skipped: the transaction hit the hard deadline",
                        ),
                    )
                }
                val js = QuickJs.create(jobDispatcher)
                transaction.liveEngine.set(js)
                try {
                    js.memoryLimit = VisualExecutionBudget.MAX_HEAP_BYTES
                    js.maxStackSize = VisualExecutionBudget.MAX_STACK_BYTES
                    js.evaluationTimeoutMillis = cooperativeTimeoutMillis
                    // 确定性 PRNG：每阶段从 input.seed 重放（prepare/render 各自是
                    // (input, metrics) 的纯函数——延续 prepare 的随机流会让 render 依赖
                    // prepare 的消耗次数，破坏纯性）。java.util.Random 算法由 Javadoc 冻结。
                    val prng = Random(transaction.request.seed)
                    js.function("__ktRandomNext") { _ -> prng.nextDouble() }
                    js.evaluate<Unit>(BOOTSTRAP_JS, BOOTSTRAP_FILENAME)
                    js.evaluate<Unit>(transaction.request.sourceJs, PROGRAM_FILENAME)
                    requireVisualShape(js)
                    if (phaseName == PHASE_PREPARE) {
                        js.evaluate<String>(
                            "JSON.stringify(visual.prepare($inputJson))",
                            PREPARE_FILENAME,
                        )
                    } else {
                        js.evaluate<String>(
                            "JSON.stringify(visual.render($inputJson, $metricsJsonText))",
                            RENDER_FILENAME,
                        )
                    }
                } finally {
                    transaction.liveEngine.compareAndSet(js, null)
                    runCatching { js.close() }
                }
            }
        } catch (outcome: PhaseRejection) {
            throw outcome
        } catch (cancel: CancellationException) {
            // 先让宿主取消赢：本事务协程真被取消时 ensureActive 原样重抛。
            currentCoroutineContext().ensureActive()
            // 非宿主取消：上游 evalAndAwait 在 close() 打断评估时抛
            // CancellationException("Already closed.")——按谁关的来定层。
            if (transaction.hardExpired) {
                throw PhaseRejection(
                    rejected(
                        VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                        "phase $phaseName was force-closed after the hard deadline",
                    ),
                )
            }
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "phase $phaseName was aborted by an engine close",
                ),
            )
        } catch (interrupted: QuickJsInterruptedException) {
            // 协作层（evaluationTimeoutMillis / interruptEvaluation）。上游 close 路径不会
            // 伪装成本类型（close 先抛 CancellationException），所以这里纯为协作层信号。
            // 该 catch 必须在 QuickJsException 之前：Interrupted extends QuickJsException，
            // 反序会把死循环误报成 INVALID_PROGRAM——catch 顺序由仪器化用例钉住。
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.PHASE_TIMEOUT,
                    "phase $phaseName exceeded the cooperative timeout " +
                        "($cooperativeTimeoutMillis ms); the cooperative layer is not a hard kill",
                ),
            )
        } catch (jsError: QuickJsException) {
            throw PhaseRejection(classifyJsException(phaseName, jsError))
        } catch (failure: Throwable) {
            // 阶段守卫之外炸出的实现缺陷：有界暴露，不吞成成功（与 fake 同型）。
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "phase $phaseName failed: ${failure.javaClass.simpleName}",
                ),
            )
        }
    }

    // ---------------------------------------------------------------- 沙箱

    /** 程序协议门：源码求值后必须已有可调用的 `visual.prepare`/`visual.render`。 */
    private suspend fun requireVisualShape(js: QuickJs) {
        val ready = js.evaluate<String>(
            "String(typeof visual !== 'undefined' && visual !== null && " +
                "typeof visual.prepare === 'function' && typeof visual.render === 'function')",
            SHAPE_FILENAME,
        )
        if (ready != "true") {
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "program must declare const visual with prepare and render functions",
                ),
            )
        }
    }

    private fun classifyJsException(
        phaseName: String,
        error: QuickJsException,
    ): VisualExecutionResult.Rejected {
        val message = error.message ?: error.javaClass.simpleName
        val lowered = message.lowercase()
        return when {
            // QuickJS 原生堆超限（memoryLimit）的消息面；程序故意抛同文案错误会被误归此类，
            // 仍是有界拒绝，无截断面（类边界登记在类 KDoc）。
            lowered.contains("out of memory") -> rejected(
                VisualExecutionRejection.MEMORY_LIMIT,
                "js heap limit (${VisualExecutionBudget.MAX_HEAP_BYTES} bytes) exceeded " +
                    "in phase $phaseName: ${message.take(120)}",
            )
            // 沙箱内不存在的全局（fetch/document/window/process/require/……）：ReferenceError。
            lowered.contains("not defined") -> rejected(
                VisualExecutionRejection.SANDBOX_VIOLATION,
                "global is absent in phase $phaseName " +
                    "(the sandbox has no IO/network/host objects): ${message.take(120)}",
            )
            else -> rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "program failed in phase $phaseName: ${message.take(160)}",
            )
        }
    }

    // ---------------------------------------------------------------- 协议

    /** 契约 §3.2 FrameInput：`{programId, revision, state, viewport, timeMs, seed, theme}`。 */
    private fun buildFrameInput(request: VisualExecutionRequest): String = buildJsonObject {
        put("programId", request.programId)
        put("revision", request.sceneRevision)
        request.stateJson?.let { stateJson ->
            // 原文内嵌（JSON 是 JS 字面量子集）；StateV1 深校验归 4E.2 compiler。
            put("state", Json.parseToJsonElement(stateJson))
        }
        put("viewport", buildJsonObject {
            put("widthDu", request.viewport.widthDu)
            put("heightDu", request.viewport.heightDu)
        })
        put("timeMs", request.timeMs)
        put("seed", request.seed)
        put("theme", request.theme)
    }.toString()

    private fun metricsJson(metrics: List<VisualMeasuredContent>): String = buildJsonArray {
        metrics.forEach { content ->
            add(buildJsonObject {
                put("contentId", content.contentId)
                put("widthDu", content.widthDu)
                put("heightDu", content.heightDu)
                put("baselineDu", content.baselineDu)
                put("fontSizeSp", content.fontSizeSp)
                put("digest", content.digest)
            })
        }
    }.toString()

    /** prepare 返回值协议：`[{kind: "text"|"math", payload: string}]` 数组。 */
    private fun parseContentRequests(prepareJson: String?): List<VisualContentMeasureEntry> {
        if (prepareJson == null) {
            throw PhaseRejection(
                rejected(VisualExecutionRejection.INVALID_PROGRAM, "prepare returned no JSON"),
            )
        }
        val root = try {
            Json.parseToJsonElement(prepareJson)
        } catch (error: SerializationException) {
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "prepare output is not valid JSON",
                ),
            )
        }
        val array = root as? JsonArray
            ?: throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "prepare must return an array of {kind, payload} entries",
                ),
            )
        return array.mapIndexed { index, element ->
            val entry = element as? JsonObject
                ?: throw PhaseRejection(
                    rejected(
                        VisualExecutionRejection.INVALID_PROGRAM,
                        "prepare[$index] must be an object",
                    ),
                )
            val kind = (entry["kind"] as? JsonPrimitive)?.content
                ?: throw PhaseRejection(
                    rejected(
                        VisualExecutionRejection.INVALID_PROGRAM,
                        "prepare[$index].kind must be a string",
                    ),
                )
            val payload = (entry["payload"] as? JsonPrimitive)?.content
                ?: throw PhaseRejection(
                    rejected(
                        VisualExecutionRejection.INVALID_PROGRAM,
                        "prepare[$index].payload must be a string",
                    ),
                )
            val kindValue = when (kind) {
                "text" -> VisualContentKind.TEXT
                "math" -> VisualContentKind.MATH
                else -> throw PhaseRejection(
                    rejected(
                        VisualExecutionRejection.INVALID_PROGRAM,
                        "prepare[$index].kind must be text or math, got '$kind'",
                    ),
                )
            }
            VisualContentMeasureEntry(kindValue, payload)
        }
    }

    /**
     * render 输出校验：字节预算 → JSON 可解析 → `contentId` 引用冻结检查（保守面：扫描全部
     * `contentId` 键值，超出本事务冻结集合即拒）。完整 SceneV1 严格校验归 4E.2 共享 compiler。
     */
    private fun validateScene(
        sceneJson: String?,
        contentIds: Set<String>,
    ): VisualExecutionResult {
        if (sceneJson == null) {
            return rejected(VisualExecutionRejection.INVALID_PROGRAM, "render returned no JSON")
        }
        val bytes = figureUsageUtf8Bytes(sceneJson)
        if (bytes > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
            return rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "rendered scene is $bytes bytes, over the " +
                    "${VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES}-byte transaction budget",
            )
        }
        val scene = try {
            Json.parseToJsonElement(sceneJson)
        } catch (error: SerializationException) {
            return rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "render output is not valid JSON",
            )
        }
        val referenced = mutableSetOf<String>()
        collectContentIds(scene, referenced)
        val unknown = referenced - contentIds
        if (unknown.isNotEmpty()) {
            return rejected(
                VisualExecutionRejection.INVALID_SCENE,
                "render referenced content not frozen in this transaction: " +
                    unknown.sorted().joinToString(",").take(64),
            )
        }
        return VisualExecutionResult.Rendered(
            sceneJson = sceneJson,
            sceneSchemaVersion = SCENE_SCHEMA_VERSION,
            contentIds = contentIds,
        )
    }

    private fun collectContentIds(element: JsonElement, into: MutableSet<String>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (key == "contentId" && value is JsonPrimitive) {
                    into.add(value.content)
                } else {
                    collectContentIds(value, into)
                }
            }
            is JsonArray -> element.forEach { collectContentIds(it, into) }
            else -> Unit
        }
    }

    private fun rejected(reason: VisualExecutionRejection, message: String) =
        VisualExecutionResult.Rejected(
            reason,
            message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS),
        )

    /** 事务级控制流：把阶段失败折成有界拒绝往事务层抛（fake 的 RejectedOutcomeException 同型）。 */
    private class PhaseRejection(val outcome: VisualExecutionResult.Rejected) :
        RuntimeException(outcome.diagnostics)

    /** 一次渲染事务的两层终止状态（inner：迟到定时器要核对引擎的当前事务指针）。 */
    private inner class Transaction(
        val request: VisualExecutionRequest,
        val digest: String,
    ) {
        /** 当前阶段活引擎；watchdog 强弃与阶段 finally 双路径竞争，getAndSet/CAS 清零。 */
        val liveEngine = AtomicReference<QuickJs?>(null)

        @Volatile
        var hardExpired: Boolean = false
            private set

        @Volatile
        private var finished: Boolean = false

        /** 契约 §4：完成后迟到的 watchdog 不得杀下一任务——先核对本事务仍是当前事务。 */
        fun onHardDeadline() {
            if (finished || currentTransaction.get() !== this) return
            hardExpired = true
            forceCloseLiveEngine()
        }

        fun forceAbandon() {
            finished = true
            forceCloseLiveEngine()
        }

        /**
         * 强弃：close() 幂等（上游 closed.compareAndSet），但可能在 jsMutex 上被 issue #240
         * 的排水循环卡死——放到专用守护线程，绝不占 watchdog 线程；卡住即 accept-the-leak。
         */
        private fun forceCloseLiveEngine() {
            val js = liveEngine.getAndSet(null) ?: return
            Thread({
                try {
                    js.close()
                } catch (_: Throwable) {
                    // 强弃尽力而为；失败留给泄漏登记。
                }
            }, "quickjs-visual-force-close").apply {
                isDaemon = true
                start()
            }
        }
    }

    private companion object {
        const val HOST_AWAIT_GRACE_MILLIS: Long = 500L
        const val SCENE_SCHEMA_VERSION: Int = 1
        const val PHASE_PREPARE = "prepare"
        const val PHASE_RENDER = "render"
        const val BOOTSTRAP_FILENAME = "visual-bootstrap.js"
        const val PROGRAM_FILENAME = "visual-program.js"
        const val SHAPE_FILENAME = "visual-shape.js"
        const val PREPARE_FILENAME = "visual-prepare.js"
        const val RENDER_FILENAME = "visual-render.js"

        /**
         * bootstrap（按 R1 §4.1 配方，两处最小硬化，均登记）：
         * 1. `Math.random` 闭包捕获 `__ktRandom` **值**（配方是按名查全局 `__ktRandomNext()`）——
         *    防程序重绑 `globalThis.__ktRandomNext` 后经 Math.random 间接换掉 PRNG；
         * 2. 尾句 `void 0;` 把脚本完成值钉为 undefined——上游 `evaluate<Unit>` 对 undefined
         *    走 `null as T` 快路径（castValueOr.jvm.kt），对对象完成值会落进通用 converter
         *    （行为引擎相关），钉住 undefined 避开该面。
         */
        const val BOOTSTRAP_JS: String =
            "globalThis.Date = undefined;\n" +
                "const __ktRandom = __ktRandomNext;\n" +
                "Math.random = function () { return __ktRandom(); };\n" +
                "Object.freeze(Math);\n" +
                "void 0;\n"
    }
}
