package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v52→53：`tutor_message` 新增 `tool_trace_json`（B1 工具痕迹落库）。
 *
 * 非破坏、不重建表：纯 `ALTER TABLE ... ADD COLUMN`，SQLite 对可空列的加列是 O(1) 元数据操作，
 * 旧行读回 NULL = **无痕迹**（迁移前写下的消息当年确实没有记过痕迹，不回填、不猜测）。
 *
 * 为什么痕迹挂在**助手消息行**上（`tutor_message`）而不是新表或账本：它是"这一轮说了什么"的
 * 一部分（这一轮查过什么），与正文/思考块同一次读、同一次渲染、同一处级联删除。会话文本的唯一
 * 权威是消息行（K1a），痕迹跟着它走，历史列表与详情页就不需要第二条订阅。
 *
 * 与另一条迁移的关系：同版本内另有其他阶段要加的列（例如引导模式的 `interaction_mode`）。
 * 本文件是唯一的 v52→53 落点，新增列请**另开一个 `addXxxColumn` 私有函数**并加进行内调用，
 * 不要在本对象里做别的表结构手术。
 */
internal val TUTOR_MESSAGE_TOOL_TRACE_MIGRATION_52_53 = object : Migration(52, 53) {
    override suspend fun migrate(connection: SQLiteConnection) {
        TUTOR_MESSAGE_ADDED_COLUMNS_52_53.forEach(connection::execSQL)
        TUTOR_CONVERSATION_ADDED_COLUMNS_52_53.forEach(connection::execSQL)
    }
}

/**
 * 本版往 `tutor_message` 上加的列，**一条一句**。
 *
 * 抽成列表不是为了好看：`TutorMessageToolTraceMigrationContractTest` 直接读它，把"迁移写下的
 * DDL"与"导出的 53.json"对起来（列名、类型、可空性），并钉住**只加列、不重建表**。
 * 同版本内另一个阶段要加的列（例如引导模式的 `interaction_mode`）请**追加到这个列表**——
 * 迁移体只有这一处调用，历史迁移逐字不动。
 */
internal val TUTOR_MESSAGE_ADDED_COLUMNS_52_53: List<String> = listOf(
    "ALTER TABLE `tutor_message` ADD COLUMN `tool_trace_json` TEXT",
)

/**
 * v52→53：`tutor_conversation` 新增 `interaction_mode`（D-Q9 交互模式）。
 *
 * 与同版本的痕迹列同一条纪律：纯加列、不重建表，NOT NULL 带默认值 `'NORMAL'`——SQLite 只允许
 * 带默认值的 NOT NULL 加列，而"默认 = 当年的事实"（迁移前的会话只有正常模式），所以旧行读回
 * 是 NORMAL 而不是"未知"。本文件是 v52→53 的唯一定点，DDL 走同一个迁移体的调用。
 */
internal val TUTOR_CONVERSATION_ADDED_COLUMNS_52_53: List<String> = listOf(
    "ALTER TABLE `tutor_conversation` ADD COLUMN `interaction_mode` TEXT NOT NULL DEFAULT 'NORMAL'",
)
