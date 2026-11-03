package com.tingyun.smartmistakebook.core.model.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAigcStreamInjectionTest {

    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val secondSha = "e".repeat(64)
    private val injector = PdfAigcInjector()

    @Test
    fun `injects a same-type xref stream without touching the original prefix`() {
        val source = PdfTestFixtures.streamPdf(includeId = true)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))

        assertArrayEquals(source, output.copyOfRange(0, source.size))
        val appended = output.copyOfRange(source.size, output.size).toString(Charsets.ISO_8859_1)
        assertTrue(appended.contains("/Type /XRef"))
        assertTrue(appended.contains("/Prev "))
        assertFalse("table xref must not be written into a stream PDF", appended.contains("\nxref\n"))

        val readBackValue = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBackValue)
        val readBack = readBackValue!!
        assertEquals(sha, readBack.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(output))
        assertEquals(PdfTestFixtures.ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(output)["Title"])
        assertEquals(2, PdfAigcReader.readStructuralPageCount(output))

        val ids = PdfAigcReader.readLatestIdSegments(output)
        assertNotNull(ids)
        assertArrayEquals(hexToBytes(PdfTestFixtures.HEX_ID), ids!![0])
        assertFalse(hexToBytes(PdfTestFixtures.HEX_ID).contentEquals(ids[1]))
    }

    @Test
    fun `supports FlateDecode with PNG predictor 12 on the previous xref stream`() {
        val source = PdfTestFixtures.streamPdf(flate = true, predictor = 12)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))

        val readBackValue = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBackValue)
        assertEquals(sha, readBackValue!!.produceId)
        assertEquals(2, PdfAigcReader.readStructuralPageCount(output))

        // 二次注入必须还能读上一段（这次上一段是我们写出的无压缩流）。
        val second = injector.inject(output, PdfAigcMetadata.document(secondSha))
        assertEquals(secondSha, PdfAigcReader.readLatestAigc(second)!!.produceId)
    }

    @Test
    fun `same value is byte idempotent on stream xref`() {
        val source = PdfTestFixtures.streamPdf()
        val first = injector.inject(source, PdfAigcMetadata.document(sha))
        val second = injector.inject(first, PdfAigcMetadata.document(sha))
        assertArrayEquals(first, second)
    }

    @Test
    fun `different value appends a new stream revision with one AIGC key`() {
        val source = PdfTestFixtures.streamPdf()
        val first = injector.inject(source, PdfAigcMetadata.document(sha))
        val second = injector.inject(first, PdfAigcMetadata.document(secondSha))

        assertArrayEquals(first, second.copyOfRange(0, first.size))
        val readBackValue = PdfAigcReader.readLatestAigc(second)
        assertNotNull(readBackValue)
        assertEquals(secondSha, readBackValue!!.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(second))
        assertEquals(PdfTestFixtures.ORIGINAL_AUTHOR, PdfAigcReader.readInfoStrings(second)["Author"])
    }

    @Test
    fun `rejects an Info object that lives in an object stream`() {
        val source = PdfTestFixtures.streamPdf(infoInObjectStream = true)
        val error = assertThrows(PdfAigcInjectionException::class.java) {
            injector.inject(source, PdfAigcMetadata.document(sha))
        }
        assertEquals(PdfAigcErrorCode.INFO_IN_OBJECT_STREAM, error.code)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
