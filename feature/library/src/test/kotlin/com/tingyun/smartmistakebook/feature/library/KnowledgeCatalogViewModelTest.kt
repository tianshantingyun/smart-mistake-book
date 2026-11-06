package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.KnowledgeCatalogRepository
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KnowledgeCatalogViewModelTest {
    @Test fun latePreviousQueryCannotReplaceTheCurrentResults() = runTest {
        val old = CompletableDeferred<CatalogPage>()
        val repo = FakeCatalog { query ->
            if (query.text == "A") withContext(NonCancellable) { old.await() } else page(query.text)
        }
        val vm = KnowledgeCatalogViewModel(repo, scope = backgroundScope)
        vm.setQuery("A"); advanceTimeBy(251); runCurrent()
        vm.setQuery("B"); advanceTimeBy(251); runCurrent()
        old.complete(page("A")); runCurrent()
        assertEquals("B", vm.state.value.query)
        assertEquals(listOf("B"), vm.state.value.hits.map { it.name })
    }

    @Test fun paginationDeduplicatesRequestsAndIgnoresOldPagesAfterQueryChange() = runTest {
        val next = CompletableDeferred<CatalogPage>()
        var nextCalls = 0
        val repo = FakeCatalog { query ->
            if (query.cursor != null) { nextCalls++; withContext(NonCancellable) { next.await() } }
            else page(query.text, hasNext = true)
        }
        val vm = KnowledgeCatalogViewModel(repo, scope = backgroundScope)
        advanceTimeBy(251); runCurrent()
        vm.loadNext(); vm.loadNext(); runCurrent()
        assertEquals(1, nextCalls)
        vm.setQuery("new"); advanceTimeBy(251); runCurrent()
        next.complete(page("old-page")); runCurrent()
        assertEquals(listOf("new"), vm.state.value.hits.map { it.name })
    }

    @Test fun catalogUpdatesRestartPagingAndPreserveTheChosenIdentity() = runTest {
        var calls = 0
        val repo = FakeCatalog { query -> calls++; page(query.text, hasNext = true) }
        val vm = KnowledgeCatalogViewModel(repo, scope = backgroundScope)
        advanceTimeBy(251); runCurrent()
        vm.toggle(hit("chosen"))
        repo.changes.emit(Unit); runCurrent()
        assertNull(vm.state.value.next)
        advanceTimeBy(251); runCurrent()
        assertEquals(2, calls)
        assertEquals(listOf("chosen"), vm.state.value.selected.map { it.node.nodeId })
    }

    @Test fun failureCanReturnToBrowseAndReadinessNeverTriggersAPrematureRead() = runTest {
        var calls = 0
        val repo = FakeCatalog { query -> calls++; if (query.text == "bad") error("offline") else page(query.text) }
        val vm = KnowledgeCatalogViewModel(repo, available = false, scope = backgroundScope)
        advanceTimeBy(251); runCurrent(); assertEquals(0, calls)
        vm.setAvailable(true); vm.setQuery("bad"); advanceTimeBy(251); runCurrent()
        assertNotNull(vm.state.value.error)
        vm.setQuery(""); advanceTimeBy(251); runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.hits.size)
    }

    @Test fun staleCursorRestartsTheQueryWithoutLosingSelections() = runTest {
        var initialCalls = 0
        val repo = FakeCatalog { query ->
            if (query.cursor != null) throw com.tingyun.smartmistakebook.core.domain.StaleCatalogCursorException()
            initialCalls++; page(query.text, hasNext = true)
        }
        val vm = KnowledgeCatalogViewModel(repo, scope = backgroundScope)
        advanceTimeBy(251); runCurrent()
        vm.toggle(hit("chosen")); vm.loadNext(); runCurrent()
        assertNull(vm.state.value.next)
        assertEquals("知识目录已更新", vm.state.value.notice)
        advanceTimeBy(251); runCurrent()
        assertEquals(2, initialCalls)
        assertEquals(listOf("chosen"), vm.state.value.selected.map { it.node.nodeId })
    }

    @Test fun overlappingPagesMergeByNodeIdentity() = runTest {
        val repo = FakeCatalog { query ->
            if (query.cursor == null) page("one", hasNext = true)
            else CatalogPage(listOf(hit("one"), hit("two")), null, "r1")
        }
        val vm = KnowledgeCatalogViewModel(repo, scope = backgroundScope)
        advanceTimeBy(251); runCurrent()
        vm.loadNext(); runCurrent()
        assertEquals(listOf("one", "two"), vm.state.value.hits.map { it.node.nodeId })
        assertNull(vm.state.value.next)
    }

    private class FakeCatalog(val search: suspend (CatalogQuery) -> CatalogPage) : KnowledgeCatalogRepository {
        val changes = MutableSharedFlow<Unit>()
        override suspend fun search(query: CatalogQuery) = search.invoke(query)
        override fun observeChanges() = changes
    }
    private fun hit(id: String) = CatalogHit(KnowledgeNodeSelection(id, "v1"), "MATH", listOf("函数"), id, "完整材料", listOf("函数"))
    private fun page(id: String, hasNext: Boolean = false) = CatalogPage(listOf(hit(id.ifEmpty { "browse" })),
        if (hasNext) CatalogCursor("a".repeat(64), "r1", "key") else null, "r1")
}
