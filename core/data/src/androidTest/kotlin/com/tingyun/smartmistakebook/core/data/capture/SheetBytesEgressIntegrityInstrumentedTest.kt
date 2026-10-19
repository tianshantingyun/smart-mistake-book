package com.tingyun.smartmistakebook.core.data.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.model.AttachedImageGenerator
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureTranscriptionReview
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageUnavailableReason
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 题面字节出网前的完整性核对（2026-09-14）。
 *
 * 消灭的失败：讲题会话的题面按 URI 直接读文件，是图片链上最后一处「按路径取字节」的地方——
 * 规范记录核对过之后又按路径重读一次，中间被替换/损坏的字节仍可能被 POST 出去。现在字节
 * 一律取自 `vault.resolve(record)`（逐位核对 sha256 与字节数）。核对不过时这条链的姿态是
 * **fail-closed：没有图，而不是崩**——`AttachedImageGenerator.requestFor` 读源失败即返回
 * null（显式重生成回 `SOURCE_UNAVAILABLE`），不会把被替换的字节送进协调器。
 */
@RunWith(AndroidJUnit4::class)
class SheetBytesEgressIntegrityInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun tamperedOrMissingSheetNeverReachesTheImageRequest() = runBlocking {
        val source = createJpeg(24, 24)
        val imported = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = "sheet-egress-import",
                localUri = privateUri(source).toString(),
                source = CaptureInputSource.CAMERA,
                // 讲题会话只能由 TUTOR 入口的采集创建（confirmForTutoring 的既有前置）。
                origin = CaptureEntryOrigin.TUTOR,
                occurredAtEpochMillis = 1_000,
            ),
        )
        val session = repository.confirmForTutoring(
            ConfirmCapturedProblemRequest(
                requestId = "sheet-egress-confirm",
                draftId = imported.draftId,
                expectedRevisionNumber = imported.revisionNumber,
                subject = "MATH",
                title = "含图函数题",
                transcription = "求单调区间。",
                writingLayer = CaptureWritingLayer.PRINTED,
                transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
                occurredAtEpochMillis = 2_000,
            ),
        )

        var generated = 0
        val sentSourceBytes = mutableListOf<ByteArray>()
        // WG-05：显式重生成走共享协调器；这里只观测"有没有带着核对过的题面字节走到这里"。
        val coordinator = object : FigureGenerationCoordinator {
            override suspend fun generate(
                request: FigureGenerationRequest,
                proposal: FigureGenerationProposal,
            ): FigureGenerationOutcome {
                generated += 1
                val source = request.source?.imageBytes() ?: ByteArray(0)
                sentSourceBytes += source
                return FigureGenerationOutcome(
                    operationId = request.operationId,
                    purpose = request.purpose,
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    status = FigureGenerationStatus.GENERATED,
                    artifact = FigureArtifact.ImageAsset(
                        method = FigureGenerationMethod.IMAGE_MODEL,
                        purpose = request.purpose,
                        mimeType = "image/png",
                        contentSha256 = figureSha256Hex(source),
                        modelId = "fake-image-model",
                        imageBytes = source,
                    ),
                )
            }
        }
        val generator = AttachedImageGenerator(
            persist = { bytes, mimeType, sourceType, createdAt, figureId, _ ->
                CanonicalSourceAssetRecord(
                    sourceAssetId = figureId,
                    contentSha256 = "0".repeat(64),
                    relativePath = "source-assets/$figureId.png",
                    mimeType = mimeType,
                    byteSize = bytes.size.toLong(),
                    width = 1,
                    height = 1,
                    sourceType = sourceType,
                    createdAtEpochMillis = createdAt,
                )
            },
            // 本用例只关心"字节有没有被核对"，不做幂等命中（每次重生成都算新请求）。
            lookupExisting = { null },
            isIntact = { true },
            resolveCurrentSheetBytes = { repository.readTutorSessionSheetBytes(session.sessionId) },
            uriFor = { record -> "file:///${record.relativePath}" },
            coordinator = coordinator,
        )
        val redraw = AttachedImage(
            imageId = "redraw-1",
            kind = AttachedImageKind.REDRAW_PROBLEM,
            description = "重绘题面",
            accessibilityText = "重绘后的题面图",
        )

        // 基线：完好的规范资产读得出来，并真的走到重生成请求（否则下面的断言证明不了任何事）。
        assertNotNull(repository.readTutorSessionSheetBytes(session.sessionId))
        assertTrue(generator.regenerate(redraw, parentRequestId = "task-1") is AttachedImageResolution.Ready)
        assertEquals(1, generated)
        assertTrue(sentSourceBytes.single().isNotEmpty())

        // 同样的路径、同样的字节数、不同的内容：只有逐位核对能抓住它。
        val record = requireNotNull(database.readProblemDraft(imported.draftId)?.sourceAsset)
        val canonicalFile = File(context.filesDir, record.relativePath)
        canonicalFile.writeBytes(ByteArray(record.byteSize.toInt()) { 7 })

        assertTrue(
            "被替换的题图必须被核对拦下，而不是原样返回字节",
            runCatching { repository.readTutorSessionSheetBytes(session.sessionId) }.isFailure,
        )
        assertEquals(
            AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
            (generator.regenerate(redraw, parentRequestId = "task-1")
                as AttachedImageResolution.Unavailable).reason,
        )
        assertEquals("核对不过时不得再发起生成请求", 1, generated)

        // 文件没了同样拦下（不是退回"按路径尽力读"）。
        canonicalFile.delete()
        assertTrue(
            runCatching { repository.readTutorSessionSheetBytes(session.sessionId) }.isFailure,
        )
        assertEquals(
            AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
            (generator.regenerate(redraw, parentRequestId = "task-1")
                as AttachedImageResolution.Unavailable).reason,
        )
        assertEquals(1, generated)
    }
}
