package com.tingyun.smartmistakebook.core.export

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcMetadata
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcReader
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-85 设备侧：**自然语言改卷**产生的模型文字必须让真实导出带上文档级 `/AIGC`。
 *
 * 链路与生产一致：纯用户来源（`USER_CORRECTION`）→ `MistakePdfPaperEdit` 改文字 →
 * `TutorPaperEditApplier` 置 `modelTextApplied` 的同一状态位（此处用公开的
 * `withModelTextApplied()` 复现；applier 的置位规则由 JVM 用例 `TutorPaperEditModelTextTest`
 * 钉住）→ `toDraft()` → `MistakePdfExporter.prepare` → `PdfAigcReader` 读回。
 *
 * 负向同链：同一份卷面**手工**改字（不置位）导出必须读不到 `/AIGC`——模型文字与用户文字
 * 不能混成一个标记（《办法》第十条）。
 *
 * 独立读回：产物写到 `files/pdf-aigc-export/run-<stamp>/kd85-*.pdf`，宿主侧用 pypdf 复读
 * （命令见类 KDoc 的 evidence 行）。
 */
@RunWith(AndroidJUnit4::class)
class MistakePdfPaperEditAigcInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val evidenceDirectory: File by lazy {
        val directory = File(context.filesDir, "pdf-aigc-export/run-$RUN_STAMP")
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
            "cannot create evidence directory $directory"
        }
        directory
    }

    @Test
    fun modelEditedPureUserPaperIsLabeledOnTheRealExportChain() {
        val paper = MistakePdfPaperEdit.fromInputs(
            inputs = listOf(plainInput()),
            layout = MistakePdfLayout.DEFAULT,
        )
        val entryId = paper.questions.single().entryId
        val draft = paper
            .withBlockText(entryId, "stem", "模型改写后的题干")
            .withModelTextApplied()
            .toDraft()

        assertTrue(
            "纯用户来源 + 模型改卷后，导出输入必须判为含模型文本（KD-85）",
            draft.input.hasModelParsedText,
        )

        val prepared = MistakePdfExporter(context).prepare(draft.input)
        assertTrue(prepared.verifyIntegrity())
        val bytes = prepared.file.readBytes()
        val readBack = PdfAigcReader.readLatestAigc(bytes)

        assertNotNull("模型改卷后的真实导出必须带文档级 /AIGC", readBack)
        assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, readBack!!.label)
        assertEquals(draft.input.inputSha256, readBack.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(bytes))

        val evidence = File(evidenceDirectory, "kd85-model-edited-labeled.pdf")
        evidence.writeBytes(bytes)
        Log.i(
            TAG,
            "case=kd85-model-edited labeled=${bytes.size} pages=${prepared.pageCount} " +
                "sha256=${prepared.sha256} evidence=${evidence.absolutePath}",
        )
    }

    @Test
    fun manualEditOfTheSamePaperStaysUnlabelled() {
        val paper = MistakePdfPaperEdit.fromInputs(
            inputs = listOf(plainInput()),
            layout = MistakePdfLayout.DEFAULT,
        )
        val draft = paper
            .withBlockText(paper.questions.single().entryId, "stem", "学生自己改的题干")
            .toDraft()

        assertFalse(
            "手工编辑不是模型文本；为它写 /AIGC 是伪造标识",
            draft.input.hasModelParsedText,
        )

        val prepared = MistakePdfExporter(context).prepare(draft.input)
        val bytes = prepared.file.readBytes()
        assertNull(PdfAigcReader.readLatestAigc(bytes))

        val evidence = File(evidenceDirectory, "kd85-manual-edited-unlabeled.pdf")
        evidence.writeBytes(bytes)
        Log.i(
            TAG,
            "case=kd85-manual-edited labeled=false bytes=${bytes.size} " +
                "sha256=${prepared.sha256} evidence=${evidence.absolutePath}",
        )
    }

    // ---- fixtures (与既有 AIGC 设备用例同一来源形状) ----

    private fun plainInput(): MistakePdfExportInput = (
        MistakePdfEligibility.check(
            readyState(listOf(ContentBlock.Paragraph("stem", "纯用户录入的题干"))),
        ) as MistakePdfEligibilityResult.Eligible
        ).input

    private fun readyState(blocks: List<ContentBlock>): MistakeDetailState.Ready {
        val unique = UUID.randomUUID().toString()
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-$unique",
                    problemId = "problem-$unique",
                    problemRevisionId = "revision-$unique",
                    revisionNumber = 1,
                    title = "KD-85 改卷导出用例",
                    subject = "数学",
                ),
                fallbackMarkdown = "fallback must never be used",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$unique",
                    title = "KD-85 改卷导出用例",
                    blocks = blocks,
                ),
                blockEvidence = blocks.map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-$unique",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            ),
        )
    }

    private companion object {
        const val TAG = "SmbAigcKd85"
        val RUN_STAMP: String = "kd85-" + System.currentTimeMillis()
    }
}
