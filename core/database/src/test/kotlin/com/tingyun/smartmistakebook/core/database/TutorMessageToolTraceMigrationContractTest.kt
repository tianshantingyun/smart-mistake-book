package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.toRecord
import com.tingyun.smartmistakebook.core.database.entity.TutorMessageEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v52→53 的迁移契约（B1 的工具痕迹列）。
 *
 * 三层证据，都是 JVM 上就能炸的：
 * 1. **迁移写的 DDL == 导出的 53.json 里的那一列**（列名、类型、可空性）——手写 DDL 与导出漂开
 *    是迁移最常见的翻车方式，而它平时只在真库上炸；
 * 2. **只加列、不重建表**（没有 CREATE/DROP/INSERT/UPDATE/DELETE，且表名就是 `tutor_message`）
 *    ——这是"旧行全部可读"的直接来源：SQLite 加可空列是元数据操作，旧行不会被改写；
 * 3. **旧行读回 = 没有痕迹**：新列在 52.json 里不存在、在 53.json 里可空且无默认值，
 *    映射层对 NULL 的处理就是 null（见 [TutorMessageEntity.toRecord]）。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 非破坏"，`FullMigrationMatrixInstrumentedTest`
 * 管"迁移出来的库与 schema 文件结构一致"（真库、真 SQLite 驱动）。
 */
class TutorMessageToolTraceMigrationContractTest {

    @Test
    fun theMigratedColumnIsTheExported53Column() {
        val migrated = migratedColumns().single()
        assertEquals("tutor_message", migrated.table)
        assertEquals("tool_trace_json", migrated.column)
        assertEquals("TEXT", migrated.affinity)

        val exported = exportedColumn(column = "tool_trace_json")
        assertEquals("TEXT", exported.affinity)
        assertNull(
            "可空是「旧行读回 NULL」的前提：迁移不许给这一列加 NOT NULL",
            exported.notNull,
        )
        assertNull(
            "这一列没有默认值：NULL 就是「没有痕迹」这一个语义，不需要第二种空值",
            exported.defaultValue,
        )
    }

    @Test
    fun theMigrationOnlyAddsAColumnSoOldRowsSurvive() {
        // 非破坏性：一条 ALTER TABLE ... ADD COLUMN，别的什么都没有（没有重建表、没有搬数据）。
        // 表重建是"旧行可能读不出来"的唯一来源，这条断言就是"旧行可读"的机器门。
        val statements = TUTOR_MESSAGE_ADDED_COLUMNS_52_53
        assertTrue("这一版必须至少加一列", statements.isNotEmpty())
        assertTrue(
            "本次任务要加的列必须在其中",
            migratedColumns().any { column -> column.column == "tool_trace_json" },
        )
        statements.forEach { statement ->
            val normalized = statement.trim().replace(Regex("\\s+"), " ")
            val match = ADD_COLUMN_PATTERN.matchEntire(normalized)
            assertTrue(
                "迁移语句必须是 tutor_message 上的单条 ADD COLUMN（非破坏）：$normalized",
                match != null,
            )
            assertEquals("tutor_message", match!!.groupValues[1])
        }
    }

    @Test
    fun everyColumnTheMigrationAddsAlsoAppearsIn53() {
        val addedBySchema = exportedColumnNames(version = 53) - exportedColumnNames(version = 52)
        assertTrue(
            "迁移加的列必须真的出现在 53 的 tutor_message 里（多出的列谁加的？）：$addedBySchema",
            addedBySchema.containsAll(migratedColumns().map { column -> column.column }),
        )
        assertTrue("tool_trace_json 必须在 53 里出现", "tool_trace_json" in addedBySchema)
    }

    @Test
    fun anOldRowWithoutATraceReadsBackAsNoTrace() {
        // 映射层：NULL 就是"没有痕迹"，不许崩、不许造安慰值（旧行当年确实没记过）。
        val entity = TutorMessageEntity(
            messageId = "tutor-message-assistant:old",
            conversationId = "tutor-conv:old",
            ordinal = 1,
            role = "ASSISTANT",
            bodyMarkdown = "旧的回复",
            status = "PERSISTED",
            logicalOperationId = "op-1",
            replyToMessageId = null,
            createdAtEpochMillis = 1_000,
            completedAtEpochMillis = 1_000,
            errorCode = null,
        )

        val record = entity.toRecord()

        assertNull(record.toolTraceJson)
    }

    // ---- 导出的 schema 读取 ----

    private fun migratedColumns(): List<MigratedColumn> = TUTOR_MESSAGE_ADDED_COLUMNS_52_53
        .map { statement ->
            val match = ADD_COLUMN_PATTERN.matchEntire(statement.trim().replace(Regex("\\s+"), " "))
            requireNotNull(match) { "迁移语句不是 ADD COLUMN：$statement" }
            MigratedColumn(
                table = match.groupValues[1],
                column = match.groupValues[2],
                affinity = match.groupValues[3].trim().substringBefore(' '),
            )
        }

    private fun exportedColumn(column: String): TraceSchemaField =
        exportedEntity(version = STUDY_DATABASE_VERSION).fields
            .single { field -> field.columnName == column }

    private fun exportedColumnNames(version: Int): Set<String> =
        exportedEntity(version = version).fields.mapTo(linkedSetOf()) { field -> field.columnName }

    private fun exportedEntity(version: Int): TraceSchemaEntity = exportedDatabase(version)
        .entities
        .single { entity -> entity.tableName == "tutor_message" }

    private fun exportedDatabase(version: Int): TraceSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<TraceSchemaFile>(schemaFile.readText()).database
    }

    private data class MigratedColumn(
        val table: String,
        val column: String,
        val affinity: String,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** 迁移只允许长成这一种形状：单条 `ADD COLUMN`（列定义随其后，允许带 NOT NULL / 默认值）。 */
        val ADD_COLUMN_PATTERN = Regex(
            "^ALTER TABLE `([a-z_]+)` ADD COLUMN `([a-z_]+)` (.+)$",
        )
    }
}

@Serializable
private data class TraceSchemaFile(
    @SerialName("database")
    val database: TraceSchemaDatabase,
)

@Serializable
private data class TraceSchemaDatabase(
    @SerialName("version")
    val version: Int,
    @SerialName("entities")
    val entities: List<TraceSchemaEntity>,
)

@Serializable
private data class TraceSchemaEntity(
    @SerialName("tableName")
    val tableName: String,
    @SerialName("fields")
    val fields: List<TraceSchemaField> = emptyList(),
)

@Serializable
private data class TraceSchemaField(
    @SerialName("columnName")
    val columnName: String,
    @SerialName("affinity")
    val affinity: String,
    /** Room 只在 NOT NULL 时写出这一项（缺省 = 可空）。 */
    @SerialName("notNull")
    val notNull: Boolean? = null,
    @SerialName("defaultValue")
    val defaultValue: String? = null,
)
