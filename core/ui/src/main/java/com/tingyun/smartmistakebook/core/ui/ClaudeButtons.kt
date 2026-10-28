package com.tingyun.smartmistakebook.core.ui

// Native port of Caho1/open-claude-ui CdsButton.tsx, c1f923ae (MIT, Copyright 2026 Caho1).
// Full upstream license is bundled at assets/open-source/open-claude-ui-LICENSE.txt.
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Source paint hierarchy, with native semantics/keyboard activation and a 48dp touch target. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ClaudeAction(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: ButtonColors?,
    elevation: ButtonElevation?,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    variant: ActionVariant,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val resolvedColors = colors ?: when (variant) {
        ActionVariant.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = if (hovered) JadeActive else Jade,
            contentColor = OnJade,
            disabledContainerColor = Jade.copy(alpha = .12f),
            disabledContentColor = InkMuted,
        )
        ActionVariant.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = Ink.copy(alpha = if (hovered) .10f else .06f),
            contentColor = Ink,
            disabledContainerColor = Ink.copy(alpha = .03f),
            disabledContentColor = InkMuted,
        )
        ActionVariant.GHOST -> ButtonDefaults.textButtonColors(
            containerColor = if (hovered) Ink.copy(alpha = .05f) else Color.Transparent,
            contentColor = InkSecondary,
            disabledContentColor = InkMuted,
        )
    }
    val feedback = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .claudeActionFeedback(source, enabled, shape, resolvedColors.containerColor)
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 48.dp,
        LocalRippleConfiguration provides null,
    ) {
        if (variant == ActionVariant.GHOST) {
            MaterialTextButton(onClick = onClick, modifier = feedback, enabled = enabled,
                shape = shape, colors = resolvedColors, border = border,
                contentPadding = contentPadding, interactionSource = source, content = content)
        } else {
            MaterialButton(onClick = onClick, modifier = feedback, enabled = enabled,
                shape = shape, colors = resolvedColors, elevation = elevation, border = border,
                contentPadding = contentPadding, interactionSource = source, content = content)
        }
    }
}

private enum class ActionVariant { PRIMARY, SECONDARY, GHOST }

internal enum class ClaudeDialogButtonKind { NONE, CONFIRM, CANCEL }
internal val LocalClaudeDialogButtonKind = staticCompositionLocalOf { ClaudeDialogButtonKind.NONE }
internal val LocalClaudeDangerConfirm = staticCompositionLocalOf { false }

@Composable
private fun Modifier.claudeActionFeedback(
    source: MutableInteractionSource,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val motion = LocalClaudeMotionEnabled.current
    val scale by animateFloatAsState(if (pressed && enabled && motion) .95f else 1f,
        ClaudeMotion.spec(motion, ClaudeMotion.PressMillis), label = "cds-button-press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (focused && enabled) Modifier.border(2.dp, claudeFocusIndicatorColor(containerColor), shape) else Modifier)
}

@Composable
fun ClaudeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = ClaudeAction(onClick, modifier, enabled, shape, colors, elevation, border,
    contentPadding, interactionSource, ActionVariant.PRIMARY, content)

/** Secondary actions intentionally have no border inside a panel (upstream's 1-border rule). */
@Composable
fun ClaudeSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = ClaudeAction(onClick, modifier, enabled, shape, colors, elevation, border,
    contentPadding, interactionSource, ActionVariant.SECONDARY, content)

@Composable
fun ClaudeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val kind = LocalClaudeDialogButtonKind.current
    val variant = when (kind) {
        ClaudeDialogButtonKind.CONFIRM -> ActionVariant.PRIMARY
        ClaudeDialogButtonKind.CANCEL -> ActionVariant.SECONDARY
        ClaudeDialogButtonKind.NONE -> ActionVariant.GHOST
    }
    val resolvedColors = if (colors == null && kind == ClaudeDialogButtonKind.CONFIRM && LocalClaudeDangerConfirm.current) {
        ButtonDefaults.buttonColors(containerColor = ErrorText, contentColor = Color.White)
    } else colors
    ClaudeAction(onClick, modifier, enabled, shape, resolvedColors, null, border,
        contentPadding, interactionSource, variant, content)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ClaudeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = InkSecondary, disabledContentColor = InkMuted),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        MaterialTextButton(onClick = onClick,
            modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .claudeActionFeedback(source, enabled, RoundedCornerShape(8.dp), colors.containerColor),
            enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (hovered && colors.containerColor.alpha == 0f) Ink.copy(alpha = .05f) else colors.containerColor,
                contentColor = colors.contentColor, disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor), interactionSource = source) { content() }
    }
}
