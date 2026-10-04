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
 * **阶段 3C 批 1 · KF-29 绑定抽样表的 schema 契约**。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v62 多一张表**：`binding_audit_sample` 在 61.json 里不存在、在 62.json 里存在；
 *    其余每一张表与两张视图的 DDL/索引逐字不变（纯新增迁移不牵动既有面）；
 * 2. **迁移 DDL 与导出的 62.json 逐字一致**：`KERNEL_WAVE9_MIGRATION_61_62` 的 CREATE TABLE
 *    必须等于 62.json 的 createSql（Room 打开库时按 schema 校验，差一个空格都会在真机升级时炸）；
 * 3. **列形状钉死**：列名/顺序/可空性逐位固定——少一列，判定（verdict/reviewed_at）或快照
 *    （binding_snapshot_json）就会静默丢失。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.bindingAuditSampleTableIsCreatedEmptyWithoutTouchingStoredRows`
 * 管"真库迁移后新表建出且为空、旧行还在"，`BindingAuditSampleInstrumentedTest` 管读写口不变量。
 */
class KernelWave9SchemaContractTest {

    @Test
    fun `the binding audit sample table appears only in 62`() {
        val v61 = exportedDatabase(61).entities.map { it.tableName }
        val v62 = exportedDatabase(62).entities.map { it.tableName }

        assertFalse(
            "61 里不该有绑定抽样表（否则这条迁移没有意义）",
            "binding_audit_sample" in v61,
        )
        assertTrue("62 里必须有绑定抽样表", "binding_audit_sample" in v62)
        assertEquals(
            "纯新增：62 相对 61 只多这一张表",
            v61.toSet() + "binding_audit_sample",
            v62.toSet(),
        )
    }

    @Test
    fun `every pre-existing table and both views are byte identical between 61 and 62`() {
        val v61 = exportedDatabase(61)
        val v62 = exportedDatabase(62)

        fun shapes(database: Wave9Database) = database.entities
            .filterNot { it.tableName == "binding_audit_sample" }
            .associate { it.tableName to (it.createSql to it.indices.map { index -> index.createSql }) }
        assertEquals("v62 相对 v61 不许动任何既有表/索引", shapes(v61), shapes(v62))
        assertEquals(
            "v62 相对 v61 不许动任何视图",
            v61.views.associate { it.viewName to it.createSql },
            v62.views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration is the byte exact create of the exported table`() {
        val expected = exportedDatabase(62).entities
            .single { it.tableName == "binding_audit_sample" }
            .createSql
            .replace("${'$'}{TABLE_NAME}", "binding_audit_sample")
        val ddl = BINDING_AUDIT_SAMPLE_DDL_61_62

        assertEquals("新增表只需要一句建表（不建索引：抽样表按配额封顶、行数很小）", 1, ddl.size)
        assertEquals(
            "CREATE TABLE 必须与 62.json 的 createSql 逐字一致（Room 按 schema 校验）",
            expected,
            ddl[0],
        )
    }

    @Test
    fun `the binding audit sample columns stay exactly the consumer contract`() {
        val table = exportedDatabase(62).entities
            .single { it.tableName == "binding_audit_sample" }
        val columns = COLUMN_PATTERN.findAll(table.createSql).map { it.groupValues[1] }.toList()

        assertEquals(
            listOf(
                "sample_id",
                "practice_unit_id",
                "binding_snapshot_json",
                "status",
                "verdict",
                "reviewed_at",
            ),
            columns,
        )
        assertTrue("ddl 必须由迁移逐字建出（主键在）", "PRIMARY KEY(`sample_id`)" in table.createSql)
    }

    // ---- fixtures ----

    private fun exportedDatabase(version: Int): Wave9Database {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave9SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val COLUMN_PATTERN = Regex("`([a-z0-9_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class Wave9SchemaFile(@SerialName("database") val database: Wave9Database)

@Serializable
private data class Wave9Database(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave9Entity> = emptyList(),
    @SerialName("views") val views: List<Wave9View> = emptyList(),
)

@Serializable
private data class Wave9Entity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave9Index> = emptyList(),
)

@Serializable
private data class Wave9Index(@SerialName("createSql") val createSql: String)

@Serializable
private data class Wave9View(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
