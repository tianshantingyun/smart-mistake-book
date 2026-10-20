package com.tingyun.smartmistakebook.core.database

import java.io.File

/** WAL checkpointing, consistency snapshots, and full clears for backup flows. */
internal class RoomBackupSupportStore(
    private val database: StudyDatabase,
) {
    suspend fun checkpointForBackup() {
        // WAL checkpoint so committed rows are captured before the archived
        // database file is packaged. This stays the cheap flush; the archive
        // flow itself uses snapshotForBackup below for the consistency
        // snapshot.
        //
        // KD-62①：`PRAGMA wal_checkpoint(TRUNCATE)` 的结果行必须**校验**，不能消费后忽略。
        // busy=1 表示 TRUNCATE 被长写者/其他连接挡下、checkpoint 没有完成；此时主库可能
        // 缺 WAL 里已提交的页，继续复制会静默产出一份不一致的备份。宁可让备份明确失败。
        var busy = 0
        var log = -1
        var checkpointed = -1
        var sawRow = false
        database.withRawConnection(isReadOnly = false) { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { statement ->
                if (statement.step()) {
                    sawRow = true
                    busy = statement.getInt(0)
                    log = statement.getInt(1)
                    checkpointed = statement.getInt(2)
                }
            }
        }
        if (!sawRow) {
            error("备份前的 WAL checkpoint 没有返回结果行，无法确认落盘完成；备份已中止")
        }
        checkpointCompletionProblem(busy = busy, log = log, checkpointed = checkpointed)
            ?.let { problem -> error("备份前的 WAL checkpoint 未完成：$problem") }
    }

    suspend fun snapshotForBackup(
        sourceDatabaseFile: File,
        snapshotTarget: File,
    ) {
        // Consistency snapshot for backup archives (see BackupPort docs).
        //
        // Preferred: VACUUM INTO (SQLite >= 3.27.0) writes a complete,
        // transaction-consistent copy of the database into the target file
        // even while concurrent writers are active; this matches the
        // semantics required by the SQLite Online Backup API approach.
        //
        // Fallback (older SQLite builds): TRUNCATE WAL checkpoint plus a
        // read-only byte copy of the main file, which is consistent as long
        // as no writer commits during the copy. The backup flow guarantees
        // that by running while the app is otherwise idle.
        if (snapshotTarget.exists() && !snapshotTarget.delete()) {
            error("Cannot replace existing backup snapshot target")
        }
        val sqliteVersion = readSqliteVersion()
        if (sqliteVersion != null && isAtLeast(sqliteVersion, 3, 27, 0)) {
            val escapedPath = snapshotTarget.absolutePath.replace("'", "''")
            val succeeded = try {
                database.withRawConnection(isReadOnly = false) { connection ->
                    connection.usePrepared("VACUUM INTO '$escapedPath'") { statement ->
                        while (statement.step()) {
                            // VACUUM INTO produces no result rows.
                        }
                    }
                }
                snapshotTarget.isFile && snapshotTarget.length() > 0L
            } catch (_: Exception) {
                // Older SQLite builds reject the INTO clause; fall through to
                // the checkpoint + read-only copy fallback.
                false
            }
            if (succeeded) {
                fsyncSnapshot(snapshotTarget)
                return
            }
            if (snapshotTarget.exists()) snapshotTarget.delete()
        }
        // Fallback: checkpoint every WAL frame into the main file, then copy
        // the main file through a plain read-only stream.
        checkpointForBackup()
        sourceDatabaseFile.inputStream().use { input ->
            snapshotTarget.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        fsyncSnapshot(snapshotTarget)
    }

    private suspend fun readSqliteVersion(): String? {
        val holder = arrayOfNulls<String>(1)
        database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared("SELECT sqlite_version()") { statement ->
                if (statement.step()) {
                    holder[0] = statement.getText(0)
                }
            }
        }
        return holder[0]
    }

    private fun isAtLeast(version: String, major: Int, minor: Int, patch: Int): Boolean {
        val parts = version.split('.').mapNotNull { part -> part.toIntOrNull() }
        val actual = listOf(
            parts.getOrElse(0) { 0 },
            parts.getOrElse(1) { 0 },
            parts.getOrElse(2) { 0 },
        )
        val required = listOf(major, minor, patch)
        for (index in 0..2) {
            if (actual[index] != required[index]) return actual[index] > required[index]
        }
        return true
    }

    private fun fsyncSnapshot(file: File) {
        runCatching {
            java.io.RandomAccessFile(file, "r").use { raf -> raf.fd.sync() }
        }
    }

    suspend fun clearAllData() {
        database.withRawConnection(isReadOnly = false) { connection ->
            connection.usePrepared("PRAGMA foreign_keys = OFF") { statement ->
                while (statement.step()) {
                    // PRAGMA result row is intentionally ignored.
                }
            }
            val tableNames = buildList {
                connection.usePrepared(
                    "SELECT name FROM sqlite_master WHERE type = 'table' " +
                        "AND name NOT IN ('room_master_table', 'android_metadata')",
                ) { statement ->
                    while (statement.step()) {
                        add(requireNotNull(statement.getText(0)))
                    }
                }
            }
            tableNames.forEach { table ->
                connection.usePrepared("DELETE FROM `$table`") { statement ->
                    statement.step()
                }
            }
            connection.usePrepared("PRAGMA foreign_keys = ON") { statement ->
                while (statement.step()) {
                    // PRAGMA result row is intentionally ignored.
                }
            }
        }
    }
}

/**
 * KD-62①：`PRAGMA wal_checkpoint(TRUNCATE)` 结果行的完成判据（纯函数，便于单测）。
 *
 * 返回值语义取自 SQLite 文档：第一列 `busy` = 1 表示 TRUNCATE 被挡下、checkpoint **没有**
 * 完成；第二/三列是 WAL 页数与已回写主库的页数，**两列都是 -1** 表示当前连接不在 WAL
 * 模式（没有 WAL 可 checkpoint，属合法 no-op）。其余组合里“已回写页数 < WAL 页数”视为
 * 未完成。
 *
 * 返回 null = 可以继续成包；非 null = 必须中止（绝不拿可能缺页的主库去复制）。
 */
internal fun checkpointCompletionProblem(
    busy: Int,
    log: Int,
    checkpointed: Int,
): String? {
    if (busy != 0) {
        return "busy=$busy（checkpoint 被其他连接或长写者挡下）"
    }
    if (log < 0 && checkpointed < 0) return null
    if (log < 0 || checkpointed < 0) {
        return "结果行不完整（log=$log, checkpointed=$checkpointed）"
    }
    if (checkpointed < log) {
        return "只回写了 $checkpointed/$log 页"
    }
    return null
}
