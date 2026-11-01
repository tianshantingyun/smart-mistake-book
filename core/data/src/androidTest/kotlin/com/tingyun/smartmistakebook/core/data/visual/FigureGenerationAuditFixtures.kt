package com.tingyun.smartmistakebook.core.data.visual

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort

/**
 * 图生成审计台账的仪器化夹具（androidTest 源集，不是生产接缝）。
 *
 * 跨模块的仪器化测试看不到 `core:database` 的 internal DAO；这里按既有夹具的同一手法
 * （`seedStudyFacts`）用**库文件直写**落行：先让 Room 建好 schema（读一次版本号），再用
 * `android.database.sqlite.SQLiteDatabase` 在 WAL 的第二条连接上写入。Room 的 invalidation
 * 由库内触发器驱动，后续读路径能看到行。
 *
 * 写入顺序是 operation → attempt，且打开 `PRAGMA foreign_keys`：让夹具也受 RESTRICT 外键
 * 约束，避免测出一个生产上根本落不下去的形状。
 *
 * 拆成 operation / attempt 两个入口，是为了让同一 operation 下的**多次尝试**
 * （例如 IMAGE_MODEL 假成功 + 本地 JS 成功）能在同一个用例里落库。
 * `reservationId` / `attemptDiagnostics` 可选：对账的不可伪造判据用例需要落出
 * "method='JS' 但带预留"这种生产写径不可达、只能由夹具构造的形状。
 */
internal suspend fun insertFigureGenerationAuditFixture(
    context: Context,
    database: StudyDatabasePort,
    databaseName: String,
    operationId: String,
    attemptId: String,
    operationStatus: String,
    attemptStatus: String,
    outputAssetId: String?,
    createdAtEpochMillis: Long,
    updatedAtEpochMillis: Long,
    startedAtEpochMillis: Long = updatedAtEpochMillis,
    endedAtEpochMillis: Long? = null,
    attemptMethod: String = "IMAGE_MODEL",
    attemptOrdinal: Int = 0,
    reservationId: String? = null,
    attemptDiagnostics: String? = null,
) {
    insertFigureGenerationOperationFixture(
        context = context,
        database = database,
        databaseName = databaseName,
        operationId = operationId,
        operationStatus = operationStatus,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
    insertFigureGenerationAttemptFixture(
        context = context,
        database = database,
        databaseName = databaseName,
        operationId = operationId,
        attemptId = attemptId,
        attemptStatus = attemptStatus,
        outputAssetId = outputAssetId,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        attemptMethod = attemptMethod,
        attemptOrdinal = attemptOrdinal,
        reservationId = reservationId,
        attemptDiagnostics = attemptDiagnostics,
    )
}

/** 只落 operation 行（同 operation 已有行时不覆盖）。 */
internal suspend fun insertFigureGenerationOperationFixture(
    context: Context,
    database: StudyDatabasePort,
    databaseName: String,
    operationId: String,
    operationStatus: String,
    createdAtEpochMillis: Long,
    updatedAtEpochMillis: Long,
) {
    // Room 惰性建库：先读一次版本号，确保 schema（两张图生成审计表）已经在文件里。
    database.readDatabaseVersion()
    withSqlite(context, databaseName) { sqlite ->
        sqlite.insertWithOnConflict(
            "figure_generation_operation",
            null,
            ContentValues().apply {
                put("operation_id", operationId)
                put("learner_id", "learner-fixture")
                put("source_kind", "TUTOR_MESSAGE")
                put("source_id", "message-$operationId")
                put("basis_fingerprint", "a".repeat(64))
                put("purpose", "EXPLANATION")
                put("policy_version", "figure-generation-policy-v1")
                put("status", operationStatus)
                put("reserved_remote_dispatches", 0)
                put("used_remote_dispatches", 0)
                put("final_answer_reserved", 0)
                put("created_at_epoch_millis", createdAtEpochMillis)
                put("updated_at_epoch_millis", updatedAtEpochMillis)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }
}

/** 只落一条 attempt 行（父 operation 必须已存在）。 */
internal suspend fun insertFigureGenerationAttemptFixture(
    context: Context,
    database: StudyDatabasePort,
    databaseName: String,
    operationId: String,
    attemptId: String,
    attemptStatus: String,
    outputAssetId: String?,
    startedAtEpochMillis: Long,
    endedAtEpochMillis: Long? = null,
    attemptMethod: String = "IMAGE_MODEL",
    attemptOrdinal: Int = 0,
    reservationId: String? = null,
    attemptDiagnostics: String? = null,
) {
    database.readDatabaseVersion()
    withSqlite(context, databaseName) { sqlite ->
        sqlite.insertOrThrow(
            "figure_generation_attempt",
            null,
            ContentValues().apply {
                put("attempt_id", attemptId)
                put("operation_id", operationId)
                put("attempt_ordinal", attemptOrdinal)
                put("method", attemptMethod)
                put("status", attemptStatus)
                putNull("provider_snapshot")
                putNull("model_snapshot")
                putNull("source_sha256")
                put("input_fingerprint", "b".repeat(64))
                if (outputAssetId == null) putNull("output_asset_id") else {
                    put("output_asset_id", outputAssetId)
                }
                if (reservationId == null) putNull("reservation_id") else {
                    put("reservation_id", reservationId)
                }
                if (attemptDiagnostics == null) putNull("diagnostics") else {
                    put("diagnostics", attemptDiagnostics)
                }
                put("started_at_epoch_millis", startedAtEpochMillis)
                if (endedAtEpochMillis == null) putNull("ended_at_epoch_millis") else {
                    put("ended_at_epoch_millis", endedAtEpochMillis)
                }
            },
        )
    }
}

private inline fun withSqlite(
    context: Context,
    databaseName: String,
    block: (SQLiteDatabase) -> Unit,
) {
    val sqlite = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    )
    try {
        sqlite.execSQL("PRAGMA foreign_keys = ON")
        sqlite.beginTransaction()
        try {
            block(sqlite)
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    } finally {
        sqlite.close()
    }
}
