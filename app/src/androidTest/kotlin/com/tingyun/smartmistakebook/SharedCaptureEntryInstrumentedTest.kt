package com.tingyun.smartmistakebook

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.BatchImportStatus
import com.tingyun.smartmistakebook.feature.capture.MAX_SHARE_IMAGE_BYTES
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A7（4C）系统分享进 App 的**全链仪器化证据**：
 * 分享 intent → MainActivity 投递 → 接收页 → 单图落草稿 / 多图进整卷管线 / 坏文件与
 * 数量/大小门如实拒绝。
 *
 * 分享源用 App 自己的 `capture.fileprovider`（真实 ContentResolver：真实 scheme / MIME /
 * 大小），同时**不经过系统 Activity 投递**——与既有的 `handleOpenIntent` 测试缝同一条
 * 先例（`MistakeExportBackgroundInstrumentedTest`）。系统的 intent-filter 由 manifest
 * 声明，这里钉住的是消费点之后的整条链。
 */
@RunWith(AndroidJUnit4::class)
class SharedCaptureEntryInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        SharedCaptureTestFiles.cleanup()
    }

    @Test
    fun aSharedImageLandsAsAnEditableCaptureDraft() {
        waitForTag("root_review")
        val application = application()
        val before = pendingDraftIds(application)
        val source = SharedCaptureTestFiles.pngFile(context, "valid.png")

        share(singleShareIntent(source))
        waitForTag("capture_screen")
        composeRule.waitUntil(timeoutMillis = 60_000) {
            pendingDraftIds(application).size > before.size
        }

        val newDraftIds = pendingDraftIds(application) - before
        assertEquals(1, newDraftIds.size)
        val draftId = newDraftIds.single()
        val draft = runBlocking { application.captureRepository.readPendingCapture(draftId) }
        assertNotNull("分享的图片应当落成可恢复草稿", draft)
        // 草稿的源图是规范资产（file://），说明字节经过了既有的入库链，而不是把外部 URI
        // 直接当成长久可读的源。
        assertTrue(checkNotNull(draft!!.sourceImageUri).startsWith("file:"))
        runCatching {
            val item = runBlocking {
                application.captureRepository.observePendingCaptures().first()
                    .first { it.draftId == draftId }
            }
            runBlocking {
                application.captureRepository.abandonPendingCapture(
                    draftId = draftId,
                    expectedRevisionNumber = item.currentRevisionNumber,
                    abandonedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    @Test
    fun aSharedFileThatIsNotAnImageIsRejectedWithoutCreatingADraft() {
        waitForTag("root_review")
        val application = application()
        val before = pendingDraftIds(application)
        val source = SharedCaptureTestFiles.fileUri(context, "bad.png") { file ->
            file.writeText("not-an-image")
        }

        share(singleShareIntent(source))
        waitForTag("share_intake_rejected")

        composeRule.onNodeWithText("不是可识别的图片", substring = true).assertExists()
        composeRule.onNodeWithTag("share_intake_manual").assertExists()
        composeRule.waitForIdle()
        assertEquals(before, pendingDraftIds(application))
    }

    @Test
    fun aShareOfMoreThanThirtyFilesIsRejectedByTheCountGate() {
        waitForTag("root_review")
        val application = application()
        val beforeJobs = batchJobIds(application)
        val uris = (0..30).map { index ->
            Uri.parse("content://com.tingyun.smartmistakebook.missing/share/$index")
        }

        share(multipleShareIntent(uris))
        waitForTag("share_intake_rejected")

        composeRule.onNodeWithText("一次最多导入 30 个文件", substring = true).assertExists()
        assertEquals(beforeJobs, batchJobIds(application))
    }

    @Test
    fun aSharedImageAboveTheByteBudgetIsRejectedByTheSizeGate() {
        waitForTag("root_review")
        val application = application()
        val before = pendingDraftIds(application)
        val source = SharedCaptureTestFiles.fileUri(context, "huge.jpg") { file ->
            file.outputStream().buffered().use { output ->
                val chunk = ByteArray(1 shl 20)
                var written = 0L
                while (written < MAX_SHARE_IMAGE_BYTES) {
                    output.write(chunk)
                    written += chunk.size
                }
                // 多 1 字节：恰好越过上限，证明门在边界上按“大于”拒绝。
                output.write(0)
            }
        }

        share(singleShareIntent(source, mimeType = "image/jpeg"))
        waitForTag("share_intake_rejected")

        composeRule.onNodeWithText("20 MB", substring = true).assertExists()
        composeRule.waitForIdle()
        assertEquals(before, pendingDraftIds(application))
    }

    @Test
    fun severalSharedImagesStartTheExistingBatchImport() {
        waitForTag("root_review")
        val application = application()
        val beforeJobs = batchJobIds(application)
        val beforeDrafts = pendingDraftIds(application)
        val sources = listOf(
            SharedCaptureTestFiles.pngFile(context, "page-a.png"),
            SharedCaptureTestFiles.pngFile(context, "page-b.png"),
        )

        share(multipleShareIntent(sources))
        waitForTag("batch_import_root")
        composeRule.waitUntil(timeoutMillis = 60_000) {
            batchJob(application, beforeJobs)?.let { job ->
                job.status == BatchImportStatus.COMPLETED && job.readyCount == 2
            } == true
        }

        val job = checkNotNull(batchJob(application, beforeJobs))
        assertEquals(2, job.pages.size)
        assertTrue(job.pages.all { page -> page.draftId != null })
        // 两页都落成待处理草稿：多文件分享没有"只认第一张"的半截行为。
        composeRule.waitUntil(timeoutMillis = 60_000) {
            pendingDraftIds(application).size >= beforeDrafts.size + 2
        }
    }

    private fun application(): SmartMistakeBookApplication =
        composeRule.activity.application as SmartMistakeBookApplication

    private fun share(intent: Intent) {
        composeRule.runOnUiThread {
            composeRule.activity.handleOpenIntent(intent)
        }
    }

    private fun singleShareIntent(uri: Uri, mimeType: String = "image/png"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun multipleShareIntent(uris: List<Uri>, mimeType: String = "image/*"): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun pendingDraftIds(application: SmartMistakeBookApplication): Set<String> =
        runBlocking {
            withTimeout(10_000) {
                application.captureRepository.observePendingCaptures().first()
            }
        }.map { it.draftId }.toSet()

    private fun batchJobIds(application: SmartMistakeBookApplication): Set<String> =
        runBlocking {
            withTimeout(10_000) {
                application.batchImportRepository.observeBatchImports().first()
            }
        }.map { it.jobId }.toSet()

    private fun batchJob(
        application: SmartMistakeBookApplication,
        beforeJobs: Set<String>,
    ) = runBlocking {
        withTimeout(10_000) {
            application.batchImportRepository.observeBatchImports().first()
        }
    }.firstOrNull { it.jobId !in beforeJobs }

    private fun waitForTag(tag: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
