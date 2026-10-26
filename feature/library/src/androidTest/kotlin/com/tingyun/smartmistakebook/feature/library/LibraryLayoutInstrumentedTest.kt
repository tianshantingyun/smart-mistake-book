package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultLayoutGivesTheFirstQuestionPriorityOverFilters() {
        show()
        compose.onNodeWithTag("library_capture_button").assertIsDisplayed()
        compose.onNodeWithTag("library_export_results").assertIsDisplayed()
        compose.onNodeWithTag("library_filter_all").assertDoesNotExist()
        val item = compose.onNodeWithTag("library_item_math").fetchSemanticsNode()
        assertTrue("The default controls must leave room for a question on the first screen",
            item.boundsInRoot.top / compose.density.density < 240f)
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") == "true") {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putFloat("library_first_question_top_dp", item.boundsInRoot.top / compose.density.density)
            })
        }
    }

    @Test fun collapsedFiltersKeepTheActiveConditionVisibleAndRemovable() {
        show()
        compose.onNodeWithTag("library_filter_toggle").performClick()
        compose.onNodeWithTag("library_filter_subject_${"MATH".hashCode().toUInt()}").performClick()
        compose.onNodeWithTag("library_filter_toggle").performClick()
        compose.onNodeWithTag("library_filter_all").assertDoesNotExist()
        compose.onNodeWithTag("library_selected_subject").assertIsDisplayed().assertTextContains("数学", substring = true)
        compose.onNodeWithTag("library_item_chemistry").assertDoesNotExist()
        compose.onNodeWithTag("library_selected_subject").performClick()
        compose.onNodeWithTag("library_item_chemistry").assertIsDisplayed()
    }

    @Test fun emptySearchStillShowsRecoveryAndPageActions() {
        show()
        compose.onNodeWithTag("library_search_field").performTextInput("找不到的关键词")
        compose.onNodeWithText("未找到相关题目").assertIsDisplayed()
        compose.onNodeWithTag("library_export_visible").assertDoesNotExist()
        compose.onNodeWithTag("library_export_results").assertIsDisplayed()
        compose.onNodeWithTag("library_search_clear").performClick()
        compose.onNodeWithTag("library_item_math").assertIsDisplayed()
    }

    @Test fun narrowScreenAndLargeTypeKeepActionsReachableWithoutOverlap() {
        show(largeType = true)
        val capture = compose.onNodeWithTag("library_capture_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val results = compose.onNodeWithTag("library_export_results").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Header actions must not overlap", results.right <= capture.left)
        compose.onNodeWithTag("library_filter_toggle").performClick()
        compose.onNodeWithTag("library_facet_mastery").performScrollTo().performClick()
        compose.onNodeWithTag("library_filter_mastered").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("library_facet_favorite").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") == "true") {
            compose.captureLibraryQaScreenshot("library-layout-large-type.png")
        }
    }

    private fun show(largeType: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeType) 1.5f else density.fontScale)) {
                SmartMistakeBookTheme {
                    Box(if (largeType) Modifier.width(320.dp) else Modifier) {
                        LibraryRoute(
                            entries = listOf(entry("math", "MATH", "函数最值"), entry("chemistry", "CHEMISTRY", "化学平衡中的条件变化")),
                            onCapture = {}, onExportVisible = {}, onOpenExportResults = {}, onOpenItem = {},
                        )
                    }
                }
            }
        }
    }

    private fun entry(id: String, subject: String, title: String) = StudyCatalogEntry(
        entryId = id, problemId = "problem-$id", problemRevisionId = "revision-$id", practiceUnitId = "practice-$id",
        subject = subject, title = title, problemMarkdown = "根据题目中的条件分析并写出计算过程。",
        sourceKey = null, isCuratedExample = false, chapterLabels = emptyList(), knowledgeLabels = emptyList(),
        masteryStatus = MasteryStatus.MASTERED, nextReviewAtEpochMillis = null, retrievability = null,
    )
}
