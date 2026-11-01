package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorComposerNoticeLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var actions = 0
    private var sends = 0

    @Test fun ordinaryPhoneWidthKeepsTheReasonBesideItsAction() {
        show(360.dp, 1f)
        val reason = compose.onNodeWithTag("tutor_composer_block_reason").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("tutor_composer_action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The recovery action must remain next to its reason", reason.top < action.bottom && action.top < reason.bottom)
        assertTrue("The notice and action must not overlap", reason.right <= action.left)
        checkAction()
    }

    @Test fun narrowLargeTypeStacksTheActionWithoutClippingOrSending() {
        show(280.dp, 2f)
        val reason = compose.onNodeWithTag("tutor_composer_block_reason").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("tutor_composer_action").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("notice_host").fetchSemanticsNode().boundsInRoot
        assertTrue(reason.bottom <= action.top)
        assertTrue(action.left >= root.left && action.right <= root.right && action.bottom <= root.bottom)
        checkAction()
    }

    private fun show(width: Dp, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            SmartMistakeBookTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Column(Modifier.width(width).testTag("notice_host")) {
                        TutorSurfaceComposer("", {}, { sends += 1 },
                            block = TutorComposerAvailability(providerReady = false).block(),
                            onAction = { actions += 1 })
                    }
                }
            }
        }
    }

    private fun checkAction() {
        compose.runOnIdle { assertEquals(0, actions); assertEquals(0, sends) }
        compose.onNodeWithTag("tutor_composer_action").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(0, sends) }
    }
}
