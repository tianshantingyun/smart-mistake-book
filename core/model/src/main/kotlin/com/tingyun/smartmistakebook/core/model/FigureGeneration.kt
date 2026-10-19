package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 4E.1 的统一生图契约（权威正文见 `docs/current/figure-generation-prompt-policy.md` 与
 * `docs/current/visual-generation-contract.md`）。
 *
 * 三条硬边界（本文件只表达契约，不决定任何业务路由）：
 * - **purpose 与 method 正交**：用途描述"这张图是干什么的"，方法只有 JS / IMAGE_MODEL；
 *   入口不得按 purpose 建立 method 映射表（计划 §1.1）。
 * - **GENERATE 必须显式给 method**：新 policy 下缺 method 一律拒绝，不静默默认 IMAGE_MODEL；
 *   旧的无 method 载体只用于历史读取。
 * - **产物是类型化的**：像素资产带 SHA/类型/模型身份，JS 程序/快照是另一种 [FigureArtifact]，
 *   调用方不得再拿裸 `ByteArray` 充当"生图结果"。
 *
 * 本波次（4E.1 波次 1）只接通 IMAGE_MODEL；JS 的 program 载体在这里保留形状，
 * 但**不广告**、不执行（QuickJS 运行库过门后才由 WG-04/批次二接入）。
 */
object FigureGenerationPolicyVersion {
    /**
     * 与权威提示词文件同名的 policy 版本。所有入口（讲题工具、转写/净化、题图重绘）必须
     * 使用同一个值；proposal 的版本不一致即拒绝，避免"一套入口一套规则"。
     */
    const val VALUE: String = "figure-generation-policy-v1"
}

/** 生图方法（与用途正交）。 */
@Serializable
enum class FigureGenerationMethod {
    JS,
    IMAGE_MODEL,
}

/**
 * 生图用途。集合来自权威提示词（至少这五类）；用途只表达业务目标，不表达方法。
 */
@Serializable
enum class FigureGenerationPurpose {
    /** 题面转写（把原图转成结构化/可编辑题面）。 */
    SOURCE_TRANSCRIPTION,

    /** 题面重建（规范化重绘、去手写后的干净题面）。 */
    SOURCE_RECONSTRUCTION,

    /** 讲解图（过程图、概念图）。 */
    EXPLANATION,

    /** 插画（教学示意、自然形态）。 */
    ILLUSTRATION,

    /** 原像素清理（保留原像素的编辑/去污）。 */
    PIXEL_CLEANUP,
}

/** 统一决策（提示词文件 §2）。 */
@Serializable
enum class FigureGenerationDecision {
    GENERATE,
    NO_FIGURE,
    NEED_SOURCE,
    UNAVAILABLE,
}

/** IMAGE_MODEL 方法内部的图像操作（不是第三种方法；PREPARE_SOURCE 不在其中）。 */
@Serializable
enum class FigureImageOperation {
    EDIT,
    GENERATE,
}

/** 输出目标中的保真档（提示词里的 fidelity）。 */
@Serializable
enum class FigureFidelity {
    /** 忠实转写/重建：保留原题文字、标注、关系、单位，不补造。 */
    FAITHFUL,

    /** 语义重建：允许规范排版，但事实与关系必须等价。 */
    SEMANTIC,

    /** 教学示意：允许简化/辅助元素，须明确是示意。 */
    ILLUSTRATIVE,
}

/**
 * 宿主冻结的目标要求（不是模型可改写的字段）。
 *
 * 这些位用于 capability/目标合法性校验：例如必需参数交互时 IMAGE_MODEL 不能冒充完成，
 * 必需原像素保真时只能走支持的 EDIT 能力。
 */
data class FigureGenerationTarget(
    val fidelity: FigureFidelity = FigureFidelity.SEMANTIC,
    val requiresParametricInteraction: Boolean = false,
    val requiresPixelFidelity: Boolean = false,
    val requiresCompleteLabels: Boolean = true,
) {
    init {
        require(!requiresPixelFidelity || fidelity != FigureFidelity.ILLUSTRATIVE) {
            "A pixel-fidelity target cannot be illustrative"
        }
    }
}

/**
 * 本机真实可用的方法/操作。缺省能力为"不支持"（提示词 §4）。
 *
 * [availableMethods] **只能填真实已接通的方法**：未接 QuickJS 时不得包含 [FigureGenerationMethod.JS]，
 * 未验证图像接口时不得包含 IMAGE_MODEL。客户端只据此校验，不按科目/入口改选。
 */
data class FigureGenerationCapabilities(
    val availableMethods: Set<FigureGenerationMethod> = emptySet(),
    val supportsImageEditing: Boolean = false,
    val supportsImageGeneration: Boolean = false,
    val supportsParametricInteraction: Boolean = false,
    val supportsPixelEditing: Boolean = false,
    val supportsLayerComposition: Boolean = false,
) {
    init {
        require(
            FigureGenerationMethod.IMAGE_MODEL !in availableMethods ||
                supportsImageEditing ||
                supportsImageGeneration,
        ) {
            "IMAGE_MODEL cannot be advertised without an image editing or generation operation"
        }
        // JS 可以是静态后端（无交互）；参数交互由 target 与 `supportsParametricInteraction`
        // 在 policy 校验时逐次比对，不在能力声明层一刀切。
    }

    /** 波次 1 的生产形状：只有图像模型，编辑/文生图/原像素编辑可用，参数交互不可用。 */
    val imageModelOnly: Boolean
        get() = availableMethods == setOf(FigureGenerationMethod.IMAGE_MODEL)

    companion object {
        val IMAGE_MODEL_ONLY: FigureGenerationCapabilities = FigureGenerationCapabilities(
            availableMethods = setOf(FigureGenerationMethod.IMAGE_MODEL),
            supportsImageEditing = true,
            supportsImageGeneration = true,
            supportsPixelEditing = true,
            supportsParametricInteraction = false,
            supportsLayerComposition = false,
        )

        /** 什么都没有：所有生图入口都应如实回 UNAVAILABLE，不假成功。 */
        val NONE: FigureGenerationCapabilities = FigureGenerationCapabilities()
    }
}

/**
 * 宿主声明的生图预算（**声明面**；durable 权威在 core:domain 的 `RemoteModelDispatchLedger`）。
 *
 * KD-60：这里必须把**上限**与**当前余量**分开，不能再用常量 6 冒充“还剩 6 次”：
 * - [operationDispatchLimit] 是同一逻辑操作的远程派发**上限**（讲题工具轮与图像派发共享）；
 * - [remainingRemoteDispatches] 是宿主从持久账本读到的**真实余量**。本波次的生产入口
 *   不预读账本（读一次要额外的持久读口），因此为 null——由账本在预留时做真门，
 *   拒绝时回真实已派发次数；声明面只如实说“未预读”。
 *
 * [reserveChatFinalAnswer] = 讲题父操作必须给终答留 1 次派发；独立转写/净化作业终态本地保存，
 * 不硬留聊天位（提示词 §5）。
 */
data class FigureGenerationBudget(
    /** 同一逻辑操作的远程派发上限（含工具轮与图像派发），不是余量。 */
    val operationDispatchLimit: Int = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES,
    /** 真实剩余次数；null = 宿主未预读，交由持久账本在预留时判定（fail-closed 真门）。 */
    val remainingRemoteDispatches: Int? = null,
    val reserveChatFinalAnswer: Boolean = false,
    /** 图像生成/编辑与讲题工具轮共享同一份 durable 预算（当前架构；KD-60）。 */
    val sharesOperationBudgetWithToolRounds: Boolean = true,
) {
    init {
        require(operationDispatchLimit >= 0) {
            "The operation dispatch limit must not be negative"
        }
        require(remainingRemoteDispatches == null || remainingRemoteDispatches >= 0) {
            "Remaining remote dispatches must not be negative"
        }
        require(remainingRemoteDispatches == null || remainingRemoteDispatches <= operationDispatchLimit) {
            "Remaining remote dispatches must not exceed the operation dispatch limit"
        }
    }

    /**
     * 声明面的预算判断。未预读（null）**放行到账本门**——常量上限不得被当成余额；
     * 账本拒绝时不出网，并把真实已派发次数回给诊断。
     */
    fun allowsOneRemoteDispatch(): Boolean {
        val required = if (reserveChatFinalAnswer) 2 else 1
        val remaining = remainingRemoteDispatches ?: return true
        return remaining >= required
    }
}

/**
 * 宿主冻结的源证据（不是模型可填的图片/URL）。
 *
 * [imageBytes] 是经调用方核对后的具体字节；IMAGE_MODEL 的 EDIT 必须带它，
 * GENERATE 可以不带（无源文生图）。[contentSha256] 若给出必须与字节一致。
 */
class FigureSourceEvidence(
    val assetId: String? = null,
    val contentSha256: String? = null,
    val mimeType: String? = null,
    imageBytes: ByteArray? = null,
) {
    private val bytes: ByteArray? = imageBytes?.copyOf()

    init {
        require((bytes == null) == (mimeType == null)) {
            "Source image bytes and mime type must be provided together"
        }
        mimeType?.let {
            require(it == "image/jpeg" || it == "image/png") {
                "Only JPEG/PNG source images are supported"
            }
        }
        require(contentSha256 == null || contentSha256.length == SHA_256_HEX_CHARS) {
            "A source evidence sha must be SHA-256"
        }
        require(assetId == null || assetId.isNotBlank()) { "A source asset id must not be blank" }
    }

    /**
     * 只读视图（构造时已复制一次；调用方**不得修改**）。源图可达 20 MiB，policy/coordinator/
     * 通道都会读它——每次再复制一份会把同一张图在内存里放大数倍。
     */
    fun imageBytes(): ByteArray? = bytes

    val byteSize: Int
        get() = bytes?.size ?: 0

    override fun toString(): String =
        "FigureSourceEvidence(assetId=$assetId, sha=$contentSha256, mime=$mimeType, bytes=$byteSize)"
}

/**
 * 一次生图调用的稳定**逻辑操作身份**的宿主绑定。
 *
 * operationId 由宿主按 subject/basis/purpose/slot 派生、**不含 method**（提示词 §5）：
 * 同一操作换方法/重试不复制新身份，也不重置预算。
 *
 * 本波次复用两种既有持久派发记录作为预留来源：
 * - [ModelTask]：讲题/转写/净化分类所在的模型任务逻辑操作（`model_task_operation.dispatch_count`）；
 * - [CleanRedrawTask]：持久净化作业已被领取的那一次尝试（`clean_redraw_task.attempt_count`）。
 */
sealed interface FigureDispatchParent {
    data class ModelTask(
        val requestId: String,
        val reserveChatFinalAnswer: Boolean = true,
    ) : FigureDispatchParent {
        init {
            require(requestId.isNotBlank()) { "A parent model task request id must not be blank" }
        }
    }

    data class CleanRedrawTask(
        val taskId: String,
        val attemptCount: Int,
    ) : FigureDispatchParent {
        init {
            require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
            require(attemptCount >= 1) {
                "A clean redraw dispatch parent must reference a claimed attempt"
            }
        }
    }
}

/**
 * 宿主构造的生图请求（所有入口同一种）。
 *
 * 模型看不到本对象里的文件/URL/数据库对象；它只看到由 `FigurePromptAssembler`（core:data）
 * 注入的能力/预算/源访问摘要。本类型不是模型输出 schema 的一部分（ModelTaskRequest 仍为 17）。
 */
data class FigureGenerationRequest(
    /** 稳定逻辑操作 id，不含 method。 */
    val operationId: String,
    val purpose: FigureGenerationPurpose,
    val target: FigureGenerationTarget,
    val capabilities: FigureGenerationCapabilities,
    val budget: FigureGenerationBudget,
    /** 预留来源；无持久父操作时不得发起远程生图（fail-closed）。 */
    val dispatchParent: FigureDispatchParent? = null,
    val source: FigureSourceEvidence? = null,
    /** 宿主约束（不含模型可改写的内容；图像 API 只收具体 imagePrompt/源图/约束）。 */
    val constraints: List<String> = emptyList(),
    /** 当前题的可追溯上下文（标题等）；null = 没有。 */
    val problemContext: String? = null,
    /**
     * 由 `FigurePromptAssembler`（core:data）一次性组装好的判定上下文（能力/预算/用途注入）。
     * 它是宿主数据，不是模型可改写的指令；coordinator 校验其 policyVersion 后仅用于诊断。
     */
    val prompt: FigureGenerationPrompt? = null,
) {
    init {
        require(operationId.isNotBlank()) { "A figure operation id must not be blank" }
        require(constraints.size <= FigureGenerationPolicy.MAX_CONSTRAINTS) {
            "A figure request carries at most ${FigureGenerationPolicy.MAX_CONSTRAINTS} constraints"
        }
        constraints.forEach { constraint ->
            require(constraint.isNotBlank() && constraint.length <= FigureGenerationPolicy.MAX_CONSTRAINT_CHARS) {
                "A figure constraint must be non-blank and at most " +
                    "${FigureGenerationPolicy.MAX_CONSTRAINT_CHARS} chars"
            }
        }
        problemContext?.let {
            require(it.length <= MAX_PROBLEM_CONTEXT_CHARS) {
                "A figure problem context must be at most $MAX_PROBLEM_CONTEXT_CHARS chars"
            }
        }
        require(prompt == null || prompt.purpose == purpose) {
            "The assembled prompt must describe the request purpose"
        }
    }

    companion object {
        const val MAX_PROBLEM_CONTEXT_CHARS = 2_000
    }
}

/**
 * 统一 proposal（提示词文件 §2 的 JSON 形状）。
 *
 * 波次 1 由**入口适配层按真实可用方法构造**（method=IMAGE_MODEL，purpose 来自入口）；
 * 模型侧输出 proposal 属 WG-02/04，下一波接入。字段名保持与权威提示词一致，
 * 便于下一波直接解析同一形状。
 */
@Serializable
data class FigureGenerationProposal(
    val policyVersion: String = FigureGenerationPolicyVersion.VALUE,
    val decision: FigureGenerationDecision,
    val purpose: FigureGenerationPurpose? = null,
    val method: FigureGenerationMethod? = null,
    val reason: String = "",
    val fidelity: FigureFidelity? = null,
    val constraints: List<String> = emptyList(),
    /** JS 方法载荷；IMAGE_MODEL 方法不得携带。 */
    val program: String? = null,
    /** IMAGE_MODEL 方法载荷；JS 方法不得携带。 */
    @SerialName("imageOperation") val imageOperation: FigureImageOperation? = null,
    @SerialName("imagePrompt") val imagePrompt: String? = null,
) {
    init {
        require(reason.length <= FigureGenerationPolicy.MAX_REASON_CHARS) {
            "A figure proposal reason must be at most ${FigureGenerationPolicy.MAX_REASON_CHARS} chars"
        }
        require(reason.none { it.isISOControl() && it != '\n' }) {
            "A figure proposal reason must not carry control characters"
        }
        require(constraints.size <= FigureGenerationPolicy.MAX_CONSTRAINTS) {
            "A figure proposal carries at most ${FigureGenerationPolicy.MAX_CONSTRAINTS} constraints"
        }
    }
}

/**
 * 由 `FigurePromptAssembler`（core:data）组装的统一规则正文。
 *
 * [body] 是**唯一一份**共享规则正文（含注入后的 availableMethods/methodCapabilities/purpose/
 * budget），不是第二个决策器；入口只读不改。
 */
class FigureGenerationPrompt(
    val policyVersion: String,
    val body: String,
    val availableMethods: List<FigureGenerationMethod>,
    val purpose: FigureGenerationPurpose,
    val budget: FigureGenerationBudget,
) {
    init {
        require(policyVersion.isNotBlank()) { "A prompt policy version must not be blank" }
        require(body.isNotBlank() && body.length <= MAX_BODY_CHARS) {
            "A figure prompt body must be non-blank and bounded"
        }
        require(availableMethods.isNotEmpty()) { "A prompt must advertise at least one method" }
        require(availableMethods.distinct().size == availableMethods.size) {
            "A prompt must not advertise a method twice"
        }
    }

    override fun toString(): String =
        "FigureGenerationPrompt(version=$policyVersion, methods=$availableMethods, purpose=$purpose)"

    companion object {
        const val MAX_BODY_CHARS = 12_000
    }
}

/**
 * 类型化产物：像素资产或 JS 程序/快照。
 *
 * 本波次只产生 [ImageAsset]；[Program] 形状用于下一波，未接通前不得构造/广告。
 * 字节不参与 equals/hashCode（大对象），身份由 [ImageAsset.contentSha256] 表达。
 */
sealed interface FigureArtifact {
    val method: FigureGenerationMethod
    val purpose: FigureGenerationPurpose

    class ImageAsset(
        override val method: FigureGenerationMethod,
        override val purpose: FigureGenerationPurpose,
        val mimeType: String,
        val contentSha256: String,
        val modelId: String?,
        /** 已登记 canonical 资产时的 id；未登记（如净化链由仓库挂接）为 null。 */
        val assetId: String? = null,
        imageBytes: ByteArray,
    ) : FigureArtifact {
        private val bytes: ByteArray = imageBytes.copyOf()

        init {
            require(method == FigureGenerationMethod.IMAGE_MODEL) {
                "An image artifact can only be produced by IMAGE_MODEL"
            }
            require(mimeType.isNotBlank()) { "An image artifact needs a mime type" }
            require(contentSha256.length == SHA_256_HEX_CHARS) {
                "An image artifact sha must be SHA-256"
            }
            require(bytes.isNotEmpty()) { "An image artifact must carry bytes" }
        }

        /**
         * 只读视图（构造时已复制一次；调用方**不得修改**）：产物随后被落盘/挂接，
         * 每次读取再复制一份会把同一张图在内存里放大数倍。
         */
        fun imageBytes(): ByteArray = bytes

        val byteSize: Int
            get() = bytes.size

        override fun equals(other: Any?): Boolean =
            other is ImageAsset &&
                method == other.method &&
                purpose == other.purpose &&
                mimeType == other.mimeType &&
                contentSha256 == other.contentSha256 &&
                modelId == other.modelId &&
                assetId == other.assetId

        override fun hashCode(): Int {
            var result = method.hashCode()
            result = 31 * result + purpose.hashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + contentSha256.hashCode()
            result = 31 * result + (modelId?.hashCode() ?: 0)
            result = 31 * result + (assetId?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String =
            "ImageAsset(method=$method, purpose=$purpose, mime=$mimeType, sha=$contentSha256, " +
                "model=$modelId, assetId=$assetId, bytes=$byteSize)"
    }

    /** JS 程序/快照（下一波）。 */
    class Program(
        override val purpose: FigureGenerationPurpose,
        val programId: String,
        val sourceJs: String,
        val snapshotId: String? = null,
    ) : FigureArtifact {
        override val method: FigureGenerationMethod = FigureGenerationMethod.JS

        init {
            require(programId.isNotBlank()) { "A visual program id must not be blank" }
            require(sourceJs.isNotBlank()) { "A visual program needs source" }
            require(snapshotId == null || snapshotId.isNotBlank()) {
                "A visual snapshot id must be null or non-blank"
            }
        }
    }
}

/** coordinator 的统一状态。 */
enum class FigureGenerationStatus {
    /** 本次真实调用后端并产出新图。 */
    GENERATED,

    /** 命中入口层缓存（不重复付费；coordinator 本身不做缓存）。 */
    CACHED,

    /** 模型/入口判定确实不需要图。 */
    NO_FIGURE,

    /** 源证据不足，需先准备（未执行后端）。 */
    NEED_SOURCE,

    /** 没有可用方法/能力（未执行后端）。 */
    UNAVAILABLE,

    /** 请求/proposal 不合法（未预留、未执行后端）。 */
    REJECTED,

    /** 有合法预留但后端失败，或预留被拒。 */
    FAILED,
}

/**
 * coordinator 的统一返回：产物或**有界**诊断。
 *
 * [diagnostics] 只给宿主/工具结果用，≤ [MAX_DIAGNOSTICS_CHARS]，不得塞源图或密钥。
 */
data class FigureGenerationOutcome(
    val operationId: String,
    val purpose: FigureGenerationPurpose,
    val method: FigureGenerationMethod?,
    val status: FigureGenerationStatus,
    val artifact: FigureArtifact? = null,
    val diagnostics: String? = null,
) {
    init {
        require(operationId.isNotBlank()) { "A figure outcome needs an operation id" }
        require(
            (artifact == null) ||
                (status == FigureGenerationStatus.GENERATED || status == FigureGenerationStatus.CACHED),
        ) { "Only a generated/cached outcome carries an artifact" }
        require(
            (status != FigureGenerationStatus.GENERATED &&
                status != FigureGenerationStatus.CACHED) || artifact != null,
        ) { "A generated/cached outcome must carry its artifact" }
        require(diagnostics == null || diagnostics.length <= MAX_DIAGNOSTICS_CHARS) {
            "Figure diagnostics must be at most $MAX_DIAGNOSTICS_CHARS chars"
        }
    }

    companion object {
        const val MAX_DIAGNOSTICS_CHARS = 512
    }
}

/** UTF-8 字节数的唯一口径（proposal 的 payload 预算按字节，不按字符）。 */
fun figureUsageUtf8Bytes(value: String): Int = value.toByteArray(StandardCharsets.UTF_8).size

/**
 * 源图/产物的 SHA-256 单一实现（十六进制小写）。
 *
 * 生图链的预留绑定、幂等键与产物身份必须用同一个哈希口径；各入口各写一份
 * `MessageDigest` 是"同一张图两个 sha"漂移的来源。
 */
fun figureSha256Hex(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
