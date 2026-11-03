package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-68 数据侧读取器（批次 40 WS3 item 22）：R 由 `ForgettingCurve` 现算，
 * 与排程侧同源；考试/再平衡计数只读派生。
 */
class ReviewCapacityPreviewReaderTest {

    private val curve = ForgettingCurve()
    private val reader = ReviewCapacityPreviewReader(curve)

    @Test
    fun `facts mirror the forgetting curve exactly`() {
        val state = memory("unit-a", lastReviewedAt = 90 * DAY, stabilityDays = 10.0)
        val now = 100 * DAY
        val facts = reader.facts(
            memoryStates = mapOf("unit-a" to state),
            subjectByPracticeUnit = mapOf("unit-a" to "MATH"),
            nowEpochMillis = now,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 7),
        ).single()

        assertEquals(curve.retentionAt(state, now), facts.retrievabilityNow, 1e-12)
        val examAt = (TODAY + 7) * DAY + (DAY - 1)
        assertEquals(curve.retentionAt(state, examAt), facts.retrievabilityAtExam!!, 1e-12)
        assertEquals(1.0, facts.elapsedFraction, 1e-12)
        assertEquals("MATH", facts.subjectId)
        assertEquals(5.5, facts.difficulty, 1e-12)
    }

    @Test
    fun `a subject without an exam gets a null exam retrievability`() {
        val state = memory("unit-a", lastReviewedAt = 90 * DAY, stabilityDays = 10.0)
        val facts = reader.facts(
            memoryStates = mapOf("unit-a" to state),
            subjectByPracticeUnit = mapOf("unit-a" to "PHYSICS"),
            nowEpochMillis = 100 * DAY,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 7),
        ).single()
        assertNull(facts.retrievabilityAtExam)
    }

    @Test
    fun `preview reports exam deficits and their unmet share honestly`() {
        val memories = (1..8).associate { index ->
            "unit-$index" to memory("unit-$index", lastReviewedAt = 90 * DAY, stabilityDays = 10.0)
        }
        val preview = reader.preview(
            memoryStates = memories,
            subjectByPracticeUnit = memories.keys.associateWith { "MATH" },
            nowEpochMillis = 100 * DAY,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 7),
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
        )
        assertEquals(1, preview.examSubjectsInWindow)
        assertEquals(8, preview.examDeficitCount)
        assertEquals("容量 6/日，ceil(8/7)=2 → 未满足 6", 6, preview.examUnmetCount)
        assertEquals(0, preview.rebalanceBacklogCount)
        // F12：关闭再平衡时 backlog=0 是"未规划"，不是"没有积压"。
        assertEquals(RebalancePlanningState.NOT_PLANNED, preview.rebalanceState)
        assertEquals(0, preview.rebalanceGranted)
        assertEquals(2, preview.totalGranted)
        assertEquals(0, preview.intakeUnschedulableCount)
    }

    @Test
    fun `rebalance and exam previews share one deduplicated count`() {
        val memories = (1..8).associate { index ->
            "unit-$index" to memory("unit-$index", lastReviewedAt = 90 * DAY, stabilityDays = 10.0)
        }
        val preview = reader.preview(
            memoryStates = memories,
            subjectByPracticeUnit = memories.keys.associateWith { "MATH" },
            nowEpochMillis = 100 * DAY,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 7),
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
            rebalanceEnabled = true,
            remainingStudyDays = 7,
        )
        assertEquals(8, preview.rebalanceBacklogCount)
        assertEquals(0, preview.rebalanceIneligibleCount)
        assertEquals("去重前置后交集恒为空", 0, preview.deduplicatedCount)
        assertEquals(RebalancePlanningState.PLANNED, preview.rebalanceState)
        assertEquals("考试 2 + 再平衡 4：总量必须计入再平衡通道", 4, preview.rebalanceGranted)
        assertEquals(6, preview.totalGranted)
        assertEquals(
            "总量 = 逐科考试名额 + 再平衡名额",
            preview.subjects.sumOf(SubjectCapacity::granted) + preview.rebalanceGranted,
            preview.totalGranted,
        )
        assertTrue("再平衡可用到 1.5× 成长上限", preview.totalGranted <= preview.maxDailyCapacity)
        assertEquals(1, preview.subjects.size)
        assertEquals(0, preview.subjects.single().unmet)
    }

    @Test
    fun `multi-subject preview reports per-subject grants and a total within capacity`() {
        val mathUnits = (1..8).map { "MATH-$it" }
        val physicsUnits = (1..112).map { "PHYSICS-$it" }
        val units = mathUnits + physicsUnits
        val memories = units.associate { unit ->
            unit to memory(unit, lastReviewedAt = 90 * DAY, stabilityDays = 10.0)
        }
        val preview = reader.preview(
            memoryStates = memories,
            subjectByPracticeUnit = units.associateWith { it.substringBefore('-') },
            nowEpochMillis = 100 * DAY,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf(
                "MATH" to TODAY + 1,
                "PHYSICS" to TODAY + 14,
            ),
            baseBudgetSeconds = 1800, // 10 slots
            p50DurationSeconds = 180,
            growthCeilingBudgetSeconds = 1800,
        )
        assertEquals(2, preview.examSubjectsInWindow)
        assertEquals(10, preview.dailyCapacity)
        assertEquals(10, preview.maxDailyCapacity)
        assertEquals("明天就考的科拿满应取数", 8, preview.subjects.first { it.subjectId == "MATH" }.granted)
        assertEquals("14 天后考的科只拿剩余名额", 2, preview.subjects.first { it.subjectId == "PHYSICS" }.granted)
        assertEquals(preview.subjects.sumOf(SubjectCapacity::granted), preview.totalGranted)
        assertTrue("逐科之和不得超当日容量", preview.totalGranted <= preview.dailyCapacity)
        assertTrue(
            "物理按今天名额考前做不完，必须如实报告",
            !preview.subjects.first { it.subjectId == "PHYSICS" }.feasibleBeforeExam,
        )
    }

    @Test
    fun `nothing in window and rebalance off returns the empty preview`() {
        val preview = reader.preview(
            memoryStates = mapOf("unit-a" to memory("unit-a", 90 * DAY, 10.0)),
            subjectByPracticeUnit = mapOf("unit-a" to "MATH"),
            nowEpochMillis = 100 * DAY,
            todayEpochDay = TODAY,
            examEpochDayBySubject = mapOf("MATH" to TODAY + 30),
            baseBudgetSeconds = 1200,
            p50DurationSeconds = 180,
        )
        assertEquals(ReviewCapacityPreview.EMPTY, preview)
    }

    private fun memory(
        unitId: String,
        lastReviewedAt: Long,
        stabilityDays: Double,
    ) = ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = stabilityDays,
        difficulty = 5.5,
        lastReviewedAtEpochMillis = lastReviewedAt,
        nextReviewAtEpochMillis = 100 * DAY,
        projectorVersion = "test",
        checkpointSequence = 0,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val TODAY = 100L
    }
}
