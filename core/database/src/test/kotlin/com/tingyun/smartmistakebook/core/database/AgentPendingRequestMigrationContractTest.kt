package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 新增表的**迁移 DDL 与导出的 52.json 逐字一致**（插眼 5 的 `agent_pending_request`）。
 *
 * 为什么这是一条单测而不是只靠仪器测试：DDL 与 Room 生成的不一致，只在**迁移后的真库**上
 * 才炸（Room 会校验表结构并抛异常），而真库要设备。这条单测在 JVM 上就能炸——把"手写 DDL
 * 与导出漂开"这个最常见的迁移事故挡在机器门里，而不是留到真机冒烟。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致"，`FullMigrationMatrixInstrumentedTest`
 * 管"迁移出来的库与 schema 文件结构一致"（后者还覆盖索引、列序、外键落地）。
 */
class AgentPendingRequestMigrationContractTest {
    @Test
    fun theNewTableDdlIsTheExportedSchemaDdlFor52() {
        val entity = exportedEntity("agent_pending_request")

        assertEquals(
            normalize(AGENT_PENDING_REQUEST_DDL),
            exportedSql(entity.createSql),
        )
    }

    @Test
    fun everyMigratedIndexMatchesItsExportedCounterpart() {
        val entity = exportedEntity("agent_pending_request")
        val exported = entity.indices.associate { index ->
            exportedSql(index.createSql) to index.name
        }

        assertEquals(3, AGENT_PENDING_REQUEST_INDEX_DDL.size)
        AGENT_PENDING_REQUEST_INDEX_DDL.forEach { ddl ->
            assertTrue(
                "Migrated index is not in the exported 52.json: $ddl",
                normalize(ddl) in exported,
            )
        }
        assertEquals(
            "Every exported index must be created by the migration",
            exported.keys,
            AGENT_PENDING_REQUEST_INDEX_DDL.map(::normalize).toSet(),
        )
    }

    /** 迁移必须写下外键本身：Room 迁移后的结构校验会看它，而 CASCADE 是"会话删了卡也走"的语义。 */
    @Test
    fun theMigratedTableKeepsTheConversationForeignKeyWithCascade() {
        assertTrue(
            normalize(AGENT_PENDING_REQUEST_DDL).endsWith(
                "PRIMARY KEY(`request_id`), FOREIGN KEY(`conversation_id`) " +
                    "REFERENCES `tutor_conversation`(`conversation_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            ),
        )
    }

    private fun exportedEntity(tableName: String): PendingRequestSchemaEntity =
        exportedDatabase().entities.single { entity -> entity.tableName == tableName }

    private fun exportedDatabase(): PendingRequestSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$STUDY_DATABASE_VERSION.json" }
        return json.decodeFromString<PendingRequestSchemaFile>(schemaFile.readText()).database
    }

    /**
     * 导出的建表语句用 `${TABLE_NAME}` 占位（Room 3 的形状）；迁移写的是真表名，所以比对前
     * 把占位符换成迁移会写的那个名字——两边比的是**同一条语句**。
     */
    private fun exportedSql(createSql: String): String =
        normalize(createSql.replace("\${TABLE_NAME}", "agent_pending_request"))

    private fun normalize(sql: String): String = sql.trim().replace(Regex("\\s+"), " ")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class PendingRequestSchemaFile(
    @SerialName("database")
    val database: PendingRequestSchemaDatabase,
)

@Serializable
private data class PendingRequestSchemaDatabase(
    @SerialName("version")
    val version: Int,
    @SerialName("entities")
    val entities: List<PendingRequestSchemaEntity>,
)

@Serializable
private data class PendingRequestSchemaEntity(
    @SerialName("tableName")
    val tableName: String,
    @SerialName("createSql")
    val createSql: String,
    @SerialName("indices")
    val indices: List<PendingRequestSchemaIndex> = emptyList(),
)

@Serializable
private data class PendingRequestSchemaIndex(
    @SerialName("name")
    val name: String,
    @SerialName("createSql")
    val createSql: String,
)
