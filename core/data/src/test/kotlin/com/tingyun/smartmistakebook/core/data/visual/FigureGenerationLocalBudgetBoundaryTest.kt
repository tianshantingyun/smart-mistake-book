package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationLocalJsBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E.2 A-10 逐阈值边界（计划任务 3 第一组；载体 = JVM 协调器 + fake 引擎/账本/通道）。
 *
 * 先红后绿的口径：在 [FigureGenerationLocalJsBudget] 常量与协调器四道门落树之前，这些向量
 * 编译不过或断言红（第 7 次尝试照样执行 / 源码无界 / 墙钟无界 / 第 3 份最终图照样产出）——
 * 红是预期，不是放宽理由。
 *
 * 失败的执行器都用 Rejected 引擎：FAILED 尝试照常计入布局/源码/墙钟（"含失败尝试"的行义），
 * 同时不占用"两份最终图"名额，使各阈值可以独立到达边界。
 *
 * 批次 38 补"跨 method 合计最终图"向量（清单 :198 覆盖缺口登记：批次 36 落地时两例终图门
 * 各单 method，无 JS+IMAGE 混合合计向量）——门本体（`FigureOperationLocalBudgets` 按
 * operationId 计数，与 method 无关）批次 36 已在树，本向量是覆盖闭合，非新门。
 */
class FigureGenerationLocalBudgetBoundaryTest {

    /** A-10 向量 1：第 7 次 JS 布局尝试拒——前 6 次真实进引擎，第 7 次未执行、不建 attempt 行。 */
    @Test
    fun aSeventhLocalJsLayoutAttemptIsRejectedBeforeReachingTheEngine() = runBlocking {
        val engine = StubVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "layout attempt failed",
            ),
        )
        val coordinator = jsCoordinator(engine, now = { 1_000L })

        repeat(6) { index ->
            val outcome = coordinator.generate(jsRequest(), jsProposal("const v = { n: $index };"))
            assertEquals("第 ${index + 1} 次尝试应真实执行后失败", FigureGenerationStatus.FAILED, outcome.status)
        }
        assertEquals(6, engine.executeCount)

        val seventh = coordinator.generate(jsRequest(), jsProposal("const v = { n: 7 };"))

        assertEquals("第 7 次布局尝试必须被拒", FigureGenerationStatus.REJECTED, seventh.status)
        assertTrue(
            "拒绝必须说明布局尝试上限：${seventh.diagnostics}",
            seventh.diagnostics.orEmpty().contains("布局尝试") &&
                seventh.diagnostics.orEmpty().contains("6"),
        )
        assertEquals("被拒的尝试不得进引擎", 6, engine.executeCount)
    }

    /**
     * A-10 向量 2：累计源码 128 KiB+1 字节拒、恰 128 KiB 放行——两个 64 KiB program 逐字节
     * 填满预算（两次都真实执行），再多 1 字节就在进引擎前被拒。
     */
    @Test
    fun cumulativeJsSourcePastThe128KiBLineIsRejectedAndTheExactLineIsAdmitted() = runBlocking {
        val engine = StubVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "program did not evaluate",
            ),
        )
        val coordinator = jsCoordinator(engine, now = { 1_000L })

        val halfBudgetProgram =
            "a".repeat(FigureGenerationLocalJsBudget.MAX_CUMULATIVE_SOURCE_UTF8_BYTES / 2)
        val first = coordinator.generate(jsRequest(), jsProposal(halfBudgetProgram))
        assertEquals("恰半个预算放行", FigureGenerationStatus.FAILED, first.status)
        val second = coordinator.generate(jsRequest(), jsProposal(halfBudgetProgram))
        assertEquals("累计恰达 128 KiB 的这次放行", FigureGenerationStatus.FAILED, second.status)
        assertEquals(2, engine.executeCount)

        val overByOne = coordinator.generate(jsRequest(), jsProposal("b"))

        assertEquals("128 KiB+1 字节必须被拒", FigureGenerationStatus.REJECTED, overByOne.status)
        assertTrue(
            "拒绝必须说明累计源码预算：${overByOne.diagnostics}",
            overByOne.diagnostics.orEmpty().contains("累计 JS 源码") &&
                overByOne.diagnostics.orEmpty()
                    .contains("${FigureGenerationLocalJsBudget.MAX_CUMULATIVE_SOURCE_UTF8_BYTES}"),
        )
        assertEquals("超预算的源码不得进引擎", 2, engine.executeCount)
    }

    /**
     * A-10 向量 3：累计 JS 墙钟恰达 10 s 后的下一次拒——注入 now 每次读数 +2500 ms，
     * execute 前后恰好两次读数（之间无其他 now 消费点），4 次执行累计恰 10 000 ms，
     * 第 5 次在进引擎前被拒。
     */
    @Test
    fun cumulativeJsWallClockIsRejectedOnceExactlyTenSecondsHaveAccumulated() = runBlocking {
        val engine = StubVisualEngine(
            result = VisualExecutionResult.Rejected(
                reason = VisualExecutionRejection.INVALID_PROGRAM,
                diagnostics = "slow program failed",
            ),
        )
        var ticks = 0L
        val coordinator = jsCoordinator(engine, now = { ticks += 2_500; ticks })

        repeat(4) { index ->
            val outcome = coordinator.generate(jsRequest(), jsProposal("const v = { t: $index };"))
            assertEquals(
                "第 ${index + 1} 次尝试（累计 <10 s）应真实执行",
                FigureGenerationStatus.FAILED,
                outcome.status,
            )
        }
        assertEquals(4, engine.executeCount)

        val fifth = coordinator.generate(jsRequest(), jsProposal("const v = { t: 5 };"))

        assertEquals("累计墙钟恰达 10 s 后必须拒下一次", FigureGenerationStatus.REJECTED, fifth.status)
        assertTrue(
            "拒绝必须说明墙钟预算：${fifth.diagnostics}",
            fifth.diagnostics.orEmpty().contains("累计 JS 墙钟") &&
                fifth.diagnostics.orEmpty()
                    .contains("${FigureGenerationLocalJsBudget.MAX_CUMULATIVE_WALL_CLOCK_MILLIS} ms"),
        )
        assertEquals("超墙钟预算的尝试不得进引擎", 4, engine.executeCount)
    }

    /**
     * A-10 向量 5（IMAGE 半边）：同一逻辑操作的第 3 份最终图拒——拒绝发生在打开后端、
     * 预留、通道调用之前；换一个 operationId 则不受牵连（per-operation 状态）。
     */
    @Test
    fun aThirdFinalFigureIsRejectedBeforeTheBackendOpensForTheSameOperation() = runBlocking {
        val ledger = CountingLedger()
        val channel = StubChannel()
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
            now = { 1_000L },
        )

        repeat(2) {
            val outcome = coordinator.generate(request(), imageProposal())
            assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        }
        assertEquals(2, ledger.reserveCount)
        assertEquals(2, channel.generateCalls)
        assertEquals(2, backendOpens)

        val third = coordinator.generate(request(), imageProposal())

        assertEquals("第 3 份最终图必须被拒", FigureGenerationStatus.REJECTED, third.status)
        assertTrue(
            "拒绝必须说明最终图上限：${third.diagnostics}",
            third.diagnostics.orEmpty().contains("最终图") &&
                third.diagnostics.orEmpty()
                    .contains("${FigureGenerationLocalJsBudget.MAX_FINAL_FIGURES_PER_OPERATION}"),
        )
        assertEquals("被拒的生成不得预留", 2, ledger.reserveCount)
        assertEquals("被拒的生成不得开后端", 2, backendOpens)
        assertEquals("被拒的生成不得调通道", 2, channel.generateCalls)

        val fresh = coordinator.generate(request(operationId = "op-2"), imageProposal())
        assertEquals("新逻辑操作不受旧操作预算态牵连", FigureGenerationStatus.GENERATED, fresh.status)
    }

    /** A-10 向量 5（JS 半边）：第 3 份最终图在进引擎前被拒（JS 产出最终图零付费，唯一的门在这里）。 */
    @Test
    fun aThirdFinalFigureIsRejectedBeforeTheEngineRunsForTheSameOperation() = runBlocking {
        val engine = StubVisualEngine()
        val coordinator = jsCoordinator(engine, now = { 1_000L })

        repeat(2) {
            val outcome = coordinator.generate(jsRequest(), jsProposal(PROGRAM))
            assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        }
        assertEquals(2, engine.executeCount)

        val third = coordinator.generate(jsRequest(), jsProposal(PROGRAM))

        assertEquals(FigureGenerationStatus.REJECTED, third.status)
        assertEquals("被拒的生成不得进引擎", 2, engine.executeCount)
    }

    /**
     * A-10 向量 5（批次 38 跨 method 合计向量，清单 :198"跨 method 合计最终图"覆盖缺口）：
     * 同一 operationId 先 JS 后 IMAGE 各产出一份最终图后，第 3 份（IMAGE）在打开后端、
     * 预留、通道调用之前被拒——最终图计数按**逻辑操作**累计（`FigureOperationLocalBudgets`
     * 的键是 operationId，与 method 无关），先 JS 后 IMAGE 的换法不重置计数。
     */
    @Test
    fun aThirdFinalFigureAcrossMethodsIsRejectedForTheSameOperation() = runBlocking {
        val ledger = CountingLedger()
        val channel = StubChannel()
        val engine = StubVisualEngine()
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
            now = { 1_000L },
            visualEngine = engine,
            visualFrameInputs = { _, _ -> FRAME },
        )

        val jsOutcome = coordinator.generate(jsRequest(), jsProposal(PROGRAM))
        assertEquals("JS 半边产出第 1 份最终图", FigureGenerationStatus.GENERATED, jsOutcome.status)
        val imageOutcome = coordinator.generate(request(), imageProposal())
        assertEquals("IMAGE 半边产出第 2 份最终图", FigureGenerationStatus.GENERATED, imageOutcome.status)
        assertEquals(1, engine.executeCount)
        assertEquals(1, channel.generateCalls)
        assertEquals(1, backendOpens)
        assertEquals("JS 不占远程派发；IMAGE 占 1 次", 1, ledger.reserveCount)

        val third = coordinator.generate(request(), imageProposal())

        assertEquals("跨 method 的第 3 份最终图必须被拒", FigureGenerationStatus.REJECTED, third.status)
        assertTrue(
            "拒绝必须说明最终图上限：${third.diagnostics}",
            third.diagnostics.orEmpty().contains("最终图") &&
                third.diagnostics.orEmpty()
                    .contains("${FigureGenerationLocalJsBudget.MAX_FINAL_FIGURES_PER_OPERATION}"),
        )
        assertEquals("被拒的生成不得开后端", 1, backendOpens)
        assertEquals("被拒的生成不得预留", 1, ledger.reserveCount)
        assertEquals("被拒的生成不得调通道", 1, channel.generateCalls)
        assertEquals("被拒的生成不得进引擎", 1, engine.executeCount)
    }

    // -----------------------------------------------------------------

    private fun jsCoordinator(
        engine: VisualExecutionEngine,
        now: () -> Long,
    ) = DefaultFigureGenerationCoordinator(
        ledger = CountingLedger(),
        openBackend = { null },
        now = now,
        visualEngine = engine,
        visualFrameInputs = { _, _ -> FRAME },
    )

    private fun imageProposal(): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        method = FigureGenerationMethod.IMAGE_MODEL,
        reason = "测试",
        imageOperation = FigureImageOperation.GENERATE,
        imagePrompt = "画一张数轴示意图。",
        fidelity = FigureFidelity.FAITHFUL,
    )

    private fun request(
        operationId: String = "op-1",
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = operationId,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
        dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
        source = null,
    )

    private fun jsRequest(
        operationId: String = "op-1",
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = operationId,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.JS),
            supportsParametricInteraction = true,
        ),
        budget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
        dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
        source = null,
    )

    private fun jsProposal(program: String): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        method = FigureGenerationMethod.JS,
        reason = "测试",
        program = program,
        fidelity = FigureFidelity.FAITHFUL,
    )

    private class StubVisualEngine(
        private val result: VisualExecutionResult = VisualExecutionResult.Rendered(
            sceneJson = "{}",
            sceneSchemaVersion = 1,
            contentIds = emptySet(),
        ),
    ) : VisualExecutionEngine {
        var executeCount: Int = 0
            private set

        override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
            executeCount += 1
            return result
        }

        override fun close() = Unit
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

    private companion object {
        const val PROGRAM: String = "const visual = { draw: true };"
        val FRAME = VisualFrameSnapshot(
            viewport = VisualViewport(360, 240),
            theme = "light",
            seed = 42L,
            timeMs = 1_700_000_000_000L,
        )
    }
}
