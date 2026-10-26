package com.tingyun.smartmistakebook.visualjs

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * `:visual_js` 隔离进程里的执行服务（批次 22 T1，4E.2 前置）。
 *
 * 契约 [visual-generation-contract.md] §4 与 R1
 * （docs/research/2026-10-26-visualjs-isolated-process.md）的落地形状：
 * - `android:isolatedProcess="true"` + `android:process=":visual_js"`、`exported=false`；
 *   宿主与它不同 UID，宿主**没有**可用 kill API，硬终止只由本进程
 *   `Process.killProcess(Process.myPid())` 执行。
 * - 契约 §4 三层终止的动作映射（旧文献编号有歧义，这里按动作命名）：
 *   - **L1 协作中断**（100 ms）：引擎内 `evaluationTimeoutMillis` → PHASE_TIMEOUT，不杀进程；
 *   - **L2 服务硬终止**（1500 ms）：本文件的独立 watchdog 线程 + 令牌 CAS →
 *     `killProcess(myPid())`；`terminate(generation)` 在 Binder 线程核对后走同一出口；
 *   - **L3 宿主阶段期限**（2500 ms）：宿主 client 的 `abandon()/unbind/death 确认/最多重建
 *     一次/熔断`——**不含任何 kill API**，也不在本文件。
 * - SPEC: KD-66（docs/known-defects.md）与上游 quickjs-kt issue #240：`execute()`/`close()`
 *   都可能永不返回，引擎自身没有调用方上界；本服务的 L2 SIGKILL 是唯一上界。T1 不修改
 *   `QuickJsVisualExecutionEngine` 的终止语义，只在进程边界兜底。
 *
 * 线程纪律（R1 §4.6）：
 * - 所有 AIDL 方法只在 Binder 线程做 UID/generation/CAS 与投递，JS 永不在 Binder 线程执行；
 * - JS 在专用单线程 executor 上跑（`runBlocking` 包住 suspend 引擎），每次请求一个全新引擎
 *   实例 + 独立线程，避免 issue #240 的残留 `inFlight` 跨请求传染；
 * - watchdog 线程只读原子量 + `Log.w` + SIGKILL，不拿引擎锁、不做 IPC/文件 IO；
 * - 结果写入独立守护线程，避免大结果（≤256 KiB）占住 JS 线程或 Binder 线程。
 *
 * 保守分支登记（本批**不**宣称完成的能力）：
 * - 批次 25 T2（KD-76 ①）已落 warm-up/ready **代码层**：[onCreate] 在任何请求之前经
 *   jsExecutor 完成一次性装载 warm-up（`QuickJsVisualExecutionEngine.warmUpNativeRuntime`：
 *   真实 `QuickJs.create` 装载 + 一次空程序 evaluate——修正 R1' §2.3-1"构造即装载"的错误
 *   草图，见 docs/research/2026-10-27-visualjs-warmup-queue.md §8），完成置 [ready]（单调）；
 *   `execute` 在 ready 前回 SHUTTING_DOWN、请求不排队——"ready 之前不 arm 程序 watchdog"
 *   由该门**结构性保证**（进不了 [runRequest] 就没有 arm 点）。ready 等待预算由宿主承担
 *   （AIDL `isReady()` 轮询，见 VisualJsClientEngine）；隔离进程内的 `libquickjs.so` 装载
 *   实测成本、KD-76 ③④⑤ 设备判据仍未验证，JS 仍不广告；
 * - 文本/数学内容度量未接线（[UnwiredVisualContentMeasurer]），只有纯几何程序可通；
 * - ContentProvider/AndroidX Startup 在隔离进程的实际装配面未审计（见 Application guard）；
 * - JS 方法仍不广告，工具面/提示词/能力快照零改动。
 */
class VisualJsService : Service() {

    private val killed = AtomicBoolean(false)

    /**
     * warm-up 完成标志（批次 25 T2，KD-76 ①）：单调量，false→true 后不回退。warm-up
     * 失败不置位（进程随即自杀）。execute 在 false 时拒绝服务；能通过该门的请求，其
     * 引擎装载必已完成，1500 ms watchdog 从真实 JS 工作起算的语义不变。
     */
    private val ready = AtomicBoolean(false)

    // 单线程 JS 执行器：一个请求一个任务；#240 挂死时泄漏的是这条 daemon 线程，
    // 由 L2 SIGKILL 回收整个进程。
    private val jsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "visual-js-executor").apply { isDaemon = true }
    }

    // 独立 watchdog：与 JS 线程分离，只做令牌 CAS + Log + SIGKILL。
    // 显式 ThreadFactory（不用尾随 lambda）：ScheduledThreadPoolExecutor 同时接受
    // ThreadFactory 与 RejectedExecutionHandler 两个 SAM，尾随 lambda 会歧义。
    private val watchdog = ScheduledThreadPoolExecutor(
        1,
        ThreadFactory { runnable ->
            Thread(runnable, "visual-js-watchdog").apply { isDaemon = true }
        },
    ).apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }

    private val activeGeneration = AtomicLong(0L)
    private val initialized = AtomicBoolean(false)
    private val abandoned = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val initLock = Any()

    override fun onBind(intent: Intent?): IBinder = stub

    /**
     * 批次 25 T2（KD-76 ①）：bind 触发服务创建后，在任何请求之前主动 warm-up。
     * warm-up 跑在 [jsExecutor]（单线程）上——它与后续请求天然串行，且 ready 置位前
     * execute 根本不排队，不存在"warm-up 与请求并发执行 JS"的交错。Chromium
     * ChildProcessService 同形（onCreate 起专用线程装载原生库→mLibraryInitialized，
     * R1' §2.1 形状 C）；装载不在 Binder 线程、不在 watchdog 线程。
     */
    override fun onCreate() {
        super.onCreate()
        warmUpOnJsExecutor()
    }

    /**
     * 一次性引擎装载 warm-up：真实 `QuickJs.create`（libquickjs.so 装载 + 运行时表
     * 预热）+ 一次空程序 evaluate，完成置 [ready]。
     *
     * **它消灭的失败**：KD-76 的"冷启动首个 execute 的原生装载成本落在 1500 ms watchdog
     * 预算内被误杀"（E-01d 设备形状）。warm-up 自身不 arm 程序 watchdog（装载不是被监视
     * 的事务）；也不加独立装载期限——装载挂死由宿主 ready 预算兜底（超时 → terminate →
     * killSelf），不加则少一层、两者都不放宽已 ready 后的 1500 ms 程序期限（KD-76 红线）。
     * warm-up 失败（引擎都装载不出）= 进程不可信 → killSelf（与 [fatal] 同型的进程级
     * 恢复），宿主走既有死亡→重建预算→fail-closed 路径，不新增第三条恢复路径。
     */
    private fun warmUpOnJsExecutor() {
        jsExecutor.execute {
            try {
                QuickJsVisualExecutionEngine.warmUpNativeRuntime()
                ready.set(true)
                Log.i(TAG, "visual_js warm-up finished; worker ready for requests")
            } catch (failure: Throwable) {
                Log.w(TAG, "visual_js warm-up failed", failure)
                killSelf("warm-up failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    /**
     * R1 §5.4：unbind 不是同步终止，但它是"移除重启理由"的配套动作。宿主已经不要我们时，
     * 进程没有存在理由——这里自杀，保证不留孤儿进程。
     */
    override fun onUnbind(intent: Intent?): Boolean {
        killSelf("the host unbound the visual_js worker")
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        killSelf("the visual_js service was destroyed")
    }

    private fun killSelf(reason: String) {
        if (!killed.compareAndSet(false, true)) return
        // 安全护栏：若 manifest/合并被改成"同 UID 的兄弟进程"甚至"主进程内服务"，
        // killProcess(myPid()) 会杀掉宿主或兄弟进程。契约设计里的 :visual_js 是
        // isolatedProcess（Uid != applicationInfo.uid）；不是这个形状时拒绝自杀，
        // 由宿主客户端的 workerUid 校验 fail-closed 并解绑。
        // API 28+ 再叠加 Process.isIsolated()：即使某平台 applicationInfo.uid 不是
        // 应用 UID（R1 §9.4 未设备验证的假设），隔离进程仍能自我终止。
        val isIsolatedWorker = Process.myUid() != applicationInfo.uid ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Process.isIsolated())
        if (!isIsolatedWorker) {
            Log.e(TAG, "refusing to kill a non-isolated worker process: $reason")
            return
        }
        Log.w(TAG, "killing :visual_js worker: $reason")
        Process.killProcess(Process.myPid())
    }

    /**
     * 每个 Binder transaction 都查调用 UID（R1 §4.4：onBind 的 calling UID 不可靠，
     * 也不能信 Intent extra）。隔离进程里 `applicationInfo.uid` 仍是应用 UID，而本进程
     * 自己的 UID 是隔离 UID，因此这个比较就是"只允许宿主"。
     */
    private fun requireHost() {
        val caller = Binder.getCallingUid()
        if (caller != applicationInfo.uid) {
            throw SecurityException("visual_js_unauthorized_caller_uid_$caller")
        }
    }

    private val stub = object : IVisualJsService.Stub() {
        override fun protocolVersion(): Int {
            requireHost()
            return VisualJsWire.PROTOCOL_VERSION
        }

        override fun workerUid(): Int {
            requireHost()
            return Process.myUid()
        }

        override fun currentGeneration(): Long {
            requireHost()
            return activeGeneration.get()
        }

        /**
         * 批次 25 T2（KD-76 ①）：warm-up 完成查询。两路、只读原子量、照旧逐事务查宿主
         * UID。ready 是单调量；宿主在握手后轮询本方法（独立预算），true 后才发首个
         * execute。
         */
        override fun isReady(): Boolean {
            requireHost()
            return ready.get()
        }

        override fun initialize(generation: Long): Int {
            requireHost()
            synchronized(initLock) {
                if (abandoned.get()) return VisualJsWire.STATUS_SHUTTING_DOWN
                if (initialized.get()) {
                    return if (activeGeneration.get() == generation) {
                        VisualJsWire.STATUS_ACCEPTED
                    } else {
                        VisualJsWire.STATUS_STALE_GENERATION
                    }
                }
                activeGeneration.set(generation)
                initialized.set(true)
                return VisualJsWire.STATUS_ACCEPTED
            }
        }

        override fun execute(
            generation: Long,
            requestId: String?,
            request: ParcelFileDescriptor?,
            callback: IVisualJsCallback?,
        ): Int {
            requireHost()
            if (request == null || callback == null || requestId.isNullOrEmpty()) {
                request?.let { runCatching { it.close() } }
                return VisualJsWire.STATUS_BAD_REQUEST
            }
            if (abandoned.get()) {
                runCatching { request.close() }
                return VisualJsWire.STATUS_SHUTTING_DOWN
            }
            if (!ready.get()) {
                // 批次 25 T2（KD-76 ①/⑤）：ready 之前不接请求、也不进入 watchdog 路径
                // （请求根本不会排队——"ready 之前不 arm 程序 watchdog"的结构性保证）。
                // ready 是单调量，随批发布的宿主只在 isReady() 轮询为真后发首个请求，
                // 正常流程到不了这里；到得了就说明对端违反 ready 协议，按既有
                // STATUS_SHUTTING_DOWN fail-closed（R1' §2.3-3 保守分支：不新增
                // STATUS_NOT_READY，不与 BUSY 背压语义混淆）。
                runCatching { request.close() }
                return VisualJsWire.STATUS_SHUTTING_DOWN
            }
            if (!initialized.get() || generation != activeGeneration.get()) {
                runCatching { request.close() }
                return VisualJsWire.STATUS_STALE_GENERATION
            }
            if (!inFlight.compareAndSet(false, true)) {
                // 契约 §4：全局一个在飞绘制；忙时回 BUSY 并保留原帧（背压，不是熔断）。
                runCatching { request.close() }
                return VisualJsWire.STATUS_BUSY
            }
            // 设备轨 T1 4/4 红（批次 22 复核 #2）的根因：createReliablePipe 的 read end 带一条
            // 状态 comm 通道。宿主在 execute() 返回后立刻关闭自己那份 read end；服务端稍后在
            // 执行线程里用 AutoCloseInputStream 读到 EOF 时，checkError() 把这次正常关闭读成
            // "Remote side is dead"，于是**已完整读入**的 256 KiB 信封被当成 pipe 读失败。
            // 在 Binder 线程先 dup()：dup 出来的数据 fd 不带 comm 通道，读端独立于宿主那份
            // read end 的关闭时机；原始 PFD 在本线程关闭，不泄漏 fd。
            val workerRequest = try {
                request.dup()
            } catch (failure: IOException) {
                inFlight.set(false)
                runCatching { request.close() }
                return VisualJsWire.STATUS_BAD_REQUEST
            }
            runCatching { request.close() }
            val execution = ExecutionToken(generation, requestId)
            return try {
                jsExecutor.execute { runRequest(execution, workerRequest, callback) }
                VisualJsWire.STATUS_ACCEPTED
            } catch (rejected: RejectedExecutionException) {
                inFlight.set(false)
                runCatching { workerRequest.close() }
                VisualJsWire.STATUS_SHUTTING_DOWN
            }
        }

        override fun terminate(generation: Long) {
            requireHost()
            // 过期 terminate 必须 no-op，绝不能落到新实例（契约 §4 / R1 §4.3）。
            if (generation != activeGeneration.get()) return
            if (!abandoned.compareAndSet(false, true)) return
            killSelf("terminate(generation=$generation) accepted")
        }
    }

    // ---------------------------------------------------------------- 单请求执行

    private fun runRequest(
        execution: ExecutionToken,
        request: ParcelFileDescriptor,
        callback: IVisualJsCallback,
    ) {
        val hostDeath = watchHostDeath(callback)
        if (hostDeath == null) {
            // 宿主 binder 已死：正常路径 killSelf 会回收进程；非隔离误配下 killSelf 拒绝自杀，
            // 这里显式释放单在飞槽位，避免没有真正开始的工作永久占住 BUSY。
            inFlight.set(false)
            runCatching { request.close() }
            return
        }
        try {
            if (abandoned.get()) {
                completeRejected(
                    execution,
                    callback,
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "visual_js is shutting down",
                )
                return
            }
            // 契约 §4：watchdog 在每个 JS 阶段开始前启动。**不**把有界请求读取/Binder
            // 传输计入 1500 ms——读端若被宿主拖住，宿主自己的 2500 ms 期限会
            // terminate(generation)，terminate 在 Binder 线程执行、不排在 JS 队列后面。
            // 期限从真正的 JS 工作（engine.execute）开始；Binder 排队与传输不误杀。
            val read = readBoundedRequest(request)
            if (read !is RequestRead.Bytes) {
                completeRejected(
                    execution,
                    callback,
                    if (read is RequestRead.OverBudget) {
                        VisualExecutionRejection.INPUT_OVER_BUDGET
                    } else {
                        VisualExecutionRejection.ENGINE_CLOSED
                    },
                    if (read is RequestRead.OverBudget) {
                        "request envelope exceeds the " +
                            "${VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES}-byte transaction budget"
                    } else {
                        "request pipe ended before a complete envelope arrived"
                    },
                )
                return
            }

            val visualRequest = try {
                VisualJsProtocol.decodeRequest(read.bytes)
            } catch (overBudget: VisualJsBudgetException) {
                completeRejected(
                    execution,
                    callback,
                    VisualExecutionRejection.INPUT_OVER_BUDGET,
                    overBudget.message ?: "request is over budget",
                )
                return
            } catch (mismatch: VisualJsLimitsMismatchException) {
                completeRejected(
                    execution,
                    callback,
                    VisualExecutionRejection.ENGINE_CLOSED,
                    mismatch.message ?: "host and worker budget tables disagree",
                )
                return
            } catch (protocol: VisualJsProtocolException) {
                completeRejected(
                    execution,
                    callback,
                    VisualExecutionRejection.ENGINE_CLOSED,
                    protocol.message ?: "malformed request envelope",
                )
                return
            }

            if (!execution.active.get()) return

            // JS 阶段从这里开始：服务硬期限只覆盖引擎执行，不覆盖传输。
            // 批次 25 T2（KD-76 ①）：能排进 runRequest 的请求都过了 execute 的 ready 门
            // （ready 单调），装载必已完成——1500 ms 只计真实 JS 工作的语义不变。
            armWatchdog(execution)

            // 每请求一个全新引擎实例：引擎内部的 inFlight/fused 集合不跨请求，
            // #240 挂死的影响面被进程边界截断。
            val engine = try {
                QuickJsVisualExecutionEngine(measurer = UnwiredVisualContentMeasurer)
            } catch (failure: Throwable) {
                fatal(
                    execution,
                    callback,
                    VisualJsWire.FATAL_INTERNAL,
                    "quickjs engine unavailable in :visual_js: ${failure.javaClass.simpleName}",
                )
                return
            }
            val result = try {
                runBlocking { engine.execute(visualRequest) }
            } catch (failure: Throwable) {
                fatal(
                    execution,
                    callback,
                    VisualJsWire.FATAL_INTERNAL,
                    "engine threw outside the port contract: ${failure.javaClass.simpleName}",
                )
                return
            } finally {
                runCatching { engine.close() }
            }

            if (result is VisualExecutionResult.Rejected &&
                (result.reason == VisualExecutionRejection.HARD_DEADLINE_EXCEEDED ||
                    result.reason == VisualExecutionRejection.BUSY)
            ) {
                // R1 §5.5：服务已经拿到单在飞槽位，引擎却回 BUSY/硬期限——说明引擎实例内部
                // 状态已不可信（#240 残留）。不能把这种 BUSY 当正常背压回宿主，否则下一请求
                // 永远 BUSY；进程级恢复是唯一可证明干净的状态。
                fatal(
                    execution,
                    callback,
                    VisualJsWire.FATAL_INTERNAL,
                    "engine reported ${result.reason} after the single-flight slot was acquired: " +
                        result.diagnostics,
                )
                return
            }

            complete(execution, callback, result)
        } catch (failure: Throwable) {
            // 兜底：传输/解码/引擎之外的意外异常不能静默退出 executor 任务而留下
            // inFlight=true（后续请求永远 BUSY）。进程级恢复是唯一可证明干净的状态。
            fatal(
                execution,
                callback,
                VisualJsWire.FATAL_INTERNAL,
                "request handling failed: ${failure.javaClass.simpleName}",
            )
        } finally {
            unwatchHostDeath(callback, hostDeath)
            runCatching { request.close() }
        }
    }

    /**
     * 正常完成/拒绝的收尾：先 CAS 令牌（迟到 watchdog 不得杀下一任务），再置空闲，最后投递。
     * 契约/R1 顺序：先置空闲再投递，避免紧随的 execute 误判 BUSY。
     */
    private fun complete(
        execution: ExecutionToken,
        callback: IVisualJsCallback,
        result: VisualExecutionResult,
    ) {
        if (!execution.active.compareAndSet(true, false)) {
            // watchdog 已经赢：进程正在死，任何结果都不再投递（避免"宣布死亡后又成功"）。
            return
        }
        execution.watchdogFuture?.cancel(false)
        inFlight.set(false)
        deliver(execution, callback, result)
    }

    private fun completeRejected(
        execution: ExecutionToken,
        callback: IVisualJsCallback,
        reason: VisualExecutionRejection,
        message: String,
    ) {
        complete(
            execution,
            callback,
            VisualExecutionResult.Rejected(reason, bounded(message)),
        )
    }

    private fun fatal(
        execution: ExecutionToken,
        callback: IVisualJsCallback,
        code: Int,
        message: String,
    ) {
        val diagnostics = bounded(message)
        // 尽力而为：onFatal 是 oneway，投递后立即 SIGKILL；宿主同时用 Binder death 兜底。
        runCatching {
            callback.onFatal(execution.generation, execution.requestId, code, diagnostics)
        }
        killSelf("fatal: $diagnostics")
    }

    private fun armWatchdog(execution: ExecutionToken) {
        val future = watchdog.schedule(
            {
                if (!execution.active.compareAndSet(true, false)) return@schedule
                if (execution.generation != activeGeneration.get()) return@schedule
                Log.w(
                    TAG,
                    "hard deadline (${VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS} ms) " +
                        "for request ${execution.requestId}; killing the worker",
                )
                killSelf("watchdog hard deadline")
            },
            VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
        execution.watchdogFuture = future
    }

    // ---------------------------------------------------------------- 传输

    private sealed class RequestRead {
        class Bytes(val bytes: ByteArray) : RequestRead()
        object OverBudget : RequestRead()
        object Failed : RequestRead()
    }

    /** 流式有界读取：超过 256 KiB 立即停止，不先无界读入再拒绝（契约 §4）。
     *
     * 调用方必须传入 [ParcelFileDescriptor.dup] 出来的副本（见 execute）：原始 PFD 的
     * reliable-pipe comm 通道会把宿主关闭自己 read end 的正常动作读成 "Remote side is dead"。
     * 这里保留 IOException → Failed 的兜底：真正读不到数据时仍 fail-closed。
     */
    private fun readBoundedRequest(request: ParcelFileDescriptor): RequestRead {
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(request).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
                        return RequestRead.OverBudget
                    }
                    output.write(buffer, 0, read)
                }
                RequestRead.Bytes(output.toByteArray())
            }
        } catch (failure: IOException) {
            RequestRead.Failed
        }
    }

    /**
     * 结果投递：先给宿主 read end（oneway + PFD），再在独立守护线程写。
     * 不能"先写完整结果再回调"——OS pipe 容量有限，256 KiB 结果会阻塞写端。
     */
    private fun deliver(
        execution: ExecutionToken,
        callback: IVisualJsCallback,
        result: VisualExecutionResult,
    ) {
        // 结果信封超预算时不能截断成"正确图"，也不能让 status 继续声称 OK：payload 与
        // status/summary 必须一起换成同一个 INVALID_SCENE 拒绝。
        val (payload, effective) = try {
            VisualJsProtocol.encodeResult(result) to result
        } catch (overBudget: VisualJsBudgetException) {
            val rejection = VisualExecutionResult.Rejected(
                VisualExecutionRejection.INVALID_SCENE,
                bounded(overBudget.message ?: "result envelope is over budget"),
            )
            VisualJsProtocol.encodeRejection(rejection.reason, rejection.diagnostics) to rejection
        }
        val pipe = try {
            ParcelFileDescriptor.createReliablePipe()
        } catch (failure: IOException) {
            fatal(
                execution,
                callback,
                VisualJsWire.FATAL_INTERNAL,
                "could not create the result pipe: ${failure.javaClass.simpleName}",
            )
            return
        }
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        val status = if (effective is VisualExecutionResult.Rendered) {
            VisualJsWire.RESULT_OK
        } else {
            VisualJsWire.RESULT_REJECTED
        }
        val summary = (effective as? VisualExecutionResult.Rejected)?.diagnostics
        try {
            callback.onResult(execution.generation, execution.requestId, status, readEnd, summary)
        } catch (failure: Throwable) {
            runCatching { readEnd.close() }
            runCatching { writeEnd.close() }
            Log.w(TAG, "result delivery failed; the host is gone", failure)
            killSelf("host callback failed: ${failure.javaClass.simpleName}")
            return
        }
        // 宿主在 onResult 里必须先 dup 再读：read end 的 reliable-pipe comm 通道会把本进程
        // 关闭自己 read end 的正常动作读成 "Remote side is dead"（设备探针 A/E）。这里只负责
        // 把自己那份 read end 关掉；数据 fd 已随 oneway 事务复制给宿主。
        runCatching { readEnd.close() }
        Thread({ writeResult(writeEnd, payload) }, "visual-js-result-writer").apply {
            isDaemon = true
            start()
        }
    }

    private fun writeResult(writeEnd: ParcelFileDescriptor, payload: ByteArray) {
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { output ->
                output.write(payload)
                output.flush()
            }
        } catch (failure: IOException) {
            // 宿主已走（超时放弃/进程死亡）：写端 EPIPE。不留孤儿进程。
            Log.w(TAG, "result pipe write failed; the host abandoned the request", failure)
            runCatching { writeEnd.close() }
            killSelf("result pipe write failed")
        }
    }

    private fun watchHostDeath(callback: IVisualJsCallback): IBinder.DeathRecipient? {
        val binder = callback.asBinder()
        val recipient = IBinder.DeathRecipient { killSelf("the host process died") }
        return try {
            binder.linkToDeath(recipient, 0)
            recipient
        } catch (_: android.os.RemoteException) {
            killSelf("the host binder was already dead")
            null
        }
    }

    private fun unwatchHostDeath(
        callback: IVisualJsCallback,
        recipient: IBinder.DeathRecipient,
    ) {
        // unlinkToDeath 未注册且 binder 活时会抛 NoSuchElementException；这里只做尽力清理。
        runCatching { callback.asBinder().unlinkToDeath(recipient, 0) }
    }

    private fun bounded(message: String): String =
        message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)

    private class ExecutionToken(
        val generation: Long,
        val requestId: String,
    ) {
        val active = AtomicBoolean(true)

        @Volatile
        var watchdogFuture: ScheduledFuture<*>? = null
    }

    private companion object {
        const val TAG = "VisualJsService"
    }
}
