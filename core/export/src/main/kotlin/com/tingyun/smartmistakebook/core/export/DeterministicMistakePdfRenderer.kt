package com.tingyun.smartmistakebook.core.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tingyun.smartmistakebook.core.model.MathMetrics
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 把一份 [MistakePdfExportInput] 画成 A4 PDF。
 *
 * **分页与折行不在这里**：全部交给 [MistakePdfPagePlanner]（纯 Kotlin、JVM 可测）。
 * 本对象只做两件 android.graphics 的事：把 [PdfLineStyle] 映射成 `Paint`（经
 * [PaintTextMeasure] 提供宽度），以及按计划把行/公式/图形/图片画到 `PdfDocument` 的
 * `Canvas` 上。行为与抽取前逐位一致（页宽/边距/字色都是原常量）。
 */
internal object DeterministicMistakePdfRenderer {
    private const val MAX_CLEAN_IMAGE_EDGE_PX = 2000

    fun render(input: MistakePdfExportInput, output: File) {
        val cleanImage = input.cleanImageLocalUri?.let(::decodeCleanImage)
        val plan = MistakePdfPagePlanner.plan(
            input = input,
            measure = PaintTextMeasure(),
            cleanImageSize = cleanImage?.let { bitmap ->
                PdfImageSize(widthPx = bitmap.width, heightPx = bitmap.height)
            },
        )
        val document = PdfDocument()
        try {
            plan.forEachIndexed { pageIndex, pageItems ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    MistakePdfPagePlanner.PAGE_WIDTH,
                    MistakePdfPagePlanner.PAGE_HEIGHT,
                    pageIndex + 1,
                ).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                var top = MistakePdfPagePlanner.TOP
                pageItems.forEach { item ->
                    when (item) {
                        is PdfPlannedItem.Line -> {
                            top += item.style.lineHeight
                            page.canvas.drawText(item.text, MistakePdfPagePlanner.LEFT, top, paintFor(item.style))
                        }
                        is PdfPlannedItem.Formula -> {
                            val metrics = MathMetrics.of(item.fontSizePx)
                            CanvasMathBoxRenderer.drawBox(
                                canvas = page.canvas,
                                box = item.box,
                                origin = CanvasMathBoxRenderer.CanvasPoint(
                                    MistakePdfPagePlanner.LEFT,
                                    top + (item.height - item.box.height) / 2f,
                                ),
                                color = Color.rgb(42, 45, 43),
                                strokeWidth = maxOf(1f, item.fontSizePx * 0.0625f),
                                metrics = metrics,
                            )
                            top += item.height
                        }
                        is PdfPlannedItem.Figure -> {
                            val bounds = RectF(
                                MistakePdfPagePlanner.LEFT,
                                top,
                                MistakePdfPagePlanner.PAGE_WIDTH - MistakePdfPagePlanner.RIGHT,
                                top + item.height,
                            )
                            DeterministicPdfFigureRenderer.draw(page.canvas, item.block, bounds)
                            top += item.height
                        }
                        is PdfPlannedItem.Image -> {
                            if (cleanImage != null) {
                                val bounds = RectF(
                                    MistakePdfPagePlanner.LEFT,
                                    top,
                                    MistakePdfPagePlanner.PAGE_WIDTH - MistakePdfPagePlanner.RIGHT,
                                    top + item.height,
                                )
                                page.canvas.drawBitmap(
                                    cleanImage,
                                    null,
                                    bounds,
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                                )
                            }
                            top += item.height
                        }
                    }
                }
                val footer = "第 ${pageIndex + 1} / ${plan.size} 页"
                val footerPaint = paintFor(PdfLineStyle.FOOTER)
                page.canvas.drawText(
                    footer,
                    MistakePdfPagePlanner.PAGE_WIDTH - MistakePdfPagePlanner.RIGHT -
                        footerPaint.measureText(footer),
                    MistakePdfPagePlanner.PAGE_HEIGHT - MistakePdfPagePlanner.BOTTOM / 2f,
                    footerPaint,
                )
                document.finishPage(page)
            }
            FileOutputStream(output).use { fileOutput ->
                val buffered = BufferedOutputStream(fileOutput)
                document.writeTo(buffered)
                buffered.flush()
                fileOutput.fd.sync()
            }
        } finally {
            document.close()
        }
    }

    private fun decodeCleanImage(uri: String): Bitmap? {
        val path = uri.removePrefix("file://")
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longEdge = max(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (longEdge / sampleSize > MAX_CLEAN_IMAGE_EDGE_PX) sampleSize *= 2
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }.getOrNull()
    }

    /** Paint 侧的折行宽度读口：与绘制用同一组 Paint（同字号、同 typeface）。 */
    private class PaintTextMeasure : PdfTextMeasure {
        private val paints = HashMap<PdfLineStyle, Paint>()

        override fun measure(text: String, start: Int, end: Int, style: PdfLineStyle): Float =
            paints.getOrPut(style) { paintFor(style) }.measureText(text, start, end)
    }

    private fun paintFor(style: PdfLineStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 45, 43)
        textSize = style.textSize
        typeface = when (style) {
            PdfLineStyle.TITLE,
            PdfLineStyle.SECTION_HEADING,
            -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            PdfLineStyle.FORMULA -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }
}
