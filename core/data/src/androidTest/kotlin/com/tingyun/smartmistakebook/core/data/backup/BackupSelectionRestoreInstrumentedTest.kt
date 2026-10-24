package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.KnowledgeSearchIndexRebuildPort
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 6C-2 恢复侧验收（T5）。
 *
 * 钉住四件事：
 * 1. 合法子集（mask 9/13）恢复：回执如实带 selection，声明与包内库实测一致；
 * 2. 非法包（digest/rowCounts/closureVersion/mask/声明撒谎）在**交换前**拒绝，原库不动；
 * 3. 派生索引两张表**恒裁**（全选 v3 与旧 v2 包也一样），恢复后是 0 行；
 * 4. 数据库重开后按科重建索引：标记被消费、检索可用；重建失败不清标记、不回滚数据库。
 *
 * 库用生产 Room 工厂（schema 69）创建，种子行经测试夹具直写；不触碰生产数据。
 */
@RunWith(AndroidJUnit4::class)
class BackupSelectionRestoreInstrumentedTest {

    private lateinit var context: Context
    private val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @Test
    fun subsetNineRestoreReportsSelectionTrimsDerivedIndexAndRebuildsAfterReopen() = runBlocking {
        val database = openSeededDatabase()
        insertTutorConversation()
        insertDerivedIndexRows()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))
        database.close()

        val receipt = restore(archive)
        assertEquals(9, receipt.selection?.mask)
        assertEquals(issueBookAndImages(), receipt.selection?.categories)
        assertEquals(1, receipt.problemCount)

        val restoredFile = context.getDatabasePath(databaseName)
        assertEquals("未勾选的会话必须被裁净", 0L, rowCount(restoredFile, "tutor_conversation"))
        assertEquals("派生索引恒裁", 0L, rowCount(restoredFile, "knowledge_search_feature"))
        assertEquals("派生索引恒裁", 0L, rowCount(restoredFile, "knowledge_search_index_state"))
        assertTrue("恢复后必须留下待重建标记", RestoreIndexRebuildMarker.exists(context))

        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            val outcome = RestoreIndexRebuildCoordinator.rebuildPending(context, reopened)
            assertEquals(RestoreIndexRebuildStatus.REBUILT, outcome.status)
            assertEquals(listOf("MATH"), outcome.rebuiltSubjects)
            assertEquals(1L, rowCount(restoredFile, "knowledge_search_index_state"))
            assertTrue(rowCount(restoredFile, "knowledge_search_feature") > 0L)
            assertEquals(
                KnowledgeSearchFeatureExtractor.INDEX_VERSION,
                indexVersionOf(restoredFile, "MATH"),
            )
            assertFalse("重建成功后标记必须清除", RestoreIndexRebuildMarker.exists(context))

            val recalled = reopened.readSubjectKnowledgeRecallCandidates(
                subject = "MATH",
                searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion("函数"),
                limit = 10,
            )
            assertTrue(
                "重建后必须能检索到恢复进来的知识点，实际 ${recalled.map { it.knowledgeNodeId }}",
                recalled.any { it.knowledgeNodeId == KNOWLEDGE_NODE_ID },
            )
        } finally {
            reopened.close()
        }
    }

    @Test
    fun subsetThirteenRestoreKeepsSessionsAndStillTrimsDerivedIndex() = runBlocking {
        val database = openSeededDatabase()
        insertTutorConversation()
        insertDerivedIndexRows()
        val archive = createArchive(database, BackupSelection.of(issueBookSessionsAndImages()))
        database.close()

        val receipt = restore(archive)
        assertEquals(13, receipt.selection?.mask)
        assertEquals(issueBookSessionsAndImages(), receipt.selection?.categories)

        val restoredFile = context.getDatabasePath(databaseName)
        assertEquals("选中的会话必须保留", 1L, rowCount(restoredFile, "tutor_conversation"))
        assertEquals(1L, rowCount(restoredFile, "problem"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_feature"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_index_state"))
        assertTrue(RestoreIndexRebuildMarker.exists(context))
    }

    @Test
    fun fullSelectionPackageAlsoTrimsDerivedRowsAndRestoreKeepsThemEmpty() = runBlocking {
        val database = openSeededDatabase()
        insertDerivedIndexRows()
        val archive = createArchive(database, BackupSelection.FULL)
        database.close()

        // 全选也走裁剪器（恒裁）：派生索引不随包；恢复侧的清理是旧包/重裁包的兜底。
        val manifest = JSONObject(manifestJson(archive))
        val selection = manifest.getJSONObject("selection")
        assertEquals(2, selection.getInt("trimmedTableCount"))
        val counts = rowCountsOf(selection)
        assertEquals(0L, counts.getValue("knowledge_search_index_state"))
        assertEquals(0L, counts.getValue("knowledge_search_feature"))

        val receipt = restore(archive)
        assertEquals(15, receipt.selection?.mask)
        val restoredFile = context.getDatabasePath(databaseName)
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_feature"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_index_state"))
        assertTrue(RestoreIndexRebuildMarker.exists(context))
    }

    @Test
    fun legacyV2ArchiveWithoutSelectionRestoresAsFullAndTrimsDerivedIndex() = runBlocking {
        val database = openSeededDatabase()
        insertDerivedIndexRows()
        val archive = createArchive(database, BackupSelection.FULL)
        database.close()

        // 旧包形状：formatVersion=2、没有 selection 键 = 整库全量（v1/v2 语义）。
        val legacyManifest = JSONObject(manifestJson(archive)).apply {
            put("formatVersion", 2)
            remove("selection")
        }
        val legacyArchive = rewriteManifest(archive, legacyManifest.toString())

        val validationDatabase = StudyDatabaseFactory.open(context, databaseName)
        val validation = try {
            AndroidBackupRepository(context, validationDatabase)
                .validate(ByteArrayInputStream(legacyArchive))
        } finally {
            validationDatabase.close()
        }
        assertTrue("v2 旧包必须仍然可读：$validation", validation is BackupValidation.Valid)
        assertNull(
            "v1/v2 缺 selection 读作全量",
            (validation as BackupValidation.Valid).manifest.selection,
        )

        val receipt = restore(legacyArchive)
        assertNull("旧包回执的 selection 必须是 null（= 全量）", receipt.selection)
        val restoredFile = context.getDatabasePath(databaseName)
        assertEquals(1L, rowCount(restoredFile, "problem"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_feature"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_index_state"))
        assertTrue(RestoreIndexRebuildMarker.exists(context))

        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            val outcome = RestoreIndexRebuildCoordinator.rebuildPending(context, reopened)
            assertEquals(RestoreIndexRebuildStatus.REBUILT, outcome.status)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun invalidDigestRowCountsAndClosureVersionAreRejectedBeforeSwapAndLiveDataStays() = runBlocking {
        val database = openSeededDatabase()
        insertTutorConversation()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))

        val tampered: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "closureDigest" to { manifest: JSONObject ->
                manifest.getJSONObject("selection").put("closureDigest", "0".repeat(64))
            },
            "rowCounts 漏表" to { manifest: JSONObject ->
                manifest.getJSONObject("selection").getJSONObject("rowCounts").remove("problem")
            },
            "closureVersion=1" to { manifest: JSONObject ->
                manifest.getJSONObject("selection").put("closureVersion", 1)
            },
            "schemaVersion=1" to { manifest: JSONObject ->
                manifest.getJSONObject("selection").put("schemaVersion", 1)
            },
            "mask=8 仅图片" to { manifest: JSONObject ->
                manifest.getJSONObject("selection").apply {
                    put("categoryMask", 8)
                    put("categories", JSONArray(listOf("IMAGES")))
                }
            },
            "assetPolicy=ABSENT" to { manifest: JSONObject ->
                manifest.getJSONObject("selection").put("assetPolicy", "ABSENT")
            },
        )

        try {
            tampered.forEach { (label, mutate) ->
                val broken = rewriteManifest(archive, mutateOn(archive, mutate))
                val repository = AndroidBackupRepository(context, database)
                val validation = repository.validate(ByteArrayInputStream(broken))
                assertTrue("$label 必须在校验期拒绝：$validation", validation is BackupValidation.Invalid)
                assertTrue(
                    "$label 的拒因必须说明选择非法：${(validation as BackupValidation.Invalid).reason}",
                    validation.reason.contains("备份内容选择"),
                )

                val failure = assertThrows(BackupRestoreException::class.java) {
                    runBlocking { repository.restore(ByteArrayInputStream(broken)) }
                }
                assertTrue(
                    "$label 必须在交换前拒绝：${failure.message}",
                    failure.message.orEmpty().contains("备份内容选择"),
                )
                assertEquals(
                    "$label 失败后原库必须原样可读",
                    1,
                    database.countMistakes(),
                )
                assertFalse(RestoreIndexRebuildMarker.exists(context))
                assertFalse(
                    "$label 不得留下上一代数据库",
                    File("${context.getDatabasePath(databaseName).absolutePath}.prev").exists(),
                )
            }
        } finally {
            database.close()
        }
    }

    /**
     * 声明撒谎：包内实际带会话，但清单改成 mask=9 并按篡改后的 rowCounts 重算 digest。
     * 自洽性检查（Phase 1）会放过，**包内库实测**（Phase 2c.1）必须拒绝。
     */
    @Test
    fun declaredSubsetThatStillContainsUnselectedRowsIsRejectedByMeasuredData() = runBlocking {
        val database = openSeededDatabase()
        insertTutorConversation()
        val archive = createArchive(database, BackupSelection.FULL)
        try {
            val manifest = JSONObject(manifestJson(archive))
            val selection = manifest.getJSONObject("selection")
            selection.put("categoryMask", 9)
            selection.put("categories", JSONArray(listOf("ISSUE_BOOK", "IMAGES")))
            selection.put(
                "closureDigest",
                SmbkSelectionPlan.closureDigest(
                    categories = SmbkSelectionPlan.categoriesOf(9),
                    assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                    rowCounts = rowCountsOf(selection),
                ),
            )
            val lyingArchive = rewriteManifest(archive, manifest.toString())

            val repository = AndroidBackupRepository(context, database)
            val selfConsistent = repository.validate(ByteArrayInputStream(lyingArchive))
            assertTrue(
                "篡改后的清单必须自洽到能通过 Phase 1：$selfConsistent",
                selfConsistent is BackupValidation.Valid,
            )

            val failure = assertThrows(BackupRestoreException::class.java) {
                runBlocking { repository.restore(ByteArrayInputStream(lyingArchive)) }
            }
            assertTrue(
                "拒因必须指向声明与包内数据不一致：${failure.message}",
                failure.message.orEmpty().contains("内容选择"),
            )
            assertEquals("拒绝后原库不动", 1, database.countMistakes())
        } finally {
            database.close()
        }
    }

    /**
     * mask=15 时没有"未勾选类别"可查，旧实现会直接跳过包内库核对。这里把清单的
     * `tutor_conversation` 行数改成 0 并重算 digest（Phase 1 自洽通过），Phase 2c.1 的
     * **逐表行数对拍**必须拒绝——否则一份被改写成"全量"的子集/残缺包会按全量恢复并
     * 清空本机数据，而回执仍显示全量。
     */
    @Test
    fun fullSelectionWithRowCountsThatDisagreeWithTheStagedDatabaseIsRejected() = runBlocking {
        val database = openSeededDatabase()
        insertTutorConversation()
        val archive = createArchive(database, BackupSelection.FULL)
        try {
            val manifest = JSONObject(manifestJson(archive))
            val selection = manifest.getJSONObject("selection")
            val counts = rowCountsOf(selection).toMutableMap()
            assertTrue(
                "夹具必须真的带一行会话，实际 ${counts["tutor_conversation"]}",
                (counts["tutor_conversation"] ?: 0L) > 0L,
            )
            counts["tutor_conversation"] = 0L
            val rewrittenCounts = JSONObject()
            counts.forEach { (table, count) -> rewrittenCounts.put(table, count) }
            selection.put("rowCounts", rewrittenCounts)
            selection.put(
                "closureDigest",
                SmbkSelectionPlan.closureDigest(
                    categories = SmbkSelectionPlan.categoriesOf(15),
                    assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                    rowCounts = counts,
                ),
            )
            val lyingArchive = rewriteManifest(archive, manifest.toString())

            val repository = AndroidBackupRepository(context, database)
            val selfConsistent = repository.validate(ByteArrayInputStream(lyingArchive))
            assertTrue(
                "篡改后的清单必须自洽到能通过 Phase 1：$selfConsistent",
                selfConsistent is BackupValidation.Valid,
            )

            val failure = assertThrows(BackupRestoreException::class.java) {
                runBlocking { repository.restore(ByteArrayInputStream(lyingArchive)) }
            }
            assertTrue(
                "拒因必须指向逐表行数与包内库不符：${failure.message}",
                failure.message.orEmpty().contains("逐表行数与声明不符"),
            )
            assertEquals("拒绝后原库不动", 1, database.countMistakes())
        } finally {
            database.close()
        }
    }

    @Test
    fun rebuildFailureKeepsMarkerAndDoesNotRollBackTheRestoredDatabase() = runBlocking {
        val database = openSeededDatabase()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))
        database.close()
        restore(archive)

        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            val failing = object : KnowledgeSearchIndexRebuildPort {
                override suspend fun listKnowledgeSubjects(): List<String> = listOf("MATH")

                override suspend fun rebuildKnowledgeSearchIndex(subject: String) {
                    error("注入的重建失败")
                }
            }
            val outcome = RestoreIndexRebuildCoordinator.rebuildPending(context, failing)
            assertEquals(RestoreIndexRebuildStatus.PARTIAL_FAILURE, outcome.status)
            assertEquals(listOf("MATH"), outcome.failedSubjects)
            assertTrue(
                "回执必须如实说明标记已保留：${outcome.message}",
                outcome.message.contains("待重建标记已保留"),
            )
            assertTrue("重建失败不得清标记", RestoreIndexRebuildMarker.exists(context))
            assertEquals("重建失败不得回滚已恢复的数据", 1, reopened.countMistakes())

            val retry = RestoreIndexRebuildCoordinator.rebuildPending(context, reopened)
            assertEquals(RestoreIndexRebuildStatus.REBUILT, retry.status)
            assertFalse(RestoreIndexRebuildMarker.exists(context))
        } finally {
            reopened.close()
        }
    }

    /**
     * 批次 19 T2-4（设计 §3.2-S3）：第三方重裁/拼接的子集包——清单自洽（rowCounts 按拼接后
     * 的库如实改写、digest 重算，Phase 1 与 2c.1 都放过）、类别表也干净，但 split 表里留了
     * **未选类别 kind** 的任务行（讲题类 TUTOR_RESPOND，mask=9 未选会话）。此前没有任何
     * 恢复侧校验能抓到它（`model_task` 不是类别表，行数声明又与实测一致）；Phase 2c.3 的
     * 软引用重查必须在**交换前**拒绝，原库不动。
     */
    @Test
    fun thirdPartyRepackagedSubsetWithUnselectedTaskKindRowsIsRejectedBeforeSwap() = runBlocking {
        val database = openSeededDatabase()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))
        try {
            val lyingArchive = repackWithModifiedDatabase(
                archive = archive,
                mutateDatabase = { db ->
                    db.insertOrThrow(
                        "model_task_operation",
                        null,
                        ContentValues().apply {
                            put("operation_fingerprint", "op-lie-tutor")
                            put("subject_id", "t5-conversation")
                            put("task_kind", "TUTOR_RESPOND")
                            put("dispatch_count", 0)
                            put("created_at_epoch_millis", 1_000L)
                            put("updated_at_epoch_millis", 1_000L)
                        },
                    )
                    db.insertOrThrow(
                        "model_task",
                        null,
                        ContentValues().apply {
                            put("task_id", "task-lie-tutor")
                            put("request_id", "req-lie-tutor")
                            put("request_fingerprint", "fp-lie-tutor")
                            put("operation_fingerprint", "op-lie-tutor")
                            put("request_snapshot", "{}")
                            put("task_kind", "TUTOR_RESPOND")
                            put("subject_id", "t5-conversation")
                            put("status", "SUCCEEDED")
                            put("state_version", 1)
                            put("stage", "DONE")
                            put("user_message", "")
                            put("attempt_count", 0)
                            put("created_at_epoch_millis", 1_000L)
                            put("updated_at_epoch_millis", 1_000L)
                        },
                    )
                },
                mutateSelectionCounts = { counts ->
                    counts["model_task_operation"] = (counts["model_task_operation"] ?: 0L) + 1L
                    counts["model_task"] = (counts["model_task"] ?: 0L) + 1L
                },
            )

            val repository = AndroidBackupRepository(context, database)
            val selfConsistent = repository.validate(ByteArrayInputStream(lyingArchive))
            assertTrue(
                "拼接包必须自洽到能通过 Phase 1 与逐表行数对拍：$selfConsistent",
                selfConsistent is BackupValidation.Valid,
            )

            val failure = assertThrows(BackupRestoreException::class.java) {
                runBlocking { repository.restore(ByteArrayInputStream(lyingArchive)) }
            }
            assertTrue(
                "拒因必须指向软引用重查并点名 kind：${failure.message}",
                failure.message.orEmpty().contains("软引用与包内数据不一致") &&
                    failure.message.orEmpty().contains("TUTOR_RESPOND"),
            )
            assertEquals("拒绝后原库不动", 1, database.countMistakes())
        } finally {
            database.close()
        }
    }

    /**
     * 批次 19 T2-4（§9.5 O_keep 行级规则）：拼接包留了一条 SESSIONS 类 `source_kind` 的
     * figure operation，既没有产物在存活根引用里（O_ref）、也没有存活清图任务钉住
     * （O_seed 第三支）——合法裁剪器一定会删掉它。恢复侧重算 O_keep 后必须在交换前拒绝。
     */
    @Test
    fun thirdPartyRepackagedSubsetWithUnreferencedFigureOperationIsRejectedBeforeSwap() = runBlocking {
        val database = openSeededDatabase()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))
        try {
            val lyingArchive = repackWithModifiedDatabase(
                archive = archive,
                mutateDatabase = { db ->
                    db.insertOrThrow(
                        "figure_generation_operation",
                        null,
                        ContentValues().apply {
                            put("operation_id", "op-lie-figure")
                            put("learner_id", "learner:local")
                            put("source_kind", "TUTOR_MESSAGE")
                            put("source_id", "message-lie")
                            put("basis_fingerprint", "f".repeat(64))
                            put("purpose", "TUTOR_EXPLAIN")
                            put("policy_version", "test-v1")
                            put("status", "SUCCEEDED")
                            put("reserved_remote_dispatches", 0)
                            put("used_remote_dispatches", 0)
                            put("final_answer_reserved", 0)
                            put("created_at_epoch_millis", 1_000L)
                            put("updated_at_epoch_millis", 1_000L)
                        },
                    )
                },
                mutateSelectionCounts = { counts ->
                    counts["figure_generation_operation"] =
                        (counts["figure_generation_operation"] ?: 0L) + 1L
                },
            )

            val repository = AndroidBackupRepository(context, database)
            val selfConsistent = repository.validate(ByteArrayInputStream(lyingArchive))
            assertTrue(
                "拼接包必须自洽到能通过 Phase 1 与逐表行数对拍：$selfConsistent",
                selfConsistent is BackupValidation.Valid,
            )

            val failure = assertThrows(BackupRestoreException::class.java) {
                runBlocking { repository.restore(ByteArrayInputStream(lyingArchive)) }
            }
            assertTrue(
                "拒因必须点名 figure operation 的行级规则：${failure.message}",
                failure.message.orEmpty().contains("软引用与包内数据不一致") &&
                    failure.message.orEmpty().contains("figure_generation_operation 留有"),
            )
            assertEquals("拒绝后原库不动", 1, database.countMistakes())
        } finally {
            database.close()
        }
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private fun openSeededDatabase(): StudyDatabasePort {
        val database = StudyDatabaseFactory.open(context, databaseName)
        seedStudyFacts(context, database, databaseName, seedBundle())
        return database
    }

    private fun seedBundle() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-t5",
                canonicalFingerprint = "a".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-t5",
                problemId = "problem-t5",
                revisionNumber = 1,
                title = "恢复校验题",
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
                practiceUnitId = "practice-t5",
                problemId = "problem-t5",
                problemRevisionId = "revision-t5",
                unitKey = "unit:t5",
                unitKind = "PROBLEM",
                title = "恢复校验题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-t5",
                practiceUnitId = "practice-t5",
                problemId = "problem-t5",
                currentRevisionId = "revision-t5",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = KNOWLEDGE_NODE_ID,
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

    private fun issueBookAndImages(): Set<BackupContentCategory> = setOf(
        BackupContentCategory.ISSUE_BOOK,
        BackupContentCategory.IMAGES,
    )

    private fun issueBookSessionsAndImages(): Set<BackupContentCategory> = setOf(
        BackupContentCategory.ISSUE_BOOK,
        BackupContentCategory.SESSIONS,
        BackupContentCategory.IMAGES,
    )

    private suspend fun createArchive(
        database: StudyDatabasePort,
        selection: BackupSelection,
    ): ByteArray {
        val archive = ByteArrayOutputStream()
        AndroidBackupRepository(context, database).create(archive, BackupOptions(selection))
        return archive.toByteArray()
    }

    private suspend fun restore(archive: ByteArray): com.tingyun.smartmistakebook.core.domain.RestoreReceipt {
        // 恢复会关闭注入的连接池；夹具里直接开一个仓库专用实例，模拟"重启后进程"。
        val database = StudyDatabaseFactory.open(context, databaseName)
        return try {
            AndroidBackupRepository(context, database)
                .restore(ByteArrayInputStream(archive))
        } finally {
            // restore 成功时连接已被仓库关闭；失败时这里兜底。
            runCatching { database.close() }
        }
    }

    private fun insertTutorConversation() {
        withWritableDatabase { database ->
            database.insertOrThrow(
                "tutor_conversation",
                null,
                ContentValues().apply {
                    put("conversation_id", "t5-conversation")
                    put("anchor_kind", "TEXT_ONLY")
                    putNull("anchor_id")
                    putNull("anchor_revision_id")
                    putNull("anchor_problem_revision_id")
                    putNull("anchor_practice_unit_id")
                    put("status", "ACTIVE")
                    put("title", "T5 会话")
                    put("created_at_epoch_millis", 1_000L)
                    put("updated_at_epoch_millis", 1_000L)
                    put("last_turn_ordinal", 1)
                },
            )
        }
    }

    /** 派生索引行：全选包会带着它们；恢复侧必须照裁。 */
    private fun insertDerivedIndexRows() {
        withWritableDatabase { database ->
            database.insertOrThrow(
                "knowledge_search_index_state",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("index_version", KnowledgeSearchFeatureExtractor.INDEX_VERSION)
                },
            )
            database.insertOrThrow(
                "knowledge_search_feature",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("search_feature", "函数")
                    put("knowledge_node_id", KNOWLEDGE_NODE_ID)
                },
            )
        }
    }

    private fun withWritableDatabase(block: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use(block)
    }

    private fun rowCount(databaseFile: File, table: String): Long =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database -> DatabaseUtils.queryNumEntries(database, table) }

    private fun indexVersionOf(databaseFile: File, subject: String): Int =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery(
                "SELECT index_version FROM knowledge_search_index_state WHERE subject = ?",
                arrayOf(subject),
            ).use { cursor ->
                assertTrue("重建后必须有锚点行 subject=$subject", cursor.moveToFirst())
                cursor.getInt(0)
            }
        }

    private fun mutateOn(
        archive: ByteArray,
        mutate: (JSONObject) -> Unit,
    ): String = JSONObject(manifestJson(archive)).apply(mutate).toString()

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

    /** 只替换 manifest 条目；其余条目逐字节保留（checksums 不覆盖 manifest）。 */
    private fun rewriteManifest(archive: ByteArray, manifest: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            ZipOutputStream(output).use { zipOutput ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    zipOutput.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == SmbkArchiveCodec.MANIFEST_ENTRY) {
                        zipOutput.write(manifest.toByteArray(Charsets.UTF_8))
                    } else {
                        zipOutput.write(zip.readBytes())
                    }
                    zipOutput.closeEntry()
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    /**
     * 第三方重裁/拼接包夹具（批次 19 T2-4）：解开一份**合法** v3 包，直接改包内库
     * （改完 checkpoint 收回主文件），再按改动后的库如实改写 manifest 的
     * `rowCounts`/`closureDigest`/`database.sqlite` 摘要与 `checksums.sha256`——
     * 绕过一切成包侧闸门（裁剪器 I1–I7），只让**恢复侧**的校验去抓。
     */
    private fun repackWithModifiedDatabase(
        archive: ByteArray,
        mutateDatabase: (SQLiteDatabase) -> Unit,
        mutateSelectionCounts: (MutableMap<String, Long>) -> Unit,
    ): ByteArray {
        val staging = File(context.cacheDir, "repack-${System.nanoTime()}").apply { mkdirs() }
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
                // 删除/插入必须落回主文件：归档只打包 database.sqlite 一个文件。
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
                if (checksums.isNotEmpty()) {
                    checksums.append('\n')
                }
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

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

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

    private companion object {
        const val KNOWLEDGE_NODE_ID = "kn-t5-function"
    }
}
