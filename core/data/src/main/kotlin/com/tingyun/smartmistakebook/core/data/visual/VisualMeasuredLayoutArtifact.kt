package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualContentKind

/**
 * 完整布局 artifact 的类型化形状（批次 38 M-01；设计依据 R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §3，契约 §3.2 事务第 2 步：
 * "原生验证并度量 text/math，保留不可变布局 artifact，返回宽、高、baseline、inkBounds、
 * 实际字号与内容摘要"、§5 持久化段 `:129`："内容文本/数学 lowering、冻结的行范围与 run
 * 位置、baseline、字体身份/版本及摘要随显示操作保存"）。
 *
 * **它消灭的具体失败**（R2 §3.4 第 1 步新增指认）：
 * 1. painter 回放对行高只能**反解**（`heightDu = 段数 × 行高` 的隐式契约一旦被未来多行距
 *    打破即静默错位，`VisualDrawOpsPainter.playLabel` 现行 `heightDu / lines.size`）——类型化
 *    行结构有了跨模块公共形状，回放批接线后直携行结构，消解该反解依赖；
 * 2. "原生检查越界"与未来交互命中区对**墨迹**不可见（布局盒 ≠ 墨迹：斜体悬挂、fallback
 *    大字形可越出盒）——[inkBounds] 显式携带墨迹矩形，消灭不可见。
 *
 * **落位**：public 于 core:data `visual` 包（与 painter 同包，`VisualLabelContent` 的 natural
 * home；`VisualDrawOpsPainterPure.kt` KDoc 预留的"app 侧装配器把度量产物折成本 DTO"的
 * 度量侧产物即本类型）。app 度量器产出它（`VisualContentMeasuring.toArtifact`）；端口
 * `VisualMeasuredContent` 五字段（core/domain，热区）照旧映射、零改动——端口 `inkBounds`
 * 增补等热区解锁（R2 §3.4 第 2 步）。
 *
 * ## 消费点枚举（M-01 验收件；每个字段谁在吃）
 *
 * | 消费方 | 消费字段 | 说明 |
 * |---|---|---|
 * | JS 编译器（render 的 metrics 注入，`QuickJsVisualExecutionEngine.metricsJson`） | 经端口投影的 widthDu/heightDu/baselineDu/fontSizeSp/digest/contentId | wire 形状本批**不变**（五字段 JSON）；改 wire = 契约变更，须同步 `tools/visual_compiler` JS 测试台（同值镜像纪律） |
 * | 原生越界检查（DrawOps 校验器/painter） | 端口 widthDu/heightDu（现行） | inkBounds 到位后**第一版不升级判据**——墨迹判据属契约变更（R2 §3.2-4） |
 * | painter / PDF 回放（`VisualDrawOpsPainter.playLabel`、`VisualDrawOpsPdfPainter`） | lines、heightDu（行高反解）、kind、degraded | 本批经 `VisualLabelContent` 的行结构消费；直携行高消除反解 = 回放批两端同改并保金样 |
 * | math 回放解锁（S-08 下游，R2 §5.3） | （经 [MathContentLowering] 的 MathBox → `VisualLabelContent.Math`） | 现行 `PAINTER_LABEL_MATH` fail-closed；解锁归回放批 |
 * | 持久化（RenderScene/快照，契约 §5 `:129`） | 全部字段 | 冷恢复须**环境指纹**可比对：digest 已锚定指纹但不可反解——持久化批落库时须随行携带环境指纹字面（`VisualMeasureEnvironment.fingerprint()` 同值；本类型不预加字段，落库形状归持久化批） |
 * | 缓存命中判定（契约 §3.2"缓存只在内容摘要和布局环境完全一致时命中"） | digest | [inkBounds] **不进** digest（派生量，R2 §3.2-5）；指纹参与 digest |
 * | 排版预算结算（M-03，`VisualLayoutBudget`） | widthDu/heightDu | `totalLayoutPixelsDu` 按盒 du 面积累计（含预览并入，D-04 接线） |
 *
 * **不可变性**：纯只读 data class；度量产出后不再变（契约"不可变布局 artifact"）。
 */
@kotlinx.serialization.Serializable
data class VisualMeasuredLayoutArtifact(
    /** 条目种类；降级 math 保留 MATH（摘要按 kind 区分，R2 §3.1）。 */
    val kind: VisualContentKind,
    /** 内蕴排版宽（du）：text = 各段 advance 最大值（`Paint.measureText` 口径）；math = 盒宽。 */
    val widthDu: Float,
    /** 高（du）：text = 段数 × 行高；math = 盒高。 */
    val heightDu: Float,
    /** baseline 自盒顶偏移（du）：text = 首行 `-ascent`；math = `MathBox.baseline`。 */
    val baselineDu: Float,
    /** 实际使用的规范字号（sp）：环境单一规范字号（MVA 条目协议不带 per-entry size，R2 D3）。 */
    val fontSizeSp: Float,
    /** 内容摘要：sha256(kind|degraded|环境指纹|payload)；[inkBounds] 不参与（见上表）。 */
    val digest: String,
    /**
     * 显式段列表（text 与降级 math 必有；math 盒 = null——盒树自带布局，无段语义）。
     * 段区间指向度量 payload（`\n` 切分、段尾单个 `\r` 归一）；不变式
     * `heightDu = lines.size × 行高`（painter 反解口径的来源，见消费表第 3 行）。
     */
    val lines: List<Line>?,
    /** math 解析/预算失败按契约降级为可读文本时 true（参与摘要）。 */
    val degraded: Boolean,
    /**
     * 墨迹边界（du 矩形；**新增字段**，R2 §3.2 坐标约定）：原点 = 布局盒左上角、y 向下
     * 为正。text = 各段 `Paint.getTextBounds` 矩形（origin (0,0)、基线 y=0 口径）折算到
     * 盒顶原点后的联合；math 第一版 = 盒自身（墨迹级细化随"真实字形度量"共享 lowering
     * 演进，R2 §3.2-3）。
     *
     * **与布局盒无包含不变式**（消费面只读不裁）：斜体悬挂、fallback 大字形（API < 33 行
     * metrics 低估混排行高）可使墨迹越出 `[0,widthDu]×[0,heightDu]` 甚至出现负坐标；
     * 越界拒绝仍按布局盒口径判（契约 §5"原生检查越界"现行实现基础）。若未来某消费面
     * 必须假设包含，属新契约变更，回主会话裁。
     */
    val inkBounds: InkRect,
) {
    init {
        require(widthDu.isFinite() && widthDu >= 0f) {
            "A measured layout width must be finite and non-negative, got $widthDu"
        }
        require(heightDu.isFinite() && heightDu >= 0f) {
            "A measured layout height must be finite and non-negative, got $heightDu"
        }
        require(baselineDu.isFinite() && baselineDu >= 0f) {
            "A measured layout baseline must be finite and non-negative, got $baselineDu"
        }
        require(fontSizeSp.isFinite() && fontSizeSp >= 0f) {
            "A measured layout font size must be finite and non-negative, got $fontSizeSp"
        }
        require(digest.isNotBlank()) { "A measured layout artifact needs a digest" }
        // lines null ⇔（math 盒且未降级）。text 必有段；降级 math 携带降级文本段（可复现）。
        require((lines == null) == (kind == VisualContentKind.MATH && !degraded)) {
            "A measured layout artifact must carry lines exactly when the content is text or " +
                "a degraded math (kind=$kind, degraded=$degraded, lines=${lines?.size})"
        }
        require(lines == null || lines.isNotEmpty()) {
            "A measured layout artifact with lines must carry at least one segment"
        }
        require(inkBounds.left.isFinite() && inkBounds.top.isFinite() &&
            inkBounds.right.isFinite() && inkBounds.bottom.isFinite()) {
            "A measured layout inkBounds must be finite, got $inkBounds"
        }
        require(inkBounds.left <= inkBounds.right && inkBounds.top <= inkBounds.bottom) {
            "A measured layout inkBounds must be a sorted rect, got $inkBounds"
        }
    }

    /** 一行显式段：payload 的 `[start, endExclusive)` 字符区间 + 该段 advance 宽（du）。 */
    @kotlinx.serialization.Serializable
    data class Line(
        val start: Int,
        val endExclusive: Int,
        val widthDu: Float,
    ) {
        init {
            require(start >= 0 && start <= endExclusive) {
                "A measured line range must satisfy 0 ≤ start ≤ endExclusive, got [$start, $endExclusive)"
            }
            require(widthDu.isFinite() && widthDu >= 0f) {
                "A measured line width must be finite and non-negative, got $widthDu"
            }
        }
    }

    /**
     * 墨迹矩形（du，盒顶原点、y 向下）。**允许负坐标/越出布局盒**（无包含不变式，见
     * [inkBounds] KDoc）；仅要求有序（left ≤ right、top ≤ bottom）与有限。
     */
    @kotlinx.serialization.Serializable
    data class InkRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )
}
