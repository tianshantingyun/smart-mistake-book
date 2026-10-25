package com.tingyun.smartmistakebook.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6：主线程看门狗的判定核（纯逻辑）。看门狗只记录、从不杀进程；一个阻塞事件期只上报一次。
 */
class AnrWatchdogMonitorTest {

    @Test
    fun firesOncePerBlockageEpisode() {
        var now = 0L
        val reported = mutableListOf<Long>()
        val monitor = AnrWatchdogMonitor(
            blockageThresholdMillis = 10_000L,
            nowMillis = { now },
            onBlockage = { blockedFor -> reported.add(blockedFor) },
        )
        now = 9_999L
        assertFalse("阈值未到不报", monitor.onCheck())
        now = 11_000L
        assertTrue("超阈值上报", monitor.onCheck())
        assertEquals(listOf(11_000L), reported)
        now = 15_000L
        assertFalse("同一阻塞事件期不重复上报", monitor.onCheck())
        assertEquals(listOf(11_000L), reported)
        // 主线程恢复心跳：事件期关闭
        monitor.onTickExecuted()
        assertFalse("恢复后立即检查不报", monitor.onCheck())
        now = monitor.lastTickAtMillisForTest() + 10_001L
        assertTrue("再次阻塞构成新事件期", monitor.onCheck())
        assertEquals(2, reported.size)
    }

    @Test
    fun regularTicksNeverFire() {
        var now = 0L
        val reported = mutableListOf<Long>()
        val monitor = AnrWatchdogMonitor(
            blockageThresholdMillis = 10_000L,
            nowMillis = { now },
            onBlockage = { reported.add(it) },
        )
        repeat(20) {
            now += 2_000L
            monitor.onTickExecuted()
            assertFalse(monitor.onCheck())
        }
        assertTrue(reported.isEmpty())
    }
}
