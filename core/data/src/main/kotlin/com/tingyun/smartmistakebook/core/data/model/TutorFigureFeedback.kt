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
                    put("preview", previewStatistics(preview))
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

internal fun previewStatistics(bitmap: Bitmap) = buildJsonObject {
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
    put("width", bitmap.width)
    put("height", bitmap.height)
    put("minLuma", minLuma)
    put("maxLuma", maxLuma)
    put("inkFraction", inkPixels.toDouble() / (bitmap.width * bitmap.height))
    put("inkBounds", if (inkPixels == 0) JsonNull else buildJsonObject {
        put("left", left)
        put("top", top)
        put("rightExclusive", right + 1)
        put("bottomExclusive", bottom + 1)
    })
}

object TutorFigureInspectorFactory {
    fun create(context: Context, database: StudyDatabasePort): TutorFigureInspector =
        AndroidTutorFigureInspector(context, database)
}
