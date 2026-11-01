package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A quiet, authored notebook signature. Pure decoration; no learning or completion data. */
@Composable
internal fun StudioNotebookMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(68.dp)) {
        val unit = size.width / 68f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun page(x: Float, y: Float, color: Color) {
            drawRoundRect(color, point(x, y), Size(35 * unit, 46 * unit), CornerRadius(5 * unit))
            drawRoundRect(SmartColors.JadeMuted.copy(alpha = .35f), point(x, y),
                Size(35 * unit, 46 * unit), CornerRadius(5 * unit), style = Stroke(unit))
        }
        rotate(-13f, point(34f, 34f)) { page(12f, 13f, SmartColors.JadeSoft) }
        rotate(10f, point(34f, 34f)) { page(23f, 9f, SmartColors.ReadingSurface) }
        page(17f, 12f, SmartColors.ContentSurface)
        drawLine(SmartColors.JadeActive, point(24f, 25f), point(43f, 25f), 2 * unit)
        drawLine(SmartColors.Outline.copy(alpha = .6f), point(24f, 33f), point(45f, 33f), unit)
        drawLine(SmartColors.Outline.copy(alpha = .6f), point(24f, 39f), point(40f, 39f), unit)
        drawPath(Path().apply {
            moveTo(44 * unit, 12 * unit); lineTo(48 * unit, 12 * unit)
            lineTo(48 * unit, 23 * unit); lineTo(46 * unit, 21 * unit)
            lineTo(44 * unit, 23 * unit); close()
        }, SmartColors.JadeActive)
    }
}

/** Actual snapshot values only. Color and layout are presentation, never a new score or trend. */
@Composable
fun StudioMetricTile(
    value: String,
    label: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    containerColor: Color = SmartColors.JadeSoft,
    accentColor: Color = SmartColors.JadeDark,
) {
    Surface(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "$label，$value$unit"
    }, shape = RoundedCornerShape(16.dp), color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = .12f))) {
        Column(Modifier.defaultMinSize(minHeight = 114.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = accentColor, style = MaterialTheme.typography.labelMedium)
            Text(value, color = accentColor, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp,
                    lineHeight = 36.sp, fontFeatureSettings = "tnum"))
            if (unit.isNotEmpty()) Text(unit, color = accentColor,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
