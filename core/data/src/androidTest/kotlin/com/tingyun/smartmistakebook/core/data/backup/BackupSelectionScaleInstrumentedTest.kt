package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * D1 设备轨：365MiB 大库的全选 / 子集（mask=9）成包与恢复，以及裁剪空间预检。
 *
 * 与 [BackupLargeDatabaseScaleInstrumentedTest] 的差别：那条钉住全选成包/恢复与写门饥饿；
 * 这条钉住 6C-2 子集路径在大库上的真实行为：
 * 1. 全选（15）与子集（9）都能成包，且 manifest 的 selection 与实测行数自洽；
 * 2. 子集成包会把未选类别与派生索引裁净，且归档仍能通过自校验；
 * 3. 子集包能恢复，恢复后题在、会话/派生索引为 0、派生索引待重建标记存在；
 * 4. 裁剪空间预检在 365MiB 快照上拒绝「可用空间不足」，且不改动被检文件；
 * 5. 输出全选/子集成包与恢复的耗时到 logcat（tag=D1Scale），供 D1 台账登记。
 *
 * 载荷写进 `knowledge_node`（ALWAYS 表，子集不裁），因此全选/子集归档都保持大体量；
 * 验证的是「裁剪正确性 + 大库往返可行性」，不是压缩收益。测试专用表会被裁剪器按
 * "未登记普通表" fail-closed 拒绝（防未知表随包泄露），故不能另建 bloat 表。
 */
@RunWith(AndroidJUnit4::class)
class BackupSelectionScaleInstrumentedTest {

    private lateinit var context: Context
    private val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
    private val archives = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @After
    fun tearDown() {
        archives.forEach { it.delete() }
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @Test
    fun threeHundredSixtyFiveMiBFullAndSubsetBackupRestoreWithSpacePreflight() {
        runBlocking {
        val scaleMiB = InstrumentationRegistry.getArguments()
            .getString("smbkSelectionScaleMiB")
            ?.toIntOrNull()
            ?: DEFAULT_SCALE_MIB
        require(scaleMiB >= 1) { "smbkSelectionScaleMiB must be >= 1, actual=$scaleMiB" }

        val databaseFile = context.getDatabasePath(databaseName)
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            seedStudyFacts(context, database, databaseName, seed())
            insertTutorConversation()
            inflateDatabase(databaseFile, scaleMiB)
            // knowledge_search_feature.knowledge_node_id 是硬 FK：必须指向已存在的节点，
            // 否则裁剪器在删派生表前/后的 foreign_key_check 会拒绝。全选也会裁掉这两张
            // 派生表，但裁剪开始前快照自身必须引用闭合。
            insertDerivedIndexRows(knowledgeNodeId = "bloat-node-0")
            val inflatedBytes = databaseFile.length() +
                File("${databaseFile.absolutePath}-wal").let { if (it.isFile) it.length() else 0L }
            assertTrue(
                "夹具必须真的把库撑到接近 ${scaleMiB}MiB，实际 $inflatedBytes B",
                inflatedBytes >= scaleMiB.toLong() * MIB / 2,
            )

            // ---- 1) 全选成包（也走裁剪器，只裁派生索引） ----------------------
            val fullArchive = newArchive("full")
            val repository = AndroidBackupRepository(context, database)
            lateinit var fullReceipt: com.tingyun.smartmistakebook.core.domain.BackupReceipt
            val fullMillis = elapsedSuspendMillis {
                val output = FileOutputStream(fullArchive)
                try {
                    fullReceipt = repository.create(output, BackupOptions(BackupSelection.FULL))
                } finally {
                    output.close()
                }
            }
            assertEquals(15, fullReceipt.selectionMask)
            assertEquals(
                "全选只裁两张派生索引表",
                2,
                fullReceipt.trimmedTableCount,
            )
            val fullManifest = JSONObject(manifestJson(fullArchive))
            assertEquals(3, fullManifest.getInt("formatVersion"))
            assertEquals(15, fullManifest.getJSONObject("selection").getInt("categoryMask"))
            assertEquals(
                2,
                fullManifest.getJSONObject("selection").getInt("trimmedTableCount"),
            )
            val fullCounts = rowCountsOf(fullManifest.getJSONObject("selection"))
            assertEquals(0L, fullCounts.getValue("knowledge_search_feature"))
            assertEquals(0L, fullCounts.getValue("knowledge_search_index_state"))
            assertTrue(
                "全选归档必须超过 100MB，实际 ${fullArchive.length()} B",
                fullArchive.length() > 100L * MIB,
            )
            Log.i(
                TAG,
                "scale=${scaleMiB}MiB fullCreate=${fullMillis}ms " +
                    "fullArchive=${fullArchive.length()}B",
            )

            // ---- 2) 子集成包（mask=9，走裁剪器） -----------------------------
            val subsetArchive = newArchive("subset9")
            val subsetMillis = elapsedSuspendMillis {
                val output = FileOutputStream(subsetArchive)
                try {
                    repository.create(
                        output,
                        BackupOptions(
                            BackupSelection.of(
                                setOf(
                                    BackupContentCategory.ISSUE_BOOK,
                                    BackupContentCategory.IMAGES,
                                ),
                            ),
                        ),
                    )
                } finally {
                    output.close()
                }
            }
            val subsetManifest = JSONObject(manifestJson(subsetArchive))
            val subsetSelection = subsetManifest.getJSONObject("selection")
            assertEquals(3, subsetManifest.getInt("formatVersion"))
            assertEquals(9, subsetSelection.getInt("categoryMask"))
            assertTrue(
                "子集必须裁掉未选类别与派生索引（至少 2 张表有删除），" +
                    "实际 ${subsetSelection.getInt("trimmedTableCount")}",
                subsetSelection.getInt("trimmedTableCount") >= 2,
            )
            val subsetCounts = rowCountsOf(subsetSelection)
            assertEquals(SmbkSelectionPlan.rowCountKeys, subsetCounts.keys)
            assertEquals(0L, subsetCounts.getValue("tutor_conversation"))
            assertEquals(0L, subsetCounts.getValue("knowledge_search_feature"))
            assertEquals(0L, subsetCounts.getValue("knowledge_search_index_state"))
            assertEquals(
                SmbkSelectionPlan.closureDigest(
                    categories = SmbkSelectionPlan.categoriesOf(9),
                    assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                    rowCounts = subsetCounts,
                ),
                subsetSelection.getString("closureDigest"),
            )
            assertTrue(
                "子集归档必须超过 100MB（载荷在 ALWAYS 的 knowledge_node，不裁剪），" +
                    "实际 ${subsetArchive.length()} B",
                subsetArchive.length() > 100L * MIB,
            )
            Log.i(
                TAG,
                "scale=${scaleMiB}MiB subsetCreate=${subsetMillis}ms " +
                    "subsetArchive=${subsetArchive.length()}B " +
                    "trimmedTables=${subsetSelection.getInt("trimmedTableCount")}",
            )

            // ---- 3) 空间预检：可用 0 字节时拒绝，且不碰被检文件 ---------------
            val liveFile = context.getDatabasePath(databaseName)
            val liveBytesBefore = liveFile.length()
            val preflight = assertThrows(BackupSnapshotTrimException::class.java) {
                BackupSnapshotTrimmer(freeSpaceProvider = { 0L })
                    .trim(liveFile, 9)
            }
            assertTrue(
                "预检拒绝必须说明磁盘空间不足：${preflight.message}",
                preflight.message.orEmpty().contains("磁盘空间不足"),
            )
            assertEquals(
                "预检失败不得改写快照文件",
                liveBytesBefore,
                liveFile.length(),
            )
            assertEquals("预检失败后原库必须仍可读", 1, countProblems(liveFile))
            Log.i(TAG, "scale=${scaleMiB}MiB preflightRejected=${preflight.message}")

            // ---- 4) 全选包恢复 -------------------------------------------------
            database.close()
            lateinit var fullRestoreReceipt: com.tingyun.smartmistakebook.core.domain.RestoreReceipt
            val fullRestoreMillis = elapsedSuspendMillis {
                fullRestoreReceipt = restore(fullArchive)
            }
            assertEquals(15, fullRestoreReceipt.selection?.mask)
            assertEquals(1, fullRestoreReceipt.problemCount)
            val reopenedAfterFull = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(1, reopenedAfterFull.countMistakes())
                assertEquals(1L, rowCount(liveFile, "problem"))
                assertEquals(1L, rowCount(liveFile, "tutor_conversation"))
                assertEquals(
                    "恢复侧恒裁派生索引",
                    0L,
                    rowCount(liveFile, "knowledge_search_index_state"),
                )
                assertTrue("恢复后必须留下待重建标记", RestoreIndexRebuildMarker.exists(context))
                assertEquals(scaleMiB.toLong(), rowCount(liveFile, "knowledge_node"))
                assertEquals("ok", integrityCheck(liveFile))
            } finally {
                reopenedAfterFull.close()
            }
            Log.i(
                TAG,
                "scale=${scaleMiB}MiB fullRestore=${fullRestoreMillis}ms",
            )

            // ---- 5) 子集包恢复 -------------------------------------------------
            RestoreIndexRebuildMarker.clear(context)
            lateinit var subsetRestoreReceipt: com.tingyun.smartmistakebook.core.domain.RestoreReceipt
            val subsetRestoreMillis = elapsedSuspendMillis {
                subsetRestoreReceipt = restore(subsetArchive)
            }
            assertEquals(9, subsetRestoreReceipt.selection?.mask)
            assertEquals(1, subsetRestoreReceipt.problemCount)
            val reopenedAfterSubset = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(1, reopenedAfterSubset.countMistakes())
                assertEquals(1L, rowCount(liveFile, "problem"))
                assertEquals("未勾选的会话必须被裁净", 0L, rowCount(liveFile, "tutor_conversation"))
                assertEquals(0L, rowCount(liveFile, "knowledge_search_feature"))
                assertEquals(0L, rowCount(liveFile, "knowledge_search_index_state"))
                assertTrue("子集恢复后必须留下待重建标记", RestoreIndexRebuildMarker.exists(context))
                assertEquals(scaleMiB.toLong(), rowCount(liveFile, "knowledge_node"))
                assertEquals("ok", integrityCheck(liveFile))
            } finally {
                reopenedAfterSubset.close()
            }
            Log.i(
                TAG,
                "scale=${scaleMiB}MiB subsetRestore=${subsetRestoreMillis}ms",
            )
        } finally {
            runCatching { database.close() }
        }
        }
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private fun newArchive(label: String): File =
        File(context.cacheDir, "selection-scale-$label-${System.nanoTime()}.smbk")
            .also { archives += it }

    private suspend fun restore(
        archive: File,
    ): com.tingyun.smartmistakebook.core.domain.RestoreReceipt {
        val database = StudyDatabaseFactory.open(context, databaseName)
        return try {
            val input = FileInputStream(archive)
            try {
                AndroidBackupRepository(context, database).restore(input)
            } finally {
                input.close()
            }
        } finally {
            runCatching { database.close() }
        }
    }

    /**
     * 用 knowledge_node（ALWAYS 表，子集不裁）里的 1MiB hex 文本撑库。
     *
     * 不能建测试专用表：裁剪器对 sqlite_master 里未登记的普通表 fail-closed
     * （BackupSnapshotTrimmer.validateSchemaIdentity），这正是它防止未知表随包泄露的
     * 设计；本用例改在登记表内制造不可压缩载荷。
     */
    private fun inflateDatabase(databaseFile: File, scaleMiB: Int) {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            var inserted = 0
            while (inserted < scaleMiB) {
                val batch = minOf(BATCH_ROWS, scaleMiB - inserted)
                db.beginTransaction()
                try {
                    repeat(batch) {
                        val index = inserted + it
                        db.execSQL(
                            """
                            INSERT INTO knowledge_node(
                                knowledge_node_id, stable_code, subject, display_name,
                                taxonomy_version, created_at_epoch_millis, boundary_markdown
                            ) VALUES (?, ?, ?, ?, ?, ?, hex(randomblob(?)))
                            """.trimIndent(),
                            arrayOf<Any>(
                                "bloat-node-$index",
                                "BLOAT.$index",
                                "MATH",
                                "大库填充 $index",
                                "scale-v1",
                                1_000L,
                                MIB / 2,
                            ),
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                inserted += batch
            }
            runCatching { db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
        }
    }

    private fun insertTutorConversation() {
        withWritableDatabase { db ->
            db.insertOrThrow(
                "tutor_conversation",
                null,
                ContentValues().apply {
                    put("conversation_id", "scale-conversation")
                    put("anchor_kind", "TEXT_ONLY")
                    putNull("anchor_id")
                    putNull("anchor_revision_id")
                    putNull("anchor_problem_revision_id")
                    putNull("anchor_practice_unit_id")
                    put("status", "ACTIVE")
                    put("title", "365MiB 会话")
                    put("created_at_epoch_millis", 1_000L)
                    put("updated_at_epoch_millis", 1_000L)
                    put("last_turn_ordinal", 1)
                },
            )
        }
    }

    private fun insertDerivedIndexRows(knowledgeNodeId: String) {
        withWritableDatabase { db ->
            db.insertOrThrow(
                "knowledge_search_index_state",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("index_version", 1)
                },
            )
            db.insertOrThrow(
                "knowledge_search_feature",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("search_feature", "函数")
                    put("knowledge_node_id", knowledgeNodeId)
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

    private fun countProblems(databaseFile: File): Int =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db -> DatabaseUtils.queryNumEntries(db, "problem").toInt() }

    private fun rowCount(databaseFile: File, table: String): Long =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db -> DatabaseUtils.queryNumEntries(db, table) }

    private fun integrityCheck(databaseFile: File): String =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        }

    private fun manifestJson(archive: File): String {
        ZipInputStream(FileInputStream(archive)).use { zip ->
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

    private fun elapsedSuspendMillis(block: suspend () -> Unit): Long = runBlocking {
        val startedAt = System.nanoTime()
        block()
        (System.nanoTime() - startedAt) / 1_000_000L
    }

    private fun seed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-selection-scale",
                canonicalFingerprint = "c".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-selection-scale",
                problemId = "problem-selection-scale",
                revisionNumber = 1,
                title = "365MiB 子集恢复题",
                problemMarkdown = "求函数最值。",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "UNKNOWN",
                sourceType = "TEST",
                sourceReference = null,
                contentFingerprint = "d".repeat(64),
                createdAtEpochMillis = 1_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-selection-scale",
                problemId = "problem-selection-scale",
                problemRevisionId = "revision-selection-scale",
                unitKey = "unit:selection-scale",
                unitKind = "PROBLEM",
                title = "365MiB 子集恢复题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-selection-scale",
                practiceUnitId = "practice-selection-scale",
                problemId = "problem-selection-scale",
                currentRevisionId = "revision-selection-scale",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
    )

    private companion object {
        const val TAG = "D1Scale"
        const val DEFAULT_SCALE_MIB = 365
        const val MIB = 1024L * 1024L
        const val BATCH_ROWS = 16
    }
}
