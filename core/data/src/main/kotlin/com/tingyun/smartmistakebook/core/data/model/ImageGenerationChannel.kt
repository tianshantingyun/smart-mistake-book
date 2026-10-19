package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 图像生成/编辑通道（4E.1 波次 1）。
 *
 * **出网强制门**：实际发送的入口只有带 [FigureImageDispatchBinding] 的两个方法
 * （[redrawClean] / [generate] 的 `dispatch` 重载）。绑定只能由
 * `DefaultFigureGenerationCoordinator` 在 `RemoteModelDispatchLedger` 授予预留之后构造；
 * 通道在组请求/发 POST **之前**复核 operation/method/源图 sha/配置指纹，任何一项不符即抛
 * [ImageDispatchNotReservedException]，一个字节都不发。
 *
 * 不带 dispatch 的旧重载保留为**fail-closed 迁移面**（默认直接抛，不发送）：
 * - `AttachedImageGeneratorFactory` 的兼容 `attachedImages` 链仍调用它——按 WG-05 的
 *   "历史 resolver cache-only"方向，它不再能生成新图（显式重生成需走 coordinator）；
 * - 旧测试替身可以只实现旧重载：带 dispatch 的默认实现先做绑定校验、再委托旧重载，
 *   因此生产通道与测试替身共享同一"先校验、后执行"的顺序。
 */
interface ImageGenerationChannel {
    /**
     * 图像编辑（有源图）。生产实现必须在任何网络 IO 前复核 [dispatch] 与真实源图字节。
     */
    suspend fun redrawClean(
        request: ImageRedrawRequest,
        dispatch: FigureImageDispatchBinding,
    ): ImageRedrawResult {
        requireImageDispatchBinding(
            dispatch = dispatch,
            sourceImageSha256 = figureSha256Hex(request.photoBytes),
            configurationFingerprint = null,
        )
        return redrawClean(request)
    }

    /**
     * 图像生成（可选源图种子）。生产实现必须在任何网络 IO 前复核 [dispatch]。
     */
    suspend fun generate(
        request: ImageGenerationRequest,
        dispatch: FigureImageDispatchBinding,
    ): ImageRedrawResult {
        requireImageDispatchBinding(
            dispatch = dispatch,
            sourceImageSha256 = request.sourceImageBytes?.let(::figureSha256Hex),
            configurationFingerprint = null,
        )
        return generate(request)
    }

    /**
     * 旧的无预留入口：默认 fail-closed。
     *
     * 生产 `OpenAiImageGenerationChannel` 不覆盖它（因此任何绕过 coordinator 的调用都在
     * 内存里立刻失败）；只实现旧入口的测试替身会被带 dispatch 的默认实现委托到。
     */
    suspend fun redrawClean(request: ImageRedrawRequest): ImageRedrawResult =
        throw ImageDispatchNotReservedException(
            "image redraw requires a granted dispatch reservation; " +
                "route it through FigureGenerationCoordinator",
        )

    /** 旧的无预留入口：默认 fail-closed（见 [redrawClean] 的说明）。 */
    suspend fun generate(request: ImageGenerationRequest): ImageRedrawResult =
        throw ImageDispatchNotReservedException(
            "image generation requires a granted dispatch reservation; " +
                "route it through FigureGenerationCoordinator",
        )
}

/**
 * 绑定复核：**POST 前**的强制门。
 *
 * [configurationFingerprint] 为 null 时只校验与请求无关的字段（测试替身没有自己的端点配置）；
 * 生产通道传入自己的配置指纹，把预留绑定到实际出网目标。
 */
internal fun requireImageDispatchBinding(
    dispatch: FigureImageDispatchBinding,
    sourceImageSha256: String?,
    configurationFingerprint: String?,
) {
    if (dispatch.method != FigureGenerationMethod.IMAGE_MODEL) {
        throw ImageDispatchNotReservedException("a reserved image dispatch must use IMAGE_MODEL")
    }
    if (dispatch.reservationId.isBlank() || dispatch.operationId.isBlank()) {
        throw ImageDispatchNotReservedException("a reserved image dispatch needs its operation identity")
    }
    if (dispatch.sourceImageSha256 != sourceImageSha256) {
        throw ImageDispatchNotReservedException(
            "the reserved source evidence does not match the bytes about to be sent",
        )
    }
    if (
        configurationFingerprint != null &&
        dispatch.configurationFingerprint != configurationFingerprint
    ) {
        throw ImageDispatchNotReservedException(
            "the reservation was made for a different image configuration",
        )
    }
}

/**
 * 图像端点/模型的非秘密指纹（预留绑定 + 通道复核共用同一份口径）。
 *
 * 只含 base URL 与模型 id；**不含 API key**。base URL 去尾斜杠后参与哈希，避免
 * 配置层与 SSRF 校验层对同一端点的规范化差异造成假不一致。
 */
fun imageGenerationConfigurationFingerprint(baseUrl: String, modelId: String): String {
    val canonical = "image-generation-config-v1|${baseUrl.trimEnd('/')}|$modelId"
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/** 通道/编排层对"没有合法预留就想出网"的统一失败类型。 */
class ImageDispatchNotReservedException(message: String) : IllegalStateException(message)

/** A request to generate a figure from prose, optionally seeded by a source image. */
@Serializable
data class ImageGenerationRequest(
    val prompt: String,
    val sourceImageBytes: ByteArray? = null,
    val sourceImageMimeType: String? = null,
    val outputFormat: String = "png",
    val maxDimension: Int = 1536,
) {
    init {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARS) {
            "Image generation prompt must be non-blank and bounded"
        }
        require(
            (sourceImageBytes == null) == (sourceImageMimeType == null),
        ) { "Source image bytes and mime type must be provided together" }
        sourceImageMimeType?.let {
            require(it == "image/jpeg" || it == "image/png") {
                "Only JPEG/PNG source images are supported"
            }
        }
        require(outputFormat == "png" || outputFormat == "jpeg" || outputFormat == "webp") {
            "Unsupported output format: $outputFormat"
        }
    }

    companion object {
        const val MAX_PROMPT_CHARS = 32_000
    }
}

/**
 * 生成质量档的**单一出处**：通道把它发给 provider，A2 的幂等指纹把同一个值算进键——
 * 两处各写一份的话，"quality 变了但指纹没变"会让新参数命中旧图（或反过来重复付费）。
 */
internal const val IMAGE_GENERATION_QUALITY = "high"

/**
 * maxDimension → provider 的 size 值的**单一出处**（通道与幂等指纹共用）。
 * 指纹算的是这个实际发出去的 size 串，不是 maxDimension 本身——只有 size 真的变了键才变。
 */
internal fun imageGenerationSizeFor(maxDimension: Int): String = when {
    maxDimension <= 1024 -> "1024x1024"
    maxDimension <= 1536 -> "1536x1024"
    else -> "1024x1536"
}

/** A request to redraw a photographed problem into a clean figure. */
@Serializable
data class ImageRedrawRequest(
    val photoBytes: ByteArray,
    val photoMimeType: String,
    val instruction: String,
    val outputFormat: String = "png",
    val maxDimension: Int = 1536,
) {
    init {
        require(photoMimeType == "image/jpeg" || photoMimeType == "image/png") {
            "Only JPEG/PNG photographs are supported"
        }
        require(instruction.isNotBlank() && instruction.length <= MAX_INSTRUCTION_CHARS) {
            "Image redraw instruction must be non-blank and bounded"
        }
        require(outputFormat == "png" || outputFormat == "jpeg" || outputFormat == "webp") {
            "Unsupported output format: $outputFormat"
        }
    }

    companion object {
        const val MAX_INSTRUCTION_CHARS = 32_000
    }
}

/** The clean redrawn figure produced by the channel. */
@Serializable
data class ImageRedrawResult(
    val imageBytes: ByteArray,
    val mimeType: String,
    val modelVersion: String = "",
) {
    companion object {
        const val MAX_OUTPUT_BYTES = 24L * 1_024L * 1_024L
    }
}
