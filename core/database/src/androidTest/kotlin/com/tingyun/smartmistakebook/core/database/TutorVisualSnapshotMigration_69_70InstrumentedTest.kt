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
 * 1. 四张新表与索引按迁移建出来（列序/索引名/外键形态与实体声明逐位同形）。快照表的索引形状
 *    按**当前版本**（v71）判定：v69→70 建的三列键会被 70→71 换成五列键（见下方"版本边界"）。
 *    **形状判定只读结构读数，不读 `sqlite_master.sql` 的 DDL 文本**：列序来自 `PRAGMA
 *    table_info`，索引名/唯一性来自 `PRAGMA index_list`，索引键列序来自 `PRAGMA
 *    index_info`，外键来自 `PRAGMA foreign_key_list`。SQLite 存进 `sqlite_master.sql`
 *    的文本会去掉 `IF NOT EXISTS`（批次 30 设备实测），拿它做文本比较必然假红；
 * 2. **旧行一行不动**：v69 的资产/题/修订/修订附图/会话/消息/消息附图原样读回；
 * 3. **新表为空**——迁移不回填历史程序/快照（"无快照可考"如实呈现，不编造）；
 * 4. **外键负向**：缺父行写不进、被引用的父行删不掉（RESTRICT）、消息删了 link 级联走；
 * 5. 再开一次（迁移已是 no-op）形态与行不变——迁移幂等。
 *
 * 版本边界：本用例把导出的 v69 库打开到 `STUDY_DATABASE_VERSION`（当前 71），因此实际连跑
 * 69→70 **与** 70→71。schema 71（批次 32 / S1）把快照唯一键从三列换成五列，所以"活库形状"
 * 的期望在本文件已同步为 **v71 形状**（[CURRENT_INDEX_KEYS]）：旧三列索引必须消失、新五列
 * 唯一索引必须在。`indexExpectationsStayInSyncWithTheMigrationDdlConstants` 仍只对 v69→70 的
 * `TUTOR_VISUAL_*_DDL_69_70` 常量做同源核对（那批 DDL 是历史事实，不改写）。
 * **不得**为了让套件变绿而把这里的索引期望放宽回三列。
 *
 * 另有一条 DAO 用例钉"只有 LIVE 快照可被消息引用"的 **SQL 级结构门**与指针 CAS 的
 * "命中行数判赢"（`TutorVisualDao` KDoc 的三条门）。
 *
 * **断言与迁移常量的同源关系**：索引名、键列序、唯一性不是另抄一份期望就完事——
 * `indexExpectationsStayInSyncWithTheMigrationDdlConstants` 把期望集合与
 * `TUTOR_VISUAL_*_DDL_69_70` 常量（`VISUAL_SNAPSHOT_MIGRATION_69_70` 真正执行的 15 条
 * 语句）双向对齐：迁移改了索引而期望没跟、或期望多写/少写一条，这条先红。表列序的权威
 * 侧是实体 `@Entity` 声明，由 JVM 的 `TutorVisualSchemaContractTest` 与导出的 `70.json`
 * 逐字比对钉住，本用例只负责真库读数与之同形。
 *
 * **断言自身可失败（负控）**：`indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex`
 * 在临时库里造"列序错位 / 非唯一 / 不存在"三种索引，断言同一组读取器与判定对它们给假；
 * 若判定退化成恒真，这条负控先红。这里**不提交一条故意写错的红色用例**：那会让套件
 * 永久红，且无法区分"断言确实能红"与"产品坏了"两种红，故以通过的负控表达同一事实。
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

            // 执行顺序 = 证据优先级：先数据保全、再外键行为、最后形状。批次 30 的红发生在
            // 形状断言，结果外键负向与旧行保持一条都没跑到——现在行为证据先落袋。
            inspectV70DataPreservation(context, databaseName)
            assertV70ForeignKeyBehaviour(context, databaseName)
            inspectCurrentShape(context, databaseName)

            // 幂等：同一库再开一次（迁移已是 no-op）不得改变形态、旧行或新表的空态。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(STUDY_DATABASE_VERSION, reopened.readDatabaseVersion())
            reopened.close()
            inspectV70DataPreservation(context, databaseName)
            inspectCurrentShape(context, databaseName)
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
                "同内容同版本再写一次回读的是既有行（INV-6：唯一索引幂等）",
                dao.findSnapshotByContent(
                    PROGRAM_ID,
                    staging.stateSha256,
                    staging.drawCommandsSha256,
                    staging.rendererVersion,
                    staging.fontManifestVersion,
                ),
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

    /**
     * **断言与迁移常量的同源关系（可执行版）**。形状用例读的是真库的 `PRAGMA` 结构读数，
     * 但"应该长什么样"不是凭记忆抄的：索引名、键列序、唯一性必须与迁移真正执行的
     * [MIGRATION_INDEX_DDL] 双向对齐。迁移改了索引而这里没跟 → 先红；这里多写/少写一条
     * 索引 → 也红（两个方向都关死）。
     *
     * 只解析迁移常量（源码里的 DDL 字符串是 `VISUAL_SNAPSHOT_MIGRATION_69_70` 直接执行的
     * 对象），**不解析 `sqlite_master.sql`**——那是数据库运行后的归一化副本，正是批次 30
     * 假红的来源。
     */
    @Test
    fun indexExpectationsStayInSyncWithTheMigrationDdlConstants() {
        assertEquals("迁移恰好执行 11 条索引 DDL", 11, MIGRATION_INDEX_DDL.size)
        assertEquals(
            "声明的索引名集合与迁移常量逐名一致（少一条/多一条都红）",
            MIGRATION_INDEX_DDL.map { indexNameInDdl(it) }.toSet(),
            VISUAL_INDEX_KEYS.values.flatMap { it.keys }.toSet(),
        )
        MIGRATION_INDEX_DDL.forEach { ddl ->
            val indexName = indexNameInDdl(ddl)
            val table = tableNameInDdl(ddl)
            val expectedColumns = VISUAL_INDEX_KEYS.getValue(table).getValue(indexName)
            assertEquals(
                "索引 $indexName 的期望键列序必须与迁移 DDL 的列元组逐位一致；DDL=$ddl",
                expectedColumns,
                columnsInDdl(ddl),
            )
            assertEquals(
                "索引 $indexName 的唯一性必须与 DDL 的 UNIQUE 关键字一致；DDL=$ddl",
                ddl.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS "),
                indexName in UNIQUE_INDICES,
            )
        }
    }

    /**
     * **断言自身可失败（负控）**。在临时库里造三种"看起来像、其实不是"的索引，用与
     * [inspectCurrentShape] 完全相同的读取器与判定比较：
     *
     * - 列序错位（`state_sha256, program_id`）必须判不等；
     * - 非唯一索引不得冒充唯一；
     * - 不存在的索引读出来是空列，必须判不等；
     * - 索引集合多一条/少一条都必须判不等。
     *
     * 若任一判定退化成恒真，这条负控先红。**故意写错列序的红色用例没有提交**：套件会因此
     * 永久红，且分不清"断言确实能红"与"产品坏了"，所以用这条通过的负控表达同一事实。
     */
    @Test
    fun indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch31-visual-index-negative-control-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE `shape_probe` (" +
                    "`program_id` TEXT NOT NULL, `state_sha256` TEXT NOT NULL, " +
                    "`draw_commands_sha256` TEXT NOT NULL)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX `shape_probe_matching` ON `shape_probe` " +
                    "(`program_id`, `state_sha256`, `draw_commands_sha256`)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX `shape_probe_wrong_order` ON `shape_probe` " +
                    "(`state_sha256`, `program_id`)",
            )
            connection.execSQL(
                "CREATE INDEX `shape_probe_not_unique` ON `shape_probe` " +
                    "(`program_id`, `state_sha256`, `draw_commands_sha256`)",
            )

            // 正例：同一组读取器/判定对"对的形状"必须给真（否则负控没有意义）。
            assertEquals(CONTENT_INDEX_COLUMNS, indexKeyColumns(connection, "shape_probe_matching"))
            assertTrue(indexIsUnique(connection, "shape_probe", "shape_probe_matching"))

            // 负控 1：错列序必须判不等（列序是唯一键的匹配顺序，交换后语义不同）。
            assertFalse(
                "错列序不得被判绿；实际=" + indexKeyColumns(connection, "shape_probe_wrong_order"),
                CONTENT_INDEX_COLUMNS == indexKeyColumns(connection, "shape_probe_wrong_order"),
            )
            // 负控 2：非唯一索引不得被判成 UNIQUE。
            assertFalse(indexIsUnique(connection, "shape_probe", "shape_probe_not_unique"))
            // 负控 3：不存在的索引读出来是空列，绝不能被当成"列都对"。
            assertTrue(indexKeyColumns(connection, "shape_probe_missing").isEmpty())
            assertFalse(
                "缺失索引不得被判绿",
                CONTENT_INDEX_COLUMNS == indexKeyColumns(connection, "shape_probe_missing"),
            )
            // 负控 4：索引集合逐个名字比，多一条也不算相等。
            assertFalse(
                "索引集合必须逐名判定；实际=" + indexNames(connection, "shape_probe"),
                indexNames(connection, "shape_probe") ==
                    setOf("shape_probe_matching", "shape_probe_wrong_order"),
            )
        } finally {
            connection.close()
            context.deleteDatabase(databaseName)
        }
    }

    // ---- 真库数据保全（排在最前：形状断言红了也不能掩盖“旧行还在吗”） ----

    private fun inspectV70DataPreservation(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            assertOldV69RowsPreserved(connection)
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

    // ---- 真库形状 ----

    private fun inspectCurrentShape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val tables = tableNames(connection)

            // 形状：表 + 列序（PRAGMA table_info，按实体声明顺序）。
            VISUAL_COLUMNS.forEach { (table, columns) ->
                assertTrue("迁移必须把 $table 建出来", table in tables)
                assertEquals("$table 列与实体声明逐位同形", columns, columnNames(connection, table))
            }

            // 索引：名字集合（PRAGMA index_list）、键列序（PRAGMA index_info）、唯一性
            // （PRAGMA index_list 的 unique 列）。三者都是结构读数，不碰 sqlite_master.sql。
            // 期望是**当前**（v71）形状：v69→70 建的三列唯一键已被 70→71 换掉。
            CURRENT_INDEX_KEYS.forEach { (table, expectedIndexKeys) ->
                assertEquals(
                    "$table 的索引一条不少、不多一条",
                    expectedIndexKeys.keys,
                    indexNames(connection, table),
                )
                expectedIndexKeys.forEach { (indexName, expectedColumns) ->
                    assertEquals(
                        "索引 $indexName 的键列序必须与实体声明逐位一致（index_info 的 seqno 顺序）",
                        expectedColumns,
                        indexKeyColumns(connection, indexName),
                    )
                    assertEquals(
                        "索引 $indexName 的唯一性（UNIQUE 索引只该有一条：内容+版本唯一键）",
                        indexName in CURRENT_UNIQUE_INDICES,
                        indexIsUnique(connection, table, indexName),
                    )
                }
            }

            assertFalse(
                "v70 的三列内容键必须被 70→71 删除（留着会让新版本撞旧键）",
                SNAPSHOT_CONTENT_INDEX_V70 in indexNames(connection, SNAPSHOT_TABLE),
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
        } finally {
            connection.close()
        }
    }

    /** 旧行一行不动：v69 的资产/题面修订/消息原样读回（多一行少一行都红）。 */
    private fun assertOldV69RowsPreserved(connection: SQLiteConnection) {
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

    /** 表上的显式索引名（`PRAGMA index_list`；排除主键/唯一约束自建的 `sqlite_autoindex_*`）。 */
    private fun indexNames(connection: SQLiteConnection, table: String): Set<String> {
        val statement = connection.prepare("PRAGMA index_list(`$table`)")
        try {
            val names = mutableSetOf<String>()
            while (statement.step()) {
                val name = statement.getText(1)!!
                if (!name.startsWith("sqlite_autoindex")) names += name
            }
            return names
        } finally {
            statement.close()
        }
    }

    /** 索引的键列（`PRAGMA index_info` 的 seqno 顺序）；索引不存在时为空列表。 */
    private fun indexKeyColumns(connection: SQLiteConnection, indexName: String): List<String> {
        val statement = connection.prepare("PRAGMA index_info(`$indexName`)")
        try {
            val columns = mutableListOf<String>()
            while (statement.step()) columns += statement.getText(2)!!
            return columns
        } finally {
            statement.close()
        }
    }

    /** 索引是否 UNIQUE（`PRAGMA index_list` 的 unique 列）；索引不存在时 false。 */
    private fun indexIsUnique(
        connection: SQLiteConnection,
        table: String,
        indexName: String,
    ): Boolean {
        val statement = connection.prepare("PRAGMA index_list(`$table`)")
        try {
            while (statement.step()) {
                if (statement.getText(1) == indexName) return statement.getLong(2) == 1L
            }
            return false
        } finally {
            statement.close()
        }
    }

    // ---- 迁移常量解析（同源核对；只解析源码常量，不解析 sqlite_master） ----

    private fun indexNameInDdl(ddl: String): String =
        ddl.substringAfter("INDEX IF NOT EXISTS `").substringBefore('`')

    private fun tableNameInDdl(ddl: String): String =
        ddl.substringAfter(" ON `").substringBefore('`')

    private fun columnsInDdl(ddl: String): List<String> =
        ddl.substringAfterLast('(').removeSuffix(")").split(", ").map { it.trim('`') }

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

        /** v71 的五列内容+版本唯一键（`TutorVisualSnapshotEntity` 的显式索引名）。 */
        const val SNAPSHOT_CONTENT_RENDER_INDEX =
            "index_tutor_visual_snapshot_content_render_identity"

        /** v70 的三列内容键名（70→71 必须 DROP 掉它）。 */
        const val SNAPSHOT_CONTENT_INDEX_V70 = SNAPSHOT_CONTENT_INDEX

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

        /** 内容唯一键的三列（v69→70 DDL 的匹配顺序，不可交换）。 */
        val CONTENT_INDEX_COLUMNS = listOf("program_id", "state_sha256", "draw_commands_sha256")

        /** v71 的内容+渲染版本键五列（顺序不可交换）。 */
        val CONTENT_RENDER_INDEX_COLUMNS = listOf(
            "program_id",
            "state_sha256",
            "draw_commands_sha256",
            "renderer_version",
            "font_manifest_version",
        )

        /**
         * 11 条 **v69→70** 索引里唯一带 UNIQUE 的一条：同 program 同内容只存一份。
         * 只服务 `indexExpectationsStayInSyncWithTheMigrationDdlConstants`（历史 DDL 同源核对）。
         */
        val UNIQUE_INDICES = setOf(SNAPSHOT_CONTENT_INDEX)

        /** 迁移到当前版本后快照表的唯一索引：v71 的五列键。 */
        val CURRENT_UNIQUE_INDICES = setOf(SNAPSHOT_CONTENT_RENDER_INDEX)

        /**
         * 每条索引的期望键列序（= 实体 `@Index` 声明顺序 = 迁移 DDL 的列元组顺序）。
         * 与 [MIGRATION_INDEX_DDL] 由 `indexExpectationsStayInSyncWithTheMigrationDdlConstants`
         * 双向对齐：这里是"期望"，那里是迁移真正执行的语句。
         */
        val VISUAL_INDEX_KEYS = linkedMapOf(
            PROGRAM_TABLE to linkedMapOf(
                "index_tutor_visual_program_learner_id_created_at_epoch_millis" to
                    listOf("learner_id", "created_at_epoch_millis"),
                "index_tutor_visual_program_basis_revision_id" to listOf("basis_revision_id"),
                "index_tutor_visual_program_source_sha256" to listOf("source_sha256"),
            ),
            SNAPSHOT_TABLE to linkedMapOf(
                SNAPSHOT_CONTENT_INDEX to CONTENT_INDEX_COLUMNS,
                "index_tutor_visual_snapshot_program_id_promoted_at_epoch_millis" to
                    listOf("program_id", "promoted_at_epoch_millis"),
                "index_tutor_visual_snapshot_preview_asset_id" to listOf("preview_asset_id"),
                "index_tutor_visual_snapshot_status_created_at_epoch_millis" to
                    listOf("status", "created_at_epoch_millis"),
            ),
            MESSAGE_VISUAL_TABLE to linkedMapOf(
                "index_tutor_message_visual_program_id" to listOf("program_id"),
                "index_tutor_message_visual_current_snapshot_id" to listOf("current_snapshot_id"),
            ),
            REVISION_VISUAL_TABLE to linkedMapOf(
                "index_problem_revision_visual_program_id" to listOf("program_id"),
                "index_problem_revision_visual_snapshot_id" to listOf("snapshot_id"),
            ),
        )

        /**
         * 迁移到当前版本（v71）后的索引期望：快照表那组换成 70→71 之后的四条（五列唯一键 +
         * 三条查询索引）；其余三张表不受 70→71 影响。活库形状判定用这一份，
         * `VISUAL_INDEX_KEYS` 只留给 v69→70 的 DDL 同源核对。
         */
        val CURRENT_INDEX_KEYS = VISUAL_INDEX_KEYS + (
            SNAPSHOT_TABLE to linkedMapOf(
                SNAPSHOT_CONTENT_RENDER_INDEX to CONTENT_RENDER_INDEX_COLUMNS,
                "index_tutor_visual_snapshot_program_id_promoted_at_epoch_millis" to
                    listOf("program_id", "promoted_at_epoch_millis"),
                "index_tutor_visual_snapshot_preview_asset_id" to listOf("preview_asset_id"),
                "index_tutor_visual_snapshot_status_created_at_epoch_millis" to
                    listOf("status", "created_at_epoch_millis"),
            )
        )

        /**
         * 迁移真正执行的 11 条索引 DDL（`VISUAL_SNAPSHOT_MIGRATION_69_70` 里的原文引用）。
         * 期望值同源核对的另一侧就是它——不是手抄的索引清单。
         */
        val MIGRATION_INDEX_DDL = listOf(
            TUTOR_VISUAL_PROGRAM_INDEX_LEARNER_DDL_69_70,
            TUTOR_VISUAL_PROGRAM_INDEX_BASIS_DDL_69_70,
            TUTOR_VISUAL_PROGRAM_INDEX_SOURCE_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_CONTENT_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_LATEST_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_PREVIEW_DDL_69_70,
            TUTOR_VISUAL_SNAPSHOT_INDEX_STATUS_DDL_69_70,
            TUTOR_MESSAGE_VISUAL_INDEX_PROGRAM_DDL_69_70,
            TUTOR_MESSAGE_VISUAL_INDEX_SNAPSHOT_DDL_69_70,
            PROBLEM_REVISION_VISUAL_INDEX_PROGRAM_DDL_69_70,
            PROBLEM_REVISION_VISUAL_INDEX_SNAPSHOT_DDL_69_70,
        )
    }
}
