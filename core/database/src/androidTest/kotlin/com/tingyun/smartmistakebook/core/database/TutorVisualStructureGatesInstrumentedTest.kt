package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualDao
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualSnapshotStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **批次 31：视觉四表的结构门与 checkpoint 保留接线**（真 Room、真 SQLite、真外键）。
 *
 * 本文件消灭的具体失败（每条用例都先写"改前会怎么坏"）：
 *
 * 1. **消息 link 初次插入缺 program 谓词**：改前 `insertMessageVisualIfLive` 只查
 *    `snapshot_id + status='LIVE'`，于是 `(program_id=P1, current_snapshot_id=S2∈P2)` 能被写进。
 *    后果不是"图画错"，而是 P2 的快照被一行自称 P1 的 link **永久 pin**（pin/evict 只按
 *    `snapshot_id` 关联），既回收不了也解释不了。现在跨 program 的初次 link 必须一行都写不进，
 *    且 `linkMessageVisual` 抛 `ImmutablePayloadConflictException`。
 * 2. **题面 pin 的 STAGING/跨 program 门**：实现已在树（批次 30 复核修复），但**零用例**；
 *    删掉谓词无人发现。本文件把它钉住。
 * 3. **指针 CAS 跨 program**：实现已在树，但缺负向；本文件确认命中 0 行且指针/revision 不变。
 * 4. **checkpoint 的 demote/evict 只在策略层正确、SQL 层没有 status 谓词**：改前 LIVE 的
 *    未固定行会被当缓存删掉。本文件在真库上钉住"只删 STAGING、INITIAL 与 LIVE 一行不动"，
 *    并验证提交事务真的执行了 demote/evict（改前 S3→S4 路径上这两条 SQL 根本不发生）。
 *
 * 本轨按批次纪律**不运行 Gradle**；门禁脚本串行执行
 * `assembleDebugAndroidTest` + `adb install -r -t` + `am instrument`（不用 connectedAndroidTest）。
 * 因此本文件在提交时是**未执行**状态：判据已写清，绿轮由设备轨产生。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualStructureGatesInstrumentedTest {

    // ------------------------------------------------------------ link / pin / CAS

    @Test
    fun messageLinkRejectsForeignKeyProgramAndStagingSnapshots() = runBlocking {
        withDatabase { context, store, dao, databaseName ->
            seedPrograms(context, databaseName)
            seedMessage(context, databaseName)
            rawSnapshot(context, databaseName, "snap-p1-live", P1, "LIVE", "a")
            rawSnapshot(context, databaseName, "snap-p2-live", P2, "LIVE", "b")
            rawSnapshot(context, databaseName, "snap-p1-staging", P1, "STAGING", "c")

            // 跨 program 的初次 link：改前会静默写进（A1 实测形态）。
            val foreign = runCatching {
                dao.linkMessageVisual(
                    messageId = MESSAGE_ID,
                    ordinal = 0,
                    programId = P1,
                    snapshotId = "snap-p2-live",
                    uiStateJson = null,
                    formDigest = null,
                    nowEpochMillis = NOW,
                )
            }
            assertTrue(
                "跨 program 的初次 link 必须抛冲突而不是写坏行：${foreign.exceptionOrNull()}",
                foreign.exceptionOrNull() is ImmutablePayloadConflictException,
            )
            assertEquals("跨 program 的 link 一行都不许写", 0L, rowCount(context, databaseName, LINK_TABLE))

            // STAGING 快照同样写不进（既有门；补负向）。
            val staging = runCatching {
                dao.linkMessageVisual(
                    messageId = MESSAGE_ID,
                    ordinal = 0,
                    programId = P1,
                    snapshotId = "snap-p1-staging",
                    uiStateJson = null,
                    formDigest = null,
                    nowEpochMillis = NOW,
                )
            }
            assertTrue(
                "STAGING 快照不得被消息引用：${staging.exceptionOrNull()}",
                staging.exceptionOrNull() is ImmutablePayloadConflictException,
            )
            assertEquals(0L, rowCount(context, databaseName, LINK_TABLE))

            // 同 program + LIVE：正常写入，revision 从 1 起。
            val linked = dao.linkMessageVisual(
                messageId = MESSAGE_ID,
                ordinal = 0,
                programId = P1,
                snapshotId = "snap-p1-live",
                uiStateJson = null,
                formDigest = null,
                nowEpochMillis = NOW,
            )
            assertEquals(P1, linked.programId)
            assertEquals(1, linked.revision)

            // 指针 CAS 跨 program：命中 0 行，指针与 revision 都不变（既有谓词，补负向）。
            val changed = dao.repointMessageVisual(
                messageId = MESSAGE_ID,
                ordinal = 0,
                expectedRevision = 1,
                nextSnapshotId = "snap-p2-live",
                nowEpochMillis = NOW + 1,
                liveStatus = TutorVisualSnapshotStatus.LIVE,
            )
            assertEquals("跨 program 的指针 CAS 必须命中 0 行", 0, changed)
            val after = dao.findMessageVisual(MESSAGE_ID, 0)
            assertNotNull(after)
            assertEquals("输家不得改写指针", "snap-p1-live", after!!.currentSnapshotId)
            assertEquals("输家不得递增 revision", 1, after.revision)
        }
    }

    @Test
    fun revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive() = runBlocking {
        withDatabase { context, store, dao, databaseName ->
            seedPrograms(context, databaseName)
            seedMessage(context, databaseName)
            rawSnapshot(context, databaseName, "snap-p1-live", P1, "LIVE", "a")
            rawSnapshot(context, databaseName, "snap-p2-live", P2, "LIVE", "b")
            rawSnapshot(context, databaseName, "snap-p1-staging", P1, "STAGING", "c")

            val staging = runCatching {
                dao.pinRevisionVisual(REVISION_ID, "QUESTION_DIAGRAM", 0, P1, "snap-p1-staging", NOW)
            }
            assertTrue(
                "被取消的 STAGING 快照不得被题面永久 pin：${staging.exceptionOrNull()}",
                staging.exceptionOrNull() is ImmutablePayloadConflictException,
            )
            assertEquals(0L, rowCount(context, databaseName, REVISION_VISUAL_TABLE))

            val foreign = runCatching {
                dao.pinRevisionVisual(REVISION_ID, "QUESTION_DIAGRAM", 0, P1, "snap-p2-live", NOW)
            }
            assertTrue(
                "跨 program 的题面 pin 不得写进：${foreign.exceptionOrNull()}",
                foreign.exceptionOrNull() is ImmutablePayloadConflictException,
            )
            assertEquals(0L, rowCount(context, databaseName, REVISION_VISUAL_TABLE))

            val pinned = dao.pinRevisionVisual(REVISION_ID, "QUESTION_DIAGRAM", 0, P1, "snap-p1-live", NOW)
            assertEquals("snap-p1-live", pinned.snapshotId)
            assertEquals(1L, rowCount(context, databaseName, REVISION_VISUAL_TABLE))
        }
    }

    // ------------------------------------------------------------ checkpoint demote / evict

    @Test
    fun evictionDeletesOnlyUnpinnedStagingIntermediates() = runBlocking {
        withDatabase { context, store, dao, databaseName ->
            seedPrograms(context, databaseName)
            seedMessage(context, databaseName)
            rawSnapshot(context, databaseName, "snap-initial", P1, "LIVE", "a", kind = "INITIAL")
            rawSnapshot(context, databaseName, "snap-latest", P1, "LIVE", "b", kind = "LATEST")
            rawSnapshot(context, databaseName, "snap-staging-middle", P1, "STAGING", "c", kind = null)
            rawSnapshot(context, databaseName, "snap-live-middle", P1, "LIVE", "d", kind = null)
            // 复核第 1 轮：被固定的行必须与 status 正交（同为 STAGING），否则 DELETE 的
            // `status = STAGING` 先把它排除，pin 谓词就没有任何分辨力。
            rawSnapshot(context, databaseName, "snap-pinned-staging", P1, "STAGING", "e", kind = null)
            rawRevisionPin(context, databaseName, "snap-pinned-staging")
            // 同理，link 的 NOT EXISTS 也需要一条"仅靠消息指针保住"的 STAGING 行。
            rawSnapshot(context, databaseName, "snap-linked-staging", P1, "STAGING", "f", kind = null)
            rawMessageLink(context, databaseName, MESSAGE_ID, "snap-linked-staging")

            assertEquals(
                "旧 LATEST 降级命中 1 行",
                1,
                dao.demoteLatestCheckpoints(P1, listOf("snap-latest"), "LATEST"),
            )
            assertEquals("降级只改角色列", null, dao.findSnapshot("snap-latest")?.checkpointKind)

            val deleted = dao.evictUnpinnedCheckpoints(
                snapshotIds = listOf(
                    "snap-initial",
                    "snap-latest",
                    "snap-staging-middle",
                    "snap-live-middle",
                    "snap-pinned-staging",
                    "snap-linked-staging",
                ),
                latestKind = "LATEST",
                stagingStatus = TutorVisualSnapshotStatus.STAGING,
            )

            assertEquals("只有未固定、非 INITIAL、STAGING 的中间态被删", 1, deleted)
            assertNull("STAGING 中间态被回收", dao.findSnapshot("snap-staging-middle"))
            assertNotNull("INITIAL 永不淘汰", dao.findSnapshot("snap-initial"))
            assertNotNull("旧 LATEST 行仍在（只降级不删）", dao.findSnapshot("snap-latest"))
            assertNotNull("LIVE 中间态不得当缓存删除", dao.findSnapshot("snap-live-middle"))
            assertNotNull(
                "被题面固定的 STAGING 行保留（pin 谓词必须有分辨力）",
                dao.findSnapshot("snap-pinned-staging"),
            )
            assertNotNull(
                "被消息当前指针引用的 STAGING 行保留（link NOT EXISTS 必须有分辨力）",
                dao.findSnapshot("snap-linked-staging"),
            )

            val summaries = dao.checkpointSummaries(P1).associateBy { it.snapshotId }
            assertEquals("pinned 读侧：题面引用 = 1", 1, summaries.getValue("snap-pinned-staging").pinned)
            assertEquals("pinned 读侧：消息引用 = 1", 1, summaries.getValue("snap-linked-staging").pinned)
            assertEquals("pinned 读侧：无引用 = 0", 0, summaries.getValue("snap-staging-middle").pinned)
        }
    }

    @Test
    fun commitTransactionExecutesDemoteAndEvict() = runBlocking {
        withDatabase { context, store, dao, databaseName ->
            seedPrograms(context, databaseName)
            seedMessage(context, databaseName)
            rawSnapshot(context, databaseName, "snap-old-latest", P1, "LIVE", "b", kind = "LATEST")
            rawSnapshot(context, databaseName, "snap-old-staging", P1, "STAGING", "c", kind = null)
            rawSnapshot(context, databaseName, "snap-old-live", P1, "LIVE", "d", kind = null)

            val program = programWrite()
            val snapshot = snapshotWrite("e")
            val committed = store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = "message-commit-gates"),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = "owner-commit-gates",
                    demoteCheckpointSnapshotIds = listOf("snap-old-latest"),
                    // 故意把 LIVE 行也列进候选：SQL 层的 status 谓词必须拒删它。
                    evictCheckpointSnapshotIds = listOf("snap-old-staging", "snap-old-live"),
                ),
            )

            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertNull("旧 LATEST 在提交事务里被降级", dao.findSnapshot("snap-old-latest")?.checkpointKind)
            assertNull("STAGING 中间态在提交事务里被回收", dao.findSnapshot("snap-old-staging"))
            assertNotNull("LIVE 行即使出现在候选集里也不得被删", dao.findSnapshot("snap-old-live"))
        }
    }

    // ------------------------------------------------------------ fixtures

    private suspend fun withDatabase(
        block: suspend (Context, RoomStudyDatabase, TutorVisualDao, String) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch31-visual-gates-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            val roomStore = store as RoomStudyDatabase
            assertEquals(STUDY_DATABASE_VERSION, store.readDatabaseVersion())
            val database = roomStore.database
            block(context, roomStore, database.tutorVisualDao(), databaseName)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    /** program 两行（payload 与 [programWrite] 同源）、题面一行。 */
    private fun seedPrograms(context: Context, databaseName: String) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(programInsertSql(P1, P1_SOURCE_JS, P1_BASIS))
            connection.execSQL(programInsertSql(P2, P2_SOURCE_JS, P2_BASIS))
            connection.execSQL(
                """
                INSERT INTO `problem` (`problem_id`, `canonical_fingerprint`, `subject`,
                                       `created_at_epoch_millis`)
                VALUES ('$PROBLEM_ID', 'fp-batch31', 'MATH', $NOW)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_revision` (`revision_id`, `problem_id`, `revision_number`,
                    `title`, `problem_markdown`, `answer_spec_id`, `answer_spec_snapshot`,
                    `answer_verification_status`, `source_type`, `source_reference`,
                    `content_fingerprint`, `created_at_epoch_millis`)
                VALUES ('$REVISION_ID', '$PROBLEM_ID', 1, '批次31题面', '题面正文', NULL, NULL,
                    'UNKNOWN', 'CAPTURE_CONFIRMED', NULL, 'fp-batch31-rev', $NOW)
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
    }

    /** link 的外键需要真实宿主：会话 + 一条消息。 */
    private fun seedMessage(context: Context, databaseName: String) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                """
                INSERT INTO `tutor_conversation` (`conversation_id`, `anchor_kind`, `status`,
                    `created_at_epoch_millis`, `updated_at_epoch_millis`, `last_turn_ordinal`)
                VALUES ('$CONVERSATION_ID', 'PROBLEM', 'ACTIVE', $NOW, $NOW, 0)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `tutor_message` (`message_id`, `conversation_id`, `ordinal`, `role`,
                    `body_markdown`, `status`, `created_at_epoch_millis`)
                VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', 0, 'ASSISTANT', '图消息',
                    'COMPLETED', $NOW)
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
    }

    private fun rawSnapshot(
        context: Context,
        databaseName: String,
        snapshotId: String,
        programId: String,
        status: String,
        drawTag: String,
        kind: String? = null,
    ) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            val kindSql = kind?.let { "'$it'" } ?: "NULL"
            connection.execSQL(
                """
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
                    '$snapshotId', '$programId', '{}', '${(drawTag + "s").repeat(64).take(64)}',
                    1, '{}', '${drawTag.repeat(64)}', 1,
                    '{"ops":["$drawTag"]}', '${drawTag.repeat(64)}', '[]',
                    360, 640, 1000, 'LIGHT',
                    0, 0, 'quickjs-test', 'visual-compiler-v1', 'visual-renderer-v1',
                    'font-manifest-v1', 'NOT_CHECKED', NULL,
                    '$status', 'owner-batch31', $kindSql,
                    $NOW, ${if (status == "LIVE") NOW else "NULL"})
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
    }

    private fun rawRevisionPin(context: Context, databaseName: String, snapshotId: String) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                """
                INSERT INTO `$REVISION_VISUAL_TABLE`
                    (`problem_revision_id`, `role`, `ordinal`, `program_id`, `snapshot_id`,
                     `created_at_epoch_millis`)
                VALUES ('$REVISION_ID', 'QUESTION_DIAGRAM', 0, '$P1', '$snapshotId', $NOW)
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
    }

    /**
     * 消息当前指针引用一条 **STAGING** 快照的夹具。
     *
     * 生产写入只允许 link 指向 LIVE（`insertMessageVisualIfLive` 的 SQL 门），但淘汰 SQL 的
     * `NOT EXISTS` 必须对**任何** status 的引用都生效（引用可能在快照降级后仍存在）。这里绕过
     * 生产门直接造行，只为证明淘汰 SQL 的 link 谓词有分辨力——不是放宽生产门。
     */
    private fun rawMessageLink(
        context: Context,
        databaseName: String,
        messageId: String,
        snapshotId: String,
    ) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                """
                INSERT INTO `$LINK_TABLE`
                    (`message_id`, `ordinal`, `program_id`, `current_snapshot_id`, `revision`,
                     `ui_state_json`, `form_digest`, `created_at_epoch_millis`,
                     `updated_at_epoch_millis`)
                VALUES ('$messageId', 0, '$P1', '$snapshotId', 1, NULL, NULL, $NOW, $NOW)
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
    }

    private fun programInsertSql(programId: String, sourceJs: String, basis: String): String = """
        INSERT INTO `$PROGRAM_TABLE` (
            `program_id`, `learner_id`, `role`, `source_sha256`, `source_js`,
            `initial_state_json`, `initial_state_sha256`, `api_version`, `sdk_version`,
            `basis_revision_id`, `basis_fingerprint`, `request_snapshot`,
            `created_at_epoch_millis`)
        VALUES ('$programId', '$LEARNER_ID', 'QUESTION_DIAGRAM',
            '${TutorVisualIdentity.sha256Hex(sourceJs)}', '$sourceJs',
            '{}', '${TutorVisualIdentity.sha256Hex("{}")}', 1, 'visual-sdk-v1',
            NULL, '$basis', NULL, $NOW)
    """.trimIndent()

    private fun programWrite() = TutorVisualProgramWrite(
        programId = TutorVisualIdentity.deriveProgramId(
            sourceJs = P1_SOURCE_JS,
            initialStateJson = "{}",
            basisFingerprint = P1_BASIS,
            apiVersion = 1,
            sdkVersion = "visual-sdk-v1",
            role = "QUESTION_DIAGRAM",
        ),
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceSha256 = TutorVisualIdentity.sha256Hex(P1_SOURCE_JS),
        sourceJs = P1_SOURCE_JS,
        initialStateJson = "{}",
        initialStateSha256 = TutorVisualIdentity.sha256Hex("{}"),
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
        basisRevisionId = null,
        basisFingerprint = P1_BASIS,
        requestSnapshot = null,
        createdAtEpochMillis = NOW,
    )

    private fun snapshotWrite(drawTag: String): TutorVisualSnapshotWrite {
        val programId = TutorVisualIdentity.deriveProgramId(
            sourceJs = P1_SOURCE_JS,
            initialStateJson = "{}",
            basisFingerprint = P1_BASIS,
            apiVersion = 1,
            sdkVersion = "visual-sdk-v1",
            role = "QUESTION_DIAGRAM",
        )
        val stateSha256 = TutorVisualIdentity.sha256Hex("{}")
        val drawCommandsSha256 = TutorVisualIdentity.sha256Hex("""{"ops":["$drawTag"]}""")
        return TutorVisualSnapshotWrite(
            snapshotId = TutorVisualIdentity.deriveSnapshotId(programId, stateSha256, drawCommandsSha256),
            programId = programId,
            stateJson = "{}",
            stateSha256 = stateSha256,
            sceneSchemaVersion = 1,
            sceneJson = """{"scene":true}""",
            sceneSha256 = TutorVisualIdentity.sha256Hex("""{"scene":true}"""),
            renderSchemaVersion = 1,
            drawCommandsJson = """{"ops":["$drawTag"]}""",
            drawCommandsSha256 = drawCommandsSha256,
            contentIdsJson = "[]",
            viewportWidthDu = 360,
            viewportHeightDu = 640,
            fontScaleMilli = 1_000,
            theme = "LIGHT",
            timeMs = 0,
            seed = 0,
            runtimeVersion = "quickjs-test",
            compilerVersion = "visual-compiler-v1",
            rendererVersion = "visual-renderer-v1",
            fontManifestVersion = "font-manifest-v1",
            questionVerificationStatus = "NOT_CHECKED",
            previewAssetId = null,
            checkpointKind = TutorVisualCheckpointKind.INITIAL,
            createdAtEpochMillis = NOW,
        )
    }

    private fun assistantCommand(messageId: String) = AppendTutorAssistantMessageDatabaseCommand(
        conversationId = CONVERSATION_ID,
        messageId = messageId,
        ordinal = null,
        replyToMessageId = null,
        bodyMarkdown = "图在下面。",
        thinkingMarkdown = null,
        toolTraceJson = null,
        sourceImageAssetIds = emptyList(),
        logicalOperationId = "operation-$messageId",
        status = "SUCCEEDED",
        createdAtEpochMillis = NOW,
        completedAtEpochMillis = NOW,
        errorCode = null,
    )

    private fun rawConnection(context: Context, databaseName: String): SQLiteConnection =
        AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)

    private fun rowCount(context: Context, databaseName: String, table: String): Long {
        val connection = rawConnection(context, databaseName)
        try {
            val statement = connection.prepare("SELECT COUNT(*) FROM `$table`")
            try {
                assertTrue("COUNT(*) 必须有结果", statement.step())
                return statement.getLong(0)
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private companion object {
        const val PROGRAM_TABLE = "tutor_visual_program"
        const val SNAPSHOT_TABLE = "tutor_visual_snapshot"
        const val LINK_TABLE = "tutor_message_visual"
        const val REVISION_VISUAL_TABLE = "problem_revision_visual"

        const val LEARNER_ID = "learner-batch31"
        const val P1_SOURCE_JS = "draw();"
        const val P1_BASIS = "basis-batch31-p1"
        const val P2_SOURCE_JS = "draw(2);"
        const val P2_BASIS = "basis-batch31-p2"

        /**
         * 两个 program 都是**内容派生** id：与 [programWrite]/[snapshotWrite] 用同一个
         * `TutorVisualIdentity`，否则提交路径重派生时会撞上"payload 不同"的冲突。
         */
        val P1: String = TutorVisualIdentity.deriveProgramId(
            sourceJs = P1_SOURCE_JS,
            initialStateJson = "{}",
            basisFingerprint = P1_BASIS,
            apiVersion = 1,
            sdkVersion = "visual-sdk-v1",
            role = "QUESTION_DIAGRAM",
        )
        val P2: String = TutorVisualIdentity.deriveProgramId(
            sourceJs = P2_SOURCE_JS,
            initialStateJson = "{}",
            basisFingerprint = P2_BASIS,
            apiVersion = 1,
            sdkVersion = "visual-sdk-v1",
            role = "QUESTION_DIAGRAM",
        )
        const val PROBLEM_ID = "problem-batch31"
        const val REVISION_ID = "revision-batch31"
        const val CONVERSATION_ID = "conversation-batch31"
        const val MESSAGE_ID = "message-batch31"
        const val NOW = 1_700_000_000_000L
    }
}
