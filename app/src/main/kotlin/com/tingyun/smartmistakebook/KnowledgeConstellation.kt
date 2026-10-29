package com.tingyun.smartmistakebook

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.ClaudeMotion
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
import com.tingyun.smartmistakebook.core.ui.ClaudeFilterChip
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.masteryIntervalLabel
import com.tingyun.smartmistakebook.core.ui.masteryStatusColor
import com.tingyun.smartmistakebook.core.ui.studentLabel

import kotlin.math.roundToInt

private const val STARS_PER_PAGE = 18

@Composable
internal fun KnowledgeConstellation(
    summaries: List<StudyKnowledgeSummary>,
    prerequisites: Map<String, Set<String>>,
) {
    val all = summaries.distinctBy { it.knowledgeNodeId }.sortedBy { it.knowledgeNodeId }
    var subjectName by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    fun resetView() { zoom = 1f; pan = Offset.Zero }
    val filtered = all.filter { subjectName.isEmpty() || it.subject.name == subjectName }
    val pages = ((filtered.size + STARS_PER_PAGE - 1) / STARS_PER_PAGE).coerceAtLeast(1)
    val activePage = page.coerceIn(0, pages - 1)
    val visible = filtered.drop(activePage * STARS_PER_PAGE).take(STARS_PER_PAGE)
    val links = remember(visible, prerequisites) { constellationLinks(visible.map { it.knowledgeNodeId }.toSet(), prerequisites) }
    val stars = remember(visible, links) { constellationStars(visible, links) }
    val selected = visible.firstOrNull { it.knowledgeNodeId == selectedId } ?: visible.firstOrNull()
    WorkspacePanel(Modifier.padding(top = 16.dp).testTag("knowledge_constellation")) {
        Text("知识星图", color = SmartColors.Ink, style = MaterialTheme.typography.titleMedium)
        Text("每颗亮星代表一个知识点，连线表示已确认的前置关系。点选亮星查看详情。",
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val subjects = listOf<SubjectKind?>(null) + all.map { it.subject }.distinct().sortedBy { it.ordinal }
            subjects.forEach { subject ->
                val key = subject?.name.orEmpty()
                ClaudeFilterChip(selected = subjectName == key, onClick = { subjectName = key; page = 0; resetView() },
                    label = { Text(subject?.studentLabel() ?: "全部科目") })
            }
        }
        StarField(stars, links, selected?.knowledgeNodeId.orEmpty(), zoom, pan,
            onViewport = { nextZoom, nextPan -> zoom = nextZoom; pan = nextPan }) { selectedId = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ClaudeSecondaryButton(onClick = { zoom = (zoom - .25f).coerceAtLeast(1f); pan = Offset.Zero },
                enabled = zoom > 1f, modifier = Modifier.testTag("star_zoom_out")) { Text("缩小") }
            Text("${(zoom * 100).roundToInt()}%", color = SmartColors.InkSecondary, modifier = Modifier.padding(top = 14.dp))
            ClaudeSecondaryButton(onClick = { zoom = (zoom + .25f).coerceAtMost(2.5f); pan = Offset.Zero },
                enabled = zoom < 2.5f, modifier = Modifier.testTag("star_zoom_in")) { Text("放大") }
            ClaudeSecondaryButton(onClick = { resetView() }, modifier = Modifier.testTag("star_reset_view")) { Text("复位") }
        }
        Text("双指缩放和移动，轻点亮星查看详情。", color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MasteryStatus.entries.forEach { status ->
                Text("● ${status.studentLabel()}", color = masteryStatusColor(status), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(if (links.isEmpty()) "当前亮星之间暂无已确认的前置关系，暂不连线。"
            else "箭头由前置知识指向后续知识。仅展示当前亮星之间的关系。",
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        if (pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ClaudeSecondaryButton(onClick = { page = activePage - 1; resetView() }, enabled = activePage > 0) { Text("上一组") }
            Text("${activePage + 1} / $pages", color = SmartColors.InkSecondary)
            ClaudeSecondaryButton(onClick = { page = activePage + 1; resetView() }, enabled = activePage + 1 < pages) { Text("下一组") }
        }
        Text("已显示 ${visible.size} / ${filtered.size} 个知识点", color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { knowledge ->
                ClaudeFilterChip(selected = knowledge.knowledgeNodeId == selected?.knowledgeNodeId,
                    onClick = { selectedId = knowledge.knowledgeNodeId },
                    modifier = Modifier.testTag("star_select:${knowledge.knowledgeNodeId}"),
                    label = { Text(knowledge.displayName) })
            }
        }
        selected?.let { knowledge ->
            val before = prerequisites[knowledge.knowledgeNodeId].orEmpty()
            val after = prerequisites.filterValues { knowledge.knowledgeNodeId in it }.keys
            fun names(ids: Set<String>) = all.filter { it.knowledgeNodeId in ids }.joinToString("、") { it.displayName }
            Column(Modifier.fillMaxWidth().background(SmartColors.SubtleSurface, RoundedCornerShape(14.dp))
                .padding(14.dp).testTag("knowledge_star_details").semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(knowledge.displayName, color = SmartColors.Ink, style = MaterialTheme.typography.titleMedium)
                Text("${knowledge.subject.studentLabel()} · ${knowledge.status.studentLabel()}",
                    color = masteryStatusColor(knowledge.status), style = MaterialTheme.typography.bodySmall)
                Text(masteryIntervalLabel(knowledge.conservativeMasteryScore, knowledge.masteryIntervalUpper),
                    color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                Text("已记录的前置知识：${names(before).ifEmpty { "暂无" }}", color = SmartColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Text("已记录的后续知识：${names(after).ifEmpty { "暂无" }}", color = SmartColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun starColor(status: MasteryStatus) = when (status) {
    MasteryStatus.MASTERED -> SmartColors.StarMastered
    MasteryStatus.LEARNING -> SmartColors.StarLearning
    MasteryStatus.CONFLICTED -> SmartColors.StarConflicted
    MasteryStatus.STALE -> SmartColors.StarStale
    MasteryStatus.UNKNOWN -> SmartColors.StarUnknown
}

@Composable
private fun StarField(stars: List<KnowledgeStar>, links: List<KnowledgeStarLink>, selected: String,
    zoom: Float, pan: Offset, onViewport: (Float, Offset) -> Unit, onSelect: (String) -> Unit) {
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val updateViewport by rememberUpdatedState(onViewport)
    val motion = LocalClaudeMotionEnabled.current
    val highlights = linkedMapOf<String, Float>()
    stars.forEach { star -> key(star.knowledge.knowledgeNodeId) {
        val target = if (star.knowledge.knowledgeNodeId == selected) 1f else 0f
        val emphasis by animateFloatAsState(target, ClaudeMotion.spec(motion, ClaudeMotion.RevealMillis), label = "star-selection")
        highlights[star.knowledge.knowledgeNodeId] = if (motion) emphasis else target
    } }
    val density = LocalDensity.current
    val textSize = with(density) { 12.sp.toPx() }
    val hitRadius = with(density) { 24.dp.toPx() }
    val paint = remember(textSize) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize; color = SmartColors.OnCode.toArgb(); textAlign = Paint.Align.CENTER
    } }
    Canvas(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(SmartColors.CodeSurface, SmartColors.Forest)))
        .testTag("knowledge_star_field")
        .semantics {
            contentDescription = "知识星图，${stars.size} 个知识点，${links.size} 条已确认的前置关系。位置不代表掌握程度。当前缩放 ${(zoom * 100).roundToInt()}%。"
            customActions = stars.map { star -> CustomAccessibilityAction("查看${star.knowledge.displayName}") {
                onSelect(star.knowledge.knowledgeNodeId); true
            } }
        }
        .pointerInput(stars) {
            detectTapGestures { tap ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val local = (tap - center - currentPan) / currentZoom + center
                stars.minByOrNull { (Offset(it.x * size.width, it.y * size.height) - local).getDistance() }
                    ?.takeIf { (Offset(it.x * size.width, it.y * size.height) - local).getDistance() * currentZoom <= hitRadius }
                    ?.let { onSelect(it.knowledge.knowledgeNodeId) }
            }
        }.pointerInput(stars) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var gestureZoom = currentZoom
                var gesturePan = currentPan
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val nextZoom = (gestureZoom * event.calculateZoom()).coerceIn(1f, 2.5f)
                        val relative = event.calculateCentroid(useCurrent = false) - Offset(size.width / 2f, size.height / 2f)
                        val nextPan = (gesturePan - relative) * (nextZoom / gestureZoom) + relative + event.calculatePan()
                        val maxX = size.width * (nextZoom - 1f) / 2
                        val maxY = size.height * (nextZoom - 1f) / 2
                        gesturePan = Offset(nextPan.x.coerceIn(-maxX, maxX), nextPan.y.coerceIn(-maxY, maxY))
                        gestureZoom = nextZoom
                        updateViewport(gestureZoom, gesturePan)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, center) }) {
        val positions = stars.associate { it.knowledge.knowledgeNodeId to Offset(it.x * size.width, it.y * size.height) }
        repeat(52) { i ->
            val at = Offset(((i * 73 + 17) % 997) / 997f * size.width, ((i * 149 + 41) % 991) / 991f * size.height)
            drawCircle(SmartColors.OnCode.copy(alpha = .13f), (.6f + i % 3 * .2f).dp.toPx(), at)
        }
        links.forEach { link ->
            val from = positions.getValue(link.prerequisiteId)
            val to = positions.getValue(link.dependentId)
            val emphasis = maxOf(highlights[link.prerequisiteId] ?: 0f, highlights[link.dependentId] ?: 0f)
            val color = SmartColors.StarLearning.copy(alpha = .25f + emphasis * .55f)
            drawLine(color, from, to, (1f + emphasis * .5f).dp.toPx())
            val direction = (to - from) / (to - from).getDistance().coerceAtLeast(1f)
            val tip = to - direction * 9.dp.toPx()
            val side = Offset(-direction.y, direction.x) * 3.dp.toPx()
            drawPath(Path().apply {
                moveTo(tip.x, tip.y)
                val back = tip - direction * 6.dp.toPx()
                lineTo((back + side).x, (back + side).y); lineTo((back - side).x, (back - side).y); close()
            }, color)
        }
        stars.forEach { star ->
            val at = positions.getValue(star.knowledge.knowledgeNodeId)
            val active = selected == star.knowledge.knowledgeNodeId
            val tint = starColor(star.knowledge.status)
            val emphasis = highlights[star.knowledge.knowledgeNodeId] ?: 0f
            drawCircle(tint.copy(alpha = .07f), (15f + emphasis * 8f).dp.toPx(), at)
            drawCircle(tint.copy(alpha = .16f), (10f + emphasis * 3f).dp.toPx(), at)
            drawCircle(tint, (4f + emphasis * 1.5f).dp.toPx(), at)
            if (emphasis > 0f) drawCircle(SmartColors.OnCode.copy(alpha = emphasis), 9.dp.toPx(), at, style = Stroke(1.dp.toPx()))
            if (stars.size <= 8 || active) {
                val name = star.knowledge.displayName.let { if (it.length > 8) it.take(8) + "…" else it }
                val half = paint.measureText(name) / 2 + 6.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(name, at.x.coerceIn(half, (size.width - half).coerceAtLeast(half)),
                    (at.y + 24.dp.toPx()).coerceAtMost(size.height - 8.dp.toPx()), paint)
            }
        }
        }
    }
}
