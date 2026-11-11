package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.port.ErrorCauseAppendResult
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseBindingRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseObservationRecord
import com.tingyun.smartmistakebook.core.database.port.ErrorCausePort
import com.tingyun.smartmistakebook.core.database.port.ErrorCauseWriteResult
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseBindingState
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDecisionSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseIdentity
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 50 / 5c：错因详情页的读写接缝。
 *
 * 分工：`ErrorCauseRetractorTest` 证明单个动作的状态迁移；本类证明**页面会调的三件事**
 * 在接缝层的语义：
 *
 * 1. 读候选**零写入**，模型自动落账在 D-1c 未认证时恒关闭（门只拦自动，不拦人）；
 * 2. 用户确认 → 候选变 `USER_CONFIRMED` 的 ACTIVE 标签，下一次读取能看到，且只有一条；
 * 3. 撤销 → `RETRACTED` 撤回行 + `VOIDED`，`active_key` 清空，历史行保留；
 * 4. 跨修订/跨作用域的候选不能被确认（I7：新修订不自动继承旧错因）。
 */
class ErrorCauseReviewServiceTest {

    private val scope = ErrorCauseScope(
        learnerId = "learner-1",
        practiceUnitId = "unit-1",
        problemRevisionId = "rev-1",
    )

    @Test
    fun `reading candidates never writes and reports the auto gate as closed`() = runBlocking {
        val port = FakeLedger()
        port.seedCandidate(observation(cause = ErrorCause.CONCEPTUAL))
        val service = ErrorCauseReviewService(port)

        assertTrue("默认门必须关闭：模型候选不得自动写", service.autoBookingClosed)
        val snapshot = service.read(scope)

        assertEquals(1, snapshot.candidates.size)
        assertNull("没有现行标签", snapshot.active)
        assertTrue("读不产生任何写", port.bindings.isEmpty())
        assertEquals(1, port.observations.size)
        assertEquals(
            "候选必须仍是 PROPOSED，读侧不能顺手把它改成 ACCEPTED",
            ErrorCauseObservationStatus.PROPOSED,
            port.observations.values.single().status,
        )
    }

    @Test
    fun `user confirmation turns a candidate into a user-confirmed active tag`() = runBlocking {
        val port = FakeLedger()
        val candidate = observation(cause = ErrorCause.PROCEDURAL, detail = ErrorCauseDetail.PROC_STEP_ORDER)
        port.seedCandidate(candidate)
        val service = ErrorCauseReviewService(port)

        val result = service.confirmCandidate(
            scope = scope,
            observationId = candidate.observationId,
            decidedAtEpochMillis = 20_000,
        )

        assertEquals(ErrorCauseReviewWriteResult.APPLIED, result)
        val active = port.readActiveBinding(scope)
        assertNotNull("确认后必须能读到现行标签", active)
        assertEquals(scope.activeKey(), active!!.activeKey)
        assertEquals(
            "人的确认不是模型自动落账",
            ErrorCauseAcceptanceSource.USER_CONFIRMED,
            active.acceptanceSource,
        )
        assertNull("用户确认不带 D-1c 门版本", active.gateVersion)
        assertEquals(
            ErrorCauseObservationStatus.ACCEPTED,
            port.observations.getValue(candidate.observationId).status,
        )
        assertEquals(
            "确认后候选从候选列表消失",
            emptyList<ErrorCauseObservationRecord>(),
            service.read(scope).candidates,
        )
        assertEquals("确认后现行标签就是它", active.bindingId, service.read(scope).active?.bindingId)
    }

    @Test
    fun `confirming twice does not create a second active binding`() = runBlocking {
        val port = FakeLedger()
        val candidate = observation(cause = ErrorCause.MISIDENTIFICATION, detail = ErrorCauseDetail.READ_SIGN)
        port.seedCandidate(candidate)
        val service = ErrorCauseReviewService(port)

        assertEquals(
            ErrorCauseReviewWriteResult.APPLIED,
            service.confirmCandidate(scope, candidate.observationId, 20_000),
        )
        val second = service.confirmCandidate(scope, candidate.observationId, 21_000)

        assertEquals(
            "已确认的候选不再是开放候选，不能重复写",
            ErrorCauseReviewWriteResult.CANDIDATE_NOT_FOUND,
            second,
        )
        assertEquals("同一作用域只允许一条 ACTIVE", 1, port.bindings.size)
        assertEquals(1, port.activeCount(scope))
    }

    @Test
    fun `a candidate from another revision cannot be confirmed from this page`() = runBlocking {
        val port = FakeLedger()
        val otherRevision = observation(
            cause = ErrorCause.CONCEPTUAL,
            scope = scope.copy(problemRevisionId = "rev-0"),
            attemptId = "attempt-old",
        )
        port.seedCandidate(otherRevision)
        val service = ErrorCauseReviewService(port)

        assertEquals(
            "旧修订的候选不进入本修订的候选列表",
            emptyList<ErrorCauseObservationRecord>(),
            service.read(scope).candidates,
        )
        assertEquals(
            ErrorCauseReviewWriteResult.CANDIDATE_NOT_FOUND,
            service.confirmCandidate(scope, otherRevision.observationId, 20_000),
        )
        assertTrue("被拒的确认零写入", port.bindings.isEmpty())
        assertEquals(
            ErrorCauseObservationStatus.PROPOSED,
            port.observations.getValue(otherRevision.observationId).status,
        )
    }

    @Test
    fun `undo writes a retraction row and clears the active key`() = runBlocking {
        val port = FakeLedger()
        val candidate = observation(cause = ErrorCause.CARELESS, detail = null)
        port.seedCandidate(candidate)
        val service = ErrorCauseReviewService(port)
        assertEquals(
            ErrorCauseReviewWriteResult.APPLIED,
            service.confirmCandidate(scope, candidate.observationId, 20_000),
        )
        val bindingId = requireNotNull(port.readActiveBinding(scope)).bindingId

        val result = service.undoActiveTag(
            scope = scope,
            bindingId = bindingId,
            observedAtEpochMillis = 30_000,
            decidedAtEpochMillis = 30_000,
        )

        assertEquals(ErrorCauseReviewWriteResult.APPLIED, result)
        assertNull("撤销后读侧必须为空", port.readActiveBinding(scope))
        val voided = port.bindings.getValue(bindingId)
        assertEquals(ErrorCauseBindingState.VOIDED, voided.state)
        assertNull(voided.activeKey)
        val retraction = port.observations.values.single {
            it.status == ErrorCauseObservationStatus.RETRACTED
        }
        assertEquals(ErrorCauseDecisionSource.USER, retraction.decisionSource)
        assertEquals(
            "撤回行指向原证据，历史不删",
            candidate.observationId,
            retraction.supersedesObservationId,
        )
        assertEquals(
            "撤回不是 attempt 写：本接缝没有 attempt 口",
            0,
            port.attemptWrites,
        )
        assertEquals(
            "撤回的是标签不是证据：已确认的 observation 不再作为新候选重复出现",
            0,
            service.read(scope).candidates.size,
        )
    }

    @Test
    fun `auto booking stays refused while the d1c gate is uncertified`() = runBlocking {
        val port = FakeLedger()
        val extractor = ErrorCauseExtractor()
        val booking = ErrorCauseBookingService(ErrorCauseRetractor(port))

        val outcome = booking.proposeAndBook(
            input = conceptualInput(),
            corroborationCount = 3,
            retractedRecently = false,
            bindingId = "binding-auto",
            decidedAtEpochMillis = 20_000,
        )

        assertEquals(ErrorCauseBookingOutcome.GATE_REJECTED, outcome)
        assertTrue("门未认证时零写入", port.bindings.isEmpty())
        assertTrue(port.observations.isEmpty())
        assertNotNull("提取器本身能产出候选（只是不落账）", extractor.extract(conceptualInput()))
    }

    private fun conceptualInput() = ErrorCauseExtractionInput(
        scope = scope,
        attemptId = "attempt-1",
        observedAtEpochMillis = 1_000,
        traceSignals = listOf(
            ErrorCauseTraceSignal.RuleClaim(
                messageId = "message-1",
                validity = StudentRuleValidity.INVALID,
            ),
        ),
    )

    private fun observation(
        cause: ErrorCause,
        detail: ErrorCauseDetail? = null,
        scope: ErrorCauseScope = this.scope,
        attemptId: String = "attempt-1",
        channel: ErrorCauseChannel = ErrorCauseChannel.TUTOR_TRACE,
    ): ErrorCauseObservationRecord {
        val refs = listOf(ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.TUTOR_MESSAGE, "message-1"))
        return ErrorCauseObservationRecord(
            observationId = ErrorCauseIdentity.observationId(
                scope = scope,
                attemptId = attemptId,
                cause = cause,
                detail = detail,
                channel = channel,
                extractorVersion = ErrorCauseExtractor.LOCAL_EXTRACTOR_VERSION,
                refs = refs,
            ),
            scope = scope,
            attemptId = attemptId,
            cause = cause,
            detail = detail,
            confidence = 0.86,
            channel = channel,
            extractorVersion = ErrorCauseExtractor.LOCAL_EXTRACTOR_VERSION,
            taxonomyVersion = ErrorCause.TAXONOMY_VERSION,
            rationaleMarkdown = "测试候选的本地理由",
            evidenceRefs = refs,
            observedAtEpochMillis = 1_000,
            status = ErrorCauseObservationStatus.PROPOSED,
        )
    }

    /** 与真 DAO 同判定顺序的假账本：observation 状态 → 作用域匹配 → active_key 占用。 */
    private class FakeLedger : ErrorCausePort {
        val observations = LinkedHashMap<String, ErrorCauseObservationRecord>()
        val bindings = LinkedHashMap<String, ErrorCauseBindingRecord>()
        var attemptWrites = 0

        fun seedCandidate(record: ErrorCauseObservationRecord) {
            observations[record.observationId] = record
        }

        fun activeCount(scope: ErrorCauseScope): Int = bindings.values.count {
            it.state == ErrorCauseBindingState.ACTIVE && it.activeKey == scope.activeKey()
        }

        override suspend fun appendObservation(
            record: ErrorCauseObservationRecord,
        ): ErrorCauseAppendResult =
            if (observations.putIfAbsent(record.observationId, record) == null) {
                ErrorCauseAppendResult.INSERTED
            } else {
                ErrorCauseAppendResult.DUPLICATE
            }

        override suspend fun readActiveBinding(scope: ErrorCauseScope): ErrorCauseBindingRecord? =
            bindings.values.firstOrNull {
                it.state == ErrorCauseBindingState.ACTIVE && it.activeKey == scope.activeKey()
            }

        override suspend fun readProposedObservations(
            learnerId: String,
            practiceUnitId: String,
        ): List<ErrorCauseObservationRecord> = observations.values.filter {
            it.scope.learnerId == learnerId &&
                it.scope.practiceUnitId == practiceUnitId &&
                it.status == ErrorCauseObservationStatus.PROPOSED
        }

        override suspend fun acceptObservationAndActivateBinding(
            binding: ErrorCauseBindingRecord,
            decidedAtEpochMillis: Long,
            decisionSource: ErrorCauseDecisionSource,
        ): ErrorCauseWriteResult {
            val observation = observations[binding.sourceObservationId]
                ?: return ErrorCauseWriteResult.CONFLICT
            if (!observation.matches(binding)) return ErrorCauseWriteResult.CONFLICT
            if (observation.status == ErrorCauseObservationStatus.ACCEPTED) {
                val existing = bindings[binding.bindingId]
                return if (existing?.state == ErrorCauseBindingState.ACTIVE &&
                    existing.activeKey == binding.activeKey
                ) {
                    ErrorCauseWriteResult.APPLIED
                } else {
                    ErrorCauseWriteResult.CONFLICT
                }
            }
            if (observation.status != ErrorCauseObservationStatus.PROPOSED) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (binding.state != ErrorCauseBindingState.ACTIVE ||
                binding.activeKey != binding.scope.activeKey()
            ) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (activeCount(binding.scope) != 0) return ErrorCauseWriteResult.CONFLICT
            observations[observation.observationId] = observation.copy(
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
            val binding = bindings[bindingId] ?: return ErrorCauseWriteResult.NOT_FOUND
            val existing = observations[retraction.observationId]
            if (existing != null) {
                return if (existing.status == ErrorCauseObservationStatus.RETRACTED &&
                    existing.supersedesObservationId == binding.sourceObservationId &&
                    binding.state != ErrorCauseBindingState.ACTIVE
                ) {
                    ErrorCauseWriteResult.APPLIED
                } else {
                    ErrorCauseWriteResult.CONFLICT
                }
            }
            if (binding.state != ErrorCauseBindingState.ACTIVE) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (retraction.status != ErrorCauseObservationStatus.RETRACTED) {
                return ErrorCauseWriteResult.CONFLICT
            }
            if (!retraction.matches(binding)) return ErrorCauseWriteResult.CONFLICT
            if (retraction.supersedesObservationId != binding.sourceObservationId) {
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

        private fun ErrorCauseObservationRecord.matches(
            binding: ErrorCauseBindingRecord,
        ): Boolean = this.scope == binding.scope &&
            this.cause == binding.cause &&
            this.detail == binding.detail
    }
}
