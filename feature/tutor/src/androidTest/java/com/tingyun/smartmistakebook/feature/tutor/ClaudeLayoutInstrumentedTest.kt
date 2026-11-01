package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaudeLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composerToolsSitBelowTheFullWidthWritingArea() {
        var sends = 0
        compose.setContent {
            SmartMistakeBookTheme {
                TutorChatComposer("这一步为什么要这样变形？", true, false, {}, { sends++ }, onOpenAttachMenu = {},
                    tools = { TutorInteractionModeSwitch(TutorInteractionMode.NORMAL, {}) })
            }
        }
        val field = compose.onNodeWithTag("tutor_chat_composer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val attach = compose.onNodeWithTag("tutor_chat_attach").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("tutor_chat_send").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Tools must sit below the writing area", attach.top >= field.bottom && send.top >= field.bottom)
        assertTrue("The field must retain the width occupied by both tools", field.left <= attach.left && field.right >= send.right)
        compose.onNodeWithTag("tutor_chat_send").performClick()
        compose.runOnIdle { assertEquals(1, sends) }
        UiAuditEvidence.capture("claude-composer-structure")
    }

    @Test fun theModeSelectorOpensAChoiceAndOnlyChangesWhenADifferentModeIsChosen() {
        var switches = 0
        compose.setContent {
            SmartMistakeBookTheme { TutorInteractionModeSwitch(TutorInteractionMode.NORMAL, { switches++ }) }
        }
        compose.onNodeWithTag(TUTOR_MODE_SWITCH_TAG).performClick()
        compose.onNodeWithTag("tutor_mode_option_normal").performClick()
        compose.runOnIdle { assertEquals(0, switches) }
        compose.onNodeWithTag(TUTOR_MODE_SWITCH_TAG).performClick()
        compose.onNodeWithTag("tutor_mode_option_guided").performClick()
        compose.runOnIdle { assertEquals(1, switches) }
    }
}
