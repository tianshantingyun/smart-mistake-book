package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [VisualExecutionEngine] 端口契约用例（批次 20 T4 保守分支）。
 *
 * 任务书用例清单（计划 §4 T4 第 4 条）逐条映射：
 * - 正常渲染 → `a registered program renders exactly one prepared and one rendered scene`
 * - 死循环硬终止 → `an infinite loop is cooperatively interrupted`（协作层）+
 *   `a watchdog hard kill fuses the program`（硬层）
 * - 超限内存拒 → `memory over the heap budget is rejected`
 * - 无 IO/网络（尝试即拒）→ `globals outside the SDK whitelist do not exist`
 * - 重复初始化/关闭幂等 → `closing is idempotent`
 * 另加端口纪律用例：跨事务全局隔离、单在飞 BUSY、宿主取消让位、迟到定时器不杀下一事务、
 * 预算常量与契约 §4 表逐项一致、度量批次预算在类型上强制。
 *
 * 桌面研究夹具（`.jez/artifacts/flexible-js-drawing-2026-10-17/experiment.mjs`，3 程序/33 场景）
 * 是**真实 JS 源码**，只能在真引擎上执行——fake 的脚本注册表无法承载它，本批如实不复用；
 * 转端口用例归真实实现落地时（4E.2 前的设备/仪器化轨）。
 *
 * 全部用例按"先红后绿"写就：在 fake 存在之前，它们因端口类型缺失而不可编译（红）；
 * fake 落地后变绿。真实 QuickJS 实现落地时**复用本文件作为同一契约的验收**。
 */
class VisualExecutionEngineContractTest {

    private val engines = CopyOnWriteArrayList<FakeVisualExecutionEngine>()

    private fun engine(
        scripts: Map<String, FakeVisualExecutionEngine.FakeVisualProgram>,
        measurer: VisualContentMeasurer,
        cooperativeTimeoutMillis: Long = VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS,
        hardDeadlineMillis: Long = VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS,
    ): FakeVisualExecutionEngine = FakeVisualExecutionEngine(
        scripts,
        measurer,
        cooperativeTimeoutMillis,
        hardDeadlineMillis,
    ).also { engines.add(it) }

    @After
    fun closeEngines() {
        engines.forEach { it.close() }
        engines.clear()
    }

    private val viewport = VisualViewport(widthDu = 360, heightDu = 240)

    private fun request(
        sourceJs: String,
        stateJson: String? = null,
        explicitUserRetry: Boolean = false,
    ) = VisualExecutionRequest(
        programId = "program-1",
        sourceJs = sourceJs,
        sceneRevision = 1,
        stateJson = stateJson,
        viewport = viewport,
        timeMs = 1_000L,
        seed = 42L,
        theme = "light",
        explicitUserRetry = explicitUserRetry,
    )

    /** 度量 stub：按请求条目回 contentId；Thread.sleep 保证被 watchdog 中断时立即醒。 */
    private class StubMeasurer(
        private val delayMillis: Long = 0,
    ) : VisualContentMeasurer {
        var calls = 0
            private set
        val seenProgramIds = CopyOnWriteArrayList<String>()

        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
            calls += 1
            seenProgramIds.add(batch.programId)
            if (delayMillis > 0) {
                Thread.sleep(delayMillis)
            }
            return VisualContentMeasureResult(
                batch.entries.mapIndexed { index, entry ->
                    VisualMeasuredContent(
                        contentId = "content-$index",
                        widthDu = 10f,
                        heightDu = 12f,
                        baselineDu = 9f,
                        fontSizeSp = 16f,
                        digest = entry.payload,
                    )
                },
            )
        }
    }

    /** 正常程序：prepare 恰一次（并消费 SDK 白名单全局），render 恰一次并引用本事务 contentId。 */
    private class EchoProgram : FakeVisualExecutionEngine.FakeVisualProgram {
        var prepareCalls = 0
            private set
        var renderCalls = 0
            private set
        var lastInput: FakeVisualExecutionEngine.FakeFrameInput? = null
            private set

        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> {
            prepareCalls += 1
            lastInput = input
            // SDK 白名单帮助函数在本事务可解析（契约 §3.2：SDK 提供纯函数图形帮助方法）。
            sandbox.global("seedRandom")
            sandbox.global("line")
            return listOf(VisualContentMeasureEntry(VisualContentKind.TEXT, "变力做功"))
        }

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput {
            renderCalls += 1
            sandbox.checkInterrupt()
            return FakeVisualExecutionEngine.FakeRenderOutput(
                sceneJson = """{"schemaVersion":1,"sceneId":"scene-1","revision":1}""",
                referencedContentIds = metrics.map { it.contentId }.toSet(),
            )
        }
    }

    private class LoopForeverProgram : FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> {
            while (true) {
                sandbox.checkInterrupt()
            }
        }

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput = throw IllegalStateException("unreachable")
    }

    /** 只响应硬终止信号（线程中断），忽略协作标志——模拟不观察 interrupt handler 的循环。 */
    private class SleepThroughProgram(private val millis: Long) :
        FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> {
            sandbox.sleepInterruptibly(millis)
            return emptyList()
        }

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput {
            sandbox.sleepInterruptibly(millis)
            return FakeVisualExecutionEngine.FakeRenderOutput(sceneJson = "{}")
        }
    }

    private class GlobalReaderProgram(private val name: String) :
        FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> {
            sandbox.global(name)
            return emptyList()
        }

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput = FakeVisualExecutionEngine.FakeRenderOutput("{}")
    }

    private class HeapEaterProgram : FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> {
            sandbox.allocate(15L * 1024 * 1024)
            return emptyList()
        }

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput {
            // 越过 16 MiB 堆预算的那一次分配（契约 §4：QuickJS 堆 16 MiB）。
            sandbox.allocate(2L * 1024 * 1024)
            return FakeVisualExecutionEngine.FakeRenderOutput("{}")
        }
    }

    /** 第一事务声明一个脚本全局；若跨事务可见，第二程序的读取会成功。 */
    private class GlobalWriterProgram : FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> {
            sandbox.declare("injected", "leaked")
            return emptyList()
        }

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput = FakeVisualExecutionEngine.FakeRenderOutput("{}")
    }

    private class DanglingReferenceProgram : FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> = listOf(
            VisualContentMeasureEntry(VisualContentKind.TEXT, "标签"),
        )

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput = FakeVisualExecutionEngine.FakeRenderOutput(
            sceneJson = "{}",
            referencedContentIds = setOf("content-0", "ghost-1"),
        )
    }

    private class FailingProgram(private val failure: RuntimeException) :
        FakeVisualExecutionEngine.FakeVisualProgram {
        override fun prepare(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): List<VisualContentMeasureEntry> = throw failure

        override fun render(
            input: FakeVisualExecutionEngine.FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeVisualExecutionEngine.FakeSandbox,
        ): FakeVisualExecutionEngine.FakeRenderOutput = throw IllegalStateException("unreachable")
    }

    @Test
    fun `a registered program renders exactly one prepared and one rendered scene`() = runBlocking {
        val program = EchoProgram()
        val measurer = StubMeasurer()
        val engine = engine(mapOf(ECHO_SOURCE to program), measurer)

        val result = engine.execute(request(ECHO_SOURCE))

        val rendered = result as VisualExecutionResult.Rendered
        assertEquals("""{"schemaVersion":1,"sceneId":"scene-1","revision":1}""", rendered.sceneJson)
        assertEquals(1, rendered.sceneSchemaVersion)
        assertEquals(setOf("content-0"), rendered.contentIds)
        // 契约 §3.2：每事务最多一次 prepare、一次 render，且度量恰一批。
        assertEquals(1, program.prepareCalls)
        assertEquals(1, program.renderCalls)
        assertEquals(1, measurer.calls)
        // FrameInput 携带宿主冻结的帧值（契约 §3.2）。
        val input = requireNotNull(program.lastInput)
        assertEquals("program-1", input.programId)
        assertEquals(1, input.sceneRevision)
        assertEquals(360, input.viewport.widthDu)
        assertEquals(240, input.viewport.heightDu)
        assertEquals(1_000L, input.timeMs)
        assertEquals(42L, input.seed)
        assertEquals("light", input.theme)
    }

    @Test
    fun `a second transaction cannot observe globals declared by the first`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(
            mapOf(
                WRITER_SOURCE to GlobalWriterProgram(),
                READER_SOURCE to GlobalReaderProgram("injected"),
            ),
            measurer,
        )

        val first = engine.execute(request(WRITER_SOURCE))
        val second = engine.execute(request(READER_SOURCE))

        assertTrue(first is VisualExecutionResult.Rendered)
        val rejected = second as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.SANDBOX_VIOLATION, rejected.reason)
        assertTrue(rejected.diagnostics.contains("injected"))
    }

    @Test
    fun `globals outside the SDK whitelist do not exist`() = runBlocking {
        val measurer = StubMeasurer()
        // 探针第 2 项同名单：fetch/document/window/process/require 均不存在；尝试即拒。
        val scripts = listOf("fetch", "document", "window", "process", "require")
            .associate { name ->
                "$SOURCE_PREFIX sandbox-probe-$name" to GlobalReaderProgram(name)
            }
        val engine = engine(scripts, measurer)

        for (name in listOf("fetch", "document", "window", "process", "require")) {
            val result = engine.execute(request("$SOURCE_PREFIX sandbox-probe-$name"))
            val rejected = result as VisualExecutionResult.Rejected
            assertEquals("global $name must be absent", VisualExecutionRejection.SANDBOX_VIOLATION, rejected.reason)
            assertTrue(rejected.diagnostics.contains(name))
            assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        }
    }

    @Test
    fun `an unregistered source is rejected as an invalid program`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(mapOf(ECHO_SOURCE to EchoProgram()), measurer)

        val result = engine.execute(request("$SOURCE_PREFIX not-registered"))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, rejected.reason)
        assertEquals(0, measurer.calls)
    }

    @Test
    fun `a program failure is reported as a bounded diagnostic, not an exception`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(
            mapOf(FAILING_SOURCE to FailingProgram(IllegalStateException("boom in prepare"))),
            measurer,
        )

        val result = engine.execute(request(FAILING_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, rejected.reason)
        assertTrue(rejected.diagnostics.contains("boom in prepare"))
        assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
    }

    @Test
    fun `oversized source and state are rejected before any execution`() = runBlocking {
        val program = EchoProgram()
        val measurer = StubMeasurer()
        val engine = engine(mapOf(ECHO_SOURCE to program), measurer)

        val oversizedSource = "a".repeat(64 * 1024 + 1)
        val sourceResult = engine.execute(request(oversizedSource))
        val oversizedState = "s".repeat(16 * 1024 + 1)
        val stateResult = engine.execute(request(ECHO_SOURCE, stateJson = oversizedState))

        assertEquals(VisualExecutionRejection.INPUT_OVER_BUDGET, (sourceResult as VisualExecutionResult.Rejected).reason)
        assertEquals(VisualExecutionRejection.INPUT_OVER_BUDGET, (stateResult as VisualExecutionResult.Rejected).reason)
        // 契约 §4：超限不截断成"正确图"，也不启动任何执行。
        assertEquals(0, program.prepareCalls)
        assertEquals(0, program.renderCalls)
        assertEquals(0, measurer.calls)
    }

    @Test
    fun `memory over the heap budget is rejected without truncation`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(mapOf(HEAP_SOURCE to HeapEaterProgram()), measurer)

        val result = engine.execute(request(HEAP_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.MEMORY_LIMIT, rejected.reason)
        assertTrue(rejected.diagnostics.contains("${VisualExecutionBudget.MAX_HEAP_BYTES}"))
    }

    @Test
    fun `an infinite loop is cooperatively interrupted and reported as a phase timeout`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(
            mapOf(LOOP_SOURCE to LoopForeverProgram()),
            measurer,
            cooperativeTimeoutMillis = 40L,
            hardDeadlineMillis = 10_000L,
        )

        val result = engine.execute(request(LOOP_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, rejected.reason)
        // 协作层不是硬终止：不得触发熔断（契约 §4 熔断只对硬期限层）。
        assertEquals(0, engine.fusedDigestCountForTest)
    }

    @Test
    fun `a watchdog hard kill fuses the program until an explicit user retry`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(
            mapOf(SLEEP_SOURCE to SleepThroughProgram(4_000L)),
            measurer,
            cooperativeTimeoutMillis = 4_000L,
            hardDeadlineMillis = 80L,
        )

        val killed = engine.execute(request(SLEEP_SOURCE))
        assertEquals(VisualExecutionRejection.HARD_DEADLINE_EXCEEDED, (killed as VisualExecutionResult.Rejected).reason)
        assertEquals(1, engine.fusedDigestCountForTest)

        // 同一源码再跑 = 熔断生效（不再执行）。
        val fused = engine.execute(request(SLEEP_SOURCE))
        assertEquals(VisualExecutionRejection.PROGRAM_FUSED, (fused as VisualExecutionResult.Rejected).reason)

        // 用户明确本地重试：解熔断、真实重跑（结果仍是超时，再次熔断仍是同一 digest）。
        val retried = engine.execute(request(SLEEP_SOURCE, explicitUserRetry = true))
        assertEquals(VisualExecutionRejection.HARD_DEADLINE_EXCEEDED, (retried as VisualExecutionResult.Rejected).reason)
        assertEquals(1, engine.fusedDigestCountForTest)
    }

    @Test
    fun `a stale timer from an earlier transaction never kills the next one`() = runBlocking {
        val measurer = StubMeasurer(delayMillis = 250L)
        val engine = engine(
            mapOf(
                FAILING_SOURCE to FailingProgram(IllegalStateException("boom")),
                ECHO_SOURCE to EchoProgram(),
            ),
            measurer,
            // 第一事务 ~1ms 即结束，其硬定时器在 ~302ms 到期（300ms 定时 + tx1 起点）——此时
            // 第二事务（~102..352ms 窗口）正在慢度量中途。若 watchdog 不核对"本事务仍是
            // 当前事务"（契约 §4：迟到定时器不得杀下一任务），它会中断 worker 并把第二
            // 事务打成 HARD_DEADLINE_EXCEEDED。
            cooperativeTimeoutMillis = 10_000L,
            hardDeadlineMillis = 300L,
        )
        val first = engine.execute(request(FAILING_SOURCE))
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, (first as VisualExecutionResult.Rejected).reason)

        delay(100)
        val second = engine.execute(request(ECHO_SOURCE))

        assertTrue(second is VisualExecutionResult.Rendered)
    }

    @Test
    fun `render cannot reference content not frozen in this transaction`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(mapOf(DANGLING_SOURCE to DanglingReferenceProgram()), measurer)

        val result = engine.execute(request(DANGLING_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.INVALID_SCENE, rejected.reason)
        assertTrue(rejected.diagnostics.contains("ghost-1"))
    }

    @Test
    fun `a concurrent execution is rejected as busy with a bounded diagnostic`() = runBlocking {
        val measurer = StubMeasurer(delayMillis = 300L)
        // 慢度量 300ms 必须短于协作期限（期限用于在飞作废判定，不是本用例的对象）。
        val engine = engine(
            mapOf(SLOW_SOURCE to EchoProgram()),
            measurer,
            cooperativeTimeoutMillis = 1_000L,
        )

        val first = async { engine.execute(request(SLOW_SOURCE)) }
        delay(50)
        val second = engine.execute(request(SLOW_SOURCE))

        val rejected = second as VisualExecutionResult.Rejected
        // 契约 §4：全局一个在飞绘制；满队列回 BUSY，不排队第二事务。
        assertEquals(VisualExecutionRejection.BUSY, rejected.reason)
        assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        assertTrue(first.await() is VisualExecutionResult.Rendered)
    }

    @Test
    fun `the host abandoning an in-flight transaction frees the single-flight slot`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(
            mapOf(
                SLEEP_SOURCE to SleepThroughProgram(4_000L),
                ECHO_SOURCE to EchoProgram(),
            ),
            measurer,
            cooperativeTimeoutMillis = 4_000L,
            hardDeadlineMillis = 4_000L,
        )
        val job = launch { engine.execute(request(SLEEP_SOURCE)) }
        delay(50)
        job.cancelAndJoin()

        val second = engine.execute(request(ECHO_SOURCE))

        assertTrue(second is VisualExecutionResult.Rendered)
    }

    @Test
    fun `closing is idempotent and execute after close is rejected`() = runBlocking {
        val measurer = StubMeasurer()
        val engine = engine(mapOf(ECHO_SOURCE to EchoProgram()), measurer)

        engine.close()
        engine.close()
        assertTrue(engine.isClosedForTest)

        val result = engine.execute(request(ECHO_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.ENGINE_CLOSED, rejected.reason)
        // 关闭后不再消耗执行资源。
        assertEquals(0, measurer.calls)
    }

    @Test
    fun `the V1 budget constants mirror the contract table`() {
        // 契约 §4 表（visual-generation-contract.md:74-82）：改这里必须回写契约。
        assertEquals(64 * 1024, VisualExecutionBudget.MAX_SOURCE_JS_BYTES)
        assertEquals(16 * 1024, VisualExecutionBudget.MAX_STATE_JSON_BYTES)
        assertEquals(256 * 1024, VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES)
        assertEquals(16L * 1024 * 1024, VisualExecutionBudget.MAX_HEAP_BYTES)
        assertEquals(256L * 1024, VisualExecutionBudget.MAX_STACK_BYTES)
        assertEquals(100L, VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS)
        assertEquals(1500L, VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS)
        assertEquals(2500L, VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS)
        assertEquals(128, VisualExecutionBudget.MAX_CONTENT_REQUESTS)
        assertEquals(6000, VisualExecutionBudget.MAX_CONTENT_TOTAL_CHARS)
        assertEquals(4096, VisualExecutionBudget.MAX_CANVAS_EDGE_DU)
    }

    @Test
    fun `measure batches over the request or character budget are rejected at the type`() {
        val tooMany = (0 until VisualExecutionBudget.MAX_CONTENT_REQUESTS + 1).map {
            VisualContentMeasureEntry(VisualContentKind.TEXT, "x")
        }
        val overRequests = runCatching {
            VisualContentMeasureBatch("program-1", 1, tooMany)
        }
        assertTrue(overRequests.isFailure)

        val overChars = List(VisualExecutionBudget.MAX_CONTENT_REQUESTS) {
            VisualContentMeasureEntry(VisualContentKind.TEXT, "x".repeat(VisualExecutionBudget.MAX_CONTENT_TOTAL_CHARS / VisualExecutionBudget.MAX_CONTENT_REQUESTS + 1))
        }
        val overTotal = runCatching {
            VisualContentMeasureBatch("program-1", 1, overChars)
        }
        assertTrue(overTotal.isFailure)

        // 白名单全局是封闭集合：SDK 之外没有第四类"可用全局"。
        assertEquals(
            setOf("line", "arrow", "circle", "arc", "axes", "polyline", "seedRandom"),
            FakeVisualExecutionEngine.FakeSandbox.SDK_GLOBALS.keys,
        )
    }

    @Test
    fun `a viewport outside the canvas budget is rejected at the type`() {
        assertTrue(runCatching { VisualViewport(0, 100) }.isFailure)
        assertTrue(runCatching { VisualViewport(100, VisualExecutionBudget.MAX_CANVAS_EDGE_DU + 1) }.isFailure)
        assertEquals(360, VisualViewport(360, 240).widthDu)
    }

    private companion object {
        const val SOURCE_PREFIX = "const visual ="
        const val ECHO_SOURCE = "$SOURCE_PREFIX echo"
        const val WRITER_SOURCE = "$SOURCE_PREFIX writer"
        const val READER_SOURCE = "$SOURCE_PREFIX reader"
        const val FAILING_SOURCE = "$SOURCE_PREFIX failing"
        const val LOOP_SOURCE = "$SOURCE_PREFIX while(true) sandbox.checkInterrupt()"
        const val SLEEP_SOURCE = "$SOURCE_PREFIX sleep-through"
        const val HEAP_SOURCE = "$SOURCE_PREFIX heap-eater"
        const val DANGLING_SOURCE = "$SOURCE_PREFIX dangling-ref"
        const val SLOW_SOURCE = "$SOURCE_PREFIX slow-measure"
    }
}
