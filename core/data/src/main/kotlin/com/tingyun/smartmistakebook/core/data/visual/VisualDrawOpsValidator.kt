package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualViewport

/**
 * DrawOps V1 的**原生严格校验器**（批次 27 T1；R1 §2 管线第 4 步"原生严格校验"的
 * DrawOps 扩展面、§7.1 建议的 JVM 纯函数层）。
 *
 * **它消灭的具体失败**：编译器跑在被不可信程序输出喂过的进程里——"编译器是可信 SDK"
 * 与"它可能带出越界值"是两回事（R1 §3.1 表第 3 行）。编译输出必须在原生侧按与编译器
 * **同一套不变量**复验（词表 / 预算 / 有限性 / save-restore 配平 / contentId 冻结引用），
 * 任何不符 = 有界拒绝（INVALID_SCENE + DRAWOPS_* 码），不进 INVALID_PROGRAM 泛化，
 * 也绝不把未验 JSON 交给 painter。
 *
 * **批次 29 T2 起结构解析下沉到 [VisualDrawOpsParser]**：本对象保留为"引擎提交门"的
 * 命名入口（引擎 `commitRendered` 与 JVM 负向矩阵都调它），语义与批 27 相同——字节预算 →
 * 结构/词表/预算/配平/stats 复算 → viewBox 回显视口。回放侧（`Rendered.drawCommandsJson`
 * 无 viewport 可用）改用 `VisualDrawOpsParser.parse(rendered)`；两入口共享同一份解析实现，
 * 不存在"两道门各写一套词表"的漂移面。
 *
 * 与 JS 侧的关系：编译器（`visual-compiler.js`）是第一道门（生成侧），本校验器是第二道门
 * （消费侧）；两者词表/预算同值（Kotlin 侧常量单源 [com.tingyun.smartmistakebook.core.domain
 * .VisualExecutionBudget]，JS 侧文件头注明同值镜像，改预算必须两端同改——Node 测试台与
 * JVM 负向矩阵各自钉住）。
 *
 * 纯函数（JSON 文本 → [VisualDrawOpsValidation]），与 [VisualJsFailureClassification] 同型，
 * 全 JVM 覆盖（`VisualDrawOpsValidatorTest` 负向矩阵 + `VisualDrawOpsParserTest` 往返矩阵）。
 */
internal object VisualDrawOpsValidator {

    /**
     * 引擎提交门：解析并复验 viewBox 回显 [viewport]。
     *
     * @param frozenContentIds 本事务冻结的 contentId 集合（label 悬空引用即拒）。
     */
    fun validate(
        text: String,
        frozenContentIds: Set<String>,
        viewport: VisualViewport,
    ): VisualDrawOpsValidation = VisualDrawOpsParser.parse(text, frozenContentIds, viewport)
}
