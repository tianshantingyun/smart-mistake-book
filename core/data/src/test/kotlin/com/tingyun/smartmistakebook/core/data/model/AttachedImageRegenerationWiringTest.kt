package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.data.visual.DefaultFigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.data.visual.FigureImageBackend
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageUnavailableReason
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-05：旧 `attachedImages` 的**显式重新生成**必须走真实生产协调器
 * （`DefaultFigureGenerationCoordinator` + `RemoteModelDispatchLedger` + 带绑定的图像通道），
 * 而不是在入口直连 `channel.generate(request)`。
 *
 * 用 fake 后端跑完整序列，钉住“无预留 → 0 图像 HTTP”在兼容链上同样成立。
 */
class AttachedImageRegenerationWiringTest {

    private val processImage = AttachedImage(
        imageId = "process-1",
        kind = AttachedImageKind.GENERATE_PROCESS,
        description = "画出导数为正与为负的区间标注图",
    )

    @Test
    fun `a denied reservation yields zero image HTTP for the legacy chain`() = runBlocking {
        val ledger = WiringLedger(granted = false)
        val channel = RecordingChannel(ledger.events)
        val generator = generator(coordinator(ledger, channel))

        val resolution = generator.regenerate(processImage, parentRequestId = "task-1")

        val unavailable = resolution as AttachedImageResolution.Unavailable
        assertEquals(
            AttachedImageUnavailableReason.REGENERATION_FAILED,
            unavailable.reason,
        )
        assertTrue("拒绝理由必须可读：$unavailable", unavailable.detail!!.contains("预算"))
        assertEquals(0, channel.generateCalls)
        assertEquals(listOf("reserve"), ledger.events)
    }

    @Test
    fun `a granted reservation posts once with the granted binding and persists the artifact`() =
        runBlocking {
            val ledger = WiringLedger(granted = true)
            val channel = RecordingChannel(ledger.events)
            var persistedId: String? = null
            val generator = generator(
                coordinator = coordinator(ledger, channel),
                persist = { _, _, sourceType, _, figureId, _ ->
                    persistedId = figureId
                    asset(sourceType = sourceType, sourceAssetId = figureId)
                },
            )

            val resolution = generator.regenerate(processImage, parentRequestId = "task-1")

            assertEquals(AttachedImageResolution.Ready("file://assets/1.png"), resolution)
            assertEquals(listOf("reserve", "post"), ledger.events)
            assertEquals(1, channel.generateCalls)
            val dispatch = channel.dispatches.single()
            assertEquals(FigureGenerationMethod.IMAGE_MODEL, dispatch.method)
            assertEquals("config-fingerprint", dispatch.configurationFingerprint)
            assertTrue(
                "显式重生成绑定原消息的父操作：${dispatch.operationId}",
                dispatch.operationId.startsWith("attached-image-op:task-1:"),
            )
            assertTrue(persistedId!!.startsWith("figure-"))
        }

    @Test
    fun `opening a historical message never reserves or posts`() = runBlocking {
        val ledger = WiringLedger(granted = true)
        val channel = RecordingChannel(ledger.events)
        val generator = generator(
            coordinator = coordinator(ledger, channel),
            lookupExisting = { asset() },
        )

        repeat(3) { generator.resolveOne(processImage) }

        assertTrue(ledger.events.isEmpty())
        assertEquals(0, channel.generateCalls)
    }

    private fun coordinator(
        ledger: RemoteModelDispatchLedger,
        channel: RecordingChannel,
    ): DefaultFigureGenerationCoordinator = DefaultFigureGenerationCoordinator(
        ledger = ledger,
        openBackend = {
            FigureImageBackend(
                configurationFingerprint = "config-fingerprint",
                openChannel = { channel },
                closeAction = {},
            )
        },
    )

    private fun generator(
        coordinator: DefaultFigureGenerationCoordinator,
        lookupExisting: suspend (String) -> CanonicalSourceAssetRecord? = { null },
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
    ) = AttachedImageGenerator(
        lookupExisting = lookupExisting,
        isIntact = { true },
        persist = persist,
        resolveCurrentSheetBytes = { null },
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
                    message = "逻辑操作的远程派发预算已用尽（图像与工具轮共享），未出网",
                )
            }
            return RemoteModelDispatchDecision.Granted(
                RemoteModelDispatchReservation(
                    reservationId = "reservation:${request.operationId}:1",
                    operationId = request.operationId,
                    parent = request.parent,
                    remoteKind = request.remoteKind,
                    method = request.method,
                    purpose = request.purpose,
                    policyVersion = request.policyVersion,
                    sourceImageSha256 = request.sourceImageSha256,
                    configurationFingerprint = request.configurationFingerprint,
                    attemptOrdinal = 1,
                    reservedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
        }
    }

    private class RecordingChannel(
        private val events: MutableList<String>,
    ) : ImageGenerationChannel {
        val dispatches = mutableListOf<FigureImageDispatchBinding>()
        var generateCalls: Int = 0
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            events += "post"
            dispatches += dispatch
            return ImageRedrawResult(
                imageBytes = byteArrayOf(9, 8, 7),
                mimeType = "image/png",
                modelVersion = "fake-image-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used by this entry")
    }
}
