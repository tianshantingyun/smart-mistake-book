package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/**
 * DrawOps V1 文本 ↔ 类型化文档的**严格解析器与确定性序列化器**（批次 29 T2；S-04 的
 * "序列化往返回路"落地）。
 *
 * **它消灭的具体失败**：
 * 1. **`Rendered.drawCommandsJson` 只能经引擎校验器消费、没有可复用入口**——批次 28 的
 *    painter 只由测试夹具消费，任何"快照 → 回放"路径都必须重新拼 `VisualDrawOpsValidator
 *    .validate(text, contentIds, viewport)`，而 `Rendered` 不携 viewport（S4 锁窗口记录），
 *    于是回放入口无处可落。本对象提供两个入口：`parse(text, frozenContentIds, viewport?)`
 *    （校验器原语义）与 `parse(rendered)`（回放语义：contentIds 来自 Rendered，viewBox 只做
 *    形状/有限性检查，不做视口回显——回显门在引擎提交时已过）。
 * 2. **缺失字段被 JSON 库默认值掩盖**——第三包 JSON 的"可选字段默认"会把 `stroke.cap`
 *    缺省成 butt、`alpha` 缺省成 1、`dash` 缺省成 null，使一份残缺文档静默画出"另一种
 *    图"。本解析器要求**结构键必须存在**（值可为 null 的只有 `background`），缺失即
 *    [CODE_MISSING_FIELD]；字符串数字（`"1"`）也不再当作整数/浮点（`DRAWOPS_TYPE`）。
 *    （"必须存在"覆盖根键、viewBox/stats 四元组、每种 op 的全部参数、stroke 五元组与
 *    hitRegion 三元组；值可为 null 的根字段只有 `background`。）
 * 3. **版本漂移静默错画**——`drawSchemaVersion != 1` 折 [CODE_SCHEMA_VERSION]（不再与其他
 *    类型错误共用 `DRAWOPS_TYPE`，使调用方可按码做版本升级/拒绝分流）。
 * 4. **序列化往返不可证**——[serialize] 以固定键序输出编译器同形的 DrawOps V1 文本；
 *    配合 `VisualDrawOpsParserTest` 的逐位（`Double.toRawBits`）往返断言，"文本 → 类型化
 *    → 文本"的可逆性成为可执行判据（此前只有编译器 JS 侧输出，没有 Kotlin 回写）。
 *
 * **严格性边界**：解析器只做**结构 + 值域**判定（与 [VisualDrawOpsValidator] 同一套不变量：
 * 词表/预算/有限性/配平/contentId 冻结/stats 复算）。它不判断 viewBox 是否回显视口，
 * 除非调用方给出 `expectedViewBox`——那是"引擎提交门"的职责，不是"文档可解析"的职责。
 *
 * 纯函数、零 android 符号：JVM 门（`VisualDrawOpsParserTest`）无需 Robolectric。
 */
internal object VisualDrawOpsParser {

    /** DrawOps V1 版本（与引擎 `DRAW_SCHEMA_VERSION`、painter 门同值镜像，改值必须三侧同改）。 */
    const val DRAW_SCHEMA_VERSION = 1

    // 码面（新增两码 + 一码专用于 Rendered 入口；其余沿用批 27 词表）。
    /** 结构键缺失（用于区分"缺字段"与"字段类型错"）。 */
    const val CODE_MISSING_FIELD = "DRAWOPS_MISSING_FIELD"
    /** 文档版本不是 [DRAW_SCHEMA_VERSION]。 */
    const val CODE_SCHEMA_VERSION = "DRAWOPS_SCHEMA_VERSION"
    /** `Rendered.drawCommandsJson` 为 null（fake/旧形态构造点把回放责任交给了不存在的文本）。 */
    const val CODE_MISSING_COMMANDS = "DRAWOPS_MISSING_COMMANDS"

    private const val MAX_COORD = 100000.0
    private const val MIN_ABS_DETERMINANT = 1e-12
    private const val MAX_DASH_ENTRIES = 32

    /**
     * ops 数量上界：每节点至多贡献 4 条 op（组：save+concat+clip+restore；path/label：1），
     * 节点 ≤256 ⇒ 1024。它消灭"被污染/有缺陷的编译器吐出无界 ops 流"。
     */
    private const val MAX_OPS = 1024

    private val COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")

    private val json = Json

    private val DOCUMENT_KEYS =
        setOf("drawSchemaVersion", "viewBox", "background", "ops", "hitRegions", "stats")

    // ---------------------------------------------------------------- 入口

    /**
     * 解析一份 DrawOps V1 文本。
     *
     * @param frozenContentIds label 可引用的 contentId 集合（冻结引用门；传空集即拒绝所有 label）。
     * @param expectedViewBox 非 null 时复验 viewBox 回显该视口（引擎提交门语义）；
     *   null 时只验四值有限且在 ±100000 内（回放入口语义，`Rendered` 不携视口）。
     */
    fun parse(
        text: String,
        frozenContentIds: Set<String>,
        expectedViewBox: VisualViewport? = null,
    ): VisualDrawOpsValidation {
        val bytes = figureUsageUtf8Bytes(text)
        if (bytes > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
            return invalid("DRAWOPS_BUDGET_BYTES", "draw ops are $bytes bytes, over the transaction JSON budget")
        }
        val root = try {
            json.parseToJsonElement(text)
        } catch (error: kotlinx.serialization.SerializationException) {
            // SerializationException 是 IllegalArgumentException 的子类，必须先接（更具体）。
            return invalid("DRAWOPS_NOT_JSON", "draw ops are not valid JSON")
        } catch (error: IllegalArgumentException) {
            return invalid("DRAWOPS_NOT_JSON", "draw ops are not valid JSON")
        }
        if (root !is JsonObject) {
            return invalid("DRAWOPS_TYPE", "draw ops document must be a JSON object")
        }
        return validateDocument(root, frozenContentIds, expectedViewBox)
    }

    /**
     * `Rendered.drawCommandsJson` → 类型化文档（回放入口）。
     *
     * 先验 `Rendered.drawSchemaVersion`（外壳镜像值）再验文本内 `drawSchemaVersion`：两处
     * 不一致的构造点（手工快照/跨版本快照）在任何绘制发生前被拒。
     */
    fun parse(rendered: VisualExecutionResult.Rendered): VisualDrawOpsValidation {
        if (rendered.drawSchemaVersion != DRAW_SCHEMA_VERSION) {
            return invalid(
                CODE_SCHEMA_VERSION,
                "rendered drawSchemaVersion ${rendered.drawSchemaVersion} is not $DRAW_SCHEMA_VERSION",
            )
        }
        val text = rendered.drawCommandsJson
            ?: return invalid(CODE_MISSING_COMMANDS, "rendered draw commands are absent")
        return parse(text, rendered.contentIds)
    }

    // ---------------------------------------------------------------- 文档

    private fun validateDocument(
        root: JsonObject,
        frozenContentIds: Set<String>,
        expectedViewBox: VisualViewport?,
    ): VisualDrawOpsValidation {
        root.checkKeys(DOCUMENT_KEYS, "document")?.let { return it }
        root.requireKeys(DOCUMENT_KEYS, "document")?.let { return it }

        val schemaVersion = root.primitiveInt("drawSchemaVersion")
            ?: return invalid("DRAWOPS_TYPE", "drawSchemaVersion must be an integer")
        if (schemaVersion != DRAW_SCHEMA_VERSION) {
            return invalid(
                CODE_SCHEMA_VERSION,
                "drawSchemaVersion must be exactly $DRAW_SCHEMA_VERSION, got $schemaVersion",
            )
        }

        val viewBoxObject = root.objectField("viewBox")
            ?: return invalid("DRAWOPS_TYPE", "viewBox must be an object")
        viewBoxObject.checkKeys(setOf("x", "y", "w", "h"), "viewBox")?.let { return it }
        viewBoxObject.requireKeys(setOf("x", "y", "w", "h"), "viewBox")?.let { return it }
        val x = viewBoxObject.primitiveDouble("x")
            ?: return invalid("DRAWOPS_TYPE", "viewBox.x must be a number")
        val y = viewBoxObject.primitiveDouble("y")
            ?: return invalid("DRAWOPS_TYPE", "viewBox.y must be a number")
        val w = viewBoxObject.primitiveDouble("w")
            ?: return invalid("DRAWOPS_TYPE", "viewBox.w must be a number")
        val h = viewBoxObject.primitiveDouble("h")
            ?: return invalid("DRAWOPS_TYPE", "viewBox.h must be a number")
        for ((label, value) in listOf("x" to x, "y" to y, "w" to w, "h" to h)) {
            if (!value.isFinite() || value > MAX_COORD || value < -MAX_COORD) {
                return invalid("DRAWOPS_BOUNDS", "viewBox.$label is outside the ±$MAX_COORD bound")
            }
        }
        if (expectedViewBox != null &&
            (x != 0.0 || y != 0.0 || w != expectedViewBox.widthDu.toDouble() ||
                h != expectedViewBox.heightDu.toDouble())
        ) {
            // 编译器已把 viewBox 钉成视口回显；此处逐项复验（回显不可被暗改）。
            return invalid(
                "DRAWOPS_VIEWBOX",
                "viewBox must echo the viewport {0,0,${expectedViewBox.widthDu},${expectedViewBox.heightDu}}",
            )
        }

        val background = when (val field = root["background"]) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (!COLOR_PATTERN.matches(field.content)) {
                    return invalid("DRAWOPS_COLOR", "background must be \"#RRGGBB\" or \"#RRGGBBAA\"")
                }
                field.content
            }
            else -> return invalid("DRAWOPS_TYPE", "background must be a color string or null")
        }

        val opsElement = root["ops"] as? JsonArray
            ?: return invalid("DRAWOPS_TYPE", "ops must be an array")
        if (opsElement.size > MAX_OPS) {
            return invalid("DRAWOPS_BUDGET_OPS", "ops has ${opsElement.size} entries, over $MAX_OPS")
        }

        val hitRegions = when (val parsed = validateHitRegions(root["hitRegions"])) {
            is HitRegionsParse.Failure -> return parsed.failure
            is HitRegionsParse.Parsed -> parsed.regions
        }

        var replayedPathCommands = 0
        var labels = 0
        var saveDepth = 0
        val ops = ArrayList<VisualDrawOp>(opsElement.size)
        for (index in opsElement.indices) {
            val opObject = opsElement[index] as? JsonObject
                ?: return invalid("DRAWOPS_TYPE", "ops[$index] must be an object")
            val op = when (val parsed = parseOp(opObject, "ops[$index]", frozenContentIds)) {
                is OpParse.Failure -> return parsed.failure
                is OpParse.Parsed -> parsed.op
            }
            when (op) {
                is VisualDrawOp.Save -> saveDepth += 1
                is VisualDrawOp.Restore -> {
                    saveDepth -= 1
                    if (saveDepth < 0) {
                        return invalid("DRAWOPS_BALANCE", "ops[$index]: restore without a matching save")
                    }
                }
                is VisualDrawOp.Clip -> replayedPathCommands += op.path.size
                is VisualDrawOp.DrawPath -> replayedPathCommands += op.path.size
                is VisualDrawOp.Label -> labels += 1
                is VisualDrawOp.Concat -> Unit
            }
            if (replayedPathCommands > VisualExecutionBudget.MAX_PATH_COMMANDS) {
                return invalid(
                    "DRAWOPS_BUDGET_COMMANDS",
                    "replayed path commands exceed ${VisualExecutionBudget.MAX_PATH_COMMANDS}",
                )
            }
            ops.add(op)
        }
        if (saveDepth != 0) {
            return invalid("DRAWOPS_BALANCE", "save/restore unbalanced (depth $saveDepth at end)")
        }

        for (region in hitRegions) {
            replayedPathCommands += region.path.size
        }
        if (replayedPathCommands > VisualExecutionBudget.MAX_PATH_COMMANDS) {
            return invalid(
                "DRAWOPS_BUDGET_COMMANDS",
                "replayed path commands (ops + hit regions) exceed ${VisualExecutionBudget.MAX_PATH_COMMANDS}",
            )
        }

        // stats：可复算项全部比对；nodes 只验上界（组节点折叠为 save/concat，不可重导出）。
        val statsObject = root.objectField("stats")
            ?: return invalid("DRAWOPS_TYPE", "stats must be an object")
        statsObject.checkKeys(setOf("nodes", "pathCommands", "labels", "ops"), "stats")?.let { return it }
        statsObject.requireKeys(setOf("nodes", "pathCommands", "labels", "ops"), "stats")?.let { return it }
        val statsNodes = statsObject.primitiveInt("nodes")
            ?: return invalid("DRAWOPS_TYPE", "stats.nodes must be an integer")
        val statsPathCommands = statsObject.primitiveInt("pathCommands")
            ?: return invalid("DRAWOPS_TYPE", "stats.pathCommands must be an integer")
        val statsLabels = statsObject.primitiveInt("labels")
            ?: return invalid("DRAWOPS_TYPE", "stats.labels must be an integer")
        val statsOps = statsObject.primitiveInt("ops")
            ?: return invalid("DRAWOPS_TYPE", "stats.ops must be an integer")
        if (statsNodes < 0 || statsNodes > VisualExecutionBudget.MAX_SCENE_NODES) {
            return invalid(
                "DRAWOPS_BUDGET_NODES",
                "stats.nodes $statsNodes outside [0, ${VisualExecutionBudget.MAX_SCENE_NODES}]",
            )
        }
        if (statsPathCommands != replayedPathCommands) {
            return invalid(
                "DRAWOPS_STATS",
                "stats.pathCommands $statsPathCommands does not match the recount $replayedPathCommands",
            )
        }
        if (statsLabels != labels) {
            return invalid("DRAWOPS_STATS", "stats.labels $statsLabels does not match the recount $labels")
        }
        if (statsOps != ops.size) {
            return invalid("DRAWOPS_STATS", "stats.ops $statsOps does not match ${ops.size}")
        }

        return VisualDrawOpsValidation.Valid(
            VisualDrawOpsDocument(
                drawSchemaVersion = schemaVersion,
                viewBox = VisualDrawOpsViewBox(x, y, w, h),
                background = background,
                ops = ops,
                hitRegions = hitRegions,
                stats = VisualDrawOpsStats(statsNodes, statsPathCommands, statsLabels, statsOps),
            ),
        )
    }

    private sealed interface HitRegionsParse {
        data class Parsed(val regions: List<VisualDrawHitRegion>) : HitRegionsParse
        data class Failure(val failure: VisualDrawOpsValidation.Invalid) : HitRegionsParse
    }

    private fun validateHitRegions(element: JsonElement?): HitRegionsParse {
        val array = element as? JsonArray
            ?: return HitRegionsParse.Failure(
                invalid(
                    "DRAWOPS_TYPE",
                    if (element == null) "hitRegions is missing" else "hitRegions must be an array",
                ),
            )
        if (array.size > VisualExecutionBudget.MAX_HIT_REGIONS) {
            return HitRegionsParse.Failure(
                invalid(
                    "DRAWOPS_BUDGET_HIT_REGIONS",
                    "hitRegions has ${array.size} entries, over ${VisualExecutionBudget.MAX_HIT_REGIONS}",
                ),
            )
        }
        val seen = HashSet<String>()
        val regions = ArrayList<VisualDrawHitRegion>(array.size)
        for (index in array.indices) {
            val at = "hitRegions[$index]"
            val regionObject = array[index] as? JsonObject
                ?: return HitRegionsParse.Failure(invalid("DRAWOPS_TYPE", "$at must be an object"))
            regionObject.checkKeys(setOf("id", "path", "rule"), at)
                ?.let { failure -> return HitRegionsParse.Failure(failure) }
            regionObject.requireKeys(setOf("id", "path", "rule"), at)
                ?.let { failure -> return HitRegionsParse.Failure(failure) }
            val id = regionObject.stringField("id")
                ?: return HitRegionsParse.Failure(invalid("DRAWOPS_TYPE", "$at.id must be a string"))
            if (!seen.add(id)) {
                return HitRegionsParse.Failure(invalid("DRAWOPS_DUP_ID", "$at: duplicate id \"$id\""))
            }
            val path = parseCommands(regionObject["path"])
                ?: return HitRegionsParse.Failure(
                    invalid(
                        "DRAWOPS_PATH",
                        "$at.path must be a non-empty M/L/C/Z command array starting with M",
                    ),
                )
            val rule = parseFillRule(regionObject["rule"])
                ?: return HitRegionsParse.Failure(
                    invalid("DRAWOPS_TYPE", "$at.rule must be \"nonzero\" or \"evenodd\""),
                )
            regions.add(VisualDrawHitRegion(id, path, rule))
        }
        return HitRegionsParse.Parsed(regions)
    }

    private sealed interface OpParse {
        data class Parsed(val op: VisualDrawOp) : OpParse
        data class Failure(val failure: VisualDrawOpsValidation.Invalid) : OpParse
    }

    private fun parseOp(op: JsonObject, at: String, frozenContentIds: Set<String>): OpParse {
        val kind = (op["op"] as? JsonPrimitive)?.content
            ?: return OpParse.Failure(invalid(CODE_MISSING_FIELD, "$at: missing required field \"op\""))
        return when (kind) {
            "save" -> {
                op.checkKeys(setOf("op"), at)?.let { return OpParse.Failure(it) }
                OpParse.Parsed(VisualDrawOp.Save)
            }

            "restore" -> {
                op.checkKeys(setOf("op"), at)?.let { return OpParse.Failure(it) }
                OpParse.Parsed(VisualDrawOp.Restore)
            }

            "concat" -> {
                op.checkKeys(setOf("op", "m"), at)?.let { return OpParse.Failure(it) }
                op.requireKeys(setOf("op", "m"), at)?.let { return OpParse.Failure(it) }
                val matrixElement = op["m"] as? JsonArray
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: m must be an array of 6 numbers"))
                if (matrixElement.size != 6) {
                    return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: m must have exactly 6 entries"))
                }
                val values = ArrayList<Double>(6)
                for (i in 0 until 6) {
                    val value = matrixElement[i].primitiveDoubleOrNull()
                        ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: m[$i] must be a finite number"))
                    if (!value.isFinite() || value > MAX_COORD || value < -MAX_COORD) {
                        return OpParse.Failure(invalid("DRAWOPS_BOUNDS", "$at: m[$i] outside the ±100000 bound"))
                    }
                    values.add(value)
                }
                val matrix = VisualMatrix(values[0], values[1], values[2], values[3], values[4], values[5])
                if (!matrix.determinant.isFinite() || abs(matrix.determinant) < MIN_ABS_DETERMINANT) {
                    return OpParse.Failure(invalid("DRAWOPS_MATRIX", "$at: |det| < $MIN_ABS_DETERMINANT"))
                }
                OpParse.Parsed(VisualDrawOp.Concat(matrix))
            }

            "clip" -> {
                op.checkKeys(setOf("op", "path", "rule"), at)?.let { return OpParse.Failure(it) }
                op.requireKeys(setOf("op", "path", "rule"), at)?.let { return OpParse.Failure(it) }
                val path = parseCommands(op["path"])
                    ?: return OpParse.Failure(
                        invalid("DRAWOPS_PATH", "$at: path must be a non-empty M/L/C/Z command array starting with M"),
                    )
                val rule = parseFillRule(op["rule"])
                    ?: return OpParse.Failure(
                        invalid("DRAWOPS_TYPE", "$at: rule must be \"nonzero\" or \"evenodd\""),
                    )
                OpParse.Parsed(VisualDrawOp.Clip(path, rule))
            }

            "path" -> {
                op.checkKeys(setOf("op", "path", "fill", "stroke", "alpha"), at)
                    ?.let { return OpParse.Failure(it) }
                op.requireKeys(setOf("op", "path", "fill", "stroke", "alpha"), at)
                    ?.let { return OpParse.Failure(it) }
                val path = parseCommands(op["path"])
                    ?: return OpParse.Failure(
                        invalid("DRAWOPS_PATH", "$at: path must be a non-empty M/L/C/Z command array starting with M"),
                    )
                val fill = when (val field = op["fill"]) {
                    is JsonNull -> null
                    is JsonPrimitive -> {
                        if (!COLOR_PATTERN.matches(field.content)) {
                            return OpParse.Failure(invalid("DRAWOPS_COLOR", "$at: fill must be \"#RRGGBB(AA)\""))
                        }
                        field.content
                    }
                    else -> return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: fill must be a color or null"))
                }
                val stroke = when (val field = op["stroke"]) {
                    is JsonNull -> null
                    is JsonObject -> when (val parsed = parseStroke(field, "$at.stroke")) {
                        is StrokeParse.Parsed -> parsed.spec
                        is StrokeParse.Failure -> return OpParse.Failure(parsed.failure)
                    }
                    else -> return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: stroke must be an object or null"))
                }
                if (fill == null && stroke == null) {
                    return OpParse.Failure(invalid("DRAWOPS_NO_PAINT", "$at: path needs a fill or a stroke"))
                }
                // KD-78 ① 生成门（批次 29 T5）：DrawOps V1 的 stroke 无 color 字段——stroke-only
                // 在回放侧无颜色源（回放裁定"stroke 色取 fill 色"要求 fill 必填）。与编译器
                // 同码前置拒绝，painter 的 [PAINTER_STROKE_WITHOUT_FILL] 保留为回放兜底。
                if (fill == null && stroke != null) {
                    return OpParse.Failure(
                        invalid(
                            PAINTER_STROKE_WITHOUT_FILL,
                            "$at: stroke-only path has no color source in DrawOps V1 (fill is required)",
                        ),
                    )
                }
                val alpha = op.primitiveDouble("alpha")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: alpha must be a number"))
                if (alpha < 0.0 || alpha > 1.0) {
                    return OpParse.Failure(invalid("DRAWOPS_ALPHA", "$at: alpha must be within [0, 1]"))
                }
                OpParse.Parsed(VisualDrawOp.DrawPath(path, fill, stroke, alpha))
            }

            "label" -> {
                val required =
                    setOf("op", "contentId", "x", "y", "rotationDeg", "hAlign", "vAlign", "color", "alpha")
                op.checkKeys(required, at)?.let { return OpParse.Failure(it) }
                op.requireKeys(required, at)?.let { return OpParse.Failure(it) }
                val contentId = op.stringField("contentId")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: contentId must be a string"))
                if (contentId !in frozenContentIds) {
                    return OpParse.Failure(
                        invalid(
                            "DRAWOPS_REF",
                            "$at: contentId \"${contentId.take(32)}\" is not frozen in this transaction",
                        ),
                    )
                }
                val x = op.primitiveDouble("x")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: x must be a number"))
                val y = op.primitiveDouble("y")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: y must be a number"))
                val rotation = op.primitiveDouble("rotationDeg")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: rotationDeg must be a number"))
                if (!x.isFinite() || !y.isFinite() || !rotation.isFinite() ||
                    x > MAX_COORD || x < -MAX_COORD || y > MAX_COORD || y < -MAX_COORD ||
                    rotation > MAX_COORD || rotation < -MAX_COORD
                ) {
                    return OpParse.Failure(invalid("DRAWOPS_BOUNDS", "$at: label position outside the ±100000 bound"))
                }
                val hAlign = parseHAlign(op["hAlign"])
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: hAlign must be left/center/right"))
                val vAlign = parseVAlign(op["vAlign"])
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: vAlign must be top/middle/bottom"))
                val color = (op["color"] as? JsonPrimitive)?.content
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: color must be a string"))
                if (!COLOR_PATTERN.matches(color)) {
                    return OpParse.Failure(invalid("DRAWOPS_COLOR", "$at: color must be \"#RRGGBB(AA)\""))
                }
                val alpha = op.primitiveDouble("alpha")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: alpha must be a number"))
                if (alpha < 0.0 || alpha > 1.0) {
                    return OpParse.Failure(invalid("DRAWOPS_ALPHA", "$at: alpha must be within [0, 1]"))
                }
                OpParse.Parsed(
                    VisualDrawOp.Label(contentId, x, y, rotation, hAlign, vAlign, color, alpha),
                )
            }

            else -> OpParse.Failure(invalid("DRAWOPS_UNKNOWN_OP", "$at: unknown op \"$kind\""))
        }
    }

    private sealed interface StrokeParse {
        data class Parsed(val spec: VisualStrokeSpec) : StrokeParse
        data class Failure(val failure: VisualDrawOpsValidation.Invalid) : StrokeParse
    }

    private fun parseStroke(stroke: JsonObject, at: String): StrokeParse {
        val required = setOf("width", "cap", "join", "miterLimit", "dash")
        stroke.checkKeys(required, at)?.let { failure -> return StrokeParse.Failure(failure) }
        stroke.requireKeys(required, at)?.let { failure -> return StrokeParse.Failure(failure) }
        val width = stroke.primitiveDouble("width")
            ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: width must be a number"))
        if (!width.isFinite() || width <= 0.0 || width > MAX_COORD) {
            return StrokeParse.Failure(invalid("DRAWOPS_BOUNDS", "$at: width must be within (0, 100000]"))
        }
        // cap/join/dash 不再取默认值：缺键即 [CODE_MISSING_FIELD]（上面 requireKeys 已拒），
        // 值类型不符才落到 DRAWOPS_TYPE——"缺"与"错"的码面分离让调用方能区分坏快照与旧快照。
        val capText = stroke.stringField("cap")
            ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: cap must be a string"))
        val cap = VisualStrokeCap.entries.firstOrNull { it.json == capText }
            ?: return StrokeParse.Failure(invalid("DRAWOPS_CAP", "$at: cap must be butt/round/square"))
        val joinText = stroke.stringField("join")
            ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: join must be a string"))
        val join = VisualStrokeJoin.entries.firstOrNull { it.json == joinText }
            ?: return StrokeParse.Failure(invalid("DRAWOPS_JOIN", "$at: join must be miter/round/bevel"))
        val miterLimit = stroke.primitiveDouble("miterLimit")
            ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: miterLimit must be a number"))
        if (!miterLimit.isFinite() || miterLimit < 1.0 || miterLimit > MAX_COORD) {
            return StrokeParse.Failure(invalid("DRAWOPS_MITER_LIMIT", "$at: miterLimit must be within [1, 100000]"))
        }
        val dash = when (val field = stroke["dash"]) {
            is JsonNull -> null
            is JsonArray -> {
                if (field.size > MAX_DASH_ENTRIES) {
                    return StrokeParse.Failure(invalid("DRAWOPS_DASH", "$at: dash has over $MAX_DASH_ENTRIES entries"))
                }
                val entries = ArrayList<Double>(field.size)
                for (i in field.indices) {
                    val entry = field[i].primitiveDoubleOrNull()
                        ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: dash[$i] must be a number"))
                    if (!entry.isFinite() || entry <= 0.0 || entry > MAX_COORD) {
                        return StrokeParse.Failure(invalid("DRAWOPS_DASH", "$at: dash[$i] must be within (0, 100000]"))
                    }
                    entries.add(entry)
                }
                // KD-78 ② 形状门（批次 29 T5）：DashPathEffect 契约要求偶数个 interval，且
                // length<2（含空数组）被 AOSP 构造器直接抛**无码** ArrayIndexOutOfBoundsException
                //（aosp-mirror DashPathEffect.java:34-39）。与编译器同码前置拒绝；painter 的
                // [PAINTER_DASH_ODD] 保留为回放兜底。
                if (entries.size < 2 || entries.size % 2 != 0) {
                    return StrokeParse.Failure(
                        invalid(
                            PAINTER_DASH_ODD,
                            "$at: dash must have an even number of entries (at least 2), got ${entries.size}",
                        ),
                    )
                }
                entries
            }
            else -> return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: dash must be an array or null"))
        }
        return StrokeParse.Parsed(VisualStrokeSpec(width, cap, join, miterLimit, dash))
    }

    /**
     * 路径命令解析（M/L/C/Z；**Q/A 在 DrawOps 词表中不存在**——Q 由编译器提升、A 由 SDK
     * 拆段，出现即拒绝：这是"编译器可信但不免检"的复验点）。null = 结构非法。
     */
    private fun parseCommands(element: JsonElement?): List<VisualPathCommand>? {
        val array = element as? JsonArray ?: return null
        val commands = ArrayList<VisualPathCommand>(array.size)
        for (index in array.indices) {
            val command = array[index] as? JsonArray ?: return null
            val code = (command.firstOrNull() as? JsonPrimitive)?.content ?: return null
            fun coordinate(i: Int): Double? {
                val value = command.getOrNull(i)?.primitiveDoubleOrNull() ?: return null
                if (!value.isFinite() || value > MAX_COORD || value < -MAX_COORD) {
                    return null
                }
                return value
            }
            val parsed: VisualPathCommand = when (code) {
                "M" -> VisualPathCommand.MoveTo(coordinate(1) ?: return null, coordinate(2) ?: return null)
                "L" -> VisualPathCommand.LineTo(coordinate(1) ?: return null, coordinate(2) ?: return null)
                "C" -> VisualPathCommand.CubicTo(
                    coordinate(1) ?: return null,
                    coordinate(2) ?: return null,
                    coordinate(3) ?: return null,
                    coordinate(4) ?: return null,
                    coordinate(5) ?: return null,
                    coordinate(6) ?: return null,
                )

                "Z" -> VisualPathCommand.Close
                else -> return null
            }
            commands.add(parsed)
        }
        if (commands.isEmpty() || commands.first() !is VisualPathCommand.MoveTo) {
            return null
        }
        return commands
    }

    private fun parseFillRule(element: JsonElement?): VisualFillRule? {
        val text = (element as? JsonPrimitive)?.content ?: return null
        return VisualFillRule.entries.firstOrNull { it.json == text }
    }

    private fun parseHAlign(element: JsonElement?): VisualTextHAlign? {
        val text = (element as? JsonPrimitive)?.content ?: return null
        return VisualTextHAlign.entries.firstOrNull { it.json == text }
    }

    private fun parseVAlign(element: JsonElement?): VisualTextVAlign? {
        val text = (element as? JsonPrimitive)?.content ?: return null
        return VisualTextVAlign.entries.firstOrNull { it.json == text }
    }

    private fun invalid(code: String, message: String) = VisualDrawOpsValidation.Invalid(code, message)

    // ---------------------------------------------------------------- 序列化

    /**
     * 类型化文档 → DrawOps V1 文本（固定键序，与 `visual-compiler.js serialize()` 同形）。
     *
     * 数字经 `Double.toString()`（Java 规范保证 `toDouble(toString(d)) == d`——这正是
     * 往返测试能要求"逐位相等"而不是 epsilon 相等的根据）；非有限值直接拒绝（JSON 无法
     * 表达 NaN/Infinity，静默写成 `NaN` 会让下游 JSON 解析器读到非 JSON）。
     */
    fun serialize(document: VisualDrawOpsDocument): String {
        val sb = StringBuilder(256)
        sb.append("{\"drawSchemaVersion\":").append(document.drawSchemaVersion)
        sb.append(",\"viewBox\":{\"x\":").append(num(document.viewBox.x))
            .append(",\"y\":").append(num(document.viewBox.y))
            .append(",\"w\":").append(num(document.viewBox.w))
            .append(",\"h\":").append(num(document.viewBox.h))
            .append('}')
        sb.append(",\"background\":")
        val background = document.background
        if (background == null) {
            sb.append("null")
        } else {
            sb.appendJsonString(background)
        }
        sb.append(",\"ops\":[")
        document.ops.forEachIndexed { index, op ->
            if (index > 0) sb.append(',')
            appendOp(sb, op)
        }
        sb.append("],\"hitRegions\":[")
        document.hitRegions.forEachIndexed { index, region ->
            if (index > 0) sb.append(',')
            sb.append("{\"id\":")
            sb.appendJsonString(region.id)
            sb.append(",\"path\":")
            appendCommands(sb, region.path)
            sb.append(",\"rule\":")
            sb.appendJsonString(region.rule.json)
            sb.append('}')
        }
        sb.append("],\"stats\":{\"nodes\":").append(document.stats.nodes)
            .append(",\"pathCommands\":").append(document.stats.pathCommands)
            .append(",\"labels\":").append(document.stats.labels)
            .append(",\"ops\":").append(document.stats.ops)
            .append("}}")
        return sb.toString()
    }

    private fun appendOp(sb: StringBuilder, op: VisualDrawOp) {
        when (op) {
            is VisualDrawOp.Save -> sb.append("{\"op\":\"save\"}")
            is VisualDrawOp.Restore -> sb.append("{\"op\":\"restore\"}")
            is VisualDrawOp.Concat -> {
                sb.append("{\"op\":\"concat\",\"m\":[")
                val values = listOf(op.matrix.a, op.matrix.b, op.matrix.c, op.matrix.d, op.matrix.e, op.matrix.f)
                values.forEachIndexed { index, value ->
                    if (index > 0) sb.append(',')
                    sb.append(num(value))
                }
                sb.append("]}")
            }

            is VisualDrawOp.Clip -> {
                sb.append("{\"op\":\"clip\",\"path\":")
                appendCommands(sb, op.path)
                sb.append(",\"rule\":")
                sb.appendJsonString(op.rule.json)
                sb.append('}')
            }

            is VisualDrawOp.DrawPath -> {
                sb.append("{\"op\":\"path\",\"path\":")
                appendCommands(sb, op.path)
                sb.append(",\"fill\":")
                val fill = op.fill
                if (fill == null) {
                    sb.append("null")
                } else {
                    sb.appendJsonString(fill)
                }
                sb.append(",\"stroke\":")
                val stroke = op.stroke
                if (stroke == null) {
                    sb.append("null")
                } else {
                    appendStroke(sb, stroke)
                }
                sb.append(",\"alpha\":").append(num(op.alpha)).append('}')
            }

            is VisualDrawOp.Label -> {
                sb.append("{\"op\":\"label\",\"contentId\":")
                sb.appendJsonString(op.contentId)
                sb.append(",\"x\":").append(num(op.x))
                sb.append(",\"y\":").append(num(op.y))
                sb.append(",\"rotationDeg\":").append(num(op.rotationDeg))
                sb.append(",\"hAlign\":")
                sb.appendJsonString(op.hAlign.json)
                sb.append(",\"vAlign\":")
                sb.appendJsonString(op.vAlign.json)
                sb.append(",\"color\":")
                sb.appendJsonString(op.color)
                sb.append(",\"alpha\":").append(num(op.alpha)).append('}')
            }
        }
    }

    private fun appendStroke(sb: StringBuilder, stroke: VisualStrokeSpec) {
        sb.append("{\"width\":").append(num(stroke.width))
        sb.append(",\"cap\":")
        sb.appendJsonString(stroke.cap.json)
        sb.append(",\"join\":")
        sb.appendJsonString(stroke.join.json)
        sb.append(",\"miterLimit\":").append(num(stroke.miterLimit))
        sb.append(",\"dash\":")
        val dash = stroke.dash
        if (dash == null) {
            sb.append("null")
        } else {
            sb.append('[')
            dash.forEachIndexed { index, value ->
                if (index > 0) sb.append(',')
                sb.append(num(value))
            }
            sb.append(']')
        }
        sb.append('}')
    }

    private fun appendCommands(sb: StringBuilder, commands: List<VisualPathCommand>) {
        sb.append('[')
        commands.forEachIndexed { index, command ->
            if (index > 0) sb.append(',')
            when (command) {
                is VisualPathCommand.MoveTo -> {
                    sb.append("[\"M\",").append(num(command.x)).append(',').append(num(command.y)).append(']')
                }

                is VisualPathCommand.LineTo -> {
                    sb.append("[\"L\",").append(num(command.x)).append(',').append(num(command.y)).append(']')
                }

                is VisualPathCommand.CubicTo -> {
                    sb.append("[\"C\",")
                        .append(num(command.x1)).append(',')
                        .append(num(command.y1)).append(',')
                        .append(num(command.x2)).append(',')
                        .append(num(command.y2)).append(',')
                        .append(num(command.x)).append(',')
                        .append(num(command.y)).append(']')
                }

                VisualPathCommand.Close -> sb.append("[\"Z\"]")
            }
        }
        sb.append(']')
    }

    /** `Double.toString` 的 JSON 数字形态；非有限值在写前拒绝（不让 `NaN` 漏进文本）。 */
    private fun num(value: Double): String {
        require(value.isFinite()) { "DrawOps numbers must be finite to serialize, got $value" }
        return value.toString()
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> {
                    if (ch < ' ') {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    // ---------------------------------------------------------------- JSON 帮助

    private fun JsonObject.checkKeys(allowed: Set<String>, at: String): VisualDrawOpsValidation.Invalid? {
        for (key in keys) {
            if (key !in allowed) {
                return invalid("DRAWOPS_UNKNOWN_FIELD", "$at: unknown field \"$key\"")
            }
        }
        return null
    }

    /**
     * 结构键存在性门（"不得用库默认值掩盖缺失字段"的落点）：值可以为 null 的字段
     * （`background`/`fill`/`stroke`/`dash`）也必须显式出现；只有键本身缺失才判
     * [CODE_MISSING_FIELD]，类型错另判 `DRAWOPS_TYPE`。
     */
    private fun JsonObject.requireKeys(required: Set<String>, at: String): VisualDrawOpsValidation.Invalid? {
        for (key in required) {
            if (!containsKey(key)) {
                return invalid(CODE_MISSING_FIELD, "$at: missing required field \"$key\"")
            }
        }
        return null
    }

    private fun JsonObject.objectField(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.stringField(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content
    }

    private fun JsonObject.primitiveInt(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        // isString 防守：字符串 "1" 不是整数（严格类型；JSON 数字的 content 也经 toIntOrNull，
        // "1.0"/"1e3" 不被当作整数——编译器只发十进制整数）。
        if (primitive.isString) return null
        return primitive.content.toIntOrNull()
    }

    private fun JsonElement.primitiveDoubleOrNull(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        // isString 防守：字符串 "1.5" 不是数字（严格类型，不接收字符串数字）。
        if (primitive.isString) return null
        return primitive.content.toDoubleOrNull()
    }

    private fun JsonObject.primitiveDouble(key: String): Double? {
        val primitive = this[key] ?: return null
        return primitive.primitiveDoubleOrNull()
    }
}
