package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * [VisualExecutionEngine] 的测试 fake（批次 20 T4 保守分支：**只有测试消费方**，无生产接线）。
 *
 * 它不执行真 JS（选型消费形态悬置，未引入引擎依赖），而是把真实实现要承担的**编排语义**
 * 在 JVM 里搭出来并接受契约用例的检验：专用单线程执行、每事务新上下文（脚本全局不跨事务）、
 * 输入字节预算先行拒绝、协作中断（脚本经 [FakeSandbox.checkInterrupt] 观察）与硬期限
 * （独立 watchdog 定时器 + 线程中断）两层终止、单在飞 BUSY、硬终止后按源码 SHA 熔断、
 * 幂等 close、宿主取消。
 *
 * **模拟保真度声明（诚实边界）**：真实实现的硬终止是 `:visual_js` 隔离进程
 * `killProcess(myPid())`（契约 §4），能终止任何循环；本 fake 只能用线程中断模拟，对完全
 * 不观察中断的循环无法终止——因此契约用例里的"硬终止"脚本以
 * `Thread.currentThread().isInterrupted` 或可中断睡眠观察终止信号。这一差距不随 fake
 * 消失，真实实现的仪器化（计划 §5 设备轨）必须重新覆盖。
 *
 * 脚本注册表把 sourceJs 字符串映射到 [FakeVisualProgram] 行为：未注册的 sourceJs 即
 * "源码不可求值"（[VisualExecutionRejection.INVALID_PROGRAM]），与真实引擎对未知/语法
 * 错误源码的行为同型。超预算的 sourceJs 在查表**之前**被拒。
 */
class FakeVisualExecutionEngine(
    private val scripts: Map<String, FakeVisualProgram>,
    private val measurer: VisualContentMeasurer,
    private val cooperativeTimeoutMillis: Long =
        VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS,
    private val hardDeadlineMillis: Long =
        VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS,
) : VisualExecutionEngine {

    private val closed = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val fusedProgramDigests = mutableSetOf<String>()
    private val currentTransaction = AtomicReference<Transaction?>(null)

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "fake-visual-js-watchdog").apply { isDaemon = true }
    }
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fake-visual-js-worker").apply { isDaemon = true }
    }

    /** [VisualExecutionRequest] 的 fake 侧视图（与端口请求逐字段一致，不复制成第二套事实）。 */
    class FakeFrameInput internal constructor(
        val programId: String,
        val sceneRevision: Int,
        val stateJson: String?,
        val viewport: VisualViewport,
        val timeMs: Long,
        val seed: Long,
        val theme: String,
    )

    /** fake 渲染输出：场景 JSON + 引用的 contentId（真实实现由原生校验器检查引用冻结纪律）。 */
    class FakeRenderOutput(
        val sceneJson: String,
        val referencedContentIds: Set<String> = emptySet(),
        val sceneSchemaVersion: Int = 1,
    )

    /** fake 程序行为：prepare/render 各调用一次（契约 §3.2 单事务纪律）。 */
    interface FakeVisualProgram {
        fun prepare(input: FakeFrameInput, sandbox: FakeSandbox): List<VisualContentMeasureEntry>

        fun render(
            input: FakeFrameInput,
            metrics: List<VisualMeasuredContent>,
            sandbox: FakeSandbox,
        ): FakeRenderOutput
    }

    /**
     * fake 沙箱：全局查找 = 事务内脚本声明 → SDK 白名单 → 否则视为不存在（对应 QuickJS
     * 的 ReferenceError；探针实测 fetch/document/window/process/require 均不存在）。
     * 堆记账按事务重置（每事务新上下文）。
     */
    class FakeSandbox internal constructor(private val transaction: Transaction) {

        companion object {
            /**
             * SDK 纯函数图形帮助白名单（4E 计划批次二 SDK 资产清单：line/arrow/circle/arc/
             * axes/polyline + input.seed 的 PRNG）。白名单之外一律不存在。
             */
            val SDK_GLOBALS: Map<String, String> = mapOf(
                "line" to "sdk:line",
                "arrow" to "sdk:arrow",
                "circle" to "sdk:circle",
                "arc" to "sdk:arc",
                "axes" to "sdk:axes",
                "polyline" to "sdk:polyline",
                "seedRandom" to "sdk:seedRandom",
            )
        }

        private val scriptGlobals = HashMap<String, Any?>()

        /** 读取全局；脚本声明优先，SDK 白名单次之，其余不存在即违规（可被脚本 catch，与 JS 同型）。 */
        fun global(name: String): Any? {
            if (scriptGlobals.containsKey(name)) return scriptGlobals[name]
            SDK_GLOBALS[name]?.let { return it }
            throw SandboxGlobalAbsentException(name)
        }

        /** 声明/赋值脚本全局（事务内有效；下一事务不可见——契约 §3.2 程序不能依赖跨调用全局变量）。 */
        fun declare(name: String, value: Any?) {
            scriptGlobals[name] = value
        }

        /** 堆记账：超过 [VisualExecutionBudget.MAX_HEAP_BYTES] 即引擎 OOM 路径。 */
        fun allocate(bytes: Long) {
            require(bytes >= 0L) { "allocation must be non-negative" }
            transaction.heapBytesUsed += bytes
            if (transaction.heapBytesUsed > VisualExecutionBudget.MAX_HEAP_BYTES) {
                throw HeapLimitExceededException(bytes, VisualExecutionBudget.MAX_HEAP_BYTES)
            }
        }

        val heapBytesUsed: Long get() = transaction.heapBytesUsed

        /** 协作中断观察点：模拟引擎在解释循环里检查 interrupt handler。 */
        fun checkInterrupt() {
            if (transaction.coopInterrupted) throw CooperativeInterruptException()
            if (transaction.hardKilled) throw HardKilledException()
        }

        /** 可中断等待：协作检查 + 线程中断都响应（给需要让出 CPU 的用例脚本用）。 */
        fun sleepInterruptibly(millis: Long) {
            var remaining = millis
            while (remaining > 0L) {
                checkInterrupt()
                val slice = min(10L, remaining)
                try {
                    Thread.sleep(slice)
                } catch (interrupted: InterruptedException) {
                    Thread.interrupted()
                    throw HardKilledException()
                }
                remaining -= slice
            }
            checkInterrupt()
        }
    }

    /** 访问不存在的全局（模拟 ReferenceError：fetch/document/window/process/require…）。 */
    class SandboxGlobalAbsentException(val name: String) :
        RuntimeException("'$name' is not defined")

    /** JS 堆上限触发（模拟引擎 OOM 异常路径）。 */
    class HeapLimitExceededException(val requestedBytes: Long, val limitBytes: Long) :
        RuntimeException("heap limit $limitBytes exceeded")

    /** 协作中断（模拟 QuickJsInterruptedException：引擎 interrupt 层）。 */
    class CooperativeInterruptException : RuntimeException("cooperative interrupt")

    /** 硬终止信号（模拟进程 kill 的可观察替代：线程中断）。 */
    class HardKilledException : RuntimeException("hard killed")

    override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
        if (closed.get()) {
            return rejected(VisualExecutionRejection.ENGINE_CLOSED, "engine is closed")
        }
        val sourceBytes = figureUsageUtf8Bytes(request.sourceJs)
        if (sourceBytes > VisualExecutionBudget.MAX_SOURCE_JS_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "sourceJs is $sourceBytes bytes, over ${VisualExecutionBudget.MAX_SOURCE_JS_BYTES}",
            )
        }
        val stateBytes = request.stateJson?.let { figureUsageUtf8Bytes(it) } ?: 0
        if (stateBytes > VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
            return rejected(
                VisualExecutionRejection.INPUT_OVER_BUDGET,
                "state is $stateBytes bytes, over ${VisualExecutionBudget.MAX_STATE_JSON_BYTES}",
            )
        }
        val digest = figureSha256Hex(request.sourceJs.toByteArray(StandardCharsets.UTF_8))
        if (!request.explicitUserRetry && digest in fusedProgramDigests) {
            return rejected(
                VisualExecutionRejection.PROGRAM_FUSED,
                "program digest ${digest.take(12)} is fused until explicit user retry",
            )
        }
        if (closed.get()) {
            return rejected(VisualExecutionRejection.ENGINE_CLOSED, "engine is closed")
        }
        if (!inFlight.compareAndSet(false, true)) {
            return rejected(
                VisualExecutionRejection.BUSY,
                "another visual transaction is already in flight",
            )
        }
        var future: Future<VisualExecutionResult>? = null
        try {
            val submitted = worker.submit(
                Callable { runTransactionOnWorker(request, digest) },
            )
            future = submitted
            // 等待必须离开调用方线程：runInterruptible 只提供"可被取消地阻塞"，不换线程；
            // 若在 runBlocking 单线程事件循环上内联 get()，并发 BUSY/宿主取消用例会整体死锁。
            val result = withContext(Dispatchers.IO) {
                runInterruptible { submitted.get() }
            }
            if (result is VisualExecutionResult.Rejected &&
                result.reason == VisualExecutionRejection.HARD_DEADLINE_EXCEEDED
            ) {
                fusedProgramDigests.add(digest)
            }
            return result
        } catch (cancel: CancellationException) {
            currentTransaction.get()?.abandon()
            future?.cancel(true)
            throw cancel
        } catch (interrupted: InterruptedException) {
            currentTransaction.get()?.abandon()
            future?.cancel(true)
            throw CancellationException("visual execution abandoned by the host")
        } catch (failure: ExecutionException) {
            // 阶段守卫之外炸出的 fake/脚本缺陷：有界暴露，不吞成成功。
            val causeName = failure.cause?.javaClass?.simpleName ?: failure.javaClass.simpleName
            return rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "worker failed: $causeName",
            )
        } finally {
            inFlight.set(false)
        }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        currentTransaction.get()?.abandon()
        worker.shutdownNow()
        scheduler.shutdownNow()
    }

    val isClosedForTest: Boolean get() = closed.get()

    val fusedDigestCountForTest: Int get() = fusedProgramDigests.size

    private fun runTransactionOnWorker(
        request: VisualExecutionRequest,
        digest: String,
    ): VisualExecutionResult {
        val workerThread = Thread.currentThread()
        // 上一事务若被硬终止，中断标志残留在这里；新事务开始前清掉（迟到定时器不得杀下一任务）。
        Thread.interrupted()
        val transaction = Transaction(request)
        currentTransaction.set(transaction)
        transaction.scheduleTimers(workerThread)
        try {
            return runPhases(request, digest, transaction)
        } catch (outcome: RejectedOutcomeException) {
            return outcome.outcome
        } catch (budget: IllegalArgumentException) {
            // 脚本超量请求度量/产出超限场景等预算越界：程序侧失败，有界暴露。
            return rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "program violated a transaction budget: ${budget.message ?: "unknown"}",
            )
        } finally {
            transaction.finish()
            currentTransaction.compareAndSet(transaction, null)
        }
    }

    private fun runPhases(
        request: VisualExecutionRequest,
        digest: String,
        transaction: Transaction,
    ): VisualExecutionResult {
        val script = scripts[request.sourceJs]
            ?: return rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                "no program registered for digest ${digest.take(12)}",
            )
        val sandbox = FakeSandbox(transaction)
        val input = FakeFrameInput(
            programId = request.programId,
            sceneRevision = request.sceneRevision,
            stateJson = request.stateJson,
            viewport = request.viewport,
            timeMs = request.timeMs,
            seed = request.seed,
            theme = request.theme,
        )
        val prepared = phase(transaction, "prepare") { script.prepare(input, sandbox) }
        val batch = VisualContentMeasureBatch(
            programId = request.programId,
            sceneRevision = request.sceneRevision,
            entries = prepared,
        )
        val measured = phase(transaction, "measure") { runBlocking { measurer.measure(batch) } }
        val contentIds = measured.entries.map { it.contentId }.toSet()
        val output = phase(transaction, "render") { script.render(input, measured.entries, sandbox) }
        val unknown = output.referencedContentIds - contentIds
        if (unknown.isNotEmpty()) {
            return rejected(
                VisualExecutionRejection.INVALID_SCENE,
                "render referenced content not frozen in this transaction: " +
                    unknown.sorted().joinToString(",").take(64),
            )
        }
        return VisualExecutionResult.Rendered(
            sceneJson = output.sceneJson,
            sceneSchemaVersion = output.sceneSchemaVersion,
            contentIds = output.referencedContentIds,
        )
    }

    /**
     * 单阶段守卫：把 fake 沙箱/脚本异常映射成有界拒绝；阶段"正常"返回后若发现协作中断或
     * 硬终止已发生，同样作废结果——真实引擎的评估被中断后不会正常返回（探针：无限循环返回
     * interrupted），fake 里不观察中断信号而自行返回的脚本因此也得到终止结果而非成功。
     */
    private fun <T> phase(transaction: Transaction, name: String, block: () -> T): T {
        val result = try {
            block()
        } catch (outcome: RejectedOutcomeException) {
            throw outcome
        } catch (interrupted: CooperativeInterruptException) {
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.PHASE_TIMEOUT,
                    "phase $name exceeded the cooperative timeout",
                ),
            )
        } catch (killed: HardKilledException) {
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "phase $name hit the watchdog hard deadline",
                ),
            )
        } catch (interrupted: InterruptedException) {
            Thread.interrupted()
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "phase $name was interrupted by the watchdog",
                ),
            )
        } catch (absent: SandboxGlobalAbsentException) {
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.SANDBOX_VIOLATION,
                    "'${absent.name}' is not defined: the sandbox has no IO/network/host globals",
                ),
            )
        } catch (oom: HeapLimitExceededException) {
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.MEMORY_LIMIT,
                    "heap exceeded ${oom.limitBytes} bytes during phase $name",
                ),
            )
        } catch (programFailure: RuntimeException) {
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.INVALID_PROGRAM,
                    "program failed in phase $name: " +
                        (programFailure.message ?: programFailure.javaClass.simpleName),
                ),
            )
        }
        if (Thread.currentThread().isInterrupted) {
            Thread.interrupted()
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                    "phase $name completed after a hard kill; the result is discarded",
                ),
            )
        }
        if (transaction.coopInterrupted) {
            throw RejectedOutcomeException(
                rejected(
                    VisualExecutionRejection.PHASE_TIMEOUT,
                    "phase $name completed after the cooperative timeout; the result is discarded",
                ),
            )
        }
        return result
    }

    private fun rejected(reason: VisualExecutionRejection, message: String) =
        VisualExecutionResult.Rejected(reason, message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS))

    private class RejectedOutcomeException(val outcome: VisualExecutionResult.Rejected) :
        RuntimeException(outcome.diagnostics)

    internal inner class Transaction(val request: VisualExecutionRequest) {
        @Volatile var coopInterrupted = false
        @Volatile var hardKilled = false
        @Volatile var finished = false
        @Volatile var heapBytesUsed = 0L
        private var cooperativeTimer: ScheduledFuture<*>? = null
        private var hardTimer: ScheduledFuture<*>? = null

        fun scheduleTimers(workerThread: Thread) {
            cooperativeTimer = scheduler.schedule(
                { if (!finished) coopInterrupted = true },
                cooperativeTimeoutMillis,
                TimeUnit.MILLISECONDS,
            )
            // 契约 §4：完成后迟到 timeout/取消不能杀下一任务——先核对本事务仍是当前事务。
            hardTimer = scheduler.schedule(
                {
                    if (!finished && currentTransaction.get() === this) {
                        hardKilled = true
                        workerThread.interrupt()
                    }
                },
                hardDeadlineMillis,
                TimeUnit.MILLISECONDS,
            )
        }

        fun finish() {
            finished = true
            cooperativeTimer?.cancel(false)
            hardTimer?.cancel(false)
        }

        fun abandon() {
            finished = true
            cooperativeTimer?.cancel(false)
            hardTimer?.cancel(false)
        }
    }
}
