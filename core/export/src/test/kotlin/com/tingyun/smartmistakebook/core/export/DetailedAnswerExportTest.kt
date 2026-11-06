package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.*
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class DetailedAnswerExportTest {
    private val question = QuestionDocument("q", "原题标题", listOf(ContentBlock.Paragraph("stem", "这是题干全文，不应复制进答案文件")))
    private val basis = SolutionBasis("revision", solutionQuestionSha256(question, emptyList()))
    private val manifest = FrozenAnswerManifest("draft", "a".repeat(64), MistakePdfLayout.DEFAULT,
        listOf(FrozenExportQuestion(1, "entry", basis, question)), "frozen-paper")
    private val document = DetailedSolutionDocument(basis, "42", QuestionDocument("steps", blocks = listOf(
        ContentBlock.Paragraph("step-1", "第一步：整理已知条件。"),
        ContentBlock.Formula("step-2", "x=6\\times7", "六乘七", true),
        ContentBlock.Paragraph("step-3", "第三步：代入计算。"))), origin = SolutionOrigin.MODEL, verification = SolutionVerification.UNVERIFIED)

    @Test fun wordContainsAllStepsFormulaAndFinalAnswerWithTheFrozenOrdinal() {
        val input = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.DOCX).toNativeInput()
        val directory = Files.createTempDirectory("detailed-answer-export").toFile()
        val file = java.io.File(directory, "answers.docx")
        try {
            DocxMistakeRenderer.render(input, file)
            val xml = ZipFile(file).use { it.getInputStream(it.getEntry("word/document.xml")).reader(Charsets.UTF_8).readText() }
            assertTrue(xml.contains("第一步：整理已知条件。"))
            assertTrue(xml.contains("第三步：代入计算。"))
            assertTrue(xml.contains("x=6\\times7"))
            assertTrue(xml.contains("最终答案") && xml.contains("42") && xml.contains("第 1 题"))
            assertFalse(xml.contains("这是题干全文"))
            assertEquals(MistakeExportArtifactRole.DETAILED_ANSWERS, input.artifactRole)
        } finally { file.delete(); directory.delete() }
    }

    @Test fun differentFormatOrWrongBasisCannotReuseAnAnswerArtifact() {
        val pdf = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.PDF).toNativeInput()
        val word = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.DOCX).toNativeInput()
        assertNotEquals(pdf.inputSha256, word.inputSha256)
        assertTrue(runCatching { DetailedAnswerExportInput(manifest, listOf(document.copy(basis = basis.copy(questionSha256 = "b".repeat(64)))), MistakeExportFormat.PDF) }.isFailure)
    }
}
