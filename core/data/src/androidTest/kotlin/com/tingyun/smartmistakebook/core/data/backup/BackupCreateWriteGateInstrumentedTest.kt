package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * KD-54 的**备份侧**接线（仪器化）：`AndroidBackupRepository.create` 的快照段确实跑在
 * [BackupWriteGate.runBackup] 里——活动状态在快照期间可见，写入口在这段时间排队，
 * 快照结束（归档生成仍在继续）后写入口放行。
 *
 * 快照本身用"委托真实库、在 `snapshotForBackup` 处停住"的测试替身模拟：真实
 * `VACUUM INTO` 的时长取决于库体积，不能用来做确定性用例；设备对照留给设备轨。
 */
@RunWith(AndroidJUnit4::class)
class BackupCreateWriteGateInstrumentedTest {
    @Test
    fun createRunsTheSnapshotBehindTheWriteGateAndQueuesWrites() = runBlocking {
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
            )
            try {
                val create = async {
                    FileOutputStream(archive).use { output -> repository.create(output) }
                }
                snapshotStarted.await()
                assertTrue(
                    "快照期间备份活动必须显式可见",
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
                assertFalse("快照期间写入口必须排队，而不是先执行", writeRan)
                assertFalse("快照期间写入口必须仍未完成", write.isCompleted)

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
                // 任何断言失败都要放行快照替身，避免协程悬住。
                releaseSnapshot.complete(Unit)
                database.close()
                archive.delete()
                context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            }
        }
    }
}
