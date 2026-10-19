package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.data.capture.GuardedEditsChannelFactory
import com.tingyun.smartmistakebook.core.data.visual.FigureImageBackend
import com.tingyun.smartmistakebook.core.data.visual.DefaultFigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.UnavailableRemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.util.Arrays
import kotlinx.coroutines.CancellationException

/**
 * A1（阶段 4B）：工具环 `GENERATE_FIGURE` 的执行缝。
 *
 * 4E.1 起，本缝不再自己决定"用哪种后端/怎么出网"：它把工具语义（kind/description）适配成
 * 统一 [FigureGenerationRequest] + [com.tingyun.smartmistakebook.core.model.FigureGenerationProposal]，
 * 转调 [FigureGenerationCoordinator]；coordinator 负责 policy 校验、持久派发预留、图像通道
 * 调用与类型化产物。入口**不按 kind 选后端**——kind 只映射 purpose。
 *
 * 消灭的失败：此前两处直接 `channel.generate`（本类与 `AttachedImageGeneratorFactory`）都没有
 * 持久预算门；这里统一到 coordinator 后，没有预留的图像 HTTP 调用为 0。
 *
 * null（未注入协调器/未配置模型）是显式的 fail-closed 装配态：runner 回"生图通道不可用"，
 * 不假装成功。结果只回 **id + 状态**——字节留在本地规范资产库，不进工具结果、不进下一轮提示词。
 */
fun interface TutorFigureGenerator {
    suspend fun generate(request: TutorFigureRequest): TutorFigureResult
}

/**
 * 一次生图请求：模型只给 kind + description；源图由本地解析（绝不来自模型）。
 *
 * [sourceSheet] 是**规范资产记录**而不是字节：读取时由 vault 再核对一次（文件存在 +
 * 字节数 + sha256 逐位一致），从"决定重绘"到真正出网之间文件被替换时不会把错字节发出去。
 *
 * [parentOperationId] 是宿主冻结的父模型任务 requestId（工具环的 `evidenceIdNamespace`）；
 * 它是持久派发预留的来源。直调（测试/兼容）省略时生成一个**诊断用**合成 id——真实账本会因
 * 找不到父操作而拒绝，仍然 fail-closed。
 */
data class TutorFigureRequest(
    val kind: TutorFigureKind,
    val description: String,
    /**
     * REDRAW_PROBLEM 的源题面规范记录（runner 从会话 → 草稿解析，**不是**模型给的）；
     * GENERATE_PROCESS 必须为 null（模型无法经任何字段提供图片）。
     */
    val sourceSheet: CanonicalSourceAssetRecord? = null,
    /**
     * GENERATE_PROCESS 的当前题上下文（草稿标题；null = 没有当前题上下文）。
     * REDRAW_PROBLEM 不使用它——源图本身就是上下文。
     */
    val problemContext: String? = null,
    /** 父模型任务 requestId；null = 直调（账本会拒绝，不出网）。 */
    val parentOperationId: String? = null,
) {
    init {
        require(description.isNotBlank()) { "A figure request needs a non-blank description" }
        require(kind == TutorFigureKind.REDRAW_PROBLEM || sourceSheet == null) {
            "Only REDRAW_PROBLEM may carry a source sheet"
        }
        require(kind != TutorFigureKind.REDRAW_PROBLEM || sourceSheet != null) {
            "REDRAW_PROBLEM requires the current problem's canonical source sheet"
        }
        require(parentOperationId == null || parentOperationId.isNotBlank()) {
            "A figure parent operation id must be null or non-blank"
        }
    }
}

/**
 * 生图结果：只回资产 id + 状态；[figureId] 仅 GENERATED 时非空。
 *
 * [model] 与 [generatedNow] 是 A2/A4 的记账半：模型标识（谁生成的）与"这次是否真的出网"
 * （幂等命中=false 时不得声称"本次生成已计入额度"——那是一句假账）。
 *
 * [diagnostics] 是 KD-60 的可读半：账本拒绝（共享预算已用尽/真实已派发次数）与后端失败的
 * 有界原文必须能沿结果回到调用方；不再只剩一句“配图没有生成成功”。
 */
data class TutorFigureResult(
    val figureId: String?,
    val status: TutorFigureStatus,
    val model: String? = null,
    val generatedNow: Boolean = false,
    val diagnostics: String? = null,
) {
    init {
        require(figureId == null || status == TutorFigureStatus.GENERATED) {
            "Only a generated figure carries an asset id"
        }
        require(figureId != null || !generatedNow) {
            "Only a generated figure can claim a fresh generation"
        }
        require(diagnostics == null || diagnostics.length <= MAX_DIAGNOSTICS_CHARS) {
            "A figure diagnostic must be at most $MAX_DIAGNOSTICS_CHARS chars"
        }
    }

    companion object {
        const val MAX_DIAGNOSTICS_CHARS: Int = 512
    }
}

/**
 * 生图状态（工具结果的本地半）。[UNAVAILABLE] 是"没有可用通道"（没配置凭证/本构建不可出网），
 * [FAILED] 是"通道在但这次没成"（出网/解码/落盘失败）——两者给学生的话不同。
 */
enum class TutorFigureStatus {
    GENERATED,
    FAILED,
    UNAVAILABLE,
}

/**
 * A2：生成图的**幂等键**（设计口径：`sha256(源图sha + prompt + size + quality + format)`）。
 *
 * 每个生成参数都必须进键——缺一个就可能在参数已经变化时命中旧图，或者反过来对同一个请求
 * 重复出网、重复付费。这里比设计口径多带两样**同样是生成参数**的东西：
 * - `kind`：同 prompt 的两种图是两件事（重绘 vs 文生图）；
 * - `model`：模型换了就是另一次生成（当前恒为 [OpenAiImageGenerationChannel.DEFAULT_MODEL_ID]，
 *   带上它保证将来换模型时旧图不会被误命中）。
 * size/quality 取的是**真正发给 provider 的值**（与 [OpenAiImageGenerationChannel] 共用
 * [imageGenerationSizeFor] / [IMAGE_GENERATION_QUALITY]），不是本地参数的代理。
 *
 * 返回 `figure-<sha256 hex>`：它同时是规范资产 id 与文件名（见 vault 的 `figureIdentity`），
 * 所以"同键重放"= 按 id 读回同一行，零 schema。
 */
internal fun tutorFigureFingerprintId(
    kind: TutorFigureKind,
    sourceImageSha256: String?,
    prompt: String,
    size: String,
    quality: String,
    format: String,
    modelId: String = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
): String {
    require(sourceImageSha256 == null || sourceImageSha256.isNotBlank()) {
        "A source image sha must be null or non-blank"
    }
    val canonical = buildString {
        append("figure-v1")
        append("|kind=").append(kind.name)
        append("|source=").append(sourceImageSha256 ?: "-")
        append("|prompt=").append(prompt)
        append("|size=").append(size)
        append("|quality=").append(quality)
        append("|format=").append(format)
        append("|model=").append(modelId)
    }
    return "figure-" + sha256Hex(canonical)
}

private fun sha256Hex(value: String): String =
    sha256Hex(value.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

/** 字节的 sha256（配图链算源图 sha 用；与指纹的字符串哈希同一实现）。 */
internal fun sha256Hex(value: ByteArray): String = figureSha256Hex(value)

/**
 * 生产装配（app 层入口）：复用用户已配置的模型凭证与 SSRF 防护客户端生成图，
 * 生成结果落规范资产库（与既有配图链同一 vault 写入面）并登记 canonical 行，只回 id+状态。
 *
 * 4E.1：出网统一经 app 注入的 [FigureGenerationCoordinator]（共享 ledger）；未注入协调器时
 * 使用本类的内部协调器（仍走同一 policy/ledger 代码）。
 */
object TutorFigureGeneratorFactory {
    fun create(
        context: Context,
        database: StudyDatabasePort,
        configurationStore: ModelConfigurationStore,
        networkRequestsAllowed: Boolean,
        coordinator: FigureGenerationCoordinator,
    ): TutorFigureGenerator {
        val vault = AndroidCanonicalAssetVault(context.applicationContext)
        return ConfiguredTutorFigureGenerator(
            configurationStore = configurationStore,
            networkRequestsAllowed = networkRequestsAllowed,
            readSourceBytes = { record ->
                runCatching { vault.resolve(record).readBytes() }.getOrNull()
            },
            lookupGenerated = { assetId -> database.readCanonicalSourceAsset(assetId) },
            isIntact = { record -> runCatching { vault.resolve(record) }.isSuccess },
            persistGenerated = { bytes, mimeType, createdAt, figureId, provenance ->
                val record = vault.persistCleanImageBytes(
                    bytes = bytes,
                    mimeType = mimeType,
                    sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
                    createdAtEpochMillis = createdAt,
                    provenance = provenance,
                    // id 与文件名都由幂等键派生：同键重放读到同一行、同一文件。
                    figureIdentity = figureId,
                )
                check(record.sourceAssetId == figureId) {
                    "A generated figure must keep the identity derived from its idempotency key"
                }
                database.registerCanonicalSourceAsset(record)
                record
            },
            channelFactory = GuardedEditsChannelFactory,
            coordinator = coordinator,
        )
    }
}

/**
 * 生产决策逻辑（幂等 / 门 / 源图核对 / coordinator / 落盘 / id+状态）本体。
 *
 * 依赖缝（[readSourceBytes] / [lookupGenerated] / [isIntact] / [persistGenerated]）刻意不直接
 * 拿 vault/database：它们是 Android/数据库类，直接依赖会把这段决策逻辑赶到只有设备才够得着的
 * 测试面上；缝把"文件 IO 与落库"同"决策"分开，JVM 单测就能钉住"一次生成一次付费"、
 * "没凭证不出网"、"源图读不出不生成"、"命中损坏文件不重付"这几条。
 */
internal class ConfiguredTutorFigureGenerator(
    private val configurationStore: ModelConfigurationStore,
    private val networkRequestsAllowed: Boolean,
    private val readSourceBytes: (CanonicalSourceAssetRecord) -> ByteArray?,
    private val lookupGenerated: suspend (assetId: String) -> CanonicalSourceAssetRecord?,
    private val isIntact: (CanonicalSourceAssetRecord) -> Boolean,
    private val persistGenerated: suspend (
        bytes: ByteArray,
        mimeType: String,
        createdAtEpochMillis: Long,
        figureId: String,
        provenance: GeneratedFigureProvenance,
    ) -> CanonicalSourceAssetRecord,
    private val channelFactory: ConfiguredCleanImageGenerator.ChannelFactory,
    private val coordinator: FigureGenerationCoordinator? = null,
    private val ledger: RemoteModelDispatchLedger = UnavailableRemoteModelDispatchLedger,
    private val now: () -> Long = System::currentTimeMillis,
) : TutorFigureGenerator {

    /**
     * 内部协调器（测试/未注入装配）：与生产协调器执行同一 policy/ledger/通道校验，
     * 只是后端由本类的凭证门 + 注入的通道工厂构造。
     */
    private val effectiveCoordinator: FigureGenerationCoordinator =
        coordinator ?: DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { openBackendFromSeams() },
            now = now,
        )

    override suspend fun generate(request: TutorFigureRequest): TutorFigureResult {
        // 先解析请求与幂等键（纯本地）：命中缓存时**不需要凭证**，也不再出网。
        val channelRequest = request.toChannelRequest()
            ?: return failed("源图读取失败，未生成（不把被替换的字节发出去）")
        val figureId = tutorFigureFingerprintId(
            kind = request.kind,
            sourceImageSha256 = request.sourceSheet?.contentSha256,
            prompt = channelRequest.prompt,
            size = imageGenerationSizeFor(channelRequest.maxDimension),
            quality = IMAGE_GENERATION_QUALITY,
            format = channelRequest.outputFormat,
        )
        val existing = lookupGenerated(figureId)
        if (existing != null) {
            // 行在但文件坏了：不出网、不重付（"一次生成一次付费"是硬规则），如实失败。
            if (!isIntact(existing)) return failed("已生成资产的本地副本损坏，未重付重生成")
            return TutorFigureResult(
                figureId = existing.sourceAssetId,
                status = TutorFigureStatus.GENERATED,
                model = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                generatedNow = false,
            )
        }
        // 门只有一处实现（A3）：resolveImageCredential。这里先做一次 fail-closed 预检，
        // 使"没凭证/不可出网/能力未确认"在打开任何后端之前就返回 UNAVAILABLE；coordinator
        // 的后端装配会再走同一道门（同一实现，不是第二份判定）。预检不发送，立即释放租约。
        val preflight = resolveImageCredential(configurationStore, networkRequestsAllowed)
            ?: return TutorFigureResult(
                figureId = null,
                status = TutorFigureStatus.UNAVAILABLE,
                diagnostics = "本机没有可用的图像凭证/能力（未出网）",
            )
        preflight.apiKey.close()

        val purpose = request.kind.toPurpose()
        val target = when (request.kind) {
            TutorFigureKind.REDRAW_PROBLEM -> FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = false,
                requiresCompleteLabels = true,
            )

            TutorFigureKind.GENERATE_PROCESS -> FigureGenerationTarget(
                fidelity = FigureFidelity.ILLUSTRATIVE,
                requiresPixelFidelity = false,
                requiresCompleteLabels = true,
            )
        }
        // KD-60：声明共享上限 + 未预读余量（真门在持久账本）。
        val budget = sharedOperationFigureBudget(reserveChatFinalAnswer = true)
        val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
        val source = request.sourceEvidence(channelRequest)
        val constraints = request.hostConstraints()
        val generationRequest = FigureGenerationRequest(
            operationId = "figure-op:${request.kind.name}:$figureId",
            purpose = purpose,
            target = target,
            capabilities = capabilities,
            budget = budget,
            dispatchParent = FigureDispatchParent.ModelTask(
                requestId = request.parentOperationId ?: "figure-direct:${request.kind.name}",
                reserveChatFinalAnswer = true,
            ),
            source = source,
            constraints = constraints,
            problemContext = request.problemContext,
            prompt = FigurePromptAssembler.assemble(purpose, capabilities, budget),
        )
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = generationRequest,
            imageOperation = when (request.kind) {
                TutorFigureKind.REDRAW_PROBLEM -> FigureImageOperation.EDIT
                TutorFigureKind.GENERATE_PROCESS -> FigureImageOperation.GENERATE
            },
            imagePrompt = channelRequest.prompt,
            reason = "入口适配层：本波次唯一已接通的方法是图像模型",
        )
        val outcome = effectiveCoordinator.generate(generationRequest, proposal)
        return outcome.toTutorFigureResult(figureId)
    }

    private suspend fun FigureGenerationOutcome.toTutorFigureResult(
        figureId: String,
    ): TutorFigureResult =
        when (status) {
            FigureGenerationStatus.GENERATED,
            FigureGenerationStatus.CACHED,
            -> {
                val artifact = artifact as? FigureArtifact.ImageAsset
                    ?: return failed("协调器没有返回可持久化的图像产物")
                val persisted = try {
                    persistGenerated(
                        artifact.imageBytes(),
                        artifact.mimeType,
                        now(),
                        figureId,
                        GeneratedFigureProvenance(
                            model = artifact.modelId ?: OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                            figureId = figureId,
                            purpose = purpose.name,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    return failed(
                        "生成成功但本地保存失败：${failure.message ?: "未知错误"}",
                    )
                }
                TutorFigureResult(
                    figureId = persisted.sourceAssetId,
                    status = TutorFigureStatus.GENERATED,
                    model = artifact.modelId ?: OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                    generatedNow = status == FigureGenerationStatus.GENERATED,
                )
            }

            FigureGenerationStatus.UNAVAILABLE -> TutorFigureResult(
                figureId = null,
                status = TutorFigureStatus.UNAVAILABLE,
                diagnostics = diagnostics,
            )

            FigureGenerationStatus.NO_FIGURE,
            FigureGenerationStatus.NEED_SOURCE,
            FigureGenerationStatus.REJECTED,
            FigureGenerationStatus.FAILED,
            -> failed(diagnostics)
        }

    /**
     * 请求 → 通道请求；REDRAW_PROBLEM 读源图前**再核对一次**规范记录（缝的实现负责
     * `vault.resolve` 的逐位校验），核对不过即 null → FAILED（不把被替换的字节发出去）。
     */
    private fun TutorFigureRequest.toChannelRequest(): ImageGenerationRequest? = when (kind) {
        TutorFigureKind.REDRAW_PROBLEM -> {
            val sheet = requireNotNull(sourceSheet) { "REDRAW_PROBLEM source sheet is contract-enforced" }
            val bytes = readSourceBytes(sheet) ?: return null
            ImageGenerationRequest(
                prompt = attachedImageRedrawInstruction(),
                sourceImageBytes = bytes,
                sourceImageMimeType = sheet.mimeType,
            )
        }

        TutorFigureKind.GENERATE_PROCESS -> ImageGenerationRequest(
            prompt = attachedImageProcessPrompt(description, problemContext),
        )
    }

    private fun TutorFigureRequest.sourceEvidence(
        channelRequest: ImageGenerationRequest,
    ): FigureSourceEvidence? {
        val bytes = channelRequest.sourceImageBytes ?: return null
        val mimeType = channelRequest.sourceImageMimeType
            ?: return null
        return FigureSourceEvidence(
            assetId = sourceSheet?.sourceAssetId,
            contentSha256 = figureSha256Hex(bytes),
            mimeType = mimeType,
            imageBytes = bytes,
        )
    }

    private fun TutorFigureRequest.hostConstraints(): List<String> = when (kind) {
        TutorFigureKind.REDRAW_PROBLEM -> listOf(
            "保留印刷题面的全部文字、标注、单位与点线关系",
            "不增加题意之外的符号或条件",
        )

        TutorFigureKind.GENERATE_PROCESS -> listOf(
            "只绘制与当前题相关的图形、数轴、步骤标注",
            "不改写题面文字，不添加题意之外的符号",
        )
    }

    /** 内部协调器的后端：凭证门 + 注入的通道工厂（生产用 GuardedEditsChannelFactory）。 */
    private suspend fun openBackendFromSeams(): FigureImageBackend? {
        val credential = resolveImageCredential(configurationStore, networkRequestsAllowed)
            ?: return null
        val configuration = credential.configuration
        val keyChars = credential.apiKey.copyChars()
        val closeAction = {
            Arrays.fill(keyChars, '\u0000')
            credential.apiKey.close()
        }
        val configurationFingerprint = imageGenerationConfigurationFingerprint(
            configuration.baseUrl,
            OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
        )
        return try {
            FigureImageBackend(
                configurationFingerprint = configurationFingerprint,
                // 通道工厂/端点解析推迟到账本授予之后（被拒时连通道都不建）；
                // 指纹与预留同源，生产通道据此复核配置绑定。
                openChannel = {
                    channelFactory.create(
                        baseUrl = configuration.baseUrl,
                        authorization = "Bearer ${String(keyChars)}",
                        configurationFingerprint = configurationFingerprint,
                    )
                },
                closeAction = closeAction,
            )
        } catch (cancelled: CancellationException) {
            closeAction()
            throw cancelled
        } catch (_: Exception) {
            closeAction()
            null
        }
    }

    private fun failed(diagnostics: String? = null): TutorFigureResult =
        TutorFigureResult(
            figureId = null,
            status = TutorFigureStatus.FAILED,
            diagnostics = diagnostics?.take(TutorFigureResult.MAX_DIAGNOSTICS_CHARS),
        )
}

private fun TutorFigureKind.toPurpose(): FigureGenerationPurpose = when (this) {
    TutorFigureKind.REDRAW_PROBLEM -> FigureGenerationPurpose.SOURCE_RECONSTRUCTION
    TutorFigureKind.GENERATE_PROCESS -> FigureGenerationPurpose.EXPLANATION
}
