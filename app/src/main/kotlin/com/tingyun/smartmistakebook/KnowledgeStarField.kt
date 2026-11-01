package com.tingyun.smartmistakebook

import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton
import com.tingyun.smartmistakebook.core.ui.ClaudeMotion
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
import com.tingyun.smartmistakebook.core.ui.SmartColors
import kotlin.math.roundToInt

@Composable
internal fun KnowledgeStarField(
    stars: List<KnowledgeStar>, links: List<KnowledgeStarLink>, selected: String,
    zoom: Float, pan: Offset, onViewport: (Float, Offset) -> Unit,
    onReset: () -> Unit, onSelect: (String) -> Unit,
) {
    val motion = LocalClaudeMotionEnabled.current
    var transforming by remember { mutableStateOf(false) }
    val animateCamera = motion && !transforming
    val cameraZoom by animateFloatAsState(zoom, ClaudeMotion.spec(animateCamera, ClaudeMotion.RevealMillis), label = "star-camera-zoom")
    val cameraX by animateFloatAsState(pan.x, ClaudeMotion.spec(animateCamera, ClaudeMotion.RevealMillis), label = "star-camera-x")
    val cameraY by animateFloatAsState(pan.y, ClaudeMotion.spec(animateCamera, ClaudeMotion.RevealMillis), label = "star-camera-y")
    val renderedZoom = if (animateCamera) cameraZoom else zoom
    val renderedPan = if (animateCamera) Offset(cameraX, cameraY) else pan
    val currentZoom by rememberUpdatedState(renderedZoom)
    val currentPan by rememberUpdatedState(renderedPan)
    val updateViewport by rememberUpdatedState(onViewport)
    val selectStar by rememberUpdatedState(onSelect)
    val neighbors = remember(links, selected) {
        links.filter { it.prerequisiteId == selected || it.dependentId == selected }
            .flatMap { listOf(it.prerequisiteId, it.dependentId) }.toSet() + selected
    }
    val emphasis = linkedMapOf<String, Float>()
    val relevance = linkedMapOf<String, Float>()
    stars.forEach { star -> key(star.knowledge.knowledgeNodeId) {
        val id = star.knowledge.knowledgeNodeId
        val target = if (id == selected) 1f else 0f
        val highlight by animateFloatAsState(target, ClaudeMotion.spec(motion, ClaudeMotion.RevealMillis), label = "star-focus")
        val relatedTarget = if (id in neighbors) 1f else .8f
        val related by animateFloatAsState(relatedTarget, ClaudeMotion.spec(motion, ClaudeMotion.RevealMillis), label = "star-neighborhood")
        emphasis[id] = if (motion) highlight else target
        relevance[id] = if (motion) related else relatedTarget
    } }
    val density = LocalDensity.current
    val textSize = with(density) { 12.sp.toPx() }
    val paint = remember(textSize) { TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = textSize } }
    val hitRadius = with(density) { 24.dp.toPx() }
    // Draw geometry is also the touch geometry; do not write Compose state from a draw pass.
    val labelTargets = remember(stars) { mutableListOf<PlacedStarLabel>() }
    Column(Modifier.fillMaxWidth().testTag("knowledge_star_observatory").clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(SmartColors.Paper, SmartColors.HeaderSurface)))
        .border(1.dp, SmartColors.Divider, RoundedCornerShape(22.dp))) {
        FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${stars.size} 个知识点 · ${links.size} 条关联", color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.labelMedium)
            Text("点选探索", color = SmartColors.JadeActive, style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp))
            .testTag("knowledge_star_field")
            .semantics {
                contentDescription = "知识星图，${stars.size} 个知识点，${links.size} 条已确认的前置关系。位置不代表掌握程度。当前缩放 ${(zoom * 100).roundToInt()}%。"
                customActions = stars.map { star -> CustomAccessibilityAction("查看${star.knowledge.displayName}") {
                    selectStar(star.knowledge.knowledgeNodeId); true
                } }
            }
            .pointerInput(stars) {
                detectTapGestures { tap ->
                    val label = labelTargets.firstOrNull { tap.x in it.left..it.right && tap.y in it.top..it.bottom }
                    if (label != null) selectStar(label.id)
                    else stars.minByOrNull { (starPosition(it, currentZoom, currentPan) - tap).getDistance() }
                        ?.takeIf { (starPosition(it, currentZoom, currentPan) - tap).getDistance() <= hitRadius }
                        ?.let { selectStar(it.knowledge.knowledgeNodeId) }
                }
            }.pointerInput(stars) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var gestureZoom = currentZoom
                    var gesturePan = currentPan
                    try {
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                transforming = true
                                val nextZoom = (gestureZoom * event.calculateZoom()).coerceIn(1f, 2.5f)
                                val relative = event.calculateCentroid(useCurrent = false) - Offset(size.width / 2f, size.height / 2f)
                                val nextPan = (gesturePan - relative) * (nextZoom / gestureZoom) + relative + event.calculatePan()
                                gesturePan = boundedPan(nextPan, nextZoom, size.width.toFloat(), size.height.toFloat())
                                gestureZoom = nextZoom
                                updateViewport(gestureZoom, gesturePan)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally { transforming = false }
                }
            }) {
            drawStarAtmosphere()
            val positions = stars.associate { it.knowledge.knowledgeNodeId to
                (Offset(it.x * size.width, it.y * size.height) - center) * renderedZoom + center + renderedPan }
            links.forEach { link ->
                val highlight = maxOf(emphasis.getValue(link.prerequisiteId), emphasis.getValue(link.dependentId))
                drawStarLink(positions.getValue(link.prerequisiteId), positions.getValue(link.dependentId), highlight)
            }
            stars.forEach { star ->
                val id = star.knowledge.knowledgeNodeId
                drawKnowledgeStar(positions.getValue(id), starColor(star.knowledge.status), emphasis.getValue(id), relevance.getValue(id))
            }
            labelTargets.clear()
            labelTargets.addAll(drawStarLabels(stars, positions, selected, neighbors, relevance, paint))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("双指缩放、移动", color = SmartColors.InkSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f))
            ClaudeIconButton(onClick = {
                val next = (zoom - .25f).coerceAtLeast(1f)
                onViewport(next, pan * ((next - 1f) / (zoom - 1f)).coerceIn(0f, 1f))
            }, enabled = zoom > 1f, modifier = Modifier.testTag("star_zoom_out"), colors = starButtonColors()) {
                Icon(Icons.Outlined.Remove, "缩小星图", Modifier.size(20.dp))
            }
            Text("${(zoom * 100).roundToInt()}%", color = SmartColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
            ClaudeIconButton(onClick = {
                val next = (zoom + .25f).coerceAtMost(2.5f); onViewport(next, pan * (next / zoom))
            }, enabled = zoom < 2.5f, modifier = Modifier.testTag("star_zoom_in"), colors = starButtonColors()) {
                Icon(Icons.Outlined.Add, "放大星图", Modifier.size(20.dp))
            }
            ClaudeIconButton(onClick = onReset, modifier = Modifier.testTag("star_reset_view"), colors = starButtonColors()) {
                Icon(Icons.Outlined.Fullscreen, "显示星图全景", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun starButtonColors() = IconButtonDefaults.iconButtonColors(
    contentColor = SmartColors.InkSecondary, disabledContentColor = SmartColors.InkMuted.copy(alpha = .45f))

private fun androidx.compose.ui.input.pointer.PointerInputScope.starPosition(star: KnowledgeStar, zoom: Float, pan: Offset): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    return (Offset(star.x * size.width, star.y * size.height) - center) * zoom + center + pan
}

private fun boundedPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val maxX = width * (zoom - 1f) / 2
    val maxY = height * (zoom - 1f) / 2
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

private fun DrawScope.drawStarAtmosphere() {
    // Quiet screen-space texture shares the page's paper/teal palette.
    drawRect(Brush.radialGradient(listOf(SmartColors.Jade.copy(alpha = .045f), Color.Transparent),
        center = Offset(size.width * .85f, size.height * .15f), radius = size.maxDimension * .8f))
    drawRect(Brush.radialGradient(listOf(SmartColors.LearningAccent.copy(alpha = .025f), Color.Transparent),
        center = Offset(size.width * .08f, size.height * .7f), radius = size.maxDimension * .75f))
    repeat(40) { i ->
        val at = Offset(((i * 73 + 17) % 997) / 997f * size.width, ((i * 149 + 41) % 991) / 991f * size.height)
        drawCircle(SmartColors.Outline.copy(alpha = .1f + (i % 4) * .02f), .5.dp.toPx(), at)
    }
}

private fun DrawScope.drawStarLink(from: Offset, to: Offset, emphasis: Float) {
    val length = (to - from).getDistance()
    if (length < 28.dp.toPx()) return
    val direction = (to - from) / length
    val start = from + direction * 12.dp.toPx()
    val end = to - direction * 12.dp.toPx()
    val color = SmartColors.Outline
    drawLine(SmartColors.Jade.copy(alpha = emphasis * .04f), start, end, 5.dp.toPx(), StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(color.copy(alpha = .32f + emphasis * .2f),
        SmartColors.JadeActive.copy(alpha = .35f + emphasis * .5f)), start, end), start, end,
        (1f + emphasis * .25f).dp.toPx(), StrokeCap.Round)
    val tip = end
    val back = tip - direction * 6.dp.toPx()
    val side = Offset(-direction.y, direction.x) * 3.dp.toPx()
    drawPath(Path().apply {
        moveTo(tip.x, tip.y); lineTo((back + side).x, (back + side).y)
        lineTo((back - side).x, (back - side).y); close()
    }, SmartColors.JadeActive.copy(alpha = .4f + emphasis * .5f))
}

private fun DrawScope.drawKnowledgeStar(at: Offset, tint: Color, emphasis: Float, relevance: Float) {
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = (.04f + emphasis * .12f) * relevance), Color.Transparent),
        center = at, radius = (17f + emphasis * 8f).dp.toPx()), (17f + emphasis * 8f).dp.toPx(), at)
    drawCircle(tint.copy(alpha = .06f * relevance), (8f + emphasis * 2f).dp.toPx(), at)
    if (emphasis > .001f) {
        drawCircle(tint.copy(alpha = emphasis * .16f), 15.dp.toPx(), at, style = Stroke(.6.dp.toPx()))
        drawArc(tint.copy(alpha = emphasis * .7f), -70f + emphasis * 20f, 255f * emphasis, false,
            at - Offset(10.dp.toPx(), 10.dp.toPx()), Size(20.dp.toPx(), 20.dp.toPx()), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
    }
    val reach = (5f + emphasis * 2f).dp.toPx()
    val width = (1f + emphasis * .7f).dp.toPx()
    drawPath(Path().apply {
        moveTo(at.x, at.y - reach); lineTo(at.x + width, at.y - width)
        lineTo(at.x + reach, at.y); lineTo(at.x + width, at.y + width)
        lineTo(at.x, at.y + reach); lineTo(at.x - width, at.y + width)
        lineTo(at.x - reach, at.y); lineTo(at.x - width, at.y - width); close()
    }, tint.copy(alpha = relevance))
    drawCircle(tint.copy(alpha = relevance), (3.2f + emphasis).dp.toPx(), at)
    drawCircle(SmartColors.ContentSurface.copy(alpha = (.65f + emphasis * .35f) * relevance), (1.2f + emphasis * .6f).dp.toPx(), at)
}

private fun DrawScope.drawStarLabels(
    stars: List<KnowledgeStar>, positions: Map<String, Offset>, selected: String,
    neighbors: Set<String>, relevance: Map<String, Float>, paint: TextPaint,
): List<PlacedStarLabel> {
    val ordered = stars.sortedWith(compareBy<KnowledgeStar>(
        { if (it.knowledge.knowledgeNodeId == selected) 0 else if (it.knowledge.knowledgeNodeId in neighbors) 1 else 2 },
        { it.knowledge.knowledgeNodeId }))
    val names = ordered.associate { star ->
        star.knowledge.knowledgeNodeId to TextUtils.ellipsize(star.knowledge.displayName, paint,
            minOf(132.dp.toPx(), size.width - 36.dp.toPx()), TextUtils.TruncateAt.MIDDLE).toString()
    }
    val metrics = paint.fontMetrics
    val padding = 8.dp.toPx()
    val labelHeight = metrics.descent - metrics.ascent + 10.dp.toPx()
    val labels = constellationLabels(ordered.map { star ->
        val id = star.knowledge.knowledgeNodeId
        val at = positions.getValue(id)
        StarLabelCandidate(id, at.x, at.y, paint.measureText(names.getValue(id)) + padding * 2, labelHeight)
    }, size.width, size.height, 4.dp.toPx(), 15.dp.toPx())
    labels.forEach { label ->
        val active = label.id == selected
        val topLeft = Offset(label.left, label.top)
        val labelSize = Size(label.width, label.height)
        drawRoundRect((if (active) SmartColors.JadeSoft else SmartColors.ContentSurface).copy(alpha = .9f),
            topLeft, labelSize, CornerRadius(8.dp.toPx()))
        if (active) drawRoundRect(SmartColors.JadeActive.copy(alpha = .5f), topLeft, labelSize,
            CornerRadius(8.dp.toPx()), style = Stroke(.75.dp.toPx()))
        paint.color = SmartColors.Ink.copy(alpha = .6f + .4f * relevance.getValue(label.id)).toArgb()
        drawContext.canvas.nativeCanvas.drawText(names.getValue(label.id), label.left + padding,
            label.top + 5.dp.toPx() - metrics.ascent, paint)
    }
    return labels
}
