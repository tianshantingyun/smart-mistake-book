package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E WG-08 / T5 的僵尸 RUNNING 对账（真库）。
 *
 * "杀进程"的模拟：上一进程留下的 RUNNING 行（时间戳早于本进程启动）原样躺在库里，随后用
 * [StudyDatabaseFactory.open] 重开并跑 [FigureGenerationReconciler]。断言两件事：
 * - 这些行不再停在 RUNNING（操作/尝试都落 FAILED，尝试带"待核实"诊断与结束时刻）；
 * - 本进程的 RUNNING（时间戳晚于启动）不被误杀；行数不变——**没有自动重派、没有新尝试**。
 *
 * 产物保留也在这里钉住：带 `output_asset_id` 的僵尸尝试在标 FAILED 后仍保留该列（GC 与备份
 * 继续 pin 它，见 `PendingCaptureDao.findUnreferencedCanonicalAssets`）。
 *
 * F13：协调器在入口落盘前就写 SUCCEEDED；`output_asset_id` 一直为空说明 persist/attach
 * 失败。对账必须把这种"假成功"纠正为 FAILED（独立诊断前缀），且不自动重派。
 */
@RunWith(AndroidJUnit4::class)
class FigureGenerationReconcilerInstrumentedTest {

    @Test
    fun staleRunningRowsAreTerminalAfterAProcessKillAndAreNeverRedispatched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "figure-reconcile-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            val outputAsset = CanonicalSourceAssetRecord(
                sourceAssetId = "asset-reconcile-output",
                contentSha256 = "c".repeat(64),
                relativePath = "source-assets/${"c".repeat(64)}.jpg",
                mimeType = "image/jpeg",
                byteSize = 128,
                width = 10,
                height = 10,
                sourceType = "CAMERA",
                createdAtEpochMillis = 1_000,
            )
            database.registerCanonicalSourceAsset(outputAsset)
            // 本进程启动时刻（夹具随后写入的行要晚于它；上一进程的僵尸行早于它）。
            val processStartedAtEpochMillis = System.currentTimeMillis() - 1_000L
            val freshTimestamp = System.currentTimeMillis() + 60_000L

            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-stale-with-output",
                attemptId = "attempt-stale-with-output",
                operationStatus = "RUNNING",
                attemptStatus = "RUNNING",
                outputAssetId = outputAsset.sourceAssetId,
                createdAtEpochMillis = 1_000,
                updatedAtEpochMillis = 2_000,
                startedAtEpochMillis = 2_000,
            )
            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-stale-without-output",
                attemptId = "attempt-stale-without-output",
                operationStatus = "RUNNING",
                attemptStatus = "RUNNING",
                outputAssetId = null,
                createdAtEpochMillis = 1_000,
                updatedAtEpochMillis = 2_000,
                startedAtEpochMillis = 2_000,
            )
            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-fresh-this-process",
                attemptId = "attempt-fresh-this-process",
                operationStatus = "RUNNING",
                attemptStatus = "RUNNING",
                outputAssetId = null,
                createdAtEpochMillis = freshTimestamp,
                updatedAtEpochMillis = freshTimestamp,
                startedAtEpochMillis = freshTimestamp,
            )
            // F13（T1b 假成功）：协调器在入口落盘前就写 SUCCEEDED（output 为空）。
            // 若 persist/attach 失败，这行会永久显示成功却没有产物；启动对账必须纠正。
            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-unconfirmed-success",
                attemptId = "attempt-unconfirmed-success",
                operationStatus = "SUCCEEDED",
                attemptStatus = "SUCCEEDED",
                outputAssetId = null,
                createdAtEpochMillis = 1_000,
                updatedAtEpochMillis = 2_000,
                startedAtEpochMillis = 2_000,
                endedAtEpochMillis = 2_000,
            )

            val report = FigureGenerationReconciler(database).reconcileStaleRunning(
                processStartedAtEpochMillis = processStartedAtEpochMillis,
            )

            assertEquals("两个僵尸 + 一个假成功的操作必须落终态", 3, report.failedOperations)
            assertEquals("两个僵尸 + 一个假成功的尝试必须落终态", 3, report.failedAttempts)
            database.close()

            openReadOnly(context, databaseName).use { db ->
                assertEquals(
                    "除本进程那一行外，不许再有 RUNNING 操作",
                    1,
                    db.count("SELECT COUNT(*) FROM figure_generation_operation WHERE status = 'RUNNING'"),
                )
                assertEquals(
                    "本进程刚发起的操作不能被同一次对账误杀",
                    "op-fresh-this-process",
                    db.string(
                        "SELECT operation_id FROM figure_generation_operation WHERE status = 'RUNNING'",
                    ),
                )
                listOf("op-stale-with-output", "op-stale-without-output").forEach { operationId ->
                    assertEquals(
                        "僵尸操作必须落 FAILED：$operationId",
                        "FAILED",
                        db.string(
                            "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                            operationId,
                        ),
                    )
                }
                db.rawQuery(
                    """
                    SELECT status, diagnostics, ended_at_epoch_millis, output_asset_id
                    FROM figure_generation_attempt
                    WHERE attempt_id = ?
                    """.trimIndent(),
                    arrayOf("attempt-stale-with-output"),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("FAILED", cursor.getString(0))
                    assertTrue(
                        "尝试必须带可读的待核实诊断，实际：${cursor.getString(1)}",
                        cursor.getString(1).orEmpty().contains("RECONCILED_STALE_RUNNING"),
                    )
                    assertFalse("终态尝试必须有结束时刻", cursor.isNull(2))
                    assertEquals(
                        "产物是证据：对账标失败不得抹掉 output_asset_id",
                        outputAsset.sourceAssetId,
                        cursor.getString(3),
                    )
                }
                db.rawQuery(
                    "SELECT diagnostics, ended_at_epoch_millis FROM figure_generation_attempt WHERE attempt_id = ?",
                    arrayOf("attempt-stale-without-output"),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.getString(0).orEmpty().contains("RECONCILED_STALE_RUNNING"))
                    assertFalse(cursor.isNull(1))
                }
                val unconfirmed = db.string(
                    "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                    "attempt-unconfirmed-success",
                )
                assertEquals(
                    "SUCCEEDED 但无产物的尝试必须被纠正为 FAILED",
                    "FAILED",
                    unconfirmed,
                )
                db.rawQuery(
                    "SELECT diagnostics FROM figure_generation_attempt WHERE attempt_id = ?",
                    arrayOf("attempt-unconfirmed-success"),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(
                        "假成功必须带独立于 RUNNING 对账的可读诊断",
                        cursor.getString(0).orEmpty().contains("RECONCILED_UNCONFIRMED_SUCCESS"),
                    )
                }
                assertEquals(
                    "假成功的父操作也必须落 FAILED",
                    "FAILED",
                    db.string(
                        "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                        "op-unconfirmed-success",
                    ),
                )
                assertEquals(
                    "对账不得自动重派：尝试行数保持四条（没有新 attempt）",
                    4,
                    db.count("SELECT COUNT(*) FROM figure_generation_attempt"),
                )
                assertEquals(
                    "对账不得自动重派：操作行数保持四条",
                    4,
                    db.count("SELECT COUNT(*) FROM figure_generation_operation"),
                )
            }

            // 幂等：同一批僵尸行再对账一次，没有行再被改（也就没有第二次"收尾"动作）。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            val second = FigureGenerationReconciler(reopened).reconcileStaleRunning(
                processStartedAtEpochMillis = System.currentTimeMillis(),
            )
            assertEquals(0, second.failedOperations)
            assertEquals(0, second.failedAttempts)
            reopened.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * A-01 本地 JS 成功（`output_asset_id` 恒为 NULL，因为 program 没有资产 id）不得被
     * 第二段对账当成"假成功"：否则每次进程启动都会把一条真实成功改写成 FAILED 待核实。
     */
    @Test
    fun succeededJavascriptAttemptWithoutAssetIsNotReconciledAsFalseSuccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "figure-reconcile-js-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            val processStartedAtEpochMillis = System.currentTimeMillis() - 1_000L
            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-js-success",
                attemptId = "attempt-js-success",
                operationStatus = "SUCCEEDED",
                attemptStatus = "SUCCEEDED",
                outputAssetId = null,
                createdAtEpochMillis = 1_000,
                updatedAtEpochMillis = 2_000,
                startedAtEpochMillis = 2_000,
                endedAtEpochMillis = 2_000,
                attemptMethod = "JS",
                attemptOrdinal = -1,
            )

            val report = FigureGenerationReconciler(database).reconcileStaleRunning(
                processStartedAtEpochMillis = processStartedAtEpochMillis,
            )

            assertEquals("本地 JS 成功不是假成功：不落任何操作", 0, report.failedOperations)
            assertEquals("本地 JS 成功不是假成功：不落任何尝试", 0, report.failedAttempts)
            database.close()

            openReadOnly(context, databaseName).use { db ->
                assertEquals(
                    "JS 尝试必须保持 SUCCEEDED",
                    "SUCCEEDED",
                    db.string(
                        "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                        "attempt-js-success",
                    ),
                )
                assertEquals(
                    "JS 操作必须保持 SUCCEEDED",
                    "SUCCEEDED",
                    db.string(
                        "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                        "op-js-success",
                    ),
                )
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 同一操作里"IMAGE_MODEL 成功但无产物"+"JS 成功"：只纠正 IMAGE_MODEL 那次尝试，
     * 父操作仍算成功（JS 的 program 是真实产物，只是没有资产 id）。
     */
    @Test
    fun javascriptSuccessKeepsItsOperationSucceededWhileImageAttemptIsReconciled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "figure-reconcile-mixed-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            val processStartedAtEpochMillis = System.currentTimeMillis() - 1_000L
            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-mixed-success",
                attemptId = "attempt-mixed-image",
                operationStatus = "SUCCEEDED",
                attemptStatus = "SUCCEEDED",
                outputAssetId = null,
                createdAtEpochMillis = 1_000,
                updatedAtEpochMillis = 2_000,
                startedAtEpochMillis = 2_000,
                endedAtEpochMillis = 2_000,
                attemptMethod = "IMAGE_MODEL",
                attemptOrdinal = 1,
            )
            insertFigureGenerationAttemptFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-mixed-success",
                attemptId = "attempt-mixed-js",
                attemptStatus = "SUCCEEDED",
                outputAssetId = null,
                startedAtEpochMillis = 2_500,
                endedAtEpochMillis = 2_500,
                attemptMethod = "JS",
                attemptOrdinal = -1,
            )

            val report = FigureGenerationReconciler(database).reconcileStaleRunning(
                processStartedAtEpochMillis = processStartedAtEpochMillis,
            )

            assertEquals("IMAGE_MODEL 假成功那次尝试必须被纠正", 1, report.failedAttempts)
            assertEquals("父操作有 JS 真实成功，不得被判失败", 0, report.failedOperations)
            database.close()

            openReadOnly(context, databaseName).use { db ->
                assertEquals(
                    "IMAGE_MODEL 无产物尝试落 FAILED",
                    "FAILED",
                    db.string(
                        "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                        "attempt-mixed-image",
                    ),
                )
                assertEquals(
                    "JS 尝试保持 SUCCEEDED",
                    "SUCCEEDED",
                    db.string(
                        "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                        "attempt-mixed-js",
                    ),
                )
                assertEquals(
                    "操作因 JS 成功保持 SUCCEEDED",
                    "SUCCEEDED",
                    db.string(
                        "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                        "op-mixed-success",
                    ),
                )
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 断言用的第二条只读口。用 `OPEN_READWRITE` 而不是 `OPEN_READONLY`：Room 刚关库时若还有
     * WAL/SHM 残留，只读连接在部分设备上打不开；这里只是读，写入权限不会改变断言含义。
     */
    private fun openReadOnly(context: Context, databaseName: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )

    private fun SQLiteDatabase.count(sql: String): Int =
        rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst()) { "count query returned no row: $sql" }
            cursor.getInt(0)
        }

    private fun SQLiteDatabase.string(sql: String, vararg args: String): String? =
        rawQuery(sql, args).use { cursor ->
            check(cursor.moveToFirst()) { "scalar query returned no row: $sql" }
            if (cursor.isNull(0)) null else cursor.getString(0)
        }
}
