package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v71→73（批次 40 / WS2，**rebase 后的主路径**）与 v72→73（72.json 为 orphan 时仍可迁移）：
 * 新增错因两表 `error_cause_observation` + `error_cause_binding`。
 *
 * **基线裁定（2026-11-02 复核轮，主会话）**：KB 32B 已按「L2 不可判→回滚」删除
 * `KnowledgeSearchMaterialFeatureMigration_71_72.kt` 与实体，71→72 链不存在；本批
 * **不恢复 L2**，直接把错因两表叠在 71 上（schema 73 = 90 + 2 = 92 表）。`MIGRATION_71_73`
 * 是真实用户从 71 升级的主路径；`ERROR_CAUSE_MIGRATION_72_73` 只保留给 from-72 快照
 * （`72.json` 仍 tracked，作为无迁移链引用的 orphan，不删不改）。
 *
 * **只建空表 + 索引，不回填任何历史行**（R2 §4.6）：
 * - 仓内没有任何错因标注样本（0 条），回填只能编造；
 * - `attempt_event.error_type` 三列（R-09）无写入方、无来源、无撤销语义，不能当历史真值搬进来。
 *
 * **消灭的具体失败**：没有这两张表时，"模型判错因"只能写进 `attempt_event.error_type` 或
 * 题级分类表——前者一次作答只能有一个值、无候选/拒绝/撤回；后者没有 learner/attempt 维度、
 * 且持久化 `display_name` 违反产品合同。两张表把"候选（含被拒）"与"现行标签（含撤回）"
 * 分开，读侧只认 `state='ACTIVE'`，漏谓词不会把被拒候选当现行标签。
 *
 * **两表 FK 都是 RESTRICT**：错因行不能被父行静默带走；`active_key` 唯一索引（可空）兜底
 * 同一 scope 最多一条 ACTIVE。
 *
 * **生成与回填（不在本文件）**：rebased `73.json`（92 表）与 `identityHash` 只能由带 KSP
 * 的构建产出（`:core:database:kspDebugKotlin`）。本文件只落 DDL；2026-11-02 13:35 门禁已
 * 生成 `73.json`（version 73 / entities 92 / views 2 / FK 110），`SmbkSelectionPlan.IDENTITY_HASH`
 * 已回填 `1d086050204a0da1ed6b6e3299dd9733`，禁止估算。
 */
internal val MIGRATION_71_73 = object : Migration(71, 73) {
    override suspend fun migrate(connection: SQLiteConnection) {
        createErrorCauseTables(connection)
    }
}

internal val ERROR_CAUSE_MIGRATION_72_73 = object : Migration(72, 73) {
    override suspend fun migrate(connection: SQLiteConnection) {
        createErrorCauseTables(connection)
    }
}

/** 两条迁移共用同一组 DDL，避免 71→73 与 72→73 的表/索引形状分叉。 */
private fun createErrorCauseTables(connection: SQLiteConnection) {
    connection.execSQL(CREATE_ERROR_CAUSE_OBSERVATION_TABLE_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_OBSERVATION_LEARNER_UNIT_OBSERVED_INDEX_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_OBSERVATION_ATTEMPT_INDEX_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_OBSERVATION_UNIT_CAUSE_INDEX_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_BINDING_TABLE_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_BINDING_ACTIVE_KEY_INDEX_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_BINDING_LEARNER_UNIT_STATE_INDEX_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_BINDING_REVISION_INDEX_72_73)
    connection.execSQL(CREATE_ERROR_CAUSE_BINDING_SOURCE_OBSERVATION_INDEX_72_73)
}

/** 列序必须与 `ErrorCauseObservationEntity` 逐位一致（Room 迁移后按 TableInfo 校验）。 */
internal val CREATE_ERROR_CAUSE_OBSERVATION_TABLE_72_73: String =
    "CREATE TABLE IF NOT EXISTS `error_cause_observation` (" +
        "`observation_id` TEXT NOT NULL, " +
        "`learner_id` TEXT NOT NULL, " +
        "`practice_unit_id` TEXT NOT NULL, " +
        "`problem_revision_id` TEXT NOT NULL, " +
        "`knowledge_node_id` TEXT, " +
        "`attempt_id` TEXT, " +
        "`cause` TEXT NOT NULL, " +
        "`detail_code` TEXT, " +
        "`confidence` REAL NOT NULL, " +
        "`channel` TEXT NOT NULL, " +
        "`extractor_version` TEXT NOT NULL, " +
        "`taxonomy_version` TEXT NOT NULL, " +
        "`rationale_markdown` TEXT NOT NULL, " +
        "`evidence_refs_json` TEXT NOT NULL, " +
        "`observed_at_epoch_millis` INTEGER NOT NULL, " +
        "`status` TEXT NOT NULL, " +
        "`decided_at_epoch_millis` INTEGER, " +
        "`decision_source` TEXT, " +
        "`supersedes_observation_id` TEXT, " +
        "PRIMARY KEY(`observation_id`), " +
        "FOREIGN KEY(`practice_unit_id`) REFERENCES `practice_unit`(`practice_unit_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`problem_revision_id`) REFERENCES `problem_revision`(`revision_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`attempt_id`) REFERENCES `attempt_event`(`attempt_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`supersedes_observation_id`) " +
        "REFERENCES `error_cause_observation`(`observation_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT)"

internal val CREATE_ERROR_CAUSE_OBSERVATION_LEARNER_UNIT_OBSERVED_INDEX_72_73: String =
    "CREATE INDEX IF NOT EXISTS `index_error_cause_observation_learner_unit_observed` " +
        "ON `error_cause_observation` " +
        "(`learner_id`, `practice_unit_id`, `observed_at_epoch_millis`)"

internal val CREATE_ERROR_CAUSE_OBSERVATION_ATTEMPT_INDEX_72_73: String =
    "CREATE INDEX IF NOT EXISTS `index_error_cause_observation_attempt_id` " +
        "ON `error_cause_observation` (`attempt_id`)"

internal val CREATE_ERROR_CAUSE_OBSERVATION_UNIT_CAUSE_INDEX_72_73: String =
    "CREATE INDEX IF NOT EXISTS `index_error_cause_observation_unit_cause` " +
        "ON `error_cause_observation` (`practice_unit_id`, `cause`)"

/** 列序必须与 `ErrorCauseBindingEntity` 逐位一致。 */
internal val CREATE_ERROR_CAUSE_BINDING_TABLE_72_73: String =
    "CREATE TABLE IF NOT EXISTS `error_cause_binding` (" +
        "`binding_id` TEXT NOT NULL, " +
        "`learner_id` TEXT NOT NULL, " +
        "`practice_unit_id` TEXT NOT NULL, " +
        "`problem_revision_id` TEXT NOT NULL, " +
        "`knowledge_node_id` TEXT, " +
        "`cause` TEXT NOT NULL, " +
        "`detail_code` TEXT, " +
        "`confidence` REAL NOT NULL, " +
        "`acceptance_source` TEXT NOT NULL, " +
        "`gate_version` TEXT, " +
        "`source_observation_id` TEXT NOT NULL, " +
        "`accepted_at_epoch_millis` INTEGER NOT NULL, " +
        "`state` TEXT NOT NULL, " +
        "`active_key` TEXT, " +
        "`superseded_by_binding_id` TEXT, " +
        "`ended_at_epoch_millis` INTEGER, " +
        "PRIMARY KEY(`binding_id`), " +
        "FOREIGN KEY(`practice_unit_id`) REFERENCES `practice_unit`(`practice_unit_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`problem_revision_id`) REFERENCES `problem_revision`(`revision_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`source_observation_id`) " +
        "REFERENCES `error_cause_observation`(`observation_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
        "FOREIGN KEY(`superseded_by_binding_id`) REFERENCES `error_cause_binding`(`binding_id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT)"

internal val CREATE_ERROR_CAUSE_BINDING_ACTIVE_KEY_INDEX_72_73: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_error_cause_binding_active_key` " +
        "ON `error_cause_binding` (`active_key`)"

internal val CREATE_ERROR_CAUSE_BINDING_LEARNER_UNIT_STATE_INDEX_72_73: String =
    "CREATE INDEX IF NOT EXISTS `index_error_cause_binding_learner_unit_state` " +
        "ON `error_cause_binding` (`learner_id`, `practice_unit_id`, `state`)"

internal val CREATE_ERROR_CAUSE_BINDING_REVISION_INDEX_72_73: String =
    "CREATE INDEX IF NOT EXISTS `index_error_cause_binding_problem_revision_id` " +
        "ON `error_cause_binding` (`problem_revision_id`)"

internal val CREATE_ERROR_CAUSE_BINDING_SOURCE_OBSERVATION_INDEX_72_73: String =
    "CREATE INDEX IF NOT EXISTS `index_error_cause_binding_source_observation_id` " +
        "ON `error_cause_binding` (`source_observation_id`)"
