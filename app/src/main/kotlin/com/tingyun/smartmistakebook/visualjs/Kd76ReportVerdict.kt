package com.tingyun.smartmistakebook.visualjs

/**
 * KD-76 报表层判词（批次 41 / WS3 item 20；主会话裁定 A）。
 *
 * **它消灭的具体失败**：报表层在 G1 红（任一轮 FAIL/INVALID）或样本不足（n < 20）时
 * 仍然渲染出 `G2 ... => PASS` 判词，下游证据再把一个未达预注册的样本当成"门通过"
 * 引用（批次 39 的 n=9/1319 ms 与批次 40 复核发现 #6 都是这个形状）。修复方式不是
 * 在 androidTest 里再写一遍 if：本对象是**单一事实源**，androidTest 的
 * `buildReport` 只调用 [decide] / [Decision.verdictLine] 渲染，不再保留第二套判定。
 *
 * **门与样本量**：门 = `READY_TIMEOUT_MILLIS / 2` = 5 000 ms（派生，不提供放宽参数）；
 * 预注册样本 = 20。`sampleCount < 20`、`g1Red`、或没有 p95 时一律
 * [Verdict.NOT_EVALUABLE]，其 [Decision.verdictLine] **不包含 PASS 字样**。
 *
 * 纯 Kotlin：零 Android import、零状态、无 I/O；JVM 单测见
 * `app/src/test/.../visualjs/Kd76ReportVerdictTest.kt`。调用方：
 * `app/src/androidTest/.../visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt`
 * 的 `buildReport`；`tools/ci/summarize_kd76.py` 是同口径的脚本实现（不共享代码，
 * 但常量/理由码在两侧单测里各自钉住）。
 */
object Kd76ReportVerdict {

    /** 预注册样本量（R2 §2.3 #2）：n=20 时 nearest-rank p95 容忍一个最大值。 */
    const val REQUIRED_SAMPLES = 20

    /** `VisualJsClientEngine.READY_TIMEOUT_MILLIS` 的镜像；androidTest 反射钉住 10_000。 */
    const val READY_TIMEOUT_MILLIS = 10_000L

    /** 主口径门 `M.p95 ≤ READY_TIMEOUT / 2`（R2 §3.2）；派生值，不追加容差。 */
    const val GATE_MILLIS = READY_TIMEOUT_MILLIS / 2

    const val REASON_G1_RED = "g1_red"
    const val REASON_SAMPLE_SHORT = "sample_short"
    const val REASON_NO_P95 = "no_p95"
    const val REASON_P95_OVER_GATE = "p95_over_gate"

    enum class Verdict { PASS, FAIL, NOT_EVALUABLE }

    /**
     * 报表判词 + 机器可读理由码。
     *
     * - [Verdict.PASS]：n ≥ 20、G1 绿、p95 ≤ 门；
     * - [Verdict.FAIL]：n ≥ 20、G1 绿、p95 > 门；
     * - [Verdict.NOT_EVALUABLE]：样本不足 / G1 红 / 无 p95——此时 [verdictLine] 不得
     *   出现 PASS（下游不得引用判词）。
     */
    data class Decision(
        val verdict: Verdict,
        val reasons: List<String>,
        val sampleCount: Int,
        val gateMillis: Long,
        val p95Millis: Long?,
    ) {
        /**
         * 报表行。`NOT_EVALUABLE` 分支只拼理由码，**不**包含 "PASS" 字样；JVM 用例
         * 直接断言这一点（`Kd76ReportVerdictTest`）。
         */
        fun verdictLine(): String = when (verdict) {
            Verdict.PASS ->
                "G2: PASS (M.p95=$p95Millis ms <= $gateMillis ms)"
            Verdict.FAIL ->
                "G2: FAIL (M.p95=$p95Millis ms > $gateMillis ms)"
            Verdict.NOT_EVALUABLE ->
                "G2: NOT EVALUABLE — " + reasons.joinToString(separator = ", ")
        }
    }

    /**
     * 由原始值判定；不读环境、不读时间、不做重试。
     *
     * @param sampleCount 成功轮数（失败轮是删失样本，不计入分子；n<20 即样本不足）。
     * @param g1Red 是否出现任一轮 FAIL/INVALID（G1 硬门；G1 红时 G2 绿也不算过）。
     * @param p95Millis 主口径 `M = S-bind + S-warmup` 的 nearest-rank p95；无样本时 null。
     * @param requiredSamples 预注册样本量（默认 [REQUIRED_SAMPLES]；只为测试显式传入）。
     * @param gateMillis 门（默认 [GATE_MILLIS]；生产调用不得传更宽的值）。
     */
    fun decide(
        sampleCount: Int,
        g1Red: Boolean,
        p95Millis: Long?,
        requiredSamples: Int = REQUIRED_SAMPLES,
        gateMillis: Long = GATE_MILLIS,
    ): Decision {
        val reasons = mutableListOf<String>()
        if (g1Red) reasons += REASON_G1_RED
        if (sampleCount < requiredSamples) reasons += REASON_SAMPLE_SHORT
        if (p95Millis == null) reasons += REASON_NO_P95
        if (reasons.isNotEmpty()) {
            return Decision(
                verdict = Verdict.NOT_EVALUABLE,
                reasons = reasons,
                sampleCount = sampleCount,
                gateMillis = gateMillis,
                p95Millis = p95Millis,
            )
        }
        val p95 = p95Millis!!
        return if (p95 <= gateMillis) {
            Decision(Verdict.PASS, emptyList(), sampleCount, gateMillis, p95)
        } else {
            Decision(
                verdict = Verdict.FAIL,
                reasons = listOf(REASON_P95_OVER_GATE),
                sampleCount = sampleCount,
                gateMillis = gateMillis,
                p95Millis = p95,
            )
        }
    }
}
