package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisualDrawOpsValidator] 的 JVM 纯函数负向矩阵（批次 27 T1；R1 §7.1 建议 1——
 * "DrawOps 原生校验器先写负向矩阵测试，再落实现……与 VisualJsFailureClassification
 * 同型可全 JVM 覆盖"）。
 *
 * **不变量**：不实例化 QuickJs、不读 JS 资产、不触发 `System.loadLibrary`——校验器是
 * 纯 JSON→类型化文档的函数，全部用例在无原生库的 JVM 门执行（真引擎上的字节级确定性
 * 与编译集成归仪器化门 `QuickJsSceneCompilerInstrumentedTest`；JS 侧行为由 Node 测试台
 * `tools/visual_compiler/run_tests.mjs` 覆盖，两侧词表同值镜像互钉）。
 *
 * 覆盖映射：词表（未知字段/op/命令）、预算（字节/ops/命令回放/命中区/节点）、有限性与
 * 边界（坐标/矩阵/alpha/颜色）、save-restore 配平、冻结引用、stats 复算、viewBox 回显。
 * 一个"fake 消费"用例演示任务书第 2 条的形态：painter（4E.2 后段）拿到的是类型化指令，
 * 不是裸字符串。
 */
class VisualDrawOpsValidatorTest {

    private val viewport = VisualViewport(360, 240)
    private val frozen = setOf("c1")

    private fun validate(text: String): VisualDrawOpsValidation =
        VisualDrawOpsValidator.validate(text, frozen, viewport)

    private fun invalidCode(result: VisualDrawOpsValidation): String {
        val invalid = result as VisualDrawOpsValidation.Invalid
        return invalid.code
    }

    /** 合法空文档（编译器对 root:null 场景的确切输出形状）。 */
    private fun emptyDoc(
        ops: String = "",
        hitRegions: String = "",
        nodes: Int = 0,
        pathCommands: Int = 0,
        labels: Int = 0,
    ): String =
        """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":null,"ops":[$ops],"hitRegions":[$hitRegions],""" +
            """"stats":{"nodes":$nodes,"pathCommands":$pathCommands,"labels":$labels,""" +
            """"ops":${if (ops.isBlank()) 0 else ops.split("{\"op\"").size - 1}}}"""

    /** 合法 label op（与 ECHO 夹具同参数：metrics w=10,h=12,baseline=9，anchor(120,60) 居中）。 */
    private fun labelOp(contentId: String = "c1", x: String = "115", y: String = "63"): String =
        """{"op":"label","contentId":"$contentId","x":$x,"y":$y,"rotationDeg":0,""" +
            """"hAlign":"center","vAlign":"middle","color":"#26354a","alpha":1}"""

    // ------------------------------------------------------------ 正向

    @Test
    fun `an empty compiled document parses into a typed document`() {
        val result = validate(emptyDoc())

        val document = (result as VisualDrawOpsValidation.Valid).document
        assertEquals(1, document.drawSchemaVersion)
        assertEquals(0.0, document.viewBox.x, 0.0)
        assertEquals(360.0, document.viewBox.w, 0.0)
        assertEquals(240.0, document.viewBox.h, 0.0)
        assertEquals(0, document.ops.size)
        assertEquals(0, document.hitRegions.size)
    }

    @Test
    fun `a fake consumer reads typed draw instructions`() {
        // 任务书第 2 条："本批只到指令序列并可 fake 消费"——消费形态即本用例的读取方式。
        val ops = "[" +
            """{"op":"save"},""" +
            """{"op":"concat","m":[2,0,0,2,10,20]},""" +
            """{"op":"path","path":[["M",0,0],["L",100,0],["L",100,50],["Z"]],""" +
            """"fill":"#ff0000","stroke":{"width":2,"cap":"round","join":"miter","miterLimit":4,"dash":null},"alpha":1},""" +
            """{"op":"restore"}]"""
        val text = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":"#ffffff","ops":$ops,"hitRegions":[],"stats":{"nodes":2,"pathCommands":4,"labels":0,"ops":4}}"""

        val document = (validate(text) as VisualDrawOpsValidation.Valid).document

        assertEquals(VisualDrawOp.Save, document.ops[0])
        val concat = document.ops[1] as VisualDrawOp.Concat
        assertEquals(VisualMatrix(a = 2.0, b = 0.0, c = 0.0, d = 2.0, e = 10.0, f = 20.0), concat.matrix)
        val path = document.ops[2] as VisualDrawOp.DrawPath
        assertEquals(4, path.path.size)
        assertEquals(VisualPathCommand.MoveTo(0.0, 0.0), path.path[0])
        assertEquals(VisualPathCommand.Close, path.path[3])
        assertEquals("#ff0000", path.fill)
        assertEquals(VisualStrokeCap.ROUND, path.stroke?.cap)
        assertEquals(1.0, path.alpha, 0.0)
        assertEquals(VisualDrawOp.Restore, document.ops[3])
        assertEquals("#ffffff", document.background)
    }

    // ------------------------------------------------------------ 词表

    @Test
    fun `an unknown field is rejected`() {
        val text = emptyDoc().replace("\"background\":null", "\"background\":null,\"oops\":1")
        assertEquals("DRAWOPS_UNKNOWN_FIELD", invalidCode(validate(text)))
    }

    @Test
    fun `an unknown op is rejected`() {
        assertEquals("DRAWOPS_UNKNOWN_OP", invalidCode(validate(emptyDoc(ops = """{"op":"rotate","deg":30}"""))))
    }

    @Test
    fun `a q command must not survive into draw ops`() {
        // Q 由编译器提升为 C；DrawOps 词表只有 M/L/C/Z（"编译器可信但不免检"复验点）。
        assertEquals(
            "DRAWOPS_PATH",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0],["Q",5,5,10,0]],""" +
                            """"fill":"#000000","stroke":null,"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `an arc command must not survive into draw ops`() {
        assertEquals(
            "DRAWOPS_PATH",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0],["A",10,10,0,0,1,20,0]],""" +
                            """"fill":"#000000","stroke":null,"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a path not starting with m is rejected`() {
        assertEquals(
            "DRAWOPS_PATH",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["L",1,1]],"fill":"#000000","stroke":null,"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a non json payload is rejected`() {
        assertEquals("DRAWOPS_NOT_JSON", invalidCode(validate("not json at all")))
    }

    // ------------------------------------------------------------ 配平与变换

    @Test
    fun `a restore without save is rejected`() {
        assertEquals("DRAWOPS_BALANCE", invalidCode(validate(emptyDoc(ops = """{"op":"restore"}"""))))
    }

    @Test
    fun `an unclosed save is rejected`() {
        assertEquals("DRAWOPS_BALANCE", invalidCode(validate(emptyDoc(ops = """{"op":"save"}"""))))
    }

    @Test
    fun `a singular matrix is rejected`() {
        assertEquals(
            "DRAWOPS_MATRIX",
            invalidCode(validate(emptyDoc(ops = """{"op":"concat","m":[1,0,0,0.0000000000001,0,0]}"""))),
        )
    }

    @Test
    fun `a matrix with an out of bound entry is rejected`() {
        assertEquals(
            "DRAWOPS_BOUNDS",
            invalidCode(validate(emptyDoc(ops = """{"op":"concat","m":[100001,0,0,1,0,0]}"""))),
        )
    }

    @Test
    fun `a matrix that is not six numbers is rejected`() {
        assertEquals(
            "DRAWOPS_TYPE",
            invalidCode(validate(emptyDoc(ops = """{"op":"concat","m":[1,0,0,1,0]}"""))),
        )
    }

    // ------------------------------------------------------------ viewBox 回显与样式

    @Test
    fun `a viewBox that does not echo the viewport is rejected`() {
        val text = emptyDoc().replace("\"w\":360", "\"w\":361")
        assertEquals("DRAWOPS_VIEWBOX", invalidCode(validate(text)))
    }

    @Test
    fun `an unknown fill rule is rejected`() {
        assertEquals(
            "DRAWOPS_TYPE",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"clip","path":[["M",0,0],["L",1,0],["L",1,1],["Z"]],"rule":"invert"}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a bad fill color is rejected`() {
        assertEquals(
            "DRAWOPS_COLOR",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0]],"fill":"red","stroke":null,"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a path without any paint is rejected`() {
        assertEquals(
            "DRAWOPS_NO_PAINT",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0]],"fill":null,"stroke":null,"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `an out of range alpha is rejected`() {
        assertEquals(
            "DRAWOPS_ALPHA",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0]],"fill":"#000000","stroke":null,"alpha":1.5}""",
                    ),
                ),
            ),
        )
    }

    // ------------------------------------------------------------ KD-78 生成门（批次 29 T5）

    @Test
    fun `a stroke only path is rejected at the generation gate`() {
        // KD-78 ①：DrawOps V1 的 stroke 无 color 字段，stroke-only 在回放侧无颜色源。
        // 生成侧（本解析器）前置拒绝，码面与 painter 同词；回放侧兜底保留（painter 仍拒
        // hand-built 文档）。它消灭"合法生成 → 回放时 FAIL-CLOSED 才拒"的预算浪费路径。
        assertEquals(
            PAINTER_STROKE_WITHOUT_FILL,
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0],["L",10,0]],"fill":null,""" +
                            """"stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":null},"alpha":1}""",
                        pathCommands = 2,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `odd short and empty dashes are rejected at the generation gate`() {
        // KD-78 ②：DashPathEffect 契约要求偶数 interval；length<2（含空数组）被 AOSP
        // 构造器抛**无码** ArrayIndexOutOfBoundsException。生成侧前置拒绝，码面与 painter
        // 同词（PAINTER_DASH_ODD），回放侧兜底保留。
        fun dashOp(dash: String): String =
            """{"op":"path","path":[["M",0,0],["L",10,0]],"fill":"#000000",""" +
                """"stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":4,"dash":[$dash]},"alpha":1}"""
        assertEquals(
            PAINTER_DASH_ODD,
            invalidCode(validate(emptyDoc(ops = dashOp("2,4,2"), pathCommands = 2))),
        )
        assertEquals(
            PAINTER_DASH_ODD,
            invalidCode(validate(emptyDoc(ops = dashOp("2"), pathCommands = 2))),
        )
        assertEquals(
            PAINTER_DASH_ODD,
            invalidCode(validate(emptyDoc(ops = dashOp(""), pathCommands = 2))),
        )
        // 负控：偶数 ≥2 的 dash 仍合法（门不是"一律拒 dash"）。
        val valid = validate(emptyDoc(ops = dashOp("2,2,4,4"), pathCommands = 2))
        val document = (valid as VisualDrawOpsValidation.Valid).document
        assertEquals(listOf(2.0, 2.0, 4.0, 4.0), (document.ops[0] as VisualDrawOp.DrawPath).stroke?.dash)
    }

    @Test
    fun `an eight digit fill color stays in the typed document for the replay alpha rule`() {
        // KD-78 ③：8 位色仍被词表接受（裁定的乘法语义在回放侧，不在解析侧收紧词表）。
        val text = emptyDoc(
            ops = """{"op":"path","path":[["M",0,0]],"fill":"#ff000080","stroke":null,"alpha":1}""",
            pathCommands = 1,
        )
        val document = (validate(text) as VisualDrawOpsValidation.Valid).document
        assertEquals("#ff000080", (document.ops[0] as VisualDrawOp.DrawPath).fill)
    }

    @Test
    fun `a miter limit below one is rejected`() {
        assertEquals(
            "DRAWOPS_MITER_LIMIT",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0]],"fill":null,""" +
                            """"stroke":{"width":1,"cap":"butt","join":"miter","miterLimit":0.5,"dash":null},"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a prototype chain key in stroke cap is rejected`() {
        // 批次 27 复核修复的宿主侧钉子：编译器若漏发继承键名 cap（原型链逃逸曾让
        // 'constructor' 直达 DrawOps），第二道门按词表枚举匹配仍以 DRAWOPS_CAP 拒绝，
        // 诊断族不落入其他 DRAWOPS_* 码。
        assertEquals(
            "DRAWOPS_CAP",
            invalidCode(
                validate(
                    emptyDoc(
                        ops = """{"op":"path","path":[["M",0,0]],"fill":null,""" +
                            """"stroke":{"width":1,"cap":"constructor","join":"miter","miterLimit":4,"dash":null},"alpha":1}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a prototype chain key in hit region rule is rejected`() {
        // 同上：rule='valueOf' 曾在编译器侧绕过 SCENE_STYLE_RULE 并发射；宿主侧
        // hitRegion rule 解析（先于 stats 复算）必须以 DRAWOPS_TYPE 拒绝。
        assertEquals(
            "DRAWOPS_TYPE",
            invalidCode(
                validate(
                    emptyDoc(
                        hitRegions = """{"id":"h1","path":[["M",0,0]],"rule":"valueOf"}""",
                        pathCommands = 1,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a label referencing content outside the frozen set is rejected`() {
        assertEquals(
            "DRAWOPS_REF",
            invalidCode(validate(emptyDoc(ops = labelOp(contentId = "ghost-1")))),
        )
    }

    @Test
    fun `a label position outside the coordinate bound is rejected`() {
        assertEquals(
            "DRAWOPS_BOUNDS",
            invalidCode(validate(emptyDoc(ops = labelOp(x = "100001")))),
        )
    }

    // ------------------------------------------------------------ 预算与统计

    @Test
    fun `a draw schema version other than one is rejected`() {
        val text = emptyDoc().replace("\"drawSchemaVersion\":1", "\"drawSchemaVersion\":2")
        // 批次 29 T2：版本错从 DRAWOPS_TYPE 独立成 DRAWOPS_SCHEMA_VERSION（行为仍是拒绝；
        // 码面分离让调用方能按"版本升级/坏文档"分流，见 VisualDrawOpsParser KDoc）。
        assertEquals("DRAWOPS_SCHEMA_VERSION", invalidCode(validate(text)))
    }

    @Test
    fun `stats that do not match the recount are rejected`() {
        val text = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":null,"ops":[${labelOp()}],"hitRegions":[],"stats":""" +
            """{"nodes":1,"pathCommands":0,"labels":2,"ops":1}}"""
        assertEquals("DRAWOPS_STATS", invalidCode(validate(text)))
    }

    @Test
    fun `node stats over the scene budget are rejected`() {
        val text = emptyDoc().replace("\"nodes\":0", "\"nodes\":257")
        assertEquals("DRAWOPS_BUDGET_NODES", invalidCode(validate(text)))
    }

    @Test
    fun `too many hit regions are rejected`() {
        val regions = (0 until 129).joinToString(",") { index ->
            """{"id":"h$index","path":[["M",0,0],["L",1,1]],"rule":"nonzero"}"""
        }
        val text = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":null,"ops":[],"hitRegions":[$regions],"stats":""" +
            """{"nodes":0,"pathCommands":258,"labels":0,"ops":0}}"""
        assertEquals("DRAWOPS_BUDGET_HIT_REGIONS", invalidCode(validate(text)))
    }

    @Test
    fun `duplicate hit region ids are rejected`() {
        val text = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":null,"ops":[],"hitRegions":[""" +
            """{"id":"h","path":[["M",0,0],["L",1,1]],"rule":"nonzero"},""" +
            """{"id":"h","path":[["M",2,2],["L",3,3]],"rule":"nonzero"}],"stats":""" +
            """{"nodes":0,"pathCommands":4,"labels":0,"ops":0}}"""
        assertEquals("DRAWOPS_DUP_ID", invalidCode(validate(text)))
    }

    @Test
    fun `replayed path commands over the budget are rejected`() {
        val commands = ArrayList<String>()
        commands.add("""["M",0,0]""")
        // 4097 条 L：先在单条 path op 内超限。
        for (i in 1..4096) {
            commands.add("""["L",$i,0]""")
        }
        val text = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":null,"ops":[{"op":"path","path":[${commands.joinToString(",")}],""" +
            """"fill":"#000000","stroke":null,"alpha":1}],"hitRegions":[],"stats":""" +
            """{"nodes":1,"pathCommands":4097,"labels":0,"ops":1}}"""
        assertEquals("DRAWOPS_BUDGET_COMMANDS", invalidCode(validate(text)))
    }

    @Test
    fun `stats lying about the replay count are rejected even under the budget`() {
        val text = """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",0,0],["L",1,0]],""" +
            """"fill":"#000000","stroke":null,"alpha":1}],"hitRegions":[],"stats":""" +
            """{"nodes":1,"pathCommands":99,"labels":0,"ops":1}}"""
        assertEquals("DRAWOPS_STATS", invalidCode(validate(text)))
    }

    @Test
    fun `an over budget payload is rejected without parsing`() {
        val oversized = "x".repeat(VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES + 1)
        assertEquals("DRAWOPS_BUDGET_BYTES", invalidCode(validate(oversized)))
    }

    @Test
    fun `rejections carry the typed code discipline`() {
        // 任务书第 3 条：类型化拒绝不进 INVALID_PROGRAM 泛化——码面 DRAWOPS_* + 有界文案，
        // 引擎 commitRendered 据此折 INVALID_SCENE（"CODE: message"）。
        val result = validate(emptyDoc(ops = """{"op":"mystery"}"""))
        val invalid = result as VisualDrawOpsValidation.Invalid
        assertTrue(invalid.code.startsWith("DRAWOPS_"))
        assertTrue(invalid.message.length <= 512)
    }
}
