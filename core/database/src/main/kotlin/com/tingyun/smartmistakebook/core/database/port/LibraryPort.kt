package com.tingyun.smartmistakebook.core.database.port

import androidx.paging.PagingSource
import com.tingyun.smartmistakebook.core.database.LibraryCatalogRow
import com.tingyun.smartmistakebook.core.database.LibraryFacetCountRecord

/**
 * Read-only port for library catalog operations.
 * Provides paging, counting, and faceted search.
 *
 * 筛选面（阶段 4A 批 1 · L5）：科目/板块/掌握程度 + `createdFromEpochMillis`/
 * `createdToEpochMillis`（「录入时间段」，按条目创建时间闭区间）；知识点参数已删，
 * 排序只有 `RECENTLY_UPDATED`（默认）与 `RECENTLY_CREATED`。
 */
interface LibraryReadPort {
    fun libraryPagingSource(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        /** C11：只读已收藏的条目（false = 不按收藏过滤）。 */
        favoriteOnly: Boolean = false,
    ): PagingSource<Int, LibraryCatalogRow>

    suspend fun libraryCatalogPage(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        offset: Int,
        limit: Int,
        favoriteOnly: Boolean = false,
    ): List<LibraryCatalogRow>

    suspend fun libraryCatalogCount(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        favoriteOnly: Boolean = false,
    ): Int

    suspend fun libraryCatalogFacets(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        facet: String,
        /**
         * C11：其余维度（科目/板块/掌握程度）的计数要与当前筛选同口径；`FAVORITE` 这一维
         * **不看自己**（favoriteOnly 对它无效，见 `LibraryQueryDao.favoriteFacets`）。
         */
        favoriteOnly: Boolean = false,
    ): List<LibraryFacetCountRecord>

    /**
     * FTS-backed paging source for non-blank search text. [matchQuery] is the
     * implicit-AND FTS4 MATCH expression and [tokens] are the index-aligned
     * query tokens used for relevance ranking; the implementation refreshes
     * the search projection incrementally before loading pages. Default is a
     * no-op hook so fakes without an FTS index keep compiling.
     */
    fun librarySearchPagingSource(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
        favoriteOnly: Boolean = false,
    ): PagingSource<Int, LibraryCatalogRow> =
        error("FTS library search is not backed by this database port")

    /** Counting twin of [librarySearchPagingSource]. */
    suspend fun librarySearchCount(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        favoriteOnly: Boolean = false,
    ): Int = error("FTS library search is not backed by this database port")

    /** Offset-paged twin of [librarySearchPagingSource] with identical ranking. */
    suspend fun librarySearchPage(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
        offset: Int,
        limit: Int,
        favoriteOnly: Boolean = false,
    ): List<LibraryCatalogRow> = error("FTS library search is not backed by this database port")

    /**
     * FTS-scoped facet counts ([facet] mirrors LibraryQueryDao facet kinds; v67 起多一个
     * `FAVORITE`——收藏这一维的计数同样不带自己的过滤，见 `LibraryQueryDao.favoriteFacets`)。
     */
    suspend fun librarySearchFacets(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        facet: String,
        favoriteOnly: Boolean = false,
    ): List<LibraryFacetCountRecord> =
        error("FTS library search is not backed by this database port")

    /**
     * Drains the library search outbox and applies pending revisions to the
     * FTS projection row by row. Safe to call repeatedly; incremental by
     * design (a full rebuild happens only as a repair).
     */
    suspend fun refreshLibrarySearchProjection() {
        // No-op default for fakes without an FTS index.
    }
}
