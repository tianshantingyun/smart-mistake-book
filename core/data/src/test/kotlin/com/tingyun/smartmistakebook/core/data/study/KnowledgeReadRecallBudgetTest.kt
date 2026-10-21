package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
import com.tingyun.smartmistakebook.core.database.MAX_KNOWLEDGE_RECALL_CANDIDATES
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A2 生产接线断言：`KNOWLEDGE_READ` 的**查询深度**必须是金标/设备融合臂的 512
 * （`MAX_KNOWLEDGE_RECALL_CANDIDATES`），而**模型可见的结果**仍是原来的 top-5 形态。
 *
 * 消灭的具体失败（2026-10-21）：生产学生走的是 `limit=5` 的纯词面召回，而金标判的是
 * B512 融合路由（主集 Recall@5 0.7752 vs 0.6444）——判得好的那条路由只有判官在走。
 * 只把 limit 改成 512 而忘了截断，会把整个候选域（含父节点）渲染进工具结果/prompt；
 * 只截断而没把深度改到 512，则稠密腿的重排域仍是 5 条，融合再准也拉不进新节点。
 * 两条断言缺一不可。
 */
class KnowledgeReadRecallBudgetTest {

    /**
     * 记录查询深度/稠密腿输入/材料读取范围的假库：委托 [FakeStudyDatabasePort] 的既有
     * 召回与材料过滤语义，只旁路记录"生产执行器实际问了什么"。
     */
    private class RecordingPort : FakeStudyDatabasePort() {
        val recallLimits = mutableListOf<Int>()
        val recallQueryTexts = mutableListOf<String?>()
        val materialNodeIdSets = mutableListOf<Set<String>>()

        override suspend fun readSubjectKnowledgeRecallCandidates(
            subject: String,
            searchFeatures: Set<String>,
            limit: Int,
            queryText: String?,
        ): List<KnowledgeNodeSeedRecord> {
            recallLimits += limit
            recallQueryTexts += queryText
            return super.readSubjectKnowledgeRecallCandidates(
                subject = subject,
                searchFeatures = searchFeatures,
                limit = limit,
                queryText = queryText,
            )
        }

        override suspend fun readKnowledgeTeachingMaterialsForNodes(
            subject: String,
            knowledgeNodeIds: Set<String>,
            limit: Int,
        ): List<KnowledgeTeachingMaterialRecord> {
            materialNodeIdSets += knowledgeNodeIds
            return super.readKnowledgeTeachingMaterialsForNodes(
                subject = subject,
                knowledgeNodeIds = knowledgeNodeIds,
                limit = limit,
            )
        }
    }

    private fun node(index: Int) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = "kc-$index",
        stableCode = "kc-$index",
        subject = "MATH",
        displayName = "知识点$index",
        parentKnowledgeNodeId = null,
        taxonomyVersion = "taxonomy-v1",
        createdAtEpochMillis = 1_000,
        canonicalName = "知识点$index",
        nodeKind = "CONCEPT",
        granularity = "ATOMIC",
        verificationStatus = "CURATED",
    )

    private fun runner(port: RecordingPort) =
        RoomTutorToolRunner(port, MutableStateFlow(KnowledgeBaseAvailability.Ready))

    private fun knowledgeReadCall(terms: List<String>) = TutorToolCall(
        tool = TutorToolName.KNOWLEDGE_READ,
        rationale = "看这道题相关的知识点",
        terms = terms,
    )

    @Test
    fun knowledgeReadQueriesTheFused512DepthButRendersOnlyTheTopFive() = runBlocking {
        val port = RecordingPort()
        // 12 个候选：512 深度会把它们全召回；执行器必须在渲染/取材料前截到 5。
        repeat(12) { index -> port.recallCandidates += node(index) }

        val execution = runner(port).runTraced(
            knowledgeReadCall(terms = listOf("单调性")),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        // ① 调用深度 = 金标融合臂的 512（常量只读引用，未改 core:database）。
        assertEquals(
            "KNOWLEDGE_READ 的查询深度必须与金标/设备融合臂同构",
            listOf(MAX_KNOWLEDGE_RECALL_CANDIDATES),
            port.recallLimits,
        )
        assertEquals(512, port.recallLimits.single())
        // ② 稠密腿在场：原始题面必须交给 store（null/blank 会静默退回纯词面次序）。
        assertEquals(listOf("单调性"), port.recallQueryTexts)

        // ③ 返回节点 ≤ 5，且就是召回序列的前 5 个（顺序不变——不重排、不补位）。
        assertTrue("expected ok outcome but was $execution", execution.outcome.ok)
        assertEquals(5, execution.resultCount)
        val lines = execution.outcome.summaryMarkdown.lines()
        assertEquals("知识点候选 5 个：", lines.first())
        val numbered = lines.filter { NUMBERED_LINE.containsMatchIn(it) }
        assertEquals("工具结果只渲染前 5 个节点", 5, numbered.size)
        assertTrue(numbered[0].startsWith("1. 知识点0"))
        assertTrue(numbered[4].startsWith("5. 知识点4"))
        assertFalse(
            "第 6 个及之后的候选不得进结果: ${execution.outcome.summaryMarkdown}",
            execution.outcome.summaryMarkdown.contains("知识点5"),
        )
        assertFalse(
            "原始 id 不进工具结果: ${execution.outcome.summaryMarkdown}",
            execution.outcome.summaryMarkdown.contains("kc-"),
        )

        // ④ 取材料也只碰截断后的 5 个节点（512 个候选绝不进 prompt/材料查询）。
        assertEquals(
            "材料摘要必须按 top-5 节点取，不能按 512 候选域取",
            listOf((0 until 5).map { "kc-$it" }.toSet()),
            port.materialNodeIdSets,
        )

        // ⑤ 形态不变：仍是 2k 预算内的单条 markdown + 本地 resultCount，没有新增模型可见字段。
        assertNull(execution.outcome.errorKind)
        assertNull("KNOWLEDGE_READ 不生图", execution.figure)
        assertTrue("KNOWLEDGE_READ 没有错题条目留痕", execution.problemEntryIds.isEmpty())
        assertTrue(
            "结果仍受单工具 2k 预算约束",
            execution.outcome.summaryMarkdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }

    @Test
    fun aSmallerCandidateSetIsRenderedAsIsWithoutPadding() = runBlocking {
        val port = RecordingPort()
        repeat(2) { index -> port.recallCandidates += node(index) }

        val execution = runner(port).runTraced(
            knowledgeReadCall(terms = listOf("单调性")),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        assertEquals(listOf(MAX_KNOWLEDGE_RECALL_CANDIDATES), port.recallLimits)
        assertEquals(2, execution.resultCount)
        val lines = execution.outcome.summaryMarkdown.lines()
        assertEquals("知识点候选 2 个：", lines.first())
        assertEquals(2, lines.count { NUMBERED_LINE.containsMatchIn(it) })
    }

    private companion object {
        val NUMBERED_LINE = Regex("^\\d+\\. ")
    }
}
