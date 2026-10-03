package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.CompleteMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.CreateMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.FailMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.MISTAKE_EXPORT_STATUS_RUNNING
import com.tingyun.smartmistakebook.core.database.MistakeExportRecordRow
import com.tingyun.smartmistakebook.core.database.entity.MistakeExportRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 导出记录（L7）的读写口。两条不变量在这里、不在调用方：
 *
 * 1. **一次导出一行**：`export_id` 是主键，`INSERT IGNORE`——同一 id 重复入队是重放，
 *    返回已存在的那一行，不会再开第二次导出。
 * 2. **终态不可回头**：结果写走 `WHERE status = 'RUNNING'` 的比较交换；已有终态的行不被
 *    覆盖（即使 worker 因进程重建重跑一次）。重跑读到旧终态时返回旧行，调用方据此判定
 *    "这次结果早已落定"，不产生第二份通知。
 *
 * 记录表只承载结果，不承载渲染请求（请求在 WorkManager 输入里）；上限删除也在这里，
 * 见 [deleteOldestBeyond]。
 */
@Dao
internal abstract class MistakeExportRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRecord(entity: MistakeExportRecordEntity): Long

    @Query("SELECT * FROM mistake_export_record WHERE export_id = :exportId LIMIT 1")
    protected abstract suspend fun findRecord(exportId: String): MistakeExportRecordEntity?

    @Query(
        """
        SELECT * FROM mistake_export_record
        ORDER BY created_at_epoch_millis DESC, export_id DESC
        LIMIT :limit
        """,
    )
    protected abstract fun observeRecords(limit: Int): Flow<List<MistakeExportRecordEntity>>

    @Query(
        """
        SELECT * FROM mistake_export_record
        ORDER BY created_at_epoch_millis DESC, export_id DESC
        LIMIT :limit
        """,
    )
    protected abstract suspend fun readRecords(limit: Int): List<MistakeExportRecordEntity>

    @Query(
        """
        UPDATE mistake_export_record
        SET status = :status,
            display_name = :displayName,
            input_sha256 = :inputSha256,
            pdf_sha256 = :pdfSha256,
            page_count = :pageCount,
            finished_at_epoch_millis = :finishedAtEpochMillis
        WHERE export_id = :exportId
          AND status = :expectedStatus
        """,
    )
    protected abstract suspend fun updateSucceeded(
        exportId: String,
        expectedStatus: String,
        status: String,
        displayName: String,
        inputSha256: String,
        pdfSha256: String,
        pageCount: Int,
        finishedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE mistake_export_record
        SET status = :status,
            failure_message = :failureMessage,
            finished_at_epoch_millis = :finishedAtEpochMillis
        WHERE export_id = :exportId
          AND status = :expectedStatus
        """,
    )
    protected abstract suspend fun updateFailed(
        exportId: String,
        expectedStatus: String,
        status: String,
        failureMessage: String,
        finishedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE mistake_export_record
        SET status = :status,
            failure_message = :failureMessage,
            finished_at_epoch_millis = :finishedAtEpochMillis
        WHERE status = :expectedStatus
          AND created_at_epoch_millis < :staleBeforeEpochMillis
        """,
    )
    protected abstract suspend fun failStaleRunning(
        staleBeforeEpochMillis: Long,
        expectedStatus: String,
        status: String,
        failureMessage: String,
        finishedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM mistake_export_record
        WHERE export_id NOT IN (
            SELECT export_id FROM mistake_export_record
            ORDER BY created_at_epoch_millis DESC, export_id DESC
            LIMIT :keepNewest
        )
        """,
    )
    protected abstract suspend fun deleteOldestBeyond(keepNewest: Int): Int

    @Transaction
    open suspend fun createRunning(command: CreateMistakeExportRecordCommand): MistakeExportRecordRow {
        require(command.exportId.isNotBlank()) { "An export id must not be blank" }
        require(command.kind.isNotBlank()) { "An export kind must not be blank" }
        require(command.createdAtEpochMillis >= 0L) {
            "An export creation time must not be negative"
        }
        val entity = MistakeExportRecordEntity(
            exportId = command.exportId,
            kind = command.kind,
            status = MISTAKE_EXPORT_STATUS_RUNNING,
            createdAtEpochMillis = command.createdAtEpochMillis,
        )
        if (insertRecord(entity) != -1L) return entity.toRow()
        return checkNotNull(findRecord(command.exportId)) {
            "Export record insert was not readable"
        }.toRow()
    }

    @Transaction
    open suspend fun complete(command: CompleteMistakeExportRecordCommand): MistakeExportRecordRow? {
        require(command.exportId.isNotBlank()) { "An export id must not be blank" }
        require(command.displayName.isNotBlank()) { "An exported file needs a display name" }
        require(command.inputSha256.isNotBlank()) { "An exported file needs its input fingerprint" }
        require(command.pdfSha256.isNotBlank()) { "An exported file needs its PDF fingerprint" }
        require(command.pageCount >= 1) { "An exported file needs at least one page" }
        require(command.finishedAtEpochMillis >= 0L) {
            "An export finish time must not be negative"
        }
        updateSucceeded(
            exportId = command.exportId,
            expectedStatus = MISTAKE_EXPORT_STATUS_RUNNING,
            status = SUCCEEDED,
            displayName = command.displayName,
            inputSha256 = command.inputSha256,
            pdfSha256 = command.pdfSha256,
            pageCount = command.pageCount,
            finishedAtEpochMillis = command.finishedAtEpochMillis,
        )
        return findRecord(command.exportId)?.toRow()
    }

    @Transaction
    open suspend fun fail(command: FailMistakeExportRecordCommand): MistakeExportRecordRow? {
        require(command.exportId.isNotBlank()) { "An export id must not be blank" }
        require(command.failureMessage.isNotBlank()) { "An export failure needs a reason" }
        require(command.finishedAtEpochMillis >= 0L) {
            "An export finish time must not be negative"
        }
        updateFailed(
            exportId = command.exportId,
            expectedStatus = MISTAKE_EXPORT_STATUS_RUNNING,
            status = FAILED,
            failureMessage = command.failureMessage,
            finishedAtEpochMillis = command.finishedAtEpochMillis,
        )
        return findRecord(command.exportId)?.toRow()
    }

    open suspend fun read(exportId: String): MistakeExportRecordRow? {
        require(exportId.isNotBlank()) { "An export id must not be blank" }
        return findRecord(exportId)?.toRow()
    }

    open fun observe(limit: Int): Flow<List<MistakeExportRecordRow>> {
        require(limit > 0) { "An export record read limit must be positive" }
        return observeRecords(limit).map { rows -> rows.map(MistakeExportRecordEntity::toRow) }
    }

    open suspend fun readAll(limit: Int): List<MistakeExportRecordRow> {
        require(limit > 0) { "An export record read limit must be positive" }
        return readRecords(limit).map(MistakeExportRecordEntity::toRow)
    }

    /** 记录表上限：只删最旧的行；产物文件由 export 缓存自己的上限与 TTL 兜底，不在这里删。 */
    open suspend fun prune(keepNewest: Int): Int {
        require(keepNewest >= 1) { "An export record retention must keep at least one record" }
        return deleteOldestBeyond(keepNewest)
    }

    /**
     * 对账被中断的 RUNNING 行（worker 被杀死后没人写终态）。
     *
     * 与 [prune] 的取舍：上限清理**不区分状态**（僵尸 RUNNING 由这里标成 FAILED，不靠"少删
     * 一点"兜底）——否则反复中断能把记录表撑满 RUNNING，清理反而失效。
     */
    open suspend fun reconcileStaleRunning(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
        failureMessage: String,
    ): Int {
        require(staleBeforeEpochMillis >= 0L) {
            "A stale export cutoff must not be negative"
        }
        require(atEpochMillis >= 0L) { "An export reconciliation time must not be negative" }
        require(failureMessage.isNotBlank()) { "An interrupted export needs a reason" }
        return failStaleRunning(
            staleBeforeEpochMillis = staleBeforeEpochMillis,
            expectedStatus = MISTAKE_EXPORT_STATUS_RUNNING,
            status = FAILED,
            failureMessage = failureMessage,
            finishedAtEpochMillis = atEpochMillis,
        )
    }

    private companion object {
        const val SUCCEEDED = "SUCCEEDED"
        const val FAILED = "FAILED"
    }
}

internal fun MistakeExportRecordEntity.toRow() = MistakeExportRecordRow(
    exportId = exportId,
    kind = kind,
    displayName = displayName,
    status = status,
    inputSha256 = inputSha256,
    pdfSha256 = pdfSha256,
    pageCount = pageCount,
    failureMessage = failureMessage,
    createdAtEpochMillis = createdAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
)
