package com.tingyun.smartmistakebook.feature.capture

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.tingyun.smartmistakebook.core.domain.BatchImportJob
import com.tingyun.smartmistakebook.core.domain.BatchImportPage
import com.tingyun.smartmistakebook.core.domain.BatchImportPageStatus
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.BatchImportStatus
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import com.tingyun.smartmistakebook.core.domain.CreatePdfImportRequest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * KD-57 分享授权用例的共享夹具：真实 FileProvider 源文件、可记录撤销调用的 Context、
 * 以及一个"读取时抛 SecurityException"的 provider（模拟授权被收回）。
 */
internal object CaptureShareTestFiles {
    /**
     * 分享层只校验文件头，所以最小的 PNG 魔数就足够让一次单图分享走成功路径；
     * 不在这里解码图片，用例也就不依赖 Bitmap 的内存行为。
     */
    val pngBytes: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D,
    )

    fun sourceFile(context: Context, name: String): File {
        val directory = File(context.cacheDir, "captured_images").apply {
            check(isDirectory || mkdirs() || isDirectory) {
                "captured_images directory is unavailable"
            }
        }
        return File(directory, "share-${System.nanoTime()}-$name")
    }

    fun sourceUri(context: Context, file: File): String = FileProvider.getUriForFile(
        context,
        "${context.packageName}.capture.fileprovider",
        file,
    ).toString()

    /** intake 在分享缓存里落下的自有预拷件（`question_*.img`）。 */
    fun ownedStagingFiles(context: Context): Set<File> {
        val directory = File(context.cacheDir, "captured_images")
        return directory.listFiles().orEmpty()
            .filterTo(mutableSetOf()) { file ->
                file.isFile && file.name.startsWith("question_") &&
                    (file.name.endsWith(".img") || file.name.endsWith(".jpg"))
            }
    }

    fun deleteNewStagingFiles(context: Context, baseline: Set<File>) {
        (ownedStagingFiles(context) - baseline).forEach { file ->
            runCatching { file.delete() }
        }
    }
}

/** 记录"撤销读授权"的调用；其余行为全部委托给真实 Context。 */
internal class RecordingRevokeContext(base: Context) : ContextWrapper(base) {
    val revokedUris = mutableListOf<String>()

    // 接收页用的是 context.applicationContext；测试里必须让这条链仍指向记录器。
    override fun getApplicationContext(): Context = this

    override fun revokeUriPermission(uri: Uri, modeFlags: Int) {
        revokedUris += uri.toString()
        super.revokeUriPermission(uri, modeFlags)
    }
}

/**
 * 模拟"授权已经不在"的 provider：类型仍然能解析成图片，但真正打开文件时抛
 * SecurityException——与进程重建后 URI 还在、读授权已随进程消失的形态一致。
 */
internal class DeniedReadProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        throw SecurityException("read grant is no longer held")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

internal fun deniedReadContext(base: Context): Context {
    val provider = DeniedReadProvider()
    provider.attachInfo(
        base,
        ProviderInfo().apply {
            authority = DENIED_READ_AUTHORITY
            exported = true
            applicationInfo = base.applicationInfo
        },
    )
    val resolver = ContentResolver.wrap(provider)
    return object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getContentResolver(): ContentResolver = resolver
    }
}

internal const val DENIED_READ_AUTHORITY = "com.tingyun.smartmistakebook.test.denied-read"

internal fun deniedReadUri(index: Int = 1): String =
    "content://$DENIED_READ_AUTHORITY/share/$index"

internal fun shareRequest(
    uris: List<String>,
    action: String = Intent.ACTION_SEND,
    mimeType: String? = "image/png",
    requestId: Long = 1L,
): CaptureShareRequest = CaptureShareRequest(
    requestId = requestId,
    action = action,
    mimeType = mimeType,
    uris = uris,
)

/** 只记录"把什么交给批量管线"；`failOnBatch` 用来精确制造 IMPORT_FAILED。 */
internal class CaptureShareTestBatchImports(
    private val failOnBatch: Boolean = false,
) : BatchImportRepository {
    val batchRequests = mutableListOf<List<String>>()

    override fun observeBatchImports(): Flow<List<BatchImportJob>> = flowOf(emptyList())

    override suspend fun hasInterruptedBatchImport(): Boolean = false

    override suspend fun createBatchImport(request: CreateBatchImportRequest): BatchImportJob {
        if (failOnBatch) error("batch staging failed")
        batchRequests += request.localUris
        return job("fake-batch", request.localUris.size)
    }

    override suspend fun createPdfImport(request: CreatePdfImportRequest): BatchImportJob =
        job("fake-pdf", pageCount = 2)

    override suspend fun pauseBatchImport(jobId: String) = Unit

    override suspend fun resumeBatchImport(jobId: String) = Unit

    override suspend fun retryBatchImportPage(jobId: String, pageIndex: Int) = Unit

    override suspend fun skipBatchImportPage(jobId: String, pageIndex: Int) = Unit

    override suspend fun organizeBatch(jobId: String) = Unit

    override suspend fun drivePendingImports(): Boolean = true

    private fun job(jobId: String, pageCount: Int): BatchImportJob {
        val safePageCount = pageCount.coerceAtLeast(2)
        return BatchImportJob(
            jobId = jobId,
            status = BatchImportStatus.COMPLETED,
            pages = (0 until safePageCount).map { pageIndex ->
                BatchImportPage(
                    pageIndex = pageIndex,
                    status = BatchImportPageStatus.SKIPPED,
                    draftId = null,
                    failureCode = null,
                    attemptCount = 0,
                    updatedAtEpochMillis = 1_000L,
                )
            },
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }
}
