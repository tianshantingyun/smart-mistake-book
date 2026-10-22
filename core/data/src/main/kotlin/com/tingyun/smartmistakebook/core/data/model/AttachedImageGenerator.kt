package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.data.visual.recordFigureOutputAsset
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageUnavailableReason
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.figureBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.CancellationException

/**
 * 历史 `attachedImages` 兼容意图的解析器（WG-05 / KD-58）。
 *
 * 两条**互不混用**的路径：
 * - [resolveOne]（打开消息/展开/旋转/进程恢复）：**cache-only**。只按幂等键读已登记的
 *   规范资产；命中且文件核对通过才回 [AttachedImageResolution.Ready]。缺失/损坏/缺源
 *   各回一个可读原因，**绝不**在这里发起生图请求。
 * - [regenerate]（用户显式点“重新生成”）：把同一意图适配成统一
 *   [FigureGenerationRequest] + `IMAGE_MODEL` proposal，转调共享
 *   [FigureGenerationCoordinator]（预留 → 执行 → 记账）。父操作是**原消息**的模型任务
 *   requestId，失败重试沿用原预算；本波次只接通 IMAGE_MODEL（不在这里按 kind 写死方法表）。
 *
 * 可注入缝保持纯 JVM 可测：
 * - [lookupExisting] / [isIntact]：幂等命中（一次生成一次付费）；
 * - [persist]：把产物落成 canonical 资产（AI provenance + 幂等键派生的 id）；
 * - [resolveCurrentSheetBytes]：当前路由绑定的题面字节（不是模型给的），供 REDRAW 指纹与源证据；
 * - [uriFor]：规范记录 → 可渲染的本地 URI。
 *
 * 取消一律向上传播（应用被杀不是“这张图没有”）。
 */
class AttachedImageGenerator(
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
    /** 显式重生成通道；null = 本装配没有可用协调器（不生成、不假成功）。 */
    private val coordinator: FigureGenerationCoordinator? = null,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * 4E WG-08 T1b：产物登记成功后的审计回填（attemptId → 规范资产 id）。
     * 默认无操作：旧装配/测试不接审计时行为逐位不变。
     */
    private val recordOutputAsset: suspend (attemptId: String, assetId: String) -> Unit =
        { _, _ -> },
) {
    /**
     * 批量 cache-only 解析，按 `imageId` 给出显式结果（不再用“缺 key = 失败”表达）。
     */
    suspend fun resolve(images: List<AttachedImage>): Map<String, AttachedImageResolution> {
        val out = mutableMapOf<String, AttachedImageResolution>()
        for (image in images) {
            out[image.imageId] = resolveOne(image)
        }
        return out
    }

    /**
     * 单张历史意图的**只读**解析。命中返回本地 URI；否则返回带原因的不可用态。
     */
    suspend fun resolveOne(image: AttachedImage): AttachedImageResolution {
        val request = requestFor(image)
            ?: return AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
                "缺少这张图所需的题面原图",
            )
        return lookupResolution(image, request)
    }

    /**
     * 显式“重新生成”：绑定原消息的父操作，进入共享协调器。
     *
     * - 已命中且文件完好：直接回本地 URI（不再付费）；
     * - 资产损坏：如实回 [AttachedImageUnavailableReason.DAMAGED]，不覆盖同键行
     *   （登记行的 sha/长度不会因同 id 重登记而刷新，盲目覆盖会让后续读回一直失败）；
     * - 缺源 / 未注入协调器 / 账本拒绝 / 后端失败：各回一个可读原因，不伪成功。
     */
    suspend fun regenerate(
        image: AttachedImage,
        parentRequestId: String,
    ): AttachedImageResolution {
        require(parentRequestId.isNotBlank()) {
            "A regenerate action must bind the original message's model task"
        }
        val request = requestFor(image)
            ?: return AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
                "缺少这张图所需的题面原图",
            )
        val existing = try {
            lookupExisting(attachedImageFingerprintId(image, request))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.LOCAL_READ_FAILED,
                failure.boundedMessage(),
            )
        }
        if (existing != null) {
            return if (isIntact(existing)) {
                AttachedImageResolution.Ready(uriFor(existing))
            } else {
                AttachedImageResolution.Unavailable(
                    AttachedImageUnavailableReason.DAMAGED,
                    "本地副本与登记不一致，未覆盖重生成",
                )
            }
        }
        val coordinator = coordinator
            ?: return AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.REGENERATION_UNAVAILABLE,
                "本装配没有可用的生图协调器",
            )
        val figureId = attachedImageFingerprintId(image, request)
        val purpose = image.kind.toPurpose()
        val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
        val budget = sharedOperationFigureBudget(reserveChatFinalAnswer = false)
        val source = request.sourceImageBytes?.let { bytes ->
            FigureSourceEvidence(
                contentSha256 = figureSha256Hex(bytes),
                mimeType = request.sourceImageMimeType,
                imageBytes = bytes,
            )
        }
        // 稳定逻辑操作 id（不含 method）；审计来源是**原消息**（显式重生成仍属于它）。
        val operationId = "attached-image-op:$parentRequestId:${image.kind.name}:$figureId"
        val generationRequest = FigureGenerationRequest(
            operationId = operationId,
            purpose = purpose,
            target = when (image.kind) {
                AttachedImageKind.REDRAW_PROBLEM -> FigureGenerationTarget(
                    fidelity = FigureFidelity.FAITHFUL,
                    requiresPixelFidelity = false,
                    requiresCompleteLabels = true,
                )

                AttachedImageKind.GENERATE_PROCESS -> FigureGenerationTarget(
                    fidelity = FigureFidelity.ILLUSTRATIVE,
                    requiresPixelFidelity = false,
                    requiresCompleteLabels = true,
                )
            },
            capabilities = capabilities,
            budget = budget,
            dispatchParent = FigureDispatchParent.ModelTask(
                requestId = parentRequestId,
                // 历史消息已经出过终答；显式重生成是独立动作，不占聊天终答位。
                reserveChatFinalAnswer = false,
            ),
            source = source,
            constraints = hostConstraints(image.kind),
            prompt = FigurePromptAssembler.assemble(purpose, capabilities, budget),
            sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
            sourceId = parentRequestId,
            basisFingerprint = figureBasisFingerprint(
                operationId = operationId,
                sourceSha256 = source?.contentSha256,
                imagePrompt = request.prompt,
            ),
        )
        // 本波次唯一已接通的生成方法是 IMAGE_MODEL；入口只适配本意图，不建方法选择表。
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = generationRequest,
            imageOperation = when (image.kind) {
                AttachedImageKind.REDRAW_PROBLEM -> FigureImageOperation.EDIT
                AttachedImageKind.GENERATE_PROCESS -> FigureImageOperation.GENERATE
            },
            imagePrompt = request.prompt,
            reason = "历史配图显式重新生成：使用统一协调器（本波次唯一已接通 IMAGE_MODEL）",
        )
        val outcome = try {
            coordinator.generate(generationRequest, proposal)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.REGENERATION_FAILED,
                failure.boundedMessage(),
            )
        }
        return outcome.toAttachedImageResolution(image, figureId)
    }

    private suspend fun lookupResolution(
        image: AttachedImage,
        request: ImageGenerationRequest,
    ): AttachedImageResolution {
        val figureId = attachedImageFingerprintId(image, request)
        val existing = try {
            lookupExisting(figureId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.LOCAL_READ_FAILED,
                failure.boundedMessage(),
            )
        } ?: return AttachedImageResolution.Unavailable(
            AttachedImageUnavailableReason.NOT_GENERATED,
            "这条历史消息在本机没有已生成的副本；打开消息不会自动联网生成",
        )
        val intact = try {
            isIntact(existing)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return if (intact) {
            AttachedImageResolution.Ready(uriFor(existing))
        } else {
            AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.DAMAGED,
                "本地副本缺失或与登记不一致",
            )
        }
    }

    private suspend fun FigureGenerationOutcome.toAttachedImageResolution(
        image: AttachedImage,
        figureId: String,
    ): AttachedImageResolution {
        val artifact = artifact
        return when (status) {
            FigureGenerationStatus.GENERATED,
            FigureGenerationStatus.CACHED,
            -> {
                val asset = artifact as? FigureArtifact.ImageAsset
                    ?: return AttachedImageResolution.Unavailable(
                        AttachedImageUnavailableReason.REGENERATION_FAILED,
                        "协调器没有返回可持久化的图像产物",
                    )
                val record = try {
                    persist(
                        asset.imageBytes(),
                        asset.mimeType,
                        StudyDbValue.SourceAssetType.GENERATED_FIGURE,
                        now(),
                        figureId,
                        GeneratedFigureProvenance(
                            model = asset.modelId ?: OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                            figureId = figureId,
                            purpose = image.kind.name,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    return AttachedImageResolution.Unavailable(
                        AttachedImageUnavailableReason.REGENERATION_FAILED,
                        "生成成功但本地保存失败：${failure.boundedMessage()}",
                    )
                }
                // WG-08：产物已登记，回填这次 attempt 的 output_asset_id（best-effort）。
                recordFigureOutputAsset(recordOutputAsset, attemptId, record.sourceAssetId)
                AttachedImageResolution.Ready(uriFor(record))
            }

            FigureGenerationStatus.UNAVAILABLE -> AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.REGENERATION_UNAVAILABLE,
                diagnostics,
            )

            FigureGenerationStatus.REJECTED -> AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.REGENERATION_REJECTED,
                diagnostics,
            )

            FigureGenerationStatus.FAILED -> AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.REGENERATION_FAILED,
                diagnostics,
            )

            FigureGenerationStatus.NO_FIGURE,
            FigureGenerationStatus.NEED_SOURCE,
            -> AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
                diagnostics,
            )
        }
    }

    /** Builds the concrete image request for an intent; null when the figure cannot be produced. */
    private suspend fun requestFor(image: AttachedImage): ImageGenerationRequest? = when (image.kind) {
        AttachedImageKind.GENERATE_PROCESS -> ImageGenerationRequest(
            prompt = processPrompt(image.description),
        )
        AttachedImageKind.REDRAW_PROBLEM -> {
            val source = try {
                resolveCurrentSheetBytes()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return null
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

    private fun AttachedImageKind.toPurpose(): FigureGenerationPurpose = when (this) {
        AttachedImageKind.REDRAW_PROBLEM -> FigureGenerationPurpose.SOURCE_RECONSTRUCTION
        AttachedImageKind.GENERATE_PROCESS -> FigureGenerationPurpose.EXPLANATION
    }

    private fun hostConstraints(kind: AttachedImageKind): List<String> = when (kind) {
        AttachedImageKind.REDRAW_PROBLEM -> listOf(
            "保留印刷题面的全部文字、标注、单位与点线关系",
            "不增加题意之外的符号或条件",
        )

        AttachedImageKind.GENERATE_PROCESS -> listOf(
            "只绘制与当前题相关的图形、数轴、步骤标注",
            "不改写题面文字，不添加题意之外的符号",
        )
    }

    private fun Exception.boundedMessage(): String? =
        (message ?: this::class.simpleName)
            ?.take(AttachedImageResolution.MAX_DETAIL_CHARS)
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
