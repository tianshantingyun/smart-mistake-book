package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.ReviewPlanBundle
import com.tingyun.smartmistakebook.core.database.ReviewPlanRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * KD-68: the review read model must carry the unschedulable intake bucket
 * alongside the existing backlog count, without changing any other field.
 */
class StudyExperienceMappersIntakeTest {

    private fun emptyBundle(): ReviewPlanBundle = ReviewPlanBundle(
        plan = ReviewPlanRecord(
            reviewPlanId = "plan-1",
            learnerId = "learner-1",
            localDate = "2026-10-26",
            localDayEpochDay = 20_000L,
            timeZoneId = "Asia/Shanghai",
            timeBudgetSeconds = 900,
            planningAtEpochMillis = 1_000L,
            status = StudyDbValue.ReviewStatus.PLANNED,
            plannerVersion = "test-planner",
            projectionCheckpoint = 0L,
            planFingerprint = "fingerprint-1",
            planRevision = 1,
            createdAtEpochMillis = 1_000L,
        ),
        queue = emptyList(),
        activeSession = null,
    )

    @Test
    fun `overview carries the unschedulable intake count next to the backlog count`() {
        val overview = emptyBundle().toOverview(
            completedReviewDays = emptyList(),
            currentLocalDay = 20_000L,
            intakeBacklogCount = 4,
            intakeMedianEstimateSeconds = 90,
            intakeUnschedulableCount = 1,
        )
        assertEquals(4, overview.intakeBacklogCount)
        assertEquals(90, overview.intakeMedianEstimateSeconds)
        assertEquals(1, overview.intakeUnschedulableCount)
        // The unschedulable bucket is a subset, not an alternative backlog count.
        assertEquals(0, overview.scheduledCount)
    }

    @Test
    fun `overview without unschedulable items keeps the legacy shape`() {
        val overview = emptyBundle().toOverview(
            completedReviewDays = emptyList(),
            currentLocalDay = 20_000L,
            intakeBacklogCount = 3,
            intakeMedianEstimateSeconds = 90,
            intakeUnschedulableCount = 0,
        )
        assertEquals(3, overview.intakeBacklogCount)
        assertEquals(0, overview.intakeUnschedulableCount)
    }
}
