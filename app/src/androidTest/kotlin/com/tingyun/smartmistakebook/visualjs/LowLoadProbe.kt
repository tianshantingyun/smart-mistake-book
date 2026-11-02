package com.tingyun.smartmistakebook.visualjs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import java.io.File
import java.util.Locale

/**
 * instrumentation 侧低负载自证探针 + 套件级门前置（批次 41 / WS4 item 25）。
 *
 * **它消灭的具体失败**：批次 40 的 isolation 只能写"单臂 15/15 绿"，因为没有任何
 * 东西能证明"这一窗是低负载"（`step04-isolation.md` §4）。R5 实测确认 API 34 上
 * app/instrumentation **读不到** `/proc/stat`、`/proc/loadavg`、`/proc/pressure/{cpu,io,irq,memory}`
 * （EACCES，AOSP 自 Android O 起 `dontaudit`）——所以"系统是否空闲"只能由
 * `tools/ci/lowload_gate.py` 在 adb shell 侧判定；instrumentation 内能做的是
 * **自证本进程当前是否被拖慢**，并把"门缺失/不通过"变成整套件跳过（`-4`），
 * 而不是静默放行。
 *
 * fail-closed 语义（R5 §4.8 / §4.7）：
 * - `smb.lowload.gate` 未传 / 不是 `PASS` → 判 `UNGATED`；
 * - 探针读不到 `/proc/self/stat`、内存压力、睡眠抖动自比值越界 → 判 `UNGATED`；
 * - `UNGATED` 通过 `Assume.assumeTrue(..., false)` 在每条用例前跳过，runner 报 `-4`。
 *   **跳过不算通过**：`tools/ci/isolation_pair_report.py` 把 `-4` 单列为 `SKIP`，
 *   并因为 logcat 里的 `SMB_LOWLOAD_GATE: verdict=UNGATED` 把该 run 的窗口判 INVALID。
 *
 * 机器行格式（报告工具按此解析，改格式必须同步
 * `tools/ci/isolation_pair_report.py:parse_gate_lines`）：
 *
 *     SMB_LOWLOAD_GATE: verdict=GATED jitter_p50_ms=.. ...
 *     SMB_LOWLOAD_GATE: verdict=UNGATED reason=<无空白的原因码>
 *
 * 通过 `-e smb.lowload.gate PASS` 从 shell 侧门注入；`tools/ci/run_isolation_pair.sh`
 * 负责跑前/跑后门并只在两次都 PASS 时注入 PASS。
 */
internal object LowLoadProbe {

    const val TAG: String = "SMB_LOWLOAD_GATE"

    /** 可选：同一 AVD 的地板（毫秒）；给了就走"3× 地板"判据，没给走自比值判据。 */
    const val ARG_JITTER_FLOOR_MS: String = "smb.lowload.jitter_p95_floor_ms"

    /** 自比值上限（R5 §4.8：实测静机 1.27、忙机 2.2）。 */
    const val SELF_RATIO_LIMIT: Double = 2.0

    /**
     * 自比值只在可观测的迟到量上判读。R5 的 1.27/2.2 来自 `date +%s%N` **子进程**探针
     * （p50 约 11 ms，含两次进程派发）；本探针是同进程 `Thread.sleep(10 ms)`，静机
     * p50 实测约 1.4 ms（2026-11-02 emulator-5584）。绝对值在 5 ms 以下时比值由调度
     * 量化噪声主导（1.4→2.9 即 >2，但设备完全空闲），因此低于该值不做比值判读；
     * 绝对判据 [ABSOLUTE_P95_LIMIT_MS] 仍然生效。
     */
    const val RATIO_OBSERVABLE_MIN_MS: Double = 5.0

    /** 无地板时的绝对下限：R5 建议的 `max(3 × floor, 30 ms)` 在无 floor 时的替代。 */
    const val ABSOLUTE_P95_LIMIT_MS: Double = 30.0

    /** 探针窗口内本进程 CPU 占比上限（单核语义；超过即视为本进程已被压满）。 */
    const val OWN_CPU_LIMIT_FRACTION: Double = 0.80

    data class Reading(
        val processors: Int,
        val jitterP50Ms: Double,
        val jitterP95Ms: Double,
        val jitterMaxMs: Double,
        val ownCpuMs: Double,
        val probeWindowMs: Long,
        val thermalStatus: Int,
        val lowMemory: Boolean,
        val reason: String?,
    ) {
        val gated: Boolean get() = reason == null

        fun fields(): String = buildString {
            append("processors=").append(processors)
            append(" jitter_p50_ms=").append(fmt(jitterP50Ms))
            append(" jitter_p95_ms=").append(fmt(jitterP95Ms))
            append(" jitter_max_ms=").append(fmt(jitterMaxMs))
            append(" own_cpu_ms=").append(fmt(ownCpuMs))
            append(" probe_window_ms=").append(probeWindowMs)
            append(" thermal_status=").append(thermalStatus)
            append(" low_memory=").append(lowMemory)
            if (reason != null) append(" reason=").append(reason)
        }

        private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
    }

    /**
     * 只读自证：睡眠 10 ms 的真实迟到量（p50/p95/max）、本进程 CPU 时间、thermal、lowMemory。
     * 任何一项读不到都返回带 [Reading.reason] 的结果，不抛异常、不静默当绿。
     */
    fun measure(samples: Int = 200, sleepMs: Long = 10L): Reading {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val floorMillis = InstrumentationRegistry.getArguments()
            .getString(ARG_JITTER_FLOOR_MS)?.toDoubleOrNull()

        val cpuBefore = readOwnCpuTicks()
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val lateness = ArrayList<Double>(samples)
        repeat(samples) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            Thread.sleep(sleepMs)
            lateness.add((SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0 - sleepMs)
        }
        val windowMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L
        val cpuAfter = readOwnCpuTicks()

        lateness.sort()
        val p50 = lateness[lateness.size / 2]
        val p95Index = (Math.ceil(lateness.size * 0.95).toInt() - 1)
            .coerceIn(0, lateness.size - 1)
        val p95 = lateness[p95Index]
        val max = lateness.last()
        val ownCpuMs = if (cpuBefore != null && cpuAfter != null && cpuAfter >= cpuBefore) {
            (cpuAfter - cpuBefore) * 1000.0 / USER_HZ
        } else {
            -1.0
        }
        val lowMemory = lowMemory(context)
        val thermal = thermalStatus(context)

        val selfRatio = p95 / maxOf(p50, 1.0)
        val reason = when {
            cpuBefore == null || cpuAfter == null -> "own_cpu_unreadable"
            windowMs <= 0L -> "probe_window_invalid"
            ownCpuMs > OWN_CPU_LIMIT_FRACTION * windowMs -> "own_cpu_busy"
            lowMemory -> "low_memory"
            floorMillis != null ->
                if (p95 > maxOf(3.0 * floorMillis, ABSOLUTE_P95_LIMIT_MS)) "jitter_p95_over_3x_floor" else null
            p95 > ABSOLUTE_P95_LIMIT_MS -> "jitter_p95_absolute"
            p95 > RATIO_OBSERVABLE_MIN_MS && selfRatio > SELF_RATIO_LIMIT -> "jitter_self_ratio"
            else -> null
        }

        return Reading(
            processors = Runtime.getRuntime().availableProcessors(),
            jitterP50Ms = p50,
            jitterP95Ms = p95,
            jitterMaxMs = max,
            ownCpuMs = ownCpuMs,
            probeWindowMs = windowMs,
            thermalStatus = thermal,
            lowMemory = lowMemory,
            reason = reason,
        )
    }

    /** `/proc/self/stat` 的 utime+stime（USER_HZ=100）。comm 可能含空格/括号，按最后一个 `)` 切。 */
    private fun readOwnCpuTicks(): Long? {
        val text = runCatching { File("/proc/self/stat").readText() }.getOrNull() ?: return null
        val close = text.lastIndexOf(')')
        if (close < 0 || close + 2 >= text.length) return null
        val fields = text.substring(close + 2).trim().split(Regex("\\s+"))
        // fields[0]=state(field 3) … utime=field 14 → index 11，stime=15 → index 12。
        val utime = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return utime + stime
    }

    private fun lowMemory(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.lowMemory
    }

    /** 仅记录：模拟器恒 0（R5 §2.6），不得当门。 */
    private fun thermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return -1
        return runCatching { power.currentThermalStatus }.getOrDefault(-1)
    }

    private const val USER_HZ: Double = 100.0
}

/**
 * 套件级门前置（每条用例前执行；结果缓存，避免 15 次重复探针）。
 *
 * 见 [LowLoadProbe] 的 fail-closed 语义。`smb.lowload.gate` 由
 * `tools/ci/run_isolation_pair.sh` 注入；手工 `am instrument` 不带该参数时整套件
 * 跳过——这正是批次 40 想要的"没有干净窗就不出绿"。
 */
internal object IsolationGate {

    const val ARG_GATE: String = "smb.lowload.gate"
    const val ARG_REASON: String = "smb.lowload.reason"

    @Volatile
    private var cached: LowLoadProbe.Reading? = null

    @Volatile
    private var cachedFailure: String? = null

    fun require() {
        IsolationRuntime.ensureDeclared()
        val failure = failure()
        if (failure != null) {
            Log.w(LowLoadProbe.TAG, "SMB_LOWLOAD_GATE: verdict=UNGATED reason=$failure")
        }
        Assume.assumeTrue("SMB_LOWLOAD_GATE UNGATED: $failure", failure == null)
    }

    private fun failure(): String? {
        cachedFailure?.let { return it }
        return synchronized(this) {
            cachedFailure ?: run {
                val arguments = InstrumentationRegistry.getArguments()
                val shellGate = arguments.getString(ARG_GATE)?.trim()?.uppercase(Locale.ROOT)
                val shellReason = arguments.getString(ARG_REASON).orEmpty()
                val failure = when {
                    shellGate.isNullOrEmpty() ->
                        "no_shell_side_verdict;run_tools/ci/run_isolation_pair.sh"
                    shellGate != "PASS" -> "shell_side_$shellGate:$shellReason"
                    else -> {
                        val reading = cached ?: LowLoadProbe.measure().also {
                            cached = it
                            val verdict = if (it.gated) "GATED" else "UNGATED"
                            Log.i(LowLoadProbe.TAG, "SMB_LOWLOAD_GATE: verdict=$verdict " + it.fields())
                        }
                        if (reading.gated) null else "instrumentation_self_probe:${reading.reason}"
                    }
                }
                cachedFailure = failure
                failure
            }
        }
    }
}
