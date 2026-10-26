package com.tingyun.smartmistakebook.visualjs

import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * 批次 22 T1（4E.2 前置）的跨进程线协议常量。
 *
 * **为什么用 Kotlin 常量而不是 AIDL `const`**：AIDL 常量只能被 Java 生成代码引用，
 * 而 service/client/仪器化用例三处都要用同一组数值；放 Kotlin 单源避免 AIDL 与 Kotlin
 * 各抄一份后漂移。AIDL 文件只保留方法签名，语义注释指向这里。
 *
 * **它消灭的具体失败**：状态码散落在 AIDL 注释、service 分支和 client 映射里时，改一个
 * 数值就会出现"service 回 ACCEPTED、client 当 BAD_REQUEST"的静默错配——本类型把数值
 * 收成一处，并让仪器化用例直接引用同一常量。
 */
internal object VisualJsWire {
    /**
     * 协议版本；宿主在 onServiceConnected 里不等即解绑（fail-closed）。
     * 批次 25 T2（KD-76）：1→2——新增 `isReady()` ready 查询。两端同 APK 发布，
     * 旧/新组合在握手处 fail-closed 解绑（既有语义）。
     */
    const val PROTOCOL_VERSION: Int = 2

    // IVisualJsService.execute / initialize 的同步返回码。
    const val STATUS_ACCEPTED: Int = 0
    const val STATUS_BUSY: Int = 1
    const val STATUS_STALE_GENERATION: Int = 2
    const val STATUS_BAD_REQUEST: Int = 3
    const val STATUS_SHUTTING_DOWN: Int = 4

    // IVisualJsCallback.onResult 的结果状态码。
    const val RESULT_OK: Int = 0
    const val RESULT_REJECTED: Int = 1

    // IVisualJsCallback.onFatal 的原因码（只做诊断，宿主统一映射到 HARD_DEADLINE_EXCEEDED）。
    const val FATAL_INTERNAL: Int = 1

    /**
     * 历史预留的 wire 字面量。T2 的最终裁定是**不新增**
     * `VisualExecutionRejection.UNKNOWN`：不可归属失败折成 `INVALID_PROGRAM`，由
     * `VisualJsFailureClassification` 的 `[UNATTRIBUTED]` 诊断前缀区分（R3 §6.6）。
     * 服务端当前不发送该值；客户端若收到未映射 reason（含本值），走
     * [VisualJsProtocol.decodeRejection] 的显式拒绝分支，不静默当成普通程序错。
     */
    const val REASON_UNKNOWN: String = "UNKNOWN"
}

/** 请求/结果信封超过契约 §4 单向上限（256 KiB UTF-8）。 */
internal class VisualJsBudgetException(message: String) : Exception(message)

/** 信封结构非法（客户端 bug / 版本错配），不是程序错误。 */
internal class VisualJsProtocolException(message: String) : Exception(message)

/** 宿主与服务对预算表不一致（构建版本漂移）；必须 fail-closed，不能各自按自己的预算跑。 */
internal class VisualJsLimitsMismatchException(message: String) : Exception(message)

/**
 * 跨进程信封编解码（app 模块单源；core:domain/core:data 不引用 AIDL/http）。
 *
 * 请求信封：
 * ```
 * { "programJson": {programId, sourceJs, revision, stateJson|null, viewport,
 *                   timeMs, seed, theme, explicitUserRetry},
 *   "limits": {maxSourceJsBytes, maxStateJsonBytes, maxTransactionJsonBytes,
 *              maxHeapBytes, maxStackBytes, cooperativePhaseTimeoutMillis,
 *              serviceHardDeadlineMillis, hostPhaseDeadlineMillis} }
 * ```
 * 结果信封（OK）：`{sceneJson, sceneSchemaVersion, contentIds[]}`。
 * 结果信封（REJECTED）：`{reason, diagnostics}`。
 *
 * **它消灭的具体失败**：不用 org.json 之外的序列化依赖时，源码/state 里的引号、反斜杠、
 * 中文、emoji 若手工拼字符串会破坏 JSON 边界；`JSONObject` 负责转义，并且读侧在**流式
 * 计数**后按预算拒绝，不会先无界建树再拒绝（契约 §4）。
 *
 * **limits 不是可被宿主放大的预算**：本类只接受与 `VisualExecutionBudget` **逐项相等**的
 * limits；不一致直接 [VisualJsLimitsMismatchException]。这样"宿主降级预算、服务仍按旧预算"
 * 或反向的版本漂移都会显式失败，而不是悄悄按两套预算跑。放宽预算必须同批改两端。
 */
internal object VisualJsProtocol {
    private const val KEY_PROGRAM = "programJson"
    private const val KEY_LIMITS = "limits"
    private const val KEY_SCENE = "sceneJson"
    private const val KEY_SCENE_SCHEMA = "sceneSchemaVersion"
    private const val KEY_CONTENT_IDS = "contentIds"
    private const val KEY_REASON = "reason"
    private const val KEY_DIAGNOSTICS = "diagnostics"

    private const val KEY_MAX_SOURCE_BYTES = "maxSourceJsBytes"
    private const val KEY_MAX_STATE_BYTES = "maxStateJsonBytes"
    private const val KEY_MAX_TRANSACTION_BYTES = "maxTransactionJsonBytes"
    private const val KEY_MAX_HEAP_BYTES = "maxHeapBytes"
    private const val KEY_MAX_STACK_BYTES = "maxStackBytes"
    private const val KEY_COOPERATIVE_TIMEOUT = "cooperativePhaseTimeoutMillis"
    private const val KEY_SERVICE_DEADLINE = "serviceHardDeadlineMillis"
    private const val KEY_HOST_DEADLINE = "hostPhaseDeadlineMillis"

    fun limits(): JSONObject = JSONObject()
        .put(KEY_MAX_SOURCE_BYTES, VisualExecutionBudget.MAX_SOURCE_JS_BYTES)
        .put(KEY_MAX_STATE_BYTES, VisualExecutionBudget.MAX_STATE_JSON_BYTES)
        .put(KEY_MAX_TRANSACTION_BYTES, VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES)
        .put(KEY_MAX_HEAP_BYTES, VisualExecutionBudget.MAX_HEAP_BYTES)
        .put(KEY_MAX_STACK_BYTES, VisualExecutionBudget.MAX_STACK_BYTES)
        .put(KEY_COOPERATIVE_TIMEOUT, VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS)
        .put(KEY_SERVICE_DEADLINE, VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS)
        .put(KEY_HOST_DEADLINE, VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS)

    fun encodeRequest(request: VisualExecutionRequest): ByteArray {
        val program = JSONObject()
            .put("programId", request.programId)
            .put("sourceJs", request.sourceJs)
            .put("revision", request.sceneRevision)
            .put("stateJson", request.stateJson ?: JSONObject.NULL)
            .put(
                "viewport",
                JSONObject()
                    .put("widthDu", request.viewport.widthDu)
                    .put("heightDu", request.viewport.heightDu),
            )
            .put("timeMs", request.timeMs)
            .put("seed", request.seed)
            .put("theme", request.theme)
            .put("explicitUserRetry", request.explicitUserRetry)
        val root = JSONObject()
            .put(KEY_PROGRAM, program)
            .put(KEY_LIMITS, limits())
        return root.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * 解析并校验请求信封。所有失败都用有界异常表达：
     * - 超预算 → [VisualJsBudgetException]（映射 INPUT_OVER_BUDGET）；
     * - limits 不一致 → [VisualJsLimitsMismatchException]（映射 ENGINE_CLOSED）；
     * - 结构非法 → [VisualJsProtocolException]（映射 ENGINE_CLOSED）。
     */
    fun decodeRequest(bytes: ByteArray): VisualExecutionRequest {
        val root = try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (failure: JSONException) {
            throw VisualJsProtocolException("request envelope is not valid JSON")
        }
        checkLimits(root.optJSONObject(KEY_LIMITS))
        val program = root.optJSONObject(KEY_PROGRAM)
            ?: throw VisualJsProtocolException("request envelope is missing programJson")
        val sourceJs = program.optString("sourceJs", "")
        if (sourceJs.isEmpty()) {
            throw VisualJsProtocolException("programJson.sourceJs is missing")
        }
        val sourceBytes = figureUsageUtf8Bytes(sourceJs)
        if (sourceBytes > VisualExecutionBudget.MAX_SOURCE_JS_BYTES) {
            throw VisualJsBudgetException(
                "sourceJs is $sourceBytes bytes, over the " +
                    "${VisualExecutionBudget.MAX_SOURCE_JS_BYTES}-byte budget",
            )
        }
        val stateJson = if (program.isNull("stateJson")) {
            null
        } else {
            program.optString("stateJson", "").takeIf { it.isNotEmpty() }
        }
        if (stateJson != null) {
            val stateBytes = figureUsageUtf8Bytes(stateJson)
            if (stateBytes > VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
                throw VisualJsBudgetException(
                    "state is $stateBytes bytes, over the " +
                        "${VisualExecutionBudget.MAX_STATE_JSON_BYTES}-byte budget",
                )
            }
        }
        val viewport = program.optJSONObject("viewport")
            ?: throw VisualJsProtocolException("programJson.viewport is missing")
        return try {
            VisualExecutionRequest(
                programId = program.getString("programId"),
                sourceJs = sourceJs,
                sceneRevision = program.getInt("revision"),
                stateJson = stateJson,
                viewport = VisualViewport(
                    widthDu = viewport.getInt("widthDu"),
                    heightDu = viewport.getInt("heightDu"),
                ),
                timeMs = program.getLong("timeMs"),
                seed = program.getLong("seed"),
                theme = program.getString("theme"),
                explicitUserRetry = program.optBoolean("explicitUserRetry", false),
            )
        } catch (failure: JSONException) {
            throw VisualJsProtocolException(
                "programJson is missing a required field: ${failure.message}",
            )
        } catch (failure: IllegalArgumentException) {
            throw VisualJsProtocolException(
                "programJson violates the port contract: ${failure.message}",
            )
        }
    }

    fun encodeResult(result: VisualExecutionResult): ByteArray {
        val root = when (result) {
            is VisualExecutionResult.Rendered -> JSONObject()
                .put(KEY_SCENE, result.sceneJson)
                .put(KEY_SCENE_SCHEMA, result.sceneSchemaVersion)
                .put(KEY_CONTENT_IDS, JSONArray(result.contentIds.sorted()))

            is VisualExecutionResult.Rejected -> JSONObject()
                .put(KEY_REASON, result.reason.name)
                .put(KEY_DIAGNOSTICS, result.diagnostics)
        }
        return bounded(root)
    }

    fun encodeRejection(
        reason: VisualExecutionRejection,
        diagnostics: String,
    ): ByteArray = bounded(
        JSONObject()
            .put(KEY_REASON, reason.name)
            .put(KEY_DIAGNOSTICS, boundedDiagnostics(diagnostics)),
    )

    /**
     * 把服务回调翻译回端口结果。
     *
     * 传输层截断/空管道按**致命层**处理（HARD_DEADLINE_EXCEEDED）：那是进程在写结果时死掉的
     * 形状，不能当"程序输出不合法"回喂模型。完整但缺字段的信封是协议 bug，映射
     * INVALID_PROGRAM 并带诊断。
     */
    fun decodeResult(
        status: Int,
        payload: ByteArray?,
        diagnostics: String?,
    ): VisualExecutionResult = when (status) {
        VisualJsWire.RESULT_OK -> decodeRendered(payload)
        VisualJsWire.RESULT_REJECTED -> decodeRejection(payload, diagnostics)
        else -> VisualExecutionResult.Rejected(
            VisualExecutionRejection.ENGINE_CLOSED,
            boundedDiagnostics("unrecognized result status $status from visual_js"),
        )
    }

    private fun decodeRendered(payload: ByteArray?): VisualExecutionResult {
        if (payload == null || payload.isEmpty()) {
            return VisualExecutionResult.Rejected(
                VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                "visual_js result pipe ended without a payload (the worker likely died)",
            )
        }
        val root = try {
            JSONObject(String(payload, StandardCharsets.UTF_8))
        } catch (failure: JSONException) {
            return VisualExecutionResult.Rejected(
                VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                "visual_js result envelope is truncated or malformed " +
                    "(the worker likely died mid-write)",
            )
        }
        return try {
            val contentIds = mutableSetOf<String>()
            root.optJSONArray(KEY_CONTENT_IDS)?.let { array ->
                for (index in 0 until array.length()) {
                    contentIds.add(array.getString(index))
                }
            }
            VisualExecutionResult.Rendered(
                sceneJson = root.getString(KEY_SCENE),
                sceneSchemaVersion = root.getInt(KEY_SCENE_SCHEMA),
                contentIds = contentIds,
            )
        } catch (failure: JSONException) {
            VisualExecutionResult.Rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                boundedDiagnostics(
                    "visual_js returned a malformed rendered result: ${failure.message}",
                ),
            )
        } catch (failure: IllegalArgumentException) {
            VisualExecutionResult.Rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                boundedDiagnostics(
                    "visual_js returned an out-of-budget scene: ${failure.message}",
                ),
            )
        }
    }

    private fun decodeRejection(
        payload: ByteArray?,
        diagnostics: String?,
    ): VisualExecutionResult.Rejected {
        if (payload == null || payload.isEmpty()) {
            return VisualExecutionResult.Rejected(
                VisualExecutionRejection.ENGINE_CLOSED,
                boundedDiagnostics(
                    "visual_js rejected without a payload: ${diagnostics.orEmpty()}",
                ),
            )
        }
        val root = try {
            JSONObject(String(payload, StandardCharsets.UTF_8))
        } catch (failure: JSONException) {
            return VisualExecutionResult.Rejected(
                VisualExecutionRejection.ENGINE_CLOSED,
                "visual_js rejection envelope is malformed",
            )
        }
        val reasonText = root.optString(KEY_REASON, "")
        val detail = root.optString(KEY_DIAGNOSTICS, diagnostics.orEmpty())
        val mapped = VisualExecutionRejection.entries.firstOrNull { it.name == reasonText }
            ?: return VisualExecutionResult.Rejected(
                VisualExecutionRejection.INVALID_PROGRAM,
                boundedDiagnostics(
                    // 未映射的 wire reason：服务端当前不会发 UNKNOWN（T2 裁定不新增端口
                    // 枚举；不可归属失败是 INVALID_PROGRAM + [UNATTRIBUTED] 前缀）。这里
                    // 仍显式登记原因，不静默折成普通"程序坏"。
                    "visual_js rejected with unmapped reason '$reasonText': $detail",
                ),
            )
        return VisualExecutionResult.Rejected(mapped, boundedDiagnostics(detail))
    }

    private fun checkLimits(limits: JSONObject?) {
        if (limits == null) {
            throw VisualJsLimitsMismatchException("request envelope is missing limits")
        }
        expectLong(limits, KEY_MAX_SOURCE_BYTES, VisualExecutionBudget.MAX_SOURCE_JS_BYTES.toLong())
        expectLong(limits, KEY_MAX_STATE_BYTES, VisualExecutionBudget.MAX_STATE_JSON_BYTES.toLong())
        expectLong(
            limits,
            KEY_MAX_TRANSACTION_BYTES,
            VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES.toLong(),
        )
        expectLong(limits, KEY_MAX_HEAP_BYTES, VisualExecutionBudget.MAX_HEAP_BYTES)
        expectLong(limits, KEY_MAX_STACK_BYTES, VisualExecutionBudget.MAX_STACK_BYTES)
        expectLong(
            limits,
            KEY_COOPERATIVE_TIMEOUT,
            VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS,
        )
        expectLong(
            limits,
            KEY_SERVICE_DEADLINE,
            VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS,
        )
        expectLong(
            limits,
            KEY_HOST_DEADLINE,
            VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS,
        )
    }

    private fun expectLong(limits: JSONObject, key: String, expected: Long) {
        if (!limits.has(key) || limits.optLong(key) != expected) {
            throw VisualJsLimitsMismatchException(
                "limits.$key=${limits.opt(key)} but this build's budget is $expected; " +
                    "host and :visual_js must be built from the same budget table",
            )
        }
    }

    private fun bounded(root: JSONObject): ByteArray {
        val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
            throw VisualJsBudgetException(
                "result envelope is ${bytes.size} bytes, over the " +
                    "${VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES}-byte transaction budget",
            )
        }
        return bytes
    }

    fun boundedDiagnostics(message: String): String =
        message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
}

/**
 * 服务进程内的内容度量实现（批次 22 T1 保守分支）。
 *
 * **为什么不是假度量**：契约 §3.2 的 text/math 度量必须给出真实宽/高/baseline；在隔离进程
 * 里凭空返回常量会把错误排版伪装成"正确图"（契约 §4"超限不截断成正确图"）。T1 只落进程层
 * 骨架，不接 4E.2 的原生字形/公式 lowering：纯几何程序（prepare 返回空批次）可以跑通，
 * 任何内容度量请求 fail-closed 成有界拒绝。
 *
 * **它消灭的具体失败**：没有这个 seam 时，服务会用一个"看起来成功"的常量度量让文本场景
 * 带着错误宽度通过校验，错误在 4E.2 才暴露且无法归因。这里把"未接线"变成可观测的拒绝。
 */
internal object UnwiredVisualContentMeasurer : VisualContentMeasurer {
    override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
        if (batch.entries.isEmpty()) {
            return VisualContentMeasureResult(emptyList())
        }
        throw VisualMeasurementUnavailableException(
            "native text/math measurement is not wired into :visual_js yet (4E.2); " +
                "${batch.entries.size} content request(s) were rejected",
        )
    }
}

internal class VisualMeasurementUnavailableException(message: String) : Exception(message)
