package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败诊断记录的**日志边界**（批次 44 / D 域「解析失败分类·日志」检查）。
 *
 * 消灭的具体失败：`ModelCallFailureRecord.rawResponse` 里是学生题面/图片读出的原文。
 * 这个类型是普通 class 而不是 data class，就是为了防止编译器合成的 `toString()` 把原文
 * 带进任何一次日志或异常消息；但这条边界此前没有任何自动化断言——把 `class` 顺手改成
 * `data class`、或为了排障在 `toString()` 里拼上原文，都会把学生内容送进 logcat，而测试
 * 全绿。本用例把「元数据可排障」与「原文永不出现在 toString」两半同时钉住。
 */
class ModelCallFailureRecordLogDisciplineTest {

    private val rawBody = """{"choices":[{"message":{"content":"学生题面原文 3x+2=8"}}]}"""

    private fun record() = ModelCallFailureRecord(
        occurredAtEpochMillis = 1_000,
        taskKind = ModelTaskKind.CAPTURE_ASSESS,
        failureCode = ModelFailureCode.INVALID_RESPONSE,
        retryable = false,
        httpStatus = 200,
        exceptionClass = "InvalidModelResponseException",
        exceptionMessage = "model response is not a JSON object",
        rawResponse = rawBody,
    )

    @Test
    fun toStringNeverEchoesTheRawResponseOrStudentText() {
        val text = record().toString()

        assertFalse("原文不得进 toString：$text", text.contains(rawBody))
        assertFalse("学生题面不得进 toString：$text", text.contains("3x+2=8"))
        assertTrue("必须标明原文长度但打印正文：$text", text.contains("chars（内容不打印）"))
    }

    @Test
    fun toStringKeepsTheMetadataNeededForTriage() {
        val text = record().toString()

        assertTrue(text, text.contains("kind=CAPTURE_ASSESS"))
        assertTrue(text, text.contains("code=INVALID_RESPONSE"))
        assertTrue(text, text.contains("retryable=false"))
        assertTrue(text, text.contains("httpStatus=200"))
        assertTrue(text, text.contains("InvalidModelResponseException"))
    }

    @Test
    fun aConnectionFailureWithoutAResponseIsReportedAsNullNotAsAnEmptyBody() {
        val text = record().copy(null).toString()

        assertTrue(text, text.contains("rawResponse=null"))
        assertEquals(1_000, record().occurredAtEpochMillis)
    }

    private fun ModelCallFailureRecord.copy(rawResponse: String?) = ModelCallFailureRecord(
        occurredAtEpochMillis = occurredAtEpochMillis,
        taskKind = taskKind,
        failureCode = failureCode,
        retryable = retryable,
        httpStatus = httpStatus,
        exceptionClass = exceptionClass,
        exceptionMessage = exceptionMessage,
        rawResponse = rawResponse,
    )
}
