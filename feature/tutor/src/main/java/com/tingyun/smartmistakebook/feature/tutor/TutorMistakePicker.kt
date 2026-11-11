package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

import com.tingyun.smartmistakebook.core.ui.ContentSurface
import com.tingyun.smartmistakebook.core.ui.SmartColors
import androidx.compose.foundation.BorderStroke

import com.tingyun.smartmistakebook.core.ui.claudeInteractiveRow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tingyun.smartmistakebook.core.ui.WorkspaceSheet
import com.tingyun.smartmistakebook.core.ui.WorkspaceNotice
import com.tingyun.smartmistakebook.core.ui.WorkspaceNoticeTone
import com.tingyun.smartmistakebook.core.ui.ClaudeDisclosure
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import com.tingyun.smartmistakebook.core.domain.LibraryCatalogRepository
import com.tingyun.smartmistakebook.core.domain.LibraryCatalogItem
import com.tingyun.smartmistakebook.core.domain.LibraryFacetCount
import com.tingyun.smartmistakebook.core.domain.LibraryFacetKind
import com.tingyun.smartmistakebook.core.domain.LibraryQuery
import com.tingyun.smartmistakebook.core.domain.LibrarySort
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.ClaudeQuietDots
import kotlinx.coroutines.CancellationException
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.studentLabel

/** Scoped at the app composition root; every picker, including export, reads the same saved sources. */
val LocalTutorMistakeRepository = staticCompositionLocalOf<MistakeDetailRepository?> { null }
val LocalTutorLibraryCatalog = staticCompositionLocalOf<LibraryCatalogRepository?> { null }

internal fun pickerLibraryQuery(search: String, subject: String?, chapter: String?, mastery: String?,
    favorite: Boolean, days: Int?, sort: LibrarySort, at: Long): LibraryQuery = LibraryQuery(
    searchText = search.trim(), subjectId = subject, sectionId = chapter, masteryId = mastery,
    favoriteOnly = favorite, sort = sort,
    createdFromEpochMillis = days?.let { (at - it * 86_400_000L).coerceAtLeast(0L) },
    createdToEpochMillis = days?.let { at },
)

private data class PickerCatalogState(
    val entries: List<StudyCatalogEntry> = emptyList(), val options: List<LibraryFacetCount> = emptyList(),
    val total: Int = 0, val loading: Boolean = false, val failed: Boolean = false,
)

private fun LibraryCatalogItem.pickerEntry() = StudyCatalogEntry(
    entryId, problemId, problemRevisionId, practiceUnitId, subjectId, title, problemMarkdown,
    sourceKey = null, isCuratedExample = false, chapterLabels = chapterLabels, knowledgeLabels = knowledgeLabels,
    masteryStatus = MasteryStatus.entries.firstOrNull { it.name.equals(masteryId, true) } ?: MasteryStatus.UNKNOWN,
    nextReviewAtEpochMillis = nextReviewAtEpochMillis, retrievability = retrievability,
)

/**
 * 「从错题库选择」：在学生当前所在的页面里直接挑一道题，选完交给调用方作为**本轮附件**带进
 * 讲题页面（`onOpenMistakeTutor`）。两个入口共用它：输入框加号里的那一项，以及空态里的
 * 「从错题本选择」快捷按钮。
 *
 * 消灭的失败：这条入口此前只是 `navigate(Routes.Library)`——跳到错题本、自己找、点进详情、
 * 再点「讲解这道题」，四步之后才回到讲题；而且从错题本"选择"这个动作根本不返回任何东西
 * （`Routes.Library` 没有参数、`LibraryRoute` 也没有选择回调）。学生在这个页面上点了"从错题库
 * 选择"，得到的是离开当前页，而不是把题加进来。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TutorMistakePickerDialog(
    entries: List<StudyCatalogEntry>, onPick: (StudyCatalogEntry) -> Unit,
    onDismiss: () -> Unit, testTagPrefix: String,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var chapter by rememberSaveable { mutableStateOf<String?>(null) }
    var chapterLabel by rememberSaveable { mutableStateOf("") }
    var mastery by rememberSaveable { mutableStateOf<String?>(null) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf<Int?>(null) }
    var sortId by rememberSaveable { mutableStateOf(LibrarySort.RECENTLY_UPDATED.name) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var facetId by rememberSaveable { mutableStateOf(LibraryFacetKind.SUBJECT.name) }
    var limit by remember { mutableStateOf(40) }
    var retry by remember { mutableStateOf(0) }
    val anchor = remember { System.currentTimeMillis() }
    val repository = LocalTutorLibraryCatalog.current
    val request = pickerLibraryQuery(query, subject, chapter, mastery, favorite, days, LibrarySort.valueOf(sortId), anchor)
    val facet = LibraryFacetKind.valueOf(facetId)
    LaunchedEffect(request) { limit = 40 }
    val result by produceState(PickerCatalogState(loading = true), repository, request, facet, limit, retry) {
        if (repository == null) {
            val fallback = filterMistakeCandidates(entries, query)
            value = PickerCatalogState(entries = fallback, total = fallback.size)
        } else {
            value = value.copy(loading = true, failed = false)
            try {
                val page = repository.query(request, 0, limit)
                val options = repository.facets(request, facet)
                value = PickerCatalogState(page.items.map { item ->
                    entries.firstOrNull { it.entryId == item.entryId && it.problemRevisionId == item.problemRevisionId }
                        ?: item.pickerEntry()
                }, options, page.totalCount)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = PickerCatalogState(failed = true) }
        }
    }
    val filtered = subject != null || chapter != null || mastery != null || favorite || days != null || query.isNotBlank()
    val facets = listOf(LibraryFacetKind.SUBJECT to "科目", LibraryFacetKind.SECTION to "板块",
        LibraryFacetKind.MASTERY to "掌握程度", LibraryFacetKind.FAVORITE to "收藏")
    val masteryLabels = listOf("conflicted" to "需巩固", "learning" to "学习中", "mastered" to "已掌握",
        "stale" to "待复习", "unknown" to "待评估")
    WorkspaceSheet(
        title = "从错题库选择",
        icon = Icons.Outlined.Search,
        onDismiss = onDismiss,
        scrollContent = false,
        actions = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("${testTagPrefix}_picker_cancel")) { Text("取消") }
        },
    ) {
        OutlinedTextField(value = query, onValueChange = { query = it.take(MAX_PICKER_QUERY_CHARS) },
            placeholder = { Text("搜索题目、板块或知识点") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(
                    onClick = { query = "" },
                    modifier = Modifier.testTag("${testTagPrefix}_picker_clear_query"),
                ) { Icon(Icons.Outlined.Close, "清空搜索") }
            },
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { if (it.isFocused) expanded = false }
                .testTag("${testTagPrefix}_picker_query"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (result.loading) "正在筛选…" else "${result.total} 道题", Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = InkSecondary)
            TextButton(onClick = {
                focusManager.clearFocus()
                expanded = !expanded
            }) { Text(if (expanded) "收起筛选" else "筛选") }
            if (filtered) TextButton(onClick = { query = ""; subject = null; chapter = null; mastery = null; favorite = false; days = null }) { Text("清空") }
        }
        if (filtered && !expanded) Text(buildList {
            subject?.let { id -> add(SubjectKind.entries.firstOrNull { it.name == id }?.studentLabel() ?: id) }
            chapter?.let { add(chapterLabel.ifBlank { "已选板块" }) }
            mastery?.let { id -> add(masteryLabels.firstOrNull { it.first == id }?.second ?: "已选掌握程度") }
            if (favorite) add("已收藏")
            days?.let { add("近 $it 天") }
        }.joinToString(" · ").ifBlank { "按关键词筛选" }, color = InkSecondary, style = MaterialTheme.typography.labelSmall)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)
            .testTag("${testTagPrefix}_picker_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "filters") {
                ClaudeDisclosure(visible = expanded && repository != null) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            facets.forEach { (id, label) -> OutlineActionChip(label, { facetId = id.name }, selected = facet == id) }
                        }
                        val selected = when (facet) {
                            LibraryFacetKind.SUBJECT -> subject; LibraryFacetKind.SECTION -> chapter
                            LibraryFacetKind.MASTERY -> mastery; LibraryFacetKind.FAVORITE -> if (favorite) "favorite" else null
                        }
                        val options = if (facet == LibraryFacetKind.MASTERY) masteryLabels.map { (id, label) ->
                            LibraryFacetCount(id, label, result.options.firstOrNull { it.id == id }?.count ?: 0)
                        } else result.options
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlineActionChip("全部", { when (facet) {
                                LibraryFacetKind.SUBJECT -> { subject = null; chapter = null }
                                LibraryFacetKind.SECTION -> chapter = null
                                LibraryFacetKind.MASTERY -> mastery = null
                                LibraryFacetKind.FAVORITE -> favorite = false
                            } }, selected = selected == null)
                            options.forEach { option ->
                                val label = if (facet == LibraryFacetKind.SUBJECT) SubjectKind.entries.firstOrNull { it.name == option.id }?.studentLabel() ?: option.label else option.label
                                OutlineActionChip("$label (${option.count})", { val next = option.id.takeUnless { it == selected }; when (facet) {
                                    LibraryFacetKind.SUBJECT -> { subject = next; chapter = null }
                                    LibraryFacetKind.SECTION -> { chapter = next; chapterLabel = option.label }
                                    LibraryFacetKind.MASTERY -> mastery = next
                                    LibraryFacetKind.FAVORITE -> favorite = next != null
                                } }, selected = selected == option.id)
                            }
                        }
                        Text("录入时间", color = InkSecondary, style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(null, 7, 30, 90).forEach { value -> OutlineActionChip(value?.let { "近 $it 天" } ?: "全部时间", { days = value }, selected = days == value) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LibrarySort.entries.forEach { value -> OutlineActionChip(if (value == LibrarySort.RECENTLY_UPDATED) "最近更新" else "最近录入", { sortId = value.name }, selected = sortId == value.name) }
                        }
                    }
                }
            }
            if (result.loading) item { WorkspaceNotice("正在读取题目…") }
            else if (result.failed) item {
                WorkspaceNotice("题目暂时无法读取，请重试。", tone = WorkspaceNoticeTone.ERROR)
                TextButton(onClick = { retry++ }) { Text("重试") }
            } else if (result.entries.isEmpty()) item {
                WorkspaceNotice(if (filtered) "没有符合条件的题目，可清空筛选重新选择。" else "错题本中还没有题目。",
                    modifier = Modifier.testTag("${testTagPrefix}_picker_empty"))
            } else {
                items(result.entries, key = { it.entryId }) { entry ->
                    MistakePickerRow(entry, { onPick(entry) }, "${testTagPrefix}_picker_item_${entry.problemId}")
                }
                if (result.entries.size < result.total) item {
                    TextButton(onClick = { limit += 40 }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                }
            }
        }
    }
}

@Composable
private fun MistakePickerRow(
    entry: StudyCatalogEntry,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        color = ContentSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SmartColors.Divider),
        modifier = Modifier
            .fillMaxWidth()
            .claudeInteractiveRow(onClick, shape = RoundedCornerShape(12.dp))
            .testTag(testTag),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val repository = LocalTutorMistakeRepository.current
            var previewUnavailable by remember(repository, entry.entryId, entry.problemRevisionId) { mutableStateOf(false) }
            val images by produceState<List<Pair<String, String>>?>(null, repository, entry.entryId, entry.problemRevisionId) {
                if (repository == null) { value = emptyList(); return@produceState }
                try {
                    repository.observeQuestionImages(entry.entryId).collect { assets ->
                        previewUnavailable = assets.any { it.location !is MistakeSourceLocation.Available }
                        value = assets.mapNotNull { asset -> (asset.location as? MistakeSourceLocation.Available)?.localUri?.let { asset.sourceAssetId to it } }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { previewUnavailable = true; value = emptyList() }
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (entry.subject.isNotBlank()) add(com.tingyun.smartmistakebook.core.model.SubjectKind.entries
                    .firstOrNull { it.name == entry.subject }?.studentLabel() ?: entry.subject)
                addAll(entry.chapterLabels.take(1))
                addAll(entry.knowledgeLabels.take(2))
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            if (images == null) Row(Modifier.fillMaxWidth().height(72.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClaudeQuietDots()
                Text("正在读取题图…", color = InkSecondary, style = MaterialTheme.typography.bodySmall)
            }
            images?.forEachIndexed { index, (_, uri) ->
                BoundedLocalImage(imageUri = uri, contentDescription = "${entry.title}，第${index + 1}张题图",
                    expanded = true, expandedMaxHeight = 240.dp,
                    modifier = Modifier.fillMaxWidth().testTag("${testTag}_image_$index"))
            }
            if (previewUnavailable) Text("题图暂时无法读取", color = InkSecondary, style = MaterialTheme.typography.bodySmall)
            if (images?.isEmpty() == true && entry.problemMarkdown.isNotBlank()) Text(
                com.tingyun.smartmistakebook.core.model.ReadableMathText.inlineMarkdown(entry.problemMarkdown, lineBreakReplacement = ' '),
                color = InkSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 候选筛选（纯函数，便于单测）：空查询返回全部；否则按题目、章节、知识点、科目做大小写无关的
 * 子串匹配。刻意不做分词与排序猜测——这一步只负责"把范围缩到学生自己认得出的那几道题"，
 * 真正的语义判定不在这里。
 */
internal fun filterMistakeCandidates(
    entries: List<StudyCatalogEntry>,
    query: String,
): List<StudyCatalogEntry> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return entries
    return entries.filter { entry ->
        entry.title.lowercase().contains(needle) ||
            entry.subject.lowercase().contains(needle) ||
            entry.chapterLabels.any { label -> label.lowercase().contains(needle) } ||
            entry.knowledgeLabels.any { label -> label.lowercase().contains(needle) }
    }
}

/** 把错题本条目转成讲题会话需要的题面身份。 */
internal fun StudyCatalogEntry.toMistakeRevisionKey(): MistakeRevisionKey = MistakeRevisionKey(
    entryId = entryId,
    problemId = problemId,
    problemRevisionId = problemRevisionId,
)

private const val MAX_PICKER_QUERY_CHARS = 40
