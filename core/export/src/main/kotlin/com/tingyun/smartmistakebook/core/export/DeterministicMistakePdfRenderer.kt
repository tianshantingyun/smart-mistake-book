package com.tingyun.smartmistakebook.core.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
 * 本对象只做两件 android.graphics 的事：把 [PdfLineStyle]（经 [PdfTypography] 缩放）
 * 映射成 `Paint`，以及按计划把行/公式/图形/图片/作答空白区画到 `PdfDocument` 的
 * `Canvas` 上。
 *
 * 版式（页边距/字号/栏数/图形缩放）来自 [MistakePdfExportInput.layout]；默认版式
 * （compact + 全默认常数）下与参数化前逐位一致：页宽 595/页高 842 不变，四边 48pt，
 * 单栏，字号因子 1.0。
 */
internal object DeterministicMistakePdfRenderer {
    private const val MAX_CLEAN_IMAGE_EDGE_PX = 2000
    private const val ANSWER_BLANK_STROKE_PX = 0.7f
    private const val ANSWER_BLANK_VERTICAL_INSET_PX = 2f

    fun render(input: MistakePdfExportInput, output: File) {
        val cleanImage = input.cleanImageLocalUri?.let(::decodeCleanImage)
        val typography = PdfTypography.forFontScale(input.layout.fontScale)
        val geometry = PdfPageGeometry.of(input.layout, typography)
        val plan = MistakePdfPagePlanner.plan(
            input = input,
            measure = PaintTextMeasure(typography),
            cleanImageSize = cleanImage?.let { bitmap ->
                PdfImageSize(widthPx = bitmap.width, heightPx = bitmap.height)
            },
        )
        val blankPaint = answerBlankPaint()
        val document = PdfDocument()
        try {
            plan.pages.forEachIndexed { pageIndex, pageItems ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    MistakePdfPagePlanner.PAGE_WIDTH,
                    MistakePdfPagePlanner.PAGE_HEIGHT,
                    pageIndex + 1,
                ).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                var top = geometry.marginPt
                var currentColumn = -1
                pageItems.forEach { item ->
                    if (item.column != currentColumn) {
                        currentColumn = item.column
                        top = geometry.marginPt
                    }
                    val left = geometry.marginPt +
                        item.column * (geometry.columnWidthPt + MistakePdfPagePlanner.COLUMN_GAP)
                    when (item) {
                        is PdfPlannedItem.Line -> {
                            top += item.height
                            page.canvas.drawText(
                                item.text,
                                left,
                                top,
                                paintFor(item.style, typography),
                            )
                        }
                        is PdfPlannedItem.Formula -> {
                            val metrics = MathMetrics.of(item.fontSizePx)
                            CanvasMathBoxRenderer.drawBox(
                                canvas = page.canvas,
                                box = item.box,
                                origin = CanvasMathBoxRenderer.CanvasPoint(
                                    left,
                                    top + (item.height - item.box.height) / 2f,
                                ),
                                color = Color.rgb(42, 45, 43),
                                strokeWidth = maxOf(1f, item.fontSizePx * 0.0625f),
                                metrics = metrics,
                            )
                            top += item.height
                        }
                        is PdfPlannedItem.Figure -> {
                            val bounds = RectF(left, top, left + item.width, top + item.height)
                            DeterministicPdfFigureRenderer.draw(page.canvas, item.block, bounds)
                            top += item.height
                        }
                        is PdfPlannedItem.Image -> {
                            if (cleanImage != null) {
                                val bounds = RectF(left, top, left + item.width, top + item.height)
                                page.canvas.drawBitmap(
                                    cleanImage,
                                    null,
                                    bounds,
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                                )
                            }
                            top += item.height
                        }
                        is PdfPlannedItem.AnswerBlank -> {
                            drawAnswerBlank(
                                canvas = page.canvas,
                                left = left,
                                top = top,
                                width = geometry.columnWidthPt,
                                item = item,
                                paint = blankPaint,
                            )
                            top += item.height
                        }
                    }
                }
                val footer = "第 ${pageIndex + 1} / ${plan.pageCount} 页"
                val footerPaint = paintFor(PdfLineStyle.FOOTER, typography)
                page.canvas.drawText(
                    footer,
                    MistakePdfPagePlanner.PAGE_WIDTH - geometry.marginPt -
                        footerPaint.measureText(footer),
                    MistakePdfPagePlanner.PAGE_HEIGHT - geometry.marginPt / 2f,
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

    /** 作答空白区：细线框 + 每个书写行之间的横线（练习卷一眼可数行数）。 */
    private fun drawAnswerBlank(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        item: PdfPlannedItem.AnswerBlank,
        paint: Paint,
    ) {
        val boxTop = top + ANSWER_BLANK_VERTICAL_INSET_PX
        val boxBottom = top + item.height - ANSWER_BLANK_VERTICAL_INSET_PX
        canvas.drawRect(left, boxTop, left + width, boxBottom, paint)
        val rowHeight = (boxBottom - boxTop) / item.rows
        for (row in 1 until item.rows) {
            val y = boxTop + rowHeight * row
            canvas.drawLine(left, y, left + width, y, paint)
        }
    }

    private fun answerBlankPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 45, 43)
        style = Paint.Style.STROKE
        strokeWidth = ANSWER_BLANK_STROKE_PX
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

    /** Paint 侧的折行宽度读口：与绘制用同一组 Paint（同字号缩放、同 typeface）。 */
    private class PaintTextMeasure(
        private val typography: PdfTypography,
    ) : PdfTextMeasure {
        private val paints = HashMap<PdfLineStyle, Paint>()

        override fun measure(text: String, start: Int, end: Int, style: PdfLineStyle): Float =
            paints.getOrPut(style) { paintFor(style, typography) }.measureText(text, start, end)
    }

    private fun paintFor(style: PdfLineStyle, typography: PdfTypography) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 45, 43)
            textSize = typography.textSize(style)
            typeface = when (style) {
                PdfLineStyle.TITLE,
                PdfLineStyle.SECTION_HEADING,
                -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                PdfLineStyle.FORMULA -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                else -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
        }
}
