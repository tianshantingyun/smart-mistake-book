package com.tingyun.smartmistakebook.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorMessageDocumentTest {
    @Test fun clipboardIncludesTheVisibleQuestionAndMeaningfulFigureCaption() {
        val copy = tutorMessageCopyText("先看图区间。\n\n[[figure:curve]]\n\n[[question]]\n\n[[question]]",
            figureCaptions = mapOf("curve" to "阴影面积表示从 0 到 h/2 的功"),
            questionMarkdown = "导数如何变化？\nA. 先正后负\nB. 先负后正")
        assertTrue(copy.contains("阴影面积表示从 0 到 h/2 的功"))
        assertTrue(copy.contains("A. 先正后负"))
        assertEquals(1, Regex("导数如何变化").findAll(copy).count())
    }

    @Test
    fun questionHasAnExplicitPositionBetweenTheExplanationParagraphs() {
        assertEquals(listOf(TutorMessageBlock.Markdown("先观察变化。"), TutorMessageBlock.Question,
            TutorMessageBlock.Markdown("带着这个判断继续分析。")),
            parseTutorMessageDocument("先观察变化。\n\n[[question]]\n\n带着这个判断继续分析。"))
        val code = "```text\n[[question]]\n```"
        assertEquals(listOf(TutorMessageBlock.Markdown(code)), parseTutorMessageDocument(code))
        assertEquals("先观察。\n\n〔思考题〕", tutorMessageCopyText("先观察。\n\n[[question]]"))
    }

    @Test
    fun blankLinesInsideMathDoNotCreateFigureBlocks() {
        val text = "$$\nx = 1\n\n[[figure:math]]\n\ny = 2\n$$"
        assertEquals(emptyList<String>(), tutorMessageFigureReferences(text))
    }

    @Test
    fun laterAnswerFiguresAreNotAppendedToTheOpeningPrompt() {
        org.junit.Assert.assertEquals(listOf("hint", "legacy"), tutorOpeningFigureAssets(
            "先看条件。\n\n[[figure:hint]]", listOf("hint", "answer", "legacy"),
            listOf("完整解法。\n\n[[figure:answer]]")))
    }

    @Test fun positionsAnImageBetweenExplanationParagraphs() {
        assertEquals(listOf(
            TutorMessageBlock.Markdown("先判断方向。"),
            TutorMessageBlock.Figure("figure-a"),
            TutorMessageBlock.Markdown("再求大小。"),
        ), parseTutorMessageDocument("先判断方向。\n\n[[figure:figure-a]]\n\n再求大小。"))
    }

    @Test fun codeQuotesListsAndInlineExamplesNeverBecomeExecutableReferences() {
        val source = "```text\n[[figure:code]]\n```\n\n> [[figure:quote]]\n\n- [[figure:list]]\n\n示例 `[[figure:inline]]`"
        assertEquals(listOf(TutorMessageBlock.Markdown(source)), parseTutorMessageDocument(source))
    }

    @Test fun repeatedReferencesKeepTheirPositionsAndUnknownIdsRemainUntrusted() {
        val blocks = parseTutorMessageDocument("[[figure:unknown]]\n\n[[figure:unknown]]")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it == TutorMessageBlock.Figure("unknown") })
    }

    @Test fun formulasAndIncompleteMarkersRemainText() {
        val source = "$$\n[[figure:formula]]\n$$\n\n[[figure:unfinished"
        assertEquals(listOf(TutorMessageBlock.Markdown(source)), parseTutorMessageDocument(source))
        assertTrue(parseTutorMessageDocument("").isEmpty())
    }

    @Test fun supportsCrLfAndUnicodeBeforeTheReference() {
        assertEquals(listOf(TutorMessageBlock.Markdown("方向 😀"), TutorMessageBlock.Figure("本地图")),
            parseTutorMessageDocument("方向 😀\r\n\r\n[[figure:本地图]]\r\n"))
    }

    @Test fun copyingKeepsMarkdownButUsesReadableFigureNumbers() {
        assertEquals("**方向**\n\n〔图 1〕\n\n〔图 1〕",
            tutorMessageCopyText("**方向**\n\n[[figure:local-secret-id]]\n\n[[figure:local-secret-id]]"))
    }
}
