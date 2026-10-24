package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.port.FigureGenerationAuditPort
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationReconcileCounts
import com.tingyun.smartmistakebook.core.domain.BackupWriteDeferredException
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
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
            val reconciler = FigureGenerationReconciler(audit, { NOW })

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
        val reconciler = FigureGenerationReconciler(audit, { NOW })

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
        val reconciler = FigureGenerationReconciler(audit, { NOW })

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
        val reconciler = FigureGenerationReconciler(audit, { NOW })

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

    /**
     * KD-61（批次 19 T3）：启动对账是应用级 collector，恢复交换段（beginCommit 之后、
     * 关库之前）到达的这次写必须被写门挡下——不落进即将被换掉的旧库，超时抛可重试的
     * [BackupWriteDeferredException]，调用方（Application 的 runCatching）记日志、下次
     * 启动再收尾。改前这里直写端口：断言"写未发生 + 明确被拒"会失败（红）。
     */
    @Test
    fun `a reconcile arriving in the restore commit window is deferred without writing`() =
        runBlocking {
            val audit = RecordingAudit(
                counts = FigureGenerationReconcileCounts(failedOperations = 5, failedAttempts = 5),
            )
            val gate = BackupWriteGate(maxWriteQueueWaitMillis = 100L, drainTimeoutMillis = 1_000L)
            val reconciler = FigureGenerationReconciler(audit, { NOW }, gate)
            val admission = gate.beginRestore("恢复备份")
            admission.beginCommit()

            try {
                val failure = runCatching {
                    reconciler.reconcileStaleRunning(
                        processStartedAtEpochMillis = PROCESS_STARTED,
                        runningTimeoutMillis = 60_000L,
                    )
                }.exceptionOrNull()

                assertTrue(
                    "恢复交换段里的对账写必须被写门拒绝（可重试），实际：$failure",
                    failure is BackupWriteDeferredException,
                )
                assertTrue(
                    "被拒的对账不得碰库：写会落进即将被换掉的旧库",
                    audit.calls.isEmpty(),
                )
            } finally {
                admission.endRestore()
            }
        }

    /** KD-61：关库终态后（连接池已关）到达的对账必须立即失败，而不是撞 code 21。 */
    @Test
    fun `a reconcile arriving after the database is closed for restart fails fast`() =
        runBlocking {
            val audit = RecordingAudit(
                counts = FigureGenerationReconcileCounts(failedOperations = 5, failedAttempts = 5),
            )
            val gate = BackupWriteGate()
            gate.markDatabaseClosedForRestart()
            val reconciler = FigureGenerationReconciler(audit, { NOW }, gate)

            val failure = runCatching {
                reconciler.reconcileStaleRunning(
                    processStartedAtEpochMillis = PROCESS_STARTED,
                    runningTimeoutMillis = 60_000L,
                )
            }.exceptionOrNull()

            assertTrue(
                "关库终态后的对账必须立即抛 BackupWriteDeferredException，实际：$failure",
                failure is BackupWriteDeferredException,
            )
            assertTrue("终态后不得有任何库访问", audit.calls.isEmpty())
        }

    /** 接门不得改变常态行为：门空闲时对账照常执行（对齐采集仓库 admittedWrite 语义）。 */
    @Test
    fun `an idle gate lets the reconcile write pass through unchanged`() = runBlocking {
        val audit = RecordingAudit(
            counts = FigureGenerationReconcileCounts(failedOperations = 1, failedAttempts = 2),
        )
        val gate = BackupWriteGate()
        val reconciler = FigureGenerationReconciler(audit, { NOW }, gate)

        val report = reconciler.reconcileStaleRunning(
            processStartedAtEpochMillis = PROCESS_STARTED,
            runningTimeoutMillis = 60_000L,
        )

        assertEquals(1, audit.calls.size)
        assertEquals(PROCESS_STARTED, audit.calls.single().staleBefore)
        assertEquals(1, report.failedOperations)
        assertEquals(2, report.failedAttempts)
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
