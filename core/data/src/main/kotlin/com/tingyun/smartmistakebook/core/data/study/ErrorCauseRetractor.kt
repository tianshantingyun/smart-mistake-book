package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.port.ErrorCauseBindingRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseObservationRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCausePort
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseWriteResult
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseBindingState
import com.tingyun.smartmistakebook.core.model.ErrorCauseCandidate
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDecisionSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseIdentity
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope

/**
 * 错因标签链的落账/撤回/改判（批次 40 / WS2 items 15/16）。
 *
 * **它消灭的头号失败**（R2 §0/§6.2）：把错因撤回实现成 `attempt_correction VOID`。
 * 那样做会把一次真实答错从掌握证据里静默抹掉，学生可以通过"改错因"把答错洗成答对。
 * 本类只依赖 [ErrorCausePort]（标签层），没有 attempt/review/projection 端口；
 * 作答事实、掌握度投影一行都不改。
 *
 * **不留半行**：binding 的状态迁移（observation→ACCEPTED + binding 新建/结束）委托给端口的
 * 单事务方法；本类只做 ① 门判定（默认关闭）② 构造记录 ③ 校验新旧绑定同作用域。
 * 新 observation 先以 `PROPOSED` 追加，是**合法的候选**（可以独立存在）；即使随后的
 * binding 事务失败/进程死亡，留下的也只是一条候选，不是"有 ACTIVE 无来源"的半行。
 *
 * **撤销不是删除**：撤回写一条 `RETRACTED` observation（`supersedes_observation_id`
 * 指向原证据）并把 binding 置 `VOIDED`；改判写 `SUPERSEDED` + 新 `ACTIVE`。
 */
class ErrorCauseRetractor(
    private val port: ErrorCausePort,
) {

    /**
     * 自动落账：只有 [ErrorCauseGate.mayAutoBook] 通过才写。门默认关闭，所以生产路径
     * 在认证前恒返回 [ErrorCauseWriteResult.CONFLICT] 且不产生任何行。
     */
    suspend fun bookIfAllowed(
        candidate: ErrorCauseCandidate,
        gate: ErrorCauseGate,
        corroborationCount: Int,
        retractedRecently: Boolean,
        bindingId: String,
        decidedAtEpochMillis: Long,
    ): ErrorCauseWriteResult {
        if (!gate.mayAutoBook(candidate, corroborationCount, retractedRecently)) {
            return ErrorCauseWriteResult.CONFLICT
        }
        val observation = candidate.toObservationRecord()
        port.appendObservation(observation)
        val binding = ErrorCauseBindingRecord(
            bindingId = bindingId,
            scope = candidate.scope,
            cause = candidate.evidence.cause,
            detail = candidate.evidence.detail,
            confidence = candidate.evidence.confidence,
            acceptanceSource = ErrorCauseAcceptanceSource.AUTO_GATE,
            gateVersion = gate.gateVersion,
            sourceObservationId = observation.observationId,
            acceptedAtEpochMillis = decidedAtEpochMillis,
            state = ErrorCauseBindingState.ACTIVE,
            activeKey = candidate.scope.activeKey(),
        )
        return port.acceptObservationAndActivateBinding(
            binding = binding,
            decidedAtEpochMillis = decidedAtEpochMillis,
            decisionSource = ErrorCauseDecisionSource.AUTO_GATE,
        )
    }

    /**
     * 用户显式确认：绕过 D-1c 的统计门（人的选择是权威），但走同一条事务边界。
     * CARELESS 允许用户确认（R2 §3.3 唯一例外），是否带 90 天复核期由调用方/产品决定。
     */
    suspend fun confirmByUser(
        candidate: ErrorCauseCandidate,
        bindingId: String,
        decidedAtEpochMillis: Long,
    ): ErrorCauseWriteResult {
        val observation = candidate.toObservationRecord()
        port.appendObservation(observation)
        val binding = ErrorCauseBindingRecord(
            bindingId = bindingId,
            scope = candidate.scope,
            cause = candidate.evidence.cause,
            detail = candidate.evidence.detail,
            confidence = candidate.evidence.confidence,
            acceptanceSource = ErrorCauseAcceptanceSource.USER_CONFIRMED,
            gateVersion = null,
            sourceObservationId = observation.observationId,
            acceptedAtEpochMillis = decidedAtEpochMillis,
            state = ErrorCauseBindingState.ACTIVE,
            activeKey = candidate.scope.activeKey(),
        )
        return port.acceptObservationAndActivateBinding(
            binding = binding,
            decidedAtEpochMillis = decidedAtEpochMillis,
            decisionSource = ErrorCauseDecisionSource.USER,
        )
    }

    /**
     * 用户撤回（"这不是我的错因"）：写 RETRACTED observation + ACTIVE→VOIDED。
     *
     * 读侧只认 ACTIVE，所以撤回后同一 scope 的自动落账必须等新的独立负事件
     * （`retractedRecently` 迟滞，见 [ErrorCauseGate]）。
     */
    suspend fun retractByUser(
        scope: ErrorCauseScope,
        bindingId: String,
        reasonMarkdown: String,
        evidenceRefs: List<ErrorCauseEvidenceRef>,
        observedAtEpochMillis: Long,
        decidedAtEpochMillis: Long,
        extractorVersion: String,
    ): ErrorCauseWriteResult = retract(
        scope = scope,
        bindingId = bindingId,
        reasonMarkdown = reasonMarkdown,
        evidenceRefs = evidenceRefs,
        observedAtEpochMillis = observedAtEpochMillis,
        decidedAtEpochMillis = decidedAtEpochMillis,
        extractorVersion = extractorVersion,
        decisionSource = ErrorCauseDecisionSource.USER,
    )

    /**
     * 答案键被纠正为"学生其实答对"：唯一允许的系统自动撤回场景（R2 §6.4）。
     * 反向不成立：本方法不触发任何 `attempt_correction` / 掌握度重放。
     */
    suspend fun retractForAnswerKeyCorrection(
        scope: ErrorCauseScope,
        bindingId: String,
        reasonMarkdown: String,
        evidenceRefs: List<ErrorCauseEvidenceRef>,
        observedAtEpochMillis: Long,
        decidedAtEpochMillis: Long,
        extractorVersion: String,
    ): ErrorCauseWriteResult = retract(
        scope = scope,
        bindingId = bindingId,
        reasonMarkdown = reasonMarkdown,
        evidenceRefs = evidenceRefs,
        observedAtEpochMillis = observedAtEpochMillis,
        decidedAtEpochMillis = decidedAtEpochMillis,
        extractorVersion = extractorVersion,
        decisionSource = ErrorCauseDecisionSource.SYSTEM,
    )

    /**
     * 用户改判：新 observation 落库（PROPOSED），旧 binding SUPERSEDED，新 binding ACTIVE。
     * 新旧必须在同一作用域（端口事务里还会再校验 active_key 相等）。
     */
    suspend fun supersedeByUser(
        scope: ErrorCauseScope,
        oldBindingId: String,
        newObservation: ErrorCauseObservationRecord,
        newBinding: ErrorCauseBindingRecord,
        decidedAtEpochMillis: Long,
    ): ErrorCauseWriteResult {
        require(newBinding.scope == scope) { "新 binding 必须与原作用域相同" }
        require(newObservation.scope == scope) { "新 observation 必须与原作用域相同" }
        require(newBinding.sourceObservationId == newObservation.observationId) {
            "新 binding 必须指向新 observation"
        }
        require(newObservation.status == ErrorCauseObservationStatus.PROPOSED) {
            "改判的新 observation 必须以 PROPOSED 落库，由端口在同一事务里置 ACCEPTED"
        }
        val active = port.readActiveBinding(scope) ?: return ErrorCauseWriteResult.NOT_FOUND
        if (active.bindingId != oldBindingId) return ErrorCauseWriteResult.CONFLICT
        port.appendObservation(newObservation)
        return port.supersedeActiveBinding(
            newObservation = newObservation,
            newBinding = newBinding,
            oldBindingId = oldBindingId,
            decidedAtEpochMillis = decidedAtEpochMillis,
            decisionSource = ErrorCauseDecisionSource.USER,
        )
    }

    private suspend fun retract(
        scope: ErrorCauseScope,
        bindingId: String,
        reasonMarkdown: String,
        evidenceRefs: List<ErrorCauseEvidenceRef>,
        observedAtEpochMillis: Long,
        decidedAtEpochMillis: Long,
        extractorVersion: String,
        decisionSource: ErrorCauseDecisionSource,
    ): ErrorCauseWriteResult {
        require(decisionSource != ErrorCauseDecisionSource.AUTO_GATE) {
            "自动落账门不得撤回标签；撤回只有 USER/SYSTEM 两个来源"
        }
        require(reasonMarkdown.isNotBlank()) { "撤回理由不能为空" }
        require(evidenceRefs.isNotEmpty()) { "撤回必须带证据引用" }
        require(decidedAtEpochMillis >= observedAtEpochMillis) { "撤回时间不能早于观察时间" }
        require(extractorVersion.isNotBlank()) { "extractorVersion 不能为空" }

        val active = port.readActiveBinding(scope) ?: return ErrorCauseWriteResult.NOT_FOUND
        if (active.bindingId != bindingId) return ErrorCauseWriteResult.CONFLICT

        // 用户撤回是自评通道；答案键纠正触发的系统撤回是本地确定性事实，用 LOCAL_RULE。
        val channel = if (decisionSource == ErrorCauseDecisionSource.SYSTEM) {
            ErrorCauseChannel.LOCAL_RULE
        } else {
            ErrorCauseChannel.SELF_REPORT
        }
        val observation = ErrorCauseObservationRecord(
            observationId = ErrorCauseIdentity.observationId(
                scope = scope,
                attemptId = null,
                cause = active.cause,
                detail = active.detail,
                channel = channel,
                extractorVersion = extractorVersion,
                refs = evidenceRefs,
                supersedesObservationId = active.sourceObservationId,
            ),
            scope = scope,
            attemptId = null,
            cause = active.cause,
            detail = active.detail,
            confidence = active.confidence,
            channel = channel,
            extractorVersion = extractorVersion,
            taxonomyVersion = ErrorCause.TAXONOMY_VERSION,
            rationaleMarkdown = reasonMarkdown,
            evidenceRefs = evidenceRefs,
            observedAtEpochMillis = observedAtEpochMillis,
            status = ErrorCauseObservationStatus.RETRACTED,
            decidedAtEpochMillis = decidedAtEpochMillis,
            decisionSource = decisionSource,
            supersedesObservationId = active.sourceObservationId,
        )
        return port.retractActiveBinding(
            retraction = observation,
            bindingId = bindingId,
            endedAtEpochMillis = decidedAtEpochMillis,
        )
    }

    private fun ErrorCauseCandidate.toObservationRecord(): ErrorCauseObservationRecord =
        ErrorCauseObservationRecord(
            observationId = ErrorCauseIdentity.observationId(this),
            scope = scope,
            attemptId = attemptId,
            cause = evidence.cause,
            detail = evidence.detail,
            confidence = evidence.confidence,
            channel = evidence.channel,
            extractorVersion = extractorVersion,
            taxonomyVersion = evidence.taxonomyVersion,
            rationaleMarkdown = evidence.rationaleMarkdown,
            evidenceRefs = evidence.refs,
            observedAtEpochMillis = evidence.observedAtEpochMillis,
            status = ErrorCauseObservationStatus.PROPOSED,
        )
}
