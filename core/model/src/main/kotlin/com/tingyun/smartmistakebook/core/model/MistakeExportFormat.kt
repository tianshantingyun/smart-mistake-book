package com.tingyun.smartmistakebook.core.model

/**
 * 错题导出的交付格式（6B-1）。
 *
 * 它消灭的失败：改前"导出"只有一条 PDF 路径，学生要的 Word 文件无处可选，worker 与成果入口
 * 也没有可持久的格式身份。这里给出**唯一**的格式定义：WorkManager 输入用 [token]、
 * 交付文件用 [fileExtension]、界面用 [studentLabel]——三处不各认一套拼写。
 *
 * 放在 core:model（而不是 core:export）：导出 sheet（feature:tutor）、请求编解码（app）与
 * 交付链（core:export）都要读它，只有这里会被三者同时依赖且不引入反向依赖。
 */
enum class MistakeExportFormat(
    /** WorkManager Data / 持久化标记里的稳定代号：不跟枚举名走，改名不换值。 */
    val token: String,
    /** 交付文件扩展名（不带点）。 */
    val fileExtension: String,
    /** 学生可读的短名（导出 sheet 的格式 chips 与完成文案）。 */
    val studentLabel: String,
) {
    PDF("pdf", "pdf", "PDF"),
    DOCX("docx", "docx", "Word 文档"),
    ;

    companion object {
        /**
         * 稳定代号 → 格式；不认识或缺失（null）返回 null，由调用方决定"整条拒"还是"按默认"。
         */
        fun fromToken(token: String?): MistakeExportFormat? =
            entries.firstOrNull { it.token == token }

        /**
         * 交付文件名 → 格式（成果入口按它分流保存/分享/打印）。
         *
         * 判据是**扩展名**：记录表零 schema（6B-1 明确不加列），displayName 是唯一被持久化的
         * 格式标记（PDF 记录本来就以 .pdf 结尾）。空名/不认识按 [PDF]——旧记录只有 PDF，
         * 而进行中的行还没有文件名（此时格式与交付动作都无从发生）。
         */
        fun ofDeliveryName(displayName: String?): MistakeExportFormat {
            val name = displayName?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { name.endsWith(".${it.fileExtension}") } ?: PDF
        }
    }
}
