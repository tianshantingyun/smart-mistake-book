package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.figureBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureJsBasisFingerprint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-02 方法切换（批次 38 计划任务 1；设计 = `docs/research/2026-11-01-method-switching-and-transactions.md`
 * §2.4/§2.5，下称 R1）。载体 = JVM 真协调器（[DefaultFigureGenerationCoordinator]）+ fake 引擎/
 * 账本/通道/审计 + 调用侧编排 [FigureMethodSwitchRunner]，替身形状与
 * `FigureGenerationLocalBudgetBoundaryTest` 同型。
 *
 * 先红后绿口径：在 [FigureMethodSwitchRunner] 落树之前，本文件引用的编排类不存在，
 * 编译即红——红是预期，不是放宽理由；编排类落地后以下向量必须全绿。
 * 本批不运行 Gradle（门禁由脚本串行执行），红/绿运行状态由门禁验证。
 *
 * 三组验收（对应清单行"同 operationId、不新增确认、预算不重置、无双重载荷"）：
 * - **切换成功**：用例 1（JS 可归属失败 → 同 operationId 的 IMAGE 尝试，审计面全链钉住）+
 *   用例 2（设施类失败不切换——切换成功判据的 fail-closed 边界，R1 §2.4）；
 * - **切换后预算语义**：用例 3（账本门不因切换绕行/重置，R1 §2.5 用例 2）+
 *   用例 4（同 operationId 跨编排轮次账本计数续接）+
 *   用例 5（JS 专面墙钟耗尽仍允许转 IMAGE 一次、预算不迁移，R1 §2.5 用例 3 前向）+
 *   用例 6（最终图上限两方向都拒、切换不发起，R1 §2.5 用例 3 反向）；
 * - **双重载荷拒绝**：用例 7（切换全程恰一个产物：JS 腿 FAILED 无产物、IMAGE 腿唯一
 *   ImageAsset、fallback 提案零 program 残留，R1 §2.5 用例 5 的编排层半边——
 *   `tutor_message_visual` link 级断言属 Room 消费面，载体在消费接线（X-01），登记不混同）。
 *
 * IMAGE→JS（"图像不佳"）方向：R1 §2.4 裁定本轮不做机械回退（program 只能来自模型；
 * 本地无确定性质量判据），登记为模型回合驱动——本文件不含该方向的机械切换用例。
 */
class FigureMethodSwitchTest {

    /**
     * 用例 1（R1 §2.5 用例 1）：JS attempt 真实进引擎失败（可归属）→ 编排层以同 request
     * （= 同 operationId、不新增确认）发起 IMAGE attempt：账本恰预留 1 次、序号 1、
     * 操作终态 SUCCEEDED、两行 attempt（JS 负序号无预留 + IMAGE 序号 1）、
     * 两次 outcome 的 operationId 相等；fallback reason 登记切换来源（契约 §7 不隐藏）。
     */
    @Test
    fun jsProgramFailureFallsBackToImageWithinTheSameOperationId() = runBlocking {
        val engine = ScriptedVisualEngine(
            VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "program failed in phase render: ReferenceError: draw is not defined",
            ),
        )
        val ledger = CountingLedger()
        val fixture = Fixture(engine, ledger = ledger)
        val request = switchingRequest()

        val outcome = fixture.runner
            .generateWithMethodFallback(request, jsProposal(), fixture.imageFallback(request))

        assertEquals("切换后同 operationId", OPERATION_ID, outcome.operationId)
        assertEquals("JS 可归属失败必须切换到 IMAGE", FigureGenerationStatus.GENERATED, outcome.status)
        assertTrue("终态产物必须是 ImageAsset", outcome.artifact is FigureArtifact.ImageAsset)
        assertEquals("JS 腿真实执行一次", 1, engine.executeCount)
        assertEquals("IMAGE 腿恰预留一次", 1, ledger.reserveCount)
        assertEquals(1, fixture.channel.generateCalls)
        // 两次调用复用同一 request 实例：同 operationId、不新增身份、不新增确认面。
        assertEquals(2, fixture.spy.requests.size)
        assertTrue(
            "切换必须原样复用同一 request（同 operationId=派生零改动）",
            fixture.spy.requests[0] === fixture.spy.requests[1],
        )
        assertEquals(FigureGenerationMethod.JS, fixture.spy.proposals[0].method)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, fixture.spy.proposals[1].method)
        assertEquals(
            "fallback reason 必须登记切换来源（前一 attempt 有界诊断摘要）",
            true,
            fixture.spy.proposals[1].reason.contains("方法切换") &&
                fixture.spy.proposals[1].reason.contains("INVALID_PROGRAM"),
        )
        // 审计面（R1 §2.5 用例 1 的行级断言）：
        assertEquals("操作行建立两次（JS/IMAGE 各一次）", 2, fixture.audit.operations.size)
        assertEquals(
            "操作行指纹按各次方法派生；真实 DAO INSERT IGNORE 保留首个（JS 派生）",
            figureJsBasisFingerprint(OPERATION_ID, null, PROGRAM),
            fixture.audit.operations[0].basisFingerprint,
        )
        assertEquals(
            figureBasisFingerprint(OPERATION_ID, null, IMAGE_PROMPT),
            fixture.audit.operations[1].basisFingerprint,
        )
        assertEquals("JS 尝试一行（本地、无预留）", 1, fixture.audit.localAttempts.size)
        assertEquals(FigureGenerationMethod.JS, fixture.audit.localAttempts[0].method)
        assertNull("本地尝试不得有预留", fixture.audit.localAttempts[0].reservationId)
        assertTrue("JS 本地序号在负数区间", fixture.audit.localAttempts[0].attemptOrdinal < 0)
        assertEquals("IMAGE 尝试一行", 1, fixture.audit.remoteAttempts.size)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, fixture.audit.remoteAttempts[0].method)
        assertEquals("IMAGE 序号从账本计数起", 1, fixture.audit.remoteAttempts[0].attemptOrdinal)
        assertEquals(2, fixture.audit.finishedAttempts.size)
        assertEquals(FigureGenerationAuditOutcome.FAILED, fixture.audit.finishedAttempts[0].outcome)
        assertEquals(FigureGenerationAuditOutcome.SUCCEEDED, fixture.audit.finishedAttempts[1].outcome)
        assertEquals(2, fixture.audit.finishedOperations.size)
        assertEquals(
            "操作行短暂复活后终态=最后一次尝试的事实（FAILED→SUCCEEDED）",
            FigureGenerationAuditOutcome.SUCCEEDED,
            fixture.audit.finishedOperations[1].outcome,
        )
        assertEquals(1, fixture.audit.finishedOperations[1].reservedRemoteDispatches)
        assertEquals(1, fixture.audit.finishedOperations[1].usedRemoteDispatches)
    }

    /**
     * 用例 2（R1 §2.4 设施类行）：ENGINE_CLOSED 与 `[UNATTRIBUTED]` 两向量 → 协调器折
     * UNAVAILABLE → **不切换**（IMAGE 走同一凭证门大概率同败，回退会把"本机没接通"
     * 掩盖成"方法不行"；P-11：原因不可归属，不回喂）。
     */
    @Test
    fun facilityFailuresDoNotFallBackToImage() = runBlocking {
        val closedLedger = CountingLedger()
        val closedFixture = Fixture(
            ScriptedVisualEngine(
                VisualExecutionResult.Rejected(
                    reason = VisualExecutionRejection.ENGINE_CLOSED,
                    diagnostics = "engine already closed",
                ),
            ),
            ledger = closedLedger,
        )
        val closed = closedFixture.runner
            .generateWithMethodFallback(switchingRequest(), jsProposal(), closedFixture.nullFallback())

        assertEquals("引擎已关是设施类，如实回 UNAVAILABLE", FigureGenerationStatus.UNAVAILABLE, closed.status)
        assertEquals("设施类失败不得发起切换", 0, closedFixture.fallbackInvocations)
        assertEquals("不切换即不得预留", 0, closedLedger.reserveCount)
        assertEquals(0, closedFixture.backendOpens)
        assertEquals("UNAVAILABLE 但 JS 尝试已开行：如实落 FAILED attempt", 1, closedFixture.audit.localAttempts.size)
        assertEquals(1, closedFixture.audit.finishedAttempts.size)
        assertEquals(FigureGenerationAuditOutcome.FAILED, closedFixture.audit.finishedAttempts[0].outcome)
        assertTrue(
            "诊断保留分类名前缀：${closedFixture.audit.finishedAttempts[0].diagnostics}",
            closedFixture.audit.finishedAttempts[0].diagnostics.orEmpty().startsWith("ENGINE_CLOSED: "),
        )

        val unattributedLedger = CountingLedger()
        val unattributedFixture = Fixture(
            ScriptedVisualEngine(
                VisualExecutionResult.Rejected(
                    reason = VisualExecutionRejection.INVALID_PROGRAM,
                    diagnostics = "$UNATTRIBUTED_DIAGNOSTIC_PREFIX phase render failed with a " +
                        "message-less engine error",
                ),
            ),
            ledger = unattributedLedger,
        )
        val unattributed = unattributedFixture.runner
            .generateWithMethodFallback(switchingRequest(), jsProposal(), unattributedFixture.nullFallback())

        assertEquals(
            "[UNATTRIBUTED] 不可归属，不回喂不切换",
            FigureGenerationStatus.UNAVAILABLE,
            unattributed.status,
        )
        assertEquals(0, unattributedFixture.fallbackInvocations)
        assertEquals(0, unattributedLedger.reserveCount)
    }

    /**
     * 用例 3（R1 §2.5 用例 2）：账本预置已用 5 次（讲题终答位保留 → 可用上限 5）→ JS 失败
     * → 切换后的 IMAGE 预留被拒 → 终态 FAILED、已用计数不变、无第 7 次派发、诊断含真实计数。
     * 向量 b：无终答位、6 次用尽，同样拒绝——切换不得绕行账本（预算事实）。
     *
     * fake 账本门与 `RoomFigureDispatchLedger` 同形：usableLimit = MAX_DISPATCHES −
     * （终答位 ? 1 : 0），拒绝消息携带真实已用计数（KD-60）。
     */
    @Test
    fun exhaustedRemoteBudgetDeniesTheSwitchedAttemptWithoutResetting() = runBlocking {
        val reservedRequest = switchingRequest(reserveChatFinalAnswer = true)
        val reservedLedger = CappedLedger(cap = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES, preUsed = 5)
        val reservedFixture = Fixture(ScriptedVisualEngine(failingProgram()), ledger = reservedLedger)
        val reservedOutcome = reservedFixture.runner.generateWithMethodFallback(
            reservedRequest,
            jsProposal(),
            reservedFixture.imageFallback(reservedRequest),
        )

        assertEquals("终答位占用下 5 次已用尽即拒", FigureGenerationStatus.FAILED, reservedOutcome.status)
        assertTrue(
            "拒绝诊断携带真实计数（KD-60）：${reservedOutcome.diagnostics}",
            reservedOutcome.diagnostics.orEmpty().contains("已派发 5 次"),
        )
        assertEquals("已用计数不得因切换变化", 5, reservedLedger.used)
        assertEquals(1, reservedLedger.reserveCalls)
        assertEquals(0, reservedLedger.grantedCount)
        assertEquals(1, reservedLedger.deniedCount)
        assertEquals("没有第 7 次派发", 0, reservedFixture.channel.generateCalls)
        assertEquals("预留被拒不建 attempt 行", 0, reservedFixture.audit.remoteAttempts.size)
        assertEquals(2, reservedFixture.audit.finishedOperations.size)
        assertEquals(FigureGenerationAuditOutcome.FAILED, reservedFixture.audit.finishedOperations[1].outcome)
        assertEquals(0, reservedFixture.audit.finishedOperations[1].usedRemoteDispatches)

        val plainLedger = CappedLedger(cap = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES, preUsed = 6)
        val plainFixture = Fixture(ScriptedVisualEngine(failingProgram()), ledger = plainLedger)
        val plainRequest = switchingRequest()
        val plainOutcome = plainFixture.runner
            .generateWithMethodFallback(plainRequest, jsProposal(), plainFixture.imageFallback(plainRequest))

        assertEquals("无终答位时 6 次用尽同样拒", FigureGenerationStatus.FAILED, plainOutcome.status)
        assertTrue(
            "拒绝诊断携带真实计数：${plainOutcome.diagnostics}",
            plainOutcome.diagnostics.orEmpty().contains("已派发 6 次"),
        )
        assertEquals(6, plainLedger.used)
        assertEquals(0, plainLedger.grantedCount)
        assertEquals(0, plainFixture.channel.generateCalls)
    }

    /**
     * 用例 4：同 operationId 连续两轮编排（同 request 两轮各自 JS 失败 → 切换）——账本
     * per-operation 计数续接（序号 1、2），不因切换重置、不换 operationId
     * （契约 §7"换方法不换 operationId，不隐藏重试/审查请求"）。
     */
    @Test
    fun aSecondSwitchingRunContinuesTheSameLedgerCounter() = runBlocking {
        val ledger = CountingLedger()
        val fixture = Fixture(ScriptedVisualEngine(failingProgram()), ledger = ledger)
        val request = switchingRequest()

        val first = fixture.runner.generateWithMethodFallback(request, jsProposal(), fixture.imageFallback(request))
        val second = fixture.runner.generateWithMethodFallback(request, jsProposal(), fixture.imageFallback(request))

        assertEquals(FigureGenerationStatus.GENERATED, first.status)
        assertEquals(FigureGenerationStatus.GENERATED, second.status)
        assertEquals("两轮同 operationId", OPERATION_ID, second.operationId)
        assertEquals("账本计数跨轮续接：序号 1、2", listOf(1, 2), fixture.audit.remoteAttempts.map { it.attemptOrdinal })
        assertEquals(2, ledger.reserveCount)
        assertEquals(2, fixture.channel.generateCalls)
        assertEquals(2, fixture.audit.localAttempts.size)
    }

    /**
     * 用例 5（R1 §2.5 用例 3 前向）：JS 累计墙钟恰达 10 s → 第 5 次 JS attempt REJECTED
     * （不进引擎、不建行）→ **允许转 IMAGE 一次**（JS 专面预算不迁移；IMAGE 由账本另管）。
     * 前 4 次直接打协调器（与 A-10 边界用例同型），第 5 次走编排层。
     */
    @Test
    fun jsWallClockExhaustionStillAllowsOneImageSwitchWithoutMigratingJsBudgets() = runBlocking {
        val engine = ScriptedVisualEngine(
            VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "slow program failed",
            ),
        )
        var ticks = 0L
        val ledger = CountingLedger()
        val fixture = Fixture(engine, ledger = ledger, now = { ticks += 2_500; ticks })

        repeat(4) { index ->
            val outcome = fixture.spy.generate(switchingRequest(), jsProposal("const v = { t: $index };"))
            assertEquals("第 ${index + 1} 次 JS 尝试应真实执行后失败", FigureGenerationStatus.FAILED, outcome.status)
        }
        assertEquals(4, engine.executeCount)

        val request = switchingRequest()
        val outcome = fixture.runner
            .generateWithMethodFallback(request, jsProposal("const v = { t: 5 };"), fixture.imageFallback(request))

        assertEquals("墙钟耗尽后的 JS REJECTED 不迁移到 IMAGE——切换照常发起", FigureGenerationStatus.GENERATED, outcome.status)
        assertEquals("第 5 次 JS 不得进引擎", 4, engine.executeCount)
        assertEquals("IMAGE 腿恰预留一次", 1, ledger.reserveCount)
        assertEquals(1, fixture.channel.generateCalls)
        assertEquals(1, fixture.audit.remoteAttempts.size)
    }

    /**
     * 用例 6（R1 §2.5 用例 3 反向）：同一操作已产出 2 份最终图 → 第 3 次生成的 admitFinalFigure
     * 门在分派前拒绝（跨方法合计计数，门在方法分派前）——编排层**不发起** fallback
     * （该门两方向都拒；REJECTED 文案不含 JS 专面令牌）。
     */
    @Test
    fun finalFigureCapRejectsBeforeDispatchAndBlocksTheSwitchToo() = runBlocking {
        val engine = ScriptedVisualEngine(
            VisualExecutionResult.Rendered(sceneJson = "{}", sceneSchemaVersion = 1, contentIds = emptySet()),
        )
        val ledger = CountingLedger()
        val fixture = Fixture(engine, ledger = ledger)
        val request = switchingRequest()

        repeat(2) {
            val outcome = fixture.spy.generate(request, jsProposal())
            assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        }

        engine.script(
            VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "third attempt failed",
            ),
        )
        val third = fixture.runner
            .generateWithMethodFallback(request, jsProposal("const v = { fail: true };"), fixture.imageFallback(request))

        assertEquals("第 3 份最终图两方向都拒", FigureGenerationStatus.REJECTED, third.status)
        assertTrue(
            "拒绝说明最终图上限：${third.diagnostics}",
            third.diagnostics.orEmpty().contains("最终图"),
        )
        assertEquals("跨方法门拒绝不得发起 fallback", 0, fixture.fallbackInvocations)
        assertEquals("被拒的第 3 次 JS 不得进引擎", 2, engine.executeCount)
        assertEquals("不预留、不开后端", 0, ledger.reserveCount)
        assertEquals(0, fixture.backendOpens)
    }

    /**
     * 用例 7（R1 §2.5 用例 5 的编排层半边）：JS FAILED + IMAGE GENERATED → 全程恰一个产物：
     * JS 腿 FAILED 不带产物、IMAGE 腿唯一 ImageAsset、fallback 提案零 program 残留
     * （policy 载荷互斥的载荷面）、审计恰一次 SUCCEEDED（失败的 JS 腿不得留下成功/载荷痕迹）。
     * `tutor_message_visual` link 级断言（(message_id, ordinal) 唯一）属 Room 消费面，
     * 载体在消费接线（X-01）——登记不混同。
     */
    @Test
    fun switchedOutcomeCarriesExactlyOneImagePayloadWithoutProgramResidue() = runBlocking {
        val ledger = CountingLedger()
        val fixture = Fixture(ScriptedVisualEngine(failingProgram()), ledger = ledger)
        val request = switchingRequest()

        val outcome = fixture.runner.generateWithMethodFallback(request, jsProposal(), fixture.imageFallback(request))

        assertNull("JS 腿 FAILED 不带产物", fixture.spy.outcomes[0].artifact)
        assertTrue("IMAGE 腿唯一产物是 ImageAsset", fixture.spy.outcomes[1].artifact is FigureArtifact.ImageAsset)
        assertEquals("编排返回的就是 IMAGE 腿的产物（无双载荷合并形状）", outcome.artifact, fixture.spy.outcomes[1].artifact)
        assertNull("fallback 提案不得携带 program（载荷互斥）", fixture.spy.proposals[1].program)
        assertEquals(FigureImageOperation.GENERATE, fixture.spy.proposals[1].imageOperation)
        assertEquals(1, fixture.audit.finishedAttempts.count { it.outcome == FigureGenerationAuditOutcome.SUCCEEDED })
        assertEquals(1, fixture.audit.finishedAttempts.count { it.outcome == FigureGenerationAuditOutcome.FAILED })
        assertEquals(1, fixture.audit.finishedOperations.count { it.outcome == FigureGenerationAuditOutcome.SUCCEEDED })
    }

    // -----------------------------------------------------------------

    /** 可改脚本的引擎替身：同一引擎先 Rendered 后 Rejected 的用例需要换脚本。 */
    private class ScriptedVisualEngine(
        private var result: VisualExecutionResult,
    ) : VisualExecutionEngine {
        var executeCount: Int = 0
            private set

        fun script(result: VisualExecutionResult) {
            this.result = result
        }

        override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
            executeCount += 1
            return result
        }

        override fun close() = Unit
    }

    /** 记录审计调用序列的替身；本地序号按持久层契约在负数区间分配。 */
    private class RecordingAuditRecorder : FigureGenerationAuditRecorder {
        val operations = mutableListOf<FigureGenerationAuditOperation>()
        val localAttempts = mutableListOf<FigureGenerationAuditAttempt>()
        val remoteAttempts = mutableListOf<FigureGenerationAuditAttempt>()
        val finishedAttempts = mutableListOf<FinishedAttempt>()
        val finishedOperations = mutableListOf<FinishedOperation>()

        private var localOrdinal = 0

        override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean {
            operations += operation
            return true
        }

        override suspend fun markOperationRunning(operationId: String, updatedAtEpochMillis: Long): Boolean = true

        override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean {
            remoteAttempts += attempt
            return true
        }

        override suspend fun beginLocalAttempt(
            operationId: String,
            method: FigureGenerationMethod,
            inputFingerprint: String,
            sourceSha256: String?,
            startedAtEpochMillis: Long,
        ): FigureGenerationAuditAttempt {
            localOrdinal -= 1
            return FigureGenerationAuditAttempt(
                attemptId = "figure-attempt:$operationId:$localOrdinal:${method.name}",
                operationId = operationId,
                attemptOrdinal = localOrdinal,
                method = method,
                reservationId = null,
                inputFingerprint = inputFingerprint,
                sourceSha256 = sourceSha256,
                startedAtEpochMillis = startedAtEpochMillis,
            ).also { localAttempts += it }
        }

        override suspend fun finishAttempt(
            attemptId: String,
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
            endedAtEpochMillis: Long,
        ): Boolean {
            finishedAttempts += FinishedAttempt(attemptId, outcome, outputAssetId, diagnostics)
            return true
        }

        override suspend fun finishOperation(
            operationId: String,
            outcome: FigureGenerationAuditOutcome,
            reservedRemoteDispatches: Int,
            usedRemoteDispatches: Int,
            finalAnswerReserved: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean {
            finishedOperations += FinishedOperation(
                operationId,
                outcome,
                reservedRemoteDispatches,
                usedRemoteDispatches,
                finalAnswerReserved,
            )
            return true
        }

        override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) = Unit
    }

    private data class FinishedAttempt(
        val attemptId: String,
        val outcome: FigureGenerationAuditOutcome,
        val outputAssetId: String?,
        val diagnostics: String?,
    )

    private data class FinishedOperation(
        val operationId: String,
        val outcome: FigureGenerationAuditOutcome,
        val reservedRemoteDispatches: Int,
        val usedRemoteDispatches: Int,
        val finalAnswerReserved: Boolean,
    )

    /** 记录每次 generate 的 request/proposal/outcome 的协调器装饰器。 */
    private class RecordingCoordinator(
        private val delegate: FigureGenerationCoordinator,
    ) : FigureGenerationCoordinator {
        val requests = mutableListOf<FigureGenerationRequest>()
        val proposals = mutableListOf<FigureGenerationProposal>()
        val outcomes = mutableListOf<FigureGenerationOutcome>()

        override suspend fun generate(
            request: FigureGenerationRequest,
            proposal: FigureGenerationProposal,
        ): FigureGenerationOutcome = delegate.generate(request, proposal).also {
            requests += request
            proposals += proposal
            outcomes += it
        }
    }

    private class CountingLedger : RemoteModelDispatchLedger {
        var reserveCount: Int = 0
            private set

        override suspend fun reserve(
            request: RemoteModelDispatchRequest,
        ): RemoteModelDispatchDecision {
            reserveCount += 1
            return RemoteModelDispatchDecision.Granted(
                RemoteModelDispatchReservation(
                    reservationId = "reservation-${request.operationId}-$reserveCount",
                    operationId = request.operationId,
                    parent = request.parent,
                    remoteKind = request.remoteKind,
                    method = request.method,
                    purpose = request.purpose,
                    policyVersion = request.policyVersion,
                    sourceImageSha256 = request.sourceImageSha256,
                    configurationFingerprint = request.configurationFingerprint,
                    attemptOrdinal = reserveCount,
                    reservedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
        }
    }

    /**
     * 与 `RoomFigureDispatchLedger` 的门同形的 fake：per-operation 计数（预置 [preUsed]）、
     * 上限 [cap]、讲题终答位（usableLimit = cap − 终答位），拒绝消息携带真实已用计数。
     */
    private class CappedLedger(
        private val cap: Int,
        private val preUsed: Int,
    ) : RemoteModelDispatchLedger {
        var reserveCalls: Int = 0
            private set
        var grantedCount: Int = 0
            private set
        var deniedCount: Int = 0
            private set

        val used: Int
            get() = preUsed + grantedCount

        override suspend fun reserve(
            request: RemoteModelDispatchRequest,
        ): RemoteModelDispatchDecision {
            reserveCalls += 1
            val reserveChatFinalAnswer =
                (request.parent as? FigureDispatchParent.ModelTask)?.reserveChatFinalAnswer ?: false
            val usableLimit = cap - if (reserveChatFinalAnswer) 1 else 0
            val nextUsed = used + 1
            if (nextUsed > usableLimit) {
                deniedCount += 1
                return RemoteModelDispatchDecision.Denied(
                    code = RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED,
                    message = "逻辑操作的远程派发预算已用尽：本逻辑操作已派发 $used 次" +
                        "（上限 $usableLimit 次" +
                        (if (reserveChatFinalAnswer) "，另为讲题终答保留 1 次" else "") +
                        "）；图像生成与讲题工具轮共享同一份 durable 预算；未出网",
                )
            }
            grantedCount += 1
            return RemoteModelDispatchDecision.Granted(
                RemoteModelDispatchReservation(
                    reservationId = "reservation-${request.operationId}-$nextUsed",
                    operationId = request.operationId,
                    parent = request.parent,
                    remoteKind = request.remoteKind,
                    method = request.method,
                    purpose = request.purpose,
                    policyVersion = request.policyVersion,
                    sourceImageSha256 = request.sourceImageSha256,
                    configurationFingerprint = request.configurationFingerprint,
                    attemptOrdinal = nextUsed,
                    reservedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
        }
    }

    private class StubChannel : ImageGenerationChannel {
        var generateCalls: Int = 0
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            return ImageRedrawResult(
                imageBytes = byteArrayOf(1, 2, 3),
                mimeType = "image/png",
                modelVersion = "test-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used in this test")
    }

    /** 一次编排测试的全部替身与计数（协调器为真实实现，只 fake 它的下沉端口）。 */
    private class Fixture(
        engine: VisualExecutionEngine,
        now: () -> Long = { 1_000L },
        ledger: RemoteModelDispatchLedger,
    ) {
        val channel = StubChannel()
        val audit = RecordingAuditRecorder()
        var backendOpens: Int = 0
            private set
        val spy = RecordingCoordinator(
            DefaultFigureGenerationCoordinator(
                ledger = ledger,
                openBackend = {
                    backendOpens += 1
                    FigureImageBackend(
                        configurationFingerprint = "config-1",
                        openChannel = { channel },
                        closeAction = {},
                    )
                },
                now = now,
                audit = audit,
                visualEngine = engine,
                visualFrameInputs = { _, _ -> FRAME },
            ),
        )
        val runner = FigureMethodSwitchRunner(spy)
        var fallbackInvocations: Int = 0
            private set

        /** R1 §2.2：fallback 用现成工厂构造（同 request=同 operationId），reason 登记切换来源。 */
        fun imageFallback(request: FigureGenerationRequest): (String) -> FigureGenerationProposal =
            { primaryFailure ->
                fallbackInvocations += 1
                FigureGenerationPolicy.imageModelProposal(
                    request = request,
                    imageOperation = FigureImageOperation.GENERATE,
                    imagePrompt = IMAGE_PROMPT,
                    reason = "方法切换：JS 尝试失败（${primaryFailure.take(REASON_DIAGNOSTIC_CHARS)}）",
                )
            }

        /** 恒不发起 fallback 的工厂（负向用例用它断言"未被调用"）。 */
        fun nullFallback(): (String) -> FigureGenerationProposal? =
            { _ ->
                fallbackInvocations += 1
                null
            }

        private companion object {
            const val REASON_DIAGNOSTIC_CHARS: Int = 200
        }
    }

    private fun failingProgram(): VisualExecutionResult =
        VisualExecutionResult.Rejected(
            reason = VisualExecutionRejection.INVALID_PROGRAM,
            diagnostics = "program failed in phase render: ReferenceError: draw is not defined",
        )

    /** 切换能力的宿主形状：两方法都真实声明可用（policy 对 fallback 的授权门）。 */
    private fun switchingRequest(
        operationId: String = OPERATION_ID,
        reserveChatFinalAnswer: Boolean = false,
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = operationId,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL),
            supportsImageGeneration = true,
        ),
        // 未预读账本（生产形状，KD-60）：声明面放行，持久账本做真门。
        budget = FigureGenerationBudget(
            remainingRemoteDispatches = null,
            reserveChatFinalAnswer = reserveChatFinalAnswer,
        ),
        dispatchParent = FigureDispatchParent.ModelTask("task-1", reserveChatFinalAnswer),
        source = null,
    )

    private fun jsProposal(program: String = PROGRAM): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        method = FigureGenerationMethod.JS,
        reason = "测试",
        program = program,
        fidelity = FigureFidelity.FAITHFUL,
    )

    /**
     * 用例 8（批次 38 收口裁定：M-03×V-02 跨特性交互）：4MP 排版预算越界经引擎折
     * `INVALID_PROGRAM: LAYOUT_PIXEL_BUDGET: …`——**资源侧限制不切换**（自动切 IMAGE
     * = 烧付费派发绕本地资源门）。fail-closed：原样 FAILED、带码诊断保留、零预留、
     * 零 fallback、零 backend。灭掉的失败：把排版预算耗尽伪装成"程序错"而静默付费
     * 升级到远程图像生成。
     */
    @Test
    fun layoutPixelBudgetOverflowDoesNotFallBackToImage() = runBlocking {
        val ledger = CountingLedger()
        val fixture = Fixture(
            ScriptedVisualEngine(
                VisualExecutionResult.Rejected(
                    reason = VisualExecutionRejection.INVALID_PROGRAM,
                    diagnostics = "INVALID_PROGRAM: LAYOUT_PIXEL_BUDGET: " +
                        "transaction layout pixels 5200000 du² exceed 4194304 du² budget",
                ),
            ),
            ledger = ledger,
        )
        val outcome = fixture.runner
            .generateWithMethodFallback(switchingRequest(), jsProposal(), fixture.nullFallback())

        assertEquals("资源侧限制如实 FAILED，不切 IMAGE", FigureGenerationStatus.FAILED, outcome.status)
        assertEquals("排版预算越界不得发起切换", 0, fixture.fallbackInvocations)
        assertEquals("不切换即不得预留", 0, ledger.reserveCount)
        assertEquals(0, fixture.backendOpens)
        assertTrue(
            "带码诊断原样保留：${fixture.audit.finishedAttempts[0].diagnostics}",
            fixture.audit.finishedAttempts[0].diagnostics.orEmpty().contains("LAYOUT_PIXEL_BUDGET"),
        )
    }

    private companion object {
        const val OPERATION_ID: String = "op-switch-1"
        const val PROGRAM: String = "const visual = { draw: true };"
        const val IMAGE_PROMPT: String = "画一张数轴示意图。"
        val FRAME = VisualFrameSnapshot(
            viewport = VisualViewport(360, 240),
            theme = "light",
            seed = 42L,
            timeMs = 1_700_000_000_000L,
        )
    }
}
