package com.tingyun.smartmistakebook.core.data.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tingyun.smartmistakebook.core.model.layout.LayoutConfig
import com.tingyun.smartmistakebook.core.model.layout.LayoutStructure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 从**已授权的出网副本**算出版面测量块（`core:model` 的 `LayoutStructure.toPromptBlock()`）。
 *
 * **它消灭的具体失败**：切分提示词里没有任何页面几何事实，模型只能从缩放图里估栏数与栏谷；
 * 而"估算"在双栏页/暗光页上直接决定"一题还是两题"。本端口把同一份出网字节量成测量值，
 * 由调用方塞进 `CaptureAssessmentInput.layoutHint`。
 *
 * **为什么吃出网副本而不是原图**：研究 §3.6——CV、提示词、模型看到的图像必须是同一帧，
 * 否则坐标与栏判会对不上（出网链路 `BitmapFactory`→JPEG 不读不写 EXIF）。
 *
 * **失败一律 null**：解码不了、内存不够、几何退化——都退回"没有提示"，绝不返回半截坐标，
 * 也绝不抛出（切分是页面入库之后的增强，不能反过来让页失败）。
 */
internal fun interface PageLayoutHint {
    suspend fun hintFor(mimeType: String, bytes: ByteArray): String?

    companion object {
        /**
         * JVM 单测与无图链路的空实现：永不产出提示。
         *
         * 之所以它是**构造参数的默认值**而不是隐藏分支：网关的 JVM 用例不装载 Android 图形栈
         * （`BitmapFactory` 在单测类路径上是 not-mocked stub），默认值让存量用例逐字节不变；
         * **生产装配必须显式传 [AndroidPageLayoutHint]**（`ConfiguredModelGatewayFactory`）。
         */
        val None: PageLayoutHint = PageLayoutHint { _, _ -> null }
    }
}

internal object AndroidPageLayoutHint : PageLayoutHint {

    /** 分析长边：与 2048 出网图同源、成本约 1/4；行距 ≥10 px 仍可分辨（研究 §3.2）。 */
    const val MAX_ANALYSIS_EDGE = 1024

    override suspend fun hintFor(mimeType: String, bytes: ByteArray): String? {
        if (!mimeType.startsWith("image/")) return null
        if (bytes.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            try {
                analyseWithinBudget(bytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // 解码/分析失败不是页面的失败：没有提示时请求体回到今天的样子。
                null
            }
        }
    }

    private fun analyseWithinBudget(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var width = bounds.outWidth
        var height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= 1 && maxOf(width, height) / sample > MAX_ANALYSIS_EDGE) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        var scaled: Bitmap? = null
        return try {
            width = decoded.width
            height = decoded.height
            val longEdge = maxOf(width, height)
            val source = if (longEdge > MAX_ANALYSIS_EDGE) {
                val scale = MAX_ANALYSIS_EDGE.toDouble() / longEdge
                scaled = Bitmap.createScaledBitmap(
                    decoded,
                    maxOf(1, (width * scale).toInt()),
                    maxOf(1, (height * scale).toInt()),
                    true,
                )
                scaled!!
            } else {
                decoded
            }
            val gray = toGray(source)
            LayoutStructure.analyse(gray, source.width, source.height, LayoutConfig()).toPromptBlock()
        } finally {
            scaled?.recycle()
            if (scaled !== decoded) decoded.recycle()
        }
    }

    /**
     * `Bitmap` → 行优先 8 位灰度：亮度按 `0.299R + 0.587G + 0.114B`，**四舍五入**。
     *
     * 与探针的 `PIL.convert("L")` 同口径（PIL 也是 round，不是截断）：用截断会在绿色分量上
     * 差 1 个灰阶（0.587×255=149.7 截成 149），逐页对拍时表现为 Otsu 阈值偶发漂移。
     * 透明像素按白纸处理——出网副本永远是 JPEG，此处只是防呆。
     */
    fun toGray(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = ByteArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            var luminance = (0.299 * red + 0.587 * green + 0.114 * blue).roundToInt()
            if (alpha < 255) luminance = luminance * alpha / 255 + 255 * (255 - alpha) / 255
            gray[index] = luminance.coerceIn(0, 255).toByte()
        }
        return gray
    }
}
