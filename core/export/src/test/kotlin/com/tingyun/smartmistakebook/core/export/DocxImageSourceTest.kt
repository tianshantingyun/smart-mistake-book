package com.tingyun.smartmistakebook.core.export

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * KD-55 正常路径：记录里的 sha 与长度都匹配时，读口返回**同一份**字节（嵌入它的就是
     * 刚复核过的那份）。大小写不敏感只为容错记录来源，不改变记录格式（仍是小写十六进制）。
     */
    @Test
    fun `a matching record sha and length yields the verified bytes`() {
        val png = pngBytes(width = 320, height = 200)
        val file = imageFile("verified.png", png)
        val sha = sha256Hex(png)

        val image = requireNotNull(
            DocxImageSource.read(
                localUri = file.toURI().toString(),
                expectedSha256 = sha,
                expectedByteSize = png.size.toLong(),
            ),
        )

        assertArrayEquals(png, image.bytes)
        assertEquals(320, image.widthPx)
        // 同一份记录用大写 hex 表达时仍接受（equals(ignoreCase = true)）。
        assertArrayEquals(
            png,
            requireNotNull(
                DocxImageSource.read(
                    localUri = file.toURI().toString(),
                    expectedSha256 = sha.uppercase(),
                    expectedByteSize = png.size.toLong(),
                ),
            ).bytes,
        )
    }

    /** KD-55 失败路径：记录长度与实际文件不符 → 按"读不出"降级，不返回任何字节。 */
    @Test
    fun `a record length mismatch is rejected before the bytes are trusted`() {
        val png = pngBytes(width = 100, height = 50)
        val file = imageFile("short.png", png)

        assertNull(
            DocxImageSource.read(
                localUri = file.toURI().toString(),
                expectedSha256 = sha256Hex(png),
                expectedByteSize = png.size + 1L,
            ),
        )
        // 只给长度（不给 sha）也同样核。
        assertNull(
            DocxImageSource.read(
                localUri = file.toURI().toString(),
                expectedByteSize = png.size - 1L,
            ),
        )
    }

    /**
     * KD-55 失败路径：长度相同但字节已被换掉（TOCTOU 的代表形态）→ sha 不符，拒绝。
     * 这正是"读详情时核过一次、渲染时文件已被替换"的窗口。
     */
    @Test
    fun `a record sha mismatch is rejected even when the length matches`() {
        val original = pngBytes(width = 100, height = 50)
        val replacement = pngBytes(width = 101, height = 50)
        assertEquals("夹具退化：两份字节必须等长才能钉住 sha 的作用", original.size, replacement.size)
        val file = imageFile("replaced.png", replacement)

        assertNull(
            DocxImageSource.read(
                localUri = file.toURI().toString(),
                expectedSha256 = sha256Hex(original),
                expectedByteSize = replacement.size.toLong(),
            ),
        )
        // 只给 sha（不给长度）也同样核。
        assertNull(
            DocxImageSource.read(
                localUri = file.toURI().toString(),
                expectedSha256 = sha256Hex(original),
            ),
        )
    }

    /**
     * KD-55 有界读取：`File.length()` 预检与真正读入之间文件可能被换成更大的文件。
     * 读入必须以记录长度为上界——读满记录长度后只多探一个字节，绝不把增长的部分整份读进内存、
     * 再靠读后比较长度来"兜底"。
     */
    @Test
    fun `a file that grows after the length check is never read past the recorded bound`() {
        val recorded = pngBytes(width = 4, height = 2)
        val grown = recorded + ByteArray(64 * 1024) { 0x41 }
        val delegate = ByteArrayInputStream(grown)
        var pulled = 0
        val bounded = object : InputStream() {
            override fun read(): Int {
                if (pulled > recorded.size) {
                    throw AssertionError("读取越过记录长度 + 1：有界读取失效")
                }
                val value = delegate.read()
                if (value >= 0) pulled += 1
                return value
            }
        }

        assertNull(DocxImageSource.readBounded(bounded, recorded.size))
        assertTrue(
            "最多只能读到记录长度 + 1 字节（实际 $pulled）",
            pulled <= recorded.size + 1,
        )
    }

    /** KD-55 长度变化：文件在预检与读入之间变短时，不返回一份长度不足的"图片"。 */
    @Test
    fun `a file that shrinks after the length check is rejected instead of returning short bytes`() {
        val recorded = pngBytes(width = 4, height = 2)
        val truncated = recorded.copyOf(recorded.size - 1)

        assertNull(
            DocxImageSource.readBounded(
                ByteArrayInputStream(truncated),
                recorded.size,
            ),
        )
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

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
