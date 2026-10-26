package com.tingyun.smartmistakebook.feature.tutor

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorComposerInteractionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aComposingCandidateIsNotSentAsACommand() {
        val draft = mutableStateOf("草稿")
        var sent = 0
        compose.setContent {
            SmartMistakeBookTheme {
                TutorChatComposer(draft.value, enabled = true, sending = false,
                    onValueChange = { draft.value = it }, onSend = { sent++ })
            }
        }
        compose.onNodeWithTag("tutor_chat_composer").performClick()
        lateinit var connection: InputConnection
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            connection = checkNotNull(activity.window.decorView.findFocus()?.onCreateInputConnection(EditorInfo()))
            check(connection.setComposingText("你", 1))
            // Dispatch while composition is active. The installed IME may finish it on a later frame.
            connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Composing confirmation must not send the message", 0, sent) }
        compose.runOnIdle { connection.finishComposingText() }
        compose.waitForIdle()
        compose.runOnIdle { connection.performEditorAction(EditorInfo.IME_ACTION_SEND) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, sent) }
    }

    @Test fun imageOnlyMessageStillUsesTheExistingSendCallback() {
        var sent = 0
        compose.setContent {
            SmartMistakeBookTheme {
                TutorChatComposer("", enabled = true, sending = false, onValueChange = {},
                    onSend = { sent++ }, attachmentCount = 1)
            }
        }
        compose.onNodeWithTag("tutor_chat_send").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sent) }
        UiAuditEvidence.capture("composer-image-only")
    }

    @Test fun aCommittedCandidateCanSendBeforeTheNextRecomposition() {
        val draft = mutableStateOf("")
        var sent = 0
        compose.setContent {
            SmartMistakeBookTheme {
                TutorChatComposer(draft.value, true, false, { draft.value = it }, { sent++ })
            }
        }
        compose.onNodeWithTag("tutor_chat_composer").performClick()
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val connection = checkNotNull(activity.window.decorView.findFocus()?.onCreateInputConnection(EditorInfo()))
            connection.setComposingText("你", 1)
            connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
            assertEquals(0, sent)
            connection.finishComposingText()
            connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
            assertEquals("Committed input can send without waiting for a rendering frame", 1, sent)
        }
    }
}
