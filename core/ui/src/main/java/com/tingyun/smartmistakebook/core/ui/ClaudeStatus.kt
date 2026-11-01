package com.tingyun.smartmistakebook.core.ui

// Native port of Caho1/open-claude-ui StatusIndicators.tsx, c1f923ae (MIT, Copyright 2026 Caho1).
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Decorative dots only; the adjacent text carries the actual task state for accessibility. */
@Composable
fun ClaudeQuietDots(modifier: Modifier = Modifier) {
    val phase = claudeLoopPhase(1900, "cds-quiet-dots")
    Canvas(modifier.size(width = 16.dp, height = 12.dp)) {
        val radius = 1.5.dp.toPx()
        repeat(3) { index ->
            val wave = sin((phase * 2 * PI - index * .5).toFloat()).coerceAtLeast(0f)
            drawCircle(JadeActive, radius, Offset(radius + index * 6.5.dp.toPx(),
                size.height / 2 - wave * 2.dp.toPx()))
        }
    }
}

/** A single sweep over neutral bars; only real loading-state callers mount it. */
@Composable
fun ClaudeLoadingPlaceholder(modifier: Modifier = Modifier) {
    val motion = LocalClaudeMotionEnabled.current
    val phase = claudeLoopPhase(2000, "cds-skeleton-sweep")
    Canvas(modifier.fillMaxWidth(.85f).height(68.dp).testTag("page_state_skeleton")) {
        val shift = (phase * 2.2f - .6f) * size.width
        val span = size.width * .6f
        val sheen = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .5f), Color.Transparent),
            start = Offset(shift, 0f), end = Offset(shift + span * .9659f, span * .2588f))
        listOf(1f, .82f, .60f).forEachIndexed { index, fraction ->
            val top = Offset(0f, index * 26.dp.toPx())
            val barSize = Size(size.width * fraction, 12.dp.toPx())
            drawRoundRect(Ink.copy(alpha = .06f), top, barSize, CornerRadius(6.dp.toPx()))
            if (motion) drawRoundRect(sheen, top, barSize, CornerRadius(6.dp.toPx()))
        }
    }
}

@Composable
private fun claudeLoopPhase(duration: Int, label: String): Float {
    if (!LocalClaudeMotionEnabled.current) return 0f
    val transition = rememberInfiniteTransition(label = "cds-quiet-dots")
    val phase by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart), label = label)
    return phase
}
