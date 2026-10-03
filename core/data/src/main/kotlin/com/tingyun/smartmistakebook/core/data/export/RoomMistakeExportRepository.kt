package com.tingyun.smartmistakebook.core.data.export

import com.tingyun.smartmistakebook.core.database.CompleteMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.CreateMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.FailMistakeExportRecordCommand
import com.tingyun.smartmistakebook.core.database.MistakeExportRecordRow
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.MISTAKE_EXPORT_INTERRUPTED_MESSAGE
import com.tingyun.smartmistakebook.core.domain.MAX_MISTAKE_EXPORT_RECORDS
import com.tingyun.smartmistakebook.core.domain.MistakeExportKind
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.MistakeExportRepository
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 导出记录（L7）的 Room 实现。
 *
 * 清理策略（与 `MistakeExportRecordEntity` 的 KDoc 同一条）：记录**保留至用户处理**，表按
 * [MAX_MISTAKE_EXPORT_RECORDS] 上限只删最旧的行——每次终态写回（成功或失败）后修剪一次。
 * **产物文件不在这里删**：它归 `MistakePdfExporter` 的既有缓存上限（24 个 / 64MB / 24h TTL）
 * 管；某条记录的文件先被缓存清理时，成果入口的分享/保存/打印会如实说"文件已清理，请重新
 * 导出"，不会静默失败。
 */
internal class RoomMistakeExportRepository(
    private val database: StudyDatabasePort,
    private val retentionLimit: Int = MAX_MISTAKE_EXPORT_RECORDS,
) : MistakeExportRepository {

    override fun observeRecords(): Flow<List<MistakeExportRecord>> =
        database.observeMistakeExportRecords(retentionLimit).map { rows ->
            rows.map(MistakeExportRecordRow::toDomain)
        }

    override suspend fun recordStarted(
        exportId: String,
        kind: MistakeExportKind,
        atEpochMillis: Long,
    ): MistakeExportRecord? = withContext(Dispatchers.IO) {
        database.createMistakeExportRecord(
            CreateMistakeExportRecordCommand(
                exportId = exportId,
                kind = kind.databaseValue(),
                createdAtEpochMillis = atEpochMillis,
            ),
        )?.toDomain()
    }

    override suspend fun recordSucceeded(
        exportId: String,
        displayName: String,
        inputSha256: String,
        pdfSha256: String,
        pageCount: Int,
        atEpochMillis: Long,
    ): MistakeExportRecord? = withContext(Dispatchers.IO) {
        val record = database.completeMistakeExportRecord(
            CompleteMistakeExportRecordCommand(
                exportId = exportId,
                displayName = displayName,
                inputSha256 = inputSha256,
                pdfSha256 = pdfSha256,
                pageCount = pageCount,
                finishedAtEpochMillis = atEpochMillis,
            ),
        )?.toDomain()
        // 修剪失败不影响这次导出已经落定的结果：记录上限是打扫，不是正确性。
        runCatching { database.pruneMistakeExportRecords(retentionLimit) }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
            }
        record
    }

    override suspend fun recordFailed(
        exportId: String,
        failureMessage: String,
        atEpochMillis: Long,
    ): MistakeExportRecord? = withContext(Dispatchers.IO) {
        val record = database.failMistakeExportRecord(
            FailMistakeExportRecordCommand(
                exportId = exportId,
                failureMessage = failureMessage,
                finishedAtEpochMillis = atEpochMillis,
            ),
        )?.toDomain()
        // 失败也修剪：否则"反复导出失败"能无界地把记录表撑大。
        runCatching { database.pruneMistakeExportRecords(retentionLimit) }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
            }
        record
    }

    override suspend fun readRecord(exportId: String): MistakeExportRecord? = withContext(Dispatchers.IO) {
        database.readMistakeExportRecord(exportId)?.toDomain()
    }

    override suspend fun reconcileStaleRunningRecords(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
    ): Int = withContext(Dispatchers.IO) {
        database.reconcileStaleMistakeExportRecords(
            staleBeforeEpochMillis = staleBeforeEpochMillis,
            atEpochMillis = atEpochMillis,
            failureMessage = MISTAKE_EXPORT_INTERRUPTED_MESSAGE,
        )
    }
}

/** 导出记录（L7）的装配点：worker、入队与「导出成果」入口共用一个实现。 */
object MistakeExportRepositoryFactory {
    fun create(database: StudyDatabasePort): MistakeExportRepository =
        RoomMistakeExportRepository(database)
}

private fun MistakeExportKind.databaseValue(): String = when (this) {
    MistakeExportKind.SINGLE -> "SINGLE"
    MistakeExportKind.BATCH -> "BATCH"
}

internal fun MistakeExportRecordRow.toDomain(): MistakeExportRecord = MistakeExportRecord(
    exportId = exportId,
    kind = when (kind) {
        "SINGLE" -> MistakeExportKind.SINGLE
        "BATCH" -> MistakeExportKind.BATCH
        else -> error("Unknown stored export kind: $kind")
    },
    displayName = displayName,
    status = MistakeExportStatus.valueOf(status),
    inputSha256 = inputSha256,
    pdfSha256 = pdfSha256,
    pageCount = pageCount,
    failureMessage = failureMessage,
    createdAtEpochMillis = createdAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
)
