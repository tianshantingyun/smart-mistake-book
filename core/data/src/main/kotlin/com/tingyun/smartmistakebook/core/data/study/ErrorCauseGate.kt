package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseCandidate
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseNodeCertainty

/**
 * D-1c 错因自动落账门（批次 40 / WS2 item 15）。
 *
 * **它消灭的具体失败**：把 D-1（KC 语义绑定精确率 ≥0.95）直接当错因门，会让"KC 绑对了"
 * 冒充"错因判对了"——两者分母不同。错因必须单独认证（R2 §5.1）。本门默认
 * `certified = false`，即**只拦不放**：仓内错因标注样本为 0，P2–P5 无证据，开自动落账
 * 等于无证据放行。
 *
 * 门的要素：
 * - 阈值：`certified`（P0–P5 全部通过且未过期）与每类最低佐证数；
 * - 置信：`minConfidence`（P6，提案 0.80，`PENDING_CALIBRATION`）；
 * - 撤销：**不在门里**，是独立的状态机（`ErrorCauseRetractor`）。门只读
 *   `retractedRecently` 迟滞位，避免"用户纠正一次、系统立刻写回"的拉锯。
 *
 * 硬规则（不是可配项）：
 * - [ErrorCause.CARELESS] 永不自动落账（R2 §3.3 / P5）；
 * - `SELF_REPORT` 通道只能调置信、不能单独定类：即使候选已 certified、置信与佐证
 *   都达标，也永不自动落账（R2 §3.6）。当前 extractor 不出该通道，但门必须自己兜底，
 *   否则未来接模型/自评候选时会绕过这条硬规则；
 * - 挂了 `knowledge_node_id` 但归属不是 [ErrorCauseNodeCertainty.DIRECT] 时不落账（P7）；
 * - `taxonomyVersion` 不一致时不落账（换版重新认证，P8）。
 */
class ErrorCauseGate(
    private val state: ErrorCauseGateState = ErrorCauseGateState(),
) {
    fun mayAutoBook(
        candidate: ErrorCauseCandidate,
        corroborationCount: Int,
        retractedRecently: Boolean,
    ): Boolean {
        if (!state.certified) return false
        if (state.paused) return false
        if (state.taxonomyVersion != candidate.evidence.taxonomyVersion) return false
        if (candidate.evidence.cause == ErrorCause.CARELESS) return false
        // SELF_REPORT 只能调置信，不能单独定类（R2 §3.6）；门自己兜底，不依赖 extractor。
        if (candidate.evidence.channel == ErrorCauseChannel.SELF_REPORT) return false
        if (candidate.evidence.confidence < state.minConfidence) return false
        if (candidate.scope.knowledgeNodeId != null &&
            candidate.knowledgeNodeCertainty != ErrorCauseNodeCertainty.DIRECT
        ) {
            return false
        }
        if (retractedRecently) return false
        if (corroborationCount < state.minCorroboration) return false
        return true
    }

    /** 当前门是否为默认关闭态（生产装配断言/诊断用）。 */
    val isDefaultClosed: Boolean get() = !state.certified

    val gateVersion: String get() = state.gateVersion
}

/**
 * 门状态。
 *
 * [certified] 只能由 P0–P5 的证据链置 true；本批没有证据，所以默认 false。
 * [minCorroboration] 是提案值（E01/E02 需 2 条独立负事件，E04 需学生明说 + 纠正后对）；
 * 写入方按类传入实际佐证数，门只比较下界。
 */
data class ErrorCauseGateState(
    val certified: Boolean = false,
    val paused: Boolean = false,
    val taxonomyVersion: String = ErrorCause.TAXONOMY_VERSION,
    val gateVersion: String = D1C_GATE_VERSION,
    val minConfidence: Double = DEFAULT_MIN_CONFIDENCE,
    val minCorroboration: Int = DEFAULT_MIN_CORROBORATION,
) {
    init {
        require(taxonomyVersion.isNotBlank()) { "taxonomyVersion 不能为空" }
        require(gateVersion.isNotBlank()) { "gateVersion 不能为空" }
        require(minConfidence.isFinite() && minConfidence in 0.0..1.0) {
            "minConfidence 必须在 0..1"
        }
        require(minCorroboration >= 1) { "minCorroboration 至少为 1" }
    }
}

/** 提案值；不是文献结论，必须在有本项目语料后由 precision–coverage 曲线校准。 */
const val DEFAULT_MIN_CONFIDENCE: Double = 0.80
const val DEFAULT_MIN_CORROBORATION: Int = 2
const val D1C_GATE_VERSION: String = "d1c-v1"
