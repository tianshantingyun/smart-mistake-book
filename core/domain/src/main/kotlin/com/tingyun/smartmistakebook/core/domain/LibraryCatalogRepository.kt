package com.tingyun.smartmistakebook.core.domain

import androidx.paging.PagingSource

enum class LibrarySort {
    /** 默认：最近更新在前（保持历史默认口径）。 */
    RECENTLY_UPDATED,
    RECENTLY_CREATED,
}

data class LibraryQuery(
    val searchText: String = "",
    val subjectId: String? = null,
    val sectionId: String? = null,
    val masteryId: String? = null,
    /** 「录入时间段」起止：按条目创建时间（`entry.accepted_at`）过滤，闭区间。 */
    val createdFromEpochMillis: Long? = null,
    val createdToEpochMillis: Long? = null,
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    /** C11：只看已收藏的条目（收藏是筛选的一维：分页/计数/facet/导出同一口径）。 */
    val favoriteOnly: Boolean = false,
)

data class LibraryCatalogItem(
    val entryId: String,
    val title: String,
    val summary: String,
    val subjectId: String,
    val chapterLabels: List<String>,
    val knowledgeLabels: List<String>,
    val masteryId: String,
    val updatedAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    /** C11 收藏：列表行开关的读值。 */
    val isFavorite: Boolean = false,
    val nextReviewAtEpochMillis: Long?,
    val retrievability: Double?,
) {
    init {
        require(entryId.isNotBlank()) { "Library item id must not be blank" }
        require(title.isNotBlank()) { "Library item title must not be blank" }
        require(subjectId.isNotBlank()) { "Library item subject must not be blank" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "Library item update time cannot precede creation"
        }
    }
}

data class LibraryCatalogPage(
    val items: List<LibraryCatalogItem>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
) {
    init {
        require(offset >= 0 && limit > 0) { "Library page window is invalid" }
        require(totalCount >= items.size) { "Library page cannot exceed total count" }
    }

    val hasMore: Boolean
        get() = offset + items.size < totalCount
}

data class LibraryFacetCount(
    val id: String,
    val label: String,
    val count: Int,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) {
            "Library facet option must have an id and label"
        }
        require(count >= 0) { "Library facet count must not be negative" }
    }
}

interface LibraryCatalogRepository {
    fun pagingSource(query: LibraryQuery): PagingSource<Int, LibraryCatalogItem>

    suspend fun totalCount(query: LibraryQuery): Int

    suspend fun query(
        query: LibraryQuery,
        offset: Int,
        limit: Int,
    ): LibraryCatalogPage

    suspend fun facets(
        query: LibraryQuery,
        facet: LibraryFacetKind,
    ): List<LibraryFacetCount>
}

enum class LibraryFacetKind {
    SUBJECT,
    SECTION,
    MASTERY,

    /**
     * C11 收藏：返回**一行** `favorite / 已收藏 / n`（n = 当前其余筛选下被收藏的条目数，
     * 0 也如实返回）。与其它三维同一条"facet 不带自己这一维过滤"的口径。
     */
    FAVORITE,
}
