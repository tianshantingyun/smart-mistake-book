package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseCandidate
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidence
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseNodeCertainty
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorCauseGateTest {

    @Test
    fun `the production gate is closed by default`() {
        val gate = ErrorCauseGate()
        assertTrue(gate.isDefaultClosed)
        assertFalse(
            "0 条本项目标注样本时不得自动落账",
            gate.mayAutoBook(candidate(), corroborationCount = 99, retractedRecently = false),
        )
    }

    @Test
    fun `certified gate admits a non-careless candidate with enough corroboration`() {
        val gate = ErrorCauseGate(ErrorCauseGateState(certified = true))
        assertTrue(gate.mayAutoBook(candidate(), corroborationCount = 2, retractedRecently = false))
    }

    @Test
    fun `careless never auto-books even when the gate is certified`() {
        val gate = ErrorCauseGate(ErrorCauseGateState(certified = true))
        assertFalse(
            "careless 只做短时 observation，不晋级为持久标签",
            gate.mayAutoBook(
                candidate(cause = ErrorCause.CARELESS, detail = null),
                corroborationCount = 99,
                retractedRecently = false,
            ),
        )
    }

    @Test
    fun `below-threshold confidence and corroboration are refused`() {
        val gate = ErrorCauseGate(ErrorCauseGateState(certified = true))
        assertFalse(gate.mayAutoBook(candidate(confidence = 0.79), 2, false))
        assertFalse(gate.mayAutoBook(candidate(confidence = 0.80), 1, false))
        assertTrue(gate.mayAutoBook(candidate(confidence = 0.80), 2, false))
    }

    @Test
    fun `a node-scoped candidate needs DIRECT attribution`() {
        val nodeScope = scope.copy(knowledgeNodeId = "node-1")
        val gate = ErrorCauseGate(ErrorCauseGateState(certified = true))
        assertFalse(
            gate.mayAutoBook(
                candidate(scope = nodeScope, certainty = ErrorCauseNodeCertainty.INFERRED),
                2,
                false,
            ),
        )
        assertTrue(
            gate.mayAutoBook(
                candidate(scope = nodeScope, certainty = ErrorCauseNodeCertainty.DIRECT),
                2,
                false,
            ),
        )
        assertTrue(
            "题级标签（node=NULL）不受 P7 限制",
            gate.mayAutoBook(candidate(scope = scope), 2, false),
        )
    }

    @Test
    fun `a recent retraction pauses automatic booking until new evidence`() {
        val gate = ErrorCauseGate(ErrorCauseGateState(certified = true))
        assertFalse(gate.mayAutoBook(candidate(), 2, retractedRecently = true))
    }

    @Test
    fun `a paused or re-versioned gate refuses`() {
        val paused = ErrorCauseGate(ErrorCauseGateState(certified = true, paused = true))
        assertFalse(paused.mayAutoBook(candidate(), 2, false))

        val staleTaxonomy = ErrorCauseGate(
            ErrorCauseGateState(certified = true, taxonomyVersion = "error-cause-v0"),
        )
        assertFalse(
            "换词表必须重新认证（P8）",
            staleTaxonomy.mayAutoBook(candidate(), 2, false),
        )
    }

    @Test
    fun `self report can never auto book even when every other threshold passes`() {
        val gate = ErrorCauseGate(ErrorCauseGateState(certified = true))
        assertFalse(
            "SELF_REPORT 只能调置信、不能单独定类（R2 §3.6）",
            gate.mayAutoBook(
                candidate(channel = ErrorCauseChannel.SELF_REPORT),
                corroborationCount = 99,
                retractedRecently = false,
            ),
        )
    }

    private fun candidate(
        cause: ErrorCause = ErrorCause.CONCEPTUAL,
        detail: ErrorCauseDetail? = ErrorCauseDetail.CONCEPT_RULE_INVALID,
        confidence: Double = 0.85,
        scope: ErrorCauseScope = this.scope,
        certainty: ErrorCauseNodeCertainty = ErrorCauseNodeCertainty.UNKNOWN,
        channel: ErrorCauseChannel = ErrorCauseChannel.TUTOR_TRACE,
    ) = ErrorCauseCandidate(
        scope = scope,
        attemptId = "attempt-1",
        evidence = ErrorCauseEvidence(
            cause = cause,
            detail = detail,
            confidence = confidence,
            channel = channel,
            refs = listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, "attempt-1")),
            observedAtEpochMillis = 1_000,
            rationaleMarkdown = "本地理由",
        ),
        extractorVersion = "error-cause-local-v1",
        knowledgeNodeCertainty = certainty,
    )

    private val scope = ErrorCauseScope(
        learnerId = "learner-1",
        practiceUnitId = "unit-1",
        problemRevisionId = "rev-1",
    )
}
