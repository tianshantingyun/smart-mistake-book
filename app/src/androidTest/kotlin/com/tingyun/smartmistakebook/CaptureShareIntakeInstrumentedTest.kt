package com.tingyun.smartmistakebook

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.BatchImportJob
import com.tingyun.smartmistakebook.core.domain.BatchImportPage
import com.tingyun.smartmistakebook.core.domain.BatchImportPageStatus
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.BatchImportStatus
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import com.tingyun.smartmistakebook.core.domain.CreatePdfImportRequest
import com.tingyun.smartmistakebook.feature.capture.CaptureShareIntake
import com.tingyun.smartmistakebook.feature.capture.CaptureShareRejection
import com.tingyun.smartmistakebook.feature.capture.CaptureShareRequest
import com.tingyun.smartmistakebook.feature.capture.CaptureShareResult
import com.tingyun.smartmistakebook.feature.capture.MAX_SHARE_IMAGE_BYTES
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A7（4C）分享接收的字节层证据（不经 UI）：自有缓存落盘、失败清理、多图交给既有批量管线、
 * PDF 分支的前置文件头门。
 *
 * 批量仓库在这里是**记录型替身**：本类只钉分享层"把什么交出去、留下什么"，真实管线
 * 由 `SharedCaptureEntryInstrumentedTest` 与 A6 的整卷用例覆盖。
 */
@RunWith(AndroidJUnit4::class)
class CaptureShareIntakeInstrumentedTest {
    private lateinit var context: Context
    private lateinit var batchImports: RecordingBatchImportRepository
    private var baselineStagingNames: Set<String> = emptySet()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        batchImports = RecordingBatchImportRepository()
        baselineStagingNames = stagingFiles().map(File::getName).toSet()
    }

    @After
    fun tearDown() {
        SharedCaptureTestFiles.cleanup()
        // 只删本用例新增的分享预拷件；App 既有的拍照缓存不能因测试被清掉。
        stagingFiles()
            .filter { file -> file.name !in baselineStagingNames }
            .forEach { file -> runCatching { file.delete() } }
    }

    @Test
    fun theManifestRegistersTheAppForSingleAndMultipleImageAndPdfShares() {
        val intents = listOf(
            Intent(Intent.ACTION_SEND).setType("image/*"),
            Intent(Intent.ACTION_SEND).setType("application/pdf"),
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*"),
        )
        intents.forEach { intent ->
            val handlers = context.packageManager.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )
            assertTrue(
                "manifest should register $intent as a share target",
                handlers.any { it.activityInfo.packageName == context.packageName },
            )
        }
    }

    @Test
    fun aSharedImageIsStagedIntoTheOwnedCaptureCache() = runBlocking<Unit> {
        val source = SharedCaptureTestFiles.pngFile(context, "single.png")
        val before = stagingFiles().map(File::getName).toSet()
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(sendRequest(source, "image/png"))

        assertTrue("expected a single-image handoff, was $result", result is CaptureShareResult.SingleImage)
        val owned = Uri.parse((result as CaptureShareResult.SingleImage).ownedUri)
        assertEquals("${context.packageName}.capture.fileprovider", owned.authority)
        val staged = File(
            File(context.cacheDir, "captured_images"),
            owned.lastPathSegment.orEmpty(),
        )
        assertTrue(staged.isFile)
        assertTrue(staged.length() in 1..MAX_SHARE_IMAGE_BYTES)
        assertEquals(before, stagingFiles().map(File::getName).toSet() - staged.name)
        assertTrue(batchImports.batchRequests.isEmpty())
        assertTrue(batchImports.pdfRequests.isEmpty())
        staged.delete()
    }

    @Test
    fun aBadImageIsRejectedAndLeavesNoStagedBytesBehind() = runBlocking<Unit> {
        val source = SharedCaptureTestFiles.fileUri(context, "bad.png") { file ->
            file.writeText("not-an-image")
        }
        val before = stagingFiles().map(File::getName).toSet()
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(sendRequest(source, "image/png"))

        assertEquals(
            CaptureShareRejection.NOT_AN_IMAGE,
            (result as CaptureShareResult.Rejected).reason,
        )
        assertEquals(before, stagingFiles().map(File::getName).toSet())
        assertTrue(batchImports.batchRequests.isEmpty())
    }

    @Test
    fun anImageAboveTheByteBudgetIsRejectedBeforeStaging() = runBlocking<Unit> {
        val source = SharedCaptureTestFiles.fileUri(context, "huge.jpg") { file ->
            file.outputStream().buffered().use { output ->
                val chunk = ByteArray(1 shl 20)
                var written = 0L
                while (written < MAX_SHARE_IMAGE_BYTES) {
                    output.write(chunk)
                    written += chunk.size
                }
                output.write(0)
            }
        }
        val before = stagingFiles().map(File::getName).toSet()
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(sendRequest(source, "image/jpeg"))

        assertEquals(
            CaptureShareRejection.FILE_TOO_LARGE,
            (result as CaptureShareResult.Rejected).reason,
        )
        assertEquals(before, stagingFiles().map(File::getName).toSet())
    }

    @Test
    fun anUnreadableUriIsRejectedWithoutThrowing() = runBlocking<Unit> {
        val directory = File(context.cacheDir, "captured_images").apply {
            check(isDirectory || mkdirs() || isDirectory)
        }
        val missing = File(directory, "share-missing-${System.nanoTime()}.png")
        val missingUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.capture.fileprovider",
            missing,
        )
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(sendRequest(missingUri, "image/png"))

        assertEquals(
            CaptureShareRejection.UNREADABLE,
            (result as CaptureShareResult.Rejected).reason,
        )
        assertFalse(missing.exists())
    }

    @Test
    fun severalImagesAreHandedToTheExistingBatchPipelineAndPreCopiesAreCleaned() = runBlocking<Unit> {
        val sources = listOf(
            SharedCaptureTestFiles.pngFile(context, "a.png"),
            SharedCaptureTestFiles.pngFile(context, "b.png"),
        )
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(
            CaptureShareRequest(
                requestId = 1L,
                action = Intent.ACTION_SEND_MULTIPLE,
                mimeType = "image/*",
                uris = sources.map(Uri::toString),
            ),
        )

        assertTrue(result is CaptureShareResult.ImageBatch)
        assertEquals(1, batchImports.batchRequests.size)
        val handedOff = batchImports.batchRequests.single()
        assertEquals(2, handedOff.size)
        assertTrue(
            handedOff.all { uri ->
                Uri.parse(uri).authority == "${context.packageName}.capture.fileprovider"
            },
        )
        // 预拷件在批量仓库返回后立即删除：后续逐页导入读的是批量仓库自己的 staging。
        handedOff.forEach { uri ->
            val file = File(File(context.cacheDir, "captured_images"), Uri.parse(uri).lastPathSegment.orEmpty())
            assertFalse("pre-copy should be cleaned: $uri", file.exists())
        }
        assertEquals("fake-batch", (result as CaptureShareResult.ImageBatch).jobId)
    }

    @Test
    fun pdfBytesAreHandedToTheExistingPdfPipeline() = runBlocking<Unit> {
        val source = SharedCaptureTestFiles.fileUri(context, "two-pages.pdf") { file ->
            file.writeBytes(SharedCaptureTestFiles.pdfBytes(pageCount = 2))
        }
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(sendRequest(source, "application/pdf"))

        assertTrue(result is CaptureShareResult.Pdf)
        assertEquals(listOf(source.toString()), batchImports.pdfRequests)
        assertEquals("fake-pdf", (result as CaptureShareResult.Pdf).jobId)
    }

    @Test
    fun nonPdfBytesWithPdfMimeAreRejectedBeforeThePipeline() = runBlocking<Unit> {
        val source = SharedCaptureTestFiles.fileUri(context, "fake.pdf") { file ->
            file.writeText("not-a-pdf")
        }
        val intake = CaptureShareIntake(context, batchImports)

        val result = intake.intake(sendRequest(source, "application/pdf"))

        assertEquals(
            CaptureShareRejection.NOT_A_PDF,
            (result as CaptureShareResult.Rejected).reason,
        )
        assertTrue(batchImports.pdfRequests.isEmpty())
    }

    private fun sendRequest(uri: Uri, mimeType: String) = CaptureShareRequest(
        requestId = 1L,
        action = Intent.ACTION_SEND,
        mimeType = mimeType,
        uris = listOf(uri.toString()),
    )

    private fun stagingFiles(): List<File> {
        val directory = File(context.cacheDir, "captured_images")
        return directory.listFiles().orEmpty().filter { file ->
            file.isFile && file.name.startsWith("question_") &&
                (file.name.endsWith(".jpg") || file.name.endsWith(".img"))
        }
    }

    private class RecordingBatchImportRepository : BatchImportRepository {
        val batchRequests = mutableListOf<List<String>>()
        val pdfRequests = mutableListOf<String>()

        override fun observeBatchImports(): Flow<List<BatchImportJob>> = flowOf(emptyList())

        override suspend fun hasInterruptedBatchImport(): Boolean = false

        override suspend fun createBatchImport(request: CreateBatchImportRequest): BatchImportJob {
            batchRequests += request.localUris
            return fakeJob(jobId = "fake-batch", pageCount = request.localUris.size)
        }

        override suspend fun createPdfImport(request: CreatePdfImportRequest): BatchImportJob {
            pdfRequests += request.localUri
            return fakeJob(jobId = "fake-pdf", pageCount = 2)
        }

        override suspend fun pauseBatchImport(jobId: String) = Unit

        override suspend fun resumeBatchImport(jobId: String) = Unit

        override suspend fun retryBatchImportPage(jobId: String, pageIndex: Int) = Unit

        override suspend fun skipBatchImportPage(jobId: String, pageIndex: Int) = Unit

        override suspend fun organizeBatch(jobId: String) = Unit

        override suspend fun drivePendingImports(): Boolean = true

        private fun fakeJob(jobId: String, pageCount: Int) = BatchImportJob(
            jobId = jobId,
            status = BatchImportStatus.COMPLETED,
            pages = (0 until pageCount).map { pageIndex ->
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
