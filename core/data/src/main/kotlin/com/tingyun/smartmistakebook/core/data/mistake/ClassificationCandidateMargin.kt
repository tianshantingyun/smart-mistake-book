package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.database.ProblemClassificationCandidateRecord

/**
 * K5-B：`normalized_margin` 的「极小 margin ⇒ 存疑」阈值——**可调策略参数**，不是外部验证过的常数。
 *
 * 语义与出处（研究文档 `docs/research/2026-10-11-r1-r2-label-accuracy-research.md` §1.3 机制 ②）：
 * cleanlab 的 `normalized_margin = p(选中) − max(p(其它候选))` 是**排序分**——官方只保证
 * "输出排序过的疑似标签问题"，**不给**一个"多少算可疑"的固定阈值（[cleanlab filter 文档]）。
 * 我们要的是一个**离散**的「存疑」标记，因此必须显式声明一个阈值：
 *
 * - 取 `0.02` = 保守起点：只把"几乎并列"（含并列，margin = 0 ≤ 0.02）的已接受标签标出来；
 * - **未验证**：本阈值**没有**用我们的数据校准过（截至本批，真实样本为 0；见
 *   `ClassificationCalibrationAuditTest` 的"无样本"口径）。它是**策略**，随审计结论可调，
 *   调整时只改这一个常量——派生结果与审计报告同源；
 * - 与 cleanlab 一致：**只派生、只呈现**。绝不据此改写、删除或降信任何已接受标签——
 *   外部两条反向证据（自纠会自我强化错误；无外部反馈的自纠可使表现变差）明确否掉静默自纠。
 */
internal const val SUSPECT_NORMALIZED_MARGIN_THRESHOLD = 0.02

/**
 * K5-B：阈值比较的**浮点噪声容差**（不是策略旋钮，别拿它调参）。
 *
 * 消灭的具体失败：`0.92 − 0.90 = 0.020000000000000018`（IEEE-754 二进制表示）——严格
 * `margin <= 0.02` 会把它判成"不存疑"，而它在语义上就是"差 0.02"。加 `1e-9` 只吃掉这种
 * 表示噪声；真正的策略边界仍是 [SUSPECT_NORMALIZED_MARGIN_THRESHOLD]。
 */
internal const val SUSPECT_MARGIN_FLOAT_TOLERANCE = 1e-9

/**
 * K5-B：一条**已接受**候选的 margin 派生结果（只呈现，不改写任何行）。
 *
 * - [margin]：cleanlab `normalized_margin` 语义（[normalizedMargin]）；`null` = **不可判**
 *   （同维度没有其它候选——没有对手就没有竞争关系可言，这是一等输出，不是 0）；
 * - [suspect]：`margin != null && margin <= SUSPECT_NORMALIZED_MARGIN_THRESHOLD +
 *   SUSPECT_MARGIN_FLOAT_TOLERANCE`（容差只吃浮点表示噪声，不是第二个阈值）；
 *   `margin == null` 时恒为 `false`（不可判 ≠ 清白，只是没有信号——调用方必须按
 *   `margin == null` 区分两者，不得读成"已验证正确"）。
 */
internal data class AcceptedLabelMarginSignal(
    val candidateId: String,
    /** 维度原文（`ClassificationDimension.name`）；解析失败的行不在这里被悄悄改写成别的维度。 */
    val dimension: String,
    val labelId: String,
    val displayName: String,
    val confidence: Double,
    val margin: Double?,
    val suspect: Boolean,
)

/**
 * cleanlab `normalized_margin` 的纯函数实现：`p(选中) − max(p(其它候选同维度))`。
 *
 * [competingConfidences] = 同维度**其它**候选的分数（含被拒候选——被拒候选恰恰是最该算进来的
 * 对手：它没过门不代表它比选中项差）；为空（仅一个候选）⇒ 返回 `null` = **不可判**，
 * 不是 0，也不是"最差"。
 */
internal fun normalizedMargin(
    selectedConfidence: Double,
    competingConfidences: List<Double>,
): Double? = competingConfidences.maxOrNull()?.let { strongestCompetitor ->
    selectedConfidence - strongestCompetitor
}

/**
 * K5-B：从落库候选集派生**已接受标签**的 margin 与「存疑」信号（纯函数，零副作用）。
 *
 * 分组口径 = 一轮一维：`(problemId, basisRevisionId, acceptedAtEpochMillis, dimension)`——
 * 同一轮同一维度的全部候选互为对手（含被拒候选）。同一轮同一维度可以有**多个**已接受标签
 * （章节 3 / 知识点 8 的预算），此时每个已接受标签各算各的 margin；排名低于同轮其它已接受
 * 候选的标签会得到**负 margin** ⇒ 自动落入「存疑」——这正是要看见的形态（它被接受了，
 * 但同轮有别的候选分更高）。
 *
 * 只返回 `accepted = true` 的候选（被拒候选的分数分布由审计的"选择偏差"段单独呈现）。
 * **本函数不写库、不改任何标签**；结果只供呈现/审计。
 */
internal fun acceptedLabelMarginSignals(
    candidates: List<ProblemClassificationCandidateRecord>,
    threshold: Double = SUSPECT_NORMALIZED_MARGIN_THRESHOLD,
): List<AcceptedLabelMarginSignal> {
    require(threshold.isFinite() && threshold >= 0.0) { "threshold must be a non-negative number" }
    val byRound = candidates.groupBy { candidate ->
        CandidateRoundKey(
            problemId = candidate.problemId,
            basisRevisionId = candidate.basisRevisionId,
            acceptedAtEpochMillis = candidate.acceptedAtEpochMillis,
            dimension = candidate.dimension,
        )
    }
    return byRound.values.flatMap { roundCandidates ->
        roundCandidates.filter { candidate -> candidate.accepted }.map { accepted ->
            val competitors = roundCandidates
                .filterNot { candidate -> candidate.candidateId == accepted.candidateId }
                .map(ProblemClassificationCandidateRecord::confidence)
            val margin = normalizedMargin(accepted.confidence, competitors)
            AcceptedLabelMarginSignal(
                candidateId = accepted.candidateId,
                dimension = accepted.dimension,
                labelId = accepted.labelId,
                displayName = accepted.displayName,
                confidence = accepted.confidence,
                margin = margin,
                suspect = margin != null &&
                    margin <= threshold + SUSPECT_MARGIN_FLOAT_TOLERANCE,
            )
        }
    }.sortedWith(
        compareBy<AcceptedLabelMarginSignal> { it.dimension }
            .thenByDescending { it.confidence }
            .thenBy { it.labelId },
    )
}

/** 一轮一维：margin 的对手集范围（同题、同修订、同轮、同维度）。 */
private data class CandidateRoundKey(
    val problemId: String,
    val basisRevisionId: String,
    val acceptedAtEpochMillis: Long,
    val dimension: String,
)
