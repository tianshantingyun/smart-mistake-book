package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v61→62：**绑定抽样表**（阶段 3C 批 1 · KF-29，`docs/research/2026-09-28-algorithm-fix-plan.md:372-385`）。
 *
 * 纯**非破坏**新增：建一张只由本功能写的表 `binding_audit_sample`（列/语义见
 * `BindingAuditSampleEntity` 的 KDoc）。既有表一列不删、一行不动、不回填——存量库没有抽样
 * 记录，迁移不编造行。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/62.json` 的 createSql **逐字一致**
 * （`KernelWave9SchemaContractTest` 在 JVM 上钉住这段字面量）；`FullMigrationMatrixInstrumentedTest`
 * 在真库上钉"旧行原样还在 + 新表已建出且为空"。
 */
internal val KERNEL_WAVE9_MIGRATION_61_62 = object : Migration(61, 62) {
    override suspend fun migrate(connection: SQLiteConnection) {
        BINDING_AUDIT_SAMPLE_DDL_61_62.forEach(connection::execSQL)
    }
}

internal val BINDING_AUDIT_SAMPLE_DDL_61_62: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `binding_audit_sample` (" +
        "`sample_id` TEXT NOT NULL, " +
        "`practice_unit_id` TEXT NOT NULL, " +
        "`binding_snapshot_json` TEXT NOT NULL, " +
        "`status` TEXT NOT NULL, " +
        "`verdict` TEXT, " +
        "`reviewed_at` INTEGER, " +
        "PRIMARY KEY(`sample_id`)" +
        ")",
)
