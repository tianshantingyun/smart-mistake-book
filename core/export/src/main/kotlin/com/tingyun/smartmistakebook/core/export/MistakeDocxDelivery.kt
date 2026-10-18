package com.tingyun.smartmistakebook.core.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class MistakeDocxSaveResult {
    SAVED,
    INVALID_PREPARED_FILE,
    DESTINATION_UNAVAILABLE,
    DESTINATION_INTEGRITY_MISMATCH,
}

/**
 * 交付链的 Word 半边（6B-1）：与 [MistakePdfDocumentSaver] / [MistakePdfDeliveryIntents] /
 * [MistakePdfShareUris] **同构**——SAF 保存（复制后回读核对摘要）、ACTION_SEND 分享、
 * FileProvider 授权 URI。
 *
 * 为什么不把 PDF 的三件套参数化复用：三者的完整性口径不同（文件名、摘要文件、目录来源各
 * 一），混在一处会让"哪份产物允许被分享"这条安全判据随参数漂移；同构实现让两条链各自
 * fail-closed。两条链唯一共享的是 `MistakePdfExportLimits` 的字节预算常数。
 */
object MistakeDocxDocumentSaver {
    fun save(
        context: Context,
        prepared: PreparedMistakeDocx,
        destination: Uri,
    ): MistakeDocxSaveResult {
        if (!prepared.verifyIntegrity()) return MistakeDocxSaveResult.INVALID_PREPARED_FILE
        if (destination.scheme != "content") {
            return MistakeDocxSaveResult.DESTINATION_UNAVAILABLE
        }
        if (destination.authority == ownProviderAuthority(context)) {
            return MistakeDocxSaveResult.DESTINATION_UNAVAILABLE
        }
        return try {
            val resolver = context.applicationContext.contentResolver
            val destinationDescriptor = resolver.openFileDescriptor(destination, "rwt")
                ?: return MistakeDocxSaveResult.DESTINATION_UNAVAILABLE
            BufferedInputStream(FileInputStream(prepared.file)).use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(destinationDescriptor).use { output ->
                    copyWithinBudget(input, output)
                    output.flush()
                    output.fd.sync()
                }
            }
            val copiedDigest = resolver.openInputStream(destination)?.use(::digestWithinBudget)
                ?: return MistakeDocxSaveResult.DESTINATION_UNAVAILABLE
            if (copiedDigest == prepared.sha256) {
                MistakeDocxSaveResult.SAVED
            } else {
                MistakeDocxSaveResult.DESTINATION_INTEGRITY_MISMATCH
            }
        } catch (_: Exception) {
            MistakeDocxSaveResult.DESTINATION_UNAVAILABLE
        }
    }

    private fun ownProviderAuthority(context: Context): String =
        "${context.packageName}.${MistakeDocxDeliveryIntents.FILE_PROVIDER_AUTHORITY_SUFFIX}"

    private fun copyWithinBudget(input: InputStream, output: FileOutputStream) {
        var copied = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            require(copied <= MistakePdfExportLimits.MAX_PDF_BYTES) {
                "Word export exceeds copy budget"
            }
            output.write(buffer, 0, read)
        }
        require(copied > 0L) { "Word export copy is empty" }
    }

    private fun digestWithinBudget(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var readBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            readBytes += read
            require(readBytes <= MistakePdfExportLimits.MAX_PDF_BYTES) {
                "Saved Word export exceeds verification budget"
            }
            digest.update(buffer, 0, read)
        }
        require(readBytes > 0L) { "Saved Word export is empty" }
        return digest.digest().toHex()
    }
}

object MistakeDocxDeliveryIntents {
    /** OOXML（WordprocessingML）文档的官方 MIME 类型。 */
    const val MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** 本模块第二个 FileProvider 的 authority 后缀（见 AndroidManifest 的 mistake-docx-exports）。 */
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = "mistake-docx-exports"

    fun createDocument(displayName: String): Intent {
        require(displayName.isNotBlank()) { "Word display name must not be blank" }
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = MIME_TYPE
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, displayName)
        }
    }

    fun share(
        context: Context,
        prepared: PreparedMistakeDocx,
        displayName: String,
    ): Intent {
        require(displayName.isNotBlank()) { "Word display name must not be blank" }
        val uri = MistakeDocxShareUris.uriFor(context, prepared)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            clipData = ClipData.newUri(context.contentResolver, displayName, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}

object MistakeDocxShareUris {
    fun uriFor(context: Context, prepared: PreparedMistakeDocx): Uri {
        require(prepared.verifyIntegrity()) { "Prepared Word artifact integrity check failed" }
        val allowedDirectory = File(
            context.applicationContext.cacheDir,
            MistakeDocxExporter.EXPORT_DIRECTORY,
        ).canonicalFile
        val preparedFile = prepared.file.canonicalFile
        val contentDirectory = preparedFile.parentFile
        require(
            contentDirectory?.parentFile == allowedDirectory &&
                contentDirectory.name == prepared.inputSha256 &&
                preparedFile.name == PREPARED_DOCX_FILE_NAME,
        ) {
            "Prepared Word artifact is outside the share directory"
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.${MistakeDocxDeliveryIntents.FILE_PROVIDER_AUTHORITY_SUFFIX}",
            preparedFile,
        )
    }
}
