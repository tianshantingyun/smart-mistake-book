package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorMistakePickerUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clearingAnEmptySearchRestoresCandidatesWithoutPickingOrDismissing() {
        val entries = listOf(entry("one", "函数的单调性"), entry("two", "自由落体"))
        val picks = mutableListOf<StudyCatalogEntry>()
        var dismissals = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                TutorMistakePickerDialog(entries, picks::add, { dismissals++ }, "audit")
            }
        }
        compose.onNodeWithTag("audit_picker_query").performTextInput("no such question")
        compose.onNodeWithTag("audit_picker_empty").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("audit_picker_empty").assertIsDisplayed()
        compose.onNodeWithTag("audit_picker_clear_query").performClick()
        compose.onNodeWithTag("audit_picker_item_one").assertIsDisplayed()
        compose.onNodeWithTag("audit_picker_item_two").assertIsDisplayed()
        compose.onNodeWithTag("audit_picker_clear_query").assertDoesNotExist()
        assertEquals(0, picks.size)
        assertEquals(0, dismissals)
        UiAuditEvidence.capture("picker-cleared")
        compose.onNodeWithTag("audit_picker_item_two").performClick()
        assertEquals(listOf(entries[1]), picks)
        compose.onNodeWithTag("audit_picker_cancel").performClick()
        assertEquals(1, dismissals)
    }

    @Test fun longCandidateMetadataWrapsInsteadOfClippingToOneLine() {
        val label = "机械运动与物理模型的建立及匀变速直线运动规律的综合应用"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MaterialTheme {
                    TutorMistakePickerDialog(listOf(entry("long", "运动综合题", label)), {}, {}, "audit")
                }
            }
        }
        compose.onNodeWithText("PHYSICS · $label", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { read ->
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(read(result))
            assertTrue("完整标签应换行", result.single().lineCount > 1)
            assertFalse("不应裁掉标签内容", result.single().hasVisualOverflow)
        }
        UiAuditEvidence.capture("picker-long-label-font150")
    }

    private fun entry(id: String, title: String, chapter: String = "运动学") = StudyCatalogEntry(
        entryId = "entry-$id", problemId = id, problemRevisionId = "$id-rev-1",
        practiceUnitId = "unit-$id", subject = "PHYSICS", title = title,
        problemMarkdown = "题干", sourceKey = null, isCuratedExample = false,
        chapterLabels = listOf(chapter), knowledgeLabels = emptyList(),
        nextReviewAtEpochMillis = null, retrievability = null,
    )
}
