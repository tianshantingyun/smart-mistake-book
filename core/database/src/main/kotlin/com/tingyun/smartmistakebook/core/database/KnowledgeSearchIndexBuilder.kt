package com.tingyun.smartmistakebook.core.database

import android.util.Log
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchFeatureEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchIndexStateEntity
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/**
 * 搜索特征索引的整科构建入口（S21，2026-10-02）：把「整科重建 + 最后写版本锚点」从
 * `RoomKnowledgeBaseStore` 的读时自愈里抽出来，供两个调用方共用——
 *
 * ① **内容安装**（`RoomKnowledgeContentReconciler`）：安装期就把索引建好。此前锚点只在
 * 首访问的 `ensureKnowledgeSearchIndex` 里写，于是首查要现场做一次整科重建——审计与桌面
 * 复现都把「首访问整科重建」列为召回路径秒级长尾（16.6s max）的最可疑来源；
 * ② 读时自愈兜底（抽取规则 `KnowledgeSearchFeatureExtractor.INDEX_VERSION` 变更、
 * 锚点缺失/不等时仍要整科换血）。
 *
 * **KD-51（2026-10-17）：重建从「一条长写事务」改成「读相 + 分块短写事务」。**
 * 改前的形态是 `COUNT + 整科 DELETE + 一次性 INSERT 全科特征行` 全在一个写事务里；
 * 全库规模（设备走查台账：`knowledge_search_feature` 623,774 行 / `knowledge_node`
 * 4,187 节点）下它把 room3 的**唯一写连接**（4 读 1 写）占住整段重建——同进程别的写
 * （采集提交、投影推送）只能排队，设备实测排到 270s（冷启动安装窗口里连接池被饿死）。
 * 改后每次只持有**一块**：
 * - 读相（计数 + 候选集 + 已索引 id 集）走**读**连接，不进写事务；
 * - 删除与插入各按 [REBUILD_NODE_CHUNK_SIZE] 个节点一块，一块一个短写事务
 *   （每块 ≤ 192×64 = 12,288 行特征，192 = 抽取器的单节点特征上限）；
 * - 块间 `yield()`/`ensureActive()` 让出调度器并接受取消（见 [chunkBoundary]）。
 *
 * **分块只改事务边界，不改结果**（三条不变量）：
 * 1. **输出行集逐位不变**：候选集仍是 `countReviewedKnowledgeNodesBySubject` 限定的
 *    `readSubjectKnowledgeRecallCandidates`（两者同一读快照，保持旧实现"计数与读取一致"
 *    的口径），插入的仍是 `nodes.flatMap { it.toSearchFeatures() }`——同样的抽取、同样的行；
 *    删除按 [ProblemOrganizationDao.readIndexedKnowledgeNodeIdsForSubject] 分块，删掉的行集
 *    与旧 `DELETE ... WHERE subject = ?` 相同（id 集在同一读快照内覆盖该科全部特征行）。
 * 2. **锚点最后写**（`knowledge_search_index_state`）：**锚点当前 ⟺ 这次重建完整跑完**。
 *    所有块都提交之后，锚点才在自己的短事务里推进。
 * 3. **跳过判据与读路径自愈不动**：contentVersion 快路径、INDEX_VERSION 锚点、内存完整性
 *    缓存三道门，以及 `RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex` 的形态一字未改。
 *
 * **崩溃/取消的中间态**（分块引入，如实登记）：取消会带走已提交的块、不回滚，可能留下
 * ① 锚点没推进（本就不当前 ⇒ 下次读时自愈整科重跑，与改前一致）；或 ② 锚点**本已当前**
 * （安装期的"换血型"重建），索引却只删了一半/插了一半——这时读路径的「已索引数 ≥ 已审校数」
 * 门会判不完整，走只补缺把缺的行补回来。**没有任何中间态会被当成"完整"**：锚点写在所有块
 * 之后，且它自己是最后一块。
 *
 * **分块带来的已知代价**（不藏）：块与块之间别的写可以插进来，于是"读相快照"与"提交结果"
 * 之间可能有并发内容变更——极端时点下，一个本轮被退役、随后又被别的路径清掉特征行的节点，
 * 可能被我们的插入块按快照重新插进特征表（对召回不可见：召回查询排 `RETIRED`；
 * 两个计数的 `已索引 ≥ 已审校` 口径下多算无害；下一次整科重建会清掉）。生产上只有本
 * 构建器与读时自愈会写这张表，而安装器自身串行（`BundledKnowledgeBaseInstaller.installMutex`）、
 * 退役在重建之前完成，所以这条窗口只在"安装与用户确认并发"这类复合情形下存在。
 * 另：改前"取消 = 整个事务回滚、索引保持旧样子"，改后"取消 = 部分索引 + 锚点不推进"，
 * 多付一次重跑（收敛，可见内容不受影响）。
 *
 * **观测**（KD-51 证据）：每科重建结束打一行 [TAG] 日志（节点数/特征行数/块数/耗时），
 * 与 `KnowledgeInstall` 的 `packs fully parsed in … ms`、`install finished in … ms` 一起
 * 把「全量安装 1,133,477ms」拆成 **JSON 解析 / 整科重建 / 其余调和** 三段可复跑读数。
 */
internal class KnowledgeSearchIndexBuilder(
    private val database: StudyDatabase,
    /**
     * ④-6（K1 批 1）：整科重建必须让「已核验完整」判定失效——它是索引状态的全量重写，
     * 旧判定无论指向哪个版本的库状态都不再可信（同版本下的安装期重建也会在此换血）。
     */
    private val searchIndexCompleteness: KnowledgeSearchIndexCompleteness =
        KnowledgeSearchIndexCompleteness(),
    /**
     * 测试缝（默认空实现 = 生产不观察）：每提交一个写块后回调一次，块种类见
     * [KnowledgeSearchIndexRebuildWrite]；回调抛出的异常（含 `CancellationException`）
     * 从 [rebuildSubject] 原样传出——"块间取消"的用例靠它注入。
     *
     * 消灭的失败：分块的**终点状态与不分块逐位相同**（分块只改事务边界，这正是本次修复的
     * 目标），`knowledge_search_index_state` / `knowledge_search_feature` 里没有任何可观察量
     * 能区分"一个大事务"与"n 个小事务"，而"被切成 >1 个写事务"恰是本轨唯一的验收口径。
     * room3 3.0.0 没有 `setQueryCallback`（核实见
     * [KnowledgeSearchIndexCompleteness.issuedVerificationQueries] 的 KDoc），故按同一先例
     * 用回调缝。生产侧只传默认值、从不读。
     */
    private val onWriteCommitted: (KnowledgeSearchIndexRebuildWrite) -> Unit = {},
) {

    /**
     * 整科重建 [subject] 的搜索特征。`reviewedCount == 0` 时只写锚点（"这一科没有可召回
     * 节点"也是一个已经算过的结论，避免以后每次读都重试）。
     *
     * ④-6：**进入与退出（含失败）都失效判定缓存**。进入时失效防止重建事务提交前有人拿旧
     * 判定跳过自愈；退出时失效防住"重建过程中并发验证读了重建前的完整旧索引并落了判定、
     * 随后本事务失败回滚"的假完整——回滚不改库，那个判定却对应不上"节点已写入但无特征行"
     * 的中间态，只有作废才会在下次召回重验。（KD-51 分块后这条**更要紧**：块与块之间缓存
     * 不得把中间态当完整；失效次数不变，仍为进入/退出各一次。）
     */
    suspend fun rebuildSubject(subject: String) {
        searchIndexCompleteness.invalidate(subject)
        try {
            val dao = database.problemOrganizationDao()
            val stateDao = database.knowledgeSearchIndexStateDao()
            val startedAtNano = System.nanoTime()

            // ① 读相（一个**读**事务，不占写连接）：审校计数、召回候选集、已索引节点 id 集。
            //    三者同一快照——候选集的 LIMIT 就是计数（改前靠写事务隔离，分块后没有那层
            //    隔离，靠读事务保住"计数与读取一致"的口径）。
            val readPhase = database.withReadTransaction {
                val reviewedCount = dao.countReviewedKnowledgeNodesBySubject(subject)
                if (reviewedCount == 0) {
                    RebuildReadPhase(candidates = emptyList(), indexedNodeIds = emptyList())
                } else {
                    RebuildReadPhase(
                        candidates = dao.readSubjectKnowledgeRecallCandidates(subject, reviewedCount),
                        indexedNodeIds = dao.readIndexedKnowledgeNodeIdsForSubject(subject),
                    )
                }
            }

            // ② 删相：整科换血（旧特征行必须清掉，否则改名节点的旧特征会留在库里），
            //    但按节点 id 分块，一块一个短写事务。
            var deleteChunks = 0
            for (batch in readPhase.indexedNodeIds.chunked(REBUILD_NODE_CHUNK_SIZE)) {
                database.withWriteTransaction { dao.deleteSearchFeaturesForNodes(batch.toSet()) }
                deleteChunks++
                onWriteCommitted(KnowledgeSearchIndexRebuildWrite.DELETE)
                chunkBoundary()
            }

            // ③ 插相：抽取（CPU 主体）在事务外算，事务里只留这一块的特征行。
            var featureCount = 0
            var insertChunks = 0
            for (batch in readPhase.candidates.chunked(REBUILD_NODE_CHUNK_SIZE)) {
                val features = batch.flatMap(KnowledgeNodeEntity::toSearchFeatures)
                database.withWriteTransaction { dao.insertKnowledgeSearchFeatures(features) }
                featureCount += features.size
                insertChunks++
                onWriteCommitted(KnowledgeSearchIndexRebuildWrite.INSERT)
                chunkBoundary()
            }

            // ④ 锚点最后写（自己的短事务）：所有块都提交之后才推进"这次跑完了"。
            database.withWriteTransaction {
                stateDao.replace(
                    KnowledgeSearchIndexStateEntity(
                        subject,
                        KnowledgeSearchFeatureExtractor.INDEX_VERSION,
                    ),
                )
            }
            onWriteCommitted(KnowledgeSearchIndexRebuildWrite.ANCHOR)

            Log.d(
                TAG,
                "rebuild subject=$subject nodes=${readPhase.candidates.size} features=$featureCount " +
                    "deleteChunks=$deleteChunks insertChunks=$insertChunks " +
                    "tookMs=${(System.nanoTime() - startedAtNano) / 1_000_000}",
            )
        } finally {
            searchIndexCompleteness.invalidate(subject)
        }
    }

    /**
     * 块间可中断点：取消在这里抛出，**已提交的块留在库里（不会回滚）**、后面的块不再写，
     * 锚点也就不会被推进——于是"取消 = 整科重跑"（见类 KDoc 的中间态说明）。
     * `ensureActive()` 先判一次，`yield()` 再让出调度器；两者都会在取消时抛出。
     */
    private suspend fun chunkBoundary() {
        coroutineContext.ensureActive()
        yield()
    }
}

/**
 * 整科重建提交的一个**写块**（测试缝用，见 [KnowledgeSearchIndexBuilder] 的 `onWriteCommitted`）。
 *
 * - [DELETE]：删掉一块已索引节点的特征行；
 * - [INSERT]：写入一块节点的特征行；
 * - [ANCHOR]：版本锚点——**最后**提交，且只在所有块都提交之后（"锚点当前 ⟺ 跑完了"）。
 */
internal enum class KnowledgeSearchIndexRebuildWrite { DELETE, INSERT, ANCHOR }

/** 重建读相的产物（同一读事务快照内读出；见 [KnowledgeSearchIndexBuilder.rebuildSubject]）。 */
private data class RebuildReadPhase(
    val candidates: List<KnowledgeNodeEntity>,
    val indexedNodeIds: List<String>,
)

/**
 * 整科重建的分块上限（节点数）：删除与插入都按它切块，一块一个**短**写事务。
 *
 * 为什么不是"一条语句删完 + 一次插入写完"（改前形态）：room3 只有 4 读 1 写，长写事务把
 * 唯一写连接占住整段重建（KD-51 设备实测：同进程其它写排到 270s）。64 个节点最多
 * 192×64 = 12,288 行特征（192 = 抽取器的单节点特征上限），一次提交在设备上是几十毫秒量级。
 * 与 SQLite 绑定变量上限无关：插入逐行绑定，删除只用 64 个 id（`IN` 列表远低于 999）。
 */
private const val REBUILD_NODE_CHUNK_SIZE = 64

/**
 * 重建耗时的观测标签（KD-51 证据）：与 `KnowledgeInstall` 标签的解析/安装两行日志合成
 * "JSON 解析 vs 整科重建 vs 其余调和"的耗时分解，供设备轨（T8）与桌面复现直接读数。
 */
private const val TAG = "KnowledgeIndexRebuild"

/**
 * 节点 → 搜索特征行。S21 与构建器同置一处：改前这三个私有扩展只服务
 * `RoomKnowledgeBaseStore` 的重建分支；现在安装期与自愈共用，**只此一份**。
 */
internal fun KnowledgeNodeSeedRecord.toSearchFeatures(): List<KnowledgeSearchFeatureEntity> =
    KnowledgeSearchFeatureExtractor.fromNode(this).map { feature ->
        KnowledgeSearchFeatureEntity(
            subject = subject,
            searchFeature = feature,
            knowledgeNodeId = knowledgeNodeId,
        )
    }

internal fun KnowledgeNodeEntity.toSearchFeatures(): List<KnowledgeSearchFeatureEntity> =
    toSeedRecord().toSearchFeatures()

internal fun KnowledgeNodeEntity.toSeedRecord() = KnowledgeNodeSeedRecord(
    knowledgeNodeId = knowledgeNodeId,
    stableCode = stableCode,
    subject = subject,
    displayName = displayName,
    parentKnowledgeNodeId = parentKnowledgeNodeId,
    taxonomyVersion = taxonomyVersion,
    createdAtEpochMillis = createdAtEpochMillis,
    canonicalName = canonicalName,
    nodeKind = nodeKind,
    granularity = granularity,
    aliases = aliasesText.split("\u001F").filter(String::isNotBlank).toSet(),
    boundaryMarkdown = boundaryMarkdown,
    verificationStatus = verificationStatus,
)
