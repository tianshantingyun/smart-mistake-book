package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **阶段 3B 步骤一 · D-M M1 fixture 系统退场的 schema 契约**
 * （`docs/research/2026-10-02-stage3b-plan.md` §3 步骤一 + 附录 A·M1）。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v58 消失**：`problem_memory_state` / `knowledge_mastery_state` 两张 legacy
 *    fixture 投影表在 57.json 里在、在 58.json 里不在，其余**每一张表的 DDL 与索引逐字不变**；
 * 2. **迁移就是两句 DROP**：不搬运（无 INSERT）、不回填（无 UPDATE）、不碰第三张表；
 * 3. **现役视图不再引用被删表**：`library_catalog` 与 `knowledge_question_lattice` 的
 *    当前 DDL（58.json 的 views）里不许出现这两个表名——它们曾是 v29→30 视图的连接对象，
 *    v35→36 起已改为真实投影表（历史迁移 SQL 不动，这里钉的是当前形态）。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.m1DropRemovesLegacyFixtureProjectionTablesAndKeepsRealRows`
 * 管"真库迁移后表没了、真实行还在"。
 */
class KernelWave5SchemaContractTest {

    @Test
    fun `legacy fixture projection tables are dropped only in 58`() {
        val v57 = exportedDatabase(57).entities.map { it.tableName }.toSet()
        val v58 = exportedDatabase(58).entities.map { it.tableName }.toSet()

        LEGACY_TABLES.forEach { table ->
            assertTrue("57 里必须有 $table（否则这条迁移没有意义）", table in v57)
            assertFalse("58 里不许再有 $table", table in v58)
        }
        assertEquals(
            "v58 相对 v57 只少这两张 legacy 表",
            v57 - LEGACY_TABLES,
            v58,
        )
    }

    @Test
    fun `every other table is byte identical between 57 and 58`() {
        val v57 = exportedDatabase(57).entities.associateBy { it.tableName }
        val v58 = exportedDatabase(58).entities.associateBy { it.tableName }

        (v57.keys - LEGACY_TABLES).forEach { table ->
            val before = requireNotNull(v57[table])
            val after = requireNotNull(v58[table])
            assertEquals("表 $table 的 DDL 必须逐字不变", before.createSql, after.createSql)
            assertEquals(
                "表 $table 的索引必须逐字不变",
                before.indices.map { it.createSql },
                after.indices.map { it.createSql },
            )
        }
    }

    @Test
    fun `the migration is exactly two drops with no copy and no backfill`() {
        assertEquals(
            "删表就是两句 DROP，不附带任何搬运（fixture 专表没有生产读者）",
            listOf("DROP TABLE `problem_memory_state`", "DROP TABLE `knowledge_mastery_state`"),
            DROP_LEGACY_FIXTURE_PROJECTION_TABLES_DDL_57_58,
        )
        val all = DROP_LEGACY_FIXTURE_PROJECTION_TABLES_DDL_57_58
        assertTrue(
            "迁移不得回填/搬运（没有 UPDATE / INSERT）",
            all.none { sql ->
                sql.trimStart().startsWith("UPDATE") || sql.trimStart().startsWith("INSERT")
            },
        )
        all.forEach { sql ->
            assertTrue(
                "每条语句都必须是删这两张表之一：$sql",
                LEGACY_TABLES.any { table -> sql == "DROP TABLE `$table`" },
            )
        }
    }

    @Test
    fun `current views no longer reference the dropped legacy tables`() {
        val views = exportedDatabase(58).views
        val viewSql = views.joinToString("\n") { it.createSql }
        LEGACY_TABLES.forEach { table ->
            // 前缀负向断言：`learner_problem_memory_state` 含子串 `problem_memory_state`，
            // 裸 `contains` 会把真实投影表误判成 legacy 表。
            assertFalse(
                "现役视图 DDL 不许引用 $table（v35→36 起已改读 learner_* 投影表）",
                Regex("(?<!learner_)" + Regex.escape(table)).containsMatchIn(viewSql),
            )
        }
        assertTrue(
            "library_catalog 视图必须还在",
            views.any { it.viewName == "library_catalog" },
        )
    }

    // ---- fixtures ----

    private fun exportedDatabase(version: Int): Wave5SchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave5SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        val LEGACY_TABLES = listOf("problem_memory_state", "knowledge_mastery_state")
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class Wave5SchemaFile(@SerialName("database") val database: Wave5SchemaDatabase)

@Serializable
private data class Wave5SchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave5SchemaEntity>,
    @SerialName("views") val views: List<Wave5SchemaView> = emptyList(),
)

@Serializable
private data class Wave5SchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave5SchemaIndex> = emptyList(),
)

@Serializable
private data class Wave5SchemaIndex(
    @SerialName("name") val name: String,
    @SerialName("createSql") val createSql: String,
)

@Serializable
private data class Wave5SchemaView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
