package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseCandidate
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidence
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseNodeCertainty
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope

/**
 * 错因候选提取器（批次 40 / WS2 item 14）。
 *
 * **只产候选，不落账**：本类是纯函数，构造时没有任何数据库/端口依赖；输出
 * [ErrorCauseCandidate]（状态恒为 `PROPOSED`）。要变成现行标签必须再过 D-1c 门
 * （`ErrorCauseGate`，默认关闭），见 R2 §5。
 *
 * **判定阶梯**（R2 §1.3，先排除再归类）：
 * 1. 题面在作答后被修订 → 直接 abstain（错可能不在学生）；
 * 2. 学生自己说出的规则不成立 → E01；
 * 3. 规则成立、某一步执行错 → E02；
 * 4. 方法与规则都对、只是读/抄错且纠正读题后正确 → E04；
 * 5. 没有正向 trace，但满足"会 + 无提示 + 立即独立重做对"三条件 → E03；
 * 6. 其余 abstain。
 *
 * **为什么没有 trace 时不能推 E01/E02/E04**：现有 `tutor_message` 只有
 * `role + body_markdown`，没有结构化学生步骤；助手侧文本是模型生成的，不是学生证据。
 * 无 trace 的语义分类只能走模型提案 + D-1c 门，v1 不在这里假装成确定性规则。
 *
 * **自评不是测量**：`selfReport` 只能给已有候选补一条引用/调置信，不能单独定类
 * （R2 §3.6）。
 */
class ErrorCauseExtractor(
    private val extractorVersion: String = LOCAL_EXTRACTOR_VERSION,
) {
    init {
        require(extractorVersion.isNotBlank()) { "extractorVersion 不能为空" }
    }

    fun extract(input: ErrorCauseExtractionInput): ErrorCauseCandidate? {
        if (input.revisionChangedAfterAttempt) return null

        val invalidRule = input.traceSignals
            .filterIsInstance<ErrorCauseTraceSignal.RuleClaim>()
            .firstOrNull { it.validity == StudentRuleValidity.INVALID }
        if (invalidRule != null) {
            val detail = if (invalidRule.overgeneralized) {
                ErrorCauseDetail.CONCEPT_OVERGENERALIZED
            } else {
                ErrorCauseDetail.CONCEPT_RULE_INVALID
            }
            return candidate(
                input = input,
                cause = ErrorCause.CONCEPTUAL,
                detail = detail,
                confidence = TRACE_CONCEPTUAL_CONFIDENCE,
                channel = ErrorCauseChannel.TUTOR_TRACE,
                refs = ruleRefs(input) + selfReportRef(input),
                rationale = "学生主动表述的一般规则在一般情况下不成立",
            )
        }

        val wrongStep = input.traceSignals
            .filterIsInstance<ErrorCauseTraceSignal.StepClaim>()
            .firstOrNull { it.ruleValidity == StudentRuleValidity.VALID && it.wrongStep != null }
        if (wrongStep != null) {
            return candidate(
                input = input,
                cause = ErrorCause.PROCEDURAL,
                detail = wrongStep.wrongStep!!.detail,
                confidence = TRACE_PROCEDURAL_CONFIDENCE,
                channel = ErrorCauseChannel.TUTOR_TRACE,
                refs = stepRefs(input) + selfReportRef(input),
                rationale = "学生调用的规则成立，但步骤执行出错",
            )
        }

        val readMismatch = input.traceSignals
            .filterIsInstance<ErrorCauseTraceSignal.ReadClaim>()
            .firstOrNull { it.correctedAfterReread }
        if (readMismatch != null) {
            return candidate(
                input = input,
                cause = ErrorCause.MISIDENTIFICATION,
                detail = readMismatch.kind.detail,
                confidence = READ_CONFIDENCE,
                channel = ErrorCauseChannel.TUTOR_TRACE,
                refs = readRefs(input) + selfReportRef(input),
                rationale = "方法与规则正确，纠正读题后立即正确",
            )
        }

        if (isCarelessTriple(input)) {
            val fast = isFastRelativeToBaseline(input)
            return candidate(
                input = input,
                cause = ErrorCause.CARELESS,
                detail = if (fast) ErrorCauseDetail.SLIP_FAST_WRONG else null,
                confidence = CARELESS_CONFIDENCE,
                channel = ErrorCauseChannel.REDO,
                refs = redoRefs(input) + selfReportRef(input),
                rationale = "历史独立正确、无提示、立即独立重做正确，本次为单次失误",
            )
        }

        return null
    }

    private fun candidate(
        input: ErrorCauseExtractionInput,
        cause: ErrorCause,
        detail: ErrorCauseDetail?,
        confidence: Double,
        channel: ErrorCauseChannel,
        refs: List<ErrorCauseEvidenceRef>,
        rationale: String,
    ): ErrorCauseCandidate = ErrorCauseCandidate(
        scope = input.scope,
        attemptId = input.attemptId,
        evidence = ErrorCauseEvidence(
            cause = cause,
            detail = detail,
            confidence = confidence,
            channel = channel,
            refs = refs.distinct(),
            observedAtEpochMillis = input.observedAtEpochMillis,
            rationaleMarkdown = rationale,
            taxonomyVersion = ErrorCause.TAXONOMY_VERSION,
        ),
        extractorVersion = extractorVersion,
        knowledgeNodeCertainty = input.knowledgeNodeCertainty,
    )

    /** 会 + 无提示 + 立即独立重做正确；三者齐才成立（R2 §1.2 E03）。 */
    private fun isCarelessTriple(input: ErrorCauseExtractionInput): Boolean {
        val redo = input.redo ?: return false
        return input.priorIndependentCorrectCount >= 1 &&
            input.hintCount == 0 &&
            !input.revealedBeforeAnswer &&
            redo.independent &&
            redo.correct &&
            redo.immediate
    }

    /**
     * 快 = 低于个人基线 p10，且基线样本 ≥30（R2 §3.4 / mastery-scheduling-spec:290）。
     * 冷启动期不得用 RT 阈值；此时 E03 仍可由其余三条件成立，但 detail 保持空。
     */
    private fun isFastRelativeToBaseline(input: ErrorCauseExtractionInput): Boolean {
        val baseline = input.personalBaselineP10Seconds ?: return false
        val duration = input.durationSeconds ?: return false
        return input.personalBaselineSampleCount >= MIN_BASELINE_SAMPLES && duration < baseline
    }

    private fun ruleRefs(input: ErrorCauseExtractionInput): List<ErrorCauseEvidenceRef> =
        input.traceSignals
            .filterIsInstance<ErrorCauseTraceSignal.RuleClaim>()
            .filter { it.validity == StudentRuleValidity.INVALID }
            .map { ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, it.messageId) }

    private fun stepRefs(input: ErrorCauseExtractionInput): List<ErrorCauseEvidenceRef> =
        input.traceSignals
            .filterIsInstance<ErrorCauseTraceSignal.StepClaim>()
            .filter { it.ruleValidity == StudentRuleValidity.VALID && it.wrongStep != null }
            .map { ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, it.messageId) }

    private fun readRefs(input: ErrorCauseExtractionInput): List<ErrorCauseEvidenceRef> =
        input.traceSignals
            .filterIsInstance<ErrorCauseTraceSignal.ReadClaim>()
            .filter { it.correctedAfterReread }
            .map { ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, it.messageId) }

    private fun redoRefs(input: ErrorCauseExtractionInput): List<ErrorCauseEvidenceRef> =
        input.redo?.let {
            listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.REDO_ATTEMPT, it.evidenceId))
        }.orEmpty()

    private fun selfReportRef(input: ErrorCauseExtractionInput): List<ErrorCauseEvidenceRef> =
        input.selfReport?.let {
            listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, it.messageId))
        }.orEmpty()

    companion object {
        /** 规则/提示词/模型的联合版本；换版必须换值（D-1c P8）。 */
        const val LOCAL_EXTRACTOR_VERSION: String = "error-cause-local-v1"

        const val MIN_BASELINE_SAMPLES: Int = 30

        const val TRACE_CONCEPTUAL_CONFIDENCE: Double = 0.85
        const val TRACE_PROCEDURAL_CONFIDENCE: Double = 0.80
        const val READ_CONFIDENCE: Double = 0.80
        const val CARELESS_CONFIDENCE: Double = 0.70
    }
}

enum class StudentRuleValidity {
    VALID,
    INVALID,
    UNKNOWN,
}

enum class StudentStepKind(val detail: ErrorCauseDetail) {
    STEP_ORDER(ErrorCauseDetail.PROC_STEP_ORDER),
    REGROUPING(ErrorCauseDetail.PROC_REGROUPING),
    SIGN(ErrorCauseDetail.PROC_SIGN),
    DECIMAL_ALIGN(ErrorCauseDetail.PROC_DECIMAL_ALIGN),
    FRACTION_RULE(ErrorCauseDetail.PROC_FRACTION_RULE),
    ALGORITHM_SELECTION(ErrorCauseDetail.PROC_ALGORITHM_SELECTION),
}

enum class StudentReadKind(val detail: ErrorCauseDetail) {
    SIGN(ErrorCauseDetail.READ_SIGN),
    DIGIT(ErrorCauseDetail.READ_DIGIT),
    CONDITION(ErrorCauseDetail.READ_CONDITION),
    TARGET(ErrorCauseDetail.READ_TARGET),
}

/**
 * 学生侧 trace 信号；每个信号必须带 [messageId] 以支持审计回读。
 *
 * 生产方必须先确认 `tutor_message.role = student`：助手侧文本是模型解释，不是学生证据。
 * `RuleClaim.validity` / `StepClaim.ruleValidity` 由模型分类提案给出，因此本提取器的 E01/E02
 * 只在 D-1c 门认证后才允许对模型提案自动落账。
 */
sealed interface ErrorCauseTraceSignal {
    val messageId: String

    data class RuleClaim(
        override val messageId: String,
        val validity: StudentRuleValidity,
        val overgeneralized: Boolean = false,
    ) : ErrorCauseTraceSignal

    data class StepClaim(
        override val messageId: String,
        val ruleValidity: StudentRuleValidity,
        val wrongStep: StudentStepKind?,
    ) : ErrorCauseTraceSignal

    data class ReadClaim(
        override val messageId: String,
        val kind: StudentReadKind,
        val correctedAfterReread: Boolean,
    ) : ErrorCauseTraceSignal
}

/** 立即独立重做；`immediate` 区别于隔天重做（后者可能只是记住答案）。 */
data class ErrorCauseRedoOutcome(
    val evidenceId: String,
    val independent: Boolean,
    val correct: Boolean,
    val immediate: Boolean,
    val observedAtEpochMillis: Long,
)

/** 学生自评；只能补引用，不能单独定类。 */
data class ErrorCauseSelfReport(
    val messageId: String,
    val expectedCorrect: Boolean,
)

/**
 * 提取器输入。所有计数都由调用方在同一快照里读好再传入——提取器不自己查库，
 * 这样它的输出只依赖输入，才可重放、可测、可审计。
 */
data class ErrorCauseExtractionInput(
    val scope: ErrorCauseScope,
    val attemptId: String?,
    val observedAtEpochMillis: Long,
    val traceSignals: List<ErrorCauseTraceSignal> = emptyList(),
    val selfReport: ErrorCauseSelfReport? = null,
    val redo: ErrorCauseRedoOutcome? = null,
    val priorIndependentCorrectCount: Int = 0,
    val hintCount: Int = 0,
    val revealedBeforeAnswer: Boolean = false,
    val durationSeconds: Int? = null,
    val personalBaselineP10Seconds: Int? = null,
    val personalBaselineSampleCount: Int = 0,
    val knowledgeNodeCertainty: ErrorCauseNodeCertainty = ErrorCauseNodeCertainty.UNKNOWN,
    /**
     * 题面在作答后被修订：该次作答对应的题面与当前修订不一致，错可能不在学生；
     * v1 取保守分支——整条 abstain，不产候选。
     */
    val revisionChangedAfterAttempt: Boolean = false,
) {
    init {
        require(observedAtEpochMillis >= 0) { "观察时间不能为负" }
        require(priorIndependentCorrectCount >= 0) { "历史独立正确次数不能为负" }
        require(hintCount >= 0) { "提示次数不能为负" }
        require(personalBaselineSampleCount >= 0) { "基线样本数不能为负" }
        require(durationSeconds == null || durationSeconds >= 0) { "用时不能为负" }
        require(personalBaselineP10Seconds == null || personalBaselineP10Seconds >= 0) {
            "基线 p10 不能为负"
        }
        traceSignals.forEach { signal ->
            require(signal.messageId.isNotBlank()) { "trace 信号必须带 messageId" }
        }
    }
}
