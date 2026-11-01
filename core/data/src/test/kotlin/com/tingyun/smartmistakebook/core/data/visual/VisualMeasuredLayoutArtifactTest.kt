package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `VisualMeasuredLayoutArtifact` 的类型不变量判据（批次 38 M-01；R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §3.1 字段表 + §3.2 坐标约定）。
 *
 * 类型 require 是消费不变式的**执行点**：app 度量器每次度量都构造本类型
 * （构造即校验），坏 artifact 在生产点即抛（引擎折有界拒绝），不流入消费方。
 * 墨迹无包含不变式（§3.2-4）在本文件钉两半：类型**允许**越界/负坐标墨迹（构造不拒），
 * 只拒非有限/乱序。
 *
 * 先红后绿：类型在实现前落盘即红（类缺失时编译不过）；本环境不运行 Gradle，执行归
 * 门禁串行双门（结果登记 UNVERIFIED）。
 */
class VisualMeasuredLayoutArtifactTest {

    private fun ink(
        left: Float = 0f,
        top: Float = 1f,
        right: Float = 10f,
        bottom: Float = 8f,
    ) = VisualMeasuredLayoutArtifact.InkRect(left, top, right, bottom)

    private fun text(lines: List<VisualMeasuredLayoutArtifact.Line>? = listOf(VisualMeasuredLayoutArtifact.Line(0, 3, 10f))) =
        VisualMeasuredLayoutArtifact(
            kind = VisualContentKind.TEXT,
            widthDu = 10f,
            heightDu = 8f,
            baselineDu = 4f,
            fontSizeSp = 16f,
            digest = "digest",
            lines = lines,
            degraded = false,
            inkBounds = ink(),
        )

    private fun math(degraded: Boolean = false, lines: List<VisualMeasuredLayoutArtifact.Line>? = null) =
        VisualMeasuredLayoutArtifact(
            kind = VisualContentKind.MATH,
            widthDu = 10f,
            heightDu = 8f,
            baselineDu = 4f,
            fontSizeSp = 16f,
            digest = "digest",
            lines = lines,
            degraded = degraded,
            inkBounds = ink(),
        )

    // ------------------------------------------------ 合法态（§3.1 字段表）

    @Test
    fun `a text artifact carries its explicit segments`() {
        val artifact = text()
        assertEquals(1, artifact.lines!!.size)
        assertEquals(10f, artifact.lines!![0].widthDu, 0f)
        assertTrue(!artifact.degraded)
    }

    @Test
    fun `a math box artifact carries no segments and a degraded one carries the fallback segments`() {
        assertTrue(math().lines == null)
        val degraded = math(degraded = true, lines = listOf(VisualMeasuredLayoutArtifact.Line(0, 7, 20f)))
        assertTrue(degraded.degraded)
        assertEquals(1, degraded.lines!!.size)
    }

    @Test
    fun `ink bounds may exceed the layout box or go negative, containment is not implied`() {
        // 无包含不变式（R2 §3.2-4）：斜体悬挂/fallback 大字形的越界墨迹是**合法 artifact**
        // ——类型只拒非有限/乱序，不拒越出 [0,width]×[0,height]。
        text().copy(inkBounds = ink(left = -3f, top = -5f, right = 999f, bottom = 30f))
    }

    // ------------------------------------------------ 非法态（构造即拒）

    @Test
    fun `a text artifact without segments is refused`() {
        assertRefused { text(lines = null) }
    }

    @Test
    fun `a text artifact with an empty segment list is refused`() {
        // 空段列表会让回放侧行高反解除零（playLabel 的空表防御是兜底，类型先拒）。
        assertRefused { text(lines = emptyList()) }
    }

    @Test
    fun `a non-degraded math artifact with segments is refused`() {
        assertRefused { math(lines = listOf(VisualMeasuredLayoutArtifact.Line(0, 3, 10f))) }
    }

    @Test
    fun `a degraded math artifact without segments is refused`() {
        // 降级 artifact 必须可复现：携带降级文本段列表。
        assertRefused { math(degraded = true) }
    }

    @Test
    fun `broken scalars and broken ink rects are refused`() {
        assertRefused { text().copy(widthDu = -1f) }
        assertRefused { text().copy(widthDu = Float.NaN) }
        assertRefused { text().copy(heightDu = -1f) }
        assertRefused { text().copy(baselineDu = Float.NaN) }
        assertRefused { text().copy(fontSizeSp = -1f) }
        assertRefused { text().copy(digest = " ") }
        assertRefused { text().copy(inkBounds = ink(right = -1f, bottom = 8f)) } // 乱序
        assertRefused { text().copy(inkBounds = ink(top = 9f, bottom = 8f)) } // 乱序
        assertRefused { text().copy(inkBounds = ink(left = Float.NaN)) }
    }

    @Test
    fun `a line with a reversed or negative range is refused`() {
        assertRefused { text(lines = listOf(VisualMeasuredLayoutArtifact.Line(4, 3, 10f))) }
        assertRefused { text(lines = listOf(VisualMeasuredLayoutArtifact.Line(-1, 3, 10f))) }
        assertRefused { text(lines = listOf(VisualMeasuredLayoutArtifact.Line(0, 3, -1f))) }
    }

    private fun assertRefused(block: () -> Any?) {
        try {
            block()
            fail("expected the artifact invariants to refuse this construction")
        } catch (expected: IllegalArgumentException) {
            // 预期路径。
        }
    }
}
