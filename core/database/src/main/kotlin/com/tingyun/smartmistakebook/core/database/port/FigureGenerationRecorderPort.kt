package com.tingyun.smartmistakebook.core.database.port

/**
 * 图生成审计台账的**记录**写口（4E WG-08 T1b）。启动对账走另一个端口
 * [FigureGenerationAuditPort]：那个端口刻意没有插入/预留入口，保证"启动时不再花钱"。
 *
 * 本端口只做三类事：建立 operation（PENDING）并推进到 RUNNING、落 operation/attempt 终态、
 * 回填产物。它**不含状态机**（状态词由 core:data 的 coordinator 决定）、**不含预算判定**：
 * `reserved_remote_dispatches` / `used_remote_dispatches` / `final_answer_reserved` 是审计镜像，
 * 真正拦截派发的计数仍只由 `model_task_operation.dispatch_count` 与
 * `clean_redraw_task.attempt_count` 持有。读这两列当预算会双重计费。
 *
 * 默认全部"做不到"（false）：测试替身与无库装配没有这张台账。默认值刻意选"做不到"而不是
 * "假装做到了"——把写不进当成"已登记"会让一次生成既没做也没人记得，正是本表要消灭的失败。
 */
interface FigureGenerationRecorderPort {
    /**
     * 建立一行操作（`PENDING`）。同 `operationId` 重复触发是重放：既有行保留、不被覆盖，
     * 端口仍返回 true（"行已在"就是这次调用的目的）。
     */
    suspend fun beginFigureGenerationOperation(
        command: BeginFigureGenerationOperationCommand,
    ): Boolean = false

    /** 预留授予后把操作推进到 `RUNNING`（只改状态与更新时间）。 */
    suspend fun markFigureGenerationOperationRunning(
        operationId: String,
        updatedAtEpochMillis: Long,
    ): Boolean = false

    /** 落操作终态与三个审计计数（重复落同一终态是幂等写）。 */
    suspend fun finishFigureGenerationOperation(
        command: FinishFigureGenerationOperationCommand,
    ): Boolean = false

    /**
     * 建立一次 `RUNNING` 尝试。`(operation_id, attempt_ordinal)` 撞车是调用方的身份错误，
     * 不是可以静默吞掉的冲突；端口抛出，recorder 按"没记上"处理（不影响生成）。
     */
    suspend fun beginFigureGenerationAttempt(
        command: BeginFigureGenerationAttemptCommand,
    ): Boolean = false

    /** 落尝试终态与有界诊断；`output_asset_id` 在成功时尚不知道，由入口落盘后回填。 */
    suspend fun finishFigureGenerationAttempt(
        command: FinishFigureGenerationAttemptCommand,
    ): Boolean = false

    /**
     * 入口落盘成功后回填产物资产 id。只更新 `output_asset_id`，不改状态与结束时刻
     * （attempt 已经是终态）；资产被删除前 RESTRICT 会先挡住。
     */
    suspend fun attachFigureGenerationAttemptOutput(
        attemptId: String,
        outputAssetId: String,
    ): Boolean = false
}

/** 建立操作行；`created_at_epoch_millis` 与 `updated_at_epoch_millis` 同取建立时刻。 */
data class BeginFigureGenerationOperationCommand(
    val operationId: String,
    val learnerId: String,
    val sourceKind: String,
    val sourceId: String,
    val basisFingerprint: String,
    val purpose: String,
    val policyVersion: String,
    val status: String,
    val createdAtEpochMillis: Long,
)

/** 操作终态 + 审计计数（只写事实，不做比较交换）。 */
data class FinishFigureGenerationOperationCommand(
    val operationId: String,
    val status: String,
    val reservedRemoteDispatches: Int,
    val usedRemoteDispatches: Int,
    val finalAnswerReserved: Boolean,
    val updatedAtEpochMillis: Long,
)

/** 建立一次 `RUNNING` 尝试。 */
data class BeginFigureGenerationAttemptCommand(
    val attemptId: String,
    val operationId: String,
    val attemptOrdinal: Int,
    val method: String,
    val status: String,
    val sourceSha256: String?,
    val inputFingerprint: String,
    val reservationId: String,
    val startedAtEpochMillis: Long,
)

/** 尝试终态；`diagnostics` 有界（由调用方截断），成功/取消时为 null 或可读原因。 */
data class FinishFigureGenerationAttemptCommand(
    val attemptId: String,
    val status: String,
    val outputAssetId: String?,
    val diagnostics: String?,
    val endedAtEpochMillis: Long,
)
