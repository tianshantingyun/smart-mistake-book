package com.tingyun.smartmistakebook.core.data.capture

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-42：整卷录入在 job 落库**之前**的 staging 阶段被杀 → 该批静默丢弃，列表里没有任务、也没有
 * 失败页。入口的「上次导入被中断，可重导」判据就是 staging 里有没有**未被任何任务认领**的会话；
 * 这里在真实文件系统上钉住那条规则（[orphanedStagingSessions]）与对账的回收判定
 * （[reclaimableStagingSessions]，即 `AndroidBatchImportSourceStaging.reconcileOrphans` 的取数）。
 *
 * 为什么是普通 JVM 单测：`AndroidBatchImportSourceStaging` 需要 Context（filesDir、FileProvider
 * 授权、`content://` 解析），JVM 跑不了；规则本身与 Android 无关，所以它留在纯函数里。uri →
 * 会话 id 的那一段映射由 `BatchImportInterruptedStagingInstrumentedTest` 的真 staging 断言覆盖。
 */
class BatchImportStagingOrphansTest {
    private val graceMillis = 60L * 60L * 1_000L
    private val roots = mutableListOf<File>()

    @After
    fun tearDown() {
        roots.forEach { root -> root.deleteRecursively() }
    }

    @Test
    fun anUnclaimedSessionReadsAsInterruptedUntilAJobClaimsItOrReclaimDeletesIt() {
        val root = stagingRoot()
        val now = 1_000L * graceMillis
        val session = sessionDirectory(root, id = sessionId(1), modifiedAt = now)

        // KD-42 现场：staging 完成、job 还没建立就死了 —— 提示为真。
        assertEquals(listOf(session.name), orphanedStagingSessions(root, emptySet()).map(File::getName))
        // 但会话还在 60 分钟宽限期内：同一轮对账不会删它（「提示为真」与「可以回收」是两件事）。
        assertTrue(reclaimableStagingSessions(root, emptySet(), now).isEmpty())

        // 在册任务认领这些页面之后，同一个目录不再算「未回收」。
        assertTrue(orphanedStagingSessions(root, setOf(session.name)).isEmpty())

        // 宽限过后，对账把它回收掉 —— 提示转假。
        val reclaimable = reclaimableStagingSessions(root, emptySet(), now + graceMillis)
        assertEquals(listOf(session.name), reclaimable.map(File::getName))
        reclaimable.forEach { session -> session.deleteRecursively() }
        assertTrue(orphanedStagingSessions(root, emptySet()).isEmpty())
    }

    @Test
    fun theGraceCutoffIsInclusiveAndOnlySessionsAtOrBeforeItAreReclaimable() {
        val root = stagingRoot()
        val now = 1_000L * graceMillis
        val exactlyAtCutoff = sessionDirectory(root, id = sessionId(1), modifiedAt = now - graceMillis)
        val youngerThanGrace = sessionDirectory(root, id = sessionId(2), modifiedAt = now - graceMillis + 1)
        val olderThanGrace = sessionDirectory(root, id = sessionId(3), modifiedAt = now - graceMillis - 1)

        assertEquals(
            listOf(exactlyAtCutoff.name, olderThanGrace.name).sorted(),
            reclaimableStagingSessions(root, emptySet(), now).map(File::getName).sorted(),
        )
        // 宽限内（含刚好等于宽限）的会话既不会被回收，也仍然是「未回收」——提示照旧为真。
        assertEquals(
            listOf(exactlyAtCutoff.name, olderThanGrace.name, youngerThanGrace.name).sorted(),
            orphanedStagingSessions(root, emptySet()).map(File::getName).sorted(),
        )
    }

    @Test
    fun aClockInsideTheGracePeriodReclaimsNothingEvenForEpochTimestamps() {
        val root = stagingRoot()
        val epochish = sessionDirectory(root, id = sessionId(9), modifiedAt = 1)

        assertEquals(listOf(epochish.name), orphanedStagingSessions(root, emptySet()).map(File::getName))
        assertTrue(reclaimableStagingSessions(root, emptySet(), graceMillis).isEmpty())
    }

    @Test
    fun entriesThatAreNotSessionsAndAMissingRootAreOutsideTheRule() {
        val root = stagingRoot()
        File(root, "not-a-session").mkdirs()
        File(root, "page-00.source").writeText("page-bytes")

        assertTrue(orphanedStagingSessions(root, emptySet()).isEmpty())
        assertTrue(reclaimableStagingSessions(root, emptySet(), 10L * graceMillis).isEmpty())

        val missingRoot = File(root.parentFile, "${root.name}-missing")
        assertTrue(orphanedStagingSessions(missingRoot, emptySet()).isEmpty())
        assertTrue(reclaimableStagingSessions(missingRoot, emptySet(), 10L * graceMillis).isEmpty())
    }

    @Test
    fun oneReconciliationPassReclaimsItsBudgetWhileVisibilityCountsEverySession() {
        val root = stagingRoot()
        val now = 10L * graceMillis
        repeat(ORPHAN_BUDGET + 2) { index ->
            sessionDirectory(root, id = sessionId(index), modifiedAt = now - graceMillis - 1)
        }

        assertEquals(ORPHAN_BUDGET + 2, orphanedStagingSessions(root, emptySet()).size)
        assertEquals(
            ORPHAN_BUDGET,
            reclaimableStagingSessions(root, emptySet(), now).size,
        )
    }

    private fun stagingRoot(): File =
        File(createOwnedDirectory("batch-staging"), BATCH_IMPORT_STAGING_DIRECTORY).also { root ->
            check(root.mkdirs() || root.isDirectory) { "test staging root could not be created" }
        }

    private fun sessionDirectory(root: File, id: String, modifiedAt: Long): File {
        val session = File(root, id).canonicalFile
        check(session.parentFile == root.canonicalFile)
        check(session.mkdirs() || session.isDirectory) { "test session could not be created" }
        File(session, "page-00.source").writeText("page-bytes")
        check(session.setLastModified(modifiedAt)) { "test session mtime could not be set" }
        return session
    }

    private fun sessionId(index: Int): String = "%08x-0000-0000-0000-%012d".format(index, index)

    private fun createOwnedDirectory(prefix: String): File =
        File.createTempFile(prefix, "").let { probe ->
            val parent = checkNotNull(probe.parentFile) { "test temp directory is unavailable" }
            probe.delete()
            File(parent, "$prefix-${System.nanoTime()}").also { directory ->
                check(directory.mkdirs() || directory.isDirectory)
                roots += directory
            }
        }

    private companion object {
        /** 与 `MAX_ORPHAN_SESSIONS_PER_RECONCILIATION` 同值：单轮对账的会话上限。 */
        const val ORPHAN_BUDGET = 128
    }
}
