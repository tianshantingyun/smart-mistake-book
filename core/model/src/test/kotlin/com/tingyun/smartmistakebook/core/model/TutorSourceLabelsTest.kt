package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2 来源标签（D-S / 2026-09-27 补录：语义 = **交叉参照**，不是答案出处）。
 *
 * 判别格是"哪些标签能活下来"：只有**本轮真的披露过**的材料标题才留；模型自己编的标题、以及
 * 任何读起来像"答案来自课本"的旧写法（来自 / 来源 / 出自）一律剥掉——剥掉标签，不是改成
 * "未经核对"，因为被本地标注过的"与课本一致"反而更像背书。
 */
class TutorSourceLabelsTest {
    private val disclosed = listOf("必修一·函数单调性", "必修二·平面向量")

    @Test
    fun aLabelAboutADisclosedMaterialSurvives() {
        val text = "单调性的判定要先看区间（与课本一致：必修一·函数单调性）。"

        assertEquals(text, stripUndisclosedSourceLabels(text, disclosed))
    }

    @Test
    fun aLabelAboutSomethingNeverDisclosedIsStripped() {
        val text = "这一步用的是三角恒等变换（与课本一致：必修四·三角恒等变换）。"

        assertEquals("这一步用的是三角恒等变换。", stripUndisclosedSourceLabels(text, disclosed))
    }

    @Test
    fun nothingIsDisclosedSoNoLabelSurvives() {
        val text = "结论（与课本一致：必修一·函数单调性）成立。"

        assertEquals("结论成立。", stripUndisclosedSourceLabels(text, emptyList()))
    }

    @Test
    fun theLegacyWordingIsAlwaysStrippedEvenWhenTheMaterialWasDisclosed() {
        // "来自：X"读作**答案出处**（台账点名的误读）；同一条材料披露过也不留——
        // 宁可没有标签，也不能留下读错的标签。
        listOf(
            "（来自：必修一·函数单调性）",
            "（来源：必修一·函数单调性）",
            "（出自：必修一·函数单调性）",
        ).forEach { label ->
            val text = "这句话有出处 $label。"

            assertEquals("这句话有出处。", stripUndisclosedSourceLabels(text, disclosed))
        }
    }

    @Test
    fun whitespaceInsideTheTitleDoesNotDefeatTheCheck() {
        val text = "与课本的顺序一致（与课本一致：必修一 · 函数单调性）。"

        assertEquals(text, stripUndisclosedSourceLabels(text, disclosed))
    }

    @Test
    fun asciiParenthesesAreAcceptedForTheCanonicalForm() {
        val text = "这条与课本一致(与课本一致：必修二·平面向量)。"

        assertEquals(text, stripUndisclosedSourceLabels(text, disclosed))
    }

    @Test
    fun severalLabelsInOneParagraphAreCheckedOneByOne() {
        val text = "第一句（与课本一致：必修一·函数单调性）。第二句（与课本一致：必修五·数列）。"

        assertEquals(
            "第一句（与课本一致：必修一·函数单调性）。第二句。",
            stripUndisclosedSourceLabels(text, disclosed),
        )
    }

    @Test
    fun aLabelWithoutAClosingParenthesisIsLeftAlone() {
        // 收不口就不是一条标签：本地不做"猜到哪里结束"的模糊处理（那等于把核对降级成看起来像）。
        val text = "（与课本一致：必修四·三角恒等变换"

        assertEquals(text, stripUndisclosedSourceLabels(text, disclosed))
    }

    @Test
    fun textWithoutLabelsIsReturnedUnchanged() {
        val text = "先看定义域，再看单调区间。\n第二行也是普通正文。"

        assertEquals(text, stripUndisclosedSourceLabels(text, disclosed))
    }

    @Test
    fun strippingTidiesTheWhitespaceLeftBehind() {
        val text = "结论  （与课本一致：必修三·概率）成立。"

        assertEquals("结论成立。", stripUndisclosedSourceLabels(text, disclosed).let { stripped ->
            // 剥完只剩一句干净的话（多余空格也要收掉，否则会留下"结论 成立"这样的残迹）。
            stripped
        })
    }
}
