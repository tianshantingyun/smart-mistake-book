package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AssessmentAssistanceEvent
import com.tingyun.smartmistakebook.core.model.AssessmentSubmissionContext
import com.tingyun.smartmistakebook.core.model.PersistedAssessmentAssistance
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorAssistanceKind
import com.tingyun.smartmistakebook.core.model.TutorChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryEvidencePolicyTest {
    private val item = TutorAssessmentItem(
        id = "item-1",
        stemMarkdown = "请选择正确答案。",
        choices = listOf(
            TutorChoice("a", "正确选项"),
            TutorChoice("b", "错误选项"),
        ),
        correctChoiceId = "a",
    )

    @Test
    fun `correct response after answer reveal contributes no mastery evidence`() {
        val reveal = AssessmentAssistanceEvent(
            eventId = "reveal-1",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.ANSWER_REVEAL,
            contentMarkdown = "正确选项是 A。",
            occurredAtEpochMillis = 10,
            eventSequence = 1,
        )
        val context = AssessmentSubmissionContext(
            assessmentItemId = "item-1",
            selectedChoiceId = "a",
            presentationId = "presentation-1",
            responseSequence = 2,
            persistedAssistance = listOf(PersistedAssessmentAssistance(reveal, 11)),
        )

        val decision = MasteryEvidencePolicy.evaluate(item, context)

        assertEquals(MasteryEvidenceKind.EXCLUDED, decision.kind)
        assertEquals(MasteryEvidenceReason.ANSWER_WAS_REVEALED, decision.reason)
        assertFalse(decision.contributesToMastery)
        assertFalse(decision.isIndependent)
        assertEquals(0.0, decision.signedWeight, 0.0)
    }

    @Test
    fun `correctness is derived from the assessment rather than supplied by caller`() {
        val context = AssessmentSubmissionContext(
            assessmentItemId = "item-1",
            selectedChoiceId = "b",
            presentationId = "presentation-1",
            responseSequence = 1,
        )

        val decision = MasteryEvidencePolicy.evaluate(item, context)

        assertEquals(MasteryEvidenceKind.INDEPENDENT, decision.kind)
        assertEquals(MasteryEvidenceReason.INCORRECT_RESPONSE, decision.reason)
        assertTrue(decision.contributesToMastery)
        assertTrue(decision.isIndependent)
        assertEquals(-1.0, decision.signedWeight, 0.0)
    }

    @Test
    fun `independent correct response is full positive evidence`() {
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                "item-1", "a", "presentation-1", responseSequence = 1,
            ),
        )

        assertEquals(MasteryEvidenceKind.INDEPENDENT, decision.kind)
        assertEquals(1.0, decision.signedWeight, 0.0)
        assertTrue(decision.contributesToMastery)
    }

    @Test
    fun `hinted correct response is discounted positive evidence`() {
        val hint = AssessmentAssistanceEvent(
            eventId = "hint-1",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.HINT,
            contentMarkdown = "先比较两个式子的结构。",
            occurredAtEpochMillis = 20,
            eventSequence = 1,
        )
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                assessmentItemId = "item-1",
                selectedChoiceId = "a",
                presentationId = "presentation-1",
                responseSequence = 2,
                persistedAssistance = listOf(PersistedAssessmentAssistance(hint, 21)),
            ),
        )

        assertEquals(MasteryEvidenceKind.ASSISTED, decision.kind)
        assertEquals(0.6, decision.signedWeight, 0.0)
        assertFalse(decision.isIndependent)
    }

    @Test
    fun `hinted incorrect response remains negative evidence`() {
        val hint = AssessmentAssistanceEvent(
            eventId = "hint-2",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.HINT,
            contentMarkdown = "先比较两个式子的结构。",
            occurredAtEpochMillis = 30,
            eventSequence = 1,
        )
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                assessmentItemId = "item-1",
                selectedChoiceId = "b",
                presentationId = "presentation-1",
                responseSequence = 2,
                persistedAssistance = listOf(PersistedAssessmentAssistance(hint, 31)),
            ),
        )

        assertEquals(MasteryEvidenceKind.ASSISTED, decision.kind)
        assertEquals(-0.9, decision.signedWeight, 0.0)
        assertTrue(decision.contributesToMastery)
    }

    @Test
    fun `answer reveal persisted after the response does not alter earlier response evidence`() {
        val revealAfterResponse = AssessmentAssistanceEvent(
            eventId = "reveal-after-response",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.ANSWER_REVEAL,
            contentMarkdown = "正确选项是 A。",
            occurredAtEpochMillis = 40,
            eventSequence = 3,
        )

        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                assessmentItemId = "item-1",
                selectedChoiceId = "a",
                presentationId = "presentation-1",
                responseSequence = 2,
                persistedAssistance = listOf(PersistedAssessmentAssistance(revealAfterResponse, 41)),
            ),
        )

        assertEquals(MasteryEvidenceKind.INDEPENDENT, decision.kind)
        assertTrue(decision.isIndependent)
    }

    @Test
    fun `assistance at the same sequence cannot contaminate the response`() {
        val sameSequenceReveal = AssessmentAssistanceEvent(
            eventId = "same-sequence-reveal",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.ANSWER_REVEAL,
            contentMarkdown = "这条事件不能与作答共享因果序号。",
            occurredAtEpochMillis = 40,
            eventSequence = 2,
        )

        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                assessmentItemId = "item-1",
                selectedChoiceId = "a",
                presentationId = "presentation-1",
                responseSequence = 2,
                persistedAssistance = listOf(PersistedAssessmentAssistance(sameSequenceReveal, 41)),
            ),
        )

        assertEquals(MasteryEvidenceKind.INDEPENDENT, decision.kind)
        assertTrue(decision.isIndependent)
    }

    @Test
    fun `second response without a hint is retry evidence and cannot be independent`() {
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                assessmentItemId = "item-1",
                selectedChoiceId = "a",
                presentationId = "presentation-1",
                responseSequence = 2,
                responseOrdinal = 2,
            ),
        )

        assertEquals(MasteryEvidenceKind.ASSISTED, decision.kind)
        assertEquals(MasteryEvidenceReason.CORRECT_ON_RETRY, decision.reason)
        assertFalse(decision.isIndependent)
    }

    @Test
    fun `incorrect response after answer reveal remains conservative negative evidence`() {
        val reveal = AssessmentAssistanceEvent(
            eventId = "reveal-before-wrong",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.ANSWER_REVEAL,
            contentMarkdown = "正确选项是 A。",
            occurredAtEpochMillis = 10,
            eventSequence = 1,
        )
        val decision = MasteryEvidencePolicy.evaluate(
            assessmentItem = item,
            context = AssessmentSubmissionContext(
                assessmentItemId = "item-1",
                selectedChoiceId = "b",
                presentationId = "presentation-1",
                responseSequence = 2,
                persistedAssistance = listOf(PersistedAssessmentAssistance(reveal, 11)),
            ),
        )

        assertEquals(MasteryEvidenceReason.INCORRECT_AFTER_REVEAL, decision.reason)
        assertEquals(-0.6, decision.signedWeight, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assistance from another presentation cannot contaminate this response`() {
        val foreignHint = AssessmentAssistanceEvent(
            eventId = "foreign-hint",
            assessmentItemId = "item-1",
            presentationId = "presentation-other",
            kind = TutorAssistanceKind.HINT,
            contentMarkdown = "跨呈现提示",
            occurredAtEpochMillis = 10,
            eventSequence = 1,
        )

        AssessmentSubmissionContext(
            assessmentItemId = "item-1",
            selectedChoiceId = "a",
            presentationId = "presentation-1",
            responseSequence = 2,
            persistedAssistance = listOf(PersistedAssessmentAssistance(foreignHint, 11)),
        )
    }

    /**
     * 先后顺序是**序号空间内的比较**，不是"有没有传进来"（W1-3/KF-02 的接线前提）。
     *
     * 落库的协助事件序号是账本的全局序号（`learning_sequence` 分配），所以传进
     * [AssessmentSubmissionContext] 的协助序号必须是**同一空间**的值、且
     * [AssessmentSubmissionContext.responseSequence] 要取"本次作答将占的序号"那一侧——
     * 把每呈现的小序号（`responseOrdinal`）塞进去会让任何已发生的协助都被过滤掉，
     * 分支永远不可达（这正是 KF-02 的失效形态）。
     */
    @Test
    fun `assistance ordered after the response is not consumed`() {
        val revealAfterResponse = AssessmentAssistanceEvent(
            eventId = "reveal-late",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.ANSWER_REVEAL,
            contentMarkdown = "作答之后才揭示",
            occurredAtEpochMillis = 10,
            eventSequence = 41,
        )
        val context = AssessmentSubmissionContext(
            assessmentItemId = "item-1",
            selectedChoiceId = "a",
            presentationId = "presentation-1",
            responseSequence = 40, // 作答发生在揭示之前（同一次账本序号空间）
            persistedAssistance = listOf(PersistedAssessmentAssistance(revealAfterResponse, 11)),
        )

        assertFalse("揭示在作答之后 → 不得当成本次作答的协助", context.answerWasRevealed)
        assertTrue("但它确实被传进来了（校验只管归属与去重）", context.persistedAssistance.isNotEmpty())
        assertEquals(MasteryEvidenceKind.INDEPENDENT, MasteryEvidencePolicy.evaluate(item, context).kind)
    }

    @Test
    fun `a reveal in the same ledger sequence space is consumed when the response follows it`() {
        val reveal = AssessmentAssistanceEvent(
            eventId = "reveal-early",
            assessmentItemId = "item-1",
            presentationId = "presentation-1",
            kind = TutorAssistanceKind.ANSWER_REVEAL,
            contentMarkdown = "先揭示、后作答",
            occurredAtEpochMillis = 10,
            eventSequence = 41,
        )
        val context = AssessmentSubmissionContext(
            assessmentItemId = "item-1",
            selectedChoiceId = "a",
            presentationId = "presentation-1",
            responseSequence = 42,
            persistedAssistance = listOf(PersistedAssessmentAssistance(reveal, 11)),
        )

        assertTrue(context.answerWasRevealed)
        assertEquals(MasteryEvidenceKind.EXCLUDED, MasteryEvidencePolicy.evaluate(item, context).kind)
    }
}
