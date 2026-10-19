package com.tingyun.smartmistakebook.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A7（4C）分享入口**外部输入门**的 JVM 断言。
 *
 * 每个用例都对应 [planCaptureShare] KDoc 里的一条门；重点不是"能过"，而是：
 * - 便宜的拒绝在昂贵动作之前（数量/方案门不碰 ContentResolver）；
 * - 边界值（恰好等于上限 vs 多 1 字节）；
 * - 分享层的图片预算与拍照/相册入口是同一个数（防两套口径漂移）。
 */
class CaptureSharePolicyTest {
    private fun contentUri(index: Int) = "content://com.example.provider/share/$index"

    private fun plan(
        uris: List<String>,
        kinds: Map<String, CaptureShareKind?> = emptyMap(),
        sizes: Map<String, Long> = emptyMap(),
        kindCalls: MutableList<String> = mutableListOf(),
        sizeCalls: MutableList<String> = mutableListOf(),
    ): CaptureSharePlan = planCaptureShare(
        rawUris = uris,
        kindOf = { uri ->
            kindCalls += uri
            kinds[uri]
        },
        sizeOf = { uri ->
            sizeCalls += uri
            sizes[uri]
        },
    )

    private fun rejection(plan: CaptureSharePlan): CaptureShareRejection {
        assertTrue("expected a rejection, was $plan", plan is CaptureSharePlan.Rejected)
        return (plan as CaptureSharePlan.Rejected).reason
    }

    @Test
    fun `empty share is rejected as nothing shared`() {
        assertEquals(
            CaptureShareRejection.NOTHING_SHARED,
            rejection(plan(emptyList())),
        )
    }

    @Test
    fun `blank uris are dropped before counting`() {
        val uri = contentUri(1)
        val plan = plan(
            uris = listOf("", "  ", uri),
            kinds = mapOf(uri to CaptureShareKind.IMAGE),
            sizes = mapOf(uri to 10L),
        )
        assertTrue(plan is CaptureSharePlan.Images)
        assertEquals(listOf(uri), (plan as CaptureSharePlan.Images).uris)
    }

    @Test
    fun `duplicate uris count once and keep the first occurrence`() {
        val first = contentUri(1)
        val second = contentUri(2)
        val plan = plan(
            uris = listOf(first, second, first),
            kinds = mapOf(
                first to CaptureShareKind.IMAGE,
                second to CaptureShareKind.IMAGE,
            ),
            sizes = mapOf(first to 10L, second to 20L),
        )
        assertEquals(listOf(first, second), (plan as CaptureSharePlan.Images).uris)
    }

    @Test
    fun `more than thirty files is rejected before the provider is touched`() {
        val kindCalls = mutableListOf<String>()
        val sizeCalls = mutableListOf<String>()
        val uris = (0 until MAX_SHARE_IMPORT_FILES + 1).map(::contentUri)

        val rejection = rejection(plan(uris, kindCalls = kindCalls, sizeCalls = sizeCalls))

        assertEquals(CaptureShareRejection.TOO_MANY_FILES, rejection)
        assertTrue("count gate must not resolve types", kindCalls.isEmpty())
        assertTrue("count gate must not query sizes", sizeCalls.isEmpty())
    }

    @Test
    fun `exactly thirty images pass the count gate`() {
        val uris = (0 until MAX_SHARE_IMPORT_FILES).map(::contentUri)
        val plan = plan(
            uris = uris,
            kinds = uris.associateWith { CaptureShareKind.IMAGE },
            sizes = uris.associateWith { 1_024L },
        )
        assertEquals(MAX_SHARE_IMPORT_FILES, (plan as CaptureSharePlan.Images).uris.size)
    }

    @Test
    fun `non content scheme is rejected before types are resolved`() {
        val kindCalls = mutableListOf<String>()
        val rejection = rejection(
            plan(
                uris = listOf("file:///sdcard/Download/question.jpg"),
                kinds = emptyMap(),
                kindCalls = kindCalls,
            ),
        )
        assertEquals(CaptureShareRejection.UNSUPPORTED_SCHEME, rejection)
        assertTrue(kindCalls.isEmpty())
    }

    @Test
    fun `content scheme is matched case insensitively`() {
        val uri = "CONTENT://com.example.provider/share/1"
        val plan = plan(
            uris = listOf(uri),
            kinds = mapOf(uri to CaptureShareKind.IMAGE),
            sizes = mapOf(uri to 10L),
        )
        assertTrue(plan is CaptureSharePlan.Images)
    }

    @Test
    fun `unsupported type is rejected`() {
        val uri = contentUri(1)
        assertEquals(
            CaptureShareRejection.UNSUPPORTED_TYPE,
            rejection(plan(listOf(uri), kinds = mapOf(uri to null))),
        )
    }

    @Test
    fun `mixed images and pdf are rejected`() {
        val image = contentUri(1)
        val pdf = contentUri(2)
        assertEquals(
            CaptureShareRejection.MIXED_TYPES,
            rejection(
                plan(
                    listOf(image, pdf),
                    kinds = mapOf(
                        image to CaptureShareKind.IMAGE,
                        pdf to CaptureShareKind.PDF,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `two pdfs are rejected`() {
        val first = contentUri(1)
        val second = contentUri(2)
        assertEquals(
            CaptureShareRejection.MULTIPLE_PDFS,
            rejection(
                plan(
                    listOf(first, second),
                    kinds = mapOf(
                        first to CaptureShareKind.PDF,
                        second to CaptureShareKind.PDF,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a single image is planned as a single image`() {
        val uri = contentUri(1)
        val plan = plan(
            listOf(uri),
            kinds = mapOf(uri to CaptureShareKind.IMAGE),
            sizes = mapOf(uri to 2_048L),
        )
        assertEquals(CaptureSharePlan.Images(listOf(uri)), plan)
    }

    @Test
    fun `a single pdf is planned as a pdf`() {
        val uri = contentUri(1)
        val plan = plan(
            listOf(uri),
            kinds = mapOf(uri to CaptureShareKind.PDF),
            sizes = mapOf(uri to 2_048L),
        )
        assertEquals(CaptureSharePlan.Pdf(uri), plan)
    }

    @Test
    fun `image at the byte limit passes and one byte above fails`() {
        val atLimit = contentUri(1)
        val above = contentUri(2)
        assertTrue(
            plan(
                listOf(atLimit),
                kinds = mapOf(atLimit to CaptureShareKind.IMAGE),
                sizes = mapOf(atLimit to MAX_SHARE_IMAGE_BYTES),
            ) is CaptureSharePlan.Images,
        )
        assertEquals(
            CaptureShareRejection.FILE_TOO_LARGE,
            rejection(
                plan(
                    listOf(above),
                    kinds = mapOf(above to CaptureShareKind.IMAGE),
                    sizes = mapOf(above to MAX_SHARE_IMAGE_BYTES + 1),
                ),
            ),
        )
    }

    @Test
    fun `pdf at the byte limit passes and one byte above fails`() {
        val atLimit = contentUri(1)
        val above = contentUri(2)
        assertTrue(
            plan(
                listOf(atLimit),
                kinds = mapOf(atLimit to CaptureShareKind.PDF),
                sizes = mapOf(atLimit to MAX_SHARE_PDF_BYTES),
            ) is CaptureSharePlan.Pdf,
        )
        assertEquals(
            CaptureShareRejection.FILE_TOO_LARGE,
            rejection(
                plan(
                    listOf(above),
                    kinds = mapOf(above to CaptureShareKind.PDF),
                    sizes = mapOf(above to MAX_SHARE_PDF_BYTES + 1),
                ),
            ),
        )
    }

    @Test
    fun `image batch above the total budget is rejected even when every file fits`() {
        val uris = (0 until 20).map(::contentUri)
        assertEquals(
            CaptureShareRejection.FILE_TOO_LARGE,
            rejection(
                plan(
                    uris,
                    kinds = uris.associateWith { CaptureShareKind.IMAGE },
                    // 20 × 15 MB = 300 MB > 256 MB，单张都在 20 MB 以内。
                    sizes = uris.associateWith { 15L * 1_024L * 1_024L },
                ),
            ),
        )
    }

    @Test
    fun `unknown declared size is accepted because the bounded copy is authoritative`() {
        val uri = contentUri(1)
        val plan = plan(
            listOf(uri),
            kinds = mapOf(uri to CaptureShareKind.IMAGE),
            sizes = emptyMap(),
        )
        assertTrue(plan is CaptureSharePlan.Images)
    }

    @Test
    fun `declared mime mapping covers images pdf and rejects everything else`() {
        assertEquals(CaptureShareKind.IMAGE, captureShareKindOf("image/jpeg"))
        assertEquals(CaptureShareKind.IMAGE, captureShareKindOf("image/*"))
        assertEquals(CaptureShareKind.IMAGE, captureShareKindOf("IMAGE/PNG"))
        assertEquals(CaptureShareKind.PDF, captureShareKindOf("application/pdf"))
        assertEquals(CaptureShareKind.PDF, captureShareKindOf("APPLICATION/PDF"))
        assertEquals(null, captureShareKindOf("text/plain"))
        assertEquals(null, captureShareKindOf("application/octet-stream"))
        assertEquals(null, captureShareKindOf(null))
    }

    @Test
    fun `share image budget stays identical to the capture cache budget`() {
        assertEquals(MAX_CAPTURE_BYTES, MAX_SHARE_IMAGE_BYTES)
    }

    @Test
    fun `failure messages name the explicit limits`() {
        assertTrue(captureShareRejectionMessage(CaptureShareRejection.TOO_MANY_FILES).contains("30"))
        assertTrue(
            captureShareRejectionMessage(CaptureShareRejection.FILE_TOO_LARGE)
                .contains("20 MB"),
        )
        assertTrue(
            captureShareRejectionMessage(CaptureShareRejection.NOTHING_SHARED)
                .contains("没有可导入"),
        )
    }

    @Test
    fun `only transient failures are retryable`() {
        assertTrue(captureShareRejectionIsRetryable(CaptureShareRejection.UNREADABLE))
        assertTrue(captureShareRejectionIsRetryable(CaptureShareRejection.IMPORT_FAILED))
        assertFalse(captureShareRejectionIsRetryable(CaptureShareRejection.TOO_MANY_FILES))
        assertFalse(captureShareRejectionIsRetryable(CaptureShareRejection.FILE_TOO_LARGE))
        assertFalse(captureShareRejectionIsRetryable(CaptureShareRejection.UNSUPPORTED_TYPE))
        assertFalse(captureShareRejectionIsRetryable(CaptureShareRejection.NOT_AN_IMAGE))
    }
}
