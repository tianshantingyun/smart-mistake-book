package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupWriteDeferredException
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-56 的恢复生命周期，JVM 面。
 *
 * 钉住的四件事：
 * 1. 恢复**准备**（校验/解包）不阻塞普通写——活库还没被交换；
 * 2. 交换段（`beginCommit`）排在途写、挡新写；失败/结束时全部放行；
 * 3. 恢复进行中拒绝新备份；备份进行中拒绝新恢复；
 * 4. `markDatabaseClosedForRestart()` 是**进程内终态**：之后任何写/备份/恢复入口
 *    都必须立即抛可读异常，而不是继续碰已关闭的 room3 连接池。
 */
class BackupWriteGateRestoreLifecycleTest {

    @Test
    fun `restore preparation does not block ordinary writes`() = gateTest {
        val gate = BackupWriteGate()
        val admission = gate.beginRestore("恢复备份")
        var writeRan = false

        gate.withWriteAdmission("采集保存") { writeRan = true }

        assertTrue("校验/解包阶段不碰活库，写必须照常执行", writeRan)
        admission.endRestore()
    }

    @Test
    fun `restore commit queues writes and releases them when the restore ends`() = gateTest {
        val gate = BackupWriteGate(
            maxWriteQueueWaitMillis = 5_000L,
            drainTimeoutMillis = 5_000L,
        )
        val admission = gate.beginRestore("恢复备份")
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val restore = launch {
            admission.beginCommit()
            commitStarted.complete(Unit)
            releaseCommit.await()
            admission.endRestore()
        }
        commitStarted.await()

        var writeRan = false
        val write = async {
            gate.withWriteAdmission("采集保存") { writeRan = true }
        }
        delay(150L)
        assertFalse("交换段的新写必须排队，不能先执行", writeRan)
        assertFalse("交换段的写必须仍未完成", write.isCompleted)

        releaseCommit.complete(Unit)
        restore.join()
        write.await()
        assertTrue("恢复结束后排队的写必须放行", writeRan)
    }

    @Test
    fun `restore commit waits for in-flight writers`() = gateTest {
        val gate = BackupWriteGate(drainTimeoutMillis = 5_000L)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val admission = gate.beginRestore("恢复备份")

        val write = launch {
            gate.withWriteAdmission("采集保存") {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
        }
        writeStarted.await()

        var commitStarted = false
        val commit = async {
            admission.beginCommit()
            commitStarted = true
        }
        delay(150L)
        assertFalse("在途写没排空前不许进交换段", commitStarted)

        releaseWrite.complete(Unit)
        write.join()
        commit.await()
        admission.endRestore()
        assertTrue(commitStarted)
    }

    @Test
    fun `restore commit fails with a readable reason when the drain times out`() = gateTest {
        val gate = BackupWriteGate(drainTimeoutMillis = 100L)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val admission = gate.beginRestore("恢复备份")

        val write = launch {
            gate.withWriteAdmission("采集保存") {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
        }
        writeStarted.await()

        val failure = runCatching { admission.beginCommit() }.exceptionOrNull()
        assertTrue(
            "排空超时必须明确失败：$failure",
            failure is BackupWriteDeferredException,
        )
        assertTrue(
            "失败消息必须说清仍有写入：${failure?.message}",
            failure?.message.orEmpty().contains("写入"),
        )

        releaseWrite.complete(Unit)
        write.join()
        admission.endRestore()

        // 失败没有烧坏门：排空后仍能正常进交换段并放行写。
        val retry = gate.beginRestore("恢复重试")
        retry.beginCommit()
        retry.endRestore()
        gate.withWriteAdmission("备份完成后写入") { }
    }

    @Test
    fun `backup is rejected while a restore is registered`() = gateTest {
        val gate = BackupWriteGate()
        val admission = gate.beginRestore("恢复备份")
        val failure = runCatching { gate.runBackup { } }.exceptionOrNull()

        assertTrue(
            "恢复期间不能开始新备份：$failure",
            failure is BackupWriteDeferredException,
        )
        assertTrue(failure?.message.orEmpty().contains("恢复"))

        admission.endRestore()

        // 恢复结束后备份恢复可用。
        gate.runBackup { }
    }

    @Test
    fun `restore is rejected while a backup snapshot is running`() = gateTest {
        val gate = BackupWriteGate()
        val snapshotStarted = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        val backup = launch {
            gate.runBackup {
                snapshotStarted.complete(Unit)
                releaseSnapshot.await()
            }
        }
        snapshotStarted.await()

        val failure = runCatching { gate.beginRestore("恢复备份") }.exceptionOrNull()
        assertTrue(
            "备份期间恢复必须明确拒绝：$failure",
            failure is BackupWriteDeferredException,
        )
        assertTrue(
            "提示必须指向备份正在进行：${failure?.message}",
            failure?.message.orEmpty().contains("备份"),
        )

        releaseSnapshot.complete(Unit)
        backup.join()
        gate.beginRestore("恢复备份").endRestore()
    }

    @Test
    fun `closing the database for restart rejects every admission immediately`() = gateTest {
        val gate = BackupWriteGate(maxWriteQueueWaitMillis = 5_000L)
        val admission = gate.beginRestore("恢复备份")
        admission.beginCommit()
        gate.markDatabaseClosedForRestart()
        admission.endRestore()

        assertTrue(
            "关库终态必须显式可观察（界面据此停内容/提示重启）",
            gate.databaseClosedForRestart.value,
        )

        val writeFailure = runCatching {
            gate.withWriteAdmission("采集保存") { error("must not run") }
        }.exceptionOrNull()
        assertTrue(
            "关库后写必须立即失败：$writeFailure",
            writeFailure is BackupWriteDeferredException,
        )
        assertTrue(
            "写失败消息必须让用户重启：${writeFailure?.message}",
            writeFailure?.message.orEmpty().contains("重新打开应用"),
        )

        val backupFailure = runCatching { gate.runBackup { error("must not run") } }
            .exceptionOrNull()
        assertTrue(
            "关库后备份必须失败：$backupFailure",
            backupFailure is BackupWriteDeferredException,
        )
        val restoreFailure = runCatching { gate.beginRestore("恢复备份") }.exceptionOrNull()
        assertTrue(
            "关库后恢复必须失败：$restoreFailure",
            restoreFailure is BackupWriteDeferredException,
        )
    }

    @Test
    fun `writes queued behind the commit fail after the restart close instead of running`() =
        gateTest {
            val gate = BackupWriteGate(
                maxWriteQueueWaitMillis = 5_000L,
                drainTimeoutMillis = 5_000L,
            )
            val admission = gate.beginRestore("恢复备份")
            admission.beginCommit()

            var writeRan = false
            val write = async {
                runCatching {
                    gate.withWriteAdmission("采集保存") { writeRan = true }
                }
            }
            delay(150L)
            assertFalse(writeRan)

            gate.markDatabaseClosedForRestart()
            admission.endRestore()

            val failure = write.await().exceptionOrNull()
            assertTrue(
                "关库后唤醒的写必须失败，而不是落进已关闭的连接池：$failure",
                failure is BackupWriteDeferredException,
            )
            assertFalse("被让路的写不能偷偷执行", writeRan)
        }

    @Test
    fun `restore admission is idempotent when ended twice`() = gateTest {
        val gate = BackupWriteGate()
        val admission = gate.beginRestore("恢复备份")
        admission.endRestore()
        admission.endRestore()

        val next = gate.beginRestore("恢复备份")
        next.beginCommit()
        next.endRestore()
        gate.withWriteAdmission("采集保存") { }
        assertEquals(false, gate.databaseClosedForRestart.value)
    }

    @Test
    fun `backup job is mutually exclusive with restore and with another backup`() = gateTest {
        val gate = BackupWriteGate()
        val job = gate.beginBackupJob("创建完整备份")
        try {
            val restoreFailure = runCatching { gate.beginRestore("恢复备份") }.exceptionOrNull()
            assertTrue(
                "备份成包期间不许开始恢复：$restoreFailure",
                restoreFailure is BackupWriteDeferredException,
            )
            assertTrue(
                "提示必须指向备份正在进行：${restoreFailure?.message}",
                restoreFailure?.message.orEmpty().contains("备份"),
            )

            val secondJobFailure = runCatching { gate.beginBackupJob("创建完整备份") }
                .exceptionOrNull()
            assertTrue(
                "并发备份必须显式拒绝：$secondJobFailure",
                secondJobFailure is BackupWriteDeferredException,
            )
        } finally {
            job.end()
        }
        gate.beginRestore("恢复备份").endRestore()
    }

    @Test
    fun `backup job releases the restore admission when it ends`() = gateTest {
        val gate = BackupWriteGate()
        val job = gate.beginBackupJob("创建完整备份")
        job.end()
        job.end()

        val admission = gate.beginRestore("恢复备份")
        admission.endRestore()
        gate.beginBackupJob("创建完整备份").end()
    }

    /**
     * 每个用例都套一层超时：断言失败时未被放行的写/备份协程会让 `runBlocking`
     * 永不返回；超时把挂起变成失败。
     */
    private fun gateTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeoutOrNull(15_000L) { block.invoke(this) }
            ?: error("gate test timed out")
    }
}
