package com.tingyun.smartmistakebook.core.data.visual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [QuickJsVisualExecutionEngine] 仪器化用例（批次 21 T3，真引擎上的契约验收；设备轨执行）。
 *
 * 任务书用例清单（计划 T3 第 6 条）逐条映射：
 * - 正常渲染 → `a program renders...`（FrameInput 逐字段 + prepare/render 各恰一次的度量面）
 * - 死循环协作中断 → `an infinite loop is cooperatively interrupted...`（协作层→PHASE_TIMEOUT，
 *   不熔断；catch 顺序钉子之一）
 * - 内存超限 OOM 路径 → `memory over the heap budget...`（memoryLimit=16MiB 引擎层 OOM）
 * - 无 IO 与网络 → `globals outside the sandbox do not exist`（JS 侧尝试即拒：裸 QuickJS
 *   无 fetch/document/window/process/require）
 * - 重复初始化与 close 幂等 → `closing is idempotent...`；批次 20 契约 17 例（core:domain
 *   JVM 契约测试，fake 承载）保持原样不动、全绿
 * - 异常类 catch 顺序 → 两枚钉子：死循环必须落在 QuickJsInterruptedException 分支
 *   （PHASE_TIMEOUT 而非 INVALID_PROGRAM），普通 JS 运行时错误必须落在 QuickJsException
 *   分支（INVALID_PROGRAM）——反序即红
 * - issue 240 → `issue240SelfQueuingJobIsKnownOpen...`（R1 §2.4 观察形状：独立 daemon 线程 +
 *   join 超时，**不**在测试线程直跑 `engine.execute`，也不调用原始 QuickJs.close()）。1.0.15
 *   上预期"5s 内不返回"；若返回则说明上游已修，断言必须反转为抛中断。该用例记录的是
 *   **T3 部分/阻塞**的已知缺陷，不是硬终止成功；泄漏 daemon 线程为已登记的 accept-the-leak。
 *
 *
 * 方法名刻意用无空格标识符：D8 在 DEX version < 040（minSdk 23 ⇒ dex 035）下拒绝
 * SimpleName 含空格（"Space characters in SimpleName ... are not allowed prior to
 * DEX version 040"），反引号空格名只在 JVM 上可编译。批次 21 设备轨实测该文件曾因此
 * 硬阻塞 `:core:data:dexBuilderDebugAndroidTest`，改名为标识符后设备门才可执行。
 * 设备侧归设备轨（计划 §3）：x86_64 .so 装载、16KB 真机装载、R8 后 JNI 面由设备门另行覆盖。
 * bootstrap 的运行时行为（Date 封闭/Math.random 确定性——R1 §4 明标 UNVERIFIED 的两项）
 * 在本文件首次真机验证。
 */
@RunWith(AndroidJUnit4::class)
class QuickJsVisualExecutionEngineInstrumentedTest {

    private val engines = CopyOnWriteArrayList<QuickJsVisualExecutionEngine>()

    @After
    fun closeEngines() {
        engines.forEach { it.close() }
        engines.clear()
    }

    /** 度量 stub：捕获批次（供 FrameInput 断言），可延迟（BUSY/宿主取消用例的慢相）。 */
    private class CapturingMeasurer(
        private val delayMillis: Long = 0,
    ) : VisualContentMeasurer {
        var calls = 0
            private set
        val payloads = CopyOnWriteArrayList<String>()

        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
            calls += 1
            batch.entries.forEach { payloads.add(it.payload) }
            if (delayMillis > 0) {
                delay(delayMillis)
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

    private fun engine(
        measurer: VisualContentMeasurer,
        cooperativeTimeoutMillis: Long = 100L,
        hardDeadlineMillis: Long = 1_500L,
    ): QuickJsVisualExecutionEngine = QuickJsVisualExecutionEngine(
        measurer,
        cooperativeTimeoutMillis = cooperativeTimeoutMillis,
        hardDeadlineMillis = hardDeadlineMillis,
    ).also { engines.add(it) }

    private fun request(
        sourceJs: String,
        stateJson: String? = null,
        explicitUserRetry: Boolean = false,
        seed: Long = 42L,
    ) = VisualExecutionRequest(
        programId = "program-1",
        sourceJs = sourceJs,
        sceneRevision = 1,
        stateJson = stateJson,
        viewport = VisualViewport(360, 240),
        timeMs = 1_000L,
        seed = seed,
        theme = "light",
        explicitUserRetry = explicitUserRetry,
    )

    @Test
    fun aProgramRendersWithFrozenFrameInputAndFrozenContentReferences() =
        runBlocking {
            val measurer = CapturingMeasurer()
            val engine = engine(measurer)

            val result = engine.execute(
                request(ECHO_SOURCE, stateJson = """{"answer":3}"""),
            )

            val rendered = result as VisualExecutionResult.Rendered
            // render 只引用本事务冻结的 contentId（契约 §3.2 第 3 步）。
            assertEquals(
                """{"schemaVersion":1,"sceneId":"scene-1","revision":1,""" +
                    """"label":{"contentId":"content-0"}}""",
                rendered.sceneJson,
            )
            assertEquals(1, rendered.sceneSchemaVersion)
            assertEquals(setOf("content-0"), rendered.contentIds)
            // 契约 §3.2：每事务至多一次 prepare（度量恰一批）、一次 render。
            assertEquals(1, measurer.calls)
            // FrameInput 逐字段冻结（契约 §3.2 FrameInput；state 为原文内嵌 JSON）。
            // 键序 = 测试程序 prepare 里 JS 对象字面量的插入序，JSON.stringify 保序。
            assertEquals(
                """{"programId":"program-1","revision":1,"seed":42,"theme":"light",""" +
                    """"timeMs":1000,"width":360,"height":240,"state":{"answer":3}}""",
                measurer.payloads.first(),
            )
        }

    @Test
    fun bootstrapClosesDateAndReplaysMathRandomDeterministicallyFromTheSeed() =
        runBlocking {
            val measurer = CapturingMeasurer()
            val engine = engine(measurer)

            val first = engine.execute(request(RANDOM_SOURCE)) as VisualExecutionResult.Rendered
            val firstPayload = measurer.payloads.removeAt(0)
            val second = engine.execute(request(RANDOM_SOURCE)) as VisualExecutionResult.Rendered
            val secondPayload = measurer.payloads.removeAt(0)
            engine.execute(request(RANDOM_SOURCE, seed = 43L))

            // Date 已封闭；同一 seed 两事务的随机序列逐位重放；序列在事务内推进。
            assertEquals("undefined", firstPayload.substringBefore('|'))
            assertTrue(
                firstPayload.substringAfter('|').substringBefore('|') !=
                    firstPayload.substringAfterLast('|'),
            )
            assertEquals(firstPayload, secondPayload)
            // 不同 seed 派生不同序列。
            assertTrue(measurer.payloads.first() != firstPayload)
        }

    @Test
    fun anInfiniteLoopIsCooperativelyInterruptedAndReportedAsAPhaseTimeout() =
        runBlocking {
            val measurer = CapturingMeasurer()
            val engine = engine(measurer, cooperativeTimeoutMillis = 200L, hardDeadlineMillis = 10_000L)

            val first = engine.execute(request(LOOP_SOURCE))
            val second = engine.execute(request(LOOP_SOURCE))

            val rejected = first as VisualExecutionResult.Rejected
            // 协作层：QuickJsInterruptedException 分支（catch 顺序钉子——反序会落进
            // QuickJsException 分支误报 INVALID_PROGRAM）。
            assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, rejected.reason)
            // 协作层不是硬终止：不得熔断——同源重跑应再次真实执行（而非 PROGRAM_FUSED）。
            assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, (second as VisualExecutionResult.Rejected).reason)
            assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        }

    @Test
    fun memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit() = runBlocking {
        val measurer = CapturingMeasurer()
        // 本用例只考察堆上限：把协作期限放宽到 10s，避免 16MiB 尚未分配完就先被
        // evaluationTimeoutMillis 中断成 PHASE_TIMEOUT（那会掩盖 memoryLimit 路径）。
        val engine = engine(
            measurer,
            cooperativeTimeoutMillis = 10_000L,
            hardDeadlineMillis = 30_000L,
        )

        val result = engine.execute(request(OOM_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // 引擎层 OOM（memoryLimit=16MiB，QuickJS 原生 "out of memory"）。
        // 失败时把实际 reason/diagnostics 带进断言消息（设备轨实测该断言曾红：
        // x86_64 上实际为 INVALID_PROGRAM；诊断为 "InternalError: string too long"）。
        // 根因是旧夹具反复翻倍字符串，先撞 QuickJS 的单字符串长度上限，根本没走到堆上限；
        // OOM_SOURCE 已改为持续分配数组/对象（不触发 string too long），断言不放宽。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.MEMORY_LIMIT,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
    }

    @Test
    fun globalsOutsideTheSandboxDoNotExist() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        for (name in listOf("fetch", "document", "window", "process", "require")) {
            val result = engine.execute(request(probeSource(name)))
            val rejected = result as VisualExecutionResult.Rejected
            assertEquals("global $name must be absent", VisualExecutionRejection.SANDBOX_VIOLATION, rejected.reason)
            assertTrue(rejected.diagnostics.contains(name))
            assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        }
    }

    @Test
    fun aSecondTransactionCannotObserveGlobalsDeclaredByTheFirst() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val first = engine.execute(request(WRITER_SOURCE))
        val second = engine.execute(request(READER_SOURCE))

        assertTrue(first is VisualExecutionResult.Rendered)
        val rejected = second as VisualExecutionResult.Rejected
        // 每事务新上下文由结构保证（prepare/render 各全新 QuickJs 实例）。
        assertEquals(VisualExecutionRejection.SANDBOX_VIOLATION, rejected.reason)
        assertTrue(rejected.diagnostics.contains("injected"))
    }

    @Test
    fun aMissingVisualObjectAndASyntaxErrorAreInvalidPrograms() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val missing = engine.execute(request("var unrelated = 1;"))
        val broken = engine.execute(request("const visual = {"))

        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, (missing as VisualExecutionResult.Rejected).reason)
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, (broken as VisualExecutionResult.Rejected).reason)
        assertEquals(0, measurer.calls)
    }

    @Test
    fun aJsRuntimeErrorIsReportedAsABoundedDiagnosticNotAnException() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val result = engine.execute(request(TYPE_ERROR_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // catch 顺序第二枚钉子：普通 JS 错误走 QuickJsException 分支（若 Interrupted 分支
        // 反序吞掉它会变成 PHASE_TIMEOUT；若泛化 Throwable 吞掉则诊断丢失文案）。
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, rejected.reason)
        assertTrue(rejected.diagnostics.contains("boom-js"))
        assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
    }

    @Test
    fun renderCannotReferenceContentNotFrozenInThisTransaction() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val result = engine.execute(request(DANGLING_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.INVALID_SCENE, rejected.reason)
        assertTrue(rejected.diagnostics.contains("ghost-1"))
    }

    @Test
    fun prepareOverTheContentRequestBudgetIsRejected() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val result = engine.execute(request(OVER_BUDGET_PREPARE_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, rejected.reason)
        // 超限不截断成"正确图"：度量一批都没发生。
        assertEquals(0, measurer.calls)
    }

    @Test
    fun issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer, cooperativeTimeoutMillis = 100L, hardDeadlineMillis = 600L)

        // R1 §2.4 观察形状：execute 放独立 daemon 线程，测试线程只做 join 超时。
        // 1.0.15 上 evaluate 在排水循环里持 jsMutex 自旋，close() 也卡同一把锁；
        // 若直接在测试线程 runBlocking { engine.execute(...) }，整个 androidTest 门会被拖死
        // （批次 21 设备轨 step2-thread-dump.txt 的 Instr 线程就是这个形状）。
        val done = CountDownLatch(1)
        thread(isDaemon = true, name = "issue240-evaluate") {
            try {
                // 独立 daemon 线程上的 runBlocking：挂死的是这条线程，不是 Instr 线程。
                runBlocking { engine.execute(request(ISSUE_240_SOURCE)) }
            } catch (_: Throwable) {
                // 上游未来修复后这里可能收到中断；是否返回由 done 观察，不在这里断言。
            } finally {
                done.countDown()
            }
        }

        // v1.0.15 预期：5s 内不返回（issue 作者实证 + 钉版源码结构核实）。若返回，说明
        // 上游已修（>1.0.15），本用例必须反转为"协作中断可终止"，不得继续把挂死当绿。
        val returned = done.await(5, TimeUnit.SECONDS)
        assertFalse(
            "issue #240 appears fixed: execute returned within 5s on quickjs-kt 1.0.15; " +
                "reverse this observation into an interruption assertion after the version bump",
            returned,
        )

        // 阻塞的是一个挂起的 execute：finally 没执行，inFlight 仍为 true。后续请求因此是
        // BUSY（不是 PROGRAM_FUSED，也不会执行一次新的 QuickJS 事务）——引擎实例并不
        // "继续可用"；如实钉住该已知形态。
        val second = engine.execute(request(ISSUE_240_SOURCE))
        assertEquals(
            VisualExecutionRejection.BUSY,
            (second as VisualExecutionResult.Rejected).reason,
        )

        // 本用例不调用原始 QuickJs.close()（会挂 jsMutex）；engine.close() 在 @After 走
        // 专用守护线程强弃，立即返回。泄漏的 evaluate/force-close daemon 线程随用例进程
        // 回收，是 R1 §2.3.4 的 accept-the-leak，不是通过项。
    }

    @Test
    fun aConcurrentExecutionIsRejectedAsBusy() = runBlocking {
        val measurer = CapturingMeasurer(delayMillis = 300L)
        val engine = engine(measurer, cooperativeTimeoutMillis = 5_000L)

        val first = async { engine.execute(request(ECHO_SOURCE)) }
        delay(50)
        val second = engine.execute(request(ECHO_SOURCE))

        // 契约 §4：全局一个在飞绘制；不排队第二事务。
        assertEquals(VisualExecutionRejection.BUSY, (second as VisualExecutionResult.Rejected).reason)
        assertTrue(second.diagnostics.length <= 512)
        assertTrue(first.await() is VisualExecutionResult.Rendered)
    }

    @Test
    fun theHostAbandoningAnInFlightTransactionFreesTheSingleFlightSlot() =
        runBlocking {
            val measurer = CapturingMeasurer()
            val engine = engine(measurer, cooperativeTimeoutMillis = 5_000L, hardDeadlineMillis = 5_000L)

            // 忙循环占住在飞事务（协作期限足够长，本用例只考察宿主取消）。
            val job = launch { engine.execute(request(LOOP_SOURCE)) }
            delay(100)
            job.cancelAndJoin()

            val second = engine.execute(request(ECHO_SOURCE))

            assertTrue(second is VisualExecutionResult.Rendered)
        }

    @Test
    fun closingIsIdempotentAndExecuteAfterCloseIsRejected() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        engine.close()
        engine.close()

        val result = engine.execute(request(ECHO_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(VisualExecutionRejection.ENGINE_CLOSED, rejected.reason)
        assertEquals(0, measurer.calls)
    }

    private companion object {
        /** 正常程序：prepare 恰一批（带全 FrameInput 字段），render 恰一次并回显冻结 contentId。 */
        val ECHO_SOURCE = """
            const visual = {
              prepare: function (input) {
                return [{ kind: 'text', payload: JSON.stringify({
                  programId: input.programId, revision: input.revision, seed: input.seed,
                  theme: input.theme, timeMs: input.timeMs,
                  width: input.viewport.widthDu, height: input.viewport.heightDu,
                  state: input.state }) }];
              },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                         label: { contentId: metrics[0].contentId } };
              },
            };
        """.trimIndent()

        /** bootstrap 验证：Date 封闭 + Math.random 走注入 PRNG。 */
        val RANDOM_SOURCE = """
            const visual = {
              prepare: function (input) {
                return [{ kind: 'text', payload: typeof Date + '|' + Math.random() + '|' + Math.random() }];
              },
              render: function (input, metrics) { return { label: { contentId: metrics[0].contentId } }; },
            };
        """.trimIndent()

        val LOOP_SOURCE = """
            const visual = {
              prepare: function () { while (true) {} },
              render: function () { return {}; },
            };
        """.trimIndent()

        /**
         * 堆上限路径：持续把新分配的对象放进数组，直到 JS 堆超 16MiB。
         * 旧夹具 `s = s + s` 会先触发 QuickJS 的 "string too long"（单字符串长度上限），
         * 被分类为 INVALID_PROGRAM，未覆盖 memoryLimit——设备轨实测曾因此红。
         */
        val OOM_SOURCE = """
            const visual = {
              prepare: function () {
                var keep = [];
                while (true) {
                  keep.push(new Array(1024 * 1024).fill(1));
                }
              },
              render: function () { return {}; },
            };
        """.trimIndent()

        /** R1 §2.4 / issue #240 作者复现，套进 visual 协议：顶层 f() 在源码求值内自续排队。 */
        val ISSUE_240_SOURCE = """
            function f() { Promise.resolve().then(f); while (true) {} }
            f();
            const visual = { prepare: function () { return []; }, render: function () { return {}; } };
        """.trimIndent()

        val TYPE_ERROR_SOURCE = """
            const visual = {
              prepare: function () { throw new TypeError('boom-js'); },
              render: function () { return {}; },
            };
        """.trimIndent()

        val WRITER_SOURCE = """
            const visual = {
              prepare: function () { globalThis.injected = 'leaked'; return []; },
              render: function () { return {}; },
            };
        """.trimIndent()

        val READER_SOURCE = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: String(injected) }]; },
              render: function () { return {}; },
            };
        """.trimIndent()

        val DANGLING_SOURCE = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '标签' }]; },
              render: function () { return { label: { contentId: 'ghost-1' } }; },
            };
        """.trimIndent()

        val OVER_BUDGET_PREPARE_SOURCE = """
            const visual = {
              prepare: function () {
                var entries = [];
                for (var i = 0; i < 129; i++) { entries.push({ kind: 'text', payload: 'x' }); }
                return entries;
              },
              render: function () { return {}; },
            };
        """.trimIndent()

        fun probeSource(name: String): String = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: String($name) }]; },
              render: function () { return {}; },
            };
        """.trimIndent()
    }
}
