package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ExpandMore
import com.tingyun.smartmistakebook.core.ui.ClaudeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.studentLabel
import com.tingyun.smartmistakebook.core.ui.ClaudeMotion
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled

@Composable
internal fun LibraryPageHeader(
    showCapture: Boolean,
    onCapture: () -> Unit,
    onOpenResults: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = SmartColors.HeaderSurface) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val showFileLabel = maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.2f
            val stackTitle = maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f
            Column {
                if (stackTitle) {
                    Text("错题本", color = SmartColors.HeaderInk,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 32.sp),
                        fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!stackTitle) {
                        Text(
                            "错题本",
                            modifier = Modifier.weight(1f),
                            color = SmartColors.HeaderInk,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 32.sp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = onOpenResults,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("library_export_results")
                            .semantics { contentDescription = "查看已导出的文件" },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = SmartColors.HeaderSurface, contentColor = SmartColors.HeaderInk),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        if (showFileLabel) {
                            Spacer(Modifier.width(4.dp))
                            Text("已导出", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (showCapture) {
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = onCapture,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("library_capture_button"),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartColors.Jade, contentColor = SmartColors.OnJade),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("录入", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryResultToolbar(
    count: Int?,
    sortLabel: String?,
    filterCount: Int,
    expanded: Boolean,
    canExport: Boolean,
    onToggleFilters: () -> Unit,
    onExport: () -> Unit,
) {
    val motion = LocalClaudeMotionEnabled.current
    val targetRotation = if (expanded) 180f else 0f
    val rotation by animateFloatAsState(targetRotation,
        animationSpec = ClaudeMotion.spec(motion), label = "library-filter-disclosure")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(count?.let { "$it 道题" } ?: "错题列表", modifier = Modifier.testTag("library_result_count"),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = SmartColors.Ink)
            if (sortLabel != null) Text(sortLabel, style = MaterialTheme.typography.labelSmall, color = SmartColors.InkSecondary)
        }
        TextButton(onClick = onToggleFilters,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("library_filter_toggle")
                .semantics { stateDescription = if (expanded) "筛选面板已展开" else "筛选面板已收起" },
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (expanded || filterCount > 0) SmartColors.JadeSoft else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = SmartColors.Ink,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "收起" else if (filterCount > 0) "筛选 $filterCount" else "筛选",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.ExpandMore, null,
                modifier = Modifier.size(16.dp).rotate(if (motion) rotation else targetRotation))
        }
        if (canExport) {
            TextButton(onClick = onExport,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    .testTag("library_export_visible").semantics { contentDescription = "导出当前 $count 道" },
                colors = ButtonDefaults.textButtonColors(contentColor = SmartColors.Ink),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("导出", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Summaries use the existing selection IDs and existing removal commands, including hierarchy rules. */
@Composable
internal fun LibrarySelectedFilters(
    state: LibraryUiState,
    onRemoveFacet: (LibraryFacet) -> Unit,
    onRemoveTimeRange: () -> Unit,
) {
    if (state.selections.isEmpty && state.timeRange == LibraryTimeRange.ALL) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LibraryFacet.entries.forEach { facet ->
            state.selections.selectedOptionId(facet)?.let { id ->
                val label = when (facet) {
                    LibraryFacet.SUBJECT -> SubjectKind.entries.firstOrNull { it.name == id }?.studentLabel() ?: id
                    LibraryFacet.CHAPTER -> id
                    LibraryFacet.MASTERY -> MasteryState.fromId(id).label
                    LibraryFacet.FAVORITE -> "已收藏"
                }
                SelectedFilter(label, "library_selected_${facet.id}", onClick = { onRemoveFacet(facet) })
            }
        }
        if (state.timeRange != LibraryTimeRange.ALL) {
            SelectedFilter(state.timeRange.label, "library_selected_time", onRemoveTimeRange)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SelectedFilter(label: String, tag: String, onClick: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClick,
        label = {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        },
        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
        border = null,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag(tag)
            .semantics { contentDescription = "移除筛选：$label" },
    )
}
