package com.tingyun.smartmistakebook.diagnostics

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6：诊断包内容——关键字段（版本/flavor/生成时刻/条数）必须可见；读不到的字段缺行
 * 而不是假造；SHA-256 复读校验的摘要函数与 JDK 参照一致（保存后校验依赖它）。
 */
class DiagnosticsBundleExporterTest {

    private val meta = DiagnosticsBundleMeta(
        versionName = "0.2.0",
        versionCode = 2L,
        flavor = "strictOffline",
        generatedAtEpochMillis = 1_234_567L,
    )

    private fun event(at: Long, category: String) = DiagnosticEvent(
        atEpochMillis = at,
        category = category,
        threadName = "main",
        message = "boom",
        stackTrace = "java.lang.IllegalStateException: boom\n at Foo(Foo.kt:1)",
        versionName = "0.2.0",
        versionCode = 2L,
        flavor = "strictOffline",
    )

    @Test
    fun headerContainsKeyFieldsAndEventLines() {
        val text = DiagnosticsBundleExporter.bundleText(
            events = listOf(event(1L, "CRASH"), event(2L, "EXIT_ANR")),
            meta = meta,
        )
        assertTrue(text.contains("# smart-mistake-book diagnostics bundle"))
        assertTrue(text.contains("# format=1"))
        assertTrue(text.contains("# versionName=0.2.0"))
        assertTrue(text.contains("# versionCode=2"))
        assertTrue(text.contains("# flavor=strictOffline"))
        assertTrue(text.contains("# generatedAtEpochMillis=1234567"))
        assertTrue(text.contains("# entries=2"))
        val eventLines = text.lines().filter { it.startsWith("{") }
        assertEquals(2, eventLines.size)
        assertEquals("CRASH", DiagnosticEventJson.decodeOrNull(eventLines[0])?.category)
        assertEquals("EXIT_ANR", DiagnosticEventJson.decodeOrNull(eventLines[1])?.category)
        assertTrue("堆栈关键字段随包", text.contains("IllegalStateException"))
    }

    @Test
    fun unknownFieldsAreOmittedNotFabricated() {
        val text = DiagnosticsBundleExporter.bundleText(
            events = emptyList(),
            meta = DiagnosticsBundleMeta(versionName = null, versionCode = null, flavor = null, generatedAtEpochMillis = 1L),
        )
        assertFalse("读不到就缺行，不假造", text.contains("# versionName="))
        assertFalse(text.contains("# versionCode="))
        assertFalse(text.contains("# flavor="))
        assertTrue(text.contains("# entries=0"))
        assertTrue(text.lines().none { it.startsWith("{") })
    }

    @Test
    fun sha256MatchesJdkReference() {
        val bytes = "smart-mistake-book diagnostics".toByteArray(Charsets.UTF_8)
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
        assertTrue(
            MessageDigest.isEqual(expected, DiagnosticsBundleExporter.sha256(bytes)),
        )
        assertTrue(
            MessageDigest.isEqual(
                expected,
                DiagnosticsBundleExporter.sha256(ByteArrayInputStream(bytes)),
            ),
        )
    }
}
