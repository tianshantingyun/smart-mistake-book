package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.MODEL_TASK_STATUS_MESSAGE_MAX_CHARS
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class CreateModelTaskCommand(
    val taskId: String,
    val request: ModelTaskRequest,
    val requestFingerprint: String,
    val operationFingerprint: String = ModelTaskLogicalOperationFingerprint.of(request),
    val occurredAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "Model task id must not be blank" }
        require(requestFingerprint.length == 64) { "Model task fingerprint must be SHA-256" }
        require(requestFingerprint == ModelTaskFingerprint.of(request)) {
            "Model task fingerprint does not match its request"
        }
        require(operationFingerprint.length == 64) {
            "Model task operation fingerprint must be SHA-256"
        }
        require(operationFingerprint == ModelTaskLogicalOperationFingerprint.of(request)) {
            "Model task operation fingerprint does not match its immutable input"
        }
        require(occurredAtEpochMillis >= 0) { "Model task creation time must not be negative" }
    }
}

data class ReserveModelTaskRemoteDispatchCommand(
    val taskId: String,
    val expectedStateVersion: Long,
    val expectedStatus: ModelTaskStatus,
    val provider: ProviderCapabilitySnapshot,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "Model task id must not be blank" }
        require(expectedStateVersion >= 0) { "Expected model task version must not be negative" }
        require(expectedStatus == ModelTaskStatus.QUEUED) {
            "Only a queued model task may reserve a remote dispatch"
        }
        require(provider.executionLocation == ModelExecutionLocation.EXTERNAL_PROVIDER) {
            "Only an external provider may reserve a remote dispatch"
        }
        require(occurredAtEpochMillis >= 0) { "Model task reservation time must not be negative" }
    }
}

/**
 * 4E.1：为**同一逻辑操作的非任务派发**（图像生成/编辑）预留一次远程预算。
 *
 * 为什么不是 `reserveModelTaskRemoteDispatch`：图像调用发生在模型任务行处于 QUEUED/RUNNING
 * 之外的工具执行阶段，那支命令会推进任务行的 `state_version`，与模型任务环自己的比较交换
 * 撞车。这里只推进既有 `model_task_operation.dispatch_count`（**不新增表/列**），
 * 因此任务行的状态机完全不动，账本仍是同一份持久预算。
 *
 * [reserveFinalAnswer] = 讲题父操作：本次预留后必须仍余 1 次给终答（最多用到第 5 次）。
 */
data class ReserveModelTaskOperationDispatchCommand(
    val parentRequestId: String,
    val remoteKind: String,
    val method: String,
    val reserveFinalAnswer: Boolean,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(parentRequestId.isNotBlank()) {
            "An operation dispatch reservation needs its parent request id"
        }
        require(remoteKind.isNotBlank()) { "An operation dispatch reservation needs a remote kind" }
        require(method.isNotBlank()) { "An operation dispatch reservation needs a method" }
        require(occurredAtEpochMillis >= 0) {
            "An operation dispatch reservation time must not be negative"
        }
    }
}

data class ModelTaskOperationDispatchReservationResult(
    val applied: Boolean,
    val budgetExhausted: Boolean,
    val logicalDispatchCount: Int,
    val reservationId: String? = null,
) {
    init {
        require(logicalDispatchCount >= 0) {
            "Logical operation dispatch count must not be negative"
        }
        require(!applied || !budgetExhausted) {
            "An applied operation dispatch reservation cannot be exhausted"
        }
        require(!applied || reservationId != null) {
            "An applied operation dispatch reservation must carry its id"
        }
    }
}

data class TransitionModelTaskCommand(
    val taskId: String,
    val expectedStateVersion: Long,
    val expectedStatus: ModelTaskStatus,
    val nextStatus: ModelTaskStatus,
    val stage: ModelTaskStage,
    val userMessage: String,
    val attemptCount: Int,
    val provider: ProviderCapabilitySnapshot? = null,
    val output: ModelTaskOutput? = null,
    val failure: ModelTaskFailure? = null,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "Model task id must not be blank" }
        require(expectedStateVersion >= 0) { "Expected model task version must not be negative" }
        require(expectedStatus.canTransitionTo(nextStatus)) {
            "Illegal model task transition: $expectedStatus -> $nextStatus"
        }
        require(userMessage.length <= MODEL_TASK_STATUS_MESSAGE_MAX_CHARS) {
            "Model task message exceeds budget"
        }
        require(attemptCount >= 0) { "Model task attempt count must not be negative" }
        require(nextStatus != ModelTaskStatus.SUCCEEDED || output != null) {
            "A successful model task transition must include output"
        }
        require(
            nextStatus != ModelTaskStatus.RETRYABLE_FAILURE &&
                nextStatus != ModelTaskStatus.PERMANENT_FAILURE || failure != null,
        ) { "A failed model task transition must include failure details" }
        require(occurredAtEpochMillis >= 0) { "Model task transition time must not be negative" }
    }
}

data class ModelTaskWriteResult(
    val applied: Boolean,
    val snapshot: ModelTaskSnapshot,
)

data class ModelTaskDispatchReservationResult(
    val applied: Boolean,
    val budgetExhausted: Boolean,
    val logicalDispatchCount: Int,
    val snapshot: ModelTaskSnapshot,
) {
    init {
        require(logicalDispatchCount >= 0) {
            "Logical model operation dispatch count must not be negative"
        }
        require(!applied || !budgetExhausted) {
            "An applied model dispatch reservation cannot be exhausted"
        }
        require(
            !applied ||
                (snapshot.status == ModelTaskStatus.RUNNING &&
                    snapshot.attemptCount == logicalDispatchCount),
        ) { "An applied model dispatch reservation must return its durable running snapshot" }
    }
}

interface ModelTaskDatabasePort {
    fun observeModelTask(requestId: String): Flow<ModelTaskSnapshot?>

    fun observeModelTasks(
        subjectId: String,
        kind: ModelTaskKind,
    ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

    fun observeRecentModelTasks(
        subjectId: String,
        kind: ModelTaskKind,
        limit: Int,
    ): Flow<List<ModelTaskSnapshot>> {
        require(limit > 0) { "Recent model-task limit must be positive" }
        return observeModelTasks(subjectId, kind).map { snapshots -> snapshots.takeLast(limit) }
    }

    suspend fun readModelTask(requestId: String): ModelTaskSnapshot?

    suspend fun createModelTask(command: CreateModelTaskCommand): ModelTaskWriteResult

    suspend fun reserveModelTaskRemoteDispatch(
        command: ReserveModelTaskRemoteDispatchCommand,
    ): ModelTaskDispatchReservationResult

    /**
     * 4E.1：为同一逻辑操作的图像生成/编辑预留一次远程预算。
     *
     * 默认实现是 fail-closed（不预留）：没有覆盖它的数据库端口不会让图像链绕过预算。
     * 覆盖方只需原子递增既有 `model_task_operation.dispatch_count`，不推进任务行状态。
     */
    suspend fun reserveModelTaskOperationDispatch(
        command: ReserveModelTaskOperationDispatchCommand,
    ): ModelTaskOperationDispatchReservationResult = ModelTaskOperationDispatchReservationResult(
        applied = false,
        budgetExhausted = false,
        logicalDispatchCount = 0,
        reservationId = null,
    )

    suspend fun transitionModelTask(command: TransitionModelTaskCommand): ModelTaskWriteResult

    /**
     * 写入证据行（含账本事件分配）。**回报**落库结果（AUD-11 ③-2）：accepted 行被
     * Room IGNORE 吃掉（同身份已在库）时，调用方不能再以为"已记录"——
     * 见 [com.tingyun.smartmistakebook.core.database.dao.ChatEvidenceWriteReceipt]。
     */
    suspend fun recordChatEvidence(
        entries: List<com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity>,
    ): com.tingyun.smartmistakebook.core.database.dao.ChatEvidenceWriteReceipt

    suspend fun readChatEvidenceByLearner(learnerId: String): List<com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity>

    /**
     * Chat evidence for one conversation (research tutor-evidence-gate §4):
     * the gate reads this to enforce the same-KC cooldown and the
     * per-conversation write quota before accepting a MASTERY_UPDATE write.
     */
    suspend fun readChatEvidenceByConversation(conversationId: String): List<com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity>

    // ---- Indexed gate queries (batch-scale: O(log n), no full-table pull) ----

    /**
     * Most recent accepted write **of one channel** ([sourceKind]) to one KC by the learner,
     * across all conversations.
     *
     * AUD-11 ①：冷却起点按通道取——两条写通道各自冷却（模型通道的自我重复由 12h 冷却挡，
     * 客观作答通道按自身幂等身份防重）。查询在 DAO 侧带 `source_kind` 过滤，通道无关的读法
     * 不再存在（那正是"小测写出模型写的冷却起点"的实测缺陷）。
     */
    suspend fun lastAcceptedChatEvidenceAtForKc(
        learnerId: String,
        knowledgeNodeId: String,
        sourceKind: String,
    ): Long?

    /** Accepted chat-evidence writes by the learner since [sinceEpochMillis]. */
    suspend fun countAcceptedChatEvidenceSince(learnerId: String, sinceEpochMillis: Long): Int

    /** Accepted chat-evidence writes in one conversation. */
    suspend fun countAcceptedChatEvidenceInConversation(conversationId: String): Int

    /** Rejected-write counts grouped by channel and gate reason (calibration input). */
    suspend fun countRejectedChatEvidenceByReason(learnerId: String): List<com.tingyun.smartmistakebook.core.database.dao.RejectedReasonCountRow>

    /** Accepted-write counts per channel per epoch hour since [sinceEpochMillis] (calibration input). */
    suspend fun countAcceptedChatEvidencePerHour(learnerId: String, sinceEpochMillis: Long): List<com.tingyun.smartmistakebook.core.database.dao.HourlyAcceptedCountRow>
}
