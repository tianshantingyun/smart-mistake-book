package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.CompleteMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.CreateMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.FailMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.MistakeExportRecordRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 导出记录（阶段 4A 批 4 · L7）的持久化端口。
 *
 * 默认全部"做不到"（null / 空流）：测试替身与无库界面没有这张表。默认值刻意选"做不到"而
 * 不是"假装做到了"——worker 读不到记录就不会在通知里冒领一次导出（记录表的行才是"这次导出
 * 存在"的凭据）。
 */
interface ExportRecordPort {
    /** 入队一行 RUNNING；同一 `export_id` 重放返回已存在的行（幂等）。 */
    suspend fun createMistakeExportRecord(
        command: CreateMistakeExportRecordCommand,
    ): MistakeExportRecordRow? = null

    /** 写回成功终态（只对 RUNNING 行生效）；返回操作后该行的权威快照。 */
    suspend fun completeMistakeExportRecord(
        command: CompleteMistakeExportRecordCommand,
    ): MistakeExportRecordRow? = null

    /** 写回失败终态 + 学生可读原因（只对 RUNNING 行生效）。 */
    suspend fun failMistakeExportRecord(
        command: FailMistakeExportRecordCommand,
    ): MistakeExportRecordRow? = null

    /** 观察导出记录，新的在前（「导出成果」入口的数据源）。 */
    fun observeMistakeExportRecords(limit: Int): Flow<List<MistakeExportRecordRow>> =
        flowOf(emptyList())

    /** 读单行（worker 判定"这次导出是否早已落定"）。 */
    suspend fun readMistakeExportRecord(exportId: String): MistakeExportRecordRow? = null

    /**
     * 对账：把 [staleBeforeEpochMillis] 之前开始、仍停在 RUNNING 的行标为 FAILED
     * （原因 [failureMessage]），返回处理行数。消灭"worker 被杀死后没人写终态"的僵尸行。
     */
    suspend fun reconcileStaleMistakeExportRecords(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
        failureMessage: String,
    ): Int = 0

    /** 记录表上限：删最旧的、只留 `keepNewest` 条，返回删除行数。 */
    suspend fun pruneMistakeExportRecords(keepNewest: Int): Int = 0
}
