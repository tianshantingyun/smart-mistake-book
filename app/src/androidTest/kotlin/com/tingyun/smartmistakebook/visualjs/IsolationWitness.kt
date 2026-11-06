package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AssumptionViolatedException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 隔离套件自证 witness（批次 43 / WS3 item 19）。
 *
 * **它消灭的具体失败**（逐条对应 `docs/research/2026-11-05-isolation-selfwitness.md` §4.5）：
 * - W1：`OK (15 tests)` 被当成"15 例通过"。JUnit 的 assumption 失败既不进 `failures`
 *   也不进 `ignored`（`Result.assumptionFailureCount`），所以 15 个 `-4` 照样打印
 *   `OK (15 tests)`。witness 的 `body_begin` 只能由用例体本身写出——没有它，就不能说
 *   "这一例跑过"。
 * - W2：`-4` 无法归因到"未执行 / 环境不满足 / 夹具跳过"。本文件把三层证据落到同一条
 *   JSONL：rule 层（调度）、gate 层（为什么没通过）、body 层（体内是否进入、墙钟/CPU 窗）。
 * - W3：进程级单次探针把 15 例绑在同一个读数上。gate 行带 `reused` 标记，长期暴露
 *   缓存复用（`IsolationGate` 的进程级缓存）。
 * - W4：witness 写入失败被误读成"零执行"。`suite_start` 后立刻写 `canary` 并读回；
 *   失败时写 `witness_error` 并置 `WITNESS_UNAVAILABLE`，**不降级成零执行**。
 * - W5：方法清单与判据两处维护导致漂移。行里的 `method` 只有一个来源
 *   （[org.junit.runner.Description.methodName]），检查器再用
 *   `tools/ci/visual_js_isolation_methods.txt` 对账。
 *
 * **红线**（同文件中 §4.4）：`cpu_delta_ms / wall_ms` 只记录、**绝不设阈值**。环境是否
 * 低负载的判定权仍在 shell 侧 `tools/ci/lowload_gate.py`；witness 只回答"这一例跑没跑、
 * 跑了多久、期间本进程花了多少 CPU"。
 *
 * 输出：`targetContext.filesDir/isolation-witness/run-<stamp>/witness.jsonl`，一行一个
 * JSON 对象，append-only，每行 `flush()` + `fd.sync()`（进程被 SIGKILL 时最后一行不丢）。
 * 同时镜像到 logcat（`SMB_ISOLATION_WITNESS: <json>`），供文件不可写时的下限回读。
 *
 * 写入**永不向外抛**：witness 不能把 `-4` 变成 `-2`（`TestWatcher` 在多错误时会把
 * assumption 包成 `MultipleFailureException`，见研究件 §4.2）。
 */
internal object IsolationWitness {

    const val TAG: String = "SMB_ISOLATION_WITNESS"

    /** runner 注入的 run 标签；缺省由设备侧生成并在 `suite_start.run_source` 记 `generated`。 */
    const val ARG_RUN: String = "smb.witness.run"

    /** runner 注入的期望用例数（用于 `suite_end.expected_rows`；缺省不判）。 */
    const val ARG_EXPECTED_TESTS: String = "smb.witness.expected_tests"

    /**
     * runner 注入的**被测 app APK** sha256（宿主侧算好）。
     *
     * 不在设备上算：本进程要 sha256 的 base.apk 在本仓约 190 MB，流式摘要在慢设备上的
     * 耗时不可预期（批次 43 probe-01 在 `@BeforeClass` 阶段整进程死亡，instrument 流只有
     * `Process crashed.`、无任何 witness 行；ANR 原文是 `Timed out while trying to bind`，
     * 与哈希的因果未证）。改由宿主侧在安装前算好并通过本参数注入；设备侧只读
     * `sourceDir / size / versionCode / lastUpdateTime`（廉价且不可伪造）。
     */
    const val ARG_APK_SHA: String = "smb.witness.apk_sha256"

    /** 由 [T4SeamProbeRule] 在 `@Before` 之前写入；15 个用例体不再各自拼方法名。 */
    @Volatile
    var currentMethod: String? = null

    private val lock = Any()

    private var stream: FileOutputStream? = null
    private var sink: File? = null
    private var opened = false
    private var seq = 0
    private var rows = 0
    private var canaryOk = false
    private var unavailable: String? = null
    private var openedAtMs = 0L
    private var runLabel: String = ""

    /** 一次计时锚点；[tMs] 与用例内计时同钟（`SystemClock.uptimeMillis`）。 */
    data class Mark(val tMs: Long, val cpuMs: Long, val ns: Long)

    // ------------------------------------------------------------ suite lifecycle

    fun beginSuite() {
        synchronized(lock) {
            if (opened) return
            opened = true
            openedAtMs = SystemClock.uptimeMillis()
            val arguments = InstrumentationRegistry.getArguments()
            val declared = arguments.getString(ARG_RUN)?.trim()?.takeIf { it.isNotEmpty() }
            runLabel = declared ?: generatedLabel()
            val context = runCatching { ApplicationProvider.getApplicationContext<Context>() }.getOrNull()
            if (context == null) {
                unavailable = "no_context"
                return
            }
            val openResult = runCatching {
                val dir = File(context.filesDir, "isolation-witness/$runLabel")
                if (!dir.isDirectory && !dir.mkdirs()) error("mkdirs failed: $dir")
                sink = File(dir, "witness.jsonl")
                stream = FileOutputStream(sink, true)
            }
            if (openResult.isFailure) {
                unavailable = "open_failed:" + (openResult.exceptionOrNull()?.javaClass?.simpleName ?: "unknown")
                Log.w(TAG, "witness unavailable: $unavailable")
                return
            }
            runCatching {
                writeRow("suite_start") { row ->
                    row.put("run", runLabel)
                    row.put("run_source", if (declared != null) "argument" else "generated")
                    row.put("pid", Process.myPid())
                    row.put("uid", Process.myUid())
                    row.put("arm", IsolationArms.read().wire)
                    row.put("pkg", context.packageName)
                    row.put("sdk", Build.VERSION.SDK_INT)
                    row.put("expected_tests", expectedTests())
                    apkIdentity(context)?.let { row.put("apk", it) }
                }
                writeRow("canary") { row -> row.put("probe", "readback") }
                canaryOk = canaryReadback()
                if (!canaryOk) {
                    unavailable = "canary_readback_failed"
                    writeRow("witness_error") { row -> row.put("error", unavailable) }
                }
            }.onFailure {
                unavailable = "write_failed:" + (it.javaClass.simpleName)
                Log.w(TAG, "witness unavailable: $unavailable (${it.message})")
            }
        }
    }

    fun endSuite() {
        synchronized(lock) {
            if (!opened) return
            runCatching {
                writeRow("suite_end") { row ->
                    row.put("rows", rows)
                    expectedTests()?.let { tests -> row.put("expected_rows", 2 + 5 * tests + 1) }
                    row.put("canary_ok", canaryOk)
                    row.put("witness_unavailable", unavailable ?: JSONObject.NULL)
                    row.put("elapsed_ms", SystemClock.uptimeMillis() - openedAtMs)
                    row.put("file", sink?.absolutePath ?: "")
                }
            }
            runCatching { stream?.flush() }
            runCatching { stream?.fd?.sync() }
            runCatching { stream?.close() }
            stream = null
        }
    }

    // ------------------------------------------------------------ three levels

    /** L0：规则层。它在 `@Before` 之前进入，只证明"被调度"。 */
    fun ruleBegin(method: String) {
        val mark = snapshot()
        write("rule_begin", method, mark) { }
    }

    fun ruleEnd(method: String) {
        val mark = snapshot()
        write("rule_end", method, mark) { }
    }

    /** L1：门层。无条件写在 `Assume` 之前，所以"有 gate 行"= `require()` 真的进去了。 */
    fun recordGate(decision: GateDecision) {
        val mark = snapshot()
        write("gate", currentMethod, mark) { row ->
            row.put("verdict", if (decision.failure == null) "GATED" else "UNGATED")
            row.put("reason", decision.failure ?: "")
            row.put("shell_gate", decision.shellGate ?: "")
            row.put("shell_reason", decision.shellReason)
            row.put("reused", decision.reused)
            decision.reading?.let { row.put("reading", readingJson(it)) }
        }
    }

    /** L2：体内层。`body_begin` 是用例体的第一条语句，`@Before` 未过时不可能出现。 */
    fun bodyBegin(method: String?): Mark {
        val mark = snapshot()
        write("body_begin", method, mark) { row -> row.put("t_ns", mark.ns) }
        return mark
    }

    fun bodyEnd(method: String?, begin: Mark, outcome: String) {
        val end = snapshot()
        val wall = end.tMs - begin.tMs
        val cpuDelta = if (begin.cpuMs >= 0 && end.cpuMs >= 0) end.cpuMs - begin.cpuMs else -1L
        write("body_end", method, end) { row ->
            row.put("outcome", outcome)
            row.put("wall_ms", wall)
            row.put("cpu_delta_ms", cpuDelta)
            row.put("cpu_unreadable", begin.cpuMs < 0 || end.cpuMs < 0)
            row.put("threads", threadCount())
        }
    }

    // ------------------------------------------------------------ internals

    private fun snapshot(): Mark = Mark(
        tMs = SystemClock.uptimeMillis(),
        cpuMs = ownCpuMs(),
        ns = SystemClock.elapsedRealtimeNanos(),
    )

    private fun write(phase: String, method: String?, mark: Mark, body: (JSONObject) -> Unit) {
        writeRow(phase, mark.tMs, mark.cpuMs) { row ->
            row.put("method", method ?: JSONObject.NULL)
            body(row)
        }
    }

    /**
     * 单行写入。**必须由调用方持有 [lock]**（或经 [beginSuite]/[endSuite] 的 synchronized
     * 块），避免 seq 交错。任何异常都在这里吞掉并记入 logcat——witness 不改变用例结论。
     */
    private fun writeRow(
        phase: String,
        tMs: Long = SystemClock.uptimeMillis(),
        cpu: Long = ownCpuMs(),
        body: (JSONObject) -> Unit,
    ) {
        val payload = runCatching {
            JSONObject().apply {
                put("v", 1)
                put("seq", ++seq)
                put("phase", phase)
                put("t_ms", tMs)
                put("cpu_ms", cpu)
                if (cpu < 0) put("cpu_unreadable", true)
                body(this)
            }.toString()
        }.getOrElse {
            Log.w(TAG, "witness row build failed for $phase: ${it.message}")
            return
        }
        mirror(payload)
        val out = stream ?: return
        runCatching {
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.write('\n'.code)
            out.flush()
            out.fd.sync()
            rows += 1
        }.onFailure {
            unavailable = "write_failed:" + it.javaClass.simpleName
            Log.w(TAG, "witness write failed: ${it.message}")
        }
    }

    private fun mirror(payload: String) {
        runCatching { Log.i(TAG, payload) }
    }

    private fun canaryReadback(): Boolean = runCatching {
        val file = sink ?: return false
        val last = file.readLines(Charsets.UTF_8).lastOrNull { it.isNotBlank() } ?: return false
        val parsed = JSONObject(last)
        parsed.optString("phase") == "canary" && parsed.optInt("seq") > 0
    }.getOrDefault(false)

    private fun readingJson(reading: LowLoadProbe.Reading): JSONObject = JSONObject().apply {
        put("processors", reading.processors)
        put("jitter_p50_ms", reading.jitterP50Ms)
        put("jitter_p95_ms", reading.jitterP95Ms)
        put("jitter_max_ms", reading.jitterMaxMs)
        put("own_cpu_ms", reading.ownCpuMs)
        put("probe_window_ms", reading.probeWindowMs)
        put("thermal_status", reading.thermalStatus)
        put("low_memory", reading.lowMemory)
    }

    private fun expectedTests(): Int? = InstrumentationRegistry.getArguments()
        .getString(ARG_EXPECTED_TESTS)?.trim()?.toIntOrNull()

    private fun generatedLabel(): String {
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return "generated-" + format.format(Date())
    }

    /** `/proc/self/stat` 的 utime+stime（USER_HZ=100），与 [LowLoadProbe] 同源；读不到返回 -1。 */
    private fun ownCpuMs(): Long {
        val text = runCatching { File("/proc/self/stat").readText() }.getOrNull() ?: return -1L
        val close = text.lastIndexOf(')')
        if (close < 0 || close + 2 >= text.length) return -1L
        val fields = text.substring(close + 2).trim().split(Regex("\\s+"))
        val utime = fields.getOrNull(11)?.toLongOrNull() ?: return -1L
        val stime = fields.getOrNull(12)?.toLongOrNull() ?: return -1L
        return (utime + stime) * 1000L / 100L
    }

    private fun threadCount(): Int =
        runCatching { File("/proc/self/task").list()?.size ?: -1 }.getOrDefault(-1)

    private fun apkIdentity(context: Context): JSONObject? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val source = info.applicationInfo?.sourceDir ?: return@runCatching null
        val file = File(source)
        val declaredSha = InstrumentationRegistry.getArguments()
            .getString(ARG_APK_SHA)?.trim()?.takeIf { it.isNotEmpty() }
        JSONObject().apply {
            put("source_dir", source)
            put("size", file.length())
            if (declaredSha != null) {
                put("sha256", declaredSha)
                put("sha256_source", "argument")
            } else {
                put("sha256_source", "unavailable")
            }
            put(
                "version_code",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                },
            )
            put("last_update_time", info.lastUpdateTime)
        }
    }.getOrNull()

}

/**
 * 15 个用例体的唯一包装（批次 43 / WS3 item 19）：`body_begin` / `body_end` 只能由用例体
 * 自身写出，这是"体内真的执行过"的唯一进程内证据（`@Rule` 与 `@After` 都在 `@Before`
 * 之外/之后，见研究件 §1.2）。
 *
 * `block` 保持 `suspend CoroutineScope.() -> Unit`，因此调用点只把 `= runBlocking {`
 * 换成 `= witnessBody {`：断言、缩进、协程作用域一字不改。`AssumptionViolatedException`
 * 原样重抛（保持 `-4` 语义）；witness 自身永不抛。
 */
internal fun witnessBody(block: suspend CoroutineScope.() -> Unit) {
    val method = IsolationWitness.currentMethod
    val begin = IsolationWitness.bodyBegin(method)
    var outcome = "EXCEPTION"
    try {
        runBlocking(block = block)
        outcome = "PASS"
    } catch (assumption: AssumptionViolatedException) {
        outcome = "ASSUMPTION"
        throw assumption
    } catch (failure: Throwable) {
        outcome = "FAIL"
        throw failure
    } finally {
        IsolationWitness.bodyEnd(method, begin, outcome)
    }
}
