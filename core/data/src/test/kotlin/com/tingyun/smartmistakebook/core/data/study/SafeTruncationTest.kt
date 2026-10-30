package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialNodeBindingRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
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
 * KB 使用面 R3/R4：`KNOWLEDGE_READ` 渲染时的**安全截断**
 * （`docs/research/2026-10-26-kb-usage-surface-audit.md` R3 `:113-146`、R4 `:148-186`）。
 *
 * 消灭的三个具体失败：
 * 1. R3：`take(60)` 落在 `$$...$$` / `$...$` / `\(...\)` 公式中间，模型看到语法损坏的
 *    数学表达式；切口必须退到公式起点，整段公式下沉（找不到安全点时才回退原 `take`）。
 * 2. R4：boundary 的 `take(80)` 落在句子中间，把范围/区分句切成难以判读的半句；切口必须
 *    退到句号/分号，完整句子才留下，半句整句下沉。
 * 3. 没有公式、也没有句界可退时，行为必须与旧的 `take(limit)` 完全一致（保底，不改变形态）。
 *
 * 预算与结果形态冻结：仍是最多 5 行、每行 boundary ≤ 80 字、材料摘要 ≤ 60 字，整条结果
 * 仍受 `TutorToolOutcome.MAX_TOOL_RESULT_CHARS`（2,000 字符）约束。
 */
class SafeTruncationTest {

    // ---- 夹具 -------------------------------------------------------------------------------

    private fun node(index: Int, boundary: String? = null) = KnowledgeNodeSeedRecord(
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
        boundaryMarkdown = boundary,
        verificationStatus = "CURATED",
    )

    private fun FakeStudyDatabasePort.seedMaterial(
        id: String,
        nodeId: String,
        summary: String,
    ) {
        teachingMaterials += KnowledgeTeachingMaterialRecord(
            materialId = id,
            stableCode = "stable:$id",
            subject = "MATH",
            materialType = KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
            title = "T-$id",
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
            role = KnowledgeMaterialNodeRole.PRIMARY.name,
        )
    }

    private fun runner(port: FakeStudyDatabasePort) =
        RoomTutorToolRunner(port, MutableStateFlow(KnowledgeBaseAvailability.Ready))

    private fun knowledgeReadCall() = TutorToolCall(
        tool = TutorToolName.KNOWLEDGE_READ,
        rationale = "看这道题相关的知识点",
        terms = listOf("单调性"),
    )

    private fun String.nodeLine(index: Int): String =
        lines().first { it.startsWith("${index + 1}. 知识点$index") }

    // ---- R3：公式配对感知 ----------------------------------------------------------------

    @Test
    fun inlineDollarFormulaAtTheCutRetreatsToItsStartInsteadOfSplittingIt() {
        val firstFormula = "\$y=kx\$"
        val secondFormula = "\$k^2x^2-y^2=m\$"
        val text = "渐近线为 $firstFormula，可设 $secondFormula，后续步骤略。"
        val secondStart = text.indexOf(secondFormula)

        val result = safeTruncateMarkdown(text, secondStart + 2)

        assertEquals(
            "第 60 字落在第二个 $...$ 中间时，必须退到该公式起点，整段公式下沉",
            text.substring(0, secondStart),
            result,
        )
        assertEquals("结果里的 $ 必须成对：$result", 0, result.count { it == '$' } % 2)
    }

    @Test
    fun displayDollarFormulaAtTheCutRetreatsToItsStartInsteadOfSplittingIt() {
        val formula = "\$\$x^2+y^2=1\$\$"
        val text = "先配方：$formula，再代入后续步骤。"
        val formulaStart = text.indexOf(formula)

        val result = safeTruncateMarkdown(text, formulaStart + 3)

        assertEquals("$$...$$ 同样不得被切成两半", "先配方：", result)
        assertFalse("整段公式下沉，结果不得留下 $：$result", result.contains("$"))
    }

    @Test
    fun parenDelimitedFormulaAtTheCutRetreatsToItsStartInsteadOfSplittingIt() {
        val firstFormula = "\\(a+b\\)"
        val secondFormula = "\\(c+d\\)"
        val text = "由 $firstFormula 得 $secondFormula 成立。"
        val secondStart = text.indexOf(secondFormula)

        val result = safeTruncateMarkdown(text, secondStart + 2)

        assertEquals("\\(...\\) 同样不得被切成两半", "由 $firstFormula 得 ", result)
    }

    @Test
    fun aFormulaAtPositionZeroLeavesNoSafePointSoTheOriginalTakeIsKept() {
        val text = "\$x+y+z\$ 是公式"

        assertEquals(
            "公式从第 0 字就开始时没有安全点，必须原样回退到 take(limit)",
            text.take(4),
            safeTruncateMarkdown(text, 4),
        )
    }

    // ---- R4：句界回退 ----------------------------------------------------------------------

    @Test
    fun truncatesAfterTheLastFullStopBeforeTheLimit() {
        val text = "第一句话在这里结束。第二句话还在继续，而且很长很长。"

        assertEquals(
            "切口必须退到前一个句号（含句号），不得把第二句切成半句",
            "第一句话在这里结束。",
            safeTruncateMarkdown(text, 20),
        )
    }

    @Test
    fun truncatesAfterTheLastSemicolonBeforeTheLimit() {
        val text = "定位：某教材某页；范围：只讨论标准形式，不涉及参数讨论。"

        assertEquals(
            "分号同样是安全句界",
            "定位：某教材某页；",
            safeTruncateMarkdown(text, 12),
        )
    }

    @Test
    fun sentencePunctuationInsideAFormulaIsNotASafeCut() {
        val text = "条件成立。设 \$a；b\$ 为已知" + "补充".repeat(30)

        assertEquals(
            "公式内部的；不是句界，退到它前面会把公式切开",
            "条件成立。",
            safeTruncateMarkdown(text, 20),
        )
    }

    @Test
    fun noFormulaAndNoSentenceBoundaryFallsBackToTheOriginalTake() {
        val text = "无标点".repeat(30)

        assertEquals(
            "既没有公式冲突、也没有句界可退时，行为必须与旧 take(limit) 完全一致",
            text.take(80),
            safeTruncateMarkdown(text, 80),
        )
    }

    // ---- 集成：boundary 渲染走安全截断 ------------------------------------------------------

    @Test
    fun boundaryIsCutAtASentenceBoundarySoAPartialRangeSentenceIsNotShown() = runBlocking {
        val range = "范围：只讨论标准形式的一元二次方程，不涉及参数讨论与根分布，" +
            "区别于判别式专题，也不涉及含参不等式与二次函数图象的开口方向判断，" +
            "更不涉及韦达定理的逆用。"
        val boundary = "定位：教材第 12 页。" + range
        assertTrue("夹具必须超过 80 字窗口", boundary.length > 80)

        val port = FakeStudyDatabasePort()
        port.recallCandidates += node(0, boundary)

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        val segment = execution.outcome.summaryMarkdown.nodeLine(0)
            .substringAfter("：")
            .substringBefore("｜")
        assertEquals(
            "80 字切口落在范围句中间时，退到前一个句号：完整句留下，半句整句下沉",
            "定位：教材第 12 页。",
            segment,
        )
        assertFalse("不得展示半截范围句：$segment", segment.contains("范围："))
    }

    @Test
    fun boundaryKeepsACompleteRangeSentenceThatFitsBeforeTheLimit() = runBlocking {
        val keptRange = "范围：只讨论标准形式，不涉及参数讨论；"
        val boundary = "定位：教材第 12 页。" + keptRange + "补充：" + "后续内容".repeat(20)
        assertTrue("夹具必须超过 80 字窗口", boundary.length > 80)

        val port = FakeStudyDatabasePort()
        port.recallCandidates += node(0, boundary)

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        val segment = execution.outcome.summaryMarkdown.nodeLine(0)
            .substringAfter("：")
            .substringBefore("｜")
        assertEquals(
            "完整落在 80 字内的范围句必须原样保留，只丢掉它之后的部分",
            "定位：教材第 12 页。" + keptRange,
            segment,
        )
        assertTrue("保留的范围句必须完整：$segment", segment.endsWith("；"))
        assertFalse("半句补充内容不得展示：$segment", segment.contains("补充："))
    }

    // ---- 集成：材料摘要渲染走安全截断 -------------------------------------------------------

    @Test
    fun materialDigestRetreatsBeforeAFormulaAndStillHonorsTheSixtyCharShapeAndTwoKBudget() = runBlocking {
        val formula = "\$x^2+y^2=r^2\$"
        val summary = "定位：教材第 12 页。推导过程如下：" + "讲解".repeat(15) +
            formula + "，后续步骤不再展示。"
        val formulaStart = summary.indexOf(formula)
        assertTrue(
            "夹具必须让 60 字切口落在公式内（formulaStart=$formulaStart）",
            formulaStart < 60 && formulaStart + formula.length > 60,
        )

        val port = FakeStudyDatabasePort()
        port.recallCandidates += node(0)
        port.seedMaterial("m-formula", "kc-0", summary)

        val execution = runner(port).runTraced(
            knowledgeReadCall(),
            RoomTutorToolRunner.Context(subject = "MATH"),
        )

        val markdown = execution.outcome.summaryMarkdown
        val digest = markdown.nodeLine(0).substringAfter("｜材料：")
        assertEquals(
            "材料摘要切口落在公式内时必须退到公式起点，整段公式下沉",
            summary.substring(0, formulaStart),
            digest,
        )
        assertTrue("材料摘要仍不得超过 60 字：${digest.length}", digest.length <= 60)
        assertFalse("不得留下半个公式：$digest", digest.contains("$"))

        // ④ 2k **字符**预算不变（TutorToolOutcome.MAX_TOOL_RESULT_CHARS = 2_000）。
        assertEquals(2_000, TutorToolOutcome.MAX_TOOL_RESULT_CHARS)
        assertTrue(
            "工具结果必须仍在 2k 字符预算内，实际 ${markdown.length} 字符",
            markdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
        )
    }
}
