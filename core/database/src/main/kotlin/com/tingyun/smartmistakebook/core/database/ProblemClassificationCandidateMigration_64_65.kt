package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v64→65：新增 `problem_classification_candidate` —— 一次整理确认里**模型给出的分类候选集与
 * 自报分数**（含被拒候选）（K5-B · R1 机制 ② 的前置，研究文档
 * `docs/research/2026-10-11-r1-r2-label-accuracy-research.md` §1.3）。
 *
 * 消灭的具体失败：改前只有 `problem_classification_binding` 记录**被接受**的标签，模型给过什么、
 * 给了多少分、哪些没过接受门（0.78）或被预算挤掉——**全部不可考**。于是 cleanlab
 * `normalized_margin`（`p(选中) − max(p(其它候选同维度))`）算不出来，"接受门截断分数"
 * 造成的选择偏差也看不见；R1 的自纠机制因此**没有信号可用**（外部共识：先有信号，再谈自纠）。
 *
 * 纯**非破坏**新增：只建一张新表与它的索引，既有表一列不删、一行不动、不回填。旧库升级后
 * 该表为空——候选历史从本轮起积累（"无样本"如实呈现，不编造历史分数）。
 *
 * DDL 与本模块 `@Entity` 经 Room 导出到 `schemas/65.json` 的 createSql/索引 **逐字一致**
 * （[ProblemClassificationCandidateSchemaContractTest] 在 JVM 上钉住；差一个字符都会在真机升级时
 * 被 Room 的 schema 校验炸掉）。`FullMigrationMatrixInstrumentedTest` 在真库上钉
 * "迁移后新表建出、旧行原样还在、再开一次不漂"。
 */
internal val PROBLEM_CLASSIFICATION_CANDIDATE_MIGRATION_64_65 =
    object : Migration(64, 65) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(PROBLEM_CLASSIFICATION_CANDIDATE_DDL_64_65)
            connection.execSQL(PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_PROBLEM_64_65)
            connection.execSQL(PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_LABEL_64_65)
            connection.execSQL(PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_ROUND_64_65)
        }
    }

/** 与 `schemas/65.json` 里 `problem_classification_candidate` 的 createSql 逐字一致。 */
internal val PROBLEM_CLASSIFICATION_CANDIDATE_DDL_64_65: String =
    "CREATE TABLE IF NOT EXISTS `problem_classification_candidate` (" +
        "`candidate_id` TEXT NOT NULL, `problem_id` TEXT NOT NULL, " +
        "`basis_revision_id` TEXT NOT NULL, `dimension` TEXT NOT NULL, " +
        "`label_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
        "`confidence` REAL NOT NULL, `accepted` INTEGER NOT NULL, " +
        "`acceptance_source` TEXT NOT NULL, " +
        "`accepted_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`candidate_id`), " +
        "FOREIGN KEY(`problem_id`, `basis_revision_id`) " +
        "REFERENCES `problem_revision`(`problem_id`, `revision_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )"

/** 与 65.json 里同名索引逐字一致（FK 前缀 + 按题读候选）。 */
internal val PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_PROBLEM_64_65: String =
    "CREATE INDEX IF NOT EXISTS " +
        "`index_problem_classification_candidate_problem_id_basis_revision_id` " +
        "ON `problem_classification_candidate` (`problem_id`, `basis_revision_id`)"

/** 与 65.json 里同名索引逐字一致（按标签跨轮读：同一标签的分数漂移/纠正审计）。 */
internal val PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_LABEL_64_65: String =
    "CREATE INDEX IF NOT EXISTS " +
        "`index_problem_classification_candidate_dimension_label_id` " +
        "ON `problem_classification_candidate` (`dimension`, `label_id`)"

/** 与 65.json 里同名索引逐字一致（按轮分组：margin 的"同维度对手集"）。 */
internal val PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_ROUND_64_65: String =
    "CREATE INDEX IF NOT EXISTS " +
        "`index_problem_classification_candidate_problem_id_basis_revision_id_accepted_at_epoch_millis` " +
        "ON `problem_classification_candidate` " +
        "(`problem_id`, `basis_revision_id`, `accepted_at_epoch_millis`)"
