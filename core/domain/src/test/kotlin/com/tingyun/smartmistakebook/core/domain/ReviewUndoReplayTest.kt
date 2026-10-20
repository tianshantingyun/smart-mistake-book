package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-05：attempt 级撤销（VOID）的**事件溯源语义**。
 *
 * 消灭的失败（研究结论 Q1/Q4）：
 * - 把 VOID 写成 `weight=0` 的 replacement 在本仓不是 no-op：`applyAttempt` 先
 *   `projectMemory` 才在 weight==0 返回，`ANSWER_REVEALED` 会被记成一次真实 lapse；
 * - 用"追加反向评分"或"改权重到零"表达撤销，会让记忆卡继续推进（FSRS 无逆函数）；
 * - 删除/重编号 attempt 会破坏 presentation 的 responseOrdinal 因果。
 *
 * 本用例钉住：VOID 保留 attempt 行/序号/曝光事实，但记忆卡与掌握度完全排除其学习效果；
 * REPLACE 仍在**原账本位置/时间**生效；零权重非哨兵载荷仍按"看答案"推进。
 */
class ReviewUndoReplayTest {
    private val projector = LearningProjector()

    @Test
    fun voidExcludesLearningEffectButKeepsAttemptAndOrdinalFacts() {
        val attempt = attempt("attempt-1", sequence = 1, ordinal = 1, evidence = assistedPositive())
        val void = AttemptCorrection.voidOf(
            correctionId = "correction-void-1",
            attemptId = attempt.attemptId,
            reasonMarkdown = "撤销误点的自评",
            occurredAtEpochMillis = 2_000,
            eventSequence = 2,
        )

        val result = projector.replay(LEARNER_ID, listOf(attempt, void))

        assertTrue("VOID 的 attempt 仍是账本事实", attempt.attemptId in result.appliedAttemptIds)
        assertEquals(setOf(attempt.attemptId), result.voidedAttemptIds)
        assertEquals(setOf(attempt.attemptId), result.correctedAttemptIds)
        assertTrue(
            "VOID = 从有效证据集合排除：不得建卡",
            result.snapshot.problemMemoryStates.isEmpty(),
        )
        assertTrue(
            "VOID 不产生掌握度证据",
            result.snapshot.knowledgeMasteryStates.isEmpty(),
        )
        val applied = result.snapshot.appliedAttemptRecords.values.single()
        assertEquals(attempt.attemptId, applied.attemptId)
        assertEquals(1, applied.responseOrdinal)
        assertEquals(1L, applied.eventSequence)
    }

    @Test
    fun zeroWeightReplacementWithoutVoidSentinelStillAdvancesTheMemoryCard() {
        val attempt = attempt("attempt-1", sequence = 1, ordinal = 1, evidence = assistedPositive())
        // 与 VOID 的 schema 载荷逐位同形，但没有 VOID 前缀：语义仍是"看答案"。
        val revealReplacement = AttemptCorrection(
            correctionId = "correction-reveal-1",
            attemptId = attempt.attemptId,
            replacementEvidence = LearningEvidence(
                direction = LearningEvidenceDirection.NONE,
                weight = 0.0,
                reason = LearningEvidenceReason.ANSWER_REVEALED,
            ),
            replacementMemoryOutcome = ProblemMemoryOutcome.ANSWER_REVEALED,
            reasonMarkdown = "学生看了答案",
            occurredAtEpochMillis = 2_000,
            eventSequence = 2,
        )

        val result = projector.replay(LEARNER_ID, listOf(attempt, revealReplacement))

        assertTrue(result.voidedAttemptIds.isEmpty())
        val memory = result.snapshot.problemMemoryStates.getValue(UNIT_ID)
        assertEquals("零权重载荷仍是真实的揭示：记一次 lapse", 1, memory.lapseCount)
        assertEquals(1, memory.answerRevealCount)
        assertEquals(
            "替代/更正挂回原作答时间，不把更正日当新练习日",
            1_000L,
            memory.lastReviewedAtEpochMillis,
        )
        assertEquals(LearningEvidenceReason.ANSWER_REVEALED.name, memory.lastEvidenceReason)
    }

    @Test
    fun voidedAttemptDoesNotPoisonTheReanswerOnTheSamePresentation() {
        val first = attempt(
            id = "attempt-1",
            sequence = 1,
            ordinal = 1,
            evidence = assistedPositive(),
            occurredAt = 1_000,
        )
        val void = AttemptCorrection.voidOf(
            correctionId = "correction-void-1",
            attemptId = first.attemptId,
            reasonMarkdown = "撤销后重评",
            occurredAtEpochMillis = 2_000,
            eventSequence = 2,
        )
        val reanswer = attempt(
            id = "attempt-2",
            sequence = 3,
            ordinal = 2,
            evidence = assistedGood(),
            occurredAt = 3_000,
        )

        val afterUndo = projector.replay(LEARNER_ID, listOf(first, void, reanswer))
        // 对照组：这道题从未有过第一次作答，只有同一次重评（换 presentation 以满足序号从 1 起）。
        val neverHappened = projector.replay(
            LEARNER_ID,
            listOf(
                attempt(
                    id = "baseline-attempt",
                    sequence = 1,
                    ordinal = 1,
                    evidence = assistedGood(),
                    occurredAt = 3_000,
                    presentationId = "presentation-baseline",
                ),
            ),
        )

        assertEquals(
            "VOID 后重评 == 第一次作答从未发生（记忆卡口径）",
            neverHappened.snapshot.problemMemoryStates.mapValues { it.value.copy(checkpointSequence = 0) },
            afterUndo.snapshot.problemMemoryStates.mapValues { it.value.copy(checkpointSequence = 0) },
        )
        val memory = afterUndo.snapshot.problemMemoryStates.getValue(UNIT_ID)
        assertEquals(3_000L, memory.lastReviewedAtEpochMillis)
        assertEquals(0, memory.lapseCount)
        assertEquals(1, memory.assistedCorrectCount)
        assertEquals(
            "序号事实仍连续：第 1、2 次响应都留在账本里",
            2,
            afterUndo.snapshot.appliedAttemptRecords.size,
        )
    }

    @Test
    fun voidKeepsTheAnswerRevealExposureAndItsPenalty() {
        val reveal = AnswerRevealOutcome(
            outcomeId = "reveal-1",
            presentationId = PRESENTATION_ID,
            assessmentSnapshot = assessmentSnapshot("attempt-1"),
            occurredAtEpochMillis = 1_000,
            studyDay = StudyDayContext(20_000, ZONE, 480),
            eventSequence = 1,
        )
        val attempt = attempt(
            id = "attempt-1",
            sequence = 2,
            ordinal = 1,
            evidence = assistedPositive(),
            occurredAt = 2_000,
        )
        val void = AttemptCorrection.voidOf(
            correctionId = "correction-void-1",
            attemptId = attempt.attemptId,
            reasonMarkdown = "撤销这次作答",
            occurredAtEpochMillis = 3_000,
            eventSequence = 3,
        )

        val withVoid = projector.replay(LEARNER_ID, listOf(reveal, attempt, void))
        val revealOnly = projector.replay(LEARNER_ID, listOf(reveal))

        assertEquals(
            "撤销评分不撤销已发生的答案曝光",
            revealOnly.snapshot.problemMemoryStates.mapValues { it.value.copy(checkpointSequence = 0) },
            withVoid.snapshot.problemMemoryStates.mapValues { it.value.copy(checkpointSequence = 0) },
        )
        assertEquals(setOf("reveal-1"), withVoid.appliedAnswerRevealOutcomeIds)
        assertEquals(1, withVoid.snapshot.problemMemoryStates.getValue(UNIT_ID).answerRevealCount)
    }

    @Test
    fun aLaterReplacementRestoresTheAttemptAtItsOriginalPosition() {
        val attempt = attempt(
            id = "attempt-1",
            sequence = 1,
            ordinal = 1,
            evidence = independentPositive(),
            occurredAt = 1_000,
        )
        val void = AttemptCorrection.voidOf(
            correctionId = "correction-void-1",
            attemptId = attempt.attemptId,
            reasonMarkdown = "先撤销",
            occurredAtEpochMillis = 2_000,
            eventSequence = 2,
        )
        val replacement = AttemptCorrection(
            correctionId = "correction-replace-1",
            attemptId = attempt.attemptId,
            replacementEvidence = assistedGood(),
            replacementMemoryOutcome = ProblemMemoryOutcome.ASSISTED_RECALL,
            reasonMarkdown = "改判为学生自评成功",
            occurredAtEpochMillis = 9_000,
            eventSequence = 3,
        )

        val corrected = projector.replay(LEARNER_ID, listOf(attempt, void, replacement))
        val positional = projector.replay(
            LEARNER_ID,
            listOf(
                attempt(
                    id = "attempt-1",
                    sequence = 1,
                    ordinal = 1,
                    evidence = assistedGood(),
                    occurredAt = 1_000,
                ),
            ),
        )

        assertTrue("最终有效更正是 REPLACE，不再标记为 VOID", corrected.voidedAttemptIds.isEmpty())
        assertEquals(
            positional.snapshot.problemMemoryStates.mapValues { it.value.copy(checkpointSequence = 0) },
            corrected.snapshot.problemMemoryStates.mapValues { it.value.copy(checkpointSequence = 0) },
        )
        assertFalse(
            "替代效果挂回原作答时间，不把修复日当成新练习日",
            corrected.snapshot.problemMemoryStates.getValue(UNIT_ID).lastReviewedAtEpochMillis == 9_000L,
        )
        assertEquals(1_000L, corrected.snapshot.problemMemoryStates.getValue(UNIT_ID).lastReviewedAtEpochMillis)
    }

    @Test
    fun voidSentinelCannotSmuggleRealEvidenceAndOnlyThePrefixMarksVoid() {
        val forged = runCatching {
            AttemptCorrection(
                correctionId = "correction-forged",
                attemptId = "attempt-1",
                replacementEvidence = assistedPositive(),
                replacementMemoryOutcome = ProblemMemoryOutcome.ASSISTED_RECALL,
                reasonMarkdown = AttemptCorrection.VOID_REASON_PREFIX + " 其实是一次真实作答",
                occurredAtEpochMillis = 1_000,
                eventSequence = 2,
            )
        }
        assertTrue(
            "带 VOID 前缀却携带真实证据必须被构造期拒绝",
            forged.exceptionOrNull() is IllegalArgumentException,
        )

        val genuine = AttemptCorrection.voidOf(
            correctionId = "correction-void",
            attemptId = "attempt-1",
            reasonMarkdown = "撤销",
            occurredAtEpochMillis = 1_000,
            eventSequence = 2,
        )
        assertTrue(genuine.isVoid)
        assertEquals(LearningEvidenceDirection.NONE, genuine.replacementEvidence.direction)
        assertEquals(0.0, genuine.replacementEvidence.weight, 0.0)
        assertFalse(
            "前缀是唯一语义开关：schema 同形但没有前缀的载荷仍按「看答案」处理",
            genuine.copy(reasonMarkdown = "学生看了答案").isVoid,
        )
    }

    // ---- fixtures ----

    private fun attempt(
        id: String,
        sequence: Long,
        ordinal: Int,
        evidence: LearningEvidence,
        occurredAt: Long = sequence * 1_000L,
        presentationId: String = PRESENTATION_ID,
    ) = Attempt(
        attemptId = id,
        presentationId = presentationId,
        responseOrdinal = ordinal,
        assessmentSnapshot = assessmentSnapshot(id),
        evidence = evidence,
        problemMemoryOutcome = when {
            evidence.direction == LearningEvidenceDirection.POSITIVE && evidence.isIndependent ->
                ProblemMemoryOutcome.INDEPENDENT_RECALL
            evidence.direction == LearningEvidenceDirection.POSITIVE ->
                ProblemMemoryOutcome.ASSISTED_RECALL
            evidence.direction == LearningEvidenceDirection.NEGATIVE ->
                ProblemMemoryOutcome.RETRIEVAL_FAILURE
            else -> ProblemMemoryOutcome.ANSWER_REVEALED
        },
        occurredAtEpochMillis = occurredAt,
        durationSeconds = 30,
        studyDay = StudyDayContext(20_000, ZONE, 480),
        eventSequence = sequence,
    )

    private fun assessmentSnapshot(id: String) = AssessmentEvidenceSnapshot(
        snapshotId = "snapshot-$id",
        assessmentItemId = "item-$id",
        practiceUnitId = UNIT_ID,
        problemRevisionId = "revision-1",
        answerSpecId = "answer-1",
        itemFamilyId = "family-1",
        sourceBundleId = null,
        taxonomyVersion = "taxonomy-v1",
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            CalibrationSupport.SUPPORTED,
            "calibration-source",
            "calibration-v1",
            0,
            100_000,
        ),
        attributions = listOf(
            KnowledgeEvidenceAttribution(
                bindingId = "binding-1",
                knowledgeNodeId = KNOWLEDGE_ID,
                weight = 1.0,
                basisRevisionId = "revision-1",
                taxonomyVersion = "taxonomy-v1",
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        ),
        capturedAtEpochMillis = 0,
    )

    private fun independentPositive() = LearningEvidence(
        LearningEvidenceDirection.POSITIVE,
        1.0,
        LearningEvidenceReason.INDEPENDENT_CORRECT,
    )

    private fun assistedPositive() = LearningEvidence(
        LearningEvidenceDirection.POSITIVE,
        0.8,
        LearningEvidenceReason.CORRECT_AFTER_HINT,
    )

    private fun assistedGood() = LearningEvidence(
        LearningEvidenceDirection.POSITIVE,
        0.8,
        LearningEvidenceReason.SELF_REPORTED_RECALL,
    )

    private companion object {
        const val LEARNER_ID = "learner-1"
        const val PRESENTATION_ID = "presentation-1"
        const val UNIT_ID = "unit-1"
        const val KNOWLEDGE_ID = "kc-a"
        const val ZONE = "Asia/Shanghai"
    }
}
