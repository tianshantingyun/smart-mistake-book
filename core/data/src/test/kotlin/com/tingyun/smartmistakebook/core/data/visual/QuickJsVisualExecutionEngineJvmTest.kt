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
import org.junit.Test

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
