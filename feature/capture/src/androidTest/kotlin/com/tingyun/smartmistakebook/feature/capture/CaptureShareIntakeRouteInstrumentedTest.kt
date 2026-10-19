package com.tingyun.smartmistakebook.feature.capture

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-57：接收页的授权两态与 Expired 派生。
 *
 * - 授权有效：可重试失败后"再试一次"真的成功；
 * - 授权失效（读取被拒 / 超时）：Expired 可达，且恢复（`StateRestorationTester`）不会
 *   把保存下来的非空请求变成永不过期；
 * - 明确放弃才撤销授权。
 */
@RunWith(AndroidJUnit4::class)
class CaptureShareIntakeRouteInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var nowEpochMillis = 1_700_000_000_000L
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
    fun aTransientFailureCanBeRetriedAndSucceedsWhileTheGrantIsValid() {
        val source = captureSourceFile("retry.png")
        val sourceUri = CaptureShareTestFiles.sourceUri(context, source)
        val request = shareRequest(listOf(sourceUri))
        val landedUri = AtomicReference<String?>(null)

        composeRule.setContent {
            TestShareRoute(
                request = request,
                now = { nowEpochMillis },
                onSingleImageReady = { uri -> landedUri.set(uri) },
            )
        }

        waitForRejectedCard()
        composeRule.onNodeWithTag("share_intake_retry").performScrollTo().assertExists()

        // 第一次失败是"文件暂时不在"；授权没有被撤，同一个 URI 这次能读。
        source.writeBytes(CaptureShareTestFiles.pngBytes)
        composeRule.onNodeWithTag("share_intake_retry").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 20_000L) { landedUri.get() != null }
        assertTrue(
            "retry should hand off an owned capture uri, was ${landedUri.get()}",
            landedUri.get().orEmpty().contains(".capture.fileprovider"),
        )
    }

    @Test
    fun aRestoredShareExpiresAtTheDeadlineInsteadOfStayingRetryableForever() {
        val source = captureSourceFile("restored.png")
        val sourceUri = CaptureShareTestFiles.sourceUri(context, source)
        val request = shareRequest(listOf(sourceUri))
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            TestShareRoute(
                request = request,
                now = { nowEpochMillis },
            )
        }

        waitForRejectedCard()
        composeRule.onNodeWithTag("share_intake_retry").assertExists()

        // 进程重建的形态：request 与"收下分享的时刻"一起被 Saver 恢复成非空，
        // 但时间已经越过窗口——派生出的必须是 Expired，而不是永远可重试。
        nowEpochMillis += 60_001L
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithText(CAPTURE_SHARE_EXPIRED_MESSAGE)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("share_intake_retry").assertDoesNotExist()
    }

    @Test
    fun aDeniedReadGrantShowsExpiredInsteadOfUnreadable() {
        // ContentResolver.wrap 是 API 29+ 的测试替身；旧设备上跳过这条替身用例。
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val request = shareRequest(listOf(deniedReadUri()))
        val deniedContext = deniedReadContext(context)

        composeRule.setContent {
            TestShareRoute(
                request = request,
                contextOverride = deniedContext,
                now = { nowEpochMillis },
            )
        }

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithText(CAPTURE_SHARE_EXPIRED_MESSAGE)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("share_intake_retry").assertDoesNotExist()
    }

    @Test
    fun abandoningTheFailedShareReleasesTheReadGrant() {
        val source = captureSourceFile("bad.png").apply { writeText("not-an-image") }
        val sourceUri = CaptureShareTestFiles.sourceUri(context, source)
        val request = shareRequest(listOf(sourceUri))
        val recorder = RecordingRevokeContext(context)
        val manualEntryOpened = AtomicBoolean(false)

        composeRule.setContent {
            TestShareRoute(
                request = request,
                contextOverride = recorder,
                now = { nowEpochMillis },
                onManualEntry = { manualEntryOpened.set(true) },
            )
        }

        waitForRejectedCard()
        assertTrue(
            "a failure alone must not revoke the grant",
            recorder.revokedUris.isEmpty(),
        )

        composeRule.onNodeWithTag("share_intake_manual").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 20_000L) { manualEntryOpened.get() }
        assertEquals(listOf(sourceUri), recorder.revokedUris)
    }

    private fun captureSourceFile(name: String): File =
        CaptureShareTestFiles.sourceFile(context, name).also { createdFiles += it }

    private fun waitForRejectedCard() {
        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithTag("share_intake_rejected")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}

@Composable
private fun TestShareRoute(
    request: CaptureShareRequest?,
    now: () -> Long,
    contextOverride: Context? = null,
    onSingleImageReady: (String) -> Unit = {},
    onImportStarted: () -> Unit = {},
    onManualEntry: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val batchImports = remember { CaptureShareTestBatchImports() }
    val content: @Composable () -> Unit = {
        SmartMistakeBookTheme {
            CaptureShareIntakeRoute(
                request = request,
                batchImports = batchImports,
                onSingleImageReady = onSingleImageReady,
                onImportStarted = onImportStarted,
                onManualEntry = onManualEntry,
                onDismiss = onDismiss,
                authorizationTimeoutMillis = 60_000L,
                clock = now,
            )
        }
    }
    if (contextOverride == null) {
        content()
    } else {
        CompositionLocalProvider(LocalContext provides contextOverride) {
            content()
        }
    }
}
