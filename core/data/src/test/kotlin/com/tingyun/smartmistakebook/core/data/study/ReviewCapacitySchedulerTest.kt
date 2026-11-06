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
    fun `items demanded by both channels are excluded before rebalance picks`() {
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
        assertEquals("考试已取的 2 道不再进入积压通道", 6, plan.rebalance?.eligibleCount)
        assertEquals(
            "去重前置后两条通道的交集必须为空（修复前这里报 2，重复题白占名额）",
            0,
            plan.deduplicatedCount,
        )
        assertEquals("当日容量 1200/180=6", 6, plan.dailyCapacity)
        assertEquals("考试 2 + 再平衡剩余名额 4，去重后总量 6", 6, plan.totalPickedCount)
        assertEquals(
            "工作守恒：有合格积压时不得留下空余名额",
            minOf(plan.dailyCapacity, plan.examPicked.size + plan.rebalance!!.eligibleCount),
            plan.totalPickedCount,
        )
        val examIds = plan.examPicked.mapTo(hashSetOf(), ReviewCapacityFacts::practiceUnitId)
        assertTrue(
            "再平衡不得重复取考试已排的题",
            plan.rebalance!!.picked.none { it.practiceUnitId in examIds },
        )
    }

    /**
     * WS1 item 1 的回归用例（先红后绿）：两科各要 80% 的当日容量，合并后总量必须 ≤ 日容量；
     * 名额按紧迫度给——明天就考的科拿满应取数，14 天后才考的科只拿剩下的。
     */
    @Test
    fun `two subjects each demanding 80 percent share the daily capacity by urgency`() {
        val math = (1..8).map { index ->
            fact("MATH-$index", subject = "MATH", atExam = 0.5, nextReviewAt = index.toLong())
        }
        val physics = (1..112).map { index ->
            fact("PHYSICS-$index", subject = "PHYSICS", atExam = 0.5, nextReviewAt = index.toLong())
        }
        val plan = ReviewCapacityScheduler.plan(
            facts = math + physics,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf(
                "MATH" to TODAY + 1,
                "PHYSICS" to TODAY + 14,
            ),
            budgetSeconds = 1800, // 1800/180 = 10
            p50DurationSeconds = 180,
        )
        assertEquals(10, plan.dailyCapacity)
        assertEquals(
            "数学明天考(缺口 8, 应取 8) 必须先拿满；物理 14 天后考只拿剩余 2",
            8,
            plan.examPicked.count { it.subjectId == "MATH" },
        )
        assertEquals(2, plan.examPicked.count { it.subjectId == "PHYSICS" })
        assertEquals("合并后总量不得超过日容量", 10, plan.subjectReadouts.sumOf(SubjectCapacity::granted))
        assertTrue(plan.examPicked.size <= plan.dailyCapacity)

        val mathReadout = plan.subjectReadouts.first { it.subjectId == "MATH" }
        assertEquals(8, mathReadout.demand)
        assertEquals(8, mathReadout.granted)
        assertEquals(0, mathReadout.unmet)
        assertTrue("明天就考、今天已排满应取数 → 考前做得完", mathReadout.feasibleBeforeExam)
        val physicsReadout = plan.subjectReadouts.first { it.subjectId == "PHYSICS" }
        assertEquals(8, physicsReadout.demand)
        assertEquals(2, physicsReadout.granted)
        assertEquals(6, physicsReadout.unmet)
        assertTrue(
            "缺口 112、每天只分到 2、剩 14 天 → 考前做不完，必须如实报告",
            !physicsReadout.feasibleBeforeExam,
        )
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
        assertEquals("两科应取数之和 12 超过单日容量 6，必须共享同一份容量", 6, plan.examPicked.size)
        assertTrue(
            "考试通道总量不得超过当日容量（修复前为 12）",
            plan.examPicked.size <= plan.dailyCapacity,
        )
        assertEquals("同紧迫度同缺口时按科名定序：MATH 先拿满，PHYSICS 得 0", 6, plan.subjectReadouts[0].granted)
        assertEquals(0, plan.subjectReadouts[1].granted)
        assertEquals(listOf("PHYSICS"), plan.starvedSubjects)
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
        // 考试取 2；再平衡只能用剩下的 4 个名额，且不再重复考试已排的题。
        assertEquals(2, plan.examPicked.size)
        assertEquals(4, plan.rebalance!!.picked.size)
        assertEquals(0, plan.deduplicatedCount)
        assertEquals("考试 2 + 再平衡 4 = 6（修复前 4，因重复题占了名额）", 6, plan.totalPickedCount)
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

    @Test
    fun `an already-ramped budget is not multiplied by 15 twice`() {
        // 考试爬坡把 1200 抬到 1800 后，调用方把 1800 作为当日预算传入，并把 1800 声明为
        // 当日绝对上限：再乘 1.5 会得到 2700s（15 道），比基准预算高 2.25×。
        val facts = (1..30).map { index ->
            fact("unit-$index", subject = "MATH", atExam = null, elapsedFraction = 0.9, nextReviewAt = index.toLong())
        }
        val plan = ReviewCapacityScheduler.plan(
            facts = facts,
            todayEpochDay = TODAY,
            examEpochDayBySubject = emptyMap(),
            budgetSeconds = 1800,
            p50DurationSeconds = 180,
            rebalanceEnabled = true,
            remainingStudyDays = 7,
            growthCeilingBudgetSeconds = 1800,
        )
        assertEquals(10, plan.dailyCapacity)
        assertEquals("绝对上限不得在已爬坡预算上再乘 1.5", 10, plan.maxDailyCapacity)
        assertEquals(10, plan.totalPickedCount)
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
