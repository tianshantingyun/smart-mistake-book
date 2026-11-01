package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轮内结果预算（spec model-intent-routing §3.1）。单工具上限管不到"一轮加起来"，
 * 所以这里守的是**总额**：三个 2k 结果不能把 6k 塞进下一轮 prompt。
 *
 * 被压缩的结果必须**说出来**——静默变短的清单会被读成完整清单，模型据此
 * 以为没有更多可查。这条性质是这些用例的核心，不是顺带断言的。
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
     * 渲染 v2 之后，KNOWLEDGE_READ 的单条结果可以长到单工具上限（2k，见
     * `docs/research/2026-10-31-kb-knowledge-read-render-v2-prereg.md` §1.1/§2.1）。它和另外
     * 两条各自可到 2k 的结果同轮时，轮总额仍必须是 4k：第一条按序放行，第二条缩节选并**说出**
     * 被截断，第三条替换成"未随请求发送"的说明而不是消失。
     *
     * 这条锁的是 v2 与轮预算的相互作用：单条变长后，"三条各 2k"不再是假想，4k 轮门必须在
     * 真实形态上仍然成立，且替换/截断的注记格式与既有契约共用同一组常量。
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
        val budgeted = round.outcomes

        // 三条都在：被压缩的说明自己是谁，不能被静默抹掉。
        assertEquals(3, budgeted.size)

        // 第一条（渲染 v2 的 KNOWLEDGE_READ）在 4k 里放得下：原样通过，不因"它自己用满了 2k"被缩。
        assertEquals(TutorToolName.KNOWLEDGE_READ, budgeted[0].tool)
        assertEquals(knowledgeRead, budgeted[0].summaryMarkdown)

        // 第二条放不下整条：保留可用前缀，并以既有截断注记结尾（注记格式 = 契约常量）。
        assertEquals(TutorToolName.NOTEBOOK_READ, budgeted[1].tool)
        assertTrue(
            "截断必须保留可用前缀：${budgeted[1].summaryMarkdown.take(32)}",
            budgeted[1].summaryMarkdown.startsWith("错题本匹配 6 条："),
        )
        assertTrue(
            "截断必须命名：${budgeted[1].summaryMarkdown.takeLast(32)}",
            budgeted[1].summaryMarkdown.endsWith(TRUNCATED_OUTCOME_NOTE),
        )
        assertTrue(
            "第二条确实被压缩了：${budgeted[1].summaryMarkdown.length}",
            budgeted[1].summaryMarkdown.length < notebookRead.length,
        )

        // 第三条完全放不下：替换成未发送说明（不是空串、不是消失），执行结论不变。
        assertEquals(DROPPED_OUTCOME_SUMMARY, budgeted[2].summaryMarkdown)
        assertEquals(TutorToolName.MASTERY_READ, budgeted[2].tool)
        assertEquals(true, budgeted[2].ok)

        // 轮总额仍受 4k 门约束（与既有用例同一口径：每条之间按 1 个换行计）。
        val total = budgeted.sumOf { it.summaryMarkdown.length } + budgeted.size - 1
        assertTrue(
            "round total $total exceeds the round budget",
            total <= TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS,
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
    fun theRoundResultIsBuiltWithTheBudgetApplied() {
        val outcomes = (1..3).map { outcome("x".repeat(2_000) + it) }

        val round = tutorToolRoundResult(
            roundOrdinal = 1,
            outcomes = outcomes,
            extendedResultUsed = false,
        )

        val total = round.outcomes.sumOf { it.summaryMarkdown.length } + round.outcomes.size - 1
        assertTrue("round total $total exceeds the base budget", total <= 4_000)
    }

    @Test
    fun anExtendedRoundGetsTheLargerTotalSoTheExtendedResultIsReachable() {
        // 这条锁的是你批的两项之间的相互作用：若轮总额不随扩展调用提高，单个 6k 结果
        // 会被 4k 的轮门砍掉——扩展预算就成了永远生效不了的空头承诺。
        val extended = outcome("a".repeat(5_000))
        val plain = outcome("b".repeat(1_500))

        val baseRound = tutorToolRoundResult(1, listOf(extended, plain), extendedResultUsed = false)
        val extendedRound = tutorToolRoundResult(1, listOf(extended, plain), extendedResultUsed = true)

        assertTrue(
            "the 5k result should not survive the base round budget",
            baseRound.outcomes.first().summaryMarkdown.length < 5_000,
        )
        assertEquals(5_000, extendedRound.outcomes.first().summaryMarkdown.length)
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
