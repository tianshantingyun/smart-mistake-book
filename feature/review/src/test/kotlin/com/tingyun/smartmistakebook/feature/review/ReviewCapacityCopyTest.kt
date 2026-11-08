package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.StudyReviewCapacity
import com.tingyun.smartmistakebook.core.domain.StudySubjectCapacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复习页容量读数与 KD-68 新题预览的文案契约（纯函数，JVM 可测）。
 *
 * 两条硬约束：
 * - 没有容量事实（`capacity == null`）时给**如实空态**，不得编造"今日容量 0 题"；
 * - 超过整日预算、每日引入永远排不进的新题必须单列成"需专项会话"，
 *   并从"每天约 X 题 / 约 M 天安排完"的覆盖承诺里剔除。
 */
class ReviewCapacityCopyTest {

    @Test
    fun `planned capacity copy reports today capacity exam gap subjects and rebalance state`() {
        val copy = requireNotNull(
            reviewCapacityCopy(
                StudyReviewCapacity(
                    examSubjectsInWindow = 2,
                    examDeficitCount = 7,
                    examUnmetCount = 2,
                    rebalancePlanned = false,
                    subjects = listOf(
                        subject("MATH", demand = 5, granted = 3, feasible = true),
                        subject("PHYSICS", demand = 2, granted = 2, feasible = true),
                    ),
                    totalGranted = 5,
                    dailyCapacity = 12,
                    maxDailyCapacity = 18,
                ),
            ),
        )

        assertEquals("今日容量 12 题（最多 18 题）", copy.capacityLine)
        assertEquals("考试缺口 7 题，今天排不下 2 题", copy.examLine)
        assertEquals(
            listOf(
                "数学：名额 3 / 应排 5 题，未满足 2 题",
                "物理：名额 2 / 应排 2 题",
            ),
            copy.subjectLines,
        )
        assertEquals("积压追赶：本轮未规划", copy.rebalanceLine)
    }

    @Test
    fun `unplanned rebalance is named not planned instead of a zero backlog reading`() {
        val copy = requireNotNull(
            reviewCapacityCopy(
                StudyReviewCapacity(
                    examSubjectsInWindow = 1,
                    examDeficitCount = 0,
                    dailyCapacity = 12,
                    maxDailyCapacity = 12,
                ),
            ),
        )

        assertEquals("积压追赶：本轮未规划", copy.rebalanceLine)
        assertFalse(
            "未规划不等于欠账为 0：不得把 0 当作积压读数",
            copy.rebalanceLine.contains("0 题"),
        )
        assertEquals("考试科目没有缺口", copy.examLine)
    }

    @Test
    fun `planned rebalance reports backlog grants and ineligible items`() {
        val copy = requireNotNull(
            reviewCapacityCopy(
                StudyReviewCapacity(
                    examSubjectsInWindow = 0,
                    rebalancePlanned = true,
                    rebalanceBacklogCount = 4,
                    rebalanceIneligibleCount = 1,
                    rebalanceGranted = 2,
                    totalGranted = 2,
                    dailyCapacity = 12,
                    maxDailyCapacity = 18,
                ),
            ),
        )

        assertEquals("窗口内没有考试", copy.examLine)
        assertEquals("积压追赶：积压 4 题，今天可安排 2 题，另有 1 题还没进入安全提前窗", copy.rebalanceLine)
        assertTrue(copy.subjectLines.isEmpty())
    }

    @Test
    fun `an infeasible subject is reported as unable to finish before the exam`() {
        val copy = requireNotNull(
            reviewCapacityCopy(
                StudyReviewCapacity(
                    examSubjectsInWindow = 1,
                    examDeficitCount = 5,
                    dailyCapacity = 4,
                    maxDailyCapacity = 4,
                    subjects = listOf(
                        subject("MATH", demand = 5, granted = 2, feasible = false, unmet = 3),
                    ),
                    totalGranted = 2,
                ),
            ),
        )

        assertEquals(
            listOf("数学：名额 2 / 应排 5 题，未满足 3 题，考前做不完"),
            copy.subjectLines,
        )
    }

    @Test
    fun `subjects without demand do not render a meaningless quota row`() {
        val copy = requireNotNull(
            reviewCapacityCopy(
                StudyReviewCapacity(
                    examSubjectsInWindow = 1,
                    dailyCapacity = 10,
                    maxDailyCapacity = 10,
                    subjects = listOf(
                        subject("MATH", demand = 0, granted = 0, feasible = true, unmet = 0),
                    ),
                ),
            ),
        )

        assertTrue(copy.subjectLines.isEmpty())
    }

    @Test
    fun `no capacity facts produce an honest empty state instead of zeros`() {
        assertNull(reviewCapacityCopy(null))
        assertTrue(ReviewCapacityCopyText.EMPTY_BODY.contains("没有临近的考试"))
        assertFalse(
            "空态不得报出任何编造的容量数字",
            ReviewCapacityCopyText.EMPTY_BODY.contains("0 题"),
        )
    }

    // ------------------------------------------------------------------
    // KD-68：新题积压覆盖预览
    // ------------------------------------------------------------------

    @Test
    fun `intake copy keeps unschedulable items out of the daily promise`() {
        val copy = requireNotNull(
            intakeBacklogCopy(
                backlogCount = 45,
                unschedulableCount = 2,
                medianEstimateSeconds = 100,
                dailyBudgetSeconds = 1200,
            ),
        )

        assertEquals("45 题待学", copy.headline)
        // 只有可调度 43 题进入估算：slice = 1200/3 = 400s，400/100 = 4 题/日，43/4 → 11 天。
        assertEquals("可每日引入的 43 题：每天约 4 题，约 11 天安排完", copy.schedulableLine)
        assertEquals(
            "另有 2 道题单次超过整日预算，需专项会话，不会进入每日计划",
            copy.unschedulableLine,
        )
    }

    @Test
    fun `an all oversized backlog promises no daily introduction`() {
        val copy = requireNotNull(
            intakeBacklogCopy(
                backlogCount = 3,
                unschedulableCount = 3,
                medianEstimateSeconds = 100,
                dailyBudgetSeconds = 1200,
            ),
        )

        assertEquals("3 题待学", copy.headline)
        assertNull("没有任何一道题能被每日引入时不得给每日承诺", copy.schedulableLine)
        assertTrue(requireNotNull(copy.unschedulableLine).contains("需专项会话"))
    }

    @Test
    fun `a fully schedulable backlog keeps no special-session line`() {
        val copy = requireNotNull(
            intakeBacklogCopy(
                backlogCount = 45,
                unschedulableCount = 0,
                medianEstimateSeconds = 100,
                dailyBudgetSeconds = 1200,
            ),
        )

        assertEquals("每天约 4 题，约 12 天安排完", copy.schedulableLine)
        assertNull(copy.unschedulableLine)
    }

    @Test
    fun `an empty backlog has no preview at all`() {
        assertNull(
            intakeBacklogCopy(
                backlogCount = 0,
                unschedulableCount = 0,
                medianEstimateSeconds = 0,
                dailyBudgetSeconds = 1200,
            ),
        )
    }

    private fun subject(
        subjectId: String,
        demand: Int,
        granted: Int,
        feasible: Boolean,
        unmet: Int = demand - granted,
    ) = StudySubjectCapacity(
        subjectId = subjectId,
        daysUntilExam = 7,
        demand = demand,
        granted = granted,
        unmet = unmet,
        feasibleBeforeExam = feasible,
    )
}
