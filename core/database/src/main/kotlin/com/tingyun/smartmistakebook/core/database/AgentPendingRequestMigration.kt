package com.tingyun.smartmistakebook.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * `agent_pending_request`（插眼 5）的建表 DDL：新表，随 K1 的 **51 → 52** 一起落
 * （阶段 1 的 schema 窗口，规格 §8「新表/新列 → bump 版本 + 非破坏迁移 + 导出 schema」）。
 *
 * 为什么并进 51→52 而不是自己一版：版本号是稀缺的（每个版本都要在迁移矩阵里走一遍全部旧
 * 版本），而这张表与 K1 是同一批工作——同一窗口里两张表各占一版，只会让矩阵多一段永远
 * 没有过的历史。
 *
 * 非破坏：新表、无回填、不碰任何旧行。旧安装升上来得到一张空表（没有待确认的卡就是没有），
 * 新安装直接由 Room 建同样的形状。
 *
 * **DDL 必须与导出的 `52.json` 的 createSql 逐字一致**（Room 在迁移后校验表结构，校验不过会抛
 * `IllegalStateException` 而不是静默降级）。手写 DDL 与导出不一致是这类迁移最常见的事故，
 * 所以这里的两份 DDL 由 [AgentPendingRequestMigrationContractTest] 对着 52.json 逐字盯着。
 */
internal fun createAgentPendingRequestTable(connection: SQLiteConnection) {
    connection.execSQL(AGENT_PENDING_REQUEST_DDL)
    AGENT_PENDING_REQUEST_INDEX_DDL.forEach(connection::execSQL)
}

/** Must match the 52.json exported createSql for agent_pending_request byte for byte. */
internal const val AGENT_PENDING_REQUEST_DDL =
    "CREATE TABLE IF NOT EXISTS `agent_pending_request` (" +
        "`request_id` TEXT NOT NULL, " +
        "`conversation_area` TEXT NOT NULL, " +
        "`conversation_id` TEXT NOT NULL, " +
        "`logical_operation_id` TEXT NOT NULL, " +
        "`message_id` TEXT NOT NULL, " +
        "`kind` TEXT NOT NULL, " +
        "`payload_json` TEXT NOT NULL, " +
        "`status` TEXT NOT NULL, " +
        "`created_at_epoch_millis` INTEGER NOT NULL, " +
        "`resolved_at_epoch_millis` INTEGER, " +
        "`resolution_note` TEXT, " +
        "PRIMARY KEY(`request_id`), " +
        "FOREIGN KEY(`conversation_id`) REFERENCES `tutor_conversation`(`conversation_id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )"

/** Must match the 52.json exported index createSql for agent_pending_request, in export order. */
internal val AGENT_PENDING_REQUEST_INDEX_DDL: List<String> = listOf(
    "CREATE INDEX IF NOT EXISTS `index_agent_pending_request_status_created_at_epoch_millis` " +
        "ON `agent_pending_request` (`status`, `created_at_epoch_millis`)",
    "CREATE INDEX IF NOT EXISTS `index_agent_pending_request_conversation_id` " +
        "ON `agent_pending_request` (`conversation_id`)",
    "CREATE INDEX IF NOT EXISTS `index_agent_pending_request_logical_operation_id` " +
        "ON `agent_pending_request` (`logical_operation_id`)",
)
