package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.knowledge.RoomTutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialNodeBindingRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
import com.tingyun.smartmistakebook.core.database.port.KnowledgeReadPort
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KB 使用面 R1：`KNOWLEDGE_READ` 的**材料分配**（`docs/research/2026-10-26-kb-usage-r1-prereg.md`
 * §2.1/§2.2）。
 *
 * 消灭的两个具体失败（审计 `2026-10-26-kb-usage-surface-audit.md` R1 `:27-28`、`:50`）：
 * 1. 旧取数把 `limit = nodes.size` 当成**跨节点合计**上限，材料丰沛的节点会把整笔预算吃光，
 *    其余有材料的节点静默得不到摘要（金标 344/645 行，53.3%）——"有材料"与"没被选中"混为一谈。
 * 2. 无绑定材料的节点被静默省略材料段，模型无法区分"这个知识点没有材料"与"材料没取到"。
 *
 * 冻结口径：
 * - 每个**有材料**的节点恰好取 1 条，5 个节点 → 材料段总数 5（§2.1）；
 * - 节点内按**教学优先序** `role → material_type → title → material_id` 取最佳一条（§2.2），
 *   这是使用面顺序，不是补救顺序；
 * - 无材料节点渲染 `｜（该节点无材料）`，不得静默省略（§2.1）；
 * - 旧补救序（`reTeachPriority`）与旧取数方法**不变**（§1.2）。
 */
class UsageMaterialAllocationTest {

    // ---- 夹具 -------------------------------------------------------------------------------

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

    /**
     * 直接播种 fake 的两张材料表。插入顺序刻意与教学优先序**相反**：断言必须靠排序得出，
     * 不能靠"列表里第一条"碰巧对上。
     */
    private fun FakeStudyDatabasePort.seedMaterial(
        id: String,
        nodeId: String,
        type: KnowledgeTeachingMaterialType,
        summary: String,
        role: KnowledgeMaterialNodeRole = KnowledgeMaterialNodeRole.PRIMARY,
        title: String = "T-$id",
    ) {
        teachingMaterials += KnowledgeTeachingMaterialRecord(
            materialId = id,
            stableCode = "stable:$id",
            subject = "MATH",
            materialType = type.name,
            title = title,
            summaryMarkdown = summary,
            applicabilityMarkdown = "适用于当前知识点。",
            contentMarkdown = "材料正文 $id。",
            boundaryMarkdown = "只在题意满足时使用。",
            derivationKind = "REVIEWED_SYNTHESIS",
            sourceId = "source:$id",
            sourceLocator = "source",
            contentFingerprint = "A".repeat(64),
            reviewedAtEpochMillis = 1_000,
        )
        materialNodeBindings += KnowledgeTeachingMaterialNodeBindingRecord(
            materialId = id,
            knowledgeNodeId = nodeId,
            role = role.name,
        )
    }

    private fun runner(port: FakeStudyDatabasePort) =
        RoomTutorToolRunner(port, MutableStateFlow(KnowledgeBaseAvailability.Ready))

    private fun knowledgeReadCall() = TutorToolCall(
        tool = TutorToolName.KNOWLEDGE_READ,
        rationale = "看这道题相关的知识点",
        terms = listOf("单调性"),
    )

    /** 工具执行结果按节点行取行的便捷视图；节点无代号时行首是 `n. 知识点m`。 */
    private fun String.nodeLine(index: Int): String =
        lines().first { it.startsWith("${index + 1}. 知识点$index") }

    private fun String.materialSegmentCount(): Int =
        Regex("｜材料：").findAll(this).count()

    private val expectedPerNodeMaterial = listOf(
        "N0-concept",
        "N1-method",
        "N2-derivation-preferred",
        "N3-misconception",
        "N4-representation-a",
    )

    // ---- 1) 5 个有材料节点 → 各 1 条，且是教学优先序下的最佳那条 ------------------------------

    @Test
    fun eachOfFiveNodesGetsExactlyItsBestTeachingMaterialInTeachingPriorityOrder() = runBlocking {
        val port = FakeStudyDatabasePort()
        repeat(5) { index -> port.recallCandidates += node(index) }

        // node0 先塞 4 条（旧实现里正是这种"富节点"吃掉跨节点合计 limit 的 5 笔预算）。
        // 其中补充讲解是 SUPPORTING、概念讲解是 PRIMARY：role 优先于类型。
        port.seedMaterial("m0-misconception", "kc-0", KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE, "N0-misconception", title = "00 误区")
        port.seedMaterial("m0-worked", "kc-0", KnowledgeTeachingMaterialType.WORKED_EXAMPLE, "N0-worked", title = "01 例题")
        port.seedMaterial("m0-support-concept", "kc-0", KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION, "N0-support-concept", role = KnowledgeMaterialNodeRole.SUPPORTING, title = "00 补充讲解")
        port.seedMaterial("m0-concept", "kc-0", KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION, "N0-concept", title = "02 概念讲解")

        // node1：DERIVATION 的 title 更靠前，但类型序 METHOD_MODEL 更靠前 → 选方法模型。
        port.seedMaterial("m1-misconception", "kc-1", KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE, "N1-misconception", title = "00 误区")
        port.seedMaterial("m1-derivation", "kc-1", KnowledgeTeachingMaterialType.DERIVATION, "N1-derivation", title = "00 推导")
        port.seedMaterial("m1-method", "kc-1", KnowledgeTeachingMaterialType.METHOD_MODEL, "N1-method", title = "zz 方法")

        // node2：两条同角色 DERIVATION → title ASC 决胜。
        port.seedMaterial("m2-worked", "kc-2", KnowledgeTeachingMaterialType.WORKED_EXAMPLE, "N2-worked")
        port.seedMaterial("m2-derivation-late", "kc-2", KnowledgeTeachingMaterialType.DERIVATION, "N2-derivation-fallback", title = "01 推导")
        port.seedMaterial("m2-derivation", "kc-2", KnowledgeTeachingMaterialType.DERIVATION, "N2-derivation-preferred", title = "00 推导")

        // node3：SUPPORTING 的概念讲解虽然类型更靠前，PRIMARY 的误区指南先赢 → 选误区指南。
        port.seedMaterial("m3-support-concept", "kc-3", KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION, "N3-support-concept", role = KnowledgeMaterialNodeRole.SUPPORTING)
        port.seedMaterial("m3-misconception", "kc-3", KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE, "N3-misconception")

        // node4：两条同角色同类型 REPRESENTATION_GUIDE → material_id ASC 决胜。
        port.seedMaterial("m4-complete", "kc-4", KnowledgeTeachingMaterialType.COMPLETE_SOLUTION, "N4-complete")
        port.seedMaterial("m4-rep-b", "kc-4", KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE, "N4-representation-b", title = "00 表征")
        port.seedMaterial("m4-rep-a", "kc-4", KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE, "N4-representation-a", title = "00 表征")

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        assertTrue("expected ok outcome but was $execution", execution.outcome.ok)
        assertEquals(5, execution.resultCount)

        val markdown = execution.outcome.summaryMarkdown

        // 每节点恰 1 条：总数 = 节点数 = 5（不是全局限 limit 的跨节点合计）。
        assertEquals(
            "5 个有材料节点必须各得 1 条，材料段总数应为 5：$markdown",
            5,
            markdown.materialSegmentCount(),
        )
        assertFalse(
            "有材料节点不得出现无材料标记：$markdown",
            markdown.contains("（该节点无材料）"),
        )

        expectedPerNodeMaterial.forEachIndexed { index, expected ->
            val line = markdown.nodeLine(index)
            assertEquals("节点 $index 只应有 1 个材料段：$line", 1, line.materialSegmentCount())
            assertTrue(
                "节点 $index 必须取教学优先序下的最佳材料 $expected，实际行：$line",
                line.contains("｜材料：$expected"),
            )
        }
    }

    // ---- 2) 无材料节点 → 显式标记；有料节点不受影响 --------------------------------------------

    @Test
    fun aNodeWithoutMaterialsRendersTheExplicitMarkerWithoutDisturbingMaterialNodes() = runBlocking {
        val port = FakeStudyDatabasePort()
        repeat(5) { index -> port.recallCandidates += node(index) }
        port.seedMaterial("m0", "kc-0", KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION, "N0-material")
        port.seedMaterial("m2", "kc-2", KnowledgeTeachingMaterialType.METHOD_MODEL, "N2-material")
        port.seedMaterial("m4", "kc-4", KnowledgeTeachingMaterialType.DERIVATION, "N4-material")

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        assertTrue("expected ok outcome but was $execution", execution.outcome.ok)
        assertEquals("节点行数仍是 5（无材料不减少节点）", 5, execution.resultCount)

        val markdown = execution.outcome.summaryMarkdown

        // 无材料节点：`｜（该节点无材料）`，不得静默省略。
        listOf(1, 3).forEach { index ->
            val line = markdown.nodeLine(index)
            assertTrue(
                "节点 $index 没有绑定材料，必须显式渲染「（该节点无材料）」，实际行：$line",
                line.contains("｜（该节点无材料）"),
            )
            assertFalse("无材料节点不得伪造材料段：$line", line.contains("｜材料："))
        }
        assertEquals("恰好两个无材料节点带标记：$markdown", 2, Regex("（该节点无材料）").findAll(markdown).count())

        // 有料节点不受无材料节点影响，仍各得其 1 条。
        listOf(0 to "N0-material", 2 to "N2-material", 4 to "N4-material").forEach { (index, summary) ->
            val line = markdown.nodeLine(index)
            assertTrue("节点 $index 的材料不得丢失：$line", line.contains("｜材料：$summary"))
            assertFalse("有材料节点不得出现无材料标记：$line", line.contains("（该节点无材料）"))
        }

        // 结构门：每一条节点行都必须以"材料或显式标记"收尾，不允许出现第三种（省略）形态。
        val nodeLines = markdown.lines().filter { NODE_LINE.containsMatchIn(it) }
        assertEquals(5, nodeLines.size)
        assertTrue(
            "每个节点行都必须带材料或显式标记：$markdown",
            nodeLines.all { it.contains("｜材料：") || it.contains("｜（该节点无材料）") },
        )
    }

    // ---- 3) 旧补救序不受影响 ----------------------------------------------------------------

    /**
     * 旧补救消费方（讲题参考 / leech 重教）必须仍走**旧取数方法**并按旧补救序
     * `MISCONCEPTION_GUIDE → WORKED_EXAMPLE → METHOD_MODEL → DERIVATION →
     * CONCEPT_EXPLANATION → REPRESENTATION_GUIDE → COMPLETE_SOLUTION` 排序。
     * 新教学序只属于 `KNOWLEDGE_READ`，不得渗入 `referencesFor`（预注册 §1.2/§2.2）。
     */
    @Test
    fun legacyRemediationOrderAndItsPortMethodAreUnchanged() = runBlocking {
        val port = RecordingLegacyPort()
        val insertionOrder = listOf(
            KnowledgeTeachingMaterialType.COMPLETE_SOLUTION to "m-complete",
            KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE to "m-representation",
            KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION to "m-concept",
            KnowledgeTeachingMaterialType.DERIVATION to "m-derivation",
            KnowledgeTeachingMaterialType.METHOD_MODEL to "m-method",
            KnowledgeTeachingMaterialType.WORKED_EXAMPLE to "m-worked-example",
            KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE to "m-misconception",
        )
        insertionOrder.forEach { (type, id) ->
            port.seedMaterial(id, "kc-0", type, "S-$id", title = "T-$id")
        }
        port.seedMaterial("m-foreign", "kc-9", KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION, "S-foreign")

        val references = RoomTutorTeachingReferenceRepository(port).referencesFor(
            subject = "MATH",
            knowledgeNodeIds = setOf("kc-0"),
        )

        val expected = listOf(
            KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE to "m-misconception",
            KnowledgeTeachingMaterialType.WORKED_EXAMPLE to "m-worked-example",
            KnowledgeTeachingMaterialType.METHOD_MODEL to "m-method",
            KnowledgeTeachingMaterialType.DERIVATION to "m-derivation",
            KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION to "m-concept",
            KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE to "m-representation",
            KnowledgeTeachingMaterialType.COMPLETE_SOLUTION to "m-complete",
        )
        assertEquals(
            "旧补救序（讲题参考/重教）必须逐项不变",
            expected,
            references.map { it.materialType to it.materialId },
        )
        assertFalse(
            "未请求节点的材料不得进讲题参考",
            references.any { it.materialId == "m-foreign" },
        )
        assertEquals(
            "旧补救消费方必须仍走 readKnowledgeTeachingMaterialsForNodes（旧取数方法）",
            listOf(setOf("kc-0") to KnowledgeReadPort.MAX_TEACHING_MATERIAL_CANDIDATES),
            port.legacyCalls,
        )
    }

    // ---- 4) 渲染（c）：take(60) 与 2k 字符预算不变 -------------------------------------------

    /** 长度 > 60 的摘要：前 60 个字符是渲染上界，尾部标记用于证明"第 61 字符起没有进工具结果"。 */
    private fun longSummary(index: Int): String =
        "材料摘要$index " + "讲解内容".repeat(20) + "末尾标记$index"

    @Test
    fun materialDigestStillTakesSixtyCharsAndTheResultStaysWithinTwoThousandChars() = runBlocking {
        val port = FakeStudyDatabasePort()
        repeat(5) { index -> port.recallCandidates += node(index) }
        val summaries = (0 until 5).map { index ->
            val summary = longSummary(index)
            port.seedMaterial("long-$index", "kc-$index", KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION, summary)
            summary
        }

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        val markdown = execution.outcome.summaryMarkdown
        assertEquals(5, execution.resultCount)
        // ② 单查询材料总条数 = 5（5 个有材料节点各 1 条；不是候选域，也不是旧的跨节点合计）。
        assertEquals(
            "单查询 5 个节点 → 材料段必须恰好 5 条：$markdown",
            5,
            markdown.materialSegmentCount(),
        )

        summaries.forEachIndexed { index, summary ->
            val line = markdown.nodeLine(index)
            val digest = line.substringAfter("｜材料：")
            assertEquals("节点 $index 的摘要必须仍是 summaryMarkdown.take(60)", summary.take(60), digest)
            assertEquals("摘要段长度必须是 60 字符", 60, digest.length)
            assertFalse("第 61 个字符起不得进工具结果：$line", digest.contains("末尾标记"))
        }

        // ④ 2k **字符**预算保留（TutorToolOutcome.MAX_TOOL_RESULT_CHARS = 2_000）。
        assertEquals(2_000, TutorToolOutcome.MAX_TOOL_RESULT_CHARS)
        assertTrue(
            "工具结果必须仍在 2k 字符预算内，实际 ${markdown.length} 字符",
            markdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }

    // ---- 5) 10 个召回节点：仍每节点 1 条，总材料预算 = 渲染节点数 -----------------------------

    /**
     * ③ "若未来扩"到 10 个节点时的分配不变量：每节点恰 1 条、总材料条数 = 渲染节点数
     * （不是固定 5，也不是候选数）。断言按 `resultCount` 相对书写，因此当前 5 条截断与
     * 未来扩容都成立。
     */
    @Test
    fun tenRecallCandidatesKeepOneMaterialPerRenderedNodeWithBudgetEqualToNodeCount() = runBlocking {
        val port = FakeStudyDatabasePort()
        repeat(10) { index -> port.recallCandidates += node(index) }
        // 每节点 2 条（误区先插、讲解后插，类型序与插入序相反）：旧全局 limit=5 会被前两个
        // 节点吃光，后面的渲染节点静默没有材料。
        repeat(10) { index ->
            port.seedMaterial(
                "ten-$index-misconception",
                "kc-$index",
                KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE,
                "N$index-misconception",
                title = "00 误区",
            )
            port.seedMaterial(
                "ten-$index-concept",
                "kc-$index",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION,
                "N$index-concept",
                title = "01 讲解",
            )
        }

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        val markdown = execution.outcome.summaryMarkdown
        val nodeLines = markdown.lines().filter { NODE_LINE.containsMatchIn(it) }
        assertEquals("渲染节点行数必须等于 resultCount", execution.resultCount, nodeLines.size)
        assertEquals(
            "材料总条数必须等于节点数（总预算 = 节点数）：$markdown",
            execution.resultCount,
            markdown.materialSegmentCount(),
        )

        nodeLines.forEachIndexed { index, line ->
            assertEquals("节点 $index 必须恰好 1 条材料：$line", 1, line.materialSegmentCount())
            assertTrue(
                "节点 $index 必须取教学优先的讲解而不是误区：$line",
                line.contains("｜材料：N$index-concept"),
            )
        }
        assertFalse(
            "10 个候选里被渲染的节点全部有材料，不得出现无材料标记：$markdown",
            markdown.contains("（该节点无材料）"),
        )
        assertTrue(
            "工具结果必须仍在 2k 字符预算内，实际 ${markdown.length} 字符",
            markdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }

    /** 记录旧取数方法是否仍被旧补救消费方调用（新 port 切换只允许发生在 `KNOWLEDGE_READ`）。 */
    private class RecordingLegacyPort : FakeStudyDatabasePort() {
        val legacyCalls = mutableListOf<Pair<Set<String>, Int>>()

        override suspend fun readKnowledgeTeachingMaterialsForNodes(
            subject: String,
            knowledgeNodeIds: Set<String>,
            limit: Int,
        ): List<KnowledgeTeachingMaterialRecord> {
            legacyCalls += knowledgeNodeIds to limit
            return super.readKnowledgeTeachingMaterialsForNodes(
                subject = subject,
                knowledgeNodeIds = knowledgeNodeIds,
                limit = limit,
            )
        }
    }

    private companion object {
        val NODE_LINE = Regex("^\\d+\\. ")
    }
}
