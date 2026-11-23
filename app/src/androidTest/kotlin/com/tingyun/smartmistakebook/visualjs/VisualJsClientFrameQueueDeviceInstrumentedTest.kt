package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/**
 * P-09 判据②（R2 G-2，批次 38 计划 §1 B.6）的**客户端队列层设备负向**：经生产
 * `VisualExecutionEngine.execute`（真 `:visual_js` 隔离进程 + 真 AIDL + 真 PFD）的
 * "4 program 并发 → 1 在飞 + ≤2 待绘 + 第 4 个 BUSY / latest-wins / 跨 program FIFO"。
 *
 * 准入清单 P-09 行（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:132`）
 * 判据原文："现有设备 BUSY 用例在服务端层
 * （aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight），经 engine.execute 的
 * '4 program 并发 → 1 在飞 + ≤2 待绘 + 第 4 个 BUSY / latest-wins' 只在 JVM 队列状态机
 * 验过；设备：经 engine.execute 的客户端队列负向（4 program 并发 → 第 4 个 BUSY /
 * latest-wins）"。本套件补的正是该缺口；引擎级单在飞（服务端纵深防御半边）已由 core:data
 * 的 `aConcurrentExecutionIsRejectedAsBusy` 设备绿覆盖，JVM 队列状态机（含 latest-wins
 * 原地替换、取消出队、close 闩死、死亡清队、重建可再收帧）已由
 * `VisualJsFrameQueueTest` 五例覆盖——本套件不重复其断言，只把组合语义在**真进程端到端**
 * 上钉住。
 *
 * 在真实 worker 死亡后，测试包装器暂存第二次绑定的 onServiceConnected 回调。
 * 收到回调门信号后，首帧仍占据在飞槽，后续帧以 UNDISPATCHED 启动并同步进入客户端队列。
 * 满队列与 latest-wins 断言完成后释放回调，验证真实 Binder/PFD 渲染与跨 program FIFO。
 *
 * **夹具复用**：请求构造与既有隔离套件 `pureGeometryRequest` 同形（可渲染的纯几何
 * 程序，`VisualJsIsolationInstrumentedTest.kt:788`）；引擎构造/关闭/测试 seam 用法与
 * `client()`（:119）与 `@After closeEverything`（:108）同纪律；
 * `abandonForTest` 用法与 `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（:293）一致。
 * 断言只增不减：BUSY 的诊断前缀一并断言（与 JVM 套件同纪律，防语义漂移成普通拒绝）。
 *
 * 覆盖映射（契约 §4:64 / R1' §4 断言清单）：
 * - A-6/A-9：第 4 个不同 program 在 1 在飞 + 2 待绘时立即 `Rejected(BUSY)`，诊断含
 *   "frame queue is full"；在飞与两个待绘帧照常完成（保留原帧）；
 * - A-7/A-10：同 program 新 state 原地顶掉旧待绘 state——旧等待者 `Rejected(BUSY)` 且
 *   诊断含 "superseded by a newer state of the same program"，新 state 渲染，**不同
 *   program 的帧不被顶掉**；
 * - A-8：跨 program 出队顺序 = 入队顺序（在飞首帧 → 被顶替槽位上的新 state → 后入队
 *   帧），完成时刻单调不减（消费者严格串行，顺序由队列结构而非时序保证）。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsClientFrameQueueDeviceInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val engines = mutableListOf<VisualJsClientEngine>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeEverything() {
        engines.forEach { it.close() }
        engines.clear()
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    /**
     * 契约 §64 的端到端组合负向。流程：
     * 1. 首连真 worker（`connectForTest` 蕴含 ready——连接发布前已过 ready 门）；
     * 2. `abandonForTest()` 真实终止 worker（死亡在 2 s 确认预算内确认，返回 true）；
     * 3. 首帧（program-A）入队即直跑、卡在重建窗口（in-flight）；随后 B1 → B2（同
     *    program latest-wins）→ C → D 依次入队：队列形状 = 1 在飞 + [B2, C]，D 满→BUSY；
     * 4. 重建完成、会话串行排水：A → B2 → C 全部 Rendered（完成时刻单调），B1 的等待者
     *    收 BUSY superseded，D 收 BUSY full；
     * 5. 连接计数恰为 2（首连 + 唯一一次重建），证明本例没有额外放大进程数。
     */
    @Test
    fun aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight() =
        runBlocking {
            val gated = ReconnectGateContext(context, newScope())
            val engine = VisualJsClientEngine(gated).also { engines.add(it) }
            assertTrue("initial connect failed", engine.connectForTest())
            assertEquals(1, engine.connectionCountForTest())

            // 真实终止当前 worker（死亡确认 ≤2 s），打开"恰好一次重建"的确定性排队窗口。
            assertTrue(
                "the abandon did not confirm the worker death within its budget (environment " +
                    "or death-path regression); the queue window below cannot be constructed",
                engine.abandonForTest(),
            )

            val frames = FrameRecorder(engine, newScope())
            // 首帧停在真实重连回调门上，后续调用同步入队。
            frames.fire("A", request("program-queue-a", "scene-A"))
            withTimeout(5_000) { gated.reconnectAttached.await() }
            // B 的旧 state 先入队，再让同 program 的新 state 原地顶掉它（latest-wins）。
            frames.fire("B1", request("program-queue-b", "scene-B1"))
            frames.fire("B2", request("program-queue-b", "scene-B2"))
            // C 占第二个待绘槽；D 是第 4 个不同 program：满队列 → BUSY。
            frames.fire("C", request("program-queue-c", "scene-C"))
            frames.fire("D", request("program-queue-d", "scene-D"))

            val busyFull = frames.await("D")
            val superseded = frames.await("B1")
            gated.releaseReconnect.complete(Unit)
            val renderedA = frames.await("A")
            val renderedB2 = frames.await("B2")
            val renderedC = frames.await("C")

            // 第 4 个不同 program：满队列 BUSY，诊断钉住"1 in-flight + 2 pending"语义，
            // 防止漂移成普通拒绝（与 JVM 套件断言口径一致）。
            assertRejected(
                busyFull,
                VisualExecutionRejection.BUSY,
                "frame queue is full",
                "the fourth distinct program must be rejected by the client queue",
            )
            // 同 program 旧 state：latest-wins 原地顶掉，旧等待者回 BUSY 背压（保原帧）。
            assertRejected(
                superseded,
                VisualExecutionRejection.BUSY,
                "superseded by a newer state of the same program",
                "the older pending state of program-B must be superseded, not executed",
            )
            // 在飞帧与两个待绘帧照常完成（满队列 BUSY 保留原帧）。
            assertRendered(renderedA, "scene-A", "the in-flight frame must complete")
            // 最新 state 渲染，且不是被顶掉的旧 state（内容级 latest-wins 钉子）。
            val newestState = assertRendered(
                renderedB2,
                "scene-B2",
                "the newest state of program-B must render",
            )
            assertRendered(renderedC, "scene-C", "a different program must not be superseded")
            assertTrue(
                "the superseded state must never render, got: $newestState",
                !newestState.sceneJson.contains("scene-B1"),
            )

            // 跨 program FIFO：消费者严格串行，完成时刻单调不减，顺序=入队顺序
            //（B2 占据 B1 的槽位，先于 C）。
            val order = frames.completionTimes()
            assertTrue(
                "cross-program drain order must follow the enqueue order (A → B2 → C), got $order",
                order.getValue("A") <= order.getValue("B2") &&
                    order.getValue("B2") <= order.getValue("C"),
            )

            // 恰好一次重建：首连 + 唯一重建 = 2。BUSY/顶替路径不得额外放大进程数。
            assertEquals(
                "the queue example must spend the rebuild budget exactly once",
                2,
                engine.connectionCountForTest(),
            )
        }

    // ---------------------------------------------------------------- 夹具

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes.add(it) }

    /** Holds the real replacement binder until all queue assertions have observed saturation. */
    private class ReconnectGateContext(base: Context, private val scope: CoroutineScope) : ContextWrapper(base) {
        val reconnectAttached = CompletableDeferred<Unit>()
        val releaseReconnect = CompletableDeferred<Unit>()
        private val bindCount = java.util.concurrent.atomic.AtomicInteger()
        private val delegates = ConcurrentHashMap<ServiceConnection, ServiceConnection>()

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            val hold = bindCount.incrementAndGet() > 1
            val delegate = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (!hold) {
                        conn.onServiceConnected(name, binder)
                        return
                    }
                    scope.launch(Dispatchers.Main) {
                        reconnectAttached.complete(Unit)
                        releaseReconnect.await()
                        if (delegates.containsKey(conn)) conn.onServiceConnected(name, binder)
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) = conn.onServiceDisconnected(name)
                override fun onBindingDied(name: ComponentName?) = conn.onBindingDied(name)
                override fun onNullBinding(name: ComponentName?) = conn.onNullBinding(name)
            }
            delegates[conn] = delegate
            val bound = baseContext.bindService(service, delegate, flags)
            if (!bound) delegates.remove(conn)
            return bound
        }

        override fun unbindService(conn: ServiceConnection) {
            baseContext.unbindService(delegates.remove(conn) ?: conn)
        }
    }

    /** 与既有隔离套件 `pureGeometryRequest` 同形的可渲染程序，programId/sceneId 参数化。 */
    private fun request(programId: String, sceneId: String) = VisualExecutionRequest(
        programId = programId,
        sourceJs = """
            const visual = {
              prepare: function () { return []; },
              render: function (input) {
                return { schemaVersion: 1, sceneId: '$sceneId', revision: input.revision,
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

    private fun assertRendered(
        result: VisualExecutionResult,
        expectedSceneId: String,
        message: String,
    ): VisualExecutionResult.Rendered {
        val rendered = result as? VisualExecutionResult.Rendered
        assertTrue("$message, got $result", rendered != null)
        assertTrue(
            "the rendered scene must be '$expectedSceneId', got: ${rendered!!.sceneJson}",
            rendered.sceneJson.contains(expectedSceneId),
        )
        return rendered
    }

    private fun assertRejected(
        result: VisualExecutionResult,
        reason: VisualExecutionRejection,
        diagnosticsFragment: String,
        message: String,
    ) {
        val rejected = result as? VisualExecutionResult.Rejected
        assertNotNull("$message, got $result", rejected)
        assertEquals(reason, rejected!!.reason)
        assertTrue(
            "diagnostics must keep the '$diagnosticsFragment' semantics: ${rejected.diagnostics}",
            rejected.diagnostics.contains(diagnosticsFragment),
        )
    }

    /**
     * 帧发射器：每帧在独立协程里走生产 `engine.execute`，完成时刻与结果分别记账。
     * 等待方用 [withTimeout] 界定——回归形状（等待者悬挂）按超时红，不挂死套件。
     */
    private class FrameRecorder(
        private val engine: VisualJsClientEngine,
        private val scope: CoroutineScope,
    ) {
        private val results = ConcurrentHashMap<String, CompletableDeferred<VisualExecutionResult>>()
        private val completedAt = ConcurrentHashMap<String, Long>()

        fun fire(tag: String, request: VisualExecutionRequest) {
            val waiter = CompletableDeferred<VisualExecutionResult>()
            results[tag] = waiter
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val outcome = engine.execute(request)
                completedAt[tag] = SystemClock.uptimeMillis()
                waiter.complete(outcome)
            }
        }

        suspend fun await(tag: String): VisualExecutionResult =
            withTimeout(FRAME_COMPLETION_TIMEOUT_MILLIS) {
                checkNotNull(results[tag]) { "frame $tag was never fired" }.await()
            }

        fun completionTimes(): Map<String, Long> = completedAt.toMap()
    }

    private companion object {
        /**
         * 单帧完成上界：最坏形状 = 在飞帧的重建走满 10 s 重建预算 + ready 10 s + 会话
         * 2.5 s——正常重建 ~3 s，该上界只兜回归/环境异常，不是语义断言。
         */
        const val FRAME_COMPLETION_TIMEOUT_MILLIS: Long = 30_000L
    }
}
