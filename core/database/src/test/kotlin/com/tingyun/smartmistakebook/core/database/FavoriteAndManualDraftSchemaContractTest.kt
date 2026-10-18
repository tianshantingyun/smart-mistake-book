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
 * **C11 收藏 + A8 手动建题（schema 半边）v66→67 的 schema 契约**。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **每处变化只在 v67 出现，且变化面极小**：`error_book_entry` 只多一列 `is_favorite`
 *    （其它列/索引/外键逐字不变）；`problem_draft` 只把 `source_asset_id` 的 NOT NULL 拿掉
 *    （列序/类型/主键/外键/两条索引逐字不变）；`library_catalog` 视图只多一行收藏列；
 *    其余每一张表、每一条既有索引与第二张视图的 DDL 逐字不变。
 * 2. **迁移 DDL 与导出的 67.json 逐字一致**：加列语句等于 JSON 里该列声明；
 *    `problem_draft` 重建的建表语句（临时表名换回真名后）与 JSON 的 createSql 逐字相同；
 *    视图 CREATE 与 JSON 的 createSql（`${VIEW_NAME}` 替换后）逐字相同——Room 打开库时按
 *    schema 校验，差一个空格都会在真机升级时炸。
 * 3. **语句形状钉死**：加列是 `ADD COLUMN`（纯追加，旧行读回 DEFAULT 0 = 未收藏）；
 *    重建是"建新表 → 逐列搬运 → DROP 旧表 → RENAME → 重建两条索引"（SQLite 没有
 *    改列可空性的 ALTER），搬运里没有 WHERE（一行不丢）、没有编造的新列值。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest`（矩阵 + v66 带数据用例）管"真库迁移后旧行原样、
 * 新列建出、视图读得到收藏、再开一次不漂"。
 *
 * 注：`67.json` 是 Room 编译器（KSP）在构建时写进 `schemas/` 的导出物，**不手工维护**；
 * 它缺失时本用例会红，说明这次构建没有跑过 core:database 的 schema 导出。
 */
class FavoriteAndManualDraftSchemaContractTest {

    @Test
    fun `the favorite column is appended to error book entry only in 67`() {
        val v66 = table("error_book_entry", 66).createSql
        val v67 = table("error_book_entry", 67).createSql

        assertFalse("66 里不该有收藏列（否则迁移没有意义）", "is_favorite" in v66)
        assertTrue("67 里必须有收藏列", "is_favorite" in v67)
        assertEquals(
            "只多一列：把追加的列声明拿掉必须逐字回到 66 的建表语句",
            v66,
            v67.replace("$FAVORITE_COLUMN, ", ""),
        )

        val field = table("error_book_entry", 67).fields
            .single { it.columnName == "is_favorite" }
        assertEquals("收藏是 0/1 的 INTEGER", "INTEGER", field.affinity)
        assertTrue("收藏必须有值（NULL 不是「未收藏」的诚实表示）", field.notNull)
        assertEquals(
            "默认 0 = 未收藏（旧库升级后全员未收藏，不回填编造的收藏）",
            "0",
            field.defaultValue,
        )
        assertEquals(
            "加列语句必须逐字追加这一列（Room 按 schema 校验列的四个属性）",
            "ALTER TABLE `error_book_entry` ADD COLUMN $FAVORITE_COLUMN",
            ERROR_BOOK_ENTRY_FAVORITE_DDL_66_67,
        )
        assertTrue(
            "纯追加：必须是 ADD COLUMN，不许重建表/动行",
            ERROR_BOOK_ENTRY_FAVORITE_DDL_66_67.startsWith(
                "ALTER TABLE `error_book_entry` ADD COLUMN ",
            ),
        )
    }

    @Test
    fun `the problem draft source asset becomes nullable only in 67`() {
        val v66 = table("problem_draft", 66)
        val v67 = table("problem_draft", 67)

        assertTrue("66 里 source_asset_id 必须还是 NOT NULL", "`source_asset_id` TEXT NOT NULL" in v66.createSql)
        assertFalse(
            "67 里 source_asset_id 必须可空（A8 手动草稿没有照片）",
            "`source_asset_id` TEXT NOT NULL" in v67.createSql,
        )
        assertTrue("67 里 source_asset_id 仍是 TEXT", "`source_asset_id` TEXT," in v67.createSql)
        assertEquals(
            "只有 source_asset_id 的可空性变了：66 的建表语句把 NOT NULL 补回去就是 67",
            v66.createSql,
            v67.createSql.replace("`source_asset_id` TEXT,", "`source_asset_id` TEXT NOT NULL,"),
        )
        val field = v67.fields.single { it.columnName == "source_asset_id" }
        assertFalse("JSON 的字段声明也必须是可空", field.notNull)
        assertEquals(
            "两条索引逐字不变（可空不牵动索引）",
            v66.indices.map { it.createSql.replace("\${TABLE_NAME}", "problem_draft") },
            v67.indices.map { it.createSql.replace("\${TABLE_NAME}", "problem_draft") },
        )

        val create = PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67.single { it.startsWith("CREATE TABLE") }
        assertEquals(
            "重建表的建表语句（临时名换回真名后）必须与 67.json 逐字一致",
            v67.createSql.replace("\${TABLE_NAME}", "problem_draft"),
            create.replace(PROBLEM_DRAFT_TEMP_TABLE, "problem_draft"),
        )
        assertEquals("重建共六句：建表/搬运/删旧/改名/两条索引", 6, PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67.size)
        assertTrue(
            "搬运必须逐列 SELECT（没有 WHERE：一行不丢、不回填）",
            PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67[1].startsWith("INSERT INTO `$PROBLEM_DRAFT_TEMP_TABLE`") &&
                "SELECT `draft_id`, `source_asset_id`" in PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67[1] &&
                "WHERE" !in PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67[1],
        )
        assertEquals(
            "第三句必须是 DROP 旧表",
            "DROP TABLE `problem_draft`",
            PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67[2],
        )
        assertEquals(
            "第四句必须是改名为真表名",
            "ALTER TABLE `$PROBLEM_DRAFT_TEMP_TABLE` RENAME TO `problem_draft`",
            PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67[3],
        )
        assertTrue(
            "两条索引必须 IF NOT EXISTS（重放迁移不炸）",
            PROBLEM_DRAFT_NULLABLE_SOURCE_DDL_66_67.drop(4).all {
                it.startsWith("CREATE INDEX IF NOT EXISTS ")
            },
        )
    }

    @Test
    fun `the library catalog view gains exactly the favorite column in 67`() {
        val v66 = view("library_catalog", 66).createSql
        val v67 = view("library_catalog", 67).createSql

        assertFalse("66 的视图里不该有收藏列", "is_favorite" in v66)
        assertEquals(
            "只多一行收藏列：删掉那一行必须逐字回到 66 的视图",
            v66,
            v67.replace("            entry.is_favorite AS is_favorite,\n", ""),
        )

        assertEquals("迁移只有 DROP + CREATE 两句", 2, LIBRARY_CATALOG_FAVORITE_VIEW_DDL_66_67.size)
        assertEquals(
            "第一句必须是 DROP VIEW",
            "DROP VIEW IF EXISTS `library_catalog`",
            LIBRARY_CATALOG_FAVORITE_VIEW_DDL_66_67[0],
        )
        assertEquals(
            "CREATE VIEW 必须与 67.json 的 createSql 逐字一致（Room 按文本校验视图）",
            v67.replace("\${VIEW_NAME}", "library_catalog"),
            LIBRARY_CATALOG_FAVORITE_VIEW_DDL_66_67[1],
        )
    }

    @Test
    fun `every other table and the other view are byte identical between 66 and 67`() {
        fun shapes(version: Int) = exportedDatabase(version).entities
            .filterNot { it.tableName in CHANGED_TABLES }
            .associate { entity ->
                entity.tableName to (entity.createSql to entity.indices.map { index -> index.createSql })
            }
        assertEquals(
            "67 相对 66 不许动任何其它表/索引",
            shapes(66),
            shapes(67),
        )
        assertEquals(
            "67 相对 66 不许动第二张视图",
            view("knowledge_question_lattice", 66).createSql,
            view("knowledge_question_lattice", 67).createSql,
        )
        assertTrue(
            "两张受影响的表都必须还在（v67 的变化面就是这三处）",
            CHANGED_TABLES.all { it in exportedDatabase(67).entities.map { entity -> entity.tableName } },
        )
    }

    // ---- fixtures ----

    private fun table(tableName: String, version: Int): Favorite67Entity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun view(viewName: String, version: Int): Favorite67View =
        exportedDatabase(version).views.single { it.viewName == viewName }

    private fun exportedDatabase(version: Int): Favorite67Database {
        val schemaFile = File("schemas")
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "$version.json" }
            ?: error(
                "schemas/$version.json 缺失：导出的 schema 由构建期 Room 导出生成" +
                    "（本用例在 core:database 的单测里跑，构建会先跑 KSP）",
            )
        return json.decodeFromString<Favorite67File>(schemaFile.readText()).database
    }

    private companion object {
        const val FAVORITE_COLUMN = "`is_favorite` INTEGER NOT NULL DEFAULT 0"
        const val PROBLEM_DRAFT_TEMP_TABLE = "problem_draft_v67"
        val CHANGED_TABLES = setOf("error_book_entry", "problem_draft")

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class Favorite67File(@SerialName("database") val database: Favorite67Database)

@Serializable
private data class Favorite67Database(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Favorite67Entity> = emptyList(),
    @SerialName("views") val views: List<Favorite67View> = emptyList(),
)

@Serializable
private data class Favorite67Entity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("fields") val fields: List<Favorite67Field> = emptyList(),
    @SerialName("indices") val indices: List<Favorite67Index> = emptyList(),
)

@Serializable
private data class Favorite67Field(
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
    @SerialName("notNull") val notNull: Boolean = false,
    @SerialName("defaultValue") val defaultValue: String? = null,
)

@Serializable
private data class Favorite67Index(@SerialName("createSql") val createSql: String)

@Serializable
private data class Favorite67View(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
