package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryAdaptiveActionsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTypeKeepsTheTitleReadableAndBothActionsIndependent() {
        var captures = 0
        var results = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SmartMistakeBookTheme {
                    Box(Modifier.width(288.dp)) {
                        LibraryPageHeader(true, { captures++ }, { results++ })
                    }
                }
            }
        }
        val title = compose.onNodeWithText("错题本").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("A short page title must fit one large-text line", title.height / compose.density.density <= 70f)
        val capture = compose.onNodeWithTag("library_capture_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val files = compose.onNodeWithTag("library_export_results").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("At large text, actions must give the page title its own reading line", title.bottom <= files.top)
        assertTrue(files.right <= capture.left)
        compose.onNodeWithTag("library_capture_button").performClick()
        compose.onNodeWithTag("library_export_results").performClick()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(1, results) }
    }
}
