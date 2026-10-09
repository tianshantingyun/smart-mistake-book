package com.tingyun.smartmistakebook.core.database

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ④-6（K1 批 1）：**「(subject, INDEX_VERSION) → 已核验完整」判定缓存**。
 *
 * 消灭的失败：`ensureKnowledgeSearchIndex` 在锚点相等时**每次召回**仍跑两条 COUNT
 * （`countReviewedKnowledgeNodesBySubject` + `countIndexedKnowledgeNodesBySubject`，
 * S19 桌面探针 ≈12.6ms/次、设备更高）——每次检索/讲题都白付的固定成本。缓存命中时
 * 召回路径**零 DAO 调用**（连版本锚点都不读）直接返回。
 *
 * 判定口径与自愈门逐条一致：`已审校 = 0 或 已索引 ≥ 已审校` 才算「已核验完整」；
 * 只有**跑完两条 COUNT 且结论为完整**的那次验证可以落缓存。
 *
 * 正确性靠**写侧显式失效**（[invalidate]），三处（+ 整理确认第四处，见下）：
 * ① 内容安装落点（`RoomKnowledgeContentReconciler.applyKnowledgeContentUpdate`）；
 * ② 整科重建（`KnowledgeSearchIndexBuilder.rebuildSubject`，进入与退出都失效）；
 * ③ 只补缺分支补完后的落点（`RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex`）；
 * ④ **整理确认**（`RoomProblemOrganizationStore.confirm`）：用户纠正落 USER_CONFIRMED
 *    节点时**不建特征行**，靠读时自愈补——少了这条失效，缓存命中会让新知识点永远
 *    不进召回索引（同进程"确认后立刻召回"用例钉住，先红后绿）。
 * 漏掉任何一处，召回就会漏掉新装节点——这是本机制唯一的正确性风险面。
 * **另有两条会写 `knowledge_search_feature` 的路径（`ProblemOrganizationDao.importKnowledgeBase`、
 * `KnowledgeGroundingDao.applyReviewedPack`）不需要失效**：它们把节点与其特征行在**同一事务**里
 * 一起写入（`insertKnowledgeNodes` + `insertKnowledgeSearchFeatures`），「已索引 ≥ 已审校」
 * 的不变量在写后仍成立；两者当前也没有 `src/main` 生产调用方（只有 DB 模块自身的定义/委托）。
 *
 * 并发防护（[markVerifiedCompleteIfUnchanged]）：验证结论必须配以**失效代号**观察——
 * 计数读完与落缓存之间若有写路径失效（安装/重建/确认），判定丢弃、下次重验。没有这道
 * 防护时，「重建入口失效 → 并发召回读出重建前的完整旧索引 → 落缓存 → 重建失败回滚」
 * 会留下假"完整"，新节点永久漏召回。
 *
 * 作用域：**每个数据库实例一份**（`RoomStudyDatabase` 持有）。生产装配一个进程一个库，
 * 所以等于"进程内每科一次"；按实例隔离而非全局静态，避免同进程多库（备份校验库、
 * 测试库）互相同意彼此的判定。
 *
 * 仅失效不回填：写路径只调用 [invalidate]；回填只发生在读路径跑完两条 COUNT 之后。
 * 因此"缓存说完整"永远意味着"上一次完整验证之后没有任何已知写写入"。
 */
internal class KnowledgeSearchIndexCompleteness(
    /**
     * 测试缝（仅同模块测试用）：置 false 时 [invalidate] 变成空操作——负向用例用它
     * 模拟"失效接线被摘掉"，证明用例真的能变红（见 `KnowledgeSearchIndexCompletenessInstrumentedTest`）。
     * 生产恒为 true。
     */
    private val invalidationEnabled: Boolean = true,
) {
    private val lock = Any()
    private val verifiedVersionBySubject = ConcurrentHashMap<String, Int>()
    private val invalidationGenerationBySubject = ConcurrentHashMap<String, Long>()

    /**
     * 测试缝（仅同模块测试读）：判定路径**计划下发**的标签集合（标签 = DAO 方法名，
     * SQL 原文见 `ProblemOrganizationDao` / `KnowledgeSearchIndexStateDao` 的 `@Query`）。
     *
     * **记录的是调用点准备下发的标签，不是真实 SQL 执行计数**——room3 3.0.0 没有
     * `setQueryCallback`（`javap androidx.room3.RoomDatabase$Builder` 只有
     * `setQueryCoroutineContext`），无法从 Room 侧观察真实执行；独立复核见 K1 完成记录
     * `docs/research/2026-10-08-k1-completion-record.md` §11#8。本缝能证明的是"判定路径
     * 走到了/没走到下发这些标签的代码位置"（调用点紧邻 DAO 调用，见
     * `RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex`）。计数断言用它——不依赖计时，
     * 也不依赖 Room 侧不存在的钩子。
     *
     * 生产只写不读；追加以"每科每次写后一次验证"为界，另有 [MAX_ISSUED_QUERY_RECORDS]
     * 封顶（达到上限即停止追加）——上限只为消灭"生产只写不读、长进程累积"这一无界增长面。
     */
    internal val issuedVerificationQueries = CopyOnWriteArrayList<String>()

    fun isVerifiedComplete(subject: String): Boolean =
        verifiedVersionBySubject[subject] == KnowledgeSearchFeatureExtractor.INDEX_VERSION

    fun readInvalidationGeneration(subject: String): Long =
        invalidationGenerationBySubject[subject] ?: 0L

    /**
     * 落「已核验完整」，但仅当 [observedGeneration] 仍是当前失效代号——验证期间发生过失效
     * （写路径）时丢弃结论。调用方必须在**读计数之前**取 [readInvalidationGeneration]。
     */
    fun markVerifiedCompleteIfUnchanged(subject: String, observedGeneration: Long) {
        synchronized(lock) {
            if ((invalidationGenerationBySubject[subject] ?: 0L) != observedGeneration) return
            verifiedVersionBySubject[subject] = KnowledgeSearchFeatureExtractor.INDEX_VERSION
        }
    }

    /** 写路径显式失效：清掉判定并推进失效代号（挡住在途验证的落缓存）。 */
    fun invalidate(subject: String) {
        synchronized(lock) {
            if (!invalidationEnabled) return
            invalidationGenerationBySubject.merge(subject, 1L, Long::plus)
            verifiedVersionBySubject.remove(subject)
        }
    }

    internal fun recordVerificationQuery(daoMethod: String) {
        synchronized(lock) {
            if (issuedVerificationQueries.size < MAX_ISSUED_QUERY_RECORDS) {
                issuedVerificationQueries += daoMethod
            }
        }
    }

    companion object {
        /**
         * [issuedVerificationQueries] 的硬上限：达到即**停止追加**（不整体清空）。
         *
         * 选"停止追加"而非"清空"的理由：① 截断保留前缀，"判定路径下发过的标签序列前缀"
         * 语义仍成立——任何真实用例的断言窗口都远小于该值；② 清空会把已有证据一起抹掉，
         * 让"越界后读到的空列表"与"根本没有下发"不可区分，对计数断言是更坏的失败形态。
         * 1024 ≈ 三百多次完整验证（每次最多 3 条标签），远高于任何用例需要；上限只为
         * "生产只写不读、长进程累积"这一无界增长面兜底。
         */
        const val MAX_ISSUED_QUERY_RECORDS = 1024
    }
}

/** 判定路径 SQL 的标签（= DAO 方法名；只用于测试断言，不参与生产逻辑）。 */
internal object SearchIndexVerificationQueries {
    /** `KnowledgeSearchIndexStateDao.readVersion`——缓存命中时连它都不发。 */
    const val READ_INDEX_VERSION = "readVersion(knowledge_search_index_state)"

    /** `ProblemOrganizationDao.countReviewedKnowledgeNodesBySubject`。 */
    const val COUNT_REVIEWED = "countReviewedKnowledgeNodesBySubject"

    /** `ProblemOrganizationDao.countIndexedKnowledgeNodesBySubject`。 */
    const val COUNT_INDEXED = "countIndexedKnowledgeNodesBySubject"
}
