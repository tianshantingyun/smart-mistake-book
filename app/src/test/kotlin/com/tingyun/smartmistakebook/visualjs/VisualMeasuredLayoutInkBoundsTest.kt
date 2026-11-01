package com.tingyun.smartmistakebook.visualjs

import com.tingyun.smartmistakebook.core.data.visual.VisualMeasuredLayoutArtifact
import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 38 M-01 的 JVM 判据：墨迹边界（`inkBounds`）与完整布局 artifact 折算
 * （[toArtifact] → `VisualMeasuredLayoutArtifact`）。R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §3 的坐标约定逐条落地：
 *
 * - §3.2-2 折算式：段 i 的基线自盒顶 y = `-ascent + i × 行高`，墨迹 top/bottom 在其上
 *   叠加 `getTextBounds` 的基线原点矩形；left/right 自盒左缘原样；整体取**联合**；
 * - §3.2-3：math 墨迹第一版 = 盒自身；
 * - §3.2-4：**无包含不变式**——墨迹可越出布局盒（本文件用负坐标/超宽墨迹的 fake 钉住
 *   "度量核心如实透传、不做钳制"）；
 * - §3.2-5：inkBounds 不进 digest（digest 断言在既有 `VisualContentMeasuringTest`）。
 *
 * 金样口径：确定性 fake backend（advance = 10 px/字符；ink = [0, 段宽-1] × [基线上 4 px,
 * 基线下 2 px]；行 metrics ascent=-20/descent=5，行高 25 px），density=2、fontScale=1、
 * sizeSp=16（与 `VisualContentMeasuringTest` 同一套环境）。
 *
 * 先红后绿：墨迹/折算在实现前落盘即红（`toArtifact`/`textInkBoundsPx` 缺失时编译不过）；
 * 本环境不运行 Gradle，执行归门禁串行双门（结果登记 UNVERIFIED）。
 */
class VisualMeasuredLayoutInkBoundsTest {

    // 与 VisualContentMeasuringTest 相同口径的确定性 fake（独立声明：两个测试文件各自钉住
    // 自己的口径，避免跨文件共享夹具把无关改动卷进来）。
    private class InkRecordingBackend : VisualTextMeasureBackend {
        override fun advancePx(payload: String, start: Int, endExclusive: Int): Float =
            (endExclusive - start) * 10f

        override fun lineMetricsPx(payload: String): VisualTextLineMetricsPx =
            VisualTextLineMetricsPx(ascentPx = -20f, descentPx = 5f)

        override fun textInkBoundsPx(
            payload: String,
            start: Int,
            endExclusive: Int,
        ): VisualTextInkBoundsPx {
            val width = (endExclusive - start) * 10f
            return VisualTextInkBoundsPx(
                left = 0f,
                top = -4f,
                right = (width - 1f).coerceAtLeast(0f),
                bottom = 2f,
            )
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

    // ------------------------------------------------ text 墨迹（R2 §3.2-2 折算与联合）

    @Test
    fun `a single line text ink bounds fold into box-top coordinates`() {
        val layout = measureTextIntrinsic("abc", environment, InkRecordingBackend())
        val ink = layout.inkBounds
        // 基线自顶 y = 20px；ink top = 20 + (-4) = 16px → 8 du；bottom = 20 + 2 = 22px → 11 du；
        // left = 0；right = 3×10 - 1 = 29px → 14.5 du。
        assertEquals(0f, ink.left, 1e-4f)
        assertEquals(8f, ink.top, 1e-4f)
        assertEquals(14.5f, ink.right, 1e-4f)
        assertEquals(11f, ink.bottom, 1e-4f)
    }

    @Test
    fun `multi-line ink is the union over per-segment rects`() {
        val layout = measureTextIntrinsic("abc\nab", environment, InkRecordingBackend())
        val ink = layout.inkBounds
        // 行 0 基线 20px：[0,29]×[16,22]；行 1 基线 45px（-ascent + 1×25）：[0,19]×[41,47]。
        // 联合 = left 0、right 29px→14.5 du、top 16px→8 du、bottom 47px→23.5 du。
        assertEquals(0f, ink.left, 1e-4f)
        assertEquals(8f, ink.top, 1e-4f)
        assertEquals(14.5f, ink.right, 1e-4f)
        assertEquals(23.5f, ink.bottom, 1e-4f)
    }

    @Test
    fun `an empty final segment contributes no ink`() {
        val layout = measureTextIntrinsic("abc\n", environment, InkRecordingBackend())
        // 段 2 为空（尾 \n）：不参与联合——墨迹 = 行 0 的矩形，bottom 不被空行基线抬走。
        assertEquals(11f, layout.inkBounds.bottom, 1e-4f)
        assertEquals(14.5f, layout.inkBounds.right, 1e-4f)
    }

    @Test
    fun `a payload of only line breaks measures zero ink`() {
        val layout = measureTextIntrinsic("\n", environment, InkRecordingBackend())
        assertEquals(0f, layout.inkBounds.left, 1e-4f)
        assertEquals(0f, layout.inkBounds.top, 1e-4f)
        assertEquals(0f, layout.inkBounds.right, 1e-4f)
        assertEquals(0f, layout.inkBounds.bottom, 1e-4f)
        // 高仍按段数计（2 段 × 25px ÷ 2 = 25 du）——墨迹与布局盒独立。
        assertEquals(25f, layout.heightDu, 1e-4f)
    }

    @Test
    fun `ink bounds are passed through unclamped, containment is not implied`() {
        // 无包含不变式（R2 §3.2-4）：fake 返回越出布局盒的墨迹（右缘超 advance、top 负到
        // 盒顶之上），度量核心必须如实透传、不钳制——消费面据此才能看见真实墨迹。
        val overhanging = object : VisualTextMeasureBackend by InkRecordingBackend() {
            override fun textInkBoundsPx(
                payload: String,
                start: Int,
                endExclusive: Int,
            ): VisualTextInkBoundsPx = VisualTextInkBoundsPx(
                left = -3f, // 斜体悬挂：越出盒左缘（负坐标）。
                top = -30f, // fallback 大字形：越出行 metrics 顶（盒顶之上）。
                right = 999f, // 超出布局盒宽。
                bottom = 40f, // 超出布局盒高。
            )
        }
        val layout = measureTextIntrinsic("abc", environment, overhanging)
        val ink = layout.inkBounds
        // left = -3px ÷ density 2 = -1.5 du——与同用例 right=999px→499.5 du、bottom→30 du 同一
        // 折算（inkBounds 全矩形按 du 口径，R2 §3 表）；负坐标如实透传、不钳制。
        assertEquals(-1.5f, ink.left, 1e-4f)
        // top = 20px + (-30) = -10px → -5 du：负坐标如实透传。
        assertEquals(-5f, ink.top, 1e-4f)
        assertEquals(499.5f, ink.right, 1e-4f)
        // bottom = (20px + 40) / 2 = 30 du：超出布局盒高（12.5 du）如实透传。
        assertEquals(30f, ink.bottom, 1e-4f)
        // 布局盒本身不变（宽/高仍是 advance/行高口径）——墨迹与布局盒无包含关系。
        assertEquals(15f, layout.widthDu, 1e-4f)
        assertEquals(12.5f, layout.heightDu, 1e-4f)
    }

    // ------------------------------------------------ math 墨迹（R2 §3.2-3：第一版 = 盒自身）

    @Test
    fun `a math box ink bounds equal the box itself`() {
        val layout = measureMathBox("\\frac{1}{2}", environment)
        val ink = requireNotNull(layout).inkBounds
        // 盒（fontSizePx=32）：48×74 px → 24×37 du；墨迹第一版 = 盒自身。
        assertEquals(0f, ink.left, 1e-4f)
        assertEquals(0f, ink.top, 1e-4f)
        assertEquals(24f, ink.right, 1e-4f)
        assertEquals(37f, ink.bottom, 1e-4f)
    }

    // ------------------------------------------------ 完整布局 artifact 折算（M-01 生产路径）

    @Test
    fun `toArtifact carries every field and keeps the ink bounds`() {
        val layout = measureTextIntrinsic("abc\nab", environment, InkRecordingBackend())
        val artifact = layout.toArtifact(
            fontSizeSp = environment.sizeSp,
            digest = "digest-1",
        )
        assertEquals(VisualContentKind.TEXT, artifact.kind)
        assertEquals(layout.widthDu, artifact.widthDu, 1e-4f)
        assertEquals(layout.heightDu, artifact.heightDu, 1e-4f)
        assertEquals(layout.baselineDu, artifact.baselineDu, 1e-4f)
        assertEquals(16f, artifact.fontSizeSp, 1e-4f)
        assertEquals("digest-1", artifact.digest)
        assertEquals(layout.degraded, artifact.degraded)
        assertEquals(layout.inkBounds, artifact.inkBounds)
        val lines = requireNotNull(artifact.lines)
        assertEquals(2, lines.size)
        assertEquals(0, lines[0].start)
        assertEquals(3, lines[0].endExclusive)
        assertEquals(15f, lines[0].widthDu, 1e-4f)
        assertEquals(4, lines[1].start)
        assertEquals(6, lines[1].endExclusive)
    }

    @Test
    fun `a degraded math artifact keeps math kind, the degraded flag and text ink`() {
        val backend = InkRecordingBackend()
        val entry = VisualContentMeasureEntry(VisualContentKind.MATH, "x".repeat(2001))
        val layout = measureContentEntry(entry, environment, backend)
        assertTrue(layout.degraded)
        assertEquals(VisualContentKind.MATH, layout.kind)
        // 降级墨迹 = 降级文本的墨迹：2001 字符 × 10px - 1 = 20009px → 10004.5 du。
        assertEquals(10004.5f, layout.inkBounds.right, 1e-4f)
        // 折算成 artifact 后仍成立（降级 math 必带段列表——可复现性不变式）。
        val artifact = layout.toArtifact(fontSizeSp = 16f, digest = "digest-2")
        assertTrue(artifact.degraded)
        assertEquals(1, requireNotNull(artifact.lines).size)
    }

    @Test
    fun `measure runs the artifact production path with its invariants`() {
        // measure() 每条目构造 artifact（构造即校验 init 不变式）：任何字段漂移/不变式
        // 违反都会让 measure 抛异常而非产出坏 artifact——本用例是生产路径的冒烟面。
        val measurer = AndroidVisualContentMeasurer(environment, InkRecordingBackend())
        val result = kotlinx.coroutines.runBlocking {
            measurer.measure(
                com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch(
                    programId = "program-1",
                    sceneRevision = 1,
                    entries = listOf(
                        VisualContentMeasureEntry(VisualContentKind.TEXT, "abc"),
                        VisualContentMeasureEntry(VisualContentKind.MATH, "\\frac{1}{2}"),
                    ),
                ),
            )
        }
        assertEquals(2, result.entries.size)
        // 端口投影与 artifact 同源（M-01 单一事实来源）：端口值 = artifact 字段。
        val textPort = result.entries[0]
        assertEquals("c0-${textPort.digest.take(8)}", textPort.contentId)
        assertEquals(VisualMeasureEnvironment.CANONICAL_SIZE_SP, textPort.fontSizeSp, 1e-4f)
        assertEquals(15f, textPort.widthDu, 1e-4f)
    }
}
