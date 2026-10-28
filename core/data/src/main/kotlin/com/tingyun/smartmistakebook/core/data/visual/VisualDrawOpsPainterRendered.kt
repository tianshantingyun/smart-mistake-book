package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.Canvas
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult

/**
 * `Rendered.drawCommandsJson` → painter 的最小衔接层（批次 29 T2 任务书第 3 项）。
 *
 * **它消灭的具体失败**：快照回放（"把一次编译的 DrawOps 文本在以后某次绘制中原样重放"）
 * 若由调用方各自拼"取文本 → 校验 → 画"，任一环节漏掉就会把未验文本交给 painter，或把
 * null（fake/旧形态 `Rendered`）当成空文档静默画出白图。本扩展把这条链钉成一次调用：
 * 文本缺失 = [PAINTER_DRAW_COMMANDS]（消息带 `DRAWOPS_MISSING_COMMANDS`），解析拒绝 =
 * [PAINTER_DRAW_COMMANDS]（消息内嵌原始 `DRAWOPS_*` 码），通过后**原样**调用既有的
 * [VisualDrawOpsPainter.draw]——painter 的 schema 门/变换舞步/状态 binder/配平策略一字未改。
 *
 * **为什么用 `parse(rendered)`**：回放时 `Rendered` 不携 viewport（S4 锁窗口记录），视口
 * 回显门已在引擎提交时（`VisualDrawOpsValidator.validate`）通过；此处只做结构/值域/
 * contentId 冻结/配平/stats 复算，不重复也不放宽引擎门的语义。
 *
 * 消费面仍是 internal（painter 生产装配留批次 31）；JVM 门覆盖解析侧，本扩展的 Canvas
 * 行为归设备轨（本环境不运行 Gradle，执行状态见批次结果）。
 */
internal const val PAINTER_DRAW_COMMANDS = "PAINTER_DRAW_COMMANDS"

/** 把一份 `Rendered` 的 DrawOps 文本解析并回放到 [canvas]；不改变 [VisualDrawOpsPainter] 语义。 */
internal fun VisualDrawOpsPainter.drawRendered(
    rendered: VisualExecutionResult.Rendered,
    canvas: Canvas,
    pxPerDu: Float,
    labelContent: (String) -> VisualLabelContent?,
) {
    val document = when (val parsed = VisualDrawOpsParser.parse(rendered)) {
        is VisualDrawOpsValidation.Valid -> parsed.document
        is VisualDrawOpsValidation.Invalid ->
            throw paintingFailure(PAINTER_DRAW_COMMANDS, "${parsed.code}: ${parsed.message}")
    }
    draw(document, canvas, pxPerDu, labelContent)
}
