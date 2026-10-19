package com.tingyun.smartmistakebook.core.export

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **A8 空源题面导出不崩**：手动建题的题面没有照片。导出输入不依赖源图
 * （`MistakeSourceSet.Missing` → 无 clean image、无原图），仍须能通过资格检查并真的
 * 渲染出一份 PDF。
 *
 * 夹具的证据形态与手动建题落库的一致：`USER_TRANSCRIPTION` + 空白 source_asset_id +
 * 无区域（这是合法提交形态，不是"丢了一张图"）。
 */
@RunWith(AndroidJUnit4::class)
class MistakePdfMissingSourceInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manualTranscriptionWithoutSourceImageExportsAPdf() {
        val state = MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "manual-entry",
                    problemId = "manual-problem",
                    problemRevisionId = "manual-revision",
                    revisionNumber = 1,
                    title = "手敲的函数题",
                    subject = "数学",
                ),
                fallbackMarkdown = "手动建题的题面以结构化文档为准",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-manual",
                    title = "手敲的函数题",
                    blocks = listOf(
                        ContentBlock.Paragraph("stem", "求函数 f(x)=x^2 的单调区间。"),
                    ),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = "stem",
                        sourceAssetId = "",
                        sourceRegion = null,
                        writingLayer = WritingLayer.UNKNOWN,
                        provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )

        val eligibility = MistakePdfEligibility.check(state)
        val eligible = eligibility as MistakePdfEligibilityResult.Eligible
        assertEquals(1, eligible.input.blocks.size)

        val prepared = MistakePdfExporter(context).prepare(eligible.input)
        assertTrue(prepared.file.isFile)
        assertTrue(prepared.file.length() > 0)
        assertTrue(prepared.pageCount >= 1)
        assertTrue(prepared.verifyIntegrity())
    }
}
