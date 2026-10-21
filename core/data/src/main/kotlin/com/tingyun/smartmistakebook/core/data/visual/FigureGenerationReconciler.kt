package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.port.FigureGenerationAuditPort

/**
 * 一次 RUNNING 行在多长时间没有落终态后算"超时"（30 分钟）。
 *
 * 这个窗口只用于**本进程还在跑**的那种僵住：图片生成/净化是分钟级操作，30 分钟没有任何
 * 状态变化只可能是协程丢了或挂死。启动对账另有更快的判据（见
 * [FigureGenerationReconciler.reconcileStaleRunning]），不必等满这个窗口。
 */
const val FIGURE_GENERATION_RUNNING_TIMEOUT_MILLIS: Long = 30L * 60L * 1_000L

/**
 * 4E WG-08 的僵尸 RUNNING 对账（T5）。
 *
 * **它消灭的具体失败**：进程在"操作/尝试已置 RUNNING、终态还没落"时被杀（或在进程内挂死），
 * 审计台账会永远显示"仍在生成"：启动后既没有协程在做，也没有任何入口会收尾。本类把这些行
 * 落成 `FAILED`，诊断写"待核实"，并把产物原样保留。
 *
 * 三条边界（都是刻意的）：
 * - **不自动重派**：依赖的 [FigureGenerationAuditPort] 只有"读僵尸行 + 标失败"一个入口，
 *   没有派发、预留或插入新尝试的方法；重试/重绘只能由用户动作触发。结构上不存在
 *   "启动时偷偷再付一次钱"的路径，也不存在"自动重试死循环"。
 * - **不抹产物**：只改状态/诊断/结束时刻；已回填的 `output_asset_id` 留着——它仍是证据，
 *   且 GC 与备份继续把它当引用（见 `PendingCaptureDao.findUnreferencedCanonicalAssets`）。
 * - **只碰 RUNNING**：`PENDING` 不在这里处理（还没开始跑、也没有在途结论）；已经终态的行
 *   不会被改写（晚到终态不许被对账覆盖）。
 *
 * 租约口径：`staleBefore = max(本进程启动时刻, 现在 - 超时窗口)`。
 * - 启动时：截止 = 本进程启动时刻，上一进程留下的 RUNNING 立刻被收尾（进程都换了，
 *   那行不可能还有协程在做）；
 * - 进程已经跑了很久：截止 = 现在 - 超时窗口，本进程里挂死的行也会在之后的复查里被收尾。
 *
 * [reconcileStaleRunning] 是幂等的：第二次调用对同一批行返回 0（它们已经是 FAILED）。
 */
class FigureGenerationReconciler(
    private val audit: FigureGenerationAuditPort,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * 对账一次。返回真正落成终态的操作/尝试数。
     *
     * [processStartedAtEpochMillis] 必须是**进程启动时刻**（在打开数据库之前取的墙钟），
     * 不是本次调用时刻——用它当租约截止，才能区分"上一进程留下的行"与"本进程刚发起的行"。
     *
     * 截止不早于当前时刻时直接返回 0、不碰库：启动竞态下（比如同毫秒发起的操作）宁可这一轮
     * 不收，也不把一个真在跑的 RUNNING 标死；下一轮复查会接上。
     */
    suspend fun reconcileStaleRunning(
        processStartedAtEpochMillis: Long,
        runningTimeoutMillis: Long = FIGURE_GENERATION_RUNNING_TIMEOUT_MILLIS,
    ): FigureGenerationReconcileReport {
        require(processStartedAtEpochMillis > 0L) {
            "A figure reconcile process start must be positive"
        }
        require(runningTimeoutMillis >= 1L) {
            "A figure reconcile running timeout must be positive"
        }
        val atEpochMillis = now()
        require(atEpochMillis > 0L) { "A figure reconcile clock must be positive" }
        val timeoutCutoff = atEpochMillis - runningTimeoutMillis
        val staleBeforeEpochMillis = maxOf(processStartedAtEpochMillis, timeoutCutoff)
        if (staleBeforeEpochMillis >= atEpochMillis) {
            return FigureGenerationReconcileReport(failedOperations = 0, failedAttempts = 0)
        }
        val counts = audit.reconcileStaleFigureGenerationRuns(
            staleBeforeEpochMillis = staleBeforeEpochMillis,
            atEpochMillis = atEpochMillis,
            diagnostics = STALE_RUNNING_DIAGNOSTICS,
        )
        return FigureGenerationReconcileReport(
            failedOperations = counts.failedOperations,
            failedAttempts = counts.failedAttempts,
        )
    }

    companion object {
        /**
         * 写到 attempt.diagnostics 的对账说明。前缀是稳定标识，后文告诉人"发生了什么、
         * 产物在不在、为什么没有自动重试"；长度远小于诊断列上限。
         */
        const val STALE_RUNNING_DIAGNOSTICS: String =
            "RECONCILED_STALE_RUNNING：进程中断或 RUNNING 超时，终态标 FAILED 待核实；" +
                "产物（如有）已保留；不自动重派"
    }
}

/**
 * 一次对账的结果：真正从 RUNNING 改成 FAILED 的操作数与尝试数。
 *
 * 计数是"真的改了行"的数，不是"扫到候选"的数——重复对账返回 0，调用方据此判断是否值得
 * 写日志。
 */
data class FigureGenerationReconcileReport(
    val failedOperations: Int,
    val failedAttempts: Int,
) {
    init {
        require(failedOperations >= 0) { "A reconciled operation count must not be negative" }
        require(failedAttempts >= 0) { "A reconciled attempt count must not be negative" }
    }
}
