package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * P-10 判据（契约 §4:68；批次 38 计划 §1 B.7）的死亡未确认设备负向：**人为"丢失"死亡
 * 事实**后，宿主必须在死亡确认预算（2 s）内 fail-closed——不重建、不并发放大进程数，
 * 后续请求一律 `ENGINE_CLOSED`，且 loading 形状与结构失败对调用方可区分（KD-76 ⑤ 的
 * fallback 语义在引擎边界呈现）。
 *
 * 准入清单 P-10 行（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:133`）
 * 验收原文："人为延迟/丢失 death：2 s 内 fail-closed、不再 bind、后续 `ENGINE_CLOSED`；
 * UI 显示 fallback（设备+UI）"。本用例落前三项（引擎边界可观测面）；**UI 呈现半边不在
 * 本套件**——呈现层归 P-04 调用方生命周期层/主会话（`VisualJsClientEngine` KDoc :69 与
 * 准入清单 P-07 ⑤"加载态 UI/文案面归主会话"同口径），本用例钉住的是呈现层赖以取材的
 * 引擎事实：锁存位、诊断区分、连接记账。
 *
 * **确定性依据（为何必须假 binder）**：真实 worker 在 `terminate(generation)` 后
 * `killSelf`（`VisualJsService.terminate:307`），死亡事实（binderDied）毫秒级到达，
 * "死亡未确认"分支（`VisualJsClientEngine.abandon:821-833`）在真服务上不可确定性触达
 * ——与 ready 超时分支同型，只有假 binder 才能确定性构造（既有先例
 * `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds` 的
 * `NotReadyWorkerContext`，`VisualJsIsolationInstrumentedTest.kt:965`；fake-binder 平台
 * 前提已取证：AOSP `android-14.0.0_r1` `Binder.java` 本地 `linkToDeath`/`unlinkToDeath`
 * 是 no-op 不抛、`isBinderAlive()` 非 final 可覆写）。本夹具让 `terminate` 成为 no-op、
 * `isBinderAlive` 恒真、回调永不投递——死亡事实与本地事实双双缺席，
 * `awaitDeathConfirmation` 必然轮询满 2 s 预算后返回 false。
 *
 * **断言映射**（时间用单钟 `SystemClock.uptimeMillis()` 端到端相减，与 KD-66 门同式；
 * 预算常量：宿主阶段 2500 ms = `VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS`，
 * 死亡确认 2 s = `VisualJsClientEngine.kt:1240` 的
 * `DEATH_CONFIRM_TIMEOUT_MILLIS`（private，测试以字面量钉住），+500 ms 为门自带余量，
 * 不追加任何容差）：
 * 1. 超时请求有界返回 `HARD_DEADLINE_EXCEEDED`（宿主阶段期限路径，不是崩溃/悬挂）；
 * 2. 端到端释放 ≤ 2500 + 2000 + 500 ms——"2 s 内 fail-closed"的计时面（fail-closed 发生
 *    在确认预算耗尽处，对调用方可见的完整释放含宿主阶段期限）；
 * 3. `unavailableForTest()` 锁存为真（结构失败，与 loading 形状的非锁存相反——既有
 *    `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching` 钉住
 *    loading 半边，本用例钉住结构半边，两态成对）；
 * 4. `connectForTest()` 拒绝 + `bindCount==1`（结算等待后再查仍为 1）——**不再 bind、
 *    不并发放大**（与 `VisualJsHandshakeRaceInstrumentedTest` 的 bindCount 观测同式）；
 * 5. 显式重试（`explicitUserRetry=true`）也被 `ENGINE_CLOSED` 拒绝且诊断含
 *    "unconfirmed death"——fail-closed 强于显式重试，诊断区分结构失败与 loading
 *    （`ensureConnected` 的锁存文案，`VisualJsClientEngine.kt:586-590`）；
 * 6. 同源码非重试请求回 `PROGRAM_FUSED`——宿主阶段超时路径的熔断先于结构门（既有
 *    契约 §4:70 行为，此处顺带钉住检查顺序，非本用例主体）。
 *
 * **同进程单测不算隔离验证**（与 [VisualJsHandshakeRaceInstrumentedTest] 同一纪律）：
 * 本类 bindService 被夹具拦截、不启动真实 `:visual_js` 进程，只驱动引擎自身的
 * 死亡确认/fail-closed 状态机；隔离进程语义由真进程套件
 * （`VisualJsIsolationInstrumentedTest`）承担。设备执行归设备轨；本轨不运行 Gradle。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsDeathUnconfirmedFailClosedInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()

    @After
    fun closeEverything() {
        engines.forEach { it.close() }
        engines.clear()
    }

    @Test
    fun aWorkerDeathThatIsNeverConfirmedFailsClosedWithinItsBudgetAndNeverBindsAgain() =
        runBlocking {
            val fake = UnconfirmableDeathWorkerContext(context)
            val engine = VisualJsClientEngine(fake).also { engines.add(it) }

            // 假 worker 握手/ready 立即通过（本地 binder，毫秒级）。
            assertTrue("initial connect failed", engine.connectForTest())
            assertEquals(1, engine.connectionCountForTest())

            // 生产路径注入：execute 被 ACCEPTED 但回调永不到达 → 宿主阶段期限（2500 ms）
            // → 熔断 + abandon → terminate（no-op）→ 死亡事实与 isBinderAlive 双双缺席
            // → 确认轮询跑满 2 s 预算 → fail-closed。
            val submittedAt = SystemClock.uptimeMillis()
            val outcome = engine.execute(pureGeometryRequest())
            val returnedAt = SystemClock.uptimeMillis()
            val elapsed = returnedAt - submittedAt

            // ① 有界拒绝：宿主阶段期限映射（不是异常穿透、不是悬挂）。
            val rejected = outcome as? VisualExecutionResult.Rejected
            assertNotNull("expected a bounded rejection, got $outcome", rejected)
            assertEquals(
                "the host phase deadline must map to the hard-deadline rejection, " +
                    "got ${rejected!!.diagnostics}",
                VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                rejected.reason,
            )
            // ② 计时门：2500（宿主阶段）+ 2000（死亡确认预算）+ 500（门自带余量，
            //    不追加容差）。fail-closed 只能在确认预算耗尽处发生。
            assertTrue(
                "the caller was released in $elapsed ms; the fail-closed gate is " +
                    "$FAIL_CLOSED_GATE_MILLIS ms (host phase " +
                    "${VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS} + death confirm " +
                    "$DEATH_CONFIRM_TIMEOUT_MILLIS + 500)",
                elapsed <= FAIL_CLOSED_GATE_MILLIS,
            )
            // ③ 结构失败锁存：与 loading 形状（不锁存）成对相反。
            assertTrue(
                "an unconfirmed death must latch the engine unavailable (structural failure)",
                engine.unavailableForTest(),
            )
            // ④ 不再 bind：显式拒绝 + bind 计数不增长（结算等待暴露任何回归性越权 bind）。
            assertFalse(
                "a fail-closed engine must not connect again",
                engine.connectForTest(),
            )
            delayForBindSettle()
            assertEquals(
                "an unconfirmed death must never trigger another bind (no process amplification)",
                1,
                fake.bindCount.get(),
            )
            assertEquals(
                "no additional handshake may be counted after the fail-closed",
                1,
                engine.connectionCountForTest(),
            )

            // ⑤ 后续请求：显式重试也被 ENGINE_CLOSED 拒绝，诊断区分结构失败
            //（"unconfirmed death"）——loading 形状的同位断言是 "loading" 诊断
            //（既有 aFirstConnectTimeout 用例），两态成对可区分。
            val retry = engine.execute(pureGeometryRequest(explicitUserRetry = true))
            val retryRejected = retry as? VisualExecutionResult.Rejected
            assertNotNull("expected a bounded rejection, got $retry", retryRejected)
            assertEquals(
                VisualExecutionRejection.ENGINE_CLOSED,
                retryRejected!!.reason,
            )
            assertTrue(
                "the fail-closed diagnostics must name the unconfirmed death (the fallback " +
                    "presentation reads this distinction): ${retryRejected.diagnostics}",
                retryRejected.diagnostics.contains("unconfirmed death"),
            )
            delayForBindSettle()
            assertEquals(
                "even the explicit retry must not rebind",
                1,
                fake.bindCount.get(),
            )

            // ⑥ 同源码非重试请求：宿主阶段超时已置熔断，检查顺序在结构门之前。
            val fused = engine.execute(pureGeometryRequest())
            assertEquals(
                VisualExecutionRejection.PROGRAM_FUSED,
                (fused as VisualExecutionResult.Rejected).reason,
            )
        }

    // ---------------------------------------------------------------- 夹具

    private fun pureGeometryRequest(explicitUserRetry: Boolean = false) =
        VisualExecutionRequest(
            programId = "program-death-unconfirmed",
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

    private suspend fun delayForBindSettle() {
        delay(BIND_SETTLE_MILLIS)
    }

    /**
     * 死亡不可确认夹具（批次 38 T5；形态复用 `NotReadyWorkerContext`
     * `VisualJsIsolationInstrumentedTest.kt:965` 与 `ManualBindContext` 的 bindCount 观测
     * `VisualJsHandshakeRaceInstrumentedTest.kt:212`）：拦截 [bindService]（计数），主线程
     * 立即回一个"永生且永不回调"的本地假 worker。与 ready 夹具的关键差异：
     * `terminate()` 是 **no-op**（NotReady 夹具翻 `isBinderAlive` 以确认死亡）、`isReady()`
     * 恒真（跨过 ready 门）、`execute` 回 ACCEPTED 但**永不投递回调**——宿主阶段期限与
     * 死亡确认两条预算都被确定性跑满。
     */
    private class UnconfirmableDeathWorkerContext(base: Context) : ContextWrapper(base) {

        /** 引擎发起的真实 bindService 次数＝"不再 bind/不并发放大"断言的观测点。 */
        val bindCount = AtomicInteger(0)

        private val handler = Handler(Looper.getMainLooper())

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCount.incrementAndGet()
            handler.post { conn.onServiceConnected(null, UnconfirmableDeathWorkerStub()) }
            return true
        }

        // 未发生真实 binding，无可解绑；引擎拆卸路径的 unbindService 全部 no-op。
        override fun unbindService(conn: ServiceConnection) = Unit
    }

    /**
     * 永不死亡、永不回调的本地假 worker。本地 binder 的 `linkToDeath` 是 no-op——死亡
     * 通知永远不会到达；`isBinderAlive()` 显式覆写为恒真——本地事实兜底也永不翻转。
     * `awaitDeathConfirmation` 的两条判据因此都只能等到 2 s 预算耗尽。
     */
    private class UnconfirmableDeathWorkerStub : IVisualJsService.Stub() {

        override fun isBinderAlive(): Boolean = true

        override fun protocolVersion(): Int = VisualJsWire.PROTOCOL_VERSION

        // != 宿主 UID：通过隔离检查（隔离语义由真进程套件覆盖）。
        override fun workerUid(): Int = Process.myUid() + 7

        override fun currentGeneration(): Long = 0L

        override fun initialize(generation: Long): Int = VisualJsWire.STATUS_ACCEPTED

        override fun isReady(): Boolean = true

        override fun execute(
            generation: Long,
            requestId: String?,
            request: ParcelFileDescriptor?,
            callback: IVisualJsCallback?,
        ): Int {
            // 接受请求但永不回调：宿主阶段期限（2500 ms）必然到点。
            runCatching { request?.close() }
            return VisualJsWire.STATUS_ACCEPTED
        }

        override fun terminate(generation: Long) = Unit
    }

    private companion object {
        /**
         * fail-closed 计时门：宿主阶段期限（2500 ms）+ 死亡确认预算（2 s，
         * `VisualJsClientEngine` private companion 常量的字面量钉子）+ 500 ms 门自带余量。
         * 与 KD-66 hard+500 ms 门同式：余量之内不追加任何容差。
         */
        const val DEATH_CONFIRM_TIMEOUT_MILLIS: Long = 2_000L

        val FAIL_CLOSED_GATE_MILLIS: Long =
            VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS + DEATH_CONFIRM_TIMEOUT_MILLIS + 500L

        /** bind 观测结算等待（`VisualJsHandshakeRaceInstrumentedTest` 同式）。 */
        const val BIND_SETTLE_MILLIS: Long = 500L
    }
}
