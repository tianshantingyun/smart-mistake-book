package com.tingyun.smartmistakebook.core.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAigcSchemaTest {

    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun `document metadata serializes exactly seven keys in template order`() {
        val metadata = PdfAigcMetadata.document(sha)
        assertEquals(
            "{\"Label\":\"1\",\"ContentProducer\":\"SmartMistakeBook\",\"ProduceID\":\"$sha\"," +
                "\"ReservedCode1\":\"\",\"ContentPropagator\":\"\",\"PropagateID\":\"\",\"ReservedCode2\":\"\"}",
            metadata.toJson(),
        )
        val keys = Regex("\"([A-Za-z0-9]+)\":").findAll(metadata.toJson())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(PdfAigcMetadata.KEY_ORDER, keys)
    }

    @Test
    fun `parse accepts a valid object regardless of key order`() {
        val json = "{\"ReservedCode2\":\"\",\"Label\":\"1\",\"ContentProducer\":\"SmartMistakeBook\"," +
            "\"ProduceID\":\"$sha\",\"ReservedCode1\":\"\",\"ContentPropagator\":\"\",\"PropagateID\":\"\"}"
        assertEquals(sha, PdfAigcMetadata.parse(json).produceId)
    }

    @Test
    fun `parse rejects missing and extra keys`() {
        val missing = "{\"Label\":\"1\",\"ContentProducer\":\"SmartMistakeBook\",\"ProduceID\":\"$sha\"}"
        assertSchemaError(missing)

        val extra = PdfAigcMetadata.document(sha).toJson().replace("}", ",\"Extra\":\"x\"}")
        assertSchemaError(extra)
    }

    @Test
    fun `parse rejects non-string values arrays and duplicate keys`() {
        val numeric = PdfAigcMetadata.document(sha).toJson()
            .replace("\"ReservedCode2\":\"\"", "\"ReservedCode2\":1")
        assertSchemaError(numeric)

        val array = PdfAigcMetadata.document(sha).toJson()
            .replace("\"ReservedCode2\":\"\"", "\"ReservedCode2\":[]")
        assertSchemaError(array)

        val duplicate = PdfAigcMetadata.document(sha).toJson()
            .replace("\"Label\":\"1\",", "\"Label\":\"1\",\"Label\":\"1\",")
        assertSchemaError(duplicate)
    }

    @Test
    fun `write path requires label 1 and printable ascii`() {
        assertSchemaErrorFails {
            PdfAigcMetadata(label = "2", produceId = sha)
        }
        assertSchemaErrorFails {
            PdfAigcMetadata(contentProducer = "坏生产者", produceId = sha)
        }
        assertSchemaErrorFails {
            PdfAigcMetadata(contentProducer = "", produceId = sha)
        }
        assertSchemaErrorFails {
            PdfAigcMetadata(produceId = "abc")
        }
        assertSchemaErrorFails {
            PdfAigcMetadata(produceId = sha.uppercase())
        }
    }

    @Test
    fun `document factory requires 64 lowercase hex`() {
        assertSchemaErrorFails { PdfAigcMetadata.document("abc") }
        assertSchemaErrorFails { PdfAigcMetadata.document(sha.uppercase()) }
        assertSchemaErrorFails { PdfAigcMetadata.document(sha + "0") }
    }

    @Test
    fun `production value is the input fingerprint not a figure id`() {
        val metadata = PdfAigcMetadata.document(sha)
        assertEquals(sha, metadata.toJson().substringAfter("\"ProduceID\":\"").substringBefore('"'))
        assertTrue(metadata.toJson().length <= 1024)
    }

    private fun assertSchemaError(json: String) {
        val error = assertThrows(PdfAigcInjectionException::class.java) { PdfAigcMetadata.parse(json) }
        assertEquals(PdfAigcErrorCode.AIGC_SCHEMA_INVALID, error.code)
    }

    private fun assertSchemaErrorFails(block: () -> Unit) {
        val error = assertThrows(PdfAigcInjectionException::class.java) { block() }
        assertEquals(PdfAigcErrorCode.AIGC_SCHEMA_INVALID, error.code)
    }
}
