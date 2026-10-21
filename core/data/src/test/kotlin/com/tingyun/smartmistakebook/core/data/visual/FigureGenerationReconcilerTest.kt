package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.port.FigureGenerationAuditPort
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationReconcileCounts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 僵尸 RUNNING 对账的 JVM 单测（4E WG-08 / T5）。
 *
 * 钉三件事：
 * - **启动租约**：上一进程留下的 RUNNING 用"本进程启动时刻"当截止，立刻收尾；本进程里
 *   挂死的行要等超时窗口才收；
 * - **不碰未来**：截止不早于当前时刻时一行也不读、不写（启动竞态下不误杀真在跑的操作）；
 * - **不重派**：端口上只有对账入口，没有任何派发/插入新尝试的方法——本类的依赖形状就是
 *   "结构上无法再花一次钱"的证据（真库 SQL 由仪器化用例覆盖）。
 */
class FigureGenerationReconcilerTest {

    @Test
    fun `leftover running rows from a previous process are reconciled at the process start`() =
        runBlocking {
            val audit = RecordingAudit(
                counts = FigureGenerationReconcileCounts(
                    failedOperations = 2,
                    failedAttempts = 3,
                ),
            )
            val reconciler = FigureGenerationReconciler(audit) { NOW }

            val report = reconciler.reconcileStaleRunning(
                processStartedAtEpochMillis = PROCESS_STARTED,
                runningTimeoutMillis = 60_000L,
            )

            val call = audit.calls.single()
            assertEquals(
                "截止必须取本进程启动时刻（它比 现在-超时 更晚），上一进程的 RUNNING 才能立刻收尾",
                PROCESS_STARTED,
                call.staleBefore,
            )
            assertEquals(NOW, call.atEpochMillis)
            assertTrue(
                "诊断必须带稳定前缀，供人区分'中断/超时'与普通失败",
                call.diagnostics.contains("RECONCILED_STALE_RUNNING"),
            )
            assertEquals(2, report.failedOperations)
            assertEquals(3, report.failedAttempts)
            assertEquals("对账只调一次端口", 1, audit.calls.size)
        }

    @Test
    fun `a run hung inside this process is reconciled only after the timeout`() = runBlocking {
        val audit = RecordingAudit(
            counts = FigureGenerationReconcileCounts(failedOperations = 1, failedAttempts = 1),
        )
        val reconciler = FigureGenerationReconciler(audit) { NOW }

        reconciler.reconcileStaleRunning(
            processStartedAtEpochMillis = NOW - 120_000L,
            runningTimeoutMillis = 60_000L,
        )

        assertEquals(
            "进程已跑了很久时，截止 = 现在 - 超时窗口，本进程挂死的 RUNNING 才会被收",
            NOW - 60_000L,
            audit.calls.single().staleBefore,
        )
    }

    @Test
    fun `nothing is touched when the cutoff is not in the past yet`() = runBlocking {
        val audit = RecordingAudit(
            counts = FigureGenerationReconcileCounts(failedOperations = 9, failedAttempts = 9),
        )
        val reconciler = FigureGenerationReconciler(audit) { NOW }

        val report = reconciler.reconcileStaleRunning(
            processStartedAtEpochMillis = NOW,
            runningTimeoutMillis = 60_000L,
        )

        assertEquals(0, report.failedOperations)
        assertEquals(0, report.failedAttempts)
        assertTrue(
            "截止不早于当前时刻时必须不碰库：同毫秒发起的操作不能被误标失败",
            audit.calls.isEmpty(),
        )
    }

    @Test
    fun `invalid leases are rejected before touching the database`() = runBlocking {
        val audit = RecordingAudit(
            counts = FigureGenerationReconcileCounts(failedOperations = 0, failedAttempts = 0),
        )
        val reconciler = FigureGenerationReconciler(audit) { NOW }

        val nonPositiveStart = runCatching {
            reconciler.reconcileStaleRunning(processStartedAtEpochMillis = 0L)
        }
        assertTrue(nonPositiveStart.isFailure)

        val nonPositiveTimeout = runCatching {
            reconciler.reconcileStaleRunning(
                processStartedAtEpochMillis = PROCESS_STARTED,
                runningTimeoutMillis = 0L,
            )
        }
        assertTrue(nonPositiveTimeout.isFailure)
        assertTrue("参数不合法时不得碰库", audit.calls.isEmpty())
    }

    /** 只实现"对账"的端口：没有派发/预留/插入尝试入口，重派在结构上不可达。 */
    private class RecordingAudit(
        private val counts: FigureGenerationReconcileCounts,
    ) : FigureGenerationAuditPort {
        data class Call(
            val staleBefore: Long,
            val atEpochMillis: Long,
            val diagnostics: String,
        )

        val calls = mutableListOf<Call>()

        override suspend fun reconcileStaleFigureGenerationRuns(
            staleBeforeEpochMillis: Long,
            atEpochMillis: Long,
            diagnostics: String,
        ): FigureGenerationReconcileCounts {
            require(atEpochMillis > staleBeforeEpochMillis)
            require(staleBeforeEpochMillis >= 0L)
            calls += Call(staleBeforeEpochMillis, atEpochMillis, diagnostics)
            return counts
        }
    }

    private companion object {
        const val NOW = 10_000_000L
        const val PROCESS_STARTED = NOW - 2_000L
    }
}
