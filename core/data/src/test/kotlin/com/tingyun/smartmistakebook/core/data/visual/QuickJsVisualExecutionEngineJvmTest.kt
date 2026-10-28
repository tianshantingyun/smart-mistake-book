package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [QuickJsVisualExecutionEngine] 的 JVM 单测——**钉住"本地单测不执行任何 QuickJs 路径"不变量**。
 *
 * 依据：quickjs-kt-android 的原生库装载发生在 `QuickJs.create` 的 companion init
 * （`NativeLibraryLoader.loadLibrary("quickjs")` → `System.loadLibrary`），桌面 JVM 没有
 * `libquickjs.so`（R1 §5.2）。本类覆盖的每条路径都必须在触碰引擎实例化**之前**返回：
 * 关闭态、字节预算。若有人把执行重排到引擎实例化之后，本类在 JVM 上会以
 * UnsatisfiedLinkError 变红——这是"QuickJS 依赖不进本地单测执行路径"的即时回归信号。
 *
 * 完整执行语义（渲染/协作中断/内存/沙箱/熔断/issue 240）没有原生库无法验证，
 * 归仪器化门：`QuickJsVisualExecutionEngineInstrumentedTest`（设备轨执行）。
 */
class QuickJsVisualExecutionEngineJvmTest {

    private class ForbiddenMeasurer : VisualContentMeasurer {
        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult =
            throw AssertionError("measurer must not be reached on closed/over-budget paths")
    }

    private fun engine() = QuickJsVisualExecutionEngine(ForbiddenMeasurer())

    private fun request(
        sourceJs: String = "const visual = {};",
        stateJson: String? = null,
    ) = VisualExecutionRequest(
        programId = "program-1",
        sourceJs = sourceJs,
        sceneRevision = 1,
        stateJson = stateJson,
        viewport = VisualViewport(360, 240),
        timeMs = 1_000L,
        seed = 42L,
        theme = "light",
    )

    @Test
    fun `execute after close is rejected without touching the engine library`() = runBlocking {
        val engine = engine()
        engine.close()
        engine.close()

        val result = engine.execute(request())

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.ENGINE_CLOSED, rejected.reason)
    }

    /**
     * KD-71 的**离设备确定性红测**（批次 30 T7）：close 精确落在"关闭态预检之后、事务登记
     * 之前"的旧窗口里。
     *
     * 判据：
     * - **修复前（红）**：预检返回 false 后 close 完成（`closed=true`、无在飞事务可强弃），
     *   实现继续非原子登记并执行——JVM 上没有原生库，最可能以 `INVALID_PROGRAM`（装载失败
     *   被折成有界拒绝）或异常穿透收场；设备上则会真的渲染成功。两种形态都不是
     *   `ENGINE_CLOSED`，第一条断言（或其结果类型转换）即红。
     * - **修复后（绿）**：登记与 closed 判定在同一临界区，close 先到则登记入口必见
     *   `closed=true` → `ENGINE_CLOSED`；close 返回后再次 execute 仍为 `ENGINE_CLOSED`。
     *
     * 与既有设备用例互补：`closeDuringAnInFlightTransactionWaitsAndRejectsIt` 钉"已登记后
     * 的强弃+有界等待"，32 轮压测是概率性的；本用例不需要设备、不需要 QuickJS 原生库，
     * 且把窗口**确定性**撑开。
     */
    @Test
    fun `close landing between the closed precheck and registration still rejects the execution`() {
        val engine = engine()
        val enteredWindow = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        engine.beforeRegistrationHookForTest = {
            enteredWindow.countDown()
            assertTrue(
                "close() must return while execute is held in the registration window",
                closeReturned.await(5, TimeUnit.SECONDS),
            )
        }
        val closer = thread(isDaemon = true, name = "kd71-registration-window-close") {
            assertTrue(
                "execute must reach the registration window",
                enteredWindow.await(5, TimeUnit.SECONDS),
            )
            engine.close()
            closeReturned.countDown()
        }

        val result = runBlocking { engine.execute(request()) }
        closer.join(5_000)

        assertFalse("close() must not hang on an unregistered execute", closer.isAlive)
        assertEquals(
            "a close that landed before registration must reject, not execute",
            VisualExecutionRejection.ENGINE_CLOSED,
            (result as VisualExecutionResult.Rejected).reason,
        )
        assertEquals(
            "the engine must stay terminal for later executions",
            VisualExecutionRejection.ENGINE_CLOSED,
            (runBlocking { engine.execute(request()) } as VisualExecutionResult.Rejected).reason,
        )
    }

    @Test
    fun `oversized source and state are rejected before any engine allocation`() = runBlocking {
        val engine = engine()

        val oversizedSource = engine.execute(request(sourceJs = "a".repeat(64 * 1024 + 1)))
        val oversizedState = engine.execute(request(stateJson = "s".repeat(16 * 1024 + 1)))

        assertEquals(
            VisualExecutionRejection.INPUT_OVER_BUDGET,
            (oversizedSource as VisualExecutionResult.Rejected).reason,
        )
        assertEquals(
            VisualExecutionRejection.INPUT_OVER_BUDGET,
            (oversizedState as VisualExecutionResult.Rejected).reason,
        )
    }
}
