package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-17「合格长程样本台账」（批次 20 / T9 只读分类视图）的判定表与口径等价证明。
 *
 * 口径原文：`docs/research/2026-09-28-algorithm-fix-plan.md:493`——「按SchedulingEvaluation
 * 实际筛选规则输出原始数、MODEL_JUDGED/REVEAL排除、首条/同日重复、缺时间/缺状态、最终可预测
 * 长程数；400门只看最后一列」。这里逐条钉住：
 * - 每个排除桶的分类与「原始/排除/可预测数分列」的恰分性（REFACTOR-MASTER-LINE §Wave X）；
 * - **口径等价**：台账的最终可预测长程数必须逐位等于
 *   `FsrsParameterOptimizer.predictableSampleCount`（同批数据、同一筛选规则）——这是
 *   "与实现同口径"的机械证明，任何一侧判据漂移都会在这里红；
 * - 400 门只引用常量、不做达标判定（不把 MODEL_JUDGED 凑入 400 门，
 *   `docs/research/2026-10-11-new-stages-spec.md:194`）。
 */
class LongRangeSampleLedgerTest {

    @Test
    fun `every aud-17 bucket is classified and the funnel partitions the raw rows`() {
        val rows = listOf(
            // 卡 A：首条 → 同日重复（state 权威）→ 长程；随后两行按来源排除。
            candidate("unit-a", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
            candidate("unit-a", DAY * 0 + 2 * HOUR, state = ReviewSample.STATE_LEARNING, deltaTDays = 0.0),
            candidate("unit-a", DAY * 3, state = ReviewSample.STATE_REVIEW, deltaTDays = 3.0),
            candidate("unit-a", DAY * 4, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            candidate("unit-a", DAY * 5, sourceKind = ReviewSample.REVEAL_KIND),
            // 卡 B：首条 → 长程。
            candidate("unit-b", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
            candidate("unit-b", DAY * 7, state = ReviewSample.STATE_REVIEW, deltaTDays = 7.0),
            // 卡 C：只有一行——永远是首条（不产预测对）。
            candidate("unit-c", DAY * 1),
            // 卡 D：唯一一行是被撤销的 attempt（行保留、移出有效证据集合）。
            candidate("unit-d", DAY * 2, schedulingEligible = false),
            // 卡 E：只有 MODEL_JUDGED 行——先排除后分组，不产生"首条"。
            candidate("unit-e", DAY * 2, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
        )

        val report = LongRangeSampleLedger.classify(rows)

        assertEquals(10, report.rawRowCount)
        assertEquals(1, report.countOf(LongRangeSampleClassification.REVOKED_OR_OBSERVED))
        assertEquals(2, report.countOf(LongRangeSampleClassification.MODEL_JUDGED_EXCLUDED))
        assertEquals(1, report.countOf(LongRangeSampleClassification.REVEAL_EXCLUDED))
        assertEquals(3, report.countOf(LongRangeSampleClassification.FIRST_OF_CARD))
        assertEquals(1, report.countOf(LongRangeSampleClassification.SAME_DAY_REPEAT))
        assertEquals(2, report.countOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE))
        // AUD-17：原始/排除/可预测数分列，且分列恰分原始行（一行一桶，不重不漏）。
        val funnelSum = listOf(
            LongRangeSampleClassification.REVOKED_OR_OBSERVED,
            LongRangeSampleClassification.MODEL_JUDGED_EXCLUDED,
            LongRangeSampleClassification.REVEAL_EXCLUDED,
            LongRangeSampleClassification.FIRST_OF_CARD,
            LongRangeSampleClassification.SAME_DAY_REPEAT,
            LongRangeSampleClassification.PREDICTABLE_LONG_RANGE,
        ).sumOf(report::countOf)
        assertEquals(report.rawRowCount, funnelSum)
        assertEquals(report.rawRowCount, report.entries.size)
        // 明细与聚合同源：可预测长程的两行分别来自卡 A 与卡 B。
        assertEquals(
            listOf("unit-a", "unit-b"),
            report.entriesOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE)
                .map { it.candidate.practiceUnitId },
        )
        // 卡 E 的 MODEL_JUDGED 行被排除，不给该卡记"首条"（先过滤、后按卡分组）。
        assertTrue(
            report.entriesOf(LongRangeSampleClassification.FIRST_OF_CARD)
                .map { it.candidate.practiceUnitId }
                .none { it == "unit-e" },
        )
    }

    @Test
    fun `predictable count equals the optimizer predictableSampleCount on the same fixture`() {
        val stamped = listOf(
            candidate("unit-a", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
            candidate("unit-a", DAY * 0 + 2 * HOUR, state = ReviewSample.STATE_LEARNING, deltaTDays = 0.0),
            candidate("unit-a", DAY * 3, state = ReviewSample.STATE_REVIEW, deltaTDays = 3.0),
            candidate("unit-a", DAY * 4, sourceKind = ReviewSample.MODEL_JUDGED_KIND),
            candidate("unit-a", DAY * 5, sourceKind = ReviewSample.REVEAL_KIND),
            candidate("unit-b", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
            candidate("unit-b", DAY * 7, state = ReviewSample.STATE_REVIEW, deltaTDays = 7.0),
            candidate("unit-c", DAY * 1),
            candidate("unit-d", DAY * 2, schedulingEligible = false),
        )
        // 生产里撤销行在读口就被移出有效证据集合（ReviewSample 不携带该旗标），
        // 等价对拍同样只喂 eligible 行——两侧必须同形，否则等价是巧合不是口径。
        assertEquals(
            FsrsParameterOptimizer.predictableSampleCount(
                stamped.filter { it.schedulingEligible }.map(::asSample),
            ),
            LongRangeSampleLedger.classify(stamped).predictableLongRangeCount,
        )

        // 迁移前的遗留行（state/delta_t 均空）走同一等价：同日判定回落 elapsed<1 墙钟启发式。
        val legacy = listOf(
            candidate("unit-l", DAY * 0, state = null, deltaTDays = null),
            candidate("unit-l", DAY * 0 + HOUR, state = null, deltaTDays = null),
            candidate("unit-l", DAY * 4, state = null, deltaTDays = null),
        )
        assertEquals(
            FsrsParameterOptimizer.predictableSampleCount(
                legacy.filter { it.schedulingEligible }.map(::asSample),
            ),
            LongRangeSampleLedger.classify(legacy).predictableLongRangeCount,
        )
    }

    @Test
    fun `state is authoritative for same-day and legacy rows fall back to the elapsed heuristic`() {
        // state=Learning 权威：即使采集日差 ≥1 也判同日重复（与 predictableSampleCount 同判据）。
        val stateWins = LongRangeSampleLedger.classify(
            listOf(
                candidate("unit-s", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
                candidate("unit-s", DAY * 5, state = ReviewSample.STATE_LEARNING, deltaTDays = 5.0),
            ),
        )
        assertEquals(
            listOf(DAY * 5),
            stateWins.entriesOf(LongRangeSampleClassification.SAME_DAY_REPEAT)
                .map { it.candidate.reviewedAtEpochMillis },
        )

        // state=Review 同样权威：即使日差 <1 也判长程（启发式只在 state 为空时接管）。
        val reviewState = LongRangeSampleLedger.classify(
            listOf(
                candidate("unit-r", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
                candidate("unit-r", DAY * 0 + HOUR, state = ReviewSample.STATE_REVIEW, deltaTDays = 0.04),
            ),
        )
        assertEquals(
            listOf(DAY * 0 + HOUR),
            reviewState.entriesOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE)
                .map { it.candidate.reviewedAtEpochMillis },
        )

        // 遗留行（state=null）：elapsed<1 → 同日；elapsed≥1 → 长程。
        val legacy = LongRangeSampleLedger.classify(
            listOf(
                candidate("unit-g", DAY * 0, state = null, deltaTDays = null),
                candidate("unit-g", DAY * 0 + HOUR, state = null, deltaTDays = null),
                candidate("unit-g", DAY * 2, state = null, deltaTDays = null),
            ),
        )
        assertEquals(1, legacy.countOf(LongRangeSampleClassification.SAME_DAY_REPEAT))
        assertEquals(1, legacy.countOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE))
    }

    @Test
    fun `excluded rows neither seed nor advance a card sequence`() {
        val rows = listOf(
            candidate("unit-x", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
            // 看答案行夹在两次真实作答之间：被排除，不占"首条"、不推进前条。
            candidate(
                "unit-x",
                DAY * 1,
                sourceKind = ReviewSample.REVEAL_KIND,
                state = ReviewSample.STATE_REVIEW,
                deltaTDays = 1.0,
            ),
            candidate("unit-x", DAY * 3, state = ReviewSample.STATE_REVIEW, deltaTDays = 3.0),
        )
        val report = LongRangeSampleLedger.classify(rows)
        assertEquals(1, report.countOf(LongRangeSampleClassification.REVEAL_EXCLUDED))
        assertEquals(
            listOf(DAY * 0),
            report.entriesOf(LongRangeSampleClassification.FIRST_OF_CARD)
                .map { it.candidate.reviewedAtEpochMillis },
        )
        assertEquals(
            listOf(DAY * 3),
            report.entriesOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE)
                .map { it.candidate.reviewedAtEpochMillis },
        )
        assertEquals(
            FsrsParameterOptimizer.predictableSampleCount(rows.filter { it.schedulingEligible }.map(::asSample)),
            report.predictableLongRangeCount,
        )

        // 卡的首条按时间序是 REVEAL：过滤后该卡只剩一行 ATTEMPT → 首条，不产长程对。
        val revealFirst = listOf(
            candidate("unit-y", DAY * 0, sourceKind = ReviewSample.REVEAL_KIND),
            candidate("unit-y", DAY * 2, state = ReviewSample.STATE_REVIEW, deltaTDays = 2.0),
        )
        val revealFirstReport = LongRangeSampleLedger.classify(revealFirst)
        assertEquals(1, revealFirstReport.countOf(LongRangeSampleClassification.REVEAL_EXCLUDED))
        assertEquals(1, revealFirstReport.countOf(LongRangeSampleClassification.FIRST_OF_CARD))
        assertEquals(0, revealFirstReport.countOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE))
        assertEquals(
            FsrsParameterOptimizer.predictableSampleCount(revealFirst.filter { it.schedulingEligible }.map(::asSample)),
            revealFirstReport.predictableLongRangeCount,
        )
    }

    @Test
    fun `empty ledger reports zero counts with the gate floor still referenced`() {
        val report = LongRangeSampleLedger.classify(emptyList())
        assertEquals(0, report.rawRowCount)
        assertEquals(0, report.predictableLongRangeCount)
        assertTrue(report.entries.isEmpty())
        assertEquals(FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING, report.gateFloor)
    }

    @Test
    fun `missing delta_t and state are counted over the predictable set only`() {
        val rows = listOf(
            candidate("unit-m", DAY * 0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
            // 遗留长程行：缺时间+缺状态（重放走墙钟回退/启发式），两个缺口各计 1。
            candidate("unit-m", DAY * 5, state = null, deltaTDays = null),
            // 同日重复行的缺口不计（口径只看最终可预测集合）。
            candidate("unit-m", DAY * 5 + HOUR, state = null, deltaTDays = null),
            // 被排除行（REVEAL / 撤销）的缺口不计。
            candidate("unit-m", DAY * 6, sourceKind = ReviewSample.REVEAL_KIND, state = null, deltaTDays = null),
            candidate("unit-m", DAY * 7, schedulingEligible = false, state = null, deltaTDays = null),
        )
        val report = LongRangeSampleLedger.classify(rows)
        assertEquals(1, report.predictableMissingDeltaTCount)
        assertEquals(1, report.predictableMissingStateCount)
    }

    @Test
    fun `gate floor references the optimizer 400 constant and never judges attainment`() {
        // AUD-17：400 门只看最后一列；这里只引用常量防两处漂移，台账本身无达标判定。
        assertEquals(400, FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING)
        assertEquals(
            FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING,
            LongRangeSampleLedger.classify(emptyList()).gateFloor,
        )
    }

    private fun candidate(
        unit: String,
        at: Long,
        sourceKind: String = ReviewSample.ATTEMPT_KIND,
        state: Int? = ReviewSample.STATE_REVIEW,
        deltaTDays: Double? = 2.0,
        schedulingEligible: Boolean = true,
    ): LongRangeSampleCandidate = LongRangeSampleCandidate(
        practiceUnitId = unit,
        reviewedAtEpochMillis = at,
        rating = FsrsRating.GOOD.storedRating,
        sourceKind = sourceKind,
        deltaTDays = deltaTDays,
        state = state,
        schedulingEligible = schedulingEligible,
    )

    /** 与生产映射（`LongRangeSampleLedger.toReplaySample`）同构的测试侧映射，供口径等价对拍。 */
    private fun asSample(candidate: LongRangeSampleCandidate): ReviewSample = ReviewSample(
        practiceUnitId = candidate.practiceUnitId,
        reviewedAtEpochMillis = candidate.reviewedAtEpochMillis,
        rating = FsrsRating.fromStored(candidate.rating),
        sourceKind = candidate.sourceKind,
        deltaTDays = candidate.deltaTDays,
        state = candidate.state,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
