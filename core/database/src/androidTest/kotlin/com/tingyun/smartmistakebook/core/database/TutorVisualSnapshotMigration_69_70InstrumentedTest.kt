package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualSnapshotStatus
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualProgramEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualSnapshotEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **D-01（v69→70）视觉四表的真库验收**。矩阵（`FullMigrationMatrixInstrumentedTest` 的
 * `for (version in 1..STUDY_DATABASE_VERSION)`）用的是空库，看不出"旧行还在不在、新表是不是
 * 空的、约束真不真"，所以这里用真库、真驱动实测：
 *
 * 1. 四张新表与 11 条索引按迁移建出来（列序/索引名/外键形态与实体声明逐位同形）；
 * 2. **旧行一行不动**：v69 的资产/题/修订/修订附图/会话/消息/消息附图原样读回；
 * 3. **新表为空**——迁移不回填历史程序/快照（"无快照可考"如实呈现，不编造）；
 * 4. **外键负向**：缺父行写不进、被引用的父行删不掉（RESTRICT）、消息删了 link 级联走；
 * 5. 再开一次（迁移已是 no-op）形态与行不变——迁移幂等。
 *
 * 另有一条 DAO 用例钉"只有 LIVE 快照可被消息引用"的 **SQL 级结构门**与指针 CAS 的
 * "命中行数判赢"（`TutorVisualDao` KDoc 的三条门）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualSnapshotMigration_69_70InstrumentedTest {

    @Test
    fun visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch30-visual-v69-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 69)
            seedV69Rows(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(
                "Room 是惰性打开：先读版本强制它把 v69 库迁到 70",
                STUDY_DATABASE_VERSION,
                migrated.readDatabaseVersion(),
            )
            migrated.close()

            inspectV70Shape(context, databaseName)
            assertV70ForeignKeyBehaviour(context, databaseName)

            // 幂等：同一库再开一次（迁移已是 no-op）不得改变形态、旧行或新表的空态。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(STUDY_DATABASE_VERSION, reopened.readDatabaseVersion())
            reopened.close()
            inspectV70Shape(context, databaseName)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * DAO 的三条结构性门（真库、真 Room）：
     * - STAGING 快照**写不进**消息引用（`INSERT … SELECT … WHERE status='LIVE'`）；
     * - `STAGING → LIVE` 以命中行数判赢（重复提升第二次 0 行）；
     * - 指针 CAS 用过期 revision 更新命中 0 行，不覆盖新状态。
     */
    @Test
    fun onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch30-visual-dao-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            // 迁移到 70 并拿到 DAO（RoomStudyDatabase.database 对本模块可见）。
            assertEquals(STUDY_DATABASE_VERSION, store.readDatabaseVersion())
            val dao = (store as RoomStudyDatabase).database.tutorVisualDao()

            assertEquals("新库四表为空", 0L, countAllVisualRows(context, databaseName))
            assertTrue("新 program 必须写进去", dao.insertProgram(programEntity()) > 0)
            dao.insertProgram(programEntity())
            assertEquals(
                "同一 program 重放是幂等（IGNORE，不新增行）",
                1L,
                countRowsIn(context, databaseName, PROGRAM_TABLE),
            )

            val staging = snapshotEntity(snapshotId = SNAPSHOT_STAGING, status = TutorVisualSnapshotStatus.STAGING)
            assertTrue("STAGING 快照必须写进去", dao.insertSnapshot(staging) > 0)
            assertNotNull(
                "同内容再写一次回读的是既有行（INV-6：唯一索引幂等）",
                dao.findSnapshotByContent(PROGRAM_ID, staging.stateSha256, staging.drawCommandsSha256),
            )
            dao.insertSnapshot(staging.copy(snapshotId = "$SNAPSHOT_STAGING-dup"))
            assertEquals(
                "同内容 checkpoint 不产生新行",
                1L,
                countRowsIn(context, databaseName, SNAPSHOT_TABLE),
            )

            val stagingLink = runCatching { linkMessage(dao, context, databaseName, SNAPSHOT_STAGING) }
            assertNotNull("非 LIVE 快照必须写不进消息引用", stagingLink.exceptionOrNull())

            assertEquals(
                "STAGING→LIVE 命中 1 行",
                1,
                dao.promoteSnapshot(
                    snapshotId = SNAPSHOT_STAGING,
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    liveStatus = TutorVisualSnapshotStatus.LIVE,
                    promotedAtEpochMillis = 1_700_000_100_000,
                ),
            )
            assertEquals(
                "重复提升命中 0 行（LIVE 是终态，没有反向更新）",
                0,
                dao.promoteSnapshot(
                    snapshotId = SNAPSHOT_STAGING,
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    liveStatus = TutorVisualSnapshotStatus.LIVE,
                    promotedAtEpochMillis = 1_700_000_100_001,
                ),
            )

            val linked = linkMessage(dao, context, databaseName, SNAPSHOT_STAGING)
            assertEquals(PROGRAM_ID, linked.programId)
            assertEquals("首版指针是 1", 1, linked.revision)

            // 第二张 LIVE 快照作为换图目标。
            val next = snapshotEntity(snapshotId = SNAPSHOT_NEXT, status = TutorVisualSnapshotStatus.STAGING)
            assertTrue("第二张快照必须写进去", dao.insertSnapshot(next) > 0)
            assertEquals(
                1,
                dao.promoteSnapshot(
                    snapshotId = SNAPSHOT_NEXT,
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    liveStatus = TutorVisualSnapshotStatus.LIVE,
                    promotedAtEpochMillis = 1_700_000_200_000,
                ),
            )

            assertEquals(
                "过期 revision 的迟到 checkpoint 命中 0 行",
                0,
                dao.repointMessageVisual(
                    messageId = DAO_MESSAGE_ID,
                    ordinal = 0,
                    expectedRevision = 2,
                    nextSnapshotId = SNAPSHOT_NEXT,
                    nowEpochMillis = 1_700_000_300_000,
                    liveStatus = TutorVisualSnapshotStatus.LIVE,
                ),
            )
            assertEquals(
                "正确 revision 的换图命中 1 行",
                1,
                dao.repointMessageVisual(
                    messageId = DAO_MESSAGE_ID,
                    ordinal = 0,
                    expectedRevision = 1,
                    nextSnapshotId = SNAPSHOT_NEXT,
                    nowEpochMillis = 1_700_000_300_000,
                    liveStatus = TutorVisualSnapshotStatus.LIVE,
                ),
            )
            val afterCas = dao.findMessageVisual(DAO_MESSAGE_ID, 0)
            assertNotNull(afterCas)
            assertEquals(SNAPSHOT_NEXT, afterCas!!.currentSnapshotId)
            assertEquals("CAS 后指针版本 +1", 2, afterCas.revision)

            assertEquals(
                "最新 LIVE 快照按 promoted_at 取",
                SNAPSHOT_NEXT,
                dao.findLatestLiveSnapshot(PROGRAM_ID, TutorVisualSnapshotStatus.LIVE)?.snapshotId,
            )
            assertEquals(
                "STAGING 僵尸扫描把 LIVE 行排除在外",
                emptyList<String>(),
                dao.findStaleSnapshots(TutorVisualSnapshotStatus.STAGING, Long.MAX_VALUE)
                    .map { it.snapshotId },
            )
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    // ---- 真库形状 ----

    private fun inspectV70Shape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val tables = tableNames(connection)
            VISUAL_COLUMNS.forEach { (table, columns) ->
                assertTrue("迁移必须把 $table 建出来", table in tables)
                assertEquals("$table 列与实体声明逐位同形", columns, columnNames(connection, table))
            }
            VISUAL_INDICES.forEach { (table, names) ->
                assertEquals("$table 的索引一条不少", names, indexNames(connection, table))
            }
            // SQLite 存进 sqlite_master 的 DDL 已去掉 `IF NOT EXISTS`（设备实测），所以
            // 不能用 `substringBefore(" IF NOT EXISTS")` 当前缀比——那会把整条 DDL 留下，
            // 与裸前缀 `CREATE UNIQUE INDEX` 必然不等（批次 30 设备红因）。
            // 断言语义不放宽：索引必须存在、必须以 CREATE UNIQUE INDEX 开头、必须钉三列。
            val snapshotContentIndexSql = indexSql(connection, SNAPSHOT_CONTENT_INDEX)
            assertNotNull(
                "(program_id, state_sha256, draw_commands_sha256) 的唯一索引必须存在",
                snapshotContentIndexSql,
            )
            assertTrue(
                "内容唯一索引必须是 UNIQUE（同内容只存一份）；实际 DDL=$snapshotContentIndexSql",
                snapshotContentIndexSql!!.startsWith("CREATE UNIQUE INDEX"),
            )
            assertTrue(
                "内容唯一索引必须钉 (program_id, state_sha256, draw_commands_sha256)；" +
                    "实际 DDL=$snapshotContentIndexSql",
                snapshotContentIndexSql.contains(
                    "(`program_id`, `state_sha256`, `draw_commands_sha256`)",
                ),
            )
            assertEquals(
                "program 无外键（basis_revision_id 是软引用）",
                emptySet<Triple<String, String, String>>(),
                foreignKeys(connection, PROGRAM_TABLE).toSet(),
            )
            assertEquals(
                "snapshot 对 program 与预览资产都是 RESTRICT",
                setOf(
                    Triple(PROGRAM_TABLE, "program_id", "RESTRICT"),
                    Triple("canonical_source_asset", "preview_asset_id", "RESTRICT"),
                ),
                foreignKeys(connection, SNAPSHOT_TABLE).toSet(),
            )
            assertEquals(
                "link：message CASCADE，program/snapshot RESTRICT",
                setOf(
                    Triple("tutor_message", "message_id", "CASCADE"),
                    Triple(PROGRAM_TABLE, "program_id", "RESTRICT"),
                    Triple(SNAPSHOT_TABLE, "current_snapshot_id", "RESTRICT"),
                ),
                foreignKeys(connection, MESSAGE_VISUAL_TABLE).toSet(),
            )
            assertEquals(
                "题面引用：revision/program/snapshot 全 RESTRICT",
                setOf(
                    Triple("problem_revision", "problem_revision_id", "RESTRICT"),
                    Triple(PROGRAM_TABLE, "program_id", "RESTRICT"),
                    Triple(SNAPSHOT_TABLE, "snapshot_id", "RESTRICT"),
                ),
                foreignKeys(connection, REVISION_VISUAL_TABLE).toSet(),
            )

            // 旧行一行不动。
            val message = connection.prepare(
                "SELECT `body_markdown` FROM `tutor_message` WHERE `message_id` = '$MESSAGE_ID'",
            )
            try {
                assertTrue("旧消息行必须一行不丢", message.step())
                assertEquals("旧消息正文原样", "v69 学生附图", message.getText(0))
                assertFalse("旧行只有这一条", message.step())
            } finally {
                message.close()
            }
            val revision = connection.prepare(
                "SELECT `title`, `problem_markdown` FROM `problem_revision` " +
                    "WHERE `revision_id` = '$REVISION_ID'",
            )
            try {
                assertTrue("旧题面修订行必须一行不丢", revision.step())
                assertEquals("v69 题面", revision.getText(0))
                assertEquals("题面正文", revision.getText(1))
                assertFalse("旧行只有这一条", revision.step())
            } finally {
                revision.close()
            }
            val asset = connection.prepare(
                "SELECT `content_sha256` FROM `canonical_source_asset` " +
                    "WHERE `source_asset_id` = '$ASSET_ID'",
            )
            try {
                assertTrue("旧资产行必须一行不丢", asset.step())
                assertEquals("a".repeat(64), asset.getText(0))
                assertFalse("旧行只有这一条", asset.step())
            } finally {
                asset.close()
            }

            VISUAL_COLUMNS.keys.forEach { table ->
                assertEquals(
                    "迁移不回填历史：$table 必须是空的",
                    0L,
                    tableRowCount(connection, table),
                )
            }
        } finally {
            connection.close()
        }
    }

    // ---- 外键负向 ----

    private fun assertV70ForeignKeyBehaviour(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")

            // 1) 缺父行的引用写不进：快照必须指向存在的 program。
            val missingProgram = runCatching {
                connection.execSQL(
                    snapshotInsertSql(
                        snapshotId = "snapshot-missing-program",
                        status = "LIVE",
                        previewAssetId = null,
                        programId = "program-missing",
                    ),
                )
            }
            assertTrue(
                "不存在的 program 不许被快照引用；${missingProgram.exceptionOrNull()}",
                missingProgram.exceptionOrNull() != null,
            )

            // 2) 预览资产必须是存在的规范资产（第六类引用站点的外键面）。
            val missingPreviewAsset = runCatching {
                connection.execSQL(
                    snapshotInsertSql(
                        snapshotId = "snapshot-missing-preview",
                        status = "STAGING",
                        previewAssetId = "asset-missing",
                        programId = PROGRAM_ID,
                    ),
                )
            }
            assertTrue(
                "不存在的预览资产不许被快照引用；${missingPreviewAsset.exceptionOrNull()}",
                missingPreviewAsset.exceptionOrNull() != null,
            )

            // 3) 正常行：program + 快照（带预览资产）+ 独立消息上的 link + 题面引用。
            connection.execSQL(programInsertSql())
            connection.execSQL(
                """
                INSERT INTO `tutor_message` (
                    `message_id`, `conversation_id`, `ordinal`, `role`, `body_markdown`,
                    `status`, `created_at_epoch_millis`
                ) VALUES (
                    '$FK_MESSAGE_ID', '$CONVERSATION_ID', 2, 'ASSISTANT', 'FK 图消息',
                    'COMPLETED', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                snapshotInsertSql(
                    snapshotId = "snapshot-fk",
                    status = "LIVE",
                    previewAssetId = ASSET_PREVIEW_ID,
                    programId = PROGRAM_ID,
                ),
            )
            val missingSnapshotLink = runCatching {
                connection.execSQL(
                    """
                    INSERT INTO `$MESSAGE_VISUAL_TABLE`
                        (`message_id`, `ordinal`, `program_id`, `current_snapshot_id`, `revision`,
                         `created_at_epoch_millis`, `updated_at_epoch_millis`)
                    VALUES ('$FK_MESSAGE_ID', 0, '$PROGRAM_ID', 'snapshot-missing', 1,
                            1700000000000, 1700000000000)
                    """.trimIndent(),
                )
            }
            assertTrue(
                "不存在的快照不许被消息引用；${missingSnapshotLink.exceptionOrNull()}",
                missingSnapshotLink.exceptionOrNull() != null,
            )
            connection.execSQL(
                """
                INSERT INTO `$MESSAGE_VISUAL_TABLE`
                    (`message_id`, `ordinal`, `program_id`, `current_snapshot_id`, `revision`,
                     `created_at_epoch_millis`, `updated_at_epoch_millis`)
                VALUES ('$FK_MESSAGE_ID', 0, '$PROGRAM_ID', 'snapshot-fk', 1, 1700000000000, 1700000000000)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `$REVISION_VISUAL_TABLE`
                    (`problem_revision_id`, `role`, `ordinal`, `program_id`, `snapshot_id`,
                     `created_at_epoch_millis`)
                VALUES ('$REVISION_ID', 'QUESTION_DIAGRAM', 0, '$PROGRAM_ID', 'snapshot-fk',
                        1700000000000)
                """.trimIndent(),
            )

            // 4) 被引用的 program/snapshot/预览资产/题面都不许删（RESTRICT）。
            val blockedProgramDelete = runCatching {
                connection.execSQL("DELETE FROM `$PROGRAM_TABLE` WHERE `program_id` = '$PROGRAM_ID'")
            }
            assertTrue(
                "被快照引用的 program 不许删（RESTRICT）；${blockedProgramDelete.exceptionOrNull()}",
                blockedProgramDelete.exceptionOrNull() != null,
            )
            val blockedSnapshotDelete = runCatching {
                connection.execSQL("DELETE FROM `$SNAPSHOT_TABLE` WHERE `snapshot_id` = 'snapshot-fk'")
            }
            assertTrue(
                "被 link/题面引用的快照不许删（RESTRICT）；${blockedSnapshotDelete.exceptionOrNull()}",
                blockedSnapshotDelete.exceptionOrNull() != null,
            )
            val blockedPreviewAssetDelete = runCatching {
                connection.execSQL(
                    "DELETE FROM `canonical_source_asset` WHERE `source_asset_id` = '$ASSET_PREVIEW_ID'",
                )
            }
            assertTrue(
                "被快照预览引用的资产不许删（RESTRICT）；${blockedPreviewAssetDelete.exceptionOrNull()}",
                blockedPreviewAssetDelete.exceptionOrNull() != null,
            )
            val blockedRevisionDelete = runCatching {
                connection.execSQL(
                    "DELETE FROM `problem_revision` WHERE `revision_id` = '$REVISION_ID'",
                )
            }
            assertTrue(
                "被题面引用固定的修订不许删（RESTRICT）；${blockedRevisionDelete.exceptionOrNull()}",
                blockedRevisionDelete.exceptionOrNull() != null,
            )

            // 5) 删消息只级联删 link；program/snapshot/题面引用都还在。
            connection.execSQL("DELETE FROM `tutor_message` WHERE `message_id` = '$FK_MESSAGE_ID'")
            assertEquals(
                "消息删除 → link 级联删除",
                0L,
                tableRowCount(connection, MESSAGE_VISUAL_TABLE),
            )
            assertEquals(
                "program 不随会话/消息消失",
                1L,
                tableRowCount(connection, PROGRAM_TABLE),
            )
            assertEquals(
                "被题面引用固定的快照不随链接删除消失",
                1L,
                tableRowCount(connection, REVISION_VISUAL_TABLE),
            )

            // 清理本轮夹具（证明拦住删除的确实是 RESTRICT：先摘引用就删得掉）。
            connection.execSQL("DELETE FROM `$REVISION_VISUAL_TABLE` WHERE `snapshot_id` = 'snapshot-fk'")
            connection.execSQL("DELETE FROM `$SNAPSHOT_TABLE` WHERE `snapshot_id` = 'snapshot-fk'")
            connection.execSQL("DELETE FROM `$PROGRAM_TABLE` WHERE `program_id` = '$PROGRAM_ID'")
            assertEquals(0L, tableRowCount(connection, PROGRAM_TABLE))
        } finally {
            connection.close()
        }
    }

    // ---- 夹具 ----

    private fun seedV69Rows(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """
                INSERT INTO `canonical_source_asset` (
                    `source_asset_id`, `content_sha256`, `relative_path`, `mime_type`,
                    `byte_size`, `width`, `height`, `source_type`, `created_at_epoch_millis`
                ) VALUES (
                    '$ASSET_ID', '${"a".repeat(64)}', 'source-assets/batch30.jpg',
                    'image/jpeg', 4096, 1200, 1600, 'CLEAN_REDRAW', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `canonical_source_asset` (
                    `source_asset_id`, `content_sha256`, `relative_path`, `mime_type`,
                    `byte_size`, `width`, `height`, `source_type`, `created_at_epoch_millis`
                ) VALUES (
                    '$ASSET_PREVIEW_ID', '${"9".repeat(64)}', 'source-assets/batch30-preview.png',
                    'image/png', 2048, 720, 1280, 'CLEAN_REDRAW', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem` (
                    `problem_id`, `canonical_fingerprint`, `subject`, `created_at_epoch_millis`
                ) VALUES ('$PROBLEM_ID', 'fp-batch30', 'MATH', 1700000000000)
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
                    '$REVISION_ID', '$PROBLEM_ID', 1, 'v69 题面', '题面正文',
                    NULL, NULL, 'UNKNOWN',
                    'CAPTURE_CONFIRMED', NULL, 'fp-batch30-rev',
                    1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_revision_source_asset` (
                    `problem_revision_id`, `source_asset_id`, `role`
                ) VALUES ('$REVISION_ID', '$ASSET_ID', 'CLEAN_IMAGE')
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `tutor_conversation` (
                    `conversation_id`, `anchor_kind`, `status`,
                    `created_at_epoch_millis`, `updated_at_epoch_millis`, `last_turn_ordinal`
                ) VALUES ('$CONVERSATION_ID', 'PROBLEM', 'ACTIVE', 1700000000000, 1700000000000, 0)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `tutor_message` (
                    `message_id`, `conversation_id`, `ordinal`, `role`, `body_markdown`,
                    `status`, `created_at_epoch_millis`
                ) VALUES (
                    '$MESSAGE_ID', '$CONVERSATION_ID', 0, 'STUDENT', 'v69 学生附图',
                    'COMPLETED', 1700000000000
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `tutor_message_source_asset` (
                    `message_id`, `source_asset_id`, `ordinal`
                ) VALUES ('$MESSAGE_ID', '$ASSET_ID', 0)
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    private suspend fun linkMessage(
        dao: com.tingyun.smartmistakebook.core.database.dao.TutorVisualDao,
        context: Context,
        databaseName: String,
        snapshotId: String,
    ): com.tingyun.smartmistakebook.core.database.entity.TutorMessageVisualEntity {
        // 建一条新消息（link 需要已存在的宿主消息，CASCADE 才是可测的）。
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL(
                """
                INSERT OR IGNORE INTO `tutor_conversation` (
                    `conversation_id`, `anchor_kind`, `status`,
                    `created_at_epoch_millis`, `updated_at_epoch_millis`, `last_turn_ordinal`
                ) VALUES ('$CONVERSATION_ID', 'PROBLEM', 'ACTIVE', 1700000000000, 1700000000000, 0)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT OR IGNORE INTO `tutor_message` (
                    `message_id`, `conversation_id`, `ordinal`, `role`, `body_markdown`,
                    `status`, `created_at_epoch_millis`
                ) VALUES (
                    '$DAO_MESSAGE_ID', '$CONVERSATION_ID', 1, 'ASSISTANT', '图消息',
                    'COMPLETED', 1700000000000
                )
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
        return dao.linkMessageVisual(
            messageId = DAO_MESSAGE_ID,
            ordinal = 0,
            programId = PROGRAM_ID,
            snapshotId = snapshotId,
            uiStateJson = null,
            formDigest = null,
            nowEpochMillis = 1_700_000_000_000,
        )
    }

    private fun programEntity() = TutorVisualProgramEntity(
        programId = PROGRAM_ID,
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceSha256 = "b".repeat(64),
        sourceJs = "draw();",
        initialStateJson = "{}",
        initialStateSha256 = "c".repeat(64),
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
        basisRevisionId = REVISION_ID,
        basisFingerprint = "basis-batch30",
        requestSnapshot = null,
        createdAtEpochMillis = 1_700_000_000_000,
    )

    private fun snapshotEntity(snapshotId: String, status: String) = TutorVisualSnapshotEntity(
        snapshotId = snapshotId,
        programId = PROGRAM_ID,
        stateJson = "{}",
        // 内容寻址的近似：按 snapshotId 派生，保证两张快照不撞唯一索引 (program,state,draw)。
        stateSha256 = (snapshotId + "s".repeat(64)).take(64),
        sceneSchemaVersion = 1,
        sceneJson = "{}",
        sceneSha256 = "e".repeat(64),
        renderSchemaVersion = 1,
        drawCommandsJson = """{"ops":[]}""",
        drawCommandsSha256 = ("draw-" + snapshotId + "d".repeat(64)).take(64),
        contentIdsJson = "[]",
        viewportWidthDu = 360,
        viewportHeightDu = 640,
        fontScaleMilli = 1000,
        theme = "LIGHT",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = "visual-renderer-v1",
        fontManifestVersion = "font-manifest-v1",
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = null,
        status = status,
        ownerRequestId = "request-batch30",
        checkpointKind = "INITIAL",
        createdAtEpochMillis = 1_700_000_000_000,
        promotedAtEpochMillis = null,
    )

    private fun programInsertSql(): String = """
        INSERT INTO `$PROGRAM_TABLE` (
            `program_id`, `learner_id`, `role`, `source_sha256`, `source_js`,
            `initial_state_json`, `initial_state_sha256`, `api_version`, `sdk_version`,
            `basis_revision_id`, `basis_fingerprint`, `request_snapshot`,
            `created_at_epoch_millis`
        ) VALUES (
            '$PROGRAM_ID', '$LEARNER_ID', 'QUESTION_DIAGRAM', '${"b".repeat(64)}', 'draw();',
            '{}', '${"c".repeat(64)}', 1, 'visual-sdk-v1',
            '$REVISION_ID', 'basis-batch30', NULL, 1700000000000
        )
    """.trimIndent()

    private fun snapshotInsertSql(
        snapshotId: String,
        status: String,
        previewAssetId: String?,
        programId: String,
    ): String {
        val preview = previewAssetId?.let { "'$it'" } ?: "NULL"
        return """
            INSERT INTO `$SNAPSHOT_TABLE` (
                `snapshot_id`, `program_id`, `state_json`, `state_sha256`,
                `scene_schema_version`, `scene_json`, `scene_sha256`, `render_schema_version`,
                `draw_commands_json`, `draw_commands_sha256`, `content_ids_json`,
                `viewport_width_du`, `viewport_height_du`, `font_scale_milli`, `theme`,
                `time_ms`, `seed`, `runtime_version`, `compiler_version`, `renderer_version`,
                `font_manifest_version`, `question_verification_status`, `preview_asset_id`,
                `status`, `owner_request_id`, `checkpoint_kind`,
                `created_at_epoch_millis`, `promoted_at_epoch_millis`
            ) VALUES (
                '$snapshotId', '$programId', '{}', '${"d".repeat(64)}',
                1, '{}', '${"e".repeat(64)}', 1,
                '{"ops":[]}', '${"f".repeat(64)}', '[]',
                360, 640, 1000, 'LIGHT',
                0, 0, 'quickjs-test', 'visual-compiler-v1', 'visual-renderer-v1',
                'font-manifest-v1', 'NOT_CHECKED', $preview,
                '$status', 'request-batch30', 'INITIAL',
                1700000000000, NULL
            )
        """.trimIndent()
    }

    private fun countRowsIn(context: Context, databaseName: String, table: String): Long {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            return tableRowCount(connection, table)
        } finally {
            connection.close()
        }
    }

    private fun countAllVisualRows(context: Context, databaseName: String): Long {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            return VISUAL_COLUMNS.keys.sumOf { tableRowCount(connection, it) }
        } finally {
            connection.close()
        }
    }

    // ---- PRAGMA 读取 ----

    private fun tableNames(connection: SQLiteConnection): Set<String> {
        val statement = connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
        try {
            val names = mutableSetOf<String>()
            while (statement.step()) names += statement.getText(0)!!
            return names
        } finally {
            statement.close()
        }
    }

    private fun columnNames(connection: SQLiteConnection, table: String): List<String> {
        val statement = connection.prepare("PRAGMA table_info(`$table`)")
        try {
            val names = mutableListOf<String>()
            while (statement.step()) names += statement.getText(1)!!
            return names
        } finally {
            statement.close()
        }
    }

    private fun indexNames(connection: SQLiteConnection, table: String): Set<String> {
        val statement = connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?",
        )
        try {
            statement.bindText(1, table)
            val names = mutableSetOf<String>()
            while (statement.step()) {
                val name = statement.getText(0)!!
                if (!name.startsWith("sqlite_autoindex")) names += name
            }
            return names
        } finally {
            statement.close()
        }
    }

    private fun indexSql(connection: SQLiteConnection, indexName: String): String? {
        val statement = connection.prepare(
            "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?",
        )
        try {
            statement.bindText(1, indexName)
            return if (statement.step()) statement.getText(0) else null
        } finally {
            statement.close()
        }
    }

    private fun foreignKeys(
        connection: SQLiteConnection,
        table: String,
    ): List<Triple<String, String, String>> {
        val statement = connection.prepare("PRAGMA foreign_key_list(`$table`)")
        try {
            val rows = mutableListOf<Triple<String, String, String>>()
            while (statement.step()) {
                rows += Triple(statement.getText(2)!!, statement.getText(3)!!, statement.getText(6)!!)
            }
            return rows
        } finally {
            statement.close()
        }
    }

    private fun tableRowCount(connection: SQLiteConnection, table: String): Long {
        val statement = connection.prepare("SELECT COUNT(*) FROM `$table`")
        try {
            assertTrue("COUNT(*) 必须有结果", statement.step())
            return statement.getLong(0)
        } finally {
            statement.close()
        }
    }

    private companion object {
        const val PROGRAM_TABLE = "tutor_visual_program"
        const val SNAPSHOT_TABLE = "tutor_visual_snapshot"
        const val MESSAGE_VISUAL_TABLE = "tutor_message_visual"
        const val REVISION_VISUAL_TABLE = "problem_revision_visual"
        const val SNAPSHOT_CONTENT_INDEX =
            "index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256"

        const val LEARNER_ID = "learner-batch30"
        const val PROGRAM_ID = "visual-program-batch30"
        const val MESSAGE_ID = "message-batch30"
        const val FK_MESSAGE_ID = "message-batch30-fk"
        const val DAO_MESSAGE_ID = "message-batch30-dao"
        const val CONVERSATION_ID = "conversation-batch30"
        const val PROBLEM_ID = "problem-batch30"
        const val REVISION_ID = "revision-batch30"
        const val ASSET_ID = "asset-batch30"
        const val ASSET_PREVIEW_ID = "asset-batch30-preview"
        const val SNAPSHOT_STAGING = "snapshot-batch30-staging"
        const val SNAPSHOT_NEXT = "snapshot-batch30-next"

        val VISUAL_COLUMNS = linkedMapOf(
            PROGRAM_TABLE to listOf(
                "program_id", "learner_id", "role", "source_sha256", "source_js",
                "initial_state_json", "initial_state_sha256", "api_version", "sdk_version",
                "basis_revision_id", "basis_fingerprint", "request_snapshot",
                "created_at_epoch_millis",
            ),
            SNAPSHOT_TABLE to listOf(
                "snapshot_id", "program_id", "state_json", "state_sha256",
                "scene_schema_version", "scene_json", "scene_sha256", "render_schema_version",
                "draw_commands_json", "draw_commands_sha256", "content_ids_json",
                "viewport_width_du", "viewport_height_du", "font_scale_milli", "theme",
                "time_ms", "seed", "runtime_version", "compiler_version", "renderer_version",
                "font_manifest_version", "question_verification_status", "preview_asset_id",
                "status", "owner_request_id", "checkpoint_kind",
                "created_at_epoch_millis", "promoted_at_epoch_millis",
            ),
            MESSAGE_VISUAL_TABLE to listOf(
                "message_id", "ordinal", "program_id", "current_snapshot_id", "revision",
                "ui_state_json", "form_digest", "created_at_epoch_millis",
                "updated_at_epoch_millis",
            ),
            REVISION_VISUAL_TABLE to listOf(
                "problem_revision_id", "role", "ordinal", "program_id", "snapshot_id",
                "created_at_epoch_millis",
            ),
        )

        val VISUAL_INDICES = linkedMapOf(
            PROGRAM_TABLE to setOf(
                "index_tutor_visual_program_learner_id_created_at_epoch_millis",
                "index_tutor_visual_program_basis_revision_id",
                "index_tutor_visual_program_source_sha256",
            ),
            SNAPSHOT_TABLE to setOf(
                SNAPSHOT_CONTENT_INDEX,
                "index_tutor_visual_snapshot_program_id_promoted_at_epoch_millis",
                "index_tutor_visual_snapshot_preview_asset_id",
                "index_tutor_visual_snapshot_status_created_at_epoch_millis",
            ),
            MESSAGE_VISUAL_TABLE to setOf(
                "index_tutor_message_visual_program_id",
                "index_tutor_message_visual_current_snapshot_id",
            ),
            REVISION_VISUAL_TABLE to setOf(
                "index_problem_revision_visual_program_id",
                "index_problem_revision_visual_snapshot_id",
            ),
        )
    }
}
