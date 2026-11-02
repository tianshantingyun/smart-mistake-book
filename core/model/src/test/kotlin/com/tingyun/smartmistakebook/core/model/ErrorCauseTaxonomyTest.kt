package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorCauseTaxonomyTest {

    @Test
    fun `stable codes are frozen and never reused`() {
        assertEquals(listOf("E01", "E02", "E03", "E04"), ErrorCause.entries.map { it.code })
        assertEquals(ErrorCause.CONCEPTUAL, ErrorCause.fromCode("E01"))
        assertEquals(ErrorCause.PROCEDURAL, ErrorCause.fromCode("E02"))
        assertEquals(ErrorCause.CARELESS, ErrorCause.fromCode("E03"))
        assertEquals(ErrorCause.MISIDENTIFICATION, ErrorCause.fromCode("E04"))
        assertNull("预留码 E05 在 v1 不得被解析成已启用类", ErrorCause.fromCode("E05"))
        assertEquals("error-cause-v1", ErrorCause.TAXONOMY_VERSION)
        assertThrows(IllegalArgumentException::class.java) { ErrorCause.requireFromCode("E99") }
    }

    @Test
    fun `every detail code belongs to exactly one top cause`() {
        assertEquals(
            "细码总表不得重复",
            ErrorCauseDetail.entries.size,
            ErrorCauseDetail.entries.map { it.code }.toSet().size,
        )
        ErrorCauseDetail.entries.forEach { detail ->
            assertEquals(detail, ErrorCauseDetail.fromCode(detail.code))
        }
        assertEquals(
            "E04 必须是感知/转录层四码，不得混入 Elbrink 的算法层",
            setOf("READ_SIGN", "READ_DIGIT", "READ_CONDITION", "READ_TARGET"),
            ErrorCauseDetail.forCause(ErrorCause.MISIDENTIFICATION).map { it.code }.toSet(),
        )
        assertTrue(
            "算法选择错误必须归 E02，不能占 E04",
            ErrorCauseDetail.PROC_ALGORITHM_SELECTION in
                ErrorCauseDetail.forCause(ErrorCause.PROCEDURAL),
        )
    }

    @Test
    fun `evidence rejects a detail code from another cause`() {
        assertThrows(IllegalArgumentException::class.java) {
            evidence(cause = ErrorCause.CONCEPTUAL, detail = ErrorCauseDetail.READ_SIGN)
        }
    }

    @Test
    fun `evidence requires confidence refs and a rationale`() {
        assertThrows(IllegalArgumentException::class.java) { evidence(confidence = -0.1) }
        assertThrows(IllegalArgumentException::class.java) { evidence(confidence = 1.1) }
        assertThrows(IllegalArgumentException::class.java) { evidence(confidence = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { evidence(refs = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            evidence(
                refs = listOf(
                    ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, "attempt-1"),
                    ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, "attempt-1"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) { evidence(rationale = "  ") }
    }

    @Test
    fun `scope active key round trips and rejects the separator inside ids`() {
        val scope = ErrorCauseScope(
            learnerId = "learner-1",
            practiceUnitId = "unit-1",
            problemRevisionId = "rev-1",
            knowledgeNodeId = "node-1",
        )
        assertEquals(scope, ErrorCauseScope.fromActiveKey(scope.activeKey()))

        val problemLevel = scope.copy(knowledgeNodeId = null)
        assertEquals(problemLevel, ErrorCauseScope.fromActiveKey(problemLevel.activeKey()))
        assertNull(ErrorCauseScope.fromActiveKey("only-one-segment"))
        assertNull(ErrorCauseScope.fromActiveKey("a\u001fb\u001fc\u001fd\u001fe"))

        assertThrows(IllegalArgumentException::class.java) {
            ErrorCauseScope(
                learnerId = "learner\u001f1",
                practiceUnitId = "unit-1",
                problemRevisionId = "rev-1",
            )
        }
    }

    @Test
    fun `observation identity is content addressed and ignores presentation fields`() {
        val base = evidence()
        val one = ErrorCauseIdentity.observationId(
            scope = scope,
            attemptId = "attempt-1",
            cause = base.cause,
            detail = base.detail,
            channel = base.channel,
            extractorVersion = "extractor-v1",
            refs = base.refs,
        )
        val same = ErrorCauseIdentity.observationId(
            scope = scope,
            attemptId = "attempt-1",
            cause = base.cause,
            detail = base.detail,
            channel = base.channel,
            extractorVersion = "extractor-v1",
            refs = base.refs,
        )
        assertEquals("同一内容重放必须得到同一 id", one, same)
        assertTrue(one.startsWith("ecobs-"))

        val presentationOnlyChanged = base.copy(
            confidence = 0.42,
            rationaleMarkdown = "另一段本地理由",
            observedAtEpochMillis = 99_000,
        )
        assertEquals(
            "confidence/rationale/时间不是身份",
            one,
            ErrorCauseIdentity.observationId(
                scope = scope,
                attemptId = "attempt-1",
                cause = presentationOnlyChanged.cause,
                detail = presentationOnlyChanged.detail,
                channel = presentationOnlyChanged.channel,
                extractorVersion = "extractor-v1",
                refs = presentationOnlyChanged.refs,
            ),
        )

        assertNotEquals(
            "refs 变了就是另一条证据",
            one,
            ErrorCauseIdentity.observationId(
                scope = scope,
                attemptId = "attempt-1",
                cause = base.cause,
                detail = base.detail,
                channel = base.channel,
                extractorVersion = "extractor-v1",
                refs = listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, "attempt-2")),
            ),
        )
        assertNotEquals(
            "提取器换版必须换 id（P8 重新认证）",
            one,
            ErrorCauseIdentity.observationId(
                scope = scope,
                attemptId = "attempt-1",
                cause = base.cause,
                detail = base.detail,
                channel = base.channel,
                extractorVersion = "extractor-v2",
                refs = base.refs,
            ),
        )
        assertNotEquals(
            "撤回/改判必须按被 supersede 的原证据区分 episode",
            one,
            ErrorCauseIdentity.observationId(
                scope = scope,
                attemptId = "attempt-1",
                cause = base.cause,
                detail = base.detail,
                channel = base.channel,
                extractorVersion = "extractor-v1",
                refs = base.refs,
                supersedesObservationId = "observation-0",
            ),
        )
    }

    @Test
    fun `evidence refs codec round trips and keeps order`() {
        val refs = listOf(
            ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, "message-1"),
            ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, "attempt-1"),
        )
        val encoded = ErrorCauseEvidenceRefsCodec.encode(refs)
        assertEquals(refs, ErrorCauseEvidenceRefsCodec.decode(encoded))
    }

    private fun evidence(
        cause: ErrorCause = ErrorCause.CONCEPTUAL,
        detail: ErrorCauseDetail? = ErrorCauseDetail.CONCEPT_RULE_INVALID,
        confidence: Double = 0.85,
        refs: List<ErrorCauseEvidenceRef> = listOf(
            ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, "attempt-1"),
        ),
        rationale: String = "学生说出的规则在一般情况下不成立",
    ) = ErrorCauseEvidence(
        cause = cause,
        detail = detail,
        confidence = confidence,
        channel = ErrorCauseChannel.TUTOR_TRACE,
        refs = refs,
        observedAtEpochMillis = 1_000,
        rationaleMarkdown = rationale,
    )

    private val scope = ErrorCauseScope(
        learnerId = "learner-1",
        practiceUnitId = "unit-1",
        problemRevisionId = "rev-1",
    )
}
