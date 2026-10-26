package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStatePresentationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reducedMotionShowsTheConfirmedStateWithoutWaitingForAnAnimation() {
        checkBadgeTransition(motion = false)
    }

    @Test fun anEnabledTransitionSettlesOnTheConfirmedState() {
        checkBadgeTransition(motion = true)
    }

    private fun checkBadgeTransition(motion: Boolean) {
        val mastery = mutableStateOf(MasteryState.UNKNOWN)
        compose.setContent {
            SmartMistakeBookTheme {
                CompositionLocalProvider(LocalClaudeMotionEnabled provides motion) {
                    Surface(Modifier.testTag("badge")) { LibraryMasteryBadge(mastery.value) }
                }
            }
        }
        compose.onNodeWithText("待评估").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { mastery.value = MasteryState.MASTERED }
        compose.mainClock.advanceTimeByFrame()
        if (motion) compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("已掌握").assertIsDisplayed()
        compose.onNodeWithText("待评估").assertDoesNotExist()
        val pixels = compose.onNodeWithTag("badge").captureToImage().toPixelMap()
        assertEquals(SmartColors.JadeSoft, pixels[3, pixels.height / 2])
    }

    @Test fun favoriteFeedbackWaitsForTheSavedValueAndAnimationsNeverRepeatTheWrite() {
        val mistake = mutableStateOf(question())
        val requests = mutableListOf<Boolean>()
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryQuestionCard(mistake.value, null, onClick = {}, onToggleFavorite = requests::add)
            }
        }
        compose.onNodeWithTag("library_favorite_state-example").performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未收藏"))
        compose.runOnIdle {
            assertEquals(listOf(true), requests)
            mistake.value = mistake.value.copy(isFavorite = true)
        }
        compose.onNodeWithTag("library_favorite_state-example")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收藏"))
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertEquals(listOf(true), requests) }
    }

    @Test fun largeTypeKeepsEveryStateReadableAndOmitsADuplicateSummary() {
        compose.setContent {
            SmartMistakeBookTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Surface(Modifier.width(320.dp).testTag("state_gallery")) {
                        Column(Modifier.padding(16.dp)) {
                            MasteryState.entries.forEach { LibraryMasteryBadge(it) }
                            LibraryQuestionCard(question(), null, onClick = {}, onToggleFavorite = {})
                        }
                    }
                }
            }
        }
        MasteryState.entries.forEach { compose.onAllNodesWithText(it.label)[0].assertIsDisplayed() }
        compose.onAllNodesWithText("函数最值").assertCountEquals(1)
        compose.captureLibraryQaScreenshot("library-state-gallery.png")
    }

    private fun question() = LibraryMistake(
        id = "state-example", title = "函数最值", summary = "函数最值",
        subject = SubjectKind.MATH, chapterLabels = listOf("函数"),
        mastery = MasteryState.UNKNOWN,
    )
}
