package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 屏 / PDF 一致性门（批次 29 T4；R2
 * `docs/research/2026-10-28-screen-pdf-parity-gate.md` 的落码形态）。
 *
 * ## 定位：**原型门**，不是产品门
 *
 * - **不接产品门**：不进入 `docs/current/release-gates.md`，不改变任何产品行为。
 * - **不接 CI 门禁**：本类只在设备轨手工 `am instrument` 执行；没有 Gradle 任务依赖它。
 * - **不得宣传为发版门 / "PDF 可用"的证据**：阈值是 `calibrated-r1`（**标定第一轮**，
 *   依据 `build/device-track-batch30/step05-parity-logcat.txt:119-122` 的设备读数，
 *   不是第一原理推导）。**不可外推**到别的设备 / API / fontScale / AA 状态 / 字形；
 *   跨这些维度必须先重基线（R2 §3.3 `PARITY_ENV_DRIFT`）。**已知盲区（批次 31 设备更正）**：
 *   `centroidDeltaPx` 是屏↔PDF 的跨栅格欧氏距离，整块平移 t 读 |v + t|（v = 基线质心偏移），
 *   方向相关；1 px 位移由 bbox（基线 bboxDev=1 时 +1 → 2）与 `bestShift` 分支抓，**不由质心保证**
 *   （P06 +1 px 实测 1.2625；P07 反解应约 0.608 < 0.75）。因此不再宣称 "文本亚像素几何在
 *   (0,0.76) px 不可分辨" 这一标量窗口——质心对 1 px 位移的分辨力取决于方向。
 * - **相对门**：只证"PDF 路径相对屏路径没有引入新偏差"；屏侧本身的正确性由 painter 套件
 *   （设备 19 例 + JVM 10 例）负责。
 *
 * ## 对照方法（R2 §3.1）
 *
 * 同一份**已通过 [VisualDrawOpsValidator] 的** `VisualDrawOpsDocument`、同一个 label resolver：
 * 1. 屏：`VisualDrawOpsPainter.draw(doc, Canvas(bitmap), pxPerDu=4f, resolver)`；
 * 2. PDF：`VisualDrawOpsPdfPainter.write(doc, out, ptPerDu=1f, resolver)` → 真写盘 →
 *    `PdfRenderer` 以 `scale(4)` + `RENDER_MODE_FOR_PRINT` 栅格化到同一 192×128 像素网格。
 *
 * 门内**不复制**任何 painter 逻辑：两侧都走生产回放入口（R2 §3.1-3）。PDF 只比**栅格**，
 * 不比文件字节（PDF 含创建时间/ID；R2 §3.3）。
 *
 * ## 判据与视线
 *
 * - 前置门（R2 §3.3）：屏两次渲染逐位一致、同一 PDF 两次栅格逐位一致、同一文档两次独立
 *   构建的栅格逐位一致。任一失败 = 仪器不确定，没有判决。
 * - 度量（R2 §4）：掩膜 IoU、内部逐通道色差分位/超阈占比、边缘带均值、文本 bbox/质心/
 *   墨量比/剖面 TV、`[-3,3]²` 最优整像素位移。
 * - 分类（R2 §5）：F1 字体 / F2 抗锯齿 / F3 alpha 取整 / F4 几何/颜色/语义 / F5 不可归因。
 *   TEXT 类按 `calibrated-r1` 走"带硬几何护栏的 AA/字体归因优先"（见
 *   `VisualDrawOpsParityMetrics.rawTextVerdict` 的 KDoc）：P07 归因 AA/字体类（F2），
 *   但 1 px 位移 / 2 px bbox / 无墨仍然先判红。**TEXT 无隔离带（F5 恒 0）**。
 * - 负控（R2 §6.2 N01–N08 + `calibrated-r1` 新增 N09–N12）：每条都必须在同一套件里红；
 *   绿了 = 门失去分辨力（`PARITY_GATE_DESENSITIZED`），不是"更稳了"。
 *   N09（1 px 整块平移）是**门级**灵敏度负控，N12（4 px 整块平移）是质心放宽到 0.75 的
 *   读数锚（P07 实测 3.56、bboxDev=5）；1 px 位移的抓取路径是 bbox / `bestShift`，不是质心
 *   （见 N09 用例注释）。N10（整页换成别的文档）与 N11（绕包围盒中心旋转 180°，bbox/墨量
 *   逐位不变）证明"整体错画仍会红"。
 *
 * ## 不主张（R2 §0/§8）
 *
 * 不主张两侧逐像素相等（Skia 写 + PDFium 栅格是两套栅格器）；不主张 ≤1/255 的 alpha 取整
 * 方向可归因（由 `effectiveDrawOpsAlpha255` 纯测钉死）；不覆盖真导出链路（`core/export`，
 * KD-75 热区）；不覆盖跨设备/跨 API。
 */
@RunWith(AndroidJUnit4::class)
class VisualDrawOpsParityInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val viewport = VisualViewport(48, 32)
    private val parityDir = File(context.cacheDir, "parity")

    @Before
    fun announcePrototypeGate() {
        parityDir.mkdirs()
        Log.i(
            TAG,
            "PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no " +
                "thresholds=${VisualDrawOpsParityMetrics.THRESHOLD_VERSION} " +
                "calibration=first-round extrapolation=device+aa-state+integer-origin-only " +
                "blindWindow=text-centroid(direction-dependent)",
        )
        Log.i(TAG, "PARITY_ENV ${environmentFingerprint()}")
    }

    @After
    fun deletePdfScratchFiles() {
        // PDF 只是把字节喂给 PdfRenderer 的中间物；成功轮不留下它们。失败诊断 PNG 保留在
        // cacheDir/parity/diagnostics/（R2 §6.3：只在失败时写，设备证据轨可 pull）。
        parityDir.listFiles()
            ?.filter { it.isFile && it.extension == "pdf" }
            ?.forEach { it.delete() }
    }

    // ------------------------------------------------------------ 正方夹具

    @Test
    fun thePrototypeGateJudgesEveryPositiveFixture() {
        assertInstrumentDeterminism()
        var unattributed = 0
        for (fixture in FIXTURES) {
            val pair = renderPair(fixture)
            val judgment = judge(fixture, pair)
            if (judgment.decision.verdict.isRed) {
                writeDiagnostics(fixture, pair)
                fail(
                    "${fixture.id} must not be red: ${judgment.decision.verdict} " +
                        "reasons=${judgment.decision.reasons}",
                )
            }
            if (judgment.decision.verdict == ParityVerdict.F5_UNATTRIBUTED) {
                // `calibrated-r1`：TEXT 无隔离带，F5 必须为 0（旧带会把 bboxDev=2 + 质心 0.7
                // 静默放行，见 VisualDrawOpsParityMetrics.isolationBandOnly 的 KDoc）。
                if (fixture.cls == ParityFixtureClass.TEXT) {
                    writeDiagnostics(fixture, pair)
                    fail(
                        "${fixture.id}: TEXT must never be F5 under " +
                            "${VisualDrawOpsParityMetrics.THRESHOLD_VERSION} " +
                            "(the isolation band was removed; reasons=${judgment.decision.reasons})",
                    )
                }
                unattributed += 1
                Log.i(TAG, "PARITY_F5 fixture=${fixture.id} class=${fixture.cls} ${metricSummary(judgment.metrics)}")
            }
            if (fixture.cls.requiresShiftSensitivity) {
                val gain = judgment.metrics.shiftSensitivityGain
                if (!(gain >= VisualDrawOpsParityMetrics.MIN_SHIFT_SENSITIVITY_GAIN)) {
                    writeDiagnostics(fixture, pair)
                    fail(
                        "$PARITY_INSENSITIVE_FIXTURE ${fixture.id}: iou0=${judgment.metrics.iou} " +
                            "iou1=${judgment.metrics.neighborBestIou} gain=$gain " +
                            "(the fixture cannot prove geometry; fix the fixture, not the threshold)",
                    )
                }
            }
        }
        assertTrue(
            "$PARITY_SYSTEMATIC_DRIFT: $unattributed F5 entries exceed " +
                VisualDrawOpsParityMetrics.MAX_F5_PER_RUN,
            unattributed <= VisualDrawOpsParityMetrics.MAX_F5_PER_RUN,
        )
    }

    @Test
    fun backgroundNullIsObservedButNeverJudged() {
        // R2 §3.4-5 / §8-2：PDFium 对未绘制/透明区的处理未实测，故 background:null 只观察、
        // 不进判决面。两侧都先 eraseColor(白)；任何读数都只写日志。
        val document = validatedDocument(DOC_BG_NULL, emptySet())
        val screen = renderScreen(document, GRID_PX_PER_DU, NO_LABELS)
        val pdf = rasterizePdf(writePdfBytes(document, ptPerDu = 1f, labelContent = NO_LABELS), GRID_PX_PER_DU)
        val metrics = VisualDrawOpsParityMetrics.measure(screen, pdf, BACKDROP, band = null)
        Log.i(
            TAG,
            "PARITY_DIAGNOSTIC fixture=P-BGNULL judged=false ${metricSummary(metrics)} " +
                "(observation only; R2 §8-2)",
        )
        assertEquals(GRID_WIDTH, metrics.width)
        assertEquals(GRID_HEIGHT, metrics.height)
    }

    // ------------------------------------------------------------ 负控（全部必须红）

    @Test
    fun n01OnePixelRasterShiftIsCaught() {
        // R2 §6.2 N01：把 PDF 栅格整体右移 1 px。矩形宽 24/64/32 px → 位移后 IoU 0.920/0.969/
        // 0.939（R2 §4.4 的手算公式 (w−1)/(w+1)；P03 的 R2 行号来自 d=2，本门 d=4 时宽度翻倍）。
        for (id in listOf("P01", "P02", "P03", "P05")) {
            val fixture = fixture(id)
            val pair = renderPair(fixture)
            val shifted = pair.copy(pdf = VisualDrawOpsParityMetrics.shift(pair.pdf, dx = 1, dy = 0))
            val judgment = assertRed(fixture, shifted, "N01-$id")
            if (judgment.metrics.bestShiftX != 1 || judgment.metrics.bestShiftY != 0) {
                fail(
                    "N01-$id: expected bestShift=(1,0), got " +
                        "(${judgment.metrics.bestShiftX},${judgment.metrics.bestShiftY})",
                )
            }
        }
    }

    @Test
    fun n02SingleChannelShiftIsCaught() {
        // R2 §6.2 N02：PDF 栅格的蓝通道 +16（P01 的红色填充蓝分量为 0，白底已被夹到 255）。
        val fixture = fixture("P01")
        val pair = renderPair(fixture)
        val boosted = pair.copy(
            pdf = VisualDrawOpsParityMetrics.addToChannel(pair.pdf, channel = ParityChannel.BLUE, delta = 16),
        )
        val judgment = assertRed(fixture, boosted, "N02")
        assertTrue(
            "N02: interiorMax=${judgment.metrics.interiorMax} should expose the +16 channel shift",
            judgment.metrics.interiorMax >= 16,
        )
    }

    @Test
    fun n03DroppedClipIsCaught() {
        // R2 §6.2 N03：PDF 输入删掉 clip op。P03 的墨从 32×32 px 涨到 32×64 px（d=4），
        // IoU 0.25 —— 几何语义错。
        val fixture = fixture("P03")
        val base = documentOf(fixture)
        val mutated = base.copy(ops = base.ops.filterNot { it is VisualDrawOp.Clip })
        assertRed(fixture, renderPair(fixture, pdfDocument = mutated), controlId = "N03")
    }

    @Test
    fun n04LabelShiftIsCaught() {
        // R2 §6.2 N04：PDF 输入的 label x +1 du（+4 px）；bbox 左缘偏离 4 px > 1 px。
        val fixture = fixture("P06")
        val base = documentOf(fixture)
        val mutated = base.copy(
            ops = base.ops.map { op ->
                if (op is VisualDrawOp.Label) op.copy(x = op.x + 1.0) else op
            },
        )
        assertRed(fixture, renderPair(fixture, pdfDocument = mutated), controlId = "N04")
    }

    @Test
    fun n05GlyphSubstitutionIsCaught() {
        // R2 §6.2 N05：同一内容 DTO 下 PDF 侧 payload "H" → "M"。分类允许 F1_FONT 或 F4，
        // 但必须红（bbox 带宽与剖面二选一暴露）。
        val fixture = fixture("P06")
        val pair = renderPair(
            fixture,
            pdfResolver = resolverFor(fixture, payloadOverride = "M"),
        )
        assertRed(fixture, pair, "N05")
    }

    @Test
    fun n06AlphaMagnitudeChangeIsCaught() {
        // R2 §6.2 N06：PDF 输入 alpha 0.5 → 0.6。白底红块内部 Δ≈26 → F4_COLOR。
        val fixture = fixture("P02")
        val base = documentOf(fixture)
        val mutated = base.copy(
            ops = base.ops.map { op ->
                if (op is VisualDrawOp.DrawPath) op.copy(alpha = 0.6) else op
            },
        )
        assertRed(fixture, renderPair(fixture, pdfDocument = mutated), controlId = "N06")
    }

    @Test
    fun n07DroppedOpsAreCaught() {
        // R2 §6.2 N07：PDF 输入 ops 清空（背景保留）。PDF 掩膜为空 → IoU=0。
        val fixture = fixture("P01")
        val base = documentOf(fixture)
        val mutated = base.copy(ops = emptyList())
        assertRed(fixture, renderPair(fixture, pdfDocument = mutated), controlId = "N07")
    }

    @Test
    fun n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5() {
        // 复核第 1 轮 R8：整块填充的**系统性**偏色不得经隔离带降级成 F5（F5 不红、只计数）。
        // 合成 32×32 白底 + 16×16 纯红块；PDF 侧整块绿通道 +4（逐通道最大差 = 4 > 容差 2，
        // 远超 VECTOR 类的 interiorFracOver2Limit=0.002）。IoU=1、无位移。
        // 旧逻辑：interiorMax∈(2,8] → in-band → F5（套件只数 F5 个数，整体错色可放行）；
        // 新逻辑：占比 ≈1 不满足隔离带 → 保持 F4_COLOR（isRed，必须红）。
        val size = 32
        val backdrop = 0xFFFFFFFF.toInt()
        val red = 0xFFFF0000.toInt()
        val pixels = IntArray(size * size) { backdrop }
        for (y in 8 until 24) {
            for (x in 8 until 24) pixels[y * size + x] = red
        }
        val screen = ParityRaster(size, size, pixels)
        val pdf = VisualDrawOpsParityMetrics.addToChannel(screen, ParityChannel.GREEN, delta = 4)
        val metrics = VisualDrawOpsParityMetrics.measure(screen, pdf, backdrop, band = null)
        assertEquals(1.0, metrics.iou, 1e-9)
        assertTrue("interiorMax=${metrics.interiorMax}", metrics.interiorMax >= 4)
        assertTrue(
            "a whole-block cast must have a high over-tolerance fraction, got ${metrics.interiorFracOver2}",
            metrics.interiorFracOver2 > 0.9,
        )
        val decision = VisualDrawOpsParityMetrics.classify(ParityFixtureClass.VECTOR, metrics)
        assertEquals(ParityVerdict.F4_COLOR, decision.verdict)
        assertTrue(decision.verdict.isRed)
    }

    // ------------------------------------------------------------ 标定负控（calibrated-r1）

    @Test
    fun n09OnePixelTextShiftIsCaught() {
        // `calibrated-r1` 的**门级**负控（R2 §2.3 N09）：质心阈值 0.5 → 0.75 后，
        // 1 px 整块平移必须仍然**判红**。
        //
        // 原"整块平移 t 使质心恰好变化 |t| ⇒ centroid=1.000"的等式已撤（批次 31 设备否证）：
        // `centroidDeltaPx` 是**屏栅格与 PDF 栅格之间的质心欧氏距离**
        // （`VisualDrawOpsParityMetrics.measureText`），不是同一侧平移前后的质心差。
        // 把 PDF 侧平移 t 后读数是 |v + t|（v = 基线屏↔PDF 质心偏移），**方向相关**：
        // - P06 设备实测 |v|=0.32 → +1 px 读 1.2624767812258337（`step08-parity-logcat.txt:125`）；
        // - P07 由同轮 N12 的 +4 读数 3.56014654994809 反解 v=(-0.4492,0.2582)，+1 px 应读
        //   ≈0.608 < 0.75 —— 即**质心不是 1 px 的抓取路径**。N09-P07 因 P06 断言中止未执行。
        //
        // 设备上真正抓住 1 px 的是几何护栏：P06 的理由为 `text bboxDev=2px > 1px`
        // （`step08-parity-logcat.txt:126`；基线 bboxDev=1），同轮 `bestShift=(1,0)`、
        // gain=0.8846−0.6897≈0.195 > 0.02（方向无关分支同样成立）。
        // 因此本用例只断言"1 px 平移必须红、且归类 F4_GEOMETRY"，质心读数只进证据日志；
        // 不再把未获设备证明的 `centroid=1.000` 当作许可证。
        for (id in listOf("P06", "P07")) {
            val fixture = fixture(id)
            val pair = renderPair(fixture)
            val shifted = pair.copy(pdf = VisualDrawOpsParityMetrics.shift(pair.pdf, dx = 1, dy = 0))
            val judgment = assertRed(fixture, shifted, "N09-$id")
            val text = judgment.metrics.text
                ?: error("N09-$id: TEXT fixture must carry text metrics")
            assertEquals(
                "N09-$id: a whole-block 1 px shift must be caught by a geometry guard",
                ParityVerdict.F4_GEOMETRY,
                judgment.decision.verdict,
            )
            Log.i(
                TAG,
                "PARITY_CONTROL_DETAIL N09-$id centroid=${text.centroidDeltaPx} " +
                    "bboxDev=${text.bboxDeviationPx} mass=${text.massRatio} " +
                    "bestShift=(${judgment.metrics.bestShiftX},${judgment.metrics.bestShiftY}) " +
                    "verdict=${judgment.decision.verdict}",
            )
        }
    }

    @Test
    fun n10WholePageMisdrawIsCaught() {
        // R2 §2.3 N10：**整体错画**（整页换成完全不同的内容）必须红，不得被文本指标平均掉。
        // 屏侧 = 文本 "H"；PDF 侧 = P01 的矩形文档（矩形 x∈[16,40] px，文本带 x∈[52,140] 内无墨）
        // ⇒ 带内 pdfBox=null、bboxDev=Int.MAX_VALUE（手算，与设备无关）。
        val fixture = fixture("P06")
        val misdrawn = renderPair(fixture, pdfDocument = validatedDocument(DOC_FILL_RECT, emptySet()))
        val judgment = assertRed(fixture, misdrawn, "N10")
        val text = judgment.metrics.text
            ?: error("N10: TEXT fixture must carry text metrics")
        assertTrue("N10: the screen band must contain ink", text.screenBox != null)
        assertTrue("N10: the pdf band must be empty, got ${text.pdfBox}", text.pdfBox == null)
        assertEquals(Int.MAX_VALUE, text.bboxDeviationPx)
        assertEquals(ParityVerdict.F4_GEOMETRY, judgment.decision.verdict)
    }

    @Test
    fun n11ShapeWrongButPositionAndMassRightIsCaught() {
        // R2 §2.3 N11：**位置与墨量都对、只有形状错**，仍必须红——证明质心/墨量不是唯一防线。
        // 构造：把 PDF 栅格带内墨迹的包围盒绕自身中心旋转 180°（整数置换，不插值）⇒
        // bbox 与带内墨量**逐位不变**（两条防线都抓不住），出口只剩质心 / 剖面 TV / IoU。
        // 用 P07（CJK "标"）而不是 P06（"H" 近似上下对称，180° 后几乎不变，构不成对抗样本）。
        val fixture = fixture("P07")
        val pair = renderPair(fixture)
        val base = judge(fixture, pair)
        val rotated = pair.copy(
            pdf = VisualDrawOpsParityMetrics.rotate180WithinBand(pair.pdf, BACKDROP, TEXT_BAND),
        )
        val judgment = assertRed(fixture, rotated, "N11")
        val before = base.metrics.text ?: error("N11: base TEXT metrics missing")
        val after = judgment.metrics.text ?: error("N11: rotated TEXT metrics missing")
        assertEquals(
            "N11: the 180° rotation must preserve bbox (bboxDev ${before.bboxDeviationPx} -> ${after.bboxDeviationPx})",
            before.bboxDeviationPx,
            after.bboxDeviationPx,
        )
        assertEquals(
            "N11: the 180° rotation must preserve band ink mass (mass ${before.massRatio} -> ${after.massRatio})",
            before.massRatio,
            after.massRatio,
            1e-9,
        )
        Log.i(
            TAG,
            "PARITY_CONTROL_DETAIL N11 centroid=${after.centroidDeltaPx} " +
                "tvColumn=${after.columnTv} tvRow=${after.rowTv} iou=${judgment.metrics.iou} " +
                "verdict=${judgment.decision.verdict}",
        )
    }

    @Test
    fun n12WholeBlockShiftBeyondToleranceIsCaught() {
        // 任务 7-三 的自证："放宽质心到 0.75 之后，**整体错画**仍然会红"。
        // 构造：PDF 栅格整块 +4 px（与 N04 的 +1 du 同量级）。读数不是 4.000：质心是跨栅格
        // 欧氏距离，P07 基线 v=(-0.4492,0.2582)，+4 px 应读 |v+(4,0)|≈3.560——
        // 设备实测 3.56014654994809、bboxDev=5（`step08-parity-logcat.txt:185-186`）。
        // bboxDev 只记录不断言：字形贴住统计带边缘时会被裁切，读数不再等于平移量。
        val fixture = fixture("P07")
        val pair = renderPair(fixture)
        val shifted = pair.copy(pdf = VisualDrawOpsParityMetrics.shift(pair.pdf, dx = 4, dy = 0))
        val judgment = assertRed(fixture, shifted, "N12")
        val text = judgment.metrics.text
            ?: error("N12: TEXT fixture must carry text metrics")
        assertTrue(
            "N12: a 4 px whole-block shift must exceed the centroid threshold " +
                "${VisualDrawOpsParityMetrics.TEXT_THRESHOLDS.maxCentroidDeltaPx}, got ${text.centroidDeltaPx}",
            text.centroidDeltaPx > VisualDrawOpsParityMetrics.TEXT_THRESHOLDS.maxCentroidDeltaPx,
        )
        Log.i(
            TAG,
            "PARITY_CONTROL_DETAIL N12 centroid=${text.centroidDeltaPx} " +
                "bboxDev=${text.bboxDeviationPx} mass=${text.massRatio} " +
                "verdict=${judgment.decision.verdict}",
        )
    }

    // ------------------------------------------------------------ 度量自证（R2 §6.4）

    @Test
    fun parityMetricsUseUnionForIouAndStayInsideErosionBounds() {
        // 并集做分母：交集 1、并集 2 → 0.5。若误用交集当分母，本断言红（N01 也会失去分辨力）。
        val screen = ParityRaster(2, 1, intArrayOf(BLACK, WHITE))
        val pdf = ParityRaster(2, 1, intArrayOf(BLACK, BLACK))
        val unionMetrics = VisualDrawOpsParityMetrics.measure(screen, pdf, BACKDROP, band = null)
        assertEquals(0.5, unionMetrics.iou, 1e-9)

        // 腐蚀不越界：1×1 的墨腐蚀后为空；3×3 的墨腐蚀后只剩中心。
        val single = ParityRaster(1, 1, intArrayOf(BLACK))
        assertEquals(0, VisualDrawOpsParityMetrics.measure(single, single, BACKDROP, band = null).interiorPx)
        val three = ParityRaster(3, 3, IntArray(9) { BLACK })
        val threeMetrics = VisualDrawOpsParityMetrics.measure(three, three, BACKDROP, band = null)
        assertEquals(1, threeMetrics.interiorPx)
        assertEquals(0, threeMetrics.interiorMax)
    }

    // ------------------------------------------------------------ 前置门（R2 §3.3）

    private fun assertInstrumentDeterminism() {
        val fixture = fixture("P01")
        val document = documentOf(fixture)
        val screenFirst = renderScreen(document, GRID_PX_PER_DU, NO_LABELS)
        val screenSecond = renderScreen(document, GRID_PX_PER_DU, NO_LABELS)
        if (!Arrays.equals(screenFirst.pixels, screenSecond.pixels)) {
            fail("$PARITY_SCREEN_NONDETERMINISTIC: the screen painter produced two different rasters")
        }

        val pdfFirst = writePdfBytes(document, ptPerDu = 1f, labelContent = NO_LABELS)
        val rasterFirst = rasterizePdf(pdfFirst, GRID_PX_PER_DU)
        val rasterAgain = rasterizePdf(pdfFirst, GRID_PX_PER_DU)
        if (!Arrays.equals(rasterFirst.pixels, rasterAgain.pixels)) {
            fail("$PARITY_PDF_RASTER_NONDETERMINISTIC: PdfRenderer produced two different rasters for one PDF")
        }

        val pdfSecond = writePdfBytes(document, ptPerDu = 1f, labelContent = NO_LABELS)
        val rasterSecond = rasterizePdf(pdfSecond, GRID_PX_PER_DU)
        if (!Arrays.equals(rasterFirst.pixels, rasterSecond.pixels)) {
            fail(
                "$PARITY_PDF_REBUILD_NONDETERMINISTIC: two independent PDF builds from one document " +
                    "rasterized differently (PDF bytes themselves are not compared)",
            )
        }
    }

    // ------------------------------------------------------------ 渲染管线

    private fun renderPair(
        fixture: ParityFixture,
        pdfDocument: VisualDrawOpsDocument? = null,
        screenResolver: (String) -> VisualLabelContent? = resolverFor(fixture),
        pdfResolver: (String) -> VisualLabelContent? = screenResolver,
    ): ParityPair {
        // R2 §3.1-1：只 validate 一次，两侧默认消费**同一个** VisualDrawOpsDocument 实例。
        // 负控显式传入 mutated 文档时，屏侧仍用原文档——那是"改 PDF 输入"的对照，不是两次校验。
        val document = documentOf(fixture)
        val screen = renderScreen(document, GRID_PX_PER_DU, screenResolver)
        val scale = GRID_PX_PER_DU / fixture.ptPerDu
        val pdfInput = pdfDocument ?: document
        val pdf = rasterizePdf(writePdfBytes(pdfInput, fixture.ptPerDu, pdfResolver), scale)
        return ParityPair(screen, pdf)
    }

    private fun renderScreen(
        document: VisualDrawOpsDocument,
        pxPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ): ParityRaster {
        val width = (document.viewBox.w * pxPerDu).roundToInt()
        val height = (document.viewBox.h * pxPerDu).roundToInt()
        require(width == GRID_WIDTH && height == GRID_HEIGHT) {
            "screen grid must be ${GRID_WIDTH}x$GRID_HEIGHT, got ${width}x$height"
        }
        val bitmap = Bitmap.createBitmap(GRID_WIDTH, GRID_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(BACKDROP)
            // 生产回放入口；门内不复制 op 遍历（R2 §3.1-3）。
            VisualDrawOpsPainter().draw(document, Canvas(bitmap), pxPerDu, labelContent)
            return ParityRaster(GRID_WIDTH, GRID_HEIGHT, pixelsOf(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    private fun writePdfBytes(
        document: VisualDrawOpsDocument,
        ptPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        // 生产 PDF 回放入口（T3）；与屏侧共享同一状态机。
        VisualDrawOpsPdfPainter().write(document, bytes, ptPerDu, labelContent)
        return bytes.toByteArray()
    }

    /**
     * 真写盘 + `PdfRenderer` 真读回（R2 §3.1-4：`PdfRenderer` 只吃可 seek 的 fd）。
     * `RENDER_MODE_FOR_PRINT` 是**测量仪器的选择**（R2 §0-3）：display 模式会追加
     * `FPDF_LCD_TEXT` 子像素彩边，把仪器差异当成 PDF 缺陷。
     */
    private fun rasterizePdf(bytes: ByteArray, scale: Float): ParityRaster {
        val file = File.createTempFile("parity-", ".pdf", parityDir)
        try {
            FileOutputStream(file).use { it.write(bytes) }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(GRID_WIDTH, GRID_HEIGHT, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(BACKDROP)
                            val transform = Matrix().apply { setScale(scale, scale) }
                            page.render(
                                bitmap,
                                null,
                                transform,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT,
                            )
                            return ParityRaster(GRID_WIDTH, GRID_HEIGHT, pixelsOf(bitmap))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    // ------------------------------------------------------------ 判决 / 报告 / 诊断

    private fun judge(fixture: ParityFixture, pair: ParityPair): Judgment {
        val metrics = VisualDrawOpsParityMetrics.measure(pair.screen, pair.pdf, BACKDROP, fixture.band)
        val decision = VisualDrawOpsParityMetrics.classify(fixture.cls, metrics)
        logReport(fixture, metrics, decision)
        return Judgment(metrics, decision)
    }

    private fun assertRed(fixture: ParityFixture, pair: ParityPair, controlId: String): Judgment {
        val judgment = judge(fixture, pair)
        if (!judgment.decision.verdict.isRed) {
            writeDiagnostics(fixture, pair)
            fail(
                "$PARITY_GATE_DESENSITIZED $controlId: expected a red verdict, got " +
                    "${judgment.decision.verdict} reasons=${judgment.decision.reasons}",
            )
        }
        Log.i(
            TAG,
            "PARITY_CONTROL $controlId fixture=${fixture.id} verdict=${judgment.decision.verdict} " +
                "red=true reasons=${judgment.decision.reasons}",
        )
        return judgment
    }

    private fun logReport(fixture: ParityFixture, metrics: ParityMetrics, decision: ParityDecision) {
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "PARITY fixture=%s class=%s iou=%.4f bestShift=(%d,%d) bestIou=%.4f neighborIou=%.4f " +
                    "maskS=%d maskP=%d union=%d alphaMismatch=%d",
                fixture.id,
                fixture.cls,
                metrics.iou,
                metrics.bestShiftX,
                metrics.bestShiftY,
                metrics.bestShiftIou,
                metrics.neighborBestIou,
                metrics.screenMaskPx,
                metrics.pdfMaskPx,
                metrics.unionMaskPx,
                metrics.alphaMismatchCount,
            ),
        )
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "PARITY fixture=%s interiorMax=%d p50=%d p95=%d p99=%d frac2=%.4f edgeMean=%.2f " +
                    "sameSign=%.2f text=%s verdict=%s thr=%s",
                fixture.id,
                metrics.interiorMax,
                metrics.interiorP50,
                metrics.interiorP95,
                metrics.interiorP99,
                metrics.interiorFracOver2,
                metrics.edgeMean,
                metrics.sameSignDominantRatio,
                textSummary(metrics.text),
                decision.verdict,
                thresholdSummary(fixture.cls),
            ),
        )
        if (metrics.firstOverX >= 0) {
            Log.i(
                TAG,
                "PARITY fixture=${fixture.id} firstOverPx=(${metrics.firstOverX},${metrics.firstOverY}) " +
                    "S=0x${Integer.toHexString(metrics.firstOverScreenArgb)} " +
                    "P=0x${Integer.toHexString(metrics.firstOverPdfArgb)}",
            )
        }
    }

    private fun metricSummary(metrics: ParityMetrics): String = String.format(
        Locale.US,
        "iou=%.4f bestShift=(%d,%d) bestIou=%.4f interiorMax=%d frac2=%.4f edgeMean=%.2f " +
            "sameSign=%.2f text=%s",
        metrics.iou,
        metrics.bestShiftX,
        metrics.bestShiftY,
        metrics.bestShiftIou,
        metrics.interiorMax,
        metrics.interiorFracOver2,
        metrics.edgeMean,
        metrics.sameSignDominantRatio,
        textSummary(metrics.text),
    )

    private fun textSummary(text: TextParityMetrics?): String = if (text == null) {
        "-"
    } else {
        String.format(
            Locale.US,
            "bboxDev=%d centroid=%.2f mass=%.3f tvColumn=%.3f tvRow=%.3f",
            text.bboxDeviationPx,
            text.centroidDeltaPx,
            text.massRatio,
            text.columnTv,
            text.rowTv,
        )
    }

    private fun thresholdSummary(fixtureClass: ParityFixtureClass): String = when (fixtureClass) {
        ParityFixtureClass.VECTOR ->
            "iou>=0.990,interMax<=2,frac2<=0.002,shift=(0,0),dIoU>=0.020"
        ParityFixtureClass.ALPHA ->
            "iou>=0.990,interMax<=2,frac2<=0.005,shift=(0,0),dIoU>=0.020"
        ParityFixtureClass.GEOMETRY ->
            "iou>=0.990,interMax<=2,frac2<=0.002,shift=(0,0),dIoU>=0.020"
        ParityFixtureClass.DASH ->
            "iou>=0.970,interMax<=2,frac2<=0.005,shift=(0,0),dIoU>=0.020"
        ParityFixtureClass.GEOMETRY_NON_INTEGRAL ->
            "iou=reported-only,interMax<=2,frac2<=0.005,shift=(0,0)"
        ParityFixtureClass.TEXT -> VisualDrawOpsParityMetrics.TEXT_THRESHOLDS.summary()
    }

    /** 只在失败时写 PNG（R2 §6.3）；判决永远读 `IntArray`，PNG 只是诊断附件。 */
    private fun writeDiagnostics(fixture: ParityFixture, pair: ParityPair) {
        runCatching {
            val directory = File(parityDir, "diagnostics").apply { mkdirs() }
            writePng(File(directory, "${fixture.id}.screen.png"), pair.screen)
            writePng(File(directory, "${fixture.id}.pdf.png"), pair.pdf)
            writePng(File(directory, "${fixture.id}.diff.png"), VisualDrawOpsParityMetrics.diffRaster(pair.screen, pair.pdf))
            Log.e(TAG, "PARITY_DIAGNOSTIC_WRITTEN fixture=${fixture.id} directory=${directory.absolutePath}")
        }.onFailure { error ->
            Log.e(TAG, "PARITY_DIAGNOSTIC_WRITE_FAILED fixture=${fixture.id}: ${error.message}")
        }
    }

    private fun writePng(file: File, raster: ParityRaster) {
        val bitmap = Bitmap.createBitmap(raster.width, raster.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(raster.pixels, 0, raster.width, 0, 0, raster.width, raster.height)
            FileOutputStream(file).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("PNG compression failed for ${file.name}")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    // ------------------------------------------------------------ 环境 / 夹具

    /**
     * 环境指纹（R2 §3.3）：设备/API/density/fontScale + **真实回放 Paint 的 flags**。
     *
     * 本批**没有历史基线**，因此这里只能记录、无法计算漂移；`PARITY_ENV_DRIFT` 的口径
     * （"记录并重基线"）留给证据轨：把同一台设备的这一行落盘，后续批次与它比对。
     * flags 读的是 `VisualDrawOpsPainter.textPaintFlagsForTest()`（实际用于绘制的那只 Paint），
     * 与 T5 的度量环境指纹同源（KD-78 ④）。
     */
    private fun environmentFingerprint(): String {
        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        // flags 从**真实回放 Paint**读取（`VisualDrawOpsPainter.textPaintFlagsForTest`），
        // 与 T5 的度量环境指纹同源口径（KD-78 ④ / R2 §3.3）。
        val replayTextFlags = VisualDrawOpsPainter().textPaintFlagsForTest()
        return "fingerprint=${Build.FINGERPRINT}|sdk=${Build.VERSION.SDK_INT}" +
            "|abi=${Build.SUPPORTED_ABIS.joinToString("+")}" +
            "|densityDpi=${displayMetrics.densityDpi}|fontScale=${configuration.fontScale}" +
            "|replayTextPaintFlags=$replayTextFlags|replayPathPaintFlags=${Paint.ANTI_ALIAS_FLAG}" +
            "|pxPerDu=$GRID_PX_PER_DU|ptPerDu=1.0"
    }

    private fun fixture(id: String): ParityFixture =
        FIXTURES.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown fixture $id")

    private fun documentOf(fixture: ParityFixture): VisualDrawOpsDocument = validatedDocument(
        fixture.json,
        if (fixture.labelPayload == null) emptySet() else setOf(LABEL_CONTENT_ID),
    )

    private fun validatedDocument(json: String, contentIds: Set<String>): VisualDrawOpsDocument {
        val validation = VisualDrawOpsValidator.validate(json, contentIds, viewport)
        if (validation !is VisualDrawOpsValidation.Valid) {
            val invalid = validation as VisualDrawOpsValidation.Invalid
            fail("fixture must validate: ${invalid.code}: ${invalid.message}")
        }
        // fail 返回 Unit（非 Nothing），sealed 两分支需显式窄化。
        return (validation as VisualDrawOpsValidation.Valid).document
    }

    private fun resolverFor(
        fixture: ParityFixture,
        payloadOverride: String? = null,
    ): (String) -> VisualLabelContent? {
        val payload = payloadOverride ?: fixture.labelPayload ?: return NO_LABELS
        // 一个 resolver 实例、一份 DTO 实例：两侧拿到的是同一个不可变对象（R2 §3.1-2）。
        val content = VisualLabelContent.Text(
            payload = payload,
            sizeSp = 16f,
            fontScale = 1f,
            heightDu = 12f,
            lines = listOf(VisualLabelLine(0, payload.length)),
        )
        return { contentId -> if (contentId == LABEL_CONTENT_ID) content else null }
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also { pixels ->
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private data class ParityFixture(
        val id: String,
        val cls: ParityFixtureClass,
        val json: String,
        val ptPerDu: Float = 1f,
        val labelPayload: String? = null,
        val band: PixelBand? = null,
    )

    private data class ParityPair(val screen: ParityRaster, val pdf: ParityRaster)

    private data class Judgment(val metrics: ParityMetrics, val decision: ParityDecision)

    private companion object {
        const val TAG = "VisualDrawOpsParity"
        const val LABEL_CONTENT_ID = "c1"
        const val GRID_WIDTH = 192
        const val GRID_HEIGHT = 128
        const val GRID_PX_PER_DU = 4f
        val BACKDROP = 0xFFFFFFFF.toInt()
        val BLACK = 0xFF000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()

        val NO_LABELS: (String) -> VisualLabelContent? = { null }

        /**
         * R2 §3.2 / §6.1：viewBox 48×32 du、d=4 px/du、ptPerDu=1（P08 = 1.5）。
         * 文本带比 R2 的 [56,120]×[24,84] 略宽（52..140 / 16..88）：64 px 字号的 CJK
         * 与上升部会被原带裁掉，而带内裁切会掩盖"字形轮廓不同"的差异（夹具调整，阈值不变）。
         */
        val TEXT_BAND = PixelBand(left = 52, top = 16, rightInclusive = 140, bottomInclusive = 88)

        /** R2 §6.1 P01：矩形 (4,2)-(10,8) → 设备 (16,8)-(40,32)，宽 24 px。 */
        val DOC_FILL_RECT = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,2],["L",10,2],["L",10,8],["L",4,8],["Z"]],""" +
            """"fill":"#ff0000","stroke":null,"alpha":1}],"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /** R2 §6.1 P02：矩形 (4,4)-(20,12)，alpha 0.5，宽 64 px。 */
        val DOC_HALF_ALPHA = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],""" +
            """"fill":"#ff0000","stroke":null,"alpha":0.5}],"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /** R2 §6.1 P03（同 `DOC_NESTED_CLIP`，白底）：交 = du (4,0)-(12,8) → 设备 32×32 px。 */
        val DOC_NESTED_CLIP = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"save"},{"op":"concat","m":[2,0,0,2,0,0]},{"op":"save"},""" +
            """{"op":"clip","path":[["M",4,0],["L",12,0],["L",12,8],["L",4,8],["Z"]],"rule":"nonzero"},""" +
            """{"op":"concat","m":[1,0,0,1,2,0]},""" +
            """{"op":"path","path":[["M",0,0],["L",8,0],["L",8,8],["L",0,8],["Z"]],"fill":"#00aa00","stroke":null,"alpha":1},""" +
            """{"op":"restore"},{"op":"restore"}],"hitRegions":[],"stats":{"nodes":2,"pathCommands":10,"labels":0,"ops":8}}"""

        /** R2 §6.1 P04：切变 `[1,1,0,1,0,0]`，局部矩形 (2,1)-(6,5)，面积 16 du²。 */
        val DOC_SHEAR = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"save"},{"op":"concat","m":[1,1,0,1,0,0]},""" +
            """{"op":"path","path":[["M",2,1],["L",6,1],["L",6,5],["L",2,5],["Z"]],"fill":"#ff0000","stroke":null,"alpha":1},""" +
            """{"op":"restore"}],"hitRegions":[],"stats":{"nodes":1,"pathCommands":5,"labels":0,"ops":4}}"""

        /** R2 §6.1 P05：横线 du y=28、x 4..44，stroke 1 du，dash [2,2]（8 px on / 8 px off）。 */
        val DOC_DASH = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,28],["L",44,28]],""" +
            """"fill":"#ff0000","stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[2,2]},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":2,"labels":0,"ops":1}}"""

        /** R2 §6.1 P06：单行 label "H"，(x,y)=(15,20)，sizeSp 16，fontScale 1，heightDu 12。 */
        val DOC_LABEL_H = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"label","contentId":"c1","x":15,"y":20,"rotationDeg":0,"hAlign":"left","vAlign":"top","color":"#26354a","alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":1,"ops":1}}"""

        /** R2 §6.1 P07：CJK label "标"（子集/Type3 回退面）。 */
        val DOC_LABEL_CJK = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"label","contentId":"c1","x":15,"y":20,"rotationDeg":0,"hAlign":"left","vAlign":"top","color":"#26354a","alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":1,"ops":1}}"""

        /** R2 §6.1 P-BGNULL：只观察、不判决（R2 §3.4-5 / §8-2）。 */
        val DOC_BG_NULL = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[],"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

        /**
         * R2 §6.1 的正方夹具表 + P08（非整数缩放）。声明在全部 DOC_* 之后：companion 属性
         * 按文本顺序初始化，前向引用尚未赋值的 `val` 会读到 null。
         */
        val FIXTURES = listOf(
            ParityFixture("P01", ParityFixtureClass.VECTOR, DOC_FILL_RECT),
            ParityFixture("P02", ParityFixtureClass.ALPHA, DOC_HALF_ALPHA),
            ParityFixture("P03", ParityFixtureClass.GEOMETRY, DOC_NESTED_CLIP),
            ParityFixture("P04", ParityFixtureClass.GEOMETRY, DOC_SHEAR),
            ParityFixture("P05", ParityFixtureClass.DASH, DOC_DASH),
            ParityFixture(
                "P06",
                ParityFixtureClass.TEXT,
                DOC_LABEL_H,
                labelPayload = "H",
                band = TEXT_BAND,
            ),
            ParityFixture(
                "P07",
                ParityFixtureClass.TEXT,
                DOC_LABEL_CJK,
                labelPayload = "标",
                band = TEXT_BAND,
            ),
            ParityFixture(
                "P08",
                ParityFixtureClass.GEOMETRY_NON_INTEGRAL,
                DOC_NESTED_CLIP,
                ptPerDu = 1.5f,
            ),
        )
    }
}
