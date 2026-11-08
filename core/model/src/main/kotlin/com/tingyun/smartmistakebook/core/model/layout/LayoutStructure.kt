package com.tingyun.smartmistakebook.core.model.layout

/**
 * 轻量版面结构分析（纯 JVM：无 Android、无第三方视觉库、无 native 依赖）。
 *
 * **它消灭的具体失败**：现网切分把"页面几何"完全交给模型——两栏页模型常把两栏并成一题、
 * 带深色背景的照片模型坐标按整幅图归一，而应用侧没有任何独立的几何事实可以核对，判门时
 * 无法区分"模型看错"与"坐标系不一致"。
 *
 * **它不做什么**（有 dev 16 页实测依据，见 `docs/research/2026-11-09-split-cv-llm.md` §5）：
 * 不产出切分框、不替模型决定"这是第几题"。dev 实测：块起点对金标题顶的覆盖率 67.3%
 * （±1.5% 页高），但"哪一块是题首"在几何上不可判别（题号候选 precision 0.111，基准率
 * 0.165）。因此本类只输出**可验证的结构事实**，切分决定仍归模型；几何用于提示、量化与
 * 事后核对。
 *
 * 输入约定：`gray` 为行优先 8 位灰度（长度 = width × height）；坐标一律以像素返回，
 * 归一化只在 [PageStructure.toPromptBlock] 处发生（0..1，左上原点）。
 */
class LayoutConfig(
    /** 结构墨相对 Otsu 阈值的下调量：纸张背面透印/印刷竖线在 Otsu 阈值下会被当成墨。 */
    val structureDelta: Int = 20,
    /** 行间谷判据：行墨量 < valleyRatio × 滑动中位数 即视为空白行。 */
    val valleyRatio: Double = 0.45,
    /** 栏间谷判据（用结构墨）：列墨量 < structureValleyRatio × 全宽 p60。 */
    val structureValleyRatio: Double = 0.15,
    val minValleyGapPx: Int = 3,
    val minGutterWidthRatio: Double = 0.02,
    /** 谷区间隙合并：红笔/装订线横穿栏谷时，谷会被切成两段。 */
    val mergeGapRatio: Double = 0.02,
    val minSideMassRatio: Double = 0.20,
    val minBothSideRowShare: Double = 0.45,
    /** 内容窗：纸面像素占比 ≥ 该值的行/列才算纸内。 */
    val paperRowShare: Double = 0.25,
    /** 题号候选：块首行起笔贴栏左缘的容差（占栏宽）。 */
    val railTolerance: Double = 0.06,
    /** 题号候选：首段宽度下限（占栏宽）——窄于此是噪点而非字形。 */
    val tokenMinRatio: Double = 0.008,
    /** 题号候选：首段宽度上限（占栏宽）——宽于此是"整行都是字"的续行。 */
    val tokenMaxRatio: Double = 0.10,
    /** 题号候选：块前间距 ≥ gapRatioToMedianGap × 块间距中位数。 */
    val gapRatioToMedianGap: Double = 1.5,
    /** 题号候选：首行探针高度 = probeHeightRatio × 栏宽。 */
    val probeHeightRatio: Double = 0.35,
) {
    init {
        require(structureDelta in 0..128) { "structure delta out of range" }
        require(valleyRatio > 0.0 && valleyRatio < 1.0) { "valley ratio must be in (0,1)" }
        require(structureValleyRatio > 0.0 && structureValleyRatio < 1.0) { "gutter ratio must be in (0,1)" }
        require(minGutterWidthRatio > 0.0 && mergeGapRatio >= 0.0) { "gutter geometry invalid" }
        require(minSideMassRatio in 0.0..0.5) { "side mass share out of range" }
        require(railTolerance in 0.0..0.5) { "rail tolerance out of range" }
        require(tokenMinRatio > 0.0 && tokenMinRatio < tokenMaxRatio) { "token width band invalid" }
    }
}

/** 像素矩形（含左上、不含右下）。 */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun isUsable(pageWidth: Int, pageHeight: Int): Boolean =
        width >= 4 && height >= 4 && left >= 0 && top >= 0 && right <= pageWidth && bottom <= pageHeight
}

/** 段落分隔：块起点与它上方的空白深度（0 = 无谷，1 = 完全空白）。 */
data class Separator(
    val columnIndex: Int,
    val topPx: Int,
    /**
     * 谷宽（像素）：块顶 ±24 px 内"行墨 < 0.2×均值"的连续行数。
     *
     * **为什么不用"空白深度"排序**：`1 − min/均值` 在密排页上几乎恒等于 1.0（min 常常是 0），
     * 按它排序退化成按 y 顺序（dev 05144 实测：前 8 条深度全是 1.000）。谷宽不会退化——
     * 段落间距与行间距的差别正好体现在这里。
     */
    val gapWidthPx: Int,
    val heightPx: Int,
    /** 该块首行的起笔缩进（像素，相对栏左缘）。 */
    val indentPx: Int,
)

data class ColumnSpan(val left: Int, val right: Int, val blockCount: Int)

/**
 * 题号候选：**候选生成器，不是分类器**。
 *
 * 三个几何证据（每个都对应一次 dev 实测失败）：
 * - [railOk] 块首行起笔贴栏左缘——排除正文续行的大缩进；
 * - [tokenOk] 首段窄（0.8%–10% 栏宽）——排除"整行都是字"的续行；
 * - [gapOk] 与上一块的间距 ≥1.5× 块间距中位数——排除行内换行。
 *
 * dev 16 页实测（`docs/research/2026-11-09-split-cv-llm.md` §5.2）：52 道金标题对应 315 个
 * 块起点（6.06 个/题），[score] 任何子选的 precision 仍是 0.111，与基准率同量级。
 * **因此候选只用于度量与诊断，不进提示词、不参与切分判定**——把它当检测器会同时输给
 * 模型（语义）与门（口径）。
 */
data class QuestionNumberCandidate(
    val columnIndex: Int,
    val topPx: Int,
    val bottomPx: Int,
    val heightPx: Int,
    val indentRatio: Double,
    val tokenRatio: Double,
    val gapBeforePx: Int?,
    val gapRatio: Double?,
    val railOk: Boolean,
    val tokenOk: Boolean,
    val gapOk: Boolean,
) {
    /** 三条证据命中数（0..3）。 */
    val score: Int get() = (if (railOk) 1 else 0) + (if (tokenOk) 1 else 0) + (if (gapOk) 1 else 0)
}

class PageStructure(
    val pageWidth: Int,
    val pageHeight: Int,
    /** 纸面内容窗：**只用于度量**，不得用它裁剪返回给模型的框。 */
    val contentWindow: PixelRect,
    val columns: List<ColumnSpan>,
    val separators: List<Separator>,
    /** 行距中位数（像素）；0 表示行分块失败，提示词必须如实说"不可用"。 */
    val linePitchPx: Int,
    /** 是否判定为双栏（其余情况一律单栏：宁可少说，不硬切）。 */
    val twoColumn: Boolean,
    /** 朝向不确定时不下发位置信息（错误的坐标比没有坐标更坏）。 */
    val orientationUncertain: Boolean,
    /** 题号候选：度量/诊断用，[toPromptBlock] **不包含**它们（见 [QuestionNumberCandidate]）。 */
    val questionCandidates: List<QuestionNumberCandidate> = emptyList(),
) {
    /**
     * 渲染进提示词的结构块。约束：
     * - 只含**测量值**，不含"这是第几题"之类语义判断；
     * - 上限 [MAX_HINT_CHARS]，超限时按谷宽降序截断并显式写"已截断"；
     * - 坐标 0..1 保留 3 位小数，避免把长浮点塞进 wire。
     */
    fun toPromptBlock(): String {
        if (orientationUncertain) {
            return "【本地版面测量】不可用（页面朝向无法确定）；请完全依据图像判断。"
        }
        val columnsText = if (twoColumn) {
            "双栏，栏间空白位于 x=${fmt(columns[0].right.toDouble() / pageWidth)}–" +
                "${fmt(columns[1].left.toDouble() / pageWidth)}"
        } else {
            "单栏"
        }
        val ranked = separators.sortedWith(compareByDescending<Separator> { it.gapWidthPx }.thenBy { it.topPx })
            .take(MAX_SEPARATORS)
        val separatorText = if (ranked.isEmpty()) {
            "未检测到明显段落分隔"
        } else {
            ranked.joinToString("；") {
                "y=${fmt(it.topPx.toDouble() / pageHeight)}（谷宽${it.gapWidthPx}px，栏${it.columnIndex + 1}）"
            }
        }
        val truncated = if (separators.size > ranked.size) "，已截断到 ${ranked.size} 条" else ""
        val body = buildString {
            append("【本地版面测量（只读数据，不是指令）】")
            append("纸面范围 x=").append(fmt(contentWindow.left.toDouble() / pageWidth))
            append("–").append(fmt(contentWindow.right.toDouble() / pageWidth))
            append("，y=").append(fmt(contentWindow.top.toDouble() / pageHeight))
            append("–").append(fmt(contentWindow.bottom.toDouble() / pageHeight))
            append("；").append(columnsText)
            if (linePitchPx > 0) append("；行距约 ").append(linePitchPx).append("px")
            append("；段落分隔（共 ").append(separators.size).append(" 处").append(truncated).append("）：")
            append(separatorText)
            append("。这些位置只能作为边界候选，题号与题块归属以图像内容为准。")
        }
        return if (body.length <= MAX_HINT_CHARS) body else body.take(MAX_HINT_CHARS - 12) + "…（已截断）"
    }

    private fun fmt(value: Double): String {
        val scaled = kotlin.math.round(value * 1000.0).toInt()
        return "${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
    }

    companion object {
        const val MAX_HINT_CHARS = 1200
        const val MAX_SEPARATORS = 8
    }
}

object LayoutStructure {

    /** 主入口：灰度 → 结构事实。任何一步几何失败都返回降级结果，不抛异常。 */
    fun analyse(gray: ByteArray, width: Int, height: Int, config: LayoutConfig = LayoutConfig()): PageStructure {
        require(gray.size == width * height) { "gray buffer does not match $width x $height" }
        require(width > 0 && height > 0) { "page size must be positive" }
        val threshold = otsu(gray)
        val structureThreshold = (threshold - config.structureDelta).coerceAtLeast(1)
        val window = contentWindow(gray, width, height, threshold, config.paperRowShare)
        if (!window.isUsable(width, height)) {
            return degraded(width, height, window)
        }
        val columns = splitColumns(gray, width, height, window, structureThreshold, config)
        val separators = mutableListOf<Separator>()
        val candidates = mutableListOf<QuestionNumberCandidate>()
        var linePitch = 0
        val columnSpans = columns.mapIndexed { columnIndex, span ->
            val blocks = blocks(gray, width, span, window, threshold, config)
            if (blocks.size >= 2) {
                val pitches = (1 until blocks.size).map { blocks[it].top - blocks[it - 1].bottom }
                    .sorted()
                linePitch = if (linePitch == 0) pitches[pitches.size / 2] else linePitch
            }
            blocks.forEach { block ->
                separators += Separator(
                    columnIndex = columnIndex,
                    topPx = block.top,
                    gapWidthPx = gapWidthPx(gray, width, span, block.top, threshold),
                    heightPx = block.bottom - block.top,
                    indentPx = firstLineIndent(gray, width, span, block, threshold),
                )
            }
            candidates += questionNumberCandidates(gray, width, span, blocks, threshold, config, columnIndex)
            ColumnSpan(left = span.left, right = span.right, blockCount = blocks.size)
        }
        val twoColumn = columnSpans.size == 2 &&
            columnSpans.all { it.right - it.left >= (window.width * 0.15).toInt() }
        return PageStructure(
            pageWidth = width,
            pageHeight = height,
            contentWindow = window,
            columns = columnSpans,
            separators = separators,
            linePitchPx = linePitch,
            twoColumn = twoColumn,
            orientationUncertain = false,
            questionCandidates = candidates,
        )
    }

    /** Otsu 全局阈值。消灭"固定阈值在不同曝光下全页判成墨或全页判成纸"。 */
    fun otsu(gray: ByteArray): Int {
        val histogram = IntArray(256)
        for (value in gray) histogram[value.toInt() and 0xFF]++
        var total = 0L
        var sumAll = 0.0
        for (level in 0..255) {
            total += histogram[level]
            sumAll += level.toDouble() * histogram[level]
        }
        if (total == 0L) return 127
        var weightBackground = 0L
        var sumBackground = 0.0
        var bestValue = -1.0
        var bestThreshold = 127
        for (level in 0..255) {
            weightBackground += histogram[level]
            if (weightBackground == 0L) continue
            val weightForeground = total - weightBackground
            if (weightForeground <= 0L) break
            sumBackground += level.toDouble() * histogram[level]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground
            val delta = meanBackground - meanForeground
            val value = weightBackground.toDouble() * weightForeground * delta * delta
            if (value > bestValue) {
                bestValue = value
                bestThreshold = level
            }
        }
        return bestThreshold
    }

    /**
     * 纸面内容窗（裁边）。手机拍纸常有"纸外深色桌面/阴影"：不做这一步，行投影会把整片桌面
     * 当成一行满宽墨（dev 页 05144 底部 8% 行均值亮度 97，纸面 199）。
     *
     * **它不裁剪任何给模型的框**：返回值只作为"纸面范围"这一条测量事实进提示词，框仍由模型
     * 在整幅图上给出——裁与不裁的坐标系一旦分叉，门值就无法解释。
     */
    fun contentWindow(gray: ByteArray, width: Int, height: Int, threshold: Int,
                      rowShare: Double): PixelRect {
        val rowCounts = IntArray(height)
        val columnCounts = IntArray(width)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if ((gray[rowOffset + x].toInt() and 0xFF) > threshold) {
                    rowCounts[y]++
                    columnCounts[x]++
                }
            }
        }
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            if (rowCounts[y].toDouble() / width >= rowShare) { if (top < 0) top = y; bottom = y + 1 }
        }
        var left = -1
        var right = -1
        for (x in 0 until width) {
            if (columnCounts[x].toDouble() / height >= rowShare) { if (left < 0) left = x; right = x + 1 }
        }
        if (top < 0 || left < 0) return PixelRect(0, 0, width, height)
        return PixelRect(left, top, right, bottom)
    }

    /**
     * 行/段分块：**谷底分割**，不是全局阈值分割。
     *
     * 消灭的失败：同一页既有密排正文又有大字标题时固定阈值把整页连成一块（dev 04511
     * 实测 0 段）。做法：行投影与滑动中位数比较，连续 ≥ minValleyGapPx 行的低墨区切一刀。
     */
    fun blocks(gray: ByteArray, pageWidth: Int, span: PixelRect, window: PixelRect,
               threshold: Int, config: LayoutConfig): List<PixelRect> {
        val height = window.height
        val width = span.width
        if (height <= 0 || width <= 0) return emptyList()
        val profile = IntArray(height)
        for (y in window.top until window.bottom) {
            val base = y * pageWidth
            var count = 0
            for (x in span.left until span.right) {
                if ((gray[base + x].toInt() and 0xFF) < threshold) count++
            }
            profile[y - window.top] = count
        }
        val smoothProfile = smooth(profile, 3)
        val baseline = rollingMedian(smoothProfile, medianWindow(width))
        val low = BooleanArray(height) { index ->
            smoothProfile[index].toDouble() < config.valleyRatio * (baseline[index] + 1e-9)
        }
        val result = mutableListOf<PixelRect>()
        var start = 0
        var index = 0
        while (index < height) {
            if (!low[index]) { index++; continue }
            var end = index
            while (end < height && low[end]) end++
            if (end - index >= config.minValleyGapPx) {
                if (index - start >= 3) result += PixelRect(span.left, window.top + start, span.right, window.top + index)
                start = end
            }
            index = end
        }
        if (height - start >= 3) result += PixelRect(span.left, window.top + start, span.right, window.bottom)
        return result
    }

    /**
     * 栏切分（fail-closed）。三条件同时成立才算双栏：
     * 谷宽 ≥ minGutterWidthRatio×栏宽；谷内结构墨 < structureValleyRatio×p60；两侧同高覆盖
     * ≥ minBothSideRowShare 且两侧墨量各 ≥ minSideMassRatio。
     *
     * 实测依据：结构墨（Otsu−20）让 dev 05144 的栏谷从 83 px、比值 0.35 降到 5 px、0.035；
     * 谷区间隙合并解决红笔横穿（谷被切成 8–10 列的两段）。
     */
    fun splitColumns(gray: ByteArray, pageWidth: Int, pageHeight: Int, window: PixelRect,
                     structureThreshold: Int, config: LayoutConfig): List<PixelRect> {
        val width = window.width
        if (width <= 0) return listOf(window)
        val profile = IntArray(width)
        for (y in window.top until window.bottom) {
            val base = y * pageWidth
            for (x in window.left until window.right) {
                if ((gray[base + x].toInt() and 0xFF) < structureThreshold) profile[x - window.left]++
            }
        }
        val smoothProfile = smooth(profile, 5)
        val baseline = percentile(smoothProfile, 60.0)
        if (baseline <= 0.0) return listOf(window)
        val low = BooleanArray(width) { smoothProfile[it].toDouble() < config.structureValleyRatio * baseline }
        val runs = mutableListOf<IntArray>()
        var index = 0
        while (index < width) {
            if (!low[index]) { index++; continue }
            var end = index
            while (end < width && low[end]) end++
            runs += intArrayOf(index, end)
            index = end
        }
        val mergeGap = (config.mergeGapRatio * width).toInt()
        val merged = mutableListOf<IntArray>()
        for (run in runs) {
            val last = merged.lastOrNull()
            if (last != null && run[0] - last[1] <= mergeGap) last[1] = run[1] else merged += run.copyOf()
        }
        var best: IntArray? = null
        var bestWidth = 0
        for (run in merged) {
            val gutterWidth = run[1] - run[0]
            if (gutterWidth < config.minGutterWidthRatio * width) continue
            val center = (run[0] + run[1]) / 2.0
            if (center < 0.30 * width || center > 0.70 * width) continue
            var leftMass = 0L
            var rightMass = 0L
            for (x in 0 until run[0]) leftMass += profile[x]
            for (x in run[1] until width) rightMass += profile[x]
            val total = leftMass + rightMass
            if (total <= 0L) continue
            if (leftMass.toDouble() / total < config.minSideMassRatio) continue
            if (rightMass.toDouble() / total < config.minSideMassRatio) continue
            var bothRows = 0
            var rows = 0
            for (y in window.top until window.bottom) {
                val base = y * pageWidth
                var leftInk = false
                var rightInk = false
                for (x in window.left until window.left + run[0]) {
                    if ((gray[base + x].toInt() and 0xFF) < structureThreshold) { leftInk = true; break }
                }
                if (!leftInk) { rows++; continue }
                for (x in window.left + run[1] until window.right) {
                    if ((gray[base + x].toInt() and 0xFF) < structureThreshold) { rightInk = true; break }
                }
                if (rightInk) bothRows++
                rows++
            }
            if (rows == 0 || bothRows.toDouble() / rows < config.minBothSideRowShare) continue
            if (gutterWidth > bestWidth) { bestWidth = gutterWidth; best = run }
        }
        val run = best ?: return listOf(window)
        val left = PixelRect(window.left, window.top, window.left + run[0], window.bottom)
        val right = PixelRect(window.left + run[1], window.top, window.right, window.bottom)
        val widest = maxOf(left.width, right.width)
        if (widest > 0.80 * width) return listOf(window)
        return listOf(left, right)
    }

    /**
     * 题号候选生成（**非分类**，见 [QuestionNumberCandidate] 的实测依据）。
     *
     * 输入是一个栏内的文本块；对每个块取首行探针带（高 `probeHeightRatio × 栏宽`），量出
     * 起笔缩进与首段宽度，再与"贴左缘 / 窄首段 / 块前大间距"三条几何证据比对。
     */
    fun questionNumberCandidates(
        gray: ByteArray,
        pageWidth: Int,
        span: PixelRect,
        blocks: List<PixelRect>,
        threshold: Int,
        config: LayoutConfig,
        columnIndex: Int,
    ): List<QuestionNumberCandidate> {
        val columnWidth = span.width
        if (columnWidth <= 0 || blocks.isEmpty()) return emptyList()
        val gaps = (1 until blocks.size).map { blocks[it].top - blocks[it - 1].bottom }
        val medianGap = if (gaps.isEmpty()) 0.0 else median(gaps)
        val probeRows = maxOf(4, (config.probeHeightRatio * columnWidth).toInt())
        val result = mutableListOf<QuestionNumberCandidate>()
        blocks.forEachIndexed { index, block ->
            val (indent, tokenWidth) = firstLineGeometry(gray, pageWidth, span, block, threshold, probeRows)
            if (tokenWidth <= 0) return@forEachIndexed
            val tokenRatio = tokenWidth.toDouble() / columnWidth
            val indentRatio = indent.toDouble() / columnWidth
            val gapBefore = if (index > 0) block.top - blocks[index - 1].bottom else null
            val gapRatio = if (gapBefore != null && medianGap > 0.0) gapBefore / medianGap else null
            result += QuestionNumberCandidate(
                columnIndex = columnIndex,
                topPx = block.top,
                bottomPx = block.bottom,
                heightPx = block.height,
                indentRatio = indentRatio,
                tokenRatio = tokenRatio,
                gapBeforePx = gapBefore,
                gapRatio = gapRatio,
                railOk = indentRatio <= config.railTolerance,
                tokenOk = tokenRatio in config.tokenMinRatio..config.tokenMaxRatio,
                gapOk = gapRatio != null && gapRatio >= config.gapRatioToMedianGap,
            )
        }
        return result
    }

    /** 块首行的起笔位置与首段宽度（像素，相对栏左缘）。 */
    fun firstLineGeometry(
        gray: ByteArray,
        pageWidth: Int,
        span: PixelRect,
        block: PixelRect,
        threshold: Int,
        probeRows: Int,
    ): Pair<Int, Int> {
        val limit = minOf(block.bottom, block.top + maxOf(3, probeRows))
        if (limit <= block.top) return (span.width) to 0
        val rows = limit - block.top
        val occupied = IntArray(span.width)
        for (y in block.top until limit) {
            val base = y * pageWidth
            for (x in span.left until span.right) {
                if ((gray[base + x].toInt() and 0xFF) < threshold) occupied[x - span.left]++
            }
        }
        var start = -1
        for (x in 0 until span.width) {
            if (occupied[x] > 0) { start = x; break }
        }
        if (start < 0) return (span.width) to 0
        var end = start
        while (end + 1 < span.width && occupied[end + 1] > 0) end++
        if (rows <= 0) return (span.width) to 0
        return start to (end - start + 1)
    }

    /** 块顶 ±24 px 内"行墨 < 0.2×均值"的连续行数（谷宽）。行间空白越厚，值越大。 */
    fun gapWidthPx(gray: ByteArray, pageWidth: Int, span: PixelRect, top: Int,
                   threshold: Int): Int {
        val from = (top - 24).coerceAtLeast(0)
        val to = (top + 24).coerceAtMost(span.bottom)
        if (to <= from) return 0
        val ink = IntArray(to - from)
        var sum = 0L
        for (y in from until to) {
            val base = y * pageWidth
            var count = 0
            for (x in span.left until span.right) {
                if ((gray[base + x].toInt() and 0xFF) < threshold) count++
            }
            ink[y - from] = count
            sum += count
        }
        val mean = sum.toDouble() / ink.size
        if (mean <= 0.0) return ink.size
        val limit = 0.2 * mean
        var best = 0
        var current = 0
        for (value in ink) {
            if (value < limit) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    /** 块首行起笔缩进（像素）。 */
    fun firstLineIndent(gray: ByteArray, pageWidth: Int, span: PixelRect, block: PixelRect,
                        threshold: Int): Int {
        val limit = minOf(block.bottom, block.top + 12)
        for (y in block.top until limit) {
            val base = y * pageWidth
            for (x in span.left until span.right) {
                if ((gray[base + x].toInt() and 0xFF) < threshold) return x - span.left
            }
        }
        return span.width
    }

    private fun smooth(values: IntArray, window: Int): IntArray {
        if (window <= 1 || values.isEmpty()) return values
        val half = window / 2
        val result = IntArray(values.size)
        for (index in values.indices) {
            var sum = 0L
            var count = 0
            for (offset in -half..half) {
                val source = (index + offset).coerceIn(0, values.size - 1)
                sum += values[source]
                count++
            }
            result[index] = (sum / count).toInt()
        }
        return result
    }

    private fun medianWindow(width: Int): Int = maxOf(15, width / 12) or 1

    private fun rollingMedian(values: IntArray, window: Int): IntArray {
        val half = window / 2
        val result = IntArray(values.size)
        val buffer = IntArray(window)
        for (index in values.indices) {
            for (offset in -half..half) {
                val source = (index + offset).coerceIn(0, values.size - 1)
                buffer[offset + half] = values[source]
            }
            buffer.sort()
            result[index] = buffer[window / 2]
        }
        return result
    }

    private fun median(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }

    private fun percentile(values: IntArray, percent: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.copyOf()
        sorted.sort()
        val index = ((sorted.size - 1) * percent / 100.0).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index].toDouble()
    }

    private fun degraded(width: Int, height: Int, window: PixelRect): PageStructure = PageStructure(
        pageWidth = width,
        pageHeight = height,
        contentWindow = window,
        columns = listOf(ColumnSpan(0, width, 0)),
        separators = emptyList(),
        linePitchPx = 0,
        twoColumn = false,
        orientationUncertain = true,
    )
}
