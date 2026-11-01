package com.tingyun.smartmistakebook.core.database

import android.util.Log
import androidx.room3.PooledConnection
import androidx.room3.RoomDatabase
import kotlinx.coroutines.CancellationException

/**
 * 导入期数据库维护（F6 性能剖析 2026-10-31 的落地）：只做两件**不改变逻辑内容**的事。
 *
 * 1. **会话级 pragma 调优**：[withImportTuning] 在 block 期间把写连接的
 *    `synchronous` / `cache_size` 改到批量写入更合适的值，退出时恢复原值。
 *    - `synchronous` 是**连接级**设置，进程退出即消失，不需要持久化；
 *    - `cache_size = -32768` 表示 32 MiB 页缓存（负数 = KiB），只影响页命中率；
 *    - 不碰 `journal_mode`：它是**数据库级**、会写进库文件、且与 Room 的 WAL 形态冲突
 *      （切到 MEMORY 会把 WAL 丢弃并让崩溃恢复失去依据）——因此本轮明确不采用；
 *    - 不碰 `temp_store`：建索引的排序可能占用大量**原生**内存，低端设备上不划算，
 *      且剖析没有把它列为主要成本。
 *    - 两个 pragma 都不改变任何 SQL 的语义：同样的语句、同样的绑定、同样的行。
 *
 * 2. **二级索引窗口**：[withDeferredFeatureIndexes] 在 block 期间移除
 *    `knowledge_search_feature` 的两条二级索引，退出时按 Room schema 的原 DDL 建回。
 *    首次导入四科重建要写 547k 行特征；逐行维护两条二级索引的成本远高于最后一次性
 *    建索引。窗口内主键索引与表内容不变，所有查询结果不变，只是个别读可能走全扫。
 *    崩溃窗口由 [ensureFeatureIndexes] 在每次 `install()` 起点自愈（幂等 DDL）。
 *
 * 为什么不在这里持有写连接跑整个 block：KD-51 已实测「一直占住唯一写连接」会让同进程
 * 其它写排到 270s。本类每次只短暂取/还连接，block 内部仍走原有的短写事务。
 */
internal class RoomKnowledgeImportMaintenance(
    private val database: RoomDatabase,
) {

    /** 幂等补建两条二级索引；索引已在时 `CREATE INDEX IF NOT EXISTS` 是 O(1) 检查。 */
    suspend fun ensureFeatureIndexes() {
        database.withRawConnection(isReadOnly = false) { connection ->
            FEATURE_INDEX_ENSURE_DDL.forEach { connection.executeDdl(it) }
        }
    }

    /**
     * 在导入调优 pragma 下执行 [block]。**先读旧值、再改、finally 恢复**：
     * 即使 block 抛异常或协程被取消，写连接也不会把 `synchronous=OFF` 留在进程里。
     *
     * 旧值读取与设置分两次短暂取连接，是因为 block 内不允许被本类长期占住写连接
     * （见类注释的 KD-51 约束）；Room 的写池只有一条连接，所以两次拿到的是同一条。
     */
    suspend fun <R> withImportTuning(block: suspend () -> R): R {
        val previousSynchronous = database.writerPragmaValue("PRAGMA synchronous")
        val previousCacheSize = database.writerPragmaValue("PRAGMA cache_size")
        database.withRawConnection(isReadOnly = false) { connection ->
            connection.executeDdl("PRAGMA synchronous = OFF")
            connection.executeDdl("PRAGMA cache_size = -32768")
        }
        try {
            return block()
        } finally {
            val restoreFailure = runCatching {
                database.withRawConnection(isReadOnly = false) { connection ->
                    previousSynchronous?.let {
                        connection.executeDdl("PRAGMA synchronous = $it")
                    }
                    previousCacheSize?.let {
                        connection.executeDdl("PRAGMA cache_size = $it")
                    }
                }
            }.exceptionOrNull()
            if (restoreFailure != null) {
                // 取消必须原样传出（吞掉 CancellationException 会破坏结构化并发）；
                // 其它恢复失败不应掩盖 block 的真实结果，写连接会随进程/连接关闭回到默认值。
                if (restoreFailure is CancellationException) throw restoreFailure
                Log.w(TAG, "failed to restore import pragmas; writer may keep tuning values", restoreFailure)
            }
        }
    }

    /**
     * 在两条二级索引被移除的窗口内执行 [block]，退出时（成功/失败/取消）无条件补建。
     * 进入前先 ensure 一次：上一次崩溃留下的「索引缺失」状态在这里先收敛，再进入窗口。
     */
    suspend fun <R> withDeferredFeatureIndexes(block: suspend () -> R): R {
        ensureFeatureIndexes()
        database.withRawConnection(isReadOnly = false) { connection ->
            FEATURE_INDEX_DROP_DDL.forEach { connection.executeDdl(it) }
        }
        try {
            return block()
        } finally {
            ensureFeatureIndexes()
        }
    }
}

/** `knowledge_search_feature` 两条二级索引的**原 DDL**（与 Room schema 71 逐字同义）。 */
internal val FEATURE_INDEX_ENSURE_DDL = listOf(
    "CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_knowledge_node_id` " +
        "ON `knowledge_search_feature` (`knowledge_node_id`)",
    "CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_subject_knowledge_node_id` " +
        "ON `knowledge_search_feature` (`subject`, `knowledge_node_id`)",
)

internal val FEATURE_INDEX_DROP_DDL = listOf(
    "DROP INDEX IF EXISTS `index_knowledge_search_feature_knowledge_node_id`",
    "DROP INDEX IF EXISTS `index_knowledge_search_feature_subject_knowledge_node_id`",
)

/** 执行一条无参数 DDL/pragma；`step()` 对 DDL 返回 false，调用一次即执行。 */
private suspend fun PooledConnection.executeDdl(sql: String) {
    usePrepared(sql) { statement -> statement.step() }
}

/** 读一条返回单值的 pragma（`PRAGMA synchronous` 等）；无结果行返回 null。 */
private suspend fun RoomDatabase.writerPragmaValue(sql: String): Long? {
    var value: Long? = null
    withRawConnection(isReadOnly = false) { connection ->
        connection.usePrepared(sql) { statement ->
            if (statement.step()) value = statement.getLong(0)
        }
    }
    return value
}

private const val TAG = "KnowledgeImportTuning"
