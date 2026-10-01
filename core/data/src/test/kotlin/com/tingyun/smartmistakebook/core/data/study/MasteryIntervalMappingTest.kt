package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.MasteryEstimateMath
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P8（批次 2 规格 §1.4 锚 3）的读面口径不变量：summary 的保守分与区间上界必须与
 * **同一次** `MasteryEstimateMath.interval(s, f)` 一致——读面重算与投影落库将来若分叉
 * （有人改了一处公式），此用例当场红。
 */
class MasteryIntervalMappingTest {

    @Test
    fun `summary conservative score and interval upper come from the same s over f estimate`() {
        val success = 8.0
        val failure = 0.0
        val interval = MasteryEstimateMath.interval(success, failure)

        val snapshot = LearnerSnapshot.empty("learner-1", projectorVersion = "test-projector").copy(
            knowledgeMasteryStates = mapOf(
                "kc-a" to KnowledgeMasteryState(
                    knowledgeNodeId = "kc-a",
                    masteryScore = MasteryEstimateMath.pointEstimate(success, failure),
                    conservativeMasteryScore = interval.lower,
                    evidenceMass = success + failure,
                    successWeight = success,
                    failureWeight = failure,
                    memoryStabilityDays = 12.5,
                    memoryDifficulty = 6.0,
                    lastAttemptAtEpochMillis = 1_000,
                    lastAttemptStudyDayEpochDay = 1,
                    status = MasteryStatus.LEARNING,
                    calibrationSupport = CalibrationSupport.UNKNOWN,
                    projectorVersion = "test-projector",
                    checkpointSequence = 0,
                ),
            ),
        )

        val summary = snapshot.toProfileOverview(
            resolvedKnowledgeContexts = emptyMap(),
            fallbackKnowledgeNames = emptyMap(),
        ).weaknesses.single()

        assertEquals(interval.lower, summary.conservativeMasteryScore, 1e-12)
        assertEquals(interval.upper, requireNotNull(summary.masteryIntervalUpper), 1e-12)
        assertEquals(12.5, requireNotNull(summary.memoryStabilityDays), 0.0)
    }

    @Test
    fun `a state without evidence carries no interval upper`() {
        val snapshot = LearnerSnapshot.empty("learner-1", projectorVersion = "test-projector").copy(
            knowledgeMasteryStates = mapOf(
                "kc-b" to KnowledgeMasteryState(
                    knowledgeNodeId = "kc-b",
                    masteryScore = 0.5,
                    conservativeMasteryScore = 0.5,
                    evidenceMass = 0.0,
                    successWeight = 0.0,
                    failureWeight = 0.0,
                    status = MasteryStatus.UNKNOWN,
                    calibrationSupport = CalibrationSupport.UNKNOWN,
                    projectorVersion = "test-projector",
                    checkpointSequence = 0,
                ),
            ),
        )

        val summary = snapshot.toProfileOverview(emptyMap(), emptyMap()).weaknesses.single()
        assertEquals(null, summary.masteryIntervalUpper)
        assertEquals(null, summary.memoryStabilityDays)
    }
}
