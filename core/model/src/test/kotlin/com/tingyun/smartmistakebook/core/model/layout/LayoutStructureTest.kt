package com.tingyun.smartmistakebook.core.model.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版面结构分析的**回归锁**（纯 JVM，合成页面，无外部夹具）。
 *
 * **它消灭的具体失败**：`LayoutStructure` 的每个阈值都来自 dev 16 页实测（见
 * `docs/research/2026-11-09-split-cv-llm.md` §3.2）。没有这组用例时，任何一次"顺手调一下
 * 比例"都能无声地把双栏页判成单栏、把提示词撑过 wire 预算，而只有重跑整个 dev 探针才会发现。
 *
 * 合成页面的坐标是**推算出来的**，不是从实现输出回填的：
 * - 页面 800×1000，纸面 (60,60)–(740,940)，纸外深色桌面 40、纸面 235 → 内容窗必须裁掉桌面；
 * - 两栏栏面 (100,360) 与 (420,700)，栏谷 60 px（>2%×680），谷心 0.485 页宽 → 判双栏；
 * - 行高 8 px、行距 24 px → 谷底分割把每一行切成块，块数远大于提示词的 8 条上限。
 */
class LayoutStructureTest {

    private val paper = 235
    private val desk = 40
    private val ink = 30

    /** 字形抗锯齿晕圈值：真机照片的字边就是这一档中间调（见 [twoColumnPage] 的说明）。 */
    private val halo = 130
    private val haloWidth = 2

    // ---- 合成页面 ----

    private fun blankPage(width: Int, height: Int): ByteArray = ByteArray(width * height) { desk.toByte() }

    private fun fillRect(
        page: ByteArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        value: Int,
    ) {
        for (y in top until bottom) {
            val base = y * width
            for (x in left until right) page[base + x] = value.toByte()
        }
    }

    /** 带 2 px 抗锯齿晕圈的字带（模拟真实字形边缘的中间调）。 */
    private fun fillGlyphRow(
        page: ByteArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        fillRect(page, width, left - haloWidth, top - haloWidth, right + haloWidth, bottom + haloWidth, halo)
        fillRect(page, width, left, top, right, bottom, ink)
    }

    /**
     * 纸面 800×1000 + 两栏各 [lineCount] 行，两个栏的行 y 完全对齐。
     *
     * 两个参数是**按算法口径推算的，不是把实现输出回填进来的**：
     * - 行高 22 + 间距 6 → 22/28 ≈ 79% 的行两侧都有墨，满足"两侧同高覆盖 ≥45%"。用 8/24 的
     *   稀疏行带只有 9%，会被 fail-closed 规则正确拒成单栏（夹具不像真页面，不是规则错）。
     * - **必须带抗锯齿晕圈**：三联值图像（墨 30 / 桌 40 / 纸 235）下 Otsu 落在 40，`Otsu−20`
     *   低于墨值，结构墨为空 → 永远单栏。真实照片的字边中间调把直方图填满，Otsu 才会落在
     *   墨与纸之间（用 `tools/layout_probe/probe_layout.py` 的同一函数离线复核：有晕圈 = 2 栏）。
     */
    private fun twoColumnPage(
        width: Int = 800,
        height: Int = 1000,
        leftColumn: IntRange = 100..360,
        rightColumn: IntRange = 420..700,
        lineCount: Int = 20,
    ): ByteArray {
        val page = blankPage(width, height)
        fillRect(page, width, 60, 60, 740, 940, paper)
        for (line in 0 until lineCount) {
            val top = 100 + line * 28
            fillGlyphRow(page, width, leftColumn.first, top, leftColumn.last, top + 22)
            fillGlyphRow(page, width, rightColumn.first, top, rightColumn.last, top + 22)
        }
        return page
    }

    /** 纸面 800×1000 + 单栏一整列（栏宽 600，占页宽 75%）。 */
    private fun singleColumnPage(): ByteArray {
        val page = blankPage(800, 1000)
        fillRect(page, 800, 60, 60, 740, 940, paper)
        for (line in 0 until 20) {
            val top = 100 + line * 28
            fillGlyphRow(page, 800, 100, top, 700, top + 22)
        }
        return page
    }

    // ---- S1/S2：阈值与裁边 ----

    @Test
    fun otsuSeparatesPaperFromInkAndDesk() {
        val threshold = LayoutStructure.otsu(twoColumnPage())

        assertTrue("阈值必须落在墨与纸之间，实测 $threshold", threshold in (ink + 1)..(paper - 1))
    }

    @Test
    fun contentWindowCropsTheDeskAroundThePaper() {
        val width = 800
        val height = 1000
        val page = twoColumnPage(width, height)
        val threshold = LayoutStructure.otsu(page)

        val window = LayoutStructure.contentWindow(page, width, height, threshold, 0.25)

        assertEquals(60, window.left)
        assertEquals(60, window.top)
        assertEquals(740, window.right)
        assertEquals(940, window.bottom)
    }

    // ---- S4：分栏 ----

    @Test
    fun twoColumnPageIsDetectedAndSingleColumnPageIsNot() {
        val twoColumn = LayoutStructure.analyse(twoColumnPage(), 800, 1000)
        val single = LayoutStructure.analyse(singleColumnPage(), 800, 1000)

        assertTrue("两栏页未判出双栏：${twoColumn.columns}", twoColumn.twoColumn)
        assertEquals(2, twoColumn.columns.size)
        // 栏谷边界 = 左栏右沿 / 右栏左沿，再各让出 2 px 抗锯齿晕圈（360+2 / 420−2）。
        assertEquals(362, twoColumn.columns[0].right)
        assertEquals(418, twoColumn.columns[1].left)
        assertFalse("单栏页被误判双栏：${single.columns}", single.twoColumn)
        assertEquals(1, single.columns.size)
    }

    @Test
    fun oneSidedBlankMiddleIsNotAColumnGutter() {
        // 消灭"单栏页中部留白被当成栏谷"：右半页整片空白时，右侧墨量为 0 < 20% 门槛。
        val page = blankPage(800, 1000)
        fillRect(page, 800, 60, 60, 740, 940, paper)
        for (line in 0 until 20) {
            val top = 100 + line * 28
            fillGlyphRow(page, 800, 100, top, 360, top + 22)
        }

        val structure = LayoutStructure.analyse(page, 800, 1000)

        assertFalse("单侧留白被误判双栏：${structure.columns}", structure.twoColumn)
    }

    // ---- S3/S5：行带与段落分隔 ----

    @Test
    fun rowProjectionSplitsDensePageIntoLinesAndReportsLinePitch() {
        val structure = LayoutStructure.analyse(twoColumnPage(), 800, 1000)

        assertTrue("行带为空：${structure.separators.size}", structure.separators.size >= 10)
        // 行距 = 行高 22 + 空白 6，但 3 抽头平滑让块边界两侧各含 1 行 → 实测 4~6。
        assertTrue("行距 ${structure.linePitchPx} 不在 4..8（行带口径漂移）", structure.linePitchPx in 4..8)
    }

    // ---- 提示词契约 ----

    @Test
    fun promptBlockStatesColumnsAndTruncatesSeparatorsExplicitly() {
        val block = LayoutStructure.analyse(twoColumnPage(), 800, 1000).toPromptBlock()

        assertTrue(block, block.contains("双栏"))
        // 栏谷在 x≈0.45–0.53（362/800 = 0.4525，418/800 = 0.5225）；不钉舍入位，
        // 只钉"栏间空白的 x 区间确实在这条谷上"。
        assertTrue(
            "栏谷坐标不在 0.45–0.53：$block",
            Regex("""栏间空白位于 x=0\.4[5-9]\d?–0\.5[0-4]\d?""").containsMatchIn(block),
        )
        assertTrue("超过 8 条必须显式写已截断：$block", block.contains("已截断到 8 条"))
        assertTrue(block.length <= PageStructure.MAX_HINT_CHARS)
    }

    @Test
    fun singleColumnPromptSaysSingleColumn() {
        val block = LayoutStructure.analyse(singleColumnPage(), 800, 1000).toPromptBlock()

        assertTrue(block, block.contains("单栏"))
    }

    @Test
    fun pageTooSmallToAnalyseDegradesToNoCoordinatesAtAll() {
        // 内容窗不可用（<4 px）→ 降级。降级文本必须**一个坐标都不给**：错的坐标比没坐标更坏。
        val block = LayoutStructure.analyse(ByteArray(2 * 2) { paper.toByte() }, 2, 2).toPromptBlock()

        assertEquals("【本地版面测量】不可用（页面朝向无法确定）；请完全依据图像判断。", block)
    }

    @Test
    fun pageWithoutVisiblePaperMarginFallsBackToTheFullFrameButStillReportsColumns() {
        // 研究 §3.1 S1：内容窗不可用（整幅无"纸面"行/列）时**退回整幅**并继续给栏信息，
        // 不是整块降级——灰底拍摄的真实页面就长这样。
        val page = ByteArray(800 * 1000) { 90.toByte() }
        for (line in 0 until 20) {
            val top = 100 + line * 28
            fillGlyphRow(page, 800, 100, top, 700, top + 22)
        }

        val structure = LayoutStructure.analyse(page, 800, 1000)
        val block = structure.toPromptBlock()

        assertEquals(0, structure.contentWindow.left)
        assertEquals(800, structure.contentWindow.right)
        assertFalse("整幅拍摄不得被判为朝向不可用", structure.orientationUncertain)
        assertTrue(block, block.contains("单栏"))
    }

    @Test
    fun questionCandidatesNeverReachThePromptBlock() {
        val structure = LayoutStructure.analyse(twoColumnPage(), 800, 1000)
        val withCandidates = PageStructure(
            pageWidth = structure.pageWidth,
            pageHeight = structure.pageHeight,
            contentWindow = structure.contentWindow,
            columns = structure.columns,
            separators = structure.separators,
            linePitchPx = structure.linePitchPx,
            twoColumn = structure.twoColumn,
            orientationUncertain = false,
            questionCandidates = structure.questionCandidates,
        )
        val withoutCandidates = withCandidates.let { base ->
            PageStructure(
                pageWidth = base.pageWidth,
                pageHeight = base.pageHeight,
                contentWindow = base.contentWindow,
                columns = base.columns,
                separators = base.separators,
                linePitchPx = base.linePitchPx,
                twoColumn = base.twoColumn,
                orientationUncertain = false,
                questionCandidates = emptyList(),
            )
        }

        assertTrue("候选生成器必须真的产出候选", structure.questionCandidates.isNotEmpty())
        assertEquals(
            "题号候选不得进入提示词（dev 实测 precision 0.111 < 基准率，见 QuestionNumberCandidate）",
            withoutCandidates.toPromptBlock(),
            withCandidates.toPromptBlock(),
        )
    }

    // ---- 题号候选：几何量必须是量出来的，不能是常量 ----

    @Test
    fun questionCandidateGeometryMeasuresRailTokenAndGap() {
        // 单栏 400×300，栏面 (50,350)。三行：
        //   第 1 行 "1." —— 贴左缘 + 窄首段（7 px / 300 = 0.023 落在 0.008..0.10）
        //   第 2 行 无缩进满宽 —— 首段宽 = 整栏 → tokenOk = false
        //   第 3 行 缩进 60 px —— railOk = false（0.20 > 0.06）
        // 块边界实测比墨带两侧各多 1 行（3 抽头平滑），所以按 ±2 找候选，不按精确 y。
        val width = 400
        val page = blankPage(width, 300)
        fillRect(page, width, 50, 20, 350, 280, paper)
        // 第 1 行：题号 + 间隔 + 正文
        fillRect(page, width, 50, 60, 57, 74, ink)
        fillRect(page, width, 70, 60, 300, 74, ink)
        // 第 2 行：满宽（从栏左缘起就是连续墨）
        fillRect(page, width, 50, 100, 350, 114, ink)
        // 第 3 行：缩进 60 px 的续行
        fillRect(page, width, 110, 140, 350, 154, ink)

        val candidates = LayoutStructure.analyse(page, width, 300).questionCandidates
        fun near(top: Int): QuestionNumberCandidate =
            candidates.minByOrNull { kotlin.math.abs(it.topPx - top) }
                ?: error("没有 top≈$top 的候选：${candidates.map { it.topPx }}")

        assertTrue("未产出候选：${candidates.size}", candidates.size >= 3)
        val first = near(60)
        assertTrue("贴左缘的首行应 railOk：indent=${first.indentRatio}", first.railOk)
        assertTrue("窄首段应 tokenOk：token=${first.tokenRatio}", first.tokenOk)
        assertEquals("首个块没有前块，gapBefore 必须为 null", null, first.gapBeforePx)
        assertFalse("首个块没有前块时 gapOk 不得为 true", first.gapOk)

        val fullLine = near(100)
        assertFalse("满宽首行不得 tokenOk：token=${fullLine.tokenRatio}", fullLine.tokenOk)

        val indented = near(140)
        assertFalse("缩进续行不得 railOk：indent=${indented.indentRatio}", indented.railOk)
        assertEquals(60.0 / 300.0, indented.indentRatio, 0.01)

        assertTrue(
            "score 必须是三条证据之和：${candidates.map { it.score }}",
            candidates.all {
                it.score == (if (it.railOk) 1 else 0) + (if (it.tokenOk) 1 else 0) + (if (it.gapOk) 1 else 0) &&
                    it.score in 0..3
            },
        )
    }

    @Test
    fun nonPositivePageIsRejectedLoudly() {
        // 编程错误（尺寸与缓冲不匹配）必须当场炸，不能静默返回"不可用"结构。
        val failure = runCatching { LayoutStructure.analyse(ByteArray(10), 4, 4) }
        assertTrue("尺寸不匹配必须抛 IllegalArgumentException", failure.exceptionOrNull() is IllegalArgumentException)
    }
}
