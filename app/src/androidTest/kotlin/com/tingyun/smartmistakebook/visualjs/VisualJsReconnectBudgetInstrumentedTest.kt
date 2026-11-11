package com.tingyun.smartmistakebook.visualjs

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * P-04 预算注入形设备例的收尾半边（批次 38 计划 §1 B.8）：**重建预算 10 s** 的注入形状
 * ——已连接 worker 死亡、重建 attach 超过重建预算时，重建按 **loading** 如实失败：
 * 诊断说明 still-loading、不锁存 `unavailable`、不误判成结构失败，且纯 loading 失败后的
 * 重试仍被允许（KD-76 ⑤ 的重建路径钉子）。
 *
 * 准入清单 P-04 行（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:127`）
 * 的预算三元组"首连预算 15 s、重建 10 s、死亡确认 2 s"注入形设备例现状：
 * - 首连 15 s：已有（`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`，
 *   attach 推迟 16 s > 15 s，`VisualJsIsolationInstrumentedTest.kt:705`）；
 * - 死亡确认 2 s：批次 38 同批补齐（`VisualJsDeathUnconfirmedFailClosedInstrumentedTest`，
 *   未确认半边 fail-closed 计时门）；
 * - **重建 10 s：此前无用例**——本套件补齐。三个预算的注入形设备例就此成套。
 *
 * **确定性窗口**（夹具复用既有 `DelayedAttachContext` 形态，
 * `VisualJsIsolationInstrumentedTest.kt:903`，逐机制同构：真实 `bindService` 立即执行、
 * 仅推迟 `onServiceConnected`、unbind 正确解绑）：attach 每次推迟 11 s——
 * - 首连预算 15 s（`VisualJsClientEngine.kt:1227`）> 11 s：首连照常成功（attach 11 s +
 *   ready warm-up ~2 s，各在其预算内），保证"失败只来自重建预算"这一归因；
 * - 重建预算 10 s（`VisualJsClientEngine.kt:1233`，private，测试以字面量钉住）< 11 s：
 *   死亡后的重建 attach 必然在 10 s 预算内超时 → loading 分支。
 *
 * **断言映射**：
 * 1. 死亡确认后重建的请求有界返回 `ENGINE_CLOSED`，诊断含 "loading" 且含 "10000 ms"
 *    ——重建预算值的字面钉子（预算改动若未回写断言即红）；
 * 2. `unavailableForTest()` 保持 false——loading 不是结构失败（与死亡未确认/绑定失败
 *    的锁存相反，两态成对）；
 * 3. `connectionCountForTest()` 保持 1——失败的重建握手不计入连接账；
 * 4. 重试仍被允许：第二次 execute 重新 bind（loading 豁免重建预算检查，
 *    `VisualJsClientEngine.kt:640-643`），再次如实 FAILED 且仍不锁存——KD-76 ⑤
 *    "加载失败不得一次失败即永久 unavailable"在重建路径上成立；
 * 5. 每次失败的 attach 都被解绑（夹具 unbind 正确性 = 无孤儿 binding/进程，与既有
 *    16 s 首连超时用例同机制）。
 *
 * 与既有用例的关系：`aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`
 * （7.6 s，首连**成功**路径）、`aFirstConnectTimeout...`（16 s，首连**失败**→loading）
 * 钉的是首连预算两半；本用例钉重建预算半边，三者共用同一夹具机制、断言互不覆盖。
 * 设备执行归设备轨；本轨不运行 Gradle。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsReconnectBudgetInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()

    @After
    fun closeEverything() {
        engines.forEach { it.close() }
        engines.clear()
    }

    @Test
    fun aRebuildAttachBeyondTheReconnectBudgetFailsAsLoadingWithoutLatchingAndRetriesStayAllowed() =
        runBlocking {
            val delayed = RebuildDelayedAttachContext(context, REBUILD_BUDGET_EXCEED_DELAY_MILLIS)
            val engine = VisualJsClientEngine(delayed).also { engines.add(it) }

            // 首连：attach 推迟 11 s 仍在 15 s 首连预算内（+ready warm-up ~2 s 在其独立
            // 预算内）——首连成功，保证后续失败只能归因重建预算。
            assertTrue("initial connect failed", engine.connectForTest())
            assertEquals(1, engine.connectionCountForTest())

            // 真实终止当前 worker（死亡确认 ≤2 s，路径与既有 confirmed-abandon 用例同）。
            assertTrue(
                "the abandon did not confirm the worker death within its budget",
                engine.abandonForTest(),
            )

            // 重建：attach 推迟 11 s > 10 s 重建预算 → loading，如实失败。
            val first = engine.execute(pureGeometryRequest())
            val firstRejected = first as? VisualExecutionResult.Rejected
            assertNotNull("expected a bounded rejection, got $first", firstRejected)
            assertEquals(
                VisualExecutionRejection.ENGINE_CLOSED,
                firstRejected!!.reason,
            )
            assertTrue(
                "the rebuild attach timeout must be reported as loading: " +
                    "${firstRejected.diagnostics}",
                firstRejected.diagnostics.contains("loading"),
            )
            assertTrue(
                "the loading diagnostics must pin the reconnect budget value: " +
                    "${firstRejected.diagnostics}",
                firstRejected.diagnostics.contains("$RECONNECT_TIMEOUT_MILLIS ms"),
            )
            assertFalse(
                "a rebuild loading timeout must not latch the engine unavailable",
                engine.unavailableForTest(),
            )
            assertEquals(
                "the timed-out rebuild handshake must not be counted as a connection",
                1,
                engine.connectionCountForTest(),
            )

            // 重试：纯 loading 失败豁免重建预算检查（KD-76 ⑤）→ 重新 bind → 再次如实
            // FAILED，仍不锁存。"最终 FAILED"不是"永久 unavailable"。
            val second = engine.execute(pureGeometryRequest())
            val secondRejected = second as? VisualExecutionResult.Rejected
            assertNotNull("the retry must fail honestly, got $second", secondRejected)
            assertEquals(
                VisualExecutionRejection.ENGINE_CLOSED,
                secondRejected!!.reason,
            )
            assertTrue(
                "the retried rebuild must fail as loading again: ${secondRejected.diagnostics}",
                secondRejected.diagnostics.contains("loading"),
            )
            assertFalse(
                "repeated rebuild loading timeouts must not latch the engine unavailable",
                engine.unavailableForTest(),
            )
            assertEquals(
                "neither timed-out rebuild may be counted as a connection",
                1,
                engine.connectionCountForTest(),
            )
            // 留出晚到 attach 的收尾窗口：loading 放弃后的晚到连接必须被 ABANDONED 分支
            // 收尾（不锁存、不重建）——等待它落地后再核对锁存位，暴露任何回归形状。
            delay(LATE_ATTACH_SETTLE_MILLIS)
            assertFalse(
                "a late attach after a loading abandonment must not latch the engine",
                engine.unavailableForTest(),
            )
        }

    // ---------------------------------------------------------------- 夹具

    private fun pureGeometryRequest() = VisualExecutionRequest(
        programId = "program-reconnect-budget",
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
    )

    /**
     * 冷启动加载态夹具（与既有 `DelayedAttachContext`
     * `VisualJsIsolationInstrumentedTest.kt:903` 同机制复制；原件为本文件私有不可跨文件
     * 复用、且顶层私有类同名会撞 JVM 类名，故改名 `RebuildDelayedAttachContext`，语义
     * 零改动）：真实 `bindService` 立即执行，但把 `onServiceConnected` 推迟
     * [attachDelayMillis]。`unbindService` 用 `bindService` 时注册的那个连接实例解绑；
     * 客户端提前放弃（预算超时）时仍能正确解绑，不残留 binding 或泄漏隔离进程。
     */
    private class RebuildDelayedAttachContext(
        base: Context,
        private val attachDelayMillis: Long,
    ) : ContextWrapper(base) {

        private val handler = Handler(Looper.getMainLooper())
        private val delegates = ConcurrentHashMap<ServiceConnection, ServiceConnection>()
        private val attachDeadlines = ConcurrentHashMap<ServiceConnection, Long>()

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            // 期限从 bindService 起算：客户端看到的回调时刻稳定在 bind + attachDelayMillis，
            // 夹具不受真实进程启动快慢影响。
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

    private companion object {
        /**
         * attach 推迟量：> 重建预算 10 s（重建必超时）且 < 首连预算 15 s（首连必成功，
         * 失败归因单一化——attach 11 s 在 15 s 内、ready warm-up ~2 s 在其独立 10 s 预算
         * 内，两侧各留 ≥1 s / ≥8 s 裕量）。
         */
        const val REBUILD_BUDGET_EXCEED_DELAY_MILLIS: Long = 11_000L

        /**
         * 重建预算字面钉子：`VisualJsClientEngine` private companion 的
         * `RECONNECT_TIMEOUT_MILLIS`（诊断文案 "within 10000 ms" 承载它）。预算改动必须
         * 与本断言同批回写。
         */
        const val RECONNECT_TIMEOUT_MILLIS: Long = 10_000L

        /**
         * 晚到 attach 收尾窗口：第二次 bind 的 attach 在 bind+11 s 到达，其 loading 超时
         * 在 bind+10 s——晚到连接落在放弃后 ~1 s。等待窗覆盖它，让"晚到 attach 不得锁存"
         * 的回归形状有时间暴露（既有首连超时用例的晚到收尾语义，KD-76 ⑤）。
         */
        const val LATE_ATTACH_SETTLE_MILLIS: Long = 3_000L
    }
}
