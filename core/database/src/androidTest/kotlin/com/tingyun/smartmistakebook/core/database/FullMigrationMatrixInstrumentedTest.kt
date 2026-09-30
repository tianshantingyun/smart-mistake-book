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

                assertEquals(0, migrated.libraryCatalogCount("", null, null, null, null))
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

    // ---- fixtures ----

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
                lastAttemptId = "attempt-wave0",
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
    }
}
