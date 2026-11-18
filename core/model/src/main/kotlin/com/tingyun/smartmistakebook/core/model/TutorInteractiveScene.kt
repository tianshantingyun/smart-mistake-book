package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SceneV1 的**交互面**（4E.2）：原生控件与 `NativeTutorFormV1`。
 *
 * 它消灭的具体失败：编译器此前把 `controls`/`form` 一律拒绝，模型只能画静态图；
 * 现在这两个字段被同一套严格校验接住，宿主按类型渲染原生控件、按 `reduce` 回写状态，
 * 表单草稿独立持久化。控件与表单都**不是**绘制指令，不进入 DrawOps，因此静态 painter 不变。
 *
 * 校验是双份的：`visual-compiler.js` 在模型产出的 `render()` 返回值上先拒一次（fail-closed，
 * 诊断回喂模型），本文件在宿主读回快照时再拒一次（不信任"自称 validated"）。两侧常量与
 * 字段名必须逐字一致；改一处必须同改另一处（JVM 测试与 Node 测试台各自钉住）。
 */
object TutorInteractiveSceneBudget {
    const val MAX_CONTROLS = 8
    const val MAX_CONTROL_ID_CHARS = 32
    const val MAX_CONTROL_LABEL_CHARS = 40
    const val MAX_CONTROL_SEMANTICS_CHARS = 80
    const val MAX_CONTROL_OPTIONS = 8
    const val MAX_FORM_ITEMS = 4
    const val MAX_FORM_PROMPT_CHARS = 200
    const val MAX_FORM_OPTION_LABEL_CHARS = 80
    const val MAX_FORM_TITLE_CHARS = 80
    const val MAX_FORM_RATIONALE_LABEL_CHARS = 40
    const val MAX_FORM_RATIONALE_INPUT_CHARS = 1_000
    const val MAX_FORM_ID_CHARS = 64
    const val MAX_STATE_JSON_CHARS = 16_384
}

enum class TutorControlKind(val wire: String) {
    BUTTON("button"),
    TOGGLE("toggle"),
    RANGE("range"),
    SELECT("select"),
    ;

    companion object {
        fun fromWire(value: String): TutorControlKind? = entries.firstOrNull { it.wire == value }
    }
}

data class TutorControlOption(val value: String, val label: String) {
    init {
        require(value.isNotBlank() && value.length <= 64) { "A control option value must be 1..64 chars" }
        require(label.isNotBlank() && label.length <= TutorInteractiveSceneBudget.MAX_FORM_OPTION_LABEL_CHARS) {
            "A control option label must be 1..${TutorInteractiveSceneBudget.MAX_FORM_OPTION_LABEL_CHARS} chars"
        }
    }
}

/**
 * 一个原生控件。值域按 kind 分列而非塞进一个 JSON 字符串：宿主渲染控件时不需要再解析
 * 第二次，非法组合（如 range 没有 min/max）在构造期就成立不了。
 */
data class TutorVisualControl(
    val id: String,
    val kind: TutorControlKind,
    val label: String,
    val enabled: Boolean = true,
    val semantics: String? = null,
    val booleanValue: Boolean? = null,
    val numberValue: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val stringValue: String? = null,
    val options: List<TutorControlOption> = emptyList(),
) {
    init {
        require(id.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,31}"))) {
            "A control id must be 1..${TutorInteractiveSceneBudget.MAX_CONTROL_ID_CHARS} letters/digits/_/-"
        }
        require(label.isNotBlank() && label.length <= TutorInteractiveSceneBudget.MAX_CONTROL_LABEL_CHARS) {
            "A control label must be 1..${TutorInteractiveSceneBudget.MAX_CONTROL_LABEL_CHARS} chars"
        }
        require(semantics == null || semantics.length <= TutorInteractiveSceneBudget.MAX_CONTROL_SEMANTICS_CHARS)
        when (kind) {
            TutorControlKind.BUTTON -> require(
                booleanValue == null && numberValue == null && stringValue == null &&
                    min == null && max == null && step == null && options.isEmpty(),
            ) { "A button carries no value" }
            TutorControlKind.TOGGLE -> require(
                booleanValue != null && numberValue == null && stringValue == null &&
                    min == null && max == null && step == null && options.isEmpty(),
            ) { "A toggle carries only a boolean value" }
            TutorControlKind.RANGE -> {
                require(numberValue != null && min != null && max != null) {
                    "A range needs value/min/max"
                }
                require(min!! < max!!) { "A range needs min < max" }
                require(numberValue!! in min!!..max!!) { "A range value must be within min..max" }
                require(step == null || step!! > 0.0) { "A range step must be positive" }
                require(booleanValue == null && stringValue == null && options.isEmpty())
            }
            TutorControlKind.SELECT -> {
                require(stringValue != null && options.size in 1..TutorInteractiveSceneBudget.MAX_CONTROL_OPTIONS) {
                    "A select needs a value and 1..${TutorInteractiveSceneBudget.MAX_CONTROL_OPTIONS} options"
                }
                require(options.any { it.value == stringValue }) { "A select value must be one of its options" }
                require(booleanValue == null && numberValue == null && min == null && max == null && step == null)
            }
        }
        require(options.map { it.value }.distinct().size == options.size) {
            "Control option values must be distinct"
        }
    }
}

data class NativeTutorFormV1(
    val formId: String,
    val title: String? = null,
    val items: List<NativeTutorFormItem>,
    val rationaleLabel: String = "理由",
) {
    init {
        require(formId.isNotBlank() && formId.length <= TutorInteractiveSceneBudget.MAX_FORM_ID_CHARS)
        require(title == null || title.length <= TutorInteractiveSceneBudget.MAX_FORM_TITLE_CHARS)
        require(items.size in 1..TutorInteractiveSceneBudget.MAX_FORM_ITEMS) {
            "A form carries 1..${TutorInteractiveSceneBudget.MAX_FORM_ITEMS} items"
        }
        require(items.map { it.id }.distinct().size == items.size) { "Form item ids must be distinct" }
        require(rationaleLabel.isNotBlank() &&
            rationaleLabel.length <= TutorInteractiveSceneBudget.MAX_FORM_RATIONALE_LABEL_CHARS)
    }
}

data class NativeTutorFormItem(
    val id: String,
    val prompt: String,
    val options: List<TutorControlOption>,
) {
    init {
        require(id.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,31}"))) { "A form item id is invalid" }
        require(prompt.isNotBlank() && prompt.length <= TutorInteractiveSceneBudget.MAX_FORM_PROMPT_CHARS)
        require(options.size in 2..6) { "A radio group carries 2..6 options" }
        require(options.map { it.value }.distinct().size == options.size) {
            "Form option values must be distinct"
        }
    }
}

data class TutorInteractiveSpec(
    val controls: List<TutorVisualControl> = emptyList(),
    val form: NativeTutorFormV1? = null,
) {
    init {
        require(controls.size <= TutorInteractiveSceneBudget.MAX_CONTROLS)
        require(controls.map { it.id }.distinct().size == controls.size) { "Control ids must be distinct" }
    }

    val isEmpty: Boolean get() = controls.isEmpty() && form == null
}

/**
 * 从快照里的 SceneV1 JSON 读回交互面。与编译器同名的字段/上限；未知键拒绝（严格面与
 * `visual-compiler.js` 一致）。没有 controls/form 时返回 [TutorInteractiveSpec] 空实例
 * （静态图仍可走原有 PNG 预览，不因本函数失败）。
 */
fun parseTutorInteractiveSpec(sceneJson: String): TutorInteractiveSpec {
    val scene = runCatching { Json.parseToJsonElement(sceneJson) }.getOrNull() as? JsonObject
        ?: throw IllegalArgumentException("scene JSON is not an object")
    val controlsElement = scene["controls"]
    val controls = when (controlsElement) {
        null, JsonNullValue -> emptyList()
        is JsonArray -> controlsElement.mapIndexed { index, element ->
            parseControl(element, index)
        }
        else -> throw IllegalArgumentException("scene.controls must be an array")
    }
    val form = scene["form"]?.let { element ->
        if (element is JsonObject) parseForm(element) else throw IllegalArgumentException("scene.form must be an object")
    }
    return TutorInteractiveSpec(controls = controls, form = form)
}

private val JsonNullValue = kotlinx.serialization.json.JsonNull

private fun JsonObject.strictKeys(label: String, allowed: Set<String>) {
    val unknown = keys - allowed
    require(unknown.isEmpty()) { "$label has unknown keys: ${unknown.sorted().joinToString(",").take(64)}" }
}

private fun parseControl(element: JsonElement, index: Int): TutorVisualControl {
    val at = "scene.controls[$index]"
    val control = element as? JsonObject ?: throw IllegalArgumentException("$at must be an object")
    val kindText = control.string("kind") ?: throw IllegalArgumentException("$at.kind is required")
    val kind = TutorControlKind.fromWire(kindText)
        ?: throw IllegalArgumentException("$at.kind is not a supported control kind")
    val common = setOf("id", "kind", "label", "enabled", "semantics")
    val allowed = when (kind) {
        TutorControlKind.BUTTON -> common
        TutorControlKind.TOGGLE -> common + "value"
        TutorControlKind.RANGE -> common + setOf("value", "min", "max", "step")
        TutorControlKind.SELECT -> common + setOf("value", "options")
    }
    control.strictKeys(at, allowed)
    val id = control.string("id") ?: throw IllegalArgumentException("$at.id is required")
    val label = control.string("label") ?: throw IllegalArgumentException("$at.label is required")
    val enabled = control["enabled"]?.let {
        (it as? JsonPrimitive)?.booleanOrNull ?: throw IllegalArgumentException("$at.enabled must be a boolean")
    } ?: true
    val semantics = control["semantics"]?.let {
        (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
            ?: throw IllegalArgumentException("$at.semantics must be a string")
    }
    return when (kind) {
        TutorControlKind.BUTTON -> TutorVisualControl(id, kind, label, enabled, semantics)
        TutorControlKind.TOGGLE -> TutorVisualControl(
            id, kind, label, enabled, semantics,
            booleanValue = control["value"]?.let {
                (it as? JsonPrimitive)?.booleanOrNull
                    ?: throw IllegalArgumentException("$at.value must be a boolean")
            } ?: throw IllegalArgumentException("$at.value is required"),
        )
        TutorControlKind.RANGE -> TutorVisualControl(
            id, kind, label, enabled, semantics,
            numberValue = control.number("value", at),
            min = control.number("min", at),
            max = control.number("max", at),
            step = control["step"]?.let { control.number("step", at) },
        )
        TutorControlKind.SELECT -> {
            val options = parseOptions(control["options"], "$at.options", max = TutorInteractiveSceneBudget.MAX_CONTROL_OPTIONS)
            TutorVisualControl(
                id, kind, label, enabled, semantics,
                stringValue = control.string("value") ?: throw IllegalArgumentException("$at.value is required"),
                options = options,
            )
        }
    }
}

private fun parseForm(form: JsonObject): NativeTutorFormV1 {
    form.strictKeys("scene.form", setOf("formId", "title", "items", "rationaleLabel"))
    val formId = form.string("formId") ?: throw IllegalArgumentException("scene.form.formId is required")
    val title = form.string("title")
    val rationaleLabel = form.string("rationaleLabel") ?: "理由"
    val items = form["items"] as? JsonArray ?: throw IllegalArgumentException("scene.form.items must be an array")
    return NativeTutorFormV1(
        formId = formId,
        title = title,
        rationaleLabel = rationaleLabel,
        items = items.mapIndexed { index, element ->
            val at = "scene.form.items[$index]"
            val item = element as? JsonObject ?: throw IllegalArgumentException("$at must be an object")
            item.strictKeys(at, setOf("id", "kind", "prompt", "options"))
            require(item.string("kind") == "radioGroup") { "$at.kind must be radioGroup" }
            NativeTutorFormItem(
                id = item.string("id") ?: throw IllegalArgumentException("$at.id is required"),
                prompt = item.string("prompt") ?: throw IllegalArgumentException("$at.prompt is required"),
                options = parseOptions(item["options"], "$at.options", max = 6),
            )
        },
    )
}

private fun parseOptions(element: JsonElement?, at: String, max: Int): List<TutorControlOption> {
    val array = element as? JsonArray ?: throw IllegalArgumentException("$at must be an array")
    require(array.size in 1..max) { "$at must carry 1..$max options" }
    return array.mapIndexed { index, optionElement ->
        val option = optionElement as? JsonObject
            ?: throw IllegalArgumentException("$at[$index] must be an object")
        option.strictKeys("$at[$index]", setOf("value", "label"))
        TutorControlOption(
            value = option.string("value") ?: throw IllegalArgumentException("$at[$index].value is required"),
            label = option.string("label") ?: throw IllegalArgumentException("$at[$index].label is required"),
        )
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content

private fun JsonObject.number(key: String, at: String): Double {
    val primitive = this[key] as? JsonPrimitive
    val value = primitive?.doubleOrNull
    require(value != null && value.isFinite()) { "$at.$key must be a finite number" }
    return value
}

/**
 * `reduce(state, event)` 的输入事件（宿主构造，模型只读）。`sequence` 由宿主单调递增，
 * 过期 revision / 重复 sequence 由宿主拒绝，不依赖程序的自觉。
 */
fun visualControlEventJson(
    programId: String,
    sceneId: String?,
    sceneRevision: Int,
    sequence: Long,
    kind: String,
    controlId: String,
    value: JsonElement,
): String = buildString {
    append('{')
    append("\"programId\":").append(JsonPrimitive(programId))
    if (sceneId != null) append(",\"sceneId\":").append(JsonPrimitive(sceneId))
    append(",\"sceneRevision\":").append(sceneRevision)
    append(",\"sequence\":").append(sequence)
    append(",\"kind\":").append(JsonPrimitive(kind))
    append(",\"controlId\":").append(JsonPrimitive(controlId))
    append(",\"value\":").append(value)
    append('}')
}
