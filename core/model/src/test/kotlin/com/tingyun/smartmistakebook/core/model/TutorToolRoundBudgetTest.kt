package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轮内结果工具的两种形态：
 * - `budgetTutorToolOutcomes` 仍是**显式调用方**可用的压缩工具（保留其完整测试）；
 * - 生产构造 `tutorToolRoundResult` 自 2026-11-18 起不做任何压缩——模型看到完整结果，
 *   这里的用例钉住这条政策，防止有人悄悄把 4k 轮门加回去。
 */
class TutorToolRoundBudgetTest {

    @Test
    fun outcomesThatFitArePassedThroughUntouched() {
        val outcomes = listOf(outcome("第一个结果"), outcome("第二个结果"))

        val budgeted = budgetTutorToolOutcomes(outcomes, budgetChars = 4_000)

        assertEquals(outcomes, budgeted)
    }

    @Test
    fun theRoundTotalIsCappedEvenWhenEachOutcomeIsWithinItsOwnBudget() {
        // 三条各 2k 的结果（各自都在单工具上限内）合计 6k，必须被压到轮预算里。
        val outcomes = (1..3).map { outcome("x".repeat(2_000) + it) }

        val budgeted = budgetTutorToolOutcomes(
            outcomes,
            budgetChars = TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS,
        )

        val total = budgeted.sumOf { it.summaryMarkdown.length }
        assertTrue(
            "round total $total exceeds the round budget",
            total <= TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS,
        )
    }

    @Test
    fun whatDoesNotFitSaysSoInsteadOfSilentlyDisappearing() {
        // 前两条把预算吃光，第三条必须留下"未随请求发送"的说明，而不是变成一个空摘要
        // 或干脆消失。第三条本身很长，确保它是真的放不下。
        val outcomes = listOf(
            outcome("a".repeat(2_000)),
            outcome("b".repeat(2_000)),
            outcome("第三个结果".repeat(500)),
        )

        val budgeted = budgetTutorToolOutcomes(outcomes, budgetChars = 4_000)

        assertEquals(3, budgeted.size)
        assertEquals(DROPPED_OUTCOME_SUMMARY, budgeted.last().summaryMarkdown)
    }

    @Test
    fun admittingADroppedOutcomeStillFitsInsideTheBudget() {
        // 这条是上一条的另一半：写出"未发送"本身也要占字符，所以每一步都得为后面几条
        // 预留那一行。没有预留时总额会反超——预算门就成了摆设。
        val outcomes = listOf(
            outcome("a".repeat(2_000)),
            outcome("b".repeat(2_000)),
            outcome("c".repeat(2_000)),
            outcome("d".repeat(2_000)),
        )

        val budgeted = budgetTutorToolOutcomes(outcomes, budgetChars = 4_000)

        val total = budgeted.sumOf { it.summaryMarkdown.length } + budgeted.size - 1
        assertTrue("round total $total exceeds 4000", total <= 4_000)
        assertEquals(DROPPED_OUTCOME_SUMMARY, budgeted.last().summaryMarkdown)
    }

    @Test
    fun aSqueezedOutcomeKeepsItsExecutionVerdict() {
        // 压缩的是文本，不是执行结论：工具确实跑过了，ok/errorKind 不能被改写。
        val failing = TutorToolOutcome(
            tool = TutorToolName.KNOWLEDGE_READ,
            ok = false,
            summaryMarkdown = "y".repeat(2_000),
            errorKind = "failed",
        )

        val budgeted = budgetTutorToolOutcomes(
            listOf(outcome("x".repeat(2_000)), failing),
            budgetChars = 4_000,
        )

        assertEquals(false, budgeted.last().ok)
        assertEquals("failed", budgeted.last().errorKind)
    }

    /**
     * 2026-11-18：生产轮结果**不再按 4k 压缩**。模型在下一轮看到三条完整结果（每条 2k
     * 的摘要全保留），`extendedResultUsed` 不再改变输出。
     */
    @Test
    fun aRenderV2KnowledgeReadSharesTheRoundWithTwoOtherResultsInOrder() {
        val knowledgeRead = renderV2KnowledgeReadAtSingleToolCap()
        assertEquals(
            "渲染 v2 的 KNOWLEDGE_READ 单条结果应恰好用满单工具上限",
            TutorToolOutcome.MAX_TOOL_RESULT_CHARS,
            knowledgeRead.length,
        )
        val notebookRead = "错题本匹配 6 条：".let { prefix ->
            prefix + "题".repeat(TutorToolOutcome.MAX_TOOL_RESULT_CHARS - prefix.length)
        }
        val masteryRead = "掌握情况（科目 MATH，按最弱优先）：".let { prefix ->
            prefix + "点".repeat(TutorToolOutcome.MAX_TOOL_RESULT_CHARS - prefix.length)
        }

        val round = tutorToolRoundResult(
            roundOrdinal = 1,
            outcomes = listOf(
                toolOutcome(TutorToolName.KNOWLEDGE_READ, knowledgeRead),
                toolOutcome(TutorToolName.NOTEBOOK_READ, notebookRead),
                toolOutcome(TutorToolName.MASTERY_READ, masteryRead),
            ),
            extendedResultUsed = false,
        )

        assertEquals(3, round.outcomes.size)
        assertEquals(knowledgeRead, round.outcomes[0].summaryMarkdown)
        assertEquals(notebookRead, round.outcomes[1].summaryMarkdown)
        assertEquals(masteryRead, round.outcomes[2].summaryMarkdown)
        val total = round.outcomes.sumOf { it.summaryMarkdown.length } + round.outcomes.size - 1
        assertTrue(
            "production must not squeeze a round at 4k: actual $total",
            total > TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS,
        )
    }

    @Test
    fun truncationKeepsAUsablePrefixAndNamesTheTruncation() {
        val outcomes = listOf(
            outcome("a".repeat(1_000)),
            outcome("开头内容" + "b".repeat(3_000)),
        )

        val budgeted = budgetTutorToolOutcomes(outcomes, budgetChars = 2_000)

        val squeezed = budgeted.last().summaryMarkdown
        assertTrue("prefix lost: $squeezed", squeezed.startsWith("开头内容"))
        assertTrue("truncation not named: $squeezed", squeezed.contains(TRUNCATED_OUTCOME_NOTE))
    }

    @Test
    fun aBudgetTooSmallForAnyOutcomeIsRefusedRatherThanSilentlyEmptyingThem() {
        assertThrows(IllegalArgumentException::class.java) {
            budgetTutorToolOutcomes(listOf(outcome("结果")), budgetChars = 3)
        }
    }

    // ---- 轮结果的构成（把预算算术放进 model 层，使它整体可测） ----

    @Test
    fun theRoundResultKeepsEveryOutcomeUnchanged() {
        val outcomes = (1..3).map { outcome("x".repeat(2_000) + it) }

        val round = tutorToolRoundResult(
            roundOrdinal = 1,
            outcomes = outcomes,
            extendedResultUsed = false,
        )

        assertEquals(outcomes, round.outcomes)
    }

    @Test
    fun theExtendedFlagNeverChangesWhatTheModelSees() {
        // 2026-11-18：轮预算退场后，扩展位不再改变结果；两种轮都完整保留 5k 结果。
        val extended = outcome("a".repeat(5_000))
        val plain = outcome("b".repeat(1_500))

        val baseRound = tutorToolRoundResult(1, listOf(extended, plain), extendedResultUsed = false)
        val extendedRound = tutorToolRoundResult(1, listOf(extended, plain), extendedResultUsed = true)

        assertEquals(5_000, baseRound.outcomes.first().summaryMarkdown.length)
        assertEquals(baseRound.outcomes, extendedRound.outcomes)
    }

    /**
     * 按渲染 v2 冻结的行语法拼一条 KNOWLEDGE_READ 结果（预注册 §1.1）：
     * `<marker>. <displayName>[：<boundary>]｜材料《<materialTitle>》<bodyExcerpt>`，每节点
     * 恰好一条物理行。段长取契约内的极值/上限（名称 24、边界 80、标题 183、节选 ≤120），
     * 使整条恰好 [TutorToolOutcome.MAX_TOOL_RESULT_CHARS] 字符——轮预算看到的是渲染**之后**
     * 的单条结果，这正是 v2 带来的新尺寸。
     *
     * 取的是**结果契约**的 2k 上限（预注册 §2.1），不是当前渲染器的 1,760 body 预算（§2.2 还
     * 另留 240 字给截断注记）：轮预算必须按"单条最多 2k"成立，渲染器内部预留多保守都不改变
     * 它收到的最坏单条尺寸。
     */
    private fun renderV2KnowledgeReadAtSingleToolCap(): String {
        val header = "知识点候选 5 个（代号 K1..Kn 本会话有效，写掌握度时 terms 填代号）："
        val displayName = "知".repeat(24)
        val boundary = "边".repeat(80)
        val materialTitle = "题".repeat(183)
        // 5 行 = 4×(4+24+1+80+4+183+1+93) + (297+95) = 1,952；加 header 43、5 个换行 = 2,000。
        val excerptLengths = listOf(93, 93, 93, 93, 95)
        return buildString {
            append(header)
            excerptLengths.forEachIndexed { index, excerptLength ->
                append('\n')
                append("K${index + 1}. $displayName：$boundary｜材料《$materialTitle》")
                append("节".repeat(excerptLength))
            }
        }
    }

    private fun toolOutcome(tool: TutorToolName, summary: String) = TutorToolOutcome(
        tool = tool,
        ok = true,
        summaryMarkdown = summary,
    )

    private fun outcome(summary: String) = TutorToolOutcome(
        tool = TutorToolName.MASTERY_READ,
        ok = true,
        summaryMarkdown = summary,
    )
}
