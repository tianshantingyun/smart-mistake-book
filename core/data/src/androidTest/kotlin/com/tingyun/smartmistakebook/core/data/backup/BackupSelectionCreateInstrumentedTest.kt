package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
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
import java.util.zip.ZipInputStream

/**
 * 6C-2 成包接线（T4）的设备侧验收。
 *
 * 三条用例分别钉住：
 * 1. 全选（mask=15）也走裁剪器：用户类别一行不动，派生索引两张表**恒裁**（产品裁定 ③），
 *    manifest 的 selection/rowCounts 如实反映裁剪后的快照；
 * 2. 子集（mask=9）走 2b–2d：裁剪后 manifest 的 rowCounts/trimmedTableCount/digest 与裁剪
 *    报告同源，被裁表为 0 行；
 * 3. 非法选择（0/8/未闭合）在**读设置、做快照之前**拒绝：不产出任何归档字节，也不留下快照。
 *
 * 库用生产 Room 工厂创建（带 schema 69 的 room_master_table 身份哈希），种子行用独立
 * SQLite 连接写入；不触碰生产数据，也不依赖裁剪器测试的夹具。
 */
@RunWith(AndroidJUnit4::class)
class BackupSelectionCreateInstrumentedTest {

    private lateinit var context: Context
    private val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun fullSelectionAlsoTrimsDerivedIndexRowsButKeepsUserCategories() = runBlocking {
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            // Room 的建库是惰性的：先经 Room 打开一次连接，文件才会落盘，
            // 之后测试才能用独立 SQLite 连接写入种子行。
            database.readDatabaseVersion()
            insertDerivedIndexState(subject = "MATH")
            // 加一行会话：全选下它属于用户数据，裁剪器一行都不许动。
            insertTutorConversation()

            val archive = ByteArrayOutputStream()
            val repository = AndroidBackupRepository(context, database)
            val receipt = repository.create(
                archive,
                BackupOptions(BackupSelection.FULL),
            )

            assertEquals(15, receipt.selectionMask)
            assertEquals(
                "全选只裁派生索引：夹具里只有 knowledge_search_index_state 有行",
                1,
                receipt.trimmedTableCount,
            )

            val manifest = JSONObject(manifestJson(archive.toByteArray()))
            assertEquals(3, manifest.getInt("formatVersion"))
            val selection = manifest.getJSONObject("selection")
            assertEquals(2, selection.getInt("schemaVersion"))
            assertEquals(15, selection.getInt("categoryMask"))
            assertEquals(
                listOf("ISSUE_BOOK", "MASTERY", "SESSIONS", "IMAGES"),
                jsonStrings(selection.getJSONArray("categories")),
            )
            assertEquals("INCLUDED", selection.getString("assetPolicy"))
            assertEquals(SmbkSelectionPlan.CLOSURE_VERSION, selection.getInt("closureVersion"))
            assertEquals(1, selection.getInt("trimmedTableCount"))

            val rowCounts = rowCountsOf(selection)
            assertEquals(SmbkSelectionPlan.rowCountKeys, rowCounts.keys)
            assertEquals(
                "派生索引恒裁：全选也不随包",
                0L,
                rowCounts.getValue("knowledge_search_index_state"),
            )
            assertEquals(0L, rowCounts.getValue("knowledge_search_feature"))
            assertEquals(
                "全选下用户类别一行不动",
                1L,
                rowCounts.getValue("tutor_conversation"),
            )
            assertEquals(
                SmbkSelectionPlan.closureDigest(
                    categories = SmbkSelectionPlan.categoriesOf(15),
                    assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                    rowCounts = rowCounts,
                ),
                selection.getString("closureDigest"),
            )

            val validation = repository.validate(ByteArrayInputStream(archive.toByteArray()))
            assertTrue("自建 v3 包必须自己验得过：$validation", validation is BackupValidation.Valid)
            val summary = (validation as BackupValidation.Valid).manifest.selection
                ?: error("v3 包必须读出 selection")
            assertEquals(15, summary.mask)
            assertEquals(BackupContentCategory.ALL, summary.categories)
        } finally {
            database.close()
        }
    }

    @Test
    fun subsetSelectionWritesTrimReportCountsIntoTheManifest() = runBlocking {
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            // 同 fullSelection 用例：先触发 Room 建库，再开独立连接写夹具。
            database.readDatabaseVersion()
            insertDerivedIndexState(subject = "MATH")
            insertTutorConversation()

            val archive = ByteArrayOutputStream()
            val repository = AndroidBackupRepository(context, database)
            val selection = BackupSelection.of(
                setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.IMAGES),
            )
            val receipt = repository.create(archive, BackupOptions(selection))

            assertEquals(9, receipt.selectionMask)
            assertTrue(
                "会话与派生索引都被裁掉，至少 2 张表有删除，实际 ${receipt.trimmedTableCount}",
                receipt.trimmedTableCount >= 2,
            )

            val manifest = JSONObject(manifestJson(archive.toByteArray()))
            assertEquals(3, manifest.getInt("formatVersion"))
            val block = manifest.getJSONObject("selection")
            assertEquals(2, block.getInt("schemaVersion"))
            assertEquals(9, block.getInt("categoryMask"))
            assertEquals(
                listOf("ISSUE_BOOK", "IMAGES"),
                jsonStrings(block.getJSONArray("categories")),
            )
            assertEquals("INCLUDED", block.getString("assetPolicy"))
            assertEquals(SmbkSelectionPlan.CLOSURE_VERSION, block.getInt("closureVersion"))
            assertEquals(receipt.trimmedTableCount, block.getInt("trimmedTableCount"))

            val rowCounts = rowCountsOf(block)
            assertEquals(SmbkSelectionPlan.rowCountKeys, rowCounts.keys)
            assertEquals("未选类别必须裁净", 0L, rowCounts.getValue("tutor_conversation"))
            assertEquals("派生索引恒裁", 0L, rowCounts.getValue("knowledge_search_index_state"))
            assertEquals("派生索引恒裁", 0L, rowCounts.getValue("knowledge_search_feature"))
            assertEquals(
                SmbkSelectionPlan.closureDigest(
                    categories = SmbkSelectionPlan.categoriesOf(9),
                    assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                    rowCounts = rowCounts,
                ),
                block.getString("closureDigest"),
            )

            val validation = repository.validate(ByteArrayInputStream(archive.toByteArray()))
            assertTrue("子集包必须自己验得过：$validation", validation is BackupValidation.Valid)
            val summary = (validation as BackupValidation.Valid).manifest.selection
                ?: error("v3 包必须读出 selection")
            assertEquals(
                setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.IMAGES),
                summary.categories,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun nonCreatableSelectionsAreRejectedBeforeAnySnapshotOrArchiveByte() = runBlocking {
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            val snapshotDir = File(context.cacheDir, "backup-snapshots")
            val snapshotsBefore = snapshotDir.listFiles().orEmpty().map { it.name }.toSet()
            val repository = AndroidBackupRepository(context, database)
            val invalidSelections = listOf(
                BackupSelection.of(emptySet()),
                BackupSelection.of(setOf(BackupContentCategory.IMAGES)),
                BackupSelection.of(setOf(BackupContentCategory.ISSUE_BOOK)),
                BackupSelection.of(
                    setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.MASTERY),
                ),
            )

            invalidSelections.forEach { selection ->
                val archive = ByteArrayOutputStream()
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repository.create(archive, BackupOptions(selection)) }
                }
                assertTrue(
                    "拒因必须说明选择非法（mask=${selection.mask}）：${failure.message}",
                    failure.message.orEmpty().contains("备份内容"),
                )
                assertEquals(
                    "非法选择（mask=${selection.mask}）不得写出任何归档字节",
                    0,
                    archive.size(),
                )
            }

            assertEquals(
                "非法选择必须在快照之前拒绝，不得留下快照文件",
                snapshotsBefore,
                snapshotDir.listFiles().orEmpty().map { it.name }.toSet(),
            )
        } finally {
            database.close()
        }
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 派生索引行：全选时必须是「原样保留」，子集时必须是「恒裁」。 */
    private fun insertDerivedIndexState(subject: String) {
        withWritableDatabase { db ->
            db.insertOrThrow(
                "knowledge_search_index_state",
                null,
                ContentValues().apply {
                    put("subject", subject)
                    put("index_version", 1)
                },
            )
        }
    }

    private fun insertTutorConversation() {
        withWritableDatabase { db ->
            db.insertOrThrow(
                "tutor_conversation",
                null,
                ContentValues().apply {
                    put("conversation_id", "selection-conversation-1")
                    put("anchor_kind", "TEXT_ONLY")
                    putNull("anchor_id")
                    putNull("anchor_revision_id")
                    putNull("anchor_problem_revision_id")
                    putNull("anchor_practice_unit_id")
                    put("status", "ACTIVE")
                    put("title", "selection-subset")
                    put("created_at_epoch_millis", 1_000L)
                    put("updated_at_epoch_millis", 1_000L)
                    put("last_turn_ordinal", 1)
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

    private fun jsonStrings(array: org.json.JSONArray): List<String> =
        (0 until array.length()).map { index -> array.getString(index) }

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
}
