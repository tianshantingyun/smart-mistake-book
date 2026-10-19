package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.BackupActivity
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * KD-54 的**备份侧**接线（仪器化）：
 *
 * 1. 独立连接可用时（设备 framework SQLite ≥ 3.27/3.28），`create()` 的快照**不经过**
 *    Room 端口、**不**进 [BackupWriteGate.runBackup]，因此写入口不被排队；这是关闭
 *    "快照期间分钟级写饥饿"的那条路径。
 * 2. 独立连接不可用时，回退仍占 Room 写连接，必须保留全写者屏障——活动状态可见、
 *    写入口排队、快照结束后放行。
 *
 * 真实大库的耗时对照留给设备轨（`BackupLargeDatabaseScaleInstrumentedTest`）；这里用
 * 可阻塞的快照替身做确定性用例。
 */
@RunWith(AndroidJUnit4::class)
class BackupCreateWriteGateInstrumentedTest {
    @Test
    fun independentSnapshotDoesNotHoldTheRoomWriteConnectionOrQueueWrites() = runBlocking {
        withTimeout(60_000L) {
            assumeTrue(
                "framework SQLite 的 VACUUM INTO 需要 API 30+（研究：API 30 = 3.28.0）",
                Build.VERSION.SDK_INT >= 30,
            )
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            val database = StudyDatabaseFactory.open(context)
            // 独立快照引擎只在 WAL 下才保证读不阻塞写。新建的空 Room 库可能仍是 delete
            // 模式，先做一次真实写事务把库落到 WAL，否则测试会误走旧回退、测不到独立连接。
            database.claimUnreferencedCanonicalAssets(createdBeforeEpochMillis = 0L)
            val archive = File(context.cacheDir, "gate-independent-${System.nanoTime()}.smbk")
            val writeGate = BackupWriteGate(
                maxWriteQueueWaitMillis = 10_000L,
                drainTimeoutMillis = 10_000L,
            )
            val calls = mutableListOf<String>()
            val recordingPort = object : StudyDatabasePort by database {
                override suspend fun snapshotForBackup(
                    sourceDatabaseFile: File,
                    snapshotTarget: File,
                ) {
                    calls += "snapshotForBackup"
                    database.snapshotForBackup(sourceDatabaseFile, snapshotTarget)
                }

                override suspend fun checkpointForBackup() {
                    calls += "checkpointForBackup"
                    database.checkpointForBackup()
                }
            }
            val snapshotStarted = CompletableDeferred<Unit>()
            val releaseSnapshot = CompletableDeferred<Unit>()
            val realEngine = FrameworkVacuumIntoSnapshotEngine()
            val blockingIndependentEngine = object : DatabaseSnapshotEngine {
                override suspend fun snapshot(
                    sourceDatabaseFile: File,
                    snapshotTarget: File,
                ): Boolean {
                    snapshotStarted.complete(Unit)
                    releaseSnapshot.await()
                    return realEngine.snapshot(sourceDatabaseFile, snapshotTarget)
                }
            }
            val repository = AndroidBackupRepository(
                context = context,
                database = recordingPort,
                writeGate = writeGate,
                snapshotEngine = blockingIndependentEngine,
                observerQuiesceMillis = 0L,
            )
            val create = async {
                FileOutputStream(archive).use { output -> repository.create(output) }
            }
            try {
                snapshotStarted.await()

                // 快照正在进行：独立连接不在写门里，写入口必须立刻执行。
                var writeRan = false
                writeGate.withWriteAdmission("采集保存") { writeRan = true }
                assertTrue("独立快照期间写入口必须直接放行（不再排队）", writeRan)
                assertEquals(
                    "独立快照不占用备份活动状态（否则界面会误报“保存会排队”）",
                    BackupActivity.Idle,
                    repository.activity.value,
                )

                releaseSnapshot.complete(Unit)
                val receipt = create.await()
                assertTrue("备份回执必须有文件计数", receipt.fileCount > 0)
                assertEquals(
                    "独立快照路径不能调用 Room 的 checkpoint/快照",
                    emptyList<String>(),
                    calls,
                )
            } finally {
                releaseSnapshot.complete(Unit)
                runCatching { create.await() }
                database.close()
                archive.delete()
                context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            }
        }
    }

    @Test
    fun legacyFallbackKeepsTheFullWriterBarrier() = runBlocking {
        withTimeout(30_000L) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            val database = StudyDatabaseFactory.open(context)
            val archive = File(context.cacheDir, "gate-create-${System.nanoTime()}.smbk")
            val snapshotStarted = CompletableDeferred<Unit>()
            val releaseSnapshot = CompletableDeferred<Unit>()
            val writeGate = BackupWriteGate(
                maxWriteQueueWaitMillis = 10_000L,
                drainTimeoutMillis = 10_000L,
            )
            val blockingDatabase = object : StudyDatabasePort by database {
                override suspend fun snapshotForBackup(
                    sourceDatabaseFile: File,
                    snapshotTarget: File,
                ) {
                    snapshotStarted.complete(Unit)
                    releaseSnapshot.await()
                    database.snapshotForBackup(sourceDatabaseFile, snapshotTarget)
                }
            }
            val repository = AndroidBackupRepository(
                context = context,
                database = blockingDatabase,
                writeGate = writeGate,
                // 强制走旧回退：独立连接策略在本用例中"不可用"。
                snapshotEngine = object : DatabaseSnapshotEngine {
                    override suspend fun snapshot(
                        sourceDatabaseFile: File,
                        snapshotTarget: File,
                    ): Boolean = false
                },
                observerQuiesceMillis = 0L,
            )
            val create = async {
                FileOutputStream(archive).use { output -> repository.create(output) }
            }
            try {
                snapshotStarted.await()
                assertTrue(
                    "旧回退快照期间备份活动必须显式可见",
                    repository.activity.value is BackupActivity.Snapshotting,
                )

                var writeRan = false
                val writeStarted = CompletableDeferred<Unit>()
                val write = async {
                    writeStarted.complete(Unit)
                    writeGate.withWriteAdmission("采集保存") { writeRan = true }
                }
                writeStarted.await()
                delay(150L)
                assertFalse("旧回退快照期间写入口必须排队，而不是先执行", writeRan)
                assertFalse("旧回退快照期间写入口必须仍未完成", write.isCompleted)

                releaseSnapshot.complete(Unit)
                val receipt = create.await()
                write.await()
                assertTrue("快照结束后排队的写必须放行", writeRan)
                assertTrue("备份回执必须有文件计数", receipt.fileCount > 0)
                assertTrue(
                    "备份结束后活动状态必须回到 Idle",
                    repository.activity.value is BackupActivity.Idle,
                )
            } finally {
                releaseSnapshot.complete(Unit)
                runCatching { create.await() }
                database.close()
                archive.delete()
                context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            }
        }
    }
}
