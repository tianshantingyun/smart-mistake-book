package com.tingyun.smartmistakebook.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorPlanStreamPreviewTest {
    @Test
    fun openingCanGrowAcrossEscapesBeforeTheObjectIsComplete() {
        val prefix = """{"openingMarkdown":"先看\"斜率\"，再写"""
        assertEquals("先看\"斜率\"，再写", (prefix + "\\u5").tutorPlanOpeningPreview())
        assertEquals("先看\"斜率\"，再写公式\n", (prefix + """\u516c式\n""").tutorPlanOpeningPreview())
    }

    @Test
    fun nestedOpeningsAndGatedAnswerFieldsAreIgnored() {
        assertNull("""{"diagnosticQuestion":{"openingMarkdown":"内部提示"}}""".tutorPlanOpeningPreview())
        assertNull("""{"solutionMarkdown":"答案","alternateMethodMarkdown":"另一种解法"}""".tutorPlanOpeningPreview())
    }

    @Test
    fun toolEnvelopesAndUnsafeOpeningsAreSuppressed() {
        assertNull("""{"openingMarkdown":"草稿","toolRequests":[{"terms":["不对外显示"]""".tutorPlanOpeningPreview())
        assertNull("""{"openingMarkdown":"点开 https://example.com"}""".tutorPlanOpeningPreview())
        assertNull("""{"openingMarkdown":"<script>"}""".tutorPlanOpeningPreview())
    }
}
