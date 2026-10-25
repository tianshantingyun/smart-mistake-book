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
 * T6：环形文件账本——文件数/字节/条目三重封顶、自动裁剪、半行容错、水位线往返。
 */
class CrashLedgerTest {

    private lateinit var tempDir: java.io.File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("diagnostics-ledger").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun event(at: Long, salt: String = "") = DiagnosticEvent(
        atEpochMillis = at,
        category = "CRASH",
        threadName = "main",
        message = "boom $at $salt ${"x".repeat(120)}",
        stackTrace = null,
    )

    @Test
    fun readAllRoundTripsAppendedEventsInOrder() {
        val ledger = CrashLedger(tempDir, maxFiles = 4, maxFileBytes = 64 * 1024, maxEntries = 200)
        val events = (1..5).map { event(it.toLong()) }
        events.forEach(ledger::append)
        assertEquals(events, ledger.readAll())
    }

    @Test
    fun rotatesFilesAndDropsOldestWhenFileBudgetExceeded() {
        val ledger = CrashLedger(tempDir, maxFiles = 2, maxFileBytes = 256, maxEntries = Int.MAX_VALUE)
        val events = (1..10).map { event(it.toLong()) }
        events.forEach(ledger::append)
        val files = tempDir.listFiles { file -> file.name.startsWith("events-") }
        assertNotNull(files)
        assertTrue("文件数封顶：${files!!.size} <= 2", files.size <= 2)
        val read = ledger.readAll()
        assertTrue("最旧事件被裁掉", read.none { it.atEpochMillis == 1L })
        assertTrue("最新事件保留", read.any { it.atEpochMillis == 10L })
    }

    @Test
    fun trimsToEntryCapKeepingNewest() {
        val ledger = CrashLedger(tempDir, maxFiles = 8, maxFileBytes = 64 * 1024, maxEntries = 3)
        (1..5).forEach { ledger.append(event(it.toLong())) }
        val read = ledger.readAll()
        assertEquals(3, read.size)
        assertEquals(listOf(3L, 4L, 5L), read.map { it.atEpochMillis })
    }

    @Test
    fun entryCapCanTrimInsideOldestFile() {
        // 单文件能装下远超 maxEntries 的条数：裁剪必须能在最旧文件内部丢头部行
        val ledger = CrashLedger(tempDir, maxFiles = 8, maxFileBytes = 64 * 1024, maxEntries = 2)
        (1..5).forEach { ledger.append(event(it.toLong())) }
        val read = ledger.readAll()
        assertEquals(2, read.size)
        assertEquals(listOf(4L, 5L), read.map { it.atEpochMillis })
    }

    @Test
    fun totalBytesStayBoundedAfterManyAppends() {
        val maxFiles = 2
        val maxFileBytes = 512L
        val ledger = CrashLedger(tempDir, maxFiles = maxFiles, maxFileBytes = maxFileBytes, maxEntries = 1000)
        (1..40).forEach { ledger.append(event(it.toLong())) }
        val total = tempDir.listFiles { file -> file.name.startsWith("events-") }
            ?.sumOf { it.length() } ?: 0L
        // 单行可略超文件预算（一行不拆），界为 maxFiles*(预算+单行上限)；事件构造 ~200B。
        assertTrue("总量封顶：$total", total <= maxFiles * (maxFileBytes + 1024))
    }

    @Test
    fun readAllSkipsIncompleteTrailingLineWithoutThrowing() {
        val ledger = CrashLedger(tempDir, maxFiles = 4, maxFileBytes = 64 * 1024, maxEntries = 100)
        ledger.append(event(1L))
        ledger.append(event(2L))
        // 模拟崩溃中断：手工往最新文件尾部追加半行
        val newest = tempDir.listFiles { file -> file.name.startsWith("events-") }!!
            .maxByOrNull { it.name }!!
        newest.appendText("""{"atEpochMillis":3,"category":"CR""")
        val read = ledger.readAll()
        assertEquals(listOf(1L, 2L), read.map { it.atEpochMillis })
    }

    @Test
    fun emptyLedgerReadsAsEmpty() {
        val ledger = CrashLedger(tempDir, maxFiles = 4, maxFileBytes = 64 * 1024, maxEntries = 10)
        assertEquals(emptyList<DiagnosticEvent>(), ledger.readAll())
    }

    @Test
    fun sessionWatermarkRoundTrips() {
        assertNull(SessionWatermark.readOrNull(tempDir))
        SessionWatermark.write(tempDir, 123456789L)
        assertEquals(123456789L, SessionWatermark.readOrNull(tempDir))
        SessionWatermark.write(tempDir, 987654321L)
        assertEquals(987654321L, SessionWatermark.readOrNull(tempDir))
    }
}
