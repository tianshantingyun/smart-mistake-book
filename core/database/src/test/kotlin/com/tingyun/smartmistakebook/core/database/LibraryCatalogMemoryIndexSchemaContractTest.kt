package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **阶段 3C 后半批 2 · S18（v62→63）目录记忆态连接索引的 schema 契约**。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v63 多一条索引**：该索引在 62.json 里不存在、在 63.json 里存在；其余每一张表、
 *    每一条既有索引与两张视图的 DDL 逐字不变（纯新增迁移不牵动既有面）；
 * 2. **迁移 DDL 与导出的 63.json 逐字一致**：`LIBRARY_CATALOG_MEMORY_INDEX_MIGRATION_62_63`
 *    的 CREATE INDEX 必须等于 63.json 的 createSql（Room 打开库时按 schema 校验，差一个
 *    空格都会在真机升级时炸）；
 * 3. **索引列序钉死**：`(projection_name, practice_unit_id, learner_id)`——这是让
 *    `library_catalog` 的记忆态连接两个等值约束都可用、且覆盖 `learner_id` 的形态；
 *    列序变化会静默退回"只吃 projection_name 前缀"的坏计划（量化报告里的前后对比）。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.libraryCatalogMemoryIndexIsCreatedWithoutTouchingStoredRows`
 * 管"真库迁移后索引建出、旧行还在"，`LibraryCatalogScalePerformanceInstrumentedTest`
 * 管读路径的 EQP 结构与预算。
 */
class LibraryCatalogMemoryIndexSchemaContractTest {

    @Test
    fun `the memory index appears only in 63`() {
        val v62 = exportedDatabase(62)
        val v63 = exportedDatabase(63)

        assertTrue("v62 不该有这条索引（否则迁移没有意义）", indexNames(v62).none { MEMORY_INDEX == it })
        assertTrue("v63 必须有这条索引", indexNames(v63).any { MEMORY_INDEX == it })
        assertEquals(
            "纯新增：v63 相对 v62 只多这一条索引",
            (indexNames(v62) + MEMORY_INDEX).sorted(),
            indexNames(v63),
        )
    }

    @Test
    fun `every pre-existing table index and both views are identical between 62 and 63`() {
        val v62 = exportedDatabase(62)
        val v63 = exportedDatabase(63)

        // 把新增索引过滤掉后再比：它只该出现在 v63 的 learner_problem_memory_state 上，
        // 其余每一张表/索引/视图的 DDL 必须逐字不变。
        fun shapes(database: MemoryIndexSchemaDatabase) = database.entities
            .associate { entity ->
                entity.tableName to (
                    entity.createSql to entity.indices
                        .map { index -> index.createSql }
                        .filterNot { MEMORY_INDEX in it }
                    )
            }
        assertEquals("v63 相对 v62 不许动任何表/既有索引", shapes(v62), shapes(v63))
        assertEquals(
            "v63 相对 v62 不许动任何视图",
            v62.views.associate { it.viewName to it.createSql },
            v63.views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration is the byte exact create of the exported index`() {
        val expected = exportedDatabase(63)
            .entities.single { it.tableName == "learner_problem_memory_state" }
            .indices.single { MEMORY_INDEX in it.createSql }
            .createSql
            .replace("${'$'}{TABLE_NAME}", "learner_problem_memory_state")

        assertEquals(
            "CREATE INDEX 必须与 63.json 的 createSql 逐字一致（Room 按 schema 校验）",
            expected,
            LIBRARY_CATALOG_MEMORY_INDEX_DDL_62_63,
        )
    }

    @Test
    fun `the memory index columns stay exactly the join contract`() {
        val createSql = exportedDatabase(63)
            .entities.single { it.tableName == "learner_problem_memory_state" }
            .indices.single { MEMORY_INDEX in it.createSql }
            .createSql

        // Index 的 createSql 形如 `... ON \`${TABLE_NAME}\` (\`a\`, \`b\`)`：取 ON 之后括号里的列。
        val columnSection = createSql.substringAfter("ON `${'$'}{TABLE_NAME}` (")
        assertEquals(
            "列序 = (projection_name, practice_unit_id, learner_id)：前两列吃视图连接的两个" +
                "等值约束，第三列让索引覆盖 learner_id",
            listOf("projection_name", "practice_unit_id", "learner_id"),
            Regex("`([a-z0-9_]+)`").findAll(columnSection).map { it.groupValues[1] }.toList(),
        )
    }

    private fun indexNames(database: MemoryIndexSchemaDatabase): List<String> = database.entities
        .flatMap { entity -> entity.indices.map { index -> indexNameOf(index.createSql) } }
        .sorted()

    private fun indexNameOf(createSql: String): String =
        requireNotNull(INDEX_NAME_PATTERN.find(createSql)) {
            "索引 createSql 里找不到名字：$createSql"
        }.groupValues[1]

    private fun exportedDatabase(version: Int): MemoryIndexSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<MemoryIndexSchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val MEMORY_INDEX =
            "index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id"
        val json = Json { ignoreUnknownKeys = true }
        val INDEX_NAME_PATTERN = Regex("CREATE (?:UNIQUE )?INDEX IF NOT EXISTS `([^`]+)`")
    }
}

@Serializable
private data class MemoryIndexSchemaFile(@SerialName("database") val database: MemoryIndexSchemaDatabase)

@Serializable
private data class MemoryIndexSchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<MemoryIndexSchemaEntity> = emptyList(),
    @SerialName("views") val views: List<MemoryIndexSchemaView> = emptyList(),
)

@Serializable
private data class MemoryIndexSchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<MemoryIndexSchemaIndex> = emptyList(),
)

@Serializable
private data class MemoryIndexSchemaIndex(@SerialName("createSql") val createSql: String)

@Serializable
private data class MemoryIndexSchemaView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
