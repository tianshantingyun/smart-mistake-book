package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FSRS 遗忘曲线（spec mastery-scheduling §2.20）。
 *
 * KF-11 / 批次 3 收口（2026-10-01）：legacy 指数基线与 `ForgettingCurveAlgorithm` 已删除，
 * 本文件只锁 FSRS 语义——0.9-at-S 契约、整日下取整、时间回拨夹取、逆函数整日化。
 */
class ForgettingCurveTest {
    private var now = 0L
    private val curve = ForgettingCurve(EpochMillisClock { now })

    @Test
    fun `retention is ninety percent after one stability interval`() {
        val state = memory(stabilityDays = 4.0, lastReviewedAt = DAY_MILLIS)
        now = DAY_MILLIS * 5

        assertEquals(0.9, curve.retentionNow(state), 1e-9)
    }

    @Test
    fun `time rollback is clamped instead of increasing retention beyond one`() {
        val state = memory(stabilityDays = 2.0, lastReviewedAt = DAY_MILLIS * 5)
        now = DAY_MILLIS

        assertEquals(1.0, curve.retentionNow(state), 0.0)
    }

    @Test
    fun `retention floors elapsed to whole days and decays monotonically`() {
        val state = memory(stabilityDays = 3.0, lastReviewedAt = 0L)
        now = DAY_MILLIS / 2

        // py-fsrs floors elapsed time to whole days: half a day past the last
        // review is still day zero, so retention stays at one.
        assertEquals(1.0, curve.retentionNow(state), 0.0)
        now = DAY_MILLIS * 30
        val later = curve.retentionNow(state)
        now = DAY_MILLIS * 300
        val muchLater = curve.retentionNow(state)

        assertTrue(later > muchLater)
        assertTrue(muchLater > 0.0)
    }

    @Test
    fun `interval inverse at default retention rounds to whole days of stability`() {
        val stabilityDays = 13.7
        val reviewedAt = DAY_MILLIS * 2
        val dueAt = ForgettingCurve().reviewAtTargetRetention(
            reviewedAtEpochMillis = reviewedAt,
            stabilityDays = stabilityDays,
        )

        assertEquals(reviewedAt + (DAY_MILLIS * kotlin.math.round(stabilityDays)).toLong(), dueAt)
    }

    private fun memory(stabilityDays: Double, lastReviewedAt: Long) = ProblemMemoryState(
        practiceUnitId = "unit-1",
        stabilityDays = stabilityDays,
        difficulty = 5.5,
        lastReviewedAtEpochMillis = lastReviewedAt,
        nextReviewAtEpochMillis = lastReviewedAt,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 1,
    )

    private companion object {
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS
    }
}
