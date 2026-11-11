package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@Stable
class ClaudeGlassState internal constructor(internal val haze: HazeState)

@Composable
fun rememberClaudeGlassState(): ClaudeGlassState = remember { ClaudeGlassState(HazeState()) }

fun Modifier.claudeGlassSource(state: ClaudeGlassState): Modifier = hazeSource(state.haze)

val LocalClaudeReadingCompact = staticCompositionLocalOf { false }
val LocalClaudeExpandReadingInput = staticCompositionLocalOf<() -> Unit> { {} }

/** Samples the reading layer behind the bars. Foreground controls remain sharp. */
@Composable
fun ClaudeGlassSurface(
    state: ClaudeGlassState,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(shape).hazeEffect(state.haze, HazeStyle(
        backgroundColor = Paper,
        tint = HazeTint(Paper.copy(alpha = .86f)),
        blurRadius = 14.dp,
        noiseFactor = .008f,
        fallbackTint = HazeTint(Paper.copy(alpha = .97f)),
    )).border(.5.dp, Ink.copy(alpha = .045f), shape), content = content)
}
