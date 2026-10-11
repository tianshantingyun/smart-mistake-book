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
 * **K5-B（v64→65）分类候选集的 schema 契约**。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v65 多一张表**：`problem_classification_candidate` 在 64.json 里不存在、在 65.json
 *    里存在；其余每一张表、每一条既有索引与两张视图的 DDL 逐字不变（纯新增迁移不牵动既有面）；
 * 2. **迁移 DDL 与导出的 65.json 逐字一致**：建表与三条索引的语句必须等于 65.json 的
 *    createSql（`${TABLE_NAME}` 占位符替换后）——Room 打开库时按 schema 校验，差一个空格都会在
 *    真机升级时炸；
 * 3. **列形状钉死**：候选身份 `candidate_id` 是 PK；`confidence`/`accepted`/
 *    `acceptance_source`/`accepted_at_epoch_millis` 全部 NOT NULL——被拒候选（`accepted = 0`）
 *    同样要有分数与权威，否则"被拒候选也要落"只剩半条事实。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.kf5bClassificationCandidatesTableIsAddedLeavingExistingRowsIntactAndIdempotent`
 * 管"真库迁移后新表建出、既有行一行不动、再开一次不漂"。
 */
class ProblemClassificationCandidateSchemaContractTest {

    @Test
    fun `the candidate table appears only in 65`() {
        val v64 = exportedDatabase(64).entities.map { it.tableName }
        val v65 = exportedDatabase(65).entities.map { it.tableName }

        assertFalse("64 里不该有这张表（否则迁移没有意义）", TABLE in v64)
        assertTrue("65 里必须有这张表", TABLE in v65)
        assertEquals(
            "纯新增：65 相对 64 只多这一张表（不删、不改名）",
            (v64 + TABLE).toSet(),
            v65.toSet(),
        )
        assertEquals("表的数量只多一张", v64.size + 1, v65.size)
    }

    @Test
    fun `every other table index and both views are byte identical between 64 and 65`() {
        fun shapes(database: CandidateSchemaDatabase) = database.entities
            .filterNot { it.tableName == TABLE }
            .associate { entity ->
                entity.tableName to (entity.createSql to entity.indices.map { it.createSql })
            }
        assertEquals(
            "65 相对 64 不许动任何既有表/索引",
            shapes(exportedDatabase(64)),
            shapes(exportedDatabase(65)),
        )
        assertEquals(
            "65 相对 64 不许动任何视图",
            exportedDatabase(64).views.associate { it.viewName to it.createSql },
            exportedDatabase(65).views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration creates exactly the exported table and indices`() {
        val exported = exportedTable(65)
        assertEquals(
            "迁移的建表语句必须与 65.json 的 createSql 逐字一致（Room 按 schema 校验）",
            exported.createSql.replace("\${TABLE_NAME}", TABLE),
            PROBLEM_CLASSIFICATION_CANDIDATE_DDL_64_65,
        )
        assertEquals(
            "三条索引一条不少、逐字一致",
            exported.indices.map { it.createSql.replace("\${TABLE_NAME}", TABLE) },
            listOf(
                PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_PROBLEM_64_65,
                PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_LABEL_64_65,
                PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_ROUND_64_65,
            ),
        )
        assertTrue(
            "建表必须是纯新增（IF NOT EXISTS，不重建表、不动行）",
            PROBLEM_CLASSIFICATION_CANDIDATE_DDL_64_65.startsWith("CREATE TABLE IF NOT EXISTS `$TABLE`"),
        )
        assertTrue(
            "索引也必须 IF NOT EXISTS（同一库重放迁移不炸）",
            listOf(
                PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_PROBLEM_64_65,
                PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_LABEL_64_65,
                PROBLEM_CLASSIFICATION_CANDIDATE_INDEX_ROUND_64_65,
            ).all { it.startsWith("CREATE INDEX IF NOT EXISTS ") },
        )
    }

    @Test
    fun `the candidate columns carry the rejected candidates too`() {
        val exported = exportedTable(65)
        assertEquals(
            "列必须与实体声明顺序一致（Room 导出的形状就是真库的形状）",
            listOf(
                "candidate_id",
                "problem_id",
                "basis_revision_id",
                "dimension",
                "label_id",
                "display_name",
                "confidence",
                "accepted",
                "acceptance_source",
                "accepted_at_epoch_millis",
            ),
            columnShape(exported.createSql),
        )
        val fields = exported.fields.associateBy { it.columnName }
        assertTrue("candidate_id 必须是主键", exported.primaryKey.columnNames == listOf("candidate_id"))
        listOf("confidence" to "REAL", "accepted" to "INTEGER", "acceptance_source" to "TEXT")
            .forEach { (column, affinity) ->
                val field = fields.getValue(column)
                assertEquals("$column 的亲和性必须是 $affinity", affinity, field.affinity)
                assertTrue(
                    "$column 必须 NOT NULL——被拒候选也要带分数与权威（accepted=0 不是「没有事实」）",
                    field.notNull,
                )
            }
    }

    // ---- fixtures ----

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(version: Int): CandidateSchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == TABLE }

    private fun exportedDatabase(version: Int): CandidateSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<CandidateSchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val TABLE = "problem_classification_candidate"

        val json = Json { ignoreUnknownKeys = true }
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class CandidateSchemaFile(
    @SerialName("database") val database: CandidateSchemaDatabase,
)

@Serializable
private data class CandidateSchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<CandidateSchemaEntity> = emptyList(),
    @SerialName("views") val views: List<CandidateSchemaView> = emptyList(),
)

@Serializable
private data class CandidateSchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("fields") val fields: List<CandidateSchemaField> = emptyList(),
    @SerialName("indices") val indices: List<CandidateSchemaIndex> = emptyList(),
    @SerialName("primaryKey") val primaryKey: CandidateSchemaPrimaryKey = CandidateSchemaPrimaryKey(),
)

@Serializable
private data class CandidateSchemaField(
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
    @SerialName("notNull") val notNull: Boolean = false,
)

@Serializable
private data class CandidateSchemaIndex(@SerialName("createSql") val createSql: String)

@Serializable
private data class CandidateSchemaPrimaryKey(
    @SerialName("columnNames") val columnNames: List<String> = emptyList(),
)

@Serializable
private data class CandidateSchemaView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
