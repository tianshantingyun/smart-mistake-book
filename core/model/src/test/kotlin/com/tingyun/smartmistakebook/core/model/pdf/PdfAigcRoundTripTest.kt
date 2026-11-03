package com.tingyun.smartmistakebook.core.model.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WS1 item 8 的 JVM 侧：字节级往返。
 *
 * 真 `PdfRenderer` 打开/页数属设备门（本批不运行 Gradle，故 UNVERIFIED）；这里用
 * [PdfAigcReader.readStructuralPageCount] 证明对象图/页树未被改写，并把产物写到
 * `-Dws1.out=<dir>` 供 pypdf/PyMuPDF 独立读者复核（本轮实际执行）。
 */
class PdfAigcRoundTripTest {

    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val injector = PdfAigcInjector()

    @Test
    fun `table PDF round trip keeps prefix page count and Info and reads AIGC back`() {
        val source = PdfTestFixtures.tablePdf(includeId = true)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))

        assertArrayEquals(source, output.copyOfRange(0, source.size))
        assertTrue(output.size > source.size)
        assertEquals(2, PdfAigcReader.readStructuralPageCount(output))
        assertEquals(PdfTestFixtures.ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(output)["Title"])
        assertEquals(PdfTestFixtures.ORIGINAL_AUTHOR, PdfAigcReader.readInfoStrings(output)["Author"])
        val readBack = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBack)
        assertEquals(PdfAigcMetadata.document(sha).toJson(), readBack!!.json)

        val second = injector.inject(output, PdfAigcMetadata.document("f".repeat(64)))
        assertEquals("f".repeat(64), PdfAigcReader.readLatestAigc(second)!!.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(second))

        emit("table", source, output, second)
    }

    @Test
    fun `stream PDF round trip keeps prefix page count and Info and reads AIGC back`() {
        val source = PdfTestFixtures.streamPdf(flate = true, predictor = 12, includeId = true)
        val output = injector.inject(source, PdfAigcMetadata.document(sha))

        assertArrayEquals(source, output.copyOfRange(0, source.size))
        assertTrue(output.size > source.size)
        assertEquals(2, PdfAigcReader.readStructuralPageCount(output))
        assertEquals(PdfTestFixtures.ORIGINAL_TITLE, PdfAigcReader.readInfoStrings(output)["Title"])
        val readBack = PdfAigcReader.readLatestAigc(output)
        assertNotNull(readBack)
        assertEquals(sha, readBack!!.produceId)

        val second = injector.inject(output, PdfAigcMetadata.document("e".repeat(64)))
        assertEquals("e".repeat(64), PdfAigcReader.readLatestAigc(second)!!.produceId)
        assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(second))

        emit("stream-predictor12", source, output, second)
    }

    private fun emit(name: String, source: ByteArray, output: ByteArray, second: ByteArray) {
        val dir = System.getProperty("ws1.out") ?: return
        val directory = File(dir)
        directory.mkdirs()
        File(directory, "$name-original.pdf").writeBytes(source)
        File(directory, "$name-labeled.pdf").writeBytes(output)
        File(directory, "$name-twice.pdf").writeBytes(second)
    }
}
