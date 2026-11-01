package com.tingyun.smartmistakebook.core.database

/**
 * 导出记录行（阶段 4A 批 4 · L7）。字段口径见
 * [com.tingyun.smartmistakebook.core.database.entity.MistakeExportRecordEntity] 的 KDoc。
 */
data class MistakeExportRecordRow(
    val exportId: String,
    val kind: String,
    val displayName: String?,
    val status: String,
    val inputSha256: String?,
    val pdfSha256: String?,
    val pageCount: Int?,
    val failureMessage: String?,
    val createdAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
)

/**
 * 入队一次导出：只写"已开始"。文件名/产物钥匙等结果列由完成命令写。
 *
 * 状态不由调用方给：落库那一刻它只可能是 RUNNING——与待确认请求同一口径。
 */
data class CreateMistakeExportRecordCommand(
    val exportId: String,
    val kind: String,
    val createdAtEpochMillis: Long,
)

/** 写回成功终态：文件名 + 产物钥匙。只对 RUNNING 行生效（终态不回头）。 */
data class CompleteMistakeExportRecordCommand(
    val exportId: String,
    val displayName: String,
    val inputSha256: String,
    val pdfSha256: String,
    val pageCount: Int,
    val finishedAtEpochMillis: Long,
)

/** 写回失败终态 + 学生可读原因。只对 RUNNING 行生效（终态不回头）。 */
data class FailMistakeExportRecordCommand(
    val exportId: String,
    val failureMessage: String,
    val finishedAtEpochMillis: Long,
)

/** 进行中（唯一非终态）。与 core:domain 的 `MistakeExportStatus.RUNNING` 同名同义。 */
internal const val MISTAKE_EXPORT_STATUS_RUNNING = "RUNNING"

internal const val MISTAKE_EXPORT_KIND_SINGLE = "SINGLE"
internal const val MISTAKE_EXPORT_KIND_BATCH = "BATCH"
