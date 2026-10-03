package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * **内核 Wave 0 的迁移矩阵**（`docs/research/2026-09-28-kernel-remediation-roadmap.md`）：
 * 1→54 的逐版本迁移（每个版本都必须能升到当前且不带破坏性回退），外加 Wave 0 两处
 * **带数据**的迁移验证——矩阵本身用的是空库，看不出"旧行还在不在"。
 *
 * v53→54 做的两件事（W0-1 ③ 新表 + W0-2 指纹列合并）都在这里用真库、真驱动实测：
 * 前者证明归档行写得进读得出（`schema_ddl` 在同一个事务里现读），后者证明 `review_plan`
 * 重建**搬走了旧行**（外键在迁移期间是关的：Room 生成代码把 `PRAGMA foreign_keys = ON`
 * 放在 `onOpen`，所以 DROP 旧表不会级联掉子表）。
 */
@RunWith(AndroidJUnit4::class)
class FullMigrationMatrixInstrumentedTest {
    @Test
    fun everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (version in 1..STUDY_DATABASE_VERSION) {
            val databaseName = "migration-matrix-v$version-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            try {
                createDatabaseFromExportedSchema(context, databaseName, version = version)
                val migrated = StudyDatabaseFactory.open(context, databaseName)

                assertEquals(0, migrated.libraryCatalogCount("", null, null, null, null, null))
                migrated.close()
            } finally {
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun reviewPlanFingerprintMergeKeepsExistingPlanAndQueueRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave0-plan-merge-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 53)
            seedV53ReviewPlan(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            val stored = migrated.observeReviewPlan(PLAN_ID).first()

            assertNotNull("重建 review_plan 不许丢行", stored)
            assertEquals(LEGACY_PLANNER_VERSION, stored!!.plan.plannerVersion)
            assertEquals(
                "两列指纹恒同值：合并后留下的那一列就是旧行的 plan_fingerprint",
                PLAN_FINGERPRINT,
                stored.plan.planFingerprint,
            )
            assertEquals(
                "子表的队列行也必须还在（DROP 旧表不许级联删子表）",
                listOf("unit-wave0"),
                stored.queue.map { it.practiceUnitId },
            )
            migrated.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun projectionArchiveStoresSnapshotJsonAndSchemaDdl() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave0-archive-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName)
            val snapshot = archivedSnapshot()
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = "study-experience-v1",
                    learnerId = LEARNER_ID,
                    snapshotJson = LearnerSnapshotJson.encode(snapshot),
                    projectorVersion = snapshot.checkpoint.projectorVersion,
                    archivedAtEpochMillis = 1_700_000_000_000,
                ),
            )
            store.close()

            readArchiveRow(context, databaseName).let { row ->
                assertEquals("study-experience-v1", row.projectionName)
                assertEquals(LEARNER_ID, row.learnerId)
                assertEquals(1_700_000_000_000, row.archivedAtEpochMillis)
                assertEquals(LEGACY_PROJECTOR_VERSION, row.projectorVersion)
                assertEquals(
                    "归档的 JSON 必须能逐位解回被替换的那一份",
                    snapshot,
                    LearnerSnapshotJson.decode(row.snapshotJson),
                )
                assertTrue(
                    "schema_ddl 必须记下当时的投影表 DDL（回退时要判断兼容性）：${row.schemaDdl.take(80)}",
                    row.schemaDdl.contains("CREATE TABLE") &&
                        row.schemaDdl.contains("learner_projection_snapshot"),
                )
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 阶段 3B 步骤一 · D-M M5（v56→57）：删 `projection_consumption` 整表 +
     * `learner_problem_memory_state` 两死列——矩阵用的是空库，看不出"旧行还在不在"，
     * 这里用真库、真驱动实测三件事：
     * 1. 两死列从列清单消失，其余列一列不少；
     * 2. 重建**搬走了旧行**且值逐位不变（非破坏）；
     * 3. `projection_consumption` 整表（含索引）不复存在。
     */
    @Test
    fun m5DropKeepsMemoryRowsAndRemovesConsumptionTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave4-m5-drop-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 56)
            seedV56MemoryRowAndConsumptionRow(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(
                "Room 是惰性打开：先读版本强制它把 v56 库迁到 57（close 本身不触发迁移）",
                STUDY_DATABASE_VERSION,
                migrated.readDatabaseVersion(),
            )
            migrated.close()

            inspectV57M5Shape(context, databaseName)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 阶段 3B 步骤一 · D-M M1（v57→58）：删两张 legacy fixture 投影表——
     * 矩阵用的是空库，看不出"旧行还在不在"，这里用真库、真驱动实测：
     * 1. `problem_memory_state` / `knowledge_mastery_state` 整表（含索引）消失；
     * 2. 两表里原有的行随表消失（fixture 专表，无生产读者，不做搬运）；
     * 3. 真实学习数据（practice_unit / problem / problem_revision / 真实投影表）一行不碰。
     */
    @Test
    fun m1DropRemovesLegacyFixtureProjectionTablesAndKeepsRealRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave5-m1-drop-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 57)
            seedV57LegacyFixtureRows(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(
                "Room 是惰性打开：先读版本强制它把 v57 库迁到 58",
                STUDY_DATABASE_VERSION,
                migrated.readDatabaseVersion(),
            )
            migrated.close()

            inspectV58M1Shape(context, databaseName)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 阶段 3B 步骤三 · KF-32（v58→59）：新增改绑事件载荷表 `binding_change_event` + 索引。
     * 非破坏迁移的验证口径与上面两条一致：**新表真的建出来（列/索引逐位同形）**，
     * 且既有真实行一行不碰。
     */
    @Test
    fun kf32CreatesBindingChangeEventTableWithoutTouchingExistingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave6-kf32-binding-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 58)
            seedV58PracticeUnit(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(
                "Room 是惰性打开：先读版本强制它把 v58 库迁到 59",
                STUDY_DATABASE_VERSION,
                migrated.readDatabaseVersion(),
            )
            migrated.close()

            inspectV59BindingChangeShape(context, databaseName)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 阶段 4A 批 1 · L2（v59→60）：重建 `library_catalog` 视图，标题列由 `unit.title`
     * 改为 `revision.title`。矩阵用的是空库，看不出"旧行还在不在、读的是哪一列"，
     * 这里用真库、真驱动实测：
     * 1. 迁移前两列分叉（`practice_unit.title` = 旧标题，`revision.title` = 新标题）；
     * 2. 迁移后视图读 `revision.title`（旧实现会读回旧标题）；
     * 3. `practice_unit.title` 一行不动（写侧与枢纽列未动，计划 §5②）；
     * 4. 原条目行原样还在（视图重建不是删数据）。
     */
    @Test
    fun libraryCatalogRebuildReadsRevisionTitleWithoutTouchingStoredRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave7-title-source-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 59)
            seedV59DivergentTitleRow(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(
                "Room 是惰性打开：先读版本强制它把 v59 库迁到 60",
                STUDY_DATABASE_VERSION,
                migrated.readDatabaseVersion(),
            )
            val row = migrated.libraryCatalogPage(
                searchText = "",
                subjectId = null,
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = null,
                createdToEpochMillis = null,
                sort = "RECENTLY_UPDATED",
                offset = 0,
                limit = 10,
            ).single()
            assertEquals("迁移后的视图必须读 revision.title", TITLE_REVISION, row.title)
            migrated.close()

            inspectV60TitleSourceShape(context, databaseName)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun seedV59DivergentTitleRow(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """
                INSERT INTO `problem` (
                    `problem_id`, `canonical_fingerprint`, `subject`, `created_at_epoch_millis`
                ) VALUES ('$TITLE_PROBLEM_ID', 'fp-wave7', 'MATH', 1700000000000)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_revision` (
                    `revision_id`, `problem_id`, `revision_number`, `title`, `problem_markdown`,
                    `answer_spec_id`, `answer_spec_snapshot`, `answer_verification_status`,
                    `source_type`, `source_reference`, `content_fingerprint`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$TITLE_REVISION_ID', '$TITLE_PROBLEM_ID', 1, '$TITLE_REVISION', '题面',
                    NULL, NULL, 'UNKNOWN',
                    'CAPTURE_CONFIRMED', NULL, 'fp-wave7-rev',
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `practice_unit` (
                    `practice_unit_id`, `problem_id`, `problem_revision_id`, `unit_key`,
                    `unit_kind`, `title`, `prompt_markdown`, `estimated_seconds`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$TITLE_UNIT_ID', '$TITLE_PROBLEM_ID', '$TITLE_REVISION_ID', 'whole',
                    'WHOLE_PROBLEM', '$TITLE_UNIT', '题面', 120,
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `error_book_entry` (
                    `entry_id`, `practice_unit_id`, `problem_id`, `current_revision_id`,
                    `source_key`, `status`, `accepted_at_epoch_millis`, `updated_at_epoch_millis`
                ) VALUES (
                    '$TITLE_ENTRY_ID', '$TITLE_UNIT_ID', '$TITLE_PROBLEM_ID', '$TITLE_REVISION_ID',
                    NULL, 'ACTIVE', 1700000000000, 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    /** 迁移后的真库形状：视图 SQL 读 revision.title，枢纽列与条目行原样。 */
    private fun inspectV60TitleSourceShape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val viewSql = connection.prepare(
                "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = 'library_catalog'",
            )
            try {
                assertTrue("library_catalog 视图必须存在", viewSql.step())
                val sql = viewSql.getText(0)!!
                assertTrue(
                    "视图必须读 revision.title 且不再读 unit.title",
                    "revision.title" in sql && "unit.title" !in sql,
                )
            } finally {
                viewSql.close()
            }

            val unit = connection.prepare(
                "SELECT title FROM `practice_unit` WHERE practice_unit_id = '$TITLE_UNIT_ID'",
            )
            try {
                assertTrue("枢纽列的行必须还在", unit.step())
                assertEquals("写侧双列与列本身未动", TITLE_UNIT, unit.getText(0))
            } finally {
                unit.close()
            }

            val entry = connection.prepare(
                "SELECT entry_id, current_revision_id, status FROM `error_book_entry` " +
                    "WHERE entry_id = '$TITLE_ENTRY_ID'",
            )
            try {
                assertTrue("条目行必须一行不丢", entry.step())
                assertEquals(TITLE_ENTRY_ID, entry.getText(0))
                assertEquals(TITLE_REVISION_ID, entry.getText(1))
                assertEquals("ACTIVE", entry.getText(2))
            } finally {
                entry.close()
            }
        } finally {
            connection.close()
        }
    }

    /** 迁移后的真库形状：新表在、列与索引同形、既有 practice unit 行原样。 */
    private fun inspectV59BindingChangeShape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            assertTrue(
                "binding_change_event 表必须由迁移建出来",
                "binding_change_event" in tableNames(connection),
            )
            assertEquals(
                "列与 BindingChangeEventEntity 逐位同形",
                listOf(
                    "binding_change_id",
                    "learner_id",
                    "practice_unit_id",
                    "previous_knowledge_node_ids",
                    "new_knowledge_node_ids",
                    "occurred_at_epoch_millis",
                ),
                columnNames(connection, "binding_change_event"),
            )
            val indices = connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'binding_change_event'",
            )
            try {
                val names = mutableSetOf<String>()
                while (indices.step()) names += indices.getText(0)!!
                assertTrue(
                    "索引必须一起建出来（差集查询按 (learner, practice_unit)）：$names",
                    names.contains("index_binding_change_event_learner_id_practice_unit_id"),
                )
            } finally {
                indices.close()
            }

            val statement = connection.prepare(
                "SELECT practice_unit_id FROM `practice_unit` WHERE practice_unit_id = '$M1_UNIT_ID'",
            )
            try {
                assertTrue("既有真实行必须一行不丢", statement.step())
                assertEquals(M1_UNIT_ID, statement.getText(0))
                assertFalse("旧行只有这一条", statement.step())
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    /** 往 58.json 建出来的库里写一行真实 practice unit（连同 problem/revision），供"行不许动"断言。 */
    private fun seedV58PracticeUnit(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """
                INSERT INTO `problem` (
                    `problem_id`, `canonical_fingerprint`, `subject`, `created_at_epoch_millis`
                ) VALUES ('$M1_PROBLEM_ID', 'fp-wave6', 'MATH', 1700000000000)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_revision` (
                    `revision_id`, `problem_id`, `revision_number`, `title`, `problem_markdown`,
                    `answer_spec_id`, `answer_spec_snapshot`, `answer_verification_status`,
                    `source_type`, `source_reference`, `content_fingerprint`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$M1_REVISION_ID', '$M1_PROBLEM_ID', 1, 'KF-32 题', '题面',
                    NULL, NULL, 'UNKNOWN',
                    'CAPTURE_CONFIRMED', NULL, 'fp-wave6-rev',
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `practice_unit` (
                    `practice_unit_id`, `problem_id`, `problem_revision_id`, `unit_key`,
                    `unit_kind`, `title`, `prompt_markdown`, `estimated_seconds`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$M1_UNIT_ID', '$M1_PROBLEM_ID', '$M1_REVISION_ID', 'whole',
                    'WHOLE_PROBLEM', 'KF-32 题', '题面', 120,
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    /** 迁移后的真库形状：两张 legacy 表消失，真实行原样还在。 */
    private fun inspectV58M1Shape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val tables = tableNames(connection)
            assertFalse("problem_memory_state 整表必须消失", LEGACY_PROBLEM_MEMORY_TABLE in tables)
            assertFalse("knowledge_mastery_state 整表必须消失", LEGACY_MASTERY_TABLE in tables)
            assertTrue("真实题卡表必须保留", "learner_problem_memory_state" in tables)
            assertTrue("真实掌握表必须保留", "learner_knowledge_mastery_state" in tables)

            val statement = connection.prepare(
                "SELECT unit.practice_unit_id, problem.subject FROM practice_unit AS unit " +
                    "JOIN problem ON problem.problem_id = unit.problem_id",
            )
            try {
                assertTrue("真实行必须一行不丢", statement.step())
                assertEquals(M1_UNIT_ID, statement.getText(0))
                assertEquals("MATH", statement.getText(1))
                assertFalse("旧行只有这一条", statement.step())
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    /**
     * 往导出的 57.json 建出来的库里写：一行真实 practice unit（连同 problem/revision）
     * + 两 legacy 表各一行（显式关外键：fixture 表可以带任意 practice_unit/知识节点 id，
     * 这里要的是"迁移删没删干净、别的行动没动"，不是 FK 体系完备性）。
     */
    private fun seedV57LegacyFixtureRows(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """
                INSERT INTO `problem` (
                    `problem_id`, `canonical_fingerprint`, `subject`, `created_at_epoch_millis`
                ) VALUES ('$M1_PROBLEM_ID', 'fp-wave5', 'MATH', 1700000000000)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_revision` (
                    `revision_id`, `problem_id`, `revision_number`, `title`, `problem_markdown`,
                    `answer_spec_id`, `answer_spec_snapshot`, `answer_verification_status`,
                    `source_type`, `source_reference`, `content_fingerprint`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$M1_REVISION_ID', '$M1_PROBLEM_ID', 1, 'M1 题', '题面',
                    NULL, NULL, 'UNKNOWN',
                    'CAPTURE_CONFIRMED', NULL, 'fp-wave5-rev',
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `practice_unit` (
                    `practice_unit_id`, `problem_id`, `problem_revision_id`, `unit_key`,
                    `unit_kind`, `title`, `prompt_markdown`, `estimated_seconds`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$M1_UNIT_ID', '$M1_PROBLEM_ID', '$M1_REVISION_ID', 'whole',
                    'WHOLE_PROBLEM', 'M1 题', '题面', 120,
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `$LEGACY_PROBLEM_MEMORY_TABLE` (
                    `practice_unit_id`, `stability_days`, `difficulty`,
                    `last_reviewed_at_epoch_millis`, `next_review_at_epoch_millis`,
                    `review_count`, `lapse_count`, `retrievability`, `projection_checkpoint`,
                    `projector_version`, `updated_at_epoch_millis`
                ) VALUES (
                    '$M1_UNIT_ID', 12.5, 7.0, NULL, 1700100000000,
                    3, 0, 0.5, 5, 'projector-v11', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `$LEGACY_MASTERY_TABLE` (
                    `knowledge_node_id`, `mastery_probability`, `independent_correct_count`,
                    `assisted_correct_count`, `incorrect_count`, `evidence_weight_total`,
                    `last_evidence_at_epoch_millis`, `projection_checkpoint`,
                    `projector_version`, `updated_at_epoch_millis`
                ) VALUES (
                    'kc-legacy-wave5', 0.9, 5, 0, 1, 5.5,
                    1700000000000, 5, 'projector-v11', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    // ---- fixtures ----

    private fun tableNames(connection: SQLiteConnection): Set<String> {
        val statement = connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        )
        try {
            val names = mutableSetOf<String>()
            while (statement.step()) {
                names += statement.getText(0)!!
            }
            return names
        } finally {
            statement.close()
        }
    }

    private fun columnNames(connection: SQLiteConnection, table: String): List<String> {
        val statement = connection.prepare("PRAGMA table_info(`$table`)")
        try {
            val names = mutableListOf<String>()
            while (statement.step()) {
                names += statement.getText(1)!!
            }
            return names
        } finally {
            statement.close()
        }
    }

    /** 迁移后的真库形状：两死列/整表都没了，旧行原样还在。 */
    private fun inspectV57M5Shape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val tables = tableNames(connection)
            assertFalse(
                "projection_consumption 整表必须消失（含索引）",
                "projection_consumption" in tables,
            )
            assertTrue("learner_problem_memory_state 表本身是活体，必须保留", MEMORY_TABLE in tables)

            val columns = columnNames(connection, MEMORY_TABLE)
            assertFalse("last_reviewed_epoch_day 必须消失", "last_reviewed_epoch_day" in columns)
            assertFalse("last_attempt_id 必须消失", "last_attempt_id" in columns)
            assertEquals(
                "其余列一列不许少（顺序也逐位保留）",
                MEMORY_COLUMNS_V57,
                columns,
            )

            val statement = connection.prepare(
                "SELECT practice_unit_id, stability_days, difficulty, last_reviewed_at_epoch_millis, " +
                    "next_review_at_epoch_millis, independent_correct_count, projector_version, " +
                    "checkpoint_sequence FROM `$MEMORY_TABLE`",
            )
            try {
                assertTrue("重建必须搬走旧行", statement.step())
                assertEquals(MEMORY_UNIT_ID, statement.getText(0))
                assertEquals(12.5, statement.getDouble(1), 0.0)
                assertEquals(7.0, statement.getDouble(2), 0.0)
                assertEquals(1_700_000_000_000L, statement.getLong(3))
                assertEquals(1_700_100_000_000L, statement.getLong(4))
                assertEquals(3L, statement.getLong(5))
                assertEquals("projector-v11", statement.getText(6))
                assertEquals(5L, statement.getLong(7))
                assertFalse("旧行只有这一条（不该多也不该少）", statement.step())
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    /**
     * 往导出的 56.json 建出来的库里写一行 v56 形态的记忆行 + 一行消费回执。
     *
     * 显式关外键再写：这两行没有对应的 `learner_projection_snapshot` / `practice_unit` /
     * `projection_outbox` 父行（真库里不会出现这种组合），这里要的是"迁移会不会搬走行 /
     * 表删没删干净"，不是"FK 体系是否完备"——FK 的完备性由别的用例管。
     */
    private fun seedV56MemoryRowAndConsumptionRow(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """
                INSERT INTO `$MEMORY_TABLE` (
                    `projection_name`, `learner_id`, `practice_unit_id`, `stability_days`,
                    `difficulty`, `last_reviewed_at_epoch_millis`, `last_reviewed_epoch_day`,
                    `next_review_at_epoch_millis`, `independent_correct_count`,
                    `assisted_correct_count`, `lapse_count`, `answer_reveal_count`,
                    `last_lapse_at_epoch_millis`, `clock_anomaly_count`,
                    `last_clock_anomaly_at_epoch_millis`, `last_attempt_id`,
                    `projector_version`, `checkpoint_sequence`
                ) VALUES (
                    'study-experience-v1', '$WAVE4_LEARNER_ID', '$MEMORY_UNIT_ID', 12.5,
                    7.0, 1700000000000, 19675,
                    1700100000000, 3,
                    1, 0, 0,
                    NULL, 0,
                    NULL, 'attempt-wave4',
                    'projector-v11', 5
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `projection_consumption` (
                    `projection_name`, `learner_id`, `outbox_id`, `outbox_sequence`,
                    `projector_version`, `consumed_at_epoch_millis`
                ) VALUES (
                    'study-experience-v1', '$WAVE4_LEARNER_ID', 'outbox-wave4', 1,
                    'projector-v11', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    private class ArchiveRow(
        val projectionName: String,
        val learnerId: String,
        val archivedAtEpochMillis: Long,
        val snapshotJson: String,
        val projectorVersion: String,
        val schemaDdl: String,
    )

    private fun readArchiveRow(context: Context, databaseName: String): ArchiveRow {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare(
                "SELECT projection_name, learner_id, archived_at_epoch_millis, snapshot_json, " +
                    "projector_version, schema_ddl FROM projection_archive",
            )
            try {
                assertTrue("归档表必须有那一行", statement.step())
                return ArchiveRow(
                    projectionName = statement.getText(0)!!,
                    learnerId = statement.getText(1)!!,
                    archivedAtEpochMillis = statement.getLong(2),
                    snapshotJson = statement.getText(3)!!,
                    projectorVersion = statement.getText(4)!!,
                    schemaDdl = statement.getText(5)!!,
                )
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun archivedSnapshot() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = mapOf(
            "unit-wave0" to ProblemMemoryState(
                practiceUnitId = "unit-wave0",
                stabilityDays = 2.0,
                difficulty = 9.0,
                lastReviewedAtEpochMillis = 10L * 86_400_000L,
                nextReviewAtEpochMillis = 12L * 86_400_000L,
                projectorVersion = LEGACY_PROJECTOR_VERSION,
                checkpointSequence = 1,
            ),
        ),
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = LEGACY_PROJECTOR_VERSION,
            projectedAtEpochMillis = 10L * 86_400_000L,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = 10L * 86_400_000L,
    )

    /**
     * 往导出的 53.json 建出来的库里写一行 v53 形态的计划 + 一条队列子行。
     *
     * 显式关外键再写：这条队列行没有对应的 `practice_unit`（真库里不会出现这种组合），
     * 这里要的是"迁移会不会搬走行"，不是"FK 体系是否完备"——FK 的完备性由别的用例管。
     */
    private fun seedV53ReviewPlan(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """
                INSERT INTO `review_plan` (
                    `review_plan_id`, `learner_id`, `local_date`, `local_day_epoch_day`,
                    `time_zone_id`, `time_budget_seconds`, `planning_at_epoch_millis`, `status`,
                    `planner_version`, `projection_checkpoint`, `input_fingerprint`,
                    `plan_fingerprint`, `plan_revision`, `created_at_epoch_millis`
                ) VALUES (
                    '$PLAN_ID', '$LEARNER_ID', '2026-09-30', 20000,
                    'Asia/Shanghai', 900, 1700000000000, 'PLANNED',
                    '$LEGACY_PLANNER_VERSION', 0, '$PLAN_FINGERPRINT',
                    '$PLAN_FINGERPRINT', 1, 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `review_queue_item` (
                    `review_queue_item_id`, `review_plan_id`, `practice_unit_id`, `item_family_id`,
                    `source_bundle_id`, `ordinal`, `priority_score`, `due_at_epoch_millis`,
                    `difficulty_band`, `estimated_seconds`, `reason_snapshot`, `status`
                ) VALUES (
                    '$PLAN_ID-queue-0', '$PLAN_ID', 'unit-wave0', 'family-wave0',
                    NULL, 0, 0.5, NULL,
                    'MEDIUM', 300, '旧行的理由快照', 'PLANNED'
                )
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    private companion object {
        const val LEARNER_ID = "learner:wave0"
        const val PLAN_ID = "plan-wave0-v53"
        const val PLAN_FINGERPRINT = "wave0-plan-fingerprint-v53"
        const val LEGACY_PLANNER_VERSION = "review-planner-legacy-v1"
        const val LEGACY_PROJECTOR_VERSION = "learning-core-v6(projector-v6,evidence-v4)"

        /** v57→58（M1）用例：被删的两张 legacy fixture 表与真实行夹具值。 */
        const val LEGACY_PROBLEM_MEMORY_TABLE = "problem_memory_state"
        const val LEGACY_MASTERY_TABLE = "knowledge_mastery_state"
        const val M1_PROBLEM_ID = "problem-wave5"
        const val M1_REVISION_ID = "revision-wave5"
        const val M1_UNIT_ID = "unit-wave5"

        /** v56→57（M5）用例：被重建/被删的表与夹具值。 */
        const val MEMORY_TABLE = "learner_problem_memory_state"
        const val MEMORY_UNIT_ID = "unit-wave4"
        const val WAVE4_LEARNER_ID = "learner:wave4"

        /** v59→60（L2 标题单源）用例：两列分叉的夹具值。 */
        const val TITLE_PROBLEM_ID = "problem-wave7"
        const val TITLE_REVISION_ID = "revision-wave7"
        const val TITLE_UNIT_ID = "unit-wave7"
        const val TITLE_ENTRY_ID = "entry-wave7"
        const val TITLE_UNIT = "旧标题"
        const val TITLE_REVISION = "新标题"
        val MEMORY_COLUMNS_V57 = listOf(
            "projection_name",
            "learner_id",
            "practice_unit_id",
            "stability_days",
            "difficulty",
            "last_reviewed_at_epoch_millis",
            "next_review_at_epoch_millis",
            "independent_correct_count",
            "assisted_correct_count",
            "lapse_count",
            "answer_reveal_count",
            "last_lapse_at_epoch_millis",
            "clock_anomaly_count",
            "last_clock_anomaly_at_epoch_millis",
            "projector_version",
            "checkpoint_sequence",
            "last_evidence_reason",
            "last_evidence_direction",
            "consecutive_cross_day_success",
            "consecutive_cross_day_again",
        )
    }
}
