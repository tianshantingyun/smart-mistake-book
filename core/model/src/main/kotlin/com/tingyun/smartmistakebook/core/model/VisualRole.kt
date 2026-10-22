package com.tingyun.smartmistakebook.core.model

/**
 * 一张视觉资产在题面/讲解/导出里的**角色单源**（4E WG-08）。
 *
 * **它消灭的具体失败**：改前同一个 `"CLEAN_IMAGE"` 字面量在题保存事务、详情读口、PDF 资格
 * 与详情路由里各写一份（`ProblemDraftTransactionDao` / `MistakeDetailDao` /
 * `MistakePdfEligibility` / `MistakeDetailRoute`）。任何一处新增角色或拼错，编译期都看不出来，
 * 读侧就会静默把讲解图当题目图（或反过来）——导出会出现"题目区画着讲解图"这种像素级错误。
 *
 * 持久值就是枚举名（数据库 `role` 列仍为 TEXT）。旧行的 `NULL` 继续表示"角色未知"，
 * 由各读侧的既有兼容分支处理；这里**不造 UNKNOWN**，不把"未知"伪装成一种真实角色。
 */
enum class VisualRole {
    /** 保存期净化 / 显式重绘产出的干净题面（题目区优先图）。 */
    CLEAN_IMAGE,

    /** 讲题工具产出的讲解过程图（解析区图，不是题目本身）。 */
    TUTOR_EXPLANATION,

    /** 题目自带的示意图（题面的一部分，用于没有干净题面时兜底）。 */
    QUESTION_DIAGRAM,

    /** 用户拍摄/选择的源证据（原题照片等，用作证据而不是展示题面）。 */
    SOURCE_EVIDENCE,
}

/**
 * 草稿源照片（原题照片、页束）在 `problem_revision_source_asset` 上的持久角色值。
 *
 * 它不是 [VisualRole] 的成员：四角色描述的是**生成图**的用途，而源照片是保存事务从草稿
 * 页束直接复制过来的既有事实。值在这里只写一次，避免保存链、文本修订复制链与读侧各写
 * 一份字面量（此前 `ProblemDraftTransactionDao` 与测试里各有一份）。
 */
const val QUESTION_SOURCE_ROLE: String = "QUESTION_SOURCE"

/**
 * 讲题工具 trace 的图 kind → 持久视觉角色（4E WG-06 单源）。
 *
 * 重绘题面产出干净题面图（题目区），生成过程产出讲解图（解析区）。kind 只映射用途，
 * 不映射方法；方法仍由 `FigureGenerationMethod` 表达。
 */
fun TutorFigureKind.visualRole(): VisualRole = when (this) {
    TutorFigureKind.REDRAW_PROBLEM -> VisualRole.CLEAN_IMAGE
    TutorFigureKind.GENERATE_PROCESS -> VisualRole.TUTOR_EXPLANATION
}

/**
 * 用户改题面（文本修订）时允许从旧 revision 复制到新 revision 的角色白名单。
 *
 * 只有**源照片**与**源证据**跟着题面走；干净题面图、讲解图、题图都冻结在旧题面上。
 * NULL（旧行角色未知）不猜——调用方必须原样排除，不得把未知当成任一角色。
 */
fun carriesAcrossTextRevision(role: String?): Boolean = when (role) {
    QUESTION_SOURCE_ROLE,
    VisualRole.SOURCE_EVIDENCE.name,
    -> true

    else -> false
}
