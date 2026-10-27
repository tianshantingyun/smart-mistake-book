package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
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
 * DrawOps V1 的**原生严格校验器**（批次 27 T1；R1 §2 管线第 4 步"原生严格校验"的
 * DrawOps 扩展面、§7.1 建议的 JVM 纯函数层）。
 *
 * **它消灭的具体失败**：编译器跑在被不可信程序输出喂过的进程里——"编译器是可信 SDK"
 * 与"它可能带出越界值"是两回事（R1 §3.1 表第 3 行）。编译输出必须在原生侧按与编译器
 * **同一套不变量**复验（词表 / 预算 / 有限性 / save-restore 配平 / contentId 冻结引用），
 * 任何不符 = 有界拒绝（INVALID_SCENE + DRAWOPS_* 码），不进 INVALID_PROGRAM 泛化，
 * 也绝不把未验 JSON 交给 painter。
 *
 * 与 JS 侧的关系：编译器（`visual-compiler.js`）是第一道门（生成侧），本校验器是第二道门
 * （消费侧）；两者词表/预算同值（Kotlin 侧常量单源 [VisualExecutionBudget]，JS 侧文件头
 * 注明同值镜像，改预算必须两端同改——Node 测试台与 JVM 负向矩阵各自钉住）。
 *
 * 校验器是纯函数（JSON 文本 → [VisualDrawOpsValidation]），与 [VisualJsFailureClassification]
 * 同型，全 JVM 覆盖（`VisualDrawOpsValidatorTest` 负向矩阵）。统计项中只有
 * `nodes` 无法从 ops 重导出（组节点折叠为 save/concat/restore）——编译层强制 ≤256，
 * 这里只验上界与类型；其余统计（ops/labels/pathCommands）全部复算比对。
 */
internal object VisualDrawOpsValidator {

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

    fun validate(
        text: String,
        frozenContentIds: Set<String>,
        viewport: VisualViewport,
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
        return validateDocument(root, frozenContentIds, viewport)
    }

    private fun validateDocument(
        root: JsonObject,
        frozenContentIds: Set<String>,
        viewport: VisualViewport,
    ): VisualDrawOpsValidation {
        root.checkKeys(setOf("drawSchemaVersion", "viewBox", "background", "ops", "hitRegions", "stats"))
            ?.let { failure -> return failure }

        val schemaVersion = root.primitiveInt("drawSchemaVersion")
            ?: return invalid("DRAWOPS_TYPE", "drawSchemaVersion must be an integer")
        if (schemaVersion != 1) {
            return invalid("DRAWOPS_TYPE", "drawSchemaVersion must be exactly 1, got $schemaVersion")
        }

        val viewBoxObject = root.objectField("viewBox")
            ?: return invalid("DRAWOPS_TYPE", "viewBox must be an object")
        viewBoxObject.checkKeys(setOf("x", "y", "w", "h"))?.let { failure -> return failure }
        val x = viewBoxObject.primitiveDouble("x") ?: return invalid("DRAWOPS_TYPE", "viewBox.x must be a number")
        val y = viewBoxObject.primitiveDouble("y") ?: return invalid("DRAWOPS_TYPE", "viewBox.y must be a number")
        val w = viewBoxObject.primitiveDouble("w") ?: return invalid("DRAWOPS_TYPE", "viewBox.w must be a number")
        val h = viewBoxObject.primitiveDouble("h") ?: return invalid("DRAWOPS_TYPE", "viewBox.h must be a number")
        // 编译器已把 viewBox 钉成视口回显；此处逐项复验（回显不可被暗改）。
        if (x != 0.0 || y != 0.0 || w != viewport.widthDu.toDouble() || h != viewport.heightDu.toDouble()) {
            return invalid(
                "DRAWOPS_VIEWBOX",
                "viewBox must echo the viewport {0,0,${viewport.widthDu},${viewport.heightDu}}",
            )
        }

        val background = when (val field = root["background"]) {
            null, is JsonNull -> null
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
        statsObject.checkKeys(setOf("nodes", "pathCommands", "labels", "ops"))
            ?.let { failure -> return failure }
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
        if (element == null || element is JsonNull) {
            return HitRegionsParse.Parsed(emptyList())
        }
        val array = element as? JsonArray
            ?: return HitRegionsParse.Failure(invalid("DRAWOPS_TYPE", "hitRegions must be an array"))
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
            regionObject.checkKeys(setOf("id", "path", "rule"))
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
            ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: op kind must be a string"))
        return when (kind) {
            "save" -> {
                op.checkKeys(setOf("op"))?.let { return OpParse.Failure(it) }
                OpParse.Parsed(VisualDrawOp.Save)
            }

            "restore" -> {
                op.checkKeys(setOf("op"))?.let { return OpParse.Failure(it) }
                OpParse.Parsed(VisualDrawOp.Restore)
            }

            "concat" -> {
                op.checkKeys(setOf("op", "m"))?.let { return OpParse.Failure(it) }
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
                op.checkKeys(setOf("op", "path", "rule"))?.let { return OpParse.Failure(it) }
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
                op.checkKeys(setOf("op", "path", "fill", "stroke", "alpha"))?.let { return OpParse.Failure(it) }
                val path = parseCommands(op["path"])
                    ?: return OpParse.Failure(
                        invalid("DRAWOPS_PATH", "$at: path must be a non-empty M/L/C/Z command array starting with M"),
                    )
                val fill = when (val field = op["fill"]) {
                    null, is JsonNull -> null
                    is JsonPrimitive -> {
                        if (!COLOR_PATTERN.matches(field.content)) {
                            return OpParse.Failure(invalid("DRAWOPS_COLOR", "$at: fill must be \"#RRGGBB(AA)\""))
                        }
                        field.content
                    }
                    else -> return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: fill must be a color or null"))
                }
                val stroke = when (val field = op["stroke"]) {
                    null, is JsonNull -> null
                    is JsonObject -> when (val parsed = parseStroke(field, "$at.stroke")) {
                        is StrokeParse.Parsed -> parsed.spec
                        is StrokeParse.Failure -> return OpParse.Failure(parsed.failure)
                    }
                    else -> return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: stroke must be an object or null"))
                }
                if (fill == null && stroke == null) {
                    return OpParse.Failure(invalid("DRAWOPS_NO_PAINT", "$at: path needs a fill or a stroke"))
                }
                val alpha = op.primitiveDouble("alpha")
                    ?: return OpParse.Failure(invalid("DRAWOPS_TYPE", "$at: alpha must be a number"))
                if (alpha < 0.0 || alpha > 1.0) {
                    return OpParse.Failure(invalid("DRAWOPS_ALPHA", "$at: alpha must be within [0, 1]"))
                }
                OpParse.Parsed(VisualDrawOp.DrawPath(path, fill, stroke, alpha))
            }

            "label" -> {
                op.checkKeys(
                    setOf("op", "contentId", "x", "y", "rotationDeg", "hAlign", "vAlign", "color", "alpha"),
                )?.let { return OpParse.Failure(it) }
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
        stroke.checkKeys(setOf("width", "cap", "join", "miterLimit", "dash"))
            ?.let { failure -> return StrokeParse.Failure(failure) }
        val width = stroke.primitiveDouble("width")
            ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: width must be a number"))
        if (!width.isFinite() || width <= 0.0 || width > MAX_COORD) {
            return StrokeParse.Failure(invalid("DRAWOPS_BOUNDS", "$at: width must be within (0, 100000]"))
        }
        val capText = stroke.stringField("cap") ?: VisualStrokeCap.BUTT.json
        val cap = VisualStrokeCap.entries.firstOrNull { it.json == capText }
            ?: return StrokeParse.Failure(invalid("DRAWOPS_CAP", "$at: cap must be butt/round/square"))
        val joinText = stroke.stringField("join") ?: VisualStrokeJoin.MITER.json
        val join = VisualStrokeJoin.entries.firstOrNull { it.json == joinText }
            ?: return StrokeParse.Failure(invalid("DRAWOPS_JOIN", "$at: join must be miter/round/bevel"))
        val miterLimit = stroke.primitiveDouble("miterLimit")
            ?: return StrokeParse.Failure(invalid("DRAWOPS_TYPE", "$at: miterLimit must be a number"))
        if (!miterLimit.isFinite() || miterLimit < 1.0 || miterLimit > MAX_COORD) {
            return StrokeParse.Failure(invalid("DRAWOPS_MITER_LIMIT", "$at: miterLimit must be within [1, 100000]"))
        }
        val dash = when (val field = stroke["dash"]) {
            null, is JsonNull -> null
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

    // ---------------------------------------------------------------- JSON 帮助

    private fun JsonObject.checkKeys(allowed: Set<String>): VisualDrawOpsValidation.Invalid? {
        for (key in keys) {
            if (key !in allowed) {
                return invalid("DRAWOPS_UNKNOWN_FIELD", "unknown field \"$key\"")
            }
        }
        return null
    }

    private fun JsonObject.objectField(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.primitiveInt(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
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
