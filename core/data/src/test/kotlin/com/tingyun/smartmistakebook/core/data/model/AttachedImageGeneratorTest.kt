package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `resolves both kinds to local uris`() = runBlocking {
        val requests = mutableListOf<ImageGenerationRequest>()
        val generator = generator(
            generate = { request ->
                requests += request
                ImageRedrawResult(imageBytes = byteArrayOf(1), mimeType = "image/png")
            },
            resolveCurrentSheetBytes = { byteArrayOf(2) },
        )

        val uris = generator.resolve(listOf(processImage, redrawImage), createdAtEpochMillis = 1)

        assertEquals(2, uris.size)
        assertEquals("file://assets/1.png", uris["process-1"])
        assertEquals("file://assets/1.png", uris["redraw-1"])
        // GENERATE_PROCESS 无源图；REDRAW_PROBLEM 带源图。
        assertNull(requests[0].sourceImageBytes)
        assertTrue(requests[1].sourceImageBytes != null)
    }

    @Test
    fun `skips a redraw problem when the current sheet is unavailable`() = runBlocking {
        val generator = generator(
            generate = { ImageRedrawResult(byteArrayOf(1), "image/png") },
            resolveCurrentSheetBytes = { null },
        )

        val uris = generator.resolve(listOf(redrawImage), createdAtEpochMillis = 1)

        assertTrue(uris.isEmpty())
    }

    @Test
    fun `a generation failure degrades to that image's absence`() = runBlocking {
        val generator = generator(
            generate = { _ -> throw ImageGenerationException("network down") },
            resolveCurrentSheetBytes = { byteArrayOf(1) },
        )

        val uris = generator.resolve(listOf(processImage), createdAtEpochMillis = 1)

        assertTrue(uris.isEmpty())
    }

    @Test
    fun `cancellation propagates instead of degrading`() = runBlocking {
        val generator = generator(
            generate = { _ -> throw CancellationException() },
            resolveCurrentSheetBytes = { byteArrayOf(1) },
        )

        val error = runCatching { generator.resolve(listOf(processImage), createdAtEpochMillis = 1) }
            .exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    @Test
    fun `generation prompts are shaped for the image kind`() = runBlocking {
        val prompts = mutableListOf<String>()
        generator(
            generate = { request ->
                prompts += request.prompt
                ImageRedrawResult(byteArrayOf(1), "image/png")
            },
            resolveCurrentSheetBytes = { byteArrayOf(1) },
        ).resolve(listOf(processImage, redrawImage), createdAtEpochMillis = 1)

        assertTrue(prompts[0].contains("解析过程图"))
        assertTrue(prompts[0].contains("数轴标注"))
        assertTrue(prompts[1].contains("去除手写笔迹"))
    }

    /**
     * A2：同一张图解析两次（旋转/进程重建后重新渲染的形态）**只生成一次**——第二次按
     * 幂等键命中已登记资产，通道调用计数 = 1。
     */
    @Test
    fun `re-resolving the same image hits the stored asset without paying again`() = runBlocking {
        var generateCalls = 0
        val stored = mutableMapOf<String, CanonicalSourceAssetRecord>()
        val generator = generator(
            generate = { _ ->
                generateCalls += 1
                ImageRedrawResult(byteArrayOf(1), "image/png")
            },
            lookupExisting = { figureId -> stored[figureId] },
            persist = { _, _, sourceType, _, figureId, _ ->
                val record = asset(sourceType = sourceType, sourceAssetId = figureId)
                stored[figureId] = record
                record
            },
            resolveCurrentSheetBytes = { byteArrayOf(1) },
        )

        val first = generator.resolve(listOf(processImage), createdAtEpochMillis = 1)
        val second = generator.resolve(listOf(processImage), createdAtEpochMillis = 2)

        assertEquals(1, generateCalls)
        assertEquals(first["process-1"], second["process-1"])
    }

    /** A2：登记时必须带生成图的 sourceType、幂等键派生的编号与隐式元数据。 */
    @Test
    fun `persists the generated figure with its provenance and key derived id`() = runBlocking {
        var capturedType: String? = null
        var capturedId: String? = null
        var capturedProvenance: GeneratedFigureProvenance? = null
        generator(
            generate = { ImageRedrawResult(byteArrayOf(1), "image/png") },
            persist = { _, _, sourceType, _, figureId, provenance ->
                capturedType = sourceType
                capturedId = figureId
                capturedProvenance = provenance
                asset(sourceType = sourceType, sourceAssetId = figureId)
            },
            resolveCurrentSheetBytes = { byteArrayOf(1) },
        ).resolve(listOf(processImage), createdAtEpochMillis = 1)

        assertEquals("GENERATED_FIGURE", capturedType)
        assertTrue(capturedId!!.startsWith("figure-"))
        assertEquals(capturedId, capturedProvenance?.figureId)
        assertEquals("GENERATE_PROCESS", capturedProvenance?.purpose)
    }

    private fun generator(
        generate: suspend (ImageGenerationRequest) -> ImageRedrawResult,
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
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
    ) = AttachedImageGenerator(
        generate = generate,
        lookupExisting = lookupExisting,
        isIntact = isIntact,
        persist = persist,
        resolveCurrentSheetBytes = resolveCurrentSheetBytes,
        uriFor = { "file://${it.relativePath}" },
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
}
