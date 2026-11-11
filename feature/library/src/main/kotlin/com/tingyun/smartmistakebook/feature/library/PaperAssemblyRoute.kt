package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.tingyun.smartmistakebook.core.ui.ClaudeSegment
import com.tingyun.smartmistakebook.core.ui.ClaudeSegmentedControl
import com.tingyun.smartmistakebook.core.ui.WorkspacePageHeader
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import com.tingyun.smartmistakebook.core.ui.ClaudeButton as Button
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingyun.smartmistakebook.core.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PaperAssemblyRoute(
    initialFilters: Map<String, String> = emptyMap(),
    entries: List<StudyCatalogEntry>,
    catalogRepository: LibraryCatalogRepository,
    mistakeDetailRepository: MistakeDetailRepository,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onEdit: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val assembly: PaperAssemblyViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val library: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val state = createSavedStateHandle()
            if (!state.contains("assembly_initialized")) {
                state["library_query"] = initialFilters["query"].orEmpty()
                state["library_sort"] = initialFilters["sort"]
                state["library_time_range"] = LibraryTimeRange.entries.firstOrNull { it.name == initialFilters["time"] }?.id
                LibraryFacet.entries.forEach { facet ->
                    state["library_filter_${facet.id}"] = initialFilters[facet.name]
                }
                state["assembly_initialized"] = true
            }
            LibraryViewModel(state)
        }
    })
    var selectedTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(catalogRepository) { library.bindRepository(catalogRepository) }
    Column(modifier.fillMaxSize()) {
        WorkspacePageHeader(title = "组卷", subtitle = "选择题目，调整顺序与卷面", compact = true,
            modifier = Modifier.padding(horizontal = 16.dp), actions = {
            TextButton(onClick = onBack) { Text("返回") }
        })
        ClaudeSegmentedControl(
            segments = listOf(ClaudeSegment("candidates", "选题", "assembly_candidates"), ClaudeSegment("selected", "已选 ${assembly.selectedEntryIds.size}", "assembly_selected")),
            selectedId = if (selectedTab) "selected" else "candidates",
            onSelect = { selectedTab = it == "selected" },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (selectedTab) {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (assembly.selectedEntryIds.isEmpty()) item { Text("还没有选题，返回选题区添加题目。") }
                itemsIndexed(assembly.selectedEntryIds, key = { _, id -> id }) { index, id ->
                    val currentState by produceState<MistakeDetailState>(MistakeDetailState.Loading, id, mistakeDetailRepository) {
                        try { mistakeDetailRepository.observe(id).collect { value = it } }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { value = MistakeDetailState.NotFound }
                    }
                    val detail = when (val state = currentState) {
                        is MistakeDetailState.Ready -> state.detail
                        is MistakeDetailState.Legacy -> state.detail
                        else -> null
                    }
                    val changed = detail?.identity?.problemRevisionId?.let { current ->
                        assembly.expectedRevisions[id]?.let { it != current }
                    } == true
                    MistakeSectionCard(title = "第 ${index + 1} 题", modifier = Modifier.padding(vertical = 6.dp)) {
                        TextButton(onClick = { onOpenItem(id) }) {
                            Text(detail?.identity?.title ?: entries.firstOrNull { it.entryId == id }?.toLibraryMistake()?.title ?: "查看题目")
                        }
                        LibraryInlineImages(
                            mistake = entries.firstOrNull { it.entryId == id }?.toLibraryMistake(),
                            repository = mistakeDetailRepository,
                            entryId = id,
                            title = "第 ${index + 1} 题",
                        )
                        if (detail?.archived == true || currentState == MistakeDetailState.NotFound || currentState is MistakeDetailState.CorruptSnapshot) {
                            com.tingyun.smartmistakebook.core.ui.WorkspaceNotice("该题暂不可用，可移除后继续组卷。")
                        } else if (changed && detail != null) {
                            com.tingyun.smartmistakebook.core.ui.WorkspaceNotice("题面已更新，请确认当前题图和题干。")
                            TextButton(enabled = !assembly.checking, onClick = { assembly.acceptCurrentVersion(id, detail.identity.problemRevisionId) }) {
                                Text("采用当前题面")
                            }
                        }
                        Row {
                            TextButton(enabled = !assembly.checking && index > 0, onClick = { assembly.move(id, -1) }) { Text("上移") }
                            TextButton(enabled = !assembly.checking && index < assembly.selectedEntryIds.lastIndex, onClick = { assembly.move(id, 1) }) { Text("下移") }
                            TextButton(enabled = !assembly.checking, onClick = { assembly.toggle(id) }) { Text("移除") }
                        }
                    }
                }
            }
        } else {
            LibraryContent(
                mistakeCount = entries.size, usePaging = true,
                mistakeDetailRepository = mistakeDetailRepository,
                pendingFailedTaskCount = 0, onContinuePendingTasks = null,
                onCapture = onBack, onExportVisible = { selectedTab = true }, onOpenExportResults = onBack,
                onOpenItem = onOpenItem, onToggleFavorite = library::toggleFavorite,
                viewModel = library, modifier = Modifier.weight(1f), assembly = assembly,
            )
        }
        assembly.notice?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("已选 ${assembly.selectedEntryIds.size} 题", modifier = Modifier.padding(vertical = 12.dp))
            Button(enabled = assembly.selectedEntryIds.isNotEmpty() && !assembly.checking, onClick = {
                val ids = assembly.selectedEntryIds
                scope.launch {
                    if (assembly.validateForEditing(mistakeDetailRepository)) onEdit(ids)
                    else selectedTab = true
                }
            }) { Text(if (assembly.checking) "正在检查…" else "调整卷面") }
        }
    }
}

@Composable
internal fun SelectionControl(model: PaperAssemblyViewModel, id: String, revisionId: String? = null) {
    val selected = id in model.selectedEntryIds
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = selected, enabled = !model.checking, role = Role.Checkbox,
                onValueChange = { model.toggle(id, revisionId) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Text(if (selected) "已加入试卷" else "加入试卷", modifier = Modifier.padding(horizontal = 8.dp))
    }
}
