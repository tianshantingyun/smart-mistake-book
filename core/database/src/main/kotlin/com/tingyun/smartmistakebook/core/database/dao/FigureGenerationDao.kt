package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.entity.FigureGenerationAttemptEntity
import com.tingyun.smartmistakebook.core.database.entity.FigureGenerationOperationEntity

/**
 * 图生成审计台账（4E WG-08）的**原始读写口**。这里只有"把行写进去 / 读出来 / 落终态"，
 * 不含状态机、不含预算判定、不含重试决策——那些属于 recorder（T1b）与启动对账（T5）。
 *
 * 分工与红线：
 * - **不是预算权威**：本 DAO 不读也不写 `model_task_operation.dispatch_count` /
 *   `clean_redraw_task.attempt_count`；三个审计计数只是旁证。
 * - **操作身份唯一**：`insertOperation` 用 `IGNORE`——同一 `operation_id` 的重复触发是重放，
 *   调用方回读已有行（它的状态才是权威，绝不被重放覆盖）。
 * - **尝试不复活**：`insertAttempt` 用 `ABORT`——`attempt_id` 或
 *   `(operation_id, attempt_ordinal)` 撞车是调用方的身份错误，不是可以静默吞掉的冲突。
 * - 所有查询参数都由 Room 绑定（`:name`），不存在拼接 SQL 的入口。
 */
@Dao
internal interface FigureGenerationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperation(entity: FigureGenerationOperationEntity): Long

    @Query("SELECT * FROM figure_generation_operation WHERE operation_id = :operationId LIMIT 1")
    suspend fun findOperation(operationId: String): FigureGenerationOperationEntity?

    /** 按来源读全部操作（同一题面/消息/净化作业可能有多 purpose 的操作）。 */
    @Query(
        """
        SELECT * FROM figure_generation_operation
        WHERE source_kind = :sourceKind AND source_id = :sourceId
        ORDER BY created_at_epoch_millis ASC, operation_id ASC
        """,
    )
    suspend fun findOperationsBySource(
        sourceKind: String,
        sourceId: String,
    ): List<FigureGenerationOperationEntity>

    /**
     * 启动对账：某状态且最后更新早于本进程启动时刻的行（僵尸 RUNNING / 待核实的终态）。
     */
    @Query(
        """
        SELECT * FROM figure_generation_operation
        WHERE status = :status AND updated_at_epoch_millis <= :staleBeforeEpochMillis
        ORDER BY updated_at_epoch_millis ASC, operation_id ASC
        """,
    )
    suspend fun findStaleOperations(
        status: String,
        staleBeforeEpochMillis: Long,
    ): List<FigureGenerationOperationEntity>

    /** 落操作状态；返回命中行数（0 = 行不存在，调用方按"没有这次操作"处理）。 */
    @Query(
        """
        UPDATE figure_generation_operation
        SET status = :status, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE operation_id = :operationId
        """,
    )
    suspend fun updateOperationStatus(
        operationId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * 落操作终态与三个审计计数（T1b recorder 的唯一终态写口）。
     *
     * 计数只在这里写：建立时三个计数都是 0，预留在途也不改——按实体 KDoc 的口径，
     * 它们表达的是"这次操作结束时的事实"，不是可变的预算余额。不做比较交换：终态写入由
     * recorder 的单一流程负责，晚到尝试不会共用同一 operation 行；僵尸 RUNNING 由 T5 的
     * 对账端口处理（它不碰这三列）。
     */
    @Query(
        """
        UPDATE figure_generation_operation
        SET status = :status,
            reserved_remote_dispatches = :reservedRemoteDispatches,
            used_remote_dispatches = :usedRemoteDispatches,
            final_answer_reserved = :finalAnswerReserved,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE operation_id = :operationId
        """,
    )
    suspend fun finishOperation(
        operationId: String,
        status: String,
        reservedRemoteDispatches: Int,
        usedRemoteDispatches: Int,
        finalAnswerReserved: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(entity: FigureGenerationAttemptEntity)

    /** 该操作已占用的最小本地序号（负数区间）；null = 还没有本地尝试。 */
    @Query(
        """
        SELECT MIN(attempt_ordinal) FROM figure_generation_attempt
        WHERE operation_id = :operationId AND attempt_ordinal < 0
        """,
    )
    suspend fun minLocalAttemptOrdinal(operationId: String): Int?

    /**
     * 本地方法（JS）尝试的**序号分配 + 建行**，同一个写事务。
     *
     * 序号取负数区间（-1、-2、…）：远程预留序号来自账本 `dispatchCount`/`attemptCount`，
     * 恒为正，两条序列在 `(operation_id, attempt_ordinal)` 唯一索引上不会撞。这正是
     * `MAX(attempt_ordinal)+1` 那种"本地与远程共用一个正数序列"写法的缺陷——本地先占 1
     * 之后，同一操作的下一次 IMAGE_MODEL 拿到账本的 1 就会 ABORT，审计行被 recorder 静默丢掉。
     *
     * `reservation_id` 为 NULL：本地执行不占远程派发。`attempt_id` 与序号同源（含 method）。
     */
    @Transaction
    suspend fun insertLocalAttempt(
        operationId: String,
        method: String,
        status: String,
        sourceSha256: String?,
        inputFingerprint: String,
        startedAtEpochMillis: Long,
    ): FigureGenerationAttemptEntity {
        val ordinal = (minLocalAttemptOrdinal(operationId) ?: 0) - 1
        val entity = FigureGenerationAttemptEntity(
            attemptId = "figure-attempt:$operationId:$ordinal:$method",
            operationId = operationId,
            attemptOrdinal = ordinal,
            method = method,
            status = status,
            sourceSha256 = sourceSha256,
            inputFingerprint = inputFingerprint,
            reservationId = null,
            startedAtEpochMillis = startedAtEpochMillis,
        )
        insertAttempt(entity)
        return entity
    }

    @Query("SELECT * FROM figure_generation_attempt WHERE attempt_id = :attemptId LIMIT 1")
    suspend fun findAttempt(attemptId: String): FigureGenerationAttemptEntity?

    @Query(
        """
        SELECT * FROM figure_generation_attempt
        WHERE operation_id = :operationId
        ORDER BY attempt_ordinal ASC
        """,
    )
    suspend fun findAttempts(operationId: String): List<FigureGenerationAttemptEntity>

    /**
     * 落一次尝试的终态与产物；返回命中行数（0 = 行不存在）。
     * 终态 attempt 不许复活：调用方只在 RUNNING 行上调用；重复落同一终态是幂等写。
     */
    @Query(
        """
        UPDATE figure_generation_attempt
        SET status = :status,
            output_asset_id = :outputAssetId,
            diagnostics = :diagnostics,
            ended_at_epoch_millis = :endedAtEpochMillis
        WHERE attempt_id = :attemptId
        """,
    )
    suspend fun finishAttempt(
        attemptId: String,
        status: String,
        outputAssetId: String?,
        diagnostics: String?,
        endedAtEpochMillis: Long,
    ): Int

    /**
     * 入口落盘成功后回填产物资产 id（T1b）。
     *
     * 只改 `output_asset_id`：attempt 已经是 SUCCEEDED 终态，回填不是第二次状态迁移；
     * 不刷新 `ended_at_epoch_millis`（结束时刻仍是生成结束的那一瞬）。重复回填同一 id 是
     * 幂等写；指向不存在的资产会被 `canonical_source_asset` 的 RESTRICT 外键挡住。
     */
    @Query(
        """
        UPDATE figure_generation_attempt
        SET output_asset_id = :outputAssetId
        WHERE attempt_id = :attemptId
        """,
    )
    suspend fun attachAttemptOutputAsset(
        attemptId: String,
        outputAssetId: String,
    ): Int
}
