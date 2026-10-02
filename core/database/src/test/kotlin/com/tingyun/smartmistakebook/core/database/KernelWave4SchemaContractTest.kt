package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **阶段 3B 步骤一 · D-M M5 死面删除的 schema 契约**（`docs/research/2026-10-02-stage3b-plan.md` §3 步骤一）。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v57 消失**：`projection_consumption` 整表、`learner_problem_memory_state` 的两列
 *    （`last_reviewed_epoch_day` / `last_attempt_id`）在 56.json 里在、在 57.json 里不在，
 *    其余**每一张表的 DDL 与索引逐字不变**（"只删死面，别的一个字都不动"）；
 * 2. **迁移 DDL 与导出的 57.json 同形**：重建后的列名/顺序与 57.json 逐位一致，
 *    两条外键与两条索引逐字重建；
 * 3. **非破坏**：`INSERT..SELECT` 逐列搬运**全部存活列**，且迁移体里不出现 `UPDATE`
 *    （没有回填），也不出现两个被删列名（不把死值搬回来）。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest` 管"真库迁移后结构与行都还在"（含 57 的带数据用例）。
 */
class KernelWave4SchemaContractTest {

    @Test
    fun `projection consumption table is dropped only in 57`() {
        val v56 = exportedDatabase(56).entities.map { it.tableName }.toSet()
        val v57 = exportedDatabase(57).entities.map { it.tableName }.toSet()

        assertTrue("56 里必须有 projection_consumption（否则这条迁移没有意义）", CONSUMPTION in v56)
        assertTrue("57 里不许再有 projection_consumption", CONSUMPTION !in v57)
        assertEquals(
            "v57 相对 v56 只少 projection_consumption 一张表",
            v56 - CONSUMPTION,
            v57,
        )
    }

    @Test
    fun `memory state loses exactly the two dead columns in 57`() {
        val v56 = columnShape(exportedTable(MEMORY, 56).createSql)
        val v57 = columnShape(exportedTable(MEMORY, 57).createSql)

        DEAD_COLUMNS.forEach { column ->
            assertTrue("56 里必须有 $column（否则这条迁移没有意义）", column in v56)
            assertTrue("57 里不许再有 $column", column !in v57)
        }
        assertEquals(
            "v57 相对 v56 只少这两列（顺序也逐位保留）",
            v56.filterNot { it in DEAD_COLUMNS },
            v57,
        )
        assertEquals(
            "两条索引必须逐字保留（差集删除与视图连接都按它们走）",
            exportedTable(MEMORY, 56).indices.map { it.createSql },
            exportedTable(MEMORY, 57).indices.map { it.createSql },
        )
    }

    @Test
    fun `every other table is byte identical between 56 and 57`() {
        val v56 = exportedDatabase(56).entities.associateBy { it.tableName }
        val v57 = exportedDatabase(57).entities.associateBy { it.tableName }

        (v56.keys - CONSUMPTION - MEMORY).forEach { table ->
            val before = requireNotNull(v56[table])
            val after = requireNotNull(v57[table])
            assertEquals("表 $table 的 DDL 必须逐字不变", before.createSql, after.createSql)
            assertEquals(
                "表 $table 的索引必须逐字不变",
                before.indices.map { it.createSql },
                after.indices.map { it.createSql },
            )
        }
    }

    @Test
    fun `the rebuild DDL matches the exported 57 schema and copies every surviving column`() {
        val v57 = columnShape(exportedTable(MEMORY, 57).createSql)
        val rebuild = DROP_DEAD_MEMORY_COLUMNS_DDL_56_57

        val create = rebuild.single { it.startsWith("CREATE TABLE") }
        assertEquals(
            "重建表的列名/顺序必须与 57.json 逐位一致",
            v57,
            columnShape(create.replace("${MEMORY}_v57", MEMORY)),
        )
        val insertSelect = rebuild.single { it.startsWith("INSERT INTO") }
        val selectList = insertSelect.substringAfter("SELECT").substringBefore("FROM")
            .replace("`", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals("INSERT..SELECT 必须逐列搬运新表的每一列（非破坏）", v57, selectList)

        val v57Sql = exportedTable(MEMORY, 57).createSql
        assertTrue(
            "两条外键必须在重建表里逐字保留",
            v57Sql.contains("REFERENCES `learner_projection_snapshot`(`projection_name`, `learner_id`)") &&
                create.contains("REFERENCES `learner_projection_snapshot`(`projection_name`, `learner_id`)") &&
                v57Sql.contains("REFERENCES `practice_unit`(`practice_unit_id`)") &&
                create.contains("REFERENCES `practice_unit`(`practice_unit_id`)"),
        )
        assertTrue("旧表必须先 DROP 再 RENAME", rebuild.contains("DROP TABLE `$MEMORY`"))
        assertTrue(
            "重建后必须换回原表名",
            rebuild.contains("ALTER TABLE `${MEMORY}_v57` RENAME TO `$MEMORY`"),
        )
        assertTrue(
            "两条索引必须重建（名字与 57.json 一致）",
            exportedTable(MEMORY, 57).indices.all { index ->
                rebuild.any { it.contains(index.name) }
            },
        )
    }

    @Test
    fun `the migration never backfills and never touches the dropped columns`() {
        val all = DROP_PROJECTION_CONSUMPTION_DDL_56_57 + DROP_DEAD_MEMORY_COLUMNS_DDL_56_57

        assertEquals(
            "删表就是一句 DROP，不附带任何搬运（表里没有读者要的值）",
            listOf("DROP TABLE `$CONSUMPTION`"),
            DROP_PROJECTION_CONSUMPTION_DDL_56_57,
        )
        assertTrue(
            "迁移不得回填（没有 UPDATE 语句；外键子句的 ON UPDATE NO ACTION 不算）",
            all.none { it.trimStart().startsWith("UPDATE") },
        )
        assertTrue(
            "被删的两列不许再出现在迁移 SQL 里（死值不搬回来）",
            all.none { sql -> DEAD_COLUMNS.any { it in sql } },
        )
    }

    // ---- fixtures ----

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(tableName: String, version: Int): Wave4SchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun exportedDatabase(version: Int): Wave4SchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave4SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val CONSUMPTION = "projection_consumption"
        const val MEMORY = "learner_problem_memory_state"
        val DEAD_COLUMNS = listOf("last_reviewed_epoch_day", "last_attempt_id")

        val json = Json { ignoreUnknownKeys = true }

        /** 只认真正的列定义（反引号列名 + 类型），不认 PRIMARY KEY / FOREIGN KEY 子句里的列名。 */
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class Wave4SchemaFile(@SerialName("database") val database: Wave4SchemaDatabase)

@Serializable
private data class Wave4SchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave4SchemaEntity>,
)

@Serializable
private data class Wave4SchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave4SchemaIndex> = emptyList(),
)

@Serializable
private data class Wave4SchemaIndex(
    @SerialName("name") val name: String,
    @SerialName("createSql") val createSql: String,
)
