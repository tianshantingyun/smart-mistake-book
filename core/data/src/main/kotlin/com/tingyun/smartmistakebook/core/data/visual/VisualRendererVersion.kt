package com.tingyun.smartmistakebook.core.data.visual

/**
 * painter **实现版本**（批次 30 T4 / D-05 ②档"重放矢量"门；设计 §7 #3 的落地载体）。
 *
 * **它消灭的具体失败**：`DRAW_SCHEMA_VERSION` 只描述 DrawOps 文本的**格式**。painter 改变
 * 绘制行为（矩阵舞步、取整、抗锯齿、textPaint flags、颜色解析、fill/stroke 顺序）而格式不变时，
 * 旧的 schema 门仍会判"匹配"，同一份快照在升级后被静默画成**另一张图**——用户看到过的图
 * 变了却没有任何记录解释。有了实现版本，恢复侧能区分"格式兼容"与"画出的是同一张图"，
 * 不匹配就走只读预览/说明（保守分支），不硬画。
 *
 * 纪律：
 * - 写入方（D-02 / T3）在写 `tutor_visual_snapshot.renderer_version` 时使用本常量；
 * - 恢复侧（[VisualSnapshotRestore]）把它作为 ② 档门之一；
 * - **改 painter 的可观察绘制行为而不改 [VisualDrawOpsParser.DRAW_SCHEMA_VERSION] 时，
 *   必须 bump 本常量**（改格式则两侧同时评估）；
 * - 重放**不需要** runtime/compiler 版本——它们只服务"用户明确要求重跑源码"的①档，本常量
 *   不把它们拉进重放门（否则应用升级后旧图会全部降级，是无谓的可用性损失）。
 */
internal const val VISUAL_RENDERER_IMPLEMENTATION_VERSION: String = "visual-renderer-v1"
