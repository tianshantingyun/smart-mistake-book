package com.tingyun.smartmistakebook.core.ui

// cds-theme.css / DraggableSessionList.tsx, Caho1/open-claude-ui c1f923ae, MIT.
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset

/** Two shadow layers from cds-theme.css: contact first, then a soft ambient penumbra. */
enum class ClaudeSurfaceDepth { PANEL, COMPOSER, FLOATING }

fun Modifier.claudeSurfaceShadow(
    shape: Shape,
    depth: ClaudeSurfaceDepth = ClaudeSurfaceDepth.PANEL,
    emphasis: Float = 1f,
): Modifier {
    val floating = depth == ClaudeSurfaceDepth.FLOATING
    val composer = depth == ClaudeSurfaceDepth.COMPOSER
    val strength = emphasis.coerceIn(0f, 1.5f)
    return dropShadow(shape, Shadow(
        radius = if (floating) 6.dp else 2.dp,
        color = Ink.copy(alpha = .025f * strength),
        offset = DpOffset(0.dp, if (floating) 2.dp else 1.dp),
    )).dropShadow(shape, Shadow(
        radius = if (floating) 24.dp else if (composer) 20.dp else 8.dp,
        color = Ink.copy(alpha = (if (floating) .085f else if (composer) .045f else .025f) * strength),
        offset = DpOffset(0.dp, if (floating) 8.dp else if (composer) 4.dp else 2.dp),
    ))
}

/** Neutral interactive paint without scaling text, image bounds, or hit targets. */
@Composable
fun Modifier.claudeInteractiveRow(
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    selectedFill: Color = Ink.copy(alpha = .06f),
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val motion = LocalClaudeMotionEnabled.current
    val targetFill = when {
        !enabled -> Color.Transparent
        pressed -> Ink.copy(alpha = .07f)
        selected -> selectedFill
        hovered -> Ink.copy(alpha = .04f)
        else -> Color.Transparent
    }
    val fill = if (motion) animateColorAsState(targetFill,
        ClaudeMotion.spec(true, if (pressed) ClaudeMotion.PressMillis else ClaudeMotion.ReleaseMillis),
        label = "cds-row-fill").value else targetFill
    return defaultMinSize(minHeight = 48.dp).clip(shape).background(fill)
        .then(if (focused && enabled) Modifier.border(2.dp, claudeFocusIndicatorColor(fill), shape) else Modifier)
        .clickable(source, indication = null, enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = onClick)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ClaudeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    bordered: Boolean = false,
    elevation: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val motion = LocalClaudeMotionEnabled.current
    val targetFill = if (pressed) SubtleSurface else if (hovered) Paper else ContentSurface
    val targetShadow = when {
        pressed -> .45f
        hovered || focused -> .9f
        else -> .65f
    }
    val duration = if (pressed) ClaudeMotion.PressMillis else ClaudeMotion.ReleaseMillis
    val fill = if (motion) animateColorAsState(targetFill, ClaudeMotion.spec(true, duration),
        label = "cds-card-fill").value else targetFill
    val shadowStrength = if (motion) animateFloatAsState(targetShadow, ClaudeMotion.spec(true, duration),
        label = "cds-card-shadow").value else targetShadow
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(onClick = onClick, modifier = modifier.then(if (elevation > 0.dp)
            Modifier.claudeSurfaceShadow(shape, emphasis = shadowStrength) else Modifier), shape = shape, color = fill,
            border = if (focused) BorderStroke(2.dp, claudeFocusIndicatorColor(fill))
                else if (bordered) BorderStroke(.5.dp, SmartColors.Divider) else null,
            shadowElevation = 0.dp,
            interactionSource = source, content = content)
    }
}
