package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.capture.GuardedEditsChannelFactory
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import java.util.Arrays
import kotlinx.coroutines.CancellationException

/**
 * A1（阶段 4B）：工具环 `GENERATE_FIGURE` 的执行缝。
 *
 * 为什么是注入接口而不是 runner 直接出网：runner 被大量直调（单测/仪器化），而生图要复用
 * 生产凭证通道——`resolveImageCredential` 是**唯一**的图片门（A3 单源）、
 * `OpenAiImageGenerationChannel` 是**唯一**的生成实现（`/v1/images/edits` 有源图 /
 * `/v1/images/generations` 无源图两条路径），加上规范资产库的写入面；这些都只在 app
 * 装配期拿得到。生产由 [TutorFigureGeneratorFactory] 装配，经 `ModelTaskRepositoryFactory`
 * 穿到 runner。
 *
 * 消灭的失败：工具面此前**没有任何生图执行路径**（`GENERATE_FIGURE` 只存在于设计稿），
 * 模型无法在工具环里要一张图。此接口把"要什么图"（模型语义）与"怎么生成/落盘"（本地）
 * 分开，runner 只解析**源图是当前题的哪条规范资产**，绝不接受模型供图。
 *
 * null（未注入）是显式的 fail-closed 装配态：runner 回"生图通道不可用"，不假装成功。
 * 结果只回 **id + 状态**（[TutorFigureResult]）——字节留在本地规范资产库，不进工具结果、
 * 不进下一轮提示词。
 */
fun interface TutorFigureGenerator {
    suspend fun generate(request: TutorFigureRequest): TutorFigureResult
}

/**
 * 一次生图请求：模型只给 kind + description；源图由本地解析（绝不来自模型）。
 *
 * [sourceSheet] 是**规范资产记录**而不是字节：读取时由 vault 再核对一次（文件存在 +
 * 字节数 + sha256 逐位一致），从"决定重绘"到真正出网之间文件被替换时不会把错字节发出去。
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
) {
    init {
        require(description.isNotBlank()) { "A figure request needs a non-blank description" }
        require(kind == TutorFigureKind.REDRAW_PROBLEM || sourceSheet == null) {
            "Only REDRAW_PROBLEM may carry a source sheet"
        }
        require(kind != TutorFigureKind.REDRAW_PROBLEM || sourceSheet != null) {
            "REDRAW_PROBLEM requires the current problem's canonical source sheet"
        }
    }
}

/** 生图结果：只回资产 id + 状态；[figureId] 仅 GENERATED 时非空。 */
data class TutorFigureResult(
    val figureId: String?,
    val status: TutorFigureStatus,
)

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
 * 生产装配（app 层入口）：复用用户已配置的模型凭证与 SSRF 防护客户端生成图，
 * 生成结果落规范资产库（与既有配图链同一 vault 写入面），只回 id+状态。
 *
 * 门是 [resolveImageCredential]（唯一实现）：本构建不可出网 / 没配模型 / 能力测试没确认
 * 图像输入 → 一律 [TutorFigureStatus.UNAVAILABLE]，一个字节都不发。
 */
object TutorFigureGeneratorFactory {
    fun create(
        context: Context,
        configurationStore: ModelConfigurationStore,
        networkRequestsAllowed: Boolean,
    ): TutorFigureGenerator {
        val vault = AndroidCanonicalAssetVault(context.applicationContext)
        return ConfiguredTutorFigureGenerator(
            configurationStore = configurationStore,
            networkRequestsAllowed = networkRequestsAllowed,
            // 读取前逐位核对（vault.resolve 是唯一实现）；失败即 null → FAILED，不发错字节。
            readSourceBytes = { record ->
                runCatching { vault.resolve(record).readBytes() }.getOrNull()
            },
            persistGenerated = { bytes, mimeType, createdAt ->
                vault.persistCleanImageBytes(
                    bytes = bytes,
                    mimeType = mimeType,
                    sourceType = StudyDbValue.SourceAssetType.TUTOR_ATTACHED,
                    createdAtEpochMillis = createdAt,
                )
            },
            channelFactory = GuardedEditsChannelFactory,
        )
    }
}

/**
 * 生产决策逻辑（门 / 源图核对 / 通道 / 落盘 / id+状态）本体。
 *
 * 两个依赖缝（[readSourceBytes] / [persistGenerated]）刻意不直接拿 vault：vault 是
 * Android 类（Context + Bitmap），直接依赖会把这段决策逻辑赶到只有设备才够得着的测试面上；
 * 缝把"文件 IO"与"决策"分开，JVM 单测就能钉住"没凭证不出网 / 源图读不出不生成 /
 * 只回 id+状态"这几条。
 */
internal class ConfiguredTutorFigureGenerator(
    private val configurationStore: ModelConfigurationStore,
    private val networkRequestsAllowed: Boolean,
    private val readSourceBytes: (CanonicalSourceAssetRecord) -> ByteArray?,
    private val persistGenerated: (
        bytes: ByteArray,
        mimeType: String,
        createdAtEpochMillis: Long,
    ) -> CanonicalSourceAssetRecord,
    private val channelFactory: ConfiguredCleanImageGenerator.ChannelFactory,
    private val now: () -> Long = System::currentTimeMillis,
) : TutorFigureGenerator {

    override suspend fun generate(request: TutorFigureRequest): TutorFigureResult {
        val credential = resolveImageCredential(configurationStore, networkRequestsAllowed)
            ?: return TutorFigureResult(figureId = null, status = TutorFigureStatus.UNAVAILABLE)
        return credential.apiKey.use { apiKey ->
            val keyChars = apiKey.copyChars()
            try {
                val channelRequest = request.toChannelRequest()
                    ?: return@use failed()
                val channel = try {
                    channelFactory.create(
                        baseUrl = credential.configuration.baseUrl,
                        authorization = "Bearer ${String(keyChars)}",
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@use failed()
                }
                val generated = try {
                    channel.generate(channelRequest)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@use failed()
                }
                val record = try {
                    persistGenerated(generated.imageBytes, generated.mimeType, now())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@use failed()
                }
                TutorFigureResult(figureId = record.sourceAssetId, status = TutorFigureStatus.GENERATED)
            } finally {
                Arrays.fill(keyChars, '\u0000')
            }
        }
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

    private fun failed(): TutorFigureResult =
        TutorFigureResult(figureId = null, status = TutorFigureStatus.FAILED)
}
