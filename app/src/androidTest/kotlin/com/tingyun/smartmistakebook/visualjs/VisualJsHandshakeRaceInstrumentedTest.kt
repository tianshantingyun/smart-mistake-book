package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 批次 25 T1：death-before-registration 竞态的确定性交错用例（修复于批次 24 复核 #3，
 * 提交 `aabae781`）。
 *
 * 被钉住的不变量（`VisualJsClientEngine.Link`，`markConnected`/`markDead`/`isConnected`
 * 共用 `stateLock`，`markDead` 先 CAS `dead` 再取锁）：
 * 1. **markDead 赢得交错时，连接发布必须让位**——已死 link 不得被发布成 CONNECTED、
 *    不得被计入连接账（`connections`）、不得被 [VisualJsClientEngine.connectForTest]
 *    当作可用连接返回；
 * 2. **重建预算仍只扣一次**——握手期死亡不得触发第二次 bind；已连接实例死亡后的
 *    恰好一次重建里，重建握手再输给死亡也不得第二次扣预算、不得第三次 bind。
 *
 * **确定性窗口的选择**：修复后 `markConnected` 的"检查 dead → 置位 CONNECTED"在同一临界区
 * 内，两者之间已无法从外部注入死亡——这正是修复消灭的窗口。可确定性注入的最邻近位置是
 * 发布点之前的最后一个握手 binder 调用：夹具让假 worker 的 `workerUid()` 停车，用例在该
 * 停车窗口内完整落地一次 `markDead`（CAS → DEAD → 完成 `death`/`connected` → handleDeath），
 * 再放行握手。发布点随后必须观察到 `dead=true` 并放弃发布。
 *
 * **红→绿边界（如实）**：本类是回归钉，不运行 Gradle/设备时绿=当前代码的构造性结论；
 * 红对应确定性回归形状——若发布点退回"检查后无条件置 CONNECTED"（`aabae781` 之前的
 * 形状），停车窗口内落地的死亡会被无条件发布覆盖，`connectionCountForTest()` 变 1、
 * 测试转红。对修复前的历史代码，旧实现在发布点前另有一次复查（旧 `:724`），本窗口会被
 * 它拦住；旧缺陷的 3 语句窗口（旧 `:724-:730`，检查与写入之间）没有可从外部确定性注入
 * 的停车点——修复的意义正是让该窗口不复存在。
 *
 * **同进程单测不算隔离验证**（与 [VisualJsIsolationInstrumentedTest] 同一纪律）：本类
 * bindService 被夹具拦截、不启动真实 `:visual_js` 进程，只驱动引擎自身的连接/死亡状态机，
 * 不产生任何隔离进程，也不作为 4E.2 隔离门或 KD-66 关闭门的证据。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsHandshakeRaceInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val engines = mutableListOf<VisualJsClientEngine>()

    @After
    fun closeEverything() {
        engines.forEach { it.close() }
        engines.clear()
    }

    private fun engineOn(fake: ManualBindContext): VisualJsClientEngine =
        VisualJsClientEngine(fake).also { engines.add(it) }

    @Test
    fun markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected() = runBlocking {
        val fake = ManualBindContext(context)
        val engine = engineOn(fake)
        val script = FakeWorkerService.gated()
        fake.enqueueNext(script)

        val connect = async { engine.connectForTest() }
        // 夹具拦截 bindService 并登记引擎的 ServiceConnection；由本用例扮演 framework 投递
        // 握手回调（假 worker binder，握手各步返回合法值）。
        val conn = withTimeout(BIND_TIMEOUT_MILLIS) { script.registered.await() }
        val handshake = launch(Dispatchers.IO) {
            conn.onServiceConnected(null, script)
        }

        // 确定性交错点：握手已越过 onServiceConnected 开头的 dead/ABANDONED/close 检查，
        // 停在发布点前最后一个握手 binder 调用（workerUid）里；此刻 markDead 完整落地。
        assertTrue(
            "the handshake never reached the publish window (workerUid did not park)",
            script.awaitParked(BIND_TIMEOUT_MILLIS),
        )
        conn.onServiceDisconnected(null)
        script.release()

        assertFalse(
            "a link that lost the handshake race to markDead must not be returned as connected",
            connect.await(),
        )
        handshake.join()
        assertFalse(
            "the dead handshake must never read back as CONNECTED",
            engine.awaitConnectedForTest(CONNECTED_POLL_MILLIS),
        )
        assertEquals(
            "publishing a dead link would also count it as a connection " +
                "(the pre-aabae781 unconditional publish would make this 1)",
            0,
            engine.connectionCountForTest(),
        )
        // unavailable 已由 connectLocked 的失败路径置真：此后没有任何路径能再 bind，
        // 等待一段只为让"若回归出现重建 bind"有时间暴露，再断言 bind 恰好 1 次。
        delay(REBUILD_SETTLE_MILLIS)
        assertEquals(
            "the handshake death must not trigger a second bind (rebuild budget untouched, " +
                "no isolated-process amplification)",
            1,
            fake.bindCount.get(),
        )
    }

    @Test
    fun aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce() = runBlocking {
        val fake = ManualBindContext(context)
        val engine = engineOn(fake)

        // 阶段 1：正常首连（假 worker 握手一次通过，无停车窗口）。
        val first = FakeWorkerService.ungated()
        fake.enqueueNext(first)
        val connect1 = async { engine.connectForTest() }
        val conn1 = withTimeout(BIND_TIMEOUT_MILLIS) { first.registered.await() }
        val handshake1 = launch(Dispatchers.IO) {
            conn1.onServiceConnected(null, first)
        }
        assertTrue("the first connect failed", connect1.await())
        handshake1.join()
        assertEquals(1, engine.connectionCountForTest())

        // 阶段 2：已连接实例非预期死亡 → 允许恰好一次重建（预算在 connectLocked 统一扣）。
        // 先登记重建握手剧本（在 workerUid 停车），死亡路径的 rebuildAfterDeath 第二次
        // bindService 时会取到它。
        val rebuild = FakeWorkerService.gated()
        fake.enqueueNext(rebuild)
        conn1.onServiceDisconnected(null)
        val conn2 = withTimeout(BIND_TIMEOUT_MILLIS) { rebuild.registered.await() }
        val handshake2 = launch(Dispatchers.IO) {
            conn2.onServiceConnected(null, rebuild)
        }
        assertTrue(
            "the rebuild handshake never reached the publish window (workerUid did not park)",
            rebuild.awaitParked(BIND_TIMEOUT_MILLIS),
        )
        // 确定性交错：重建握手的发布点前落地第二次死亡，再放行握手。
        conn2.onServiceDisconnected(null)
        rebuild.release()
        handshake2.join()

        assertFalse(
            "a rebuild that lost the handshake race to markDead must not be returned as connected",
            engine.awaitConnectedForTest(CONNECTED_POLL_MILLIS),
        )
        assertEquals(
            "only the first (live-at-publish) connect may be counted; the dead rebuild " +
                "handshake must not be published or counted",
            1,
            engine.connectionCountForTest(),
        )
        assertEquals(
            "the rebuild budget must be spent exactly once (two binds total: first connect " +
                "+ the single allowed rebuild)",
            2,
            fake.bindCount.get(),
        )
        assertFalse(
            "after the budget is spent the client must fail closed instead of connecting again",
            engine.connectForTest(),
        )
        // unavailable 已置真：等待一段让"若回归出现第三次 bind"有时间暴露，再断言不再增长。
        delay(REBUILD_SETTLE_MILLIS)
        assertEquals(
            "a third bind would amplify the isolated process count beyond the once-only " +
                "rebuild budget",
            2,
            fake.bindCount.get(),
        )
    }

    private companion object {
        /** 夹具内所有等待的上界：交错是确定性的，超时只覆盖调度抖动。 */
        const val BIND_TIMEOUT_MILLIS: Long = 5_000L

        /** CONNECTED 复查轮询窗口；终态已由 unavailable/link=null 决定，轮询只做确认。 */
        const val CONNECTED_POLL_MILLIS: Long = 500L

        /**
         * 回归暴露等待窗。健全性：connectForTest 返回 false 时 `unavailable` 已置真且
         * `link` 已清空，此后 `ensureConnected`/`rebuildAfterDeath`/`handleDeath` 没有任何
         * 路径能再到达 bindService；本等待只为在回归形状下让假想的越权 bind 先发生。
         */
        const val REBUILD_SETTLE_MILLIS: Long = 500L
    }
}

/**
 * 交错夹具：拦截 [bindService]，不启动真实 `:visual_js`——只把引擎传入的
 * [ServiceConnection] 登记到当次握手剧本并计数；framework 的连接/死亡回调改由用例线程
 * 手动投递。`unbindService` 为 no-op：从未发生真实 binding，无可解绑。
 */
private class ManualBindContext(base: Context) : ContextWrapper(base) {

    private val scripts = ConcurrentLinkedQueue<FakeWorkerService>()

    /** 引擎发起的真实 bindService 次数＝隔离进程放大断言的观测点。 */
    val bindCount = AtomicInteger(0)

    /** 用例在发起连接前登记下一次 bind 应使用的假 worker 剧本（按 bind 顺序出队）。 */
    fun enqueueNext(script: FakeWorkerService) {
        scripts.add(script)
    }

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        val script = scripts.poll()
            ?: error("no handshake script was queued for bind #${bindCount.get() + 1}")
        bindCount.incrementAndGet()
        script.registered.complete(conn)
        return true
    }

    override fun unbindService(conn: ServiceConnection) = Unit
}

/**
 * 假 worker binder：握手各步返回与真实服务相同的合法值（协议版本、initialize、非宿主
 * UID）。同进程 `Stub.asInterface` 返回本地对象，不产生任何远端调用。
 *
 * [park] 非空时 `workerUid()`（发布点前最后一个握手 binder 调用）先落 [arrived] 门闩再等
 * [releaseLatch]——用例在该窗口内完整落地一次 markDead，握手恢复后直达发布点。
 */
private class FakeWorkerService private constructor(private val park: Park?) :
    IVisualJsService.Stub() {

    class Park {
        val arrived = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)

        fun release() {
            releaseLatch.countDown()
        }
    }

    /** bindService 时由夹具登记的引擎侧 [ServiceConnection]；用例据此手动投递回调。 */
    val registered = CompletableDeferred<ServiceConnection>()

    companion object {
        fun gated(): FakeWorkerService = FakeWorkerService(Park())

        fun ungated(): FakeWorkerService = FakeWorkerService(null)
    }

    /** 用例等待握手进入停车窗口；返回 false 表示交错点没有在期限内到达。 */
    fun awaitParked(timeoutMillis: Long): Boolean =
        park?.arrived?.await(timeoutMillis, TimeUnit.MILLISECONDS) ?: true

    fun release() {
        park?.release()
    }

    override fun protocolVersion(): Int = VisualJsWire.PROTOCOL_VERSION

    override fun workerUid(): Int {
        val pending = park
        if (pending != null) {
            pending.arrived.countDown()
            pending.releaseLatch.await()
        }
        // != 宿主 UID：通过隔离检查，走到发布点（markConnected）。
        return Process.myUid() + 7
    }

    override fun currentGeneration(): Long = 0L

    override fun initialize(generation: Long): Int = VisualJsWire.STATUS_ACCEPTED

    // 批次 25 T2（KD-76）：握手后客户端轮询 ready（新进程 warm-up 完成标志）。本夹具
    // 只驱动连接/死亡状态机，一律视为"已装载完成"——返回 false 会让 connectLocked 在
    // ready 预算内等待并按 loading 失败，本文件的两个用例都会红。
    override fun isReady(): Boolean = true

    override fun execute(
        generation: Long,
        requestId: String?,
        request: ParcelFileDescriptor?,
        callback: IVisualJsCallback?,
    ): Int = VisualJsWire.STATUS_BAD_REQUEST

    override fun terminate(generation: Long) = Unit
}
