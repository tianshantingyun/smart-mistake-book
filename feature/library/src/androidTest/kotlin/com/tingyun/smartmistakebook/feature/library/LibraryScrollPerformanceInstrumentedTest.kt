package com.tingyun.smartmistakebook.feature.library

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the system frame clock; a Compose test clock would distort native frame timings. */
class LibraryScrollPerformanceInstrumentedTest {
    @Test fun longCatalogRecordsNativeFrameStatsDuringTouchScrolling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-completion").apply { mkdirs() }
        val automation = instrumentation.uiAutomation
        fun shell(command: String): String = automation.executeShellCommand(command).use {
            ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { stream -> stream.readText() }
        }
        fun firstVisible(): Boolean = automation.rootInActiveWindow?.let(::containsFirstCard) == true
        File(directory, "library-150-touch-frames.txt").delete()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    SmartMistakeBookTheme {
                        LibraryRoute((0 until 150).map { index ->
                            StudyCatalogEntry("scroll-$index", "problem-$index", "revision-$index", "practice-$index",
                                "MATH", "函数与几何练习 ${index + 1}", "根据题意写出计算过程，并检查结果。",
                                null, false, emptyList(), emptyList(), nextReviewAtEpochMillis = null, retrievability = null)
                        }, onCapture = {}, onExportVisible = {}, onOpenExportResults = {}, onOpenItem = {})
                    }
                }
            }
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (!firstVisible()) {
                check(SystemClock.uptimeMillis() < deadline) { "The actual catalog did not render" }
                SystemClock.sleep(100)
            }
            val list = checkNotNull(findScrollable(checkNotNull(automation.rootInActiveWindow)))
            val bounds = Rect().also(list::getBoundsInScreen)
            val top = bounds.top + bounds.height() / 5
            val bottom = bounds.bottom - bounds.height() / 5
            shell("dumpsys gfxinfo ${context.packageName} reset")
            repeat(8) { shell("input swipe ${bounds.centerX()} $bottom ${bounds.centerX()} $top 320") }
            assertTrue("Actual touch scrolling must move the first card offscreen", !firstVisible())
            repeat(8) { shell("input swipe ${bounds.centerX()} $top ${bounds.centerX()} $bottom 320") }
            instrumentation.waitForIdleSync()
            val report = shell("dumpsys gfxinfo ${context.packageName} framestats")
            File(directory, "library-150-touch-frames.txt").writeText(report)
            val total = Regex("Total frames rendered: (\\d+)").find(report)?.groupValues?.get(1)?.toInt()
            assertTrue("Must record actual rendered frames", total != null && total > 0)
            val screenshot = checkNotNull(automation.takeScreenshot())
            try {
                File(directory, "library-150-native-scroll.png").outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
        }
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        if (node.isScrollable) node else (0 until node.childCount).firstNotNullOfOrNull {
            node.getChild(it)?.let(::findScrollable)
        }

    private fun containsFirstCard(node: AccessibilityNodeInfo): Boolean =
        (node.isVisibleToUser && Regex("函数与几何练习 1(?!\\d)").containsMatchIn(node.text?.toString().orEmpty())) ||
            (0 until node.childCount).any { node.getChild(it)?.let(::containsFirstCard) == true }
}
