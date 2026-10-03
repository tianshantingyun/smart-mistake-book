package com.tingyun.smartmistakebook.core.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureAxis
import com.tingyun.smartmistakebook.core.model.FigureCoordinate
import com.tingyun.smartmistakebook.core.model.FigurePolyline
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B4 的三链路回归（仪器化）：预览 / 打印 / 部分页重渲染在参数化版式下仍同源。
 *
 * 1. 预览第 0 页的位图尺寸与 A4 aspect 不随版式参数变化（页宽/页高是固定的 A4）；
 * 2. 打印"整份"请求必须**逐字节**等于 prepared 文件（真读两边字节再比对，不是比长度）；
 * 3. 只请求第 N 页时，输出只有 1 页、且内容确实来自源第 N 页（用最后一页的稠密图形
 *    与第一页的文字做墨迹对照；这是"环境相关"的退化判据，页面数是精确断言）。
 */
@RunWith(AndroidJUnit4::class)
class MistakePdfDeliveryRegressionInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun previewPageZeroKeepsA4SizeAndAspectUnderParameterizedLayouts() {
        val defaultPrepared = prepare(MistakePdfLayout.DEFAULT)
        val parameterizedPrepared = prepare(
            MistakePdfLayout(
                templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                marginPt = 72,
                fontScale = 3,
                columnCount = 2,
                imageScale = 0.8f,
                includeAnswer = true,
            ),
        )

        listOf(defaultPrepared, parameterizedPrepared).forEach { prepared ->
            MistakePdfPreview.open(prepared).use { preview ->
                val bitmap = preview.renderPage(0, 595, 842)
                try {
                    assertEquals(595, bitmap.width)
                    assertEquals(842, bitmap.height)
                    assertEquals(595f / 842f, bitmap.width.toFloat() / bitmap.height, 0.0001f)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun printingTheWholeDocumentStreamsExactlyThePreparedBytes() {
        val prepared = prepare(MistakePdfLayout.DEFAULT)
        val destination = File(context.cacheDir, "print-whole-${UUID.randomUUID()}.pdf")

        assertEquals(
            "整份请求必须展开为全部页",
            (0 until prepared.pageCount).toList(),
            PreparedPdfPrintWriter.requestedPageIndexes(
                arrayOf(PageRange.ALL_PAGES),
                prepared.pageCount,
            ),
        )
        withDestination(destination) { descriptor ->
            val digest = PreparedPdfPrintWriter.copyWhole(
                prepared,
                descriptor,
                CancellationSignal(),
            )
            assertEquals("打印复制出的字节摘要必须等于 prepared 文件摘要", prepared.sha256, digest)
        }

        assertEquals(prepared.file.length(), destination.length())
        assertArrayEquals(prepared.file.readBytes(), destination.readBytes())
        assertEquals(prepared.sha256, sha256File(destination))
    }

    @Test
    fun printingAPartialRangeReRendersOnlyTheRequestedPage() {
        val prepared = prepare(MistakePdfLayout.DEFAULT, multiPageFixture())
        assertTrue("夹具必须至少三页", prepared.pageCount >= 3)
        val lastIndex = prepared.pageCount - 1
        val destination = File(context.cacheDir, "print-subset-${UUID.randomUUID()}.pdf")

        val requested = PreparedPdfPrintWriter.requestedPageIndexes(
            arrayOf(PageRange(lastIndex, lastIndex)),
            prepared.pageCount,
        )
        assertEquals(listOf(lastIndex), requested)
        withDestination(destination) { descriptor ->
            PreparedPdfPrintWriter.writeSubset(
                prepared = prepared,
                pageIndexes = checkNotNull(requested),
                destination = descriptor,
                cancellationSignal = CancellationSignal(),
            )
        }

        assertEquals("只请求一页时输出必须只有一页", 1, pageCountOf(destination))
        val subsetInk = inkRatio(destination, pageIndex = 0)
        val fullLastInk = inkRatio(prepared.file, pageIndex = lastIndex)
        val fullFirstInk = inkRatio(prepared.file, pageIndex = 0)
        assertTrue("请求页必须真的画上了内容：ink=$subsetInk", subsetInk > 0.0005)
        assertTrue(
            "部分页重渲染必须来自请求的最后一页（稠密图形）而非第一页：" +
                "subset=$subsetInk first=$fullFirstInk last=$fullLastInk",
            subsetInk > fullFirstInk * 2,
        )
        assertTrue(
            "部分页墨迹应与源最后一页同量级：subset=$subsetInk last=$fullLastInk",
            subsetInk in (fullLastInk * 0.4)..(fullLastInk * 2.5),
        )
    }

    // ---- print writer driver ----

    private fun <T> withDestination(
        file: File,
        block: (ParcelFileDescriptor) -> T,
    ): T {
        val descriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE,
        )
        return try {
            block(descriptor)
        } finally {
            // 打印写入实现会经 FileOutputStream 关掉这个 fd；重复 close 必须无害。
            runCatching { descriptor.close() }
        }
    }

    // ---- pdf inspection ----

    private fun pageCountOf(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }

    /** 采样墨迹占比（每 3 像素取一点，避免逐像素拖慢仪器化用例）。 */
    private fun inkRatio(file: File, pageIndex: Int): Double =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        var nonWhite = 0L
                        var total = 0L
                        for (y in 0 until bitmap.height step 3) {
                            for (x in 0 until bitmap.width step 3) {
                                total++
                                if (bitmap.getPixel(x, y) != Color.WHITE) nonWhite++
                            }
                        }
                        nonWhite.toDouble() / total.toDouble()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }

    // ---- fixtures ----

    private fun prepare(
        layout: MistakePdfLayout,
        blocks: List<ContentBlock> = defaultFixture(),
    ): PreparedMistakePdf {
        val unique = UUID.randomUUID().toString()
        val state = MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-$unique",
                    problemId = "problem-$unique",
                    problemRevisionId = "revision-$unique",
                    revisionNumber = 1,
                    title = "交付回归用例",
                    subject = "数学",
                ),
                fallbackMarkdown = "fallback must never be used",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$unique",
                    title = "交付回归用例",
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
            MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible
            ).input
        return MistakePdfExporter(context).prepare(input)
    }

    private fun defaultFixture(): List<ContentBlock> = listOf(
        ContentBlock.Paragraph("stem", "已知函数满足 \$f(x)=x^2-2x\$。"),
        ContentBlock.ChoiceGroup(
            id = "choice",
            promptMarkdown = "函数在哪个区间递增？",
            choices = listOf(
                StructuredChoice("a", "负无穷到 1"),
                StructuredChoice("b", "1 到正无穷"),
            ),
            selectedChoiceId = "b",
        ),
    )

    /**
     * 多页夹具：第一页短文，中间用换行强行翻页，最后一页是网格图形（可辨认来源页）。
     *
     * 网格必须遵守 `StructuredContentLimits`（polyline ≤ 16）：16 条线（8 横 + 8 纵）已
     * 足够与第一页的文字做墨迹对照，超过上限会让资格判定直接 Ineligible。
     */
    private fun multiPageFixture(): List<ContentBlock> = listOf(
        ContentBlock.Paragraph("first", "第一页只有一句话。"),
        ContentBlock.Paragraph("spacer", "断页\n" + "\n".repeat(120)),
        ContentBlock.Figure(
            id = "dense",
            title = "稠密网格",
            alternativeText = "由多行多列直线组成的网格",
            schema = FigureSchema.Cartesian(
                xAxis = FigureAxis(-2.0, 2.0, "x"),
                yAxis = FigureAxis(-2.0, 2.0, "y"),
                polylines = buildList {
                    for (index in 0..7) {
                        val value = -2.0 + index * (4.0 / 7.0)
                        add(
                            FigurePolyline(
                                id = "h$index",
                                points = listOf(
                                    FigureCoordinate(-2.0, value),
                                    FigureCoordinate(2.0, value),
                                ),
                            ),
                        )
                        add(
                            FigurePolyline(
                                id = "v$index",
                                points = listOf(
                                    FigureCoordinate(value, -2.0),
                                    FigureCoordinate(value, 2.0),
                                ),
                            ),
                        )
                    }
                },
            ),
        ),
    )
}
