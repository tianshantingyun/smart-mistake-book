package com.tingyun.smartmistakebook.diagnostics

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/** 诊断包头部的版本三元组（读不到就 null，不假造——同 AndroidBackupRepository 纪律）。 */
data class DiagnosticsBundleMeta(
    val versionName: String?,
    val versionCode: Long?,
    val flavor: String?,
    val generatedAtEpochMillis: Long,
)

/**
 * 诊断包组装与导出（T6）：
 * - [bundleText] 纯文本报告：头部键值 + 每事件一行 JSON。选纯文本而非 zip：零额外编码层，
 *   反馈时肉眼可读，打包最近崩溃/ANR/版本信息这一需求不需要压缩。
 * - [metaFrom] 版本信息只读本地 PackageManager（API 28 分支处理 longVersionCode）。
 * - [writeAndVerify] 经 ContentResolver 写 SAF 目标后**复读校验 SHA-256**——逐字沿用
 *   MistakePdfDocumentSaver.save 的验证语义（core/export/.../MistakePdfDelivery.kt:310-344）。
 *
 * 零外发：唯一的"离开设备"路径是用户在系统文件选择器里选定的目标（SAF，本机 IPC）。
 */
object DiagnosticsBundleExporter {
    const val FORMAT_VERSION = 1

    fun bundleText(events: List<DiagnosticEvent>, meta: DiagnosticsBundleMeta): String = buildString {
        appendLine("# smart-mistake-book diagnostics bundle")
        appendLine("# format=$FORMAT_VERSION")
        // 读不到的版本字段缺行而不是假造（对齐备份包的版本戳纪律）。
        meta.versionName?.let { appendLine("# versionName=$it") }
        meta.versionCode?.let { appendLine("# versionCode=$it") }
        meta.flavor?.let { appendLine("# flavor=$it") }
        appendLine("# generatedAtEpochMillis=${meta.generatedAtEpochMillis}")
        appendLine("# entries=${events.size}")
        for (event in events) {
            appendLine(DiagnosticEventJson.encode(event))
        }
    }

    fun metaFrom(context: Context, flavor: String?): DiagnosticsBundleMeta {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionCode = packageInfo?.let {
            // API 28 起 versionCode 被 longVersionCode 取代；minSdk 23 需要分支。
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
        }
        return DiagnosticsBundleMeta(
            versionName = packageInfo?.versionName,
            versionCode = versionCode,
            flavor = flavor,
            generatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    /**
     * 写入并复读校验。返回目标文件的 SHA-256 摘要；目标不可写或复读摘要与写入内容不符
     * 时返回 null（调用方如实报"导出未完成"，不假报成功）。
     */
    fun writeAndVerify(context: Context, uri: android.net.Uri, text: String): ByteArray? {
        return try {
            val resolver = context.applicationContext.contentResolver
            val payload = text.toByteArray(Charsets.UTF_8)
            val expected = sha256(payload)
            val descriptor = resolver.openFileDescriptor(uri, "rwt") ?: return null
            BufferedInputStream(ByteArrayInputStream(payload)).use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            val stored = resolver.openInputStream(uri)?.use(::sha256) ?: return null
            if (MessageDigest.isEqual(stored, expected)) stored else null
        } catch (_: Exception) {
            null
        }
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    private const val DEFAULT_BUFFER_SIZE = 8 * 1024
}
