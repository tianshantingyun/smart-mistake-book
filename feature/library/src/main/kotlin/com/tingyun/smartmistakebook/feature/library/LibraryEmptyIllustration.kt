package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.SmartColors

/** Decorative notebook, drawn locally so it stays crisp at every display density. */
@Composable
internal fun LibraryEmptyIllustration() {
    Canvas(Modifier.size(120.dp)) {
        val unit = size.width / 120f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun extent(w: Float, h: Float) = Size(w * unit, h * unit)
        drawCircle(SmartColors.JadeSoft, radius = 54 * unit, center = center)
        rotate(10f) {
            drawRoundRect(SmartColors.Outline, point(32f, 20f), extent(63f, 86f), CornerRadius(8 * unit))
        }
        rotate(-7f) {
            drawRoundRect(SmartColors.Forest, point(25f, 14f), extent(64f, 88f), CornerRadius(8 * unit))
            drawRoundRect(SmartColors.ContentSurface, point(33f, 14f), extent(56f, 88f), CornerRadius(8 * unit))
            drawLine(SmartColors.Jade, point(45f, 43f), point(72f, 43f), 3 * unit)
            drawLine(SmartColors.Divider, point(45f, 56f), point(77f, 56f), 2 * unit)
            drawLine(SmartColors.Divider, point(45f, 66f), point(73f, 66f), 2 * unit)
            drawLine(SmartColors.Divider, point(45f, 76f), point(65f, 76f), 2 * unit)
            drawPath(Path().apply {
                moveTo(69 * unit, 14 * unit)
                lineTo(80 * unit, 14 * unit)
                lineTo(80 * unit, 34 * unit)
                lineTo(74.5f * unit, 30 * unit)
                lineTo(69 * unit, 34 * unit)
                close()
            }, SmartColors.Jade)
        }
    }
}
