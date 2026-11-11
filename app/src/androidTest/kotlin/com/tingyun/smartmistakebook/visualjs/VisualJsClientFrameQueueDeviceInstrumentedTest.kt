package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 * **确定性窗口的构造**（不靠会话内时序）：先 `abandonForTest()` 真实终止当前 worker
 * （terminate → killSelf → 死亡事实确认 ≤2 s，与既有
 * `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 同路径），随后首帧经
 * `engine.execute` 触发"确认死亡后的恰好一次重建"——重建 = 新隔离进程 fork + warm-up
 * （设备实测冷态 warm-up ~1.9 s，KD-76 ③ E-03）+ attach（E-01b 0.6–0.9 s）。在这
 * ≥2 s 的窗口里，消费者协程停在 `ensureConnected()`，`inFlight` 位由首帧入队时同步置真
 * （`VisualJsFrameQueue.submit` 直跑分支），后续帧全部落进待绘队列。四个 submit 各隔
 * 300–400 ms 发出（间隔 ≫ 单次 submit 的毫秒级路径，逐个排除 Dispatchers.IO 多线程
 * 的入队顺序竞态），远短于重建窗口——若任何一个 submit 晚到（首帧已完成、队列已排空），
 * 下方断言会以明确的诊断红（BUSY 不出现/顺序翻转），不会假绿。
 *
 * **已知红定性（批次 40/41/42/43 同方法同报文；批次 43 WS7）**：失败是 D 被判
 * `Rendered` 而非 `BUSY`（`build/ws4-batch43/suites/suite-p09.log:13-16`）。四个帧
 * 全部真实渲染，说明 **D 提交时队列没有饱和**（重建窗口在 1.3 s 内结束，in-flight
 * 已排空）——不是队列容量语义失效：容量 2 由 `VisualJsFrameQueue.kt:102-107,205-207`
 * 源码钉住，JVM 队列用例在树（本会话未复跑）。定性边界：本用例不得计入产品红，也不得
 * 删断言/放宽断言来变绿。确定性修法（二选一，都需设备复跑）：① 在飞帧用可控的慢渲染
 * 撑住窗口；② 给引擎加只读队列状态 seam，确认饱和后再提交 D。
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
            val engine = VisualJsClientEngine(context).also { engines.add(it) }
            assertTrue("initial connect failed", engine.connectForTest())
            assertEquals(1, engine.connectionCountForTest())

            // 真实终止当前 worker（死亡确认 ≤2 s），打开"恰好一次重建"的确定性排队窗口。
            assertTrue(
                "the abandon did not confirm the worker death within its budget (environment " +
                    "or death-path regression); the queue window below cannot be constructed",
                engine.abandonForTest(),
            )

            val frames = FrameRecorder(engine, newScope())
            // 首帧先发并独占 400 ms 起跑裕量：它的 submit（毫秒级）必然先于后续帧，
            // 且它以 in-flight 身份停在重建窗口里。
            frames.fire("A", request("program-queue-a", "scene-A"))
            delay(FIRST_FRAME_HEAD_START_MILLIS)
            // B 的旧 state 先入队，再让同 program 的新 state 原地顶掉它（latest-wins）。
            frames.fire("B1", request("program-queue-b", "scene-B1"))
            delay(FRAME_SPACING_MILLIS)
            frames.fire("B2", request("program-queue-b", "scene-B2"))
            delay(FRAME_SPACING_MILLIS)
            // C 占第二个待绘槽；D 是第 4 个不同 program：满队列 → BUSY。
            frames.fire("C", request("program-queue-c", "scene-C"))
            delay(FRAME_SPACING_MILLIS)
            frames.fire("D", request("program-queue-d", "scene-D"))

            val busyFull = frames.await("D")
            val superseded = frames.await("B1")
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
            scope.launch {
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
         * 首帧起跑裕量与帧间距：远大于单次 submit 的毫秒级路径（协程两次派发 + SHA-256 +
         * 信封编码），远小于重建窗口（fork + attach + warm-up，设备实测 ≥2 s）。
         * 间隔只用于排除入队顺序竞态，不承担任何语义断言。
         */
        const val FIRST_FRAME_HEAD_START_MILLIS: Long = 400L
        const val FRAME_SPACING_MILLIS: Long = 300L

        /**
         * 单帧完成上界：最坏形状 = 在飞帧的重建走满 10 s 重建预算 + ready 10 s + 会话
         * 2.5 s——正常重建 ~3 s，该上界只兜回归/环境异常，不是语义断言。
         */
        const val FRAME_COMPLETION_TIMEOUT_MILLIS: Long = 30_000L
    }
}
