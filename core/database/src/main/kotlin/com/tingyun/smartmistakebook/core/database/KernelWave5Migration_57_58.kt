package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v57→58：**阶段 3B 步骤一 · D-M M1 遗留投影表删除**（`docs/research/2026-10-02-stage3b-plan.md` §3 步骤一）。
 *
 * 两张表是 M1 fixture 播种系统的专属物，**无任何生产读者**：
 * - `problem_memory_state` / `knowledge_mastery_state` 是 v2 投影之前的denormalized 预览表，
 *   由 `FixtureSeedDao` 播种、只被 fixture 演练的列表预览消费；v35→36 起真实投影视图
 *   （`learner_problem_memory_state` / `learner_knowledge_mastery_state`）已取代它们的读路径，
 *   `ProblemDao` 的注释早已写明二者只服务历史迁移视图。
 * - 全仓（main 源集）除 FixtureSeedDao 外零 SELECT（`docs/research/2026-10-02-stage3b-plan.md` §1 基线）。
 *
 * **非破坏**：DROP 的只是 fixture 专表，学生真实学习数据不在其中（真实投影/记忆卡在
 * `learner_*` 系列表里，一行不碰）。历史迁移 SQL 一律不动：v29→30 建的
 * `library_catalog` 视图曾 LEFT JOIN 这两张表，但 v35→36 已重建为读 `learner_*` 的形态
 * （`LibraryCatalogView` 当前 DDL 逐字不含它们），老库一路升上来的每一段历史保持当时的样子。
 *
 * 迁移期间外键是关的（Room 生成代码把 `PRAGMA foreign_keys = ON` 放在 `onOpen`），
 * 两张表自身的外键随表消失；没有任何其他表引用它们，DROP 不触发级联。
 */
internal val KERNEL_WAVE5_MIGRATION_57_58 = object : Migration(57, 58) {
    override suspend fun migrate(connection: SQLiteConnection) {
        DROP_LEGACY_FIXTURE_PROJECTION_TABLES_DDL_57_58.forEach(connection::execSQL)
    }
}

/** M1：`problem_memory_state` + `knowledge_mastery_state` 整表删除（索引随表消失）。 */
internal val DROP_LEGACY_FIXTURE_PROJECTION_TABLES_DDL_57_58: List<String> = listOf(
    "DROP TABLE `problem_memory_state`",
    "DROP TABLE `knowledge_mastery_state`",
)
