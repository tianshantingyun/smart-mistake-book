package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.KnowledgeNodeSuccessors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S20：后继映射缓存的命中 / 失效 / 并发单次加载 / 「一次 drain 内恒定」。
 *
 * 缓存消灭的失败：每次 drain 重读 `knowledge_node` 全表（数万节点），而映射只在内容调和
 * 合并退役时变化。失效依据（表级失效信号）的端到端接线见
 * `RoomBackedStudyExperienceRepositorySuccessorsInvalidationTest`。
 */
class KnowledgeNodeSuccessorsCacheTest {

    @Test
    fun `hits the cache across calls and reloads only after invalidation`() = runBlocking {
        var loads = 0
        val cache = KnowledgeNodeSuccessorsCache {
            loads++
            KnowledgeNodeSuccessors(mapOf("kc-old" to "kc-final"))
        }

        val first = cache.current()
        val second = cache.current()
        assertSame("命中缓存：两次 current() 是同一实例", first, second)
        assertEquals("命中缓存不重读", 1, loads)

        cache.invalidate()
        val reloaded = cache.current()
        assertNotSame("失效后必须重读（新实例）", first, reloaded)
        assertEquals("失效后只重读一次", 2, loads)
        assertEquals("重读后解析走新映射", "kc-final", reloaded.resolve("kc-old"))
    }

    /**
     * 真并发版本（复核修复 2026-10-05）：旧写法在 `runBlocking` 单线程事件循环里用
     * `async` + 不挂起的 loader，8 次 `current()` 实际顺序执行——删掉 `loadMutex`
     * 用例照样绿，锚点证明不了互斥。
     *
     * 现在：8 个调用者跑在 `Dispatchers.Default`（多线程），loader 是**挂起型**
     * （挂在 release 信号上），并等待"全部 8 个 loader 同时在飞"的信号——只有互斥
     * 缺失时第 2..8 个才会重入 loader；互斥在场时只有 1 个在飞、信号永不到达，
     * 由超时兜底放行。去掉 `loadMutex` 时本用例必红（loads > 1）。
     */
    @Test
    fun `concurrent first calls load the table once`() = runBlocking {
        val loads = AtomicInteger()
        val allLoadersInFlight = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        val cache = KnowledgeNodeSuccessorsCache {
            val inFlight = loads.incrementAndGet()
            if (inFlight >= CONCURRENT_CALLERS) allLoadersInFlight.complete(Unit)
            releaseLoad.await()
            KnowledgeNodeSuccessors(mapOf("kc-old" to "kc-final"))
        }

        val callers = (1..CONCURRENT_CALLERS).map {
            async(Dispatchers.Default) { cache.current() }
        }
        // 互斥在场 → 只有 1 个 loader 在飞，"8 个同时在飞"等不到，超时后放行；
        // 互斥缺失 → 8 个都重入 loader，信号立即到达（确定性，不靠 sleep 猜）。
        withTimeoutOrNull(LOAD_RENDEZVOUS_TIMEOUT_MS) { allLoadersInFlight.await() }
        releaseLoad.complete(Unit)
        val loaded = callers.awaitAll()

        assertEquals("并发未命中只加载一次", 1, loads.get())
        assertTrue("并发调用拿到同一实例", loaded.all { it === loaded.first() })
    }

    @Test
    fun `an instance already handed out keeps its mapping after invalidation`() = runBlocking {
        var loads = 0
        val cache = KnowledgeNodeSuccessorsCache {
            loads++
            KnowledgeNodeSuccessors(
                if (loads == 1) mapOf("kc-old" to "kc-final") else emptyMap(),
            )
        }

        val inFlight = cache.current()
        cache.invalidate()

        assertEquals(
            "失效不触碰已取出的实例：一次 drain 内映射恒定",
            "kc-final",
            inFlight.resolve("kc-old"),
        )
        assertEquals("下一次 current() 才看到新表", "kc-old", cache.current().resolve("kc-old"))
    }

    /**
     * 复核修复的回归：加载挂在数据库调度器上、失效在仓库作用域上，两者跨线程可交错
     * （应用启动把内容安装与首次排空并行）。失效若在加载期间送达，**不得**被随后写回的
     * 旧值吞掉——否则缓存会带着合并前映射、且本进程内再无失效来源。
     */
    @Test
    fun `an invalidation delivered during a load is not swallowed by the loaded value`() =
        runBlocking {
            val loadStarted = CompletableDeferred<Unit>()
            val releaseLoad = CompletableDeferred<Unit>()
            var loads = 0
            val cache = KnowledgeNodeSuccessorsCache {
                loads++
                if (loads == 1) {
                    loadStarted.complete(Unit)
                    releaseLoad.await()
                    // 失效前读到的表状态：这次加载必须被丢弃，不得安装。
                    KnowledgeNodeSuccessors(mapOf("kc-old" to "kc-stale"))
                } else {
                    KnowledgeNodeSuccessors(mapOf("kc-old" to "kc-final"))
                }
            }

            val inFlight = async { cache.current() }
            loadStarted.await()
            cache.invalidate()
            releaseLoad.complete(Unit)

            val resolved = inFlight.await()
            assertEquals(
                "加载期间的失效不得被旧值吞掉：本次调用返回重读结果",
                "kc-final",
                resolved.resolve("kc-old"),
            )
            assertEquals("必须重读（不是把失效前的值装进缓存）", 2, loads)
            assertEquals("缓存里也不能留旧值", "kc-final", cache.current().resolve("kc-old"))
        }

    private companion object {
        /** 真并发用例的并发调用者数（也是"全部在飞"信号的阈值）。 */
        const val CONCURRENT_CALLERS = 8

        /**
         * 等"全部 loader 同时在飞"的上限。互斥在场时该信号不会出现，必须靠超时兜底；
         * 取 1s 是测试时长与机器负载的折中（互斥缺失时信号在毫秒级到达，不依赖这个值）。
         */
        const val LOAD_RENDEZVOUS_TIMEOUT_MS = 1_000L
    }
}
