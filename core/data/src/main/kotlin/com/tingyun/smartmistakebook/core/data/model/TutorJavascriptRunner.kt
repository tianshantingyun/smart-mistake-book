package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionMode
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import com.tingyun.smartmistakebook.core.model.parameterNames
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class TutorJavascriptRunResult(val outcome: TutorToolOutcome, val stateJson: String?)

/** Replays pure JS prefixes, returning each real host receipt to the next isolated execution.
 * Host side effects execute once per call index. Persistent state commits only on full completion.
 */
internal suspend fun runTutorJavascript(
    engine: VisualExecutionEngine,
    script: String,
    stateJson: String?,
    runTool: suspend (TutorToolCall) -> TutorToolOutcome,
    runAction: suspend (TutorLocalActionRequest) -> TutorLobbyLocalActionOutcome,
): TutorJavascriptRunResult {
    fun failure(kind: String, message: String) = TutorJavascriptRunResult(
        TutorToolOutcome(TutorToolName.RUN_JAVASCRIPT, false,
            message.filter { !it.isISOControl() || it == '\n' }.take(TutorToolOutcome.MAX_TOOL_RESULT_CHARS), kind),
        stateJson,
    )
    if (script.isBlank()) return failure("JAVASCRIPT_INVALID_PROGRAM", "JavaScript 源码不能为空。")
    if (stateJson != null && figureUsageUtf8Bytes(stateJson) > VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
        return failure("JAVASCRIPT_STATE_OVER_BUDGET", "JavaScript state 超过 16 KiB UTF-8 上限。")
    }
    val history = mutableListOf<JsonElement>()
    try {
        if (stateJson != null) {
            try {
                Json.parseToJsonElement(stateJson)
            } catch (_: Exception) {
                return failure("JAVASCRIPT_INVALID_STATE", "JavaScript state 不是合法 JSON。")
            }
        }
        while (true) {
            val source = tutorJavascriptSource(script, history)
            if (figureUsageUtf8Bytes(source) > VisualExecutionBudget.MAX_SOURCE_JS_BYTES) {
                return failure("JAVASCRIPT_INPUT_OVER_BUDGET", "JavaScript 源码与重放回执超过 64 KiB UTF-8 上限。")
            }
            val execution = engine.execute(VisualExecutionRequest(
                programId = "tutor-javascript",
                sourceJs = source,
                sceneRevision = 1,
                stateJson = stateJson,
                viewport = VisualViewport(1, 1),
                timeMs = 0,
                seed = 0,
                theme = "light",
                executionMode = VisualExecutionMode.PROGRAM,
            ))
            val payload = when (execution) {
                is VisualExecutionResult.Computed -> Json.parseToJsonElement(execution.valueJson) as? JsonObject
                    ?: return failure("JAVASCRIPT_INVALID_PROGRAM", "JavaScript 调度回执不是对象。")
                is VisualExecutionResult.Rejected -> return failure("JAVASCRIPT_" + execution.reason.name,
                    "JavaScript 执行失败，" + execution.diagnostics)
                is VisualExecutionResult.Rendered -> return failure("JAVASCRIPT_INVALID_PROGRAM", "JavaScript 调度收到场景回执。")
            }
            when (payload["kind"]?.jsonPrimitive?.content) {
                "complete" -> {
                    if (payload["consumed"]?.jsonPrimitive?.intOrNull != history.size) {
                        return failure("JAVASCRIPT_NONDETERMINISTIC_REPLAY", "JavaScript 重放没有消费全部已执行回执。")
                    }
                    val value = payload["value"]?.toString()
                        ?: return failure("JAVASCRIPT_INVALID_PROGRAM", "JavaScript 必须显式返回 JSON 值。")
                    if (value.length > 6_000) {
                        return failure("JAVASCRIPT_OUTPUT_OVER_BUDGET", "JavaScript 返回 JSON 超过 6000 字符上限。")
                    }
                    val updated = payload["stateUpdated"]?.jsonPrimitive?.booleanOrNull == true
                    val nextState = if (updated) payload["state"]?.toString()
                        ?: return failure("JAVASCRIPT_INVALID_PROGRAM", "agent.setState 必须提供 JSON 值。") else stateJson
                    if (nextState != null && figureUsageUtf8Bytes(nextState) > VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
                        return failure("JAVASCRIPT_STATE_OVER_BUDGET", "agent.setState 超过 16 KiB UTF-8 上限。")
                    }
                    return TutorJavascriptRunResult(TutorToolOutcome(TutorToolName.RUN_JAVASCRIPT,
                        true, "JavaScript 已执行。", dataJson = value), nextState)
                }
                "yield" -> {
                    if (payload["index"]?.jsonPrimitive?.intOrNull != history.size) {
                        return failure("JAVASCRIPT_NONDETERMINISTIC_REPLAY", "JavaScript 重放调用顺序发生变化。")
                    }
                    if (history.size >= MAX_JAVASCRIPT_HOST_CALLS) {
                        return failure("JAVASCRIPT_HOST_CALL_LIMIT", "JavaScript 一次执行最多调用 $MAX_JAVASCRIPT_HOST_CALLS 个宿主工具或动作。")
                    }
                    val key = payload["key"] as? JsonPrimitive
                        ?: return failure("JAVASCRIPT_INVALID_HOST_CALL", "JavaScript 调用缺少重放标识。")
                    if (!key.isString) {
                        return failure("JAVASCRIPT_INVALID_HOST_CALL", "JavaScript 重放标识必须是字符串。")
                    }
                    val name = (payload["name"] as? JsonPrimitive)?.contentOrNull
                        ?: return failure("JAVASCRIPT_INVALID_HOST_CALL", "JavaScript 调用缺少工具或动作名。")
                    val arguments = payload["args"] as? JsonObject
                        ?: return failure("JAVASCRIPT_INVALID_HOST_CALL", "JavaScript 工具或动作参数必须为 JSON 对象。")
                    val result = when (payload["callType"]?.jsonPrimitive?.content) {
                        "tool" -> {
                            val tool = TutorToolName.entries.firstOrNull {
                                it.name.equals(name.trim(), ignoreCase = true)
                            }
                                ?: return failure(
                                    "JAVASCRIPT_INVALID_HOST_CALL",
                                    "JavaScript 调用了未知工具：${name.take(64)}",
                                )
                            if (tool == TutorToolName.RUN_JAVASCRIPT) {
                                return failure("JAVASCRIPT_INVALID_HOST_CALL", "JavaScript 不能递归调用 RUN_JAVASCRIPT。")
                            }
                            toolReceipt(runTool(javascriptToolCall(tool, arguments)))
                        }
                        "action" -> {
                            if (TutorLocalAction.fromActionId(name.trim()) == null) {
                                return failure(
                                    "JAVASCRIPT_INVALID_HOST_CALL",
                                    "JavaScript 调用了未知动作：${name.take(64)}",
                                )
                            }
                            actionReceipt(runAction(localActionRequest(name.trim(), arguments)))
                        }
                        else -> return failure("JAVASCRIPT_INVALID_HOST_CALL", "JavaScript 宿主调用类型无效。")
                    }
                    history.add(buildJsonObject {
                        put("key", key)
                        put("result", result)
                    })
                }
                else -> return failure("JAVASCRIPT_INVALID_PROGRAM", "JavaScript 调度回执类型无效。")
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        return failure("JAVASCRIPT_INVALID_HOST_CALL",
            "JavaScript 调度失败，" + (error.message ?: error.javaClass.simpleName))
    }
}

private const val MAX_JAVASCRIPT_HOST_CALLS = 20

internal fun javascriptToolCall(tool: TutorToolName, arguments: JsonObject): TutorToolCall {
    val parameterKeys = tool.parameterNames()
    val existing = arguments["parameters"]?.let { it as? JsonObject ?: error("parameters must be an object") }
    val parameters = buildJsonObject {
        existing?.forEach { (key, value) -> put(key, value) }
        arguments.filterKeys { it in parameterKeys }.forEach { (key, value) ->
            require(existing?.containsKey(key) != true) { "Duplicate tool parameter $key" }
            require(value is JsonPrimitive && value !is JsonNull) { "Tool parameter $key must be scalar" }
            put(key, JsonPrimitive(value.content))
        }
    }
    return buildJsonObject {
        arguments.filterKeys { it !in parameterKeys && it != "parameters" }.forEach { (key, value) -> put(key, value) }
        if ("rationale" !in arguments) put("rationale", "JavaScript 调度")
        put("parameters", parameters)
    }.toTutorToolCall(tool)
}

private fun toolReceipt(outcome: TutorToolOutcome): JsonObject = buildJsonObject {
    put("ok", outcome.ok)
    put("summaryMarkdown", outcome.summaryMarkdown)
    put("errorKind", outcome.errorKind?.let(::JsonPrimitive) ?: JsonNull)
    put("data", outcome.dataJson?.let { Json.parseToJsonElement(it) } ?: JsonNull)
}

private fun actionReceipt(outcome: TutorLobbyLocalActionOutcome): JsonObject = buildJsonObject {
    put("ok", outcome.ok?.let(::JsonPrimitive) ?: JsonNull)
    put("kind", outcome.kind)
    put("decision", outcome.decision)
    put("detail", outcome.detail?.let(::JsonPrimitive) ?: JsonNull)
    put("callId", outcome.callId?.let(::JsonPrimitive) ?: JsonNull)
    put("artifactId", outcome.artifactId?.let(::JsonPrimitive) ?: JsonNull)
}

/** Function constructor keeps user code outside the replay controller's lexical scope. */
internal fun tutorJavascriptSource(script: String, history: List<JsonElement>): String = """
    const history = JSON.parse(${JsonPrimitive(JsonArray(history).toString())});
    const parse = JSON.parse, stringify = JSON.stringify;
    const user = new Function('agent', 'input', '"use strict";\n' + ${JsonPrimitive(script)});
    let cursor = 0, pending = null, stateUpdated = false, nextState = input.state;
    const sentinel = {};
    function stable(value) {
        if (Array.isArray(value)) return '[' + value.map(stable).join(',') + ']';
        if (value && typeof value === 'object') return '{' + Object.keys(value).sort()
            .map(key => stringify(key) + ':' + stable(value[key])).join(',') + '}';
        return stringify(value);
    }
    function call(callType, name, args) {
        if (pending) throw sentinel;
        if (typeof name !== 'string' || !args || typeof args !== 'object' || Array.isArray(args))
            throw new TypeError('agent call needs a name and argument object');
        args = parse(stringify(args));
        const key = callType + ':' + name + ':' + stable(args);
        const index = cursor++;
        if (index < history.length) {
            if (history[index].key !== key) throw new Error('Non-deterministic JavaScript host-call replay');
            return parse(stringify(history[index].result));
        }
        pending = {kind:'yield', index:index, key:key, callType:callType, name:name, args:args};
        throw sentinel;
    }
    const agent = Object.freeze({
        get state() { return nextState === undefined ? null : nextState; },
        callTool: (name, args = {}) => call('tool', name, args),
        callAction: (name, args = {}) => call('action', name, args),
        setState: value => {
            const text = stringify(value);
            if (typeof text !== 'string') throw new TypeError('agent.setState requires a JSON value');
            nextState = parse(text); stateUpdated = true;
        }
    });
    let value;
    try { value = user(agent, input); } catch (error) { if (!pending) throw error; }
    if (pending) return pending;
    if (cursor !== history.length) throw new Error('JavaScript replay did not consume all host receipts');
    if (value && typeof value.then === 'function') throw new TypeError('JavaScript must return synchronous JSON');
    if (typeof stringify(value) !== 'string') throw new TypeError('JavaScript must return a JSON value');
    return {kind:'complete', value:value, consumed:cursor, stateUpdated:stateUpdated, state:nextState};
""".trimIndent()
