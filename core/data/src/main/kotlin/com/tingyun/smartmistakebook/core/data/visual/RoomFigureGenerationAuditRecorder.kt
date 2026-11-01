package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.BeginLocalFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.FinishFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.FinishFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationRecorderPort
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 生产审计记录器：把 [FigureGenerationAuditRecorder] 的调用翻译成
 * [FigureGenerationRecorderPort] 命令（schema 69 的两张表）。
 *
 * **它不读预算**：只写 operation/attempt 行与三个审计镜像列；`RemoteModelDispatchLedger`
 * 仍是唯一的派发/预算权威。读写失败（库忙、迁移未完成、行冲突）一律降级为 false/无操作：
 * 审计是证据，不是业务成败的判据——生成已经发生，不能因为台账写不进就说"没有生成"。
 *
 * [learnerId] 与库内其余单用户表同口径（`learner:local`），不是多用户路由。
 */
internal class RoomFigureGenerationAuditRecorder(
    private val port: FigureGenerationRecorderPort,
    private val learnerId: String = DEFAULT_LEARNER_ID,
) : FigureGenerationAuditRecorder {

    override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean =
        write {
            port.beginFigureGenerationOperation(
                BeginFigureGenerationOperationCommand(
                    operationId = operation.operationId,
                    learnerId = learnerId,
                    sourceKind = operation.sourceKind.name,
                    sourceId = operation.sourceId,
                    basisFingerprint = operation.basisFingerprint,
                    purpose = operation.purpose.name,
                    policyVersion = operation.policyVersion,
                    status = OPERATION_PENDING,
                    createdAtEpochMillis = operation.createdAtEpochMillis,
                ),
            )
        } ?: false

    override suspend fun markOperationRunning(
        operationId: String,
        updatedAtEpochMillis: Long,
    ): Boolean = write {
        port.markFigureGenerationOperationRunning(operationId, updatedAtEpochMillis)
    } ?: false

    override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean =
        write {
            port.beginFigureGenerationAttempt(
                BeginFigureGenerationAttemptCommand(
                    attemptId = attempt.attemptId,
                    operationId = attempt.operationId,
                    attemptOrdinal = attempt.attemptOrdinal,
                    method = attempt.method.name,
                    status = ATTEMPT_RUNNING,
                    sourceSha256 = attempt.sourceSha256,
                    inputFingerprint = attempt.inputFingerprint,
                    reservationId = attempt.reservationId,
                    startedAtEpochMillis = attempt.startedAtEpochMillis,
                ),
            )
        } ?: false

    /**
     * 本地方法（JS）尝试：没有预留，序号由持久端口在同一个写事务里分配。
     * 端口"做不到"（默认实现/null）时如实回 null，不伪造 attemptId。
     */
    override suspend fun beginLocalAttempt(
        operationId: String,
        method: FigureGenerationMethod,
        inputFingerprint: String,
        sourceSha256: String?,
        startedAtEpochMillis: Long,
    ): FigureGenerationAuditAttempt? {
        val identity = write {
            port.beginLocalFigureGenerationAttempt(
                BeginLocalFigureGenerationAttemptCommand(
                    operationId = operationId,
                    method = method.name,
                    status = ATTEMPT_RUNNING,
                    sourceSha256 = sourceSha256,
                    inputFingerprint = inputFingerprint,
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        } ?: return null
        return FigureGenerationAuditAttempt(
            attemptId = identity.attemptId,
            operationId = operationId,
            attemptOrdinal = identity.attemptOrdinal,
            method = method,
            reservationId = null,
            inputFingerprint = inputFingerprint,
            sourceSha256 = sourceSha256,
            startedAtEpochMillis = startedAtEpochMillis,
        )
    }

    override suspend fun finishAttempt(
        attemptId: String,
        outcome: FigureGenerationAuditOutcome,
        outputAssetId: String?,
        diagnostics: String?,
        endedAtEpochMillis: Long,
    ): Boolean = write {
        port.finishFigureGenerationAttempt(
            FinishFigureGenerationAttemptCommand(
                attemptId = attemptId,
                status = outcome.name,
                outputAssetId = outputAssetId,
                diagnostics = diagnostics,
                endedAtEpochMillis = endedAtEpochMillis,
            ),
        )
    } ?: false

    override suspend fun finishOperation(
        operationId: String,
        outcome: FigureGenerationAuditOutcome,
        reservedRemoteDispatches: Int,
        usedRemoteDispatches: Int,
        finalAnswerReserved: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean = write {
        port.finishFigureGenerationOperation(
            FinishFigureGenerationOperationCommand(
                operationId = operationId,
                status = outcome.name,
                reservedRemoteDispatches = reservedRemoteDispatches,
                usedRemoteDispatches = usedRemoteDispatches,
                finalAnswerReserved = finalAnswerReserved,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
    } ?: false

    /**
     * 回填是入口在产物**已经落盘**之后的动作：用 NonCancellable 保证这次写不被取消打断，
     * 失败只丢一条审计链接，绝不反悔已成功的生成。
     */
    override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) {
        withContext(NonCancellable) {
            write { port.attachFigureGenerationAttemptOutput(attemptId, outputAssetId) }
        }
    }

    private suspend fun <T> write(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private companion object {
        /** 与库内其余单用户表同口径（`RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID`）。 */
        const val DEFAULT_LEARNER_ID = "learner:local"
        const val OPERATION_PENDING = "PENDING"
        const val ATTEMPT_RUNNING = "RUNNING"
    }
}
