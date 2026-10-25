package com.tingyun.smartmistakebook.core.domain

/**
 * AUD-17「合格长程样本台账」——只读分类视图（批次 20 / T9，Wave X 准备件）。
 *
 * 口径原文（`docs/research/2026-09-28-algorithm-fix-plan.md:493`，AUD-17 §）：
 * 「按SchedulingEvaluation实际筛选规则输出**原始数、MODEL_JUDGED/REVEAL排除、首条/同日重复、
 * 缺时间/缺状态、最终可预测长程数**；400门只看最后一列。」分列要求另见
 * `docs/REFACTOR-MASTER-LINE.md:78,150`（原始/排除/可预测数分列；400 原始记录不等于达标）与
 * `docs/research/2026-10-11-new-stages-spec.md:194`（不把 MODEL_JUDGED 凑入 400 样本门）。
 *
 * 与实现同口径的锚点（逐条复用 `SchedulingEvaluation.kt` 的判据，不另立标准）：
 * - **撤销/观察行**：`scheduling_eligible=0` 先于一切消费排除（PRD-05，与
 *   `ReviewLogSink.eligibleRows` 同口径——撤销的 attempt 行保留供审计但移出有效证据集合）；
 * - **MODEL_JUDGED / REVEAL 排除**：`fittableReviewSamples`（模型判词 κ≈0.70 判定噪声、
 *   看答案行不是回忆尝试，均不参与拟合，台账同列排除）；
 * - **首条 / 同日重复**：`FsrsParameterOptimizer.predictableSampleCount` 的逐卡判定——
 *   **先过滤、后按卡分组**（排除行不占"首条"、不推进前条），首行只播种状态，其后每行按
 *   `state==Learning`（权威）或 `elapsed<1` 墙钟启发式（旧行）判同日；间隔一律走
 *   `ReviewSample.elapsedDaysSince`（采集日差优先、墙钟地板回退）。
 *
 * 边界（已裁，勿越）：
 * - **只读**：不写库、不失效任何缓存、不触碰投影/排程，无启用开关；
 * - 400 门只**引用**常量（[LongRangeSampleLedgerReport.gateFloor] 默认 =
 *   [FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING]），不做达标判定、不写任何启用路径
 *   （AUD-17：达 400 才执行 Wave X，由人裁）；
 * - 「未核验键」不在本视图分类里：review_log 行不携带答案键核验状态，可落 LOCAL_CHECKED
 *   档的写入路径以键可信为前提（台账裁决 18 / G8 `PaperAnswerRecycleJudge`：只有
 *   VERIFIED / USER_ASSERTED 的键才判对错）；键真值分层属 AUD-11/PRD-02 的证据面，
 *   不在本数据面内。缺口如实登记，不在台账里虚构一档。
 */
data class LongRangeSampleCandidate(
    val practiceUnitId: String,
    val reviewedAtEpochMillis: Long,
    /** 落库评分（1..4，`StoredReviewRating` 基址）——明细审计与未来重放回读所需。 */
    val rating: Int,
    /** ATTEMPT / LOCAL_CHECKED / MODEL_JUDGED / REVEAL / SELF_REPORT / VISUAL（spec §2.5）。 */
    val sourceKind: String,
    /** null=迁移前旧行（缺时间）：重放走墙钟地板回退。 */
    val deltaTDays: Double? = null,
    /** null=迁移前旧行（缺状态）：同日判定走 elapsed<1 启发式。 */
    val state: Int? = null,
    /** false=撤销/冷却观察行（PRD-05）：保留在台账原始面里分类，不参与拟合口径。 */
    val schedulingEligible: Boolean = true,
)

/**
 * 台账分桶（一行一桶，恰分原始行）：前三个是排除原因，中间两个是"不产生长程预测对"的
 * 结构性原因，最后一个是 400 门唯一计数的终态。
 */
enum class LongRangeSampleClassification {
    /** 撤销/冷却观察行（`scheduling_eligible=0`，spec 2.7 / PRD-05）。 */
    REVOKED_OR_OBSERVED,

    /** 模型判词行（开放作答 κ≈0.70，拟合排除，台账裁决 18）。 */
    MODEL_JUDGED_EXCLUDED,

    /** 看答案行（rating=AGAIN 但不是回忆尝试，拟合排除，W1-4/KF-03）。 */
    REVEAL_EXCLUDED,

    /** 卡的首条（只播种状态，不产生预测对）。 */
    FIRST_OF_CARD,

    /** 同日重复（state=Learning 权威 / 旧行 elapsed<1 启发式；无长程信号，spec §2.15）。 */
    SAME_DAY_REPEAT,

    /** 合格长程样本：400 门只数这一桶（AUD-17）。 */
    PREDICTABLE_LONG_RANGE,
}

/** 明细行：候选样本 + 它落入的分桶。 */
data class LongRangeSampleLedgerEntry(
    val candidate: LongRangeSampleCandidate,
    val classification: LongRangeSampleClassification,
)

/**
 * 台账报告：AUD-17 各列全部由 [entries] **派生**（单一事实源，聚合一处算错/算漏的漂移
 * 在结构上不可达）；[gateFloor] 是唯一存字段——只引用常量供消费方对照，不构成达标判定。
 */
class LongRangeSampleLedgerReport(
    /** 明细（按复习时间稳定排序，与 review_log 读序一致）。 */
    val entries: List<LongRangeSampleLedgerEntry>,
    /** 400 门只看可预测长程数这一列；本字段仅引用常量，无达标布尔、无启用路径。 */
    val gateFloor: Int = FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING,
) {
    /** 原始数：读到的全部行（含撤销/观察行）。 */
    val rawRowCount: Int get() = entries.size

    /** 撤销/冷却观察行数。 */
    val revokedOrObservedCount: Int get() = countOf(LongRangeSampleClassification.REVOKED_OR_OBSERVED)

    /** MODEL_JUDGED 排除数。 */
    val modelJudgedExcludedCount: Int get() = countOf(LongRangeSampleClassification.MODEL_JUDGED_EXCLUDED)

    /** REVEAL 排除数。 */
    val revealExcludedCount: Int get() = countOf(LongRangeSampleClassification.REVEAL_EXCLUDED)

    /** 首条数（不产生预测对）。 */
    val firstOfCardCount: Int get() = countOf(LongRangeSampleClassification.FIRST_OF_CARD)

    /** 同日重复数（不产生长程信号）。 */
    val sameDayRepeatCount: Int get() = countOf(LongRangeSampleClassification.SAME_DAY_REPEAT)

    /** 最终可预测长程数——400 门只看这一列（AUD-17）。 */
    val predictableLongRangeCount: Int
        get() = countOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE)

    /**
     * 缺时间（delta_t 为空的迁移前旧行）——只统计**最终可预测集合**：这些行仍被现行规则
     * 计入（重放走墙钟地板回退），此列暴露其中多少行的时间语义依赖回退而非采集值。
     */
    val predictableMissingDeltaTCount: Int
        get() = entriesOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE)
            .count { it.candidate.deltaTDays == null }

    /** 缺状态（state 为空的迁移前旧行）——同上，只统计最终可预测集合（同日判定走启发式）。 */
    val predictableMissingStateCount: Int
        get() = entriesOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE)
            .count { it.candidate.state == null }

    /** 明细查询：按分桶取行。 */
    fun entriesOf(kind: LongRangeSampleClassification): List<LongRangeSampleLedgerEntry> =
        entries.filter { it.classification == kind }

    fun countOf(kind: LongRangeSampleClassification): Int = entriesOf(kind).size
}

/**
 * 分类器（纯函数）：把候选行按 AUD-17 口径逐行分桶。
 *
 * 消灭的失败：AUD-17 各列此前只能靠人读 `predictableSampleCount` 的实现反推，没有任何
 * 机械面把"原始数→排除→首条/同日→可预测长程数"的漏斗按行摊开；Wave X 校准前的样本
 * 盘点也无从对账。分类判据逐字复用既有实现（见文件头），并由
 * `LongRangeSampleLedgerTest` 的口径等价用例钉住不漂移。
 */
object LongRangeSampleLedger {

    fun classify(candidates: List<LongRangeSampleCandidate>): LongRangeSampleLedgerReport {
        val classified = mutableListOf<LongRangeSampleLedgerEntry>()
        // 与 predictableSampleCount 相同的顺序：先按行排除（撤销/观察 → MODEL_JUDGED →
        // REVEAL），其余按卡缓冲后再判首条/同日——排除行不占"首条"、不推进前条。
        val fittableByCard = LinkedHashMap<String, MutableList<Pair<LongRangeSampleCandidate, ReviewSample>>>()
        for (candidate in candidates) {
            val excluded = when {
                !candidate.schedulingEligible -> LongRangeSampleClassification.REVOKED_OR_OBSERVED
                candidate.sourceKind == ReviewSample.MODEL_JUDGED_KIND ->
                    LongRangeSampleClassification.MODEL_JUDGED_EXCLUDED
                candidate.sourceKind == ReviewSample.REVEAL_KIND ->
                    LongRangeSampleClassification.REVEAL_EXCLUDED
                else -> null
            }
            if (excluded != null) {
                classified += LongRangeSampleLedgerEntry(candidate, excluded)
            } else {
                fittableByCard.getOrPut(candidate.practiceUnitId) { mutableListOf() }
                    .add(candidate to candidate.toReplaySample())
            }
        }
        fittableByCard.values.forEach { cardRows ->
            val ordered = cardRows.sortedBy { (candidate, _) -> candidate.reviewedAtEpochMillis }
            var lastReviewedAt = ordered.first().first.reviewedAtEpochMillis
            ordered.forEachIndexed { index, (candidate, sample) ->
                val classification = if (index == 0) {
                    LongRangeSampleClassification.FIRST_OF_CARD
                } else {
                    // 与 predictableSampleCount 逐字同口径：state 权威（Learning=同日），
                    // 旧行（state=null）回落 elapsed<1 墙钟启发式；elapsed 一律走
                    // elapsedDaysSince（采集日差优先、墙钟地板回退）。
                    val elapsedDays = sample.elapsedDaysSince(lastReviewedAt)
                    val sameDayRepeat = candidate.state
                        ?.let { it == ReviewSample.STATE_LEARNING }
                        ?: (elapsedDays < 1.0)
                    if (sameDayRepeat) {
                        LongRangeSampleClassification.SAME_DAY_REPEAT
                    } else {
                        LongRangeSampleClassification.PREDICTABLE_LONG_RANGE
                    }
                }
                classified += LongRangeSampleLedgerEntry(candidate, classification)
                // 与 predictableSampleCount 一致：每一行（含同日重复）都推进前条时间戳。
                lastReviewedAt = candidate.reviewedAtEpochMillis
            }
        }
        return LongRangeSampleLedgerReport(
            entries = classified.sortedBy { it.candidate.reviewedAtEpochMillis },
        )
    }

    /** 与 `ReviewLogSink.reviewSamples` 同构的行→重放样本映射（间隔/状态判据的共享单源）。 */
    private fun LongRangeSampleCandidate.toReplaySample(): ReviewSample = ReviewSample(
        practiceUnitId = practiceUnitId,
        reviewedAtEpochMillis = reviewedAtEpochMillis,
        rating = FsrsRating.fromStored(rating),
        sourceKind = sourceKind,
        deltaTDays = deltaTDays,
        state = state,
    )
}
