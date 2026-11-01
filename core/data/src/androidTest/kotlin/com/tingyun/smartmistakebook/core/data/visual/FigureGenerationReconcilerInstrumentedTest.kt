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
     * 不可伪造判据（批次 35 修复环收紧）："本地已确认成功"必须是
     * `method='JS' AND reservation_id IS NULL`——这正是 `insertLocalAttempt` 写出的唯一形状
     * （`FigureGenerationDao.insertLocalAttempt` 恒 `reservationId = null`）。一条
     * **method='JS' 但带账本预留** 的行在生产写径上不可达（派发预留处恒 IMAGE_MODEL、
     * 本地路径恒无预留），但判据不得依赖那两道上游护栏自证：这种行一旦出现（只可能出自
     * 上游回归）会被 `method = 'JS'` 当"本地确认成功"，且被候选排除、永久逃逸改判。
     * 改前本用例红：`method <> 'JS'` 把它整体排除，行永远停在 SUCCEEDED。
     */
    @Test
    fun reservedJavascriptNamedSuccessIsStillReconciledAsFalseSuccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "figure-reconcile-js-reserved-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            val processStartedAtEpochMillis = System.currentTimeMillis() - 1_000L
            insertFigureGenerationAuditFixture(
                context = context,
                database = database,
                databaseName = databaseName,
                operationId = "op-js-reserved",
                attemptId = "attempt-js-reserved",
                operationStatus = "SUCCEEDED",
                attemptStatus = "SUCCEEDED",
                outputAssetId = null,
                createdAtEpochMillis = 1_000,
                updatedAtEpochMillis = 2_000,
                startedAtEpochMillis = 2_000,
                endedAtEpochMillis = 2_000,
                attemptMethod = "JS",
                attemptOrdinal = 1,
                reservationId = "reservation-js-x",
            )

            val report = FigureGenerationReconciler(database).reconcileStaleRunning(
                processStartedAtEpochMillis = processStartedAtEpochMillis,
            )

            assertEquals(
                "JS 名义预留行的假成功必须被改判（不得因 method='JS' 逃逸）",
                1,
                report.failedAttempts,
            )
            assertEquals(
                "操作名下没有任何可核验成功，必须一并落 FAILED",
                1,
                report.failedOperations,
            )
            database.close()

            openReadOnly(context, databaseName).use { db ->
                assertEquals(
                    "JS 名义预留行必须落 FAILED 待核实",
                    "FAILED",
                    db.string(
                        "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                        "attempt-js-reserved",
                    ),
                )
                db.rawQuery(
                    "SELECT diagnostics FROM figure_generation_attempt WHERE attempt_id = ?",
                    arrayOf("attempt-js-reserved"),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(
                        "改判诊断必须是假成功前缀而不是僵尸 RUNNING 前缀",
                        cursor.getString(0).orEmpty().contains("RECONCILED_UNCONFIRMED_SUCCESS"),
                    )
                }
                assertEquals(
                    "父操作必须落 FAILED",
                    "FAILED",
                    db.string(
                        "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                        "op-js-reserved",
                    ),
                )
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 同一操作里"JS 名义预留行的假成功"与"真 JS 成功"（`reservation_id IS NULL`）并存：
     * 只纠正带预留的那条；真 JS 成功（program 是真实产物）保住操作不落 FAILED。
     * 操作级确认谓词必须用同一条不可伪造判据，而不是裸 `method = 'JS'`。
     */
    @Test
    fun genuineJavascriptSuccessKeepsOperationSucceededWhileReservedJavascriptRowIsCorrected() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "figure-reconcile-js-mixed-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            try {
                val database = StudyDatabaseFactory.open(context, databaseName)
                val processStartedAtEpochMillis = System.currentTimeMillis() - 1_000L
                insertFigureGenerationAuditFixture(
                    context = context,
                    database = database,
                    databaseName = databaseName,
                    operationId = "op-js-mixed-authenticity",
                    attemptId = "attempt-js-mixed-reserved",
                    operationStatus = "SUCCEEDED",
                    attemptStatus = "SUCCEEDED",
                    outputAssetId = null,
                    createdAtEpochMillis = 1_000,
                    updatedAtEpochMillis = 2_000,
                    startedAtEpochMillis = 2_000,
                    endedAtEpochMillis = 2_000,
                    attemptMethod = "JS",
                    attemptOrdinal = 1,
                    reservationId = "reservation-js-y",
                )
                insertFigureGenerationAttemptFixture(
                    context = context,
                    database = database,
                    databaseName = databaseName,
                    operationId = "op-js-mixed-authenticity",
                    attemptId = "attempt-js-mixed-genuine",
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

                assertEquals(
                    "只有带预留的 JS 名义行被改判",
                    1,
                    report.failedAttempts,
                )
                assertEquals(
                    "真 JS 成功（无预留）保住操作不落 FAILED",
                    0,
                    report.failedOperations,
                )
                database.close()

                openReadOnly(context, databaseName).use { db ->
                    assertEquals(
                        "带预留的 JS 名义行落 FAILED",
                        "FAILED",
                        db.string(
                            "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                            "attempt-js-mixed-reserved",
                        ),
                    )
                    assertEquals(
                        "真 JS 成功保持 SUCCEEDED",
                        "SUCCEEDED",
                        db.string(
                            "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                            "attempt-js-mixed-genuine",
                        ),
                    )
                    assertEquals(
                        "操作因真 JS 成功保持 SUCCEEDED",
                        "SUCCEEDED",
                        db.string(
                            "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                            "op-js-mixed-authenticity",
                        ),
                    )
                }
            } finally {
                context.deleteDatabase(databaseName)
            }
        }

    /**
     * JS 的失败/取消路径在对账里是**终态**：FAILED / CANCELLED（含父操作）不被任何段改写，
     * 诊断与结束时刻原样保留；而卡在 RUNNING 的 JS 行仍走僵尸段（与方法无关）落 FAILED。
     * 判据收紧只改"假成功"候选与操作级确认谓词，不得波及这些路径。
     */
    @Test
    fun terminalJavascriptAttemptsSurviveReconcileUnchangedWhileStaleRunningIsStillFailed() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "figure-reconcile-js-terminal-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            try {
                val database = StudyDatabaseFactory.open(context, databaseName)
                val processStartedAtEpochMillis = System.currentTimeMillis() - 1_000L
                // 生产 failAttempt 的形状：attempt 与 operation 同落 FAILED，诊断有界。
                insertFigureGenerationAuditFixture(
                    context = context,
                    database = database,
                    databaseName = databaseName,
                    operationId = "op-js-failed",
                    attemptId = "attempt-js-failed",
                    operationStatus = "FAILED",
                    attemptStatus = "FAILED",
                    outputAssetId = null,
                    createdAtEpochMillis = 1_000,
                    updatedAtEpochMillis = 2_000,
                    startedAtEpochMillis = 2_000,
                    endedAtEpochMillis = 2_000,
                    attemptMethod = "JS",
                    attemptOrdinal = -1,
                    attemptDiagnostics = "JS 执行失败（有界诊断）",
                )
                // 生产 cancel() 的形状：attempt 与 operation 同落 CANCELLED，诊断"已取消"。
                insertFigureGenerationAuditFixture(
                    context = context,
                    database = database,
                    databaseName = databaseName,
                    operationId = "op-js-cancelled",
                    attemptId = "attempt-js-cancelled",
                    operationStatus = "CANCELLED",
                    attemptStatus = "CANCELLED",
                    outputAssetId = null,
                    createdAtEpochMillis = 1_000,
                    updatedAtEpochMillis = 2_000,
                    startedAtEpochMillis = 2_000,
                    endedAtEpochMillis = 2_000,
                    attemptMethod = "JS",
                    attemptOrdinal = -1,
                    attemptDiagnostics = "已取消",
                )
                // 上一进程留下的 JS RUNNING：僵尸段与方法无关，仍必须收尾。
                insertFigureGenerationAuditFixture(
                    context = context,
                    database = database,
                    databaseName = databaseName,
                    operationId = "op-js-stale-running",
                    attemptId = "attempt-js-stale-running",
                    operationStatus = "RUNNING",
                    attemptStatus = "RUNNING",
                    outputAssetId = null,
                    createdAtEpochMillis = 1_000,
                    updatedAtEpochMillis = 2_000,
                    startedAtEpochMillis = 2_000,
                    attemptMethod = "JS",
                    attemptOrdinal = -1,
                )

                val report = FigureGenerationReconciler(database).reconcileStaleRunning(
                    processStartedAtEpochMillis = processStartedAtEpochMillis,
                )

                assertEquals(
                    "只有僵尸 RUNNING 那一对被收尾",
                    1,
                    report.failedOperations,
                )
                assertEquals(1, report.failedAttempts)
                database.close()

                openReadOnly(context, databaseName).use { db ->
                    assertEquals(
                        "JS FAILED 尝试是对账终态，不得被改写",
                        "FAILED",
                        db.string(
                            "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                            "attempt-js-failed",
                        ),
                    )
                    assertEquals(
                        "JS CANCELLED 尝试是对账终态，不得被改写",
                        "CANCELLED",
                        db.string(
                            "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                            "attempt-js-cancelled",
                        ),
                    )
                    db.rawQuery(
                        "SELECT diagnostics, ended_at_epoch_millis FROM figure_generation_attempt" +
                            " WHERE attempt_id = ?",
                        arrayOf("attempt-js-cancelled"),
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(
                            "取消原因必须原样保留",
                            "已取消",
                            cursor.getString(0),
                        )
                        assertFalse("终态结束时刻不得被对账刷新", cursor.isNull(1))
                    }
                    assertEquals(
                        "JS FAILED 操作保持 FAILED",
                        "FAILED",
                        db.string(
                            "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                            "op-js-failed",
                        ),
                    )
                    assertEquals(
                        "JS CANCELLED 操作保持 CANCELLED",
                        "CANCELLED",
                        db.string(
                            "SELECT status FROM figure_generation_operation WHERE operation_id = ?",
                            "op-js-cancelled",
                        ),
                    )
                    assertEquals(
                        "僵尸 RUNNING 的 JS 行仍被僵尸段收尾（与方法无关）",
                        "FAILED",
                        db.string(
                            "SELECT status FROM figure_generation_attempt WHERE attempt_id = ?",
                            "attempt-js-stale-running",
                        ),
                    )
                    db.rawQuery(
                        "SELECT diagnostics FROM figure_generation_attempt WHERE attempt_id = ?",
                        arrayOf("attempt-js-stale-running"),
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue(
                            "僵尸收尾诊断必须是 RUNNING 前缀",
                            cursor.getString(0).orEmpty().contains("RECONCILED_STALE_RUNNING"),
                        )
                    }
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
