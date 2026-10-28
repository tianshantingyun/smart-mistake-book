package com.tingyun.smartmistakebook.visualjs

import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureEntry
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 内容度量 JVM 判据（批次 27 T2；R2 §6 的 JVM 可达子集：判据 1–5）。
 *
 * 覆盖 T2 任务书的三、测试四项：纯几何批次不回归、text 条目测宽/断行、math 降级如实标注、
 * 度量结果确定性（含单位换算与端口预算回归）。
 *
 * **金样口径**：math 期望值由 [MathMetrics.of]/[MathBoxBuilder] 的共享 lowering 公式手工
 * 推导（charWidth=0.5×字号、charHeight=字号、lineSpacing=0.25×字号、fracLineThickness=
 * max(1, 0.0625×字号)），不是从实现回抄——金样钉住的是共享公式本身（契约 §5："不得继续
 * 增加第三套公式/坐标绘制逻辑"）。文本期望值来自确定性 fake backend（10 px/字符）。
 *
 * Paint 真实度量与隔离进程字体面（R2 §6-6/7/8、假设 A1）属设备轨，不在 JVM 判据内。
 */
class VisualContentMeasuringTest {

    /** 确定性 fake：advance = 字符数 × 10 px；行 metrics 固定 ascent=-20px / descent=5px。 */
    private class RecordingBackend : VisualTextMeasureBackend {
        val advanceCalls = mutableListOf<Pair<Int, Int>>()
        var lineMetricCalls = 0

        override fun advancePx(payload: String, start: Int, endExclusive: Int): Float {
            advanceCalls.add(start to endExclusive)
            return (endExclusive - start) * 10f
        }

        override fun lineMetricsPx(payload: String): VisualTextLineMetricsPx {
            lineMetricCalls++
            return VisualTextLineMetricsPx(ascentPx = -20f, descentPx = 5f)
        }
    }

    // density=2, fontScale=1, sizeSp=16 → 规范字号 32 px；du = px / 2。
    private val environment = VisualMeasureEnvironment(
        density = 2f,
        fontScale = 1f,
        typefaceId = VisualMeasureEnvironment.SYSTEM_DEFAULT_TYPEFACE_ID,
        localeTag = "zh-CN",
        sizeSp = VisualMeasureEnvironment.CANONICAL_SIZE_SP,
    )

    private fun measurer(backend: VisualTextMeasureBackend) =
        AndroidVisualContentMeasurer(environment, backend)

    private fun batch(vararg entries: VisualContentMeasureEntry) = VisualContentMeasureBatch(
        programId = "program-1",
        sceneRevision = 1,
        entries = entries.toList(),
    )

    private fun measure(
        backend: VisualTextMeasureBackend,
        vararg entries: VisualContentMeasureEntry,
    ): VisualContentMeasureResult = runBlocking { measurer(backend).measure(batch(*entries)) }

    // ------------------------------------------------ 纯几何批次不回归（R2 §6-5/9 的服务侧面）

    @Test
    fun `an empty batch returns an empty result and never touches the measure backend`() {
        val backend = RecordingBackend()
        val result = measure(backend)
        assertTrue(result.entries.isEmpty())
        assertEquals(0, backend.advanceCalls.size)
        assertEquals(0, backend.lineMetricCalls)
    }

    @Test
    fun `port batch budgets stay enforced by the port types`() {
        // 129 条（上限 128）：条数越界，构造即拒。
        val overCount = List(VisualExecutionBudget.MAX_CONTENT_REQUESTS + 1) {
            VisualContentMeasureEntry(VisualContentKind.TEXT, "x")
        }
        try {
            VisualContentMeasureBatch("program-1", 1, overCount)
            fail("a 129-entry batch must be rejected by the port type")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("128"))
        }
        // 总字符 6001（上限 6000）：2 条 × 3001+3000，条数合法、总量越界。
        val overChars = listOf(
            VisualContentMeasureEntry(VisualContentKind.TEXT, "x".repeat(3001)),
            VisualContentMeasureEntry(VisualContentKind.TEXT, "x".repeat(3000)),
        )
        try {
            VisualContentMeasureBatch("program-1", 1, overChars)
            fail("a 6001-char batch must be rejected by the port type")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("6000"))
        }
    }

    // ------------------------------------------------ text 测宽/断行（R2 §4.2 内蕴口径）

    @Test
    fun `a text entry is measured intrinsically over explicit line breaks`() {
        val backend = RecordingBackend()
        val result = measure(backend, VisualContentMeasureEntry(VisualContentKind.TEXT, "abc\nab\nabcd"))
        assertEquals(1, result.entries.size)
        val measured = result.entries[0]
        // 宽 = 最宽段（"abcd" 40px）÷ density 2 = 20 du。
        assertEquals(20f, measured.widthDu, 1e-4f)
        // 高 = 段数 3 × 行高 25px ÷ 2 = 37.5 du。
        assertEquals(37.5f, measured.heightDu, 1e-4f)
        // baseline = 首行自顶偏移 -ascent = 20px ÷ 2 = 10 du。
        assertEquals(10f, measured.baselineDu, 1e-4f)
        assertEquals(VisualMeasureEnvironment.CANONICAL_SIZE_SP, measured.fontSizeSp, 1e-4f)
        // contentId = "c{index}-{digest 前 8}"（R2 §4.3-3）。
        assertEquals("c0-${measured.digest.take(8)}", measured.contentId)
    }

    @Test
    fun `the internal layout artifact carries the explicit line ranges and per-line widths`() {
        val backend = RecordingBackend()
        val layout = measureTextIntrinsic(
            "abc\nab\nabcd",
            environment,
            backend,
        )
        val lines = requireNotNull(layout.lines)
        assertEquals(3, lines.size)
        assertEquals(0, lines[0].start)
        assertEquals(3, lines[0].endExclusive)
        assertEquals(15f, lines[0].widthDu, 1e-4f)
        assertEquals(4, lines[1].start)
        assertEquals(6, lines[1].endExclusive)
        assertEquals(10f, lines[1].widthDu, 1e-4f)
        assertEquals(7, lines[2].start)
        assertEquals(11, lines[2].endExclusive)
        assertEquals(20f, lines[2].widthDu, 1e-4f)
        // 行级断言之外，advance 只按归一后的段区间调用。
        assertEquals(listOf(0 to 3, 4 to 6, 7 to 11), backend.advanceCalls)
    }

    @Test
    fun `crlf endings are normalized out of the measured segments`() {
        val backend = RecordingBackend()
        val layout = measureTextIntrinsic("a\r\nb", environment, backend)
        val lines = requireNotNull(layout.lines)
        assertEquals(2, lines.size)
        assertEquals(0, lines[0].start)
        assertEquals(1, lines[0].endExclusive)
        // "a\r\nb"：a=0, \r=1, \n=2, b=3——\r 从段 1 剥离（[0,1)），段 2 是 b 本位 [3,4)；
        // [2,3) 是 '\n' 本身，归一后的段绝不覆盖分隔符。
        assertEquals(3, lines[1].start)
        assertEquals(4, lines[1].endExclusive)
        // \r 不计入 advance（调用的区间已归一），也不产生额外行。
        assertEquals(listOf(0 to 1, 3 to 4), backend.advanceCalls)
        assertEquals(5f, layout.widthDu, 1e-4f)
        assertEquals(25f, layout.heightDu, 1e-4f)
    }

    @Test
    fun `a trailing line break yields a deterministic empty final segment`() {
        val backend = RecordingBackend()
        val layout = measureTextIntrinsic("abc\n", environment, backend)
        val lines = requireNotNull(layout.lines)
        assertEquals(2, lines.size)
        assertEquals(4, lines[1].start)
        assertEquals(4, lines[1].endExclusive)
        assertEquals(15f, layout.widthDu, 1e-4f)
        assertEquals(25f, layout.heightDu, 1e-4f)
    }

    // ------------------------------------------------ math 金样 + 降级如实标注（R2 §6-1/2）

    @Test
    fun `a supported fraction is measured with the shared core-model box lowering`() {
        // 手工推导（MathMetrics.of(32)：charWidth=16, charHeight=32, lineSpacing=8,
        // fracLineThickness=max(1, 2)=2）：Fraction(1,2) 宽 = max(16,16)+2×16 = 48px = 24 du；
        // 高 = 32+32+8+2 = 74px = 37 du；baseline = 32+4+1 = 37px = 18.5 du。
        val layout = measureMathBox("\\frac{1}{2}", environment)
        requireNotNull(layout)
        assertEquals(VisualContentKind.MATH, layout.kind)
        assertEquals(24f, layout.widthDu, 1e-4f)
        assertEquals(37f, layout.heightDu, 1e-4f)
        assertEquals(18.5f, layout.baselineDu, 1e-4f)
        assertNull(layout.lines)
        assertTrue(!layout.degraded)
    }

    @Test
    fun `a supported atom is measured with the shared core-model box lowering`() {
        // Atom：宽 = 1×16px = 8 du；高 = 32px = 16 du；baseline = 0.4×32 = 12.8px = 6.4 du。
        val layout = measureMathBox("x", environment)
        requireNotNull(layout)
        assertEquals(8f, layout.widthDu, 1e-4f)
        assertEquals(16f, layout.heightDu, 1e-4f)
        assertEquals(6.4f, layout.baselineDu, 1e-4f)
        assertTrue(!layout.degraded)
    }

    @Test
    fun `an over-budget math payload degrades to the text path and the digest records it`() {
        val backend = RecordingBackend()
        val overBudget = "x".repeat(2001) // > MathBudget.MAX_INPUT_LENGTH(2000)，批次预算内
        val result = measure(backend, VisualContentMeasureEntry(VisualContentKind.MATH, overBudget))
        assertEquals(1, result.entries.size)
        val measured = result.entries[0]
        // 降级走 text 路径度量原文：1 段 × 2001 字符 × 10px ÷ 2 = 10005 du；高 25px ÷ 2 = 12.5 du。
        assertEquals(10005f, measured.widthDu, 1e-4f)
        assertEquals(12.5f, measured.heightDu, 1e-4f)
        assertEquals(10f, measured.baselineDu, 1e-4f)

        val layout = measureContentEntry(
            VisualContentMeasureEntry(VisualContentKind.MATH, overBudget),
            environment,
            backend,
        )
        assertEquals(VisualContentKind.MATH, layout.kind)
        assertTrue("a degraded math entry must be labeled degraded", layout.degraded)
        // 降级 artifact 可复现：携带降级文本的段列表。
        assertEquals(1, requireNotNull(layout.lines).size)

        // 摘要含降级语义：同 payload 不同降级态/kind 摘要不同（R2 §6-2）。
        val degradedDigest = contentDigest(VisualContentKind.MATH, overBudget, environment, true)
        val plainMathDigest = contentDigest(VisualContentKind.MATH, overBudget, environment, false)
        val textDigest = contentDigest(VisualContentKind.TEXT, overBudget, environment, false)
        assertEquals(degradedDigest, measured.digest)
        assertNotEquals(plainMathDigest, degradedDigest)
        assertNotEquals(textDigest, degradedDigest)
    }

    // ------------------------------------------------ 确定性（R2 §6-3）与单位换算（R2 §6-4）

    @Test
    fun `the same batch measured twice yields identical content ids and digests`() {
        val first = measure(
            RecordingBackend(),
            VisualContentMeasureEntry(VisualContentKind.TEXT, "abc\nab"),
            VisualContentMeasureEntry(VisualContentKind.MATH, "\\frac{1}{2}"),
        )
        val second = measure(
            RecordingBackend(),
            VisualContentMeasureEntry(VisualContentKind.TEXT, "abc\nab"),
            VisualContentMeasureEntry(VisualContentKind.MATH, "\\frac{1}{2}"),
        )
        assertEquals(first.entries.map { it.contentId }, second.entries.map { it.contentId })
        assertEquals(first.entries.map { it.digest }, second.entries.map { it.digest })
    }

    @Test
    fun `a changed layout environment changes the digest and the content id`() {
        val scaled = environment.copy(fontScale = 1.3f)
        val base = AndroidVisualContentMeasurer(environment, RecordingBackend())
        val other = AndroidVisualContentMeasurer(scaled, RecordingBackend())
        val entry = VisualContentMeasureEntry(VisualContentKind.TEXT, "abc")
        val baseResult = runBlocking { base.measure(batch(entry)) }
        val otherResult = runBlocking { other.measure(batch(entry)) }
        assertNotEquals(baseResult.entries[0].digest, otherResult.entries[0].digest)
        assertNotEquals(baseResult.entries[0].contentId, otherResult.entries[0].contentId)
    }

    @Test
    fun `the environment fingerprint pins the replay paint flags`() {
        // KD-78 ④（批次 29 T5）：指纹字面钉死 paintFlags=0——该 0 与回放侧
        // `REPLAY_TEXT_PAINT_FLAGS`（core:data visual 纯层，JVM 测钉同值）是同一常量语义。
        // 任一侧单边改 flags 而未改另一侧/指纹，本断言或 core 侧常量断言必红。
        assertEquals(
            "system-default|sizeSp=16.0|density=2.0|fontScale=1.0|locale=zh-CN|paintFlags=0",
            environment.fingerprint(),
        )
        assertEquals(
            environment.fingerprint(),
            environment.copy().fingerprint(),
        )
    }

    @Test
    fun `changing the paint flags changes the digest`() {
        // flags 必须参与摘要：否则换 flag 后旧快照仍被当命中，度量与回放静默失配。
        val flagged = environment.copy(paintFlags = 1)
        val entry = VisualContentMeasureEntry(VisualContentKind.TEXT, "abc")
        val base = runBlocking { AndroidVisualContentMeasurer(environment, RecordingBackend()).measure(batch(entry)) }
        val other = runBlocking { AndroidVisualContentMeasurer(flagged, RecordingBackend()).measure(batch(entry)) }
        assertNotEquals(base.entries[0].digest, other.entries[0].digest)
        assertNotEquals(base.entries[0].contentId, other.entries[0].contentId)
    }

    @Test
    fun `the same payload at a different batch position gets a different content id`() {
        val entry = VisualContentMeasureEntry(VisualContentKind.TEXT, "abc")
        val result = measure(RecordingBackend(), entry, entry)
        assertNotEquals(result.entries[0].contentId, result.entries[1].contentId)
        assertEquals("c1-${result.entries[1].digest.take(8)}", result.entries[1].contentId)
    }

    @Test
    fun `sp to du conversion follows fontScale`() {
        // px = sp × density × fontScale：16 × 2 × 1 = 32。
        assertEquals(32f, environment.fontSizePx(), 1e-4f)
        // du per sp = fontScale：math 盒尺寸对 fontScale 线性（density 在 du 换算中相消）。
        val scaled = environment.copy(fontScale = 1.3f)
        val base = measureMathBox("\\frac{1}{2}", environment)!!
        val scaledBox = measureMathBox("\\frac{1}{2}", scaled)!!
        assertEquals(1.3f, scaledBox.widthDu / base.widthDu, 1e-3f)
        assertEquals(1.3f, scaledBox.heightDu / base.heightDu, 1e-3f)
        // 文本路径同口径：du = px advance ÷ density，换算不再乘 fontScale（fontScale 只在
        // 度量 Paint 的 textSize 里生效一次；fake 的 advance 是固定 px，故 du 不随 fontScale 变）。
        val scaledText = measureTextIntrinsic("abcd", scaled, RecordingBackend())
        assertEquals(20f, scaledText.widthDu, 1e-4f)
    }

    @Test
    fun `an invalid environment fails closed at construction`() {
        // 环境读数不可信时构造即拒（A1 fail-closed），绝不带病度量。
        assertThrowsIllegalArgument { VisualMeasureEnvironment(0f, 1f, "t", "zh-CN", 16f) }
        assertThrowsIllegalArgument { VisualMeasureEnvironment(2f, 0f, "t", "zh-CN", 16f) }
        assertThrowsIllegalArgument { VisualMeasureEnvironment(2f, 1f, "t", "zh-CN", 8f) }
        assertThrowsIllegalArgument { VisualMeasureEnvironment(2f, 1f, "t", "zh-CN", 64f) }
        assertThrowsIllegalArgument { VisualMeasureEnvironment(2f, 1f, " ", "zh-CN", 16f) }
        assertThrowsIllegalArgument { VisualMeasureEnvironment(2f, 1f, "t", "", 16f) }
        // KD-78 ④：负数 flags 不是合法 Paint flags 口径，构造即拒。
        assertThrowsIllegalArgument { VisualMeasureEnvironment(2f, 1f, "t", "zh-CN", 16f, -1) }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // 预期路径。
        }
    }
}
