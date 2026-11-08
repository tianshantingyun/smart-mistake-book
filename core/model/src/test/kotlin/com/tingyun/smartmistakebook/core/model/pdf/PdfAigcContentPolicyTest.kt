package com.tingyun.smartmistakebook.core.model.pdf

import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import org.junit.Assert.assertEquals
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

    // ------------------------------------------------- 防漂移（KD-75 C2，仅新增断言，不改行为）

    @Test
    fun `user provenance names never force a label`() {
        assertFalse(
            PdfAigcContentPolicy.hasModelParsedText(
                listOf(
                    QuestionBlockProvenance.USER_TRANSCRIPTION.name,
                    QuestionBlockProvenance.USER_CORRECTION.name,
                ),
            ),
        )
    }

    /**
     * 消灭的失败：`QuestionBlockProvenance` 新增取值（例如将来接 `OPTIONAL_REMOTE_OCR`）时，
     * 判据悄悄把它算成/不算成模型文本而没人决定。新增取值会让本断言的名称集合先红，
     * 必须在同一提交里显式表态。
     */
    @Test
    fun `every provenance is explicitly classified`() {
        val names = QuestionBlockProvenance.entries.map { it.name }

        assertEquals(
            "new provenance: classify it here before it can reach the export chain",
            setOf(
                "USER_TRANSCRIPTION",
                "USER_CORRECTION",
                "LOCAL_OCR",
                "OPTIONAL_REMOTE_OCR",
                "MODEL_DOCUMENT_PARSE",
                "DETERMINISTIC_RULE",
                "IMPORTED_STRUCTURE",
            ),
            names.toSet(),
        )
        assertEquals(
            "only MODEL_DOCUMENT_PARSE may force /AIGC",
            setOf(QuestionBlockProvenance.MODEL_DOCUMENT_PARSE.name),
            names.filter { name -> PdfAigcContentPolicy.hasModelParsedText(listOf(name)) }.toSet(),
        )
    }

    /**
     * 消灭的失败：枚举改名后手写字符串常量不跟着改 → 判据恒为 false，**全量漏标且用例全绿**
     * （判据层用的是字符串比较，编译器不会报错）。
     */
    @Test
    fun `provenance constant stays aligned with the enum name`() {
        assertEquals(
            QuestionBlockProvenance.MODEL_DOCUMENT_PARSE.name,
            PdfAigcContentPolicy.MODEL_DOCUMENT_PARSE_PROVENANCE,
        )
    }
}
