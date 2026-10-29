package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualSnapshotStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **schema 71（批次 32 / S1）的真库验收**：快照唯一键并入 `renderer_version` 与
 * `font_manifest_version`。
 *
 * 矩阵（`FullMigrationMatrixInstrumentedTest`）用的是空库，看不出"旧行还在不在、旧索引是否
 * 真被换掉、新版本是否真的能落成第二行"，所以这里用**导出的 70.json 建库 + 真驱动 + 真 Room**：
 *
 * 1. **迁移 70→71**：版本到 71；v70 的三列内容键消失；五列唯一键在（列序/唯一性走 PRAGMA
 *    结构读数，不读 `sqlite_master.sql` 文本）；再开一次形态不变（迁移幂等）；
 * 2. **旧行保持**：v70 落下的快照行一行不丢、内容逐列原样（迁移不回填、不改历史 id）；
 * 3. **身份负向（本批要消灭的失败）**：同 program + 同 state + 同 draw，只把 `rendererVersion`
 *    bump 一档 → **新增一行**且 id 不同，不是"静默复用旧行"，也不是冲突；同内容同版本仍复用
 *    既有行（幂等不退化）；五列全同的第二次插入被唯一键拒绝。
 *
 * 关于旧 id：v70 的 `snapshotId` 是三段无前缀摘要（[legacySnapshotId] 的冻结副本）。迁移
 * **不重算**存量行的 id——PK 不变仍合法；新写入的五段 id 与旧 id 不同是预期事实，所以"复用
 * 旧行"只能靠五列内容查重（`findSnapshotByContent`），不是靠 id 相等。
 *
 * 本轨按批次纪律**不运行 Gradle**；门禁脚本串行执行 `assembleDebugAndroidTest` +
 * `adb install -r -t` + `am instrument -e class …`。批次 32 E-04 已在设备上实跑本套件：
 * **`OK (3 tests)`**（`build/device-track-batch32/step04-migration-70-71.log`）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest {

    @Test
    fun v70SnapshotRowSurvivesMigrationAndTheContentKeyBecomesFiveColumns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch32-visual-identity-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 70)
            seedV70ProgramAndSnapshot(context, databaseName)

            // v70 的形态是本迁移的前提：三列唯一键在、五列键不在。
            val before = readSnapshotIndexes(context, databaseName)
            assertTrue("v70 必须有旧的三列内容键", SNAPSHOT_CONTENT_INDEX_V70 in before.keys)
            assertFalse("v70 不该有 71 的五列键", SNAPSHOT_IDENTITY_INDEX in before.keys)

            val store = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(
                    "Room 是惰性打开：先读版本强制它把 v70 库迁到 71",
                    STUDY_DATABASE_VERSION,
                    store.readDatabaseVersion(),
                )
                val preserved = store.readTutorVisualSnapshot(LEGACY_SNAPSHOT_ID)
                assertNotNull("旧行必须一行不丢（迁移不重算、不重建）", preserved)
                assertEquals("旧行 id 原样（不按新公式回填）", LEGACY_SNAPSHOT_ID, preserved!!.snapshotId)
                assertEquals("旧行内容原样", DRAW_COMMANDS_JSON, preserved.drawCommandsJson)
                assertEquals("旧行 digest 原样", DRAW_SHA256, preserved.drawCommandsSha256)
                assertEquals("旧行渲染版本原样", RENDERER_V1, preserved.rendererVersion)
                assertEquals("旧行字体版本原样", FONT_V1, preserved.fontManifestVersion)
                assertEquals("旧行状态原样（STAGING 不被迁移提升）", TutorVisualSnapshotStatus.STAGING, preserved.status)

                val dao = (store as RoomStudyDatabase).database.tutorVisualDao()
                assertEquals("迁移不复制/不回填：该 program 仍只有一行", 1, dao.countSnapshots(PROGRAM_ID))
            } finally {
                store.close()
            }

            val after = readSnapshotIndexes(context, databaseName)
            assertFalse(
                "v70 的三列内容键必须被 DROP（留着会让新版本撞旧键）",
                SNAPSHOT_CONTENT_INDEX_V70 in after.keys,
            )
            assertEquals(
                "快照表索引集合 = 五列唯一键 + 三条原样保留的查询索引",
                setOf(
                    SNAPSHOT_IDENTITY_INDEX,
                    "index_tutor_visual_snapshot_program_id_promoted_at_epoch_millis",
                    "index_tutor_visual_snapshot_preview_asset_id",
                    "index_tutor_visual_snapshot_status_created_at_epoch_millis",
                ),
                after.keys,
            )
            assertEquals(
                "五列键的列序必须与实体声明逐位一致",
                CONTENT_RENDER_INDEX_COLUMNS,
                after.getValue(SNAPSHOT_IDENTITY_INDEX).columns,
            )
            assertTrue(
                "五列键必须是 UNIQUE",
                after.getValue(SNAPSHOT_IDENTITY_INDEX).unique,
            )

            // 幂等：同一库再开一次（迁移已是 no-op）不得改变形态或旧行。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(STUDY_DATABASE_VERSION, reopened.readDatabaseVersion())
                assertNotNull(reopened.readTutorVisualSnapshot(LEGACY_SNAPSHOT_ID))
                assertEquals(
                    after.keys,
                    readSnapshotIndexes(context, databaseName).keys,
                )
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun bumpedRendererVersionCreatesASecondSnapshotInsteadOfReusingTheV70Row() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch32-visual-identity-negative-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 70)
            seedV70ProgramAndSnapshot(context, databaseName)

            val store = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(STUDY_DATABASE_VERSION, store.readDatabaseVersion())
                val dao = (store as RoomStudyDatabase).database.tutorVisualDao()

                // 1) 同内容同版本：五列查重回读既有 v70 行（幂等不退化）。
                assertNotNull(
                    "同内容同版本必须命中既有行",
                    dao.findSnapshotByContent(
                        PROGRAM_ID,
                        STATE_SHA256,
                        DRAW_SHA256,
                        RENDERER_V1,
                        FONT_V1,
                    ),
                )
                val sameVersion = store.stageTutorVisualSnapshot(
                    StageTutorVisualSnapshotCommand(
                        program = programWrite(),
                        snapshot = snapshotWrite(RENDERER_V1),
                        ownerRequestId = OWNER_NEW,
                    ),
                )
                assertEquals(
                    "同内容同版本不许产生第二行；查重按五列而不是按 id",
                    LEGACY_SNAPSHOT_ID,
                    sameVersion.snapshotId,
                )
                assertNotEquals(
                    "五段派生 id 与 v70 的三段 id 本来就不相等（所以才必须按内容查重）",
                    NEW_SNAPSHOT_ID_V1,
                    LEGACY_SNAPSHOT_ID,
                )
                assertEquals(1, dao.countSnapshots(PROGRAM_ID))

                // 2) 只 bump rendererVersion：另一行，不是"复用旧行"，也不是冲突。
                val bumped = store.stageTutorVisualSnapshot(
                    StageTutorVisualSnapshotCommand(
                        program = programWrite(),
                        snapshot = snapshotWrite(RENDERER_V2),
                        ownerRequestId = OWNER_NEW,
                    ),
                )
                assertEquals("新版本快照必须是另一行", NEW_SNAPSHOT_ID_V2, bumped.snapshotId)
                assertEquals("两行并存", 2, dao.countSnapshots(PROGRAM_ID))
                assertEquals(
                    "新行的渲染版本是 bump 后的值（旧行不被覆盖）",
                    RENDERER_V2,
                    store.readTutorVisualSnapshot(bumped.snapshotId)?.rendererVersion,
                )
                assertEquals(
                    "旧行的渲染版本原样",
                    RENDERER_V1,
                    store.readTutorVisualSnapshot(LEGACY_SNAPSHOT_ID)?.rendererVersion,
                )

                // 3) 五列查重各自命中自己的行（WHERE 的列集必须与唯一键同形）。
                assertEquals(
                    LEGACY_SNAPSHOT_ID,
                    dao.findSnapshotByContent(PROGRAM_ID, STATE_SHA256, DRAW_SHA256, RENDERER_V1, FONT_V1)
                        ?.snapshotId,
                )
                assertEquals(
                    NEW_SNAPSHOT_ID_V2,
                    dao.findSnapshotByContent(PROGRAM_ID, STATE_SHA256, DRAW_SHA256, RENDERER_V2, FONT_V1)
                        ?.snapshotId,
                )

                // 4) 五列全同的第二次插入必须被唯一键拒绝（不是靠代码 review 去重）。
                val duplicate = runCatching {
                    insertRawSnapshot(
                        context = context,
                        databaseName = databaseName,
                        snapshotId = "visual-snapshot-duplicate-five-columns",
                        rendererVersion = RENDERER_V2,
                    )
                }
                assertTrue(
                    "五列全同必须 UNIQUE 失败；${duplicate.exceptionOrNull()}",
                    duplicate.exceptionOrNull() != null,
                )
                assertEquals("被拒不产生第三行", 2, dao.countSnapshots(PROGRAM_ID))
            } finally {
                store.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * **负控**：索引形状判定必须能对"错列序 / 非唯一 / 缺失"给假。
     *
     * 只提交通过的负控、不提交一条故意写错的红色用例：后者会让套件永久红，且分不清
     * "断言确实能红"与"产品坏了"（与 v69→70 用例的同一纪律）。
     */
    @Test
    fun indexShapeAssertionsCanFailOnWrongColumnOrderNonUniqueAndMissingIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch32-visual-identity-negative-control-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE `probe_snapshot` (" +
                    "`program_id` TEXT NOT NULL, `state_sha256` TEXT NOT NULL, " +
                    "`draw_commands_sha256` TEXT NOT NULL, `renderer_version` TEXT NOT NULL, " +
                    "`font_manifest_version` TEXT NOT NULL)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX `probe_matching` ON `probe_snapshot` " +
                    "(`program_id`, `state_sha256`, `draw_commands_sha256`, " +
                    "`renderer_version`, `font_manifest_version`)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX `probe_wrong_order` ON `probe_snapshot` " +
                    "(`program_id`, `state_sha256`, `draw_commands_sha256`, " +
                    "`font_manifest_version`, `renderer_version`)",
            )
            connection.execSQL(
                "CREATE INDEX `probe_not_unique` ON `probe_snapshot` " +
                    "(`program_id`, `state_sha256`, `draw_commands_sha256`, " +
                    "`renderer_version`, `font_manifest_version`)",
            )

            assertEquals(CONTENT_RENDER_INDEX_COLUMNS, indexKeyColumns(connection, "probe_matching"))
            assertTrue(indexIsUnique(connection, "probe_snapshot", "probe_matching"))

            assertFalse(
                "版本列交换顺序后语义不同，必须判不等",
                CONTENT_RENDER_INDEX_COLUMNS == indexKeyColumns(connection, "probe_wrong_order"),
            )
            assertFalse(
                "非唯一索引不得被判成 UNIQUE",
                indexIsUnique(connection, "probe_snapshot", "probe_not_unique"),
            )
            assertTrue("缺失索引读出来是空列", indexKeyColumns(connection, "probe_missing").isEmpty())
            assertFalse(
                "缺失索引不得被判绿",
                CONTENT_RENDER_INDEX_COLUMNS == indexKeyColumns(connection, "probe_missing"),
            )
        } finally {
            connection.close()
            context.deleteDatabase(databaseName)
        }
    }

    // ---- 夹具 ----

    /** v70 形态：一行 program + 一行用**三段旧公式**派生的 STAGING 快照。 */
    private fun seedV70ProgramAndSnapshot(context: Context, databaseName: String) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                """
                INSERT INTO `$PROGRAM_TABLE` (
                    `program_id`, `learner_id`, `role`, `source_sha256`, `source_js`,
                    `initial_state_json`, `initial_state_sha256`, `api_version`, `sdk_version`,
                    `basis_revision_id`, `basis_fingerprint`, `request_snapshot`,
                    `created_at_epoch_millis`)
                VALUES (
                    '$PROGRAM_ID', '$LEARNER_ID', '$ROLE',
                    '${TutorVisualIdentity.sha256Hex(SOURCE_JS)}', '$SOURCE_JS',
                    '$INITIAL_STATE_JSON', '${TutorVisualIdentity.sha256Hex(INITIAL_STATE_JSON)}',
                    1, '$SDK_VERSION', NULL, '$BASIS_FINGERPRINT', NULL, $NOW)
                """.trimIndent(),
            )
            connection.execSQL(
                snapshotInsertSql(
                    snapshotId = LEGACY_SNAPSHOT_ID,
                    rendererVersion = RENDERER_V1,
                    ownerRequestId = OWNER_LEGACY,
                ),
            )
        } finally {
            connection.close()
        }
    }

    private fun insertRawSnapshot(
        context: Context,
        databaseName: String,
        snapshotId: String,
        rendererVersion: String,
    ) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                snapshotInsertSql(
                    snapshotId = snapshotId,
                    rendererVersion = rendererVersion,
                    ownerRequestId = OWNER_NEW,
                ),
            )
        } finally {
            connection.close()
        }
    }

    private fun snapshotInsertSql(
        snapshotId: String,
        rendererVersion: String,
        ownerRequestId: String,
    ): String = """
        INSERT INTO `$SNAPSHOT_TABLE` (
            `snapshot_id`, `program_id`, `state_json`, `state_sha256`,
            `scene_schema_version`, `scene_json`, `scene_sha256`, `render_schema_version`,
            `draw_commands_json`, `draw_commands_sha256`, `content_ids_json`,
            `viewport_width_du`, `viewport_height_du`, `font_scale_milli`, `theme`,
            `time_ms`, `seed`, `runtime_version`, `compiler_version`, `renderer_version`,
            `font_manifest_version`, `question_verification_status`, `preview_asset_id`,
            `status`, `owner_request_id`, `checkpoint_kind`,
            `created_at_epoch_millis`, `promoted_at_epoch_millis`)
        VALUES (
            '$snapshotId', '$PROGRAM_ID', '$STATE_JSON', '$STATE_SHA256',
            1, '$SCENE_JSON', '$SCENE_SHA256', 1,
            '$DRAW_COMMANDS_JSON', '$DRAW_SHA256', '[]',
            360, 640, 1000, 'LIGHT',
            0, 0, 'quickjs-test', 'visual-compiler-v1', '$rendererVersion',
            '$FONT_V1', 'NOT_CHECKED', NULL,
            '${TutorVisualSnapshotStatus.STAGING}', '$ownerRequestId', 'INITIAL',
            $NOW, NULL)
    """.trimIndent()

    private fun programWrite() = TutorVisualProgramWrite(
        programId = PROGRAM_ID,
        learnerId = LEARNER_ID,
        role = ROLE,
        sourceSha256 = TutorVisualIdentity.sha256Hex(SOURCE_JS),
        sourceJs = SOURCE_JS,
        initialStateJson = INITIAL_STATE_JSON,
        initialStateSha256 = TutorVisualIdentity.sha256Hex(INITIAL_STATE_JSON),
        apiVersion = 1,
        sdkVersion = SDK_VERSION,
        basisRevisionId = null,
        basisFingerprint = BASIS_FINGERPRINT,
        requestSnapshot = null,
        createdAtEpochMillis = NOW,
    )

    /** 与 [seedV70ProgramAndSnapshot] 同内容、同 scene；只有渲染版本可换。 */
    private fun snapshotWrite(rendererVersion: String) = TutorVisualSnapshotWrite(
        snapshotId = TutorVisualIdentity.deriveSnapshotId(
            programId = PROGRAM_ID,
            stateSha256 = STATE_SHA256,
            drawCommandsSha256 = DRAW_SHA256,
            rendererVersion = rendererVersion,
            fontManifestVersion = FONT_V1,
        ),
        programId = PROGRAM_ID,
        stateJson = STATE_JSON,
        stateSha256 = STATE_SHA256,
        sceneSchemaVersion = 1,
        sceneJson = SCENE_JSON,
        sceneSha256 = SCENE_SHA256,
        renderSchemaVersion = 1,
        drawCommandsJson = DRAW_COMMANDS_JSON,
        drawCommandsSha256 = DRAW_SHA256,
        contentIdsJson = "[]",
        viewportWidthDu = 360,
        viewportHeightDu = 640,
        fontScaleMilli = 1_000,
        theme = "LIGHT",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = rendererVersion,
        fontManifestVersion = FONT_V1,
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = null,
        checkpointKind = TutorVisualCheckpointKind.INITIAL,
        createdAtEpochMillis = NOW,
    )

    // ---- PRAGMA 结构读数 ----

    private data class IndexShape(val columns: List<String>, val unique: Boolean)

    private fun readSnapshotIndexes(
        context: Context,
        databaseName: String,
    ): Map<String, IndexShape> {
        val connection = rawConnection(context, databaseName)
        try {
            val statement = connection.prepare("PRAGMA index_list(`$SNAPSHOT_TABLE`)")
            val names = mutableListOf<Pair<String, Boolean>>()
            try {
                while (statement.step()) {
                    val name = statement.getText(1)!!
                    if (name.startsWith("sqlite_autoindex")) continue
                    names += name to (statement.getLong(2) == 1L)
                }
            } finally {
                statement.close()
            }
            return names.associate { (name, unique) ->
                name to IndexShape(indexKeyColumns(connection, name), unique)
            }
        } finally {
            connection.close()
        }
    }

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

    private fun rawConnection(context: Context, databaseName: String): SQLiteConnection =
        AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)

    private companion object {
        const val PROGRAM_TABLE = "tutor_visual_program"
        const val SNAPSHOT_TABLE = "tutor_visual_snapshot"

        /** v70 的三列内容键（70→71 必须 DROP 掉它）。 */
        const val SNAPSHOT_CONTENT_INDEX_V70 =
            "index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256"

        /** v71 的五列内容+渲染版本唯一键。 */
        const val SNAPSHOT_IDENTITY_INDEX =
            "index_tutor_visual_snapshot_content_render_identity"

        val CONTENT_RENDER_INDEX_COLUMNS = listOf(
            "program_id",
            "state_sha256",
            "draw_commands_sha256",
            "renderer_version",
            "font_manifest_version",
        )

        const val LEARNER_ID = "learner-batch32"
        const val SOURCE_JS = "draw();"
        const val INITIAL_STATE_JSON = "{}"
        const val BASIS_FINGERPRINT = "basis-batch32"
        const val SDK_VERSION = "visual-sdk-v1"
        const val ROLE = "QUESTION_DIAGRAM"

        val PROGRAM_ID: String = TutorVisualIdentity.deriveProgramId(
            sourceJs = SOURCE_JS,
            initialStateJson = INITIAL_STATE_JSON,
            basisFingerprint = BASIS_FINGERPRINT,
            apiVersion = 1,
            sdkVersion = SDK_VERSION,
            role = ROLE,
        )

        const val STATE_JSON = "{}"
        const val DRAW_COMMANDS_JSON = """{"ops":["a"]}"""
        const val SCENE_JSON = """{"scene":true}"""
        const val FONT_V1 = "font-manifest-v1"
        const val RENDERER_V1 = "visual-renderer-v1"
        const val RENDERER_V2 = "visual-renderer-v2"

        val STATE_SHA256: String = TutorVisualIdentity.sha256Hex(STATE_JSON)
        val DRAW_SHA256: String = TutorVisualIdentity.sha256Hex(DRAW_COMMANDS_JSON)
        val SCENE_SHA256: String = TutorVisualIdentity.sha256Hex(SCENE_JSON)

        /** v70 的三段派生式（冻结副本）：旧行就是按它落的 id，迁移不回填。 */
        val LEGACY_SNAPSHOT_ID: String = TutorVisualIdentity.SNAPSHOT_ID_PREFIX +
            TutorVisualIdentity.sha256Hex(PROGRAM_ID + STATE_SHA256 + DRAW_SHA256).take(32)

        val NEW_SNAPSHOT_ID_V1: String = TutorVisualIdentity.deriveSnapshotId(
            programId = PROGRAM_ID,
            stateSha256 = STATE_SHA256,
            drawCommandsSha256 = DRAW_SHA256,
            rendererVersion = RENDERER_V1,
            fontManifestVersion = FONT_V1,
        )
        val NEW_SNAPSHOT_ID_V2: String = TutorVisualIdentity.deriveSnapshotId(
            programId = PROGRAM_ID,
            stateSha256 = STATE_SHA256,
            drawCommandsSha256 = DRAW_SHA256,
            rendererVersion = RENDERER_V2,
            fontManifestVersion = FONT_V1,
        )

        const val OWNER_LEGACY = "owner-batch32-legacy"
        const val OWNER_NEW = "owner-batch32-new"
        const val NOW = 1_700_000_000_000L
    }
}
