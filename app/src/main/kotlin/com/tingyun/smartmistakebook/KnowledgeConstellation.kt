package com.tingyun.smartmistakebook

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.ClaudeFilterChip
import com.tingyun.smartmistakebook.core.ui.ClaudeMotion
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.masteryIntervalLabel
import com.tingyun.smartmistakebook.core.ui.masteryStatusColor
import com.tingyun.smartmistakebook.core.ui.studentLabel

@Composable
internal fun KnowledgeConstellation(
    summaries: List<StudyKnowledgeSummary>,
    prerequisites: Map<String, Set<String>>,
) {
    val all = remember(summaries) { summaries.distinctBy { it.knowledgeNodeId }.sortedBy { it.knowledgeNodeId } }
    var subjectName by rememberSaveable { mutableStateOf("") }
    val initialId = summaries.firstOrNull()?.knowledgeNodeId.orEmpty()
    var page by rememberSaveable { mutableIntStateOf(constellationDestination(all, "", initialId)?.page ?: 0) }
    var selectedId by rememberSaveable { mutableStateOf(initialId) }
    var localAnchorId by rememberSaveable { mutableStateOf("") }
    // Zoom and pan share a lifetime, so recreation cannot restore a zoom with a lost camera offset.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    fun resetView() { zoom = 1f; pan = Offset.Zero }
    val activeSubject = subjectName.takeIf { name -> all.any { it.subject.name == name } }.orEmpty()
    val filtered = all.filter { activeSubject.isEmpty() || it.subject.name == activeSubject }
    val neighborhood = remember(all, prerequisites, localAnchorId) { constellationNeighborhood(all, prerequisites, localAnchorId) }
    val local = neighborhood.isNotEmpty()
    val items = if (local) neighborhood.drop(1) else filtered
    val groupSize = if (local) STARS_PER_PAGE - 1 else STARS_PER_PAGE
    val pages = ((items.size + groupSize - 1) / groupSize).coerceAtLeast(1)
    val activePage = page.coerceIn(0, pages - 1)
    val visible = (if (local) neighborhood.take(1) else emptyList()) + items.drop(activePage * groupSize).take(groupSize)
    val links = remember(visible, prerequisites) { constellationLinks(visible.map { it.knowledgeNodeId }.toSet(), prerequisites) }
    val stars = remember(visible, links, localAnchorId) { constellationStars(visible, links, localAnchorId.takeIf { local }) }
    val selected = visible.firstOrNull { it.knowledgeNodeId == selectedId } ?: visible.firstOrNull()
    fun openLocal(id: String) { localAnchorId = id; selectedId = id; page = 0; resetView() }
    fun openOverview() {
        val destination = constellationDestination(all, activeSubject, selected?.knowledgeNodeId.orEmpty())
        selectedId = selected?.knowledgeNodeId.orEmpty()
        localAnchorId = ""
        subjectName = destination?.subjectName.orEmpty()
        page = destination?.page ?: 0
        resetView()
    }
    fun reveal(id: String) {
        if (local) { openLocal(id); return }
        val destination = constellationDestination(all, activeSubject, id) ?: return
        subjectName = destination.subjectName
        page = destination.page
        selectedId = id
        resetView()
    }
    WorkspacePanel(Modifier.padding(top = 16.dp).testTag("knowledge_constellation")) {
        Text("知识星图", color = SmartColors.Ink, style = MaterialTheme.typography.titleMedium)
        Text("查看知识分布，沿前置关系找到需要巩固的内容。", color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClaudeFilterChip(selected = !local, onClick = { openOverview() },
                modifier = Modifier.testTag("star_overview_mode"), label = { Text("知识总览") })
            ClaudeFilterChip(selected = local, onClick = { if (!local) selected?.let { openLocal(it.knowledgeNodeId) } },
                modifier = Modifier.testTag("star_local_mode"), label = { Text("局部关联") })
        }
        if (local) {
            Text("围绕：${neighborhood.first().displayName}", color = SmartColors.Ink,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("star_local_anchor"))
            Text("显示已有学习记录的直接前置与后续知识。", color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodySmall)
        } else Row(Modifier.fillMaxWidth().testTag("star_subject_selector").horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val subjects = listOf<SubjectKind?>(null) + all.map { it.subject }.distinct().sortedBy { it.ordinal }
            subjects.forEach { subject ->
                val name = subject?.name.orEmpty()
                ClaudeFilterChip(selected = activeSubject == name,
                    onClick = {
                        subjectName = name
                        selectedId = summaries.firstOrNull { name.isEmpty() || it.subject.name == name }?.knowledgeNodeId.orEmpty()
                        page = constellationDestination(all, name, selectedId)?.page ?: 0
                        resetView()
                    }, modifier = Modifier.testTag("star_subject:${name.ifEmpty { "all" }}"),
                    label = { Text(subject?.studentLabel() ?: "全部科目") })
            }
        }
        KnowledgeStarField(stars, links, selected?.knowledgeNodeId.orEmpty(), zoom, pan,
            onViewport = { nextZoom, nextPan -> zoom = nextZoom; pan = nextPan },
            onReset = { resetView() }, onSelect = { selectedId = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Show states present in this group, rather than five permanent empty categories.
            MasteryStatus.entries.filter { status -> visible.any { it.status == status } }.forEach { status ->
                Text("● ${status.studentLabel()}", color = masteryStatusColor(status), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(when {
            local && neighborhood.size == 1 -> "该知识暂无已记录的直接关联，可在知识总览查看其他知识。"
            links.isEmpty() -> "本组暂无已确认的前置关系，可在详情中查看跨组关联。"
            else -> "箭头从前置指向后续知识，颜色表示掌握状态。"
        },
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        if (pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            ClaudeSecondaryButton(onClick = {
                page = activePage - 1; selectedId = ""; resetView()
            }, enabled = activePage > 0, modifier = Modifier.testTag("star_previous_page")) { Text("上一组") }
            Text("${activePage + 1} / $pages", color = SmartColors.InkSecondary)
            ClaudeSecondaryButton(onClick = {
                page = activePage + 1; selectedId = ""; resetView()
            }, enabled = activePage + 1 < pages, modifier = Modifier.testTag("star_next_page")) { Text("下一组") }
        }
        if (pages > 1) Text(if (local) "直接关联 ${items.size} 个 · 本组显示 ${visible.size - 1} 个，中心知识始终保留"
            else "本组 ${visible.size} / 共 ${filtered.size} 个知识点 · 仅连同组关系",
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
        Text("选择知识点", color = SmartColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
        key(activeSubject, activePage, localAnchorId) {
            Row(Modifier.fillMaxWidth().testTag("star_knowledge_selector").horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { knowledge ->
                    ClaudeFilterChip(selected = knowledge.knowledgeNodeId == selected?.knowledgeNodeId,
                        onClick = { selectedId = knowledge.knowledgeNodeId; resetView() },
                        modifier = Modifier.testTag("star_select:${knowledge.knowledgeNodeId}"),
                        label = { Text(knowledge.displayName) })
                }
            }
        }
        selected?.let { knowledge ->
            StarDetails(knowledge, all, prerequisites, ::reveal)
            if (!local || knowledge.knowledgeNodeId != localAnchorId) ClaudeSecondaryButton(
                onClick = { openLocal(knowledge.knowledgeNodeId) }, modifier = Modifier.testTag("star_view_relations")) {
                Text(if (local) "以此知识为中心" else "查看直接关联")
            }
        }
    }
}

@Composable
private fun StarDetails(
    knowledge: StudyKnowledgeSummary,
    all: List<StudyKnowledgeSummary>,
    prerequisites: Map<String, Set<String>>,
    onSelect: (String) -> Unit,
) {
    val motion = LocalClaudeMotionEnabled.current
    val target = starColor(knowledge.status).copy(alpha = .06f)
    val tint by animateColorAsState(target, ClaudeMotion.spec(motion, ClaudeMotion.RevealMillis), label = "star-detail-tint")
    Column(Modifier.fillMaxWidth().background(if (motion) tint else target, RoundedCornerShape(16.dp)).padding(16.dp)
        .testTag("knowledge_star_details").semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(knowledge.displayName, color = SmartColors.Ink, style = MaterialTheme.typography.titleMedium)
        Text("${knowledge.subject.studentLabel()} · ${knowledge.status.studentLabel()}",
            color = masteryStatusColor(knowledge.status), style = MaterialTheme.typography.labelMedium)
        Text(masteryIntervalLabel(knowledge.conservativeMasteryScore, knowledge.masteryIntervalUpper),
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        val before = prerequisites[knowledge.knowledgeNodeId].orEmpty() - knowledge.knowledgeNodeId
        val after = prerequisites.filterValues { knowledge.knowledgeNodeId in it }.keys - knowledge.knowledgeNodeId
        StarRelations("前置知识", "before", all.filter { it.knowledgeNodeId in before }, onSelect)
        StarRelations("后续知识", "after", all.filter { it.knowledgeNodeId in after }, onSelect)
    }
}

@Composable
private fun StarRelations(title: String, direction: String, knowledge: List<StudyKnowledgeSummary>, onSelect: (String) -> Unit) {
    Text("$title${if (knowledge.isEmpty()) " · 暂无已记录关系" else " · 点选查看"}",
        color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
    if (knowledge.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        knowledge.forEach { item ->
            ClaudeFilterChip(selected = false, onClick = { onSelect(item.knowledgeNodeId) },
                modifier = Modifier.testTag("star_relation:$direction:${item.knowledgeNodeId}"),
                label = { Text(item.displayName) })
        }
    }
}

internal fun starColor(status: MasteryStatus): Color = masteryStatusColor(status)
