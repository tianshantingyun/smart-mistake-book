package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/**
 * DrawOps V1 → Android Canvas 的回放 painter（批次 28 T2；R1
 * `docs/research/2026-10-28-painter-design.md` §3.2-§3.7 落码规格）。
 *
 * **它消灭的具体失败**（R1 §3.8 自检表的落码形态）：
 * 1. **嵌套变换组内的 clip 与组内 label 被二次变换**——DrawOps V1 坐标空间是二分的：
 *    `path` 携带局部坐标（随 `concat` 回放），而 `clip`（编译器烘焙组合坐标，
 *    `visual-compiler.js:853-855`）与 `label`（一次投影算定，`:918-927`）携带**根空间**
 *    坐标，却发射在组的 `concat` 之后。朴素顺序回放在这两类场景必然双乘。painter 自维护
 *    矩阵镜像，对 clip/label 走"setMatrix(root) → 应用 → setMatrix(mirror)"舞步
 *    （androidsvg `SVGAndroidRenderer.java:598-620` 同型生产先例）；镜像与 canvas 走同一
 *    float32 操作序列（`preConcat` 镜像 `concat`、快照走 `getValues`/`setValues`），逐位
 *    同步，不触碰已 deprecated 的 `canvas.getMatrix`。
 * 2. **跨 op 绘制状态泄漏**——Paint 常驻复用（lottie `BaseLayer.java:84-88,313` 先例）+
 *    每相位**无条件全字段** binder：style/color/alpha/描边四参/pathEffect 一次设齐，
 *    DashPathEffect 残留是 Paint 复用最常见事故。
 * 3. **非法/越界输入静默错画**——全部折进 [VisualDrawOpsPaintingException] 有码异常族
 *    （码面见 `VisualDrawOpsPainterPure.kt`），未知 op 在 Kotlin 层受 sealed `when` 穷尽
 *    强制，结构上不可表达（R1 §3.7）。
 *
 * 纪律与边界：
 * - **线程独占**：实例持有常驻 Paint/Path/Matrix 缓冲，非线程安全；同一实例只在单线程
 *   使用（同 `AndroidVisualContentMeasurer` 线程纪律 KDoc，`VisualContentMeasuring.kt:38-40`）。
 *   `draw()` 入口自复位实例状态（镜像栈清空），同实例可跨多次 `draw` 复用。
 * - **矩阵接管**：`draw()` 入口 `canvas.setMatrix(rootMatrix)`（根变换 = scale(pxPerDu)，
 *   契约 §5"根变换只做一次 du→px"，painter 是唯一执行者）——调用方画布上的既有变换被
 *   替换。本批消费面是新建位图（恒等起始）；PDF 页面基础变换与 painter 的组合归回放/PDF
 *   批（`ptPerDu` 只替换 pxPerDu，契约 `:192`）。
 * - **不自愈**：配平由校验器保证（`VisualDrawOpsValidator.kt:134-156`）；painter 对
 *   bypass 文档只拒不修（[PAINTER_BALANCE]）。
 * - **hitRegions 不绘制**：命中区是原生点内判定数据（4E.2 交互段消费），不在 ops 词表内，
 *   本类不读 `document.hitRegions`。
 * - **fill 先画、stroke 后画**（同一 op 两相位，Canvas/SVG 惯例，R1 §4 `:178`）。
 * - 不做 PDF 回放/持久化/导出（后段批次）。
 */
internal class VisualDrawOpsPainter {

    // 常驻复用缓冲（线程独占）。dash **不设**常驻缓冲（复核 2026-10-28：只扩不缩的复用
    // 缓冲把上一 op 的残尾混进下一个更短 dash 的 pattern——AOSP DashPathEffect 构造器按
    // intervals.length 全量消费（aosp-mirror DashPathEffect.java:34-39，本日 gh api 实读）；
    // 改为每个 dash op 按 dash.size 精确分配）。
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 平台 `Paint()` 默认 flags = 1283（AA|FILTER_BITMAP|DEV_KERN|EMBEDDED_BITMAP），
    // **不是** [REPLAY_TEXT_PAINT_FLAGS]=0（批次 29 设备证据 R1）。必须显式装配，否则
    // 度量环境指纹（paintFlags=0）与真实回放 Paint 不一致（KD-78 ④ 跨侧钉会红）。
    private val textPaint = Paint().apply { flags = REPLAY_TEXT_PAINT_FLAGS }
    private val path = Path()
    private val scratchMatrix = Matrix()
    private val rootMatrix = Matrix()
    private val mirror = Matrix()
    private val mirrorSnapshots = ArrayDeque<FloatArray>()

    /**
     * 把一份**已通过 [VisualDrawOpsValidator] 校验**的类型化文档回放到 [canvas] 上。
     *
     * 入口不收裸文本（消灭"未验 JSON 交 painter"——校验器 KDoc `:22` 明令）；管线钉为
     * validator(text) → [VisualDrawOpsDocument] → 本方法（R1 §5-④）。
     *
     * @param pxPerDu 根变换比例（屏上 = density；PDF = ptPerDu）。非有限/非正即拒
     *   （[PAINTER_BOUNDS]）——"超尺寸画布"（比例坏 → 坐标溢出画布域）的 fail-closed 面。
     * @param labelContent contentId → 内容解析器；返回 null = 绘制时内容不可达
     *   （[PAINTER_UNKNOWN_LABEL]）。
     */
    fun draw(
        document: VisualDrawOpsDocument,
        canvas: Canvas,
        pxPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ) {
        if (document.drawSchemaVersion != DRAW_SCHEMA_VERSION) {
            throw paintingFailure(
                PAINTER_SCHEMA_VERSION,
                "draw schema version ${document.drawSchemaVersion} is not $DRAW_SCHEMA_VERSION",
            )
        }
        if (!pxPerDu.isFinite() || pxPerDu <= 0f) {
            throw paintingFailure(PAINTER_BOUNDS, "replay pxPerDu must be finite and positive, got $pxPerDu")
        }
        rootMatrix.setScale(pxPerDu, pxPerDu)
        mirror.set(rootMatrix)
        mirrorSnapshots.clear()
        canvas.setMatrix(rootMatrix)
        document.background?.let { background -> canvas.drawColor(parseDrawOpsColor(background)) }
        document.ops.forEachIndexed { index, op -> play(index, op, canvas, labelContent) }
    }

    private fun play(
        index: Int,
        op: VisualDrawOp,
        canvas: Canvas,
        labelContent: (String) -> VisualLabelContent?,
    ) {
        val at = "ops[$index]"
        when (op) {
            is VisualDrawOp.Save -> {
                val snapshot = FloatArray(MATRIX_VALUES)
                mirror.getValues(snapshot)
                mirrorSnapshots.addLast(snapshot)
                canvas.save()
            }

            is VisualDrawOp.Concat -> {
                // preconcat 语义两侧同构：canvas.concat(m) ≡ mirror.preConcat(m)
                //（AOSP Canvas.concat javadoc；R1 §3.4 的镜像配对）。
                scratchMatrix.setValues(op.matrix.toAndroidValues())
                canvas.concat(scratchMatrix)
                mirror.preConcat(scratchMatrix)
            }

            is VisualDrawOp.Clip -> {
                // 舞步（R1 §3.4）：clip 坐标是根空间烘焙坐标（编译器在组 concat 之前发射，
                // `visual-compiler.js:853-855`），须在恒等于根变换的矩阵下回放，画完立刻
                // 恢复当前镜像。setMatrix 不动 save 栈，与编译器的 save/restore 计数不脱钩；
                // 单参 clipPath = INTERSECT 求交（契约 §5"clip 只相交"）。
                canvas.setMatrix(rootMatrix)
                rebuildPath(op.path, fillTypeOf(op.rule))
                canvas.clipPath(path)
                canvas.setMatrix(mirror)
            }

            is VisualDrawOp.DrawPath -> playDrawPath(at, op, canvas)
            is VisualDrawOp.Label -> playLabel(at, op, canvas, labelContent)
            is VisualDrawOp.Restore -> {
                val snapshot = mirrorSnapshots.removeLastOrNull()
                    ?: throw paintingFailure(PAINTER_BALANCE, "$at: restore without a matching save")
                mirror.setValues(snapshot)
                canvas.restore()
            }
        }
    }

    private fun playDrawPath(at: String, op: VisualDrawOp.DrawPath, canvas: Canvas) {
        // 门序与两道生成门**同码同序**：dash 形状门先于 stroke-only 门。
        // `visual-compiler.js` 先在 `requireStroke` 内拒 dash 形状、再在 path 节点查
        // stroke-only；`VisualDrawOpsParser` 同样先 `parseStroke`（内含 dash 门）再查
        // stroke-only。回放侧必须同序，否则同一"无 fill + 奇数 dash"输入在生成侧报
        // PAINTER_DASH_ODD、在回放侧报 PAINTER_STROKE_WITHOUT_FILL（复核第 1 轮 R3）。
        // dash 形状门（偶数且 ≥2）提前到任何相位之前——拒绝时不留"fill 相位已上墨"的半相位
        // 画布。下界消灭两个 AOSP 构造器层面的失败（复核 2026-10-28）：奇数进 native 行为
        // 无规约；length<2（含两道生成门都放行的空数组）会被 DashPathEffect 构造器直接抛
        // **无码** ArrayIndexOutOfBoundsException（aosp-mirror DashPathEffect.java:34-39，
        // 本日 gh api 实读），破坏"全部折进有码异常族"契约。
        op.stroke?.dash?.takeIf { it.size % 2 != 0 || it.size < 2 }?.let { dash ->
            throw paintingFailure(
                PAINTER_DASH_ODD,
                "$at: dash must have an even number of entries (at least 2) for DashPathEffect, got ${dash.size}",
            )
        }
        if (op.fill == null && op.stroke != null) {
            throw paintingFailure(
                PAINTER_STROKE_WITHOUT_FILL,
                "$at: stroke-only path has no color source in DrawOps V1 (contract gap, R1 §5-①)",
            )
        }
        // stroke 相位的颜色来源：DrawOps V1 的 VisualStrokeSpec 无 color（KD-78 ①，生成侧已
        // 前置拒绝 stroke-only），唯一色源是同 op 的 fill（过渡裁定"stroke 色取 fill 色"）。
        val fillColor = op.fill?.let(::parseDrawOpsColor)
        rebuildPath(op.path, Path.FillType.WINDING)
        if (fillColor != null) {
            // 批次 29 T5（KD-78 ③）：alpha 取 colorA 与 op.alpha 的乘法（纯层公式单点），
            // 不再用 op.alpha 覆盖色内 alpha——`#ff000080`（末两位=alpha）→ 128/255，非不透明。
            bindPathPaint(fillColor, effectiveDrawOpsAlpha255(fillColor, op.alpha), stroke = null)
            canvas.drawPath(path, pathPaint)
        }
        val stroke = op.stroke
        if (stroke != null) {
            // 到此 stroke 非空 ⟹ fill 非空（上方 PAINTER_STROKE_WITHOUT_FILL 已拒）。
            val strokeColor = fillColor!!
            bindPathPaint(strokeColor, effectiveDrawOpsAlpha255(strokeColor, op.alpha), stroke)
            canvas.drawPath(path, pathPaint)
        }
    }

    private fun playLabel(
        at: String,
        op: VisualDrawOp.Label,
        canvas: Canvas,
        labelContent: (String) -> VisualLabelContent?,
    ) {
        val content = labelContent(op.contentId)
            ?: throw paintingFailure(
                PAINTER_UNKNOWN_LABEL,
                "$at: contentId \"${op.contentId.take(32)}\" is frozen but has no paintable content at replay time",
            )
        val text = when (content) {
            is VisualLabelContent.Math ->
                throw paintingFailure(
                    PAINTER_LABEL_MATH,
                    "$at: math box replay is not implemented this batch (fail-closed, R1 §3.6)",
                )
            is VisualLabelContent.Text -> content
        }
        val colorArgb = parseDrawOpsColor(op.color)
        // KD-78 ③：label 同为 8 位色 × op.alpha 的乘法语义（与 path 相位同一纯函数）。
        val alpha255 = effectiveDrawOpsAlpha255(colorArgb, op.alpha)
        // label 舞步（R1 §3.4）：(x,y) 已是编译器一次算定的最终左缘 + baseline（金样
        // `run_tests.mjs:221-223`），hAlign/vAlign 是回显元数据，**不重放对齐偏移**
        //（双重偏移即错位）；rotation 绕 (x,y)。save/restore 自包含，不触碰组状态栈。
        canvas.save()
        canvas.setMatrix(rootMatrix)
        canvas.translate(drawOpsFloat(op.x), drawOpsFloat(op.y))
        canvas.rotate(drawOpsFloat(op.rotationDeg))
        bindTextPaint(text, colorArgb, alpha255)
        // 行高反解：heightDu = 段数 × 行高（`VisualContentMeasuring.kt:262` 不变量）。
        // 空段列表（真实度量链不存在——explicitLineRanges 恒返 ≥1 段）画零行，无行距除零。
        val lineHeightDu = if (text.lines.isEmpty()) 0f else text.heightDu / text.lines.size
        for (lineIndex in text.lines.indices) {
            val line = text.lines[lineIndex]
            canvas.drawText(text.payload, line.start, line.endExclusive, 0f, lineIndex * lineHeightDu, textPaint)
        }
        canvas.restore()
    }

    /** 全字段 binder（fill 或 stroke 相位二选一调用；消灭跨 op DashPathEffect/描边参泄漏）。 */
    private fun bindPathPaint(colorArgb: Int, alpha255: Int, stroke: VisualStrokeSpec?) {
        pathPaint.style = if (stroke == null) Paint.Style.FILL else Paint.Style.STROKE
        pathPaint.color = colorArgb
        pathPaint.alpha = alpha255
        if (stroke == null) {
            // FILL 相位：描边字段无条件复位（FILL 不消费它们，复位防未来相位序变化时泄漏）。
            pathPaint.strokeWidth = 1f
            pathPaint.strokeCap = Paint.Cap.BUTT
            pathPaint.strokeJoin = Paint.Join.MITER
            pathPaint.strokeMiter = 4f
            pathPaint.pathEffect = null
        } else {
            pathPaint.strokeWidth = drawOpsFloat(stroke.width)
            pathPaint.strokeCap = when (stroke.cap) {
                VisualStrokeCap.BUTT -> Paint.Cap.BUTT
                VisualStrokeCap.ROUND -> Paint.Cap.ROUND
                VisualStrokeCap.SQUARE -> Paint.Cap.SQUARE
            }
            pathPaint.strokeJoin = when (stroke.join) {
                VisualStrokeJoin.MITER -> Paint.Join.MITER
                VisualStrokeJoin.ROUND -> Paint.Join.ROUND
                VisualStrokeJoin.BEVEL -> Paint.Join.BEVEL
            }
            pathPaint.strokeMiter = drawOpsFloat(stroke.miterLimit)
            val dash = stroke.dash
            if (dash == null) {
                pathPaint.pathEffect = null
            } else {
                // 形状（偶数且 ≥2）已在 playDrawPath 前置门保证；此处只装配。按 dash.size
                // **精确**分配——AOSP 构造器按 intervals.length 全量消费（DashPathEffect.java
                // :34-39），曾用的"常驻缓冲只扩不缩"把上一 op 的残尾混进更短 dash 的 pattern
                //（复核 2026-10-28 三失败：残尾错画 / 空数组无码崩溃 / 空 dash 复用旧 pattern；
                // 精确尺寸后该串状态类失败结构性不存在）。
                val buffer = FloatArray(dash.size)
                for (i in dash.indices) {
                    buffer[i] = drawOpsFloat(dash[i])
                }
                pathPaint.pathEffect = DashPathEffect(buffer, 0f)
            }
        }
    }

    /**
     * 回放 textPaint 的实际 flags（批次 29 T5；KD-78 ④ 跨侧钉的观测面）。
     *
     * **它消灭的具体失败**：`textPaint` 是私有字段，跨侧测试只能看到"渲染出来的像素"——
     * 有人给回放 Paint 加 ANTI_ALIAS/locale 后，指纹侧测试仍全绿，度量-回放失配静默发生。
     * 本访问器让设备侧测试直接读**实际用于绘制**的那只 Paint 的 flags，与
     * [REPLAY_TEXT_PAINT_FLAGS] 及度量环境指纹的 `paintFlags` 比对。
     */
    internal fun textPaintFlagsForTest(): Int = textPaint.flags

    /**
     * textPaint binder：**度量 parity**（R1 §3.2/§2.3）。flags 与度量 Paint 同构——
     * 构造时显式 `flags = REPLAY_TEXT_PAINT_FLAGS`（0；平台 `Paint()` 默认 1283 不是本仓
     * 语义，批次 29 设备证据 R1），**不**设 AA/locale/subpixel（R1 §3.2"AA 关闭是 MVA 视觉
     * 取舍，升级须两侧同步 + 进环境指纹"）；本 binder 不动 flags。
     * color/alpha 不影响 advance 宽，可安全设置（alpha 走 [effectiveDrawOpsAlpha255]）。
     *
     * textSize 取 **du 空间值 `sizeSp × fontScale`**：label 在 rootMatrix（scale pxPerDu）
     * 生效下绘制，设备字号 = (sizeSp×fontScale)×pxPerDu px——即 R1 §3.5 的"回放目标 px"；
     * pxPerDu = density 时与度量 Paint 逐像素同宽（设备轨用同构测量 Paint 钉住该 parity）。
     * R1 §3.5 句面"textPaint.textSize = (sizeSp×fontScale)×pxPerDu"是该设备 px 目标值，
     * 若在 rootMatrix 生效下照抄会双乘 pxPerDu——本处以 §3.5 自身推导（"文本 du 足印 =
     * sizeSp×fontScale；回放 px = du×pxPerDu"）为准，落 du 空间值。
     */
    private fun bindTextPaint(content: VisualLabelContent.Text, colorArgb: Int, alpha255: Int) {
        textPaint.style = Paint.Style.FILL
        textPaint.color = colorArgb
        textPaint.alpha = alpha255
        textPaint.textSize = drawOpsFloat((content.sizeSp * content.fontScale).toDouble())
        textPaint.pathEffect = null
    }

    /** 路径重建（reset + 显式 setFillType：fillType 每 op 可变，故不用 rewind——R1 §3.2）。 */
    private fun rebuildPath(commands: List<VisualPathCommand>, fillType: Path.FillType) {
        path.reset()
        path.fillType = fillType
        for (command in commands) {
            when (command) {
                is VisualPathCommand.MoveTo -> path.moveTo(drawOpsFloat(command.x), drawOpsFloat(command.y))
                is VisualPathCommand.LineTo -> path.lineTo(drawOpsFloat(command.x), drawOpsFloat(command.y))
                is VisualPathCommand.CubicTo -> path.cubicTo(
                    drawOpsFloat(command.x1),
                    drawOpsFloat(command.y1),
                    drawOpsFloat(command.x2),
                    drawOpsFloat(command.y2),
                    drawOpsFloat(command.x),
                    drawOpsFloat(command.y),
                )

                VisualPathCommand.Close -> path.close()
            }
        }
    }

    private fun fillTypeOf(rule: VisualFillRule): Path.FillType = when (rule) {
        VisualFillRule.NONZERO -> Path.FillType.WINDING
        VisualFillRule.EVEN_ODD -> Path.FillType.EVEN_ODD
    }

    private companion object {
        /**
         * 与引擎 `QuickJsVisualExecutionEngine.DRAW_SCHEMA_VERSION`、校验器
         * （`VisualDrawOpsValidator.kt:80-84`）同值镜像（引擎侧常量为 private，跨类共享
         * 需扩它的可见面——不值；三侧同值各由校验器/引擎测试钉住）。
         */
        const val DRAW_SCHEMA_VERSION = 1

        const val MATRIX_VALUES = 9
    }
}
