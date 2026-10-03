package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MISTAKE_EXPORT_INTERRUPTED_MESSAGE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导出记录端口（L7）的真库行为：终态只落一次、失败带原因、上限只删最旧。
 *
 * 与 `FullMigrationMatrixInstrumentedTest` 的分工：那边管"迁移后表建没建出来、旧行在不在"，
 * 这里管读写口的不变量（`MistakeExportRecordDao` 的 KDoc 三条）。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportRecordInstrumentedTest {

    @Test
    fun runningRecordSettlesOnceAndTheRetentionLimitDropsOnlyTheOldest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "export-record-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName)

            val started = store.createMistakeExportRecord(
                CreateMistakeExportRecordCommand(
                    exportId = "export:1",
                    kind = MISTAKE_EXPORT_KIND_SINGLE,
                    createdAtEpochMillis = 1_000,
                ),
            )
            assertEquals("入队只写 RUNNING", "RUNNING", started?.status)
            assertNull("成功前文件名必须是 NULL（不许提前编）", started?.displayName)

            val completed = store.completeMistakeExportRecord(
                CompleteMistakeExportRecordCommand(
                    exportId = "export:1",
                    displayName = "错题-测试-第1版.pdf",
                    inputSha256 = "a".repeat(64),
                    pdfSha256 = "b".repeat(64),
                    pageCount = 3,
                    finishedAtEpochMillis = 2_000,
                ),
            )
            assertEquals("SUCCEEDED", completed?.status)
            assertEquals(3, completed?.pageCount)
            assertEquals("错题-测试-第1版.pdf", completed?.displayName)

            // 终态不回头：后来的一次失败写不覆盖已成功的结果（重跑/重放不会产生矛盾的两态）。
            val stillSucceeded = store.failMistakeExportRecord(
                FailMistakeExportRecordCommand(
                    exportId = "export:1",
                    failureMessage = "这次导出意外中断，请重新导出。",
                    finishedAtEpochMillis = 3_000,
                ),
            )
            assertEquals("SUCCEEDED", stillSucceeded?.status)
            assertNull(stillSucceeded?.failureMessage)

            val failed = store.createMistakeExportRecord(
                CreateMistakeExportRecordCommand(
                    exportId = "export:failed",
                    kind = MISTAKE_EXPORT_KIND_BATCH,
                    createdAtEpochMillis = 1_500,
                ),
            )
            assertEquals("BATCH", failed?.kind)
            val failedTerminal = store.failMistakeExportRecord(
                FailMistakeExportRecordCommand(
                    exportId = "export:failed",
                    failureMessage = "当前没有可导出的错题。",
                    finishedAtEpochMillis = 2_500,
                ),
            )
            assertEquals("FAILED", failedTerminal?.status)
            assertEquals("当前没有可导出的错题。", failedTerminal?.failureMessage)
            assertNull("失败行没有产物钥匙", failedTerminal?.inputSha256)

            // 上限：再入队 30 条（最新 30 条是 export:2..31），prune(30) 只该删最旧的 export:1。
            for (index in 2..31) {
                store.createMistakeExportRecord(
                    CreateMistakeExportRecordCommand(
                        exportId = "export:$index",
                        kind = MISTAKE_EXPORT_KIND_BATCH,
                        createdAtEpochMillis = 1_000L + index,
                    ),
                )
            }
            assertEquals(2, store.pruneMistakeExportRecords(30))
            val remaining = store.observeMistakeExportRecords(100).first()
            assertEquals("只留上限条数", 30, remaining.size)
            assertTrue(
                "删的必须是最旧的两条（export:1 @1000 与 export:2 @1002）",
                remaining.none { it.exportId == "export:1" || it.exportId == "export:2" },
            )
            assertTrue(
                "较新的失败行按时间保留（上限清理不挑状态）",
                remaining.any { it.exportId == "export:failed" },
            )
            assertEquals(
                "新的在前：失败行 @1500 比 export:2..31（@1002..1031）都新",
                "export:failed",
                remaining.first().exportId,
            )

            store.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * P1 对账：worker 被系统/进程杀死后没人写终态，RUNNING 行会永远"正在整理"。对账只把
     * **超过阈值**的 RUNNING 行标成 FAILED（原因如实），不动仍在正常排队的行与已落终态的行。
     */
    @Test
    fun staleRunningRecordsAreReconciledWithoutTouchingFreshOrSettledRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "export-reconcile-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName)

            store.createMistakeExportRecord(
                CreateMistakeExportRecordCommand(
                    exportId = "export:stale",
                    kind = MISTAKE_EXPORT_KIND_SINGLE,
                    createdAtEpochMillis = 1_000_000,
                ),
            )
            store.createMistakeExportRecord(
                CreateMistakeExportRecordCommand(
                    exportId = "export:fresh",
                    kind = MISTAKE_EXPORT_KIND_BATCH,
                    createdAtEpochMillis = 5_000_000,
                ),
            )
            store.createMistakeExportRecord(
                CreateMistakeExportRecordCommand(
                    exportId = "export:done",
                    kind = MISTAKE_EXPORT_KIND_SINGLE,
                    createdAtEpochMillis = 900_000,
                ),
            )
            store.completeMistakeExportRecord(
                CompleteMistakeExportRecordCommand(
                    exportId = "export:done",
                    displayName = "错题-测试-第1版.pdf",
                    inputSha256 = "a".repeat(64),
                    pdfSha256 = "b".repeat(64),
                    pageCount = 1,
                    finishedAtEpochMillis = 950_000,
                ),
            )

            val reconciled = store.reconcileStaleMistakeExportRecords(
                staleBeforeEpochMillis = 4_000_000,
                atEpochMillis = 6_000_000,
                failureMessage = MISTAKE_EXPORT_INTERRUPTED_MESSAGE,
            )

            assertEquals("只该对账超阈值的那一条", 1, reconciled)
            val stale = store.readMistakeExportRecord("export:stale")
            assertEquals("FAILED", stale?.status)
            assertEquals(MISTAKE_EXPORT_INTERRUPTED_MESSAGE, stale?.failureMessage)
            assertEquals(6_000_000L, stale?.finishedAtEpochMillis)
            assertEquals(
                "仍在排队（阈值内）的行不许动",
                "RUNNING",
                store.readMistakeExportRecord("export:fresh")?.status,
            )
            assertEquals(
                "已落终态的行不许被改写",
                "SUCCEEDED",
                store.readMistakeExportRecord("export:done")?.status,
            )

            store.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
