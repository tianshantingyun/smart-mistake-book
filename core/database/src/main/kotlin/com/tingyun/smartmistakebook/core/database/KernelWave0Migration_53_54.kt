package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v53→54：**内核 Wave 0** 的两件事（`docs/research/2026-09-28-kernel-remediation-roadmap.md` W0-1/W0-2）。
 *
 * 一个版本只允许一个 `Migration` 对象，所以两件事共用这一处迁移体、各自一个函数，
 * 历史迁移逐字不动（v52→53 的同一约定）。
 *
 * **两件都不回填**：归档表从一开始就有行（首次重放前落），不是给旧数据补的；指纹列合并是
 * "两列恒同值"的收敛（`StudyReviewPlannerService` 一直把同一个 `planFingerprint` 写进两列），
 * 丢掉的那一列不带信息。
 */
internal val KERNEL_WAVE0_MIGRATION_53_54 = object : Migration(53, 54) {
    override suspend fun migrate(connection: SQLiteConnection) {
        createProjectionArchive(connection)
        mergeReviewPlanFingerprintColumns(connection)
    }
}

/**
 * W0-1 ③：新增投影归档表（追加历史，见 [ProjectionArchiveEntity] 的注释）。
 *
 * 纯建表、无回填：空表就是正确答案（此前的投影没有归档，编不出来也不该编）。
 */
internal val PROJECTION_ARCHIVE_DDL_53_54: List<String> = listOf(
    """
    CREATE TABLE IF NOT EXISTS `projection_archive` (
        `archive_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `projection_name` TEXT NOT NULL,
        `learner_id` TEXT NOT NULL,
        `archived_at_epoch_millis` INTEGER NOT NULL,
        `snapshot_json` TEXT NOT NULL,
        `projector_version` TEXT NOT NULL,
        `schema_ddl` TEXT NOT NULL
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS `index_projection_archive_projection_name_learner_id_archived_at_epoch_millis`
    ON `projection_archive` (`projection_name`, `learner_id`, `archived_at_epoch_millis`)
    """.trimIndent(),
)

private fun createProjectionArchive(connection: SQLiteConnection) {
    PROJECTION_ARCHIVE_DDL_53_54.forEach(connection::execSQL)
}

/** v53→54 被合并掉的那一列：读侧改读 `plan_fingerprint`，旧行不留副本。 */
internal const val REVIEW_PLAN_MERGED_AWAY_COLUMN_53_54 = "input_fingerprint"

/**
 * W0-2：`review_plan` 的两列指纹合并为一列（`plan_fingerprint`）。删列只能重建表
 * （minSdk 23 的 SQLite 没有 `DROP COLUMN`，与 v51→52 的口径一致），顺序是仓库既有范式：
 * **建新表 → INSERT SELECT 逐列搬运 → DROP 旧表 → RENAME → 重建索引**。
 *
 * 为什么安全（两条都是机械事实，不是"应该没问题"）：
 * 1. 被删的列不带信息——`StudyReviewPlannerService` 从第一天起就把同一个 `plan.planFingerprint`
 *    同时写进 `input_fingerprint` 与 `plan_fingerprint`，所以"搬运旧行"就是把 `plan_fingerprint`
 *    原样留在原位，不需要（也不许）编造第二份值；
 * 2. 迁移期间外键是**关**的：Room 的生成代码把 `PRAGMA foreign_keys = ON` 放在 `onOpen`
 *    （迁移之后），所以 `DROP TABLE review_plan` 不会触发子表（`review_queue_item` /
 *    `review_session*` 等 CASCADE 外键）的级联删除。这条由
 *    `FullMigrationMatrixInstrumentedTest.reviewPlanFingerprintMergeKeepsExistingRows`
 *    在真库上实测（旧计划行与它的队列行都必须还在），不是靠推理。
 *
 * 索引逐条重建，名字与 `54.json` 完全一致：其中
 * `index_review_plan_learner_id_local_day_epoch_day_time_zone_id_review_plan_id` 是子表
 * 复合外键的**父键**，漏了它子表外键就没有可指的唯一索引。
 */
internal val REVIEW_PLAN_FINGERPRINT_MERGE_DDL_53_54: List<String> = listOf(
    """
    CREATE TABLE IF NOT EXISTS `review_plan_v54` (
        `review_plan_id` TEXT NOT NULL,
        `learner_id` TEXT NOT NULL,
        `local_date` TEXT NOT NULL,
        `local_day_epoch_day` INTEGER NOT NULL,
        `time_zone_id` TEXT NOT NULL,
        `time_budget_seconds` INTEGER NOT NULL,
        `planning_at_epoch_millis` INTEGER NOT NULL,
        `status` TEXT NOT NULL,
        `planner_version` TEXT NOT NULL,
        `projection_checkpoint` INTEGER NOT NULL,
        `plan_fingerprint` TEXT NOT NULL,
        `plan_revision` INTEGER NOT NULL,
        `created_at_epoch_millis` INTEGER NOT NULL,
        PRIMARY KEY(`review_plan_id`)
    )
    """.trimIndent(),
    """
    INSERT INTO `review_plan_v54` (
        `review_plan_id`, `learner_id`, `local_date`, `local_day_epoch_day`, `time_zone_id`,
        `time_budget_seconds`, `planning_at_epoch_millis`, `status`, `planner_version`,
        `projection_checkpoint`, `plan_fingerprint`, `plan_revision`, `created_at_epoch_millis`
    )
    SELECT
        `review_plan_id`, `learner_id`, `local_date`, `local_day_epoch_day`, `time_zone_id`,
        `time_budget_seconds`, `planning_at_epoch_millis`, `status`, `planner_version`,
        `projection_checkpoint`, `plan_fingerprint`, `plan_revision`, `created_at_epoch_millis`
    FROM `review_plan`
    """.trimIndent(),
    "DROP TABLE `review_plan`",
    "ALTER TABLE `review_plan_v54` RENAME TO `review_plan`",
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    `index_review_plan_learner_id_local_day_epoch_day_time_zone_id_review_plan_id`
    ON `review_plan` (`learner_id`, `local_day_epoch_day`, `time_zone_id`, `review_plan_id`)
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS `index_review_plan_plan_fingerprint`
    ON `review_plan` (`plan_fingerprint`)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS `index_review_plan_status_local_date`
    ON `review_plan` (`status`, `local_date`)
    """.trimIndent(),
)

private fun mergeReviewPlanFingerprintColumns(connection: SQLiteConnection) {
    REVIEW_PLAN_FINGERPRINT_MERGE_DDL_53_54.forEach(connection::execSQL)
}
