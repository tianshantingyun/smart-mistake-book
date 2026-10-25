package com.tingyun.smartmistakebook.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T6（beta 反馈闭环）：诊断事件 JSONL 编解码。手写编解码的正当性来自 app 模块零 JSON
 * 依赖（app/build.gradle.kts 无 serialization 插件）；往返正确性由本测试钉住。
 */
class DiagnosticEventJsonTest {

    @Test
    fun roundtripPreservesAllFieldsIncludingHostileStrings() {
        val event = DiagnosticEvent(
            atEpochMillis = 1_761_000_000_000L,
            category = "CRASH",
            threadName = "main",
            message = "引号\" 反斜杠\\ 换行\n 制表\t 回车\r 控制\u0001 emoji 😀 中文",
            stackTrace = "java.lang.IllegalStateException: boom\n" +
                "\tat com.example.Foo.bar(Foo.kt:1)\n" +
                "\tat \"quoted\"",
            versionName = "0.2.0",
            versionCode = 2L,
            flavor = "strictOffline",
        )
        val decoded = DiagnosticEventJson.decodeOrNull(DiagnosticEventJson.encode(event))
        assertEquals(event, decoded)
    }

    @Test
    fun roundtripPreservesNullOptionalFields() {
        val event = DiagnosticEvent(
            atEpochMillis = 42L,
            category = "ANR_WATCHDOG",
            threadName = "main",
            message = null,
            stackTrace = null,
            versionName = null,
            versionCode = null,
            flavor = null,
        )
        assertEquals(event, DiagnosticEventJson.decodeOrNull(DiagnosticEventJson.encode(event)))
    }

    @Test
    fun encodedEventIsSingleLine() {
        val event = DiagnosticEvent(
            atEpochMillis = 1L,
            category = "CRASH",
            threadName = "main",
            message = "multi\nline\r\nmessage",
            stackTrace = "line1\nline2",
        )
        val encoded = DiagnosticEventJson.encode(event)
        assertEquals(
            "一行一事件：编码后的行里不允许出现裸换行（环形账本按行读）",
            0,
            encoded.count { it == '\n' || it == '\r' },
        )
    }

    @Test
    fun decodeSkipsMalformedLines() {
        assertNull(DiagnosticEventJson.decodeOrNull(""))
        assertNull(DiagnosticEventJson.decodeOrNull("   "))
        assertNull(DiagnosticEventJson.decodeOrNull("garbage"))
        // 崩溃中断留下的半行
        assertNull(DiagnosticEventJson.decodeOrNull("""{"atEpochMillis":12"""))
        // 缺必需字段
        assertNull(DiagnosticEventJson.decodeOrNull("{}"))
        assertNull(
            DiagnosticEventJson.decodeOrNull(
                """{"atEpochMillis":1,"category":"CRASH"}""",
            ),
        )
        // 尾随内容（两个对象拼在一起不是一行事件）
        assertNull(
            DiagnosticEventJson.decodeOrNull(
                """{"atEpochMillis":1,"category":"CRASH","threadName":"t"}x""",
            ),
        )
        // 原始控制字符不允许出现在字符串里
        assertNull(
            DiagnosticEventJson.decodeOrNull(
                "{\"atEpochMillis\":1,\"category\":\"CR\",\"threadName\":\"a\u0001b\"}",
            ),
        )
    }

    @Test
    fun decodeIgnoresUnknownKeysForForwardCompatibility() {
        val encoded = DiagnosticEventJson.encode(
            DiagnosticEvent(atEpochMillis = 7L, category = "CRASH", threadName = "main"),
        )
        val withFutureField = encoded.dropLast(1) + ""","futureField":"x"}"""
        val decoded = DiagnosticEventJson.decodeOrNull(withFutureField)
        assertEquals(7L, decoded?.atEpochMillis)
        assertEquals("CRASH", decoded?.category)
        assertEquals("main", decoded?.threadName)
    }
}
