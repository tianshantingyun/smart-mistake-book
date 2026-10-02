package com.tingyun.smartmistakebook.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 插眼 8（裁决 22 修订二）的设备级验证：本科「未分类」伪节点由既有 ensure 路径幂等创建，
 * 且**不进召回面**（MODEL_CANDIDATE 被召回过滤挡住）。
 *
 * 排除性断言要有对照：同文本的 CURATED 节点能被召回，而伪节点不能——这样"没召回"证明的是
 * 过滤（verification_status），不是"查询词没命中"。
 */
@RunWith(AndroidJUnit4::class)
class PseudoUnclassifiedBucketInstrumentedTest {

    @Test
    fun theBucketNodeIsEnsuredIdempotentlyAndStaysOutOfTheRecallSurface() = runBlocking {
        val store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext())
        try {
            val first = store.ensurePseudoKnowledgeNode(subject = "MATH", atEpochMillis = 1_000)
            assertNotNull(first)
            first!!
            assertEquals("pseudo:MATH", first.knowledgeNodeId)
            assertEquals("未归类知识点", first.displayName)
            assertEquals("MODEL_CANDIDATE", first.verificationStatus)

            // 幂等：重复 ensure 返回同一条（创建时刻不刷新——它不是"最近更新"）。
            val replay = store.ensurePseudoKnowledgeNode(subject = "MATH", atEpochMillis = 2_000)
            assertNotNull(replay)
            assertEquals(first.createdAtEpochMillis, replay!!.createdAtEpochMillis)

            // 对照节点：同文本、CURATED（可召回）。它的存在让下面的"伪节点不在召回集里"
            // 证明的是过滤而不是零命中。
            store.importKnowledgeBase(
                sources = listOf(source()),
                nodes = listOf(curatedTwin()),
                bindings = listOf(binding(TWIN_ID)),
            )

            val recalled = store.readSubjectKnowledgeRecallCandidates(
                subject = "MATH",
                searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion("未归类知识点"),
                limit = 10,
            )
            assertTrue(
                "同文本的 CURATED 节点必须被召回（证明查询词命中）：$recalled",
                recalled.any { node -> node.knowledgeNodeId == TWIN_ID },
            )
            assertFalse(
                "未分类桶是 MODEL_CANDIDATE，不得进召回面：$recalled",
                recalled.any { node -> node.knowledgeNodeId == BUCKET_ID },
            )

            // 桶只作证据兜底落点：按 id 读回仍存在（历史解释路径不过滤）。
            val bucket = store.readKnowledgeNodesByIds(setOf(BUCKET_ID)).single()
            assertEquals("未归类知识点", bucket.displayName)
        } finally {
            store.close()
        }
    }

    private fun source() = KnowledgeSourceSeedRecord(
        sourceId = SOURCE_ID,
        subject = "MATH",
        sourceType = KnowledgeSourceType.MANUAL_RESEARCH.name,
        title = "经审校的函数知识目录",
        publisher = "授权教研机构",
        edition = null,
        sourceUri = "https://example.edu/math/bucket-twin",
        licenseStatus = KnowledgeSourceLicenseStatus.REFERENCE_ONLY.name,
        contentFingerprint = "C".repeat(64),
        importedAtEpochMillis = 1_000,
    )

    private fun curatedTwin() = KnowledgeNodeSeedRecord(
        knowledgeNodeId = TWIN_ID,
        stableCode = "research-v1:math:topic:bucket-twin",
        subject = "MATH",
        displayName = "未归类知识点",
        parentKnowledgeNodeId = null,
        taxonomyVersion = "research-v1",
        createdAtEpochMillis = 1_000,
        canonicalName = "未归类知识点",
        nodeKind = KnowledgeNodeKind.TOPIC.name,
        granularity = KnowledgeNodeGranularity.TOPIC.name,
        verificationStatus = KnowledgeNodeVerificationStatus.CURATED.name,
    )

    private fun binding(nodeId: String) = KnowledgeNodeSourceBindingSeedRecord(
        knowledgeNodeId = nodeId,
        sourceId = SOURCE_ID,
        sourceLocator = "目录·未归类对照",
        derivationNote = "人工核对原始材料后拆分。",
        reviewedAtEpochMillis = 1_000,
    )

    private companion object {
        const val SOURCE_ID = "source:research:bucket-twin"
        const val TWIN_ID = "kb:research-v1:math:topic:bucket-twin"
        const val BUCKET_ID = "pseudo:MATH"
    }
}
