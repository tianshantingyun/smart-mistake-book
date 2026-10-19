package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 备份活动的显式状态（KD-54）。
 *
 * 备份快照是一次 `VACUUM INTO`：SQLite 要求它在**一条语句**里跑完，期间占住 room3 唯一
 * 的写连接（设备实测 601.274s，期间 40 条 `unable to grant a connection`）。本状态让
 * 写入口和界面都能看见"现在正处于快照阶段"，而不是只能从数据库连接池超时里猜。
 */
sealed interface BackupActivity {
    /** 没有备份在进行，写入口直接放行。 */
    data object Idle : BackupActivity

    /**
     * 备份已登记，正在等**在途**写完成（备份前写队列排空）。此状态下新到的写入口
     * 不再进入数据库，而是短暂排队。
     */
    data class DrainingWriters(val startedAtEpochMillis: Long) : BackupActivity

    /**
     * 快照语句正在执行（`VACUUM INTO`）。写入口在此期间短暂排队；备份结束（成功或失败）
     * 后统一放行。
     */
    data class Snapshotting(val startedAtEpochMillis: Long) : BackupActivity
}

/**
 * 写入口在备份快照期间排队超时后抛出：**没有写进数据库**，调用方可以安全重试。
 *
 * 与"连接池饿死 601s 后抛出的 `unable to grant a connection`"的区别：这个消息在
 * [BackupWriteGate.maxWriteQueueWaitMillis] 内就返回，说明"因为备份，这次没有写"，
 * 而不是让调用方（和用户）等分钟级。
 */
class BackupWriteDeferredException(message: String) : Exception(message)

/**
 * 备份独占段的协程标记：`runBackup` 的块（含它启动的子协程）里再调用
 * [BackupWriteGate.withWriteAdmission] 时直接放行——备份自己的 checkpoint/快照不能被
 * 自己的门挡住。只在进程内、协程上下文里生效，不写任何全局静态状态。
 */
private object BackupExclusiveKey : CoroutineContext.Key<BackupExclusive>

private object BackupExclusive : AbstractCoroutineContextElement(BackupExclusiveKey)

/**
 * 备份/写入口之间的"让路"门（KD-54，medium）。
 *
 * **它消灭的失败**：备份快照（`VACUUM INTO`）持续十分钟时，同进程的写请求在 room3
 * 连接池里排队到 `unable to grant a connection`（设备实测 40 条、最长 601s），采集提交
 * 等用户动作表现为卡死。本门把这段等待从"数据库连接池里的分钟级饥饿"提前成：
 *
 * 1. **备份前**：先登记 [BackupActivity.DrainingWriters] 并等在途写排空（有上限）；
 * 2. **备份期间**：写入口在 [maxWriteQueueWaitMillis] 内短暂排队；超时则抛
 *    [BackupWriteDeferredException]（明确"这次没写，可重试"），不再把写请求送进
 *    已被快照占住的连接池；
 * 3. **备份后**（成功、失败或取消的 finally）：状态回到 [BackupActivity.Idle]，
 *    排队的写在放行信号上醒来、重新判断门状态后继续（唤醒顺序由调度器决定，不做
 *    FIFO 承诺）。
 *
 * **不改变快照一致性机制**：`snapshotForBackup` 仍是原来的 `VACUUM INTO`（或旧
 * SQLite 的 checkpoint + 只读拷贝）；分块 checkpoint 拷贝留待另行设计。本门只调整
 * "谁能在快照期间进入数据库写路径"。
 *
 * **计数口径**：[withWriteAdmission] 的计数只覆盖显式接入本门的写入口；未接入的 DAO
 * 直写仍会在快照期间落到连接池。本轨接入了备份仓库与采集仓库的写入口，其余写入路径的
 * 接入面见批次记录（设备复核对饥饿的判定以真实入口为准）。
 *
 * 线程安全：状态转移与计数都在同一把 [Mutex] 下；真实代码路径的写/备份分别在不同
 * 协程/线程上调用，不能用普通字段裸读。
 *
 * @param maxWriteQueueWaitMillis 写入口在备份期间最多排队多久；超时抛可重试异常。
 * @param drainTimeoutMillis 备份等待在途写排空的上限；超时则备份明确失败（不带着
 *   随意快照硬开始）。
 */
class BackupWriteGate(
    private val maxWriteQueueWaitMillis: Long = 2_000L,
    private val drainTimeoutMillis: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /**
         * 进程内共享门：生产装配让备份仓库与采集等写入口看到**同一个**实例。
         * 测试可以构造自己的门，不需要也不应该依赖这里的全局状态。
         */
        val shared = BackupWriteGate()
    }

    init {
        require(maxWriteQueueWaitMillis > 0L) {
            "Backup write queue wait must be positive"
        }
        require(drainTimeoutMillis > 0L) {
            "Backup drain timeout must be positive"
        }
    }

    private val lock = Mutex()
    private var activeWriters = 0
    private var backupInProgress = false

    /** 当前备份结束（含失败/取消）时完成；备份期间到达的写入口等它。 */
    private var backupFinished: CompletableDeferred<Unit>? = null

    /** 备份开始时仍在途的写全部完成时完成；备份靠它做"写队列排空"。 */
    private var writersDrained: CompletableDeferred<Unit>? = null

    private val mutableActivity = MutableStateFlow<BackupActivity>(BackupActivity.Idle)

    /** 备份活动的显式状态：[BackupActivity.Idle] 之外的阶段说明备份正在进行。 */
    val activity: StateFlow<BackupActivity> = mutableActivity.asStateFlow()

    /**
     * 跑一次备份快照段：登记状态 → 等在途写排空 → 进入独占段执行 [block] → 放行写入口。
     *
     * [block] 只应包住真正占用数据库写连接的快照步骤（checkpoint + `VACUUM INTO`），
     * **不要**把成包/压缩/哈希这类不碰写连接的活包进来：那会把写入口无谓地关在门外
     * 整个归档生成过程。
     *
     * 并发备份：已有备份在进行时直接抛 [BackupWriteDeferredException]，不排队——
     * 两个备份同时读同一份库没有意义，而且界面上本来就是串行的。
     */
    suspend fun <T> runBackup(block: suspend () -> T): T {
        val finished = CompletableDeferred<Unit>()
        val drained = CompletableDeferred<Unit>()
        val startedAtEpochMillis = clock()
        lock.withLock {
            if (backupInProgress) {
                throw BackupWriteDeferredException("备份已在进行中，请等当前备份完成。")
            }
            backupInProgress = true
            backupFinished = finished
            writersDrained = drained
            if (activeWriters == 0) {
                drained.complete(Unit)
            }
            mutableActivity.value = BackupActivity.DrainingWriters(startedAtEpochMillis)
        }
        try {
            // 备份前写队列排空：只等"备份开始时已经在途"的写；备份开始后到达的写走排队，
            // 不会把这次等待无限拉长。
            if (!drained.isCompleted) {
                val drainedInTime = withTimeoutOrNull(drainTimeoutMillis) { drained.await() }
                if (drainedInTime == null) {
                    throw BackupWriteDeferredException(
                        "备份没有开始：仍有写入在进行，请稍后重试。",
                    )
                }
            }
            mutableActivity.value = BackupActivity.Snapshotting(clock())
            // 独占段：备份自己的 checkpoint/快照调用（含子协程）不再向本门排队。
            return withContext(BackupExclusive) { block() }
        } finally {
            // 取消时的清理不能被取消拿走：否则状态停在 Snapshotting、排队的写永远等不到
            // 放行，下一次备份也会被判成"已在进行中"。
            withContext(NonCancellable) {
                lock.withLock {
                    backupInProgress = false
                    backupFinished = null
                    writersDrained = null
                    mutableActivity.value = BackupActivity.Idle
                    // 备份后写队列放行：所有在备份期间排队的写在这里统一醒来，重新判断门状态。
                    finished.complete(Unit)
                }
            }
        }
    }

    /**
     * 写入口的准入：没有备份时登记一个在途写并执行 [block]；备份期间短暂排队，超时抛
     * [BackupWriteDeferredException]（**block 没有被执行**）。
     *
     * [entry] 只用于失败消息（例如"保存题目"），让调用方/用户知道是哪一类写被让路。
     */
    suspend fun <T> withWriteAdmission(entry: String, block: suspend () -> T): T {
        // 备份独占段内的写直接放行：否则 checkpoint/快照会等自己。
        if (currentCoroutineContext()[BackupExclusiveKey] != null) {
            return block()
        }
        while (true) {
            var waitFor: CompletableDeferred<Unit>? = null
            lock.withLock {
                if (backupInProgress) {
                    waitFor = backupFinished
                } else {
                    activeWriters += 1
                }
            }
            val signal = waitFor ?: break
            val resumed = withTimeoutOrNull(maxWriteQueueWaitMillis) { signal.await() }
            if (resumed == null) {
                throw BackupWriteDeferredException(
                    "备份正在进行，$entry 暂未执行；请等备份完成后再试。",
                )
            }
        }
        try {
            return block()
        } finally {
            // 同理：被取消的写也必须归还计数，否则备份永远等不到"排空"。
            withContext(NonCancellable) {
                lock.withLock {
                    activeWriters -= 1
                    if (activeWriters == 0) {
                        writersDrained?.complete(Unit)
                    }
                }
            }
        }
    }
}
