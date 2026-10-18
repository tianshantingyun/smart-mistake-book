package com.tingyun.smartmistakebook.feature.library

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.tingyun.smartmistakebook.core.domain.*
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryRepositoryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val entries = mutableStateOf(listOf(entry("one")))
    private val repository = Catalog()

    @Test fun anEmptySearchKeepsTheFieldAndClearAction() {
        show()
        compose.onNodeWithTag("library_search_field").performTextReplacement("unmatched")
        compose.waitForIdle()
        compose.onNodeWithTag("library_search_field").assertIsDisplayed()
        compose.onNodeWithTag("library_search_clear").performClick()
        compose.onNodeWithTag("library_result_count").assertTextEquals("1 道题")
    }

    @Test fun repositoryFacetOptionsCanActuallyBeSelected() {
        show()
        compose.onNodeWithTag("library_filter_subject_${"MATH".hashCode().toUInt()}").performClick()
        compose.waitForIdle()
        assertEquals("MATH", repository.lastQuery.subjectId)
    }

    @Test fun aChangedCatalogRefreshesCountsWithoutChangingTheQuery() {
        show()
        compose.runOnIdle { entries.value = entries.value + entry("two") }
        compose.waitForIdle()
        compose.onNodeWithTag("library_result_count").assertTextEquals("2 道题")
    }

    @Test fun exportCountDescribesAllCandidatesEvenWhenOnlyOneRowHasLoaded() {
        entries.value = (1..80).map { entry("entry-$it") }
        show()
        compose.onNodeWithTag("library_export_visible").assertTextContains("导出当前 80 道")
    }

    @Test fun aLateOldQueryCannotOverwriteTheCurrentCount() {
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        repository.beforeCount = { query ->
            if (query.searchText == "slow") withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                returned.complete(Unit)
            }
        }
        show()
        compose.onNodeWithTag("library_search_field").performTextReplacement("slow")
        compose.waitUntil { started.isCompleted }
        compose.onNodeWithTag("library_search_field").performTextReplacement("题目")
        compose.onNodeWithTag("library_result_count").assertTextEquals("1 道题")
        compose.runOnIdle { release.complete(Unit) }
        compose.waitUntil { returned.isCompleted }
        compose.waitForIdle()
        compose.onNodeWithTag("library_result_count").assertTextEquals("1 道题")
    }

    private fun show() {
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(entries.value, repository, onCapture = {}, onExportVisible = {},
                    onOpenExportResults = {}, onOpenItem = {})
            }
        }
        compose.waitForIdle()
    }

    private inner class Catalog : LibraryCatalogRepository {
        var lastQuery = LibraryQuery()
        var beforeCount: suspend (LibraryQuery) -> Unit = {}
        private fun matching(query: LibraryQuery) = entries.value.filter {
            (query.searchText.isBlank() || it.title.contains(query.searchText)) &&
                (query.subjectId == null || query.subjectId == it.subject)
        }
        override suspend fun totalCount(query: LibraryQuery): Int {
            beforeCount(query)
            return matching(query).size
        }
        override suspend fun facets(query: LibraryQuery, facet: LibraryFacetKind): List<LibraryFacetCount> {
            lastQuery = query
            return listOf(LibraryFacetCount("MATH", "数学", matching(query).size))
        }
        override suspend fun query(query: LibraryQuery, offset: Int, limit: Int) =
            LibraryCatalogPage(matching(query).drop(offset).take(limit).map(::item), totalCount(query), offset, limit)
        override fun pagingSource(query: LibraryQuery) = object : PagingSource<Int, LibraryCatalogItem>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LibraryCatalogItem> =
                LoadResult.Page(matching(query).take(1).map(::item), null, null)
            override fun getRefreshKey(state: PagingState<Int, LibraryCatalogItem>): Int? = null
        }
        private fun item(entry: StudyCatalogEntry) = LibraryCatalogItem(entry.entryId, entry.title,
            entry.problemMarkdown, entry.subject, emptyList(), emptyList(), "unknown", 1, 1, false, null, null)
    }

    private fun entry(id: String) = StudyCatalogEntry(id, "problem-$id", "revision-$id", "practice-$id",
        "MATH", "题目$id", "题面", null, false, emptyList(), emptyList(), MasteryStatus.UNKNOWN, null, null)
}
