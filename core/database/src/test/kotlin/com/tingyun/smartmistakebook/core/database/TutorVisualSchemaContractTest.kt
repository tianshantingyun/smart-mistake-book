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
 * **D-01（v69→70）视觉四表的 schema 契约**。JVM 上就能炸的三层：
 *
 * 1. **只在 v70 多四张表**：`tutor_visual_program` / `tutor_visual_snapshot` /
 *    `tutor_message_visual` / `problem_revision_visual` 在 69.json 里不存在、在 70.json 里存在；
 *    其余每一张表、每一条既有索引与两个视图的 DDL **逐字不变**（纯新增不是重建）；
 * 2. **迁移 DDL 与导出的 70.json 逐字一致**：4 张表 + 11 条索引。Room 打开库时按 schema
 *    校验，差一个空格都会在真机升级时炸（与 `FigureGenerationSchemaContractTest` 同型）；
 * 3. **形状钉死**：程序行没有任何可变列（无 status、身份=program_id）；快照的
 *    `(program_id, state_sha256, draw_commands_sha256)` 唯一（同内容只存一份）；
 *    消息引用的主键是 `(message_id, ordinal)`、`current_snapshot_id` NOT NULL；
 *    题面引用主键是 `(problem_revision_id, role, ordinal)`；删除语义 = 唯一 CASCADE 边
 *    （message→link），其余被固定的事实一律 RESTRICT。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，真库迁移/旧行/外键负向由
 * `TutorVisualSnapshotMigration_69_70InstrumentedTest` 与
 * `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` 管。
 *
 * **schema 71（批次 32 / S1）**：v70 的四表断言**原样冻结**（历史事实不改写），另加一层
 * "快照唯一键在 v71 换成五列"的断言——见
 * `the snapshot identity key gains render and font versions only in 71`。
 *
 * 注：`70.json` 是 Room 编译器（KSP）在构建时写进 `schemas/` 的导出物，**不手工维护**；
 * 本批次没有运行 Gradle，所以它随首次构建出现（见同目录 `70.json.NOT_EXPORTED.md`）。
 * 缺失时本用例会红——那正是"提了版本但 schema 没导出"的信号。
 */
class TutorVisualSchemaContractTest {

    @Test
    fun `the four visual tables appear only in 70`() {
        val v69 = exportedDatabase(69).entities.map { it.tableName }
        val v70 = exportedDatabase(70).entities.map { it.tableName }

        NEW_TABLES.forEach { table ->
            assertFalse("69 里不该有 $table（否则迁移没有意义）", table in v69)
            assertTrue("70 里必须有 $table（70.json 由构建期 Room 导出生成）", table in v70)
        }
        assertEquals(
            "纯新增：70 相对 69 只多这四张表（不删、不改名）",
            (v69 + NEW_TABLES).toSet(),
            v70.toSet(),
        )
        assertEquals("表的数量只多四张", v69.size + NEW_TABLES.size, v70.size)
    }

    @Test
    fun `every untouched table index and view is byte identical between 69 and 70`() {
        fun shapes(version: Int) = exportedDatabase(version).entities
            .filterNot { it.tableName in NEW_TABLES }
            .associate { entity ->
                entity.tableName to (entity.createSql to entity.indices.map { it.createSql })
            }

        assertEquals("70 相对 69 不许动任何未被本迁移点名的表/索引", shapes(69), shapes(70))
        assertEquals(
            "70 相对 69 不许动任何视图",
            exportedDatabase(69).views.associate { it.viewName to it.createSql },
            exportedDatabase(70).views.associate { it.viewName to it.createSql },
        )
    }

    @Test
    fun `the migration creates exactly the exported tables and indices`() {
        TABLE_DDL.forEach { (table, ddl) ->
            assertEquals(
                "$table 建表语句必须与 70.json 的 createSql 逐字一致（Room 按 schema 校验）",
                exportedTable(table, 70).createSql.replace(TABLE_PLACEHOLDER, table),
                ddl,
            )
            assertTrue(
                "$table 必须是纯新增（IF NOT EXISTS，不重建表、不动行）",
                ddl.startsWith("CREATE TABLE IF NOT EXISTS "),
            )
        }
        INDEX_DDL.forEach { (table, ddls) ->
            assertEquals(
                "$table 的索引一条不少、逐字一致",
                exportedTable(table, 70).indices.map { it.createSql.replace(TABLE_PLACEHOLDER, table) },
                ddls,
            )
            ddls.forEach { ddl ->
                assertTrue(
                    "索引也必须 IF NOT EXISTS（同一库重放迁移不炸）：$ddl",
                    ddl.startsWith("CREATE INDEX IF NOT EXISTS ") ||
                        ddl.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS "),
                )
            }
        }
    }

    @Test
    fun `the migration only contains create statements`() {
        val statements = listOf(
            TUTOR_VISUAL_PROGRAM_DDL_69_70,
            TUTOR_VISUAL_PROGRAM_INDEX_LEARNER_DDL_69_70,
            TUTOR_VISUAL_PROGRAM_INDEX_BASIS_DDL_69_70,
            TUTOR_VISUAL_PROGRAM_INDEX_SOURCE_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_CONTENT_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_LATEST_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_PREVIEW_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_STATUS_DDL_69_70,
            TUTOR_MESSAGE_VISUAL_DDL_69_70,
            TUTOR_MESSAGE_VISUAL_INDEX_PROGRAM_DDL_69_70,
            TUTOR_MESSAGE_VISUAL_INDEX_SNAPSHOT_DDL_69_70,
            PROBLEM_REVISION_VISUAL_DDL_69_70,
            PROBLEM_REVISION_VISUAL_INDEX_PROGRAM_DDL_69_70,
            PROBLEM_REVISION_VISUAL_INDEX_SNAPSHOT_DDL_69_70,
        )
        assertEquals("迁移恰好 15 条 DDL（4 表 + 11 索引）", 15, statements.size)
        statements.forEach { ddl ->
            assertTrue("v69→70 只许 CREATE：$ddl", ddl.startsWith("CREATE "))
            listOf("ALTER TABLE", "DROP TABLE", "DELETE FROM", "INSERT INTO", "UPDATE `").forEach { verb ->
                assertFalse("v69→70 不许出现 $verb（旧行零改动、无回填）：$ddl", ddl.contains(verb))
            }
        }
    }

    @Test
    fun `the program row is immutable and has no foreign keys`() {
        val table = exportedTable(PROGRAM_TABLE, 70)
        assertEquals(
            "列必须与实体声明顺序一致",
            listOf(
                "program_id",
                "learner_id",
                "role",
                "source_sha256",
                "source_js",
                "initial_state_json",
                "initial_state_sha256",
                "api_version",
                "sdk_version",
                "basis_revision_id",
                "basis_fingerprint",
                "request_snapshot",
                "created_at_epoch_millis",
            ),
            table.fields.map { it.columnName },
        )
        assertEquals("程序身份是 program_id（= 对外 visualId）", listOf("program_id"), table.primaryKey.columnNames)
        assertEquals(
            "程序表不许外键：basis_revision_id 是软引用（多态来源不建外键）",
            emptyList<TutorVisualSchemaForeignKey>(),
            table.foreignKeys,
        )
        val fields = table.fields.associateBy { it.columnName }
        listOf(
            "program_id",
            "learner_id",
            "role",
            "source_sha256",
            "source_js",
            "initial_state_json",
            "initial_state_sha256",
            "api_version",
            "sdk_version",
            "basis_fingerprint",
            "created_at_epoch_millis",
        ).forEach { column ->
            assertTrue("$column 必须 NOT NULL", fields.getValue(column).notNull)
        }
        listOf("basis_revision_id", "request_snapshot").forEach { column ->
            assertFalse("$column 必须可空：旧/缺省来源就是 NULL，不编造", fields.getValue(column).notNull)
            assertNull("$column 不许带默认值", fields.getValue(column).defaultValue)
        }
        assertTrue(
            "api_version 必须是 INTEGER（能否再执行的判据）",
            fields.getValue("api_version").affinity == "INTEGER",
        )
    }

    @Test
    fun `the snapshot pins content identity replay inputs and the preview asset`() {
        val table = exportedTable(SNAPSHOT_TABLE, 70)
        assertEquals(
            "列必须与实体声明顺序一致",
            listOf(
                "snapshot_id",
                "program_id",
                "state_json",
                "state_sha256",
                "scene_schema_version",
                "scene_json",
                "scene_sha256",
                "render_schema_version",
                "draw_commands_json",
                "draw_commands_sha256",
                "content_ids_json",
                "viewport_width_du",
                "viewport_height_du",
                "font_scale_milli",
                "theme",
                "time_ms",
                "seed",
                "runtime_version",
                "compiler_version",
                "renderer_version",
                "font_manifest_version",
                "question_verification_status",
                "preview_asset_id",
                "status",
                "owner_request_id",
                "checkpoint_kind",
                "created_at_epoch_millis",
                "promoted_at_epoch_millis",
            ),
            table.fields.map { it.columnName },
        )
        assertEquals("快照身份是 snapshot_id", listOf("snapshot_id"), table.primaryKey.columnNames)
        assertEquals(
            "程序与预览资产都是 RESTRICT：删父行不许带走被引用的快照",
            listOf(
                TutorVisualSchemaForeignKey(
                    table = PROGRAM_TABLE,
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("program_id"),
                    referencedColumns = listOf("program_id"),
                ),
                TutorVisualSchemaForeignKey(
                    table = "canonical_source_asset",
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("preview_asset_id"),
                    referencedColumns = listOf("source_asset_id"),
                ),
            ),
            table.foreignKeys,
        )
        val contentIndex = table.indices.single { it.name == SNAPSHOT_CONTENT_INDEX }
        assertTrue(
            "同 program 同内容快照只存一份：唯一索引标志必须为 unique=true",
            contentIndex.unique,
        )
        assertEquals(
            "唯一键的三列与实体声明逐位一致",
            "CREATE UNIQUE INDEX IF NOT EXISTS `$SNAPSHOT_CONTENT_INDEX` ON " +
                "`$SNAPSHOT_TABLE` (`program_id`, `state_sha256`, `draw_commands_sha256`)",
            contentIndex.createSql.replace(TABLE_PLACEHOLDER, SNAPSHOT_TABLE),
        )

        val fields = table.fields.associateBy { it.columnName }
        listOf(
            "preview_asset_id",
            "owner_request_id",
            "checkpoint_kind",
            "promoted_at_epoch_millis",
        ).forEach { column ->
            assertFalse("$column 必须可空（未知/未提升就是 NULL）", fields.getValue(column).notNull)
        }
        listOf("draw_commands_json", "content_ids_json", "status").forEach { column ->
            assertTrue("$column 必须 NOT NULL（回放唯一输入与状态都是必填）", fields.getValue(column).notNull)
        }
    }

    @Test
    fun `the message link is unique per ordinal and only cascades from its host message`() {
        val table = exportedTable(MESSAGE_VISUAL_TABLE, 70)
        assertEquals(
            "列必须与实体声明顺序一致",
            listOf(
                "message_id",
                "ordinal",
                "program_id",
                "current_snapshot_id",
                "revision",
                "ui_state_json",
                "form_digest",
                "created_at_epoch_millis",
                "updated_at_epoch_millis",
            ),
            table.fields.map { it.columnName },
        )
        assertEquals(
            "主键 = (message_id, ordinal)（契约的 messageId+ordinal 唯一）",
            listOf("message_id", "ordinal"),
            table.primaryKey.columnNames,
        )
        assertEquals(
            "唯一 CASCADE 边（消息删 → link 删）；program/snapshot 一律 RESTRICT",
            listOf(
                TutorVisualSchemaForeignKey(
                    table = "tutor_message",
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION",
                    columns = listOf("message_id"),
                    referencedColumns = listOf("message_id"),
                ),
                TutorVisualSchemaForeignKey(
                    table = PROGRAM_TABLE,
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("program_id"),
                    referencedColumns = listOf("program_id"),
                ),
                TutorVisualSchemaForeignKey(
                    table = SNAPSHOT_TABLE,
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("current_snapshot_id"),
                    referencedColumns = listOf("snapshot_id"),
                ),
            ),
            table.foreignKeys,
        )
        val fields = table.fields.associateBy { it.columnName }
        assertTrue(
            "current_snapshot_id 必须 NOT NULL：可空会允许「link 存在但没有图」的中间态",
            fields.getValue("current_snapshot_id").notNull,
        )
        assertTrue("revision 是 CAS 期望版本，必须 NOT NULL", fields.getValue("revision").notNull)
        listOf("ui_state_json", "form_digest").forEach { column ->
            assertFalse("$column 必须可空（草稿/表单摘要可以没有）", fields.getValue(column).notNull)
        }
    }

    @Test
    fun `the problem revision link pins a saved problem without depending on the conversation`() {
        val table = exportedTable(REVISION_VISUAL_TABLE, 70)
        assertEquals(
            "列必须与实体声明顺序一致",
            listOf(
                "problem_revision_id",
                "role",
                "ordinal",
                "program_id",
                "snapshot_id",
                "created_at_epoch_millis",
            ),
            table.fields.map { it.columnName },
        )
        assertEquals(
            "主键镜像 problem_revision_source_asset：修订 + 角色 + 顺序",
            listOf("problem_revision_id", "role", "ordinal"),
            table.primaryKey.columnNames,
        )
        assertEquals(
            "修订/程序/快照全部 RESTRICT：被固定的图不许悄悄消失",
            listOf(
                TutorVisualSchemaForeignKey(
                    table = "problem_revision",
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("problem_revision_id"),
                    referencedColumns = listOf("revision_id"),
                ),
                TutorVisualSchemaForeignKey(
                    table = PROGRAM_TABLE,
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("program_id"),
                    referencedColumns = listOf("program_id"),
                ),
                TutorVisualSchemaForeignKey(
                    table = SNAPSHOT_TABLE,
                    onDelete = "RESTRICT",
                    onUpdate = "NO ACTION",
                    columns = listOf("snapshot_id"),
                    referencedColumns = listOf("snapshot_id"),
                ),
            ),
            table.foreignKeys,
        )
        val ordinal = table.fields.single { it.columnName == "ordinal" }
        assertTrue("ordinal 必须 NOT NULL", ordinal.notNull)
        assertEquals("单图 = 第 0 张：DEFAULT 0 与 v69 先例同形", "0", ordinal.defaultValue)
    }

    @Test
    fun `the snapshot identity key gains render and font versions only in 71`() {
        val v70 = exportedTable(SNAPSHOT_TABLE, 70)
        val v71 = exportedTable(SNAPSHOT_TABLE, 71)

        // v70 侧保持冻结：三列内容键是历史事实，迁移必须从它出发。
        assertTrue(
            "70 里必须还是三列内容键（否则这条迁移没有意义）",
            v70.indices.any { it.name == SNAPSHOT_CONTENT_INDEX && it.unique },
        )
        assertTrue(
            "71 里不许再有 v70 的三列唯一键（Room 会因'实际索引集合多一个'在真机升级时失败）",
            v71.indices.none { it.name == SNAPSHOT_CONTENT_INDEX },
        )

        val identity = v71.indices.single { it.name == SNAPSHOT_CONTENT_RENDER_INDEX }
        assertTrue("内容+渲染版本唯一键必须 unique=true", identity.unique)
        assertEquals(
            "五列与实体声明逐位一致（顺序即唯一键的匹配顺序）",
            "CREATE UNIQUE INDEX IF NOT EXISTS `$SNAPSHOT_CONTENT_RENDER_INDEX` ON " +
                "`$SNAPSHOT_TABLE` (`program_id`, `state_sha256`, `draw_commands_sha256`, " +
                "`renderer_version`, `font_manifest_version`)",
            identity.createSql.replace(TABLE_PLACEHOLDER, SNAPSHOT_TABLE),
        )
        assertEquals(
            "迁移 DDL 必须与 71.json 的 createSql 逐字一致（真机升级按它校验）",
            identity.createSql.replace(TABLE_PLACEHOLDER, SNAPSHOT_TABLE),
            VISUAL_SNAPSHOT_IDENTITY_INDEX_DDL_70_71,
        )
        assertEquals(
            "快照表索引集合：新唯一键 + 三条原样保留的查询索引",
            setOf(
                SNAPSHOT_CONTENT_RENDER_INDEX,
                "index_tutor_visual_snapshot_program_id_promoted_at_epoch_millis",
                "index_tutor_visual_snapshot_preview_asset_id",
                "index_tutor_visual_snapshot_status_created_at_epoch_millis",
            ),
            v71.indices.map { it.name }.toSet(),
        )
        assertEquals(
            "71 相对 70 只换索引：列一列不增不减",
            v70.fields.map { it.columnName },
            v71.fields.map { it.columnName },
        )
        assertTrue(
            "旧三列索引必须被显式 DROP（同名 CREATE … IF NOT EXISTS 不会改列）",
            DROP_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71.startsWith("DROP INDEX IF EXISTS ") &&
                DROP_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71.contains(SNAPSHOT_CONTENT_INDEX),
        )
    }

    // ---- fixtures ----

    private fun exportedTable(tableName: String, version: Int): TutorVisualSchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun exportedDatabase(version: Int): TutorVisualSchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "$version.json" }
            ?: error(
                "schemas/$version.json 缺失：导出的 schema 由构建期 Room 导出生成" +
                    "（本用例在 core:database 的单测里跑，构建会先跑 KSP）",
            )
        return json.decodeFromString<TutorVisualSchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        const val PROGRAM_TABLE = "tutor_visual_program"
        const val SNAPSHOT_TABLE = "tutor_visual_snapshot"
        const val MESSAGE_VISUAL_TABLE = "tutor_message_visual"
        const val REVISION_VISUAL_TABLE = "problem_revision_visual"
        const val SNAPSHOT_CONTENT_INDEX =
            "index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256"

        /** v71 的五列内容+渲染版本唯一键（`TutorVisualSnapshotEntity` 的显式索引名）。 */
        const val SNAPSHOT_CONTENT_RENDER_INDEX =
            "index_tutor_visual_snapshot_content_render_identity"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"

        val NEW_TABLES = listOf(PROGRAM_TABLE, SNAPSHOT_TABLE, MESSAGE_VISUAL_TABLE, REVISION_VISUAL_TABLE)

        /** 迁移里的建表语句（顺序与 `VISUAL_SNAPSHOT_MIGRATION_69_70` 一致）。 */
        val TABLE_DDL = linkedMapOf(
            PROGRAM_TABLE to TUTOR_VISUAL_PROGRAM_DDL_69_70,
            SNAPSHOT_TABLE to TUTOR_VISUAL_SNAPSHOT_DDL_69_70,
            MESSAGE_VISUAL_TABLE to TUTOR_MESSAGE_VISUAL_DDL_69_70,
            REVISION_VISUAL_TABLE to PROBLEM_REVISION_VISUAL_DDL_69_70,
        )

        /** 迁移里的索引语句（导出物顺序 = 实体 indices 声明顺序）。 */
        val INDEX_DDL = linkedMapOf(
            PROGRAM_TABLE to listOf(
                TUTOR_VISUAL_PROGRAM_INDEX_LEARNER_DDL_69_70,
                TUTOR_VISUAL_PROGRAM_INDEX_BASIS_DDL_69_70,
                TUTOR_VISUAL_PROGRAM_INDEX_SOURCE_DDL_69_70,
            ),
            SNAPSHOT_TABLE to listOf(
                TUTOR_VISUAL_SNAPSHOT_INDEX_CONTENT_DDL_69_70,
                TUTOR_VISUAL_SNAPSHOT_INDEX_LATEST_DDL_69_70,
                TUTOR_VISUAL_SNAPSHOT_INDEX_PREVIEW_DDL_69_70,
                TUTOR_VISUAL_SNAPSHOT_INDEX_STATUS_DDL_69_70,
            ),
            MESSAGE_VISUAL_TABLE to listOf(
                TUTOR_MESSAGE_VISUAL_INDEX_PROGRAM_DDL_69_70,
                TUTOR_MESSAGE_VISUAL_INDEX_SNAPSHOT_DDL_69_70,
            ),
            REVISION_VISUAL_TABLE to listOf(
                PROBLEM_REVISION_VISUAL_INDEX_PROGRAM_DDL_69_70,
                PROBLEM_REVISION_VISUAL_INDEX_SNAPSHOT_DDL_69_70,
            ),
        )

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class TutorVisualSchemaFile(
    @SerialName("database") val database: TutorVisualSchemaDatabase,
)

@Serializable
private data class TutorVisualSchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<TutorVisualSchemaEntity> = emptyList(),
    @SerialName("views") val views: List<TutorVisualSchemaView> = emptyList(),
)

@Serializable
private data class TutorVisualSchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("fields") val fields: List<TutorVisualSchemaField> = emptyList(),
    @SerialName("indices") val indices: List<TutorVisualSchemaIndex> = emptyList(),
    @SerialName("primaryKey") val primaryKey: TutorVisualSchemaPrimaryKey =
        TutorVisualSchemaPrimaryKey(),
    @SerialName("foreignKeys") val foreignKeys: List<TutorVisualSchemaForeignKey> =
        emptyList(),
)

@Serializable
private data class TutorVisualSchemaField(
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
    @SerialName("notNull") val notNull: Boolean = false,
    @SerialName("defaultValue") val defaultValue: String? = null,
)

@Serializable
private data class TutorVisualSchemaIndex(
    @SerialName("name") val name: String = "",
    @SerialName("unique") val unique: Boolean = false,
    @SerialName("createSql") val createSql: String,
)

@Serializable
private data class TutorVisualSchemaPrimaryKey(
    @SerialName("columnNames") val columnNames: List<String> = emptyList(),
)

@Serializable
private data class TutorVisualSchemaForeignKey(
    @SerialName("table") val table: String,
    @SerialName("onDelete") val onDelete: String,
    @SerialName("onUpdate") val onUpdate: String,
    @SerialName("columns") val columns: List<String>,
    @SerialName("referencedColumns") val referencedColumns: List<String>,
)

@Serializable
private data class TutorVisualSchemaView(
    @SerialName("viewName") val viewName: String,
    @SerialName("createSql") val createSql: String,
)
