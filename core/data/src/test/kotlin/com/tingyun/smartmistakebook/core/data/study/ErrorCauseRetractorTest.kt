package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.port.ErrorCauseAppendResult
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseBindingRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseObservationRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCausePort
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseWriteResult
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseBindingState
import com.tingyun.smartmistakebook.core.model.ErrorCauseCandidate
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDecisionSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidence
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseIdentity
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorCauseRetractorTest {

    @Test
    fun `the default-closed gate writes nothing`() = runBlocking {
        val port = FakeErrorCausePort()
        val result = ErrorCauseRetractor(port).bookIfAllowed(
            candidate = candidate(),
            gate = ErrorCauseGate(),
            corroborationCount = 99,
            retractedRecently = false,
            bindingId = "binding-1",
            decidedAtEpochMillis = 5_000,
        )
        assertEquals(ErrorCauseWriteResult.CONFLICT, result)
        assertEquals(0, port.appendCalls)
        assertEquals(0, port.acceptCalls)
        assertTrue(port.bindings.isEmpty())
    }

    @Test
    fun `a certified gate books through one label-chain transaction`() = runBlocking {
        val port = FakeErrorCausePort()
        val result = ErrorCauseRetractor(port).bookIfAllowed(
            candidate = candidate(),
            gate = ErrorCauseGate(ErrorCauseGateState(certified = true)),
            corroborationCount = 2,
            retractedRecently = false,
            bindingId = "binding-1",
            decidedAtEpochMillis = 5_000,
        )
        assertEquals(ErrorCauseWriteResult.APPLIED, result)
        val binding = port.bindings.getValue("binding-1")
        assertEquals(ErrorCauseBindingState.ACTIVE, binding.state)
        assertEquals(scope.activeKey(), binding.activeKey)
        assertEquals(ErrorCauseAcceptanceSource.AUTO_GATE, binding.acceptanceSource)
        assertEquals("d1c-v1", binding.gateVersion)
        assertEquals(
            ErrorCauseObservationStatus.ACCEPTED,
            port.observations.getValue(binding.sourceObservationId).status,
        )
        assertEquals("自动落账不是掌握度重放", 0, port.attemptWrites)
    }

    @Test
    fun `user confirmation can book careless without the statistical gate`() = runBlocking {
        val port = FakeErrorCausePort()
        val result = ErrorCauseRetractor(port).confirmByUser(
            candidate = candidate(cause = ErrorCause.CARELESS, detail = null),
            bindingId = "binding-careless",
            decidedAtEpochMillis = 5_000,
        )
        assertEquals(ErrorCauseWriteResult.APPLIED, result)
        assertEquals(
            ErrorCauseAcceptanceSource.USER_CONFIRMED,
            port.bindings.getValue("binding-careless").acceptanceSource,
        )
    }

    @Test
    fun `retracting without an active binding is a no-op`() = runBlocking {
        val port = FakeErrorCausePort()
        val result = ErrorCauseRetractor(port).retractByUser(
            scope = scope,
            bindingId = "missing",
            reasonMarkdown = "这不是我的错因",
            evidenceRefs = refs(),
            observedAtEpochMillis = 6_000,
            decidedAtEpochMillis = 7_000,
            extractorVersion = "error-cause-local-v1",
        )
        assertEquals(ErrorCauseWriteResult.NOT_FOUND, result)
        assertEquals(0, port.retractCalls)
        assertTrue(port.observations.isEmpty())
    }

    @Test
    fun `a stale binding id conflicts without writing a retraction row`() = runBlocking {
        val port = FakeErrorCausePort()
        val retractor = ErrorCauseRetractor(port)
        retractor.confirmByUser(candidate(), "binding-1", 5_000)
        val result = retractor.retractByUser(
            scope = scope,
            bindingId = "binding-other",
            reasonMarkdown = "这不是我的错因",
            evidenceRefs = refs(),
            observedAtEpochMillis = 6_000,
            decidedAtEpochMillis = 7_000,
            extractorVersion = "error-cause-local-v1",
        )
        assertEquals(ErrorCauseWriteResult.CONFLICT, result)
        assertEquals(0, port.retractCalls)
        assertEquals(
            ErrorCauseBindingState.ACTIVE,
            port.bindings.getValue("binding-1").state,
        )
    }

    @Test
    fun `user retraction writes a RETRACTED observation and voids the binding`() = runBlocking {
        val port = FakeErrorCausePort()
        val retractor = ErrorCauseRetractor(port)
        retractor.confirmByUser(candidate(), "binding-1", 5_000)
        val sourceObservation = port.bindings.getValue("binding-1").sourceObservationId

        val result = retractor.retractByUser(
            scope = scope,
            bindingId = "binding-1",
            reasonMarkdown = "这不是我的错因",
            evidenceRefs = refs("user-correction-1"),
            observedAtEpochMillis = 6_000,
            decidedAtEpochMillis = 7_000,
            extractorVersion = "error-cause-local-v1",
        )

        assertEquals(ErrorCauseWriteResult.APPLIED, result)
        val retraction = port.observations.values.single {
            it.status == ErrorCauseObservationStatus.RETRACTED
        }
        assertEquals(ErrorCauseDecisionSource.USER, retraction.decisionSource)
        assertEquals(sourceObservation, retraction.supersedesObservationId)
        assertEquals(ErrorCauseChannel.SELF_REPORT, retraction.channel)

        val binding = port.bindings.getValue("binding-1")
        assertEquals(ErrorCauseBindingState.VOIDED, binding.state)
        assertNull("撤回必须腾出活键", binding.activeKey)
        assertEquals(7_000L, binding.endedAtEpochMillis)
    }

    @Test
    fun `answer-key correction is the only SYSTEM retraction path`() = runBlocking {
        val port = FakeErrorCausePort()
        val retractor = ErrorCauseRetractor(port)
        retractor.confirmByUser(candidate(), "binding-1", 5_000)

        val result = retractor.retractForAnswerKeyCorrection(
            scope = scope,
            bindingId = "binding-1",
            reasonMarkdown = "答案键被纠正为学生答对",
            evidenceRefs = refs("answer-key-correction-1"),
            observedAtEpochMillis = 6_000,
            decidedAtEpochMillis = 7_000,
            extractorVersion = "error-cause-local-v1",
        )
        assertEquals(ErrorCauseWriteResult.APPLIED, result)
        assertEquals(
            ErrorCauseDecisionSource.SYSTEM,
            port.observations.values.single {
                it.status == ErrorCauseObservationStatus.RETRACTED
            }.decisionSource,
        )
        assertEquals("作答/掌握度一行都不能改", 0, port.attemptWrites)
    }

    @Test
    fun `superseding moves the active key to the new binding`() = runBlocking {
        val port = FakeErrorCausePort()
        val retractor = ErrorCauseRetractor(port)
        retractor.confirmByUser(candidate(), "binding-1", 5_000)

        val newObservation = observation(
            cause = ErrorCause.PROCEDURAL,
            detail = ErrorCauseDetail.PROC_STEP_ORDER,
            status = ErrorCauseObservationStatus.PROPOSED,
        )
        val newBinding = ErrorCauseBindingRecord(
            bindingId = "binding-2",
            scope = scope,
            cause = ErrorCause.PROCEDURAL,
            detail = ErrorCauseDetail.PROC_STEP_ORDER,
            confidence = 0.8,
            acceptanceSource = ErrorCauseAcceptanceSource.USER_CORRECTED,
            gateVersion = null,
            sourceObservationId = newObservation.observationId,
            acceptedAtEpochMillis = 8_000,
            state = ErrorCauseBindingState.ACTIVE,
            activeKey = scope.activeKey(),
        )

        val result = retractor.supersedeByUser(
            scope = scope,
            oldBindingId = "binding-1",
            newObservation = newObservation,
            newBinding = newBinding,
            decidedAtEpochMillis = 8_000,
        )

        assertEquals(ErrorCauseWriteResult.APPLIED, result)
        val old = port.bindings.getValue("binding-1")
        assertEquals(ErrorCauseBindingState.SUPERSEDED, old.state)
        assertEquals("binding-2", old.supersededByBindingId)
        assertNull(old.activeKey)
        val current = port.bindings.getValue("binding-2")
        assertEquals(ErrorCauseBindingState.ACTIVE, current.state)
        assertEquals(scope.activeKey(), current.activeKey)
        assertEquals(
            ErrorCauseObservationStatus.ACCEPTED,
            port.observations.getValue(newObservation.observationId).status,
        )
    }

    @Test
    fun `a blank retraction reason is refused before any write`() = runBlocking {
        val port = FakeErrorCausePort()
        val retractor = ErrorCauseRetractor(port)
        retractor.confirmByUser(candidate(), "binding-1", 5_000)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                retractor.retractByUser(
                    scope = scope,
                    bindingId = "binding-1",
                    reasonMarkdown = "  ",
                    evidenceRefs = refs(),
                    observedAtEpochMillis = 6_000,
                    decidedAtEpochMillis = 7_000,
                    extractorVersion = "error-cause-local-v1",
                )
                Unit
            }
        }
        assertEquals(0, port.retractCalls)
    }

    private fun candidate(
        cause: ErrorCause = ErrorCause.CONCEPTUAL,
        detail: ErrorCauseDetail? = ErrorCauseDetail.CONCEPT_RULE_INVALID,
    ) = ErrorCauseCandidate(
        scope = scope,
        attemptId = "attempt-1",
        evidence = ErrorCauseEvidence(
            cause = cause,
            detail = detail,
            confidence = 0.85,
            channel = ErrorCauseChannel.TUTOR_TRACE,
            refs = refs(),
            observedAtEpochMillis = 1_000,
            rationaleMarkdown = "本地理由",
        ),
        extractorVersion = "error-cause-local-v1",
    )

    private fun refs(id: String = "attempt-1") =
        listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.ATTEMPT, id))

    private fun observation(
        cause: ErrorCause,
        detail: ErrorCauseDetail?,
        status: ErrorCauseObservationStatus,
    ) = ErrorCauseObservationRecord(
        observationId = ErrorCauseIdentity.observationId(
            scope = scope,
            attemptId = "attempt-2",
            cause = cause,
            detail = detail,
            channel = ErrorCauseChannel.TUTOR_TRACE,
            extractorVersion = "error-cause-local-v1",
            refs = refs("attempt-2"),
        ),
        scope = scope,
        attemptId = "attempt-2",
        cause = cause,
        detail = detail,
        confidence = 0.8,
        channel = ErrorCauseChannel.TUTOR_TRACE,
        extractorVersion = "error-cause-local-v1",
        taxonomyVersion = ErrorCause.TAXONOMY_VERSION,
        rationaleMarkdown = "本地理由",
        evidenceRefs = refs("attempt-2"),
        observedAtEpochMillis = 2_000,
        status = status,
    )

    /**
     * 只实现标签链端口。`attemptWrites` 永远是 0——它是"撤回不得触发作答纠正"的结构性
     * 证据：本测试类拿不到 attempt 写口，也没有任何调用面。
     */
    private class FakeErrorCausePort : ErrorCausePort {
        val observations = LinkedHashMap<String, ErrorCauseObservationRecord>()
        val bindings = LinkedHashMap<String, ErrorCauseBindingRecord>()
        var appendCalls = 0
        var acceptCalls = 0
        var retractCalls = 0
        var supersedeCalls = 0
        var attemptWrites = 0

        override suspend fun appendObservation(
            record: ErrorCauseObservationRecord,
        ): ErrorCauseAppendResult {
            appendCalls++
            return if (observations.putIfAbsent(record.observationId, record) == null) {
                ErrorCauseAppendResult.INSERTED
            } else {
                ErrorCauseAppendResult.DUPLICATE
            }
        }

        override suspend fun readActiveBinding(scope: ErrorCauseScope): ErrorCauseBindingRecord? =
            bindings.values.firstOrNull {
                it.scope == scope && it.state == ErrorCauseBindingState.ACTIVE
            }

        override suspend fun acceptObservationAndActivateBinding(
            binding: ErrorCauseBindingRecord,
            decidedAtEpochMillis: Long,
            decisionSource: ErrorCauseDecisionSource,
        ): ErrorCauseWriteResult {
            acceptCalls++
            val observation = observations[binding.sourceObservationId]
                ?: return ErrorCauseWriteResult.CONFLICT
            if (observation.status == ErrorCauseObservationStatus.ACCEPTED) {
                return if (bindings[binding.bindingId]?.state == ErrorCauseBindingState.ACTIVE) {
                    ErrorCauseWriteResult.APPLIED
                } else {
                    ErrorCauseWriteResult.CONFLICT
                }
            }
            if (observation.status != ErrorCauseObservationStatus.PROPOSED) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (bindings.values.any {
                    it.state == ErrorCauseBindingState.ACTIVE &&
                        it.activeKey == binding.activeKey
                }
            ) {
                return ErrorCauseWriteResult.CONFLICT
            }
            observations[binding.sourceObservationId] = observation.copy(
                status = ErrorCauseObservationStatus.ACCEPTED,
                decidedAtEpochMillis = decidedAtEpochMillis,
                decisionSource = decisionSource,
            )
            bindings[binding.bindingId] = binding
            return ErrorCauseWriteResult.APPLIED
        }

        override suspend fun retractActiveBinding(
            retraction: ErrorCauseObservationRecord,
            bindingId: String,
            endedAtEpochMillis: Long,
        ): ErrorCauseWriteResult {
            retractCalls++
            val binding = bindings[bindingId] ?: return ErrorCauseWriteResult.CONFLICT
            if (binding.state != ErrorCauseBindingState.ACTIVE) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (retraction.status != ErrorCauseObservationStatus.RETRACTED) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (retraction.supersedesObservationId != binding.sourceObservationId) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (retraction.decisionSource != ErrorCauseDecisionSource.USER &&
                retraction.decisionSource != ErrorCauseDecisionSource.SYSTEM
            ) {
                return ErrorCauseWriteResult.CONFLICT
            }
            observations[retraction.observationId] = retraction
            bindings[bindingId] = binding.copy(
                state = ErrorCauseBindingState.VOIDED,
                activeKey = null,
                endedAtEpochMillis = endedAtEpochMillis,
            )
            return ErrorCauseWriteResult.APPLIED
        }

        override suspend fun supersedeActiveBinding(
            newObservation: ErrorCauseObservationRecord,
            newBinding: ErrorCauseBindingRecord,
            oldBindingId: String,
            decidedAtEpochMillis: Long,
            decisionSource: ErrorCauseDecisionSource,
        ): ErrorCauseWriteResult {
            supersedeCalls++
            val old = bindings[oldBindingId] ?: return ErrorCauseWriteResult.CONFLICT
            if (old.state != ErrorCauseBindingState.ACTIVE) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (newBinding.activeKey != old.activeKey) {
                return ErrorCauseWriteResult.CONFLICT
            }
            val observation = observations[newObservation.observationId]
                ?: return ErrorCauseWriteResult.CONFLICT
            if (observation.status == ErrorCauseObservationStatus.PROPOSED) {
                observations[newObservation.observationId] = observation.copy(
                    status = ErrorCauseObservationStatus.ACCEPTED,
                    decidedAtEpochMillis = decidedAtEpochMillis,
                    decisionSource = decisionSource,
                )
            }
            bindings[oldBindingId] = old.copy(
                state = ErrorCauseBindingState.SUPERSEDED,
                activeKey = null,
                supersededByBindingId = newBinding.bindingId,
                endedAtEpochMillis = decidedAtEpochMillis,
            )
            bindings[newBinding.bindingId] = newBinding
            return ErrorCauseWriteResult.APPLIED
        }
    }

    private val scope = ErrorCauseScope(
        learnerId = "learner-1",
        practiceUnitId = "unit-1",
        problemRevisionId = "rev-1",
    )
}
