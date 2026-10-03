package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGenerator
import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.data.capture.GuardedEditsChannelFactory
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
 *
 * A2（批 2）补上这条链的另一半：结果**落规范资产行 + 写文件隐式元数据**，并按幂等键
 * 命中已有生成图（[tutorFigureFingerprintId]）——同请求重放不再出网、不再付费。
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

/**
 * 生图结果：只回资产 id + 状态；[figureId] 仅 GENERATED 时非空。
 *
 * [model] 与 [generatedNow] 是 A2/A4 的记账半：模型标识（谁生成的）与"这次是否真的出网"
 * （幂等命中=false 时不得声称"本次生成已计入额度"——那是一句假账）。
 */
data class TutorFigureResult(
    val figureId: String?,
    val status: TutorFigureStatus,
    val model: String? = null,
    val generatedNow: Boolean = false,
) {
    init {
        require(figureId == null || status == TutorFigureStatus.GENERATED) {
            "Only a generated figure carries an asset id"
        }
        require(figureId != null || !generatedNow) {
            "Only a generated figure can claim a fresh generation"
        }
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
 *
 * 零 schema 下的**状态机表达**（设计稿 PENDING→GENERATED→REFERENCED；无新列可用，状态由
 * 现有持久事实**推导**，不另存一个会漂的副本）：
 * - `PENDING`：还没有 canonical 行——生成在途（界面由工具痕迹的"正在查阅…"体现）；
 * - `GENERATED`：canonical 行已登记（[StudyDatabasePort.registerCanonicalSourceAsset]）；
 * - `REFERENCED`：引用行已建立（消息落库同事务写 `tutor_message_source_asset`；已保存题面的
 *   重绘另写 `problem_revision_source_asset` role=CLEAN_IMAGE）。
 * 界面按"有没有引用 + 资产能否读回"消费（见 `GeneratedFiguresSection`）。
 *
 * 设计稿还要求"记录源图 sha 与 prompt sha"——零 schema 下没有这样的列，两者的**组合就是
 * 这个 id 本身**（指纹 = 源图 sha + prompt + 全部生成参数），可由 id 复算核对。
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
    sha256Hex(value.toByteArray(StandardCharsets.UTF_8))

/** 字节的 sha256（配图链算源图 sha 用；与指纹的字符串哈希同一实现）。 */
internal fun sha256Hex(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

/**
 * 生产装配（app 层入口）：复用用户已配置的模型凭证与 SSRF 防护客户端生成图，
 * 生成结果落规范资产库（与既有配图链同一 vault 写入面）并登记 canonical 行，只回 id+状态。
 *
 * 门是 [resolveImageCredential]（唯一实现）：本构建不可出网 / 没配模型 / 能力测试没确认
 * 图像输入 → 一律 [TutorFigureStatus.UNAVAILABLE]，一个字节都不发。
 *
 * A2：先按幂等键查已生成资产——命中且文件完好时**连门都不用过**（本地读回，不出网）；
 * 登记走 [StudyDatabasePort.registerCanonicalSourceAsset]（canonical 行是 GC 的可见面，
 * 引用由消息落库时建立）。
 */
object TutorFigureGeneratorFactory {
    fun create(
        context: Context,
        database: StudyDatabasePort,
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
        )
    }
}

/**
 * 生产决策逻辑（幂等 / 门 / 源图核对 / 通道 / 落盘 / id+状态）本体。
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
    private val now: () -> Long = System::currentTimeMillis,
) : TutorFigureGenerator {

    override suspend fun generate(request: TutorFigureRequest): TutorFigureResult {
        // 先解析请求与幂等键（纯本地）：命中缓存时**不需要凭证**，也不再出网。
        val channelRequest = request.toChannelRequest()
            ?: return failed()
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
            if (!isIntact(existing)) return failed()
            return TutorFigureResult(
                figureId = existing.sourceAssetId,
                status = TutorFigureStatus.GENERATED,
                model = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                generatedNow = false,
            )
        }
        val credential = resolveImageCredential(configurationStore, networkRequestsAllowed)
            ?: return TutorFigureResult(figureId = null, status = TutorFigureStatus.UNAVAILABLE)
        return credential.apiKey.use { apiKey ->
            val keyChars = apiKey.copyChars()
            try {
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
                    persistGenerated(
                        generated.imageBytes,
                        generated.mimeType,
                        now(),
                        figureId,
                        GeneratedFigureProvenance(
                            model = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                            figureId = figureId,
                            purpose = request.kind.name,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@use failed()
                }
                TutorFigureResult(
                    figureId = record.sourceAssetId,
                    status = TutorFigureStatus.GENERATED,
                    model = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                    generatedNow = true,
                )
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
