package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **内核 Wave 2 的 schema 契约**（roadmap W2-4 / KF-23）：`review_log` 的 `state` 列。
 *
 * 三层证据，都是 JVM 上就能炸的：
 * 1. **只在 v55 出现**：54.json 没有 `state`、55.json 有，且只多这一列；
 * 2. **迁移 DDL 与导出的 55.json 同形**（列名/类型/NOT NULL/DEFAULT 逐字）；
 * 3. **回填四分支**：无前条=New(0)、前条 AGAIN=Relearning(3)、同日=Learning(1)、
 *    跨日=Review(2)——SQL 形状逐条钉住（真库回填结果由仪器化用例覆盖）。
 */
class KernelWave2SchemaContractTest {

    @Test
    fun `review log gains only the state column in 55`() {
        val v54 = columnShape(exportedTable("review_log", 54).createSql)
        val v55 = columnShape(exportedTable("review_log", 55).createSql)

        assertTrue("54 里不该有 state（否则迁移没有意义）", "state" !in v54)
        assertTrue("55 里必须有 state", "state" in v55)
        assertEquals("v55 相对 v54 只多 state 一列", v54.toSet() + "state", v55.toSet())
    }

    @Test
    fun `the state column shape matches the exported schema byte for byte`() {
        val createSql = exportedTable("review_log", 55).createSql
        val alter = REVIEW_LOG_STATE_DDL_54_55.first()

        assertEquals(
            "迁移的 ALTER 必须与 55.json 的列定义同形（可空性/默认值不许漂）",
            "ALTER TABLE `review_log` ADD COLUMN `state` INTEGER NOT NULL DEFAULT 0",
            alter,
        )
        assertTrue(
            "55.json 里 state 的定义必须与迁移一致：${createSql.substringAfter("`state`").take(40)}",
            createSql.contains("`state` INTEGER NOT NULL DEFAULT 0"),
        )
    }

    @Test
    fun `the backfill derives all four states from existing columns only`() {
        val sql = REVIEW_LOG_STATE_DDL_54_55.single { it.contains("UPDATE") }

        assertTrue("无前条 → New(0)", sql.contains("IS NULL THEN 0"))
        assertTrue("前条 AGAIN(rating=1) → Relearning(3)", sql.contains("= 1 THEN 3"))
        assertTrue("同日（delta_t_days=0）→ Learning(1)", sql.contains("`delta_t_days` = 0 THEN 1"))
        assertTrue("其余跨日 → Review(2)", sql.contains("ELSE 2"))
        assertTrue(
            "回填只能读既有列（前条 rating / 时间戳 / 日差），不许编造",
            sql.contains("prev.`rating`") &&
                sql.contains("prev.`reviewed_at_utc`") &&
                sql.contains("prev.`review_log_id`"),
        )
    }

    // ---- fixtures ----

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(tableName: String, version: Int): Wave2SchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun exportedDatabase(version: Int): Wave2SchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave2SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** 只认真正的列定义（反引号列名 + 类型），不认 PRIMARY KEY / FOREIGN KEY 子句里的列名。 */
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class Wave2SchemaFile(@SerialName("database") val database: Wave2SchemaDatabase)

@Serializable
private data class Wave2SchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave2SchemaEntity>,
)

@Serializable
private data class Wave2SchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave2SchemaIndex> = emptyList(),
)

@Serializable
private data class Wave2SchemaIndex(
    @SerialName("createSql") val createSql: String,
)
