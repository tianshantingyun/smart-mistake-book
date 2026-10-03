package com.tingyun.smartmistakebook.core.export

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B6 的像素层证据：role=CLEAN_IMAGE 的重绘资产必须**真的画进导出的 PDF**，而不只是
 * 出现在导出输入的 `cleanImageLocalUri` 字段里（投影层断言由 `MistakePdfEligibilityTest`
 * 覆盖；无重绘图的回退分支也在那里）。
 *
 * 做法：写一张真实 PNG（纯深色实心块）→ 作为 CLEAN_IMAGE 资产 → 生成 PDF → 渲染第 0 页，
 * 在"干净题面"图块应占据的像素带里数非白像素：有重绘图的样本应几乎全为深色，无重绘图的
 * 同一带应几乎全白（对照证明深色不是标题/正文造成的）。
 */
@RunWith(AndroidJUnit4::class)
class MistakePdfCleanImageExportInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aCleanImageAssetIsActuallyDrawnIntoTheExportedPdf() {
        val png = File(context.cacheDir, "clean-redraw-${UUID.randomUUID()}.png")
        writeSolidPng(png, width = 200, height = 100)
        try {
            val withImage = prepare(
                source = MistakeSourceSet.Present(
                    listOf(
                        MistakeSourceAsset(
                            role = CLEAN_IMAGE_SOURCE_ROLE,
                            sourceAssetId = "redraw-${UUID.randomUUID()}",
                            contentSha256 = sha256File(png),
                            mimeType = "image/png",
                            byteSize = png.length(),
                            width = 200,
                            height = 100,
                            sourceType = "GENERATED_FIGURE",
                            createdAtEpochMillis = png.lastModified(),
                            location = MistakeSourceLocation.Available("file://${png.absolutePath}"),
                        ),
                    ),
                ),
            )
            val withoutImage = prepare(source = MistakeSourceSet.Missing)

            assertEquals(
                "file://${png.absolutePath}",
                withImage.inputCleanImageUri,
            )
            val drawnRatio = inkRatioInCleanImageBand(withImage.prepared)
            val controlRatio = inkRatioInCleanImageBand(withoutImage.prepared)
            assertTrue(
                "干净题面重绘图必须真的画进 PDF：bandInk=$drawnRatio（对照=$controlRatio）",
                drawnRatio > 0.9,
            )
            assertTrue(
                "无重绘图时同一像素带必须接近空白（证明深色来自重绘图而非标题/正文）：$controlRatio",
                controlRatio < 0.05,
            )
        } finally {
            png.delete()
        }
    }

    // ---- pixel inspection ----

    /**
     * 干净题面图块在默认版式下占据的像素带：页边距 48 + 标题 28 + 副标题 16 + 间隔 8 +
     * "干净题面"标题 22 = 图块顶 122；图块高 = 100 × 499/200 = 249.5，底 371.5。
     * 取 160..340（带内）并避开任何文字行。
     */
    private fun inkRatioInCleanImageBand(prepared: PreparedMistakePdf): Double =
        MistakePdfPreview.open(prepared).use { preview ->
            val bitmap = preview.renderPage(0, 595, 842)
            try {
                var nonWhite = 0
                var total = 0
                for (y in 160 until 340 step 2) {
                    for (x in 60 until 540 step 2) {
                        total++
                        if (bitmap.getPixel(x, y) != Color.WHITE) nonWhite++
                    }
                }
                nonWhite.toDouble() / total.toDouble()
            } finally {
                bitmap.recycle()
            }
        }

    private fun writeSolidPng(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(20, 20, 20))
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    // ---- fixtures ----

    private data class PreparedFixture(
        val prepared: PreparedMistakePdf,
        val inputCleanImageUri: String?,
    )

    private fun prepare(source: MistakeSourceSet): PreparedFixture {
        val unique = UUID.randomUUID().toString()
        val blocks = listOf(ContentBlock.Paragraph("stem", "题干文字在重绘图的下面。"))
        val state = MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-$unique",
                    problemId = "problem-$unique",
                    problemRevisionId = "revision-$unique",
                    revisionNumber = 1,
                    title = "干净图导出用例",
                    subject = "数学",
                ),
                fallbackMarkdown = "fallback must never be used",
                source = source,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$unique",
                    title = "干净图导出用例",
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
        val input = (
            MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible
            ).input
        return PreparedFixture(
            prepared = MistakePdfExporter(context).prepare(input),
            inputCleanImageUri = input.cleanImageLocalUri,
        )
    }
}
