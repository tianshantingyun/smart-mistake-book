package com.tingyun.smartmistakebook.visualjs

import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 契约 §64 队列状态机的 JVM 单测（批次 25 T3；设计断言清单 R1' §4 A-5②/A-6/A-7/A-8/A-9/A-10）。
 *
 * 这组用例钉住的是**批次 24 现状结构性不具备**的行为：旧 `executeMutex` 排队无上界、无
 * 容量门、无 latest-wins、无 BUSY 背压信号——对着旧实现这些断言全部不成立（先红）；
 * 落地 [VisualJsFrameQueue] 后必须全绿。可控会话夹具用 gate 挡住在飞会话，保证
 * "1 在飞 + 2 待绘"状态可确定性构造，不靠时序。
 *
 * 纪律：不放宽断言；BUSY/superseded 的诊断前缀也一并断言，防止语义漂移成普通拒绝。
 */
class VisualJsFrameQueueTest {

    private val harnesses = CopyOnWriteArrayList<QueueHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.shutdown() }
    }

    private fun harness(): QueueHarness = QueueHarness().also { harnesses.add(it) }

    @Test
    fun `two concurrent requests queue behind one in flight and the third distinct program is rejected busy`() =
        runBlocking {
            val h = harness()
            val a = h.submit("A")
            h.awaitStarted("A")

            // 并发两请求：都进入待绘队列，不执行（1 在飞不变量，A-5②）
            val b = h.submit("B")
            val c = h.submit("C")
            assertEquals("only the in-flight frame may execute while the gate is closed", listOf("A#A"), h.executions)
            assertEquals(2, h.queue.pendingCountForTest())
            assertTrue(h.queue.inFlightForTest())

            // 第三个不同 program：满队列 → BUSY，在飞与两个待绘一概不动（A-6/A-9）
            val d = h.submit("D")
            val busy = h.awaitResult(d)
            assertRejected(busy, VisualExecutionRejection.BUSY, "full")
            assertEquals("a BUSY rejection must not disturb the queue", listOf("A#A"), h.executions)
            assertEquals(2, h.queue.pendingCountForTest())
            // A-9：连续 BUSY 重试不放大队列
            assertRejected(h.awaitResult(h.submit("D")), VisualExecutionRejection.BUSY, "full")
            assertEquals(2, h.queue.pendingCountForTest())

            h.release()
            assertRendered(h.awaitResult(a))
            assertRendered(h.awaitResult(b))
            assertRendered(h.awaitResult(c))
            // 跨 program 按序：A（在飞）→ B → C（FIFO），无交错（A-8）
            assertEquals(listOf("A#A", "B#B", "C#C"), h.executions)
            assertEquals(0, h.queue.pendingCountForTest())

            // 排空后队列重新武装：新帧直接起会话（inFlight 复位的行为证明）
            val e = h.submit("E")
            h.awaitStarted("E")
            h.release()
            assertRendered(h.awaitResult(e))
            assertEquals(listOf("A#A", "B#B", "C#C", "E#E"), h.executions)
        }

    @Test
    fun `latest wins replaces the older pending state of the same program without dropping other programs`() =
        runBlocking {
            val h = harness()
            val a = h.submit("A")
            h.awaitStarted("A")

            // 待绘队列排满：[P-rev2, Q1]
            val pOld = h.submit("P", digest = "P-rev2")
            val q = h.submit("Q", digest = "Q1")
            // 同 program 新 state：latest-wins 原地顶掉 P-rev2（A-7）
            val pNew = h.submit("P", digest = "P-rev3")

            val superseded = h.awaitResult(pOld)
            assertRejected(superseded, VisualExecutionRejection.BUSY, "superseded")
            // 队列长度不因同 program 增长（A-7/A-9）；不同 program 的 Q 未被顶掉（A-10）
            assertEquals(2, h.queue.pendingCountForTest())
            assertRejected(h.awaitResult(h.submit("R")), VisualExecutionRejection.BUSY, "full")
            assertEquals(2, h.queue.pendingCountForTest())

            h.release()
            assertRendered(h.awaitResult(a))
            assertRendered(h.awaitResult(pNew))
            assertRendered(h.awaitResult(q))
            // 旧 state 从未执行；FIFO 位置由原地替换保住：A → P(rev3) → Q
            assertEquals(listOf("A#A", "P#P-rev3", "Q#Q1"), h.executions)
        }

    @Test
    fun `a caller cancelled while queued is dequeued and never executed`() = runBlocking {
        val h = harness()
        val a = h.submit("A")
        h.awaitStarted("A")

        val b = h.submit("B")
        b.cancel(CancellationException("the caller went away"))
        // 取消即出队：空出的槽可被新帧占用
        val c = h.submit("C")
        assertEquals(1, h.queue.pendingCountForTest())
        val d = h.submit("D")
        assertEquals(2, h.queue.pendingCountForTest())
        assertRejected(h.awaitResult(h.submit("E")), VisualExecutionRejection.BUSY, "full")

        h.release()
        assertRendered(h.awaitResult(a))
        assertRendered(h.awaitResult(c))
        assertRendered(h.awaitResult(d))
        // 取消的帧不执行；取消的等待者保持取消态
        assertEquals(listOf("A#A", "C#C", "D#D"), h.executions)
        assertTrue("a cancelled waiter must stay cancelled", b.isCancelled)
    }

    @Test
    fun `close latches the queue and completes queued waiters with engine closed`() = runBlocking {
        val h = harness()
        val a = h.submit("A")
        h.awaitStarted("A")
        val b = h.submit("B")

        h.queue.close("the visual_js client is closed")
        val closed = h.awaitResult(b)
        assertRejected(closed, VisualExecutionRejection.ENGINE_CLOSED, "closed")
        assertRejected(h.awaitResult(h.submit("C")), VisualExecutionRejection.ENGINE_CLOSED, "closed")
        assertEquals(0, h.queue.pendingCountForTest())

        // 在飞帧不受队列 close 影响：由会话自身收尾（引擎层的 link 拆卸不在此夹具内）
        h.release()
        assertRendered(h.awaitResult(a))
    }

    @Test
    fun `death drain completes queued waiters but the queue still accepts frames for the rebuilt worker`() =
        runBlocking {
            val h = harness()
            val a = h.submit("A")
            h.awaitStarted("A")
            val b = h.submit("B")
            val c = h.submit("C")

            // R1' §3.2 保守分支：死亡清队 ENGINE_CLOSED，但**不闩死**——重建后新帧照常入队
            h.queue.dropPending(VisualExecutionRejection.ENGINE_CLOSED, "visual_js worker died")
            assertRejected(h.awaitResult(b), VisualExecutionRejection.ENGINE_CLOSED, "died")
            assertRejected(h.awaitResult(c), VisualExecutionRejection.ENGINE_CLOSED, "died")
            assertEquals(0, h.queue.pendingCountForTest())

            val d = h.submit("D")
            assertEquals(1, h.queue.pendingCountForTest())
            h.release()
            assertRendered(h.awaitResult(a))
            assertRendered(h.awaitResult(d))
            assertEquals(listOf("A#A", "D#D"), h.executions)
        }

    // ---------------------------------------------------------------- 断言夹具

    private fun assertRendered(result: VisualExecutionResult) {
        assertTrue("expected Rendered, got $result", result is VisualExecutionResult.Rendered)
    }

    private fun assertRejected(
        result: VisualExecutionResult,
        reason: VisualExecutionRejection,
        diagnosticsFragment: String,
    ) {
        val rejected = result as? VisualExecutionResult.Rejected
        assertTrue("expected Rejected, got $result", rejected != null)
        assertEquals(reason, rejected!!.reason)
        assertTrue(
            "diagnostics must keep the '$diagnosticsFragment' semantics: ${rejected.diagnostics}",
            rejected.diagnostics.contains(diagnosticsFragment),
        )
    }

    /**
     * 可控会话夹具：每个会话在记录执行后阻塞在共享 gate 上，等待者由 gate 放行后完成。
     * 执行序按会话开始时刻记录——队列链式调度保证严格串行，顺序断言不依赖时序。
     */
    private class QueueHarness {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val release = CompletableDeferred<Unit>()
        private val startedGates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        val executions = CopyOnWriteArrayList<String>()
        val queue = VisualJsFrameQueue(scope) { frame ->
            // 执行序记录为 programId#digest：latest-wins 用例必须区分同 program 的不同
            // state（digest），因此所有 executions 断言一律用该格式，不得退回裸 programId
            //（裸格式与夹具不符，用例会假红——本文件首批实跑前的形状）。
            executions.add("${frame.programId}#${frame.digest}")
            startedGates[frame.digest]?.complete(Unit)
            release.await()
            frame.waiter.complete(
                VisualExecutionResult.Rendered("scene-${frame.programId}", 1, emptySet()),
            )
        }

        fun submit(programId: String, digest: String = programId): CompletableDeferred<VisualExecutionResult> {
            startedGates[digest] = CompletableDeferred()
            val waiter = CompletableDeferred<VisualExecutionResult>()
            queue.submit(
                VisualJsFrameQueue.Frame(
                    programId = programId,
                    digest = digest,
                    envelope = ByteArray(0),
                    explicitUserRetry = false,
                    waiter = waiter,
                ),
            )
            return waiter
        }

        suspend fun awaitStarted(digest: String) {
            withTimeout(STARTED_TIMEOUT_MILLIS) {
                checkNotNull(startedGates[digest]) { "no gate registered for $digest" }.await()
            }
        }

        suspend fun awaitResult(
            waiter: CompletableDeferred<VisualExecutionResult>,
        ): VisualExecutionResult = withTimeout(COMPLETION_TIMEOUT_MILLIS) { waiter.await() }

        fun release() {
            release.complete(Unit)
        }

        fun shutdown() {
            scope.cancel()
        }

        private companion object {
            const val STARTED_TIMEOUT_MILLIS: Long = 5_000L
            const val COMPLETION_TIMEOUT_MILLIS: Long = 5_000L
        }
    }
}
