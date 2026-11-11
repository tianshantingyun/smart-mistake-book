package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepository
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualProgramInput
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualSnapshotInput
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
 * **V-03 保存题/删会话语义的两组缺口用例（批次 38）**。与既有覆盖的分工（实读核对，不重复）：
 *
 * - FK 的 RESTRICT/CASCADE 形状已由 `TutorVisualSnapshotMigration_69_70InstrumentedTest`
 *   （裸 FK 负向）与 `TutorVisualStructureGatesInstrumentedTest`（pin/淘汰谓词分辨力）钉住；
 * - **缺的是端到端**：删会话（生产路径 `deleteTutorConversation`）后，保存题图 + 它引用的
 *   program/snapshot/checkpoint 续读源**整体存活**——"删会话只带走 link，不带走被保存题
 *   固定的事实"（`TutorVisualEntities.kt` 删除语义段 / 契约 §8.1）此前无人走通；
 * - 对账 SQL（`deleteStaleStagingSnapshots`）的两条 `NOT EXISTS` 是纵深防御
 *   （`TutorVisualDao.kt:238-241` KDoc）：消息 link 半边已由
 *   `reconcilerDeletesOnlyStaleUnreferencedStagingSnapshots` 覆盖，**题面 pin 半边无人钉**——
 *   若这条谓词丢失，启动对账会删掉"被题面固定的预备行"（pin 行随后永远无法被恢复重放，
 *   因为 pin 只认 LIVE、行已不在）。
 *
 * 题面 pin 的 STAGING 行只能裸 SQL 造（生产 pin 门只认 LIVE——`pinRevisionVisual` 的
 * INSERT…SELECT WHERE status='LIVE'），与 gates 套件同款登记：绕过生产门造行是**为证明
 * SQL 谓词有分辨力**，不是放宽生产门。
 *
 * **本轨纪律**：不运行 Gradle/设备命令（门禁由脚本串行执行）；判据是设备轨要执行的断言，
 * 不是已通过的结果。方法名无空格（D8 SimpleName 约束）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualSavedProblemRetentionInstrumentedTest {

    @Test
    fun deletingTheConversationKeepsTheSavedProblemVisual() = runBlocking {
        withDatabase { context, store, dao, databaseName ->
            assertEquals(STUDY_DATABASE_VERSION, store.readDatabaseVersion())
            seedProblemAndRevision(context, databaseName)
            store.createTutorConversation(conversationCommand())

            val program = programWrite()
            val snapshot = snapshotWrite("a")
            val committed = store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(MESSAGE_ID),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_ONE,
                ),
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            val snapshotId = committed.snapshot.snapshotId

            // 保存题固定这张 LIVE 快照（生产 pin 门：同 program + LIVE）。
            dao.pinRevisionVisual(REVISION_ID, "QUESTION_DIAGRAM", 0, PROGRAM_ID, snapshotId, NOW)
            assertEquals(1L, rowCount(context, databaseName, REVISION_VISUAL_TABLE))

            // 删会话（生产路径）：link 随消息 CASCADE 走；program/snapshot/题面 pin 无级联。
            store.deleteTutorConversation(CONVERSATION_ID)

            assertEquals("消息行随会话删除", 0L, rowCount(context, databaseName, MESSAGE_TABLE))
            assertEquals("会话行删除", 0L, rowCount(context, databaseName, CONVERSATION_TABLE))
            assertEquals("消息图引用随消息 CASCADE 走", 0L, rowCount(context, databaseName, LINK_TABLE))
            assertTrue(
                "会话删除后消息/link 无残留",
                dao.findMessageVisuals(MESSAGE_ID).isEmpty(),
            )

            assertEquals("program 行不随会话删除", 1L, rowCount(context, databaseName, PROGRAM_TABLE))
            assertEquals("快照行不随会话删除", 1L, rowCount(context, databaseName, SNAPSHOT_TABLE))
            assertEquals("保存题的固定引用不随会话删除", 1L, rowCount(context, databaseName, REVISION_VISUAL_TABLE))
            assertEquals(
                "快照仍是 LIVE（用户已见事实）",
                TutorVisualStatus.LIVE,
                dao.findSnapshot(snapshotId)?.status,
            )
            assertNotNull(
                "保存题引用行仍在",
                dao.findRevisionVisual(REVISION_ID, "QUESTION_DIAGRAM", 0),
            )
            assertEquals(
                "恢复续读源仍在：删会话后最近 LIVE checkpoint 仍指向被保存的快照",
                snapshotId,
                dao.findLatestLiveSnapshot(PROGRAM_ID, TutorVisualSnapshotStatus.LIVE)?.snapshotId,
            )

            // 淘汰 SQL 即使拿到该行做候选也命中 0（LIVE 状态门；被题面固定的 STAGING 向量
            // 已由 TutorVisualStructureGatesInstrumentedTest 钉住，此处不重复）。
            assertEquals(
                "被保存题固定的 LIVE 快照不进淘汰",
                0,
                dao.evictUnpinnedCheckpoints(
                    snapshotIds = listOf(snapshotId),
                    latestKind = "LATEST",
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    committingOwnerRequestId = "owner-unrelated",
                    leaseExpiredBeforeEpochMillis = Long.MIN_VALUE,
                ),
            )
            assertNotNull("淘汰后行仍在", dao.findSnapshot(snapshotId))
        }
    }

    @Test
    fun reconciliationKeepsARevisionPinnedStagingSnapshot() = runBlocking {
        withDatabase { context, store, dao, databaseName ->
            assertEquals(STUDY_DATABASE_VERSION, store.readDatabaseVersion())
            seedProblemAndRevision(context, databaseName)

            val repository = TutorVisualCommitRepository(
                database = store,
                freeSpaceBytes = { Long.MAX_VALUE },
                clock = { NOW },
            )
            val program = programInput()
            // 两条 STAGING 预备行：同 program、同 created_at（对账 SQL 不看 kind/owner——
            // 唯一能让一行活下来的变量就是题面 pin 谓词本身）。
            val pinned = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput("pin"),
                ownerRequestId = "owner-pin",
            )
            val abandoned = repository.stageSnapshot(
                program = program,
                snapshot = snapshotInput("loose"),
                ownerRequestId = "owner-loose",
            )
            assertEquals(
                TutorVisualSnapshotStatus.STAGING,
                dao.findSnapshot(pinned.snapshotId)?.status,
            )
            // 题面 pin 行绕过生产 pin 门裸插（生产门只认 LIVE；见文件 KDoc 登记）。
            rawRevisionPin(context, databaseName, pinned.snapshotId)

            assertEquals(
                "对账删除过期且无引用的 STAGING 行",
                1,
                store.reconcileStaleTutorVisualStaging(staleBeforeEpochMillis = NOW + 1),
            )
            assertNull("未被引用的 STAGING 预备行被对账回收", dao.findSnapshot(abandoned.snapshotId))
            assertNotNull(
                "被题面固定的 STAGING 预备行必须扛过启动对账（NOT EXISTS 第二半的分辨力）",
                dao.findSnapshot(pinned.snapshotId),
            )
            assertEquals(
                "对账后题面引用行原样保留",
                1L,
                rowCount(context, databaseName, REVISION_VISUAL_TABLE),
            )
        }
    }

    // ------------------------------------------------------------ 夹具（gates 套件同型，自包含）

    private suspend fun withDatabase(
        block: suspend (Context, StudyDatabasePort, TutorVisualDao, String) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch38-visual-saved-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            val dao = (store as RoomStudyDatabase).database.tutorVisualDao()
            block(context, store, dao, databaseName)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    /** `problem_revision_visual.problem_revision_id` 的外键宿主：题面 + 修订各一行。 */
    private fun seedProblemAndRevision(context: Context, databaseName: String) {
        val connection = rawConnection(context, databaseName)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                """
                INSERT INTO `problem` (`problem_id`, `canonical_fingerprint`, `subject`,
                                       `created_at_epoch_millis`)
                VALUES ('$PROBLEM_ID', 'fp-batch38', 'MATH', $NOW)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_revision` (`revision_id`, `problem_id`, `revision_number`,
                    `title`, `problem_markdown`, `answer_spec_id`, `answer_spec_snapshot`,
                    `answer_verification_status`, `source_type`, `source_reference`,
                    `content_fingerprint`, `created_at_epoch_millis`)
                VALUES ('$REVISION_ID', '$PROBLEM_ID', 1, '批次38题面', '题面正文', NULL, NULL,
                    'UNKNOWN', 'CAPTURE_CONFIRMED', NULL, 'fp-batch38-rev', $NOW)
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
                VALUES ('$REVISION_ID', 'QUESTION_DIAGRAM', 0, '$PROGRAM_ID', '$snapshotId', $NOW)
                """.trimIndent(),
            )
        } finally {
            connection.close()
        }
    }

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

    private fun conversationCommand() = CreateTutorConversationDatabaseCommand(
        conversationId = CONVERSATION_ID,
        anchorKind = "TEXT_ONLY",
        anchorId = null,
        anchorRevisionId = null,
        title = null,
        createdAtEpochMillis = 1_000,
    )

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

    private fun programInput() = TutorVisualProgramInput(
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceJs = SOURCE_JS,
        initialStateJson = "{}",
        basisFingerprint = BASIS,
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
    )

    /** 与 [programInput] 同一份内容的端口写载荷（身份派生源相同，DB 层重派生必须相等）。 */
    private fun programWrite() = TutorVisualProgramWrite(
        programId = PROGRAM_ID,
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceSha256 = TutorVisualIdentity.sha256Hex(SOURCE_JS),
        sourceJs = SOURCE_JS,
        initialStateJson = "{}",
        initialStateSha256 = TutorVisualIdentity.sha256Hex("{}"),
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
        basisRevisionId = null,
        basisFingerprint = BASIS,
        requestSnapshot = null,
        createdAtEpochMillis = NOW,
    )

    /** `drawTag` 不同 = 内容不同 = 另一张快照（仓储测试同款字节量口径）。 */
    private fun snapshotInput(drawTag: String) = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = 1,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = 1,
        drawCommandsJson = """{"ops":["$drawTag"]}""",
        contentIds = emptySet(),
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
    )

    /** 与 [snapshotInput] 同一份内容的端口写载荷（身份/digest 由 DB 层重派生核对）。 */
    private fun snapshotWrite(drawTag: String): TutorVisualSnapshotWrite {
        val stateJson = "{}"
        val drawCommandsJson = """{"ops":["$drawTag"]}"""
        val sceneJson = """{"scene":true}"""
        val stateSha256 = TutorVisualIdentity.sha256Hex(stateJson)
        val drawCommandsSha256 = TutorVisualIdentity.sha256Hex(drawCommandsJson)
        return TutorVisualSnapshotWrite(
            snapshotId = TutorVisualIdentity.deriveSnapshotId(
                programId = PROGRAM_ID,
                stateSha256 = stateSha256,
                drawCommandsSha256 = drawCommandsSha256,
                rendererVersion = "visual-renderer-v1",
                fontManifestVersion = "font-manifest-v1",
            ),
            programId = PROGRAM_ID,
            stateJson = stateJson,
            stateSha256 = stateSha256,
            sceneSchemaVersion = 1,
            sceneJson = sceneJson,
            sceneSha256 = TutorVisualIdentity.sha256Hex(sceneJson),
            renderSchemaVersion = 1,
            drawCommandsJson = drawCommandsJson,
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

    private companion object {
        const val PROGRAM_TABLE = "tutor_visual_program"
        const val SNAPSHOT_TABLE = "tutor_visual_snapshot"
        const val LINK_TABLE = "tutor_message_visual"
        const val REVISION_VISUAL_TABLE = "problem_revision_visual"
        const val MESSAGE_TABLE = "tutor_message"
        const val CONVERSATION_TABLE = "tutor_conversation"

        const val LEARNER_ID = "learner-batch38"
        const val SOURCE_JS = "draw();"
        const val BASIS = "basis-batch38-saved"

        /** 与 programWrite/snapshotWrite 用同一个 TutorVisualIdentity 派生（内容派生 id）。 */
        val PROGRAM_ID: String = TutorVisualIdentity.deriveProgramId(
            sourceJs = SOURCE_JS,
            initialStateJson = "{}",
            basisFingerprint = BASIS,
            apiVersion = 1,
            sdkVersion = "visual-sdk-v1",
            role = "QUESTION_DIAGRAM",
        )
        const val PROBLEM_ID = "problem-batch38-saved"
        const val REVISION_ID = "revision-batch38-saved"
        const val CONVERSATION_ID = "conversation-batch38-saved"
        const val MESSAGE_ID = "message-batch38-saved"
        const val OWNER_ONE = "request-batch38-saved"
        const val NOW = 1_700_000_000_000L
    }
}
