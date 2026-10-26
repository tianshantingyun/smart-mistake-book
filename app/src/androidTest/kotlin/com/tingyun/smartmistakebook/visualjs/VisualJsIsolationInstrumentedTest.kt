package com.tingyun.smartmistakebook.visualjs

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 批次 22 T1（4E.2 前置）的隔离进程仪器化用例（设备轨执行）。2026-10-26 修复前设备实跑
 * **4/4 红**：隔离 UID 断言成立，但首个 `execute` 被服务端判成 `request pipe ended before a
 * complete envelope arrived`（reliable-pipe comm 通道误报，见 `VisualJsService.execute` 的
 * `dup()` 修复）。dup 修复后批次 23 设备复跑 **5 例 4 绿 1 红**（唯一红为 abandon 结构性
 * 缺陷）；批次 24 已修该缺陷与冷启动首连预算，设备轨 E-01/E-01b 在两代 debug APK 上均
 * **6/6**（`OK (6 tests)`，见 `docs/research/evidence/2026-10-26-batch-24/`）；本类仍以
 * ≤6 s 判死，不能作为 KD-66 hard+500 ms 关闭门。
 *
 * 覆盖任务书 T1 第四条：
 * 1. AIDL 往返 + generation 校验（[workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater]、
 *    [aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp]）；
 * 2. 超期终止不泄漏后续 BUSY——新进程新状态（[theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState]）；
 * 3. client 在服务进程死亡后重建一次（[theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy]）；
 * 4. 宿主阶段超时（abandon）确认死亡后最多重建一次，重建实例可直接执行
 *    （[theClientRebuildsAtMostOnceAfterConfirmedAbandons]）；
 * 5. 冷启动 attach 超过旧 5 s 预算时首次执行仍成功
 *    （[aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram]）。
 *
 * 关键：**同进程单测不算隔离验证**（`Stub.asInterface` 在同进程返回本地对象）。这些用例必须
 * 起真实 `:visual_js` 进程：`workerUid != Process.myUid()`、watchdog SIGKILL 后 binder death、
 * 新进程的 generation/引擎状态都是真进程行为。
 *
 * 设备前置（批次 22 修复前设备轨只验证到"隔离 UID 成立"；批次 23 设备轨已用往返/watchdog
 * 用例证明下列两项，见 E-01b）：
 * - 隔离进程能 `System.loadLibrary("quickjs")`（AAR 的 libquickjs.so 从 APK 映射）——绿；
 * - `Process.killProcess(myPid())` 在 isolated UID 下允许——watchdog 硬杀用例绿；
 * - Process.myUid() != applicationInfo.uid 在隔离进程中的实际值已由首例断言通过（R1 §9.4）。
 *
 * 本文件不调用 `QuickJs.close()` 的挂死路径：issue #240 由服务 watchdog 直接 SIGKILL，
 * 测试只观察 binder death 与新进程行为。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsIsolationInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val harnesses = CopyOnWriteArrayList<VisualJsServiceHarness>()
    private val clients = CopyOnWriteArrayList<VisualJsClientEngine>()

    @After
    fun closeEverything() {
        harnesses.forEach { it.close() }
        harnesses.clear()
        clients.forEach { it.close() }
        clients.clear()
    }

    private fun harness(): VisualJsServiceHarness =
        VisualJsServiceHarness(context).also { harnesses.add(it) }

    private fun client(): VisualJsClientEngine =
        VisualJsClientEngine(context).also { clients.add(it) }

    @Test
    fun workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater() = runBlocking {
        val harness = harness()
        val service = harness.connect()

        assertEquals(VisualJsWire.PROTOCOL_VERSION, service.protocolVersion())
        assertNotEquals(
            "a :visual_js worker must not share the host UID",
            Process.myUid(),
            service.workerUid(),
        )

        val generation = 101L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))
        assertEquals(generation, service.currentGeneration())

        val first = harness.execute(generation, "round-trip-1", pureGeometryRequest())
        assertEquals("first execution: ${first.diagnostics}", VisualJsWire.RESULT_OK, first.status)
        val rendered = first.asResult() as VisualExecutionResult.Rendered
        assertTrue(rendered.sceneJson.contains("scene-1"))

        // 有界拒绝也走同一 PFD 结果通道（不是抛异常/断连接）。
        val broken = harness.execute(
            generation,
            "round-trip-2",
            requestWithSource("const visual = {"),
        )
        assertEquals(VisualJsWire.RESULT_REJECTED, broken.status)
        assertEquals(
            VisualExecutionRejection.INVALID_PROGRAM,
            (broken.asResult() as VisualExecutionResult.Rejected).reason,
        )

        // 正常完成必须先使 watchdog 令牌失效：超过 1500 ms 服务硬期限后仍能执行，
        // 证明"迟到定时器不得杀下一任务"（契约 §4）。
        delay(1_800L)
        val later = harness.execute(generation, "round-trip-3", pureGeometryRequest())
        assertEquals(
            "the watchdog killed a healthy worker: status=${later.status} " +
                "diagnostics=${later.diagnostics}",
            VisualJsWire.RESULT_OK,
            later.status,
        )
    }

    @Test
    fun aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp() = runBlocking {
        val harness = harness()
        val service = harness.connect()

        val generation = 202L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))

        val stale = harness.execute(generation + 1, "stale-execute", pureGeometryRequest())
        assertEquals(VisualJsWire.STATUS_STALE_GENERATION, stale.status)

        // 过期 terminate 必须 no-op：服务仍活、当前 generation 不变、后续请求仍可执行。
        service.terminate(generation + 1)
        delay(250L)
        assertEquals(generation, service.currentGeneration())

        val live = harness.execute(generation, "live-after-stale-terminate", pureGeometryRequest())
        assertEquals(
            "a stale terminate killed the current worker: status=${live.status} " +
                "diagnostics=${live.diagnostics}",
            VisualJsWire.RESULT_OK,
            live.status,
        )
    }

    @Test
    fun theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState() = runBlocking {
        val first = harness()
        val service = first.connect()
        val generation = 303L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))

        // issue #240 形状：evaluate 在排水循环里自旋，协作中断与 close 都无效。
        // 服务 L2 watchdog 应在 ~1500 ms 后 SIGKILL。
        val hung = async { first.execute(generation, "issue-240", issue240Request()) }
        val died = withTimeoutOrNull(6_000L) { first.deathSignal().await() } != null
        // 若钉版升级后本断言变红且 hung 返回 PHASE_TIMEOUT，说明上游已修 #240：
        // 应反转为"协作中断在 100 ms 内返回且进程不死"，而不是放宽本断言
        //（与批次 21 `issue240...` 用例同型）。
        assertTrue(
            "the L2 watchdog did not kill :visual_js within 6 s (issue #240 must be " +
                "handled by the process layer, not by engine timeouts)",
            died,
        )
        val outcome = hung.await()
        assertNotEquals(
            "a hung program must not produce a rendered result: ${outcome.diagnostics}",
            VisualJsWire.RESULT_OK,
            outcome.status,
        )

        // 新进程 + 新 generation：不得继承旧实例卡死的 inFlight → BUSY。
        val second = harness()
        try {
            val freshService = second.connect()
            val freshGeneration = 304L
            assertEquals(VisualJsWire.STATUS_ACCEPTED, freshService.initialize(freshGeneration))

            val fresh = second.execute(freshGeneration, "fresh-process", pureGeometryRequest())
            assertEquals(
                "the new process inherited the old BUSY state: status=${fresh.status} " +
                    "diagnostics=${fresh.diagnostics}",
                VisualJsWire.RESULT_OK,
                fresh.status,
            )
        } finally {
            second.close()
        }
    }

    @Test
    fun theClientRebuildsAtMostOnceAfterConfirmedAbandons() = runBlocking {
        val engine = client()

        assertTrue("initial connect failed", engine.connectForTest())
        assertEquals(1, engine.connectionCountForTest())

        // 宿主阶段超时走 abandon（不是死亡通知）：第一次确认死亡后允许一次重建。
        assertTrue("the first abandon did not confirm death", engine.abandonForTest())
        assertTrue("the client did not rebuild after the first confirmed abandon", engine.connectForTest())
        assertEquals(2, engine.connectionCountForTest())

        // 重建后的实例必须能直接接活：BUSY 说明旧实例的在飞槽位/进程状态泄漏到了新进程。
        val rebuilt = engine.execute(pureGeometryRequest())
        assertTrue(
            "the rebuilt worker is not usable after a confirmed abandon (BUSY means the old " +
                "instance leaked state): $rebuilt",
            rebuilt is VisualExecutionResult.Rendered,
        )

        // 第二次确认死亡后不得再 bind：连续超时不能无限放大隔离进程数。
        assertTrue("the second abandon did not confirm death", engine.abandonForTest())
        assertFalse(
            "the client rebuilt more than once across confirmed abandons",
            engine.connectForTest(),
        )
        assertEquals(2, engine.connectionCountForTest())
    }

    /**
     * 冷启动加载态：设备实测隔离进程 attach 4.6–7.5 s（E-01d），旧 5 s 首连预算会把
     * "进程 fork + `libquickjs.so` 装载"误判成结构性失败，首个 `execute` 直接
     * `ENGINE_CLOSED`。本用例把真实 attach 回调推迟到实测上界（7.6 s），钉住"首次执行
     * 必须成功"这一用户可见结果；断言的是行为，不是超时常量本身。
     */
    @Test
    fun aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram() = runBlocking {
        val delayed = DelayedAttachContext(context, COLD_START_ATTACH_DELAY_MILLIS)
        val engine = VisualJsClientEngine(delayed).also { clients.add(it) }

        // 不先 connectForTest()：这条用例钉的正是冷启动的第一次 execute。
        val first = engine.execute(pureGeometryRequest())
        assertTrue(
            "the first execution after a ${COLD_START_ATTACH_DELAY_MILLIS} ms cold-start attach " +
                "was misclassified as a structural failure: $first",
            first is VisualExecutionResult.Rendered,
        )
        assertEquals(1, engine.connectionCountForTest())
    }

    @Test
    fun theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy() = runBlocking {
        val engine = client()

        assertTrue("initial connect failed", engine.connectForTest())
        val firstGeneration = engine.generationForTest()
        assertNotNull(firstGeneration)
        assertNotEquals(Process.myUid(), engine.workerUidForTest())
        assertEquals(1, engine.connectionCountForTest())

        val first = engine.execute(issue240Request())
        assertEquals(
            "the host L3 layer must map the worker death to the hard-deadline rejection, got $first",
            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
            (first as VisualExecutionResult.Rejected).reason,
        )

        assertTrue(
            "the client did not rebuild after the worker death",
            engine.awaitConnectedForTest(10_000L),
        )
        assertEquals(2, engine.connectionCountForTest())
        assertTrue(
            "a rebuild must use a fresh generation nonce",
            firstGeneration != engine.generationForTest(),
        )

        // 熔断：同一 programHash 不显式重试不再下发（契约 §4:70）。
        val fused = engine.execute(issue240Request())
        assertEquals(
            VisualExecutionRejection.PROGRAM_FUSED,
            (fused as VisualExecutionResult.Rejected).reason,
        )

        // 新进程新状态：没有旧 #240 实例的 inFlight → 不会把 BUSY 泄漏给下一张图。
        val fresh = engine.execute(pureGeometryRequest(explicitUserRetry = true))
        assertTrue("the rebuilt worker was busy: $fresh", fresh is VisualExecutionResult.Rendered)

        // 重建后的实例再死：最多重建一次 → 不再 bind，后续 fail-closed。
        val secondDeath = engine.execute(issue240Request(explicitUserRetry = true))
        assertEquals(
            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
            (secondDeath as VisualExecutionResult.Rejected).reason,
        )
        assertFalse(
            "the client rebuilt more than once after repeated deaths",
            engine.awaitConnectedForTest(5_000L),
        )
        assertEquals(2, engine.connectionCountForTest())

        val after = engine.execute(pureGeometryRequest(explicitUserRetry = true))
        assertEquals(
            VisualExecutionRejection.ENGINE_CLOSED,
            (after as VisualExecutionResult.Rejected).reason,
        )
    }

    // ---------------------------------------------------------------- 夹具

    private fun pureGeometryRequest(explicitUserRetry: Boolean = false) =
        VisualExecutionRequest(
            programId = "program-pure-geometry",
            sourceJs = """
                const visual = {
                  prepare: function () { return []; },
                  render: function (input) {
                    return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                             viewBox: { x: 0, y: 0,
                                        width: input.viewport.widthDu,
                                        height: input.viewport.heightDu },
                             paths: [], root: { type: 'group', children: [] } };
                  },
                };
            """.trimIndent(),
            sceneRevision = 1,
            stateJson = null,
            viewport = VisualViewport(360, 240),
            timeMs = 1_000L,
            seed = 42L,
            theme = "light",
            explicitUserRetry = explicitUserRetry,
        )

    private fun issue240Request(explicitUserRetry: Boolean = false) =
        VisualExecutionRequest(
            programId = "program-issue-240",
            sourceJs = """
                function f() { Promise.resolve().then(f); while (true) {} }
                f();
                const visual = {
                  prepare: function () { return []; },
                  render: function () { return {}; },
                };
            """.trimIndent(),
            sceneRevision = 1,
            stateJson = null,
            viewport = VisualViewport(360, 240),
            timeMs = 2_000L,
            seed = 7L,
            theme = "light",
            explicitUserRetry = explicitUserRetry,
        )

    private fun requestWithSource(source: String) = VisualExecutionRequest(
        programId = "program-broken",
        sourceJs = source,
        sceneRevision = 1,
        stateJson = null,
        viewport = VisualViewport(360, 240),
        timeMs = 3_000L,
        seed = 9L,
        theme = "light",
    )

    private companion object {
        /**
         * 设备实测冷启动 attach 4.6–7.5 s（E-01d）。取 7.6 s（实测上界 + 100 ms），
         * 旧 5 s 首连预算下必红；新预算 15 s 留出握手余量。只推迟 attach 回调、不推迟
         * `bindService`，避免把"延迟"与"真实进程 fork"串行相加。
         */
        const val COLD_START_ATTACH_DELAY_MILLIS: Long = 7_600L
    }
}

/**
 * 冷启动加载态夹具：真实 `bindService` 立即执行，但把 `onServiceConnected` 推迟
 * [attachDelayMillis]，复现"隔离进程 attach 超过旧 5 s 预算"的时间形状。
 *
 * `unbindService` 必须用 `bindService` 时注册的那个 [ServiceConnection] 实例，因此这里
 * 维护"客户端连接 → 包装连接"的映射；客户端提前放弃（旧实现 5 s 超时）时仍能正确解绑，
 * 不会残留 binding 或泄漏隔离进程。
 */
private class DelayedAttachContext(
    base: Context,
    private val attachDelayMillis: Long,
) : ContextWrapper(base) {

    private val handler = Handler(Looper.getMainLooper())
    private val delegates = ConcurrentHashMap<ServiceConnection, ServiceConnection>()
    private val attachDeadlines = ConcurrentHashMap<ServiceConnection, Long>()

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        // 期限从 bindService 起算，而不是从 onServiceConnected 起算：真实 attach 无论多快，
        // 客户端看到回调的时刻都稳定在 bind + attachDelayMillis，夹具不受进程启动快慢影响。
        attachDeadlines[conn] = SystemClock.uptimeMillis() + attachDelayMillis
        val delayed = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val remaining = (attachDeadlines.remove(conn) ?: 0L) - SystemClock.uptimeMillis()
                if (remaining <= 0L) {
                    conn.onServiceConnected(name, binder)
                } else {
                    handler.postDelayed({ conn.onServiceConnected(name, binder) }, remaining)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                conn.onServiceDisconnected(name)
            }

            override fun onBindingDied(name: ComponentName?) {
                conn.onBindingDied(name)
            }

            override fun onNullBinding(name: ComponentName?) {
                conn.onNullBinding(name)
            }
        }
        delegates[conn] = delayed
        val bound = baseContext.bindService(service, delayed, flags)
        if (!bound) {
            delegates.remove(conn)
            attachDeadlines.remove(conn)
        }
        return bound
    }

    override fun unbindService(conn: ServiceConnection) {
        attachDeadlines.remove(conn)
        val delegate = delegates.remove(conn) ?: conn
        baseContext.unbindService(delegate)
    }
}

/** 一次直接 AIDL 调用的结果；status 为 [VisualJsWire] 返回码或本类的本地哨兵。 */
internal data class HarnessExecution(
    val status: Int,
    val payload: ByteArray?,
    val diagnostics: String?,
) {
    fun asResult(): VisualExecutionResult =
        VisualJsProtocol.decodeResult(status, payload, diagnostics)

    companion object {
        const val FATAL: Int = -1
        const val TIMEOUT: Int = -2
        const val DIED: Int = -3
    }
}

/**
 * 测试专用直连 harness：真实 bind `:visual_js`、真实 PFD 往返、真实 binder death。
 *
 * 不复用 [VisualJsClientEngine] 是为了让 generation 校验（stale execute/terminate）能绕过
 * client 的状态机，直接打 AIDL 表面；client 自身由重建用例覆盖。
 */
internal class VisualJsServiceHarness(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connected = CompletableDeferred<Unit>()
    private val died = CompletableDeferred<Unit>()

    @Volatile
    private var service: IVisualJsService? = null

    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var connection: ServiceConnection? = null

    private val deathRecipient = IBinder.DeathRecipient {
        if (!died.isCompleted) died.complete(Unit)
    }

    suspend fun connect(): IVisualJsService {
        val target = Intent(context, VisualJsService::class.java)
        val candidate = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    if (!died.isCompleted) died.complete(Unit)
                    return
                }
                this@VisualJsServiceHarness.binder = binder
                try {
                    binder.linkToDeath(deathRecipient, 0)
                } catch (_: RemoteException) {
                    died.complete(Unit)
                }
                this@VisualJsServiceHarness.service = IVisualJsService.Stub.asInterface(binder)
                if (!connected.isCompleted) connected.complete(Unit)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                died.complete(Unit)
            }

            override fun onBindingDied(name: ComponentName?) {
                died.complete(Unit)
            }

            override fun onNullBinding(name: ComponentName?) {
                died.complete(Unit)
            }
        }
        connection = candidate
        check(context.bindService(target, candidate, Context.BIND_AUTO_CREATE)) {
            "bindService returned false for :visual_js"
        }
        withTimeout(CONNECT_TIMEOUT_MILLIS) { connected.await() }
        return checkNotNull(service) { "onServiceConnected never supplied a binder" }
    }

    fun deathSignal(): CompletableDeferred<Unit> = died

    suspend fun execute(
        generation: Long,
        requestId: String,
        request: VisualExecutionRequest,
    ): HarnessExecution {
        val target = checkNotNull(service) { "the harness is not connected" }
        val envelope = VisualJsProtocol.encodeRequest(request)
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        val result = CompletableDeferred<HarnessExecution>()
        val callback = object : IVisualJsCallback.Stub() {
            override fun onResult(
                generation: Long,
                callbackRequestId: String?,
                status: Int,
                payload: ParcelFileDescriptor?,
                diagnostics: String?,
            ) {
                scope.launch {
                    val bytes = payload?.let { descriptor ->
                        runCatching {
                            // 与 VisualJsClientEngine 同因：先 dup 去掉 reliable-pipe 的
                            // comm 通道，否则 worker 关闭自己 read end 后这里会读到
                            // "Remote side is dead"（设备探针 A/E）。
                            try {
                                ParcelFileDescriptor.AutoCloseInputStream(descriptor.dup()).use {
                                    it.readBytes()
                                }
                            } finally {
                                descriptor.close()
                            }
                        }.getOrNull()
                    }
                    if (!result.isCompleted) {
                        result.complete(HarnessExecution(status, bytes, diagnostics))
                    }
                }
            }

            override fun onFatal(
                generation: Long,
                callbackRequestId: String?,
                code: Int,
                diagnostics: String?,
            ) {
                if (!result.isCompleted) {
                    result.complete(HarnessExecution(HarnessExecution.FATAL, null, diagnostics))
                }
            }
        }

        val accepted = try {
            target.execute(generation, requestId, readEnd, callback)
        } catch (failure: RemoteException) {
            runCatching { writeEnd.close() }
            return HarnessExecution(HarnessExecution.DIED, null, failure.message)
        } finally {
            runCatching { readEnd.close() }
        }
        if (accepted != VisualJsWire.STATUS_ACCEPTED) {
            runCatching { writeEnd.close() }
            return HarnessExecution(accepted, null, null)
        }

        // 先 execute 再写信封（R1 高危项：先写满 pipe 会把宿主期限拖死）。
        scope.launch {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { it.write(envelope) }
            } catch (_: IOException) {
                runCatching { writeEnd.close() }
            }
        }

        val deathWatcher = scope.launch {
            died.await()
            if (!result.isCompleted) {
                result.complete(HarnessExecution(HarnessExecution.DIED, null, null))
            }
        }
        val outcome = withTimeoutOrNull(EXECUTION_WAIT_MILLIS) { result.await() }
            ?: HarnessExecution(HarnessExecution.TIMEOUT, null, null)
        deathWatcher.cancel()
        return outcome
    }

    fun close() {
        binder?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
        connection?.let { runCatching { context.unbindService(it) } }
        binder = null
        service = null
        connection = null
        scope.cancel()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS: Long = 10_000L
        const val EXECUTION_WAIT_MILLIS: Long =
            VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS + 8_000L
    }
}
