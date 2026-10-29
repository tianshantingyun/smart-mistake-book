package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepository
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualProgramInput
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualSnapshotInput
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StageTutorVisualSnapshotCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
import com.tingyun.smartmistakebook.core.database.TutorVisualProgramWrite
import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotWrite
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * **批次 33 / KD-80 80-C4：备份/恢复之后视觉四表的数据与引用关系仍然成立。**
 *
 * 本文件消灭的具体失败：恢复侧已经有 `quick_check` + `foreign_key_check` 与 90 表行数对拍，
 * 但没有任何用例用**已知源数据**证明归档/恢复后的行集合、复合 program 一致性、LIVE-only
 * 引用与内容摘要仍是原来那一组。于是"恢复接受了一份被第三方重裁、引用指向不存在快照的包"
 * 或"裁剪把本应保留的行删掉"都不会让任何测试变红。
 *
 * 五个用例分别钉住：
 * 1. 全选（mask 15）源 → 归档 → 恢复三次快照恒等，S2–S10 全部为空；
 * 2. 子集 9 只保留题面 pin 与 INITIAL 基线（`M=0`，`S={S1,S3}`，`P={P1,P2}`）；
 * 3. 子集 13 保留消息 link，但裁掉未被引用的 STAGING 中间态（`M=2`，`S={S1,S2,S3}`）；
 * 4. 归档库与恢复库按主键逐行内容相等，且两库 `integrity_check=ok`；
 * 5. 第三方重裁包删掉被 link 引用的快照后，恢复必须在交换前拒绝（`foreign_key_check`），原库不动。
 *
 * 期望集合用 Kotlin 从**源行**按裁剪公式计算（不复制 SQL）；数据库内自洽检查走只读连接，
 * 不复制主文件字节（WAL 中已提交的行只在连接视图里可见）。用例只新增文件，不改 34 例基线。
 */
@RunWith(AndroidJUnit4::class)
class BackupVisualReferenceInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 恢复路径固定落在 DEFAULT_DATABASE_NAME（AndroidBackupRepository.doRestore 的
        // `context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)`）；用别的
        // 名字会让 restore 换掉另一份库、本用例读到的是未被替换的源库。
        databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    // ------------------------------------------------------------------ 1

    @Test
    fun fullSelectionRoundTripPreservesAllVisualRowsAndReferences() = runBlocking {
        val store = openSeededStore()
        try {
            val fixture = seedVisualFixture(store)
            val source = readVisualRows(context.getDatabasePath(databaseName))
            assertEquals("源库自洽检查必须全空", emptyList<String>(), source.violations)

            val archive = createArchive(store, BackupSelection.FULL)
            val archiveFile = extractArchiveDatabaseFile(archive)
            val archived = readVisualRows(archiveFile)

            assertEquals("全选不裁视觉行（归档 = 源）", source.copy(violations = emptyList()), archived.copy(violations = emptyList()))
            assertEquals("全选归档不得产生不变量违规", emptyList<String>(), archived.violations)

            store.close()
            restore(archive)
            val restored = readVisualRows(context.getDatabasePath(databaseName))

            assertEquals(
                "全选恢复后四表内容必须与源库逐行相等",
                source.copy(violations = emptyList()),
                restored.copy(violations = emptyList()),
            )
            assertEquals("全选恢复不得产生不变量违规", emptyList<String>(), restored.violations)
            assertEquals(
                "全选必须保留所有已知快照",
                setOf(fixture.s1, fixture.s2, fixture.s3, fixture.s4),
                restored.snapshotIds,
            )
        } finally {
            runCatching { store.close() }
        }
    }

    // ------------------------------------------------------------------ 2

    @Test
    fun subsetNineKeepsRevisionPinsAndInitialBaselinesOnly() = runBlocking {
        val store = openSeededStore()
        try {
            val fixture = seedVisualFixture(store)
            val source = readVisualRows(context.getDatabasePath(databaseName))
            val expected = expectedRows(source, mask = 9)

            val archive = createArchive(store, selectionOf(9))
            val archived = readVisualRows(extractArchiveDatabaseFile(archive))

            assertEquals("mask 9：消息 link 整表裁掉", emptySet<Pair<String, Int>>(), archived.links)
            assertEquals("mask 9：题面 pin 保留", expected.pins, archived.pins)
            assertEquals("mask 9：快照集合 = INITIAL 基线 ∪ 题面 pin", setOf(fixture.s1, fixture.s3), archived.snapshotIds)
            assertEquals("mask 9：program 集合随保留快照/题面 pin", expected.programs, archived.programIds)
            assertEquals("mask 9：只被消息引用的快照必须裁掉", false, archived.snapshotIds.contains(fixture.s2))
            assertEquals("mask 9：未被引用的 STAGING 中间态必须裁掉", false, archived.snapshotIds.contains(fixture.s4))
            assertEquals("mask 9 归档不得产生不变量违规", emptyList<String>(), archived.violations)

            store.close()
            restore(archive)
            val restored = readVisualRows(context.getDatabasePath(databaseName))

            assertEquals("mask 9：恢复后快照集合必须与归档一致", archived.snapshotIds, restored.snapshotIds)
            assertEquals("mask 9：恢复后 program 集合必须与归档一致", archived.programIds, restored.programIds)
            assertEquals("mask 9：恢复后 pin 必须与归档一致", archived.pins, restored.pins)
            assertEquals("mask 9：恢复后不得产生不变量违规", emptyList<String>(), restored.violations)
        } finally {
            runCatching { store.close() }
        }
    }

    // ------------------------------------------------------------------ 3

    @Test
    fun subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging() = runBlocking {
        val store = openSeededStore()
        try {
            val fixture = seedVisualFixture(store)
            val source = readVisualRows(context.getDatabasePath(databaseName))

            val archive = createArchive(store, selectionOf(13))
            val archived = readVisualRows(extractArchiveDatabaseFile(archive))

            assertEquals("mask 13：两条消息 link 全部保留", 2, archived.links.size)
            assertEquals("mask 13：快照集合 = 两个 link ∪ 题面 pin ∪ INITIAL", setOf(fixture.s1, fixture.s2, fixture.s3), archived.snapshotIds)
            assertEquals("mask 13：program 集合 = P1 + P2", setOf(fixture.p1, fixture.p2), archived.programIds)
            assertEquals("mask 13：未被引用的 STAGING 中间态必须裁掉", false, archived.snapshotIds.contains(fixture.s4))
            assertEquals("mask 13：只被已裁快照引用的 program 也必须裁掉", false, archived.programIds.contains(fixture.p3))
            assertEquals("mask 13：题面 pin 保留", 1, archived.pins.size)
            assertEquals("mask 13 归档不得产生不变量违规", emptyList<String>(), archived.violations)
            assertEquals(
                "mask 13：被消息/题面引用的快照必须仍是 LIVE",
                setOf(fixture.s1, fixture.s2),
                archived.snapshotIds.filter { archived.snapshotStatus[it] == "LIVE" }.toSet(),
            )

            store.close()
            restore(archive)
            val restored = readVisualRows(context.getDatabasePath(databaseName))

            assertEquals("mask 13：恢复后 link 集合必须与归档一致", archived.links, restored.links)
            assertEquals("mask 13：恢复后快照集合必须与归档一致", archived.snapshotIds, restored.snapshotIds)
            assertEquals("mask 13：恢复后 program 集合必须与归档一致", archived.programIds, restored.programIds)
            assertEquals("mask 13：恢复后不得产生不变量违规", emptyList<String>(), restored.violations)
            assertEquals("mask 13：期望公式与归档一致", expectedRows(source, 13).snapshots, archived.snapshotIds)
        } finally {
            runCatching { store.close() }
        }
    }

    // ------------------------------------------------------------------ 4

    @Test
    fun archiveAndRestoredDatabaseAgreeOnVisualRowIdentity() = runBlocking {
        val store = openSeededStore()
        try {
            seedVisualFixture(store)
            val archive = createArchive(store, BackupSelection.FULL)
            val archiveFile = extractArchiveDatabaseFile(archive)
            val archived = readVisualRows(archiveFile)
            assertEquals("归档库 integrity_check 必须 ok", "ok", integrityCheck(archiveFile))

            store.close()
            restore(archive)
            val restoredFile = context.getDatabasePath(databaseName)
            val restored = readVisualRows(restoredFile)
            assertEquals("恢复库 integrity_check 必须 ok", "ok", integrityCheck(restoredFile))

            assertEquals("归档与恢复：program 逐行内容相等", archived.programRows, restored.programRows)
            assertEquals("归档与恢复：snapshot 逐行内容相等", archived.snapshotRows, restored.snapshotRows)
            assertEquals("归档与恢复：link 逐行内容相等", archived.linkRows, restored.linkRows)
            assertEquals("归档与恢复：pin 逐行内容相等", archived.pinRows, restored.pinRows)
        } finally {
            runCatching { store.close() }
        }
    }

    // ------------------------------------------------------------------ 5

    @Test
    fun repackagedArchiveWhoseLinkPointsAtAMissingSnapshotIsRejectedBeforeSwap() = runBlocking {
        val store = openSeededStore()
        try {
            val fixture = seedVisualFixture(store)
            val archive = createArchive(store, selectionOf(13))

            val lyingArchive = repackWithModifiedDatabase(
                archive = archive,
                mutateDatabase = { db ->
                    // 外键先关掉：这里模拟第三方重裁者删掉被 link/pin 引用的快照行。
                    db.execSQL("PRAGMA foreign_keys = OFF")
                    db.execSQL(
                        "DELETE FROM tutor_visual_snapshot WHERE snapshot_id = ?",
                        arrayOf(fixture.s1),
                    )
                },
                mutateSelectionCounts = { counts ->
                    counts["tutor_visual_snapshot"] = (counts["tutor_visual_snapshot"] ?: 0L) - 1L
                },
            )

            val failure = assertThrows(BackupRestoreException::class.java) {
                runBlocking {
                    AndroidBackupRepository(context, store)
                        .restore(ByteArrayInputStream(lyingArchive))
                }
            }
            assertTrue(
                "拒因必须点名 foreign_key_check：${failure.message}",
                failure.message.orEmpty().contains("foreign_key_check"),
            )
            assertEquals("拒绝后原库不动", 1, store.countMistakes())
            assertEquals(
                "拒绝后源库的视觉行仍完整",
                4,
                readVisualRows(context.getDatabasePath(databaseName)).snapshotIds.size,
            )
        } finally {
            runCatching { store.close() }
        }
    }

    // ------------------------------------------------------------ fixture

    private data class VisualFixture(
        val p1: String,
        val p2: String,
        val p3: String,
        val s1: String,
        val s2: String,
        val s3: String,
        val s4: String,
    )

    private fun openSeededStore(): StudyDatabasePort {
        val store = StudyDatabaseFactory.open(context, databaseName)
        seedStudyFacts(context, store, databaseName, seedBundle())
        return store
    }

    /**
     * 源数据形状（C4 研究 §5.2）：
     * - P1: 两次 commit → S1 INITIAL/LIVE + link L1、S2 LATEST/LIVE + link L2；
     * - P2: 一次 stage → S3 STAGING/INITIAL（未被引用）；
     * - P3: 直接走端口 stage，`checkpointKind=null` → S4 STAGING 可回收中间态；
     * - R1: raw pin revision-backup/QUESTION_DIAGRAM/0 → (P1, S1)。
     *
     * S4 不能走仓储 `stageSnapshot`：仓储会用 `plan.incomingKind` 覆盖调用方传入的 kind，
     * 空 program 必然得到 INITIAL。端口不做覆盖，这里只造"合法但非仓储默认"的行，不放宽生产门。
     */
    private suspend fun seedVisualFixture(store: StudyDatabasePort): VisualFixture {
        store.createTutorConversation(conversationCommand())
        val repository = TutorVisualCommitRepository(
            database = store,
            freeSpaceBytes = { Long.MAX_VALUE },
            clock = { NOW },
        )
        val p1Input = programInput(sourceJs = "draw(1);", basis = "basis-batch33-p1")
        val p2Input = programInput(sourceJs = "draw(2);", basis = "basis-batch33-p2")
        val p3Input = programInput(sourceJs = "draw(3);", basis = "basis-batch33-p3")

        val first = repository.commitAssistantTurnWithVisual(
            message = assistantCommand("message-c4-1"),
            program = p1Input,
            snapshot = snapshotInput("a"),
            ownerRequestId = "owner-c4-1",
        )
        val second = repository.commitAssistantTurnWithVisual(
            message = assistantCommand("message-c4-2"),
            program = p1Input,
            snapshot = snapshotInput("b"),
            ownerRequestId = "owner-c4-2",
        )
        val stagedInitial = repository.stageSnapshot(
            program = p2Input,
            snapshot = snapshotInput("c"),
            ownerRequestId = "owner-c4-3",
        )
        val p3Write = programWrite(p3Input)
        val stagedIntermediate = store.stageTutorVisualSnapshot(
            StageTutorVisualSnapshotCommand(
                program = p3Write,
                snapshot = snapshotWrite(
                    programId = p3Write.programId,
                    drawTag = "d",
                    checkpointKind = null,
                ),
                ownerRequestId = "owner-c4-4",
            ),
        )
        assertEquals("P1 首张是 INITIAL", "INITIAL", first.snapshot.checkpointKind)
        assertEquals("P1 第二张是 LATEST", "LATEST", second.snapshot.checkpointKind)
        assertEquals("P2 首张 stage 是 STAGING/INITIAL", "STAGING", stagedInitial.status)
        assertEquals("P3 中间态是 STAGING", "STAGING", stagedIntermediate.status)
        assertEquals("P3 中间态没有 checkpoint 角色", null, stagedIntermediate.checkpointKind)

        insertRevisionPin(programId = first.snapshot.programId, snapshotId = first.snapshot.snapshotId)

        return VisualFixture(
            p1 = first.snapshot.programId,
            p2 = stagedInitial.programId,
            p3 = p3Write.programId,
            s1 = first.snapshot.snapshotId,
            s2 = second.snapshot.snapshotId,
            s3 = stagedInitial.snapshotId,
            s4 = stagedIntermediate.snapshotId,
        )
    }

    private fun seedBundle() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-c4",
                canonicalFingerprint = "a".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = REVISION_ID,
                problemId = "problem-c4",
                revisionNumber = 1,
                title = "C4 视觉引用题",
                problemMarkdown = "求函数最值。",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "UNKNOWN",
                sourceType = "TEST",
                sourceReference = null,
                contentFingerprint = "b".repeat(64),
                createdAtEpochMillis = 1_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-c4",
                problemId = "problem-c4",
                problemRevisionId = REVISION_ID,
                unitKey = "unit:c4",
                unitKind = "PROBLEM",
                title = "C4 视觉引用题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-c4",
                practiceUnitId = "practice-c4",
                problemId = "problem-c4",
                currentRevisionId = REVISION_ID,
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = "kn-c4",
                stableCode = "MATH.FUNCTION",
                subject = "MATH",
                displayName = "函数",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "test-v1",
                createdAtEpochMillis = 1_000,
                canonicalName = "函数",
                verificationStatus = "CURATED",
            ),
        ),
    )

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

    private fun programInput(sourceJs: String, basis: String) = TutorVisualProgramInput(
        learnerId = LEARNER_ID,
        role = "QUESTION_DIAGRAM",
        sourceJs = sourceJs,
        initialStateJson = "{}",
        basisFingerprint = basis,
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
        basisRevisionId = null,
        requestSnapshot = null,
    )

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
        checkpointKind = "INITIAL",
    )

    private fun programWrite(input: TutorVisualProgramInput): TutorVisualProgramWrite {
        val programId = TutorVisualIdentity.deriveProgramId(
            sourceJs = input.sourceJs,
            initialStateJson = input.initialStateJson,
            basisFingerprint = input.basisFingerprint,
            apiVersion = input.apiVersion,
            sdkVersion = input.sdkVersion,
            role = input.role,
        )
        return TutorVisualProgramWrite(
            programId = programId,
            learnerId = input.learnerId,
            role = input.role,
            sourceSha256 = TutorVisualIdentity.sha256Hex(input.sourceJs),
            sourceJs = input.sourceJs,
            initialStateJson = input.initialStateJson,
            initialStateSha256 = TutorVisualIdentity.sha256Hex(input.initialStateJson),
            apiVersion = input.apiVersion,
            sdkVersion = input.sdkVersion,
            basisRevisionId = input.basisRevisionId,
            basisFingerprint = input.basisFingerprint,
            requestSnapshot = input.requestSnapshot,
            createdAtEpochMillis = NOW,
        )
    }

    private fun snapshotWrite(
        programId: String,
        drawTag: String,
        checkpointKind: String?,
    ): TutorVisualSnapshotWrite {
        val stateJson = "{}"
        val sceneJson = """{"scene":true}"""
        val drawJson = """{"ops":["$drawTag"]}"""
        val stateSha = TutorVisualIdentity.sha256Hex(stateJson)
        val drawSha = TutorVisualIdentity.sha256Hex(drawJson)
        return TutorVisualSnapshotWrite(
            snapshotId = TutorVisualIdentity.deriveSnapshotId(
                programId = programId,
                stateSha256 = stateSha,
                drawCommandsSha256 = drawSha,
                rendererVersion = "visual-renderer-v1",
                fontManifestVersion = "font-manifest-v1",
            ),
            programId = programId,
            stateJson = stateJson,
            stateSha256 = stateSha,
            sceneSchemaVersion = 1,
            sceneJson = sceneJson,
            sceneSha256 = TutorVisualIdentity.sha256Hex(sceneJson),
            renderSchemaVersion = 1,
            drawCommandsJson = drawJson,
            drawCommandsSha256 = drawSha,
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
            checkpointKind = checkpointKind,
            createdAtEpochMillis = NOW,
        )
    }

    private fun insertRevisionPin(programId: String, snapshotId: String) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                """
                INSERT INTO `problem_revision_visual`
                    (`problem_revision_id`, `role`, `ordinal`, `program_id`, `snapshot_id`,
                     `created_at_epoch_millis`)
                VALUES (?, 'QUESTION_DIAGRAM', 0, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(REVISION_ID, programId, snapshotId, NOW),
            )
        }
    }

    // ------------------------------------------------------------ backup / restore

    private suspend fun createArchive(
        store: StudyDatabasePort,
        selection: BackupSelection,
    ): ByteArray = ByteArrayOutputStream().also { output ->
        AndroidBackupRepository(context, store).create(output, BackupOptions(selection))
    }.toByteArray()

    private suspend fun restore(archive: ByteArray) {
        val port = StudyDatabaseFactory.open(context, databaseName)
        try {
            AndroidBackupRepository(context, port)
                .restore(ByteArrayInputStream(archive))
        } finally {
            runCatching { port.close() }
        }
    }

    private fun selectionOf(mask: Int): BackupSelection = BackupSelection.of(
        when (mask) {
            9 -> setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.IMAGES)
            13 -> setOf(
                BackupContentCategory.ISSUE_BOOK,
                BackupContentCategory.SESSIONS,
                BackupContentCategory.IMAGES,
            )
            else -> throw IllegalArgumentException("unsupported mask=$mask")
        },
    )

    private fun extractArchiveDatabaseFile(archive: ByteArray): File {
        val staging = File(context.cacheDir, "c4-archive-${System.nanoTime()}").apply { mkdirs() }
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == SmbkArchiveCodec.DATABASE_ENTRY) {
                    File(staging, "database.sqlite").outputStream().use { output ->
                        output.write(zip.readBytes())
                    }
                    break
                }
                zip.closeEntry()
            }
        }
        val file = File(staging, "database.sqlite")
        check(file.exists()) { "归档缺少 ${SmbkArchiveCodec.DATABASE_ENTRY}" }
        file.deleteOnExit()
        staging.deleteOnExit()
        return file
    }

    // ------------------------------------------------------------ checker

    private data class VisualRows(
        val programIds: Set<String> = emptySet(),
        val snapshotIds: Set<String> = emptySet(),
        val snapshotProgram: Map<String, String> = emptyMap(),
        val snapshotStatus: Map<String, String> = emptyMap(),
        val snapshotKind: Map<String, String?> = emptyMap(),
        val links: Set<Pair<String, Int>> = emptySet(),
        val linkProgram: Map<Pair<String, Int>, String> = emptyMap(),
        val linkSnapshot: Map<Pair<String, Int>, String> = emptyMap(),
        val pins: Set<Triple<String, String, Int>> = emptySet(),
        val pinProgram: Map<Triple<String, String, Int>, String> = emptyMap(),
        val pinSnapshot: Map<Triple<String, String, Int>, String> = emptyMap(),
        val programRows: Map<String, List<String?>> = emptyMap(),
        val snapshotRows: Map<String, List<String?>> = emptyMap(),
        val linkRows: Map<Pair<String, Int>, List<String?>> = emptyMap(),
        val pinRows: Map<Triple<String, String, Int>, List<String?>> = emptyMap(),
        val danglingBasisRevisionIds: Set<String> = emptySet(),
        val violations: List<String> = emptyList(),
    )

    private data class ExpectedVisualRows(
        val programs: Set<String>,
        val snapshots: Set<String>,
        val links: Set<Pair<String, Int>>,
        val pins: Set<Triple<String, String, Int>>,
    )

    private fun readVisualRows(file: File): VisualRows {
        check(file.exists()) { "数据库文件不存在：$file" }
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val present = FOUR_TABLES.associateWith { hasTable(db, it) }

            val programRows = LinkedHashMap<String, List<String?>>()
            if (present.getValue(PROGRAM_TABLE)) {
                db.rawQuery(
                    """
                    SELECT program_id, learner_id, role, source_sha256, source_js,
                           initial_state_json, initial_state_sha256, api_version, sdk_version,
                           basis_revision_id, basis_fingerprint, request_snapshot,
                           created_at_epoch_millis
                    FROM tutor_visual_program ORDER BY program_id
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        programRows[cursor.getString(0)] =
                            (1 until cursor.columnCount).map { cursor.getString(it) }
                    }
                }
            }

            val snapshotRows = LinkedHashMap<String, List<String?>>()
            val snapshotProgram = LinkedHashMap<String, String>()
            val snapshotStatus = LinkedHashMap<String, String>()
            val snapshotKind = LinkedHashMap<String, String?>()
            if (present.getValue(SNAPSHOT_TABLE)) {
                db.rawQuery(
                    """
                    SELECT snapshot_id, program_id, state_json, state_sha256,
                           scene_schema_version, scene_json, scene_sha256,
                           render_schema_version, draw_commands_json, draw_commands_sha256,
                           content_ids_json, viewport_width_du, viewport_height_du,
                           font_scale_milli, theme, time_ms, seed, runtime_version,
                           compiler_version, renderer_version, font_manifest_version,
                           question_verification_status, preview_asset_id, status,
                           owner_request_id, checkpoint_kind, created_at_epoch_millis,
                           promoted_at_epoch_millis
                    FROM tutor_visual_snapshot ORDER BY snapshot_id
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        snapshotRows[id] = (2 until cursor.columnCount).map { cursor.getString(it) }
                        snapshotProgram[id] = cursor.getString(1)
                        snapshotStatus[id] = cursor.getString(23)
                        snapshotKind[id] = cursor.getString(25)
                    }
                }
            }

            val linkRows = LinkedHashMap<Pair<String, Int>, List<String?>>()
            val linkProgram = LinkedHashMap<Pair<String, Int>, String>()
            val linkSnapshot = LinkedHashMap<Pair<String, Int>, String>()
            if (present.getValue(LINK_TABLE)) {
                db.rawQuery(
                    """
                    SELECT message_id, ordinal, program_id, current_snapshot_id, revision,
                           ui_state_json, form_digest, created_at_epoch_millis,
                           updated_at_epoch_millis
                    FROM tutor_message_visual ORDER BY message_id, ordinal
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(0) to cursor.getInt(1)
                        linkRows[key] = (2 until cursor.columnCount).map { cursor.getString(it) }
                        linkProgram[key] = cursor.getString(2)
                        linkSnapshot[key] = cursor.getString(3)
                    }
                }
            }

            val pinRows = LinkedHashMap<Triple<String, String, Int>, List<String?>>()
            val pinProgram = LinkedHashMap<Triple<String, String, Int>, String>()
            val pinSnapshot = LinkedHashMap<Triple<String, String, Int>, String>()
            if (present.getValue(PIN_TABLE)) {
                db.rawQuery(
                    """
                    SELECT problem_revision_id, role, ordinal, program_id, snapshot_id,
                           created_at_epoch_millis
                    FROM problem_revision_visual
                    ORDER BY problem_revision_id, role, ordinal
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = Triple(cursor.getString(0), cursor.getString(1), cursor.getInt(2))
                        pinRows[key] = (3 until cursor.columnCount).map { cursor.getString(it) }
                        pinProgram[key] = cursor.getString(3)
                        pinSnapshot[key] = cursor.getString(4)
                    }
                }
            }

            val violations = mutableListOf<String>()
            if (present.getValue(PROGRAM_TABLE) && present.getValue(SNAPSHOT_TABLE)) {
                collectViolation(
                    db,
                    "V-R5/R6/R7 孤儿引用",
                    """
                    SELECT link.message_id, link.ordinal, 'snapshot'
                    FROM tutor_message_visual AS link
                    LEFT JOIN tutor_visual_snapshot AS s ON s.snapshot_id = link.current_snapshot_id
                    WHERE s.snapshot_id IS NULL
                    UNION ALL
                    SELECT r.problem_revision_id, r.ordinal, 'snapshot'
                    FROM problem_revision_visual AS r
                    LEFT JOIN tutor_visual_snapshot AS s ON s.snapshot_id = r.snapshot_id
                    WHERE s.snapshot_id IS NULL
                    UNION ALL
                    SELECT link.message_id, link.ordinal, 'program'
                    FROM tutor_message_visual AS link
                    LEFT JOIN tutor_visual_program AS p ON p.program_id = link.program_id
                    WHERE p.program_id IS NULL
                    UNION ALL
                    SELECT r.problem_revision_id, r.ordinal, 'program'
                    FROM problem_revision_visual AS r
                    LEFT JOIN tutor_visual_program AS p ON p.program_id = r.program_id
                    WHERE p.program_id IS NULL
                    UNION ALL
                    SELECT s.snapshot_id, 0, 'program'
                    FROM tutor_visual_snapshot AS s
                    LEFT JOIN tutor_visual_program AS p ON p.program_id = s.program_id
                    WHERE p.program_id IS NULL
                    """.trimIndent(),
                )?.let(violations::add)
            }
            if (present.getValue(LINK_TABLE) && present.getValue(SNAPSHOT_TABLE)) {
                collectViolation(
                    db,
                    "V-R8/R9 复合 program 一致",
                    """
                    SELECT link.message_id, link.ordinal, 'message'
                    FROM tutor_message_visual AS link
                    JOIN tutor_visual_snapshot AS s ON s.snapshot_id = link.current_snapshot_id
                    WHERE s.program_id <> link.program_id
                    UNION ALL
                    SELECT r.problem_revision_id, r.ordinal, 'revision'
                    FROM problem_revision_visual AS r
                    JOIN tutor_visual_snapshot AS s ON s.snapshot_id = r.snapshot_id
                    WHERE s.program_id <> r.program_id
                    """.trimIndent(),
                )?.let(violations::add)
            }
            if (present.getValue(LINK_TABLE)) {
                collectViolation(
                    db,
                    "V-R14 被引用快照必须 LIVE",
                    """
                    SELECT 'message', link.message_id, link.ordinal
                    FROM tutor_message_visual AS link
                    JOIN tutor_visual_snapshot AS s ON s.snapshot_id = link.current_snapshot_id
                    WHERE s.status <> 'LIVE'
                    UNION ALL
                    SELECT 'revision', r.problem_revision_id, r.ordinal
                    FROM problem_revision_visual AS r
                    JOIN tutor_visual_snapshot AS s ON s.snapshot_id = r.snapshot_id
                    WHERE s.status <> 'LIVE'
                    """.trimIndent(),
                )?.let(violations::add)
            }
            if (present.getValue(SNAPSHOT_TABLE)) {
                collectViolation(
                    db,
                    "V-R12/R13 状态与晋升配对",
                    """
                    SELECT snapshot_id, status, promoted_at_epoch_millis
                    FROM tutor_visual_snapshot
                    WHERE status NOT IN ('STAGING','LIVE')
                       OR (status = 'LIVE' AND promoted_at_epoch_millis IS NULL)
                       OR (status = 'STAGING' AND promoted_at_epoch_millis IS NOT NULL)
                    """.trimIndent(),
                )?.let(violations::add)
                collectViolation(
                    db,
                    "V-R15 checkpoint 角色域",
                    """
                    SELECT snapshot_id, checkpoint_kind
                    FROM tutor_visual_snapshot
                    WHERE checkpoint_kind IS NOT NULL
                      AND checkpoint_kind NOT IN ('INITIAL','LATEST')
                    """.trimIndent(),
                )?.let(violations::add)
                collectViolation(
                    db,
                    "V-R10 预览资产必须可解析",
                    """
                    SELECT s.snapshot_id, s.preview_asset_id
                    FROM tutor_visual_snapshot AS s
                    LEFT JOIN canonical_source_asset AS a ON a.source_asset_id = s.preview_asset_id
                    WHERE s.preview_asset_id IS NOT NULL AND a.source_asset_id IS NULL
                    """.trimIndent(),
                )?.let(violations::add)
                collectViolation(
                    db,
                    "V-R21 内容唯一键重复",
                    """
                    SELECT program_id, state_sha256, draw_commands_sha256,
                           renderer_version, font_manifest_version, COUNT(*) AS n
                    FROM tutor_visual_snapshot
                    GROUP BY program_id, state_sha256, draw_commands_sha256,
                             renderer_version, font_manifest_version
                    HAVING n > 1
                    """.trimIndent(),
                )?.let(violations::add)
            }
            if (present.getValue(LINK_TABLE)) {
                collectViolation(
                    db,
                    "V-R16 link revision 必须 >= 1",
                    "SELECT message_id, ordinal, revision FROM tutor_message_visual WHERE revision < 1",
                )?.let(violations::add)
            }
            if (present.getValue(SNAPSHOT_TABLE)) {
                collectViolation(
                    db,
                    "V-R2 外键",
                    "PRAGMA foreign_key_check",
                )?.let(violations::add)
            }
            if (present.getValue(PROGRAM_TABLE)) {
                collectViolation(
                    db,
                    "V-R11 软引用不得新增悬空",
                    """
                    SELECT p.program_id, p.basis_revision_id
                    FROM tutor_visual_program AS p
                    WHERE p.basis_revision_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM problem_revision AS r
                          WHERE r.revision_id = p.basis_revision_id
                      )
                    """.trimIndent(),
                )?.let(violations::add)
            }

            // V-R17 program 身份重派生。
            if (present.getValue(PROGRAM_TABLE)) {
                db.rawQuery(
                    """
                    SELECT program_id, source_js, initial_state_json, basis_fingerprint,
                           api_version, sdk_version, role
                    FROM tutor_visual_program
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val derived = TutorVisualIdentity.deriveProgramId(
                            sourceJs = cursor.getString(1),
                            initialStateJson = cursor.getString(2),
                            basisFingerprint = cursor.getString(3),
                            apiVersion = cursor.getInt(4),
                            sdkVersion = cursor.getString(5),
                            role = cursor.getString(6),
                        )
                        if (derived != cursor.getString(0)) {
                            violations += "V-R17 program_id 与内容不符：${cursor.getString(0)}"
                        }
                    }
                }
            }
            // V-R18/R19/R20 snapshot 身份与三条摘要。
            if (present.getValue(SNAPSHOT_TABLE)) {
                db.rawQuery(
                    """
                    SELECT snapshot_id, program_id, state_json, state_sha256,
                           scene_json, scene_sha256, draw_commands_json, draw_commands_sha256,
                           renderer_version, font_manifest_version
                    FROM tutor_visual_snapshot
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val programId = cursor.getString(1)
                        val stateJson = cursor.getString(2)
                        val stateSha = cursor.getString(3)
                        val sceneJson = cursor.getString(4)
                        val sceneSha = cursor.getString(5)
                        val drawJson = cursor.getString(6)
                        val drawSha = cursor.getString(7)
                        if (stateSha != sha256Hex(stateJson)) {
                            violations += "V-R19 state_sha256 与内容不符：$id"
                        }
                        if (drawSha != sha256Hex(drawJson)) {
                            violations += "V-R19 draw_commands_sha256 与内容不符：$id"
                        }
                        if (sceneSha != sha256Hex(sceneJson)) {
                            violations += "V-R20 scene_sha256 与内容不符：$id"
                        }
                        val derived = TutorVisualIdentity.deriveSnapshotId(
                            programId = programId,
                            stateSha256 = stateSha,
                            drawCommandsSha256 = drawSha,
                            rendererVersion = cursor.getString(8),
                            fontManifestVersion = cursor.getString(9),
                        )
                        if (derived != id) {
                            violations += "V-R18 snapshot_id 与内容不符：$id"
                        }
                    }
                }
            }

            val dangling = mutableSetOf<String>()
            if (present.getValue(PROGRAM_TABLE)) {
                db.rawQuery(
                    """
                    SELECT program_id FROM tutor_visual_program AS p
                    WHERE p.basis_revision_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM problem_revision AS r
                          WHERE r.revision_id = p.basis_revision_id
                      )
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) dangling += cursor.getString(0)
                }
            }

            return VisualRows(
                programIds = programRows.keys.toSet(),
                snapshotIds = snapshotRows.keys.toSet(),
                snapshotProgram = snapshotProgram,
                snapshotStatus = snapshotStatus,
                snapshotKind = snapshotKind,
                links = linkRows.keys.toSet(),
                linkProgram = linkProgram,
                linkSnapshot = linkSnapshot,
                pins = pinRows.keys.toSet(),
                pinProgram = pinProgram,
                pinSnapshot = pinSnapshot,
                programRows = programRows,
                snapshotRows = snapshotRows,
                linkRows = linkRows,
                pinRows = pinRows,
                danglingBasisRevisionIds = dangling,
                violations = violations,
            )
        }
    }

    /** C4 研究 §1.3 的保留集公式，用 Kotlin 从源行计算（不复制 SQL）。 */
    private fun expectedRows(source: VisualRows, mask: Int): ExpectedVisualRows {
        require(mask == 9 || mask == 13 || mask == 15) { "unsupported mask=$mask" }
        val sessions = mask and 4 != 0
        val issueBook = mask and 1 != 0
        if (mask == 15) {
            return ExpectedVisualRows(
                programs = source.programIds,
                snapshots = source.snapshotIds,
                links = source.links,
                pins = source.pins,
            )
        }
        val keptSnapshots = buildSet {
            if (sessions) source.linkSnapshot.values.forEach(::add)
            if (issueBook) source.pinSnapshot.values.forEach(::add)
            source.snapshotKind.forEach { (id, kind) -> if (kind == "INITIAL") add(id) }
        }
        val keptPrograms = buildSet {
            if (sessions) source.linkProgram.values.forEach(::add)
            if (issueBook) source.pinProgram.values.forEach(::add)
            keptSnapshots.forEach { id -> add(source.snapshotProgram.getValue(id)) }
        }
        return ExpectedVisualRows(
            programs = keptPrograms,
            snapshots = keptSnapshots,
            links = if (sessions) source.links else emptySet(),
            pins = if (issueBook) source.pins else emptySet(),
        )
    }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun collectViolation(db: SQLiteDatabase, id: String, sql: String): String? =
        db.rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val sample = (0 until minOf(cursor.columnCount, 4))
                .joinToString("|") { cursor.getString(it) ?: "NULL" }
            "$id：$sample"
        }

    private fun integrityCheck(file: File): String =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst()) "no-result" else cursor.getString(0)
            }
        }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    // ------------------------------------------------------------ third-party repack

    /**
     * 第三方重裁/拼接包夹具（与 `BackupSelectionRestoreInstrumentedTest.repackWithModifiedDatabase`
     * 同形）：解开一份合法 v3 包，直接改包内库（改完 checkpoint 回主文件），再按改动后的库如实
     * 改写 manifest 的 `rowCounts`/`closureDigest`/`database.sqlite` 摘要与 `checksums.sha256`，
     * 绕过成包侧闸门，只让恢复侧校验去抓。
     */
    private fun repackWithModifiedDatabase(
        archive: ByteArray,
        mutateDatabase: (SQLiteDatabase) -> Unit,
        mutateSelectionCounts: (MutableMap<String, Long>) -> Unit,
    ): ByteArray {
        val staging = File(context.cacheDir, "c4-repack-${System.nanoTime()}").apply { mkdirs() }
        try {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(staging, entry.name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> output.write(zip.readBytes()) }
                    zip.closeEntry()
                }
            }
            val databaseFile = File(staging, SmbkArchiveCodec.DATABASE_ENTRY)
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                mutateDatabase(db)
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }

            val manifest = JSONObject(manifestJson(archive))
            val selection = manifest.getJSONObject("selection")
            val counts = rowCountsOf(selection).toMutableMap().also(mutateSelectionCounts)
            val rewrittenCounts = JSONObject()
            counts.forEach { (table, count) -> rewrittenCounts.put(table, count) }
            selection.put("rowCounts", rewrittenCounts)
            selection.put(
                "closureDigest",
                SmbkSelectionPlan.closureDigest(
                    categories = SmbkSelectionPlan.categoriesOf(selection.getInt("categoryMask")),
                    assetPolicy = selection.getString("assetPolicy"),
                    rowCounts = counts,
                ),
            )
            val files = manifest.getJSONArray("files")
            val databaseSha = sha256Hex(databaseFile.readBytes())
            val checksums = StringBuilder()
            for (index in 0 until files.length()) {
                val record = files.getJSONObject(index)
                if (record.getString("path") == SmbkArchiveCodec.DATABASE_ENTRY) {
                    record.put("sha256", databaseSha)
                    record.put("byteSize", databaseFile.length())
                }
                if (checksums.isNotEmpty()) checksums.append('\n')
                checksums.append(record.getString("sha256"))
                    .append(" *")
                    .append(record.getString("path"))
            }

            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zipOutput ->
                zipOutput.putNextEntry(ZipEntry(SmbkArchiveCodec.MANIFEST_ENTRY))
                zipOutput.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zipOutput.closeEntry()
                zipOutput.putNextEntry(ZipEntry(SmbkArchiveCodec.DATABASE_ENTRY))
                zipOutput.write(databaseFile.readBytes())
                zipOutput.closeEntry()
                File(staging, "assets").listFiles().orEmpty()
                    .filter { it.isFile }
                    .sortedBy { it.name }
                    .forEach { asset ->
                        zipOutput.putNextEntry(ZipEntry("assets/${asset.name}"))
                        zipOutput.write(asset.readBytes())
                        zipOutput.closeEntry()
                    }
                zipOutput.putNextEntry(ZipEntry("checksums.sha256"))
                zipOutput.write(checksums.toString().toByteArray(Charsets.UTF_8))
                zipOutput.closeEntry()
            }
            return output.toByteArray()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun manifestJson(archive: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == SmbkArchiveCodec.MANIFEST_ENTRY) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        error("归档缺少 manifest.json")
    }

    private fun rowCountsOf(selection: JSONObject): Map<String, Long> {
        val rowCounts = selection.getJSONObject("rowCounts")
        val keys = rowCounts.keys()
        val counts = LinkedHashMap<String, Long>()
        while (keys.hasNext()) {
            val key = keys.next()
            counts[key] = rowCounts.getLong(key)
        }
        return counts
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val PROGRAM_TABLE = "tutor_visual_program"
        const val SNAPSHOT_TABLE = "tutor_visual_snapshot"
        const val LINK_TABLE = "tutor_message_visual"
        const val PIN_TABLE = "problem_revision_visual"

        val FOUR_TABLES = listOf(PROGRAM_TABLE, SNAPSHOT_TABLE, LINK_TABLE, PIN_TABLE)

        const val LEARNER_ID = "learner-batch33-c4"
        const val CONVERSATION_ID = "conversation-batch33-c4"
        const val REVISION_ID = "revision-backup"
        const val NOW = 1_700_000_000_000L
    }
}
