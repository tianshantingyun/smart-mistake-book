package com.tingyun.smartmistakebook.visualjs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-76 报表层判词的 JVM 单测（批次 41 / WS3 item 20；主会话裁定 A）。
 *
 * 它钉住的具体失败：G1 红（任一轮 FAIL/INVALID）或样本不足（n<20）时，报表层仍渲染
 * `G2 ... => PASS`，下游证据把未达预注册的样本当"门通过"引用（批次 39 的 n=9/1319 ms；
 * 批次 40 复核发现 #6）。这些断言直接检查 [Kd76ReportVerdict.Decision.verdictLine] 的
 * 输出，而不是只检查判词非空：NOT_EVALUABLE 分支里出现 PASS 字样即红。
 *
 * 设备用例（androidTest）只调用同一对象；本文件是 JVM 覆盖，不替代设备执行。
 */
class Kd76ReportVerdictTest {

    @Test
    fun `constants mirror the pre-registered gate`() {
        assertEquals(20, Kd76ReportVerdict.REQUIRED_SAMPLES)
        assertEquals(10_000L, Kd76ReportVerdict.READY_TIMEOUT_MILLIS)
        assertEquals(5_000L, Kd76ReportVerdict.GATE_MILLIS)
        assertEquals(
            "the gate must stay READY_TIMEOUT/2 (never widened)",
            Kd76ReportVerdict.READY_TIMEOUT_MILLIS / 2,
            Kd76ReportVerdict.GATE_MILLIS,
        )
    }

    @Test
    fun `thresholds cannot be overridden through the api`() {
        // F11：旧签名带 requiredSamples/gateMillis 默认参数，任何调用方都能放宽预注册。
        // 现在 decide 只有 3 个入参，阈值只能来自本对象的常量。
        val decideMethods = Kd76ReportVerdict::class.java.declaredMethods
            .filter { it.name == "decide" }
        assertEquals("decide must have exactly one overload", 1, decideMethods.size)
        assertEquals(
            "decide must not accept threshold overrides",
            3,
            decideMethods.single().parameterCount,
        )
        val decision = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES,
            g1Red = false,
            p95Millis = Kd76ReportVerdict.GATE_MILLIS,
        )
        assertEquals(Kd76ReportVerdict.GATE_MILLIS, decision.gateMillis)
        assertEquals(Kd76ReportVerdict.Verdict.PASS, decision.verdict)
    }

    @Test
    fun `g1 red is not evaluable and never renders pass`() {
        val decision = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES,
            g1Red = true,
            p95Millis = 1_000L,
        )

        assertEquals(Kd76ReportVerdict.Verdict.NOT_EVALUABLE, decision.verdict)
        assertEquals(listOf(Kd76ReportVerdict.REASON_G1_RED), decision.reasons)
        assertTrue(decision.verdictLine().contains("NOT EVALUABLE"))
        assertFalse(
            "a G1-red run must never render a PASS verdict: ${decision.verdictLine()}",
            decision.verdictLine().contains("PASS"),
        )
    }

    @Test
    fun `short sample is not evaluable and never renders pass`() {
        val decision = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES - 1,
            g1Red = false,
            p95Millis = 1_000L,
        )

        assertEquals(Kd76ReportVerdict.Verdict.NOT_EVALUABLE, decision.verdict)
        assertEquals(listOf(Kd76ReportVerdict.REASON_SAMPLE_SHORT), decision.reasons)
        assertFalse(
            "an n<20 run must never render a PASS verdict: ${decision.verdictLine()}",
            decision.verdictLine().contains("PASS"),
        )
    }

    @Test
    fun `twenty samples at or under the gate pass`() {
        val atGate = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES,
            g1Red = false,
            p95Millis = Kd76ReportVerdict.GATE_MILLIS,
        )
        val underGate = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES,
            g1Red = false,
            p95Millis = Kd76ReportVerdict.GATE_MILLIS - 1,
        )

        assertEquals(Kd76ReportVerdict.Verdict.PASS, atGate.verdict)
        assertTrue("PASS must carry no reason codes", atGate.reasons.isEmpty())
        assertTrue(atGate.verdictLine().startsWith("G2: PASS"))
        assertEquals(Kd76ReportVerdict.Verdict.PASS, underGate.verdict)
        assertTrue(underGate.verdictLine().startsWith("G2: PASS"))
    }

    @Test
    fun `p95 above the gate is fail not pass`() {
        val decision = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES,
            g1Red = false,
            p95Millis = Kd76ReportVerdict.GATE_MILLIS + 1,
        )

        assertEquals(Kd76ReportVerdict.Verdict.FAIL, decision.verdict)
        assertEquals(listOf(Kd76ReportVerdict.REASON_P95_OVER_GATE), decision.reasons)
        assertTrue(decision.verdictLine().startsWith("G2: FAIL"))
        assertFalse(
            "an over-gate run must not also render PASS: ${decision.verdictLine()}",
            decision.verdictLine().contains("PASS"),
        )
    }

    @Test
    fun `missing p95 is not evaluable`() {
        val decision = Kd76ReportVerdict.decide(
            sampleCount = Kd76ReportVerdict.REQUIRED_SAMPLES,
            g1Red = false,
            p95Millis = null,
        )

        assertEquals(Kd76ReportVerdict.Verdict.NOT_EVALUABLE, decision.verdict)
        assertEquals(listOf(Kd76ReportVerdict.REASON_NO_P95), decision.reasons)
        assertFalse(decision.verdictLine().contains("PASS"))
    }

    @Test
    fun `all not-evaluable reasons are reported together`() {
        val decision = Kd76ReportVerdict.decide(
            sampleCount = 0,
            g1Red = true,
            p95Millis = null,
        )

        assertEquals(Kd76ReportVerdict.Verdict.NOT_EVALUABLE, decision.verdict)
        assertEquals(
            listOf(
                Kd76ReportVerdict.REASON_G1_RED,
                Kd76ReportVerdict.REASON_SAMPLE_SHORT,
                Kd76ReportVerdict.REASON_NO_P95,
            ),
            decision.reasons,
        )
        assertEquals(0, decision.sampleCount)
        assertFalse(decision.verdictLine().contains("PASS"))
    }
}
