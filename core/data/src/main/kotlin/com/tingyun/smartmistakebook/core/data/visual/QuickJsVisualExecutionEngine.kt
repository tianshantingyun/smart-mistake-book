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
import com.tingyun.smartmistakebook.core.domain.VisualViewport
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
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
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
 * 事务编排（契约 §3.2 逐步；批次 27 T1 起为每事务三 JS 上下文）：
 * 1. prepare 上下文：新建 QuickJs 实例 → bootstrap（封闭 Date、注入种子 PRNG、冻结
 *    Math/Object）→ visualSdk 资产 → 求值用户源码 → `visual.prepare(input)` → JSON.stringify
 *    取回批量内容度量请求 → close。input 在注入瞬间经 [DEEP_FREEZE_JS] 深冻结（契约 §3.2:58）。
 * 2. 原生度量：经 [VisualContentMeasurer] 恰一批。
 * 3. render 上下文：**新建** QuickJs 实例重走 bootstrap + visualSdk + 同一源码 →
 *    `visual.render(input, metrics)` → JSON.stringify 取回场景 JSON → close。input 与
 *    metrics 在注入瞬间各自深冻结（契约 §3.2:58）。
 * 4. 场景保守校验：字节预算 + 引用冻结检查（扫描场景 JSON 的 `contentId` 值，拒绝本事务
 *    未冻结的引用）。冻结检查**先于**编译（悬空引用的诊断以"引用未冻结"为准）。
 * 5. compile 上下文（批次 27 T1 新增，R1 §2 第三上下文）：**新建** QuickJs 实例 →
 *    bootstrap → 编译器资产（**无用户源码、无 SDK**——编译器完整性是结构性的，无需
 *    深冻结注入图：上下文里没有可改动它的代码）→ `__visualCompile(sceneJson, metricsJson,
 *    expect)` → DrawOps V1 JSON 文本 → close。编译错误面（SCENE_* 令牌）经分类器折
 *    [VisualExecutionRejection.INVALID_SCENE]（类型化拒绝，不进 INVALID_PROGRAM 泛化）。
 * 6. DrawOps 原生严格校验：[VisualDrawOpsValidator] 以与编译器同一套不变量复验
 *    （词表/预算/有限性/配平/冻结引用）——"编译器可信但不免检"。
 * 7. 原子提交：[VisualExecutionResult.Rendered] 携带 sceneJson（保留）与
 *    drawCommandsJson/drawSchemaVersion（批次 27 T1 新增）。
 *
 * 每事务新上下文：prepare/render/compile 各用全新实例（脚本全局不跨事务、也不跨阶段复用）——
 * 契约 §3.2"程序不能依赖跨调用全局变量"由结构保证，不靠脚本自觉。每上下文各受一次
 * [VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS] 协作期限约束；watchdog/1500ms
 * 硬期限/队列/终止语义零改动（共享纪律：客户端/协议不动引擎终止语义）。
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
 * [VisualExecutionResult.Rejected] 返回，不抛库异常穿透端口。分类是纯函数
 * [classifyVisualJsFailure] 的四级策略（R3 `docs/research/2026-10-26-quickjs-oom-semantics.md`
 * §6.1）：
 * 1. **类型优先**：[QuickJsInterruptedException] → [PHASE_TIMEOUT]；它必须先于消息匹配，
 *    否则伪造/恰好同文案的中断会被误归堆上限。
 * 2. **消息严格前缀**：只认 `InternalError: out of memory` → [MEMORY_LIMIT]；不认裸
 *    `out of memory` 或 `RangeError: out of memory`（程序伪造面）。`string too long` /
 *    `stack overflow` 不是堆上限 → [INVALID_PROGRAM]。
 * 3. **消息丢失 + 堆证据**：quickjs-kt 在 Error 对象也分配不出来时只留下 `"null"`
 *    （JS_NULL 链，KD-70 设备实测）；仅当 `memoryUsage.mallocSize >= 90% mallocLimit`
 *    才归属 [MEMORY_LIMIT]。`"not defined"` → [SANDBOX_VIOLATION] 维持原判据。
 *    **比值是未校准的主证据**（R2 `docs/research/2026-10-26-quickjs-memory-accounting.md`）：
 *    该分支的诊断带 `PENDING_CALIBRATION`，直到设备用 GC 锚定夹具复跑并落盘；阈值不变。
 * 4. **不可归属兜底**：丢失哨兵但堆不接近上限（如 `throw null`）→ 内部
 *    [VisualJsFailureKind.UNATTRIBUTED]；对外枚举本批不新增 `UNKNOWN`，折成
 *    [INVALID_PROGRAM]，诊断以 `[UNATTRIBUTED]` 开头供宿主区分（R3 §6.6 退化口径）。
 * 分类纯函数由 JVM 单测覆盖（`VisualJsFailureClassificationTest`）；设备形态由仪器化
 * 探针 P1–P5 钉住。程序故意抛同文案错误可被误分类（仍是有界拒绝，无截断面），登记为
 * 已知边界。
 *
 * **close/execute 生命周期（批次 22 T6，KD-71）**：`closed` 与"当前在飞事务"由同一把
 * `stateLock` 保护——登记时在同一临界区内先判 closed 再记 inFlight，close 也在同一临界区内
 * 置 closed 并取走该事务。两种顺序因此只可能是二选一：close 先 → 登记被拒（[ENGINE_CLOSED]）；
 * 登记先 → close 必能取到该事务并强弃它。不存在"close 读到 null、execute 随后执行成功"的窗口。
 *
 * - 强弃：close 对在飞事务置 `abandoned` 并强弃活引擎；阶段在创建活引擎前与登记活引擎后各查
 *   一次 `abandoned`（close 的 set/getAndSet 与这里的 set/get 构成 Dekker 式握手，至少一侧
 *   看见另一侧），保证不会留下"未被强弃且继续执行"的事务。
 * - 有界等待：close 在强弃后等待该事务收尾，上界 `hardDeadlineMillis + 500ms`。正常事务在
 *   强弃后毫秒级收尾；issue #240 的同步 JNI 阻塞无法被等待，超时后按 accept-the-leak 返回
 *   （因此 close 仍可能在 #240 形状下先于 `execute()` 实际返回，但该 execute 永远不会成功）。
 * - 线性化点：事务最终结果与 close 的 closed=true 在同一临界区内判定。close 先到 → 任何结果
 *   （包括本可成功的渲染）折成 [ENGINE_CLOSED]；事务判定先到 → 结果在关闭前已成立。
 * - 幂等：重复 close 在没有在飞事务时直接返回；并发 close 的后来者同样等待在飞事务收尾
 *   （有界），不会让调用方误以为引擎已排空。关闭后 `execute()` 立即回 [ENGINE_CLOSED]。
 *
 * close 不等待引擎锁/`jsMutex`（可能被 #240 卡住的强弃走专用守护线程），可在任意线程调用；
 * 但会等待在飞事务收尾（有界），因此不应在 Binder/主线程调用。
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

    /**
     * 生命周期门（KD-71）：[closed] 与 [inFlight] 只在持有本锁时读写。
     *
     * 拆成两个原子量（closed AtomicBoolean + currentTransaction AtomicReference）时，
     * close 可以落在"读到 closed=false"与"设置 currentTransaction"之间：close 取不到事务、
     * shutdown 掉 watchdog，execute 却已登记并继续执行，最终在 close 之后返回成功。同一临界区
     * 把两种顺序压成互斥的二选一，窗口消失。
     */
    private val stateLock = Any()

    /** 持 [stateLock] 读写；true = 终态，不再登记任何事务。 */
    private var closed = false

    /** 持 [stateLock] 读写；非 null = 已登记、尚未收尾的唯一在飞事务。 */
    private var inFlight: Transaction? = null

    /**
     * KD-71 的**确定性红测缝**（批次 30 T7）：在"关闭态预检"之后、登记临界区之前调用。
     *
     * **它消灭的失败**：登记窗口的回归没有离设备、确定性的红测。既有确定性用例
     * `closeDuringAnInFlightTransactionWaitsAndRejectsIt` 只能把事务卡在 measure（登记已
     * 完成之后）——它钉的是"强弃+等待"半边；32 轮压测是概率性的。若有人把登记重新拆回
     * "`isClosed()` 预检 → 非原子登记"，在原生库缺席的 JVM 上没有任何用例能稳定地踩中窗口。
     * 本缝让测试把 close 精确地落在预检与登记之间：**修复前的代码会继续登记并执行**（JVM
     * 上表现为 `INVALID_PROGRAM`，设备上会真的渲染成功），修复后必在临界区内看见
     * `closed=true` 并回 [VisualExecutionRejection.ENGINE_CLOSED]。
     *
     * 只允许测试赋值；生产路径恒为 null（一次可空读，不改变任何临界区语义）。
     */
    @Volatile
    internal var beforeRegistrationHookForTest: (() -> Unit)? = null

    private val fusedProgramDigests: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    private val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "quickjs-visual-watchdog").apply { isDaemon = true }
    }

    override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
        if (isClosed()) {
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
        val transaction = Transaction(request, digest)
        // KD-71 确定性红测缝（仅测试赋值；生产为 null）：把 close 精确地放进"预检之后、
        // 登记之前"的窗口。修复前的实现会在这里之后继续登记并执行；现实现进入下一临界区
        // 时必然看见 closed=true 并拒绝。
        beforeRegistrationHookForTest?.invoke()
        // 判定 closed 与登记事务在同一临界区内（KD-71）：close 先则这里必见 closed；
        // 登记先则 close 必能取到 inFlight。watchdog 的 shutdownNow() 与 closed=true 也在
        // 同一临界区（见 close），因此登记成功时调度器必然仍接受任务。
        synchronized(stateLock) {
            if (closed) {
                return rejected(VisualExecutionRejection.ENGINE_CLOSED, "engine is closed")
            }
            if (inFlight != null) {
                return rejected(
                    VisualExecutionRejection.BUSY,
                    "another visual transaction is already in flight",
                )
            }
            inFlight = transaction
            transaction.hardTimer = watchdog.schedule(
                { transaction.onHardDeadline() },
                hardDeadlineMillis,
                TimeUnit.MILLISECONDS,
            )
        }
        try {
            val result = try {
                // 这个 withTimeout 只对可协作的挂起有效：issue #240 的同步 JNI 阻塞不可抢占，
                // 此时本调用永不返回，下面的 catch/finally 都不执行（见类 KDoc）。真正的调用方
                // 上界必须由调用方自己的线程/进程边界承担（R1 §2.4 观察形状）。
                withTimeout(hardDeadlineMillis + HOST_AWAIT_GRACE_MILLIS) {
                    runTransaction(transaction)
                }
            } catch (timeout: TimeoutCancellationException) {
                // 可协作挂起的硬上界到点（#240 的同步 JNI 阻塞到不了这里，见类 KDoc）——强弃、
                // 熔断、放弃该事务的执行线程（daemon，随进程回收）。
                transaction.forceAbandon()
                rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "transaction did not finish within the host hard deadline " +
                        "(${hardDeadlineMillis} ms + ${HOST_AWAIT_GRACE_MILLIS} ms grace); " +
                        "the program is fused and its worker abandoned",
                )
            } catch (cancel: CancellationException) {
                transaction.forceAbandon()
                throw cancel
            }
            if (result is VisualExecutionResult.Rejected &&
                result.reason == VisualExecutionRejection.HARD_DEADLINE_EXCEEDED
            ) {
                fusedProgramDigests.add(digest)
            }
            // 线性化点：与 close 的 closed=true 互斥。close 先到 → 本事务（哪怕已经算出可渲染
            // 结果）一律折成 ENGINE_CLOSED；本判定先到 → 结果在关闭前已经成立，可以返回。
            return synchronized(stateLock) {
                if (closed) {
                    rejected(
                        VisualExecutionRejection.ENGINE_CLOSED,
                        "engine closed before the transaction result was released",
                    )
                } else {
                    result
                }
            }
        } finally {
            transaction.hardTimer?.cancel(false)
            synchronized(stateLock) {
                if (inFlight === transaction) inFlight = null
            }
            // close 在该事务上最多等 hardDeadline + grace；取消/异常路径同样要放行。
            transaction.markCompleted()
        }
    }

    override fun close() {
        var transaction: Transaction? = null
        synchronized(stateLock) {
            // 并发 close 的后来者也要等同一个在飞事务收尾（inFlight 由该事务的 finally 清空），
            // 不能因为 closed 已是 true 就提前返回、让调用方误以为引擎已排空。
            transaction = inFlight
            if (!closed) {
                closed = true
                // 与 execute 的登记同临界区：登记成功的 schedule 不可能撞上已 shutdown 的调度器。
                watchdog.shutdownNow()
            }
        }
        val draining = transaction ?: return
        // 先强弃（幂等、不占 watchdog 线程），再有界等待它收尾。上界与事务自身的宿主期限一致；
        // issue #240 的同步 JNI 阻塞无法被等待，超时后按 accept-the-leak 返回。
        draining.forceAbandon()
        draining.awaitCompletion(hardDeadlineMillis + HOST_AWAIT_GRACE_MILLIS)
    }

    private fun isClosed(): Boolean = synchronized(stateLock) { closed }

    // ---------------------------------------------------------------- 事务

    private suspend fun runTransaction(transaction: Transaction): VisualExecutionResult {
        val request = transaction.request
        if (transaction.abandoned.get()) {
            // close 在登记后、coroutine 真正开跑前抢到了事务：连事务线程都不必建。
            return rejected(
                VisualExecutionRejection.ENGINE_CLOSED,
                "transaction was abandoned before it started",
            )
        }
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
            ) { js ->
                js.evaluate<Unit>(VisualJsAssets.sdkJs, SDK_FILENAME)
                js.evaluate<Unit>(transaction.request.sourceJs, PROGRAM_FILENAME)
                requireVisualShape(js)
                // 深冻结（契约 §3.2:58）：input 在注入瞬间冻透，见 [DEEP_FREEZE_JS]。
                js.evaluate<String>(
                    "JSON.stringify(visual.prepare($DEEP_FREEZE_JS($inputJson)))",
                    PREPARE_FILENAME,
                )
            }
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
            val metricsJsonText = metricsJson(measured.entries)
            val sceneJson = runPhase(
                transaction,
                jsDispatcher,
                PHASE_RENDER,
            ) { js ->
                js.evaluate<Unit>(VisualJsAssets.sdkJs, SDK_FILENAME)
                js.evaluate<Unit>(transaction.request.sourceJs, PROGRAM_FILENAME)
                requireVisualShape(js)
                // 深冻结（契约 §3.2:58）：input 与 metrics 在注入瞬间各自冻透。
                js.evaluate<String>(
                    "JSON.stringify(visual.render(" +
                        "$DEEP_FREEZE_JS($inputJson), $DEEP_FREEZE_JS($metricsJsonText)))",
                    RENDER_FILENAME,
                )
            }
            if (transaction.hardExpired) {
                // 渲染完成但硬期限在收尾窗口到点：结果作废（契约 §4"线程中已开始的同步工作
                // 可能晚于期限结束，只能废弃结果"），不截断成"正确图"。
                return rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "transaction result discarded: the hard deadline fired during the render phase",
                )
            }
            // 保守面（字节预算 / JSON / 冻结引用）先于编译：悬空引用的诊断以"引用未冻结"
            // 为准，且不合法场景不进编译上下文。
            val checkedSceneJson = validateSceneJson(sceneJson, contentIds)
            val drawCommandsJson = runCompilePhase(transaction, jsDispatcher, checkedSceneJson, metricsJsonText, request)
            if (transaction.hardExpired) {
                return rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "transaction result discarded: the hard deadline fired during the compile phase",
                )
            }
            return commitRendered(checkedSceneJson, contentIds, drawCommandsJson, request.viewport)
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
     * 一个 JS 上下文（prepare / render / compile）：全新 QuickJs 实例 → 沙箱配置 →
     * bootstrap → [evaluateInContext]（各阶段在此求值自己的资产与调用点）→ 返回
     * `JSON.stringify` 的结果字符串。
     *
     * 堆快照只在"消息丢失哨兵"路径上取（[isLostMessage]）：普通 TypeError 不会多拿一次
     * `jsMutex`；读取发生在 `finally { js.close() }` 之前，且 `JS_ComputeMemoryUsage` 本身不分配
     * （R3 §2.1.4）。取消/中断不读堆，也不改变既有终止层语义。
     *
     * **快照可信度**（R2 §2）：OOM 的 JS_NULL 抛出后，`JS_CallInternal` 的异常出口会先释放
     * 该帧所有局部变量与栈值（`quickjs.c:20690`、`:20699-20709`）；引用计数归零的对象立即
     * 释放并级联释放 `next` 属性（`:6474-6485`、`:6341-6353`、`:6097-6117`）。只挂在局部
     * 变量上的链表因此在 `JS_Eval` 返回前就被清空，快照读到回落水位——E-02 的未锚定夹具
     * 因此读到 177216（1.06%）。探针必须给链一个根引用（仪器化 P2 用 `globalThis`），否则
     * 比值证据不成立。
     */
    private suspend fun runPhase(
        transaction: Transaction,
        jsDispatcher: CoroutineDispatcher,
        phaseName: String,
        evaluateInContext: suspend (QuickJs) -> String?,
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
                if (transaction.abandoned.get()) {
                    // close 已强弃本事务（尚无活引擎可强弃）：不得再创建新上下文。
                    throw PhaseRejection(
                        rejected(
                            VisualExecutionRejection.ENGINE_CLOSED,
                            "phase $phaseName skipped: the engine is closed",
                        ),
                    )
                }
                val js = QuickJs.create(jobDispatcher)
                transaction.liveEngine.set(js)
                try {
                    // 与 close 的 forceCloseLiveEngine 构成 Dekker 握手（abandoned.set →
                    // liveEngine.getAndSet 对 liveEngine.set → abandoned.get）：至少一侧看到
                    // 另一侧，所以不存在"close 没拿到引擎、这里也没看到 abandoned"的执行窗口。
                    if (transaction.abandoned.get()) {
                        throw PhaseRejection(
                            rejected(
                                VisualExecutionRejection.ENGINE_CLOSED,
                                "phase $phaseName aborted: the engine is closed",
                            ),
                        )
                    }
                    js.memoryLimit = VisualExecutionBudget.MAX_HEAP_BYTES
                    js.maxStackSize = VisualExecutionBudget.MAX_STACK_BYTES
                    js.evaluationTimeoutMillis = cooperativeTimeoutMillis
                    // 确定性 PRNG：每阶段从 input.seed 重放（prepare/render 各自是
                    // (input, metrics) 的纯函数——延续 prepare 的随机流会让 render 依赖
                    // prepare 的消耗次数，破坏纯性）。java.util.Random 算法由 Javadoc 冻结。
                    val prng = Random(transaction.request.seed)
                    js.function("__ktRandomNext") { _ -> prng.nextDouble() }
                    js.evaluate<Unit>(BOOTSTRAP_JS, BOOTSTRAP_FILENAME)
                    // 各阶段资产与调用点由调用方注入（prepare/render：SDK + 源码 + 协议门；
                    // compile：编译器资产 + __visualCompile，无用户源码）。
                    evaluateInContext(js)
                } catch (jsError: QuickJsException) {
                    // 类型分支（QuickJsInterruptedException）不读堆：它不是 OOM 形态。
                    val heap = if (jsError !is QuickJsInterruptedException &&
                        isLostMessage(jsError.message)
                    ) {
                        runCatching {
                            js.memoryUsage.let { usage ->
                                HeapSnapshot(
                                    mallocSize = usage.mallocSize,
                                    mallocLimit = usage.mallocLimit,
                                )
                            }
                        }.getOrNull()
                    } else {
                        null
                    }
                    throw ClassifiedJsFailure(jsError, heap)
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
        } catch (failure: ClassifiedJsFailure) {
            // 类型优先在分类器内部（QuickJsInterruptedException 先于任何消息匹配），
            // 不再靠 catch 顺序把中断钉成 PHASE_TIMEOUT；仪器化死循环用例继续钉住该出口。
            throw PhaseRejection(classifyJsException(phaseName, failure.error, failure.heap))
        } catch (jsError: QuickJsException) {
            // 阶段守卫之外（如 QuickJs.create 本身）抛出的库异常：同一分类器兜底，无堆快照。
            throw PhaseRejection(classifyJsException(phaseName, jsError, heap = null))
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

    /** 端口层落点：纯分类器给 kind + 诊断，这里只折 reason 并做有界截断。 */
    private fun classifyJsException(
        phaseName: String,
        error: QuickJsException,
        heap: HeapSnapshot?,
    ): VisualExecutionResult.Rejected {
        val failure = classifyVisualJsFailure(
            phaseName = phaseName,
            error = error,
            heap = heap,
            cooperativeTimeoutMillis = cooperativeTimeoutMillis,
        )
        return rejected(failure.reason, failure.diagnostics)
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
     * render 输出的保守校验（先于编译）：字节预算 → JSON 可解析 → `contentId` 引用冻结检查。
     * 冻结检查在这里先行、[VisualDrawOpsValidator] 在编译后再复验一次——悬空引用的诊断
     * 以本函数的"引用未冻结"文案为准（仪器化 DANGLING 用例钉住），不合法场景不进编译上下文。
     */
    private fun validateSceneJson(
        sceneJson: String?,
        contentIds: Set<String>,
    ): String {
        if (sceneJson == null) {
            throw PhaseRejection(
                rejected(VisualExecutionRejection.INVALID_PROGRAM, "render returned no JSON"),
            )
        }
        val bytes = figureUsageUtf8Bytes(sceneJson)
        if (bytes > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "rendered scene is $bytes bytes, over the " +
                        "${VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES}-byte transaction budget",
                ),
            )
        }
        val scene = try {
            Json.parseToJsonElement(sceneJson)
        } catch (error: SerializationException) {
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "render output is not valid JSON",
                ),
            )
        }
        val referenced = mutableSetOf<String>()
        collectContentIds(scene, referenced)
        val unknown = referenced - contentIds
        if (unknown.isNotEmpty()) {
            throw PhaseRejection(
                rejected(
                    VisualExecutionRejection.INVALID_SCENE,
                    "render referenced content not frozen in this transaction: " +
                        unknown.sorted().joinToString(",").take(64),
                ),
            )
        }
        return sceneJson
    }

    /**
     * compile 第三上下文（批次 27 T1，R1 §2）：bootstrap → 编译器资产 →
     * `__visualCompile(sceneJson, metricsJson, expect)` → DrawOps JSON 文本。
     *
     * 无用户源码、无 SDK 原语——编译器完整性是结构性的；注入图不深冻结（上下文里没有
     * 可改动它的代码）。错误面：SceneCompileError（SCENE_* 令牌）经分类器折 INVALID_SCENE；
     * OOM/协作中断仍走各自层（MEMORY_LIMIT / PHASE_TIMEOUT）——编译不豁免预算。
     */
    private suspend fun runCompilePhase(
        transaction: Transaction,
        jsDispatcher: CoroutineDispatcher,
        sceneJson: String,
        metricsJsonText: String,
        request: VisualExecutionRequest,
    ): String {
        // expect（R1 §3.1 + 本批钉定扩展）：场景回显核对项。viewport 供编译器执行
        // "viewBox == viewport" 的 V1 MVA 不变量（R1 §3.2）。
        val expectJson = buildJsonObject {
            put("sceneRevision", request.sceneRevision)
            put("viewport", buildJsonObject {
                put("widthDu", request.viewport.widthDu)
                put("heightDu", request.viewport.heightDu)
            })
        }.toString()
        val drawJson = runPhase(transaction, jsDispatcher, PHASE_COMPILE) { js ->
            js.evaluate<Unit>(VisualJsAssets.compilerJs, COMPILER_FILENAME)
            // 编译器唯一暴露面收三个 JSON 文本（visual-compiler.js strictParseJson 与
            // typeof 门只收 string）——文本必须经 [jsStringLiteral] 编码为 JS 字符串
            // 字面量传入；`$` 原样插值会把实参变成 JS 对象值，每笔事务先抛
            // SCENE_FIELD_TYPE（批次 27 复核修复，设备轨隔离 10 红/引擎套件 9 红）。
            js.evaluate<String>(
                "__visualCompile(" +
                    "${jsStringLiteral(sceneJson)}, ${jsStringLiteral(metricsJsonText)}, ${jsStringLiteral(expectJson)})",
                COMPILE_FILENAME,
            )
        }
        if (drawJson == null) {
            throw PhaseRejection(
                rejected(VisualExecutionRejection.INVALID_SCENE, "compiler returned no draw ops"),
            )
        }
        return drawJson
    }

    /**
     * DrawOps 原生严格校验（第二道门）+ 原子提交。校验器拒绝 = INVALID_SCENE +
     * DRAWOPS_* 码；通过 = [VisualExecutionResult.Rendered]（sceneJson 保留、
     * drawCommandsJson/drawSchemaVersion 随行——契约 §8 快照两者都存）。
     */
    private fun commitRendered(
        sceneJson: String,
        contentIds: Set<String>,
        drawCommandsJson: String,
        viewport: VisualViewport,
    ): VisualExecutionResult {
        return when (val validation = VisualDrawOpsValidator.validate(drawCommandsJson, contentIds, viewport)) {
            is VisualDrawOpsValidation.Invalid ->
                rejected(
                    VisualExecutionRejection.INVALID_SCENE,
                    "${validation.code}: ${validation.message}",
                )

            is VisualDrawOpsValidation.Valid ->
                VisualExecutionResult.Rendered(
                    sceneJson = sceneJson,
                    sceneSchemaVersion = SCENE_SCHEMA_VERSION,
                    contentIds = contentIds,
                    drawCommandsJson = drawCommandsJson,
                    drawSchemaVersion = DRAW_SCHEMA_VERSION,
                )
        }
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

    /**
     * 携带阶段堆快照的库异常包装。必须在 `close()` 之前取快照（关闭后实例不可读）；
     * [heap] 只在消息丢失哨兵路径上非空，普通 JS 错误为 null。
     */
    private class ClassifiedJsFailure(
        val error: QuickJsException,
        val heap: HeapSnapshot?,
    ) : RuntimeException(error.message, error)

    /** 一次渲染事务的两层终止状态（inner：迟到定时器要核对引擎的当前事务指针）。 */
    private inner class Transaction(
        val request: VisualExecutionRequest,
        val digest: String,
    ) {
        /** 当前阶段活引擎；watchdog 强弃与阶段 finally 双路径竞争，getAndSet/CAS 清零。 */
        val liveEngine = AtomicReference<QuickJs?>(null)

        /**
         * close 置位；阶段在创建活引擎前与登记活引擎后各查一次（KD-71）。与 [liveEngine]
         * 的 set/getAndSet 构成 Dekker 握手，见 runPhase。
         */
        val abandoned = AtomicBoolean(false)

        /** 硬期限定时器句柄；仅 execute 协程在登记后与本事务收尾时读写。 */
        var hardTimer: ScheduledFuture<*>? = null

        @Volatile
        var hardExpired: Boolean = false
            private set

        /** close 等待本事务收尾；execute 的 finally 在成功/拒绝/取消路径都 countDown。 */
        private val completed = CountDownLatch(1)

        /** 契约 §4：完成后迟到的 watchdog 不得杀下一任务——先核对本事务仍是当前事务。 */
        fun onHardDeadline() {
            synchronized(stateLock) {
                if (abandoned.get() || inFlight !== this) return
            }
            hardExpired = true
            forceCloseLiveEngine()
        }

        fun forceAbandon() {
            abandoned.set(true)
            forceCloseLiveEngine()
        }

        fun markCompleted() {
            completed.countDown()
        }

        /** close 的有界等待；被中断时恢复中断标志并按已尽力处理返回。 */
        fun awaitCompletion(timeoutMillis: Long) {
            try {
                completed.await(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
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

    companion object {
        private const val HOST_AWAIT_GRACE_MILLIS: Long = 500L
        private const val SCENE_SCHEMA_VERSION: Int = 1

        /** DrawOps V1（批次 27 T1；与 `visual-compiler.js` 的 drawSchemaVersion 同值）。 */
        private const val DRAW_SCHEMA_VERSION: Int = 1
        private const val PHASE_PREPARE = "prepare"
        private const val PHASE_RENDER = "render"
        private const val PHASE_COMPILE = "compile"
        private const val BOOTSTRAP_FILENAME = "visual-bootstrap.js"
        private const val SDK_FILENAME = "visual-sdk.js"
        private const val PROGRAM_FILENAME = "visual-program.js"
        private const val SHAPE_FILENAME = "visual-shape.js"
        private const val PREPARE_FILENAME = "visual-prepare.js"
        private const val RENDER_FILENAME = "visual-render.js"
        private const val COMPILER_FILENAME = "visual-compiler.js"
        private const val COMPILE_FILENAME = "visual-compile.js"

        /**
         * 服务进程 warm-up（批次 25 T2，KD-76 ①）：真实触达原生装载点并执行一次空程序。
         *
         * **它消灭的失败**：KD-76 的"冷启动首个 execute 在装载竞争下被 1500 ms watchdog
         * 误杀"（E-01d 形状）——装载成本移出任何请求的 watchdog 预算。R1'
         * （docs/research/2026-10-27-visualjs-warmup-queue.md §2.3-1）的草图曾写"构造
         * [QuickJsVisualExecutionEngine] 即触发 QuickJs.create"，经 T2 读码复核**不成立**：
         * 本类构造函数只初始化 measurer/期限、stateLock/fused 集合与 watchdog 线程池，
         * 不引用 QuickJs 类型；`QuickJs.create` 全文件唯一调用点在 [runPhase] 的事务路径内
         * （本文件 `val js = QuickJs.create(jobDispatcher)` 一行）。因此 warm-up 必须
         * **直接调用 [QuickJs.create]**——修正登记见该研究文档 §8。
         *
         * "空程序" = 一次 `void 0;` evaluate（KD-76 ①"libquickjs.so 装载 + 一次空程序
         * warm-up"的原文口径）。不预热每事务的 prepare/render 全路径：那是引擎事务自身的
         * JS 工作，合法占用 1500 ms 程序预算，不是一次性装载成本。
         *
         * 线程与期限：任意线程可调（服务端在专用单线程 executor 上调用）；**不 arm 任何
         * watchdog**——装载不是被监视的"事务"（AOSP Watchdog 只监视已注册对象的形状，
         * R1' §2.1 形状 B），装载期限由调用方/宿主 ready 预算兜底。失败向上抛出
         * （UnsatisfiedLinkError/QuickJsException/…），由调用方决定 fatal 语义。
         * [QuickJs.close] 在无用户 job 的全新实例上不经过 issue #240 的排水循环
         * （warm-up 不 evaluate 任何用户程序），不会卡死。
         */
        fun warmUpNativeRuntime() {
            // 上游 evaluate 是 suspend（排水循环按挂起协作取消），warm-up 不在协程上下文，
            // 用 runBlocking 在调用线程（服务端：jsExecutor 专用线程）内完成；warm-up 只
            // evaluate 一次空程序，无用户 job，阻塞有界。
            runBlocking {
                val js = QuickJs.create(Dispatchers.Default)
                try {
                    js.evaluate<Unit>("void 0;", "visual-warmup.js")
                } finally {
                    runCatching { js.close() }
                }
            }
        }

        /**
         * bootstrap（按 R1 §4.1 配方，三处最小硬化，均登记）：
         * 1. `Math.random` 闭包捕获 `__ktRandom` **值**（配方是按名查全局 `__ktRandomNext()`）——
         *    防程序重绑 `globalThis.__ktRandomNext` 后经 Math.random 间接换掉 PRNG；
         * 2. 尾句 `void 0;` 把脚本完成值钉为 undefined——上游 `evaluate<Unit>` 对 undefined
         *    走 `null as T` 快路径（castValueOr.jvm.kt），对对象完成值会落进通用 converter
         *    （行为引擎相关），钉住 undefined 避开该面。
         * 3. `Object.freeze(Object)`（批次 27 T3）——**它消灭的失败**：注入点深冻结
         *    （[DEEP_FREEZE_JS]）在用户源码求值**之后**才执行，若 `Object.freeze` 与
         *    `Object.getOwnPropertyNames` 仍可写，程序先重绑它们（如
         *    `Object.freeze = function (v) { return v; }`）即可让深冻结整体空转。冻结
         *    Object 构造器把两个原语钉为不可写不可配置：重绑在 sloppy 下静默失败、strict
         *    下抛 TypeError（本地 ES 语义实证，仪器化 `rebindingObjectFreezeCannotDefeatTheDeepFreeze`
         *    在设备轨钉住）。`Object.freeze(Math)` 在此之前已执行——冻结 Object 不影响调用
         *    已冻结对象上的既有方法。
         */
        const val BOOTSTRAP_JS: String =
            "globalThis.Date = undefined;\n" +
                "const __ktRandom = __ktRandomNext;\n" +
                "Math.random = function () { return __ktRandom(); };\n" +
                "Object.freeze(Math);\n" +
                "Object.freeze(Object);\n" +
                "void 0;\n"

        /**
         * 注入点深冻结函数表达式（批次 27 T3；契约 §3.2:58"每次调用的 input 与 metrics
         * 深冻结"）。调用点以 `DEEP_FREEZE_JS(inputJson)` IIFE 形式包住注入字面量。
         *
         * **它消灭的失败**：注入的 input/metrics JSON 图以 JS 字面量进入调用点后是普通
         * 可变对象，程序可改写任意嵌套字段（`input.viewport.widthDu`、`metrics[0].contentId`、
         * `input.state.answer`）再回显，宿主据回显做的判读即被输入伪造逃逸（本地 RED 基线
         * 与仪器化用例实证）。此表达式在**注入瞬间**递归 `Object.freeze` 整图（含数组与其
         * 元素），冻结语义按 ES 规范钉死：普通属性/元素赋值在 sloppy 下静默失败（宿主读回
         * 原值）、strict 下抛 TypeError；`Array.prototype.push` 是规范特例——它以
         * throw-on-fail 写 `length`，对冻结数组在 sloppy 下也抛 TypeError。
         *
         * **为什么是内联函数表达式而不是 bootstrap 全局函数**：调用点 evaluate 在用户源码
         * **之后**执行，期间程序可重绑任何 `globalThis.*`（WRITER 用例实证全局可写）——
         * 全局名在调用点不可信；具名函数表达式的递归名 `df` 绑定在表达式自身作用域，
         * 不受外层/全局同名重绑影响（本地 g9 实证）。配合 [BOOTSTRAP_JS] 的
         * `Object.freeze(Object)`，`Object.freeze` 与 `Object.getOwnPropertyNames` 两个
         * 原语在调用点同样不可被程序替换。
         *
         * 先冻容器再走子节点对纯 JSON 数据与 MDN 先子后父配方等价：字面量图无 getter，
         * 遍历期间没有用户代码可执行。递归深度 = JSON 嵌套深度，受
         * [VisualExecutionBudget.MAX_STACK_BYTES] 兜底（溢出 → `stack overflow` →
         * INVALID_PROGRAM，有界拒绝，与分类器既有判据一致）。
         *
         * **登记边界**：冻结只覆盖注入图自身的属性写/增/删；共享原型（`Object.prototype`）
         * 未冻——程序污染原型只能"伪造 input 缺失字段的读取值"，而对自有字段的读取
         * （宿主判据所在）原型无法劫持，且程序本就控制自身代码，未获新增伪造能力；
         * `JSON.stringify`/`JSON.parse` 未冻（输出侧完整性属另一硬化面，不在本项）。
         */
        private const val DEEP_FREEZE_JS: String =
            "(function df(v) { if (v !== null && typeof v === 'object') { Object.freeze(v); " +
                "var ks = Object.getOwnPropertyNames(v); " +
                "for (var i = 0; i < ks.length; i++) { df(v[ks[i]]); } } return v; })"

        /**
         * 宿主侧 JSON 文本 → JS 双引号字符串字面量（批次 27 复核修复）。
         *
         * **它消灭的失败**：`__visualCompile(sceneText, metricsText, expectText)` 的三参数
         * 契约是 JSON **文本**（visual-compiler.js `strictParseJson` 只收字符串，
         * `globalThis.__visualCompile` 的 typeof 门逐字要求三实参皆 string）。此前
         * [runCompilePhase] 把三个文本以 `$` 原样插值进 JS 源——实参成了 JS 对象值，
         * 每笔事务先抛 `SCENE_FIELD_TYPE: __visualCompile expects three JSON text
         * arguments`，全部 render 事务折 INVALID_SCENE，MVA 编译路径全死（设备轨隔离
         * 套件 10 红 / 引擎套件 9 红，step05 §3；修复前复现与修复后编译通过已在 Node
         * 台对真实资产实证）。
         *
         * 转义集：反斜杠、双引号与 C0 控制字符（`\b` `\f` `\n` `\r` `\t` 具名，其余
         * `\u00XX`）——合法 JSON 文本中可能出现的全部需转义字符；结构字符与空白在
         * 双引号字面量内无需转义，单引号/斜杠亦然；U+2028/U+2029 在 ES2019+ 字符串
         * 字面量中合法（QuickJS 按 ES2020+ 实现），不转义。
         *
         * 调用面只有 [runCompilePhase]：prepare/render 的 `$inputJson`/`$metricsJsonText`
         * 插值是**对象表达式注入**（SDK 契约收对象图、[DEEP_FREEZE_JS] 吃对象），与
         * 编译器的文本契约不同面，不经这里。
         */
        private fun jsStringLiteral(text: String): String {
            val builder = StringBuilder(text.length + 2)
            builder.append('"')
            for (ch in text) {
                when (ch) {
                    '\\' -> builder.append("\\\\")
                    '"' -> builder.append("\\\"")
                    '\b' -> builder.append("\\b")
                    '\u000C' -> builder.append("\\f")
                    '\n' -> builder.append("\\n")
                    '\r' -> builder.append("\\r")
                    '\t' -> builder.append("\\t")
                    else -> {
                        if (ch < ' ') {
                            builder.append("\\u")
                            builder.append(ch.code.toString(16).padStart(4, '0'))
                        } else {
                            builder.append(ch)
                        }
                    }
                }
            }
            builder.append('"')
            return builder.toString()
        }
    }
}
