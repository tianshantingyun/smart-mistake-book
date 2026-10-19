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
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E.1 协调器状态机的 JVM 测试（任务书 `FigureGenerationCoordinatorTest`）。
 *
 * 钉住四条硬序列：
 * 1. policy 拒绝 → 不打开后端、不预留、不调用通道；
 * 2. 账本拒绝 → 通道调用 = 0（"无预留 → 0 HTTP"）；
 * 3. 账本授予 → 通道只调用一次，且拿到与预留逐项一致的绑定；
 * 4. 通道失败 → 有界诊断，不抛出假成功。
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
    fun aJavascriptProposalIsNotExecutedInThisWave() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel) {}

        val request = request(
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(FigureGenerationMethod.JS),
                supportsParametricInteraction = true,
            ),
        )
        val proposal = FigureGenerationProposal(
            decision = com.tingyun.smartmistakebook.core.model.FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
            method = FigureGenerationMethod.JS,
            program = "const visual = {}",
            fidelity = FigureFidelity.FAITHFUL,
        )
        val outcome = coordinator.generate(request, proposal)

        assertEquals(FigureGenerationStatus.UNAVAILABLE, outcome.status)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
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
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "op-1",
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = capabilities,
        budget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
        dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
        source = source,
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
    }
}
