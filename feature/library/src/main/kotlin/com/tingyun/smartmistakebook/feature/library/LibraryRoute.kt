package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import com.tingyun.smartmistakebook.core.domain.ArchivedMistakeRef
import com.tingyun.smartmistakebook.core.domain.LibrarySort
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.LibraryCatalogRepository
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageLazyColumn
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.SubjectIcon
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
    val loading = usePaging && !uiState.loaded
    val emptyState = resolveLibraryEmptyState(
        totalMistakeCount = mistakeCount,
        visibleMistakeCount = if (loading) 1 else visibleMistakeCount,
        hasActiveSearch = uiState.query.isNotBlank(),
    )
    RootPageLazyColumn(
        modifier = modifier.testTag("library_root"),
        contentPadding = PaddingValues(
            start = 26.dp,
            top = 0.dp,
            end = 26.dp,
            bottom = 12.dp,
        ),
    ) {
        item(key = "library_header") {
            Column {
                Text(
                    text = "错题本",
                    color = SmartColors.Ink,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
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
                if (emptyState == LibraryEmptyState.CATALOG_EMPTY) {
                    EmptyLibraryResult(
                        state = emptyState,
                        canClear = false,
                        onCapture = onCapture,
                        onClear = {},
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    // 只有一个「录入」动作（L6）：拍照、相册、整卷/PDF 都在录入里选，
                    // 错题本栏不再有第二个并列入口。
                    PrimaryActionButton(
                        text = "录入",
                        onClick = onCapture,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("library_capture_button"),
                        icon = Icons.Outlined.LibraryAdd,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 「导出成果」（L7）：后台导出的永久应用内落点。始终可见——通知未授权时
                // 它是退化入口，也是"图书馆里有没有整理好的 A4"这一问题的唯一答案。
                OutlineActionChip(
                    text = "导出成果",
                    onClick = onOpenExportResults,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_export_results"),
                    icon = Icons.Outlined.PictureAsPdf,
                )
                PaperDivider(Modifier.padding(vertical = 8.dp))
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
                if (emptyState != LibraryEmptyState.CATALOG_EMPTY) {
                    LibrarySearchField(
                        query = uiState.query,
                        onQueryChange = viewModel::updateQuery,
                    )
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(
                        title = "分类筛选",
                        action = {
                            Text(
                                text = "${uiState.totalCount} 道题",
                                modifier = Modifier.testTag("library_result_count"),
                                style = MaterialTheme.typography.labelMedium,
                                color = SmartColors.InkSecondary,
                            )
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    FacetTabs(
                        activeFacet = uiState.activeFacet,
                        onSelect = viewModel::selectFacet,
                    )
                    Spacer(Modifier.height(4.dp))
                    FacetOptions(
                        facet = uiState.activeFacet,
                        options = uiState.activeOptions,
                        selectedOptionId = uiState.selections.selectedOptionId(uiState.activeFacet),
                        onSelect = { viewModel.toggleFilter(uiState.activeFacet, it) },
                    )
                    // 收藏开关的写失败必须看得见（默默回弹会把"没写进去"说成"写进去了"）。
                    uiState.favoriteNotice?.let { notice ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = notice,
                            modifier = Modifier.testTag("library_favorite_notice"),
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartColors.ErrorWarm,
                        )
                    }
                    if (usePaging) {
                        Spacer(Modifier.height(8.dp))
                        LibrarySortControl(
                            sort = uiState.sort,
                            onSelect = viewModel::selectSort,
                        )
                        Spacer(Modifier.height(6.dp))
                        LibraryTimeRangeControl(
                            timeRange = uiState.timeRange,
                            onSelect = viewModel::selectTimeRange,
                        )
                    }
                    if ((pagingItems?.itemCount ?: uiState.visibleMistakes.size) > 0) {
                        Spacer(Modifier.height(10.dp))
                        OutlineActionChip(
                            text = "导出当前 ${visibleMistakeCount} 道",
                            onClick = {
                                // 与按钮口径一致：按当前筛选拉全量 id，而不是
                                // Paging 已加载的子集；超限时入口不会下单，由「导出成果」
                                // 如实说明原因（L7 起没有前台导出页）。
                                viewModel.exportVisible(
                                    MAX_LIBRARY_BATCH_EXPORT_QUESTIONS,
                                ) { ids -> onExportVisible(ids) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_export_visible"),
                            icon = Icons.Outlined.PictureAsPdf,
                        )
                    }
                }
            }
        }
        if (emptyState == LibraryEmptyState.FILTERED_EMPTY) {
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
                contentType = pagingItems.itemContentType { _ -> "library_item" },
            ) { index ->
                val mistake = pagingItems[index]?.toLibraryMistake()
                if (mistake != null) {
                    LibraryItemRow(
                        mistake = mistake,
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
                LibraryItemRow(
                    mistake = mistake,
                    onClick = { onOpenItem(mistake.id) },
                    onToggleFavorite = { favorite ->
                        onToggleFavorite(mistake.id, favorite)
                    },
                )
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
            .height(54.dp)
            .testTag("library_search_field"),
        singleLine = true,
        placeholder = { Text("搜索题目、板块或知识点") },
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
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SmartColors.Jade,
            unfocusedBorderColor = SmartColors.Outline,
            focusedTextColor = SmartColors.Ink,
            unfocusedTextColor = SmartColors.Ink,
            focusedLeadingIconColor = SmartColors.Jade,
            unfocusedLeadingIconColor = SmartColors.InkSecondary,
            cursorColor = SmartColors.Jade,
        ),
    )
}

@Composable
private fun FacetTabs(
    activeFacet: LibraryFacet,
    onSelect: (LibraryFacet) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LibraryFacet.entries.forEach { facet ->
            val selected = facet == activeFacet
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics { role = Role.Tab }
                    .clickable { onSelect(facet) }
                    .padding(vertical = 8.dp)
                    .testTag("library_facet_${facet.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = facet.label,
                    color = if (selected) SmartColors.Jade else SmartColors.InkSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 34.dp else 0.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) SmartColors.Jade else SmartColors.Paper),
                )
            }
        }
    }
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
            val displayText = if (option.count > 0) {
                "${option.label} (${option.count})"
            } else {
                option.label
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
private fun LibraryItemRow(
    mistake: LibraryMistake,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("library_item_${mistake.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectIcon(
            subject = mistake.subject,
            contentDescription = "${mistake.title} 学科",
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = mistake.title,
                style = MaterialTheme.typography.titleMedium,
                color = SmartColors.Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = mistake.summary,
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${mistake.contentPath} · ${mistake.mastery.label}",
                style = MaterialTheme.typography.labelMedium,
                color = masteryColor(mistake.mastery),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // C11 收藏开关（列表行）：点它只改这一行条目的收藏标记，不打开详情。
        IconButton(
            onClick = { onToggleFavorite(!mistake.isFavorite) },
            modifier = Modifier.testTag("library_favorite_${mistake.id}"),
        ) {
            Icon(
                imageVector = if (mistake.isFavorite) {
                    Icons.Filled.Bookmark
                } else {
                    Icons.Outlined.BookmarkBorder
                },
                contentDescription = if (mistake.isFavorite) {
                    "取消收藏 ${mistake.title}"
                } else {
                    "收藏 ${mistake.title}"
                },
                tint = if (mistake.isFavorite) SmartColors.Jade else SmartColors.InkSecondary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "打开 ${mistake.title}",
            tint = SmartColors.InkSecondary,
        )
    }
}

@Composable
private fun masteryColor(mastery: MasteryState) = when (mastery) {
    MasteryState.MASTERED -> SmartColors.Jade
    MasteryState.UNKNOWN -> SmartColors.InkSecondary
    MasteryState.LEARNING,
    MasteryState.CONFLICTED,
    MasteryState.STALE,
    -> SmartColors.ErrorWarm
}

@Composable
private fun EmptyLibraryResult(
    state: LibraryEmptyState,
    canClear: Boolean,
    onCapture: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp)
            .testTag("library_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
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
        )
        if (state == LibraryEmptyState.CATALOG_EMPTY) {
            // 首装没有照片也能起步：录入页有本地「手动建题」入口，不依赖模型能力。
            Spacer(Modifier.height(6.dp))
            Text(
                text = LIBRARY_EMPTY_MANUAL_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
                modifier = Modifier.testTag("library_empty_manual_hint"),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(
                text = "录入",
                onClick = onCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
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
    Column(Modifier.fillMaxWidth()) {
        OutlineActionChip(
            text = "已移出的题（${archived.size}）",
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_archived_toggle"),
        )
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
                    text = "恢复后这道题会回到错题本列表和复习计划。",
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
    "继续处理 $count 道待处理题目"

/** 首装空态补充说明：指向本地手动建题（不需要照片或模型）。 */
internal const val LIBRARY_EMPTY_MANUAL_HINT = "没有照片也可以：在录入页选择「手动建题」。"
