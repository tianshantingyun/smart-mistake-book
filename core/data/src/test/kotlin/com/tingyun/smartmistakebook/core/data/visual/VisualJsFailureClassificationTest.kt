package com.tingyun.smartmistakebook.core.data.visual

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [classifyVisualJsFailure] 的 JVM 纯函数用例（批次 22 T2；矩阵来自 R3 §6.4）。
 *
 * **不变量**：本类不实例化 [com.dokar.quickjs.QuickJs]，不触发 `System.loadLibrary("quickjs")`。
 * 只构造库的异常值类型（[QuickJsException]/[QuickJsInterruptedException]，纯 Kotlin 类），
 * 因此可在无原生库的 JVM 门执行。
 *
 * 覆盖三层：类型分支（中断优先于消息）、消息分支（严格前缀/沙箱/程序错误）、
 * UNKNOWN 兜底（消息丢失且无堆证据 → 内部 UNATTRIBUTED，对外折 INVALID_PROGRAM + 前缀）。
 * 阈值边界与 `unlimited` 上限也在本类钉住，防止有人为让设备变绿而下调 90%。
 */
class VisualJsFailureClassificationTest {

    private val phase = "prepare"

    private fun nearLimitHeap() = HeapSnapshot(
        mallocSize = 16_700_000L,
        mallocLimit = 16_777_216L,
    )

    private fun classify(
        message: String?,
        heap: HeapSnapshot? = null,
        error: QuickJsException = QuickJsException(message),
    ): VisualJsFailure =
        classifyVisualJsFailure(phase, error, heap, cooperativeTimeoutMillis = 10_000L)

    // ------------------------------------------------------------ 类型分支

    @Test
    fun `interrupted type wins over an out of memory message`() {
        // 消息面完全像 OOM，但类型是协作中断：类型必须优先，不能被误归 MEMORY_LIMIT。
        val failure = classify(
            message = "InternalError: out of memory",
            heap = nearLimitHeap(),
            error = QuickJsInterruptedException("InternalError: out of memory"),
        )

        assertEquals(VisualJsFailureKind.COOPERATIVE_TIMEOUT, failure.kind)
        assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, failure.reason)
        assertTrue(failure.diagnostics.contains("10000 ms"))
        assertFalse(failure.diagnostics.contains("[UNATTRIBUTED]"))
    }

    // ------------------------------------------------------------ 消息分支

    @Test
    fun `the engine internal error prefix is a memory limit`() {
        val failure = classify("InternalError: out of memory\n    at prepare")

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertEquals(VisualExecutionRejection.MEMORY_LIMIT, failure.reason)
        assertTrue(failure.diagnostics.contains("16777216"))
        assertTrue(failure.diagnostics.contains("out of memory"))
    }

    @Test
    fun `a forged range error with the oom text stays an invalid program`() {
        // 旧实现 contains("out of memory") 会把这条误报成 MEMORY_LIMIT。
        val failure = classify("RangeError: out of memory")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertFalse(failure.diagnostics.contains("[UNATTRIBUTED]"))
    }

    @Test
    fun `a bare out of memory string is not the engine oom prefix`() {
        val failure = classify("out of memory")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
    }

    @Test
    fun `string too long is a program error even when the heap looks full`() {
        // 旧夹具红例的机制：单字符串长度上限（2^30-1）与堆上限无关；
        // 消息分支先于堆证据，堆再满也不得归 MEMORY_LIMIT。
        val failure = classify("InternalError: string too long\n    at prepare", nearLimitHeap())

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
        assertTrue(failure.diagnostics.contains("string too long"))
    }

    @Test
    fun `a missing global is a sandbox violation`() {
        val failure = classify("ReferenceError: fetch is not defined")

        assertEquals(VisualJsFailureKind.SANDBOX_VIOLATION, failure.kind)
        assertEquals(VisualExecutionRejection.SANDBOX_VIOLATION, failure.reason)
        assertTrue(failure.diagnostics.contains("fetch"))
    }

    @Test
    fun `an ordinary type error keeps its bounded diagnostic`() {
        val failure = classify("TypeError: boom-js")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
        assertTrue(failure.diagnostics.contains("boom-js"))
    }

    // ------------------------------------------------------------ 丢失哨兵 + 堆证据

    @Test
    fun `a lost message near the heap limit is attributed to memory`() {
        val failure = classify("null", nearLimitHeap())

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertEquals(VisualExecutionRejection.MEMORY_LIMIT, failure.reason)
        assertTrue(failure.diagnostics.contains("attributed by memoryUsage"))
        assertTrue(failure.diagnostics.contains("mallocSize=16700000"))
        assertTrue(failure.diagnostics.contains("mallocLimit=16777216"))
    }

    @Test
    fun `the invalid evaluation handle sentinel is attributed by heap evidence`() {
        val failure = classify("Invalid evaluation handle: -1", nearLimitHeap())

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertTrue(failure.diagnostics.contains("attributed by memoryUsage"))
    }

    @Test
    fun `a java null message near the heap limit is attributed to memory`() {
        val failure = classify(null, nearLimitHeap())

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
    }

    // ------------------------------------------------------------ UNKNOWN 兜底

    @Test
    fun `a lost message with a low heap is unattributed`() {
        val failure = classify("null", HeapSnapshot(mallocSize = 1_000_000L, mallocLimit = 16_777_216L))

        assertEquals(VisualJsFailureKind.UNATTRIBUTED, failure.kind)
        // 对外枚举不变：折成 INVALID_PROGRAM，靠诊断前缀区分。
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertTrue(failure.diagnostics.startsWith(UNATTRIBUTED_DIAGNOSTIC_PREFIX))
        assertTrue(failure.diagnostics.contains("mallocSize=1000000"))
        assertTrue(failure.diagnostics.contains("mallocLimit=16777216"))
    }

    @Test
    fun `a lost message without a heap snapshot is unattributed`() {
        val failure = classify("null", heap = null)

        assertEquals(VisualJsFailureKind.UNATTRIBUTED, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertTrue(failure.diagnostics.startsWith(UNATTRIBUTED_DIAGNOSTIC_PREFIX))
    }

    // ------------------------------------------------------------ 90% 阈值与上限

    @Test
    fun `the near limit threshold is inclusive at ninety percent`() {
        // 14_400_000 / 16_000_000 = 90%：含边界归属堆上限。
        val atThreshold = HeapSnapshot(mallocSize = 14_400_000L, mallocLimit = 16_000_000L)
        val justBelow = HeapSnapshot(mallocSize = 14_399_999L, mallocLimit = 16_000_000L)

        assertTrue(atThreshold.isNearLimit())
        assertFalse(justBelow.isNearLimit())
    }

    @Test
    fun `an unlimited or zero heap limit is never attributed`() {
        // mallocLimit <= 0 表示未设上限（bellard 默认 -1）：不能用"接近上限"的证据。
        assertEquals(
            VisualJsFailureKind.UNATTRIBUTED,
            classify("null", HeapSnapshot(mallocSize = 16_000_000L, mallocLimit = 0L)).kind,
        )
        assertEquals(
            VisualJsFailureKind.UNATTRIBUTED,
            classify("null", HeapSnapshot(mallocSize = 16_000_000L, mallocLimit = -1L)).kind,
        )
    }
}
