package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes

/**
 * JS 视觉程序执行端口（4E 契约 [visual-generation-contract.md] §3.2/§4/§5；批次 20 T4）。
 *
 * **本端口消灭的具体失败**：没有这个抽象时，4E 批次二的 `AndroidVisualExecutionEngine`
 * 只能把 QuickJS/AIDL/原生排版直接写进 core:data 的业务编排——运行库换版或整轨摘除
 * （计划 §7 风险：T4 独立成块）就要连片改业务代码。端口先冻结边界语义，实现后落。
 *
 * **批次 20 落地形态（保守分支，2026-10-25）**：研究复核发现 R1（js-engine-selection §2.1）
 * 与 R4（16kb-arm64-api23-build §2.4）对 quickjs-kt-android 1.0.15 发布 AAR 的 16 KB/RELRO
 * 判定相反且 R1 的阻断理由（"GNU_RELRO 结束未对齐→必须源码重建"）被官方豁免判据证伪——
 * 选型的**消费形态**（发布 AAR 直接消费 vs 钉源码 NDK 自建）悬置待两线收口。按任务书第 5 条，
 * 本批只落端口接口 + fake 实现 + 全部用例，**不引入任何依赖**；无生产消费方（JS 方法保持
 * UNAVAILABLE，能力快照/工具面/提示词零改动）。真实实现落地时实现本端口并复用同一组契约用例。
 *
 * 端口语义（均出自契约原文，实现不得弱化）：
 * - 每次执行 = 一个有界事务：新上下文执行源码 → `prepare(input)` → 原生度量（经
 *   [VisualContentMeasurer]，一次批量）→ 新上下文重执行 + `render(input, metrics)` →
 *   输出校验。程序不能依赖跨调用全局变量（契约 §3.2）。
 * - 沙箱：无 IO、无网络、无主机对象、无模块加载器；全局只允许 SDK 白名单（契约 §3.2
 *   "模块加载器、主机对象和 import 均不开启"）。
 * - 硬终止：引擎 timeout 是**协作中断**，不是硬终止；硬终止由实现侧的独立 watchdog 承担
 *   （契约 §4）。端口结果必须区分两层（[VisualExecutionRejection.PHASE_TIMEOUT] vs
 *   [VisualExecutionRejection.HARD_DEADLINE_EXCEEDED]），宿主台账才能记录哪层生效。
 * - 内存上限：独立于宿主 JVM 堆的 JS 堆/栈上限（契约 §4 预算表）。
 * - 超限不截断成"正确图"：一切拒绝返回**有界**诊断，不抛库异常穿透边界（契约 §4
 *   "所有错误经 AIDL 有界诊断回传"）。
 * - 单在飞：全局一个在飞绘制，忙时回 [VisualExecutionRejection.BUSY]（契约 §4）。
 * - 熔断：硬终止后该 programHash 熔断，至用户明确本地重试或源码改变；重建只恢复环境，
 *   不自动重跑坏源码（契约 §4）。[VisualExecutionRequest.explicitUserRetry] 即"用户明确
 *   本地重试"的端口表达。
 *
 * `close()` 幂等；关闭后 `execute()` 立即回 [VisualExecutionRejection.ENGINE_CLOSED]，
 * 不得再启动任何事务。本端口不依赖 Android 类型（core:domain 是纯 JVM 模块）。
 */
interface VisualExecutionEngine {
    /** 执行一次渲染事务。不抛库异常：失败/超限/终止一律以 [VisualExecutionResult.Rejected] 返回。 */
    suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult

    /** 幂等关闭。真实实现的 close 可能等待引擎锁（契约 §4），不得在 Binder/主线程调用。 */
    fun close()
}

/**
 * 一次渲染事务的输入（契约 §3.2 `FrameInput`：`{programId, revision, state, viewport,
 * timeMs, seed, theme}`）。只含本图数据：无密钥、无凭据、无文件路径、无全库对象。
 *
 * 字节预算（源码 64 KiB / state 16 KiB，按 UTF-8 字节）由**实现**在边界强制并回
 * [VisualExecutionRejection.INPUT_OVER_BUDGET]——构造器只做结构校验，拒绝走结果不走异常，
 * 与契约 §4"返回有界诊断"一致。
 */
data class VisualExecutionRequest(
    /** 宿主分配的不可变程序身份（契约 §8：programId 与对外 visualId 同一）。 */
    val programId: String,
    /** 原始 UTF-8 源码；身份与熔断指纹按其字节 SHA-256 计算。 */
    val sourceJs: String,
    /** 宿主分配的场景修订号；JS 输出只可回显，不能重置（契约 §8）。 */
    val sceneRevision: Int,
    /** 本帧状态（StateV1 JSON）；null = 使用程序 initialState。 */
    val stateJson: String? = null,
    val viewport: VisualViewport,
    /** 宿主 native clock 提供的时间；引擎不自带时钟（契约 §3.2 bootstrap 封闭 Date）。 */
    val timeMs: Long,
    /** SDK PRNG 种子；引擎封闭 Math.random（契约 §3.2）。 */
    val seed: Long,
    val theme: String,
    /** 用户明确本地重试（解熔断）；默认 false。 */
    val explicitUserRetry: Boolean = false,
) {
    init {
        require(programId.isNotBlank()) { "A visual execution request needs a program id" }
        require(sourceJs.isNotEmpty()) { "A visual execution request needs source" }
        require(sceneRevision >= 1) { "A visual scene revision starts at 1" }
        require(stateJson == null || stateJson.isNotEmpty()) {
            "A visual state must be null or non-empty"
        }
        require(timeMs >= 0L) { "A visual frame time must not be negative" }
        require(theme.isNotBlank()) { "A visual frame needs a theme" }
    }
}

/** 画布视口（du；契约 §5：`1 du=1 dp`；契约 §4：画布边长 ≤4096 du）。 */
data class VisualViewport(
    val widthDu: Int,
    val heightDu: Int,
) {
    init {
        require(widthDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A visual viewport width must be within the canvas budget"
        }
        require(heightDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A visual viewport height must be within the canvas budget"
        }
    }
}

/**
 * 契约 §4 的 V1 初始上限单源。**它消灭的失败**：每个消费方（本批 fake、批次二真实引擎、
 * core:data 台账）各从契约散文里抄一份数字，改预算时漂移出多套口径。真实值仍是
 * "待设备性能验收的产品预算"（契约 §4 原文），改这里必须回写契约。
 */
object VisualExecutionBudget {
    /** 单程序源码（UTF-8 字节）。 */
    const val MAX_SOURCE_JS_BYTES: Int = 64 * 1024

    /** 单帧 state（UTF-8 字节）。 */
    const val MAX_STATE_JSON_BYTES: Int = 16 * 1024

    /** 单向事务 JSON（帧输入与场景输出各按此口径，UTF-8 字节）。 */
    const val MAX_TRANSACTION_JSON_BYTES: Int = 256 * 1024

    /** JS 堆上限（独立于宿主内存）。 */
    const val MAX_HEAP_BYTES: Long = 16L * 1024 * 1024

    /** JS 栈上限。 */
    const val MAX_STACK_BYTES: Long = 256L * 1024

    /** 单 JS 阶段协作期限（引擎 interrupt；不是硬终止）。 */
    const val COOPERATIVE_PHASE_TIMEOUT_MILLIS: Long = 100L

    /** 服务硬期限（独立 watchdog 自杀）。 */
    const val SERVICE_HARD_DEADLINE_MILLIS: Long = 1500L

    /** 宿主阶段期限（软期限，结果作废与背压）。 */
    const val HOST_PHASE_DEADLINE_MILLIS: Long = 2500L

    /** 单事务内容度量请求条数（契约 §4：内容请求 128）。 */
    const val MAX_CONTENT_REQUESTS: Int = 128

    /** 单事务内容请求总字符（契约 §4：总字符 6000）。 */
    const val MAX_CONTENT_TOTAL_CHARS: Int = 6000

    /** 画布边长（du）。 */
    const val MAX_CANVAS_EDGE_DU: Int = 4096
}

/**
 * 原生内容度量 seam（契约 §3.2 事务第 2 步："原生验证并度量 text/math，保留不可变布局
 * artifact"）。引擎在每个事务里恰好调用一次、且仅一次（"一次 measure batch"）。
 *
 * **它消灭的失败**：没有这个 seam，"prepare 一次/度量一次/render 只引用本事务 contentId"
 * 的纪律就只存在于实现内部，端口用例无法观测；实现也可能无限追加测量（契约 §4 明令拒绝）。
 * 真实度量（真实字形/公式 lowering）是批次二 core:visual 的工作；预算在类型上就地强制。
 */
interface VisualContentMeasurer {
    suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult
}

/** 一批内容度量请求（契约 §3.2：prepare 返回"批量内容度量请求"）。 */
data class VisualContentMeasureBatch(
    val programId: String,
    val sceneRevision: Int,
    val entries: List<VisualContentMeasureEntry>,
) {
    init {
        require(programId.isNotBlank()) { "A content measure batch needs a program id" }
        require(sceneRevision >= 1) { "A content measure batch revision starts at 1" }
        require(entries.size <= VisualExecutionBudget.MAX_CONTENT_REQUESTS) {
            "A content measure batch exceeds ${VisualExecutionBudget.MAX_CONTENT_REQUESTS} requests"
        }
        require(entries.sumOf { it.payload.length } <= VisualExecutionBudget.MAX_CONTENT_TOTAL_CHARS) {
            "A content measure batch exceeds ${VisualExecutionBudget.MAX_CONTENT_TOTAL_CHARS} total chars"
        }
    }
}

/** 单条内容请求；kind 只区分契约 §3.2 的 text/math 两类。 */
data class VisualContentMeasureEntry(
    val kind: VisualContentKind,
    val payload: String,
) {
    init {
        require(payload.isNotEmpty()) { "A content measure entry needs a payload" }
    }
}

enum class VisualContentKind {
    TEXT,
    MATH,
}

/** 度量结果：不可变布局 artifact 的句柄（contentId 由原生侧分配，程序只可回显引用）。
 *
 * 允许为空：纯几何场景（无 text/math 内容）的 prepare 返回空批次是合法事务，
 * 空结果不构成失败。
 */
data class VisualContentMeasureResult(
    val entries: List<VisualMeasuredContent>,
)

/**
 * 单条已度量内容（契约 §3.2：返回"宽、高、baseline、实际字号与内容摘要"）。
 * inkBounds 的完整矩形随批次二类型化布局落地，本端口先携带标量度量。
 */
data class VisualMeasuredContent(
    val contentId: String,
    val widthDu: Float,
    val heightDu: Float,
    val baselineDu: Float,
    val fontSizeSp: Float,
    val digest: String,
) {
    init {
        require(contentId.isNotBlank()) { "A measured content id must not be blank" }
        require(widthDu.isFinite() && widthDu >= 0f) { "A measured width must be finite and non-negative" }
        require(heightDu.isFinite() && heightDu >= 0f) { "A measured height must be finite and non-negative" }
        require(baselineDu.isFinite() && baselineDu >= 0f) { "A measured baseline must be finite and non-negative" }
        require(fontSizeSp.isFinite() && fontSizeSp >= 0f) { "A measured font size must be finite and non-negative" }
        require(digest.isNotBlank()) { "A measured content needs a digest" }
    }
}

/** 执行结果：成功渲染，或有界拒绝。不设"部分成功/截断"形态（契约 §4：超限不截断成"正确图"）。 */
sealed interface VisualExecutionResult {
    /**
     * 渲染成功：经实现校验的场景 JSON（批次二类型化 SceneV1/RenderScene DTO 与 compiler
     * 落地前，payload 以有界字符串过端口）+ 本事务冻结的 contentId 集合。
     */
    data class Rendered(
        val sceneJson: String,
        val sceneSchemaVersion: Int,
        val contentIds: Set<String>,
    ) : VisualExecutionResult {
        init {
            require(sceneJson.isNotEmpty()) { "A rendered scene needs its payload" }
            require(sceneSchemaVersion >= 1) { "A scene schema version starts at 1" }
            require(figureUsageUtf8Bytes(sceneJson) <= VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
                "A rendered scene exceeds the transaction JSON budget"
            }
        }
    }

    /** 有界拒绝：诊断 ≤ [FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS]，不塞源码/密钥/源图。 */
    data class Rejected(
        val reason: VisualExecutionRejection,
        val diagnostics: String,
    ) : VisualExecutionResult {
        init {
            require(diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS) {
                "Visual execution diagnostics must be bounded"
            }
        }
    }
}

/**
 * 拒绝原因分类。每项对应一类**宿主要区别处理**的失败（合并它们会把"该改输入"与"该熔断"
 * 与"该稍后重排"混为一谈）：
 * - 输入侧（调用方修输入）：[INPUT_OVER_BUDGET]；
 * - 程序侧（回喂模型诊断）：[INVALID_PROGRAM]、[SANDBOX_VIOLATION]、[MEMORY_LIMIT]、
 *   [PHASE_TIMEOUT]、[INVALID_SCENE]；
 * - 编排侧（宿主改调度）：[HARD_DEADLINE_EXCEEDED]、[PROGRAM_FUSED]、[BUSY]、[ENGINE_CLOSED]。
 */
enum class VisualExecutionRejection {
    /** 帧输入超字节预算（源码/state/事务 JSON）——契约 §4：不截断，直接拒。 */
    INPUT_OVER_BUDGET,

    /** 源码不可求值（未注册/语法/运行时错误）。 */
    INVALID_PROGRAM,

    /** 沙箱违规：访问不存在的全局（IO/网络/主机对象/DOM 等）。 */
    SANDBOX_VIOLATION,

    /** JS 堆/栈超限（引擎 OOM 路径）。 */
    MEMORY_LIMIT,

    /** 单阶段协作期限到（引擎 interrupt 层；非硬终止）。 */
    PHASE_TIMEOUT,

    /** 服务硬期限到（watchdog/进程终止层）。 */
    HARD_DEADLINE_EXCEEDED,

    /** 该程序已熔断（硬终止后），需用户明确本地重试或源码改变。 */
    PROGRAM_FUSED,

    /** 已有一个在飞事务（契约 §4：全局一个在飞绘制）。 */
    BUSY,

    /** 引擎已关闭。 */
    ENGINE_CLOSED,

    /** 渲染输出不合规（如引用了非本事务冻结的 contentId）。 */
    INVALID_SCENE,
}
