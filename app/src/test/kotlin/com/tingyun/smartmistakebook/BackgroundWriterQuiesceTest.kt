package com.tingyun.smartmistakebook

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-61（批次 19 T3）：恢复/清库关库前的应用级 collector 硬性 quiesce。
 *
 * 钉四条契约（每条对应一个真实失败面）：
 * 1. **安全等待**：在途 collector 的最后一笔写必须在 quiesce 返回**之前**跑完——join 等
 *    "最后一笔归还连接"，关库瞬间不再有在途 SQL；
 * 2. **有界**：挂死（不吃取消）的 collector 不能把恢复无限拖住，超时返回 false；
 * 3. **不再有新写**：quiesce 后 scope 终态，新 `launch` 是 no-op——"排空后又冒出新写"
 *    这条路被结构性关死；
 * 4. **空转便宜**：没有 collector 时立即返回 true（常态恢复不为 quiesce 付额外等待）。
 */
class BackgroundWriterQuiesceTest {

    @Test
    fun `an in-flight writer finishes its write before quiesce returns`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writerStarted = CompletableDeferred<Unit>()
        val writeFinished = CompletableDeferred<Unit>()
        scope.launch {
            // 先向测试确认 writer 已真正开始执行：launch 只是把协程排队，cancel 若赶在
            // 协程体首行之前到达，协程会直接以取消完成、body 从不运行——"在途"这个前提
            // 就不成立了（首版测试正因为缺这一步而自身带竞态）。
            writerStarted.complete(Unit)
            // 模拟一笔在途 DAO 写：SQL 段不响应取消（协作式取消里在途语句要跑完才归还
            // 连接），quiesce 的限时 join 必须等它结束——这正是本契约要钉的行为。
            withContext(NonCancellable) {
                writeFinished.complete(Unit)
            }
        }
        writerStarted.await()

        val drained = quiesceApplicationScopeWriters(scope = scope, timeoutMillis = 2_000L)

        assertTrue("无挂死 collector 时必须排空成功", drained)
        assertTrue(
            "在途写必须先于 quiesce 返回完成（关库前最后一笔归还连接）",
            writeFinished.isCompleted,
        )
    }

    @Test
    fun `a stuck writer that ignores cancellation is abandoned at the timeout`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writerStarted = CompletableDeferred<Unit>()
        scope.launch {
            // 与上一个测试同因：launch 只排队，cancel 若赶在协程体首行之前到达，
            // 协程会直接以取消完成、body 从不运行——joinAll 秒过、"挂死"前提不成立
            // （2026-10-25 00:49 复跑 time=0.005s、drained=true 即此象）。先确认
            // 挂死 writer 已真正在途，再进超时排空。
            writerStarted.complete(Unit)
            // 不吃取消的挂死 collector（等价于卡在网络/原生调用里）：join 永远等不完。
            withContext(NonCancellable) {
                CompletableDeferred<Unit>().await()
            }
        }
        writerStarted.await()

        val startedAt = System.nanoTime()
        val drained = quiesceApplicationScopeWriters(scope = scope, timeoutMillis = 200L)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

        assertFalse("挂死 collector 必须如实报未排空", drained)
        assertTrue(
            "quiesce 必须在超时上限附近返回（实测 ${elapsedMillis}ms），恢复不能被拖住",
            elapsedMillis < 5_000L,
        )
        scope.cancel()
    }

    @Test
    fun `a quiesced scope accepts no new writers`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        assertTrue(quiesceApplicationScopeWriters(scope = scope, timeoutMillis = 1_000L))

        val probe = CompletableDeferred<Unit>()
        scope.launch { probe.complete(Unit) }
        // 给调度器时间：如果 launch 仍会执行，这里就会完成。
        delay(200L)

        assertFalse(
            "quiesce 后的 scope 上新 launch 必须是 no-op（排空后不再有新写）",
            probe.isCompleted,
        )
    }

    @Test
    fun `a scope without writers reports drained immediately`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val startedAt = System.nanoTime()
        val drained = quiesceApplicationScopeWriters(scope = scope, timeoutMillis = 2_000L)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

        assertTrue(drained)
        assertTrue("没有 collector 时不付出等待（实测 ${elapsedMillis}ms）", elapsedMillis < 1_000L)
    }
}
