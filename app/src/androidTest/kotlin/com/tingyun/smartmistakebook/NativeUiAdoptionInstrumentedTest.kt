package com.tingyun.smartmistakebook

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the task-owned emulator. Does not clear or seed the application's study database. */
@RunWith(AndroidJUnit4::class)
class NativeUiAdoptionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allFourDestinationsAndSettingsRemainReachable() {
        listOf("review" to "root_review", "library" to "root_library",
            "tutor" to "root_tutor", "profile" to "root_profile").forEach { (destination, screen) ->
            await("nav_$destination")
            // Use a real pointer so default screenshots do not inherit keyboard focus from other suites.
            compose.onNodeWithTag("nav_$destination").assertIsDisplayed().performTouchInput { click() }
            await(screen)
            capture(destination)
        }
        compose.onNodeWithTag("profile_capability_setting").performScrollTo().performClick()
        await("capability_screen")
        compose.onNodeWithTag("capability_protocol_group").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capability_model_id").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capability_save").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
        await("root_profile")
    }

    private fun await(tag: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") != "true") return
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("ui-adoption"))
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
