package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 一次导出的对象形态：单题（详情页）或当前筛选（错题本列表）。 */
enum class MistakeExportKind {
    SINGLE,
    BATCH,
}

/**
 * 导出记录表上限（清理策略的域侧口径，core:data 的 Room 实现读它）。
 *
 * 记录**保留至用户处理**：上限只删最旧的行，不按时间删"学生还没看过"的结果；产物文件由
 * `MistakePdfExporter` 的既有缓存上限（24 个 / 64MB / 24h TTL）兜底，不在记录表里删。
 */
const val MAX_MISTAKE_EXPORT_RECORDS = 30

/**
 * RUNNING 行超过这个时长仍未落终态即视为**被中断**（见
 * [MistakeExportRepository.reconcileStaleRunningRecords]）。
 *
 * 一小时是"排队等待执行"与"已经死掉"的保守分界：expedited 配额不足时任务会降级排队，
 * 短窗口会把仍在队列里的导出误判为失败；一小时后仍未完成，对学生来说这次导出事实上已经
 * 没有进展，如实标失败并让他重新发起。
 */
const val MISTAKE_EXPORT_RUNNING_TIMEOUT_MILLIS = 60L * 60L * 1000L

/** 中断记录的学生可读原因（对账与 worker 最终放弃共用一句）。 */
const val MISTAKE_EXPORT_INTERRUPTED_MESSAGE = "这次导出被中断了，请重新导出。"

/**
 * 一次导出在「导出成果」入口里所处的相位。
 *
 * 三态而不是布尔：进行中既不是成功也不是失败，提前归入任何一边都是撒谎。
 */
enum class MistakeExportStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
}

/**
 * 一条导出记录（L7 后台导出 + 成果入口的读侧模型）。
 *
 * [displayName] / [inputSha256] / [pdfSha256] / [pageCount] 只在 [status] == [SUCCEEDED] 时
 * 齐备；进行中为 null（文件名要到 worker 读到不可变快照才确定）；失败时 [failureMessage]
 * 是学生可读的原因。
 */
data class MistakeExportRecord(
    val exportId: String,
    val kind: MistakeExportKind,
    val displayName: String?,
    val status: MistakeExportStatus,
    val inputSha256: String?,
    val pdfSha256: String?,
    val pageCount: Int?,
    val failureMessage: String?,
    val createdAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
) {
    init {
        require(exportId.isNotBlank()) { "An export record needs an id" }
        require(createdAtEpochMillis >= 0) { "An export creation time must not be negative" }
        when (status) {
            MistakeExportStatus.RUNNING -> require(finishedAtEpochMillis == null) {
                "A running export cannot have a finish time"
            }
            MistakeExportStatus.SUCCEEDED -> require(
                displayName != null && inputSha256 != null && pdfSha256 != null && pageCount != null,
            ) { "A succeeded export must carry its artifact identity" }
            MistakeExportStatus.FAILED -> require(failureMessage != null) {
                "A failed export must carry its reason"
            }
        }
    }

    /**
     * 成功记录的产物钥匙（与 `MistakePdfExporter.reopenVerified` 的 token 同形）。
     * 未成功或钥匙不全时为 null——调用方据此如实说"还没有可打开的文件"。
     */
    fun preparedPdfValueOrNull(): String? {
        if (status != MistakeExportStatus.SUCCEEDED) return null
        val input = inputSha256 ?: return null
        val pdf = pdfSha256 ?: return null
        val pages = pageCount ?: return null
        return "$input:$pdf:$pages"
    }
}

/**
 * 导出记录的读侧与两处写口（L7）。
 *
 * 写口只有两个时刻：入队（[recordStarted]）与终态（[recordSucceeded] / [recordFailed]）。
 * 渲染请求本身不在这里——它在 WorkManager 输入里，记录表只承载"结果"。
 */
interface MistakeExportRepository {
    /** 「导出成果」入口的数据源：新的在前。 */
    fun observeRecords(): Flow<List<MistakeExportRecord>> = flowOf(emptyList())

    /** 入队一行 RUNNING；同一 [exportId] 重放返回已存在的行。 */
    suspend fun recordStarted(
        exportId: String,
        kind: MistakeExportKind,
        atEpochMillis: Long,
    ): MistakeExportRecord? = null

    /** 写回成功终态（只对 RUNNING 行生效）；返回操作后该行的权威快照。 */
    suspend fun recordSucceeded(
        exportId: String,
        displayName: String,
        inputSha256: String,
        pdfSha256: String,
        pageCount: Int,
        atEpochMillis: Long,
    ): MistakeExportRecord? = null

    /** 写回失败终态 + 学生可读原因（只对 RUNNING 行生效）。 */
    suspend fun recordFailed(
        exportId: String,
        failureMessage: String,
        atEpochMillis: Long,
    ): MistakeExportRecord? = null

    /** 读单行；worker 用它判定"这次导出是否早已落定"。 */
    suspend fun readRecord(exportId: String): MistakeExportRecord? = null

    /**
     * 对账：把 [staleBeforeEpochMillis] 之前开始、仍停在 [MistakeExportStatus.RUNNING] 的行
     * 标为 FAILED（原因见 [MISTAKE_EXPORT_INTERRUPTED_MESSAGE]），返回处理行数。
     *
     * **它消灭的失败**：worker 被系统/进程杀死后没人写终态，记录永远"正在整理"——既不完成
     * 也不失败，学生只能重新导出。调用点只放在两个明确时刻（用户再次导出前、应用启动），
     * 且只动阈值之外的行；仍在队列里正常排队的导出不受影响。
     */
    suspend fun reconcileStaleRunningRecords(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
    ): Int = 0
}
