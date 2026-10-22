package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationReconcileCounts
import com.tingyun.smartmistakebook.core.database.port.MAX_FIGURE_GENERATION_RECONCILE_DIAGNOSTICS_CHARS

/**
 * 图生成审计台账的**启动对账**写口（4E WG-08）。
 *
 * 与 `FigureGenerationDao` 的分工：那个 DAO 是 recorder（T1b）的原始读写口，本 DAO 回答
 * "上一进程留下的 RUNNING 行有哪些、怎样把它们落成 FAILED"，以及"终态 SUCCEEDED 但产物
 * 从未落盘/回填的行怎样落成 FAILED 待核实"。**不改** `FigureGenerationDao` 的既有契约，
 * 也不在实体上加减列。
 *
 * 状态迁移只认 `status = 'RUNNING'` 或"SUCCEEDED 且 output_asset_id IS NULL"这两条比较交换：
 * 已回填产物的 SUCCEEDED / FAILED / CANCELLED 行不会被对账改写（晚到终态不许被对账覆盖）。
 * 产物是证据：对账只改状态、诊断与结束时刻，绝不删 `output_asset_id`。
 *
 * **为什么需要第二段**：协调器在生成成功、入口落盘之前就写 SUCCEEDED（output 为空）。
 * 若 persist/attach 失败（如 T3 的 ATTACH_FAILED），行会永久显示成功却没有产物。启动对账
 * 把"结束时刻早于本进程启动、仍无 output"的 SUCCEEDED 行落成 FAILED（待核实），同一操作
 * 只有在没有任何已回填产物的尝试时才一并落 FAILED。不自动重派。
 */
@Dao
internal abstract class FigureGenerationReconcileDao {

    /**
     * 父操作**不是** RUNNING 的僵尸尝试。父操作仍是 RUNNING 的尝试留给
     * [reconcileStaleRunning] 的第二段（连同父操作一起、在同事务里判定），这样不会出现
     * "父操作其实是活的、尝试却被独立扫描标死"。
     */
    @Query(
        """
        SELECT attempt.attempt_id
        FROM figure_generation_attempt AS attempt
        WHERE attempt.status = 'RUNNING'
          AND attempt.started_at_epoch_millis <= :staleBeforeEpochMillis
          AND NOT EXISTS (
              SELECT 1 FROM figure_generation_operation AS parent
              WHERE parent.operation_id = attempt.operation_id
                AND parent.status = 'RUNNING'
          )
        ORDER BY attempt.started_at_epoch_millis ASC, attempt.attempt_id ASC
        """,
    )
    protected abstract suspend fun findStaleRunningAttemptIds(
        staleBeforeEpochMillis: Long,
    ): List<String>

    /** 最后更新不晚于租约截止、仍停在 RUNNING 的操作（按 `(status, updated_at)` 索引扫）。 */
    @Query(
        """
        SELECT operation_id
        FROM figure_generation_operation
        WHERE status = 'RUNNING'
          AND updated_at_epoch_millis <= :staleBeforeEpochMillis
        ORDER BY updated_at_epoch_millis ASC, operation_id ASC
        """,
    )
    protected abstract suspend fun findStaleRunningOperationIds(
        staleBeforeEpochMillis: Long,
    ): List<String>

    @Query(
        """
        UPDATE figure_generation_attempt
        SET status = 'FAILED',
            diagnostics = :diagnostics,
            ended_at_epoch_millis = :endedAtEpochMillis
        WHERE attempt_id = :attemptId
          AND status = 'RUNNING'
        """,
    )
    protected abstract suspend fun failRunningAttempt(
        attemptId: String,
        diagnostics: String,
        endedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE figure_generation_attempt
        SET status = 'FAILED',
            diagnostics = :diagnostics,
            ended_at_epoch_millis = :endedAtEpochMillis
        WHERE operation_id = :operationId
          AND status = 'RUNNING'
        """,
    )
    protected abstract suspend fun failRunningAttemptsOfOperation(
        operationId: String,
        diagnostics: String,
        endedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE figure_generation_operation
        SET status = 'FAILED',
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE operation_id = :operationId
          AND status = 'RUNNING'
        """,
    )
    protected abstract suspend fun failRunningOperation(
        operationId: String,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * 终态 SUCCEEDED、但入口从未回填产物（`output_asset_id IS NULL`）的尝试。
     *
     * 结束时刻必须早于租约截止：本进程刚生成、入口还在落盘的行不能被同一轮对账误杀。
     */
    @Query(
        """
        SELECT attempt_id AS attemptId, operation_id AS operationId
        FROM figure_generation_attempt
        WHERE status = 'SUCCEEDED'
          AND output_asset_id IS NULL
          AND ended_at_epoch_millis IS NOT NULL
          AND ended_at_epoch_millis <= :staleBeforeEpochMillis
        ORDER BY ended_at_epoch_millis ASC, attempt_id ASC
        """,
    )
    protected abstract suspend fun findUnconfirmedSucceededAttempts(
        staleBeforeEpochMillis: Long,
    ): List<UnconfirmedSucceededAttemptRow>

    @Query(
        """
        UPDATE figure_generation_attempt
        SET status = 'FAILED',
            diagnostics = :diagnostics,
            ended_at_epoch_millis = :endedAtEpochMillis
        WHERE attempt_id = :attemptId
          AND status = 'SUCCEEDED'
          AND output_asset_id IS NULL
        """,
    )
    protected abstract suspend fun failUnconfirmedSucceededAttempt(
        attemptId: String,
        diagnostics: String,
        endedAtEpochMillis: Long,
    ): Int

    /**
     * 操作仍显示 SUCCEEDED，但名下没有任何"已回填产物"的 SUCCEEDED 尝试 → 落 FAILED。
     *
     * 两个**不更新**条件：有任一确认产物（同一操作的另一次尝试已成功落盘），或还有
     * RUNNING/PENDING 尝试在途（重试可能马上回填产物，不能先把它判死；那次尝试自己的
     * 终态/僵尸对账会收尾）。
     */
    @Query(
        """
        UPDATE figure_generation_operation
        SET status = 'FAILED',
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE operation_id = :operationId
          AND status = 'SUCCEEDED'
          AND NOT EXISTS (
              SELECT 1 FROM figure_generation_attempt AS confirmed
              WHERE confirmed.operation_id = :operationId
                AND confirmed.status = 'SUCCEEDED'
                AND confirmed.output_asset_id IS NOT NULL
          )
          AND NOT EXISTS (
              SELECT 1 FROM figure_generation_attempt AS in_flight
              WHERE in_flight.operation_id = :operationId
                AND in_flight.status IN ('RUNNING', 'PENDING')
          )
        """,
    )
    protected abstract suspend fun failUnconfirmedSucceededOperation(
        operationId: String,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * 三段式对账的同事务边界。顺序是刻意的：先处理"父操作已经不在 RUNNING"的独立尝试，
     * 再处理僵尸操作（先尝试、后操作），最后纠正"SUCCEEDED 但无产物"的假成功。
     * 任何一段失败都不留半个状态。
     */
    @Transaction
    open suspend fun reconcileStaleRunning(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
        diagnostics: String,
    ): FigureGenerationReconcileCounts {
        require(staleBeforeEpochMillis >= 0L) {
            "A figure reconcile cutoff must not be negative"
        }
        require(atEpochMillis > staleBeforeEpochMillis) {
            "A figure reconcile timestamp must be later than its cutoff"
        }
        require(diagnostics.isNotBlank()) { "A figure reconcile diagnostics must not be blank" }
        require(diagnostics.length <= MAX_FIGURE_GENERATION_RECONCILE_DIAGNOSTICS_CHARS) {
            "Figure reconcile diagnostics are too long"
        }

        var failedAttempts = 0
        var failedOperations = 0

        findStaleRunningAttemptIds(staleBeforeEpochMillis).forEach { attemptId ->
            failedAttempts += failRunningAttempt(
                attemptId = attemptId,
                diagnostics = diagnostics,
                endedAtEpochMillis = atEpochMillis,
            )
        }
        findStaleRunningOperationIds(staleBeforeEpochMillis).forEach { operationId ->
            failedAttempts += failRunningAttemptsOfOperation(
                operationId = operationId,
                diagnostics = diagnostics,
                endedAtEpochMillis = atEpochMillis,
            )
            failedOperations += failRunningOperation(
                operationId = operationId,
                updatedAtEpochMillis = atEpochMillis,
            )
        }
        // 第二段：假成功。协调器在入口落盘前就写了 SUCCEEDED；output 一直为空说明
        // persist/attach 失败（或进程在落盘前被杀）。落 FAILED 待核实，不自动重派。
        findUnconfirmedSucceededAttempts(staleBeforeEpochMillis).forEach { attempt ->
            failedAttempts += failUnconfirmedSucceededAttempt(
                attemptId = attempt.attemptId,
                diagnostics = UNCONFIRMED_SUCCESS_DIAGNOSTICS,
                endedAtEpochMillis = atEpochMillis,
            )
            failedOperations += failUnconfirmedSucceededOperation(
                operationId = attempt.operationId,
                updatedAtEpochMillis = atEpochMillis,
            )
        }
        return FigureGenerationReconcileCounts(
            failedOperations = failedOperations,
            failedAttempts = failedAttempts,
        )
    }

    private companion object {
        /**
         * 假成功的稳定诊断前缀：生成已成功，但产物从未落盘/回填。与僵尸 RUNNING 的
         * `RECONCILED_STALE_RUNNING` 分开，读日志的人能区分"没跑完"与"跑完但没接上"。
         */
        const val UNCONFIRMED_SUCCESS_DIAGNOSTICS =
            "RECONCILED_UNCONFIRMED_SUCCESS：生成已成功但产物未落盘或未回填，" +
                "终态标 FAILED 待核实；不自动重派"
    }
}

/** 一条"SUCCEEDED 但无产物"的尝试（对账第二段的读形状）。 */
internal data class UnconfirmedSucceededAttemptRow(
    val attemptId: String,
    val operationId: String,
)
