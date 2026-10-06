package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.KnowledgeNodeSuccessors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * S20：`knowledge_node` 合并重定向映射（[KnowledgeNodeSuccessors]）的进程内缓存。
 *
 * **它消灭的失败**：每次 drain 都重读 `knowledge_node` 全表（`superseded_by IS NOT NULL`
 * 在数万节点上是全表扫描），而 drain 由每次观察发射与每次写路径发布触发——驻留快照的每次
 * 重建都带着一遍与本次重建无关的全表读。合并重定向是**内容**输入：内容调和之外没有写路径
 * 会改它，逐次重读等于把"很少变"当成"每次都变"。
 *
 * **不变量（一次 drain 内映射恒定）**：调用方在一次 drain 的开头取一次实例，整个 drain
 * （增量投影与全量重放）共用它——[invalidate] 只影响**之后**的 [current] 调用，不触碰已经
 * 取出的实例（[KnowledgeNodeSuccessors] 本身不可变）。
 *
 * **失效依据**：`superseded_by` 的权威写入者是内容调和（`update-manifest.json` →
 * `knowledge_node.superseded_by`，见 [KnowledgeNodeSuccessors] 的 KDoc），它经 Room DAO
 * 落库。仓库订阅 `knowledge_node` 的表级失效信号
 * （`KnowledgeReadPort.observeKnowledgeNodeChanges`），任何经 Room 的节点写入（合并退役 /
 * 节点导入 / 确认路径补写）都会让缓存失效，下一次 drain 重读。**旁路直写 SQLite（不经
 * Room）不会触发失效**——那不是生产写路径，生产唯一改 `superseded_by` 的是内容调和。
 *
 * **失效不会被在飞加载吞掉**：加载挂在数据库调度器上、失效在仓库作用域上，两者跨线程可
 * 交错（应用启动把内容安装与首次排空并行）。加载前记录失效世代、安装前在同一把锁下复核
 * 世代——加载期间送达的失效会让这次结果被丢弃并重读（见 [loadVerified]）。因此不存在
 * "缓存住合并前映射、而本进程内再无失效来源"的状态。
 *
 * **仍存在的窗口（如实登记，不隐藏）**：失效经 Room 的失效投递**异步**到达。写提交与失效
 * 投递之间启动的 drain 仍可能读到旧映射——这与"drain 恰好读到写前状态"同类，窗口是毫秒级；
 * 生产改 `superseded_by` 只在启动/横幅重试的内容安装期发生。
 */
internal class KnowledgeNodeSuccessorsCache(
    /** 未命中时的加载器；调用方负责把它跑在数据库调度器上（drainer 的 `onDatabase`）。 */
    private val loadSuccessors: suspend () -> KnowledgeNodeSuccessors,
) {
    private val loadMutex = Mutex()

    /** 世代与缓存的"复核-安装"原子区：只包住非挂起代码，不阻塞协程。 */
    private val stateLock = Any()
    private var generation = 0L

    @Volatile
    private var cached: KnowledgeNodeSuccessors? = null

    /** 当前映射：命中直接返回；未命中在互斥下加载一次（并发 drain 不放大这次读）。 */
    suspend fun current(): KnowledgeNodeSuccessors {
        cached?.let { return it }
        return loadMutex.withLock {
            cached?.let { return@withLock it }
            loadVerified()
        }
    }

    /** 失效：递增世代并清缓存；下一次 [current] 重读。不打断在飞的 drain（见类 KDoc）。 */
    fun invalidate() {
        synchronized(stateLock) {
            generation++
            cached = null
        }
    }

    /**
     * 加载并只在"加载期间没有失效"时安装：安装与世代复核在同一把锁下完成，因此加载期间
     * 送达的失效不可能被随后写回的旧值吞掉（复核不过 → 丢弃重读）。
     *
     * 连续失效达到 [MAX_LOAD_ATTEMPTS] 次（失效风暴）时**不安装**，只把最后一次读到的值
     * 交给本次调用——宁可不缓存，也不能缓存可能过期的映射；下一次 [current] 会重新加载。
     */
    private suspend fun loadVerified(): KnowledgeNodeSuccessors {
        var lastLoaded: KnowledgeNodeSuccessors? = null
        repeat(MAX_LOAD_ATTEMPTS) {
            val startedAt = synchronized(stateLock) { generation }
            val loaded = loadSuccessors()
            val installed = synchronized(stateLock) {
                if (generation == startedAt && cached == null) {
                    cached = loaded
                    true
                } else {
                    false
                }
            }
            if (installed) return loaded
            lastLoaded = loaded
        }
        return checkNotNull(lastLoaded)
    }

    private companion object {
        /** 加载-失效交错的有限重试；内容调和的节点写入本就稀少，超过它按"不缓存"处理。 */
        const val MAX_LOAD_ATTEMPTS = 4
    }
}
