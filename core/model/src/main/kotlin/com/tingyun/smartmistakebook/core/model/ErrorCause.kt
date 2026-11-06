package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/**
 * 结构化错因 taxonomy v1（批次 40 / WS2 item 9）。
 *
 * 依据：`docs/research/2026-11-02-error-taxonomy.md` §1.2 的四类判据。四类**不是**按
 * "答案为什么错"做的穷尽枚举，而是按"学生自己产生的证据能区分到哪一步"做的保守分类：
 *
 * - [CONCEPTUAL]：学生表述的一般规则不成立，或已确认前置知识缺口；
 * - [PROCEDURAL]：规则成立、步骤执行错；
 * - [CARELESS]：历史会、本次单次失误（快 / 无提示 / 立即独立重做对），且**不晋级**为长期标签；
 * - [MISIDENTIFICATION]：方法与规则都对，只是读/抄题面错（纠正读题后立即正确）。
 *
 * **稳定码**：[code] 一旦发布永不复用、永不重编号；展示名只在 UI 映射层。
 * `E05` 预留给 IRIS 的 factual 类，v1 不启用（factual 目前折叠进 E01 的
 * [ErrorCauseDetail.FACTUAL_BASIC_FACT] 等细码）。启用 `E05` 必须同时改
 * [TAXONOMY_VERSION]，不能复用旧码。
 *
 * **同名冲突**：Elbrink 理论里的 "Misidentification" 是算法层（找错点/比较错函数值），
 * 本项目 [MISIDENTIFICATION] 是**感知/转录层**；算法层错误归 [PROCEDURAL] 的
 * [ErrorCauseDetail.PROC_ALGORITHM_SELECTION]。不写清这一点，标注者会把 E02 样本抽空。
 */
enum class ErrorCause(val code: String) {
    CONCEPTUAL("E01"),
    PROCEDURAL("E02"),
    CARELESS("E03"),
    MISIDENTIFICATION("E04"),
    ;

    companion object {
        /** 词表版本；与 `error_cause_observation.taxonomy_version` 同值。换词表必须换版本。 */
        const val TAXONOMY_VERSION: String = "error-cause-v1"

        /** 预留码，v1 不产生该值。 */
        const val RESERVED_FACTUAL_CODE: String = "E05"

        fun fromCode(code: String): ErrorCause? = entries.firstOrNull { it.code == code }

        fun requireFromCode(code: String): ErrorCause =
            fromCode(code) ?: throw IllegalArgumentException("未知错因稳定码：$code")
    }
}

/**
 * [ErrorCause] 之下的受控细码（批次 40 / WS2 item 9）。
 *
 * 顶层只保留四类；细码承载 IRIS factual 与 Elbrink procedural 的粒度，避免为了一个子类
 * 反复扩张顶层枚举。`detail_code` 可空：没有可核证据时**不猜**细码。
 *
 * 每个细码只属于一个顶层类（[cause]）；写入方必须校验两者兼容，否则同一条错因会在两个
 * 聚合口径下被读出不同含义。
 */
enum class ErrorCauseDetail(val cause: ErrorCause, val code: String) {
    // E01 CONCEPTUAL
    CONCEPT_RULE_INVALID(ErrorCause.CONCEPTUAL, "CONCEPT_RULE_INVALID"),
    CONCEPT_OVERGENERALIZED(ErrorCause.CONCEPTUAL, "CONCEPT_OVERGENERALIZED"),
    CONCEPT_PREREQUISITE_GAP(ErrorCause.CONCEPTUAL, "CONCEPT_PREREQUISITE_GAP"),
    FACTUAL_BASIC_FACT(ErrorCause.CONCEPTUAL, "FACTUAL_BASIC_FACT"),
    FACTUAL_VOCABULARY(ErrorCause.CONCEPTUAL, "FACTUAL_VOCABULARY"),
    FACTUAL_FORMULA(ErrorCause.CONCEPTUAL, "FACTUAL_FORMULA"),

    // E02 PROCEDURAL
    PROC_STEP_ORDER(ErrorCause.PROCEDURAL, "PROC_STEP_ORDER"),
    PROC_REGROUPING(ErrorCause.PROCEDURAL, "PROC_REGROUPING"),
    PROC_SIGN(ErrorCause.PROCEDURAL, "PROC_SIGN"),
    PROC_DECIMAL_ALIGN(ErrorCause.PROCEDURAL, "PROC_DECIMAL_ALIGN"),
    PROC_FRACTION_RULE(ErrorCause.PROCEDURAL, "PROC_FRACTION_RULE"),
    PROC_ALGORITHM_SELECTION(ErrorCause.PROCEDURAL, "PROC_ALGORITHM_SELECTION"),

    // E03 CARELESS
    SLIP_FAST_WRONG(ErrorCause.CARELESS, "SLIP_FAST_WRONG"),
    SLIP_INTERRUPTED(ErrorCause.CARELESS, "SLIP_INTERRUPTED"),
    SLIP_TRANSCRIPTION(ErrorCause.CARELESS, "SLIP_TRANSCRIPTION"),

    // E04 MISIDENTIFICATION（感知/转录层，非 Elbrink 的算法层）
    READ_SIGN(ErrorCause.MISIDENTIFICATION, "READ_SIGN"),
    READ_DIGIT(ErrorCause.MISIDENTIFICATION, "READ_DIGIT"),
    READ_CONDITION(ErrorCause.MISIDENTIFICATION, "READ_CONDITION"),
    READ_TARGET(ErrorCause.MISIDENTIFICATION, "READ_TARGET"),
    ;

    companion object {
        fun fromCode(code: String): ErrorCauseDetail? = entries.firstOrNull { it.code == code }

        fun forCause(cause: ErrorCause): List<ErrorCauseDetail> = entries.filter { it.cause == cause }
    }
}

/**
 * 证据通道。权重与限制见 R2 §3.6：
 * [TUTOR_TRACE] 只认学生 role 的轮次；[SELF_REPORT] 只能调置信、不能单独定类；
 * [MODEL] 是语义分类提案，必须过 D-1c 门。
 */
enum class ErrorCauseChannel {
    LOCAL_RULE,
    TUTOR_TRACE,
    SELF_REPORT,
    REDO,
    MODEL,
}

/** observation 的追加式状态机；只允许 `PROPOSED → ACCEPTED/REJECTED` 与 `ACCEPTED → RETRACTED/SUPERSEDED`。 */
enum class ErrorCauseObservationStatus {
    PROPOSED,
    ACCEPTED,
    REJECTED,
    RETRACTED,
    SUPERSEDED,
}

/** 谁做了这次状态迁移。`SYSTEM` 只用于"答案键被纠正 → 错因前提消失"这一条单向失效。 */
enum class ErrorCauseDecisionSource {
    AUTO_GATE,
    USER,
    SYSTEM,
}

/** binding 的现行状态；读侧只认 [ACTIVE]。 */
enum class ErrorCauseBindingState {
    ACTIVE,
    VOIDED,
    SUPERSEDED,
}

/** binding 的接受权威；与 `problem_classification_binding.acceptance_source` 同域但独立存储。 */
enum class ErrorCauseAcceptanceSource {
    AUTO_GATE,
    USER_CONFIRMED,
    USER_CORRECTED,
}

/**
 * 证据引用类型。**软引用**（不建 FK）：会话删除后允许悬空，读侧必须容错并标注
 * "证据已删除"，不得因悬空丢掉整条标签（标签的权威来源是 observation 行）。
 */
@Serializable
enum class ErrorCauseEvidenceKind {
    ATTEMPT,
    TUTOR_MESSAGE,
    REVIEW_LOG,
    REDO_ATTEMPT,
    INDEPENDENT_CORRECT,
    USER_CORRECTION,
}
