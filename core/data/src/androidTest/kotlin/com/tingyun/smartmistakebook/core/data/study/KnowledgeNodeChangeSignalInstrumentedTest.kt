package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.KnowledgeContentUpdateCommand
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSourceBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSourceSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S20：**真 Room** 上的 `knowledge_node` 表级失效信号（`observeKnowledgeNodeChanges`）——
 * 后继映射缓存的失效依据必须在真实数据库上成立，而不是只在假库里成立。
 *
 * 钉住两件事：
 * 1. 订阅即得一次结果（探针在空表上也要能发射）；
 * 2. 两条经 Room 的写路径——节点导入与**合并退役**（唯一改 `superseded_by` 的生产写路径，
 *    见 `KnowledgeNodeSuccessors` 的 KDoc）——各让信号再发射一次；退役后
 *    `readKnowledgeNodeSuccessors()` 如实给出取代边（缓存据此重读的内容）。
 *
 * 已知边界（有意不钉）：旁路直写 SQLite（不经 Room）不会触发 Room 的表级失效——那不是生产
 * 写路径；`BindingChangedReplayDrillInstrumentedTest` 里用原始 SQL 模拟合并，随后仍会经 Room
 * 写节点，覆盖不受影响。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeNodeChangeSignalInstrumentedTest {

    @Test
    fun roomKnowledgeNodeWritesEmitTheInvalidationSignalAndAMergeRetireIsReadable() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "s20-node-signal-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
            val emissions = AtomicInteger()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val collector = scope.launch {
                database.observeKnowledgeNodeChanges().collect { emissions.incrementAndGet() }
            }
            try {
                awaitEmissions(emissions, atLeast = 1)

                // 1) 节点导入：经 Room DAO 的 knowledge_node 写入。
                database.applyKnowledgeContentUpdate(command(includeAtomic = true))
                awaitEmissions(emissions, atLeast = 2)

                // 2) 合并退役：atomic 退役并指向 topic（生产唯一改 superseded_by 的写路径）。
                database.applyKnowledgeContentUpdate(
                    command(includeAtomic = false, supersededBy = TOPIC_ID),
                )
                awaitEmissions(emissions, atLeast = 3)

                assertEquals(
                    "退役后取代边必须可读（缓存重读的内容）",
                    mapOf(ATOMIC_ID to TOPIC_ID),
                    database.readKnowledgeNodeSuccessors(),
                )
                assertTrue(
                    "两次 Room 写入都必须让信号再次发射（实际 ${emissions.get()} 次）",
                    emissions.get() >= 3,
                )
            } finally {
                collector.cancel()
                scope.cancel()
                database.close()
                context.deleteDatabase(databaseName)
            }
        }

    private suspend fun awaitEmissions(emissions: AtomicInteger, atLeast: Int) {
        withTimeout(10_000) {
            while (emissions.get() < atLeast) delay(20)
        }
    }

    // ---- 夹具（与 KnowledgeContentReconciliationInstrumentedTest 同形的最小集）----

    private fun command(
        includeAtomic: Boolean,
        supersededBy: String? = null,
    ): KnowledgeContentUpdateCommand {
        val nodes = buildList {
            add(topic())
            if (includeAtomic) add(atomic())
        }
        return KnowledgeContentUpdateCommand(
            packId = TAXONOMY_VERSION,
            contentVersion = "signal-test",
            nodes = nodes,
            sources = listOf(source()),
            nodeSourceBindings = nodes.map { binding(it.knowledgeNodeId) },
            relations = emptyList(),
            materials = emptyList(),
            materialBindings = emptyList(),
            nodeRetirements = if (includeAtomic) emptyMap() else mapOf(ATOMIC_ID to supersededBy),
            teachingSources = emptyList(),
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

    private fun atomic() = KnowledgeNodeSeedRecord(
        knowledgeNodeId = ATOMIC_ID,
        stableCode = "$TAXONOMY_VERSION:math:atomic:monotonicity",
        subject = "MATH",
        displayName = "判断函数单调性",
        parentKnowledgeNodeId = TOPIC_ID,
        taxonomyVersion = TAXONOMY_VERSION,
        createdAtEpochMillis = 1_000,
        canonicalName = "判断函数单调性",
        nodeKind = KnowledgeNodeKind.REASONING.name,
        granularity = KnowledgeNodeGranularity.ATOMIC.name,
        boundaryMarkdown = "只判断给定函数在指定区间上的单调性。",
        verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
    )

    private fun binding(nodeId: String) = KnowledgeNodeSourceBindingSeedRecord(
        knowledgeNodeId = nodeId,
        sourceId = SOURCE_ID,
        sourceLocator = "函数目录·单调性",
        derivationNote = "人工核对原始材料后拆分。",
        reviewedAtEpochMillis = 1_000,
    )

    private companion object {
        const val TAXONOMY_VERSION = "moe-signal-test-v1"
        const val SOURCE_ID = "source:research:math"
        const val TOPIC_ID = "kb:moe-signal-test-v1:math:topic:function"
        const val ATOMIC_ID = "kb:moe-signal-test-v1:math:atomic:monotonicity"
    }
}
