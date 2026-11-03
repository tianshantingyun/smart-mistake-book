package com.tingyun.smartmistakebook.core.data.telemetry

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryBufferTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("smbk-telemetry-buffer").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `constructor does not create the directory before the first append`() {
        val directory = File(root, "not-created-yet")
        val buffer = TelemetryBuffer(directory)

        assertFalse(directory.exists())
        assertFalse(buffer.hasFiles())

        assertTrue(buffer.append("{\"schema\":1}"))
        assertTrue(directory.isDirectory)
        assertTrue(buffer.hasFiles())
    }

    @Test
    fun `entry cap keeps the newest entries and drops the oldest`() {
        val buffer = TelemetryBuffer(
            directory = File(root, "entry-cap"),
            maxFiles = 10,
            maxFileBytes = 10_000,
            maxEntries = 3,
        )

        listOf("a", "b", "c", "d").forEach { assertTrue(buffer.append(it)) }

        assertEquals(listOf("b", "c", "d"), buffer.readAll())
        assertEquals(3, buffer.entryCount())
    }

    @Test
    fun `file count and per-file bytes stay bounded`() {
        val buffer = TelemetryBuffer(
            directory = File(root, "file-cap"),
            maxFiles = 2,
            maxFileBytes = 20,
            maxEntries = 1_000,
        )

        repeat(6) { index -> assertTrue(buffer.append("0123456789abcde$index")) }

        val files = File(root, "file-cap").listFiles { file -> file.name.endsWith(".jsonl") }.orEmpty()
        assertEquals(2, files.size)
        files.forEach { file -> assertTrue("${file.name} is ${file.length()} bytes", file.length() <= 20L) }
        assertTrue(buffer.totalBytes() <= 40L)
    }

    @Test
    fun `a crash-truncated half line is ignored on read`() {
        val directory = File(root, "half-line").also { it.mkdirs() }
        File(directory, "metrics-000001.jsonl").writeText("complete\nhalf", Charsets.UTF_8)

        val buffer = TelemetryBuffer(directory)

        assertEquals(listOf("complete"), buffer.readAll())
        assertEquals(1, buffer.entryCount())
    }

    @Test
    fun `a single oversize line still lands in an empty file`() {
        val buffer = TelemetryBuffer(
            directory = File(root, "oversize"),
            maxFileBytes = 5,
        )
        val line = "x".repeat(40)

        assertTrue(buffer.append(line))
        assertEquals(listOf(line), buffer.readAll())
    }

    @Test
    fun `purge removes files and the now-empty directory`() {
        val directory = File(root, "purge")
        val buffer = TelemetryBuffer(directory)
        assertTrue(buffer.append("line"))

        buffer.purge()

        assertFalse(directory.exists())
        assertFalse(buffer.hasFiles())
        assertEquals(emptyList<String>(), buffer.readAll())
    }

    @Test
    fun `default caps match the crash-ledger three-way bound`() {
        assertEquals(6, TelemetryBuffer.DEFAULT_MAX_FILES)
        assertEquals(64L * 1024L, TelemetryBuffer.DEFAULT_MAX_FILE_BYTES)
        assertEquals(300, TelemetryBuffer.DEFAULT_MAX_ENTRIES)
    }
}
