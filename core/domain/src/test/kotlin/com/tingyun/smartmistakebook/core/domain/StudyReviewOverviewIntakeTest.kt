package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * KD-68 read-surface invariant: the snapshot's unschedulable intake count is a
 * subset of the intake backlog and defaults to zero for every pre-existing
 * construction site.
 */
class StudyReviewOverviewIntakeTest {

    @Test
    fun `intake unschedulable count is exposed as a subset of the backlog`() {
        val overview = StudyReviewOverview(
            intakeBacklogCount = 5,
            intakeUnschedulableCount = 2,
        )
        assertEquals(5, overview.intakeBacklogCount)
        assertEquals(2, overview.intakeUnschedulableCount)
        // Default stays zero: no existing call site changes behavior.
        assertEquals(0, StudyReviewOverview().intakeUnschedulableCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `intake unschedulable count cannot exceed the backlog`() {
        StudyReviewOverview(intakeBacklogCount = 1, intakeUnschedulableCount = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `intake unschedulable count cannot be negative`() {
        StudyReviewOverview(intakeUnschedulableCount = -1)
    }
}
