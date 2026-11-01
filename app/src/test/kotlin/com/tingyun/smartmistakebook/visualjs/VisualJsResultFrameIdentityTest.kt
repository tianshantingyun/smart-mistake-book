package com.tingyun.smartmistakebook.visualjs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果帧消费者身份校验的 JVM 负向单测（批次 25 T3；R1' §4 A-12/A-13 的 JVM 部分，
 * 契约 §64 末句"消费者须验证服务调用身份"）。
 *
 * 覆盖批次 24 复核确认缺失的独立负向用例：此前三重核对内联在 Binder Stub 里，
 * 伪造 generation/requestId/UID 的丢弃分支没有脱离真进程的断言（R1' A-12"旧 requestId
 * 帧丢弃的独立负向用例未见"）。任何一项伪造都必须拒绝——只测 happy path 不能证明
 * "不把另一帧当本帧结果"。
 */
class VisualJsResultFrameIdentityTest {

    private val linkGeneration = 101L
    private val requestId = "request-1"
    private val workerUid = 99_123

    @Test
    fun `a frame matching all three identities is accepted`() {
        assertTrue(
            VisualJsResultFrameFilter.accepts(
                frameGeneration = linkGeneration,
                frameRequestId = requestId,
                callerUid = workerUid,
                linkGeneration = linkGeneration,
                requestId = requestId,
                workerUid = workerUid,
            ),
        )
    }

    @Test
    fun `a stale generation frame is dropped`() {
        assertFalse(
            VisualJsResultFrameFilter.accepts(
                frameGeneration = linkGeneration + 1,
                frameRequestId = requestId,
                callerUid = workerUid,
                linkGeneration = linkGeneration,
                requestId = requestId,
                workerUid = workerUid,
            ),
        )
    }

    @Test
    fun `a stale request id frame is dropped`() {
        assertFalse(
            VisualJsResultFrameFilter.accepts(
                frameGeneration = linkGeneration,
                frameRequestId = "another-request",
                callerUid = workerUid,
                linkGeneration = linkGeneration,
                requestId = requestId,
                workerUid = workerUid,
            ),
        )
    }

    @Test
    fun `a null request id frame is dropped`() {
        assertFalse(
            VisualJsResultFrameFilter.accepts(
                frameGeneration = linkGeneration,
                frameRequestId = null,
                callerUid = workerUid,
                linkGeneration = linkGeneration,
                requestId = requestId,
                workerUid = workerUid,
            ),
        )
    }

    @Test
    fun `a forged caller uid frame is dropped`() {
        // 非 worker UID（宿主自己或第三方进程伪造的回调）必须被拒（A-13）
        assertFalse(
            VisualJsResultFrameFilter.accepts(
                frameGeneration = linkGeneration,
                frameRequestId = requestId,
                callerUid = workerUid + 1,
                linkGeneration = linkGeneration,
                requestId = requestId,
                workerUid = workerUid,
            ),
        )
    }
}
