package com.tingyun.smartmistakebook.core.data.tutor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.tingyun.smartmistakebook.core.data.capture.AndroidBatchImportSourceStaging
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.batchImportProviderAuthority
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImage
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 交图给录入链路时的暂存目录：与大厅拍照落盘的目录同一个（capture provider 覆盖它）。 */
private const val INTAKE_STAGING_DIRECTORY = "captured_images"

/**
 * 消息附图摄取：相机拍照与相册选择都统一落到规范资产库。
 *
 * - 相机路径给出的是本应用 FileProvider 的 URI，vault 可直接导入；
 * - 相册/系统选择器给出的是外部 content URI，先经 batch staging 有界拷贝到
 *   应用私有目录并换发 FileProvider URI，再进 vault（字节与图片链同源校验）。
 */
internal class RoomLobbyMessageImageIntake(
    context: Context,
    private val database: StudyDatabasePort,
) : LobbyMessageImageIntake {
    private val appContext = context.applicationContext
    private val vault = AndroidCanonicalAssetVault(appContext)
    private val staging = AndroidBatchImportSourceStaging(appContext)

    override suspend fun registerImage(
        localUri: String,
        occurredAtEpochMillis: Long,
    ): LobbyMessageImage = withContext(Dispatchers.IO) {
        val importableUri = if (isVaultImportable(localUri)) {
            localUri
        } else {
            stagedUri(localUri)
        }
        val record = vault.import(
            localUri = importableUri,
            sourceType = StudyDbValue.SourceAssetType.PHOTO_PICKER,
            createdAtEpochMillis = occurredAtEpochMillis,
        )
        database.registerCanonicalSourceAsset(record)
        LobbyMessageImage(
            assetId = record.sourceAssetId,
            sha256 = record.contentSha256,
            byteSize = record.byteSize,
            width = record.width,
            height = record.height,
        )
    }

    override suspend fun resolveImageUri(assetId: String): String? = withContext(Dispatchers.IO) {
        val record = database.readCanonicalSourceAsset(assetId) ?: return@withContext null
        val file = runCatching { vault.resolve(record) }.getOrNull() ?: return@withContext null
        if (file.isFile) "file://${file.absolutePath}" else null
    }

    override suspend fun describeImage(assetId: String): LobbyMessageImage? =
        withContext(Dispatchers.IO) {
            val record = database.readCanonicalSourceAsset(assetId) ?: return@withContext null
            LobbyMessageImage(
                assetId = record.sourceAssetId,
                sha256 = record.contentSha256,
                byteSize = record.byteSize,
                width = record.width,
                height = record.height,
            )
        }

    /**
     * 把已登记资产交给录入链路（A4 的 ③）：规范资产库里的文件 → 本应用私有 provider 的
     * `content://` URI。
     *
     * 为什么要有这一步：vault 的目录**不在任何 FileProvider 的 paths 配置里**（这正是它"私有"
     * 的含义），而录入链路（批量导入的 staging、草稿导入的 vault 入口）只接受本应用 provider 的
     * content URI。于是把字节有界复制到相机/相册那条既有的暂存目录（`cache/captured_images/`，
     * capture provider 已覆盖），文件名按 sha256 取——同一张图重复执行不会堆副本。
     *
     * 资产缺失或被改动时返回 null：调用方如实说"这张图不在了"。
     */
    override suspend fun resolveIntakeUri(assetId: String): String? = withContext(Dispatchers.IO) {
        val record = database.readCanonicalSourceAsset(assetId) ?: return@withContext null
        val source = runCatching { vault.resolve(record) }.getOrNull() ?: return@withContext null
        val directory = File(appContext.cacheDir, INTAKE_STAGING_DIRECTORY).apply {
            if (!isDirectory) mkdirs()
        }
        val extension = record.relativePath.substringAfterLast('.', "jpg")
        val destination = File(directory, "lobby-intake-${record.contentSha256}.$extension")
        if (!destination.isFile || destination.length() != record.byteSize) {
            runCatching {
                source.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }.getOrElse {
                destination.delete()
                return@withContext null
            }
        }
        if (destination.length() != record.byteSize) {
            destination.delete()
            return@withContext null
        }
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.capture.fileprovider",
            destination,
        ).toString()
    }

    private fun isVaultImportable(localUri: String): Boolean {
        val uri = Uri.parse(localUri)
        return uri.scheme == "content" &&
            (
                uri.authority == "${appContext.packageName}.capture.fileprovider" ||
                    uri.authority == batchImportProviderAuthority(appContext)
                )
    }

    private fun stagedUri(localUri: String): String {
        val staged = staging.stage(listOf(localUri))
        return staged.sourceUris.singleOrNull()
            ?: throw IllegalArgumentException("Lobby image could not be staged")
    }
}

object LobbyMessageImageIntakeFactory {
    fun create(context: Context, database: StudyDatabasePort): LobbyMessageImageIntake =
        RoomLobbyMessageImageIntake(context, database)
}
