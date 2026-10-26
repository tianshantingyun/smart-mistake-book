package com.tingyun.smartmistakebook.feature.tutor

import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.After
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class TutorKeyboardLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var fixture: File? = null
    @After fun removeFixture() { fixture?.delete() }

    @Test fun largeTextFourLinesAndAttachmentKeepActionsAboveTheRealKeyboard() {
        lateinit var activity: android.app.Activity
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        var stops = 0
        val file = File.createTempFile("keyboard-image", ".png",
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir).also { fixture = it }
        val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SmartMistakeBookTheme {
                    list = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        TutorConversationFrame(header = { Text("讲题") }, autoScrollVersion = 1,
                            liveTurn = TutorLiveTurn(answer = "正在继续讲解"),
                            liveTurnStopAction = { stops++ }, liveTurnStopTestTag = "keyboard_stop",
                            listState = list, composer = {
                                TutorChatComposer("第一行\n第二行\n第三行\n第四行", true, false, {}, {},
                                    attachmentCount = 1, attachmentPreview = {
                                        PendingMessageImagesRow(listOf(PendingMessageImage(file.toURI().toString())),
                                            {}, "keyboard_fixture")
                                    })
                            }) {
                            item { Text((1..70).joinToString("\n") { "第 $it 步，保留当前阅读位置。" }) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("tutor_chat_composer").performClick()
        compose.waitUntil(5000) {
            compose.runOnIdle { activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        }
        compose.runOnIdle { scope.launch { list.scrollToItem(0, 2000) } }
        compose.onNodeWithTag("tutor_conversation_list").performTouchInput {
            swipe(Offset(centerX, height * .35f), Offset(centerX, height * .65f), 1000)
        }
        val jumpNode = compose.onNodeWithTag("tutor_scroll_to_bottom").assertIsDisplayed()
        val jump = jumpNode.fetchSemanticsNode()
        val attachment = compose.onNodeWithTag("keyboard_fixture_pending_images").assertIsDisplayed().fetchSemanticsNode()
        val send = compose.onNodeWithTag("tutor_chat_send").assertIsDisplayed().fetchSemanticsNode()
        val keyboardTop = compose.runOnIdle {
            activity.windowManager.currentWindowMetrics.bounds.height() -
                activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        }
        assertTrue(jump.boundsInWindow.bottom <= attachment.boundsInWindow.top)
        assertTrue("Send must remain above the actual IME", send.boundsInWindow.bottom <= keyboardTop + 1f)
        UiAuditEvidence.capture("keyboard-four-lines-large-type")
        jumpNode.performClick()
        compose.onNodeWithTag("keyboard_stop").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }
}
