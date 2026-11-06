package com.tingyun.smartmistakebook.core.database

import android.util.Log
import androidx.room3.PooledConnection
import androidx.room3.RoomDatabase
import androidx.room3.Transactor
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
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

    /**
     * 幂等补建两条二级索引。
     *
     * **先查 `sqlite_master`，缺失才 CREATE**：SQLite 3.8.10.2（API 23）把「索引已存在时的
     * `CREATE INDEX IF NOT EXISTS`」判成只读语句（`ifNotExist` 分支在
     * `sqlite3BeginWriteOperation()` 之前就跳出），Android 框架据此申请只读连接并抛
     * `Cannot execute this statement ... connection is read-only`。语句不执行，就不存在这个判定；
     * 索引缺失时 CREATE 是真写，`stmt_readonly=false`，与 SQLite 版本无关。
     * `IF NOT EXISTS` 仍保留：目录检查与执行之间还有重入窗口，它是并发兜底。
     */
    suspend fun ensureFeatureIndexes() {
        inIndexDdlTransaction { transactor ->
            val present = transactor.indexNames(FEATURE_INDEX_TABLE)
            FEATURE_INDEX_DDL.forEach { index ->
                if (index.name !in present) transactor.executeDdl(index.createSql)
            }
        }
    }

    /**
     * 在一次 `BEGIN IMMEDIATE` 写事务里执行索引 DDL。
     *
     * 两个作用：①事务期间写连接被整段复用（Room 的 `PassthroughConnection` 按协程上下文复用），
     * 即使某条 DDL 仍被判只读，也不会被框架的 `throwIfStatementForbidden` 拒绝；
     * ②两条 DDL 原子，不会留下「只建/只删了一条」的半状态。
     *
     * 只包 ensure/drop 这几条短 DDL，不包导入主体（KD-51：长期占住唯一写连接会把同进程其它写
     * 排到 270s）。`internal` 是测试缝：用例据此断言 DDL 确实运行在事务内。
     */
    internal suspend fun <R> inIndexDdlTransaction(block: suspend (Transactor) -> R): R =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block(transactor) }
        }

    /**
     * 在导入调优 pragma 下执行 [block]。**先读旧值、再改、finally 恢复**：
     * 即使 block 抛异常或协程被取消，写连接也不会把 `synchronous=NORMAL` 留在进程里。
     *
     * 旧值读取与设置分两次短暂取连接，是因为 block 内不允许被本类长期占住写连接
     * （见类注释的 KD-51 约束）；Room 的写池只有一条连接，所以两次拿到的是同一条。
     */
    suspend fun <R> withImportTuning(block: suspend () -> R): R {
        val previousSynchronous = database.writerPragmaValue("PRAGMA synchronous")
        val previousCacheSize = database.writerPragmaValue("PRAGMA cache_size")
        database.withRawConnection(isReadOnly = false) { connection ->
            connection.executeDdl("PRAGMA synchronous = NORMAL")
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
     *
     * DROP 侧同样先查 `sqlite_master`、**存在才 DROP**：SQLite 3.8.10.2 把「索引不存在时的
     * `DROP INDEX IF EXISTS`」也判成只读（同一根因，见 [ensureFeatureIndexes]）；
     * `IF EXISTS` 同样保留为并发/重入兜底。
     */
    suspend fun <R> withDeferredFeatureIndexes(block: suspend () -> R): R {
        ensureFeatureIndexes()
        inIndexDdlTransaction { transactor ->
            val present = transactor.indexNames(FEATURE_INDEX_TABLE)
            FEATURE_INDEX_DDL.forEach { index ->
                if (index.name in present) transactor.executeDdl(index.dropSql)
            }
        }
        try {
            return block()
        } finally {
            ensureFeatureIndexes()
        }
    }
}

internal data class IndexDdl(
    val name: String,
    val createSql: String,
    val dropSql: String,
)

private const val FEATURE_INDEX_TABLE = "knowledge_search_feature"

/** `knowledge_search_feature` 两条二级索引的**原 DDL**（与 Room schema 73 逐字同义）。 */
private val FEATURE_INDEX_DDL = listOf(
    IndexDdl(
        name = "index_knowledge_search_feature_knowledge_node_id",
        createSql = "CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_knowledge_node_id` " +
            "ON `knowledge_search_feature` (`knowledge_node_id`)",
        dropSql = "DROP INDEX IF EXISTS `index_knowledge_search_feature_knowledge_node_id`",
    ),
    IndexDdl(
        name = "index_knowledge_search_feature_subject_knowledge_node_id",
        createSql = "CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_subject_knowledge_node_id` " +
            "ON `knowledge_search_feature` (`subject`, `knowledge_node_id`)",
        dropSql = "DROP INDEX IF EXISTS `index_knowledge_search_feature_subject_knowledge_node_id`",
    ),
)

// 保留原有类型与字符串内容：JVM 等价测试按 List<String> 与 schema 比对。
internal val FEATURE_INDEX_ENSURE_DDL: List<String> = FEATURE_INDEX_DDL.map { it.createSql }
internal val FEATURE_INDEX_DROP_DDL: List<String> = FEATURE_INDEX_DDL.map { it.dropSql }

/**
 * 目录读取：用 `sqlite_master`，不用 `PRAGMA index_list`（旧版本列集/含义有差异）。
 * 参数只绑定表名常量，不拼接外部输入。
 */
private suspend fun PooledConnection.indexNames(table: String): Set<String> {
    val names = mutableSetOf<String>()
    usePrepared("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?") { statement ->
        statement.bindText(1, table)
        while (statement.step()) names += statement.getText(0)
    }
    return names
}

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
