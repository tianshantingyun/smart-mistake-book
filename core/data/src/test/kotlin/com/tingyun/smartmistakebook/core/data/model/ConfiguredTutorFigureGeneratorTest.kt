package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityVerification
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1（4B）：生产生图装配的决策逻辑——门 / 源图核对 / 通道 / 落盘 / id+状态。
 *
 * 消灭的失败：`GENERATE_FIGURE` 的工具结果**只允许**回 id + 状态，且没凭证时一个字节都不许
 * 出网、源图读不出时不许把错字节发出去。这些判定此前只能靠设备测试；依赖缝把文件 IO
 * 摘出去之后，JVM 用例就能逐条钉住。
 */
class ConfiguredTutorFigureGeneratorTest {

    private val sourceSheet = CanonicalSourceAssetRecord(
        sourceAssetId = "asset-sheet-1",
        contentSha256 = "b".repeat(64),
        relativePath = "source-assets/sheet-1.png",
        mimeType = "image/png",
        byteSize = 4,
        width = 10,
        height = 10,
        sourceType = "CAMERA",
        createdAtEpochMillis = 1,
    )

    @Test
    fun declinesWithoutEgressOrACredentialOrImageCapability() = runBlocking {
        // 三条门条件任一不满足都是 UNAVAILABLE，且一个字节都不发（通道工厂不被调用）。
        var channelFactoryCalls = 0
        val neverCalled = ChannelFactory { _, _ ->
            channelFactoryCalls += 1
            FakeChannel()
        }

        val cases = listOf(
            generator(
                networkRequestsAllowed = false,
                credentialAvailable = true,
                capability = configuredCapability(),
                channelFactory = neverCalled,
            ),
            generator(
                networkRequestsAllowed = true,
                credentialAvailable = false,
                capability = configuredCapability(),
                channelFactory = neverCalled,
            ),
            generator(
                networkRequestsAllowed = true,
                credentialAvailable = true,
                capability = null,
                channelFactory = neverCalled,
            ),
            generator(
                networkRequestsAllowed = true,
                credentialAvailable = true,
                capability = configuredCapability().copy(supportsImageInput = false),
                channelFactory = neverCalled,
            ),
        )

        cases.forEach { generator ->
            val result = generator.generate(
                TutorFigureRequest(
                    kind = TutorFigureKind.GENERATE_PROCESS,
                    description = "数轴标注导数符号区间",
                ),
            )
            assertEquals(TutorFigureStatus.UNAVAILABLE, result.status)
            assertNull(result.figureId)
        }
        assertEquals(0, channelFactoryCalls)
    }

    @Test
    fun generatesAndReturnsOnlyTheAssetIdAndStatus() = runBlocking {
        val prompts = mutableListOf<String>()
        var capturedBase: String? = null
        var capturedAuthorization: String? = null
        val generator = generator(
            channelFactory = ChannelFactory { base, authorization ->
                capturedBase = base
                capturedAuthorization = authorization
                FakeChannel(prompts = prompts)
            },
            persistGenerated = { _, _, _ -> sourceSheet.copy(sourceAssetId = "asset-figure-9") },
        )

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "数轴标注导数符号区间",
                problemContext = "单调性练习",
            ),
        )

        assertEquals(TutorFigureStatus.GENERATED, result.status)
        assertEquals("asset-figure-9", result.figureId)
        assertEquals("https://provider.test/v1", capturedBase)
        assertEquals("Bearer test-secret", capturedAuthorization)
        assertTrue("生成提示词必须带描述：${prompts.single()}", prompts.single().contains("数轴标注导数符号区间"))
        assertTrue("生成提示词必须带当前题上下文：${prompts.single()}", prompts.single().contains("单调性练习"))
    }

    @Test
    fun redrawReadsTheVerifiedSourceAndSeedsTheChannel() = runBlocking {
        val requests = mutableListOf<ImageGenerationRequest>()
        val generator = generator(
            readSourceBytes = { record ->
                assertEquals("asset-sheet-1", record.sourceAssetId)
                byteArrayOf(7, 7, 7)
            },
            channelFactory = ChannelFactory { _, _ -> FakeChannel(requests = requests) },
        )

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.REDRAW_PROBLEM,
                description = "重绘题面去手写",
                sourceSheet = sourceSheet,
            ),
        )

        assertEquals(TutorFigureStatus.GENERATED, result.status)
        val request = requests.single()
        assertArrayEquals(byteArrayOf(7, 7, 7), request.sourceImageBytes)
        assertEquals("image/png", request.sourceImageMimeType)
        assertTrue(request.prompt.contains("去除手写笔迹"))
    }

    @Test
    fun redrawFailsClosedWhenTheSourceBytesCannotBeRead() = runBlocking {
        // 记录在但文件被替换/损坏（缝返回 null）：不生成、不出网、不把错字节发出去。
        var channelCalls = 0
        val generator = generator(
            readSourceBytes = { null },
            channelFactory = ChannelFactory { _, _ ->
                channelCalls += 1
                FakeChannel()
            },
        )

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.REDRAW_PROBLEM,
                description = "重绘题面去手写",
                sourceSheet = sourceSheet,
            ),
        )

        assertEquals(TutorFigureStatus.FAILED, result.status)
        assertNull(result.figureId)
        assertEquals(0, channelCalls)
    }

    @Test
    fun channelAndPersistenceFailuresBecomeFailedStatus() = runBlocking {
        val channelFailure = generator(
            channelFactory = ChannelFactory { _, _ -> FakeChannel(failure = true) },
        ).generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画一张图"),
        )
        assertEquals(TutorFigureStatus.FAILED, channelFailure.status)
        assertNull(channelFailure.figureId)

        val persistFailure = generator(
            persistGenerated = { _, _, _ -> error("vault write failed") },
        ).generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画一张图"),
        )
        assertEquals(TutorFigureStatus.FAILED, persistFailure.status)
        assertNull(persistFailure.figureId)
    }

    // -----------------------------------------------------------------

    private fun generator(
        networkRequestsAllowed: Boolean = true,
        credentialAvailable: Boolean = true,
        capability: ModelCapabilityVerification? = configuredCapability(),
        readSourceBytes: (CanonicalSourceAssetRecord) -> ByteArray? = { byteArrayOf(1) },
        persistGenerated: (ByteArray, String, Long) -> CanonicalSourceAssetRecord =
            { _, _, _ -> sourceSheet },
        channelFactory: ChannelFactory = ChannelFactory { _, _ -> FakeChannel() },
    ) = ConfiguredTutorFigureGenerator(
        configurationStore = FakeStore(
            credentialAvailable = credentialAvailable,
            snapshot = configuration(capability),
        ),
        networkRequestsAllowed = networkRequestsAllowed,
        readSourceBytes = readSourceBytes,
        persistGenerated = persistGenerated,
        channelFactory = channelFactory,
    )

    private class FakeChannel(
        private val prompts: MutableList<String> = mutableListOf(),
        private val requests: MutableList<ImageGenerationRequest> = mutableListOf(),
        private val failure: Boolean = false,
    ) : ImageGenerationChannel {
        override suspend fun redrawClean(request: ImageRedrawRequest): ImageRedrawResult =
            throw UnsupportedOperationException("redrawClean is not used by the figure tool")

        override suspend fun generate(request: ImageGenerationRequest): ImageRedrawResult {
            requests += request
            prompts += request.prompt
            if (failure) throw ImageGenerationException("provider down")
            return ImageRedrawResult(imageBytes = byteArrayOf(1, 2, 3), mimeType = "image/png")
        }
    }

    private class ChannelFactory(
        private val factory: (String, String) -> ImageGenerationChannel,
    ) : ConfiguredCleanImageGenerator.ChannelFactory {
        override suspend fun create(baseUrl: String, authorization: String): ImageGenerationChannel =
            factory(baseUrl, authorization)
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
