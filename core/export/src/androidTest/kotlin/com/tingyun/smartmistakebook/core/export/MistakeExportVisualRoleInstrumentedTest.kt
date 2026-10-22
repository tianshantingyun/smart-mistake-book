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
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E WG-07 的**像素层**证据：讲解图（role=TUTOR_EXPLANATION）不得画进题目区。
 *
 * 做法：题目图 = 纯红 PNG，讲解图 = 纯蓝 PNG，各自带真实记录（sha/长度）落成
 * `problem_revision_source_asset` 形状的资产 → 过资格层 → 生成真实 PDF → 逐页渲染像素。
 *
 * - 不请求解析区：整份 PDF 不得出现任何蓝色像素（讲解图没有被画进去）；
 * - 请求解析区：蓝色像素必须存在，且**第一个蓝像素在最后一个红像素之后**——题目区在上、
 *   解析区在下，讲解图没有串到题目位置。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportVisualRoleInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun anExplanationImageIsNotDrawnIntoTheQuestionAreaWhenTheSolutionAreaIsOff() {
        val fixture = coloredFixture()
        try {
            val input = (MistakePdfEligibility.check(fixture.state) as
                MistakePdfEligibilityResult.Eligible).input
            val pdf = MistakePdfExporter(context).prepare(input)
            val pixels = scan(pdf)

            assertTrue("题目图必须真的画进 PDF：red=${pixels.red}", pixels.red > 1_000)
            assertEquals("不请求解析区时讲解图不得出现在任何一页", 0, pixels.blue)
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun aRequestedSolutionSectionDrawsTheExplanationImageAfterTheQuestionArea() {
        val fixture = coloredFixture()
        try {
            val layout = MistakePdfLayout(includeSolution = true)
            val input = (MistakePdfEligibility.check(fixture.state, layout) as
                MistakePdfEligibilityResult.Eligible).input
            assertEquals(1, input.solutionImages.size)
            val pdf = MistakePdfExporter(context).prepare(input)
            val pixels = scan(pdf)

            assertTrue("题目图必须仍在：red=${pixels.red}", pixels.red > 1_000)
            assertTrue("请求解析区后讲解图必须真的画进 PDF：blue=${pixels.blue}", pixels.blue > 1_000)
            assertTrue(
                "讲解图必须排在题目图之后（firstBlue=${pixels.firstBlueY}，" +
                    "lastRed=${pixels.lastRedY}）",
                pixels.firstBlueY > pixels.lastRedY,
            )
        } finally {
            fixture.delete()
        }
    }

    // ---- pixel inspection ----

    private data class PixelScan(
        val red: Int,
        val blue: Int,
        val firstBlueY: Int,
        val lastRedY: Int,
    )

    /** 逐页渲染并统计纯红/纯蓝像素；y 用"页号 × 页高 + 页内 y"拼成全局坐标。 */
    private fun scan(prepared: PreparedMistakePdf): PixelScan {
        var red = 0
        var blue = 0
        var firstBlueY = Int.MAX_VALUE
        var lastRedY = -1
        MistakePdfPreview.open(prepared).use { preview ->
            repeat(preview.pageCount) { page ->
                val bitmap = preview.renderPage(page, 595, 842)
                try {
                    for (y in 0 until bitmap.height step 2) {
                        for (x in 0 until bitmap.width step 2) {
                            val color = bitmap.getPixel(x, y)
                            val globalY = page * bitmap.height + y
                            if (
                                Color.red(color) > 180 &&
                                Color.green(color) < 70 &&
                                Color.blue(color) < 70
                            ) {
                                red++
                                if (globalY > lastRedY) lastRedY = globalY
                            }
                            if (
                                Color.blue(color) > 180 &&
                                Color.red(color) < 70 &&
                                Color.green(color) < 70
                            ) {
                                blue++
                                if (globalY < firstBlueY) firstBlueY = globalY
                            }
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        return PixelScan(red, blue, firstBlueY, lastRedY)
    }

    // ---- fixture ----

    private data class ColoredFixture(
        val state: MistakeDetailState.Ready,
        val files: List<File>,
    ) {
        fun delete() = files.forEach(File::delete)
    }

    private fun coloredFixture(): ColoredFixture {
        val unique = UUID.randomUUID().toString()
        val questionImage = File(context.cacheDir, "role-question-$unique.png").also {
            writeSolidPng(it, color = Color.rgb(220, 30, 30))
        }
        val explanationImage = File(context.cacheDir, "role-explanation-$unique.png").also {
            writeSolidPng(it, color = Color.rgb(30, 30, 220))
        }
        val blocks = listOf(ContentBlock.Paragraph("stem", "题干文字。"))
        val state = MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-role-$unique",
                    problemId = "problem-role-$unique",
                    problemRevisionId = "revision-role-$unique",
                    revisionNumber = 1,
                    title = "角色过滤用例",
                    subject = "数学",
                ),
                fallbackMarkdown = "fallback must never be used",
                source = MistakeSourceSet.Present(
                    listOf(
                        sourceAsset(VisualRole.CLEAN_IMAGE, "question", questionImage),
                        sourceAsset(VisualRole.TUTOR_EXPLANATION, "explanation", explanationImage),
                    ),
                ),
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-role-$unique",
                    title = "角色过滤用例",
                    blocks = blocks,
                ),
                blockEvidence = blocks.map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-role-$unique",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            ),
        )
        return ColoredFixture(state, listOf(questionImage, explanationImage))
    }

    private fun sourceAsset(
        role: VisualRole,
        sourceAssetId: String,
        file: File,
    ): MistakeSourceAsset = MistakeSourceAsset(
        role = role.name,
        sourceAssetId = "$sourceAssetId-${UUID.randomUUID()}",
        contentSha256 = sha256File(file),
        mimeType = "image/png",
        byteSize = file.length(),
        width = 200,
        height = 60,
        sourceType = "GENERATED_FIGURE",
        createdAtEpochMillis = 1L,
        location = MistakeSourceLocation.Available(file.toURI().toString()),
    )

    private fun writeSolidPng(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(200, 60, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
