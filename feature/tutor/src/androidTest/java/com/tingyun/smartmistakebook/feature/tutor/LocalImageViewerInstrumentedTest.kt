package com.tingyun.smartmistakebook.feature.tutor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.ui.LocalImageViewer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import androidx.compose.ui.geometry.Offset
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalImageViewerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = mutableListOf<File>()
    @After fun cleanFixtures() { fixtures.forEach { it.delete() } }

    @Test fun zoomResetAndDismissAreAvailableWithoutAGesture() {
        val file = image("first", Color.RED)
        val open = mutableStateOf(true)
        compose.setContent {
            SmartMistakeBookTheme {
                Text("原来的页面")
                if (open.value) LocalImageViewer(file.toURI().toString(), "题目原图", { open.value = false })
            }
        }
        compose.waitUntil(5000) { compose.onAllImageNodes() }
        compose.onNodeWithContentDescription("放大图片").performClick()
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("125%")
        compose.onNodeWithContentDescription("复位图片").performClick()
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("100%")
        UiAuditEvidence.capture("local-image-viewer")
        compose.onNodeWithContentDescription("关闭图片查看").performClick()
        compose.onNodeWithText("原来的页面").assertIsDisplayed()
    }

    @Test fun changingTheImageResetsItsZoom() {
        val uri = mutableStateOf(image("one", Color.RED).toURI().toString())
        val second = image("two", Color.GREEN).toURI().toString()
        compose.setContent {
            SmartMistakeBookTheme { LocalImageViewer(uri.value, "当前图", {}) }
        }
        compose.waitUntil(5000) { compose.onAllImageNodes() }
        compose.onNodeWithContentDescription("放大图片").performClick()
        compose.runOnIdle { uri.value = second }
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("100%")
    }

    @Test fun directionButtonsCanRevealAnEdgeWithoutDragging() {
        val file = image("pan", Color.GREEN)
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
            .copy(Bitmap.Config.ARGB_8888, true)
        android.graphics.Canvas(bitmap).drawRect(0f, 0f, 100f, 100f,
            android.graphics.Paint().apply { color = Color.RED })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.setContent {
            SmartMistakeBookTheme { LocalImageViewer(file.toURI().toString(), "图形说明".repeat(80), {}) }
        }
        compose.waitUntil(5000) { compose.onAllImageNodes() }
        compose.onNodeWithContentDescription("关闭图片查看").assertIsDisplayed()
        repeat(4) { compose.onNodeWithContentDescription("放大图片").performClick() }
        compose.onNodeWithContentDescription("查看左侧").performClick()
        val pixels = compose.onNodeWithTag("image_viewer_viewport").captureToImage().toPixelMap()
        assertEquals(androidx.compose.ui.graphics.Color.Red, pixels[pixels.width / 2, pixels.height / 2])
        compose.onNodeWithContentDescription("查看右侧").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看上方").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看下方").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看上方").assertIsNotEnabled()
        compose.onNodeWithContentDescription("查看下方").assertIsNotEnabled()
    }

    @Test fun missingBytesProduceAReadableLocalFailure() {
        compose.setContent {
            SmartMistakeBookTheme { LocalImageViewer("file:///no-such-viewer-image.png", "缺失图", {}) }
        }
        compose.onNodeWithText("图片暂时打不开，请返回查看来源或重新选择。").assertIsDisplayed()
    }

    @Test fun doubleTapPanAndRegionChangeKeepTheCurrentImageBounded() {
        val uri = image("region", Color.RED).toURI().toString()
        val region = mutableStateOf<NormalizedSourceRegion?>(null)
        compose.setContent {
            SmartMistakeBookTheme { LocalImageViewer(uri, "区域图片", {}, region.value) }
        }
        compose.waitUntil(5000) { compose.onAllImageNodes() }
        compose.onNodeWithTag("image_viewer_viewport").performTouchInput { doubleClick(center) }
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("200%")
        compose.onNodeWithTag("image_viewer_viewport").performTouchInput {
            swipe(center, center + Offset(100f, 0f))
        }
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("200%")
        compose.runOnIdle { region.value = NormalizedSourceRegion(0.0, 0.0, 0.5, 0.5) }
        compose.waitUntil(5000) {
            compose.onNodeWithTag("image_viewer_resolution").runCatching {
                assertTextContains("100 × 50", substring = true); true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("100%")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllImageNodes(): Boolean =
        onNodeWithTag("image_viewer_resolution").runCatching { fetchSemanticsNode(); true }.getOrDefault(false)

    private fun image(name: String, color: Int): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "viewer-$name.png").also { fixtures += it }
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
