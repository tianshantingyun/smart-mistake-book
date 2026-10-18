package com.tingyun.smartmistakebook.core.export

import java.io.File

/** 一张可从本地文件读出的题面图（PNG/JPEG），带交付所需的像素尺寸与部件元数据。 */
internal data class DocxImageBytes(
    val bytes: ByteArray,
    val fileExtension: String,
    val contentType: String,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * 干净题面图的读取口（6B-1）：`file://` 本地路径 → 校验过的字节 + 像素尺寸。
 *
 * 它消灭的失败：Word 侧要按图片真实宽高比给出绘制范围（`wp:extent`），而 Android 的
 * `BitmapFactory` 是 android.graphics 的、JVM 用例跑不了——这里只用**字节头部**解析
 * （PNG 的 IHDR、JPEG 的 SOF 段），渲染器因此能在纯 JVM 上完整演练。
 *
 * 读不出（文件不存在/不是 PNG-JPEG/头部损坏）返回 null：**按 PDF 计划器的同口径降级**——
 * `MistakePdfPagePlanner` 在拿不到图片尺寸时就不排入这张图（:126-137），Word 侧同样省略
 * 图片、保留题面其余内容。与 PDF 渲染器的差异（它在这种输入上整份失败）见批记录。
 */
internal object DocxImageSource {
    fun read(localUri: String?): DocxImageBytes? {
        val uri = localUri?.takeIf(String::isNotBlank) ?: return null
        return try {
            val file = File(java.net.URI(uri))
            if (!file.isFile) return null
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            when {
                isPng(bytes) -> pngSize(bytes)?.let { size ->
                    DocxImageBytes(bytes, "png", "image/png", size[0], size[1])
                }
                isJpeg(bytes) -> jpegSize(bytes)?.let { size ->
                    DocxImageBytes(bytes, "jpeg", "image/jpeg", size[0], size[1])
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= PNG_SIGNATURE.size &&
            PNG_SIGNATURE.indices.all { index -> bytes[index] == PNG_SIGNATURE[index] }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    /** PNG：IHDR 必须是第一个块，宽高在签名后的 16..23 字节（大端）。 */
    private fun pngSize(bytes: ByteArray): IntArray? {
        if (bytes.size < 24) return null
        if (bytes[12] != 'I'.code.toByte() ||
            bytes[13] != 'H'.code.toByte() ||
            bytes[14] != 'D'.code.toByte() ||
            bytes[15] != 'R'.code.toByte()
        ) {
            return null
        }
        val width = readBigEndianInt(bytes, 16)
        val height = readBigEndianInt(bytes, 20)
        return intArrayOf(width, height).takeIf { width > 0 && height > 0 }
    }

    /**
     * JPEG：逐段扫描到 SOFn（帧开始）取宽高。0xD8/0xD9/0xD0–0xD7 不是"带长度段"，
     * 0xDA（扫描开始）之后就没有尺寸信息了——遇到这些按规则跳过或停下。
     */
    private fun jpegSize(bytes: ByteArray): IntArray? {
        var offset = 2
        while (offset + 9 < bytes.size) {
            if (bytes[offset] != 0xFF.toByte()) return null
            var marker = bytes[offset + 1].toInt() and 0xFF
            while (marker == 0xFF && offset + 2 < bytes.size) {
                offset += 1
                marker = bytes[offset + 1].toInt() and 0xFF
            }
            when {
                marker == 0x01 || marker in 0xD0..0xD8 -> offset += 2
                marker == 0xD9 || marker == 0xDA -> return null
                else -> {
                    if (offset + 3 >= bytes.size) return null
                    val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 3].toInt() and 0xFF)
                    if (length < 2) return null
                    if (marker in SOF_MARKERS) {
                        if (offset + 8 >= bytes.size) return null
                        val height = ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 6].toInt() and 0xFF)
                        val width = ((bytes[offset + 7].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 8].toInt() and 0xFF)
                        return intArrayOf(width, height).takeIf { width > 0 && height > 0 }
                    }
                    offset += 2 + length
                }
            }
        }
        return null
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private val SOF_MARKERS = setOf(
        0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
        0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
    )
}
