package com.tingyun.smartmistakebook

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.NetworkMode
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.profile.ProfileRoute
import com.tingyun.smartmistakebook.feature.review.ReviewRoute
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReviewContinuityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedReviewShowsAnAutomaticPlainLanguageContinuityRecord() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(
                        scheduledCount = 5,
                        estimatedSeconds = 600,
                        completedToday = true,
                        completionStreakDays = 3,
                    ),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_continuity")
            .assertTextEquals("今天已完成 · 连续复习 3 天")
        composeRule.onNodeWithTag("review_start_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_capture_button").assertDoesNotExist()
        composeRule.onNodeWithTag("review_capture_shortcut").assertDoesNotExist()
        composeRule.waitForIdle()
        captureReviewForestScreenshot("review-completed")
    }

    @Test
    fun learningDataShowsTheSameContinuityWithoutAnotherCheckInControl() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ProfileRoute(
                    overview = StudyProfileOverview(
                        hasLearningEvidence = true,
                        recordedAttemptCount = 8,
                    ),
                    review = StudyReviewOverview(
                        completedToday = true,
                        completionStreakDays = 3,
                    ),
                    capabilities = AppCapabilitySnapshot(
                        networkMode = NetworkMode.LOCAL_FIRST,
                        cameraCaptureAvailable = true,
                        trustedOcrAvailable = false,
                        tutorTeachingEnabled = true,
                        remoteModelConfigured = false,
                    ),
                    onOpenCapability = {},
                    onOpenLearningMastery = {},
                    onOpenDataPrivacy = {},
                    onOpenReminder = {},
                    onOpenScheduling = {},
                    onOpenStorage = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("连续复习，3天").assertExists()
        composeRule.waitForIdle()
        captureReviewForestScreenshot("profile-overview")
        composeRule.onNodeWithTag("profile_storage_setting").performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        captureReviewForestScreenshot("profile-settings")
    }
}

/** Evidence stays opt-in and uses the real native pixels from the current test. */
internal fun captureReviewForestScreenshot(name: String) {
    if (InstrumentationRegistry.getArguments().getString("uiEvidence") != "true") return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    instrumentation.uiAutomation.waitForIdle(150, 2_000)
    val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("full-ui-forest"))
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(directory, "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    } finally {
        screenshot.recycle()
    }
}
