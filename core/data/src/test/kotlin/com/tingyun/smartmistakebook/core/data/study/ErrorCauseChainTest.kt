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
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceKind
import com.tingyun.smartmistakebook.core.model.ErrorCauseEvidenceRef
import com.tingyun.smartmistakebook.core.model.ErrorCauseIdentity
import com.tingyun.smartmistakebook.core.model.ErrorCauseObservationStatus
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 41 / WS6 item 38：extractor → gate → retractor → 撤销的**组合**验收。
 *
 * 与单件用例的分工：`ErrorCauseExtractorTest`/`GateTest`/`RetractorTest` 各自证明一段；
 * 本类把真实提取器的输出直接喂给门与落账器，证明三段串起来时的状态机与账本不变量：
 *
 * 1. 提取器只产 `PROPOSED` 候选，默认关闭的门零写入；
 * 2. 认证门放行后 candidate→observation `PROPOSED→ACCEPTED`、binding `ACTIVE`，
 *    `active_key` 等于作用域活键；
 * 3. 提取器自己产出的 CARELESS 候选（E03）被认证门按硬规则拒绝，零写入；
 * 4. 同 scope 第二条候选不能产生第二条 ACTIVE（`active_key` 唯一）；被拒候选留在 `PROPOSED`；
 * 5. 用户撤回写 `RETRACTED` observation + `VOIDED` binding、`active_key=NULL`，读侧为空；
 * 6. 撤回迟滞阻止自动写回；新独立负事件可重新落账，且仍只有一条 ACTIVE。
 *
 * 假端口按真 DAO 的判定顺序实现（先查 observation 状态，再查 `active_key` 占用），
 * 但不模拟 SQL 事务——事务回滚由设备用例
 * `ErrorCauseLedgerInstrumentedTest`（item 39/40）在真 SQLite 上证明。
 */
class ErrorCauseChainTest {

    private val extractor = ErrorCauseExtractor()
    private val certifiedGate = ErrorCauseGate(ErrorCauseGateState(certified = true))

    @Test
    fun `a default-closed seam keeps the extracted candidate as a proposal only`() = runBlocking {
        val port = InMemoryErrorCauseLedger()
        val service = ErrorCauseBookingService(ErrorCauseRetractor(port))

        assertTrue("生产默认门必须关闭", service.isGateClosed)
        val candidate = requireNotNull(extractor.extract(traceInput()))
        assertEquals(
            "提取器只产候选，不能自己落账",
            ErrorCauseObservationStatus.PROPOSED,
            candidate.status,
        )

        val outcome = service.proposeAndBook(
            input = traceInput(),
            corroborationCount = 99,
            retractedRecently = false,
            bindingId = "binding-1",
            decidedAtEpochMillis = 5_000,
        )

        assertEquals(ErrorCauseBookingOutcome.GATE_REJECTED, outcome)
        assertEquals("门拒绝时不能写 observation", 0, port.observations.size)
        assertEquals("门拒绝时不能写 binding", 0, port.bindings.size)
        assertNull(port.readActiveBinding(scope))
        assertEquals(0, port.attemptWrites)
    }

    @Test
    fun `the certified gate turns one extracted candidate into exactly one ACTIVE binding`() = runBlocking {
        val port = InMemoryErrorCauseLedger()
        val service = ErrorCauseBookingService(
            retractor = ErrorCauseRetractor(port),
            gate = certifiedGate,
        )
        assertFalse(service.isGateClosed)

        val outcome = service.proposeAndBook(
            input = traceInput(),
            corroborationCount = 2,
            retractedRecently = false,
            bindingId = "binding-1",
            decidedAtEpochMillis = 5_000,
        )

        assertEquals(ErrorCauseBookingOutcome.BOOKED, outcome)
        val active = requireNotNull(port.readActiveBinding(scope))
        assertEquals("binding-1", active.bindingId)
        assertEquals(ErrorCauseBindingState.ACTIVE, active.state)
        assertEquals(scope.activeKey(), active.activeKey)
        assertEquals(ErrorCauseAcceptanceSource.AUTO_GATE, active.acceptanceSource)
        assertEquals(certifiedGate.gateVersion, active.gateVersion)
        assertEquals(
            "来源 observation 必须从 PROPOSED 迁到 ACCEPTED",
            ErrorCauseObservationStatus.ACCEPTED,
            port.observations.getValue(active.sourceObservationId).status,
        )
        assertEquals(1, port.activeCount(scope))
        assertEquals("落账不是作答/掌握度重放", 0, port.attemptWrites)
    }

    @Test
    fun `the extractor's own careless candidate is refused by the certified gate`() = runBlocking {
        val port = InMemoryErrorCauseLedger()
        val service = ErrorCauseBookingService(
            retractor = ErrorCauseRetractor(port),
            gate = certifiedGate,
        )
        val careless = carelessInput()
        val candidate = requireNotNull(extractor.extract(careless))
        assertEquals(ErrorCause.CARELESS, candidate.evidence.cause)
        assertEquals(ErrorCauseChannel.REDO, candidate.evidence.channel)

        val outcome = service.proposeAndBook(
            input = careless,
            corroborationCount = 99,
            retractedRecently = false,
            bindingId = "binding-careless",
            decidedAtEpochMillis = 5_000,
        )

        assertEquals(
            "CARELESS 永不自动晋级为持久标签（R2 §3.3）",
            ErrorCauseBookingOutcome.GATE_REJECTED,
            outcome,
        )
        assertEquals(0, port.observations.size)
        assertEquals(0, port.bindings.size)
    }

    @Test
    fun `a second candidate in the same scope cannot create a second ACTIVE row`() = runBlocking {
        val port = InMemoryErrorCauseLedger()
        val service = ErrorCauseBookingService(
            retractor = ErrorCauseRetractor(port),
            gate = certifiedGate,
        )
        service.proposeAndBook(traceInput(), 2, false, "binding-1", 5_000)

        val secondInput = traceInput(attemptId = "attempt-2", messageId = "message-2")
        val secondCandidate = requireNotNull(extractor.extract(secondInput))
        val outcome = service.proposeAndBook(secondInput, 2, false, "binding-2", 6_000)

        assertEquals(
            "同 scope 已有 ACTIVE 时落账必须冲突，不能覆盖",
            ErrorCauseBookingOutcome.CONFLICT,
            outcome,
        )
        assertEquals(1, port.activeCount(scope))
        assertEquals(
            "被拒的候选是合法 observation，留在 PROPOSED",
            ErrorCauseObservationStatus.PROPOSED,
            port.observations.getValue(ErrorCauseIdentity.observationId(secondCandidate)).status,
        )
        assertFalse("被拒的 binding 不得留下一行", port.bindings.containsKey("binding-2"))
        assertEquals("binding-1", port.readActiveBinding(scope)?.bindingId)
    }

    @Test
    fun `user retraction voids the binding, clears the active key and keeps the audit rows`() = runBlocking {
        val port = InMemoryErrorCauseLedger()
        val retractor = ErrorCauseRetractor(port)
        retractor.bookIfAllowed(
            candidate = requireNotNull(extractor.extract(traceInput())),
            gate = certifiedGate,
            corroborationCount = 2,
            retractedRecently = false,
            bindingId = "binding-1",
            decidedAtEpochMillis = 5_000,
        )
        val sourceObservationId = port.bindings.getValue("binding-1").sourceObservationId

        val result = retractor.retractByUser(
            scope = scope,
            bindingId = "binding-1",
            reasonMarkdown = "这不是我的错因",
            evidenceRefs = listOf(
                ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.USER_CORRECTION, "user-correction-1"),
            ),
            observedAtEpochMillis = 6_000,
            decidedAtEpochMillis = 7_000,
            extractorVersion = ErrorCauseExtractor.LOCAL_EXTRACTOR_VERSION,
        )

        assertEquals(ErrorCauseWriteResult.APPLIED, result)
        assertNull("撤回后读侧必须看不到 ACTIVE", port.readActiveBinding(scope))
        assertEquals(0, port.activeCount(scope))

        val voided = port.bindings.getValue("binding-1")
        assertEquals(ErrorCauseBindingState.VOIDED, voided.state)
        assertNull("撤回必须腾出 active_key", voided.activeKey)
        assertEquals(7_000L, voided.endedAtEpochMillis)
        assertEquals("binding 行是状态迁移，不是删除", 1, port.bindings.size)

        val retraction = port.observations.values.single {
            it.status == ErrorCauseObservationStatus.RETRACTED
        }
        assertEquals(sourceObservationId, retraction.supersedesObservationId)
        assertEquals(ErrorCauseChannel.SELF_REPORT, retraction.channel)
        assertEquals(ErrorCauseDecisionSource.USER, retraction.decisionSource)
        assertEquals(
            "原 observation 仍是 ACCEPTED 审计行，不得抹掉",
            ErrorCauseObservationStatus.ACCEPTED,
            port.observations.getValue(sourceObservationId).status,
        )
        assertEquals(0, port.attemptWrites)
    }

    @Test
    fun `retraction hysteresis blocks auto write-back until a new negative event`() = runBlocking {
        val port = InMemoryErrorCauseLedger()
        val retractor = ErrorCauseRetractor(port)
        val service = ErrorCauseBookingService(retractor = retractor, gate = certifiedGate)
        service.proposeAndBook(traceInput(), 2, false, "binding-1", 5_000)
        retractor.retractByUser(
            scope = scope,
            bindingId = "binding-1",
            reasonMarkdown = "这不是我的错因",
            evidenceRefs = listOf(
                ErrorCauseEvidenceRef(ErrorCauseEvidenceKind.USER_CORRECTION, "user-correction-1"),
            ),
            observedAtEpochMillis = 6_000,
            decidedAtEpochMillis = 7_000,
            extractorVersion = ErrorCauseExtractor.LOCAL_EXTRACTOR_VERSION,
        )
        val newEvent = traceInput(attemptId = "attempt-2", messageId = "message-2")

        val blocked = service.proposeAndBook(newEvent, 2, retractedRecently = true, "binding-2", 8_000)
        assertEquals(ErrorCauseBookingOutcome.GATE_REJECTED, blocked)
        assertNull(port.readActiveBinding(scope))
        assertEquals(0, port.activeCount(scope))

        val allowed = service.proposeAndBook(newEvent, 2, retractedRecently = false, "binding-3", 9_000)
        assertEquals(ErrorCauseBookingOutcome.BOOKED, allowed)
        val active = requireNotNull(port.readActiveBinding(scope))
        assertEquals("binding-3", active.bindingId)
        assertEquals(scope.activeKey(), active.activeKey)
        assertEquals("重新落账后仍只有一条 ACTIVE", 1, port.activeCount(scope))
        assertEquals(ErrorCauseBindingState.VOIDED, port.bindings.getValue("binding-1").state)
    }

    private fun traceInput(
        attemptId: String = "attempt-1",
        messageId: String = "message-1",
    ) = ErrorCauseExtractionInput(
        scope = scope,
        attemptId = attemptId,
        observedAtEpochMillis = 1_000,
        traceSignals = listOf(
            ErrorCauseTraceSignal.RuleClaim(
                messageId = messageId,
                validity = StudentRuleValidity.INVALID,
            ),
        ),
    )

    private fun carelessInput() = ErrorCauseExtractionInput(
        scope = scope,
        attemptId = "attempt-careless",
        observedAtEpochMillis = 1_000,
        redo = ErrorCauseRedoOutcome(
            evidenceId = "redo-1",
            independent = true,
            correct = true,
            immediate = true,
            observedAtEpochMillis = 2_000,
        ),
        priorIndependentCorrectCount = 1,
    )

    /** 只实现标签链；`attemptWrites` 结构性恒为 0（本类没有 attempt 写口）。 */
    private class InMemoryErrorCauseLedger : ErrorCausePort {
        val observations = LinkedHashMap<String, ErrorCauseObservationRecord>()
        val bindings = LinkedHashMap<String, ErrorCauseBindingRecord>()
        var attemptWrites = 0

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

    private val scope = ErrorCauseScope(
        learnerId = "learner-1",
        practiceUnitId = "unit-1",
        problemRevisionId = "rev-1",
    )
}
