package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MistakeSectionCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theHeaderAndBodyAreActuallyPaintedAsDifferentRegions() {
        compose.setContent {
            SmartMistakeBookTheme {
                MistakeSectionCard("题面", Modifier.width(320.dp).testTag("section")) {
                    Text("已保存的题面内容")
                }
            }
        }
        val pixels = compose.onNodeWithTag("section").captureToImage().toPixelMap()
        val density = compose.density.density
        assertNotEquals(pixels[(4 * density).toInt(), (20 * density).toInt()],
            pixels[(4 * density).toInt(), (60 * density).toInt()])
    }

    @Test fun aCollapsedModuleKeepsItsLargeTypeActionInsideAndDoesNotRevealTheBody() {
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            SmartMistakeBookTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    MistakeSectionCard("讲解图", Modifier.width(280.dp).testTag("section"), expanded = false,
                        action = { OutlineActionChip("查看3张讲解图", { clicks += 1 }, Modifier.testTag("action")) }) {
                        Text("尚未展开的内容")
                    }
                }
            }
        }
        compose.onNodeWithText("尚未展开的内容").assertDoesNotExist()
        val section = compose.onNodeWithTag("section").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("action").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= section.left && action.right <= section.right && action.bottom <= section.bottom)
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithTag("action").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
