package com.tingyun.smartmistakebook.feature.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-57：分享读授权的生命周期与失败理由（字节层，不经 UI）。
 *
 * 三条被钉住的事实：
 * 1. 可重试失败**不**撤销读授权，同一个 URI 的第二次尝试能成功；
 * 2. 成功才撤销；用户明确放弃（[CaptureShareIntake.releaseReadGrants]）也撤销；
 * 3. 读授权被拒是 AUTHORIZATION_EXPIRED，不是 UNREADABLE，也不是 IMPORT_FAILED。
 */
@RunWith(AndroidJUnit4::class)
class CaptureShareAuthorizationInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdFiles = mutableListOf<File>()
    private var stagingBaseline: Set<File> = emptySet()

    @Before
    fun captureStagingBaseline() {
        stagingBaseline = CaptureShareTestFiles.ownedStagingFiles(context)
    }

    @After
    fun tearDown() {
        createdFiles.forEach { file -> runCatching { file.delete() } }
        createdFiles.clear()
        CaptureShareTestFiles.deleteNewStagingFiles(context, stagingBaseline)
    }

    @Test
    fun aRetryableFailureKeepsTheGrantAndTheSameFileCanBeRetriedSuccessfully() =
        runBlocking<Unit> {
            val source = captureSourceFile("retry.png")
            val sourceUri = CaptureShareTestFiles.sourceUri(context, source)
            val recorder = RecordingRevokeContext(context)
            val intake = CaptureShareIntake(recorder, CaptureShareTestBatchImports())

            val first = intake.intake(shareRequest(listOf(sourceUri)))

            assertEquals(
                CaptureShareRejection.UNREADABLE,
                (first as CaptureShareResult.Rejected).reason,
            )
            assertTrue(
                "a retryable failure must keep the source grant, revoked=${recorder.revokedUris}",
                recorder.revokedUris.isEmpty(),
            )

            // 同一个 URI（授权仍在）第二次读取：这次文件可读，必须真的成功并被撤销授权。
            source.writeBytes(CaptureShareTestFiles.pngBytes)
            val second = intake.intake(shareRequest(listOf(sourceUri)))

            assertTrue("retry should succeed, was $second", second is CaptureShareResult.SingleImage)
            assertEquals(sourceUri, recorder.revokedUris.single())
        }

    @Test
    fun aNonRetryableFailureKeepsTheGrantUntilTheCallerExplicitlyAbandonsIt() =
        runBlocking<Unit> {
            val source = captureSourceFile("bad.png").apply { writeText("not-an-image") }
            val sourceUri = CaptureShareTestFiles.sourceUri(context, source)
            val recorder = RecordingRevokeContext(context)
            val intake = CaptureShareIntake(recorder, CaptureShareTestBatchImports())
            val request = shareRequest(listOf(sourceUri))

            val result = intake.intake(request)

            assertEquals(
                CaptureShareRejection.NOT_AN_IMAGE,
                (result as CaptureShareResult.Rejected).reason,
            )
            assertTrue(
                "a terminal failure must not revoke before the user abandons the share",
                recorder.revokedUris.isEmpty(),
            )

            intake.releaseReadGrants(request)

            assertEquals(listOf(sourceUri), recorder.revokedUris)
        }

    @Test
    fun aDeniedReadGrantIsReportedAsAuthorizationExpiredNotUnreadable() = runBlocking<Unit> {
        // ContentResolver.wrap 是 API 29+ 的测试替身；旧设备上没有真实授权撤销可模拟。
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val recorder = RecordingRevokeContext(deniedReadContext(context))
        val intake = CaptureShareIntake(recorder, CaptureShareTestBatchImports())

        val result = intake.intake(
            shareRequest(listOf(deniedReadUri()), mimeType = "image/png"),
        )

        val rejected = result as CaptureShareResult.Rejected
        assertEquals(CaptureShareRejection.AUTHORIZATION_EXPIRED, rejected.reason)
        assertEquals(CAPTURE_SHARE_EXPIRED_MESSAGE, rejected.message)
        assertTrue(
            "an expired authorization has nothing left to revoke",
            recorder.revokedUris.isEmpty(),
        )
    }

    @Test
    fun anImportFailureIsReportedAsImportFailedAndKeepsTheGrant() = runBlocking<Unit> {
        val first = captureSourceFile("a.png").apply {
            writeBytes(CaptureShareTestFiles.pngBytes)
        }
        val second = captureSourceFile("b.png").apply {
            writeBytes(CaptureShareTestFiles.pngBytes)
        }
        val sourceUris = listOf(
            CaptureShareTestFiles.sourceUri(context, first),
            CaptureShareTestFiles.sourceUri(context, second),
        )
        val recorder = RecordingRevokeContext(context)
        val intake = CaptureShareIntake(
            recorder,
            CaptureShareTestBatchImports(failOnBatch = true),
        )

        val result = intake.intake(
            shareRequest(sourceUris, action = Intent.ACTION_SEND_MULTIPLE, mimeType = "image/*"),
        )

        assertEquals(
            CaptureShareRejection.IMPORT_FAILED,
            (result as CaptureShareResult.Rejected).reason,
        )
        assertTrue(
            "a failed import must stay retryable with the same grants",
            recorder.revokedUris.isEmpty(),
        )
        assertEquals(
            "pre-copies must be cleaned up after the failed batch handoff",
            stagingBaseline,
            CaptureShareTestFiles.ownedStagingFiles(context),
        )
    }

    private fun captureSourceFile(name: String): File =
        CaptureShareTestFiles.sourceFile(context, name).also { createdFiles += it }
}
