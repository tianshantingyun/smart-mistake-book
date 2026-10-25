package com.tingyun.smartmistakebook.diagnostics

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T6：启动对账——API 30+ 退出记录（经 seam 注入假数据）→ 本机账本；时间窗/去重/分类；
 * "上次异常退出"横幅文案。API<30 的语义（source 恒空）由 emptySource 用例代表。
 */
class StartupExitReconcilerTest {

    private lateinit var tempDir: java.io.File
    private lateinit var ledger: CrashLedger

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("diagnostics-reconciler").toFile()
        ledger = CrashLedger(tempDir, maxFiles = 8, maxFileBytes = 64 * 1024, maxEntries = 100)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun ledgerEvent(at: Long, category: String) = DiagnosticEvent(
        atEpochMillis = at,
        category = category,
        threadName = "main",
    )

    @Test
    fun recordsPreStartExitsWithExitCategories() {
        val source = ProcessExitInfoSource {
            listOf(
                RawProcessExit(atEpochMillis = 1_000L, exitClass = ExitClass.ANR, description = "Input dispatching timed out"),
                RawProcessExit(atEpochMillis = 2_000L, exitClass = ExitClass.CRASH_NATIVE, description = null),
                RawProcessExit(atEpochMillis = 3_000L, exitClass = ExitClass.LOW_MEMORY, description = null),
            )
        }
        val reconciler = StartupExitReconciler(ledger, source)
        val report = reconciler.reconcile(
            processStartedAtEpochMillis = 10_000L,
            previousSessionStartAtEpochMillis = 500L,
        )
        assertEquals(3, report.recordedExitEvents)
        val recorded = ledger.readAll()
        assertEquals(listOf("EXIT_ANR", "EXIT_CRASH_NATIVE", "EXIT_LOW_MEMORY"), recorded.map { it.category })
        assertTrue(recorded.all { it.threadName == "(previous process)" })
        assertEquals("Input dispatching timed out", recorded.first { it.category == "EXIT_ANR" }.message)
        assertNull(recorded.first { it.category == "EXIT_CRASH_NATIVE" }.message)
    }

    @Test
    fun ignoresExitsAtOrAfterProcessStart() {
        val source = ProcessExitInfoSource {
            listOf(
                RawProcessExit(atEpochMillis = 10_000L, exitClass = ExitClass.ANR),
                RawProcessExit(atEpochMillis = 12_000L, exitClass = ExitClass.CRASH),
            )
        }
        val report = StartupExitReconciler(ledger, source).reconcile(
            processStartedAtEpochMillis = 10_000L,
            previousSessionStartAtEpochMillis = null,
        )
        assertEquals(0, report.recordedExitEvents)
        assertTrue(ledger.readAll().isEmpty())
    }

    @Test
    fun dedupsAgainstAlreadyRecordedExits() {
        ledger.append(ledgerEvent(at = 2_000L, category = "EXIT_ANR"))
        val source = ProcessExitInfoSource {
            listOf(
                RawProcessExit(atEpochMillis = 1_500L, exitClass = ExitClass.ANR), // 已有更晚记录：跳过
                RawProcessExit(atEpochMillis = 2_500L, exitClass = ExitClass.CRASH), // 更新：记录
            )
        }
        val report = StartupExitReconciler(ledger, source).reconcile(
            processStartedAtEpochMillis = 10_000L,
            previousSessionStartAtEpochMillis = null,
        )
        assertEquals(1, report.recordedExitEvents)
        assertEquals(2, ledger.readAll().size)
        assertNotNull(ledger.readAll().firstOrNull { it.category == "EXIT_CRASH" })
    }

    @Test
    fun emptySourceStillSurfacesLedgerCrashAsNotice() {
        // API 23–29：系统退出记录不可用，但 handler/看门狗落的事件仍然要撑起横幅
        ledger.append(ledgerEvent(at = 8_000L, category = "CRASH"))
        val report = StartupExitReconciler(ledger, ProcessExitInfoSource { emptyList() }).reconcile(
            processStartedAtEpochMillis = 9_000L,
            previousSessionStartAtEpochMillis = 6_000L,
        )
        assertEquals(0, report.recordedExitEvents)
        assertNotNull(report.notice)
        assertTrue("横幅说明只存本机", report.notice!!.contains("本机"))
        assertTrue("横幅给出类别", report.notice!!.contains("崩溃"))
    }

    @Test
    fun noticeReportsNewestRelevantEventOnly() {
        ledger.append(ledgerEvent(at = 7_000L, category = "ANR_WATCHDOG"))
        ledger.append(ledgerEvent(at = 8_000L, category = "EXIT_ANR"))
        ledger.append(ledgerEvent(at = 9_000L, category = "COROUTINE_FAILURE")) // 非退出类：不进横幅
        val report = StartupExitReconciler(ledger, ProcessExitInfoSource { emptyList() }).reconcile(
            processStartedAtEpochMillis = 20_000L,
            previousSessionStartAtEpochMillis = 6_000L,
        )
        assertNotNull(report.notice)
        assertTrue("取最新相关事件", report.notice!!.contains("系统判定无响应"))
    }

    @Test
    fun eventsBeforePreviousSessionStartDoNotRaiseNotice() {
        ledger.append(ledgerEvent(at = 1_000L, category = "CRASH"))
        val report = StartupExitReconciler(ledger, ProcessExitInfoSource { emptyList() }).reconcile(
            processStartedAtEpochMillis = 20_000L,
            previousSessionStartAtEpochMillis = 5_000L,
        )
        assertNull(report.notice)
    }

    @Test
    fun noEventsYieldNullNotice() {
        val report = StartupExitReconciler(ledger, ProcessExitInfoSource { emptyList() }).reconcile(
            processStartedAtEpochMillis = 20_000L,
            previousSessionStartAtEpochMillis = 5_000L,
        )
        assertNull(report.notice)
    }

    @Test
    fun sourceFailureDegradesToEmpty() {
        val source = ProcessExitInfoSource { throw RuntimeException("activity manager unavailable") }
        val report = StartupExitReconciler(ledger, source).reconcile(
            processStartedAtEpochMillis = 20_000L,
            previousSessionStartAtEpochMillis = 5_000L,
        )
        assertEquals(0, report.recordedExitEvents)
        assertNull(report.notice)
    }
}
