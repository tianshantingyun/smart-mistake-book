package com.tingyun.smartmistakebook.core.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 5B 容量策略纯函数（批次 40 WS3 item 18）。所有断言只依赖确定性算术：
 * 不读时钟、不读库、不跑 Gradle。
 */
class ReviewCapacityPolicyTest {

    // ------------------------------------------------------------------
    // F8：预算档位
    // ------------------------------------------------------------------

    @Test
    fun `budget minutes map to seconds only inside the adjudicated 10-60 range`() {
        assertEquals(20 * 60, ReviewCapacityPolicy.secondsForBudgetMinutes(20))
        assertEquals(10 * 60, ReviewCapacityPolicy.secondsForBudgetMinutes(10))
        assertEquals(60 * 60, ReviewCapacityPolicy.secondsForBudgetMinutes(60))
        assertEquals(25 * 60, ReviewCapacityPolicy.secondsForBudgetMinutes(25))
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.secondsForBudgetMinutes(9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.secondsForBudgetMinutes(61)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.secondsForBudgetMinutes(23)
        }
    }

    // ------------------------------------------------------------------
    // 每日配额
    // ------------------------------------------------------------------

    @Test
    fun `daily quota uses ceil and clamps to the day capacity`() {
        assertEquals(0, ReviewCapacityPolicy.dailyQuota(remaining = 0, studyDaysLeft = 7, capPerDay = 6))
        assertEquals(3, ReviewCapacityPolicy.dailyQuota(remaining = 60, studyDaysLeft = 21, capPerDay = 99))
        assertEquals(3, ReviewCapacityPolicy.dailyQuota(remaining = 61, studyDaysLeft = 21, capPerDay = 99))
        assertEquals(3, ReviewCapacityPolicy.dailyQuota(remaining = 60, studyDaysLeft = 20, capPerDay = 99))
        assertEquals(4, ReviewCapacityPolicy.dailyQuota(remaining = 61, studyDaysLeft = 20, capPerDay = 99))
        assertEquals(6, ReviewCapacityPolicy.dailyQuota(remaining = 300, studyDaysLeft = 21, capPerDay = 6))
        assertEquals(6, ReviewCapacityPolicy.dailyQuota(remaining = 1, studyDaysLeft = 0, capPerDay = 6))
        assertEquals(0, ReviewCapacityPolicy.dailyQuota(remaining = 5, studyDaysLeft = 7, capPerDay = 0))
    }

    @Test
    fun `daily quota is monotone in remaining and in study days`() {
        var previous = -1
        for (remaining in 0..100) {
            val quota = ReviewCapacityPolicy.dailyQuota(remaining, studyDaysLeft = 7, capPerDay = 99)
            assertTrue("quota must not decrease as remaining grows", quota >= previous)
            previous = quota
        }
        var previousWide = Int.MAX_VALUE
        for (daysLeft in 1..30) {
            val quota = ReviewCapacityPolicy.dailyQuota(remaining = 60, studyDaysLeft = daysLeft, capPerDay = 99)
            assertTrue("quota must not increase as more days are available", quota <= previousWide)
            previousWide = quota
        }
    }

    // ------------------------------------------------------------------
    // 考试目标保留率与缺口
    // ------------------------------------------------------------------

    @Test
    fun `exam target retention ramps to 097 only inside the 14-day window`() {
        assertEquals(0.90, ReviewCapacityPolicy.examTargetRetention(0.90, daysUntilExam = 14), 1e-12)
        assertEquals(0.90, ReviewCapacityPolicy.examTargetRetention(0.90, daysUntilExam = 20), 1e-12)
        assertEquals(0.90, ReviewCapacityPolicy.examTargetRetention(0.90, daysUntilExam = -1), 1e-12)
        assertEquals(0.97, ReviewCapacityPolicy.examTargetRetention(0.90, daysUntilExam = 0), 1e-12)
        assertEquals(0.97, ReviewCapacityPolicy.examTargetRetention(0.97, daysUntilExam = 7), 1e-12)
        assertEquals(0.935, ReviewCapacityPolicy.examTargetRetention(0.90, daysUntilExam = 7), 1e-12)
        assertEquals(0.965, ReviewCapacityPolicy.examTargetRetention(0.90, daysUntilExam = 1), 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.examTargetRetention(0.69, daysUntilExam = 7)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.examTargetRetention(0.98, daysUntilExam = 7)
        }
    }

    @Test
    fun `exam deficit is zero when target is met and bounded otherwise`() {
        assertEquals(0.0, ReviewCapacityPolicy.examDeficit(0.90, 0.90), 1e-12)
        assertEquals(0.4, ReviewCapacityPolicy.examDeficit(0.50, 0.90), 1e-12)
        assertEquals(0.0, ReviewCapacityPolicy.examDeficit(0.95, 0.90), 1e-12)
        assertEquals(1.0, ReviewCapacityPolicy.examDeficit(0.0, 1.0), 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.examDeficit(1.1, 0.90)
        }
    }

    // ------------------------------------------------------------------
    // F18：按日权重重排
    // ------------------------------------------------------------------

    @Test
    fun `day weights redistribute without changing the total`() {
        val weights = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2)
        val allocated = ReviewCapacityPolicy.allocateByDayWeights(remaining = 60, dayWeights = weights)
        assertEquals("总量必须守恒", 60, allocated.sum())
        assertEquals(14, allocated.size)
        // 周末 2× 应拿到比平日更多的配额。
        val weekday = (0..9).sumOf { allocated[it] }.toDouble() / 10
        val weekend = (10..13).sumOf { allocated[it] }.toDouble() / 4
        assertTrue("周末配额应高于平日", weekend > weekday)
        assertTrue("休息外的每天至少 1 题", allocated.all { it >= 1 })
    }

    @Test
    fun `rest days receive zero and a zero-weight plan fails closed`() {
        val allocated = ReviewCapacityPolicy.allocateByDayWeights(
            remaining = 10,
            dayWeights = listOf(1, 0, 1, 0, 1),
        )
        assertEquals(10, allocated.sum())
        assertEquals(0, allocated[1])
        assertEquals(0, allocated[3])
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.allocateByDayWeights(10, listOf(0, 0, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.allocateByDayWeights(10, emptyList())
        }
    }

    @Test
    fun `largest remainder is deterministic and conserves the total`() {
        val weights = listOf(1, 1, 1)
        val first = ReviewCapacityPolicy.allocateByDayWeights(remaining = 10, dayWeights = weights)
        val second = ReviewCapacityPolicy.allocateByDayWeights(remaining = 10, dayWeights = weights)
        assertEquals(first, second)
        assertEquals(10, first.sum())
        // 10/3 = 3.33：三天各 3，余 1 给第一天（同分按日序）。
        assertEquals(listOf(4, 3, 3), first)
    }

    // ------------------------------------------------------------------
    // F9：追赶上限与清账
    // ------------------------------------------------------------------

    @Test
    fun `days to clear follows ceil of backlog over growth headroom`() {
        assertEquals(10, ReviewCapacityPolicy.daysToClear(backlogDays = 5.0, maxDailyGrowth = 1.5))
        assertEquals(2, ReviewCapacityPolicy.daysToClear(backlogDays = 1.0, maxDailyGrowth = 1.5))
        assertEquals(0, ReviewCapacityPolicy.daysToClear(backlogDays = 0.0, maxDailyGrowth = 1.5))
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.daysToClear(5.0, maxDailyGrowth = 1.0)
        }
    }

    @Test
    fun `growth multiplier is bounded by the 15x adjudicated cap`() {
        assertEquals(1.0 + 2.0 / 7.0, ReviewCapacityPolicy.requiredGrowthMultiplier(2.0, 7), 1e-12)
        assertEquals(
            1.5,
            ReviewCapacityPolicy.boundedGrowthMultiplier(
                backlogDays = 10.0,
                remainingStudyDays = 3,
                growthCap = ReviewCapacityPolicy.MAX_DAILY_GROWTH,
            ),
            1e-12,
        )
        assertEquals(
            1.0 + 2.0 / 7.0,
            ReviewCapacityPolicy.boundedGrowthMultiplier(2.0, 7),
            1e-12,
        )
        assertEquals(1.5, ReviewCapacityPolicy.boundedGrowthMultiplier(2.0, 0), 1e-12)
        assertTrue(ReviewCapacityPolicy.requiredGrowthMultiplier(1.0, 0).isInfinite())
    }

    @Test
    fun `budget multiplier grows the budget, not the item count`() {
        assertEquals(1800, ReviewCapacityPolicy.grownBudgetSeconds(1200, 1.5))
        assertEquals(1372, ReviewCapacityPolicy.grownBudgetSeconds(1200, 1.0 + 1.0 / 7.0))
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.grownBudgetSeconds(1200, 0.9)
        }
    }

    @Test
    fun `daily capacity is at least one and rejects non-positive inputs`() {
        assertEquals(6, ReviewCapacityPolicy.capacityPerDay(1200, 180))
        assertEquals(1, ReviewCapacityPolicy.capacityPerDay(60, 300))
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.capacityPerDay(0, 180)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.capacityPerDay(1200, 0)
        }
    }

    // ------------------------------------------------------------------
    // 多科共享容量：按紧迫度分科配额（WS1 item 1，R1 §4.2）
    // ------------------------------------------------------------------

    @Test
    fun `the subject with the nearest exam keeps its full demand`() {
        // 容量 6；数学明天考（应取 6），物理 14 天后考（应取 5）。
        // 等比切分会得到 [3,3]（R1 §4.3 的反例）；紧迫度配额必须是 [6,0]。
        val caps = ReviewCapacityPolicy.allocateSubjectCaps(
            subjectIds = listOf("MATH", "PHYSICS"),
            demands = listOf(6, 5),
            daysUntilExam = listOf(1, 14),
            deficits = listOf(10, 60),
            capacity = 6,
        )
        assertEquals(listOf(6, 0), caps)
    }

    @Test
    fun `subject caps conserve the capacity and never exceed a subject demand`() {
        // 确定性输入表（24 组），覆盖容量 0、单科超额、多科争抢与需求全 0。
        val cases = listOf(
            Triple(listOf(6, 5), listOf(1, 14), 6),
            Triple(listOf(0, 0), listOf(1, 2), 5),
            Triple(listOf(99, 1), listOf(2, 1), 3),
            Triple(listOf(2, 2, 2), listOf(3, 2, 1), 4),
            Triple(listOf(1), listOf(1), 0),
            Triple(listOf(7, 7, 7, 7), listOf(1, 2, 3, 4), 9),
        )
        cases.forEach { (demands, days, capacity) ->
            val caps = ReviewCapacityPolicy.allocateSubjectCaps(
                subjectIds = demands.indices.map { "S$it" },
                demands = demands,
                daysUntilExam = days,
                deficits = demands.map { it * 3 },
                capacity = capacity,
            )
            assertEquals(
                "守恒：Σcap == min(Σdemand, capacity)",
                minOf(demands.sum(), capacity),
                caps.sum(),
            )
            caps.forEachIndexed { index, cap ->
                assertTrue("cap_i 不得超本科需求", cap <= demands[index])
                assertTrue("cap_i 不得为负", cap >= 0)
            }
        }
    }

    @Test
    fun `zero capacity grants nothing and duplicate subjects fail closed`() {
        assertEquals(
            listOf(0, 0),
            ReviewCapacityPolicy.allocateSubjectCaps(
                subjectIds = listOf("MATH", "PHYSICS"),
                demands = listOf(3, 3),
                daysUntilExam = listOf(1, 2),
                deficits = listOf(3, 3),
                capacity = 0,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ReviewCapacityPolicy.allocateSubjectCaps(
                subjectIds = listOf("MATH", "MATH"),
                demands = listOf(3, 3),
                daysUntilExam = listOf(1, 2),
                deficits = listOf(3, 3),
                capacity = 6,
            )
        }
    }
}
