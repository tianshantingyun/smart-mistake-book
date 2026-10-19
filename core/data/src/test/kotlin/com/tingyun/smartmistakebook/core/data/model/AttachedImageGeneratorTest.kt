package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageUnavailableReason
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-05 / KD-58：旧 `attachedImages` 链的**收口**。
 *
 * 三条关闭条件各有用例：
 * 1. 历史打开/展开/旋转/恢复只读本地资产（`resolve*` 一次都不碰 coordinator）；
 * 2. 缓存未命中不再静默——返回带原因的 [AttachedImageResolution.Unavailable]，由 UI 给
 *    “重新生成”显式动作；缺源/损坏不盲目重生成；
 * 3. 显式“重新生成”绑定**原消息**的父操作，经共享 [FigureGenerationCoordinator] 完成
 *    预留→执行→记账，入口不再直连图像通道。
 */
class AttachedImageGeneratorTest {

    private val processImage = AttachedImage(
        imageId = "process-1",
        kind = AttachedImageKind.GENERATE_PROCESS,
        description = "数轴标注导数符号区间",
    )
    private val redrawImage = AttachedImage(
        imageId = "redraw-1",
        kind = AttachedImageKind.REDRAW_PROBLEM,
        description = "重绘题面",
    )

    @Test
    fun `a cache hit reads the local asset without touching the coordinator`() = runBlocking {
        val coordinator = RecordingCoordinator { _, _ -> error("resolve must not generate") }
        val generator = generator(
            lookupExisting = { asset() },
            coordinator = coordinator,
        )

        val resolution = generator.resolveOne(processImage)

        assertEquals(AttachedImageResolution.Ready("file://assets/1.png"), resolution)
        assertEquals("历史打开只读本地资产", 0, coordinator.calls)
    }

    @Test
    fun `rotating and re-resolving the same message stays local`() = runBlocking {
        val coordinator = RecordingCoordinator { _, _ -> error("resolve must not generate") }
        val generator = generator(lookupExisting = { asset() }, coordinator = coordinator)

        repeat(3) {
            assertEquals(
                AttachedImageResolution.Ready("file://assets/1.png"),
                generator.resolveOne(processImage),
            )
        }

        assertEquals(0, coordinator.calls)
    }

    @Test
    fun `a cache miss is a typed reason instead of a silent null`() = runBlocking {
        val coordinator = RecordingCoordinator { _, _ -> error("resolve must not generate") }
        val generator = generator(lookupExisting = { null }, coordinator = coordinator)

        val resolution = generator.resolveOne(processImage)

        assertUnavailable(AttachedImageUnavailableReason.NOT_GENERATED, resolution)
        assertTrue(
            "缓存未命中必须给可读原因",
            (resolution as AttachedImageResolution.Unavailable).detail!!.isNotBlank(),
        )
        assertEquals("缓存未命中不得在渲染时开生图请求", 0, coordinator.calls)
    }

    @Test
    fun `a damaged local copy is reported as damaged and is not regenerated implicitly`() = runBlocking {
        var persists = 0
        val coordinator = RecordingCoordinator { _, _ -> error("resolve must not generate") }
        val generator = generator(
            lookupExisting = { asset() },
            isIntact = { false },
            persist = { _, _, sourceType, _, figureId, _ ->
                persists += 1
                asset(sourceType = sourceType, sourceAssetId = figureId)
            },
            coordinator = coordinator,
        )

        val resolution = generator.resolveOne(processImage)

        assertUnavailable(AttachedImageUnavailableReason.DAMAGED, resolution)
        assertEquals(0, persists)
        assertEquals(0, coordinator.calls)
    }

    @Test
    fun `a redraw without the original sheet is an explicit missing-source state`() = runBlocking {
        val coordinator = RecordingCoordinator { _, _ -> error("resolve must not generate") }
        val generator = generator(
            lookupExisting = { null },
            resolveCurrentSheetBytes = { null },
            coordinator = coordinator,
        )

        assertUnavailable(
            AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
            generator.resolveOne(redrawImage),
        )
        assertUnavailable(
            AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
            generator.regenerate(redrawImage, parentRequestId = "task-1"),
        )
        assertEquals("缺源不得用别的题面顶替", 0, coordinator.calls)
    }

    @Test
    fun `explicit regeneration goes through the shared coordinator with the original message parent`() =
        runBlocking {
            val coordinator = RecordingCoordinator { request, _ -> generated(request) }
            var persistedId: String? = null
            val generator = generator(
                lookupExisting = { null },
                persist = { _, _, sourceType, _, figureId, _ ->
                    persistedId = figureId
                    asset(sourceType = sourceType, sourceAssetId = figureId)
                },
                coordinator = coordinator,
            )

            val resolution = generator.regenerate(redrawImage, parentRequestId = "task-1")

            assertEquals(AttachedImageResolution.Ready("file://assets/1.png"), resolution)
            val request = coordinator.requests.single()
            assertEquals(
                FigureDispatchParent.ModelTask("task-1", reserveChatFinalAnswer = false),
                request.dispatchParent,
            )
            assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, request.purpose)
            assertNull("预算声明不得再用常量 6 冒充实余量", request.budget.remainingRemoteDispatches)
            assertEquals(FigureGenerationPolicy.POLICY_VERSION, request.prompt?.policyVersion)
            assertTrue(request.source!!.imageBytes()!!.contentEquals(byteArrayOf(2)))
            assertTrue("幂等键派生 id 必须稳定", persistedId!!.startsWith("figure-"))

            val proposal = coordinator.proposals.single()
            assertEquals(FigureGenerationPolicy.POLICY_VERSION, proposal.policyVersion)
            assertEquals(FigureGenerationMethod.IMAGE_MODEL, proposal.method)
            assertEquals(FigureImageOperation.EDIT, proposal.imageOperation)
            assertTrue(proposal.imagePrompt!!.contains("去除手写笔迹"))
        }

    @Test
    fun `explicit regeneration of a process figure is an unseeded GENERATE`() = runBlocking {
        val coordinator = RecordingCoordinator { request, _ -> generated(request) }
        val generator = generator(lookupExisting = { null }, coordinator = coordinator)

        generator.regenerate(processImage, parentRequestId = "task-1")

        val request = coordinator.requests.single()
        assertEquals(FigureGenerationPurpose.EXPLANATION, request.purpose)
        assertNull(request.source)
        assertEquals(FigureImageOperation.GENERATE, coordinator.proposals.single().imageOperation)
    }

    @Test
    fun `explicit regeneration without a coordinator is unavailable and makes no request`() = runBlocking {
        val generator = generator(lookupExisting = { null }, coordinator = null)

        assertUnavailable(
            AttachedImageUnavailableReason.REGENERATION_UNAVAILABLE,
            generator.regenerate(processImage, parentRequestId = "task-1"),
        )
    }

    @Test
    fun `a denied or failed coordinator outcome keeps a readable reason and persists nothing`() =
        runBlocking {
            var persists = 0
            val coordinator = RecordingCoordinator { request, _ ->
                FigureGenerationOutcome(
                    operationId = request.operationId,
                    purpose = request.purpose,
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    status = FigureGenerationStatus.FAILED,
                    diagnostics = "逻辑操作预算已用尽（图像与工具轮共享），未出网",
                )
            }
            val generator = generator(
                lookupExisting = { null },
                persist = { _, _, sourceType, _, figureId, _ ->
                    persists += 1
                    asset(sourceType = sourceType, sourceAssetId = figureId)
                },
                coordinator = coordinator,
            )

            val resolution = generator.regenerate(processImage, parentRequestId = "task-1")

            val unavailable = resolution as AttachedImageResolution.Unavailable
            assertEquals(
                AttachedImageUnavailableReason.REGENERATION_FAILED,
                unavailable.reason,
            )
            assertTrue(
                "拒绝原因必须可读地传回界面：$unavailable",
                unavailable.detail!!.contains("预算"),
            )
            assertEquals(0, persists)
        }

    @Test
    fun `a cancellation during regeneration propagates instead of degrading`() = runBlocking {
        val coordinator = RecordingCoordinator { _, _ -> throw CancellationException() }
        val generator = generator(lookupExisting = { null }, coordinator = coordinator)

        val error = runCatching {
            generator.regenerate(processImage, parentRequestId = "task-1")
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    /** A2：登记时必须带生成图的 sourceType、幂等键派生的编号与隐式元数据。 */
    @Test
    fun `persists the generated figure with its provenance and key derived id`() = runBlocking {
        var capturedType: String? = null
        var capturedId: String? = null
        var capturedProvenance: GeneratedFigureProvenance? = null
        val coordinator = RecordingCoordinator { request, _ -> generated(request) }
        generator(
            lookupExisting = { null },
            persist = { _, _, sourceType, _, figureId, provenance ->
                capturedType = sourceType
                capturedId = figureId
                capturedProvenance = provenance
                asset(sourceType = sourceType, sourceAssetId = figureId)
            },
            coordinator = coordinator,
        ).regenerate(processImage, parentRequestId = "task-1")

        assertEquals("GENERATED_FIGURE", capturedType)
        assertTrue(capturedId!!.startsWith("figure-"))
        assertEquals(capturedId, capturedProvenance?.figureId)
        assertEquals("GENERATE_PROCESS", capturedProvenance?.purpose)
    }

    private fun assertUnavailable(
        expected: AttachedImageUnavailableReason,
        resolution: AttachedImageResolution,
    ) {
        assertTrue("期望不可用态，实际：$resolution", resolution is AttachedImageResolution.Unavailable)
        assertEquals(expected, (resolution as AttachedImageResolution.Unavailable).reason)
    }

    private fun generated(request: FigureGenerationRequest): FigureGenerationOutcome =
        FigureGenerationOutcome(
            operationId = request.operationId,
            purpose = request.purpose,
            method = FigureGenerationMethod.IMAGE_MODEL,
            status = FigureGenerationStatus.GENERATED,
            artifact = FigureArtifact.ImageAsset(
                method = FigureGenerationMethod.IMAGE_MODEL,
                purpose = request.purpose,
                mimeType = "image/png",
                contentSha256 = "b".repeat(64),
                modelId = "fake-image-model",
                imageBytes = byteArrayOf(1, 2, 3),
            ),
        )

    @Test
    fun `the production resolver carries reasons and regeneration through the legacy function seam`() =
        runBlocking {
            val resolver = AttachedImageRuntimeResolver(generator(lookupExisting = { null }))
            val asLegacyFunction: suspend (AttachedImage) -> String? = resolver

            assertNull("旧函数类型仍只回本地 URI", asLegacyFunction(processImage))
            val unavailable = resolver.resolve(processImage) as AttachedImageResolution.Unavailable
            assertEquals(
                "带原因解析不再把缓存未命中折叠成静默 null",
                AttachedImageUnavailableReason.NOT_GENERATED,
                unavailable.reason,
            )
            val regeneration =
                resolver.regenerate("task-1", processImage) as AttachedImageResolution.Unavailable
            assertEquals(
                "没有注入共享 coordinator 时显式重生成如实回不可用",
                AttachedImageUnavailableReason.REGENERATION_UNAVAILABLE,
                regeneration.reason,
            )
        }

    private fun generator(
        lookupExisting: suspend (String) -> CanonicalSourceAssetRecord? = { null },
        isIntact: (CanonicalSourceAssetRecord) -> Boolean = { true },
        persist: suspend (
            ByteArray,
            String,
            String,
            Long,
            String,
            GeneratedFigureProvenance,
        ) -> CanonicalSourceAssetRecord = { _, _, sourceType, _, figureId, _ ->
            asset(sourceType = sourceType, sourceAssetId = figureId)
        },
        resolveCurrentSheetBytes: suspend () -> ByteArray? = { byteArrayOf(2) },
        coordinator: FigureGenerationCoordinator? = null,
    ) = AttachedImageGenerator(
        lookupExisting = lookupExisting,
        isIntact = isIntact,
        persist = persist,
        resolveCurrentSheetBytes = resolveCurrentSheetBytes,
        uriFor = { "file://${it.relativePath}" },
        coordinator = coordinator,
    )

    private fun asset(
        sourceType: String = "GENERATED_FIGURE",
        sourceAssetId: String = "asset-1",
    ) = CanonicalSourceAssetRecord(
        sourceAssetId = sourceAssetId,
        contentSha256 = "a".repeat(64),
        relativePath = "assets/1.png",
        mimeType = "image/png",
        byteSize = 4,
        width = 10,
        height = 10,
        sourceType = sourceType,
        createdAtEpochMillis = 1,
    )

    private class RecordingCoordinator(
        private val outcome: suspend (FigureGenerationRequest, FigureGenerationProposal) ->
            FigureGenerationOutcome,
    ) : FigureGenerationCoordinator {
        val requests = mutableListOf<FigureGenerationRequest>()
        val proposals = mutableListOf<FigureGenerationProposal>()
        var calls: Int = 0
            private set

        override suspend fun generate(
            request: FigureGenerationRequest,
            proposal: FigureGenerationProposal,
        ): FigureGenerationOutcome {
            calls += 1
            requests += request
            proposals += proposal
            return outcome(request, proposal)
        }
    }
}
