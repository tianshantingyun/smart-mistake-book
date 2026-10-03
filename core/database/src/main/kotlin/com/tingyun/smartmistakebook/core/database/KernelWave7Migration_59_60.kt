package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v59→60：**错题本标题单源**（阶段 4A 批 1 · L2，`docs/research/2026-10-03-stage4a-plan.md` §3 批 1）。
 *
 * 读侧唯一的库内形态变化：`library_catalog` 视图的标题列由 `unit.title` 改为
 * `revision.title`（视图本就 JOIN 了 `problem_revision`，`revision.revision_id =
 * entry.current_revision_id`），让列表/目录与详情/历史/FTS 读同一处标题。
 *
 * **写侧不动**：`practice_unit.title` 列与提交时的双列同写保持原样（计划 §5②；列删除的
 * 收益不抵 RESTRICT 子表重建风险）。因此这不是“加一份冗余”，而是**读侧挑明单源**：
 * 唯一被读的标题是 `revision.title`，`unit.title` 退化为枢纽列。
 *
 * 迁移 = **重建视图**（DROP + CREATE）。迁移里的 CREATE 语句与 `@DatabaseView`
 * (`LibraryCatalogView.kt`) 经 Room 导出到 `schemas/60.json` 的 createSql **逐字一致**
 * (`KernelWave7SchemaContractTest` 在 JVM 上钉住这段字面量)；迁移不动任何表、任何行。
 */
internal val KERNEL_WAVE7_MIGRATION_59_60 = object : Migration(59, 60) {
    override suspend fun migrate(connection: SQLiteConnection) {
        LIBRARY_CATALOG_TITLE_SOURCE_DDL_59_60.forEach(connection::execSQL)
    }
}

internal val LIBRARY_CATALOG_TITLE_SOURCE_DDL_59_60: List<String> = listOf(
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
