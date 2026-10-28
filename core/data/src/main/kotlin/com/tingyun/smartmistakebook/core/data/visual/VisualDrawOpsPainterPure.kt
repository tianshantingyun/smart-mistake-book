package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.MathBox
import kotlin.math.floor

/**
 * DrawOps V1 painter 的**纯计算层**（批次 28 T2；R1 `docs/research/2026-10-28-painter-design.md`
 * §3.1 分层裁定）。
 *
 * **文件级结构不变量：本文件零 android 导入。** app/core:data 的 JVM 单测没有 Robolectric
 * （`VisualContentMeasuring.kt:171-179` seam 的存在理由），painter 的三个错位陷阱——矩阵
 * `[a,c,e,b,d,f]` 列序、颜色 `#RRGGBB(AA)` 严格词表、alpha floor 取整——只有落在本文件这样
 * 无 android 符号的纯函数里，JVM 门才钉得住（R1 §3.8"纯计算层分离"行）。Canvas/Paint/Path
 * 应用层在 [VisualDrawOpsPainter]，归设备轨。
 *
 * 本层与消费类型同为 `internal`：painter 尚未进公共 API（`VisualDrawOps.kt:13-14` 可见性
 * 裁定同款——同一模块内 `internal` 直达，无需提升）。
 */

/** painter 的 fail-closed 有码异常（R1 §3.7：未知指令/非法输入 = 抛有码异常，调用方据此丢弃本次渲染）。 */
internal class VisualDrawOpsPaintingException(
    val code: String,
    message: String,
) : Exception(message)

/** 码面（R1 §3.7 六码 + 本批增补两码，各自指认消灭的失败）： */
// 文档 schema 门：消灭"旧 schema 文档喂新 painter 被静默错画"（与校验器同值门
// `VisualDrawOpsValidator.kt:80-84`、引擎 `DRAW_SCHEMA_VERSION` 三侧镜像，改值必须同改）。
internal const val PAINTER_SCHEMA_VERSION = "PAINTER_SCHEMA_VERSION"

// 状态栈门：消灭"绕过校验器的未配平手工文档 → Canvas.restore() 静默 no-op 造成状态腐蚀"
// （"painter 不自愈" `VisualDrawOps.kt:139` 的守门形态：不修，只拒）。
internal const val PAINTER_BALANCE = "PAINTER_BALANCE"

// 内容可达门：消灭"冻结引用在绘制时不可解析 → label 静默消失"（冻结性与绘制时可达是
// 两道不同时刻的门，R1 §3.6）。
internal const val PAINTER_UNKNOWN_LABEL = "PAINTER_UNKNOWN_LABEL"

// 描边颜色门：消灭"stroke-only 路径无颜色来源仍被画"（`VisualStrokeSpec` 无 color，
// stroke 色取 fill 色是 V1 过渡裁定）。**批次 29 T5（KD-78 ①）起两道生成门
// （`visual-compiler.js` 与 `VisualDrawOpsParser`）前置同码拒绝**；本门保留为回放侧兜底
// （hand-built / 绕过生成门的文档仍拒，不自愈、不默认色）。
internal const val PAINTER_STROKE_WITHOUT_FILL = "PAINTER_STROKE_WITHOUT_FILL"

// dash 形状门（偶数且 ≥2）：消灭"奇数/不足 2 项 dash 进 DashPathEffect"（AOSP javadoc
// 契约要求偶数个 interval，奇数进 native 行为无规约；length<2 被构造器直接抛**无码**
// ArrayIndexOutOfBoundsException——aosp-mirror DashPathEffect.java:34-39，2026-10-28 复核
// gh api 实读。2026-10-28 复核修复把最小 2 项下界收进本门）。**批次 29 T5（KD-78 ②）起
// 两道生成门前置同码拒绝**；本门保留为回放侧兜底。
internal const val PAINTER_DASH_ODD = "PAINTER_DASH_ODD"

// math 内容门：消灭"math 盒被当 text 画（几何错乱）或被静默跳过（内容消失）"（本批未实现
// 盒走查；盒 Canvas 映射须与 core/ui Compose lowering 逐点一致，归回放/PDF 批，R1 §3.6）。
internal const val PAINTER_LABEL_MATH = "PAINTER_LABEL_MATH"

// 数值域门：消灭"Double→Float 溢出/非有限值静默进 Skia 产生未定义栅格化"（校验器 Double
// ±1e5 在 Float 域内安全，此门只防未来界限松动与调用方坏参数——含 pxPerDu 入口，R1 §3.7）。
internal const val PAINTER_BOUNDS = "PAINTER_BOUNDS"

// 颜色词表门（本批增补，超出 R1 六码的部分）：消灭"android Color.parseColor 的宽容词表
// （#RGB/#AARRGGBB/命名色）放行契约外颜色被静默接受"——DrawOps 契约只认 #RRGGBB(AA)。
internal const val PAINTER_COLOR = "PAINTER_COLOR"

// alpha 范围门（本批增补）：消灭"bypass 校验器的文档 alpha>1 被截进 255 或 Paint.setAlpha
// 抛无码 IAE"——统一折进本异常族，带 op 上下文。
internal const val PAINTER_ALPHA = "PAINTER_ALPHA"

// 快照回放衔接外码（批次 29 T2，定义在 VisualDrawOpsPainterRendered.kt）：把
// "Rendered.drawCommandsJson 缺失 / 解析拒绝"折成有码族，消息内嵌原始 DRAWOPS_* 码；
// 设备判据见 VisualDrawOpsPainterInstrumentedTest 的 drawRendered 两例（R10）。
// 不在此处重复声明常量——单一来源在 Rendered 文件。

/**
 * 回放 textPaint 的**显式** flags（批次 29 T5；KD-78 ④ / 批次 29 计划 §1.5 裁定）。
 *
 * **它消灭的具体失败**：度量 Paint 与回放 textPaint 的 flags 各自演化时，内容摘要不变、
 * 度量与回放静默失配（advance/字形与冻结布局不同）。本常量是**回放侧**的单一来源，
 * 由 `VisualDrawOpsPainter.textPaint` 在构造时显式装配（`flags = REPLAY_TEXT_PAINT_FLAGS`）。
 *
 * **不是平台 `Paint()` 的构造默认**：API 34 实测默认 1283（`ANTI_ALIAS|FILTER_BITMAP|
 * DEV_KERN|EMBEDDED_BITMAP`，批次 29 设备证据 R1）；只写常量不显式赋值会让本侧保持 1283，
 * 与度量指纹 `paintFlags=0` 失配。度量侧 `VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS`
 * 同为 0（两侧 KDoc 互指，app 侧 JVM 测钉指纹字面、本侧 JVM 测钉常量，设备侧由
 * `VisualDrawOpsPainter.textPaintFlagsForTest` 读真实回放 Paint 的 flags 钉死）。
 */
internal const val REPLAY_TEXT_PAINT_FLAGS = 0

/** 工厂：消息带码前缀（引擎折叠诊断的 "CODE: message" 同形）并截到诊断预算。 */
internal fun paintingFailure(code: String, message: String): VisualDrawOpsPaintingException =
    VisualDrawOpsPaintingException(
        code,
        "$code: ${message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)}",
    )

/**
 * 契约矩阵 `[a,b,c,d,e,f]` → Android [android.graphics.Matrix] 的 9 值列序装配（R1 §3.3）。
 *
 * 契约映射 `x' = a·x + c·y + e; y' = b·x + d·y + f`（`VisualDrawOps.kt:77`）；Android
 * setValues 列序为 `[MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y]`
 * （AOSP `Matrix.java:36-44`），故装载序 = **`[a, c, e, b, d, f]`**。skew 列序错位是该
 * 映射的头号隐性错误——JVM 门用"切变工作例 + 原序误装负例"钉死（R1 §4-判据 2）。
 *
 * 透视行恒为 `[0, 0, 1]`（DrawOps V1 无透视项；校验器只收 6 值）。
 */
internal fun VisualMatrix.toAndroidValues(): FloatArray = floatArrayOf(
    drawOpsFloat(a), drawOpsFloat(c), drawOpsFloat(e),
    drawOpsFloat(b), drawOpsFloat(d), drawOpsFloat(f),
    0f, 0f, 1f,
)

/**
 * DrawOps 数值 → Float 的唯一窄化通道：越出 Float 有限域即 [PAINTER_BOUNDS]。
 * 校验器 Double 界限 ±1e5（`VisualDrawOpsValidator.kt:35`）远在 Float 域内，本门只防
 * 未来界限松动（R1 §3.7"一行成本"）；`pxPerDu` 等调用方参数也经它/同款检查收口。
 */
internal fun drawOpsFloat(value: Double): Float {
    val narrowed = value.toFloat()
    if (!narrowed.isFinite()) {
        throw paintingFailure(PAINTER_BOUNDS, "value $value is not representable as a finite float")
    }
    return narrowed
}

/**
 * `#RRGGBB(AA)` → ARGB Int 的**严格**解析：8 位缺省 alpha=FF。
 *
 * **它消灭的具体失败**：android `Color.parseColor` 的宽容词表（`#RGB`、`#AARRGGBB`、
 * 命名色 "red"）会静默接受 DrawOps 契约外颜色——painter 消费的是"校验器已验"的文档，
 * 但 hand-built 文档（负向测试、未来管线缺陷）可绕过校验器，宽容解析把词表漂移变成
 * 静默错色而不是有码拒绝（[PAINTER_COLOR]）。
 */
internal fun parseDrawOpsColor(color: String): Int {
    if ((color.length != 7 && color.length != 9) || color[0] != '#') {
        throw paintingFailure(PAINTER_COLOR, "color must be \"#RRGGBB\" or \"#RRGGBBAA\", got \"$color\"")
    }
    fun nibbleAt(index: Int): Int {
        val value = when (color[index]) {
            in '0'..'9' -> color[index] - '0'
            in 'a'..'f' -> color[index] - 'a' + 10
            in 'A'..'F' -> color[index] - 'A' + 10
            else -> -1
        }
        if (value < 0) {
            throw paintingFailure(PAINTER_COLOR, "color \"$color\" has a non-hex digit at index $index")
        }
        return value
    }
    fun channel(highIndex: Int): Int = (nibbleAt(highIndex) shl 4) or nibbleAt(highIndex + 1)
    val rgb = (channel(1) shl 16) or (channel(3) shl 8) or channel(5)
    val alpha8 = if (color.length == 9) channel(7) else 0xFF
    return (alpha8 shl 24) or rgb
}

/**
 * 8 位色 alpha 与 op.alpha 的**乘法**语义（批次 29 T5；KD-78 ③ 收口 / 批次 29 计划 §1.2
 * 裁定）：`effectiveAlpha = floor(colorA × op.alpha)`，其中 `colorA` 是 `#RRGGBBAA` 末两位
 * （6 位色取 255）。等价于 `floor((colorA/255) × op.alpha × 255)`——先归一再量化在数学上
 * 同一结果，但直接算整数乘法避免中间浮点两次取整。
 *
 * **它消灭的具体失败**：`bindPathPaint`/`bindTextPaint` 先 `setColor`（带 8 位色 alpha 字节）
 * 后 `setAlpha(op.alpha)`，而 AOSP `Paint.setAlpha` 只替换 alpha 通道——8 位色的 alpha 字节
 * 在回放侧**永远失效**（`#ff000080` 无组透明按不透明画，无任何诊断，KD-78 ③ 静默错画）。
 * 乘法语义让色内 alpha 与组 alpha 都生效：`#ff000080` + `op.alpha=1` → 128。
 *
 * floor 取整与 [drawOpsAlpha255] 同口径（R1 §3.2 / lottie `BaseLayer.java:264` 截断）；
 * 越界/非有限仍先折 [PAINTER_ALPHA]（守 bypass 文档）。
 */
internal fun effectiveDrawOpsAlpha255(colorArgb: Int, alpha: Double): Int {
    if (!alpha.isFinite() || alpha < 0.0 || alpha > 1.0) {
        throw paintingFailure(PAINTER_ALPHA, "alpha $alpha is outside [0, 1]")
    }
    val colorAlpha = (colorArgb ushr 24) and 0xFF
    return floor(colorAlpha * alpha).toInt()
}

/**
 * DrawOps alpha（Double [0,1]）→ Paint alpha（Int [0,255]）的**floor** 取整（R1 §3.2：
 * 与 lottie `BaseLayer.java:264` 的 `(int)` 截断同口径——Java 截断对正值即 floor）。
 * 冒烟判据要求 round 实现者必红：0.5 → **127** 而非 128（R1 §4-判据 2 的手算基准）。
 * 越界/非有限 → [PAINTER_ALPHA]（校验器钉 [0,1]，此门守 bypass 文档）。
 *
 * 本函数是 [effectiveDrawOpsAlpha255] 在"无色内 alpha"（6 位色 → colorA=255）时的特例；
 * 回放路径统一走后者，保留本函数作纯层既有判据与语义单点。
 */
internal fun drawOpsAlpha255(alpha: Double): Int = effectiveDrawOpsAlpha255(0xFF000000.toInt(), alpha)

/**
 * label 内容解析结果 DTO（R1 §3.6）：painter 不重推断行/分段规则，由冻结度量 artifact
 * 携带（`VisualMeasuredLayout.lines` seam 的回放侧形态——"painter 复用同一 seam、同一
 * 数字"，`VisualContentMeasuring.kt:33-36`）。app 侧未来装配器把度量产物折成本 DTO；
 * 本批由测试台/冒烟直接构造。
 */
internal sealed interface VisualLabelContent {

    /**
     * 文本内容：显式段列表（字符区间指向 [payload]）+ 行高反解所需的 [heightDu]。
     *
     * [heightDu] 是相对 R1 §3.6 草图的一处补齐（登记）：草图 Text(payload, sizeSp,
     * fontScale, lines) 未携带行高，而 §3.5 的多行 baseline 递推 `y + i × lineHeightDu`
     * 需要 `lineHeightDu = heightDu / 行数`（`VisualContentMeasuring.kt:262` 不变量
     * "heightDu = 段数 × 行高"）——没有它 DTO 无法表达多行行距。
     */
    data class Text(
        val payload: String,
        /** 冻结度量里的规范字号 sp（op 无 per-label size，R2 D3；R1 §3.5）。 */
        val sizeSp: Float,
        /** 度量环境的 fontScale（du per sp = fontScale，`VisualContentMeasuring.kt:112-118`）。 */
        val fontScale: Float,
        /** 冻结布局的总高（du）= 段数 × 行高（`VisualContentMeasuring.kt:262`）。 */
        val heightDu: Float,
        /** 显式段（`[start, endExclusive)` 指向 payload；首段 baseline 恰为 label 的 y）。 */
        val lines: List<VisualLabelLine>,
    ) : VisualLabelContent

    /** math 盒内容：本批 fail-closed（[PAINTER_LABEL_MATH]），盒走查归回放/PDF 批（R1 §3.6）。 */
    data class Math(
        val box: MathBox,
        val sizeSp: Float,
        val fontScale: Float,
    ) : VisualLabelContent
}

/** 一行显式段：payload 的 `[start, endExclusive)` 字符区间（`VisualMeasuredLine` 同形）。 */
internal data class VisualLabelLine(
    val start: Int,
    val endExclusive: Int,
)
