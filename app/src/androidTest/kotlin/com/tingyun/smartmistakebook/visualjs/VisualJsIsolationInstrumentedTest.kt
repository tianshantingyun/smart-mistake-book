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
 * **6/6**（`OK (6 tests)`，见 `docs/research/evidence/2026-10-26-batch-24/`）；watchdog
 * 观察用例仍以 ≤6 s 判死；KD-66 hard+500 ms 关闭门由
 * [aHungWorkerReleasesTheCallerWithinHardPlus500ms] 承担（批次 26 T1，设备执行归设备轨）。
 *
 * 覆盖任务书 T1 第四条：
 * 1. AIDL 往返 + generation 校验（[workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater]、
 *    [aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp]）；
 * 2. 超期终止不泄漏后续 BUSY——新进程新状态（[theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState]）；
 * 3. client 在服务进程死亡后重建一次（[theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy]）；
 * 4. 宿主阶段超时（abandon）确认死亡后最多重建一次，重建实例可直接执行
 *    （[theClientRebuildsAtMostOnceAfterConfirmedAbandons]）；
 * 5. 冷启动 attach 超过旧 5 s 预算时首次执行仍成功
 *    （[aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram]）；
 * 6. 契约 §64 消费者校验负向（批次 25 T3，A-2①/A-5①/A-14）：畸形调用参数 BAD_REQUEST、
 *    超 256 KiB 信封有界拒绝、并发第二请求单在飞 BUSY
 *    （[aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot]、
 *    [anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead]、
 *    [aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight]）；
 * 7. warm-up/ready（批次 25 T2，KD-76 ①/④/⑤）：ready 前后的 acquire 都有界且不误杀
 *    （[anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled]）、首连
 *    超时如实 FAILED 不锁存且有界重试
 *    （[aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching]）、
 *    ready 超时不锁存并重新 bind（[aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds]）。
 *    KD-76 ③④⑤ 的设备判据（冷启动+CPU 竞争、日志无 watchdog 签名）仍以设备轨为准。
 * 8. KD-66 关闭门（批次 26 T1）：#240 形状从调用方提交到返回有界拒绝 ≤ 服务硬期限
 *    + 500 ms，拒绝归因 worker 死亡路径且后续请求可用
 *    （[aHungWorkerReleasesTheCallerWithinHardPlus500ms]；与 6 s 观察用例共存，R1 §6 裁定）。
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
        // 批次 25 T2（KD-76）：真实 execute 前等 warm-up ready；ready 前服务回 SHUTTING_DOWN。
        assertTrue("the worker never became ready (warm-up failed or hung)", harness.awaitReady())

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
        // 批次 25 T2：ready 门在 generation 核对之前——ready 前的 execute 回 SHUTTING_DOWN
        // 而不是 STALE，因此 stale 用例也必须先等 warm-up。
        assertTrue("the worker never became ready (warm-up failed or hung)", harness.awaitReady())

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
        // 批次 25 T2：warm 服务上的挂死负控（KD-76 ④）——ready 完成后 1500 ms 硬杀不变。
        assertTrue("the worker never became ready (warm-up failed or hung)", first.awaitReady())
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
            assertTrue("the fresh worker never became ready", second.awaitReady())
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

    // ---------------------------------------------------------------- KD-66 关闭门（批次 26 T1）

    /**
     * KD-66 关闭门（`docs/known-defects.md` Reopen condition）：issue #240 形状必须让
     * **调用方**在服务硬期限 + 500 ms 内拿到有界拒绝，且后续请求可用。500 ms 是门自带的
     * 余量（watchdog 调度与 binder death 均无平台 SLA），本用例在其上**不追加任何容差**；
     * hard+500 ms 严格紧于宿主自己的 2500 ms 阶段期限，超时兜底路径（诊断不含 "died"）
     * 不可能蒙混过关。
     *
     * 计时面是生产调用路径 `VisualExecutionEngine.execute`（client 引擎），不是 harness
     * 直连：门的主体是调用方可见延迟（队列派发 + AIDL + 硬期限等待 + 死亡传播 + 拒绝映射）。
     * 计时端到端、不扣除任何段；起止同用 `SystemClock.uptimeMillis()` 单钟相减，服务端
     * 时刻只进 logcat 诊断、不进断言。
     *
     * `connectForTest()` 返回 true 蕴含 worker ready（连接发布前已过 ready 门），warm-up
     * 装载耗时结构性排除在计时外；其前的预热 execute（断言 `Rendered`）双职责——负控
     * "worker 注入前功能正常"（否则门红会被误读为 L2 损坏），并压低 submit→arm 开销。
     *
     * 与既有用例的关系（R1 §6 裁定：**共存**）：[theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState]
     * 与 [aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight] 的 6 s 死亡观察
     * 保留原样，是 L2 kill 的服务级回归观察、**非本门证据**；
     * [theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy] 共享死亡→拒绝→重建→可用
     * 四拍但无计时，保留为状态机回归。
     *
     * 执行边界（如实）：本用例由批次 26 T1 落码，非 Gradle 直编仅证可编译；设备执行与
     * 两轮独立绿证据归设备轨（关闭判据 = 用例绿 + known-defects 记录回链，计划 §2）。
     */
    @Test
    fun aHungWorkerReleasesTheCallerWithinHardPlus500ms() = runBlocking {
        val engine = client()
        assertTrue("initial connect failed", engine.connectForTest())

        // 预热（非计时）：顺序执行，不占服务单在飞槽、不与 #240 帧并发。
        val warm = engine.execute(pureGeometryRequest())
        assertTrue(
            "the worker must render before the hang is injected (otherwise the gate below " +
                "would be misread as an L2 failure when the worker never worked): $warm",
            warm is VisualExecutionResult.Rendered,
        )

        // ── 计时段：t_submit → t_return，端到端，不扣除 ──
        val submittedAt = SystemClock.uptimeMillis()
        val outcome = engine.execute(issue240Request())
        val returnedAt = SystemClock.uptimeMillis()
        val elapsed = returnedAt - submittedAt

        // A1：#240 形状不得产出成功结果。
        assertTrue("expected a bounded rejection, got $outcome", outcome is VisualExecutionResult.Rejected)
        val rejection = outcome as VisualExecutionResult.Rejected
        // A2：端口语义映射必须是硬期限拒绝（不是 BUSY/PROGRAM_FUSED 等其他理由）。
        assertEquals(
            "the #240 shape must map to the hard-deadline rejection, got ${rejection.diagnostics}",
            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
            rejection.reason,
        )
        // A4（先于计时断言）：拒绝必须来自 worker 死亡路径（"visual_js worker died: …"），
        // 不是宿主 2500 ms 阶段超时兜底（诊断 "host phase deadline (2500 ms) exceeded"
        // 不含 "died"）——归因断言把两种失败形状在计时之前分开。
        assertTrue(
            "the rejection must come from the worker death, not the host phase timeout: " +
                rejection.diagnostics,
            rejection.diagnostics.contains("died"),
        )
        // A5：KD-66 关闭门本体。500 ms 是门自带余量，不追加容差；环境慢到吃穿余量即真红，
        // 按 logcat watchdog 行归因，不是改大门常数。
        assertTrue(
            "the caller was released in $elapsed ms; the KD-66 gate is " +
                "$HARD_PLUS_500_GATE_MILLIS ms (hard " +
                "${VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS} + 500). " +
                "reason=${rejection.reason} diagnostics=${rejection.diagnostics}",
            elapsed <= HARD_PLUS_500_GATE_MILLIS,
        )

        // A6+A7：门第二句——后续请求可用（非预期死亡路径自动重建一次，新程序可执行；
        // `Rendered` 传递性证明旧进程真死——否则 #240 线程仍占服务单在飞槽，新请求必 BUSY）。
        assertTrue(
            "the client did not rebuild after the hard-deadline death",
            engine.awaitConnectedForTest(10_000L),
        )
        assertEquals(
            "exactly one rebuild is expected after the death",
            2,
            engine.connectionCountForTest(),
        )
        val fresh = engine.execute(pureGeometryRequest())
        assertTrue(
            "a fresh program must render on the rebuilt worker (BUSY would mean the hung " +
                "engine's in-flight slot leaked into the new process): $fresh",
            fresh is VisualExecutionResult.Rendered,
        )
    }

    // ---------------------------------------------------------------- 消费者校验负向（批次 25 T3）

    /**
     * 契约 §64 末句"消费者须验证……不暴露宿主通用 RPC"的参数面负向用例（A-14）：缺失
     * requestId / 缺失请求描述符 / 缺失回调一律 `STATUS_BAD_REQUEST`，且**不占**单在飞槽
     * ——后续合法请求必须照常执行。
     */
    @Test
    fun aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot() = runBlocking {
        val harness = harness()
        val service = harness.connect()
        // 批次 25 T2：末尾的合法 execute 需要 warm-up ready（畸形参数检查在 ready 门之前，
        // 上面的 BAD_REQUEST 断言不受影响）。
        assertTrue("the worker never became ready (warm-up failed or hung)", harness.awaitReady())
        val generation = 401L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))

        val pipe = ParcelFileDescriptor.createReliablePipe()
        val sink = sinkCallback()
        assertEquals(
            "a null requestId must be rejected",
            VisualJsWire.STATUS_BAD_REQUEST,
            service.execute(generation, null, pipe[0], sink),
        )
        runCatching { pipe[0].close() }
        runCatching { pipe[1].close() }
        assertEquals(
            "a null request descriptor must be rejected",
            VisualJsWire.STATUS_BAD_REQUEST,
            service.execute(generation, "no-descriptor", null, sink),
        )
        // 复核 1 轮修复（A-14 设备红例）：pipe[0]/pipe[1] 已在上面关闭，本负向调用必须
        // 新开 pipe——复用已关闭描述符会在 binder 客户端编组期抛 Bad file descriptor，
        // 事务到不了服务端，null-回调子断言拿不到设备证据。断言与期望值不放宽。
        val callbackPipe = ParcelFileDescriptor.createReliablePipe()
        assertEquals(
            "a null callback must be rejected",
            VisualJsWire.STATUS_BAD_REQUEST,
            service.execute(generation, "no-callback", callbackPipe[0], null),
        )
        runCatching { callbackPipe[0].close() }
        runCatching { callbackPipe[1].close() }

        val good = harness.execute(generation, "after-malformed", pureGeometryRequest())
        assertEquals(
            "the worker must stay usable after malformed calls: ${good.diagnostics}",
            VisualJsWire.RESULT_OK,
            good.status,
        )
    }

    /**
     * A-2① 负向：超过 256 KiB 事务预算的信封必须被服务端**流式有界读**拒绝
     * （`INPUT_OVER_BUDGET`），不先无界读入再拒绝；worker 仍可用。
     */
    @Test
    fun anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead() = runBlocking {
        val harness = harness()
        val service = harness.connect()
        // 批次 25 T2：超预算信封的有界读拒绝在 ready 门之后才发生——先等 warm-up。
        assertTrue("the worker never became ready (warm-up failed or hung)", harness.awaitReady())
        val generation = 402L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))

        val oversized = pureGeometryRequest().copy(stateJson = "x".repeat(300 * 1024))
        val outcome = harness.execute(generation, "over-budget", oversized)
        assertEquals(VisualJsWire.RESULT_REJECTED, outcome.status)
        assertEquals(
            VisualExecutionRejection.INPUT_OVER_BUDGET,
            (outcome.asResult() as VisualExecutionResult.Rejected).reason,
        )

        val good = harness.execute(generation, "after-over-budget", pureGeometryRequest())
        assertEquals(
            "the worker must stay usable after an over-budget request: ${good.diagnostics}",
            VisualJsWire.RESULT_OK,
            good.status,
        )
    }

    /**
     * A-5① 负向（批次 24 复核确认缺失的直接覆盖）：并发第二请求在服务端单在飞 CAS 上
     * 立即 `STATUS_BUSY`。确定性依据：第一请求经 `execute` 返回 ACCEPTED 时已占住槽位，
     * issue-240 形状的自旋至少持续到 1500 ms watchdog SIGKILL；BUSY 分支不依赖时序。
     */
    @Test
    fun aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight() = runBlocking {
        val harness = harness()
        val service = harness.connect()
        // 批次 25 T2：单在飞 BUSY 检查在 ready 门之后——ready 前 execute 回 SHUTTING_DOWN。
        assertTrue("the worker never became ready (warm-up failed or hung)", harness.awaitReady())
        val generation = 403L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))

        val envelope = VisualJsProtocol.encodeRequest(issue240Request())
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val accepted = service.execute(generation, "in-flight-1", pipe[0], sinkCallback())
        runCatching { pipe[0].close() }
        assertEquals("the in-flight request must be accepted", VisualJsWire.STATUS_ACCEPTED, accepted)
        // 先 execute 后写信封（R1 高危项）；hung worker 由 watchdog 在 1500 ms 后 SIGKILL。
        launch(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(envelope) }
            }
        }

        val second = harness.execute(generation, "concurrent-2", pureGeometryRequest())
        assertEquals(
            "a second concurrent request must hit the single-flight BUSY, got status=${second.status}",
            VisualJsWire.STATUS_BUSY,
            second.status,
        )

        assertTrue(
            "the watchdog should kill the hung worker within 6 s",
            withTimeoutOrNull(6_000L) { harness.deathSignal().await() } != null,
        )
    }

    // ---------------------------------------------------------------- warm-up/ready（批次 25 T2）

    /**
     * KD-76 ①的服务端门 + ③的前置（直连 AIDL）：连接建立后**不等 ready** 直接 acquire，
     * 无论落在 warm-up 前还是后都必须有界返回（SHUTTING_DOWN 或正常结果），绝不 fatal /
     * 进程死亡——"ready 之前不 arm 程序 watchdog"由 execute 门结构性保证；ready 之后的
     * 同一 worker 必须可用（warm-up 窗口没有任何 watchdog 误杀痕迹）。
     */
    @Test
    fun anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled() = runBlocking {
        val harness = harness()
        val service = harness.connect()
        val generation = 404L
        assertEquals(VisualJsWire.STATUS_ACCEPTED, service.initialize(generation))

        val racing = harness.execute(generation, "warm-up-race", pureGeometryRequest())
        assertTrue(
            "a pre-ready acquire must be bounded (SHUTTING_DOWN or a normal result), never " +
                "fatal or a worker death: status=${racing.status} diagnostics=${racing.diagnostics}",
            racing.status == VisualJsWire.STATUS_SHUTTING_DOWN ||
                racing.status == VisualJsWire.RESULT_OK ||
                racing.status == VisualJsWire.RESULT_REJECTED,
        )
        assertTrue("the worker never became ready (warm-up failed or hung)", harness.awaitReady())
        assertTrue(
            "the worker must survive the warm-up window (a watchdog misfire kills the process)",
            !harness.deathSignal().isCompleted,
        )
        val after = harness.execute(generation, "after-ready", pureGeometryRequest())
        assertEquals(
            "status=${after.status} diagnostics=${after.diagnostics}",
            VisualJsWire.RESULT_OK,
            after.status,
        )
    }

    /**
     * KD-76 ⑤ / P-04 的核心行为（真实进程、真实 attach 推迟）：attach 预算内未连上是
     * **loading**，不是结构性失败——请求如实 FAILED（ENGINE_CLOSED + loading 诊断）、
     * **不锁存** `unavailable`、连接计数保持 0；下一次 execute 重新 bind（再次完整等待，
     * 有界重试的语义落点；生命周期限次属 P-04）。旧实现一次失败即永久 unavailable。
     */
    @Test
    fun aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching() = runBlocking {
        val delayed = DelayedAttachContext(context, CONNECT_TIMEOUT_DELAY_MILLIS)
        val engine = VisualJsClientEngine(delayed).also { clients.add(it) }

        val first = engine.execute(pureGeometryRequest())
        assertTrue("expected a bounded rejection, got $first", first is VisualExecutionResult.Rejected)
        assertEquals(
            VisualExecutionRejection.ENGINE_CLOSED,
            (first as VisualExecutionResult.Rejected).reason,
        )
        assertTrue(
            "the attach timeout must be reported as loading, got: ${first.diagnostics}",
            first.diagnostics.contains("loading"),
        )
        assertFalse(
            "a first-connect loading timeout must not latch the engine unavailable",
            engine.unavailableForTest(),
        )
        assertEquals("the attach never completed; no connection may be counted", 0, engine.connectionCountForTest())

        // 有界重试：第二次 execute 重新 bind（DelayedAttach 对每次 bind 重新推迟 attach），
        // 再次如实 FAILED；锁存位仍必须是 false——"最终 FAILED"不是"永久 unavailable"。
        val second = engine.execute(pureGeometryRequest())
        assertTrue(
            "the retry must also fail honestly (final FAILED), got $second",
            second is VisualExecutionResult.Rejected &&
                (second as VisualExecutionResult.Rejected).reason == VisualExecutionRejection.ENGINE_CLOSED,
        )
        assertFalse(
            "repeated loading timeouts must not latch the engine unavailable",
            engine.unavailableForTest(),
        )
        assertEquals(0, engine.connectionCountForTest())
    }

    /**
     * KD-76 ⑤ 的 ready 超时分支（确定性）：真实 warm-up 总会完成，无法在真服务上触达
     * ready 超时；夹具注入"永不上报 ready"的本地 fake binder + 短 ready 预算（测试 seam）。
     * 断言：请求如实 FAILED（ENGINE_CLOSED + not-ready 诊断）、不锁存 unavailable、
     * 重试真的重新握手（connectionCount 增长）、terminate 后死亡确认走本地事实分支
     * （isBinderAlive 翻假），不触发 fail-closed。
     */
    @Test
    fun aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds() = runBlocking {
        val faked = NotReadyWorkerContext(context)
        val engine = VisualJsClientEngine(faked).also { clients.add(it) }
        engine.readyTimeoutOverrideForTest = 250L

        val first = engine.execute(pureGeometryRequest())
        assertTrue("expected a bounded rejection, got $first", first is VisualExecutionResult.Rejected)
        assertEquals(
            VisualExecutionRejection.ENGINE_CLOSED,
            (first as VisualExecutionResult.Rejected).reason,
        )
        assertTrue(
            "the ready timeout must be reported as not ready/loading: ${first.diagnostics}",
            first.diagnostics.contains("not become ready") || first.diagnostics.contains("loading"),
        )
        assertFalse(
            "a ready timeout must not latch the engine unavailable",
            engine.unavailableForTest(),
        )
        assertEquals(
            "the first bind's handshake succeeded once (a ready timeout does not un-count the handshake)",
            1,
            engine.connectionCountForTest(),
        )

        // 有界重试：重新 bind + 重新等 ready（假 worker 仍不 ready）→ 再次如实 FAILED。
        val second = engine.execute(pureGeometryRequest())
        assertTrue(
            "the retry must re-bind and fail honestly again, got $second",
            second is VisualExecutionResult.Rejected,
        )
        assertFalse(
            "repeated ready timeouts must not latch the engine unavailable",
            engine.unavailableForTest(),
        )
        assertEquals(2, engine.connectionCountForTest())
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

    /** 只吞结果/致命通知的回调桩：负向用例（BAD_REQUEST / 并发 BUSY）不关心结果载荷。 */
    private fun sinkCallback(): IVisualJsCallback = object : IVisualJsCallback.Stub() {
        override fun onResult(
            generation: Long,
            callbackRequestId: String?,
            status: Int,
            payload: ParcelFileDescriptor?,
            diagnostics: String?,
        ) {
            runCatching { payload?.close() }
        }

        override fun onFatal(
            generation: Long,
            callbackRequestId: String?,
            code: Int,
            diagnostics: String?,
        ) = Unit
    }

    private companion object {
        /**
         * 设备实测冷启动 attach 4.6–7.5 s（E-01d）。取 7.6 s（实测上界 + 100 ms），
         * 旧 5 s 首连预算下必红；新预算 15 s 留出握手余量。只推迟 attach 回调、不推迟
         * `bindService`，避免把"延迟"与"真实进程 fork"串行相加。
         */
        const val COLD_START_ATTACH_DELAY_MILLIS: Long = 7_600L

        /**
         * 首连超时夹具的 attach 推迟量：超过生产首连预算 15 s（bind + 16 s 交付 attach），
         * 客户端必在预算内超时 → loading 分支。1 s 余量覆盖调度抖动。
         */
        const val CONNECT_TIMEOUT_DELAY_MILLIS: Long = 16_000L

        /**
         * KD-66 关闭门（docs/known-defects.md Reopen condition）：#240 形状须在服务硬期限
         * + 500 ms 内从调用方返回有界拒绝。相对形式——hard 值改动自动跟随，门语义保持
         * "hard+500"；500 ms 是门自带余量，测试不追加任何容差。
         */
        const val HARD_PLUS_500_GATE_MILLIS: Long =
            VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS + 500L
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

/**
 * ready 超时夹具（批次 25 T2，KD-76 ⑤）：拦截 [bindService]，不启动真实 `:visual_js`，
 * 立即在主线程回一个"永不 ready"的本地 fake binder。与 [DelayedAttachContext] 的差异：
 * 这里握手照常完成（attach 无延迟），卡的是握手**之后**的 warm-up ready——真实服务上
 * warm-up 总会完成，该分支只有假 binder 才能确定性触达。
 *
 * 平台前提（AOSP `android-14.0.0_r1` `Binder.java` 本轮实读）：本地 binder 的
 * `linkToDeath`/`unlinkToDeath` 是 no-op 不抛；`isBinderAlive()` 本地恒 true 且非 final，
 * 可覆写。terminate 后翻假 → 客户端死亡确认走 `awaitDeathConfirmation` 的本地事实分支
 * （markDead）→ 确认成功，不触发 fail-closed。同进程直连不产生任何远端调用。
 */
private class NotReadyWorkerContext(base: Context) : ContextWrapper(base) {

    private val handler = Handler(Looper.getMainLooper())

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        handler.post { conn.onServiceConnected(null, NeverReadyWorkerStub()) }
        return true
    }

    // 未发生真实 binding，无可解绑；客户端拆卸路径的 unbindService 全部 no-op。
    override fun unbindService(conn: ServiceConnection) = Unit
}

/** 永不上报 ready 的本地 fake worker（批次 25 T2 ready 超时夹具的唯一 fake）。 */
private class NeverReadyWorkerStub : IVisualJsService.Stub() {

    @Volatile
    private var alive = true

    /** 本地 binder 恒真 → 覆写：terminate 后翻假，供死亡确认的 isBinderAlive 轮询读取。 */
    override fun isBinderAlive(): Boolean = alive

    override fun protocolVersion(): Int = VisualJsWire.PROTOCOL_VERSION

    // != 宿主 UID：通过隔离检查（真实值不重要，隔离断言由真进程用例覆盖）。
    override fun workerUid(): Int = 990_001

    override fun currentGeneration(): Long = 0L

    override fun initialize(generation: Long): Int = VisualJsWire.STATUS_ACCEPTED

    override fun isReady(): Boolean = false

    override fun execute(
        generation: Long,
        requestId: String?,
        request: ParcelFileDescriptor?,
        callback: IVisualJsCallback?,
    ): Int {
        // ready 恒假：客户端在 ready 等待里就会放弃，正常流程到不了这里。
        runCatching { request?.close() }
        return VisualJsWire.STATUS_SHUTTING_DOWN
    }

    override fun terminate(generation: Long) {
        alive = false
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

    /**
     * 轮询服务端 ready（批次 25 T2，KD-76 ①：warm-up 完成标志）。新协议下直连用例必须在
     * 真实 execute 前等 ready——服务端 execute 在 ready 前回 SHUTTING_DOWN（结构性保证：
     * "ready 之前不 arm 程序 watchdog"）。attach 与 ready 是两个独立事实：连接建立时
     * warm-up 可能仍在跑。
     */
    suspend fun awaitReady(timeoutMillis: Long = READY_WAIT_MILLIS): Boolean {
        val target = checkNotNull(service) { "the harness is not connected" }
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (died.isCompleted) return false
            if (target.isReady()) return true
            delay(READY_POLL_MILLIS)
        }
        return !died.isCompleted && target.isReady()
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

        /** harness ready 轮询预算：真实 warm-up 含原生装载，冷启动下可能秒级——给足上界。 */
        const val READY_WAIT_MILLIS: Long = 30_000L

        /** harness ready 轮询间隔。 */
        const val READY_POLL_MILLIS: Long = 100L

        const val EXECUTION_WAIT_MILLIS: Long =
            VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS + 8_000L
    }
}
