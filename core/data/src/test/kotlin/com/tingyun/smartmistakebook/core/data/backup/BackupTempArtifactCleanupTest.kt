package com.tingyun.smartmistakebook.core.data.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-62②：启动清理覆盖成包/校验中途留下的临时快照。
 *
 * 消灭的失败：进程在成包/校验中途被杀后，`cache/backup-snapshots/` 与
 * `databases/backup-snapshot-*.db` 永远不会被清，长期占用磁盘。
 */
class BackupTempArtifactCleanupTest {

    @Test
    fun `startup cleanup removes packaging snapshots and their sidecars`() {
        val root = Files.createTempDirectory("backup-temp-cleanup").toFile()
        try {
            val databaseDir = File(root, "databases").apply { mkdirs() }
            val cacheDir = File(root, "cache").apply { mkdirs() }

            val snapshotDir = File(cacheDir, "backup-snapshots").apply { mkdirs() }
            val vacuumSnapshot = File(snapshotDir, "snapshot-1.db").apply { writeText("snapshot") }
            val tempDatabase = File(databaseDir, "backup-snapshot-uuid.db").apply { writeText("db") }
            val tempWal = File(databaseDir, "backup-snapshot-uuid.db-wal").apply { writeText("wal") }
            val tempShm = File(databaseDir, "backup-snapshot-uuid.db-shm").apply { writeText("shm") }
            // 不该被误删：正式库、恢复隔离区、普通缓存。
            val liveDatabase = File(databaseDir, "study.db").apply { writeText("live") }
            val otherCache = File(cacheDir, "keep.txt").apply { writeText("keep") }

            cleanupOrphanBackupArtifacts(databaseDir = databaseDir, cacheDir = cacheDir)

            assertFalse("成包快照目录应被整体清理", snapshotDir.exists())
            assertFalse("临时快照库应被删除", tempDatabase.exists())
            assertFalse("临时库的 WAL 应被删除", tempWal.exists())
            assertFalse("临时库的 SHM 应被删除", tempShm.exists())
            assertTrue("正式库不能被误删", liveDatabase.exists())
            assertTrue("无关缓存不能被误删", otherCache.exists())
            assertFalse(vacuumSnapshot.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `null directories are tolerated`() {
        cleanupOrphanBackupArtifacts(databaseDir = null, cacheDir = null)
    }
}
