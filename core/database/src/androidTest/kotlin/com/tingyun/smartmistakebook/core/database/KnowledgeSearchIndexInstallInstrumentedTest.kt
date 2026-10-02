package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceType
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S21（2026-10-02）：**内容安装期**预热搜索特征索引。
 *
 * 消灭的失败：锚点此前只在首访问的读时自愈里写，首查要现场做一次整科重建（3.5 万节点
 * 量级的提取 + 写入），正是召回长尾（审计 max 16.6s）最可疑的来源。现在安装完成即建好
 * 索引并写好锚点——本用例在**任何召回调用之前**断言这一点（用文件库直读内部表，
 * 与 `KnowledgeContentReconciliationInstrumentedTest` 同法）。
 *
 * 第二条用例钉住"节点改名（upsert）→ 特征随安装重建、锚点保持当前"，防止安装期预热
 * 只覆盖首装、不覆盖后续内容更新。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeSearchIndexInstallInstrumentedTest {

    private fun open(name: String): Pair<StudyDatabasePort, String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "kb-index-install-$name-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        return StudyDatabaseFactory.open(context, databaseName) to databaseName
    }

    private fun readRaw(databaseName: String, sql: String): List<Map<String, String?>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery(sql, null).use { cursor ->
                val rows = mutableListOf<Map<String, String?>>()
                while (cursor.moveToNext()) {
                    val row = mutableMapOf<String, String?>()
                    for (i in 0 until cursor.columnCount) {
                        row[cursor.getColumnName(i)] = cursor.getString(i)
                    }
                    rows += row
                }
                return rows
            }
        }
    }

    private fun singleInt(databaseName: String, sql: String): Int =
        readRaw(databaseName, sql).single()["c"]!!.toInt()

    @Test
    fun contentInstallBuildsTheSearchIndexBeforeAnyRecall() = runBlocking {
        val (store, name) = open("first-install")
        try {
            store.applyKnowledgeContentUpdate(command())

            // 从未调用任何召回——锚点与特征必须已经就位（安装期预热）。
            assertEquals(
                "安装完成即写锚点，首查不再触发整科重建",
                KnowledgeSearchFeatureExtractor.INDEX_VERSION,
                readRaw(name, "SELECT index_version FROM knowledge_search_index_state WHERE subject = 'MATH'")
                    .single()["index_version"]!!.toInt(),
            )
            assertEquals(
                "两个已审校节点（TOPIC + ATOMIC）都要有特征行",
                2,
                singleInt(
                    name,
                    "SELECT COUNT(DISTINCT knowledge_node_id) AS c FROM knowledge_search_feature WHERE subject = 'MATH'",
                ),
            )
            assertTrue(
                "特征行不能是空壳（每个节点至少一条特征）",
                singleInt(name, "SELECT COUNT(*) AS c FROM knowledge_search_feature WHERE subject = 'MATH'") > 0,
            )
        } finally {
            store.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
        }
    }

    @Test
    fun renamingANodeRebuildsFeaturesAtInstallAndKeepsTheAnchorCurrent() = runBlocking {
        val (store, name) = open("rename-rebuild")
        try {
            store.applyKnowledgeContentUpdate(command())
            store.applyKnowledgeContentUpdate(command(atomicName = "判断函数的单调性（改）"))

            assertEquals(
                "后续内容更新同样在安装期重建，锚点保持当前",
                KnowledgeSearchFeatureExtractor.INDEX_VERSION,
                readRaw(name, "SELECT index_version FROM knowledge_search_index_state WHERE subject = 'MATH'")
                    .single()["index_version"]!!.toInt(),
            )
            assertEquals(
                2,
                singleInt(
                    name,
                    "SELECT COUNT(DISTINCT knowledge_node_id) AS c FROM knowledge_search_feature WHERE subject = 'MATH'",
                ),
            )
        } finally {
            store.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
        }
    }

    // ---- 夹具（与 KnowledgeContentReconciliationInstrumentedTest 同形的最小合法包）----

    private fun command(atomicName: String = "判断函数单调性"): KnowledgeContentUpdateCommand {
        val nodes = listOf(topic(), atomic(atomicName))
        return KnowledgeContentUpdateCommand(
            packId = TAXONOMY_VERSION,
            contentVersion = "test-version",
            nodes = nodes,
            sources = listOf(source()),
            nodeSourceBindings = nodes.map { binding(it.knowledgeNodeId) },
            relations = emptyList(),
            materials = listOf(material()),
            materialBindings = listOf(
                KnowledgeTeachingMaterialNodeBindingRecord(
                    materialId = MATERIAL_ID,
                    knowledgeNodeId = ATOMIC_ID,
                    role = "PRIMARY",
                ),
            ),
            nodeRetirements = emptyMap(),
            teachingSources = listOf(source()),
        )
    }

    private fun source() = KnowledgeSourceSeedRecord(
        sourceId = SOURCE_ID,
        subject = "MATH",
        sourceType = KnowledgeSourceType.MANUAL_RESEARCH.name,
        title = "经审校的函数知识目录",
        publisher = "授权教研机构",
        edition = null,
        sourceUri = "https://example.edu/math/function-index",
        licenseStatus = KnowledgeSourceLicenseStatus.REFERENCE_ONLY.name,
        contentFingerprint = "B".repeat(64),
        importedAtEpochMillis = 1_000,
    )

    private fun topic() = KnowledgeNodeSeedRecord(
        knowledgeNodeId = TOPIC_ID,
        stableCode = "$TAXONOMY_VERSION:math:topic:function",
        subject = "MATH",
        displayName = "函数",
        parentKnowledgeNodeId = null,
        taxonomyVersion = TAXONOMY_VERSION,
        createdAtEpochMillis = 1_000,
        canonicalName = "函数",
        nodeKind = KnowledgeNodeKind.TOPIC.name,
        granularity = KnowledgeNodeGranularity.TOPIC.name,
        verificationStatus = KnowledgeNodeVerificationStatus.CURATED.name,
    )

    private fun atomic(name: String) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = ATOMIC_ID,
        stableCode = "$TAXONOMY_VERSION:math:atomic:monotonicity",
        subject = "MATH",
        displayName = name,
        parentKnowledgeNodeId = TOPIC_ID,
        taxonomyVersion = TAXONOMY_VERSION,
        createdAtEpochMillis = 1_000,
        canonicalName = name,
        nodeKind = KnowledgeNodeKind.REASONING.name,
        granularity = KnowledgeNodeGranularity.ATOMIC.name,
        boundaryMarkdown = "只判断给定函数在指定区间上的单调性。",
        verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
    )

    private fun material() = KnowledgeTeachingMaterialRecord(
        materialId = MATERIAL_ID,
        stableCode = "$TAXONOMY_VERSION:math:teaching:$MATERIAL_ID",
        subject = "MATH",
        materialType = KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
        title = "单调性的判断",
        summaryMarkdown = "先看定义域，再比较区间内任意两点的函数值。",
        applicabilityMarkdown = "判断给定区间上单调性的题。",
        contentMarkdown = "1. 取定义域内的任意两点。\n2. 比较函数值大小。\n3. 得出单调性。",
        boundaryMarkdown = "只在给定区间上讨论，端点开闭需单独说明。",
        derivationKind = "REVIEWED_SYNTHESIS",
        sourceId = SOURCE_ID,
        sourceLocator = "函数目录·单调性",
        contentFingerprint = "PENDING",
        reviewedAtEpochMillis = 1_000,
    ).let { it.copy(contentFingerprint = KnowledgeTeachingMaterialFingerprint.expected(it)) }

    private fun binding(nodeId: String) = KnowledgeNodeSourceBindingSeedRecord(
        knowledgeNodeId = nodeId,
        sourceId = SOURCE_ID,
        sourceLocator = "函数目录·单调性",
        derivationNote = "人工核对原始材料后拆分。",
        reviewedAtEpochMillis = 1_000,
    )

    private companion object {
        const val TAXONOMY_VERSION = "moe-2025-four-subjects-v1"
        const val SOURCE_ID = "kb-source:math:function-index"
        const val TOPIC_ID = "kb-node:math:function"
        const val ATOMIC_ID = "kb-node:math:monotonicity"
        const val MATERIAL_ID = "kb-material:monotonicity"
    }
}
