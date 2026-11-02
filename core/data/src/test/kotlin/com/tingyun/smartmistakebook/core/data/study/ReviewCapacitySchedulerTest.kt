package com.tingyun.smartmistakebook.core.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 容量调度门面（批次 40 WS3 item 21）：考试与再平衡合并、按科目取各自最近考试、
 * 同一道题只计一次。
 */
class ReviewCapacitySchedulerTest {

    @Test
    fun `no exam and no rebalance is an empty plan`() {
        val plan = ReviewCapacityScheduler.plan(
            facts = listOf(fact("unit-a", subject = "MATH", atExam = 0.5)),
            todayEpochDay = TODAY,
            examEpochDayBySubject = emptyMap(),
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
            rebalanceEnabled = false,
        )
        assertEquals(0, plan.examDeficitCount)
        assertEquals(0, plan.examUnmetCount)
        assertTrue(plan.examPicked.isEmpty())
        assertNull(plan.rebalance)
        assertEquals(0, plan.deduplicatedCount)
    }

    @Test
    fun `each subject uses its own nearest exam day`() {
        val facts = listOf(
            fact("math-1", subject = "MATH", atExam = 0.30),
            fact("math-2", subject = "MATH", atExam = 0.40),
            fact("physics-1", subject = "PHYSICS", atExam = 0.30),
            fact("unbound", subject = "", atExam = 0.30),
        )
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf(
                "MATH" to TODAY + 7,
                "PHYSICS" to TODAY + 30, // 窗口外
                "" to TODAY + 1,
            ),
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
        )
        assertEquals("只有 MATH 的 2 道缺口题进入考试通道", 2, plan.examDeficitCount)
        assertEquals("ceil(2/7)=1，今天只取 1 道", 1, plan.examPicked.size)
        assertEquals(1, plan.examUnmetCount)
        assertEquals(
            setOf("math-1"),
            plan.examPicked.map(ReviewCapacityFacts::practiceUnitId).toSet(),
        )
    }

    @Test
    fun `an item demanded by both channels is counted once`() {
        val facts = (1..8).map { index ->
            fact("unit-$index", subject = "MATH", atExam = 0.5, elapsedFraction = 0.9, nextReviewAt = index.toLong())
        }
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 7),
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
            rebalanceEnabled = true,
            remainingStudyDays = 7,
        )
        assertEquals(8, plan.examDeficitCount)
        assertEquals(2, plan.examPicked.size) // ceil(8/7)=2
        assertEquals(6, plan.examUnmetCount)
        assertEquals(8, plan.rebalance?.eligibleCount)
        assertEquals("考试与再平衡的 2 道交集只计一次", 2, plan.deduplicatedCount)
        assertEquals("当日容量 1200/180=6", 6, plan.dailyCapacity)
        assertEquals("两条通道去重后不得超过当日容量", 4, plan.totalPickedCount)
    }

    @Test
    fun `two in-window subjects share one daily capacity instead of doubling it`() {
        val facts = listOf("MATH", "PHYSICS").flatMap { subject ->
            (1..60).map { index ->
                fact("$subject-$index", subject = subject, atExam = 0.5, nextReviewAt = index.toLong())
            }
        }
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf(
                "MATH" to TODAY + 7,
                "PHYSICS" to TODAY + 7,
            ),
            budgetSeconds = 1080, // 1080/180 = 6
            p50DurationSeconds = 180,
        )
        assertEquals(6, plan.dailyCapacity)
        assertEquals("两科各 6 道应取数超过单日容量 6，必须按比例分同一份容量", 6, plan.examPicked.size)
        assertTrue(
            "考试通道总量不得超过当日容量（修复前为 12）",
            plan.examPicked.size <= plan.dailyCapacity,
        )
        assertEquals(120, plan.examDeficitCount)
        assertEquals(114, plan.examUnmetCount)
    }

    @Test
    fun `rebalance only spends the slots left by the exam channel`() {
        val facts = (1..8).map { index ->
            fact("unit-$index", subject = "MATH", atExam = 0.5, elapsedFraction = 0.9, nextReviewAt = index.toLong())
        }
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 7),
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
            rebalanceEnabled = true,
            remainingStudyDays = 7,
        )
        // 考试取 2；再平衡只能用剩下的 4 个名额（修复前用整份 1200 预算取 7 道）。
        assertEquals(2, plan.examPicked.size)
        assertEquals(4, plan.rebalance!!.picked.size)
        assertEquals(4, plan.totalPickedCount)
        assertTrue(plan.totalPickedCount <= plan.dailyCapacity)
    }

    @Test
    fun `without exam picks the backlog may still use the 15x ceiling`() {
        val facts = (1..30).map { index ->
            fact("unit-$index", subject = "MATH", atExam = null, elapsedFraction = 0.9, nextReviewAt = index.toLong())
        }
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = TODAY,
            examEpochDayBySubject = emptyMap(),
            budgetSeconds = 1200,
            p50DurationSeconds = 180,
            rebalanceEnabled = true,
            remainingStudyDays = 7,
        )
        assertEquals(6, plan.dailyCapacity)
        assertEquals(10, plan.maxDailyCapacity) // 1800/180
        assertEquals(10, plan.rebalance!!.picked.size)
        assertEquals(10, plan.totalPickedCount)
        assertTrue(plan.totalPickedCount <= plan.maxDailyCapacity)
    }

    private fun fact(
        unitId: String,
        subject: String,
        atExam: Double?,
        elapsedFraction: Double = 0.9,
        nextReviewAt: Long = 0L,
    ) = ReviewCapacityFacts(
        practiceUnitId = unitId,
        subjectId = subject,
        difficulty = 5.5,
        stabilityDays = 10.0,
        retrievabilityNow = 0.7,
        retrievabilityAtExam = atExam,
        elapsedFraction = elapsedFraction,
        nextReviewAtEpochMillis = nextReviewAt,
        lapseCount = 0,
    )

    private companion object {
        const val TODAY = 20_000L
    }
}
