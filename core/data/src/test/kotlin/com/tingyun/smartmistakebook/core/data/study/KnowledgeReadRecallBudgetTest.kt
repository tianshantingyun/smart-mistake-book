package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialNodeBindingRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
import com.tingyun.smartmistakebook.core.database.MAX_KNOWLEDGE_RECALL_CANDIDATES
import com.tingyun.smartmistakebook.core.database.port.KnowledgeRecallResult
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
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
        ): KnowledgeRecallResult {
            recallLimits += limit
            recallQueryTexts += queryText
            return super.readSubjectKnowledgeRecallCandidates(
                subject = subject,
                searchFeatures = searchFeatures,
                limit = limit,
                queryText = queryText,
            )
        }

        override suspend fun readBestKnowledgeTeachingMaterialPerNode(
            subject: String,
            knowledgeNodeIds: Set<String>,
        ): Map<String, KnowledgeTeachingMaterialRecord> {
            materialNodeIdSets += knowledgeNodeIds
            return super.readBestKnowledgeTeachingMaterialPerNode(
                subject = subject,
                knowledgeNodeIds = knowledgeNodeIds,
            )
        }
    }

    private fun teachingMaterial(
        id: String,
        type: KnowledgeTeachingMaterialType,
        summary: String,
    ) = KnowledgeTeachingMaterialRecord(
        materialId = id,
        stableCode = "stable:$id",
        subject = "MATH",
        materialType = type.name,
        title = "T-$id",
        summaryMarkdown = summary,
        applicabilityMarkdown = "",
        contentMarkdown = "正文：$id",
        boundaryMarkdown = "",
        derivationKind = "REVIEWED_SYNTHESIS",
        sourceId = "source:$id",
        sourceLocator = "source",
        contentFingerprint = "A".repeat(64),
        reviewedAtEpochMillis = 1_000,
    )

    private fun materialBinding(materialId: String, knowledgeNodeId: String) =
        KnowledgeTeachingMaterialNodeBindingRecord(
            materialId = materialId,
            knowledgeNodeId = knowledgeNodeId,
            role = KnowledgeMaterialNodeRole.PRIMARY.name,
        )

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

    @Test
    fun everyRenderedNodeGetsItsOwnMaterialAndAMaterialslessNodeIsMarkedExplicitly() = runBlocking {
        val port = RecordingPort()
        repeat(3) { index -> port.recallCandidates += node(index) }
        // kc-0 两条材料：教学序取概念讲解而不是误区；kc-1 无绑定材料；kc-2 一条材料。
        port.teachingMaterials += teachingMaterial(
            "m0-concept",
            KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION,
            "N0-摘要",
        )
        port.teachingMaterials += teachingMaterial(
            "m0-misconception",
            KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE,
            "N0-误区",
        )
        port.materialNodeBindings += materialBinding("m0-concept", "kc-0")
        port.materialNodeBindings += materialBinding("m0-misconception", "kc-0")
        port.teachingMaterials += teachingMaterial(
            "m2-method",
            KnowledgeTeachingMaterialType.METHOD_MODEL,
            "N2-摘要",
        )
        port.materialNodeBindings += materialBinding("m2-method", "kc-2")

        val execution = runner(port).runTraced(
            knowledgeReadCall(terms = listOf("单调性")),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        assertEquals(3, execution.resultCount)
        assertEquals(
            "材料取数仍只碰截断后的节点集合",
            listOf(setOf("kc-0", "kc-1", "kc-2")),
            port.materialNodeIdSets,
        )
        val lines = execution.outcome.summaryMarkdown.lines()
            .filter { NUMBERED_LINE.containsMatchIn(it) }
        assertEquals(3, lines.size)

        assertTrue(
            "kc-0 应取教学优先的概念讲解（标题 + 正文节选）：${lines[0]}",
            lines[0].contains("｜材料《T-m0-concept》正文：m0-concept"),
        )
        assertFalse("kc-0 不得取误区材料：${lines[0]}", lines[0].contains("N0-误区"))
        assertFalse("summaryMarkdown 不再进结果：${lines[0]}", lines[0].contains("N0-摘要"))
        assertTrue("kc-1 无材料必须显式标注：${lines[1]}", lines[1].contains("｜（该节点无材料）"))
        assertFalse("kc-1 不得伪造材料段：${lines[1]}", lines[1].contains("｜材料《"))
        assertTrue(
            "kc-2 应得到自己的材料：${lines[2]}",
            lines[2].contains("｜材料《T-m2-method》正文：m2-method"),
        )

        val markdown = execution.outcome.summaryMarkdown
        assertEquals(2, Regex("｜材料《").findAll(markdown).count())
        assertEquals(1, Regex("｜（该节点无材料）").findAll(markdown).count())
        assertTrue(
            "工具结果仍受单工具 2k 预算约束",
            markdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }

    /**
     * 无材料标记是**沿用**的契约（预注册 §1.1：标记文本不变），不随本次“标题 + 节选”改版漂移。
     *
     * 为什么这不是放宽：旧测试只 `contains` 标记；这里把节点行的尾巴逐字锁成
     * `｜（该节点无材料）`，并同时断言没有材料段——标记既不能改字，也不能被材料段顶替。
     */
    @Test
    fun aMaterialslessNodeKeepsTheUnchangedExplicitMarkerAndHasNoMaterialSegment() = runBlocking {
        val port = RecordingPort()
        port.recallCandidates += node(0)
        port.recallCandidates += node(1)
        port.teachingMaterials += teachingMaterial(
            "m0",
            KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION,
            "N0-摘要",
        )
        port.materialNodeBindings += materialBinding("m0", "kc-0")

        val execution = runner(port).runTraced(
            knowledgeReadCall(terms = listOf("单调性")),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        val lines = execution.outcome.summaryMarkdown.lines()
            .filter { NUMBERED_LINE.containsMatchIn(it) }
        assertEquals(2, lines.size)
        assertTrue("有材料节点仍渲染材料段：${lines[0]}", lines[0].contains("｜材料《T-m0》正文：m0"))

        val marker = "｜（该节点无材料）"
        assertEquals(
            "无材料节点的尾巴必须逐字等于沿用标记：${lines[1]}",
            marker,
            lines[1].substringAfter("知识点1"),
        )
        assertFalse("无材料节点不得出现材料段：${lines[1]}", lines[1].contains("｜材料"))
        assertEquals(
            "标记全条只出现一次，且没有改名：${execution.outcome.summaryMarkdown}",
            1,
            Regex(Regex.escape(marker)).findAll(execution.outcome.summaryMarkdown).count(),
        )
    }

    // ---- 2k 渲染门：预算不足先缩节选、缩不下才丢行，且两种裁剪都写进尾部注 --------------------

    /** 极值夹具：24 字节点名、80 字边界、标题与正文长度由测试指定。 */
    private fun budgetNode(index: Int) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = "kc-b$index",
        stableCode = "kc-b$index",
        subject = "MATH",
        displayName = "点".repeat(24),
        parentKnowledgeNodeId = null,
        taxonomyVersion = "taxonomy-v1",
        createdAtEpochMillis = 1_000,
        canonicalName = "点".repeat(24),
        nodeKind = "CONCEPT",
        granularity = "ATOMIC",
        boundaryMarkdown = "边".repeat(80),
        verificationStatus = "CURATED",
    )

    private fun RecordingPort.seedBudgetMaterial(
        nodeId: String,
        materialId: String,
        title: String,
        content: String,
    ) {
        teachingMaterials += KnowledgeTeachingMaterialRecord(
            materialId = materialId,
            stableCode = "stable:$materialId",
            subject = "MATH",
            materialType = KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
            title = title,
            summaryMarkdown = "摘要",
            applicabilityMarkdown = "",
            contentMarkdown = content,
            boundaryMarkdown = "",
            derivationKind = "REVIEWED_SYNTHESIS",
            sourceId = "source",
            sourceLocator = "source",
            contentFingerprint = "A".repeat(64),
            reviewedAtEpochMillis = 1_000,
        )
        materialNodeBindings += materialBinding(materialId, nodeId)
    }

    private fun fiveBudgetNodes(
        port: RecordingPort,
        title: String,
        content: String,
    ) {
        repeat(5) { index ->
            port.recallCandidates += budgetNode(index)
            port.seedBudgetMaterial("kc-b$index", "mb$index", title, content)
        }
    }

    private fun runBudgetRead(port: RecordingPort): TutorToolExecution = runBlocking {
        runner(port).runTraced(
            knowledgeReadCall(terms = listOf("单调性")),
            RoomTutorToolRunner.Context(
                subject = "MATH",
                knowledgeCodeRegistry = TutorKnowledgeCodeRegistry(),
            ),
        )
    }

    @Test
    fun anOverBudgetNodeShortensOnlyItsExcerptAndSaysSoInTheFooter() {
        val port = RecordingPort()
        fiveBudgetNodes(port, title = "题".repeat(120), content = "正".repeat(2_000))

        val text = runBudgetRead(port).outcome.summaryMarkdown
        val nodeLines = text.lines().filter { it.startsWith("K") }

        // 头与行首逐字：代号通道开启时 header 固定，行首即已分配的 K 代号。
        assertEquals(
            "知识点候选 5 个（代号 K1..Kn 本会话有效，写掌握度时 terms 填代号）：",
            text.lines().first(),
        )
        assertTrue("首行必须以 K1 代号开头：${nodeLines.first()}", nodeLines.first().startsWith("K1. "))
        assertTrue("末行必须以 K5 代号开头：${nodeLines.last()}", nodeLines.last().startsWith("K5. "))

        // 前 4 行全量：`K1. ` 4 + 名称 24 + `：` 1 + 边界 80 + `｜材料《` 4 + 标题 120 + `》` 1 + 节选 120。
        assertEquals("5 个节点都必须出现（第 5 个只缩节选、不丢行）：$text", 5, nodeLines.size)
        assertEquals(354, nodeLines.first().length)
        assertEquals(354, nodeLines[3].length)
        // 第 5 行：skeleton 234 + 节选缩到 avail = 1760 - 1464 - 234 - 1 = 61。
        assertEquals(234 + 61, nodeLines[4].length)
        assertTrue("第 5 行必须保住节点、标题与边界，只缩节选：${nodeLines[4]}", nodeLines[4].endsWith("正".repeat(61)))
        assertEquals(
            "footer 必须与裁剪一一对应：0 行丢弃、1 行节选缩短",
            "已截断：0 项未显示，1 行节选缩短。",
            text.lines().last(),
        )
        assertTrue(
            "裁剪后整条仍必须 ≤ 2k，实际 ${text.length}",
            text.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }

    @Test
    fun aNodeWhoseSkeletonNoLongerFitsIsCountedAsDroppedInsteadOfVanishingSilently() {
        val port = RecordingPort()
        fiveBudgetNodes(port, title = "题".repeat(183), content = "正".repeat(2_000))

        val text = runBudgetRead(port).outcome.summaryMarkdown
        val nodeLines = text.lines().filter { it.startsWith("K") }

        // 单行 417（成本 418）：4 行后余 44 < skeleton 297，第 5 行整行丢弃但计数。
        assertEquals("前 4 行全量、第 5 行丢弃：$text", 4, nodeLines.size)
        assertEquals(4 + 1, nodeLines.size + 1) // displayed + dropped = 5，0 静默丢失
        assertEquals(
            "footer 必须报告被丢的那 1 项",
            "已截断：1 项未显示，0 行节选缩短。",
            text.lines().last(),
        )
        assertTrue(
            "裁剪后整条仍必须 ≤ 2k，实际 ${text.length}",
            text.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }

    private companion object {
        val NUMBERED_LINE = Regex("^\\d+\\. ")
    }
}
