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
 * **KF-30（v63→64）分类接受置信度列的 schema 契约**。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v64 多一列**：`accepted_confidence` 在 63.json 里不存在、在 64.json 里存在；
 *    其余每一张表、每一条既有索引与两张视图的 DDL 逐字不变（纯新增迁移不牵动既有面）；
 * 2. **迁移 DDL 的列定义与导出的 64.json 逐字一致**：`ALTER TABLE ... ADD COLUMN` 里的
 *    列定义必须等于 64.json 的 createSql 里 `accepted_confidence` 那一段（Room 打开库时按
 *    schema 校验，差一个空格都会在真机升级时炸）；
 * 3. **列形状钉死**：可空 `REAL`、追加在行尾（SQLite 的 ADD COLUMN 就在行尾，与实体声明
 *    顺序一致）——`NULL` 是"未知"的载体，不可 NOT NULL。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.kf30AcceptedConfidenceColumnIsAddedLeavingLegacyRowsNullAndIdempotent`
 * 管"真库迁移后列建出、旧行该列为 NULL、再开一次不漂"。
 */
class ProblemClassificationAcceptedConfidenceSchemaContractTest {

    @Test
    fun `the accepted confidence column appears only in 64`() {
        val v63 = exportedTable(63)
        val v64 = exportedTable(64)

        assertFalse(
            "63 里不该有这个列（否则迁移没有意义）",
            "accepted_confidence" in columnShape(v63.createSql),
        )
        assertTrue("64 里必须有这个列", "accepted_confidence" in columnShape(v64.createSql))
        assertEquals(
            "纯新增：64 相对 63 只多这一列（追加在行尾 = SQLite ADD COLUMN 的位置）",
            columnShape(v63.createSql) + "accepted_confidence",
            columnShape(v64.createSql),
        )
    }

    @Test
    fun `every other table index and both views are byte identical between 63 and 64`() {
        fun shapes(database: AcceptedConfidenceDatabase) = database.entities
            .associate { entity ->
                // 只允许分类表在该列上不同；其余 DDL/索引必须逐字不变。
                val createSql = if (entity.tableName == TABLE) {
                    entity.createSql.replace(", `accepted_confidence` REAL", "")
                } else {
                    entity.createSql
                }
                entity.tableName to (createSql to entity.indices.map { it.createSql })
            }
        assertEquals(
            "64 相对 63 不许动任何表/既有索引（除分类表这一列）",
            shapes(exportedDatabase(63)),
            shapes(exportedDatabase(64)),
        )
        assertEquals(
            "64 相对 63 不许动任何视图",
            exportedDatabase(63).views.associate { it.viewName to it.createSql },
            exportedDatabase(64).views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration adds exactly the exported column definition`() {
        val exportedColumn = exportedTable(64).createSql
            .substringAfter("`accepted_confidence` ")
            .substringBefore(",")
            .trim()
        assertEquals("列类型必须是可空 REAL（无 NOT NULL、无 DEFAULT）", "REAL", exportedColumn)

        val ddl = PROBLEM_CLASSIFICATION_ACCEPTED_CONFIDENCE_DDL_63_64
        assertTrue(
            "ALTER TABLE 必须是纯加列（不重建表、不动行）：$ddl",
            ddl.startsWith("ALTER TABLE `$TABLE` ADD COLUMN "),
        )
        assertEquals(
            "迁移的列定义必须与 64.json 的导出列定义逐字一致（Room 按 schema 校验）",
            "`accepted_confidence` REAL",
            ddl.removePrefix("ALTER TABLE `$TABLE` ADD COLUMN "),
        )
        assertFalse("迁移不许带 DEFAULT（未知就留 NULL）", ddl.contains("DEFAULT", ignoreCase = true))
        assertFalse("迁移不许出现回填语句", ddl.contains("UPDATE", ignoreCase = true))
    }

    @Test
    fun `the accepted confidence field is nullable and appended last`() {
        val exported = exportedTable(64)
        val columns = columnShape(exported.createSql)
        assertEquals(
            "列必须在 accepted_at_epoch_millis 之后、且是最后一列（ADD COLUMN 的行尾位置）",
            "accepted_confidence",
            columns.last(),
        )
        assertTrue(
            "前一列是 accepted_at_epoch_millis",
            "accepted_at_epoch_millis" in columns.dropLast(1),
        )

        val field = exported.fields.single { it.columnName == "accepted_confidence" }
        assertEquals("acceptedConfidence", field.fieldPath)
        assertEquals("REAL", field.affinity)
        assertFalse("可空：NULL = 未知（升级前旧行 / 用户路径无模型分数）", field.notNull)
    }

    // ---- fixtures ----

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(version: Int): AcceptedConfidenceEntity =
        exportedDatabase(version).entities.single { it.tableName == TABLE }

    private fun exportedDatabase(version: Int): AcceptedConfidenceDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<AcceptedConfidenceSchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val TABLE = "problem_classification_binding"

        val json = Json { ignoreUnknownKeys = true }
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class AcceptedConfidenceSchemaFile(
    @SerialName("database") val database: AcceptedConfidenceDatabase,
)

@Serializable
private data class AcceptedConfidenceDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<AcceptedConfidenceEntity> = emptyList(),
    @SerialName("views") val views: List<AcceptedConfidenceView> = emptyList(),
)

@Serializable
private data class AcceptedConfidenceEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("fields") val fields: List<AcceptedConfidenceField> = emptyList(),
    @SerialName("indices") val indices: List<AcceptedConfidenceIndex> = emptyList(),
)

/** Room 导出的可空列不写 `notNull`（缺省即 false）。 */
@Serializable
private data class AcceptedConfidenceField(
    @SerialName("fieldPath") val fieldPath: String,
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
    @SerialName("notNull") val notNull: Boolean = false,
)

@Serializable
private data class AcceptedConfidenceIndex(@SerialName("createSql") val createSql: String)

@Serializable
private data class AcceptedConfidenceView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
