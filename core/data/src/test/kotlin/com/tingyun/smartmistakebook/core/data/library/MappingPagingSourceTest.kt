package com.tingyun.smartmistakebook.core.data.library

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MappingPagingSourceTest {
    @Test fun invalidationReachesThePagerAndReleasesTheDelegate() {
        val source = Source()
        val mapped = MappingPagingSource(source) { it.length }
        source.invalidate()
        assertTrue("Database changes must invalidate the visible source", mapped.invalid)
        val other = Source()
        MappingPagingSource(other) { it.length }.invalidate()
        assertTrue("Discarding a wrapper must release its delegate", other.invalid)
    }

    @Test fun pageCountsSurviveMapping() = runBlocking {
        val page = MappingPagingSource(Source()) { it.length }
            .load(PagingSource.LoadParams.Refresh(null, 1, true)) as PagingSource.LoadResult.Page
        assertEquals(listOf(3), page.data)
        assertEquals(12, page.itemsBefore)
        assertEquals(87, page.itemsAfter)
    }

    private class Source : PagingSource<Int, String>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> =
            LoadResult.Page(listOf("row"), 0, 2, itemsBefore = 12, itemsAfter = 87)
        override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
    }
}
