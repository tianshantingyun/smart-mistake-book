package com.tingyun.smartmistakebook.feature.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import com.tingyun.smartmistakebook.core.domain.CreatePdfImportRequest
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * A7（4C）：系统分享内容的接收与落点。
 *
 * 职责只有三件：按 [planCaptureShare] 的门校验、把通过门的字节落到**自有私有缓存**
 * （单图）；把多图/PDF 交给既有整卷管线（`BatchImportRepository`），不另建导入链。
 *
 * 为什么单图要先落自有缓存、多图先预拷、PDF 不预拷：
 * - 单图要交给录入页的既有接收路径（`CaptureReturnedImagePlan.ApplyAsNew`），它只认
 *   `capture.fileprovider` 的 `question_*.img`，所以分享进来的任何 `content://` 都必须先
 *   拷成自有文件；有界拷贝同时是**真正的字节门**（`OpenableColumns.SIZE` 只用于早拒绝）。
 * - 多图也先经同一道门预拷：一张坏图在**接收页**就被点名拒绝，而不是变成整卷任务里
 *   一个语焉不详的失败页。预拷件交给批量仓库后立即删除——批量仓库在
 *   `createBatchImport` 内已完成自己的有界 staging（两侧都校验，不是二选一）。
 * - PDF 不预拷：文件头检查（`%PDF-`）是提示性的，真正的解析与 128 MB / 2~30 页门在
 *   `createPdfImport` 的私有 staging + `PdfRenderer` 里；预拷只会让同样的字节多落一次盘。
 *
 * 所有失败都返回带原因的 [CaptureShareResult.Rejected]：入口会把原因如实显示出来，
 * 不把"这份文件没读出来"和"这次没保存成功"混成一句泛化错误。
 */
class CaptureShareIntake(
    private val context: Context,
    private val batchImports: BatchImportRepository,
) {
    suspend fun intake(request: CaptureShareRequest): CaptureShareResult =
        withContext(Dispatchers.IO) {
            try {
                when (
                    val plan = planCaptureShare(
                        rawUris = request.uris,
                        kindOf = { uri -> captureShareKindFor(uri, request.mimeType) },
                        sizeOf = ::sourceByteSize,
                    )
                ) {
                    is CaptureSharePlan.Rejected -> rejected(plan.reason)
                    is CaptureSharePlan.Pdf -> importPdf(plan.uri)
                    is CaptureSharePlan.Images -> importImages(plan.uris)
                }
            } finally {
                request.uris.forEach(::revokeReadGrant)
            }
        }

    /**
     * 多张图片 → 既有整卷管线。预拷到自有缓存后把自有 URI 交给批量仓库（它要求 content
     * scheme，`capture.fileprovider` 满足），并在调用返回后立刻删掉预拷件——批量仓库在
     * 返回前已完成自己的有界 staging，后续逐页导入读的是它自己的副本。
     */
    private suspend fun importImages(uris: List<String>): CaptureShareResult {
        val staged = ArrayList<String>(uris.size)
        var handedOffUri: String? = null
        // 声明的 SIZE 可能缺失或撒谎；这里用**实际拷贝字节数**再守一次总量门
        // （MAX_SHARE_TOTAL_BYTES），避免 30 个"每张都不超 20 MB"的文件把缓存塞满。
        var stagedBytes = 0L
        try {
            uris.forEach { uri ->
                when (val result = stageSharedImage(uri)) {
                    is SharedImageStage.Staged -> {
                        // 先记进 staged 再判总量：超限时 finally 会连当前这一张一起删掉。
                        staged += result.ownedUri
                        stagedBytes += result.byteSize
                        if (stagedBytes > MAX_SHARE_TOTAL_BYTES) {
                            return rejected(CaptureShareRejection.FILE_TOO_LARGE)
                        }
                    }
                    is SharedImageStage.Rejected -> return rejected(result.reason)
                }
            }
            if (staged.size == 1) {
                // 交接前再看一次取消：被取消的接收不能把一张没人认领的预拷图留在缓存里。
                currentCoroutineContext().ensureActive()
                handedOffUri = staged.single()
                return CaptureShareResult.SingleImage(handedOffUri)
            }
            val job = try {
                batchImports.createBatchImport(
                    CreateBatchImportRequest(
                        requestId = "capture-share:${UUID.randomUUID()}",
                        localUris = staged,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return rejected(CaptureShareRejection.IMPORT_FAILED)
            }
            return CaptureShareResult.ImageBatch(job.jobId)
        } finally {
            staged
                .filter { it != handedOffUri }
                .forEach { deleteOwnedCapture(context, it) }
        }
    }

    /**
     * 一份 PDF → 既有 `createPdfImport`（私有 staging + 逐页渲染 + 2~30 页门都在那边）。
     * 这里只补两件分享侧的事：`%PDF-` 文件头（把"明显不是 PDF"尽早说清楚）与失败原因。
     * 文件头校验是**提示性**的，真正的解析权威仍是 `PdfRenderer`。
     */
    private suspend fun importPdf(uri: String): CaptureShareResult {
        when (readPdfHeader(uri)) {
            PdfHeader.UNREADABLE -> return rejected(CaptureShareRejection.UNREADABLE)
            PdfHeader.NOT_A_PDF -> return rejected(CaptureShareRejection.NOT_A_PDF)
            PdfHeader.PDF -> Unit
        }
        return try {
            val job = batchImports.createPdfImport(
                CreatePdfImportRequest(
                    requestId = "capture-share-pdf:${UUID.randomUUID()}",
                    localUri = uri,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            CaptureShareResult.Pdf(job.jobId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            rejected(CaptureShareRejection.IMPORT_FAILED)
        }
    }

    private fun captureShareKindFor(uri: String, declaredMimeType: String?): CaptureShareKind? {
        val resolved = runCatching {
            context.contentResolver.getType(Uri.parse(uri))
        }.getOrNull()
        // 解析类型优先；部分 provider 对任何文件都报 application/octet-stream，此时退到
        // 发送方声明的类型，再由图片魔数 / PDF 文件头做二次确认。
        return captureShareKindOf(resolved) ?: captureShareKindOf(declaredMimeType)
    }

    private fun sourceByteSize(uri: String): Long? = runCatching {
        context.contentResolver
            .query(Uri.parse(uri), arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }.getOrNull()?.takeIf { size -> size >= 0L }

    private fun stageSharedImage(sourceUri: String): SharedImageStage {
        val directory = File(context.cacheDir, CAPTURE_IMAGE_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            return SharedImageStage.Rejected(CaptureShareRejection.IMPORT_FAILED)
        }
        val target = runCatching {
            File.createTempFile("question_", ".img", directory)
        }.getOrNull() ?: return SharedImageStage.Rejected(CaptureShareRejection.IMPORT_FAILED)
        val stream = try {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))
        } catch (_: Exception) {
            null
        }
        if (stream == null) {
            target.delete()
            return SharedImageStage.Rejected(CaptureShareRejection.UNREADABLE)
        }
        val copyResult = stream.use { input -> copySharedImageStream(input, target) }
        if (copyResult is ImageCopyResult.Rejected) {
            target.delete()
            return SharedImageStage.Rejected(copyResult.reason)
        }
        val copiedBytes = (copyResult as ImageCopyResult.Copied).byteSize
        val ownedUri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.capture.fileprovider",
                target,
            )
        }.getOrNull()
        if (ownedUri == null) {
            target.delete()
            return SharedImageStage.Rejected(CaptureShareRejection.IMPORT_FAILED)
        }
        return SharedImageStage.Staged(ownedUri.toString(), copiedBytes)
    }

    /**
     * 分享单图的**强制**字节门与类型门：流内计数不超过 [MAX_SHARE_IMAGE_BYTES]，
     * 且落盘后的文件头必须是支持的图片格式。失败时目标文件由调用方删除。
     */
    private fun copySharedImageStream(
        input: InputStream,
        destination: File,
    ): ImageCopyResult {
        var totalBytes = 0L
        var consecutiveEmptyReads = 0
        val copied = runCatching {
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) {
                        consecutiveEmptyReads += 1
                        if (consecutiveEmptyReads > MAX_EMPTY_READS) {
                            return ImageCopyResult.Rejected(CaptureShareRejection.UNREADABLE)
                        }
                        continue
                    }
                    consecutiveEmptyReads = 0
                    totalBytes += read
                    if (totalBytes > MAX_SHARE_IMAGE_BYTES) {
                        return ImageCopyResult.Rejected(CaptureShareRejection.FILE_TOO_LARGE)
                    }
                    output.write(buffer, 0, read)
                }
            }
            true
        }.getOrDefault(false)
        if (!copied || totalBytes <= 0L) {
            return ImageCopyResult.Rejected(CaptureShareRejection.UNREADABLE)
        }
        if (!hasSupportedImageSignature(destination)) {
            return ImageCopyResult.Rejected(CaptureShareRejection.NOT_AN_IMAGE)
        }
        return ImageCopyResult.Copied(totalBytes)
    }

    private fun readPdfHeader(uri: String): PdfHeader {
        val stream = try {
            context.contentResolver.openInputStream(Uri.parse(uri))
        } catch (_: Exception) {
            null
        } ?: return PdfHeader.UNREADABLE
        return try {
            stream.use { input ->
                val header = ByteArray(PDF_HEADER_LENGTH)
                var read = 0
                var emptyReads = 0
                while (read < PDF_HEADER_LENGTH) {
                    val count = input.read(header, read, PDF_HEADER_LENGTH - read)
                    if (count < 0) break
                    if (count == 0) {
                        emptyReads += 1
                        if (emptyReads > MAX_EMPTY_READS) return PdfHeader.UNREADABLE
                        continue
                    }
                    emptyReads = 0
                    read += count
                }
                if (
                    read == PDF_HEADER_LENGTH &&
                    String(header, 0, PDF_HEADER_LENGTH, Charsets.US_ASCII) == PDF_HEADER
                ) {
                    PdfHeader.PDF
                } else {
                    PdfHeader.NOT_A_PDF
                }
            }
        } catch (_: Exception) {
            PdfHeader.UNREADABLE
        }
    }

    private fun revokeReadGrant(uri: String) {
        runCatching {
            context.revokeUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun rejected(reason: CaptureShareRejection): CaptureShareResult.Rejected =
        CaptureShareResult.Rejected(
            reason = reason,
            message = captureShareRejectionMessage(reason),
        )

    private sealed interface SharedImageStage {
        data class Staged(val ownedUri: String, val byteSize: Long) : SharedImageStage

        data class Rejected(val reason: CaptureShareRejection) : SharedImageStage
    }

    private sealed interface ImageCopyResult {
        data class Copied(val byteSize: Long) : ImageCopyResult

        data class Rejected(val reason: CaptureShareRejection) : ImageCopyResult
    }

    private enum class PdfHeader {
        PDF,
        NOT_A_PDF,
        UNREADABLE,
    }

    private companion object {
        const val PDF_HEADER = "%PDF-"
        const val PDF_HEADER_LENGTH = 5
        const val MAX_EMPTY_READS = 16
    }
}
