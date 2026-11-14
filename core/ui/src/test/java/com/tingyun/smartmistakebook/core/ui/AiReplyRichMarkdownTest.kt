package com.tingyun.smartmistakebook.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReplyRichMarkdownTest {
    @Test
    fun formulasInsideCodeAndEscapedDollarsRemainLiteral() {
        val source = "示例 `§x^2§`。\n\n~~~text\n§§y^2§§\n~~~\n\n转义 \\§x\\§".replace('§', '$')
        org.junit.Assert.assertEquals(listOf(TutorProseSegment.Markdown(source)), splitTutorProseSegments(source))
    }

    @Test
    fun commonLatexDelimitersAndMathematicalComparisonsRenderWithoutLosingMarkdown() {
        val parts = splitTutorProseSegments("先看\\[x^2\\]，再看 **条件** \\(0<e<1\\)。")
        org.junit.Assert.assertEquals(TutorProseSegment.DisplayMath("x^2"), parts[1])
        val tail = (parts.last() as TutorProseSegment.Markdown).text
        assertTrue(tail.contains("&lt;"))
        assertTrue(shouldUseRichTextMarkdown(tail))
    }

    @Test
    fun unterminatedDisplayMathDoesNotBecomeInlineMath() {
        val source = "先看 §$ x^2\n尚未完成".replace('§', '$')
        org.junit.Assert.assertEquals(listOf(TutorProseSegment.Markdown(source)), splitTutorProseSegments(source))
    }

    @Test
    fun richTextAllowedForWhitelistedBlockMarkdown() {
        assertTrue(shouldUseRichTextMarkdown("# 先看符号\n\n- 步骤一\n- 步骤二"))
        assertTrue(shouldUseRichTextMarkdown("**重点** 与 `代码`"))
        assertTrue(shouldUseRichTextMarkdown("> 引用一句讲解"))
    }

    @Test
    fun richTextFallsBackForRawHtml() {
        assertFalse(shouldUseRichTextMarkdown("答案 <div>不是这样</div>"))
        assertFalse(shouldUseRichTextMarkdown("<script>run()</script>"))
    }

    @Test
    fun richTextFallsBackForActiveSchemeAndRemoteImage() {
        assertFalse(shouldUseRichTextMarkdown("点我 javascript:alert(1)"))
        assertFalse(shouldUseRichTextMarkdown("![图](https://evil.com/x.png)"))
        assertFalse(shouldUseRichTextMarkdown("详见 https://example.com"))
    }

    @Test
    fun richTextFallsBackForBlank() {
        assertFalse(shouldUseRichTextMarkdown(""))
        assertFalse(shouldUseRichTextMarkdown("   "))
    }

    @Test
    fun singleNewlinesBecomeParagraphBreaks() {
        assertEquals("第一段\n\n第二段", normalizeTutorParagraphs("第一段\n第二段"))
    }

    @Test
    fun listItemsKeepTheirStructure() {
        assertEquals("- 步骤一\n- 步骤二", normalizeTutorParagraphs("- 步骤一\n- 步骤二"))
    }

    @Test
    fun fencedCodeIsUntouched() {
        val source = "前文\n\n```kotlin\nval a = 1\nval b = 2\n```"
        assertEquals(source, normalizeTutorParagraphs(source))
    }
}
