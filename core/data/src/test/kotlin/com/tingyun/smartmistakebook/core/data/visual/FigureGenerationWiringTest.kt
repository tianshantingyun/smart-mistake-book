package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.model.ConfiguredTutorFigureGenerator
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.data.model.TutorFigureGenerator
import com.tingyun.smartmistakebook.core.data.model.TutorFigureRequest
import com.tingyun.smartmistakebook.core.data.model.TutorFigureStatus
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.FigureArtifactRef
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityVerification
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E.1 全入口接线验收（任务书 `FigureGenerationWiringTest` / WG-01）。
 *
 * 用 fake 后端实际跑**讲题**（GENERATE_FIGURE 的 process/redraw 两种 kind）与**录入净化**
 * 请求，观测：
 * - 两个入口使用同一个 policyVersion；
 * - 账本预留先于通道 POST（事件序列 `reserve → post`）；
 * - 通道拿到的 operation/attempt 与授予的预留逐项一致；
 * - 账本拒绝时图像 HTTP 调用 = 0。
 *
 * fake 成功不等于真实 Provider 质量通过（真实模型另记，未跑）。
 */
class FigureGenerationWiringTest {

    @Test
    fun tutorFiguresAndCleanRedrawShareOnePolicyAndReserveBeforePosting() = runBlocking {
        val tutorLedger = WiringLedger()
        val tutorChannel = RecordingChannel(tutorLedger.events)
        val tutorGenerator = tutorGenerator(tutorLedger, tutorChannel)

        val process = tutorGenerator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "画出函数图像并标注顶点",
                parentOperationId = "tutor-task-1",
            ),
        )
        val redraw = tutorGenerator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.REDRAW_PROBLEM,
                description = "重绘题面去手写",
                sourceSheet = sourceSheet(),
                parentOperationId = "tutor-task-1",
            ),
        )

        assertEquals(TutorFigureStatus.GENERATED, process.status)
        assertEquals(TutorFigureStatus.GENERATED, redraw.status)
        assertEquals(2, tutorLedger.requests.size)
        assertTrue(tutorLedger.requests.all { it.policyVersion == FigureGenerationPolicy.POLICY_VERSION })
        assertTrue(tutorLedger.requests.all { it.method == FigureGenerationMethod.IMAGE_MODEL })
        assertEquals(
            listOf(FigureGenerationPurpose.EXPLANATION, FigureGenerationPurpose.SOURCE_RECONSTRUCTION),
            tutorLedger.requests.map { it.purpose },
        )
        assertTrue(
            tutorLedger.requests.all {
                it.parent == FigureDispatchParent.ModelTask("tutor-task-1", reserveChatFinalAnswer = true)
            },
        )
        assertEquals(
            "预留必须发生在通道 POST 之前",
            listOf("reserve", "post", "reserve", "post"),
            tutorLedger.events,
        )

        assertEquals(2, tutorChannel.dispatches.size)
        val processDispatch = tutorChannel.dispatches[0]
        val redrawDispatch = tutorChannel.dispatches[1]
        assertEquals(2, processDispatch.attemptOrdinal)
        assertEquals(2, redrawDispatch.attemptOrdinal)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, processDispatch.method)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, redrawDispatch.method)
        assertNull(processDispatch.sourceImageSha256)
        assertEquals(figureSha256Hex(SOURCE_BYTES), redrawDispatch.sourceImageSha256)
        assertTrue(processDispatch.operationId.isNotBlank())
        assertTrue(redrawDispatch.operationId.isNotBlank())
        assertTrue("两种 kind 是不同逻辑操作", processDispatch.operationId != redrawDispatch.operationId)
        assertTrue(tutorChannel.requests[1].sourceImageBytes!!.contentEquals(SOURCE_BYTES))

        // 录入净化：同一 policyVersion、同一"先预留后 POST"顺序，父操作是持久作业尝试。
        val cleanLedger = WiringLedger()
        val cleanChannel = RecordingChannel(cleanLedger.events)
        val cleanGenerator = cleanGenerator(cleanLedger, cleanChannel)
        val clean = cleanGenerator.generateClean(
            CleanImageRequest(
                originalBytes = SOURCE_BYTES,
                mimeType = "image/png",
                parent = FigureDispatchParent.CleanRedrawTask(
                    taskId = "clean-task-1",
                    attemptCount = 2,
                ),
                operationId = "clean-op-1",
            ),
        )

        assertNotNull(clean)
        val cleanRef = clean as FigureArtifactRef.CanonicalImage
        assertTrue(cleanRef.imageBytes().contentEquals(cleanChannel.resultBytes))
        val cleanRequest = cleanLedger.requests.single()
        assertEquals(FigureGenerationPolicy.POLICY_VERSION, cleanRequest.policyVersion)
        assertEquals(FigureGenerationPurpose.PIXEL_CLEANUP, cleanRequest.purpose)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, cleanRequest.method)
        assertEquals(
            FigureDispatchParent.CleanRedrawTask("clean-task-1", 2),
            cleanRequest.parent,
        )
        assertEquals(listOf("reserve", "post"), cleanLedger.events)
        assertEquals(figureSha256Hex(SOURCE_BYTES), cleanChannel.dispatches.single().sourceImageSha256)
    }

    @Test
    fun aDeniedReservationResultsInZeroImageHttpCallsForBothEntries() = runBlocking {
        val ledger = WiringLedger(granted = false)
        val channel = RecordingChannel(ledger.events)
        val tutorGenerator = tutorGenerator(ledger, channel)

        val result = tutorGenerator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "画一张过程图",
                parentOperationId = "tutor-task-2",
            ),
        )

        assertEquals(TutorFigureStatus.FAILED, result.status)
        assertNull(result.figureId)
        assertEquals(0, channel.generateCalls)
        assertEquals(listOf("reserve"), ledger.events)

        val cleanLedger = WiringLedger(granted = false)
        val cleanChannel = RecordingChannel(cleanLedger.events)
        val cleanResult = cleanGenerator(cleanLedger, cleanChannel).generateClean(
            CleanImageRequest(
                originalBytes = SOURCE_BYTES,
                mimeType = "image/png",
                parent = FigureDispatchParent.CleanRedrawTask("clean-task-2", 1),
            ),
        )

        assertNull(cleanResult)
        assertEquals(0, cleanChannel.generateCalls)
        assertEquals(listOf("reserve"), cleanLedger.events)
    }

    // -----------------------------------------------------------------

    private fun tutorGenerator(
        ledger: WiringLedger,
        channel: RecordingChannel,
    ): TutorFigureGenerator = ConfiguredTutorFigureGenerator(
        configurationStore = FakeStore(),
        networkRequestsAllowed = true,
        readSourceBytes = { SOURCE_BYTES },
        lookupGenerated = { null },
        isIntact = { true },
        persistGenerated = { _, _, _, figureId, _ ->
            sourceSheet().copy(sourceAssetId = figureId)
        },
        channelFactory = ChannelFactory(channel),
        ledger = ledger,
    )

    private fun cleanGenerator(
        ledger: WiringLedger,
        channel: RecordingChannel,
    ): ConfiguredCleanImageGenerator = ConfiguredCleanImageGenerator(
        configurationStore = FakeStore(),
        channelFactory = ChannelFactory(channel),
        networkRequestsAllowed = true,
        ledger = ledger,
    )

    private fun sourceSheet(): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
        sourceAssetId = "asset-sheet-1",
        contentSha256 = figureSha256Hex(SOURCE_BYTES),
        relativePath = "source-assets/sheet-1.png",
        mimeType = "image/png",
        byteSize = SOURCE_BYTES.size.toLong(),
        width = 10,
        height = 10,
        sourceType = "CAMERA",
        createdAtEpochMillis = 1,
    )

    private class WiringLedger(
        private val granted: Boolean = true,
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
                    message = "预算已用尽，未出网",
                )
            }
            val attemptOrdinal = when (val parent = request.parent) {
                is FigureDispatchParent.ModelTask -> 2
                is FigureDispatchParent.CleanRedrawTask -> parent.attemptCount
            }
            return RemoteModelDispatchDecision.Granted(
                RemoteModelDispatchReservation(
                    reservationId = "reservation:${request.operationId}:$attemptOrdinal",
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
    ) : ImageGenerationChannel {
        val resultBytes: ByteArray = byteArrayOf(9, 8, 7, 6)
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
            return ImageRedrawResult(
                imageBytes = resultBytes,
                mimeType = "image/png",
                modelVersion = "fake-image-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used by these entries")
    }

    private class ChannelFactory(
        private val channel: ImageGenerationChannel,
    ) : ConfiguredCleanImageGenerator.ChannelFactory {
        override suspend fun create(
            baseUrl: String,
            authorization: String,
        ): ImageGenerationChannel = channel
    }

    private class FakeStore : ModelConfigurationStore {
        private val state = MutableStateFlow(
            ModelConfigurationSnapshot(
                provider = "provider",
                baseUrl = "https://provider.test/v1",
                modelId = "gpt-image-2",
                isConfigured = true,
                updatedAtEpochMillis = 1,
                configurationVersion = "test-config-v1",
                capabilityVerification = ModelCapabilityVerification(
                    provider = "provider",
                    baseUrl = "https://provider.test/v1",
                    modelId = "gpt-image-2",
                    configurationVersion = "test-config-v1",
                    configurationUpdatedAtEpochMillis = 1,
                    supportsImageInput = true,
                    supportsStructuredOutput = true,
                    testedAtEpochMillis = 2,
                ),
            ),
        )

        override val configuration: Flow<ModelConfigurationSnapshot> = state

        override suspend fun save(
            update: ModelConfigurationUpdate,
            apiKey: ModelApiKey,
        ): ModelConfigurationMutationResult = error("not used")

        override suspend fun rotateApiKey(apiKey: ModelApiKey): ModelConfigurationMutationResult =
            error("not used")

        override suspend fun readCredential(): ModelCredentialReadResult =
            ModelCredentialReadResult.Available(
                state.value,
                ModelApiKey.from("test-secret".toCharArray()),
            )

        override suspend fun clear(): ModelConfigurationMutationResult = error("not used")
    }

    private companion object {
        val SOURCE_BYTES = "source-photo-bytes".toByteArray()
    }
}
