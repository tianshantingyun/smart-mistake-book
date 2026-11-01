package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
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
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.figureBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 4E.1 协调器状态机的 JVM 测试（任务书 `FigureGenerationCoordinatorTest`）。
 *
 * 钉住四条硬序列：
 * 1. policy 拒绝 → 不打开后端、不预留、不调用通道；
 * 2. 账本拒绝 → 通道调用 = 0（"无预留 → 0 HTTP"）；
 * 3. 账本授予 → 通道只调用一次，且拿到与预留逐项一致的绑定；
 * 4. 通道失败 → 有界诊断，不抛出假成功。
 *
 * 批次 34（A-01）追加：JS 双态与 **0 远程派发** 的可观测断言——
 * - 未注入引擎 → UNAVAILABLE（与改前同一文案，不建 attempt/operation 行）；
 * - 注入引擎 + 帧输入 → 本地执行，`ledger.requests` 为空、后端 0 次、通道 0 次，
 *   attempt 行 `method=JS`/`reservationId=null`，operation 终态计数 `0/0`；
 * - 注入引擎后 IMAGE_MODEL 路径逐项不变（预留一次、通道一次、引擎 0 次）；
 * - 不同 JS program 得到不同 basis 指纹（A-08）。
 */
class FigureGenerationCoordinatorTest {

    @Test
    fun aPolicyRejectionTouchesNeitherTheLedgerNorTheBackend() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        var backendOpens = 0
        val coordinator = coordinator(ledger, channel) { backendOpens += 1 }

        val request = request()
        val invalid = FigureGenerationProposal(
            decision = com.tingyun.smartmistakebook.core.model.FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
        )
        val outcome = coordinator.generate(request, invalid)

        assertEquals(FigureGenerationStatus.REJECTED, outcome.status)
        assertEquals(0, backendOpens)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
        assertNull(outcome.artifact)
    }

    @Test
    fun aDeniedReservationNeverCallsTheChannel() = runBlocking {
        val ledger = RecordingLedger(granted = false)
        val channel = RecordingChannel(ledger.events)
        var backendOpens = 0
        val coordinator = coordinator(ledger, channel) { backendOpens += 1 }

        val outcome = coordinator.generate(request(), imageProposal(request()))

        assertEquals(FigureGenerationStatus.FAILED, outcome.status)
        assertEquals(1, backendOpens)
        assertEquals(1, ledger.requests.size)
        assertEquals(0, channel.generateCalls)
        assertEquals(listOf("reserve"), ledger.events)
        assertTrue(outcome.diagnostics?.contains("预算") == true)
    }

    @Test
    fun aGrantedReservationCallsTheChannelOnceWithItsExactBinding() = runBlocking {
        val ledger = RecordingLedger(attemptOrdinal = 3)
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel) {}

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertEquals(1, channel.generateCalls)
        val dispatch = channel.dispatches.single()
        assertEquals("op-1", dispatch.operationId)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, dispatch.method)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, dispatch.purpose)
        assertEquals(3, dispatch.attemptOrdinal)
        assertEquals("config-1", dispatch.configurationFingerprint)
        assertEquals(figureSha256Hex(SOURCE_BYTES), dispatch.sourceImageSha256)
        assertEquals(listOf("reserve", "post"), ledger.events)

        val artifact = requireNotNull(outcome.artifact)
        assertTrue(artifact is com.tingyun.smartmistakebook.core.model.FigureArtifact.ImageAsset)
        val image = artifact as com.tingyun.smartmistakebook.core.model.FigureArtifact.ImageAsset
        assertEquals(figureSha256Hex(channel.resultBytes), image.contentSha256)
        assertEquals("test-model", image.modelId)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, image.method)
    }

    @Test
    fun noBackendIsUnavailableWithoutReserving() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
        )

        val outcome = coordinator.generate(request(), imageProposal(request()))

        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
    }

    @Test
    fun anEditSendsTheVerifiedSourceBytes() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel) {}

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        val sent = channel.requests.single()
        assertEquals("基于已授权原图重建干净题面。", sent.prompt)
        assertNotNull(sent.sourceImageBytes)
        assertTrue(sent.sourceImageBytes!!.contentEquals(SOURCE_BYTES))
        assertEquals("image/png", sent.sourceImageMimeType)
    }

    @Test
    fun aGenerateSendsNoSourceWhenNoneWasProvided() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel) {}

        val request = request(source = null)
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.GENERATE,
            imagePrompt = "画一张数轴示意图。",
            reason = "测试",
        )
        val outcome = coordinator.generate(request, proposal)

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertNull(channel.requests.single().sourceImageBytes)
        assertNull(channel.dispatches.single().sourceImageSha256)
    }

    @Test
    fun aChannelFailureBecomesABoundedFailure() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events, fail = true)
        val coordinator = coordinator(ledger, channel) {}

        val outcome = coordinator.generate(request(), imageProposal(request()))

        assertEquals(FigureGenerationStatus.FAILED, outcome.status)
        assertNull(outcome.artifact)
        val diagnostics = requireNotNull(outcome.diagnostics)
        assertTrue(diagnostics.length <= 512)
        assertTrue(diagnostics.contains("provider down"))
    }

    @Test
    fun nonGeneratingDecisionsDoNotReserve() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel) {}

        val request = request()
        val outcome = coordinator.generate(
            request,
            FigureGenerationPolicy.noFigureProposal(request, "纯文字已经足够"),
        )

        assertEquals(FigureGenerationStatus.NO_FIGURE, outcome.status)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
    }

    @Test
    fun aJavascriptProposalWithoutAnEngineStaysUnavailable() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val recorder = RecordingAuditRecorder()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            audit = recorder,
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request))

        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        assertTrue("未接通必须如实说没有运行库", outcome.diagnostics?.contains("JS") == true)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
        assertNull(outcome.artifact)
        assertNull("没有开始执行就不该有 attempt", outcome.attemptId)
        assertTrue("没有引擎时不该建 attempt 行", recorder.attempts.isEmpty())
        assertTrue("没有执行就不该有 operation 行", recorder.operations.isEmpty())
    }

    @Test
    fun aJavascriptProposalWithoutFrameInputsStaysUnavailable() = runBlocking {
        val ledger = RecordingLedger()
        val engine = RecordingVisualEngine()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            visualEngine = engine,
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request))

        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        assertTrue("缺宿主帧输入时不得自己编 viewport/theme", outcome.diagnostics?.contains("帧输入") == true)
        assertTrue(engine.requests.isEmpty())
        assertTrue(ledger.requests.isEmpty())
        assertNull(outcome.attemptId)
    }

    @Test
    fun aJavascriptProposalIsExecutedLocallyWithoutReserving() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine()
        var backendOpens = 0
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = {
                backendOpens += 1
                FigureImageBackend(
                    configurationFingerprint = "config-1",
                    openChannel = { channel },
                    closeAction = {},
                )
            },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request, PROGRAM))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        // "0 远程派发"的三层正面证据：不预留、不开图像后端、不调通道。
        assertEquals(0, backendOpens)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)

        val artifact = requireNotNull(outcome.artifact)
        assertTrue(artifact is FigureArtifact.Program)
        val program = artifact as FigureArtifact.Program
        assertEquals(FigureGenerationMethod.JS, program.method)
        assertEquals(PROGRAM, program.sourceJs)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, program.purpose)

        val sent = engine.requests.single()
        assertEquals(PROGRAM, sent.sourceJs)
        assertEquals(program.programId, sent.programId)
        assertTrue(sent.programId.startsWith("visual:op-1:"))
        assertEquals(FRAME.viewport, sent.viewport)
        assertEquals(FRAME.theme, sent.theme)
        assertEquals(FRAME.seed, sent.seed)
        assertEquals(FRAME.timeMs, sent.timeMs)
        assertEquals(1, sent.sceneRevision)
        assertNull(sent.stateJson)
        assertEquals(false, sent.explicitUserRetry)

        val attempt = recorder.attempts.single()
        assertEquals(FigureGenerationMethod.JS, attempt.method)
        assertEquals(-1, attempt.attemptOrdinal)
        assertNull("本地方法没有远程预留", attempt.reservationId)
        assertEquals(outcome.attemptId, attempt.attemptId)
        assertEquals(recorder.operations.single().basisFingerprint, attempt.inputFingerprint)

        val operationFinish = recorder.operationFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.SUCCEEDED, operationFinish.outcome)
        assertEquals(0, operationFinish.reservedRemoteDispatches)
        assertEquals(0, operationFinish.usedRemoteDispatches)
        assertEquals(FigureGenerationAuditOutcome.SUCCEEDED, recorder.attemptFinishes.single().second)
    }

    @Test
    fun aJavascriptProposalAtZeroRemoteBudgetStillRunsLocally() = runBlocking {
        val ledger = RecordingLedger()
        val engine = RecordingVisualEngine()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest(
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 0,
                reserveChatFinalAnswer = true,
            ),
        )
        val outcome = coordinator.generate(request, jsProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertTrue("本地执行不得因远程余额为 0 被拒", ledger.requests.isEmpty())
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun aRejectedJavascriptExecutionRecordsAFailedAttemptAndZeroDispatches() = runBlocking {
        val ledger = RecordingLedger()
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "program did not evaluate",
            ),
        )
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request))

        assertEquals(FigureGenerationStatus.FAILED, outcome.status)
        assertNull(outcome.artifact)
        val diagnostics = requireNotNull(outcome.diagnostics)
        assertTrue(diagnostics.contains("INVALID_PROGRAM"))
        assertTrue(diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(FigureGenerationAuditOutcome.FAILED, recorder.attemptFinishes.single().second)
        assertEquals(0, recorder.operationFinishes.single().reservedRemoteDispatches)
        assertEquals(0, recorder.operationFinishes.single().usedRemoteDispatches)
    }

    @Test
    fun aCancelledJavascriptExecutionLandsCancelledRowsWithoutDispatch() = runBlocking {
        val ledger = RecordingLedger()
        val recorder = RecordingAuditRecorder()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            audit = recorder,
            visualEngine = RecordingVisualEngine(failure = CancellationException("cancelled")),
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        try {
            coordinator.generate(request, jsProposal(request))
            fail("取消必须原样抛出")
        } catch (_: CancellationException) {
            // expected
        }

        assertTrue(ledger.requests.isEmpty())
        assertEquals(FigureGenerationAuditOutcome.CANCELLED, recorder.attemptFinishes.single().second)
        val operationFinish = recorder.operationFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.CANCELLED, operationFinish.outcome)
        assertEquals(0, operationFinish.reservedRemoteDispatches)
        assertEquals(0, operationFinish.usedRemoteDispatches)
    }

    @Test
    fun anEngineClosedRejectionIsReportedUnavailable() = runBlocking {
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.ENGINE_CLOSED,
                diagnostics = "engine closed",
            ),
        )
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = RecordingLedger(),
            openBackend = { null },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request))

        // 引擎已关 = 本机没有可用引擎，与"未接通"同一语义，不冒充失败也不冒充成功。
        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        assertTrue(requireNotNull(outcome.diagnostics).contains("ENGINE_CLOSED"))
        assertEquals(FigureGenerationAuditOutcome.FAILED, recorder.attemptFinishes.single().second)
    }

    /**
     * P-11 消费点（批次 35）：引擎失败但原因不可归属（分类器"丢失哨兵 + 低堆"兜底，诊断以
     * `[UNATTRIBUTED]` 前缀开头——与设备 P4 用例同形）时，出口走设施类 UNAVAILABLE，与程序错
     * FAILED 区分；不折"程序错"回喂模型去修 program（R3 §6.6 退化口径从登记变生效）。
     * 对照臂（防过拒）：无前缀的 INVALID_PROGRAM 仍 FAILED
     * （[aRejectedJavascriptExecutionRecordsAFailedAttemptAndZeroDispatches]）。
     */
    @Test
    fun anUnattributedEngineFailureIsNotFedBackAsAProgramError() = runBlocking {
        val ledger = RecordingLedger()
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                // 字面量是跨进程契约：协调器消费的常量与它漂移时，本用例立红。
                diagnostics = "[UNATTRIBUTED] phase render failed with a message-less " +
                    "engine error (raw=null, mallocSize=1000000, mallocLimit=16777216)",
            ),
        )
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request))

        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        // 保留半边：前缀在 boundedRejection 之后逐字存活进 outcome。
        val diagnostics = requireNotNull(outcome.diagnostics)
        assertTrue(
            "the [UNATTRIBUTED] prefix must survive boundedRejection: $diagnostics",
            diagnostics.startsWith("INVALID_PROGRAM: [UNATTRIBUTED]"),
        )
        assertTrue(diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        // 审计 attempt 终态诊断同样保留前缀（boundedRejection 不吃掉它）。
        assertTrue(
            requireNotNull(recorder.attemptFinishes.single().third)
                .startsWith("INVALID_PROGRAM: [UNATTRIBUTED]"),
        )
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, recorder.operationFinishes.single().reservedRemoteDispatches)
        assertEquals(0, recorder.operationFinishes.single().usedRemoteDispatches)
    }

    /**
     * M-04 案 B 的协调器侧半边：度量设施失败的稳定标记（MEASUREMENT_UNAVAILABLE）经
     * boundedRejection 存活进 outcome 与 attempt 审计诊断；状态折设施类 UNAVAILABLE。
     * 引擎折点本身（measurer 抛 → ENGINE_CLOSED + 标记，而非 INVALID_PROGRAM）由设备
     * 用例 QuickJsVisualExecutionEngineInstrumentedTest 钉住——QuickJs 原生库不在 JVM。
     */
    @Test
    fun aMeasurementFacilityFailureMarkerSurvivesIntoOutcomeAndAudit() = runBlocking {
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.ENGINE_CLOSED,
                diagnostics = "content measurement failed: IllegalStateException; " +
                    MEASUREMENT_UNAVAILABLE_DIAGNOSTIC_MARKER,
            ),
        )
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = RecordingLedger(),
            openBackend = { null },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        val outcome = coordinator.generate(request, jsProposal(request))

        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        val diagnostics = requireNotNull(outcome.diagnostics)
        assertTrue(diagnostics.startsWith("ENGINE_CLOSED: content measurement failed"))
        assertTrue(
            "the measurement-unavailable marker must survive into the outcome: $diagnostics",
            diagnostics.contains(MEASUREMENT_UNAVAILABLE_DIAGNOSTIC_MARKER),
        )
        assertTrue(
            requireNotNull(recorder.attemptFinishes.single().third)
                .contains(MEASUREMENT_UNAVAILABLE_DIAGNOSTIC_MARKER),
        )
    }

    /**
     * P-12② 预算承继（正向）：同一逻辑操作（同一 operationId）的显式重试承继同一操作身份
     * 与预算形状——JS 不预留（账本零触碰）、本地 attempt 负序号链延续（-1 → -2）、两次终态
     * 都是 0/0 远程派发；重试位透传给引擎（第一次 false 是现状钉死，第二次 true）。
     * 真库侧"同 id insertOperation IGNORE 压成一行"由设备真库用例钉（fake 如实记录每次 begin）。
     */
    @Test
    fun anExplicitRetryOfTheSameOperationInheritsTheLocalAttemptChain() = runBlocking {
        val ledger = RecordingLedger()
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { null },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        coordinator.generate(request, jsProposal(request, PROGRAM))
        val retry = request.copy(explicitUserRetry = true)
        val outcome = coordinator.generate(retry, jsProposal(retry, PROGRAM))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(listOf("op-1", "op-1"), recorder.operations.map { it.operationId })
        assertEquals(listOf(-1, -2), recorder.attempts.map { it.attemptOrdinal })
        assertTrue(recorder.attempts.all { it.reservationId == null })
        assertEquals(2, recorder.operationFinishes.size)
        assertTrue(
            recorder.operationFinishes.all {
                it.reservedRemoteDispatches == 0 && it.usedRemoteDispatches == 0
            },
        )
        assertEquals(listOf(false, true), engine.requests.map { it.explicitUserRetry })
    }

    /** P-12② 承继的反面：换 operationId 的"重试"是新逻辑操作——各开各的负序号链。 */
    @Test
    fun aRetryUnderADifferentOperationIdIsANewLogicalOperation() = runBlocking {
        val recorder = RecordingAuditRecorder()
        val engine = RecordingVisualEngine()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = RecordingLedger(),
            openBackend = { null },
            audit = recorder,
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val first = jsRequest()
        coordinator.generate(first, jsProposal(first, PROGRAM))
        val second = jsRequest().copy(operationId = "op-2", explicitUserRetry = true)
        val outcome = coordinator.generate(second, jsProposal(second, PROGRAM))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertEquals(listOf("op-1", "op-2"), recorder.operations.map { it.operationId })
        // 两条独立负序号链各从 -1 起（不是 -2）——承继不成立。
        assertEquals(listOf(-1, -1), recorder.attempts.map { it.attemptOrdinal })
    }

    /** P-12② 的远程半边：重试位不产生任何豁免——零余额 + flag=true 仍被 policy 预算门拒。 */
    @Test
    fun anExplicitRetryFlagDoesNotExemptTheRemoteBudgetGate() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel) {}

        val request = request(
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 0,
                reserveChatFinalAnswer = false,
            ),
        ).copy(explicitUserRetry = true)
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.REJECTED, outcome.status)
        assertTrue("flag=true 不得跳过远程预算门", ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
    }

    @Test
    fun anImageModelProposalIsUnaffectedByTheEngineInjection() = runBlocking {
        val ledger = RecordingLedger(attemptOrdinal = 2)
        val channel = RecordingChannel(ledger.events)
        val engine = RecordingVisualEngine()
        var backendOpens = 0
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = {
                backendOpens += 1
                FigureImageBackend(
                    configurationFingerprint = "config-1",
                    openChannel = { channel },
                    closeAction = {},
                )
            },
            visualEngine = engine,
            visualFrameInputs = frameInputs(),
        )

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertEquals(1, backendOpens)
        assertEquals(1, ledger.requests.size)
        assertEquals(1, channel.generateCalls)
        assertTrue("图像方法不得走本地引擎", engine.requests.isEmpty())
    }

    @Test
    fun distinctJavascriptProgramsGetDistinctBasisFingerprints() = runBlocking {
        val recorder = RecordingAuditRecorder()
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = RecordingLedger(),
            openBackend = { null },
            audit = recorder,
            visualEngine = RecordingVisualEngine(),
            visualFrameInputs = frameInputs(),
        )

        val request = jsRequest()
        coordinator.generate(request, jsProposal(request, "const visual = { a: 1 };"))
        coordinator.generate(request, jsProposal(request, "const visual = { b: 2 };"))

        val basis = recorder.operations.map { it.basisFingerprint }
        assertEquals(2, basis.size)
        assertNotEquals("两个不同 program 不得共享 input_fingerprint（A-08）", basis[0], basis[1])
        // 旧回退（imagePrompt 恒 null）正是会把两者压成同一指纹的那条路径。
        assertNotEquals(
            basis[0],
            figureBasisFingerprint("op-1", figureSha256Hex(SOURCE_BYTES), null),
        )
    }

    // -----------------------------------------------------------------

    private fun coordinator(
        ledger: RemoteModelDispatchLedger,
        channel: RecordingChannel,
        onOpen: () -> Unit,
    ) = DefaultFigureGenerationCoordinator(
        ledger = ledger,
        openBackend = {
            onOpen()
            FigureImageBackend(
                configurationFingerprint = "config-1",
                openChannel = { channel },
                closeAction = {},
            )
        },
    )

    private fun imageProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "基于已授权原图重建干净题面。",
            reason = "测试",
        )

    private fun request(
        source: FigureSourceEvidence? = FigureSourceEvidence(
            assetId = "asset-1",
            contentSha256 = figureSha256Hex(SOURCE_BYTES),
            mimeType = "image/png",
            imageBytes = SOURCE_BYTES,
        ),
        capabilities: FigureGenerationCapabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget: FigureGenerationBudget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "op-1",
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = capabilities,
        budget = budget,
        dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
        source = source,
    )

    private fun jsRequest(
        budget: FigureGenerationBudget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
    ): FigureGenerationRequest = request(
        capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.JS),
            supportsParametricInteraction = true,
        ),
        budget = budget,
    )

    private fun jsProposal(
        request: FigureGenerationRequest,
        program: String = "const visual = {}",
    ): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicy.POLICY_VERSION,
        decision = com.tingyun.smartmistakebook.core.model.FigureGenerationDecision.GENERATE,
        purpose = request.purpose,
        method = FigureGenerationMethod.JS,
        reason = "测试",
        program = program,
        fidelity = FigureFidelity.FAITHFUL,
    )

    private fun frameInputs(): VisualFrameInputs = VisualFrameInputs { _, _ -> FRAME }

    private class RecordingVisualEngine(
        private val result: VisualExecutionResult = VisualExecutionResult.Rendered(
            sceneJson = "{}",
            sceneSchemaVersion = 1,
            contentIds = emptySet(),
        ),
        private val failure: Exception? = null,
    ) : VisualExecutionEngine {
        val requests = mutableListOf<VisualExecutionRequest>()

        override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
            requests += request
            failure?.let { throw it }
            return result
        }

        override fun close() = Unit
    }

    private class RecordingAuditRecorder : FigureGenerationAuditRecorder {
        val operations = mutableListOf<FigureGenerationAuditOperation>()
        val attempts = mutableListOf<FigureGenerationAuditAttempt>()
        /** (attemptId, outcome, diagnostics)：诊断原文随行（批次 35 P-11/M-04 审计保留断言）。 */
        val attemptFinishes = mutableListOf<Triple<String, FigureGenerationAuditOutcome, String?>>()
        val operationFinishes = mutableListOf<OperationFinish>()
        private val localOrdinals = mutableMapOf<String, Int>()

        override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean {
            operations += operation
            return true
        }

        override suspend fun markOperationRunning(
            operationId: String,
            updatedAtEpochMillis: Long,
        ): Boolean = true

        override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean {
            attempts += attempt
            return true
        }

        override suspend fun beginLocalAttempt(
            operationId: String,
            method: FigureGenerationMethod,
            inputFingerprint: String,
            sourceSha256: String?,
            startedAtEpochMillis: Long,
        ): FigureGenerationAuditAttempt {
            val ordinal = (localOrdinals[operationId] ?: 0) - 1
            localOrdinals[operationId] = ordinal
            val row = FigureGenerationAuditAttempt(
                attemptId = "figure-attempt:$operationId:$ordinal:$method",
                operationId = operationId,
                attemptOrdinal = ordinal,
                method = method,
                reservationId = null,
                inputFingerprint = inputFingerprint,
                sourceSha256 = sourceSha256,
                startedAtEpochMillis = startedAtEpochMillis,
            )
            attempts += row
            return row
        }

        override suspend fun finishAttempt(
            attemptId: String,
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
            endedAtEpochMillis: Long,
        ): Boolean {
            // Triple：诊断随行（P-11/M-04 的"审计保留"断言要读 attempt 终态诊断原文）。
            attemptFinishes += Triple(attemptId, outcome, diagnostics)
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
            operationFinishes += OperationFinish(
                outcome = outcome,
                reservedRemoteDispatches = reservedRemoteDispatches,
                usedRemoteDispatches = usedRemoteDispatches,
                finalAnswerReserved = finalAnswerReserved,
            )
            return true
        }

        override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) = Unit
    }

    private data class OperationFinish(
        val outcome: FigureGenerationAuditOutcome,
        val reservedRemoteDispatches: Int,
        val usedRemoteDispatches: Int,
        val finalAnswerReserved: Boolean,
    )

    private class RecordingLedger(
        private val granted: Boolean = true,
        private val attemptOrdinal: Int = 1,
        val events: MutableList<String> = mutableListOf(),
    ) : RemoteModelDispatchLedger {
        val requests = mutableListOf<RemoteModelDispatchRequest>()

        override suspend fun reserve(
            request: RemoteModelDispatchRequest,
        ): RemoteModelDispatchDecision {
            requests += request
            events += "reserve"
            if (!granted) {
                return RemoteModelDispatchDecision.Denied(
                    code = RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED,
                    message = "逻辑操作的远程派发预算已用尽，未出网",
                )
            }
            return RemoteModelDispatchDecision.Granted(
                RemoteModelDispatchReservation(
                    reservationId = "reservation-${request.operationId}-$attemptOrdinal",
                    operationId = request.operationId,
                    parent = request.parent,
                    remoteKind = request.remoteKind,
                    method = request.method,
                    purpose = request.purpose,
                    policyVersion = request.policyVersion,
                    sourceImageSha256 = request.sourceImageSha256,
                    configurationFingerprint = request.configurationFingerprint,
                    attemptOrdinal = attemptOrdinal,
                    reservedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
        }
    }

    private class RecordingChannel(
        private val events: MutableList<String>,
        private val fail: Boolean = false,
    ) : ImageGenerationChannel {
        val resultBytes: ByteArray = byteArrayOf(1, 2, 3, 4)
        val requests = mutableListOf<ImageGenerationRequest>()
        val dispatches = mutableListOf<FigureImageDispatchBinding>()
        var generateCalls: Int = 0
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            events += "post"
            requests += request
            dispatches += dispatch
            if (fail) throw IllegalStateException("provider down")
            return ImageRedrawResult(
                imageBytes = resultBytes,
                mimeType = "image/png",
                modelVersion = "test-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used in this test")
    }

    private companion object {
        val SOURCE_BYTES = "source-photo".toByteArray()
        const val PROGRAM: String = "const visual = { draw: true };"
        val FRAME = VisualFrameSnapshot(
            viewport = VisualViewport(360, 240),
            theme = "light",
            seed = 42L,
            timeMs = 1_700_000_000_000L,
        )
    }
}
