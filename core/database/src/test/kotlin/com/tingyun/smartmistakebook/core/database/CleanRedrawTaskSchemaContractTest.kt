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
 * **AUD-09（v65→66）净化作业台账的 schema 契约**。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v66 多一张表**：`clean_redraw_task` 在 65.json 里不存在、在 66.json 里存在；
 *    其余每一张表、每一条既有索引与两张视图的 DDL 逐字不变（纯新增迁移不牵动既有面）；
 * 2. **迁移 DDL 与导出的 66.json 逐字一致**：建表与两条索引的语句必须等于 66.json 的
 *    createSql（`${TABLE_NAME}` 占位符替换后）——Room 打开库时按 schema 校验，差一个空格
 *    都会在真机升级时炸；
 * 3. **列形状钉死**：作业身份 `task_id` 是 PK（不是 `revision_id`：同一修订容许不同源图/
 *    不同请求指纹的作业行）；`attempt_count` / `source_image_sha256` / `request_fingerprint`
 *    全部 NOT NULL——"这是第几次尝试""在给哪张图做作业"不许是未知；`last_failure_code`
 *    可空（成功时为空）。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.cleanRedrawTaskTableIsCreatedEmptyWithoutTouchingStoredRows`
 * 管"真库迁移后新表建出、既有行一行不动、再开一次不漂"。
 *
 * 注：`66.json` 是 Room 编译器（KSP）在构建时写进 `schemas/` 的导出物，**不手工维护**；
 * 它缺失时本用例会红，说明这次构建没有跑过 core:database 的 schema 导出。
 */
class CleanRedrawTaskSchemaContractTest {

    @Test
    fun `the clean redraw table appears only in 66`() {
        val v65 = exportedDatabase(65).entities.map { it.tableName }
        val v66 = exportedDatabase(66).entities.map { it.tableName }

        assertFalse("65 里不该有这张表（否则迁移没有意义）", TABLE in v65)
        assertTrue(
            "66 里必须有这张表（66.json 由构建期的 Room 导出生成——缺失说明 schema 没导出）",
            TABLE in v66,
        )
        assertEquals(
            "纯新增：66 相对 65 只多这一张表（不删、不改名）",
            (v65 + TABLE).toSet(),
            v66.toSet(),
        )
        assertEquals("表的数量只多一张", v65.size + 1, v66.size)
    }

    @Test
    fun `every other table index and both views are byte identical between 65 and 66`() {
        fun shapes(database: CleanRedrawSchemaDatabase) = database.entities
            .filterNot { it.tableName == TABLE }
            .associate { entity ->
                entity.tableName to (entity.createSql to entity.indices.map { it.createSql })
            }
        assertEquals(
            "66 相对 65 不许动任何既有表/索引",
            shapes(exportedDatabase(65)),
            shapes(exportedDatabase(66)),
        )
        assertEquals(
            "66 相对 65 不许动任何视图",
            exportedDatabase(65).views.associate { it.viewName to it.createSql },
            exportedDatabase(66).views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration creates exactly the exported table and indices`() {
        val exported = exportedTable(66)
        assertEquals(
            "迁移的建表语句必须与 66.json 的 createSql 逐字一致（Room 按 schema 校验）",
            exported.createSql.replace("\${TABLE_NAME}", TABLE),
            CLEAN_REDRAW_TASK_DDL_65_66,
        )
        assertEquals(
            "两条索引一条不少、逐字一致",
            exported.indices.map { it.createSql.replace("\${TABLE_NAME}", TABLE) },
            listOf(
                CLEAN_REDRAW_TASK_INDEX_FINGERPRINT_65_66,
                CLEAN_REDRAW_TASK_INDEX_STATUS_65_66,
            ),
        )
        assertTrue(
            "建表必须是纯新增（IF NOT EXISTS，不重建表、不动行）",
            CLEAN_REDRAW_TASK_DDL_65_66.startsWith("CREATE TABLE IF NOT EXISTS `$TABLE`"),
        )
        assertTrue(
            "索引也必须 IF NOT EXISTS（同一库重放迁移不炸）",
            listOf(
                CLEAN_REDRAW_TASK_INDEX_FINGERPRINT_65_66,
                CLEAN_REDRAW_TASK_INDEX_STATUS_65_66,
            ).all { it.startsWith("CREATE INDEX IF NOT EXISTS ") },
        )
    }

    @Test
    fun `the ledger columns pin identity status and failure tracking`() {
        val exported = exportedTable(66)
        assertEquals(
            "列必须与实体声明顺序一致（Room 导出的形状就是真库的形状）",
            listOf(
                "task_id",
                "revision_id",
                "source_asset_id",
                "source_image_sha256",
                "request_fingerprint",
                "subject_id",
                "status",
                "attempt_count",
                "last_failure_code",
                "created_at_epoch_millis",
                "updated_at_epoch_millis",
            ),
            columnShape(exported.createSql),
        )
        assertEquals(
            "作业身份是 task_id（四个身份列的合成摘要）——同一修订上的不同源图/不同请求是两行",
            listOf("task_id"),
            exported.primaryKey.columnNames,
        )
        val fields = exported.fields.associateBy { it.columnName }
        listOf(
            "source_image_sha256" to "TEXT",
            "request_fingerprint" to "TEXT",
            "attempt_count" to "INTEGER",
            "status" to "TEXT",
        ).forEach { (column, affinity) ->
            val field = fields.getValue(column)
            assertEquals("$column 的亲和性必须是 $affinity", affinity, field.affinity)
            assertTrue("$column 必须 NOT NULL", field.notNull)
        }
        assertFalse(
            "last_failure_code 必须可空：成功/待做的行没有失败码（NULL 才是诚实值）",
            fields.getValue("last_failure_code").notNull,
        )
    }

    // ---- fixtures ----

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(version: Int): CleanRedrawSchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == TABLE }

    private fun exportedDatabase(version: Int): CleanRedrawSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "$version.json" }
            ?: error(
                "schemas/$version.json 缺失：导出的 schema 由构建期 Room 导出生成" +
                    "（本用例在 core:database 的单测里跑，构建会先跑 KSP）",
            )
        return json.decodeFromString<CleanRedrawSchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val TABLE = "clean_redraw_task"

        val json = Json { ignoreUnknownKeys = true }

        /**
         * 从建表语句里抠列名。字符类必须收**数字**：本题的作业身份列就叫
         * `source_image_sha256`——只写 `[a-z_]+` 会把它整体吞掉，抠出来的列比实际少一条，
         * 于是断言在"列名都真在"的情况下反而假红（首轮门禁正是这样炸的）。
         */
        val COLUMN_PATTERN = Regex("`([A-Za-z0-9_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class CleanRedrawSchemaFile(
    @SerialName("database") val database: CleanRedrawSchemaDatabase,
)

@Serializable
private data class CleanRedrawSchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<CleanRedrawSchemaEntity> = emptyList(),
    @SerialName("views") val views: List<CleanRedrawSchemaView> = emptyList(),
)

@Serializable
private data class CleanRedrawSchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("fields") val fields: List<CleanRedrawSchemaField> = emptyList(),
    @SerialName("indices") val indices: List<CleanRedrawSchemaIndex> = emptyList(),
    @SerialName("primaryKey") val primaryKey: CleanRedrawSchemaPrimaryKey =
        CleanRedrawSchemaPrimaryKey(),
)

@Serializable
private data class CleanRedrawSchemaField(
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
    @SerialName("notNull") val notNull: Boolean = false,
)

@Serializable
private data class CleanRedrawSchemaIndex(@SerialName("createSql") val createSql: String)

@Serializable
private data class CleanRedrawSchemaPrimaryKey(
    @SerialName("columnNames") val columnNames: List<String> = emptyList(),
)

@Serializable
private data class CleanRedrawSchemaView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
