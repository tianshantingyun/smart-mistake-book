package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseNodeCertainty
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorCauseExtractorTest {

    private val extractor = ErrorCauseExtractor()

    @Test
    fun `no student trace abstains instead of guessing a cause`() {
        assertNull(
            "没有学生自己产生的 trace 时，只能 abstain；禁止凭答案错推断",
            extractor.extract(input()),
        )
    }

    @Test
    fun `an invalid rule stated by the student is conceptual`() {
        val candidate = extractor.extract(
            input(
                traceSignals = listOf(
                    ErrorCauseTraceSignal.RuleClaim(
                        messageId = "message-1",
                        validity = StudentRuleValidity.INVALID,
                    ),
                ),
            ),
        )!!
        assertEquals(ErrorCause.CONCEPTUAL, candidate.evidence.cause)
        assertEquals(ErrorCauseDetail.CONCEPT_RULE_INVALID, candidate.evidence.detail)
        assertEquals(ErrorCauseChannel.TUTOR_TRACE, candidate.evidence.channel)
        assertEquals(ErrorCauseObservationStatus.PROPOSED, candidate.status)
        assertTrue(
            candidate.evidence.refs.any {
                it.kind == ErrorCauseEvidenceKind.TUTOR_MESSAGE && it.id == "message-1"
            },
        )
        assertEquals("error-cause-local-v1", candidate.extractorVersion)
    }

    @Test
    fun `an overgeneralized rule keeps the finer detail code`() {
        val candidate = extractor.extract(
            input(
                traceSignals = listOf(
                    ErrorCauseTraceSignal.RuleClaim(
                        messageId = "message-1",
                        validity = StudentRuleValidity.INVALID,
                        overgeneralized = true,
                    ),
                ),
            ),
        )!!
        assertEquals(ErrorCauseDetail.CONCEPT_OVERGENERALIZED, candidate.evidence.detail)
    }

    @Test
    fun `a valid rule with a wrong step is procedural and rules out the conceptual branch`() {
        val candidate = extractor.extract(
            input(
                traceSignals = listOf(
                    ErrorCauseTraceSignal.RuleClaim("rule-good", StudentRuleValidity.VALID),
                    ErrorCauseTraceSignal.StepClaim(
                        messageId = "message-2",
                        ruleValidity = StudentRuleValidity.VALID,
                        wrongStep = StudentStepKind.REGROUPING,
                    ),
                ),
            ),
        )!!
        assertEquals(ErrorCause.PROCEDURAL, candidate.evidence.cause)
        assertEquals(ErrorCauseDetail.PROC_REGROUPING, candidate.evidence.detail)
    }

    @Test
    fun `a read mismatch corrected after rereading is misidentification`() {
        val candidate = extractor.extract(
            input(
                traceSignals = listOf(
                    ErrorCauseTraceSignal.ReadClaim(
                        messageId = "message-3",
                        kind = StudentReadKind.SIGN,
                        correctedAfterReread = true,
                    ),
                ),
            ),
        )!!
        assertEquals(ErrorCause.MISIDENTIFICATION, candidate.evidence.cause)
        assertEquals(ErrorCauseDetail.READ_SIGN, candidate.evidence.detail)
    }

    @Test
    fun `a read mismatch without the correction evidence does not classify`() {
        assertNull(
            extractor.extract(
                input(
                    traceSignals = listOf(
                        ErrorCauseTraceSignal.ReadClaim(
                            messageId = "message-3",
                            kind = StudentReadKind.SIGN,
                            correctedAfterReread = false,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `the careless triple must hold in full`() {
        val triple = input(
            redo = redo(correct = true, independent = true, immediate = true),
            priorIndependentCorrectCount = 1,
        )
        val candidate = extractor.extract(triple)!!
        assertEquals(ErrorCause.CARELESS, candidate.evidence.cause)
        assertEquals(ErrorCauseChannel.REDO, candidate.evidence.channel)
        assertNull("冷启动期不得用 RT 阈值定细码", candidate.evidence.detail)

        assertNull("没有历史会 → 不能归 careless", extractor.extract(triple.copy(priorIndependentCorrectCount = 0)))
        assertNull("有提示 → 不能算会而失手", extractor.extract(triple.copy(hintCount = 1)))
        assertNull("看过答案 → 不能算会而失手", extractor.extract(triple.copy(revealedBeforeAnswer = true)))
        assertNull(
            "重做不独立 → 不能算会",
            extractor.extract(triple.copy(redo = redo(correct = true, independent = false, immediate = true))),
        )
        assertNull(
            "隔天重做可能只是记住答案",
            extractor.extract(triple.copy(redo = redo(correct = true, independent = true, immediate = false))),
        )
    }

    @Test
    fun `fast wrong needs thirty baseline samples before it changes the detail code`() {
        val base = input(
            redo = redo(correct = true, independent = true, immediate = true),
            priorIndependentCorrectCount = 2,
            durationSeconds = 3,
            personalBaselineP10Seconds = 10,
        )
        assertEquals(
            "基线不足 30 次时不得用 RT 阈值",
            null,
            extractor.extract(base.copy(personalBaselineSampleCount = 29))!!.evidence.detail,
        )
        assertEquals(
            ErrorCauseDetail.SLIP_FAST_WRONG,
            extractor.extract(base.copy(personalBaselineSampleCount = 30))!!.evidence.detail,
        )
        assertEquals(
            "用时高于 p10 时不能标快",
            null,
            extractor.extract(
                base.copy(
                    personalBaselineSampleCount = 30,
                    durationSeconds = 11,
                ),
            )!!.evidence.detail,
        )
    }

    @Test
    fun `self report alone never classifies but supports an existing trace candidate`() {
        assertNull(
            "自评不是测量，不能单独定类",
            extractor.extract(input(selfReport = ErrorCauseSelfReport("self-1", expectedCorrect = true))),
        )

        val candidate = extractor.extract(
            input(
                traceSignals = listOf(
                    ErrorCauseTraceSignal.RuleClaim("message-1", StudentRuleValidity.INVALID),
                ),
                selfReport = ErrorCauseSelfReport("self-1", expectedCorrect = true),
            ),
        )!!
        assertTrue(
            "自评可以作为支持引用，但不能变成主证据",
            candidate.evidence.refs.any { it.id == "self-1" },
        )
        assertEquals(ErrorCauseChannel.TUTOR_TRACE, candidate.evidence.channel)
    }

    @Test
    fun `a revision changed after the attempt abstains`() {
        assertNull(
            extractor.extract(
                input(
                    traceSignals = listOf(
                        ErrorCauseTraceSignal.RuleClaim("message-1", StudentRuleValidity.INVALID),
                    ),
                    revisionChangedAfterAttempt = true,
                ),
            ),
        )
    }

    @Test
    fun `node certainty is carried through without upgrading it`() {
        val candidate = extractor.extract(
            input(
                traceSignals = listOf(
                    ErrorCauseTraceSignal.RuleClaim("message-1", StudentRuleValidity.INVALID),
                ),
            ).copy(
                scope = scope.copy(knowledgeNodeId = "node-1"),
                knowledgeNodeCertainty = ErrorCauseNodeCertainty.INFERRED,
            ),
        )!!
        assertEquals(ErrorCauseNodeCertainty.INFERRED, candidate.knowledgeNodeCertainty)
    }

    private fun input(
        traceSignals: List<ErrorCauseTraceSignal> = emptyList(),
        selfReport: ErrorCauseSelfReport? = null,
        redo: ErrorCauseRedoOutcome? = null,
        priorIndependentCorrectCount: Int = 0,
        hintCount: Int = 0,
        revealedBeforeAnswer: Boolean = false,
        durationSeconds: Int? = null,
        personalBaselineP10Seconds: Int? = null,
        personalBaselineSampleCount: Int = 0,
        revisionChangedAfterAttempt: Boolean = false,
    ) = ErrorCauseExtractionInput(
        scope = scope,
        attemptId = "attempt-1",
        observedAtEpochMillis = 1_000,
        traceSignals = traceSignals,
        selfReport = selfReport,
        redo = redo,
        priorIndependentCorrectCount = priorIndependentCorrectCount,
        hintCount = hintCount,
        revealedBeforeAnswer = revealedBeforeAnswer,
        durationSeconds = durationSeconds,
        personalBaselineP10Seconds = personalBaselineP10Seconds,
        personalBaselineSampleCount = personalBaselineSampleCount,
        revisionChangedAfterAttempt = revisionChangedAfterAttempt,
    )

    private fun redo(correct: Boolean, independent: Boolean, immediate: Boolean) =
        ErrorCauseRedoOutcome(
            evidenceId = "redo-1",
            independent = independent,
            correct = correct,
            immediate = immediate,
            observedAtEpochMillis = 2_000,
        )

    private val scope = ErrorCauseScope(
        learnerId = "learner-1",
        practiceUnitId = "unit-1",
        problemRevisionId = "rev-1",
    )
}
