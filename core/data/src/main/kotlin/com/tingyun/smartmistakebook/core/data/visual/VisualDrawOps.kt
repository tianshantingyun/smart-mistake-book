package com.tingyun.smartmistakebook.core.data.visual

/**
 * DrawOps V1 的宿主类型化指令模型（批次 27 T1；R1
 * `docs/research/2026-10-27-scenev1-compiler-design.md` §3.2 词表与 §4 Canvas 映射点）。
 *
 * **它消灭的具体失败**：编译器输出的 DrawOps JSON 若以裸字符串越过端口，宿主就只能在
 * painter（4E.2 后段）落地时才发现词表漂移——一条未知 op 或越界坐标要等到真机渲染才炸。
 * 本类型把"指令枚举 + 参数"钉成 Kotlin 面：原生校验器（[VisualDrawOpsValidator]）解析出
 * 该模型即证明词表/预算/有限性/配平全部成立；4E.2 的 painter 与 fake 消费方都以此为
 * 消费形态（"本批只到指令序列并可 fake 消费"）。
 *
 * 本批落位为 `internal`：消费方（painter）尚未存在，先不进公共 API；4E.2 落
 * `core:visual` 回放时按需提升可见性，词表本身不变。
 *
 * 词表与 JS 侧 `visual-compiler.js` 文件头一一对应；改词表必须两侧同改（Node 测试台
 * `tools/visual_compiler/run_tests.mjs` 与 JVM 校验器负向矩阵各自钉住）。
 */

/** 路径命令（DrawOps 词表不变量 1：仅 M/L/C/Z；Q 已在编译器提升为 C，弧已由 SDK 拆段）。 */
internal sealed interface VisualPathCommand {
    data class MoveTo(val x: Double, val y: Double) : VisualPathCommand
    data class LineTo(val x: Double, val y: Double) : VisualPathCommand
    data class CubicTo(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val x: Double,
        val y: Double,
    ) : VisualPathCommand

    data object Close : VisualPathCommand
}

/** 填充规则（契约 §5：nonzero/evenodd；DrawOps JSON 字面量小写）。 */
internal enum class VisualFillRule(val json: String) {
    NONZERO("nonzero"),
    EVEN_ODD("evenodd"),
}

internal enum class VisualStrokeCap(val json: String) {
    BUTT("butt"),
    ROUND("round"),
    SQUARE("square"),
}

internal enum class VisualStrokeJoin(val json: String) {
    MITER("miter"),
    ROUND("round"),
    BEVEL("bevel"),
}

/** 描边规格（宽度必填；cap/join/miterLimit/dash 缺省即编译器输出中的钉定默认值）。 */
internal data class VisualStrokeSpec(
    val width: Double,
    val cap: VisualStrokeCap,
    val join: VisualStrokeJoin,
    val miterLimit: Double,
    val dash: List<Double>?,
)

/** 标签水平对齐（编译器已把对齐折进最终 (x, y)；op 内为回显元数据）。 */
internal enum class VisualTextHAlign(val json: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
}

internal enum class VisualTextVAlign(val json: String) {
    TOP("top"),
    MIDDLE("middle"),
    BOTTOM("bottom"),
}

/**
 * 矩阵 `[a,b,c,d,e,f]`：`x' = a·x + c·y + e; y' = b·x + d·y + f`（契约 §5）。
 *
 * **painter 映射警示（R1 §4）**：Android [android.graphics.Matrix.setValues] 列序为
 * `[MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y]`，即本矩阵须以
 * `[a, c, e, b, d, f]` 装载——skew 列序错位是该映射的头号隐性错误。
 */
internal data class VisualMatrix(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
) {
    /** |det|；宿主校验保证 ≥ 1e-12（不可逆矩阵在编译层已拒）。 */
    val determinant: Double get() = a * d - b * c
}

/** 绘制指令（DrawOps V1 词表六原语；顺序即回放顺序）。 */
internal sealed interface VisualDrawOp {
    /** 压栈（组开启）。 */
    data object Save : VisualDrawOp

    /**
     * 乘入本地矩阵。绘制路径保持**局部坐标**（R1 §3.2 不变量 2：非均匀缩放下"stroke 随
     * 变换缩放"只能由 painter 在变换空间里描边保真）；clip/命中区则已烘焙组合坐标。
     */
    data class Concat(val matrix: VisualMatrix) : VisualDrawOp

    /**
     * 裁剪（只相交，契约 §5）。路径为**组合后坐标**，须在恒等变换下回放——编译器把
     * clip 发射在同组 concat 之前正是为此。
     */
    data class Clip(val path: List<VisualPathCommand>, val rule: VisualFillRule) : VisualDrawOp

    /**
     * 画一条路径（当前变换空间内的局部坐标）。fill 先画、stroke 后画；alpha 为 group
     * 链预乘后的最终 alpha（不变量 3，painter 每次绘制恰一次 setAlpha）。
     */
    data class DrawPath(
        val path: List<VisualPathCommand>,
        val fill: String?,
        val stroke: VisualStrokeSpec?,
        val alpha: Double,
    ) : VisualDrawOp

    /**
     * 画已度量内容：位置 (x, y) 是编译器一次算定的最终文本原点（左缘 + baseline；
     * 契约 §5"compiler 一次算出最终位置"），rotationDeg 绕 (x, y) 旋转。
     */
    data class Label(
        val contentId: String,
        val x: Double,
        val y: Double,
        val rotationDeg: Double,
        val hAlign: VisualTextHAlign,
        val vAlign: VisualTextVAlign,
        val color: String,
        val alpha: Double,
    ) : VisualDrawOp

    /** 出栈（与 [Save] 严格配平；配平由校验器保证，painter 不自愈）。 */
    data object Restore : VisualDrawOp
}

/** 命中区（组合后坐标；原生点内判定按同 fill rule，4E.2 交互段消费）。 */
internal data class VisualDrawHitRegion(
    val id: String,
    val path: List<VisualPathCommand>,
    val rule: VisualFillRule,
)

/** 编译器统计（不变量 5：预算计数进 stats；校验器复算可复算项）。 */
internal data class VisualDrawOpsStats(
    val nodes: Int,
    val pathCommands: Int,
    val labels: Int,
    val ops: Int,
)

internal data class VisualDrawOpsViewBox(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
)

/** 一次编译的完整 DrawOps 文档。 */
internal data class VisualDrawOpsDocument(
    val drawSchemaVersion: Int,
    val viewBox: VisualDrawOpsViewBox,
    val background: String?,
    val ops: List<VisualDrawOp>,
    val hitRegions: List<VisualDrawHitRegion>,
    val stats: VisualDrawOpsStats,
)

/** 校验结果：类型化文档或带码拒绝（引擎折 INVALID_SCENE；诊断 ≤512 字符）。 */
internal sealed interface VisualDrawOpsValidation {
    data class Valid(val document: VisualDrawOpsDocument) : VisualDrawOpsValidation
    data class Invalid(val code: String, val message: String) : VisualDrawOpsValidation
}

/**
 * [VisualDrawOpsParser.serialize] 的结果（批次 30 T8）：通过同一生成门与往返门的文本，
 * 或带码拒绝。
 *
 * 返回类型从 `String` 改成 sealed 结果，**它消灭的失败**：旧序列化器只做发射，遇到
 * stroke-only / 奇数 dash / stats 不符 / 非有限数的类型化文档仍会吐出一段文本，调用方
 * 拿到的是"看起来合法的 JSON"，要等到回放门才炸；且旧 `num()` 对非有限值直接抛
 * `IllegalArgumentException`，同一入口混用"返回文本"和"抛异常"两种失败形态。
 */
internal sealed interface VisualDrawOpsSerialization {
    data class Text(val text: String) : VisualDrawOpsSerialization
    data class Rejected(val failure: VisualDrawOpsValidation.Invalid) : VisualDrawOpsSerialization
}
