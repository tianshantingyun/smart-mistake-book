package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.StudyDayMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * Behavior tests for the FSRS-6 projection (spec mastery-scheduling §2.4,
 * §2.15, §2.16, §2.10) and the legacy kill-switch equivalence.
 */
class FsrsProjectionBehaviorTest {

    private val projector = LearningProjector()

    @Test
    fun `first review seeds initial stability and difficulty for its rating`() {
        val result = projector.project(
            LearnerSnapshot.empty("learner-1"),
            listOf(attempt("a-1", 1, easyEvidence())),
            1,
        )
        val memory = result.snapshot.problemMemoryStates.getValue("unit-1")

        assertEquals(FsrsScheduleMath.initialStability(FsrsRating.GOOD), memory.stabilityDays, 1e-9)
        assertEquals(
            FsrsScheduleMath.clampDifficulty(FsrsScheduleMath.initialDifficulty(FsrsRating.GOOD)),
            memory.difficulty,
            1e-9,
        )
        assertEquals(0, memory.consecutiveCrossDaySuccess)
        assertEquals(LearningEvidenceReason.INDEPENDENT_CORRECT.name, memory.lastEvidenceReason)
        assertEquals(LearningEvidenceDirection.POSITIVE.name, memory.lastEvidenceDirection)
    }

    @Test
    fun `same day review takes the short term branch and never grows stability for good`() {
        val first = projector.project(
            LearnerSnapshot.empty("learner-1"),
            listOf(attempt("a-1", 1, easyEvidence(), occurredAt = DAY_MILLIS)),
            1,
        ).snapshot
        val firstStability = first.problemMemoryStates.getValue("unit-1").stabilityDays

        val second = projector.project(
            first,
            listOf(attempt("a-2", 2, easyEvidence(), occurredAt = DAY_MILLIS + 60_000)),
            2,
        )
        val memory = second.snapshot.problemMemoryStates.getValue("unit-1")

        assertEquals(
            FsrsScheduleMath.shortTermStability(firstStability, FsrsRating.GOOD),
            memory.stabilityDays,
            1e-9,
        )
        // Same-day repeats never reach the cross-day long-run branch.
        assertEquals(0, memory.consecutiveCrossDaySuccess)
    }

    @Test
    fun `cross midnight review under 24 wall clock hours still counts as a new day`() {
        val first = projector.project(
            LearnerSnapshot.empty("learner-1"),
            listOf(attempt("a-1", 1, easyEvidence(), occurredAt = DAY_MILLIS)),
            1,
        ).snapshot
        val firstStability = first.problemMemoryStates.getValue("unit-1").stabilityDays

        // Second review is only 30 wall-clock minutes later (DAY_MILLIS + 60_000), but its
        // learner-local calendar day is the next day (epoch day 2) — a cross-midnight review that
        // must take the long-run branch, not the same-day short-term branch.
        val crossingMidnight = attempt("a-2", 2, easyEvidence(), occurredAt = DAY_MILLIS + 60_000)
            .copy(studyDay = StudyDayContext(epochDay = 2, timeZoneId = "Asia/Shanghai", utcOffsetMinutes = 480))
        val second = projector.project(first, listOf(crossingMidnight), 2)
        val memory = second.snapshot.problemMemoryStates.getValue("unit-1")

        // Cross-day success must advance the streak (same-day would keep it at 1).
        // P5（批次 3）：首答不计跨日连击——首答后为 0，跨日成功累计从第二次起。
        assertEquals(1, memory.consecutiveCrossDaySuccess)
        // The long-run recall branch grows stability past the short-term floor; the same-day
        // branch for Good would leave it at the short-term value (which equals the seed here).
        assertTrue(
            "cross-midnight Good must grow stability via the long-run branch, got ${memory.stabilityDays}",
            memory.stabilityDays > firstStability,
        )
    }

    @Test
    fun `cross day again counts toward the leech streak and lowers stability`() {
        val seeded = seededCrossDay()
        val lastReviewedAt = seeded.memory.lastReviewedAtEpochMillis
        val lapseAt = lastReviewedAt + 3 * DAY_MILLIS
        val result = projector.project(
            seeded.snapshot,
            listOf(
                attempt(
                    "a-lapse",
                    4,
                    wrongEvidence(),
                    occurredAt = lapseAt,
                ),
            ),
            4,
        )
        val memory = result.snapshot.problemMemoryStates.getValue("unit-1")

        assertEquals(1, memory.consecutiveCrossDayAgain)
        assertTrue(
            memory.stabilityDays <=
                seeded.memory.stabilityDays / exp(
                    FsrsScheduleMath.DEFAULT_PARAMETERS[17] * FsrsScheduleMath.DEFAULT_PARAMETERS[18],
                ) + 1e-9,
        )
    }

    @Test
    fun `cross day again resets the graduation success streak`() {
        // Seed one cross-day success, then lapse on the next cross-day review:
        // the success streak must reset to zero — two further successes must
        // NOT read as "three in a row" (spec §2.10 counts consecutive
        // cross-day successes; a lapse breaks the run).
        val seeded = seededCrossDay()
        val lapseAt = seeded.memory.lastReviewedAtEpochMillis + 3 * DAY_MILLIS
        val lapsed = projector.project(
            seeded.snapshot,
            listOf(attempt("a-lapse", 4, wrongEvidence(), occurredAt = lapseAt)),
            4,
        )
        val afterLapse = lapsed.snapshot.problemMemoryStates.getValue("unit-1")
        assertEquals("a cross-day Again must clear the success streak", 0, afterLapse.consecutiveCrossDaySuccess)
        assertEquals(1, afterLapse.consecutiveCrossDayAgain)

        // Drive the card through two further cross-day successes (each review
        // explicitly on a fresh calendar day after the lapse). If the streak
        // had not been reset by the lapse, the second success would already
        // read as three in a row and trigger graduation.
        val lapseEpochDay = lapseAt / DAY_MILLIS
        var snapshot = lapsed.snapshot
        for (index in 1..2) {
            val occurredAt = lapseAt + index * DAY_MILLIS
            val crossDayAttempt = attempt(
                "a-recover-$index",
                4L + index,
                easyEvidence(),
                occurredAt = occurredAt,
            ).copy(
                studyDay = StudyDayContext(
                    epochDay = lapseEpochDay + index,
                    timeZoneId = "UTC",
                    utcOffsetMinutes = 0,
                ),
            )
            val result = projector.project(snapshot, listOf(crossDayAttempt), 4L + index)
            snapshot = result.snapshot
        }
        val recovered = snapshot.problemMemoryStates.getValue("unit-1")
        // Exactly two successes after the lapse; graduation needs three.
        assertEquals(2, recovered.consecutiveCrossDaySuccess)
        assertEquals(0, recovered.consecutiveCrossDayAgain)
    }

    @Test
    fun `leech freezes difficulty at its ceiling`() {
        val leeched = seededCrossDay().memory.copy(
            lapseCount = ProblemMemoryState.LEECH_LAPSE_THRESHOLD,
            consecutiveCrossDayAgain = ProblemMemoryState.LEECH_AGAIN_STREAK,
            difficulty = 8.0,
        )
        val snapshot = LearnerSnapshot(
            learnerId = "learner-1",
            problemMemoryStates = mapOf("unit-1" to leeched),
            checkpoint = ProjectionCheckpoint(10, LearningProjector.VERSION, 10 * DAY_MILLIS),
            generatedAtEpochMillis = 10 * DAY_MILLIS,
        )

        val result = projector.project(
            snapshot,
            listOf(
                attempt(
                    "a-lapse",
                    11,
                    wrongEvidence(),
                    occurredAt = leeched.lastReviewedAtEpochMillis + 3 * DAY_MILLIS,
                ),
            ),
            11,
        )
        val memory = result.snapshot.problemMemoryStates.getValue("unit-1")

        assertTrue(memory.isLeeched)
        // Spec 2.16: a leeched card's difficulty may not climb further, even
        // though the Again rating would normally push it up.
        assertEquals(8.0, memory.difficulty, 1e-9)
    }

    @Test
    fun `three cross day successes with a ninety day interval schedule maintenance`() {
        var snapshot = LearnerSnapshot.empty("learner-1")
        var sequence = 0L
        // Drive the card through enough successful cross-day reviews for a
        // ninety-day interval; the graduation override must then kick in.
        // W2-4/KF-25：夹具用贴近真实日期的时间基（04:00 日界下纪元附近的时刻会落到负日序，
        // 而 ProblemMemoryState 的日序不变量要求 ≥ 0）。
        var nextAt = BASE_AT_MILLIS
        for (index in 1..14) {
            sequence += 1
            val occurredAt = nextAt
            val result = projector.project(
                snapshot,
                listOf(attempt("a-$index", sequence, easyEvidence(), occurredAt = occurredAt)),
                sequence,
            )
            snapshot = result.snapshot
            val memory = snapshot.problemMemoryStates.getValue("unit-1")
            nextAt = memory.nextReviewAtEpochMillis
            if (memory.consecutiveCrossDaySuccess >= 3) {
                val regularInterval = FsrsScheduleMath.intervalDays(
                    memory.stabilityDays,
                    FsrsMemoryUpdateModel.DEFAULT_DESIRED_RETENTION,
                    -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
                )
                if (regularInterval >= 90) {
                    val maintenanceInterval = FsrsScheduleMath.intervalDays(
                        memory.stabilityDays,
                        LearningProjector.GRADUATION_TARGET_RETENTION,
                        -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
                    )
                    val scheduledInterval =
                        (memory.nextReviewAtEpochMillis - occurredAt) / DAY_MILLIS
                    assertEquals(maintenanceInterval.toLong(), scheduledInterval)
                    assertTrue(maintenanceInterval >= regularInterval)
                    return
                }
            }
        }
        throw AssertionError("graduation never scheduled a maintenance interval")
    }

    private fun seededCrossDay(): LearningProjectionResult {
        var snapshot = LearnerSnapshot.empty("learner-1")
        // W2-4/KF-25：夹具用贴近真实日期的时间基（04:00 日界下纪元附近的时刻会落到负日序，
        // 而 ProblemMemoryState 的日序不变量要求 ≥ 0）。
        var nextAt = BASE_AT_MILLIS
        for (index in 1..3) {
            val result = projector.project(
                snapshot,
                listOf(attempt("a-$index", index.toLong(), easyEvidence(), occurredAt = nextAt)),
                index.toLong(),
            )
            snapshot = result.snapshot
            nextAt = snapshot.problemMemoryStates.getValue("unit-1").nextReviewAtEpochMillis
        }
        return LearningProjectionResult(snapshot, emptySet(), emptySet(), emptySet())
    }

    private val LearningProjectionResult.memory
        get() = snapshot.problemMemoryStates.getValue("unit-1")

    private fun attempt(
        id: String,
        sequence: Long,
        evidence: LearningEvidence = easyEvidence(),
        occurredAt: Long = sequence * DAY_MILLIS,
    ): Attempt {
        val outcome = when {
            evidence.direction == LearningEvidenceDirection.NONE -> ProblemMemoryOutcome.ANSWER_REVEALED
            evidence.signedWeight > 0 -> ProblemMemoryOutcome.INDEPENDENT_RECALL
            else -> ProblemMemoryOutcome.RETRIEVAL_FAILURE
        }
        return Attempt(
            attemptId = id,
            presentationId = "presentation-$id",
            responseOrdinal = 1,
            assessmentSnapshot = AssessmentEvidenceSnapshot(
                snapshotId = "snapshot-$id",
                assessmentItemId = "assessment-$id",
                practiceUnitId = "unit-1",
                problemRevisionId = "revision-1",
                answerSpecId = "answer-1",
                itemFamilyId = "family-$id",
                sourceBundleId = "source-$id",
                taxonomyVersion = "taxonomy-v1",
                verification = AssessmentSnapshotVerification.VERIFIED,
                calibration = CalibrationSnapshot(
                    CalibrationSupport.SUPPORTED,
                    "calibration-source",
                    "calibration-v1",
                    0,
                    400 * DAY_MILLIS,
                ),
                attributions = listOf(
                    KnowledgeEvidenceAttribution(
                        bindingId = "binding-$id",
                        knowledgeNodeId = "kc-a",
                        weight = 1.0,
                        basisRevisionId = "revision-1",
                        taxonomyVersion = "taxonomy-v1",
                        role = EvidenceAttributionRole.PRIMARY,
                        certainty = EvidenceAttributionCertainty.DIRECT,
                    ),
                ),
                capturedAtEpochMillis = 0,
            ),
            evidence = evidence,
            problemMemoryOutcome = outcome,
            occurredAtEpochMillis = occurredAt,
            durationSeconds = 60,
            // W2-4/KF-25：夹具的日序必须与生产写路径同源（`StudyDayMath`，04:00 日界）——
            // 手写 `occurredAt / DAY_MILLIS` 是旧的 00:00 口径，投影器按新口径推导上一复习日时
            // 会把同一学习日误判成跨日。
            studyDay = StudyDayContext(
                epochDay = StudyDayMath.localEpochDayOf(occurredAt, utcOffsetMinutes = 0),
                timeZoneId = "UTC",
                utcOffsetMinutes = 0,
            ),
            eventSequence = sequence,
        )
    }

    private fun easyEvidence(weight: Double = 1.0) = LearningEvidence(
        LearningEvidenceDirection.POSITIVE,
        weight,
        LearningEvidenceReason.INDEPENDENT_CORRECT,
    )

    private fun wrongEvidence() = LearningEvidence(
        LearningEvidenceDirection.NEGATIVE,
        1.0,
        LearningEvidenceReason.INDEPENDENT_INCORRECT,
    )

    private companion object {
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS

        /** W2-4/KF-25：夹具时间基——2026-01-05 09:00 UTC（远离纪元，04:00 学习日界下日序为正）。 */
        const val BASE_AT_MILLIS = 1_767_603_600_000L
    }
}
