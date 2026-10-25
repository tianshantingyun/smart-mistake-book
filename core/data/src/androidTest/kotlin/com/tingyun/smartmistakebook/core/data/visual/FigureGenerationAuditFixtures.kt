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
) {
    // Room 惰性建库：先读一次版本号，确保 schema（两张图生成审计表）已经在文件里。
    database.readDatabaseVersion()
    val sqlite = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    )
    try {
        sqlite.execSQL("PRAGMA foreign_keys = ON")
        sqlite.beginTransaction()
        try {
            sqlite.insertOrThrow(
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
            )
            sqlite.insertOrThrow(
                "figure_generation_attempt",
                null,
                ContentValues().apply {
                    put("attempt_id", attemptId)
                    put("operation_id", operationId)
                    put("attempt_ordinal", 0)
                    put("method", "IMAGE_MODEL")
                    put("status", attemptStatus)
                    putNull("provider_snapshot")
                    putNull("model_snapshot")
                    putNull("source_sha256")
                    put("input_fingerprint", "b".repeat(64))
                    if (outputAssetId == null) putNull("output_asset_id") else {
                        put("output_asset_id", outputAssetId)
                    }
                    putNull("reservation_id")
                    putNull("diagnostics")
                    put("started_at_epoch_millis", startedAtEpochMillis)
                    if (endedAtEpochMillis == null) putNull("ended_at_epoch_millis") else {
                        put("ended_at_epoch_millis", endedAtEpochMillis)
                    }
                },
            )
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    } finally {
        sqlite.close()
    }
}
