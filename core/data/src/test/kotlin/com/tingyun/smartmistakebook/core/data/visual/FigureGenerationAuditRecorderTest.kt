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
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 4E WG-08 T1b：协调器审计状态机的 JVM 测试。
 *
 * 钉住五条（任务书验收"状态机/审计计数/旧调用逐位不变"）：
 * 1. GENERATE 成功：operation PENDING → RUNNING，attempt 在**拿到 reservation 之后** RUNNING，
 *    终态先 attempt 后 operation；reserved/used = 1，finalAnswerReserved 跟随父操作；
 * 2. 预留被拒：operation PENDING → FAILED，不建 attempt，计数 0/0；
 * 3. 取消：即使协程已取消，两行仍以 CANCELLED 落终态（NonCancellable），异常原样抛出；
 * 4. 后端失败：attempt FAILED + operation FAILED，已派发计数 1（不是 0）；
 * 5. 没注入 recorder 时结果与改前逐位一致；recorder 自身失败不影响生成与预算。
 *
 * 审计计数只被**写**、从不出现在预算判定里：最后一条用例用一个"全抛异常"的 recorder
 * 证明生成仍成功、账本仍只预留一次。
 */
class FigureGenerationAuditRecorderTest {

    @Test
    fun aGeneratedImageLandsBothRowsInOrderWithAuditCounts() = runBlocking {
        val ledger = AuditLedger(attemptOrdinal = 2)
        val channel = AuditChannel()
        val recorder = RecordingAuditRecorder()
        val coordinator = coordinator(ledger, channel, audit = recorder)

        val request = request(
            sourceKind = FigureGenerationSourceKind.PROBLEM_REVISION,
            sourceId = "revision-1",
            basisFingerprint = BASIS,
        )
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertNotNull("成功产物必须带 attemptId 供入口回填 output_asset_id", outcome.attemptId)
        assertEquals(
            listOf(
                "operation:PENDING",
                "operation:RUNNING",
                "attempt:RUNNING",
                "attempt:SUCCEEDED",
                "operation:SUCCEEDED",
            ),
            recorder.events,
        )

        val operation = recorder.operations.single()
        assertEquals("op-1", operation.operationId)
        assertEquals(FigureGenerationSourceKind.PROBLEM_REVISION, operation.sourceKind)
        assertEquals("revision-1", operation.sourceId)
        assertEquals(BASIS, operation.basisFingerprint)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, operation.purpose)
        assertEquals(FigureGenerationPolicy.POLICY_VERSION, operation.policyVersion)

        val attempt = recorder.attempts.single()
        assertEquals("figure-attempt:op-1:2:IMAGE_MODEL", attempt.attemptId)
        assertEquals("op-1", attempt.operationId)
        assertEquals(2, attempt.attemptOrdinal)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, attempt.method)
        assertEquals("reservation-op-1-2", attempt.reservationId)
        assertEquals(figureSha256Hex(SOURCE_BYTES), attempt.sourceSha256)
        assertEquals(BASIS, attempt.inputFingerprint)

        val attemptFinish = recorder.attemptFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.SUCCEEDED, attemptFinish.outcome)
        assertNull("成功 attempt 的产物 id 由入口落盘后回填", attemptFinish.outputAssetId)

        val operationFinish = recorder.operationFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.SUCCEEDED, operationFinish.outcome)
        assertEquals(1, operationFinish.reservedRemoteDispatches)
        assertEquals(1, operationFinish.usedRemoteDispatches)
        assertTrue("父操作保留终答位时必须如实镜像", operationFinish.finalAnswerReserved)
        assertEquals(attempt.attemptId, outcome.attemptId)
    }

    @Test
    fun aDeniedReservationFinishesTheOperationWithoutAnAttempt() = runBlocking {
        val ledger = AuditLedger(granted = false)
        val channel = AuditChannel()
        val recorder = RecordingAuditRecorder()
        val coordinator = coordinator(ledger, channel, audit = recorder)

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.FAILED, outcome.status)
        assertNull(outcome.attemptId)
        assertTrue("没有预留就不该有 attempt", recorder.attempts.isEmpty())
        assertEquals(listOf("operation:PENDING", "operation:FAILED"), recorder.events)
        val finish = recorder.operationFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.FAILED, finish.outcome)
        assertEquals(0, finish.reservedRemoteDispatches)
        assertEquals(0, finish.usedRemoteDispatches)
        assertEquals(0, channel.generateCalls)
    }

    @Test
    fun cancellationLandsCancelledOnBothRowsBeforeRethrowing() = runBlocking {
        val ledger = AuditLedger()
        val channel = AuditChannel(cancelled = true)
        val recorder = RecordingAuditRecorder()
        val coordinator = coordinator(ledger, channel, audit = recorder)

        val request = request()
        try {
            coordinator.generate(request, imageProposal(request))
            fail("取消必须原样抛出")
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(
            listOf(
                "operation:PENDING",
                "operation:RUNNING",
                "attempt:RUNNING",
                "attempt:CANCELLED",
                "operation:CANCELLED",
            ),
            recorder.events,
        )
        assertEquals(
            FigureGenerationAuditOutcome.CANCELLED,
            recorder.attemptFinishes.single().outcome,
        )
        val operationFinish = recorder.operationFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.CANCELLED, operationFinish.outcome)
        assertEquals(1, operationFinish.reservedRemoteDispatches)
        assertEquals("已进入通道调用，按已派发记账", 1, operationFinish.usedRemoteDispatches)
    }

    @Test
    fun aChannelFailureLandsFailedRowsWithOneUsedDispatch() = runBlocking {
        val ledger = AuditLedger()
        val channel = AuditChannel(failure = IllegalStateException("provider down"))
        val recorder = RecordingAuditRecorder()
        val coordinator = coordinator(ledger, channel, audit = recorder)

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals(FigureGenerationStatus.FAILED, outcome.status)
        assertNotNull("已开始的 attempt 即使失败也要回 id", outcome.attemptId)
        assertEquals(
            listOf(
                "operation:PENDING",
                "operation:RUNNING",
                "attempt:RUNNING",
                "attempt:FAILED",
                "operation:FAILED",
            ),
            recorder.events,
        )
        val attemptFinish = recorder.attemptFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.FAILED, attemptFinish.outcome)
        assertTrue(attemptFinish.diagnostics?.contains("provider down") == true)
        val operationFinish = recorder.operationFinishes.single()
        assertEquals(FigureGenerationAuditOutcome.FAILED, operationFinish.outcome)
        assertEquals(1, operationFinish.usedRemoteDispatches)
    }

    @Test
    fun withoutARecorderTheLegacyOutcomeIsBitForBitUnchanged() = runBlocking {
        val ledger = AuditLedger()
        val channel = AuditChannel(events = ledger.events)
        val coordinator = DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { backend(channel) },
            now = { NOW },
        )

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        val expectedArtifact = FigureArtifact.ImageAsset(
            method = FigureGenerationMethod.IMAGE_MODEL,
            purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
            mimeType = "image/png",
            contentSha256 = figureSha256Hex(channel.resultBytes),
            modelId = "test-model",
            assetId = null,
            imageBytes = channel.resultBytes,
        )
        assertEquals(
            FigureGenerationOutcome(
                operationId = "op-1",
                purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
                method = FigureGenerationMethod.IMAGE_MODEL,
                status = FigureGenerationStatus.GENERATED,
                artifact = expectedArtifact,
                diagnostics = null,
            ),
            outcome,
        )
        assertNull(outcome.attemptId)
        assertEquals(listOf("reserve", "post"), ledger.events)
    }

    @Test
    fun anAuditRecorderFailureNeverBlocksGenerationOrTheBudget() = runBlocking {
        val ledger = AuditLedger()
        val channel = AuditChannel(events = ledger.events)
        val coordinator = coordinator(ledger, channel, audit = FailingAuditRecorder())

        val request = request()
        val outcome = coordinator.generate(request, imageProposal(request))

        assertEquals("审计写不进不是生成失败", FigureGenerationStatus.GENERATED, outcome.status)
        assertNull("没写进行就没有可回填的 attempt", outcome.attemptId)
        assertEquals(1, channel.generateCalls)
        assertEquals("账本仍只预留一次（审计计数不参与预算）", 1, ledger.requests.size)
        assertEquals(listOf("reserve", "post"), ledger.events)
    }

    @Test
    fun aLegacyRequestDerivesItsSourceFromTheDispatchParent() = runBlocking {
        val recorder = RecordingAuditRecorder()
        val modelTaskLedger = AuditLedger()
        val modelTaskCoordinator = coordinator(
            modelTaskLedger,
            AuditChannel(),
            audit = recorder,
        )
        val legacy = request(sourceKind = null, sourceId = null, basisFingerprint = null)
        modelTaskCoordinator.generate(legacy, imageProposal(legacy))

        val modelTaskOperation = recorder.operations.single()
        assertEquals(FigureGenerationSourceKind.TUTOR_MESSAGE, modelTaskOperation.sourceKind)
        assertEquals("task-1", modelTaskOperation.sourceId)
        assertEquals(64, modelTaskOperation.basisFingerprint.length)

        val cleanRecorder = RecordingAuditRecorder()
        val cleanLedger = AuditLedger()
        val cleanCoordinator = coordinator(cleanLedger, AuditChannel(), audit = cleanRecorder)
        val cleanRequest = request(
            purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
            parent = FigureDispatchParent.CleanRedrawTask("clean-task-1", 2),
            sourceKind = null,
            sourceId = null,
            basisFingerprint = null,
        )
        cleanCoordinator.generate(cleanRequest, imageProposal(cleanRequest))

        val cleanOperation = cleanRecorder.operations.single()
        assertEquals(FigureGenerationSourceKind.CLEAN_REDRAW, cleanOperation.sourceKind)
        assertEquals("clean-task-1", cleanOperation.sourceId)
    }

    // -----------------------------------------------------------------

    private fun coordinator(
        ledger: RemoteModelDispatchLedger,
        channel: AuditChannel,
        audit: FigureGenerationAuditRecorder,
    ) = DefaultFigureGenerationCoordinator(
        ledger = ledger,
        openBackend = { backend(channel) },
        now = { NOW },
        audit = audit,
    )

    private fun backend(channel: ImageGenerationChannel): FigureImageBackend =
        FigureImageBackend(
            configurationFingerprint = "config-1",
            openChannel = { channel },
            closeAction = {},
        )

    private fun imageProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "基于已授权原图重建干净题面。",
            reason = "测试",
        )

    private fun request(
        purpose: FigureGenerationPurpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        parent: FigureDispatchParent = FigureDispatchParent.ModelTask("task-1", true),
        sourceKind: FigureGenerationSourceKind? = FigureGenerationSourceKind.PROBLEM_REVISION,
        sourceId: String? = "revision-1",
        basisFingerprint: String? = BASIS,
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "op-1",
        purpose = purpose,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = parent is FigureDispatchParent.ModelTask &&
                parent.reserveChatFinalAnswer,
        ),
        dispatchParent = parent,
        source = FigureSourceEvidence(
            assetId = "asset-1",
            contentSha256 = figureSha256Hex(SOURCE_BYTES),
            mimeType = "image/png",
            imageBytes = SOURCE_BYTES,
        ),
        sourceKind = sourceKind,
        sourceId = sourceId,
        basisFingerprint = basisFingerprint,
    )

    private class AuditLedger(
        private val granted: Boolean = true,
        private val attemptOrdinal: Int = 1,
    ) : RemoteModelDispatchLedger {
        val requests = mutableListOf<RemoteModelDispatchRequest>()
        val events = mutableListOf<String>()

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

    private class AuditChannel(
        private val cancelled: Boolean = false,
        private val failure: Exception? = null,
        /** 与账本共用事件表，记录"预留之后真的走到通道"这一次派发。 */
        private val events: MutableList<String>? = null,
    ) : ImageGenerationChannel {
        val resultBytes: ByteArray = byteArrayOf(5, 6, 7, 8)
        var generateCalls: Int = 0
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            events?.add("post")
            if (cancelled) throw CancellationException("cancelled")
            failure?.let { throw it }
            return ImageRedrawResult(
                imageBytes = resultBytes,
                mimeType = "image/png",
                modelVersion = "test-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used")
    }

    private class RecordingAuditRecorder : FigureGenerationAuditRecorder {
        val events = mutableListOf<String>()
        val operations = mutableListOf<FigureGenerationAuditOperation>()
        val attempts = mutableListOf<FigureGenerationAuditAttempt>()
        val attemptFinishes = mutableListOf<AttemptFinish>()
        val operationFinishes = mutableListOf<OperationFinish>()
        val outputAssets = mutableListOf<Pair<String, String>>()

        override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean {
            operations += operation
            events += "operation:PENDING"
            return true
        }

        override suspend fun markOperationRunning(
            operationId: String,
            updatedAtEpochMillis: Long,
        ): Boolean {
            events += "operation:RUNNING"
            return true
        }

        override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean {
            attempts += attempt
            events += "attempt:RUNNING"
            return true
        }

        override suspend fun beginLocalAttempt(
            operationId: String,
            method: FigureGenerationMethod,
            inputFingerprint: String,
            sourceSha256: String?,
            startedAtEpochMillis: Long,
        ): FigureGenerationAuditAttempt {
            val row = FigureGenerationAuditAttempt(
                attemptId = "figure-attempt:$operationId:-1:$method",
                operationId = operationId,
                attemptOrdinal = -1,
                method = method,
                reservationId = null,
                inputFingerprint = inputFingerprint,
                sourceSha256 = sourceSha256,
                startedAtEpochMillis = startedAtEpochMillis,
            )
            attempts += row
            events += "attempt:RUNNING"
            return row
        }

        override suspend fun finishAttempt(
            attemptId: String,
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
            endedAtEpochMillis: Long,
        ): Boolean {
            attemptFinishes += AttemptFinish(attemptId, outcome, outputAssetId, diagnostics)
            events += "attempt:${outcome.name}"
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
                operationId = operationId,
                outcome = outcome,
                reservedRemoteDispatches = reservedRemoteDispatches,
                usedRemoteDispatches = usedRemoteDispatches,
                finalAnswerReserved = finalAnswerReserved,
            )
            events += "operation:${outcome.name}"
            return true
        }

        override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) {
            outputAssets += attemptId to outputAssetId
        }
    }

    private class FailingAuditRecorder : FigureGenerationAuditRecorder {
        override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean =
            error("audit unavailable")

        override suspend fun markOperationRunning(
            operationId: String,
            updatedAtEpochMillis: Long,
        ): Boolean = error("audit unavailable")

        override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean =
            error("audit unavailable")

        override suspend fun beginLocalAttempt(
            operationId: String,
            method: FigureGenerationMethod,
            inputFingerprint: String,
            sourceSha256: String?,
            startedAtEpochMillis: Long,
        ): FigureGenerationAuditAttempt? = error("audit unavailable")

        override suspend fun finishAttempt(
            attemptId: String,
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
            endedAtEpochMillis: Long,
        ): Boolean = error("audit unavailable")

        override suspend fun finishOperation(
            operationId: String,
            outcome: FigureGenerationAuditOutcome,
            reservedRemoteDispatches: Int,
            usedRemoteDispatches: Int,
            finalAnswerReserved: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean = error("audit unavailable")

        override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) =
            error("audit unavailable")
    }

    private data class AttemptFinish(
        val attemptId: String,
        val outcome: FigureGenerationAuditOutcome,
        val outputAssetId: String?,
        val diagnostics: String?,
    )

    private data class OperationFinish(
        val operationId: String,
        val outcome: FigureGenerationAuditOutcome,
        val reservedRemoteDispatches: Int,
        val usedRemoteDispatches: Int,
        val finalAnswerReserved: Boolean,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
        val BASIS = "b".repeat(64)
        val SOURCE_BYTES = "source-photo".toByteArray()
    }
}
