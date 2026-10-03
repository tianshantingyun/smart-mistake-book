package com.tingyun.smartmistakebook.core.export

/*
 * 导出面向学生的文案与文件名（阶段 4A 批 4 从 feature/library 的导出页迁来）。
 *
 * 迁到 core:export 的原因：同一批文案现在有两个消费者——后台 worker（把失败原因写进导出
 * 记录 / 通知）与「导出成果」入口（显示记录）。放在 UI 模块里，worker 就得在 app 层重抄
 * 一份，两边迟早说不一样的话。
 */

/** 单题导出的文件名：去掉不安全路径字符，钉住版次。 */
fun exportDisplayName(title: String, revisionNumber: Int): String {
    val safeTitle = title
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cc}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)
        .ifBlank { "未命名错题" }
    return "错题-$safeTitle-第${revisionNumber}版.pdf"
}

/** 批量导出的文件名：纳入题数在快照确定后才写进名字。 */
fun batchExportDisplayName(includedCount: Int): String = "错题练习-${includedCount}道.pdf"

/**
 * 单题不可导出的学生可读原因。
 *
 * 筛选层口径（L5 后）：还剩科目/板块/掌握程度/录入时间段——文案不再提已退场的"知识点"。
 */
fun exportBlockedMessage(reasons: Set<MistakePdfIneligibility>): String = when {
    MistakePdfIneligibility.UNSUPPORTED_BLOCK in reasons ->
        "题面里有暂时不能稳定排版的图形。为避免导出失真的题目，这一版先不生成文件。"
    MistakePdfIneligibility.LEGACY_CONTENT in reasons ->
        "这是一条旧格式记录，题面不完整，暂时无法导出。"
    MistakePdfIneligibility.CORRUPT_CONTENT in reasons ->
        "这版题面没有通过完整性检查，因此不会用备用文字拼出一份可能错误的文件。"
    MistakePdfIneligibility.NOT_FOUND in reasons ->
        "没有找到这一版题目，它可能已经不在当前错题本中。"
    MistakePdfIneligibility.UNCONFIRMED_OR_INVALID_DOCUMENT in reasons ->
        "这版题面还不完整，暂时无法导出。"
    MistakePdfIneligibility.EMPTY_SUPPORTED_BLOCK in reasons ->
        "这版题面有空白内容，暂时无法生成完整的练习页。"
    MistakePdfIneligibility.INVALID_CHOICE_STATE in reasons ->
        "这版选择题的选项状态不完整，暂时无法可靠排版。"
    else -> "这版题面还没有准备好，请稍后重新导出。"
}

/** 单题渲染失败的学生可读原因。 */
fun exportFailureMessage(failure: MistakePdfExportFailure): String = when (failure) {
    MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED,
    MistakePdfExportFailure.PDF_SIZE_LIMIT_EXCEEDED,
    -> "这道题的内容超出了单题 A4 导出的范围，题目仍会原样保留。"
    MistakePdfExportFailure.EXISTING_FILE_INTEGRITY_CONFLICT,
    MistakePdfExportFailure.GENERATED_PDF_INVALID,
    MistakePdfExportFailure.FILE_PUBLISH_FAILED,
    -> "这次没有整理出完整的 A4 文件，请稍后重新导出。"
}

/** 批量不可导出的学生可读原因。 */
fun batchBlockedMessage(reason: MistakePdfBatchIneligibility): String = when (reason) {
    MistakePdfBatchIneligibility.EMPTY -> "当前没有可导出的错题。"
    MistakePdfBatchIneligibility.TOO_MANY_QUESTIONS ->
        "当前结果较多。按科目、板块、掌握程度或录入时间段筛选后，就能直接导出。"
    MistakePdfBatchIneligibility.NO_READY_QUESTION ->
        "当前这些题的题面还没有整理完整，暂时不能生成练习文件。"
}

/** 批量渲染失败的学生可读原因。 */
fun batchExportFailureMessage(failure: MistakePdfExportFailure): String = when (failure) {
    MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED,
    MistakePdfExportFailure.PDF_SIZE_LIMIT_EXCEEDED,
    -> "当前结果的页数较多。缩小筛选范围后再导出，错题内容不会改变。"
    MistakePdfExportFailure.EXISTING_FILE_INTEGRITY_CONFLICT,
    MistakePdfExportFailure.GENERATED_PDF_INVALID,
    MistakePdfExportFailure.FILE_PUBLISH_FAILED,
    -> "这次没有整理出完整的 A4 文件，请稍后重新导出。"
}
