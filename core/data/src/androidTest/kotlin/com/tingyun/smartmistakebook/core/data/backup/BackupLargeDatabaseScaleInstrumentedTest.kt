package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * KD-53 恢复侧 + KD-54 关闭条件（设备大库对照）。
 *
 * 默认按 KD-56/KD-53 验收的 **365 MiB** 库跑；可用 instrumentation 参数
 * `smbkBackupScaleMiB` 调整（设备存储不足时先如实失败，不缩小断言）：
 *
 * ```
 * adb shell am instrument -w \
 *   -e class com.tingyun.smartmistakebook.core.data.backup.BackupLargeDatabaseScaleInstrumentedTest \
 *   -e smbkBackupScaleMiB 365 \
 *   com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * 用例钉住：
 * 1. 大库成包（`database.sqlite` 单条目远超旧 100MB 门）能自校验；
 * 2. 快照期间经写门发起的数据库访问**没有分钟级饥饿**（每次 < 5s，0 次失败）；
 * 3. 大库恢复不崩、返回后新 Room 实例能读出题数与完整的大表行数；
 * 4. 恢复关库后写门终态生效。
 *
 * 大表 `test_backup_bloat` 用 `randomblob` 填充：故意不用全零，否则 deflate 压缩比
 * 会超过归档校验的 100:1 防 zip-bomb 门，测试会先被校验拒绝而不是验证恢复。
 */
@RunWith(AndroidJUnit4::class)
class BackupLargeDatabaseScaleInstrumentedTest {

    @Test
    fun largeDatabaseBackupAndRestoreRoundTripWithoutWriteStarvation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scaleMiB = InstrumentationRegistry.getArguments()
            .getString("smbkBackupScaleMiB")
            ?.toIntOrNull()
            ?: DEFAULT_SCALE_MIB
        require(scaleMiB >= 1) { "smbkBackupScaleMiB must be >= 1, actual=$scaleMiB" }

        val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        val databaseFile = context.getDatabasePath(databaseName)
        val archive = File(context.cacheDir, "scale-backup-${System.nanoTime()}.smbk")
        context.deleteDatabase(databaseName)
        val database = StudyDatabaseFactory.open(context, databaseName)
        val gate = BackupWriteGate(
            maxWriteQueueWaitMillis = 10_000L,
            drainTimeoutMillis = 10_000L,
        )
        val restartEvents = Collections.synchronizedList(mutableListOf<String>())
        val repository = AndroidBackupRepository(
            context = context,
            database = database,
            writeGate = gate,
            restartNotifier = { message -> restartEvents += message },
            observerQuiesceMillis = 0L,
        )
        try {
            seedStudyFacts(context, database, databaseName, seed())
            inflateDatabase(databaseFile, scaleMiB)
            val inflatedBytes = databaseFile.length() +
                File("${databaseFile.absolutePath}-wal").let { wal ->
                    if (wal.isFile) wal.length() else 0L
                }
            assertTrue(
                "夹具必须真的把主库撑到接近 ${scaleMiB}MiB，实际 $inflatedBytes B",
                inflatedBytes >= scaleMiB.toLong() * MIB / 2,
            )

            // KD-54：成包期间持续从写门入口做真实数据库访问，记录最坏等待。
            val stopWriters = AtomicBoolean(false)
            val worstWriteNanos = AtomicLong(0L)
            val writeFailures = Collections.synchronizedList(mutableListOf<Throwable>())
            var writeCount = 0
            val writer = async(Dispatchers.IO) {
                while (!stopWriters.get()) {
                    val startedAt = System.nanoTime()
                    try {
                        gate.withWriteAdmission("设备大库写入口") {
                            // 真实写事务（不是只读查询）：验证快照期间 Room 的写路径
                            // 仍能拿到连接；空资产表上这是一次廉价写。
                            database.claimUnreferencedCanonicalAssets(
                                createdBeforeEpochMillis = 0L,
                            )
                            writeCount += 1
                        }
                    } catch (failure: Throwable) {
                        writeFailures += failure
                    }
                    val elapsed = System.nanoTime() - startedAt
                    synchronized(worstWriteNanos) {
                        if (elapsed > worstWriteNanos.get()) {
                            worstWriteNanos.set(elapsed)
                        }
                    }
                    delay(50L)
                }
            }

            val createStartedAt = System.nanoTime()
            val receipt = FileOutputStream(archive).use { output ->
                repository.create(output)
            }
            stopWriters.set(true)
            writer.await()
            val createMillis = (System.nanoTime() - createStartedAt) / 1_000_000L

            assertTrue("夹具必须真的产生一次备份", receipt.fileCount > 0)
            assertEquals(1, receipt.problemCount)
            assertTrue(
                "归档内数据库条目必须超过旧 100MB 单条目上限，实际载荷 ${receipt.totalBytes} B",
                receipt.totalBytes > 100L * MIB,
            )
            assertTrue(
                "归档文件必须确实存在且非空，实际 ${archive.length()} B",
                archive.length() > 0L,
            )
            val validation = FileInputStream(archive).use { repository.validate(it) }
            assertTrue(
                "大库自建备份必须自己验得过：$validation",
                validation is BackupValidation.Valid,
            )

            assertTrue(
                "快照期间写门入口不得失败（${writeFailures.size} 次：$writeFailures）",
                writeFailures.isEmpty(),
            )
            assertTrue("并发写者必须确实跑过若干次，实际 $writeCount", writeCount > 0)
            val worstWriteMillis = worstWriteNanos.get() / 1_000_000L
            assertTrue(
                "快照期间写入口最坏等待 $worstWriteMillis ms（成包 ${createMillis}ms）：" +
                    "KD-54 要求无分钟级饥饿",
                worstWriteMillis < MAX_WRITE_WAIT_MILLIS,
            )

            val restoreReceipt = FileInputStream(archive).use { input ->
                repository.restore(input)
            }
            assertEquals(1, restoreReceipt.problemCount)
            assertTrue(
                "恢复必须进入重启态（连接池已关闭）：$restartEvents",
                restartEvents.any { it.contains("恢复完成") },
            )
            assertTrue(
                "关库后写门必须进入终态",
                gate.databaseClosedForRestart.value,
            )

            // 模拟重启后的打开路径：新 Room 实例 + 直接读大表。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(1, reopened.countMistakes())
            } finally {
                reopened.close()
            }
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM test_backup_bloat", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(
                        "恢复后大表行数必须与备份时一致",
                        scaleMiB,
                        cursor.getInt(0),
                    )
                }
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                }
            }
        } finally {
            runCatching { database.close() }
            archive.delete()
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 在 Room 已打开的库上追加一张测试大表并写入随机字节。
     *
     * 用独立连接（不是 Room 的）——这正是生产快照要处理的并发形态；表是纯测试夹具，
     * 不参与任何 DAO，也不影响 Room 的 identity hash 校验。
     */
    private fun inflateDatabase(databaseFile: File, scaleMiB: Int) {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS test_backup_bloat(payload BLOB)",
            )
            db.execSQL("DELETE FROM test_backup_bloat")
            var inserted = 0
            while (inserted < scaleMiB) {
                val batch = minOf(BATCH_ROWS, scaleMiB - inserted)
                db.beginTransaction()
                try {
                    repeat(batch) {
                        db.execSQL(
                            "INSERT INTO test_backup_bloat(payload) VALUES (randomblob(?))",
                            arrayOf<Any>(MIB),
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                inserted += batch
            }
            // best effort: 让主库文件本身也接近目标体积；WAL 里未回填的部分仍会被
            // VACUUM INTO 读到，所以 checkpoint busy 不影响快照一致性。
            runCatching { db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
        }
    }

    private fun seed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-scale",
                canonicalFingerprint = "e".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-scale",
                problemId = "problem-scale",
                revisionNumber = 1,
                title = "大库恢复题",
                problemMarkdown = "求函数最值。",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "UNKNOWN",
                sourceType = "TEST",
                sourceReference = null,
                contentFingerprint = "f".repeat(64),
                createdAtEpochMillis = 1_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-scale",
                problemId = "problem-scale",
                problemRevisionId = "revision-scale",
                unitKey = "unit:scale",
                unitKind = "PROBLEM",
                title = "大库恢复题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-scale",
                practiceUnitId = "practice-scale",
                problemId = "problem-scale",
                currentRevisionId = "revision-scale",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
    )

    private companion object {
        const val DEFAULT_SCALE_MIB = 365
        const val MIB = 1024L * 1024L
        const val BATCH_ROWS = 16
        const val MAX_WRITE_WAIT_MILLIS = 5_000L
    }
}
