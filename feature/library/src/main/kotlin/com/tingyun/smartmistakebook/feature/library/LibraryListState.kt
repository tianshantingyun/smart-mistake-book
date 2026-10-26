package com.tingyun.smartmistakebook.feature.library

import androidx.paging.LoadState

internal enum class LibraryListState { CONTENT, LOADING, ERROR }

/** Keep loaded rows usable while refreshing; an unread result is never an empty result. */
internal fun libraryListState(
    metadataLoaded: Boolean,
    refresh: LoadState?,
    loadedItemCount: Int,
): LibraryListState = when {
    loadedItemCount > 0 -> LibraryListState.CONTENT
    refresh is LoadState.Error -> LibraryListState.ERROR
    !metadataLoaded || refresh is LoadState.Loading -> LibraryListState.LOADING
    else -> LibraryListState.CONTENT
}
