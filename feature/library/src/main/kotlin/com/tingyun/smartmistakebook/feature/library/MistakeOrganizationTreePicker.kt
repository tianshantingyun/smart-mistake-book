package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tingyun.smartmistakebook.core.ui.Outline

/**
 * 「从知识树选择」：把 `observeOrganizationOptions` 给出的已审目录选项并入本次修改。
 *
 * 消灭的失败：改前只有离线自由文本编辑器能改分类（`MistakeOfflineCorrectionEditor`，
 * 本轮删除）——学生要么接受模型的分类，要么自己**手打**一个名字；手打的字符串与已审
 * 知识树没有任何关系，也无法复现目录里的既有标签。这里换成从知识树点选既有选项，
 * 选中的名字照常并入 `confirm(requestId, selection)` 的 selection 集合
 * （[UserProblemClassification] 以 displayName 为权威，与模型建议同一条落库路径）。
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从知识树选择") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { value -> query = value.take(MAX_TREE_QUERY_CHARS) },
                    singleLine = true,
                    label = { Text("搜索板块或知识点") },
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
                            .heightIn(max = TREE_PICKER_MAX_HEIGHT)
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
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("mistake_tree_done"),
            ) { Text("完成") }
        },
    )
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
            .clickable(enabled = !selected, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) JadeSoft.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Outline),
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
