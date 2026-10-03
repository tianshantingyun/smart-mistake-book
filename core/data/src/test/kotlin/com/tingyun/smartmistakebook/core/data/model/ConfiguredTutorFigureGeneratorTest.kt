package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1/A2（4B）：生产生图装配的决策逻辑——幂等 / 门 / 源图核对 / 通道 / 落盘 / id+状态。
 *
 * 消灭的失败：`GENERATE_FIGURE` 的工具结果**只允许**回 id + 状态，且没凭证时一个字节都不许
 * 出网、源图读不出时不许把错字节发出去、同一个请求第二次必须命中已生成资产（**一次生成
 * 一次付费**）。依赖缝把文件 IO 与数据库摘出去之后，JVM 用例就能逐条钉住。
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
        val persistedIds = mutableListOf<String>()
        val generator = generator(
            channelFactory = ChannelFactory { base, authorization ->
                capturedBase = base
                capturedAuthorization = authorization
                FakeChannel(prompts = prompts)
            },
            persistGenerated = { _, _, _, figureId, _ ->
                persistedIds += figureId
                sourceSheet.copy(sourceAssetId = figureId)
            },
        )

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "数轴标注导数符号区间",
                problemContext = "单调性练习",
            ),
        )

        assertEquals(TutorFigureStatus.GENERATED, result.status)
        // A2：资产 id 由幂等键派生（同一请求重放必须算出同一个 id）。
        assertEquals(persistedIds.single(), result.figureId)
        assertTrue(result.generatedNow)
        assertEquals(OpenAiImageGenerationChannel.DEFAULT_MODEL_ID, result.model)
        assertEquals("https://provider.test/v1", capturedBase)
        assertEquals("Bearer test-secret", capturedAuthorization)
        assertTrue("生成提示词必须带描述：${prompts.single()}", prompts.single().contains("数轴标注导数符号区间"))
        assertTrue("生成提示词必须带当前题上下文：${prompts.single()}", prompts.single().contains("单调性练习"))
    }

    /**
     * A2 的核心验收：**一次生成一次付费**——同一个请求跑两次，通道调用计数 = 1，
     * 第二次直接返回第一次登记的资产 id（generatedNow=false，界面不会再说"已计入额度"）。
     */
    @Test
    fun aRepeatedRequestHitsTheStoredFigureWithoutCallingTheChannelAgain() = runBlocking {
        var channelGenerateCalls = 0
        val stored = mutableMapOf<String, CanonicalSourceAssetRecord>()
        val generator = generator(
            channelFactory = ChannelFactory { _, _ ->
                FakeChannel(onGenerate = { channelGenerateCalls += 1 })
            },
            lookupGenerated = { figureId -> stored[figureId] },
            persistGenerated = { _, _, _, figureId, _ ->
                val record = sourceSheet.copy(sourceAssetId = figureId)
                stored[figureId] = record
                record
            },
        )
        val request = TutorFigureRequest(
            kind = TutorFigureKind.GENERATE_PROCESS,
            description = "数轴标注导数符号区间",
        )

        val first = generator.generate(request)
        val second = generator.generate(request)

        assertEquals(1, channelGenerateCalls)
        assertEquals(TutorFigureStatus.GENERATED, first.status)
        assertTrue(first.generatedNow)
        assertEquals(TutorFigureStatus.GENERATED, second.status)
        assertEquals(first.figureId, second.figureId)
        assertTrue("幂等命中不得声称本次计费", !second.generatedNow)
    }

    /** 命中已登记但文件损坏的图：不出网、不重付，如实失败（硬规则：一次生成一次付费）。 */
    @Test
    fun aBrokenStoredFigureFailsClosedWithoutPayingAgain() = runBlocking {
        var channelGenerateCalls = 0
        val generator = generator(
            channelFactory = ChannelFactory { _, _ ->
                FakeChannel(onGenerate = { channelGenerateCalls += 1 })
            },
            lookupGenerated = { sourceSheet.copy(sourceAssetId = it) },
            isIntact = { false },
        )

        val result = generator.generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画一张图"),
        )

        assertEquals(TutorFigureStatus.FAILED, result.status)
        assertNull(result.figureId)
        assertEquals(0, channelGenerateCalls)
    }

    /** 缓存命中连门都不用过：没凭证时已有生成图仍可复用（本地读回，不出网）。 */
    @Test
    fun aCachedFigureIsReturnedEvenWithoutACredential() = runBlocking {
        val generator = generator(
            credentialAvailable = false,
            lookupGenerated = { sourceSheet.copy(sourceAssetId = it) },
        )

        val result = generator.generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画一张图"),
        )

        assertEquals(TutorFigureStatus.GENERATED, result.status)
        assertTrue(result.figureId != null)
        assertTrue(!result.generatedNow)
    }

    /** A2：指纹必须把**每个生成参数**都算进去——缺一个就会在参数变了时命中旧图。 */
    @Test
    fun theFingerprintCoversEveryGenerationParameter() {
        val base = FingerprintArgs()
        assertEquals("同一组参数必须算出同一个键", base.id(), FingerprintArgs().id())
        val variants = mapOf(
            "kind" to base.copy(kind = TutorFigureKind.REDRAW_PROBLEM),
            "source sha" to base.copy(sourceImageSha256 = "a".repeat(64)),
            "prompt" to base.copy(prompt = "画另一张图"),
            "size" to base.copy(size = "1024x1024"),
            "quality" to base.copy(quality = "medium"),
            "format" to base.copy(format = "jpeg"),
            "model" to base.copy(modelId = "another-image-model"),
        )
        variants.forEach { (parameter, variant) ->
            assertNotEquals("参数变了键必须变：$parameter", base.id(), variant.id())
        }
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
            persistGenerated = { _, _, _, _, _ -> error("vault write failed") },
        ).generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画一张图"),
        )
        assertEquals(TutorFigureStatus.FAILED, persistFailure.status)
        assertNull(persistFailure.figureId)
    }

    /** A2：落盘时带上**隐式元数据**与幂等键派生的编号（用途=kind，提供者=模型）。 */
    @Test
    fun persistenceCarriesTheProvenanceAndTheKeyDerivedIdentity() = runBlocking {
        var capturedId: String? = null
        var capturedProvenance: GeneratedFigureProvenance? = null
        val generator = generator(
            persistGenerated = { _, _, _, figureId, provenance ->
                capturedId = figureId
                capturedProvenance = provenance
                sourceSheet.copy(sourceAssetId = figureId)
            },
        )

        generator.generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画一张图"),
        )

        val provenance = requireNotNull(capturedProvenance)
        assertEquals(capturedId, provenance.figureId)
        assertEquals("GENERATE_PROCESS", provenance.purpose)
        assertEquals(OpenAiImageGenerationChannel.DEFAULT_MODEL_ID, provenance.model)
        assertTrue(capturedId!!.startsWith("figure-"))
    }

    /**
     * 修复轮 F2-7：幂等键的入参必须与**通道实际收到的请求参数**逐项同源。
     *
     * 用假通道捕获真实请求，再用同一组共享映射（[imageGenerationSizeFor] /
     * [IMAGE_GENERATION_QUALITY]）从捕获值重算键——调用点若漏传参数退回默认值（或改成
     * 字面量），这里立刻变红。
     */
    @Test
    fun theKeyInputsMatchTheParametersTheChannelActuallyReceives() = runBlocking {
        val requests = mutableListOf<ImageGenerationRequest>()
        val persistedIds = mutableListOf<String>()
        val generator = generator(
            channelFactory = ChannelFactory { _, _ -> FakeChannel(requests = requests) },
            persistGenerated = { _, _, _, figureId, _ ->
                persistedIds += figureId
                sourceSheet.copy(sourceAssetId = figureId)
            },
        )

        generator.generate(
            TutorFigureRequest(kind = TutorFigureKind.GENERATE_PROCESS, description = "画数轴"),
        )
        generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.REDRAW_PROBLEM,
                description = "重绘题面",
                sourceSheet = sourceSheet,
            ),
        )

        assertEquals(2, requests.size)
        val expectedIds = requests.mapIndexed { index, request ->
            tutorFigureFingerprintId(
                kind = if (index == 0) TutorFigureKind.GENERATE_PROCESS else TutorFigureKind.REDRAW_PROBLEM,
                // REDRAW 的源图 sha 来自本地解析出的规范记录（与生成器一致）。
                sourceImageSha256 = if (index == 0) null else sourceSheet.contentSha256,
                prompt = request.prompt,
                size = imageGenerationSizeFor(request.maxDimension),
                quality = IMAGE_GENERATION_QUALITY,
                format = request.outputFormat,
            )
        }
        assertEquals(expectedIds, persistedIds)
        // 映射本身的当前取值（改通道参数时必须同步改键，反之亦然）。
        assertTrue(requests.all { imageGenerationSizeFor(it.maxDimension) == "1536x1024" })
        assertEquals("high", IMAGE_GENERATION_QUALITY)
    }

    // -----------------------------------------------------------------

    private fun generator(
        networkRequestsAllowed: Boolean = true,
        credentialAvailable: Boolean = true,
        capability: ModelCapabilityVerification? = configuredCapability(),
        readSourceBytes: (CanonicalSourceAssetRecord) -> ByteArray? = { byteArrayOf(1) },
        lookupGenerated: suspend (String) -> CanonicalSourceAssetRecord? = { null },
        isIntact: (CanonicalSourceAssetRecord) -> Boolean = { true },
        persistGenerated: suspend (
            ByteArray,
            String,
            Long,
            String,
            GeneratedFigureProvenance,
        ) -> CanonicalSourceAssetRecord = { _, _, _, figureId, _ ->
            sourceSheet.copy(sourceAssetId = figureId)
        },
        channelFactory: ChannelFactory = ChannelFactory { _, _ -> FakeChannel() },
    ) = ConfiguredTutorFigureGenerator(
        configurationStore = FakeStore(
            credentialAvailable = credentialAvailable,
            snapshot = configuration(capability),
        ),
        networkRequestsAllowed = networkRequestsAllowed,
        readSourceBytes = readSourceBytes,
        lookupGenerated = lookupGenerated,
        isIntact = isIntact,
        persistGenerated = persistGenerated,
        channelFactory = channelFactory,
    )

    /** 指纹参数的可读构造（函数没有 copy；这里给测试一份带默认值的形状）。 */
    private data class FingerprintArgs(
        val kind: TutorFigureKind = TutorFigureKind.GENERATE_PROCESS,
        val sourceImageSha256: String? = null,
        val prompt: String = "画数轴",
        val size: String = "1536x1024",
        val quality: String = "high",
        val format: String = "png",
        val modelId: String = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
    ) {
        fun id() = tutorFigureFingerprintId(
            kind = kind,
            sourceImageSha256 = sourceImageSha256,
            prompt = prompt,
            size = size,
            quality = quality,
            format = format,
            modelId = modelId,
        )
    }

    private class FakeChannel(
        private val prompts: MutableList<String> = mutableListOf(),
        private val requests: MutableList<ImageGenerationRequest> = mutableListOf(),
        private val failure: Boolean = false,
        private val onGenerate: () -> Unit = {},
    ) : ImageGenerationChannel {
        override suspend fun redrawClean(request: ImageRedrawRequest): ImageRedrawResult =
            throw UnsupportedOperationException("redrawClean is not used by the figure tool")

        override suspend fun generate(request: ImageGenerationRequest): ImageRedrawResult {
            onGenerate()
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
