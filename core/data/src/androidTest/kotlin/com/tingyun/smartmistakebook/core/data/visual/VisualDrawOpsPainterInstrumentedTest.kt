package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [VisualDrawOpsPainter] 的设备轨冒烟（批次 28 T2；R1 `docs/research/2026-10-28-painter-design.md`
 * §4 全量判据）。Canvas/Paint/Path 应用层只有真 Skia 能证——JVM 无 Robolectric，本文件
 * 即 R1 §4.3"fail-closed 矩阵逐条 + §4.2 结构探针"的承载（先红后绿边界与同批
 * `QuickJsSceneCompilerInstrumentedTest` 同款：判据在此，执行归设备收口轨）。
 *
 * 判据边界（R1 §4.4，不主张的明写）：跨设备/跨 API 字节一致不主张（字体/Skia 差异）——
 * 本文件只在单设备上钉确定性；label 探针是量级/带宽断言而非字形级精确。
 *
 * 坐标判据全部手工推导并写进注释（pxPerDu=2，viewBox 48×32 → 96×64 位图）；夹具一律走
 * 生产管线 JSON → [VisualDrawOpsValidator] → [VisualDrawOpsDocument] → painter
 * （R1 §4.2；hand-built 文档只用于"校验器会拒、painter 仍须守"的 schema/balance 负例）。
 */
@RunWith(AndroidJUnit4::class)
class VisualDrawOpsPainterInstrumentedTest {

    private val viewport = VisualViewport(48, 32)
    private val engines = CopyOnWriteArrayList<QuickJsVisualExecutionEngine>()

    @After
    fun closeEngines() {
        engines.forEach { it.close() }
        engines.clear()
    }

    // ------------------------------------------------------------ 夹具与助手

    private fun validatedDocument(json: String, atViewport: VisualViewport = viewport): VisualDrawOpsDocument {
        val validation = VisualDrawOpsValidator.validate(json, setOf("c1"), atViewport)
        if (validation !is VisualDrawOpsValidation.Valid) {
            val invalid = validation as VisualDrawOpsValidation.Invalid
            fail("fixture must validate: ${invalid.code}: ${invalid.message}")
        }
        // fail 返回 Unit（非 Nothing），sealed 两分支需显式窄化（同批 QuickJs 场景测试惯用法）。
        return (validation as VisualDrawOpsValidation.Valid).document
    }

    private fun render(
        document: VisualDrawOpsDocument,
        pxPerDu: Float,
        painter: VisualDrawOpsPainter = VisualDrawOpsPainter(),
        labelContent: (String) -> VisualLabelContent? = { null },
        bitmapPxPerDu: Float = pxPerDu,
    ): Bitmap {
        // 批次 28 设备轨更正（先红后绿）：fail-closed 入口门用例（pxPerDu 非有限/非正）
        // 原实现把坏 scale 先乘进位图尺寸 → createBitmap(0,…) 在夹具抛
        // IllegalArgumentException，painter 的 PAINTER_BOUNDS 门（VisualDrawOpsPainter.kt
        // :79-80，先于 setScale 与任何绘制）从未被执行到——设备首曝
        // `anUnusableReplayScaleFailsClosed` 红。bitmapPxPerDu 把"位图尺寸比例"与
        // "回放比例"解耦：门用例给合法位图比例建画布、仍以坏 pxPerDu 调 draw；
        // 默认值 = pxPerDu，既有调用点位图尺寸逐位不变。
        val width = (document.viewBox.w * bitmapPxPerDu).toInt()
        val height = (document.viewBox.h * bitmapPxPerDu).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        painter.draw(document, Canvas(bitmap), pxPerDu, labelContent)
        return bitmap
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also { pixels ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    private fun pixelAt(pixels: IntArray, width: Int, x: Int, y: Int): Int = pixels[y * width + x]

    /** 矩形带提取（行主序子阵）：跨渲染逐位比较用（dash 残尾回归的 strip 断言面）。 */
    private fun stripOf(pixels: IntArray, width: Int, xRange: IntRange, yRange: IntRange): IntArray {
        val strip = IntArray(xRange.count() * yRange.count())
        var index = 0
        for (y in yRange) {
            for (x in xRange) {
                strip[index++] = pixelAt(pixels, width, x, y)
            }
        }
        return strip
    }

    /** 全带扫描：返回带内非 transparent（或非 given 背景色）像素计数。 */
    private fun inkCount(pixels: IntArray, width: Int, xRange: IntRange, yRange: IntRange, blankColor: Int): Int {
        var count = 0
        for (y in yRange) {
            for (x in xRange) {
                if (pixelAt(pixels, width, x, y) != blankColor) count += 1
            }
        }
        return count
    }

    private fun textContent(payload: String, lines: List<VisualLabelLine>, heightDu: Float): VisualLabelContent.Text =
        VisualLabelContent.Text(
            payload = payload,
            sizeSp = 16f,
            fontScale = 1f,
            heightDu = heightDu,
            lines = lines,
        )

    private fun paintingFailureOf(block: () -> Unit): VisualDrawOpsPaintingException =
        try {
            block()
            throw AssertionError("expected VisualDrawOpsPaintingException")
        } catch (error: VisualDrawOpsPaintingException) {
            error
        }

    // ------------------------------------------------------------ 判据 1：同设备确定性

    @Test
    fun theSameOpsPaintIdenticalPixelsWithinOneMethod() {
        // R1 §4.1：ARGB_8888 软件位图、固定 pxPerDu、小尺寸、同 painter 实例（入口自复位
        // 被同测覆盖）渲两遍，getPixels 整阵完全相等——不是"接近"。
        val document = validatedDocument(DOC_FILL_RECT)
        val painter = VisualDrawOpsPainter()
        val first = pixelsOf(render(document, pxPerDu = 2f, painter = painter))
        val second = pixelsOf(render(document, pxPerDu = 2f, painter = painter))
        assertEquals(96, first.size / 64)
        assertTrue(Arrays.equals(first, second))
    }

    @Test
    fun theSameOpsPaintIdenticalPixelsInAFreshTestMethod() {
        // 跨测试方法复跑形态（防单测初始化顺序耦合）：全新 painter + 全新位图，仍逐位相等。
        val document = validatedDocument(DOC_FILL_RECT)
        val first = pixelsOf(render(document, pxPerDu = 2f))
        val second = pixelsOf(render(document, pxPerDu = 2f))
        assertTrue(Arrays.equals(first, second))
    }

    // ------------------------------------------------------------ 判据 2：结构探针

    @Test
    fun aShearMatrixPlacesInkByContractColumnOrder() {
        // 矩阵列序正例（R1 §3.3 工作例的设备形态）：契约映射 (x,y)→(x, x+y)。局部矩形
        // du (2,1)-(3,2) 落到 du (2,3)-(3,5)，pxPerDu=2 → 设备 (4,6)-(6,10)。
        // 原序误装把该矩形映到设备 (6,4)-(10,6)：探针 (5,8) 只有正确列序才着墨、
        // (8,5) 只有误装才会着墨——两断言合起来钉死列序。
        val pixels = pixelsOf(render(validatedDocument(DOC_SHEAR), pxPerDu = 2f))
        assertEquals(0xFFFF0000.toInt(), pixelAt(pixels, 96, 5, 8))
        assertEquals(0x00000000, pixelAt(pixels, 96, 8, 5))
    }

    @Test
    fun aNestedGroupClipCutsInRootSpace() {
        // 嵌套变换组内的 clip（朴素回放必错场景 1，R1 §2.2）：G1(scale2) ⊃ G2(translate+2du,
        // clip 烘焙矩形 du (4,0)-(12,8))，路径局部矩形 du (0,0)-(8,8)。
        // 正确：clip 在根空间求交 → 设备 (8,0)-(24,16)；绘制区 (8,0)-(40,32)。
        // 探针 (10,4) 在交内有墨；(30,4) 在绘制区内、交外——朴素"当前矩阵下 clipPath"
        // 会把 clip 双乘到设备 (16,0)-(48,32) 从而给 (30,4) 上墨，本断言红。
        val pixels = pixelsOf(render(validatedDocument(DOC_NESTED_CLIP), pxPerDu = 2f))
        assertEquals(0xFF00AA00.toInt(), pixelAt(pixels, 96, 10, 4))
        assertEquals(0x00000000, pixelAt(pixels, 96, 30, 4))
    }

    @Test
    fun aGroupLabelLandsAtTheProjectedRootSpacePosition() {
        // 组内 label（朴素回放必错场景 2，R1 §2.2）：label(x=15,y=20) 在 concat(+5du,0)
        // 生效下发射，但坐标已是根空间投影——舞步下墨迹左缘在设备 x=30。
        // 朴素回放（label 随组矩阵双乘）会把墨迹右移 +10px：右界断言必红。
        // parity 钉（R1 §3.5）：用与度量链同构的 Paint（textSize=sizeSp×density×fontScale，
        // density 以 pxPerDu=2 代用）量 "H" 的 advance——回放设备墨迹带宽必须与之同量级。
        val measurePaint = Paint()
        measurePaint.textSize = 16f * 2f * 1f
        val advancePx = measurePaint.measureText("H")
        assertTrue("font advance sanity", advancePx > 0f)
        val leftEdgePx = 30
        val pixels = pixelsOf(
            render(
                validatedDocument(DOC_LABEL_GROUP),
                pxPerDu = 2f,
                labelContent = { id -> if (id == "c1") textContent("H", listOf(VisualLabelLine(0, 1)), heightDu = 12f) else null },
            ),
        )
        val rows = 6..50
        assertTrue(inkCount(pixels, 96, leftEdgePx - 1..leftEdgePx + advancePx.toInt() + 1, rows, 0x00000000) > 0)
        // 左界外无墨 + 右界外无墨（右界即朴素双乘的杀死断言）。
        assertEquals(0, inkCount(pixels, 96, 0..leftEdgePx - 4, rows, 0x00000000))
        assertEquals(0, inkCount(pixels, 96, leftEdgePx + advancePx.toInt() + 6..95, rows, 0x00000000))
    }

    @Test
    fun halfAlphaFillsFloorTheQuantizedAlpha() {
        // alpha 取整（R1 §4-判据 2）：alpha=0.5 → floor(127.5)=127（round 实现者必红）。
        // 手算预乘混色（白底 #ffffff 上 #ff0000，srcA=127）：
        //   out_r = 255×127/255 + 255×128/255 = 127 + 128 = 255
        //   out_g = out_b = 0 + 255×128/255 = 128 → 0xFFFF8080。中心像素无 AA 边缘。
        val pixels = pixelsOf(render(validatedDocument(DOC_ALPHA), pxPerDu = 2f))
        assertEquals(0xFFFF8080.toInt(), pixelAt(pixels, 96, 24, 16))
        assertEquals(0xFFFFFFFF.toInt(), pixelAt(pixels, 96, 1, 1))
    }

    @Test
    fun backgroundColorsTheCanvasAndNullLeavesItTransparent() {
        val withBackground = pixelsOf(render(validatedDocument(DOC_BG_SET), pxPerDu = 2f))
        assertEquals(0xFF123456.toInt(), pixelAt(withBackground, 96, 0, 0))
        assertEquals(0xFF123456.toInt(), pixelAt(withBackground, 96, 95, 63))
        val withoutBackground = pixelsOf(render(validatedDocument(DOC_BG_NULL), pxPerDu = 2f))
        assertEquals(0x00000000, pixelAt(withoutBackground, 96, 0, 0))
        assertEquals(0x00000000, pixelAt(withoutBackground, 96, 95, 63))
    }

    @Test
    fun stateIsRestoredAfterGroupClose() {
        // 状态隔离：restore 后下一 op 回到根空间落墨。
        // 组内矩形 du (2,2)-(6,6) 过 translate(+6) → 设备 (16,4)-(24,12)：探针 (20,8)。
        // restore 后矩形 du (16,2)-(20,6) 在根空间 → 设备 (32,4)-(40,12)：探针 (36,8)。
        // 若组平移泄漏进 restore 之后的状态，rect2 会落在 (44,4)-(52,12)：探针 (48,8) 红。
        val pixels = pixelsOf(render(validatedDocument(DOC_STATE), pxPerDu = 2f))
        assertEquals(0xFFFF0000.toInt(), pixelAt(pixels, 96, 20, 8))
        assertEquals(0xFFFF0000.toInt(), pixelAt(pixels, 96, 36, 8))
        assertEquals(0x00000000, pixelAt(pixels, 96, 48, 8))
    }

    @Test
    fun aStrokePhasePaintsOutsideTheFillBand() {
        // stroke 相位存在性（几何探针）：fill 矩形设备 (20,20)-(60,44)，stroke 宽 4du=8px
        // 外扩 4px → 左带 [16,20) 只有 stroke 相位会着墨（R1 §4.2"中心线颜色=stroke 色"
        // 的原形在 V1 不可分——stroke 无颜色源、取 fill 色（R1 §5-① 过渡裁定），故改为
        // 几何存在性断言；缺口移交 T3）。
        val pixels = pixelsOf(render(validatedDocument(DOC_STROKE), pxPerDu = 2f))
        assertEquals(0xFF00AA00.toInt(), pixelAt(pixels, 96, 17, 32))
        assertEquals(0xFF00AA00.toInt(), pixelAt(pixels, 96, 40, 32))
        assertEquals(0x00000000, pixelAt(pixels, 96, 8, 32))
    }

    @Test
    fun aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern() {
        // 复核修复回归（2026-10-28，失败 (a)）：bindPathPaint 曾用"只扩不缩"的常驻 dash
        // 缓冲——同实例先画 6 项 dash 再画 2 项 dash，DashPathEffect 收到 [2,2,6,6,6,6]
        //（构造器按 intervals.length 全量消费），victim 虚线节奏混入 donor 残尾、静默错画。
        // 修复=按 dash.size 精确装配：同实例 donor→victim 后，victim 带内像素必须与
        // "只画 victim"逐位一致（先断非空墨，防真空通过）。
        val painter = VisualDrawOpsPainter()
        val victimOnly = pixelsOf(render(validatedDocument(DOC_DASH_VICTIM), pxPerDu = 2f, painter = painter))
        pixelsOf(render(validatedDocument(DOC_DASH_DONOR), pxPerDu = 2f, painter = painter))
        val victimAfterDonor = pixelsOf(render(validatedDocument(DOC_DASH_VICTIM), pxPerDu = 2f, painter = painter))
        // victim 线 du y=28 → px 56（宽 1du 带 + AA）；du x=4..44 → px 8..88。donor 在 y=2du，
        // 不进本带；binder 其余字段无条件全设，本 strip 只分离 dash pattern 之差。
        assertTrue(
            "victim strip must contain ink",
            inkCount(victimOnly, 96, 8..88, 54..58, 0xFFFFFFFF.toInt()) > 0,
        )
        assertTrue(Arrays.equals(stripOf(victimOnly, 96, 8..88, 54..58), stripOf(victimAfterDonor, 96, 8..88, 54..58)))
    }

    // ------------------------------------------------------------ 判据 3：fail-closed 矩阵

    @Test
    fun aForeignSchemaVersionFailsClosed() {
        // hand-built 文档（校验器会拒 schemaVersion≠1；painter 仍须守自己的 intake）。
        val document = VisualDrawOpsDocument(
            drawSchemaVersion = 2,
            viewBox = VisualDrawOpsViewBox(0.0, 0.0, 48.0, 32.0),
            background = null,
            ops = emptyList(),
            hitRegions = emptyList(),
            stats = VisualDrawOpsStats(0, 0, 0, 0),
        )
        val failure = paintingFailureOf { render(document, pxPerDu = 2f) }
        assertEquals(PAINTER_SCHEMA_VERSION, failure.code)
    }

    @Test
    fun aRestoreWithoutSaveFailsClosedInsideTheWalk() {
        // hand-built 未配平文档（校验器会拒；"painter 不自愈"——只拒不修）。
        val document = VisualDrawOpsDocument(
            drawSchemaVersion = 1,
            viewBox = VisualDrawOpsViewBox(0.0, 0.0, 48.0, 32.0),
            background = null,
            ops = listOf(VisualDrawOp.Restore),
            hitRegions = emptyList(),
            stats = VisualDrawOpsStats(0, 0, 0, 1),
        )
        val failure = paintingFailureOf { render(document, pxPerDu = 2f) }
        assertEquals(PAINTER_BALANCE, failure.code)
        assertTrue(failure.message!!.contains("ops[0]"))
    }

    @Test
    fun aStrokeOnlyPathFailsClosed() {
        // R1 §5-① 缺口的运行时面：两道生成门都放行 stroke-only，painter 拒。
        val document = validatedDocument(DOC_STROKE_ONLY)
        val failure = paintingFailureOf { render(document, pxPerDu = 2f) }
        assertEquals(PAINTER_STROKE_WITHOUT_FILL, failure.code)
        assertTrue(failure.message!!.contains("ops[0]"))
    }

    @Test
    fun anOddDashFailsClosedEvenThoughValidationAcceptsIt() {
        // R1 §5-② 缺口的运行时面：dash=[2,4,2] 过校验器（值域/条数均合法），painter 按
        // DashPathEffect 契约（偶数 interval）拒——本用例同时是缺口存在的证据，移交 T3。
        val document = validatedDocument(DOC_DASH_ODD)
        val failure = paintingFailureOf { render(document, pxPerDu = 2f) }
        assertEquals(PAINTER_DASH_ODD, failure.code)
        assertTrue(failure.message!!.contains("ops[0]"))
    }

    @Test
    fun anEmptyDashFailsClosedEvenThoughValidationAcceptsIt() {
        // 复核修复回归（2026-10-28）：两道生成门都放行 dash=[]（validator :424-442 循环零项、
        // compiler requireStroke 空数组原样返回），painter 曾把 FloatArray(0) 直接交
        // DashPathEffect——AOSP 构造器对 length<2 抛**无码** ArrayIndexOutOfBoundsException
        //（aosp-mirror DashPathEffect.java:34-39，本日 gh api 实读），破坏"全部折进有码异常
        // 族"；最小 2 项下界已并入前置形状门（与奇数门同码同相位，fill 上墨前拒绝）。
        val failure = paintingFailureOf { render(validatedDocument(DOC_DASH_EMPTY), pxPerDu = 2f) }
        assertEquals(PAINTER_DASH_ODD, failure.code)
        assertTrue(failure.message!!.contains("ops[0]"))
        // 跟在实 dash 后的空 dash（同文档第二 op）同样被拒于 ops[1]——修复前它整条复用
        // 旧 pattern 静默错画（奇数门对 0 放行、缓冲只扩不缩）。
        val afterRealDash =
            paintingFailureOf { render(validatedDocument(DOC_DASH_DONOR_THEN_EMPTY), pxPerDu = 2f) }
        assertEquals(PAINTER_DASH_ODD, afterRealDash.code)
        assertTrue(afterRealDash.message!!.contains("ops[1]"))
    }

    @Test
    fun aLabelWithoutResolvableContentFailsClosed() {
        val document = validatedDocument(DOC_LABEL_FROZEN)
        val failure = paintingFailureOf { render(document, pxPerDu = 2f, labelContent = { null }) }
        assertEquals(PAINTER_UNKNOWN_LABEL, failure.code)
        assertTrue(failure.message!!.contains("ops[0]"))
    }

    @Test
    fun aMathLabelFailsClosedThisBatch() {
        val document = validatedDocument(DOC_LABEL_FROZEN)
        val mathContent = VisualLabelContent.Math(
            box = com.tingyun.smartmistakebook.core.model.MathBox.Leaf(
                width = 10f,
                height = 12f,
                baseline = 9f,
                content = "x",
            ),
            sizeSp = 16f,
            fontScale = 1f,
        )
        val failure = paintingFailureOf {
            render(document, pxPerDu = 2f, labelContent = { id -> if (id == "c1") mathContent else null })
        }
        assertEquals(PAINTER_LABEL_MATH, failure.code)
        assertTrue(failure.message!!.contains("ops[0]"))
    }

    @Test
    fun anUnusableReplayScaleFailsClosed() {
        // "超尺寸画布"入口门：pxPerDu 坏（非有限/非正）→ 坐标域失控，绘制前 fail-closed。
        val document = validatedDocument(DOC_FILL_RECT)
        assertEquals(PAINTER_BOUNDS, paintingFailureOf { render(document, pxPerDu = 0f, bitmapPxPerDu = 2f) }.code)
        assertEquals(PAINTER_BOUNDS, paintingFailureOf { render(document, pxPerDu = Float.NaN, bitmapPxPerDu = 2f) }.code)
        assertEquals(PAINTER_BOUNDS, paintingFailureOf { render(document, pxPerDu = Float.POSITIVE_INFINITY, bitmapPxPerDu = 2f) }.code)
    }

    // ------------------------------------------------------------ 判据 4：合法场景端到端

    @Test
    fun aLegalSceneGoesFromCompileToColoredBitmap() = runBlocking {
        // compile → 指令 → Bitmap 非空且关键区域着色（真引擎 + 真编译器资产 + 校验器 + painter）。
        // 场景与编译金样同构但去掉 clip、dot 带 fill（stroke-only 是已登记缺口，见上）：
        // 组 matrix(+5,+5) opacity 0.5 > path(dot, fill #ff0000) + label(center/middle)。
        val engine = QuickJsVisualExecutionEngine(
            StubMetricsMeasurer(),
            cooperativeTimeoutMillis = 100L,
            hardDeadlineMillis = 1_500L,
        ).also { engines.add(it) }

        val result = engine.execute(
            VisualExecutionRequest(
                programId = "program-painter-e2e",
                sourceJs = E2E_SCENE_SOURCE,
                sceneRevision = 1,
                stateJson = null,
                viewport = VisualViewport(360, 240),
                timeMs = 1_000L,
                seed = 42L,
                theme = "light",
            ),
        )
        if (result !is VisualExecutionResult.Rendered) {
            fail("expected Rendered, got: $result")
        }
        val rendered = result as VisualExecutionResult.Rendered
        assertNotNull(rendered.drawCommandsJson)
        val e2eViewport = VisualViewport(360, 240)
        val validation = VisualDrawOpsValidator.validate(
            rendered.drawCommandsJson!!,
            rendered.contentIds,
            e2eViewport,
        )
        if (validation !is VisualDrawOpsValidation.Valid) {
            val invalid = validation as VisualDrawOpsValidation.Invalid
            fail("compiled draw ops failed native validation: ${invalid.code}: ${invalid.message}")
        }
        val document = (validation as VisualDrawOpsValidation.Valid).document

        // label 内容由冻结 artifact 折成 DTO：'标签' 两段内蕴行（单段）。
        val contentId = rendered.contentIds.single()
        val bitmap = render(
            document,
            pxPerDu = 1f,
            labelContent = { id ->
                if (id == contentId) textContent("标签", listOf(VisualLabelLine(0, 2)), heightDu = 12f) else null
            },
        )
        val pixels = pixelsOf(bitmap)
        val width = bitmap.width
        // 背景设色 → 角点白；dot 中心 (55,55)（circle(50,50,40) 过组平移）着色但被 0.5 组
        // alpha 调淡；label 带内（基线 71、16sp）有墨。跨引擎数字格式差 → e2e 只断言
        // "着色/非空白"，精确混色归上面的专用 alpha 探针。
        assertEquals(0xFFFFFFFF.toInt(), pixelAt(pixels, width, 1, 1))
        val dotCenter = pixelAt(pixels, width, 55, 55)
        assertNotEquals(0xFFFFFFFF.toInt(), dotCenter)
        assertNotEquals(0x00000000, dotCenter)
        assertTrue(inkCount(pixels, width, 100..140, 54..78, 0xFFFFFFFF.toInt()) > 0)
        assertTrue(inkCount(pixels, width, 0..width - 1, 0..bitmap.height - 1, 0xFFFFFFFF.toInt()) > 0)
    }

    /** 度量桩：与 `QuickJsSceneCompilerInstrumentedTest.SingleContentMeasurer` 同形（w=10/h=12/baseline=9）。 */
    private class StubMetricsMeasurer : VisualContentMeasurer {
        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult =
            VisualContentMeasureResult(
                batch.entries.mapIndexed { index, _ ->
                    VisualMeasuredContent(
                        contentId = "content-$index",
                        widthDu = 10f,
                        heightDu = 12f,
                        baselineDu = 9f,
                        fontSizeSp = 16f,
                        digest = "digest-$index",
                    )
                },
            )
    }

    private companion object {
        // viewBox 48×32（pxPerDu=2 → 96×64 位图）。stats 计数与 ops 逐一核对（校验器复算）。

        /** 白底红矩形（确定性/入口门夹具）：设备 (8,4)-(44,28)。 */
        val DOC_FILL_RECT = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,2],["L",22,2],["L",22,14],["L",4,14],["Z"]],""" +
            """"fill":"#ff0000","stroke":null,"alpha":1}],"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /** 切变 [1,1,0,1,0,0]（y'=x+y）下的局部矩形：正确落点设备 (4,6)-(6,10)，误装 (6,4)-(10,6)。 */
        val DOC_SHEAR = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"save"},{"op":"concat","m":[1,1,0,1,0,0]},""" +
            """{"op":"path","path":[["M",2,1],["L",3,1],["L",3,2],["L",2,2],["Z"]],"fill":"#ff0000","stroke":null,"alpha":1},""" +
            """{"op":"restore"}],"hitRegions":[],"stats":{"nodes":1,"pathCommands":5,"labels":0,"ops":4}}"""

        /**
         * 嵌套 clip：G1(scale2) ⊃ G2(translate+2du, clip)。clip 烘焙 = composed(2x+4, 2y)
         * 作用于局部 (0,0)-(4,4) → du (4,0)-(12,8)；绘制矩形局部 (0,0)-(8,8) → 设备
         * (8,0)-(40,32)；交 = (8,0)-(24,16)。
         */
        val DOC_NESTED_CLIP = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"save"},{"op":"concat","m":[2,0,0,2,0,0]},{"op":"save"},""" +
            """{"op":"clip","path":[["M",4,0],["L",12,0],["L",12,8],["L",4,8],["Z"]],"rule":"nonzero"},""" +
            """{"op":"concat","m":[1,0,0,1,2,0]},""" +
            """{"op":"path","path":[["M",0,0],["L",8,0],["L",8,8],["L",0,8],["Z"]],"fill":"#00aa00","stroke":null,"alpha":1},""" +
            """{"op":"restore"},{"op":"restore"}],"hitRegions":[],"stats":{"nodes":2,"pathCommands":10,"labels":0,"ops":8}}"""

        /** 组内 label：concat(+5du,0) 生效下发射，(x,y)=(15,20) 已是根空间投影。 */
        val DOC_LABEL_GROUP = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"save"},{"op":"concat","m":[1,0,0,1,5,0]},""" +
            """{"op":"label","contentId":"c1","x":15,"y":20,"rotationDeg":0,"hAlign":"center","vAlign":"middle","color":"#26354a","alpha":1},""" +
            """{"op":"restore"}],"hitRegions":[],"stats":{"nodes":1,"pathCommands":0,"labels":1,"ops":4}}"""

        /** 白底 0.5 红矩形：中心 (24,16) == 0xFFFF8080（手算见用例注释）。 */
        val DOC_ALPHA = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],""" +
            """"fill":"#ff0000","stroke":null,"alpha":0.5}],"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        val DOC_BG_SET = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#123456","ops":[],"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

        val DOC_BG_NULL = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[],"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

        /** 状态隔离：组内矩形 (+6du 平移) + restore 后根空间矩形。 */
        val DOC_STATE = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"save"},{"op":"concat","m":[1,0,0,1,6,0]},""" +
            """{"op":"path","path":[["M",2,2],["L",6,2],["L",6,6],["L",2,6],["Z"]],"fill":"#ff0000","stroke":null,"alpha":1},""" +
            """{"op":"restore"},""" +
            """{"op":"path","path":[["M",16,2],["L",20,2],["L",20,6],["L",16,6],["Z"]],"fill":"#ff0000","stroke":null,"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":1,"pathCommands":10,"labels":0,"ops":5}}"""

        /** fill+stroke 同 op（stroke 色取 fill 色，过渡裁定）：左带 [16,20) 验 stroke 相位存在。 */
        val DOC_STROKE = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",10,10],["L",30,10],["L",30,22],["L",10,22],["Z"]],""" +
            """"fill":"#00aa00","stroke":{"width":4,"cap":"butt","join":"miter","miterLimit":4,"dash":null},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /** stroke-only（校验器放行、painter 拒——R1 §5-① 缺口运行时面）。 */
        val DOC_STROKE_ONLY = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],""" +
            """"fill":null,"stroke":{"width":2,"cap":"butt","join":"miter","miterLimit":4,"dash":null},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /** 奇数 dash（校验器放行、painter 拒——R1 §5-② 缺口运行时面）。 */
        val DOC_DASH_ODD = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],""" +
            """"fill":"#ff0000","stroke":{"width":2,"cap":"butt","join":"miter","miterLimit":4,"dash":[2,4,2]},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /**
         * dash 残尾 donor（6 项，白底，du y=2 横线）：同实例先画，曾把 dash 复用缓冲扩到
         * 6 项——复核失败 (a) 的残尾载体（2026-10-28 复核修复回归夹具）。
         */
        val DOC_DASH_DONOR = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,2],["L",44,2]],""" +
            """"fill":"#ff0000","stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[6,6,6,6,6,6]},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":2,"labels":0,"ops":1}}"""

        /** dash victim（2 项，du y=28 横线）：修复前同实例 donor 之后画，pattern = [2,2,6,6,6,6]。 */
        val DOC_DASH_VICTIM = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":"#ffffff","ops":[{"op":"path","path":[["M",4,28],["L",44,28]],""" +
            """"fill":"#ff0000","stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[2,2]},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":2,"labels":0,"ops":1}}"""

        /** 空 dash（两道生成门都放行、painter 拒——复核 2026-10-28 缺口运行时面：失败 (b)）。 */
        val DOC_DASH_EMPTY = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],""" +
            """"fill":"#ff0000","stroke":{"width":2,"cap":"butt","join":"miter","miterLimit":4,"dash":[]},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}"""

        /** 实 dash 后跟空 dash（同文档）：修复前整条复用 donor pattern 静默错画（失败 (c)）。 */
        val DOC_DASH_DONOR_THEN_EMPTY = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",4,2],["L",44,2]],""" +
            """"fill":"#ff0000","stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[6,6,6,6,6,6]},"alpha":1},""" +
            """{"op":"path","path":[["M",4,28],["L",44,28]],""" +
            """"fill":"#ff0000","stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[]},"alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":4,"labels":0,"ops":2}}"""

        /** 冻结引用 label（resolver null → UNKNOWN_LABEL；resolver 给 Math → LABEL_MATH）。 */
        val DOC_LABEL_FROZEN = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},""" +
            """"background":null,"ops":[{"op":"label","contentId":"c1","x":15,"y":20,"rotationDeg":0,"hAlign":"left","vAlign":"top","color":"#26354a","alpha":1}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":1,"ops":1}}"""

        /**
         * e2e 场景（viewport 360×240，pxPerDu=1）：组 matrix(+5,+5) opacity 0.5 >
         * path(visualSdk.circle(50,50,40), fill #ff0000) + label(anchor(100,60)+offset(2,3),
         * center/middle)。label 投影金样：x = 107−10×0.5 = 102，y = 68−12×0.5+9 = 71。
         */
        val E2E_SCENE_SOURCE = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '标签' }]; },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-painter-e2e', revision: input.revision,
                         viewBox: { x: 0, y: 0, width: input.viewport.widthDu,
                                    height: input.viewport.heightDu },
                         background: '#ffffff',
                         paths: [{ id: 'dot', commands: visualSdk.circle(50, 50, 40) }],
                         root: { type: 'group', matrix: [1, 0, 0, 1, 5, 5], opacity: 0.5,
                                 children: [
                                   { type: 'path', pathId: 'dot', fill: '#ff0000' },
                                   { type: 'label', contentId: metrics[0].contentId,
                                     anchor: { x: 100, y: 60 }, offset: { x: 2, y: 3 },
                                     hAlign: 'center', vAlign: 'middle', color: '#26354a' } ] },
                         controls: [], hitRegions: [] };
              },
            };
        """.trimIndent()
    }
}
