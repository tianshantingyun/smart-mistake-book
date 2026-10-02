package com.tingyun.smartmistakebook.core.database

import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchFeatureEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchIndexStateEntity

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
 * 崩溃安全与旧实现逐条一致：**锚点在同一写事务内最后写**——「锚点当前」⟺「这次重建完整
 * 跑完」；崩在中途则锚点没推进，下次重跑收敛。
 */
internal class KnowledgeSearchIndexBuilder(private val database: StudyDatabase) {

    /**
     * 整科重建 [subject] 的搜索特征。`reviewedCount == 0` 时只写锚点（"这一科没有可召回
     * 节点"也是一个已经算过的结论，避免以后每次读都重试）。
     */
    suspend fun rebuildSubject(subject: String) {
        val dao = database.problemOrganizationDao()
        val stateDao = database.knowledgeSearchIndexStateDao()
        database.withWriteTransaction {
            val reviewedCount = dao.countReviewedKnowledgeNodesBySubject(subject)
            if (reviewedCount > 0) {
                dao.deleteSearchFeaturesForSubject(subject)
                val nodes = dao.readSubjectKnowledgeRecallCandidates(subject, reviewedCount)
                dao.insertKnowledgeSearchFeatures(nodes.flatMap(KnowledgeNodeEntity::toSearchFeatures))
            }
            stateDao.replace(
                KnowledgeSearchIndexStateEntity(subject, KnowledgeSearchFeatureExtractor.INDEX_VERSION),
            )
        }
    }
}

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
