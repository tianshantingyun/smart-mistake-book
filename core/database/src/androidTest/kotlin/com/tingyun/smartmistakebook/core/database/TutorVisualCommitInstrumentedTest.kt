package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualDao
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualSnapshotStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **D-02（批次 30 / 任务 4）：身份不可变 + `STAGING → LIVE` + 正文/trace/link 同事务**的真库验收。
 *
 * 先于实现写（红），覆盖设计 §3.3–§3.6 的五条路径，全部用真 Room、真 SQLite、真外键：
 *
 * 1. **同事务提交**：assistant 正文 + tool trace + 快照提升 + link 一次提交；任一步失败整体回滚；
 * 2. **重复提交幂等**：同 program + state + draw 第二次返回既有 LIVE 快照，四表行数不变；
 * 3. **CAS 冲突**：转租约与指针换图都以**命中行数**判赢，迟到 checkpoint 不覆盖新状态；
 * 4. **取消/对账**：S3 之后被取消的 STAGING 快照只由对账删除，LIVE 与被引用行一律不动；
 * 5. **同 (message, ordinal) 指向另一张快照**不是"重复提交"而是冲突，不许静默换图。
 *
 * 说明：本轨按批次纪律**不运行 Gradle**，因此本文件在提交时是**未执行**状态；门禁脚本
 * 串行执行 `./gradlew :core:database:connectedDebugAndroidTest`（或定向 `-Pandroid.testInstrumentationRunnerArguments.class=…`）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualCommitInstrumentedTest {

    @Test
    fun assistantBodyTraceAndVisualLinkCommitInOneTransaction() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())

            val program = programWrite()
            val snapshot = snapshotWrite()
            val committed = store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_ONE),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_ONE,
                ),
            )

            assertEquals("正文来自同一次事务写入", "图在下面。", committed.message.bodyMarkdown)
            assertEquals("tool trace 与正文同一行", TOOL_TRACE, committed.message.toolTraceJson)
            assertEquals("快照提升为 LIVE", TutorVisualStatus.LIVE, committed.snapshot.status)
            assertNull("提升后 owner 租约清空", committed.snapshot.ownerRequestId)
            assertNotNull("提升时间必须落列", committed.snapshot.promotedAtEpochMillis)
            assertEquals(1, committed.link.revision)
            assertEquals(committed.snapshot.snapshotId, committed.link.currentSnapshotId)

            assertEquals("同内容只存一份 program", 1, dao.countPrograms(program.programId))
            assertEquals("同内容只存一份 snapshot", 1, dao.countSnapshots(program.programId))
            assertEquals(1, dao.findMessageVisuals(MESSAGE_ONE).size)
        }
    }

    @Test
    fun programIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val forged = programWrite().copy(programId = "visual-program-forged")
            val failure = runCatching {
                store.commitTutorTurnWithVisual(
                    CommitTutorTurnWithVisualCommand(
                        message = assistantCommand(messageId = MESSAGE_ONE),
                        program = forged,
                        snapshot = snapshotWrite().copy(programId = forged.programId),
                        ownerRequestId = OWNER_ONE,
                    ),
                )
            }

            assertTrue(
                "visualId 只能是内容派生值；与内容不符的 id 必须被拒：${failure.exceptionOrNull()}",
                failure.exceptionOrNull() is IllegalArgumentException,
            )
            assertEquals(0, dao.countPrograms(forged.programId))
            assertEquals(0, dao.countSnapshots(forged.programId))
            assertTrue(dao.findMessageVisuals(MESSAGE_ONE).isEmpty())
        }
    }

    @Test
    fun snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val forged = snapshotWrite().copy(snapshotId = "visual-snapshot-forged")

            val failure = runCatching {
                store.stageTutorVisualSnapshot(
                    StageTutorVisualSnapshotCommand(
                        program = programWrite(),
                        snapshot = forged,
                        ownerRequestId = OWNER_ONE,
                    ),
                )
            }

            assertTrue(
                "快照 id 必须由 program+内容派生；与内容不符的 id 必须被拒：${failure.exceptionOrNull()}",
                failure.exceptionOrNull() is IllegalArgumentException,
            )
            assertEquals(
                "复派生门在任何写入之前：不许留下 program 行",
                0,
                dao.countPrograms(PROGRAM_ID),
            )
            assertEquals(
                "复派生门在任何写入之前：不许留下 snapshot 行",
                0,
                dao.countSnapshots(PROGRAM_ID),
            )
        }
    }

    @Test
    fun snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            // id 与"声明的 digest"自洽（旧门只比这一条，会放行），但 stateSha256 与 stateJson
            // 内容不符：这正是"身份派生放宽后内容与身份脱钩 / 同内容不同 digest 产生重复行"
            // 的入口。批次 31 复核修复后必须被 IllegalArgumentException 拒绝。
            val declaredStateSha256 = "e".repeat(64)
            val declaredDrawCommandsSha256 =
                TutorVisualIdentity.sha256Hex(snapshotWrite().drawCommandsJson)
            val forged = snapshotWrite().copy(
                stateSha256 = declaredStateSha256,
                snapshotId = TutorVisualIdentity.deriveSnapshotId(
                    programId = PROGRAM_ID,
                    stateSha256 = declaredStateSha256,
                    drawCommandsSha256 = declaredDrawCommandsSha256,
                ),
            )

            val failure = runCatching {
                store.stageTutorVisualSnapshot(
                    StageTutorVisualSnapshotCommand(
                        program = programWrite(),
                        snapshot = forged,
                        ownerRequestId = OWNER_ONE,
                    ),
                )
            }

            assertTrue(
                "digest 必须与持久化 JSON 一致；只有 id 自洽不够：${failure.exceptionOrNull()}",
                failure.exceptionOrNull() is IllegalArgumentException,
            )
            assertEquals(
                "内容/digest 门在任何写入之前：不许留下 snapshot 行",
                0,
                dao.countSnapshots(PROGRAM_ID),
            )
        }
    }

    @Test
    fun sameDerivedProgramIdWithDifferentNonHashedPayloadIsAConflict() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val program = programWrite()
            store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_ONE),
                    program = program,
                    snapshot = snapshotWrite(),
                    ownerRequestId = OWNER_ONE,
                ),
            )

            // learnerId 不在身份哈希里（设计 §3.1 的派生式）：同 id 但列值不同必须冲突，
            // 不许静默沿用第一次写入的值——那等于悄悄改写"这是谁的程序"。
            val conflicting = program.copy(learnerId = "learner-other")
            val failure = runCatching {
                store.commitTutorTurnWithVisual(
                    CommitTutorTurnWithVisualCommand(
                        message = assistantCommand(messageId = MESSAGE_TWO),
                        program = conflicting,
                        snapshot = snapshotWrite(),
                        ownerRequestId = OWNER_TWO,
                    ),
                )
            }

            assertTrue(
                "同 programId 不同载荷必须抛 ImmutablePayloadConflictException：${failure.exceptionOrNull()}",
                failure.exceptionOrNull() is ImmutablePayloadConflictException,
            )
            assertEquals(
                "第一次写入的 learner 不被覆盖",
                LEARNER_ID,
                store.readTutorVisualProgram(PROGRAM_ID)?.learnerId,
            )
            assertEquals("冲突提交不产生消息", 0, dao.findMessageVisuals(MESSAGE_TWO).size)
        }
    }

    @Test
    fun failedMessageWriteRollsBackProgramSnapshotAndLink() = runBlocking {
        withDatabase { store, dao ->
            val program = programWrite()
            val snapshot = snapshotWrite()
            // 不存在的会话：消息行的外键立刻失败，整个 @Transaction 必须回滚。
            val failure = runCatching {
                store.commitTutorTurnWithVisual(
                    CommitTutorTurnWithVisualCommand(
                        message = assistantCommand(
                            messageId = MESSAGE_ONE,
                            conversationId = "missing-conversation",
                        ),
                        program = program,
                        snapshot = snapshot,
                        ownerRequestId = OWNER_ONE,
                    ),
                )
            }

            assertNotNull("消息写不进时提交必须失败", failure.exceptionOrNull())
            assertNull("回滚后不许留下 program 行", dao.findProgram(program.programId))
            assertEquals("回滚后不许留下 snapshot 行", 0, dao.countSnapshots(program.programId))
            assertTrue("回滚后不许留下 link 行", dao.findMessageVisuals(MESSAGE_ONE).isEmpty())
        }
    }

    @Test
    fun duplicateSubmissionReusesTheLiveSnapshotWithoutNewRows() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val program = programWrite()
            val snapshot = snapshotWrite()

            val first = store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_ONE, createdAt = 1_000),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_ONE,
                ),
            )
            val second = store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    // 同一条消息重放：消息、program、snapshot、link 都必须是幂等读回。
                    message = assistantCommand(messageId = MESSAGE_ONE, createdAt = 2_000),
                    program = program.copy(createdAtEpochMillis = 2_000),
                    snapshot = snapshot.copy(createdAtEpochMillis = 2_000),
                    ownerRequestId = OWNER_TWO,
                ),
            )

            assertEquals("重复提交返回既有快照 id", first.snapshot.snapshotId, second.snapshot.snapshotId)
            assertEquals("重复提交不许新建 program 行", 1, dao.countPrograms(program.programId))
            assertEquals("重复提交不许新建 snapshot 行", 1, dao.countSnapshots(program.programId))
            assertEquals("重复提交不许新建 link 行", 1, dao.findMessageVisuals(MESSAGE_ONE).size)

            // 同内容、不同消息：复用 LIVE 快照，只多一条消息自己的 link。
            store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_TWO, createdAt = 3_000),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_THREE,
                ),
            )
            assertEquals("同内容仍只有一份快照", 1, dao.countSnapshots(program.programId))
            assertEquals(1, dao.findMessageVisuals(MESSAGE_TWO).size)
        }
    }

    @Test
    fun stagingLeaseAndPointerCasUseChangedRowCounts() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val program = programWrite()
            val snapshot = snapshotWrite()

            store.stageTutorVisualSnapshot(
                StageTutorVisualSnapshotCommand(
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_ONE,
                ),
            )
            assertEquals(
                "S3 先落 STAGING 与 owner 租约",
                OWNER_ONE,
                store.readTutorVisualSnapshot(snapshot.snapshotId)?.ownerRequestId,
            )
            assertEquals(
                "过期期望 owner 的转租约命中 0 行（CAS 冲突由行数判）",
                0,
                dao.reassignStagingOwner(
                    snapshotId = snapshot.snapshotId,
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    expectedOwnerRequestId = "owner-stale",
                    nextOwnerRequestId = OWNER_TWO,
                ),
            )
            assertEquals(
                "输家不得改写租约",
                OWNER_ONE,
                store.readTutorVisualSnapshot(snapshot.snapshotId)?.ownerRequestId,
            )
            assertEquals(
                "正确期望 owner 的转租约命中 1 行",
                1,
                dao.reassignStagingOwner(
                    snapshotId = snapshot.snapshotId,
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    expectedOwnerRequestId = OWNER_ONE,
                    nextOwnerRequestId = OWNER_TWO,
                ),
            )

            val committed = store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_ONE),
                    program = program,
                    snapshot = snapshot,
                    ownerRequestId = OWNER_THREE,
                ),
            )
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertEquals(1, committed.link.revision)

            // 第二张快照（内容不同）作为换图目标。
            val next = snapshotWrite(drawTag = "b")
            store.stageTutorVisualSnapshot(
                StageTutorVisualSnapshotCommand(
                    program = program,
                    snapshot = next,
                    ownerRequestId = OWNER_ONE,
                ),
            )
            assertEquals(
                1,
                dao.promoteSnapshot(
                    snapshotId = next.snapshotId,
                    stagingStatus = TutorVisualSnapshotStatus.STAGING,
                    liveStatus = TutorVisualStatus.LIVE,
                    promotedAtEpochMillis = 4_000,
                ),
            )

            assertTrue(
                "正确 revision 的换图命中 1 行",
                store.repointTutorMessageVisual(
                    RepointTutorMessageVisualCommand(
                        messageId = MESSAGE_ONE,
                        ordinal = 0,
                        expectedRevision = 1,
                        nextSnapshotId = next.snapshotId,
                        nowEpochMillis = 5_000,
                    ),
                ),
            )
            assertFalse(
                "迟到 checkpoint（期望 revision 已过期）命中 0 行",
                store.repointTutorMessageVisual(
                    RepointTutorMessageVisualCommand(
                        messageId = MESSAGE_ONE,
                        ordinal = 0,
                        expectedRevision = 1,
                        nextSnapshotId = snapshot.snapshotId,
                        nowEpochMillis = 6_000,
                    ),
                ),
            )
            val link = dao.findMessageVisual(MESSAGE_ONE, 0)
            assertEquals("迟到者不得覆盖新状态", next.snapshotId, link?.currentSnapshotId)
            assertEquals(2, link?.revision)
        }
    }

    @Test
    fun reconcilerDeletesOnlyStaleUnreferencedStagingSnapshots() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val program = programWrite()
            val live = snapshotWrite()
            store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_ONE),
                    program = program,
                    snapshot = live,
                    ownerRequestId = OWNER_ONE,
                ),
            )

            // S3 之后被取消：只有 STAGING 行，没有消息/link。
            val cancelled = snapshotWrite(drawTag = "b").copy(createdAtEpochMillis = 9_000)
            store.stageTutorVisualSnapshot(
                StageTutorVisualSnapshotCommand(
                    program = program,
                    snapshot = cancelled,
                    ownerRequestId = OWNER_TWO,
                ),
            )

            assertEquals(
                "未过租约期不回收",
                0,
                store.reconcileStaleTutorVisualStaging(staleBeforeEpochMillis = 8_999),
            )
            assertEquals(
                "过租约期且无任何引用的 STAGING 被删除",
                1,
                store.reconcileStaleTutorVisualStaging(staleBeforeEpochMillis = 9_001),
            )
            assertNull("取消的快照不得留行", store.readTutorVisualSnapshot(cancelled.snapshotId))
            assertEquals(
                "LIVE 快照不得被对账删除",
                TutorVisualStatus.LIVE,
                store.readTutorVisualSnapshot(live.snapshotId)?.status,
            )
            assertEquals("消息引用不得被对账删除", 1, dao.findMessageVisuals(MESSAGE_ONE).size)
        }
    }

    @Test
    fun sameMessageOrdinalPointingAtAnotherSnapshotIsAConflictNotADuplicate() = runBlocking {
        withDatabase { store, dao ->
            store.createTutorConversation(conversationCommand())
            val program = programWrite()
            val first = snapshotWrite()
            store.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = assistantCommand(messageId = MESSAGE_ONE),
                    program = program,
                    snapshot = first,
                    ownerRequestId = OWNER_ONE,
                ),
            )

            val second = snapshotWrite(drawTag = "b")
            val failure = runCatching {
                store.commitTutorTurnWithVisual(
                    CommitTutorTurnWithVisualCommand(
                        // 同一条消息、同一个 ordinal，却带着另一张快照：这不是重放。
                        message = assistantCommand(messageId = MESSAGE_ONE),
                        program = program,
                        snapshot = second,
                        ownerRequestId = OWNER_TWO,
                    ),
                )
            }

            assertTrue(
                "同 (message, ordinal) 的换图只能走指针 CAS，不得由提交路径静默改写",
                failure.exceptionOrNull() is ImmutablePayloadConflictException,
            )
            assertEquals(
                "冲突后指针保持原快照",
                first.snapshotId,
                dao.findMessageVisual(MESSAGE_ONE, 0)?.currentSnapshotId,
            )
        }
    }

    // ---- 夹具 ----

    private suspend fun withDatabase(
        block: suspend (StudyDatabasePort, TutorVisualDao) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch30-visual-commit-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            assertTrue(
                "首读即把新库建到当前 schema（新库没有迁移，但版本必须是 70）",
                store.readDatabaseVersion() == STUDY_DATABASE_VERSION,
            )
            val dao = (store as RoomStudyDatabase).database.tutorVisualDao()
            block(store, dao)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
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

    private fun assistantCommand(
        messageId: String,
        conversationId: String = CONVERSATION_ID,
        createdAt: Long = 1_000,
    ) = AppendTutorAssistantMessageDatabaseCommand(
        conversationId = conversationId,
        messageId = messageId,
        ordinal = null,
        replyToMessageId = null,
        bodyMarkdown = "图在下面。",
        thinkingMarkdown = "先算再画。",
        toolTraceJson = TOOL_TRACE,
        sourceImageAssetIds = emptyList(),
        logicalOperationId = "operation-$messageId",
        status = "SUCCEEDED",
        createdAtEpochMillis = createdAt,
        completedAtEpochMillis = createdAt,
        errorCode = null,
    )

    private fun programWrite() = TutorVisualProgramWrite(
        programId = PROGRAM_ID,
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceSha256 = "a".repeat(64),
        sourceJs = "draw();",
        initialStateJson = "{}",
        initialStateSha256 = "b".repeat(64),
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
        basisRevisionId = null,
        basisFingerprint = "basis-batch30",
        requestSnapshot = null,
        createdAtEpochMillis = 1_000,
    )

    /**
     * 快照写入载荷。`snapshotId` **不再由测试随手给**：批次 31 起 DB 层会在写库前重新派生
     * 并 `require` 相等（`RoomStudyDatabase.ensureTutorVisualSnapshot`），伪造 id 会被
     * `IllegalArgumentException` 拒绝。**digest 列也不再是任意常量**（批次 31 复核修复）：
     * DB 层现在同时校验 `stateSha256 == sha256Hex(stateJson)` 与
     * `drawCommandsSha256 == sha256Hex(drawCommandsJson)`，所以这里与生产同源计算。
     * `drawTag` 不同 = 内容不同 = 另一张快照。
     */
    private fun snapshotWrite(
        drawTag: String = "a",
        createdAtEpochMillis: Long = 1_000,
    ): TutorVisualSnapshotWrite {
        val stateJson = "{}"
        val drawCommandsJson = """{"ops":["$drawTag"]}"""
        val stateSha256 = TutorVisualIdentity.sha256Hex(stateJson)
        val drawCommandsSha256 = TutorVisualIdentity.sha256Hex(drawCommandsJson)
        return TutorVisualSnapshotWrite(
            snapshotId = TutorVisualIdentity.deriveSnapshotId(
                programId = PROGRAM_ID,
                stateSha256 = stateSha256,
                drawCommandsSha256 = drawCommandsSha256,
            ),
            programId = PROGRAM_ID,
            stateJson = stateJson,
            stateSha256 = stateSha256,
            sceneSchemaVersion = 1,
            sceneJson = """{"scene":true}""",
            sceneSha256 = "d".repeat(64),
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
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    private companion object {
        const val CONVERSATION_ID = "conversation-batch30-commit"
        const val LEARNER_ID = "learner-batch30"

        /** D-02 之后 `programId` 只能是内容派生值（写库前会被重新派生比对）。 */
        val PROGRAM_ID: String = TutorVisualIdentity.deriveProgramId(
            sourceJs = "draw();",
            initialStateJson = "{}",
            basisFingerprint = "basis-batch30",
            apiVersion = 1,
            sdkVersion = "visual-sdk-v1",
            role = "QUESTION_DIAGRAM",
        )
        const val MESSAGE_ONE = "message-batch30-one"
        const val MESSAGE_TWO = "message-batch30-two"
        const val OWNER_ONE = "request-batch30-one"
        const val OWNER_TWO = "request-batch30-two"
        const val OWNER_THREE = "request-batch30-three"
        const val TOOL_TRACE = """[{"tool":"plot","status":"OK"}]"""
    }
}
