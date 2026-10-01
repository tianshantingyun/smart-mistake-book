package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionSignalTest {

    @Test
    fun `first switch is absorbed and clean attempts keep full weight`() {
        assertEquals(1.0, AttentionSignal.attentionFactor(0, 0), 0.0)
        assertEquals(1.0, AttentionSignal.attentionFactor(1, 0), 0.0)
    }

    @Test
    fun `extra switches and away time discount with a floor`() {
        val twoSwitches = AttentionSignal.attentionFactor(2, 0)
        assertEquals(1.0 - AttentionSignal.PER_EXTRA_SWITCH_PENALTY, twoSwitches, 1e-9)

        val withAway = AttentionSignal.attentionFactor(2, 30_000)
        assertEquals(
            1.0 - AttentionSignal.PER_EXTRA_SWITCH_PENALTY - AttentionSignal.PER_AWAY_GRADE_PENALTY,
            withAway,
            1e-9,
        )

        val floored = AttentionSignal.attentionFactor(12, 600_000)
        assertEquals(AttentionSignal.FLOOR, floored, 1e-9)
    }

    @Test
    fun `avoidance signal needs repeated switches and a poor grade`() {
        // P9：形参已是 FsrsRating（基址进类型）——PoorGrade 四格（AGAIN|HARD 命中、
        // GOOD|EASY 不命中）都在本用例内。
        assertTrue(AttentionSignal.isAvoidanceSignal(switchCount = 2, rating = FsrsRating.AGAIN))
        assertTrue(AttentionSignal.isAvoidanceSignal(switchCount = 3, rating = FsrsRating.HARD))
        assertFalse(AttentionSignal.isAvoidanceSignal(switchCount = 1, rating = FsrsRating.AGAIN))
        assertFalse(AttentionSignal.isAvoidanceSignal(switchCount = 2, rating = FsrsRating.GOOD))
        assertFalse(AttentionSignal.isAvoidanceSignal(switchCount = 2, rating = FsrsRating.EASY))
    }
}
