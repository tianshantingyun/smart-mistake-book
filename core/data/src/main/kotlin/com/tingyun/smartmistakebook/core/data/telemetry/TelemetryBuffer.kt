package com.tingyun.smartmistakebook.core.data.telemetry

import java.io.File
import java.io.IOException

/**
 * 本地环形缓冲（R5 §1.3.5/§1.3.6、V8）：目录下若干 JSONL 文件，**文件数 × 单文件字节 ×
 * 总条目数**三重封顶，超限丢最旧；读到崩溃中断的半行时跳过。
 *
 * 消灭的失败：直接把每条指标追加到单个无界文件，会让离线设备上的遥测目录无限增长；
 * 只保留“最新 N 条”才能同时满足本地留存有界与崩溃可恢复。
 *
 * 关键边界：**构造不创建目录/文件**。只有第一次 [append]（即调用方已过同意门）才会
 * `mkdirs`；这是 V2“同意前 0 字节、无 telemetry 目录”的代码级保证。
 */
class TelemetryBuffer(
    private val directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxFiles >= 1) { "maxFiles must be >= 1" }
        require(maxFileBytes > 0L) { "maxFileBytes must be positive" }
        require(maxEntries >= 1) { "maxEntries must be >= 1" }
    }

    private var openFile: File? = null

    @Synchronized
    fun append(line: String): Boolean {
        val normalized = line.trimEnd('\n')
        if (normalized.isEmpty()) return false
        return try {
            if (!directory.isDirectory && !directory.mkdirs()) return false
            val payload = normalized + "\n"
            val target = writableFileFor(payload.toByteArray(Charsets.UTF_8).size.toLong())
            target.appendText(payload, Charsets.UTF_8)
            openFile = target
            trimToEntryCap()
            true
        } catch (_: IOException) {
            // 落盘失败不能把调用方的业务路径变成失败；缓冲只是旁路。
            false
        }
    }

    @Synchronized
    fun readAll(): List<String> {
        val lines = ArrayList<String>()
        for (file in ledgerFiles()) {
            readCompleteLines(file, lines)
        }
        return lines
    }

    @Synchronized
    fun purge() {
        openFile = null
        ledgerFiles().forEach { file ->
            runCatching { file.delete() }
        }
        // 只在自己是空目录时删除，避免误删调用方在同一目录下的其它文件。
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) {
            runCatching { directory.delete() }
        }
    }

    @Synchronized
    fun totalBytes(): Long = ledgerFiles().sumOf { file ->
        runCatching { file.length() }.getOrDefault(0L)
    }

    @Synchronized
    fun entryCount(): Int = ledgerFiles().sumOf(::lineCount)

    /** 目录内是否已有缓冲文件；不创建目录。 */
    @Synchronized
    fun hasFiles(): Boolean = ledgerFiles().isNotEmpty()

    private fun writableFileFor(lineBytes: Long): File {
        val open = openFile
        if (open != null && open.isFile &&
            (open.length() == 0L || open.length() + lineBytes <= maxFileBytes)
        ) {
            // 空文件永远接受：单行超预算也写进空文件，避免无法落盘。
            return open
        }
        return rotate()
    }

    private fun rotate(): File {
        val existing = ledgerFiles()
        if (existing.size >= maxFiles) {
            val toDelete = existing.size - maxFiles + 1
            existing.take(toDelete).forEach { file ->
                runCatching { file.delete() }
                if (openFile == file) openFile = null
            }
        }
        val next = File(directory, nextFileName(ledgerFiles()))
        next.delete()
        return try {
            next.createNewFile()
            next
        } catch (_: IOException) {
            // 建不出新文件就继续写既有的最后一个：记录仍在本机，只是不再轮转。
            existing.lastOrNull { it.isFile } ?: next
        }
    }

    private fun nextFileName(existing: List<File>): String {
        val maxSequence = existing.lastOrNull()?.let(::sequenceOf) ?: 0L
        val next = if (maxSequence >= MAX_SEQUENCE) 1L else maxSequence + 1L
        return FILE_PREFIX + String.format("%06d", next) + FILE_SUFFIX
    }

    private fun sequenceOf(file: File): Long =
        file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX).toLongOrNull() ?: 0L

    private fun ledgerFiles(): List<File> =
        directory.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }?.sortedBy(::sequenceOf) ?: emptyList()

    private fun readCompleteLines(file: File, into: MutableList<String>) {
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return
        }
        var start = 0
        while (start < text.length) {
            val newline = text.indexOf('\n', start)
            if (newline < 0) break
            val line = text.substring(start, newline)
            start = newline + 1
            if (line.isNotEmpty()) into.add(line)
        }
    }

    /** 完整行数（'\n' 计数）；半行不算，与 [readAll] 口径一致。 */
    private fun lineCount(file: File): Int = try {
        file.readText(Charsets.UTF_8).count { it == '\n' }
    } catch (_: IOException) {
        0
    }

    private fun trimToEntryCap() {
        var excess = ledgerFiles().sumOf(::lineCount) - maxEntries
        if (excess <= 0) return
        for (file in ledgerFiles()) {
            if (excess <= 0) return
            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (_: IOException) {
                continue
            }
            val split = text.split('\n')
            val hasHalfLine = split.isNotEmpty() && split.last().isNotEmpty()
            val complete = if (hasHalfLine) split.dropLast(1) else split
            val lines = complete.filter(String::isNotEmpty)
            val drop = excess.coerceAtMost(lines.size)
            if (drop == lines.size) {
                runCatching { file.delete() }
                if (openFile == file) openFile = null
            } else {
                runCatching {
                    file.writeText(
                        lines.drop(drop).joinToString(separator = "\n", postfix = "\n"),
                        Charsets.UTF_8,
                    )
                }
            }
            excess -= drop
        }
    }

    companion object {
        const val DEFAULT_MAX_FILES = 6
        const val DEFAULT_MAX_FILE_BYTES = 64L * 1024L // 6 × 64 KiB = 384 KiB
        const val DEFAULT_MAX_ENTRIES = 300
        const val FILE_PREFIX = "metrics-"
        const val FILE_SUFFIX = ".jsonl"
        private const val MAX_SEQUENCE = 999_999L
    }
}
