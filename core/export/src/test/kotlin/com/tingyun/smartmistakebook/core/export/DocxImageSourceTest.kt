package com.tingyun.smartmistakebook.core.export

import java.io.File
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 干净题面图读取口（6B-1）的 JVM 用例：PNG/JPEG 头部解析、坏输入降级为 null。
 *
 * 它钉住的失败：Word 侧按图片真实宽高比算 `wp:extent`，头部解析错一位就会把图拉变形；
 * 而读不出的图必须**省略**（PDF 计划器同口径），不能抛异常让整份文档失败。
 */
class DocxImageSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `a png header yields the pixel size and content type`() {
        val file = imageFile("clean.png", pngBytes(width = 640, height = 480))

        val image = requireNotNull(DocxImageSource.read(file.toURI().toString()))
        assertEquals(640, image.widthPx)
        assertEquals(480, image.heightPx)
        assertEquals("png", image.fileExtension)
        assertEquals("image/png", image.contentType)
    }

    @Test
    fun `a jpeg SOF segment yields the pixel size without decoding the pixels`() {
        val file = imageFile("clean.jpeg", jpegBytes(width = 800, height = 600))

        val image = requireNotNull(DocxImageSource.read(file.toURI().toString()))
        assertEquals(800, image.widthPx)
        assertEquals(600, image.heightPx)
        assertEquals("jpeg", image.fileExtension)
        assertEquals("image/jpeg", image.contentType)
    }

    @Test
    fun `an unreadable or unsupported image degrades to null instead of failing the export`() {
        // 文件不存在
        assertNull(
            DocxImageSource.read(File(temporaryFolder.root, "missing.png").toURI().toString()),
        )
        // 普通文本（不是 PNG/JPEG）
        assertNull(DocxImageSource.read(imageFile("note.png", "not an image".toByteArray()).toURI().toString()))
        // 空文件
        assertNull(DocxImageSource.read(imageFile("empty.png", ByteArray(0)).toURI().toString()))
        // PNG 签名正确但 IHDR 缺失（头部损坏）
        val signatureOnly = pngBytes(width = 10, height = 10).copyOf(16)
        assertNull(DocxImageSource.read(imageFile("broken.png", signatureOnly).toURI().toString()))
        // PNG 宽高为 0（占位图）→ 没有可用的交付尺寸
        assertNull(DocxImageSource.read(imageFile("zero.png", pngBytes(0, 0)).toURI().toString()))
        // 只有 SOI/EOI、没有 SOF 的 JPEG
        assertNull(
            DocxImageSource.read(
                imageFile("no-frame.jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
                    .toURI().toString(),
            ),
        )
        // 不是 URI 的裸路径
        assertNull(DocxImageSource.read("/no/such/image.png"))
        assertNull(DocxImageSource.read(null))
        assertNull(DocxImageSource.read("  "))
    }

    private fun imageFile(name: String, bytes: ByteArray): File =
        File(temporaryFolder.root, name).apply { writeBytes(bytes) }

    /** 结构完整的 PNG（签名 + IHDR + IEND）；读取口只解析头部。 */
    private fun pngBytes(width: Int, height: Int): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply {
                update(typeBytes)
                update(data)
            }.value
            return byteArrayOf(
                (data.size ushr 24).toByte(),
                (data.size ushr 16).toByte(),
                (data.size ushr 8).toByte(),
                data.size.toByte(),
            ) + typeBytes + data + byteArrayOf(
                (crc ushr 24).toByte(),
                (crc ushr 16).toByte(),
                (crc ushr 8).toByte(),
                crc.toByte(),
            )
        }

        val header = byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(),
            (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(),
            (height ushr 8).toByte(), height.toByte(),
            8, 6, 0, 0, 0,
        )
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", header) +
            chunk("IEND", ByteArray(0))
    }

    /** 最小 JPEG：SOI + APP0 + SOF0（宽高）+ EOI；读取口扫到 SOF0 即返回。 */
    private fun jpegBytes(width: Int, height: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        // APP0, length 16, "JFIF\0", version 1.1, units 0, density 1x1, thumbnail 0x0
        0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        // SOF0, length 17, precision 8, height, width, 3 components
        0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
        (height ushr 8).toByte(), height.toByte(),
        (width ushr 8).toByte(), width.toByte(),
        0x03,
        0x01, 0x11, 0x00,
        0x02, 0x11, 0x01,
        0x03, 0x11, 0x01,
        0xFF.toByte(), 0xD9.toByte(),
    )
}
