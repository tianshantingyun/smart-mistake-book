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
 * **恢复生命周期（KD-56）**：恢复的交换段必须先把在途写排空、再把新写挡在门外
 * （[beginRestore] → [RestoreAdmission.beginCommit]），因为交换会关闭 room3 的连接池。
 * 关库之后调用 [markDatabaseClosedForRestart] 把本门置为**进程内终态**：之后任何
 * 接入本门的写都立即抛 [BackupWriteDeferredException]，而不是落进已关闭的
 * `PassthroughConnectionPool` 抛 `SQLITE_MISUSE`（21）把进程打死。终态只能靠进程
 * 重启解除——与 room3 `close()` 的终态语义一致，本门不提供“热重开”。
 *
 * 线程安全：状态转移与计数都在同一把 [Mutex] 下；真实代码路径的写/备份分别在不同
 * 协程/线程上调用，不能用普通字段裸读。
 *
 * @param maxWriteQueueWaitMillis 写入口在备份期间最多排队多久；超时抛可重试异常。
 * @param drainTimeoutMillis 备份/恢复等待在途写排空的上限；超时则明确失败（不带着
 *   随意快照或关闭连接池硬开始）。
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

    /**
     * 一次完整备份（快照 + 成包 + 哈希）正在进行。与 [backupInProgress] 不同：它**不**
     * 让写入口排队（独立连接快照不占 Room 写连接），只负责让"备份"与"恢复/另一个备份"
     * 互斥——恢复的交换段会关库换文件，绝不能与一份仍在读取活库的备份重叠。
     */
    private var backupJobInProgress = false

    /**
     * 恢复从开始（校验/解包）到交换完成都在进行中。恢复期间**拒绝新备份**：一份在恢复
     * 交换窗口里生成的快照没有意义，且两条重操作会在文件层互相踩。
     */
    private var restoreInProgress = false

    /**
     * 恢复的交换段：在途写必须排空，新写排队。与 [restoreInProgress] 分开，因为
     * 校验/解包阶段不碰活库，没必要把写入口关在门外几分钟。
     */
    private var restoreCommitInProgress = false

    /** 当前备份结束（含失败/取消）时完成；备份期间到达的写入口等它。 */
    private var backupFinished: CompletableDeferred<Unit>? = null

    /** 当前恢复结束（含失败/取消）时完成；交换段排队中的写入口等它。 */
    private var restoreFinished: CompletableDeferred<Unit>? = null

    /** 备份开始时仍在途的写全部完成时完成；备份靠它做"写队列排空"。 */
    private var writersDrained: CompletableDeferred<Unit>? = null

    /** 恢复交换段开始时仍在途的写全部完成时完成。 */
    private var restoreDrained: CompletableDeferred<Unit>? = null

    private val mutableActivity = MutableStateFlow<BackupActivity>(BackupActivity.Idle)

    /** 备份活动的显式状态：[BackupActivity.Idle] 之外的阶段说明备份正在进行。 */
    val activity: StateFlow<BackupActivity> = mutableActivity.asStateFlow()

    private val mutableDatabaseClosedForRestart = MutableStateFlow(false)

    /**
     * 数据库是否已为恢复关闭（进程内终态）。界面/写入口可据此停止数据库操作并提示重启；
     * 写入口本身在 [withWriteAdmission] 里会直接失败，不依赖观察者是否订阅。
     */
    val databaseClosedForRestart: StateFlow<Boolean> =
        mutableDatabaseClosedForRestart.asStateFlow()

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
            if (mutableDatabaseClosedForRestart.value) throw databaseClosedException()
            if (restoreInProgress) {
                throw BackupWriteDeferredException(
                    "恢复正在进行，备份暂未开始；请等恢复完成后再试。",
                )
            }
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
            var waitReason: String? = null
            lock.withLock {
                // 进程内终态：已关闭的 room3 连接池不能再被任何入口碰到（KD-56）。
                if (mutableDatabaseClosedForRestart.value) throw databaseClosedException()
                when {
                    backupInProgress -> {
                        waitFor = backupFinished
                        waitReason = "备份正在进行"
                    }
                    restoreCommitInProgress -> {
                        waitFor = restoreFinished
                        waitReason = "恢复正在进行"
                    }
                    else -> activeWriters += 1
                }
            }
            val signal = waitFor ?: break
            val resumed = withTimeoutOrNull(maxWriteQueueWaitMillis) { signal.await() }
            if (resumed == null) {
                throw BackupWriteDeferredException(
                    "${waitReason ?: "备份正在进行"}，$entry 暂未执行；请等当前操作完成后再试。",
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
                        restoreDrained?.complete(Unit)
                    }
                }
            }
        }
    }

    /**
     * 登记一次完整备份，与恢复/另一个备份互斥；**不**阻塞普通写入口。
     *
     * 调用方必须在 `finally` 里 [BackupJob.end]。只需要快照段的写者屏障时仍用
     * [runBackup]（旧回退路径）；独立连接路径用本方法登记整个成包过程。
     */
    suspend fun beginBackupJob(entry: String): BackupJob {
        lock.withLock {
            if (mutableDatabaseClosedForRestart.value) throw databaseClosedException()
            if (restoreInProgress) {
                throw BackupWriteDeferredException(
                    "恢复正在进行，$entry 暂未开始；请等恢复完成后再试。",
                )
            }
            if (backupJobInProgress) {
                throw BackupWriteDeferredException("备份已在进行中，请等当前备份完成。")
            }
            backupJobInProgress = true
        }
        return BackupJob()
    }

    /** 一次完整备份的登记票据；[end] 幂等。 */
    inner class BackupJob internal constructor() {
        private var ended = false

        suspend fun end() {
            withContext(NonCancellable) {
                lock.withLock {
                    if (ended) return@withLock
                    ended = true
                    backupJobInProgress = false
                }
            }
        }
    }

    /**
     * 恢复的开始：登记 [restoreInProgress]（阻止新备份），但**不**阻塞写入口——校验/
     * 解包可能持续数分钟，这期间活库没有被交换，写可以照常进行。
     *
     * 交换段必须再调用 [RestoreAdmission.beginCommit]：排空在途写并挡住新写，直到
     * [RestoreAdmission.endRestore] 或 [markDatabaseClosedForRestart]。
     *
     * 备份进行中时直接拒绝（不是排队）：备份可能持续十分钟，让恢复在后台干等没有意义。
     */
    suspend fun beginRestore(entry: String): RestoreAdmission {
        lock.withLock {
            if (mutableDatabaseClosedForRestart.value) throw databaseClosedException()
            if (restoreInProgress) {
                throw BackupWriteDeferredException("恢复已在进行中，重复的恢复请求被拒绝。")
            }
            if (backupJobInProgress || backupInProgress) {
                throw BackupWriteDeferredException(
                    "备份正在进行，$entry 暂未执行；请等备份完成后再试。",
                )
            }
            restoreInProgress = true
            restoreFinished = CompletableDeferred()
        }
        return RestoreAdmission(entry)
    }

    /**
     * 把门置为**进程内终态**：room3 的 `PassthroughConnectionPool.close()` 不可逆，
     * 关库后本进程不得再碰数据库。调用本方法后：
     *   - 等待中的写入口被唤醒，并在重新判断状态时立即失败（不是继续写）；
     *   - 新备份/新恢复入口直接拒绝；
     *   - [databaseClosedForRestart] 变为 true，界面可据此停止数据库内容并提示重启。
     */
    suspend fun markDatabaseClosedForRestart() {
        lock.withLock {
            if (mutableDatabaseClosedForRestart.value) return
            mutableDatabaseClosedForRestart.value = true
            // 唤醒所有等待者；它们回到循环后会看到终态并失败。
            backupFinished?.complete(Unit)
            restoreFinished?.complete(Unit)
            writersDrained?.complete(Unit)
            restoreDrained?.complete(Unit)
        }
    }

    private fun databaseClosedException(): BackupWriteDeferredException =
        BackupWriteDeferredException(
            "数据库已为备份恢复关闭，请完全退出并重新打开应用后再操作。",
        )

    /**
     * 一次恢复的登记票据。恢复流程必须：
     * 1. [beginRestore] 取得票据（阻止新备份）；
     * 2. 在关闭活库前调用 [beginCommit]（排空在途写、挡住新写）；
     * 3. 无论成功/失败/取消都在 `finally` 调用 [endRestore]；
     * 4. 若交换段关闭了活库，先 [markDatabaseClosedForRestart] 再 [endRestore]。
     *
     * 票据自身不持有协程，不保证线程；方法都在门的 [Mutex] 下串行化。
     */
    inner class RestoreAdmission internal constructor(
        private val entry: String,
    ) {
        private var commitStarted = false
        private var ended = false

        /**
         * 关闭活库前的临界区入口：等在途写排空（有上限），期间到达的写排队。
         * 排空超时抛 [BackupWriteDeferredException]，且**没有**进入交换段。
         */
        suspend fun beginCommit() {
            val drained = CompletableDeferred<Unit>()
            lock.withLock {
                check(!ended) { "Restore admission for $entry already ended" }
                if (mutableDatabaseClosedForRestart.value) throw databaseClosedException()
                if (commitStarted) return
                commitStarted = true
                restoreCommitInProgress = true
                restoreDrained = drained
                if (activeWriters == 0) drained.complete(Unit)
            }
            val drainedInTime = withTimeoutOrNull(drainTimeoutMillis) { drained.await() }
            if (drainedInTime == null) {
                lock.withLock {
                    restoreCommitInProgress = false
                    restoreDrained = null
                    restoreFinished?.complete(Unit)
                }
                throw BackupWriteDeferredException(
                    "恢复没有开始：仍有写入在进行，请稍后重试。",
                )
            }
        }

        /** 结束本次恢复的登记（成功、失败、取消都必须调用）；幂等。 */
        suspend fun endRestore() {
            withContext(NonCancellable) {
                lock.withLock {
                    if (ended) return@withLock
                    ended = true
                    restoreInProgress = false
                    restoreCommitInProgress = false
                    restoreDrained = null
                    restoreFinished?.complete(Unit)
                    restoreFinished = null
                }
            }
        }
    }
}
