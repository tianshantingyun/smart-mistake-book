package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.*
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class DetailedAnswerExportTest {
    @Test fun answerIllustrationsRespectColumnWidthAndPageHeight() {
        val wide = detailedAnswerImageWidth(1200, 800, 500f, 720f, 1f)
        assertEquals(.25f, wide * wide / (345.6f * 345.6f), .0001f)
        assertEquals(250f * .5f, detailedAnswerImageWidth(1200, 800, 500f, 720f, .5f), .01f)
        val portrait = detailedAnswerImageWidth(300, 1800, 500f, 720f, 1f)
        assertEquals(230.4f * .5f, portrait * 1800 / 300, .01f)
    }

    private val question = QuestionDocument("q", "原题标题", listOf(ContentBlock.Paragraph("stem", "这是题干全文，不应复制进答案文件")))
    private val basis = SolutionBasis("revision", solutionQuestionSha256(question, emptyList()))
    private val manifest = FrozenAnswerManifest("draft", "a".repeat(64), MistakePdfLayout.DEFAULT,
        listOf(FrozenExportQuestion(1, "entry", basis, question)), "frozen-paper")
    private val document = DetailedSolutionDocument(basis, "42", QuestionDocument("steps", blocks = listOf(
        ContentBlock.Paragraph("step-1", "第一步：整理已知条件。"),
        ContentBlock.Formula("step-2", "x=6\\times7", "六乘七", true),
        ContentBlock.Paragraph("step-3", "第三步：代入计算。"))), origin = SolutionOrigin.MODEL, verification = SolutionVerification.UNVERIFIED, teachingFigureOmission = TeachingFigureOmission("complex structure", "natural morphology requires image model", "image model unavailable"), imageModelUnavailableAtPreparation = true)

    @Test fun pdfContainsPaperOrdinalsWithoutProvenanceOrSpokenFormulaLines() {
        val input = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.PDF).toNativeInput()
        val plan = MistakePdfPagePlanner.plan(input, PdfTextMeasure { _, start, end, _ -> (end - start) * 6f })
        val lines = plan.pages.flatten().filterIsInstance<PdfPlannedItem.Line>().map { it.text }
        assertTrue(lines.contains("第 1 题"))
        listOf("来源", "第 1 版", "智能体生成", "未经独立核对", "读作", "complex structure", "image model unavailable").forEach { unwanted ->
            assertTrue(lines.none { it.contains(unwanted) })
        }
    }

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
            listOf("来源", "第 1 版", "智能体生成", "未经独立核对", "读作", "本图由 AI 生成", "与题目卷题号一致").forEach {
                assertFalse("Unexpected answer boilerplate: $it", xml.contains(it))
            }
            assertEquals(MistakeExportArtifactRole.DETAILED_ANSWERS, input.artifactRole)
        } finally { file.delete(); directory.delete() }
    }

    @Test fun differentFormatOrWrongBasisCannotReuseAnAnswerArtifact() {
        val pdf = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.PDF).toNativeInput()
        val word = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.DOCX).toNativeInput()
        assertNotEquals(pdf.inputSha256, word.inputSha256)
        assertTrue(runCatching { DetailedAnswerExportInput(manifest, listOf(document.copy(basis = basis.copy(questionSha256 = "b".repeat(64)))), MistakeExportFormat.PDF) }.isFailure)
    }
    @Test fun missingIllustrationCannotExportAndOmissionIsVisible() {
        assertTrue(runCatching { DetailedAnswerExportInput(manifest, listOf(document.copy(teachingFigureOmission = null)), MistakeExportFormat.DOCX) }.isFailure)
        val input = DetailedAnswerExportInput(manifest, listOf(document), MistakeExportFormat.DOCX).toNativeInput()
        assertNotNull(input)
    }
}
