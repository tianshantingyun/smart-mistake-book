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
 * **阶段 4A 批 1 · L2 标题单源的 schema 契约**（`docs/research/2026-10-03-stage4a-plan.md` §3 批 1）。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v60 翻列**：`library_catalog` 视图在 59.json 里读 `unit.title`、在 60.json 里
 *    读 `revision.title`；另一张视图与**每一张表**的 DDL/索引逐字不变（视图重建不牵动表）；
 * 2. **迁移 DDL 与导出的 60.json 逐字一致**：`KERNEL_WAVE7_MIGRATION_59_60` 的
 *    CREATE VIEW 字面量必须等于 60.json 的 createSql（Room 打开库时按这段文本校验视图，
 *    差一个空格都会在真机升级时炸）；
 * 3. **迁移只重建视图**：DDL 只有 DROP + CREATE 两句，没有表级 DDL、没有数据搬运。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.libraryCatalogRebuildReadsRevisionTitleWithoutTouchingStoredRows`
 * 管"真库迁移后视图读新列、旧行还在"。
 */
class KernelWave7SchemaContractTest {

    @Test
    fun `library catalog title source flips only in 60`() {
        val v59 = view("library_catalog", 59).createSql
        val v60 = view("library_catalog", 60).createSql

        assertTrue("59 里必须还读 unit.title（否则这条迁移没有意义）", "unit.title" in v59)
        assertFalse("60 里不许再读 unit.title", "unit.title" in v60)
        assertTrue("60 里必须读 revision.title", "revision.title" in v60)
        assertEquals(
            "其余列一列不许少（只换标题来源）",
            v59.replace("unit.title", "revision.title"),
            v60,
        )
    }

    @Test
    fun `every table and the other view are byte identical between 59 and 60`() {
        val v59 = exportedDatabase(59)
        val v60 = exportedDatabase(60)

        assertEquals(
            "v60 相对 v59 不许有表级 DDL/索引变化",
            v59.entities.associate { it.tableName to (it.createSql to it.indices.map { index -> index.createSql }) },
            v60.entities.associate { it.tableName to (it.createSql to it.indices.map { index -> index.createSql }) },
        )
        assertEquals(
            "v60 相对 v59 不许动第二张视图",
            view("knowledge_question_lattice", 59).createSql,
            view("knowledge_question_lattice", 60).createSql,
        )
    }

    @Test
    fun `the migration is drop plus byte exact create of the exported view`() {
        val expected = view("library_catalog", 60).createSql
            .replace("${'$'}{VIEW_NAME}", "library_catalog")
        val ddl = LIBRARY_CATALOG_TITLE_SOURCE_DDL_59_60

        assertEquals("迁移只有 DROP + CREATE 两句", 2, ddl.size)
        assertTrue("第一句必须是 DROP VIEW", ddl[0].startsWith("DROP VIEW IF EXISTS"))
        assertEquals(
            "CREATE VIEW 必须与 60.json 的 createSql 逐字一致（Room 按文本校验视图）",
            expected,
            ddl[1],
        )
    }

    // ---- fixtures ----

    private fun view(viewName: String, version: Int): Wave7View =
        exportedDatabase(version).views.single { it.viewName == viewName }

    private fun exportedDatabase(version: Int): Wave7Database {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave7SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class Wave7SchemaFile(@SerialName("database") val database: Wave7Database)

@Serializable
private data class Wave7Database(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave7Entity> = emptyList(),
    @SerialName("views") val views: List<Wave7View> = emptyList(),
)

@Serializable
private data class Wave7Entity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave7Index> = emptyList(),
)

@Serializable
private data class Wave7Index(@SerialName("createSql") val createSql: String)

@Serializable
private data class Wave7View(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
