package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prompt-side tool-history compaction: newest full results win, older ones become visible summaries. */
class TutorPromptBudgetTest {
    private fun round(
        ordinal: Int,
        tool: TutorToolName = TutorToolName.NOTEBOOK_READ,
        dataChars: Int = 5_000,
    ) = TutorToolRoundResult(
        roundOrdinal = ordinal,
        outcomes = listOf(
            TutorToolOutcome(
                tool = tool,
                ok = true,
                summaryMarkdown = "第 $ordinal 轮结果",
                dataJson = """{"blob":"${"x".repeat(dataChars)}"}""",
            ),
        ),
    )

    @Test
    fun newestRoundsKeepTheirDataAndOlderRoundsBecomeBoundedSummaries() {
        val rounds = (1..8).map { round(it) }

        val compacted = rounds.compactedForTutorPrompt(maxChars = 18_000)

        assertEquals(8, compacted.size)
        assertNotNull(compacted.last().outcomes.single().dataJson)
        assertNotNull(compacted[compacted.lastIndex - 1].outcomes.single().dataJson)
        assertNull(compacted.first().outcomes.single().dataJson)
        assertTrue(compacted.first().outcomes.single().summaryMarkdown.startsWith(TutorPromptBudget.COMPACTED_MARK))
        assertTrue(
            compacted.first().outcomes.single().summaryMarkdown.length <=
                TutorPromptBudget.COMPACTED_MARK.length + TutorPromptBudget.COMPACT_SUMMARY_CHARS,
        )
    }

    @Test
    fun shortHistoryIsUnchanged() {
        val rounds = listOf(round(1, dataChars = 10), round(2, dataChars = 10))
        assertEquals(rounds, rounds.compactedForTutorPrompt())
    }
}
