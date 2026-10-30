package com.tingyun.smartmistakebook.visualjs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 宿主主进程的 [VisualExecutionEngine] 实现：经 AIDL 调 `:visual_js` 隔离进程（批次 22 T1）。
 *
 * 契约 §4 的 L3（宿主阶段期限，旧文献"第三层"）在本文件：
 * - **不杀任何进程**（宿主对隔离 UID 没有 kill API，R1 §2.3）：期限到只做
 *   `abandon()`（标记 ABANDONED → `terminate(generation)` oneway → `unbindService` →
 *   **保留 death 注册**，以 binder death 事实 + 轮询 `isBinderAlive` 组合确认 ≤2 s）；
 * - 只有确认旧实例终结后才清空 link，由下一次执行扣唯一一次重建预算；死亡未确认才
 *   fail-closed（`unavailable`），不并发放大进程数；
 * - 死亡确认后最多重建一次；重建后的实例再死 → 熔断并不再 bind；
 * - 重建只恢复运行环境，**不重跑**刚才超时/崩溃的源码；`programHash` 熔断到
 *   `explicitUserRetry` 或源码改变（契约 §4:70）；
 * - 旧 generation 的回调、旧 requestId 的结果一律丢弃并关 fd（不把另一张图当旧帧）。
 *
 * 契约 §64 队列/背压（批次 25 T3，[VisualJsFrameQueue]）：全局 1 在飞 + ≤2 待绘 +
 * 同 program latest-wins（被顶掉/超容的调用方回 `BUSY` 背压拒绝）+ 跨 program FIFO。
 * 会话在常驻消费者侧执行，调用方只 `waiter.await()`——排队上界由"1+2 帧 × 会话预算"
 * 约束，不再是旧 `executeMutex` 的无上界挂起。worker 死亡时排队帧清空回 `ENGINE_CLOSED`
 * （R1' §3.2 保守分支，待主会话确认）；调用方取消只出队自己的等待帧（排队帧出队不执行），
 * 取消在飞帧不 abandon worker、不打断在飞执行（会话按自身预算跑完，结果丢弃；主会话已
 * 裁定确认采纳——批次 25 记录 §3.3 ②，批次 27 T4 定稿）。"由可见/交互生命周期限次重新调度"（§64 后半句）
 * 属调用方生命周期层（P-04），不在本文件。
 *
 * 批次 25 T2（KD-76 ①/⑤，R1' §2.3-4）：**握手成功 ≠ 引擎可用**。服务在 onCreate 后
 * 做一次性装载 warm-up，完成才置 ready（AIDL `isReady()`，单调量）。宿主在每次新进程
 * 握手成功后轮询 ready（预算 [READY_TIMEOUT_MILLIS]，独立于 attach 预算与宿主 2500 ms
 * 阶段期限），ready 后才把连接交给首个请求。attach/ready 预算超时是 **loading 形状**：
 * 请求如实折 `ENGINE_CLOSED`（诊断说明 loading/not-ready），**不锁存** `unavailable`、
 * 不消耗死亡重建预算（[lastBindFailedOnLoading] 旗标），下一次 execute 重新 bind +
 * 等 ready——KD-76 ⑤"加载失败不得一次失败即永久 unavailable"；生命周期限次重试属
 * P-04。死亡与结构性失败（bind 失败/协议不匹配/同 UID/死亡未确认）仍按既有语义
 * fail-closed。close() 在首连/ready 等待中立即终结（markDead 完成两个等待点），不无限等。
 *
 * 高危研究项的落点（docs/research/2026-10-26-visualjs-isolated-process.md 复核）：
 * 1. **请求管道死锁**：绝不"先写完整请求再 execute"。本实现先 `execute(...)` 拿到
 *    ACCEPTED，再在 IO 协程写信封；pipe 容量不足时服务端已在并发读，写不会阻塞到宿主
 *    2500 ms 期限。
 * 2. **per-bind 状态**：generation / connection / death 延迟 / deathRecipient / state
 *    全部放在每次 bind 新建的 [Link] 里；旧实例的死亡不会污染新实例，onDeath 会把
 *    `service` 置空并清掉当前 link，不会留下 "Connected 但 NPE" 的窗口。
 * 3. **不可归属失败**：wire 侧保留 [VisualJsWire.REASON_UNKNOWN] 字面量，但 T2 裁定
 *    **不新增**端口枚举 `UNKNOWN`；不可归属失败折成 `INVALID_PROGRAM`，诊断以
 *    `[UNATTRIBUTED]` 前缀区分（`core/data/.../VisualJsFailureClassification.kt`）。
 *
 * **本批不广告 JS**：本类没有生产装配点，工具面/提示词/能力快照零改动。
 * 构造参数 [context] 必须是 application context：引擎生命周期长于任何 Activity，
 * 且 binding 不应随某个页面销毁而失效。
 *
 * 测试可观测性（internal，仪器化用例使用）：连接计数、当前 generation/UID、等待重连。
 * 这些 seam 消灭的失败是"进程层行为只能靠猜"——重建一次、新进程无 BUSY 都由设备用例
 * 直接断言，而不是靠日志推断。
 */
class VisualJsClientEngine(private val context: Context) : VisualExecutionEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 契约 §64 队列/背压（批次 25 T3）：全局 1 在飞 + ≤2 待绘 + 同 program latest-wins +
     * 满→BUSY 保帧 + 跨 program FIFO。会话体在消费者侧执行（[executeFrame]），调用方只
     * `waiter.await()`；锁不再跨会话持有（旧 `executeMutex` 整段串行的根因）。
     */
    private val frameQueue = VisualJsFrameQueue(scope) { frame -> executeFrame(frame) }
    private val connectMutex = Mutex()
    private val linkLock = Any()
    private val closed = AtomicBoolean(false)
    private val fusedDigests: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val random = SecureRandom()
    private val connections = AtomicInteger(0)

    @Volatile
    private var link: Link? = null

    @Volatile
    private var unavailable: Boolean = false

    @Volatile
    private var rebuildConsumed: Boolean = false

    /**
     * 纯 loading 失败旗标（批次 25 T2，KD-76 ⑤）：上一次 bind 以 attach/ready 预算超时
     * 结束（进程未死）时为真。它让"加载慢"**不消耗死亡重建预算、不锁存
     * [unavailable]**——下次 bind 豁免 [rebuildConsumed] 的检查与扣减（R1' §2.3-4：
     * loading 重试的语义落点；生命周期限次属 P-04）。死亡与结构性失败清零它，恢复既有
     * 记账；握手/ready 成功也清零。消费点在 [ensureConnected] 与 [connectLocked]。
     */
    @Volatile
    private var lastBindFailedOnLoading: Boolean = false

    /**
     * 最近一次 [ensureConnected] 失败的可读原因（诊断用途；只在它返回 null 后被读，
     * 每个失败出口都先写它，不存在陈旧值被当成新失败依据的路径）。
     * **它消灭的失败**：KD-76 ⑤ 要求 loading 失败与结构性失败对调用方可区分——只有
     * reason（都是 ENGINE_CLOSED）无法表达，诊断文本承担区分。
     */
    @Volatile
    private var lastConnectFailure: String? = null

    @Volatile
    private var activeCall: ActiveCall? = null

    private enum class LinkState {
        CONNECTING,
        CONNECTED,
        ABANDONED,
        DEAD,
        DEATH_UNCONFIRMED,
    }

    /**
     * 端口实现。整段放到 IO：请求信封序列化（≤80 KiB 源/state 字段）与 Binder 调用都不应在
     * 主线程跑——契约 §4 的宿主阶段期限是软期限，不是允许阻塞 UI 的理由。
     */
    override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult =
        withContext(Dispatchers.IO) { executeOffMainThread(request) }

    private suspend fun executeOffMainThread(
        request: VisualExecutionRequest,
    ): VisualExecutionResult {
        if (closed.get()) {
            return rejected(VisualExecutionRejection.ENGINE_CLOSED, "the visual_js client is closed")
        }
        // 先按字节预算拒绝，再算摘要/序列化：不能先给超预算源码建整棵 JSON 树或整份字节
        // （契约 §4 不先无界建树再拒绝）。服务端仍会再核一遍（纵深防御）。
        val sourceBytes = figureUsageUtf8Bytes(request.sourceJs)
        if (sourceBytes > VisualExecutionBudget.MAX_SOURCE_JS_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "sourceJs is $sourceBytes bytes, over the " +
                    "${VisualExecutionBudget.MAX_SOURCE_JS_BYTES}-byte budget",
            )
        }
        val stateBytes = request.stateJson?.let { figureUsageUtf8Bytes(it) } ?: 0
        if (stateBytes > VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "state is $stateBytes bytes, over the " +
                    "${VisualExecutionBudget.MAX_STATE_JSON_BYTES}-byte budget",
            )
        }
        val digest = figureSha256Hex(request.sourceJs.toByteArray(StandardCharsets.UTF_8))
        if (!request.explicitUserRetry && digest in fusedDigests) {
            return rejected(
                VisualExecutionRejection.PROGRAM_FUSED,
                "program digest ${digest.take(12)} is fused until explicit user retry",
            )
        }
        val envelope = try {
            VisualJsProtocol.encodeRequest(request)
        } catch (failure: RuntimeException) {
            return rejected(
                VisualExecutionRejection.ENGINE_CLOSED,
                "request envelope could not be encoded: ${failure.javaClass.simpleName}",
            )
        }
        if (envelope.size > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "request envelope is ${envelope.size} bytes, over the " +
                    "${VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES}-byte transaction budget",
            )
        }

        // 入队判定（1 在飞 / latest-wins / 满→BUSY）在队列的短临界区内完成；本协程只等
        // 结果。调用方取消：排队帧出队不执行；在飞帧由消费者按自身预算跑完、结果丢弃
        //（不再像旧实现那样 abandon worker——那会连坐顶掉全部排队帧，见 VisualJsFrameQueue KDoc）。
        val waiter = CompletableDeferred<VisualExecutionResult>()
        frameQueue.submit(
            VisualJsFrameQueue.Frame(
                programId = request.programId,
                digest = digest,
                envelope = envelope,
                explicitUserRetry = request.explicitUserRetry,
                waiter = waiter,
            ),
        )
        return waiter.await()
    }

    /**
     * 消费者侧会话体：从旧实现的 `executeMutex.withLock` 原样迁来（含 2500 ms 宿主阶段
     * 期限、超时 abandon、`NonCancellable` 死亡确认全套），只有三点差异：
     * 1. 出队后的**二次熔断复查**（排队期间同源码可能已被硬期限/死亡熔断）；
     * 2. 会话结果走独立的 [sessionResult]：回调/死亡只完成它，调用方取消 waiter 不会
     *    污染超时竞态判定（旧实现 pending 与调用方同生共死，消费者侧必须解耦）；
     * 3. 每帧在会话开始时新取 `requestId`/连接——排队期间可能已经历死亡重建，generation
     *    必须取当前 link 的，而不是入队时刻的。
     */
    private suspend fun executeFrame(frame: VisualJsFrameQueue.Frame) {
        if (closed.get()) {
            frame.waiter.complete(
                rejected(VisualExecutionRejection.ENGINE_CLOSED, "the visual_js client is closed"),
            )
            return
        }
        if (!frame.explicitUserRetry && frame.digest in fusedDigests) {
            frame.waiter.complete(
                rejected(
                    VisualExecutionRejection.PROGRAM_FUSED,
                    "program digest ${frame.digest.take(12)} is fused until explicit user retry",
                ),
            )
            return
        }
        val connection = ensureConnected()
            ?: run {
                frame.waiter.complete(
                    rejected(
                        VisualExecutionRejection.ENGINE_CLOSED,
                        "visual_js unavailable: " +
                            (lastConnectFailure
                                ?: "bind failed, or the previous worker's death was not confirmed"),
                    ),
                )
                return
            }
        val requestId = UUID.randomUUID().toString()
        val sessionResult = CompletableDeferred<VisualExecutionResult>()
        val call = ActiveCall(connection, frame.digest, sessionResult)
        activeCall = call
        val outcome: VisualExecutionResult = try {
            try {
                withTimeout(VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS) {
                    runSession(connection, frame.digest, requestId, frame.envelope, sessionResult)
                }
            } catch (timeout: TimeoutCancellationException) {
                // 竞态：结果与超时同时到；已完成就不误熔断（R1 §4.5）。
                if (sessionResult.isCompleted) {
                    sessionResult.await()
                } else {
                    fusedDigests.add(frame.digest)
                    withContext(NonCancellable) { abandon(connection) }
                    rejected(
                        VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                        "host phase deadline " +
                            "(${VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS} ms) exceeded",
                    )
                }
            } catch (cancel: CancellationException) {
                // 消费者协程被取消（scope 级；调用方取消不再到达这里）：原样重抛，同时停掉
                // 服务里的工作。
                withContext(NonCancellable) { abandon(connection) }
                throw cancel
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
        // 契约 §4：熔断到"用户明确本地重试或源码改变"。一次成功的显式重试即解除熔断。
        if (frame.explicitUserRetry && outcome is VisualExecutionResult.Rendered) {
            fusedDigests.remove(frame.digest)
        }
        frame.waiter.complete(outcome)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // 队列先闩死并清空：排队中的等待者立即回 ENGINE_CLOSED，不再等 link 拆卸。
        // 在飞帧的 waiter 由下方 markDead → handleDeath → activeCall 路径完成（既有行为）。
        frameQueue.close("the visual_js client is closed")
        val current = link
        if (current != null) {
            current.markAbandoned()
            runCatching { current.service?.terminate(current.generation) }
            current.service = null
            runCatching { context.unbindService(current.connection) }
            // close 必须完成 link 的两个等待点，不能只解绑：
            // ① 首连/重建中的 connectLocked 正等 connected.await()（预算 15 s/10 s），
            //    不完成就要一直等到预算耗尽才回 ENGINE_CLOSED；
            // ② 在飞请求的 runSession 正等结果/死亡，不完成只能等宿主 2500 ms 期限。
            // markDead(unexpected=false) 是既有幂等死亡路径：完成 death/connected、
            // 终结 activeCall、解注册并解绑；closed=true 保证不会触发重建。
            current.markDead("the client was closed", unexpected = false)
        }
        synchronized(linkLock) { link = null }
    }

    // ---------------------------------------------------------------- 一次会话

    /**
     * 一次请求的完整会话：create pipe → **先 execute** → 再写请求 → 等结果。
     * 顺序是 R1 高危项（请求写满 pipe 阻塞）的直接修复。
     */
    private suspend fun runSession(
        connection: Link,
        digest: String,
        requestId: String,
        envelope: ByteArray,
        pending: CompletableDeferred<VisualExecutionResult>,
    ): VisualExecutionResult = coroutineScope {
        val pipe = try {
            ParcelFileDescriptor.createReliablePipe()
        } catch (failure: IOException) {
            return@coroutineScope rejected(
                VisualExecutionRejection.ENGINE_CLOSED,
                "could not create the request pipe: ${failure.javaClass.simpleName}",
            )
        }
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        val callback = newCallback(connection, digest, requestId, pending)
        val accepted: Int = try {
            val service = connection.service
            if (service == null) {
                runCatching { readEnd.close() }
                runCatching { writeEnd.close() }
                return@coroutineScope rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "the visual_js connection is no longer usable",
                )
            }
            try {
                service.execute(connection.generation, requestId, readEnd, callback)
            } catch (failure: RemoteException) {
                connection.markDead("execute failed with RemoteException", unexpected = true)
                runCatching { readEnd.close() }
                runCatching { writeEnd.close() }
                return@coroutineScope rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "visual_js died while accepting the request",
                )
            } catch (failure: SecurityException) {
                runCatching { readEnd.close() }
                runCatching { writeEnd.close() }
                unavailable = true
                return@coroutineScope rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "visual_js rejected the caller identity: ${failure.message}",
                )
            } catch (failure: RuntimeException) {
                // 端口契约要求不抛库异常：Binder 运行时错误（非 Security/Remote）一律折成
                // 有界致命拒绝；传输层坏掉按进程不可信处理（会消耗一次重建预算）。
                runCatching { readEnd.close() }
                runCatching { writeEnd.close() }
                connection.markDead(
                    "execute threw ${failure.javaClass.simpleName}",
                    unexpected = true,
                )
                return@coroutineScope rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "visual_js request transport failed: ${failure.javaClass.simpleName}",
                )
            }
        } finally {
            // 服务端已在 execute 事务里 dup 了 read end；本地副本可以立刻关。
            runCatching { readEnd.close() }
        }

        when (accepted) {
            VisualJsWire.STATUS_ACCEPTED -> Unit
            VisualJsWire.STATUS_BUSY -> {
                runCatching { writeEnd.close() }
                return@coroutineScope rejected(
                    VisualExecutionRejection.BUSY,
                    "visual_js is busy with another transaction",
                )
            }

            VisualJsWire.STATUS_STALE_GENERATION -> {
                runCatching { writeEnd.close() }
                unavailable = true
                withContext(NonCancellable) { abandon(connection) }
                return@coroutineScope rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "visual_js reported a stale generation",
                )
            }

            VisualJsWire.STATUS_SHUTTING_DOWN -> {
                runCatching { writeEnd.close() }
                unavailable = true
                withContext(NonCancellable) { abandon(connection) }
                return@coroutineScope rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "visual_js is shutting down",
                )
            }

            else -> {
                runCatching { writeEnd.close() }
                unavailable = true
                return@coroutineScope rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "visual_js rejected the request envelope (status $accepted)",
                )
            }
        }

        val writeJob: Job = launch(Dispatchers.IO) { writeEnvelope(writeEnd, envelope) }
        try {
            val result = pending.await()
            if (result is VisualExecutionResult.Rejected &&
                result.reason == VisualExecutionRejection.HARD_DEADLINE_EXCEEDED
            ) {
                fusedDigests.add(digest)
            }
            result
        } finally {
            // 超时/取消/进程死亡时关闭写端，解除可能阻塞在 pipe 满的写协程。
            runCatching { writeEnd.close() }
            writeJob.cancel()
        }
    }

    private fun newCallback(
        connection: Link,
        digest: String,
        requestId: String,
        pending: CompletableDeferred<VisualExecutionResult>,
    ): IVisualJsCallback = object : IVisualJsCallback.Stub() {
        override fun onResult(
            generation: Long,
            callbackRequestId: String?,
            status: Int,
            payload: ParcelFileDescriptor?,
            diagnostics: String?,
        ) {
            // 旧 generation / 旧请求 / 非 worker UID 的结果：丢弃并关 fd（不误当旧帧）。
            // 三重身份核对抽成 [VisualJsResultFrameFilter]（可 JVM 负向单测，R1' §4 A-12/A-13）。
            if (!VisualJsResultFrameFilter.accepts(
                    frameGeneration = generation,
                    frameRequestId = callbackRequestId,
                    callerUid = Binder.getCallingUid(),
                    linkGeneration = connection.generation,
                    requestId = requestId,
                    workerUid = connection.workerUid,
                )
            ) {
                runCatching { payload?.close() }
                return
            }
            // Binder 回调线程只入队；PFD 读取放 IO，避免占住 Binder 线程。
            scope.launch {
                try {
                    val bytes = payload?.let { readResultPayload(it) }
                    if (!pending.isCompleted) {
                        pending.complete(VisualJsProtocol.decodeResult(status, bytes, diagnostics))
                    }
                } catch (failure: Throwable) {
                    // 结果处理异常不能冒泡到宿主默认异常处理器（会杀掉主进程）：
                    // 折成有界致命拒绝，等待方仍能返回。
                    if (!pending.isCompleted) {
                        pending.complete(
                            rejected(
                                VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                                "visual_js result handling failed: ${failure.javaClass.simpleName}",
                            ),
                        )
                    }
                }
            }
        }

        override fun onFatal(
            generation: Long,
            callbackRequestId: String?,
            code: Int,
            diagnostics: String?,
        ) {
            if (!VisualJsResultFrameFilter.accepts(
                    frameGeneration = generation,
                    frameRequestId = callbackRequestId,
                    callerUid = Binder.getCallingUid(),
                    linkGeneration = connection.generation,
                    requestId = requestId,
                    workerUid = connection.workerUid,
                )
            ) {
                return
            }
            fusedDigests.add(digest)
            if (!pending.isCompleted) {
                pending.complete(
                    rejected(
                        VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                        "visual_js fatal (code $code): ${diagnostics.orEmpty()}",
                    ),
                )
            }
        }
    }

    /**
     * 读结果 payload 前先 [ParcelFileDescriptor.dup]：结果的 reliable-pipe comm 通道会把
     * worker 关闭自己 read end 的正常动作读成 `IOException: Remote side is dead`（设备
     * 探针 A/E：数据已完整缓冲仍抛）。dup 出的数据 fd 不带 comm 通道；真正截断的 payload
     * 仍会在 JSON 解码处被拒绝，不会把半截场景当成功。
     */
    private suspend fun readResultPayload(payload: ParcelFileDescriptor): ByteArray? =
        withContext(Dispatchers.IO) {
            val detached = try {
                payload.dup()
            } catch (failure: IOException) {
                null
            } finally {
                runCatching { payload.close() }
            }
            if (detached == null) return@withContext null
            try {
                ParcelFileDescriptor.AutoCloseInputStream(detached).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
                            // 结果信封超预算：按截断/不可信处理，不把半截 JSON 当场景。
                            return@withContext null
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            } catch (failure: IOException) {
                null
            }
        }

    private suspend fun writeEnvelope(writeEnd: ParcelFileDescriptor, envelope: ByteArray) {
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { output ->
                output.write(envelope)
                output.flush()
            }
        } catch (failure: IOException) {
            // 服务端拒绝/死亡/超时后关闭读端：写端 EPIPE 属预期，由等待侧的超时/死亡处理。
            runCatching { writeEnd.close() }
        }
    }

    // ---------------------------------------------------------------- 连接与死亡

    private suspend fun ensureConnected(): Link? = connectMutex.withLock {
        if (closed.get()) {
            lastConnectFailure = "the visual_js client is closed"
            return@withLock null
        }
        if (unavailable) {
            lastConnectFailure =
                "the engine was latched unavailable by an earlier structural failure " +
                    "(bind failed, protocol mismatch, same UID, or an unconfirmed death)"
            return@withLock null
        }
        val existing = link
        if (existing != null && existing.state == LinkState.CONNECTED) {
            return@withLock existing
        }
        // 预算已用尽而 link 为空（超时 abandon / 死亡清空）时不得再 bind：否则连续宿主阶段
        // 超时可以无限新建隔离进程，契约"确认旧实例终结后最多重建一次"不成立。
        // 纯 loading 超时（attach/ready 预算、进程未死）是唯一豁免：不消耗重建预算、不锁存
        //（KD-76 ⑤ / R1' §2.3-4），由 lastBindFailedOnLoading 表达。
        if (rebuildConsumed && !lastBindFailedOnLoading) {
            if (existing != null) dispose(existing)
            unavailable = true
            lastConnectFailure = "the single rebuild budget was already consumed by an earlier death"
            return@withLock null
        }
        if (existing != null) {
            dispose(existing)
        }
        connectLocked()
    }

    /**
     * 每次 bind 新建 Link（generation/connection/death/recipient 一体）。禁止复用旧实例
     * 的死亡信号：`CompletableDeferred` 一旦完成，重建后 `abandon()` 的等待会立刻误判
     * "新实例已死"（R1 复核高危项 2）。
     *
     * 重建预算在这里统一扣：`connections` 已 >0 说明本次不是首连，而是"旧实例终结后的
     * 重建"。以前只有死亡通知路径扣预算，宿主阶段超时走 [abandon] 时不扣，导致
     * `terminate → 死亡确认 → link=null → 下次 execute 再 bind` 可以无限重复。
     * 调用方（[ensureConnected] / [rebuildAfterDeath]）都持有 [connectMutex]，检查与置位
     * 在同一临界区内，不会两个路径同时各 bind 一个进程。
     */
    private suspend fun connectLocked(): Link? {
        if (closed.get()) {
            lastConnectFailure = "the visual_js client is closed"
            return null
        }
        if (unavailable) {
            lastConnectFailure =
                "the engine was latched unavailable by an earlier structural failure"
            return null
        }
        if (connections.get() > 0) {
            if (rebuildConsumed && !lastBindFailedOnLoading) {
                unavailable = true
                lastConnectFailure = "the single rebuild budget was already consumed by an earlier death"
                return null
            }
            // loading 超时后的重试 bind 不扣重建预算（批次 25 T2，KD-76 ⑤）——否则第二次
            // loading 超时就会把引擎永久锁存，与 R1' §2.3-4 的重试语义矛盾。
            if (!lastBindFailedOnLoading) {
                rebuildConsumed = true
            }
        }
        val generation = newGeneration()
        val candidate = Link(generation)
        link = candidate
        val intent = Intent(context, VisualJsService::class.java)
        val bound = try {
            context.bindService(intent, candidate.connection, Context.BIND_AUTO_CREATE)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "bindService threw", failure)
            false
        }
        if (!bound) {
            candidate.state = LinkState.DEAD
            synchronized(linkLock) { if (link === candidate) link = null }
            unavailable = true
            lastConnectFailure = "bindService returned false for :visual_js"
            return null
        }
        // 首连与重建用不同预算：冷启动要把隔离进程 fork 起来并装载 libquickjs，设备实测
        // 4.6–7.5 s（E-01d）；重建时原生库已在页缓存里（E-01b 0.6–0.9 s），但"已连接过、
        // 之后系统再次进入重负载"没有实测，R1 §6.2 建议给重建留 10 s。统一 5 s 会在冷启动
        // 把加载态误判成结构性失败；统一 15 s 又会让真正卡死的重建拖太久。
        val connectBudget = if (connections.get() == 0) {
            FIRST_CONNECT_TIMEOUT_MILLIS
        } else {
            RECONNECT_TIMEOUT_MILLIS
        }
        val connected = try {
            withTimeoutOrNull(connectBudget) { candidate.connected.await() } ?: false
        } catch (cancel: CancellationException) {
            throw cancel
        }
        // 死亡优先于超时判定：markDead 会提前完成 connected=false，不能把"握手期间死亡"
        // 记成 attach 预算超时（两者的收尾与后续记账完全不同）。
        if (!connected && candidate.death.isCompleted) {
            // 握手期间死亡：markDead/handleDeath 已按死亡语义处理（可能已排重建）。
            candidate.markAbandoned()
            candidate.service = null
            runCatching { context.unbindService(candidate.connection) }
            unlinkDeath(candidate)
            synchronized(linkLock) { if (link === candidate) link = null }
            lastBindFailedOnLoading = false
            unavailable = true
            lastConnectFailure = "the visual_js worker died during the handshake"
            return null
        }
        if (!connected) {
            // attach 预算内未连上（E-01d 形状）：loading，不是结构性失败。批次 24 曾把
            // 该形状永久锁存；批次 25 T2 起如实 FAILED、不锁存、不扣重建预算。解绑会触发
            // 服务 onDestroy→killSelf，不留孤儿进程；下一次 execute 重新 bind + 等 ready。
            // 晚到的 attach 由 Link.abandonedForLoading 分支收尾，不锁存。
            // 写序敏感（复核 1 轮 TOCTOU 修复）：先置 abandonedForLoading 再 markAbandoned。
            // 两者是不同 volatile 变量、读侧（onServiceConnected 的 ABANDONED 分支）不持
            // stateLock 且读序固定先 state 后 flag；若写序反置，晚到 attach 可读到 ABANDONED
            // 却见 flag=false，按 markDead(unexpected=false) 收尾——handleDeath 在 ABANDONED
            // 短路之前就置 unavailable=true，首连 loading 超时后的重试被永久锁存（KD-76 ⑤
            // 回归形状，正是 onServiceConnected 注释声称已消灭的失败）。volatile 写序保证：
            // 读者见到 ABANDONED 时必已见到 flag=true；flag 先行不引入新害——state 仍为
            // CONNECTING 的晚到 attach 落入正常握手，由 markConnected 锁内复查 ABANDONED 拒收。
            candidate.abandonedForLoading = true
            candidate.markAbandoned()
            candidate.service = null
            runCatching { context.unbindService(candidate.connection) }
            unlinkDeath(candidate)
            synchronized(linkLock) { if (link === candidate) link = null }
            lastBindFailedOnLoading = true
            lastConnectFailure =
                "visual_js attach did not complete within $connectBudget ms (still loading); " +
                    "the request failed without latching the engine"
            return null
        }
        // isConnected() 在同一把锁内复查 dead：即使 connected.complete(true) 后死亡通知
        // 立刻到达，也不会把 DEAD link 当连接返回（见 Link.markConnected）。
        if (!candidate.isConnected()) {
            candidate.markAbandoned()
            candidate.service = null
            runCatching { context.unbindService(candidate.connection) }
            unlinkDeath(candidate)
            synchronized(linkLock) { if (link === candidate) link = null }
            unavailable = true
            lastConnectFailure = "the visual_js worker died during the handshake"
            return null
        }
        if (closed.get()) {
            // close() 与握手竞态：close 先看到 link==null（死亡已清空）而不会再解绑这个
            // 新实例；这里补上，避免关闭后泄漏一条 binding/进程。
            candidate.markAbandoned()
            runCatching { candidate.service?.terminate(candidate.generation) }
            candidate.service = null
            runCatching { context.unbindService(candidate.connection) }
            unlinkDeath(candidate)
            synchronized(linkLock) { if (link === candidate) link = null }
            lastConnectFailure = "the visual_js client was closed during the handshake"
            return null
        }
        // 批次 25 T2（KD-76 ①/⑤）：握手成功 ≠ 引擎可用——服务在新进程里做一次性装载
        // warm-up，完成才置 ready（单调）。宿主在此轮询 isReady()，预算独立于 attach
        // 预算与 2500 ms 宿主阶段期限。
        if (!awaitWorkerReady(candidate)) {
            if (closed.get()) {
                // close() 在 ready 等待中到达：markDead(unexpected=false) 已收尾（terminate/
                // unbind/两个等待点），这里补清引用即可。
                synchronized(linkLock) { if (link === candidate) link = null }
                lastConnectFailure = "the visual_js client was closed while waiting for worker ready"
                return null
            }
            if (candidate.death.isCompleted) {
                // warm-up 失败等自杀：handleDeath 已按死亡语义处理（重建预算照常记账）。
                // 不算 loading、不豁免下一次 bind 的预算检查。
                synchronized(linkLock) { if (link === candidate) link = null }
                lastBindFailedOnLoading = false
                lastConnectFailure = "the visual_js worker died before it became ready (warm-up failure?)"
                return null
            }
            // 纯 loading 超时：进程活着但 warm-up 未在预算内完成。terminate + unbind（服务
            // 自杀）→ 复用 abandon 的死亡确认（≤2 s；未确认才按既有 fail-closed）。不锁存
            // unavailable、不扣重建预算：下一次 execute 重新 bind + 等 ready（KD-76 ⑤）。
            // 旗标必须在 abandon **之后**置位：abandon 内部经 markDead 触发 handleDeath，
            // 后者会清零本旗标（死亡 invalidate 豁免）——而这里的死亡是我们主动 terminate
            // 的结果，整体 bind 的分类仍是 loading，豁免必须保留到下一次 bind。
            abandon(candidate)
            lastBindFailedOnLoading = true
            lastConnectFailure =
                "visual_js worker did not become ready within ${readyBudgetMillis()} ms " +
                    "(warm-up still loading); the request failed without latching the engine"
            return null
        }
        lastBindFailedOnLoading = false
        lastConnectFailure = null
        return candidate
    }

    /**
     * 服务端引擎 ready 等待（批次 25 T2，KD-76 ①/⑤）：轮询 AIDL `isReady()` 直到翻转或
     * 预算到点。返回 false 时的三种事实由调用方区分：[closed]（客户端已关）、
     * `candidate.death`（warm-up 失败自杀等）、纯超时（loading，进程仍活）。
     *
     * ready 是单调量（服务进程内 false→true 不回退），因此"已连接 link 复用"路径不需要
     * 复查——只有本函数（每次新进程 bind 后恰一次）等待它。
     */
    private suspend fun awaitWorkerReady(candidate: Link): Boolean {
        val service = candidate.service ?: return false
        val deadline = SystemClock.uptimeMillis() + readyBudgetMillis()
        while (SystemClock.uptimeMillis() < deadline) {
            if (closed.get() || candidate.death.isCompleted) return false
            val readyNow = try {
                service.isReady()
            } catch (_: RemoteException) {
                return false
            } catch (_: RuntimeException) {
                return false
            }
            if (readyNow) return true
            delay(READY_POLL_INTERVAL_MILLIS)
        }
        // 预算边界最后一次判定：到点瞬间翻转不算超时（不放宽预算，只是不做时钟截断）。
        if (closed.get() || candidate.death.isCompleted) return false
        return try {
            service.isReady()
        } catch (_: RemoteException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun readyBudgetMillis(): Long = readyTimeoutOverrideForTest ?: READY_TIMEOUT_MILLIS

    private suspend fun abandon(link: Link) {
        if (link.state == LinkState.ABANDONED) return
        link.markAbandoned()
        // death 注册必须保留到确认完成：markDead 是 link.death 的唯一完成点，先 unlinkDeath
        // 会把 binderDied 从确认路径上摘掉（批次 23 E-01c 的结构性红）。unbindService 只
        // 移除 AMS 的重启理由、不是同步终止，也不会撤销已注册的 death 通知。
        runCatching { link.service?.terminate(link.generation) }
        link.service = null
        runCatching { context.unbindService(link.connection) }
        val confirmed = awaitDeathConfirmation(link)
        if (!confirmed) {
            // 契约 §4：死亡未确认时展示 fallback，不能并发放大进程数。
            link.state = LinkState.DEATH_UNCONFIRMED
            unavailable = true
            // 明确记录策略：未确认即 fail-closed（不是"已确认"，只是不再并发放大进程数），随后解注册；
            // 迟到的死亡不被"丢弃"——仍走 markDead/handleDeath 补完等待者，重建被 fail-closed 门挡下。
            Log.w(
                TAG,
                "visual_js death was not confirmed within " +
                    "${DEATH_CONFIRM_TIMEOUT_MILLIS} ms; fail-closed (a late death is still " +
                    "processed but cannot trigger a rebuild)",
            )
        }
        // 确认后事件已到；未确认时已 fail-closed，保留注册只会让迟到的 markDead 在
        // unavailable 状态下再跑一次 handleDeath。两条路径都在此解注册，避免接收器泄漏。
        unlinkDeath(link)
        synchronized(linkLock) { if (this.link === link) this.link = null }
    }

    /**
     * 旧实例终结的组合判据（R1 推荐）：**死亡事实优先**（[Link.death] 由 [Link.markDead]
     * 完成，含 `binderDied`/`onServiceDisconnected`），**轮询 [IBinder.isBinderAlive] 兜底**
     * 回调丢失，最后以 [DEATH_CONFIRM_TIMEOUT_MILLIS] 为界。
     *
     * **它消灭的失败**：只听 `link.death` 时，死亡通知偶发丢失会让客户端在服务已 SIGKILL
     * 后仍判"未确认"并永久 fail-closed；只轮询而不保留注册则可能永远读到 `mAlive=true`
     * （R1 §4.2：未注册 death 且没有失败事务时，缓存位不会因远端进程死亡自动清零）。
     * 组合判据让两条独立事实互为兜底，仍不并发新建进程（未确认不重建）。
     *
     * 预算 2 s 是产品选择，不是平台 SLA：AOSP 不承诺 `binderDied` 的延迟上界
     * （仓库也无毫秒级 death 延迟实测；E-01b 的 16 ms 是 attach→SIGKILL，不是 death 延迟）。
     */
    private suspend fun awaitDeathConfirmation(link: Link): Boolean {
        if (link.death.isCompleted) return true
        val binder = link.binder
        // 竞态：markDead 可能在上面两个读之间完成 death 并让 handleDeath 清空 binder。
        // 这里补一次死亡事实判定，不能把"已确认死亡但注册已摘除"误判成未确认。
        if (binder == null) return link.death.isCompleted
        val deadline = SystemClock.uptimeMillis() + DEATH_CONFIRM_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (link.death.isCompleted) return true
            if (!binder.isBinderAlive) {
                // 死亡通知丢失时的本地事实。走同一个 markDead（CAS 幂等）：完成 death、
                // 关掉在飞请求；state 已是 ABANDONED，handleDeath 不会为 abandon 自动重建。
                link.markDead("binder is no longer alive", unexpected = true)
                return true
            }
            delay(DEATH_POLL_INTERVAL_MILLIS)
        }
        if (link.death.isCompleted) return true
        if (!binder.isBinderAlive) {
            // 最后一刻翻转也必须先记死亡事实，否则 link.death/activeCall 可能永远不完成
            //（R1 §4.4 待收口点 1）。
            link.markDead("binder is no longer alive", unexpected = true)
            return true
        }
        return false
    }

    private fun dispose(dead: Link) {
        dead.markAbandoned()
        runCatching { dead.service?.terminate(dead.generation) }
        dead.service = null
        runCatching { context.unbindService(dead.connection) }
        unlinkDeath(dead)
        synchronized(linkLock) { if (link === dead) link = null }
    }

    private fun handleDeath(deadLink: Link, cause: String, unexpected: Boolean) {
        // 死亡 invalidate "上一次是纯 loading 超时"的豁免：下一次 bind 恢复既有重建预算
        // 记账（批次 25 T2——死亡是危险事实，loading 豁免只属于"进程未死"的加载超时）。
        // 批次 25 主会话收口（R15 裁定）：清零从本函数顶部移到 ABANDONED 短路**之后**——
        // connectLocked 的 loading 超时路径是"abandon() → 置位豁免"两步，若清零留在顶部，
        // binder 线程并发的 handleDeath 可在置位之后执行并把豁免清掉，重建 warm-up 再超时
        // 时命中 rebuildConsumed && !flag → 永久锁存（复核 R15）。ABANDONED 死亡是我们
        // 主动 terminate 的 loading 收尾，豁免必须保留；真实（非预期）死亡在短路之后仍会
        // 清零，预算记账语义不变。volatile 单变量无写序问题：清零只剩这一个写点。
        // R1' §3.2 保守分支（待主会话确认）：worker 死亡时清空待绘队列，全部 ENGINE_CLOSED，
        // 不把排队帧留给不确定性未收敛的重建窗口；在飞帧由 activeCall 路径收尾。
        // dropPending 不闩死队列：重建成功后新帧照常入队。
        frameQueue.dropPending(
            VisualExecutionRejection.ENGINE_CLOSED,
            "visual_js worker died ($cause); the queued frame was dropped",
        )
        synchronized(linkLock) {
            if (link === deadLink) link = null
        }
        unlinkDeath(deadLink)
        // 关键：unbind 移除 AMS 的重启理由；不 unbind 时系统可能在宿主决定重建前又把
        // 服务拉起来（R1 §5.4，AndroidX PDF 同型注释）。
        runCatching { context.unbindService(deadLink.connection) }

        val call = activeCall
        if (call != null && call.link === deadLink && !call.pending.isCompleted) {
            fusedDigests.add(call.digest)
            call.pending.complete(
                rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "visual_js worker died: $cause",
                ),
            )
        }
        if (!unexpected) {
            // 握手失败 / onNullBinding：重试同一个坏配置没有意义，fail-closed。
            unavailable = true
            return
        }
        if (closed.get() || deadLink.state == LinkState.ABANDONED) return
        // 真实（非预期）死亡：到这里才清 loading 豁免（R15 裁定，见本函数头注释）。
        lastBindFailedOnLoading = false
        // 非预期死亡：熔断当前程序，并在确认死亡后最多重建一次。
        if (rebuildConsumed) {
            // 重建预算已用完：直接在死亡线程上 fail-closed，避免"等待重建协程再置
            // unavailable"期间的下一次 execute 又 bind 出一个新实例（并发放大进程数）。
            unavailable = true
            return
        }
        scope.launch { rebuildAfterDeath() }
    }

    private suspend fun rebuildAfterDeath() {
        connectMutex.withLock {
            if (closed.get() || unavailable) return
            if (link?.state == LinkState.CONNECTED) return
            if (rebuildConsumed) {
                unavailable = true
                return
            }
            // 预算由 connectLocked 统一扣（它知道这是不是首次连接）。
            if (connectLocked() == null) {
                unavailable = true
            }
        }
    }

    private fun newGeneration(): Long {
        while (true) {
            val candidate = random.nextLong()
            if (candidate != 0L) return candidate
        }
    }

    private fun unlinkDeath(link: Link) {
        val binder: IBinder = link.binder ?: return
        link.binder = null
        // unlinkToDeath 未注册且 binder 活时抛 NoSuchElementException。
        runCatching { binder.unlinkToDeath(link.deathRecipient, 0) }
    }

    private fun rejected(
        reason: VisualExecutionRejection,
        message: String,
    ): VisualExecutionResult = VisualExecutionResult.Rejected(
        reason,
        message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS),
    )

    private inner class Link(val generation: Long) {
        val death = CompletableDeferred<Unit>()
        val connected = CompletableDeferred<Boolean>()

        @Volatile
        var state: LinkState = LinkState.CONNECTING

        @Volatile
        var service: IVisualJsService? = null

        @Volatile
        var binder: IBinder? = null

        @Volatile
        var workerUid: Int = -1

        /**
         * 本 link 因 **loading 超时**（attach/ready 预算、进程未死）被主动放弃（批次 25 T2，
         * KD-76 ⑤）。晚到的 onServiceConnected 据此区分两类放弃：loading 放弃后的晚到连接
         * 不是"坏配置"（重试正是本设计语义）→ 按 markDead(unexpected=true) 收尾，
         * handleDeath 在 ABANDONED 上短路，**不锁存 unavailable、不触发重建**；其余放弃
         * （close/结构失败）保持既有 unexpected=false → 锁存语义。
         */
        @Volatile
        var abandonedForLoading: Boolean = false

        private val dead = AtomicBoolean(false)
        private val stateLock = Any()

        val deathRecipient = IBinder.DeathRecipient {
            markDead("binder died", unexpected = true)
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    markDead("onServiceConnected with a null binder", unexpected = false)
                    return
                }
                if (dead.get() || state == LinkState.ABANDONED || closed.get()) {
                    // ABANDONED 之后到达的连接不得采纳：立刻终止并解绑（R1 §4.5）。
                    // loading 放弃（attach/ready 超时）后的晚到连接不是"坏配置"：unexpected=true
                    // 让 handleDeath 在 ABANDONED 上短路，不锁存 unavailable（批次 25 T2，
                    // KD-76 ⑤——否则首连超时的重试会被 1 s 后的晚到 attach 锁存）。
                    val late = IVisualJsService.Stub.asInterface(binder)
                    runCatching { late.terminate(generation) }
                    runCatching { context.unbindService(this) }
                    markDead(
                        if (abandonedForLoading) {
                            "a late connection arrived after a loading abandonment"
                        } else {
                            "a late connection arrived after abandonment"
                        },
                        // unexpected=true + ABANDONED → handleDeath 在 ABANDONED 上短路：
                        // 不锁存、不重建。非 loading 放弃保持既有 unexpected=false → 锁存。
                        unexpected = abandonedForLoading,
                    )
                    return
                }
                try {
                    binder.linkToDeath(deathRecipient, 0)
                } catch (_: RemoteException) {
                    markDead("linkToDeath failed", unexpected = true)
                    return
                }
                this@Link.binder = binder
                try {
                    val candidate = IVisualJsService.Stub.asInterface(binder)
                    if (candidate.protocolVersion() != VisualJsWire.PROTOCOL_VERSION) {
                        markDead("protocol version mismatch", unexpected = false)
                        return
                    }
                    val initializeStatus = candidate.initialize(generation)
                    if (initializeStatus != VisualJsWire.STATUS_ACCEPTED) {
                        markDead("initialize returned $initializeStatus", unexpected = false)
                        return
                    }
                    val uid = candidate.workerUid()
                    if (uid == Process.myUid()) {
                        // 宿主与 worker 同 UID = 隔离没生效。fail-closed，不把它当隔离进程用。
                        markDead(
                            "the worker shares the host UID (android:isolatedProcess is not in effect)",
                            unexpected = false,
                        )
                        return
                    }
                    if (!markConnected(candidate, uid)) {
                        // 握手期间已死亡/被 close/被 abandon：不得把 dead link 发布成
                        // CONNECTED（旧实现会在这里无条件覆盖 handleDeath 写入的 DEAD）。
                        markDead("connection died during the handshake", unexpected = true)
                        return
                    }
                } catch (_: RemoteException) {
                    markDead("handshake failed with RemoteException", unexpected = true)
                } catch (failure: RuntimeException) {
                    markDead(
                        "handshake failed: ${failure.javaClass.simpleName}",
                        unexpected = false,
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                markDead("onServiceDisconnected", unexpected = true)
            }

            override fun onBindingDied(name: ComponentName?) {
                markDead("onBindingDied", unexpected = true)
            }

            override fun onNullBinding(name: ComponentName?) {
                // 永远不可用：unbind 仍然必须做（官方 ServiceConnection 文档）。
                markDead("onNullBinding", unexpected = false)
            }
        }

        /**
         * 把一次成功握手发布成 CONNECTED，并与 [markDead] 串行化。
         *
         * **它消灭的失败**：旧实现先读 `dead`/`state`、再无条件写 `state = CONNECTED`。
         * 如果 `markDead` 恰好在这两步之间赢（binderDied/onServiceDisconnected 可能在握手
         * 返回后立即到达），`handleDeath` 已把 link 清空并写 DEAD，随后仍被覆盖成 CONNECTED；
         * `connectLocked` 只看 `state`，会把已死 link 当连接返回，导致一次有界 HARD_DEADLINE
         * 拒绝和一次多余重建。这里让"检查死亡 → 发布连接"在同一把锁内完成，`markDead`
         * 先把 `dead` CAS 置真再拿同一把锁，因此只有一方能赢。
         *
         * @return true 表示已发布；false 表示 link 已死/已 abandon/客户端已 close，调用方
         * 必须走 [markDead] 清理，不能把服务当作可用连接。
         */
        fun markConnected(candidate: IVisualJsService, uid: Int): Boolean =
            synchronized(stateLock) {
                if (dead.get() || state == LinkState.ABANDONED || closed.get()) {
                    return@synchronized false
                }
                workerUid = uid
                service = candidate
                state = LinkState.CONNECTED
                // 与 state 同时记账：awaitConnectedForTest 看到 CONNECTED 时计数已包含本次。
                connections.incrementAndGet()
                if (!connected.isCompleted) connected.complete(true)
                true
            }

        /** 与 [markConnected] 使用同一把锁，杜绝"检查后写 ABANDONED"与发布 CONNECTED 交错。 */
        fun markAbandoned() {
            synchronized(stateLock) { state = LinkState.ABANDONED }
        }

        /** 与 [markDead] 的 state 写入互斥，避免读到"已死但 state 仍是 CONNECTED"的瞬间。 */
        fun isConnected(): Boolean = synchronized(stateLock) {
            !dead.get() && state == LinkState.CONNECTED
        }

        fun markDead(cause: String, unexpected: Boolean) {
            if (!dead.compareAndSet(false, true)) return
            // dead 先置真，再拿 stateLock 改写 state/service：markConnected 在锁内复查
            // dead，看到 true 就不会发布连接。
            synchronized(stateLock) {
                if (state != LinkState.ABANDONED) state = LinkState.DEAD
                service = null
            }
            if (!death.isCompleted) death.complete(Unit)
            if (!connected.isCompleted) connected.complete(false)
            handleDeath(this, cause, unexpected)
        }
    }

    private inner class ActiveCall(
        val link: Link,
        val digest: String,
        val pending: CompletableDeferred<VisualExecutionResult>,
    )

    // ---------------------------------------------------------------- 测试 seam

    internal suspend fun connectForTest(): Boolean = ensureConnected() != null

    /**
     * 测试 seam：走生产 [abandon] 路径（terminate → unbind → 死亡事实/`isBinderAlive`
     * 组合确认 ≤2 s），返回死亡是否在期限内确认。它消灭的失败是"宿主阶段超时路径的重建
     * 预算只能靠猜"：连续 abandon 后是否还会 bind 由设备用例直接断言。
     */
    internal suspend fun abandonForTest(): Boolean {
        val current = link ?: return false
        abandon(current)
        return !unavailable
    }

    internal fun connectionCountForTest(): Int = connections.get()

    /**
     * 测试 seam：观测结构性失败锁存位。**它消灭的失败**：KD-76 ⑤"loading 超时不得锁存
     * unavailable"在黑盒断言里只能靠时序推断（未锁存 = 下一次 execute 重新等满预算），
     * 仪器化用例可以直接断言锁存位本身。
     */
    internal fun unavailableForTest(): Boolean = unavailable

    /**
     * 测试 seam：覆盖 ready 预算（毫秒；null = 生产常量 [READY_TIMEOUT_MILLIS]）。
     * **它消灭的失败**：ready 超时分支在真实服务上无法确定性触达（真实 warm-up 总会完成），
     * 其"如实 FAILED + 不锁存"语义只能靠读码；夹具注入永不上报 ready 的本地 fake binder
     * 与短预算后，用例可确定性走到该分支并断言行为。
     */
    @Volatile
    internal var readyTimeoutOverrideForTest: Long? = null

    /**
     * 测试 seam：预置一条熔断记录（P-12③，批次 36）。
     *
     * **它消灭的具体失败**：契约 §4 的"成功显式重试解除熔断"（[executeFrame] 尾部的
     * `fusedDigests.remove` 分支）在真实触发路径下不可确定性构造——熔断只由真实故障路径
     * 置位（宿主阶段超时、结果层硬期限、worker onFatal/死亡），而触发熔断的程序必然挂死
     * 或崩溃，对同一 digest 的显式重试只会再次挂死，永远到不了解除分支（批次 35 判定，
     * 准入清单 :135）。预置 digest 让检查位（[execute] 入队前 + [executeFrame] 出队后）
     * 与解除位在零进程死亡、零时序标定下直测；熔断**置位**半边不受本 seam 影响，仍由
     * 真实死亡路径的既有用例钉住。
     *
     * digest 口径与 [execute] 同源：`figureSha256Hex(sourceJs 的 UTF-8 字节)`。
     *
     * 回滚：删除本函数与唯一消费者（`VisualJsIsolationInstrumentedTest` 的 P-12③ 用例）
     * 即完整回退；[fusedDigests] 本体与全部生产路径零改动。
     */
    internal fun fuseDigestForTest(digest: String) {
        fusedDigests.add(digest)
    }

    internal fun workerUidForTest(): Int? = link?.workerUid?.takeIf { it > 0 }

    internal fun generationForTest(): Long? = link?.generation

    /** 轮询等待"已连接"；不主动发起连接（重建由死亡路径负责）。 */
    internal suspend fun awaitConnectedForTest(timeoutMillis: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (link?.state == LinkState.CONNECTED) return true
            if (unavailable) return false
            delay(50L)
        }
        return link?.state == LinkState.CONNECTED
    }

    private companion object {
        const val TAG = "VisualJsClientEngine"

        /**
         * 首连（冷启动）预算：设备实测隔离进程 attach 4.6–7.5 s（E-01d），再加握手。
         * 旧值 5_000 会把加载态误判成结构性失败；这里覆盖实测上界并留余量。
         */
        const val FIRST_CONNECT_TIMEOUT_MILLIS: Long = 15_000L

        /**
         * 重建预算：worker 已装载过原生库（E-01b 实测 0.6–0.9 s），但系统再次进入重负载
         * 时没有实测；R1 §6.2 建议 10 s，避免把"慢但会到"的重建永久判死。
         */
        const val RECONNECT_TIMEOUT_MILLIS: Long = 10_000L

        /**
         * 旧实例死亡确认上界；死亡事实 + `isBinderAlive` 轮询共用这一预算。
         * 2 s 是产品选择（与 R1 §6.2 / nextvol 对齐），不是平台 SLA：AOSP 不承诺
         * `binderDied` 的延迟上界。
         */
        const val DEATH_CONFIRM_TIMEOUT_MILLIS: Long = 2_000L

        /** `isBinderAlive` 轮询间隔；1 s 预算内至少 20 个判定点。 */
        const val DEATH_POLL_INTERVAL_MILLIS: Long = 50L

        /**
         * 服务端 warm-up ready 预算（批次 25 T2，KD-76 ①/⑤）：attach 预算之上的**独立**
         * 第二段预算——握手成功后服务仍在做一次性 `QuickJs.create` 装载 + 空程序 warm-up。
         * 10 s 是 R1' §2.3-4 的待设备校准起点值（设备实测冷启动 attach 4.6–7.5 s 叠加装载
         * 的总成本无实测），不是实测上界。超时按 loading 如实失败（不锁存、不扣重建预算）。
         */
        const val READY_TIMEOUT_MILLIS: Long = 10_000L

        /** `isReady()` 轮询间隔；10 s 预算内 100 个判定点（R1' §2.3-4 的 ~100 ms 间隔）。 */
        const val READY_POLL_INTERVAL_MILLIS: Long = 100L
    }
}

/**
 * 结果帧消费者身份校验（契约 §64 末句"消费者须验证服务调用身份"；批次 25 T3 抽出）。
 *
 * **它消灭的失败**：三重核对原先内联在 [VisualJsClientEngine.newCallback] 的 Binder Stub
 * 里，伪造 generation/requestId/UID 的拒绝分支无法脱离真进程做 JVM 负向断言（R1' §4
 * A-12/A-13 判定"旧 requestId 帧丢弃的独立负向用例未见"）。抽成纯函数后，伪造帧被拒
 * 可直接单测；Binder 侧的 `Binder.getCallingUid()` 取值仍留在调用点，语义零改动。
 *
 * 核对项（全部命中才接受，任何一项伪造即丢弃帧并关 fd）：
 * - generation 必须等于当前 link 的 generation（旧实例的迟到帧）；
 * - requestId 必须等于本会话的 requestId（另一请求的结果帧）；
 * - 调用 UID 必须等于 worker 隔离 UID（宿主自己/第三方进程伪造的回调）。
 */
internal object VisualJsResultFrameFilter {
    fun accepts(
        frameGeneration: Long,
        frameRequestId: String?,
        callerUid: Int,
        linkGeneration: Long,
        requestId: String,
        workerUid: Int,
    ): Boolean =
        frameGeneration == linkGeneration &&
            frameRequestId == requestId &&
            callerUid == workerUid
}
