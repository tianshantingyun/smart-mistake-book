package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v68→69（批次 17 / 4E WG-08）：图生成的操作/尝试审计台账 + 三处**追加列**。
 *
 * **它消灭的具体失败**：改前一次配图/净化重绘只活在那段协程里——进程被杀后，"基于哪一版
 * 输入、按哪套策略、试了几次、断在哪一环、产物是哪张图"全部不可考；晚到/重复的尝试也没有
 * 持久身份可以对照。两张新表把操作（冻结输入 + 策略版本）与每次尝试（方法/Provider/产物/
 * 诊断）钉成可查事实；三处追加列让"消息附图是什么角色、题目 revision 的图排第几"与
 * "这条净化作业对应哪次生成操作"从此有字段可落。
 *
 * **预算权威不变**：`model_task_operation.dispatch_count` 与 `clean_redraw_task.attempt_count`
 * 仍是唯一的派发/尝试预算；`figure_generation_operation` 的三个计数是审计镜像，本迁移
 * 不复制、不回填、不与任何预算行建立外键。
 *
 * 纯**非破坏**迁移：
 * - 只 `CREATE TABLE`/`CREATE INDEX` 与 `ALTER TABLE ... ADD COLUMN`；
 * - 既有表一列不删、一行不改、不回填：
 *   `tutor_message_source_asset.role` / `basis_revision_id` 旧行读回 NULL（未知，不猜）；
 *   `clean_redraw_task.operation_id` 旧行读回 NULL（历史作业没有操作台账）；
 *   `problem_revision_source_asset.ordinal` 旧行读回 DEFAULT 0（单一源图 = 第 0 张）。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/69.json` 的 createSql/索引 **逐字一致**
 * （[FigureGenerationSchemaContractTest] 在 JVM 上钉住；差一个字符都会在真机升级时被 Room
 * 的 schema 校验炸掉）。`FullMigrationMatrixInstrumentedTest` 在真库上钉"迁移后新表建出、
 * 旧行原样还在、约束生效、再开一次不漂"。
 *
 * 注：`schemas/69.json` 是**构建产物**（KSP 写出、房间插件写回 `schemas/`），不手工维护——
 * 本轮实施不运行 Gradle，所以它随构建出现。
 */
internal val FIGURE_GENERATION_MIGRATION_68_69 =
    object : Migration(68, 69) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(TUTOR_MESSAGE_SOURCE_ASSET_ROLE_DDL_68_69)
            connection.execSQL(TUTOR_MESSAGE_SOURCE_ASSET_BASIS_REVISION_DDL_68_69)
            connection.execSQL(CLEAN_REDRAW_TASK_OPERATION_DDL_68_69)
            connection.execSQL(PROBLEM_REVISION_SOURCE_ASSET_ORDINAL_DDL_68_69)
            connection.execSQL(FIGURE_GENERATION_OPERATION_DDL_68_69)
            connection.execSQL(FIGURE_GENERATION_OPERATION_INDEX_SOURCE_DDL_68_69)
            connection.execSQL(FIGURE_GENERATION_OPERATION_INDEX_STATUS_DDL_68_69)
            connection.execSQL(FIGURE_GENERATION_ATTEMPT_DDL_68_69)
            connection.execSQL(FIGURE_GENERATION_ATTEMPT_INDEX_ORDINAL_DDL_68_69)
            connection.execSQL(FIGURE_GENERATION_ATTEMPT_INDEX_OUTPUT_ASSET_DDL_68_69)
        }
    }

/** 与 `schemas/69.json` 里 `figure_generation_operation` 的 createSql 逐字一致。 */
internal val FIGURE_GENERATION_OPERATION_DDL_68_69: String =
    "CREATE TABLE IF NOT EXISTS `figure_generation_operation` (" +
        "`operation_id` TEXT NOT NULL, `learner_id` TEXT NOT NULL, " +
        "`source_kind` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
        "`basis_fingerprint` TEXT NOT NULL, `purpose` TEXT NOT NULL, " +
        "`policy_version` TEXT NOT NULL, `status` TEXT NOT NULL, " +
        "`reserved_remote_dispatches` INTEGER NOT NULL, " +
        "`used_remote_dispatches` INTEGER NOT NULL, " +
        "`final_answer_reserved` INTEGER NOT NULL, " +
        "`created_at_epoch_millis` INTEGER NOT NULL, " +
        "`updated_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`operation_id`))"

/** 与 69.json 里同名索引逐字一致（按来源定位"这条消息/修订上有哪些生成操作"）。 */
internal val FIGURE_GENERATION_OPERATION_INDEX_SOURCE_DDL_68_69: String =
    "CREATE INDEX IF NOT EXISTS `index_figure_generation_operation_source_kind_source_id` " +
        "ON `figure_generation_operation` (`source_kind`, `source_id`)"

/** 与 69.json 里同名索引逐字一致（启动对账按状态 + 最后更新时刻扫僵尸）。 */
internal val FIGURE_GENERATION_OPERATION_INDEX_STATUS_DDL_68_69: String =
    "CREATE INDEX IF NOT EXISTS `index_figure_generation_operation_status_updated_at_epoch_millis` " +
        "ON `figure_generation_operation` (`status`, `updated_at_epoch_millis`)"

/** 与 `schemas/69.json` 里 `figure_generation_attempt` 的 createSql 逐字一致。 */
internal val FIGURE_GENERATION_ATTEMPT_DDL_68_69: String =
    "CREATE TABLE IF NOT EXISTS `figure_generation_attempt` (" +
        "`attempt_id` TEXT NOT NULL, `operation_id` TEXT NOT NULL, " +
        "`attempt_ordinal` INTEGER NOT NULL, `method` TEXT NOT NULL, " +
        "`status` TEXT NOT NULL, `provider_snapshot` TEXT, `model_snapshot` TEXT, " +
        "`source_sha256` TEXT, `input_fingerprint` TEXT NOT NULL, " +
        "`output_asset_id` TEXT, `reservation_id` TEXT, `diagnostics` TEXT, " +
        "`started_at_epoch_millis` INTEGER NOT NULL, `ended_at_epoch_millis` INTEGER, " +
        "PRIMARY KEY(`attempt_id`), " +
        "FOREIGN KEY(`operation_id`) REFERENCES `figure_generation_operation`(`operation_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
        "FOREIGN KEY(`output_asset_id`) REFERENCES `canonical_source_asset`(`source_asset_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )"

/**
 * 与 69.json 里同名唯一索引逐字一致：`(operation_id, attempt_ordinal)` 唯一——
 * 终态 attempt 不许复活，同一操作的第 n 次尝试只有一行。
 */
internal val FIGURE_GENERATION_ATTEMPT_INDEX_ORDINAL_DDL_68_69: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS " +
        "`index_figure_generation_attempt_operation_id_attempt_ordinal` " +
        "ON `figure_generation_attempt` (`operation_id`, `attempt_ordinal`)"

/** 与 69.json 里同名索引逐字一致（按产物反查"这张图是哪次尝试产出的"）。 */
internal val FIGURE_GENERATION_ATTEMPT_INDEX_OUTPUT_ASSET_DDL_68_69: String =
    "CREATE INDEX IF NOT EXISTS `index_figure_generation_attempt_output_asset_id` " +
        "ON `figure_generation_attempt` (`output_asset_id`)"

/**
 * 与 69.json 里 `tutor_message_source_asset.role` 的列声明逐字一致。
 * 可空：旧行没有角色（NULL = 未知），迁移不回填、不猜测。
 */
internal val TUTOR_MESSAGE_SOURCE_ASSET_ROLE_DDL_68_69: String =
    "ALTER TABLE `tutor_message_source_asset` ADD COLUMN `role` TEXT"

/**
 * 与 69.json 里 `tutor_message_source_asset.basis_revision_id` 的列声明逐字一致。
 * 可空：旧行没有 basis（NULL = 未知），迁移不回填。
 */
internal val TUTOR_MESSAGE_SOURCE_ASSET_BASIS_REVISION_DDL_68_69: String =
    "ALTER TABLE `tutor_message_source_asset` ADD COLUMN `basis_revision_id` TEXT"

/**
 * 与 69.json 里 `clean_redraw_task.operation_id` 的列声明逐字一致。
 * 可空：历史净化作业没有生成操作台账（NULL = 未知），迁移不回填。
 */
internal val CLEAN_REDRAW_TASK_OPERATION_DDL_68_69: String =
    "ALTER TABLE `clean_redraw_task` ADD COLUMN `operation_id` TEXT"

/**
 * 与 69.json 里 `problem_revision_source_asset.ordinal` 的列声明逐字一致。
 * 非空但带 DEFAULT 0：SQLite 需要给存量行一个确定值（单一源图 = 第 0 张），
 * 这比"可空 + 读侧到处判空"更贴近"没有顺序时就是第一张"的事实。
 */
internal val PROBLEM_REVISION_SOURCE_ASSET_ORDINAL_DDL_68_69: String =
    "ALTER TABLE `problem_revision_source_asset` " +
        "ADD COLUMN `ordinal` INTEGER NOT NULL DEFAULT 0"
