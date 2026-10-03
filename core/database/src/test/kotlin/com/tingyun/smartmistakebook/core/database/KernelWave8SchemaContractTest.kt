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
 * **阶段 4A 批 4 · L7 导出记录表的 schema 契约**（`docs/research/2026-10-03-stage4a-plan.md` §3 批 4）。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v61 多一张表**：`mistake_export_record` 在 60.json 里不存在、在 61.json 里存在；
 *    其余每一张表与两张视图的 DDL/索引逐字不变（纯新增迁移不牵动既有面）；
 * 2. **迁移 DDL 与导出的 61.json 逐字一致**：`KERNEL_WAVE8_MIGRATION_60_61` 的 CREATE TABLE
 *    必须等于 61.json 的 createSql（Room 打开库时按 schema 校验，差一个空格都会在真机升级时炸）；
 * 3. **列形状钉死**：列名/顺序/可空性逐位固定——记录表少一列，"导出成果"的重开钥匙
 *    （`input_sha256`/`pdf_sha256`/`page_count`）或失败原因就会静默丢失。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.mistakeExportRecordTableIsCreatedEmptyWithoutTouchingStoredRows`
 * 管"真库迁移后新表建出且为空、旧行还在"。
 */
class KernelWave8SchemaContractTest {

    @Test
    fun `the export record table appears only in 61`() {
        val v60 = exportedDatabase(60).entities.map { it.tableName }
        val v61 = exportedDatabase(61).entities.map { it.tableName }

        assertFalse(
            "60 里不该有导出记录表（否则这条迁移没有意义）",
            "mistake_export_record" in v60,
        )
        assertTrue("61 里必须有导出记录表", "mistake_export_record" in v61)
        assertEquals(
            "纯新增：61 相对 60 只多这一张表",
            v60.toSet() + "mistake_export_record",
            v61.toSet(),
        )
    }

    @Test
    fun `every pre-existing table and both views are byte identical between 60 and 61`() {
        val v60 = exportedDatabase(60)
        val v61 = exportedDatabase(61)

        fun shapes(database: Wave8Database) = database.entities
            .filterNot { it.tableName == "mistake_export_record" }
            .associate { it.tableName to (it.createSql to it.indices.map { index -> index.createSql }) }
        assertEquals("v61 相对 v60 不许动任何既有表/索引", shapes(v60), shapes(v61))
        assertEquals(
            "v61 相对 v60 不许动任何视图",
            v60.views.associate { it.viewName to it.createSql },
            v61.views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration is the byte exact create of the exported table`() {
        val expected = exportedDatabase(61).entities
            .single { it.tableName == "mistake_export_record" }
            .createSql
            .replace("${'$'}{TABLE_NAME}", "mistake_export_record")
        val ddl = MISTAKE_EXPORT_RECORD_DDL_60_61

        assertEquals("新增表只需要一句建表（不建索引：记录表按行数上限清理）", 1, ddl.size)
        assertEquals(
            "CREATE TABLE 必须与 61.json 的 createSql 逐字一致（Room 按 schema 校验）",
            expected,
            ddl[0],
        )
    }

    @Test
    fun `the export record columns stay exactly the consumer contract`() {
        val table = exportedDatabase(61).entities
            .single { it.tableName == "mistake_export_record" }
        val columns = COLUMN_PATTERN.findAll(table.createSql).map { it.groupValues[1] }.toList()

        assertEquals(
            listOf(
                "export_id",
                "kind",
                "display_name",
                "status",
                "input_sha256",
                "pdf_sha256",
                "page_count",
                "failure_message",
                "created_at_epoch_millis",
                "finished_at_epoch_millis",
            ),
            columns,
        )
        assertTrue("ddl 必须由迁移逐字建出（主键在）", "PRIMARY KEY(`export_id`)" in table.createSql)
    }

    // ---- fixtures ----

    private fun exportedDatabase(version: Int): Wave8Database {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave8SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val COLUMN_PATTERN = Regex("`([a-z0-9_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class Wave8SchemaFile(@SerialName("database") val database: Wave8Database)

@Serializable
private data class Wave8Database(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave8Entity> = emptyList(),
    @SerialName("views") val views: List<Wave8View> = emptyList(),
)

@Serializable
private data class Wave8Entity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave8Index> = emptyList(),
)

@Serializable
private data class Wave8Index(@SerialName("createSql") val createSql: String)

@Serializable
private data class Wave8View(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
