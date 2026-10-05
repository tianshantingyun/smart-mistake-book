package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v62→63：**目录记忆态连接索引**（阶段 3C 后半批 2 · S18，
 * `docs/research/2026-10-05-s9-s18-quantification-report.md`）。
 *
 * 纯**非破坏**新增：只加一条索引，既有表一列不删、一行不动、不回填。
 *
 * 消灭的具体失败（实测，不是推测）：`library_catalog` 视图把
 * `learner_problem_memory_state` 当作「learner_id 的来源」时（`mastery` 子查询的
 * `mastery.learner_id = memory.learner_id`），查询只用到该表的 `projection_name`
 * 与 `practice_unit_id`。无统计信息时规划器挑**主键索引**
 * `sqlite_autoindex_learner_problem_memory_state_1`（presentation 上"覆盖" learner_id），
 * 但主键是 `(projection_name, learner_id, practice_unit_id)`——`learner_id` 是空档，
 * 只能吃 `projection_name` 前缀，于是**逐外层行扫全部同 projection 行**。5 万行夹具实测
 * （补上投影数据后）：`LibraryQueryDao.count`（章节+掌握筛选）3972ms、
 * `subjectFacets` 78870ms；本索引（两列等值 + 覆盖 learner_id）后同一夹具 44ms / 69ms。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/63.json` 的 createSql **逐字一致**
 * （[LibraryCatalogMemoryIndexSchemaContractTest] 在 JVM 上钉住这段字面量）；
 * `FullMigrationMatrixInstrumentedTest` 在真库上钉「迁移后索引建出、旧行原样还在」。
 */
internal val LIBRARY_CATALOG_MEMORY_INDEX_MIGRATION_62_63 = object : Migration(62, 63) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(LIBRARY_CATALOG_MEMORY_INDEX_DDL_62_63)
    }
}

internal val LIBRARY_CATALOG_MEMORY_INDEX_DDL_62_63: String =
    "CREATE INDEX IF NOT EXISTS " +
        "`index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id` " +
        "ON `learner_problem_memory_state` (`projection_name`, `practice_unit_id`, `learner_id`)"
