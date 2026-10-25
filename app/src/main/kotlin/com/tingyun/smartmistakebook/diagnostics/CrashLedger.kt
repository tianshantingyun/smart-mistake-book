package com.tingyun.smartmistakebook.diagnostics

import java.io.File
import java.io.IOException

/**
 * 环形文件账本（T6）：目录下若干 JSONL 文件，**文件数 × 单文件字节 × 总条目数**三重
 * 封顶，超限自动丢最旧。位置用 `filesDir/diagnostics/`（不用 cacheDir：cache 可被系统
 * 随时清理，崩溃记录不可再生成；R2 §5.1）。
 *
 * 崩溃路径用一次 open→append→close 落盘（不依赖进程内缓冲）；读到崩溃中断留下的半行
 * 时跳过不炸（写读分离容错）。不进 Room/DataStore/备份域：崩溃记录不是学生数据，
 * 是否带出门由用户在导出时决定（R2 §5.3）。
 *
 * 消灭的失败：现状崩溃只进 logcat（R2 F5/F6），重启即丢——本类让它在本机留下有界的、
 * 可导出的记录。
 */
class CrashLedger(
    directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val dir: File = directory

    init {
        require(maxFiles >= 1) { "maxFiles must be >= 1" }
        require(maxFileBytes > 0) { "maxFileBytes must be positive" }
        require(maxEntries >= 1) { "maxEntries must be >= 1" }
    }

    /** 当前追加目标（可能已被裁剪轮转删除，使用前总是重新核 isFile）。 */
    private var openFile: File? = null

    @Synchronized
    fun append(event: DiagnosticEvent) {
        try {
            val line = DiagnosticEventJson.encode(event) + "\n"
            val target = writableFileFor(line.length)
            target.appendText(line, Charsets.UTF_8)
            openFile = target
            trimToEntryCap()
        } catch (_: IOException) {
            // 落盘失败只能吞：崩溃路径上任何二次异常都会打断"链回系统 handler"。
        }
    }

    @Synchronized
    fun readAll(): List<DiagnosticEvent> {
        val events = ArrayList<DiagnosticEvent>()
        for (file in ledgerFiles()) {
            readEvents(file, events)
        }
        return events
    }

    private fun writableFileFor(lineBytes: Int): File {
        val open = openFile
        if (open != null && open.isFile &&
            (open.length() == 0L || open.length() + lineBytes <= maxFileBytes)
        ) {
            // 空文件永远接受（一行永不拆分，单行超预算也写进空文件）。
            return open
        }
        return rotate()
    }

    private fun rotate(): File {
        val existing = ledgerFiles()
        if (existing.size >= maxFiles) {
            val toDelete = existing.size - maxFiles + 1
            existing.take(toDelete).forEach(File::delete)
        }
        val next = File(dir, nextFileName(ledgerFiles()))
        next.delete()
        try {
            next.createNewFile()
        } catch (_: IOException) {
            // 建不出新文件就写进既有的最后一个：记录仍落本机，只是不再轮转。
            return existing.lastOrNull() ?: next
        }
        return next
    }

    private fun nextFileName(existing: List<File>): String {
        val maxSequence = existing.lastOrNull()?.let(::sequenceOf) ?: 0L
        val next = if (maxSequence >= MAX_SEQUENCE) 1L else maxSequence + 1L
        return FILE_PREFIX + String.format("%06d", next) + FILE_SUFFIX
    }

    private fun sequenceOf(file: File): Long =
        file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX).toLongOrNull() ?: 0L

    private fun ledgerFiles(): List<File> =
        dir.listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX) }
            ?.sortedBy(::sequenceOf)
            ?: emptyList()

    private fun readEvents(file: File, into: MutableList<DiagnosticEvent>) {
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return
        }
        var start = 0
        while (start < text.length) {
            val newline = text.indexOf('\n', start)
            if (newline < 0) break
            // 尾部没有换行 = 崩溃中断的半行，不解析（它不属于任何完整事件）。
            val line = text.substring(start, newline)
            start = newline + 1
            val event = DiagnosticEventJson.decodeOrNull(line)
            if (event != null) into.add(event)
        }
    }

    /** 完整行数（'\n' 计数）；半行不算，与 readAll 的口径一致。 */
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
            // 以 '\n' 结尾的文本 split 末尾是空串；无结尾换行时最后一项是半行（丢弃）。
            val halfLine = split.isNotEmpty() && split.last().isNotEmpty()
            val complete = if (halfLine) split.dropLast(1) else split
            val lines = complete.filter(String::isNotEmpty)
            val drop = excess.coerceAtMost(lines.size)
            if (drop == lines.size) {
                file.delete()
            } else {
                // 每行一条 JSON：行间与行尾都要换行（separator 漏掉会把多行粘连成一行）。
                file.writeText(
                    lines.drop(drop).joinToString(separator = "\n", postfix = "\n"),
                    Charsets.UTF_8,
                )
            }
            excess -= drop
        }
    }

    companion object {
        const val DEFAULT_MAX_FILES = 6
        const val DEFAULT_MAX_FILE_BYTES = 64 * 1024L
        const val DEFAULT_MAX_ENTRIES = 300
        const val FILE_PREFIX = "events-"
        const val FILE_SUFFIX = ".jsonl"
        const val SESSION_WATERMARK_FILE = "session-watermark.txt"
        private const val MAX_SEQUENCE = 999_999L
    }
}

/**
 * 会话水位线：每个进程启动时把"自己活着"写在这里。启动对账拿它当上一会话的起点
 * （窗口 = [上一会话水位线, 本进程启动)），更早的事件早已展示过或被轮转，不再打扰。
 */
object SessionWatermark {
    fun write(directory: File, atEpochMillis: Long) {
        try {
            File(directory, CrashLedger.SESSION_WATERMARK_FILE)
                .writeText(atEpochMillis.toString() + "\n", Charsets.UTF_8)
        } catch (_: IOException) {
            // 写不进水位线只影响下次对账窗口的精度，不影响任何功能。
        }
    }

    fun readOrNull(directory: File): Long? = try {
        File(directory, CrashLedger.SESSION_WATERMARK_FILE)
            .readText(Charsets.UTF_8)
            .trim()
            .toLongOrNull()
    } catch (_: IOException) {
        null
    }
}
