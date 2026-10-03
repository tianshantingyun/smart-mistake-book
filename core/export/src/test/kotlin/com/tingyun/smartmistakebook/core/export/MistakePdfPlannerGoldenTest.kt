package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.FigureSchema
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 确定性渲染的**金样对拍**（阶段 4B 批 3 · B4，新机制）。
 *
 * 它消灭的失败：4A 把分页/渲染抽成纯单元后，复核只能"逐行读码"证明行为等价
 * （4A 完成记录明写"无逐位 golden 摘要"）——之后任何一次"顺手改版式"都可能让默认输出
 * 悄悄变样而没人发现。这里把默认版式与练习卷版式的**计划结构**钉成两段显式内容断言
 * + 一个 sha256 登记值：先逐项钉内容，再比摘要（摘要不是自我实现——它是对同一串
 * 显式期望的独立压缩）。
 *
 * **登记口径（如实标注）**：4A 未沉淀改前产物，本轮也不允许检出旧提交，因此摘要为
 * "本实现单机登记"（退化方案），不是与改前 PDF 的逐位对拍；默认版式与改前等价的
 * 主要证据是**逐项内容断言 + 常量读码**，摘要用于钉住今后不再漂移。字体/系统相关的
 * 字节层对拍见 `MistakePdfDeliveryRegressionInstrumentedTest` 的仪器化用例。
 */
class MistakePdfPlannerGoldenTest {

    private val measure = PdfTextMeasure { text, start, end, style ->
        (end - start) * style.textSize
    }

    @Test
    fun `the default layout plan is pinned by explicit content and a registered digest`() {
        val plan = MistakePdfPagePlanner.plan(input(MistakePdfLayout.DEFAULT), measure)

        assertEquals(1, plan.pageCount)
        assertEquals(
            listOf(
                "line/TITLE/28.00/false/0/测试题目",
                "line/META/16.00/false/0/MATH · 第 1 版",
                "line/SPACER/8.00/false/0/",
                "line/BODY/18.00/false/0/求函数的单调区间。",
                "line/SPACER/8.00/false/0/",
                "line/BODY/18.00/false/0/下列哪个正确？",
                "line/CHOICE/18.00/false/0/A. 单调递增",
                "line/CHOICE/18.00/false/0/B. 先减后增 [已选]",
                "line/SPACER/8.00/false/0/",
                "figure/figure/SymbolTable/102.00/499.00/0",
                "line/SPACER/8.00/false/0/",
            ),
            describe(plan),
        )
        assertEquals(DEFAULT_PLAN_GOLDEN_SHA256, goldenDigest(MistakePdfLayout.DEFAULT, plan))
    }

    @Test
    fun `the practice sheet plan is pinned by explicit content and a registered digest`() {
        val layout = MistakePdfLayout(
            templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
            includeAnswer = true,
        )
        val plan = MistakePdfPagePlanner.plan(input(layout), measure)

        assertEquals(1, plan.pageCount)
        assertEquals(
            listOf(
                "line/TITLE/28.00/false/0/测试题目",
                "line/META/16.00/false/0/MATH · 第 1 版",
                "line/SPACER/8.00/false/0/",
                "line/BODY/18.00/true/0/求函数的单调区间。",
                "blank/3/54.00/0",
                "line/SPACER/8.00/false/0/",
                "line/BODY/18.00/false/0/下列哪个正确？",
                "line/CHOICE/18.00/false/0/A. 单调递增",
                "line/CHOICE/18.00/true/0/B. 先减后增 [已选]",
                "blank/2/36.00/0",
                "line/SPACER/8.00/false/0/",
                "figure/figure/SymbolTable/102.00/499.00/0",
                "line/SPACER/8.00/false/0/",
                "line/SECTION_HEADING/22.00/true/0/答案",
                "line/BODY/18.00/false/0/1. B. 先减后增",
                "line/SPACER/8.00/false/0/",
            ),
            describe(plan),
        )
        assertEquals(PRACTICE_PLAN_GOLDEN_SHA256, goldenDigest(layout, plan))
    }

    // ---- golden helpers ----

    private fun describe(plan: PdfPlan): List<String> = plan.pages.flatMap { page ->
        page.map { item ->
            when (item) {
                is PdfPlannedItem.Line ->
                    "line/${item.style.name}/${format(item.height)}/${item.keepWithNext}/" +
                        "${item.column}/${item.text}"
                is PdfPlannedItem.Formula ->
                    "formula/${item.latex}/${format(item.fontSizePx)}/${format(item.height)}/${item.column}"
                is PdfPlannedItem.Figure ->
                    "figure/${item.block.id}/${item.block.schema::class.simpleName}/" +
                        "${format(item.height)}/${format(item.width)}/${item.column}"
                is PdfPlannedItem.Image ->
                    "image/${format(item.height)}/${format(item.width)}/${item.column}"
                is PdfPlannedItem.AnswerBlank ->
                    "blank/${item.rows}/${format(item.height)}/${item.column}"
            }
        }
    }

    /** 摘要 = 版式身份 + 逐项计划结构的 sha256（与绘制字节无关，JVM 上确定可复现）。 */
    private fun goldenDigest(layout: MistakePdfLayout, plan: PdfPlan): String {
        val canonical = buildString {
            append("layout:").append(layout.canonicalForm()).append('\n')
            plan.pages.forEachIndexed { index, _ ->
                append("page:").append(index).append('\n')
            }
            describe(plan).forEach { append(it).append('\n') }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .toHex()
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)

    // ---- fixture ----

    private fun input(layout: MistakePdfLayout): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry-golden",
        problemId = "problem-golden",
        problemRevisionId = "revision-golden",
        revisionNumber = 1,
        title = "测试题目",
        subject = "MATH",
        subtitle = "MATH · 第 1 版",
        documentTitle = null,
        blocks = listOf(
            MistakePdfBlock.Paragraph(id = "stem", text = "求函数的单调区间。"),
            MistakePdfBlock.ChoiceGroup(
                id = "choices",
                prompt = "下列哪个正确？",
                choices = listOf(
                    MistakePdfChoice("a", "单调递增", false, true),
                    MistakePdfChoice("b", "先减后增", true, true),
                ),
                enabled = true,
            ),
            MistakePdfBlock.Figure(
                id = "figure",
                title = "数表",
                alternativeText = "两行数表",
                schema = FigureSchema.SymbolTable(
                    headers = listOf("x", "y"),
                    rows = listOf(listOf("0", "0"), listOf("1", "1")),
                ),
            ),
        ),
        questionDocumentSha256 = "0".repeat(64),
        inputSha256 = "1".repeat(64),
        layout = layout,
    )

    private companion object {
        /**
         * 本实现单机登记（2026-10-03）；默认版式与改前等价的结构证据见上方逐项断言与
         * `MistakePdfPagePlannerTest` 的既有常量断言。
         *
         * 修复轮 P2-1 把布局身份改成无损位表示（`imageScaleBits`），摘要随之重登记；
         * 逐项内容断言未变。
         */
        const val DEFAULT_PLAN_GOLDEN_SHA256 =
            "b2cb5e067a053c9f2ca41ceb814ff4d8ec71a61effc2712af3f66f9ba34400ee"

        /** 练习卷版式（作答空白区 + 答案区）的本实现单机登记（同上，P2-1 后重登记）。 */
        const val PRACTICE_PLAN_GOLDEN_SHA256 =
            "0a8dfa0d8e7797b2f0f554352d81adf8de97986e4b4048f8dbc59376433d6a75"
    }
}
