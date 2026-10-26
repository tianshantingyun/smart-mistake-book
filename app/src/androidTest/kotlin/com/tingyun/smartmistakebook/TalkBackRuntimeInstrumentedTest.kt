package com.tingyun.smartmistakebook

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in walkthrough with the installed TalkBack service, not a semantics substitute. */
class TalkBackRuntimeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun talkBackTraversalActivatesTheRealLibraryDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("talkback_audit") == "true")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("今日复习").fetchSemanticsNodes().isNotEmpty() }
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            notificationTimeout = 0
        }
        fun shell(command: String): String = automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { stream -> stream.readText() }
        }
        val state = shell("dumpsys accessibility")
        assertTrue("TalkBack must be bound with touch exploration enabled", state.contains("TalkBackService") &&
            state.contains("touchExplorationEnabled=true"))
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-completion").apply { mkdirs() }
        File(directory, "talkback-service.txt").writeText(state)
        val initial = checkNotNull(automation.takeScreenshot())
        try {
            File(directory, "talkback-start.png").outputStream().use { initial.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { initial.recycle() }
        val visits = JSONArray()
        var sequence = 0
        fun hardware(kind: String, bounds: Rect) {
            val number = ++sequence
            File(directory, "hardware-input-request.json").writeText(JSONObject().put("sequence", number)
                .put("kind", kind).put("x", bounds.centerX()).put("y", bounds.centerY())
                .put("width", bounds.width()).toString())
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (File(directory, "hardware-input-response.txt").run {
                if (exists()) readText().trim().toIntOrNull() else null
            } != number) {
                check(SystemClock.uptimeMillis() < deadline) { "The opt-in emulator hardware driver did not acknowledge $number" }
                SystemClock.sleep(20)
            }
        }
        val tutor = checkNotNull(findId(checkNotNull(automation.rootInActiveWindow), "nav_tutor"))
        val tutorBounds = Rect().also(tutor::getBoundsInScreen)
        hardware("tap", tutorBounds)
        SystemClock.sleep(300)
        val firstFocus = automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        File(directory, "talkback-initial-focus.txt").writeText(firstFocus.toString())
        var library: AccessibilityNodeInfo? = null
        repeat(60) {
            if (library != null) return@repeat
            val root = checkNotNull(automation.rootInActiveWindow)
            val bounds = Rect().also(root::getBoundsInScreen)
            try { automation.executeAndWaitForEvent({
                hardware("swipe", bounds)
            }, { it.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED }, 5_000)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                File(directory, "talkback-timeout-root.txt").writeText(root.toString() + "\n" +
                    shell("dumpsys accessibility"))
                throw timeout
            }
            val focused = automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (focused != null) {
                visits.put(JSONObject().put("text", focused.text?.toString())
                    .put("description", focused.contentDescription?.toString())
                    .put("resourceId", focused.viewIdResourceName).put("selected", focused.isSelected)
                    .put("state", focused.stateDescription?.toString()))
                if (focused.viewIdResourceName?.endsWith("nav_library") == true) library = focused
            }
            File(directory, "talkback-traversal.json").writeText(visits.toString(2))
        }
        File(directory, "talkback-traversal.json").writeText(visits.toString(2))
        val target = checkNotNull(library) { "TalkBack did not reach the library navigation item: $visits" }
        val bounds = Rect().also(target::getBoundsInScreen)
        // Kernel touchscreen events pass through TalkBack rather than directly invoking the app.
        hardware("activate", bounds)
        // External input changes Compose state outside the test scheduler; allow its frame to render.
        compose.waitForIdle()
        val deadline = SystemClock.uptimeMillis() + 10_000
        var arrived = false
        while (!arrived && SystemClock.uptimeMillis() < deadline) {
            arrived = automation.rootInActiveWindow?.let {
                findId(it, "nav_library")?.isSelected == true && containsId(it, "library_export_results")
            } == true
            if (!arrived) SystemClock.sleep(100)
        }
        File(directory, "talkback-activated-tree.txt").writeText(describeTree(checkNotNull(automation.rootInActiveWindow)))
        val screenshot = checkNotNull(automation.takeScreenshot())
        try {
            File(directory, "talkback-library-focus.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        assertTrue("TalkBack's activation must select Library and expose its page actions", arrived)
        assertTrue(shell("dumpsys accessibility").contains("touchExplorationEnabled=true"))
    }

    private fun containsId(node: AccessibilityNodeInfo, suffix: String): Boolean =
        node.viewIdResourceName?.endsWith(suffix) == true ||
            (0 until node.childCount).any { index -> node.getChild(index)?.let { containsId(it, suffix) } == true }

    private fun findId(node: AccessibilityNodeInfo, suffix: String): AccessibilityNodeInfo? =
        if (node.viewIdResourceName?.endsWith(suffix) == true) node else
            (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> findId(child, suffix) } }

    private fun describeTree(node: AccessibilityNodeInfo): String =
        "${node.viewIdResourceName} selected=${node.isSelected} text=${node.text} description=${node.contentDescription}\n" +
            (0 until node.childCount).joinToString("") { node.getChild(it)?.let(::describeTree).orEmpty() }
}
