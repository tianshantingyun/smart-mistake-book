package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MathContentLowering] 的 JVM 判据（批次 38 S-08；R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §5.4 的 JVM 可达子集）。
 *
 * **金样口径**：期望盒尺寸由 [MathMetrics.of] 共享公式手工推导（16px：charWidth=8、
 * charHeight=16、lineSpacing=4、fracLineThickness=max(1,1)=1），与 `MathBoxGoldenTest`
 * 的 DEFAULT 金样同源（16px 基线对拍，R2 §5.4-1）。金样钉住"共享 lowering 不另起公式"。
 *
 * 先红后绿：本文件与 [MathContentLowering] 同批落盘（金样/负向用例在类缺失时编译不过）；
 * 本环境不运行 Gradle，执行归门禁串行双门（本文件结果登记为 UNVERIFIED，由 Gradle 门
 * `core:model test` 判定）。
 */
class MathContentLoweringTest {

    // ------------------------------------------------ 金样（R2 §5.4-1，16px 基线）

    @Test
    fun `golden lowering - atom boxes at the canonical 16px metrics`() {
        val result = MathContentLowering.lower("x", fontSizePx = 16f)
        val box = (result as MathContentLowering.Result.Box).box
        // 字形基线保留 20% 字号的下伸空间。
        assertEquals(8f, box.width, 1e-3f)
        assertEquals(16f, box.height, 1e-3f)
        assertEquals(12.8f, box.baseline, 1e-3f)
    }

    @Test
    fun `golden lowering - fraction boxes at the canonical 16px metrics`() {
        val result = MathContentLowering.lower("\\frac{1}{2}", fontSizePx = 16f)
        val box = (result as MathContentLowering.Result.Box).box
        // 分数横线对齐数学轴，基线在横线下方 0.3em。
        assertEquals(24f, box.width, 1e-3f)
        assertEquals(37f, box.height, 1e-3f)
        assertEquals(23.3f, box.baseline, 1e-3f)
    }

    @Test
    fun `golden lowering - radical boxes at the canonical 16px metrics`() {
        val result = MathContentLowering.lower("\\sqrt{x}", fontSizePx = 16f)
        val box = (result as MathContentLowering.Result.Box).box
        // 根号为被开方数预留 4px 顶部空间。
        assertEquals(24f, box.width, 1e-3f)
        assertEquals(20f, box.height, 1e-3f)
        assertEquals(16.8f, box.baseline, 1e-3f)
    }

    // ------------------------------------------------ 未知 TeX 语义（宽容解析不是失败面）

    @Test
    fun `an unknown command is tolerated as literal atoms, not degraded`() {
        // 解析器对未知命令降级为字面 atom（宽容语义）：未知 TeX **不是** FallbackText 的
        // 触发面——触发面是预算越界。本用例钉住该语义，防止有人把降级条件"修"到
        // 未知命令上（那会让 app 侧金样回归全红，行为零变化红线被打破）。
        val result = MathContentLowering.lower("\\foo{bar}", fontSizePx = 16f)
        assertTrue(
            "an unknown command must parse into literal atoms (Box), not degrade",
            result is MathContentLowering.Result.Box,
        )
    }

    // ------------------------------------------------ 预算越界降级（R2 §5.4-3）

    @Test
    fun `an over-budget input degrades with the original text passed through`() {
        val overBudget = "x".repeat(MathBudget.MAX_INPUT_LENGTH + 1) // 2001 字符
        val result = MathContentLowering.lower(overBudget, fontSizePx = 16f)
        val fallback = result as MathContentLowering.Result.FallbackText
        // 原文透传（非重建，R2 §5.4-2）：readable 恒等于输入——度量侧量的、回放侧画的
        // 都是同一份文本，单一事实来源。
        assertEquals(overBudget, fallback.readable)
        // error 携带预算诊断（进诊断、不进显示）。
        assertTrue(
            "the fallback error must carry the budget message, got: ${fallback.error}",
            fallback.error.contains("${MathBudget.MAX_INPUT_LENGTH}"),
        )
    }

    @Test
    fun `a within-budget input keeps the box result unchanged`() {
        val result = MathContentLowering.lower("x", fontSizePx = 16f)
        assertTrue(result is MathContentLowering.Result.Box)
        assertEquals("", (result as MathContentLowering.Result.Box).parse.error ?: "")
    }

    // ------------------------------------------------ 与既有链逐字段等价（R2 §5.4-4 迁移回归）

    @Test
    fun `lower matches the existing parseAndBuildBox plus buildBox chain field by field`() {
        val inputs = listOf("x", "\\frac{1}{2}", "\\sqrt{x}", "x^2", "a+b")
        for (input in inputs) {
            val lowered = MathContentLowering.lower(input, fontSizePx = 16f) as MathContentLowering.Result.Box
            val parsed = parseAndBuildBox(input)
            val expected = MathBoxBuilder.buildBox(parsed.node!!, MathMetrics.of(16f))
            // 每字段独立比对（不做整体 data-class 相等——MathBox 子类结构可能因 builder
            // 内部优化变化，逐字段才是迁移回归要钉的语义）。
            assertEquals("width of \"$input\"", expected.width, lowered.box.width, 1e-3f)
            assertEquals("height of \"$input\"", expected.height, lowered.box.height, 1e-3f)
            assertEquals("baseline of \"$input\"", expected.baseline, lowered.box.baseline, 1e-3f)
            assertEquals("parse token count of \"$input\"", parsed.tokenCount, lowered.parse.tokenCount)
            assertEquals("parse node count of \"$input\"", parsed.nodeCount, lowered.parse.nodeCount)
        }
    }

    // ------------------------------------------------ fontScale 联动（R2 §5.4-6 / M-03 N 家族共享）

    @Test
    fun `box dimensions scale linearly with fontsizepx`() {
        val base = (MathContentLowering.lower("\\frac{1}{2}", 16f) as MathContentLowering.Result.Box).box
        val scaled = (MathContentLowering.lower("\\frac{1}{2}", 32f) as MathContentLowering.Result.Box).box
        // MathMetrics.of 对字号线性 → 盒尺寸线性：fontScale 放大即盒尺寸放大，数学半边
        // 的布局预算（checkLayoutSize 的字符宽口径）随之天然生效（M-03 N 家族共享口径）。
        assertEquals(2f, scaled.width / base.width, 1e-3f)
        assertEquals(2f, scaled.height / base.height, 1e-3f)
        assertEquals(2f, scaled.baseline / base.baseline, 1e-3f)
    }
}
