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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * [QuickJsVisualExecutionEngine] 仪器化用例（批次 21 T3，真引擎上的契约验收；设备轨执行）。
 *
 * 任务书用例清单（计划 T3 第 6 条）逐条映射：
 * - 正常渲染 → `a program renders...`（FrameInput 逐字段 + prepare/render 各恰一次的度量面）
 * - 死循环协作中断 → `an infinite loop is cooperatively interrupted...`（协作层→PHASE_TIMEOUT，
 *   不熔断；类型优先分类的钉子之一）
 * - 内存超限 OOM 路径 → R3 §5 的确定性探针（批次 22 T2 替换旧夹具）：
 *   P1 `memoryOverTheHeapBudget...`（单次 64 MiB ArrayBuffer，message 可读的堆上限主路径）、
 *   P2 `heapExhaustionWithLostErrorMessage...`（50 万小对象链表，头节点钉在 `globalThis`
 *   上作根引用，`"null"` 由堆证据归属；见用例 KDoc 与 R2 记账研究）、
 *   P3 `stringLengthCapIsNotAHeapOutOfMemory`（rope 翻倍负控：`string too long` 不是堆上限）、
 *   P4 `nullThrownAtLowMemoryIsUnattributedInvalidProgram`（`throw null` 低堆，内部
 *   UNATTRIBUTED → 对外 INVALID_PROGRAM + `[UNATTRIBUTED]` 前缀；T2 裁定不新增 UNKNOWN）、
 *   P5 `forgedOutOfMemoryMessageIsInvalidProgram`（伪造 OOM 文案不得误报）。
 *   夹具选择依据 `docs/research/2026-10-26-quickjs-oom-semantics.md` §5：分配型、必然先撞堆、
 *   单字符串长度远低于 `JS_STRING_LEN_MAX = 2^30-1`（P3 除外，故意越界作负控）。
 * - 无 IO 与网络 → `globals outside the sandbox do not exist`（JS 侧尝试即拒：裸 QuickJS
 *   无 fetch/document/window/process/require）
 * - 重复初始化与 close 幂等 → `closing is idempotent...`；批次 20 契约 17 例（core:domain
 *   JVM 契约测试，fake 承载）保持原样不动、全绿
 * - close/execute 竞态（批次 22 T6，KD-71）→ `closeDuringAnInFlightTransactionWaitsAndRejectsIt`
 *   （确定性：close 等在飞事务、事务被强弃后回 ENGINE_CLOSED）与
 *   `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（32 轮无协调压测：
 *   只允许 Rendered/ENGINE_CLOSED，close 后不得再执行）
 * - 异常分类（类型优先） → 两枚钉子：死循环必须落在中断类型分支（PHASE_TIMEOUT 而非
 *   INVALID_PROGRAM），普通 JS 运行时错误必须落 INVALID_PROGRAM（P5 钉住严格前缀）。
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

    /**
     * 卡在度量阶段的 measurer（KD-71 并发用例）：`measure` 进入后立刻放行
     * [awaitEntered]，然后阻塞到测试调用 [release]。由此得到一个**确定性的在飞事务**：
     * 进入 measure 前登记已完成，且事务不可能自行结束——close 若不等它，就必然先返回。
     * 5s 超时兜底，测试断言失败时也不会把执行线程永久挂住。
     */
    private class GatedMeasurer : VisualContentMeasurer {
        private val entered = CountDownLatch(1)
        private val gate = CountDownLatch(1)

        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
            entered.countDown()
            gate.await(5, TimeUnit.SECONDS)
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

        fun awaitEntered(timeout: Long, unit: TimeUnit): Boolean = entered.await(timeout, unit)

        fun release() = gate.countDown()
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
            // 协作层：类型优先分类（QuickJsInterruptedException → PHASE_TIMEOUT，先于任何
            // 消息匹配）。若类型分支失效，消息面会把它折成 INVALID_PROGRAM——本用例即红。
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
        // P1（R3 §5.2）：单次 64 MiB ArrayBuffer。请求被拒后预算仍剩约 15 MiB，
        // QuickJS 能构造出 OOM Error，message 应可读为 "InternalError: out of memory"。
        // 设备轨历史上落 INVALID_PROGRAM 的是旧夹具（rope 翻倍先撞单字符串长度上限）；
        // 本断言不放宽：若设备仍拿不到可读 message，说明 AAR 形态与源码推导不一致，
        // 按 KD-70 记录 diagnostics，而不是把断言改成"有界拒绝即可"。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.MEMORY_LIMIT,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("16777216"))
        assertTrue(rejected.diagnostics.contains("out of memory"))
        assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
    }

    /**
     * P2：消息丢失的堆耗尽必须由 `memoryUsage` 比值归属为 `MEMORY_LIMIT`。
     *
     * **批次 23 设备形状（E-02）与本批修复.** 旧夹具是"50 万节点链表只挂在
     * `prepare` 的局部变量上"，设备读到 `[UNATTRIBUTED] … raw=null, mallocSize=177216,
     * mallocLimit=16777216`（1.06%，批次 22/23 两次同值）。R2
     * （`docs/research/2026-10-26-quickjs-memory-accounting.md` §2）给出主机制：OOM 以
     * JS_NULL 抛出后，`JS_CallInternal` 的异常出口先释放该帧所有局部变量与栈值
     * （`quickjs.c:20690`、`:20699-20709`）；引用计数归零的对象立即释放并级联释放
     * `next` 属性（`:6474-6485`、`:6341-6353`、`:6097-6117`）。旧夹具的链只由局部 `head`
     * 与节点 `next` 属性持有 → 局部释放即整链同步释放，发生在 `JS_Eval` 返回前；引擎
     * 随后读到的只是回落水位（次要机制：分配前 `js_trigger_gc` 与 job 排水中的 GC，
     * `quickjs.c:5619`、`QuickJs.jni.kt:590`，同向）。因此旧形状**不可能**产生比值证据；
     * 换夹具是唯一出路，**不得下调 90% 阈值**。
     *
     * **本批新形状（预期形态，设备复跑归设备轨）**：
     * - 每次迭代把最新头节点写到 `globalThis.__visualHeapProbeHead`，链表始终有根引用；
     * - 失败仍发生在分配 `sizeof(JSObject)` 量级时 → Error 对象放不下 → 消息丢失
     *   （`raw` 为 `"null"` 或 Java null）；
     * - 读快照时 `mallocSize` 应 ≥ 90% × 16777216（≥ 15099495），`mallocLimit=16777216`；
     * - reason=`MEMORY_LIMIT`，诊断含 `attributed by memoryUsage` 与
     *   `PENDING_CALIBRATION`（比值主证据尚未在设备校准成功，R2 §4）。
     * 若设备复跑仍读到约 177216：说明锚定未生效或该 AAR 另有回收路径，把实际数字落盘并按
     * KD-70 继续跟踪——不得改成"有界拒绝即可"，也不得调低阈值。
     *
     * **本批（批次 24 T2）未运行 Gradle/设备命令**（门禁由脚本串行执行、设备复跑归设备轨）；
     * 下面的断言是设备轨要执行的判据，不是已通过的结果。
     */
    @Test
    fun heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(
            measurer,
            cooperativeTimeoutMillis = 10_000L,
            hardDeadlineMillis = 30_000L,
        )

        val result = engine.execute(request(HEAP_LIST_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // P2（R3 §5.3 + R2 §2）：链表全部可达（钉在 globalThis 上，OOM 后的 GC 也收不走），
        // 失败分配只有 sizeof(JSObject) 量级，余量连 OOM Error 都放不下 → quickjs-kt 只留下
        // "null"。分类必须靠 memoryUsage.mallocSize >= 90% mallocLimit 归属堆上限，
        // 并把"比值尚未校准"如实标成 PENDING_CALIBRATION。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.MEMORY_LIMIT,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("attributed by memoryUsage"))
        assertTrue(rejected.diagnostics.contains("mallocLimit=16777216"))
        assertTrue(rejected.diagnostics.contains("PENDING_CALIBRATION"))
        // -1 = 快照没取到（heap == null）；这条形状要求真的读到水位。
        assertFalse(rejected.diagnostics.contains("mallocSize=-1"))
        assertTrue(rejected.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
    }

    @Test
    fun stringLengthCapIsNotAHeapOutOfMemory() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(
            measurer,
            cooperativeTimeoutMillis = 10_000L,
            hardDeadlineMillis = 30_000L,
        )

        val result = engine.execute(request(STRING_ROPE_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // P3（R3 §5.4）：rope 翻倍到 2^30-1 抛 "InternalError: string too long"；
        // 堆几乎没涨。这条负控证明"消息里有 InternalError ≠ OOM"，
        // 也钉住旧夹具红例的机制不再被归成 MEMORY_LIMIT。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.INVALID_PROGRAM,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("string too long"))
        assertFalse(rejected.diagnostics.contains("attributed by memoryUsage"))
    }

    @Test
    fun nullThrownAtLowMemoryIsUnattributedInvalidProgram() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val result = engine.execute(request(THROW_NULL_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // P4（R3 §5.5）：throw null 与真 OOM 的 message 同为 "null"，但堆很低。
        // 对外枚举本批不新增 UNKNOWN（T2 裁定），折成 INVALID_PROGRAM，
        // 以 [UNATTRIBUTED] 诊断前缀如实表达"原因不可归属"。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.INVALID_PROGRAM,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.startsWith("[UNATTRIBUTED]"))
        assertFalse(rejected.diagnostics.contains("attributed by memoryUsage"))
    }

    @Test
    fun forgedOutOfMemoryMessageIsInvalidProgram() = runBlocking {
        val measurer = CapturingMeasurer()
        val engine = engine(measurer)

        val result = engine.execute(request(FORGED_OOM_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // P5（R3 §5.5）：旧实现 contains("out of memory") 会把这条误报成 MEMORY_LIMIT；
        // 严格前缀只认引擎生成的 InternalError 文案。伪造后果仅是有界拒绝被标错类，
        // 不是认证机制——但正向的误报必须被钉住。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.INVALID_PROGRAM,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("RangeError: out of memory"))
        assertFalse(rejected.diagnostics.startsWith("[UNATTRIBUTED]"))
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
        // 分类第二枚钉子：普通 JS 错误不得走中断类型分支（否则会变成 PHASE_TIMEOUT），
        // 也不得被泛化 Throwable 吞掉（否则诊断丢失文案）；必须保留程序文案并归 INVALID_PROGRAM。
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
        // 专用守护线程强弃，随后最多等 hardDeadline + 500ms（本用例 600 + 500）后返回——
        // 不会被 #240 永久挂住，也不会把 @After 拖死。泄漏的 evaluate/force-close daemon
        // 线程随用例进程回收，是 R1 §2.3.4 的 accept-the-leak，不是通过项。
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

    /**
     * KD-71 的确定性红例（批次 22 T6）：close 与一个**已登记且在飞**的事务并发。
     *
     * 旧实现里 close 只 `currentTransaction.get()?.forceAbandon()`：measure 期间活引擎为
     * null（prepare 的上下文已关闭），强弃什么也没做；close 立即返回，事务随后照常 render
     * 并返回 `Rendered`——即"关闭后仍执行成功"。新实现下：
     * - close 在同一临界区取走已登记事务并置 closed；
     * - close 强弃后**有界等待**事务收尾（gate 未放行前不可能完成，200ms 内不得返回）；
     * - 事务在 render 阶段开始前看到 `abandoned`，返回 `ENGINE_CLOSED`。
     * 若把新实现的 close 改回"不强弃/不等待"，本用例第一条断言即红；若阶段不做
     * abandoned 检查，第二条断言会拿到 `Rendered` 而红。
     */
    @Test
    fun closeDuringAnInFlightTransactionWaitsAndRejectsIt() {
        val measurer = GatedMeasurer()
        val engine = engine(
            measurer,
            cooperativeTimeoutMillis = 5_000L,
            hardDeadlineMillis = 10_000L,
        )
        val outcome = AtomicReference<VisualExecutionResult?>(null)
        val executeThread = thread(isDaemon = true, name = "close-race-execute") {
            try {
                outcome.set(runBlocking { engine.execute(request(ECHO_SOURCE)) })
            } catch (failure: Throwable) {
                outcome.set(
                    VisualExecutionResult.Rejected(
                        VisualExecutionRejection.INVALID_PROGRAM,
                        "execute threw: ${failure.javaClass.simpleName}",
                    ),
                )
            }
        }
        assertTrue(
            "execute must reach the measurer before close is attempted",
            measurer.awaitEntered(5, TimeUnit.SECONDS),
        )

        val closeReturned = CountDownLatch(1)
        val closeThread = thread(isDaemon = true, name = "close-race-close") {
            engine.close()
            closeReturned.countDown()
        }
        // 事务被 gate 卡在 measure，close 若"立即返回"就说明它没有等这个在飞事务。
        assertFalse(
            "close must wait for the in-flight transaction; it returned before the gate was released",
            closeReturned.await(200, TimeUnit.MILLISECONDS),
        )

        measurer.release()
        executeThread.join(5_000)
        assertFalse("execute must finish after the gate is released", executeThread.isAlive)
        assertTrue(
            "close must finish once the transaction drains",
            closeReturned.await(5, TimeUnit.SECONDS),
        )
        closeThread.join(1_000)

        val rejected = outcome.get() as VisualExecutionResult.Rejected
        assertEquals(
            "an execute that was in flight when close landed must be aborted, not rendered",
            VisualExecutionRejection.ENGINE_CLOSED,
            rejected.reason,
        )

        // close 返回后引擎是终态：后续 execute 立即拒绝，且不触达度量/JS。
        val afterClose = runBlocking { engine.execute(request(ECHO_SOURCE)) }
        assertEquals(
            VisualExecutionRejection.ENGINE_CLOSED,
            (afterClose as VisualExecutionResult.Rejected).reason,
        )
    }

    /**
     * KD-71 并发压测（批次 22 T6）：多轮让 close 与 execute 自然竞争登记窗口。
     *
     * 每轮不注入调度延迟，允许两种合法结果：
     * - `Rendered`：事务在 close 之前完成（close 无需强弃，属于"execute 成功且 close 等其
     *   结束"分支——close 的等待由 `closeDuringAnInFlightTransactionWaitsAndRejectsIt`
     *   确定性钉住）；
     * - `ENGINE_CLOSED`：close 先登记生效，事务被强弃/被拒。
     * 其余任何结果（尤其 BUSY、库异常穿透）都视为回归。每轮 close 返回后再发一次 execute，
     * 断言终态永不执行（KD-71 的核心"无关闭后仍执行成功"）。
     */
    @Test
    fun concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome() {
        val rounds = 32
        repeat(rounds) { round ->
            val engine = engine(CapturingMeasurer())
            val outcome = AtomicReference<VisualExecutionResult?>(null)
            val started = CountDownLatch(1)
            val executeThread = thread(isDaemon = true, name = "close-stress-execute-$round") {
                started.countDown()
                try {
                    outcome.set(runBlocking { engine.execute(request(ECHO_SOURCE)) })
                } catch (failure: Throwable) {
                    outcome.set(
                        VisualExecutionResult.Rejected(
                            VisualExecutionRejection.INVALID_PROGRAM,
                            "execute threw: ${failure.javaClass.simpleName}",
                        ),
                    )
                }
            }
            assertTrue("round $round: execute did not start", started.await(5, TimeUnit.SECONDS))
            engine.close()
            executeThread.join(5_000)
            assertFalse("round $round: execute leaked past close", executeThread.isAlive)

            val result = outcome.get()
            assertTrue("round $round: execute produced no result", result != null)
            when (result) {
                null -> fail("round $round: execute produced no result")
                is VisualExecutionResult.Rendered -> Unit
                is VisualExecutionResult.Rejected -> assertEquals(
                    "round $round: only ENGINE_CLOSED may be reported once close landed",
                    VisualExecutionRejection.ENGINE_CLOSED,
                    result.reason,
                )
            }

            val afterClose = runBlocking { engine.execute(request(ECHO_SOURCE)) }
            assertEquals(
                "round $round: a closed engine must never execute again",
                VisualExecutionRejection.ENGINE_CLOSED,
                (afterClose as VisualExecutionResult.Rejected).reason,
            )
        }
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
         * P1（R3 §5.2）：单次 64 MiB `ArrayBuffer`，确定性撞 16 MiB 堆上限。
         * - 64 MiB < INT32_MAX，先过 `js_array_buffer_constructor3` 的长度检查；
         * - 不产生字符串，不会先撞 `JS_STRING_LEN_MAX`（2^30-1）；
         * - 请求被拒时仍剩约 15 MiB 预算，OOM Error 可构造（message 可读）。
         * 旧夹具（rope 翻倍 / 1M-array.fill）已删：前者必然先撞单字符串长度上限，
         * 后者依赖 fast-array 扩容、余量不可控（非确定性）。
         */
        val OOM_SOURCE = """
            const visual = {
              prepare: function () {
                var buffer = new ArrayBuffer(64 * 1024 * 1024);
                return [];
              },
              render: function () { return {}; },
            };
        """.trimIndent()

        /**
         * P2（R3 §5.3 + R2 §2）：50 万个小对象链表，**每次迭代把最新头节点钉到
         * `globalThis` 上**，整条链在 OOM 之后仍持有根引用。
         *
         * 旧形状（只存局部变量 `head`）在设备上被证明不成立：OOM 抛出后，`JS_CallInternal`
         * 的异常出口会释放该帧局部变量（`quickjs.c:20699-20709`），引用计数归零的链被
         * 同步级联释放（`:6474-6485`、`:6341-6353`、`:6097-6117`）→ 引擎读到 177216（1.06%）。
         * 锚定到 `globalThis` 后，局部释放不会让引用计数归零，读数才代表失败时的存活水位。
         *
         * 其余约束不变：20 MB > 16 MiB；单轮只分配 `sizeof(JSObject)` 量级，无大 realloc、
         * 无长字符串（否则会先撞 `JS_STRING_LEN_MAX` 或留下大余量）；有界循环——memoryLimit
         * 若失效，prepare 会正常返回 `[]`（测试红），不会靠超时兜底。
         */
        val HEAP_LIST_SOURCE = """
            const visual = {
              prepare: function () {
                var head = null;
                for (var i = 0; i < 500000; i++) {
                  head = { next: head };
                  globalThis.__visualHeapProbeHead = head;
                }
                return [];
              },
              render: function () { return {}; },
            };
        """.trimIndent()

        /**
         * P3（R3 §5.4）：rope 翻倍。第 ~30 次长度超过 `JS_STRING_LEN_MAX = 2^30-1`，
         * 抛 `InternalError: string too long`；堆只增加几十个 rope 节点。
         * 这是单字符串长度上限，不是堆上限——负控。
         */
        val STRING_ROPE_SOURCE = """
            const visual = {
              prepare: function () {
                var s = 'x';
                for (var i = 0; i < 40; i++) { s = s + s; }
                return [];
              },
              render: function () { return {}; },
            };
        """.trimIndent()

        /** P4（R3 §5.5）：message 与真 OOM 同为 `"null"`，但堆很低。 */
        val THROW_NULL_SOURCE = """
            const visual = {
              prepare: function () { throw null; },
              render: function () { return {}; },
            };
        """.trimIndent()

        /** P5（R3 §5.5）：伪造 OOM 文案；严格前缀必须把它留在 INVALID_PROGRAM。 */
        val FORGED_OOM_SOURCE = """
            const visual = {
              prepare: function () { throw new RangeError('out of memory'); },
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
