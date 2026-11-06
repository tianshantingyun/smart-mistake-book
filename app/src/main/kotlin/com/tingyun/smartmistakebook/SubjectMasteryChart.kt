package com.tingyun.smartmistakebook

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.masteryBandLabel
import com.tingyun.smartmistakebook.core.ui.masteryEstimateColor
import com.tingyun.smartmistakebook.core.ui.studentLabel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class SubjectMasteryPoint(
    val subject: SubjectKind,
    val estimate: Float?,
)

internal fun subjectMasteryPoints(summaries: List<StudyKnowledgeSummary>): List<SubjectMasteryPoint> =
    summaries.distinctBy { it.knowledgeNodeId }.groupBy { it.subject }.entries
        .sortedBy { if (it.key == SubjectKind.GENERAL) Int.MAX_VALUE else it.key.ordinal }
        .map { (subject, values) ->
            val average = values.map { it.conservativeMasteryScore }.average()
            SubjectMasteryPoint(subject, if (values.any { it.masteryIntervalUpper != null } && average.isFinite())
                average.toFloat().coerceIn(0f, 1f) else null)
        }

@Composable
internal fun SubjectMasteryChart(summaries: List<StudyKnowledgeSummary>) {
    val points = subjectMasteryPoints(summaries)
    val known = points.filter { it.estimate != null }
    val largeText = LocalDensity.current.fontScale > 1.3f
    WorkspacePanel(modifier = Modifier.padding(top = 16.dp).testTag("mastery_estimate_chart")) {
        Text("科目掌握估计", color = SmartColors.Ink, style = MaterialTheme.typography.titleMedium)
        Text("雷达图比较各科当前的平均掌握估计，不表示知识点关系、考试分数或正确率。",
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        when {
            known.size < 3 -> Text("至少有 3 个有练习证据的科目时显示雷达图。",
                color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            largeText -> Text("当前字号较大，以下方文字显示各科掌握估计。",
                color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            else -> RadarMasteryChart(known)
        }
        points.forEach { point ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(point.subject.studentLabel(), color = SmartColors.Ink, style = MaterialTheme.typography.bodySmall)
                Text(point.estimate?.let { "平均估计 · ${masteryBandLabel(it.toDouble())}" } ?: "证据还少",
                    color = point.estimate?.let { masteryEstimateColor(it.toDouble()) } ?: SmartColors.InkMuted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (known.size < points.size) Text("证据还少的科目暂不纳入雷达图。",
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RadarMasteryChart(points: List<SubjectMasteryPoint>) {
    val labelSize = with(LocalDensity.current) { 12.sp.toPx() }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = labelSize; color = SmartColors.InkSecondary.toArgb(); textAlign = Paint.Align.CENTER
    }
    Canvas(Modifier.fillMaxWidth().height(280.dp).testTag("mastery_radar_chart")
        .semantics { contentDescription = "科目掌握雷达图，离中心越远估计越高；详细含义见下方科目说明" }) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width / 2 - 58.dp.toPx(), size.height / 2 - 32.dp.toPx()).coerceAtLeast(0f)
        fun point(index: Int, fraction: Float): Offset {
            val angle = -Math.PI / 2 + 2 * Math.PI * index / points.size
            return center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius * fraction)
        }
        for (fraction in listOf(.25f, .5f, .75f, 1f)) {
            val ring = Path().apply {
                points.indices.forEach { i -> val p = point(i, fraction); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close()
            }
            drawPath(ring, SmartColors.Divider, style = Stroke(1.dp.toPx()))
        }
        points.forEachIndexed { index, value ->
            drawLine(SmartColors.Divider, center, point(index, 1f), 1.dp.toPx())
            val label = point(index, 1f)
            val direction = label - center
            val length = direction.getDistance().coerceAtLeast(1f)
            val at = label + direction / length * 18.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(value.subject.studentLabel(), at.x, at.y + labelSize * .35f, paint)
        }
        val outline = Path().apply {
            points.forEachIndexed { i, value -> val p = point(i, value.estimate ?: 0f); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close()
        }
        drawPath(outline, SmartColors.Jade.copy(alpha = .14f))
        drawPath(outline, SmartColors.Jade, style = Stroke(2.dp.toPx()))
        points.forEachIndexed { i, value -> drawCircle(SmartColors.Jade, 4.dp.toPx(), point(i, value.estimate ?: 0f)) }
    }
}
