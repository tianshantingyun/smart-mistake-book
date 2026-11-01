package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorLongReplyScrollInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val body = mutableStateOf((1..70).joinToString("\n") { "第 $it 步：根据题目条件推导。" })
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun showReply() {
        compose.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            SmartMistakeBookTheme {
                TutorConversationFrame(
                    header = { Text("讲题") },
                    autoScrollVersion = body.value,
                    listState = listState,
                    composer = { Box(Modifier.fillMaxWidth().height(180.dp).testTag("tall_composer")) },
                ) {
                    item("long_reply") {
                        Column {
                            Text(body.value)
                            Text("讲解末尾", Modifier.testTag("actual_tail"))
                        }
                    }
                }
            }
        }
    }

    @Test fun anInitialTallReplyReachesItsActualTail() {
        showReply()
        compose.onNodeWithTag("actual_tail").assertIsDisplayed()
    }

    @Test fun aNewConversationStartsAtTheBeginningOfItsWelcome() {
        compose.setContent {
            SmartMistakeBookTheme {
                TutorConversationScreen(
                    config = TutorSurfaceConfig(header = { Text("讲题") }, emptyState = {
                        Column {
                            Text("先告诉我你卡在哪里", Modifier.testTag("welcome_start"))
                            Text(body.value)
                        }
                    }),
                    composer = { Box(Modifier.fillMaxWidth().height(180.dp)) },
                    emptyStateVisible = true,
                ) {}
            }
        }
        compose.onNodeWithTag("welcome_start").assertIsDisplayed()
    }

    @Test fun streamingDoesNotStealReadingWithinOneTallReply() {
        showReply()
        compose.runOnIdle { scope.launch { listState.scrollToItem(0, 3000) } }
        compose.waitForIdle()
        compose.onNodeWithTag("tutor_conversation_list").performTouchInput {
            swipe(Offset(centerX, height * 0.35f), Offset(centerX, height * 0.65f), durationMillis = 1000)
        }
        compose.waitForIdle()
        val before = compose.runOnIdle { listState.firstVisibleItemScrollOffset }
        assertTrue("The fixture must be inside the long final item", before > 0)
        compose.runOnIdle { body.value += "\n继续追加的一步。" }
        compose.waitForIdle()
        assertEquals("Streaming must preserve the student's reading anchor", before,
            compose.runOnIdle { listState.firstVisibleItemScrollOffset })
        val jump = compose.onNodeWithTag("tutor_scroll_to_bottom").assertIsDisplayed()
        val jumpBounds = jump.fetchSemanticsNode().boundsInRoot
        val composerBounds = compose.onNodeWithTag("tall_composer").fetchSemanticsNode().boundsInRoot
        assertTrue("The jump action must stay above the measured composer", jumpBounds.bottom <= composerBounds.top)
        jump.performClick()
        compose.onNodeWithTag("actual_tail").assertIsDisplayed()
        UiAuditEvidence.capture("long-reply-reading")
    }
}
