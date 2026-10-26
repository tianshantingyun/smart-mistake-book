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
 *   等 Binder death ≤1 s）；
 * - 死亡确认后最多重建一次；重建后的实例再死 → 熔断并不再 bind；
 * - 重建只恢复运行环境，**不重跑**刚才超时/崩溃的源码；`programHash` 熔断到
 *   `explicitUserRetry` 或源码改变（契约 §4:70）；
 * - 旧 generation 的回调、旧 requestId 的结果一律丢弃并关 fd（不把另一张图当旧帧）。
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
    private val executeMutex = Mutex()
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

        return executeMutex.withLock {
            if (closed.get()) {
                return@withLock rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "the visual_js client is closed",
                )
            }
            if (!request.explicitUserRetry && digest in fusedDigests) {
                return@withLock rejected(
                    VisualExecutionRejection.PROGRAM_FUSED,
                    "program digest ${digest.take(12)} is fused until explicit user retry",
                )
            }
            val connection = ensureConnected()
                ?: return@withLock rejected(
                    VisualExecutionRejection.ENGINE_CLOSED,
                    "visual_js is unavailable (bind failed, or the previous worker's death was not confirmed)",
                )
            val requestId = UUID.randomUUID().toString()
            val pending = CompletableDeferred<VisualExecutionResult>()
            val call = ActiveCall(connection, digest, pending)
            activeCall = call
            val outcome: VisualExecutionResult = try {
                try {
                    withTimeout(VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS) {
                        runSession(connection, digest, requestId, envelope, pending)
                    }
                } catch (timeout: TimeoutCancellationException) {
                    // 竞态：结果与超时同时到；已完成就不误熔断（R1 §4.5）。
                    if (pending.isCompleted) {
                        pending.await()
                    } else {
                        fusedDigests.add(digest)
                        withContext(NonCancellable) { abandon(connection) }
                        rejected(
                            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                            "host phase deadline " +
                                "(${VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS} ms) exceeded",
                        )
                    }
                } catch (cancel: CancellationException) {
                    // 调用方取消：与现有引擎一致，原样重抛，不折成拒绝；同时停掉服务里的工作。
                    withContext(NonCancellable) { abandon(connection) }
                    throw cancel
                }
            } finally {
                if (activeCall === call) activeCall = null
            }
            // 契约 §4：熔断到"用户明确本地重试或源码改变"。一次成功的显式重试即解除熔断；
            // 否则同一程序在成功后仍会被 PROGRAM_FUSED 永久挡住（除非每帧都标 explicitRetry）。
            if (request.explicitUserRetry && outcome is VisualExecutionResult.Rendered) {
                fusedDigests.remove(digest)
            }
            outcome
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = link
        if (current != null) {
            current.state = LinkState.ABANDONED
            runCatching { current.service?.terminate(current.generation) }
            current.service = null
            runCatching { context.unbindService(current.connection) }
            unlinkDeath(current)
            // 死亡确认只做诊断；close 不阻塞调用方（端口允许 close 幂等且不等引擎锁）。
            scope.launch {
                withTimeoutOrNull(DEATH_CONFIRM_TIMEOUT_MILLIS) { current.death.await() }
            }
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
            if (generation != connection.generation || callbackRequestId != requestId) {
                runCatching { payload?.close() }
                return
            }
            if (Binder.getCallingUid() != connection.workerUid) {
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
            if (generation != connection.generation || callbackRequestId != requestId) return
            if (Binder.getCallingUid() != connection.workerUid) return
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
        if (closed.get() || unavailable) return@withLock null
        val existing = link
        if (existing != null && existing.state == LinkState.CONNECTED) {
            return@withLock existing
        }
        // 预算已用尽而 link 为空（超时 abandon / 死亡清空）时不得再 bind：否则连续宿主阶段
        // 超时可以无限新建隔离进程，契约"确认旧实例终结后最多重建一次"不成立。
        if (rebuildConsumed) {
            if (existing != null) dispose(existing)
            unavailable = true
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
        if (closed.get() || unavailable) return null
        if (connections.get() > 0) {
            if (rebuildConsumed) {
                unavailable = true
                return null
            }
            rebuildConsumed = true
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
            return null
        }
        val connected = try {
            withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { candidate.connected.await() } ?: false
        } catch (cancel: CancellationException) {
            throw cancel
        }
        if (!connected || candidate.state != LinkState.CONNECTED) {
            candidate.state = LinkState.ABANDONED
            runCatching { context.unbindService(candidate.connection) }
            unlinkDeath(candidate)
            synchronized(linkLock) { if (link === candidate) link = null }
            unavailable = true
            return null
        }
        if (closed.get()) {
            // close() 与握手竞态：close 先看到 link==null（死亡已清空）而不会再解绑这个
            // 新实例；这里补上，避免关闭后泄漏一条 binding/进程。
            candidate.state = LinkState.ABANDONED
            runCatching { candidate.service?.terminate(candidate.generation) }
            candidate.service = null
            runCatching { context.unbindService(candidate.connection) }
            unlinkDeath(candidate)
            synchronized(linkLock) { if (link === candidate) link = null }
            return null
        }
        return candidate
    }

    private suspend fun abandon(link: Link) {
        if (link.state == LinkState.ABANDONED) return
        link.state = LinkState.ABANDONED
        runCatching { link.service?.terminate(link.generation) }
        link.service = null
        runCatching { context.unbindService(link.connection) }
        unlinkDeath(link)
        val confirmed = withTimeoutOrNull(DEATH_CONFIRM_TIMEOUT_MILLIS) { link.death.await() } != null
        if (!confirmed) {
            // 契约 §4：死亡未确认时展示 fallback，不能并发放大进程数。
            link.state = LinkState.DEATH_UNCONFIRMED
            unavailable = true
        }
        synchronized(linkLock) { if (this.link === link) this.link = null }
    }

    private fun dispose(dead: Link) {
        dead.state = LinkState.ABANDONED
        runCatching { dead.service?.terminate(dead.generation) }
        dead.service = null
        runCatching { context.unbindService(dead.connection) }
        unlinkDeath(dead)
        synchronized(linkLock) { if (link === dead) link = null }
    }

    private fun handleDeath(deadLink: Link, cause: String, unexpected: Boolean) {
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

        private val dead = AtomicBoolean(false)

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
                    val late = IVisualJsService.Stub.asInterface(binder)
                    runCatching { late.terminate(generation) }
                    runCatching { context.unbindService(this) }
                    markDead("a late connection arrived after abandonment", unexpected = false)
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
                    if (dead.get() || state == LinkState.ABANDONED) {
                        markDead("connection died during the handshake", unexpected = true)
                        return
                    }
                    workerUid = uid
                    service = candidate
                    state = LinkState.CONNECTED
                    // 与 state 同时记账：awaitConnectedForTest 看到 CONNECTED 时计数已包含本次。
                    connections.incrementAndGet()
                    if (!connected.isCompleted) connected.complete(true)
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

        fun markDead(cause: String, unexpected: Boolean) {
            if (!dead.compareAndSet(false, true)) return
            if (state != LinkState.ABANDONED) state = LinkState.DEAD
            service = null
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
     * 测试 seam：走生产 [abandon] 路径（terminate → unbind → 等 Binder death ≤1 s），
     * 返回死亡是否在期限内确认。它消灭的失败是"宿主阶段超时路径的重建预算只能靠猜"：
     * 连续 abandon 后是否还会 bind 由设备用例直接断言。
     */
    internal suspend fun abandonForTest(): Boolean {
        val current = link ?: return false
        abandon(current)
        return !unavailable
    }

    internal fun connectionCountForTest(): Int = connections.get()

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
        const val CONNECT_TIMEOUT_MILLIS: Long = 5_000L
        const val DEATH_CONFIRM_TIMEOUT_MILLIS: Long = 1_000L
    }
}
