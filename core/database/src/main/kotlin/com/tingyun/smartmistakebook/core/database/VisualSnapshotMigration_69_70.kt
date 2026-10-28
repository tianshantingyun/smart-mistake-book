package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v69→70（批次 30 / D-01）：可重放视觉程序/快照/消息引用/题面引用**四表**。
 *
 * **它消灭的具体失败**：改前一张图只活在消息正文与一张 PNG 里——重启/换页/导出之后只剩像素，
 * 图从哪段源码来、按哪版题面算的、能不能重放全部不可考；同一程序还可能被原地改写源码，
 * 让已显示的图静默变成另一张。四张表把"程序（内容派生身份，永不 UPDATE）/ 快照
 * （STAGING→LIVE 单向）/ 消息引用 / 保存题固定引用"钉成可查、可重放的事实。
 *
 * 纯**非破坏**迁移：
 * - 只 `CREATE TABLE` / `CREATE INDEX`，零 `ALTER`、零 `UPDATE`、零回填；
 * - 既有表一列不删、一行不改：升级后四张新表为空——"历史图没有程序/快照可考"如实呈现，
 *   不编造历史快照（与 v68→69"无台账如实呈现"同一纪律，[FigureGenerationMigration_68_69]）；
 * - 外键列全部建了索引（Room 在 `onOpen` 打开 `PRAGMA foreign_keys = ON`，缺索引会让父表
 *   删除退化成全表扫，`FullMigrationMatrixInstrumentedTest` 的既有注释已记载这条行为）。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/70.json` 的 createSql/索引 **逐字一致**
 * （[TutorVisualSchemaContractTest] 在 JVM 上钉住；差一个字符都会在真机升级时被 Room 的
 * schema 校验炸掉）。
 *
 * **`schemas/70.json` 是构建产物，本轮没有生成**（本轨按纪律不运行 Gradle）：它由 KSP 在
 * `core:database` 编译时写出，并与 `database.version` / `identityHash` 一起决定新装库的
 * Room 身份。因此本轮**不手写、不伪造** `70.json`（伪造 `identityHash` 会让新装库写进错误的
 * `room_master_table` 身份；构造测试也不许用占位哈希冒充"已导出"）。占位说明见
 * `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json.NOT_EXPORTED.md`。
 * 首次带 KSP 的构建会生成它；在那之前 [TutorVisualSchemaContractTest] 与
 * `KernelWave0SchemaContractTest` 的"版本 == 最高导出"断言必然红——这是"提了版本但没导出"
 * 的诚实信号，不是缺陷。
 */
internal val VISUAL_SNAPSHOT_MIGRATION_69_70 =
    object : Migration(69, 70) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(TUTOR_VISUAL_PROGRAM_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_PROGRAM_INDEX_LEARNER_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_PROGRAM_INDEX_BASIS_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_PROGRAM_INDEX_SOURCE_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_SNAPSHOT_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_CONTENT_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_LATEST_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_PREVIEW_DDL_69_70)
            connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_STATUS_DDL_69_70)
            connection.execSQL(TUTOR_MESSAGE_VISUAL_DDL_69_70)
            connection.execSQL(TUTOR_MESSAGE_VISUAL_INDEX_PROGRAM_DDL_69_70)
            connection.execSQL(TUTOR_MESSAGE_VISUAL_INDEX_SNAPSHOT_DDL_69_70)
            connection.execSQL(PROBLEM_REVISION_VISUAL_DDL_69_70)
            connection.execSQL(PROBLEM_REVISION_VISUAL_INDEX_PROGRAM_DDL_69_70)
            connection.execSQL(PROBLEM_REVISION_VISUAL_INDEX_SNAPSHOT_DDL_69_70)
        }
    }

/** 与 `schemas/70.json` 里 `tutor_visual_program` 的 createSql 逐字一致。 */
internal val TUTOR_VISUAL_PROGRAM_DDL_69_70: String =
    "CREATE TABLE IF NOT EXISTS `tutor_visual_program` (" +
        "`program_id` TEXT NOT NULL, `learner_id` TEXT NOT NULL, `role` TEXT NOT NULL, " +
        "`source_sha256` TEXT NOT NULL, `source_js` TEXT NOT NULL, " +
        "`initial_state_json` TEXT NOT NULL, `initial_state_sha256` TEXT NOT NULL, " +
        "`api_version` INTEGER NOT NULL, `sdk_version` TEXT NOT NULL, " +
        "`basis_revision_id` TEXT, `basis_fingerprint` TEXT NOT NULL, " +
        "`request_snapshot` TEXT, `created_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`program_id`))"

/** 与 70.json 里同名索引逐字一致（"这个学员最近有哪些程序"）。 */
internal val TUTOR_VISUAL_PROGRAM_INDEX_LEARNER_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_visual_program_learner_id_created_at_epoch_millis` " +
        "ON `tutor_visual_program` (`learner_id`, `created_at_epoch_millis`)"

/** 与 70.json 里同名索引逐字一致（按题面反查；软引用 `no-new-dangling` 校验用）。 */
internal val TUTOR_VISUAL_PROGRAM_INDEX_BASIS_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_visual_program_basis_revision_id` " +
        "ON `tutor_visual_program` (`basis_revision_id`)"

/** 与 70.json 里同名索引逐字一致（同一份源码被复用几次：复用审计/熔断指纹同源）。 */
internal val TUTOR_VISUAL_PROGRAM_INDEX_SOURCE_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_visual_program_source_sha256` " +
        "ON `tutor_visual_program` (`source_sha256`)"

/**
 * 与 `schemas/70.json` 里 `tutor_visual_snapshot` 的 createSql 逐字一致。
 *
 * 两个 RESTRICT 外键：删程序/删预览资产都不许带走仍在被引用（或可能被引用）的快照。
 */
internal val TUTOR_VISUAL_SNAPSHOT_DDL_69_70: String =
    "CREATE TABLE IF NOT EXISTS `tutor_visual_snapshot` (" +
        "`snapshot_id` TEXT NOT NULL, `program_id` TEXT NOT NULL, `state_json` TEXT NOT NULL, " +
        "`state_sha256` TEXT NOT NULL, `scene_schema_version` INTEGER NOT NULL, " +
        "`scene_json` TEXT NOT NULL, `scene_sha256` TEXT NOT NULL, " +
        "`render_schema_version` INTEGER NOT NULL, `draw_commands_json` TEXT NOT NULL, " +
        "`draw_commands_sha256` TEXT NOT NULL, `content_ids_json` TEXT NOT NULL, " +
        "`viewport_width_du` INTEGER NOT NULL, `viewport_height_du` INTEGER NOT NULL, " +
        "`font_scale_milli` INTEGER NOT NULL, `theme` TEXT NOT NULL, `time_ms` INTEGER NOT NULL, " +
        "`seed` INTEGER NOT NULL, `runtime_version` TEXT NOT NULL, " +
        "`compiler_version` TEXT NOT NULL, `renderer_version` TEXT NOT NULL, " +
        "`font_manifest_version` TEXT NOT NULL, `question_verification_status` TEXT NOT NULL, " +
        "`preview_asset_id` TEXT, `status` TEXT NOT NULL, `owner_request_id` TEXT, " +
        "`checkpoint_kind` TEXT, `created_at_epoch_millis` INTEGER NOT NULL, " +
        "`promoted_at_epoch_millis` INTEGER, " +
        "PRIMARY KEY(`snapshot_id`), " +
        "FOREIGN KEY(`program_id`) REFERENCES `tutor_visual_program`(`program_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
        "FOREIGN KEY(`preview_asset_id`) REFERENCES `canonical_source_asset`(`source_asset_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )"

/**
 * 与 70.json 里同名唯一索引逐字一致：同 program 同内容快照只存一份（重复 checkpoint 幂等）。
 */
internal val TUTOR_VISUAL_SNAPSHOT_INDEX_CONTENT_DDL_69_70: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS " +
        "`index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256` " +
        "ON `tutor_visual_snapshot` (`program_id`, `state_sha256`, `draw_commands_sha256`)"

/** 与 70.json 里同名索引逐字一致（"每 program 最新快照"：LATEST 迁移与恢复取最新）。 */
internal val TUTOR_VISUAL_SNAPSHOT_INDEX_LATEST_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS " +
        "`index_tutor_visual_snapshot_program_id_promoted_at_epoch_millis` " +
        "ON `tutor_visual_snapshot` (`program_id`, `promoted_at_epoch_millis`)"

/** 与 70.json 里同名索引逐字一致（资产引用第六站点：孤儿清理/备份按资产反查）。 */
internal val TUTOR_VISUAL_SNAPSHOT_INDEX_PREVIEW_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_visual_snapshot_preview_asset_id` " +
        "ON `tutor_visual_snapshot` (`preview_asset_id`)"

/** 与 70.json 里同名索引逐字一致（启动对账扫 STAGING 僵尸）。 */
internal val TUTOR_VISUAL_SNAPSHOT_INDEX_STATUS_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_visual_snapshot_status_created_at_epoch_millis` " +
        "ON `tutor_visual_snapshot` (`status`, `created_at_epoch_millis`)"

/**
 * 与 `schemas/70.json` 里 `tutor_message_visual` 的 createSql 逐字一致。
 *
 * `message_id` 是唯一的 CASCADE：引用行随宿主消息走；program/snapshot 一律 RESTRICT。
 */
internal val TUTOR_MESSAGE_VISUAL_DDL_69_70: String =
    "CREATE TABLE IF NOT EXISTS `tutor_message_visual` (" +
        "`message_id` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `program_id` TEXT NOT NULL, " +
        "`current_snapshot_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, " +
        "`ui_state_json` TEXT, `form_digest` TEXT, " +
        "`created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`message_id`, `ordinal`), " +
        "FOREIGN KEY(`message_id`) REFERENCES `tutor_message`(`message_id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`program_id`) REFERENCES `tutor_visual_program`(`program_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
        "FOREIGN KEY(`current_snapshot_id`) REFERENCES `tutor_visual_snapshot`(`snapshot_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )"

/** 与 70.json 里同名索引逐字一致（按程序反查引用了它的消息）。 */
internal val TUTOR_MESSAGE_VISUAL_INDEX_PROGRAM_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_message_visual_program_id` " +
        "ON `tutor_message_visual` (`program_id`)"

/** 与 70.json 里同名索引逐字一致（反向：这张快照被哪些消息引用）。 */
internal val TUTOR_MESSAGE_VISUAL_INDEX_SNAPSHOT_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_tutor_message_visual_current_snapshot_id` " +
        "ON `tutor_message_visual` (`current_snapshot_id`)"

/**
 * 与 `schemas/70.json` 里 `problem_revision_visual` 的 createSql 逐字一致。
 *
 * `ordinal` 非空 + DEFAULT 0：SQLite 需要给存量行一个确定值（单图 = 第 0 张）——与
 * `problem_revision_source_asset.ordinal`（v68→69）同一形态。
 */
internal val PROBLEM_REVISION_VISUAL_DDL_69_70: String =
    "CREATE TABLE IF NOT EXISTS `problem_revision_visual` (" +
        "`problem_revision_id` TEXT NOT NULL, `role` TEXT NOT NULL, " +
        "`ordinal` INTEGER NOT NULL DEFAULT 0, `program_id` TEXT NOT NULL, " +
        "`snapshot_id` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`problem_revision_id`, `role`, `ordinal`), " +
        "FOREIGN KEY(`problem_revision_id`) REFERENCES `problem_revision`(`revision_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
        "FOREIGN KEY(`program_id`) REFERENCES `tutor_visual_program`(`program_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
        "FOREIGN KEY(`snapshot_id`) REFERENCES `tutor_visual_snapshot`(`snapshot_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )"

/** 与 70.json 里同名索引逐字一致（按程序反查它被哪些保存题固定）。 */
internal val PROBLEM_REVISION_VISUAL_INDEX_PROGRAM_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_problem_revision_visual_program_id` " +
        "ON `problem_revision_visual` (`program_id`)"

/** 与 70.json 里同名索引逐字一致（按快照反查它被哪些保存题固定）。 */
internal val PROBLEM_REVISION_VISUAL_INDEX_SNAPSHOT_DDL_69_70: String =
    "CREATE INDEX IF NOT EXISTS `index_problem_revision_visual_snapshot_id` " +
        "ON `problem_revision_visual` (`snapshot_id`)"
