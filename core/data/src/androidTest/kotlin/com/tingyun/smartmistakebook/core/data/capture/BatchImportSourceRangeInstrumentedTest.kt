package com.tingyun.smartmistakebook.core.data.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.BatchImportPageStatus
import com.tingyun.smartmistakebook.core.domain.BatchImportStatus
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import com.tingyun.smartmistakebook.core.domain.CreatePdfImportRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A6（4C）：既有 PDF/图片整卷导入链的**类型范围与失败恢复核验**（补用例，不重建）。
 *
 * 这里刻意只补既有 `BatchImportRepositoryInstrumentedTest` 没钉住的三条事实：
 * 1. 图片通道收到 PDF 字节：该页失败、其余页照常入库（"某一页失败不影响其他页"的承诺），
 *    且失败页没有草稿；失败码按**当前**映射如实断言（见测试内注释，不按期望值造事实）。
 * 2. PDF 通道收到 1 页 PDF：低于既有 2~30 页范围，直接拒绝，不落 job、不留私有 staging。
 * 3. PDF 通道收到非 PDF 字节：解析器拒绝，不落 job、不留私有 staging。
 *
 * 既有用例已覆盖：超大图片（20 MB）、31 页 PDF、有效 PDF 逐页渲染、失败页重试。
 * 因此本类不重复它们，也不改生产实现。
 */
@RunWith(AndroidJUnit4::class)
class BatchImportSourceRangeInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: StudyDatabasePort
    private lateinit var databaseName: String
    private val temporaryFiles = mutableListOf<File>()
    private val processingScopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "batch-source-range-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        clearDirectory(stagingDirectory())
        clearDirectory(File(context.filesDir, "source-assets"))
        database = StudyDatabaseFactory.open(context, databaseName)
    }

    @After
    fun tearDown() {
        processingScopes.forEach { scope -> scope.cancel() }
        runCatching { database.close() }
        context.deleteDatabase(databaseName)
        temporaryFiles.forEach { file -> runCatching { file.delete() } }
        clearDirectory(stagingDirectory())
        clearDirectory(File(context.filesDir, "source-assets"))
    }

    @Test
    fun imagePageRejectsPdfBytesWhileTheOtherPageStillImports() = runBlocking {
        val validImage = fileUri("range-valid.png") { file ->
            file.outputStream().use { output -> writePng(output) }
        }
        val pdfAsImage = fileUri("range-wrong.png") { file ->
            file.outputStream().use { output -> output.write(createPdfBytes(pageCount = 2)) }
        }
        val repository = newRepository()
        val created = repository.createBatchImport(
            CreateBatchImportRequest(
                requestId = "range-image-pdf-bytes",
                localUris = listOf(validImage.toString(), pdfAsImage.toString()),
                occurredAtEpochMillis = 1_000,
            ),
        )
        val completed = withTimeout(20_000) {
            repository.observeBatchImports().first { jobs ->
                jobs.firstOrNull()?.let { job ->
                    job.jobId == created.jobId && job.status == BatchImportStatus.COMPLETED
                } == true
            }.first()
        }

        assertEquals(1, completed.readyCount)
        assertEquals(1, completed.failedCount)
        val failed = completed.pages.single { it.status == BatchImportPageStatus.FAILED }
        // 当前映射（RoomBatchImportRepository.toBatchFailureCode）：解码失败是
        // IllegalStateException，不是 SecurityException/IllegalArgumentException，因此落
        // IMPORT_FAILED 而不是 SOURCE_NOT_READABLE。这里断言真实行为；若要改成更精确的
        // 失败码，那是另一条实现变更，不在 A6"核验"范围内。
        assertEquals("IMPORT_FAILED", failed.failureCode)
        assertNull(failed.draftId)
        val ready = completed.pages.single { it.status == BatchImportPageStatus.READY }
        assertTrue(ready.draftId != null)
        // 失败不会拖垮成功页：成功页的草稿在待处理里可见；失败页的源仍在 staging 里等重试/跳过。
        assertTrue(
            captureRepository.observePendingCaptures().first().any { it.draftId == ready.draftId },
        )
        assertEquals(1, stagingFiles().size)
    }

    @Test
    fun onePagePdfIsRejectedBeforeAnyJobOrPrivateStagingExists() = runBlocking {
        val source = fileUri("range-one-page.pdf") { file ->
            file.outputStream().use { output -> output.write(createPdfBytes(pageCount = 1)) }
        }
        val repository = newRepository()

        val failure = runCatching {
            repository.createPdfImport(
                CreatePdfImportRequest(
                    requestId = "range-one-page-pdf",
                    localUri = source.toString(),
                    occurredAtEpochMillis = 2_000,
                ),
            )
        }.exceptionOrNull()

        assertTrue("expected the page-range gate, was $failure", failure is IllegalArgumentException)
        assertTrue(repository.observeBatchImports().first().isEmpty())
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun nonPdfBytesAreRejectedByThePdfChainWithoutPrivateStaging() = runBlocking {
        val source = fileUri("range-fake.pdf") { file ->
            file.writeText("not-a-pdf")
        }
        val repository = newRepository()

        val failure = runCatching {
            repository.createPdfImport(
                CreatePdfImportRequest(
                    requestId = "range-fake-pdf",
                    localUri = source.toString(),
                    occurredAtEpochMillis = 3_000,
                ),
            )
        }.exceptionOrNull()

        // PdfRenderer 对无效输入抛 IOException（"not a valid PDF"）；某些平台/版本也可能在
        // 页码校验处抛 IllegalArgumentException。两者都是"解析器拒绝"，这里断言拒绝本身。
        assertTrue(
            "PdfRenderer should refuse non-PDF bytes, was $failure",
            failure is IOException || failure is IllegalArgumentException,
        )
        assertTrue(repository.observeBatchImports().first().isEmpty())
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun thirtyOneImagesAreRejectedAtTheBatchRangeBoundaryWithoutStaging() = runBlocking {
        val sources = (0..30).map { index ->
            fileUri("range-$index.png") { file ->
                file.outputStream().use { output -> writePng(output) }
            }
        }
        val repository = newRepository()

        val failure = runCatching {
            repository.createBatchImport(
                CreateBatchImportRequest(
                    requestId = "range-thirty-one",
                    localUris = sources.map(Uri::toString),
                    occurredAtEpochMillis = 4_000,
                ),
            )
        }.exceptionOrNull()

        assertTrue("expected the 30-file boundary, was $failure", failure is IllegalArgumentException)
        assertTrue(repository.observeBatchImports().first().isEmpty())
        assertTrue(stagingFiles().isEmpty())
    }

    private val captureRepository: RoomCaptureWorkflowRepository
        get() = RoomCaptureWorkflowRepository(
            database,
            AndroidCanonicalAssetVault(context),
            LocalQuestionTextRecognizer { _, _, _ ->
                LocalTextRecognition(emptyList(), "fixture-no-text-v1")
            },
        )

    private fun newRepository(): RoomBatchImportRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also {
            processingScopes += it
        }
        return RoomBatchImportRepository(
            database = database,
            capture = captureRepository,
            processingScope = scope,
            sourceStaging = AndroidBatchImportSourceStaging(context),
        )
    }

    /** 与既有用例同一条测试来源：`cache/captured_images/` 经测试 manifest 里的 FileProvider 出 URI。 */
    private fun fileUri(name: String, write: (File) -> Unit): Uri {
        val directory = File(context.cacheDir, "captured_images").apply {
            check(isDirectory || mkdirs() || isDirectory)
        }
        val file = File(directory, "range-${System.nanoTime()}-$name").canonicalFile
        check(file.parentFile == directory.canonicalFile)
        write(file)
        temporaryFiles += file
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.capture.fileprovider",
            file,
        )
    }

    private fun writePng(output: OutputStream) {
        val bitmap = Bitmap.createBitmap(72, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            bitmap.recycle()
        }
    }

    private fun createPdfBytes(pageCount: Int): ByteArray {
        val document = PdfDocument()
        val output = ByteArrayOutputStream()
        try {
            repeat(pageCount) { pageIndex ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "Range page ${pageIndex + 1}",
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
        } finally {
            document.close()
        }
        return output.toByteArray()
    }

    private fun stagingDirectory(): File = File(context.filesDir, BATCH_IMPORT_STAGING_DIRECTORY)

    private fun stagingFiles(): List<File> = stagingDirectory()
        .listFiles()
        .orEmpty()
        .flatMap { session -> session.listFiles().orEmpty().asList() }
        .filter(File::isFile)

    private fun clearDirectory(directory: File) {
        directory.listFiles().orEmpty().forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }
}
