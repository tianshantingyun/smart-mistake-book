package com.tingyun.smartmistakebook.core.data.capture

import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityVerification
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gating + unified-coordinator contract for the production clean-redraw generator.
 * The network POST itself is covered by OpenAiImageGenerationChannel's
 * MockWebServer tests; here a fake channel factory substitutes the network.
 */
class ConfiguredCleanImageGeneratorTest {

    @Test
    fun declinesWhenNetworkIsNotAllowed() = runBlocking {
        val generator = generator(networkRequestsAllowed = false)

        assertNull(generate(generator))
    }

    @Test
    fun declinesWhenNoCredentialIsConfigured() = runBlocking {
        val generator = generator(credentialAvailable = false)

        assertNull(generate(generator))
    }

    @Test
    fun declinesWhenCapabilityTestNeverRan() = runBlocking {
        val generator = generator(capability = null)

        assertNull(generate(generator))
    }

    @Test
    fun declinesWhenCapabilityTestDidNotVerifyImageInput() = runBlocking {
        val generator = generator(
            capability = configuredCapability().copy(supportsImageInput = false),
        )

        assertNull(generate(generator))
    }

    @Test
    fun declinesWhenTheRedrawFailsAndDoesNotThrow() = runBlocking {
        var channelCalled = false
        val generator = generator(
            channelFactory = ChannelFactory { _, _ ->
                channelCalled = true
                throw IllegalStateException("provider down")
            },
        )

        assertNull(generate(generator))
        assertTrue(channelCalled)
    }

    @Test
    fun passesConfiguredBaseAndBearerAndReturnsTheCleanResult() = runBlocking {
        var capturedBase: String? = null
        var capturedAuthorization: String? = null
        val generator = generator(
            channelFactory = ChannelFactory { base, authorization ->
                capturedBase = base
                capturedAuthorization = authorization
                FakeRedrawChannel(cleanBytes = "cleaned-bytes", cleanMime = "image/png")
            },
        )

        val result = generate(generator)

        assertEquals("https://provider.test/v1", capturedBase)
        assertEquals("Bearer test-secret", capturedAuthorization)
        assertArrayEquals(bytes("cleaned-bytes"), result!!.bytes)
        assertEquals("image/png", result.mimeType)
    }

    /**
     * KD-60：净化入口声明共享上限 + 未预读余量，不再用常量 6 冒充实余量。
     */
    @Test
    fun declaresTheSharedOperationBudgetWithoutAFakeRemainingCount() = runBlocking {
        val coordinator = CapturingCoordinator()
        val generator = generator(coordinator = coordinator)

        generate(generator)

        val budget = coordinator.requests.single().budget
        assertNull("上限不是余量", budget.remainingRemoteDispatches)
        assertEquals(ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES, budget.operationDispatchLimit)
        assertTrue(budget.sharesOperationBudgetWithToolRounds)
    }

    // -----------------------------------------------------------------

    /** 4E.1：净化生成必须带持久派发父操作（这里是已领取的净化作业尝试）。 */
    private suspend fun generate(generator: ConfiguredCleanImageGenerator) =
        generator.generateClean(
            CleanImageRequest(
                originalBytes = bytes("a"),
                mimeType = "image/jpeg",
                parent = FigureDispatchParent.CleanRedrawTask(
                    taskId = "clean-redraw-task-1",
                    attemptCount = 1,
                ),
            ),
        )

    private fun generator(
        credentialAvailable: Boolean = true,
        capability: ModelCapabilityVerification? = configuredCapability(),
        networkRequestsAllowed: Boolean = true,
        channelFactory: ChannelFactory = ChannelFactory { _, _ ->
            FakeRedrawChannel("default", "image/png")
        },
        coordinator: FigureGenerationCoordinator? = null,
    ) = ConfiguredCleanImageGenerator(
        configurationStore = FakeStore(
            credentialAvailable = credentialAvailable,
            snapshot = configuration(capability = capability),
        ),
        channelFactory = channelFactory,
        networkRequestsAllowed = networkRequestsAllowed,
        coordinator = coordinator,
        ledger = GrantingLedger(),
    )

    /** 只捕获入口构造的统一请求；返回 FAILED 让测试不依赖后端。 */
    private class CapturingCoordinator : FigureGenerationCoordinator {
        val requests = mutableListOf<FigureGenerationRequest>()

        override suspend fun generate(
            request: FigureGenerationRequest,
            proposal: FigureGenerationProposal,
        ): FigureGenerationOutcome {
            requests += request
            return FigureGenerationOutcome(
                operationId = request.operationId,
                purpose = request.purpose,
                method = FigureGenerationMethod.IMAGE_MODEL,
                status = FigureGenerationStatus.FAILED,
                diagnostics = "capture only",
            )
        }
    }

    private class FakeRedrawChannel(
        private val cleanBytes: String,
        private val cleanMime: String,
    ) : ImageGenerationChannel {
        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding,
        ): ImageRedrawResult =
            ImageRedrawResult(
                imageBytes = cleanBytes.toByteArray(),
                mimeType = cleanMime,
            )

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding,
        ): ImageRedrawResult =
            ImageRedrawResult(
                imageBytes = cleanBytes.toByteArray(),
                mimeType = cleanMime,
            )
    }

    private class ChannelFactory(
        private val factory: (String, String) -> ImageGenerationChannel,
    ) : ConfiguredCleanImageGenerator.ChannelFactory {
        override suspend fun create(
            baseUrl: String,
            authorization: String,
        ): ImageGenerationChannel = factory(baseUrl, authorization)
    }

    private class GrantingLedger : RemoteModelDispatchLedger {
        override suspend fun reserve(
            request: RemoteModelDispatchRequest,
        ): RemoteModelDispatchDecision = RemoteModelDispatchDecision.Granted(
            RemoteModelDispatchReservation(
                reservationId = "test-reservation:${request.operationId}",
                operationId = request.operationId,
                parent = request.parent,
                remoteKind = request.remoteKind,
                method = request.method,
                purpose = request.purpose,
                policyVersion = request.policyVersion,
                sourceImageSha256 = request.sourceImageSha256,
                configurationFingerprint = request.configurationFingerprint,
                attemptOrdinal = request.parent.let { parent ->
                    (parent as? FigureDispatchParent.CleanRedrawTask)?.attemptCount ?: 1
                },
                reservedAtEpochMillis = request.occurredAtEpochMillis,
            ),
        )
    }

    private class FakeStore(
        private val credentialAvailable: Boolean,
        snapshot: ModelConfigurationSnapshot,
    ) : ModelConfigurationStore {
        private val state = MutableStateFlow(snapshot)
        override val configuration: Flow<ModelConfigurationSnapshot> = state
        override suspend fun save(
            update: ModelConfigurationUpdate,
            apiKey: ModelApiKey,
        ): ModelConfigurationMutationResult = error("not used")
        override suspend fun rotateApiKey(apiKey: ModelApiKey): ModelConfigurationMutationResult =
            error("not used")
        override suspend fun readCredential(): ModelCredentialReadResult =
            if (credentialAvailable) {
                ModelCredentialReadResult.Available(
                    state.value,
                    ModelApiKey.from("test-secret".toCharArray()),
                )
            } else {
                ModelCredentialReadResult.Missing
            }
        override suspend fun clear(): ModelConfigurationMutationResult = error("not used")
    }

    private fun bytes(value: String) = value.toByteArray()

    private companion object {
        const val BASE_URL = "https://provider.test/v1"
        const val MODEL_ID = "gpt-image-2"
        const val GENERATION = "test-config-v1"

        fun configuration(capability: ModelCapabilityVerification?) = ModelConfigurationSnapshot(
            provider = "provider",
            baseUrl = BASE_URL,
            modelId = MODEL_ID,
            isConfigured = true,
            updatedAtEpochMillis = 1,
            configurationVersion = GENERATION,
            capabilityVerification = capability,
        )

        fun configuredCapability() = ModelCapabilityVerification(
            provider = "provider",
            baseUrl = BASE_URL,
            modelId = MODEL_ID,
            configurationVersion = GENERATION,
            configurationUpdatedAtEpochMillis = 1,
            supportsImageInput = true,
            supportsStructuredOutput = true,
            testedAtEpochMillis = 2,
        )
    }
}
