package com.tingyun.smartmistakebook.core.database.port

/**
 * 图生成审计台账（4E WG-08，schema 69）的**启动对账**端口。
 *
 * **它消灭的具体失败**：进程在"操作/尝试已置 RUNNING、终态还没落"的时刻被杀，行会永远停在
 * RUNNING——界面/诊断看到的是"仍在生成"，而实际上既没有协程在做，也没有人会收尾。启动时把
 * 这些僵尸行落成 `FAILED`（产物如有则保留，标"待核实"），事实才和库里的状态一致。
 *
 * 两条硬边界：
 * - **只落终态，不重派**：本端口没有派发/预留/插入尝试的入口。对账能做的只有读僵尸行、
 *   把它们标失败；"要不要再生成"永远由用户动作驱动，结构上不存在"启动时偷偷再花一次钱"。
 * - **不抹产物**：落 FAILED 只写 `status` / `diagnostics` / `ended_at_epoch_millis`，
 *   `output_asset_id` 原样保留。产物仍是"这张图从哪来"的证据，也让 GC/备份继续 pin 它。
 *
 * 默认实现"做不到"（抛异常）：把一次未实现静默读成"没有僵尸行"会让启动对账假装成功。
 * 测试替身与无库装配不会被调用到；真库实现见 `RoomStudyDatabase`。
 */
interface FigureGenerationAuditPort {
    /**
     * 在一个写事务里对账 [staleBeforeEpochMillis]（含）之前的僵尸/假成功行：
     *
     * 1. 父操作**不是** RUNNING 的僵尸尝试逐行标 `FAILED`（比较交换：只对仍 RUNNING 的行生效）；
     * 2. 僵尸操作先把它名下的 RUNNING 尝试标 `FAILED`（不看尝试的开始时刻：父操作都过期了），
     *    再把操作本身标 `FAILED`。两步同事务，避免"操作被别的写者翻成终态、尝试却被我们标失败"；
     * 3. **假成功**：`SUCCEEDED` 且 `output_asset_id IS NULL`、结束时刻早于截止的尝试标
     *    `FAILED`（独立诊断前缀 `RECONCILED_UNCONFIRMED_SUCCESS`）；其父操作若没有任何已
     *    回填产物的成功尝试、且没有在途（RUNNING/PENDING）尝试，也一并标 `FAILED`。协调器
     *    在入口落盘前就写 SUCCEEDED，这一步让"用户失败但审计永久成功"不会留到下一次启动
     *    之后；有在途重试时留给该尝试自己的终态/僵尸对账收尾。
     *
     * [atEpochMillis] 是写入终态/结束时刻用的时刻，必须严格晚于 [staleBeforeEpochMillis]；
     * [diagnostics] 是有界的"待核实"说明，写到 RUNNING 尝试上（假成功用独立诊断）。
     *
     * 返回真正改变状态的行数：重复对账是幂等的（已经终态/已纠正的行不会被再改一次）。
     */
    suspend fun reconcileStaleFigureGenerationRuns(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
        diagnostics: String,
    ): FigureGenerationReconcileCounts = throw UnsupportedOperationException(
        "This database does not reconcile figure-generation audit rows",
    )
}

/** 一次对账真正落成 FAILED 的行数（已经 FAILED 的行不算；含僵尸 RUNNING 与假成功）。 */
data class FigureGenerationReconcileCounts(
    val failedOperations: Int,
    val failedAttempts: Int,
) {
    init {
        require(failedOperations >= 0) { "A reconcile operation count must not be negative" }
        require(failedAttempts >= 0) { "A reconcile attempt count must not be negative" }
    }
}

/**
 * `diagnostics` 列的有界上限。与 `FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS` 同量级：
 * 对账说明是给人看的失败原因，不是日志倾倒口。
 */
const val MAX_FIGURE_GENERATION_RECONCILE_DIAGNOSTICS_CHARS: Int = 512
