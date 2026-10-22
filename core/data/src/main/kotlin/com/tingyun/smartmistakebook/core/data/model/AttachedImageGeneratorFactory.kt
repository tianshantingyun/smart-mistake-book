package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import android.net.Uri
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.visual.RoomFigureGenerationAuditRecorder
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageResolutionPort

/**
 * 历史 `attachedImages` 解析器的生产装配口（WG-05 / KD-58）。
 *
 * **不再有图像后端**：本工厂不解析端点、不读凭证、不构造通道。三条出口：
 * - [create]：兼容入口，返回旧的 `String?` lambda——**cache-only**（只读本地 canonical 资产）。
 *   旧装配（`SmartMistakeBookApplication.attachedImageResolver`）继续用它，历史消息仍可显示，
 *   但渲染路径不会再出网；返回的函数对象**同时实现** [AttachedImageResolutionPort]，
 *   能读取真实原因并在用户显式动作时重生成；
 * - [createResolver]：带可读原因的 cache-only 解析；
 * - [createGenerator]：cache-only + 显式“重新生成”。重生成必须注入 app 的**共享**
 *   [FigureGenerationCoordinator]（同一 policy/ledger）；没有协调器时显式动作回
 *   `REGENERATION_UNAVAILABLE`，不静默失败，也不自建第二套预算/通道。
 *
 * 历史打开/展开/旋转/恢复只走 cache；生图只由用户显式动作触发（KD-58 的关闭条件）。
 */
object AttachedImageGeneratorFactory {
    /**
     * 兼容入口（cache-only）。[configurationStore] 与 [networkRequestsAllowed] 保留只为
     * 既有装配的源码兼容——本入口不再读凭证，也不再自动生成。
     *
     * [coordinator] 只用于**用户显式**重生成；null 时 [AttachedImageResolutionPort.regenerate]
     * 回 `REGENERATION_UNAVAILABLE`，读取路径仍可用。
     */
    @Suppress("UNUSED_PARAMETER")
    fun create(
        context: Context,
        database: StudyDatabasePort,
        configurationStore: ModelConfigurationStore?,
        networkRequestsAllowed: Boolean,
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
        coordinator: FigureGenerationCoordinator? = null,
    ): suspend (AttachedImage) -> String? {
        val generator = createGenerator(
            context = context,
            database = database,
            resolveCurrentSheetBytes = resolveCurrentSheetBytes,
            coordinator = coordinator,
        )
        // 返回对象同时是函数类型与 AttachedImageResolutionPort：面板的旧参数类型
        // 不需要改，TutorChatConversation 可做能力检测后拿到原因/重生成。
        return AttachedImageRuntimeResolver(generator)
    }

    /** cache-only 解析（含可读原因）；生产 `create` 的对象也通过端口提供同样原因。 */
    fun createResolver(
        context: Context,
        database: StudyDatabasePort,
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
    ): suspend (AttachedImage) -> AttachedImageResolution {
        val generator = generator(
            context = context,
            database = database,
            resolveCurrentSheetBytes = resolveCurrentSheetBytes,
            coordinator = null,
        )
        return { image -> generator.resolveOne(image) }
    }

    /**
     * 生产 generator：cache-only 读取 + 显式重生成经共享协调器。
     *
     * [coordinator] 为 app 装配的同一个实例（`FigureGenerationCoordinationFactory`）；
     * null 时只有读取，没有可用生图通道。
     */
    fun createGenerator(
        context: Context,
        database: StudyDatabasePort,
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
        coordinator: FigureGenerationCoordinator?,
    ): AttachedImageGenerator = generator(
        context = context,
        database = database,
        resolveCurrentSheetBytes = resolveCurrentSheetBytes,
        coordinator = coordinator,
    )

    private fun generator(
        context: Context,
        database: StudyDatabasePort,
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
        coordinator: FigureGenerationCoordinator?,
    ): AttachedImageGenerator {
        val vault = AndroidCanonicalAssetVault(context.applicationContext)
        // 审计回填与 coordinator 的 recorder 指向同一张台账（同一个 database 端口）。
        val audit = RoomFigureGenerationAuditRecorder(database)
        return AttachedImageGenerator(
            lookupExisting = { figureId -> database.readCanonicalSourceAsset(figureId) },
            isIntact = { record -> runCatching { vault.resolve(record) }.isSuccess },
            persist = { bytes, mimeType, sourceType, createdAt, figureId, provenance ->
                val record = vault.persistCleanImageBytes(
                    bytes = bytes,
                    mimeType = mimeType,
                    sourceType = sourceType,
                    createdAtEpochMillis = createdAt,
                    provenance = provenance,
                    figureIdentity = figureId,
                )
                database.registerCanonicalSourceAsset(record)
                record
            },
            resolveCurrentSheetBytes = resolveCurrentSheetBytes,
            uriFor = { record -> Uri.fromFile(vault.resolve(record)).toString() },
            coordinator = coordinator,
            recordOutputAsset = { attemptId, assetId -> audit.attachOutputAsset(attemptId, assetId) },
        )
    }
}

/**
 * 生产解析器：既是旧的 `String?` 函数，又是带原因/重生成能力的端口。
 *
 * `invoke` 保持旧语义（只回本地 URI 或 null）；`resolve` 保留 DAMAGED /
 * SOURCE_UNAVAILABLE / LOCAL_READ_FAILED / NOT_GENERATED 的真实原因；`regenerate`
 * 只由用户显式动作调用，走注入的共享 coordinator。
 */
internal class AttachedImageRuntimeResolver(
    private val generator: AttachedImageGenerator,
) : AttachedImageResolutionPort {
    override suspend fun invoke(image: AttachedImage): String? =
        (generator.resolveOne(image) as? AttachedImageResolution.Ready)?.localUri

    override suspend fun resolve(image: AttachedImage): AttachedImageResolution =
        generator.resolveOne(image)

    override suspend fun regenerate(
        parentRequestId: String,
        image: AttachedImage,
    ): AttachedImageResolution = generator.regenerate(image, parentRequestId)
}
