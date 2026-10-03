package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v60→61：**导出记录表**（阶段 4A 批 4 · L7，`docs/research/2026-10-03-stage4a-plan.md` §3 批 4）。
 *
 * 纯**非破坏**新增：建一张只由本功能写的表 `mistake_export_record`（列/语义见
 * `MistakeExportRecordEntity` 的 KDoc）。既有表一列不删、一行不动、不回填——存量库没有
 * 导出记录，迁移不编造行。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/61.json` 的 createSql **逐字一致**
 * （`KernelWave8SchemaContractTest` 在 JVM 上钉住这段字面量）；`FullMigrationMatrixInstrumentedTest`
 * 在真库上钉"旧行原样还在 + 新表已建出且为空"。
 */
internal val KERNEL_WAVE8_MIGRATION_60_61 = object : Migration(60, 61) {
    override suspend fun migrate(connection: SQLiteConnection) {
        MISTAKE_EXPORT_RECORD_DDL_60_61.forEach(connection::execSQL)
    }
}

internal val MISTAKE_EXPORT_RECORD_DDL_60_61: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `mistake_export_record` (" +
        "`export_id` TEXT NOT NULL, " +
        "`kind` TEXT NOT NULL, " +
        "`display_name` TEXT, " +
        "`status` TEXT NOT NULL, " +
        "`input_sha256` TEXT, " +
        "`pdf_sha256` TEXT, " +
        "`page_count` INTEGER, " +
        "`failure_message` TEXT, " +
        "`created_at_epoch_millis` INTEGER NOT NULL, " +
        "`finished_at_epoch_millis` INTEGER, " +
        "PRIMARY KEY(`export_id`)" +
        ")",
)
