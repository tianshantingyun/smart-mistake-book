package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import kotlinx.coroutines.CancellationException

/**
 * 图生成审计台账的**记录器**（4E WG-08 T1b）。
 *
 * **它消灭的具体失败**：改前一次配图/重绘的"来源、冻结输入、预留、产物"只活在那个协程和
 * 一行日志里；进程被杀后无法回答"这张图用了哪个来源、哪次预留、是不是用过最终答案预算"。
 * 协调器只经本端口写 operation/attempt 两行，不直接依赖 Room/Android，JVM 测试可以用
 * 内存替身钉住状态机与计数。
 *
 * 三条红线：
 * - **不是预算权威**：本接口没有任何"读余量/扣预算"的方法；计数只写不读。真正拦截派发的
 *   仍是 `RemoteModelDispatchLedger`。
 * - **审计写失败不影响生成**：实现必须吞掉持久化失败（取消除外），返回 false；协调器据此
 *   把 attemptId 置空，但生成结果照旧。
 * - **终态 attempt 不复活**：每次尝试只 begin 一次、finish 一次；回填产物只改
 *   `output_asset_id`，不改状态。唯一例外是启动对账把"SUCCEEDED 但 output 一直为空"的
 *   假成功纠正为 FAILED（见 `FigureGenerationReconciler`）——那不是新尝试，而是把
 *   与产物事实不符的终态改成待核实。
 */
internal interface FigureGenerationAuditRecorder {

    /** 建立操作（`PENDING`）。返回 true = 行已在（重放也算在）。 */
    suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean

    /** 预留授予后把操作推进到 `RUNNING`。 */
    suspend fun markOperationRunning(
        operationId: String,
        updatedAtEpochMillis: Long,
    ): Boolean

    /** 建立尝试（`RUNNING`）；只能在拿到 reservation 之后调用。 */
    suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean

    /** 落尝试终态；`outputAssetId` 成功时为 null（由入口落盘后回填）。 */
    suspend fun finishAttempt(
        attemptId: String,
        outcome: FigureGenerationAuditOutcome,
        outputAssetId: String?,
        diagnostics: String?,
        endedAtEpochMillis: Long,
    ): Boolean

    /** 落操作终态与三个审计计数（reserved/used/finalAnswerReserved）。 */
    suspend fun finishOperation(
        operationId: String,
        outcome: FigureGenerationAuditOutcome,
        reservedRemoteDispatches: Int,
        usedRemoteDispatches: Int,
        finalAnswerReserved: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean

    /**
     * 入口落盘成功后回填产物 id。实现必须 best-effort（不得把已成功的生成改判为失败），
     * 并在 [kotlinx.coroutines.NonCancellable] 中完成这一次写。
     */
    suspend fun attachOutputAsset(attemptId: String, outputAssetId: String)

    /** 没注入记录器的旧装配：所有调用都是无操作，且不产生任何持久副作用。 */
    object Noop : FigureGenerationAuditRecorder {
        override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean =
            false

        override suspend fun markOperationRunning(
            operationId: String,
            updatedAtEpochMillis: Long,
        ): Boolean = false

        override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean = false

        override suspend fun finishAttempt(
            attemptId: String,
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
            endedAtEpochMillis: Long,
        ): Boolean = false

        override suspend fun finishOperation(
            operationId: String,
            outcome: FigureGenerationAuditOutcome,
            reservedRemoteDispatches: Int,
            usedRemoteDispatches: Int,
            finalAnswerReserved: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean = false

        override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) = Unit
    }
}

/** 一次操作的冻结输入（落 `figure_generation_operation`）。 */
internal data class FigureGenerationAuditOperation(
    val operationId: String,
    val sourceKind: FigureGenerationSourceKind,
    val sourceId: String,
    val basisFingerprint: String,
    val purpose: FigureGenerationPurpose,
    val policyVersion: String,
    val createdAtEpochMillis: Long,
)

/** 一次尝试的身份与预留绑定（落 `figure_generation_attempt`）。 */
internal data class FigureGenerationAuditAttempt(
    val attemptId: String,
    val operationId: String,
    val attemptOrdinal: Int,
    val method: FigureGenerationMethod,
    val reservationId: String,
    val inputFingerprint: String,
    val sourceSha256: String?,
    val startedAtEpochMillis: Long,
)

/** 审计终态（operation/attempt 共用；`RUNNING`/`PENDING` 只由 begin/mark 写入）。 */
internal enum class FigureGenerationAuditOutcome {
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * 入口在产物落盘后的回填（`figure_generation_attempt.output_asset_id`）。
 *
 * 回填缝用函数类型而不是 recorder 类型：`AttachedImageGenerator` 是公开类，公开构造函数
 * 不能暴露 internal 的 recorder。两个入口（讲题、历史配图）共用这一层 try/catch：
 * - 没有 attemptId（没注入 recorder / 台账没写成 / 生成没开始）→ 不动作；
 * - 回填失败不反悔已成功的生成（审计是证据，不是业务判据）；
 * - **取消照旧抛出**（应用被杀不是"这张图没有"，也不是"回填可以吞掉取消"）。
 */
internal suspend fun recordFigureOutputAsset(
    record: suspend (attemptId: String, assetId: String) -> Unit,
    attemptId: String?,
    assetId: String,
) {
    if (attemptId == null) return
    try {
        record(attemptId, assetId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // 见 KDoc：回填失败只丢一条审计链接。
    }
}
