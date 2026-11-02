package com.tingyun.smartmistakebook.core.data.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAigcContentPolicyTest {

    @Test
    fun `AI image alone requires a label`() {
        assertTrue(PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage = true, hasModelParsedText = false))
    }

    @Test
    fun `model parsed text alone requires a label`() {
        assertTrue(PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage = false, hasModelParsedText = true))
    }

    @Test
    fun `pure user text and question diagrams stay unlabeled`() {
        assertFalse(PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage = false, hasModelParsedText = false))
    }

    @Test
    fun `derives model parsed text from block provenance names`() {
        assertTrue(
            PdfAigcContentPolicy.hasModelParsedText(
                listOf("USER_ENTERED", PdfAigcContentPolicy.MODEL_DOCUMENT_PARSE_PROVENANCE),
            ),
        )
        assertFalse(PdfAigcContentPolicy.hasModelParsedText(listOf("USER_ENTERED", "OCR_TRANSCRIPT")))
    }
}
