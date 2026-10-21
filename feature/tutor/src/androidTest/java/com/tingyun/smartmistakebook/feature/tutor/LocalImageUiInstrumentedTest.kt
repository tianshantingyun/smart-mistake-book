package com.tingyun.smartmistakebook.feature.tutor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.ui.AttachedImageCard
import com.tingyun.smartmistakebook.core.ui.AttachedImageRenderState
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.LocalImageLoadState
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalImageUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val files = mutableListOf<File>()

    @After fun cleanFixtures() {
        ControlledImageProvider.clear()
        files.forEach { it.delete() }
    }

    @Test fun changingUriHidesThePreviousImageWhileTheNextReadIsBlocked() {
        val first = fixture("first")
        val next = fixture("next", blocked = true)
        val uri = mutableStateOf(first.first)
        val load = AtomicReference(LocalImageLoadState.LOADING)
        compose.setContent {
            Column {
                BoundedLocalImage(uri.value, "current image", expanded = false, onLoadStateChange = load::set)
                Text("below image")
            }
        }
        compose.waitUntil(5_000) { load.get() == LocalImageLoadState.AVAILABLE }
        compose.onNodeWithContentDescription("current image").assertIsDisplayed()
        val previousBottom = compose.onNodeWithText("below image").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { uri.value = next.first }
        compose.waitUntil(5_000) { next.second.readStarted.get() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("current image").assertDoesNotExist()
        assertEquals(LocalImageLoadState.LOADING, load.get())
        assertEquals("Loading must preserve the previous image's layout height", previousBottom,
            compose.onNodeWithText("below image").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        next.second.release.countDown()
        compose.waitUntil(5_000) { load.get() == LocalImageLoadState.AVAILABLE }
        compose.onNodeWithContentDescription("current image").assertIsDisplayed()
    }

    @Test fun changingRegionOfTheSameUriCannotShowThePreviousCrop() {
        val first = fixture("crop")
        val region = mutableStateOf<NormalizedSourceRegion?>(null)
        val load = AtomicReference(LocalImageLoadState.LOADING)
        compose.setContent {
            BoundedLocalImage(first.first, "current crop", expanded = false,
                sourceRegion = region.value, onLoadStateChange = load::set)
        }
        compose.waitUntil(5_000) { load.get() == LocalImageLoadState.AVAILABLE }
        val blocked = ControlledImageProvider.register("crop", first.second.file, blocked = true)
        compose.runOnIdle { region.value = NormalizedSourceRegion(0.0, 0.0, 0.5, 0.5) }
        compose.waitUntil(5_000) { blocked.second.readStarted.get() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("current crop").assertDoesNotExist()
        assertEquals(LocalImageLoadState.LOADING, load.get())
        blocked.second.release.countDown()
        compose.waitUntil(5_000) { load.get() == LocalImageLoadState.AVAILABLE }
        compose.onNodeWithContentDescription("current crop").assertIsDisplayed()
    }

    @Test fun aLateReadCannotReplaceTheLatestImage() {
        val first = fixture("a")
        val blocked = fixture("b", blocked = true, color = Color.RED)
        val last = fixture("c", color = Color.GREEN)
        val uri = mutableStateOf(first.first)
        val load = AtomicReference(LocalImageLoadState.LOADING)
        compose.setContent {
            BoundedLocalImage(uri.value, uri.value, expanded = false, onLoadStateChange = load::set)
        }
        compose.waitUntil(5_000) { load.get() == LocalImageLoadState.AVAILABLE }
        compose.runOnIdle { uri.value = blocked.first }
        compose.waitUntil(5_000) { blocked.second.readStarted.get() }
        compose.runOnIdle { uri.value = last.first }
        compose.waitUntil(5_000) { load.get() == LocalImageLoadState.AVAILABLE }
        blocked.second.release.countDown()
        compose.waitUntil(5_000) { blocked.second.readsClosed.get() >= 2 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(last.first).assertIsDisplayed()
        compose.onNodeWithContentDescription(blocked.first).assertDoesNotExist()
        val pixels = compose.onNodeWithContentDescription(last.first).captureToImage().toPixelMap()
        assertEquals(androidx.compose.ui.graphics.Color.Green, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test fun explicitlyReopeningCanRetryARepairedLocalFileAtTheSameUri() {
        val broken = fixture("repair", valid = false)
        var regenerations = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                AttachedImageCard(
                    image = AttachedImage("repair", AttachedImageKind.GENERATE_PROCESS,
                        description = "可恢复的本地图片", accessibilityText = "图形说明"),
                    state = AttachedImageRenderState.Ready(broken.first),
                    onRegenerate = { regenerations++ },
                )
            }
        }
        compose.onNodeWithContentDescription("展开图").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("本机读取这张配图失败", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("收起图").performClick()
        writePng(broken.second.file, Color.BLUE)
        compose.onNodeWithContentDescription("展开图").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("图形说明"))
                .fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithText("本机读取这张配图失败", substring = true).assertDoesNotExist()
        assertEquals(0, regenerations)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("图形说明"))
                .fetchSemanticsNodes().size >= 2
        }
        assertEquals(0, regenerations)
    }

    @Test fun aReadyButUndecodableImageShowsItsSummaryWithoutRegenerating() {
        val broken = fixture("broken", valid = false)
        val uri = mutableStateOf(broken.first)
        var regenerations = 0
        compose.setContent {
            MaterialTheme {
                AttachedImageCard(
                    image = AttachedImage("figure", AttachedImageKind.GENERATE_PROCESS,
                        description = "受力示意", accessibilityText = "木塞受到向上的拉力"),
                    state = AttachedImageRenderState.Ready(uri.value),
                    onRegenerate = { regenerations++ },
                )
            }
        }
        compose.onNodeWithText("过程图 · 受力示意").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("本机读取这张配图失败", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("木塞受到向上的拉力", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("重新生成").assertDoesNotExist()
        compose.onNodeWithText("过程图 · 受力示意 · 未就绪").assertIsDisplayed()
        UiAuditEvidence.capture("image-local-failure")
        compose.onNodeWithContentDescription("收起图").performClick()
        compose.onNodeWithText("过程图 · 受力示意 · 未就绪").assertIsDisplayed()
        compose.onNodeWithContentDescription("展开图").performClick()
        assertEquals(0, regenerations)
        val good = fixture("good")
        compose.runOnIdle { uri.value = good.first }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("木塞受到向上的拉力"))
                .fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithText("过程图 · 受力示意").assertIsDisplayed()
        compose.onNodeWithText("本机读取这张配图失败", substring = true).assertDoesNotExist()
        assertEquals(0, regenerations)
    }

    private fun fixture(id: String, blocked: Boolean = false, valid: Boolean = true, color: Int = Color.BLUE): Pair<String, ControlledImageProvider.Fixture> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("ui-$id-", ".png", context.cacheDir).also(files::add)
        if (valid) writePng(file, color) else file.writeText("not a decodable image")
        return ControlledImageProvider.register(id, file, blocked)
    }

    private fun writePng(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
