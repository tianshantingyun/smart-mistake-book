package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class OpenAiImageGenerationChannelTest {

    private lateinit var server: MockWebServer
    private lateinit var channel: OpenAiImageGenerationChannel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        channel = OpenAiImageGenerationChannel(
            baseUrl = server.url("/v1").toString().removeSuffix("/"),
            client = okhttp3.OkHttpClient(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refuses a source over the per-asset egress bound before any request`() = runBlocking {
        // 这条链发的是 multipart 原始字节，请求体 ≈ 源图字节 + 少量字段。源图来自规范资产
        // （vault 导入上限 20 MiB），今天必然低于传输上限 36 MiB；此处按单资产出网上限
        // （24 MiB）钉住这条耦合，避免将来放宽来源上限后"读完整块内存才发现超限"。
        val oversized = ByteArray((OpenAiImageGenerationChannel.IMAGE_EDITS_MAX_SOURCE_BYTES + 1).toInt())

        val redraw = runCatching {
            channel.redrawClean(
                ImageRedrawRequest(
                    photoBytes = oversized,
                    photoMimeType = "image/png",
                    instruction = "去手写",
                ),
                dispatchFor(oversized),
            )
        }
        assertTrue("超限的输入必须被拒绝", redraw.isFailure)

        val generate = runCatching {
            channel.generate(
                ImageGenerationRequest(
                    prompt = "重绘题面",
                    sourceImageBytes = oversized,
                    sourceImageMimeType = "image/png",
                ),
                dispatchFor(oversized),
            )
        }
        assertTrue("超限的输入必须被拒绝", generate.isFailure)

        // 关键：拒绝发生在组请求之前——一格字节都不许发出去。
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refuses to post without a granted dispatch`() = runBlocking {
        // 旧的无预留入口是 fail-closed 的：绕过 coordinator 的调用不得产生 HTTP。
        val legacy = runCatching {
            channel.generate(ImageGenerationRequest(prompt = "画一条数轴"))
        }
        assertTrue(legacy.exceptionOrNull() is ImageDispatchNotReservedException)
        val legacyRedraw = runCatching {
            channel.redrawClean(
                ImageRedrawRequest(
                    photoBytes = byteArrayOf(1),
                    photoMimeType = "image/png",
                    instruction = "清理",
                ),
            )
        }
        assertTrue(legacyRedraw.exceptionOrNull() is ImageDispatchNotReservedException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refuses a dispatch bound to different bytes or another configuration`() = runBlocking {
        val source = byteArrayOf(1, 2, 3)
        val request = ImageGenerationRequest(
            prompt = "基于源图重绘",
            sourceImageBytes = source,
            sourceImageMimeType = "image/png",
        )

        val wrongSource = dispatchFor(source).copy(
            sourceImageSha256 = figureSha256Hex(byteArrayOf(9, 9)),
        )
        val sourceError = runCatching {
            channel.generate(request, wrongSource)
        }.exceptionOrNull()
        assertTrue(sourceError is ImageDispatchNotReservedException)

        val wrongConfiguration = dispatchFor(source).copy(
            configurationFingerprint = "another-image-configuration",
        )
        val configurationError = runCatching {
            channel.generate(request, wrongConfiguration)
        }.exceptionOrNull()
        assertTrue(configurationError is ImageDispatchNotReservedException)

        // 两项都是在组请求之前拒绝：一个字节都不发。
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `posts a clean-redraw edit request and decodes the returned image`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val b64 = Base64.getEncoder().encodeToString(png)
        server.enqueue(
            MockResponse().setBody(
                """{"created":1,"data":[{"b64_json":"$b64","mime_type":"image/png"}]}""",
            ),
        )

        val source = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val result = channel.redrawClean(
            ImageRedrawRequest(
                photoBytes = source,
                photoMimeType = "image/png",
                instruction = "去除手写笔迹，保留印刷题面，重绘干净。",
            ),
            dispatchFor(source),
        )

        val recorded = server.takeRequest()
        assertEquals("/v1/images/edits", recorded.path)
        val bodyText = recorded.body.readUtf8()
        assertTrue(bodyText.contains("gpt-image-2"))
        assertTrue(bodyText.contains("去除手写笔迹"))
        assertTrue(bodyText.contains("quality"))
        assertTrue(result.imageBytes.contentEquals(png))
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `surfaces http failures as an exception`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val source = byteArrayOf(1)
        val error = runCatching {
            channel.redrawClean(
                ImageRedrawRequest(
                    photoBytes = source,
                    photoMimeType = "image/png",
                    instruction = "清理",
                ),
                dispatchFor(source),
            )
        }.exceptionOrNull()

        assertTrue(error is ImageGenerationException)
        assertTrue(error?.message?.contains("500") == true)
    }

    @Test
    fun `rejects an empty image payload`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"created":1,"data":[]}"""))

        val source = byteArrayOf(1)
        val error = runCatching {
            channel.redrawClean(
                ImageRedrawRequest(
                    photoBytes = source,
                    photoMimeType = "image/png",
                    instruction = "清理",
                ),
                dispatchFor(source),
            )
        }.exceptionOrNull()

        assertTrue(error is ImageGenerationException)
    }

    @Test
    fun `generates a text-to-image figure via the generations endpoint`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 8, 7)
        val b64 = Base64.getEncoder().encodeToString(png)
        server.enqueue(
            MockResponse().setBody(
                """{"created":1,"data":[{"b64_json":"$b64","mime_type":"image/png"}]}""",
            ),
        )

        val result = channel.generate(
            ImageGenerationRequest(
                prompt = "画出函数 f(x)=x^2 的抛物线，标注顶点与对称轴。",
            ),
            dispatchFor(source = null),
        )

        val recorded = server.takeRequest()
        assertEquals("/v1/images/generations", recorded.path)
        val bodyText = recorded.body.readUtf8()
        assertTrue(bodyText.contains("gpt-image-2"))
        assertTrue(bodyText.contains("抛物线"))
        assertTrue(result.imageBytes.contentEquals(png))
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `generates a seeded figure via the edits endpoint when a source image is given`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 5, 6)
        val b64 = Base64.getEncoder().encodeToString(png)
        server.enqueue(
            MockResponse().setBody(
                """{"created":1,"data":[{"b64_json":"$b64","mime_type":"image/png"}]}""",
            ),
        )

        val source = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        channel.generate(
            ImageGenerationRequest(
                prompt = "基于此题图添加辅助线并重绘。",
                sourceImageBytes = source,
                sourceImageMimeType = "image/png",
            ),
            dispatchFor(source),
        )

        val recorded = server.takeRequest()
        assertEquals("/v1/images/edits", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("辅助线"))
    }

    /** 一份与当前通道配置/源图绑定的**已授予**预留上下文（测试替身，不经过网络）。 */
    private fun dispatchFor(source: ByteArray?): FigureImageDispatchBinding =
        FigureImageDispatchBinding(
            reservationId = "test-reservation",
            operationId = "test-operation",
            method = FigureGenerationMethod.IMAGE_MODEL,
            purpose = FigureGenerationPurpose.EXPLANATION,
            attemptOrdinal = 1,
            sourceImageSha256 = source?.let(::figureSha256Hex),
            configurationFingerprint = channel.configurationFingerprint,
        )
}
