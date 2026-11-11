package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.entity.ErrorCauseBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.ErrorCauseObservationEntity
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseBindingState
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDecisionSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRefsCodec
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope

/**
 * 错因账本（schema 73）的跨层读写口（批次 40 / WS2）。
 *
 * **它消灭的具体失败**：core/data 不能直接摸 internal DAO；如果每处调用方各自写
 * "插 observation + 改 binding" 的多条 SQL，进程死在两条写之间就会留下半行。端口把
 * 三种状态迁移（落账 / 撤回 / 改判）收敛成三个**单事务**方法。
 *
 * 默认实现抛 [UnsupportedOperationException]：这是新增能力，不给既有测试替身伪造一个
 * "看起来成功"的空实现（与 `TutorVisualPort` 同约定）。
 */
interface ErrorCausePort {

    /** 追加一条 observation；同内容重放返回 [ErrorCauseAppendResult.DUPLICATE]，不写第二行。 */
    suspend fun appendObservation(record: ErrorCauseObservationRecord): ErrorCauseAppendResult =
        throw UnsupportedOperationException("This StudyDatabasePort implementation does not support error-cause observations")

    /** 现行标签读侧：只返回 `state=ACTIVE` 且来源 observation 已 ACCEPTED 的行。 */
    suspend fun readActiveBinding(scope: ErrorCauseScope): ErrorCauseBindingRecord? =
        throw UnsupportedOperationException("This StudyDatabasePort implementation does not support error-cause bindings")

    /**
     * 候选读侧：某学习者某道练习单元下仍是 `PROPOSED` 的 observation（只读，不迁移状态）。
     *
     * **为什么按 learner+unit 而不是按 scope 查**：SQL 层没有 `problem_revision_id` 的活键概念，
     * 调用方（详情页）要自己按当前 `scope` 过滤——旧修订的候选不得自动继承（R2 I7）。
     * 读候选**不产生任何写**：候选升为标签只走用户确认或 D-1c 门。
     */
    suspend fun readProposedObservations(
        learnerId: String,
        practiceUnitId: String,
    ): List<ErrorCauseObservationRecord> =
        throw UnsupportedOperationException("This StudyDatabasePort implementation does not support error-cause observations")

    /** 自动落账/用户确认：observation PROPOSED→ACCEPTED + 插 ACTIVE binding，同一事务。 */
    suspend fun acceptObservationAndActivateBinding(
        binding: ErrorCauseBindingRecord,
        decidedAtEpochMillis: Long,
        decisionSource: ErrorCauseDecisionSource,
    ): ErrorCauseWriteResult =
        throw UnsupportedOperationException("This StudyDatabasePort implementation does not support error-cause bindings")

    /** 撤回：插 RETRACTED observation + ACTIVE binding→VOIDED，同一事务。 */
    suspend fun retractActiveBinding(
        retraction: ErrorCauseObservationRecord,
        bindingId: String,
        endedAtEpochMillis: Long,
    ): ErrorCauseWriteResult =
        throw UnsupportedOperationException("This StudyDatabasePort implementation does not support error-cause bindings")

    /** 改判：新 observation ACCEPTED + 旧 binding SUPERSEDED + 新 binding ACTIVE，同一事务。 */
    suspend fun supersedeActiveBinding(
        newObservation: ErrorCauseObservationRecord,
        newBinding: ErrorCauseBindingRecord,
        oldBindingId: String,
        decidedAtEpochMillis: Long,
        decisionSource: ErrorCauseDecisionSource,
    ): ErrorCauseWriteResult =
        throw UnsupportedOperationException("This StudyDatabasePort implementation does not support error-cause bindings")
}

enum class ErrorCauseAppendResult {
    INSERTED,
    DUPLICATE,
}

enum class ErrorCauseWriteResult {
    APPLIED,
    CONFLICT,
    NOT_FOUND,
}

/** 一条 observation 的端口记录；与实体同形，但用 model 枚举而不是裸串。 */
data class ErrorCauseObservationRecord(
    val observationId: String,
    val scope: ErrorCauseScope,
    val attemptId: String?,
    val cause: ErrorCause,
    val detail: ErrorCauseDetail?,
    val confidence: Double,
    val channel: ErrorCauseChannel,
    val extractorVersion: String,
    val taxonomyVersion: String,
    val rationaleMarkdown: String,
    val evidenceRefs: List<ErrorCauseEvidenceRef>,
    val observedAtEpochMillis: Long,
    val status: ErrorCauseObservationStatus,
    val decidedAtEpochMillis: Long? = null,
    val decisionSource: ErrorCauseDecisionSource? = null,
    val supersedesObservationId: String? = null,
) {
    init {
        require(observationId.isNotBlank()) { "observationId 不能为空" }
        require(detail == null || detail.cause == cause) { "细码不属于顶层错因" }
        require(confidence.isFinite() && confidence in 0.0..1.0) { "置信度必须在 0..1" }
        require(extractorVersion.isNotBlank()) { "extractorVersion 不能为空" }
        require(taxonomyVersion.isNotBlank()) { "taxonomyVersion 不能为空" }
        require(rationaleMarkdown.isNotBlank()) { "理由不能为空" }
        require(evidenceRefs.isNotEmpty()) { "证据引用不能为空" }
        require(evidenceRefs.size == evidenceRefs.toSet().size) { "证据引用不能重复" }
        require(observedAtEpochMillis >= 0) { "观察时间不能为负" }
        require(status != ErrorCauseObservationStatus.ACCEPTED || decidedAtEpochMillis != null) {
            "ACCEPTED observation 必须有 decided_at"
        }
        require(status != ErrorCauseObservationStatus.PROPOSED || decidedAtEpochMillis == null) {
            "PROPOSED observation 不得带 decided_at"
        }
        require(status != ErrorCauseObservationStatus.RETRACTED || supersedesObservationId != null) {
            "RETRACTED observation 必须指向被撤回的原证据"
        }
    }
}

/** 一条现行/历史 binding 的端口记录。 */
data class ErrorCauseBindingRecord(
    val bindingId: String,
    val scope: ErrorCauseScope,
    val cause: ErrorCause,
    val detail: ErrorCauseDetail?,
    val confidence: Double,
    val acceptanceSource: ErrorCauseAcceptanceSource,
    val gateVersion: String?,
    val sourceObservationId: String,
    val acceptedAtEpochMillis: Long,
    val state: ErrorCauseBindingState,
    val activeKey: String?,
    val supersededByBindingId: String? = null,
    val endedAtEpochMillis: Long? = null,
) {
    init {
        require(bindingId.isNotBlank()) { "bindingId 不能为空" }
        require(detail == null || detail.cause == cause) { "细码不属于顶层错因" }
        require(confidence.isFinite() && confidence in 0.0..1.0) { "置信度必须在 0..1" }
        require(sourceObservationId.isNotBlank()) { "sourceObservationId 不能为空" }
        require(acceptedAtEpochMillis >= 0) { "接受时间不能为负" }
        if (state == ErrorCauseBindingState.ACTIVE) {
            require(activeKey == scope.activeKey()) { "ACTIVE binding 的 active_key 必须等于作用域活键" }
            require(endedAtEpochMillis == null) { "ACTIVE binding 不得带结束时间" }
            require(supersededByBindingId == null) { "ACTIVE binding 不得带替换指向" }
        } else {
            require(activeKey == null) { "非 ACTIVE binding 的 active_key 必须为 NULL" }
            require(endedAtEpochMillis != null) { "非 ACTIVE binding 必须有结束时间" }
        }
        if (state == ErrorCauseBindingState.SUPERSEDED) {
            require(!supersededByBindingId.isNullOrBlank()) { "SUPERSEDED binding 必须指向新 binding" }
        } else {
            require(supersededByBindingId == null) { "VOIDED binding 不得带替换指向" }
        }
        if (acceptanceSource == ErrorCauseAcceptanceSource.AUTO_GATE) {
            require(!gateVersion.isNullOrBlank()) { "自动落账必须带 gate_version" }
        }
    }
}

internal fun ErrorCauseObservationRecord.toEntity(): ErrorCauseObservationEntity =
    ErrorCauseObservationEntity(
        observationId = observationId,
        learnerId = scope.learnerId,
        practiceUnitId = scope.practiceUnitId,
        problemRevisionId = scope.problemRevisionId,
        knowledgeNodeId = scope.knowledgeNodeId,
        attemptId = attemptId,
        cause = cause.code,
        detailCode = detail?.code,
        confidence = confidence,
        channel = channel.name,
        extractorVersion = extractorVersion,
        taxonomyVersion = taxonomyVersion,
        rationaleMarkdown = rationaleMarkdown,
        evidenceRefsJson = ErrorCauseEvidenceRefsCodec.encode(evidenceRefs),
        observedAtEpochMillis = observedAtEpochMillis,
        status = status.name,
        decidedAtEpochMillis = decidedAtEpochMillis,
        decisionSource = decisionSource?.name,
        supersedesObservationId = supersedesObservationId,
    )

internal fun ErrorCauseObservationEntity.toRecord(): ErrorCauseObservationRecord =
    ErrorCauseObservationRecord(
        observationId = observationId,
        scope = ErrorCauseScope(
            learnerId = learnerId,
            practiceUnitId = practiceUnitId,
            problemRevisionId = problemRevisionId,
            knowledgeNodeId = knowledgeNodeId,
        ),
        attemptId = attemptId,
        cause = ErrorCause.requireFromCode(cause),
        detail = detailCode?.let { ErrorCauseDetail.fromCode(it) },
        confidence = confidence,
        channel = ErrorCauseChannel.valueOf(channel),
        extractorVersion = extractorVersion,
        taxonomyVersion = taxonomyVersion,
        rationaleMarkdown = rationaleMarkdown,
        evidenceRefs = ErrorCauseEvidenceRefsCodec.decode(evidenceRefsJson),
        observedAtEpochMillis = observedAtEpochMillis,
        status = ErrorCauseObservationStatus.valueOf(status),
        decidedAtEpochMillis = decidedAtEpochMillis,
        decisionSource = decisionSource?.let { ErrorCauseDecisionSource.valueOf(it) },
        supersedesObservationId = supersedesObservationId,
    )

internal fun ErrorCauseBindingRecord.toEntity(): ErrorCauseBindingEntity =
    ErrorCauseBindingEntity(
        bindingId = bindingId,
        learnerId = scope.learnerId,
        practiceUnitId = scope.practiceUnitId,
        problemRevisionId = scope.problemRevisionId,
        knowledgeNodeId = scope.knowledgeNodeId,
        cause = cause.code,
        detailCode = detail?.code,
        confidence = confidence,
        acceptanceSource = acceptanceSource.name,
        gateVersion = gateVersion,
        sourceObservationId = sourceObservationId,
        acceptedAtEpochMillis = acceptedAtEpochMillis,
        state = state.name,
        activeKey = activeKey,
        supersededByBindingId = supersededByBindingId,
        endedAtEpochMillis = endedAtEpochMillis,
    )

internal fun ErrorCauseBindingEntity.toRecord(): ErrorCauseBindingRecord =
    ErrorCauseBindingRecord(
        bindingId = bindingId,
        scope = ErrorCauseScope(
            learnerId = learnerId,
            practiceUnitId = practiceUnitId,
            problemRevisionId = problemRevisionId,
            knowledgeNodeId = knowledgeNodeId,
        ),
        cause = ErrorCause.requireFromCode(cause),
        detail = detailCode?.let { ErrorCauseDetail.fromCode(it) },
        confidence = confidence,
        acceptanceSource = ErrorCauseAcceptanceSource.valueOf(acceptanceSource),
        gateVersion = gateVersion,
        sourceObservationId = sourceObservationId,
        acceptedAtEpochMillis = acceptedAtEpochMillis,
        state = ErrorCauseBindingState.valueOf(state),
        activeKey = activeKey,
        supersededByBindingId = supersededByBindingId,
        endedAtEpochMillis = endedAtEpochMillis,
    )
