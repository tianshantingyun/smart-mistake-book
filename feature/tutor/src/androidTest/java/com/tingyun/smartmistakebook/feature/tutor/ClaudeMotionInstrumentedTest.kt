package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.provider.Settings
import android.os.ParcelFileDescriptor
import com.tingyun.smartmistakebook.core.ui.ClaudeButton
import com.tingyun.smartmistakebook.core.ui.ClaudeDisclosure
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
import com.tingyun.smartmistakebook.core.ui.PageState
import com.tingyun.smartmistakebook.core.ui.PageStateFrame
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaudeMotionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingTheSystemAnimationScaleUpdatesTheActiveThemeWithoutRestarting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val original = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun setScale(scale: Float) {
            val descriptor = instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $scale")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        try {
            setScale(1f)
            compose.setContent {
                SmartMistakeBookTheme { Text(if (LocalClaudeMotionEnabled.current) "motion-enabled" else "motion-disabled") }
            }
            compose.onNodeWithText("motion-enabled").assertIsDisplayed()
            setScale(0f)
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("motion-disabled")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("motion-disabled").assertIsDisplayed()
            setScale(1f)
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("motion-enabled")).fetchSemanticsNodes().isNotEmpty() }
        } finally { setScale(original) }
    }

    @Test fun reducedMotionRemovesDisclosureImmediatelyAndKeepsTheDraft() {
        var toggles = 0
        compose.setContent {
            CompositionLocalProvider(LocalClaudeMotionEnabled provides false) {
                SmartMistakeBookTheme {
                    var expanded by remember { mutableStateOf(true) }
                    var draft by remember { mutableStateOf("") }
                    Column {
                        ClaudeTextField(draft, { draft = it }, Modifier.testTag("motion_draft"))
                        ClaudeButton({ expanded = !expanded; toggles++ }, Modifier.testTag("motion_toggle")) { Text("切换详情") }
                        ClaudeDisclosure(expanded) { Text("这些是原有详情", Modifier.testTag("motion_details")) }
                    }
                }
            }
        }
        compose.onNodeWithTag("motion_draft").performTextInput("保留完整草稿🧪")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("motion_toggle").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("motion_details").assertDoesNotExist()
        compose.onNodeWithText("保留完整草稿🧪").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun loadingSkeletonFollowsTheRealStateAndDoesNotShowAFalseEmptyOrProgressValue() {
        val loading = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalClaudeMotionEnabled provides false) {
                SmartMistakeBookTheme {
                    PageStateFrame(if (loading.value) PageState.Loading else null,
                        Modifier.fillMaxWidth()) { Text("真实内容已载入", Modifier.testTag("loaded_content")) }
                }
            }
        }
        compose.onNodeWithTag("page_state_loading").assertIsDisplayed()
        compose.onNodeWithTag("page_state_skeleton", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("page_state_empty").assertDoesNotExist()
        compose.onNodeWithText("0%").assertDoesNotExist()
        compose.runOnIdle { loading.value = false }
        compose.onNodeWithTag("page_state_skeleton", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("loaded_content").assertIsDisplayed()
    }
}
