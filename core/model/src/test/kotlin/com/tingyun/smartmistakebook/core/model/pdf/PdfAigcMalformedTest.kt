package com.tingyun.smartmistakebook.core.model.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfAigcMalformedTest {

    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val metadata = PdfAigcMetadata.document(sha)

    @Test
    fun `rejects a non-PDF header`() {
        val source = PdfTestFixtures.tablePdf().copyOf()
        source[0] = 'X'.code.toByte()
        assertCode(PdfAigcErrorCode.NOT_PDF_HEADER, source)
    }

    @Test
    fun `rejects a truncated header`() {
        assertCode(PdfAigcErrorCode.HEADER_TRUNCATED, ByteArray(0))
        assertCode(PdfAigcErrorCode.HEADER_TRUNCATED, "%PDF-1.".toByteArray(Charsets.US_ASCII))
    }

    @Test
    fun `rejects an unsupported PDF version`() {
        val source = PdfTestFixtures.replaceAscii(PdfTestFixtures.tablePdf(), "%PDF-1.4", "%PDF-9.9")
        assertCode(PdfAigcErrorCode.HEADER_VERSION_INVALID, source)
    }

    @Test
    fun `rejects a missing startxref`() {
        val source = PdfTestFixtures.replaceAscii(PdfTestFixtures.tablePdf(), "startxref", "startxrefX")
        assertCode(PdfAigcErrorCode.STARTXREF_MISSING, source)
    }

    @Test
    fun `rejects a non-integer startxref`() {
        val source = replaceStartxrefValue(PdfTestFixtures.tablePdf(), "abc")
        assertCode(PdfAigcErrorCode.STARTXREF_NOT_INTEGER, source)
    }

    @Test
    fun `rejects an out-of-range startxref offset`() {
        val source = PdfTestFixtures.tablePdf(startxrefOverride = 999_999_999L)
        assertCode(PdfAigcErrorCode.STARTXREF_OUT_OF_RANGE, source)
    }

    @Test
    fun `rejects a startxref that points neither at xref nor at an object header`() {
        // 指向第一页对象的对象号行内部。
        val source = PdfTestFixtures.tablePdf(startxrefOverride = 20L)
        assertCode(PdfAigcErrorCode.XREF_KEYWORD_MISSING, source)
    }

    @Test
    fun `rejects a malformed 20-byte xref entry`() {
        val source = PdfTestFixtures.tablePdf(corruptXrefEntryObject = 7)
        assertCode(PdfAigcErrorCode.XREF_TABLE_MALFORMED, source)
    }

    @Test
    fun `rejects a truncated Info dictionary`() {
        val source = PdfTestFixtures.tablePdf(infoBody = "<< /Title (Original Title) /Author (Original Author)")
        assertCode(PdfAigcErrorCode.INFO_OBJECT_TRUNCATED, source)
    }

    @Test
    fun `rejects a missing Root`() {
        val source = PdfTestFixtures.replaceAscii(PdfTestFixtures.tablePdf(), " /Root 1 0 R", "")
        assertCode(PdfAigcErrorCode.TRAILER_ROOT_MISSING, source)
    }

    @Test
    fun `rejects a direct Root dictionary`() {
        val source = PdfTestFixtures.replaceAscii(PdfTestFixtures.tablePdf(), " /Root 1 0 R", " /Root << >>")
        assertCode(PdfAigcErrorCode.TRAILER_ROOT_NOT_INDIRECT, source)
    }

    @Test
    fun `rejects a missing Size`() {
        val source = PdfTestFixtures.replaceAscii(PdfTestFixtures.tablePdf(), " /Size 8", "")
        assertCode(PdfAigcErrorCode.SIZE_MISSING, source)
    }

    @Test
    fun `rejects a malformed ID array`() {
        val source = PdfTestFixtures.tablePdf(trailerExtra = " /ID 1")
        assertCode(PdfAigcErrorCode.ID_MALFORMED, source)
    }

    @Test
    fun `rejects a direct Info dictionary`() {
        val source = PdfTestFixtures.replaceAscii(
            PdfTestFixtures.tablePdf(),
            " /Info 7 0 R",
            " /Info << /Title (x) >>",
        )
        assertCode(PdfAigcErrorCode.INFO_NOT_INDIRECT, source)
    }

    @Test
    fun `rejects Info pointing at a missing object`() {
        val source = PdfTestFixtures.replaceAscii(PdfTestFixtures.tablePdf(), " /Info 7 0 R", " /Info 19 0 R")
        assertCode(PdfAigcErrorCode.INFO_OBJECT_MISSING, source)
    }

    @Test
    fun `rejects an Info object that is not a dictionary`() {
        val source = PdfTestFixtures.tablePdf(infoBody = "(not a dictionary)")
        assertCode(PdfAigcErrorCode.INFO_NOT_DICTIONARY, source)
    }

    @Test
    fun `rejects an existing AIGC value that is not a string`() {
        val source = PdfTestFixtures.tablePdf(infoBody = "<< /Title (x) /AIGC /NotAString >>")
        assertCode(PdfAigcErrorCode.AIGC_VALUE_NOT_STRING, source)
    }

    @Test
    fun `rejects encrypted PDFs`() {
        val source = PdfTestFixtures.tablePdf(trailerExtra = " /Encrypt 9 0 R")
        assertCode(PdfAigcErrorCode.ENCRYPTED, source)
    }

    @Test
    fun `rejects linearized PDFs`() {
        val source = PdfTestFixtures.tablePdf(linearizedFirstObject = true)
        assertCode(PdfAigcErrorCode.LINEARIZED, source)
    }

    @Test
    fun `rejects hybrid-reference PDFs`() {
        val source = PdfTestFixtures.tablePdf(trailerExtra = " /XRefStm 100")
        assertCode(PdfAigcErrorCode.HYBRID_XREF, source)
    }

    @Test
    fun `rejects input over the configured limit before writing anything`() {
        val source = PdfTestFixtures.tablePdf()
        val injector = PdfAigcInjector(PdfAigcLimits(maxInputBytes = 16))
        val error = assertThrows(PdfAigcInjectionException::class.java) {
            injector.inject(source, metadata)
        }
        assertEquals(PdfAigcErrorCode.INPUT_TOO_LARGE, error.code)
    }

    @Test
    fun `rejects output over the configured limit`() {
        val source = PdfTestFixtures.tablePdf()
        val injector = PdfAigcInjector(PdfAigcLimits(maxOutputBytes = source.size + 1))
        val error = assertThrows(PdfAigcInjectionException::class.java) {
            injector.inject(source, metadata)
        }
        assertEquals(PdfAigcErrorCode.OUTPUT_TOO_LARGE, error.code)
    }

    private fun assertCode(expected: PdfAigcErrorCode, source: ByteArray) {
        val error = assertThrows(PdfAigcInjectionException::class.java) {
            PdfAigcInjector().inject(source, metadata)
        }
        assertEquals(expected, error.code)
    }

    private fun replaceStartxrefValue(source: ByteArray, replacement: String): ByteArray {
        val text = source.toString(Charsets.ISO_8859_1)
        val keyword = text.lastIndexOf("startxref")
        check(keyword >= 0)
        var position = keyword + "startxref".length
        while (position < text.length && text[position].isWhitespace()) position++
        var end = position
        while (end < text.length && text[end].isDigit()) end++
        return (text.substring(0, position) + replacement + text.substring(end))
            .toByteArray(Charsets.ISO_8859_1)
    }
}
