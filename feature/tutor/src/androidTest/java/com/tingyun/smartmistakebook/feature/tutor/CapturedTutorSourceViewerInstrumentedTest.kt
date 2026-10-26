package com.tingyun.smartmistakebook.feature.tutor

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturedTutorSourceViewerInstrumentedTest : CapturedTutorSessionTestBase() {
    private var fixture: File? = null
    @After fun removeFixture() { fixture?.delete() }

    @Test fun capturedSessionUsesTheSameReadOnlyZoomAndReturnsToItsSource() {
        val file = File.createTempFile("tutor-original-", ".png",
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir).also { fixture = it }
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        var saves = 0
        composeRule.setContent { SmartMistakeBookTheme {
            ReadyCapturedSession(session = session().copy(sourceImageUri = file.toURI().toString()),
                saveInProgress = false, saveError = null, onSave = { saves++ },
                modelTasks = unavailableModelTasks(), interactions = emptyInteractions(),
                profile = StudyProfileOverview(), onOpenModelSettings = {})
        } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("captured_tutor_source_image").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("captured_tutor_source_image").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("拍题原图").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("放大图片").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("image_viewer_scale").assertTextContains("125%")
        composeRule.onNodeWithContentDescription("关闭图片查看").performClick()
        composeRule.onNodeWithTag("captured_tutor_source_image").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, saves) }
    }
}
