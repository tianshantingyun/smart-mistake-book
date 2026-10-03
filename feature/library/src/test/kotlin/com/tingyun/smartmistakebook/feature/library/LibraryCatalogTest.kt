package com.tingyun.smartmistakebook.feature.library

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.LibrarySort
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录层（facets 层级收敛 + 两值排序 + 「录入时间段」）的 JVM 行为契约。
 *
 * 阶段 4A 批 1 · L5 的断言处置（不静默丢）：
 * - 「知识点筛选层」相关用例（按知识点过滤、知识点 facet 选项、层级收敛中的知识点一环）
 *   随该筛选层一起退场（裁定 `docs/agent-first-refactor-decisions-2026-09-23.md:1066`）；
 * - 新增 `libraryTimeRangeTurnsPresetsIntoAbsoluteCreationBounds` 与
 *   `timeRangeAndSortAreUiStateAndClearableFilters` 覆盖「录入时间段」的起止换算、
 *   默认排序与清除筛选语义。
 */
class LibraryCatalogTest {
    private val mistakes = listOf(
        mistake(
            id = "derivative",
            title = "导数与函数单调性",
            summary = "闭区间最值",
            mastery = MasteryState.LEARNING,
            chapter = "函数",
            knowledge = listOf("导数", "函数单调性"),
        ),
        mistake(
            id = "geometry",
            title = "导数的几何意义",
            summary = "连续答对 3 次",
            mastery = MasteryState.MASTERED,
            chapter = "函数",
            knowledge = listOf("导数几何意义"),
        ),
        mistake(
            id = "chemistry",
            title = "化学平衡移动判断",
            summary = "浓度商",
            mastery = MasteryState.MASTERED,
            subject = SubjectKind.CHEMISTRY,
            chapter = "化学平衡",
            knowledge = listOf("浓度商"),
        ),
    )
    private val catalog = LibraryCatalog(mistakes)

    @Test
    fun userVisibleFacetsOnlyContainContentHierarchyAndMastery() {
        // 阶段 4A 批 1 · L5：知识点筛选层随 L5 裁定删除（`docs/agent-first-refactor-decisions-2026-09-23.md:1066`），
        // 一层筛选 = 科目 → 板块；知识点标签仍随条目展示与全文搜索，只是不再是一层筛选。
        assertEquals(
            listOf(
                LibraryFacet.SUBJECT,
                LibraryFacet.CHAPTER,
                LibraryFacet.MASTERY,
            ),
            LibraryFacet.entries,
        )
        assertEquals(listOf("科目", "板块", "掌握程度"), LibraryFacet.entries.map { it.label })
    }

    @Test
    fun unfilteredCatalogReusesStableBackingList() {
        val result = catalog.filter(query = "", selections = LibrarySelections())

        assertSame(mistakes, result)
    }

    @Test
    fun emptyStateDistinguishesANewLibraryFromFilteredResults() {
        val newLibrary = resolveLibraryEmptyState(
            totalMistakeCount = 0,
            visibleMistakeCount = 0,
            hasActiveSearch = false,
        )
        val filteredResults = resolveLibraryEmptyState(
            totalMistakeCount = mistakes.size,
            visibleMistakeCount = 0,
            hasActiveSearch = false,
        )
        val searchResults = resolveLibraryEmptyState(
            totalMistakeCount = mistakes.size,
            visibleMistakeCount = 0,
            hasActiveSearch = true,
        )

        assertEquals(LibraryEmptyState.CATALOG_EMPTY, newLibrary)
        assertEquals("还没有错题", newLibrary?.title)
        assertEquals("拍照或上传第一道错题，之后会自动整理到这里。", newLibrary?.supportingText)
        assertEquals(LibraryEmptyState.FILTERED_EMPTY, filteredResults)
        assertEquals("当前筛选下没有错题", filteredResults?.title)
        assertEquals(LibraryEmptyState.SEARCH_EMPTY, searchResults)
        assertEquals("没找到相关内容", searchResults?.title)
        assertNull(
            resolveLibraryEmptyState(
                totalMistakeCount = mistakes.size,
                visibleMistakeCount = mistakes.size,
                hasActiveSearch = false,
            ),
        )
    }

    @Test
    fun searchAndTypedMasteryFilterKeepExistingBehavior() {
        val searchResult = catalog.filter(query = "3", selections = LibrarySelections())
        val masteredResult = catalog.filter(
            query = "",
            selections = LibrarySelections(mastery = MasteryState.MASTERED.id),
        )

        assertEquals(setOf("geometry"), searchResult.mapTo(mutableSetOf(), LibraryMistake::id))
        assertEquals(
            setOf("geometry", "chemistry"),
            masteredResult.mapTo(mutableSetOf(), LibraryMistake::id),
        )
        assertEquals("已掌握", MasteryState.MASTERED.label)
    }

    @Test
    fun facetSwitchDoesNotRecomputeVisibleResults() {
        val viewModel = LibraryViewModel(SavedStateHandle())
        viewModel.updateCatalog(mistakes)
        val initialResults = viewModel.uiState.visibleMistakes

        viewModel.selectFacet(LibraryFacet.MASTERY)

        assertSame(initialResults, viewModel.uiState.visibleMistakes)
        assertTrue(viewModel.uiState.activeOptions.isNotEmpty())
    }

    @Test
    fun legacyMasteryLabelRestoresAsStableId() {
        val viewModel = LibraryViewModel(
            SavedStateHandle(mapOf("library_filter_mastery" to "已掌握")),
        )
        viewModel.updateCatalog(mistakes)

        assertEquals(MasteryState.MASTERED.id, viewModel.uiState.selections.mastery)
        assertEquals(2, viewModel.uiState.visibleMistakes.size)
    }

    @Test
    fun subjectAndChapterNarrowTheNextHierarchyLevel() {
        val mathSelection = LibrarySelections(subject = SubjectKind.MATH.name)
        assertEquals(
            listOf("函数"),
            catalog.optionsFor(LibraryFacet.CHAPTER, mathSelection).map { it.id },
        )

        val chemistrySelection = LibrarySelections(subject = SubjectKind.CHEMISTRY.name)
        assertEquals(
            listOf("化学平衡"),
            catalog.optionsFor(LibraryFacet.CHAPTER, chemistrySelection).map { it.id },
        )
    }

    @Test
    fun changingSubjectClearsDownstreamHierarchySelections() {
        val viewModel = LibraryViewModel(SavedStateHandle())
        viewModel.updateCatalog(mistakes)
        viewModel.toggleFilter(LibraryFacet.SUBJECT, SubjectKind.MATH.name)
        viewModel.toggleFilter(LibraryFacet.CHAPTER, "函数")

        viewModel.toggleFilter(LibraryFacet.SUBJECT, SubjectKind.CHEMISTRY.name)

        assertEquals(SubjectKind.CHEMISTRY.name, viewModel.uiState.selections.subject)
        assertNull(viewModel.uiState.selections.chapter)
        assertEquals(listOf("chemistry"), viewModel.uiState.visibleMistakes.map { it.id })
    }

    @Test
    fun libraryTimeRangeTurnsPresetsIntoAbsoluteCreationBounds() {
        val now = 1_700_000_000_000L
        assertNull(LibraryTimeRange.ALL.createdFromEpochMillis(now))
        assertEquals(
            now - 7 * LibraryTimeRange.MILLIS_PER_DAY,
            LibraryTimeRange.LAST_7_DAYS.createdFromEpochMillis(now),
        )
        assertEquals(
            now - 30 * LibraryTimeRange.MILLIS_PER_DAY,
            LibraryTimeRange.LAST_30_DAYS.createdFromEpochMillis(now),
        )
        assertEquals(
            now - 90 * LibraryTimeRange.MILLIS_PER_DAY,
            LibraryTimeRange.LAST_90_DAYS.createdFromEpochMillis(now),
        )
        // 复核 2026-10-03：上界生产化——「近 N 天」是闭区间 [now−N 天, now]，ALL 无上界。
        assertNull(LibraryTimeRange.ALL.createdToEpochMillis(now))
        assertEquals(now, LibraryTimeRange.LAST_7_DAYS.createdToEpochMillis(now))
        assertEquals(now, LibraryTimeRange.LAST_30_DAYS.createdToEpochMillis(now))
        assertEquals(now, LibraryTimeRange.LAST_90_DAYS.createdToEpochMillis(now))
        assertEquals(LibraryTimeRange.ALL, LibraryTimeRange.fromId(null))
        assertEquals(LibraryTimeRange.LAST_7_DAYS, LibraryTimeRange.fromId("last7"))
        assertEquals(LibraryTimeRange.ALL, LibraryTimeRange.fromId("no-such-range"))
    }

    @Test
    fun timeRangeAndSortAreUiStateAndClearableFilters() {
        val viewModel = LibraryViewModel(SavedStateHandle())
        viewModel.updateCatalog(mistakes)

        assertEquals(LibraryTimeRange.ALL, viewModel.uiState.timeRange)
        assertEquals(LibrarySort.RECENTLY_UPDATED, viewModel.uiState.sort)
        assertFalse(viewModel.uiState.hasActiveFilters)

        viewModel.selectTimeRange(LibraryTimeRange.LAST_30_DAYS)
        assertEquals(LibraryTimeRange.LAST_30_DAYS, viewModel.uiState.timeRange)
        assertTrue(viewModel.uiState.hasActiveFilters)

        viewModel.selectSort(LibrarySort.RECENTLY_CREATED)
        assertEquals(LibrarySort.RECENTLY_CREATED, viewModel.uiState.sort)

        viewModel.clearAll()
        assertEquals(LibraryTimeRange.ALL, viewModel.uiState.timeRange)
        assertFalse(viewModel.uiState.hasActiveFilters)
        // 排序不是筛选：清除筛选不回退排序选择。
        assertEquals(LibrarySort.RECENTLY_CREATED, viewModel.uiState.sort)
    }

    @Test
    fun missingClassificationDoesNotCreatePlaceholderFilters() {
        val entry = StudyCatalogEntry(
            entryId = "legacy",
            problemId = "problem-legacy",
            problemRevisionId = "revision-legacy",
            practiceUnitId = "practice-legacy",
            subject = SubjectKind.MATH.name,
            title = "函数题",
            problemMarkdown = "求函数值。",
            sourceKey = "capture:legacy",
            isCuratedExample = false,
            masteryStatus = MasteryStatus.UNKNOWN,
            nextReviewAtEpochMillis = null,
            retrievability = null,
        ).toLibraryMistake()
        val legacyCatalog = LibraryCatalog(listOf(entry))

        listOf("本机题库", "证据不足").forEach { placeholder ->
            assertTrue(legacyCatalog.filter(placeholder, LibrarySelections()).isEmpty())
        }
        assertEquals("数学", entry.contentPath)
        assertEquals("暂无学习记录", entry.mastery.label)
        assertTrue(legacyCatalog.optionsFor(LibraryFacet.CHAPTER).isEmpty())
    }

    @Test
    fun generalSubjectUsesTheSharedStudentLabel() {
        val entry = mistake(
            id = "general",
            title = "综合实践题",
            summary = "根据材料完成建模。",
            mastery = MasteryState.UNKNOWN,
            subject = SubjectKind.GENERAL,
            chapter = "综合实践",
            knowledge = listOf("建模"),
        )

        assertEquals("综合 › 综合实践 › 建模", entry.contentPath)
    }

    private fun mistake(
        id: String,
        title: String,
        summary: String,
        mastery: MasteryState,
        subject: SubjectKind = SubjectKind.MATH,
        chapter: String,
        knowledge: List<String>,
    ) = LibraryMistake(
        id = id,
        title = title,
        summary = summary,
        subject = subject,
        chapterLabels = listOf(chapter),
        knowledgeLabels = knowledge,
        mastery = mastery,
    )
}
