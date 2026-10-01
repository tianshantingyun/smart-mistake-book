package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **内核 Wave 3 的 schema 契约**（roadmap W3-1 + W3-2/E 判据）：`learner_knowledge_mastery_state`
 * 的六列（β-二项 s/f + 记忆卡）。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v56 出现**：55.json 没有这六列、56.json 有，且只多这六列；
 * 2. **迁移 DDL 与导出的 56.json 同形**（列名/类型/NOT NULL/DEFAULT 逐字）；
 * 3. **迁移不回填**：值由 `PROJECTOR` bump 触发的全量重放重建（W0-1 的归档先于重放），
 *    任何手工回填都会与重放结果分叉——DDL 列表里出现 UPDATE 即本测试红。
 */
class KernelWave3SchemaContractTest {

    @Test
    fun `mastery state gains only the six memory-card columns in 56`() {
        val v55 = columnShape(exportedTable(TABLE, 55).createSql)
        val v56 = columnShape(exportedTable(TABLE, 56).createSql)
        val added = listOf(
            "success_weight",
            "failure_weight",
            "memory_stability_days",
            "memory_difficulty",
            "last_attempt_at_epoch_millis",
            "last_attempt_study_day",
        )

        assertTrue("55 里不该有这些列（否则迁移没有意义）", added.none { it in v55 })
        assertTrue("56 里必须有这六列", added.all { it in v56 })
        assertEquals("v56 相对 v55 只多这六列", v55.toSet() + added, v56.toSet())
    }

    @Test
    fun `the migration DDL matches the exported 56 schema byte for byte`() {
        val createSql = exportedTable(TABLE, 56).createSql
        MASTERY_MEMORY_CARD_DDL_55_56.forEach { alter ->
            val column = alter.substringAfter("ADD COLUMN `").substringBefore("`")
            val definition = alter.substringAfter("ADD COLUMN `$column` ").trim()
            assertTrue(
                "56.json 里 $column 的定义必须与迁移一致：$definition",
                createSql.contains("`$column` $definition"),
            )
        }
    }

    @Test
    fun `the migration performs no backfill because full replay owns the values`() {
        assertTrue(
            "迁移不得回填（PROJECTOR bump 触发的全量重放负责重建投影值）",
            MASTERY_MEMORY_CARD_DDL_55_56.none { it.contains("UPDATE") },
        )
    }

    // ---- fixtures ----

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(tableName: String, version: Int): Wave3SchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun exportedDatabase(version: Int): Wave3SchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave3SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val TABLE = "learner_knowledge_mastery_state"

        val json = Json { ignoreUnknownKeys = true }

        /** 只认真正的列定义（反引号列名 + 类型），不认 PRIMARY KEY / FOREIGN KEY 子句里的列名。 */
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class Wave3SchemaFile(@SerialName("database") val database: Wave3SchemaDatabase)

@Serializable
private data class Wave3SchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave3SchemaEntity>,
)

@Serializable
private data class Wave3SchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave3SchemaIndex> = emptyList(),
)

@Serializable
private data class Wave3SchemaIndex(
    @SerialName("createSql") val createSql: String,
)
