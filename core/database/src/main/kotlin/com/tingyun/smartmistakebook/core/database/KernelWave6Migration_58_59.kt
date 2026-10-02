package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v58→59：**KF-32 改绑补偿事件的载荷表**（`docs/research/2026-10-02-stage3b-plan.md` §3 步骤三 1）。
 *
 * 新增一张只增不改的事件表 `binding_change_event` 与一个索引。既有表一列不删、一行不动
 * （纯**非破坏**迁移）：`practice_unit_knowledge_binding` 是当前值表，改绑仍在原地生效；
 * 这条事件只是"这次改绑发生过"的不可变凭据，供账本读边界重建 `BindingChanged` 并校验规范指纹。
 *
 * DDL 与 `StudyDatabase` 注册的 [BindingChangeEventEntity] 逐字同形（列序/类型/NOT NULL/
 * 索引名）；`FullMigrationMatrixInstrumentedTest` 在真库上钉"旧行原样还在 + 表已建出"。
 */
internal val KERNEL_WAVE6_MIGRATION_58_59 = object : Migration(58, 59) {
    override suspend fun migrate(connection: SQLiteConnection) {
        BINDING_CHANGE_EVENT_DDL_58_59.forEach(connection::execSQL)
    }
}

internal val BINDING_CHANGE_EVENT_DDL_58_59: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `binding_change_event` (" +
        "`binding_change_id` TEXT NOT NULL, " +
        "`learner_id` TEXT NOT NULL, " +
        "`practice_unit_id` TEXT NOT NULL, " +
        "`previous_knowledge_node_ids` TEXT NOT NULL, " +
        "`new_knowledge_node_ids` TEXT NOT NULL, " +
        "`occurred_at_epoch_millis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`binding_change_id`)" +
        ")",
    "CREATE INDEX IF NOT EXISTS `index_binding_change_event_learner_id_practice_unit_id` " +
        "ON `binding_change_event` (`learner_id`, `practice_unit_id`)",
)
