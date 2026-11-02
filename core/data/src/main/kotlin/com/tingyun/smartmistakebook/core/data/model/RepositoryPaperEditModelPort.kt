package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditModelException
import com.tingyun.smartmistakebook.core.model.PaperEditModelPort
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import java.util.UUID
import kotlinx.coroutines.flow.last

/**
 * [PaperEditModelPort] 的持久实现（批次 41 / WS7 项 46）：把一次改卷交给既有
 * [ModelTaskRepository]，等终态，只交回白名单解析器已经放行的 [PaperEditOutput]。
 *
 * 消灭的具体失败（与接口 KDoc 同一组）：feature 侧若直连网关或自己拼请求，会绕过有界快照、
 * schema 19 守卫与**同一把**白名单解析器，并且可能以"原始字符串"跨层传递——任务在账本里
 * SUCCEEDED，applier 却解析失败。这里把"提交 → 终态 → 类型化命令"收成一个不可分叉的入口。
 *
 * 不新造任何东西：请求形状、失败码、派发预算都复用既有 `ModelTaskRepository`（`kind.name +
 * 输入 JSON` 决定逻辑操作，因此改卷有自己的 6 次额度，不与讲题轮共享）。本类不构造
 * `egressManifest`：改卷走全局同意通道，逐次清单不是它的授权形状。
 */
class RepositoryPaperEditModelPort(
    private val repository: ModelTaskRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestIds: () -> String = { "paper-edit:" + UUID.randomUUID() },
) : PaperEditModelPort {

    override suspend fun commandsFor(
        input: PaperEditInput,
        agentConsentGranted: Boolean,
    ): PaperEditOutput {
        val request = ModelTaskRequest(
            requestId = requestIds(),
            input = input,
            occurredAtEpochMillis = clock(),
            agentConsentGranted = agentConsentGranted,
        )
        val settled = try {
            repository.execute(request).last()
        } catch (empty: NoSuchElementException) {
            throw PaperEditModelException(
                status = null,
                failureCode = null,
                retryable = false,
                message = "改卷任务没有产生任何状态，请稍后重试",
                cause = empty,
            )
        }
        if (settled.request.requestId != request.requestId) {
            // 终态必须属于本次提交：把别的请求的结果交给 applier 等于按错误卷面改卷。
            throw PaperEditModelException(
                status = settled.status,
                failureCode = null,
                retryable = false,
                message = "改卷任务的终态不属于本次请求",
            )
        }
        return when (settled.status) {
            ModelTaskStatus.SUCCEEDED -> (settled.output as? PaperEditOutput)
                ?: throw PaperEditModelException(
                    status = settled.status,
                    failureCode = null,
                    retryable = false,
                    message = "模型返回的不是改卷命令",
                )
            ModelTaskStatus.CANCELLED -> throw PaperEditModelException(
                status = settled.status,
                failureCode = null,
                retryable = false,
                message = settled.userMessage.ifBlank { "改卷已取消" },
            )
            ModelTaskStatus.RETRYABLE_FAILURE,
            ModelTaskStatus.PERMANENT_FAILURE,
            -> {
                val failure = settled.failure
                throw PaperEditModelException(
                    status = settled.status,
                    failureCode = failure?.code,
                    retryable = failure?.retryable ?: false,
                    message = failure?.message ?: settled.userMessage.ifBlank { "改卷没有完成" },
                )
            }
            // QUEUED / RUNNING / WAITING_FOR_MODEL / STREAMING：流结束了却没有终态。
            ModelTaskStatus.WAITING_FOR_MODEL,
            ModelTaskStatus.QUEUED,
            ModelTaskStatus.RUNNING,
            ModelTaskStatus.STREAMING,
            -> throw PaperEditModelException(
                status = settled.status,
                failureCode = null,
                retryable = true,
                message = "改卷任务没有落终态，请稍后重试",
            )
        }
    }
}
