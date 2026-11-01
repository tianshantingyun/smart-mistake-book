package com.tingyun.smartmistakebook.core.ui

// cds-theme.css / DraggableSessionList.tsx, Caho1/open-claude-ui c1f923ae, MIT.
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

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
    val fill by animateColorAsState(when {
        !enabled -> Color.Transparent
        pressed -> Ink.copy(alpha = .10f)
        selected -> selectedFill
        hovered -> Ink.copy(alpha = .05f)
        else -> Color.Transparent
    }, ClaudeMotion.spec(LocalClaudeMotionEnabled.current), label = "cds-row-fill")
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
    val fill by animateColorAsState(if (pressed) SubtleSurface else if (hovered) Paper else ContentSurface,
        ClaudeMotion.spec(LocalClaudeMotionEnabled.current), label = "cds-card-fill")
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill,
            border = if (focused) BorderStroke(2.dp, claudeFocusIndicatorColor(fill))
                else if (bordered) BorderStroke(1.dp, Ink.copy(alpha = .1f)) else null,
            shadowElevation = elevation,
            interactionSource = source, content = content)
    }
}
