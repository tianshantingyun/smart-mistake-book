package com.tingyun.smartmistakebook

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        captureStudioScreenshot("review-completed")
    }

    @Test
    fun learningDataShowsTheSameContinuityWithoutAnotherCheckInControl() {
        val largeText = mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText.value) 1.6f else density.fontScale)) {
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
        }

        composeRule.onNodeWithContentDescription("连续复习，3天").assertExists()
        composeRule.waitForIdle()
        captureStudioScreenshot("profile-overview")
        composeRule.runOnIdle { largeText.value = true }
        composeRule.onNodeWithContentDescription("连续复习，3天").performScrollTo().assertIsDisplayed()
        captureStudioScreenshot("profile-large-type")
        composeRule.runOnIdle { largeText.value = false }
        composeRule.onNodeWithTag("profile_storage_setting").performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        captureStudioScreenshot("profile-settings")
    }

    private fun captureStudioScreenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") != "true") return
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = checkNotNull(context.getExternalFilesDir("full-ui-forest"))
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
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
