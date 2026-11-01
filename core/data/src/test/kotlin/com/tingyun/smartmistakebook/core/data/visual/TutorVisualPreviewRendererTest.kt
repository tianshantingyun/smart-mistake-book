package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * D-04 预览渲染器的 JVM 面（批次 39；R1 `docs/research/2026-11-01-preview-wiring.md`
 * §2.4/§2.5/§2.3 的**纯计算与解析拒绝**子集）。分配/绘制/压缩/persist 的设备半边在
 * `TutorVisualPreviewRendererInstrumentedTest`（N5/N6/N7 + 取消重抛）——JVM 无 Robolectric，
 * 本套件只覆盖**拒绝点先于任何位图/写入口**的路径（准入、解析、math 边界、定界策略）。
 *
 * 先红后绿：渲染器/纯函数在实现前落盘即红（符号缺失编译不过）；本环境不运行 Gradle，
 * 执行归门禁串行（结果登记 UNVERIFIED）。
 *
 * 判据映射：
 * - N5 的 JVM 半边：满幅 4096² 预览 → `LAYOUT_PIXEL_BUDGET` 前缀 + 零写入口（设备半边
 *   钉"快照照常提交"，JVM 半边钉"拒绝发生在任何 vault 写入口之前"）；
 * - N7 的口径半边：2048² 恰达不拒（纯函数）、+1 du² 即拒、通道甲退化语义（null 条目 =
 *   预览独判）；
 * - 恢复级复检：坏 DrawOps → `PAINTER_DRAW_COMMANDS`（内嵌原始 `DRAWOPS_*` 码），不信任
 *   "自称 validated"；
 * - math 盒边界：`PAINTER_LABEL_MATH`（预览继承 painter fail-closed，不做特例）；
 * - 定界策略：`min(1f, 1600/maxDu)`——密度无关（内容寻址幂等的根据）与 1600 上限值。
 */
class TutorVisualPreviewRendererTest {

    // ------------------------------------------------------------ 夹具

    private val validDocumentText =
        """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":"#ffffff","ops":[],"hitRegions":[],""" +
            """"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

    /** persist 写入口的观测器：拒绝路径必须零触碰（"拒绝先于任何写"的可观测面）。 */
    private class PersistRecorder {
        var writeAttempts = 0
        var recordedSourceType: String? = null
    }

    private fun persisterWith(
        recorder: PersistRecorder,
    ): TutorVisualPreviewAssetPersister = TutorVisualPreviewAssetPersister(
        persistPngBytes = { _, sourceType, _ ->
            recorder.writeAttempts += 1
            recorder.recordedSourceType = sourceType
            canonicalRecord(sourceType)
        },
        registerRow = { },
    )

    private fun renderer(): Pair<TutorVisualPreviewRendererImpl, PersistRecorder> {
        val recorder = PersistRecorder()
        return Pair(
            TutorVisualPreviewRendererFactory.create(persisterWith(recorder))
                as TutorVisualPreviewRendererImpl,
            recorder,
        )
    }

    private fun request(
        drawCommandsJson: String,
        viewportWidthDu: Int = 360,
        viewportHeightDu: Int = 240,
        labels: List<TutorVisualPreviewLabel> = emptyList(),
        measuredEntries: List<VisualMeasuredContent>? = null,
    ): TutorVisualPreviewRequest = TutorVisualPreviewRequest(
        rendered = VisualExecutionResult.Rendered(
            sceneJson = """{"scene":true}""",
            sceneSchemaVersion = 1,
            contentIds = labels.map { it.contentId }.toSet(),
            drawCommandsJson = drawCommandsJson,
        ),
        viewportWidthDu = viewportWidthDu,
        viewportHeightDu = viewportHeightDu,
        fontScaleMilli = 1_000,
        createdAtEpochMillis = 9_000,
        labels = labels,
        measuredEntries = measuredEntries,
    )

    private fun refusedOf(outcome: TutorVisualPreviewOutcome): TutorVisualPreviewOutcome.Refused =
        outcome as? TutorVisualPreviewOutcome.Refused
            ?: error("expected a refusal, got $outcome")

    // ------------------------------------------------------------ N5 的 JVM 半边：满幅预览准入拒绝

    @Test
    fun `a full-canvas preview is refused by the pixel budget before any write`() = runBlocking {
        val (renderer, recorder) = renderer()

        val outcome = renderer.renderPreview(
            // drawCommands 恒不解析（准入先于解析，R1 §2.1 步序）——任意非空文本即可。
            request(drawCommandsJson = "...", viewportWidthDu = 4096, viewportHeightDu = 4096),
        )

        val refused = refusedOf(outcome)
        assertTrue(
            "the refusal must carry the LAYOUT_PIXEL_BUDGET code, got: ${refused.code}",
            refused.code == VisualLayoutBudget.LAYOUT_PIXEL_BUDGET,
        )
        assertTrue(
            "the diagnostic must carry the same code prefix, got: ${refused.diagnostic}",
            refused.diagnostic.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET),
        )
        assertEquals("拒绝必须发生在任何 vault 写入口之前（零分配的可观测面）", 0, recorder.writeAttempts)
        assertTrue("诊断必须有界", refused.diagnostic.length <= 512)
    }

    // ------------------------------------------------------------ N7 的口径半边：恰达不拒 + 合计越界

    @Test
    fun `the exact 4 MP preview alone is admitted and one du pixel more is refused`() {
        // 恰达：2048² du = 4,194,304 = 上限（"≤"语义的纯函数钉；设备半边走真实渲染）。
        assertNull(previewAdmissionDiagnostic(2048, 2048, measuredEntries = null))
        // +1 du² 的合成度量条目 → 合计越界 → 有码拒绝（N7 的"合计"语义）。
        val diagnostic = previewAdmissionDiagnostic(2048, 2048, listOf(measured(1f, 1f)))
        assertNotNull(diagnostic)
        assertTrue(
            "the refusal must carry the LAYOUT_PIXEL_BUDGET code, got: $diagnostic",
            diagnostic!!.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET),
        )
        assertTrue(
            "the diagnostic must show the preview's 4,194,304 du² footprint, got: $diagnostic",
            diagnostic.contains("4194304"),
        )
        assertTrue(diagnostic.contains("exceeds"))
    }

    @Test
    fun `a full-canvas viewport alone exceeds the preview budget even without measured entries`() {
        // 4096² du = 16,777,216 = 4× 预算（R2 §4.2 参照数）：预览必须缩采样，准入先拒绝。
        val diagnostic = previewAdmissionDiagnostic(4096, 4096, measuredEntries = null)
        assertNotNull(diagnostic)
        assertTrue(diagnostic!!.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET))
    }

    @Test
    fun `null measured entries degrade to preview-only admission - channel A registered semantics`() {
        // 通道甲（R1 §2.5）：装配点当前传不了度量条目 → 合计退化为"预览独判"。
        // 同一视口在"预览独判"下准入、带上真实条目即拒——两者之差就是登记的合并语义待接线。
        assertNull(previewAdmissionDiagnostic(1024, 1024, measuredEntries = null))
        assertNotNull(
            previewAdmissionDiagnostic(
                1024,
                1024,
                // 2048×1024 = 2,097,152 du²；1,048,576 + 2,097,152 = 3,145,728 ≤ 4 MP 不够，
                // 再补一盒到合计 > 4,194,304。
                listOf(measured(2048f, 1024f), measured(2048f, 1024f)),
            ),
        )
    }

    // ------------------------------------------------------------ 恢复级复检 + math 边界（拒绝点先于分配）

    @Test
    fun `draw commands that fail the recovery recheck are refused with the painter code`() =
        runBlocking {
            val (renderer, recorder) = renderer()
            val refused = refusedOf(renderer.renderPreview(request(drawCommandsJson = "{}")))
            assertEquals(PAINTER_DRAW_COMMANDS, refused.code)
            assertTrue(
                "the diagnostic must embed the original DRAWOPS_* code, got: ${refused.diagnostic}",
                refused.diagnostic.contains("DRAWOPS_"),
            )
            assertEquals("解析拒绝发生在任何写入口之前", 0, recorder.writeAttempts)
        }

    @Test
    fun `a math box label refuses the whole preview with the fail-closed boundary code`() =
        runBlocking {
            val (renderer, recorder) = renderer()
            val refused = refusedOf(
                renderer.renderPreview(
                    request(
                        drawCommandsJson = validDocumentText,
                        labels = listOf(mathLabel()),
                    ),
                ),
            )
            assertEquals(PAINTER_LABEL_MATH, refused.code)
            assertEquals("math 边界拒绝发生在任何写入口之前", 0, recorder.writeAttempts)
        }

    // ------------------------------------------------------------ 定界策略（纯函数）

    @Test
    fun `the preview scale policy is density independent and bounded by the policy edge`() {
        // 小画布不放大（1x 封顶）；大画布缩采样到 1600 长边。
        assertEquals(1f, previewPxPerDu(360, 240))
        assertEquals(1600f / 4096f, previewPxPerDu(4096, 4096))
        assertEquals(1600f / 2048f, previewPxPerDu(2048, 2048))
        // 同输入同输出（幂等的确定性前提）。
        assertEquals(previewPxPerDu(4096, 4096), previewPxPerDu(4096, 4096))
    }

    @Test
    fun `the policy value stays within the hard cap of the byte gate`() {
        // 策略上限 2048 不可越线（R1 §2.4）：越线 = 每次满幅渲染都触发 BITMAP_BYTE_BUDGET
        // 降级（预览永久缺席）——previewPxPerDu 对常量本身 require 此上界，调大当场炸出。
        val edge = TutorVisualPreviewRendererFactory.PREVIEW_EDGE_PX
        assertTrue(
            "the preview edge policy must stay within the 2048 px hard cap, got $edge",
            edge in 1..TutorVisualPreviewRendererFactory.MAX_PREVIEW_EDGE_PX,
        )
        // 同源核对：1600²×4 = 10.24 MB 对 16 MiB 门留余量；2048²×4 = 恰达门。
        assertEquals(1_600, edge)
        assertEquals(
            16_777_216L,
            VisualLayoutBudget.bitmapBytes(2048, 2048, VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL),
        )
    }

    // ------------------------------------------------------------ 请求校验（类型层 fail-fast）

    @Test
    fun `request validation refuses out-of-budget viewports and duplicate label keys`() {
        assertIllegalArgument {
            request(drawCommandsJson = "{}", viewportWidthDu = 0)
        }
        assertIllegalArgument {
            request(
                drawCommandsJson = "{}",
                viewportHeightDu = VisualExecutionBudget.MAX_CANVAS_EDGE_DU + 1,
            )
        }
        assertIllegalArgument {
            request(
                drawCommandsJson = "{}",
                labels = listOf(textLabel("c1"), textLabel("c1")),
            )
        }
    }

    // ------------------------------------------------------------ 夹具助手

    private fun measured(widthDu: Float, heightDu: Float) = VisualMeasuredContent(
        contentId = "c-x",
        widthDu = widthDu,
        heightDu = heightDu,
        baselineDu = 0f,
        fontSizeSp = 16f,
        digest = "d",
    )

    private fun textLabel(contentId: String): TutorVisualPreviewLabel = TutorVisualPreviewLabel(
        contentId = contentId,
        payload = "标签 S=ab",
        artifact = VisualMeasuredLayoutArtifact(
            kind = VisualContentKind.TEXT,
            widthDu = 30f,
            heightDu = 24f,
            baselineDu = 4f,
            fontSizeSp = 16f,
            digest = "digest-preview",
            lines = listOf(VisualMeasuredLayoutArtifact.Line(0, 6, 30f)),
            degraded = false,
            inkBounds = VisualMeasuredLayoutArtifact.InkRect(0f, 0f, 30f, 24f),
        ),
    )

    private fun mathLabel(): TutorVisualPreviewLabel = TutorVisualPreviewLabel(
        contentId = "c-math",
        payload = "S=ab",
        artifact = VisualMeasuredLayoutArtifact(
            kind = VisualContentKind.MATH,
            widthDu = 10f,
            heightDu = 20f,
            baselineDu = 2f,
            fontSizeSp = 16f,
            digest = "digest-math",
            lines = null, // 纯 math 盒（未降级）——preview 继承 PAINTER_LABEL_MATH 边界。
            degraded = false,
            inkBounds = VisualMeasuredLayoutArtifact.InkRect(0f, 0f, 10f, 20f),
        ),
    )

    private fun canonicalRecord(sourceType: String): CanonicalSourceAssetRecord =
        CanonicalSourceAssetRecord(
            sourceAssetId = "asset-d04f3ed7038cd1d34e4b0d3f3d3f2f21",
            contentSha256 = "d04f3ed7038cd1d34e4b0d3f3d3f2f21".repeat(2),
            relativePath = "source-assets/d04f3ed7038cd1d34e4b0d3f3d3f2f21.png",
            mimeType = "image/png",
            byteSize = 7L,
            width = 2,
            height = 2,
            sourceType = sourceType,
            createdAtEpochMillis = 9_000L,
        )

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // 预期路径。
        }
    }
}
