package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import kotlinx.coroutines.CancellationException

/**
 * Resolves a model-authored [AttachedImage] intent into a renderable local image
 * URI: it redraws/generates the figure, persists it as a canonical asset, and
 * maps each intent to its local `file://`/`content://` URI.
 *
 * Injectable seams keep this pure and testable without DNS/network:
 * - [generate] redraws/generates a figure from a [ImageGenerationRequest].
 * - [lookupExisting] / [isIntact] implement the A2 idempotency hit: a figure already
 *   generated for the same key is returned without a second paid generation.
 * - [persist] stores decoded bytes as a canonical asset (asset vault, with the
 *   AI-provenance metadata embedded) under the key-derived id.
 * - [resolveCurrentSheetBytes] supplies the current question's problem-sheet
 *   bytes for a REDRAW_PROBLEM (auto-resolved, never model-supplied).
 * - [uriFor] turns a persisted asset record into a renderable local URI.
 *
 * One failed figure (network/auth/decode) degrades to that image's absence — the
 * reply still shows its text; cancellation propagates so an app shutdown is not
 * swallowed.
 */
class AttachedImageGenerator(
    private val generate: suspend (ImageGenerationRequest) -> ImageRedrawResult,
    private val lookupExisting: suspend (figureId: String) -> CanonicalSourceAssetRecord?,
    private val isIntact: (CanonicalSourceAssetRecord) -> Boolean,
    private val persist: suspend (
        bytes: ByteArray,
        mimeType: String,
        sourceType: String,
        createdAtEpochMillis: Long,
        figureId: String,
        provenance: GeneratedFigureProvenance,
    ) -> CanonicalSourceAssetRecord,
    private val resolveCurrentSheetBytes: suspend () -> ByteArray?,
    private val uriFor: (CanonicalSourceAssetRecord) -> String,
) {
    /**
     * Resolves [images] into local URIs keyed by [AttachedImage.imageId]. Images
     * that cannot be produced (no source sheet, generation failure) are omitted.
     *
     * A2：每个意图先按幂等键查已生成资产——旋转/进程重建后重新解析同一张图时
     * **不再出网、不再付费**；命中但文件损坏时按"这张图不可用"处理（不重付重生成）。
     */
    suspend fun resolve(
        images: List<AttachedImage>,
        createdAtEpochMillis: Long,
    ): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (image in images) {
            resolveOne(image, createdAtEpochMillis)?.let { out[image.imageId] = it }
        }
        return out
    }

    private suspend fun resolveOne(
        image: AttachedImage,
        createdAtEpochMillis: Long,
    ): String? = try {
        val request = requestFor(image) ?: return null
        val figureId = attachedImageFingerprintId(image, request)
        val existing = lookupExisting(figureId)
        if (existing != null) {
            // 命中：文件完好就直接复用；损坏则如实缺席（不重付重生成）。
            if (isIntact(existing)) uriFor(existing) else null
        } else {
            val result = generate(request)
            persist(
                result.imageBytes,
                result.mimeType,
                StudyDbValue.SourceAssetType.GENERATED_FIGURE,
                createdAtEpochMillis,
                figureId,
                GeneratedFigureProvenance(
                    model = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                    figureId = figureId,
                    purpose = image.kind.name,
                ),
            ).let(uriFor)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** Builds the concrete generation request for an intent; null when the figure cannot be produced. */
    private suspend fun requestFor(image: AttachedImage): ImageGenerationRequest? = when (image.kind) {
        AttachedImageKind.GENERATE_PROCESS -> ImageGenerationRequest(
            prompt = processPrompt(image.description),
        )
        AttachedImageKind.REDRAW_PROBLEM -> {
            val source = resolveCurrentSheetBytes() ?: return null
            ImageGenerationRequest(
                prompt = redrawProblemInstruction(),
                sourceImageBytes = source,
                sourceImageMimeType = "image/png",
            )
        }
    }

    private fun processPrompt(description: String): String =
        attachedImageProcessPrompt(description)

    private fun redrawProblemInstruction(): String = attachedImageRedrawInstruction()
}

/**
 * 兼容配图链的幂等键：与工具链共用同一个 [tutorFigureFingerprintId]（源图 sha + prompt +
 * size + quality + format + kind/model）。
 *
 * 效力边界（如实说）：
 * - REDRAW_PROBLEM：两条入口的 prompt 与源图都同源（同一份重绘指令 + 当前题规范资产），
 *   同一次重绘在两条链上算出同一个键；
 * - GENERATE_PROCESS：**不保证**同键——工具链的 prompt 还带本地取的当前题上下文
 *   （`problemContext`），兼容链不带。同一轮同时产出 `attachedImages` 与 `GENERATE_FIGURE`
 *   的过程图会被视为两个不同请求、各生成一次（F4 已把工具链定为唯一主路径；这条双付窗口
 *   登记为残余边界，不在本批穿上下文）。
 */
internal fun attachedImageFingerprintId(
    image: AttachedImage,
    request: ImageGenerationRequest,
): String = tutorFigureFingerprintId(
    kind = when (image.kind) {
        AttachedImageKind.REDRAW_PROBLEM -> TutorFigureKind.REDRAW_PROBLEM
        AttachedImageKind.GENERATE_PROCESS -> TutorFigureKind.GENERATE_PROCESS
    },
    sourceImageSha256 = request.sourceImageBytes?.let(::sha256Hex),
    prompt = request.prompt,
    size = imageGenerationSizeFor(request.maxDimension),
    quality = IMAGE_GENERATION_QUALITY,
    format = request.outputFormat,
)

/**
 * 配图提示词的**单一来源**：`AttachedImageGenerator`（`attachedImages` 兼容字段链）与
 * `ConfiguredTutorFigureGenerator`（A1 的 `GENERATE_FIGURE` 工具链）是同一件事的两条入口，
 * 提示词各写一份就会漂开——一处改了"不添加题意之外的符号"，另一处不知道。
 *
 * [problemContext] 是本地从当前题草稿取的短上下文（标题）；null 时不渲染该句。
 */
internal fun attachedImageProcessPrompt(description: String, problemContext: String? = null): String =
    buildString {
        append("根据下面讲解生成题目解析过程图：").append(description).append('。')
        problemContext?.takeIf(String::isNotBlank)?.let { context ->
            append("当前题：").append(context).append('。')
        }
        append("只绘制与当前题目相关的图形、数轴、步骤标注，不添加题意之外的符号。")
    }

/** 题面去手写重绘的指令（两条配图链共用）。 */
internal fun attachedImageRedrawInstruction(): String =
    "这是一道被拍摄的题目。请保留印刷题面的所有文字与图形内容不变，仅去除手写笔迹、涂改、阴影与折痕，重绘成一张干净的题面图。"
