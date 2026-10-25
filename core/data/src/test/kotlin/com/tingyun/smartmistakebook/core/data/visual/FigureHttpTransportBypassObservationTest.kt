package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.model.AttachedImageGenerator
import com.tingyun.smartmistakebook.core.data.model.ConfiguredTutorFigureGenerator
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.OpenAiImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.TutorFigureRequest
import com.tingyun.smartmistakebook.core.data.model.TutorFigureStatus
import com.tingyun.smartmistakebook.core.data.model.imageGenerationConfigurationFingerprint
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
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
import com.tingyun.smartmistakebook.core.domain.UnavailableRemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageUnavailableReason
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * WG-10 收口（批次 20 T1）：**HTTP transport 层**的旁路观测。
 *
 * 既有证据各差一层：`FigureGenerationWiringTest` / `AttachedImageRegenerationWiringTest` 观测的是
 * fake 通道（接口缝），`OpenAiImageGenerationChannelTest` 直驱真实通道但没有业务入口，
 * `ImageDispatchReservationGuardTest` 是文本对拍。本测试把**三条业务入口**（讲题工具 / 历史配图
 * 显式重生成 / 保存净化）接到**真实 `OpenAiImageGenerationChannel` + MockWebServer 假后端**上，
 * 在 `server.requestCount`（假后端的计数器）断言：
 *
 * - 账本拒绝 → 到达假后端的图像 HTTP 请求 = **0**（未经 coordinator 授予的图像调用不存在）；
 * - 账本授予 → 恰好 **1** 个请求，且路径/授权头与预留一致。
 *
 * 授予臂同时是拒绝臂的**非空转对照**：计数器在允许出网时确实会计到 1（否则"=0"可能是观测本身
 * 失灵的假绿）。任何入口重新出现直调通道、或 coordinator 退化为"先 POST 后预留"，都会让对应
 * 断言翻红。fake 后端成功不等于真实 Provider 质量通过（无 key，照旧 UNVERIFIED）。
 */
class FigureHttpTransportBypassObservationTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private lateinit var store: ModelConfigurationStore

    private val sourceBytes: ByteArray = "transport-observation-source".toByteArray()
    private val pngBytes: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)

    private val processImage = AttachedImage(
        imageId = "process-1",
        kind = AttachedImageKind.GENERATE_PROCESS,
        description = "画出函数图像并标注顶点",
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/v1").toString().removeSuffix("/")
        store = FakeStore(baseUrl)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -----------------------------------------------------------------
    // 入口一：保存净化（ConfiguredCleanImageGenerator，EDIT → /images/edits）
    // -----------------------------------------------------------------

    @Test
    fun `clean redraw entry sends zero http requests when the ledger denies`() = runBlocking {
        val ledger = WiringLedger(granted = false)
        val generator = cleanGenerator(ledger)

        val result = generator.generateClean(
            CleanImageRequest(
                originalBytes = sourceBytes,
                mimeType = "image/png",
                parent = FigureDispatchParent.CleanRedrawTask(taskId = "clean-task-1", attemptCount = 1),
                operationId = "clean-op-transport-1",
            ),
        )

        assertNull("账本拒绝必须按不可用处理", result)
        assertEquals(
            "未经 coordinator 授予的图像调用 = 0（真实 HTTP 层观测）",
            0,
            server.requestCount,
        )
    }

    @Test
    fun `clean redraw entry posts exactly one edit request when the reservation is granted`() = runBlocking {
        server.enqueue(pngResponse())
        val ledger = WiringLedger(granted = true)
        val generator = cleanGenerator(ledger)

        val result = generator.generateClean(
            CleanImageRequest(
                originalBytes = sourceBytes,
                mimeType = "image/png",
                parent = FigureDispatchParent.CleanRedrawTask(taskId = "clean-task-2", attemptCount = 1),
                operationId = "clean-op-transport-2",
            ),
        )

        assertNotNull("授予后必须得到可持久化产物（对照臂：观测器确实看得见 HTTP）", result)
        assertEquals(1, server.requestCount)
        val recorded = server.takeRequest()
        assertEquals("/v1/images/edits", recorded.path)
        assertEquals("Bearer test-secret", recorded.getHeader("Authorization"))
    }

    // -----------------------------------------------------------------
    // 入口二：讲题工具 GENERATE_FIGURE（ConfiguredTutorFigureGenerator，
    // GENERATE_PROCESS → /images/generations）
    // -----------------------------------------------------------------

    @Test
    fun `tutor figure entry sends zero http requests when the ledger denies`() = runBlocking {
        val ledger = WiringLedger(granted = false)
        val generator = tutorGenerator(ledger)

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "画一张过程图",
                parentOperationId = "tutor-task-transport-1",
            ),
        )

        assertEquals(TutorFigureStatus.FAILED, result.status)
        assertEquals(
            "未经 coordinator 授予的图像调用 = 0（真实 HTTP 层观测）",
            0,
            server.requestCount,
        )
    }

    @Test
    fun `tutor figure entry posts exactly one generations request when the reservation is granted`() =
        runBlocking {
            server.enqueue(pngResponse())
            val ledger = WiringLedger(granted = true)
            val generator = tutorGenerator(ledger)

            val result = generator.generate(
                TutorFigureRequest(
                    kind = TutorFigureKind.GENERATE_PROCESS,
                    description = "画一张过程图",
                    parentOperationId = "tutor-task-transport-2",
                ),
            )

            assertEquals(TutorFigureStatus.GENERATED, result.status)
            assertEquals(1, server.requestCount)
            assertEquals("/v1/images/generations", server.takeRequest().path)
        }

    // -----------------------------------------------------------------
    // 入口三：历史配图显式重生成（AttachedImageGenerator，注入生产协调器）
    // -----------------------------------------------------------------

    @Test
    fun `attached-image regeneration sends zero http requests when the ledger denies`() = runBlocking {
        val ledger = WiringLedger(granted = false)
        val generator = attachedGenerator(productionShapedCoordinator(ledger))

        val resolution = generator.regenerate(processImage, parentRequestId = "task-transport-1")

        assertTrue(resolution is AttachedImageResolution.Unavailable)
        assertEquals(
            AttachedImageUnavailableReason.REGENERATION_FAILED,
            (resolution as AttachedImageResolution.Unavailable).reason,
        )
        assertEquals(
            "未经 coordinator 授予的图像调用 = 0（真实 HTTP 层观测）",
            0,
            server.requestCount,
        )
    }

    @Test
    fun `attached-image regeneration posts exactly one generations request when granted`() = runBlocking {
        server.enqueue(pngResponse())
        val ledger = WiringLedger(granted = true)
        val generator = attachedGenerator(productionShapedCoordinator(ledger))

        val resolution = generator.regenerate(processImage, parentRequestId = "task-transport-2")

        assertTrue("授予后必须回本地 URI（对照臂）：$resolution", resolution is AttachedImageResolution.Ready)
        assertEquals(1, server.requestCount)
        assertEquals("/v1/images/generations", server.takeRequest().path)
    }

    // -----------------------------------------------------------------
    // 装配兜底：忘了注入共享 coordinator 的入口（内部协调器 + fail-closed 账本）
    // -----------------------------------------------------------------

    @Test
    fun `entries without an injected coordinator stay fail-closed with zero http requests`() = runBlocking {
        val clean = ConfiguredCleanImageGenerator(
            configurationStore = store,
            channelFactory = MockServerChannelFactory(),
            networkRequestsAllowed = true,
        )
        assertNull(
            clean.generateClean(
                CleanImageRequest(
                    originalBytes = sourceBytes,
                    mimeType = "image/png",
                    parent = FigureDispatchParent.CleanRedrawTask(
                        taskId = "clean-task-fallback",
                        attemptCount = 1,
                    ),
                ),
            ),
        )

        val tutor = ConfiguredTutorFigureGenerator(
            configurationStore = store,
            networkRequestsAllowed = true,
            readSourceBytes = { sourceBytes },
            lookupGenerated = { null },
            isIntact = { true },
            persistGenerated = { _, _, _, figureId, _ -> asset(sourceAssetId = figureId) },
            channelFactory = MockServerChannelFactory(),
            coordinator = null,
            ledger = UnavailableRemoteModelDispatchLedger,
        )
        val result = tutor.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "画一张过程图",
                parentOperationId = "tutor-task-fallback",
            ),
        )

        assertEquals(TutorFigureStatus.FAILED, result.status)
        assertTrue(
            "拒绝理由必须指向缺失的持久账本：${result.diagnostics}",
            result.diagnostics!!.contains("账本"),
        )
        assertEquals(
            "未注入 coordinator 的装配也不得产生任何图像 HTTP",
            0,
            server.requestCount,
        )
    }

    // -----------------------------------------------------------------

    /** 真实通道工厂：与生产 `GuardedEditsChannelFactory` 同形，端点指向 MockWebServer。 */
    private inner class MockServerChannelFactory : ConfiguredCleanImageGenerator.ChannelFactory {
        override suspend fun create(
            baseUrl: String,
            authorization: String,
        ): ImageGenerationChannel = create(
            baseUrl = baseUrl,
            authorization = authorization,
            configurationFingerprint = imageGenerationConfigurationFingerprint(
                baseUrl,
                OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
            ),
        )

        override suspend fun create(
            baseUrl: String,
            authorization: String,
            configurationFingerprint: String,
        ): ImageGenerationChannel = OpenAiImageGenerationChannel(
            baseUrl = this@FigureHttpTransportBypassObservationTest.baseUrl,
            client = OkHttpClient(),
            authorization = authorization,
            configurationFingerprint = configurationFingerprint,
        )
    }

    private fun cleanGenerator(ledger: RemoteModelDispatchLedger): ConfiguredCleanImageGenerator =
        ConfiguredCleanImageGenerator(
            configurationStore = store,
            channelFactory = MockServerChannelFactory(),
            networkRequestsAllowed = true,
            coordinator = null,
            ledger = ledger,
        )

    private fun tutorGenerator(ledger: RemoteModelDispatchLedger): ConfiguredTutorFigureGenerator =
        ConfiguredTutorFigureGenerator(
            configurationStore = store,
            networkRequestsAllowed = true,
            readSourceBytes = { sourceBytes },
            lookupGenerated = { null },
            isIntact = { true },
            persistGenerated = { _, _, _, figureId, _ -> asset(sourceAssetId = figureId) },
            channelFactory = MockServerChannelFactory(),
            coordinator = null,
            ledger = ledger,
        )

    /** 入口三显式注入生产协调器：后端 = 真实通道（与 app 装配同形，只换端点）。 */
    private fun attachedGenerator(
        coordinator: DefaultFigureGenerationCoordinator,
    ): AttachedImageGenerator = AttachedImageGenerator(
        lookupExisting = { null },
        isIntact = { true },
        persist = { _, _, _, _, figureId, _ -> asset(sourceAssetId = figureId) },
        resolveCurrentSheetBytes = { null },
        uriFor = { "file://${it.relativePath}" },
        coordinator = coordinator,
    )

    private fun realBackend(): FigureImageBackend = FigureImageBackend(
        configurationFingerprint = imageGenerationConfigurationFingerprint(
            baseUrl,
            OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
        ),
        openChannel = {
            OpenAiImageGenerationChannel(baseUrl = baseUrl, client = OkHttpClient())
        },
        closeAction = {},
    )

    /** 入口三的协调器：与 app 生产装配同形（DefaultFigureGenerationCoordinator + 真实后端）。 */
    private fun productionShapedCoordinator(ledger: RemoteModelDispatchLedger) =
        DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { realBackend() },
        )

    private fun pngResponse(): MockResponse = MockResponse().setBody(
        """{"created":1,"data":[{"b64_json":"${Base64.getEncoder().encodeToString(pngBytes)}",""" +
            """"mime_type":"image/png"}]}""",
    )

    private fun asset(sourceAssetId: String): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
        sourceAssetId = sourceAssetId,
        contentSha256 = figureSha256Hex(pngBytes),
        relativePath = "source-assets/$sourceAssetId.png",
        mimeType = "image/png",
        byteSize = pngBytes.size.toLong(),
        width = 10,
        height = 10,
        sourceType = "GENERATED_FIGURE",
        createdAtEpochMillis = 1,
    )

    private class WiringLedger(
        private val granted: Boolean,
    ) : RemoteModelDispatchLedger {
        val events = mutableListOf<String>()

        override suspend fun reserve(
            request: RemoteModelDispatchRequest,
        ): RemoteModelDispatchDecision {
            events += "reserve"
            if (!granted) {
                return RemoteModelDispatchDecision.Denied(
                    code = RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED,
                    message = "预算已用尽，未出网",
                )
            }
            val attemptOrdinal = when (val parent = request.parent) {
                is FigureDispatchParent.ModelTask -> 1
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

    private class FakeStore(
        baseUrl: String,
    ) : ModelConfigurationStore {
        private val state = MutableStateFlow(
            ModelConfigurationSnapshot(
                provider = "provider",
                baseUrl = baseUrl,
                modelId = "gpt-image-2",
                isConfigured = true,
                updatedAtEpochMillis = 1,
                configurationVersion = "test-config-v1",
                capabilityVerification = ModelCapabilityVerification(
                    provider = "provider",
                    baseUrl = baseUrl,
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
}
