package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v66→67：**C11 收藏（读侧） + A8 手动建题（schema 半边）**，两处合并在**一个**迁移里
 * （一批一个迁移：两处都要开库，分成两个只会让真机升级多开一次、也让矩阵测试多一段）。
 *
 * 1. `error_book_entry` 加 `is_favorite INTEGER NOT NULL DEFAULT 0`（收藏是条目的私有状态，
 *    与 `user_note` 同层：可自由改动，不属于 append-only 的修订链）。消灭的具体失败：
 *    改前错题本只能按科目/板块/掌握程度/时间筛，"我最想重看的那几道"没有任何可持久化的
 *    表达——收藏只能是每次重新找一遍。纯**非破坏**新增：旧库升级后全员 0（未收藏），
 *    不编造历史收藏。
 * 2. `problem_draft.source_asset_id` 改为**可空**——A8 手动建题（用户转写）的草稿没有照片。
 *    SQLite 的列可空性不能 ALTER，只能重建表：建新表 → 逐列搬运旧行 → DROP → RENAME →
 *    重建两条索引（与 `KernelWave4Migration_56_57` 同一范式）。迁移期间外键是关的
 *    （Room 生成代码把 `PRAGMA foreign_keys = ON` 放在 `onOpen`，迁移之后——见
 *    `StudyDatabase_Impl.onOpen`），所以 DROP 父表不会级联删子行；`problem_draft` 的四个
 *    子表（`problem_draft_source_asset`/`problem_draft_revision`/`problem_draft_edit_snapshot`/
 *    `problem_draft_commit_receipt`）在重建后引用的仍是同一个表名。旧行一列不动、一行不丢；
 *    存量草稿都有照片，搬运后 `source_asset_id` 全部非空。
 * 3. `library_catalog` 视图多一列 `entry.is_favorite AS is_favorite`（列表/搜索两条查询链
 *    都从这个视图取行，收藏筛选与行级开关的读值都落在它上面）。视图只能重建
 *    （DROP + CREATE），文本与 `@DatabaseView` 经 Room 导出到 `schemas/67.json` 的
 *    createSql **逐字一致**（`FavoriteAndManualDraftSchemaContractTest` 在 JVM 上钉住；
 *    差一个字符都会在真机升级时被 Room 的 schema 校验炸掉）。
 *
 * 注：`schemas/67.json` 是**构建产物**（KSP 写出），不手工维护——本轮实施不运行 Gradle，
 * 所以它随构建出现；契约测试缺了它会红（说明这次构建没有跑 schema 导出）。
 */
internal val FAVORITE_AND_MANUAL_DRAFT_MIGRATION_66_67 =
    object : Migration(66, 67) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(ERROR_BOOK_ENTRY_FAVORITE_DDL_66_67)
            PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67.forEach(connection::execSQL)
            LIBRARY_CATALOG_FAVORITE_VIEW_DDL_66_67.forEach(connection::execSQL)
        }
    }

/**
 * 与 `schemas/67.json` 里 `error_book_entry` 的 `is_favorite` 列声明逐字一致。
 * `ALTER TABLE ... ADD COLUMN` 是追加：列在行尾、旧行读回 DEFAULT 0（未收藏，不是未知）。
 */
internal val ERROR_BOOK_ENTRY_FAVORITE_DDL_66_67: String =
    "ALTER TABLE `error_book_entry` " +
        "ADD COLUMN `is_favorite` INTEGER NOT NULL DEFAULT 0"

/**
 * `problem_draft` 重建为 `source_asset_id` 可空的 67 形态。列序/类型/NOT NULL/主键/外键
 * 与 `schemas/67.json` 的 createSql 逐位一致（只把 `source_asset_id` 的 NOT NULL 拿掉）。
 */
internal val PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `problem_draft_v67` (" +
        "`draft_id` TEXT NOT NULL, `source_asset_id` TEXT, `origin` TEXT NOT NULL, " +
        "`status` TEXT NOT NULL, `current_revision_number` INTEGER NOT NULL, " +
        "`created_at_epoch_millis` INTEGER NOT NULL, " +
        "`updated_at_epoch_millis` INTEGER NOT NULL, `request_fingerprint` TEXT, " +
        "PRIMARY KEY(`draft_id`), " +
        "FOREIGN KEY(`source_asset_id`) REFERENCES `canonical_source_asset`(`source_asset_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )",
    "INSERT INTO `problem_draft_v67` (" +
        "`draft_id`, `source_asset_id`, `origin`, `status`, `current_revision_number`, " +
        "`created_at_epoch_millis`, `updated_at_epoch_millis`, `request_fingerprint`) " +
        "SELECT `draft_id`, `source_asset_id`, `origin`, `status`, " +
        "`current_revision_number`, `created_at_epoch_millis`, `updated_at_epoch_millis`, " +
        "`request_fingerprint` FROM `problem_draft`",
    "DROP TABLE `problem_draft`",
    "ALTER TABLE `problem_draft_v67` RENAME TO `problem_draft`",
    "CREATE INDEX IF NOT EXISTS `index_problem_draft_source_asset_id` " +
        "ON `problem_draft` (`source_asset_id`)",
    "CREATE INDEX IF NOT EXISTS `index_problem_draft_status_updated_at_epoch_millis` " +
        "ON `problem_draft` (`status`, `updated_at_epoch_millis`)",
)

/** 与 `schemas/67.json` 里 `library_catalog` 的 createSql 逐字一致（只多一列收藏标志）。 */
internal val LIBRARY_CATALOG_FAVORITE_VIEW_DDL_66_67: List<String> = listOf(
    "DROP VIEW IF EXISTS `library_catalog`",
    "CREATE VIEW `library_catalog` AS SELECT\n" +
    "            entry.entry_id,\n" +
    "            entry.problem_id,\n" +
    "            revision.revision_id AS problem_revision_id,\n" +
    "            entry.practice_unit_id,\n" +
    "            problem.subject,\n" +
    "            revision.title,\n" +
    "            revision.problem_markdown,\n" +
    "            entry.accepted_at_epoch_millis AS created_at_epoch_millis,\n" +
    "            entry.updated_at_epoch_millis AS updated_at_epoch_millis,\n" +
    "            entry.is_favorite AS is_favorite,\n" +
    "            memory.next_review_at_epoch_millis,\n" +
    "            NULL AS retrievability,\n" +
    "            memory.learner_id AS memory_learner_id,\n" +
    "            (\n" +
    "                SELECT\n" +
    "                    CASE\n" +
    "                        WHEN COUNT(*) = 0 THEN 'unknown'\n" +
    "                        WHEN SUM(CASE WHEN mastery.status = 'CONFLICTED' THEN 1 ELSE 0 END) > 0\n" +
    "                            THEN 'conflicted'\n" +
    "                        WHEN SUM(CASE WHEN mastery.status = 'STALE' THEN 1 ELSE 0 END) > 0\n" +
    "                            THEN 'stale'\n" +
    "                        WHEN SUM(CASE WHEN mastery.status = 'LEARNING' THEN 1 ELSE 0 END) > 0\n" +
    "                            THEN 'learning'\n" +
    "                        WHEN SUM(CASE WHEN mastery.status = 'MASTERED' THEN 1 ELSE 0 END) =\n" +
    "                            COUNT(*) THEN 'mastered'\n" +
    "                        ELSE 'unknown'\n" +
    "                    END\n" +
    "                FROM learner_knowledge_mastery_state AS mastery\n" +
    "                INNER JOIN practice_unit_knowledge_binding AS binding\n" +
    "                    ON binding.knowledge_node_id = mastery.knowledge_node_id\n" +
    "                   AND binding.practice_unit_id = entry.practice_unit_id\n" +
    "                   AND binding.basis_revision_id = revision.revision_id\n" +
    "                WHERE mastery.projection_name = 'study-experience-v1'\n" +
    "                  AND mastery.learner_id = memory.learner_id\n" +
    "            ) AS mastery_id,\n" +
    "            (\n" +
    "                SELECT GROUP_CONCAT(classification.display_name, CHAR(31))\n" +
    "                FROM problem_classification_binding AS classification\n" +
    "                WHERE classification.problem_id = entry.problem_id\n" +
    "                  AND classification.basis_revision_id = revision.revision_id\n" +
    "                  AND classification.dimension = 'CHAPTER'\n" +
    "            ) AS chapter_labels,\n" +
    "            (\n" +
    "                SELECT GROUP_CONCAT(classification.display_name, CHAR(31))\n" +
    "                FROM problem_classification_binding AS classification\n" +
    "                WHERE classification.problem_id = entry.problem_id\n" +
    "                  AND classification.basis_revision_id = revision.revision_id\n" +
    "                  AND classification.dimension = 'KNOWLEDGE'\n" +
    "            ) AS knowledge_labels\n" +
    "        FROM error_book_entry AS entry\n" +
    "        JOIN practice_unit AS unit\n" +
    "            ON unit.practice_unit_id = entry.practice_unit_id\n" +
    "        JOIN problem AS problem\n" +
    "            ON problem.problem_id = entry.problem_id\n" +
    "        JOIN problem_revision AS revision\n" +
    "            ON revision.revision_id = entry.current_revision_id\n" +
    "           AND revision.problem_id = entry.problem_id\n" +
    "        LEFT JOIN learner_problem_memory_state AS memory\n" +
    "            ON memory.practice_unit_id = entry.practice_unit_id\n" +
    "           AND memory.projection_name = 'study-experience-v1'\n" +
    "        WHERE entry.status = 'ACTIVE'",
)
