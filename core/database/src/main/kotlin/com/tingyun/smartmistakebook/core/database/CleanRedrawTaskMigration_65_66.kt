package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v65→66：新增 `clean_redraw_task` —— 一次「分类 → 净化（重绘）→ 挂接」作业的持久身份与状态
 * （AUD-09 / 4D）。
 *
 * 消灭的具体失败：改前净化重绘是提交路径里一段 fire-and-forget——进程被杀这次作业就没了
 * （题面永远停在原图，没有任何地方记得"还欠一次"）；失败被吞成 false/null；续跑若靠重放已提交
 * 操作会静默再付一次费。这张表让"还欠一次 / 上次断在哪一环 / 下一步还该不该跑"成为可查的事实。
 *
 * 纯**非破坏**新增：只建一张新表与两条索引，既有表一列不删、一行不动、不回填。旧库升级后
 * 该表为空——历史重绘没有台账可考（"无台账"如实呈现，不编造历史作业）。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/66.json` 的 createSql/索引 **逐字一致**
 * （[CleanRedrawTaskSchemaContractTest] 在 JVM 上钉住；差一个字符都会在真机升级时被 Room 的
 * schema 校验炸掉）。`FullMigrationMatrixInstrumentedTest` 在真库上钉"迁移后新表建出、旧行原样
 * 还在、再开一次不漂"。
 *
 * 注：`schemas/66.json` 是**构建产物**（KSP 写出、`RoomSchemaCopyTask` 拷回 `schemas/`），
 * 不手工维护——本轮实施不运行 Gradle，所以它随构建出现。
 */
internal val CLEAN_REDRAW_TASK_MIGRATION_65_66 =
    object : Migration(65, 66) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(CLEAN_REDRAW_TASK_DDL_65_66)
            connection.execSQL(CLEAN_REDRAW_TASK_INDEX_FINGERPRINT_65_66)
            connection.execSQL(CLEAN_REDRAW_TASK_INDEX_STATUS_65_66)
        }
    }

/** 与 `schemas/66.json` 里 `clean_redraw_task` 的 createSql 逐字一致。 */
internal val CLEAN_REDRAW_TASK_DDL_65_66: String =
    "CREATE TABLE IF NOT EXISTS `clean_redraw_task` (" +
        "`task_id` TEXT NOT NULL, `revision_id` TEXT NOT NULL, " +
        "`source_asset_id` TEXT NOT NULL, `source_image_sha256` TEXT NOT NULL, " +
        "`request_fingerprint` TEXT NOT NULL, `subject_id` TEXT NOT NULL, " +
        "`status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, " +
        "`last_failure_code` TEXT, `created_at_epoch_millis` INTEGER NOT NULL, " +
        "`updated_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`task_id`))"

/** 与 66.json 里同名索引逐字一致（按分类请求指纹去重读："同指纹已成功 → 不再付费"）。 */
internal val CLEAN_REDRAW_TASK_INDEX_FINGERPRINT_65_66: String =
    "CREATE INDEX IF NOT EXISTS `index_clean_redraw_task_request_fingerprint` " +
        "ON `clean_redraw_task` (`request_fingerprint`)"

/** 与 66.json 里同名索引逐字一致（启动对账按状态扫描待做/失败行）。 */
internal val CLEAN_REDRAW_TASK_INDEX_STATUS_65_66: String =
    "CREATE INDEX IF NOT EXISTS `index_clean_redraw_task_status` " +
        "ON `clean_redraw_task` (`status`)"
