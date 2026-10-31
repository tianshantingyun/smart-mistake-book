package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Q-04 六类 fixture 静态子集的 JVM 门（批次 38；R3 §4.3"静态 MVA 的必测子集是 1/2/5/6 +
 * 新组合"）。
 *
 * 语料：`core/data/src/test/resources/visual/fixtures/q04/`（三件套见该目录 README）：
 * - `*.fixture.json` 冻结输入（SceneV1 + metrics + labels 文本 + subject 声明参数）；
 * - `*.drawops.json` 生产编译器逐字节输出（由 `tools/visual_compiler/compile_fixtures.mjs`
 *   漂移门钉住——JVM 无 QuickJS 原生库，"场景→DrawOps"半边只能在 Node 侧验证）；
 * - `*.oracle.json` **独立预期**（本类逐字段执行的渲染结构 oracle）。
 *
 * 两层预期分开（计划 `:169`"渲染 fixture 与学科正确性金标分开"）：
 * - **渲染 oracle**（oracle.json）：op 序列/stats/颜色/stroke/label 终位置/可精确手导的
 *   路径坐标——回答"这份 DrawOps 长什么样"；
 * - **学科 oracle**（fixture.subject + 本类独立重算）：比例 3:1、面积 3F0h/8（shoelace）、
 *   受力方向（杆轴单位向量）、阶段映射、波形叠加顶点——回答"画的东西物理上对不对"。
 *   分离方向由两条反证钉住：只坏 label 位置 → 仅渲染 oracle 红（学科仍绿）；只坏波形
 *   顶点 → 仅学科 oracle 红（渲染结构仍绿）。
 *
 * **负例的非空洞性**：几何/物理篡改用例保证 oracle 比对不是恒真——若有人把比较器改成
 * 恒真（或丢字段），篡改用例立即变红。malformed 变体钉住"坏语料按码拒绝"（不是静默
 * 画错）。JVM 执行由主会话门禁串行完成（本轨不运行 Gradle）。
 */
class VisualFigureFixtureOracleTest {

    private val fixtures = listOf(
        "screenshot1-ratio-bar",
        "screenshot2-fx-area",
        "screenshot5-force-diagram",
        "screenshot6-plug-stages",
        "combo-wave-trajectory",
    )

    private fun resource(name: String): String {
        val stream = VisualFigureFixtureOracleTest::class.java.classLoader
            ?.getResourceAsStream("visual/fixtures/q04/$name")
            ?: error("Missing fixture resource visual/fixtures/q04/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun load(name: String): Loaded {
        val fixture = Json.parseToJsonElement(resource("$name.fixture.json")).jsonObject
        val drawOpsText = resource("$name.drawops.json")
        val oracle = Json.parseToJsonElement(resource("$name.oracle.json")).jsonObject
        val viewport = VisualViewport(
            fixture.objectAt("viewport").num("widthDu").toInt(),
            fixture.objectAt("viewport").num("heightDu").toInt(),
        )
        val contentIds = fixture.arrayAt("metrics").mapTo(HashSet()) {
            it.jsonObject.str("contentId")
        }
        return Loaded(name, fixture, drawOpsText, oracle, viewport, contentIds)
    }

    private class Loaded(
        val name: String,
        val fixture: JsonObject,
        val drawOpsText: String,
        val oracle: JsonObject,
        val viewport: VisualViewport,
        val contentIds: Set<String>,
    ) {
        val parsed: VisualDrawOpsDocument by lazy {
            when (val validation = VisualDrawOpsParser.parse(drawOpsText, contentIds, viewport)) {
                is VisualDrawOpsValidation.Valid -> validation.document
                is VisualDrawOpsValidation.Invalid ->
                    throw AssertionError("$name: committed drawops must parse: ${validation.code} ${validation.message}")
            }
        }
        val subject: JsonObject get() = fixture.objectAt("subject")
    }

    // ---------------------------------------------------------------- 渲染 oracle

    @Test
    fun `every fixture parses and matches its independent rendering oracle`() {
        for (loaded in fixtures.map(::load)) {
            assertMatchesOracle(loaded, loaded.parsed, context = loaded.name)
            // 静态子集面：无命中区（交互不入静态语料）、词表六原语之内、版本 1。
            assertTrue(
                "${loaded.name}: static subset carries no hit regions",
                loaded.parsed.hitRegions.isEmpty(),
            )
            for (op in loaded.parsed.ops) {
                val kind = when (op) {
                    is VisualDrawOp.Save -> "save"
                    is VisualDrawOp.Restore -> "restore"
                    is VisualDrawOp.Concat -> "concat"
                    is VisualDrawOp.Clip -> "clip"
                    is VisualDrawOp.DrawPath -> "path"
                    is VisualDrawOp.Label -> "label"
                }
                assertTrue(
                    "${loaded.name}: op kind $kind outside the static vocabulary",
                    kind in setOf("save", "restore", "concat", "clip", "path", "label"),
                )
            }
        }
    }

    private fun assertMatchesOracle(loaded: Loaded, document: VisualDrawOpsDocument, context: String) {
        val oracle = loaded.oracle
        assertEquals("$context: schema version", oracle.num("drawSchemaVersion"), document.drawSchemaVersion.toDouble(), 0.0)
        val viewBox = oracle.objectAt("viewBox")
        assertEquals("$context: viewBox.x", viewBox.num("x"), document.viewBox.x, 0.0)
        assertEquals("$context: viewBox.y", viewBox.num("y"), document.viewBox.y, 0.0)
        assertEquals("$context: viewBox.w", viewBox.num("w"), document.viewBox.w, 0.0)
        assertEquals("$context: viewBox.h", viewBox.num("h"), document.viewBox.h, 0.0)
        val expectedBackground = when (val background = oracle["background"]) {
            // 键缺失与显式 null 同为"无背景"（JSON 语义等价）：缺失 → 期望 null → 与
            // document.background 比对，文档侧有背景则照常红。null 并入 JsonNull 分支后，
            // else 分支对 background 完成非空智能转换（Map.get 返回可空——第 1 轮补
            // jsonPrimitive import 后，:127 的可空接收者错误才得以显形）。
            null, is JsonNull -> null
            else -> background.jsonPrimitive.content
        }
        assertEquals("$context: background", expectedBackground, document.background)
        assertEquals("$context: hit region count", oracle.num("hitRegionCount").toInt(), document.hitRegions.size)
        val expectedKinds = oracle.arrayAt("opSequence").map { it.jsonPrimitive.content }
        assertEquals(
            "$context: op sequence",
            expectedKinds,
            document.ops.map { it.kindString() },
        )
        val expectedStats = oracle.objectAt("stats")
        assertEquals("$context: stats.nodes", expectedStats.num("nodes"), document.stats.nodes.toDouble(), 0.0)
        assertEquals("$context: stats.pathCommands", expectedStats.num("pathCommands"), document.stats.pathCommands.toDouble(), 0.0)
        assertEquals("$context: stats.labels", expectedStats.num("labels"), document.stats.labels.toDouble(), 0.0)
        assertEquals("$context: stats.ops", expectedStats.num("ops"), document.stats.ops.toDouble(), 0.0)

        val expectedOps = oracle.arrayAt("ops").map { it.jsonObject }
        assertEquals(expectedOps.size, document.ops.size)
        for ((index, expected) in expectedOps.withIndex()) {
            val actual = document.ops[index]
            when (expected.str("kind")) {
                "path" -> {
                    val path = actual as? VisualDrawOp.DrawPath ?: error("$context: ops[$index] is not a path")
                    assertEquals("$context: ops[$index].fill", expected.str("fill"), path.fill)
                    assertEquals("$context: ops[$index].alpha", expected.num("alpha"), path.alpha, 0.0)
                    assertStrokeEquals("$context: ops[$index].stroke", expected["stroke"], path.stroke)
                    val commands = expected["commands"]
                    if (commands is JsonNull) {
                        // 超越函数坐标不进渲染 oracle（学科 oracle 按叠加函数重算）；
                        // 这里只钉命令码序列与条数。
                        val codes = expected.arrayAt("codes").map { it.jsonPrimitive.content }
                        assertEquals("$context: ops[$index] command codes", codes, path.path.map { it.codeString() })
                    } else {
                        // 这里必须 `!!` 而**不能**把 null 并入 JsonNull 分支：commands 键缺失
                        // 必须响亮失败（!! 抛出），不得静默降级成 codes-only 检查而漏掉坐标
                        // 比对——那才是放宽。收窄惯例与 assertStrokeEquals 的 expected!! 一致。
                        assertCommandsEquals("$context: ops[$index].commands", commands!!.jsonArray, path.path)
                    }
                }

                "label" -> {
                    val label = actual as? VisualDrawOp.Label ?: error("$context: ops[$index] is not a label")
                    assertEquals("$context: ops[$index].contentId", expected.str("contentId"), label.contentId)
                    assertEquals("$context: ops[$index].x", expected.num("x"), label.x, 0.0)
                    assertEquals("$context: ops[$index].y", expected.num("y"), label.y, 0.0)
                    assertEquals("$context: ops[$index].rotationDeg", expected.num("rotationDeg"), label.rotationDeg, 0.0)
                    assertEquals("$context: ops[$index].hAlign", expected.str("hAlign"), label.hAlign.json)
                    assertEquals("$context: ops[$index].vAlign", expected.str("vAlign"), label.vAlign.json)
                    assertEquals("$context: ops[$index].color", expected.str("color"), label.color)
                    assertEquals("$context: ops[$index].alpha", expected.num("alpha"), label.alpha, 0.0)
                }

                else -> error("$context: oracle op kind ${expected.str("kind")} not covered")
            }
        }
    }

    private fun assertStrokeEquals(context: String, expected: JsonElement?, actual: VisualStrokeSpec?) {
        if (expected is JsonNull) {
            assertEquals("$context: expected null stroke", null, actual)
            return
        }
        val stroke = expected!!.jsonObject
        assertEquals("$context: width", stroke.num("width"), actual!!.width, 0.0)
        assertEquals("$context: cap", stroke.str("cap"), actual.cap.json)
        assertEquals("$context: join", stroke.str("join"), actual.join.json)
        assertEquals("$context: miterLimit", stroke.num("miterLimit"), actual.miterLimit, 0.0)
        val dash = stroke["dash"]
        // 键缺失与显式 null 同为"无 dash"（逐输入等价）：缺失 → 期望 null → actual.dash
        // 非空则照常红。null 并入后 else 分支完成非空智能转换。
        if (dash == null || dash is JsonNull) {
            assertEquals("$context: dash", null, actual.dash)
        } else {
            assertEquals("$context: dash", dash.jsonArray.map { it.jsonPrimitive.content.toDouble() }, actual.dash)
        }
    }

    private fun assertCommandsEquals(context: String, expected: JsonArray, actual: List<VisualPathCommand>) {
        assertEquals("$context: command count", expected.size, actual.size)
        for ((index, command) in expected.withIndex()) {
            val expectedCommand = command.jsonArray
            val actualCommand = actual[index]
            val expectedCode = expectedCommand.first().jsonPrimitive.content
            assertEquals("$context: command[$index] code", expectedCode, actualCommand.codeString())
            val actualNumbers = actualCommand.numbers()
            assertEquals("$context: command[$index] arity", expectedCommand.size - 1, actualNumbers.size)
            for ((i, value) in expectedCommand.drop(1).withIndex()) {
                assertEquals("$context: command[$index] component $i", value.jsonPrimitive.content.toDouble(), actualNumbers[i], 0.0)
            }
        }
    }

    private fun VisualDrawOp.kindString(): String = when (this) {
        is VisualDrawOp.Save -> "save"
        is VisualDrawOp.Restore -> "restore"
        is VisualDrawOp.Concat -> "concat"
        is VisualDrawOp.Clip -> "clip"
        is VisualDrawOp.DrawPath -> "path"
        is VisualDrawOp.Label -> "label"
    }

    private fun VisualPathCommand.codeString(): String = when (this) {
        is VisualPathCommand.MoveTo -> "M"
        is VisualPathCommand.LineTo -> "L"
        is VisualPathCommand.CubicTo -> "C"
        is VisualPathCommand.Close -> "Z"
    }

    private fun VisualPathCommand.numbers(): List<Double> = when (this) {
        is VisualPathCommand.MoveTo -> listOf(x, y)
        is VisualPathCommand.LineTo -> listOf(x, y)
        is VisualPathCommand.CubicTo -> listOf(x1, y1, x2, y2, x, y)
        is VisualPathCommand.Close -> emptyList()
    }

    // ---------------------------------------------------------------- 学科 oracle

    @Test
    fun `ratio bar subject oracle holds three to one and one to one from the drawn geometry`() {
        val loaded = load("screenshot1-ratio-bar")
        val bars = loaded.subject.arrayAt("bars").map { it.jsonObject }
        val widths = bars.map { bar -> barWidth(loaded.parsed, bar.num("pathIndex").toInt()) }
        val expect = loaded.subject.objectAt("expect")
        for ((index, bar) in bars.withIndex()) {
            assertEquals("bar[$index].left", bar.num("leftDu"), widths[index].left, 0.0)
            assertEquals("bar[$index].right", bar.num("rightDu"), widths[index].right, 0.0)
        }
        assertEquals(expect.num("ratioAOverB"), (widths[0].right - widths[0].left) / (widths[1].right - widths[1].left), 0.0)
        assertEquals(expect.num("ratioCOverD"), (widths[2].right - widths[2].left) / (widths[3].right - widths[3].left), 0.0)
        for (bar in bars) {
            assertEquals("bar height", expect.num("barHeightDu"), barHeight(loaded.parsed, bar.num("pathIndex").toInt()), 0.0)
            assertEquals("bar left edge", expect.num("leftEdgeDu"), barLeft(loaded.parsed, bar.num("pathIndex").toInt()), 0.0)
        }
    }

    @Test
    fun `fx area subject oracle recomputes three f0 h over eight by shoelace`() {
        val loaded = load("screenshot2-fx-area")
        val mapping = loaded.subject.objectAt("mapping")
        val origin = mapping.arrayAt("originDu").map { it.jsonPrimitive.content.toDouble() }
        val originX = origin[0]
        val originY = origin[1]
        val scaleX = mapping.num("duPerUnitX")
        val scaleY = mapping.num("duPerUnitY")
        val polygon = polygonVertices(loaded.parsed, loaded.subject.num("areaPathIndex").toInt())
        // 闭式：x=h/2 处面积 = 3F0h/8。梯形顶点独立换算到单位坐标。
        val unit = polygon.map { listOf((it[0] - originX) / scaleX, (originY - it[1]) / scaleY) }
        val expectedCorners = loaded.subject.arrayAt("areaCornersUnit").map { corner ->
            corner.jsonArray.map { it.jsonPrimitive.content.toDouble() }
        }
        for ((index, corner) in expectedCorners.withIndex()) {
            assertEquals("corner[$index].x", corner[0], unit[index][0], 1e-9)
            assertEquals("corner[$index].y", corner[1], unit[index][1], 1e-9)
        }
        var twiceArea = 0.0
        for (i in unit.indices) {
            val j = (i + 1) % unit.size
            twiceArea += unit[i][0] * unit[j][1] - unit[j][0] * unit[i][1]
        }
        val expect = loaded.subject.objectAt("expect")
        assertEquals(
            "area under F(x)=F0(1-x/h) on [0, h/2]",
            expect.num("areaAtHalfH"),
            abs(twiceArea) / 2.0,
            expect.num("tolerance"),
        )
    }

    @Test
    fun `force diagram subject oracle holds directions from the drawn shafts`() {
        val loaded = load("screenshot5-force-diagram")
        val arrows = loaded.subject.arrayAt("arrows").map { it.jsonObject }
        val expect = loaded.subject.objectAt("expect")
        val tolerance = expect.num("tolerance")
        for (arrow in arrows) {
            val geometry = arrowGeometry(loaded.parsed, arrow.num("pathIndex").toInt())
            val expectedUnit = arrow.arrayAt("expectUnit").map { it.jsonPrimitive.content.toDouble() }
            assertEquals("${arrow.str("name")} dir.x", expectedUnit[0], geometry.unit[0], tolerance)
            assertEquals("${arrow.str("name")} dir.y", expectedUnit[1], geometry.unit[1], tolerance)
            val expectedTip = arrow.arrayAt("tipDu").map { it.jsonPrimitive.content.toDouble() }
            assertEquals("${arrow.str("name")} tip.x", expectedTip[0], geometry.tip[0], tolerance)
            assertEquals("${arrow.str("name")} tip.y", expectedTip[1], geometry.tip[1], tolerance)
        }
        // 摩擦与相对运动趋势相反（方向按实际条件标注，不照搬横箭头误导）。
        if (expect.bool("frictionOpposesMotion")) {
            val motion = loaded.subject.arrayAt("motionTendency").map { it.jsonPrimitive.content.toDouble() }
            val friction = arrows.first { it.str("name") == "f" }
            val unit = arrowGeometry(loaded.parsed, friction.num("pathIndex").toInt()).unit
            assertEquals("friction dot motion", -1.0, unit[0] * motion[0] + unit[1] * motion[1], tolerance)
        }
    }

    @Test
    fun `plug stages subject oracle maps markers to zero half and full height`() {
        val loaded = load("screenshot6-plug-stages")
        val mapping = loaded.subject.objectAt("mapping")
        val x0 = mapping.num("x0Du")
        val duPerUnit = mapping.num("duPerUnit")
        val stages = loaded.subject.arrayAt("stagePathIndices").map { it.jsonPrimitive.content.toInt() }
        val expectedCenters = loaded.subject.arrayAt("stageCentersDu").map { it.jsonPrimitive.content.toDouble() }
        val units = stages.map { index -> (stageCenterX(loaded.parsed, index) - x0) / duPerUnit }
        val expect = loaded.subject.objectAt("expect")
        for ((index, expectedUnit) in expect.arrayAt("stageUnits").map { it.jsonPrimitive.content.toDouble() }.withIndex()) {
            assertEquals("stage[$index] in units", expectedUnit, units[index], expect.num("tolerance"))
        }
        for ((index, expectedCenter) in expectedCenters.withIndex()) {
            assertEquals("stage[$index] in du", expectedCenter, stageCenterX(loaded.parsed, stages[index]), expect.num("tolerance"))
        }
        // 相对位移线性：h/2 − 0 == h − h/2。
        assertEquals("equal stage spacing", units[1] - units[0], units[2] - units[1], expect.num("tolerance"))
        // 公式不显示原始 TeX：声明文本无 TeX 标记（^、\）。
        if (expect.bool("noRawTex")) {
            val labels = loaded.fixture.objectAt("labels")
            for ((contentId, text) in labels) {
                val value = text.jsonPrimitive.content
                assertFalse("$contentId must not carry raw TeX (backslash)", value.contains('\\'))
                assertFalse("$contentId must not carry raw TeX (superscript caret)", value.contains('^'))
            }
        }
        // 无裁切：x 全部在 [0, 视口宽]、y 全部在 [0, 视口高]（按坐标位配对检查）。
        if (expect.bool("noClipping")) {
            val width = loaded.viewport.widthDu.toDouble()
            val height = loaded.viewport.heightDu.toDouble()
            for (op in loaded.parsed.ops) {
                if (op is VisualDrawOp.DrawPath) {
                    for (command in op.path) {
                        val numbers = command.numbers()
                        for ((i, value) in numbers.withIndex()) {
                            val bound = if (i % 2 == 0) width else height
                            assertTrue("coordinate $value inside viewport bound $bound", value in 0.0..bound)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `wave trajectory subject oracle recomputes superposition and sampling from declared params`() {
        val loaded = load("combo-wave-trajectory")
        val wave = loaded.subject.objectAt("wave")
        val midY = wave.num("midYDu")
        val xStart = wave.num("xStartDu")
        val step = wave.num("stepDu")
        val a1 = wave.num("amplitude1")
        val lambda1 = wave.num("wavelength1Du")
        val a2 = wave.num("amplitude2")
        val lambda2 = wave.num("wavelength2Du")
        val tolerance = wave.num("tolerance")
        val wavePath = pathOp(loaded.parsed, wave.num("pathIndex").toInt())
        for (checkX in wave.arrayAt("checkXDu").map { it.jsonPrimitive.content.toDouble() }) {
            val index = ((checkX - xStart) / step).toInt()
            val drawnY = when (val command = wavePath.path[index]) {
                is VisualPathCommand.MoveTo -> command.y
                is VisualPathCommand.LineTo -> command.y
                else -> error("wave sample command $index must be M/L")
            }
            val expectedY = midY -
                (a1 * sin(2.0 * Math.PI * checkX / lambda1) + a2 * sin(2.0 * Math.PI * checkX / lambda2))
            assertEquals("wave vertex at x=$checkX", expectedY, drawnY, tolerance)
        }
        val trajectory = loaded.subject.objectAt("trajectory")
        val p0 = trajectory.arrayAt("p0").map { it.jsonPrimitive.content.toDouble() }
        val v = trajectory.arrayAt("vDuPerT").map { it.jsonPrimitive.content.toDouble() }
        val halfG = trajectory.num("halfGDuPerT2")
        val pathIndices = trajectory.arrayAt("pathIndices").map { it.jsonPrimitive.content.toInt() }
        val tValues = trajectory.arrayAt("tValues").map { it.jsonPrimitive.content.toDouble() }
        for ((i, t) in tValues.withIndex()) {
            val center = diamondCenter(loaded.parsed, pathIndices[i])
            val expectedX = p0[0] + v[0] * t
            val expectedY = p0[1] + v[1] * t - halfG * t * t
            assertEquals("trajectory x at t=$t", expectedX, center[0], trajectory.num("tolerance"))
            assertEquals("trajectory y at t=$t", expectedY, center[1], trajectory.num("tolerance"))
        }
    }

    // ---------------------------------------------------------------- 负例（非空洞性）

    @Test
    fun `a one unit geometry tamper in every fixture is caught by the rendering oracle`() {
        val tampers = mapOf(
            "screenshot1-ratio-bar" to Tamper(opIndex = 0, commandIndex = 1, coordinateIndex = 1, delta = 1.0),
            "screenshot2-fx-area" to Tamper(opIndex = 2, commandIndex = 2, coordinateIndex = 2, delta = 1.0),
            "screenshot5-force-diagram" to Tamper(opIndex = 1, commandIndex = 2, coordinateIndex = 1, delta = 1.0),
            "screenshot6-plug-stages" to Tamper(opIndex = 2, commandIndex = 0, coordinateIndex = 1, delta = 1.0),
            "combo-wave-trajectory" to Tamper(opIndex = 3, commandIndex = 2, coordinateIndex = 1, delta = 1.0),
        )
        for ((name, tamper) in tampers) {
            val loaded = load(name)
            val tamperedText = tamperPathCoordinate(loaded.drawOpsText, tamper)
            val tamperedDoc = when (val validation = VisualDrawOpsParser.parse(tamperedText, loaded.contentIds, loaded.viewport)) {
                is VisualDrawOpsValidation.Valid -> validation.document
                is VisualDrawOpsValidation.Invalid ->
                    throw AssertionError("${loaded.name}: tampered variant must still be a well-formed document: ${validation.code}")
            }
            assertFalse(
                "${loaded.name}: the rendering oracle must reject a one-unit geometry tamper",
                matchesOracle(loaded, tamperedDoc),
            )
        }
    }

    @Test
    fun `a label tamper fails only the rendering oracle while subject geometry still holds`() {
        val loaded = load("screenshot1-ratio-bar")
        val tamperedText = tamperLabelField(loaded.drawOpsText, opIndex = 4, field = "x", delta = 5.0)
        val tamperedDoc = when (val validation = VisualDrawOpsParser.parse(tamperedText, loaded.contentIds, loaded.viewport)) {
            is VisualDrawOpsValidation.Valid -> validation.document
            is VisualDrawOpsValidation.Invalid ->
                throw AssertionError("tampered label must still parse: ${validation.code}")
        }
        assertFalse("rendering oracle must catch a moved label", matchesOracle(loaded, tamperedDoc))
        // 学科断言只读杆几何——label 位移不影响 3:1 比例成立。
        val bars = loaded.subject.arrayAt("bars").map { it.jsonObject }
        val widthA = barWidth(tamperedDoc, bars[0].num("pathIndex").toInt())
        val widthB = barWidth(tamperedDoc, bars[1].num("pathIndex").toInt())
        assertEquals(3.0, (widthA.right - widthA.left) / (widthB.right - widthB.left), 0.0)
    }

    @Test
    fun `a wave vertex tamper fails only the subject oracle while rendering structure still holds`() {
        val loaded = load("combo-wave-trajectory")
        // 波形顶点坐标不进渲染 oracle（codes + codes 数量而已）——只坏 y 坐标时渲染结构仍匹配。
        val tamperedText = tamperPathCoordinate(
            loaded.drawOpsText,
            Tamper(opIndex = 0, commandIndex = 20, coordinateIndex = 2, delta = 1.0),
        )
        val tamperedDoc = when (val validation = VisualDrawOpsParser.parse(tamperedText, loaded.contentIds, loaded.viewport)) {
            is VisualDrawOpsValidation.Valid -> validation.document
            is VisualDrawOpsValidation.Invalid ->
                throw AssertionError("tampered wave vertex must still parse: ${validation.code}")
        }
        assertTrue("rendering structure must still match (wave coordinates are subject-owned)", matchesOracle(loaded, tamperedDoc))
        val wave = loaded.subject.objectAt("wave")
        val drawnY = (pathCommand(tamperedDoc, wave.num("pathIndex").toInt(), 20) as VisualPathCommand.LineTo).y
        val expectedY = wave.num("midYDu") -
            (wave.num("amplitude1") * sin(2.0 * Math.PI * 60.0 / wave.num("wavelength1Du")) +
                wave.num("amplitude2") * sin(2.0 * Math.PI * 60.0 / wave.num("wavelength2Du")))
        assertFalse(
            "subject oracle must catch a tampered superposition vertex",
            abs(expectedY - drawnY) <= wave.num("tolerance"),
        )
    }

    @Test
    fun `malformed fixture variants are rejected with typed codes`() {
        val loaded = load("screenshot1-ratio-bar")
        val wrongVersion = loaded.drawOpsText.replace("\"drawSchemaVersion\":1", "\"drawSchemaVersion\":2")
        assertEquals(
            VisualDrawOpsParser.CODE_SCHEMA_VERSION,
            (VisualDrawOpsParser.parse(wrongVersion, loaded.contentIds, loaded.viewport) as VisualDrawOpsValidation.Invalid).code,
        )
        val duplicateKey = loaded.drawOpsText.replace(
            "\"background\":\"#ffffff\"",
            "\"background\":\"#ffffff\",\"background\":\"#ffffff\"",
        )
        assertEquals(
            VisualDrawOpsParser.CODE_DUP_KEY,
            (VisualDrawOpsParser.parse(duplicateKey, loaded.contentIds, loaded.viewport) as VisualDrawOpsValidation.Invalid).code,
        )
    }

    // ---------------------------------------------------------------- 学科几何提取

    private data class Tamper(val opIndex: Int, val commandIndex: Int, val coordinateIndex: Int, val delta: Double)

    private fun pathOp(document: VisualDrawOpsDocument, index: Int): VisualDrawOp.DrawPath =
        document.ops[index] as? VisualDrawOp.DrawPath
            ?: error("ops[$index] is not a path op")

    private fun pathCommand(document: VisualDrawOpsDocument, opIndex: Int, commandIndex: Int): VisualPathCommand =
        pathOp(document, opIndex).path[commandIndex]

    private data class Span(val left: Double, val right: Double)

    private fun barWidth(document: VisualDrawOpsDocument, index: Int): Span {
        val xs = pathOp(document, index).path.flatMap { it.numbers().filterIndexed { i, _ -> i % 2 == 0 } }
        return Span(xs.min(), xs.max())
    }

    private fun barHeight(document: VisualDrawOpsDocument, index: Int): Double {
        val ys = pathOp(document, index).path.flatMap { it.numbers().filterIndexed { i, _ -> i % 2 == 1 } }
        return ys.max() - ys.min()
    }

    private fun barLeft(document: VisualDrawOpsDocument, index: Int): Double = barWidth(document, index).left

    /** 闭合多边形顶点（M + L 链；Close 只闭合回 M，不引入新顶点）。 */
    private fun polygonVertices(document: VisualDrawOpsDocument, index: Int): List<List<Double>> =
        pathOp(document, index).path.mapNotNull { command ->
            when (command) {
                is VisualPathCommand.MoveTo -> listOf(command.x, command.y)
                is VisualPathCommand.LineTo -> listOf(command.x, command.y)
                is VisualPathCommand.Close -> null
                is VisualPathCommand.CubicTo -> error("area polygon must be M/L only")
            }
        }

    /**
     * 箭头方向（约定无关的几何读法）：方向 = 从**杆四边形重心**指向**头三角尖端**的单位
     * 向量；尖端 = 头三角中离杆重心最远的顶点。不依赖杆角的手写角序（横/竖箭头的角序
     * 环绕方向不同），两条信息都从绘制几何实测。
     */
    private fun arrowGeometry(document: VisualDrawOpsDocument, index: Int): ArrowGeometry {
        val commands = pathOp(document, index).path
        val shaft = commands.subList(0, 4).map { it.numbers() }
        val centroidX = shaft.sumOf { it[0] } / shaft.size
        val centroidY = shaft.sumOf { it[1] } / shaft.size
        val head = commands.subList(5, 8).map { it.numbers() }
        val tip = head.maxBy { point ->
            val dx = point[0] - centroidX
            val dy = point[1] - centroidY
            dx * dx + dy * dy
        }
        val dx = tip[0] - centroidX
        val dy = tip[1] - centroidY
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        return ArrowGeometry(tip = tip, unit = listOf(dx / length, dy / length))
    }

    private class ArrowGeometry(val tip: List<Double>, val unit: List<Double>)

    private fun stageCenterX(document: VisualDrawOpsDocument, index: Int): Double {
        val commands = pathOp(document, index).path
        val xs = commands.flatMap { it.numbers().filterIndexed { i, _ -> i % 2 == 0 } }
        return xs.sum() / xs.size
    }

    private fun diamondCenter(document: VisualDrawOpsDocument, index: Int): List<Double> {
        val commands = pathOp(document, index).path
        val c0 = commands[0].numbers()
        val c1 = commands[1].numbers()
        val c2 = commands[2].numbers()
        val c3 = commands[3].numbers()
        return listOf((c0[0] + c2[0]) / 2.0, (c1[1] + c3[1]) / 2.0)
    }

    // ---------------------------------------------------------------- JSON 工具

    private fun matchesOracle(loaded: Loaded, document: VisualDrawOpsDocument): Boolean = try {
        assertMatchesOracle(loaded, document, context = "${loaded.name} (probe)")
        true
    } catch (_: AssertionError) {
        false
    }

    private fun tamperPathCoordinate(text: String, tamper: Tamper): String {
        val root = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        val ops = (root["ops"] as JsonArray).toMutableList()
        val op = ops[tamper.opIndex].jsonObject.toMutableMap()
        val path = (op["path"] as JsonArray).toMutableList()
        val command = path[tamper.commandIndex].jsonArray.toMutableList()
        val oldValue = command[tamper.coordinateIndex].jsonPrimitive.content.toDouble()
        command[tamper.coordinateIndex] = JsonPrimitive(oldValue + tamper.delta)
        path[tamper.commandIndex] = JsonArray(command)
        op["path"] = JsonArray(path)
        ops[tamper.opIndex] = JsonObject(op)
        root["ops"] = JsonArray(ops)
        return JsonObject(root).toString()
    }

    private fun tamperLabelField(text: String, opIndex: Int, field: String, delta: Double): String {
        val root = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        val ops = (root["ops"] as JsonArray).toMutableList()
        val op = ops[opIndex].jsonObject.toMutableMap()
        val oldValue = op.getValue(field).jsonPrimitive.content.toDouble()
        op[field] = JsonPrimitive(oldValue + delta)
        ops[opIndex] = JsonObject(op)
        root["ops"] = JsonArray(ops)
        return JsonObject(root).toString()
    }

}

// JSON 工具（文件级私有扩展，从类成员上提）：嵌套类 Loaded 非 inner，不能以外层类的成员
// 扩展作接收者（门禁第 1 轮 :85 "Outer class ... cannot be used as receiver"）。jsonObject/
// jsonArray 是**扩展属性**——先前误写成 `fun JsonElement.jsonObject: JsonObject`（函数语法
// 不允许无参列表，:593/:595 "Syntax error: Expecting '('"，并连锁产生全文件
// "Function invocation 'jsonObject()' expected"）。与 kotlinx.serialization 自带同名扩展的
// 差异仅在校验失败消息；本文件未 import 那两个同名扩展，无解析歧义。
private val JsonElement.jsonObject: JsonObject
    get() = this as? JsonObject ?: error("expected JSON object")

private val JsonElement.jsonArray: JsonArray
    get() = this as? JsonArray ?: error("expected JSON array")

private fun JsonObject.objectAt(key: String): JsonObject = getValue(key).jsonObject

private fun JsonObject.arrayAt(key: String): JsonArray = getValue(key).jsonArray

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.num(key: String): Double = getValue(key).jsonPrimitive.content.toDouble()

private fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.content == "true"
