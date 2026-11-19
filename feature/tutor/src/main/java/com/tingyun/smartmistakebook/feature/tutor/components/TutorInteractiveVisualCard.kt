package com.tingyun.smartmistakebook.feature.tutor.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrame
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrameResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualPort
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualReduceResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualScene
import com.tingyun.smartmistakebook.core.model.NativeTutorFormV1
import com.tingyun.smartmistakebook.core.model.TutorControlKind
import com.tingyun.smartmistakebook.core.model.TutorInteractiveSceneBudget
import com.tingyun.smartmistakebook.core.model.TutorInteractiveSpec
import com.tingyun.smartmistakebook.core.model.TutorVisualControl
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import com.tingyun.smartmistakebook.core.model.parseTutorInteractiveSpec
import com.tingyun.smartmistakebook.core.model.tutorTurnVisualRef
import com.tingyun.smartmistakebook.core.model.visualControlEventJson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private data class InteractiveVisualUi(
    val scene: TutorInteractiveVisualScene,
    val spec: TutorInteractiveSpec,
    val image: ImageBitmap,
    val stateJson: String,
    val controlValues: Map<String, JsonElement>,
    val answers: Map<String, String>,
    val rationale: String,
    val busy: Boolean = false,
    val message: String? = null,
    val submitted: Boolean = false,
)

private data class InteractiveDraft(
    val answers: Map<String, String>,
    val rationale: String,
    val controls: Map<String, JsonElement>,
)

/**
 * 4E.2 交互图卡：静态帧由 DrawOps painter 渲染（与预览同源），控件/表单是**原生 Compose**
 * 组件；每次变化走 `reduce` → 重画，草稿按 500ms 节流写 `ui_state_json`。
 *
 * 只对带 `controls`/`form` 的 SceneV1 出现；静态图继续走原有 PNG 配图卡。加载失败不
 * 伪造一张空图：卡片显示一条可读原因并停止重试。
 */
@Composable
internal fun TutorInteractiveVisualCard(
    messageId: String?,
    toolTraceJson: String?,
    port: TutorInteractiveVisualPort?,
    onSubmitForm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messageId == null || port == null) return
    val scope = rememberCoroutineScope()
    var ui by remember(messageId) { mutableStateOf<InteractiveVisualUi?>(null) }
    var loadFailure by remember(messageId) { mutableStateOf<String?>(null) }
    var sequence by remember(messageId) { mutableLongStateOf(0L) }
    // 同一时刻只跑一个 reduce / 一个草稿写：快速连点与 500ms 防抖不能各自从同一旧状态出发。
    val reduceMutex = remember(messageId) { Mutex() }
    val draftMutex = remember(messageId) { Mutex() }

    LaunchedEffect(messageId, toolTraceJson, port) {
        loadFailure = null
        val ref = tutorTurnVisualRef(toolTraceJson)
        val scene = runCatching {
            port.read(messageId, 0, ref?.programId, ref?.snapshotId)
        }.getOrElse { failure ->
            loadFailure = "交互场景读取失败：${failure.message?.take(120) ?: failure::class.simpleName}"
            return@LaunchedEffect
        } ?: return@LaunchedEffect
        val spec = runCatching { parseTutorInteractiveSpec(scene.sceneJson) }.getOrElse { failure ->
            loadFailure = "交互场景不合法：${failure.message?.take(120)}"
            return@LaunchedEffect
        }
        if (spec.isEmpty) return@LaunchedEffect
        when (val rendered = port.render(scene, scene.stateJson)) {
            is TutorInteractiveVisualFrameResult.Rendered -> {
                val draft = parseDraft(scene.uiStateJson)
                val initialControls = spec.controls.associate { control -> control.id to control.initialValue() }
                ui = InteractiveVisualUi(
                    scene = scene,
                    spec = spec,
                    image = decodeFrame(rendered.frame) ?: run {
                        loadFailure = "交互帧图片解码失败"
                        return@LaunchedEffect
                    },
                    stateJson = rendered.frame.stateJson,
                    controlValues = initialControls + draft.controls.filterKeys { it in initialControls },
                    answers = draft.answers,
                    rationale = draft.rationale,
                )
            }
            is TutorInteractiveVisualFrameResult.Rejected -> {
                loadFailure = "交互帧未生成：${rendered.diagnostic.take(160)}"
            }
        }
    }

    val current = ui ?: run {
        loadFailure?.let { reason ->
            Text(
                text = reason,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = modifier.testTag("tutor_interactive_visual_error"),
            )
        }
        return
    }

    fun saveDraft(
        scene: TutorInteractiveVisualScene,
        state: String,
        answers: Map<String, String>,
        rationale: String,
        controls: Map<String, JsonElement>,
    ) {
        val form = current.spec.form
        val draft = buildJsonObject {
            put("state", runCatching { Json.parseToJsonElement(state) }.getOrNull() ?: JsonObject(emptyMap()))
            if (form != null) {
                put("form", buildJsonObject {
                    put("answers", buildJsonObject { answers.forEach { (key, value) -> put(key, value) } })
                    put("rationale", rationale)
                    put("formId", form.formId)
                })
            }
            put("controls", buildJsonObject { controls.forEach { (key, value) -> put(key, value) } })
        }.toString()
        val digestPayload = buildString {
            append(scene.programId).append('\u001f').append(scene.sceneRevision).append('\u001f')
            append(form?.formId.orEmpty())
            answers.toSortedMap().forEach { (key, value) -> append('\u001e').append(key).append('=').append(value) }
            controls.toSortedMap().forEach { (key, value) -> append('\u001e').append(key).append('=').append(value) }
        }
        val digest = figureSha256Hex(digestPayload.toByteArray(Charsets.UTF_8))
        scope.launch {
            draftMutex.withLock {
                port.saveDraft(
                    scene.messageId,
                    scene.ordinal,
                    scene.revision,
                    draft,
                    digest,
                )
            }
        }
    }

    fun dispatch(control: TutorVisualControl, value: JsonElement, kind: String) {
        scope.launch {
            reduceMutex.withLock {
                val latest = ui ?: return@withLock
                if (latest.submitted) return@withLock
                sequence += 1
                val event = visualControlEventJson(
                    programId = latest.scene.programId,
                    sceneId = null,
                    sceneRevision = latest.scene.sceneRevision,
                    sequence = sequence,
                    kind = kind,
                    controlId = control.id,
                    value = value,
                )
                ui = latest.copy(busy = true, message = null)
                when (val reduced = port.reduce(latest.scene.copy(stateJson = latest.stateJson), event)) {
                    is TutorInteractiveVisualReduceResult.Updated -> {
                        val nextControls = latest.controlValues + (control.id to value)
                        ui = latest.copy(
                            image = decodeFrame(reduced.frame) ?: latest.image,
                            stateJson = reduced.frame.stateJson,
                            controlValues = nextControls,
                            busy = false,
                        )
                        saveDraft(
                            latest.scene,
                            reduced.frame.stateJson,
                            latest.answers,
                            latest.rationale,
                            nextControls,
                        )
                    }
                    is TutorInteractiveVisualReduceResult.Rejected -> {
                        ui = latest.copy(busy = false, message = reduced.diagnostic.take(160))
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("tutor_interactive_visual"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                bitmap = current.image,
                contentDescription = "可交互的教学图",
                modifier = Modifier.fillMaxWidth().testTag("tutor_interactive_visual_canvas"),
                contentScale = ContentScale.Fit,
            )
            current.spec.controls.forEach { control ->
                when (control.kind) {
                    TutorControlKind.RANGE -> {
                        val value = current.controlValues[control.id]?.jsonPrimitive?.doubleOrNull
                            ?: control.numberValue ?: 0.0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${control.label}：${"%.2f".format(value)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { next ->
                                    ui = current.copy(
                                        controlValues = current.controlValues + (control.id to JsonPrimitive(next)),
                                    )
                                },
                                onValueChangeFinished = {
                                    val committed = ui?.controlValues?.get(control.id) ?: JsonPrimitive(value)
                                    dispatch(control, committed, "change")
                                },
                                valueRange = (control.min ?: 0.0).toFloat()..(control.max ?: 1.0).toFloat(),
                                enabled = control.enabled && !current.busy && !current.submitted,
                                modifier = Modifier.weight(2f).testTag("tutor_visual_control_${control.id}"),
                            )
                        }
                    }
                    TutorControlKind.TOGGLE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(control.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(
                                checked = current.controlValues[control.id]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                                    ?: control.booleanValue ?: false,
                                onCheckedChange = { next ->
                                    dispatch(control, JsonPrimitive(next), "change")
                                },
                                enabled = control.enabled && !current.busy && !current.submitted,
                                modifier = Modifier.testTag("tutor_visual_control_${control.id}"),
                            )
                        }
                    }
                    TutorControlKind.SELECT -> {
                        Text(control.label, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            control.options.forEach { option ->
                                FilterChip(
                                    selected = current.controlValues[control.id]?.jsonPrimitive?.content == option.value,
                                    onClick = { dispatch(control, JsonPrimitive(option.value), "change") },
                                    label = { Text(option.label) },
                                    enabled = control.enabled && !current.busy && !current.submitted,
                                    modifier = Modifier.testTag("tutor_visual_control_${control.id}_${option.value}"),
                                )
                            }
                        }
                    }
                    TutorControlKind.BUTTON -> {
                        OutlinedButton(
                            onClick = { dispatch(control, JsonPrimitive(true), "activate") },
                            enabled = control.enabled && !current.busy && !current.submitted,
                            modifier = Modifier.testTag("tutor_visual_control_${control.id}"),
                        ) { Text(control.label) }
                    }
                }
            }
            current.spec.form?.let { form ->
                NativeVisualForm(
                    form = form,
                    answers = current.answers,
                    rationale = current.rationale,
                    busy = current.busy,
                    submitted = current.submitted,
                    onAnswer = { itemId, value ->
                        ui = current.copy(answers = current.answers + (itemId to value))
                    },
                    onRationale = { text ->
                        if (text.length <= TutorInteractiveSceneBudget.MAX_FORM_RATIONALE_INPUT_CHARS) {
                            ui = current.copy(rationale = text)
                        }
                    },
                    onSubmit = {
                        if (!current.busy && !current.submitted) {
                            val text = formSubmissionText(form, current.answers, current.rationale)
                            ui = current.copy(submitted = true, busy = true)
                            onSubmitForm(text)
                            scope.launch {
                                draftMutex.withLock {
                                    port.saveDraft(
                                        current.scene.messageId,
                                        current.scene.ordinal,
                                        current.scene.revision,
                                        null,
                                        null,
                                    )
                                }
                                ui = ui?.copy(busy = false)
                            }
                        }
                    },
                )
            }
            if (current.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("tutor_interactive_visual_busy"),
                    strokeWidth = 2.dp,
                )
            }
            current.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("tutor_interactive_visual_message"),
                )
            }
        }
    }

    // 表单输入 500ms 合并保存：只在答案/理由变化时写一次 checkpoint（不是每键一写）。
    // `submitted` 进 key：提交会取消尚未醒来的防抖写，避免它在清草稿之后把旧答案写回去。
    LaunchedEffect(current.answers, current.rationale, current.submitted) {
        if (current.spec.form == null || current.submitted) return@LaunchedEffect
        delay(500)
        if (ui?.submitted == true) return@LaunchedEffect
        saveDraft(
            current.scene,
            current.stateJson,
            current.answers,
            current.rationale,
            current.controlValues,
        )
    }
}

@Composable
private fun NativeVisualForm(
    form: NativeTutorFormV1,
    answers: Map<String, String>,
    rationale: String,
    busy: Boolean,
    submitted: Boolean,
    onAnswer: (String, String) -> Unit,
    onRationale: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        form.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        form.items.forEach { item ->
            Text(item.prompt, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.options.forEach { option ->
                    FilterChip(
                        selected = answers[item.id] == option.value,
                        onClick = { onAnswer(item.id, option.value) },
                        label = { Text(option.label) },
                        enabled = !busy && !submitted,
                        modifier = Modifier.testTag("tutor_form_${item.id}_${option.value}"),
                    )
                }
            }
        }
        OutlinedTextField(
            value = rationale,
            onValueChange = onRationale,
            label = { Text(form.rationaleLabel) },
            enabled = !busy && !submitted,
            modifier = Modifier.fillMaxWidth().testTag("tutor_form_rationale"),
        )
        Button(
            onClick = onSubmit,
            enabled = !busy && !submitted && form.items.all { answers[it.id] != null },
            modifier = Modifier.testTag("tutor_form_submit"),
        ) { Text(if (submitted) "已提交" else "提交") }
    }
}

private fun TutorVisualControl.initialValue(): JsonElement = when (kind) {
    TutorControlKind.BUTTON -> JsonPrimitive(true)
    TutorControlKind.TOGGLE -> JsonPrimitive(booleanValue ?: false)
    TutorControlKind.RANGE -> JsonPrimitive(numberValue ?: 0.0)
    TutorControlKind.SELECT -> JsonPrimitive(stringValue ?: options.firstOrNull()?.value.orEmpty())
}

private fun decodeFrame(frame: TutorInteractiveVisualFrame): ImageBitmap? =
    runCatching {
        BitmapFactory.decodeByteArray(frame.pngBytes, 0, frame.pngBytes.size)?.asImageBitmap()
    }.getOrNull()

private fun parseDraft(json: String?): InteractiveDraft {
    if (json.isNullOrBlank()) return InteractiveDraft(emptyMap(), "", emptyMap())
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        ?: return InteractiveDraft(emptyMap(), "", emptyMap())
    val form = root["form"] as? JsonObject
    val answers = (form?.get("answers") as? JsonObject)
        ?.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
        }
        ?.toMap()
        .orEmpty()
    val rationale = (form?.get("rationale") as? JsonPrimitive)
        ?.takeIf { it.isString }?.content.orEmpty()
    val controls = (root["controls"] as? JsonObject)
        ?.mapNotNull { (key, value) ->
            value.takeIf { it is JsonPrimitive }?.let { key to it }
        }
        ?.toMap()
        .orEmpty()
    return InteractiveDraft(answers, rationale, controls)
}

private fun formSubmissionText(
    form: NativeTutorFormV1,
    answers: Map<String, String>,
    rationale: String,
): String = buildString {
    append("我做了图里的检查题：")
    form.items.forEachIndexed { index, item ->
        val chosen = item.options.firstOrNull { it.value == answers[item.id] }
        append('\n').append(index + 1).append(". ").append(item.prompt)
        if (chosen != null) append(" → ").append(chosen.label)
    }
    val trimmed = rationale.trim().take(TutorInteractiveSceneBudget.MAX_FORM_RATIONALE_INPUT_CHARS)
    if (trimmed.isNotEmpty()) append("\n").append(form.rationaleLabel).append("：").append(trimmed)
}.take(1_200)
