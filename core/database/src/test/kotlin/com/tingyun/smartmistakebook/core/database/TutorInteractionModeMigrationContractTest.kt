package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v52→53 的第二条迁移契约（D-Q9 的交互模式列）。
 *
 * 三层证据与 [TutorMessageToolTraceMigrationContractTest] 逐条对应，只是换了表：
 * 1. **迁移写的 DDL == 导出的 53.json 里的那一列**（列名、类型、NOT NULL、默认值）；
 * 2. **只加列、不重建表**（单条 `ALTER TABLE ... ADD COLUMN`）；
 * 3. **旧行读回 = 正常模式**：默认值 `'NORMAL'` 就是当年的事实（迁移前的会话只有正常模式），
 *    所以迁移不回填、也不留 NULL 这个第三态。
 */
class TutorInteractionModeMigrationContractTest {

    @Test
    fun theMigratedColumnIsTheExported53Column() {
        val migrated = migratedColumns().single()
        assertEquals("tutor_conversation", migrated.table)
        assertEquals("interaction_mode", migrated.column)

        val exported = exportedField(column = "interaction_mode")
        assertEquals("TEXT", exported.affinity)
        assertEquals(
            "NOT NULL 是「旧行读回一定有一个模式」的前提",
            true,
            exported.notNull,
        )
        assertEquals(
            "默认值就是当年的事实：迁移前的会话只有正常模式",
            "'NORMAL'",
            exported.defaultValue,
        )
    }

    @Test
    fun theMigrationOnlyAddsAColumnSoOldRowsSurvive() {
        val statements = TUTOR_CONVERSATION_ADDED_COLUMNS_52_53
        assertTrue("这一版必须至少加一列（交互模式）", statements.isNotEmpty())
        statements.forEach { statement ->
            val normalized = statement.trim().replace(Regex("\\s+"), " ")
            val match = ADD_COLUMN_PATTERN.matchEntire(normalized)
            assertTrue(
                "迁移语句必须是 tutor_conversation 上的单条 ADD COLUMN（非破坏）：$normalized",
                match != null,
            )
            assertEquals("tutor_conversation", match!!.groupValues[1])
        }
    }

    @Test
    fun everyColumnTheMigrationAddsAlsoAppearsIn53() {
        val addedBySchema = exportedColumnNames(version = 53) - exportedColumnNames(version = 52)

        assertTrue(
            "迁移加的列必须真的出现在 53 的 tutor_conversation 里（多出的列谁加的？）：$addedBySchema",
            addedBySchema.containsAll(migratedColumns().map { column -> column.column }),
        )
    }

    @Test
    fun theAreaDefaultsAndTheModeDefaultAreTwoDifferentThings() {
        // 会话区有自己的默认（AGENT），模式有自己的默认（NORMAL）：两者都在迁移里给旧行，
        // 但**语义不同**——区是"这条会话属于哪一栏"，模式是"它按哪种形态答复"。
        val area = exportedField(column = "conversation_area")
        assertEquals(
            "'AGENT'",
            area.defaultValue,
        )
        assertTrue(area.notNull == true)
    }

    // ---- 导出的 schema 读取 ----

    private fun migratedColumns(): List<MigratedColumn> = TUTOR_CONVERSATION_ADDED_COLUMNS_52_53
        .map { statement ->
            val match = ADD_COLUMN_PATTERN.matchEntire(statement.trim().replace(Regex("\\s+"), " "))
            requireNotNull(match) { "迁移语句不是 ADD COLUMN：$statement" }
            MigratedColumn(
                table = match.groupValues[1],
                column = match.groupValues[2],
            )
        }

    private fun exportedField(column: String): ModeSchemaField =
        exportedEntity(version = STUDY_DATABASE_VERSION).fields
            .single { field -> field.columnName == column }

    private fun exportedColumnNames(version: Int): Set<String> =
        exportedEntity(version = version).fields.mapTo(linkedSetOf()) { field -> field.columnName }

    private fun exportedEntity(version: Int): ModeSchemaEntity = exportedDatabase(version)
        .entities
        .single { entity -> entity.tableName == "tutor_conversation" }

    private fun exportedDatabase(version: Int): ModeSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<ModeSchemaFile>(schemaFile.readText()).database
    }

    private data class MigratedColumn(
        val table: String,
        val column: String,
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
private data class ModeSchemaFile(
    @SerialName("database")
    val database: ModeSchemaDatabase,
)

@Serializable
private data class ModeSchemaDatabase(
    @SerialName("version")
    val version: Int,
    @SerialName("entities")
    val entities: List<ModeSchemaEntity>,
)

@Serializable
private data class ModeSchemaEntity(
    @SerialName("tableName")
    val tableName: String,
    @SerialName("fields")
    val fields: List<ModeSchemaField> = emptyList(),
)

@Serializable
private data class ModeSchemaField(
    @SerialName("columnName")
    val columnName: String,
    @SerialName("affinity")
    val affinity: String,
    @SerialName("notNull")
    val notNull: Boolean? = null,
    @SerialName("defaultValue")
    val defaultValue: String? = null,
)
