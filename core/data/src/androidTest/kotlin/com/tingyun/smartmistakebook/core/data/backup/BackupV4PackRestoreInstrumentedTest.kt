package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.RestoreReceipt
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
import java.security.MessageDigest

/**
 * KD-83 / WS5 item 45–46：**冻结字节**的 closure 4（schema 72）备份包在设备上的恢复。
 *
 * 与 `BackupSelectionRestoreInstrumentedTest.closureFourArchiveRestores…` 的区别（后者保留为
 * 迁移形状的既有覆盖）：
 * 1. 载荷来自 `tools/backup_fixtures/v4/`（`72.json` 物化的真库 + Python 独立摘要），
 *    不是"用 `ClosureRuleTables` 现算、当场重打包"的合成件——规则表漂了，这里会红；
 * 2. 恢复前先对 APK 里的字节做 sha256 断言：证明设备读到的就是冻结的那份字节，
 *    而不是"某次构建顺手打进来的别的包"；
 * 3. 负向样本（自洽伪造行数 / 被删表非空 / 载荷翻位）各一条，fail-closed 且在交换前拒绝。
 *
 * 本测试证明：真 v4 包 → 恢复 → 72→75 迁移 → DAO 可读、旧行不丢、错因两表为空、
 * 被删表 0 行。它**不**证明"生产 `create()` 能写出 v4 形态包"——写侧只写 v5。
 */
@RunWith(AndroidJUnit4::class)
class BackupV4PackRestoreInstrumentedTest {

    private lateinit var context: Context
    private val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @Test
    fun frozenV4PackRestoresToSchema73WithEmptyNewTablesAndNoRowLoss() = runBlocking {
        val bytes = readFixture(GOLDEN_NAME)
        assertEquals(
            "设备读到的字节不是冻结的那份（APK 里的副本过期或被换过）",
            goldenValue("packSha256"),
            sha256Hex(bytes),
        )

        // 先建一份真实当前 schema（75）活库（Room 懒开库：读一次才落文件），恢复才有
        // "迁移后版本 == 实时版本"的对照。
        createLiveSchema73Database()

        val receipt = restore(bytes)
        assertEquals("v4 包必须按 mask=15 回执", 15, receipt.selection?.mask)
        assertEquals("v4 回执必须报告其事实上的 schema 版本", 72, receipt.databaseSchemaVersion)

        // 迁移发生在下一次开库（Room 的 onUpgrade）；开一次让 72→75 真跑完。
        // 能开成功本身就是 Room 迁移后的 TableInfo 校验通过（不是 SQL 级"能读"）。
        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            assertEquals(
                "Room 连接上读到的版本必须是当前 schema（DAO 侧可读）",
                SmbkSelectionPlan.SCHEMA_VERSION,
                reopened.readDatabaseVersion(),
            )
            assertEquals(
                "DAO 可读；v4 最小载荷没有 error_book_entry，活动错题数按定义为 0",
                0,
                reopened.countMistakes(),
            )
        } finally {
            reopened.close()
        }

        val restoredFile = context.getDatabasePath(databaseName)
        assertEquals(
            "迁移后 user_version 必须是当前 schema",
            SmbkSelectionPlan.SCHEMA_VERSION.toLong(),
            userVersion(restoredFile),
        )
        assertEquals("problem 旧行不得丢", 1L, rowCount(restoredFile, "problem"))
        assertEquals("attempt_submission 两行不得丢", 2L, rowCount(restoredFile, "attempt_submission"))
        assertEquals("knowledge_node 旧行不得丢", 1L, rowCount(restoredFile, "knowledge_node"))
        assertEquals(
            "content_install_state 旧行不得丢",
            1L,
            rowCount(restoredFile, "content_install_state"),
        )
        assertEquals(
            "新增的错因两表必须由迁移建空、不得回填",
            0L,
            rowCount(restoredFile, "error_cause_observation"),
        )
        assertEquals(0L, rowCount(restoredFile, "error_cause_binding"))
        assertEquals(
            "被删表 knowledge_search_material_feature 恢复后必须 0 行（表可能残留，行数必须为 0）",
            0L,
            rowCountOrZero(restoredFile, "knowledge_search_material_feature"),
        )
        assertEquals(
            "恢复侧恒裁派生索引",
            0L,
            rowCount(restoredFile, "knowledge_search_feature"),
        )
    }

    @Test
    fun selfConsistentForgedRowCountIsRejectedByMeasuredCountsAndLiveDataStays() = runBlocking {
        val bytes = readFixture(FORGED_NAME)
        assertEquals(
            "负向样本字节必须与 golden 一致",
            goldenNegative(FORGED_NAME, "packSha256"),
            sha256Hex(bytes),
        )

        // 活库放一行哨兵：失败后必须原样还在。
        createLiveSchema73Database()
        val liveFile = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(
            liveFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.execSQL(
                "INSERT INTO problem(problem_id, canonical_fingerprint, subject, " +
                    "created_at_epoch_millis) VALUES('sentinel-1','sentinel-fp-1','MATH',1000)",
            )
        }

        val failure = assertThrows(BackupRestoreException::class.java) {
            runBlocking { restore(bytes) }
        }
        assertTrue(
            "自洽伪造包只能由恢复侧的逐表 COUNT 对拍抓：${failure.message}",
            failure.message.orEmpty().contains("逐表行数与声明不符"),
        )
        assertEquals("失败后原库必须原样可读", 1L, rowCount(liveFile, "problem"))
        assertTrue(
            "失败后不得留下半行哨兵之外的数据",
            SQLiteDatabase.openDatabase(liveFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.rawQuery("SELECT COUNT(*) FROM problem", null).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) == 1L } },
        )
    }

    @Test
    fun droppedTableWithRowsAndPayloadFlipAreRejectedBeforeSwap() = runBlocking {
        val dropped = readFixture(DROPPED_NONEMPTY_NAME)
        assertEquals(
            "负向样本字节必须与 golden 一致",
            goldenNegative(DROPPED_NONEMPTY_NAME, "packSha256"),
            sha256Hex(dropped),
        )
        StudyDatabaseFactory.open(context, databaseName).let { database ->
            database.countMistakes()
            database.close()
        }
        val liveFile = context.getDatabasePath(databaseName)

        val droppedFailure = assertThrows(BackupRestoreException::class.java) {
            runBlocking { restore(dropped) }
        }
        assertTrue(
            "被删表非空必须在 codec 前门整包拒绝：${droppedFailure.message}",
            droppedFailure.message.orEmpty().contains("已废弃表"),
        )
        assertEquals("拒绝后原库必须仍在", 0L, rowCount(liveFile, "problem"))

        // 载荷翻一位：结构门（CRC/sha256/checksums）任一都必须拒绝，且不落半成品。
        val flipped = readFixture(GOLDEN_NAME).copyOf().also { bytes ->
            bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0xFF).toByte()
        }
        val validationFailure = assertThrows(BackupRestoreException::class.java) {
            runBlocking { restore(flipped) }
        }
        assertTrue(
            "篡改包必须在交换前拒绝：${validationFailure.message}",
            validationFailure.message.orEmpty().isNotBlank(),
        )
    }

    // ------------------------------------------------------------------

    private suspend fun restore(archive: ByteArray): RestoreReceipt {
        // 恢复会关闭注入的连接池；夹具里直接开一个仓库专用实例，模拟"重启后进程"。
        val database = StudyDatabaseFactory.open(context, databaseName)
        return try {
            AndroidBackupRepository(context, database)
                .restore(ByteArrayInputStream(archive))
        } finally {
            runCatching { database.close() }
        }
    }

    /** Room 懒开库：必须读一次才会把当前 schema 的库文件落到磁盘。 */
    private suspend fun createLiveSchema73Database() {
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            database.countMistakes()
        } finally {
            database.close()
        }
    }

    private fun readFixture(name: String): ByteArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val candidates = listOf(instrumentation.context, context)
        candidates.forEach { candidate ->
            runCatching { candidate.assets.open("$ASSET_DIR/$name").use { it.readBytes() } }
                .onSuccess { return it }
        }
        error("APK 里找不到夹具 $ASSET_DIR/$name（先跑 ./gradlew :core:data:syncV4PackFixtures）")
    }

    private fun golden(): JSONObject =
        JSONObject(readFixture(GOLDEN_JSON).toString(Charsets.UTF_8))

    private fun goldenValue(key: String): String = golden().getString(key)

    private fun goldenNegative(name: String, key: String): String =
        golden().getJSONObject("negative").getJSONObject(name).getString(key)

    private fun userVersion(databaseFile: File): Long =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }

    private fun rowCount(databaseFile: File, table: String): Long =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database -> DatabaseUtils.queryNumEntries(database, table) }

    /**
     * 表不存在按 0 行（与恢复侧/`countTables` 的语义一致）：`knowledge_search_material_feature`
     * 在 v5 规则外，未来迁移若真的把它 DROP 掉，"0 行"仍然成立，测试不该因此崩。
     */
    private fun rowCountOrZero(databaseFile: File, table: String): Long =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val exists = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table),
            ).use { cursor -> cursor.moveToFirst() }
            if (exists) DatabaseUtils.queryNumEntries(database, table) else 0L
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val ASSET_DIR = "backup/v4"
        const val GOLDEN_NAME = "closure4-schema72-mask15.smbk"
        const val FORGED_NAME = "forged-count.smbk"
        const val DROPPED_NONEMPTY_NAME = "dropped-nonempty.smbk"
        const val GOLDEN_JSON = "v4-pack.golden.json"
    }
}
