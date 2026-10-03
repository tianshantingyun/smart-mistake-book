package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBatchExportEntryInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportUsesCurrentFilteredOrderWithoutPerQuestionSelection() {
        var candidates: LibraryExportCandidates? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(
                    entries = listOf(
                        entry("math-1", SubjectKind.MATH.name, "导数题"),
                        entry("chemistry-1", SubjectKind.CHEMISTRY.name, "平衡题"),
                        entry("math-2", SubjectKind.MATH.name, "函数题"),
                    ),
                    onCapture = {},
                    onExportVisible = { candidates = it },
                    onOpenExportResults = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag(
            "library_filter_subject_${SubjectKind.MATH.name.hashCode().toUInt()}",
        ).performClick()
        composeRule.onNodeWithTag("library_export_visible").performClick()

        composeRule.runOnIdle {
            // 三态里的候选态：上限内的当前筛选顺序原样交给入队（L7 后台导出）。
            assertEquals(
                LibraryExportCandidates.Candidates(listOf("math-1", "math-2")),
                candidates,
            )
        }
    }

    @Test
    fun emptyLibraryDoesNotShowAnExportActionButKeepsTheResultsEntry() {
        var exportResultsOpened = false
        composeRule.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(
                    entries = emptyList(),
                    onCapture = {},
                    onExportVisible = {},
                    onOpenExportResults = { exportResultsOpened = true },
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_export_visible").assertDoesNotExist()
        // 「导出成果」是常驻入口（通知未授权时的退化落点），空库也必须在。
        composeRule.onNodeWithTag("library_export_results").performClick()
        composeRule.runOnIdle { assertEquals(true, exportResultsOpened) }
    }

    private fun entry(
        id: String,
        subject: String,
        title: String,
    ) = StudyCatalogEntry(
        entryId = id,
        problemId = "problem-$id",
        problemRevisionId = "revision-$id",
        practiceUnitId = "practice-$id",
        subject = subject,
        title = title,
        problemMarkdown = "$title 的题面",
        sourceKey = null,
        isCuratedExample = false,
        chapterLabels = listOf("板块"),
        knowledgeLabels = listOf("知识点"),
        masteryStatus = MasteryStatus.LEARNING,
        nextReviewAtEpochMillis = null,
        retrievability = null,
    )
}
