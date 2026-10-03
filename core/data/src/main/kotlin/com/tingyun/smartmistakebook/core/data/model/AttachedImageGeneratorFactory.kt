package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import android.net.Uri
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.model.AttachedImage

/**
 * Public app-layer entry point for the attached-figure resolver. Assembles a
 * production [AttachedImageGenerator] over the user's configured model
 * credential (SSRF-guarded, same as the chat gateway), resolving each model
 * [AttachedImage] intent to a persisted local `file://` URI.
 *
 * [resolveCurrentSheetBytes] must come from the caller — it supplies the current
 * question's problem-sheet bytes for a REDRAW_PROBLEM (never model-supplied).
 * Networking-declined / no credential / no image capability → returns a resolver
 * that always yields null (so no figure shows), mirroring the clean-redraw gate.
 *
 * A2（4B）：生成结果登记 canonical 行（幂等键派生的 id）并写隐式 AI 元数据；重复解析同一
 * 意图先查该行——旋转/重建不会再次出网付费。这是**兼容链**（`attachedImages` 兼容字段）：
 * 新配图一律走 `GENERATE_FIGURE` 工具链（F4）。
 */
object AttachedImageGeneratorFactory {
    fun create(
        context: Context,
        database: StudyDatabasePort,
        configurationStore: ModelConfigurationStore?,
        networkRequestsAllowed: Boolean,
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
    ): suspend (AttachedImage) -> String? {
        if (configurationStore == null) return { null }
        val vault = AndroidCanonicalAssetVault(context.applicationContext)
        val generator = AttachedImageGenerator(
            generate = { request ->
                val credential = resolveImageCredential(configurationStore, networkRequestsAllowed)
                    ?: throw ImageGenerationException("no usable model credential")
                credential.apiKey.use { apiKey ->
                    val keyChars = apiKey.copyChars()
                    try {
                        val endpoint = resolveGuardedEdits(credential.configuration.baseUrl)
                        val channel = OpenAiImageGenerationChannel(
                            baseUrl = endpoint.baseUrl,
                            client = endpoint.client,
                            authorization = "Bearer ${String(keyChars)}",
                        )
                        channel.generate(request)
                    } finally {
                        java.util.Arrays.fill(keyChars, '\u0000')
                    }
                }
            },
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
        )
        return { image -> generator.resolve(listOf(image), System.currentTimeMillis())[image.imageId] }
    }
}
