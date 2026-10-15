package com.tingyun.smartmistakebook

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.model.ModelCallFailureDiagnostics
import com.tingyun.smartmistakebook.core.data.model.ModelCallFailureRecord
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 讲题失败构成诊断的开关制通道（KD-38/AUD-24 证据链）：**关着不写、开着写、原文截断到上限、
 * 条目封顶 20 条**。
 *
 * 走的是生产同一条路径：广播（[TestCaptureModelFailuresReceiver]）→ 捕获缝
 * （[ModelCallFailureDiagnostics.sink]，网关失败时就调它）→ 落盘（[DebugModelFailureCapture]）。
 * 用例之间互不影响：每个用例先关再清目录，结束时同样清掉——这些文件含学生内容，不留设备上。
 */
@RunWith(AndroidJUnit4::class)
class DebugModelFailureCaptureInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = DebugModelFailureCapture.directory(context)

    @Before
    fun startFromOffWithAnEmptyDirectory() {
        DebugModelFailureCapture.uninstall()
        directory.deleteRecursively()
    }

    @After
    fun cleanUp() {
        DebugModelFailureCapture.uninstall()
        directory.deleteRecursively()
    }

    @Test
    fun captureStaysOffUntilTheDebugBroadcastTurnsItOn() {
        // 关（默认）：缝没被武装，一次"失败的模型调用"不产生任何文件
        assertNull(ModelCallFailureDiagnostics.sink)
        deliverFailure(record(occurredAtEpochMillis = 1_700_000_000_000L))
        assertEquals(0, capturedFiles().size)

        // 别的广播（action 不匹配）不武装：开关不能靠任何一条广播顺带打开
        TestCaptureModelFailuresReceiver().onReceive(context, Intent("com.example.OTHER"))
        assertNull(ModelCallFailureDiagnostics.sink)
        assertEquals(0, capturedFiles().size)

        // 开：同一条失败路径写出一个条目，文件名带时间与 task kind
        sendToggle(enabled = true)
        deliverFailure(record(occurredAtEpochMillis = 1_700_000_000_000L))
        val written = capturedFiles()
        assertEquals(1, written.size)
        assertEquals("failure-1700000000000-00-TUTOR_PLAN.txt", written.single().name)

        // 关：再关掉之后失败不再落盘（已落的条目保留，供复核）
        sendToggle(enabled = false)
        assertNull(ModelCallFailureDiagnostics.sink)
        deliverFailure(record(occurredAtEpochMillis = 1_700_000_000_001L))
        assertEquals(1, capturedFiles().size)
    }

    @Test
    fun capturedResponseIsTruncatedToTheByteBudgetAndCarriesTheStudentContentNotice() {
        sendToggle(enabled = true)
        // 720 000 字节的原文，远过 64 KiB 上限
        val rawResponse = "题面原文".repeat(60_000)
        deliverFailure(record(occurredAtEpochMillis = 1_700_000_001_000L, rawResponse = rawResponse))

        val entry = capturedFiles().single().readText()
        assertTrue(
            "条目必须自带提示：${DebugModelFailureCapture.STUDENT_CONTENT_NOTICE}",
            entry.contains(DebugModelFailureCapture.STUDENT_CONTENT_NOTICE),
        )
        assertTrue("条目要写明原始大小", entry.contains("720000 字节"))
        assertTrue("条目要写明截断", entry.contains("超过 ${DebugModelFailureCapture.MAX_CAPTURED_RESPONSE_BYTES} 字节上限"))

        val captured = entry.substringAfter("---------- 响应原文 ----------\n")
        val capturedBytes = captured.toByteArray(Charsets.UTF_8).size
        assertTrue("截断不得越过上限（实际 $capturedBytes）", capturedBytes <= 64 * 1024)
        // 紧贴上限：多字节字符最多让出 3 字节的边界
        assertTrue("截断要贴住上限（实际 $capturedBytes）", capturedBytes >= 64 * 1024 - 3)
        assertTrue("截断处不能切碎字符", captured.startsWith("题面原文"))
        // 输入全是 3 字节字符：字节数是 3 的倍数 ⇒ 没有在字符中间下刀
        assertEquals(0, capturedBytes % 3)
    }

    @Test
    fun capturedEntriesAreCappedAtTwentyKeepingTheNewest() {
        sendToggle(enabled = true)
        repeat(25) { index ->
            deliverFailure(record(occurredAtEpochMillis = 1_700_000_100_000L + index))
        }

        val kept = capturedFiles().map { it.name.removePrefix("failure-").substringBefore('-').toLong() }
        assertEquals(20, kept.size)
        assertEquals(
            "留下的必须是最新的 20 次失败",
            (5 until 25).map { 1_700_000_100_000L + it }.sorted(),
            kept.sorted(),
        )
    }

    @Test
    fun twoFailuresInTheSameMillisecondDoNotCollapseIntoOneEntry() {
        sendToggle(enabled = true)
        deliverFailure(record(occurredAtEpochMillis = 1_700_000_200_000L))
        deliverFailure(record(occurredAtEpochMillis = 1_700_000_200_000L))

        assertEquals(
            listOf(
                "failure-1700000200000-00-TUTOR_PLAN.txt",
                "failure-1700000200000-01-TUTOR_PLAN.txt",
            ),
            capturedFiles().map { it.name }.sorted(),
        )
    }

    private fun sendToggle(enabled: Boolean) {
        TestCaptureModelFailuresReceiver().onReceive(
            context,
            Intent(TestCaptureModelFailuresReceiver.ACTION_TEST_CAPTURE_MODEL_FAILURES)
                .putExtra(TestCaptureModelFailuresReceiver.EXTRA_ENABLED, enabled),
        )
    }

    /**
     * 网关失败时做的就是这一次调用（`core:data` 的 reportModelCallFailure 里 `sink(record)`）：
     * 开关关着时 sink 为 null，这里也就什么都不发生——这正是"关着不写"的判据。
     */
    private fun deliverFailure(record: ModelCallFailureRecord) {
        ModelCallFailureDiagnostics.sink?.invoke(record)
    }

    private fun record(
        occurredAtEpochMillis: Long,
        rawResponse: String? = """{"error":{"message":"Tutor plan rejected"}}""",
    ) = ModelCallFailureRecord(
        occurredAtEpochMillis = occurredAtEpochMillis,
        taskKind = ModelTaskKind.TUTOR_PLAN,
        failureCode = ModelFailureCode.INVALID_RESPONSE,
        retryable = false,
        httpStatus = 200,
        exceptionClass = "java.lang.IllegalArgumentException",
        exceptionMessage = "Tutor move label exceeds budget",
        rawResponse = rawResponse,
    )

    private fun capturedFiles(): List<File> =
        DebugModelFailureCapture.directory(context)
            .listFiles { file -> file.isFile }
            ?.toList()
            .orEmpty()
}
