package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * schema 73（批次 40 / WS2）的 JVM 契约：导出物、实体列序、迁移 DDL、索引名四处同源。
 *
 * **为什么允许"73.json 尚不存在时先跳过"**：KSP 只在构建期写 schema；本批先落代码，由构建门
 * 生成 73.json 后再回填 `identityHash`（同批次 32 流程）。用 `assumeTrue` 把"尚未生成"与
 * "生成了但形状不对"分开：前者是流程中的正常态，后者必须红。构建门之后本测试会真正执行。
 *
 * 注意：`KernelWave0SchemaContractTest` 的版本字面量**不**跳过——它必须立刻把"提了版本但
 * 没导出"炸出来；本测试只负责形状对拍。
 */
class ErrorCauseSchemaContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the exported 73 schema has the two error cause tables`() {
        val database = exportedDatabase(73) ?: return
        val observation = database.entities.single { it.tableName == "error_cause_observation" }
        val binding = database.entities.single { it.tableName == "error_cause_binding" }

        assertEquals(
            listOf(
                "observation_id",
                "learner_id",
                "practice_unit_id",
                "problem_revision_id",
                "knowledge_node_id",
                "attempt_id",
                "cause",
                "detail_code",
                "confidence",
                "channel",
                "extractor_version",
                "taxonomy_version",
                "rationale_markdown",
                "evidence_refs_json",
                "observed_at_epoch_millis",
                "status",
                "decided_at_epoch_millis",
                "decision_source",
                "supersedes_observation_id",
            ),
            columnShape(observation.createSql),
        )
        assertEquals(
            listOf(
                "binding_id",
                "learner_id",
                "practice_unit_id",
                "problem_revision_id",
                "knowledge_node_id",
                "cause",
                "detail_code",
                "confidence",
                "acceptance_source",
                "gate_version",
                "source_observation_id",
                "accepted_at_epoch_millis",
                "state",
                "active_key",
                "superseded_by_binding_id",
                "ended_at_epoch_millis",
            ),
            columnShape(binding.createSql),
        )

        assertEquals(
            setOf(
                "practice_unit",
                "problem_revision",
                "knowledge_node",
                "attempt_event",
                "error_cause_observation",
            ),
            observation.foreignKeys.map { it.table }.toSet(),
        )
        assertEquals(
            setOf(
                "practice_unit",
                "problem_revision",
                "knowledge_node",
                "error_cause_observation",
                "error_cause_binding",
            ),
            binding.foreignKeys.map { it.table }.toSet(),
        )
        assertTrue(
            "FK 必须是 RESTRICT：错因行不能被父行静默带走",
            observation.foreignKeys.all { it.onDelete == "RESTRICT" } &&
                binding.foreignKeys.all { it.onDelete == "RESTRICT" },
        )

        assertEquals(
            setOf(
                "index_error_cause_observation_learner_unit_observed",
                "index_error_cause_observation_attempt_id",
                "index_error_cause_observation_unit_cause",
            ),
            observation.indices.map { it.name }.toSet(),
        )
        assertEquals(
            setOf(
                "index_error_cause_binding_active_key",
                "index_error_cause_binding_learner_unit_state",
                "index_error_cause_binding_problem_revision_id",
                "index_error_cause_binding_source_observation_id",
            ),
            binding.indices.map { it.name }.toSet(),
        )
        assertTrue(
            "active_key 必须是唯一索引（同一 scope 最多一条 ACTIVE）",
            binding.indices.single { it.name == "index_error_cause_binding_active_key" }.unique,
        )
    }

    @Test
    fun `the migration DDL and the exported schema name the same columns and indices`() {
        val database = exportedDatabase(73) ?: return
        val observation = database.entities.single { it.tableName == "error_cause_observation" }
        val binding = database.entities.single { it.tableName == "error_cause_binding" }

        assertEquals(
            columnShape(observation.createSql),
            columnShape(CREATE_ERROR_CAUSE_OBSERVATION_TABLE_72_73),
        )
        assertEquals(
            columnShape(binding.createSql),
            columnShape(CREATE_ERROR_CAUSE_BINDING_TABLE_72_73),
        )

        val migrationIndexNames = listOf(
            CREATE_ERROR_CAUSE_OBSERVATION_LEARNER_UNIT_OBSERVED_INDEX_72_73,
            CREATE_ERROR_CAUSE_OBSERVATION_ATTEMPT_INDEX_72_73,
            CREATE_ERROR_CAUSE_OBSERVATION_UNIT_CAUSE_INDEX_72_73,
            CREATE_ERROR_CAUSE_BINDING_ACTIVE_KEY_INDEX_72_73,
            CREATE_ERROR_CAUSE_BINDING_LEARNER_UNIT_STATE_INDEX_72_73,
            CREATE_ERROR_CAUSE_BINDING_REVISION_INDEX_72_73,
            CREATE_ERROR_CAUSE_BINDING_SOURCE_OBSERVATION_INDEX_72_73,
        ).map(::indexName)
        assertEquals(
            (observation.indices + binding.indices).map { it.name }.toSet(),
            migrationIndexNames.toSet(),
        )
    }

    @Test
    fun `the migration creates empty tables and performs no backfill`() {
        val observation = columnShape(CREATE_ERROR_CAUSE_OBSERVATION_TABLE_72_73)
        val binding = columnShape(CREATE_ERROR_CAUSE_BINDING_TABLE_72_73)
        assertTrue(observation.isNotEmpty() && binding.isNotEmpty())
        // 迁移对象只有一个：不读旧表、不写 INSERT/UPDATE/DELETE（本迁移只建空表）。
        val migrationSource = File(
            "src/main/kotlin/com/tingyun/smartmistakebook/core/database/ErrorCauseMigration_72_73.kt",
        ).readText()
        assertTrue(
            "迁移不得回填历史（无 INSERT/UPDATE/DELETE）",
            listOf("INSERT INTO", "DELETE FROM", "UPDATE `").none { it in migrationSource },
        )
    }

    private fun exportedDatabase(version: Int): SchemaDatabase? {
        val file = File("schemas")
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "$version.json" }
        assumeTrue("KSP 尚未生成 $version.json（构建门后回填）", file != null)
        return json.decodeFromString<ErrorCauseSchemaFile>(file!!.readText()).database
    }

    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun indexName(ddl: String): String =
        INDEX_NAME_PATTERN.find(ddl)?.groupValues?.get(1)
            ?: error("索引 DDL 缺少名字：$ddl")

    private companion object {
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
        val INDEX_NAME_PATTERN = Regex("CREATE (?:UNIQUE )?INDEX IF NOT EXISTS `([^`]+)`")
    }
}

// 名字必须带 ErrorCause 前缀：同包的 ExportedSchemaContractTest.kt 已声明私有 top-level
// `SchemaFile`，同包重名会被编译期判成 Redeclaration，并让两边的引用都落到"私有于他文件"
// 的声明上（Cannot access … it is private in file）。
@Serializable
private data class ErrorCauseSchemaFile(@SerialName("database") val database: SchemaDatabase)

@Serializable
private data class SchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<SchemaEntity>,
)

@Serializable
private data class SchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("foreignKeys") val foreignKeys: List<SchemaForeignKey> = emptyList(),
    @SerialName("indices") val indices: List<SchemaIndex> = emptyList(),
)

@Serializable
private data class SchemaForeignKey(
    @SerialName("table") val table: String,
    @SerialName("onDelete") val onDelete: String = "",
)

@Serializable
private data class SchemaIndex(
    @SerialName("name") val name: String,
    @SerialName("unique") val unique: Boolean = false,
)
