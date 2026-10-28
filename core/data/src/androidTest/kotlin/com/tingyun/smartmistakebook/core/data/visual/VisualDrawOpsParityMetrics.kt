package com.tingyun.smartmistakebook.core.data.visual

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * 屏 / PDF 一致性门的度量与分类（批次 29 T4；R2
 * `docs/research/2026-10-28-screen-pdf-parity-gate.md` §4–§5 的落码形态）。
 *
 * **原型门仪器，不是产品代码。** 本文件只存在于 `androidTest` 源集（R2 §6.4）：IoU / 腐蚀 /
 * 文本剖面是测试仪器，不是产品行为；把它放进 `main` 会为"原型门"扩产品面（R2 §7）。
 * 它**不是** CI 门、**不是**发版门，也**不证明**屏侧画得对——屏侧正确性由 painter 套件
 * （设备 24 例 + JVM 10 例）负责；本门只证"PDF 路径相对屏路径没有引入新偏差"（相对门）。
 *
 * 度量为什么不是"整图差异百分比"（R2 §5.2）：一个大区域的墨全丢仍可能低于 5% 像素差，
 * 单一分数会把"内容缺失"平均掉。这里用两个正交量——`iou`（错几个像素）与 `chanDist`
 * （差几级）——外加文本的 bbox/质心/墨量/剖面，每一个都能手算复核。
 *
 * 判据来源与阈值版本见 [THRESHOLD_VERSION]；所有阈值都是 R2 §4.2 的**初始值**，未在设备上
 * 标定，读数必须与版本号一起进证据（R2 §4.3）。
 */

/** 一张已渲染的栅格（ARGB_8888，行主序）。两侧必须是同一像素网格。 */
internal data class ParityRaster(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
)

/** 文本度量带（像素坐标，闭区间；只在这个带内统计墨量）。 */
internal data class PixelBand(
    val left: Int,
    val top: Int,
    val rightInclusive: Int,
    val bottomInclusive: Int,
)

/** 墨迹包围盒（闭区间像素坐标）。 */
internal data class InkBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** 四条边与另一包围盒的最大绝对偏离（px）；R2 §4.2 的 bbox 判据。 */
    fun maxEdgeDeviationFrom(other: InkBox): Int = maxOf(
        abs(left - other.left),
        abs(top - other.top),
        abs(right - other.right),
        abs(bottom - other.bottom),
    )
}

/** 夹具类与 R2 §4.2 的初始阈值。`null` = 本类不判该量。 */
internal enum class ParityFixtureClass(
    val iouFloor: Double?,
    val interiorFracOver2Limit: Double?,
    val requiresShiftSensitivity: Boolean,
) {
    /** 不透明矢量（无文本、无 alpha）。 */
    VECTOR(iouFloor = 0.990, interiorFracOver2Limit = 0.002, requiresShiftSensitivity = true),

    /** 半透明填充。 */
    ALPHA(iouFloor = 0.990, interiorFracOver2Limit = 0.005, requiresShiftSensitivity = true),

    /** 几何（clip / 矩阵 / stroke）。 */
    GEOMETRY(iouFloor = 0.990, interiorFracOver2Limit = 0.002, requiresShiftSensitivity = true),

    /** 细线 / dash。 */
    DASH(iouFloor = 0.970, interiorFracOver2Limit = 0.005, requiresShiftSensitivity = true),

    /** 非整数缩放（P08）：只判位移与内部色，不判 IoU（R2 §3.2）。 */
    GEOMETRY_NON_INTEGRAL(iouFloor = null, interiorFracOver2Limit = 0.005, requiresShiftSensitivity = true),

    /** 文本：IoU 只报不判，改用 bbox / 质心 / 墨量比 / 剖面 TV（R2 §4.2）。 */
    TEXT(iouFloor = null, interiorFracOver2Limit = null, requiresShiftSensitivity = false),
}

/**
 * 判决分类（R2 §5.2）。`PASS` / `F2_AA` / `F3_ALPHA_ROUNDING` 属于"容差内通过"；
 * `F5_UNATTRIBUTED` 不计入 PASS 也不阻断原型构建（每轮上限 [VisualDrawOpsParityMetrics.MAX_F5_PER_RUN]）。
 *
 * [isRed] 包含 `F1_FONT`：R2 §6.2 的 N05（换字形）允许分类为 F1 或 F4，但**必须红**；
 * 正方夹具若落进 F1 即"同一内容 DTO 的字形覆盖不同"，按真缺陷处理（R2 §5.2 的处置列）。
 */
internal enum class ParityVerdict {
    PASS,
    F1_FONT,
    F2_AA,
    F3_ALPHA_ROUNDING,
    F4_GEOMETRY,
    F4_COLOR,
    F4_SEMANTIC,
    F5_UNATTRIBUTED,
    ;

    val isRed: Boolean
        get() = this == F1_FONT || this == F4_GEOMETRY || this == F4_COLOR || this == F4_SEMANTIC
}

/** 文本类补充读数（R2 §4.1）。 */
internal data class TextParityMetrics(
    val screenBox: InkBox?,
    val pdfBox: InkBox?,
    /** 四边最大偏离；任一侧无墨 → `Int.MAX_VALUE`（按红处理，不降级 F5）。 */
    val bboxDeviationPx: Int,
    /** 墨量加权质心的欧氏距离（px）；任一侧墨量为 0 → `NaN`。 */
    val centroidDeltaPx: Double,
    /** `Σmass_P / Σmass_S`；屏侧墨量为 0 → `NaN`。 */
    val massRatio: Double,
    /** 列 / 行墨量剖面归一后的全变差（TV ∈ [0,1]）；任一侧墨量为 0 → `NaN`。 */
    val columnTv: Double,
    val rowTv: Double,
    val screenMass: Double,
    val pdfMass: Double,
)

/** 一次对照的全部可复算读数（R2 §4.1）。 */
internal data class ParityMetrics(
    val width: Int,
    val height: Int,
    /** 非背景掩膜 IoU；两侧并集为空 → `NaN`（由判决层折 PARITY_EMPTY_MASK）。 */
    val iou: Double,
    val screenMaskPx: Int,
    val pdfMaskPx: Int,
    val unionMaskPx: Int,
    /** 在 `[-3,3]²` 搜索到的最优整像素位移（以屏掩膜为基准采样 PDF 掩膜）。 */
    val bestShiftX: Int,
    val bestShiftY: Int,
    val bestShiftIou: Double,
    /** `(0,0)` 四个邻域的最优 IoU（R2 §4.4 的 `iou1`）。 */
    val neighborBestIou: Double,
    val interiorPx: Int,
    val interiorMax: Int,
    val interiorP50: Int,
    val interiorP95: Int,
    val interiorP99: Int,
    /** `count(p ∈ E, chanDist > 2) / |E|`。 */
    val interiorFracOver2: Double,
    val edgePx: Int,
    val edgeMean: Double,
    val nonzeroInteriorPx: Int,
    /** `E` 内非零差中"主导通道同号"的占比（F3 判据，R2 §5.2）。 */
    val sameSignDominantRatio: Double,
    val alphaMismatchCount: Int,
    /** 首个内部超阈像素（`chanDist > 2`）；无则 -1。 */
    val firstOverX: Int,
    val firstOverY: Int,
    val firstOverScreenArgb: Int,
    val firstOverPdfArgb: Int,
    val text: TextParityMetrics?,
) {
    /** R2 §4.4 的 `iou0 − iou1`：夹具对 1 px 位移的灵敏度。 */
    val shiftSensitivityGain: Double get() = iou - neighborBestIou
}

/** 判决结果：分类 + 可读原因（原因进 logcat 报告，不参与"看起来像"）。 */
internal data class ParityDecision(
    val verdict: ParityVerdict,
    val reasons: List<String>,
)

/** 前置门失败（R2 §3.3）：**没有判决**，不是"失败"；测试以该码报红。 */
internal class ParityGateException(
    val code: String,
    message: String,
) : Exception("$code: $message")

internal const val PARITY_EMPTY_MASK = "PARITY_EMPTY_MASK"
internal const val PARITY_INSENSITIVE_FIXTURE = "PARITY_INSENSITIVE_FIXTURE"
internal const val PARITY_SCREEN_NONDETERMINISTIC = "PARITY_INSTRUMENT_SCREEN_NONDETERMINISTIC"
internal const val PARITY_PDF_RASTER_NONDETERMINISTIC = "PARITY_INSTRUMENT_PDF_RASTER_NONDETERMINISTIC"
internal const val PARITY_PDF_REBUILD_NONDETERMINISTIC = "PARITY_INSTRUMENT_PDF_REBUILD_NONDETERMINISTIC"
internal const val PARITY_GATE_DESENSITIZED = "PARITY_GATE_DESENSITIZED"
internal const val PARITY_SYSTEMATIC_DRIFT = "PARITY_SYSTEMATIC_DRIFT"

/** 诊断通道（N02 用）。 */
internal enum class ParityChannel { RED, GREEN, BLUE }

internal object VisualDrawOpsParityMetrics {

    /** R2 §4.2 的初始阈值版本；读数必须与它一起落证据（R2 §4.3-4）。 */
    const val THRESHOLD_VERSION = "R2-2026-10-28/§4.2-initial-uncalibrated"

    /** R2 §4.1：非背景掩膜阈值（噪声带 ≤2，2.4% 覆盖率 ≈6）。 */
    const val MASK_BACKGROUND_DISTANCE = 6

    /** R2 §4.1：内部色差容差（Paparazzi OffByTwo 的独立先例）。 */
    const val INTERIOR_COLOR_TOLERANCE = 2

    /** R2 §4.1：整像素位移搜索半径。 */
    const val SHIFT_SEARCH_RADIUS = 3

    /** R2 §4.4：几何夹具对 1 px 位移的最小灵敏度。 */
    const val MIN_SHIFT_SENSITIVITY_GAIN = 0.02

    /** R2 §5.2 F3：同号主导阈值。 */
    const val SAME_SIGN_DOMINANCE = 0.60

    /**
     * "边缘带显著"的**分类用**常量（不是通过阈值）：只决定报告里 PASS 与 F2_AA 的标签，
     * 两者都不阻断原型门。R2 §5.1-d 只写"显著"，未给数值；取 1 级（一个颜色通道单位）。
     */
    const val EDGE_SIGNIFICANT_MEAN = 1.0

    /** R2 §5.3：每轮最多 2 个 F5；第 3 个是系统性漂移。 */
    const val MAX_F5_PER_RUN = 2

    /**
     * R2 §5.3 隔离带的**色差幅度**上界（级）。
     *
     * 幅度之外还要求越界像素占比 ≤ 夹具类自身的 [ParityFixtureClass.interiorFracOver2Limit]
     * ——隔离带只吸收**少量孤立像素**的越界。整块同向偏色是系统性错色，不得降级为 F5
     * 而绕过红线（复核第 1 轮 R8：旧实现只看 interiorMax∈(2,8] 就降级，整块偏 3~8 级
     * 的填充会以 IoU≈1、无位移的姿态被当成"不可归因"放行）。
     */
    const val ISOLATION_COLOR_MAX = 8

    /** 浮点比较余量（搜索平局时保留 (0,0)）。 */
    private const val TIE_EPSILON = 1e-12

    /**
     * 两侧像素先合成到不透明背景（R2 §3.4-3）：
     * `out_c = (c·a + bg_c·(255−a) + 127) / 255`。
     *
     * 这是**门自己的**取整口径（与任一侧渲染器无关），消灭"α≠255 的像素让掩膜/色差
     * 不可比"——PDF 写侧没有页 alpha 通道，两侧必须落到同一不透明基准。
     */
    fun compositeOver(source: ParityRaster, backdrop: Int): ParityRaster {
        val out = IntArray(source.pixels.size)
        val bgR = (backdrop ushr 16) and 0xFF
        val bgG = (backdrop ushr 8) and 0xFF
        val bgB = backdrop and 0xFF
        for (index in source.pixels.indices) {
            val color = source.pixels[index]
            val alpha = (color ushr 24) and 0xFF
            if (alpha == 0xFF) {
                out[index] = color
                continue
            }
            val red = (color ushr 16) and 0xFF
            val green = (color ushr 8) and 0xFF
            val blue = color and 0xFF
            val inverse = 255 - alpha
            val composedRed = (red * alpha + bgR * inverse + 127) / 255
            val composedGreen = (green * alpha + bgG * inverse + 127) / 255
            val composedBlue = (blue * alpha + bgB * inverse + 127) / 255
            out[index] = (0xFF shl 24) or
                (composedRed shl 16) or
                (composedGreen shl 8) or
                composedBlue
        }
        return ParityRaster(source.width, source.height, out)
    }

    /** 全部读数（R2 §4.1）。[band] 只对文本夹具给出。 */
    fun measure(
        screen: ParityRaster,
        pdf: ParityRaster,
        backdrop: Int,
        band: PixelBand? = null,
    ): ParityMetrics {
        require(screen.width == pdf.width && screen.height == pdf.height) {
            "parity rasters must share the pixel grid: ${screen.width}x${screen.height} vs ${pdf.width}x${pdf.height}"
        }
        val width = screen.width
        val height = screen.height
        val size = width * height
        val screenOpaque = compositeOver(screen, backdrop).pixels
        val pdfOpaque = compositeOver(pdf, backdrop).pixels

        val screenMask = BooleanArray(size) {
            backgroundDistance(screenOpaque[it], backdrop) > MASK_BACKGROUND_DISTANCE
        }
        val pdfMask = BooleanArray(size) {
            backgroundDistance(pdfOpaque[it], backdrop) > MASK_BACKGROUND_DISTANCE
        }
        val screenMaskPx = screenMask.count { it }
        val pdfMaskPx = pdfMask.count { it }
        val unionMaskPx = (0 until size).count { screenMask[it] || pdfMask[it] }
        val iouAtZero = iou(screenMask, pdfMask)

        var bestShiftX = 0
        var bestShiftY = 0
        var bestShiftIou = iouAtZero
        for (dy in -SHIFT_SEARCH_RADIUS..SHIFT_SEARCH_RADIUS) {
            for (dx in -SHIFT_SEARCH_RADIUS..SHIFT_SEARCH_RADIUS) {
                val candidate = shiftedIou(screenMask, pdfMask, width, height, dx, dy)
                if (candidate.isNaN()) continue
                if (bestShiftIou.isNaN() || candidate > bestShiftIou + TIE_EPSILON) {
                    bestShiftIou = candidate
                    bestShiftX = dx
                    bestShiftY = dy
                }
            }
        }
        var neighborBestIou = Double.NaN
        for (offset in NEIGHBOR_OFFSETS) {
            val candidate = shiftedIou(screenMask, pdfMask, width, height, offset[0], offset[1])
            if (candidate.isNaN()) continue
            if (neighborBestIou.isNaN() || candidate > neighborBestIou) {
                neighborBestIou = candidate
            }
        }

        // 内部区 = 两侧掩膜各自腐蚀 1 次后的交集（R2 §4.1：单侧腐蚀会把"PDF 多出的一块墨"
        // 整块漏进内部统计；用交集才能把 AA 边缘排除干净）。
        val erodedScreen = erode(screenMask, width, height)
        val erodedPdf = erode(pdfMask, width, height)
        val interior = BooleanArray(size) { erodedScreen[it] && erodedPdf[it] }
        var interiorPx = 0
        var interiorMax = 0
        var overTolerance = 0
        var positiveDominant = 0
        var negativeDominant = 0
        var firstOverX = -1
        var firstOverY = -1
        var firstOverScreen = 0
        var firstOverPdf = 0
        val interiorDistances = ArrayList<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!interior[index]) continue
                interiorPx += 1
                val distance = channelDistance(screenOpaque[index], pdfOpaque[index])
                interiorDistances.add(distance)
                if (distance > interiorMax) interiorMax = distance
                if (distance > INTERIOR_COLOR_TOLERANCE) {
                    overTolerance += 1
                    if (firstOverX < 0) {
                        firstOverX = x
                        firstOverY = y
                        firstOverScreen = screenOpaque[index]
                        firstOverPdf = pdfOpaque[index]
                    }
                }
                if (distance > 0) {
                    if (dominantChannelSign(screenOpaque[index], pdfOpaque[index]) >= 0) {
                        positiveDominant += 1
                    } else {
                        negativeDominant += 1
                    }
                }
            }
        }
        val nonzeroInteriorPx = positiveDominant + negativeDominant
        val sameSignDominantRatio = if (nonzeroInteriorPx == 0) {
            0.0
        } else {
            max(positiveDominant, negativeDominant).toDouble() / nonzeroInteriorPx
        }

        // 边缘带 = 两侧并集的 1 px 膨胀去掉内部区（只作 F2 诊断，不单独判死）。
        val unionMask = BooleanArray(size) { screenMask[it] || pdfMask[it] }
        val dilatedUnion = dilate(unionMask, width, height)
        var edgePx = 0
        var edgeSum = 0L
        for (index in 0 until size) {
            if (dilatedUnion[index] && !interior[index]) {
                edgePx += 1
                edgeSum += channelDistance(screenOpaque[index], pdfOpaque[index])
            }
        }
        val edgeMean = if (edgePx == 0) 0.0 else edgeSum.toDouble() / edgePx

        val sortedDistances = interiorDistances.toIntArray().apply { sort() }

        var alphaMismatchCount = 0
        for (index in 0 until size) {
            if (((screen.pixels[index] ushr 24) and 0xFF) != 0xFF) alphaMismatchCount += 1
            if (((pdf.pixels[index] ushr 24) and 0xFF) != 0xFF) alphaMismatchCount += 1
        }

        val text = band?.let {
            measureText(screenOpaque, pdfOpaque, screenMask, pdfMask, backdrop, it, width, height)
        }

        return ParityMetrics(
            width = width,
            height = height,
            iou = iouAtZero,
            screenMaskPx = screenMaskPx,
            pdfMaskPx = pdfMaskPx,
            unionMaskPx = unionMaskPx,
            bestShiftX = bestShiftX,
            bestShiftY = bestShiftY,
            bestShiftIou = bestShiftIou,
            neighborBestIou = neighborBestIou,
            interiorPx = interiorPx,
            interiorMax = interiorMax,
            interiorP50 = percentile(sortedDistances, 0.50),
            interiorP95 = percentile(sortedDistances, 0.95),
            interiorP99 = percentile(sortedDistances, 0.99),
            interiorFracOver2 = if (interiorPx == 0) 0.0 else overTolerance.toDouble() / interiorPx,
            edgePx = edgePx,
            edgeMean = edgeMean,
            nonzeroInteriorPx = nonzeroInteriorPx,
            sameSignDominantRatio = sameSignDominantRatio,
            alphaMismatchCount = alphaMismatchCount,
            firstOverX = firstOverX,
            firstOverY = firstOverY,
            firstOverScreenArgb = firstOverScreen,
            firstOverPdfArgb = firstOverPdf,
            text = text,
        )
    }

    /**
     * R2 §5.1 判决决策树。前置门（空掩膜）以 [PARITY_EMPTY_MASK] 抛出——那是"没有判决"，
     * 不是失败。隔离带（R2 §5.3）优先于 F4：带内的越界读数降为 [ParityVerdict.F5_UNATTRIBUTED]，
     * 超出隔离带的越界读数保持红（"默认真错，不允许降级成 F5"）。
     */
    fun classify(fixtureClass: ParityFixtureClass, metrics: ParityMetrics): ParityDecision {
        if (metrics.unionMaskPx == 0 || metrics.screenMaskPx < MIN_SCREEN_MASK_PX) {
            throw ParityGateException(
                PARITY_EMPTY_MASK,
                "fixture produced an empty or tiny ink mask " +
                    "(screen=${metrics.screenMaskPx}, pdf=${metrics.pdfMaskPx}, union=${metrics.unionMaskPx})",
            )
        }
        val reasons = ArrayList<String>()
        val raw = when (fixtureClass) {
            ParityFixtureClass.TEXT -> rawTextVerdict(metrics, reasons)
            else -> rawGeometricVerdict(fixtureClass, metrics, reasons)
        }
        val verdict = if (raw.isRed && isolationBandOnly(fixtureClass, metrics)) {
            reasons.add("isolation-band (R2 §5.3)")
            ParityVerdict.F5_UNATTRIBUTED
        } else {
            raw
        }
        return ParityDecision(verdict = verdict, reasons = reasons)
    }

    /** 诊断通道移位（N01）：内容整体平移 `dx/dy` px，空出的像素为透明。 */
    fun shift(source: ParityRaster, dx: Int, dy: Int): ParityRaster {
        val out = IntArray(source.pixels.size)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val sourceX = x - dx
                val sourceY = y - dy
                if (sourceX in 0 until source.width && sourceY in 0 until source.height) {
                    out[y * source.width + x] = source.pixels[sourceY * source.width + sourceX]
                }
            }
        }
        return ParityRaster(source.width, source.height, out)
    }

    /** 诊断通道加值（N02）：只改目标通道，α 不变；越界夹到 0..255。 */
    fun addToChannel(source: ParityRaster, channel: ParityChannel, delta: Int): ParityRaster {
        val shift = when (channel) {
            ParityChannel.RED -> 16
            ParityChannel.GREEN -> 8
            ParityChannel.BLUE -> 0
        }
        val out = IntArray(source.pixels.size)
        for (index in source.pixels.indices) {
            val color = source.pixels[index]
            val value = (color ushr shift) and 0xFF
            val updated = value + delta
            val clamped = if (updated < 0) 0 else if (updated > 0xFF) 0xFF else updated
            val cleared = color and (0xFF shl shift).inv()
            out[index] = cleared or (clamped shl shift)
        }
        return ParityRaster(source.width, source.height, out)
    }

    /** 失败诊断图（R2 §6.3）：0 差为黑、≤2 为灰、>2 为红。判决从不读 PNG。 */
    fun diffRaster(screen: ParityRaster, pdf: ParityRaster): ParityRaster {
        require(screen.width == pdf.width && screen.height == pdf.height) { "diff rasters need one grid" }
        val out = IntArray(screen.pixels.size)
        for (index in out.indices) {
            val distance = channelDistance(screen.pixels[index], pdf.pixels[index])
            out[index] = when {
                distance == 0 -> 0xFF000000.toInt()
                distance > INTERIOR_COLOR_TOLERANCE -> 0xFFFF0000.toInt()
                else -> {
                    val grey = (distance * 0x7F) / INTERIOR_COLOR_TOLERANCE
                    (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
                }
            }
        }
        return ParityRaster(screen.width, screen.height, out)
    }

    private const val MIN_SCREEN_MASK_PX = 32

    private val NEIGHBOR_OFFSETS = arrayOf(
        intArrayOf(-1, 0),
        intArrayOf(1, 0),
        intArrayOf(0, -1),
        intArrayOf(0, 1),
    )

    private fun rawGeometricVerdict(
        fixtureClass: ParityFixtureClass,
        metrics: ParityMetrics,
        reasons: MutableList<String>,
    ): ParityVerdict {
        val iouFloor = fixtureClass.iouFloor
        var geometryViolation = false
        if (iouFloor != null && (metrics.iou.isNaN() || metrics.iou < iouFloor)) {
            geometryViolation = true
            reasons.add("iou=${metrics.iou} < $iouFloor")
        }
        val shiftGain = metrics.bestShiftIou - metrics.iou
        if ((metrics.bestShiftX != 0 || metrics.bestShiftY != 0) &&
            (shiftGain.isNaN() || shiftGain > MIN_SHIFT_SENSITIVITY_GAIN)
        ) {
            geometryViolation = true
            reasons.add(
                "bestShift=(${metrics.bestShiftX},${metrics.bestShiftY}) gain=${shiftGain}",
            )
        }
        if (geometryViolation) return ParityVerdict.F4_GEOMETRY

        val fracLimit = fixtureClass.interiorFracOver2Limit
        if (metrics.interiorMax > INTERIOR_COLOR_TOLERANCE ||
            (fracLimit != null && metrics.interiorFracOver2 > fracLimit)
        ) {
            reasons.add(
                "interiorMax=${metrics.interiorMax} fracOver2=${metrics.interiorFracOver2} limit=$fracLimit",
            )
            return ParityVerdict.F4_COLOR
        }
        if (metrics.edgeMean >= EDGE_SIGNIFICANT_MEAN) {
            reasons.add("edgeMean=${metrics.edgeMean} (AA band only)")
            return ParityVerdict.F2_AA
        }
        if (metrics.interiorMax in 1..INTERIOR_COLOR_TOLERANCE &&
            metrics.sameSignDominantRatio >= SAME_SIGN_DOMINANCE
        ) {
            reasons.add(
                "same-sign α rounding: max=${metrics.interiorMax} ratio=${metrics.sameSignDominantRatio}",
            )
            return ParityVerdict.F3_ALPHA_ROUNDING
        }
        if (metrics.iou.isNaN()) {
            reasons.add("non-finite iou with non-empty mask")
            return ParityVerdict.F4_SEMANTIC
        }
        return ParityVerdict.PASS
    }

    private fun rawTextVerdict(metrics: ParityMetrics, reasons: MutableList<String>): ParityVerdict {
        val text = metrics.text
            ?: throw IllegalArgumentException("TEXT fixtures must supply a text band for measure()")
        if (text.screenBox == null || text.pdfBox == null) {
            reasons.add("text ink missing on one side (screen=${text.screenBox}, pdf=${text.pdfBox})")
            return ParityVerdict.F4_GEOMETRY
        }
        if (text.bboxDeviationPx > MAX_TEXT_BBOX_DEVIATION_PX ||
            !(text.centroidDeltaPx <= MAX_TEXT_CENTROID_DELTA_PX)
        ) {
            reasons.add(
                "text bboxDev=${text.bboxDeviationPx}px centroid=${text.centroidDeltaPx}px",
            )
            return ParityVerdict.F4_GEOMETRY
        }
        if (!(text.massRatio in MIN_TEXT_MASS_RATIO..MAX_TEXT_MASS_RATIO) ||
            text.columnTv > MAX_TEXT_PROFILE_TV ||
            text.rowTv > MAX_TEXT_PROFILE_TV
        ) {
            reasons.add(
                "text massRatio=${text.massRatio} tvColumn=${text.columnTv} tvRow=${text.rowTv}",
            )
            return ParityVerdict.F1_FONT
        }
        if (metrics.edgeMean >= EDGE_SIGNIFICANT_MEAN ||
            (!metrics.iou.isNaN() && metrics.iou < TEXT_REPORTED_IOU_LIMIT)
        ) {
            reasons.add("text AA band only: edgeMean=${metrics.edgeMean} iou=${metrics.iou}")
            return ParityVerdict.F2_AA
        }
        return ParityVerdict.PASS
    }

    /**
     * R2 §5.3：只有**全部**越界读数都落在隔离带内时才允许 F5。
     * 任何不带隔离带的越界（位移、质心、剖面 TV）都保持红——"不可归因 ≠ 忽略"。
     *
     * 色差面另加**占比**条件：`interiorMax` 在 `(2, 8]` 只是必要条件，超容差像素还必须
     * 不超过该夹具类自己的 `interiorFracOver2Limit`。否则"整块统一偏 3~8 级"会被当成
     * 孤立噪声降级成 F5（复核第 1 轮 R8）。
     */
    private fun isolationBandOnly(fixtureClass: ParityFixtureClass, metrics: ParityMetrics): Boolean {
        if (fixtureClass == ParityFixtureClass.TEXT) {
            val text = metrics.text ?: return false
            val bboxViolated = text.bboxDeviationPx > MAX_TEXT_BBOX_DEVIATION_PX
            val bboxInBand = text.bboxDeviationPx <= 2
            val centroidViolated = !(text.centroidDeltaPx <= MAX_TEXT_CENTROID_DELTA_PX)
            val massViolated = !(text.massRatio in MIN_TEXT_MASS_RATIO..MAX_TEXT_MASS_RATIO)
            val massInBand = (text.massRatio >= 0.90 && text.massRatio < MIN_TEXT_MASS_RATIO) ||
                (text.massRatio > MAX_TEXT_MASS_RATIO && text.massRatio <= 1.10)
            val tvViolated = text.columnTv > MAX_TEXT_PROFILE_TV || text.rowTv > MAX_TEXT_PROFILE_TV
            val anyViolation = bboxViolated || centroidViolated || massViolated || tvViolated
            return anyViolation &&
                !centroidViolated &&
                !tvViolated &&
                (!bboxViolated || bboxInBand) &&
                (!massViolated || massInBand)
        }
        val iouFloor = fixtureClass.iouFloor
        val iouViolated = iouFloor != null && (metrics.iou.isNaN() || metrics.iou < iouFloor)
        val iouInBand = iouFloor != null && !metrics.iou.isNaN() && metrics.iou >= iouFloor - 0.01
        val shiftViolated = (metrics.bestShiftX != 0 || metrics.bestShiftY != 0) &&
            (metrics.bestShiftIou - metrics.iou).let { it.isNaN() || it > MIN_SHIFT_SENSITIVITY_GAIN }
        val fracLimit = fixtureClass.interiorFracOver2Limit
        val interiorViolated = metrics.interiorMax > INTERIOR_COLOR_TOLERANCE ||
            (fracLimit != null && metrics.interiorFracOver2 > fracLimit)
        // 隔离带 = 幅度上界 + 占比上界：只有少量内部像素可以略超容差；整块同向偏色
        // （fracOver2≈1）保持 F4_COLOR 红。fracLimit=null 的类不在带内（默认严格）。
        val interiorInBand = metrics.interiorMax in (INTERIOR_COLOR_TOLERANCE + 1)..ISOLATION_COLOR_MAX &&
            fracLimit != null &&
            metrics.interiorFracOver2 <= fracLimit
        val anyViolation = iouViolated || shiftViolated || interiorViolated
        return anyViolation &&
            !shiftViolated &&
            (!iouViolated || iouInBand) &&
            (!interiorViolated || interiorInBand)
    }

    private fun measureText(
        screen: IntArray,
        pdf: IntArray,
        screenMask: BooleanArray,
        pdfMask: BooleanArray,
        backdrop: Int,
        band: PixelBand,
        width: Int,
        height: Int,
    ): TextParityMetrics {
        require(band.left >= 0 && band.top >= 0 && band.rightInclusive < width && band.bottomInclusive < height) {
            "text band $band must lie inside the ${width}x$height grid"
        }
        val backdropLuma = luma(backdrop).coerceAtLeast(1)
        var screenMass = 0.0
        var pdfMass = 0.0
        var screenCentroidX = 0.0
        var screenCentroidY = 0.0
        var pdfCentroidX = 0.0
        var pdfCentroidY = 0.0
        val columnCount = band.rightInclusive - band.left + 1
        val rowCount = band.bottomInclusive - band.top + 1
        val screenColumns = DoubleArray(columnCount)
        val pdfColumns = DoubleArray(columnCount)
        val screenRows = DoubleArray(rowCount)
        val pdfRows = DoubleArray(rowCount)
        for (y in band.top..band.bottomInclusive) {
            for (x in band.left..band.rightInclusive) {
                val index = y * width + x
                val screenMassAt = massAt(screen[index], backdropLuma)
                val pdfMassAt = massAt(pdf[index], backdropLuma)
                screenMass += screenMassAt
                pdfMass += pdfMassAt
                screenCentroidX += x * screenMassAt
                screenCentroidY += y * screenMassAt
                pdfCentroidX += x * pdfMassAt
                pdfCentroidY += y * pdfMassAt
                screenColumns[x - band.left] += screenMassAt
                pdfColumns[x - band.left] += pdfMassAt
                screenRows[y - band.top] += screenMassAt
                pdfRows[y - band.top] += pdfMassAt
            }
        }
        val screenBox = bbox(screenMask, band, width)
        val pdfBox = bbox(pdfMask, band, width)
        val bboxDeviation = if (screenBox == null || pdfBox == null) {
            Int.MAX_VALUE
        } else {
            screenBox.maxEdgeDeviationFrom(pdfBox)
        }
        val centroidDelta = if (screenMass > 0.0 && pdfMass > 0.0) {
            hypot(pdfCentroidX / pdfMass - screenCentroidX / screenMass, pdfCentroidY / pdfMass - screenCentroidY / screenMass)
        } else {
            Double.NaN
        }
        val massRatio = if (screenMass > 0.0) pdfMass / screenMass else Double.NaN
        return TextParityMetrics(
            screenBox = screenBox,
            pdfBox = pdfBox,
            bboxDeviationPx = bboxDeviation,
            centroidDeltaPx = centroidDelta,
            massRatio = massRatio,
            columnTv = profileTv(screenColumns, pdfColumns),
            rowTv = profileTv(screenRows, pdfRows),
            screenMass = screenMass,
            pdfMass = pdfMass,
        )
    }

    private fun profileTv(screen: DoubleArray, pdf: DoubleArray): Double {
        val screenSum = screen.sum()
        val pdfSum = pdf.sum()
        if (screenSum <= 0.0 || pdfSum <= 0.0) return Double.NaN
        var totalVariation = 0.0
        for (index in screen.indices) {
            totalVariation += abs(screen[index] / screenSum - pdf[index] / pdfSum)
        }
        return 0.5 * totalVariation
    }

    private fun bbox(mask: BooleanArray, band: PixelBand, width: Int): InkBox? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in band.top..band.bottomInclusive) {
            for (x in band.left..band.rightInclusive) {
                if (!mask[y * width + x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else InkBox(left, top, right, bottom)
    }

    private fun massAt(color: Int, backdropLuma: Int): Double {
        val ink = 1.0 - luma(color).toDouble() / backdropLuma
        return if (ink < 0.0) 0.0 else ink
    }

    private fun luma(color: Int): Int {
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        return (299 * red + 587 * green + 114 * blue) / 1000
    }

    /** R2 §4.1 的 `chanDist = max(|ΔR|,|ΔG|,|ΔB|)`（α 已被 [compositeOver] 消去）。 */
    private fun channelDistance(first: Int, second: Int): Int {
        val red = abs(((first ushr 16) and 0xFF) - ((second ushr 16) and 0xFF))
        val green = abs(((first ushr 8) and 0xFF) - ((second ushr 8) and 0xFF))
        val blue = abs((first and 0xFF) - (second and 0xFF))
        return max(red, max(green, blue))
    }

    private fun backgroundDistance(color: Int, backdrop: Int): Int = channelDistance(color, backdrop)

    private fun dominantChannelSign(first: Int, second: Int): Int {
        val redDelta = ((second ushr 16) and 0xFF) - ((first ushr 16) and 0xFF)
        val greenDelta = ((second ushr 8) and 0xFF) - ((first ushr 8) and 0xFF)
        val blueDelta = (second and 0xFF) - (first and 0xFF)
        val absoluteRed = abs(redDelta)
        val absoluteGreen = abs(greenDelta)
        val absoluteBlue = abs(blueDelta)
        val dominant = when {
            absoluteRed >= absoluteGreen && absoluteRed >= absoluteBlue -> redDelta
            absoluteGreen >= absoluteBlue -> greenDelta
            else -> blueDelta
        }
        return if (dominant >= 0) 1 else -1
    }

    private fun percentile(sorted: IntArray, fraction: Double): Int {
        if (sorted.isEmpty()) return 0
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** 并集做分母（R2 §6.4 的自证：用交集当分母时 N01 不会红）。 */
    internal fun iou(first: BooleanArray, second: BooleanArray): Double {
        require(first.size == second.size) { "masks must share a grid" }
        var firstCount = 0
        var secondCount = 0
        var intersection = 0
        for (index in first.indices) {
            val inFirst = first[index]
            val inSecond = second[index]
            if (inFirst) firstCount += 1
            if (inSecond) secondCount += 1
            if (inFirst && inSecond) intersection += 1
        }
        val union = firstCount + secondCount - intersection
        return if (union == 0) Double.NaN else intersection.toDouble() / union
    }

    /**
     * 把 PDF 掩膜按 `(dx,dy)` 采样后与屏掩膜求 IoU：比较 `S(x,y)` 与 `P(x+dx,y+dy)`。
     * 因此"PDF 内容整体右移 1 px"的最优读数是 `(1,0)`（与 R2 §6.2 N01 的报告同号）。
     */
    internal fun shiftedIou(
        screen: BooleanArray,
        pdf: BooleanArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
    ): Double {
        var screenCount = 0
        var pdfCount = 0
        var intersection = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val inScreen = screen[y * width + x]
                val pdfX = x + dx
                val pdfY = y + dy
                val inPdf = pdfX in 0 until width && pdfY in 0 until height && pdf[pdfY * width + pdfX]
                if (inScreen) screenCount += 1
                if (inPdf) pdfCount += 1
                if (inScreen && inPdf) intersection += 1
            }
        }
        val union = screenCount + pdfCount - intersection
        return if (union == 0) Double.NaN else intersection.toDouble() / union
    }

    /** 4 邻域腐蚀 1 次；边界像素视为不可腐蚀（R2 §6.4 的边界不越界要求）。 */
    internal fun erode(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (!mask[index]) continue
                if (mask[index - 1] && mask[index + 1] && mask[index - width] && mask[index + width]) {
                    out[index] = true
                }
            }
        }
        return out
    }

    internal fun dilate(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (mask[index] ||
                    (x > 0 && mask[index - 1]) ||
                    (x < width - 1 && mask[index + 1]) ||
                    (y > 0 && mask[index - width]) ||
                    (y < height - 1 && mask[index + width])
                ) {
                    out[index] = true
                }
            }
        }
        return out
    }

    private const val MAX_TEXT_BBOX_DEVIATION_PX = 1
    private const val MAX_TEXT_CENTROID_DELTA_PX = 0.5
    private const val MIN_TEXT_MASS_RATIO = 0.95
    private const val MAX_TEXT_MASS_RATIO = 1.05
    private const val MAX_TEXT_PROFILE_TV = 0.08
    private const val TEXT_REPORTED_IOU_LIMIT = 0.99
}
