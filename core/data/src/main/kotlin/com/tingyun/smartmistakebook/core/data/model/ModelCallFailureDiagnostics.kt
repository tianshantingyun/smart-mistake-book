package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskKind

/**
 * 失败模型调用的**诊断缝**：默认关，只有 debug 源集里的代码会把它武装起来
 * （`app/src/debug/.../TestCaptureModelFailuresReceiver.kt`，adb 广播开/关）。
 *
 * **它消灭的失败**：失败现场只留得下一行 `class: message`（[logModelCallFailure]），而"这次为什么
 * 失败"的构成——上游拒收的报文、被截断的响应、形状/预算越界的那一版原文——全都在**响应原文**里。
 * 缺了它，"同一道题连续 4 次失败、第 5 次才成功"这类判断只能停在推测上：logcat 一轮转，就再没有
 * 任何一处留下可复核的报文（KD-38 的证据缺口正是这一类）。
 *
 * 三条边界都是结构性的，不靠约定：
 * 1. **默认关**：[sink] 为 null 时，失败路径上只多一次 null 检查，不产生任何 IO；
 * 2. **只随 debug 源集存在**：落盘、截断、条数上限都在 `app/src/debug` 的写入实现里，release /
 *    benchmark 变体里没有那个类，因此那边**不可能**被武装——不是"运行时开关兜底"；
 * 3. **原文含学生内容**：捕获记录只允许写进应用私有目录的本机调试文件，写入实现负责在条目里
 *    标注"含学生内容，仅本机调试"。
 *
 * 覆盖范围：网关**真的把请求发出去之后**失败的每一次调用——上游非 2xx、解析失败、截断、超时与
 * 连接层失败。本地前置条件失败（未配置 / 授权失效 / 载荷超预算 / 安全拒绝）不捕获：那些没有响应
 * 原文，失败构成已经在各自的用户文案里说清了。能力探测（`ModelCapabilityTester`）是另一个入口，
 * 不在本条通道内。
 */
object ModelCallFailureDiagnostics {
    /**
     * 捕获口；null（默认）= 关闭，写入实现只存在于 debug 源集。
     *
     * [reportModelCallFailure] 只在失败路径上读它，并吞掉写入实现抛出的任何异常：诊断是旁路，
     * 永远不改变产品结果（包括失败码与崩溃与否）。
     */
    @Volatile
    var sink: ((ModelCallFailureRecord) -> Unit)? = null
}

/**
 * 一次失败模型调用的构成。**可能含学生内容**（题面/图片里读出来的文字会出现在 [rawResponse]）：
 * 只允许落到本机调试文件，不进日志、不上报。
 *
 * 故意不是 `data class`：合成的 `toString()` 会把 [rawResponse] 整段带进任何一次日志或异常消息，
 * 而这正是不能进日志的东西。这里的 [toString] 只给元数据。
 */
class ModelCallFailureRecord(
    /** 失败发生的时刻（宿主机时钟，毫秒）。 */
    val occurredAtEpochMillis: Long,
    /** 这次调用在做的任务：讲题失败构成的第一层分类就是它（如 TUTOR_PLAN）。 */
    val taskKind: ModelTaskKind,
    val failureCode: ModelFailureCode,
    val retryable: Boolean,
    /** 上游 HTTP 状态；连接层失败（压根没拿到响应）时为 null。 */
    val httpStatus: Int?,
    val exceptionClass: String?,
    val exceptionMessage: String?,
    /**
     * 失败那一次的**响应原文**：上游错误报文，或（解析失败时）解析器读到的那一版响应体。
     * 流式调用给的是逐帧重建出来的响应体。null = 连接层失败，没有响应体可留。
     */
    val rawResponse: String?,
) {
    override fun toString(): String {
        val body = rawResponse?.let { "${it.length} chars（内容不打印）" } ?: "null"
        return "ModelCallFailureRecord(kind=$taskKind, code=$failureCode, retryable=$retryable, " +
            "httpStatus=$httpStatus, exception=$exceptionClass, rawResponse=$body)"
    }
}

/**
 * 失败路径的单一上报口：先记既有那一行日志（只 class + message，[error] 为 null 时不记），再把
 * 构成交给 [ModelCallFailureDiagnostics.sink]（默认关时什么都不发生）。
 *
 * 捕获实现抛异常时**吞掉**：一次失败不能被诊断变成崩溃，也不能因此换一个失败码。
 */
internal fun reportModelCallFailure(
    execution: ModelGatewayExecution,
    failure: ModelTaskFailure,
    error: Throwable? = null,
    httpStatus: Int? = null,
    rawResponse: String? = null,
) {
    error?.let(::logModelCallFailure)
    val sink = ModelCallFailureDiagnostics.sink ?: return
    val record = ModelCallFailureRecord(
        occurredAtEpochMillis = System.currentTimeMillis(),
        taskKind = execution.request.input.kind,
        failureCode = failure.code,
        retryable = failure.retryable,
        httpStatus = httpStatus,
        exceptionClass = error?.javaClass?.name,
        exceptionMessage = error?.message,
        rawResponse = rawResponse,
    )
    runCatching { sink(record) }
}

/**
 * Diagnostics for a failed model call: the exception class and message only. Request and
 * response bodies are never logged — they carry student photos and text — and the credential
 * never reaches the log either. （响应原文只有 [ModelCallFailureDiagnostics] 那条默认关的
 * 调试通道会带走，且只落应用私有目录。）
 */
internal fun logModelCallFailure(error: Throwable) {
    android.util.Log.e(
        MODEL_CALL_LOG_TAG,
        "Model call failed: ${error.javaClass.name}: ${error.message}",
    )
}

internal const val MODEL_CALL_LOG_TAG = "SmartMistakeBook"
