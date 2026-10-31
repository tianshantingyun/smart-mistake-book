package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import kotlin.math.ceil

/**
 * 排版预算（批次 38 M-03；设计依据 R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §4，契约 §4 `:90`：
 * "sizeSp 限 12..48，系统 fontScale 另计；单事务所有文字 artifact 与预览的累计像素 ≤
 * 4 MP，单 bitmap ≤ 16 MiB，宿主只保留当前帧和一个候选帧，释放旧 artifact……超限不截断
 * 成'正确图'，返回有界诊断"）。
 *
 * **它消灭的具体失败**（各自指认）：
 * - **4 MP 累计像素门**：度量端口只限条数（128）/字符（6000），对"布局盒总面积"无界——
 *   6000 个 CJK 字符在 fontScale 2.0 下实测可超 4 MP（6000×32 du 宽 × 38 du 高 ≈ 7.3 MP），
 *   单个预算满格 math 盒在 density 1 下 ≈ 16.7 MP；超限内容此前畅通无阻地进 render/编译。
 * - **16 MiB 单 bitmap 门**：超尺寸位图在 `Bitmap.createBitmap` 分配点抛 OOM/
 *   `OutOfResourcesError`，把"程序超限"变成进程死亡——fail-closed 应是**分配前**的有界拒绝。
 * - sizeSp 12..48 与 fontScale 门**已落**（`VisualMeasureEnvironment.init` require
 *   fail-closed，app 侧），本类只提供常量同源声明，不重复执行（单一执行点在环境类型）。
 *
 * **两门独立判，不得合并**（数字自洽注记，R2 §4.2）：16 MiB ÷ 4 B = 4,194,304 px 恰好
 * 4 MP——ARGB_8888 下两上限**数值重合是巧合级对齐**：像素门按 **du** 计（内容预算，同一
 * 内容在 1x/3x 设备上不差别拒绝；契约 `:100`"根变换只做一次 du→px"，布局期尚未做根变换），
 * 字节门按真实 **px × bytesPerPixel** 计（内存分配事实，RGB_565 等配置下与像素数脱钩）。
 * 语义一个管内容总量、一个管单次内存分配。
 *
 * **落位与执行点**（热区约束下）：预算器落 core:data `visual` 包（core/domain
 * `VisualExecutionBudget` 是热区，解锁后搬并归位）；执行点两处——
 * 1. **度量结算处**：引擎在 measurer 返回后、render 阶段前调用 [transactionBudgetDiagnostic]
 *    （本批已接线，越界折 `INVALID_PROGRAM` + [LAYOUT_PIXEL_BUDGET] 有界拒绝——内容来自程序
 *    prepare 输出，与"度量批次构造器预算 → INVALID_PROGRAM"同族同映射判断）。
 *    **M-03×V-02 交互登记**：该 `INVALID_PROGRAM` 前缀落在 `FigureMethodSwitchRunner` 的
 *    可归属切换集——V-02 生产接线生效后，越界会触发 JS→IMAGE 切换并预留远程派发（远程账本
 *    6 上限不重置）；"越界属程序侧确定性超限还是输入侧越量（对照切换集排除 INPUT_OVER_BUDGET
 *    的'输入侧修输入'理由；IMAGE 产物不经 4MP 布局门）"为开放裁定点，归主会话（X-01 生产
 *    接线前裁定），改裁定须本注记与切换集 KDoc 两处同步；
 * 2. **预览分配点**：D-04 轨的 createBitmap 前调用 [requireBitmapBytesWithinBudget]
 *    （本批提供纯门，接线归 D-04；满幅 4096² 画布预览 = 16 MP 必拒，预览须缩采样——
 *    参照数 R2 §4.2）。
 * JS 侧**不镜像**本预算：场景几何预算（节点/路径命令）才有 JS 镜像先例；像素/字节预算是
 * 宿主度量与分配事实，JS 不分配 bitmap、不算像素。
 */
object VisualLayoutBudget {

    /**
     * 契约 §4：单事务所有文字 artifact 与预览的累计像素上限 = 4 MP = 4,194,304 px
     * （按 **du** 口径：每盒 `ceil(widthDu) × ceil(heightDu)`；2048² 恰达上限）。
     * **按 du 不按设备 px**——理由见类 KDoc"两门独立判"。
     */
    const val MAX_TOTAL_LAYOUT_PIXELS_DU: Long = 4_194_304L

    /**
     * 契约 §4：单 bitmap 字节上限 = 16 MiB = 16,777,216 B（真实 px × bytesPerPixel，
     * ARGB_8888 = [ARGB_8888_BYTES_PER_PIXEL]）。**在分配点之前拒绝**，不制造 OOM。
     */
    const val MAX_BITMAP_BYTES: Long = 16_777_216L

    /** ARGB_8888 每像素字节数（仓内会计口径先例：`AndroidCanonicalAssetVault.kt:103` 的 ×4）。 */
    const val ARGB_8888_BYTES_PER_PIXEL: Int = 4

    /** 有界拒绝码（诊断标记，与 `PAINTER_*` 生成门码族同风格；进诊断、不进显示）。 */
    const val LAYOUT_PIXEL_BUDGET: String = "LAYOUT_PIXEL_BUDGET"

    /** 有界拒绝码（单 bitmap 字节门；分配前拒绝，D-04 接线点消费）。 */
    const val BITMAP_BYTE_BUDGET: String = "BITMAP_BYTE_BUDGET"

    /**
     * 单布局盒的 du 像素 = `ceil(widthDu) × ceil(heightDu)`（R2 §4.2 口径：du 域取整到
     * 像素格）。非有限/负输入即拒（IllegalArgumentException）——度量端口类型已保证
     * 度量产物合法；本门同时服务预览尺寸（D-04），坏参数在此显式暴露，不静默计 0。
     */
    fun boxPixelsDu(widthDu: Float, heightDu: Float): Double {
        require(widthDu.isFinite() && widthDu >= 0f) {
            "A layout box width must be finite and non-negative, got $widthDu"
        }
        require(heightDu.isFinite() && heightDu >= 0f) {
            "A layout box height must be finite and non-negative, got $heightDu"
        }
        return ceil(widthDu.toDouble()) * ceil(heightDu.toDouble())
    }

    /**
     * 单事务累计布局像素（du 口径）：所有已度量内容盒面积求和。**预览并入是 D-04 的
     * 接线点**（契约"文字 artifact **与预览**的累计像素"——预览产物的 du 尺寸用
     * [boxPixelsDu] 并入同一累计）。Double 累加防 Long 溢出（极端产物饱和到
     * Long.MAX_VALUE 时仍正确地远超上限）。
     */
    fun totalLayoutPixelsDu(entries: List<VisualMeasuredContent>): Double =
        entries.fold(0.0) { total, entry -> total + boxPixelsDu(entry.widthDu, entry.heightDu) }

    /**
     * 度量结算门：累计超 [MAX_TOTAL_LAYOUT_PIXELS_DU] 时返回携带 [LAYOUT_PIXEL_BUDGET]
     * 码的有界拒绝诊断；null = 准入。**不截断、不静默缩放**（契约 §4）。
     */
    fun transactionBudgetDiagnostic(measured: List<VisualMeasuredContent>): String? {
        val total = totalLayoutPixelsDu(measured)
        if (total <= MAX_TOTAL_LAYOUT_PIXELS_DU.toDouble()) {
            return null
        }
        return "$LAYOUT_PIXEL_BUDGET: transaction layout pixels $total du² exceed " +
            "$MAX_TOTAL_LAYOUT_PIXELS_DU (canvas edge cap " +
            "${VisualExecutionBudget.MAX_CANVAS_EDGE_DU} du means a full-canvas preview is " +
            "16 MP and must downsample)"
    }

    /**
     * 单 bitmap 分配前置门（D-04 createBitmap 前调用）：`width × height × bytesPerPixel`
     * 超 [MAX_BITMAP_BYTES] 即抛 IllegalArgumentException（携带 [BITMAP_BYTE_BUDGET] 码），
     * **分配不发生**——消灭"超尺寸位图在分配点抛 OOM/OutOfResourcesError 变进程死亡"。
     * 恰达上限不拒（契约"≤ 16 MiB"）。
     */
    fun requireBitmapBytesWithinBudget(widthPx: Int, heightPx: Int, bytesPerPixel: Int) {
        require(widthPx >= 0 && heightPx >= 0) {
            "A bitmap allocation must have non-negative dimensions, got ${widthPx}x$heightPx"
        }
        require(bytesPerPixel > 0) {
            "A bitmap allocation needs a positive bytes-per-pixel, got $bytesPerPixel"
        }
        val bytes = bitmapBytes(widthPx, heightPx, bytesPerPixel)
        if (bytes > MAX_BITMAP_BYTES) {
            throw IllegalArgumentException(
                "$BITMAP_BYTE_BUDGET: bitmap ${widthPx}x$heightPx × $bytesPerPixel B/px = " +
                    "$bytes B exceeds $MAX_BITMAP_BYTES B; allocation refused before creation",
            )
        }
    }

    /** 单 bitmap 字节数（纯计算；Int 乘法溢出防护用 Long）。 */
    fun bitmapBytes(widthPx: Int, heightPx: Int, bytesPerPixel: Int): Long =
        widthPx.toLong() * heightPx.toLong() * bytesPerPixel.toLong()
}
