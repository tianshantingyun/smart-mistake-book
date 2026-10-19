package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.ReserveModelTaskOperationDispatchCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchKind
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest as DispatchRequest
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import kotlinx.coroutines.CancellationException

/**
 * 生产账本：把 4E.1 的图像派发预留接到**既有持久记录**上（本波次不新增表/列）。
 *
 * 两种父操作：
 * 1. [FigureDispatchParent.ModelTask]（讲题工具 / 转写 / 净化分类所在任务）：
 *    调用 [StudyDatabasePort.reserveModelTaskOperationDispatch]，只递增
 *    `model_task_operation.dispatch_count`，不推进任务行状态机。这样模型任务环在工具执行后
 *    用自己的比较交换继续预留时不会被账本的写入撞车。
 * 2. [FigureDispatchParent.CleanRedrawTask]（后台净化作业）：作业行在生成前已经
 *    `claimCleanRedrawTaskAttempt` 领取过一次（`attempt_count` +1，durable）；这次领取
 *    就是该作业的持久派发预留。这里只**核验**行仍存在、仍是这一次领取、状态可执行、
 *    源图 sha 与请求一致，不重复递增（重复递增会把一次尝试花成两次预算）。
 *
 * 任何"没有可复用持久记录"的路径都 fail-closed（Denied）：协调器不会建立图像通道、不会发 HTTP。
 */
internal class RoomFigureDispatchLedger(
    private val database: StudyDatabasePort,
    private val now: () -> Long = System::currentTimeMillis,
) : RemoteModelDispatchLedger {

    override suspend fun reserve(request: DispatchRequest): RemoteModelDispatchDecision =
        when (val parent = request.parent) {
            is FigureDispatchParent.ModelTask -> reserveModelTaskDispatch(request, parent)
            is FigureDispatchParent.CleanRedrawTask -> reserveCleanRedrawDispatch(request, parent)
        }

    private suspend fun reserveModelTaskDispatch(
        request: DispatchRequest,
        parent: FigureDispatchParent.ModelTask,
    ): RemoteModelDispatchDecision {
        val occurredAt = now()
        val result = try {
            database.reserveModelTaskOperationDispatch(
                ReserveModelTaskOperationDispatchCommand(
                    parentRequestId = parent.requestId,
                    remoteKind = request.remoteKind.name,
                    method = request.method.name,
                    reserveFinalAnswer = parent.reserveChatFinalAnswer,
                    occurredAtEpochMillis = occurredAt,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return RemoteModelDispatchDecision.Denied(
                code = RemoteModelDispatchDenialCode.CONFLICT,
                message = "持久派发预留没有写入成功，未出网",
            )
        }
        if (!result.applied) {
            val code = when {
                result.budgetExhausted -> RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED
                result.logicalDispatchCount == 0 -> RemoteModelDispatchDenialCode.PARENT_NOT_FOUND
                else -> RemoteModelDispatchDenialCode.CONFLICT
            }
            // KD-60：拒绝理由必须可读地说清“共享预算 + 真实已派发次数”，
            // 不能只回一句“没有生成成功”。
            val usableLimit = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES -
                if (parent.reserveChatFinalAnswer) 1 else 0
            val budgetDetail = buildString {
                append("本逻辑操作已派发 ").append(result.logicalDispatchCount)
                append(" 次（上限 ").append(usableLimit).append(" 次")
                if (parent.reserveChatFinalAnswer) {
                    append("，另为讲题终答保留 1 次")
                }
                append("）")
                append("；图像生成与讲题工具轮共享同一份 durable 预算")
            }
            return RemoteModelDispatchDecision.Denied(
                code = code,
                message = if (code == RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED) {
                    "逻辑操作的远程派发预算已用尽：$budgetDetail；未出网"
                } else {
                    "没有可复用的持久派发记录：$budgetDetail；未出网"
                },
            )
        }
        val reservationId = requireNotNull(result.reservationId) {
            "An applied operation dispatch reservation carries its id"
        }
        return RemoteModelDispatchDecision.Granted(
            RemoteModelDispatchReservation(
                reservationId = reservationId,
                operationId = request.operationId,
                parent = parent,
                remoteKind = request.remoteKind,
                method = request.method,
                purpose = request.purpose,
                policyVersion = request.policyVersion,
                sourceImageSha256 = request.sourceImageSha256,
                configurationFingerprint = request.configurationFingerprint,
                attemptOrdinal = result.logicalDispatchCount,
                reservedAtEpochMillis = occurredAt,
            ),
        )
    }

    private suspend fun reserveCleanRedrawDispatch(
        request: DispatchRequest,
        parent: FigureDispatchParent.CleanRedrawTask,
    ): RemoteModelDispatchDecision {
        val row = try {
            database.readCleanRedrawTask(parent.taskId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return RemoteModelDispatchDecision.Denied(
            code = RemoteModelDispatchDenialCode.PARENT_NOT_FOUND,
            message = "净化作业行不存在，未出网",
        )
        if (row.attemptCount != parent.attemptCount) {
            return RemoteModelDispatchDecision.Denied(
                code = RemoteModelDispatchDenialCode.PARENT_NOT_RESERVABLE,
                message = "净化作业的领取次数与这次尝试不一致，未出网",
            )
        }
        if (row.status == CleanRedrawTaskStatus.SUCCEEDED || row.status == CleanRedrawTaskStatus.SUPERSEDED) {
            return RemoteModelDispatchDecision.Denied(
                code = RemoteModelDispatchDenialCode.PARENT_NOT_RESERVABLE,
                message = "净化作业已无需要执行的尝试，未出网",
            )
        }
        if (request.sourceImageSha256 != null && row.sourceImageSha256 != request.sourceImageSha256) {
            return RemoteModelDispatchDecision.Denied(
                code = RemoteModelDispatchDenialCode.CONFLICT,
                message = "净化作业绑定的源图与这次请求不一致，未出网",
            )
        }
        return RemoteModelDispatchDecision.Granted(
            RemoteModelDispatchReservation(
                reservationId = "clean-redraw-dispatch:${row.taskId}:${row.attemptCount}",
                operationId = request.operationId,
                parent = parent,
                remoteKind = RemoteModelDispatchKind.IMAGE_GENERATION,
                method = request.method,
                purpose = request.purpose,
                policyVersion = request.policyVersion,
                sourceImageSha256 = request.sourceImageSha256,
                configurationFingerprint = request.configurationFingerprint,
                attemptOrdinal = row.attemptCount,
                reservedAtEpochMillis = now(),
            ),
        )
    }
}
