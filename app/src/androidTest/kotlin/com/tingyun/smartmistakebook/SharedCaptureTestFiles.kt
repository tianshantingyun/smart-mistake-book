package com.tingyun.smartmistakebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A7/A6 仪器化用例的共享夹具：在 `cache/captured_images/` 里造文件，再经 App 的
 * `capture.fileprovider` 出 `content://` URI——与系统分享进入 App 的形态一致
 * （scheme / MIME / 大小都由真实的 ContentResolver 回答，不是假的 map）。
 */
internal object SharedCaptureTestFiles {
    private val createdFiles = mutableListOf<File>()

    fun fileUri(context: Context, name: String, write: (File) -> Unit): Uri {
        val directory = File(context.cacheDir, "captured_images").apply {
            check(isDirectory || mkdirs() || isDirectory) {
                "captured_images directory is unavailable"
            }
        }
        val file = File(directory, "share-${System.nanoTime()}-$name").canonicalFile
        check(file.parentFile == directory.canonicalFile)
        write(file)
        createdFiles += file
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.capture.fileprovider",
            file,
        )
    }

    /** 用例收尾：删掉本类造出的分享源文件（App 自己的预拷件由被测代码/缓存 TTL 负责）。 */
    fun cleanup() {
        createdFiles.forEach { file -> runCatching { file.delete() } }
        createdFiles.clear()
    }

    fun pngFile(context: Context, name: String = "page.png"): Uri = fileUri(context, name) { file ->
        file.writeBytes(pngBytes())
    }

    fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun pdfBytes(pageCount: Int): ByteArray {
        val document = PdfDocument()
        return try {
            ByteArrayOutputStream().use { output ->
                repeat(pageCount) { pageIndex ->
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create(),
                    )
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawText(
                        "Shared page ${pageIndex + 1}",
                        48f,
                        96f,
                        Paint().apply {
                            color = Color.BLACK
                            textSize = 28f
                        },
                    )
                    document.finishPage(page)
                }
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }
}
