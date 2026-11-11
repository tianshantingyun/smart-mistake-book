package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.port.ErrorCauseBindingRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseObservationRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCausePort
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseWriteResult
import com.tingyun.smartmistakebook.core.model.ErrorCauseCandidate
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidence
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseNodeCertainty
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope

/**
 * 错题详情页的错因读写接缝（批次 50 / 5c）。
 *
 * **它消灭的具体失败**：详情页若自己拼装 `ErrorCauseRetractor` 的参数，会出现三种不可测的退化：
 * 1. 每次点击生成随机 `bindingId` → 重放时插第二条 `ACTIVE`（`active_key` 唯一索引报冲突，
 *    学生看到"确认失败"但账本已经有一条）；本类用**由 observationId 派生的稳定 bindingId**，
 *    同一候选重复确认是幂等的。
 * 2. 直接把"页面上任意一条候选"当确认对象 → 旧修订/别的练习单元的候选被挂到当前题上
 *    （R2 I7 要求新修订不自动继承旧错因）；本类先按当前 `scope` 校验，跨作用域一律拒绝且零写入。
 * 3. 把"确认"接成 `bookIfAllowed` → 人的选择被 D-1c 统计门挡掉，而门本轮未认证，
 *    确认按钮会永远失败；本类走 [ErrorCauseRetractor.confirmByUser]（人的选择是权威，
 *    来源 `USER_CONFIRMED`），**自动/模型落账仍只走 [ErrorCauseBookingService]**，门的关闭
 *    状态由 [autoBookingClosed] 如实暴露给 UI。
 *
 * **撤销不是删除**：委托 [ErrorCauseRetractor.retractByUser]，写 `RETRACTED` 撤回行 +
 * `ACTIVE→VOIDED`（端口单事务，不留半行）；读侧只认 `ACTIVE`，所以撤销后同一 scope 的
 * 自动落账还要等新的独立负事件（`retractedRecently` 迟滞，见 [ErrorCauseGate]）。
 *
 * **读不写**：`read` 只查 `ACTIVE` 与 `PROPOSED` 两类行，不改任何状态——候选升标签只能由
 * 用户确认或 D-1c 门触发。
 */
class ErrorCauseReviewService(
    private val port: ErrorCausePort,
    private val gate: ErrorCauseGate = ErrorCauseGate(),
) {
    private val retractor = ErrorCauseRetractor(port)

    /** 模型自动落账门是否仍关闭；UI 用它决定是否显示"模型不会自动写"的说明。 */
    val autoBookingClosed: Boolean get() = gate.isDefaultClosed

    /** 读取当前作用域的现行标签与候选。候选按 `scope` 过滤：旧修订的候选不进入本修订页面。 */
    suspend fun read(scope: ErrorCauseScope): ErrorCauseReviewSnapshot {
        val active = port.readActiveBinding(scope)
        val candidates = port.readProposedObservations(
            learnerId = scope.learnerId,
            practiceUnitId = scope.practiceUnitId,
        ).filter { record ->
            record.scope == scope &&
                record.status == ErrorCauseObservationStatus.PROPOSED &&
                record.observationId != active?.sourceObservationId
        }
        return ErrorCauseReviewSnapshot(
            active = active,
            candidates = candidates,
            autoBookingClosed = gate.isDefaultClosed,
        )
    }

    /**
     * 用户确认一条候选：写 `USER_CONFIRMED` 的 ACTIVE 标签。
     *
     * 只接受**当前作用域内仍为 PROPOSED** 的候选；否则返回对应结果且零写入。重复确认同一候选
     * 返回 [ErrorCauseReviewWriteResult.CANDIDATE_NOT_OPEN]（账本里它已是 ACCEPTED），不新建第二条。
     */
    suspend fun confirmCandidate(
        scope: ErrorCauseScope,
        observationId: String,
        decidedAtEpochMillis: Long,
    ): ErrorCauseReviewWriteResult {
        val record = findCandidate(scope, observationId)
            ?: return ErrorCauseReviewWriteResult.CANDIDATE_NOT_FOUND
        return when (
            retractor.confirmByUser(
                candidate = record.toCandidate(),
                bindingId = userBindingId(record.observationId),
                decidedAtEpochMillis = decidedAtEpochMillis,
            )
        ) {
            ErrorCauseWriteResult.APPLIED -> ErrorCauseReviewWriteResult.APPLIED
            ErrorCauseWriteResult.CONFLICT -> ErrorCauseReviewWriteResult.CONFLICT
            ErrorCauseWriteResult.NOT_FOUND -> ErrorCauseReviewWriteResult.NOT_FOUND
        }
    }

    /**
     * 用户撤销现行标签（"这不是我的错因"）：撤回行 + `VOIDED` 同一事务，历史保留。
     *
     * `observedAtEpochMillis`/`decidedAtEpochMillis` 由调用方给真实时钟；两者相等即"当场撤销"。
     * 同一 binding 重复撤销是幂等的（撤回行 id 由证据引用决定，端口按已有行判 APPLIED）。
     */
    suspend fun undoActiveTag(
        scope: ErrorCauseScope,
        bindingId: String,
        observedAtEpochMillis: Long,
        decidedAtEpochMillis: Long,
    ): ErrorCauseReviewWriteResult = when (
        retractor.retractByUser(
            scope = scope,
            bindingId = bindingId,
            reasonMarkdown = USER_RETRACTION_REASON,
            evidenceRefs = listOf(
                ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.USER_CORRECTION, bindingId),
            ),
            observedAtEpochMillis = observedAtEpochMillis,
            decidedAtEpochMillis = decidedAtEpochMillis,
            extractorVersion = USER_ACTION_EXTRACTOR_VERSION,
        )
    ) {
        ErrorCauseWriteResult.APPLIED -> ErrorCauseReviewWriteResult.APPLIED
        ErrorCauseWriteResult.CONFLICT -> ErrorCauseReviewWriteResult.CONFLICT
        ErrorCauseWriteResult.NOT_FOUND -> ErrorCauseReviewWriteResult.NOT_FOUND
    }

    private suspend fun findCandidate(
        scope: ErrorCauseScope,
        observationId: String,
    ): ErrorCauseObservationRecord? = port.readProposedObservations(
        learnerId = scope.learnerId,
        practiceUnitId = scope.practiceUnitId,
    ).firstOrNull { it.observationId == observationId && it.scope == scope }

    private fun ErrorCauseObservationRecord.toCandidate(): ErrorCauseCandidate = ErrorCauseCandidate(
        scope = scope,
        attemptId = attemptId,
        evidence = ErrorCauseEvidence(
            cause = cause,
            detail = detail,
            confidence = confidence,
            channel = channel,
            refs = evidenceRefs,
            observedAtEpochMillis = observedAtEpochMillis,
            rationaleMarkdown = rationaleMarkdown,
            taxonomyVersion = taxonomyVersion,
        ),
        extractorVersion = extractorVersion,
        // 挂了节点就说明当初只有 DIRECT 才允许写节点；题级标签保持 UNKNOWN。
        knowledgeNodeCertainty = if (scope.knowledgeNodeId == null) {
            ErrorCauseNodeCertainty.UNKNOWN
        } else {
            ErrorCauseNodeCertainty.DIRECT
        },
    )

    companion object {
        /** 用户确认派生 binding id：同一 observation 重复确认落到同一行（幂等）。 */
        fun userBindingId(observationId: String): String = "ecuser-$observationId"

        /** 用户动作不是提取器产物；但端口要求非空版本串，便于审计区分。 */
        const val USER_ACTION_EXTRACTOR_VERSION: String = "error-cause-user-review-v1"

        const val USER_RETRACTION_REASON: String = "学生表示这条错因不成立"
    }
}

/** 一次读取的两侧：现行标签（至多一条）与候选（只读）。 */
data class ErrorCauseReviewSnapshot(
    val active: ErrorCauseBindingRecord?,
    val candidates: List<ErrorCauseObservationRecord>,
    val autoBookingClosed: Boolean,
)

/** 页面可区分的写结果；不折叠成一个 boolean。 */
enum class ErrorCauseReviewWriteResult {
    /** 已写入：确认→ACCEPTED+ACTIVE；撤销→RETRACTED+VOIDED。 */
    APPLIED,

    /** 账本现状与请求不一致（同 scope 已有 ACTIVE、重放证据不一致等）。 */
    CONFLICT,

    /** 作用域内没有这条候选（或它在别的修订/练习单元下，或已不是开放候选）。 */
    CANDIDATE_NOT_FOUND,

    /** 撤销时该作用域已没有现行标签。 */
    NOT_FOUND,
}
