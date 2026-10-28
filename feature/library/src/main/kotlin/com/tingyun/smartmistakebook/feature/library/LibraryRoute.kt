package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tingyun.smartmistakebook.core.ui.ClaudeDisclosure as AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import com.tingyun.smartmistakebook.core.domain.ArchivedMistakeRef
import com.tingyun.smartmistakebook.core.domain.LibrarySort
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.LibraryCatalogRepository
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageLazyColumn
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.ClaudeSegment
import com.tingyun.smartmistakebook.core.ui.ClaudeSegmentedControl
import com.tingyun.smartmistakebook.core.ui.ClaudeMotion
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
import com.tingyun.smartmistakebook.core.ui.PageState
import com.tingyun.smartmistakebook.core.ui.PageStateFrame
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    entries: List<StudyCatalogEntry>,
    catalogRepository: LibraryCatalogRepository? = null,
    mistakeDetailRepository: MistakeDetailRepository? = null,
    /**
     * 待处理录入任务中需要学生继续的数量（PRD-06 空态半边；只读）。
     * 来源建议：`CaptureWorkflowRepository.observePendingCaptures()` 里
     * `SOURCE_UNAVAILABLE / RECAPTURE_REQUIRED / RETRY_OR_MANUAL` 的条数。
     */
    pendingFailedTaskCount: Int = 0,
    /**
     * 继续处理待处理任务。I1 接线建议：
     * `{ navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY)) }`——录入页同页展示
     * 待处理列表，不要求重新拍照。null = 未接线，不渲染入口。
     */
    onContinuePendingTasks: (() -> Unit)? = null,
    onCapture: () -> Unit,
    onExportVisible: (LibraryExportCandidates) -> Unit,
    onOpenExportResults: () -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryViewModel: LibraryViewModel = viewModel()
    LaunchedEffect(catalogRepository, entries) {
        if (catalogRepository != null) {
            libraryViewModel.bindRepository(catalogRepository)
        } else {
            libraryViewModel.updateCatalog(entries.map(StudyCatalogEntry::toLibraryMistake))
        }
    }
    // C11：收藏开关复用详情仓储的行级更新（与详情页同一列 `error_book_entry.is_favorite`，
    // 只有一条事实源）。没接仓储的入口（预览/替身）开关不可用——界面如实置灰。
    LaunchedEffect(mistakeDetailRepository) {
        libraryViewModel.bindFavoriteWriter(
            mistakeDetailRepository?.let { repository ->
                LibraryFavoriteWriter { entryId, isFavorite ->
                    repository.setFavorite(
                        entryId = entryId,
                        isFavorite = isFavorite,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
                }
            },
        )
    }
    LibraryContent(
        mistakeCount = entries.size,
        usePaging = catalogRepository != null,
        mistakeDetailRepository = mistakeDetailRepository,
        pendingFailedTaskCount = pendingFailedTaskCount,
        onContinuePendingTasks = onContinuePendingTasks,
        onCapture = onCapture,
        onExportVisible = onExportVisible,
        onOpenExportResults = onOpenExportResults,
        onOpenItem = onOpenItem,
        onToggleFavorite = libraryViewModel::toggleFavorite,
        viewModel = libraryViewModel,
        modifier = modifier,
    )
}

@Composable
private fun LibraryContent(
    mistakeCount: Int,
    usePaging: Boolean,
    mistakeDetailRepository: MistakeDetailRepository?,
    pendingFailedTaskCount: Int,
    onContinuePendingTasks: (() -> Unit)?,
    onCapture: () -> Unit,
    onExportVisible: (LibraryExportCandidates) -> Unit,
    onOpenExportResults: () -> Unit,
    onOpenItem: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    viewModel: LibraryViewModel,
    modifier: Modifier,
) {
    val uiState = viewModel.uiState
    val scope = rememberCoroutineScope()
    val pagingItems = if (usePaging) viewModel.pagingData.collectAsLazyPagingItems() else null
    val visibleMistakeCount = if (usePaging) uiState.totalCount else uiState.visibleMistakes.size
    val refresh = pagingItems?.loadState?.refresh
    val loading = usePaging && (!uiState.loaded || refresh is LoadState.Loading)
    val listState = if (usePaging) libraryListState(uiState.loaded, refresh, pagingItems?.itemCount ?: 0)
        else LibraryListState.CONTENT
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val emptyState = if (loading || listState != LibraryListState.CONTENT) null else resolveLibraryEmptyState(
        totalMistakeCount = if (usePaging && !uiState.hasActiveFilters) uiState.totalCount else mistakeCount,
        visibleMistakeCount = if (loading) 1 else visibleMistakeCount,
        hasActiveSearch = uiState.query.isNotBlank(),
        hasActiveFilters = uiState.hasActiveFilters,
    )
    RootPageLazyColumn(
        modifier = modifier.testTag("library_root"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 12.dp,
        ),
    ) {
        item(key = "library_header") {
            Column {
                LibraryPageHeader(
                    showCapture = emptyState != LibraryEmptyState.CATALOG_EMPTY,
                    onCapture = onCapture,
                    onOpenResults = onOpenExportResults,
                )
                // PRD-06 空态半边：上次没处理完的录入不能因为错题本为空就失去继续入口。
                if (libraryPendingTasksEntryVisible(
                        count = pendingFailedTaskCount,
                        hasContinue = onContinuePendingTasks != null,
                    )
                ) {
                    Spacer(Modifier.height(10.dp))
                    OutlineActionChip(
                        text = libraryPendingTasksEntryLabel(pendingFailedTaskCount),
                        onClick = requireNotNull(onContinuePendingTasks),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_pending_tasks"),
                    )
                }
                ArchivedEntriesSection(
                    repository = mistakeDetailRepository,
                    onRestore = { entryId ->
                        scope.launch {
                            mistakeDetailRepository?.restoreEntry(
                                entryId = entryId,
                                at = System.currentTimeMillis(),
                            )
                        }
                    },
                )
                if (emptyState == LibraryEmptyState.CATALOG_EMPTY) {
                    EmptyLibraryResult(emptyState, canClear = false, onCapture = onCapture, onClear = {})
                } else {
                    Spacer(Modifier.height(12.dp))
                    LibrarySearchField(
                        query = uiState.query,
                        onQueryChange = viewModel::updateQuery,
                    )
                    Spacer(Modifier.height(4.dp))
                    LibraryResultToolbar(
                        count = if (loading) null else visibleMistakeCount,
                        sortLabel = if (usePaging) sortLabel(uiState.sort) else null,
                        filterCount = LibraryFacet.entries.count { uiState.selections.selectedOptionId(it) != null } +
                            if (uiState.timeRange != LibraryTimeRange.ALL) 1 else 0,
                        expanded = filtersExpanded,
                        canExport = !loading && visibleMistakeCount > 0 &&
                            (pagingItems?.itemCount ?: uiState.visibleMistakes.size) > 0,
                        onToggleFilters = { filtersExpanded = !filtersExpanded },
                        onExport = {
                            // Export the full current query, including pages not loaded on screen.
                            viewModel.exportVisible(MAX_LIBRARY_BATCH_EXPORT_QUESTIONS, onExportVisible)
                        },
                    )
                    LibrarySelectedFilters(
                        state = uiState,
                        onRemoveFacet = { viewModel.toggleFilter(it, null) },
                        onRemoveTimeRange = { viewModel.selectTimeRange(LibraryTimeRange.ALL) },
                    )
                    AnimatedVisibility(visible = filtersExpanded) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(SmartColors.JadeSoft.copy(alpha = 0.35f)).padding(12.dp)) {
                            FacetTabs(activeFacet = uiState.activeFacet, onSelect = viewModel::selectFacet)
                            FacetOptions(
                                facet = uiState.activeFacet,
                                options = uiState.activeOptions,
                                selectedOptionId = uiState.selections.selectedOptionId(uiState.activeFacet),
                                onSelect = { viewModel.toggleFilter(uiState.activeFacet, it) },
                            )
                            if (usePaging) {
                                Spacer(Modifier.height(12.dp))
                                Text("排序", style = MaterialTheme.typography.labelMedium, color = SmartColors.InkSecondary)
                                LibrarySortControl(sort = uiState.sort, onSelect = viewModel::selectSort)
                                Spacer(Modifier.height(8.dp))
                                Text("录入时间", style = MaterialTheme.typography.labelMedium, color = SmartColors.InkSecondary)
                                LibraryTimeRangeControl(timeRange = uiState.timeRange, onSelect = viewModel::selectTimeRange)
                            }
                        }
                    }
                    // 收藏开关的写失败必须看得见（默默回弹会把"没写进去"说成"写进去了"）。
                    uiState.favoriteNotice?.let { notice ->
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.testTag("library_favorite_notice").semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        }, verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = SmartColors.ErrorText, modifier = Modifier.size(18.dp))
                            Text(notice, style = MaterialTheme.typography.labelMedium, color = SmartColors.ErrorText)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (listState != LibraryListState.CONTENT) {
            item(key = "library_load_state") {
                PageStateFrame(
                    state = if (listState == LibraryListState.LOADING) PageState.Loading else PageState.Error(
                        title = "错题加载失败",
                        onRetry = { pagingItems?.retry() },
                    ),
                ) {}
            }
        } else if (emptyState == LibraryEmptyState.FILTERED_EMPTY || emptyState == LibraryEmptyState.SEARCH_EMPTY) {
            item(key = "library_empty") {
                EmptyLibraryResult(
                    state = emptyState,
                    canClear = uiState.hasActiveFilters,
                    onCapture = onCapture,
                    onClear = viewModel::clearAll,
                )
            }
        } else if (emptyState == null && pagingItems != null) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.entryId },
                contentType = pagingItems.itemContentType { _ -> "library_item" },
            ) { index ->
                val mistake = pagingItems[index]?.toLibraryMistake()
                if (mistake != null) {
                    LibraryQuestionCard(
                        mistake = mistake,
                        sourceRepository = mistakeDetailRepository,
                        onClick = { onOpenItem(mistake.id) },
                        onToggleFavorite = { favorite ->
                            onToggleFavorite(mistake.id, favorite)
                        },
                    )
                }
            }
        } else if (emptyState == null) {
            items(
                items = uiState.visibleMistakes,
                key = LibraryMistake::id,
            ) { mistake ->
                LibraryQuestionCard(
                    mistake = mistake,
                    sourceRepository = mistakeDetailRepository,
                    onClick = { onOpenItem(mistake.id) },
                    onToggleFavorite = { favorite ->
                        onToggleFavorite(mistake.id, favorite)
                    },
                )
            }
        }
        if (listState == LibraryListState.CONTENT && pagingItems != null &&
            (refresh is LoadState.Error || pagingItems.loadState.append is LoadState.Error)) {
            item(key = "library_more_error") {
                PageStateFrame(PageState.Error(
                    title = if (refresh is LoadState.Error) "更新失败" else "后续题目加载失败",
                    supportingText = "已显示的题目仍可查看。",
                    onRetry = pagingItems::retry,
                )) {}
            }
        }
        item(key = "library_footer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .testTag("library_search_field"),
        singleLine = true,
        placeholder = { Text("搜索题目、板块或知识点", style = MaterialTheme.typography.bodyMedium,
            color = SmartColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("library_search_clear"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清除搜索",
                    )
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SmartColors.Jade,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = SmartColors.OnJade,
            unfocusedContainerColor = SmartColors.OnJade,
            focusedTextColor = SmartColors.Ink,
            unfocusedTextColor = SmartColors.Ink,
            focusedLeadingIconColor = SmartColors.Jade,
            unfocusedLeadingIconColor = SmartColors.Ink,
            cursorColor = SmartColors.Jade,
        ),
    )
}

@Composable
private fun FacetTabs(
    activeFacet: LibraryFacet,
    onSelect: (LibraryFacet) -> Unit,
) {
    ClaudeSegmentedControl(
        segments = LibraryFacet.entries.map { ClaudeSegment(it.id, it.label, "library_facet_${it.id}") },
        selectedId = activeFacet.id,
        onSelect = { id -> onSelect(LibraryFacet.entries.first { it.id == id }) },
    )
}

@Composable
private fun FacetOptions(
    facet: LibraryFacet,
    options: List<LibraryFacetOption>,
    selectedOptionId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlineActionChip(
            text = "全部",
            onClick = { onSelect(null) },
            modifier = Modifier.testTag("library_filter_all"),
            selected = selectedOptionId == null,
        )
        options.forEach { option ->
            val label = if (facet == LibraryFacet.MASTERY)
                MasteryState.entries.firstOrNull { it.id == option.id }?.label ?: option.label else option.label
            val displayText = if (option.count > 0) {
                "$label (${option.count})"
            } else {
                label
            }
            OutlineActionChip(
                text = displayText,
                onClick = { onSelect(option.id) },
                modifier = Modifier.testTag(filterTag(facet, option)),
                selected = selectedOptionId == option.id,
            )
        }
    }
}

private fun filterTag(facet: LibraryFacet, option: LibraryFacetOption): String = when {
    facet == LibraryFacet.MASTERY && option.id == MasteryState.MASTERED.id ->
        "library_filter_mastered"
    else -> "library_filter_${facet.id}_${option.id.hashCode().toUInt()}"
}

/** 排序控件（阶段 4A 批 1 · L5）：只有"最近更新"（默认）与"最近录入"两值。 */
@Composable
private fun LibrarySortControl(
    sort: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySort.entries.forEach { option ->
            OutlineActionChip(
                text = sortLabel(option),
                onClick = { onSelect(option) },
                modifier = Modifier.testTag("library_sort_${option.name.lowercase()}"),
                selected = option == sort,
            )
        }
    }
}

private fun sortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.RECENTLY_UPDATED -> "最近更新"
    LibrarySort.RECENTLY_CREATED -> "最近录入"
}

/** 「录入时间段」控件：档位在查询层落成创建时间的绝对起止。 */
@Composable
private fun LibraryTimeRangeControl(
    timeRange: LibraryTimeRange,
    onSelect: (LibraryTimeRange) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryTimeRange.entries.forEach { option ->
            OutlineActionChip(
                text = option.label,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag("library_time_range_${option.id}"),
                selected = option == timeRange,
            )
        }
    }
}

@Composable
private fun EmptyLibraryResult(
    state: LibraryEmptyState,
    canClear: Boolean,
    onCapture: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = SmartColors.ContentSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .testTag("library_empty_state"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state == LibraryEmptyState.CATALOG_EMPTY) {
                LibraryEmptyIllustration()
            } else Icon(
                imageVector = when (state) {
                    LibraryEmptyState.CATALOG_EMPTY -> Icons.Outlined.LibraryAdd
                    LibraryEmptyState.FILTERED_EMPTY -> Icons.Outlined.Search
                    LibraryEmptyState.SEARCH_EMPTY -> Icons.Outlined.SearchOff
                },
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = SmartColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = SmartColors.Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = SmartColors.InkSecondary,
                textAlign = TextAlign.Center,
            )
            if (state == LibraryEmptyState.CATALOG_EMPTY) {
                // 首装没有照片也能起步：录入页有本地「手动建题」入口，不依赖模型能力。
                Spacer(Modifier.height(6.dp))
                Text(
                    text = LIBRARY_EMPTY_MANUAL_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartColors.InkSecondary,
                    modifier = Modifier.testTag("library_empty_manual_hint"),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryActionButton(
                    text = "录入",
                    onClick = onCapture,
                    modifier = Modifier
                        .widthIn(min = 160.dp)
                        .testTag("library_capture_button"),
                    icon = Icons.Outlined.LibraryAdd,
                )
            } else if (canClear) {
                Spacer(Modifier.height(16.dp))
                OutlineActionChip(
                    text = "清除搜索与筛选",
                    onClick = onClear,
                    modifier = Modifier.testTag("library_clear_filters"),
                )
            }
        }
    }
}

/**
 * "已移出的题"折叠区：归档条目从列表/搜索消失后，这里给出唯一的恢复入口，
 * 让详情页"之后可以恢复"的承诺真正可达。
 */
@Composable
private fun ArchivedEntriesSection(
    repository: MistakeDetailRepository?,
    onRestore: (String) -> Unit,
) {
    if (repository == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val archived by produceState<List<ArchivedMistakeRef>>(emptyList(), repository) {
        repository.observeArchived().collect { value = it }
    }
    if (archived.isEmpty()) return
    val motion = LocalClaudeMotionEnabled.current
    val targetRotation = if (expanded) 180f else 0f
    val rotation by animateFloatAsState(targetRotation,
        animationSpec = ClaudeMotion.spec(motion), label = "library-archive-disclosure")
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("library_archived_toggle").semantics {
                stateDescription = if (expanded) "已展开" else "已收起"
            },
        ) {
            Icon(Icons.Outlined.ExpandMore, null,
                modifier = Modifier.size(18.dp).rotate(if (motion) rotation else targetRotation))
            Spacer(Modifier.width(4.dp))
            Text("已移出的题（${archived.size}）", style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                archived.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = SmartColors.Ink,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlineActionChip(
                            text = "恢复",
                            onClick = { onRestore(item.entryId) },
                            modifier = Modifier.testTag("library_archived_restore"),
                        )
                    }
                }
                Text(
                    text = "恢复后将返回错题本和复习计划。",
                    color = SmartColors.InkSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * PRD-06：待处理任务入口只在"真有待处理数"且"真接了继续回调"时出现——
 * 没有落点的按钮会把"继续"说成谎言。
 */
internal fun libraryPendingTasksEntryVisible(count: Int, hasContinue: Boolean): Boolean =
    count > 0 && hasContinue

internal fun libraryPendingTasksEntryLabel(count: Int): String =
    "继续处理 $count 道题"

/** 首装空态补充说明：指向本地手动建题（不需要照片或模型）。 */
internal const val LIBRARY_EMPTY_MANUAL_HINT = "也可在录入页选择「手动建题」。"
