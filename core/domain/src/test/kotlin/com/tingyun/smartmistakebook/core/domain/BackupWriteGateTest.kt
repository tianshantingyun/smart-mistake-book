package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-54：备份快照与写入口之间的「让路」门，JVM 面。
 *
 * 钉住的四件事：
 * 1. 备份期间到达的写**排队**（不执行、也不进数据库），备份结束后继续——不是
 *    "连接池里饿到 `unable to grant a connection`"；
 * 2. 排队有上限：超过 `maxWriteQueueWaitMillis` 抛可重试异常，且块**没有被执行**；
 * 3. 备份开始前等在途写排空；排空超时则备份明确失败；
 * 4. 状态显式可观察（[BackupActivity]），失败/取消也会回到 Idle 并放行写。
 *
 * 用 `runBlocking` + 真实 `CompletableDeferred`/`delay`：门的语义是"谁先拿到锁/谁在等"，
 * 不需要虚拟时间也能稳定观察。
 */
class BackupWriteGateTest {
    @Test
    fun `a write issued while the snapshot runs waits and then proceeds`() = gateTest {
        val gate = BackupWriteGate(maxWriteQueueWaitMillis = 5_000L)
        val snapshotStarted = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        var writeRan = false

        val backup = launch {
            gate.runBackup {
                snapshotStarted.complete(Unit)
                releaseSnapshot.await()
            }
        }
        snapshotStarted.await()
        assertTrue(
            "快照阶段必须显式可见",
            gate.activity.value is BackupActivity.Snapshotting,
        )

        val write = async {
            gate.withWriteAdmission("采集保存") { writeRan = true }
        }
        delay(150L)
        assertFalse("备份快照期间写必须排队，不能先执行", writeRan)
        assertFalse("备份快照期间写必须仍未完成", write.isCompleted)

        releaseSnapshot.complete(Unit)
        backup.join()
        write.await()
        assertTrue("备份结束后排队的写必须继续执行", writeRan)
        assertTrue("备份结束后门回到 Idle", gate.activity.value is BackupActivity.Idle)
    }

    @Test
    fun `a queued write fails with a retryable prompt after the short wait`() = gateTest {
        val gate = BackupWriteGate(maxWriteQueueWaitMillis = 100L)
        val snapshotStarted = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        var writeRan = false

        val backup = launch {
            gate.runBackup {
                snapshotStarted.complete(Unit)
                releaseSnapshot.await()
            }
        }
        snapshotStarted.await()

        val failure = runCatching {
            gate.withWriteAdmission("采集保存") { writeRan = true }
        }

        val rejection = failure.exceptionOrNull()
        assertTrue(
            "排队超时必须抛可重试的明确异常，实际：$rejection",
            rejection is BackupWriteDeferredException,
        )
        assertTrue(
            "提示必须说清是备份期间的让路：${rejection?.message}",
            rejection?.message.orEmpty().contains("备份"),
        )
        assertFalse("被让路的写不能偷偷执行", writeRan)

        releaseSnapshot.complete(Unit)
        backup.join()
        assertTrue(gate.activity.value is BackupActivity.Idle)
    }

    @Test
    fun `backup drains in-flight writes before it starts the snapshot`() = gateTest {
        val gate = BackupWriteGate(drainTimeoutMillis = 5_000L)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var snapshotStarted = false

        val write = launch {
            gate.withWriteAdmission("采集保存") {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
        }
        writeStarted.await()

        val backup = launch {
            gate.runBackup { snapshotStarted = true }
        }
        delay(150L)
        assertFalse("在途写没排空前不许开始快照", snapshotStarted)
        assertTrue(
            "排空阶段必须显式可见",
            gate.activity.value is BackupActivity.DrainingWriters,
        )

        releaseWrite.complete(Unit)
        write.join()
        backup.join()
        assertTrue("在途写完成后备份必须开始并完成快照", snapshotStarted)
    }

    @Test
    fun `backup fails with a readable reason when the drain times out`() = gateTest {
        val gate = BackupWriteGate(drainTimeoutMillis = 100L)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var snapshotStarted = false

        val write = launch {
            gate.withWriteAdmission("采集保存") {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
        }
        writeStarted.await()

        val failure = runCatching { gate.runBackup { snapshotStarted = true } }
        val rejection = failure.exceptionOrNull()
        assertTrue(
            "排空超时必须明确失败，而不是带着随意快照硬开始：$rejection",
            rejection is BackupWriteDeferredException,
        )
        assertFalse(snapshotStarted)
        assertTrue("失败后门必须回到 Idle", gate.activity.value is BackupActivity.Idle)

        releaseWrite.complete(Unit)
        write.join()

        // 失败没有烧坏门：之后仍然可以正常让路/写入。
        gate.withWriteAdmission("备份完成后写入") { snapshotStarted = true }
        assertTrue(snapshotStarted)
    }

    @Test
    fun `a second backup is rejected while one is running`() = gateTest {
        val gate = BackupWriteGate()
        val snapshotStarted = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()

        val first = launch {
            gate.runBackup {
                snapshotStarted.complete(Unit)
                releaseSnapshot.await()
            }
        }
        snapshotStarted.await()

        val failure = runCatching { gate.runBackup { } }
        assertTrue(
            "并发备份必须显式拒绝：${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is BackupWriteDeferredException,
        )

        releaseSnapshot.complete(Unit)
        first.join()
        assertTrue(gate.activity.value is BackupActivity.Idle)
    }

    @Test
    fun `a failed backup releases writes that queued behind it`() = gateTest {
        val gate = BackupWriteGate(maxWriteQueueWaitMillis = 5_000L)
        val snapshotStarted = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        var writeRan = false

        val backup = launch {
            runCatching {
                gate.runBackup {
                    snapshotStarted.complete(Unit)
                    releaseSnapshot.await()
                    error("snapshot failed")
                }
            }
        }
        snapshotStarted.await()

        val write = async {
            gate.withWriteAdmission("采集保存") { writeRan = true }
        }
        delay(150L)
        assertFalse(writeRan)

        releaseSnapshot.complete(Unit)
        backup.join()
        write.await()
        assertTrue("备份失败也必须放行排队的写", writeRan)
        assertTrue(gate.activity.value is BackupActivity.Idle)
    }

    @Test
    fun `a cancelled backup releases queued writes and resets state`() = gateTest {
        val gate = BackupWriteGate(maxWriteQueueWaitMillis = 5_000L)
        val snapshotStarted = CompletableDeferred<Unit>()
        var writeRan = false

        val backup = launch {
            gate.runBackup {
                snapshotStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        snapshotStarted.await()

        val write = async {
            gate.withWriteAdmission("采集保存") { writeRan = true }
        }
        delay(100L)
        assertFalse("备份期间写必须排队", writeRan)

        backup.cancelAndJoin()
        write.await()
        assertTrue("取消备份后排队写必须放行（清理不能被取消拿走）", writeRan)
        assertTrue(gate.activity.value is BackupActivity.Idle)
    }

    @Test
    fun `writes inside the backup block bypass the gate instead of waiting on themselves`() = gateTest {
        val gate = BackupWriteGate(maxWriteQueueWaitMillis = 100L)

        var innerWriteRan = false
        gate.runBackup {
            gate.withWriteAdmission("备份内部写") { innerWriteRan = true }
        }

        assertTrue(
            "备份独占段内的写必须直接放行（否则 checkpoint/快照会等自己），" +
                "实际 innerWriteRan=$innerWriteRan",
            innerWriteRan,
        )
        assertEquals(BackupActivity.Idle, gate.activity.value)
    }

    /**
     * 每个用例都套一层超时：若断言在释放 [CompletableDeferred] 之前失败，未被放行的
     * 备份/写协程会让 `runBlocking` 永不返回；超时把失败变成失败，而不是挂住测试进程。
     *
     * 接收者是 `CoroutineScope`：用例体里的 `launch`/`async` 必须绑定到本作用域；
     * 没有接收者时 Kotlin 会解析到 kotlinx.coroutines 的顶层废弃重载
     * （`DeprecationLevel.ERROR`），编译直接失败。
     */
    private fun gateTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        kotlinx.coroutines.withTimeout(15_000L) { block.invoke(this) }
    }
}
