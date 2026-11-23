package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.model.ModelEgressAssetGrant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface TutorFigureInspector {
    suspend fun inspect(assetId: String): TutorFigureFeedback
}

data class TutorFigureFeedback(val dataJson: String, val image: ModelEgressAssetGrant?)

/** Checks canonical bytes and samples actual pixels. Semantic judgment belongs to the next model call. */
private class AndroidTutorFigureInspector(context: Context, private val database: StudyDatabasePort) : TutorFigureInspector {
    private val vault = AndroidCanonicalAssetVault(context.applicationContext)

    override suspend fun inspect(assetId: String): TutorFigureFeedback = withContext(Dispatchers.IO) {
        try {
            val record = database.readCanonicalSourceAsset(assetId)
                ?: return@withContext unavailable(assetId, "ASSET_UNAVAILABLE")
            val file = vault.resolve(record)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            check(bounds.outWidth == record.width && bounds.outHeight == record.height && bounds.outMimeType == record.mimeType) {
                "Canonical image dimensions or MIME changed"
            }
            var sample = 1
            while (maxOf(record.width, record.height) / sample > 256) sample *= 2
            val preview = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return@withContext unavailable(assetId, "PIXELS_UNDECODABLE")
            try {
                val grant = ModelEgressAssetGrant(record.sourceAssetId, record.contentSha256,
                    record.byteSize, record.width, record.height)
                TutorFigureFeedback(buildJsonObject {
                    put("assetId", record.sourceAssetId)
                    put("width", record.width)
                    put("height", record.height)
                    put("mimeType", record.mimeType)
                    put("sha256", record.contentSha256)
                    put("byteSize", record.byteSize)
                    put("pixelStatus", "DECODED")
                    put("semanticReview", "MODEL_REVIEW_REQUIRED")
                    val summary = previewSummary(preview)
                    put("acceptance", figureAcceptanceJson(summary))
                    put("preview", previewStatistics(summary))
                }.toString(), grant)
            } finally {
                preview.recycle()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: OutOfMemoryError) {
            unavailable(assetId, "PREVIEW_MEMORY_LIMIT")
        } catch (_: Exception) {
            unavailable(assetId, "ASSET_INTEGRITY_OR_DECODE_FAILED")
        }
    }

    private fun unavailable(assetId: String, error: String) = TutorFigureFeedback(buildJsonObject {
        put("assetId", assetId)
        put("errorKind", error)
    }.toString(), null)
}

/**
 * 一张图的实际像素摘要（本地解码后一次扫描得到）。纯数据，让验收规则能在 JVM 单测里
 * 用真实边界值直接跑，不依赖 Android Bitmap。
 */
internal data class FigurePreviewSummary(
    val width: Int,
    val height: Int,
    val minLuma: Int,
    val maxLuma: Int,
    val inkPixels: Int,
    val inkLeft: Int,
    val inkTop: Int,
    val inkRightExclusive: Int,
    val inkBottomExclusive: Int,
    val hasInkBounds: Boolean,
) {
    val inkFraction: Double get() = if (width <= 0 || height <= 0) 0.0 else
        inkPixels.toDouble() / (width.toLong() * height.toLong())
}

/**
 * 本地视觉验收门：只看**可机器判定**的失败（空白 / 内容过少 / 贴边裁切 / 对比度低）。
 *
 * 消灭的具体失败：READ_FIGURE 以前只返回像素统计和一句"等模型判断"，模型完全可能对一张
 * 全白图说"图很清晰"。这里把本地能证伪的情形先判死，模型只在 PASS 的基础上做语义判断；
 * 非 PASS 时工具文案要求它如实说明问题（自己生成的图应修正参数重生成）。
 *
 * 边界刻意保守：贴边不等于一定裁切（坐标轴、边框合法贴边），因此它只把 status 降为
 * REVIEW 并给出原因，不冒充"图片一定坏了"。
 */
internal fun figureAcceptance(summary: FigurePreviewSummary): Pair<String, List<String>> {
    if (!summary.hasInkBounds || summary.inkPixels <= 0 || summary.inkFraction <= 0.0) {
        return "BLANK" to listOf("NO_INK")
    }
    val reasons = mutableListOf<String>()
    if (summary.inkFraction < MIN_INK_FRACTION) reasons += "SPARSE_INK"
    if (summary.maxLuma - summary.minLuma < MIN_CONTRAST_RANGE) reasons += "LOW_CONTRAST"
    if (summary.inkLeft <= 0 || summary.inkTop <= 0 ||
        summary.inkRightExclusive >= summary.width || summary.inkBottomExclusive >= summary.height
    ) {
        reasons += "EDGE_CLIPPED"
    }
    return if (reasons.isEmpty()) "PASS" to emptyList() else "REVIEW" to reasons
}

internal fun figureAcceptanceJson(summary: FigurePreviewSummary) = buildJsonObject {
    val (status, reasons) = figureAcceptance(summary)
    put("status", status)
    put("reasons", kotlinx.serialization.json.buildJsonArray {
        reasons.forEach { reason -> add(kotlinx.serialization.json.JsonPrimitive(reason)) }
    })
    if (status != "PASS") {
        put("advice", "本地像素检查未通过：请如实说明问题；如果这张图是你生成的，先修正参数重新生成，再声称图已可用。")
    }
}

internal fun previewSummary(bitmap: Bitmap): FigurePreviewSummary {
    var left = bitmap.width
    var top = bitmap.height
    var right = -1
    var bottom = -1
    var inkPixels = 0
    var minLuma = 255
    var maxLuma = 0
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
            minLuma = minOf(minLuma, luma)
            maxLuma = maxOf(maxLuma, luma)
            if (Color.alpha(pixel) > 16 && luma < 245) {
                inkPixels++
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
    }
    return FigurePreviewSummary(
        width = bitmap.width,
        height = bitmap.height,
        minLuma = minLuma,
        maxLuma = maxLuma,
        inkPixels = inkPixels,
        inkLeft = left,
        inkTop = top,
        inkRightExclusive = right + 1,
        inkBottomExclusive = bottom + 1,
        hasInkBounds = right >= 0 && bottom >= 0,
    )
}

internal fun previewStatistics(summary: FigurePreviewSummary) = buildJsonObject {
    put("width", summary.width)
    put("height", summary.height)
    put("minLuma", summary.minLuma)
    put("maxLuma", summary.maxLuma)
    put("inkFraction", summary.inkFraction)
    put("inkBounds", if (!summary.hasInkBounds) JsonNull else buildJsonObject {
        put("left", summary.inkLeft)
        put("top", summary.inkTop)
        put("rightExclusive", summary.inkRightExclusive)
        put("bottomExclusive", summary.inkBottomExclusive)
    })
}

private const val MIN_INK_FRACTION = 0.005
private const val MIN_CONTRAST_RANGE = 24

object TutorFigureInspectorFactory {
    fun create(context: Context, database: StudyDatabasePort): TutorFigureInspector =
        AndroidTutorFigureInspector(context, database)
}
