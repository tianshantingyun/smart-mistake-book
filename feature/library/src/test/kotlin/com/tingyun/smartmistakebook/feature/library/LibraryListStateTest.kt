package com.tingyun.smartmistakebook.feature.library

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryListStateTest {
    @Test fun theOldUnknownFilterLabelAndTheNewLabelRestoreTheSameId() {
        assertEquals("unknown", MasteryState.normalizeStoredValue("暂无学习记录"))
        assertEquals("unknown", MasteryState.normalizeStoredValue("待评估"))
    }

    @Test fun unreadMetadataIsLoadingRatherThanAnEmptyResult() {
        assertEquals(LibraryListState.LOADING, libraryListState(false, LoadState.NotLoading(false), 0))
    }

    @Test fun firstPageLoadingStillShowsLoadingAfterTheCountArrives() {
        assertEquals(LibraryListState.LOADING, libraryListState(true, LoadState.Loading, 0))
    }

    @Test fun aFailedFirstPageIsAnErrorEvenBeforeMetadataArrives() {
        assertEquals(LibraryListState.ERROR, libraryListState(false, LoadState.Error(Exception()), 0))
    }

    @Test fun successfulZeroResultsCanUseTheExistingEmptyState() {
        assertEquals(LibraryListState.CONTENT, libraryListState(true, LoadState.NotLoading(true), 0))
    }

    @Test fun refreshAndRefreshFailureKeepPreviouslyLoadedRowsVisible() {
        for (refresh in listOf(LoadState.Loading, LoadState.Error(Exception()))) {
            assertEquals(LibraryListState.CONTENT, libraryListState(false, refresh, 3))
        }
    }
}
