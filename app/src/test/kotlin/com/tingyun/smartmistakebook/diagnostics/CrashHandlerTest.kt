package com.tingyun.smartmistakebook.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6：崩溃处理器——先落盘、再链回 previous handler（系统杀进程行为不变）。
 * 记录失败绝不吞掉原崩溃：链回必须仍然发生。
 */
class CrashHandlerTest {

    private class RecordingPrevious : Thread.UncaughtExceptionHandler {
        var calledWith: Pair<Thread, Throwable>? = null
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            calledWith = thread to throwable
        }
    }

    @Test
    fun recordsEventAndChainsToPreviousHandler() {
        val recorded = mutableListOf<Pair<Thread, Throwable>>()
        val previous = RecordingPrevious()
        val handler = CrashHandler(
            record = { thread, throwable -> recorded.add(thread to throwable) },
            previous = previous,
        )
        val thread = Thread.currentThread()
        val failure = IllegalStateException("boom")
        handler.uncaughtException(thread, failure)
        assertEquals(listOf(thread to failure), recorded)
        assertEquals(thread to failure, previous.calledWith)
    }

    @Test
    fun recordingFailureStillChainsToPreviousHandler() {
        val previous = RecordingPrevious()
        val handler = CrashHandler(
            record = { _, _ -> throw RuntimeException("ledger write failed") },
            previous = previous,
        )
        val thread = Thread.currentThread()
        val failure = IllegalStateException("original crash")
        handler.uncaughtException(thread, failure)
        assertSame(thread, previous.calledWith?.first)
        assertSame(failure, previous.calledWith?.second)
    }

    @Test
    fun nullPreviousHandlerDoesNotThrow() {
        val handler = CrashHandler(record = { _, _ -> }, previous = null)
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        assertTrue(true)
    }
}
