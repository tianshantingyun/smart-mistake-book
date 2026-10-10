package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v63→64：`problem_classification_binding` 增加 `accepted_confidence`（可空 `REAL`）——
 * 分类标签**被接受时的真实置信度**（KF-30，裁定 `docs/research/2026-10-09-kf30-semantics-rulings.md`）。
 *
 * 消灭的失败（(a)(b) 的共同根因）：合并（`mergeAutomaticClassifications`）此前只能把存量
 * 自动行一律估成接受门 0.78——恰好 0.78 的新提议"同值永不覆盖"（(a)），而 0.79 的新提议能顶掉
 * 曾以 0.99 接受的老标签（(b)）。真值落库后，合并读真实值参与"严格更高者胜、等值保留存量"。
 *
 * 纯**非破坏**新增：只加一列，既有表一列不删、一行不动、**不回填**——旧行的该列留 `NULL`，
 * 语义 = 未知（升级时刻真值已不可考）。合并时 NULL 按门值参与比较（Q2 interim：未知不冻结，
 * 任何严格更高且 ≥ 门值的新提议都能替换它；此后新提议的真值随写路径落库，系统自纠）。
 *
 * 列定义与本模块 `@Entity` 经 Room 导出到 `schemas/64.json` 的 createSql **逐字同形**——
 * `ALTER TABLE ... ADD COLUMN `accepted_confidence` REAL` 使用的正是导出的列定义片段；
 * `ProblemClassificationAcceptedConfidenceSchemaContractTest` 在 JVM 上钉住这一点（差一个空格
 * 都会在真机升级时被 Room 的 schema 校验炸掉）。`FullMigrationMatrixInstrumentedTest` 在真库上
 * 钉"迁移后旧行还在、旧行该列为 NULL"。
 */
internal val PROBLEM_CLASSIFICATION_ACCEPTED_CONFIDENCE_MIGRATION_63_64 =
    object : Migration(63, 64) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(PROBLEM_CLASSIFICATION_ACCEPTED_CONFIDENCE_DDL_63_64)
        }
    }

/**
 * 与 `schemas/64.json` 里 `problem_classification_binding` 的 `accepted_confidence` 列定义
 * 逐字一致（见上方 KDoc）；SQLite 的 `ALTER TABLE ... ADD COLUMN` 把新列追加在行尾，
 * 与实体声明顺序（`accepted_confidence` 为最后一列）一致。
 */
internal val PROBLEM_CLASSIFICATION_ACCEPTED_CONFIDENCE_DDL_63_64: String =
    "ALTER TABLE `problem_classification_binding` ADD COLUMN `accepted_confidence` REAL"
