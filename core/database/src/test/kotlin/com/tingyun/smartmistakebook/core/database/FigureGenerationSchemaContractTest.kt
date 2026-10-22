package com.tingyun.smartmistakebook.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **4E WG-08（v68→69）图生成审计台账与三处追加列的 schema 契约**。
 *
 * 这张台账只回答"谁、基于什么输入、用哪个方法试了几次、断在哪一环"——它是**审计镜像**，
 * 不是预算权威：远程派发的预算仍只由 `model_task_operation.dispatch_count` 与
 * `clean_redraw_task.attempt_count` 持有。所以这里钉住的不是"计数正确"（那是测试与
 * recorder 的职责），而是"这两张表**不能**长成第二本预算"：operation 没有任何指向预算表/
 * 资产表的级联外键，attempt 对 operation 与输出资产都是 RESTRICT（删父行不许悄悄抹掉尝试证据）。
 *
 * 三层证据（JVM 上就能炸）：
 * 1. **只在 v69 多两张表**：`figure_generation_operation` / `figure_generation_attempt` 在
 *    68.json 里不存在、在 69.json 里存在；其余每一张表、每一条既有索引与两张视图的 DDL
 *    逐字不变；
 * 2. **迁移 DDL 与导出的 69.json 逐字一致**：两张新表、四条索引与四条 `ALTER TABLE`
 *    追加列必须等于 69.json 的 createSql/字段声明——Room 打开库时按 schema 校验，差一个
 *    空格都会在真机升级时炸；
 * 3. **列形状钉死**：operation 的身份是 `operation_id`（不是 source 组合：同一来源的
 *    不同 purpose/basis 是两次操作）；attempt 的 `(operation_id, attempt_ordinal)` 唯一
 *    （终态 attempt 不许复活）；`output_asset_id` 可空（失败没有产物），`provider_snapshot`/
 *    `model_snapshot`/`diagnostics` 可空（失败时可能未知或为空）。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest.figureGenerationLedgerIsCreatedWithoutTouchingV68RowsAndIdempotent`
 * 管"真库迁移后新表建出、旧行一行不动、约束真的生效、再开一次不漂"。
 *
 * 注：`69.json` 是 Room 编译器（KSP）在构建时写进 `schemas/` 的导出物，**不手工维护**；
 * 它缺失时本用例会红，说明这次构建没有跑过 core:database 的 schema 导出。
 */
class FigureGenerationSchemaContractTest {

    @Test
    fun `the figure generation tables appear only in 69`() {
        val v68 = exportedDatabase(68).entities.map { it.tableName }
        val v69 = exportedDatabase(69).entities.map { it.tableName }

        assertFalse("68 里不该有操作台账（否则迁移没有意义）", OPERATION_TABLE in v68)
        assertFalse("68 里不该有尝试台账（否则迁移没有意义）", ATTEMPT_TABLE in v68)
        assertTrue(
            "69 里必须有操作台账（69.json 由构建期 Room 导出生成——缺失说明 schema 没导出）",
            OPERATION_TABLE in v69,
        )
        assertTrue("69 里必须有尝试台账", ATTEMPT_TABLE in v69)
        assertEquals(
            "纯新增：69 相对 68 只多这两张表（不删、不改名）",
            (v68 + NEW_TABLES).toSet(),
            v69.toSet(),
        )
        assertEquals("表的数量只多两张", v68.size + 2, v69.size)
    }

    @Test
    fun `every untouched table index and view is byte identical between 68 and 69`() {
        val changed = (NEW_TABLES + ALTERED_TABLES).toSet()
        fun shapes(version: Int) = exportedDatabase(version).entities
            .filterNot { it.tableName in changed }
            .associate { entity ->
                entity.tableName to (entity.createSql to entity.indices.map { it.createSql })
            }

        assertEquals("69 相对 68 不许动任何未被本迁移点名的表/索引", shapes(68), shapes(69))
        assertEquals(
            "69 相对 68 不许动任何视图",
            exportedDatabase(68).views.associate { it.viewName to it.createSql },
            exportedDatabase(69).views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the three altered tables only append their columns`() {
        APPENDED_COLUMNS.forEach { (table, appended) ->
            val before = exportedTable(table, 68)
            val after = exportedTable(table, 69)
            assertEquals(
                "$table 只追加列（列序：旧列逐位在前，新列追加在行尾）",
                before.fields.map { it.columnName } + appended,
                after.fields.map { it.columnName },
            )
            assertEquals(
                "$table 的索引一条不增不减（追加列不改变查询形状）",
                before.indices.map { it.createSql },
                after.indices.map { it.createSql },
            )
        }
    }

    @Test
    fun `the migration creates exactly the exported tables and indices`() {
        assertEquals(
            "operation 建表语句必须与 69.json 的 createSql 逐字一致（Room 按 schema 校验）",
            exportedTable(OPERATION_TABLE, 69).createSql
                .replace(TABLE_PLACEHOLDER, OPERATION_TABLE),
            FIGURE_GENERATION_OPERATION_DDL_68_69,
        )
        assertEquals(
            "operation 的两条索引一条不少、逐字一致",
            exportedTable(OPERATION_TABLE, 69).indices.map {
                it.createSql.replace(TABLE_PLACEHOLDER, OPERATION_TABLE)
            },
            listOf(
                FIGURE_GENERATION_OPERATION_INDEX_SOURCE_DDL_68_69,
                FIGURE_GENERATION_OPERATION_INDEX_STATUS_DDL_68_69,
            ),
        )
        assertEquals(
            "attempt 建表语句必须与 69.json 的 createSql 逐字一致",
            exportedTable(ATTEMPT_TABLE, 69).createSql.replace(TABLE_PLACEHOLDER, ATTEMPT_TABLE),
            FIGURE_GENERATION_ATTEMPT_DDL_68_69,
        )
        assertEquals(
            "attempt 的唯一索引与输出资产索引一条不少、逐字一致",
            exportedTable(ATTEMPT_TABLE, 69).indices.map {
                it.createSql.replace(TABLE_PLACEHOLDER, ATTEMPT_TABLE)
            },
            listOf(
                FIGURE_GENERATION_ATTEMPT_INDEX_ORDINAL_DDL_68_69,
                FIGURE_GENERATION_ATTEMPT_INDEX_OUTPUT_ASSET_DDL_68_69,
            ),
        )

        listOf(
            FIGURE_GENERATION_OPERATION_DDL_68_69,
            FIGURE_GENERATION_ATTEMPT_DDL_68_69,
        ).forEach { ddl ->
            assertTrue(
                "建表必须是纯新增（IF NOT EXISTS，不重建表、不动行）",
                ddl.startsWith("CREATE TABLE IF NOT EXISTS "),
            )
        }
        listOf(
            FIGURE_GENERATION_OPERATION_INDEX_SOURCE_DDL_68_69,
            FIGURE_GENERATION_OPERATION_INDEX_STATUS_DDL_68_69,
            FIGURE_GENERATION_ATTEMPT_INDEX_ORDINAL_DDL_68_69,
            FIGURE_GENERATION_ATTEMPT_INDEX_OUTPUT_ASSET_DDL_68_69,
        ).forEach { ddl ->
            assertTrue(
                "索引也必须 IF NOT EXISTS（同一库重放迁移不炸）",
                ddl.startsWith("CREATE INDEX IF NOT EXISTS ") ||
                    ddl.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS "),
            )
        }
        assertTrue(
            "(operation_id, attempt_ordinal) 必须是 UNIQUE（终态 attempt 不许复活）",
            FIGURE_GENERATION_ATTEMPT_INDEX_ORDINAL_DDL_68_69.startsWith(
                "CREATE UNIQUE INDEX IF NOT EXISTS ",
            ),
        )
        val attemptIndices = exportedTable(ATTEMPT_TABLE, 69).indices
        assertTrue(
            "唯一索引的导出标志必须为 unique=true（不只是 SQL 文本写着 UNIQUE）",
            attemptIndices.single {
                it.name == "index_figure_generation_attempt_operation_id_attempt_ordinal"
            }.unique,
        )
        assertFalse(
            "输出资产索引不是唯一索引（同一张图可以被多次尝试引用/审计）",
            attemptIndices.single {
                it.name == "index_figure_generation_attempt_output_asset_id"
            }.unique,
        )
    }

    @Test
    fun `the operation ledger pins identity source and audit mirrors`() {
        val table = exportedTable(OPERATION_TABLE, 69)
        assertEquals(
            "列必须与实体声明顺序一致（Room 导出的形状就是真库的形状）",
            listOf(
                "operation_id",
                "learner_id",
                "source_kind",
                "source_id",
                "basis_fingerprint",
                "purpose",
                "policy_version",
                "status",
                "reserved_remote_dispatches",
                "used_remote_dispatches",
                "final_answer_reserved",
                "created_at_epoch_millis",
                "updated_at_epoch_millis",
            ),
            table.fields.map { it.columnName },
        )
        assertEquals(
            "操作身份是 operation_id（同一来源的不同 purpose/basis 是两次操作）",
            listOf("operation_id"),
            table.primaryKey.columnNames,
        )
        assertEquals(
            "操作台账不许有外键：它不是预算/资产的存在性依赖，也不能被删除级联带走",
            emptyList<FigureGenerationSchemaForeignKey>(),
            table.foreignKeys,
        )
        val fields = table.fields.associateBy { it.columnName }
        listOf(
            "learner_id" to "TEXT",
            "source_kind" to "TEXT",
            "source_id" to "TEXT",
            "basis_fingerprint" to "TEXT",
            "purpose" to "TEXT",
            "policy_version" to "TEXT",
            "status" to "TEXT",
            "reserved_remote_dispatches" to "INTEGER",
            "used_remote_dispatches" to "INTEGER",
            "final_answer_reserved" to "INTEGER",
            "created_at_epoch_millis" to "INTEGER",
            "updated_at_epoch_millis" to "INTEGER",
        ).forEach { (column, affinity) ->
            val field = fields.getValue(column)
            assertEquals("$column 的亲和性必须是 $affinity", affinity, field.affinity)
            assertTrue("$column 必须 NOT NULL", field.notNull)
        }
    }

    @Test
    fun `the attempt ledger pins the operation foreign key and one row per ordinal`() {
        val table = exportedTable(ATTEMPT_TABLE, 69)
        assertEquals(
            "列必须与实体声明顺序一致",
            listOf(
                "attempt_id",
                "operation_id",
                "attempt_ordinal",
                "method",
                "status",
                "provider_snapshot",
                "model_snapshot",
                "source_sha256",
                "input_fingerprint",
                "output_asset_id",
                "reservation_id",
                "diagnostics",
                "started_at_epoch_millis",
                "ended_at_epoch_millis",
            ),
            table.fields.map { it.columnName },
        )
        assertEquals(
            "尝试身份是 attempt_id（含 method；同一操作的第 n 次尝试是独立一行）",
            listOf("attempt_id"),
            table.primaryKey.columnNames,
        )
        assertEquals(
            "operation 与输出资产都必须 RESTRICT：删父行不许抹掉尝试证据",
            listOf(
                FigureGenerationSchemaForeignKey(
                    table = OPERATION_TABLE,
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("operation_id"),
                    referencedColumns = listOf("operation_id"),
                ),
                FigureGenerationSchemaForeignKey(
                    table = "canonical_source_asset",
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("output_asset_id"),
                    referencedColumns = listOf("source_asset_id"),
                ),
            ),
            table.foreignKeys,
        )

        val fields = table.fields.associateBy { it.columnName }
        listOf(
            "operation_id" to "TEXT",
            "attempt_ordinal" to "INTEGER",
            "method" to "TEXT",
            "status" to "TEXT",
            "input_fingerprint" to "TEXT",
            "started_at_epoch_millis" to "INTEGER",
        ).forEach { (column, affinity) ->
            val field = fields.getValue(column)
            assertEquals("$column 的亲和性必须是 $affinity", affinity, field.affinity)
            assertTrue("$column 必须 NOT NULL", field.notNull)
        }
        listOf(
            "provider_snapshot",
            "model_snapshot",
            "source_sha256",
            "output_asset_id",
            "reservation_id",
            "diagnostics",
            "ended_at_epoch_millis",
        ).forEach { column ->
            assertFalse(
                "$column 必须可空：失败/未完成/无源图时 NULL 才是诚实值",
                fields.getValue(column).notNull,
            )
            assertNull(
                "$column 不许带默认值（未知就是 NULL，不编造）",
                fields.getValue(column).defaultValue,
            )
        }
    }

    @Test
    fun `the append-only columns are nullable or defaulted so v68 rows stay readable`() {
        val role = exportedField("tutor_message_source_asset", "role", 69)
        assertFalse("旧行没有角色：NULL = 未知，迁移不回填", role.notNull)
        assertNull("不编造角色，也就没有默认值", role.defaultValue)

        val basisRevision = exportedField("tutor_message_source_asset", "basis_revision_id", 69)
        assertFalse("旧行没有 basis：NULL = 未知，迁移不回填", basisRevision.notNull)
        assertNull("不编造 basis，也就没有默认值", basisRevision.defaultValue)

        val operation = exportedField("clean_redraw_task", "operation_id", 69)
        assertFalse("旧作业没有 operation：NULL = 未知，迁移不回填", operation.notNull)
        assertNull("不编造 operation，也就没有默认值", operation.defaultValue)

        val ordinal = exportedField("problem_revision_source_asset", "ordinal", 69)
        assertTrue(
            "ordinal 是不可空列：旧行没有顺序，用 DEFAULT 0 兜底（单一源图 = 第 0 张）",
            ordinal.notNull,
        )
        assertEquals("默认值必须是 0", "0", ordinal.defaultValue)

        assertEquals(
            "迁移的 role 追加列必须与 69.json 的列声明逐字一致",
            "ALTER TABLE `tutor_message_source_asset` ADD COLUMN `role` TEXT",
            TUTOR_MESSAGE_SOURCE_ASSET_ROLE_DDL_68_69,
        )
        assertEquals(
            "迁移的 basis_revision_id 追加列必须与 69.json 的列声明逐字一致",
            "ALTER TABLE `tutor_message_source_asset` ADD COLUMN `basis_revision_id` TEXT",
            TUTOR_MESSAGE_SOURCE_ASSET_BASIS_REVISION_DDL_68_69,
        )
        assertEquals(
            "迁移的 operation_id 追加列必须与 69.json 的列声明逐字一致",
            "ALTER TABLE `clean_redraw_task` ADD COLUMN `operation_id` TEXT",
            CLEAN_REDRAW_TASK_OPERATION_DDL_68_69,
        )
        assertEquals(
            "迁移的 ordinal 追加列必须与 69.json 的列声明逐字一致（NOT NULL 必须带 DEFAULT）",
            "ALTER TABLE `problem_revision_source_asset` " +
                "ADD COLUMN `ordinal` INTEGER NOT NULL DEFAULT 0",
            PROBLEM_REVISION_SOURCE_ASSET_ORDINAL_DDL_68_69,
        )
    }

    @Test
    fun `the budget authorities keep their shapes and the audit ledger never points at them`() {
        val modelOperation68 = exportedTable("model_task_operation", 68)
        val modelOperation69 = exportedTable("model_task_operation", 69)
        assertEquals(
            "model_task_operation 是远程派发预算的权威：68→69 一个字符都不许动",
            modelOperation68.createSql,
            modelOperation69.createSql,
        )
        assertTrue(
            "dispatch_count 必须还在（读取预算的那一列）",
            "dispatch_count" in modelOperation69.fields.map { it.columnName },
        )

        val cleanRedraw69 = exportedTable("clean_redraw_task", 69)
        assertTrue(
            "clean_redraw_task.attempt_count 仍是净化重绘尝试上限的权威",
            "attempt_count" in cleanRedraw69.fields.map { it.columnName },
        )

        val auditTables = listOf(OPERATION_TABLE, ATTEMPT_TABLE)
        auditTables.forEach { table ->
            assertTrue(
                "$table 不许外键指向预算表：审计台账不是第二本预算",
                exportedTable(table, 69).foreignKeys.none {
                    it.table == "model_task_operation" || it.table == "clean_redraw_task"
                },
            )
        }
    }

    // ---- fixtures ----

    private fun exportedField(tableName: String, columnName: String, version: Int) =
        exportedTable(tableName, version).fields.single { it.columnName == columnName }

    private fun exportedTable(tableName: String, version: Int): FigureGenerationSchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun exportedDatabase(version: Int): FigureGenerationSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "$version.json" }
            ?: error(
                "schemas/$version.json 缺失：导出的 schema 由构建期 Room 导出生成" +
                    "（本用例在 core:database 的单测里跑，构建会先跑 KSP）",
            )
        return json.decodeFromString<FigureGenerationSchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val OPERATION_TABLE = "figure_generation_operation"
        const val ATTEMPT_TABLE = "figure_generation_attempt"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"

        val NEW_TABLES = listOf(OPERATION_TABLE, ATTEMPT_TABLE)

        /** 68→69 里被追加列的三张既有表与它们的新列（按实体声明顺序，追加在行尾）。 */
        val APPENDED_COLUMNS = linkedMapOf(
            "tutor_message_source_asset" to listOf("role", "basis_revision_id"),
            "clean_redraw_task" to listOf("operation_id"),
            "problem_revision_source_asset" to listOf("ordinal"),
        )
        val ALTERED_TABLES = APPENDED_COLUMNS.keys.toList()

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class FigureGenerationSchemaFile(
    @SerialName("database") val database: FigureGenerationSchemaDatabase,
)

@Serializable
private data class FigureGenerationSchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<FigureGenerationSchemaEntity> = emptyList(),
    @SerialName("views") val views: List<FigureGenerationSchemaView> = emptyList(),
)

@Serializable
private data class FigureGenerationSchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("fields") val fields: List<FigureGenerationSchemaField> = emptyList(),
    @SerialName("indices") val indices: List<FigureGenerationSchemaIndex> = emptyList(),
    @SerialName("primaryKey") val primaryKey: FigureGenerationSchemaPrimaryKey =
        FigureGenerationSchemaPrimaryKey(),
    @SerialName("foreignKeys") val foreignKeys: List<FigureGenerationSchemaForeignKey> =
        emptyList(),
)

@Serializable
private data class FigureGenerationSchemaField(
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
    @SerialName("notNull") val notNull: Boolean = false,
    @SerialName("defaultValue") val defaultValue: String? = null,
)

@Serializable
private data class FigureGenerationSchemaIndex(
    @SerialName("name") val name: String = "",
    @SerialName("unique") val unique: Boolean = false,
    @SerialName("createSql") val createSql: String,
)

@Serializable
private data class FigureGenerationSchemaPrimaryKey(
    @SerialName("columnNames") val columnNames: List<String> = emptyList(),
)

@Serializable
private data class FigureGenerationSchemaForeignKey(
    @SerialName("table") val table: String,
    @SerialName("onDelete") val onDelete: String,
    @SerialName("onUpdate") val onUpdate: String,
    @SerialName("columns") val columns: List<String>,
    @SerialName("referencedColumns") val referencedColumns: List<String>,
)

@Serializable
private data class FigureGenerationSchemaView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
