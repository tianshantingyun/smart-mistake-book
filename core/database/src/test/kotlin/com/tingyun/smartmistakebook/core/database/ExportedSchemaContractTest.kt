package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the exported Room schema set at the file level: every checked-in schema
 * must be a complete, non-placeholder export whose version matches its filename.
 * v16 is the only known exception because its identity hash was reconstructed
 * from DDL and is not Room-generated; see docs/current/implementation-status.md.
 */
class ExportedSchemaContractTest {

    @Test
    fun everyExportedSchemaIsCompleteAndVersioned() {
        val schemas = File("schemas")
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .toList()
        assertTrue("Expected exported Room schemas", schemas.isNotEmpty())

        schemas.forEach { file ->
            val version = file.nameWithoutExtension.toIntOrNull()
            assertTrue("Schema filename must be a version: ${file.name}", version != null)
            val bundle = json.decodeFromString<SchemaFile>(file.readText())
            val database = bundle.database
            assertEquals("Schema version must match filename", version, database.version)
            assertTrue(
                "identityHash must not be blank in ${file.name}",
                database.identityHash.isNotBlank(),
            )
            assertTrue(
                "identityHash must be a generated hex hash in ${file.name}",
                database.identityHash.matches(Regex("[0-9a-f]{32}")),
            )
            assertTrue(
                "schema must export at least one entity in ${file.name}",
                database.entities.isNotEmpty(),
            )
            database.entities.forEach { entity ->
                assertTrue(
                    "entity ${entity.tableName} in ${file.name} must export createSql",
                    entity.createSql.isNotBlank(),
                )
                // FTS4 virtual tables carry no physical primary key; Room
                // exports them without one (valid since the v32 FTS index).
                if (!entity.createSql.startsWith("CREATE VIRTUAL TABLE")) {
                    assertTrue(
                        "entity ${entity.tableName} in ${file.name} must declare a primary key",
                        entity.primaryKey.columnNames.isNotEmpty(),
                    )
                }
            }
            assertTrue(
                "schema must include the Room master table in ${file.name}",
                database.setupQueries.any { it.contains("room_master_table") },
            )
        }
    }

    @Test
    fun placeholderSchemaHashIsExplicitlyScopedToV16Only() {
        val schemas = File("schemas")
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .toList()
        val placeholderVersions = schemas
            .map { file ->
                val database = json.decodeFromString<SchemaFile>(file.readText()).database
                file.nameWithoutVersion to database.identityHash
            }
            .filter { (_, hash) -> hash == PLACEHOLDER_HASH }
            .map { (version, _) -> version }
        assertEquals(
            "Only v16 may carry a reconstructed placeholder identity hash",
            listOf(16),
            placeholderVersions,
        )
    }

    /**
     * 消灭的具体失败（批次 15 复核）：68.json 的 `identityHash` 与 `setupQueries` 都
     * 直接复制了 67 的值——新装库会把旧身份写进 `room_master_table`，迁移/身份校验拿到
     * 与 KSP 生成物不一致的哈希。两条必须同源，否则只看字段"非空且是 32 位 hex"看不出来。
     */
    @Test
    fun everySchemaMasterTableHashMatchesItsIdentityHash() {
        val schemas = File("schemas")
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .toList()
        schemas.forEach { file ->
            val database = json.decodeFromString<SchemaFile>(file.readText()).database
            val masterInsert = database.setupQueries.firstOrNull { query ->
                query.contains("INSERT", ignoreCase = true) &&
                    query.contains("room_master_table") &&
                    query.contains("identity_hash")
            }
            assertTrue(
                "${file.name} must insert the room_master_table identity hash",
                masterInsert != null,
            )
            assertTrue(
                "identityHash must match the room_master_table insert in ${file.name}: " +
                    masterInsert,
                masterInsert!!.contains("'${database.identityHash}'"),
            )
        }
    }

    private val File.nameWithoutVersion: Int
        get() = nameWithoutExtension.toInt()

    private companion object {
        const val PLACEHOLDER_HASH = "00000000000000000000000000000000"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class SchemaFile(
    @SerialName("database")
    val database: DatabaseFile,
)

@Serializable
private data class DatabaseFile(
    @SerialName("version")
    val version: Int,
    @SerialName("identityHash")
    val identityHash: String,
    @SerialName("entities")
    val entities: List<EntityFile>,
    @SerialName("setupQueries")
    val setupQueries: List<String>,
)

@Serializable
private data class EntityFile(
    @SerialName("tableName")
    val tableName: String,
    @SerialName("createSql")
    val createSql: String,
    @SerialName("primaryKey")
    val primaryKey: PrimaryKeyFile,
)

@Serializable
private data class PrimaryKeyFile(
    @SerialName("columnNames")
    val columnNames: List<String>,
)
