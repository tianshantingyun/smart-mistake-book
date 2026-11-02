package com.tingyun.smartmistakebook.core.data.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAigcTableInjectionTest {

    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val secondSha = "f".repeat(64)
    private val injector = PdfAigcInjector()

    @Test
    fun `injects AIGC and preserves original prefix Info keys and page count`() {
        val source = PdfTestFixtures.tablePdf(includeId = true)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))

        // 增量语义：原始字节前缀逐字节保留（source 已以 EOL 结尾，不需要补换行）。
        assertArrayEquals(source, output.copyOfRange(0, source.size))

        val firstReadBack = PdfAigcReader.readLatestAigc(output)
        assertNotNull(firstReadBack)
        val readBack = firstReadBack!!
        assertEquals("1", readBack.label)
        assertEquals("SmartMistakeBook", readBack.contentProducer)
        assertEquals(sha, readBack.produceId)
        assertEquals(PdfAigcMetadata.document(sha).toJson(), readBack.json)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(output))

        val info = PdfAigcReader.readInfoStrings(output)
        assertEquals(PdfTestFixtures.ORIGINAL_TITLE, info["Title"])
        assertEquals(PdfTestFixtures.ORIGINAL_AUTHOR, info["Author"])
        assertEquals(2, PdfAigcReader.readStructuralPageCount(output))

        // ID：第一段原样保留，第二段按新内容更新。
        val idsValue = PdfAigcReader.readLatestIdSegments(output)
        assertNotNull(idsValue)
        val ids = idsValue!!
        assertArrayEquals(hexToBytes(PdfTestFixtures.HEX_ID), ids[0])
        assertFalse(hexToBytes(PdfTestFixtures.HEX_ID).contentEquals(ids[1]))
    }

    @Test
    fun `same value is byte idempotent and produces no second revision`() {
        val source = PdfTestFixtures.tablePdf()
        val first = injector.inject(source, PdfAigcMetadata.document(sha))
        val second = injector.inject(first, PdfAigcMetadata.document(sha))
        assertArrayEquals(first, second)
    }

    @Test
    fun `different value replaces the single AIGC key and appends a revision`() {
        val source = PdfTestFixtures.tablePdf()
        val first = injector.inject(source, PdfAigcMetadata.document(sha))
        val second = injector.inject(first, PdfAigcMetadata.document(secondSha))

        assertArrayEquals(first, second.copyOfRange(0, first.size))
        val readBackValue = PdfAigcReader.readLatestAigc(second)
        assertNotNull(readBackValue)
        val readBack = readBackValue!!
        assertEquals(secondSha, readBack.produceId)
        assertEquals(PdfAigcMetadata.document(secondSha).toJson(), readBack.json)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(second))
        assertEquals(PdfTestFixtures.ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(second)["Title"])
    }

    @Test
    fun `creates a new Info object when trailer has no Info`() {
        val source = PdfTestFixtures.tablePdf(includeInfo = false)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))

        val readBackValue = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBackValue)
        val readBack = readBackValue!!
        // 原文件 Size=8、最高对象号 7 → 新对象号 8。
        assertEquals(8, readBack.infoObjectNumber)
        assertTrue(output.toString(Charsets.ISO_8859_1).contains("8 0 obj"))
        val info = PdfAigcReader.readInfoStrings(output)
        assertFalse(info.containsKey("Title"))
        assertFalse(info.containsKey("Author"))
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(output))
        assertEquals(2, PdfAigcReader.readStructuralPageCount(output))
    }

    @Test
    fun `preserves the generation number from the Info reference`() {
        val source = PdfTestFixtures.tablePdf(infoGeneration = 2)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))
        val readBackValue = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBackValue)
        val readBack = readBackValue!!
        assertTrue(output.toString(Charsets.ISO_8859_1).contains("7 2 obj"))
    }

    @Test
    fun `accepts the JSON string overload and canonicalizes it`() {
        val source = PdfTestFixtures.tablePdf()
        val json = PdfAigcMetadata.document(sha).toJson()
        val output = injector.inject(source, json)
        val readBackValue = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBackValue)
        assertEquals(json, readBackValue!!.json)
    }

    @Test
    fun `same value with PDF-escaped optional fields is still byte idempotent`() {
        val metadata = PdfAigcMetadata.document(sha).copy(
            reservedCode1 = "a(b)\"c",
            contentPropagator = "back\\slash",
        )
        val source = PdfTestFixtures.tablePdf()
        val first = injector.inject(source, metadata)
        assertEquals(metadata.toJson(), PdfAigcReader.readLatestAigc(first)!!.json)
        val second = injector.inject(first, metadata)
        assertArrayEquals(first, second)
    }

    @Test
    fun `works when the source does not end with an EOL`() {
        val source = PdfTestFixtures.tablePdf()
        val trimmed = source.copyOfRange(0, source.size - 1) // 去掉最后一个 '\n'
        val output = injector.inject(trimmed, PdfAigcMetadata.document(sha))
        assertArrayEquals(trimmed, output.copyOfRange(0, trimmed.size))
        // 追加前补的 EOL 是扩展字节的一部分，不污染原前缀；新段仍可解析。
        assertNotNull(PdfAigcReader.readLatestAigc(output))
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
