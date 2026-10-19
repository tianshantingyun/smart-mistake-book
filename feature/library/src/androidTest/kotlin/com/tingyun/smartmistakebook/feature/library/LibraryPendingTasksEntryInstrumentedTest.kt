package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-06 空态半边：空库仍有录入 CTA，同时待处理失败任务保留继续入口。
 */
@RunWith(AndroidJUnit4::class)
class LibraryPendingTasksEntryInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryKeepsCaptureAndPendingTaskEntryReachable() {
        var continued = false
        composeRule.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(
                    entries = emptyList(),
                    pendingFailedTaskCount = 2,
                    onContinuePendingTasks = { continued = true },
                    onCapture = {},
                    onExportVisible = {},
                    onOpenExportResults = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_pending_tasks").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(continued) }
        composeRule.onNodeWithTag("library_capture_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCountWithoutAContinueTargetDoesNotRenderAFakeEntry() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(
                    entries = emptyList(),
                    pendingFailedTaskCount = 2,
                    onCapture = {},
                    onExportVisible = {},
                    onOpenExportResults = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_pending_tasks").assertDoesNotExist()
        composeRule.onNodeWithTag("library_capture_button").performScrollTo().assertIsDisplayed()
    }
}
