package com.tingyun.smartmistakebook.core.data.visual

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection

/**
 * QuickJS 异常分类的纯函数层（批次 22 T2；批次 24 T2 增补证据校准状态，依据
 * `docs/research/2026-10-26-quickjs-oom-semantics.md` §6.1/§6.2 与
 * `docs/research/2026-10-26-quickjs-memory-accounting.md` §2–§4）。
 *
 * **它消灭的具体失败**：分类只认 `message.contains("out of memory")` 时，
 * (a) 堆耗尽到连 Error 对象都构造不出来时 quickjs-kt 只留下字符串 `"null"`
 * （[JS_NULL 链](https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/jni/mapping/js_value_to_jobject.c#L409-L411)），
 * 真实 OOM 被折成 `INVALID_PROGRAM`（KD-70 设备实测）；(b) 程序自己
 * `throw new RangeError('out of memory')` 反而被误报成 `MEMORY_LIMIT`。本层把分类变成
 * "类型 → 消息严格前缀 → 堆证据 → 不可归属"四级可单测的纯函数。
 *
 * 分类顺序（与实现严格一致）：
 * 1. **类型优先**：[QuickJsInterruptedException] 是 quickjs-kt 唯一可区分的失败类型，
 *    它只来自协作中断层 → [VisualJsFailureKind.COOPERATIVE_TIMEOUT]。必须先于任何消息匹配，
 *    否则消息里恰好含 `"out of memory"` 的中断会被误归堆上限。
 * 2. **消息严格前缀**：只认引擎生成的 `InternalError: out of memory`
 *    （bellard QuickJS `JS_ThrowOutOfMemory` → `JS_ThrowInternalError(ctx, "out of memory")`）。
 *    不认裸 `out of memory`、不认 `RangeError: out of memory`；`string too long` /
 *    `stack overflow` 明确不是堆上限（R3 §2.3/§5.4）。
 * 3. **消息丢失哨兵 + 堆证据**：`null` / `"null"` / `Invalid evaluation handle: -1` 只说明
 *    "错误对象丢失"，不能单独当 OOM（`throw null`、`unhandled Promise.reject(null)` 同形）。
 *    只有同时满足 `mallocSize >= 90% mallocLimit` 才归属 [VisualJsFailureKind.MEMORY_LIMIT]。
 *    **该比值是尚未校准的主证据**（R2 §2：未锚定的夹具在 OOM 后、读快照前会被 GC 回收，
 *    E-02 读到 177216 而非上限），因此这类归因带
 *    [VisualJsCalibration.PENDING_CALIBRATION]，诊断追加
 *    [PENDING_CALIBRATION_DIAGNOSTIC_MARKER]；阈值与判据本身不变。
 * 4. **兜底**：丢失哨兵但堆不接近上限 → [VisualJsFailureKind.UNATTRIBUTED]；其余 →
 *    [VisualJsFailureKind.INVALID_PROGRAM]。
 *
 * **对外枚举不变（批次 22 T2 裁定）**：[VisualExecutionRejection] 不新增 `UNKNOWN`。
 * 不可归属失败折成 `INVALID_PROGRAM`，但诊断以 [UNATTRIBUTED_DIAGNOSTIC_PREFIX] 开头，
 * 宿主/AIDL 可据此前缀区分"程序错"与"引擎失败原因不可归属"（R3 §6.6 的退化口径）。
 */
internal data class HeapSnapshot(
    /** QuickJS `JS_ComputeMemoryUsage` 的 `malloc_size`：memoryLimit 检查用的同一计数器。 */
    val mallocSize: Long,
    /** 当前 `malloc_limit`；`<= 0` 表示未设上限（不能据此归属 OOM）。 */
    val mallocLimit: Long,
) {
    /**
     * 是否接近堆上限。90% 的推导见 R3 §6.3：消息丢失通常意味着"余量小于一个 Error 对象"，
     * 真实丢失型 OOM 一般 ≥99.9% 满；90% 留出 `usable_size` 记账误差与 GC/形状分配抖动。
     * **不得为提高设备通过率而下调**（R3 §6.3）。
     */
    fun isNearLimit(): Boolean =
        mallocLimit > 0 && mallocSize * 100 >= mallocLimit * NEAR_LIMIT_PERCENT

    private companion object {
        const val NEAR_LIMIT_PERCENT: Long = 90
    }
}

/** 内部分类结果；[VisualJsFailure.reason] 是折回端口枚举的映射。 */
internal enum class VisualJsFailureKind {
    /** 协作中断（类型分支）。 */
    COOPERATIVE_TIMEOUT,

    /** 堆上限（消息严格前缀，或消息丢失 + 堆证据）。 */
    MEMORY_LIMIT,

    /** 沙箱违规（引用不存在的全局）。 */
    SANDBOX_VIOLATION,

    /** 消息丢失且没有堆证据：引擎失败但原因不可归属（R3 的 `UNKNOWN`）。 */
    UNATTRIBUTED,

    /** 已知程序错误。 */
    INVALID_PROGRAM,
}

/**
 * 归因证据的校准状态（批次 24 T2，R2 `docs/research/2026-10-26-quickjs-memory-accounting.md`）。
 *
 * **它消灭的具体失败**：把 `mallocSize / mallocLimit` 比值当作已校准的判决性证据。
 * E-02 显示同一个"消息丢失"形状在设备上只读到 1.06%（OOM 后、读快照前被 GC 回收到
 * 基线），若宿主/设备日志不区分"引擎原文直证"与"比值推断"，就会把一次未成立的
 * 归因当成 KD-70 已关闭，或反过来误以为要下调 90% 阈值。
 */
internal enum class VisualJsCalibration {
    /** 判据不依赖堆水位比值（引擎原文、程序错误、沙箱、超时、无堆证据）。 */
    NONE,

    /**
     * 判据以 `mallocSize >= 90% mallocLimit` 为主证据，且该比值**尚未在设备上校准成功**：
     * 唯一设备数据是 E-02 的 `mallocSize=177216`（1.06%），KD-70 仍 open。夹具把存活对象
     * 锚定到 GC 根后设备复跑并落盘，才可能把该分支升为已校准；在此之前不改变阈值与映射。
     */
    PENDING_CALIBRATION,
}

/** 比值证据"待校准"在诊断里的显式标记；设备日志与 KD 回链只认这个字面量。 */
internal const val PENDING_CALIBRATION_DIAGNOSTIC_MARKER: String = "PENDING_CALIBRATION"

/** 纯分类结果：内部 kind + 有界诊断 + 归因证据的校准状态。 */
internal data class VisualJsFailure(
    val kind: VisualJsFailureKind,
    val diagnostics: String,
    /**
     * [VisualJsCalibration.PENDING_CALIBRATION] 只在"比值主证据"的 [VisualJsFailureKind.MEMORY_LIMIT]
     * 上出现；直接由 `InternalError: out of memory` 支撑的归因是 [VisualJsCalibration.NONE]。
     */
    val calibration: VisualJsCalibration = VisualJsCalibration.NONE,
) {
    /**
     * 折回端口枚举。`UNATTRIBUTED` 折成 [VisualExecutionRejection.INVALID_PROGRAM]
     * （对外枚举本批不新增 `UNKNOWN`），诊断前缀负责区分；见文件 KDoc。
     */
    val reason: VisualExecutionRejection
        get() = when (kind) {
            VisualJsFailureKind.COOPERATIVE_TIMEOUT -> VisualExecutionRejection.PHASE_TIMEOUT
            VisualJsFailureKind.MEMORY_LIMIT -> VisualExecutionRejection.MEMORY_LIMIT
            VisualJsFailureKind.SANDBOX_VIOLATION -> VisualExecutionRejection.SANDBOX_VIOLATION
            VisualJsFailureKind.UNATTRIBUTED -> VisualExecutionRejection.INVALID_PROGRAM
            VisualJsFailureKind.INVALID_PROGRAM -> VisualExecutionRejection.INVALID_PROGRAM
        }
}

/** 不可归属失败的诊断前缀；跨进程映射与设备断言都只认这个常量值。 */
internal const val UNATTRIBUTED_DIAGNOSTIC_PREFIX: String = "[UNATTRIBUTED]"

/**
 * "消息丢失哨兵"判定（R3 §6.2/§6.3）。`null` 消息、Kotlin 对 JS `null` 的 `"null"` 合成，
 * 以及 JNI `store_evaluate_result` 静默失败后的 `Invalid evaluation handle: -1` 防御路径。
 * `Cannot format QuickJS exception message.` 暂不纳入（未观察到，列入 R3 §7 观察项）。
 */
internal fun isLostMessage(raw: String?): Boolean {
    val normalized = raw?.trim() ?: return true
    return normalized == "null" || normalized.startsWith("Invalid evaluation handle: -1")
}

/**
 * 分类一个非取消的 [QuickJsException]（[QuickJsInterruptedException] 也走这里，类型分支优先）。
 *
 * @param phaseName `prepare` / `render`，只进诊断，不参与判据。
 * @param heap 该阶段的堆快照；只在消息丢失哨兵路径上读取（见
 *   `QuickJsVisualExecutionEngine.runPhase`），其余为 null。
 * @param cooperativeTimeoutMillis 协作期限（诊断用；引擎可注入不同值）。
 */
internal fun classifyVisualJsFailure(
    phaseName: String,
    error: QuickJsException,
    heap: HeapSnapshot?,
    cooperativeTimeoutMillis: Long = VisualExecutionBudget.COOPERATIVE_PHASE_TIMEOUT_MILLIS,
): VisualJsFailure {
    val rawMessage = error.message
    val normalized = rawMessage?.trim().orEmpty()
    val lowered = normalized.lowercase()
    val lostMessage = isLostMessage(rawMessage)
    val kind = when {
        // 1) 类型优先：中断层不能被消息面重新归类（含伪造 OOM 文案的中断）。
        error is QuickJsInterruptedException -> VisualJsFailureKind.COOPERATIVE_TIMEOUT
        // 2) 只认引擎生成的 Error 名前缀；程序伪造的 RangeError/裸字符串不命中。
        lowered.startsWith("internalerror: out of memory") -> VisualJsFailureKind.MEMORY_LIMIT
        lowered.contains("not defined") -> VisualJsFailureKind.SANDBOX_VIOLATION
        // 3) 消息丢失 + 堆证据；两个条件必须同时成立（throw null 与真 OOM 的唯一分界）。
        lostMessage && heap?.isNearLimit() == true -> VisualJsFailureKind.MEMORY_LIMIT
        lostMessage -> VisualJsFailureKind.UNATTRIBUTED
        // 4) 已知程序错误（含 string too long / stack overflow / TypeError）。
        else -> VisualJsFailureKind.INVALID_PROGRAM
    }
    // 比值是主证据的分支（丢失哨兵 + 堆接近上限）在设备上尚未校准成功，显式标注；
    // 引擎原文直证的 OOM 不带标记。这不是放宽/收紧判据——kind/reason 均不变。
    val calibration = if (kind == VisualJsFailureKind.MEMORY_LIMIT && lostMessage) {
        VisualJsCalibration.PENDING_CALIBRATION
    } else {
        VisualJsCalibration.NONE
    }
    val diagnostics = when (kind) {
        VisualJsFailureKind.COOPERATIVE_TIMEOUT ->
            "phase $phaseName exceeded the cooperative timeout " +
                "($cooperativeTimeoutMillis ms); the cooperative layer is not a hard kill"

        VisualJsFailureKind.MEMORY_LIMIT ->
            if (calibration == VisualJsCalibration.PENDING_CALIBRATION) {
                "js heap limit (${VisualExecutionBudget.MAX_HEAP_BYTES} bytes) exceeded " +
                    "in phase $phaseName (attributed by memoryUsage: " +
                    "mallocSize=${heap?.mallocSize ?: -1}, " +
                    "mallocLimit=${heap?.mallocLimit ?: -1}; " +
                    "$PENDING_CALIBRATION_DIAGNOSTIC_MARKER)"
            } else {
                "js heap limit (${VisualExecutionBudget.MAX_HEAP_BYTES} bytes) exceeded " +
                    "in phase $phaseName: ${normalized.take(120)}"
            }

        VisualJsFailureKind.SANDBOX_VIOLATION ->
            "global is absent in phase $phaseName " +
                "(the sandbox has no IO/network/host objects): ${normalized.take(120)}"

        VisualJsFailureKind.UNATTRIBUTED ->
            "$UNATTRIBUTED_DIAGNOSTIC_PREFIX phase $phaseName failed with a message-less " +
                "engine error (raw=${rawMessage ?: "null"}, " +
                "mallocSize=${heap?.mallocSize ?: -1}, " +
                "mallocLimit=${heap?.mallocLimit ?: -1})"

        VisualJsFailureKind.INVALID_PROGRAM ->
            "program failed in phase $phaseName: ${normalized.take(160)}"
    }
    return VisualJsFailure(kind, diagnostics, calibration)
}
