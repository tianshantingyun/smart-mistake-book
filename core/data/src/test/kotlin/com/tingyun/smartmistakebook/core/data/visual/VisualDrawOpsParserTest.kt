package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * [VisualDrawOpsParser] 的 JVM 门（批次 29 T2；S-04 "序列化往返"与"无函数专项断言"的
 * 可执行判据）。
 *
 * **不变量**：不实例化 QuickJS、不读 JS 资产、不触 android 符号——解析器与序列化器都是
 * 纯 JSON ↔ 类型化文档的函数，全部用例在无原生库的 JVM 门执行。
 *
 * 覆盖映射（任务书三项）：
 * 1. **往返逐位相等**：`DrawOps → JSON → DrawOps` 的每个浮点（矩阵、路径坐标、描边、
 *    alpha、viewBox）按 [Double.toRawBits] 比较——**不用 epsilon**（epsilon 会把
 *    "末位被改写"这种真实回归吞掉）；再序列化文本 byte 级相同。
 * 2. **负向矩阵**：缺字段（根/op/stroke/label/hitRegion）、错类型（字符串数字、数组形状）、
 *    坏版本（0/2）、超大数组（ops 1025 / hitRegions 129 / dash 33 / 命令 4097）。
 * 3. **Rendered 入口**：`drawCommandsJson == null`、外壳版本不符、正常解析（无 viewport
 *    时不做视口回显——这正是回放入口与引擎提交门的语义差）。
 *
 * 先红后绿：本文件先于 [VisualDrawOpsParser] 的实现落盘（往返用例在解析器缺失时编译不
 * 过，负向用例在实现返回 Valid 时全红）；本环境不运行 Gradle，执行归双门（本结果登记为
 * UNVERIFIED）。
 */
class VisualDrawOpsParserTest {

    private val viewport = VisualViewport(360, 240)
    private val frozen = setOf("c1")

    // ------------------------------------------------------------ 夹具

    /**
     * 往返夹具：刻意选用"toString 会丢位"的值（0.1/1/3/π/1e-300/-0.0）——若序列化器
     * 走 Float 中转或格式化截断，逐位断言必红。
     */
    private fun fixtureDocument(): VisualDrawOpsDocument = VisualDrawOpsDocument(
        drawSchemaVersion = 1,
        viewBox = VisualDrawOpsViewBox(0.0, 0.0, 360.0, 240.0),
        background = "#ffffff",
        ops = listOf(
            VisualDrawOp.Save,
            VisualDrawOp.Concat(
                VisualMatrix(a = 1.0, b = 0.5, c = -0.25, d = 2.0, e = 10.0, f = -20.0),
            ),
            VisualDrawOp.Clip(
                path = listOf(
                    VisualPathCommand.MoveTo(0.0, 0.0),
                    VisualPathCommand.LineTo(360.0, 0.0),
                    VisualPathCommand.LineTo(360.0, 240.0),
                    VisualPathCommand.Close,
                ),
                rule = VisualFillRule.EVEN_ODD,
            ),
            VisualDrawOp.DrawPath(
                path = listOf(
                    VisualPathCommand.MoveTo(0.1, 0.2),
                    VisualPathCommand.LineTo(1.0 / 3.0, 2.0 / 3.0),
                    VisualPathCommand.CubicTo(
                        x1 = 1e-7,
                        y1 = 12345.6789,
                        x2 = -0.0,
                        y2 = 3.141592653589793,
                        x = 1.0E-300,
                        y = 0.5,
                    ),
                    VisualPathCommand.Close,
                ),
                fill = "#12345678",
                stroke = VisualStrokeSpec(
                    width = 2.5,
                    cap = VisualStrokeCap.ROUND,
                    join = VisualStrokeJoin.BEVEL,
                    miterLimit = 3.0,
                    dash = listOf(4.0, 2.5, 0.1, 3.0),
                ),
                alpha = 0.3333333333333333,
            ),
            VisualDrawOp.Label(
                contentId = "c1",
                x = 115.0,
                y = 63.0,
                rotationDeg = 15.5,
                hAlign = VisualTextHAlign.CENTER,
                vAlign = VisualTextVAlign.MIDDLE,
                color = "#26354a",
                alpha = 0.5,
            ),
            VisualDrawOp.Restore,
        ),
        hitRegions = listOf(
            VisualDrawHitRegion(
                id = "h1",
                path = listOf(
                    VisualPathCommand.MoveTo(1.0, 1.0),
                    VisualPathCommand.LineTo(2.0, 2.0),
                    VisualPathCommand.Close,
                ),
                rule = VisualFillRule.NONZERO,
            ),
        ),
        // pathCommands = clip 4 + drawPath 4 + hitRegion 3 = 11（解析器复算项）。
        stats = VisualDrawOpsStats(nodes = 3, pathCommands = 11, labels = 1, ops = 6),
    )

    private fun fixtureText(): String = VisualDrawOpsParser.serialize(fixtureDocument())

    /**
     * 可拼装的文档文本：`include` 控制键的存在性（缺字段负向用）。
     *
     * `ops` / `hitRegions` 是**字段原文**，数组形态要自带 `[ ]`——故意不替调用方补括号，
     * 这样"字段不是数组"（`{}`）这个错型用例才表达得出来；传单个 op/region 的负向用例
     * 必须自己写成 `"[$element]"`。
     */
    private fun documentText(
        schemaVersion: String = "1",
        viewBox: String = """{"x":0,"y":0,"w":360,"h":240}""",
        background: String = "null",
        ops: String = "[]",
        hitRegions: String = "[]",
        stats: String = """{"nodes":0,"pathCommands":0,"labels":0,"ops":0}""",
        include: Set<String> = setOf("drawSchemaVersion", "viewBox", "background", "ops", "hitRegions", "stats"),
    ): String {
        val parts = ArrayList<String>(6)
        if ("drawSchemaVersion" in include) parts.add("\"drawSchemaVersion\":$schemaVersion")
        if ("viewBox" in include) parts.add("\"viewBox\":$viewBox")
        if ("background" in include) parts.add("\"background\":$background")
        if ("ops" in include) parts.add("\"ops\":$ops")
        if ("hitRegions" in include) parts.add("\"hitRegions\":$hitRegions")
        if ("stats" in include) parts.add("\"stats\":$stats")
        return parts.joinToString(separator = ",", prefix = "{", postfix = "}")
    }

    private fun codeOf(
        text: String,
        frozenContentIds: Set<String> = frozen,
        expectedViewBox: VisualViewport? = viewport,
    ): String {
        val result = VisualDrawOpsParser.parse(text, frozenContentIds, expectedViewBox)
        if (result is VisualDrawOpsValidation.Valid) {
            fail("expected a rejection, got a valid document")
        }
        return (result as VisualDrawOpsValidation.Invalid).code
    }

    private fun parseValid(
        text: String,
        frozenContentIds: Set<String> = frozen,
        expectedViewBox: VisualViewport? = viewport,
    ): VisualDrawOpsDocument = when (val result = VisualDrawOpsParser.parse(text, frozenContentIds, expectedViewBox)) {
        is VisualDrawOpsValidation.Valid -> result.document
        is VisualDrawOpsValidation.Invalid -> throw AssertionError("${result.code}: ${result.message}")
    }

    // ------------------------------------------------------------ 往返（逐位）

    @Test
    fun `draw ops survive a json round trip with every number bit for bit`() {
        val original = fixtureDocument()
        val text = VisualDrawOpsParser.serialize(original)
        val reparsed = parseValid(text)

        assertDocumentBitwiseEqual(original, reparsed)
        // 第二圈：文本也必须稳定（固定键序 + 同值 toString → byte 级相同）。
        assertEquals(text, VisualDrawOpsParser.serialize(reparsed))
    }

    @Test
    fun `a compiler shaped text round trips through the typed document`() {
        // 编译器输出形态（整数不带小数点、"alpha":1、dash:null），验证解析器不要求
        // Kotlin 风格 "1.0" 才能读——宽松只允许在数字书写上，不允许缺字段/错类型上。
        val text = documentText(
            ops = "[" +
                """{"op":"save"},""" +
                """{"op":"concat","m":[2,0,0,2,10,20]},""" +
                """{"op":"path","path":[["M",0,0],["L",100,0],["L",100,50],["Z"]],""" +
                """"fill":"#ff0000","stroke":{"width":2,"cap":"round","join":"miter",""" +
                """"miterLimit":4,"dash":null},"alpha":1},""" +
                """{"op":"restore"}]""",
            stats = """{"nodes":2,"pathCommands":4,"labels":0,"ops":4}""",
        )

        val first = parseValid(text)
        val rewritten = VisualDrawOpsParser.serialize(first)
        val second = parseValid(rewritten)

        assertDocumentBitwiseEqual(first, second)
        assertEquals(rewritten, VisualDrawOpsParser.serialize(second))
        assertEquals(1.0, (first.ops[2] as VisualDrawOp.DrawPath).alpha, 0.0)
    }

    // ------------------------------------------------------------ 负向：缺字段

    @Test
    fun `a missing root field is rejected with the missing field code`() {
        for (key in listOf("drawSchemaVersion", "viewBox", "background", "ops", "hitRegions", "stats")) {
            val text = documentText(include = setOf("drawSchemaVersion", "viewBox", "background", "ops", "hitRegions", "stats") - key)
            assertEquals("missing $key", VisualDrawOpsParser.CODE_MISSING_FIELD, codeOf(text))
        }
    }

    @Test
    fun `a missing op field is rejected instead of defaulting it`() {
        val pathWithoutAlpha = """{"op":"path","path":[["M",0,0]],"fill":"#000000","stroke":null}"""
        assertEquals(
            VisualDrawOpsParser.CODE_MISSING_FIELD,
            codeOf(documentText(ops = "[$pathWithoutAlpha]")),
        )
        val labelWithoutAlpha =
            """{"op":"label","contentId":"c1","x":1,"y":2,"rotationDeg":0,""" +
                """"hAlign":"left","vAlign":"top","color":"#000000"}"""
        assertEquals(
            VisualDrawOpsParser.CODE_MISSING_FIELD,
            codeOf(documentText(ops = "[$labelWithoutAlpha]")),
        )
    }

    @Test
    fun `a missing stroke field is rejected instead of defaulting it`() {
        // 旧行为把缺省 cap 当 butt、缺省 dash 当 null——残缺文档会静默画出另一种描边。
        val strokeWithoutDash = """{"op":"path","path":[["M",0,0]],"fill":"#000000",""" +
            """"stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4},"alpha":1}"""
        assertEquals(
            VisualDrawOpsParser.CODE_MISSING_FIELD,
            codeOf(documentText(ops = "[$strokeWithoutDash]")),
        )
        val strokeWithoutCap = """{"op":"path","path":[["M",0,0]],"fill":"#000000",""" +
            """"stroke":{"width":1,"join":"miter","miterLimit":4,"dash":null},"alpha":1}"""
        assertEquals(
            VisualDrawOpsParser.CODE_MISSING_FIELD,
            codeOf(documentText(ops = "[$strokeWithoutCap]")),
        )
    }

    @Test
    fun `a missing hit region field is rejected`() {
        val regionWithoutRule = """{"id":"h1","path":[["M",0,0]]}"""
        assertEquals(
            VisualDrawOpsParser.CODE_MISSING_FIELD,
            codeOf(documentText(hitRegions = "[$regionWithoutRule]")),
        )
    }

    // ------------------------------------------------------------ 负向：错类型

    @Test
    fun `a string where a number belongs is rejected`() {
        assertEquals(
            "DRAWOPS_TYPE",
            codeOf(documentText(schemaVersion = "\"1\"")),
        )
        val stringAlpha = """{"op":"path","path":[["M",0,0]],"fill":"#000000","stroke":null,"alpha":"1"}"""
        assertEquals("DRAWOPS_TYPE", codeOf(documentText(ops = "[$stringAlpha]")))
        assertEquals(
            "DRAWOPS_TYPE",
            codeOf(documentText(viewBox = """{"x":0,"y":0,"w":"360","h":240}""")),
        )
    }

    @Test
    fun `a wrongly shaped field is rejected`() {
        assertEquals("DRAWOPS_TYPE", codeOf(documentText(ops = "{}")))
        assertEquals("DRAWOPS_TYPE", codeOf(documentText(hitRegions = "{}")))
        val fiveEntryMatrix = """{"op":"concat","m":[1,0,0,1,0]}"""
        assertEquals("DRAWOPS_TYPE", codeOf(documentText(ops = "[$fiveEntryMatrix]")))
    }

    // ------------------------------------------------------------ 负向：版本

    @Test
    fun `a wrong schema version is rejected with its own code`() {
        for (version in listOf("0", "2", "999")) {
            assertEquals(
                "version $version",
                VisualDrawOpsParser.CODE_SCHEMA_VERSION,
                codeOf(documentText(schemaVersion = version)),
            )
        }
    }

    // ------------------------------------------------------------ 负向：超大数组

    @Test
    fun `oversized arrays are rejected under their own budget codes`() {
        val tooManyOps = (0 until 1025).joinToString(",") { """{"op":"save"}""" }
        assertEquals("DRAWOPS_BUDGET_OPS", codeOf(documentText(ops = "[$tooManyOps]")))

        val tooManyRegions = (0 until 129).joinToString(",") { index ->
            """{"id":"h$index","path":[["M",0,0],["L",1,1]],"rule":"nonzero"}"""
        }
        assertEquals("DRAWOPS_BUDGET_HIT_REGIONS", codeOf(documentText(hitRegions = "[$tooManyRegions]")))

        val oversizedDash = (0 until 33).joinToString(",") { "1" }
        val dashOp = """{"op":"path","path":[["M",0,0]],"fill":"#000000",""" +
            """"stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[$oversizedDash]},"alpha":1}"""
        assertEquals("DRAWOPS_DASH", codeOf(documentText(ops = "[$dashOp]")))

        val commands = ArrayList<String>(4097)
        commands.add("""["M",0,0]""")
        for (i in 1..4096) commands.add("""["L",$i,0]""")
        val oversizedPath = """{"op":"path","path":[${commands.joinToString(",")}],""" +
            """"fill":"#000000","stroke":null,"alpha":1}"""
        assertEquals("DRAWOPS_BUDGET_COMMANDS", codeOf(documentText(ops = "[$oversizedPath]")))
    }

    @Test
    fun `unknown fields and unknown ops keep their codes`() {
        val unknownField = documentText().replaceFirst("{", "{\"oops\":1,")
        assertEquals("DRAWOPS_UNKNOWN_FIELD", codeOf(unknownField))
        assertEquals("DRAWOPS_UNKNOWN_OP", codeOf(documentText(ops = """[{"op":"rotate","deg":30}]""")))
    }

    // ------------------------------------------------------------ Rendered 入口

    @Test
    fun `a rendered without draw commands is rejected`() {
        val rendered = VisualExecutionResult.Rendered(
            sceneJson = "{}",
            sceneSchemaVersion = 1,
            contentIds = frozen,
        )
        val result = VisualDrawOpsParser.parse(rendered)
        assertEquals(
            VisualDrawOpsParser.CODE_MISSING_COMMANDS,
            (result as VisualDrawOpsValidation.Invalid).code,
        )
    }

    @Test
    fun `a rendered shell version mismatch is rejected before parsing`() {
        val rendered = VisualExecutionResult.Rendered(
            sceneJson = "{}",
            sceneSchemaVersion = 1,
            contentIds = frozen,
            drawCommandsJson = fixtureText(),
            drawSchemaVersion = 2,
        )
        val result = VisualDrawOpsParser.parse(rendered)
        assertEquals(
            VisualDrawOpsParser.CODE_SCHEMA_VERSION,
            (result as VisualDrawOpsValidation.Invalid).code,
        )
    }

    @Test
    fun `a rendered parses without a viewport because the engine gate already checked it`() {
        // 回放入口语义：Rendered 不携 viewport（S4 锁窗口记录），因此 viewBox 只验形状/
        // 有限性；视口回显门只在引擎提交时（parse(text, …, viewport)）执行。
        val rendered = VisualExecutionResult.Rendered(
            sceneJson = "{}",
            sceneSchemaVersion = 1,
            contentIds = frozen,
            drawCommandsJson = fixtureText(),
        )
        val document = when (val result = VisualDrawOpsParser.parse(rendered)) {
            is VisualDrawOpsValidation.Valid -> result.document
            is VisualDrawOpsValidation.Invalid -> throw AssertionError("${result.code}: ${result.message}")
        }
        assertDocumentBitwiseEqual(fixtureDocument(), document)
    }

    @Test
    fun `a rendered label outside the frozen ids is still rejected`() {
        val rendered = VisualExecutionResult.Rendered(
            sceneJson = "{}",
            sceneSchemaVersion = 1,
            contentIds = setOf("other"),
            drawCommandsJson = fixtureText(),
        )
        val result = VisualDrawOpsParser.parse(rendered)
        assertEquals("DRAWOPS_REF", (result as VisualDrawOpsValidation.Invalid).code)
    }

    // ------------------------------------------------------------ 逐位比较

    private fun assertDocumentBitwiseEqual(expected: VisualDrawOpsDocument, actual: VisualDrawOpsDocument) {
        assertEquals(expected.drawSchemaVersion, actual.drawSchemaVersion)
        assertEquals(expected.background, actual.background)
        assertDoubleBits(expected.viewBox.x, actual.viewBox.x, "viewBox.x")
        assertDoubleBits(expected.viewBox.y, actual.viewBox.y, "viewBox.y")
        assertDoubleBits(expected.viewBox.w, actual.viewBox.w, "viewBox.w")
        assertDoubleBits(expected.viewBox.h, actual.viewBox.h, "viewBox.h")
        assertEquals("ops.size", expected.ops.size, actual.ops.size)
        for (index in expected.ops.indices) {
            assertOpBitwiseEqual(expected.ops[index], actual.ops[index], "ops[$index]")
        }
        assertEquals("hitRegions.size", expected.hitRegions.size, actual.hitRegions.size)
        for (index in expected.hitRegions.indices) {
            val expectedRegion = expected.hitRegions[index]
            val actualRegion = actual.hitRegions[index]
            assertEquals(expectedRegion.id, actualRegion.id)
            assertEquals(expectedRegion.rule, actualRegion.rule)
            assertCommandsBitwiseEqual(expectedRegion.path, actualRegion.path, "hitRegions[$index].path")
        }
        assertEquals(expected.stats, actual.stats)
    }

    private fun assertOpBitwiseEqual(expected: VisualDrawOp, actual: VisualDrawOp, at: String) {
        when (expected) {
            is VisualDrawOp.Save -> assertEquals(at, VisualDrawOp.Save, actual)
            is VisualDrawOp.Restore -> assertEquals(at, VisualDrawOp.Restore, actual)
            is VisualDrawOp.Concat -> {
                val other = actual as? VisualDrawOp.Concat ?: run { fail("$at: expected concat, got $actual"); return }
                assertDoubleBits(expected.matrix.a, other.matrix.a, "$at.m.a")
                assertDoubleBits(expected.matrix.b, other.matrix.b, "$at.m.b")
                assertDoubleBits(expected.matrix.c, other.matrix.c, "$at.m.c")
                assertDoubleBits(expected.matrix.d, other.matrix.d, "$at.m.d")
                assertDoubleBits(expected.matrix.e, other.matrix.e, "$at.m.e")
                assertDoubleBits(expected.matrix.f, other.matrix.f, "$at.m.f")
            }

            is VisualDrawOp.Clip -> {
                val other = actual as? VisualDrawOp.Clip ?: run { fail("$at: expected clip, got $actual"); return }
                assertEquals("$at.rule", expected.rule, other.rule)
                assertCommandsBitwiseEqual(expected.path, other.path, "$at.path")
            }

            is VisualDrawOp.DrawPath -> {
                val other = actual as? VisualDrawOp.DrawPath
                    ?: run { fail("$at: expected path, got $actual"); return }
                assertEquals("$at.fill", expected.fill, other.fill)
                assertCommandsBitwiseEqual(expected.path, other.path, "$at.path")
                assertDoubleBits(expected.alpha, other.alpha, "$at.alpha")
                val expectedStroke = expected.stroke
                val actualStroke = other.stroke
                if (expectedStroke == null) {
                    assertNull("$at.stroke", actualStroke)
                } else {
                    if (actualStroke == null) {
                        fail("$at.stroke: expected a stroke, got null")
                        return
                    }
                    assertDoubleBits(expectedStroke.width, actualStroke.width, "$at.stroke.width")
                    assertEquals("$at.stroke.cap", expectedStroke.cap, actualStroke.cap)
                    assertEquals("$at.stroke.join", expectedStroke.join, actualStroke.join)
                    assertDoubleBits(expectedStroke.miterLimit, actualStroke.miterLimit, "$at.stroke.miterLimit")
                    val expectedDash = expectedStroke.dash
                    val actualDash = actualStroke.dash
                    if (expectedDash == null) {
                        assertNull("$at.stroke.dash", actualDash)
                    } else {
                        if (actualDash == null) {
                            fail("$at.stroke.dash: expected entries, got null")
                            return
                        }
                        assertEquals("$at.stroke.dash.size", expectedDash.size, actualDash.size)
                        for (i in expectedDash.indices) {
                            assertDoubleBits(expectedDash[i], actualDash[i], "$at.stroke.dash[$i]")
                        }
                    }
                }
            }

            is VisualDrawOp.Label -> {
                val other = actual as? VisualDrawOp.Label ?: run { fail("$at: expected label, got $actual"); return }
                assertEquals("$at.contentId", expected.contentId, other.contentId)
                assertDoubleBits(expected.x, other.x, "$at.x")
                assertDoubleBits(expected.y, other.y, "$at.y")
                assertDoubleBits(expected.rotationDeg, other.rotationDeg, "$at.rotationDeg")
                assertEquals("$at.hAlign", expected.hAlign, other.hAlign)
                assertEquals("$at.vAlign", expected.vAlign, other.vAlign)
                assertEquals("$at.color", expected.color, other.color)
                assertDoubleBits(expected.alpha, other.alpha, "$at.alpha")
            }
        }
    }

    private fun assertCommandsBitwiseEqual(
        expected: List<VisualPathCommand>,
        actual: List<VisualPathCommand>,
        at: String,
    ) {
        assertEquals("$at.size", expected.size, actual.size)
        for (index in expected.indices) {
            val expectedCommand = expected[index]
            val actualCommand = actual[index]
            val commandAt = "$at[$index]"
            when (expectedCommand) {
                is VisualPathCommand.MoveTo -> {
                    val other = actualCommand as? VisualPathCommand.MoveTo
                        ?: run { fail("$commandAt: expected moveTo"); return }
                    assertDoubleBits(expectedCommand.x, other.x, "$commandAt.x")
                    assertDoubleBits(expectedCommand.y, other.y, "$commandAt.y")
                }

                is VisualPathCommand.LineTo -> {
                    val other = actualCommand as? VisualPathCommand.LineTo
                        ?: run { fail("$commandAt: expected lineTo"); return }
                    assertDoubleBits(expectedCommand.x, other.x, "$commandAt.x")
                    assertDoubleBits(expectedCommand.y, other.y, "$commandAt.y")
                }

                is VisualPathCommand.CubicTo -> {
                    val other = actualCommand as? VisualPathCommand.CubicTo
                        ?: run { fail("$commandAt: expected cubicTo"); return }
                    assertDoubleBits(expectedCommand.x1, other.x1, "$commandAt.x1")
                    assertDoubleBits(expectedCommand.y1, other.y1, "$commandAt.y1")
                    assertDoubleBits(expectedCommand.x2, other.x2, "$commandAt.x2")
                    assertDoubleBits(expectedCommand.y2, other.y2, "$commandAt.y2")
                    assertDoubleBits(expectedCommand.x, other.x, "$commandAt.x")
                    assertDoubleBits(expectedCommand.y, other.y, "$commandAt.y")
                }

                VisualPathCommand.Close -> assertEquals(commandAt, VisualPathCommand.Close, actualCommand)
            }
        }
    }

    /** 逐位（raw bits）——不是 epsilon：-0.0 与 0.0、末位差 1 ULP 都必须红。 */
    private fun assertDoubleBits(expected: Double, actual: Double, at: String) {
        assertEquals(at, expected.toRawBits(), actual.toRawBits())
    }
}
