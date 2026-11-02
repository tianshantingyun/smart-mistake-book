package com.tingyun.smartmistakebook.core.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缺勤再平衡（批次 40 WS3 item 20）：每日重算、预算上限 1.5×、
 * 只拉 ≥85% 间隔、超 horizon 时如实报清账天数与不可拉题数。
 */
class MissedDayRebalancePolicyTest {

    @Test
    fun `empty backlog is a neutral plan that leaves the budget untouched`() {
        val plan = MissedDayRebalancePolicy.plan(
            backlog = emptyList(),
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
            remainingStudyDays = 7,
        )
        assertEquals(0, plan.dailyTake)
        assertEquals(1200, plan.dailyBudgetSeconds)
        assertEquals(0, plan.daysNeeded)
        assertEquals(1.0, plan.growthMultiplier, 1e-12)
        assertTrue(plan.picked.isEmpty())
        assertEquals(0, plan.ineligibleCount)
    }

    @Test
    fun `backlog beyond the growth cap extends the horizon at 15x budget`() {
        val plan = MissedDayRebalancePolicy.plan(
            backlog = facts(30), // 30 / 6 = 5 normal days
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
            remainingStudyDays = 7,
        )
        // required = 1 + 5/7 = 1.714 > 1.5 → 预算封顶 1.5×，清账 10 天。
        assertEquals(1.5, plan.growthMultiplier, 1e-12)
        assertEquals(1800, plan.dailyBudgetSeconds)
        assertEquals(10, plan.dailyTake)
        assertEquals(10, plan.daysNeeded)
        assertEquals(30, plan.eligibleCount)
        assertEquals(0, plan.ineligibleCount)
    }

    @Test
    fun `backlog within the cap uses the required multiplier and the remaining days`() {
        val plan = MissedDayRebalancePolicy.plan(
            backlog = facts(6), // 6 / 6 = 1 normal day
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
            remainingStudyDays = 7,
        )
        assertEquals(1.0 + 1.0 / 7.0, plan.growthMultiplier, 1e-12)
        assertEquals(1372, plan.dailyBudgetSeconds)
        // 容量 1372/180=7，但积压只有 6 道 → 实际取出 6（dailyTake 是真实取出数，不是假容量）。
        assertEquals(6, plan.dailyTake)
        assertEquals(6, plan.picked.size)
        assertEquals(7, plan.daysNeeded)
    }

    @Test
    fun `zero remaining days still grows the budget to the cap and reports clear days`() {
        val plan = MissedDayRebalancePolicy.plan(
            backlog = facts(12), // 12 / 6 = 2 normal days
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
            remainingStudyDays = 0,
        )
        assertEquals(1.5, plan.growthMultiplier, 1e-12)
        assertEquals(1800, plan.dailyBudgetSeconds)
        assertEquals(10, plan.dailyTake)
        assertEquals(4, plan.daysNeeded)
    }

    @Test
    fun `only items with at least 85 percent elapsed interval are pulled`() {
        val plan = MissedDayRebalancePolicy.plan(
            backlog = listOf(
                fact("unit-soon", elapsedFraction = 0.84),
                fact("unit-ready", elapsedFraction = 0.85),
                fact("unit-overdue", elapsedFraction = 1.40),
            ),
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
            remainingStudyDays = 7,
        )
        assertEquals(1, plan.ineligibleCount)
        assertEquals(2, plan.eligibleCount)
        assertEquals(
            listOf("unit-overdue", "unit-ready"),
            plan.picked.map(ReviewCapacityFacts::practiceUnitId),
        )
    }

    @Test
    fun `invalid growth cap fails closed instead of chasing forever`() {
        assertThrows(IllegalArgumentException::class.java) {
            MissedDayRebalancePolicy.plan(
                backlog = facts(6),
                baseBudgetSeconds = 1200,
                p50DurationSeconds = 180,
                remainingStudyDays = 7,
                growthCap = 1.0,
            )
        }
    }

    private fun facts(count: Int, elapsedFraction: Double = 0.9): List<ReviewCapacityFacts> =
        (1..count).map { index ->
            fact("unit-$index", elapsedFraction = elapsedFraction, nextReviewAt = index.toLong())
        }

    private fun fact(
        unitId: String,
        elapsedFraction: Double,
        nextReviewAt: Long = 0L,
    ) = ReviewCapacityFacts(
        practiceUnitId = unitId,
        subjectId = "MATH",
        difficulty = 5.5,
        stabilityDays = 10.0,
        retrievabilityNow = 0.70,
        retrievabilityAtExam = null,
        elapsedFraction = elapsedFraction,
        nextReviewAtEpochMillis = nextReviewAt,
        lapseCount = 0,
    )
}
