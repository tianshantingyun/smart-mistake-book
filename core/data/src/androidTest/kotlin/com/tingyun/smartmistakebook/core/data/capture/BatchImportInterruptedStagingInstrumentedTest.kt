package com.tingyun.smartmistakebook.core.data.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-42：整卷录入在 job 落库**之前**的 staging 阶段被杀 —— 该批静默丢弃，重进应用后列表里既没有
 * 任务也没有失败页。入口据此显示「上次导入被中断，可重导」，判据是「有没有未被任何任务认领的
 * staging 会话」。
 *
 * 用真 staging（真文件系统 + 真 FileProvider uri）钉住三件事：
 * ① 现场为真（staging 完成、job 没建立）；
 * ② 被在册任务认领后为假——页面文件仍在磁盘上，只是被引用，不能算中断（否则每次正常导入都会
 *    附带一句假的中断声明）；
 * ③ 超 60 分钟宽限被启动对账回收后为假。
 *
 * 规则本身（含 60 分钟宽限的边界与每轮上限）由 JVM 单测 `BatchImportStagingOrphansTest` 覆盖；
 * 这里补的是 uri → 会话 id 的映射与「引用集取自持久层」的那一段。
 */
@RunWith(AndroidJUnit4::class)
class BatchImportInterruptedStagingInstrumentedTest : CaptureWorkflowTestBase() {
    @Test
    fun interruptedStagingIsVisibleUntilAnImportClaimsItOrReconciliationReclaimsIt() = runBlocking {
        val stoppedScope = CoroutineScope(SupervisorJob().also { it.cancel() } + Dispatchers.Default)
        val batchImports = createBatchImports(stoppedScope)
        val staging = AndroidBatchImportSourceStaging(context)
        val selected = List(2) { privateUri(createJpeg(48, 64)).toString() }

        // ① 现场：staging 完成、job 还没建立就死掉 —— 持久层里没有任何东西，只剩 staging 会话。
        val interrupted = staging.stage(selected)
        assertTrue(batchImports.hasInterruptedBatchImport())

        // ③ 超宽限回收（启动对账的判定）：会话被删，提示转假。
        staging.reconcileOrphans(
            referencedSourceUris = emptySet(),
            nowEpochMillis = System.currentTimeMillis() + ORPHAN_GRACE_MILLIS + 1_000L,
        )
        interrupted.sourceUris.forEach { sourceUri ->
            assertFalse(stagedSourceFile(sourceUri).exists())
        }
        assertFalse(batchImports.hasInterruptedBatchImport())

        // ② 在册任务认领页面：文件还在磁盘上（processing 被停掉），但已被引用 —— 不属于中断。
        batchImports.createBatchImport(
            CreateBatchImportRequest(
                requestId = "kd-42-claimed-staging",
                localUris = selected,
                occurredAtEpochMillis = 1_000,
            ),
        )
        val claimed = database.observeBatchImportJobs().first().single()
        assertPagesStillOnDisk(claimed.pages.map { page -> page.sourceUri })
        assertFalse(batchImports.hasInterruptedBatchImport())
    }

    private fun assertPagesStillOnDisk(sourceUris: List<String>) {
        assertTrue(sourceUris.isNotEmpty())
        sourceUris.forEach { sourceUri ->
            assertTrue(
                "a claimed session's page must stay on disk for the assertion to be non-vacuous",
                stagedSourceFile(sourceUri).isFile,
            )
        }
    }

    private fun createBatchImports(scope: CoroutineScope): BatchImportRepository =
        BatchImportRepositoryFactory.create(
            context = context,
            database = database,
            capture = repository,
            processingScope = scope,
        )

    private companion object {
        const val ORPHAN_GRACE_MILLIS = 60L * 60L * 1_000L
    }
}
