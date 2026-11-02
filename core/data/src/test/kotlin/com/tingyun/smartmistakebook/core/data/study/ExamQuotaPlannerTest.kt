package com.tingyun.smartmistakebook.core.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试截止日每日配额（批次 40 WS3 item 19）。窗口外零输出、窗口内按
 * ceil(缺口/剩余学习日) 排、超容量如实报 unmet——全部是纯算术。
 */
class ExamQuotaPlannerTest {

    @Test
    fun `outside the fourteen-day window produces zero output`() {
        val facts = listOf(fact("unit-a", atExam = 0.50))
        listOf(0L, 15L, 30L, -1L).forEach { daysUntil ->
            val plan = ExamQuotaPlanner.plan(
                facts = facts,
                examEpochDay = TODAY + daysUntil,
                todayEpochDay = TODAY,
                baseRetention = 0.90,
                budgetSeconds = 1200,
                p50DurationSeconds = 180,
            )
            assertFalse("d=$daysUntil must be outside the window", plan.inWindow)
            assertEquals(0, plan.quota)
            assertEquals(0, plan.deficitCount)
            assertTrue(plan.picked.isEmpty())
            assertEquals(0.90, plan.targetRetentionAtExam, 1e-12)
        }
    }

    @Test
    fun `in-window target retention follows the fourteen-day ramp`() {
        val plan = ExamQuotaPlanner.plan(
            facts = emptyList(),
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
        )
        assertTrue(plan.inWindow)
        assertEquals(0.935, plan.targetRetentionAtExam, 1e-12)
        assertEquals(7, plan.studyDaysLeft)
        assertEquals(6, plan.capacityPerDay)
        assertEquals(0, plan.quota)
    }

    @Test
    fun `only items below the exam target count as deficits`() {
        val plan = ExamQuotaPlanner.plan(
            facts = listOf(
                fact("unit-met", atExam = 0.95),
                fact("unit-above-low", atExam = 0.94),
                fact("unit-low", atExam = 0.40),
                fact("unit-no-exam", atExam = null),
            ),
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
        )
        assertEquals("只有低于 r*_exam 的题进入缺口集合", 1, plan.deficitCount)
        assertEquals(listOf("unit-low"), plan.picked.map(ReviewCapacityFacts::practiceUnitId))
    }

    @Test
    fun `quota is capped by the daily capacity and the excess is reported honestly`() {
        val facts = (1..60).map { index ->
            fact("unit-$index", atExam = 0.50, nextReviewAt = TODAY * DAY + index)
        }
        val plan = ExamQuotaPlanner.plan(
            facts = facts,
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 1080,
            p50DurationSeconds = 180,
        )
        assertEquals(60, plan.deficitCount)
        assertEquals("ceil(60/7)=9，但单日容量只有 6", 6, plan.quota)
        assertEquals(6, plan.picked.size)
        assertEquals(54, plan.unmetCount)
        assertEquals(plan.deficitCount, plan.picked.size + plan.unmetCount)
    }

    @Test
    fun `study-day weights shrink the denominator without changing the window`() {
        val facts = (1..60).map { index ->
            fact("unit-$index", atExam = 0.50, nextReviewAt = TODAY * DAY + index)
        }
        val noRest = ExamQuotaPlanner.plan(
            facts = facts,
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 3600,
            p50DurationSeconds = 180,
            studyDayWeights = listOf(1, 1, 1, 1, 1, 1, 1),
        )
        val oneRest = ExamQuotaPlanner.plan(
            facts = facts,
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 3600,
            p50DurationSeconds = 180,
            studyDayWeights = listOf(1, 1, 1, 1, 1, 1, 0),
        )
        assertEquals(7, noRest.studyDaysLeft)
        assertEquals(6, oneRest.studyDaysLeft)
        assertEquals("ceil(60/7)=9", 9, noRest.quota)
        assertEquals("ceil(60/6)=10", 10, oneRest.quota)
        assertThrows(IllegalArgumentException::class.java) {
            ExamQuotaPlanner.plan(
                facts = facts,
                examEpochDay = TODAY + 7,
                todayEpochDay = TODAY,
                baseRetention = 0.90,
                budgetSeconds = 3600,
                p50DurationSeconds = 180,
                studyDayWeights = listOf(1, 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExamQuotaPlanner.plan(
                facts = facts,
                examEpochDay = TODAY + 7,
                todayEpochDay = TODAY,
                baseRetention = 0.90,
                budgetSeconds = 3600,
                p50DurationSeconds = 180,
                studyDayWeights = listOf(0, 0, 0, 0, 0, 0, 0),
            )
        }
    }

    @Test
    fun `deficit ordering is deterministic by deficit then exam risk then due time then id`() {
        val plan = ExamQuotaPlanner.plan(
            facts = listOf(
                fact("unit-b", atExam = 0.60, nextReviewAt = 5 * DAY),
                fact("unit-a", atExam = 0.60, nextReviewAt = 5 * DAY),
                fact("unit-earlier", atExam = 0.30, nextReviewAt = 9 * DAY),
                fact("unit-more-overdue", atExam = 0.60, nextReviewAt = 1 * DAY),
            ),
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 3600,
            p50DurationSeconds = 180,
            // 7 天窗口里只留 1 个学习日 → quota = ceil(4/1) = 4，四道题全部取出以验证排序。
            studyDayWeights = listOf(1, 0, 0, 0, 0, 0, 0),
        )
        assertEquals(
            listOf("unit-earlier", "unit-more-overdue", "unit-a", "unit-b"),
            plan.picked.map(ReviewCapacityFacts::practiceUnitId),
        )
    }

    @Test
    fun `a shared subject quota caps the plan and never over-picks`() {
        val facts = (1..60).map { index ->
            fact("unit-$index", atExam = 0.50, nextReviewAt = TODAY * DAY + index)
        }
        val plan = ExamQuotaPlanner.plan(
            facts = facts,
            examEpochDay = TODAY + 7,
            todayEpochDay = TODAY,
            baseRetention = 0.90,
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
            quotaCap = 2,
        )
        assertEquals("本科今天只分到 2 个共享名额", 2, plan.quota)
        assertEquals(2, plan.picked.size)
        assertTrue(plan.picked.size <= plan.quota)
        assertEquals(58, plan.unmetCount)
        assertEquals(plan.deficitCount, plan.picked.size + plan.unmetCount)
    }

    @Test
    fun `invalid budget or duration fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamQuotaPlanner.plan(
                facts = emptyList(),
                examEpochDay = TODAY + 7,
                todayEpochDay = TODAY,
                baseRetention = 0.90,
                budgetSeconds = 0,
                p50DurationSeconds = 180,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExamQuotaPlanner.plan(
                facts = emptyList(),
                examEpochDay = TODAY + 7,
                todayEpochDay = TODAY,
                baseRetention = 0.90,
                budgetSeconds = 1200,
                p50DurationSeconds = 0,
            )
        }
    }

    private fun fact(
        unitId: String,
        atExam: Double?,
        nextReviewAt: Long = 0L,
        subject: String = "MATH",
    ) = ReviewCapacityFacts(
        practiceUnitId = unitId,
        subjectId = subject,
        difficulty = 5.5,
        stabilityDays = 10.0,
        retrievabilityNow = 0.80,
        retrievabilityAtExam = atExam,
        elapsedFraction = 0.9,
        nextReviewAtEpochMillis = nextReviewAt,
        lapseCount = 0,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val TODAY = 20_000L
    }
}
