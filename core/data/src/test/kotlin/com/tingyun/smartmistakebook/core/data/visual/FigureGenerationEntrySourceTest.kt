package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.model.AttachedImageGenerator
import com.tingyun.smartmistakebook.core.data.model.ConfiguredTutorFigureGenerator
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.TutorFigureRequest
import com.tingyun.smartmistakebook.core.data.model.TutorFigureStatus
import com.tingyun.smartmistakebook.core.data.model.attachedImageRedrawInstruction
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityVerification
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.figureBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-08 T1b：四个生成入口必须给统一请求填 `sourceKind/sourceId/basisFingerprint`。
 *
 * 改前审计行只能靠 `operation_id` 猜"这次生成属于哪条消息/哪次重绘"；入口各写一份来源映射
 * 又会漂移。这里对每个入口直接捕获**真实交给协调器的请求**，钉住来源口径：
 * - 讲题工具 → TUTOR_MESSAGE / 父模型任务 requestId；
 * - 保存期净化 → CLEAN_REDRAW / 持久净化作业 taskId；
 * - 历史配图显式重生成 → TUTOR_MESSAGE / 原消息 requestId。
 *
 * 旧的 `FigureGenerationRequest` 调用不填这三个字段时由协调器按父操作派生（见
 * `FigureGenerationAuditRecorderTest.aLegacyRequestDerivesItsSourceFromTheDispatchParent`）。
 *
 * 同时钉住"入口落盘后回填 output_asset_id"：讲题入口与历史配图入口在持久化成功后，
 * 把 outcome 的 attemptId 与新资产 id 一起交给回填缝；净化入口只回字节、落盘在作业执行器，
 * 不在本类回填（T3 的显式重绘接线负责）。
 */
class FigureGenerationEntrySourceTest {

    @Test
    fun theTutorFigureEntryCarriesTheTutorMessageSource() = runBlocking {
        val coordinator = CapturingCoordinator()
        val generator = tutorGenerator(coordinator)

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "画出函数图像并标注顶点",
                parentOperationId = "tutor-task-1",
            ),
        )

        assertEquals(TutorFigureStatus.FAILED, result.status)
        val request = coordinator.requests.single()
        assertEquals(FigureGenerationSourceKind.TUTOR_MESSAGE, request.sourceKind)
        assertEquals("tutor-task-1", request.sourceId)
        assertBasis(request)
    }

    @Test
    fun theSaveTimeCleanRedrawEntryCarriesTheCleanTaskSource() = runBlocking {
        val coordinator = CapturingCoordinator()
        val generator = ConfiguredCleanImageGenerator(
            configurationStore = FakeStore(),
            channelFactory = ChannelFactory(),
            networkRequestsAllowed = true,
            coordinator = coordinator,
        )

        val result = generator.generateClean(
            CleanImageRequest(
                originalBytes = SOURCE_BYTES,
                mimeType = "image/png",
                parent = FigureDispatchParent.CleanRedrawTask("clean-task-1", 1),
                operationId = "clean-redraw-op:clean-task-1",
            ),
        )

        assertEquals(null, result)
        val request = coordinator.requests.single()
        assertEquals(FigureGenerationSourceKind.CLEAN_REDRAW, request.sourceKind)
        assertEquals("clean-task-1", request.sourceId)
        assertBasis(request)
    }

    @Test
    fun theAttachedImageRegenerationCarriesTheOriginalMessageSource() = runBlocking {
        val coordinator = CapturingCoordinator()
        val generator = attachedGenerator(coordinator)

        val resolution = generator.regenerate(REDRAW_IMAGE, parentRequestId = "message-1")

        assertTrue(resolution is com.tingyun.smartmistakebook.core.model.AttachedImageResolution.Unavailable)
        val request = coordinator.requests.single()
        assertEquals(FigureGenerationSourceKind.TUTOR_MESSAGE, request.sourceKind)
        assertEquals("message-1", request.sourceId)
        assertEquals(
            figureBasisFingerprint(
                request.operationId,
                figureSha256Hex(SOURCE_BYTES),
                attachedImageRedrawInstruction(),
            ),
            request.basisFingerprint,
        )
    }

    @Test
    fun aPersistedTutorFigureBackfillsTheAttemptOutputAsset() = runBlocking {
        val coordinator = GeneratedCoordinator()
        var recorded: Pair<String, String>? = null
        val generator = tutorGenerator(
            coordinator = coordinator,
            recordOutputAsset = { attemptId, assetId -> recorded = attemptId to assetId },
        )

        val result = generator.generate(
            TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS,
                description = "画一张过程图",
                parentOperationId = "tutor-task-2",
            ),
        )

        assertEquals(TutorFigureStatus.GENERATED, result.status)
        assertNotNull(result.figureId)
        assertEquals(GeneratedCoordinator.ATTEMPT_ID to result.figureId, recorded)
    }

    @Test
    fun aPersistedAttachedImageBackfillsTheAttemptOutputAsset() = runBlocking {
        val coordinator = GeneratedCoordinator()
        var recorded: Pair<String, String>? = null
        val generator = attachedGenerator(
            coordinator = coordinator,
            recordOutputAsset = { attemptId, assetId -> recorded = attemptId to assetId },
        )

        val resolution = generator.regenerate(REDRAW_IMAGE, parentRequestId = "message-2")

        assertTrue(resolution is com.tingyun.smartmistakebook.core.model.AttachedImageResolution.Ready)
        assertNotNull(recorded)
        assertEquals(GeneratedCoordinator.ATTEMPT_ID, recorded!!.first)
        assertTrue(recorded!!.second.isNotBlank())
    }

    // -----------------------------------------------------------------

    private fun assertBasis(request: FigureGenerationRequest) {
        val basis = requireNotNull(request.basisFingerprint)
        assertEquals(64, basis.length)
        assertTrue("basis 必须是小写十六进制", basis.all { it in '0'..'9' || it in 'a'..'f' })
    }

    private fun tutorGenerator(
        coordinator: FigureGenerationCoordinator,
        recordOutputAsset: suspend (String, String) -> Unit = { _, _ -> },
    ): ConfiguredTutorFigureGenerator = ConfiguredTutorFigureGenerator(
        configurationStore = FakeStore(),
        networkRequestsAllowed = true,
        readSourceBytes = { SOURCE_BYTES },
        lookupGenerated = { null },
        isIntact = { true },
        persistGenerated = { bytes, mimeType, createdAt, figureId, provenance ->
            asset(sourceAssetId = figureId, contentSha256 = figureSha256Hex(bytes), mimeType = mimeType)
        },
        channelFactory = ChannelFactory(),
        coordinator = coordinator,
        recordOutputAsset = recordOutputAsset,
    )

    private fun attachedGenerator(
        coordinator: FigureGenerationCoordinator,
        recordOutputAsset: suspend (String, String) -> Unit = { _, _ -> },
    ): AttachedImageGenerator = AttachedImageGenerator(
        lookupExisting = { null },
        isIntact = { true },
        persist = { bytes, mimeType, sourceType, _, figureId, _ ->
            asset(sourceAssetId = figureId, contentSha256 = figureSha256Hex(bytes), mimeType = mimeType)
        },
        resolveCurrentSheetBytes = { SOURCE_BYTES },
        uriFor = { record -> "file://${record.relativePath}" },
        coordinator = coordinator,
        recordOutputAsset = recordOutputAsset,
    )

    private fun asset(
        sourceAssetId: String,
        contentSha256: String = figureSha256Hex(SOURCE_BYTES),
        mimeType: String = "image/png",
    ): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
        sourceAssetId = sourceAssetId,
        contentSha256 = contentSha256,
        relativePath = "source-assets/$sourceAssetId.png",
        mimeType = mimeType,
        byteSize = SOURCE_BYTES.size.toLong(),
        width = 10,
        height = 10,
        sourceType = "GENERATED_FIGURE",
        createdAtEpochMillis = 1,
    )

    private open class CapturingCoordinator : FigureGenerationCoordinator {
        val requests = mutableListOf<FigureGenerationRequest>()

        override suspend fun generate(
            request: FigureGenerationRequest,
            proposal: FigureGenerationProposal,
        ): FigureGenerationOutcome {
            requests += request
            return FigureGenerationOutcome(
                operationId = request.operationId,
                purpose = request.purpose,
                method = proposal.method,
                status = FigureGenerationStatus.FAILED,
                diagnostics = "test-coordinator",
            )
        }
    }

    private class GeneratedCoordinator : CapturingCoordinator() {
        override suspend fun generate(
            request: FigureGenerationRequest,
            proposal: FigureGenerationProposal,
        ): FigureGenerationOutcome {
            requests += request
            return FigureGenerationOutcome(
                operationId = request.operationId,
                purpose = request.purpose,
                method = FigureGenerationMethod.IMAGE_MODEL,
                status = FigureGenerationStatus.GENERATED,
                artifact = FigureArtifact.ImageAsset(
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    purpose = request.purpose,
                    mimeType = "image/png",
                    contentSha256 = figureSha256Hex(FigureGenerationEntrySourceTest.RESULT_BYTES),
                    modelId = "test-model",
                    assetId = null,
                    imageBytes = FigureGenerationEntrySourceTest.RESULT_BYTES,
                ),
                attemptId = ATTEMPT_ID,
            )
        }

        companion object {
            const val ATTEMPT_ID = "figure-attempt:test:1:IMAGE_MODEL"
        }
    }

    private class ChannelFactory : ConfiguredCleanImageGenerator.ChannelFactory {
        override suspend fun create(
            baseUrl: String,
            authorization: String,
        ): ImageGenerationChannel = throw UnsupportedOperationException("coordinator is faked")
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
        val RESULT_BYTES = byteArrayOf(9, 8, 7, 6)
        val REDRAW_IMAGE = AttachedImage(
            imageId = "redraw-1",
            kind = AttachedImageKind.REDRAW_PROBLEM,
            description = "重绘题面",
        )
    }
}
