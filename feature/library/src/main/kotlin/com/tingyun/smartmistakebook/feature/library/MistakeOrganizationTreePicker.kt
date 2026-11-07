package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationOptions
import com.tingyun.smartmistakebook.core.domain.OrganizationOption
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationSelection
import com.tingyun.smartmistakebook.core.domain.UserProblemClassification
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.claudeInteractiveRow
import com.tingyun.smartmistakebook.core.ui.Outline

/** Legacy name-based picker helpers remain compatible with callers without a catalog dependency.
 * Production uses KnowledgeCatalogPickerSheet and keeps reviewed node identities end to end.
 */

/** 一次知识树选择的结果：加入后的集合，或不能加入的原因（界面据此给提示）。 */
internal sealed interface OrganizationTreePick {
    data class Added(val classifications: List<UserProblemClassification>) : OrganizationTreePick
    data object Duplicate : OrganizationTreePick
    data object LimitReached : OrganizationTreePick
    data object Invalid : OrganizationTreePick
}

/**
 * 把知识树里选中的一个选项并入本次修改的分类集合（纯函数，便于单测）。
 *
 * - 重名（同维度、大小写无关）视为已选：不重复加入；
 * - 名称不合规或为空：不加入（[OrganizationTreePick.Invalid]）；
 * - 达到 [ProblemOrganizationSelection.MAX_USER_CLASSIFICATIONS]：不加入。
 */
internal fun pickOrganizationTreeOption(
    existing: List<UserProblemClassification>,
    dimension: ClassificationDimension,
    displayName: String,
): OrganizationTreePick {
    val normalized = displayName.trim()
    if (normalized.isEmpty()) return OrganizationTreePick.Invalid
    if (existing.any { it.dimension == dimension && it.displayName.equals(normalized, ignoreCase = true) }) {
        return OrganizationTreePick.Duplicate
    }
    if (existing.size >= ProblemOrganizationSelection.MAX_USER_CLASSIFICATIONS) {
        return OrganizationTreePick.LimitReached
    }
    val added = runCatching {
        UserProblemClassification(dimension = dimension, displayName = normalized)
    }.getOrNull() ?: return OrganizationTreePick.Invalid
    return OrganizationTreePick.Added(existing + added)
}

/** 筛选纯函数：空查询返回全部；否则按名称做大小写无关的子串匹配。 */
internal fun filterOrganizationOptions(
    options: List<OrganizationOption>,
    query: String,
): List<OrganizationOption> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return options
    return options.filter { option -> option.displayName.lowercase().contains(needle) }
}

internal fun organizationOptionSelectionKey(
    dimension: ClassificationDimension,
    displayName: String,
): Pair<ClassificationDimension, String> = dimension to displayName.trim().lowercase()

@Composable
internal fun OrganizationTreePickerDialog(
    options: MistakeOrganizationOptions,
    selectedKeys: Set<Pair<ClassificationDimension, String>>,
    onPick: (ClassificationDimension, OrganizationOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleChapters = remember(options.chapters, query) {
        filterOrganizationOptions(options.chapters, query)
    }
    val visibleKnowledge = remember(options.knowledgeNodes, query) {
        filterOrganizationOptions(options.knowledgeNodes, query)
    }
    val isEmpty = options.chapters.isEmpty() && options.knowledgeNodes.isEmpty()

    com.tingyun.smartmistakebook.core.ui.WorkspaceSheet(
        title = "从知识树选择", onDismiss = onDismiss, scrollContent = false,
        icon = androidx.compose.material.icons.Icons.Outlined.Search,
        actions = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("mistake_tree_done")) { Text("完成") } },
    ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { value -> query = value.take(MAX_TREE_QUERY_CHARS) },
                    singleLine = true,
                    placeholder = { Text("搜索板块或知识点") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_tree_query"),
                )
                if (isEmpty) {
                    Text(
                        text = "知识目录里暂时没有可选项，可以在下方直接补充分类。",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("mistake_tree_empty"),
                    )
                } else if (visibleChapters.isEmpty() && visibleKnowledge.isEmpty()) {
                    Text(
                        text = "没有匹配的选项，换个词试试。",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("mistake_tree_empty"),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("mistake_tree_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (visibleChapters.isNotEmpty()) {
                            item(key = "tree-chapter-header") {
                                TreeGroupHeader("板块")
                            }
                            items(
                                items = visibleChapters,
                                key = { option -> "chapter:${option.labelId}" },
                            ) { option ->
                                OrganizationTreeOptionRow(
                                    option = option,
                                    selected = organizationOptionSelectionKey(
                                        ClassificationDimension.CHAPTER,
                                        option.displayName,
                                    ) in selectedKeys,
                                    onClick = { onPick(ClassificationDimension.CHAPTER, option) },
                                    testTag = "mistake_tree_chapter_${option.labelId}",
                                )
                            }
                        }
                        if (visibleKnowledge.isNotEmpty()) {
                            item(key = "tree-knowledge-header") {
                                TreeGroupHeader("知识点")
                            }
                            items(
                                items = visibleKnowledge,
                                key = { option -> "knowledge:${option.labelId}" },
                            ) { option ->
                                OrganizationTreeOptionRow(
                                    option = option,
                                    selected = organizationOptionSelectionKey(
                                        ClassificationDimension.KNOWLEDGE,
                                        option.displayName,
                                    ) in selectedKeys,
                                    onClick = { onPick(ClassificationDimension.KNOWLEDGE, option) },
                                    testTag = "mistake_tree_knowledge_${option.labelId}",
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun TreeGroupHeader(title: String) {
    Text(
        text = title,
        color = Ink,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun OrganizationTreeOptionRow(
    option: OrganizationOption,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .claudeInteractiveRow(onClick, selected = selected, enabled = !selected)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) JadeSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) JadeActive.copy(alpha = .35f) else com.tingyun.smartmistakebook.core.ui.SmartColors.Divider),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.displayName,
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text(
                    text = "已选",
                    color = InkSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private const val MAX_TREE_QUERY_CHARS = 40
private val TREE_PICKER_MAX_HEIGHT = 360.dp

@Composable
internal fun KnowledgeCatalogPickerSheet(
    catalog: com.tingyun.smartmistakebook.core.domain.KnowledgeCatalogRepository,
    subject: String,
    initialSelection: List<com.tingyun.smartmistakebook.core.model.CatalogHit>,
    available: Boolean,
    selectionLimit: Int,
    onDone: (List<com.tingyun.smartmistakebook.core.model.CatalogHit>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val vm = remember(catalog) { KnowledgeCatalogViewModel(catalog, initialSelection, available, selectionLimit, scope) }
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(state.query) { listState.scrollToItem(0) }
    androidx.compose.runtime.LaunchedEffect(available) { vm.setAvailable(available) }
    com.tingyun.smartmistakebook.core.ui.WorkspaceSheet(
        title = "选择知识点",
        scrollContent = false,
        icon = androidx.compose.material.icons.Icons.Outlined.Search,
        modifier = Modifier.testTag("knowledge_catalog_sheet"),
        subtitle = "全知识库 · 当前题目：${catalogSubjectLabel(subject)} · 可跨学科关联",
        onDismiss = onDismiss,
        actions = {
            com.tingyun.smartmistakebook.core.ui.ClaudeButton(
                onClick = { keyboard?.hide(); onDone(state.selected) },
                modifier = Modifier.fillMaxWidth().testTag("mistake_tree_done"),
            ) { Text("完成 · 已选 ${state.selected.size}") }
            Text("完成后返回分类草稿，保存修改才会生效", color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        },
    ) {
        OutlinedTextField(
            value = state.query, onValueChange = vm::setQuery,
            placeholder = { Text("搜索知识点、关键词或整句话") },
            modifier = Modifier.fillMaxWidth().testTag("mistake_tree_query"),
            maxLines = 3,
        )
        val keywords = remember(state.hits) { state.hits.flatMap { it.keywords }.distinct().take(12) }
        if (keywords.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(keywords) { word ->
                androidx.compose.material3.AssistChip(onClick = { vm.setQuery(word) }, label = { Text(word) })
            }
        }
        if (state.selected.isNotEmpty()) {
            Text("已选知识点", color = Ink, style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.selected, key = { it.node.nodeId }) { hit ->
                    androidx.compose.material3.InputChip(
                        selected = true, onClick = { vm.toggle(hit) },
                        label = { Text("${if (hit.subject.isBlank()) "" else catalogSubjectLabel(hit.subject) + " · "}${hit.name} ×") },
                        modifier = Modifier.testTag("catalog_selected_${hit.node.nodeId}"),
                    )
                }
            }
        }
        state.notice?.let { Text(it, color = InkSecondary, style = MaterialTheme.typography.bodySmall) }
        if (!state.available) Text("知识目录正在准备，就绪后会自动显示", color = InkSecondary)
        else if (state.loading) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().testTag("catalog_loading"))
            Text("正在读取知识目录", color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            state.error?.let { error ->
                com.tingyun.smartmistakebook.core.ui.WorkspaceNotice(error, tone = com.tingyun.smartmistakebook.core.ui.WorkspaceNoticeTone.ERROR, modifier = Modifier.testTag("catalog_error"))
                TextButton(onClick = { if (state.hits.isEmpty()) vm.retry() else vm.loadNext() }) { Text("重试") }
            }
            if (state.hits.isEmpty() && state.error == null) {
                Text(if (state.query.isBlank()) "知识目录暂时没有可选项" else "没有找到匹配内容，可以缩短句子或换个关键词", color = InkSecondary, modifier = Modifier.testTag("mistake_tree_empty"))
                if (state.query.isNotBlank()) TextButton(onClick = { vm.setQuery("") }) { Text("浏览全部知识点") }
            }
            val groupedHits = remember(state.hits) { state.hits.groupBy { it.subject to it.chapterPath }.values.flatten() }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("mistake_tree_list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groupedHits.forEachIndexed { index, hit ->
                    val group = "${catalogSubjectLabel(hit.subject)} · ${hit.chapterPath.joinToString(" / ").ifEmpty { "未分章节" }}"
                    val previous = groupedHits.getOrNull(index - 1)
                    if (previous == null || previous.subject != hit.subject || previous.chapterPath != hit.chapterPath) {
                        item(key = "header:${hit.node.nodeId}") { TreeGroupHeader(group) }
                    }
                    item(key = hit.node.nodeId) {
                        val selected = state.selected.any { it.node.nodeId == hit.node.nodeId }
                        Surface(
                            Modifier.fillMaxWidth().selectable(selected, role = androidx.compose.ui.semantics.Role.Checkbox, onClick = { vm.toggle(hit) }).testTag("mistake_tree_knowledge_${hit.node.nodeId}"),
                            color = if (selected) JadeSoft else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) JadeActive.copy(alpha = .4f) else com.tingyun.smartmistakebook.core.ui.SmartColors.Divider),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(hit.name, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleSmall)
                                    androidx.compose.material3.Checkbox(selected, onCheckedChange = null)
                                }
                                if (hit.snippet.isNotBlank()) Text(hit.snippet, color = InkSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (state.next != null) item(key = "load-next") {
                    TextButton(onClick = vm::loadNext, enabled = !state.loadingNext, modifier = Modifier.fillMaxWidth().testTag("catalog_load_next")) { Text(if (state.loadingNext) "正在加载" else "加载更多") }
                }
            }
        }
    }
}

internal fun catalogSubjectLabel(subject: String): String = when (subject) {
    "MATH" -> "数学"
    "PHYSICS" -> "物理"
    "CHEMISTRY" -> "化学"
    "BIOLOGY" -> "生物"
    "CHINESE" -> "语文"
    "ENGLISH" -> "英语"
    "HISTORY" -> "历史"
    "GEOGRAPHY" -> "地理"
    "POLITICS" -> "政治"
    else -> subject.ifBlank { "未指定学科" }
}
