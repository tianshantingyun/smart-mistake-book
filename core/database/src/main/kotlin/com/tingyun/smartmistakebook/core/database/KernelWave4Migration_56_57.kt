package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v56→57：**阶段 3B 步骤一 · D-M M5 死面删除**（`docs/research/2026-10-02-stage3b-plan.md` §3 步骤一）。
 *
 * 两处都是**只写不读/写后无读者的死面**，删除不改任何投影输出（零算法 bump）：
 * - `projection_consumption`：投影消费回执表，全仓**零 SELECT**（唯一读者不存在；幂等由
 *   `projection_outbox` 状态与快照 CAS 保证）。整表删除。
 * - `learner_problem_memory_state` 的 `last_reviewed_epoch_day` / `last_attempt_id`：
 *   写后无读者——跨日 delta_t 由 `lastReviewedAtEpochMillis` + 事件 UTC 偏移现算
 *   （`LearningProjector.projectMemory`、`ReviewLogSink`），"上次作答 id"没有任何消费方。
 *   两列删除，**表本身保留**（活体：`library_catalog` 视图、`ProblemDao`、
 *   `readCurrentSnapshot` 都在读）。
 *
 * **非破坏**：删列只能重建表（minSdk 23 的 SQLite 没有 `DROP COLUMN`），顺序是仓库既有范式：
 * 建新表 → INSERT SELECT 逐列搬运 → DROP 旧表 → RENAME → 重建索引。旧行其余列原样保留；
 * 被删两列本来就不带信息，不编造也不回填。迁移期间外键是关的（Room 的生成代码把
 * `PRAGMA foreign_keys = ON` 放在 `onOpen`，迁移之后），所以 DROP 不会触发级联；
 * 本表没有任何子表外键指向它，两条被参照的外键都在重建后的新表里逐字保留。
 *
 * 历史迁移 SQL 一律不动：v42 加的 `last_reviewed_epoch_day` 仍在 `CalendarDayMigration` 里，
 * 57 只是把它拿掉——老库要能一路升上来，就得让每一段历史保持当时的样子。
 */
internal val KERNEL_WAVE4_MIGRATION_56_57 = object : Migration(56, 57) {
    override suspend fun migrate(connection: SQLiteConnection) {
        DROP_PROJECTION_CONSUMPTION_DDL_56_57.forEach(connection::execSQL)
        DROP_DEAD_MEMORY_COLUMNS_DDL_56_57.forEach(connection::execSQL)
    }
}

/**
 * M5 ①：`projection_consumption` 整表删除。索引随表一起消失（SQLite 语义），无需单列。
 */
internal val DROP_PROJECTION_CONSUMPTION_DDL_56_57: List<String> = listOf(
    "DROP TABLE `projection_consumption`",
)

/**
 * M5 ②：`learner_problem_memory_state` 去掉 `last_reviewed_epoch_day` / `last_attempt_id`。
 *
 * 重建后的列序与 57.json 的实体顺序逐位一致（Room 的 createSql 按属性声明序生成）；
 * 列名/类型/NOT NULL/DEFAULT/主键/两条外键/两条索引全部逐字照搬 57.json。
 */
internal val DROP_DEAD_MEMORY_COLUMNS_DDL_56_57: List<String> = listOf(
    """
    CREATE TABLE IF NOT EXISTS `learner_problem_memory_state_v57` (
        `projection_name` TEXT NOT NULL,
        `learner_id` TEXT NOT NULL,
        `practice_unit_id` TEXT NOT NULL,
        `stability_days` REAL NOT NULL,
        `difficulty` REAL NOT NULL,
        `last_reviewed_at_epoch_millis` INTEGER NOT NULL,
        `next_review_at_epoch_millis` INTEGER NOT NULL,
        `independent_correct_count` INTEGER NOT NULL,
        `assisted_correct_count` INTEGER NOT NULL,
        `lapse_count` INTEGER NOT NULL,
        `answer_reveal_count` INTEGER NOT NULL,
        `last_lapse_at_epoch_millis` INTEGER,
        `clock_anomaly_count` INTEGER NOT NULL,
        `last_clock_anomaly_at_epoch_millis` INTEGER,
        `projector_version` TEXT NOT NULL,
        `checkpoint_sequence` INTEGER NOT NULL,
        `last_evidence_reason` TEXT,
        `last_evidence_direction` TEXT,
        `consecutive_cross_day_success` INTEGER NOT NULL DEFAULT 0,
        `consecutive_cross_day_again` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`projection_name`, `learner_id`, `practice_unit_id`),
        FOREIGN KEY(`projection_name`, `learner_id`)
            REFERENCES `learner_projection_snapshot`(`projection_name`, `learner_id`)
            ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`practice_unit_id`)
            REFERENCES `practice_unit`(`practice_unit_id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
    """.trimIndent(),
    """
    INSERT INTO `learner_problem_memory_state_v57` (
        `projection_name`, `learner_id`, `practice_unit_id`, `stability_days`, `difficulty`,
        `last_reviewed_at_epoch_millis`, `next_review_at_epoch_millis`,
        `independent_correct_count`, `assisted_correct_count`, `lapse_count`,
        `answer_reveal_count`, `last_lapse_at_epoch_millis`, `clock_anomaly_count`,
        `last_clock_anomaly_at_epoch_millis`, `projector_version`, `checkpoint_sequence`,
        `last_evidence_reason`, `last_evidence_direction`, `consecutive_cross_day_success`,
        `consecutive_cross_day_again`
    )
    SELECT
        `projection_name`, `learner_id`, `practice_unit_id`, `stability_days`, `difficulty`,
        `last_reviewed_at_epoch_millis`, `next_review_at_epoch_millis`,
        `independent_correct_count`, `assisted_correct_count`, `lapse_count`,
        `answer_reveal_count`, `last_lapse_at_epoch_millis`, `clock_anomaly_count`,
        `last_clock_anomaly_at_epoch_millis`, `projector_version`, `checkpoint_sequence`,
        `last_evidence_reason`, `last_evidence_direction`, `consecutive_cross_day_success`,
        `consecutive_cross_day_again`
    FROM `learner_problem_memory_state`
    """.trimIndent(),
    "DROP TABLE `learner_problem_memory_state`",
    "ALTER TABLE `learner_problem_memory_state_v57` RENAME TO `learner_problem_memory_state`",
    """
    CREATE INDEX IF NOT EXISTS `index_learner_problem_memory_state_projection_name_learner_id`
    ON `learner_problem_memory_state` (`projection_name`, `learner_id`)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS `index_learner_problem_memory_state_practice_unit_id`
    ON `learner_problem_memory_state` (`practice_unit_id`)
    """.trimIndent(),
)
