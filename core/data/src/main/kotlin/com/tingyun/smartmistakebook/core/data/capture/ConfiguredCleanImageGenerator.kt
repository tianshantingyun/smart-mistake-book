package com.tingyun.smartmistakebook.core.data.capture

import com.tingyun.smartmistakebook.core.data.model.FigurePromptAssembler
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.OpenAiImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.imageGenerationConfigurationFingerprint
import com.tingyun.smartmistakebook.core.data.model.resolveGuardedEdits
import com.tingyun.smartmistakebook.core.data.model.resolveImageCredential
import com.tingyun.smartmistakebook.core.data.visual.DefaultFigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.data.visual.FigureImageBackend
import com.tingyun.smartmistakebook.core.domain.CleanImageGenerator
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.CleanImageResult
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.UnavailableRemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.util.Arrays
import kotlinx.coroutines.CancellationException

/**
 * Production [CleanImageGenerator] that reuses the model configuration the user
 * already entered for chat (the same baseUrl + API key leased atomically from
 * [ModelConfigurationStore]).
 *
 * 4E.1：净化不再是"自己开一个 edits 通道"的独立路径——它构造统一
 * [FigureGenerationRequest] + IMAGE_MODEL proposal，转调 app 注入的
 * [FigureGenerationCoordinator]（共享 policy/ledger）。真正出网时，图像通道在 POST 前复核
 * 预留绑定；没有 `CleanRedrawTaskRunner` 已领取的持久作业尝试作为父操作时，请求在校验层
 * 即被拒绝，一个字节都不发。
 *
 * Gating (a redraw is only attempted when adding to the mistake book):
 *  - no network-capable flavor            → decline (null)
 *  - no configured credential            → decline (null)
 *  - capability test never ran or did not verify image input → decline (null)
 *
 * A3：这三条判定的**唯一实现**是 [resolveImageCredential]（本类只调用它，不再内联一份）——
 * 与配图链（`AttachedImageGeneratorFactory` / `ConfiguredTutorFigureGenerator`）同一道门。
 *
 * A failed edit (network, auth, provider, decode) also declines — the commit is
 * already durable, so the mistake keeps the original photo. Cancellation
 * propagates so an app shutdown does not silently swallow it.
 */
internal class ConfiguredCleanImageGenerator(
    private val configurationStore: ModelConfigurationStore,
    private val channelFactory: ChannelFactory = GuardedEditsChannelFactory,
    private val networkRequestsAllowed: Boolean = true,
    private val coordinator: FigureGenerationCoordinator? = null,
    private val ledger: RemoteModelDispatchLedger = UnavailableRemoteModelDispatchLedger,
    private val now: () -> Long = System::currentTimeMillis,
) : CleanImageGenerator {

    /** 内部协调器（测试/未注入装配）：与生产协调器同一 policy/ledger/通道校验。 */
    private val effectiveCoordinator: FigureGenerationCoordinator =
        coordinator ?: DefaultFigureGenerationCoordinator(
            ledger = ledger,
            openBackend = { openBackendFromSeams() },
            now = now,
        )

    override suspend fun generateClean(request: CleanImageRequest): CleanImageResult? {
        // 门只有一处实现（A3）：resolveImageCredential（本构建不可出网 / 没配凭证 /
        // 能力测试没确认图像输入 → null）。预检不发送，立即释放租约。
        val preflight = resolveImageCredential(configurationStore, networkRequestsAllowed)
            ?: return null
        preflight.apiKey.close()

        val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
        val budget = FigureGenerationBudget(
            remainingRemoteDispatches = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES,
            reserveChatFinalAnswer = false,
        )
        val sourceSha = figureSha256Hex(request.originalBytes)
        val generationRequest = FigureGenerationRequest(
            operationId = request.operationId ?: "clean-redraw-op:$sourceSha",
            purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = false,
                requiresCompleteLabels = true,
            ),
            capabilities = capabilities,
            budget = budget,
            dispatchParent = request.parent,
            source = FigureSourceEvidence(
                contentSha256 = sourceSha,
                mimeType = request.mimeType,
                imageBytes = request.originalBytes,
            ),
            constraints = CLEAN_REDRAW_CONSTRAINTS,
            prompt = FigurePromptAssembler.assemble(
                purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
                capabilities = capabilities,
                budget = budget,
            ),
        )
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = generationRequest,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = CLEAN_REDRAW_INSTRUCTION,
            reason = "入口适配层：保存期净化使用当前唯一已接通的图像模型方法",
        )
        val outcome = effectiveCoordinator.generate(generationRequest, proposal)
        return outcome.toCleanImageResult()
    }

    private fun FigureGenerationOutcome.toCleanImageResult(): CleanImageResult? =
        if (status == FigureGenerationStatus.GENERATED || status == FigureGenerationStatus.CACHED) {
            (artifact as? FigureArtifact.ImageAsset)?.let { artifact ->
                CleanImageResult(bytes = artifact.imageBytes(), mimeType = artifact.mimeType)
            }
        } else {
            null
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

    /** Small seam so unit tests substitute a fake channel without DNS or network. */
    internal fun interface ChannelFactory {
        suspend fun create(baseUrl: String, authorization: String): ImageGenerationChannel

        /**
         * 生产工厂同时把**与预留相同的配置指纹**交给通道；测试替身只需实现两参版本，
         * 默认实现忽略指纹（fake 不校验配置）。
         */
        suspend fun create(
            baseUrl: String,
            authorization: String,
            configurationFingerprint: String,
        ): ImageGenerationChannel = create(baseUrl, authorization)
    }

    private companion object {
        const val CLEAN_REDRAW_INSTRUCTION =
            "这是一道被学生拍摄的题目照片。请保留印刷题面的所有文字与图形内容不变，" +
                "仅去除照片中的手写笔迹、涂改、无关阴影与折痕，重绘成一张干净的题面图。" +
                "不要改写、增删或重新排版任何印刷内容。"

        val CLEAN_REDRAW_CONSTRAINTS: List<String> = listOf(
            "保留印刷题面的全部文字、标注、单位与版式关系",
            "不改写、增删或重新排版任何印刷内容",
        )
    }
}

/**
 * Builds the authenticated redraw channel over the SSRF-guarded edits endpoint.
 * internal（不是 private）：4B A1 的 `ConfiguredTutorFigureGenerator` 复用同一个工厂，
 * 免得"带 SSRF 防护的图片通道"出现第二份构造。
 */
internal object GuardedEditsChannelFactory : ConfiguredCleanImageGenerator.ChannelFactory {
    override suspend fun create(
        baseUrl: String,
        authorization: String,
    ): ImageGenerationChannel = create(
        baseUrl = baseUrl,
        authorization = authorization,
        configurationFingerprint = imageGenerationConfigurationFingerprint(
            baseUrl,
            OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
        ),
    )

    override suspend fun create(
        baseUrl: String,
        authorization: String,
        configurationFingerprint: String,
    ): ImageGenerationChannel {
        val endpoint = resolveGuardedEdits(baseUrl)
        return OpenAiImageGenerationChannel(
            baseUrl = endpoint.baseUrl,
            client = endpoint.client,
            authorization = authorization,
            configurationFingerprint = configurationFingerprint,
        )
    }
}

/**
 * Public app-layer entry point. Returns a production clean-redraw generator over
 * the user's configured model credential, or a generator that always declines
 * when networking is not permitted by the current flavor.
 *
 * 4E.1：`coordinator` 是 app 装配的**共享**生图协调器（与讲题工具同一 ledger/policy）。
 */
object ConfiguredCleanImageGeneratorFactory {
    fun create(
        configurationStore: ModelConfigurationStore,
        networkRequestsAllowed: Boolean,
        coordinator: FigureGenerationCoordinator,
    ): CleanImageGenerator = ConfiguredCleanImageGenerator(
        configurationStore = configurationStore,
        channelFactory = GuardedEditsChannelFactory,
        networkRequestsAllowed = networkRequestsAllowed,
        coordinator = coordinator,
    )
}
